#!/usr/bin/env bash
# Builds sherpa-onnx's Android JNI library for speech recognition only, for Lowkey's Whisper models.
#
# The prebuilt sherpa-onnx Android libraries include text-to-speech, which statically links
# eSpeak NG (GPL-3.0-or-later). This build turns text-to-speech and speaker diarization off, so
# the library contains only permissively licensed code. It links the native-only ONNX Runtime
# build that sherpa-onnx uses (github.com/csukuangfj/onnxruntime-libs). Unlike Microsoft's Maven
# package, that has no Java telemetry transport, so ONNX Runtime's telemetry never initializes on
# Android (onnxruntime/core/platform/telemetry_1ds.cc) and the app makes no such network requests.
#
#   ANDROID_HOME=<sdk> scripts/build-sherpa-onnx.sh <empty-output-dir>
#
# Writes sherpa-onnx-<version>-asr-android.zip and BUILD-INFO.txt. The zip holds
# jni/<abi>/libsherpa-onnx-jni.so and libonnxruntime.so, and licenses/ with every notice that
# applies to them (copied from app/src/main/assets/licenses).
# Needs the NDK and CMake from the Android SDK, curl, unzip, and network access (CMake fetches
# sherpa-onnx's pinned dependencies). Build paths are remapped, so no local paths end up in the
# libraries. The zip is attached to the "native-libs" GitHub release, which the Gradle build
# downloads and verifies by SHA-256.
set -euo pipefail

out=${1:?usage: $0 <empty-output-dir>}
: "${ANDROID_HOME:?set ANDROID_HOME to the Android SDK}"
sherpa_version=1.13.8
sherpa_sha256=b0374cc56dbc186d442ae73d5de743bb092470b640c4c50ce7b029044c0c4fa8
ort_version=1.31.0
ort_sha256=be178835b05a8e9439e8fa1729072e4f0437166fdaf3390ea76156ff1df6f56c
ndk_version=${NDK_VERSION:-28.2.13676358}
cmake_version=${CMAKE_VERSION:-3.22.1}
abis=(arm64-v8a x86_64)

ndk="$ANDROID_HOME/ndk/$ndk_version"
cmake_bin="$ANDROID_HOME/cmake/$cmake_version/bin"
[ -d "$ndk" ] || { echo "NDK $ndk_version not found in $ANDROID_HOME/ndk" >&2; exit 1; }
[ -x "$cmake_bin/cmake" ] || { echo "CMake $cmake_version not found in $ANDROID_HOME/cmake" >&2; exit 1; }
root=$(cd "$(dirname "$0")/.." && pwd)
licenses="$root/app/src/main/assets/licenses"
mkdir -p "$out"
out=$(cd "$out" && pwd)
work=$(mktemp -d /tmp/sherpa-asr-build.XXXXXX)
trap 'rm -rf "$work"' EXIT

fetch() { # url sha256 file
  curl -fsSL --retry 3 -o "$3" "$1"
  echo "$2  $3" | sha256sum -c --quiet - || { echo "checksum mismatch: $1" >&2; exit 1; }
}
fetch "https://github.com/k2-fsa/sherpa-onnx/archive/refs/tags/v$sherpa_version.tar.gz" "$sherpa_sha256" "$work/sherpa.tar.gz"
fetch "https://github.com/csukuangfj/onnxruntime-libs/releases/download/v$ort_version/onnxruntime-android-$ort_version.zip" \
  "$ort_sha256" "$work/onnxruntime.zip"
mkdir "$work/src" "$work/ort"
tar -xzf "$work/sherpa.tar.gz" -C "$work/src" --strip-components=1
unzip -q "$work/onnxruntime.zip" -d "$work/ort"
ort_root=$(dirname "$(find "$work/ort" -path '*/headers/onnxruntime_c_api.h' | head -1)")/..

for abi in "${abis[@]}"; do
  build="$work/build-$abi"
  # Map every local path to a neutral one, so neither __FILE__ strings nor debug info name them.
  maps="-ffile-prefix-map=$work/src=sherpa-onnx -ffile-prefix-map=$build=build -ffile-prefix-map=$ndk=ndk"
  SHERPA_ONNXRUNTIME_LIB_DIR="$ort_root/jni/$abi" SHERPA_ONNXRUNTIME_INCLUDE_DIR="$ort_root/headers" \
    "$cmake_bin/cmake" -S "$work/src" -B "$build" -G Ninja -DCMAKE_MAKE_PROGRAM="$cmake_bin/ninja" \
    -DCMAKE_TOOLCHAIN_FILE="$ndk/build/cmake/android.toolchain.cmake" \
    -DANDROID_ABI="$abi" -DANDROID_PLATFORM=android-28 \
    -DCMAKE_BUILD_TYPE=Release -DBUILD_SHARED_LIBS=ON \
    -DCMAKE_C_FLAGS="$maps" -DCMAKE_CXX_FLAGS="$maps" \
    -DCMAKE_SHARED_LINKER_FLAGS="-Wl,-z,max-page-size=16384 -Wl,--build-id=none" \
    -DSHERPA_ONNX_ENABLE_TTS=OFF -DSHERPA_ONNX_ENABLE_SPEAKER_DIARIZATION=OFF \
    -DSHERPA_ONNX_ENABLE_JNI=ON -DSHERPA_ONNX_ENABLE_C_API=OFF -DSHERPA_ONNX_ENABLE_BINARY=OFF \
    -DSHERPA_ONNX_ENABLE_PYTHON=OFF -DSHERPA_ONNX_ENABLE_TESTS=OFF -DSHERPA_ONNX_ENABLE_CHECK=OFF \
    -DSHERPA_ONNX_ENABLE_PORTAUDIO=OFF -DSHERPA_ONNX_ENABLE_WEBSOCKET=OFF \
    -DSHERPA_ONNX_ENABLE_RKNN=OFF -DSHERPA_ONNX_ENABLE_QNN=OFF \
    -DSHERPA_ONNX_LINK_LIBSTDCPP_STATICALLY=OFF
  "$cmake_bin/cmake" --build "$build" --target sherpa-onnx-jni -j "${JOBS:-4}"
  lib=$(find "$build" -name libsherpa-onnx-jni.so | head -1)
  mkdir -p "$work/zip/jni/$abi"
  "$ndk/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip" --strip-unneeded -o "$work/zip/jni/$abi/libsherpa-onnx-jni.so" "$lib"
  cp "$ort_root/jni/$abi/libonnxruntime.so" "$work/zip/jni/$abi/libonnxruntime.so"
done

# No GPL code may be in the result: eSpeak NG's data names and piper-phonemize must be absent.
# strings runs on its own (not in a pipeline), so an early match can't be lost to SIGPIPE.
for so in "$work"/zip/jni/*/libsherpa-onnx-jni.so; do
  strings -a "$so" > "$work/strings.txt"
  if grep -E 'phontab|espeak-ng-data|piper_phonemize|PiperPhonemesToIds' "$work/strings.txt" > /dev/null; then
    echo "error: $so contains text-to-speech code" >&2
    exit 1
  fi
done

# The notices travel with the binaries.
mkdir -p "$work/zip/licenses"
cp "$licenses/Apache-2.0.txt" "$work/zip/licenses/"
cp -r "$licenses/onnxruntime" "$licenses/sherpa-onnx" "$work/zip/licenses/"
sed -n '/^ONNX Runtime /,/^Speech models/p' "$licenses/README.txt" | sed '$d' > "$work/zip/licenses/README.txt"

zip_name="sherpa-onnx-$sherpa_version-asr-android.zip"
rm -f "$out/$zip_name"
# Sorted entries and fixed timestamps, so the same inputs give the same zip.
(cd "$work/zip" && find . -type f -exec touch -d @0 {} + && find jni licenses -type f | LC_ALL=C sort | zip -q -X -D "$out/$zip_name" -@)
{
  echo "sherpa-onnx $sherpa_version (https://github.com/k2-fsa/sherpa-onnx/tree/v$sherpa_version), speech recognition only"
  echo "Built by scripts/build-sherpa-onnx.sh with Android NDK $ndk_version, CMake $cmake_version, android-28"
  echo "ONNX Runtime $ort_version: github.com/csukuangfj/onnxruntime-libs v$ort_version onnxruntime-android-$ort_version.zip (SHA-256 $ort_sha256)"
  echo "Compiler: $("$ndk/toolchains/llvm/prebuilt/linux-x86_64/bin/clang" --version | head -1)"
  echo "Zip: $(zip -v | sed -n 2p | sed 's/^ *//')"
  echo
  (cd "$out" && sha256sum "$zip_name")
  (cd "$work/zip" && find jni -type f | sort | xargs sha256sum)
} > "$out/BUILD-INFO.txt"
cat "$out/BUILD-INFO.txt"

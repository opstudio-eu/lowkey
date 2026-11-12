Lowkey Keyboard
Copyright 2026 OpStudio

Licensed under the Apache License, Version 2.0 (Apache-2.0.txt). Distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND. Source code: https://github.com/opstudio-eu/lowkey

The OpStudio name and logo (res/drawable/opstudio_logo_blue.xml and opstudio_logo_light_blue.xml)
are trademarks of OpStudio, all rights reserved. They are not licensed under Apache-2.0, which grants
no trademark rights (section 6); the app shows them only to say who made it. Forks and modified
versions should remove the logo.

The APK contains these third-party components. Their notices are in this folder.

Unicode Emoji 17.0 data (Unicode License V3)                   UNICODE-LICENSE.txt
  ../emoji/emoji-test.txt from https://www.unicode.org/Public/17.0.0/emoji/emoji-test.txt
  SHA-256: 1d8a944f88d7952f7ef7c5167fef3c67995bcae24543949710231b03a201acda
  Copyright 2025 Unicode, Inc. The device supplies emoji glyphs; no font or image assets are bundled.

Java and Kotlin libraries (Apache-2.0, Apache-2.0.txt)
  AndroidX, Jetpack Compose, Material 3, Kotlin standard library, kotlinx.coroutines and their
  dependencies: the exact versions are in dependencies/manifest.txt, and the NOTICE files they carry
  are next to it.

AOSP LatinIME word lists (Apache-2.0)                           Apache-2.0.txt, ../dict/NOTICE
  platform/packages/inputmethods/LatinIME, commit 127336e9f29d69607eab55982324b210279ae8c5
  English (US) is in the APK. The other keyboard languages in
  ../dict/languages.txt are downloaded on request from the same commit (same license), checked
  against their pinned SHA-256 and converted on the phone; they are not part of the APK.

ONNX Runtime 1.31.0 (libonnxruntime.so), MIT                    onnxruntime/
  https://github.com/microsoft/onnxruntime/tree/v1.31.0, native-only Android build from
  https://github.com/csukuangfj/onnxruntime-libs/releases/tag/v1.31.0 (no Java telemetry transport,
  so ONNX Runtime's telemetry stays off on Android)
  onnxruntime/LICENSE.txt, onnxruntime/ThirdPartyNotices.txt (lists every component ONNX Runtime
  can use; not all are in the Android build)
  It contains its own copy of Eigen (MPL-2.0; sherpa-onnx/eigen-COPYING.MPL2.txt): commit
  1d8b82b0740839c0de7f1242a3585e3390ff5f33, source code:
  https://github.com/eigen-mirror/eigen/archive/1d8b82b0740839c0de7f1242a3585e3390ff5f33.zip
  with the build patches in onnxruntime v1.31.0 cmake/patches/eigen/ (s390x only).

sherpa-onnx 1.13.8 (libsherpa-onnx-jni.so), Apache-2.0          Apache-2.0.txt
  https://github.com/k2-fsa/sherpa-onnx/tree/v1.13.8, built for speech recognition only (without
  text-to-speech) by scripts/build-sherpa-onnx.sh. Its Kotlin API is included unmodified
  (com.k2fsa.sherpa.onnx). The library also contains:
    kaldi-decoder 0.3.0 (Apache-2.0)                       Apache-2.0.txt
    kaldifst 1.8.0 (Apache-2.0)                            sherpa-onnx/kaldifst-LICENSE.txt
      with libc++-derived basic_filebuf (MIT or NCSA)      sherpa-onnx/kaldifst-bundled-libcxx-basic-filebuf-LICENSE.txt
    OpenFst, csukuangfj fork v1.8.5-2026-07-09 (Apache-2.0) sherpa-onnx/openfst-COPYING.txt, sherpa-onnx/openfst-AUTHORS.txt
    kaldi-native-fbank 1.22.3 (Apache-2.0)                 Apache-2.0.txt
      with KISS FFT (BSD-3-Clause)                         sherpa-onnx/kissfft-COPYING.txt, sherpa-onnx/kissfft-LICENSES-BSD-3-Clause.txt
    simple-sentencepiece 0.7 (Apache-2.0)                  Apache-2.0.txt
      with darts-clone 0.32 (BSD-2-Clause)                 sherpa-onnx/simple-sentencepiece-bundled-darts-clone-LICENSE.txt
      and ThreadPool (zlib)                                sherpa-onnx/simple-sentencepiece-bundled-ThreadPool-LICENSE.txt
    nlohmann/json 3.12.0 (MIT)                             sherpa-onnx/nlohmann-json-LICENSE.MIT.txt
    Eigen 5.0.1 (MPL-2.0; some files BSD or Apache-2.0)    sherpa-onnx/eigen-COPYING.MPL2.txt, sherpa-onnx/eigen-COPYING.BSD.txt,
                                                           sherpa-onnx/eigen-COPYING.README.txt
      Eigen's source code: https://gitlab.com/libeigen/eigen/-/archive/5.0.1/eigen-5.0.1.tar.gz
    LLVM libc++ from Android NDK r28 (Apache-2.0 WITH LLVM-exception)

Speech models (OpenAI Whisper and Distil-Whisper, MIT) are downloaded separately and are not part of
the APK. FUTO Keyboard and the Moonshine voice-input fork are design references only; no source code
or assets are taken from them.

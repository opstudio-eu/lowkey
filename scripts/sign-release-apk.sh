#!/usr/bin/env bash
# Signs an unsigned release APK with the release key and the key-rotation lineage, then checks it.
#
#   scripts/sign-release-apk.sh app/build/outputs/apk/release/app-release-unsigned.apk out.apk
#
# Needs RELEASE_KEYSTORE (path to the PKCS12 keystore) and RELEASE_KEYSTORE_PASSWORD. Keep both
# outside the repository; CI restores them from Actions secrets.
#
# The app was first signed with a development machine's debug key. signing/rotation-lineage.bin is the debug
# key's signed approval of the release key (APK Signature Scheme v3), so phones with a debug-signed
# install accept these APKs as updates and keep their data. v3 needs Android 9 (minSdk 28), and
# v1/v2 are off so the debug key is never needed again.
set -euo pipefail

if [ $# -ne 2 ]; then
  echo "usage: $0 <unsigned.apk> <signed.apk>" >&2
  exit 2
fi
: "${RELEASE_KEYSTORE:?set RELEASE_KEYSTORE to the release keystore path}"
: "${RELEASE_KEYSTORE_PASSWORD:?set RELEASE_KEYSTORE_PASSWORD}"
export RELEASE_KEYSTORE_PASSWORD

root=$(cd "$(dirname "$0")/.." && pwd)
lineage="$root/signing/rotation-lineage.bin"
release_cert=27f8bcb88e1c89a314d16ff1aed882d2985177db762102b6447d3288e242d3d9
debug_cert=fce3cdc7b3faf4be2817b224526a7ec95484273e2d741b5b4a6a9be4b796e54a

build_tools=$(find "${ANDROID_HOME:?set ANDROID_HOME}/build-tools" -mindepth 1 -maxdepth 1 -type d | sort -V | tail -1)
apksigner="$build_tools/apksigner"

"$apksigner" sign \
  --ks "$RELEASE_KEYSTORE" --ks-key-alias slim-keyboard --ks-pass env:RELEASE_KEYSTORE_PASSWORD \
  --lineage "$lineage" --rotation-min-sdk-version 28 \
  --v1-signing-enabled false --v2-signing-enabled false --v3-signing-enabled true --v4-signing-enabled false \
  --out "$2" "$1"

certs=$("$apksigner" verify --min-sdk-version 28 --print-certs "$2" |
  sed -nE 's/^.*certificate SHA-256 digest: ([0-9a-f]+)$/\1/p' | sort -u | paste -sd,)
if [ "$certs" != "$release_cert" ]; then
  echo "error: $2 is signed by '$certs', expected the release key $release_cert" >&2
  exit 1
fi
lineage_info=$("$apksigner" lineage --in "$2" --print-certs -v)
if ! grep -q "Signer #1 in lineage certificate SHA-256 digest: $debug_cert" <<<"$lineage_info"; then
  echo "error: $2 has no rotation lineage from the debug key; it would not update existing installs" >&2
  exit 1
fi
# The debug key needs installed-data (to update in place) and permission (the app redeclares
# AndroidX's signature permission, otherwise INSTALL_FAILED_DUPLICATE_PERMISSION). Nothing else.
old_caps=$(sed -n '/^Signer #1 /,/^Signer #2 /p' <<<"$lineage_info" | sed -nE 's/^Has (.*) capability *: (true|false)$/\1=\2/p' | paste -sd,)
expected_caps="installed data=true,shared UID=false,permission=true,rollback=false,auth=false"
if [ "$old_caps" != "$expected_caps" ]; then
  echo "error: debug key capabilities are '$old_caps', expected '$expected_caps'" >&2
  exit 1
fi
echo "Signed $2 with the release key ($release_cert) and the rotation lineage."

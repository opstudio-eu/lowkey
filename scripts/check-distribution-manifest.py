#!/usr/bin/env python3
"""Checks a merged manifest for the GitHub or Play distribution flavor.

    python3 -I scripts/check-distribution-manifest.py github path/to/AndroidManifest.xml
    python3 -I scripts/check-distribution-manifest.py play path/to/manifest-dir

Play must not declare the in-app updater. The GitHub manifest must still declare it.
A directory is searched for AndroidManifest.xml; every match is checked.
"""
import sys
from pathlib import Path

UPDATER_PERMISSIONS = (
    "android.permission.REQUEST_INSTALL_PACKAGES",
    "android.permission.UPDATE_PACKAGES_WITHOUT_USER_ACTION",
    "android.permission.FOREGROUND_SERVICE",
    "android.permission.FOREGROUND_SERVICE_DATA_SYNC",
    "android.permission.POST_NOTIFICATIONS",
    "android.permission.ACCESS_NETWORK_STATE",
)
# Source manifests use a relative name; the merged manifest expands it to the Kotlin namespace.
NAMESPACE = "com.rheerkens.androidkeyboard"
APPLICATION_ID = "com.opstudio.lowkey"
UPDATER_COMPONENTS = (
    ".updates.UpdateCheckJob",
    ".updates.UpdateDownloadService",
    ".updates.UpdateInstallReceiver",
    ".updates.UpdatedReceiver",
)


def manifests(path: Path):
    if path.is_dir():
        found = sorted(path.rglob("AndroidManifest.xml"))
        if not found:
            sys.exit(f"no AndroidManifest.xml under {path}")
        return found
    if not path.is_file():
        sys.exit(f"manifest not found: {path}")
    return [path]


def declared(text: str, name: str) -> bool:
    names = (name, NAMESPACE + name) if name.startswith(".") else (name,)
    return any(f'android:name="{item}"' in text for item in names)


def main(argv):
    if len(argv) != 3 or argv[1] not in ("github", "play"):
        sys.exit("usage: check-distribution-manifest.py github|play <manifest-or-directory>")
    flavor, path = argv[1], Path(argv[2])
    failed = False
    for manifest in manifests(path):
        text = manifest.read_text(encoding="utf-8")
        for permission in UPDATER_PERMISSIONS:
            present = declared(text, permission)
            if flavor == "play" and present:
                print(f"{manifest}: Play manifest declares {permission}", file=sys.stderr)
                failed = True
            if flavor == "github" and not present:
                print(f"{manifest}: GitHub manifest is missing {permission}", file=sys.stderr)
                failed = True
        for component in UPDATER_COMPONENTS:
            present = declared(text, component)
            if flavor == "play" and present:
                print(f"{manifest}: Play manifest declares {component}", file=sys.stderr)
                failed = True
            if flavor == "github" and not present:
                print(f"{manifest}: GitHub manifest is missing {component}", file=sys.stderr)
                failed = True
        if 'android:name="android.hardware.microphone"' not in text or 'android:required="false"' not in text:
            print(f"{manifest}: microphone must be optional", file=sys.stderr)
            failed = True
        if f'package="{APPLICATION_ID}"' not in text:
            print(f"{manifest}: package must be {APPLICATION_ID}", file=sys.stderr)
            failed = True
        print(f"checked {flavor} manifest {manifest}", file=sys.stderr)
    if failed:
        sys.exit(1)


if __name__ == "__main__":
    main(sys.argv)

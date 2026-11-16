#!/usr/bin/env python3
"""Shows what takes space in an APK, or compares two APKs.

    scripts/apk-size.py app/build/outputs/apk/release/app-release-unsigned.apk
    scripts/apk-size.py before.apk after.apk

Groups the entries into native libraries per ABI, DEX, resources, assets and the rest, by their
size inside the APK (compressed where the entry is compressed; native libraries are stored). Also
prints the classes and methods the DEX files define, read from their headers.
"""
import struct
import sys
import zipfile

MB = 1_000_000


def group(name):
    if name.startswith("lib/"):
        return "lib/" + name.split("/")[1]
    if name.endswith(".dex"):
        return "dex"
    if name.startswith("res/") or name == "resources.arsc":
        return "resources"
    if name.startswith("assets/"):
        return "assets"
    return "other"


def measure(path):
    sizes = {}
    classes = methods = 0
    with zipfile.ZipFile(path) as apk:
        for info in apk.infolist():
            key = group(info.filename)
            sizes[key] = sizes.get(key, 0) + info.compress_size
            if key == "dex":
                header = apk.open(info).read(0x70)
                methods += struct.unpack_from("<I", header, 0x58)[0]
                classes += struct.unpack_from("<I", header, 0x60)[0]
    with open(path, "rb") as f:
        total = f.seek(0, 2)
    # Zip headers, alignment and the signature block.
    sizes["other"] = sizes.get("other", 0) + total - sum(sizes.values())
    return total, sizes, classes, methods


def main(paths):
    results = [measure(p) for p in paths]
    keys = sorted({k for _, sizes, _, _ in results for k in sizes}, key=lambda k: (k == "other", k))
    one = len(results) == 1
    print(f"{'Part':<20}" + (f"{'size':>12}" if one else f"{'before':>12}{'after':>12}{'change':>13}"))
    rows = [(k, [r[1].get(k, 0) for r in results]) for k in keys]
    rows.append(("APK total", [r[0] for r in results]))
    for label, values in rows:
        cells = "".join(f"{v / MB:9.1f} MB" for v in values)
        change = ""
        if not one:
            delta = values[1] - values[0]
            pct = f" ({delta / values[0] * 100:+.0f}%)" if values[0] else ""
            change = f"  {delta / MB:+8.1f} MB{pct}"
        print(f"{label:<20}{cells}{change}")
    for label, index in (("DEX classes", 2), ("DEX methods", 3)):
        print(f"{label:<20}" + "".join(f"{r[index]:12,d}" for r in results))


if __name__ == "__main__":
    if len(sys.argv) not in (2, 3):
        print(__doc__.strip().splitlines()[0], file=sys.stderr)
        print("usage: scripts/apk-size.py <apk> [<other.apk>]", file=sys.stderr)
        sys.exit(2)
    main(sys.argv[1:])

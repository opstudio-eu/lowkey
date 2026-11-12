"""Build the local design demo. No app files, downloads or external writes.

Usage: python docs/design/lowkey-ux/build-prototype.py --signal-root <checkout>
The fonts stay in ignored artifacts, with their source license texts.
"""
import argparse
import base64
import json
import re
from pathlib import Path
import xml.etree.ElementTree as ET


def build():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--signal-root", type=Path, required=True)
    args = parser.parse_args()
    here = Path(__file__).resolve().parent
    root = here.parents[2]
    signal = args.signal_root.resolve()
    fonts = [signal / "public/fonts/instrument-sans-latin.woff2",
             signal / "public/fonts/bricolage-grotesque.woff2"]
    # Keep the private checkout path out of generated artifacts and error messages.
    if not all(p.is_file() for p in fonts):
        parser.error("The Signal checkout must contain both referenced font files.")
    licenses = [signal / "public/fonts/INSTRUMENT-SANS-LICENSE.txt",
                signal / "docs/branding/new-opstudio/BRICOLAGE-LICENSE.txt"]
    if not all(p.is_file() for p in licenses):
        parser.error("Both source font license texts are required; inspect Signal's font licenses.")
    android = "{http://schemas.android.com/apk/res/android}"
    vector = ET.parse(root / "app/src/main/res/drawable/opstudio_logo_blue.xml")
    paths = [p.attrib[android + "pathData"] for p in vector.iter("path")]
    if len(paths) != 4:
        raise ValueError("Expected the four original OpStudio logo paths.")
    # Exact source geometry, only context colour follows the shell.
    mark = '<svg viewBox="100 185 801 640" aria-hidden="true">' + "".join(
        f'<path fill="currentColor" d="{path}"/>' for path in paths) + "</svg>"
    html = (here / "prototype.html").read_text(encoding="utf-8")
    for token, filename in (("STYLES", "prototype.css"), ("SCRIPT", "prototype.js"),
                            ("STARTUP_STYLES", "startup.css"), ("STARTUP_SCRIPT", "startup.js")):
        html = html.replace("__" + token + "__", (here / filename).read_text(encoding="utf-8"))
    for name, path in zip(("INSTRUMENT", "BRICOLAGE"), fonts):
        html = html.replace("__" + name + "__", base64.b64encode(path.read_bytes()).decode("ascii"))
    html = html.replace("__LOGO__", mark)
    # Read the existing project icons; do not add an icon dependency or draw replacements.
    icon_source = (root / "app/src/main/java/com/rheerkens/androidkeyboard/ui/SlimIcons.kt").read_text(encoding="utf-8")
    icons = {}
    for name in ("Home", "Mic", "Book", "Settings", "Back", "ChevronRight", "Check", "Close", "Plus", "Edit", "Trash", "Download"):
        definition = re.search(r"val " + name + r" = stroke\((.*?)(?:\n    \)|\)\n)", icon_source, re.S)
        if not definition:
            raise ValueError("Missing project icon: " + name)
        parts = re.findall(r'"([^"\n]*)"|circle\(([\d.]+)f, ([\d.]+)f, ([\d.]+)f\)', definition.group(1))[1:]
        paths = []
        for literal, cx, cy, radius in parts:
            if literal:
                paths.append(literal)
            else:
                cx, cy, radius = float(cx), float(cy), float(radius)
                paths.append(f"M{cx-radius:g} {cy:g}a{radius:g} {radius:g} 0 1 0 {2*radius:g} 0a{radius:g} {radius:g} 0 1 0 {-2*radius:g} 0")
        if not paths:
            raise ValueError("Missing paths for project icon: " + name)
        icons[name] = '<svg viewBox="0 0 24 24" aria-hidden="true">' + ''.join(
            '<path fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" d="' + path + '"/>' for path in paths) + '</svg>'
    html = html.replace("__ICONS__", json.dumps(icons))
    output = root / "artifacts/lowkey-ux"
    output.mkdir(parents=True, exist_ok=True)
    (output / "prototype.html").write_text(html, encoding="utf-8")
    for i, path in enumerate(licenses):
        (output / f"font-license-{i + 1}.txt").write_bytes(path.read_bytes())
    print("Built artifacts/lowkey-ux/prototype.html (self-contained design demo).")


if __name__ == "__main__":
    build()

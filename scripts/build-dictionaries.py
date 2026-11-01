#!/usr/bin/env python3
"""Builds the keyboard's word lists from the AOSP LatinIME dictionaries (Apache 2.0).

Downloads the pinned `*_wordlist.combined.gz` files, verifies their SHA-256, and writes
`app/src/main/assets/dict/<locale>.dict` (gzip; not named `.gz` because the Android build unpacks
`.gz` assets) with one entry per line:

    word<TAB>frequency(0-255)

followed by a `#whitelist` section of safe autocorrections:

    from<TAB>to

Offensive and "not a word" entries are dropped from the word list. From the AOSP whitelist only
contractions (`ill` -> `I'll`) and corrections of non-words (`im` -> `I'm`) are kept; real-word
swaps such as `hid` -> `his` are left out.

Usage: scripts/build-dictionaries.py   (run from the repo root; needs network)
"""
import gzip
import hashlib
import io
import os
import sys
import urllib.request

COMMIT = "127336e9f29d69607eab55982324b210279ae8c5"
BASE = f"https://android.googlesource.com/platform/packages/inputmethods/LatinIME/+/{COMMIT}/dictionaries"
SOURCES = {
    "en_US": ("en_US_wordlist.combined.gz", "0f78dd455b532be169a23f233227b811fabced4b5bd7fc9c40cc05839793bcbd"),
    "nl": ("nl_wordlist.combined.gz", "a647ed5fdd846d3240572c9cb10fb8c30880df2ebefea6184a378adb713cd0cd"),
}
OUT_DIR = os.path.join("app", "src", "main", "assets", "dict")
NOTICE = f"""Word lists derived from the Android Open Source Project LatinIME dictionaries
(platform/packages/inputmethods/LatinIME, commit {COMMIT}).

Copyright (c) 2008, The Android Open Source Project
Licensed under the Apache License, Version 2.0: https://www.apache.org/licenses/LICENSE-2.0

Changes: converted to word/frequency lists; offensive and non-word entries removed;
whitelist reduced to contractions and corrections of non-words.
"""


def fetch(name: str, sha256: str) -> bytes:
    import base64
    with urllib.request.urlopen(f"{BASE}/{name}?format=TEXT", timeout=60) as r:
        data = base64.b64decode(r.read())
    actual = hashlib.sha256(data).hexdigest()
    if actual != sha256:
        sys.exit(f"{name}: SHA-256 mismatch {actual}")
    return data


def parse(data: bytes):
    words, whitelist = {}, {}
    current = None
    lines = gzip.decompress(data).decode("utf-8").splitlines()
    valid_lower = set()
    for raw in lines:
        if raw.startswith(" word=") and "not_a_word=true" not in raw and "offensive" not in raw:
            valid_lower.add(raw.split(",", 1)[0][len(" word="):].lower())
    for raw in lines:
        if raw.startswith(" word="):
            fields = dict(part.split("=", 1) for part in raw.strip().split(",") if "=" in part)
            current = fields
            word = fields["word"]
            flags = fields.get("flags", "")
            if fields.get("not_a_word") == "true" or "offensive" in flags:
                continue
            freq = int(fields.get("f", "0") or 0)
            if word not in words or words[word] < freq:
                words[word] = freq
        elif raw.startswith("  shortcut=") and current is not None:
            fields = dict(part.split("=", 1) for part in raw.strip().split(",") if "=" in part)
            if fields.get("f") != "whitelist":
                continue
            source, target = current["word"], fields["shortcut"]
            # Keep contractions and fixes for non-words, but never rewrite a valid word (`ill`).
            if current.get("not_a_word") == "true" or ("'" in target and source.lower() not in valid_lower):
                whitelist[source] = target
    return words, whitelist


def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    for locale, (name, sha256) in SOURCES.items():
        words, whitelist = parse(fetch(name, sha256))
        buf = io.StringIO()
        for word, freq in sorted(words.items(), key=lambda kv: (-kv[1], kv[0])):
            buf.write(f"{word}\t{freq}\n")
        buf.write("#whitelist\n")
        for source, target in sorted(whitelist.items()):
            buf.write(f"{source}\t{target}\n")
        path = os.path.join(OUT_DIR, f"{locale}.dict")
        with gzip.GzipFile(path, "wb", mtime=0) as f:
            f.write(buf.getvalue().encode("utf-8"))
        print(f"{path}: {len(words)} words, {len(whitelist)} whitelist entries, {os.path.getsize(path)} bytes")
    with open(os.path.join(OUT_DIR, "NOTICE"), "w") as f:
        f.write(NOTICE)


if __name__ == "__main__":
    main()

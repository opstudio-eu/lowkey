#!/usr/bin/env python3
"""Builds the keyboard's bundled word lists from the AOSP LatinIME dictionaries (Apache 2.0).

Reads the language catalog `app/src/main/assets/dict/languages.txt`. For every language marked
`bundled = true` it downloads the pinned `<id>_wordlist.combined.gz`, verifies its size and SHA-256,
and writes `app/src/main/assets/dict/<id>.dict` (gzip; not named `.gz` because the Android build
unpacks `.gz` assets) with one entry per line:

    word<TAB>frequency(0-255)

followed by a `#whitelist` section of safe autocorrections:

    from<TAB>to

Offensive and "not a word" entries are dropped from the word list. From the AOSP whitelist only
contractions (`ill` -> `I'll`) and corrections of non-words (`im` -> `I'm`) are kept; real-word
swaps such as `hid` -> `his` are left out. At most MAX_WORDS words are kept, the most frequent.

The app converts downloaded (not bundled) languages on the phone with the same rules
(`AospWordList.kt`); `scripts/test_build_dictionaries.py` and `AospWordListTest` check that both
give the same output for one fixture.

Usage: scripts/build-dictionaries.py            (run from the repo root; needs network)
       scripts/build-dictionaries.py --check    (also verify every downloadable language's pin)
"""
import gzip
import hashlib
import io
import os
import sys
import urllib.request

MAX_WORDS = 250_000
OUT_DIR = os.path.join("app", "src", "main", "assets", "dict")
CATALOG = os.path.join(OUT_DIR, "languages.txt")
NOTICE = """Word lists derived from the Android Open Source Project LatinIME dictionaries
(platform/packages/inputmethods/LatinIME, commit {commit}).

Copyright (c) 2008, The Android Open Source Project
Licensed under the Apache License, Version 2.0: https://www.apache.org/licenses/LICENSE-2.0

Bundled in the APK: {bundled}.
Downloaded on request from the same commit and converted on the phone: {downloaded}.

Changes: converted to word/frequency lists of at most {max_words} words; offensive and non-word
entries removed; whitelist reduced to contractions and corrections of non-words.
"""


def read_catalog(path: str = CATALOG):
    """Returns (source, [section dicts with an "id" key]) from languages.txt."""
    source, sections = None, []
    with open(path, encoding="utf-8") as f:
        for raw in f:
            line = raw.strip()
            if not line or line.startswith("#"):
                continue
            if line.startswith("[") and line.endswith("]"):
                sections.append({"id": line[1:-1].strip()})
                continue
            key, _, value = line.partition("=")
            if sections:
                sections[-1][key.strip()] = value.strip()
            elif key.strip() == "source":
                source = value.strip()
    if not source or not source.startswith("https://"):
        sys.exit(f"{path}: no HTTPS source")
    return source, sections


def fetch(source: str, lang: dict) -> bytes:
    import base64
    name = f"{lang['id']}_wordlist.combined.gz"
    with urllib.request.urlopen(f"{source}/{name}?format=TEXT", timeout=60) as r:
        data = base64.b64decode(r.read())
    actual = hashlib.sha256(data).hexdigest()
    if actual != lang["sha256"] or len(data) != int(lang["size"]):
        sys.exit(f"{name}: expected {lang['size']} bytes, SHA-256 {lang['sha256']}; got {len(data)}, {actual}")
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


def render(words: dict, whitelist: dict) -> str:
    buf = io.StringIO()
    for word, freq in sorted(words.items(), key=lambda kv: (-kv[1], kv[0]))[:MAX_WORDS]:
        buf.write(f"{word}\t{freq}\n")
    buf.write("#whitelist\n")
    for source, target in sorted(whitelist.items()):
        buf.write(f"{source}\t{target}\n")
    return buf.getvalue()


def main():
    check_all = "--check" in sys.argv[1:]
    source, langs = read_catalog()
    commit = source.split("/+/", 1)[1].split("/", 1)[0]
    os.makedirs(OUT_DIR, exist_ok=True)
    for lang in langs:
        bundled = lang.get("bundled") == "true"
        if not bundled and not check_all:
            continue
        data = fetch(source, lang)
        if not bundled:
            print(f"{lang['id']}: pin OK ({len(data)} bytes)")
            continue
        words, whitelist = parse(data)
        path = os.path.join(OUT_DIR, f"{lang['id']}.dict")
        with gzip.GzipFile(path, "wb", mtime=0) as f:
            f.write(render(words, whitelist).encode("utf-8"))
        print(f"{path}: {len(words)} words, {len(whitelist)} whitelist entries, {os.path.getsize(path)} bytes")
    with open(os.path.join(OUT_DIR, "NOTICE"), "w") as f:
        f.write(NOTICE.format(
            commit=commit,
            bundled=", ".join(l["id"] for l in langs if l.get("bundled") == "true"),
            downloaded=", ".join(l["id"] for l in langs if l.get("bundled") != "true"),
            max_words=f"{MAX_WORDS:,}",
        ))


if __name__ == "__main__":
    main()

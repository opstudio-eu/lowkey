#!/usr/bin/env python3
"""Fails when public content would expose private details or secrets.

    scripts/check-leaks.py files                  # tracked files in the working tree
    scripts/check-leaks.py commits <rev-args>     # metadata and changed files of each commit
    scripts/check-leaks.py apk <file.apk>         # every entry of a built APK (network, key, token
                                                  # and private-list rules)
    scripts/check-leaks.py tag <rev>              # an annotated tag's tagger and message (nested too)
    scripts/check-leaks.py text <label>           # text on stdin
    scripts/check-leaks.py github-pr              # title, body and branch of the pull request
                                                  # in $GITHUB_EVENT_PATH (never via env or args)
    scripts/check-leaks.py allow <path>...        # record reviewed files in .leak-allowlist

Options: --require-denylist fails when the private list is missing (the pre-push hook uses it).

General patterns cover private network names and addresses, home-directory paths, personal email
addresses, keys and tokens, media, keystores and unreviewed binary files. A private list of exact
strings (one per line, case-insensitive) adds values only the development machine knows. It lives
outside the repository: $LEAK_DENYLIST, or ~/.config/lowkey/leak-denylist.

.leak-allowlist records files that were reviewed by hand, by SHA-256 and path: third-party license
texts that contain their authors' email addresses, and binary assets. A listed file skips the
general patterns while its content is unchanged; the private list always applies.

Findings name the location and the rule, never the matched text, because CI logs are public. Paths
that themselves match a rule are replaced by a placeholder. Exit status: 0 clean, 1 findings, 2 error.
"""
import hashlib
import json
import os
import re
import subprocess
import sys
from pathlib import Path

RULES = [
    ("tailnet hostname", re.compile(r"[a-z0-9-]+\.(?:ts\.net|tailscale\.net)\b", re.I)),
    ("Tailscale address", re.compile(
        r"(?<![\d.])100\.(?:6[4-9]|[7-9]\d|1[01]\d|12[0-7])\.\d{1,3}\.\d{1,3}(?![\d.])|\bfd7a:115c:a1e0:", re.I)),
    ("private network address", re.compile(
        r"(?<![\d.])(?:192\.168|172\.(?:1[6-9]|2\d|3[01])|10\.\d{1,3})\.\d{1,3}\.\d{1,3}(?![\d.])"
        r"|\b(?:[a-z0-9-]+\.)+(?:local|lan|internal|home\.arpa)\b(?![.-])", re.I)),
    ("home-directory path", re.compile(
        r"(?:/home|/Users)/(?!runner\b)[A-Za-z0-9._-]+|~[A-Za-z][A-Za-z0-9._-]*/"
        r"|[A-Za-z]:(?:\\\\?|/)Users(?:\\\\?|/)[A-Za-z0-9._-]+")),
    ("personal email address", re.compile(r"[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(?:\.[A-Za-z0-9-]+)*\.[A-Za-z]{2,}")),
    ("private key", re.compile(r"-----BEGIN (?:[A-Z]+ )*PRIVATE KEY-----")),
    ("access token", re.compile(
        r"\b(?:gh[pousr]_[A-Za-z0-9]{36}|github_pat_[A-Za-z0-9_]{40,}|AKIA[0-9A-Z]{16}|AIza[0-9A-Za-z_-]{35}"
        r"|sk-[A-Za-z0-9_-]{32,}|hf_[A-Za-z0-9]{30,}|xox[abpr]-[A-Za-z0-9-]{10,}|tskey-[A-Za-z0-9-]{10,})\b")),
]
# Public by design: GitHub's no-reply addresses, the attribution trailer, documentation domains.
ALLOWED_EMAIL = re.compile(
    r"^(?:[A-Za-z0-9._+-]+@users\.noreply\.github\.com|noreply@github\.com|noreply@anthropic\.com"
    r"|[A-Za-z0-9._%+-]+@example\.(?:com|org|net))$", re.I)
ALLOWED_AUTHOR = re.compile(r"^(?:[A-Za-z0-9._+-]+@users\.noreply\.github\.com|noreply@github\.com)$", re.I)
MEDIA = re.compile(r"\.(?:png|jpe?g|gif|webp|bmp|heic|svg|mp4|mov|webm|mkv|avi|mp3|m4a|ogg|opus|flac|wav|pdf)$", re.I)
SECRET_FILE = re.compile(r"\.(?:jks|keystore|p12|pfx|pem|key)$|(?:^|/)local\.properties$|(?:^|/)\.env(?:\.|$)", re.I)
ALLOWLIST = ".leak-allowlist"
EMPTY_TREE = "4b825dc642cb6eb9a060e54bf8d69288fbee4904"
DENYLIST_PATH = Path(os.environ.get("LEAK_DENYLIST") or Path.home() / ".config/lowkey/leak-denylist")


def load_denylist():
    # CI passes the list as a secret in $LEAK_DENYLIST_TEXT; locally it is a file.
    text = os.environ.get("LEAK_DENYLIST_TEXT")
    if text is None:
        if not DENYLIST_PATH.is_file():
            return []
        text = DENYLIST_PATH.read_text(encoding="utf-8")
    lines = text.splitlines()
    return [line.strip().lower() for line in lines if line.strip() and not line.lstrip().startswith("#")]


DENYLIST = load_denylist()


def rules_hit(text):
    """Names of the rules that match [text]."""
    hits = []
    for name, pattern in RULES:
        for m in pattern.finditer(text):
            if name == "personal email address" and ALLOWED_EMAIL.match(m.group(0)):
                continue
            hits.append(name)
            break
    return hits + denylist_hit(text)


def denylist_hit(text):
    lowered = text.lower()
    return ["private denylist entry"] if any(entry in lowered for entry in DENYLIST) else []


def binary_strings(data):
    """Printable runs in [data] as ASCII/UTF-8 and UTF-16 text, for scanning binaries."""
    runs = [m.group(0).decode("ascii") for m in re.finditer(rb"[\x20-\x7e]{4,}", data)]
    # UTF-16 in either byte order. Each pattern anchors on the NUL byte of its own order, so a run
    # keeps its first and last characters.
    runs += [m.group(0).decode("utf-16-le") for m in re.finditer(rb"(?:[\x20-\x7e]\x00){4,}", data)]
    runs += [m.group(0).decode("utf-16-be") for m in re.finditer(rb"(?:\x00[\x20-\x7e]){4,}", data)]
    return runs


def raw_denylist_hit(data):
    """True when a private entry appears in [data] as UTF-8 or UTF-16 (either byte order)."""
    lowered = data.lower()
    if any(entry.encode(codec) in lowered for entry in DENYLIST for codec in ("utf-8", "utf-16-le", "utf-16-be")):
        return True
    if any(not entry.isascii() for entry in DENYLIST):
        # bytes.lower() only folds ASCII; decode to fold other letters, at both alignments.
        texts = [data.decode("utf-8", "ignore").lower()]
        texts += [data[o:].decode(c, "ignore").lower() for c in ("utf-16-le", "utf-16-be") for o in (0, 1)]
        return any(entry in text for entry in DENYLIST for text in texts)
    return False


def git(*args, binary=False, check=True):
    result = subprocess.run(["git", *args], capture_output=True)
    if result.returncode != 0 and check:
        sys.stderr.write(f"check-leaks: git {args[0]} failed\n")
        sys.exit(2)
    return result.stdout if binary else result.stdout.decode("utf-8", "replace")


def parse_allowlist(text):
    entries = {}
    for line in text.splitlines():
        line = line.strip()
        if line and not line.startswith("#"):
            digest, _, path = line.partition(" ")
            entries[path.strip()] = digest
    return entries


class Checker:
    def __init__(self):
        self.findings = []
        self.hidden_paths = {}

    def report(self, finding):
        self.findings.append(finding)

    def show(self, path):
        """[path] for printing, or a placeholder when the path itself would leak."""
        if rules_hit(path) or not path.isprintable():
            return self.hidden_paths.setdefault(path, f"<hidden path {len(self.hidden_paths) + 1}>")
        return path

    def check_path(self, path, where):
        shown = self.show(path)
        for hit in rules_hit(path):
            self.report(f"{where}{shown}: file name contains a {hit}")
        if not path.isprintable():
            self.report(f"{where}{shown}: file name contains control characters")
        if SECRET_FILE.search(path):
            self.report(f"{where}{shown}: key, credential or local configuration file")

    def check_content(self, path, data, allowlist, where=""):
        """Checks one file's bytes. [allowlist] maps reviewed paths to their SHA-256."""
        shown = self.show(path)
        self.check_path(path, where)
        reviewed = allowlist.get(path) == hashlib.sha256(data).hexdigest()
        is_binary = b"\0" in data or MEDIA.search(path)
        if not is_binary:
            try:
                text = data.decode("utf-8")
            except UnicodeDecodeError:
                is_binary = True
        if is_binary:
            if not reviewed:
                kind = "media file" if MEDIA.search(path) else "binary file"
                self.report(f"{where}{shown}: unreviewed {kind} (review it, then scripts/check-leaks.py allow)")
            strings = binary_strings(data)
            for hit in sorted({h for s in strings for h in (denylist_hit(s) if reviewed else rules_hit(s))}):
                self.report(f"{where}{shown}: binary contains a {hit}")
            if raw_denylist_hit(data):
                self.report(f"{where}{shown}: binary contains a private denylist entry")
            return
        for number, line in enumerate(text.splitlines(), 1):
            for hit in (denylist_hit(line) if reviewed else rules_hit(line)):
                self.report(f"{where}{shown}:{number}: {hit}")

    def check_files(self):
        allowlist = parse_allowlist(Path(ALLOWLIST).read_text() if Path(ALLOWLIST).is_file() else "")
        for path in git("ls-files", "-z").split("\0"):
            if path:
                try:
                    data = Path(path).read_bytes()
                except OSError:
                    continue
                self.check_content(path, data, allowlist)

    def check_commits(self, rev_args):
        for sha in git("rev-list", *rev_args).split():
            where = f"commit {sha[:12]}: "
            fields = git("show", "-s", "--format=%ae%x00%ce%x00%an%x00%cn%x00%B", sha).split("\0", 4)
            author, committer, author_name, committer_name, message = fields
            for role, email in (("author", author), ("committer", committer)):
                if not ALLOWED_AUTHOR.match(email):
                    self.report(f"{where}{role} email is not a GitHub no-reply address")
                for hit in denylist_hit(email):
                    self.report(f"{where}{role} email contains a {hit}")
            for role, name in (("author", author_name), ("committer", committer_name)):
                for hit in rules_hit(name):
                    self.report(f"{where}{role} name contains a {hit}")
            for number, line in enumerate(message.splitlines(), 1):
                for hit in rules_hit(line):
                    self.report(f"{where}message line {number}: {hit}")
            allowlist = parse_allowlist(git("show", f"{sha}:{ALLOWLIST}", check=False))
            parents = git("show", "-s", "--format=%P", sha).split() or [EMPTY_TREE]
            changed = set()
            for parent in parents:  # every side of a merge, so evil merges are covered too
                changed.update(p for p in git("diff", "--name-only", "--no-renames", "--diff-filter=d", "-z",
                                              parent, sha).split("\0") if p)
            for path in sorted(changed):
                data = git("cat-file", "blob", f"{sha}:{path}", binary=True, check=False)
                self.check_content(path, data, allowlist, where)

    def check_tag(self, rev):
        """Tagger and message of an annotated tag, and of every tag it points to."""
        sha = git("rev-parse", "--verify", rev).strip()
        while git("cat-file", "-t", sha).strip() == "tag":
            where = f"tag {sha[:12]}: "
            raw = git("cat-file", "tag", sha)
            header, _, message = raw.partition("\n\n")
            target = ""
            for line in header.splitlines():
                key, _, value = line.partition(" ")
                if key == "object":
                    target = value.strip()
                elif key == "tagger":
                    m = re.match(r"(.*?) <([^>]*)>", value)
                    name, email = (m.group(1), m.group(2)) if m else (value, "")
                    if not ALLOWED_AUTHOR.match(email):
                        self.report(f"{where}tagger email is not a GitHub no-reply address")
                    for hit in denylist_hit(email):
                        self.report(f"{where}tagger email contains a {hit}")
                    for hit in rules_hit(name):
                        self.report(f"{where}tagger name contains a {hit}")
                elif key == "tag":
                    for hit in rules_hit(value):
                        self.report(f"{where}tag name contains a {hit}")
            for number, line in enumerate(message.splitlines(), 1):
                for hit in rules_hit(line):
                    self.report(f"{where}message line {number}: {hit}")
            sha = target

    def check_archive(self, path):
        """Every entry of a built APK or zip. Upstream notices and prebuilt libraries legitimately
        contain authors' emails and their build machines' paths, so only the private list and the
        network, key and token rules apply."""
        import zipfile
        rules = [r for r in RULES if r[0] not in ("personal email address", "home-directory path")]
        with zipfile.ZipFile(path) as archive:
            for info in archive.infolist():
                data = archive.read(info)
                shown = self.show(info.filename)
                name_hits = {n for n, pattern in rules if pattern.search(info.filename)}
                name_hits.update(denylist_hit(info.filename))
                for hit in sorted(name_hits):
                    self.report(f"{Path(path).name}: {shown}: entry name contains a {hit}")
                texts = binary_strings(data) if b"\0" in data else data.decode("utf-8", "replace").splitlines()
                hits = {name for text in texts for name, pattern in rules if pattern.search(text)}
                if raw_denylist_hit(data) or any(denylist_hit(t) for t in texts):
                    hits.add("private denylist entry")
                for hit in sorted(hits):
                    self.report(f"{Path(path).name}: {shown}: contains a {hit}")

    def check_text(self, label, text):
        for number, line in enumerate(text.splitlines(), 1):
            for hit in rules_hit(line):
                self.report(f"{label} line {number}: {hit}")


def allow(paths):
    entries = parse_allowlist(Path(ALLOWLIST).read_text() if Path(ALLOWLIST).is_file() else "")
    for path in paths:
        entries[path] = hashlib.sha256(Path(path).read_bytes()).hexdigest()
    header = ("# Files reviewed by hand for private details: SHA-256, then path. A listed file skips the\n"
              "# general leak patterns while unchanged (see scripts/check-leaks.py). Review before adding.\n")
    Path(ALLOWLIST).write_text(header + "".join(f"{d} {p}\n" for p, d in sorted(entries.items())))


def main():
    args = sys.argv[1:]
    require_denylist = "--require-denylist" in args
    args = [a for a in args if a != "--require-denylist"]
    if not args or args[0] not in ("files", "commits", "tag", "apk", "text", "github-pr", "allow"):
        sys.stderr.write(__doc__)
        return 2
    if require_denylist and not DENYLIST:
        sys.stderr.write(f"check-leaks: the private denylist is missing or empty ({DENYLIST_PATH}).\n")
        return 2
    mode, rest = args[0], args[1:]
    if mode == "allow":
        allow(rest)
        return 0
    checker = Checker()
    if mode == "files":
        checker.check_files()
    elif mode == "commits":
        if not rest:
            sys.stderr.write("usage: check-leaks.py commits <rev-list arguments>\n")
            return 2
        checker.check_commits(rest)
    elif mode == "apk":
        if len(rest) != 1:
            sys.stderr.write("usage: check-leaks.py apk <file>\n")
            return 2
        checker.check_archive(rest[0])
    elif mode == "tag":
        if len(rest) != 1:
            sys.stderr.write("usage: check-leaks.py tag <rev>\n")
            return 2
        checker.check_tag(rest[0])
    elif mode == "github-pr":
        event = json.loads(Path(os.environ["GITHUB_EVENT_PATH"]).read_text(encoding="utf-8"))
        pr = event.get("pull_request") or {}
        checker.check_text("pull request branch", (pr.get("head") or {}).get("ref") or "")
        checker.check_text("pull request title", pr.get("title") or "")
        checker.check_text("pull request body", pr.get("body") or "")
    else:
        checker.check_text(rest[0] if rest else "text", sys.stdin.read())
    findings = list(dict.fromkeys(checker.findings))
    for finding in findings:
        print(f"leak: {finding}")
    if findings:
        print(f"check-leaks: {len(findings)} finding(s). Remove the content; see AGENTS.md.", file=sys.stderr)
        return 1
    print(f"check-leaks: {mode} clean" + (" (with private denylist)" if DENYLIST else ""), file=sys.stderr)
    return 0


if __name__ == "__main__":
    sys.exit(main())

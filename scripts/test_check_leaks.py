#!/usr/bin/env python3
"""Regression tests for scripts/check-leaks.py and the pre-push hook. Run: python3 -I scripts/test_check_leaks.py

Fake leaks are assembled at runtime, so this file itself passes the leak check.
"""
import json
import os
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SCRIPT = ROOT / "scripts" / "check-leaks.py"
HOOK = ROOT / ".githooks" / "pre-push"
AT = "@"
EMAIL = "bob" + AT + "corp-mail.nl"
TAILNET = "box.tail" + "1234.ts" + ".net"
TS_IP = "100." + "101.1.2"
HOME = "/ho" + "me/alice/notes"
SECRET = "very-private-" + "marker"
NOREPLY = "1+me" + AT + "users.noreply.github.com"


class LeakCheckTest(unittest.TestCase):
    def setUp(self):
        self.dir = Path(tempfile.mkdtemp(prefix="leaktest-"))
        self.denylist = self.dir / "denylist"
        self.denylist.write_text("# test\n" + SECRET + "\n")
        self.repo = self.dir / "repo"
        self.repo.mkdir()
        self.git("init", "-q", "-b", "main")
        self.git("config", "user.name", "me")
        self.git("config", "user.email", NOREPLY)

    def tearDown(self):
        shutil.rmtree(self.dir)

    def env(self, **extra):
        env = dict(os.environ, LEAK_DENYLIST=str(self.denylist), GIT_CONFIG_NOSYSTEM="1")
        env.pop("LEAK_DENYLIST_TEXT", None)  # CI's secret list must not replace the test list
        env.update(extra)
        return env

    def git(self, *args, **kw):
        return subprocess.run(["git", *args], cwd=self.repo, check=True, capture_output=True, text=True,
                              env=self.env(), **kw).stdout

    def check(self, *args, stdin="", **env):
        r = subprocess.run([sys.executable, "-I", str(SCRIPT), *args], cwd=self.repo, input=stdin,
                           capture_output=True, text=True, env=self.env(**env))
        return r.returncode, r.stdout

    def commit(self, files, message="change", email=NOREPLY):
        for name, content in files.items():
            path = self.repo / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(content if isinstance(content, bytes) else content.encode())
        self.git("add", "-A")
        self.git("-c", f"user.email={email}", "commit", "-qm", message)
        return self.git("rev-parse", "HEAD").strip()

    def assertLeak(self, result, *needles):
        code, out = result
        self.assertEqual(code, 1, out)
        for needle in needles:
            self.assertIn(needle, out)
        for private in (EMAIL, TAILNET, TS_IP, HOME, SECRET):
            self.assertNotIn(private, out, "findings must never print the matched text")

    def test_clean_repository_passes(self):
        self.commit({"a.txt": "version 1.31.0 and 10.0, mail " + NOREPLY + "\n"})
        self.assertEqual(self.check("files")[0], 0)
        self.assertEqual(self.check("commits", "HEAD")[0], 0)

    def test_general_patterns(self):
        lines = [TAILNET, TS_IP, "192." + "168.1.20", HOME, EMAIL, "noreply" + AT + "evil.example.co",
                 "fd7a:" + "115c:a1e0::1", "C:\\\\Users\\\\alice", "ghp_" + "a" * 36, SECRET.upper()]
        self.commit({"notes.txt": "\n".join(lines) + "\n"})
        code, out = self.check("files")
        self.assertEqual(code, 1)
        self.assertEqual(out.count("notes.txt:"), len(lines), out)

    def test_diff_header_lookalike_is_scanned(self):
        # A line starting with "++ " shows as "+++ " in a diff; whole-file scanning must not skip it.
        self.commit({"a.txt": "start\n"})
        self.commit({"a.txt": "start\n++ " + EMAIL + "\n"})
        self.assertLeak(self.check("commits", "HEAD", "^HEAD~1"), "a.txt:2: personal email address")

    def test_leaking_file_name_is_hidden(self):
        self.commit({EMAIL + ".txt": "ok\n"})
        self.assertLeak(self.check("files"), "<hidden path 1>")
        self.assertLeak(self.check("commits", "HEAD"), "<hidden path 1>")

    def test_binaries_need_review_and_are_scanned(self):
        utf16 = SECRET.encode("utf-16-le")
        self.commit({"blob.bin": b"\0\1" + EMAIL.encode() + b"\0" + utf16, "shot.png": b"\x89PNG"})
        code, out = self.check("files")
        self.assertLeak((code, out), "blob.bin: unreviewed binary file", "blob.bin: binary contains a personal email",
                        "blob.bin: binary contains a private denylist entry", "shot.png: unreviewed media file")

    def test_utf16_big_endian_denylist_entry_in_reviewed_binary(self):
        self.commit({"blob.bin": b"\0\1" + SECRET.encode("utf-16-be")})
        self.assertEqual(self.check("allow", "blob.bin")[0], 0)
        self.assertLeak(self.check("files"), "blob.bin: binary contains a private denylist entry")

    def test_denylist_applies_to_accepted_noreply_emails(self):
        self.commit({"a.txt": "ok\n"}, email="1+" + SECRET + AT + "users.noreply.github.com")
        self.assertLeak(self.check("commits", "HEAD"), "author email contains a private denylist entry")

    def test_non_ascii_denylist_entry_in_utf16(self):
        self.denylist.write_text("Ångström-geheim\n")
        self.commit({"blob.bin": b"\0\1" + "ÅNGSTRÖM-GEHEIM".encode("utf-16-be")})
        self.assertEqual(self.check("allow", "blob.bin")[0], 0)
        code, out = self.check("files")
        self.assertEqual(code, 1, out)
        self.assertIn("blob.bin: binary contains a private denylist entry", out)

    def test_apk_mode(self):
        import zipfile
        apk = self.dir / "app.apk"
        with zipfile.ZipFile(apk, "w") as z:
            z.writestr("assets/licenses/NOTICE.txt", "Author " + EMAIL + "\nbuilt in " + HOME + "\n")
            z.writestr("lib/libx.so", b"\0ELF" + b"\0" * 8 + ("see " + TAILNET).encode() + b"\0")
            z.writestr("assets/x.txt", SECRET)
        code, out = self.check("apk", str(apk))
        self.assertLeak((code, out), "lib/libx.so: contains a tailnet hostname", "assets/x.txt: contains a private denylist")
        self.assertNotIn("NOTICE.txt", out, "upstream emails and paths in notices are allowed")

    def test_apk_entry_names(self):
        import zipfile
        apk = self.dir / "app.apk"
        with zipfile.ZipFile(apk, "w") as z:
            z.writestr("assets/" + SECRET + ".txt", "clean\n")
        self.assertLeak(self.check("apk", str(apk)), "<hidden path 1>: entry name contains a private denylist")

    def test_annotated_tags(self):
        self.commit({"a.txt": "ok\n"})
        self.git("-c", "user.email=me" + AT + "gmail.com", "tag", "-a", "v1", "-m", "notes " + SECRET)
        self.git("tag", "-a", "v1-outer", "v1", "-m", "outer")  # a tag of a tag
        self.assertLeak(self.check("tag", "v1-outer"), "tagger email is not a GitHub no-reply address",
                        "message line 1: private denylist entry")
        self.git("tag", "-a", "v2", "-m", "clean")
        self.assertEqual(self.check("tag", "v2")[0], 0)

    def test_allowlist_is_bound_to_content(self):
        self.commit({"NOTICE.txt": "Author " + EMAIL + "\n"})
        self.assertEqual(self.check("files")[0], 1)
        self.assertEqual(self.check("allow", "NOTICE.txt")[0], 0)
        self.git("add", "-A")
        self.git("commit", "-qm", "allow")
        self.assertEqual(self.check("files")[0], 0)
        self.assertEqual(self.check("commits", "HEAD", "^HEAD~1")[0], 0)
        # The first commit still holds the unreviewed file, so the full history fails.
        self.assertEqual(self.check("commits", "HEAD")[0], 1)
        # The private list still applies to reviewed files, and a changed file loses its review.
        (self.repo / "NOTICE.txt").write_text("Author " + EMAIL + "\n" + SECRET + "\n")
        self.assertLeak(self.check("files"), "NOTICE.txt:1: personal email address", "NOTICE.txt:2: private denylist")

    def test_commit_metadata(self):
        self.commit({"a.txt": "ok\n"}, message="see http://" + TS_IP + ":8770", email="me" + AT + "gmail.com")
        self.assertLeak(self.check("commits", "HEAD"), "author email is not a GitHub no-reply address",
                        "message line 1: Tailscale address")

    def test_github_pr_reads_event_file(self):
        event = self.dir / "event.json"
        event.write_text(json.dumps({"pull_request": {"title": "Fix " + TAILNET, "body": "ok", "head": {"ref": "x"}}}))
        self.assertLeak(self.check("github-pr", GITHUB_EVENT_PATH=str(event)), "pull request title line 1: tailnet")

    def test_denylist_required_for_push(self):
        code, _ = self.check("--require-denylist", "files", LEAK_DENYLIST=str(self.dir / "missing"))
        self.assertEqual(code, 2)

    def hook(self, url, stdin):
        return subprocess.run(["bash", str(HOOK), "public", str(url)], cwd=self.repo, input=stdin,
                              capture_output=True, text=True, env=self.env())

    def test_pre_push_checks_history_missing_from_destination(self):
        (self.repo / "scripts").mkdir()  # the hook runs the repository's own checker
        shutil.copy(SCRIPT, self.repo / "scripts" / "check-leaks.py")
        leak = self.commit({"old.txt": EMAIL + "\n"}, message="old private work")
        tip = self.commit({"new.txt": "ok\n"})
        dest = self.dir / "dest.git"
        subprocess.run(["git", "init", "-q", "--bare", str(dest)], check=True, env=self.env())
        zero = "0" * 40
        push = f"refs/heads/main {tip} refs/heads/main {zero}\n"
        # A stale tracking ref claims the destination has the old history, but it is empty.
        self.git("update-ref", "refs/remotes/public/main", leak)
        result = self.hook(dest, push)
        self.assertEqual(result.returncode, 1, result.stdout + result.stderr)
        self.assertIn("old.txt:1: personal email address", result.stdout)
        # Once the destination really has the old commit, only the new commits are checked.
        self.git("push", "-q", str(dest), f"{leak}:refs/heads/old")
        result = self.hook(dest, push)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        # An unreachable destination excludes nothing.
        result = self.hook(self.dir / "missing.git", push)
        self.assertEqual(result.returncode, 1, result.stdout + result.stderr)

    def test_pre_push_rejects_tags_of_blobs(self):
        (self.repo / "scripts").mkdir()
        shutil.copy(SCRIPT, self.repo / "scripts" / "check-leaks.py")
        self.commit({"a.txt": "ok\n"})
        blob = subprocess.run(["git", "hash-object", "-w", "--stdin"], cwd=self.repo, input=SECRET + "\n",
                              capture_output=True, text=True, check=True, env=self.env()).stdout.strip()
        self.git("tag", "light", blob)
        self.git("tag", "-a", "heavy", blob, "-m", "a blob")
        dest = self.dir / "dest.git"
        subprocess.run(["git", "init", "-q", "--bare", str(dest)], check=True, env=self.env())
        for ref in ("light", "heavy"):
            sha = self.git("rev-parse", ref).strip()
            result = self.hook(dest, f"refs/tags/{ref} {sha} refs/tags/{ref} {'0' * 40}\n")
            self.assertEqual(result.returncode, 1, result.stdout + result.stderr)
            self.assertIn("not a commit", result.stdout)
            self.assertNotIn(ref, result.stdout.replace("refs/tags/", ""), "ref names must not be printed")

    def test_pre_push_checks_annotated_tags(self):
        (self.repo / "scripts").mkdir()
        shutil.copy(SCRIPT, self.repo / "scripts" / "check-leaks.py")
        self.commit({"a.txt": "ok\n"})
        self.git("tag", "-a", "v1", "-m", "release " + SECRET)
        tag = self.git("rev-parse", "v1").strip()
        dest = self.dir / "dest.git"
        subprocess.run(["git", "init", "-q", "--bare", str(dest)], check=True, env=self.env())
        result = self.hook(dest, f"refs/tags/v1 {tag} refs/tags/v1 {'0' * 40}\n")
        self.assertEqual(result.returncode, 1, result.stdout + result.stderr)
        self.assertIn("message line 1: private denylist entry", result.stdout)

if __name__ == "__main__":
    unittest.main(verbosity=1)

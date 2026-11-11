#!/usr/bin/env python3
"""Checks that scripts/build-dictionaries.py converts the shared fixture as expected.

The app's on-phone converter (AospWordList.kt) is checked against the same files by
AospWordListTest, so bundled and downloaded word lists use the same rules.

Usage: python3 -I scripts/test_build_dictionaries.py   (run from the repo root)
"""
import gzip
import importlib.util
import os
import unittest

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
FIXTURE = os.path.join(ROOT, "app", "src", "test", "resources", "aosp")


def load_script():
    spec = importlib.util.spec_from_file_location("build_dictionaries", os.path.join(ROOT, "scripts", "build-dictionaries.py"))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


class BuildDictionariesTest(unittest.TestCase):
    def test_fixture_converts_to_expected_output(self):
        bd = load_script()
        with open(os.path.join(FIXTURE, "sample.combined"), "rb") as f:
            words, whitelist = bd.parse(gzip.compress(f.read()))
        with open(os.path.join(FIXTURE, "sample.dict.txt"), encoding="utf-8") as f:
            expected = f.read()
        self.assertEqual(expected, bd.render(words, whitelist))

    def test_catalog_lists_bundled_languages_with_pins(self):
        bd = load_script()
        source, langs = bd.read_catalog(os.path.join(ROOT, "app", "src", "main", "assets", "dict", "languages.txt"))
        self.assertIn("/+/", source)
        bundled = [l["id"] for l in langs if l.get("bundled") == "true"]
        self.assertEqual(["en_US"], bundled)
        for lang in langs:
            self.assertEqual(64, len(lang["sha256"]), lang["id"])
            self.assertGreater(int(lang["size"]), 0, lang["id"])
            path = os.path.join(ROOT, "app", "src", "main", "assets", "dict", f"{lang['id']}.dict")
            self.assertEqual(lang.get("bundled") == "true", os.path.exists(path), lang["id"])
        bd = load_script()
        for test_list in bd.TEST_LISTS:
            self.assertTrue(os.path.exists(os.path.join(ROOT, bd.TEST_DIR, f"{test_list}.dict")), test_list)


if __name__ == "__main__":
    unittest.main()

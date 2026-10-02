import json
import unittest
from pathlib import Path

from build_review_catalog import lookup_name, reviewed_display


class DisplayTextTests(unittest.TestCase):
    def test_review_changes_display_but_keeps_original_matching(self):
        review = json.loads(Path("catalog/research/display-text-v1.json").read_text(encoding="utf-8"))["games"]
        entries = json.loads(Path("catalog/research/matches-v1.json").read_text(encoding="utf-8-sig"))["entries"]
        names = json.loads(Path("kairo98/src/main/assets/catalog/name-index-v1.json").read_text(encoding="utf-8"))
        original_names = {}
        for entry in entries:
            if entry["platform"] != "pc98":
                continue
            key = f'{entry["platform"]}:{entry["databaseId"]}'
            for candidate in [entry["title"], *entry.get("aliases", []),
                              *(group.rsplit(":", 1)[-1] for group in entry.get("sourceGroups", []))]:
                normalized = lookup_name(candidate)
                if len(normalized) >= 4:
                    original_names.setdefault(normalized, set()).add(key)
        for name, keys in original_names.items():
            if len(keys) == 1:
                self.assertEqual(names["names"].get(name), next(iter(keys)), name)
        for entry in entries:
            key = f'{entry["platform"]}:{entry["databaseId"]}'
            if key not in review:
                continue
            for field, expected in review[key]["fields"].items():
                self.assertEqual(names["games"][key][field], expected)
            if entry["platform"] == "pc98":
                for title in (entry["title"], names["games"][key]["title"]):
                    normalized = lookup_name(title)
                    # Short and ambiguous names are deliberately not indexed.
                    if len(normalized) >= 4 and normalized in names["names"]:
                        self.assertEqual(names["names"][normalized], key)
        original = {"title": "Sex 2", "launch": {"commands": ["START"]}}
        edited = reviewed_display(original, review, "pc98:183486")
        self.assertEqual(edited["title"], "S♥x 2")
        self.assertEqual(edited["launch"], original["launch"])
        self.assertEqual(original["title"], "Sex 2")

    def test_stale_review_rejected(self):
        review = {"id": {"sourceTitle": "Old", "fields": {"title": "New"}}}
        with self.assertRaisesRegex(ValueError, "no longer matches"):
            reviewed_display({"title": "Different game"}, review, "id")

    def test_review_cannot_edit_matching_or_launch_fields(self):
        review = {"id": {"sourceTitle": "Title", "fields": {"contentIds": []}}}
        with self.assertRaisesRegex(ValueError, "Invalid display review"):
            reviewed_display({"title": "Title"}, review, "id")

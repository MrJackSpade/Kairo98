import json
import tempfile
import unittest
from pathlib import Path

from build_catalog import build, compact, main, validate_record


class CatalogBuildTests(unittest.TestCase):
    def setUp(self):
        self.source = json.loads(Path("docs/fixtures/catalog/source-v1.json").read_text(encoding="utf-8"))

    def test_fixture_and_stable_output(self):
        first = build(self.source)
        second = build(json.loads(json.dumps(self.source, sort_keys=True)))
        self.assertEqual(compact(first[0]), compact(second[0]))
        self.assertEqual(first[1], second[1])
        self.assertEqual(first[0]["shards"], ["00"])

    def test_large_synthetic_index(self):
        template = self.source["datasets"][0]["games"][0]
        self.source["datasets"][0]["games"] = [
            {**template, "contentIds": [f"sha256-hdi-v1:{index:064x}"], "title": f"Disk {index}"}
            for index in range(10000)
        ]
        manifest, shards = build(self.source)
        self.assertEqual(manifest["games"], 10000)
        self.assertEqual(sum(len(shard) for shard in shards.values()), 10000)
        self.assertEqual(shards["00"][f"sha256-hdi-v1:{9999:064x}"]["title"], "Disk 9999")

    def test_conflicting_hash_requires_review(self):
        original = self.source["datasets"][0]["games"][0]
        self.source["datasets"][0]["games"].append({**original, "title": "Different title"})
        with self.assertRaisesRegex(ValueError, "conflicting identity"):
            build(self.source)

    def test_bad_command_and_art_rejected(self):
        record = self.source["datasets"][0]["games"][0]
        with self.assertRaisesRegex(ValueError, "invalid launch"):
            validate_record({**record, "launch": {"type": "guestCommand", "text": "X&Y"}})
        with self.assertRaisesRegex(ValueError, "invalid artwork"):
            validate_record({**record, "artwork": {"preview": "../outside.png"}})


if __name__ == "__main__":
    unittest.main()

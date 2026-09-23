import json
import shutil
import tempfile
import unittest
from pathlib import Path

from build_catalog import build, compact
from import_art import import_art


class ArtImportTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.assets = self.root / "assets"
        catalog = self.assets / "catalog"
        (catalog / "shards").mkdir(parents=True)
        source = json.loads(Path("docs/fixtures/catalog/source-v1.json").read_text(encoding="utf-8"))
        manifest, shards = build(source)
        (catalog / "manifest-v1.json").write_bytes(compact(manifest))
        for prefix, games in shards.items():
            (catalog / "shards" / f"{prefix}.json").write_bytes(
                compact({"schemaVersion": 1, "games": games}))
        self.art_source = json.loads(Path("docs/fixtures/art-source/source-v1.json").read_text(encoding="utf-8"))
        for item in self.art_source["assets"]:
            shutil.copy2(Path("docs/fixtures/art-source") / item["file"], self.root / item["file"])
        self.manifest_path = self.root / "art-source.json"
        self.save_manifest()

    def save_manifest(self):
        self.manifest_path.write_text(json.dumps(self.art_source), encoding="utf-8")

    def test_approved_art_is_referenced_and_counted(self):
        coverage = import_art(self.manifest_path, self.assets)
        self.assertEqual(coverage["gamesWithArt"], 1)
        self.assertEqual(coverage["boxArt"], 1)
        self.assertEqual(coverage["previews"], 1)
        self.assertGreater(coverage["packagedImageBytes"], 0)
        provenance = json.loads((self.assets / "art/provenance-v1.json").read_text(encoding="utf-8"))
        self.assertEqual(provenance["coverage"], coverage)
        self.assertEqual(len(provenance["assets"]), 2)
        for item in provenance["assets"]:
            self.assertTrue((self.assets / item["asset"]).is_file())

    def test_missing_paid_redistribution_permission_is_rejected(self):
        self.art_source["assets"][0]["freeAndPaidRedistribution"] = False
        self.save_manifest()
        with self.assertRaisesRegex(ValueError, "permission"):
            import_art(self.manifest_path, self.assets)


if __name__ == "__main__":
    unittest.main()

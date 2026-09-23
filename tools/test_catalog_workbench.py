import json
import tempfile
import unittest
import zipfile
from pathlib import Path

from catalog_workbench import candidates, groups, load_metadata, record_review, report, scan


class CatalogWorkbenchTest(unittest.TestCase):
    def test_scan_hashes_uncompressed_hdi_and_marks_floppy_only(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for name, compression in (("Game [T-En v1].zip", zipfile.ZIP_STORED),
                                      ("Game [T-En v2].zip", zipfile.ZIP_DEFLATED)):
                with zipfile.ZipFile(root / name, "w") as archive:
                    archive.writestr("disk.hdi", b"identical image" * 100, compress_type=compression)
            with zipfile.ZipFile(root / "Floppy [T-En].zip", "w") as archive:
                archive.writestr("disk.fdi", b"floppy")
            result = scan(root)
            self.assertEqual([item["status"] for item in result["archives"]],
                             ["no-hdi", "ready", "ready"])
            self.assertEqual(result["archives"][1]["disks"][0]["contentId"],
                             result["archives"][2]["disks"][0]["contentId"])
            self.assertEqual(result, scan(root, result))

    def test_review_gate_and_alias_search(self):
        with tempfile.TemporaryDirectory() as directory:
            metadata = Path(directory) / "metadata.xml"
            metadata.write_text("""<LaunchBox><Game><DatabaseID>10</DatabaseID>
                <Name>Japanese Name</Name><Overview>Source description.</Overview></Game>
                <GameAlternateName><DatabaseID>10</DatabaseID>
                <AlternateName>English Name</AlternateName></GameAlternateName>
                <GameImage><DatabaseID>10</DatabaseID><Type>Box - Front</Type></GameImage>
                </LaunchBox>""", encoding="utf-8")
            games = load_metadata(metadata)
            self.assertEqual(candidates(games, "English Name")[0]["DatabaseID"], "10")
            index = {"archives": [{"path": "English Name.zip", "titleHint": "English Name",
                                    "status": "ready", "disks": [{"entry": "disk.hdi",
                                    "contentId": "sha256-hdi-v1:" + "a" * 64}]},
                                   {"path": "Floppy.zip", "titleHint": "Floppy",
                                    "status": "no-hdi", "disks": []}]}
            queue = {"groups": groups(index, games)}
            self.assertTrue(all(group["review"] == "needs-review" for group in queue["groups"]))
            with self.assertRaises(ValueError):
                record_review(queue, {}, "floppy", "10", "https://example.com/10")
            with self.assertRaises(ValueError):
                record_review(queue, {}, "englishname", "10", "http://example.com/10")
            with self.assertRaises(ValueError):
                record_review(queue, {}, "englishname", "10", "https://example.com/10")
            decisions = record_review(queue, {}, "englishname", "10", "https://example.com/10",
                                      "Compared title and alias", "An original summary.")
            draft = report(queue, decisions, games)
            self.assertEqual(len(draft), 1)
            self.assertEqual(draft[0]["title"], "Japanese Name")
            self.assertEqual(draft[0]["contentIds"], ["sha256-hdi-v1:" + "a" * 64])
            self.assertEqual(draft[0]["draftDescriptions"], ["An original summary."])


if __name__ == "__main__":
    unittest.main()

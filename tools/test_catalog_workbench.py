import json
import tempfile
import unittest
import zipfile
from pathlib import Path

from catalog_workbench import candidates, groups, load_metadata, scan


class CatalogWorkbenchTest(unittest.TestCase):
    def test_scan_hashes_extracted_disks_and_upgrades_hdi_only_index(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for name, compression in (("Game [T-En v1].zip", zipfile.ZIP_STORED),
                                      ("Game [T-En v2].zip", zipfile.ZIP_DEFLATED)):
                with zipfile.ZipFile(root / name, "w") as archive:
                    archive.writestr("disk.hdi", b"identical image" * 100, compress_type=compression)
            with zipfile.ZipFile(root / "Floppy [T-En].zip", "w") as archive:
                archive.writestr("disk.fdi", b"floppy")
            old = {"schemaVersion": 1, "archives": [{
                "path": "Floppy [T-En].zip", "status": "no-hdi", "disks": [],
                "fingerprint": {"size": (root / "Floppy [T-En].zip").stat().st_size,
                                "mtimeNs": (root / "Floppy [T-En].zip").stat().st_mtime_ns}}]}
            result = scan(root, old)
            self.assertEqual([item["status"] for item in result["archives"]],
                             ["ready", "ready", "ready"])
            self.assertTrue(result["archives"][0]["disks"][0]["contentId"].startswith("sha256-fd-v1:"))
            self.assertEqual(result["archives"][1]["disks"][0]["contentId"],
                             result["archives"][2]["disks"][0]["contentId"])
            self.assertEqual(result, scan(root, result))

    def test_fdd_vfdd_extension_is_included_after_format_upgrade(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            path = root / "Game.zip"
            with zipfile.ZipFile(path, "w") as archive:
                archive.writestr("disk.fdd", b"VFD1.00\0" + b"example")
            old = {"schemaVersion": 1, "mediaFormatsVersion": 2, "archives": [{
                "path": path.name, "status": "no-supported-image", "disks": [],
                "fingerprint": {"size": path.stat().st_size, "mtimeNs": path.stat().st_mtime_ns}}]}
            result = scan(root, old)
            self.assertEqual(result["archives"][0]["status"], "ready")
            self.assertTrue(result["archives"][0]["disks"][0]["contentId"].startswith("sha256-fd-v1:"))

    def test_alias_search_and_grouping_without_review_state(self):
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
            self.assertEqual(len(queue["groups"]), 2)
            self.assertTrue(all("review" not in group for group in queue["groups"]))
            linked = next(group for group in queue["groups"] if group["groupId"] == "englishname")
            self.assertEqual(linked["contentIds"], ["sha256-hdi-v1:" + "a" * 64])
            self.assertEqual(linked["candidates"][0]["DatabaseID"], "10")


if __name__ == "__main__":
    unittest.main()

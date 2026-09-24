import json
import tempfile
import unittest
import zipfile
from pathlib import Path

from catalog_review import audit_flags_document, propose, validate
from extract_launchbox_snapshot import extract
from inventory_neokobe import pc88_groups, pc98_groups


class CatalogResearchTest(unittest.TestCase):
    def test_flags_document_lists_each_held_image_once(self):
        reviews = {"entries": [{"boxArt": {"review": "flagged", "url": "https://images.launchbox-app.com/a.png"},
                                "screenshot": {"review": "checked", "url": "https://images.launchbox-app.com/b.png"}}]}
        self.assertEqual(audit_flags_document(reviews,
                         "[Image](https://images.launchbox-app.com/a.png)"), 1)
        with self.assertRaises(ValueError):
            audit_flags_document(reviews, "No image links")
        with self.assertRaises(ValueError):
            audit_flags_document(reviews,
                "[Image](https://images.launchbox-app.com/a.png) "
                "[Image](https://images.launchbox-app.com/a.png)")

    def test_extracts_only_target_platforms_and_source_linked_images(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "Metadata.zip"
            xml = """<LaunchBox><Game><DatabaseID>1</DatabaseID><Platform>NEC PC-8801</Platform>
                <Name>Example</Name><Overview>Text</Overview></Game>
                <Game><DatabaseID>2</DatabaseID><Platform>MS-DOS</Platform><Name>Other</Name></Game>
                <GameAlternateName><DatabaseID>1</DatabaseID><AlternateName>Alias</AlternateName></GameAlternateName>
                <GameImage><DatabaseID>1</DatabaseID><Type>Box - Front</Type>
                <FileName>image.png</FileName></GameImage></LaunchBox>"""
            with zipfile.ZipFile(path, "w") as archive:
                archive.writestr("Metadata.xml", xml)
            result = extract(path)
            self.assertEqual(set(result["pc88"]), {"1"})
            self.assertEqual(result["pc98"], {})
            self.assertEqual(result["pc88"]["1"]["aliases"], ["Alias"])
            self.assertEqual(result["pc88"]["1"]["images"][0]["url"],
                             "https://images.launchbox-app.com/image.png")

    def test_inventories_game_folders_without_extracting_archives(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            pc88 = root / "pc88"
            pc88.mkdir()
            game = pc88 / "Game (Publisher)"
            game.mkdir()
            (game / "Game [FD].7z").write_bytes(b"not decompressed")
            self.assertEqual(pc88_groups(pc88)[0]["titleHint"], "Game")
            pc98 = root / "pc98"
            pc98.mkdir()
            with zipfile.ZipFile(pc98 / "Publisher.zip", "w") as archive:
                archive.writestr("Publisher/Game/Game [HD].zip", b"not decompressed")
            groups, errors = pc98_groups(pc98)
            self.assertEqual(errors, [])
            self.assertEqual(groups[0]["titleHint"], "Game")

    def test_review_requires_human_description_and_image_source(self):
        game = {"Name": "Example", "Overview": "source overview", "aliases": [],
                "images": [{"type": "Box - Front", "url": "https://images.launchbox-app.com/a.png"},
                           {"type": "Screenshot - Gameplay", "url": "https://images.launchbox-app.com/b.png"}],
                "imageCounts": {"Box - Front": 1, "Screenshot - Gameplay": 1}}
        snapshots = {"pc88": {"1": game}, "pc98": {}}
        backlog = {"groups": [{"platform": "pc88", "groupId": "g", "titleHint": "Example",
                               "contentIds": [], "exactCandidates": [{"DatabaseID": "1"}]}]}
        reviews = propose(backlog, snapshots, ["g"])
        with self.assertRaises(ValueError):
            validate(reviews, backlog, snapshots)
        entry = reviews["entries"][0]
        entry.update(pageUrl="https://gamesdb.launchbox-app.com/games/details/1",
                     description="An independently written description of the game.",
                     notes="Manually opened page and artwork")
        entry["boxArt"]["review"] = "checked"
        entry["screenshot"]["review"] = "checked"
        self.assertEqual(validate(reviews, backlog, snapshots), (1, 1))
        entry["boxArt"]["url"] = "https://images.launchbox-app.com/wrong.png"
        with self.assertRaises(ValueError):
            validate(reviews, backlog, snapshots)


if __name__ == "__main__":
    unittest.main()

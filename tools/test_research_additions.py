import hashlib
import json
import unittest

from add_inventory_games import entry_for as inventory_entry
from add_mobygames_games import entry_for as moby_entry, front_cover, gameplay_screenshot
from artwork_references import SELF_HOSTED_BASE, compact
from build_catalog import valid_image_url
import research_additions
from research_additions import ResearchInputs, name_matched

TEXT = """{
  "schemaVersion": 1,
  "entries": [
    {
      "platform": "pc98",
      "databaseId": "1",
      "title": "One",
      "sourceGroups": [
        "pc98:A.zip:One"
      ],
      "contentIds": []
    }
  ]
}
"""


class ResearchInputTests(unittest.TestCase):
    def inputs(self):
        research = ResearchInputs.__new__(ResearchInputs)
        research.text = TEXT
        research.matches = json.loads(TEXT)
        research.adult = {"games": {}}
        research.core_review = {"excluded": {}}
        research.linked = {"pc98:A.zip:One": "pc98:1"}
        research.cataloged = set()
        research.keys = {"pc98:1"}
        research.added = []
        return research

    def test_link_edits_only_the_entry_groups(self):
        research = self.inputs()
        research.link("pc98:1", "pc98:A.zip:One (CD)")
        self.assertEqual(json.loads(research.text)["entries"][0]["sourceGroups"],
                         ["pc98:A.zip:One", "pc98:A.zip:One (CD)"])
        with self.assertRaises(ValueError):
            research.link("pc98:1", "pc98:A.zip:One")
        with self.assertRaises(ValueError):
            research.link("pc98:2", "pc98:B.zip:Two")

    def test_adult_entry_needs_core_exclusion(self):
        research = self.inputs()
        entry = inventory_entry({"group": "pc98:B.zip:Two", "title": "Two"})
        with self.assertRaises(ValueError):
            research.add(entry, {"status": "eroge"})
        research.add(entry, {"status": "eroge"}, "adult")
        self.assertEqual(research.core_review["excluded"][f"pc98:{entry['databaseId']}"],
                         {"title": "Two", "reason": "adult"})
        with self.assertRaises(ValueError):
            research.add(inventory_entry({"group": "pc98:A.zip:One", "title": "Again"}), {"status": "unreviewed"})


class EntryTests(unittest.TestCase):
    def test_inventory_entries_are_stable_and_not_name_matched(self):
        entry = inventory_entry({"group": "pc98:Utility.zip:Assist Calc", "title": "Assist Calc"})
        digest = hashlib.sha256(b"pc98:Utility.zip:Assist Calc").hexdigest()[:12]
        self.assertEqual(entry["databaseId"], research_additions.INVENTORY_PREFIX + digest)
        self.assertFalse(name_matched(entry))
        self.assertTrue(name_matched({"databaseId": "moby-109"}))

    def test_moby_title_override_keeps_moby_title_as_alias(self):
        game = {"game_id": 4439, "title": "PGA Tour Golf 486", "moby_url": "https://www.mobygames.com/game/4439/",
                "alternate_titles": [{"title": "PGA Tour Golf III"}, {"title": "PGA Tour 96"}]}
        entry = moby_entry({"group": "pc98:EA.zip:PGA Tour Golf III", "moby": 4439,
                            "title": "PGA Tour Golf III", "description": "D"}, game)
        self.assertEqual((entry["databaseId"], entry["title"]), ("moby-4439", "PGA Tour Golf III"))
        self.assertEqual(entry["aliases"], ["PGA Tour Golf 486", "PGA Tour 96"])

    def test_cover_prefers_japanese_front(self):
        covers = {"cover_groups": [
            {"countries": ["United States"], "covers": [{"scan_of": "Front Cover", "image": "us"}]},
            {"countries": ["Japan"], "covers": [{"scan_of": "Back Cover", "image": "back"},
                                                {"scan_of": "Front Cover", "image": "jp"}]}]}
        self.assertEqual(front_cover(covers), "jp")
        self.assertIsNone(front_cover({"cover_groups": []}))

    def test_screenshot_is_mid_play(self):
        shots = [{"caption": c, "image": c} for c in
                 ("Title screen", "Main Menu", "Forest", "Boat", "Shipwreck", "Ending")]
        self.assertEqual(gameplay_screenshot({"screenshots": shots}), "Boat")
        self.assertEqual(gameplay_screenshot({"screenshots": shots[:2]}), "Main Menu")
        self.assertIsNone(gameplay_screenshot({"screenshots": []}))


class SelfHostedArtworkTests(unittest.TestCase):
    def test_self_hosted_artwork_is_kept_and_allowed(self):
        path = "art/catalog/moby/109/screenshot-0123456789ab.webp"
        art = {"preview": path, "previewUrl": SELF_HOSTED_BASE + path}
        self.assertEqual(compact(art), art)
        self.assertTrue(valid_image_url(SELF_HOSTED_BASE + path))
        for bad in ({"preview": path, "previewUrl": SELF_HOSTED_BASE + "art/catalog/moby/110/x.webp"},
                    {"boxArt": path, "boxArtUrl": SELF_HOSTED_BASE + path}):
            with self.subTest(bad=bad), self.assertRaises(ValueError):
                compact(bad)
        self.assertFalse(valid_image_url(SELF_HOSTED_BASE + "art/catalog/../../secret"))
        self.assertFalse(valid_image_url("https://raw.githubusercontent.com/Other/Repo/main/catalog/artwork/art/catalog/x.webp"))


if __name__ == "__main__":
    unittest.main()

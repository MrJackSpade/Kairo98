import unittest

from add_vndb_games import entry_for


def release(identity, has_ero, vns, titles, official="t", patch="f"):
    return {"id": identity, "has_ero": has_ero, "official": official, "patch": patch, "olang": "ja",
            "vns": set(vns), "titles": [{"lang": "ja", "title": title, "latin": latin} for title, latin in titles]}


class VndbEntryTests(unittest.TestCase):
    def setUp(self):
        self.releases = {"r1": release("r1", "t", ["v1"], [("クレセント", "Crescent")]),
                         "r2": release("r2", "f", ["v2"], [("ホワッツマイケル", "What's Michael")]),
                         "r3": release("r3", "f", ["v2"], [("ホワッツマイケル", "What's Michael")], patch="t")}
        self.vns = {"v1": {"olang": "ja", "alias": "\\N", "titles": [
                        {"lang": "ja", "official": "t", "title": "クレセント", "latin": "Crescent"}]},
                    "v2": {"olang": "ja", "alias": "Michael\\nマイケル", "titles": [
                        {"lang": "ja", "official": "t", "title": "ホワッツマイケル", "latin": "What's Michael"}]}}
        self.disks = [{"group": "g", "contentId": "sha256-fd-v1:" + c * 64} for c in "ba"]

    def test_adult_release_names_its_entry_and_review(self):
        entry, review = entry_for({"group": "g", "vndb": "r1", "description": "D"}, self.releases, self.vns, self.disks)
        self.assertEqual((entry["databaseId"], entry["title"], entry["aliases"]), ("vndb-r1", "Crescent", ["クレセント"]))
        self.assertEqual(entry["contentIds"], sorted(d["contentId"] for d in self.disks))
        self.assertEqual(review, {"status": "eroge", "vndbIds": ["v1"], "releaseIds": ["r1"]})

    def test_visual_novel_reviews_its_official_complete_releases(self):
        entry, review = entry_for({"group": "g", "vndb": "v2", "description": "D"}, self.releases, self.vns, self.disks)
        self.assertEqual(entry["aliases"], ["Michael", "マイケル", "ホワッツマイケル"])
        self.assertEqual(review, {"status": "nonadult", "vndbIds": ["v2"], "releaseIds": ["r2"]})

    def test_group_without_disks_fails(self):
        with self.assertRaises(ValueError):
            entry_for({"group": "other", "vndb": "r1", "description": "D"}, self.releases, self.vns, self.disks)


if __name__ == "__main__":
    unittest.main()

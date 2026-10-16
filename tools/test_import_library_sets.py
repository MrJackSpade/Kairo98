import json
import unittest

from import_library_sets import add_research_id, find_sets, set_profile


def image(zip_name, name, content):
    return {"id": name, "uri": "content://" + zip_name, "path": zip_name, "zipEntry": name,
            "contentId": "sha256-fd-v1:" + content * 64, "error": None}


class LibrarySetTests(unittest.TestCase):
    def test_numbered_disks_user_disk_and_alternates_form_one_set(self):
        zip_name = "Gage [T-En].zip"
        entries = [image(zip_name, "Gage (Disk 2) [T-En].d88", "b"),
                   image(zip_name, "Gage (Disk 1) [T-En].d88", "a"),
                   image(zip_name, "Gage (User Disk).d88", "c"),
                   image(zip_name, "alts/Gage (Disk 1) [Alt 1].d88", "d")]
        (found,) = find_sets(entries)
        self.assertEqual(found["entry"]["zipEntry"], "Gage (Disk 1) [T-En].d88")
        self.assertEqual(found["disks"], sorted("sha256-fd-v1:" + c * 64 for c in "bcd"))

    def test_hard_disk_is_the_entry_point(self):
        zip_name = "Giten.zip"
        entries = [image(zip_name, "Giten (Boot Disk).fdi", "a"),
                   {**image(zip_name, "Giten.hdi", "b"), "contentId": "sha256-hdi-v1:" + "b" * 64}]
        (found,) = find_sets(entries)
        self.assertEqual(found["entry"]["zipEntry"], "Giten.hdi")

    def test_episodes_and_separate_games_are_not_sets(self):
        zip_name = "Lime [Separate Episodes].zip"
        entries = [image(zip_name, "Lime - Episode 01.hdi", "a"),
                   image(zip_name, "Lime - Episode 02.hdi", "b"),
                   image("Other.zip", "Other (Disk 1).fdi", "c"),
                   image("Third.zip", "Third (Disk 2).fdi", "d")]
        self.assertEqual(find_sets(entries), [])

    def test_profile_edits_are_idempotent_and_keep_other_fields(self):
        text = '{\n  "schemaVersion": 1,\n  "games": {\n    "a": {\n      "title": "A",\n' \
               '      "hidden": false\n    }\n  }\n}\n'
        once = set_profile(text, "a", ["b"], None)
        once = set_profile(once, "n", ["m"], "New")
        twice = set_profile(set_profile(once, "a", ["b"], None), "n", ["m"], "New")
        self.assertEqual(once, twice)
        games = json.loads(twice)["games"]
        self.assertEqual(games["a"], {"setDisks": ["b"], "title": "A", "hidden": False})
        self.assertEqual(games["n"], {"title": "New", "setDisks": ["m"]})

    def test_research_id_is_added_once(self):
        text = '{\n  "entries": [\n    {\n      "platform": "pc98",\n      "databaseId": "1",\n' \
               '      "contentIds": [],\n      "title": "T"\n    }\n  ]\n}\n'
        once = add_research_id(text, "1", "x")
        self.assertEqual(add_research_id(once, "1", "x"), once)
        self.assertEqual(json.loads(once)["entries"][0]["contentIds"], ["x"])


if __name__ == "__main__":
    unittest.main()

import json
import unittest

from import_disks import add_research_ids, find_sets, neokobe_disks, set_profile


def disk(source, name, content):
    kind = "hdi" if name.endswith(".hdi") else "fd"
    return {"contentId": f"sha256-{kind}-v1:" + content * 64, "name": name, "source": source}


def entry_name(disks):
    (found,), ambiguous = find_sets(disks)
    assert ambiguous == []
    return found["entry"]["name"]


class SetTests(unittest.TestCase):
    def test_numbered_disks_user_disk_and_alternates_form_one_set(self):
        source = "Gage [T-En].zip"
        disks = [disk(source, "Gage (Disk 2) [T-En].d88", "b"),
                 disk(source, "Gage (Disk 1) [T-En].d88", "a"),
                 disk(source, "Gage (User Disk).d88", "c"),
                 disk(source, "Gage (Disk 1) [Alt 1].d88", "d")]
        (found,), _ = find_sets(disks)
        self.assertEqual(found["entry"]["name"], "Gage (Disk 1) [T-En].d88")
        self.assertEqual(found["disks"], sorted("sha256-fd-v1:" + c * 64 for c in "bcd"))

    def test_hard_disk_is_the_entry_point(self):
        self.assertEqual(entry_name([disk("Giten", "Giten (Boot Disk).fdi", "a"),
                                     disk("Giten", "Giten.hdi", "b")]), "Giten.hdi")

    def test_roles_letters_and_unlabeled_disks(self):
        self.assertEqual(entry_name([disk("D", "D (Data disk).hdm", "a"),
                                     disk("D", "D (System disk).hdm", "b")]), "D (System disk).hdm")
        self.assertEqual(entry_name([disk("R", "R (Disk B).d88", "a"),
                                     disk("R", "R (Disk A).d88", "b")]), "R (Disk A).d88")
        self.assertEqual(entry_name([disk("Y", "Y (User disk).d88", "a"),
                                     disk("Y", "Y.d88", "b")]), "Y.d88")
        self.assertEqual(entry_name([disk("W", "W (Duplicate disk).d88", "a"),
                                     disk("W", "W (Master disk).d88", "b")]), "W (Master disk).d88")

    def test_copies_rank_plain_then_tagged_then_alternates_then_originals(self):
        self.assertEqual(entry_name([disk("C", "C (Disk A) [original].hdm", "a"),
                                     disk("C", "C (Disk A) [Alt 1] [installed].hdm", "b"),
                                     disk("C", "C (Disk A) [installed].hdm", "c"),
                                     disk("C", "C (Disk B).hdm", "d")]), "C (Disk A) [installed].hdm")
        self.assertEqual(entry_name([disk("V", "V (Disk A) [original uncracked].hdm", "a"),
                                     disk("V", "V (Disk A) [Alt 2].hdm", "b"),
                                     disk("V", "V (Disk A) [Alt 1].hdm", "c")]), "V (Disk A) [Alt 1].hdm")

    def test_same_disk_in_two_formats_starts_from_one(self):
        self.assertEqual(entry_name([disk("R", "R (Disk A).nfd", "a"), disk("R", "R (Disk A).d88", "b"),
                                     disk("R", "R (Disk B).nfd", "c")]), "R (Disk A).d88")

    def test_supplementary_set_and_numbered_games_stay_separate(self):
        sets, _ = find_sets([disk("M", "M (#1 System disk).hdm", "a"), disk("M", "M (#1 Sampling disk).hdm", "b"),
                             disk("M", "M (#2 System disk).hdm", "c"), disk("M", "M (#2 Sampling disk).hdm", "d")])
        self.assertEqual([found["entry"]["name"] for found in sets],
                         ["M (#1 System disk).hdm", "M (#2 System disk).hdm"])
        self.assertEqual(entry_name([disk("S", "D (Special Disk) (Disk 2).hdm", "a"),
                                     disk("S", "D (Special Disk) (Disk 1).hdm", "b")]),
                         "D (Special Disk) (Disk 1).hdm")

    def test_equal_candidates_are_reported_not_chosen(self):
        sets, ambiguous = find_sets([disk("D", "D (Conquest disk).hdm", "a"), disk("D", "D (Dungeon disk).hdm", "b")])
        self.assertEqual(sets, [])
        self.assertEqual(len(ambiguous), 1)

    def test_episodes_and_separate_games_are_not_sets(self):
        source = "Lime [Separate Episodes].zip"
        disks = [disk(source, "Lime - Episode 01.hdi", "a"), disk(source, "Lime - Episode 02.hdi", "b"),
                 disk("Other.zip", "Other (Disk 1).fdi", "c"), disk("Third.zip", "Third (Disk 2).fdi", "d")]
        self.assertEqual(find_sets(disks), ([], []))


class NeoKobeTests(unittest.TestCase):
    def test_disks_belong_to_the_release_folder_or_kept_zip(self):
        backlog = {"groups": [
            {"platform": "pc98", "groupId": "pc98:Mink.zip:Dangel", "source": "Mink.zip",
             "variants": ["Mink/Dangel/Dangel [FD].zip"]},
            {"platform": "pc98", "groupId": "pc98:doujinHomebrew.zip:A", "source": "doujinHomebrew.zip",
             "variants": ["[Doujin & Homebrew]/A/A (B) [FD].zip"]}]}
        index = {"archives": [
            {"path": "Mink/Mink/Dangel/Dangel [FD]/alts/Dangel (Disk 01) [Alt 1].hdm",
             "disks": [{"entry": None, "contentId": "x"}]},
            {"path": "doujinHomebrew/[Doujin & Homebrew]/A/A (B) [FD].zip",
             "disks": [{"entry": "B (Disk 1).d88", "contentId": "y"}]}]}
        self.assertEqual([(d["source"], d["group"], d["name"]) for d in neokobe_disks(index, backlog)], [
            ("Mink/Mink/Dangel/Dangel [FD]", "pc98:Mink.zip:Dangel", "Dangel (Disk 01) [Alt 1].hdm"),
            ("doujinHomebrew/[Doujin & Homebrew]/A/A (B) [FD].zip", "pc98:doujinHomebrew.zip:A",
             "B (Disk 1).d88")])

    def test_disk_outside_every_group_fails(self):
        with self.assertRaises(ValueError):
            list(neokobe_disks({"archives": [{"path": "X/x.hdm", "disks": [{"entry": None, "contentId": "x"}]}]},
                               {"groups": []}))


class EditTests(unittest.TestCase):
    def test_profile_edits_are_idempotent_merge_and_keep_other_fields(self):
        text = '{\n  "schemaVersion": 1,\n  "games": {\n    "a": {\n      "title": "A",\n' \
               '      "hidden": false\n    }\n  }\n}\n'
        once = set_profile(text, "a", ["c"], None)
        once = set_profile(once, "a", ["b"], None)
        once = set_profile(once, "n", ["m"], "New")
        twice = set_profile(set_profile(once, "a", ["b"], None), "n", ["m"], "New")
        self.assertEqual(once, twice)
        games = json.loads(twice)["games"]
        self.assertEqual(games["a"], {"setDisks": ["b", "c"], "title": "A", "hidden": False})
        self.assertEqual(games["n"], {"title": "New", "setDisks": ["m"]})

    def test_research_ids_are_added_once(self):
        text = '{\n  "entries": [\n    {\n      "platform": "pc98",\n      "databaseId": "1",\n' \
               '      "contentIds": [],\n      "title": "T"\n    }\n  ]\n}\n'
        once = add_research_ids(text, "1", ["y", "x"])
        self.assertEqual(add_research_ids(once, "1", ["x"]), once)
        self.assertEqual(json.loads(once)["entries"][0]["contentIds"], ["x", "y"])


if __name__ == "__main__":
    unittest.main()

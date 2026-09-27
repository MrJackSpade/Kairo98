import tempfile
import unittest
from pathlib import Path

from audit_vndb_adult_content import review


class AdultReviewTests(unittest.TestCase):
    def test_platform_release_flags_and_ambiguity(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            (directory / "releases_platforms").write_text(
                "r1\tp98\nr2\tp98\nr3\tp88\nr4\tp98\n", encoding="utf-8")
            (directory / "releases_vn").write_text(
                "r1\tv1\tcomplete\nr2\tv2\tcomplete\nr3\tv3\tcomplete\n"
                "r4\tv1\tcomplete\n", encoding="utf-8")
            def release(number, erotic):
                fields = ["\\N"] * 22
                fields[0] = f"r{number}"
                fields[17] = "t" if erotic else "f"
                fields[18] = "f"
                fields[21] = "t"
                return "\t".join(fields) + "\n"
            (directory / "releases").write_text(
                release(1, True) + release(2, False) + release(3, True) + release(4, False),
                encoding="utf-8")
            (directory / "vn_titles").write_text(
                "v1\tja\tt\tAlpha Game\t\\N\n"
                "v2\tja\tt\tBeta Game\t\\N\n"
                "v3\tja\tt\tAlpha Game\t\\N\n", encoding="utf-8")
            (directory / "vn").write_text("", encoding="utf-8")
            matches = [
                {"platform": "pc98", "databaseId": "1", "title": "Alpha Game"},
                {"platform": "pc98", "databaseId": "2", "title": "Beta Game"},
                {"platform": "pc88", "databaseId": "3", "title": "Alpha Game"},
                {"platform": "pc88", "databaseId": "4", "title": "Unknown"},
            ]
            games = review(matches, directory, "sha")["games"]
            self.assertEqual(games["pc98:1"]["status"], "unreviewed")
            self.assertEqual(games["pc98:1"]["reason"], "conflicting platform releases")
            self.assertEqual(games["pc98:2"]["status"], "nonadult")
            self.assertEqual(games["pc88:3"]["status"], "eroge")
            self.assertEqual(games["pc88:4"]["status"], "unreviewed")


if __name__ == "__main__":
    unittest.main()

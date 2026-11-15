import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from catalog_tags import tags


def match(database_id, page_url):
    return {"platform": "pc98", "databaseId": database_id, "pageUrl": page_url}


class CatalogTagsTest(unittest.TestCase):
    def test_each_source_gives_its_tags(self):
        matches = [match("100", "https://gamesdb.launchbox-app.com/games/details/100-x"),
                   match("moby-7", "https://www.mobygames.com/game/7/y/"),
                   match("vndb-r1", "https://vndb.org/r1"),
                   match("inventory-1", "")]
        snapshot = {"100": {"ReleaseDate": "1992-03-01T00:00:00-08:00"}}
        launchbox = {"100": {"Genres": "Role-Playing", "MaxPlayers": "1", "Cooperative": "false"}}
        listing = {7: {"release_year": 1989, "genres": ["Strategy / tactics", "Top-down"]}}
        self.assertEqual({"pc98:100": ["1992", "Role-Playing", "Single Player"],
                          "pc98:moby-7": ["1989", "Strategy"]},
                         tags(matches, snapshot, launchbox, listing))

    def test_a_launchbox_game_missing_from_the_downloads_fails(self):
        with self.assertRaises(ValueError):
            tags([match("100", "https://gamesdb.launchbox-app.com/games/details/100-x")], {}, {}, {})


if __name__ == "__main__":
    unittest.main()

import json
import tempfile
import unittest
import zipfile
from pathlib import Path

from catalog_match import add_disk_aliases, add_live, choose, consolidate, homebrew_attribution_matches, new_entry, page_url, sync_page_urls, unlink_groups, validate
from extract_launchbox_snapshot import extract
from inventory_neokobe import pc88_groups, pc98_groups
from prepare_catalog_backlog import split_homebrew_creator, split_neokobe_group, translated_groups
from scrape_launchbox_platform import parse_listing


class CatalogResearchTest(unittest.TestCase):
    def test_platform_listing_extracts_canonical_link(self):
        html = '<script>pages: 2</script><a class="list-item link-no-underline" href="/games/details/123-example">' \
               '<div><h3>Example &amp; More</h3></div></a>'
        pages, games = parse_listing(html)
        self.assertEqual(pages, 2)
        self.assertEqual(games, [{"id": "123", "title": "Example & More",
                                  "pageUrl": "https://gamesdb.launchbox-app.com/games/details/123-example"}])

    def test_sync_pages_requires_complete_title_alignment(self):
        snapshots = {"pc88": {"50001": {"Name": "One"}},
                     "pc98": {"50002": {"Name": "Two"}}}
        listings = {
            "pc88": {"platform": "pc88", "entries": [{"title": "One", "pageUrl": "https://gamesdb.launchbox-app.com/games/details/101-one"}]},
            "pc98": {"platform": "pc98", "entries": [{"title": "Two", "pageUrl": "https://gamesdb.launchbox-app.com/games/details/102-two"}]},
        }
        catalog = {"entries": [{"platform": "pc88", "databaseId": "50001", "pageUrl": "old"},
                               {"platform": "pc98", "databaseId": "50002", "pageUrl": "old"}]}
        synced = sync_page_urls(catalog, snapshots, listings)
        self.assertEqual(synced["entries"][0]["pageUrl"], listings["pc88"]["entries"][0]["pageUrl"])
        self.assertEqual(catalog["entries"][0]["pageUrl"], "old")
        listings["pc98"]["entries"][0]["title"] = "Wrong"
        with self.assertRaises(ValueError):
            sync_page_urls(catalog, snapshots, listings)

    def test_unlink_recomputes_content_ids_and_keeps_other_group(self):
        backlog = {"groups": [{"groupId": "one", "contentIds": ["a"]},
                              {"groupId": "two", "contentIds": ["b"]}]}
        catalog = {"entries": [{"platform": "pc88", "databaseId": "50001",
                                "title": "Game", "sourceGroups": ["one", "two"],
                                "contentIds": ["a", "b"]}]}
        result = unlink_groups(catalog, backlog, ["one"])
        self.assertEqual(result["entries"][0]["sourceGroups"], ["two"])
        self.assertEqual(result["entries"][0]["contentIds"], ["b"])
        self.assertEqual(catalog["entries"][0]["sourceGroups"], ["one", "two"])

    def test_disk_aliases_are_source_linked_and_skip_file_extensions(self):
        entry = {"title": "Reserve 1/2", "aliases": ["Reserve Half"],
                 "sourceGroups": ["game", "disk"]}
        groups = {"game": {"titleHint": "Reserve 1-2 (Half)"},
                  "disk": {"titleHint": "Reserve.hdi"}}
        add_disk_aliases([entry], groups)
        self.assertEqual(entry["aliases"], ["Reserve Half", "Reserve 1-2 (Half)"])
        add_disk_aliases([entry], groups)
        self.assertEqual(entry["aliases"], ["Reserve Half", "Reserve 1-2 (Half)"])

    def test_media_prefers_original_cover_and_gameplay_then_uses_fallbacks(self):
        game = {"Name": "Example", "aliases": [], "images": [
            {"type": "Fanart - Box - Front", "url": "https://images.launchbox-app.com/fan.jpg"},
            {"type": "Screenshot - Game Title", "url": "https://images.launchbox-app.com/title.jpg"},
            {"type": "Box - Front", "url": "https://images.launchbox-app.com/box.jpg"},
            {"type": "Screenshot - Gameplay", "url": "https://images.launchbox-app.com/play.jpg"}]}
        entry = new_entry("pc98", "50001", game)
        self.assertEqual(entry["boxArtUrl"], "https://images.launchbox-app.com/box.jpg")
        self.assertEqual(entry["screenshotUrl"], "https://images.launchbox-app.com/play.jpg")
        fallback = new_entry("pc98", "50001", {**game, "images": game["images"][:2]})
        self.assertEqual(fallback["boxArtUrl"], "https://images.launchbox-app.com/fan.jpg")
        self.assertEqual(fallback["screenshotUrl"], "https://images.launchbox-app.com/title.jpg")
        selection = new_entry("pc98", "50001", {**game, "images": [
            {"type": "Screenshot - Game Select",
             "url": "https://images.launchbox-app.com/select.jpg"}]})
        self.assertEqual(selection["screenshotUrl"],
                         "https://images.launchbox-app.com/select.jpg")

    def test_homebrew_exact_title_respects_creator_credit(self):
        group = {"source": "doujinHomebrew.zip",
                 "groupId": "pc98:doujinHomebrew.zip:Nanno Soft:title:Pipe Dream"}
        game = {"Publisher": "Bullet-Proof Software", "Developer": "Bullet-Proof Software"}
        self.assertFalse(homebrew_attribution_matches(group, game))
        self.assertTrue(homebrew_attribution_matches(group, {**game, "Developer": "Nanno Soft"}))
        self.assertTrue(homebrew_attribution_matches(
            {**group, "groupId": "pc98:doujinHomebrew.zip:Promisence Soft:title:Star Striker"},
            {**game, "Developer": "Promiscence"}))

    def test_homebrew_creator_folder_splits_actual_game_titles(self):
        group = {"platform": "pc98", "groupId": "pc98:doujinHomebrew.zip:Example",
                 "titleHint": "Example", "source": "doujinHomebrew.zip", "variants": [
                     "[Doujin & Homebrew]/Example/Example (First Game) [FD].zip",
                     "[Doujin & Homebrew]/Example/Example (First Game) [HD].zip",
                     "[Doujin & Homebrew]/Example/Example (Second Game) [FD].zip",
                     "[Doujin & Homebrew]/Example/Example [extras].zip"]}
        split = split_homebrew_creator(group)
        self.assertEqual([part["titleHint"] for part in split],
                         ["Example", "First Game", "Second Game"])
        self.assertEqual([len(part["variants"]) for part in split], [1, 2, 1])
        self.assertEqual(sorted(path for part in split for path in part["variants"]),
                         sorted(group["variants"]))

    def test_verified_public_page_override(self):
        self.assertEqual(page_url("454686"),
                         "https://gamesdb.launchbox-app.com/games/details/"
                         "420466-daisenryaku-ii-campaign-version")
        self.assertEqual(page_url("454574"),
                         "https://gamesdb.launchbox-app.com/games/details/"
                         "420354-bio-100-free-game-collection")
        self.assertEqual(page_url("454575"),
                         "https://gamesdb.launchbox-app.com/games/details/"
                         "420355-bio-100-game-collection-part-2")
        self.assertEqual(page_url("454556"),
                         "https://gamesdb.launchbox-app.com/games/details/"
                         "420336-bakutotsu-turb")
        self.assertEqual(page_url("468902"),
                         "https://gamesdb.launchbox-app.com/games/details/"
                         "434684-abnormal-soldier")

    def test_454xxx_public_page_mapping(self):
        self.assertEqual(page_url("454484"),
                         "https://gamesdb.launchbox-app.com/games/details/420264")
        self.assertEqual(page_url("454554"),
                         "https://gamesdb.launchbox-app.com/games/details/420334")
        self.assertEqual(page_url("454997"),
                         "https://gamesdb.launchbox-app.com/games/details/420777")

    def test_462xxx_and_470xxx_public_page_mapping(self):
        self.assertEqual(page_url("462715"),
                         "https://gamesdb.launchbox-app.com/games/details/428495")
        self.assertEqual(page_url("462176"),
                         "https://gamesdb.launchbox-app.com/games/details/427956")
        self.assertEqual(page_url("470408"),
                         "https://gamesdb.launchbox-app.com/games/details/"
                         "436190-jewel-bem-hunter-lime-vol-02")
        self.assertEqual(page_url("470837"),
                         "https://gamesdb.launchbox-app.com/games/details/436621")
        self.assertEqual(page_url("470842"),
                         "https://gamesdb.launchbox-app.com/games/details/436626")
        self.assertEqual(page_url("457974"),
                         "https://gamesdb.launchbox-app.com/games/details/423754")
        self.assertEqual(page_url("459605"),
                         "https://gamesdb.launchbox-app.com/games/details/425385")
        self.assertEqual(page_url("465705"),
                         "https://gamesdb.launchbox-app.com/games/details/431487")
        self.assertEqual(page_url("468901"),
                         "https://gamesdb.launchbox-app.com/games/details/434683")
        self.assertEqual(page_url("453654"),
                         "https://gamesdb.launchbox-app.com/games/details/"
                         "418778-berserkers-front-gaiden-3")

    def test_split_neokobe_folder_keeps_combined_release_separate(self):
        group = {"platform": "pc98", "groupId": "pc98:AicSpirits.zip:Magical Girl Pretty Samy",
                 "titleHint": "Magical Girl Pretty Samy", "contentIds": [],
                 "variants": ["Samy (First Part) [CD].zip",
                              "Samy (First Part+Second Part) [HD].zip",
                              "Samy (Second Part) [CD].zip", "Samy [extras].zip"]}
        split = split_neokobe_group(group)
        self.assertEqual([item["groupId"] for item in split],
                         [group["groupId"], group["groupId"] + ":part-1",
                          group["groupId"] + ":part-2"])
        self.assertEqual(split[0]["variants"],
                         ["Samy (First Part+Second Part) [HD].zip", "Samy [extras].zip"])
        self.assertEqual({item["titleHint"] for item in split[1:]},
                         {"Magical Girl Pretty Samy: First Part",
                          "Magical Girl Pretty Samy: Second Part"})
        self.assertEqual(sorted(variant for item in split for variant in item["variants"]),
                         sorted(group["variants"]))

    def test_pro_yakyuu_fan_power_up_disks_split_from_base(self):
        group = {"platform": "pc88", "groupId": "pc88:Pro Yakyuu Fan (Telenet Japan)",
                 "titleHint": "Pro Yakyuu Fan", "variants": [
                     "Pro Yakyuu Fan [FD].7z",
                     "Pro Yakyuu Fan (Yousei Gibs) [FD].7z"]}
        split = split_neokobe_group(group)
        self.assertEqual([item["groupId"] for item in split],
                         [group["groupId"], group["groupId"] + ":yousei-gibs"])
        self.assertEqual([item["titleHint"] for item in split],
                         ["Pro Yakyuu Fan", "Pro Yakyuu Fan: Power Up Kit - Yousei Gips"])
        self.assertEqual([len(item["variants"]) for item in split], [1, 1])

    def test_disc_station_compilation_keeps_cd_separate_from_games(self):
        group = {"platform": "pc98", "groupId": "pc98:Compile.zip:Disc Station Vol. 10",
                 "titleHint": "Disc Station Vol. 10", "variants": [
                     "Compile/Disc Station Vol. 10/Disc Station Vol. 10 [CD].zip",
                     "Compile/Disc Station Vol. 10/Disc Station Vol. 10 (Nazo Puyo) [FD].zip",
                     "Compile/Disc Station Vol. 10/Disc Station Vol. 10 (Rude Breaker) [FD].zip"]}
        split = split_neokobe_group(group)
        self.assertEqual([part["titleHint"] for part in split],
                         ["Disc Station Vol. 10", "Disc Station Vol. 10: Nazo Puyo",
                          "Rude Breaker"])
        self.assertEqual([len(part["variants"]) for part in split], [1, 1, 1])
        self.assertEqual(sorted(path for part in split for path in part["variants"]),
                         sorted(group["variants"]))

    def test_translated_bundle_separates_episode_hashes_from_combined_image(self):
        group = {"groupId": "series", "titleHint": "Series", "contentIds": ["all", "one", "two"],
                 "sources": [{"path": "separate.zip"}, {"path": "combined.zip"}]}
        archives = {
            "separate.zip": {"disks": [
                {"entry": "Series - Episode 01.hdi", "contentId": "one"},
                {"entry": "Series - Episode 02.hdi", "contentId": "two"}]},
            "combined.zip": {"disks": [{"entry": "Series.hdi", "contentId": "all"}]},
        }
        split = translated_groups(group, archives)
        self.assertEqual([item["groupId"] for item in split],
                         ["translated:series", "translated:series:episode-01",
                          "translated:series:episode-02"])
        self.assertEqual([item["contentIds"] for item in split], [["all"], ["one"], ["two"]])
        self.assertEqual(split[1]["variants"], ["separate.zip"])
        with self.assertRaises(ValueError):
            translated_groups({**group, "contentIds": ["all", "one", "two", "missing"]}, archives)

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

    def test_consolidation_merges_exact_groups_and_keeps_source_links(self):
        game = {"Name": "Example", "Overview": "source overview", "aliases": [],
                "images": [{"type": "Box - Front", "url": "https://images.launchbox-app.com/a.png"},
                           {"type": "Screenshot - Gameplay", "url": "https://images.launchbox-app.com/b.png"}],
                "imageCounts": {"Box - Front": 1, "Screenshot - Gameplay": 1}}
        snapshots = {"pc88": {"1": game}, "pc98": {}}
        snapshots["pc88"]["50001"] = snapshots["pc88"].pop("1")
        backlog = {"groups": [{"platform": "pc88", "groupId": group_id, "titleHint": "Example",
                               "contentIds": [], "exactCandidates": [{"DatabaseID": "50001"}]}
                              for group_id in ("g1", "g2")]}
        matches = consolidate(backlog, snapshots, {"entries": []})
        self.assertEqual(validate(matches, backlog, snapshots), (1, 2))
        entry = matches["entries"][0]
        self.assertEqual(entry["pageUrl"], "https://gamesdb.launchbox-app.com/games/details/1")
        self.assertEqual(entry["sourceGroups"], ["g1", "g2"])
        self.assertEqual(entry["description"], "")
        self.assertNotIn("review", str(entry).lower())
        self.assertEqual(consolidate(backlog, snapshots, matches), matches)
        changed_backlog = {"groups": [{**group, "contentIds": ["sha256-fd-v1:" + "a" * 64]}
                                      if group["groupId"] == "g1" else group
                                      for group in backlog["groups"]]}
        refreshed = consolidate(changed_backlog, snapshots, matches)
        self.assertEqual(refreshed["entries"][0]["contentIds"], ["sha256-fd-v1:" + "a" * 64])
        self.assertEqual(refreshed["entries"][0]["sourceGroups"], ["g1", "g2"])
        entry["boxArtUrl"] = "https://images.launchbox-app.com/wrong.png"
        with self.assertRaises(ValueError):
            validate(matches, backlog, snapshots)
        entry["boxArtUrl"] = "https://images.launchbox-app.com/a.png"
        entry["pageUrl"] = "https://gamesdb.launchbox-app.com/games/details/2"
        with self.assertRaises(ValueError):
            validate(matches, backlog, snapshots)

    def test_exact_title_collision_is_not_relinked(self):
        game = {"Name": "Misty", "aliases": [], "images": []}
        snapshots = {"pc88": {"207573": game}, "pc98": {}}
        backlog = {"groups": [{"platform": "pc88", "groupId": "pc88:Misty (Champion Soft)",
                               "titleHint": "Misty", "contentIds": [],
                               "exactCandidates": [{"DatabaseID": "207573"}]}]}
        self.assertEqual(consolidate(backlog, snapshots, {"entries": []})["entries"], [])

    def test_ambiguous_exact_match_is_left_unassigned(self):
        game = {"Name": "Example", "aliases": [], "images": []}
        snapshots = {"pc88": {"50001": game, "50002": game}, "pc98": {}}
        backlog = {"groups": [{"platform": "pc88", "groupId": "g", "contentIds": [],
                               "exactCandidates": [{"DatabaseID": "50001"}, {"DatabaseID": "50002"}]}]}
        self.assertEqual(consolidate(backlog, snapshots, {"entries": []})["entries"], [])

    def test_explicit_choice_moves_group_without_losing_other_groups(self):
        game = {"Name": "Example", "aliases": [], "images": []}
        snapshots = {"pc88": {"50001": game, "50002": {**game, "Name": "Example 2"}}, "pc98": {}}
        backlog = {"groups": [{"platform": "pc88", "groupId": group_id, "contentIds": [],
                               "exactCandidates": [{"DatabaseID": "50001"}]}
                              for group_id in ("g1", "g2")]}
        catalog = consolidate(backlog, snapshots, {"entries": []})
        changed = choose(catalog, backlog, snapshots, {"g2": "50002"})
        self.assertEqual(validate(changed, backlog, snapshots), (2, 2))
        self.assertEqual({entry["databaseId"]: entry["sourceGroups"] for entry in changed["entries"]},
                         {"50001": ["g1"], "50002": ["g2"]})

    def test_live_page_match_without_snapshot_record(self):
        backlog = {"groups": [{"platform": "pc98", "groupId": "g", "contentIds": ["sha256-hdi-v1:abc"],
                               "exactCandidates": []}]}
        snapshots = {"pc88": {}, "pc98": {}}
        record = {"groupId": "g", "title": "Briganty: The Roots of Darkness", "aliases": [],
                  "pageUrl": "https://gamesdb.launchbox-app.com/games/details/89265-briganty-the-roots-of-darkness",
                  "boxArtUrl": "https://images.launchbox-app.com/a.jpg", "screenshotUrl": ""}
        catalog = add_live({"schemaVersion": 1, "entries": []}, backlog, snapshots, [record])
        self.assertEqual(validate(catalog, backlog, snapshots), (1, 1))
        self.assertEqual(catalog["entries"][0]["databaseId"], "live:89265")
        self.assertEqual(consolidate(backlog, snapshots, catalog), catalog)


if __name__ == "__main__":
    unittest.main()

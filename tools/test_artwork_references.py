import hashlib
import json
import unittest
from pathlib import Path
from artwork_references import compact, expand


class ArtworkReferenceTests(unittest.TestCase):
    def test_all_template_combinations(self):
        for platform in (88, 98):
            for fmt in (0, 1):
                for revision in (0, 2):
                    ref = {'platform': platform, 'game': 123, 'boxArt': {
                        'id': 'd5aa48ab-5b14-4f12-8314-a3d1fd753ec0', 'format': fmt, 'revision': revision}}
                    art = expand(ref)
                    self.assertEqual(compact(art), ref)
                    self.assertEqual(compact(ref), ref)
                    self.assertNotIn('preview', art)
                    url = 'https://images.launchbox-app.com/' + ('r2_' if revision else '') + ref['boxArt']['id'] + ('.png' if fmt else '.jpg')
                    token = hashlib.sha256(url.encode()).hexdigest()[:12]
                    self.assertEqual(art['boxArtUrl'], url)
                    self.assertEqual(art['boxArt'], f'art/catalog/pc{platform}/123/box-{token}.webp')

    def test_malformed_references_rejected(self):
        image = {'id': 'd5aa48ab-5b14-4f12-8314-a3d1fd753ec0', 'format': 0, 'revision': 0}
        ref = {'platform': 98, 'game': 123, 'boxArt': image}
        for bad in ({**ref, 'platform': 99}, {**ref, 'game': True},
                    {**ref, 'previewUrl': 'https://example.com/'},
                    {**ref, 'boxArt': {**image, 'revision': 1}},
                    {**ref, 'boxArt': {**image, 'format': True}},
                    {**ref, 'boxArt': {**image, 'id': '../escape'}}):
            with self.subTest(bad=bad), self.assertRaises(ValueError):
                expand(bad)

    def test_generated_references_and_cached_assets(self):
        root = Path(__file__).resolve().parents[1]
        index = json.loads((root / 'kairo98/src/main/assets/catalog/name-index-v1.json').read_text(encoding='utf-8'))
        urls = set()
        paths = set()
        for record in index['games'].values():
            art = record.get('artwork', {})
            self.assertEqual(compact(art), art)
            for key, value in expand(art).items():
                if key.endswith('Url'):
                    urls.add(value)
                else:
                    paths.add(value)
                    self.assertTrue((root / 'kairo98/src/withImages/assets' / value).is_file(), value)
        self.assertEqual(paths, set(json.loads((root / "catalog/core-review-v1.json").read_text("utf8"))["approvedArtwork"]))

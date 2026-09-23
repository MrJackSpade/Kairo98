import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

from build_catalog import build, compact, main, validate_record
from make_synthetic_catalog import synthetic_source


class CatalogBuildTests(unittest.TestCase):
    def setUp(self):
        self.source = json.loads(Path("docs/fixtures/catalog/source-v1.json").read_text(encoding="utf-8"))

    def test_fixture_and_stable_output(self):
        first = build(self.source)
        second = build(json.loads(json.dumps(self.source, sort_keys=True)))
        self.assertEqual(compact(first[0]), compact(second[0]))
        self.assertEqual(first[1], second[1])
        self.assertEqual(first[0]["shards"], ["00"])

    def test_large_synthetic_index(self):
        manifest, shards = build(synthetic_source(10000))
        self.assertEqual(manifest["games"], 10000)
        self.assertEqual(sum(len(shard) for shard in shards.values()), 10000)
        self.assertEqual(len(shards), 256)
        self.assertEqual(manifest, build(synthetic_source(10000))[0])

    def test_conflicting_hash_requires_review(self):
        original = self.source["datasets"][0]["games"][0]
        self.source["datasets"][0]["games"].append({**original, "title": "Different title"})
        with self.assertRaisesRegex(ValueError, "conflicting identity"):
            build(self.source)

    def test_bad_command_and_art_rejected(self):
        record = self.source["datasets"][0]["games"][0]
        with self.assertRaisesRegex(ValueError, "invalid launch"):
            validate_record({**record, "launch": {"type": "guestCommand", "text": "X&Y"}})
        with self.assertRaisesRegex(ValueError, "invalid artwork"):
            validate_record({**record, "artwork": {"preview": "../outside.png"}})

    def test_controller_binding_contract(self):
        record = self.source["datasets"][0]["games"][0]
        valid = {"profile": "standard-v1", "bindings": [
            {"input": "button:96", "keys": [112, 29]},
            {"input": "axis:0:+", "action": "menu"},
            {"input": "hat:15:-", "joystick": "left"},
        ]}
        validate_record({**record, "controller": valid})
        for bindings in (
            [{"input": "button:96", "keys": [29, 29]}],
            [{"input": "button:96", "keys": [128]}],
            [{"input": "button:96", "keys": [True]}],
            [{"input": "button:96", "action": "shell"}],
            [{"input": "button:96", "joystick": "button3"}],
            [{"input": "button:96", "keys": [29], "joystick": "button1"}],
            [{"input": "button:96", "keys": [29]}, {"input": "button:96", "action": "menu"}],
            [{"input": "axis:0", "keys": [29]}],
        ):
            with self.subTest(bindings=bindings), self.assertRaisesRegex(ValueError, "invalid controller"):
                validate_record({**record, "controller": {"bindings": bindings}})

    def test_input_mode(self):
        record = {"contentIds": ["sha256-hdi-v1:" + "0" * 64], "title": "Test"}
        for mode in ("auto", "keyboard", "mouse"):
            self.assertEqual(validate_record({**record, "input": {"mode": mode}})["input"]["mode"], mode)
        for bad in ({"mode": "pointer"}, {}, {"mode": 1}):
            with self.assertRaisesRegex(ValueError, "invalid input mode"):
                validate_record({**record, "input": bad})

    def test_typed_machine_and_media(self):
        record = self.source["datasets"][0]["games"][0]
        for machine in ({"baseClockTenthsMHz": 25.0}, {"baseClockTenthsMHz": True}):
            with self.subTest(machine=machine), self.assertRaisesRegex(ValueError, "invalid machine"):
                validate_record({**record, "machine": machine})
        for media in ([{"role": "boot", "contentId": 7}], [{"contentId": record["contentIds"][0]}]):
            with self.subTest(media=media), self.assertRaisesRegex(ValueError, "invalid media"):
                validate_record({**record, "media": media})

    def test_removed_hash_prunes_generated_shard(self):
        original = self.source["datasets"][0]["games"][0]
        extra = {**original, "contentIds": ["sha256-hdi-v1:" + "f" * 64], "title": "Extra"}
        with tempfile.TemporaryDirectory() as temporary:
            source = Path(temporary) / "source.json"
            output = Path(temporary) / "catalog"
            self.source["datasets"][0]["games"] = [original, extra]
            source.write_bytes(compact(self.source))
            command = [sys.executable, "tools/build_catalog.py", str(source), str(output)]
            subprocess.run(command, check=True, capture_output=True)
            self.assertTrue((output / "shards/ff.json").exists())
            self.source["datasets"][0]["games"] = [original]
            source.write_bytes(compact(self.source))
            subprocess.run(command, check=True, capture_output=True)
            self.assertFalse((output / "shards/ff.json").exists())


if __name__ == "__main__":
    unittest.main()

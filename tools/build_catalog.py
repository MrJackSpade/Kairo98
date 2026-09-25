#!/usr/bin/env python3
"""Validate source metadata and emit deterministic, content-ID-keyed Android shards."""

import argparse
import hashlib
import json
import re
from pathlib import Path
from urllib.parse import urlsplit

CONTENT_ID = re.compile(r"sha256-(?:hdi|fd)-v1:[0-9a-f]{64}\Z")
ART_PATH = re.compile(r"art/(?!.*\.\.)[A-Za-z0-9_./-]{1,252}\Z")
FIELDS = {"title", "description", "aliases", "artwork", "machine", "controller", "input", "media", "launch", "startupChoices", "diskSwaps"}
SCREEN_HASH = re.compile(r"[0-9a-f]{16}\Z")
SHORT_ID = re.compile(r"[a-z0-9-]{1,40}\Z")


def valid_hashes(values):
    return (isinstance(values, list) and 1 <= len(values) <= 16 and
            all(isinstance(value, str) and SCREEN_HASH.fullmatch(value) for value in values) and
            len(values) == len(set(values)))


def valid_startup_choices(choices):
    if not isinstance(choices, list) or not 1 <= len(choices) <= 4:
        return False
    ids = set()
    for choice in choices:
        if not isinstance(choice, dict) or set(choice) != {"id", "title", "screenHashes", "options"}:
            return False
        name = choice["id"]
        title = choice["title"]
        options = choice["options"]
        if (not isinstance(name, str) or not SHORT_ID.fullmatch(name) or name in ids or
                not isinstance(title, str) or not 0 < len(title.strip()) <= 100 or
                not valid_hashes(choice["screenHashes"]) or
                not isinstance(options, list) or not 1 <= len(options) <= 12):
            return False
        ids.add(name)
        option_ids = set()
        for option in options:
            if not isinstance(option, dict) or set(option) not in (
                    {"id", "label", "key", "enter"}, {"id", "label", "steps"}):
                return False
            option_id = option["id"]
            label = option["label"]
            if (not isinstance(option_id, str) or not SHORT_ID.fullmatch(option_id) or
                    option_id in option_ids or not isinstance(label, str) or
                    not 0 < len(label.strip()) <= 100):
                return False
            if "steps" in option:
                steps = option["steps"]
                if (not isinstance(steps, list) or not 2 <= len(steps) <= 4 or
                        any(not isinstance(step, dict) or
                            set(step) != {"key", "enter", "screenHashes"} or
                            not isinstance(step["key"], str) or
                            not re.fullmatch(r"[A-Za-z0-9]", step["key"]) or
                            type(step["enter"]) is not bool or
                            not valid_hashes(step["screenHashes"]) for step in steps)):
                    return False
            elif (not isinstance(option["key"], str) or
                    not re.fullmatch(r"[A-Za-z0-9]", option["key"]) or
                    type(option["enter"]) is not bool):
                return False
            option_ids.add(option_id)
    return True


def valid_disk_swaps(swaps):
    if not isinstance(swaps, list) or not 1 <= len(swaps) <= 16:
        return False
    ids = set()
    hashes = set()
    for swap in swaps:
        if not isinstance(swap, dict) or set(swap) != {
                "id", "drive", "contentId", "screenHashes", "key", "enter"}:
            return False
        if (not isinstance(swap["id"], str) or not SHORT_ID.fullmatch(swap["id"]) or
                swap["id"] in ids or type(swap["drive"]) is not int or
                swap["drive"] not in (0, 1) or not isinstance(swap["contentId"], str) or
                not swap["contentId"].startswith("sha256-fd-v1:") or
                not CONTENT_ID.fullmatch(swap["contentId"]) or
                not valid_hashes(swap["screenHashes"]) or
                any(value in hashes for value in swap["screenHashes"]) or
                not isinstance(swap["key"], str) or
                not re.fullmatch(r"[A-Za-z0-9]?", swap["key"]) or
                type(swap["enter"]) is not bool):
            return False
        ids.add(swap["id"])
        hashes.update(swap["screenHashes"])
    return True


def valid_image_url(value):
    if not isinstance(value, str) or len(value) > 512:
        return False
    try:
        parsed = urlsplit(value)
        return (parsed.scheme == "https" and parsed.hostname in
                {"images.launchbox-app.com", "gamesdb-images.launchbox.gg"} and
                parsed.port is None and not parsed.username and not parsed.password and
                bool(parsed.path) and not parsed.query and not parsed.fragment)
    except ValueError:
        return False
CONTROLLER_INPUT = re.compile(r"(?:virtual:[a-z0-9]+|button:[0-9]{1,4}|(?:axis|hat):[0-9]{1,3}:[+-])\Z")
CONTROLLER_CONTROLS = {"up", "down", "left", "right", "a", "b", "x", "y",
                       "l1", "r1", "l2", "r2", "start", "select", "menu",
                       "rsup", "rsdown", "rsleft", "rsright"}
CONTROLLER_ACTIONS = {"menu", "pause", "restart", "exit"}
CONTROLLER_JOYSTICK = {"up", "down", "left", "right", "button1", "button2"}
CONTROLLER_MOUSE = {"moveUp", "moveDown", "moveLeft", "moveRight",
                    "leftButton", "rightButton"}


def require(condition, message):
    if not condition:
        raise ValueError(message)


def valid_bindings(bindings):
    if not isinstance(bindings, list) or len(bindings) > 128:
        return False
    seen = set()
    for binding in bindings:
        if not isinstance(binding, dict) or set(binding) not in (
                {"input", "keys"}, {"input", "action"}, {"input", "joystick"},
                {"input", "mouse"}):
            return False
        source = binding.get("input")
        if not isinstance(source, str) or not CONTROLLER_INPUT.fullmatch(source) or source in seen:
            return False
        if source.startswith("virtual:") and source.removeprefix("virtual:") not in CONTROLLER_CONTROLS:
            return False
        seen.add(source)
        if "keys" in binding:
            keys = binding["keys"]
            if not isinstance(keys, list) or not 1 <= len(keys) <= 4:
                return False
            if any(type(key) is not int or not 0 <= key <= 127 for key in keys):
                return False
            if len(keys) != len(set(keys)):
                return False
        elif "action" in binding and (not isinstance(binding["action"], str) or
                                      binding["action"] not in CONTROLLER_ACTIONS):
            return False
        elif "joystick" in binding and (not isinstance(binding["joystick"], str) or
                                        binding["joystick"] not in CONTROLLER_JOYSTICK):
            return False
        elif "mouse" in binding and (not isinstance(binding["mouse"], str) or
                                     binding["mouse"] not in CONTROLLER_MOUSE):
            return False
    return True


def validate_record(record):
    require(isinstance(record, dict), "game must be an object")
    require(set(record) <= FIELDS | {"contentIds"}, "unknown game field")
    ids = record.get("contentIds")
    require(isinstance(ids, list) and ids and len(ids) <= 32, "contentIds must contain 1–32 hashes")
    require(all(isinstance(value, str) and CONTENT_ID.fullmatch(value) for value in ids), "invalid content ID")
    require(len(ids) == len(set(ids)), "duplicate content ID in one game")
    title = record.get("title")
    require(isinstance(title, str) and 0 < len(title.strip()) <= 256, "invalid title")
    description = record.get("description")
    require(description is None or
            (isinstance(description, str) and 0 < len(description.strip()) <= 8000),
            "invalid description")
    aliases = record.get("aliases", [])
    require(isinstance(aliases, list) and len(aliases) <= 64 and
            all(isinstance(x, str) and 0 < len(x.strip()) <= 256 for x in aliases), "invalid aliases")
    artwork = record.get("artwork", {})
    require(isinstance(artwork, dict) and set(artwork) <=
            {"boxArt", "preview", "boxArtUrl", "previewUrl"} and
            all(isinstance(x, str) and ART_PATH.fullmatch(x)
                for key, x in artwork.items() if key in {"boxArt", "preview"}) and
            all(valid_image_url(x) for key, x in artwork.items()
                if key in {"boxArtUrl", "previewUrl"}), "invalid artwork")
    machine = record.get("machine", {})
    require(isinstance(machine, dict) and set(machine) <= {"baseClockTenthsMHz", "gdcClockTenthsMHz"} and
            type(machine.get("baseClockTenthsMHz", 25)) is int and
            machine.get("baseClockTenthsMHz", 25) in (20, 25) and
            type(machine.get("gdcClockTenthsMHz", 50)) is int and
            machine.get("gdcClockTenthsMHz", 50) in (25, 50), "invalid machine")
    controller = record.get("controller", {})
    require(isinstance(controller, dict) and set(controller) <= {"profile", "bindings"} and
            isinstance(controller.get("profile", ""), str) and
            ("profile" not in controller or 1 <= len(controller["profile"]) <= 64) and
            valid_bindings(controller.get("bindings", [])), "invalid controller")
    input_mode = record.get("input", {"mode": "auto"})
    require(isinstance(input_mode, dict) and set(input_mode) == {"mode"} and
            input_mode["mode"] in ("auto", "keyboard", "mouse"), "invalid input mode")
    media = record.get("media", [])
    require(isinstance(media, list) and len(media) <= 16 and
            all(isinstance(x, dict) and isinstance(x.get("role"), str) and
                re.fullmatch(r"[A-Za-z0-9_-]{1,32}", x["role"]) and
                isinstance(x.get("contentId"), str) and CONTENT_ID.fullmatch(x["contentId"])
                for x in media), "invalid media")
    require(all(item["contentId"].startswith("sha256-fd-v1:")
                for item in media if item["role"] in ("bootFloppy", "floppyB")),
            "startup floppy must reference a floppy hash")
    require(all(sum(item["role"] == role for item in media) <= 1
                for role in ("bootFloppy", "floppyB")), "duplicate startup floppy role")
    launch = record.get("launch")
    if launch is not None:
        commands = launch.get("commands") if isinstance(launch, dict) else None
        if commands is None:
            commands = [launch.get("text")] if isinstance(launch, dict) else []
        require(isinstance(launch, dict) and launch.get("type") == "guestCommand" and
                launch.get("ready", "dosPrompt") == "dosPrompt" and
                not ("text" in launch and "commands" in launch) and
                isinstance(commands, list) and 1 <= len(commands) <= 4 and
                all(isinstance(command, str) and 0 < len(command) <= 128 and
                    all(c.isascii() and (c.isalnum() or c in " \\/._:-") for c in command)
                    for command in commands) and
                type(launch.get("timeoutMs", 30000)) is int and
                1000 <= launch.get("timeoutMs", 30000) <= 120000 and
                ("screenHashes" not in launch or
                 (isinstance(launch["screenHashes"], list) and
                  len(launch["screenHashes"]) == len(commands) and
                  all(valid_hashes(group) for group in launch["screenHashes"]))), "invalid launch")
    if "startupChoices" in record:
        require(valid_startup_choices(record["startupChoices"]), "invalid startup choices")
    if "diskSwaps" in record:
        require(valid_disk_swaps(record["diskSwaps"]), "invalid disk swaps")
    return {key: value for key, value in record.items() if key != "contentIds"}


def compact(obj):
    return (json.dumps(obj, sort_keys=True, separators=(",", ":"), ensure_ascii=False) + "\n").encode("utf-8")


def build(source):
    require(source.get("schemaVersion") == 1, "unsupported source schema")
    datasets = source.get("datasets")
    require(isinstance(datasets, list), "datasets must be an array")
    by_id = {}
    provenance = []
    dataset_ids = set()
    for dataset in datasets:
        require(isinstance(dataset, dict), "dataset must be an object")
        dataset_id = dataset.get("id")
        info = dataset.get("provenance")
        require(isinstance(dataset_id, str) and dataset_id and dataset_id not in dataset_ids,
                "invalid or duplicate dataset ID")
        require(isinstance(info, dict) and all(isinstance(info.get(k), str) and info[k]
                                               for k in ("source", "license", "attribution")),
                "dataset needs source, license and attribution")
        require(isinstance(dataset.get("games"), list), "games must be an array")
        dataset_ids.add(dataset_id)
        provenance.append({"id": dataset_id, **{k: info[k] for k in ("source", "license", "attribution")}})
        for game in dataset["games"]:
            normalized = validate_record(game)
            for content_id in game["contentIds"]:
                require(content_id not in by_id or by_id[content_id] == normalized,
                        f"conflicting identity for {content_id}; review before merging")
                by_id[content_id] = normalized
    shards = {}
    for content_id, metadata in sorted(by_id.items()):
        prefix = content_id.split(":", 1)[1][:2]
        shards.setdefault(prefix, {})[content_id] = metadata
    manifest = {"schemaVersion": 1, "shards": sorted(shards),
                "datasets": sorted(provenance, key=lambda x: x["id"]), "games": len(by_id)}
    return manifest, shards


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path)
    parser.add_argument("output", type=Path, help="catalog asset directory")
    args = parser.parse_args()
    manifest, shards = build(json.loads(args.source.read_text(encoding="utf-8")))
    shard_dir = args.output / "shards"
    shard_dir.mkdir(parents=True, exist_ok=True)
    for prefix, games in shards.items():
        (shard_dir / f"{prefix}.json").write_bytes(compact({"schemaVersion": 1, "games": games}))
    for previous in shard_dir.glob("*.json"):
        if re.fullmatch(r"[0-9a-f]{2}\.json", previous.name) and previous.stem not in shards:
            previous.unlink()
    (args.output / "manifest-v1.json").write_bytes(compact(manifest))
    digest = hashlib.sha256(compact(manifest) + b"".join(
        compact({"schemaVersion": 1, "games": shards[name]}) for name in sorted(shards))).hexdigest()
    print(f"{len(shards)} shards, {manifest['games']} hashes, sha256 {digest}")


if __name__ == "__main__":
    main()

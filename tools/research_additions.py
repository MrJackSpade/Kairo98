"""Add research entries, their adult reviews and core-review exclusions to the catalog inputs.

The research file keeps its own layout: new entries are appended after the last one, and a group
joins an existing entry by an edit to that entry's sourceGroups alone. Run
tools/import_disks.py neokobe afterwards to give each linked group's disks and sets to its entry.
"""

import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MATCHES = ROOT / "catalog/research/matches-v1.json"
ADULT = ROOT / "catalog/research/adult-content-v1.json"
CORE_REVIEW = ROOT / "catalog/core-review-v1.json"
# Entries titled from a NeoKobe folder name alone. They are recognized only by their disks:
# a folder name is no evidence that a file of the same name is that game.
INVENTORY_PREFIX = "neokobe-"


def name_matched(entry):
    return not entry["databaseId"].startswith(INVENTORY_PREFIX)


def entry_key(entry):
    return f'{entry["platform"]}:{entry["databaseId"]}'


class ResearchInputs:
    def __init__(self):
        self.text = MATCHES.read_text(encoding="utf-8")
        self.matches = json.loads(self.text)
        self.adult = json.loads(ADULT.read_text(encoding="utf-8"))
        self.core_review = json.loads(CORE_REVIEW.read_text(encoding="utf-8"))
        self.linked = {group: entry_key(item) for item in self.matches["entries"]
                       for group in item["sourceGroups"]}
        self.cataloged = {content_id for item in self.matches["entries"]
                          for content_id in item["contentIds"]}
        self.keys = {entry_key(item) for item in self.matches["entries"]}
        self.added = []

    def add(self, entry, review, exclusion=None):
        """A new entry with its adult review; an adult entry needs the reason it leaves the core."""
        key = entry_key(entry)
        if key in self.keys:
            raise ValueError(f"{key} is already a research entry")
        for group in entry["sourceGroups"]:
            if group in self.linked:
                raise ValueError(f"{group} is already linked to {self.linked[group]}")
        shared = set(entry["contentIds"]) & self.cataloged
        if shared:
            raise ValueError(f"{key} shares disks with cataloged games: {sorted(shared)[:2]}")
        if (review["status"] == "eroge") != (exclusion is not None):
            raise ValueError(f"{key}: an adult review and a core exclusion go together")
        self.linked.update(dict.fromkeys(entry["sourceGroups"], key))
        self.keys.add(key)
        self.cataloged.update(entry["contentIds"])
        self.added.append(entry)
        self.adult["games"][key] = review
        if exclusion is not None:
            self.core_review["excluded"][key] = {"title": entry["title"], "reason": exclusion}

    def link(self, key, group):
        """Adds an inventory group to an existing entry's sourceGroups."""
        if key not in self.keys:
            raise ValueError(f"{key} is not a research entry")
        if group in self.linked:
            raise ValueError(f"{group} is already linked to {self.linked[group]}")
        self.linked[group] = key
        platform, database_id = key.split(":", 1)
        anchor = re.search(r'\n\s*"platform": %s,\n\s*"databaseId": %s,' % (
            re.escape(json.dumps(platform)), re.escape(json.dumps(database_id))), self.text)
        if anchor is None:
            raise ValueError(f"{key} has no entry in the research file")
        field = re.compile(r'\n(\s*)"sourceGroups": \[(.*?)\n\s*\]', re.S).search(self.text, anchor.end())
        groups = json.loads("[" + field.group(2) + "]") + [group]
        indent = field.group(1)
        body = ",".join(f"\n{indent}  {json.dumps(value, ensure_ascii=False)}" for value in groups)
        self.text = self.text[:field.start()] + f'\n{indent}"sourceGroups": [{body}\n{indent}]' + \
            self.text[field.end():]

    def write(self):
        block = "".join(",\n" + "\n".join("    " + line for line in
                                          json.dumps(entry, ensure_ascii=False, indent=2).split("\n"))
                        for entry in self.added)
        close = self.text.rindex("\n  ]\n}")
        text = self.text[:close] + block + self.text[close:]
        written = json.loads(text)
        if [entry_key(item) for item in written["entries"]] != \
                [entry_key(item) for item in self.matches["entries"]] + [entry_key(item) for item in self.added]:
            raise ValueError("The research file's entries changed while adding")
        MATCHES.write_text(text, encoding="utf-8", newline="\n")
        ADULT.write_text(json.dumps(self.adult, ensure_ascii=False, separators=(",", ":")) + "\n",
                         encoding="utf-8", newline="\n")
        CORE_REVIEW.write_text(json.dumps(self.core_review, ensure_ascii=False, indent=2) + "\n",
                               encoding="utf-8", newline="\n")

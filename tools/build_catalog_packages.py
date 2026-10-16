#!/usr/bin/env python3
"""Generate reviewed core assets, the downloadable core catalog and the artwork package.

core-v2.json, online-v1.json and the v1 adult and artwork packages are frozen: app versions up
to 1.1.1 read their per-disk records with each game's metadata repeated, and keep that catalog
until they update. Everything generated here names each game's shared record instead."""
import hashlib
import json
import shutil
import subprocess
import sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'shared/tools'))
from artwork_references import expand, compact as compact_art
from catalog_parts import generate_parts


def generate():
    subprocess.run([sys.executable,str(ROOT/'tools/build_review_catalog.py'),'--reuse-art'],cwd=ROOT,check=True)
    subprocess.run([sys.executable,str(ROOT/'tools/build_online_catalog.py'),'catalog/source-v1.json',
        'kairo98/src/main/assets/catalog/name-index-v1.json','catalog/core-v3.json'],cwd=ROOT,check=True)
    policy=json.loads((ROOT/'catalog/core-review-v1.json').read_text('utf8'))
    art_root=ROOT/'catalog/artwork'
    for path,sha in policy['approvedArtwork'].items():
        assert hashlib.sha256((art_root/path).read_bytes()).hexdigest()==sha,path
        destination=ROOT/'kairo98/src/withImages/assets'/path
        destination.parent.mkdir(parents=True,exist_ok=True)
        shutil.copyfile(art_root/path,destination)
    provenance=json.loads((art_root/'art/catalog-provenance-v1.json').read_text('utf8'))
    provenance['assets']=[v for v in provenance['assets'] if v['asset'] in policy['approvedArtwork']]
    (ROOT/'kairo98/src/withImages/assets/art/catalog-provenance-v1.json').write_text(json.dumps(provenance,separators=(',',':')),'utf8')
    full = json.loads((ROOT/'catalog/optional/full-v1.json').read_text('utf8'))
    assets = ROOT/'kairo98/src/main/assets/catalog'
    documents = {p.relative_to(assets).as_posix():json.loads(p.read_text('utf8')) for p in assets.rglob('*.json')}
    documents['name-index-v1.json'] = full['nameIndex']
    for name, document in documents.items():
        if name.startswith('shards/'):
            document['games'] = {k:full['games'][k] for k in document['games']}
    generate_parts(ROOT,'kairo98',documents,full,expand,compact_art)
    print('PC98:' ,len(policy['excluded']),'excluded entries;',len(policy['approvedArtwork']),'reviewed core images')


if __name__=='__main__': generate()

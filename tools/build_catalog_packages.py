#!/usr/bin/env python3
"""Generate reviewed core assets and the separate manually installed catalog."""
import hashlib
import json
import shutil
import subprocess
import sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'shared/tools'))
from catalog_package import build_package,image_paths
from artwork_references import expand


def generate():
    subprocess.run([sys.executable,str(ROOT/'tools/build_review_catalog.py'),'--reuse-art'],cwd=ROOT,check=True)
    subprocess.run([sys.executable,str(ROOT/'tools/build_online_catalog.py'),'catalog/source-v1.json',
        'kairo98/src/main/assets/catalog/name-index-v1.json','catalog/online-v1.json'],cwd=ROOT,check=True)
    policy=json.loads((ROOT/'catalog/core-review-v1.json').read_text('utf8'))
    art_root=ROOT/'catalog/artwork'
    for path,sha in policy['approvedArtwork'].items():
        assert hashlib.sha256((art_root/path).read_bytes()).hexdigest()==sha,path
        destination=ROOT/'kairo98/src/withImages/assets'/path
        destination.parent.mkdir(parents=True,exist_ok=True)
        shutil.copyfile(art_root/path,destination)
    data=json.loads((ROOT/'catalog/optional/data-v1.json').read_text('utf8'))
    build_package(data,image_paths(data,expand),art_root,ROOT/'catalog/optional/kairo98-adult-v1.zip',
        product='pc98',identity='kairo98-adult',name='Kairo98 adult catalog',revision=1,
        source='https://raw.githubusercontent.com/MrJackSpade/Kairo98/main/catalog/optional/kairo98-adult-v1.meta.json',
        archive_url='https://raw.githubusercontent.com/MrJackSpade/Kairo98/main/catalog/optional/kairo98-adult-v1.zip')
    for suffix in ('.json','.meta.json'):
        shutil.copyfile(ROOT/('catalog/online-v1'+suffix),ROOT/('catalog/core-v2'+suffix))
    metadata=json.loads((ROOT/'catalog/core-v2.meta.json').read_text('utf8'))
    metadata['archive']='core-v2.json'
    (ROOT/'catalog/core-v2.meta.json').write_text(json.dumps(metadata,separators=(',',':'))+'\n','utf8')
    provenance=json.loads((art_root/'art/catalog-provenance-v1.json').read_text('utf8'))
    provenance['assets']=[v for v in provenance['assets'] if v['asset'] in policy['approvedArtwork']]
    (ROOT/'kairo98/src/withImages/assets/art/catalog-provenance-v1.json').write_text(json.dumps(provenance,separators=(',',':')),'utf8')
    print('PC98:' ,len(policy['excluded']),'excluded entries;',len(policy['approvedArtwork']),'reviewed core images')


if __name__=='__main__': generate()

#!/usr/bin/env python3
"""Fetch only pinned public detector assets. No photographs, credentials or runtime CDN."""
import argparse
import hashlib
import json
from pathlib import Path
import urllib.request

ROOT = Path(__file__).resolve().parent


def prepare(check=False):
    manifest = json.loads((ROOT / 'assets.lock.json').read_text())
    total = 0
    for item in manifest['files']:
        destination = ROOT / 'assets' / item['path']
        if not destination.resolve().is_relative_to((ROOT / 'assets').resolve()):
            raise ValueError('Invalid manifest path')
        data = destination.read_bytes() if destination.exists() else b''
        valid = len(data) == item['bytes'] and hashlib.sha256(data).hexdigest() == item['sha256']
        if not valid:
            if check:
                raise ValueError(f"Missing or changed asset: {item['path']}")
            if not item['url'].startswith('https://'):
                raise ValueError('HTTPS required')
            with urllib.request.urlopen(item['url'], timeout=90) as response:
                data = response.read(item['bytes'] + 1)
            if len(data) != item['bytes'] or hashlib.sha256(data).hexdigest() != item['sha256']:
                raise ValueError(f"Downloaded asset does not match lock: {item['path']}")
            destination.parent.mkdir(parents=True, exist_ok=True)
            temp = destination.with_suffix(destination.suffix + '.part')
            temp.write_bytes(data)
            temp.replace(destination)
        total += len(data)
    print(f"Verified {len(manifest['files'])} pinned assets ({total:,} bytes). Photos never leave the browser.")


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true', help='Verify without network access')
    prepare(parser.parse_args().check)

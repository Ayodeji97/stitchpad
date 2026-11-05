import contextlib
import hashlib
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import setup_assets


class AssetTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.data = b'fixture model, not real weights'
        self.item = {'path': 'model.task', 'url': 'https://example.invalid/model',
                     'sha256': hashlib.sha256(self.data).hexdigest(), 'bytes': len(self.data)}
        self.write_manifest()

    def write_manifest(self):
        (self.root / 'assets.lock.json').write_text(json.dumps({'files': [self.item]}))

    def run_check(self):
        with patch.object(setup_assets, 'ROOT', self.root), contextlib.redirect_stdout(io.StringIO()):
            setup_assets.prepare(check=True)

    def test_verified_assets_never_use_network(self):
        (self.root / 'assets').mkdir()
        (self.root / 'assets' / 'model.task').write_bytes(self.data)
        with patch('urllib.request.urlopen', side_effect=AssertionError('network')):
            self.run_check()

    def test_missing_or_corrupt_asset_is_not_accepted(self):
        with self.assertRaises(ValueError): self.run_check()
        (self.root / 'assets').mkdir()
        (self.root / 'assets' / 'model.task').write_bytes(b'x' * len(self.data))
        with self.assertRaises(ValueError): self.run_check()

    def test_manifest_cannot_escape_asset_directory(self):
        self.item['path'] = '../outside'; self.write_manifest()
        with self.assertRaises(ValueError): self.run_check()

    def test_wrong_download_hash_does_not_install_or_replace_asset(self):
        with patch.object(setup_assets, 'ROOT', self.root), patch('urllib.request.urlopen', return_value=io.BytesIO(b'bad')):
            with self.assertRaises(ValueError): setup_assets.prepare()
        self.assertFalse((self.root / 'assets' / 'model.task').exists())

import json
from functools import partial
from http.server import ThreadingHTTPServer
from pathlib import Path
import subprocess
import tempfile
import threading
import unittest
import urllib.error
import urllib.request
import start_session


class StudyToolsTests(unittest.TestCase):
    def test_private_blank_protocol_cannot_claim_approval(self):
        protocol = json.loads((Path(__file__).parent / 'protocol-template.json').read_text())
        self.assertIs(protocol['protocol']['frozen'], False)
        self.assertEqual(protocol['protocol']['approved_by'], '')
        for field in protocol['protocol']['fields'].values():
            self.assertIsNone(field['tolerance_mm'])
            self.assertIsNone(field['reference_spread_limit_mm'])
        self.assertEqual(protocol['participants'], [])

    def test_pipeline_plan_has_nine_distinct_versions_without_measurements(self):
        script = Path(__file__).parent / 'pipeline_plans.mjs'
        result = subprocess.run(['node', str(script)], capture_output=True, text=True, check=True)
        plans = json.loads(result.stdout)['plans']
        self.assertEqual(len(plans), 9)
        self.assertEqual(len({p['engine_version'] for p in plans.values()}), 9)
        self.assertNotIn('estimates', result.stdout)
        self.assertTrue(all(p['front_mode'] in 'AEM' and p['side_mode'] in 'AEM' for p in plans.values()))

    def test_loopback_server_has_no_upload_or_directory_listing(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            (root / 'index.html').write_text('<h1>Fixture only</h1>')
            (root / 'directory').mkdir()
            server = ThreadingHTTPServer(('127.0.0.1', 0), partial(start_session.CaptureHandler, directory=str(root)))
            worker = threading.Thread(target=server.serve_forever, daemon=True)
            worker.start()
            try:
                url = f'http://127.0.0.1:{server.server_port}'
                with urllib.request.urlopen(url) as response:
                    self.assertIn(b'Fixture only', response.read())
                for path in ['/directory/', '/../outside-secret']:
                    with self.assertRaises(urllib.error.HTTPError) as caught:
                        urllib.request.urlopen(url + path)
                    self.assertEqual(caught.exception.code, 404)
                    caught.exception.close()
                with self.assertRaises(urllib.error.HTTPError) as caught:
                    urllib.request.urlopen(urllib.request.Request(url, data=b'not stored', method='POST'))
                self.assertEqual(caught.exception.code, 501)
                caught.exception.close()
                self.assertFalse((root / 'not stored').exists())
            finally:
                server.shutdown()
                server.server_close()
                worker.join()

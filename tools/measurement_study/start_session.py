#!/usr/bin/env python3
"""Serve only bundled capture files on this laptop. No upload route or study-data directory."""
from functools import partial
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
import webbrowser


class CaptureHandler(SimpleHTTPRequestHandler):
    def list_directory(self, path):
        self.send_error(404)
        return None

    def log_message(self, format, *args):
        # Filenames/paths are not added to a session log.
        pass


def main():
    root = Path(__file__).resolve().parent / 'capture'
    if not (root / 'index.html').exists():
        raise SystemExit('Capture files missing. Unzip the complete kit first.')
    handler = partial(CaptureHandler, directory=str(root))
    with ThreadingHTTPServer(('127.0.0.1', 0), handler) as server:
        address = f'http://127.0.0.1:{server.server_port}/'
        print('StitchPad local measurement test')
        print(address)
        print('This link works only on this laptop. Do not expose the server publicly.')
        print('No photos are uploaded. Keep private records outside this kit folder.')
        print('Leave this window open. Press Ctrl+C to stop.')
        webbrowser.open(address)
        try:
            server.serve_forever()
        except KeyboardInterrupt:
            pass


if __name__ == '__main__':
    main()

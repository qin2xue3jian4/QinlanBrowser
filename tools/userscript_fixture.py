"""Synthetic userscript install/frame/request fixture. No credentials or external services."""
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import argparse

SCRIPT = '''// ==UserScript==
// @name Qinglan synthetic install fixture
// @namespace qinglan-qa
// @version 1.0
// @match http://127.0.0.1:8895/*
// @grant GM_setValue
// @grant GM_getValue
// ==/UserScript==
GM_setValue('fixture', true);
document.body.dataset.installed = String(GM_getValue('fixture'));
'''

class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        path = self.path.split('?', 1)[0]
        if path == '/api':
            body, mime = '{"synthetic":true,"number":42}', 'application/json'
        elif path == '/frame':
            body, mime = "<html><body>Frame<script>document.addEventListener('DOMContentLoaded',()=>parent.postMessage({fixtureFrame:true,ran:window.qaFrame===true,forbidden:window.qaNoFrame===true},'*'));</script></body></html>", 'text/html'
        elif path.endswith('.user.js'):
            body, mime = SCRIPT, 'text/javascript'
        else:
            body, mime = '<html><body><h1>Userscript install fixture</h1><a id="install" href="/fixture.user.js">Install synthetic script</a></body></html>', 'text/html'
        data = body.encode('utf-8')
        self.send_response(200)
        self.send_header('Content-Type', mime + '; charset=utf-8')
        self.send_header('Content-Length', str(len(data)))
        self.end_headers()
        self.wfile.write(data)
    def log_message(self, *_):
        pass

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--port', type=int, default=8895)
    args = parser.parse_args()
    print(f'Userscript fixture: http://127.0.0.1:{args.port}', flush=True)
    ThreadingHTTPServer(('127.0.0.1', args.port), Handler).serve_forever()

"""Local, synthetic browser QA fixture. No account data or external network."""
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
import json

EVENTS=Path(__file__).resolve().parents[1]/'test-results'/'fixture-events.jsonl'
EVENTS.parent.mkdir(exist_ok=True)

class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        with EVENTS.open('a',encoding='utf8') as f:
            f.write(json.dumps({'path':self.path,'cookie_ok':'ql_fixture=synthetic-only' in self.headers.get('Cookie',''),'desktop':'X11' in self.headers.get('User-Agent','')})+'\n')
        if self.path=='/download':
            if 'ql_fixture=synthetic-only' not in self.headers.get('Cookie',''):
                self.send_error(403);return
            body=b'Qinglan synthetic authenticated download OK'
            self.send_response(200);self.send_header('Content-Type','text/plain');self.send_header('Content-Length',str(len(body)));self.send_header('Content-Disposition','attachment; filename="qinglan-test.txt"');self.end_headers();self.wfile.write(body);return
        self.send_response(200);self.send_header('Content-Type','image/svg+xml' if self.path=='/image.svg' else 'text/html; charset=utf-8')
        self.send_header('Set-Cookie','ql_fixture=synthetic-only; Path=/; HttpOnly; SameSite=Lax');self.end_headers()
        html='''<!doctype html><meta name="viewport" content="width=device-width,initial-scale=1"><title>Qinglan QA</title><style>body{font:18px sans-serif;padding:22px;background:#fafcfb;color:#243b33}a,button,input{display:block;margin:22px 0;padding:12px}img{width:90px;height:90px}</style><h1>Qinglan QA</h1><p>Find needle here. Another needle.</p><a href="/second">Second page</a><a href="/download">Download test file</a><a target="_blank" href="/new-tab">New tab</a><input type="file"><img alt="Test image" src="/image.svg"><p>Cookie fixture uses synthetic data only.</p>'''
        if self.path=='/image.svg':
            self.wfile.write(b'<svg xmlns="http://www.w3.org/2000/svg" width="90" height="90"><rect width="90" height="90" fill="green"/></svg>')
        else:self.wfile.write(html.encode())
    def log_message(self,*args): pass

if __name__=='__main__':
    print('Fixture: http://127.0.0.1:8765',flush=True)
    ThreadingHTTPServer(('127.0.0.1',8765),Handler).serve_forever()

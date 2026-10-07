"""Synthetic browser controls fixture, with optional local self-signed TLS."""
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
import argparse
import ssl
import threading

class Handler(BaseHTTPRequestHandler):
    def log_message(self, *_): pass
    def do_GET(self):
        if self.path == '/blob-fixture.js':
            body=b'''const bytes=new Uint8Array(200003);for(let i=0;i<bytes.length;i++)bytes[i]=i%251;
            blob.href=URL.createObjectURL(new Blob([bytes],{type:'application/octet-stream'}));blob.download='controls-blob.bin';
            document.getElementById('generate').addEventListener('click',()=>{const link=document.createElement('a');link.href=URL.createObjectURL(new Blob([bytes]));link.download='controls-blob.bin';document.body.append(link);link.click();URL.revokeObjectURL(link.href);link.remove();});'''
            self.send_response(200);self.send_header('Content-Type','text/javascript');self.send_header('Content-Length',str(len(body)));self.end_headers();self.wfile.write(body);return
        if self.path == '/redirect-http':
            self.send_response(302);self.send_header('Location','http://127.0.0.1:8892/download');self.end_headers();return
        if self.path == '/download':
            body=b'synthetic secure download\n';kind='application/octet-stream'
        else:
            body=b'''<!doctype html><meta name="viewport" content="width=device-width,initial-scale=1"><title>Controls fixture</title>
            <style>html,body{margin:0}header{height:100px;background:#ff0000}main{height:2800px;background:#ffffff}footer{height:100px;background:#00ff00}</style>
            <header>FIRST_MARKER</header><main><p id="text">Selected fixture text for translation and page find.</p>
            <a href="/second">Second page</a><button onclick="const bytes=new Uint8Array(200003);for(let i=0;i&lt;bytes.length;i++)bytes[i]=i%251;const a=document.createElement('a');a.href=URL.createObjectURL(new Blob([bytes],{type:'application/octet-stream'}));a.download='controls-blob.bin';document.body.append(a);a.click();">Generate Blob</button>
            <a id="blob">Download Blob</a><button id="generate">Programmatic Blob</button><script src="/blob-fixture.js"></script>
            </main><footer>LAST_MARKER</footer>''';kind='text/html'
        self.send_response(200);self.send_header('Content-Type',kind);self.send_header('Content-Length',str(len(body)))
        if self.path == '/csp':self.send_header('Content-Security-Policy',"default-src 'self'; script-src 'self'; style-src 'self'; connect-src 'self' ws: wss:; img-src 'self' data:; object-src 'none'")
        self.end_headers();self.wfile.write(body)

if __name__ == '__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--cert');parser.add_argument('--key');args=parser.parse_args()
    http=ThreadingHTTPServer(('127.0.0.1',8892),Handler)
    if args.cert:
        https=ThreadingHTTPServer(('127.0.0.1',8893),Handler)
        context=ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER);context.load_cert_chain(args.cert,args.key)
        https.socket=context.wrap_socket(https.socket,server_side=True)
        threading.Thread(target=https.serve_forever,daemon=True).start()
    print('READY: HTTP 8892, HTTPS 8893 if configured',flush=True)
    http.serve_forever()

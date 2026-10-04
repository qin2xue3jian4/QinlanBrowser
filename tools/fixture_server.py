"""Local, synthetic browser QA fixture. No account data or external network."""
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
import json
import struct
import zlib

def icon_png():
    def chunk(kind,data):
        return struct.pack('>I',len(data))+kind+data+struct.pack('>I',zlib.crc32(kind+data)&0xffffffff)
    pixels=b''.join(b'\x00'+bytes([32,150,100,255])*32 for _ in range(32))
    return b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',32,32,8,6,0,0,0))+chunk(b'IDAT',zlib.compress(pixels))+chunk(b'IEND',b'')

EVENTS=Path(__file__).resolve().parents[1]/'test-results'/'fixture-events.jsonl'
EVENTS.parent.mkdir(exist_ok=True)

class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path.startswith('/protected-tone.wav'):
            if 'ql_fixture=synthetic-only' not in self.headers.get('Cookie','') or '/resources' not in self.headers.get('Referer',''):
                self.send_error(403);return
            import io, wave
            output=io.BytesIO()
            with wave.open(output,'wb') as wav:
                wav.setnchannels(1);wav.setsampwidth(2);wav.setframerate(8000);wav.writeframes(b'\0\0'*1600)
            body=output.getvalue()
            self.send_response(200);self.send_header('Content-Type','audio/wav');self.send_header('Content-Length',str(len(body)));self.end_headers();self.wfile.write(body);return
        if self.path=='/sample.m3u8':
            body=b'#EXTM3U\n#EXT-X-TARGETDURATION:2\n#EXTINF:2,\nsynthetic-segment.ts\n#EXT-X-ENDLIST\n'
            self.send_response(200);self.send_header('Content-Type','application/vnd.apple.mpegurl');self.end_headers();self.wfile.write(body);return
        if self.path=='/favicon.ico':
            body=icon_png();self.send_response(200);self.send_header('Content-Type','image/png');self.send_header('Content-Length',str(len(body)));self.end_headers();self.wfile.write(body);return
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
        if self.path=='/resources':
            html='''<!doctype html><meta name="viewport" content="width=device-width,initial-scale=1"><title>资源识别测试</title><style>body{font:18px sans-serif;padding:24px}audio{width:100%}#synthetic-banner{padding:25px;background:#e4a474}</style><h1>资源识别测试</h1><p>合成音频、播放列表、图片，不包含真实登录数据。</p><audio controls preload="auto" src="/protected-tone.wav"></audio><img src="/image.svg"><p id="synthetic-banner">手动屏蔽测试区域</p><script>fetch('/sample.m3u8').then(r=>r.text()).then(()=>document.body.dataset.fetch='ok');</script>'''
        if self.path=='/long':
            html=html.replace('<title>Qinglan QA</title>','<title>'+('Long title for uniform tab rows ' * 12)+'</title>')+'<p>Scroll test</p>'*180
        if self.path=='/image.svg':
            self.wfile.write(b'<svg xmlns="http://www.w3.org/2000/svg" width="90" height="90"><rect width="90" height="90" fill="green"/></svg>')
        else:self.wfile.write(html.encode())
    def log_message(self,*args): pass

if __name__=='__main__':
    print('Fixture: http://127.0.0.1:8765',flush=True)
    ThreadingHTTPServer(('127.0.0.1',8765),Handler).serve_forever()

"""Synthetic reader, link, successful/failed/slow download fixture on loopback only."""
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import time

class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path.startswith('/fail'):
            self.send_error(404);return
        if self.path.startswith(('/file','/slow')):
            slow=self.path.startswith('/slow')
            body=(b'Qinglan synthetic download.\n'*4096) if slow else b'Qinglan download verified.\n'
            self.send_response(200);self.send_header('Content-Type','text/plain');self.send_header('Content-Length',str(len(body)));self.send_header('Content-Disposition','attachment; filename="qinglan-improvement-qa.txt"');self.end_headers()
            try:
                for i in range(0,len(body),4096):
                    self.wfile.write(body[i:i+4096]);self.wfile.flush()
                    if slow:time.sleep(1)
            except (BrokenPipeError,ConnectionResetError):pass
            return
        paragraphs=''.join(f'<p>第 {i} 段。清岚阅读测试：浏览器应当帮助人们安静地阅读文章，保留正文内容、段落与原网页位置。这个页面只包含合成数据，不会涉及真实账号。每段还有足够的文字，用于观察本地正文提取与长文滚动是否正常。</p>' for i in range(1,31))
        body=f'''<!doctype html><html lang="zh"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>清岚本地阅读测试</title><style>body{{font:18px sans-serif;padding:20px;line-height:1.6}}a{{display:block;padding:12px}}</style><nav>这是应该被阅读模式排除的导航</nav><h1>清岚本地阅读测试</h1><a href="/second">打开第二页</a><a href="/file">下载小文件</a><a href="/slow">下载进度测试</a><article><h2>FIRST_MARKER 正文开始</h2>{paragraphs}<p>LAST_MARKER 正文结束。</p><script>window.qaOriginal=1;</script><form><input value="synthetic-private-input"></form></article><footer>这是页脚推广信息</footer></html>'''.encode()
        self.send_response(200);self.send_header('Content-Type','text/html; charset=utf-8');self.send_header('Content-Length',str(len(body)));self.end_headers();self.wfile.write(body)
    def log_message(self,*args):pass

if __name__=='__main__':
    print('Improvement fixture: http://127.0.0.1:8877',flush=True)
    ThreadingHTTPServer(('127.0.0.1',8877),Handler).serve_forever()

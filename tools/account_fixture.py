"""Local-only, synthetic login fixture. adb reverse tcp:8881 tcp:8881."""
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

PAGE = """<!doctype html><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>账号空间测试</title><style>body{font:18px sans-serif;padding:20px;line-height:1.8}button,a{display:block;margin:16px 0;padding:12px}b{color:#286658}</style>
<h1>账号空间测试</h1><p>仅使用虚构的工作/个人身份，不是真实登录。</p>
<p>Cookie：<b id="cookie"></b><br>本地存储：<b id="storage"></b></p>
<button onclick="login('work')">设为工作账号</button><button onclick="login('personal')">设为个人账号</button>
<button onclick="document.cookie='qinglan_accounts_demo=;Max-Age=0;path=/';localStorage.removeItem('qinglan_accounts_demo');location.reload()">清除测试登录</button>
<a href="/download">下载当前身份的测试文件</a><a href="/child" target="_blank">新标签检查同一身份</a>
<script>function login(n){document.cookie='qinglan_accounts_demo='+n+';Max-Age=3600;path=/;SameSite=Lax';localStorage.setItem('qinglan_accounts_demo',n);location.reload()}
document.querySelector('#cookie').textContent=(document.cookie.match(/(?:^|; )qinglan_accounts_demo=([^;]*)/)||[])[1]||'未登录';
document.querySelector('#storage').textContent=localStorage.getItem('qinglan_accounts_demo')||'未登录';</script>"""


class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path == '/favicon.ico':
            self.send_error(404)
            return
        data = PAGE.encode('utf-8')
        mime = 'text/html; charset=utf-8'
        if self.path == '/download':
            # Inspect this fixture's synthetic cookie only. Never log the Cookie header.
            from http.cookies import SimpleCookie
            value = SimpleCookie(self.headers.get('Cookie', '')).get('qinglan_accounts_demo')
            identity = value.value if value and value.value in ('work', 'personal') else 'none'
            data = ('synthetic account: ' + identity + '\n').encode()
            mime = 'text/plain'
            print('Synthetic download identity:', identity, flush=True)
        self.send_response(200)
        self.send_header('Content-Type', mime)
        self.send_header('Content-Length', str(len(data)))
        self.send_header('Cache-Control', 'no-store')
        if self.path == '/download':
            self.send_header('Content-Disposition', 'attachment; filename="qinglan-account-demo.txt"')
        self.end_headers()
        self.wfile.write(data)

    def log_message(self, *_):
        pass


if __name__ == '__main__':
    print('Account fixture on http://127.0.0.1:8881', flush=True)
    ThreadingHTTPServer(('127.0.0.1', 8881), Handler).serve_forever()

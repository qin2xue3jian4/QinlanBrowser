# 首版验证记录

日期：2026-10-04。设备：22021211RC，Android 12 / API 31，系统 WebView 146.0.7680.119。

## 构建结果

- Release 0.1.0（dev.qinglan.browser），105,956 字节，使用系统 WebView，不包含浏览器内核。
- SHA-256：`EDFA5DC12D61F3F20C00927C43E99DC6015235ECED4BA73604ED92CD17139CA8`。
- APK v2 签名校验通过；覆盖安装成功。真机 `run-as` 返回 package not debuggable，符合 Release 预期。
- Cookie JVM 单元测试 10 项通过；Android lint：0 errors / 8 warnings。

## 真机验证

- 顶部地址栏、底部菜单、标签切换和关闭。
- 创建 AI 文件夹、将 ChatGPT 移入文件夹、点击展开子网站网格。
- 页面查找：合成测试页匹配 2 处文字。
- 全屏进入及返回键退出；夜间模式同时改变界面和网页颜色。
- 电脑模式：合成服务器确认收到桌面 UA。
- 无图模式：测试图像停止显示，刷新未请求图像资源；关闭后恢复。
- 系统下载：合成服务器确认收到所需 Cookie，文件成功下载。
- 原生 Cookie instrumentation：GET_COOKIE_INFO、HttpOnly、Secure、域与路径、有效期、JSON 往返、路径隔离、host-only 前缀及精确删除通过。
- 用户授权的 ChatGPT Cookie 文件：45 项，44 项通过解析校验，WebView 接受 43 项、拒绝 1 项；另 1 项在解析阶段跳过。导入后界面呈现登录后的聊天控件。升级 Release 并重新启动后登录状态保留。未发送聊天消息。
- 导入副本仅进入应用私有目录，处理后已删除；电脑原始文件保留。此记录不包含 Cookie 值。

## 验证范围

实际测试设备为上述单台 Android 12 手机，未覆盖所有系统版本。书签 HTML 互导、文件上传及视频全屏尚未逐项真机验收。ChatGPT 当前登录成功不代表凭据永不过期，也不代表所有登录流程均兼容。首版功能边界见 README。

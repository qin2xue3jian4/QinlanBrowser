# 发布 APK

仓库提供两个 GitHub Actions 工作流：

| 工作流 | 触发条件 | 产物 |
| --- | --- | --- |
| Android CI | `main` 提交、Pull Request、手动运行 | Debug APK、检查报告 |
| Release APK | 推送 `v*` 标签、手动选择已有标签 | 签名 APK、SHA-256 校验文件、Release 草稿 |

发布流程不会直接公开草稿，也不会覆盖已发布版本的安装包。

## 首次配置签名

在仓库的 **Settings → Secrets and variables → Actions → New repository secret** 中添加：

| Secret | 值 |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | 签名 keystore 文件的 Base64 内容 |
| `ANDROID_STORE_PASSWORD` | keystore 密码 |
| `ANDROID_KEY_ALIAS` | 密钥别名 |
| `ANDROID_KEY_PASSWORD` | 密钥密码 |

已有安装用户时，应继续使用原发行密钥；更换签名后 Android 无法直接覆盖升级。妥善备份密钥及密码，后续版本都需要它们。不要将密钥或 Base64 内容提交到仓库。

新项目可以交互式创建发行密钥：

```sh
keytool -genkeypair -v -keystore qinglan-release.jks -alias qinglan \
  -keyalg RSA -keysize 3072 -validity 10000
```

在 PowerShell 中可将已有 keystore 编码到被 Git 忽略的本地目录，再把文件内容复制到 Secret：

```powershell
New-Item -ItemType Directory -Force .local | Out-Null
$keyPath = (Resolve-Path 'qinglan-release.jks').Path
[IO.File]::WriteAllText(
    (Join-Path $PWD '.local/keystore-base64.txt'),
    [Convert]::ToBase64String([IO.File]::ReadAllBytes($keyPath))
)
```

Linux / macOS 可使用 `mkdir -p .local && openssl base64 -A -in qinglan-release.jks -out .local/keystore-base64.txt`。用完后删除临时编码文件。

## 发布一个版本

1. 修改根目录 `version.properties`。`VERSION_NAME` 为显示版本，`VERSION_CODE` 每次升级必须递增。
2. 提交代码，并确认 CI 通过。
3. 创建与 `VERSION_NAME` 一致的标签并推送。例如版本为 `0.4.0`：

   ```sh
   git tag -a v0.4.0 -m "清岚 0.4.0"
   git push origin main
   git push origin v0.4.0
   ```

4. 在 Actions 中等待 **Release APK** 完成。工作流会检查标签与源码版本一致、运行测试和 lint、验证 APK 签名，然后创建 Release 草稿。
5. 打开 Releases，编辑草稿的版本说明，确认附件后点击 **Publish release**。

发布附件为 `qinglan-<版本>.apk` 和 `SHA256SUMS`。检查报告和 R8 混淆映射保存在该次 Actions 运行的 artifacts 中，不放入公开下载附件。

应用更新检查使用 GitHub 的 latest release API。正式版本需公开发布，标签为 `v<版本>`，且 APK 附件名为 `qinglan-<版本>.apk`。草稿、预发布版本和缺少 APK 的版本不会触发更新提示。每次发布应递增语义版本及 Android 版本号，保持相同的发行签名；不要用 Debug 包替代公开发行包。

预发布版本可使用 `0.5.0-beta.1` / `v0.5.0-beta.1`，流程会标记为 prerelease。草稿阶段可以重跑工作流，已有说明会保留，附件会更新。公开发布后需要创建新版本，不能用重跑替换原 APK。

若自动运行失败，在修复 Secrets 后可重跑该次任务；也可在 **Actions → Release APK → Run workflow** 中填写已有标签。修改过源码或工作流时，应创建包含修改的新版本标签。

## 本地发行构建

Gradle 从以下环境变量读取签名配置：

```text
QINGLAN_STORE_FILE       keystore 的路径
QINGLAN_STORE_PASSWORD   keystore 密码
QINGLAN_KEY_ALIAS        密钥别名
QINGLAN_KEY_PASSWORD     密钥密码
QINGLAN_REQUIRE_SIGNING  设为 true 时禁止无签名构建
```

配置后执行 `./gradlew :app:assembleRelease`，Windows 使用 `gradlew.bat` 或 `build.ps1`。四项签名配置必须齐全；未配置时只生成未签名 Release，发布工作流不允许使用未签名包。

GitHub 工作流只需要仓库自带的 `GITHUB_TOKEN`，无需额外的个人访问令牌。创建草稿的独立任务拥有 `contents: write`；普通 CI 和签名构建只有仓库读取权限。

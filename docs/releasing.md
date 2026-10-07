# 发布 APK

仓库提供两个 GitHub Actions 工作流：

| 工作流 | 触发条件 | 产物 |
| --- | --- | --- |
| Android CI | `main` 提交、Pull Request、手动运行 | Debug APK、检查报告 |
| Release APK | Releases 页面发布、推送版本标签、手动选择已有标签 | 签名 APK、SHA-256 校验文件；为现有 Release 附加文件，或创建草稿 |

当前版本为 **1.0.1**，Android 版本号为 **18**。正式发布使用标签 **v1.0.1**；现有 Release 的标题和正文会保留，已发布文件不会被覆盖。工作流创建的草稿需要手动发布。

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

## 从 Releases 页面发布

1. 完成上面的四项签名 Secrets 配置，并确保仓库允许 GitHub Actions 运行。签名密钥只配置一次，以后版本继续使用同一密钥。
2. 将本次代码及工作流同步到 GitHub 的默认分支，等待 **Android CI** 通过。不要选择仍包含旧版 `version.properties` 的提交。
3. 打开 **Releases → Draft a new release**，在 **Choose a tag** 输入 `v1.0.1`，选择 **Create new tag on publish**，目标选择包含本次修改的最新提交。
4. 标题可填写 **清岚 1.0.1**，正文可复制 [1.0.1 发布说明](releases/1.0.1.md)。不要勾选 **Set as a pre-release**；将正式版本设为 latest。
5. 点击 **Publish release**。发布事件会启动 **Release APK** 工作流，验证版本、运行测试和 lint、构建压缩后的签名 APK，并核对签名。
6. 在 **Actions** 等待工作流完成，然后刷新 Release 页面。应出现 **qinglan-1.0.1.apk** 和 **SHA256SUMS** 两个附件。无需从 CI 下载 Debug 包，也无需手动上传安装包。

发布页面先创建，APK 附件随后生成；构建失败时该页面可能暂时只有源码压缩包。查看 Actions 失败步骤，修复签名 Secrets 或权限后重跑；附件齐全之前，应用更新检查不会将这个版本显示为可安装更新。

如果只点击 **Save draft**，GitHub 不会发送发布事件，因此不会自动触发这条入口。[GitHub 发布事件说明](https://docs.github.com/en/actions/reference/workflows-and-actions/events-that-trigger-workflows#release)

**启用了不可变发布时：** 公开发布后 GitHub 禁止补充附件，必须先使用下面的草稿流程构建并附加文件，再从 Releases 页面发布已有草稿。希望使用“点击 Publish 后自动构建”的入口时，需要仓库未启用不可变发布。[GitHub 不可变发布说明](https://docs.github.com/en/repositories/releasing-projects-on-github/managing-releases-in-a-repository#creating-a-release)

## 先生成附件，再发布草稿

适用于需要先审核安装包，或仓库已启用不可变发布的情况。

```sh
git tag -a v1.0.1 -m "清岚 1.0.1"
git push origin main
git push origin v1.0.1
```

推送版本标签会触发 **Release APK**，完成后自动创建含 APK、校验文件及默认说明的草稿。打开 Releases 检查附件，编辑说明并点击 **Publish release**。如果已有同标签的草稿，只更新附件并保留你填写的标题和正文。

已有标签也可从 **Actions → Release APK → Run workflow** 手动填写 `v1.0.1`。此入口不会创建 Git 标签，标签必须已经存在；仅在 Releases 草稿中填写一个尚未创建的标签并不等于标签已经存在。

## 重跑、校验和后续版本

- 已发布且两个附件齐全时，重复事件或重跑会跳过构建，保留现有公开文件。
- 上传部分成功时，仅在已上传文件与本次构建完全一致时补充缺失文件；不同文件不会被自动覆盖。若提示文件不一致，需先核对已有文件，或创建新版本。
- 检查报告及 R8 混淆映射保存在该次 Actions 的 artifacts 中，不放入公开下载附件。
- 构建和附件上传均固定到同一个 Git 提交；工作流只在最后上传附件的任务申请 `contents: write`。无需额外个人访问令牌。
- 使用 `sha256sum -c SHA256SUMS` 校验下载文件。Windows 可执行 `Get-FileHash .\qinglan-1.0.0.apk -Algorithm SHA256`，与文件中的摘要比较。

后续发布需修改 `version.properties`，递增 `VERSION_NAME` 和 `VERSION_CODE`，提交后创建对应标签，例如 `v1.0.2`；不要通过移动原标签替换已经发布的二进制文件。预发布可使用 `v1.1.0-beta.1`，并在 Releases 页面勾选 pre-release。

应用更新检查使用 GitHub latest release API，接受带或不带 `v` 的版本标签。只有公开正式版及名为 `qinglan-<版本>.apk` 的附件会触发更新提示；草稿、预发布和缺少 APK 的版本不参与。后续版本必须保持相同发行签名，不能用 Debug 包替代发行包。

## 仓库改名

当前仓库为 `qin2xue3jian4/QinlanBrowser`。仓库改名后，应更新 `AppUpdates.kt` 中的项目地址和 API 地址、README 与发布说明中的绝对链接，以及本地 Git remote：

```sh
git remote set-url origin https://github.com/qin2xue3jian4/QinlanBrowser.git
```

更新检查禁止 HTTP 重定向，并严格核对发布页和 APK 来源地址，因此旧版 APK 不能依赖 GitHub 的改名跳转。将地址修复随递增版本发布，本次使用 `v1.0.1` 和 Android `VERSION_CODE=18`；保留已发布的 `v1.0.0` 标签和附件。

Release 工作流使用动态的 `GITHUB_REPOSITORY`，无需修改仓库名。仓库改名不改变应用包名、安装数据或发行签名，后续安装包继续使用原发行密钥。保持仓库私有时，应用的匿名更新检查无法读取其 Release；不要把仓库访问令牌打包到 APK。

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

GitHub 工作流只需要仓库自带的 `GITHUB_TOKEN`，无需额外的个人访问令牌。上传附件的独立任务拥有 `contents: write`；普通 CI、发布预检和签名构建只有仓库读取权限。

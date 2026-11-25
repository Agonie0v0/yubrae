# 余白 · YUBRAE

> 给生活留一点余白 — 极简清雅、支持本地/AI 自动捕获与多币种资产管理的 **Android + iOS 双端原生记账应用**。

## 核心特性

- **双端原生体验**：Android（Kotlin + Jetpack Compose + Room）与 iOS（SwiftUI + 零第三方依赖原生工程）共享一致的多账本与数据模型。
- **极简明细与周日历**：清爽流水视图、自定义多账本独立核算（支持账本迁移与安全删除）、周日起始收支日历。
- **底部快捷记账 + AI 速记**：底部中央 `+` 一键手动记账，AI 悬浮速记支持自然语言文本与账单截图识别入账，并提供本地正则解析兜底。
- **全本地自动捕获与快捷入账**：
  - **Android**：无障碍页面识别（微信 / 支付宝 / 电商订单 / NFC 公交卡）、通知监听、短信捕获、桌面小组件、悬浮确认条，兼容小米 HyperOS / 非小米 Root / 免 Root 主流设备。
  - **iOS**：原生支持 `yubrae://addbill?...`、`yubrae://record?...` 等 URL Scheme 快捷指令自动化入账与隐私锁保护。
- **一木风多币种资产管理**：实时汇率折算、报销 / 债务 / 理财模块联动、净资产与月度趋势统计。
- **双端无损数据同步与备份**：
  - Android 支持一木记账多格式账单（`.xlsx` / `.xls` / `.csv` / `.tsv` / `.zip`）无损迁移。
  - Android 与 iOS 采用 100% 字节级互通的 `KLBK1` 加密备份格式（PBKDF2-HMAC-SHA256 120,000 次迭代 + AES-256-GCM）及 V3 `KairoBackupSnapshot` JSON 结构，支持本地 `.klbk` 文件导入导出与 WebDAV 云同步双向无缝互通。
- **应用内更新检查**：内置 GitHub Releases 检查更新与可选 `https://` 国内镜像加速，Android 端支持流式下载、SHA-256 强校验与直接拉起系统安装器。

## 自动构建与双端发版

每次推送至 `main`、`pull_request` 或发布 `v*` 标签都会触发 GitHub Actions 工作流（[android-build.yml](.github/workflows/android-build.yml) — `Yubrae Release CI (Android + iOS)`）：

1. **Android Job (`ubuntu-latest`)**：
   - 运行全部 JVM 单元测试（`testDebugUnitTest`）并上传测试报告。
   - **仅构建正式 Release APK**（`assembleRelease`，不再产出多余的 `debug` / `perf` 发版包）。
   - 发布 `v*` 标签时强制要求配置正式签名 Secrets，产出 `yubrae-<tag>-release.apk`；普通分支或 PR 无签名密钥时仅构建未签名包校验 R8 编译。
2. **iOS Job (`macos-latest`)**：
   - 通过 `xcodegen generate` 由 `ios/project.yml` 生成 `ios/Yubrae.xcodeproj`。
   - 自动选择最新可用 iPhone Simulator 运行 `YubraeTests` 单元测试。
   - 构建真机 Release App 并打包为标准未签名 IPA（`yubrae-<tag>-ios-unsigned.ipa`，可通过 AltStore / SideStore / Sideloadly / 巨魔等工具自签安装）。
3. **Release Job (`refs/tags/v*`)**：
   - 合并双端产物并生成统一 `SHA256SUMS.txt`（仅含 `yubrae-<tag>-release.apk` 与 `yubrae-<tag>-ios-unsigned.ipa`）。
   - 自动发布 GitHub Release 并生成版本更新日志。

> **仓库 Secrets 配置说明（`Settings -> Secrets and variables -> Actions`）**：
> - **Android 正式签名（发布 `v*` 标签必填）**：`ANDROID_KEYSTORE_BASE64`、`SIGNING_STORE_PASSWORD`、`SIGNING_KEY_ALIAS`、`SIGNING_KEY_PASSWORD`。
> - **iOS 正式签名（可选预留）**：工作流已预留 `IOS_P12_BASE64` 与 `IOS_PROVISION_PROFILE_BASE64` 检测入口，后续接入 Apple 开发者证书即可扩展输出已签名 IPA。

## 本地开发与构建

### Android（Windows PowerShell 7）

```powershell
# 运行单元测试
.\gradlew.bat testDebugUnitTest --no-parallel --console=plain

# 默认构建正式签名 Release 包并在已连接 ADB 设备上自动安装与重启
.\scripts\dev-run.ps1 -EnsureAccessibility

# 如需切换本地调试包（com.yubrae.app.debug）
.\scripts\dev-run.ps1 -Debug
```

### iOS（macOS + Xcode 15+）

```bash
# 安装 XcodeGen 并生成工程
brew install xcodegen
cd ios
xcodegen generate

# 使用 Xcode 打开工程进行真机调试或单元测试
open Yubrae.xcodeproj
```

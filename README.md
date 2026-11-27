# 余白 · YUBRAE

> 给生活留一点余白 — 极简清雅、支持本地自动捕获、AI 多模态速记与多币种资产管理的 **Android + iOS 双端原生记账应用**。

## 核心特性

- **双端原生体验**：
  - **Android**（`minSdk 26` / `targetSdk 35`）：Kotlin + Jetpack Compose + Room + Glance 桌面小组件。
  - **iOS**（`iOS 16.0+`）：Swift 5.9 + SwiftUI + AppIntents，零第三方依赖原生工程，双端共享一致的多账本与数据模型。
- **极简明细、周日历与精准检索**：
  - 清爽流水视图、自定义多账本独立核算（支持账本迁移与安全删除）、周日起始收支日历。
  - 明细检索支持分类 / 父分类 / 账户模糊匹配与币种筛选，内置 130+ 套自定义分类与标签图标。
- **多方式快捷记账与 AI 速记**：
  - 底部中央 `+` 一键手动记账（支持金额表达式计算）、内置语音记账。
  - AI 悬浮速记支持自然语言文本与账单 / 小票截图识别自动拆单入账，未配置大模型时自动回退至本地离线正则解析兜底。
- **全本地自动捕获与快捷自动化**：
  - **Android**：无障碍页面识别（微信 / 支付宝 / 电商订单 / 交通卡等）、系统通知监听、银行短信捕获、分享文本 / 图片入账、桌面小组件与底部悬浮确认弹窗，兼容主流机型及 Root / 免 Root 保活策略。
  - **iOS**：原生集成系统快捷指令（`AppIntents`：快捷记一笔、一句话智能记账、截图 / 小票识图记账、一键从相册识图记账）与 `yubrae://addbill` / `yubrae://record` / `yubrae://smartinput` / `yubrae://receipt` 等 URL Scheme 自动化，支持生物识别隐私锁。
- **多币种资产、债务与报销管理**：
  - 支持多币种实时汇率折算、主列表长按直拖排序、账户备注、账户详情账单工作台、信用卡真实账期、隐藏账户与定期周期记账。
  - 内置报销中心与债务中心（支持债务编辑与自由控制是否计入净资产），配合净资产与月度多维度趋势统计图表。
- **双端加密备份、WebDAV 同步与多格式账单导入**：
  - Android 与 iOS 采用 100% 字节级互通的 `KLBK1` 加密备份格式（PBKDF2-HMAC-SHA256 120,000 次迭代 + AES-256-GCM）及 V3 `KairoBackupSnapshot` JSON 结构，支持本地 `.klbk` 文件导入导出与 WebDAV 云端双向无缝同步。
  - Android 端支持 `.xlsx` / `.xls` / `.csv` / `.tsv` / `.zip` 等常见表格与压缩包账单文件导入迁移。
- **应用内更新检查**：
  - 内置 GitHub Releases 新版本检测与可选 `https://` 国内镜像加速，Android 端支持流式下载、SHA-256 强校验与直接拉起系统安装器。

## 自动识别规则与来源说明

- **本地沙箱规则引擎（Android）**：内置基于 Mozilla Rhino 的安全沙箱 JS 规则引擎（禁用 Java 反射访问并具备指令级超时熔断），全部解析均在设备本地完成，覆盖短信、系统通知与屏幕文本识别及智能分类映射。
- **内置基线与热更新**：规则包由仓库中的 [rules/manifest.json](rules/manifest.json) 与 [rules/rules.zip](rules/rules.zip) 维护，应用打包时内置一份基线副本，运行期支持从云端清单或本地压缩包热更新并进行 SHA-256 完整性校验。
- **规则来源声明**：本项目中内置及云端提供的账单识别规则与分类词库均**搜集整理自网络公开资源**，仅在用户设备本地运行，用于辅助日常个人记账解析。

## 项目结构

```text
.
├── app/                       # Android 端源码、资源、内置规则基线与 JVM 单元测试
├── ios/                       # iOS 端 SwiftUI 源码、XcodeGen 配置 (project.yml) 与单元测试
├── rules/                     # 云端热更新规则清单 (manifest.json) 与规则包 (rules.zip)
├── scripts/                   # 本地开发自动构建/重启脚本 (dev-run.ps1) 与规则打包脚本 (pack-rules.ps1)
└── .github/workflows/         # 双端 CI 测试与自动发版工作流 (android-build.yml)
```

## 自动构建与双端发版

每次推送至 `main`（修改仅限 `rules/**` 时自动跳过）、发起 `pull_request` 或手动触发，都会运行单一 GitHub Actions 工作流（[android-build.yml](.github/workflows/android-build.yml) — `Yubrae Release CI (Android + iOS)`）：

1. **Meta Job (`Detect Release Metadata`)**：
   - 校验 `app/build.gradle.kts` 的 `versionName` 与 `ios/project.yml` 的 `MARKETING_VERSION` 一致性。
   - 推送至 `main` 时（提交信息不含 `[skip release]`）：若代码基线版本号高于远端最高 `v*` 标签则使用该基线版本，否则自动递增版本号并触发双端发版；PR、非 `main` 分支或包含 `[skip release]` 的提交仅执行测试与构建校验。
2. **Android Job (`ubuntu-latest`)**：
   - 发版时自动注入目标版本号，运行全部 JVM 单元测试（`testDebugUnitTest`）并上传测试报告。
   - 构建正式 Release APK（`assembleRelease`）：触发发版时强制校验正式签名 Secrets 并产出 `yubrae-<tag>-release.apk`；日常校验若未配置签名密钥则构建未签名包以验证 R8 编译与打包。
3. **iOS Job (`macos-latest`)**：
   - 发版时自动注入目标版本号，通过 `xcodegen generate` 由 `ios/project.yml` 生成 `ios/Yubrae.xcodeproj`。
   - 自动选择最新可用 iOS 16+ iPhone Simulator 运行 `YubraeTests` 单元测试。
   - 构建真机 Release App 并打包为标准未签名 IPA（`yubrae-<tag>-ios-unsigned.ipa`，可通过 AltStore / SideStore / Sideloadly / 巨魔等工具自签安装）。
4. **Release Job（触发发版时自动执行）**：
   - 合并双端产物并生成统一 `SHA256SUMS.txt`（包含 `yubrae-<tag>-release.apk` 与 `yubrae-<tag>-ios-unsigned.ipa`）。
   - 自动创建 `v<version>` 标签、生成更新日志并发布 GitHub Release。

> **仓库 Secrets 配置说明（`Settings -> Secrets and variables -> Actions`）**：
> - **Android 正式签名（发布新版本必填）**：`ANDROID_KEYSTORE_BASE64`、`SIGNING_STORE_PASSWORD`、`SIGNING_KEY_ALIAS`、`SIGNING_KEY_PASSWORD`。
> - **iOS 正式签名（可选预留）**：工作流已预留 `IOS_P12_BASE64` 与 `IOS_PROVISION_PROFILE_BASE64` 检测入口，后续接入 Apple 开发者证书即可扩展输出已签名 IPA。

## 本地开发与构建

### Android（Windows PowerShell 7）

```powershell
# 运行 JVM 单元测试
.\gradlew.bat testDebugUnitTest --no-parallel --console=plain

# 默认构建正式签名 Release 包并在已连接 ADB 设备上自动安装与重启
.\scripts\dev-run.ps1 -EnsureAccessibility

# 开启源码监听模式（修改代码后自动编译、安装并重启应用）
.\scripts\dev-run.ps1 -Watch

# 如需切换本地调试包（com.yubrae.app.debug）
.\scripts\dev-run.ps1 -Debug
```

### iOS（macOS + Xcode 16+）

```bash
# 安装 XcodeGen 并生成工程
brew install xcodegen
cd ios
xcodegen generate

# 使用 Xcode 打开工程进行真机调试或运行单元测试
open Yubrae.xcodeproj
```

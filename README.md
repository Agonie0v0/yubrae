# 余白 · YUBRAE

> 给生活留一点余白 — 极简清雅、支持本地/AI 自动捕获与多币种资产管理的 Android 原生记账应用。

## 核心特性

- **极简明细与周日历**：清爽两行流水视图、自定义多账本独立核算（支持任意账本迁移或回滚删除）、周日起始收支日历。
- **底部快捷记账 + 首页 AI 速记**：底部中央 `+` 一键手动记账，首页右下角 AI 悬浮按钮支持自然语言与账单截图识别入账。
- **全本地自动记账引擎**：无障碍页面识别（微信 / 支付宝 / 电商订单 / NFC 公交卡）、通知监听、短信捕获、桌面小组件与加密悬浮确认条。
- **一木风多币种资产管理**：实时汇率折算、报销 / 债务 / 理财模块联动、顶部下拉进入「资产隐藏」长按切换显隐。
- **安全导入导出与备份**：支持一木记账多格式账单（`.xlsx` / `.xls` / `.csv` / `.tsv` / `.zip`）无损迁移、AES-GCM `.klbk` 加密本地备份与 WebDAV 云同步。

## 自动构建与下载

每次推送至 `main` 或发布 `v*` 标签都会触发 GitHub Actions 工作流（[android-build.yml](.github/workflows/android-build.yml)）：
1. 运行全部 JVM 单元测试（`testDebugUnitTest`）。
2. 自动构建可直接安装的 `perf`（R8 优化性能版）与 `debug` 安装包并归档至 Workflow Artifacts。
3. 推送 `v*` 标签（例如 `v1.0.0`）时自动发布对应的 GitHub Release 并附带 APK 下载包与 `SHA256SUMS.txt`。

> 如需在 Release 中启用自己的正式签名密钥，可在仓库 `Settings -> Secrets and variables -> Actions` 中配置 `ANDROID_KEYSTORE_BASE64`、`SIGNING_STORE_PASSWORD`、`SIGNING_KEY_ALIAS`、`SIGNING_KEY_PASSWORD`。

## 本地构建

```powershell
# 运行全量 Kotlin 编译与单元测试
.\gradlew.bat compileDebugKotlin compilePerfKotlin testDebugUnitTest --no-parallel --console=plain

# 构建 Perf R8 安装包并在已连接 ADB 设备上自动安装重启
.\scripts\dev-run.ps1 -EnsureAccessibility
```

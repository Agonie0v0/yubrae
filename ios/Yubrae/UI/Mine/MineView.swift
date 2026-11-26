import SwiftUI

public struct MineView: View {
    @ObservedObject public var store: LedgerStore
    public let availableUpdate: AppUpdateInfo?
    public let isCheckingUpdate: Bool
    public let onCheckUpdate: () -> Void
    public let onPresentUpdateSheet: () -> Void

    @Environment(\.colorScheme) private var colorScheme

    public init(
        store: LedgerStore,
        availableUpdate: AppUpdateInfo?,
        isCheckingUpdate: Bool,
        onCheckUpdate: @escaping () -> Void,
        onPresentUpdateSheet: @escaping () -> Void
    ) {
        self.store = store
        self.availableUpdate = availableUpdate
        self.isCheckingUpdate = isCheckingUpdate
        self.onCheckUpdate = onCheckUpdate
        self.onPresentUpdateSheet = onPresentUpdateSheet
    }

    private var palette: YubraePalette {
        YubraeTheme.palette(for: store.settings, colorScheme: colorScheme)
    }

    private var hasUpdateDot: Bool {
        guard let update = availableUpdate else { return false }
        return update.tagName != store.settings.ignoredUpdateTag
    }

    public var body: some View {
        NavigationStack {
            List {
                // 品牌头卡
                Section {
                    brandHeaderCard
                        .listRowInsets(EdgeInsets(top: 6, leading: 0, bottom: 6, trailing: 0))
                        .listRowBackground(Color.clear)
                }

                // 新版本提醒条
                if let update = availableUpdate {
                    Section {
                        Button {
                            onPresentUpdateSheet()
                        } label: {
                            HStack(spacing: 12) {
                                Image(systemName: "arrow.down.circle.fill")
                                    .font(.title2)
                                    .foregroundColor(palette.primary)
                                VStack(alignment: .leading, spacing: 2) {
                                    Text("发现新版本 \(update.tagName)")
                                        .font(.subheadline.weight(.bold))
                                        .foregroundColor(palette.textPrimary)
                                        .lineLimit(1)
                                    Text("点击查看更新日志与 .ipa 下载安装方式")
                                        .font(.caption)
                                        .foregroundColor(palette.textSecondary)
                                        .lineLimit(1)
                                }
                                Spacer(minLength: 8)
                                Circle()
                                    .fill(Color.red)
                                    .frame(width: 8, height: 8)
                            }
                            .padding(.vertical, 4)
                        }
                    }
                }

                Section("账务目录") {
                    NavigationLink {
                        LedgerManagementView(store: store)
                    } label: {
                        settingRow(icon: "book.closed.fill", color: 0xFF2AA876, title: "多账本管理", subtitle: "\(store.activeLedgers.count) 个活跃账本")
                    }

                    NavigationLink {
                        CategoryManagementView(store: store)
                    } label: {
                        settingRow(icon: "square.grid.2x2.fill", color: 0xFFEA580C, title: "分类与图标管理", subtitle: "\(store.categories.count) 个收支分类")
                    }

                    NavigationLink {
                        TagManagementView(store: store)
                    } label: {
                        settingRow(icon: "tag.fill", color: 0xFF0284C7, title: "标签库管理", subtitle: "\(store.tags.count) 个消费标签")
                    }

                    NavigationLink {
                        BudgetAndRecurringView(store: store)
                    } label: {
                        settingRow(icon: "calendar.badge.clock", color: 0xFF7C3AED, title: "预算与周期自动记账", subtitle: "\(store.budgets.count) 预算 · \(store.recurringRules.count) 周期规则")
                    }
                }

                Section("自动化与智能记账") {
                    Button {
                        ShortcutsDeepLinkHelper.openShortcuts(createNew: true) { msg in
                            store.toastMessage = msg
                        }
                    } label: {
                        settingRow(icon: "arrow.up.forward.app.fill", color: 0xFF2563EB, title: "直达「快捷指令」添加动作", subtitle: "直接打开 Apple 快捷指令创建或关联余白记账")
                    }
                    NavigationLink {
                        ShortcutsAutomationGuideView(store: store)
                    } label: {
                        settingRow(icon: "bolt.fill", color: 0xFF16A34A, title: "iOS 快捷指令与自动记账", subtitle: "轻点背面截屏、App Intents 与 yubrae:// 测试")
                    }

                    NavigationLink {
                        LlmSettingsView(store: store)
                    } label: {
                        let aiStatus = store.settings.llmEnabled ? "已启用 · \(store.settings.llmModel)" : "未启用（本地离线解析可用）"
                        settingRow(icon: "sparkles", color: 0xFF4F46E5, title: "AI 大模型与小票识图", subtitle: aiStatus)
                    }
                }

                Section("多币种与加密同步") {
                    NavigationLink {
                        ExchangeRateCenterView(store: store)
                    } label: {
                        settingRow(icon: "arrow.triangle.2.circlepath.circle.fill", color: 0xFF0D9488, title: "汇率中心", subtitle: "基准币种 \(store.settings.baseCurrency)")
                    }

                    NavigationLink {
                        BackupAndSyncView(store: store)
                    } label: {
                        let syncText = store.settings.lastBackupSyncMillis > 0
                            ? "上次云同步：\(YubraeTheme.formatDate(store.settings.lastBackupSyncMillis, pattern: "MM-dd HH:mm"))"
                            : ".klbk 加密导入导出 · WebDAV（与 Android 互通）"
                        settingRow(icon: "lock.icloud.fill", color: 0xFF0B57D0, title: "加密备份与 WebDAV 云同步", subtitle: syncText)
                    }
                }

                Section("外观、隐私与更新") {
                    NavigationLink {
                        AppearanceAndPrivacyView(store: store)
                    } label: {
                        let themeName = ThemeStyle.from(store.settings.themeStyle).displayName
                        settingRow(icon: "paintpalette.fill", color: 0xFFC84B78, title: "外观主题与隐私安全", subtitle: "\(themeName) · \(store.settings.themeMode.labelZh)")
                    }

                    NavigationLink {
                        UpdateSettingsView(
                            store: store,
                            availableUpdate: availableUpdate,
                            isCheckingUpdate: isCheckingUpdate,
                            onCheckUpdate: onCheckUpdate,
                            onPresentUpdateSheet: onPresentUpdateSheet
                        )
                    } label: {
                        HStack {
                            settingRow(
                                icon: "arrow.clockwise.circle.fill",
                                color: 0xFF2AA876,
                                title: "检查版本更新",
                                subtitle: "当前版本 v\(AppUpdateChecker.currentAppVersion)"
                            )
                            Spacer(minLength: 8)
                            if hasUpdateDot {
                                Circle()
                                    .fill(Color.red)
                                    .frame(width: 8, height: 8)
                            }
                        }
                    }
                }
            }
            .listStyle(.insetGrouped)
            .scrollContentBackground(.hidden)
            .background(palette.background.ignoresSafeArea())
            .navigationTitle("我的")
            .navigationBarTitleDisplayMode(.inline)
        }
    }

    private var brandHeaderCard: some View {
        VStack(spacing: 14) {
            HStack(spacing: 14) {
                YubraeBrandMark(size: 56, primaryColor: palette.primary)
                VStack(alignment: .leading, spacing: 4) {
                    HStack(spacing: 6) {
                        Text("余白")
                            .font(.title2.weight(.bold))
                            .foregroundColor(palette.textPrimary)
                            .lineLimit(1)
                        Text("YUBRAE")
                            .font(.caption.weight(.semibold))
                            .foregroundColor(palette.primary)
                            .lineLimit(1)
                            .fixedSize(horizontal: true, vertical: false)
                            .padding(.horizontal, 7)
                            .padding(.vertical, 2)
                            .background(Capsule().fill(palette.accentSoftFill))
                    }
                    Text("本地优先 · 跨端 .klbk 加密互通 · 多币种个人记账")
                        .font(.caption)
                        .foregroundColor(palette.textSecondary)
                }
                Spacer(minLength: 0)
            }

            HStack(spacing: 0) {
                statPill(value: "\(store.transactions.count)", label: "总账单")
                Divider().frame(height: 28)
                statPill(value: "\(store.activeAccounts.count)", label: "活跃账户")
                Divider().frame(height: 28)
                statPill(value: "\(store.activeLedgers.count)", label: "账本")
            }
            .padding(.vertical, 10)
            .background(RoundedRectangle(cornerRadius: 14, style: .continuous).fill(palette.surfaceSecondary))
        }
        .padding(16)
        .background(
            RoundedRectangle(cornerRadius: 20, style: .continuous)
                .fill(palette.surface)
                .overlay(
                    RoundedRectangle(cornerRadius: 20, style: .continuous)
                        .strokeBorder(palette.outline, lineWidth: 0.8)
                )
        )
    }

    private func statPill(value: String, label: String) -> some View {
        VStack(spacing: 2) {
            Text(value)
                .font(.headline.monospacedDigit())
                .foregroundColor(palette.textPrimary)
                .lineLimit(1)
            Text(label)
                .font(.caption2)
                .foregroundColor(palette.textSecondary)
                .lineLimit(1)
        }
        .frame(maxWidth: .infinity)
    }

    private func settingRow(icon: String, color: Int64, title: String, subtitle: String) -> some View {
        HStack(spacing: 12) {
            ZStack {
                RoundedRectangle(cornerRadius: 9, style: .continuous)
                    .fill(Color(argbHex: color).opacity(0.15))
                    .frame(width: 34, height: 34)
                Image(systemName: icon)
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundColor(Color(argbHex: color))
            }
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.subheadline.weight(.semibold))
                    .foregroundColor(palette.textPrimary)
                Text(subtitle)
                    .font(.caption)
                    .foregroundColor(palette.textSecondary)
                    .lineLimit(1)
            }
        }
        .padding(.vertical, 2)
    }
}

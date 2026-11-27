import LocalAuthentication
import SwiftUI
import UIKit

public enum YubraeTab: Int, CaseIterable, Hashable {
    case transactions = 0
    case assets = 1
    case stats = 2
    case mine = 3

    public var title: String {
        switch self {
        case .transactions: return "明细"
        case .assets: return "资产"
        case .stats: return "统计"
        case .mine: return "我的"
        }
    }

    public var systemImage: String {
        switch self {
        case .transactions: return "list.bullet.rectangle.portrait"
        case .assets: return "creditcard"
        case .stats: return "chart.bar.xaxis"
        case .mine: return "person.crop.circle"
        }
    }

    public var selectedSystemImage: String {
        switch self {
        case .transactions: return "list.bullet.rectangle.portrait.fill"
        case .assets: return "creditcard.fill"
        case .stats: return "chart.bar.xaxis"
        case .mine: return "person.crop.circle.fill"
        }
    }

    public func shifted(by delta: Int) -> YubraeTab {
        let all = Self.allCases
        guard !all.isEmpty else { return self }
        let currentIndex = all.firstIndex(of: self) ?? 0
        let maxIndex = all.count - 1
        let (sum, overflow) = currentIndex.addingReportingOverflow(delta)
        let clamped: Int
        if overflow {
            clamped = delta > 0 ? maxIndex : 0
        } else {
            clamped = min(max(sum, 0), maxIndex)
        }
        return all[clamped]
    }
}

private struct QuickRecordContext: Identifiable {
    let id = UUID()
    let editingTransaction: TransactionDto?
}

public struct RootTabView: View {
    @ObservedObject public var store: LedgerStore
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.openURL) private var openURL

    @State private var selectedTab: YubraeTab = .transactions
    @State private var quickRecordContext: QuickRecordContext?
    @State private var smartInputLaunch: SmartInputLaunchRequest?
    @State private var isCenterButtonPressed = false

    // Global Update Check State
    @State private var availableUpdate: AppUpdateInfo?
    @State private var showUpdateSheet = false
    @State private var isCheckingUpdate = false
    @State private var hasPerformedLaunchTasks = false

    // Biometric & Privacy Lock State
    @State private var isBiometricLocked = false
    @State private var biometricErrorText: String?

    // Toast State
    @State private var activeToastText: String?
    @State private var activeToastIsError = false
    @State private var toastDismissTask: Task<Void, Never>?

    // External .klbk File Import via onOpenURL
    @State private var pendingKlbkData: Data?
    @State private var klbkPassphraseInput = ""
    @State private var showKlbkPassphraseSheet = false

    public init(store: LedgerStore) {
        self.store = store
    }

    private var palette: YubraePalette {
        YubraeTheme.palette(for: store.settings, colorScheme: colorScheme)
    }

    private var hasUpdateBadge: Bool {
        guard let update = availableUpdate else { return false }
        return update.tagName != store.settings.ignoredUpdateTag
    }

    public var body: some View {
        ZStack(alignment: .top) {
            ZStack(alignment: .bottomTrailing) {
                TabView(selection: $selectedTab) {
                    TransactionsView(
                        store: store,
                        onOpenQuickRecord: { tx in
                            quickRecordContext = QuickRecordContext(editingTransaction: tx)
                        },
                        onOpenSmartInput: {
                            smartInputLaunch = SmartInputLaunchRequest(initialMode: .text)
                        }
                    )
                    .tag(YubraeTab.transactions)

                    AssetsView(store: store)
                        .tag(YubraeTab.assets)

                    StatsView(store: store)
                        .tag(YubraeTab.stats)

                    MineView(
                        store: store,
                        availableUpdate: availableUpdate,
                        isCheckingUpdate: isCheckingUpdate,
                        onCheckUpdate: {
                            Task { await performUpdateCheck(manual: true) }
                        },
                        onPresentUpdateSheet: {
                            if availableUpdate != nil {
                                showUpdateSheet = true
                            }
                        }
                    )
                    .tag(YubraeTab.mine)
                }
                .tabViewStyle(.page(indexDisplayMode: .never))
                .tint(palette.primary)
                .background(palette.background.ignoresSafeArea())

                // 仅在明细页保留精致 AI 速记悬浮圆钮（避开底栏高度，不与中央 + 按钮冲突）
                if selectedTab == .transactions {
                    floatingAiButton
                        .padding(.trailing, 18)
                        .padding(.bottom, 16)
                        .transition(.scale(scale: 0.85).combined(with: .opacity))
                }
            }
            .safeAreaInset(edge: .bottom, spacing: 0) {
                yubraeBottomTabBar
            }

            // 全局 Toast 提示条
            if let toastText = activeToastText {
                YubraeToastBanner(text: toastText, isError: activeToastIsError)
                    .padding(.top, 10)
                    .transition(.move(edge: .top).combined(with: .opacity))
                    .zIndex(50)
            }

            // Face ID / 生物识别锁屏遮罩
            if isBiometricLocked {
                biometricLockOverlay
                    .transition(.opacity)
                    .zIndex(90)
            }

            // 后台切屏隐私防窥模糊遮罩
            if scenePhase != .active && store.settings.hideRecentsPreviewEnabled {
                privacyBlurOverlay
                    .zIndex(100)
            }
        }
        .fullScreenCover(item: $quickRecordContext) { ctx in
            QuickRecordSheet(store: store, editingTransaction: ctx.editingTransaction)
                .yubraeSheetStyle(palette: palette)
        }
        .sheet(item: $smartInputLaunch) { req in
            SmartInputSheet(
                store: store,
                initialMode: req.initialMode,
                autoOpenPhotoPicker: req.autoOpenPhotoPicker,
                autoParseOnPick: req.autoParseOnPick
            )
            .id(req.id)
            .yubraeSheetStyle(palette: palette)
        }
        .sheet(isPresented: $showUpdateSheet) {
            if let update = availableUpdate {
                UpdateReleaseSheet(
                    store: store,
                    updateInfo: update,
                    onDismiss: { showUpdateSheet = false }
                )
                .yubraeSheetStyle(palette: palette)
            }
        }
        .sheet(isPresented: $showKlbkPassphraseSheet) {
            klbkImportPassphraseSheet
                .yubraeSheetStyle(palette: palette)
        }
        .onOpenURL { url in
            handleIncomingURL(url)
        }
        .onAppear {
            if !hasPerformedLaunchTasks {
                hasPerformedLaunchTasks = true
                runLaunchTasks()
            }
            consumePendingSmartInputIfReady()
        }
        .onChange(of: selectedTab) { _ in
            triggerImpactFeedback(.light)
        }
        .onChange(of: scenePhase) { newPhase in
            handleScenePhaseChange(newPhase)
        }
        .onChange(of: store.pendingSmartInputLaunch) { newLaunch in
            if newLaunch != nil {
                consumePendingSmartInputIfReady()
            }
        }
        .onChange(of: store.toastMessage) { newMessage in
            if let msg = newMessage, !msg.isEmpty {
                presentToast(msg, isError: false)
                store.toastMessage = nil
            }
        }
        .onChange(of: store.lastErrorMessage) { newError in
            if let err = newError, !err.isEmpty {
                presentToast(err, isError: true)
                store.lastErrorMessage = nil
            }
        }
    }

    // MARK: - Bottom Tab Bar & Floating AI Button

    private var yubraeBottomTabBar: some View {
        VStack(spacing: 0) {
            Rectangle()
                .fill(palette.outline)
                .frame(height: 0.5)

            HStack(spacing: 0) {
                bottomTabItem(.transactions)
                bottomTabItem(.assets)
                centerQuickRecordButton
                bottomTabItem(.stats)
                bottomTabItem(.mine)
            }
            .padding(.horizontal, 6)
            .frame(height: 58)
        }
        .background(
            Rectangle()
                .fill(.ultraThinMaterial)
                .overlay(palette.surface.opacity(palette.isDark ? 0.78 : 0.84))
                .ignoresSafeArea(edges: .bottom)
        )
        .animation(.easeOut(duration: 0.18), value: selectedTab)
    }

    private func bottomTabItem(_ tab: YubraeTab) -> some View {
        let isSelected = selectedTab == tab
        let pendingCount = tab == .transactions ? store.pendingCount : 0
        let showUpdateDot = tab == .mine && hasUpdateBadge

        return Button {
            if selectedTab != tab {
                if abs(tab.rawValue - selectedTab.rawValue) == 1 {
                    withAnimation(.easeOut(duration: 0.2)) {
                        selectedTab = tab
                    }
                } else {
                    selectedTab = tab
                }
            } else {
                triggerImpactFeedback(.light)
            }
        } label: {
            VStack(spacing: 3) {
                ZStack {
                    Capsule(style: .continuous)
                        .fill(palette.accentSoftFill)
                        .frame(width: isSelected ? 48 : 26, height: 28)
                        .opacity(isSelected ? 1 : 0)

                    Image(systemName: isSelected ? tab.selectedSystemImage : tab.systemImage)
                        .font(.system(size: 17, weight: isSelected ? .semibold : .regular))
                        .foregroundColor(isSelected ? palette.primary : palette.textSecondary)
                        .scaleEffect(isSelected ? 1.04 : 1.0)
                        .overlay(alignment: .topTrailing) {
                            if pendingCount > 0 {
                                Text(pendingCount > 99 ? "99+" : "\(pendingCount)")
                                    .font(.system(size: 9, weight: .bold))
                                    .foregroundColor(.white)
                                    .padding(.horizontal, 4.5)
                                    .padding(.vertical, 1.5)
                                    .background(Capsule().fill(palette.dangerColor))
                                    .overlay(Capsule().strokeBorder(palette.surface, lineWidth: 1))
                                    .fixedSize()
                                    .offset(x: 11, y: -5)
                            } else if showUpdateDot {
                                Circle()
                                    .fill(palette.dangerColor)
                                    .frame(width: 7, height: 7)
                                    .overlay(Circle().strokeBorder(palette.surface, lineWidth: 1))
                                    .offset(x: 5, y: -3)
                            }
                        }
                }
                .frame(height: 28)

                Text(tab.title)
                    .font(.system(size: 10.5, weight: isSelected ? .semibold : .regular))
                    .foregroundColor(isSelected ? palette.primary : palette.textSecondary)
                    .lineLimit(1)
            }
            .frame(maxWidth: .infinity)
            .frame(height: 52)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(tab.title)
        .accessibilityAddTraits(isSelected ? AccessibilityTraits.isSelected : AccessibilityTraits())
    }

    private var centerQuickRecordButton: some View {
        ZStack {
            Circle()
                .fill(
                    LinearGradient(
                        colors: [palette.primary, palette.primary.opacity(0.84)],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    )
                )
                .frame(width: 44, height: 44)
                .shadow(color: palette.primary.opacity(0.28), radius: 8, x: 0, y: 4)

            Image(systemName: "plus")
                .font(.system(size: 19, weight: .bold))
                .foregroundColor(palette.onPrimary)
        }
        .frame(maxWidth: .infinity)
        .frame(height: 52)
        .scaleEffect(isCenterButtonPressed ? 0.91 : 1.0)
        .animation(.spring(response: 0.22, dampingFraction: 0.72), value: isCenterButtonPressed)
        .contentShape(Rectangle())
        .onTapGesture {
            triggerImpactFeedback(.medium)
            quickRecordContext = QuickRecordContext(editingTransaction: nil)
        }
        .onLongPressGesture(minimumDuration: 0.42, maximumDistance: 20, pressing: { pressing in
            isCenterButtonPressed = pressing
        }, perform: {
            triggerImpactFeedback(.heavy)
            smartInputLaunch = SmartInputLaunchRequest(initialMode: .text)
        })
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("记一笔")
        .accessibilityHint("轻点手动记一笔，长按触发 AI 智能记账")
        .accessibilityAddTraits(.isButton)
    }

    private var floatingAiButton: some View {
        Button {
            triggerImpactFeedback(.light)
            smartInputLaunch = SmartInputLaunchRequest(initialMode: .text)
        } label: {
            Image(systemName: "sparkles")
                .font(.system(size: 17, weight: .semibold))
                .foregroundColor(palette.onPrimary)
                .frame(width: 48, height: 48)
                .background(
                    Circle()
                        .fill(
                            LinearGradient(
                                colors: [palette.primary, palette.primary.opacity(0.86)],
                                startPoint: .topLeading,
                                endPoint: .bottomTrailing
                            )
                        )
                        .shadow(color: palette.primary.opacity(0.30), radius: 10, x: 0, y: 5)
                )
        }
        .buttonStyle(.plain)
        .accessibilityLabel("AI 智能记账")
    }

    private func triggerImpactFeedback(_ style: UIImpactFeedbackGenerator.FeedbackStyle) {
        let generator = UIImpactFeedbackGenerator(style: style)
        generator.prepare()
        generator.impactOccurred()
    }

    // MARK: - Lock & Privacy Overlays

    private var biometricLockOverlay: some View {
        ZStack {
            palette.background.ignoresSafeArea()
            VStack(spacing: 20) {
                YubraeBrandMark(size: 72, primaryColor: palette.primary)
                VStack(spacing: 6) {
                    Text("余白已锁定")
                        .font(.title2.weight(.bold))
                        .foregroundColor(palette.textPrimary)
                    Text("通过面容 ID 或设备密码验证以保护账本隐私")
                        .font(.subheadline)
                        .foregroundColor(palette.textSecondary)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 32)
                }
                if let err = biometricErrorText {
                    Text(err)
                        .font(.footnote)
                        .foregroundColor(palette.dangerColor)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 32)
                }
                Button {
                    authenticateBiometrics()
                } label: {
                    Label("立即解锁", systemImage: "faceid")
                        .font(.headline)
                        .foregroundColor(palette.onPrimary)
                        .padding(.horizontal, 28)
                        .padding(.vertical, 12)
                        .background(Capsule().fill(palette.primary))
                }
                .padding(.top, 8)
            }
        }
    }

    private var privacyBlurOverlay: some View {
        ZStack {
            Rectangle()
                .fill(.ultraThinMaterial)
                .ignoresSafeArea()
            VStack(spacing: 14) {
                YubraeBrandMark(size: 64, primaryColor: palette.primary)
                Text("余白 · 隐私保护中")
                    .font(.headline)
                    .foregroundColor(palette.textPrimary)
            }
        }
    }

    // MARK: - KLBK File Passphrase Sheet

    private var klbkImportPassphraseSheet: some View {
        let isPassphraseEmpty = klbkPassphraseInput.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty

        return NavigationStack {
            ScrollView {
                VStack(spacing: 18) {
                    VStack(spacing: 10) {
                        ZStack {
                            Circle()
                                .fill(palette.accentSoftFill)
                                .frame(width: 58, height: 58)
                            Image(systemName: "key.fill")
                                .font(.system(size: 24, weight: .semibold))
                                .foregroundColor(palette.primary)
                        }

                        Text("解密 .klbk 加密备份")
                            .font(.headline.weight(.bold))
                            .foregroundColor(palette.textPrimary)

                        Text("支持导入 Android 端或 iOS 端导出的余白/棱账 .klbk 备份文件，恢复后会覆盖当前账本数据。")
                            .font(.footnote)
                            .foregroundColor(palette.textSecondary)
                            .multilineTextAlignment(.center)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    .padding(.top, 6)

                    YubraeCard(palette: palette) {
                        VStack(alignment: .leading, spacing: 10) {
                            Label("备份加密口令", systemImage: "lock.shield.fill")
                                .font(.subheadline.weight(.semibold))
                                .foregroundColor(palette.textPrimary)

                            HStack(spacing: 10) {
                                Image(systemName: "lock")
                                    .font(.subheadline)
                                    .foregroundColor(palette.textTertiary)
                                SecureField("请输入备份加密口令", text: $klbkPassphraseInput)
                                    .textInputAutocapitalization(.never)
                                    .autocorrectionDisabled(true)
                                    .font(.subheadline)
                            }
                            .padding(.horizontal, 12)
                            .padding(.vertical, 11)
                            .background(
                                RoundedRectangle(cornerRadius: 12, style: .continuous)
                                    .fill(palette.surfaceSecondary)
                            )
                            .overlay(
                                RoundedRectangle(cornerRadius: 12, style: .continuous)
                                    .strokeBorder(palette.outline, lineWidth: 0.8)
                            )
                        }
                    }

                    Button {
                        guard let data = pendingKlbkData else { return }
                        do {
                            let summary = try store.importEncryptedBackup(data, passphrase: klbkPassphraseInput)
                            store.toastMessage = summary.descriptionZh
                            pendingKlbkData = nil
                            klbkPassphraseInput = ""
                            showKlbkPassphraseSheet = false
                        } catch {
                            store.lastErrorMessage = error.localizedDescription
                        }
                    } label: {
                        Text("确认解密并恢复")
                            .font(.headline)
                            .foregroundColor(palette.onPrimary)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 14)
                            .background(
                                RoundedRectangle(cornerRadius: 14, style: .continuous)
                                    .fill(
                                        LinearGradient(
                                            colors: [palette.primary, palette.primary.opacity(0.84)],
                                            startPoint: .topLeading,
                                            endPoint: .bottomTrailing
                                        )
                                    )
                            )
                            .opacity(isPassphraseEmpty ? 0.48 : 1.0)
                    }
                    .buttonStyle(.plain)
                    .disabled(isPassphraseEmpty)
                }
                .padding(18)
            }
            .background(palette.background.ignoresSafeArea())
            .navigationTitle("导入加密备份")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("取消") {
                        pendingKlbkData = nil
                        showKlbkPassphraseSheet = false
                    }
                }
            }
        }
        .presentationDetents([.medium, .large])
    }

    // MARK: - Lifecycle, URL Scheme & Update Logic

    private func runLaunchTasks() {
        let executed = store.processDueRecurringRules()
        if executed > 0 {
            presentToast("已自动入账 \(executed) 笔到期周期账单", isError: false)
        }
        if store.settings.biometricLockEnabled {
            isBiometricLocked = true
            authenticateBiometrics()
        }
        if store.settings.autoCheckUpdateEnabled {
            let twelveHoursMillis: Int64 = 12 * 3600 * 1000
            let now = currentEpochMillis()
            if now - store.settings.lastUpdateCheckMillis > twelveHoursMillis {
                Task { await performUpdateCheck(manual: false) }
            }
        }
    }

    private func handleScenePhaseChange(_ newPhase: ScenePhase) {
        switch newPhase {
        case .background:
            if store.settings.biometricLockEnabled {
                isBiometricLocked = true
            }
        case .active:
            if isBiometricLocked && store.settings.biometricLockEnabled {
                authenticateBiometrics()
            }
            consumePendingSmartInputIfReady()
        default:
            break
        }
    }

    private func authenticateBiometrics() {
        let context = LAContext()
        context.localizedFallbackTitle = "使用设备密码"
        var authError: NSError?
        guard context.canEvaluatePolicy(.deviceOwnerAuthentication, error: &authError) else {
            // 无生物识别且未设置系统密码（如模拟器）时直接放行，避免锁死
            isBiometricLocked = false
            biometricErrorText = nil
            consumePendingSmartInputIfReady()
            return
        }
        context.evaluatePolicy(
            .deviceOwnerAuthentication,
            localizedReason: "验证身份以解锁余白账本"
        ) { success, error in
            DispatchQueue.main.async {
                if success {
                    withAnimation(.easeOut(duration: 0.2)) {
                        isBiometricLocked = false
                        biometricErrorText = nil
                    }
                    consumePendingSmartInputIfReady()
                } else {
                    biometricErrorText = error?.localizedDescription ?? "验证未通过，请重试"
                }
            }
        }
    }

    private func consumePendingSmartInputIfReady() {
        guard !isBiometricLocked else { return }
        if let launch = store.consumePendingSmartInputLaunch() {
            smartInputLaunch = launch
        }
    }

    private func handleIncomingURL(_ url: URL) {
        if let launch = ExternalBillUrlParser.parseSmartInputLaunch(url: url) {
            store.pendingSmartInputLaunch = launch
            consumePendingSmartInputIfReady()
            return
        }

        if url.isFileURL && url.pathExtension.lowercased() == "klbk" {
            let accessed = url.startAccessingSecurityScopedResource()
            defer {
                if accessed { url.stopAccessingSecurityScopedResource() }
            }
            do {
                let data = try Data(contentsOf: url)
                let savedPass = store.settings.backupPassphrase.trimmingCharacters(in: .whitespacesAndNewlines)
                if !savedPass.isEmpty, let summary = try? store.importEncryptedBackup(data, passphrase: savedPass) {
                    presentToast(summary.descriptionZh, isError: false)
                } else {
                    pendingKlbkData = data
                    klbkPassphraseInput = savedPass
                    showKlbkPassphraseSheet = true
                }
            } catch {
                presentToast("读取备份文件失败：\(error.localizedDescription)", isError: true)
            }
            return
        }

        if ExternalBillUrlParser.isSupportedURL(url) {
            do {
                if let saved = try store.handleIncomingURL(url) {
                    let amt = MoneyAmount.format(saved.origAmountMicros, currencyCode: saved.origCurrency)
                    let label = saved.merchant.isEmpty ? (store.category(byId: saved.categoryId)?.name ?? "快捷记账") : saved.merchant
                    presentToast("快捷入账成功：\(label) \(amt)", isError: false)
                    selectedTab = .transactions
                } else {
                    presentToast("链接缺少有效金额，未创建账单", isError: true)
                }
            } catch {
                presentToast("快捷入账失败：\(error.localizedDescription)", isError: true)
            }
        }
    }

    @MainActor
    private func performUpdateCheck(manual: Bool) async {
        guard !isCheckingUpdate else { return }
        isCheckingUpdate = true
        defer { isCheckingUpdate = false }

        let result = await YubraeRuntime.updateChecker.checkLatestRelease(
            currentVersion: AppUpdateChecker.currentAppVersion,
            mirrorPrefix: store.settings.updateMirrorPrefix
        )
        store.updateSettings { $0.lastUpdateCheckMillis = currentEpochMillis() }

        switch result {
        case .updateAvailable(let info):
            availableUpdate = info
            if manual || info.tagName != store.settings.ignoredUpdateTag {
                if manual {
                    showUpdateSheet = true
                } else {
                    presentToast("发现新版本 \(info.tagName)，可在「我的」查看更新", isError: false)
                }
            }
        case .upToDate(let currentVersion, _):
            availableUpdate = nil
            if manual {
                presentToast("当前已是最新版本 v\(currentVersion)", isError: false)
            }
        case .failed(let reason, _):
            if manual {
                presentToast(reason, isError: true)
            }
        }
    }

    private func presentToast(_ message: String, isError: Bool) {
        toastDismissTask?.cancel()
        withAnimation(.spring(response: 0.32, dampingFraction: 0.85)) {
            activeToastText = message
            activeToastIsError = isError
        }
        toastDismissTask = Task {
            try? await Task.sleep(nanoseconds: 2_800_000_000)
            guard !Task.isCancelled else { return }
            await MainActor.run {
                withAnimation(.easeOut(duration: 0.22)) {
                    activeToastText = nil
                }
            }
        }
    }
}

// MARK: - GitHub Release Update Sheet

public struct UpdateReleaseSheet: View {
    @ObservedObject public var store: LedgerStore
    public let updateInfo: AppUpdateInfo
    public let onDismiss: () -> Void

    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.openURL) private var openURL

    @State private var mirrorInput: String
    @State private var mirrorValidationError: String?

    public init(store: LedgerStore, updateInfo: AppUpdateInfo, onDismiss: @escaping () -> Void) {
        self.store = store
        self.updateInfo = updateInfo
        self.onDismiss = onDismiss
        _mirrorInput = State(initialValue: store.settings.updateMirrorPrefix)
    }

    private var palette: YubraePalette {
        YubraeTheme.palette(for: store.settings, colorScheme: colorScheme)
    }

    private var resolvedIpaUrlString: String? {
        guard let rawIpa = updateInfo.ipaDownloadUrl, !rawIpa.isEmpty else { return nil }
        return AppUpdateChecker.applyMirrorPrefix(rawUrl: rawIpa, mirrorPrefix: store.settings.updateMirrorPrefix)
    }

    public var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    // 头部版本卡片
                    HStack(spacing: 14) {
                        YubraeBrandMark(size: 52, primaryColor: palette.primary)
                        VStack(alignment: .leading, spacing: 4) {
                            Text("发现新版本 \(updateInfo.tagName)")
                                .font(.title3.weight(.bold))
                                .foregroundColor(palette.textPrimary)
                                .lineLimit(1)
                                .minimumScaleFactor(0.85)
                            Text("当前版本 v\(AppUpdateChecker.currentAppVersion)  →  最新版 v\(updateInfo.versionName)")
                                .font(.subheadline)
                                .foregroundColor(palette.textSecondary)
                                .lineLimit(1)
                                .minimumScaleFactor(0.85)
                        }
                        Spacer()
                    }
                    .padding(16)
                    .background(
                        RoundedRectangle(cornerRadius: 16, style: .continuous)
                            .fill(palette.accentSoftFill)
                    )

                    // IPA 安装包信息
                    YubraeCard(palette: palette) {
                        VStack(alignment: .leading, spacing: 10) {
                            Label("安装包信息", systemImage: "shippingbox.fill")
                                .font(.subheadline.weight(.semibold))
                                .foregroundColor(palette.primary)
                            if let assetName = updateInfo.ipaAssetName {
                                HStack(spacing: 8) {
                                    Text(assetName)
                                        .font(.subheadline.monospaced())
                                        .foregroundColor(palette.textPrimary)
                                        .lineLimit(1)
                                        .truncationMode(.middle)
                                    Spacer(minLength: 6)
                                    Text(YubraeTheme.formatFileSize(updateInfo.ipaAssetSizeBytes))
                                        .font(.footnote.weight(.semibold))
                                        .foregroundColor(palette.textSecondary)
                                        .lineLimit(1)
                                        .fixedSize(horizontal: true, vertical: false)
                                }
                            } else {
                                Text("该 Release 暂未附带 .ipa 资产，可直接前往 Release 页面查看详情。")
                                    .font(.footnote)
                                    .foregroundColor(palette.textSecondary)
                            }
                            Text("iOS 安装说明：点击下载 .ipa 后，在 Safari 下载目录中通过 AltStore、SideStore、TrollStore 或其他自签工具导入安装更新，原有本地账本与设置会自动保留。")
                                .font(.caption)
                                .foregroundColor(palette.textSecondary)
                                .fixedSize(horizontal: false, vertical: true)
                        }
                    }

                    // 更新日志
                    YubraeCard(palette: palette) {
                        let noteLines = YubraeReleaseNotesFormatter.attributedLines(updateInfo.releaseNotes)
                        VStack(alignment: .leading, spacing: 8) {
                            Label(updateInfo.releaseTitle, systemImage: "doc.text.fill")
                                .font(.subheadline.weight(.semibold))
                                .foregroundColor(palette.textPrimary)
                            if noteLines.isEmpty {
                                Text("本次版本包含体验优化与稳定性改进。")
                                    .font(.subheadline)
                                    .foregroundColor(palette.textSecondary)
                                    .frame(maxWidth: .infinity, alignment: .leading)
                            } else {
                                VStack(alignment: .leading, spacing: 6) {
                                    ForEach(Array(noteLines.enumerated()), id: \.offset) { _, line in
                                        Text(line)
                                            .font(.subheadline)
                                            .foregroundColor(palette.textSecondary)
                                            .fixedSize(horizontal: false, vertical: true)
                                            .frame(maxWidth: .infinity, alignment: .leading)
                                    }
                                }
                            }
                        }
                    }

                    // 镜像加速前缀设置
                    YubraeCard(palette: palette) {
                        VStack(alignment: .leading, spacing: 8) {
                            Label("GitHub 下载镜像前缀（可选）", systemImage: "bolt.horizontal.circle.fill")
                                .font(.subheadline.weight(.semibold))
                                .foregroundColor(palette.textPrimary)
                            HStack(spacing: 8) {
                                TextField("如 https://gh-proxy.com/", text: $mirrorInput)
                                    .textInputAutocapitalization(.never)
                                    .autocorrectionDisabled(true)
                                    .keyboardType(.URL)
                                    .font(.subheadline)
                                    .padding(10)
                                    .background(RoundedRectangle(cornerRadius: 10).fill(palette.surfaceSecondary))
                                Button {
                                    saveMirrorPrefix()
                                } label: {
                                    Text("保存")
                                        .font(.subheadline.weight(.semibold))
                                        .lineLimit(1)
                                        .fixedSize(horizontal: true, vertical: false)
                                        .foregroundColor(palette.onPrimary)
                                        .padding(.horizontal, 14)
                                        .padding(.vertical, 10)
                                        .background(RoundedRectangle(cornerRadius: 10).fill(palette.primary))
                                }
                                .buttonStyle(.plain)
                            }
                            if let err = mirrorValidationError {
                                Text(err)
                                    .font(.caption)
                                    .foregroundColor(palette.dangerColor)
                            }
                        }
                    }

                    // 操作按钮
                    VStack(spacing: 10) {
                        if let ipaUrlStr = resolvedIpaUrlString, let url = URL(string: ipaUrlStr) {
                            Button {
                                openURL(url)
                            } label: {
                                Label("在 Safari 下载 .ipa 安装包", systemImage: "arrow.down.circle.fill")
                                    .font(.headline)
                                    .foregroundColor(palette.onPrimary)
                                    .frame(maxWidth: .infinity)
                                    .padding(.vertical, 14)
                                    .background(RoundedRectangle(cornerRadius: 14, style: .continuous).fill(palette.primary))
                            }
                        }

                        if let pageUrl = URL(string: updateInfo.htmlUrl) {
                            Button {
                                openURL(pageUrl)
                            } label: {
                                Label("打开 GitHub Release 页面", systemImage: "safari.fill")
                                    .font(.subheadline.weight(.semibold))
                                    .foregroundColor(palette.primary)
                                    .frame(maxWidth: .infinity)
                                    .padding(.vertical, 13)
                                    .background(
                                        RoundedRectangle(cornerRadius: 14, style: .continuous)
                                            .fill(palette.accentSoftFill)
                                    )
                            }
                        }

                        Button(role: .destructive) {
                            store.updateSettings { $0.ignoredUpdateTag = updateInfo.tagName }
                            store.toastMessage = "已忽略版本 \(updateInfo.tagName)"
                            onDismiss()
                        } label: {
                            Text("忽略此版本提醒")
                                .font(.footnote)
                                .padding(.vertical, 6)
                        }
                    }
                }
                .padding(16)
            }
            .background(palette.background.ignoresSafeArea())
            .navigationTitle("发现新版本")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("关闭") { onDismiss() }
                }
            }
        }
    }

    private func saveMirrorPrefix() {
        switch AppUpdateChecker.validateMirrorPrefix(mirrorInput) {
        case .valid(let normalized):
            mirrorValidationError = nil
            mirrorInput = normalized
            store.updateSettings { $0.updateMirrorPrefix = normalized }
            store.toastMessage = normalized.isEmpty ? "已清除镜像加速前缀" : "已保存镜像加速前缀"
        case .invalid(let reason):
            mirrorValidationError = reason
        }
    }
}

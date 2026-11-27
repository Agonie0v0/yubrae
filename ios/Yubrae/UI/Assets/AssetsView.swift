import SwiftUI
import UIKit

private enum AssetActiveSheet: Identifiable {
    case editAccount(AccountDto?, String)
    case calibrate(AccountDto)
    case deleteAccount(AccountDto)
    case reorder
    case hiddenManager
    case addDebt
    case settleDebt(DebtRecordDto)
    case settleReimburse

    var id: String {
        switch self {
        case .editAccount(let acc, let group):
            return "edit-\(acc?.id ?? 0)-\(group)"
        case .calibrate(let acc):
            return "cal-\(acc.id)"
        case .deleteAccount(let acc):
            return "del-\(acc.id)"
        case .reorder:
            return "reorder"
        case .hiddenManager:
            return "hidden"
        case .addDebt:
            return "addDebt"
        case .settleDebt(let debt):
            return "settleDebt-\(debt.id)"
        case .settleReimburse:
            return "settleReimburse"
        }
    }
}

private struct AssetQuickRecordPreset: Identifiable {
    let id = UUID()
    let type: String
    let accountId: Int64?
    let toAccountId: Int64?
    let amountMicros: Int64?
}

private struct AssetCompositionSlice: Identifiable {
    let id: String
    let title: String
    let amountMicros: Int64
    let ratio: Double
    let color: Color
}

public struct AssetsView: View {
    @ObservedObject public var store: LedgerStore
    @Environment(\.colorScheme) private var colorScheme

    @State private var displayCurrencyOverride: String?
    @State private var showHiddenAccounts = false
    @State private var showSearchBar = false
    @State private var searchQuery = ""
    @State private var showArchivedSection = false
    @State private var activeSheet: AssetActiveSheet?
    @State private var quickRecordPreset: AssetQuickRecordPreset?

    public init(store: LedgerStore) {
        self.store = store
    }

    private var palette: YubraePalette {
        YubraeTheme.palette(for: store.settings, colorScheme: colorScheme)
    }

    private var activeDisplayCurrency: String {
        displayCurrencyOverride ?? store.settings.baseCurrency
    }

    private var netWorth: NetWorthSummary {
        store.computeNetWorth(displayCurrency: activeDisplayCurrency)
    }

    private var hiddenAccountIdSet: Set<Int64> {
        Set(store.settings.hiddenAssetAccountIds)
    }

    private var hiddenModuleSet: Set<String> {
        Set(store.settings.hiddenAssetModules)
    }

    private var collapsedGroupSet: Set<String> {
        Set(store.settings.collapsedAssetGroups.map { $0.uppercased() })
    }

    private var orderedGroups: [AccountGroup] {
        var seen = Set<AccountGroup>()
        var result: [AccountGroup] = []
        for raw in store.settings.assetGroupOrder {
            if let group = AccountGroup(rawValue: raw.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()),
               seen.insert(group).inserted {
                result.append(group)
            }
        }
        for group in AccountGroup.allCases where seen.insert(group).inserted {
            result.append(group)
        }
        return result
    }

    private var trimmedSearchQuery: String {
        searchQuery.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private var isSearching: Bool {
        !trimmedSearchQuery.isEmpty
    }

    private var pendingReimburseTxs: [TransactionDto] {
        store.transactions.filter { $0.reimburseStatus == ReimburseStatus.PENDING }
    }

    private var openDebts: [DebtRecordDto] {
        store.debts.filter { $0.status == DebtStatus.OPEN }
    }

    private var visibleActiveAccounts: [AccountDto] {
        let base = store.activeAccounts.filter { acc in
            if !showHiddenAccounts && hiddenAccountIdSet.contains(acc.id) {
                return false
            }
            if acc.groupType.uppercased() == AccountGroup.INVEST
                && hiddenModuleSet.contains(AssetHiddenModule.INVEST)
                && !showHiddenAccounts {
                return false
            }
            return true
        }
        let sorted = base.sorted { lhs, rhs in
            if lhs.sortOrder != rhs.sortOrder { return lhs.sortOrder < rhs.sortOrder }
            return lhs.id < rhs.id
        }
        guard isSearching else { return sorted }
        return sorted.filter { matchesAccountSearch($0, query: trimmedSearchQuery) }
    }

    private var archivedAccounts: [AccountDto] {
        let base = store.accounts.filter(\.archived).sorted { lhs, rhs in
            if lhs.sortOrder != rhs.sortOrder { return lhs.sortOrder < rhs.sortOrder }
            return lhs.id < rhs.id
        }
        guard isSearching else { return base }
        return base.filter { matchesAccountSearch($0, query: trimmedSearchQuery) }
    }

    private var totalSearchHitCount: Int {
        visibleActiveAccounts.count + archivedAccounts.count
    }

    /// 资产构成比例（仅统计计入净资产且折算余额 > 0 的活跃账户）
    private var assetCompositionSlices: [AssetCompositionSlice] {
        let baseCur = CurrencyCatalog.normalize(store.settings.baseCurrency)
        var liquidMicros: Int64 = 0
        var investMicros: Int64 = 0
        var foreignMicros: Int64 = 0

        for acc in store.activeAccounts where acc.includeInNetWorth {
            let converted = netWorth.accountConvertedBalances[acc.id] ?? 0
            guard converted > 0 else { continue }
            let groupUpper = acc.groupType.uppercased()
            let curUpper = CurrencyCatalog.normalize(acc.currencyCode)
            if groupUpper == AccountGroup.INVEST {
                investMicros += converted
            } else if groupUpper == AccountGroup.FOREIGN || curUpper != baseCur {
                foreignMicros += converted
            } else {
                liquidMicros += converted
            }
        }

        let total = liquidMicros + investMicros + foreignMicros
        guard total > 0 else { return [] }

        let raw: [(String, String, Int64, Color)] = [
            ("liquid", "流动资金", liquidMicros, Color(argbHex: 0xFF6EE7B7)),
            ("invest", "理财投资", investMicros, Color(argbHex: 0xFFFCD34D)),
            ("foreign", "外币资产", foreignMicros, Color(argbHex: 0xFF93C5FD))
        ]
        return raw.compactMap { item in
            guard item.2 > 0 else { return nil }
            return AssetCompositionSlice(
                id: item.0,
                title: item.1,
                amountMicros: item.2,
                ratio: Double(item.2) / Double(total),
                color: item.3
            )
        }
    }

    public var body: some View {
        NavigationStack {
            ScrollView {
                LazyVStack(spacing: 14) {
                    netWorthHeroCard

                    if showSearchBar || isSearching {
                        searchBarCard
                            .transition(.move(edge: .top).combined(with: .opacity))
                    }

                    if !hiddenAccountIdSet.isEmpty || !hiddenModuleSet.isEmpty {
                        hiddenVisibilityStrip
                    }

                    if !isSearching && !hiddenModuleSet.contains(AssetHiddenModule.REIMBURSE) && !pendingReimburseTxs.isEmpty {
                        reimbursementModuleCard
                    }

                    if !isSearching && !hiddenModuleSet.contains(AssetHiddenModule.DEBT) {
                        debtModuleCard
                    }

                    groupedAccountsSection

                    if !archivedAccounts.isEmpty {
                        archivedAccountsCard
                    }
                }
                .padding(.horizontal, 16)
                .padding(.top, 10)
                .padding(.bottom, 40)
            }
            .background(palette.background.ignoresSafeArea())
            .navigationTitle("资产")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button {
                        activeSheet = .hiddenManager
                    } label: {
                        Image(systemName: "eye.trianglebadge.exclamationmark")
                            .foregroundColor(palette.textPrimary)
                    }
                    .accessibilityLabel("隐藏资产管理")
                }
                ToolbarItem(placement: .navigationBarTrailing) {
                    HStack(spacing: 14) {
                        Button {
                            store.updateSettings { $0.privacyMaskEnabled.toggle() }
                        } label: {
                            Image(systemName: store.settings.privacyMaskEnabled ? "eye.slash" : "eye")
                                .foregroundColor(palette.textPrimary)
                        }
                        .accessibilityLabel("切换隐私遮罩")
                        Button {
                            activeSheet = .editAccount(nil, AccountGroup.WALLET)
                        } label: {
                            Image(systemName: "plus.circle.fill")
                                .foregroundColor(palette.primary)
                        }
                        .accessibilityLabel("新建账户")
                    }
                }
            }
            .sheet(item: $activeSheet) { sheet in
                sheetDestination(for: sheet)
                    .yubraeSheetStyle(palette: palette)
            }
            .fullScreenCover(item: $quickRecordPreset) { preset in
                QuickRecordSheet(
                    store: store,
                    editingTransaction: nil,
                    initialType: preset.type,
                    initialAccountId: preset.accountId,
                    initialToAccountId: preset.toAccountId,
                    initialAmountMicros: preset.amountMicros
                )
                .yubraeSheetStyle(palette: palette)
            }
        }
    }

    @ViewBuilder
    private func sheetDestination(for sheet: AssetActiveSheet) -> some View {
        switch sheet {
        case .editAccount(let account, let initialGroup):
            AccountEditorSheet(store: store, editingAccount: account, initialGroupType: initialGroup)
        case .calibrate(let account):
            BalanceCalibrationSheet(store: store, account: account)
        case .deleteAccount(let account):
            AccountDeleteSheet(store: store, account: account) {
                activeSheet = nil
            }
        case .reorder:
            AssetReorderSheet(store: store, initialGroupOrder: orderedGroups)
        case .hiddenManager:
            HiddenAssetsManagerSheet(store: store)
        case .addDebt:
            AddDebtSheet(store: store)
        case .settleDebt(let debt):
            SettleDebtSheet(store: store, debt: debt)
        case .settleReimburse:
            SettleReimburseSheet(store: store, pendingTxs: pendingReimburseTxs)
        }
    }

    // MARK: - Hero Card (Net Worth + Composition Bar + 4-Action Quick Strip)

    private var netWorthHeroCard: some View {
        let masked = store.settings.privacyMaskEnabled
        let cur = netWorth.displayCurrency
        let nwStr = YubraeTheme.masked(MoneyAmount.format(netWorth.netWorthMicros, currencyCode: cur), isMasked: masked)
        let assetsStr = YubraeTheme.masked(MoneyAmount.format(netWorth.totalAssetsMicros, currencyCode: cur), isMasked: masked)
        let liabStr = YubraeTheme.masked(MoneyAmount.format(netWorth.totalLiabilitiesMicros, currencyCode: cur), isMasked: masked)
        let slices = assetCompositionSlices

        return VStack(alignment: .leading, spacing: 14) {
            HStack(spacing: 8) {
                Text("净资产 (\(cur))")
                    .font(.subheadline.weight(.medium))
                    .foregroundColor(.white.opacity(0.88))
                    .lineLimit(1)
                    .minimumScaleFactor(0.85)
                Spacer(minLength: 8)
                Menu {
                    ForEach(CurrencyCatalog.all) { info in
                        Button("\(info.flag) \(info.code) · \(info.nameZh)") {
                            displayCurrencyOverride = info.code
                        }
                    }
                } label: {
                    HStack(spacing: 4) {
                        Text(CurrencyCatalog.find(cur).flag)
                        Text(cur)
                            .font(.caption.weight(.bold))
                        Image(systemName: "chevron.down")
                            .font(.caption2)
                    }
                    .lineLimit(1)
                    .fixedSize(horizontal: true, vertical: false)
                    .foregroundColor(.white)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 5)
                    .background(Capsule().fill(Color.white.opacity(0.18)))
                }
            }

            Text(nwStr)
                .font(.system(size: 32, weight: .bold, design: .rounded).monospacedDigit())
                .foregroundColor(.white)
                .minimumScaleFactor(0.55)
                .lineLimit(1)

            HStack(spacing: 16) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("总资产")
                        .font(.caption2)
                        .foregroundColor(.white.opacity(0.78))
                        .lineLimit(1)
                        .fixedSize(horizontal: true, vertical: false)
                    Text(assetsStr)
                        .font(.subheadline.weight(.semibold).monospacedDigit())
                        .foregroundColor(.white)
                        .lineLimit(1)
                        .minimumScaleFactor(0.65)
                }
                .frame(maxWidth: .infinity, alignment: .leading)

                Divider()
                    .frame(height: 24)
                    .background(Color.white.opacity(0.3))

                VStack(alignment: .leading, spacing: 2) {
                    Text("总负债")
                        .font(.caption2)
                        .foregroundColor(.white.opacity(0.78))
                        .lineLimit(1)
                        .fixedSize(horizontal: true, vertical: false)
                    Text(liabStr)
                        .font(.subheadline.weight(.semibold).monospacedDigit())
                        .foregroundColor(.white)
                        .lineLimit(1)
                        .minimumScaleFactor(0.65)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }

            if !slices.isEmpty {
                assetCompositionBar(slices: slices, masked: masked)
            }

            heroQuickActionStrip
        }
        .padding(18)
        .background(
            RoundedRectangle(cornerRadius: 22, style: .continuous)
                .fill(palette.heroGradient)
                .shadow(color: palette.primary.opacity(0.22), radius: 14, x: 0, y: 8)
        )
    }

    private func assetCompositionBar(slices: [AssetCompositionSlice], masked: Bool) -> some View {
        VStack(alignment: .leading, spacing: 7) {
            GeometryReader { geo in
                let gap: CGFloat = slices.count > 1 ? 2 : 0
                let usableWidth = max(0, geo.size.width - gap * CGFloat(max(0, slices.count - 1)))
                HStack(spacing: gap) {
                    ForEach(slices) { slice in
                        RoundedRectangle(cornerRadius: 4, style: .continuous)
                            .fill(slice.color)
                            .frame(width: max(6, usableWidth * CGFloat(slice.ratio)))
                    }
                }
            }
            .frame(height: 7)
            .padding(2)
            .background(Capsule().fill(Color.white.opacity(0.14)))

            HStack(spacing: 12) {
                ForEach(slices) { slice in
                    let pctText = masked ? "••%" : "\(Int((slice.ratio * 100).rounded()))%"
                    HStack(spacing: 4) {
                        Circle()
                            .fill(slice.color)
                            .frame(width: 6, height: 6)
                        Text("\(slice.title) \(pctText)")
                            .font(.system(size: 11, weight: .medium).monospacedDigit())
                            .foregroundColor(.white.opacity(0.88))
                            .lineLimit(1)
                            .minimumScaleFactor(0.8)
                    }
                }
                Spacer(minLength: 0)
            }
        }
    }

    private var heroQuickActionStrip: some View {
        HStack(spacing: 8) {
            heroQuickActionButton(
                title: "搜索",
                systemImage: showSearchBar ? "magnifyingglass.circle.fill" : "magnifyingglass",
                highlighted: showSearchBar || isSearching
            ) {
                withAnimation(.spring(response: 0.28, dampingFraction: 0.86)) {
                    if showSearchBar && !isSearching {
                        showSearchBar = false
                    } else if showSearchBar && isSearching {
                        searchQuery = ""
                        showSearchBar = false
                    } else {
                        showSearchBar = true
                    }
                }
            }

            heroQuickActionButton(
                title: "排序",
                systemImage: "arrow.up.arrow.down",
                highlighted: false
            ) {
                activeSheet = .reorder
            }

            heroQuickActionButton(
                title: "转账",
                systemImage: "arrow.left.arrow.right",
                highlighted: false
            ) {
                quickRecordPreset = AssetQuickRecordPreset(
                    type: TransactionType.TRANSFER,
                    accountId: nil,
                    toAccountId: nil,
                    amountMicros: nil
                )
            }

            heroQuickActionButton(
                title: "新建账户",
                systemImage: "plus.circle",
                highlighted: false
            ) {
                activeSheet = .editAccount(nil, AccountGroup.WALLET)
            }
        }
    }

    private func heroQuickActionButton(
        title: String,
        systemImage: String,
        highlighted: Bool,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            VStack(spacing: 4) {
                Image(systemName: systemImage)
                    .font(.system(size: 15, weight: .semibold))
                Text(title)
                    .font(.system(size: 11, weight: .semibold))
                    .lineLimit(1)
                    .fixedSize(horizontal: true, vertical: false)
            }
            .foregroundColor(.white)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 8)
            .padding(.horizontal, 4)
            .background(
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .fill(Color.white.opacity(highlighted ? 0.28 : 0.15))
            )
            .overlay(
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .strokeBorder(Color.white.opacity(highlighted ? 0.45 : 0.14), lineWidth: 0.7)
            )
        }
        .buttonStyle(.plain)
    }

    // MARK: - Search Bar & Hidden Strip

    private var searchBarCard: some View {
        HStack(spacing: 8) {
            Image(systemName: "magnifyingglass")
                .font(.subheadline)
                .foregroundColor(palette.textTertiary)
            TextField("搜索账户名称、备注、币种或分组", text: $searchQuery)
                .font(.subheadline)
                .submitLabel(.search)
            if isSearching {
                Text("命中 \(totalSearchHitCount)")
                    .font(.caption2.weight(.semibold))
                    .foregroundColor(palette.primary)
                    .lineLimit(1)
                    .fixedSize(horizontal: true, vertical: false)
                    .padding(.horizontal, 7)
                    .padding(.vertical, 3)
                    .background(Capsule().fill(palette.accentSoftFill))
                Button {
                    searchQuery = ""
                } label: {
                    Image(systemName: "xmark.circle.fill")
                        .foregroundColor(palette.textTertiary)
                }
                .buttonStyle(.plain)
            } else {
                Button("收起") {
                    withAnimation(.easeInOut(duration: 0.2)) {
                        showSearchBar = false
                    }
                }
                .font(.caption.weight(.semibold))
                .foregroundColor(palette.textSecondary)
                .lineLimit(1)
                .fixedSize(horizontal: true, vertical: false)
                .buttonStyle(.plain)
            }
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 10)
        .background(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .fill(palette.surface)
                .overlay(
                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                        .strokeBorder(palette.outline, lineWidth: 0.8)
                )
        )
    }

    private var hiddenVisibilityStrip: some View {
        HStack(spacing: 10) {
            Button {
                withAnimation(.easeInOut(duration: 0.2)) {
                    showHiddenAccounts.toggle()
                }
            } label: {
                Label(
                    showHiddenAccounts ? "收起隐藏资产" : "显示隐藏项 (\(hiddenAccountIdSet.count + hiddenModuleSet.count))",
                    systemImage: showHiddenAccounts ? "eye.slash" : "eye"
                )
                .font(.caption.weight(.semibold))
                .foregroundColor(showHiddenAccounts ? palette.primary : palette.textSecondary)
                .lineLimit(1)
                .fixedSize(horizontal: true, vertical: false)
                .padding(.horizontal, 12)
                .padding(.vertical, 7)
                .background(
                    Capsule().fill(showHiddenAccounts ? palette.accentSoftFill : palette.surface)
                )
                .overlay(
                    Capsule().strokeBorder(showHiddenAccounts ? palette.primary.opacity(0.4) : palette.outline, lineWidth: 0.8)
                )
            }
            .buttonStyle(.plain)

            if hiddenModuleSet.contains(AssetHiddenModule.DEBT) {
                Button {
                    activeSheet = .addDebt
                } label: {
                    Label("记借贷", systemImage: "person.2")
                        .font(.caption.weight(.semibold))
                        .foregroundColor(palette.textSecondary)
                        .lineLimit(1)
                        .fixedSize(horizontal: true, vertical: false)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 7)
                        .background(Capsule().fill(palette.surface))
                        .overlay(Capsule().strokeBorder(palette.outline, lineWidth: 0.8))
                }
                .buttonStyle(.plain)
            }

            Spacer(minLength: 0)
        }
    }

    // MARK: - Modules (Reimbursements & Debts)

    private var reimbursementModuleCard: some View {
        let totalPending = pendingReimburseTxs.reduce(Int64(0)) { acc, tx in
            let rate = store.getRate(from: tx.origCurrency, to: activeDisplayCurrency).rateMicros
            return acc + MoneyAmount.convert(amountMicros: tx.origAmountMicros, rateMicros: rate)
        }
        let masked = store.settings.privacyMaskEnabled
        return YubraeCard(palette: palette, padding: 14) {
            HStack(spacing: 8) {
                VStack(alignment: .leading, spacing: 3) {
                    Label("待报销款项（\(pendingReimburseTxs.count) 笔）", systemImage: "doc.text.magnifyingglass")
                        .font(.subheadline.weight(.semibold))
                        .foregroundColor(palette.textPrimary)
                        .lineLimit(1)
                        .minimumScaleFactor(0.85)
                    Text("合计约 \(YubraeTheme.masked(MoneyAmount.format(totalPending, currencyCode: activeDisplayCurrency), isMasked: masked))")
                        .font(.caption)
                        .foregroundColor(palette.transferColor)
                        .lineLimit(1)
                        .minimumScaleFactor(0.85)
                }
                Spacer(minLength: 8)
                Button {
                    activeSheet = .settleReimburse
                } label: {
                    Text("去核销")
                        .font(.caption.weight(.semibold))
                        .foregroundColor(palette.onPrimary)
                        .lineLimit(1)
                        .fixedSize(horizontal: true, vertical: false)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 7)
                        .background(Capsule().fill(palette.primary))
                }
                .buttonStyle(.plain)
                .fixedSize(horizontal: true, vertical: false)
            }
        }
    }

    private var debtModuleCard: some View {
        let masked = store.settings.privacyMaskEnabled
        return YubraeCard(palette: palette, padding: 14) {
            VStack(alignment: .leading, spacing: 10) {
                HStack(spacing: 8) {
                    Label("借贷往来（进行中 \(openDebts.count) 笔）", systemImage: "person.2.fill")
                        .font(.subheadline.weight(.semibold))
                        .foregroundColor(palette.textPrimary)
                        .lineLimit(1)
                        .minimumScaleFactor(0.85)
                    Spacer(minLength: 8)
                    Button("+ 新增") {
                        activeSheet = .addDebt
                    }
                    .font(.caption.weight(.semibold))
                    .foregroundColor(palette.primary)
                    .lineLimit(1)
                    .fixedSize(horizontal: true, vertical: false)
                    .buttonStyle(.plain)
                }

                if openDebts.isEmpty {
                    Text("暂无未结清的借入/借出记录")
                        .font(.caption)
                        .foregroundColor(palette.textTertiary)
                } else {
                    ForEach(openDebts) { debt in
                        let remain = max(0, debt.amountMicros - debt.settledAmountMicros)
                        let isLend = debt.direction == DebtDirection.LEND
                        HStack(spacing: 8) {
                            VStack(alignment: .leading, spacing: 2) {
                                HStack(spacing: 6) {
                                    Text(isLend ? "借出" : "借入")
                                        .font(.caption2.weight(.bold))
                                        .foregroundColor(isLend ? palette.incomeColor : palette.expenseColor)
                                        .lineLimit(1)
                                        .fixedSize(horizontal: true, vertical: false)
                                        .padding(.horizontal, 6)
                                        .padding(.vertical, 2)
                                        .background(Capsule().fill((isLend ? palette.incomeColor : palette.expenseColor).opacity(0.14)))
                                    Text(debt.personName)
                                        .font(.subheadline.weight(.semibold))
                                        .foregroundColor(palette.textPrimary)
                                        .lineLimit(1)
                                }
                                if !debt.note.isEmpty {
                                    Text(debt.note)
                                        .font(.caption2)
                                        .foregroundColor(palette.textTertiary)
                                        .lineLimit(1)
                                }
                            }
                            Spacer(minLength: 8)
                            VStack(alignment: .trailing, spacing: 4) {
                                Text(YubraeTheme.masked(MoneyAmount.format(remain, currencyCode: debt.currencyCode, showCurrencyCode: true), isMasked: masked))
                                    .font(.subheadline.weight(.bold).monospacedDigit())
                                    .foregroundColor(palette.textPrimary)
                                    .lineLimit(1)
                                    .fixedSize(horizontal: true, vertical: false)
                                Button("核销/还款") {
                                    activeSheet = .settleDebt(debt)
                                }
                                .font(.caption2.weight(.semibold))
                                .foregroundColor(palette.primary)
                                .lineLimit(1)
                                .fixedSize(horizontal: true, vertical: false)
                                .buttonStyle(.plain)
                            }
                            .layoutPriority(1)
                        }
                        .padding(.vertical, 4)
                    }
                }
            }
        }
    }

    // MARK: - Grouped Accounts

    private var groupedAccountsSection: some View {
        let visibleAccounts = visibleActiveAccounts

        return Group {
            if visibleAccounts.isEmpty && (!isSearching || archivedAccounts.isEmpty) {
                YubraeEmptyStateView(
                    icon: isSearching ? "magnifyingglass" : "creditcard",
                    title: isSearching ? "未找到匹配的账户" : "暂无可展示的账户",
                    subtitle: isSearching
                        ? "试试搜索账户名、备注、币种（如 USD）或分组名称。"
                        : "你可以新建账户，或在「隐藏资产管理」中取消隐藏。",
                    palette: palette,
                    actionTitle: isSearching ? "清空搜索" : "新建账户",
                    action: {
                        if isSearching {
                            searchQuery = ""
                        } else {
                            activeSheet = .editAccount(nil, AccountGroup.WALLET)
                        }
                    }
                )
            } else {
                ForEach(orderedGroups) { group in
                    let groupAccounts = visibleAccounts.filter { $0.groupType.uppercased() == group.rawValue }
                    if !groupAccounts.isEmpty {
                        accountGroupCard(group: group, accounts: groupAccounts)
                    }
                }
            }
        }
    }

    private func accountGroupCard(group: AccountGroup, accounts: [AccountDto]) -> some View {
        let masked = store.settings.privacyMaskEnabled
        let groupSubtotal = accounts.reduce(Int64(0)) { sum, acc in
            sum + (netWorth.accountConvertedBalances[acc.id] ?? 0)
        }
        let isCollapsed = !isSearching && collapsedGroupSet.contains(group.rawValue)

        return VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 8) {
                Button {
                    withAnimation(.easeInOut(duration: 0.2)) {
                        store.toggleAssetGroupCollapsed(group.rawValue)
                    }
                } label: {
                    HStack(spacing: 5) {
                        Image(systemName: isCollapsed ? "chevron.right" : "chevron.down")
                            .font(.system(size: 10, weight: .bold))
                            .foregroundColor(palette.textTertiary)
                            .frame(width: 12)
                        Image(systemName: group.sfSymbol)
                            .font(.caption.weight(.semibold))
                            .foregroundColor(palette.primary)
                        Text(group.labelZh)
                            .font(.caption.weight(.semibold))
                            .foregroundColor(palette.textSecondary)
                            .lineLimit(1)
                        Text("\(accounts.count)")
                            .font(.system(size: 10, weight: .semibold).monospacedDigit())
                            .foregroundColor(palette.textTertiary)
                            .lineLimit(1)
                            .fixedSize(horizontal: true, vertical: false)
                            .padding(.horizontal, 6)
                            .padding(.vertical, 1.5)
                            .background(Capsule().fill(palette.surfaceSecondary))
                    }
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)

                Spacer(minLength: 6)

                Text(YubraeTheme.masked(MoneyAmount.format(groupSubtotal, currencyCode: activeDisplayCurrency), isMasked: masked))
                    .font(.caption.weight(.semibold).monospacedDigit())
                    .foregroundColor(groupSubtotal < 0 ? palette.expenseColor : palette.textSecondary)
                    .lineLimit(1)
                    .fixedSize(horizontal: true, vertical: false)

                Button {
                    activeSheet = .editAccount(nil, group.rawValue)
                } label: {
                    Image(systemName: "plus.circle.fill")
                        .font(.system(size: 16))
                        .foregroundColor(palette.primary.opacity(0.85))
                }
                .buttonStyle(.plain)
                .accessibilityLabel("在\(group.labelZh)新建账户")
            }
            .padding(.horizontal, 4)

            if !isCollapsed {
                YubraeCard(palette: palette, padding: 0) {
                    VStack(spacing: 0) {
                        ForEach(accounts) { acc in
                            accountRowLink(acc)
                            if acc.id != accounts.last?.id {
                                Divider().padding(.leading, 64)
                            }
                        }
                    }
                }
                .transition(.opacity.combined(with: .move(edge: .top)))
            }
        }
    }

    private func accountRowLink(_ acc: AccountDto) -> some View {
        let isHidden = hiddenAccountIdSet.contains(acc.id)
        let isCredit = acc.groupType.uppercased() == AccountGroup.CREDIT

        return NavigationLink {
            AccountDetailView(store: store, accountId: acc.id)
        } label: {
            accountRowContent(acc, isHidden: isHidden, isCredit: isCredit)
        }
        .buttonStyle(.plain)
        .contextMenu {
            if isCredit {
                Button {
                    quickRecordPreset = AssetQuickRecordPreset(
                        type: TransactionType.TRANSFER,
                        accountId: nil,
                        toAccountId: acc.id,
                        amountMicros: max(0, -acc.balanceMicros)
                    )
                } label: {
                    Label("还信用卡", systemImage: "arrow.uturn.left.circle")
                }
            }
            Button {
                activeSheet = .calibrate(acc)
            } label: {
                Label("余额校准", systemImage: "slider.horizontal.3")
            }
            Button {
                activeSheet = .editAccount(acc, acc.groupType)
            } label: {
                Label("编辑账户", systemImage: "pencil")
            }
            Button {
                toggleAccountHidden(acc.id)
            } label: {
                Label(isHidden ? "取消隐藏" : "隐藏此账户", systemImage: isHidden ? "eye" : "eye.slash")
            }
            Button {
                store.archiveAccount(id: acc.id, archived: true)
                store.toastMessage = "已归档账户「\(acc.name)」"
            } label: {
                Label("归档账户", systemImage: "archivebox")
            }
            Button(role: .destructive) {
                activeSheet = .deleteAccount(acc)
            } label: {
                Label("删除账户", systemImage: "trash")
            }
        }
    }

    private func accountRowContent(_ acc: AccountDto, isHidden: Bool, isCredit: Bool) -> some View {
        let masked = store.settings.privacyMaskEnabled
        let origFmt = MoneyAmount.format(acc.balanceMicros, currencyCode: acc.currencyCode, showCurrencyCode: true)
        let convertedMicros = netWorth.accountConvertedBalances[acc.id] ?? acc.balanceMicros
        let trimmedNote = acc.note.trimmingCharacters(in: .whitespacesAndNewlines)
        let creditCycle: CreditBillingCycleSummary? = isCredit
            ? store.computeCreditBillingCycleSummary(account: acc)
            : nil
        let usedMicros = max(Int64(0), -acc.balanceMicros)
        let limitMicros = acc.creditLimitMicros
        let availMicros = max(Int64(0), limitMicros - usedMicros)
        let usageRatio: Double = limitMicros > 0 ? min(1.0, max(0.0, Double(usedMicros) / Double(limitMicros))) : 0.0

        return VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 12) {
                YubraeCategoryIconBadge(iconKey: acc.icon, colorHex: acc.colorHex, size: 40)

                VStack(alignment: .leading, spacing: 3) {
                    HStack(spacing: 5) {
                        Text(acc.name)
                            .font(.subheadline.weight(.semibold))
                            .foregroundColor(palette.textPrimary)
                            .lineLimit(1)
                        if isHidden {
                            Text("已隐藏")
                                .font(.system(size: 10, weight: .semibold))
                                .foregroundColor(palette.textSecondary)
                                .lineLimit(1)
                                .fixedSize(horizontal: true, vertical: false)
                                .padding(.horizontal, 6)
                                .padding(.vertical, 2)
                                .background(Capsule().fill(palette.surfaceSecondary))
                        }
                        if !acc.includeInNetWorth {
                            Text("不计净资产")
                                .font(.system(size: 10, weight: .medium))
                                .foregroundColor(palette.textTertiary)
                                .lineLimit(1)
                                .fixedSize(horizontal: true, vertical: false)
                                .padding(.horizontal, 5)
                                .padding(.vertical, 1.5)
                                .background(Capsule().fill(palette.surfaceSecondary))
                        }
                    }

                    if !trimmedNote.isEmpty {
                        Text(trimmedNote)
                            .font(.caption2)
                            .foregroundColor(palette.textSecondary)
                            .lineLimit(1)
                    } else if !isCredit {
                        Text(CurrencyCatalog.find(acc.currencyCode).nameZh + " · " + acc.currencyCode)
                            .font(.caption2)
                            .foregroundColor(palette.textSecondary)
                            .lineLimit(1)
                    } else {
                        creditInlineMetaRow(acc: acc, cycle: creditCycle, availMicros: availMicros, masked: masked)
                    }
                }

                Spacer(minLength: 6)

                HStack(spacing: 6) {
                    VStack(alignment: .trailing, spacing: 2) {
                        Text(YubraeTheme.masked(origFmt, isMasked: masked))
                            .font(.subheadline.weight(.bold).monospacedDigit())
                            .foregroundColor(acc.balanceMicros < 0 ? palette.expenseColor : palette.textPrimary)
                            .lineLimit(1)
                            .fixedSize(horizontal: true, vertical: false)
                        if acc.currencyCode.uppercased() != activeDisplayCurrency.uppercased() {
                            let convFmt = MoneyAmount.format(convertedMicros, currencyCode: activeDisplayCurrency)
                            Text("≈ \(YubraeTheme.masked(convFmt, isMasked: masked))")
                                .font(.caption2.monospacedDigit())
                                .foregroundColor(palette.textTertiary)
                                .lineLimit(1)
                                .fixedSize(horizontal: true, vertical: false)
                        }
                    }
                    Image(systemName: "chevron.right")
                        .font(.system(size: 11, weight: .semibold))
                        .foregroundColor(palette.textTertiary.opacity(0.75))
                }
                .layoutPriority(1)
            }

            if isCredit {
                VStack(alignment: .leading, spacing: 5) {
                    if !trimmedNote.isEmpty {
                        creditInlineMetaRow(acc: acc, cycle: creditCycle, availMicros: availMicros, masked: masked)
                    }
                    if limitMicros > 0 {
                        HStack(spacing: 8) {
                            GeometryReader { geo in
                                ZStack(alignment: .leading) {
                                    Capsule().fill(palette.surfaceSecondary)
                                    Capsule()
                                        .fill(creditUsageColor(ratio: usageRatio))
                                        .frame(width: max(usageRatio > 0 ? 4 : 0, geo.size.width * CGFloat(usageRatio)))
                                }
                            }
                            .frame(height: 4)
                            Text(masked ? "已用 ••%" : "已用 \(Int((usageRatio * 100).rounded()))%")
                                .font(.system(size: 10, weight: .medium).monospacedDigit())
                                .foregroundColor(palette.textTertiary)
                                .lineLimit(1)
                                .fixedSize(horizontal: true, vertical: false)
                        }
                    }
                }
                .padding(.leading, 52)
            }
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 12)
        .contentShape(Rectangle())
    }

    private func creditInlineMetaRow(
        acc: AccountDto,
        cycle: CreditBillingCycleSummary?,
        availMicros: Int64,
        masked: Bool
    ) -> some View {
        HStack(spacing: 6) {
            if acc.creditLimitMicros > 0 {
                let availStr = YubraeTheme.masked(
                    MoneyAmount.format(availMicros, currencyCode: acc.currencyCode),
                    isMasked: masked
                )
                Text("可用 \(availStr)")
                    .font(.caption2.monospacedDigit())
                    .foregroundColor(palette.textSecondary)
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)
            }
            if let dBill = cycle?.daysUntilBilling {
                Text(dBill == 0 ? "今日出账" : "\(dBill)天后出账")
                    .font(.system(size: 10, weight: .medium).monospacedDigit())
                    .foregroundColor(palette.primary)
                    .lineLimit(1)
                    .fixedSize(horizontal: true, vertical: false)
                    .padding(.horizontal, 5)
                    .padding(.vertical, 1.5)
                    .background(Capsule().fill(palette.accentSoftFill))
            }
            if let dRepay = cycle?.daysUntilRepayment {
                let urgent = dRepay <= 5
                Text(dRepay == 0 ? "今日还款" : "\(dRepay)天后还款")
                    .font(.system(size: 10, weight: .semibold).monospacedDigit())
                    .foregroundColor(urgent ? palette.warningColor : palette.textSecondary)
                    .lineLimit(1)
                    .fixedSize(horizontal: true, vertical: false)
                    .padding(.horizontal, 5)
                    .padding(.vertical, 1.5)
                    .background(Capsule().fill(urgent ? palette.warningColor.opacity(0.16) : palette.surfaceSecondary))
            }
        }
    }

    private func creditUsageColor(ratio: Double) -> Color {
        if ratio >= 0.85 { return palette.dangerColor }
        if ratio >= 0.60 { return palette.warningColor }
        return palette.primary
    }

    // MARK: - Archived Accounts Section

    private var archivedAccountsCard: some View {
        let masked = store.settings.privacyMaskEnabled
        let expandedBinding = Binding<Bool>(
            get: { isSearching ? true : showArchivedSection },
            set: { showArchivedSection = $0 }
        )
        return YubraeCard(palette: palette) {
            DisclosureGroup("已归档账户（\(archivedAccounts.count)）", isExpanded: expandedBinding) {
                VStack(spacing: 10) {
                    ForEach(archivedAccounts) { acc in
                        HStack(spacing: 10) {
                            NavigationLink {
                                AccountDetailView(store: store, accountId: acc.id)
                            } label: {
                                HStack(spacing: 10) {
                                    YubraeCategoryIconBadge(iconKey: acc.icon, colorHex: acc.colorHex, size: 32)
                                        .opacity(0.7)
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text(acc.name)
                                            .font(.subheadline.weight(.medium))
                                            .foregroundColor(palette.textSecondary)
                                            .lineLimit(1)
                                        let balText = YubraeTheme.masked(
                                            MoneyAmount.format(acc.balanceMicros, currencyCode: acc.currencyCode, showCurrencyCode: true),
                                            isMasked: masked
                                        )
                                        Text(acc.note.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? balText : "\(balText) · \(acc.note)")
                                            .font(.caption2.monospacedDigit())
                                            .foregroundColor(palette.textTertiary)
                                            .lineLimit(1)
                                    }
                                    Spacer(minLength: 4)
                                }
                                .contentShape(Rectangle())
                            }
                            .buttonStyle(.plain)

                            Button("恢复") {
                                store.archiveAccount(id: acc.id, archived: false)
                                store.toastMessage = "已恢复账户「\(acc.name)」"
                            }
                            .buttonStyle(.borderless)
                            .font(.caption.weight(.semibold))
                            .foregroundColor(palette.primary)
                            .lineLimit(1)
                            .fixedSize(horizontal: true, vertical: false)

                            Button("删除", role: .destructive) {
                                activeSheet = .deleteAccount(acc)
                            }
                            .buttonStyle(.borderless)
                            .font(.caption.weight(.semibold))
                            .lineLimit(1)
                            .fixedSize(horizontal: true, vertical: false)
                        }
                        if acc.id != archivedAccounts.last?.id {
                            Divider()
                        }
                    }
                }
                .padding(.top, 10)
            }
            .font(.subheadline.weight(.medium))
            .foregroundColor(palette.textSecondary)
        }
    }

    // MARK: - Helpers

    private func toggleAccountHidden(_ accountId: Int64) {
        store.updateSettings { s in
            if s.hiddenAssetAccountIds.contains(accountId) {
                s.hiddenAssetAccountIds.removeAll { $0 == accountId }
            } else {
                s.hiddenAssetAccountIds.append(accountId)
            }
        }
    }

    private func matchesAccountSearch(_ acc: AccountDto, query: String) -> Bool {
        let curName = CurrencyCatalog.find(acc.currencyCode).nameZh
        let groupName = AccountGroup.labelZh(for: acc.groupType)
        return acc.name.localizedCaseInsensitiveContains(query)
            || acc.note.localizedCaseInsensitiveContains(query)
            || acc.currencyCode.localizedCaseInsensitiveContains(query)
            || curName.localizedCaseInsensitiveContains(query)
            || groupName.localizedCaseInsensitiveContains(query)
            || acc.groupType.localizedCaseInsensitiveContains(query)
    }
}

// MARK: - Native Drag-and-Drop Asset Reorder Sheet

private enum AssetReorderTab: Int, CaseIterable, Identifiable {
    case accounts = 0
    case groups = 1

    var id: Int { rawValue }
    var title: String {
        switch self {
        case .accounts: return "账户排序"
        case .groups: return "分组顺序"
        }
    }
}

private struct AssetReorderSheet: View {
    @ObservedObject var store: LedgerStore
    @Environment(\.dismiss) private var dismiss
    @Environment(\.colorScheme) private var colorScheme

    @State private var activeTab: AssetReorderTab = .accounts
    @State private var draftGroupOrder: [String]
    @State private var draftAccountsByGroup: [String: [AccountDto]]

    private var palette: YubraePalette {
        YubraeTheme.palette(for: store.settings, colorScheme: colorScheme)
    }

    init(store: LedgerStore, initialGroupOrder: [AccountGroup]) {
        self.store = store
        let groupKeys = initialGroupOrder.map(\.rawValue)
        _draftGroupOrder = State(initialValue: groupKeys)

        var map: [String: [AccountDto]] = [:]
        let sortedActive = store.activeAccounts.sorted { lhs, rhs in
            if lhs.sortOrder != rhs.sortOrder { return lhs.sortOrder < rhs.sortOrder }
            return lhs.id < rhs.id
        }
        for key in groupKeys {
            map[key] = sortedActive.filter { $0.groupType.uppercased() == key }
        }
        _draftAccountsByGroup = State(initialValue: map)
    }

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                Picker("排序维度", selection: $activeTab) {
                    ForEach(AssetReorderTab.allCases) { tab in
                        Text(tab.title).tag(tab)
                    }
                }
                .pickerStyle(.segmented)
                .padding(.horizontal, 16)
                .padding(.top, 10)
                .padding(.bottom, 6)
                .background(palette.background)

                HStack(spacing: 6) {
                    Image(systemName: "line.3.horizontal")
                        .font(.caption2)
                        .foregroundColor(palette.primary)
                    Text(activeTab == .accounts
                        ? "按住右侧把手上下拖动，调整组内账户顺序"
                        : "按住右侧把手上下拖动，调整资产分组顺序")
                        .font(.caption)
                        .foregroundColor(palette.textSecondary)
                        .lineLimit(1)
                        .minimumScaleFactor(0.85)
                    Spacer(minLength: 0)
                }
                .padding(.horizontal, 18)
                .padding(.bottom, 6)
                .background(palette.background)

                if activeTab == .accounts {
                    accountsReorderList
                } else {
                    groupsReorderList
                }
            }
            .background(palette.background.ignoresSafeArea())
            .navigationTitle("资产排序")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("取消") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("保存") {
                        saveOrder()
                    }
                    .fontWeight(.semibold)
                }
            }
        }
    }

    private var accountsReorderList: some View {
        let nonEmptyGroups = draftGroupOrder.filter { !(draftAccountsByGroup[$0] ?? []).isEmpty }
        return List {
            if nonEmptyGroups.isEmpty {
                Text("暂无可排序的活跃账户")
                    .font(.subheadline)
                    .foregroundColor(.secondary)
            } else {
                ForEach(nonEmptyGroups, id: \.self) { groupKey in
                    let items = draftAccountsByGroup[groupKey] ?? []
                    Section(header: Text(AccountGroup.labelZh(for: groupKey))) {
                        ForEach(items) { acc in
                            HStack(spacing: 10) {
                                YubraeCategoryIconBadge(iconKey: acc.icon, colorHex: acc.colorHex, size: 32)
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(acc.name)
                                        .font(.subheadline.weight(.semibold))
                                        .foregroundColor(palette.textPrimary)
                                        .lineLimit(1)
                                    Text(acc.note.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? acc.currencyCode : acc.note)
                                        .font(.caption2)
                                        .foregroundColor(palette.textSecondary)
                                        .lineLimit(1)
                                }
                                Spacer(minLength: 8)
                                Text(MoneyAmount.format(acc.balanceMicros, currencyCode: acc.currencyCode, showCurrencyCode: true))
                                    .font(.caption.weight(.semibold).monospacedDigit())
                                    .foregroundColor(palette.textSecondary)
                                    .lineLimit(1)
                                    .fixedSize(horizontal: true, vertical: false)
                            }
                            .deleteDisabled(true)
                        }
                        .onMove { source, destination in
                            var list = draftAccountsByGroup[groupKey] ?? []
                            list.move(fromOffsets: source, toOffset: destination)
                            draftAccountsByGroup[groupKey] = list
                        }
                    }
                }
            }
        }
        .listStyle(.insetGrouped)
        .scrollContentBackground(.hidden)
        .environment(\.editMode, .constant(.active))
    }

    private var groupsReorderList: some View {
        List {
            Section(
                header: Text("资产分组展示顺序"),
                footer: Text("拖动右侧把手调整各账户分组在资产首页的上下顺序。")
            ) {
                ForEach(draftGroupOrder, id: \.self) { groupKey in
                    let group = AccountGroup(rawValue: groupKey)
                    let count = (draftAccountsByGroup[groupKey] ?? []).count
                    HStack(spacing: 12) {
                        Image(systemName: group?.sfSymbol ?? "folder.fill")
                            .font(.subheadline.weight(.semibold))
                            .foregroundColor(palette.primary)
                            .frame(width: 24)
                        Text(AccountGroup.labelZh(for: groupKey))
                            .font(.subheadline.weight(.semibold))
                            .foregroundColor(palette.textPrimary)
                            .lineLimit(1)
                        Spacer(minLength: 8)
                        Text("\(count) 个账户")
                            .font(.caption)
                            .foregroundColor(palette.textTertiary)
                            .lineLimit(1)
                            .fixedSize(horizontal: true, vertical: false)
                    }
                    .deleteDisabled(true)
                }
                .onMove { source, destination in
                    draftGroupOrder.move(fromOffsets: source, toOffset: destination)
                }
            }

            Section {
                Button("恢复默认分组顺序") {
                    draftGroupOrder = AccountGroup.allCases.map(\.rawValue)
                }
                .buttonStyle(.borderless)
                .foregroundColor(palette.primary)
            }
        }
        .listStyle(.insetGrouped)
        .scrollContentBackground(.hidden)
        .environment(\.editMode, .constant(.active))
    }

    private func saveOrder() {
        var orderedIds: [Int64] = []
        var seen = Set<Int64>()
        for groupKey in draftGroupOrder {
            for acc in (draftAccountsByGroup[groupKey] ?? []) {
                if seen.insert(acc.id).inserted {
                    orderedIds.append(acc.id)
                }
            }
        }
        for acc in store.accounts where seen.insert(acc.id).inserted {
            orderedIds.append(acc.id)
        }
        store.reorderAccounts(orderedIds: orderedIds)
        store.updateAssetGroupOrder(draftGroupOrder)
        store.toastMessage = "已保存资产排序"
        dismiss()
    }
}

// MARK: - Account Editor Sheet (Internal - Shared with AccountDetailView)

struct AccountEditorSheet: View {
    @ObservedObject var store: LedgerStore
    let editingAccount: AccountDto?

    @Environment(\.dismiss) private var dismiss
    @Environment(\.colorScheme) private var colorScheme

    private var palette: YubraePalette {
        YubraeTheme.palette(for: store.settings, colorScheme: colorScheme)
    }

    @State private var name: String
    @State private var groupType: String
    @State private var currencyCode: String
    @State private var balanceText: String
    @State private var note: String
    @State private var creditLimitText: String
    @State private var billingDayText: String
    @State private var repaymentDayText: String
    @State private var selectedIcon: String
    @State private var selectedColorHex: Int64
    @State private var includeInNetWorth: Bool
    @State private var showCalibration = false
    @State private var showDeleteConfirm = false

    private let iconOptions = ["wallet", "chat", "account_balance_wallet", "account_balance", "credit_card", "payments", "directions_bus", "attach_money", "euro", "currency_pound", "trending_up", "savings"]
    private let colorOptions: [Int64] = [0xFF2AA876, 0xFF07C160, 0xFF1677FF, 0xFF0B57D0, 0xFFDC2626, 0xFFD97706, 0xFF7C3AED, 0xFF0F766E, 0xFF475569]

    init(store: LedgerStore, editingAccount: AccountDto?, initialGroupType: String = AccountGroup.WALLET) {
        self.store = store
        self.editingAccount = editingAccount
        if let acc = editingAccount {
            let digits = CurrencyCatalog.find(acc.currencyCode).minorDigits
            _name = State(initialValue: acc.name)
            _groupType = State(initialValue: acc.groupType)
            _currencyCode = State(initialValue: acc.currencyCode)
            _balanceText = State(initialValue: MoneyAmount.formatPlain(acc.balanceMicros, minorDigits: digits, grouping: false))
            _note = State(initialValue: acc.note)
            _creditLimitText = State(initialValue: acc.creditLimitMicros > 0 ? MoneyAmount.formatPlain(acc.creditLimitMicros, minorDigits: digits, grouping: false) : "")
            _billingDayText = State(initialValue: acc.billingDay.map(String.init) ?? "")
            _repaymentDayText = State(initialValue: acc.repaymentDay.map(String.init) ?? "")
            _selectedIcon = State(initialValue: acc.icon)
            _selectedColorHex = State(initialValue: acc.colorHex)
            _includeInNetWorth = State(initialValue: acc.includeInNetWorth)
        } else {
            let resolvedGroup = AccountGroup(rawValue: initialGroupType.uppercased())?.rawValue ?? AccountGroup.WALLET
            let defaultIcon: String
            switch resolvedGroup {
            case AccountGroup.DEBIT: defaultIcon = "account_balance"
            case AccountGroup.CREDIT: defaultIcon = "credit_card"
            case AccountGroup.FOREIGN: defaultIcon = "attach_money"
            case AccountGroup.INVEST: defaultIcon = "trending_up"
            case AccountGroup.DEBT: defaultIcon = "payments"
            default: defaultIcon = "wallet"
            }
            _name = State(initialValue: "")
            _groupType = State(initialValue: resolvedGroup)
            _currencyCode = State(initialValue: resolvedGroup == AccountGroup.FOREIGN ? "USD" : store.settings.baseCurrency)
            _balanceText = State(initialValue: "0.00")
            _note = State(initialValue: "")
            _creditLimitText = State(initialValue: "")
            _billingDayText = State(initialValue: "")
            _repaymentDayText = State(initialValue: "")
            _selectedIcon = State(initialValue: defaultIcon)
            _selectedColorHex = State(initialValue: 0xFF2AA876)
            _includeInNetWorth = State(initialValue: true)
        }
    }

    var body: some View {
        NavigationStack {
            Form {
                Section("基本信息") {
                    TextField("账户名称（如 招商银行储蓄卡）", text: $name)
                    TextField("账户备注（可选，如 卡号后四位 / 用途）", text: $note)
                    Picker("账户分组", selection: $groupType) {
                        ForEach(AccountGroup.allCases) { group in
                            Text(group.labelZh).tag(group.rawValue)
                        }
                    }
                    Picker("币种", selection: $currencyCode) {
                        ForEach(CurrencyCatalog.all) { info in
                            Text("\(info.flag) \(info.code) · \(info.nameZh)").tag(info.code)
                        }
                    }
                    HStack {
                        Text(editingAccount == nil ? "初始余额" : "当前余额")
                        Spacer()
                        TextField("0.00", text: $balanceText)
                            .keyboardType(.numbersAndPunctuation)
                            .multilineTextAlignment(.trailing)
                    }
                    Toggle("计入总净资产", isOn: $includeInNetWorth)
                }

                if groupType == AccountGroup.CREDIT {
                    Section("信用卡参数") {
                        HStack {
                            Text("信用额度")
                            Spacer()
                            TextField("如 50000", text: $creditLimitText)
                                .keyboardType(.decimalPad)
                                .multilineTextAlignment(.trailing)
                        }
                        HStack {
                            Text("账单日 (1-31)")
                            Spacer()
                            TextField("如 5", text: $billingDayText)
                                .keyboardType(.numberPad)
                                .multilineTextAlignment(.trailing)
                        }
                        HStack {
                            Text("还款日 (1-31)")
                            Spacer()
                            TextField("如 23", text: $repaymentDayText)
                                .keyboardType(.numberPad)
                                .multilineTextAlignment(.trailing)
                        }
                    }
                }

                Section("图标与色标") {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 10) {
                            ForEach(iconOptions, id: \.self) { iconKey in
                                Button {
                                    selectedIcon = iconKey
                                } label: {
                                    YubraeCategoryIconBadge(iconKey: iconKey, colorHex: selectedColorHex, size: 36)
                                        .overlay(
                                            Circle().strokeBorder(selectedIcon == iconKey ? Color(argbHex: selectedColorHex) : Color.clear, lineWidth: 2)
                                        )
                                }
                                .buttonStyle(.plain)
                            }
                        }
                        .padding(.vertical, 4)
                    }

                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 10) {
                            ForEach(colorOptions, id: \.self) { hex in
                                Button {
                                    selectedColorHex = hex
                                } label: {
                                    Circle()
                                        .fill(Color(argbHex: hex))
                                        .frame(width: 28, height: 28)
                                        .overlay(
                                            Image(systemName: "checkmark")
                                                .font(.caption.weight(.bold))
                                                .foregroundColor(.white)
                                                .opacity(selectedColorHex == hex ? 1 : 0)
                                        )
                                }
                                .buttonStyle(.plain)
                            }
                        }
                        .padding(.vertical, 4)
                    }
                }

                if editingAccount != nil {
                    Section("快捷操作") {
                        Button {
                            showCalibration = true
                        } label: {
                            Label("校准余额", systemImage: "slider.horizontal.3")
                        }
                        .buttonStyle(.borderless)
                        Button(role: .destructive) {
                            showDeleteConfirm = true
                        } label: {
                            Label("删除该账户", systemImage: "trash")
                        }
                        .buttonStyle(.borderless)
                    }
                }
            }
            .navigationTitle(editingAccount == nil ? "新建账户" : "编辑账户")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("取消") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("保存") {
                        save()
                    }
                    .fontWeight(.semibold)
                    .disabled(name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                }
                ToolbarItemGroup(placement: .keyboard) {
                    Spacer()
                    Button("完成") {
                        UIApplication.shared.sendAction(#selector(UIResponder.resignFirstResponder), to: nil, from: nil, for: nil)
                    }
                }
            }
            .sheet(isPresented: $showCalibration) {
                if let existing = editingAccount.flatMap({ store.account(byId: $0.id) }) ?? editingAccount {
                    BalanceCalibrationSheet(store: store, account: existing)
                        .yubraeSheetStyle(palette: palette)
                }
            }
            .sheet(isPresented: $showDeleteConfirm) {
                if let existing = editingAccount.flatMap({ store.account(byId: $0.id) }) ?? editingAccount {
                    AccountDeleteSheet(store: store, account: existing) {
                        showDeleteConfirm = false
                        dismiss()
                    }
                    .yubraeSheetStyle(palette: palette)
                }
            }
        }
    }

    private func save() {
        let trimmedName = name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmedName.isEmpty else { return }
        let trimmedNote = note.trimmingCharacters(in: .whitespacesAndNewlines)
        let balMicros = MoneyAmount.parseToMicros(balanceText) ?? (editingAccount?.balanceMicros ?? 0)
        let limitMicros = MoneyAmount.parseToMicros(creditLimitText) ?? 0
        let billDay = Int(billingDayText).flatMap { (1...31).contains($0) ? $0 : nil }
        let repayDay = Int(repaymentDayText).flatMap { (1...31).contains($0) ? $0 : nil }

        var dto = AccountDto(
            id: editingAccount?.id ?? 0,
            name: trimmedName,
            groupType: groupType,
            currencyCode: currencyCode,
            balanceMicros: balMicros,
            creditLimitMicros: groupType == AccountGroup.CREDIT ? limitMicros : 0,
            billingDay: groupType == AccountGroup.CREDIT ? billDay : nil,
            repaymentDay: groupType == AccountGroup.CREDIT ? repayDay : nil,
            icon: selectedIcon,
            colorHex: selectedColorHex,
            includeInNetWorth: includeInNetWorth,
            sortOrder: editingAccount?.sortOrder ?? (store.accounts.count * 10 + 10),
            archived: editingAccount?.archived ?? false,
            createdAt: editingAccount?.createdAt ?? 0,
            updatedAt: 0
        )
        dto.note = trimmedNote
        _ = store.saveAccount(dto)
        store.toastMessage = editingAccount == nil ? "已创建账户「\(trimmedName)」" : "已保存账户修改"
        dismiss()
    }
}

// MARK: - Balance Calibration Sheet (Internal - Shared with AccountDetailView)

struct BalanceCalibrationSheet: View {
    @ObservedObject var store: LedgerStore
    let account: AccountDto

    @Environment(\.dismiss) private var dismiss
    @State private var targetBalanceText: String
    @State private var noteText: String = "余额校准"
    @State private var createRecord: Bool = true

    init(store: LedgerStore, account: AccountDto) {
        self.store = store
        self.account = account
        let digits = CurrencyCatalog.find(account.currencyCode).minorDigits
        _targetBalanceText = State(initialValue: MoneyAmount.formatPlain(account.balanceMicros, minorDigits: digits, grouping: false))
    }

    private var deltaMicros: Int64? {
        guard let target = MoneyAmount.parseToMicros(targetBalanceText) else { return nil }
        return target - account.balanceMicros
    }

    var body: some View {
        NavigationStack {
            Form {
                Section("账户信息") {
                    HStack(spacing: 8) {
                        Text("账户")
                        Spacer(minLength: 8)
                        Text(account.name)
                            .foregroundColor(.secondary)
                            .lineLimit(1)
                    }
                    HStack(spacing: 8) {
                        Text("校准前账面余额")
                            .lineLimit(1)
                        Spacer(minLength: 8)
                        Text(MoneyAmount.format(account.balanceMicros, currencyCode: account.currencyCode, showCurrencyCode: true))
                            .fontWeight(.semibold)
                            .monospacedDigit()
                            .lineLimit(1)
                            .fixedSize(horizontal: true, vertical: false)
                    }
                }
                Section {
                    HStack(spacing: 8) {
                        Text("实际余额")
                            .lineLimit(1)
                        Spacer(minLength: 8)
                        TextField("输入当前实际余额", text: $targetBalanceText)
                            .keyboardType(.numbersAndPunctuation)
                            .multilineTextAlignment(.trailing)
                    }
                    if let delta = deltaMicros {
                        HStack(spacing: 8) {
                            Text("差额变动预览")
                                .lineLimit(1)
                            Spacer(minLength: 8)
                            Text(MoneyAmount.format(delta, currencyCode: account.currencyCode, showSign: true, showCurrencyCode: true))
                                .foregroundColor(delta > 0 ? .green : (delta < 0 ? .red : .secondary))
                                .fontWeight(.semibold)
                                .monospacedDigit()
                                .lineLimit(1)
                                .fixedSize(horizontal: true, vertical: false)
                        }
                    }
                    Toggle("生成差额变动记录", isOn: $createRecord)
                    if createRecord {
                        TextField("备注（如 对账调平）", text: $noteText)
                    }
                } header: {
                    Text("目标实际余额")
                } footer: {
                    Text(createRecord ? "将在流水中生成一条余额校准记录，删除该记录可回滚账户余额。" : "关闭后将直接修改账户余额，不写入流水明细。")
                }
            }
            .navigationTitle("余额校准")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("取消") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("确认校准") {
                        guard let target = MoneyAmount.parseToMicros(targetBalanceText) else { return }
                        do {
                            _ = try store.calibrateAccountBalance(
                                accountId: account.id,
                                targetBalanceMicros: target,
                                note: noteText,
                                createRecord: createRecord
                            )
                            store.toastMessage = createRecord
                                ? "已完成「\(account.name)」余额校准并生成记录"
                                : "已直接更新「\(account.name)」余额"
                            dismiss()
                        } catch {
                            store.lastErrorMessage = error.localizedDescription
                        }
                    }
                    .fontWeight(.semibold)
                    .disabled(deltaMicros == nil)
                }
                ToolbarItemGroup(placement: .keyboard) {
                    Spacer()
                    Button("完成") {
                        UIApplication.shared.sendAction(#selector(UIResponder.resignFirstResponder), to: nil, from: nil, for: nil)
                    }
                }
            }
        }
        .presentationDetents([.medium, .large])
    }
}

// MARK: - Account Delete Sheet (Internal - Shared with AccountDetailView)

struct AccountDeleteSheet: View {
    @ObservedObject var store: LedgerStore
    let account: AccountDto
    let onDeleted: () -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var migrateTargetId: Int64 = 0
    @State private var initialized = false

    private var linkedCount: Int {
        store.transactions.filter { $0.accountId == account.id || $0.toAccountId == account.id }.count
    }

    private var migrateTargets: [AccountDto] {
        store.activeAccounts.filter { $0.id != account.id }
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    VStack(alignment: .leading, spacing: 4) {
                        Text(account.name)
                            .font(.headline)
                        Text(CurrencyCatalog.find(account.currencyCode).nameZh + " · " + account.currencyCode)
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }
                }

                if linkedCount == 0 {
                    Section(footer: Text("当前账户暂无关联账单，删除后将清理识别别名，重启也不会自动重建。")) {
                        Text("当前余额：\(MoneyAmount.format(account.balanceMicros, currencyCode: account.currencyCode, showCurrencyCode: true))")
                            .foregroundColor(.secondary)
                    }
                } else {
                    Section(
                        header: Text("该账户存在 \(linkedCount) 笔关联账单"),
                        footer: Text(migrateTargetId > 0
                            ? "将把该账户的历史账单、余额及周期/分期/借贷关联迁移到目标账户后再删除。"
                            : "将删除该账户及全部关联账单，并自动回滚跨账户转账对方账户的余额。")
                    ) {
                        Picker("处理方式", selection: $migrateTargetId) {
                            Text("直接删除账户及关联账单").tag(Int64(0))
                            ForEach(migrateTargets) { target in
                                Text("迁移至「\(target.name) (\(target.currencyCode))」").tag(target.id)
                            }
                        }
                    }
                }

                Section {
                    Button(
                        linkedCount > 0 && migrateTargetId > 0
                            ? "迁移并删除「\(account.name)」"
                            : "确认删除「\(account.name)」",
                        role: .destructive
                    ) {
                        do {
                            let target: Int64? = (linkedCount > 0 && migrateTargetId > 0) ? migrateTargetId : nil
                            try store.deleteAccount(id: account.id, migrateToAccountId: target)
                            store.toastMessage = target != nil
                                ? "已迁移账单并删除账户「\(account.name)」"
                                : "已删除账户「\(account.name)」"
                            onDeleted()
                            dismiss()
                        } catch {
                            store.lastErrorMessage = error.localizedDescription
                        }
                    }
                    .buttonStyle(.borderless)
                }
            }
            .navigationTitle("删除账户")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("取消") { dismiss() }
                }
            }
            .onAppear {
                if !initialized {
                    initialized = true
                    if linkedCount > 0, let firstTarget = migrateTargets.first {
                        migrateTargetId = firstTarget.id
                    }
                }
            }
        }
        .presentationDetents([.medium, .large])
    }
}

// MARK: - Hidden Assets Manager Sheet

private struct HiddenAssetsManagerSheet: View {
    @ObservedObject var store: LedgerStore
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            Form {
                Section(header: Text("资产页模块显隐"), footer: Text("隐藏后仅在资产列表默认视图中折叠，不影响净资产计算与历史账单。")) {
                    moduleToggleRow(title: "待报销模块", key: AssetHiddenModule.REIMBURSE)
                    moduleToggleRow(title: "借贷往来模块", key: AssetHiddenModule.DEBT)
                    moduleToggleRow(title: "理财投资账户组", key: AssetHiddenModule.INVEST)
                }

                Section("隐藏单个账户") {
                    ForEach(store.activeAccounts) { acc in
                        let isHidden = store.settings.hiddenAssetAccountIds.contains(acc.id)
                        Toggle(isOn: Binding(
                            get: { isHidden },
                            set: { shouldHide in
                                store.updateSettings { s in
                                    if shouldHide {
                                        if !s.hiddenAssetAccountIds.contains(acc.id) {
                                            s.hiddenAssetAccountIds.append(acc.id)
                                        }
                                    } else {
                                        s.hiddenAssetAccountIds.removeAll { $0 == acc.id }
                                    }
                                }
                            }
                        )) {
                            HStack {
                                Text(acc.name)
                                    .lineLimit(1)
                                Spacer()
                                Text(acc.currencyCode)
                                    .font(.caption)
                                    .foregroundColor(.secondary)
                                    .lineLimit(1)
                                    .fixedSize(horizontal: true, vertical: false)
                            }
                        }
                    }
                }
            }
            .navigationTitle("隐藏资产管理")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("完成") { dismiss() }
                }
            }
        }
    }

    private func moduleToggleRow(title: String, key: String) -> some View {
        let isHidden = store.settings.hiddenAssetModules.contains(key)
        return Toggle(title + "（隐藏）", isOn: Binding(
            get: { isHidden },
            set: { shouldHide in
                store.updateSettings { s in
                    if shouldHide {
                        if !s.hiddenAssetModules.contains(key) {
                            s.hiddenAssetModules.append(key)
                        }
                    } else {
                        s.hiddenAssetModules.removeAll { $0 == key }
                    }
                }
            }
        ))
    }
}

// MARK: - Debt & Reimbursement Sheets

private struct AddDebtSheet: View {
    @ObservedObject var store: LedgerStore
    @Environment(\.dismiss) private var dismiss

    @State private var direction: String = DebtDirection.LEND
    @State private var personName: String = ""
    @State private var amountText: String = ""
    @State private var currencyCode: String
    @State private var accountId: Int64?
    @State private var syncBalance = true
    @State private var note: String = ""

    init(store: LedgerStore) {
        self.store = store
        _currencyCode = State(initialValue: store.settings.baseCurrency)
        _accountId = State(initialValue: store.activeAccounts.first?.id)
    }

    var body: some View {
        NavigationStack {
            Form {
                Picker("借贷方向", selection: $direction) {
                    Text("借出（别人欠我）").tag(DebtDirection.LEND)
                    Text("借入（我欠别人）").tag(DebtDirection.BORROW)
                }
                .pickerStyle(.segmented)

                TextField("对方姓名", text: $personName)
                TextField("金额", text: $amountText)
                    .keyboardType(.decimalPad)
                Picker("币种", selection: $currencyCode) {
                    ForEach(CurrencyCatalog.all) { info in
                        Text("\(info.flag) \(info.code)").tag(info.code)
                    }
                }
                Picker("关联账户", selection: Binding(
                    get: { accountId ?? 0 },
                    set: { accountId = $0 > 0 ? $0 : nil }
                )) {
                    Text("不关联账户").tag(Int64(0))
                    ForEach(store.activeAccounts) { acc in
                        Text(acc.name).tag(acc.id)
                    }
                }
                Toggle("同步扣减/增加账户余额", isOn: $syncBalance)
                TextField("备注（可选）", text: $note)
            }
            .navigationTitle("新增借贷记录")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("取消") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("保存") {
                        guard let micros = MoneyAmount.parseToMicros(amountText), micros > 0 else { return }
                        let dto = DebtRecordDto(
                            direction: direction,
                            personName: personName.trimmingCharacters(in: .whitespacesAndNewlines),
                            amountMicros: micros,
                            currencyCode: currencyCode,
                            accountId: accountId,
                            note: note.trimmingCharacters(in: .whitespacesAndNewlines)
                        )
                        _ = store.saveDebt(dto, syncAccountBalance: syncBalance)
                        store.toastMessage = "已记录借贷往来"
                        dismiss()
                    }
                    .disabled(personName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || (MoneyAmount.parseToMicros(amountText) ?? 0) <= 0)
                }
                ToolbarItemGroup(placement: .keyboard) {
                    Spacer()
                    Button("完成") {
                        UIApplication.shared.sendAction(#selector(UIResponder.resignFirstResponder), to: nil, from: nil, for: nil)
                    }
                }
            }
        }
    }
}

private struct SettleDebtSheet: View {
    @ObservedObject var store: LedgerStore
    let debt: DebtRecordDto

    @Environment(\.dismiss) private var dismiss
    @State private var settleAmountText: String
    @State private var targetAccountId: Int64?
    @State private var note: String = ""

    init(store: LedgerStore, debt: DebtRecordDto) {
        self.store = store
        self.debt = debt
        let remain = max(0, debt.amountMicros - debt.settledAmountMicros)
        let digits = CurrencyCatalog.find(debt.currencyCode).minorDigits
        _settleAmountText = State(initialValue: MoneyAmount.formatPlain(remain, minorDigits: digits, grouping: false))
        _targetAccountId = State(initialValue: debt.accountId ?? store.activeAccounts.first?.id)
    }

    var body: some View {
        NavigationStack {
            Form {
                Section("借贷详情") {
                    Text("\(debt.direction == DebtDirection.LEND ? "向 \(debt.personName) 收回借款" : "向 \(debt.personName) 归还借款")")
                }
                Section("本次核销金额") {
                    TextField("核销金额", text: $settleAmountText)
                        .keyboardType(.decimalPad)
                    Picker("入账/扣款账户", selection: Binding(
                        get: { targetAccountId ?? 0 },
                        set: { targetAccountId = $0 > 0 ? $0 : nil }
                    )) {
                        Text("仅更新借贷状态").tag(Int64(0))
                        ForEach(store.activeAccounts) { acc in
                            Text(acc.name).tag(acc.id)
                        }
                    }
                    TextField("备注（可选）", text: $note)
                }
            }
            .navigationTitle("借贷核销")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("取消") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("确认核销") {
                        guard let micros = MoneyAmount.parseToMicros(settleAmountText), micros > 0 else { return }
                        do {
                            _ = try store.settleDebt(debtId: debt.id, settleAmountMicros: micros, accountId: targetAccountId, note: note)
                            store.toastMessage = "借贷核销已完成"
                            dismiss()
                        } catch {
                            store.lastErrorMessage = error.localizedDescription
                        }
                    }
                    .fontWeight(.semibold)
                }
                ToolbarItemGroup(placement: .keyboard) {
                    Spacer()
                    Button("完成") {
                        UIApplication.shared.sendAction(#selector(UIResponder.resignFirstResponder), to: nil, from: nil, for: nil)
                    }
                }
            }
        }
        .presentationDetents([.medium, .large])
    }
}

private struct SettleReimburseSheet: View {
    @ObservedObject var store: LedgerStore
    let pendingTxs: [TransactionDto]

    @Environment(\.dismiss) private var dismiss
    @State private var selectedIds: Set<Int64>
    @State private var targetAccountId: Int64
    @State private var note: String = ""

    init(store: LedgerStore, pendingTxs: [TransactionDto]) {
        self.store = store
        self.pendingTxs = pendingTxs
        _selectedIds = State(initialValue: Set(pendingTxs.map(\.id)))
        _targetAccountId = State(initialValue: store.activeAccounts.first?.id ?? 0)
    }

    var body: some View {
        NavigationStack {
            Form {
                Section("选择收款账户") {
                    Picker("报销回款账户", selection: $targetAccountId) {
                        ForEach(store.activeAccounts) { acc in
                            Text("\(acc.name) (\(acc.currencyCode))").tag(acc.id)
                        }
                    }
                    TextField("回款备注（可选）", text: $note)
                }
                Section("勾选待报销账单（已选 \(selectedIds.count) 笔）") {
                    ForEach(pendingTxs) { tx in
                        let checked = selectedIds.contains(tx.id)
                        Button {
                            if checked {
                                selectedIds.remove(tx.id)
                            } else {
                                selectedIds.insert(tx.id)
                            }
                        } label: {
                            HStack(spacing: 10) {
                                Image(systemName: checked ? "checkmark.circle.fill" : "circle")
                                    .foregroundColor(checked ? .accentColor : .secondary)
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(tx.merchant.isEmpty ? (store.category(byId: tx.categoryId)?.name ?? "待报销") : tx.merchant)
                                        .foregroundColor(.primary)
                                        .lineLimit(1)
                                    Text(YubraeTheme.formatDate(tx.timestamp, pattern: "yyyy-MM-dd"))
                                        .font(.caption2)
                                        .foregroundColor(.secondary)
                                        .lineLimit(1)
                                }
                                Spacer(minLength: 8)
                                Text(MoneyAmount.format(tx.origAmountMicros, currencyCode: tx.origCurrency, showCurrencyCode: true))
                                    .font(.subheadline.weight(.semibold).monospacedDigit())
                                    .foregroundColor(.primary)
                                    .lineLimit(1)
                                    .fixedSize(horizontal: true, vertical: false)
                                    .layoutPriority(1)
                            }
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
            .navigationTitle("报销回款核销")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("取消") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("确认核销") {
                        do {
                            _ = try store.settleReimbursements(
                                txIds: Array(selectedIds),
                                targetAccountId: targetAccountId,
                                note: note
                            )
                            store.toastMessage = "已核销 \(selectedIds.count) 笔报销款项"
                            dismiss()
                        } catch {
                            store.lastErrorMessage = error.localizedDescription
                        }
                    }
                    .disabled(selectedIds.isEmpty || targetAccountId <= 0)
                }
            }
        }
    }
}

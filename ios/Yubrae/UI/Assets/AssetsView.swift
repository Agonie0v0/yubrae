import SwiftUI
import UIKit

private struct AccountEditContext: Identifiable {
    let id = UUID()
    let account: AccountDto?
}

public struct AssetsView: View {
    @ObservedObject public var store: LedgerStore
    @Environment(\.colorScheme) private var colorScheme

    @State private var displayCurrencyOverride: String?
    @State private var showHiddenAccounts = false
    @State private var accountEditContext: AccountEditContext?
    @State private var calibratingAccount: AccountDto?
    @State private var deletingAccount: AccountDto?
    @State private var showHiddenManagerSheet = false
    @State private var showAddDebtSheet = false
    @State private var settlingDebt: DebtRecordDto?
    @State private var showSettleReimburseSheet = false
    @State private var showArchivedSection = false

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

    private var pendingReimburseTxs: [TransactionDto] {
        store.transactions.filter { $0.reimburseStatus == ReimburseStatus.PENDING }
    }

    private var openDebts: [DebtRecordDto] {
        store.debts.filter { $0.status == DebtStatus.OPEN }
    }

    private var archivedAccounts: [AccountDto] {
        store.accounts.filter(\.archived)
    }

    public var body: some View {
        NavigationStack {
            ScrollView {
                LazyVStack(spacing: 14) {
                    netWorthHeroCard

                    quickActionStrip

                    if !hiddenModuleSet.contains(AssetHiddenModule.REIMBURSE) && !pendingReimburseTxs.isEmpty {
                        reimbursementModuleCard
                    }

                    if !hiddenModuleSet.contains(AssetHiddenModule.DEBT) {
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
                        showHiddenManagerSheet = true
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
                        Button {
                            accountEditContext = AccountEditContext(account: nil)
                        } label: {
                            Image(systemName: "plus.circle.fill")
                                .foregroundColor(palette.primary)
                        }
                    }
                }
            }
            .sheet(item: $accountEditContext) { ctx in
                AccountEditorSheet(store: store, editingAccount: ctx.account)
                    .yubraeSheetStyle(palette: palette)
            }
            .sheet(item: $calibratingAccount) { acc in
                BalanceCalibrationSheet(store: store, account: acc)
                    .yubraeSheetStyle(palette: palette)
            }
            .sheet(item: $deletingAccount) { acc in
                DeleteAccountConfirmSheet(store: store, account: acc) {
                    deletingAccount = nil
                }
                .yubraeSheetStyle(palette: palette)
            }
            .sheet(isPresented: $showHiddenManagerSheet) {
                HiddenAssetsManagerSheet(store: store)
                    .yubraeSheetStyle(palette: palette)
            }
            .sheet(isPresented: $showAddDebtSheet) {
                AddDebtSheet(store: store)
                    .yubraeSheetStyle(palette: palette)
            }
            .sheet(item: $settlingDebt) { debt in
                SettleDebtSheet(store: store, debt: debt)
                    .yubraeSheetStyle(palette: palette)
            }
            .sheet(isPresented: $showSettleReimburseSheet) {
                SettleReimburseSheet(store: store, pendingTxs: pendingReimburseTxs)
                    .yubraeSheetStyle(palette: palette)
            }
        }
    }

    // MARK: - Hero Card

    private var netWorthHeroCard: some View {
        let masked = store.settings.privacyMaskEnabled
        let cur = netWorth.displayCurrency
        let nwStr = YubraeTheme.masked(MoneyAmount.format(netWorth.netWorthMicros, currencyCode: cur), isMasked: masked)
        let assetsStr = YubraeTheme.masked(MoneyAmount.format(netWorth.totalAssetsMicros, currencyCode: cur), isMasked: masked)
        let liabStr = YubraeTheme.masked(MoneyAmount.format(netWorth.totalLiabilitiesMicros, currencyCode: cur), isMasked: masked)

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
                .font(.system(size: 32, weight: .bold, design: .rounded))
                .foregroundColor(.white)
                .minimumScaleFactor(0.7)
                .lineLimit(1)

            HStack(spacing: 20) {
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
                        .minimumScaleFactor(0.75)
                }
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
                        .minimumScaleFactor(0.75)
                }
                Spacer(minLength: 0)
            }
        }
        .padding(18)
        .background(
            RoundedRectangle(cornerRadius: 22, style: .continuous)
                .fill(palette.heroGradient)
                .shadow(color: palette.primary.opacity(0.22), radius: 14, x: 0, y: 8)
        )
    }

    private var quickActionStrip: some View {
        HStack(spacing: 10) {
            Button {
                accountEditContext = AccountEditContext(account: nil)
            } label: {
                Label("新建账户", systemImage: "plus.circle")
                    .font(.caption.weight(.semibold))
                    .foregroundColor(palette.primary)
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)
                    .padding(.horizontal, 6)
                    .padding(.vertical, 10)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .background(RoundedRectangle(cornerRadius: 12).fill(palette.surface))
                    .overlay(RoundedRectangle(cornerRadius: 12).strokeBorder(palette.outline, lineWidth: 0.8))
            }

            Button {
                showHiddenAccounts.toggle()
            } label: {
                Label(
                    showHiddenAccounts ? "隐藏私密项" : "含隐藏(\(hiddenAccountIdSet.count))",
                    systemImage: showHiddenAccounts ? "eye.slash" : "eye"
                )
                .font(.caption.weight(.semibold))
                .foregroundColor(palette.textPrimary)
                .lineLimit(1)
                .minimumScaleFactor(0.8)
                .padding(.horizontal, 6)
                .padding(.vertical, 10)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(RoundedRectangle(cornerRadius: 12).fill(palette.surface))
                .overlay(RoundedRectangle(cornerRadius: 12).strokeBorder(palette.outline, lineWidth: 0.8))
            }

            Button {
                showAddDebtSheet = true
            } label: {
                Label("记借贷", systemImage: "arrow.left.arrow.right.circle")
                    .font(.caption.weight(.semibold))
                    .foregroundColor(palette.textPrimary)
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)
                    .padding(.horizontal, 6)
                    .padding(.vertical, 10)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .background(RoundedRectangle(cornerRadius: 12).fill(palette.surface))
                    .overlay(RoundedRectangle(cornerRadius: 12).strokeBorder(palette.outline, lineWidth: 0.8))
            }
        }
        .fixedSize(horizontal: false, vertical: true)
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
                    showSettleReimburseSheet = true
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
                        showAddDebtSheet = true
                    }
                    .font(.caption.weight(.semibold))
                    .foregroundColor(palette.primary)
                    .lineLimit(1)
                    .fixedSize(horizontal: true, vertical: false)
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
                                    settlingDebt = debt
                                }
                                .font(.caption2.weight(.semibold))
                                .foregroundColor(palette.primary)
                                .lineLimit(1)
                                .fixedSize(horizontal: true, vertical: false)
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
        let visibleAccounts = store.activeAccounts.filter { acc in
            if !showHiddenAccounts && hiddenAccountIdSet.contains(acc.id) {
                return false
            }
            if acc.groupType == AccountGroup.INVEST && hiddenModuleSet.contains(AssetHiddenModule.INVEST) && !showHiddenAccounts {
                return false
            }
            return true
        }

        return Group {
            if visibleAccounts.isEmpty {
                YubraeEmptyStateView(
                    icon: "creditcard",
                    title: "暂无可展示的账户",
                    subtitle: "你可以新建账户，或在「隐藏资产管理」中取消隐藏。",
                    palette: palette,
                    actionTitle: "新建账户",
                    action: { accountEditContext = AccountEditContext(account: nil) }
                )
            } else {
                ForEach(AccountGroup.allCases) { group in
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

        return VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 8) {
                Label(group.labelZh, systemImage: group.sfSymbol)
                    .font(.caption.weight(.semibold))
                    .foregroundColor(palette.textSecondary)
                    .lineLimit(1)
                Spacer(minLength: 8)
                Text(YubraeTheme.masked(MoneyAmount.format(groupSubtotal, currencyCode: activeDisplayCurrency), isMasked: masked))
                    .font(.caption.weight(.semibold).monospacedDigit())
                    .foregroundColor(palette.textSecondary)
                    .lineLimit(1)
                    .fixedSize(horizontal: true, vertical: false)
            }
            .padding(.horizontal, 4)

            YubraeCard(palette: palette, padding: 0) {
                VStack(spacing: 0) {
                    ForEach(accounts) { acc in
                        accountRow(acc)
                        if acc.id != accounts.last?.id {
                            Divider().padding(.leading, 64)
                        }
                    }
                }
            }
        }
    }

    private func accountRow(_ acc: AccountDto) -> some View {
        let masked = store.settings.privacyMaskEnabled
        let isHidden = hiddenAccountIdSet.contains(acc.id)
        let origFmt = MoneyAmount.format(acc.balanceMicros, currencyCode: acc.currencyCode, showCurrencyCode: true)
        let convertedMicros = netWorth.accountConvertedBalances[acc.id] ?? acc.balanceMicros

        return HStack(spacing: 12) {
            YubraeCategoryIconBadge(iconKey: acc.icon, colorHex: acc.colorHex, size: 40)

            VStack(alignment: .leading, spacing: 3) {
                HStack(spacing: 6) {
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
                    }
                }

                if acc.groupType == AccountGroup.CREDIT {
                    let limitStr = MoneyAmount.format(acc.creditLimitMicros, currencyCode: acc.currencyCode)
                    let billText = acc.billingDay.map { "账单日\($0)日" } ?? ""
                    let repayText = acc.repaymentDay.map { "还款日\($0)日" } ?? ""
                    Text("额度 \(YubraeTheme.masked(limitStr, isMasked: masked)) \(billText) \(repayText)")
                        .font(.caption2)
                        .foregroundColor(palette.textSecondary)
                        .lineLimit(1)
                } else {
                    Text(CurrencyCatalog.find(acc.currencyCode).nameZh + " · " + acc.currencyCode)
                        .font(.caption2)
                        .foregroundColor(palette.textSecondary)
                        .lineLimit(1)
                }
            }

            Spacer(minLength: 6)

            VStack(alignment: .trailing, spacing: 2) {
                Text(YubraeTheme.masked(origFmt, isMasked: masked))
                    .font(.subheadline.weight(.bold).monospacedDigit())
                    .foregroundColor(acc.balanceMicros < 0 ? palette.expenseColor : palette.textPrimary)
                    .lineLimit(1)
                    .fixedSize(horizontal: true, vertical: false)
                if acc.currencyCode != activeDisplayCurrency {
                    let convFmt = MoneyAmount.format(convertedMicros, currencyCode: activeDisplayCurrency)
                    Text("≈ \(YubraeTheme.masked(convFmt, isMasked: masked))")
                        .font(.caption2)
                        .foregroundColor(palette.textTertiary)
                        .lineLimit(1)
                        .fixedSize(horizontal: true, vertical: false)
                }
            }
            .layoutPriority(1)
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 12)
        .contentShape(Rectangle())
        .onTapGesture {
            accountEditContext = AccountEditContext(account: acc)
        }
        .contextMenu {
            Button {
                calibratingAccount = acc
            } label: {
                Label("余额校准", systemImage: "slider.horizontal.3")
            }
            Button {
                accountEditContext = AccountEditContext(account: acc)
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
                deletingAccount = acc
            } label: {
                Label("删除账户", systemImage: "trash")
            }
        }
    }

    private var archivedAccountsCard: some View {
        YubraeCard(palette: palette) {
            DisclosureGroup("已归档账户（\(archivedAccounts.count)）", isExpanded: $showArchivedSection) {
                VStack(spacing: 10) {
                    ForEach(archivedAccounts) { acc in
                        HStack(spacing: 10) {
                            Text(acc.name)
                                .font(.subheadline)
                                .foregroundColor(palette.textSecondary)
                                .lineLimit(1)
                            Spacer(minLength: 8)
                            Button("恢复活跃") {
                                store.archiveAccount(id: acc.id, archived: false)
                                store.toastMessage = "已恢复账户「\(acc.name)」"
                            }
                            .buttonStyle(.borderless)
                            .font(.caption.weight(.semibold))
                            .foregroundColor(palette.primary)
                            .lineLimit(1)
                            .fixedSize(horizontal: true, vertical: false)
                            Button("删除", role: .destructive) {
                                deletingAccount = acc
                            }
                            .buttonStyle(.borderless)
                            .font(.caption.weight(.semibold))
                            .lineLimit(1)
                            .fixedSize(horizontal: true, vertical: false)
                        }
                    }
                }
                .padding(.top, 8)
            }
            .font(.subheadline.weight(.medium))
            .foregroundColor(palette.textSecondary)
        }
    }

    private func toggleAccountHidden(_ accountId: Int64) {
        store.updateSettings { s in
            if s.hiddenAssetAccountIds.contains(accountId) {
                s.hiddenAssetAccountIds.removeAll { $0 == accountId }
            } else {
                s.hiddenAssetAccountIds.append(accountId)
            }
        }
    }
}

// MARK: - Account Editor Sheet

private struct AccountEditorSheet: View {
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

    init(store: LedgerStore, editingAccount: AccountDto?) {
        self.store = store
        self.editingAccount = editingAccount
        if let acc = editingAccount {
            let digits = CurrencyCatalog.find(acc.currencyCode).minorDigits
            _name = State(initialValue: acc.name)
            _groupType = State(initialValue: acc.groupType)
            _currencyCode = State(initialValue: acc.currencyCode)
            _balanceText = State(initialValue: MoneyAmount.formatPlain(acc.balanceMicros, minorDigits: digits, grouping: false))
            _creditLimitText = State(initialValue: acc.creditLimitMicros > 0 ? MoneyAmount.formatPlain(acc.creditLimitMicros, minorDigits: digits, grouping: false) : "")
            _billingDayText = State(initialValue: acc.billingDay.map(String.init) ?? "")
            _repaymentDayText = State(initialValue: acc.repaymentDay.map(String.init) ?? "")
            _selectedIcon = State(initialValue: acc.icon)
            _selectedColorHex = State(initialValue: acc.colorHex)
            _includeInNetWorth = State(initialValue: acc.includeInNetWorth)
        } else {
            _name = State(initialValue: "")
            _groupType = State(initialValue: AccountGroup.WALLET)
            _currencyCode = State(initialValue: store.settings.baseCurrency)
            _balanceText = State(initialValue: "0.00")
            _creditLimitText = State(initialValue: "")
            _billingDayText = State(initialValue: "")
            _repaymentDayText = State(initialValue: "")
            _selectedIcon = State(initialValue: "wallet")
            _selectedColorHex = State(initialValue: 0xFF2AA876)
            _includeInNetWorth = State(initialValue: true)
        }
    }

    var body: some View {
        NavigationStack {
            Form {
                Section("基本信息") {
                    TextField("账户名称（如 招商银行储蓄卡）", text: $name)
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

                if let existing = editingAccount {
                    Section("快捷操作") {
                        Button {
                            showCalibration = true
                        } label: {
                            Label("余额校准", systemImage: "slider.horizontal.3")
                        }
                        Button(role: .destructive) {
                            showDeleteConfirm = true
                        } label: {
                            Label("删除该账户", systemImage: "trash")
                        }
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
                if let existing = editingAccount {
                    BalanceCalibrationSheet(store: store, account: existing)
                        .yubraeSheetStyle(palette: palette)
                }
            }
            .sheet(isPresented: $showDeleteConfirm) {
                if let existing = editingAccount {
                    DeleteAccountConfirmSheet(store: store, account: existing) {
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
        let balMicros = MoneyAmount.parseToMicros(balanceText) ?? (editingAccount?.balanceMicros ?? 0)
        let limitMicros = MoneyAmount.parseToMicros(creditLimitText) ?? 0
        let billDay = Int(billingDayText).flatMap { (1...31).contains($0) ? $0 : nil }
        let repayDay = Int(repaymentDayText).flatMap { (1...31).contains($0) ? $0 : nil }

        let dto = AccountDto(
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
        _ = store.saveAccount(dto)
        store.toastMessage = editingAccount == nil ? "已创建账户「\(trimmedName)」" : "已保存账户修改"
        dismiss()
    }
}

// MARK: - Balance Calibration Sheet

private struct BalanceCalibrationSheet: View {
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
                    HStack {
                        Text("账户")
                        Spacer()
                        Text(account.name).foregroundColor(.secondary)
                    }
                    HStack {
                        Text("校准前账面余额")
                        Spacer()
                        Text(MoneyAmount.format(account.balanceMicros, currencyCode: account.currencyCode, showCurrencyCode: true))
                            .fontWeight(.semibold)
                    }
                }
                Section {
                    TextField("输入当前实际余额", text: $targetBalanceText)
                        .keyboardType(.numbersAndPunctuation)
                    if let delta = deltaMicros {
                        HStack {
                            Text("差额变动")
                            Spacer()
                            Text(MoneyAmount.format(delta, currencyCode: account.currencyCode, showSign: true, showCurrencyCode: true))
                                .foregroundColor(delta > 0 ? .green : (delta < 0 ? .red : .secondary))
                                .fontWeight(.semibold)
                        }
                    }
                    Toggle("生成差额变动记录", isOn: $createRecord)
                    if createRecord {
                        TextField("备注", text: $noteText)
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
                                Spacer()
                                Text(acc.currencyCode)
                                    .font(.caption)
                                    .foregroundColor(.secondary)
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

private struct DeleteAccountConfirmSheet: View {
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

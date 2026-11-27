import Charts
import SwiftUI
import UIKit

private enum AccountHistoryRangeMode: String, CaseIterable, Identifiable {
    case monthly = "按月"
    case yearly = "按年"
    case all = "全部"

    var id: String { rawValue }
}

private enum AccountTxTypeFilter: String, CaseIterable, Identifiable {
    case all = "全部"
    case expense = "支出"
    case income = "收入"
    case transfer = "转账"
    case adjust = "校准"

    var id: String { rawValue }
}

private struct AccountDayGroup: Identifiable {
    let id: String
    let dateText: String
    let subText: String
    let inflowMicros: Int64
    let outflowMicros: Int64
    let items: [TransactionDto]
}

private struct AccountRowMicroBadge: Identifiable {
    let id: String
    let text: String
    let color: Color
}

/// 账户详情页统一金额格式化：对字母代码币种（如 `CHF`）采用 `12.00 CHF` 后置格式，避免 `CHF12.00 CHF` 重复代码；符号币种保留清晰货币符号。
fileprivate func formatAccountDetailAmount(
    _ micros: Int64,
    currencyCode: String,
    showSign: Bool = false,
    showCurrencyCode: Bool = false
) -> String {
    let norm = CurrencyCatalog.normalize(currencyCode)
    let info = CurrencyCatalog.find(norm)
    let trimmedSymbol = info.symbol.trimmingCharacters(in: .whitespacesAndNewlines)
    if trimmedSymbol.isEmpty || trimmedSymbol.caseInsensitiveCompare(info.code) == .orderedSame {
        return YubraeTheme.formatPlainSignedAmount(micros, currencyCode: norm, baseCurrency: norm, showSign: showSign)
    }
    return MoneyAmount.format(micros, currencyCode: norm, showSign: showSign, showCurrencyCode: showCurrencyCode)
}

private struct DetailQuickRecordContext: Identifiable {
    let id = UUID()
    let editingTransaction: TransactionDto?
    let initialType: String
    let initialAccountId: Int64?
    let initialToAccountId: Int64?
    let initialAmountMicros: Int64?
}

private enum AccountDetailActiveSheet: Identifiable {
    case editAccount(AccountDto)
    case calibrate(AccountDto)
    case deleteAccount(AccountDto)
    case addInstallment(AccountDto)
    case pickMonth
    case pickYear
    case batchCategory
    case batchAccount

    var id: String {
        switch self {
        case .editAccount(let acc): return "edit-\(acc.id)"
        case .calibrate(let acc): return "cal-\(acc.id)"
        case .deleteAccount(let acc): return "del-\(acc.id)"
        case .addInstallment(let acc): return "inst-\(acc.id)"
        case .pickMonth: return "pickMonth"
        case .pickYear: return "pickYear"
        case .batchCategory: return "batchCategory"
        case .batchAccount: return "batchAccount"
        }
    }
}

private enum AccountDetailConfirmAction {
    case deleteTransaction(TransactionDto)
    case batchDelete([Int64])
    case deleteInstallment(InstallmentPlanDto)
}

public struct AccountDetailView: View {
    @ObservedObject public var store: LedgerStore
    public let accountId: Int64

    @Environment(\.dismiss) private var dismiss
    @Environment(\.colorScheme) private var colorScheme

    // Trend chart state
    @State private var trendDays: Int = 30
    @State private var selectedTrendPoint: AccountDailyBalancePoint?

    // History workbench state
    @State private var rangeMode: AccountHistoryRangeMode = .monthly
    @State private var selectedYear: Int
    @State private var selectedMonth: Int
    @State private var txSearchQuery: String = ""
    @State private var txTypeFilter: AccountTxTypeFilter = .all

    // Multi-select batch mode
    @State private var isSelectionMode: Bool = false
    @State private var selectedTxIds: Set<Int64> = []

    // Sheets & dialogs
    @State private var activeSheet: AccountDetailActiveSheet?
    @State private var quickRecordContext: DetailQuickRecordContext?
    @State private var pendingConfirmAction: AccountDetailConfirmAction?

    private let trendDayOptions: [Int] = [7, 30, 90, 180]

    public init(store: LedgerStore, accountId: Int64) {
        self.store = store
        self.accountId = accountId
        let comps = Calendar.current.dateComponents([.year, .month], from: Date())
        _selectedYear = State(initialValue: comps.year ?? 2026)
        _selectedMonth = State(initialValue: comps.month ?? 1)
    }

    private var palette: YubraePalette {
        YubraeTheme.palette(for: store.settings, colorScheme: colorScheme)
    }

    private var account: AccountDto? {
        store.account(byId: accountId)
    }

    private var currentYearMonth: (year: Int, month: Int) {
        let comps = Calendar.current.dateComponents([.year, .month], from: Date())
        return (comps.year ?? 2026, comps.month ?? 1)
    }

    private var allAccountTransactions: [TransactionDto] {
        store.transactions
            .filter { $0.accountId == accountId || $0.toAccountId == accountId }
            .sorted { lhs, rhs in
                if lhs.timestamp != rhs.timestamp { return lhs.timestamp > rhs.timestamp }
                return lhs.id > rhs.id
            }
    }

    private var periodTransactions: [TransactionDto] {
        switch rangeMode {
        case .monthly:
            let range = store.monthRangeMillis(year: selectedYear, month: selectedMonth)
            return allAccountTransactions.filter { $0.timestamp >= range.0 && $0.timestamp <= range.1 }
        case .yearly:
            let range = store.yearRangeMillis(year: selectedYear)
            return allAccountTransactions.filter { $0.timestamp >= range.0 && $0.timestamp <= range.1 }
        case .all:
            return allAccountTransactions
        }
    }

    private var periodFlowSummary: (inflow: Int64, outflow: Int64, net: Int64) {
        var inflow: Int64 = 0
        var outflow: Int64 = 0
        for tx in periodTransactions where tx.status == TransactionStatus.CONFIRMED {
            let delta = store.accountDeltaForTransaction(tx, accountId: accountId)
            if delta > 0 {
                inflow += delta
            } else if delta < 0 {
                outflow += -delta
            }
        }
        return (inflow, outflow, inflow - outflow)
    }

    private var filteredTransactions: [TransactionDto] {
        let query = txSearchQuery.trimmingCharacters(in: .whitespacesAndNewlines)
        return periodTransactions.filter { tx in
            let typeOk: Bool
            switch txTypeFilter {
            case .all: typeOk = true
            case .expense: typeOk = tx.type == TransactionType.EXPENSE
            case .income: typeOk = tx.type == TransactionType.INCOME
            case .transfer: typeOk = tx.type == TransactionType.TRANSFER
            case .adjust: typeOk = tx.type == TransactionType.ADJUST
            }
            guard typeOk else { return false }
            guard !query.isEmpty else { return true }
            if store.matchesSearchQuery(query, for: tx) {
                return true
            }
            let delta = store.accountDeltaForTransaction(tx, accountId: accountId)
            let digits = CurrencyCatalog.find(tx.origCurrency).minorDigits
            let origPlain = MoneyAmount.formatPlain(abs(tx.origAmountMicros), minorDigits: digits, grouping: false)
            let deltaPlain = MoneyAmount.formatPlain(abs(delta), minorDigits: 2, grouping: false)
            return origPlain.localizedCaseInsensitiveContains(query)
                || deltaPlain.localizedCaseInsensitiveContains(query)
        }
    }

    private var groupedByDay: [AccountDayGroup] {
        var order: [String] = []
        var map: [String: [TransactionDto]] = [:]
        for tx in filteredTransactions {
            let key = DefaultSeedData.formatISODate(epochMillis: tx.timestamp)
            if map[key] == nil {
                order.append(key)
            }
            map[key, default: []].append(tx)
        }
        return order.compactMap { key -> AccountDayGroup? in
            guard let items = map[key], let first = items.first else { return nil }
            var inflow: Int64 = 0
            var outflow: Int64 = 0
            for tx in items where tx.status == TransactionStatus.CONFIRMED {
                let delta = store.accountDeltaForTransaction(tx, accountId: accountId)
                if delta > 0 {
                    inflow += delta
                } else if delta < 0 {
                    outflow += -delta
                }
            }
            let headerParts = YubraeTheme.formatDayHeaderParts(first.timestamp)
            return AccountDayGroup(
                id: key,
                dateText: headerParts.dateText,
                subText: headerParts.subText,
                inflowMicros: inflow,
                outflowMicros: outflow,
                items: items
            )
        }
    }

    private var confirmDialogBinding: Binding<Bool> {
        Binding(
            get: { pendingConfirmAction != nil },
            set: { isPresented in
                if !isPresented {
                    pendingConfirmAction = nil
                }
            }
        )
    }

    private var confirmDialogMeta: (title: String, message: String, confirmTitle: String) {
        switch pendingConfirmAction {
        case .deleteTransaction(let tx):
            let amt = formatAccountDetailAmount(abs(tx.origAmountMicros), currencyCode: tx.origCurrency, showCurrencyCode: true)
            return ("删除该笔账单？", "删除后将自动回滚关联账户余额（金额 \(amt)），且无法撤销。", "确认删除")
        case .batchDelete(let ids):
            return ("批量删除 \(ids.count) 笔账单？", "删除后将同步回滚涉及账户的账面余额，操作不可撤销。", "删除 \(ids.count) 笔")
        case .deleteInstallment(let plan):
            return ("删除分期计划？", "将移除分期计划「\(plan.name)」，已生成的历史分期账单不会被删除。", "确认删除")
        case nil:
            return ("确认操作", "", "确认")
        }
    }

    public var body: some View {
        Group {
            if let acc = account {
                accountDetailContent(acc)
            } else {
                VStack {
                    YubraeEmptyStateView(
                        icon: "creditcard.trianglebadge.exclamationmark",
                        title: "账户已不存在",
                        subtitle: "该账户可能已被删除。",
                        palette: palette,
                        actionTitle: "返回资产页",
                        action: { dismiss() }
                    )
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(palette.background.ignoresSafeArea())
            }
        }
        .navigationTitle(account?.name ?? "账户详情")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            if let acc = account {
                ToolbarItem(placement: .navigationBarTrailing) {
                    HStack(spacing: 12) {
                        Button {
                            store.updateSettings { $0.privacyMaskEnabled.toggle() }
                        } label: {
                            Image(systemName: store.settings.privacyMaskEnabled ? "eye.slash" : "eye")
                                .foregroundColor(palette.textPrimary)
                        }
                        .accessibilityLabel("切换隐私遮罩")

                        Button {
                            activeSheet = .editAccount(acc)
                        } label: {
                            Image(systemName: "pencil")
                                .foregroundColor(palette.textPrimary)
                        }
                        .accessibilityLabel("编辑账户")

                        Menu {
                            Button {
                                let targetArchived = !acc.archived
                                store.archiveAccount(id: acc.id, archived: targetArchived)
                                store.toastMessage = targetArchived ? "已归档账户「\(acc.name)」" : "已恢复账户「\(acc.name)」"
                            } label: {
                                Label(acc.archived ? "恢复活跃账户" : "归档此账户", systemImage: acc.archived ? "arrow.uturn.backward.circle" : "archivebox")
                            }
                            Divider()
                            Button(role: .destructive) {
                                activeSheet = .deleteAccount(acc)
                            } label: {
                                Label("删除账户", systemImage: "trash")
                            }
                        } label: {
                            Image(systemName: "ellipsis.circle")
                                .foregroundColor(palette.textPrimary)
                        }
                        .accessibilityLabel("更多账户操作")
                    }
                }
            }
        }
        .yubraeConfirmDialog(
            isPresented: confirmDialogBinding,
            palette: palette,
            icon: "trash.fill",
            title: confirmDialogMeta.title,
            message: confirmDialogMeta.message,
            confirmTitle: confirmDialogMeta.confirmTitle,
            cancelTitle: "取消",
            isDestructive: true
        ) {
            executePendingConfirmAction()
        }
        .onChange(of: trendDays) { _ in
            selectedTrendPoint = nil
        }
        .onChange(of: rangeMode) { _ in
            if isSelectionMode {
                selectedTxIds.removeAll()
            }
        }
    }

    private func accountDetailContent(_ acc: AccountDto) -> some View {
        ScrollView {
            LazyVStack(spacing: 14) {
                heroInfoCard(acc)
                quickActionsRow(acc)
                balanceTrendCard(acc)

                if acc.groupType.uppercased() == AccountGroup.CREDIT {
                    creditBillingCard(acc)
                    installmentsCard(acc)
                }

                historyWorkbenchSection(acc)
            }
            .padding(.horizontal, 16)
            .padding(.top, 10)
            .padding(.bottom, isSelectionMode ? 24 : 40)
        }
        .background(palette.background.ignoresSafeArea())
        .safeAreaInset(edge: .bottom, spacing: 0) {
            if isSelectionMode {
                batchActionBottomBar(acc)
            }
        }
        .sheet(item: $activeSheet) { sheet in
            sheetDestination(for: sheet, account: acc)
                .yubraeSheetStyle(palette: palette)
        }
        .fullScreenCover(item: $quickRecordContext) { ctx in
            QuickRecordSheet(
                store: store,
                editingTransaction: ctx.editingTransaction,
                initialType: ctx.initialType,
                initialAccountId: ctx.initialAccountId,
                initialToAccountId: ctx.initialToAccountId,
                initialAmountMicros: ctx.initialAmountMicros
            )
            .yubraeSheetStyle(palette: palette)
        }
    }

    @ViewBuilder
    private func sheetDestination(for sheet: AccountDetailActiveSheet, account acc: AccountDto) -> some View {
        switch sheet {
        case .editAccount(let target):
            AccountEditorSheet(store: store, editingAccount: target, initialGroupType: target.groupType)
        case .calibrate(let target):
            BalanceCalibrationSheet(store: store, account: target)
        case .deleteAccount(let target):
            AccountDeleteSheet(store: store, account: target) {
                activeSheet = nil
                dismiss()
            }
        case .addInstallment(let target):
            InstallmentEditorSheet(store: store, account: target)
        case .pickMonth:
            YubraeYearMonthPickerSheet(
                initialYear: selectedYear,
                initialMonth: selectedMonth,
                palette: palette
            ) { year, month in
                selectedYear = year
                selectedMonth = month
            }
        case .pickYear:
            YubraeYearPickerSheet(
                initialYear: selectedYear,
                palette: palette
            ) { year in
                selectedYear = year
            }
        case .batchCategory:
            BatchCategoryPickerSheet(store: store, selectedCount: selectedTxIds.count) { catId in
                let updated = store.batchUpdateCategory(txIds: Array(selectedTxIds), categoryId: catId)
                store.toastMessage = "已批量修改 \(updated) 笔账单分类"
                exitSelectionMode()
            }
        case .batchAccount:
            BatchAccountPickerSheet(store: store, currentAccountId: acc.id, selectedCount: selectedTxIds.count) { targetAccId in
                do {
                    let moved = try store.batchMoveTransactionsAccount(
                        txIds: Array(selectedTxIds),
                        targetAccountId: targetAccId
                    )
                    let targetName = store.account(byId: targetAccId)?.name ?? "目标账户"
                    store.toastMessage = moved > 0
                        ? "已将 \(moved) 笔账单迁移至「\(targetName)」"
                        : "所选账单均已在目标账户或因转账冲突跳过"
                    exitSelectionMode()
                } catch {
                    store.lastErrorMessage = error.localizedDescription
                }
            }
        }
    }

    // MARK: - 1. Top Hero Info Card

    private func heroInfoCard(_ acc: AccountDto) -> some View {
        let masked = store.settings.privacyMaskEnabled
        let curInfo = CurrencyCatalog.find(acc.currencyCode)
        let baseCur = CurrencyCatalog.normalize(store.settings.baseCurrency)
        let accCur = CurrencyCatalog.normalize(acc.currencyCode)
        let balanceStr = YubraeTheme.masked(
            formatAccountDetailAmount(acc.balanceMicros, currencyCode: accCur, showCurrencyCode: true),
            isMasked: masked
        )
        let convertedBase: Int64 = {
            if accCur == baseCur { return acc.balanceMicros }
            let rate = store.getRate(from: accCur, to: baseCur).rateMicros
            return MoneyAmount.convert(amountMicros: acc.balanceMicros, rateMicros: rate)
        }()
        let flow = periodFlowSummary
        let trimmedNote = acc.note.trimmingCharacters(in: .whitespacesAndNewlines)

        return VStack(alignment: .leading, spacing: 14) {
            HStack(spacing: 12) {
                ZStack {
                    Circle()
                        .fill(Color.white.opacity(0.22))
                        .frame(width: 44, height: 44)
                    Image(systemName: DefaultSeedData.sfSymbol(for: acc.icon))
                        .font(.system(size: 19, weight: .semibold))
                        .foregroundColor(.white)
                }

                VStack(alignment: .leading, spacing: 4) {
                    HStack(spacing: 6) {
                        Text(acc.name)
                            .font(.headline.weight(.bold))
                            .foregroundColor(.white)
                            .lineLimit(1)
                        Text("\(curInfo.flag) \(accCur)")
                            .font(.system(size: 11, weight: .bold))
                            .foregroundColor(.white)
                            .lineLimit(1)
                            .fixedSize(horizontal: true, vertical: false)
                            .padding(.horizontal, 7)
                            .padding(.vertical, 2)
                            .background(Capsule().fill(Color.white.opacity(0.20)))
                    }
                    HStack(spacing: 6) {
                        Text(AccountGroup.labelZh(for: acc.groupType))
                            .font(.caption)
                            .foregroundColor(.white.opacity(0.82))
                            .lineLimit(1)
                        if !acc.includeInNetWorth {
                            Text("不计净资产")
                                .font(.system(size: 10, weight: .semibold))
                                .foregroundColor(.white.opacity(0.95))
                                .lineLimit(1)
                                .fixedSize(horizontal: true, vertical: false)
                                .padding(.horizontal, 6)
                                .padding(.vertical, 1.5)
                                .background(Capsule().fill(Color.white.opacity(0.18)))
                        }
                        if acc.archived {
                            Text("已归档")
                                .font(.system(size: 10, weight: .semibold))
                                .foregroundColor(.white)
                                .lineLimit(1)
                                .fixedSize(horizontal: true, vertical: false)
                                .padding(.horizontal, 6)
                                .padding(.vertical, 1.5)
                                .background(Capsule().fill(Color.black.opacity(0.28)))
                        }
                    }
                }
                Spacer(minLength: 0)
            }

            VStack(alignment: .leading, spacing: 4) {
                Text(acc.groupType.uppercased() == AccountGroup.CREDIT ? "当前账面余额" : "账户余额")
                    .font(.caption)
                    .foregroundColor(.white.opacity(0.78))
                Text(balanceStr)
                    .font(.system(size: 32, weight: .bold, design: .rounded).monospacedDigit())
                    .foregroundColor(.white)
                    .lineLimit(1)
                    .minimumScaleFactor(0.5)
                if accCur != baseCur {
                    let baseInfo = CurrencyCatalog.find(baseCur)
                    let convStr = YubraeTheme.masked(
                        formatAccountDetailAmount(convertedBase, currencyCode: baseCur),
                        isMasked: masked
                    )
                    let codeSuffix = baseInfo.symbol.caseInsensitiveCompare(baseInfo.code) == .orderedSame ? "" : " (\(baseCur))"
                    Text("折合参考 ≈ \(convStr)\(codeSuffix)")
                        .font(.caption.monospacedDigit())
                        .foregroundColor(.white.opacity(0.82))
                        .lineLimit(1)
                }
            }

            if !trimmedNote.isEmpty {
                HStack(alignment: .top, spacing: 6) {
                    Image(systemName: "note.text")
                        .font(.caption)
                        .foregroundColor(.white.opacity(0.82))
                        .padding(.top, 1)
                    Text(trimmedNote)
                        .font(.caption)
                        .foregroundColor(.white.opacity(0.92))
                        .lineLimit(2)
                        .multilineTextAlignment(.leading)
                    Spacer(minLength: 0)
                }
                .padding(.horizontal, 11)
                .padding(.vertical, 8)
                .background(
                    RoundedRectangle(cornerRadius: 10, style: .continuous)
                        .fill(Color.white.opacity(0.13))
                )
            }

            // 3-column period summary
            HStack(spacing: 10) {
                heroFlowMetric(
                    title: "期间流入",
                    value: YubraeTheme.masked(formatAccountDetailAmount(flow.inflow, currencyCode: accCur, showSign: flow.inflow > 0), isMasked: masked)
                )
                Divider()
                    .frame(height: 26)
                    .background(Color.white.opacity(0.28))
                heroFlowMetric(
                    title: "期间流出",
                    value: YubraeTheme.masked(formatAccountDetailAmount(flow.outflow == 0 ? 0 : -flow.outflow, currencyCode: accCur), isMasked: masked)
                )
                Divider()
                    .frame(height: 26)
                    .background(Color.white.opacity(0.28))
                heroFlowMetric(
                    title: "净变动",
                    value: YubraeTheme.masked(formatAccountDetailAmount(flow.net, currencyCode: accCur, showSign: true), isMasked: masked)
                )
            }
            .padding(.top, 2)
        }
        .padding(18)
        .background(
            RoundedRectangle(cornerRadius: 22, style: .continuous)
                .fill(palette.heroGradient)
                .shadow(color: palette.primary.opacity(0.22), radius: 14, x: 0, y: 8)
        )
    }

    private func heroFlowMetric(title: String, value: String) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(title)
                .font(.caption2)
                .foregroundColor(.white.opacity(0.78))
                .lineLimit(1)
            Text(value)
                .font(.subheadline.weight(.semibold).monospacedDigit())
                .foregroundColor(.white)
                .lineLimit(1)
                .minimumScaleFactor(0.55)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    // MARK: - 3 Quick Actions Bar

    private func quickActionsRow(_ acc: AccountDto) -> some View {
        let isCredit = acc.groupType.uppercased() == AccountGroup.CREDIT
        return HStack(spacing: 8) {
            detailQuickButton(title: "记一笔", systemImage: "plus.circle.fill", accent: palette.primary) {
                quickRecordContext = DetailQuickRecordContext(
                    editingTransaction: nil,
                    initialType: TransactionType.EXPENSE,
                    initialAccountId: acc.id,
                    initialToAccountId: nil,
                    initialAmountMicros: nil
                )
            }

            detailQuickButton(
                title: isCredit ? "还信用卡" : "转账",
                systemImage: isCredit ? "arrow.uturn.left.circle.fill" : "arrow.left.arrow.right.circle.fill",
                accent: palette.transferColor
            ) {
                if isCredit {
                    quickRecordContext = DetailQuickRecordContext(
                        editingTransaction: nil,
                        initialType: TransactionType.TRANSFER,
                        initialAccountId: nil,
                        initialToAccountId: acc.id,
                        initialAmountMicros: max(0, -acc.balanceMicros)
                    )
                } else {
                    quickRecordContext = DetailQuickRecordContext(
                        editingTransaction: nil,
                        initialType: TransactionType.TRANSFER,
                        initialAccountId: acc.id,
                        initialToAccountId: nil,
                        initialAmountMicros: nil
                    )
                }
            }

            detailQuickButton(title: "校准余额", systemImage: "slider.horizontal.3", accent: palette.warningColor) {
                activeSheet = .calibrate(acc)
            }
        }
    }

    private func detailQuickButton(
        title: String,
        systemImage: String,
        accent: Color,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            VStack(spacing: 5) {
                Image(systemName: systemImage)
                    .font(.system(size: 18, weight: .semibold))
                    .foregroundColor(accent)
                Text(title)
                    .font(.caption.weight(.semibold))
                    .foregroundColor(palette.textPrimary)
                    .lineLimit(1)
                    .fixedSize(horizontal: true, vertical: false)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 11)
            .padding(.horizontal, 4)
            .background(
                RoundedRectangle(cornerRadius: 14, style: .continuous)
                    .fill(palette.surface)
            )
            .overlay(
                RoundedRectangle(cornerRadius: 14, style: .continuous)
                    .strokeBorder(palette.outline, lineWidth: 0.8)
            )
        }
        .buttonStyle(.plain)
    }

    // MARK: - 2. Balance Trend Chart Card (iOS 16 Native Swift Charts)

    private func balanceTrendCard(_ acc: AccountDto) -> some View {
        let points = store.computeAccountDailyBalanceTrend(accountId: acc.id, days: trendDays)
        let masked = store.settings.privacyMaskEnabled
        let cur = CurrencyCatalog.normalize(acc.currencyCode)

        let maxMicros = points.map(\.balanceMicros).max() ?? acc.balanceMicros
        let minMicros = points.map(\.balanceMicros).min() ?? acc.balanceMicros
        let deltaMicros = (points.last?.balanceMicros ?? acc.balanceMicros) - (points.first?.balanceMicros ?? acc.balanceMicros)

        let minVal = Double(minMicros) / Double(MoneyAmount.amountScale)
        let maxVal = Double(maxMicros) / Double(MoneyAmount.amountScale)
        let yMin: Double
        let yMax: Double
        if abs(maxVal - minVal) < 0.0001 {
            let pad = max(abs(minVal) * 0.1, 10.0)
            yMin = minVal - pad
            yMax = maxVal + pad
        } else {
            let span = maxVal - minVal
            yMin = minVal - span * 0.12
            yMax = maxVal + span * 0.12
        }

        return YubraeCard(palette: palette) {
            VStack(alignment: .leading, spacing: 12) {
                HStack(spacing: 8) {
                    Label("余额走势", systemImage: "chart.xyaxis.line")
                        .font(.subheadline.weight(.bold))
                        .foregroundColor(palette.textPrimary)
                        .lineLimit(1)
                    Spacer(minLength: 6)
                    HStack(spacing: 4) {
                        ForEach(trendDayOptions, id: \.self) { days in
                            let active = trendDays == days
                            Button {
                                trendDays = days
                            } label: {
                                Text("\(days)天")
                                    .font(.system(size: 11, weight: .semibold).monospacedDigit())
                                    .lineLimit(1)
                                    .fixedSize(horizontal: true, vertical: false)
                                    .foregroundColor(active ? palette.onPrimary : palette.textSecondary)
                                    .padding(.horizontal, 8)
                                    .padding(.vertical, 4)
                                    .background(Capsule().fill(active ? palette.primary : palette.surfaceSecondary))
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }

                // Period High / Low / Net change OR active scrubbed point tooltip
                if let hovered = selectedTrendPoint {
                    HStack(spacing: 8) {
                        Image(systemName: "hand.point.up.left.fill")
                            .font(.caption)
                            .foregroundColor(palette.primary)
                        Text(formatChartDate(hovered.date))
                            .font(.caption.weight(.semibold))
                            .foregroundColor(palette.textSecondary)
                            .lineLimit(1)
                            .fixedSize(horizontal: true, vertical: false)
                        Spacer(minLength: 6)
                        Text(YubraeTheme.masked(formatAccountDetailAmount(hovered.balanceMicros, currencyCode: cur, showCurrencyCode: true), isMasked: masked))
                            .font(.subheadline.weight(.bold).monospacedDigit())
                            .foregroundColor(palette.primary)
                            .lineLimit(1)
                            .fixedSize(horizontal: true, vertical: false)
                    }
                    .padding(.horizontal, 10)
                    .padding(.vertical, 7)
                    .background(
                        RoundedRectangle(cornerRadius: 10, style: .continuous)
                            .fill(palette.accentSoftFill)
                    )
                } else {
                    HStack(spacing: 10) {
                        trendMetricPill(
                            label: "最高",
                            value: YubraeTheme.masked(formatAccountDetailAmount(maxMicros, currencyCode: cur), isMasked: masked),
                            color: palette.textPrimary
                        )
                        trendMetricPill(
                            label: "最低",
                            value: YubraeTheme.masked(formatAccountDetailAmount(minMicros, currencyCode: cur), isMasked: masked),
                            color: palette.textPrimary
                        )
                        trendMetricPill(
                            label: "净变动",
                            value: YubraeTheme.masked(formatAccountDetailAmount(deltaMicros, currencyCode: cur, showSign: true), isMasked: masked),
                            color: deltaMicros >= 0 ? palette.incomeColor : palette.expenseColor
                        )
                    }
                }

                if points.isEmpty {
                    Text("暂无余额走势数据")
                        .font(.caption)
                        .foregroundColor(palette.textTertiary)
                        .frame(maxWidth: .infinity, alignment: .center)
                        .padding(.vertical, 32)
                } else {
                    balanceTrendChartView(points: points, yMin: yMin, yMax: yMax, masked: masked)

                    Text("左右按住滑动曲线可查看任意日期的收盘余额")
                        .font(.caption2)
                        .foregroundColor(palette.textTertiary)
                }
            }
        }
    }

    private func balanceTrendChartView(
        points: [AccountDailyBalancePoint],
        yMin: Double,
        yMax: Double,
        masked: Bool
    ) -> some View {
        Chart {
            ForEach(points) { point in
                let val = Double(point.balanceMicros) / Double(MoneyAmount.amountScale)
                AreaMark(
                    x: .value("日期", point.date),
                    yStart: .value("基线", yMin),
                    yEnd: .value("余额", val)
                )
                .interpolationMethod(.catmullRom)
                .foregroundStyle(
                    LinearGradient(
                        colors: [palette.primary.opacity(0.28), palette.primary.opacity(0.02)],
                        startPoint: .top,
                        endPoint: .bottom
                    )
                )

                LineMark(
                    x: .value("日期", point.date),
                    y: .value("余额", val)
                )
                .interpolationMethod(.catmullRom)
                .foregroundStyle(palette.primary)
                .lineStyle(StrokeStyle(lineWidth: 2.2, lineCap: .round, lineJoin: .round))
            }

            if let hovered = selectedTrendPoint {
                let hVal = Double(hovered.balanceMicros) / Double(MoneyAmount.amountScale)
                RuleMark(x: .value("选中日期", hovered.date))
                    .foregroundStyle(palette.primary.opacity(0.55))
                    .lineStyle(StrokeStyle(lineWidth: 1, dash: [4, 3]))

                PointMark(
                    x: .value("选中日期", hovered.date),
                    y: .value("选中余额", hVal)
                )
                .foregroundStyle(palette.primary)
                .symbolSize(48)
            }
        }
        .chartYScale(domain: yMin...yMax)
        .chartXAxis {
            AxisMarks(values: .automatic(desiredCount: 4)) { value in
                AxisGridLine(stroke: StrokeStyle(lineWidth: 0.4))
                    .foregroundStyle(palette.outline)
                AxisValueLabel {
                    if let date = value.as(Date.self) {
                        Text(formatShortMonthDay(date))
                            .font(.caption2.monospacedDigit())
                            .foregroundColor(palette.textTertiary)
                            .lineLimit(1)
                    }
                }
            }
        }
        .chartYAxis {
            AxisMarks(position: .leading, values: .automatic(desiredCount: 4)) { value in
                AxisGridLine(stroke: StrokeStyle(lineWidth: 0.4, dash: [3, 3]))
                    .foregroundStyle(palette.outline)
                AxisValueLabel {
                    if masked {
                        Text("••")
                            .font(.caption2)
                            .foregroundColor(palette.textTertiary)
                    } else if let dbl = value.as(Double.self) {
                        Text(compactAxisNumber(dbl))
                            .font(.caption2.monospacedDigit())
                            .foregroundColor(palette.textTertiary)
                    }
                }
            }
        }
        .chartOverlay { proxy in
            GeometryReader { geo in
                Rectangle()
                    .fill(Color.clear)
                    .contentShape(Rectangle())
                    .gesture(
                        DragGesture(minimumDistance: 0)
                            .onChanged { value in
                                let plotFrame = geo[proxy.plotAreaFrame]
                                let relativeX = value.location.x - plotFrame.origin.x
                                guard relativeX >= 0, relativeX <= plotFrame.width,
                                      let touchedDate: Date = proxy.value(atX: relativeX) else {
                                    return
                                }
                                if let nearest = points.min(by: {
                                    abs($0.date.timeIntervalSince(touchedDate)) < abs($1.date.timeIntervalSince(touchedDate))
                                }) {
                                    selectedTrendPoint = nearest
                                }
                            }
                            .onEnded { _ in
                                selectedTrendPoint = nil
                            }
                    )
            }
        }
        .frame(height: 176)
    }

    private func trendMetricPill(label: String, value: String, color: Color) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(label)
                .font(.caption2)
                .foregroundColor(palette.textTertiary)
                .lineLimit(1)
            Text(value)
                .font(.caption.weight(.bold).monospacedDigit())
                .foregroundColor(color)
                .lineLimit(1)
                .minimumScaleFactor(0.6)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 10)
        .padding(.vertical, 7)
        .background(
            RoundedRectangle(cornerRadius: 10, style: .continuous)
                .fill(palette.surfaceSecondary)
        )
    }

    // MARK: - 3. Credit Card Exclusive Panel (Billing Cycle + Installments)

    private func creditBillingCard(_ acc: AccountDto) -> some View {
        let masked = store.settings.privacyMaskEnabled
        let cur = CurrencyCatalog.normalize(acc.currencyCode)
        let cycle = store.computeCreditBillingCycleSummary(account: acc)
        let usedMicros = max(Int64(0), -acc.balanceMicros)
        let limitMicros = acc.creditLimitMicros
        let availMicros = max(Int64(0), limitMicros - usedMicros)
        let ratio = limitMicros > 0 ? min(1.0, max(0.0, Double(usedMicros) / Double(limitMicros))) : 0.0
        let barColor: Color = ratio >= 0.85 ? palette.dangerColor : (ratio >= 0.60 ? palette.warningColor : palette.primary)

        return YubraeCard(palette: palette) {
            VStack(alignment: .leading, spacing: 12) {
                HStack(spacing: 8) {
                    Label("信用卡账单周期", systemImage: "creditcard.fill")
                        .font(.subheadline.weight(.bold))
                        .foregroundColor(palette.textPrimary)
                        .lineLimit(1)
                    Spacer(minLength: 6)
                    Text("\(YubraeTheme.formatDate(cycle.cycleStartMillis, pattern: "M/d")) - \(YubraeTheme.formatDate(cycle.cycleEndMillis, pattern: "M/d"))")
                        .font(.caption2.weight(.semibold).monospacedDigit())
                        .foregroundColor(palette.textSecondary)
                        .lineLimit(1)
                        .fixedSize(horizontal: true, vertical: false)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 3)
                        .background(Capsule().fill(palette.surfaceSecondary))
                }

                // Countdown badges
                HStack(spacing: 8) {
                    if let billDay = acc.billingDay {
                        let dText = cycle.daysUntilBilling.map { $0 == 0 ? "今日出账" : "\($0)天后出账" } ?? "每月\(billDay)日出账"
                        creditCycleChip(text: "账单日 \(billDay)日 · \(dText)", tint: palette.primary)
                    } else {
                        creditCycleChip(text: "未设账单日（按自然月统计）", tint: palette.textSecondary)
                    }

                    if let repayDay = acc.repaymentDay {
                        let dRepay = cycle.daysUntilRepayment
                        let urgent = (dRepay ?? 99) <= 5
                        let rText = dRepay.map { $0 == 0 ? "今日还款" : "\($0)天后还款" } ?? "每月\(repayDay)日还款"
                        creditCycleChip(
                            text: "还款日 \(repayDay)日 · \(rText)",
                            tint: urgent ? palette.warningColor : palette.transferColor
                        )
                    }
                    Spacer(minLength: 0)
                }

                // 2x2 metrics grid
                LazyVGrid(columns: [GridItem(.flexible(), spacing: 10), GridItem(.flexible(), spacing: 10)], spacing: 10) {
                    creditMetricBox(
                        title: "本期消费",
                        value: YubraeTheme.masked(formatAccountDetailAmount(cycle.cycleSpendMicros, currencyCode: cur), isMasked: masked),
                        valueColor: palette.expenseColor
                    )
                    creditMetricBox(
                        title: "本期已还",
                        value: YubraeTheme.masked(formatAccountDetailAmount(cycle.cycleRepaidMicros, currencyCode: cur), isMasked: masked),
                        valueColor: palette.incomeColor
                    )
                    creditMetricBox(
                        title: "信用额度",
                        value: limitMicros > 0
                            ? YubraeTheme.masked(formatAccountDetailAmount(limitMicros, currencyCode: cur), isMasked: masked)
                            : "未设置",
                        valueColor: palette.textPrimary
                    )
                    creditMetricBox(
                        title: "可用额度",
                        value: limitMicros > 0
                            ? YubraeTheme.masked(formatAccountDetailAmount(availMicros, currencyCode: cur), isMasked: masked)
                            : "—",
                        valueColor: palette.primary
                    )
                }

                if limitMicros > 0 {
                    VStack(alignment: .leading, spacing: 5) {
                        HStack {
                            Text("额度使用率")
                                .font(.caption2)
                                .foregroundColor(palette.textSecondary)
                            Spacer()
                            Text(masked ? "••%" : String(format: "%.1f%%", ratio * 100))
                                .font(.caption2.weight(.bold).monospacedDigit())
                                .foregroundColor(barColor)
                                .lineLimit(1)
                                .fixedSize(horizontal: true, vertical: false)
                        }
                        GeometryReader { geo in
                            ZStack(alignment: .leading) {
                                Capsule().fill(palette.surfaceSecondary)
                                Capsule()
                                    .fill(barColor)
                                    .frame(width: max(ratio > 0 ? 5 : 0, geo.size.width * CGFloat(ratio)))
                            }
                        }
                        .frame(height: 7)
                    }
                }
            }
        }
    }

    private func creditCycleChip(text: String, tint: Color) -> some View {
        Text(text)
            .font(.system(size: 10.5, weight: .semibold))
            .foregroundColor(tint)
            .lineLimit(1)
            .minimumScaleFactor(0.8)
            .padding(.horizontal, 8)
            .padding(.vertical, 4)
            .background(Capsule().fill(tint.opacity(0.13)))
    }

    private func creditMetricBox(title: String, value: String, valueColor: Color) -> some View {
        VStack(alignment: .leading, spacing: 3) {
            Text(title)
                .font(.caption2)
                .foregroundColor(palette.textSecondary)
                .lineLimit(1)
            Text(value)
                .font(.subheadline.weight(.bold).monospacedDigit())
                .foregroundColor(valueColor)
                .lineLimit(1)
                .minimumScaleFactor(0.65)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 12)
        .padding(.vertical, 9)
        .background(
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .fill(palette.surfaceSecondary)
        )
    }

    private func installmentsCard(_ acc: AccountDto) -> some View {
        let masked = store.settings.privacyMaskEnabled
        let plans = store.installments
            .filter { $0.accountId == acc.id }
            .sorted { lhs, rhs in
                let lDone = lhs.completedPeriods >= lhs.totalPeriods
                let rDone = rhs.completedPeriods >= rhs.totalPeriods
                if lDone != rDone { return !lDone }
                return lhs.nextDueMillis < rhs.nextDueMillis
            }

        return YubraeCard(palette: palette) {
            VStack(alignment: .leading, spacing: 12) {
                HStack(spacing: 8) {
                    Label("分期计划（\(plans.count)）", systemImage: "calendar.badge.clock")
                        .font(.subheadline.weight(.bold))
                        .foregroundColor(palette.textPrimary)
                        .lineLimit(1)
                    Spacer(minLength: 8)
                    Button {
                        activeSheet = .addInstallment(acc)
                    } label: {
                        Label("新建分期", systemImage: "plus")
                            .font(.caption.weight(.semibold))
                            .foregroundColor(palette.primary)
                            .lineLimit(1)
                            .fixedSize(horizontal: true, vertical: false)
                    }
                    .buttonStyle(.plain)
                }

                if plans.isEmpty {
                    Text("暂无分期计划，点击右上角「+ 新建分期」可追踪每期本金与手续费并一键记账。")
                        .font(.caption)
                        .foregroundColor(palette.textTertiary)
                } else {
                    VStack(spacing: 10) {
                        ForEach(plans) { plan in
                            installmentPlanRow(plan, masked: masked)
                            if plan.id != plans.last?.id {
                                Divider()
                            }
                        }
                    }
                }
            }
        }
    }

    private func installmentPlanRow(_ plan: InstallmentPlanDto, masked: Bool) -> some View {
        let isCompleted = plan.completedPeriods >= plan.totalPeriods
        let periods = Int64(max(1, plan.totalPeriods))
        let perPeriod = (plan.totalAmountMicros / periods) + plan.feePerPeriodMicros
        let ratio = min(1.0, max(0.0, Double(plan.completedPeriods) / Double(max(1, plan.totalPeriods))))

        return VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 8) {
                VStack(alignment: .leading, spacing: 2) {
                    HStack(spacing: 6) {
                        Text(plan.name)
                            .font(.subheadline.weight(.semibold))
                            .foregroundColor(palette.textPrimary)
                            .lineLimit(1)
                        Text(isCompleted ? "已还清" : "\(plan.completedPeriods)/\(plan.totalPeriods)期")
                            .font(.system(size: 10, weight: .bold).monospacedDigit())
                            .foregroundColor(isCompleted ? palette.incomeColor : palette.primary)
                            .lineLimit(1)
                            .fixedSize(horizontal: true, vertical: false)
                            .padding(.horizontal, 6)
                            .padding(.vertical, 2)
                            .background(Capsule().fill((isCompleted ? palette.incomeColor : palette.primary).opacity(0.14)))
                    }
                    let totalStr = YubraeTheme.masked(formatAccountDetailAmount(plan.totalAmountMicros, currencyCode: plan.currencyCode), isMasked: masked)
                    let perStr = YubraeTheme.masked(formatAccountDetailAmount(perPeriod, currencyCode: plan.currencyCode), isMasked: masked)
                    Text("总额 \(totalStr) · 每期 \(perStr)")
                        .font(.caption2.monospacedDigit())
                        .foregroundColor(palette.textSecondary)
                        .lineLimit(1)
                }
                Spacer(minLength: 6)
                if !isCompleted {
                    Text("下期 \(YubraeTheme.formatDate(plan.nextDueMillis, pattern: "MM/dd"))")
                        .font(.caption2.monospacedDigit())
                        .foregroundColor(palette.textTertiary)
                        .lineLimit(1)
                        .fixedSize(horizontal: true, vertical: false)
                }
            }

            GeometryReader { geo in
                ZStack(alignment: .leading) {
                    Capsule().fill(palette.surfaceSecondary)
                    Capsule()
                        .fill(isCompleted ? palette.incomeColor : palette.primary)
                        .frame(width: max(ratio > 0 ? 4 : 0, geo.size.width * CGFloat(ratio)))
                }
            }
            .frame(height: 5)

            HStack(spacing: 10) {
                if plan.feePerPeriodMicros > 0 {
                    let feeStr = YubraeTheme.masked(formatAccountDetailAmount(plan.feePerPeriodMicros, currencyCode: plan.currencyCode), isMasked: masked)
                    Text("含每期手续费 \(feeStr)")
                        .font(.caption2)
                        .foregroundColor(palette.textTertiary)
                        .lineLimit(1)
                }
                Spacer(minLength: 6)
                if !isCompleted {
                    Button {
                        do {
                            let nextNum = plan.completedPeriods + 1
                            _ = try store.recordInstallmentPeriod(planId: plan.id)
                            store.toastMessage = "已记入「\(plan.name)」第 \(nextNum)/\(plan.totalPeriods) 期"
                        } catch {
                            store.lastErrorMessage = error.localizedDescription
                        }
                    } label: {
                        Text("记入本期")
                            .font(.caption.weight(.semibold))
                            .foregroundColor(palette.onPrimary)
                            .lineLimit(1)
                            .fixedSize(horizontal: true, vertical: false)
                            .padding(.horizontal, 10)
                            .padding(.vertical, 5)
                            .background(Capsule().fill(palette.primary))
                    }
                    .buttonStyle(.plain)
                }

                Button(role: .destructive) {
                    pendingConfirmAction = .deleteInstallment(plan)
                } label: {
                    Text("删除")
                        .font(.caption.weight(.semibold))
                        .foregroundColor(palette.dangerColor)
                        .lineLimit(1)
                        .fixedSize(horizontal: true, vertical: false)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 5)
                        .background(Capsule().fill(palette.dangerColor.opacity(0.12)))
                }
                .buttonStyle(.plain)
            }
        }
    }

    // MARK: - 4. Historical Transactions Workbench

    private func historyWorkbenchSection(_ acc: AccountDto) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 8) {
                Label("历史账单（\(filteredTransactions.count)）", systemImage: "list.bullet.rectangle")
                    .font(.subheadline.weight(.bold))
                    .foregroundColor(palette.textPrimary)
                    .lineLimit(1)
                Spacer(minLength: 8)
                Button {
                    withAnimation(.easeInOut(duration: 0.2)) {
                        if isSelectionMode {
                            exitSelectionMode()
                        } else {
                            isSelectionMode = true
                            selectedTxIds.removeAll()
                        }
                    }
                } label: {
                    Text(isSelectionMode ? "完成" : "多选")
                        .font(.caption.weight(.semibold))
                        .foregroundColor(isSelectionMode ? palette.onPrimary : palette.primary)
                        .lineLimit(1)
                        .fixedSize(horizontal: true, vertical: false)
                        .padding(.horizontal, 11)
                        .padding(.vertical, 5)
                        .background(Capsule().fill(isSelectionMode ? palette.primary : palette.accentSoftFill))
                }
                .buttonStyle(.plain)
            }
            .padding(.horizontal, 2)

            // Period & Filter Card
            YubraeCard(palette: palette, padding: 12) {
                VStack(spacing: 10) {
                    Picker("时间维度", selection: $rangeMode) {
                        ForEach(AccountHistoryRangeMode.allCases) { mode in
                            Text(mode.rawValue).tag(mode)
                        }
                    }
                    .pickerStyle(.segmented)

                    periodNavigatorRow

                    HStack(spacing: 8) {
                        Image(systemName: "magnifyingglass")
                            .font(.subheadline)
                            .foregroundColor(palette.textTertiary)
                        TextField("搜索商户、备注、分类、标签或金额", text: $txSearchQuery)
                            .font(.subheadline)
                        if !txSearchQuery.isEmpty {
                            Button {
                                txSearchQuery = ""
                            } label: {
                                Image(systemName: "xmark.circle.fill")
                                    .foregroundColor(palette.textTertiary)
                            }
                            .buttonStyle(.plain)
                        }
                    }
                    .padding(.horizontal, 10)
                    .padding(.vertical, 8)
                    .background(
                        RoundedRectangle(cornerRadius: 10, style: .continuous)
                            .fill(palette.surfaceSecondary)
                    )

                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 7) {
                            ForEach(AccountTxTypeFilter.allCases) { filter in
                                let selected = txTypeFilter == filter
                                Button {
                                    txTypeFilter = filter
                                } label: {
                                    Text(filter.rawValue)
                                        .font(.caption.weight(.semibold))
                                        .lineLimit(1)
                                        .fixedSize(horizontal: true, vertical: false)
                                        .foregroundColor(selected ? palette.onPrimary : palette.textSecondary)
                                        .padding(.horizontal, 11)
                                        .padding(.vertical, 5)
                                        .background(Capsule().fill(selected ? palette.primary : palette.surfaceSecondary))
                                }
                                .buttonStyle(.plain)
                            }
                        }
                    }
                }
            }

            if groupedByDay.isEmpty {
                YubraeEmptyStateView(
                    icon: "tray",
                    title: "暂无符合条件的流水",
                    subtitle: rangeMode == .all
                        ? "点击上方「记一笔」或「转账」即可为该账户新增账单。"
                        : "当前筛选时间或条件下没有记录，可切换时间范围或清空搜索。",
                    palette: palette,
                    actionTitle: rangeMode == .all ? "立即记一笔" : "查看全部历史",
                    action: {
                        if rangeMode == .all {
                            quickRecordContext = DetailQuickRecordContext(
                                editingTransaction: nil,
                                initialType: TransactionType.EXPENSE,
                                initialAccountId: acc.id,
                                initialToAccountId: nil,
                                initialAmountMicros: nil
                            )
                        } else {
                            rangeMode = .all
                            txSearchQuery = ""
                            txTypeFilter = .all
                        }
                    }
                )
            } else {
                let refundedByOrigId = store.computeRefundedAmountsByOrigTxId()
                let categoryById = Dictionary(store.categories.map { ($0.id, $0) }, uniquingKeysWith: { first, _ in first })
                let accountById = Dictionary(store.accounts.map { ($0.id, $0) }, uniquingKeysWith: { first, _ in first })
                ForEach(groupedByDay) { group in
                    daySectionCard(
                        group: group,
                        account: acc,
                        refundedByOrigId: refundedByOrigId,
                        categoryById: categoryById,
                        accountById: accountById
                    )
                }
            }
        }
    }

    @ViewBuilder
    private var periodNavigatorRow: some View {
        let nowYM = currentYearMonth
        switch rangeMode {
        case .monthly:
            let isCurrentMonth = selectedYear == nowYM.year && selectedMonth == nowYM.month
            HStack(spacing: 8) {
                Button { shiftMonth(by: -1) } label: {
                    Image(systemName: "chevron.left.circle.fill")
                        .font(.title3)
                        .foregroundColor(palette.primary)
                }
                .buttonStyle(.plain)

                Spacer(minLength: 4)

                Button {
                    activeSheet = .pickMonth
                } label: {
                    HStack(spacing: 4) {
                        Text("\(String(selectedYear))年\(selectedMonth)月")
                            .font(.subheadline.weight(.bold).monospacedDigit())
                            .lineLimit(1)
                            .fixedSize(horizontal: true, vertical: false)
                        Image(systemName: "chevron.down")
                            .font(.caption2.weight(.bold))
                    }
                    .foregroundColor(palette.textPrimary)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 5)
                    .background(Capsule().fill(palette.surfaceSecondary))
                }
                .buttonStyle(.plain)

                if !isCurrentMonth {
                    Button {
                        selectedYear = nowYM.year
                        selectedMonth = nowYM.month
                    } label: {
                        Text("回本月")
                            .font(.caption2.weight(.semibold))
                            .foregroundColor(palette.primary)
                            .lineLimit(1)
                            .fixedSize(horizontal: true, vertical: false)
                            .padding(.horizontal, 8)
                            .padding(.vertical, 5)
                            .background(Capsule().fill(palette.accentSoftFill))
                    }
                    .buttonStyle(.plain)
                }

                Spacer(minLength: 4)

                Button { shiftMonth(by: 1) } label: {
                    Image(systemName: "chevron.right.circle.fill")
                        .font(.title3)
                        .foregroundColor(palette.primary)
                }
                .buttonStyle(.plain)
            }

        case .yearly:
            let isCurrentYear = selectedYear == nowYM.year
            HStack(spacing: 8) {
                Button { selectedYear = max(1, selectedYear - 1) } label: {
                    Image(systemName: "chevron.left.circle.fill")
                        .font(.title3)
                        .foregroundColor(palette.primary)
                }
                .buttonStyle(.plain)

                Spacer(minLength: 4)

                Button {
                    activeSheet = .pickYear
                } label: {
                    HStack(spacing: 4) {
                        Text("\(String(selectedYear))年")
                            .font(.subheadline.weight(.bold).monospacedDigit())
                            .lineLimit(1)
                            .fixedSize(horizontal: true, vertical: false)
                        Image(systemName: "chevron.down")
                            .font(.caption2.weight(.bold))
                    }
                    .foregroundColor(palette.textPrimary)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 5)
                    .background(Capsule().fill(palette.surfaceSecondary))
                }
                .buttonStyle(.plain)

                if !isCurrentYear {
                    Button {
                        selectedYear = nowYM.year
                    } label: {
                        Text("回今年")
                            .font(.caption2.weight(.semibold))
                            .foregroundColor(palette.primary)
                            .lineLimit(1)
                            .fixedSize(horizontal: true, vertical: false)
                            .padding(.horizontal, 8)
                            .padding(.vertical, 5)
                            .background(Capsule().fill(palette.accentSoftFill))
                    }
                    .buttonStyle(.plain)
                }

                Spacer(minLength: 4)

                Button { selectedYear += 1 } label: {
                    Image(systemName: "chevron.right.circle.fill")
                        .font(.title3)
                        .foregroundColor(palette.primary)
                }
                .buttonStyle(.plain)
            }

        case .all:
            HStack {
                Text("全部历史流水（共 \(allAccountTransactions.count) 笔）")
                    .font(.caption)
                    .foregroundColor(palette.textSecondary)
                Spacer()
            }
            .padding(.horizontal, 4)
        }
    }

    private func daySectionCard(
        group: AccountDayGroup,
        account acc: AccountDto,
        refundedByOrigId: [Int64: Int64],
        categoryById: [Int64: CategoryDto],
        accountById: [Int64: AccountDto]
    ) -> some View {
        let cur = CurrencyCatalog.normalize(acc.currencyCode)
        let baseCur = CurrencyCatalog.normalize(store.settings.baseCurrency)
        let masked = store.settings.privacyMaskEnabled

        return VStack(alignment: .leading, spacing: 6) {
            HStack(alignment: .firstTextBaseline, spacing: 6) {
                Text(group.dateText)
                    .font(.footnote.weight(.semibold))
                    .foregroundColor(palette.textPrimary)
                    .lineLimit(1)
                    .fixedSize(horizontal: true, vertical: false)
                if !group.subText.isEmpty {
                    Text(group.subText)
                        .font(.caption)
                        .foregroundColor(palette.textSecondary)
                        .lineLimit(1)
                        .fixedSize(horizontal: true, vertical: false)
                }
                Spacer(minLength: 6)
                HStack(spacing: 10) {
                    if group.inflowMicros > 0 {
                        let inStr = YubraeTheme.masked(
                            YubraeTheme.formatPlainSignedAmount(
                                group.inflowMicros,
                                currencyCode: cur,
                                baseCurrency: baseCur,
                                showSign: false
                            ),
                            isMasked: masked
                        )
                        Text("入 \(inStr)")
                            .font(.caption.monospacedDigit())
                            .foregroundColor(palette.textSecondary)
                            .lineLimit(1)
                            .fixedSize(horizontal: true, vertical: false)
                    }
                    if group.outflowMicros > 0 {
                        let outStr = YubraeTheme.masked(
                            YubraeTheme.formatPlainSignedAmount(
                                group.outflowMicros,
                                currencyCode: cur,
                                baseCurrency: baseCur,
                                showSign: false
                            ),
                            isMasked: masked
                        )
                        Text("出 \(outStr)")
                            .font(.caption.monospacedDigit())
                            .foregroundColor(palette.textSecondary)
                            .lineLimit(1)
                            .fixedSize(horizontal: true, vertical: false)
                    }
                }
            }
            .padding(.horizontal, 4)

            YubraeCard(palette: palette, padding: 0, cornerRadius: 14) {
                VStack(spacing: 0) {
                    ForEach(group.items) { tx in
                        accountTransactionRow(
                            tx,
                            account: acc,
                            refundedMicros: refundedByOrigId[tx.id] ?? 0,
                            categoryById: categoryById,
                            accountById: accountById
                        )
                        if tx.id != group.items.last?.id {
                            Rectangle()
                                .fill(palette.outline.opacity(0.75))
                                .frame(height: 0.5)
                                .padding(.leading, isSelectionMode ? 90 : 58)
                        }
                    }
                }
            }
        }
    }

    private func accountTransactionRow(
        _ tx: TransactionDto,
        account acc: AccountDto,
        refundedMicros: Int64,
        categoryById: [Int64: CategoryDto],
        accountById: [Int64: AccountDto]
    ) -> some View {
        let cat = tx.categoryId.flatMap { categoryById[$0] }
        let parentCat = cat?.parentId.flatMap { categoryById[$0] }
        let fromAcc = accountById[tx.accountId]
        let toAcc = tx.toAccountId.flatMap { accountById[$0] }
        let masked = store.settings.privacyMaskEnabled
        let cur = CurrencyCatalog.normalize(acc.currencyCode)
        let baseCur = CurrencyCatalog.normalize(store.settings.baseCurrency)
        let delta = store.accountDeltaForTransaction(tx, accountId: acc.id)
        let isSelected = selectedTxIds.contains(tx.id)

        let iconKey: String
        let iconColor: Int64
        let primaryTitle: String

        switch tx.type {
        case TransactionType.TRANSFER:
            iconKey = "currency_exchange"
            iconColor = 0xFF2563EB
            let isCredit = acc.groupType.uppercased() == AccountGroup.CREDIT
            if tx.accountId == acc.id {
                primaryTitle = "→ \(toAcc?.name ?? "目标账户")"
            } else if tx.toAccountId == acc.id {
                primaryTitle = isCredit
                    ? "← \(fromAcc?.name ?? "付款账户") 还款"
                    : "← \(fromAcc?.name ?? "源账户")"
            } else {
                primaryTitle = "\(fromAcc?.name ?? "账户") → \(toAcc?.name ?? "目标账户")"
            }
        case TransactionType.ADJUST:
            iconKey = "calculate"
            iconColor = 0xFF64748B
            primaryTitle = "余额校准"
        default:
            iconKey = cat?.icon ?? "category"
            iconColor = cat?.colorHex ?? 0xFF2AA876
            if let cat {
                if let parent = parentCat, !parent.name.isEmpty, parent.name != cat.name {
                    primaryTitle = "\(parent.name)-\(cat.name)"
                } else {
                    primaryTitle = cat.name
                }
            } else {
                primaryTitle = "未分类"
            }
        }

        var rowBadges: [AccountRowMicroBadge] = []
        if tx.status == TransactionStatus.PENDING {
            rowBadges.append(AccountRowMicroBadge(id: "pending", text: "待确认", color: palette.warningColor))
        }
        if tx.type == TransactionType.EXPENSE && refundedMicros > 0 {
            let refStr = YubraeTheme.masked(
                YubraeTheme.formatPlainSignedAmount(
                    refundedMicros,
                    currencyCode: tx.origCurrency,
                    baseCurrency: baseCur,
                    showSign: false
                ),
                isMasked: masked
            )
            rowBadges.append(AccountRowMicroBadge(id: "refunded", text: "退\(MoneyAmount.symbolGap)\(refStr)", color: palette.incomeColor))
        }
        if tx.type == TransactionType.INCOME && tx.refundOfTxId != nil {
            rowBadges.append(AccountRowMicroBadge(id: "refund", text: "退款", color: palette.incomeColor))
        }
        if tx.reimburseStatus == ReimburseStatus.PENDING {
            rowBadges.append(AccountRowMicroBadge(id: "reimburse", text: "待报销", color: palette.transferColor))
        }
        if tx.discountAmountMicros > 0 {
            let discStr = YubraeTheme.masked(
                YubraeTheme.formatPlainSignedAmount(
                    tx.discountAmountMicros,
                    currencyCode: tx.origCurrency,
                    baseCurrency: baseCur,
                    showSign: false
                ),
                isMasked: masked
            )
            rowBadges.append(AccountRowMicroBadge(id: "discount", text: "惠\(MoneyAmount.symbolGap)\(discStr)", color: palette.incomeColor))
        }
        if tx.feeAmountMicros > 0 {
            let feeStr = YubraeTheme.masked(
                YubraeTheme.formatPlainSignedAmount(
                    tx.feeAmountMicros,
                    currencyCode: tx.origCurrency,
                    baseCurrency: baseCur,
                    showSign: false
                ),
                isMasked: masked
            )
            rowBadges.append(AccountRowMicroBadge(id: "fee", text: "费\(MoneyAmount.symbolGap)\(feeStr)", color: palette.warningColor))
        }
        let visibleBadges = Array(rowBadges.prefix(2))

        let hasAttachments = tx.attachmentPaths
            .split { $0 == ";" || $0 == "," || $0 == "\n" }
            .contains { !$0.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }
        let inlineTags = Array(TagTextCodec.parse(tx.tags).prefix(2))

        let line2MetaText = YubraeTheme.buildListRowMetaText(
            merchant: tx.merchant,
            rawNote: tx.note,
            excludedNames: [
                primaryTitle,
                cat?.name,
                parentCat?.name,
                fromAcc?.name,
                toAcc?.name,
                acc.name
            ]
        )

        let deltaColor: Color
        if delta > 0 {
            deltaColor = palette.incomeColor
        } else if delta < 0 {
            deltaColor = palette.expenseColor
        } else {
            deltaColor = palette.textSecondary
        }

        let deltaFormatted = YubraeTheme.formatPlainSignedAmount(
            delta,
            currencyCode: cur,
            baseCurrency: baseCur,
            showSign: true
        )

        let secondaryAmountText: String? = {
            let hasDiscountOrFee = tx.discountAmountMicros > 0 || tx.feeAmountMicros > 0
            if tx.type == TransactionType.TRANSFER && tx.accountId == acc.id,
               let toAmt = tx.toAmountMicros {
                let toCur = CurrencyCatalog.normalize(tx.toCurrency ?? tx.origCurrency)
                if hasDiscountOrFee || toCur != cur || toAmt != tx.accountAmountMicros {
                    let toFmt = YubraeTheme.formatPlainSignedAmount(toAmt, currencyCode: toCur, baseCurrency: baseCur, showSign: false)
                    return "到账 \(YubraeTheme.masked(toFmt, isMasked: masked))"
                }
            } else if tx.type == TransactionType.TRANSFER && tx.toAccountId == acc.id {
                let fromCur = CurrencyCatalog.normalize(tx.accountCurrency.isEmpty ? tx.origCurrency : tx.accountCurrency)
                let toAmt = tx.toAmountMicros ?? tx.accountAmountMicros
                if hasDiscountOrFee || fromCur != cur || tx.accountAmountMicros != toAmt {
                    let outFmt = YubraeTheme.formatPlainSignedAmount(tx.accountAmountMicros, currencyCode: fromCur, baseCurrency: baseCur, showSign: false)
                    return "实扣 \(YubraeTheme.masked(outFmt, isMasked: masked))"
                }
            } else if CurrencyCatalog.normalize(tx.origCurrency) != cur {
                let origFmt = YubraeTheme.formatPlainSignedAmount(tx.origAmountMicros, currencyCode: tx.origCurrency, baseCurrency: baseCur, showSign: false)
                return "原币 \(YubraeTheme.masked(origFmt, isMasked: masked))"
            }
            return nil
        }()

        return HStack(alignment: .center, spacing: 10) {
            if isSelectionMode {
                Image(systemName: isSelected ? "checkmark.circle.fill" : "circle")
                    .font(.system(size: 20))
                    .foregroundColor(isSelected ? palette.primary : palette.textTertiary)
                    .frame(width: 22)
            }

            YubraeCategoryIconBadge(iconKey: iconKey, colorHex: iconColor, size: 34)

            // 严格 2 行中列
            VStack(alignment: .leading, spacing: 4) {
                HStack(spacing: 5) {
                    Text(primaryTitle)
                        .font(.subheadline.weight(.semibold))
                        .foregroundColor(palette.textPrimary)
                        .lineLimit(1)
                        .truncationMode(.tail)
                    ForEach(visibleBadges) { badge in
                        smallBadge(badge.text, color: badge.color)
                    }
                }

                HStack(spacing: 5) {
                    Text(YubraeTheme.formatDate(tx.timestamp, pattern: "HH:mm"))
                        .font(.caption2.monospacedDigit())
                        .foregroundColor(palette.textTertiary)
                        .lineLimit(1)
                        .fixedSize(horizontal: true, vertical: false)

                    if hasAttachments {
                        Image(systemName: "paperclip")
                            .font(.system(size: 10, weight: .medium))
                            .foregroundColor(palette.textTertiary)
                    }

                    ForEach(inlineTags, id: \.self) { tag in
                        Text("#\(tag)")
                            .font(.system(size: 10, weight: .medium))
                            .foregroundColor(palette.primary)
                            .lineLimit(1)
                            .minimumScaleFactor(0.85)
                            .padding(.horizontal, 5)
                            .padding(.vertical, 1)
                            .background(
                                RoundedRectangle(cornerRadius: 4, style: .continuous)
                                    .fill(palette.primary.opacity(palette.isDark ? 0.16 : 0.09))
                            )
                    }

                    if !line2MetaText.isEmpty {
                        Text(line2MetaText)
                            .font(.caption2)
                            .foregroundColor(palette.textSecondary)
                            .lineLimit(1)
                            .truncationMode(.tail)
                    }
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)

            HStack(spacing: 6) {
                VStack(alignment: .trailing, spacing: 3) {
                    Text(YubraeTheme.masked(deltaFormatted, isMasked: masked))
                        .font(.subheadline.weight(.semibold).monospacedDigit())
                        .foregroundColor(deltaColor)
                        .lineLimit(1)
                        .fixedSize(horizontal: true, vertical: false)
                    if let subAmt = secondaryAmountText {
                        Text(subAmt)
                            .font(.caption2.monospacedDigit())
                            .foregroundColor(palette.textTertiary)
                            .lineLimit(1)
                            .fixedSize(horizontal: true, vertical: false)
                    }
                }

                if !isSelectionMode {
                    Menu {
                        txActionMenuItems(tx)
                    } label: {
                        Image(systemName: "ellipsis")
                            .font(.caption.weight(.semibold))
                            .foregroundColor(palette.textTertiary)
                            .frame(width: 24, height: 28)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                }
            }
            .layoutPriority(1)
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 11)
        .background(isSelectionMode && isSelected ? palette.accentSoftFill.opacity(0.45) : Color.clear)
        .contentShape(Rectangle())
        .onTapGesture {
            if isSelectionMode {
                toggleSelection(tx.id)
            } else {
                openEditTransaction(tx)
            }
        }
        .contextMenu {
            txActionMenuItems(tx)
        }
    }

    @ViewBuilder
    private func txActionMenuItems(_ tx: TransactionDto) -> some View {
        Button {
            openEditTransaction(tx)
        } label: {
            Label("编辑账单", systemImage: "pencil")
        }
        Button {
            do {
                _ = try store.duplicateTransaction(id: tx.id)
                store.toastMessage = "已复制并再记一笔"
            } catch {
                store.lastErrorMessage = error.localizedDescription
            }
        } label: {
            Label("再记一笔", systemImage: "doc.on.doc")
        }
        Button(role: .destructive) {
            pendingConfirmAction = .deleteTransaction(tx)
        } label: {
            Label("删除账单", systemImage: "trash")
        }
    }

    // MARK: - Multi-Select Batch Action Bottom Bar

    private func batchActionBottomBar(_ acc: AccountDto) -> some View {
        let allFilteredIds = Set(filteredTransactions.map(\.id))
        let allSelected = !allFilteredIds.isEmpty && allFilteredIds.isSubset(of: selectedTxIds)
        let hasSelection = !selectedTxIds.isEmpty

        return VStack(spacing: 10) {
            Divider().overlay(palette.outline)

            HStack(spacing: 8) {
                Text("已选 \(selectedTxIds.count) 笔")
                    .font(.caption.weight(.bold).monospacedDigit())
                    .foregroundColor(palette.textPrimary)
                    .lineLimit(1)
                    .fixedSize(horizontal: true, vertical: false)

                Spacer(minLength: 6)

                Button {
                    if allSelected {
                        selectedTxIds.removeAll()
                    } else {
                        selectedTxIds = allFilteredIds
                    }
                } label: {
                    Text(allSelected ? "取消全选" : "全选 (\(allFilteredIds.count))")
                        .font(.caption.weight(.semibold))
                        .foregroundColor(palette.primary)
                        .lineLimit(1)
                        .fixedSize(horizontal: true, vertical: false)
                }
                .buttonStyle(.plain)
                .disabled(allFilteredIds.isEmpty)

                Button("退出") {
                    exitSelectionMode()
                }
                .font(.caption.weight(.semibold))
                .foregroundColor(palette.textSecondary)
                .lineLimit(1)
                .fixedSize(horizontal: true, vertical: false)
                .buttonStyle(.plain)
            }

            HStack(spacing: 10) {
                Button {
                    activeSheet = .batchCategory
                } label: {
                    Label("改分类", systemImage: "folder")
                        .font(.caption.weight(.semibold))
                        .lineLimit(1)
                        .fixedSize(horizontal: true, vertical: false)
                        .foregroundColor(hasSelection ? palette.textPrimary : palette.textTertiary)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 10)
                        .background(RoundedRectangle(cornerRadius: 11, style: .continuous).fill(palette.surfaceSecondary))
                }
                .buttonStyle(.plain)
                .disabled(!hasSelection)

                Button {
                    activeSheet = .batchAccount
                } label: {
                    Label("改账户", systemImage: "arrow.left.arrow.right")
                        .font(.caption.weight(.semibold))
                        .lineLimit(1)
                        .fixedSize(horizontal: true, vertical: false)
                        .foregroundColor(hasSelection ? palette.primary : palette.textTertiary)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 10)
                        .background(RoundedRectangle(cornerRadius: 11, style: .continuous).fill(hasSelection ? palette.accentSoftFill : palette.surfaceSecondary))
                }
                .buttonStyle(.plain)
                .disabled(!hasSelection)

                Button {
                    pendingConfirmAction = .batchDelete(Array(selectedTxIds))
                } label: {
                    Label("删除", systemImage: "trash")
                        .font(.caption.weight(.semibold))
                        .lineLimit(1)
                        .fixedSize(horizontal: true, vertical: false)
                        .foregroundColor(hasSelection ? .white : palette.textTertiary)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 10)
                        .background(
                            RoundedRectangle(cornerRadius: 11, style: .continuous)
                                .fill(hasSelection ? palette.dangerColor : palette.surfaceSecondary)
                        )
                }
                .buttonStyle(.plain)
                .disabled(!hasSelection)
            }
        }
        .padding(.horizontal, 16)
        .padding(.top, 2)
        .padding(.bottom, 12)
        .background(palette.surface)
    }

    // MARK: - Helpers

    private func smallBadge(_ text: String, color: Color) -> some View {
        Text(text)
            .font(.system(size: 10, weight: .medium).monospacedDigit())
            .foregroundColor(color)
            .lineLimit(1)
            .fixedSize(horizontal: true, vertical: false)
            .padding(.horizontal, 5)
            .padding(.vertical, 1.5)
            .background(
                RoundedRectangle(cornerRadius: 4, style: .continuous)
                    .fill(color.opacity(palette.isDark ? 0.18 : 0.11))
            )
    }

    private func openEditTransaction(_ tx: TransactionDto) {
        quickRecordContext = DetailQuickRecordContext(
            editingTransaction: tx,
            initialType: tx.type == TransactionType.ADJUST ? TransactionType.EXPENSE : tx.type,
            initialAccountId: tx.accountId,
            initialToAccountId: tx.toAccountId,
            initialAmountMicros: nil
        )
    }

    private func toggleSelection(_ id: Int64) {
        if selectedTxIds.contains(id) {
            selectedTxIds.remove(id)
        } else {
            selectedTxIds.insert(id)
        }
    }

    private func exitSelectionMode() {
        isSelectionMode = false
        selectedTxIds.removeAll()
    }

    private func executePendingConfirmAction() {
        guard let action = pendingConfirmAction else { return }
        pendingConfirmAction = nil
        switch action {
        case .deleteTransaction(let tx):
            do {
                try store.deleteTransaction(id: tx.id)
                selectedTxIds.remove(tx.id)
                store.toastMessage = "已删除该笔账单"
            } catch {
                store.lastErrorMessage = error.localizedDescription
            }
        case .batchDelete(let ids):
            let deleted = store.batchDeleteTransactions(ids: ids)
            store.toastMessage = "已批量删除 \(deleted) 笔账单"
            exitSelectionMode()
        case .deleteInstallment(let plan):
            store.deleteInstallment(id: plan.id)
            store.toastMessage = "已删除分期计划「\(plan.name)」"
        }
    }

    private func shiftMonth(by delta: Int) {
        let cal = Calendar.current
        if let base = cal.date(from: DateComponents(year: selectedYear, month: selectedMonth, day: 1)),
           let next = cal.date(byAdding: .month, value: delta, to: base) {
            let comps = cal.dateComponents([.year, .month], from: next)
            selectedYear = comps.year ?? selectedYear
            selectedMonth = comps.month ?? selectedMonth
        }
    }

    private func formatChartDate(_ date: Date) -> String {
        let df = DateFormatter()
        df.locale = Locale(identifier: "zh_CN")
        df.dateFormat = "yyyy-MM-dd"
        return df.string(from: date)
    }

    private func formatShortMonthDay(_ date: Date) -> String {
        let df = DateFormatter()
        df.locale = Locale(identifier: "zh_CN")
        df.dateFormat = "M/d"
        return df.string(from: date)
    }

    private func compactAxisNumber(_ value: Double) -> String {
        let absVal = abs(value)
        if absVal >= 100_000_000 {
            return String(format: "%.1f亿", value / 100_000_000)
        }
        if absVal >= 10_000 {
            return String(format: "%.1fw", value / 10_000)
        }
        if absVal >= 1_000 {
            return String(format: "%.1fk", value / 1_000)
        }
        return String(format: "%.0f", value)
    }
}

// MARK: - New Credit Card Installment Plan Sheet

private struct InstallmentEditorSheet: View {
    @ObservedObject var store: LedgerStore
    let account: AccountDto

    @Environment(\.dismiss) private var dismiss
    @State private var name: String = ""
    @State private var totalAmountText: String = ""
    @State private var feePerPeriodText: String = ""
    @State private var totalPeriods: Int = 12
    @State private var completedPeriods: Int = 0
    @State private var billingDay: Int
    @State private var nextDueDate: Date = Date()
    @State private var selectedCategoryId: Int64 = 0

    private let periodPresets = [3, 6, 12, 24]

    init(store: LedgerStore, account: AccountDto) {
        self.store = store
        self.account = account
        _billingDay = State(initialValue: min(31, max(1, account.billingDay ?? 5)))
    }

    private var expenseCategories: [CategoryDto] {
        store.categories
            .filter { $0.type.caseInsensitiveCompare(TransactionType.EXPENSE) == .orderedSame }
            .sorted { lhs, rhs in
                if lhs.sortOrder != rhs.sortOrder { return lhs.sortOrder < rhs.sortOrder }
                return lhs.id < rhs.id
            }
    }

    private var perPeriodEstimateMicros: Int64? {
        guard let total = MoneyAmount.parseToMicros(totalAmountText), total > 0 else { return nil }
        let fee = MoneyAmount.parseToMicros(feePerPeriodText) ?? 0
        return (total / Int64(max(1, totalPeriods))) + max(0, fee)
    }

    private var canSave: Bool {
        !name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            && (MoneyAmount.parseToMicros(totalAmountText) ?? 0) > 0
            && totalPeriods >= 1
    }

    var body: some View {
        NavigationStack {
            Form {
                Section("分期商品与金额") {
                    TextField("分期名称（如 MacBook Pro 12期）", text: $name)
                    HStack {
                        Text("分期本金总额")
                        Spacer()
                        TextField("0.00", text: $totalAmountText)
                            .keyboardType(.decimalPad)
                            .multilineTextAlignment(.trailing)
                    }
                    HStack {
                        Text("每期手续费（可选）")
                        Spacer()
                        TextField("0.00", text: $feePerPeriodText)
                            .keyboardType(.decimalPad)
                            .multilineTextAlignment(.trailing)
                    }
                    if let est = perPeriodEstimateMicros {
                        HStack {
                            Text("预估每期应还")
                            Spacer()
                            Text(formatAccountDetailAmount(est, currencyCode: account.currencyCode, showCurrencyCode: true))
                                .fontWeight(.semibold)
                                .monospacedDigit()
                                .foregroundColor(.accentColor)
                        }
                    }
                }

                Section("期数与账单日") {
                    HStack(spacing: 6) {
                        Text("常用期数")
                            .font(.subheadline)
                            .foregroundColor(.secondary)
                            .lineLimit(1)
                            .minimumScaleFactor(0.85)
                        Spacer(minLength: 4)
                        ForEach(periodPresets, id: \.self) { count in
                            Button("\(count)期") {
                                totalPeriods = count
                                if completedPeriods >= totalPeriods {
                                    completedPeriods = max(0, totalPeriods - 1)
                                }
                            }
                            .buttonStyle(.borderless)
                            .font(.caption.weight(.semibold))
                            .lineLimit(1)
                            .fixedSize(horizontal: true, vertical: false)
                            .padding(.horizontal, 9)
                            .padding(.vertical, 5)
                            .background(Capsule().fill(totalPeriods == count ? Color.accentColor.opacity(0.18) : Color.secondary.opacity(0.12)))
                        }
                    }
                    Stepper("总期数：\(totalPeriods) 期", value: $totalPeriods, in: 1...60)
                    Stepper("已还期数：\(completedPeriods) 期", value: $completedPeriods, in: 0...max(0, totalPeriods - 1))
                    Stepper("每月入账日：\(billingDay) 日", value: $billingDay, in: 1...31)
                    DatePicker("下期入账日期", selection: $nextDueDate, displayedComponents: .date)
                        .environment(\.calendar, YubraePeriodHelper.sundayCalendar())
                }

                Section("记账分类（可选）") {
                    Picker("消费分类", selection: $selectedCategoryId) {
                        Text("默认不指定").tag(Int64(0))
                        ForEach(expenseCategories) { cat in
                            Text(cat.name).tag(cat.id)
                        }
                    }
                }
            }
            .navigationTitle("新建分期计划")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("取消") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("保存") {
                        savePlan()
                    }
                    .fontWeight(.semibold)
                    .disabled(!canSave)
                }
                ToolbarItemGroup(placement: .keyboard) {
                    Spacer()
                    Button("完成") {
                        UIApplication.shared.sendAction(#selector(UIResponder.resignFirstResponder), to: nil, from: nil, for: nil)
                    }
                }
            }
            .onChange(of: totalPeriods) { newTotal in
                if completedPeriods >= newTotal {
                    completedPeriods = max(0, newTotal - 1)
                }
            }
        }
    }

    private func savePlan() {
        let trimmed = name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty, let totalMicros = MoneyAmount.parseToMicros(totalAmountText), totalMicros > 0 else { return }
        let feeMicros = max(0, MoneyAmount.parseToMicros(feePerPeriodText) ?? 0)
        let dueMillis = Int64((nextDueDate.timeIntervalSince1970 * 1000.0).rounded())
        let dto = InstallmentPlanDto(
            accountId: account.id,
            name: trimmed,
            totalAmountMicros: totalMicros,
            feePerPeriodMicros: feeMicros,
            currencyCode: account.currencyCode,
            totalPeriods: totalPeriods,
            completedPeriods: min(completedPeriods, max(0, totalPeriods - 1)),
            billingDay: billingDay,
            nextDueMillis: dueMillis,
            categoryId: selectedCategoryId > 0 ? selectedCategoryId : nil,
            enabled: true
        )
        _ = store.saveInstallment(dto)
        store.toastMessage = "已创建分期计划「\(trimmed)」"
        dismiss()
    }
}

// MARK: - Batch Category Picker Sheet

private struct BatchCategoryPickerSheet: View {
    @ObservedObject var store: LedgerStore
    let selectedCount: Int
    let onPick: (Int64?) -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var categoryType: String = TransactionType.EXPENSE

    var body: some View {
        NavigationStack {
            List {
                Section {
                    Picker("分类类型", selection: $categoryType) {
                        Text("支出分类").tag(TransactionType.EXPENSE)
                        Text("收入分类").tag(TransactionType.INCOME)
                    }
                    .pickerStyle(.segmented)
                }

                Section {
                    Button {
                        onPick(nil)
                        dismiss()
                    } label: {
                        Label("清除分类（设为未分类）", systemImage: "xmark.circle")
                            .foregroundColor(.secondary)
                    }
                    .buttonStyle(.borderless)
                }

                ForEach(store.parentCategories(for: categoryType)) { parent in
                    Section {
                        Button {
                            onPick(parent.id)
                            dismiss()
                        } label: {
                            HStack(spacing: 10) {
                                YubraeCategoryIconBadge(iconKey: parent.icon, colorHex: parent.colorHex, size: 30)
                                Text(parent.name)
                                    .font(.subheadline.weight(.semibold))
                                    .foregroundColor(.primary)
                                Spacer()
                            }
                        }
                        .buttonStyle(.plain)

                        ForEach(store.childCategories(of: parent.id)) { child in
                            Button {
                                onPick(child.id)
                                dismiss()
                            } label: {
                                HStack(spacing: 10) {
                                    Spacer().frame(width: 18)
                                    Image(systemName: DefaultSeedData.sfSymbol(for: child.icon))
                                        .foregroundColor(Color(argbHex: child.colorHex))
                                        .frame(width: 20)
                                    Text(child.name)
                                        .font(.subheadline)
                                        .foregroundColor(.primary)
                                    Spacer()
                                }
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
            }
            .navigationTitle("批量改分类（\(selectedCount) 笔）")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("取消") { dismiss() }
                }
            }
        }
    }
}

// MARK: - Batch Move Account Picker Sheet

private struct BatchAccountPickerSheet: View {
    @ObservedObject var store: LedgerStore
    let currentAccountId: Int64
    let selectedCount: Int
    let onPick: (Int64) -> Void

    @Environment(\.dismiss) private var dismiss

    private var candidateAccounts: [AccountDto] {
        store.activeAccounts.filter { $0.id != currentAccountId }
    }

    var body: some View {
        NavigationStack {
            List {
                Section(footer: Text("跨币种迁移将自动按当日或最新汇率重算目标账户扣款金额，并精准回滚原账户余额；若涉及同账户自转的转账记录将自动跳过。")) {
                    if candidateAccounts.isEmpty {
                        Text("暂无其他活跃账户可供迁入")
                            .font(.subheadline)
                            .foregroundColor(.secondary)
                    } else {
                        ForEach(candidateAccounts) { target in
                            Button {
                                onPick(target.id)
                                dismiss()
                            } label: {
                                HStack(spacing: 10) {
                                    YubraeCategoryIconBadge(iconKey: target.icon, colorHex: target.colorHex, size: 34)
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text(target.name)
                                            .font(.subheadline.weight(.semibold))
                                            .foregroundColor(.primary)
                                            .lineLimit(1)
                                        Text("\(AccountGroup.labelZh(for: target.groupType)) · \(target.currencyCode)")
                                            .font(.caption2)
                                            .foregroundColor(.secondary)
                                            .lineLimit(1)
                                    }
                                    Spacer(minLength: 8)
                                    Text(formatAccountDetailAmount(target.balanceMicros, currencyCode: target.currencyCode, showCurrencyCode: true))
                                        .font(.caption.weight(.semibold).monospacedDigit())
                                        .foregroundColor(.secondary)
                                        .lineLimit(1)
                                        .fixedSize(horizontal: true, vertical: false)
                                }
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
            }
            .navigationTitle("批量改账户（\(selectedCount) 笔）")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("取消") { dismiss() }
                }
            }
        }
        .presentationDetents([.medium, .large])
    }
}

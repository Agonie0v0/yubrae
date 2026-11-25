import SwiftUI

private enum TransactionFilter: String, CaseIterable, Identifiable {
    case all = "全部"
    case expense = "支出"
    case income = "收入"
    case transfer = "转账"
    case pending = "待确认"
    case reimburse = "待报销"

    var id: String { rawValue }
}

private struct DayTransactionGroup: Identifiable {
    let id: String
    let title: String
    let expenseMicros: Int64
    let incomeMicros: Int64
    let items: [TransactionDto]
}

public struct TransactionsView: View {
    @ObservedObject public var store: LedgerStore
    public let onOpenQuickRecord: (TransactionDto?) -> Void
    public let onOpenSmartInput: () -> Void

    @Environment(\.colorScheme) private var colorScheme

    @State private var selectedYear: Int
    @State private var selectedMonth: Int
    @State private var searchText: String = ""
    @State private var selectedFilter: TransactionFilter = .all
    @State private var showAllLedgers: Bool = false

    public init(
        store: LedgerStore,
        onOpenQuickRecord: @escaping (TransactionDto?) -> Void,
        onOpenSmartInput: @escaping () -> Void
    ) {
        self.store = store
        self.onOpenQuickRecord = onOpenQuickRecord
        self.onOpenSmartInput = onOpenSmartInput
        let comps = Calendar.current.dateComponents([.year, .month], from: Date())
        _selectedYear = State(initialValue: comps.year ?? 2026)
        _selectedMonth = State(initialValue: comps.month ?? 1)
    }

    private var palette: YubraePalette {
        YubraeTheme.palette(for: store.settings, colorScheme: colorScheme)
    }

    private var activeLedgerFilterId: Int64 {
        showAllLedgers ? 0 : (store.activeLedger?.id ?? store.settings.activeLedgerId)
    }

    private var monthRange: (Int64, Int64) {
        store.monthRangeMillis(year: selectedYear, month: selectedMonth)
    }

    private var monthTransactions: [TransactionDto] {
        let range = monthRange
        let base = store.transactions(forLedgerId: activeLedgerFilterId)
            .filter { $0.timestamp >= range.0 && $0.timestamp <= range.1 }

        return base.filter { tx in
            let filterOk: Bool
            switch selectedFilter {
            case .all: filterOk = true
            case .expense: filterOk = tx.type == TransactionType.EXPENSE
            case .income: filterOk = tx.type == TransactionType.INCOME
            case .transfer: filterOk = tx.type == TransactionType.TRANSFER
            case .pending: filterOk = tx.status == TransactionStatus.PENDING
            case .reimburse: filterOk = tx.reimburseStatus == ReimburseStatus.PENDING
            }
            guard filterOk else { return false }

            let query = searchText.trimmingCharacters(in: .whitespacesAndNewlines)
            guard !query.isEmpty else { return true }
            let catName = store.category(byId: tx.categoryId)?.name ?? ""
            let accName = store.account(byId: tx.accountId)?.name ?? ""
            return tx.merchant.localizedCaseInsensitiveContains(query)
                || tx.note.localizedCaseInsensitiveContains(query)
                || tx.tags.localizedCaseInsensitiveContains(query)
                || catName.localizedCaseInsensitiveContains(query)
                || accName.localizedCaseInsensitiveContains(query)
        }
    }

    private var monthExpenseMicros: Int64 {
        let range = monthRange
        return store.computeCategoryStats(
            startMillis: range.0,
            endMillis: range.1,
            type: TransactionType.EXPENSE,
            ledgerId: activeLedgerFilterId,
            displayCurrency: store.settings.baseCurrency
        ).reduce(0) { $0 + $1.totalBaseMicros }
    }

    private var monthIncomeMicros: Int64 {
        let range = monthRange
        return store.computeCategoryStats(
            startMillis: range.0,
            endMillis: range.1,
            type: TransactionType.INCOME,
            ledgerId: activeLedgerFilterId,
            displayCurrency: store.settings.baseCurrency
        ).reduce(0) { $0 + $1.totalBaseMicros }
    }

    private var groupedByDay: [DayTransactionGroup] {
        var bucketOrder: [String] = []
        var byKey: [String: [TransactionDto]] = [:]
        for tx in monthTransactions {
            let key = DefaultSeedData.formatISODate(epochMillis: tx.timestamp)
            if byKey[key] == nil {
                bucketOrder.append(key)
            }
            byKey[key, default: []].append(tx)
        }

        let baseCurrency = store.settings.baseCurrency
        return bucketOrder.compactMap { key -> DayTransactionGroup? in
            guard let items = byKey[key], let first = items.first else { return nil }
            var exp: Int64 = 0
            var inc: Int64 = 0
            for tx in items where tx.status == TransactionStatus.CONFIRMED && !tx.excludeFromStats {
                let converted = displayBaseAmountMicros(for: tx, targetCurrency: baseCurrency)
                if tx.type == TransactionType.EXPENSE {
                    exp += converted
                } else if tx.type == TransactionType.INCOME {
                    inc += converted
                }
            }
            return DayTransactionGroup(
                id: key,
                title: YubraeTheme.formatDaySectionTitle(first.timestamp),
                expenseMicros: exp,
                incomeMicros: inc,
                items: items
            )
        }
    }

    private var budgetSummaries: [BudgetProgress] {
        store.computeBudgetProgress(year: selectedYear, month: selectedMonth, ledgerId: activeLedgerFilterId)
    }

    public var body: some View {
        NavigationStack {
            ScrollView {
                LazyVStack(spacing: 14) {
                    monthlyHeroCard

                    if store.pendingCount > 0 {
                        pendingBannerCard
                    }

                    if let topBudget = budgetSummaries.first(where: { $0.budget.categoryId == nil }) ?? budgetSummaries.first {
                        compactBudgetCard(topBudget)
                    }

                    searchAndFilterBar

                    if groupedByDay.isEmpty {
                        YubraeEmptyStateView(
                            icon: "tray",
                            title: "本月暂无符合条件的账单",
                            subtitle: "点击右下角「记一笔」或「智能记账」，支持一句话拆单与小票识图。",
                            palette: palette,
                            actionTitle: "立即记一笔",
                            action: { onOpenQuickRecord(nil) }
                        )
                    } else {
                        ForEach(groupedByDay) { group in
                            daySectionView(group)
                        }
                    }
                }
                .padding(.horizontal, 16)
                .padding(.top, 10)
                .padding(.bottom, 120)
            }
            .background(palette.background.ignoresSafeArea())
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    ledgerSwitcherMenu
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
                            onOpenSmartInput()
                        } label: {
                            Image(systemName: "sparkles")
                                .foregroundColor(palette.primary)
                        }
                        .accessibilityLabel("AI 智能记账")
                    }
                }
            }
        }
    }

    // MARK: - Subviews

    private var ledgerSwitcherMenu: some View {
        Menu {
            ForEach(store.activeLedgers) { ledger in
                Button {
                    showAllLedgers = false
                    store.setActiveLedger(id: ledger.id)
                } label: {
                    if !showAllLedgers && store.activeLedger?.id == ledger.id {
                        Label(ledger.name, systemImage: "checkmark")
                    } else {
                        Text(ledger.name)
                    }
                }
            }
            Divider()
            Button {
                showAllLedgers = true
            } label: {
                if showAllLedgers {
                    Label("全部账本汇总", systemImage: "checkmark")
                } else {
                    Text("全部账本汇总")
                }
            }
        } label: {
            HStack(spacing: 6) {
                YubraeBrandMark(size: 24, primaryColor: palette.primary)
                Text(showAllLedgers ? "全部账本" : (store.activeLedger?.name ?? "默认账本"))
                    .font(.headline)
                    .foregroundColor(palette.textPrimary)
                Image(systemName: "chevron.down")
                    .font(.caption.weight(.bold))
                    .foregroundColor(palette.textSecondary)
            }
        }
    }

    private var monthlyHeroCard: some View {
        let baseCur = store.settings.baseCurrency
        let masked = store.settings.privacyMaskEnabled
        let expText = YubraeTheme.masked(MoneyAmount.format(monthExpenseMicros, currencyCode: baseCur), isMasked: masked)
        let incText = YubraeTheme.masked(MoneyAmount.format(monthIncomeMicros, currencyCode: baseCur), isMasked: masked)
        let netMicros = monthIncomeMicros - monthExpenseMicros
        let netText = YubraeTheme.masked(MoneyAmount.format(netMicros, currencyCode: baseCur, showSign: true), isMasked: masked)

        return VStack(alignment: .leading, spacing: 14) {
            HStack {
                HStack(spacing: 12) {
                    Button { shiftMonth(by: -1) } label: {
                        Image(systemName: "chevron.left")
                            .font(.subheadline.weight(.bold))
                            .foregroundColor(.white.opacity(0.9))
                            .frame(width: 28, height: 28)
                            .background(Circle().fill(Color.white.opacity(0.18)))
                    }
                    Text("\(String(selectedYear))年\(selectedMonth)月")
                        .font(.headline.weight(.bold))
                        .foregroundColor(.white)
                    Button { shiftMonth(by: 1) } label: {
                        Image(systemName: "chevron.right")
                            .font(.subheadline.weight(.bold))
                            .foregroundColor(.white.opacity(0.9))
                            .frame(width: 28, height: 28)
                            .background(Circle().fill(Color.white.opacity(0.18)))
                    }
                }
                Spacer()
                Text("\(monthTransactions.count) 笔明细")
                    .font(.caption.weight(.medium))
                    .foregroundColor(.white.opacity(0.9))
                    .padding(.horizontal, 10)
                    .padding(.vertical, 4)
                    .background(Capsule().fill(Color.white.opacity(0.18)))
            }

            VStack(alignment: .leading, spacing: 4) {
                Text("本月支出 (\(baseCur))")
                    .font(.caption)
                    .foregroundColor(.white.opacity(0.84))
                Text(expText)
                    .font(.system(size: 30, weight: .bold, design: .rounded))
                    .foregroundColor(.white)
                    .minimumScaleFactor(0.75)
                    .lineLimit(1)
            }

            HStack(spacing: 20) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("本月收入")
                        .font(.caption2)
                        .foregroundColor(.white.opacity(0.78))
                    Text(incText)
                        .font(.subheadline.weight(.semibold))
                        .foregroundColor(.white)
                }
                Divider()
                    .frame(height: 24)
                    .background(Color.white.opacity(0.3))
                VStack(alignment: .leading, spacing: 2) {
                    Text("月度结余")
                        .font(.caption2)
                        .foregroundColor(.white.opacity(0.78))
                    Text(netText)
                        .font(.subheadline.weight(.semibold))
                        .foregroundColor(.white)
                }
                Spacer()
            }
        }
        .padding(18)
        .background(
            RoundedRectangle(cornerRadius: 22, style: .continuous)
                .fill(palette.heroGradient)
                .shadow(color: palette.primary.opacity(0.22), radius: 14, x: 0, y: 8)
        )
    }

    private var pendingBannerCard: some View {
        HStack(spacing: 10) {
            Image(systemName: "clock.badge.exclamationmark")
                .foregroundColor(palette.warningColor)
            VStack(alignment: .leading, spacing: 2) {
                Text("有 \(store.pendingCount) 笔待确认账单")
                    .font(.subheadline.weight(.semibold))
                    .foregroundColor(palette.textPrimary)
                Text("核对后可一键转为已确认状态并纳入统计")
                    .font(.caption)
                    .foregroundColor(palette.textSecondary)
            }
            Spacer()
            Button("全部确认") {
                let count = store.confirmAllPending()
                store.toastMessage = "已确认 \(count) 笔待处理账单"
            }
            .font(.caption.weight(.semibold))
            .foregroundColor(palette.onPrimary)
            .padding(.horizontal, 12)
            .padding(.vertical, 6)
            .background(Capsule().fill(palette.warningColor))
        }
        .padding(14)
        .background(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .fill(palette.warningColor.opacity(0.12))
        )
    }

    private func compactBudgetCard(_ progress: BudgetProgress) -> some View {
        let masked = store.settings.privacyMaskEnabled
        let spentStr = YubraeTheme.masked(MoneyAmount.format(progress.spentInBudgetCurrencyMicros, currencyCode: progress.budget.currencyCode), isMasked: masked)
        let limitStr = YubraeTheme.masked(MoneyAmount.format(progress.limitInBudgetCurrencyMicros, currencyCode: progress.budget.currencyCode), isMasked: masked)
        let clampedRatio = min(1.0, max(0.0, progress.progressRatio))

        return YubraeCard(palette: palette, padding: 14) {
            VStack(alignment: .leading, spacing: 8) {
                HStack {
                    Label(progress.categoryName, systemImage: "gauge.medium")
                        .font(.caption.weight(.semibold))
                        .foregroundColor(palette.textPrimary)
                    Spacer()
                    Text("\(spentStr) / \(limitStr)")
                        .font(.caption.weight(.semibold))
                        .foregroundColor(progress.overBudget ? palette.dangerColor : palette.textSecondary)
                }
                GeometryReader { geo in
                    ZStack(alignment: .leading) {
                        Capsule().fill(palette.surfaceSecondary)
                        Capsule()
                            .fill(progress.overBudget ? palette.dangerColor : palette.primary)
                            .frame(width: geo.size.width * CGFloat(clampedRatio))
                    }
                }
                .frame(height: 7)
            }
        }
    }

    private var searchAndFilterBar: some View {
        VStack(spacing: 10) {
            HStack(spacing: 8) {
                Image(systemName: "magnifyingglass")
                    .foregroundColor(palette.textTertiary)
                TextField("搜索商户、分类、标签或备注", text: $searchText)
                    .font(.subheadline)
                if !searchText.isEmpty {
                    Button {
                        searchText = ""
                    } label: {
                        Image(systemName: "xmark.circle.fill")
                            .foregroundColor(palette.textTertiary)
                    }
                }
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 9)
            .background(
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .fill(palette.surface)
                    .overlay(
                        RoundedRectangle(cornerRadius: 12, style: .continuous)
                            .strokeBorder(palette.outline, lineWidth: 0.8)
                    )
            )

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(TransactionFilter.allCases) { filter in
                        let selected = selectedFilter == filter
                        Button {
                            selectedFilter = filter
                        } label: {
                            Text(filter.rawValue)
                                .font(.caption.weight(.semibold))
                                .foregroundColor(selected ? palette.onPrimary : palette.textSecondary)
                                .padding(.horizontal, 12)
                                .padding(.vertical, 6)
                                .background(
                                    Capsule().fill(selected ? palette.primary : palette.surface)
                                )
                                .overlay(
                                    Capsule().strokeBorder(selected ? Color.clear : palette.outline, lineWidth: 0.8)
                                )
                        }
                    }
                }
            }
        }
    }

    private func daySectionView(_ group: DayTransactionGroup) -> some View {
        let baseCur = store.settings.baseCurrency
        let masked = store.settings.privacyMaskEnabled
        return VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text(group.title)
                    .font(.caption.weight(.semibold))
                    .foregroundColor(palette.textSecondary)
                Spacer()
                HStack(spacing: 10) {
                    if group.expenseMicros > 0 {
                        Text("支 \(YubraeTheme.masked(MoneyAmount.format(group.expenseMicros, currencyCode: baseCur), isMasked: masked))")
                            .font(.caption2)
                            .foregroundColor(palette.textTertiary)
                    }
                    if group.incomeMicros > 0 {
                        Text("收 \(YubraeTheme.masked(MoneyAmount.format(group.incomeMicros, currencyCode: baseCur), isMasked: masked))")
                            .font(.caption2)
                            .foregroundColor(palette.textTertiary)
                    }
                }
            }
            .padding(.horizontal, 4)

            YubraeCard(palette: palette, padding: 0) {
                VStack(spacing: 0) {
                    ForEach(group.items) { tx in
                        transactionRow(tx)
                            .contentShape(Rectangle())
                            .onTapGesture {
                                onOpenQuickRecord(tx)
                            }
                            .contextMenu {
                                Button {
                                    onOpenQuickRecord(tx)
                                } label: {
                                    Label("编辑账单", systemImage: "pencil")
                                }
                                if tx.status == TransactionStatus.PENDING {
                                    Button {
                                        try? store.confirmTransaction(id: tx.id)
                                        store.toastMessage = "已确认该笔账单"
                                    } label: {
                                        Label("确认入账", systemImage: "checkmark.circle")
                                    }
                                }
                                if tx.reimburseStatus == ReimburseStatus.PENDING, let defaultAcc = store.activeAccounts.first {
                                    Button {
                                        do {
                                            _ = try store.settleReimbursements(txIds: [tx.id], targetAccountId: defaultAcc.id)
                                            store.toastMessage = "报销已核销至「\(defaultAcc.name)」"
                                        } catch {
                                            store.lastErrorMessage = error.localizedDescription
                                        }
                                    } label: {
                                        Label("一键核销报销", systemImage: "arrow.uturn.backward.circle")
                                    }
                                }
                                Button(role: .destructive) {
                                    do {
                                        try store.deleteTransaction(id: tx.id)
                                        store.toastMessage = "已删除账单"
                                    } catch {
                                        store.lastErrorMessage = error.localizedDescription
                                    }
                                } label: {
                                    Label("删除", systemImage: "trash")
                                }
                            }
                        if tx.id != group.items.last?.id {
                            Divider()
                                .padding(.leading, 64)
                        }
                    }
                }
            }
        }
    }

    private func transactionRow(_ tx: TransactionDto) -> some View {
        let cat = store.category(byId: tx.categoryId)
        let acc = store.account(byId: tx.accountId)
        let toAcc = tx.toAccountId.flatMap { store.account(byId: $0) }
        let masked = store.settings.privacyMaskEnabled

        let iconKey: String
        let iconColor: Int64
        let primaryTitle: String

        switch tx.type {
        case TransactionType.TRANSFER:
            iconKey = "currency_exchange"
            iconColor = 0xFF2563EB
            let route = "\(acc?.name ?? "账户") → \(toAcc?.name ?? "目标账户")"
            primaryTitle = tx.merchant.isEmpty ? route : tx.merchant
        case TransactionType.ADJUST:
            iconKey = "calculate"
            iconColor = 0xFF64748B
            primaryTitle = tx.merchant.isEmpty ? "余额校准" : tx.merchant
        default:
            iconKey = cat?.icon ?? "category"
            iconColor = cat?.colorHex ?? 0xFF2AA876
            primaryTitle = tx.merchant.isEmpty ? (cat?.name ?? "未分类") : tx.merchant
        }

        let amountSignPrefix: String
        let amountColor: Color
        switch tx.type {
        case TransactionType.EXPENSE:
            amountSignPrefix = "-"
            amountColor = palette.expenseColor
        case TransactionType.INCOME:
            amountSignPrefix = "+"
            amountColor = palette.incomeColor
        case TransactionType.TRANSFER:
            amountSignPrefix = ""
            amountColor = palette.transferColor
        default:
            amountSignPrefix = tx.origAmountMicros >= 0 ? "+" : ""
            amountColor = palette.textSecondary
        }

        let formattedOrig = amountSignPrefix + MoneyAmount.format(tx.origAmountMicros, currencyCode: tx.origCurrency, showCurrencyCode: true)
        let txTags = TagTextCodec.parse(tx.tags)

        return HStack(spacing: 12) {
            YubraeCategoryIconBadge(iconKey: iconKey, colorHex: iconColor, size: 40)

            VStack(alignment: .leading, spacing: 3) {
                HStack(spacing: 6) {
                    Text(primaryTitle)
                        .font(.subheadline.weight(.semibold))
                        .foregroundColor(palette.textPrimary)
                        .lineLimit(1)
                    if tx.status == TransactionStatus.PENDING {
                        badgeTag("待确认", color: palette.warningColor)
                    }
                    if tx.reimburseStatus == ReimburseStatus.PENDING {
                        badgeTag("待报销", color: palette.transferColor)
                    }
                }

                HStack(spacing: 6) {
                    Text(YubraeTheme.formatDate(tx.timestamp, pattern: "HH:mm"))
                    if let acc {
                        Text("· \(acc.name)")
                    }
                    if let cat, !tx.merchant.isEmpty {
                        Text("· \(cat.name)")
                    }
                }
                .font(.caption)
                .foregroundColor(palette.textSecondary)
                .lineLimit(1)

                if !tx.note.isEmpty || !txTags.isEmpty {
                    HStack(spacing: 4) {
                        ForEach(txTags.prefix(2), id: \.self) { t in
                            Text("#\(t)")
                                .font(.caption2)
                                .foregroundColor(palette.primary)
                        }
                        if !tx.note.isEmpty {
                            Text(tx.note)
                                .font(.caption2)
                                .foregroundColor(palette.textTertiary)
                                .lineLimit(1)
                        }
                    }
                }
            }

            Spacer(minLength: 6)

            VStack(alignment: .trailing, spacing: 2) {
                Text(YubraeTheme.masked(formattedOrig, isMasked: masked))
                    .font(.subheadline.weight(.bold).monospacedDigit())
                    .foregroundColor(amountColor)
                if tx.origCurrency != tx.accountCurrency {
                    let accFmt = MoneyAmount.format(tx.accountAmountMicros, currencyCode: tx.accountCurrency, showCurrencyCode: true)
                    Text("实扣 \(YubraeTheme.masked(accFmt, isMasked: masked))")
                        .font(.caption2)
                        .foregroundColor(palette.textTertiary)
                }
            }
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 12)
    }

    private func badgeTag(_ text: String, color: Color) -> some View {
        Text(text)
            .font(.system(size: 10, weight: .semibold))
            .foregroundColor(color)
            .padding(.horizontal, 6)
            .padding(.vertical, 2)
            .background(Capsule().fill(color.opacity(0.14)))
    }

    // MARK: - Helpers

    private func shiftMonth(by delta: Int) {
        var comps = DateComponents(year: selectedYear, month: selectedMonth, day: 1)
        let cal = Calendar.current
        if let base = cal.date(from: comps),
           let shifted = cal.date(byAdding: .month, value: delta, to: base) {
            comps = cal.dateComponents([.year, .month], from: shifted)
            selectedYear = comps.year ?? selectedYear
            selectedMonth = comps.month ?? selectedMonth
        }
    }

    private func displayBaseAmountMicros(for tx: TransactionDto, targetCurrency: String) -> Int64 {
        let orig = CurrencyCatalog.normalize(tx.origCurrency)
        let txBase = CurrencyCatalog.normalize(tx.baseCurrency)
        let frozenBase = (orig == txBase)
            ? tx.origAmountMicros
            : MoneyAmount.convert(amountMicros: tx.origAmountMicros, rateMicros: tx.rateToBaseMicros)
        let target = CurrencyCatalog.normalize(targetCurrency)
        if txBase == target { return frozenBase }
        let rate = store.getRate(from: txBase, to: target).rateMicros
        return MoneyAmount.convert(amountMicros: frozenBase, rateMicros: rate)
    }
}

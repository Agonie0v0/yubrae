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
    let dateText: String
    let subText: String
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
    @State private var isSearchExpanded: Bool = false
    @State private var showAllLedgers: Bool = false
    @State private var showMonthPickerSheet: Bool = false
    @State private var showMonthSummarySheet: Bool = false

    public init(
        store: LedgerStore,
        onOpenQuickRecord: @escaping (TransactionDto?) -> Void,
        onOpenSmartInput: @escaping () -> Void = {}
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

    private var currentYearMonth: (year: Int, month: Int) {
        let comps = Calendar.current.dateComponents([.year, .month], from: Date())
        return (comps.year ?? 2026, comps.month ?? 1)
    }

    private var isCurrentMonth: Bool {
        let now = currentYearMonth
        return selectedYear == now.year && selectedMonth == now.month
    }

    private var hasActiveFilter: Bool {
        selectedFilter != .all || !searchText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    private var isSearchBarVisible: Bool {
        isSearchExpanded || hasActiveFilter
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

            return store.matchesSearchQuery(searchText, for: tx)
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

    private var monthDiscountFeeSummary: PeriodDiscountFeeSummary {
        let range = monthRange
        return store.computeDiscountFeeSummary(
            startMillis: range.0,
            endMillis: range.1,
            ledgerId: activeLedgerFilterId,
            displayCurrency: store.settings.baseCurrency
        )
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
            let headerParts = YubraeTheme.formatDayHeaderParts(first.timestamp)
            return DayTransactionGroup(
                id: key,
                dateText: headerParts.dateText,
                subText: headerParts.subText,
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
        let groups = groupedByDay
        let refundedByOrigId = store.computeRefundedAmountsByOrigTxId()
        let categoryById = Dictionary(store.categories.map { ($0.id, $0) }, uniquingKeysWith: { first, _ in first })
        let accountById = Dictionary(store.accounts.map { ($0.id, $0) }, uniquingKeysWith: { first, _ in first })

        return NavigationStack {
            VStack(spacing: 0) {
                minimalMonthlyHeader

                ScrollView {
                    LazyVStack(spacing: 14) {
                        if isSearchBarVisible {
                            searchAndFilterBar
                                .transition(.opacity.combined(with: .move(edge: .top)))
                        }

                        if store.pendingCount > 0 {
                            pendingBannerCard
                        }

                        if let topBudget = budgetSummaries.first(where: { $0.budget.categoryId == nil }) ?? budgetSummaries.first {
                            compactBudgetCard(topBudget)
                        }

                        if groups.isEmpty {
                            minimalEmptyState
                        } else {
                            ForEach(groups) { group in
                                daySectionView(
                                    group,
                                    refundedByOrigId: refundedByOrigId,
                                    categoryById: categoryById,
                                    accountById: accountById
                                )
                            }
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.top, 12)
                    .padding(.bottom, 108)
                }
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
                            withAnimation(.easeInOut(duration: 0.2)) {
                                if isSearchBarVisible {
                                    isSearchExpanded = false
                                    if hasActiveFilter {
                                        searchText = ""
                                        selectedFilter = .all
                                    }
                                } else {
                                    isSearchExpanded = true
                                }
                            }
                        } label: {
                            Image(systemName: "magnifyingglass")
                                .foregroundColor(isSearchBarVisible ? palette.primary : palette.textPrimary)
                        }
                        .accessibilityLabel(isSearchBarVisible ? "收起搜索与筛选" : "搜索与筛选")

                        Button {
                            store.updateSettings { $0.privacyMaskEnabled.toggle() }
                        } label: {
                            Image(systemName: store.settings.privacyMaskEnabled ? "eye.slash" : "eye")
                                .foregroundColor(palette.textPrimary)
                        }
                        .accessibilityLabel("切换隐私遮罩")
                    }
                }
            }
            .onChange(of: selectedFilter) { newFilter in
                if newFilter != .all {
                    isSearchExpanded = true
                }
            }
            .onChange(of: searchText) { newQuery in
                if !newQuery.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                    isSearchExpanded = true
                }
            }
            .sheet(isPresented: $showMonthPickerSheet) {
                YubraeYearMonthPickerSheet(
                    initialYear: selectedYear,
                    initialMonth: selectedMonth,
                    palette: palette
                ) { year, month in
                    selectedYear = year
                    selectedMonth = month
                }
                .yubraeSheetStyle(palette: palette)
            }
            .sheet(isPresented: $showMonthSummarySheet) {
                let baseCur = store.settings.baseCurrency
                let masked = store.settings.privacyMaskEnabled
                let expText = YubraeTheme.masked(
                    YubraeTheme.formatPlainSignedAmount(monthExpenseMicros, currencyCode: baseCur, baseCurrency: baseCur, showSign: false),
                    isMasked: masked
                )
                let incText = YubraeTheme.masked(
                    YubraeTheme.formatPlainSignedAmount(monthIncomeMicros, currencyCode: baseCur, baseCurrency: baseCur, showSign: false),
                    isMasked: masked
                )
                let netText = YubraeTheme.masked(
                    YubraeTheme.formatPlainSignedAmount(monthIncomeMicros - monthExpenseMicros, currencyCode: baseCur, baseCurrency: baseCur, showSign: true),
                    isMasked: masked
                )
                YubraeMonthSummaryDetailSheet(
                    title: "\(String(selectedYear))年\(selectedMonth)月",
                    currency: baseCur,
                    expenseText: expText,
                    incomeText: incText,
                    balanceText: netText,
                    transactionCount: monthTransactions.count,
                    palette: palette
                ) { msg in
                    store.toastMessage = msg
                }
                .yubraeSheetStyle(palette: palette)
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
            HStack(spacing: 5) {
                YubraeBrandMark(size: 20, primaryColor: palette.primary)
                Text(showAllLedgers ? "全部账本" : (store.activeLedger?.name ?? "默认账本"))
                    .font(.subheadline.weight(.semibold))
                    .foregroundColor(palette.textPrimary)
                    .lineLimit(1)
                    .truncationMode(.tail)
                    .fixedSize(horizontal: true, vertical: false)
                Image(systemName: "chevron.down")
                    .font(.system(size: 10, weight: .semibold))
                    .foregroundColor(palette.textSecondary)
            }
        }
    }

    /// Linear / 苹果备忘录风格扁平月度概览区（无厚重实心梯度与冗余笔数胶囊）
    private var minimalMonthlyHeader: some View {
        let baseCur = CurrencyCatalog.normalize(store.settings.baseCurrency)
        let masked = store.settings.privacyMaskEnabled
        let expText = YubraeTheme.masked(
            YubraeTheme.formatPlainSignedAmount(monthExpenseMicros, currencyCode: baseCur, baseCurrency: baseCur, showSign: false),
            isMasked: masked
        )
        let incText = YubraeTheme.masked(
            YubraeTheme.formatPlainSignedAmount(monthIncomeMicros, currencyCode: baseCur, baseCurrency: baseCur, showSign: false),
            isMasked: masked
        )
        let netMicros = monthIncomeMicros - monthExpenseMicros
        let netText = YubraeTheme.masked(
            YubraeTheme.formatPlainSignedAmount(netMicros, currencyCode: baseCur, baseCurrency: baseCur, showSign: true),
            isMasked: masked
        )
        let dfSummary = monthDiscountFeeSummary
        let nowYM = currentYearMonth
        let monthTitle = selectedYear == nowYM.year
            ? "\(selectedMonth)月"
            : "\(String(selectedYear))年\(selectedMonth)月"

        return VStack(spacing: 0) {
            VStack(alignment: .leading, spacing: 10) {
                // 月份切换条：‹ 11月 ▾ ›
                HStack(spacing: 6) {
                    Button { shiftMonth(by: -1) } label: {
                        Image(systemName: "chevron.left")
                            .font(.caption.weight(.semibold))
                            .foregroundColor(palette.textSecondary)
                            .frame(width: 28, height: 28)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("上个月")

                    Button {
                        showMonthPickerSheet = true
                    } label: {
                        HStack(spacing: 4) {
                            Text(monthTitle)
                                .font(.subheadline.weight(.semibold).monospacedDigit())
                                .foregroundColor(isCurrentMonth ? palette.textPrimary : palette.primary)
                                .lineLimit(1)
                                .fixedSize(horizontal: true, vertical: false)
                            Image(systemName: "chevron.down")
                                .font(.system(size: 9, weight: .bold))
                                .foregroundColor(isCurrentMonth ? palette.textSecondary : palette.primary)
                        }
                        .padding(.horizontal, 8)
                        .padding(.vertical, 4)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("选择年月")

                    Button { shiftMonth(by: 1) } label: {
                        Image(systemName: "chevron.right")
                            .font(.caption.weight(.semibold))
                            .foregroundColor(palette.textSecondary)
                            .frame(width: 28, height: 28)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("下个月")

                    Spacer(minLength: 6)

                    if !isCurrentMonth {
                        Button {
                            selectedYear = nowYM.year
                            selectedMonth = nowYM.month
                        } label: {
                            Text("回本月")
                                .font(.caption2.weight(.medium))
                                .foregroundColor(palette.primary)
                                .lineLimit(1)
                                .fixedSize(horizontal: true, vertical: false)
                                .padding(.horizontal, 8)
                                .padding(.vertical, 4)
                                .background(
                                    Capsule().fill(palette.primary.opacity(palette.isDark ? 0.18 : 0.10))
                                )
                        }
                        .buttonStyle(.plain)
                    }
                }

                // 3 列月度汇总：支出 / 收入 / 结余
                Button {
                    showMonthSummarySheet = true
                } label: {
                    HStack(spacing: 12) {
                        summaryMetricColumn(
                            label: baseCur == CurrencyCatalog.defaultBaseCurrency ? "支出" : "支出 (\(baseCur))",
                            value: expText,
                            valueColor: palette.textPrimary
                        )
                        summaryMetricColumn(
                            label: "收入",
                            value: incText,
                            valueColor: palette.incomeColor
                        )
                        summaryMetricColumn(
                            label: "结余",
                            value: netText,
                            valueColor: palette.textSecondary
                        )
                    }
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel("查看本月收支详情")

                // 优惠 / 手续费极轻一行（均为 0 时零占位）
                if dfSummary.discountBaseMicros > 0 || dfSummary.feeBaseMicros > 0 {
                    HStack(spacing: 6) {
                        if dfSummary.discountBaseMicros > 0 {
                            let discStr = YubraeTheme.masked(
                                YubraeTheme.formatPlainSignedAmount(
                                    dfSummary.discountBaseMicros,
                                    currencyCode: baseCur,
                                    baseCurrency: baseCur,
                                    showSign: false
                                ),
                                isMasked: masked
                            )
                            Text("省 \(discStr)")
                                .font(.caption2.monospacedDigit())
                                .foregroundColor(palette.textTertiary)
                                .lineLimit(1)
                                .fixedSize(horizontal: true, vertical: false)
                        }
                        if dfSummary.discountBaseMicros > 0 && dfSummary.feeBaseMicros > 0 {
                            Text("·")
                                .font(.caption2)
                                .foregroundColor(palette.textTertiary)
                        }
                        if dfSummary.feeBaseMicros > 0 {
                            let feeStr = YubraeTheme.masked(
                                YubraeTheme.formatPlainSignedAmount(
                                    dfSummary.feeBaseMicros,
                                    currencyCode: baseCur,
                                    baseCurrency: baseCur,
                                    showSign: false
                                ),
                                isMasked: masked
                            )
                            Text("费 \(feeStr)")
                                .font(.caption2.monospacedDigit())
                                .foregroundColor(palette.textTertiary)
                                .lineLimit(1)
                                .fixedSize(horizontal: true, vertical: false)
                        }
                        Spacer(minLength: 0)
                    }
                }
            }
            .padding(.horizontal, 16)
            .padding(.top, 6)
            .padding(.bottom, 12)
            .background(palette.surface)

            Rectangle()
                .fill(palette.outline)
                .frame(height: 0.5)
        }
    }

    private func summaryMetricColumn(label: String, value: String, valueColor: Color) -> some View {
        VStack(alignment: .leading, spacing: 3) {
            Text(label)
                .font(.caption2)
                .foregroundColor(palette.textTertiary)
                .lineLimit(1)
            Text(value)
                .font(.system(size: 17, weight: .semibold, design: .rounded).monospacedDigit())
                .foregroundColor(valueColor)
                .frame(maxWidth: .infinity, alignment: .leading)
                .lineLimit(1)
                .minimumScaleFactor(0.6)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private var pendingBannerCard: some View {
        HStack(spacing: 8) {
            Image(systemName: "clock.badge.exclamationmark")
                .font(.footnote.weight(.semibold))
                .foregroundColor(palette.warningColor)
            Text("\(store.pendingCount) 笔待确认")
                .font(.footnote.weight(.semibold).monospacedDigit())
                .foregroundColor(palette.textPrimary)
                .lineLimit(1)
            Spacer(minLength: 6)
            Button {
                let count = store.confirmAllPending()
                store.toastMessage = "已确认 \(count) 笔待处理账单"
            } label: {
                Text("全部确认")
                    .font(.caption.weight(.semibold))
                    .lineLimit(1)
                    .fixedSize(horizontal: true, vertical: false)
                    .foregroundColor(palette.warningColor)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 5)
                    .background(
                        Capsule().fill(palette.warningColor.opacity(palette.isDark ? 0.22 : 0.14))
                    )
            }
            .buttonStyle(.plain)
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 9)
        .background(
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .fill(palette.warningColor.opacity(palette.isDark ? 0.12 : 0.08))
        )
    }

    private func compactBudgetCard(_ progress: BudgetProgress) -> some View {
        let masked = store.settings.privacyMaskEnabled
        let baseCur = store.settings.baseCurrency
        let spentStr = YubraeTheme.masked(
            YubraeTheme.formatPlainSignedAmount(
                progress.spentInBudgetCurrencyMicros,
                currencyCode: progress.budget.currencyCode,
                baseCurrency: baseCur,
                showSign: false
            ),
            isMasked: masked
        )
        let limitStr = YubraeTheme.masked(
            YubraeTheme.formatPlainSignedAmount(
                progress.limitInBudgetCurrencyMicros,
                currencyCode: progress.budget.currencyCode,
                baseCurrency: baseCur,
                showSign: false
            ),
            isMasked: masked
        )
        let clampedRatio = min(1.0, max(0.0, progress.progressRatio))

        return YubraeCard(palette: palette, padding: 12, cornerRadius: 14) {
            VStack(alignment: .leading, spacing: 7) {
                HStack(spacing: 8) {
                    Label(progress.categoryName, systemImage: "gauge.medium")
                        .font(.caption.weight(.semibold))
                        .foregroundColor(palette.textPrimary)
                        .lineLimit(1)
                        .truncationMode(.tail)
                    Spacer(minLength: 6)
                    Text("\(spentStr) / \(limitStr)")
                        .font(.caption.weight(.semibold).monospacedDigit())
                        .foregroundColor(progress.overBudget ? palette.dangerColor : palette.textSecondary)
                        .lineLimit(1)
                        .fixedSize(horizontal: true, vertical: false)
                }
                GeometryReader { geo in
                    ZStack(alignment: .leading) {
                        Capsule().fill(palette.surfaceSecondary)
                        Capsule()
                            .fill(progress.overBudget ? palette.dangerColor : palette.primary)
                            .frame(width: geo.size.width * CGFloat(clampedRatio))
                    }
                }
                .frame(height: 5)
            }
        }
    }

    private var searchAndFilterBar: some View {
        VStack(spacing: 8) {
            HStack(spacing: 8) {
                Image(systemName: "magnifyingglass")
                    .font(.subheadline)
                    .foregroundColor(palette.textTertiary)
                TextField("搜索分类、商户、账户、标签或备注", text: $searchText)
                    .font(.subheadline)
                if !searchText.isEmpty {
                    Button {
                        searchText = ""
                    } label: {
                        Image(systemName: "xmark.circle.fill")
                            .foregroundColor(palette.textTertiary)
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 8)
            .background(
                RoundedRectangle(cornerRadius: 11, style: .continuous)
                    .fill(palette.surface)
                    .overlay(
                        RoundedRectangle(cornerRadius: 11, style: .continuous)
                            .strokeBorder(palette.outline, lineWidth: 0.5)
                    )
            )

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 7) {
                    ForEach(TransactionFilter.allCases) { filter in
                        let selected = selectedFilter == filter
                        Button {
                            selectedFilter = filter
                        } label: {
                            Text(filter.rawValue)
                                .font(.caption.weight(.medium))
                                .lineLimit(1)
                                .fixedSize(horizontal: true, vertical: false)
                                .foregroundColor(selected ? palette.onPrimary : palette.textSecondary)
                                .padding(.horizontal, 11)
                                .padding(.vertical, 5)
                                .background(
                                    Capsule().fill(selected ? palette.primary : palette.surface)
                                )
                                .overlay(
                                    Capsule().strokeBorder(selected ? Color.clear : palette.outline, lineWidth: 0.5)
                                )
                        }
                        .buttonStyle(.plain)
                    }

                    if hasActiveFilter {
                        Button {
                            searchText = ""
                            selectedFilter = .all
                        } label: {
                            Text("清空")
                                .font(.caption.weight(.medium))
                                .lineLimit(1)
                                .fixedSize(horizontal: true, vertical: false)
                                .foregroundColor(palette.primary)
                                .padding(.horizontal, 10)
                                .padding(.vertical, 5)
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
        }
    }

    private var minimalEmptyState: some View {
        VStack(spacing: 12) {
            Image(systemName: "tray")
                .font(.system(size: 26, weight: .regular))
                .foregroundColor(palette.textTertiary)
            Text(hasActiveFilter ? "无匹配账单" : "暂无账单")
                .font(.subheadline.weight(.medium))
                .foregroundColor(palette.textSecondary)
            Button {
                if hasActiveFilter {
                    searchText = ""
                    selectedFilter = .all
                    isSearchExpanded = false
                } else {
                    onOpenQuickRecord(nil)
                }
            } label: {
                Text(hasActiveFilter ? "清空筛选" : "记一笔")
                    .font(.subheadline.weight(.semibold))
                    .lineLimit(1)
                    .fixedSize(horizontal: true, vertical: false)
                    .foregroundColor(palette.onPrimary)
                    .padding(.horizontal, 18)
                    .padding(.vertical, 8)
                    .background(Capsule().fill(palette.primary))
            }
            .buttonStyle(.plain)
            .padding(.top, 2)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 48)
    }

    private func daySectionView(
        _ group: DayTransactionGroup,
        refundedByOrigId: [Int64: Int64],
        categoryById: [Int64: CategoryDto],
        accountById: [Int64: AccountDto]
    ) -> some View {
        let baseCur = store.settings.baseCurrency
        let masked = store.settings.privacyMaskEnabled

        return VStack(alignment: .leading, spacing: 6) {
            // 日期分组头位于白卡外部上方
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
                    if group.incomeMicros > 0 {
                        let incStr = YubraeTheme.masked(
                            YubraeTheme.formatPlainSignedAmount(
                                group.incomeMicros,
                                currencyCode: baseCur,
                                baseCurrency: baseCur,
                                showSign: false
                            ),
                            isMasked: masked
                        )
                        Text("收 \(incStr)")
                            .font(.caption.monospacedDigit())
                            .foregroundColor(palette.textSecondary)
                            .lineLimit(1)
                            .fixedSize(horizontal: true, vertical: false)
                    }
                    if group.expenseMicros > 0 {
                        let expStr = YubraeTheme.masked(
                            YubraeTheme.formatPlainSignedAmount(
                                group.expenseMicros,
                                currencyCode: baseCur,
                                baseCurrency: baseCur,
                                showSign: false
                            ),
                            isMasked: masked
                        )
                        Text("支 \(expStr)")
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
                        let cat = tx.categoryId.flatMap { categoryById[$0] }
                        let parentCat = cat?.parentId.flatMap { categoryById[$0] }
                        let acc = accountById[tx.accountId]
                        let toAcc = tx.toAccountId.flatMap { accountById[$0] }

                        TransactionRowView(
                            tx: tx,
                            category: cat,
                            parentCategory: parentCat,
                            account: acc,
                            toAccount: toAcc,
                            refundedMicros: refundedByOrigId[tx.id] ?? 0,
                            baseCurrency: baseCur,
                            isMasked: masked,
                            palette: palette
                        )
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
                            Rectangle()
                                .fill(palette.outline.opacity(0.75))
                                .frame(height: 0.5)
                                .padding(.leading, 58)
                        }
                    }
                }
            }
        }
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

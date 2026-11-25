import Charts
import SwiftUI

private struct TrendBarEntry: Identifiable {
    let id: String
    let label: String
    let series: String
    let amount: Double
}

private enum StatsChartMode: Int, CaseIterable, Identifiable {
    case cashflow = 0
    case netWorth = 1

    var id: Int { rawValue }
    var title: String {
        switch self {
        case .cashflow: return "收支趋势"
        case .netWorth: return "净资产走势"
        }
    }
}

public struct StatsView: View {
    @ObservedObject public var store: LedgerStore
    @Environment(\.colorScheme) private var colorScheme

    @State private var granularity: StatsGranularity = .monthly
    @State private var anchorDate: Date = Date()
    @State private var customStartDate: Date = Calendar.current.date(byAdding: .day, value: -14, to: Date()) ?? Date()
    @State private var customEndDate: Date = Date()
    @State private var breakdownType: String = TransactionType.EXPENSE
    @State private var chartMode: StatsChartMode = .cashflow
    @State private var showAllLedgers: Bool = false
    @State private var highlightedCategoryId: String?
    @State private var showAddBudgetSheet = false

    public init(store: LedgerStore) {
        self.store = store
    }

    private var palette: YubraePalette {
        YubraeTheme.palette(for: store.settings, colorScheme: colorScheme)
    }

    private var activeLedgerId: Int64 {
        showAllLedgers ? 0 : (store.activeLedger?.id ?? store.settings.activeLedgerId)
    }

    private var baseCurrency: String {
        store.settings.baseCurrency
    }

    private var anchorMillis: Int64 {
        Int64((anchorDate.timeIntervalSince1970 * 1000.0).rounded())
    }

    private var activeRangeMillis: (Int64, Int64) {
        let cal = Calendar.current
        switch granularity {
        case .weekly:
            var mondayCal = Calendar(identifier: .gregorian)
            mondayCal.firstWeekday = 2
            let startOfDay = mondayCal.startOfDay(for: anchorDate)
            let weekday = mondayCal.component(.weekday, from: startOfDay)
            let daysFromMonday = (weekday + 5) % 7
            let monday = mondayCal.date(byAdding: .day, value: -daysFromMonday, to: startOfDay) ?? startOfDay
            let nextMonday = mondayCal.date(byAdding: .day, value: 7, to: monday) ?? monday
            return (
                Int64((monday.timeIntervalSince1970 * 1000.0).rounded()),
                Int64((nextMonday.timeIntervalSince1970 * 1000.0).rounded()) - 1
            )
        case .monthly:
            let comps = cal.dateComponents([.year, .month], from: anchorDate)
            return store.monthRangeMillis(year: comps.year ?? 2026, month: comps.month ?? 1)
        case .yearly:
            let year = cal.component(.year, from: anchorDate)
            return store.yearRangeMillis(year: year)
        case .custom:
            let s = cal.startOfDay(for: min(customStartDate, customEndDate))
            let e = cal.date(byAdding: .day, value: 1, to: cal.startOfDay(for: max(customStartDate, customEndDate))) ?? max(customStartDate, customEndDate)
            return (
                Int64((s.timeIntervalSince1970 * 1000.0).rounded()),
                Int64((e.timeIntervalSince1970 * 1000.0).rounded()) - 1
            )
        }
    }

    private var periodTitle: String {
        let cal = Calendar.current
        switch granularity {
        case .weekly:
            let r = activeRangeMillis
            return "\(YubraeTheme.formatDate(r.0, pattern: "M/d")) - \(YubraeTheme.formatDate(r.1, pattern: "M/d"))"
        case .monthly:
            let comps = cal.dateComponents([.year, .month], from: anchorDate)
            return "\(comps.year ?? 2026)年\(comps.month ?? 1)月"
        case .yearly:
            return "\(cal.component(.year, from: anchorDate))年"
        case .custom:
            let r = activeRangeMillis
            return "\(YubraeTheme.formatDate(r.0, pattern: "yyyy/M/d")) - \(YubraeTheme.formatDate(r.1, pattern: "M/d"))"
        }
    }

    private var trendBuckets: [TrendBucketPoint] {
        store.computeTrendBuckets(
            granularity: granularity.rawValue,
            anchorDateMillis: anchorMillis,
            customStartMillis: Int64((customStartDate.timeIntervalSince1970 * 1000.0).rounded()),
            customEndMillis: Int64((customEndDate.timeIntervalSince1970 * 1000.0).rounded()),
            ledgerId: activeLedgerId,
            displayCurrency: baseCurrency
        )
    }

    private var netWorthHistory: [NetWorthPoint] {
        store.computeNetWorthHistory(monthsBack: 6, displayCurrency: baseCurrency, nowMillis: anchorMillis)
    }

    private var categoryStats: [CategoryStat] {
        let r = activeRangeMillis
        return store.computeCategoryStats(
            startMillis: r.0,
            endMillis: r.1,
            type: breakdownType,
            ledgerId: activeLedgerId,
            displayCurrency: baseCurrency
        )
    }

    private var tagStats: [TagStat] {
        let r = activeRangeMillis
        return store.computeTagStats(
            startMillis: r.0,
            endMillis: r.1,
            type: breakdownType,
            ledgerId: activeLedgerId,
            displayCurrency: baseCurrency
        )
    }

    private var periodExpenseMicros: Int64 {
        let r = activeRangeMillis
        return store.computeCategoryStats(
            startMillis: r.0,
            endMillis: r.1,
            type: TransactionType.EXPENSE,
            ledgerId: activeLedgerId,
            displayCurrency: baseCurrency
        ).reduce(0) { $0 + $1.totalBaseMicros }
    }

    private var periodIncomeMicros: Int64 {
        let r = activeRangeMillis
        return store.computeCategoryStats(
            startMillis: r.0,
            endMillis: r.1,
            type: TransactionType.INCOME,
            ledgerId: activeLedgerId,
            displayCurrency: baseCurrency
        ).reduce(0) { $0 + $1.totalBaseMicros }
    }

    private var budgetProgressList: [BudgetProgress] {
        let comps = Calendar.current.dateComponents([.year, .month], from: anchorDate)
        return store.computeBudgetProgress(
            year: comps.year ?? 2026,
            month: comps.month ?? 1,
            ledgerId: activeLedgerId
        )
    }

    public var body: some View {
        NavigationStack {
            ScrollView {
                LazyVStack(spacing: 14) {
                    dimensionControlsCard
                    summaryMetricsCard
                    trendChartCard
                    categoryBreakdownCard
                    tagRankingCard
                    budgetExecutionCard
                }
                .padding(.horizontal, 16)
                .padding(.top, 10)
                .padding(.bottom, 40)
            }
            .background(palette.background.ignoresSafeArea())
            .navigationTitle("统计")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Menu {
                        Button {
                            showAllLedgers = false
                        } label: {
                            if !showAllLedgers {
                                Label(store.activeLedger?.name ?? "当前账本", systemImage: "checkmark")
                            } else {
                                Text(store.activeLedger?.name ?? "当前账本")
                            }
                        }
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
                        HStack(spacing: 4) {
                            Text(showAllLedgers ? "全部账本" : (store.activeLedger?.name ?? "账本"))
                                .font(.caption.weight(.semibold))
                            Image(systemName: "chevron.down")
                                .font(.caption2)
                        }
                        .foregroundColor(palette.primary)
                    }
                }
            }
            .sheet(isPresented: $showAddBudgetSheet) {
                QuickBudgetCreateSheet(store: store)
            }
        }
    }

    // MARK: - Controls & Summary

    private var dimensionControlsCard: some View {
        YubraeCard(palette: palette, padding: 14) {
            VStack(spacing: 12) {
                Picker("统计粒度", selection: $granularity) {
                    Text("周").tag(StatsGranularity.weekly)
                    Text("月").tag(StatsGranularity.monthly)
                    Text("年").tag(StatsGranularity.yearly)
                    Text("自定义").tag(StatsGranularity.custom)
                }
                .pickerStyle(.segmented)

                if granularity == .custom {
                    HStack(spacing: 10) {
                        DatePicker("开始", selection: $customStartDate, displayedComponents: .date)
                            .labelsHidden()
                        Text("至")
                            .font(.caption)
                            .foregroundColor(palette.textSecondary)
                        DatePicker("结束", selection: $customEndDate, displayedComponents: .date)
                            .labelsHidden()
                    }
                } else {
                    HStack {
                        Button { shiftAnchor(by: -1) } label: {
                            Image(systemName: "chevron.left.circle.fill")
                                .font(.title3)
                                .foregroundColor(palette.primary)
                        }
                        Spacer()
                        Text(periodTitle)
                            .font(.subheadline.weight(.bold))
                            .foregroundColor(palette.textPrimary)
                        Spacer()
                        Button { shiftAnchor(by: 1) } label: {
                            Image(systemName: "chevron.right.circle.fill")
                                .font(.title3)
                                .foregroundColor(palette.primary)
                        }
                    }
                }
            }
        }
    }

    private var summaryMetricsCard: some View {
        let masked = store.settings.privacyMaskEnabled
        let expStr = YubraeTheme.masked(MoneyAmount.format(periodExpenseMicros, currencyCode: baseCurrency), isMasked: masked)
        let incStr = YubraeTheme.masked(MoneyAmount.format(periodIncomeMicros, currencyCode: baseCurrency), isMasked: masked)
        let netStr = YubraeTheme.masked(MoneyAmount.format(periodIncomeMicros - periodExpenseMicros, currencyCode: baseCurrency, showSign: true), isMasked: masked)

        return HStack(spacing: 10) {
            metricBox(title: "总支出", value: expStr, accent: palette.expenseColor)
            metricBox(title: "总收入", value: incStr, accent: palette.incomeColor)
            metricBox(title: "结余", value: netStr, accent: palette.primary)
        }
    }

    private func metricBox(title: String, value: String, accent: Color) -> some View {
        YubraeCard(palette: palette, padding: 12, cornerRadius: 14) {
            VStack(alignment: .leading, spacing: 4) {
                Text(title)
                    .font(.caption)
                    .foregroundColor(palette.textSecondary)
                Text(value)
                    .font(.subheadline.weight(.bold).monospacedDigit())
                    .foregroundColor(accent)
                    .lineLimit(1)
                    .minimumScaleFactor(0.65)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    // MARK: - Swift Charts (100% iOS 16+ BarMark / LineMark / AreaMark)

    private var trendChartCard: some View {
        YubraeCard(palette: palette) {
            VStack(alignment: .leading, spacing: 12) {
                HStack {
                    Text(chartMode == .cashflow ? "收支趋势" : "近 6 月净资产走势")
                        .font(.subheadline.weight(.bold))
                        .foregroundColor(palette.textPrimary)
                    Spacer()
                    Picker("图表模式", selection: $chartMode) {
                        ForEach(StatsChartMode.allCases) { m in
                            Text(m.title).tag(m)
                        }
                    }
                    .pickerStyle(.segmented)
                    .frame(width: 170)
                }

                if chartMode == .cashflow {
                    cashflowBarChart
                } else {
                    netWorthLineChart
                }
            }
        }
    }

    private var cashflowBarChart: some View {
        let entries: [TrendBarEntry] = trendBuckets.flatMap { bucket -> [TrendBarEntry] in
            [
                TrendBarEntry(
                    id: "\(bucket.id)-exp",
                    label: bucket.label,
                    series: "支出",
                    amount: Double(bucket.expenseBaseMicros) / Double(MoneyAmount.amountScale)
                ),
                TrendBarEntry(
                    id: "\(bucket.id)-inc",
                    label: bucket.label,
                    series: "收入",
                    amount: Double(bucket.incomeBaseMicros) / Double(MoneyAmount.amountScale)
                )
            ]
        }

        let hasAnyData = entries.contains { $0.amount > 0 }
        return Group {
            if !hasAnyData {
                YubraeEmptyStateView(
                    icon: "chart.bar",
                    title: "暂无趋势数据",
                    subtitle: "该统计区间内尚无已确认的收支记录。",
                    palette: palette
                )
            } else {
                Chart(entries) { entry in
                    BarMark(
                        x: .value("周期", entry.label),
                        y: .value("金额", entry.amount)
                    )
                    .cornerRadius(4)
                    .foregroundStyle(by: .value("类型", entry.series))
                    .position(by: .value("类型", entry.series))
                }
                .chartForegroundStyleScale([
                    "支出": palette.expenseColor,
                    "收入": palette.incomeColor
                ])
                .chartLegend(position: .top, alignment: .trailing)
                .chartYAxis {
                    AxisMarks(position: .leading)
                }
                .frame(height: 200)
            }
        }
    }

    private struct NetWorthChartItem: Identifiable {
        let id: Int64
        let label: String
        let value: Double
    }

    private var netWorthLineChart: some View {
        let items = netWorthHistory.map { point in
            NetWorthChartItem(
                id: point.id,
                label: point.label,
                value: Double(point.netWorthMicros) / Double(MoneyAmount.amountScale)
            )
        }
        return Group {
            if items.isEmpty {
                YubraeEmptyStateView(
                    icon: "chart.xyaxis.line",
                    title: "暂无净资产曲线",
                    subtitle: "添加账户余额或记账后即可生成净资产变化曲线。",
                    palette: palette
                )
            } else {
                Chart(items) { item in
                    AreaMark(
                        x: .value("月份", item.label),
                        y: .value("净资产", item.value)
                    )
                    .interpolationMethod(.catmullRom)
                    .foregroundStyle(
                        LinearGradient(
                            colors: [palette.primary.opacity(0.30), palette.primary.opacity(0.02)],
                            startPoint: .top,
                            endPoint: .bottom
                        )
                    )

                    LineMark(
                        x: .value("月份", item.label),
                        y: .value("净资产", item.value)
                    )
                    .interpolationMethod(.catmullRom)
                    .foregroundStyle(palette.primary)
                    .lineStyle(StrokeStyle(lineWidth: 2.5))

                    PointMark(
                        x: .value("月份", item.label),
                        y: .value("净资产", item.value)
                    )
                    .foregroundStyle(palette.primary)
                }
                .chartYAxis {
                    AxisMarks(position: .leading)
                }
                .frame(height: 200)
            }
        }
    }

    // MARK: - Category Donut Chart (iOS 16 Pure SwiftUI Ring) & Ranking

    private var categoryBreakdownCard: some View {
        let stats = categoryStats
        let masked = store.settings.privacyMaskEnabled
        let grandTotal = stats.reduce(Int64(0)) { $0 + $1.totalBaseMicros }
        let selectedItem = stats.first(where: { $0.id == highlightedCategoryId })

        return YubraeCard(palette: palette) {
            VStack(alignment: .leading, spacing: 14) {
                HStack {
                    Text("分类构成")
                        .font(.subheadline.weight(.bold))
                        .foregroundColor(palette.textPrimary)
                    Spacer()
                    Picker("分类类型", selection: $breakdownType) {
                        Text("支出").tag(TransactionType.EXPENSE)
                        Text("收入").tag(TransactionType.INCOME)
                    }
                    .pickerStyle(.segmented)
                    .frame(width: 132)
                }

                if stats.isEmpty {
                    YubraeEmptyStateView(
                        icon: "chart.pie",
                        title: "暂无分类统计",
                        subtitle: "切换统计周期或记入一笔交易即可查看分类占比。",
                        palette: palette
                    )
                } else {
                    HStack(spacing: 16) {
                        YubraeDonutChart(
                            stats: stats,
                            highlightedId: highlightedCategoryId,
                            centerTopText: selectedItem?.categoryName ?? (breakdownType == TransactionType.EXPENSE ? "总支出" : "总收入"),
                            centerValueText: YubraeTheme.masked(
                                MoneyAmount.format(selectedItem?.totalBaseMicros ?? grandTotal, currencyCode: baseCurrency),
                                isMasked: masked
                            ),
                            palette: palette
                        )
                        .frame(width: 140, height: 140)

                        VStack(alignment: .leading, spacing: 8) {
                            ForEach(stats.prefix(5)) { stat in
                                Button {
                                    highlightedCategoryId = (highlightedCategoryId == stat.id) ? nil : stat.id
                                } label: {
                                    HStack(spacing: 6) {
                                        Circle()
                                            .fill(Color(argbHex: stat.colorHex))
                                            .frame(width: 8, height: 8)
                                        Text(stat.categoryName)
                                            .font(.caption.weight(highlightedCategoryId == stat.id ? .bold : .medium))
                                            .foregroundColor(palette.textPrimary)
                                            .lineLimit(1)
                                        Spacer()
                                        Text("\(Int((stat.percentage * 100).rounded()))%")
                                            .font(.caption.monospacedDigit())
                                            .foregroundColor(palette.textSecondary)
                                    }
                                }
                            }
                        }
                    }

                    Divider()

                    VStack(spacing: 10) {
                        ForEach(stats) { stat in
                            VStack(spacing: 6) {
                                HStack(spacing: 10) {
                                    YubraeCategoryIconBadge(iconKey: stat.icon, colorHex: stat.colorHex, size: 32)
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text(stat.categoryName)
                                            .font(.subheadline.weight(.medium))
                                            .foregroundColor(palette.textPrimary)
                                        Text("\(stat.count) 笔 · \(String(format: "%.1f%%", stat.percentage * 100))")
                                            .font(.caption2)
                                            .foregroundColor(palette.textSecondary)
                                    }
                                    Spacer()
                                    Text(YubraeTheme.masked(MoneyAmount.format(stat.totalBaseMicros, currencyCode: baseCurrency), isMasked: masked))
                                        .font(.subheadline.weight(.semibold).monospacedDigit())
                                        .foregroundColor(palette.textPrimary)
                                }
                                GeometryReader { geo in
                                    ZStack(alignment: .leading) {
                                        Capsule().fill(palette.surfaceSecondary)
                                        Capsule()
                                            .fill(Color(argbHex: stat.colorHex))
                                            .frame(width: geo.size.width * CGFloat(min(1.0, max(0.02, stat.percentage))))
                                    }
                                }
                                .frame(height: 5)
                            }
                            .contentShape(Rectangle())
                            .onTapGesture {
                                highlightedCategoryId = (highlightedCategoryId == stat.id) ? nil : stat.id
                            }
                        }
                    }
                }
            }
        }
    }

    // MARK: - Tag Ranking

    private var tagRankingCard: some View {
        let stats = tagStats
        let masked = store.settings.privacyMaskEnabled

        return YubraeCard(palette: palette) {
            VStack(alignment: .leading, spacing: 12) {
                Label("标签排行", systemImage: "tag.fill")
                    .font(.subheadline.weight(.bold))
                    .foregroundColor(palette.textPrimary)

                if stats.isEmpty {
                    Text("该周期内暂无带标签的账单，可在记账时为消费打上「外卖」「差旅」「刚需」等标签。")
                        .font(.caption)
                        .foregroundColor(palette.textTertiary)
                } else {
                    ForEach(stats.prefix(8)) { item in
                        VStack(spacing: 5) {
                            HStack {
                                Text("#\(item.tagName)")
                                    .font(.caption.weight(.semibold))
                                    .foregroundColor(Color(argbHex: item.colorHex))
                                    .padding(.horizontal, 8)
                                    .padding(.vertical, 3)
                                    .background(Capsule().fill(Color(argbHex: item.colorHex).opacity(0.14)))
                                Text("\(item.count) 笔")
                                    .font(.caption2)
                                    .foregroundColor(palette.textSecondary)
                                Spacer()
                                Text(YubraeTheme.masked(MoneyAmount.format(item.totalBaseMicros, currencyCode: baseCurrency), isMasked: masked))
                                    .font(.caption.weight(.bold).monospacedDigit())
                                    .foregroundColor(palette.textPrimary)
                            }
                            GeometryReader { geo in
                                ZStack(alignment: .leading) {
                                    Capsule().fill(palette.surfaceSecondary)
                                    Capsule()
                                        .fill(Color(argbHex: item.colorHex))
                                        .frame(width: geo.size.width * CGFloat(min(1.0, max(0.02, item.percentage))))
                                }
                            }
                            .frame(height: 5)
                        }
                    }
                }
            }
        }
    }

    // MARK: - Budget Execution

    private var budgetExecutionCard: some View {
        let list = budgetProgressList
        let masked = store.settings.privacyMaskEnabled

        return YubraeCard(palette: palette) {
            VStack(alignment: .leading, spacing: 12) {
                HStack {
                    Label("月度预算执行", systemImage: "gauge.medium")
                        .font(.subheadline.weight(.bold))
                        .foregroundColor(palette.textPrimary)
                    Spacer()
                    Button("+ 设预算") {
                        showAddBudgetSheet = true
                    }
                    .font(.caption.weight(.semibold))
                    .foregroundColor(palette.primary)
                }

                if list.isEmpty {
                    Text("尚未设置预算，点击右上角「+ 设预算」即可监控总预算或单一分类限额。")
                        .font(.caption)
                        .foregroundColor(palette.textTertiary)
                } else {
                    ForEach(list) { item in
                        let ratio = min(1.0, max(0.0, item.progressRatio))
                        let spentText = YubraeTheme.masked(MoneyAmount.format(item.spentInBudgetCurrencyMicros, currencyCode: item.budget.currencyCode), isMasked: masked)
                        let limitText = YubraeTheme.masked(MoneyAmount.format(item.limitInBudgetCurrencyMicros, currencyCode: item.budget.currencyCode), isMasked: masked)
                        let dailyText = YubraeTheme.masked(MoneyAmount.format(item.dailyAvailableMicros, currencyCode: item.budget.currencyCode), isMasked: masked)

                        VStack(alignment: .leading, spacing: 6) {
                            HStack {
                                Text(item.categoryName)
                                    .font(.subheadline.weight(.semibold))
                                    .foregroundColor(palette.textPrimary)
                                if item.overBudget {
                                    Text("已超支")
                                        .font(.caption2.weight(.bold))
                                        .foregroundColor(palette.dangerColor)
                                }
                                Spacer()
                                Text("\(spentText) / \(limitText)")
                                    .font(.caption.weight(.semibold).monospacedDigit())
                                    .foregroundColor(item.overBudget ? palette.dangerColor : palette.textSecondary)
                            }
                            GeometryReader { geo in
                                ZStack(alignment: .leading) {
                                    Capsule().fill(palette.surfaceSecondary)
                                    Capsule()
                                        .fill(item.overBudget ? palette.dangerColor : palette.primary)
                                        .frame(width: geo.size.width * CGFloat(ratio))
                                }
                            }
                            .frame(height: 7)
                            HStack {
                                Text("剩余 \(item.remainingDays) 天 · 日均可用 \(dailyText)")
                                    .font(.caption2)
                                    .foregroundColor(palette.textTertiary)
                                Spacer()
                                Button(role: .destructive) {
                                    store.deleteBudget(id: item.budget.id)
                                } label: {
                                    Text("删除")
                                        .font(.caption2)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private func shiftAnchor(by delta: Int) {
        let cal = Calendar.current
        switch granularity {
        case .weekly:
            anchorDate = cal.date(byAdding: .day, value: delta * 7, to: anchorDate) ?? anchorDate
        case .monthly:
            anchorDate = cal.date(byAdding: .month, value: delta, to: anchorDate) ?? anchorDate
        case .yearly:
            anchorDate = cal.date(byAdding: .year, value: delta, to: anchorDate) ?? anchorDate
        case .custom:
            break
        }
    }
}

// MARK: - Custom iOS 16 Pure SwiftUI Donut Chart

private struct DonutSlice: Identifiable {
    var id: String { stat.id }
    let stat: CategoryStat
    let start: CGFloat
    let end: CGFloat
}

private struct YubraeDonutChart: View {
    let stats: [CategoryStat]
    let highlightedId: String?
    let centerTopText: String
    let centerValueText: String
    let palette: YubraePalette

    private var slices: [DonutSlice] {
        var cursor: CGFloat = 0
        var out: [DonutSlice] = []
        for stat in stats {
            let pct = CGFloat(max(0, stat.percentage))
            let end = min(1.0, cursor + pct)
            out.append(DonutSlice(stat: stat, start: cursor, end: end))
            cursor = end
        }
        return out
    }

    var body: some View {
        ZStack {
            Circle()
                .stroke(palette.surfaceSecondary, lineWidth: 20)

            ForEach(slices) { slice in
                let isSelected = highlightedId == slice.stat.id
                let gap: CGFloat = slices.count > 1 ? 0.004 : 0
                let from = min(slice.end, slice.start + gap)
                let to = max(from, slice.end - gap)
                Circle()
                    .trim(from: from, to: to)
                    .stroke(
                        Color(argbHex: slice.stat.colorHex),
                        style: StrokeStyle(lineWidth: isSelected ? 24 : 20, lineCap: .butt)
                    )
                    .rotationEffect(.degrees(-90))
                    .opacity(highlightedId == nil || isSelected ? 1.0 : 0.45)
            }

            VStack(spacing: 2) {
                Text(centerTopText)
                    .font(.caption2)
                    .foregroundColor(palette.textSecondary)
                    .lineLimit(1)
                Text(centerValueText)
                    .font(.caption.weight(.bold).monospacedDigit())
                    .foregroundColor(palette.textPrimary)
                    .lineLimit(1)
                    .minimumScaleFactor(0.65)
            }
            .padding(.horizontal, 24)
        }
    }
}

// MARK: - Quick Budget Creation Sheet

private struct QuickBudgetCreateSheet: View {
    @ObservedObject var store: LedgerStore
    @Environment(\.dismiss) private var dismiss

    @State private var name: String = ""
    @State private var categoryId: Int64 = 0
    @State private var period: String = "MONTHLY"
    @State private var limitText: String = ""
    @State private var rolloverEnabled: Bool = false

    var body: some View {
        NavigationStack {
            Form {
                TextField("预算名称（留空自动命名）", text: $name)
                Picker("适用范围", selection: $categoryId) {
                    Text("全部分类（总预算）").tag(Int64(0))
                    ForEach(store.parentCategories(for: TransactionType.EXPENSE)) { cat in
                        Text(cat.name).tag(cat.id)
                    }
                }
                Picker("预算周期", selection: $period) {
                    Text("月度预算").tag("MONTHLY")
                    Text("年度预算").tag("YEARLY")
                }
                TextField("预算限额 (\(store.settings.baseCurrency))", text: $limitText)
                    .keyboardType(.decimalPad)
                if period == "MONTHLY" {
                    Toggle("结余结转到下月", isOn: $rolloverEnabled)
                }
            }
            .navigationTitle("新建预算")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("取消") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("保存") {
                        guard let micros = MoneyAmount.parseToMicros(limitText), micros > 0 else { return }
                        let targetCatId: Int64? = categoryId > 0 ? categoryId : nil
                        let resolvedName: String
                        if !name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                            resolvedName = name.trimmingCharacters(in: .whitespacesAndNewlines)
                        } else if let cid = targetCatId, let cat = store.category(byId: cid) {
                            resolvedName = "\(cat.name)预算"
                        } else {
                            resolvedName = period == "YEARLY" ? "年度总预算" : "月度总预算"
                        }
                        let dto = BudgetDto(
                            name: resolvedName,
                            categoryId: targetCatId,
                            period: period,
                            limitAmountMicros: micros,
                            currencyCode: store.settings.baseCurrency,
                            enabled: true,
                            ledgerId: store.activeLedger?.id ?? store.settings.activeLedgerId,
                            rolloverEnabled: rolloverEnabled
                        )
                        _ = store.saveBudget(dto)
                        store.toastMessage = "已保存预算「\(resolvedName)」"
                        dismiss()
                    }
                    .disabled((MoneyAmount.parseToMicros(limitText) ?? 0) <= 0)
                }
            }
        }
        .presentationDetents([.medium])
    }
}

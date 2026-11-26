import SwiftUI
import UIKit

// MARK: - Period Calculation & Formatting Helpers

public enum YubraePeriodHelper {
    public static func mondayCalendar(timeZone: TimeZone = .current) -> Calendar {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = timeZone
        cal.firstWeekday = 2 // Monday
        cal.minimumDaysInFirstWeek = 4
        return cal
    }

    public static func weekBounds(
        for date: Date,
        calendar: Calendar = mondayCalendar()
    ) -> (start: Date, end: Date) {
        let startOfDay = calendar.startOfDay(for: date)
        let weekday = calendar.component(.weekday, from: startOfDay) // 1 = Sun, 2 = Mon ... 7 = Sat
        let daysFromMonday = (weekday + 5) % 7
        let rawMonday = calendar.date(byAdding: .day, value: -daysFromMonday, to: startOfDay) ?? startOfDay
        let monday = calendar.startOfDay(for: rawMonday)
        let rawNextMonday = calendar.date(byAdding: .day, value: 7, to: monday) ?? monday
        let nextMonday = calendar.startOfDay(for: rawNextMonday)
        let sundayEnd = nextMonday.addingTimeInterval(-0.001)
        return (start: monday, end: max(monday, sundayEnd))
    }

    public static func yearGridRange(for anchorYear: Int) -> [Int] {
        let safeYear = max(1, anchorYear)
        let maxStart = Int.max - 11
        let rawStart = (safeYear / 12) * 12
        let startYear = min(maxStart, max(1, rawStart))
        return Array(startYear..<(startYear + 12))
    }

    public static func formatWeekRangeText(
        for date: Date,
        calendar: Calendar = mondayCalendar()
    ) -> String {
        let bounds = weekBounds(for: date, calendar: calendar)
        let startFormatter = DateFormatter()
        startFormatter.calendar = calendar
        startFormatter.timeZone = calendar.timeZone
        startFormatter.locale = Locale(identifier: "en_US_POSIX")
        startFormatter.dateFormat = "yyyy/MM/dd"

        let endFormatter = DateFormatter()
        endFormatter.calendar = calendar
        endFormatter.timeZone = calendar.timeZone
        endFormatter.locale = Locale(identifier: "en_US_POSIX")
        endFormatter.dateFormat = "MM/dd"

        return "\(startFormatter.string(from: bounds.start)) - \(endFormatter.string(from: bounds.end))"
    }

    public static func formatMonthSummaryCopyText(
        title: String,
        currency: String,
        expenseText: String,
        incomeText: String,
        balanceText: String
    ) -> String {
        let cleanTitle = title.trimmingCharacters(in: .whitespacesAndNewlines)
        let cleanCurrency = currency.trimmingCharacters(in: .whitespacesAndNewlines)
        let header = cleanCurrency.isEmpty
            ? "\(cleanTitle)收支汇总"
            : "\(cleanTitle)收支汇总 (\(cleanCurrency))"
        return [
            header,
            "本月支出：\(expenseText)",
            "本月收入：\(incomeText)",
            "月度结余：\(balanceText)"
        ].joined(separator: "\n")
    }
}

// MARK: - Year-Month Picker Sheet

public struct YubraeYearMonthPickerSheet: View {
    public let initialYear: Int
    public let initialMonth: Int
    public let palette: YubraePalette
    public let onSelect: (Int, Int) -> Void

    @Environment(\.dismiss) private var dismiss

    @State private var displayYear: Int
    @State private var showYearGrid: Bool = false
    @State private var yearGridAnchor: Int

    public init(
        initialYear: Int,
        initialMonth: Int,
        palette: YubraePalette,
        onSelect: @escaping (Int, Int) -> Void
    ) {
        let safeYear = max(1, initialYear)
        let safeMonth = min(12, max(1, initialMonth))
        self.initialYear = safeYear
        self.initialMonth = safeMonth
        self.palette = palette
        self.onSelect = onSelect
        _displayYear = State(initialValue: safeYear)
        _yearGridAnchor = State(initialValue: safeYear)
    }

    public var body: some View {
        let nowComps = Calendar.current.dateComponents([.year, .month], from: Date())
        let currentYear = nowComps.year ?? initialYear
        let currentMonth = nowComps.month ?? initialMonth
        let gridYears = YubraePeriodHelper.yearGridRange(for: yearGridAnchor)
        let firstGridYear = gridYears.first ?? 1
        let lastGridYear = gridYears.last ?? 12

        return NavigationStack {
            ScrollView {
                VStack(spacing: 14) {
                    YubraeCard(palette: palette, padding: 16) {
                        VStack(spacing: 14) {
                            HStack(spacing: 8) {
                                Button {
                                    if showYearGrid {
                                        yearGridAnchor = max(1, firstGridYear - 12)
                                    } else {
                                        displayYear = max(1, displayYear - 1)
                                    }
                                } label: {
                                    Image(systemName: "chevron.left")
                                        .font(.subheadline.weight(.bold))
                                        .foregroundColor(palette.primary)
                                        .frame(width: 34, height: 34)
                                        .background(Circle().fill(palette.accentSoftFill))
                                }
                                .buttonStyle(.plain)
                                .disabled(showYearGrid ? firstGridYear <= 1 : displayYear <= 1)
                                .opacity((showYearGrid ? firstGridYear <= 1 : displayYear <= 1) ? 0.4 : 1.0)

                                Spacer(minLength: 6)

                                Button {
                                    if showYearGrid {
                                        showYearGrid = false
                                    } else {
                                        yearGridAnchor = displayYear
                                        showYearGrid = true
                                    }
                                } label: {
                                    HStack(spacing: 5) {
                                        Text(showYearGrid ? "\(String(firstGridYear)) - \(String(lastGridYear))年" : "\(String(displayYear))年")
                                            .font(.headline.weight(.bold))
                                            .monospacedDigit()
                                        Image(systemName: showYearGrid ? "chevron.up" : "chevron.down")
                                            .font(.caption.weight(.bold))
                                    }
                                    .foregroundColor(palette.textPrimary)
                                    .padding(.horizontal, 14)
                                    .padding(.vertical, 7)
                                    .background(Capsule().fill(palette.surfaceSecondary))
                                    .overlay(Capsule().strokeBorder(palette.outline, lineWidth: 0.7))
                                }
                                .buttonStyle(.plain)

                                Spacer(minLength: 6)

                                Button {
                                    if showYearGrid {
                                        yearGridAnchor = firstGridYear + 12
                                    } else {
                                        displayYear += 1
                                    }
                                } label: {
                                    Image(systemName: "chevron.right")
                                        .font(.subheadline.weight(.bold))
                                        .foregroundColor(palette.primary)
                                        .frame(width: 34, height: 34)
                                        .background(Circle().fill(palette.accentSoftFill))
                                }
                                .buttonStyle(.plain)
                            }

                            if showYearGrid {
                                LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 10), count: 3), spacing: 10) {
                                    ForEach(gridYears, id: \.self) { year in
                                        let isSelected = year == displayYear
                                        let isCurrent = year == currentYear
                                        Button {
                                            displayYear = year
                                            showYearGrid = false
                                        } label: {
                                            VStack(spacing: 2) {
                                                Text("\(String(year))年")
                                                    .font(.subheadline.weight(isSelected || isCurrent ? .bold : .medium))
                                                    .monospacedDigit()
                                                    .lineLimit(1)
                                                    .minimumScaleFactor(0.85)
                                                if isCurrent {
                                                    Text("今年")
                                                        .font(.system(size: 10, weight: .semibold))
                                                        .opacity(isSelected ? 0.9 : 1.0)
                                                }
                                            }
                                            .foregroundColor(isSelected ? palette.onPrimary : (isCurrent ? palette.primary : palette.textPrimary))
                                            .frame(maxWidth: .infinity)
                                            .frame(height: 50)
                                            .background(
                                                RoundedRectangle(cornerRadius: 12, style: .continuous)
                                                    .fill(isSelected ? palette.primary : (isCurrent ? palette.accentSoftFill : palette.surfaceSecondary))
                                            )
                                            .overlay(
                                                RoundedRectangle(cornerRadius: 12, style: .continuous)
                                                    .strokeBorder(isCurrent && !isSelected ? palette.primary.opacity(0.65) : Color.clear, lineWidth: 1)
                                            )
                                        }
                                        .buttonStyle(.plain)
                                    }
                                }
                            } else {
                                LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 10), count: 4), spacing: 10) {
                                    ForEach(1...12, id: \.self) { month in
                                        let isSelected = displayYear == initialYear && month == initialMonth
                                        let isCurrentMonth = displayYear == currentYear && month == currentMonth
                                        Button {
                                            onSelect(displayYear, month)
                                            dismiss()
                                        } label: {
                                            VStack(spacing: 2) {
                                                Text("\(month)月")
                                                    .font(.subheadline.weight(isSelected || isCurrentMonth ? .bold : .medium))
                                                    .monospacedDigit()
                                                    .lineLimit(1)
                                                if isCurrentMonth {
                                                    Text("本月")
                                                        .font(.system(size: 10, weight: .semibold))
                                                        .opacity(isSelected ? 0.9 : 1.0)
                                                }
                                            }
                                            .foregroundColor(isSelected ? palette.onPrimary : (isCurrentMonth ? palette.primary : palette.textPrimary))
                                            .frame(maxWidth: .infinity)
                                            .frame(height: 50)
                                            .background(
                                                RoundedRectangle(cornerRadius: 12, style: .continuous)
                                                    .fill(isSelected ? palette.primary : (isCurrentMonth ? palette.accentSoftFill : palette.surfaceSecondary))
                                            )
                                            .overlay(
                                                RoundedRectangle(cornerRadius: 12, style: .continuous)
                                                    .strokeBorder(isCurrentMonth && !isSelected ? palette.primary.opacity(0.65) : Color.clear, lineWidth: 1)
                                            )
                                        }
                                        .buttonStyle(.plain)
                                    }
                                }
                            }
                        }
                    }

                    HStack(spacing: 12) {
                        Button {
                            dismiss()
                        } label: {
                            Text("关闭")
                                .font(.subheadline.weight(.semibold))
                                .foregroundColor(palette.textPrimary)
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 12)
                                .background(
                                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                                        .fill(palette.surface)
                                )
                                .overlay(
                                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                                        .strokeBorder(palette.outline, lineWidth: 0.8)
                                )
                        }
                        .buttonStyle(.plain)

                        Button {
                            onSelect(currentYear, currentMonth)
                            dismiss()
                        } label: {
                            Label("回到本月", systemImage: "calendar.badge.clock")
                                .font(.subheadline.weight(.bold))
                                .foregroundColor(palette.onPrimary)
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 12)
                                .background(
                                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                                        .fill(palette.primary)
                                )
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(16)
            }
            .background(palette.background.ignoresSafeArea())
            .navigationTitle("选择年月")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("关闭") { dismiss() }
                }
            }
        }
        .presentationDetents([.medium, .large])
    }
}

// MARK: - Year Picker Sheet

public struct YubraeYearPickerSheet: View {
    public let initialYear: Int
    public let palette: YubraePalette
    public let onSelect: (Int) -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var anchorYear: Int

    public init(
        initialYear: Int,
        palette: YubraePalette,
        onSelect: @escaping (Int) -> Void
    ) {
        let safeYear = max(1, initialYear)
        self.initialYear = safeYear
        self.palette = palette
        self.onSelect = onSelect
        _anchorYear = State(initialValue: safeYear)
    }

    public var body: some View {
        let currentYear = Calendar.current.component(.year, from: Date())
        let years = YubraePeriodHelper.yearGridRange(for: anchorYear)
        let firstYear = years.first ?? 1
        let lastYear = years.last ?? 12

        return NavigationStack {
            ScrollView {
                VStack(spacing: 14) {
                    YubraeCard(palette: palette, padding: 16) {
                        VStack(spacing: 14) {
                            HStack(spacing: 8) {
                                Button {
                                    anchorYear = max(1, firstYear - 12)
                                } label: {
                                    Image(systemName: "chevron.left")
                                        .font(.subheadline.weight(.bold))
                                        .foregroundColor(palette.primary)
                                        .frame(width: 34, height: 34)
                                        .background(Circle().fill(palette.accentSoftFill))
                                }
                                .buttonStyle(.plain)
                                .disabled(firstYear <= 1)
                                .opacity(firstYear <= 1 ? 0.4 : 1.0)

                                Spacer(minLength: 6)

                                Text("\(String(firstYear)) - \(String(lastYear))年")
                                    .font(.headline.weight(.bold))
                                    .monospacedDigit()
                                    .foregroundColor(palette.textPrimary)

                                Spacer(minLength: 6)

                                Button {
                                    anchorYear = firstYear + 12
                                } label: {
                                    Image(systemName: "chevron.right")
                                        .font(.subheadline.weight(.bold))
                                        .foregroundColor(palette.primary)
                                        .frame(width: 34, height: 34)
                                        .background(Circle().fill(palette.accentSoftFill))
                                }
                                .buttonStyle(.plain)
                            }

                            LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 10), count: 3), spacing: 10) {
                                ForEach(years, id: \.self) { year in
                                    let isSelected = year == initialYear
                                    let isCurrent = year == currentYear
                                    Button {
                                        onSelect(year)
                                        dismiss()
                                    } label: {
                                        VStack(spacing: 2) {
                                            Text("\(String(year))年")
                                                .font(.subheadline.weight(isSelected || isCurrent ? .bold : .medium))
                                                .monospacedDigit()
                                                .lineLimit(1)
                                                .minimumScaleFactor(0.85)
                                            if isCurrent {
                                                Text("今年")
                                                    .font(.system(size: 10, weight: .semibold))
                                                    .opacity(isSelected ? 0.9 : 1.0)
                                            }
                                        }
                                        .foregroundColor(isSelected ? palette.onPrimary : (isCurrent ? palette.primary : palette.textPrimary))
                                        .frame(maxWidth: .infinity)
                                        .frame(height: 52)
                                        .background(
                                            RoundedRectangle(cornerRadius: 12, style: .continuous)
                                                .fill(isSelected ? palette.primary : (isCurrent ? palette.accentSoftFill : palette.surfaceSecondary))
                                        )
                                        .overlay(
                                            RoundedRectangle(cornerRadius: 12, style: .continuous)
                                                .strokeBorder(isCurrent && !isSelected ? palette.primary.opacity(0.65) : Color.clear, lineWidth: 1)
                                        )
                                    }
                                    .buttonStyle(.plain)
                                }
                            }
                        }
                    }

                    HStack(spacing: 12) {
                        Button {
                            dismiss()
                        } label: {
                            Text("关闭")
                                .font(.subheadline.weight(.semibold))
                                .foregroundColor(palette.textPrimary)
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 12)
                                .background(
                                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                                        .fill(palette.surface)
                                )
                                .overlay(
                                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                                        .strokeBorder(palette.outline, lineWidth: 0.8)
                                )
                        }
                        .buttonStyle(.plain)

                        Button {
                            onSelect(currentYear)
                            dismiss()
                        } label: {
                            Label("回到今年", systemImage: "calendar")
                                .font(.subheadline.weight(.bold))
                                .foregroundColor(palette.onPrimary)
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 12)
                                .background(
                                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                                        .fill(palette.primary)
                                )
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(16)
            }
            .background(palette.background.ignoresSafeArea())
            .navigationTitle("选择年份")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("关闭") { dismiss() }
                }
            }
        }
        .presentationDetents([.medium, .large])
    }
}

// MARK: - Week Picker Sheet

public struct YubraeWeekPickerSheet: View {
    public let initialDate: Date
    public let palette: YubraePalette
    public let onSelect: (Date) -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var pickedDate: Date
    @State private var selectedDetent: PresentationDetent = .large

    public init(
        initialDate: Date,
        palette: YubraePalette,
        onSelect: @escaping (Date) -> Void
    ) {
        self.initialDate = initialDate
        self.palette = palette
        self.onSelect = onSelect
        _pickedDate = State(initialValue: initialDate)
    }

    public var body: some View {
        let weekPreview = YubraePeriodHelper.formatWeekRangeText(for: pickedDate)

        return NavigationStack {
            ScrollView {
                VStack(spacing: 14) {
                    YubraeCard(palette: palette, padding: 14) {
                        HStack(spacing: 10) {
                            Image(systemName: "calendar.day.timeline.left")
                                .font(.title3.weight(.semibold))
                                .foregroundColor(palette.primary)
                            VStack(alignment: .leading, spacing: 2) {
                                Text("所选自然周（周一至周日）")
                                    .font(.caption)
                                    .foregroundColor(palette.textSecondary)
                                Text(weekPreview)
                                    .font(.headline.weight(.bold).monospacedDigit())
                                    .foregroundColor(palette.primary)
                                    .lineLimit(1)
                                    .minimumScaleFactor(0.8)
                            }
                            Spacer(minLength: 0)
                        }
                    }

                    YubraeCard(palette: palette, padding: 8) {
                        DatePicker(
                            "选择周内日期",
                            selection: $pickedDate,
                            displayedComponents: .date
                        )
                        .datePickerStyle(.graphical)
                        .labelsHidden()
                        .tint(palette.primary)
                        .environment(\.calendar, YubraePeriodHelper.mondayCalendar())
                        .environment(\.locale, Locale(identifier: "zh_CN"))
                    }

                    HStack(spacing: 12) {
                        Button {
                            withAnimation(.easeInOut(duration: 0.18)) {
                                pickedDate = Date()
                            }
                        } label: {
                            Text("回到本周")
                                .font(.subheadline.weight(.semibold))
                                .foregroundColor(palette.primary)
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 12)
                                .background(
                                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                                        .fill(palette.accentSoftFill)
                                )
                        }
                        .buttonStyle(.plain)

                        Button {
                            onSelect(pickedDate)
                            dismiss()
                        } label: {
                            Text("确定选择该周")
                                .font(.subheadline.weight(.bold))
                                .foregroundColor(palette.onPrimary)
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 12)
                                .background(
                                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                                        .fill(palette.primary)
                                )
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(16)
            }
            .background(palette.background.ignoresSafeArea())
            .navigationTitle("选择自然周")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("关闭") { dismiss() }
                }
            }
        }
        .presentationDetents([.medium, .large], selection: $selectedDetent)
    }
}

// MARK: - Month Summary Detail Sheet

public struct YubraeMonthSummaryDetailSheet: View {
    public let title: String
    public let currency: String
    public let expenseText: String
    public let incomeText: String
    public let balanceText: String
    public var transactionCount: Int?
    public let palette: YubraePalette
    public var onCopied: ((String) -> Void)?

    @Environment(\.dismiss) private var dismiss
    @State private var didCopy = false

    public init(
        title: String,
        currency: String,
        expenseText: String,
        incomeText: String,
        balanceText: String,
        transactionCount: Int? = nil,
        palette: YubraePalette,
        onCopied: ((String) -> Void)? = nil
    ) {
        self.title = title
        self.currency = currency
        self.expenseText = expenseText
        self.incomeText = incomeText
        self.balanceText = balanceText
        self.transactionCount = transactionCount
        self.palette = palette
        self.onCopied = onCopied
    }

    public var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 14) {
                    HStack(spacing: 8) {
                        Text("\(title) · 收支明细汇总")
                            .font(.subheadline.weight(.bold))
                            .foregroundColor(palette.textPrimary)
                        Spacer(minLength: 6)
                        if let count = transactionCount {
                            Text("\(count) 笔")
                                .font(.caption.weight(.semibold))
                                .foregroundColor(palette.primary)
                                .padding(.horizontal, 9)
                                .padding(.vertical, 4)
                                .background(Capsule().fill(palette.accentSoftFill))
                        }
                        Text(currency)
                            .font(.caption.weight(.bold))
                            .foregroundColor(palette.textSecondary)
                            .padding(.horizontal, 9)
                            .padding(.vertical, 4)
                            .background(Capsule().fill(palette.surfaceSecondary))
                    }

                    YubraeCard(palette: palette, padding: 18) {
                        VStack(alignment: .leading, spacing: 16) {
                            summaryRow(
                                label: "本月支出",
                                value: expenseText,
                                accent: palette.expenseColor,
                                fontSize: 32
                            )
                            Divider().overlay(palette.outline)
                            summaryRow(
                                label: "本月收入",
                                value: incomeText,
                                accent: palette.incomeColor,
                                fontSize: 28
                            )
                            Divider().overlay(palette.outline)
                            summaryRow(
                                label: "月度结余",
                                value: balanceText,
                                accent: palette.primary,
                                fontSize: 28
                            )
                        }
                    }

                    HStack(spacing: 12) {
                        Button {
                            dismiss()
                        } label: {
                            Text("关闭")
                                .font(.subheadline.weight(.semibold))
                                .foregroundColor(palette.textPrimary)
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 13)
                                .background(
                                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                                        .fill(palette.surface)
                                )
                                .overlay(
                                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                                        .strokeBorder(palette.outline, lineWidth: 0.8)
                                )
                        }
                        .buttonStyle(.plain)

                        Button {
                            let text = YubraePeriodHelper.formatMonthSummaryCopyText(
                                title: title,
                                currency: currency,
                                expenseText: expenseText,
                                incomeText: incomeText,
                                balanceText: balanceText
                            )
                            UIPasteboard.general.string = text
                            didCopy = true
                            onCopied?("已复制\(title)收支汇总")
                        } label: {
                            Label(didCopy ? "已复制到剪贴板" : "一键复制汇总", systemImage: didCopy ? "checkmark.circle.fill" : "doc.on.doc")
                                .font(.subheadline.weight(.bold))
                                .foregroundColor(palette.onPrimary)
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 13)
                                .background(
                                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                                        .fill(palette.primary)
                                )
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(16)
            }
            .background(palette.background.ignoresSafeArea())
            .navigationTitle("月度收支详情")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("关闭") { dismiss() }
                }
            }
        }
        .presentationDetents([.medium, .large])
    }

    private func summaryRow(
        label: String,
        value: String,
        accent: Color,
        fontSize: CGFloat
    ) -> some View {
        VStack(alignment: .leading, spacing: 5) {
            Text(label)
                .font(.caption.weight(.medium))
                .foregroundColor(palette.textSecondary)
            Text(value)
                .font(.system(size: fontSize, weight: .bold, design: .rounded).monospacedDigit())
                .foregroundColor(accent)
                .frame(maxWidth: .infinity, alignment: .leading)
                .lineLimit(1)
                .minimumScaleFactor(0.4)
        }
    }
}

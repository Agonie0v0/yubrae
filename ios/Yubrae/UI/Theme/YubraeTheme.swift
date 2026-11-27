import SwiftUI
import UIKit
import UniformTypeIdentifiers

// MARK: - Custom UTType & KLBK Backup FileDocument

public extension UTType {
    static let yubraeKlbk = UTType(exportedAs: "com.yubrae.app.klbk", conformingTo: .data)
}

public struct KlbkBackupDocument: FileDocument {
    public static var readableContentTypes: [UTType] { [.yubraeKlbk, .data] }
    public static var writableContentTypes: [UTType] { [.yubraeKlbk, .data] }

    public var data: Data

    public init(data: Data = Data()) {
        self.data = data
    }

    public init(configuration: ReadConfiguration) throws {
        guard let fileData = configuration.file.regularFileContents else {
            throw CocoaError(.fileReadCorruptFile)
        }
        self.data = fileData
    }

    public func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper {
        FileWrapper(regularFileWithContents: data)
    }
}

// MARK: - Color Hex Helper

public extension Color {
    init(argbHex: Int64) {
        let comps = colorComponents(fromARGBHex: argbHex)
        self.init(.sRGB, red: comps.red, green: comps.green, blue: comps.blue, opacity: comps.alpha)
    }
}

// MARK: - Theme Palette & Visual Tokens

public struct YubraePalette {
    public let style: ThemeStyle
    public let isDark: Bool
    public let primary: Color
    public let onPrimary: Color
    public let primaryContainer: Color
    public let onPrimaryContainer: Color
    public let background: Color
    public let surface: Color
    public let surfaceSecondary: Color
    public let outline: Color
    public let textPrimary: Color
    public let textSecondary: Color
    public let textTertiary: Color
    public let expenseColor: Color
    public let incomeColor: Color
    public let transferColor: Color
    public let warningColor: Color
    public let dangerColor: Color
    public let heroGradient: LinearGradient

    public var accentSoftFill: Color {
        isDark ? primary.opacity(0.22) : primaryContainer
    }
}

public enum YubraeTheme {
    public static func colorScheme(for mode: ThemeMode) -> ColorScheme? {
        switch mode {
        case .system: return nil
        case .light: return .light
        case .dark: return .dark
        }
    }

    public static func palette(for settings: AppSettings, colorScheme: ColorScheme) -> YubraePalette {
        let style = ThemeStyle.from(settings.themeStyle)
        let isDark = colorScheme == .dark
        let basePrimary = Color(argbHex: style.primaryColorHex)
        let baseContainer = Color(argbHex: style.containerColorHex)

        let primary: Color
        if isDark && style == .obsidianMono {
            primary = Color(argbHex: 0xFFE5E7EB)
        } else if isDark {
            primary = lightenedColor(fromARGBHex: style.primaryColorHex, factor: 0.20)
        } else {
            primary = basePrimary
        }

        let onPrimary: Color = (isDark && style == .obsidianMono) ? Color(argbHex: 0xFF111318) : .white

        let primaryContainer: Color = isDark
            ? primary.opacity(0.22)
            : baseContainer

        let onPrimaryContainer: Color = isDark
            ? Color.white.opacity(0.95)
            : Color(argbHex: style.primaryColorHex)

        let background: Color
        let surface: Color
        let surfaceSecondary: Color
        let outline: Color

        if isDark {
            if settings.oledPureBlack {
                background = .black
                surface = Color(argbHex: 0xFF101316)
                surfaceSecondary = Color(argbHex: 0xFF1A1F24)
                outline = Color.white.opacity(0.10)
            } else {
                background = Color(argbHex: 0xFF0E1412)
                surface = Color(argbHex: 0xFF161E1B)
                surfaceSecondary = Color(argbHex: 0xFF1F2A26)
                outline = Color.white.opacity(0.09)
            }
        } else {
            background = lightBackground(for: style)
            surface = .white
            surfaceSecondary = Color(argbHex: 0xFFF3F6F5)
            outline = Color(argbHex: 0xFFE3EAE7)
        }

        let textPrimary = isDark ? Color(argbHex: 0xFFF2F6F4) : Color(argbHex: 0xFF17211D)
        let textSecondary = isDark ? Color(argbHex: 0xFF9EB0A8) : Color(argbHex: 0xFF5D6F67)
        let textTertiary = isDark ? Color(argbHex: 0xFF6E8078) : Color(argbHex: 0xFF8EA098)

        let redTone = isDark ? Color(argbHex: 0xFFF87171) : Color(argbHex: 0xFFD9383A)
        let greenTone = isDark ? Color(argbHex: 0xFF34D399) : Color(argbHex: 0xFF1E9E6B)
        let expenseColor = settings.greenExpenseMode ? greenTone : redTone
        let incomeColor = settings.greenExpenseMode ? redTone : greenTone
        let transferColor = isDark ? Color(argbHex: 0xFF60A5FA) : Color(argbHex: 0xFF2563EB)
        let warningColor = isDark ? Color(argbHex: 0xFFFBBF24) : Color(argbHex: 0xFFD97706)
        let dangerColor = redTone

        let gradientColors: [Color]
        if isDark {
            gradientColors = [
                Color(argbHex: style.primaryColorHex).opacity(0.82),
                Color(argbHex: 0xFF12231D)
            ]
        } else {
            gradientColors = [
                basePrimary,
                darkenedColor(fromARGBHex: style.primaryColorHex, factor: 0.16)
            ]
        }

        return YubraePalette(
            style: style,
            isDark: isDark,
            primary: primary,
            onPrimary: onPrimary,
            primaryContainer: primaryContainer,
            onPrimaryContainer: onPrimaryContainer,
            background: background,
            surface: surface,
            surfaceSecondary: surfaceSecondary,
            outline: outline,
            textPrimary: textPrimary,
            textSecondary: textSecondary,
            textTertiary: textTertiary,
            expenseColor: expenseColor,
            incomeColor: incomeColor,
            transferColor: transferColor,
            warningColor: warningColor,
            dangerColor: dangerColor,
            heroGradient: LinearGradient(
                colors: gradientColors,
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
        )
    }

    public static func masked(_ text: String, isMasked: Bool) -> String {
        isMasked ? "••••" : text
    }

    public static func formatDate(_ epochMillis: Int64, pattern: String = "MM-dd HH:mm") -> String {
        let df = DateFormatter()
        df.locale = Locale(identifier: "zh_CN")
        df.dateFormat = pattern
        return df.string(from: Date(timeIntervalSince1970: Double(epochMillis) / 1000.0))
    }

    public static func formatDaySectionTitle(_ epochMillis: Int64) -> String {
        let date = Date(timeIntervalSince1970: Double(epochMillis) / 1000.0)
        let cal = Calendar.current
        if cal.isDateInToday(date) {
            return "今天 · \(formatDate(epochMillis, pattern: "M月d日"))"
        }
        if cal.isDateInYesterday(date) {
            return "昨天 · \(formatDate(epochMillis, pattern: "M月d日"))"
        }
        return formatDate(epochMillis, pattern: "M月d日 EEEE")
    }

    private static let weekdayShortLabelsZh = ["周日", "周一", "周二", "周三", "周四", "周五", "周六"]
    private static let notePlaceholderValues: Set<String> = ["/", "-", "--", "无"]
    private static let noteDotTrimCharacterSet = CharacterSet(charactersIn: "·")
    private static let noteSegmentSplitRegex: NSRegularExpression = {
        try! NSRegularExpression(pattern: #"\s+·\s+|\s*\|\s*|\r?\n|；"#)
    }()
    private static let noteClauseSplitRegex: NSRegularExpression = {
        try! NSRegularExpression(pattern: #"[，。！!]|(?<!\d),(?!\d)"#)
    }()
    private static let structuredNoteMetadataRegex: NSRegularExpression = {
        try! NSRegularExpression(
            pattern: #"^(?:地址|创建用户|创建人|记账人|优惠|优惠金额|报销账户|报销金额|报销明细|报销说明|报销状态|多币种|退款|退款金额|退款明细|退款说明|其他|其它|附件\d*|交易单号|商户单号|流水号|交易订单号|商家订单号)\s*[:：].*$"#
        )
    }()
    private static let importerDiscountNoteRegex: NSRegularExpression = {
        try! NSRegularExpression(
            pattern: #"^优惠\s+[¥￥$€£+\-]?\s*\d+(?:[.,]\d+)*(?:\s*(?:元|折|[A-Za-z]{3}))?$"#
        )
    }()
    private static let autoNotifyStatusRegex: NSRegularExpression = {
        try! NSRegularExpression(
            pattern: #"^(?:刷卡完成|支付成功|付款成功|扣款成功|交易成功|收款成功|充值成功)[，,。！!]?$"#
        )
    }()
    private static let autoNotifyAmountRegex: NSRegularExpression = {
        try! NSRegularExpression(
            pattern: #"^(?:充值|交易|消费|付款|扣款|支付|入账|转出|转入)金额\s*[:：]\s*[¥￥$€£+\-]?\s*\d+(?:[.,]\d+)*\s*(?:元|[A-Za-z]{3})?[。！!]?$"#
        )
    }()
    private static let autoNotifyBalanceRegex: NSRegularExpression = {
        try! NSRegularExpression(
            pattern: #"^(?:卡内|账户|当前|可用|剩余)余额\s*[:：]\s*[¥￥$€£+\-]?\s*\d+(?:[.,]\d+)*\s*(?:元|[A-Za-z]{3})?[。！!]?$"#
        )
    }()

    /// 仅用于列表行展示的备注清洗：过滤账单导入结构化元数据与自动通知机器废话，绝不改写持久化原值。
    public static func cleanListRowNote(_ rawNote: String) -> String {
        let trimmedRaw = rawNote.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmedRaw.isEmpty else { return "" }

        var keptSegments: [String] = []
        for rawSegment in split(trimmedRaw, using: noteSegmentSplitRegex) {
            let segment = rawSegment
                .trimmingCharacters(in: .whitespacesAndNewlines)
                .trimmingCharacters(in: noteDotTrimCharacterSet)
                .trimmingCharacters(in: .whitespacesAndNewlines)
            guard !segment.isEmpty,
                  !notePlaceholderValues.contains(segment),
                  !isNoiseNoteClause(segment) else {
                continue
            }

            let clauses = split(segment, using: noteClauseSplitRegex)
                .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            if clauses.count > 1 {
                let hasNoiseClause = clauses.contains { clause in
                    !clause.isEmpty && (notePlaceholderValues.contains(clause) || isNoiseNoteClause(clause))
                }
                if hasNoiseClause {
                    let surviving = clauses.filter { clause in
                        !clause.isEmpty &&
                            !notePlaceholderValues.contains(clause) &&
                            !isNoiseNoteClause(clause)
                    }
                    if !surviving.isEmpty {
                        keptSegments.append(surviving.joined(separator: "，"))
                    }
                    continue
                }
            }

            keptSegments.append(segment)
        }
        return keptSegments.joined(separator: " · ")
    }

    /// 返回日期分组头拆分的 `(dateText, subText)`：
    /// 如 `("11月27日", "今天")` / `("11月26日", "昨天")` / `("11月25日", "周三")`，跨年时 `dateText` 带年份（如 `"2025年11月25日"`）。
    public static func formatDayHeaderParts(_ epochMillis: Int64) -> (dateText: String, subText: String) {
        formatDayHeaderParts(epochMillis, referenceDate: Date(), calendar: .current)
    }

    public static func formatDayHeaderParts(
        _ epochMillis: Int64,
        referenceDate: Date,
        calendar: Calendar = .current
    ) -> (dateText: String, subText: String) {
        let date = Date(timeIntervalSince1970: Double(epochMillis) / 1000.0)
        let comps = calendar.dateComponents([.year, .month, .day, .weekday], from: date)
        let refYear = calendar.component(.year, from: referenceDate)
        let year = comps.year ?? refYear
        let month = comps.month ?? 1
        let day = comps.day ?? 1

        let dateText = year == refYear
            ? "\(month)月\(day)日"
            : "\(year)年\(month)月\(day)日"

        let subText: String
        if calendar.isDate(date, inSameDayAs: referenceDate) {
            subText = "今天"
        } else if let yesterday = calendar.date(byAdding: .day, value: -1, to: referenceDate),
                  calendar.isDate(date, inSameDayAs: yesterday) {
            subText = "昨天"
        } else {
            let weekdayIdx = (comps.weekday ?? 1) - 1
            subText = (0..<weekdayShortLabelsZh.count).contains(weekdayIdx)
                ? weekdayShortLabelsZh[weekdayIdx]
                : ""
        }

        return (dateText: dateText, subText: subText)
    }

    /// 全币种清晰带符号金额格式：所有币种（含主币种）均保留清晰货币符号（如 `-¥1.80` / `+¥106.00` / `¥50.00` / `¥1,280.50` / `¥0.00` / `-$53.16` / `+$12.00` / `-JP¥1,200` / `-12.00 CHF`）。
    public static func formatPlainSignedAmount(
        _ micros: Int64,
        currencyCode: String,
        baseCurrency: String = CurrencyCatalog.defaultBaseCurrency,
        showSign: Bool = true
    ) -> String {
        _ = baseCurrency
        let normCurrency = CurrencyCatalog.normalize(currencyCode)
        let info = CurrencyCatalog.find(normCurrency)
        let sign: String
        if micros < 0 {
            sign = "-"
        } else if showSign && micros > 0 {
            sign = "+"
        } else {
            sign = ""
        }
        let absMicros: Int64 = micros == Int64.min ? Int64.max : abs(micros)
        let numberStr = MoneyAmount.formatPlain(absMicros, minorDigits: info.minorDigits, grouping: true)
        let trimmedSymbol = info.symbol.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmedSymbol.isEmpty || trimmedSymbol.caseInsensitiveCompare(info.code) == .orderedSame {
            return "\(sign)\(numberStr) \(info.code)"
        }
        return "\(sign)\(trimmedSymbol)\(numberStr)"
    }

    private static func isNoiseNoteClause(_ text: String) -> Bool {
        fullMatch(text, using: structuredNoteMetadataRegex)
            || fullMatch(text, using: importerDiscountNoteRegex)
            || fullMatch(text, using: autoNotifyStatusRegex)
            || fullMatch(text, using: autoNotifyAmountRegex)
            || fullMatch(text, using: autoNotifyBalanceRegex)
    }

    private static func split(_ text: String, using regex: NSRegularExpression) -> [String] {
        let nsText = text as NSString
        let fullRange = NSRange(location: 0, length: nsText.length)
        let matches = regex.matches(in: text, options: [], range: fullRange)
        guard !matches.isEmpty else { return [text] }
        var parts: [String] = []
        var lastEnd = 0
        for match in matches {
            let segmentRange = NSRange(location: lastEnd, length: match.range.location - lastEnd)
            parts.append(nsText.substring(with: segmentRange))
            lastEnd = match.range.location + match.range.length
        }
        let tailRange = NSRange(location: lastEnd, length: nsText.length - lastEnd)
        parts.append(nsText.substring(with: tailRange))
        return parts
    }

    private static func fullMatch(_ text: String, using regex: NSRegularExpression) -> Bool {
        let nsText = text as NSString
        let fullRange = NSRange(location: 0, length: nsText.length)
        return regex.firstMatch(in: text, options: [], range: fullRange) != nil
    }

    public static func formatFileSize(_ bytes: Int64) -> String {
        if bytes <= 0 { return "未知大小" }
        let formatter = ByteCountFormatter()
        formatter.allowedUnits = [.useKB, .useMB, .useGB]
        formatter.countStyle = .file
        return formatter.string(fromByteCount: bytes)
    }

    private static func lightBackground(for style: ThemeStyle) -> Color {
        switch style {
        case .yimuFresh: return Color(argbHex: 0xFFF4F8F6)
        case .prismTeal: return Color(argbHex: 0xFFF3F8FA)
        case .sakuraPink: return Color(argbHex: 0xFFFCF5F8)
        case .skyBlue: return Color(argbHex: 0xFFF3F7FC)
        case .amberWarm: return Color(argbHex: 0xFFFCF8F2)
        case .lavenderMist: return Color(argbHex: 0xFFF7F5FC)
        case .forestMatcha: return Color(argbHex: 0xFFF6F8F3)
        case .coralSunset: return Color(argbHex: 0xFFFCF5F3)
        case .obsidianMono: return Color(argbHex: 0xFFF4F5F7)
        case .latteMocha: return Color(argbHex: 0xFFFAF6F2)
        }
    }

    private static func lightenedColor(fromARGBHex hex: Int64, factor: Double) -> Color {
        let c = colorComponents(fromARGBHex: hex)
        return Color(
            .sRGB,
            red: min(1.0, c.red + (1.0 - c.red) * factor),
            green: min(1.0, c.green + (1.0 - c.green) * factor),
            blue: min(1.0, c.blue + (1.0 - c.blue) * factor),
            opacity: 1.0
        )
    }

    private static func darkenedColor(fromARGBHex hex: Int64, factor: Double) -> Color {
        let c = colorComponents(fromARGBHex: hex)
        return Color(
            .sRGB,
            red: max(0.0, c.red * (1.0 - factor)),
            green: max(0.0, c.green * (1.0 - factor)),
            blue: max(0.0, c.blue * (1.0 - factor)),
            opacity: 1.0
        )
    }
}

// MARK: - Brand Mark & Reusable UI Components

/// 「余白 YUBRAE」薄荷清风几何品牌徽标。
public struct YubraeBrandMark: View {
    public var size: CGFloat = 56
    public var primaryColor: Color = Color(argbHex: ThemeStyle.yimuFresh.primaryColorHex)

    public init(size: CGFloat = 56, primaryColor: Color = Color(argbHex: ThemeStyle.yimuFresh.primaryColorHex)) {
        self.size = size
        self.primaryColor = primaryColor
    }

    public var body: some View {
        ZStack {
            RoundedRectangle(cornerRadius: size * 0.28, style: .continuous)
                .fill(
                    LinearGradient(
                        colors: [primaryColor, primaryColor.opacity(0.76)],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    )
                )
            Circle()
                .strokeBorder(Color.white.opacity(0.32), lineWidth: max(1.5, size * 0.035))
                .frame(width: size * 0.68, height: size * 0.68)
            Image(systemName: "leaf.fill")
                .font(.system(size: size * 0.42, weight: .semibold))
                .foregroundColor(.white)
        }
        .frame(width: size, height: size)
        .shadow(color: primaryColor.opacity(0.24), radius: size * 0.16, x: 0, y: size * 0.08)
    }
}

public struct YubraeCategoryIconBadge: View {
    public let iconKey: String
    public let colorHex: Int64
    public var size: CGFloat = 38

    public init(iconKey: String, colorHex: Int64, size: CGFloat = 38) {
        self.iconKey = iconKey
        self.colorHex = colorHex
        self.size = size
    }

    public var body: some View {
        let tint = Color(argbHex: colorHex)
        ZStack {
            Circle()
                .fill(tint.opacity(0.15))
            Image(systemName: DefaultSeedData.sfSymbol(for: iconKey))
                .font(.system(size: size * 0.44, weight: .semibold))
                .foregroundColor(tint)
        }
        .frame(width: size, height: size)
    }
}

public struct YubraeCard<Content: View>: View {
    public let palette: YubraePalette
    public var padding: CGFloat = 16
    public var cornerRadius: CGFloat = 18
    @ViewBuilder public let content: () -> Content

    public init(
        palette: YubraePalette,
        padding: CGFloat = 16,
        cornerRadius: CGFloat = 18,
        @ViewBuilder content: @escaping () -> Content
    ) {
        self.palette = palette
        self.padding = padding
        self.cornerRadius = cornerRadius
        self.content = content
    }

    public var body: some View {
        content()
            .padding(padding)
            .background(
                RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                    .fill(palette.surface)
            )
            .overlay(
                RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                    .strokeBorder(palette.outline, lineWidth: 0.8)
            )
    }
}

/// iOS 16 原生兼容的空态视图（不依赖 iOS 17 ContentUnavailableView）。
public struct YubraeEmptyStateView: View {
    public let icon: String
    public let title: String
    public let subtitle: String
    public let palette: YubraePalette
    public var actionTitle: String? = nil
    public var action: (() -> Void)? = nil

    public init(
        icon: String,
        title: String,
        subtitle: String,
        palette: YubraePalette,
        actionTitle: String? = nil,
        action: (() -> Void)? = nil
    ) {
        self.icon = icon
        self.title = title
        self.subtitle = subtitle
        self.palette = palette
        self.actionTitle = actionTitle
        self.action = action
    }

    public var body: some View {
        VStack(spacing: 12) {
            ZStack {
                Circle()
                    .fill(palette.accentSoftFill)
                    .frame(width: 60, height: 60)
                Image(systemName: icon)
                    .font(.system(size: 24, weight: .semibold))
                    .foregroundColor(palette.primary)
            }
            Text(title)
                .font(.headline)
                .foregroundColor(palette.textPrimary)
            Text(subtitle)
                .font(.subheadline)
                .foregroundColor(palette.textSecondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 24)
            if let actionTitle, let action {
                Button(action: action) {
                    Text(actionTitle)
                        .font(.subheadline.weight(.semibold))
                        .foregroundColor(palette.onPrimary)
                        .padding(.horizontal, 18)
                        .padding(.vertical, 9)
                        .background(Capsule().fill(palette.primary))
                }
                .padding(.top, 4)
            }
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 36)
    }
}

public struct YubraeToastBanner: View {
    public let text: String
    public let isError: Bool

    public init(text: String, isError: Bool = false) {
        self.text = text
        self.isError = isError
    }

    public var body: some View {
        HStack(spacing: 8) {
            Image(systemName: isError ? "exclamationmark.triangle.fill" : "checkmark.circle.fill")
                .foregroundColor(isError ? Color(argbHex: 0xFFFCA5A5) : Color(argbHex: 0xFF6EE7B7))
            Text(text)
                .font(.subheadline.weight(.medium))
                .foregroundColor(.white)
                .lineLimit(2)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 11)
        .background(
            Capsule(style: .continuous)
                .fill(Color(argbHex: 0xFF15221D).opacity(0.94))
                .shadow(color: Color.black.opacity(0.22), radius: 12, x: 0, y: 6)
        )
        .padding(.horizontal, 20)
    }
}

/// iOS 16+ 统一居中确认弹窗，替代原生简陋 `.alert`。
public struct YubraeConfirmDialog: View {
    @Binding public var isPresented: Bool
    public let palette: YubraePalette
    public let icon: String
    public let title: String
    public let message: String
    public var confirmTitle: String
    public var cancelTitle: String
    public var isDestructive: Bool
    public var onCancel: (() -> Void)?
    public let onConfirm: () -> Void

    public init(
        isPresented: Binding<Bool>,
        palette: YubraePalette,
        icon: String,
        title: String,
        message: String,
        confirmTitle: String = "确认",
        cancelTitle: String = "取消",
        isDestructive: Bool = false,
        onCancel: (() -> Void)? = nil,
        onConfirm: @escaping () -> Void
    ) {
        self._isPresented = isPresented
        self.palette = palette
        self.icon = icon
        self.title = title
        self.message = message
        self.confirmTitle = confirmTitle
        self.cancelTitle = cancelTitle
        self.isDestructive = isDestructive
        self.onCancel = onCancel
        self.onConfirm = onConfirm
    }

    public var body: some View {
        ZStack {
            if isPresented {
                Color.black.opacity(0.42)
                    .ignoresSafeArea()
                    .contentShape(Rectangle())
                    .onTapGesture {
                        dismissDialog(invokeCancel: true)
                    }
                    .transition(.opacity)

                dialogCard
                    .padding(.horizontal, 24)
                    .transition(.scale(scale: 0.92).combined(with: .opacity))
                    .zIndex(1)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .allowsHitTesting(isPresented)
        .animation(.spring(response: 0.28, dampingFraction: 0.86), value: isPresented)
    }

    private var dialogCard: some View {
        YubraeConfirmDialogCard(
            palette: palette,
            icon: icon,
            title: title,
            message: message,
            confirmTitle: confirmTitle,
            cancelTitle: cancelTitle,
            isDestructive: isDestructive,
            onCancel: {
                dismissDialog(invokeCancel: true)
            },
            onConfirm: {
                dismissDialog(invokeCancel: false)
                onConfirm()
            }
        )
    }

    private func dismissDialog(invokeCancel: Bool) {
        withAnimation(.spring(response: 0.28, dampingFraction: 0.86)) {
            isPresented = false
        }
        if invokeCancel {
            onCancel?()
        }
    }
}

private struct YubraeConfirmDialogCard: View {
    let palette: YubraePalette
    let icon: String
    let title: String
    let message: String
    let confirmTitle: String
    let cancelTitle: String
    let isDestructive: Bool
    let onCancel: () -> Void
    let onConfirm: () -> Void

    var body: some View {
        let accent = isDestructive ? palette.dangerColor : palette.primary
        let confirmFill = LinearGradient(
            colors: isDestructive
                ? [palette.dangerColor, palette.dangerColor.opacity(0.88)]
                : [palette.primary, palette.primary.opacity(0.86)],
            startPoint: .topLeading,
            endPoint: .bottomTrailing
        )

        VStack(spacing: 16) {
            ZStack {
                Circle()
                    .fill(accent.opacity(palette.isDark ? 0.22 : 0.14))
                    .frame(width: 52, height: 52)
                Image(systemName: icon)
                    .font(.system(size: 22, weight: .semibold))
                    .foregroundColor(accent)
            }

            VStack(spacing: 8) {
                Text(title)
                    .font(.headline.weight(.bold))
                    .foregroundColor(palette.textPrimary)
                    .multilineTextAlignment(.center)

                Text(message)
                    .font(.subheadline)
                    .foregroundColor(palette.textSecondary)
                    .multilineTextAlignment(.center)
                    .fixedSize(horizontal: false, vertical: true)
            }

            HStack(spacing: 12) {
                Button(action: onCancel) {
                    Text(cancelTitle)
                        .font(.subheadline.weight(.semibold))
                        .lineLimit(1)
                        .minimumScaleFactor(0.85)
                        .foregroundColor(palette.textPrimary)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 12)
                        .padding(.horizontal, 8)
                        .background(
                            RoundedRectangle(cornerRadius: 12, style: .continuous)
                                .fill(palette.surfaceSecondary)
                        )
                        .overlay(
                            RoundedRectangle(cornerRadius: 12, style: .continuous)
                                .strokeBorder(palette.outline.opacity(0.65), lineWidth: 0.6)
                        )
                }
                .buttonStyle(.plain)

                Button(action: onConfirm) {
                    Text(confirmTitle)
                        .font(.subheadline.weight(.semibold))
                        .lineLimit(1)
                        .minimumScaleFactor(0.85)
                        .foregroundColor(isDestructive ? .white : palette.onPrimary)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 12)
                        .padding(.horizontal, 8)
                        .background(
                            RoundedRectangle(cornerRadius: 12, style: .continuous)
                                .fill(confirmFill)
                        )
                }
                .buttonStyle(.plain)
            }
            .padding(.top, 4)
        }
        .padding(22)
        .frame(maxWidth: 340)
        .background(
            RoundedRectangle(cornerRadius: 24, style: .continuous)
                .fill(palette.surface)
        )
        .overlay(
            RoundedRectangle(cornerRadius: 24, style: .continuous)
                .strokeBorder(palette.outline, lineWidth: 0.9)
        )
        .shadow(color: Color.black.opacity(palette.isDark ? 0.45 : 0.18), radius: 22, x: 0, y: 10)
    }
}

private struct ClearFullScreenCoverBackground: UIViewRepresentable {
    func makeUIView(context: Context) -> UIView {
        let view = ProbeView()
        view.backgroundColor = .clear
        return view
    }

    func updateUIView(_ uiView: UIView, context: Context) {}

    private final class ProbeView: UIView {
        override func didMoveToWindow() {
            super.didMoveToWindow()
            clearHostingBackground()
        }

        override func layoutSubviews() {
            super.layoutSubviews()
            clearHostingBackground()
        }

        private func clearHostingBackground() {
            superview?.superview?.backgroundColor = .clear
            var responder: UIResponder? = self
            while let next = responder?.next {
                if let vc = next as? UIViewController {
                    vc.view.backgroundColor = .clear
                    break
                }
                responder = next
            }
        }
    }
}

private extension View {
    @ViewBuilder
    func yubraeClearFullScreenBackground() -> some View {
        if #available(iOS 16.4, *) {
            self.presentationBackground(Color.clear)
        } else {
            self.background(ClearFullScreenCoverBackground())
        }
    }
}

private struct YubraeConfirmDialogModifier: ViewModifier {
    private enum PendingOutcome {
        case confirm
        case cancel
    }

    @Binding var isPresented: Bool
    let palette: YubraePalette
    let icon: String
    let title: String
    let message: String
    let confirmTitle: String
    let cancelTitle: String
    let isDestructive: Bool
    let onCancel: (() -> Void)?
    let onConfirm: () -> Void

    @State private var pendingOutcome: PendingOutcome?
    @State private var backdropVisible = false

    func body(content: Content) -> some View {
        content
            .fullScreenCover(isPresented: $isPresented, onDismiss: {
                backdropVisible = false
                let outcome = pendingOutcome
                pendingOutcome = nil
                switch outcome {
                case .confirm:
                    onConfirm()
                case .cancel:
                    onCancel?()
                case nil:
                    break
                }
            }) {
                ZStack {
                    Color.black.opacity(backdropVisible ? 0.42 : 0.0)
                        .ignoresSafeArea()
                        .contentShape(Rectangle())
                        .onTapGesture {
                            dismissCover(outcome: .cancel)
                        }

                    YubraeConfirmDialogCard(
                        palette: palette,
                        icon: icon,
                        title: title,
                        message: message,
                        confirmTitle: confirmTitle,
                        cancelTitle: cancelTitle,
                        isDestructive: isDestructive,
                        onCancel: {
                            dismissCover(outcome: .cancel)
                        },
                        onConfirm: {
                            dismissCover(outcome: .confirm)
                        }
                    )
                    .padding(.horizontal, 24)
                    .opacity(backdropVisible ? 1 : 0)
                    .scaleEffect(backdropVisible ? 1 : 0.94)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(Color.clear)
                .onAppear {
                    withAnimation(.spring(response: 0.24, dampingFraction: 0.88)) {
                        backdropVisible = true
                    }
                }
                .yubraeClearFullScreenBackground()
            }
    }

    private func dismissCover(outcome: PendingOutcome) {
        pendingOutcome = outcome
        withAnimation(.easeOut(duration: 0.12)) {
            backdropVisible = false
        }
        isPresented = false
    }
}

public extension View {
    func yubraeConfirmDialog(
        isPresented: Binding<Bool>,
        palette: YubraePalette,
        icon: String,
        title: String,
        message: String,
        confirmTitle: String = "确认",
        cancelTitle: String = "取消",
        isDestructive: Bool = false,
        onCancel: (() -> Void)? = nil,
        onConfirm: @escaping () -> Void
    ) -> some View {
        modifier(
            YubraeConfirmDialogModifier(
                isPresented: isPresented,
                palette: palette,
                icon: icon,
                title: title,
                message: message,
                confirmTitle: confirmTitle,
                cancelTitle: cancelTitle,
                isDestructive: isDestructive,
                onCancel: onCancel,
                onConfirm: onConfirm
            )
        )
    }

    @ViewBuilder
    func yubraeSheetStyle(palette: YubraePalette) -> some View {
        if #available(iOS 16.4, *) {
            self
                .presentationDragIndicator(.visible)
                .presentationCornerRadius(24)
                .tint(palette.primary)
        } else {
            self
                .presentationDragIndicator(.visible)
                .tint(palette.primary)
        }
    }
}

/// 轻量 GitHub Release Notes Markdown 清洗与富文本解析工具（iOS 16+）。
public enum YubraeReleaseNotesFormatter {
    public static func cleanedPlainText(_ raw: String) -> String {
        attributedLines(raw)
            .map { String($0.characters) }
            .joined(separator: "\n")
    }

    public static func attributedLines(_ raw: String) -> [AttributedString] {
        let normalized = raw
            .replacingOccurrences(of: "\r\n", with: "\n")
            .replacingOccurrences(of: "\r", with: "\n")

        var result: [AttributedString] = []
        for rawLine in normalized.split(separator: "\n", omittingEmptySubsequences: false) {
            let lineStr = String(rawLine)
            let trimmed = lineStr.trimmingCharacters(in: .whitespaces)
            if trimmed.isEmpty || isHorizontalRule(trimmed) {
                continue
            }

            let leadingSpaces = lineStr.prefix(while: { $0 == " " || $0 == "\t" }).count
            if let headingContent = stripHeadingPrefix(trimmed) {
                let parsed = parseInline(headingContent, forceBold: true)
                if !String(parsed.characters).trimmingCharacters(in: .whitespaces).isEmpty {
                    result.append(parsed)
                }
                continue
            }

            if let bulletContent = stripBulletPrefix(trimmed) {
                let bulletPrefix = leadingSpaces >= 2 ? "  • " : "• "
                let contentAttr = parseInline(bulletContent, forceBold: false)
                if !String(contentAttr.characters).trimmingCharacters(in: .whitespaces).isEmpty {
                    var lineAttr = AttributedString(bulletPrefix)
                    lineAttr.append(contentAttr)
                    result.append(lineAttr)
                }
                continue
            }

            let parsed = parseInline(trimmed, forceBold: false)
            if !String(parsed.characters).trimmingCharacters(in: .whitespaces).isEmpty {
                result.append(parsed)
            }
        }
        return result
    }

    private static func isHorizontalRule(_ trimmed: String) -> Bool {
        let stripped = trimmed.replacingOccurrences(of: " ", with: "")
        guard stripped.count >= 3, let first = stripped.first else { return false }
        guard first == "-" || first == "*" || first == "_" else { return false }
        return stripped.allSatisfy { $0 == first }
    }

    private static func stripHeadingPrefix(_ trimmed: String) -> String? {
        var hashCount = 0
        var idx = trimmed.startIndex
        while idx < trimmed.endIndex && trimmed[idx] == "#" {
            hashCount += 1
            idx = trimmed.index(after: idx)
        }
        guard hashCount >= 1 && hashCount <= 6 else { return nil }
        guard idx < trimmed.endIndex && trimmed[idx].isWhitespace else { return nil }
        let rest = trimmed[idx...].trimmingCharacters(in: .whitespaces)
        return rest.replacingOccurrences(of: #"\s+#+\s*$"#, with: "", options: .regularExpression)
    }

    private static func stripBulletPrefix(_ trimmed: String) -> String? {
        for marker in ["- ", "* ", "+ ", "• "] {
            if trimmed.hasPrefix(marker) {
                return String(trimmed.dropFirst(marker.count)).trimmingCharacters(in: .whitespaces)
            }
        }
        return nil
    }

    private static func replaceMarkdownLinks(_ input: String) -> String {
        guard input.contains("[") && input.contains("](") else { return input }
        return input.replacingOccurrences(
            of: #"\[([^\]]+)\]\((https?://[^\s\)]+)\)"#,
            with: "$1",
            options: .regularExpression
        )
    }

    private static func parseInline(_ input: String, forceBold: Bool) -> AttributedString {
        let linkCleaned = replaceMarkdownLinks(input)
        var output = AttributedString()
        var cursor = linkCleaned.startIndex
        let end = linkCleaned.endIndex

        while cursor < end {
            if linkCleaned[cursor...].hasPrefix("***") {
                let afterOpen = linkCleaned.index(cursor, offsetBy: 3)
                if let closeRange = linkCleaned.range(of: "***", range: afterOpen..<end) {
                    let inner = String(linkCleaned[afterOpen..<closeRange.lowerBound])
                        .replacingOccurrences(of: "**", with: "")
                        .replacingOccurrences(of: "`", with: "")
                    if !inner.isEmpty {
                        output.append(makeSegment(inner, bold: true, italic: true, code: false))
                    }
                    cursor = closeRange.upperBound
                    continue
                }
            }

            if linkCleaned[cursor...].hasPrefix("**") {
                let afterOpen = linkCleaned.index(cursor, offsetBy: 2)
                if let closeRange = linkCleaned.range(of: "**", range: afterOpen..<end) {
                    let inner = String(linkCleaned[afterOpen..<closeRange.lowerBound])
                        .replacingOccurrences(of: "**", with: "")
                        .replacingOccurrences(of: "`", with: "")
                    if !inner.isEmpty {
                        output.append(makeSegment(inner, bold: true, italic: false, code: false))
                    }
                    cursor = closeRange.upperBound
                    continue
                } else {
                    cursor = afterOpen
                    continue
                }
            }

            if linkCleaned[cursor] == "`" {
                let afterOpen = linkCleaned.index(after: cursor)
                if let closeIdx = linkCleaned[afterOpen..<end].firstIndex(of: "`") {
                    let inner = String(linkCleaned[afterOpen..<closeIdx])
                    if !inner.isEmpty {
                        output.append(makeSegment(inner, bold: forceBold, italic: false, code: true))
                    }
                    cursor = linkCleaned.index(after: closeIdx)
                    continue
                } else {
                    cursor = afterOpen
                    continue
                }
            }

            let nextStart = cursor
            var scan = linkCleaned.index(after: cursor)
            while scan < end {
                if linkCleaned[scan] == "`" || linkCleaned[scan...].hasPrefix("**") {
                    break
                }
                scan = linkCleaned.index(after: scan)
            }
            let plain = String(linkCleaned[nextStart..<scan])
            output.append(makeSegment(plain, bold: forceBold, italic: false, code: false))
            cursor = scan
        }

        return normalizeSpaces(output)
    }

    private static func makeSegment(_ text: String, bold: Bool, italic: Bool, code: Bool) -> AttributedString {
        var seg = AttributedString(text)
        var intent: InlinePresentationIntent = []
        if bold { intent.insert(.stronglyEmphasized) }
        if italic { intent.insert(.emphasized) }
        if code { intent.insert(.code) }
        if !intent.isEmpty {
            seg.inlinePresentationIntent = intent
        }
        return seg
    }

    private static func normalizeSpaces(_ attr: AttributedString) -> AttributedString {
        var result = attr
        while let range = result.range(of: "  ") {
            result.replaceSubrange(range, with: AttributedString(" "))
        }
        while let first = result.characters.first, first.isWhitespace {
            let nextIdx = result.index(afterCharacter: result.startIndex)
            result.removeSubrange(result.startIndex..<nextIdx)
        }
        while let last = result.characters.last, last.isWhitespace {
            let prevIdx = result.index(beforeCharacter: result.endIndex)
            result.removeSubrange(prevIdx..<result.endIndex)
        }
        return result
    }
}

/// iOS 16+ 原生 `Layout` 协议流式换行布局，用于标签与快捷选项芯片。
public struct YubraeFlowLayout: Layout {
    public var spacing: CGFloat
    public var lineSpacing: CGFloat

    public init(spacing: CGFloat = 8, lineSpacing: CGFloat = 8) {
        self.spacing = spacing
        self.lineSpacing = lineSpacing
    }

    private struct RowItem {
        let index: Int
        let size: CGSize
        let xOffset: CGFloat
    }

    private struct Row {
        var items: [RowItem] = []
        var width: CGFloat = 0
        var height: CGFloat = 0
    }

    private func resolvedMaxWidth(for proposal: ProposedViewSize) -> CGFloat {
        proposal.width ?? proposal.replacingUnspecifiedDimensions(by: CGSize(width: 320, height: 0)).width
    }

    private func computeRows(maxWidth: CGFloat, subviews: Subviews) -> [Row] {
        guard !subviews.isEmpty else { return [] }
        let clampWidth: CGFloat? = (maxWidth.isFinite && maxWidth > 0) ? maxWidth : nil
        var rows: [Row] = []
        var currentRow = Row()
        var currentX: CGFloat = 0

        for (index, subview) in subviews.enumerated() {
            let idealSize = subview.sizeThatFits(.unspecified)
            let itemSize: CGSize
            if let clampWidth, idealSize.width > clampWidth {
                let constrained = subview.sizeThatFits(ProposedViewSize(width: clampWidth, height: nil))
                itemSize = CGSize(width: min(constrained.width, clampWidth), height: constrained.height)
            } else if let clampWidth {
                itemSize = CGSize(width: min(idealSize.width, clampWidth), height: idealSize.height)
            } else {
                itemSize = idealSize
            }

            if !currentRow.items.isEmpty && currentX + itemSize.width > maxWidth {
                rows.append(currentRow)
                currentRow = Row()
                currentX = 0
            }

            currentRow.items.append(RowItem(index: index, size: itemSize, xOffset: currentX))
            currentRow.width = currentX + itemSize.width
            currentRow.height = max(currentRow.height, itemSize.height)
            currentX += itemSize.width + spacing
        }

        if !currentRow.items.isEmpty {
            rows.append(currentRow)
        }
        return rows
    }

    public func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let maxWidth = resolvedMaxWidth(for: proposal)
        let rows = computeRows(maxWidth: maxWidth, subviews: subviews)
        guard !rows.isEmpty else { return .zero }

        let totalWidth = rows.map(\.width).max() ?? 0
        let totalHeight = rows.reduce(CGFloat(0)) { $0 + $1.height }
            + CGFloat(max(0, rows.count - 1)) * lineSpacing
        let finalWidth = maxWidth.isFinite ? min(maxWidth, totalWidth) : totalWidth
        return CGSize(width: finalWidth, height: totalHeight)
    }

    public func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        let maxWidth = bounds.width > 0 ? bounds.width : resolvedMaxWidth(for: proposal)
        let rows = computeRows(maxWidth: maxWidth, subviews: subviews)
        var currentY = bounds.minY

        for row in rows {
            for item in row.items {
                let y = currentY + (row.height - item.size.height) / 2
                subviews[item.index].place(
                    at: CGPoint(x: bounds.minX + item.xOffset, y: y),
                    proposal: ProposedViewSize(width: item.size.width, height: item.size.height)
                )
            }
            currentY += row.height + lineSpacing
        }
    }
}

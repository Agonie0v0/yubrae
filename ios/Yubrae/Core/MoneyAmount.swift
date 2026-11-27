import Foundation

/// ISO 4217 币种元数据与默认兜底参考汇率（与 Android `CurrencyCatalog.kt` 完全对齐）。
public struct CurrencyInfo: Identifiable, Hashable, Codable, Sendable {
    public var id: String { code }
    public let code: String
    public let nameZh: String
    public let symbol: String
    public let flag: String
    public let minorDigits: Int
    /// 1 单位该币种折合 CNY 的百万分位定点数（RATE_SCALE = 1_000_000）。
    public let fallbackRateToCnyMicros: Int64

    public init(
        code: String,
        nameZh: String,
        symbol: String,
        flag: String,
        minorDigits: Int,
        fallbackRateToCnyMicros: Int64
    ) {
        self.code = code
        self.nameZh = nameZh
        self.symbol = symbol
        self.flag = flag
        self.minorDigits = minorDigits
        self.fallbackRateToCnyMicros = fallbackRateToCnyMicros
    }
}

/// 币种目录、排序与文本币种探测（100% 复用 Android 端清单与兜底汇率）。
public enum CurrencyCatalog {
    public static let defaultBaseCurrency = "CNY"
    public static let pinnedCurrencies: [String] = ["CNY", "USD", "EUR", "GBP"]

    public static let all: [CurrencyInfo] = [
        CurrencyInfo(code: "CNY", nameZh: "人民币", symbol: "¥", flag: "🇨🇳", minorDigits: 2, fallbackRateToCnyMicros: 1_000_000),
        CurrencyInfo(code: "USD", nameZh: "美元", symbol: "$", flag: "🇺🇸", minorDigits: 2, fallbackRateToCnyMicros: 7_240_000),
        CurrencyInfo(code: "EUR", nameZh: "欧元", symbol: "€", flag: "🇪🇺", minorDigits: 2, fallbackRateToCnyMicros: 7_620_000),
        CurrencyInfo(code: "GBP", nameZh: "英镑", symbol: "£", flag: "🇬🇧", minorDigits: 2, fallbackRateToCnyMicros: 9_150_000),
        CurrencyInfo(code: "HKD", nameZh: "港币", symbol: "HK$", flag: "🇭🇰", minorDigits: 2, fallbackRateToCnyMicros: 930_000),
        CurrencyInfo(code: "JPY", nameZh: "日元", symbol: "JP¥", flag: "🇯🇵", minorDigits: 0, fallbackRateToCnyMicros: 47_000),
        CurrencyInfo(code: "SGD", nameZh: "新加坡元", symbol: "S$", flag: "🇸🇬", minorDigits: 2, fallbackRateToCnyMicros: 5_380_000),
        CurrencyInfo(code: "AUD", nameZh: "澳元", symbol: "A$", flag: "🇦🇺", minorDigits: 2, fallbackRateToCnyMicros: 4_710_000),
        CurrencyInfo(code: "CAD", nameZh: "加元", symbol: "C$", flag: "🇨🇦", minorDigits: 2, fallbackRateToCnyMicros: 5_160_000),
        CurrencyInfo(code: "CHF", nameZh: "瑞士法郎", symbol: "CHF", flag: "🇨🇭", minorDigits: 2, fallbackRateToCnyMicros: 8_160_000),
        CurrencyInfo(code: "TWD", nameZh: "新台币", symbol: "NT$", flag: "🇹🇼", minorDigits: 2, fallbackRateToCnyMicros: 223_000),
        CurrencyInfo(code: "KRW", nameZh: "韩元", symbol: "₩", flag: "🇰🇷", minorDigits: 0, fallbackRateToCnyMicros: 5_180)
    ]

    private static let byCode: [String: CurrencyInfo] = Dictionary(
        uniqueKeysWithValues: all.map { ($0.code, $0) }
    )

    public static func normalize(_ code: String?) -> String {
        let trimmed = (code ?? "").trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        return trimmed.isEmpty ? defaultBaseCurrency : trimmed
    }

    public static func find(_ code: String) -> CurrencyInfo {
        let upper = code.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        if let found = byCode[upper] {
            return found
        }
        let resolved = upper.isEmpty ? defaultBaseCurrency : upper
        return CurrencyInfo(
            code: resolved,
            nameZh: upper.isEmpty ? "人民币" : resolved,
            symbol: upper.isEmpty ? "¥" : resolved,
            flag: "🌐",
            minorDigits: 2,
            fallbackRateToCnyMicros: 1_000_000
        )
    }

    public static func symbol(of code: String) -> String {
        find(code).symbol
    }

    public static func orderedCodes(pinned: [String] = pinnedCurrencies) -> [String] {
        var seen = Set<String>()
        var normalizedPinned: [String] = []
        for raw in pinned {
            let code = raw.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
            if !code.isEmpty, seen.insert(code).inserted {
                normalizedPinned.append(code)
            }
        }
        let remaining = all.map(\.code).filter { !seen.contains($0) }
        return normalizedPinned + remaining
    }

    /// 根据文本中的货币符号或关键词猜测币种（与 Android `CurrencyCatalog.detectCurrencyFromText` 一致）。
    public static func detectCurrency(from text: String, defaultCode: String = defaultBaseCurrency) -> String {
        let upper = text.uppercased()
        if upper.contains("USD") || upper.contains("US$") || upper.contains("美元") || upper.contains("美金") ||
            (text.contains("$") && !upper.contains("HK$") && !upper.contains("S$") && !upper.contains("NT$") && !upper.contains("A$") && !upper.contains("C$")) {
            return "USD"
        }
        if upper.contains("EUR") || text.contains("€") || upper.contains("欧元") {
            return "EUR"
        }
        if upper.contains("GBP") || text.contains("£") || upper.contains("英镑") {
            return "GBP"
        }
        if upper.contains("HKD") || upper.contains("HK$") || upper.contains("港币") || upper.contains("港元") {
            return "HKD"
        }
        if upper.contains("JPY") || upper.contains("JP¥") || upper.contains("日元") {
            return "JPY"
        }
        if upper.contains("SGD") || upper.contains("S$") || upper.contains("新元") || upper.contains("新加坡元") {
            return "SGD"
        }
        if upper.contains("AUD") || upper.contains("A$") || upper.contains("澳元") {
            return "AUD"
        }
        if upper.contains("CAD") || upper.contains("C$") || upper.contains("加元") {
            return "CAD"
        }
        if upper.contains("CHF") || upper.contains("瑞郎") || upper.contains("瑞士法郎") {
            return "CHF"
        }
        if upper.contains("TWD") || upper.contains("NT$") || upper.contains("新台币") {
            return "TWD"
        }
        if upper.contains("KRW") || text.contains("₩") || upper.contains("韩元") {
            return "KRW"
        }
        if upper.contains("CNY") || upper.contains("RMB") || text.contains("¥") || text.contains("￥") || text.contains("元") || upper.contains("人民币") {
            return "CNY"
        }
        return defaultCode
    }
}

/// 金融级定点数运算工具（严格对齐 Android `MoneyAmount.kt`，严禁使用 Float/Double 存储或累加金额）。
/// - 金额缩放因子 `amountScale = 10_000`（支持最多 4 位小数，1.00 元 = `10_000`）
/// - 汇率缩放因子 `rateScale = 1_000_000`（支持 6 位小数精度，汇率 7.2450 = `7_245_000`）
public enum MoneyAmount {
    public static let amountScale: Int64 = 10_000
    public static let rateScale: Int64 = 1_000_000
    /// 正负号（`-` / `+`）与货币符号（或后置代码数字）之间的不换行空格（U+00A0）。
    public static let signGap = "\u{00A0}"
    /// 前置货币符号与数字之间的窄空格 Thin Space（U+2009）。
    public static let symbolGap = "\u{2009}"

    private static let decimalAmountScale = Decimal(amountScale)
    private static let decimalRateScale = Decimal(rateScale)
    private static let posixLocale = Locale(identifier: "en_US_POSIX")
    private static let numericPattern: NSRegularExpression = {
        // 严格匹配十进制实数，避免 Decimal(string:) 忽略尾随非法字符
        try! NSRegularExpression(pattern: #"^[+-]?(?:\d+(?:\.\d*)?|\.\d+)$"#)
    }()

    /// 将字符串金额转为定点数 Int64（乘以 `amountScale = 10_000`），非法输入返回 `nil`。
    public static func parseToMicros(_ raw: String) -> Int64? {
        var cleaned = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        for token in [
            ",", "，",
            "JP¥", "HK$", "NT$", "S$", "A$", "C$",
            "¥", "￥", "$", "€", "£", "₩",
            "\u{00A0}", "\u{2007}", "\u{2008}", "\u{2009}", "\u{200A}", "\u{202F}", "\u{3000}", "\u{200B}", "\u{FEFF}"
        ] {
            cleaned = cleaned.replacingOccurrences(of: token, with: "")
        }
        cleaned = cleaned.trimmingCharacters(in: .whitespacesAndNewlines)
        guard isValidDecimalLiteral(cleaned),
              let dec = Decimal(string: cleaned, locale: posixLocale) else {
            return nil
        }
        let scaled = dec * decimalAmountScale
        return roundedInt64(scaled)
    }

    /// 将汇率字符串转为百万分位汇率 Int64（乘以 `rateScale = 1_000_000`），非正数或非法输入返回 `nil`。
    public static func parseRateToMicros(_ raw: String) -> Int64? {
        let cleaned = raw.trimmingCharacters(in: .whitespacesAndNewlines)
            .replacingOccurrences(of: ",", with: "")
            .trimmingCharacters(in: .whitespacesAndNewlines)
        guard isValidDecimalLiteral(cleaned),
              let dec = Decimal(string: cleaned, locale: posixLocale),
              dec > 0 else {
            return nil
        }
        let scaled = dec * decimalRateScale
        guard let result = roundedInt64(scaled), result > 0 else {
            return nil
        }
        return result
    }

    public static func rateFromDecimal(_ rate: Decimal) -> Int64 {
        guard rate > 0 else { return rateScale }
        let scaled = rate * decimalRateScale
        return max(1, roundedInt64(scaled) ?? rateScale)
    }

    public static func rateToDecimal(_ rateMicros: Int64) -> Decimal {
        roundDecimal(Decimal(rateMicros) / decimalRateScale, scale: 6)
    }

    public static func microsToDecimal(_ amountMicros: Int64) -> Decimal {
        roundDecimal(Decimal(amountMicros) / decimalAmountScale, scale: 4)
    }

    /// 使用成交汇率换算金额：`target = orig * rateMicros / rateScale`（HALF_UP）。
    public static func convert(amountMicros: Int64, rateMicros: Int64) -> Int64 {
        if amountMicros == 0 { return 0 }
        if rateMicros == rateScale { return amountMicros }
        let value = (Decimal(amountMicros) * Decimal(rateMicros)) / decimalRateScale
        return roundedInt64(value) ?? amountMicros
    }

    /// 根据原币种金额与实际账户扣款金额反算成交汇率（支持汇损反算）。
    public static func computeRate(origAmountMicros: Int64, targetAmountMicros: Int64) -> Int64 {
        if origAmountMicros == 0 || targetAmountMicros == 0 { return rateScale }
        let origAbs = Decimal(origAmountMicros.magnitude)
        let targetAbs = Decimal(targetAmountMicros.magnitude)
        let value = (targetAbs * decimalRateScale) / origAbs
        return max(1, roundedInt64(value) ?? rateScale)
    }

    /// 反向求汇率：`1 / rateMicros`。
    public static func invertRate(_ rateMicros: Int64) -> Int64 {
        guard rateMicros > 0 else { return rateScale }
        let value = (decimalRateScale * decimalRateScale) / Decimal(rateMicros)
        return max(1, roundedInt64(value) ?? rateScale)
    }

    /// 通过中间币种（如 CNY）推导交叉汇率：`from -> via -> to`。
    public static func crossRate(fromToViaMicros: Int64, toToViaMicros: Int64) -> Int64 {
        guard fromToViaMicros > 0, toToViaMicros > 0 else { return rateScale }
        let value = (Decimal(fromToViaMicros) * decimalRateScale) / Decimal(toToViaMicros)
        return max(1, roundedInt64(value) ?? rateScale)
    }

    /// 格式化纯数字金额字符串（不带货币符号）。
    public static func formatPlain(_ amountMicros: Int64, minorDigits: Int = 2, grouping: Bool = false) -> String {
        let digits = max(0, minorDigits)
        let value = roundDecimal(Decimal(amountMicros) / decimalAmountScale, scale: digits)
        let formatter = NumberFormatter()
        formatter.locale = posixLocale
        formatter.numberStyle = .decimal
        formatter.usesGroupingSeparator = grouping
        formatter.minimumFractionDigits = digits
        formatter.maximumFractionDigits = digits
        formatter.roundingMode = .halfUp
        return formatter.string(from: NSDecimalNumber(decimal: value)) ?? "0"
    }

    /// 格式化带币种符号的展示金额，如 `¥\u{2009}128.50`、`-\u{00A0}$\u{2009}15.00 USD`。
    public static func format(
        _ amountMicros: Int64,
        currencyCode: String,
        showSign: Bool = false,
        showCurrencyCode: Bool = false,
        grouping: Bool = true
    ) -> String {
        let info = CurrencyCatalog.find(currencyCode)
        let sign: String
        if amountMicros < 0 {
            sign = "-"
        } else if showSign && amountMicros > 0 {
            sign = "+"
        } else {
            sign = ""
        }
        let absMicros: Int64 = amountMicros == Int64.min ? Int64.max : abs(amountMicros)
        let numberStr = formatPlain(absMicros, minorDigits: info.minorDigits, grouping: grouping)
        let signPart = sign.isEmpty ? "" : "\(sign)\(signGap)"
        let trimmedSymbol = info.symbol.trimmingCharacters(in: .whitespacesAndNewlines)
        let symbolPart = trimmedSymbol.isEmpty ? "" : "\(trimmedSymbol)\(symbolGap)"
        let suffix = (showCurrencyCode && info.code != CurrencyCatalog.defaultBaseCurrency) ? " \(info.code)" : ""
        return "\(signPart)\(symbolPart)\(numberStr)\(suffix)"
    }

    /// 格式化汇率字符串（至少保留 2 位小数，最多 `decimals` 位小数）。
    public static func formatRate(_ rateMicros: Int64, decimals: Int = 4) -> String {
        let maxDigits = max(2, decimals)
        let value = roundDecimal(Decimal(rateMicros) / decimalRateScale, scale: maxDigits)
        let formatter = NumberFormatter()
        formatter.locale = posixLocale
        formatter.numberStyle = .decimal
        formatter.usesGroupingSeparator = false
        formatter.minimumFractionDigits = 2
        formatter.maximumFractionDigits = maxDigits
        formatter.roundingMode = .halfUp
        return formatter.string(from: NSDecimalNumber(decimal: value)) ?? "1.00"
    }

    // MARK: - Private Helpers

    private static func isValidDecimalLiteral(_ str: String) -> Bool {
        guard !str.isEmpty else { return false }
        let range = NSRange(str.startIndex..., in: str)
        return numericPattern.firstMatch(in: str, range: range) != nil
    }

    private static func roundDecimal(_ value: Decimal, scale: Int) -> Decimal {
        var input = value
        var result = Decimal()
        NSDecimalRound(&result, &input, scale, .plain)
        return result
    }

    private static func roundedInt64(_ value: Decimal) -> Int64? {
        let rounded = roundDecimal(value, scale: 0)
        guard !rounded.isNaN,
              rounded >= Decimal(Int64.min),
              rounded <= Decimal(Int64.max) else {
            return nil
        }
        return NSDecimalNumber(decimal: rounded).int64Value
    }
}

import Foundation

/// 解析后的外部快捷记账协议参数（与 Android `ExternalBillProtocolParser.kt` 完全对齐）。
public struct ExternalBillParams: Equatable, Sendable {
    public let origAmountMicros: Int64
    public let type: String
    public let categoryName: String?
    public let accountNameOrHint: String?
    public let toAccountNameOrHint: String?
    public let merchant: String
    public let note: String
    public let timestamp: Int64
    public let currency: String?
    public let showFloatingPrompt: Bool
    public let sourceScheme: String

    public init(
        origAmountMicros: Int64,
        type: String,
        categoryName: String? = nil,
        accountNameOrHint: String? = nil,
        toAccountNameOrHint: String? = nil,
        merchant: String = "",
        note: String = "",
        timestamp: Int64 = currentEpochMillis(),
        currency: String? = nil,
        showFloatingPrompt: Bool = true,
        sourceScheme: String = "yubrae"
    ) {
        self.origAmountMicros = origAmountMicros
        self.type = type
        self.categoryName = categoryName
        self.accountNameOrHint = accountNameOrHint
        self.toAccountNameOrHint = toAccountNameOrHint
        self.merchant = merchant
        self.note = note
        self.timestamp = timestamp
        self.currency = currency
        self.showFloatingPrompt = showFloatingPrompt
        self.sourceScheme = sourceScheme
    }
}

/// 统一解析 `yubrae://addbill?...`、`yubrae://record?...`、`yubrae://smartinput?...`、`yubrae://receipt`、`yubrae://scan` 及向后兼容的 `kairoledger://...` URL 协议。
public enum ExternalBillUrlParser {
    public static let supportedSchemes: Set<String> = ["yubrae", "kairoledger"]
    public static let supportedActions: Set<String> = ["addbill", "record"]
    private static let smartInputActions: Set<String> = ["smartinput", "receipt", "scan"]

    /// 判断 URL 是否属于余白外部记账协议。
    public static func isSupportedURL(_ url: URL) -> Bool {
        guard let scheme = url.scheme?.lowercased(), supportedSchemes.contains(scheme) else {
            return false
        }
        return supportedActions.contains(extractAction(from: url))
    }

    /// 解析 `yubrae://smartinput?...`、`yubrae://receipt`、`yubrae://scan`（及兼容 `kairoledger://...`）智能记账启动请求。
    public static func parseSmartInputLaunch(url: URL) -> SmartInputLaunchRequest? {
        guard let scheme = url.scheme?.lowercased(), supportedSchemes.contains(scheme) else {
            return nil
        }
        let action = extractAction(from: url)
        guard smartInputActions.contains(action),
              let components = URLComponents(url: url, resolvingAgainstBaseURL: false) else {
            return nil
        }

        let (_, lowerParams) = queryMaps(from: components)
        let isReceiptAction = action == "receipt" || action == "scan"

        let modeRaw = (lowerParams["mode"] ?? "")
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .lowercased()
        let initialMode: SmartInputMode
        switch modeRaw {
        case "photo", "image", "receipt", "screenshot":
            initialMode = .receiptImage
        case "text":
            initialMode = .text
        default:
            initialMode = isReceiptAction ? .receiptImage : .text
        }

        let autoOpenPhotoPicker = parseBoolFlag(lowerParams["autoopenphotopicker"] ?? lowerParams["autopick"])
            ?? isReceiptAction
        let autoParseOnPick = parseBoolFlag(lowerParams["autoparseonpick"] ?? lowerParams["autoparse"])
            ?? autoOpenPhotoPicker

        return SmartInputLaunchRequest(
            initialMode: initialMode,
            autoOpenPhotoPicker: autoOpenPhotoPicker,
            autoParseOnPick: autoParseOnPick
        )
    }

    public static func parse(urlString: String, nowMillis: Int64 = currentEpochMillis()) -> ExternalBillParams? {
        let trimmed = urlString.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty, let url = URL(string: trimmed) else { return nil }
        return parse(url: url, nowMillis: nowMillis)
    }

    public static func parse(url: URL, nowMillis: Int64 = currentEpochMillis()) -> ExternalBillParams? {
        guard isSupportedURL(url),
              let components = URLComponents(url: url, resolvingAgainstBaseURL: false) else {
            return nil
        }

        let (params, lowerParams) = queryMaps(from: components)

        func getParam(_ key: String) -> String? {
            params[key] ?? lowerParams[key.lowercased()]
        }

        guard let moneyRaw = getParam("money") ?? getParam("amount"),
              let amountMicros = MoneyAmount.parseToMicros(moneyRaw),
              amountMicros > 0 else {
            return nil
        }

        let type = parseType(getParam("type"))
        let category = nonEmptyTrimmed(getParam("category"))
        let account = nonEmptyTrimmed(getParam("account") ?? getParam("accountName"))
        let toAccount = nonEmptyTrimmed(getParam("toAccount") ?? getParam("toAccountName"))
        let remark = (getParam("remark") ?? getParam("note") ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        let merchantRaw = (getParam("merchant") ?? getParam("shopName") ?? remark)
            .trimmingCharacters(in: .whitespacesAndNewlines)
        let merchant = String(merchantRaw.prefix(40))
        let timestamp = parseTimestamp(getParam("time"), fallbackMillis: nowMillis)
        let currency = nonEmptyTrimmed(getParam("currency")).map { CurrencyCatalog.find($0).code }

        let showPrompt: Bool
        if let rawPrompt = getParam("showPrompt")?.trimmingCharacters(in: .whitespacesAndNewlines).lowercased(),
           !rawPrompt.isEmpty {
            showPrompt = rawPrompt != "false" && rawPrompt != "0"
        } else {
            showPrompt = true
        }

        return ExternalBillParams(
            origAmountMicros: amountMicros,
            type: type,
            categoryName: category,
            accountNameOrHint: account,
            toAccountNameOrHint: toAccount,
            merchant: merchant,
            note: remark,
            timestamp: timestamp,
            currency: currency,
            showFloatingPrompt: showPrompt,
            sourceScheme: url.scheme?.lowercased() ?? "yubrae"
        )
    }

    /// 构造标准 `yubrae://addbill?...` 快捷记账链接（便于 iOS 快捷指令配置与分享）。
    public static func buildURL(
        scheme: String = "yubrae",
        action: String = "addbill",
        amount: String,
        type: String = TransactionType.EXPENSE,
        currency: String? = nil,
        category: String? = nil,
        account: String? = nil,
        merchant: String? = nil,
        remark: String? = nil,
        showPrompt: Bool = true
    ) -> URL? {
        var components = URLComponents()
        components.scheme = scheme
        components.host = action
        var items: [URLQueryItem] = [
            URLQueryItem(name: "money", value: amount),
            URLQueryItem(name: "type", value: type)
        ]
        if let currency, !currency.isEmpty {
            items.append(URLQueryItem(name: "currency", value: currency))
        }
        if let category, !category.isEmpty {
            items.append(URLQueryItem(name: "category", value: category))
        }
        if let account, !account.isEmpty {
            items.append(URLQueryItem(name: "account", value: account))
        }
        if let merchant, !merchant.isEmpty {
            items.append(URLQueryItem(name: "merchant", value: merchant))
        }
        if let remark, !remark.isEmpty {
            items.append(URLQueryItem(name: "remark", value: remark))
        }
        if !showPrompt {
            items.append(URLQueryItem(name: "showPrompt", value: "false"))
        }
        components.queryItems = items
        return components.url
    }

    // MARK: - Private

    private static func extractAction(from url: URL) -> String {
        let host = (url.host ?? "").lowercased()
        if !host.isEmpty {
            return host
        }
        return url.path.trimmingCharacters(in: CharacterSet(charactersIn: "/")).lowercased()
    }

    private static func queryMaps(from components: URLComponents) -> (exact: [String: String], lower: [String: String]) {
        var params: [String: String] = [:]
        var lowerParams: [String: String] = [:]
        for item in components.queryItems ?? [] {
            guard let value = item.value else { continue }
            if params[item.name] == nil {
                params[item.name] = value
            }
            let lowerKey = item.name.lowercased()
            if lowerParams[lowerKey] == nil {
                lowerParams[lowerKey] = value
            }
        }
        return (params, lowerParams)
    }

    private static func parseBoolFlag(_ raw: String?) -> Bool? {
        guard let normalized = raw?.trimmingCharacters(in: .whitespacesAndNewlines).lowercased(),
              !normalized.isEmpty else {
            return nil
        }
        switch normalized {
        case "1", "true", "yes":
            return true
        case "0", "false", "no":
            return false
        default:
            return nil
        }
    }

    private static func parseType(_ raw: String?) -> String {
        let normalized = (raw ?? "").trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        switch normalized {
        case "1", "INCOME", "IN", "收入":
            return TransactionType.INCOME
        case "2", "TRANSFER", "转账":
            return TransactionType.TRANSFER
        default:
            return TransactionType.EXPENSE
        }
    }

    private static func parseTimestamp(_ raw: String?, fallbackMillis: Int64) -> Int64 {
        let cleaned = (raw ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleaned.isEmpty, let numeric = Int64(cleaned), numeric > 0 else {
            return fallbackMillis
        }
        // 10 位及以下视为秒级时间戳，自动换算毫秒
        if numeric <= 9_999_999_999 {
            return numeric * 1000
        }
        return numeric
    }

    private static func nonEmptyTrimmed(_ raw: String?) -> String? {
        let trimmed = (raw ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? nil : trimmed
    }
}

import Foundation

// MARK: - Domain Enums (Aligned 100% with Android Entities.kt & SettingsStore.kt)

public enum AccountGroup: String, CaseIterable, Identifiable, Codable, Sendable {
    case wallet = "WALLET"
    case debit = "DEBIT"
    case credit = "CREDIT"
    case foreign = "FOREIGN"
    case invest = "INVEST"
    case debt = "DEBT"

    public var id: String { rawValue }

    public static let WALLET = AccountGroup.wallet.rawValue
    public static let DEBIT = AccountGroup.debit.rawValue
    public static let CREDIT = AccountGroup.credit.rawValue
    public static let FOREIGN = AccountGroup.foreign.rawValue
    public static let INVEST = AccountGroup.invest.rawValue
    public static let DEBT = AccountGroup.debt.rawValue

    public var labelZh: String {
        Self.labelZh(for: rawValue)
    }

    public static func labelZh(for raw: String) -> String {
        switch raw.uppercased() {
        case WALLET: return "电子钱包与现金"
        case DEBIT: return "储蓄卡"
        case CREDIT: return "信用卡"
        case FOREIGN: return "外币账户"
        case INVEST: return "理财投资"
        case DEBT: return "借贷与报销"
        default: return "其他账户"
        }
    }

    public var sfSymbol: String {
        switch self {
        case .wallet: return "wallet.pass.fill"
        case .debit: return "building.columns.fill"
        case .credit: return "creditcard.fill"
        case .foreign: return "globe"
        case .invest: return "chart.line.uptrend.xyaxis"
        case .debt: return "arrow.left.arrow.right.circle.fill"
        }
    }
}

public enum TransactionType: String, CaseIterable, Identifiable, Codable, Sendable {
    case expense = "EXPENSE"
    case income = "INCOME"
    case transfer = "TRANSFER"
    case adjust = "ADJUST"

    public var id: String { rawValue }

    public static let EXPENSE = TransactionType.expense.rawValue
    public static let INCOME = TransactionType.income.rawValue
    public static let TRANSFER = TransactionType.transfer.rawValue
    public static let ADJUST = TransactionType.adjust.rawValue

    public var labelZh: String {
        switch self {
        case .expense: return "支出"
        case .income: return "收入"
        case .transfer: return "转账"
        case .adjust: return "余额校准"
        }
    }
}

public enum TransactionStatus: String, CaseIterable, Identifiable, Codable, Sendable {
    case confirmed = "CONFIRMED"
    case pending = "PENDING"

    public var id: String { rawValue }

    public static let CONFIRMED = TransactionStatus.confirmed.rawValue
    public static let PENDING = TransactionStatus.pending.rawValue
}

public enum ReimburseStatus: String, CaseIterable, Identifiable, Codable, Sendable {
    case none = "NONE"
    case pending = "PENDING"
    case reimbursed = "REIMBURSED"

    public var id: String { rawValue }

    public static let NONE = ReimburseStatus.none.rawValue
    public static let PENDING = ReimburseStatus.pending.rawValue
    public static let REIMBURSED = ReimburseStatus.reimbursed.rawValue

    public var labelZh: String {
        switch self {
        case .none: return "无需报销"
        case .pending: return "待报销"
        case .reimbursed: return "已报销"
        }
    }
}

public enum DebtDirection: String, CaseIterable, Identifiable, Codable, Sendable {
    case lend = "LEND"
    case borrow = "BORROW"

    public var id: String { rawValue }

    public static let LEND = DebtDirection.lend.rawValue
    public static let BORROW = DebtDirection.borrow.rawValue

    public var labelZh: String {
        switch self {
        case .lend: return "借出"
        case .borrow: return "借入"
        }
    }
}

public enum DebtStatus: String, CaseIterable, Identifiable, Codable, Sendable {
    case open = "OPEN"
    case settled = "SETTLED"

    public var id: String { rawValue }

    public static let OPEN = DebtStatus.open.rawValue
    public static let SETTLED = DebtStatus.settled.rawValue
}

public enum CaptureChannel: String, CaseIterable, Identifiable, Codable, Sendable {
    case manual = "MANUAL"
    case notification = "NOTIFICATION"
    case sms = "SMS"
    case screen = "SCREEN"
    case autoAccounting = "AUTO_ACCOUNTING"
    case share = "SHARE"
    case ai = "AI"
    case `import` = "IMPORT"
    case recurring = "RECURRING"

    public var id: String { rawValue }

    public static let MANUAL = CaptureChannel.manual.rawValue
    public static let NOTIFICATION = CaptureChannel.notification.rawValue
    public static let SMS = CaptureChannel.sms.rawValue
    public static let SCREEN = CaptureChannel.screen.rawValue
    public static let AUTO_ACCOUNTING = CaptureChannel.autoAccounting.rawValue
    public static let SHARE = CaptureChannel.share.rawValue
    public static let AI = CaptureChannel.ai.rawValue
    public static let IMPORT = CaptureChannel.`import`.rawValue
    public static let RECURRING = CaptureChannel.recurring.rawValue
}

public enum StatsGranularity: String, CaseIterable, Identifiable, Codable, Sendable {
    case weekly = "WEEKLY"
    case monthly = "MONTHLY"
    case yearly = "YEARLY"
    case custom = "CUSTOM"

    public var id: String { rawValue }

    public static let WEEKLY = StatsGranularity.weekly.rawValue
    public static let MONTHLY = StatsGranularity.monthly.rawValue
    public static let YEARLY = StatsGranularity.yearly.rawValue
    public static let CUSTOM = StatsGranularity.custom.rawValue
}

public enum ThemeMode: String, CaseIterable, Identifiable, Codable, Sendable {
    case system = "SYSTEM"
    case light = "LIGHT"
    case dark = "DARK"

    public var id: String { rawValue }

    public var labelZh: String {
        switch self {
        case .system: return "跟随系统"
        case .light: return "浅色模式"
        case .dark: return "深色模式"
        }
    }
}

public enum ThemeStyle: String, CaseIterable, Identifiable, Codable, Sendable {
    case yimuFresh = "YIMU_FRESH"
    case prismTeal = "PRISM_TEAL"
    case sakuraPink = "SAKURA_PINK"
    case skyBlue = "SKY_BLUE"
    case amberWarm = "AMBER_WARM"
    case lavenderMist = "LAVENDER_MIST"
    case forestMatcha = "FOREST_MATCHA"
    case coralSunset = "CORAL_SUNSET"
    case obsidianMono = "OBSIDIAN_MONO"
    case latteMocha = "LATTE_MOCHA"

    public var id: String { rawValue }

    public static let YIMU_FRESH = ThemeStyle.yimuFresh.rawValue
    public static let PRISM_TEAL = ThemeStyle.prismTeal.rawValue
    public static let SAKURA_PINK = ThemeStyle.sakuraPink.rawValue
    public static let SKY_BLUE = ThemeStyle.skyBlue.rawValue
    public static let AMBER_WARM = ThemeStyle.amberWarm.rawValue
    public static let LAVENDER_MIST = ThemeStyle.lavenderMist.rawValue
    public static let FOREST_MATCHA = ThemeStyle.forestMatcha.rawValue
    public static let CORAL_SUNSET = ThemeStyle.coralSunset.rawValue
    public static let OBSIDIAN_MONO = ThemeStyle.obsidianMono.rawValue
    public static let LATTE_MOCHA = ThemeStyle.latteMocha.rawValue

    public var displayName: String {
        switch self {
        case .yimuFresh: return "薄荷清风"
        case .prismTeal: return "棱镜青蓝"
        case .sakuraPink: return "樱花绯粉"
        case .skyBlue: return "晴空蔚蓝"
        case .amberWarm: return "暖阳琥珀"
        case .lavenderMist: return "鸢尾暮紫"
        case .forestMatcha: return "森系抹茶"
        case .coralSunset: return "丹霞珊瑚"
        case .obsidianMono: return "极简曜石"
        case .latteMocha: return "醇香拿铁"
        }
    }

    public var tagline: String {
        switch self {
        case .yimuFresh: return "清新温和 · 浅绿卡片"
        case .prismTeal: return "冷静克制 · 金融青蓝"
        case .sakuraPink: return "温柔樱粉 · 浆果点缀"
        case .skyBlue: return "清澈通透 · 天空海蓝"
        case .amberWarm: return "温暖日光 · 杏金焦糖"
        case .lavenderMist: return "优雅浅紫 · 莫兰迪韵"
        case .forestMatcha: return "复古苔藓 · 纸感米白"
        case .coralSunset: return "活力暖橘 · 陶土质感"
        case .obsidianMono: return "黑白高对比 · 高级商务"
        case .latteMocha: return "温润咖啡 · 暖棕质感"
        }
    }

    public var primaryColorHex: Int64 {
        switch self {
        case .yimuFresh: return 0xFF2AA876
        case .prismTeal: return 0xFF006874
        case .sakuraPink: return 0xFFC84B78
        case .skyBlue: return 0xFF1976D2
        case .amberWarm: return 0xFFD97706
        case .lavenderMist: return 0xFF7B5EA7
        case .forestMatcha: return 0xFF557A46
        case .coralSunset: return 0xFFE05A47
        case .obsidianMono: return 0xFF181A1F
        case .latteMocha: return 0xFF8C5A3C
        }
    }

    public var containerColorHex: Int64 {
        switch self {
        case .yimuFresh: return 0xFFE4F6EE
        case .prismTeal: return 0xFF9EEFFD
        case .sakuraPink: return 0xFFFFDCE8
        case .skyBlue: return 0xFFDCF0FF
        case .amberWarm: return 0xFFFFEDD5
        case .lavenderMist: return 0xFFEDE4FA
        case .forestMatcha: return 0xFFE4EEDB
        case .coralSunset: return 0xFFFFE1DC
        case .obsidianMono: return 0xFFE4E7EC
        case .latteMocha: return 0xFFF4E6DC
        }
    }

    public static func from(_ raw: String) -> ThemeStyle {
        ThemeStyle(rawValue: raw.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()) ?? .yimuFresh
    }
}

public enum AliasMatchType {
    public static let PACKAGE = "PACKAGE"
    public static let CARD_LAST4 = "CARD_LAST4"
    public static let KEYWORD = "KEYWORD"
    public static let REGEX = "REGEX"
}

public enum AssetHiddenModule {
    public static let REIMBURSE = "REIMBURSE"
    public static let DEBT = "DEBT"
    public static let INVEST = "INVEST"
    public static let all: [String] = [REIMBURSE, DEBT, INVEST]
}

/// 标签逗号文本编解码（与 Android `TagTextCodec` 完全一致）。
public enum TagTextCodec {
    private static let separators = CharacterSet(charactersIn: ",，;；#")

    public static func parse(_ raw: String) -> [String] {
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return [] }
        var seen = Set<String>()
        var result: [String] = []
        for part in trimmed.components(separatedBy: separators) {
            let item = part.trimmingCharacters(in: .whitespacesAndNewlines)
            if !item.isEmpty, seen.insert(item).inserted {
                result.append(item)
            }
        }
        return result
    }

    public static func format(_ tags: [String]) -> String {
        var seen = Set<String>()
        var result: [String] = []
        for raw in tags {
            let item = raw.trimmingCharacters(in: .whitespacesAndNewlines)
            if !item.isEmpty, seen.insert(item).inserted {
                result.append(item)
            }
        }
        return result.joined(separator: ",")
    }

    public static func merge(_ existing: String, with incoming: String) -> String {
        format(parse(existing) + parse(incoming))
    }
}

/// 辅助将 Android 风格 ARGB Int64（如 `0xFF2AA876`）拆解为归一化 RGBA 分量。
public func colorComponents(fromARGBHex hex: Int64) -> (red: Double, green: Double, blue: Double, alpha: Double) {
    let unsigned = UInt64(bitPattern: hex)
    let a = Double((unsigned >> 24) & 0xFF) / 255.0
    let r = Double((unsigned >> 16) & 0xFF) / 255.0
    let g = Double((unsigned >> 8) & 0xFF) / 255.0
    let b = Double(unsigned & 0xFF) / 255.0
    return (r, g, b, a > 0 ? a : 1.0)
}

public func currentEpochMillis() -> Int64 {
    Int64((Date().timeIntervalSince1970 * 1000.0).rounded())
}

// MARK: - AppSettings (Full Local + Syncable Settings)

public struct AppSettings: Codable, Equatable, Sendable {
    public var baseCurrency: String
    public var pinnedCurrencies: [String]
    public var themeMode: ThemeMode
    public var dynamicColor: Bool
    public var themeStyle: String
    public var activeLedgerId: Int64
    public var privacyMaskEnabled: Bool
    public var biometricLockEnabled: Bool
    public var floatingRichEditorEnabled: Bool
    public var notificationCaptureEnabled: Bool
    public var smsCaptureEnabled: Bool
    public var autoAccountingBridgeEnabled: Bool
    public var floatingConfirmEnabled: Bool
    public var floatingAutoConfirmSeconds: Int
    public var defaultPendingWhenUncertain: Bool
    public var llmEnabled: Bool
    public var llmBaseUrl: String
    public var llmApiKey: String
    public var llmModel: String
    public var webDavUrl: String
    public var webDavUsername: String
    public var webDavPassword: String
    public var webDavRemoteDir: String
    public var backupPassphrase: String
    public var autoBackupEnabled: Bool
    public var lastRateSyncMillis: Int64
    public var lastBackupSyncMillis: Int64
    public var oledPureBlack: Bool
    public var greenExpenseMode: Bool
    public var accessibilityOrderDetailEnabled: Bool
    public var hiddenAssetAccountIds: [Int64]
    public var hiddenAssetModules: [String]
    public var hideRecentsPreviewEnabled: Bool
    public var autoCheckUpdateEnabled: Bool
    public var lastUpdateCheckMillis: Int64
    public var ignoredUpdateTag: String
    public var updateMirrorPrefix: String
    public var assetGroupOrder: [String]
    public var collapsedAssetGroups: [String]

    public init(
        baseCurrency: String = CurrencyCatalog.defaultBaseCurrency,
        pinnedCurrencies: [String] = CurrencyCatalog.pinnedCurrencies,
        themeMode: ThemeMode = .system,
        dynamicColor: Bool = false,
        themeStyle: String = ThemeStyle.YIMU_FRESH,
        activeLedgerId: Int64 = 0,
        privacyMaskEnabled: Bool = false,
        biometricLockEnabled: Bool = false,
        floatingRichEditorEnabled: Bool = true,
        notificationCaptureEnabled: Bool = true,
        smsCaptureEnabled: Bool = true,
        autoAccountingBridgeEnabled: Bool = true,
        floatingConfirmEnabled: Bool = true,
        floatingAutoConfirmSeconds: Int = 8,
        defaultPendingWhenUncertain: Bool = false,
        llmEnabled: Bool = false,
        llmBaseUrl: String = "https://api.openai.com/v1",
        llmApiKey: String = "",
        llmModel: String = "gpt-4o-mini",
        webDavUrl: String = "",
        webDavUsername: String = "",
        webDavPassword: String = "",
        webDavRemoteDir: String = "/Yubrae",
        backupPassphrase: String = "",
        autoBackupEnabled: Bool = false,
        lastRateSyncMillis: Int64 = 0,
        lastBackupSyncMillis: Int64 = 0,
        oledPureBlack: Bool = false,
        greenExpenseMode: Bool = false,
        accessibilityOrderDetailEnabled: Bool = true,
        hiddenAssetAccountIds: [Int64] = [],
        hiddenAssetModules: [String] = [],
        hideRecentsPreviewEnabled: Bool = false,
        autoCheckUpdateEnabled: Bool = true,
        lastUpdateCheckMillis: Int64 = 0,
        ignoredUpdateTag: String = "",
        updateMirrorPrefix: String = "",
        assetGroupOrder: [String] = [],
        collapsedAssetGroups: [String] = []
    ) {
        self.baseCurrency = baseCurrency
        self.pinnedCurrencies = pinnedCurrencies
        self.themeMode = themeMode
        self.dynamicColor = dynamicColor
        self.themeStyle = themeStyle
        self.activeLedgerId = activeLedgerId
        self.privacyMaskEnabled = privacyMaskEnabled
        self.biometricLockEnabled = biometricLockEnabled
        self.floatingRichEditorEnabled = floatingRichEditorEnabled
        self.notificationCaptureEnabled = notificationCaptureEnabled
        self.smsCaptureEnabled = smsCaptureEnabled
        self.autoAccountingBridgeEnabled = autoAccountingBridgeEnabled
        self.floatingConfirmEnabled = floatingConfirmEnabled
        self.floatingAutoConfirmSeconds = floatingAutoConfirmSeconds
        self.defaultPendingWhenUncertain = defaultPendingWhenUncertain
        self.llmEnabled = llmEnabled
        self.llmBaseUrl = llmBaseUrl
        self.llmApiKey = llmApiKey
        self.llmModel = llmModel
        self.webDavUrl = webDavUrl
        self.webDavUsername = webDavUsername
        self.webDavPassword = webDavPassword
        self.webDavRemoteDir = webDavRemoteDir
        self.backupPassphrase = backupPassphrase
        self.autoBackupEnabled = autoBackupEnabled
        self.lastRateSyncMillis = lastRateSyncMillis
        self.lastBackupSyncMillis = lastBackupSyncMillis
        self.oledPureBlack = oledPureBlack
        self.greenExpenseMode = greenExpenseMode
        self.accessibilityOrderDetailEnabled = accessibilityOrderDetailEnabled
        self.hiddenAssetAccountIds = hiddenAssetAccountIds
        self.hiddenAssetModules = hiddenAssetModules
        self.hideRecentsPreviewEnabled = hideRecentsPreviewEnabled
        self.autoCheckUpdateEnabled = autoCheckUpdateEnabled
        self.lastUpdateCheckMillis = lastUpdateCheckMillis
        self.ignoredUpdateTag = ignoredUpdateTag
        self.updateMirrorPrefix = updateMirrorPrefix
        self.assetGroupOrder = assetGroupOrder
        self.collapsedAssetGroups = collapsedAssetGroups
    }

    enum CodingKeys: String, CodingKey {
        case baseCurrency, pinnedCurrencies, themeMode, dynamicColor, themeStyle, activeLedgerId, privacyMaskEnabled, biometricLockEnabled, floatingRichEditorEnabled, notificationCaptureEnabled, smsCaptureEnabled, autoAccountingBridgeEnabled, floatingConfirmEnabled, floatingAutoConfirmSeconds, defaultPendingWhenUncertain, llmEnabled, llmBaseUrl, llmApiKey, llmModel, webDavUrl, webDavUsername, webDavPassword, webDavRemoteDir, backupPassphrase, autoBackupEnabled, lastRateSyncMillis, lastBackupSyncMillis, oledPureBlack, greenExpenseMode, accessibilityOrderDetailEnabled, hiddenAssetAccountIds, hiddenAssetModules, hideRecentsPreviewEnabled, autoCheckUpdateEnabled, lastUpdateCheckMillis, ignoredUpdateTag, updateMirrorPrefix, assetGroupOrder, collapsedAssetGroups
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        let d = AppSettings()
        self.baseCurrency = try c.decodeIfPresent(String.self, forKey: .baseCurrency) ?? d.baseCurrency
        self.pinnedCurrencies = try c.decodeIfPresent([String].self, forKey: .pinnedCurrencies) ?? d.pinnedCurrencies
        if let modeStr = try c.decodeIfPresent(String.self, forKey: .themeMode),
           let parsedMode = ThemeMode(rawValue: modeStr.uppercased()) {
            self.themeMode = parsedMode
        } else {
            self.themeMode = d.themeMode
        }
        self.dynamicColor = try c.decodeIfPresent(Bool.self, forKey: .dynamicColor) ?? d.dynamicColor
        self.themeStyle = try c.decodeIfPresent(String.self, forKey: .themeStyle) ?? d.themeStyle
        self.activeLedgerId = try c.decodeIfPresent(Int64.self, forKey: .activeLedgerId) ?? d.activeLedgerId
        self.privacyMaskEnabled = try c.decodeIfPresent(Bool.self, forKey: .privacyMaskEnabled) ?? d.privacyMaskEnabled
        self.biometricLockEnabled = try c.decodeIfPresent(Bool.self, forKey: .biometricLockEnabled) ?? d.biometricLockEnabled
        self.floatingRichEditorEnabled = try c.decodeIfPresent(Bool.self, forKey: .floatingRichEditorEnabled) ?? d.floatingRichEditorEnabled
        self.notificationCaptureEnabled = try c.decodeIfPresent(Bool.self, forKey: .notificationCaptureEnabled) ?? d.notificationCaptureEnabled
        self.smsCaptureEnabled = try c.decodeIfPresent(Bool.self, forKey: .smsCaptureEnabled) ?? d.smsCaptureEnabled
        self.autoAccountingBridgeEnabled = try c.decodeIfPresent(Bool.self, forKey: .autoAccountingBridgeEnabled) ?? d.autoAccountingBridgeEnabled
        self.floatingConfirmEnabled = try c.decodeIfPresent(Bool.self, forKey: .floatingConfirmEnabled) ?? d.floatingConfirmEnabled
        self.floatingAutoConfirmSeconds = try c.decodeIfPresent(Int.self, forKey: .floatingAutoConfirmSeconds) ?? d.floatingAutoConfirmSeconds
        self.defaultPendingWhenUncertain = try c.decodeIfPresent(Bool.self, forKey: .defaultPendingWhenUncertain) ?? d.defaultPendingWhenUncertain
        self.llmEnabled = try c.decodeIfPresent(Bool.self, forKey: .llmEnabled) ?? d.llmEnabled
        self.llmBaseUrl = try c.decodeIfPresent(String.self, forKey: .llmBaseUrl) ?? d.llmBaseUrl
        self.llmApiKey = try c.decodeIfPresent(String.self, forKey: .llmApiKey) ?? d.llmApiKey
        self.llmModel = try c.decodeIfPresent(String.self, forKey: .llmModel) ?? d.llmModel
        self.webDavUrl = try c.decodeIfPresent(String.self, forKey: .webDavUrl) ?? d.webDavUrl
        self.webDavUsername = try c.decodeIfPresent(String.self, forKey: .webDavUsername) ?? d.webDavUsername
        self.webDavPassword = try c.decodeIfPresent(String.self, forKey: .webDavPassword) ?? d.webDavPassword
        self.webDavRemoteDir = try c.decodeIfPresent(String.self, forKey: .webDavRemoteDir) ?? d.webDavRemoteDir
        self.backupPassphrase = try c.decodeIfPresent(String.self, forKey: .backupPassphrase) ?? d.backupPassphrase
        self.autoBackupEnabled = try c.decodeIfPresent(Bool.self, forKey: .autoBackupEnabled) ?? d.autoBackupEnabled
        self.lastRateSyncMillis = try c.decodeIfPresent(Int64.self, forKey: .lastRateSyncMillis) ?? d.lastRateSyncMillis
        self.lastBackupSyncMillis = try c.decodeIfPresent(Int64.self, forKey: .lastBackupSyncMillis) ?? d.lastBackupSyncMillis
        self.oledPureBlack = try c.decodeIfPresent(Bool.self, forKey: .oledPureBlack) ?? d.oledPureBlack
        self.greenExpenseMode = try c.decodeIfPresent(Bool.self, forKey: .greenExpenseMode) ?? d.greenExpenseMode
        self.accessibilityOrderDetailEnabled = try c.decodeIfPresent(Bool.self, forKey: .accessibilityOrderDetailEnabled) ?? d.accessibilityOrderDetailEnabled
        self.hiddenAssetAccountIds = try c.decodeIfPresent([Int64].self, forKey: .hiddenAssetAccountIds) ?? d.hiddenAssetAccountIds
        self.hiddenAssetModules = try c.decodeIfPresent([String].self, forKey: .hiddenAssetModules) ?? d.hiddenAssetModules
        self.hideRecentsPreviewEnabled = try c.decodeIfPresent(Bool.self, forKey: .hideRecentsPreviewEnabled) ?? d.hideRecentsPreviewEnabled
        self.autoCheckUpdateEnabled = try c.decodeIfPresent(Bool.self, forKey: .autoCheckUpdateEnabled) ?? d.autoCheckUpdateEnabled
        self.lastUpdateCheckMillis = try c.decodeIfPresent(Int64.self, forKey: .lastUpdateCheckMillis) ?? d.lastUpdateCheckMillis
        self.ignoredUpdateTag = try c.decodeIfPresent(String.self, forKey: .ignoredUpdateTag) ?? d.ignoredUpdateTag
        self.updateMirrorPrefix = try c.decodeIfPresent(String.self, forKey: .updateMirrorPrefix) ?? d.updateMirrorPrefix
        self.assetGroupOrder = try c.decodeIfPresent([String].self, forKey: .assetGroupOrder) ?? d.assetGroupOrder
        self.collapsedAssetGroups = try c.decodeIfPresent([String].self, forKey: .collapsedAssetGroups) ?? d.collapsedAssetGroups
    }

    private static func normalizedOrderedUniqueStrings(_ raw: [String]) -> [String] {
        var seen = Set<String>()
        var result: [String] = []
        for entry in raw {
            let trimmed = entry.trimmingCharacters(in: .whitespacesAndNewlines)
            if !trimmed.isEmpty, seen.insert(trimmed).inserted {
                result.append(trimmed)
            }
        }
        return result
    }

    public func toSnapshotDto() -> SettingsSnapshotDto {
        SettingsSnapshotDto(
            baseCurrency: baseCurrency,
            pinnedCurrencies: pinnedCurrencies,
            themeMode: themeMode.rawValue,
            dynamicColor: dynamicColor,
            themeStyle: themeStyle,
            activeLedgerId: activeLedgerId,
            privacyMaskEnabled: privacyMaskEnabled,
            biometricLockEnabled: biometricLockEnabled,
            floatingRichEditorEnabled: floatingRichEditorEnabled,
            notificationCaptureEnabled: notificationCaptureEnabled,
            smsCaptureEnabled: smsCaptureEnabled,
            autoAccountingBridgeEnabled: autoAccountingBridgeEnabled,
            floatingConfirmEnabled: floatingConfirmEnabled,
            floatingAutoConfirmSeconds: floatingAutoConfirmSeconds,
            defaultPendingWhenUncertain: defaultPendingWhenUncertain,
            llmEnabled: llmEnabled,
            llmBaseUrl: llmBaseUrl,
            llmModel: llmModel,
            webDavRemoteDir: webDavRemoteDir,
            autoBackupEnabled: autoBackupEnabled,
            oledPureBlack: oledPureBlack,
            greenExpenseMode: greenExpenseMode,
            accessibilityOrderDetailEnabled: accessibilityOrderDetailEnabled,
            hiddenAssetAccountIds: Array(Set(hiddenAssetAccountIds)).sorted(),
            hiddenAssetModules: Array(Set(hiddenAssetModules.map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }.filter { !$0.isEmpty })).sorted(),
            hideRecentsPreviewEnabled: hideRecentsPreviewEnabled,
            assetGroupOrder: Self.normalizedOrderedUniqueStrings(assetGroupOrder),
            collapsedAssetGroups: Array(Set(collapsedAssetGroups.map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }.filter { !$0.isEmpty })).sorted()
        )
    }

    public func applying(_ dto: SettingsSnapshotDto) -> AppSettings {
        var copy = self
        copy.baseCurrency = dto.baseCurrency.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            ? CurrencyCatalog.defaultBaseCurrency
            : dto.baseCurrency.uppercased()
        copy.pinnedCurrencies = dto.pinnedCurrencies.isEmpty ? CurrencyCatalog.pinnedCurrencies : dto.pinnedCurrencies
        copy.themeMode = ThemeMode(rawValue: dto.themeMode.uppercased()) ?? .system
        copy.dynamicColor = dto.dynamicColor
        copy.themeStyle = dto.themeStyle.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            ? ThemeStyle.YIMU_FRESH
            : dto.themeStyle
        copy.activeLedgerId = dto.activeLedgerId
        copy.privacyMaskEnabled = dto.privacyMaskEnabled
        copy.biometricLockEnabled = dto.biometricLockEnabled
        copy.floatingRichEditorEnabled = dto.floatingRichEditorEnabled
        copy.notificationCaptureEnabled = dto.notificationCaptureEnabled
        copy.smsCaptureEnabled = dto.smsCaptureEnabled
        copy.autoAccountingBridgeEnabled = dto.autoAccountingBridgeEnabled
        copy.floatingConfirmEnabled = dto.floatingConfirmEnabled
        copy.floatingAutoConfirmSeconds = dto.floatingAutoConfirmSeconds
        copy.defaultPendingWhenUncertain = dto.defaultPendingWhenUncertain
        copy.llmEnabled = dto.llmEnabled
        if !dto.llmBaseUrl.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            copy.llmBaseUrl = dto.llmBaseUrl
        }
        if !dto.llmModel.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            copy.llmModel = dto.llmModel
        }
        if !dto.webDavRemoteDir.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            copy.webDavRemoteDir = dto.webDavRemoteDir
        }
        copy.autoBackupEnabled = dto.autoBackupEnabled
        copy.oledPureBlack = dto.oledPureBlack
        copy.greenExpenseMode = dto.greenExpenseMode
        copy.accessibilityOrderDetailEnabled = dto.accessibilityOrderDetailEnabled
        copy.hiddenAssetAccountIds = Array(Set(dto.hiddenAssetAccountIds)).sorted()
        copy.hiddenAssetModules = Array(Set(dto.hiddenAssetModules.map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }.filter { !$0.isEmpty })).sorted()
        copy.hideRecentsPreviewEnabled = dto.hideRecentsPreviewEnabled
        copy.assetGroupOrder = Self.normalizedOrderedUniqueStrings(dto.assetGroupOrder)
        copy.collapsedAssetGroups = Array(Set(dto.collapsedAssetGroups.map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }.filter { !$0.isEmpty })).sorted()
        return copy
    }
}

// MARK: - KairoBackupSnapshot V3 & 14 DTO Models (100% Android BackupModels.kt Compatible)

public struct KairoBackupSnapshot: Codable, Equatable, Sendable {
    public static let currentFormatVersion = 3
    public static let appName = "Yubrae"
    public static let legacyAppName = "KairoLedger"

    public var formatVersion: Int
    public var exportedAtMillis: Int64
    public var appName: String
    public var settings: SettingsSnapshotDto
    public var accounts: [AccountDto]
    public var categories: [CategoryDto]
    public var transactions: [TransactionDto]
    public var exchangeRates: [ExchangeRateDto]
    public var accountAliases: [AccountAliasDto]
    public var parseRules: [ParseRuleDto]
    public var rawCaptureLogs: [RawCaptureLogDto]
    public var budgets: [BudgetDto]
    public var recurringRules: [RecurringRuleDto]
    public var ledgers: [LedgerDto]
    public var debts: [DebtRecordDto]
    public var installments: [InstallmentPlanDto]
    public var tags: [TagDto]

    public init(
        formatVersion: Int = KairoBackupSnapshot.currentFormatVersion,
        exportedAtMillis: Int64 = currentEpochMillis(),
        appName: String = KairoBackupSnapshot.appName,
        settings: SettingsSnapshotDto = SettingsSnapshotDto(),
        accounts: [AccountDto] = [],
        categories: [CategoryDto] = [],
        transactions: [TransactionDto] = [],
        exchangeRates: [ExchangeRateDto] = [],
        accountAliases: [AccountAliasDto] = [],
        parseRules: [ParseRuleDto] = [],
        rawCaptureLogs: [RawCaptureLogDto] = [],
        budgets: [BudgetDto] = [],
        recurringRules: [RecurringRuleDto] = [],
        ledgers: [LedgerDto] = [],
        debts: [DebtRecordDto] = [],
        installments: [InstallmentPlanDto] = [],
        tags: [TagDto] = []
    ) {
        self.formatVersion = formatVersion
        self.exportedAtMillis = exportedAtMillis
        self.appName = appName
        self.settings = settings
        self.accounts = accounts
        self.categories = categories
        self.transactions = transactions
        self.exchangeRates = exchangeRates
        self.accountAliases = accountAliases
        self.parseRules = parseRules
        self.rawCaptureLogs = rawCaptureLogs
        self.budgets = budgets
        self.recurringRules = recurringRules
        self.ledgers = ledgers
        self.debts = debts
        self.installments = installments
        self.tags = tags
    }

    enum CodingKeys: String, CodingKey {
        case formatVersion, exportedAtMillis, appName, settings, accounts, categories, transactions, exchangeRates, accountAliases, parseRules, rawCaptureLogs, budgets, recurringRules, ledgers, debts, installments, tags
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        self.formatVersion = try c.decodeIfPresent(Int.self, forKey: .formatVersion) ?? Self.currentFormatVersion
        self.exportedAtMillis = try c.decodeIfPresent(Int64.self, forKey: .exportedAtMillis) ?? currentEpochMillis()
        self.appName = try c.decodeIfPresent(String.self, forKey: .appName) ?? Self.appName
        self.settings = try c.decodeIfPresent(SettingsSnapshotDto.self, forKey: .settings) ?? SettingsSnapshotDto()
        self.accounts = try c.decodeIfPresent([AccountDto].self, forKey: .accounts) ?? []
        self.categories = try c.decodeIfPresent([CategoryDto].self, forKey: .categories) ?? []
        self.transactions = try c.decodeIfPresent([TransactionDto].self, forKey: .transactions) ?? []
        self.exchangeRates = try c.decodeIfPresent([ExchangeRateDto].self, forKey: .exchangeRates) ?? []
        self.accountAliases = try c.decodeIfPresent([AccountAliasDto].self, forKey: .accountAliases) ?? []
        self.parseRules = try c.decodeIfPresent([ParseRuleDto].self, forKey: .parseRules) ?? []
        self.rawCaptureLogs = try c.decodeIfPresent([RawCaptureLogDto].self, forKey: .rawCaptureLogs) ?? []
        self.budgets = try c.decodeIfPresent([BudgetDto].self, forKey: .budgets) ?? []
        self.recurringRules = try c.decodeIfPresent([RecurringRuleDto].self, forKey: .recurringRules) ?? []
        self.ledgers = try c.decodeIfPresent([LedgerDto].self, forKey: .ledgers) ?? []
        self.debts = try c.decodeIfPresent([DebtRecordDto].self, forKey: .debts) ?? []
        self.installments = try c.decodeIfPresent([InstallmentPlanDto].self, forKey: .installments) ?? []
        self.tags = try c.decodeIfPresent([TagDto].self, forKey: .tags) ?? []
    }
}

public struct SettingsSnapshotDto: Codable, Equatable, Sendable {
    public var baseCurrency: String
    public var pinnedCurrencies: [String]
    public var themeMode: String
    public var dynamicColor: Bool
    public var themeStyle: String
    public var activeLedgerId: Int64
    public var privacyMaskEnabled: Bool
    public var biometricLockEnabled: Bool
    public var floatingRichEditorEnabled: Bool
    public var notificationCaptureEnabled: Bool
    public var smsCaptureEnabled: Bool
    public var autoAccountingBridgeEnabled: Bool
    public var floatingConfirmEnabled: Bool
    public var floatingAutoConfirmSeconds: Int
    public var defaultPendingWhenUncertain: Bool
    public var llmEnabled: Bool
    public var llmBaseUrl: String
    public var llmModel: String
    public var webDavRemoteDir: String
    public var autoBackupEnabled: Bool
    public var oledPureBlack: Bool
    public var greenExpenseMode: Bool
    public var accessibilityOrderDetailEnabled: Bool
    public var hiddenAssetAccountIds: [Int64]
    public var hiddenAssetModules: [String]
    public var hideRecentsPreviewEnabled: Bool
    public var assetGroupOrder: [String]
    public var collapsedAssetGroups: [String]

    public init(
        baseCurrency: String = CurrencyCatalog.defaultBaseCurrency,
        pinnedCurrencies: [String] = CurrencyCatalog.pinnedCurrencies,
        themeMode: String = ThemeMode.system.rawValue,
        dynamicColor: Bool = false,
        themeStyle: String = ThemeStyle.YIMU_FRESH,
        activeLedgerId: Int64 = 0,
        privacyMaskEnabled: Bool = false,
        biometricLockEnabled: Bool = false,
        floatingRichEditorEnabled: Bool = true,
        notificationCaptureEnabled: Bool = true,
        smsCaptureEnabled: Bool = true,
        autoAccountingBridgeEnabled: Bool = true,
        floatingConfirmEnabled: Bool = true,
        floatingAutoConfirmSeconds: Int = 8,
        defaultPendingWhenUncertain: Bool = false,
        llmEnabled: Bool = false,
        llmBaseUrl: String = "https://api.openai.com/v1",
        llmModel: String = "gpt-4o-mini",
        webDavRemoteDir: String = "/Yubrae",
        autoBackupEnabled: Bool = false,
        oledPureBlack: Bool = false,
        greenExpenseMode: Bool = false,
        accessibilityOrderDetailEnabled: Bool = true,
        hiddenAssetAccountIds: [Int64] = [],
        hiddenAssetModules: [String] = [],
        hideRecentsPreviewEnabled: Bool = false,
        assetGroupOrder: [String] = [],
        collapsedAssetGroups: [String] = []
    ) {
        self.baseCurrency = baseCurrency
        self.pinnedCurrencies = pinnedCurrencies
        self.themeMode = themeMode
        self.dynamicColor = dynamicColor
        self.themeStyle = themeStyle
        self.activeLedgerId = activeLedgerId
        self.privacyMaskEnabled = privacyMaskEnabled
        self.biometricLockEnabled = biometricLockEnabled
        self.floatingRichEditorEnabled = floatingRichEditorEnabled
        self.notificationCaptureEnabled = notificationCaptureEnabled
        self.smsCaptureEnabled = smsCaptureEnabled
        self.autoAccountingBridgeEnabled = autoAccountingBridgeEnabled
        self.floatingConfirmEnabled = floatingConfirmEnabled
        self.floatingAutoConfirmSeconds = floatingAutoConfirmSeconds
        self.defaultPendingWhenUncertain = defaultPendingWhenUncertain
        self.llmEnabled = llmEnabled
        self.llmBaseUrl = llmBaseUrl
        self.llmModel = llmModel
        self.webDavRemoteDir = webDavRemoteDir
        self.autoBackupEnabled = autoBackupEnabled
        self.oledPureBlack = oledPureBlack
        self.greenExpenseMode = greenExpenseMode
        self.accessibilityOrderDetailEnabled = accessibilityOrderDetailEnabled
        self.hiddenAssetAccountIds = hiddenAssetAccountIds
        self.hiddenAssetModules = hiddenAssetModules
        self.hideRecentsPreviewEnabled = hideRecentsPreviewEnabled
        self.assetGroupOrder = assetGroupOrder
        self.collapsedAssetGroups = collapsedAssetGroups
    }

    enum CodingKeys: String, CodingKey {
        case baseCurrency, pinnedCurrencies, themeMode, dynamicColor, themeStyle, activeLedgerId, privacyMaskEnabled, biometricLockEnabled, floatingRichEditorEnabled, notificationCaptureEnabled, smsCaptureEnabled, autoAccountingBridgeEnabled, floatingConfirmEnabled, floatingAutoConfirmSeconds, defaultPendingWhenUncertain, llmEnabled, llmBaseUrl, llmModel, webDavRemoteDir, autoBackupEnabled, oledPureBlack, greenExpenseMode, accessibilityOrderDetailEnabled, hiddenAssetAccountIds, hiddenAssetModules, hideRecentsPreviewEnabled, assetGroupOrder, collapsedAssetGroups
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        let d = SettingsSnapshotDto()
        self.baseCurrency = try c.decodeIfPresent(String.self, forKey: .baseCurrency) ?? d.baseCurrency
        self.pinnedCurrencies = try c.decodeIfPresent([String].self, forKey: .pinnedCurrencies) ?? d.pinnedCurrencies
        self.themeMode = try c.decodeIfPresent(String.self, forKey: .themeMode) ?? d.themeMode
        self.dynamicColor = try c.decodeIfPresent(Bool.self, forKey: .dynamicColor) ?? d.dynamicColor
        self.themeStyle = try c.decodeIfPresent(String.self, forKey: .themeStyle) ?? d.themeStyle
        self.activeLedgerId = try c.decodeIfPresent(Int64.self, forKey: .activeLedgerId) ?? d.activeLedgerId
        self.privacyMaskEnabled = try c.decodeIfPresent(Bool.self, forKey: .privacyMaskEnabled) ?? d.privacyMaskEnabled
        self.biometricLockEnabled = try c.decodeIfPresent(Bool.self, forKey: .biometricLockEnabled) ?? d.biometricLockEnabled
        self.floatingRichEditorEnabled = try c.decodeIfPresent(Bool.self, forKey: .floatingRichEditorEnabled) ?? d.floatingRichEditorEnabled
        self.notificationCaptureEnabled = try c.decodeIfPresent(Bool.self, forKey: .notificationCaptureEnabled) ?? d.notificationCaptureEnabled
        self.smsCaptureEnabled = try c.decodeIfPresent(Bool.self, forKey: .smsCaptureEnabled) ?? d.smsCaptureEnabled
        self.autoAccountingBridgeEnabled = try c.decodeIfPresent(Bool.self, forKey: .autoAccountingBridgeEnabled) ?? d.autoAccountingBridgeEnabled
        self.floatingConfirmEnabled = try c.decodeIfPresent(Bool.self, forKey: .floatingConfirmEnabled) ?? d.floatingConfirmEnabled
        self.floatingAutoConfirmSeconds = try c.decodeIfPresent(Int.self, forKey: .floatingAutoConfirmSeconds) ?? d.floatingAutoConfirmSeconds
        self.defaultPendingWhenUncertain = try c.decodeIfPresent(Bool.self, forKey: .defaultPendingWhenUncertain) ?? d.defaultPendingWhenUncertain
        self.llmEnabled = try c.decodeIfPresent(Bool.self, forKey: .llmEnabled) ?? d.llmEnabled
        self.llmBaseUrl = try c.decodeIfPresent(String.self, forKey: .llmBaseUrl) ?? d.llmBaseUrl
        self.llmModel = try c.decodeIfPresent(String.self, forKey: .llmModel) ?? d.llmModel
        self.webDavRemoteDir = try c.decodeIfPresent(String.self, forKey: .webDavRemoteDir) ?? d.webDavRemoteDir
        self.autoBackupEnabled = try c.decodeIfPresent(Bool.self, forKey: .autoBackupEnabled) ?? d.autoBackupEnabled
        self.oledPureBlack = try c.decodeIfPresent(Bool.self, forKey: .oledPureBlack) ?? d.oledPureBlack
        self.greenExpenseMode = try c.decodeIfPresent(Bool.self, forKey: .greenExpenseMode) ?? d.greenExpenseMode
        self.accessibilityOrderDetailEnabled = try c.decodeIfPresent(Bool.self, forKey: .accessibilityOrderDetailEnabled) ?? d.accessibilityOrderDetailEnabled
        self.hiddenAssetAccountIds = try c.decodeIfPresent([Int64].self, forKey: .hiddenAssetAccountIds) ?? d.hiddenAssetAccountIds
        self.hiddenAssetModules = try c.decodeIfPresent([String].self, forKey: .hiddenAssetModules) ?? d.hiddenAssetModules
        self.hideRecentsPreviewEnabled = try c.decodeIfPresent(Bool.self, forKey: .hideRecentsPreviewEnabled) ?? d.hideRecentsPreviewEnabled
        self.assetGroupOrder = try c.decodeIfPresent([String].self, forKey: .assetGroupOrder) ?? d.assetGroupOrder
        self.collapsedAssetGroups = try c.decodeIfPresent([String].self, forKey: .collapsedAssetGroups) ?? d.collapsedAssetGroups
    }
}

public struct AccountDto: Identifiable, Hashable, Codable, Sendable {
    public var id: Int64
    public var name: String
    public var groupType: String
    public var currencyCode: String
    public var balanceMicros: Int64
    public var creditLimitMicros: Int64
    public var billingDay: Int?
    public var repaymentDay: Int?
    public var icon: String
    public var colorHex: Int64
    public var includeInNetWorth: Bool
    public var sortOrder: Int
    public var archived: Bool
    public var createdAt: Int64
    public var updatedAt: Int64
    public var note: String

    public init(
        id: Int64 = 0,
        name: String,
        groupType: String,
        currencyCode: String = CurrencyCatalog.defaultBaseCurrency,
        balanceMicros: Int64 = 0,
        creditLimitMicros: Int64 = 0,
        billingDay: Int? = nil,
        repaymentDay: Int? = nil,
        icon: String = "wallet",
        colorHex: Int64 = 0xFF0B57D0,
        includeInNetWorth: Bool = true,
        sortOrder: Int = 0,
        archived: Bool = false,
        createdAt: Int64 = 0,
        updatedAt: Int64 = 0,
        note: String = ""
    ) {
        self.id = id
        self.name = name
        self.groupType = groupType
        self.currencyCode = currencyCode
        self.balanceMicros = balanceMicros
        self.creditLimitMicros = creditLimitMicros
        self.billingDay = billingDay
        self.repaymentDay = repaymentDay
        self.icon = icon
        self.colorHex = colorHex
        self.includeInNetWorth = includeInNetWorth
        self.sortOrder = sortOrder
        self.archived = archived
        self.createdAt = createdAt
        self.updatedAt = updatedAt
        self.note = note
    }

    enum CodingKeys: String, CodingKey {
        case id, name, groupType, currencyCode, balanceMicros, creditLimitMicros, billingDay, repaymentDay, icon, colorHex, includeInNetWorth, sortOrder, archived, createdAt, updatedAt, note
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        self.id = try c.decodeIfPresent(Int64.self, forKey: .id) ?? 0
        self.name = try c.decode(String.self, forKey: .name)
        self.groupType = try c.decodeIfPresent(String.self, forKey: .groupType) ?? AccountGroup.WALLET
        self.currencyCode = try c.decodeIfPresent(String.self, forKey: .currencyCode) ?? CurrencyCatalog.defaultBaseCurrency
        self.balanceMicros = try c.decodeIfPresent(Int64.self, forKey: .balanceMicros) ?? 0
        self.creditLimitMicros = try c.decodeIfPresent(Int64.self, forKey: .creditLimitMicros) ?? 0
        self.billingDay = try c.decodeIfPresent(Int.self, forKey: .billingDay)
        self.repaymentDay = try c.decodeIfPresent(Int.self, forKey: .repaymentDay)
        self.icon = try c.decodeIfPresent(String.self, forKey: .icon) ?? "wallet"
        self.colorHex = try c.decodeIfPresent(Int64.self, forKey: .colorHex) ?? 0xFF0B57D0
        self.includeInNetWorth = try c.decodeIfPresent(Bool.self, forKey: .includeInNetWorth) ?? true
        self.sortOrder = try c.decodeIfPresent(Int.self, forKey: .sortOrder) ?? 0
        self.archived = try c.decodeIfPresent(Bool.self, forKey: .archived) ?? false
        self.createdAt = try c.decodeIfPresent(Int64.self, forKey: .createdAt) ?? 0
        self.updatedAt = try c.decodeIfPresent(Int64.self, forKey: .updatedAt) ?? 0
        self.note = try c.decodeIfPresent(String.self, forKey: .note) ?? ""
    }
}

public struct TransactionDto: Identifiable, Hashable, Codable, Sendable {
    public var id: Int64
    public var type: String
    public var timestamp: Int64
    public var origAmountMicros: Int64
    public var origCurrency: String
    public var accountId: Int64
    public var accountAmountMicros: Int64
    public var accountCurrency: String
    public var toAccountId: Int64?
    public var toAmountMicros: Int64?
    public var toCurrency: String?
    public var rateToAccountMicros: Int64
    public var rateToBaseMicros: Int64
    public var baseCurrency: String
    public var categoryId: Int64?
    public var merchant: String
    public var note: String
    public var tags: String
    public var reimburseStatus: String
    public var sourceType: String
    public var rawEventId: Int64?
    public var status: String
    public var ledgerId: Int64
    public var excludeFromStats: Bool
    public var excludeFromBudget: Bool
    public var refundOfTxId: Int64?
    public var attachmentPaths: String
    public var reimbursedTxId: Int64?
    public var createdAt: Int64
    public var updatedAt: Int64
    public var discountAmountMicros: Int64
    public var feeAmountMicros: Int64

    public init(
        id: Int64 = 0,
        type: String,
        timestamp: Int64,
        origAmountMicros: Int64,
        origCurrency: String,
        accountId: Int64,
        accountAmountMicros: Int64,
        accountCurrency: String,
        toAccountId: Int64? = nil,
        toAmountMicros: Int64? = nil,
        toCurrency: String? = nil,
        rateToAccountMicros: Int64 = MoneyAmount.rateScale,
        rateToBaseMicros: Int64 = MoneyAmount.rateScale,
        baseCurrency: String = CurrencyCatalog.defaultBaseCurrency,
        categoryId: Int64? = nil,
        merchant: String = "",
        note: String = "",
        tags: String = "",
        reimburseStatus: String = ReimburseStatus.NONE,
        sourceType: String = CaptureChannel.MANUAL,
        rawEventId: Int64? = nil,
        status: String = TransactionStatus.CONFIRMED,
        ledgerId: Int64 = 1,
        excludeFromStats: Bool = false,
        excludeFromBudget: Bool = false,
        refundOfTxId: Int64? = nil,
        attachmentPaths: String = "",
        reimbursedTxId: Int64? = nil,
        createdAt: Int64 = 0,
        updatedAt: Int64 = 0,
        discountAmountMicros: Int64 = 0,
        feeAmountMicros: Int64 = 0
    ) {
        self.id = id
        self.type = type
        self.timestamp = timestamp
        self.origAmountMicros = origAmountMicros
        self.origCurrency = origCurrency
        self.accountId = accountId
        self.accountAmountMicros = accountAmountMicros
        self.accountCurrency = accountCurrency
        self.toAccountId = toAccountId
        self.toAmountMicros = toAmountMicros
        self.toCurrency = toCurrency
        self.rateToAccountMicros = rateToAccountMicros
        self.rateToBaseMicros = rateToBaseMicros
        self.baseCurrency = baseCurrency
        self.categoryId = categoryId
        self.merchant = merchant
        self.note = note
        self.tags = tags
        self.reimburseStatus = reimburseStatus
        self.sourceType = sourceType
        self.rawEventId = rawEventId
        self.status = status
        self.ledgerId = ledgerId
        self.excludeFromStats = excludeFromStats
        self.excludeFromBudget = excludeFromBudget
        self.refundOfTxId = refundOfTxId
        self.attachmentPaths = attachmentPaths
        self.reimbursedTxId = reimbursedTxId
        self.createdAt = createdAt
        self.updatedAt = updatedAt
        self.discountAmountMicros = discountAmountMicros
        self.feeAmountMicros = feeAmountMicros
    }

    enum CodingKeys: String, CodingKey {
        case id, type, timestamp, origAmountMicros, origCurrency, accountId, accountAmountMicros, accountCurrency, toAccountId, toAmountMicros, toCurrency, rateToAccountMicros, rateToBaseMicros, baseCurrency, categoryId, merchant, note, tags, reimburseStatus, sourceType, rawEventId, status, ledgerId, excludeFromStats, excludeFromBudget, refundOfTxId, attachmentPaths, reimbursedTxId, createdAt, updatedAt, discountAmountMicros, feeAmountMicros
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        self.id = try c.decodeIfPresent(Int64.self, forKey: .id) ?? 0
        self.type = try c.decode(String.self, forKey: .type)
        self.timestamp = try c.decode(Int64.self, forKey: .timestamp)
        self.origAmountMicros = try c.decode(Int64.self, forKey: .origAmountMicros)
        self.origCurrency = try c.decodeIfPresent(String.self, forKey: .origCurrency) ?? CurrencyCatalog.defaultBaseCurrency
        self.accountId = try c.decode(Int64.self, forKey: .accountId)
        self.accountAmountMicros = try c.decodeIfPresent(Int64.self, forKey: .accountAmountMicros) ?? self.origAmountMicros
        self.accountCurrency = try c.decodeIfPresent(String.self, forKey: .accountCurrency) ?? self.origCurrency
        self.toAccountId = try c.decodeIfPresent(Int64.self, forKey: .toAccountId)
        self.toAmountMicros = try c.decodeIfPresent(Int64.self, forKey: .toAmountMicros)
        self.toCurrency = try c.decodeIfPresent(String.self, forKey: .toCurrency)
        self.rateToAccountMicros = try c.decodeIfPresent(Int64.self, forKey: .rateToAccountMicros) ?? MoneyAmount.rateScale
        self.rateToBaseMicros = try c.decodeIfPresent(Int64.self, forKey: .rateToBaseMicros) ?? MoneyAmount.rateScale
        self.baseCurrency = try c.decodeIfPresent(String.self, forKey: .baseCurrency) ?? CurrencyCatalog.defaultBaseCurrency
        self.categoryId = try c.decodeIfPresent(Int64.self, forKey: .categoryId)
        self.merchant = try c.decodeIfPresent(String.self, forKey: .merchant) ?? ""
        self.note = try c.decodeIfPresent(String.self, forKey: .note) ?? ""
        self.tags = try c.decodeIfPresent(String.self, forKey: .tags) ?? ""
        self.reimburseStatus = try c.decodeIfPresent(String.self, forKey: .reimburseStatus) ?? ReimburseStatus.NONE
        self.sourceType = try c.decodeIfPresent(String.self, forKey: .sourceType) ?? CaptureChannel.MANUAL
        self.rawEventId = try c.decodeIfPresent(Int64.self, forKey: .rawEventId)
        self.status = try c.decodeIfPresent(String.self, forKey: .status) ?? TransactionStatus.CONFIRMED
        self.ledgerId = try c.decodeIfPresent(Int64.self, forKey: .ledgerId) ?? 1
        self.excludeFromStats = try c.decodeIfPresent(Bool.self, forKey: .excludeFromStats) ?? false
        self.excludeFromBudget = try c.decodeIfPresent(Bool.self, forKey: .excludeFromBudget) ?? false
        self.refundOfTxId = try c.decodeIfPresent(Int64.self, forKey: .refundOfTxId)
        self.attachmentPaths = try c.decodeIfPresent(String.self, forKey: .attachmentPaths) ?? ""
        self.reimbursedTxId = try c.decodeIfPresent(Int64.self, forKey: .reimbursedTxId)
        self.createdAt = try c.decodeIfPresent(Int64.self, forKey: .createdAt) ?? 0
        self.updatedAt = try c.decodeIfPresent(Int64.self, forKey: .updatedAt) ?? 0
        self.discountAmountMicros = try c.decodeIfPresent(Int64.self, forKey: .discountAmountMicros) ?? 0
        self.feeAmountMicros = try c.decodeIfPresent(Int64.self, forKey: .feeAmountMicros) ?? 0
    }
}

public struct CategoryDto: Identifiable, Hashable, Codable, Sendable {
    public var id: Int64
    public var parentId: Int64?
    public var name: String
    public var type: String
    public var icon: String
    public var colorHex: Int64
    public var sortOrder: Int
    public var isSystem: Bool

    public init(
        id: Int64 = 0,
        parentId: Int64? = nil,
        name: String,
        type: String,
        icon: String = "category",
        colorHex: Int64 = 0xFF0B57D0,
        sortOrder: Int = 0,
        isSystem: Bool = true
    ) {
        self.id = id
        self.parentId = parentId
        self.name = name
        self.type = type
        self.icon = icon
        self.colorHex = colorHex
        self.sortOrder = sortOrder
        self.isSystem = isSystem
    }

    enum CodingKeys: String, CodingKey {
        case id, parentId, name, type, icon, colorHex, sortOrder, isSystem
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        self.id = try c.decodeIfPresent(Int64.self, forKey: .id) ?? 0
        self.parentId = try c.decodeIfPresent(Int64.self, forKey: .parentId)
        self.name = try c.decode(String.self, forKey: .name)
        self.type = try c.decodeIfPresent(String.self, forKey: .type) ?? TransactionType.EXPENSE
        self.icon = try c.decodeIfPresent(String.self, forKey: .icon) ?? "category"
        self.colorHex = try c.decodeIfPresent(Int64.self, forKey: .colorHex) ?? 0xFF0B57D0
        self.sortOrder = try c.decodeIfPresent(Int.self, forKey: .sortOrder) ?? 0
        self.isSystem = try c.decodeIfPresent(Bool.self, forKey: .isSystem) ?? true
    }
}

public struct ExchangeRateDto: Identifiable, Hashable, Codable, Sendable {
    public var id: Int64
    public var baseCurrency: String
    public var targetCurrency: String
    public var rateMicros: Int64
    public var date: String
    public var source: String
    public var updatedAt: Int64

    public init(
        id: Int64 = 0,
        baseCurrency: String,
        targetCurrency: String,
        rateMicros: Int64,
        date: String,
        source: String,
        updatedAt: Int64 = 0
    ) {
        self.id = id
        self.baseCurrency = baseCurrency
        self.targetCurrency = targetCurrency
        self.rateMicros = rateMicros
        self.date = date
        self.source = source
        self.updatedAt = updatedAt
    }

    enum CodingKeys: String, CodingKey {
        case id, baseCurrency, targetCurrency, rateMicros, date, source, updatedAt
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        self.id = try c.decodeIfPresent(Int64.self, forKey: .id) ?? 0
        self.baseCurrency = try c.decode(String.self, forKey: .baseCurrency)
        self.targetCurrency = try c.decode(String.self, forKey: .targetCurrency)
        self.rateMicros = try c.decode(Int64.self, forKey: .rateMicros)
        self.date = try c.decodeIfPresent(String.self, forKey: .date) ?? ""
        self.source = try c.decodeIfPresent(String.self, forKey: .source) ?? "BUILTIN"
        self.updatedAt = try c.decodeIfPresent(Int64.self, forKey: .updatedAt) ?? 0
    }
}

public struct AccountAliasDto: Identifiable, Hashable, Codable, Sendable {
    public var id: Int64
    public var accountId: Int64
    public var matchType: String
    public var pattern: String
    public var priority: Int
    public var enabled: Bool

    public init(
        id: Int64 = 0,
        accountId: Int64,
        matchType: String,
        pattern: String,
        priority: Int = 50,
        enabled: Bool = true
    ) {
        self.id = id
        self.accountId = accountId
        self.matchType = matchType
        self.pattern = pattern
        self.priority = priority
        self.enabled = enabled
    }

    enum CodingKeys: String, CodingKey {
        case id, accountId, matchType, pattern, priority, enabled
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        self.id = try c.decodeIfPresent(Int64.self, forKey: .id) ?? 0
        self.accountId = try c.decode(Int64.self, forKey: .accountId)
        self.matchType = try c.decodeIfPresent(String.self, forKey: .matchType) ?? AliasMatchType.KEYWORD
        self.pattern = try c.decode(String.self, forKey: .pattern)
        self.priority = try c.decodeIfPresent(Int.self, forKey: .priority) ?? 50
        self.enabled = try c.decodeIfPresent(Bool.self, forKey: .enabled) ?? true
    }
}

public struct ParseRuleDto: Identifiable, Hashable, Codable, Sendable {
    public var id: Int64
    public var name: String
    public var sourcePackage: String
    public var channelType: String
    public var regexPattern: String
    public var transactionType: String
    public var amountGroup: Int
    public var currencyGroup: Int
    public var defaultCurrency: String
    public var accountHintGroup: Int
    public var merchantGroup: Int
    public var defaultCategoryName: String
    public var priority: Int
    public var enabled: Bool
    public var isBuiltIn: Bool

    public init(
        id: Int64 = 0,
        name: String,
        sourcePackage: String = "*",
        channelType: String = "ANY",
        regexPattern: String,
        transactionType: String = TransactionType.EXPENSE,
        amountGroup: Int = 1,
        currencyGroup: Int = 0,
        defaultCurrency: String = CurrencyCatalog.defaultBaseCurrency,
        accountHintGroup: Int = 0,
        merchantGroup: Int = 0,
        defaultCategoryName: String = "其他支出",
        priority: Int = 50,
        enabled: Bool = true,
        isBuiltIn: Bool = false
    ) {
        self.id = id
        self.name = name
        self.sourcePackage = sourcePackage
        self.channelType = channelType
        self.regexPattern = regexPattern
        self.transactionType = transactionType
        self.amountGroup = amountGroup
        self.currencyGroup = currencyGroup
        self.defaultCurrency = defaultCurrency
        self.accountHintGroup = accountHintGroup
        self.merchantGroup = merchantGroup
        self.defaultCategoryName = defaultCategoryName
        self.priority = priority
        self.enabled = enabled
        self.isBuiltIn = isBuiltIn
    }

    enum CodingKeys: String, CodingKey {
        case id, name, sourcePackage, channelType, regexPattern, transactionType, amountGroup, currencyGroup, defaultCurrency, accountHintGroup, merchantGroup, defaultCategoryName, priority, enabled, isBuiltIn
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        self.id = try c.decodeIfPresent(Int64.self, forKey: .id) ?? 0
        self.name = try c.decode(String.self, forKey: .name)
        self.sourcePackage = try c.decodeIfPresent(String.self, forKey: .sourcePackage) ?? "*"
        self.channelType = try c.decodeIfPresent(String.self, forKey: .channelType) ?? "ANY"
        self.regexPattern = try c.decode(String.self, forKey: .regexPattern)
        self.transactionType = try c.decodeIfPresent(String.self, forKey: .transactionType) ?? TransactionType.EXPENSE
        self.amountGroup = try c.decodeIfPresent(Int.self, forKey: .amountGroup) ?? 1
        self.currencyGroup = try c.decodeIfPresent(Int.self, forKey: .currencyGroup) ?? 0
        self.defaultCurrency = try c.decodeIfPresent(String.self, forKey: .defaultCurrency) ?? CurrencyCatalog.defaultBaseCurrency
        self.accountHintGroup = try c.decodeIfPresent(Int.self, forKey: .accountHintGroup) ?? 0
        self.merchantGroup = try c.decodeIfPresent(Int.self, forKey: .merchantGroup) ?? 0
        self.defaultCategoryName = try c.decodeIfPresent(String.self, forKey: .defaultCategoryName) ?? "其他支出"
        self.priority = try c.decodeIfPresent(Int.self, forKey: .priority) ?? 50
        self.enabled = try c.decodeIfPresent(Bool.self, forKey: .enabled) ?? true
        self.isBuiltIn = try c.decodeIfPresent(Bool.self, forKey: .isBuiltIn) ?? false
    }
}

public struct RawCaptureLogDto: Identifiable, Hashable, Codable, Sendable {
    public var id: Int64
    public var channel: String
    public var sourcePackage: String
    public var title: String
    public var content: String
    public var capturedAt: Int64
    public var parseStatus: String
    public var parserUsed: String
    public var extractedJson: String
    public var linkedTransactionId: Int64?
    public var errorMessage: String

    public init(
        id: Int64 = 0,
        channel: String,
        sourcePackage: String,
        title: String,
        content: String,
        capturedAt: Int64 = 0,
        parseStatus: String,
        parserUsed: String = "",
        extractedJson: String = "",
        linkedTransactionId: Int64? = nil,
        errorMessage: String = ""
    ) {
        self.id = id
        self.channel = channel
        self.sourcePackage = sourcePackage
        self.title = title
        self.content = content
        self.capturedAt = capturedAt
        self.parseStatus = parseStatus
        self.parserUsed = parserUsed
        self.extractedJson = extractedJson
        self.linkedTransactionId = linkedTransactionId
        self.errorMessage = errorMessage
    }

    enum CodingKeys: String, CodingKey {
        case id, channel, sourcePackage, title, content, capturedAt, parseStatus, parserUsed, extractedJson, linkedTransactionId, errorMessage
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        self.id = try c.decodeIfPresent(Int64.self, forKey: .id) ?? 0
        self.channel = try c.decodeIfPresent(String.self, forKey: .channel) ?? CaptureChannel.MANUAL
        self.sourcePackage = try c.decodeIfPresent(String.self, forKey: .sourcePackage) ?? ""
        self.title = try c.decodeIfPresent(String.self, forKey: .title) ?? ""
        self.content = try c.decodeIfPresent(String.self, forKey: .content) ?? ""
        self.capturedAt = try c.decodeIfPresent(Int64.self, forKey: .capturedAt) ?? 0
        self.parseStatus = try c.decodeIfPresent(String.self, forKey: .parseStatus) ?? "IGNORED"
        self.parserUsed = try c.decodeIfPresent(String.self, forKey: .parserUsed) ?? ""
        self.extractedJson = try c.decodeIfPresent(String.self, forKey: .extractedJson) ?? ""
        self.linkedTransactionId = try c.decodeIfPresent(Int64.self, forKey: .linkedTransactionId)
        self.errorMessage = try c.decodeIfPresent(String.self, forKey: .errorMessage) ?? ""
    }
}

public struct BudgetDto: Identifiable, Hashable, Codable, Sendable {
    public var id: Int64
    public var name: String
    public var categoryId: Int64?
    public var period: String
    public var limitAmountMicros: Int64
    public var currencyCode: String
    public var enabled: Bool
    public var ledgerId: Int64
    public var rolloverEnabled: Bool

    public init(
        id: Int64 = 0,
        name: String,
        categoryId: Int64? = nil,
        period: String = "MONTHLY",
        limitAmountMicros: Int64,
        currencyCode: String = CurrencyCatalog.defaultBaseCurrency,
        enabled: Bool = true,
        ledgerId: Int64 = 1,
        rolloverEnabled: Bool = false
    ) {
        self.id = id
        self.name = name
        self.categoryId = categoryId
        self.period = period
        self.limitAmountMicros = limitAmountMicros
        self.currencyCode = currencyCode
        self.enabled = enabled
        self.ledgerId = ledgerId
        self.rolloverEnabled = rolloverEnabled
    }

    enum CodingKeys: String, CodingKey {
        case id, name, categoryId, period, limitAmountMicros, currencyCode, enabled, ledgerId, rolloverEnabled
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        self.id = try c.decodeIfPresent(Int64.self, forKey: .id) ?? 0
        self.name = try c.decode(String.self, forKey: .name)
        self.categoryId = try c.decodeIfPresent(Int64.self, forKey: .categoryId)
        self.period = try c.decodeIfPresent(String.self, forKey: .period) ?? "MONTHLY"
        self.limitAmountMicros = try c.decode(Int64.self, forKey: .limitAmountMicros)
        self.currencyCode = try c.decodeIfPresent(String.self, forKey: .currencyCode) ?? CurrencyCatalog.defaultBaseCurrency
        self.enabled = try c.decodeIfPresent(Bool.self, forKey: .enabled) ?? true
        self.ledgerId = try c.decodeIfPresent(Int64.self, forKey: .ledgerId) ?? 1
        self.rolloverEnabled = try c.decodeIfPresent(Bool.self, forKey: .rolloverEnabled) ?? false
    }
}

public struct LedgerDto: Identifiable, Hashable, Codable, Sendable {
    public var id: Int64
    public var name: String
    public var currencyCode: String
    public var icon: String
    public var colorHex: Int64
    public var sortOrder: Int
    public var archived: Bool
    public var createdAt: Int64

    public init(
        id: Int64 = 0,
        name: String,
        currencyCode: String = CurrencyCatalog.defaultBaseCurrency,
        icon: String = "menu_book",
        colorHex: Int64 = 0xFF2AA876,
        sortOrder: Int = 0,
        archived: Bool = false,
        createdAt: Int64 = 0
    ) {
        self.id = id
        self.name = name
        self.currencyCode = currencyCode
        self.icon = icon
        self.colorHex = colorHex
        self.sortOrder = sortOrder
        self.archived = archived
        self.createdAt = createdAt
    }

    enum CodingKeys: String, CodingKey {
        case id, name, currencyCode, icon, colorHex, sortOrder, archived, createdAt
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        self.id = try c.decodeIfPresent(Int64.self, forKey: .id) ?? 0
        self.name = try c.decode(String.self, forKey: .name)
        self.currencyCode = try c.decodeIfPresent(String.self, forKey: .currencyCode) ?? CurrencyCatalog.defaultBaseCurrency
        self.icon = try c.decodeIfPresent(String.self, forKey: .icon) ?? "menu_book"
        self.colorHex = try c.decodeIfPresent(Int64.self, forKey: .colorHex) ?? 0xFF2AA876
        self.sortOrder = try c.decodeIfPresent(Int.self, forKey: .sortOrder) ?? 0
        self.archived = try c.decodeIfPresent(Bool.self, forKey: .archived) ?? false
        self.createdAt = try c.decodeIfPresent(Int64.self, forKey: .createdAt) ?? 0
    }
}

public struct DebtRecordDto: Identifiable, Hashable, Codable, Sendable {
    public var id: Int64
    public var direction: String
    public var personName: String
    public var amountMicros: Int64
    public var settledAmountMicros: Int64
    public var currencyCode: String
    public var accountId: Int64?
    public var dueDateMillis: Int64?
    public var note: String
    public var status: String
    public var createdAt: Int64
    public var updatedAt: Int64
    public var includeInNetWorth: Bool

    public init(
        id: Int64 = 0,
        direction: String,
        personName: String,
        amountMicros: Int64,
        settledAmountMicros: Int64 = 0,
        currencyCode: String = CurrencyCatalog.defaultBaseCurrency,
        accountId: Int64? = nil,
        dueDateMillis: Int64? = nil,
        note: String = "",
        status: String = DebtStatus.OPEN,
        createdAt: Int64 = 0,
        updatedAt: Int64 = 0,
        includeInNetWorth: Bool = false
    ) {
        self.id = id
        self.direction = direction
        self.personName = personName
        self.amountMicros = amountMicros
        self.settledAmountMicros = settledAmountMicros
        self.currencyCode = currencyCode
        self.accountId = accountId
        self.dueDateMillis = dueDateMillis
        self.note = note
        self.status = status
        self.createdAt = createdAt
        self.updatedAt = updatedAt
        self.includeInNetWorth = includeInNetWorth
    }

    enum CodingKeys: String, CodingKey {
        case id, direction, personName, amountMicros, settledAmountMicros, currencyCode, accountId, dueDateMillis, note, status, createdAt, updatedAt, includeInNetWorth
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        self.id = try c.decodeIfPresent(Int64.self, forKey: .id) ?? 0
        self.direction = try c.decodeIfPresent(String.self, forKey: .direction) ?? DebtDirection.LEND
        self.personName = try c.decode(String.self, forKey: .personName)
        self.amountMicros = try c.decode(Int64.self, forKey: .amountMicros)
        self.settledAmountMicros = try c.decodeIfPresent(Int64.self, forKey: .settledAmountMicros) ?? 0
        self.currencyCode = try c.decodeIfPresent(String.self, forKey: .currencyCode) ?? CurrencyCatalog.defaultBaseCurrency
        self.accountId = try c.decodeIfPresent(Int64.self, forKey: .accountId)
        self.dueDateMillis = try c.decodeIfPresent(Int64.self, forKey: .dueDateMillis)
        self.note = try c.decodeIfPresent(String.self, forKey: .note) ?? ""
        self.status = try c.decodeIfPresent(String.self, forKey: .status) ?? DebtStatus.OPEN
        self.createdAt = try c.decodeIfPresent(Int64.self, forKey: .createdAt) ?? 0
        self.updatedAt = try c.decodeIfPresent(Int64.self, forKey: .updatedAt) ?? 0
        self.includeInNetWorth = try c.decodeIfPresent(Bool.self, forKey: .includeInNetWorth) ?? false
    }
}

public struct InstallmentPlanDto: Identifiable, Hashable, Codable, Sendable {
    public var id: Int64
    public var accountId: Int64
    public var name: String
    public var totalAmountMicros: Int64
    public var feePerPeriodMicros: Int64
    public var currencyCode: String
    public var totalPeriods: Int
    public var completedPeriods: Int
    public var billingDay: Int
    public var nextDueMillis: Int64
    public var categoryId: Int64?
    public var enabled: Bool
    public var createdAt: Int64

    public init(
        id: Int64 = 0,
        accountId: Int64,
        name: String,
        totalAmountMicros: Int64,
        feePerPeriodMicros: Int64 = 0,
        currencyCode: String = CurrencyCatalog.defaultBaseCurrency,
        totalPeriods: Int,
        completedPeriods: Int = 0,
        billingDay: Int = 5,
        nextDueMillis: Int64,
        categoryId: Int64? = nil,
        enabled: Bool = true,
        createdAt: Int64 = 0
    ) {
        self.id = id
        self.accountId = accountId
        self.name = name
        self.totalAmountMicros = totalAmountMicros
        self.feePerPeriodMicros = feePerPeriodMicros
        self.currencyCode = currencyCode
        self.totalPeriods = totalPeriods
        self.completedPeriods = completedPeriods
        self.billingDay = billingDay
        self.nextDueMillis = nextDueMillis
        self.categoryId = categoryId
        self.enabled = enabled
        self.createdAt = createdAt
    }

    enum CodingKeys: String, CodingKey {
        case id, accountId, name, totalAmountMicros, feePerPeriodMicros, currencyCode, totalPeriods, completedPeriods, billingDay, nextDueMillis, categoryId, enabled, createdAt
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        self.id = try c.decodeIfPresent(Int64.self, forKey: .id) ?? 0
        self.accountId = try c.decode(Int64.self, forKey: .accountId)
        self.name = try c.decode(String.self, forKey: .name)
        self.totalAmountMicros = try c.decode(Int64.self, forKey: .totalAmountMicros)
        self.feePerPeriodMicros = try c.decodeIfPresent(Int64.self, forKey: .feePerPeriodMicros) ?? 0
        self.currencyCode = try c.decodeIfPresent(String.self, forKey: .currencyCode) ?? CurrencyCatalog.defaultBaseCurrency
        self.totalPeriods = try c.decodeIfPresent(Int.self, forKey: .totalPeriods) ?? 1
        self.completedPeriods = try c.decodeIfPresent(Int.self, forKey: .completedPeriods) ?? 0
        self.billingDay = try c.decodeIfPresent(Int.self, forKey: .billingDay) ?? 5
        self.nextDueMillis = try c.decodeIfPresent(Int64.self, forKey: .nextDueMillis) ?? currentEpochMillis()
        self.categoryId = try c.decodeIfPresent(Int64.self, forKey: .categoryId)
        self.enabled = try c.decodeIfPresent(Bool.self, forKey: .enabled) ?? true
        self.createdAt = try c.decodeIfPresent(Int64.self, forKey: .createdAt) ?? 0
    }
}

public struct RecurringRuleDto: Identifiable, Hashable, Codable, Sendable {
    public var id: Int64
    public var name: String
    public var type: String
    public var amountMicros: Int64
    public var currencyCode: String
    public var accountId: Int64
    public var toAccountId: Int64?
    public var categoryId: Int64?
    public var merchant: String
    public var note: String
    public var cycleType: String
    public var dayOfCycle: Int
    public var nextRunEpochMillis: Int64
    public var enabled: Bool

    public init(
        id: Int64 = 0,
        name: String,
        type: String = TransactionType.EXPENSE,
        amountMicros: Int64,
        currencyCode: String = CurrencyCatalog.defaultBaseCurrency,
        accountId: Int64,
        toAccountId: Int64? = nil,
        categoryId: Int64? = nil,
        merchant: String = "",
        note: String = "",
        cycleType: String = "MONTHLY",
        dayOfCycle: Int = 1,
        nextRunEpochMillis: Int64,
        enabled: Bool = true
    ) {
        self.id = id
        self.name = name
        self.type = type
        self.amountMicros = amountMicros
        self.currencyCode = currencyCode
        self.accountId = accountId
        self.toAccountId = toAccountId
        self.categoryId = categoryId
        self.merchant = merchant
        self.note = note
        self.cycleType = cycleType
        self.dayOfCycle = dayOfCycle
        self.nextRunEpochMillis = nextRunEpochMillis
        self.enabled = enabled
    }

    enum CodingKeys: String, CodingKey {
        case id, name, type, amountMicros, currencyCode, accountId, toAccountId, categoryId, merchant, note, cycleType, dayOfCycle, nextRunEpochMillis, enabled
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        self.id = try c.decodeIfPresent(Int64.self, forKey: .id) ?? 0
        self.name = try c.decode(String.self, forKey: .name)
        self.type = try c.decodeIfPresent(String.self, forKey: .type) ?? TransactionType.EXPENSE
        self.amountMicros = try c.decode(Int64.self, forKey: .amountMicros)
        self.currencyCode = try c.decodeIfPresent(String.self, forKey: .currencyCode) ?? CurrencyCatalog.defaultBaseCurrency
        self.accountId = try c.decode(Int64.self, forKey: .accountId)
        self.toAccountId = try c.decodeIfPresent(Int64.self, forKey: .toAccountId)
        self.categoryId = try c.decodeIfPresent(Int64.self, forKey: .categoryId)
        self.merchant = try c.decodeIfPresent(String.self, forKey: .merchant) ?? ""
        self.note = try c.decodeIfPresent(String.self, forKey: .note) ?? ""
        self.cycleType = try c.decodeIfPresent(String.self, forKey: .cycleType) ?? "MONTHLY"
        self.dayOfCycle = try c.decodeIfPresent(Int.self, forKey: .dayOfCycle) ?? 1
        self.nextRunEpochMillis = try c.decodeIfPresent(Int64.self, forKey: .nextRunEpochMillis) ?? currentEpochMillis()
        self.enabled = try c.decodeIfPresent(Bool.self, forKey: .enabled) ?? true
    }
}

public struct TagDto: Identifiable, Hashable, Codable, Sendable {
    public var id: Int64
    public var name: String
    public var groupName: String
    public var colorHex: Int64
    public var sortOrder: Int
    public var isSystem: Bool
    public var icon: String

    public init(
        id: Int64 = 0,
        name: String,
        groupName: String = "常用",
        colorHex: Int64 = 0xFF2AA876,
        sortOrder: Int = 0,
        isSystem: Bool = true,
        icon: String = ""
    ) {
        self.id = id
        self.name = name
        self.groupName = groupName
        self.colorHex = colorHex
        self.sortOrder = sortOrder
        self.isSystem = isSystem
        self.icon = icon
    }

    enum CodingKeys: String, CodingKey {
        case id, name, groupName, colorHex, sortOrder, isSystem, icon
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        self.id = try c.decodeIfPresent(Int64.self, forKey: .id) ?? 0
        self.name = try c.decode(String.self, forKey: .name)
        self.groupName = try c.decodeIfPresent(String.self, forKey: .groupName) ?? "常用"
        self.colorHex = try c.decodeIfPresent(Int64.self, forKey: .colorHex) ?? 0xFF2AA876
        self.sortOrder = try c.decodeIfPresent(Int.self, forKey: .sortOrder) ?? 0
        self.isSystem = try c.decodeIfPresent(Bool.self, forKey: .isSystem) ?? true
        self.icon = try c.decodeIfPresent(String.self, forKey: .icon) ?? ""
    }
}

// MARK: - Domain Requests, Analytics & Sync Value Objects

public struct RecordTransactionRequest: Sendable {
    public var existingId: Int64?
    public var type: String
    public var timestamp: Int64
    public var origAmountMicros: Int64
    public var origCurrency: String
    public var accountId: Int64
    public var manualAccountAmountMicros: Int64?
    public var manualRateToAccountMicros: Int64?
    public var toAccountId: Int64?
    public var manualToAmountMicros: Int64?
    public var categoryId: Int64?
    public var merchant: String
    public var note: String
    public var tags: String
    public var reimburseStatus: String
    public var sourceType: String
    public var rawEventId: Int64?
    public var status: String
    public var ledgerId: Int64
    public var excludeFromStats: Bool
    public var excludeFromBudget: Bool
    public var refundOfTxId: Int64?
    public var attachmentPaths: String
    public var reimbursedTxId: Int64?
    public var discountAmountMicros: Int64
    public var feeAmountMicros: Int64

    public init(
        existingId: Int64? = nil,
        type: String,
        timestamp: Int64 = currentEpochMillis(),
        origAmountMicros: Int64,
        origCurrency: String = CurrencyCatalog.defaultBaseCurrency,
        accountId: Int64,
        manualAccountAmountMicros: Int64? = nil,
        manualRateToAccountMicros: Int64? = nil,
        toAccountId: Int64? = nil,
        manualToAmountMicros: Int64? = nil,
        categoryId: Int64? = nil,
        merchant: String = "",
        note: String = "",
        tags: String = "",
        reimburseStatus: String = ReimburseStatus.NONE,
        sourceType: String = CaptureChannel.MANUAL,
        rawEventId: Int64? = nil,
        status: String = TransactionStatus.CONFIRMED,
        ledgerId: Int64 = 0,
        excludeFromStats: Bool = false,
        excludeFromBudget: Bool = false,
        refundOfTxId: Int64? = nil,
        attachmentPaths: String = "",
        reimbursedTxId: Int64? = nil,
        discountAmountMicros: Int64 = 0,
        feeAmountMicros: Int64 = 0
    ) {
        self.existingId = existingId
        self.type = type
        self.timestamp = timestamp
        self.origAmountMicros = origAmountMicros
        self.origCurrency = origCurrency
        self.accountId = accountId
        self.manualAccountAmountMicros = manualAccountAmountMicros
        self.manualRateToAccountMicros = manualRateToAccountMicros
        self.toAccountId = toAccountId
        self.manualToAmountMicros = manualToAmountMicros
        self.categoryId = categoryId
        self.merchant = merchant
        self.note = note
        self.tags = tags
        self.reimburseStatus = reimburseStatus
        self.sourceType = sourceType
        self.rawEventId = rawEventId
        self.status = status
        self.ledgerId = ledgerId
        self.excludeFromStats = excludeFromStats
        self.excludeFromBudget = excludeFromBudget
        self.refundOfTxId = refundOfTxId
        self.attachmentPaths = attachmentPaths
        self.reimbursedTxId = reimbursedTxId
        self.discountAmountMicros = discountAmountMicros
        self.feeAmountMicros = feeAmountMicros
    }
}

public struct ConversionPreview: Equatable, Sendable {
    public let origAmountMicros: Int64
    public let origCurrency: String
    public let accountAmountMicros: Int64
    public let accountCurrency: String
    public let rateToAccountMicros: Int64
    public let baseAmountMicros: Int64
    public let baseCurrency: String
    public let rateToBaseMicros: Int64
    public let rateSource: String
}

public struct RateQuote: Equatable, Sendable {
    public let fromCurrency: String
    public let toCurrency: String
    public let rateMicros: Int64
    public let date: String
    public let source: String
}

public struct NetWorthSummary: Equatable, Sendable {
    public let displayCurrency: String
    public let totalAssetsMicros: Int64
    public let totalLiabilitiesMicros: Int64
    public let netWorthMicros: Int64
    public let accountConvertedBalances: [Int64: Int64]
}

public struct CategoryStat: Identifiable, Equatable, Sendable {
    public var id: String { "\(categoryId ?? -1)-\(categoryName)" }
    public let categoryId: Int64?
    public let categoryName: String
    public let colorHex: Int64
    public let icon: String
    public let totalBaseMicros: Int64
    public let count: Int
    public let percentage: Double
}

public struct TagStat: Identifiable, Equatable, Sendable {
    public var id: String { tagName }
    public let tagName: String
    public let colorHex: Int64
    public let totalBaseMicros: Int64
    public let count: Int
    public let percentage: Double
}

public struct MonthlyPoint: Identifiable, Equatable, Sendable {
    public var id: String { "\(year)-\(month)" }
    public let monthLabel: String
    public let year: Int
    public let month: Int
    public let expenseBaseMicros: Int64
    public let incomeBaseMicros: Int64
}

public struct BudgetProgress: Identifiable, Equatable, Sendable {
    public var id: Int64 { budget.id }
    public let budget: BudgetDto
    public let categoryName: String
    public let spentInBudgetCurrencyMicros: Int64
    public let limitInBudgetCurrencyMicros: Int64
    public let progressRatio: Double
    public let overBudget: Bool
    public let rolloverMicros: Int64
    public let dailyAvailableMicros: Int64
    public let remainingDays: Int
}

public struct NetWorthPoint: Identifiable, Equatable, Sendable {
    public var id: Int64 { epochMillis }
    public let label: String
    public let epochMillis: Int64
    public let netWorthMicros: Int64
}

public struct TrendBucketPoint: Identifiable, Equatable, Sendable {
    public var id: String { "\(label)-\(startMillis)" }
    public let label: String
    public let startMillis: Int64
    public let endMillis: Int64
    public let expenseBaseMicros: Int64
    public let incomeBaseMicros: Int64
}

public struct RestoreSummary: Equatable, Sendable {
    public let accountsCount: Int
    public let transactionsCount: Int
    public let categoriesCount: Int
    public let exchangeRatesCount: Int
    public let rulesAndAliasesCount: Int
    public let budgetsAndRecurringCount: Int
    public let captureLogsCount: Int
    public let tagsCount: Int

    public var descriptionZh: String {
        "恢复成功：\(accountsCount) 个账户，\(transactionsCount) 笔交易，\(categoriesCount) 个分类"
    }
}

public struct RemoteBackupFile: Identifiable, Hashable, Sendable {
    public var id: String { path }
    public let name: String
    public let path: String
    public let sizeBytes: Int64
    public let modifiedAtMillis: Int64

    public init(name: String, path: String, sizeBytes: Int64, modifiedAtMillis: Int64) {
        self.name = name
        self.path = path
        self.sizeBytes = sizeBytes
        self.modifiedAtMillis = modifiedAtMillis
    }
}

public struct ParsedBillDraft: Identifiable, Equatable, Sendable {
    public let id: UUID
    public var type: String
    public var origAmountMicros: Int64
    public var origCurrency: String
    public var accountId: Int64?
    public var toAccountId: Int64?
    public var accountHint: String
    public var categoryId: Int64?
    public var categoryName: String
    public var merchant: String
    public var note: String
    public var timestamp: Int64
    public var confidence: Double
    public var parserUsed: String
    public var tags: String

    public init(
        id: UUID = UUID(),
        type: String,
        origAmountMicros: Int64,
        origCurrency: String = CurrencyCatalog.defaultBaseCurrency,
        accountId: Int64? = nil,
        toAccountId: Int64? = nil,
        accountHint: String = "",
        categoryId: Int64? = nil,
        categoryName: String = "",
        merchant: String = "",
        note: String = "",
        timestamp: Int64 = currentEpochMillis(),
        confidence: Double = 0.85,
        parserUsed: String = "LOCAL_SMART_INPUT",
        tags: String = ""
    ) {
        self.id = id
        self.type = type
        self.origAmountMicros = origAmountMicros
        self.origCurrency = origCurrency
        self.accountId = accountId
        self.toAccountId = toAccountId
        self.accountHint = accountHint
        self.categoryId = categoryId
        self.categoryName = categoryName
        self.merchant = merchant
        self.note = note
        self.timestamp = timestamp
        self.confidence = confidence
        self.parserUsed = parserUsed
        self.tags = tags
    }
}

public struct AccountDailyBalancePoint: Identifiable, Hashable, Sendable {
    public let id: String
    public let date: Date
    public let label: String
    public let balanceMicros: Int64

    public init(id: String, date: Date, label: String, balanceMicros: Int64) {
        self.id = id
        self.date = date
        self.label = label
        self.balanceMicros = balanceMicros
    }
}

public struct CreditBillingCycleSummary: Equatable, Sendable {
    public let cycleStartMillis: Int64
    public let cycleEndMillis: Int64
    public let cycleSpendMicros: Int64
    public let cycleRepaidMicros: Int64
    public let daysUntilBilling: Int?
    public let daysUntilRepayment: Int?

    public init(
        cycleStartMillis: Int64,
        cycleEndMillis: Int64,
        cycleSpendMicros: Int64,
        cycleRepaidMicros: Int64,
        daysUntilBilling: Int?,
        daysUntilRepayment: Int?
    ) {
        self.cycleStartMillis = cycleStartMillis
        self.cycleEndMillis = cycleEndMillis
        self.cycleSpendMicros = cycleSpendMicros
        self.cycleRepaidMicros = cycleRepaidMicros
        self.daysUntilBilling = daysUntilBilling
        self.daysUntilRepayment = daysUntilRepayment
    }
}

public struct PeriodDiscountFeeSummary: Equatable, Sendable {
    public let discountBaseMicros: Int64
    public let feeBaseMicros: Int64
    public let discountCount: Int
    public let feeCount: Int

    public init(
        discountBaseMicros: Int64 = 0,
        feeBaseMicros: Int64 = 0,
        discountCount: Int = 0,
        feeCount: Int = 0
    ) {
        self.discountBaseMicros = discountBaseMicros
        self.feeBaseMicros = feeBaseMicros
        self.discountCount = discountCount
        self.feeCount = feeCount
    }
}

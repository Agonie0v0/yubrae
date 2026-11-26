import Combine
import Foundation
import Security

public enum SmartInputMode: Int, CaseIterable, Identifiable, Equatable, Sendable {
    case text = 0
    case receiptImage = 1

    public var id: Int { rawValue }
    public var title: String {
        switch self {
        case .text: return "一句话拆单"
        case .receiptImage: return "小票/截图识图"
        }
    }
}

public struct SmartInputLaunchRequest: Identifiable, Equatable, Sendable {
    public let id: UUID
    public let initialMode: SmartInputMode
    public let autoOpenPhotoPicker: Bool
    public let autoParseOnPick: Bool

    public init(
        id: UUID = UUID(),
        initialMode: SmartInputMode = .text,
        autoOpenPhotoPicker: Bool = false,
        autoParseOnPick: Bool = false
    ) {
        self.id = id
        self.initialMode = initialMode
        self.autoOpenPhotoPicker = autoOpenPhotoPicker
        self.autoParseOnPick = autoParseOnPick
    }
}

public enum LedgerStoreError: LocalizedError, Equatable {
    case unsupportedTransactionType(String)
    case nonPositiveAmount
    case accountNotFound(Int64)
    case sameTransferAccount
    case missingTransferTarget
    case transactionNotFound(Int64)
    case ledgerNotFound(Int64)
    case cannotDeleteLastActiveLedger
    case cannotMigrateToSameLedger
    case cannotMigrateToArchivedLedger(String)
    case cannotMigrateToSameAccount
    case cannotMigrateToArchivedAccount(String)
    case invalidReimbursementSelection
    case debtNotFound(Int64)
    case debtAlreadySettled
    case installmentNotFound(Int64)
    case installmentCompleted
    case invalidBackupFormat(String)

    public var errorDescription: String? {
        switch self {
        case .unsupportedTransactionType(let t): return "不支持的交易类型：\(t)"
        case .nonPositiveAmount: return "交易金额必须大于 0"
        case .accountNotFound(let id): return "账户不存在（id=\(id)）"
        case .sameTransferAccount: return "转出与转入账户不能相同"
        case .missingTransferTarget: return "转账交易必须指定目标账户"
        case .transactionNotFound(let id): return "交易不存在（id=\(id)）"
        case .ledgerNotFound(let id): return "账本不存在（id=\(id)）"
        case .cannotDeleteLastActiveLedger: return "至少保留一个活跃账本，无法删除最后一个活跃账本"
        case .cannotMigrateToSameLedger: return "目标迁移账本不能与待删除账本相同"
        case .cannotMigrateToArchivedLedger(let name): return "不能迁移到已归档账本：\(name)"
        case .cannotMigrateToSameAccount: return "目标迁移账户不能与待删除账户相同"
        case .cannotMigrateToArchivedAccount(let name): return "不能迁移到已归档账户：\(name)"
        case .invalidReimbursementSelection: return "所选账单均非待报销状态或金额为 0"
        case .debtNotFound(let id): return "借贷记录不存在（id=\(id)）"
        case .debtAlreadySettled: return "该笔借贷已全部结清"
        case .installmentNotFound(let id): return "分期计划不存在（id=\(id)）"
        case .installmentCompleted: return "该分期计划已全部还清"
        case .invalidBackupFormat(let msg): return msg
        }
    }
}

// MARK: - Secure Local Credential Store (Keychain with Automatic Simulator/Unsigned Fallback)

private struct LocalSecretsPayload: Codable {
    var llmApiKey: String = ""
    var webDavPassword: String = ""
    var backupPassphrase: String = ""
}

private final class SecretStorage {
    private let service = "com.yubrae.app.secrets"
    private let account = "credentials.v1"
    private let useKeychain: Bool
    private let fallbackFileURL: URL

    init(directory: URL, useKeychain: Bool) {
        self.useKeychain = useKeychain
        self.fallbackFileURL = directory.appendingPathComponent("secrets.json")
    }

    func load() -> LocalSecretsPayload {
        if useKeychain, let data = readKeychain(),
           let decoded = try? JSONDecoder().decode(LocalSecretsPayload.self, from: data) {
            return decoded
        }
        if let data = try? Data(contentsOf: fallbackFileURL),
           let decoded = try? JSONDecoder().decode(LocalSecretsPayload.self, from: data) {
            return decoded
        }
        return LocalSecretsPayload()
    }

    func save(_ payload: LocalSecretsPayload) {
        guard let data = try? JSONEncoder().encode(payload) else { return }
        if useKeychain && writeKeychain(data) {
            try? FileManager.default.removeItem(at: fallbackFileURL)
            return
        }
        try? data.write(to: fallbackFileURL, options: [.atomic])
    }

    private func readKeychain() -> Data? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne
        ]
        var result: AnyObject?
        let status = SecItemCopyMatching(query as CFDictionary, &result)
        guard status == errSecSuccess else { return nil }
        return result as? Data
    }

    private func writeKeychain(_ data: Data) -> Bool {
        let baseQuery: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account
        ]
        let attributes: [String: Any] = [
            kSecValueData as String: data,
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        ]
        let updateStatus = SecItemUpdate(baseQuery as CFDictionary, attributes as CFDictionary)
        if updateStatus == errSecSuccess {
            return true
        }
        if updateStatus == errSecItemNotFound {
            var addQuery = baseQuery
            for (k, v) in attributes { addQuery[k] = v }
            return SecItemAdd(addQuery as CFDictionary, nil) == errSecSuccess
        }
        return false
    }
}

/// 持久化到本地非敏感设置文件（不含凭据，凭据通过 `SecretStorage` 隔离存储）。
private struct LocalSettingsEnvelope: Codable {
    var settings: AppSettings
}

// MARK: - LedgerStore

/// 余白 iOS 核心业务状态机与本地持久化引擎。
/// - 本地原子持久化至 `Application Support/Yubrae/snapshot.json`
/// - 完整支持多账本、多币种交易与余额联动、余额校准、预算/周期/借贷/分期、多维统计、`.klbk` 加密备份互通与快捷外部入账。
@MainActor
public final class LedgerStore: ObservableObject {

    // MARK: - Published State

    @Published public private(set) var settings: AppSettings
    @Published public private(set) var ledgers: [LedgerDto]
    @Published public private(set) var accounts: [AccountDto]
    @Published public private(set) var categories: [CategoryDto]
    @Published public private(set) var transactions: [TransactionDto]
    @Published public private(set) var exchangeRates: [ExchangeRateDto]
    @Published public private(set) var accountAliases: [AccountAliasDto]
    @Published public private(set) var parseRules: [ParseRuleDto]
    @Published public private(set) var rawCaptureLogs: [RawCaptureLogDto]
    @Published public private(set) var budgets: [BudgetDto]
    @Published public private(set) var recurringRules: [RecurringRuleDto]
    @Published public private(set) var debts: [DebtRecordDto]
    @Published public private(set) var installments: [InstallmentPlanDto]
    @Published public private(set) var tags: [TagDto]

    @Published public var lastErrorMessage: String?
    @Published public var toastMessage: String?
    @Published public var pendingSmartInputLaunch: SmartInputLaunchRequest?

    // MARK: - Dependencies & Storage

    private let storageDirectory: URL
    private let snapshotURL: URL
    private let settingsURL: URL
    private let secretStorage: SecretStorage
    private let timeZone: TimeZone

    public static let sourceIdentity = "IDENTITY"
    public static let sourceCrossCny = "CROSS_CNY"
    public static let sourceBuiltin = "BUILTIN"
    public static let sourceCustom = "CUSTOM"
    public static let sourceNetwork = "NETWORK"
    private static let sourceManualAmount = "MANUAL_AMOUNT"
    private static let sourceManualRate = "MANUAL_RATE"
    private static let maxRecurringCatchupRuns = 24

    // MARK: - Initialization

    public init(
        storageDirectory: URL? = nil,
        useKeychain: Bool = true,
        timeZone: TimeZone = .current
    ) {
        let dir = storageDirectory ?? Self.defaultStorageDirectory()
        self.storageDirectory = dir
        self.snapshotURL = dir.appendingPathComponent("snapshot.json")
        self.settingsURL = dir.appendingPathComponent("settings.json")
        self.secretStorage = SecretStorage(directory: dir, useKeychain: useKeychain)
        self.timeZone = timeZone

        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)

        let loadedSnapshot: KairoBackupSnapshot
        let isFreshInstall: Bool
        if let data = try? Data(contentsOf: snapshotURL),
           let decoded = try? JSONDecoder().decode(KairoBackupSnapshot.self, from: data) {
            loadedSnapshot = decoded
            isFreshInstall = false
        } else {
            loadedSnapshot = DefaultSeedData.makeInitialSnapshot()
            isFreshInstall = true
        }

        var loadedSettings: AppSettings
        if let settingsData = try? Data(contentsOf: settingsURL),
           let env = try? JSONDecoder().decode(LocalSettingsEnvelope.self, from: settingsData) {
            loadedSettings = env.settings
        } else {
            loadedSettings = AppSettings().applying(loadedSnapshot.settings)
        }

        let secrets = secretStorage.load()
        if !secrets.llmApiKey.isEmpty { loadedSettings.llmApiKey = secrets.llmApiKey }
        if !secrets.webDavPassword.isEmpty { loadedSettings.webDavPassword = secrets.webDavPassword }
        if !secrets.backupPassphrase.isEmpty { loadedSettings.backupPassphrase = secrets.backupPassphrase }

        self.settings = loadedSettings
        self.ledgers = loadedSnapshot.ledgers.isEmpty ? DefaultSeedData.makeDefaultLedgers() : loadedSnapshot.ledgers
        self.accounts = loadedSnapshot.accounts
        self.categories = loadedSnapshot.categories
        self.transactions = loadedSnapshot.transactions
        self.exchangeRates = loadedSnapshot.exchangeRates.isEmpty ? DefaultSeedData.makeDefaultExchangeRates() : loadedSnapshot.exchangeRates
        self.accountAliases = loadedSnapshot.accountAliases
        self.parseRules = loadedSnapshot.parseRules
        self.rawCaptureLogs = loadedSnapshot.rawCaptureLogs
        self.budgets = loadedSnapshot.budgets
        self.recurringRules = loadedSnapshot.recurringRules
        self.debts = loadedSnapshot.debts
        self.installments = loadedSnapshot.installments
        self.tags = loadedSnapshot.tags

        ensureActiveLedgerValid()
        if isFreshInstall {
            persist()
        }
    }

    public static func defaultStorageDirectory() -> URL {
        let base = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first
            ?? FileManager.default.temporaryDirectory
        return base.appendingPathComponent("Yubrae", isDirectory: true)
    }

    // MARK: - Derived Read-Only Views

    public var activeLedgers: [LedgerDto] {
        ledgers.filter { !$0.archived }.sorted { lhs, rhs in
            if lhs.sortOrder != rhs.sortOrder { return lhs.sortOrder < rhs.sortOrder }
            return lhs.id < rhs.id
        }
    }

    public var activeLedger: LedgerDto? {
        if let found = ledgers.first(where: { $0.id == settings.activeLedgerId && !$0.archived }) {
            return found
        }
        return activeLedgers.first ?? ledgers.first
    }

    public var activeAccounts: [AccountDto] {
        accounts.filter { !$0.archived }.sorted { lhs, rhs in
            if lhs.sortOrder != rhs.sortOrder { return lhs.sortOrder < rhs.sortOrder }
            return lhs.id < rhs.id
        }
    }

    public var pendingTransactions: [TransactionDto] {
        transactions
            .filter { $0.status == TransactionStatus.PENDING }
            .sorted { $0.timestamp > $1.timestamp }
    }

    public var pendingCount: Int {
        transactions.reduce(0) { $0 + ($1.status == TransactionStatus.PENDING ? 1 : 0) }
    }

    public func transactions(forLedgerId ledgerId: Int64) -> [TransactionDto] {
        let filtered = ledgerId > 0 ? transactions.filter { $0.ledgerId == ledgerId } : transactions
        return filtered.sorted { lhs, rhs in
            if lhs.timestamp != rhs.timestamp { return lhs.timestamp > rhs.timestamp }
            return lhs.id > rhs.id
        }
    }

    public func account(byId id: Int64) -> AccountDto? {
        accounts.first { $0.id == id }
    }

    public func category(byId id: Int64?) -> CategoryDto? {
        guard let id else { return nil }
        return categories.first { $0.id == id }
    }

    public func parentCategories(for type: String) -> [CategoryDto] {
        categories
            .filter { $0.parentId == nil && $0.type.caseInsensitiveCompare(type) == .orderedSame }
            .sorted { lhs, rhs in
                if lhs.sortOrder != rhs.sortOrder { return lhs.sortOrder < rhs.sortOrder }
                return lhs.id < rhs.id
            }
    }

    public func childCategories(of parentId: Int64) -> [CategoryDto] {
        categories
            .filter { $0.parentId == parentId }
            .sorted { lhs, rhs in
                if lhs.sortOrder != rhs.sortOrder { return lhs.sortOrder < rhs.sortOrder }
                return lhs.id < rhs.id
            }
    }

    // MARK: - Settings

    public func updateSettings(_ transform: (inout AppSettings) -> Void) {
        var copy = settings
        transform(&copy)
        settings = copy
        ensureActiveLedgerValid()
        persist()
    }

    // MARK: - Exchange Rates

    public func getRate(from fromCurrency: String, to toCurrency: String, onDate: String? = nil) -> RateQuote {
        let from = CurrencyCatalog.normalize(fromCurrency)
        let to = CurrencyCatalog.normalize(toCurrency)
        let dateFilter = onDate?.trimmingCharacters(in: .whitespacesAndNewlines).nonEmpty
        let fallbackDate = dateFilter ?? DefaultSeedData.formatISODate(epochMillis: currentEpochMillis(), timeZone: timeZone)

        if from == to {
            return RateQuote(
                fromCurrency: from,
                toCurrency: to,
                rateMicros: MoneyAmount.rateScale,
                date: fallbackDate,
                source: Self.sourceIdentity
            )
        }

        // 1. Direct from -> to
        if let entity = findStoredRate(base: from, target: to, onOrBefore: dateFilter), entity.rateMicros > 0 {
            return RateQuote(fromCurrency: from, toCurrency: to, rateMicros: entity.rateMicros, date: entity.date, source: entity.source)
        }

        // 2. Inverse to -> from
        if let entity = findStoredRate(base: to, target: from, onOrBefore: dateFilter), entity.rateMicros > 0 {
            return RateQuote(
                fromCurrency: from,
                toCurrency: to,
                rateMicros: MoneyAmount.invertRate(entity.rateMicros),
                date: entity.date,
                source: entity.source
            )
        }

        // 3. Cross via CNY pivot
        let pivot = CurrencyCatalog.defaultBaseCurrency
        if from != pivot && to != pivot,
           let fromToPivot = findStoredRateToPivot(currency: from, onOrBefore: dateFilter),
           let toToPivot = findStoredRateToPivot(currency: to, onOrBefore: dateFilter) {
            let cross = MoneyAmount.crossRate(fromToViaMicros: fromToPivot.rateMicros, toToViaMicros: toToPivot.rateMicros)
            return RateQuote(
                fromCurrency: from,
                toCurrency: to,
                rateMicros: cross,
                date: max(fromToPivot.date, toToPivot.date),
                source: Self.sourceCrossCny
            )
        }

        // 4. Builtin CurrencyCatalog fallback
        let fromCny = CurrencyCatalog.find(from).fallbackRateToCnyMicros
        let toCny = CurrencyCatalog.find(to).fallbackRateToCnyMicros
        let fallbackRate: Int64
        if to == pivot {
            fallbackRate = fromCny
        } else if from == pivot {
            fallbackRate = MoneyAmount.invertRate(toCny)
        } else {
            fallbackRate = MoneyAmount.crossRate(fromToViaMicros: fromCny, toToViaMicros: toCny)
        }
        return RateQuote(
            fromCurrency: from,
            toCurrency: to,
            rateMicros: fallbackRate,
            date: fallbackDate,
            source: Self.sourceBuiltin
        )
    }

    @discardableResult
    public func applyFetchedRates(_ table: FetchedRateTable) -> Int {
        let base = CurrencyCatalog.normalize(table.baseCurrency)
        let now = currentEpochMillis()
        var syncedPairs = 0

        for (rawTarget, rateMicros) in table.ratesMicros {
            let target = CurrencyCatalog.normalize(rawTarget)
            if target == base || rateMicros <= 0 { continue }
            let source = table.sourcesByCurrency[rawTarget] ?? Self.sourceNetwork
            upsertExchangeRate(base: base, target: target, rateMicros: rateMicros, date: table.date, source: source, now: now)
            upsertExchangeRate(base: target, target: base, rateMicros: MoneyAmount.invertRate(rateMicros), date: table.date, source: source, now: now)
            syncedPairs += 1
        }
        if syncedPairs > 0 {
            settings.lastRateSyncMillis = now
            persist()
        }
        return syncedPairs
    }

    public func saveCustomRate(from fromCurrency: String, to toCurrency: String, rateMicros: Int64) {
        let from = CurrencyCatalog.normalize(fromCurrency)
        let to = CurrencyCatalog.normalize(toCurrency)
        guard from != to, rateMicros > 0 else { return }
        let now = currentEpochMillis()
        let today = DefaultSeedData.formatISODate(epochMillis: now, timeZone: timeZone)
        upsertExchangeRate(base: from, target: to, rateMicros: rateMicros, date: today, source: Self.sourceCustom, now: now)
        upsertExchangeRate(base: to, target: from, rateMicros: MoneyAmount.invertRate(rateMicros), date: today, source: Self.sourceCustom, now: now)
        persist()
    }

    // MARK: - Conversion Preview & Transactions

    public func previewConversion(
        origAmountMicros: Int64,
        origCurrency: String,
        accountId: Int64,
        manualAccountAmountMicros: Int64? = nil,
        manualRateToAccountMicros: Int64? = nil,
        baseCurrency: String? = nil,
        onDate: String? = nil
    ) -> ConversionPreview {
        let resolvedBase = CurrencyCatalog.normalize(baseCurrency ?? settings.baseCurrency)
        let account = account(byId: accountId)
        let accountCurrency = CurrencyCatalog.normalize(account?.currencyCode ?? resolvedBase)
        let normOrig = CurrencyCatalog.normalize(origCurrency.isEmpty ? accountCurrency : origCurrency)
        return resolveConversion(
            origAmountMicros: origAmountMicros,
            origCurrency: normOrig,
            accountCurrency: accountCurrency,
            baseCurrency: resolvedBase,
            manualAccountAmountMicros: manualAccountAmountMicros,
            manualRateToAccountMicros: manualRateToAccountMicros,
            onDate: onDate
        )
    }

    @discardableResult
    public func saveTransaction(_ request: RecordTransactionRequest) throws -> TransactionDto {
        let saved = try saveTransactionInternal(request)
        persist()
        return saved
    }

    public func deleteTransaction(id: Int64) throws {
        guard let idx = transactions.firstIndex(where: { $0.id == id }) else {
            throw LedgerStoreError.transactionNotFound(id)
        }
        let existing = transactions[idx]
        let now = currentEpochMillis()
        applyBalanceDelta(existing, direction: -1, now: now)
        transactions.remove(at: idx)
        persist()
    }

    @discardableResult
    public func batchDeleteTransactions(ids: [Int64]) -> Int {
        guard !ids.isEmpty else { return 0 }
        let now = currentEpochMillis()
        let idSet = Set(ids)
        var deleted = 0
        for tx in transactions where idSet.contains(tx.id) {
            applyBalanceDelta(tx, direction: -1, now: now)
            deleted += 1
        }
        transactions.removeAll { idSet.contains($0.id) }
        if deleted > 0 { persist() }
        return deleted
    }

    @discardableResult
    public func batchUpdateCategory(txIds: [Int64], categoryId: Int64?) -> Int {
        guard !txIds.isEmpty else { return 0 }
        let now = currentEpochMillis()
        let idSet = Set(txIds)
        var updated = 0
        for i in transactions.indices where idSet.contains(transactions[i].id) {
            transactions[i].categoryId = categoryId
            transactions[i].updatedAt = now
            updated += 1
        }
        if updated > 0 { persist() }
        return updated
    }

    public func confirmTransaction(id: Int64) throws {
        guard let idx = transactions.firstIndex(where: { $0.id == id }) else {
            throw LedgerStoreError.transactionNotFound(id)
        }
        transactions[idx].status = TransactionStatus.CONFIRMED
        transactions[idx].updatedAt = currentEpochMillis()
        persist()
    }

    @discardableResult
    public func confirmAllPending() -> Int {
        let now = currentEpochMillis()
        var count = 0
        for i in transactions.indices where transactions[i].status == TransactionStatus.PENDING {
            transactions[i].status = TransactionStatus.CONFIRMED
            transactions[i].updatedAt = now
            count += 1
        }
        if count > 0 { persist() }
        return count
    }

    @discardableResult
    public func settleReimbursements(
        txIds: [Int64],
        targetAccountId: Int64,
        timestamp: Int64 = currentEpochMillis(),
        note: String = ""
    ) throws -> TransactionDto {
        guard !txIds.isEmpty else { throw LedgerStoreError.invalidReimbursementSelection }
        guard let targetAcc = account(byId: targetAccountId) else {
            throw LedgerStoreError.accountNotFound(targetAccountId)
        }
        let targetCur = CurrencyCatalog.normalize(targetAcc.currencyCode)
        let dateStr = DefaultSeedData.formatISODate(epochMillis: timestamp, timeZone: timeZone)

        var totalMicros: Int64 = 0
        var pendingIds: [Int64] = []
        var firstLedgerId: Int64 = 0
        for id in txIds {
            guard let tx = transactions.first(where: { $0.id == id }),
                  tx.reimburseStatus == ReimburseStatus.PENDING else { continue }
            if pendingIds.isEmpty { firstLedgerId = tx.ledgerId }
            pendingIds.append(id)
            let origCur = CurrencyCatalog.normalize(tx.origCurrency)
            if origCur == targetCur {
                totalMicros += tx.origAmountMicros
            } else {
                let rate = getRate(from: origCur, to: targetCur, onDate: dateStr).rateMicros
                totalMicros += MoneyAmount.convert(amountMicros: tx.origAmountMicros, rateMicros: rate)
            }
        }
        guard !pendingIds.isEmpty, totalMicros > 0 else {
            throw LedgerStoreError.invalidReimbursementSelection
        }

        let incomeCat = categories.first { $0.name == "报销退款" && $0.type == TransactionType.INCOME }
            ?? categories.first { $0.type == TransactionType.INCOME }

        let savedIncome = try saveTransactionInternal(
            RecordTransactionRequest(
                type: TransactionType.INCOME,
                timestamp: timestamp,
                origAmountMicros: totalMicros,
                origCurrency: targetCur,
                accountId: targetAcc.id,
                categoryId: incomeCat?.id,
                merchant: "报销回款",
                note: note.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? "报销核销 (\(pendingIds.count) 笔)" : note,
                reimburseStatus: ReimburseStatus.NONE,
                sourceType: CaptureChannel.MANUAL,
                status: TransactionStatus.CONFIRMED,
                ledgerId: firstLedgerId,
                excludeFromStats: true,
                excludeFromBudget: true
            )
        )

        let now = currentEpochMillis()
        let pendingSet = Set(pendingIds)
        for i in transactions.indices where pendingSet.contains(transactions[i].id) {
            transactions[i].reimburseStatus = ReimburseStatus.REIMBURSED
            transactions[i].reimbursedTxId = savedIncome.id
            transactions[i].updatedAt = now
        }
        persist()
        return savedIncome
    }

    // MARK: - Accounts & Balance Calibration

    @discardableResult
    public func saveAccount(_ account: AccountDto) -> AccountDto {
        let now = currentEpochMillis()
        var dto = account
        dto.currencyCode = CurrencyCatalog.normalize(dto.currencyCode)
        dto.updatedAt = now
        if dto.id > 0, let idx = accounts.firstIndex(where: { $0.id == dto.id }) {
            if dto.createdAt <= 0 { dto.createdAt = accounts[idx].createdAt }
            accounts[idx] = dto
        } else {
            dto.id = nextId(in: accounts.map(\.id))
            if dto.createdAt <= 0 { dto.createdAt = now }
            accounts.append(dto)
        }
        persist()
        return dto
    }

    public func archiveAccount(id: Int64, archived: Bool) {
        guard let idx = accounts.firstIndex(where: { $0.id == id }) else { return }
        accounts[idx].archived = archived
        accounts[idx].updatedAt = currentEpochMillis()
        persist()
    }

    public func deleteAccount(id: Int64) {
        try? deleteAccount(id: id, migrateToAccountId: nil)
    }

    public func deleteAccount(id: Int64, migrateToAccountId: Int64?) throws {
        guard let sourceAccount = accounts.first(where: { $0.id == id }) else {
            throw LedgerStoreError.accountNotFound(id)
        }
        if let targetId = migrateToAccountId, targetId == id {
            throw LedgerStoreError.cannotMigrateToSameAccount
        }

        let now = currentEpochMillis()
        if let targetId = migrateToAccountId {
            guard let targetAccount = accounts.first(where: { $0.id == targetId }) else {
                throw LedgerStoreError.accountNotFound(targetId)
            }
            if targetAccount.archived {
                throw LedgerStoreError.cannotMigrateToArchivedAccount(targetAccount.name)
            }

            let sourceCur = CurrencyCatalog.normalize(sourceAccount.currencyCode)
            let targetCur = CurrencyCatalog.normalize(targetAccount.currencyCode)
            let rateToTarget: Int64 = sourceCur == targetCur
                ? MoneyAmount.rateScale
                : getRate(from: sourceCur, to: targetCur).rateMicros

            if sourceAccount.balanceMicros != 0 {
                let delta = sourceCur == targetCur
                    ? sourceAccount.balanceMicros
                    : MoneyAmount.convert(amountMicros: sourceAccount.balanceMicros, rateMicros: rateToTarget)
                adjustAccountBalance(id: targetAccount.id, delta: delta, now: now)
            }

            for i in transactions.indices {
                var changed = false
                if transactions[i].accountId == id {
                    transactions[i].accountId = targetId
                    if sourceCur != targetCur {
                        let converted = MoneyAmount.convert(
                            amountMicros: transactions[i].accountAmountMicros,
                            rateMicros: rateToTarget
                        )
                        transactions[i].accountAmountMicros = converted
                        transactions[i].rateToAccountMicros = MoneyAmount.computeRate(
                            origAmountMicros: transactions[i].origAmountMicros,
                            targetAmountMicros: converted
                        )
                    }
                    changed = true
                }
                if transactions[i].toAccountId == id {
                    transactions[i].toAccountId = targetId
                    if transactions[i].accountId == targetId && transactions[i].type == TransactionType.TRANSFER {
                        transactions[i].toAmountMicros = transactions[i].accountAmountMicros
                    } else if sourceCur != targetCur, let toAmt = transactions[i].toAmountMicros {
                        transactions[i].toAmountMicros = MoneyAmount.convert(amountMicros: toAmt, rateMicros: rateToTarget)
                    }
                    changed = true
                }
                if changed {
                    transactions[i].updatedAt = now
                }
            }

            for i in recurringRules.indices {
                if recurringRules[i].accountId == id { recurringRules[i].accountId = targetId }
                if recurringRules[i].toAccountId == id { recurringRules[i].toAccountId = targetId }
            }
            for i in debts.indices where debts[i].accountId == id {
                debts[i].accountId = targetId
                debts[i].updatedAt = now
            }
            for i in installments.indices where installments[i].accountId == id {
                installments[i].accountId = targetId
            }
        } else {
            let linkedTxs = transactions.filter { $0.accountId == id || $0.toAccountId == id }
            let linkedIds = Set(linkedTxs.map(\.id))

            for tx in linkedTxs where tx.type == TransactionType.TRANSFER {
                if tx.accountId == id,
                   let toId = tx.toAccountId,
                   toId != id,
                   let toAmount = tx.toAmountMicros {
                    adjustAccountBalance(id: toId, delta: -toAmount, now: now)
                } else if tx.toAccountId == id,
                          tx.accountId != id {
                    adjustAccountBalance(id: tx.accountId, delta: tx.accountAmountMicros, now: now)
                }
            }

            if !linkedIds.isEmpty {
                transactions.removeAll { linkedIds.contains($0.id) }
                for i in transactions.indices {
                    if let ref = transactions[i].refundOfTxId, linkedIds.contains(ref) {
                        transactions[i].refundOfTxId = nil
                        transactions[i].updatedAt = now
                    }
                    if let reimb = transactions[i].reimbursedTxId, linkedIds.contains(reimb) {
                        transactions[i].reimbursedTxId = nil
                        transactions[i].reimburseStatus = ReimburseStatus.PENDING
                        transactions[i].updatedAt = now
                    }
                }
            }

            recurringRules.removeAll { $0.accountId == id || $0.toAccountId == id }
            installments.removeAll { $0.accountId == id }
            for i in debts.indices where debts[i].accountId == id {
                debts[i].accountId = nil
                debts[i].updatedAt = now
            }
        }

        accounts.removeAll { $0.id == id }
        accountAliases.removeAll { $0.accountId == id }
        settings.hiddenAssetAccountIds.removeAll { $0 == id }
        persist()
    }

    @discardableResult
    public func calibrateAccountBalance(
        accountId: Int64,
        targetBalanceMicros: Int64,
        note: String = "余额校准"
    ) throws -> TransactionDto {
        guard let acc = account(byId: accountId) else {
            throw LedgerStoreError.accountNotFound(accountId)
        }
        let delta = targetBalanceMicros - acc.balanceMicros
        let saved = try saveTransactionInternal(
            RecordTransactionRequest(
                type: TransactionType.ADJUST,
                timestamp: currentEpochMillis(),
                origAmountMicros: delta,
                origCurrency: acc.currencyCode,
                accountId: acc.id,
                merchant: "余额校准",
                note: note.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? "余额校准" : note,
                sourceType: CaptureChannel.MANUAL,
                status: TransactionStatus.CONFIRMED
            )
        )
        persist()
        return saved
    }

    // MARK: - Ledgers

    @discardableResult
    public func saveLedger(_ ledger: LedgerDto) -> LedgerDto {
        var dto = ledger
        dto.currencyCode = CurrencyCatalog.normalize(dto.currencyCode)
        if dto.id > 0, let idx = ledgers.firstIndex(where: { $0.id == dto.id }) {
            ledgers[idx] = dto
        } else {
            dto.id = nextId(in: ledgers.map(\.id))
            if dto.createdAt <= 0 { dto.createdAt = currentEpochMillis() }
            ledgers.append(dto)
        }
        ensureActiveLedgerValid()
        persist()
        return dto
    }

    public func setActiveLedger(id: Int64) {
        guard ledgers.contains(where: { $0.id == id && !$0.archived }) else { return }
        settings.activeLedgerId = id
        persist()
    }

    public func archiveLedger(id: Int64, archived: Bool) {
        guard let idx = ledgers.firstIndex(where: { $0.id == id }) else { return }
        if ledgers[idx].archived == archived { return }
        if archived {
            let remaining = activeLedgers.filter { $0.id != id }
            guard let fallback = remaining.first else { return }
            ledgers[idx].archived = true
            if settings.activeLedgerId == id {
                settings.activeLedgerId = fallback.id
            }
        } else {
            ledgers[idx].archived = false
        }
        persist()
    }

    public func deleteLedger(id: Int64, migrateToLedgerId: Int64? = nil) throws {
        if let targetId = migrateToLedgerId, targetId == id {
            throw LedgerStoreError.cannotMigrateToSameLedger
        }
        guard ledgers.contains(where: { $0.id == id }) else {
            throw LedgerStoreError.ledgerNotFound(id)
        }
        let remainingActive = activeLedgers.filter { $0.id != id }
        guard let fallbackLedger = remainingActive.first else {
            throw LedgerStoreError.cannotDeleteLastActiveLedger
        }

        let now = currentEpochMillis()
        let nextActiveId: Int64

        if let targetId = migrateToLedgerId {
            guard let targetLedger = ledgers.first(where: { $0.id == targetId }) else {
                throw LedgerStoreError.ledgerNotFound(targetId)
            }
            if targetLedger.archived {
                throw LedgerStoreError.cannotMigrateToArchivedLedger(targetLedger.name)
            }
            for i in transactions.indices where transactions[i].ledgerId == id {
                transactions[i].ledgerId = targetId
                transactions[i].updatedAt = now
            }

            var existingKeys = Set(
                budgets
                    .filter { $0.ledgerId == targetId }
                    .map { "\($0.categoryId ?? -1)::\($0.period.trimmingCharacters(in: .whitespacesAndNewlines).uppercased())" }
            )
            var keptBudgets: [BudgetDto] = []
            for var budget in budgets {
                if budget.ledgerId != id {
                    keptBudgets.append(budget)
                } else {
                    let key = "\(budget.categoryId ?? -1)::\(budget.period.trimmingCharacters(in: .whitespacesAndNewlines).uppercased())"
                    if existingKeys.insert(key).inserted {
                        budget.ledgerId = targetId
                        keptBudgets.append(budget)
                    }
                }
            }
            budgets = keptBudgets
            ledgers.removeAll { $0.id == id }
            nextActiveId = targetId
        } else {
            let deletedTxIds = Set(transactions.filter { $0.ledgerId == id }.map(\.id))
            for tx in transactions where tx.ledgerId == id {
                applyBalanceDelta(tx, direction: -1, now: now)
            }
            transactions.removeAll { $0.ledgerId == id }
            if !deletedTxIds.isEmpty {
                for i in transactions.indices {
                    if let ref = transactions[i].refundOfTxId, deletedTxIds.contains(ref) {
                        transactions[i].refundOfTxId = nil
                        transactions[i].updatedAt = now
                    }
                    if let reimb = transactions[i].reimbursedTxId, deletedTxIds.contains(reimb) {
                        transactions[i].reimbursedTxId = nil
                        transactions[i].reimburseStatus = ReimburseStatus.PENDING
                        transactions[i].updatedAt = now
                    }
                }
            }
            budgets.removeAll { $0.ledgerId == id }
            ledgers.removeAll { $0.id == id }
            nextActiveId = fallbackLedger.id
        }

        if settings.activeLedgerId == id {
            settings.activeLedgerId = nextActiveId
        }
        ensureActiveLedgerValid()
        persist()
    }

    // MARK: - Categories, Tags, Budgets, Recurring, Debts, Installments

    @discardableResult
    public func saveCategory(_ category: CategoryDto) -> CategoryDto {
        var dto = category
        if dto.id > 0, let idx = categories.firstIndex(where: { $0.id == dto.id }) {
            categories[idx] = dto
        } else {
            dto.id = nextId(in: categories.map(\.id))
            categories.append(dto)
        }
        persist()
        return dto
    }

    public func deleteCategory(id: Int64) {
        categories.removeAll { $0.id == id || $0.parentId == id }
        persist()
    }

    @discardableResult
    public func saveTag(_ tag: TagDto) -> TagDto? {
        let normalizedName = tag.name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !normalizedName.isEmpty else { return nil }
        var dto = tag
        dto.name = normalizedName
        dto.groupName = tag.groupName.trimmingCharacters(in: .whitespacesAndNewlines).nonEmpty ?? "常用"
        dto.icon = tag.icon.trimmingCharacters(in: .whitespacesAndNewlines)

        if dto.id > 0, let idx = tags.firstIndex(where: { $0.id == dto.id }) {
            if let conflictIdx = tags.firstIndex(where: { $0.name == dto.name && $0.id != dto.id }) {
                dto.id = tags[conflictIdx].id
                tags[conflictIdx] = dto
            } else {
                tags[idx] = dto
            }
        } else if let existingIdx = tags.firstIndex(where: { $0.name == dto.name }) {
            dto.id = tags[existingIdx].id
            tags[existingIdx] = dto
        } else {
            dto.id = nextId(in: tags.map(\.id))
            tags.append(dto)
        }
        persist()
        return dto
    }

    public func deleteTag(id: Int64) {
        tags.removeAll { $0.id == id }
        persist()
    }

    @discardableResult
    public func ensurePresetCatalogSeeded() -> (addedCategories: Int, addedTags: Int) {
        let addedCats = DefaultSeedData.mergeMissingPresetCategories(into: &categories)
        let addedTags = DefaultSeedData.mergeMissingPresetTags(into: &tags)
        if addedCats > 0 || addedTags > 0 {
            persist()
        }
        return (addedCats, addedTags)
    }

    @discardableResult
    public func saveBudget(_ budget: BudgetDto) -> BudgetDto {
        var dto = budget
        dto.currencyCode = CurrencyCatalog.normalize(dto.currencyCode)
        dto.ledgerId = resolveTargetLedgerId(requested: dto.ledgerId, existing: nil)
        if dto.id > 0, let idx = budgets.firstIndex(where: { $0.id == dto.id }) {
            budgets[idx] = dto
        } else {
            dto.id = nextId(in: budgets.map(\.id))
            budgets.append(dto)
        }
        persist()
        return dto
    }

    public func deleteBudget(id: Int64) {
        budgets.removeAll { $0.id == id }
        persist()
    }

    @discardableResult
    public func saveRecurringRule(_ rule: RecurringRuleDto) -> RecurringRuleDto {
        var dto = rule
        dto.currencyCode = CurrencyCatalog.normalize(dto.currencyCode)
        if dto.id > 0, let idx = recurringRules.firstIndex(where: { $0.id == dto.id }) {
            recurringRules[idx] = dto
        } else {
            dto.id = nextId(in: recurringRules.map(\.id))
            recurringRules.append(dto)
        }
        persist()
        return dto
    }

    public func deleteRecurringRule(id: Int64) {
        recurringRules.removeAll { $0.id == id }
        persist()
    }

    @discardableResult
    public func processDueRecurringRules(nowMillis: Int64 = currentEpochMillis()) -> Int {
        var executedTotal = 0
        for i in recurringRules.indices {
            guard recurringRules[i].enabled,
                  recurringRules[i].nextRunEpochMillis > 0,
                  recurringRules[i].nextRunEpochMillis <= nowMillis else { continue }

            var rule = recurringRules[i]
            var currentRun = rule.nextRunEpochMillis
            var count = 0
            while currentRun <= nowMillis && count < Self.maxRecurringCatchupRuns {
                let req = RecordTransactionRequest(
                    type: rule.type,
                    timestamp: currentRun,
                    origAmountMicros: rule.amountMicros,
                    origCurrency: rule.currencyCode,
                    accountId: rule.accountId,
                    toAccountId: rule.toAccountId,
                    categoryId: rule.categoryId,
                    merchant: rule.merchant.trimmingCharacters(in: .whitespacesAndNewlines).nonEmpty ?? rule.name,
                    note: rule.note.trimmingCharacters(in: .whitespacesAndNewlines).nonEmpty ?? "周期自动记账：\(rule.name)",
                    sourceType: CaptureChannel.RECURRING,
                    status: TransactionStatus.CONFIRMED
                )
                guard (try? saveTransactionInternal(req)) != nil else { break }
                count += 1
                let nextRun = computeNextRecurringMillis(rule: rule, currentRunMillis: currentRun)
                if nextRun <= currentRun { break }
                currentRun = nextRun
            }
            if count > 0 {
                rule.nextRunEpochMillis = currentRun
                recurringRules[i] = rule
                executedTotal += count
            }
        }
        if executedTotal > 0 { persist() }
        return executedTotal
    }

    @discardableResult
    public func saveDebt(_ debt: DebtRecordDto, syncAccountBalance: Bool = false) -> DebtRecordDto {
        let now = currentEpochMillis()
        var dto = debt
        dto.currencyCode = CurrencyCatalog.normalize(dto.currencyCode)
        dto.updatedAt = now
        if dto.id > 0, let idx = debts.firstIndex(where: { $0.id == dto.id }) {
            debts[idx] = dto
        } else {
            dto.id = nextId(in: debts.map(\.id))
            if dto.createdAt <= 0 { dto.createdAt = now }
            debts.append(dto)
            if syncAccountBalance, let accId = dto.accountId, dto.amountMicros > 0, account(byId: accId) != nil {
                let isBorrow = dto.direction == DebtDirection.BORROW
                _ = try? saveTransactionInternal(
                    RecordTransactionRequest(
                        type: isBorrow ? TransactionType.INCOME : TransactionType.EXPENSE,
                        timestamp: now,
                        origAmountMicros: dto.amountMicros,
                        origCurrency: dto.currencyCode,
                        accountId: accId,
                        merchant: dto.personName,
                        note: dto.note.trimmingCharacters(in: .whitespacesAndNewlines).nonEmpty
                            ?? (isBorrow ? "借入资金：\(dto.personName)" : "借出资金：\(dto.personName)"),
                        excludeFromStats: true,
                        excludeFromBudget: true
                    )
                )
            }
        }
        persist()
        return dto
    }

    @discardableResult
    public func settleDebt(
        debtId: Int64,
        settleAmountMicros: Int64,
        accountId: Int64? = nil,
        note: String = ""
    ) throws -> DebtRecordDto {
        guard settleAmountMicros > 0 else { throw LedgerStoreError.nonPositiveAmount }
        guard let idx = debts.firstIndex(where: { $0.id == debtId }) else {
            throw LedgerStoreError.debtNotFound(debtId)
        }
        var debt = debts[idx]
        let remaining = max(0, debt.amountMicros - debt.settledAmountMicros)
        let applied = min(settleAmountMicros, remaining)
        guard applied > 0 else { throw LedgerStoreError.debtAlreadySettled }

        let now = currentEpochMillis()
        debt.settledAmountMicros += applied
        debt.status = debt.settledAmountMicros >= debt.amountMicros ? DebtStatus.SETTLED : DebtStatus.OPEN
        debt.updatedAt = now
        debts[idx] = debt

        if let targetAccId = accountId ?? debt.accountId, account(byId: targetAccId) != nil {
            let isLend = debt.direction == DebtDirection.LEND
            _ = try? saveTransactionInternal(
                RecordTransactionRequest(
                    type: isLend ? TransactionType.INCOME : TransactionType.EXPENSE,
                    timestamp: now,
                    origAmountMicros: applied,
                    origCurrency: debt.currencyCode,
                    accountId: targetAccId,
                    merchant: debt.personName,
                    note: note.trimmingCharacters(in: .whitespacesAndNewlines).nonEmpty
                        ?? (isLend ? "收回借款：\(debt.personName)" : "归还借款：\(debt.personName)"),
                    excludeFromStats: true,
                    excludeFromBudget: true
                )
            )
        }
        persist()
        return debt
    }

    public func deleteDebt(id: Int64) {
        debts.removeAll { $0.id == id }
        persist()
    }

    @discardableResult
    public func saveInstallment(_ plan: InstallmentPlanDto) -> InstallmentPlanDto {
        var dto = plan
        dto.currencyCode = CurrencyCatalog.normalize(dto.currencyCode)
        if dto.id > 0, let idx = installments.firstIndex(where: { $0.id == dto.id }) {
            installments[idx] = dto
        } else {
            dto.id = nextId(in: installments.map(\.id))
            if dto.createdAt <= 0 { dto.createdAt = currentEpochMillis() }
            installments.append(dto)
        }
        persist()
        return dto
    }

    @discardableResult
    public func recordInstallmentPeriod(planId: Int64) throws -> TransactionDto {
        guard let idx = installments.firstIndex(where: { $0.id == planId }) else {
            throw LedgerStoreError.installmentNotFound(planId)
        }
        var plan = installments[idx]
        guard plan.completedPeriods < plan.totalPeriods else {
            throw LedgerStoreError.installmentCompleted
        }
        let nextIndex = plan.completedPeriods + 1
        let periods = Int64(max(1, plan.totalPeriods))
        let basePerPeriod = plan.totalAmountMicros / periods
        let remainder = nextIndex == plan.totalPeriods ? (plan.totalAmountMicros - basePerPeriod * periods) : 0
        let periodTotal = max(1, basePerPeriod + remainder + plan.feePerPeriodMicros)

        let savedTx = try saveTransactionInternal(
            RecordTransactionRequest(
                type: TransactionType.EXPENSE,
                timestamp: currentEpochMillis(),
                origAmountMicros: periodTotal,
                origCurrency: plan.currencyCode,
                accountId: plan.accountId,
                categoryId: plan.categoryId,
                merchant: plan.name,
                note: "信用卡分期第 \(nextIndex)/\(plan.totalPeriods) 期",
                sourceType: CaptureChannel.RECURRING,
                status: TransactionStatus.CONFIRMED
            )
        )

        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = timeZone
        let currentDue = Date(timeIntervalSince1970: Double(plan.nextDueMillis) / 1000.0)
        if let nextMonth = cal.date(byAdding: .month, value: 1, to: currentDue),
           let dayRange = cal.range(of: .day, in: .month, for: nextMonth) {
            var comps = cal.dateComponents([.year, .month], from: nextMonth)
            comps.day = min(max(1, plan.billingDay), dayRange.upperBound - 1)
            comps.hour = 9
            comps.minute = 0
            if let nextDueDate = cal.date(from: comps) {
                plan.nextDueMillis = Int64((nextDueDate.timeIntervalSince1970 * 1000.0).rounded())
            }
        }
        plan.completedPeriods = nextIndex
        plan.enabled = nextIndex < plan.totalPeriods
        installments[idx] = plan
        persist()
        return savedTx
    }

    public func deleteInstallment(id: Int64) {
        installments.removeAll { $0.id == id }
        persist()
    }

    // MARK: - Analytics (100% Parity with Android LedgerAnalytics.kt)

    public func computeNetWorth(displayCurrency: String? = nil) -> NetWorthSummary {
        let targetCurrency = CurrencyCatalog.normalize(displayCurrency ?? settings.baseCurrency)
        var rateCache: [String: Int64] = [:]
        var convertedBalances: [Int64: Int64] = [:]
        var totalAssets: Int64 = 0
        var totalLiabilities: Int64 = 0

        for account in activeAccounts {
            let accCur = CurrencyCatalog.normalize(account.currencyCode)
            let converted: Int64
            if accCur == targetCurrency {
                converted = account.balanceMicros
            } else {
                let rate: Int64
                if let cached = rateCache[accCur] {
                    rate = cached
                } else {
                    let fetched = getRate(from: accCur, to: targetCurrency).rateMicros
                    rateCache[accCur] = fetched
                    rate = fetched
                }
                converted = MoneyAmount.convert(amountMicros: account.balanceMicros, rateMicros: rate)
            }
            convertedBalances[account.id] = converted
            if account.includeInNetWorth {
                if converted >= 0 {
                    totalAssets += converted
                } else {
                    totalLiabilities += -converted
                }
            }
        }

        return NetWorthSummary(
            displayCurrency: targetCurrency,
            totalAssetsMicros: totalAssets,
            totalLiabilitiesMicros: totalLiabilities,
            netWorthMicros: totalAssets - totalLiabilities,
            accountConvertedBalances: convertedBalances
        )
    }

    public func computeMonthlyTrend(
        monthsBack: Int = 6,
        displayCurrency: String? = nil,
        ledgerId: Int64 = 0,
        nowMillis: Int64 = currentEpochMillis()
    ) -> [MonthlyPoint] {
        let targetCurrency = CurrencyCatalog.normalize(displayCurrency ?? settings.baseCurrency)
        let months = buildMonthBuckets(monthsBack: monthsBack, nowMillis: nowMillis)
        let monthSet = Set(months)
        var expenseByMonth: [YearMonthKey: Int64] = [:]
        var incomeByMonth: [YearMonthKey: Int64] = [:]
        var rateCache: [String: Int64] = [:]
        let txById = Dictionary(transactions.map { ($0.id, $0) }, uniquingKeysWith: { _, latest in latest })

        for tx in transactions {
            guard tx.status == TransactionStatus.CONFIRMED, !tx.excludeFromStats else { continue }
            if ledgerId > 0 && tx.ledgerId != ledgerId { continue }

            if tx.type == TransactionType.INCOME, let refundId = tx.refundOfTxId {
                let origTime = txById[refundId]?.timestamp ?? tx.timestamp
                let ym = yearMonth(of: origTime)
                if monthSet.contains(ym) {
                    let refundAmt = convertFrozenToDisplayMicros(tx: tx, displayCurrency: targetCurrency, rateCache: &rateCache)
                    expenseByMonth[ym, default: 0] -= refundAmt
                }
                continue
            }

            guard tx.type == TransactionType.EXPENSE || tx.type == TransactionType.INCOME else { continue }
            let ym = yearMonth(of: tx.timestamp)
            guard monthSet.contains(ym) else { continue }
            let amount = convertFrozenToDisplayMicros(tx: tx, displayCurrency: targetCurrency, rateCache: &rateCache)
            if tx.type == TransactionType.EXPENSE {
                expenseByMonth[ym, default: 0] += amount
            } else {
                incomeByMonth[ym, default: 0] += amount
            }
        }

        return months.map { ym in
            MonthlyPoint(
                monthLabel: "\(ym.month)月",
                year: ym.year,
                month: ym.month,
                expenseBaseMicros: max(0, expenseByMonth[ym] ?? 0),
                incomeBaseMicros: incomeByMonth[ym] ?? 0
            )
        }
    }

    public func computeCategoryStats(
        startMillis: Int64,
        endMillis: Int64,
        type: String,
        ledgerId: Int64 = 0,
        displayCurrency: String? = nil
    ) -> [CategoryStat] {
        let targetCurrency = CurrencyCatalog.normalize(displayCurrency ?? settings.baseCurrency)
        let categoriesById = Dictionary(categories.map { ($0.id, $0) }, uniquingKeysWith: { _, latest in latest })
        let txById = Dictionary(transactions.map { ($0.id, $0) }, uniquingKeysWith: { _, latest in latest })
        var rateCache: [String: Int64] = [:]
        var totalsByCat: [Int64: Int64] = [:]
        var countsByCat: [Int64: Int] = [:]
        let uncategorizedKey: Int64 = -1

        for tx in transactions {
            guard tx.status == TransactionStatus.CONFIRMED, !tx.excludeFromStats else { continue }
            if ledgerId > 0 && tx.ledgerId != ledgerId { continue }

            if tx.type == TransactionType.INCOME, let refundId = tx.refundOfTxId {
                if type == TransactionType.EXPENSE {
                    let orig = txById[refundId]
                    let targetCat = orig?.categoryId ?? tx.categoryId ?? uncategorizedKey
                    let targetTime = orig?.timestamp ?? tx.timestamp
                    if targetTime >= startMillis && targetTime <= endMillis {
                        let refundAmt = convertFrozenToDisplayMicros(tx: tx, displayCurrency: targetCurrency, rateCache: &rateCache)
                        totalsByCat[targetCat, default: 0] -= refundAmt
                    }
                }
                continue
            }

            guard tx.type == type, tx.timestamp >= startMillis, tx.timestamp <= endMillis else { continue }
            let key = tx.categoryId ?? uncategorizedKey
            let amount = convertFrozenToDisplayMicros(tx: tx, displayCurrency: targetCurrency, rateCache: &rateCache)
            totalsByCat[key, default: 0] += amount
            countsByCat[key, default: 0] += 1
        }

        let entries: [(catId: Int64?, total: Int64, count: Int)] = totalsByCat.compactMap { key, rawTotal in
            let clamped = max(0, rawTotal)
            guard clamped > 0 else { return nil }
            let catId: Int64? = key == uncategorizedKey ? nil : key
            return (catId, clamped, countsByCat[key] ?? 0)
        }
        guard !entries.isEmpty else { return [] }
        let grandTotal = entries.reduce(Int64(0)) { $0 + $1.total }

        return entries.sorted { $0.total > $1.total }.map { item in
            let cat = item.catId.flatMap { categoriesById[$0] }
            let ratio = grandTotal > 0 ? Double(item.total) / Double(grandTotal) : 0
            return CategoryStat(
                categoryId: item.catId,
                categoryName: cat?.name ?? "未分类",
                colorHex: cat?.colorHex ?? 0xFF64748B,
                icon: cat?.icon ?? "category",
                totalBaseMicros: item.total,
                count: item.count,
                percentage: ratio
            )
        }
    }

    public func computeTagStats(
        startMillis: Int64,
        endMillis: Int64,
        type: String,
        ledgerId: Int64 = 0,
        displayCurrency: String? = nil
    ) -> [TagStat] {
        let targetCurrency = CurrencyCatalog.normalize(displayCurrency ?? settings.baseCurrency)
        let tagColorByName = Dictionary(tags.map { ($0.name, $0.colorHex) }, uniquingKeysWith: { _, latest in latest })
        var rateCache: [String: Int64] = [:]
        var totalsByTag: [String: Int64] = [:]
        var countsByTag: [String: Int] = [:]

        for tx in transactions {
            guard tx.status == TransactionStatus.CONFIRMED,
                  !tx.excludeFromStats,
                  tx.type == type,
                  tx.timestamp >= startMillis,
                  tx.timestamp <= endMillis else { continue }
            if ledgerId > 0 && tx.ledgerId != ledgerId { continue }
            let txTags = TagTextCodec.parse(tx.tags)
            guard !txTags.isEmpty else { continue }
            let amount = convertFrozenToDisplayMicros(tx: tx, displayCurrency: targetCurrency, rateCache: &rateCache)
            for tag in txTags {
                totalsByTag[tag, default: 0] += amount
                countsByTag[tag, default: 0] += 1
            }
        }

        let grandTotal = totalsByTag.values.reduce(Int64(0), +)
        guard grandTotal > 0 else { return [] }
        return totalsByTag.sorted { $0.value > $1.value }.map { name, total in
            TagStat(
                tagName: name,
                colorHex: tagColorByName[name] ?? 0xFF2AA876,
                totalBaseMicros: total,
                count: countsByTag[name] ?? 0,
                percentage: Double(total) / Double(grandTotal)
            )
        }
    }

    public func computeBudgetProgress(
        year: Int,
        month: Int,
        ledgerId: Int64 = 0,
        nowMillis: Int64 = currentEpochMillis()
    ) -> [BudgetProgress] {
        let enabledBudgets = budgets.filter { $0.enabled && (ledgerId <= 0 || $0.ledgerId == ledgerId) }
        guard !enabledBudgets.isEmpty else { return [] }

        let categoriesById = Dictionary(categories.map { ($0.id, $0) }, uniquingKeysWith: { _, latest in latest })
        var childrenByParent: [Int64: Set<Int64>] = [:]
        for cat in categories {
            if let pid = cat.parentId {
                childrenByParent[pid, default: []].insert(cat.id)
            }
        }

        let safeMonth = min(12, max(1, month))
        let monthRange = monthRangeMillis(year: year, month: safeMonth)
        let prevYM = previousYearMonth(year: year, month: safeMonth)
        let prevMonthRange = monthRangeMillis(year: prevYM.year, month: prevYM.month)
        let yearRange = yearRangeMillis(year: year)
        let txById = Dictionary(transactions.map { ($0.id, $0) }, uniquingKeysWith: { _, latest in latest })

        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = timeZone
        let todayDate = Date(timeIntervalSince1970: Double(nowMillis) / 1000.0)
        let todayComps = cal.dateComponents([.year, .month, .day], from: todayDate)
        let targetDate = cal.date(from: DateComponents(year: year, month: safeMonth, day: 1)) ?? todayDate
        let daysInMonth = cal.range(of: .day, in: .month, for: targetDate)?.count ?? 30
        let remainingDays: Int
        if year == todayComps.year && safeMonth == todayComps.month {
            remainingDays = max(1, daysInMonth - (todayComps.day ?? 1) + 1)
        } else if (year > (todayComps.year ?? year)) || (year == todayComps.year && safeMonth > (todayComps.month ?? safeMonth)) {
            remainingDays = daysInMonth
        } else {
            remainingDays = 0
        }

        func spentInRange(budget: BudgetDto, budgetCurrency: String, range: (Int64, Int64), childIds: Set<Int64>) -> Int64 {
            var rateCache: [String: Int64] = [:]
            var spent: Int64 = 0
            for tx in transactions {
                guard tx.status == TransactionStatus.CONFIRMED, !tx.excludeFromBudget, !tx.excludeFromStats else { continue }
                if ledgerId > 0 && tx.ledgerId != ledgerId { continue }
                if budget.ledgerId > 0 && ledgerId <= 0 && tx.ledgerId != budget.ledgerId { continue }

                if tx.type == TransactionType.INCOME, let refundId = tx.refundOfTxId {
                    let orig = txById[refundId]
                    let matchCat = budget.categoryId == nil ||
                        orig?.categoryId == budget.categoryId ||
                        (orig?.categoryId.map { childIds.contains($0) } ?? false)
                    let t = orig?.timestamp ?? tx.timestamp
                    if matchCat && t >= range.0 && t <= range.1 {
                        spent -= convertFrozenToDisplayMicros(tx: tx, displayCurrency: budgetCurrency, rateCache: &rateCache)
                    }
                    continue
                }
                guard tx.type == TransactionType.EXPENSE, tx.timestamp >= range.0, tx.timestamp <= range.1 else { continue }
                if let targetCat = budget.categoryId, tx.categoryId != targetCat && !(tx.categoryId.map { childIds.contains($0) } ?? false) {
                    continue
                }
                spent += convertFrozenToDisplayMicros(tx: tx, displayCurrency: budgetCurrency, rateCache: &rateCache)
            }
            return max(0, spent)
        }

        return enabledBudgets.map { budget in
            let budgetCurrency = CurrencyCatalog.normalize(budget.currencyCode)
            let isYearly = budget.period.caseInsensitiveCompare("YEARLY") == .orderedSame
            let range = isYearly ? yearRange : monthRange
            let childIds = budget.categoryId.flatMap { childrenByParent[$0] } ?? []
            let spent = spentInRange(budget: budget, budgetCurrency: budgetCurrency, range: range, childIds: childIds)
            let rollover: Int64
            if !isYearly && budget.rolloverEnabled {
                let prevSpent = spentInRange(budget: budget, budgetCurrency: budgetCurrency, range: prevMonthRange, childIds: childIds)
                rollover = max(0, budget.limitAmountMicros - prevSpent)
            } else {
                rollover = 0
            }
            let effectiveLimit = budget.limitAmountMicros + rollover
            let ratio = effectiveLimit > 0 ? Double(spent) / Double(effectiveLimit) : 0
            let remaining = max(0, effectiveLimit - spent)
            let daily = (remainingDays > 0 && remaining > 0) ? (remaining / Int64(remainingDays)) : 0
            let catName = budget.categoryId == nil
                ? "总预算"
                : (categoriesById[budget.categoryId!]?.name ?? (budget.name.isEmpty ? "分类预算" : budget.name))

            return BudgetProgress(
                budget: budget,
                categoryName: catName,
                spentInBudgetCurrencyMicros: spent,
                limitInBudgetCurrencyMicros: effectiveLimit,
                progressRatio: ratio,
                overBudget: effectiveLimit > 0 && spent > effectiveLimit,
                rolloverMicros: rollover,
                dailyAvailableMicros: daily,
                remainingDays: remainingDays
            )
        }
    }

    public func computeNetWorthHistory(
        monthsBack: Int = 6,
        displayCurrency: String? = nil,
        nowMillis: Int64 = currentEpochMillis()
    ) -> [NetWorthPoint] {
        let targetCurrency = CurrencyCatalog.normalize(displayCurrency ?? settings.baseCurrency)
        let netWorthAccounts = activeAccounts.filter(\.includeInNetWorth)
        let accountMap = Dictionary(netWorthAccounts.map { ($0.id, $0) }, uniquingKeysWith: { _, latest in latest })
        let months = buildMonthBuckets(monthsBack: monthsBack, nowMillis: nowMillis)
        var rateCache: [String: Int64] = [:]

        func rateToTarget(from cur: String) -> Int64 {
            let norm = CurrencyCatalog.normalize(cur)
            if norm == targetCurrency { return MoneyAmount.rateScale }
            if let cached = rateCache[norm] { return cached }
            let r = getRate(from: norm, to: targetCurrency).rateMicros
            rateCache[norm] = r
            return r
        }

        var balances = Dictionary(netWorthAccounts.map { ($0.id, $0.balanceMicros) }, uniquingKeysWith: { _, latest in latest })
        let sortedDescTxs = transactions
            .filter { $0.status == TransactionStatus.CONFIRMED }
            .sorted { $0.timestamp > $1.timestamp }

        let monthEnds: [(YearMonthKey, Int64)] = months.enumerated().map { idx, ym in
            let endMs = idx == months.count - 1 ? nowMillis : monthRangeMillis(year: ym.year, month: ym.month).1
            return (ym, endMs)
        }

        var pointsReversed: [NetWorthPoint] = []
        var txIdx = 0
        for (ym, endMs) in monthEnds.reversed() {
            while txIdx < sortedDescTxs.count && sortedDescTxs[txIdx].timestamp > endMs {
                let tx = sortedDescTxs[txIdx]
                switch tx.type {
                case TransactionType.EXPENSE:
                    if balances[tx.accountId] != nil {
                        balances[tx.accountId, default: 0] += tx.accountAmountMicros
                    }
                case TransactionType.INCOME, TransactionType.ADJUST:
                    if balances[tx.accountId] != nil {
                        balances[tx.accountId, default: 0] -= tx.accountAmountMicros
                    }
                case TransactionType.TRANSFER:
                    if balances[tx.accountId] != nil {
                        balances[tx.accountId, default: 0] += tx.accountAmountMicros
                    }
                    if let toId = tx.toAccountId, let toAmt = tx.toAmountMicros, balances[toId] != nil {
                        balances[toId, default: 0] -= toAmt
                    }
                default:
                    break
                }
                txIdx += 1
            }

            var netWorth: Int64 = 0
            for (accId, rawBal) in balances {
                let cur = accountMap[accId]?.currencyCode ?? targetCurrency
                netWorth += MoneyAmount.convert(amountMicros: rawBal, rateMicros: rateToTarget(from: cur))
            }
            pointsReversed.append(
                NetWorthPoint(label: "\(ym.month)月", epochMillis: endMs, netWorthMicros: netWorth)
            )
        }
        return pointsReversed.reversed()
    }

    public func computeTrendBuckets(
        granularity: String = StatsGranularity.MONTHLY,
        anchorDateMillis: Int64 = currentEpochMillis(),
        customStartMillis: Int64? = nil,
        customEndMillis: Int64? = nil,
        ledgerId: Int64 = 0,
        displayCurrency: String? = nil
    ) -> [TrendBucketPoint] {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = timeZone
        cal.firstWeekday = 2 // Monday
        let anchorDate = Date(timeIntervalSince1970: Double(anchorDateMillis) / 1000.0)
        var buckets: [(label: String, start: Int64, end: Int64)] = []

        switch granularity.uppercased() {
        case StatsGranularity.WEEKLY:
            let startOfDay = cal.startOfDay(for: anchorDate)
            let weekday = cal.component(.weekday, from: startOfDay) // 1=Sun, 2=Mon...
            let daysFromMonday = (weekday + 5) % 7
            let monday = cal.date(byAdding: .day, value: -daysFromMonday, to: startOfDay) ?? startOfDay
            let labels = ["周一", "周二", "周三", "周四", "周五", "周六", "周日"]
            for i in 0..<7 {
                let d = cal.date(byAdding: .day, value: i, to: monday) ?? monday
                let next = cal.date(byAdding: .day, value: 1, to: d) ?? d
                buckets.append((labels[i], epochMillis(of: d), epochMillis(of: next) - 1))
            }
        case StatsGranularity.YEARLY:
            let year = cal.component(.year, from: anchorDate)
            for m in 1...12 {
                let r = monthRangeMillis(year: year, month: m)
                buckets.append(("\(m)月", r.0, r.1))
            }
        case StatsGranularity.CUSTOM:
            let sDate = cal.startOfDay(for: Date(timeIntervalSince1970: Double(customStartMillis ?? anchorDateMillis) / 1000.0))
            let eDate = cal.startOfDay(for: Date(timeIntervalSince1970: Double(customEndMillis ?? anchorDateMillis) / 1000.0))
            let safeStart = min(sDate, eDate)
            let safeEnd = max(sDate, eDate)
            let days = (cal.dateComponents([.day], from: safeStart, to: safeEnd).day ?? 0) + 1
            if days <= 31 {
                for i in 0..<days {
                    let d = cal.date(byAdding: .day, value: i, to: safeStart) ?? safeStart
                    let next = cal.date(byAdding: .day, value: 1, to: d) ?? d
                    let comps = cal.dateComponents([.month, .day], from: d)
                    buckets.append(("\(comps.month ?? 1)/\(comps.day ?? 1)", epochMillis(of: d), epochMillis(of: next) - 1))
                }
            } else {
                var ym = yearMonth(of: epochMillis(of: safeStart))
                let endYm = yearMonth(of: epochMillis(of: safeEnd))
                while ym <= endYm {
                    let r = monthRangeMillis(year: ym.year, month: ym.month)
                    buckets.append(("\(ym.month)月", r.0, r.1))
                    ym = nextYearMonth(ym)
                }
            }
        default:
            let months = buildMonthBuckets(monthsBack: 6, nowMillis: anchorDateMillis)
            for ym in months {
                let r = monthRangeMillis(year: ym.year, month: ym.month)
                buckets.append(("\(ym.month)月", r.0, r.1))
            }
        }

        let targetCurrency = CurrencyCatalog.normalize(displayCurrency ?? settings.baseCurrency)
        var rateCache: [String: Int64] = [:]
        let txById = Dictionary(transactions.map { ($0.id, $0) }, uniquingKeysWith: { _, latest in latest })

        return buckets.map { bucket in
            var exp: Int64 = 0
            var inc: Int64 = 0
            for tx in transactions {
                guard tx.status == TransactionStatus.CONFIRMED, !tx.excludeFromStats else { continue }
                if ledgerId > 0 && tx.ledgerId != ledgerId { continue }
                if tx.type == TransactionType.INCOME, let refundId = tx.refundOfTxId {
                    let origTime = txById[refundId]?.timestamp ?? tx.timestamp
                    if origTime >= bucket.start && origTime <= bucket.end {
                        exp -= convertFrozenToDisplayMicros(tx: tx, displayCurrency: targetCurrency, rateCache: &rateCache)
                    }
                    continue
                }
                guard tx.timestamp >= bucket.start && tx.timestamp <= bucket.end else { continue }
                if tx.type == TransactionType.EXPENSE {
                    exp += convertFrozenToDisplayMicros(tx: tx, displayCurrency: targetCurrency, rateCache: &rateCache)
                } else if tx.type == TransactionType.INCOME {
                    inc += convertFrozenToDisplayMicros(tx: tx, displayCurrency: targetCurrency, rateCache: &rateCache)
                }
            }
            return TrendBucketPoint(
                label: bucket.label,
                startMillis: bucket.start,
                endMillis: bucket.end,
                expenseBaseMicros: max(0, exp),
                incomeBaseMicros: inc
            )
        }
    }

    public func monthRangeMillis(year: Int, month: Int) -> (Int64, Int64) {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = timeZone
        let safeMonth = min(12, max(1, month))
        let start = cal.date(from: DateComponents(year: year, month: safeMonth, day: 1)) ?? Date()
        let nextMonth = cal.date(byAdding: .month, value: 1, to: start) ?? start
        return (epochMillis(of: start), epochMillis(of: nextMonth) - 1)
    }

    public func yearRangeMillis(year: Int) -> (Int64, Int64) {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = timeZone
        let start = cal.date(from: DateComponents(year: year, month: 1, day: 1)) ?? Date()
        let nextYear = cal.date(from: DateComponents(year: year + 1, month: 1, day: 1)) ?? start
        return (epochMillis(of: start), epochMillis(of: nextYear) - 1)
    }

    // MARK: - Backup & Restore (.klbk V3 Interoperable with Android)

    public func buildBackupSnapshot(exportedAtMillis: Int64 = currentEpochMillis()) -> KairoBackupSnapshot {
        KairoBackupSnapshot(
            formatVersion: KairoBackupSnapshot.currentFormatVersion,
            exportedAtMillis: exportedAtMillis,
            appName: KairoBackupSnapshot.appName,
            settings: settings.toSnapshotDto(),
            accounts: accounts,
            categories: categories,
            transactions: transactions,
            exchangeRates: exchangeRates,
            accountAliases: accountAliases,
            parseRules: parseRules,
            rawCaptureLogs: rawCaptureLogs,
            budgets: budgets,
            recurringRules: recurringRules,
            ledgers: ledgers,
            debts: debts,
            installments: installments,
            tags: tags
        )
    }

    public func exportBackupJson() throws -> String {
        let snapshot = buildBackupSnapshot()
        let data = try JSONEncoder().encode(snapshot)
        guard let str = String(data: data, encoding: .utf8) else {
            throw LedgerStoreError.invalidBackupFormat("序列化备份 JSON 失败")
        }
        return str
    }

    public func exportEncryptedBackup(passphrase: String) throws -> Data {
        let json = try exportBackupJson()
        return try KlbkCrypto.encryptString(json, passphrase: passphrase)
    }

    public func decodeBackupJson(_ jsonString: String) throws -> KairoBackupSnapshot {
        let trimmed = jsonString.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty, let data = trimmed.data(using: .utf8) else {
            throw LedgerStoreError.invalidBackupFormat("备份 JSON 内容为空")
        }
        let snapshot: KairoBackupSnapshot
        do {
            snapshot = try JSONDecoder().decode(KairoBackupSnapshot.self, from: data)
        } catch {
            throw LedgerStoreError.invalidBackupFormat("备份文件 JSON 格式解析失败：\(error.localizedDescription)")
        }
        guard snapshot.appName == KairoBackupSnapshot.appName ||
                snapshot.appName == KairoBackupSnapshot.legacyAppName else {
            throw LedgerStoreError.invalidBackupFormat("不支持的备份来源：\(snapshot.appName)")
        }
        guard (1...KairoBackupSnapshot.currentFormatVersion).contains(snapshot.formatVersion) else {
            throw LedgerStoreError.invalidBackupFormat("不支持的备份格式版本：v\(snapshot.formatVersion)")
        }
        return snapshot
    }

    @discardableResult
    public func restoreFromSnapshot(_ snapshot: KairoBackupSnapshot) -> RestoreSummary {
        accounts = snapshot.accounts
        categories = snapshot.categories
        transactions = snapshot.transactions
        exchangeRates = snapshot.exchangeRates
        accountAliases = snapshot.accountAliases
        parseRules = snapshot.parseRules
        rawCaptureLogs = snapshot.rawCaptureLogs
        budgets = snapshot.budgets
        recurringRules = snapshot.recurringRules
        if !snapshot.ledgers.isEmpty {
            ledgers = snapshot.ledgers
        } else if ledgers.isEmpty {
            ledgers = DefaultSeedData.makeDefaultLedgers()
        }
        debts = snapshot.debts
        installments = snapshot.installments
        if snapshot.formatVersion >= 3 || !snapshot.tags.isEmpty {
            tags = snapshot.tags
        }

        settings = settings.applying(snapshot.settings)
        ensureActiveLedgerValid()
        persist()

        return RestoreSummary(
            accountsCount: accounts.count,
            transactionsCount: transactions.count,
            categoriesCount: categories.count,
            exchangeRatesCount: exchangeRates.count,
            rulesAndAliasesCount: accountAliases.count + parseRules.count,
            budgetsAndRecurringCount: budgets.count + recurringRules.count,
            captureLogsCount: rawCaptureLogs.count,
            tagsCount: tags.count
        )
    }

    @discardableResult
    public func restoreFromBackupJson(_ jsonString: String) throws -> RestoreSummary {
        let snapshot = try decodeBackupJson(jsonString)
        return restoreFromSnapshot(snapshot)
    }

    @discardableResult
    public func importEncryptedBackup(_ data: Data, passphrase: String) throws -> RestoreSummary {
        let json = try KlbkCrypto.decryptToString(data, passphrase: passphrase)
        return try restoreFromBackupJson(json)
    }

    // MARK: - High-Level Sync Convenience

    @discardableResult
    public func uploadWebDavBackup(client: WebDavClient = WebDavClient()) async throws -> RemoteBackupFile {
        let passphrase = settings.backupPassphrase
        let encrypted = try exportEncryptedBackup(passphrase: passphrase)
        let config = WebDavConfig(settings: settings)
        let remoteFile = try await client.uploadEncryptedBackup(data: encrypted, config: config)
        settings.lastBackupSyncMillis = remoteFile.modifiedAtMillis
        persist()
        return remoteFile
    }

    @discardableResult
    public func restoreWebDavBackup(remotePath: String, client: WebDavClient = WebDavClient()) async throws -> RestoreSummary {
        let passphrase = settings.backupPassphrase
        let config = WebDavConfig(settings: settings)
        let encrypted = try await client.downloadBackup(remotePath: remotePath, config: config)
        let summary = try importEncryptedBackup(encrypted, passphrase: passphrase)
        settings.lastBackupSyncMillis = currentEpochMillis()
        persist()
        return summary
    }

    // MARK: - External URL & Smart Draft Entry

    public func requestSmartInputLaunch(
        mode: SmartInputMode = .receiptImage,
        autoOpenPhotoPicker: Bool = false,
        autoParseOnPick: Bool = false
    ) {
        pendingSmartInputLaunch = SmartInputLaunchRequest(
            initialMode: mode,
            autoOpenPhotoPicker: autoOpenPhotoPicker,
            autoParseOnPick: autoParseOnPick
        )
    }

    public func consumePendingSmartInputLaunch() -> SmartInputLaunchRequest? {
        guard let current = pendingSmartInputLaunch else { return nil }
        pendingSmartInputLaunch = nil
        return current
    }

    @discardableResult
    public func handleIncomingURL(_ url: URL) throws -> TransactionDto? {
        guard let params = ExternalBillUrlParser.parse(url: url) else {
            return nil
        }
        return try recordExternalBill(params)
    }

    @discardableResult
    public func recordExternalBill(_ params: ExternalBillParams) throws -> TransactionDto {
        let targetCurrency = CurrencyCatalog.normalize(params.currency ?? settings.baseCurrency)
        let pool = activeAccounts.isEmpty ? accounts : activeAccounts
        guard let matchedAccount = resolveAccount(hint: params.accountNameOrHint, currency: targetCurrency, in: pool) else {
            throw LedgerStoreError.accountNotFound(0)
        }

        var matchedToAccountId: Int64?
        if params.type == TransactionType.TRANSFER {
            if let toHint = params.toAccountNameOrHint {
                matchedToAccountId = resolveAccount(hint: toHint, currency: targetCurrency, in: pool.filter { $0.id != matchedAccount.id })?.id
            }
            if matchedToAccountId == nil {
                matchedToAccountId = pool.first(where: { $0.id != matchedAccount.id })?.id
            }
        }

        let matchedCategory = resolveCategory(name: params.categoryName, type: params.type)
        let req = RecordTransactionRequest(
            type: params.type,
            timestamp: params.timestamp,
            origAmountMicros: params.origAmountMicros,
            origCurrency: targetCurrency,
            accountId: matchedAccount.id,
            toAccountId: matchedToAccountId,
            categoryId: params.type == TransactionType.TRANSFER ? nil : matchedCategory?.id,
            merchant: params.merchant,
            note: params.note,
            sourceType: CaptureChannel.AUTO_ACCOUNTING,
            status: TransactionStatus.CONFIRMED,
            ledgerId: settings.activeLedgerId
        )
        return try saveTransaction(req)
    }

    @discardableResult
    public func recordParsedDraft(_ draft: ParsedBillDraft, ledgerId: Int64? = nil) throws -> TransactionDto {
        let pool = activeAccounts.isEmpty ? accounts : activeAccounts
        let accountId = draft.accountId ?? resolveAccount(hint: draft.accountHint, currency: draft.origCurrency, in: pool)?.id
        guard let resolvedAccountId = accountId else {
            throw LedgerStoreError.accountNotFound(0)
        }
        let categoryId = draft.type == TransactionType.TRANSFER
            ? nil
            : (draft.categoryId ?? resolveCategory(name: draft.categoryName, type: draft.type)?.id)
        let req = RecordTransactionRequest(
            type: draft.type,
            timestamp: draft.timestamp,
            origAmountMicros: draft.origAmountMicros,
            origCurrency: draft.origCurrency,
            accountId: resolvedAccountId,
            toAccountId: draft.toAccountId,
            categoryId: categoryId,
            merchant: draft.merchant,
            note: draft.note,
            tags: draft.tags,
            sourceType: draft.parserUsed.hasPrefix("LLM") ? CaptureChannel.AI : CaptureChannel.SHARE,
            status: TransactionStatus.CONFIRMED,
            ledgerId: ledgerId ?? settings.activeLedgerId
        )
        return try saveTransaction(req)
    }

    // MARK: - Private Core Engine

    private func saveTransactionInternal(_ request: RecordTransactionRequest) throws -> TransactionDto {
        let validTypes: Set<String> = [
            TransactionType.EXPENSE,
            TransactionType.INCOME,
            TransactionType.TRANSFER,
            TransactionType.ADJUST
        ]
        guard validTypes.contains(request.type) else {
            throw LedgerStoreError.unsupportedTransactionType(request.type)
        }
        if request.type != TransactionType.ADJUST && request.origAmountMicros <= 0 {
            throw LedgerStoreError.nonPositiveAmount
        }
        guard let sourceAccount = account(byId: request.accountId) else {
            throw LedgerStoreError.accountNotFound(request.accountId)
        }

        let accountCurrency = CurrencyCatalog.normalize(sourceAccount.currencyCode)
        let origCurrency = CurrencyCatalog.normalize(request.origCurrency.isEmpty ? accountCurrency : request.origCurrency)
        let baseCurrency = CurrencyCatalog.normalize(settings.baseCurrency)
        let txDate = DefaultSeedData.formatISODate(epochMillis: request.timestamp, timeZone: timeZone)

        let conversion = resolveConversion(
            origAmountMicros: request.origAmountMicros,
            origCurrency: origCurrency,
            accountCurrency: accountCurrency,
            baseCurrency: baseCurrency,
            manualAccountAmountMicros: request.manualAccountAmountMicros,
            manualRateToAccountMicros: request.manualRateToAccountMicros,
            onDate: txDate
        )

        var resolvedToAccountId: Int64?
        var resolvedToAmountMicros: Int64?
        var resolvedToCurrency: String?

        if request.type == TransactionType.TRANSFER {
            guard let targetId = request.toAccountId else {
                throw LedgerStoreError.missingTransferTarget
            }
            if targetId == sourceAccount.id {
                throw LedgerStoreError.sameTransferAccount
            }
            guard let toAccount = account(byId: targetId) else {
                throw LedgerStoreError.accountNotFound(targetId)
            }
            let toCur = CurrencyCatalog.normalize(toAccount.currencyCode)
            let toAmount: Int64
            if let manualTo = request.manualToAmountMicros {
                guard manualTo > 0 else { throw LedgerStoreError.nonPositiveAmount }
                toAmount = manualTo
            } else if toCur == origCurrency {
                toAmount = request.origAmountMicros
            } else if toCur == accountCurrency {
                toAmount = conversion.accountAmountMicros
            } else {
                let quote = getRate(from: origCurrency, to: toCur, onDate: txDate)
                toAmount = MoneyAmount.convert(amountMicros: request.origAmountMicros, rateMicros: quote.rateMicros)
            }
            resolvedToAccountId = toAccount.id
            resolvedToAmountMicros = toAmount
            resolvedToCurrency = toCur
        }

        let now = currentEpochMillis()
        var createdAt = now
        var existingOldLedgerId: Int64?
        var existingIndex: Int?

        if let existingId = request.existingId {
            guard let idx = transactions.firstIndex(where: { $0.id == existingId }) else {
                throw LedgerStoreError.transactionNotFound(existingId)
            }
            let oldTx = transactions[idx]
            createdAt = oldTx.createdAt > 0 ? oldTx.createdAt : now
            existingOldLedgerId = oldTx.ledgerId
            existingIndex = idx
            applyBalanceDelta(oldTx, direction: -1, now: now)
        }

        let resolvedLedgerId = resolveTargetLedgerId(requested: request.ledgerId, existing: existingOldLedgerId)
        let assignedId = request.existingId ?? nextId(in: transactions.map(\.id))

        let entity = TransactionDto(
            id: assignedId,
            type: request.type,
            timestamp: request.timestamp,
            origAmountMicros: request.origAmountMicros,
            origCurrency: origCurrency,
            accountId: sourceAccount.id,
            accountAmountMicros: conversion.accountAmountMicros,
            accountCurrency: accountCurrency,
            toAccountId: resolvedToAccountId,
            toAmountMicros: resolvedToAmountMicros,
            toCurrency: resolvedToCurrency,
            rateToAccountMicros: conversion.rateToAccountMicros,
            rateToBaseMicros: conversion.rateToBaseMicros,
            baseCurrency: baseCurrency,
            categoryId: request.categoryId,
            merchant: request.merchant,
            note: request.note,
            tags: request.tags,
            reimburseStatus: request.reimburseStatus,
            sourceType: request.sourceType,
            rawEventId: request.rawEventId,
            status: request.status,
            ledgerId: resolvedLedgerId,
            excludeFromStats: request.excludeFromStats,
            excludeFromBudget: request.excludeFromBudget,
            refundOfTxId: request.refundOfTxId,
            attachmentPaths: request.attachmentPaths,
            reimbursedTxId: request.reimbursedTxId,
            createdAt: createdAt,
            updatedAt: now
        )

        if let idx = existingIndex {
            transactions[idx] = entity
        } else {
            transactions.append(entity)
        }
        applyBalanceDelta(entity, direction: 1, now: now)
        return entity
    }

    private func resolveConversion(
        origAmountMicros: Int64,
        origCurrency: String,
        accountCurrency: String,
        baseCurrency: String,
        manualAccountAmountMicros: Int64?,
        manualRateToAccountMicros: Int64?,
        onDate: String?
    ) -> ConversionPreview {
        let accountAmountMicros: Int64
        let rateToAccountMicros: Int64
        let accountRateSource: String

        if let manualAmt = manualAccountAmountMicros {
            accountAmountMicros = manualAmt
            if origCurrency == accountCurrency && origAmountMicros == manualAmt {
                rateToAccountMicros = MoneyAmount.rateScale
            } else {
                rateToAccountMicros = MoneyAmount.computeRate(origAmountMicros: origAmountMicros, targetAmountMicros: manualAmt)
            }
            accountRateSource = Self.sourceManualAmount
        } else if let manualRate = manualRateToAccountMicros, manualRate > 0 {
            rateToAccountMicros = manualRate
            accountAmountMicros = manualRate == MoneyAmount.rateScale
                ? origAmountMicros
                : MoneyAmount.convert(amountMicros: origAmountMicros, rateMicros: manualRate)
            accountRateSource = Self.sourceManualRate
        } else if origCurrency == accountCurrency {
            rateToAccountMicros = MoneyAmount.rateScale
            accountAmountMicros = origAmountMicros
            accountRateSource = Self.sourceIdentity
        } else {
            let quote = getRate(from: origCurrency, to: accountCurrency, onDate: onDate)
            rateToAccountMicros = quote.rateMicros
            accountAmountMicros = MoneyAmount.convert(amountMicros: origAmountMicros, rateMicros: rateToAccountMicros)
            accountRateSource = quote.source
        }

        let rateToBaseMicros: Int64
        let baseAmountMicros: Int64
        var finalSource = accountRateSource

        if origCurrency == baseCurrency {
            rateToBaseMicros = MoneyAmount.rateScale
            baseAmountMicros = origAmountMicros
        } else if accountCurrency == baseCurrency {
            rateToBaseMicros = rateToAccountMicros
            baseAmountMicros = accountAmountMicros
        } else {
            let baseQuote = getRate(from: origCurrency, to: baseCurrency, onDate: onDate)
            rateToBaseMicros = baseQuote.rateMicros
            baseAmountMicros = MoneyAmount.convert(amountMicros: origAmountMicros, rateMicros: rateToBaseMicros)
            if origCurrency == accountCurrency {
                finalSource = baseQuote.source
            }
        }

        return ConversionPreview(
            origAmountMicros: origAmountMicros,
            origCurrency: origCurrency,
            accountAmountMicros: accountAmountMicros,
            accountCurrency: accountCurrency,
            rateToAccountMicros: rateToAccountMicros,
            baseAmountMicros: baseAmountMicros,
            baseCurrency: baseCurrency,
            rateToBaseMicros: rateToBaseMicros,
            rateSource: finalSource
        )
    }

    private func applyBalanceDelta(_ tx: TransactionDto, direction: Int64, now: Int64) {
        switch tx.type {
        case TransactionType.EXPENSE:
            adjustAccountBalance(id: tx.accountId, delta: -tx.accountAmountMicros * direction, now: now)
        case TransactionType.INCOME, TransactionType.ADJUST:
            adjustAccountBalance(id: tx.accountId, delta: tx.accountAmountMicros * direction, now: now)
        case TransactionType.TRANSFER:
            adjustAccountBalance(id: tx.accountId, delta: -tx.accountAmountMicros * direction, now: now)
            if let toId = tx.toAccountId, let toAmount = tx.toAmountMicros {
                adjustAccountBalance(id: toId, delta: toAmount * direction, now: now)
            }
        default:
            break
        }
    }

    private func adjustAccountBalance(id: Int64, delta: Int64, now: Int64) {
        guard let idx = accounts.firstIndex(where: { $0.id == id }) else { return }
        accounts[idx].balanceMicros += delta
        accounts[idx].updatedAt = now
    }

    private func computeFrozenBaseMicros(_ tx: TransactionDto) -> Int64 {
        let orig = CurrencyCatalog.normalize(tx.origCurrency)
        let txBase = CurrencyCatalog.normalize(tx.baseCurrency)
        if orig == txBase {
            return tx.origAmountMicros
        }
        return MoneyAmount.convert(amountMicros: tx.origAmountMicros, rateMicros: tx.rateToBaseMicros)
    }

    private func convertFrozenToDisplayMicros(
        tx: TransactionDto,
        displayCurrency: String,
        rateCache: inout [String: Int64]
    ) -> Int64 {
        let frozenBase = computeFrozenBaseMicros(tx)
        let txBase = CurrencyCatalog.normalize(tx.baseCurrency)
        let target = CurrencyCatalog.normalize(displayCurrency)
        if txBase == target {
            return frozenBase
        }
        let rate: Int64
        if let cached = rateCache[txBase] {
            rate = cached
        } else {
            let fetched = getRate(from: txBase, to: target).rateMicros
            rateCache[txBase] = fetched
            rate = fetched
        }
        return MoneyAmount.convert(amountMicros: frozenBase, rateMicros: rate)
    }

    private func findStoredRate(base: String, target: String, onOrBefore dateFilter: String?) -> ExchangeRateDto? {
        let matching = exchangeRates.filter {
            $0.baseCurrency.uppercased() == base && $0.targetCurrency.uppercased() == target && $0.rateMicros > 0
        }
        guard !matching.isEmpty else { return nil }
        if let dateFilter {
            let onOrBefore = matching
                .filter { !$0.date.isEmpty && $0.date <= dateFilter }
                .sorted { lhs, rhs in
                    if lhs.date != rhs.date { return lhs.date > rhs.date }
                    return lhs.updatedAt > rhs.updatedAt
                }
            if let best = onOrBefore.first { return best }
        }
        return matching.sorted { lhs, rhs in
            if lhs.date != rhs.date { return lhs.date > rhs.date }
            return lhs.updatedAt > rhs.updatedAt
        }.first
    }

    private func findStoredRateToPivot(currency: String, onOrBefore dateFilter: String?) -> (rateMicros: Int64, date: String)? {
        let pivot = CurrencyCatalog.defaultBaseCurrency
        if let direct = findStoredRate(base: currency, target: pivot, onOrBefore: dateFilter) {
            return (direct.rateMicros, direct.date)
        }
        if let inv = findStoredRate(base: pivot, target: currency, onOrBefore: dateFilter) {
            return (MoneyAmount.invertRate(inv.rateMicros), inv.date)
        }
        return nil
    }

    private func upsertExchangeRate(
        base: String,
        target: String,
        rateMicros: Int64,
        date: String,
        source: String,
        now: Int64
    ) {
        if let idx = exchangeRates.firstIndex(where: {
            $0.baseCurrency.uppercased() == base &&
                $0.targetCurrency.uppercased() == target &&
                $0.date == date
        }) {
            exchangeRates[idx].rateMicros = rateMicros
            exchangeRates[idx].source = source
            exchangeRates[idx].updatedAt = now
        } else {
            let id = nextId(in: exchangeRates.map(\.id))
            exchangeRates.append(
                ExchangeRateDto(
                    id: id,
                    baseCurrency: base,
                    targetCurrency: target,
                    rateMicros: rateMicros,
                    date: date,
                    source: source,
                    updatedAt: now
                )
            )
        }
    }

    private func resolveAccount(hint: String?, currency: String, in pool: [AccountDto]) -> AccountDto? {
        guard !pool.isEmpty else { return nil }
        if let hint = hint?.trimmingCharacters(in: .whitespacesAndNewlines), !hint.isEmpty {
            if let exact = pool.first(where: { $0.name.caseInsensitiveCompare(hint) == .orderedSame }) { return exact }
            if let partial = pool.first(where: {
                $0.name.localizedCaseInsensitiveContains(hint) || hint.localizedCaseInsensitiveContains($0.name)
            }) { return partial }
        }
        let normCur = CurrencyCatalog.normalize(currency)
        if let byCur = pool.first(where: { $0.currencyCode.uppercased() == normCur }) {
            return byCur
        }
        return pool.first
    }

    private func resolveCategory(name: String?, type: String) -> CategoryDto? {
        let typed = categories.filter { $0.type.caseInsensitiveCompare(type) == .orderedSame }
        let pool = typed.isEmpty ? categories : typed
        if let name = name?.trimmingCharacters(in: .whitespacesAndNewlines), !name.isEmpty {
            if let exact = pool.first(where: { $0.name.caseInsensitiveCompare(name) == .orderedSame }) { return exact }
            if let partial = pool.first(where: {
                $0.name.localizedCaseInsensitiveContains(name) || name.localizedCaseInsensitiveContains($0.name)
            }) { return partial }
        }
        let fallback = type == TransactionType.INCOME ? "其他收入" : "其他支出"
        return pool.first(where: { $0.name == fallback }) ?? pool.first
    }

    private func resolveTargetLedgerId(requested: Int64, existing: Int64?) -> Int64 {
        let sorted = ledgers.sorted { lhs, rhs in
            if lhs.sortOrder != rhs.sortOrder { return lhs.sortOrder < rhs.sortOrder }
            return lhs.id < rhs.id
        }
        if sorted.isEmpty {
            if requested > 0 { return requested }
            if let existing, existing > 0 { return existing }
            if settings.activeLedgerId > 0 { return settings.activeLedgerId }
            return 1
        }
        if requested > 0, sorted.contains(where: { $0.id == requested }) {
            return requested
        }
        if let existing, existing > 0, sorted.contains(where: { $0.id == existing }) {
            return existing
        }
        return sorted.first(where: { $0.id == settings.activeLedgerId && !$0.archived })?.id
            ?? sorted.first(where: { !$0.archived })?.id
            ?? sorted[0].id
    }

    private func ensureActiveLedgerValid() {
        if ledgers.isEmpty {
            ledgers = DefaultSeedData.makeDefaultLedgers()
        }
        if !ledgers.contains(where: { $0.id == settings.activeLedgerId && !$0.archived }) {
            settings.activeLedgerId = activeLedgers.first?.id ?? ledgers[0].id
        }
    }

    private func computeNextRecurringMillis(rule: RecurringRuleDto, currentRunMillis: Int64) -> Int64 {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = timeZone
        let current = Date(timeIntervalSince1970: Double(currentRunMillis) / 1000.0)
        let timeComps = cal.dateComponents([.hour, .minute, .second], from: current)

        switch rule.cycleType.trimmingCharacters(in: .whitespacesAndNewlines).uppercased() {
        case "DAILY":
            return epochMillis(of: cal.date(byAdding: .day, value: 1, to: current) ?? current)
        case "WEEKLY":
            return epochMillis(of: cal.date(byAdding: .day, value: 7, to: current) ?? current)
        case "YEARLY":
            let nextYear = cal.component(.year, from: current) + 1
            let month = cal.component(.month, from: current)
            let desiredDay = (2...31).contains(rule.dayOfCycle) ? rule.dayOfCycle : cal.component(.day, from: current)
            return clampedMonthDateMillis(year: nextYear, month: month, desiredDay: desiredDay, time: timeComps, calendar: cal)
        default:
            let nextDate = cal.date(byAdding: .month, value: 1, to: current) ?? current
            let comps = cal.dateComponents([.year, .month], from: nextDate)
            let desiredDay = (1...31).contains(rule.dayOfCycle) ? rule.dayOfCycle : cal.component(.day, from: current)
            return clampedMonthDateMillis(
                year: comps.year ?? 2026,
                month: comps.month ?? 1,
                desiredDay: desiredDay,
                time: timeComps,
                calendar: cal
            )
        }
    }

    private func clampedMonthDateMillis(
        year: Int,
        month: Int,
        desiredDay: Int,
        time: DateComponents,
        calendar: Calendar
    ) -> Int64 {
        let firstDay = calendar.date(from: DateComponents(year: year, month: month, day: 1)) ?? Date()
        let maxDay = calendar.range(of: .day, in: .month, for: firstDay)?.count ?? 28
        let targetDay = min(max(1, desiredDay), maxDay)
        var comps = DateComponents(year: year, month: month, day: targetDay)
        comps.hour = time.hour
        comps.minute = time.minute
        comps.second = time.second
        let target = calendar.date(from: comps) ?? firstDay
        return epochMillis(of: target)
    }

    private func nextId(in existingIds: [Int64]) -> Int64 {
        (existingIds.max() ?? 0) + 1
    }

    private func persist() {
        try? FileManager.default.createDirectory(at: storageDirectory, withIntermediateDirectories: true)
        let snapshot = buildBackupSnapshot()
        if let data = try? JSONEncoder().encode(snapshot) {
            try? data.write(to: snapshotURL, options: [.atomic])
        }

        // 将敏感凭据剥离后写入 settings.json，凭据单独进入 Keychain / SecretStorage
        var sanitized = settings
        let secrets = LocalSecretsPayload(
            llmApiKey: settings.llmApiKey,
            webDavPassword: settings.webDavPassword,
            backupPassphrase: settings.backupPassphrase
        )
        sanitized.llmApiKey = ""
        sanitized.webDavPassword = ""
        sanitized.backupPassphrase = ""
        if let sData = try? JSONEncoder().encode(LocalSettingsEnvelope(settings: sanitized)) {
            try? sData.write(to: settingsURL, options: [.atomic])
        }
        secretStorage.save(secrets)
    }

    // MARK: - Calendar Helpers

    private struct YearMonthKey: Hashable, Comparable {
        let year: Int
        let month: Int

        static func < (lhs: YearMonthKey, rhs: YearMonthKey) -> Bool {
            if lhs.year != rhs.year { return lhs.year < rhs.year }
            return lhs.month < rhs.month
        }
    }

    private func yearMonth(of epochMillis: Int64) -> YearMonthKey {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = timeZone
        let comps = cal.dateComponents([.year, .month], from: Date(timeIntervalSince1970: Double(epochMillis) / 1000.0))
        return YearMonthKey(year: comps.year ?? 2026, month: comps.month ?? 1)
    }

    private func previousYearMonth(year: Int, month: Int) -> YearMonthKey {
        month == 1 ? YearMonthKey(year: year - 1, month: 12) : YearMonthKey(year: year, month: month - 1)
    }

    private func nextYearMonth(_ ym: YearMonthKey) -> YearMonthKey {
        ym.month == 12 ? YearMonthKey(year: ym.year + 1, month: 1) : YearMonthKey(year: ym.year, month: ym.month + 1)
    }

    private func buildMonthBuckets(monthsBack: Int, nowMillis: Int64) -> [YearMonthKey] {
        let count = max(1, monthsBack)
        var current = yearMonth(of: nowMillis)
        var result: [YearMonthKey] = []
        for _ in 0..<count {
            result.append(current)
            current = previousYearMonth(year: current.year, month: current.month)
        }
        return result.reversed()
    }

    private func epochMillis(of date: Date) -> Int64 {
        Int64((date.timeIntervalSince1970 * 1000.0).rounded())
    }
}

private extension String {
    var nonEmpty: String? {
        isEmpty ? nil : self
    }
}

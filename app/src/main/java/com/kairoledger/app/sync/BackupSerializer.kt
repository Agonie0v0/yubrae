package com.kairoledger.app.sync

import androidx.room.withTransaction
import com.kairoledger.app.data.db.AccountAliasDao
import com.kairoledger.app.data.db.AccountDao
import com.kairoledger.app.data.db.BudgetDao
import com.kairoledger.app.data.db.CategoryDao
import com.kairoledger.app.data.db.DebtRecordDao
import com.kairoledger.app.data.db.ExchangeRateDao
import com.kairoledger.app.data.db.InstallmentPlanDao
import com.kairoledger.app.data.db.KairoDatabase
import com.kairoledger.app.data.db.LedgerDao
import com.kairoledger.app.data.db.ParseRuleDao
import com.kairoledger.app.data.db.RawCaptureLogDao
import com.kairoledger.app.data.db.RecurringRuleDao
import com.kairoledger.app.data.db.TransactionDao
import com.kairoledger.app.data.prefs.AppSettings
import com.kairoledger.app.data.prefs.SettingsStore
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class RestoreSummary(
    val accountsCount: Int,
    val transactionsCount: Int,
    val categoriesCount: Int,
    val exchangeRatesCount: Int,
    val rulesAndAliasesCount: Int,
    val budgetsAndRecurringCount: Int,
    val captureLogsCount: Int
)

/**
 * 棱账全库与核心偏好快照序列化器：
 * 覆盖全部 Room 表及关键 AppSettings，支持导出 JSON 字符串与事务内安全回放恢复。
 */
class BackupSerializer(
    private val accountDao: AccountDao,
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao,
    private val exchangeRateDao: ExchangeRateDao,
    private val accountAliasDao: AccountAliasDao,
    private val parseRuleDao: ParseRuleDao,
    private val rawCaptureLogDao: RawCaptureLogDao,
    private val budgetDao: BudgetDao,
    private val recurringRuleDao: RecurringRuleDao,
    private val readSettings: suspend () -> AppSettings,
    private val writeSettings: suspend (AppSettings) -> Unit,
    private val runInTransaction: suspend (suspend () -> Unit) -> Unit,
    private val ledgerDao: LedgerDao? = null,
    private val debtRecordDao: DebtRecordDao? = null,
    private val installmentPlanDao: InstallmentPlanDao? = null
) {

    constructor(db: KairoDatabase, settingsStore: SettingsStore) : this(
        accountDao = db.accountDao(),
        transactionDao = db.transactionDao(),
        categoryDao = db.categoryDao(),
        exchangeRateDao = db.exchangeRateDao(),
        accountAliasDao = db.accountAliasDao(),
        parseRuleDao = db.parseRuleDao(),
        rawCaptureLogDao = db.rawCaptureLogDao(),
        budgetDao = db.budgetDao(),
        recurringRuleDao = db.recurringRuleDao(),
        readSettings = { settingsStore.getSnapshot() },
        writeSettings = { settingsStore.saveAll(it) },
        runInTransaction = { block -> db.withTransaction { block() } },
        ledgerDao = db.ledgerDao(),
        debtRecordDao = db.debtRecordDao(),
        installmentPlanDao = db.installmentPlanDao()
    )

    private val jsonCodec = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    suspend fun buildSnapshot(): KairoBackupSnapshot {
        val settings = readSettings()
        return KairoBackupSnapshot(
            formatVersion = KairoBackupSnapshot.CURRENT_FORMAT_VERSION,
            exportedAtMillis = System.currentTimeMillis(),
            appName = KairoBackupSnapshot.APP_NAME,
            settings = settings.toSnapshotDto(),
            accounts = accountDao.getAll().map { it.toDto() },
            categories = categoryDao.getAll().map { it.toDto() },
            transactions = transactionDao.getAll().map { it.toDto() },
            exchangeRates = exchangeRateDao.getAll().map { it.toDto() },
            accountAliases = accountAliasDao.getAll().map { it.toDto() },
            parseRules = parseRuleDao.getAll().map { it.toDto() },
            rawCaptureLogs = rawCaptureLogDao.getAll().map { it.toDto() },
            budgets = budgetDao.getAll().map { it.toDto() },
            recurringRules = recurringRuleDao.getAll().map { it.toDto() },
            ledgers = ledgerDao?.getAll()?.map { it.toDto() }.orEmpty(),
            debts = debtRecordDao?.getAll()?.map { it.toDto() }.orEmpty(),
            installments = installmentPlanDao?.getAll()?.map { it.toDto() }.orEmpty()
        )
    }

    suspend fun exportToJson(): String {
        val snapshot = buildSnapshot()
        return jsonCodec.encodeToString(snapshot)
    }

    fun decodeFromJson(jsonString: String): KairoBackupSnapshot {
        require(jsonString.isNotBlank()) { "备份 JSON 内容为空" }
        val snapshot = try {
            jsonCodec.decodeFromString<KairoBackupSnapshot>(jsonString)
        } catch (e: Exception) {
            throw IllegalArgumentException("备份文件 JSON 格式解析失败：${e.message}", e)
        }
        if (snapshot.appName != KairoBackupSnapshot.APP_NAME) {
            throw IllegalArgumentException("不支持的备份来源：${snapshot.appName}")
        }
        if (snapshot.formatVersion !in 1..KairoBackupSnapshot.CURRENT_FORMAT_VERSION) {
            throw IllegalArgumentException("不支持的备份格式版本：v${snapshot.formatVersion}")
        }
        return snapshot
    }

    suspend fun restoreFromJson(jsonString: String): RestoreSummary {
        val snapshot = decodeFromJson(jsonString)
        return restoreSnapshot(snapshot)
    }

    suspend fun restoreSnapshot(snapshot: KairoBackupSnapshot): RestoreSummary {
        val accounts = snapshot.accounts.map { it.toEntity() }
        val categories = snapshot.categories.map { it.toEntity() }
        val transactions = snapshot.transactions.map { it.toEntity() }
        val exchangeRates = snapshot.exchangeRates.map { it.toEntity() }
        val aliases = snapshot.accountAliases.map { it.toEntity() }
        val parseRules = snapshot.parseRules.map { it.toEntity() }
        val captureLogs = snapshot.rawCaptureLogs.map { it.toEntity() }
        val budgets = snapshot.budgets.map { it.toEntity() }
        val recurringRules = snapshot.recurringRules.map { it.toEntity() }
        val ledgers = snapshot.ledgers.map { it.toEntity() }
        val debts = snapshot.debts.map { it.toEntity() }
        val installments = snapshot.installments.map { it.toEntity() }

        runInTransaction {
            transactionDao.clearAll()
            rawCaptureLogDao.clearAll()
            recurringRuleDao.clearAll()
            installmentPlanDao?.clearAll()
            debtRecordDao?.clearAll()
            budgetDao.clearAll()
            accountAliasDao.clearAll()
            parseRuleDao.clearAll()
            exchangeRateDao.clearAll()
            categoryDao.clearAll()
            accountDao.clearAll()
            if (ledgers.isNotEmpty()) {
                ledgerDao?.clearAll()
                ledgerDao?.insertAll(ledgers)
            }

            if (accounts.isNotEmpty()) accountDao.insertAll(accounts)
            if (categories.isNotEmpty()) categoryDao.insertAll(categories)
            if (exchangeRates.isNotEmpty()) exchangeRateDao.upsertAll(exchangeRates)
            if (parseRules.isNotEmpty()) parseRuleDao.insertAll(parseRules)
            if (aliases.isNotEmpty()) accountAliasDao.insertAll(aliases)
            if (budgets.isNotEmpty()) budgetDao.insertAll(budgets)
            if (recurringRules.isNotEmpty()) recurringRuleDao.insertAll(recurringRules)
            if (debts.isNotEmpty()) debtRecordDao?.insertAll(debts)
            if (installments.isNotEmpty()) installmentPlanDao?.insertAll(installments)
            if (captureLogs.isNotEmpty()) rawCaptureLogDao.insertAll(captureLogs)
            if (transactions.isNotEmpty()) transactionDao.insertAll(transactions)
        }

        val currentSettings = readSettings()
        writeSettings(snapshot.settings.applyOnto(currentSettings))

        return RestoreSummary(
            accountsCount = accounts.size,
            transactionsCount = transactions.size,
            categoriesCount = categories.size,
            exchangeRatesCount = exchangeRates.size,
            rulesAndAliasesCount = aliases.size + parseRules.size,
            budgetsAndRecurringCount = budgets.size + recurringRules.size,
            captureLogsCount = captureLogs.size
        )
    }
}

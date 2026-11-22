package com.kairoledger.app.core.contract

import com.kairoledger.app.data.db.AccountAliasEntity
import com.kairoledger.app.data.db.AccountEntity
import com.kairoledger.app.data.db.BudgetEntity
import com.kairoledger.app.data.db.CaptureChannel
import com.kairoledger.app.data.db.CategoryEntity
import com.kairoledger.app.data.db.ExchangeRateEntity
import com.kairoledger.app.data.db.ParseRuleEntity
import com.kairoledger.app.data.db.RawCaptureLogEntity
import com.kairoledger.app.data.db.RecurringRuleEntity
import com.kairoledger.app.data.db.ReimburseStatus
import com.kairoledger.app.data.db.TransactionEntity
import com.kairoledger.app.data.db.TransactionStatus
import kotlinx.coroutines.flow.Flow
import java.io.InputStream

/**
 * 跨模块稳定契约层（所有 Swarm Worker 共享此契约，互不修改签名）。
 */

data class RecordTransactionRequest(
    val existingId: Long? = null,
    val type: String,
    val timestamp: Long = System.currentTimeMillis(),
    val origAmountMicros: Long,
    val origCurrency: String,
    val accountId: Long,
    /** 若不为空，表示用户或银行入账报文指定的真实扣款额（用于汇损修正/自动反算汇率）。 */
    val manualAccountAmountMicros: Long? = null,
    /** 若不为空，表示显式指定 origCurrency -> accountCurrency 成交汇率。 */
    val manualRateToAccountMicros: Long? = null,
    val toAccountId: Long? = null,
    val manualToAmountMicros: Long? = null,
    val categoryId: Long? = null,
    val merchant: String = "",
    val note: String = "",
    val tags: String = "",
    val reimburseStatus: String = ReimburseStatus.NONE,
    val sourceType: String = CaptureChannel.MANUAL,
    val rawEventId: Long? = null,
    val status: String = TransactionStatus.CONFIRMED
)

data class ConversionPreview(
    val origAmountMicros: Long,
    val origCurrency: String,
    val accountAmountMicros: Long,
    val accountCurrency: String,
    val rateToAccountMicros: Long,
    val baseAmountMicros: Long,
    val baseCurrency: String,
    val rateToBaseMicros: Long,
    val rateSource: String
)

data class RateQuote(
    val fromCurrency: String,
    val toCurrency: String,
    val rateMicros: Long,
    val date: String,
    val source: String
)

data class NetWorthSummary(
    val displayCurrency: String,
    val totalAssetsMicros: Long,
    val totalLiabilitiesMicros: Long,
    val netWorthMicros: Long,
    val accountConvertedBalances: Map<Long, Long>
)

data class CategoryStat(
    val categoryId: Long?,
    val categoryName: String,
    val colorHex: Long,
    val icon: String,
    val totalBaseMicros: Long,
    val count: Int,
    val percentage: Float
)

data class MonthlyPoint(
    val monthLabel: String,
    val year: Int,
    val month: Int,
    val expenseBaseMicros: Long,
    val incomeBaseMicros: Long
)

data class BudgetProgress(
    val budget: BudgetEntity,
    val categoryName: String,
    val spentInBudgetCurrencyMicros: Long,
    val limitInBudgetCurrencyMicros: Long,
    val progressRatio: Float,
    val overBudget: Boolean
)

interface ExchangeRateProvider {
    fun observeRates(): Flow<List<ExchangeRateEntity>>

    suspend fun getRate(
        fromCurrency: String,
        toCurrency: String,
        onDate: String? = null
    ): RateQuote

    suspend fun refreshFromNetwork(baseCurrency: String = "CNY"): Result<Int>

    suspend fun saveCustomRate(
        fromCurrency: String,
        toCurrency: String,
        rateMicros: Long
    )
}

interface LedgerService {
    fun observeActiveAccounts(): Flow<List<AccountEntity>>
    fun observeAllAccounts(): Flow<List<AccountEntity>>
    fun observeTransactions(): Flow<List<TransactionEntity>>
    fun observePendingTransactions(): Flow<List<TransactionEntity>>
    fun observePendingCount(): Flow<Int>
    fun observeCategories(): Flow<List<CategoryEntity>>
    fun observeBudgets(): Flow<List<BudgetEntity>>
    fun observeRecurringRules(): Flow<List<RecurringRuleEntity>>

    suspend fun previewConversion(
        origAmountMicros: Long,
        origCurrency: String,
        accountId: Long,
        manualAccountAmountMicros: Long? = null,
        manualRateToAccountMicros: Long? = null,
        baseCurrency: String? = null
    ): ConversionPreview

    suspend fun saveTransaction(request: RecordTransactionRequest): Result<TransactionEntity>
    suspend fun deleteTransaction(transactionId: Long): Result<Unit>
    suspend fun confirmTransaction(transactionId: Long): Result<Unit>
    suspend fun confirmAllPending(): Result<Int>

    suspend fun saveAccount(account: AccountEntity): Long
    suspend fun archiveAccount(accountId: Long, archived: Boolean)
    suspend fun calibrateAccountBalance(accountId: Long, targetBalanceMicros: Long, note: String = "余额校准"): Result<TransactionEntity>

    suspend fun saveCategory(category: CategoryEntity): Long
    suspend fun deleteCategory(categoryId: Long)

    suspend fun saveBudget(budget: BudgetEntity): Long
    suspend fun deleteBudget(budgetId: Long)

    suspend fun saveRecurringRule(rule: RecurringRuleEntity): Long
    suspend fun deleteRecurringRule(ruleId: Long)
    suspend fun processDueRecurringRules(nowMillis: Long = System.currentTimeMillis()): Int

    suspend fun computeNetWorth(displayCurrency: String): NetWorthSummary
    suspend fun computeMonthlyTrend(monthsBack: Int = 6, displayCurrency: String): List<MonthlyPoint>
    suspend fun computeCategoryStats(startMillis: Long, endMillis: Long, type: String, displayCurrency: String): List<CategoryStat>
    suspend fun computeBudgetProgress(year: Int, month: Int): List<BudgetProgress>
}

data class CaptureInput(
    val channel: String,
    val sourcePackage: String,
    val title: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class ParsedBillDraft(
    val type: String,
    val origAmountMicros: Long,
    val origCurrency: String,
    val accountId: Long?,
    val accountHint: String = "",
    val categoryId: Long?,
    val categoryName: String = "",
    val merchant: String = "",
    val note: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val confidence: Float = 0.9f,
    val parserUsed: String = "LOCAL_RULE"
)

data class CaptureHandleResult(
    val logId: Long,
    val status: String,
    val transactionId: Long? = null,
    val mergedIntoExisting: Boolean = false,
    val draft: ParsedBillDraft? = null,
    val message: String = ""
)

data class DevicePermissionReport(
    val isRooted: Boolean,
    val hasLsposedHint: Boolean,
    val notificationListenerEnabled: Boolean,
    val smsPermissionGranted: Boolean,
    val overlayPermissionGranted: Boolean,
    val ignoringBatteryOptimizations: Boolean,
    val postNotificationGranted: Boolean,
    val manufacturer: String,
    val osDisplay: String
)

interface CaptureEngine {
    fun observeCaptureLogs(limit: Int = 200): Flow<List<RawCaptureLogEntity>>
    fun observeAccountAliases(): Flow<List<AccountAliasEntity>>
    fun observeParseRules(): Flow<List<ParseRuleEntity>>

    suspend fun handleIncomingCapture(input: CaptureInput, showFloatingPrompt: Boolean = true): CaptureHandleResult
    suspend fun testParseText(title: String, content: String, sourcePackage: String = "*", allowLlm: Boolean = false): Result<ParsedBillDraft?>
    suspend fun testLlmConnection(): Result<String>
    suspend fun promoteLogToAliasOrRule(logId: Long, accountId: Long, matchPattern: String, matchType: String): Result<Unit>

    suspend fun saveAlias(alias: AccountAliasEntity): Long
    suspend fun deleteAlias(aliasId: Long)
    suspend fun saveRule(rule: ParseRuleEntity): Long
    suspend fun deleteRule(ruleId: Long)

    suspend fun inspectPermissions(): DevicePermissionReport
    suspend fun tryRootGrantPermissions(): Result<String>
}

data class ImportPreview(
    val formatName: String,
    val totalRows: Int,
    val validRows: Int,
    val skippedDuplicateRows: Int,
    val newAccountNames: List<String>,
    val newCategoryNames: List<String>,
    val detectedCurrencies: List<String>,
    val warnings: List<String>
)

data class ImportSummary(
    val importedCount: Int,
    val skippedDuplicateCount: Int,
    val createdAccounts: Int,
    val createdCategories: Int,
    val warnings: List<String>
)

interface BillImporter {
    suspend fun previewCsv(inputStream: InputStream): Result<ImportPreview>
    suspend fun importCsv(inputStream: InputStream): Result<ImportSummary>
}

data class RemoteBackupFile(
    val name: String,
    val path: String,
    val sizeBytes: Long,
    val modifiedAtMillis: Long
)

interface CloudSyncService {
    suspend fun testWebDavConnection(): Result<String>
    suspend fun listRemoteBackups(): Result<List<RemoteBackupFile>>
    suspend fun uploadEncryptedBackup(): Result<RemoteBackupFile>
    suspend fun restoreEncryptedBackup(remotePath: String): Result<String>
    suspend fun exportLocalEncryptedBytes(passphrase: String): Result<ByteArray>
    suspend fun importLocalEncryptedBytes(bytes: ByteArray, passphrase: String): Result<String>
}

object IntentConstants {
    const val ACTION_ADD_RECORD = "com.kairoledger.app.ACTION_ADD_RECORD"
    const val EXTRA_OPEN_QUICK_RECORD = "com.kairoledger.app.EXTRA_OPEN_QUICK_RECORD"
    const val EXTRA_EDIT_TRANSACTION_ID = "com.kairoledger.app.EXTRA_EDIT_TRANSACTION_ID"
    const val EXTRA_INITIAL_TAB = "com.kairoledger.app.EXTRA_INITIAL_TAB"
}


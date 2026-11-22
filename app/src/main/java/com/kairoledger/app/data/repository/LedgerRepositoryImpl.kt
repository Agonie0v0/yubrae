package com.kairoledger.app.data.repository

import androidx.room.withTransaction
import com.kairoledger.app.core.contract.BudgetProgress
import com.kairoledger.app.core.contract.CategoryStat
import com.kairoledger.app.core.contract.ConversionPreview
import com.kairoledger.app.core.contract.ExchangeRateProvider
import com.kairoledger.app.core.contract.LedgerService
import com.kairoledger.app.core.contract.MonthlyPoint
import com.kairoledger.app.core.contract.NetWorthSummary
import com.kairoledger.app.core.contract.RecordTransactionRequest
import com.kairoledger.app.core.model.MoneyAmount
import com.kairoledger.app.data.db.AccountDao
import com.kairoledger.app.data.db.AccountEntity
import com.kairoledger.app.data.db.BudgetDao
import com.kairoledger.app.data.db.BudgetEntity
import com.kairoledger.app.data.db.CaptureChannel
import com.kairoledger.app.data.db.CategoryDao
import com.kairoledger.app.data.db.CategoryEntity
import com.kairoledger.app.data.db.KairoDatabase
import com.kairoledger.app.data.db.RecurringRuleDao
import com.kairoledger.app.data.db.RecurringRuleEntity
import com.kairoledger.app.data.db.TransactionDao
import com.kairoledger.app.data.db.TransactionEntity
import com.kairoledger.app.data.db.TransactionStatus
import com.kairoledger.app.data.db.TransactionType
import com.kairoledger.app.data.prefs.AppSettings
import com.kairoledger.app.data.prefs.SettingsStore
import com.kairoledger.app.domain.LedgerAnalytics
import com.kairoledger.app.domain.RecurringScheduleCalculator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.ZoneId

/**
 * 记账核心仓库实现：
 * - 全部金额计算严格使用 MoneyAmount 微元 Long 运算
 * - 交易写入/编辑/删除/余额校准/周期执行均在事务中原子完成
 * - 主构造函数接收 DAO 接口与回调，可直接用于纯 JVM 单元测试；次构造函数供 AppContainer 注入
 */
class LedgerRepositoryImpl(
    private val accountDao: AccountDao,
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao,
    private val budgetDao: BudgetDao,
    private val recurringRuleDao: RecurringRuleDao,
    private val exchangeRateProvider: ExchangeRateProvider,
    private val getSettings: suspend () -> AppSettings = { AppSettings() },
    private val inTransaction: suspend (suspend () -> Any?) -> Any? = { block -> block() },
    private val zoneId: ZoneId = ZoneId.systemDefault(),
    private val clockMillisProvider: () -> Long = { System.currentTimeMillis() }
) : LedgerService {

    constructor(
        db: KairoDatabase,
        exchangeRateProvider: ExchangeRateProvider,
        settingsStore: SettingsStore
    ) : this(
        accountDao = db.accountDao(),
        transactionDao = db.transactionDao(),
        categoryDao = db.categoryDao(),
        budgetDao = db.budgetDao(),
        recurringRuleDao = db.recurringRuleDao(),
        exchangeRateProvider = exchangeRateProvider,
        getSettings = { settingsStore.getSnapshot() },
        inTransaction = { block -> db.withTransaction { block() } }
    )

    override fun observeActiveAccounts(): Flow<List<AccountEntity>> = accountDao.observeActive()

    override fun observeAllAccounts(): Flow<List<AccountEntity>> = accountDao.observeAll()

    override fun observeTransactions(): Flow<List<TransactionEntity>> = transactionDao.observeAll()

    override fun observePendingTransactions(): Flow<List<TransactionEntity>> = transactionDao.observePending()

    override fun observePendingCount(): Flow<Int> = transactionDao.observePendingCount()

    override fun observeCategories(): Flow<List<CategoryEntity>> = categoryDao.observeAll()

    override fun observeBudgets(): Flow<List<BudgetEntity>> = budgetDao.observeAll()

    override fun observeRecurringRules(): Flow<List<RecurringRuleEntity>> = recurringRuleDao.observeAll()

    override suspend fun previewConversion(
        origAmountMicros: Long,
        origCurrency: String,
        accountId: Long,
        manualAccountAmountMicros: Long?,
        manualRateToAccountMicros: Long?,
        baseCurrency: String?
    ): ConversionPreview {
        val settings = getSettings()
        val resolvedBase = LedgerAnalytics.normalizeCurrency(baseCurrency ?: settings.baseCurrency)
        val account = accountDao.getById(accountId)
        val accountCurrency = LedgerAnalytics.normalizeCurrency(account?.currencyCode ?: resolvedBase)
        val normOrigCurrency = LedgerAnalytics.normalizeCurrency(origCurrency.ifBlank { accountCurrency })

        return resolveConversion(
            origAmountMicros = origAmountMicros,
            origCurrency = normOrigCurrency,
            accountCurrency = accountCurrency,
            baseCurrency = resolvedBase,
            manualAccountAmountMicros = manualAccountAmountMicros,
            manualRateToAccountMicros = manualRateToAccountMicros,
            onDate = null
        )
    }

    override suspend fun saveTransaction(request: RecordTransactionRequest): Result<TransactionEntity> {
        return try {
            runInTransaction {
                saveTransactionInternal(request)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun saveTransactionInternal(request: RecordTransactionRequest): Result<TransactionEntity> {
        if (request.type != TransactionType.EXPENSE &&
            request.type != TransactionType.INCOME &&
            request.type != TransactionType.TRANSFER &&
            request.type != TransactionType.ADJUST
        ) {
            return Result.failure(IllegalArgumentException("不支持的交易类型: ${request.type}"))
        }
        if (request.type != TransactionType.ADJUST && request.origAmountMicros <= 0L) {
            return Result.failure(IllegalArgumentException("交易金额必须大于 0"))
        }

        val sourceAccount = accountDao.getById(request.accountId)
            ?: return Result.failure(IllegalArgumentException("源账户不存在: ${request.accountId}"))

        val accountCurrency = LedgerAnalytics.normalizeCurrency(sourceAccount.currencyCode)
        val origCurrency = LedgerAnalytics.normalizeCurrency(request.origCurrency.ifBlank { accountCurrency })
        val settings = getSettings()
        val baseCurrency = LedgerAnalytics.normalizeCurrency(settings.baseCurrency)
        val txDate = Instant.ofEpochMilli(request.timestamp).atZone(zoneId).toLocalDate().toString()

        val conversion = resolveConversion(
            origAmountMicros = request.origAmountMicros,
            origCurrency = origCurrency,
            accountCurrency = accountCurrency,
            baseCurrency = baseCurrency,
            manualAccountAmountMicros = request.manualAccountAmountMicros,
            manualRateToAccountMicros = request.manualRateToAccountMicros,
            onDate = txDate
        )

        var resolvedToAccountId: Long? = null
        var resolvedToAmountMicros: Long? = null
        var resolvedToCurrency: String? = null

        if (request.type == TransactionType.TRANSFER) {
            val targetId = request.toAccountId
                ?: return Result.failure(IllegalArgumentException("转账交易必须指定目标账户"))
            if (targetId == sourceAccount.id) {
                return Result.failure(IllegalArgumentException("转出与转入账户不能相同"))
            }
            val toAccount = accountDao.getById(targetId)
                ?: return Result.failure(IllegalArgumentException("目标账户不存在: $targetId"))
            val toCurrency = LedgerAnalytics.normalizeCurrency(toAccount.currencyCode)
            val toAmount = when {
                request.manualToAmountMicros != null -> {
                    if (request.manualToAmountMicros <= 0L) {
                        return Result.failure(IllegalArgumentException("转入金额必须大于 0"))
                    }
                    request.manualToAmountMicros
                }
                toCurrency == origCurrency -> request.origAmountMicros
                toCurrency == accountCurrency -> conversion.accountAmountMicros
                else -> {
                    val toQuote = exchangeRateProvider.getRate(origCurrency, toCurrency, onDate = txDate)
                    MoneyAmount.convert(request.origAmountMicros, toQuote.rateMicros)
                }
            }
            resolvedToAccountId = toAccount.id
            resolvedToAmountMicros = toAmount
            resolvedToCurrency = toCurrency
        }

        val now = clockMillisProvider()
        val existingId = request.existingId
        var createdAt = now
        if (existingId != null) {
            val oldTx = transactionDao.getById(existingId)
                ?: return Result.failure(IllegalArgumentException("待编辑交易不存在: $existingId"))
            createdAt = oldTx.createdAt
            applyBalanceDelta(oldTx, direction = -1L, now = now)
        }

        val entity = TransactionEntity(
            id = existingId ?: 0L,
            type = request.type,
            timestamp = request.timestamp,
            origAmountMicros = request.origAmountMicros,
            origCurrency = origCurrency,
            accountId = sourceAccount.id,
            accountAmountMicros = conversion.accountAmountMicros,
            accountCurrency = accountCurrency,
            toAccountId = resolvedToAccountId,
            toAmountMicros = resolvedToAmountMicros,
            toCurrency = resolvedToCurrency,
            rateToAccountMicros = conversion.rateToAccountMicros,
            rateToBaseMicros = conversion.rateToBaseMicros,
            baseCurrency = baseCurrency,
            categoryId = request.categoryId,
            merchant = request.merchant,
            note = request.note,
            tags = request.tags,
            reimburseStatus = request.reimburseStatus,
            sourceType = request.sourceType,
            rawEventId = request.rawEventId,
            status = request.status,
            createdAt = createdAt,
            updatedAt = now
        )

        val saved = if (existingId != null) {
            transactionDao.update(entity)
            entity
        } else {
            val newId = transactionDao.insert(entity)
            entity.copy(id = newId)
        }
        applyBalanceDelta(saved, direction = 1L, now = now)
        return Result.success(saved)
    }

    override suspend fun deleteTransaction(transactionId: Long): Result<Unit> {
        return try {
            runInTransaction {
                val existing = transactionDao.getById(transactionId)
                    ?: return@runInTransaction Result.failure(
                        IllegalArgumentException("交易不存在: $transactionId")
                    )
                val now = clockMillisProvider()
                applyBalanceDelta(existing, direction = -1L, now = now)
                transactionDao.deleteById(transactionId)
                Result.success(Unit)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun confirmTransaction(transactionId: Long): Result<Unit> {
        return try {
            runInTransaction {
                transactionDao.getById(transactionId)
                    ?: return@runInTransaction Result.failure(
                        IllegalArgumentException("交易不存在: $transactionId")
                    )
                transactionDao.confirmById(transactionId, clockMillisProvider())
                Result.success(Unit)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun confirmAllPending(): Result<Int> {
        return try {
            runInTransaction {
                val pendingCount = transactionDao.getAll().count { it.status == TransactionStatus.PENDING }
                if (pendingCount > 0) {
                    transactionDao.confirmAllPending(clockMillisProvider())
                }
                Result.success(pendingCount)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun saveAccount(account: AccountEntity): Long {
        val now = clockMillisProvider()
        val exists = account.id > 0L && accountDao.getById(account.id) != null
        return if (!exists) {
            val created = if (account.createdAt > 0L) account.createdAt else now
            accountDao.insert(account.copy(createdAt = created, updatedAt = now))
        } else {
            accountDao.update(account.copy(updatedAt = now))
            account.id
        }
    }

    override suspend fun archiveAccount(accountId: Long, archived: Boolean) {
        val existing = accountDao.getById(accountId) ?: return
        accountDao.update(existing.copy(archived = archived, updatedAt = clockMillisProvider()))
    }

    override suspend fun calibrateAccountBalance(
        accountId: Long,
        targetBalanceMicros: Long,
        note: String
    ): Result<TransactionEntity> {
        return try {
            runInTransaction {
                val account = accountDao.getById(accountId)
                    ?: return@runInTransaction Result.failure(
                        IllegalArgumentException("账户不存在: $accountId")
                    )
                val delta = targetBalanceMicros - account.balanceMicros
                saveTransactionInternal(
                    RecordTransactionRequest(
                        type = TransactionType.ADJUST,
                        timestamp = clockMillisProvider(),
                        origAmountMicros = delta,
                        origCurrency = account.currencyCode,
                        accountId = account.id,
                        merchant = "余额校准",
                        note = note.ifBlank { "余额校准" },
                        sourceType = CaptureChannel.MANUAL,
                        status = TransactionStatus.CONFIRMED
                    )
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun saveCategory(category: CategoryEntity): Long {
        val exists = category.id > 0L && categoryDao.getById(category.id) != null
        return if (!exists) {
            categoryDao.insert(category)
        } else {
            categoryDao.update(category)
            category.id
        }
    }

    override suspend fun deleteCategory(categoryId: Long) {
        categoryDao.deleteById(categoryId)
    }

    override suspend fun saveBudget(budget: BudgetEntity): Long {
        val exists = budget.id > 0L && budgetDao.getAll().any { it.id == budget.id }
        return if (!exists) {
            budgetDao.insert(budget)
        } else {
            budgetDao.update(budget)
            budget.id
        }
    }

    override suspend fun deleteBudget(budgetId: Long) {
        budgetDao.deleteById(budgetId)
    }

    override suspend fun saveRecurringRule(rule: RecurringRuleEntity): Long {
        val exists = rule.id > 0L && recurringRuleDao.getAll().any { it.id == rule.id }
        return if (!exists) {
            recurringRuleDao.insert(rule)
        } else {
            recurringRuleDao.update(rule)
            rule.id
        }
    }

    override suspend fun deleteRecurringRule(ruleId: Long) {
        recurringRuleDao.deleteById(ruleId)
    }

    override suspend fun processDueRecurringRules(nowMillis: Long): Int {
        val dueRules = recurringRuleDao.getDueRules(nowMillis)
        if (dueRules.isEmpty()) return 0

        var executedTransactions = 0
        for (rule in dueRules) {
            if (!rule.enabled || rule.nextRunEpochMillis <= 0L) continue
            val createdForRule = runInTransaction {
                var currentRun = rule.nextRunEpochMillis
                var iterations = 0
                var ruleCount = 0

                while (currentRun <= nowMillis && iterations < MAX_RECURRING_CATCHUP_RUNS) {
                    val result = saveTransactionInternal(
                        RecordTransactionRequest(
                            type = rule.type,
                            timestamp = currentRun,
                            origAmountMicros = rule.amountMicros,
                            origCurrency = rule.currencyCode,
                            accountId = rule.accountId,
                            toAccountId = rule.toAccountId,
                            categoryId = rule.categoryId,
                            merchant = rule.merchant.ifBlank { rule.name },
                            note = rule.note.ifBlank { "周期自动记账：${rule.name}" },
                            sourceType = CaptureChannel.RECURRING,
                            status = TransactionStatus.CONFIRMED
                        )
                    )
                    if (result.isFailure) {
                        break
                    }
                    ruleCount++
                    iterations++
                    val nextRun = RecurringScheduleCalculator.nextRunEpochMillis(
                        rule = rule,
                        currentRunMillis = currentRun,
                        zoneId = zoneId
                    )
                    if (nextRun <= currentRun) break
                    currentRun = nextRun
                }

                if (ruleCount > 0) {
                    recurringRuleDao.update(rule.copy(nextRunEpochMillis = currentRun))
                }
                ruleCount
            }
            executedTransactions += createdForRule
        }
        return executedTransactions
    }

    override suspend fun computeNetWorth(displayCurrency: String): NetWorthSummary {
        val accounts = accountDao.getAll()
        return LedgerAnalytics.computeNetWorth(
            accounts = accounts,
            displayCurrency = displayCurrency,
            fetchRate = { from, to -> exchangeRateProvider.getRate(from, to).rateMicros }
        )
    }

    override suspend fun computeMonthlyTrend(
        monthsBack: Int,
        displayCurrency: String
    ): List<MonthlyPoint> {
        val nowMillis = clockMillisProvider()
        val months = LedgerAnalytics.buildMonthBuckets(monthsBack, nowMillis, zoneId)
        val firstMonth = months.first()
        val lastMonth = months.last()
        val startMillis = LedgerAnalytics.monthRangeMillis(firstMonth.year, firstMonth.monthValue, zoneId).first
        val endMillis = LedgerAnalytics.monthRangeMillis(lastMonth.year, lastMonth.monthValue, zoneId).second
        val transactions = transactionDao.getBetween(startMillis, endMillis)

        return LedgerAnalytics.computeMonthlyTrend(
            months = months,
            transactions = transactions,
            displayCurrency = displayCurrency,
            zoneId = zoneId,
            fetchRate = { from, to -> exchangeRateProvider.getRate(from, to).rateMicros }
        )
    }

    override suspend fun computeCategoryStats(
        startMillis: Long,
        endMillis: Long,
        type: String,
        displayCurrency: String
    ): List<CategoryStat> {
        val transactions = transactionDao.getBetween(startMillis, endMillis)
        val categories = categoryDao.getAll()
        return LedgerAnalytics.computeCategoryStats(
            transactions = transactions,
            categories = categories,
            startMillis = startMillis,
            endMillis = endMillis,
            type = type,
            displayCurrency = displayCurrency,
            fetchRate = { from, to -> exchangeRateProvider.getRate(from, to).rateMicros }
        )
    }

    override suspend fun computeBudgetProgress(year: Int, month: Int): List<BudgetProgress> {
        val budgets = budgetDao.getAll()
        if (budgets.none { it.enabled }) return emptyList()

        val hasYearly = budgets.any { it.enabled && it.period.equals("YEARLY", ignoreCase = true) }
        val (startMillis, endMillis) = if (hasYearly) {
            LedgerAnalytics.yearRangeMillis(year, zoneId)
        } else {
            LedgerAnalytics.monthRangeMillis(year, month, zoneId)
        }
        val transactions = transactionDao.getBetween(startMillis, endMillis)
        val categories = categoryDao.getAll()

        return LedgerAnalytics.computeBudgetProgress(
            budgets = budgets,
            transactions = transactions,
            categories = categories,
            year = year,
            month = month,
            zoneId = zoneId,
            fetchRate = { from, to -> exchangeRateProvider.getRate(from, to).rateMicros }
        )
    }

    private suspend fun resolveConversion(
        origAmountMicros: Long,
        origCurrency: String,
        accountCurrency: String,
        baseCurrency: String,
        manualAccountAmountMicros: Long?,
        manualRateToAccountMicros: Long?,
        onDate: String?
    ): ConversionPreview {
        val accountAmountMicros: Long
        val rateToAccountMicros: Long
        val accountRateSource: String

        when {
            manualAccountAmountMicros != null -> {
                accountAmountMicros = manualAccountAmountMicros
                rateToAccountMicros = if (origCurrency == accountCurrency &&
                    origAmountMicros == manualAccountAmountMicros
                ) {
                    MoneyAmount.RATE_SCALE
                } else {
                    MoneyAmount.computeRate(origAmountMicros, manualAccountAmountMicros)
                }
                accountRateSource = SOURCE_MANUAL_AMOUNT
            }
            manualRateToAccountMicros != null && manualRateToAccountMicros > 0L -> {
                rateToAccountMicros = manualRateToAccountMicros
                accountAmountMicros = if (rateToAccountMicros == MoneyAmount.RATE_SCALE) {
                    origAmountMicros
                } else {
                    MoneyAmount.convert(origAmountMicros, rateToAccountMicros)
                }
                accountRateSource = SOURCE_MANUAL_RATE
            }
            origCurrency == accountCurrency -> {
                rateToAccountMicros = MoneyAmount.RATE_SCALE
                accountAmountMicros = origAmountMicros
                accountRateSource = ExchangeRateRepositoryImpl.SOURCE_IDENTITY
            }
            else -> {
                val quote = exchangeRateProvider.getRate(origCurrency, accountCurrency, onDate = onDate)
                rateToAccountMicros = quote.rateMicros
                accountAmountMicros = MoneyAmount.convert(origAmountMicros, rateToAccountMicros)
                accountRateSource = quote.source
            }
        }

        val rateToBaseMicros: Long
        val baseAmountMicros: Long
        var finalSource = accountRateSource

        when {
            origCurrency == baseCurrency -> {
                rateToBaseMicros = MoneyAmount.RATE_SCALE
                baseAmountMicros = origAmountMicros
            }
            accountCurrency == baseCurrency -> {
                rateToBaseMicros = rateToAccountMicros
                baseAmountMicros = accountAmountMicros
            }
            else -> {
                val baseQuote = exchangeRateProvider.getRate(origCurrency, baseCurrency, onDate = onDate)
                rateToBaseMicros = baseQuote.rateMicros
                baseAmountMicros = MoneyAmount.convert(origAmountMicros, rateToBaseMicros)
                if (origCurrency == accountCurrency) {
                    finalSource = baseQuote.source
                }
            }
        }

        return ConversionPreview(
            origAmountMicros = origAmountMicros,
            origCurrency = origCurrency,
            accountAmountMicros = accountAmountMicros,
            accountCurrency = accountCurrency,
            rateToAccountMicros = rateToAccountMicros,
            baseAmountMicros = baseAmountMicros,
            baseCurrency = baseCurrency,
            rateToBaseMicros = rateToBaseMicros,
            rateSource = finalSource
        )
    }

    /**
     * direction = +1L 时应用交易对账户余额的影响；direction = -1L 时精确回滚。
     */
    private suspend fun applyBalanceDelta(tx: TransactionEntity, direction: Long, now: Long) {
        when (tx.type) {
            TransactionType.EXPENSE -> {
                accountDao.adjustBalance(tx.accountId, -tx.accountAmountMicros * direction, now)
            }
            TransactionType.INCOME, TransactionType.ADJUST -> {
                accountDao.adjustBalance(tx.accountId, tx.accountAmountMicros * direction, now)
            }
            TransactionType.TRANSFER -> {
                accountDao.adjustBalance(tx.accountId, -tx.accountAmountMicros * direction, now)
                val toId = tx.toAccountId
                val toAmount = tx.toAmountMicros
                if (toId != null && toAmount != null) {
                    accountDao.adjustBalance(toId, toAmount * direction, now)
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private suspend fun <T> runInTransaction(block: suspend () -> T): T =
        inTransaction { block() } as T

    companion object {
        private const val SOURCE_MANUAL_AMOUNT = "MANUAL_AMOUNT"
        private const val SOURCE_MANUAL_RATE = "MANUAL_RATE"
        private const val MAX_RECURRING_CATCHUP_RUNS = 24
    }
}

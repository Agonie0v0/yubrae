package com.kairoledger.app.domain

import com.kairoledger.app.core.contract.BudgetProgress
import com.kairoledger.app.core.contract.CategoryStat
import com.kairoledger.app.core.contract.MonthlyPoint
import com.kairoledger.app.core.contract.NetWorthSummary
import com.kairoledger.app.core.model.CurrencyCatalog
import com.kairoledger.app.core.model.MoneyAmount
import com.kairoledger.app.data.db.AccountEntity
import com.kairoledger.app.data.db.BudgetEntity
import com.kairoledger.app.data.db.CategoryEntity
import com.kairoledger.app.data.db.TransactionEntity
import com.kairoledger.app.data.db.TransactionStatus
import com.kairoledger.app.data.db.TransactionType
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * 账本净资产与多维报表计算引擎：
 * - 所有金额运算均以微元 Long 定点数累加，严禁使用 Float/Double 累加金额
 * - 历史报表严格依据交易冻结的 rateToBaseMicros 与 baseCurrency，防止后续汇率变化引发历史报表漂移
 */
object LedgerAnalytics {

    fun normalizeCurrency(code: String?): String =
        code?.trim()?.uppercase()?.ifEmpty { CurrencyCatalog.DEFAULT_BASE_CURRENCY }
            ?: CurrencyCatalog.DEFAULT_BASE_CURRENCY

    /**
     * 先按交易记录时冻结的 rateToBaseMicros 还原记录时主币种（tx.baseCurrency）金额。
     */
    fun computeFrozenBaseMicros(tx: TransactionEntity): Long {
        val orig = normalizeCurrency(tx.origCurrency)
        val txBase = normalizeCurrency(tx.baseCurrency)
        return if (orig == txBase) {
            tx.origAmountMicros
        } else {
            MoneyAmount.convert(tx.origAmountMicros, tx.rateToBaseMicros)
        }
    }

    /**
     * 先还原记录时冻结主币种金额，仅当 displayCurrency != tx.baseCurrency 时再折算至目标展示币种。
     */
    suspend fun convertFrozenToDisplayMicros(
        tx: TransactionEntity,
        displayCurrency: String,
        rateCache: MutableMap<String, Long>,
        fetchRate: suspend (from: String, to: String) -> Long
    ): Long {
        val frozenBase = computeFrozenBaseMicros(tx)
        val txBase = normalizeCurrency(tx.baseCurrency)
        val target = normalizeCurrency(displayCurrency)
        if (txBase == target) {
            return frozenBase
        }
        val rate = rateCache.getOrPut(txBase) { fetchRate(txBase, target) }
        return MoneyAmount.convert(frozenBase, rate)
    }

    suspend fun computeNetWorth(
        accounts: List<AccountEntity>,
        displayCurrency: String,
        fetchRate: suspend (from: String, to: String) -> Long
    ): NetWorthSummary {
        val targetCurrency = normalizeCurrency(displayCurrency)
        val activeAccounts = accounts.filter { !it.archived }
        val rateCache = HashMap<String, Long>()
        val convertedBalances = LinkedHashMap<Long, Long>(activeAccounts.size)
        var totalAssets = 0L
        var totalLiabilities = 0L

        for (account in activeAccounts) {
            val accCurrency = normalizeCurrency(account.currencyCode)
            val rate = if (accCurrency == targetCurrency) {
                MoneyAmount.RATE_SCALE
            } else {
                rateCache.getOrPut(accCurrency) { fetchRate(accCurrency, targetCurrency) }
            }
            val converted = if (accCurrency == targetCurrency) {
                account.balanceMicros
            } else {
                MoneyAmount.convert(account.balanceMicros, rate)
            }
            convertedBalances[account.id] = converted
            if (account.includeInNetWorth) {
                if (converted >= 0L) {
                    totalAssets += converted
                } else {
                    totalLiabilities += -converted
                }
            }
        }

        return NetWorthSummary(
            displayCurrency = targetCurrency,
            totalAssetsMicros = totalAssets,
            totalLiabilitiesMicros = totalLiabilities,
            netWorthMicros = totalAssets - totalLiabilities,
            accountConvertedBalances = convertedBalances
        )
    }

    fun monthRangeMillis(year: Int, month: Int, zoneId: ZoneId): Pair<Long, Long> {
        val ym = YearMonth.of(year, month.coerceIn(1, 12))
        val start = ym.atDay(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        val end = ym.plusMonths(1).atDay(1).atStartOfDay(zoneId).toInstant().toEpochMilli() - 1L
        return start to end
    }

    fun yearRangeMillis(year: Int, zoneId: ZoneId): Pair<Long, Long> {
        val start = LocalDate.of(year, 1, 1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        val end = LocalDate.of(year + 1, 1, 1).atStartOfDay(zoneId).toInstant().toEpochMilli() - 1L
        return start to end
    }

    fun buildMonthBuckets(monthsBack: Int, nowMillis: Long, zoneId: ZoneId): List<YearMonth> {
        val count = monthsBack.coerceAtLeast(1)
        val currentYm = YearMonth.from(Instant.ofEpochMilli(nowMillis).atZone(zoneId))
        return (count - 1 downTo 0).map { offset ->
            currentYm.minusMonths(offset.toLong())
        }
    }

    suspend fun computeMonthlyTrend(
        months: List<YearMonth>,
        transactions: List<TransactionEntity>,
        displayCurrency: String,
        zoneId: ZoneId,
        fetchRate: suspend (from: String, to: String) -> Long
    ): List<MonthlyPoint> {
        if (months.isEmpty()) return emptyList()
        val targetCurrency = normalizeCurrency(displayCurrency)
        val expenseByMonth = HashMap<YearMonth, Long>(months.size)
        val incomeByMonth = HashMap<YearMonth, Long>(months.size)
        val monthSet = months.toHashSet()
        val rateCache = HashMap<String, Long>()

        for (tx in transactions) {
            if (tx.status != TransactionStatus.CONFIRMED) continue
            if (tx.type != TransactionType.EXPENSE && tx.type != TransactionType.INCOME) continue
            val txYm = YearMonth.from(Instant.ofEpochMilli(tx.timestamp).atZone(zoneId))
            if (txYm !in monthSet) continue

            val amount = convertFrozenToDisplayMicros(tx, targetCurrency, rateCache, fetchRate)
            when (tx.type) {
                TransactionType.EXPENSE -> {
                    expenseByMonth[txYm] = (expenseByMonth[txYm] ?: 0L) + amount
                }
                TransactionType.INCOME -> {
                    incomeByMonth[txYm] = (incomeByMonth[txYm] ?: 0L) + amount
                }
            }
        }

        return months.map { ym ->
            MonthlyPoint(
                monthLabel = "${ym.monthValue}月",
                year = ym.year,
                month = ym.monthValue,
                expenseBaseMicros = expenseByMonth[ym] ?: 0L,
                incomeBaseMicros = incomeByMonth[ym] ?: 0L
            )
        }
    }

    suspend fun computeCategoryStats(
        transactions: List<TransactionEntity>,
        categories: List<CategoryEntity>,
        startMillis: Long,
        endMillis: Long,
        type: String,
        displayCurrency: String,
        fetchRate: suspend (from: String, to: String) -> Long
    ): List<CategoryStat> {
        val targetCurrency = normalizeCurrency(displayCurrency)
        val categoriesById = categories.associateBy { it.id }
        val rateCache = HashMap<String, Long>()
        val totalsByCategory = LinkedHashMap<Long?, Long>()
        val countsByCategory = LinkedHashMap<Long?, Int>()
        var grandTotal = 0L

        for (tx in transactions) {
            if (tx.status != TransactionStatus.CONFIRMED) continue
            if (tx.type != type) continue
            if (tx.timestamp !in startMillis..endMillis) continue

            val amount = convertFrozenToDisplayMicros(tx, targetCurrency, rateCache, fetchRate)
            totalsByCategory[tx.categoryId] = (totalsByCategory[tx.categoryId] ?: 0L) + amount
            countsByCategory[tx.categoryId] = (countsByCategory[tx.categoryId] ?: 0) + 1
            grandTotal += amount
        }

        if (totalsByCategory.isEmpty()) return emptyList()

        return totalsByCategory.entries
            .sortedByDescending { it.value }
            .map { (catId, totalMicros) ->
                val category = catId?.let { categoriesById[it] }
                val ratio = if (grandTotal > 0L) {
                    BigDecimal(totalMicros)
                        .divide(BigDecimal(grandTotal), 6, RoundingMode.HALF_UP)
                        .toFloat()
                } else {
                    0f
                }
                CategoryStat(
                    categoryId = catId,
                    categoryName = category?.name ?: "未分类",
                    colorHex = category?.colorHex ?: 0xFF64748BL,
                    icon = category?.icon ?: "category",
                    totalBaseMicros = totalMicros,
                    count = countsByCategory[catId] ?: 0,
                    percentage = ratio
                )
            }
    }

    suspend fun computeBudgetProgress(
        budgets: List<BudgetEntity>,
        transactions: List<TransactionEntity>,
        categories: List<CategoryEntity>,
        year: Int,
        month: Int,
        zoneId: ZoneId,
        fetchRate: suspend (from: String, to: String) -> Long
    ): List<BudgetProgress> {
        val enabledBudgets = budgets.filter { it.enabled }
        if (enabledBudgets.isEmpty()) return emptyList()

        val categoriesById = categories.associateBy { it.id }
        val childrenByParent = categories
            .filter { it.parentId != null }
            .groupBy { it.parentId!! }
            .mapValues { (_, list) -> list.map { it.id }.toHashSet() }

        val monthRange = monthRangeMillis(year, month, zoneId)
        val yearRange = yearRangeMillis(year, zoneId)
        val rateCachesByBudgetCurrency = HashMap<String, MutableMap<String, Long>>()

        return enabledBudgets.map { budget ->
            val budgetCurrency = normalizeCurrency(budget.currencyCode)
            val rateCache = rateCachesByBudgetCurrency.getOrPut(budgetCurrency) { HashMap() }
            val (startMillis, endMillis) = if (budget.period.equals("YEARLY", ignoreCase = true)) {
                yearRange
            } else {
                monthRange
            }

            val targetCategoryId = budget.categoryId
            val childIds = targetCategoryId?.let { childrenByParent[it] }.orEmpty()

            var spent = 0L
            for (tx in transactions) {
                if (tx.status != TransactionStatus.CONFIRMED) continue
                if (tx.type != TransactionType.EXPENSE) continue
                if (tx.timestamp !in startMillis..endMillis) continue
                if (targetCategoryId != null &&
                    tx.categoryId != targetCategoryId &&
                    tx.categoryId !in childIds
                ) {
                    continue
                }
                spent += convertFrozenToDisplayMicros(tx, budgetCurrency, rateCache, fetchRate)
            }

            val limit = budget.limitAmountMicros
            val ratio = if (limit > 0L) {
                BigDecimal(spent)
                    .divide(BigDecimal(limit), 6, RoundingMode.HALF_UP)
                    .toFloat()
            } else {
                0f
            }
            val categoryName = if (targetCategoryId == null) {
                "总预算"
            } else {
                categoriesById[targetCategoryId]?.name ?: budget.name.ifBlank { "分类预算" }
            }

            BudgetProgress(
                budget = budget,
                categoryName = categoryName,
                spentInBudgetCurrencyMicros = spent,
                limitInBudgetCurrencyMicros = limit,
                progressRatio = ratio,
                overBudget = limit > 0L && spent > limit
            )
        }
    }
}

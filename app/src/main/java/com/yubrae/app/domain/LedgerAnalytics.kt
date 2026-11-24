package com.yubrae.app.domain

import com.yubrae.app.core.contract.BudgetProgress
import com.yubrae.app.core.contract.CategoryStat
import com.yubrae.app.core.contract.MonthlyPoint
import com.yubrae.app.core.contract.NetWorthPoint
import com.yubrae.app.core.contract.NetWorthSummary
import com.yubrae.app.core.contract.TrendBucketPoint
import com.yubrae.app.core.model.CurrencyCatalog
import com.yubrae.app.core.model.MoneyAmount
import com.yubrae.app.data.db.AccountEntity
import com.yubrae.app.data.db.BudgetEntity
import com.yubrae.app.data.db.CategoryEntity
import com.yubrae.app.data.db.StatsGranularity
import com.yubrae.app.data.db.TransactionEntity
import com.yubrae.app.data.db.TransactionStatus
import com.yubrae.app.data.db.TransactionType
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

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
        ledgerId: Long = 0L,
        fetchRate: suspend (from: String, to: String) -> Long
    ): List<MonthlyPoint> {
        if (months.isEmpty()) return emptyList()
        val targetCurrency = normalizeCurrency(displayCurrency)
        val expenseByMonth = HashMap<YearMonth, Long>(months.size)
        val incomeByMonth = HashMap<YearMonth, Long>(months.size)
        val monthSet = months.toHashSet()
        val rateCache = HashMap<String, Long>()
        val txById = transactions.associateBy { it.id }

        for (tx in transactions) {
            if (tx.status != TransactionStatus.CONFIRMED || tx.excludeFromStats) continue
            if (ledgerId > 0L && tx.ledgerId != ledgerId) continue

            if (tx.type == TransactionType.INCOME && tx.refundOfTxId != null) {
                val origTx = txById[tx.refundOfTxId]
                val targetTime = origTx?.timestamp ?: tx.timestamp
                val refundYm = YearMonth.from(Instant.ofEpochMilli(targetTime).atZone(zoneId))
                if (refundYm in monthSet) {
                    val refundAmt = convertFrozenToDisplayMicros(tx, targetCurrency, rateCache, fetchRate)
                    expenseByMonth[refundYm] = (expenseByMonth[refundYm] ?: 0L) - refundAmt
                }
                continue
            }

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
                expenseBaseMicros = (expenseByMonth[ym] ?: 0L).coerceAtLeast(0L),
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
        ledgerId: Long = 0L,
        fetchRate: suspend (from: String, to: String) -> Long
    ): List<CategoryStat> {
        val targetCurrency = normalizeCurrency(displayCurrency)
        val categoriesById = categories.associateBy { it.id }
        val txById = transactions.associateBy { it.id }
        val rateCache = HashMap<String, Long>()
        val totalsByCategory = LinkedHashMap<Long?, Long>()
        val countsByCategory = LinkedHashMap<Long?, Int>()

        for (tx in transactions) {
            if (tx.status != TransactionStatus.CONFIRMED || tx.excludeFromStats) continue
            if (ledgerId > 0L && tx.ledgerId != ledgerId) continue

            if (tx.type == TransactionType.INCOME && tx.refundOfTxId != null) {
                if (type == TransactionType.EXPENSE) {
                    val origTx = txById[tx.refundOfTxId]
                    val targetCatId = origTx?.categoryId ?: tx.categoryId
                    val targetTime = origTx?.timestamp ?: tx.timestamp
                    if (targetTime in startMillis..endMillis) {
                        val refundAmt = convertFrozenToDisplayMicros(tx, targetCurrency, rateCache, fetchRate)
                        totalsByCategory[targetCatId] = (totalsByCategory[targetCatId] ?: 0L) - refundAmt
                    }
                }
                continue
            }

            if (tx.type != type) continue
            if (tx.timestamp !in startMillis..endMillis) continue

            val amount = convertFrozenToDisplayMicros(tx, targetCurrency, rateCache, fetchRate)
            totalsByCategory[tx.categoryId] = (totalsByCategory[tx.categoryId] ?: 0L) + amount
            countsByCategory[tx.categoryId] = (countsByCategory[tx.categoryId] ?: 0) + 1
        }

        val netEntries = totalsByCategory.entries
            .mapNotNull { (catId, rawTotal) ->
                val clamped = rawTotal.coerceAtLeast(0L)
                val count = countsByCategory[catId] ?: 0
                if (clamped > 0L || count > 0) Triple(catId, clamped, count) else null
            }
            .filter { it.second > 0L }

        if (netEntries.isEmpty()) return emptyList()
        val grandTotal = netEntries.sumOf { it.second }

        return netEntries
            .sortedByDescending { it.second }
            .map { (catId, totalMicros, count) ->
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
                    count = count,
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
        ledgerId: Long = 0L,
        nowMillis: Long = System.currentTimeMillis(),
        fetchRate: suspend (from: String, to: String) -> Long
    ): List<BudgetProgress> {
        val enabledBudgets = budgets.filter { b ->
            b.enabled && (ledgerId <= 0L || b.ledgerId == ledgerId)
        }
        if (enabledBudgets.isEmpty()) return emptyList()

        val categoriesById = categories.associateBy { it.id }
        val childrenByParent = categories
            .filter { it.parentId != null }
            .groupBy { it.parentId!! }
            .mapValues { (_, list) -> list.map { it.id }.toHashSet() }

        val targetYm = YearMonth.of(year, month.coerceIn(1, 12))
        val prevYm = targetYm.minusMonths(1)
        val monthRange = monthRangeMillis(targetYm.year, targetYm.monthValue, zoneId)
        val prevMonthRange = monthRangeMillis(prevYm.year, prevYm.monthValue, zoneId)
        val yearRange = yearRangeMillis(targetYm.year, zoneId)
        val rateCachesByBudgetCurrency = HashMap<String, MutableMap<String, Long>>()
        val txById = transactions.associateBy { it.id }

        val today = Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate()
        val currentYm = YearMonth.from(today)
        val remainingDays = when {
            targetYm == currentYm -> (targetYm.lengthOfMonth() - today.dayOfMonth + 1).coerceAtLeast(1)
            targetYm.isAfter(currentYm) -> targetYm.lengthOfMonth()
            else -> 0
        }

        suspend fun computeSpentInRange(
            budget: BudgetEntity,
            budgetCurrency: String,
            rateCache: MutableMap<String, Long>,
            range: Pair<Long, Long>,
            childIds: Set<Long>
        ): Long {
            val targetCategoryId = budget.categoryId
            var spent = 0L
            for (tx in transactions) {
                if (tx.status != TransactionStatus.CONFIRMED || tx.excludeFromBudget || tx.excludeFromStats) continue
                if (ledgerId > 0L && tx.ledgerId != ledgerId) continue
                if (budget.ledgerId > 0L && ledgerId <= 0L && tx.ledgerId != budget.ledgerId) continue

                if (tx.type == TransactionType.INCOME && tx.refundOfTxId != null) {
                    val origTx = txById[tx.refundOfTxId]
                    val matchCategory = targetCategoryId == null ||
                        origTx?.categoryId == targetCategoryId ||
                        origTx?.categoryId in childIds
                    val targetTime = origTx?.timestamp ?: tx.timestamp
                    if (matchCategory && targetTime in range.first..range.second) {
                        spent -= convertFrozenToDisplayMicros(tx, budgetCurrency, rateCache, fetchRate)
                    }
                    continue
                }

                if (tx.type != TransactionType.EXPENSE) continue
                if (tx.timestamp !in range.first..range.second) continue
                if (targetCategoryId != null &&
                    tx.categoryId != targetCategoryId &&
                    tx.categoryId !in childIds
                ) {
                    continue
                }
                spent += convertFrozenToDisplayMicros(tx, budgetCurrency, rateCache, fetchRate)
            }
            return spent.coerceAtLeast(0L)
        }

        return enabledBudgets.map { budget ->
            val budgetCurrency = normalizeCurrency(budget.currencyCode)
            val rateCache = rateCachesByBudgetCurrency.getOrPut(budgetCurrency) { HashMap() }
            val isYearly = budget.period.equals("YEARLY", ignoreCase = true)
            val currentRange = if (isYearly) yearRange else monthRange
            val targetCategoryId = budget.categoryId
            val childIds = targetCategoryId?.let { childrenByParent[it] }.orEmpty()

            val spent = computeSpentInRange(budget, budgetCurrency, rateCache, currentRange, childIds)
            val rolloverMicros = if (!isYearly && budget.rolloverEnabled) {
                val prevSpent = computeSpentInRange(budget, budgetCurrency, rateCache, prevMonthRange, childIds)
                (budget.limitAmountMicros - prevSpent).coerceAtLeast(0L)
            } else {
                0L
            }

            val effectiveLimit = budget.limitAmountMicros + rolloverMicros
            val ratio = if (effectiveLimit > 0L) {
                BigDecimal(spent)
                    .divide(BigDecimal(effectiveLimit), 6, RoundingMode.HALF_UP)
                    .toFloat()
            } else {
                0f
            }
            val remainingMicros = (effectiveLimit - spent).coerceAtLeast(0L)
            val dailyAvailableMicros = if (remainingDays > 0 && remainingMicros > 0L) {
                remainingMicros / remainingDays.toLong()
            } else {
                0L
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
                limitInBudgetCurrencyMicros = effectiveLimit,
                progressRatio = ratio,
                overBudget = effectiveLimit > 0L && spent > effectiveLimit,
                rolloverMicros = rolloverMicros,
                dailyAvailableMicros = dailyAvailableMicros,
                remainingDays = remainingDays
            )
        }
    }

    /**
     * 基于当前账户余额按交易流水反向回推历史各月末净资产，输出时间序列折线点。
     */
    suspend fun computeNetWorthHistory(
        accounts: List<AccountEntity>,
        transactions: List<TransactionEntity>,
        monthsBack: Int,
        displayCurrency: String,
        zoneId: ZoneId,
        nowMillis: Long,
        fetchRate: suspend (from: String, to: String) -> Long
    ): List<NetWorthPoint> {
        val targetCurrency = normalizeCurrency(displayCurrency)
        val activeNetWorthAccounts = accounts.filter { !it.archived && it.includeInNetWorth }
        val accountMap = activeNetWorthAccounts.associateBy { it.id }
        val months = buildMonthBuckets(monthsBack, nowMillis, zoneId)
        val rateCache = HashMap<String, Long>()
        suspend fun cachedRate(from: String): Long {
            val f = normalizeCurrency(from)
            return if (f == targetCurrency) MoneyAmount.RATE_SCALE else rateCache.getOrPut(f) { fetchRate(f, targetCurrency) }
        }

        val currentBalancesById = activeNetWorthAccounts.associate { it.id to it.balanceMicros }.toMutableMap()
        val sortedDescTxs = transactions
            .filter { it.status == TransactionStatus.CONFIRMED }
            .sortedByDescending { it.timestamp }

        // 先构造每月结束时刻（当前月使用 nowMillis），按月由近及远回推余额
        val monthEnds = months.mapIndexed { idx, ym ->
            val endMillis = if (idx == months.lastIndex) {
                nowMillis
            } else {
                monthRangeMillis(ym.year, ym.monthValue, zoneId).second
            }
            ym to endMillis
        }

        val pointsReversed = mutableListOf<NetWorthPoint>()
        var txIdx = 0
        for ((ym, endMillis) in monthEnds.asReversed()) {
            while (txIdx < sortedDescTxs.size && sortedDescTxs[txIdx].timestamp > endMillis) {
                val tx = sortedDescTxs[txIdx]
                // 反向回滚交易对该时刻后账户余额产生的影响
                when (tx.type) {
                    TransactionType.EXPENSE -> {
                        if (tx.accountId in accountMap) {
                            currentBalancesById[tx.accountId] =
                                (currentBalancesById[tx.accountId] ?: 0L) + tx.accountAmountMicros
                        }
                    }
                    TransactionType.INCOME, TransactionType.ADJUST -> {
                        if (tx.accountId in accountMap) {
                            currentBalancesById[tx.accountId] =
                                (currentBalancesById[tx.accountId] ?: 0L) - tx.accountAmountMicros
                        }
                    }
                    TransactionType.TRANSFER -> {
                        if (tx.accountId in accountMap) {
                            currentBalancesById[tx.accountId] =
                                (currentBalancesById[tx.accountId] ?: 0L) + tx.accountAmountMicros
                        }
                        val toId = tx.toAccountId
                        val toAmt = tx.toAmountMicros
                        if (toId != null && toAmt != null && toId in accountMap) {
                            currentBalancesById[toId] =
                                (currentBalancesById[toId] ?: 0L) - toAmt
                        }
                    }
                }
                txIdx++
            }

            var netWorth = 0L
            for ((accId, rawBal) in currentBalancesById) {
                val accCur = accountMap[accId]?.currencyCode ?: targetCurrency
                val r = cachedRate(accCur)
                netWorth += MoneyAmount.convert(rawBal, r)
            }
            pointsReversed.add(
                NetWorthPoint(
                    label = "${ym.monthValue}月",
                    epochMillis = endMillis,
                    netWorthMicros = netWorth
                )
            )
        }
        return pointsReversed.asReversed()
    }

    /**
     * 生成周 / 月 / 年 / 自定义区间的收支趋势分桶，用于图表展示与交互下钻。
     */
    suspend fun computeTrendBuckets(
        granularity: String,
        anchorDateMillis: Long,
        customStartMillis: Long?,
        customEndMillis: Long?,
        ledgerId: Long,
        transactions: List<TransactionEntity>,
        displayCurrency: String,
        zoneId: ZoneId,
        fetchRate: suspend (from: String, to: String) -> Long
    ): List<TrendBucketPoint> {
        val anchorDate = Instant.ofEpochMilli(anchorDateMillis).atZone(zoneId).toLocalDate()
        val buckets = mutableListOf<Triple<String, Long, Long>>()

        when (granularity) {
            StatsGranularity.WEEKLY -> {
                val monday = anchorDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                val weekLabels = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
                for (i in 0 until 7) {
                    val day = monday.plusDays(i.toLong())
                    val s = day.atStartOfDay(zoneId).toInstant().toEpochMilli()
                    val e = day.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli() - 1L
                    buckets.add(Triple(weekLabels[i], s, e))
                }
            }
            StatsGranularity.YEARLY -> {
                for (m in 1..12) {
                    val (s, e) = monthRangeMillis(anchorDate.year, m, zoneId)
                    buckets.add(Triple("${m}月", s, e))
                }
            }
            StatsGranularity.CUSTOM -> {
                val startDate = Instant.ofEpochMilli(customStartMillis ?: anchorDateMillis).atZone(zoneId).toLocalDate()
                val endDate = Instant.ofEpochMilli(customEndMillis ?: anchorDateMillis).atZone(zoneId).toLocalDate()
                val safeStart = if (startDate.isAfter(endDate)) endDate else startDate
                val safeEnd = if (startDate.isAfter(endDate)) startDate else endDate
                val days = ChronoUnit.DAYS.between(safeStart, safeEnd).toInt() + 1
                if (days <= 31) {
                    for (i in 0 until days) {
                        val d = safeStart.plusDays(i.toLong())
                        val s = d.atStartOfDay(zoneId).toInstant().toEpochMilli()
                        val e = d.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli() - 1L
                        buckets.add(Triple("${d.monthValue}/${d.dayOfMonth}", s, e))
                    }
                } else {
                    var ym = YearMonth.from(safeStart)
                    val endYm = YearMonth.from(safeEnd)
                    while (!ym.isAfter(endYm)) {
                        val (s, e) = monthRangeMillis(ym.year, ym.monthValue, zoneId)
                        buckets.add(Triple("${ym.monthValue}月", s, e))
                        ym = ym.plusMonths(1)
                    }
                }
            }
            else -> {
                // MONTHLY: 展示锚点月及此前共 6 个月趋势
                val anchorYm = YearMonth.from(anchorDate)
                for (offset in 5 downTo 0) {
                    val ym = anchorYm.minusMonths(offset.toLong())
                    val (s, e) = monthRangeMillis(ym.year, ym.monthValue, zoneId)
                    buckets.add(Triple("${ym.monthValue}月", s, e))
                }
            }
        }

        val targetCurrency = normalizeCurrency(displayCurrency)
        val rateCache = HashMap<String, Long>()
        val txById = transactions.associateBy { it.id }

        return buckets.map { (label, startMs, endMs) ->
            var exp = 0L
            var inc = 0L
            for (tx in transactions) {
                if (tx.status != TransactionStatus.CONFIRMED || tx.excludeFromStats) continue
                if (ledgerId > 0L && tx.ledgerId != ledgerId) continue

                if (tx.type == TransactionType.INCOME && tx.refundOfTxId != null) {
                    val origTx = txById[tx.refundOfTxId]
                    val targetTime = origTx?.timestamp ?: tx.timestamp
                    if (targetTime in startMs..endMs) {
                        exp -= convertFrozenToDisplayMicros(tx, targetCurrency, rateCache, fetchRate)
                    }
                    continue
                }

                if (tx.timestamp !in startMs..endMs) continue
                if (tx.type == TransactionType.EXPENSE) {
                    exp += convertFrozenToDisplayMicros(tx, targetCurrency, rateCache, fetchRate)
                } else if (tx.type == TransactionType.INCOME) {
                    inc += convertFrozenToDisplayMicros(tx, targetCurrency, rateCache, fetchRate)
                }
            }
            TrendBucketPoint(
                label = label,
                startMillis = startMs,
                endMillis = endMs,
                expenseBaseMicros = exp.coerceAtLeast(0L),
                incomeBaseMicros = inc
            )
        }
    }
}

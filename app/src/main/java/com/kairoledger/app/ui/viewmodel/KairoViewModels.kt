package com.kairoledger.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kairoledger.app.AppContainer
import com.kairoledger.app.capture.LlmBillParser
import com.kairoledger.app.capture.SmartInputParser
import com.kairoledger.app.core.contract.BudgetProgress
import com.kairoledger.app.core.contract.CategoryStat
import com.kairoledger.app.core.contract.ConversionPreview
import com.kairoledger.app.core.contract.DevicePermissionReport
import com.kairoledger.app.core.contract.ImportPreview
import com.kairoledger.app.core.contract.MonthlyPoint
import com.kairoledger.app.core.contract.NetWorthPoint
import com.kairoledger.app.core.contract.NetWorthSummary
import com.kairoledger.app.core.contract.ParsedBillDraft
import com.kairoledger.app.core.contract.RateQuote
import com.kairoledger.app.core.contract.RecordTransactionRequest
import com.kairoledger.app.core.contract.RemoteBackupFile
import com.kairoledger.app.core.contract.TagStat
import com.kairoledger.app.core.contract.TrendBucketPoint
import com.kairoledger.app.core.model.CurrencyCatalog
import com.kairoledger.app.core.model.MoneyAmount
import com.kairoledger.app.data.db.AccountAliasEntity
import com.kairoledger.app.data.db.AccountEntity
import com.kairoledger.app.data.db.BudgetEntity
import com.kairoledger.app.data.db.CaptureChannel
import com.kairoledger.app.data.db.CategoryEntity
import com.kairoledger.app.data.db.DebtRecordEntity
import com.kairoledger.app.data.db.DebtStatus
import com.kairoledger.app.data.db.ExchangeRateEntity
import com.kairoledger.app.data.db.InstallmentPlanEntity
import com.kairoledger.app.data.db.LedgerEntity
import com.kairoledger.app.data.db.ParseRuleEntity
import com.kairoledger.app.data.db.RawCaptureLogEntity
import com.kairoledger.app.data.db.RecurringRuleEntity
import com.kairoledger.app.data.db.ReimburseStatus
import com.kairoledger.app.data.db.StatsGranularity
import com.kairoledger.app.data.db.TagEntity
import com.kairoledger.app.data.db.TagTextCodec
import com.kairoledger.app.data.db.TransactionEntity
import com.kairoledger.app.data.db.TransactionStatus
import com.kairoledger.app.data.db.TransactionType
import com.kairoledger.app.data.prefs.AppSettings
import com.kairoledger.app.domain.LedgerAnalytics
import com.kairoledger.app.ui.components.KairoIcons
import java.io.File
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.DayOfWeek
import java.time.temporal.TemporalAdjusters
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlin.math.abs

data class TransactionFilters(
    val yearMonth: YearMonth = YearMonth.now(),
    val query: String = "",
    val currencyFilter: String = "ALL",
    val accountIdFilter: Long? = null,
    val channelFilter: String = "ALL",
    val pendingExpanded: Boolean = false,
    val categoryIdFilter: Long? = null,
    val reimburseFilter: String = "ALL",
    val minAmountMicros: Long? = null,
    val maxAmountMicros: Long? = null,
    val tagFilter: String? = null
)

data class DayTransactionGroup(
    val date: LocalDate,
    val transactions: List<TransactionEntity>,
    val dayExpenseBaseMicros: Long,
    val dayIncomeBaseMicros: Long
)

data class TransactionsUiState(
    val filters: TransactionFilters = TransactionFilters(),
    val baseCurrency: String = CurrencyCatalog.DEFAULT_BASE_CURRENCY,
    val monthExpenseBaseMicros: Long = 0L,
    val monthIncomeBaseMicros: Long = 0L,
    val monthBalanceBaseMicros: Long = 0L,
    val groupedDays: List<DayTransactionGroup> = emptyList(),
    val pendingTransactions: List<TransactionEntity> = emptyList(),
    val pendingCount: Int = 0,
    val baseAmountByTxId: Map<Long, Long> = emptyMap()
)

data class AssetsUiState(
    val displayCurrency: String = CurrencyCatalog.DEFAULT_BASE_CURRENCY,
    val showArchived: Boolean = false,
    val netWorth: NetWorthSummary = NetWorthSummary(
        displayCurrency = CurrencyCatalog.DEFAULT_BASE_CURRENCY,
        totalAssetsMicros = 0L,
        totalLiabilitiesMicros = 0L,
        netWorthMicros = 0L,
        accountConvertedBalances = emptyMap()
    ),
    val groupedAccounts: Map<String, List<AccountEntity>> = emptyMap(),
    val isLoading: Boolean = false
)

data class StatsUiState(
    val selectedMonth: YearMonth = YearMonth.now(),
    val displayCurrency: String = CurrencyCatalog.DEFAULT_BASE_CURRENCY,
    val selectedType: String = TransactionType.EXPENSE,
    val granularity: String = StatsGranularity.MONTHLY,
    val anchorDateMillis: Long = System.currentTimeMillis(),
    val customStartMillis: Long? = null,
    val customEndMillis: Long? = null,
    val monthlyTrend: List<MonthlyPoint> = emptyList(),
    val trendBuckets: List<TrendBucketPoint> = emptyList(),
    val netWorthHistory: List<NetWorthPoint> = emptyList(),
    val categoryStats: List<CategoryStat> = emptyList(),
    val tagStats: List<TagStat> = emptyList(),
    val budgetProgressList: List<BudgetProgress> = emptyList(),
    val totalCategoryAmountMicros: Long = 0L,
    val isLoading: Boolean = false
)

private data class StatsInputSnapshot(
    val txs: List<TransactionEntity>,
    val tags: List<TagEntity>,
    val budgets: List<BudgetEntity>,
    val rates: List<ExchangeRateEntity>
)

private data class StatsQueryParams(
    val month: YearMonth,
    val displayCurrency: String,
    val type: String,
    val granularity: String,
    val anchorDateMillis: Long,
    val customStartMillis: Long?,
    val customEndMillis: Long?,
    val activeLedgerId: Long
)

data class SandboxTestUiState(
    val isRunning: Boolean = false,
    val hasResult: Boolean = false,
    val draft: ParsedBillDraft? = null,
    val errorMessage: String? = null
)

data class CsvImportDialogState(
    val fileName: String = "",
    val bytes: ByteArray? = null,
    val preview: ImportPreview? = null,
    val isProcessing: Boolean = false,
    val error: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class KairoMainViewModel(
    private val container: AppContainer
) : ViewModel() {

    private val ledgerService = container.requireLedgerService()
    private val rateProvider = container.requireExchangeRateProvider()
    private val captureEngine = container.requireCaptureEngine()
    private val billImporter = container.requireBillImporter()
    private val cloudSyncService = container.requireCloudSyncService()
    private val settingsStore = container.settingsStore
    private val llmBillParser = LlmBillParser(container.okHttpClient)
    private val smartInputParser = SmartInputParser(llmBillParser = llmBillParser)

    private val _snackbarEvents = MutableSharedFlow<String>(extraBufferCapacity = 24)
    val snackbarEvents: SharedFlow<String> = _snackbarEvents.asSharedFlow()

    val settings: StateFlow<AppSettings> = settingsStore.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    val ledgers: StateFlow<List<LedgerEntity>> = ledgerService.observeLedgers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val debts: StateFlow<List<DebtRecordEntity>> = ledgerService.observeDebts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val installments: StateFlow<List<InstallmentPlanEntity>> = ledgerService.observeInstallments()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val pendingReimburseCount: StateFlow<Int> = ledgerService.observeTransactions()
        .map { txs -> txs.count { it.reimburseStatus == ReimburseStatus.PENDING } }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val openDebtCount: StateFlow<Int> = debts
        .map { list -> list.count { it.status == DebtStatus.OPEN } }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val activeAccounts: StateFlow<List<AccountEntity>> = ledgerService.observeActiveAccounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val allAccounts: StateFlow<List<AccountEntity>> = ledgerService.observeAllAccounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val categories: StateFlow<List<CategoryEntity>> = ledgerService.observeCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val tags: StateFlow<List<TagEntity>> = ledgerService.observeTags()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val allTransactions: StateFlow<List<TransactionEntity>> = ledgerService.observeTransactions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val pendingTransactions: StateFlow<List<TransactionEntity>> = ledgerService.observePendingTransactions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val pendingCount: StateFlow<Int> = ledgerService.observePendingCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val exchangeRates: StateFlow<List<ExchangeRateEntity>> = rateProvider.observeRates()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val accountAliases: StateFlow<List<AccountAliasEntity>> = captureEngine.observeAccountAliases()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val parseRules: StateFlow<List<ParseRuleEntity>> = captureEngine.observeParseRules()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val captureLogs: StateFlow<List<RawCaptureLogEntity>> = captureEngine.observeCaptureLogs(200)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val budgets: StateFlow<List<BudgetEntity>> = ledgerService.observeBudgets()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val recurringRules: StateFlow<List<RecurringRuleEntity>> = ledgerService.observeRecurringRules()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // Tab 1: Transactions filters & derived state
    private val _txFilters = MutableStateFlow(TransactionFilters())

    val transactionsUiState: StateFlow<TransactionsUiState> = combine(
        allTransactions,
        pendingTransactions,
        categories,
        exchangeRates,
        combine(_txFilters, settings) { filters, appSettings -> filters to appSettings }
    ) { transactions, pending, allCategories, rates, (filters, appSettings) ->
        val base = appSettings.baseCurrency
        val zone = ZoneId.systemDefault()
        val monthStart = filters.yearMonth.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val monthEnd = filters.yearMonth.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1L
        val ledgerFilterId = appSettings.activeLedgerId

        val childrenByParent = HashMap<Long, MutableSet<Long>>()
        for (cat in allCategories) {
            val pid = cat.parentId ?: continue
            childrenByParent.getOrPut(pid) { HashSet() }.add(cat.id)
        }
        val targetCatIds = filters.categoryIdFilter?.let { filterId ->
            HashSet<Long>().apply {
                add(filterId)
                childrenByParent[filterId]?.let { addAll(it) }
            }
        }

        val baseAmountByTxId = HashMap<Long, Long>(64)
        for (tx in pending) {
            baseAmountByTxId[tx.id] = convertTxToDisplayBase(tx, base, rates)
        }

        val inMonthLedger = transactions.filter { tx ->
            tx.timestamp in monthStart..monthEnd && (ledgerFilterId <= 0L || tx.ledgerId == ledgerFilterId)
        }
        var monthExpense = 0L
        var monthIncome = 0L
        for (tx in inMonthLedger) {
            val baseMicros = baseAmountByTxId.getOrPut(tx.id) {
                convertTxToDisplayBase(tx, base, rates)
            }
            if (tx.status == TransactionStatus.CONFIRMED && !tx.excludeFromStats) {
                when (tx.type) {
                    TransactionType.EXPENSE -> monthExpense += baseMicros
                    TransactionType.INCOME -> {
                        if (tx.refundOfTxId != null) {
                            monthExpense -= baseMicros
                        } else {
                            monthIncome += baseMicros
                        }
                    }
                }
            }
        }
        monthExpense = monthExpense.coerceAtLeast(0L)

        val queryLower = filters.query.trim().lowercase()
        val filtered = inMonthLedger.filter { tx ->
            val matchCurrency = filters.currencyFilter == "ALL" ||
                tx.origCurrency.equals(filters.currencyFilter, ignoreCase = true) ||
                tx.accountCurrency.equals(filters.currencyFilter, ignoreCase = true)
            val matchAccount = filters.accountIdFilter == null ||
                tx.accountId == filters.accountIdFilter ||
                tx.toAccountId == filters.accountIdFilter
            val matchChannel = filters.channelFilter == "ALL" || tx.sourceType == filters.channelFilter
            val matchCat = targetCatIds == null || tx.categoryId in targetCatIds
            val matchReimburse = filters.reimburseFilter == "ALL" || tx.reimburseStatus == filters.reimburseFilter
            val matchAmount = if (filters.minAmountMicros != null || filters.maxAmountMicros != null) {
                val baseAmt = abs(baseAmountByTxId[tx.id] ?: convertTxToDisplayBase(tx, base, rates))
                (filters.minAmountMicros == null || baseAmt >= filters.minAmountMicros) &&
                    (filters.maxAmountMicros == null || baseAmt <= filters.maxAmountMicros)
            } else {
                true
            }
            val matchQuery = queryLower.isEmpty() ||
                tx.merchant.lowercase().contains(queryLower) ||
                tx.note.lowercase().contains(queryLower) ||
                tx.tags.lowercase().contains(queryLower)
            val matchTag = filters.tagFilter == null || filters.tagFilter in TagTextCodec.parse(tx.tags)
            matchCurrency && matchAccount && matchChannel && matchCat && matchReimburse && matchAmount && matchQuery && matchTag
        }

        val groupedDays = filtered
            .groupBy { Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate() }
            .entries
            .sortedByDescending { it.key }
            .map { (date, dayTxs) ->
                var dayExp = 0L
                var dayInc = 0L
                dayTxs.forEach { tx ->
                    if (tx.status == TransactionStatus.CONFIRMED && !tx.excludeFromStats) {
                        val amt = baseAmountByTxId[tx.id] ?: convertTxToDisplayBase(tx, base, rates)
                        when {
                            tx.type == TransactionType.EXPENSE -> dayExp += amt
                            tx.type == TransactionType.INCOME && tx.refundOfTxId != null -> dayExp -= amt
                            tx.type == TransactionType.INCOME -> dayInc += amt
                        }
                    }
                }
                DayTransactionGroup(
                    date = date,
                    transactions = dayTxs.sortedByDescending { it.timestamp },
                    dayExpenseBaseMicros = dayExp.coerceAtLeast(0L),
                    dayIncomeBaseMicros = dayInc
                )
            }

        TransactionsUiState(
            filters = filters,
            baseCurrency = base,
            monthExpenseBaseMicros = monthExpense,
            monthIncomeBaseMicros = monthIncome,
            monthBalanceBaseMicros = monthIncome - monthExpense,
            groupedDays = groupedDays,
            pendingTransactions = pending,
            pendingCount = pending.size,
            baseAmountByTxId = baseAmountByTxId
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TransactionsUiState())

    // Tab 2: Assets state
    private val _assetsDisplayCurrency = MutableStateFlow<String?>(null)
    private val _showArchivedAccounts = MutableStateFlow(false)

    val assetsUiState: StateFlow<AssetsUiState> = combine(
        allAccounts,
        exchangeRates,
        settings,
        _assetsDisplayCurrency,
        _showArchivedAccounts
    ) { accounts, rates, appSettings, chosenCurrency, showArchived ->
        Triple(accounts, chosenCurrency ?: appSettings.baseCurrency, showArchived) to rates
    }
        .distinctUntilChanged()
        .flatMapLatest { (query, _) ->
            val (accounts, displayCurrency, showArchived) = query
            flow {
                val summary = runCatching {
                    withContext(Dispatchers.IO) { ledgerService.computeNetWorth(displayCurrency) }
                }.getOrElse {
                    NetWorthSummary(displayCurrency, 0L, 0L, 0L, emptyMap())
                }
                val visible = if (showArchived) accounts else accounts.filter { !it.archived }
                val grouped = visible.groupBy { it.groupType }
                emit(
                    AssetsUiState(
                        displayCurrency = displayCurrency,
                        showArchived = showArchived,
                        netWorth = summary,
                        groupedAccounts = grouped,
                        isLoading = false
                    )
                )
            }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AssetsUiState(isLoading = true))

    // Tab 3: Stats state
    private val _statsMonth = MutableStateFlow(YearMonth.now())
    private val _statsCurrency = MutableStateFlow<String?>(null)
    private val _statsType = MutableStateFlow(TransactionType.EXPENSE)
    private val _statsGranularity = MutableStateFlow(StatsGranularity.MONTHLY)
    private val _statsAnchorMillis = MutableStateFlow(System.currentTimeMillis())
    private val _statsCustomStart = MutableStateFlow<Long?>(null)
    private val _statsCustomEnd = MutableStateFlow<Long?>(null)

    private val _statsTimeFlow = combine(
        _statsGranularity,
        _statsAnchorMillis,
        _statsCustomStart,
        _statsCustomEnd
    ) { gran, anchor, cStart, cEnd ->
        listOf(gran, anchor, cStart, cEnd)
    }

    val statsUiState: StateFlow<StatsUiState> = combine(
        combine(allTransactions, tags) { txs, tagList -> txs to tagList },
        budgets,
        exchangeRates,
        settings,
        combine(_statsMonth, _statsCurrency, _statsType, _statsTimeFlow) { m, c, t, timeArgs ->
            Triple(Triple(m, c, t), timeArgs[0] as String, Triple(timeArgs[1] as Long, timeArgs[2] as Long?, timeArgs[3] as Long?))
        }
    ) { (txs, tagList), bgs, rts, appSettings, (baseTriple, granularity, timeRange) ->
        StatsQueryParams(
            month = baseTriple.first,
            displayCurrency = baseTriple.second ?: appSettings.baseCurrency,
            type = baseTriple.third,
            granularity = granularity,
            anchorDateMillis = timeRange.first,
            customStartMillis = timeRange.second,
            customEndMillis = timeRange.third,
            activeLedgerId = appSettings.activeLedgerId
        ) to StatsInputSnapshot(
            txs = txs,
            tags = tagList,
            budgets = bgs,
            rates = rts
        )
    }
        .distinctUntilChanged()
        .flatMapLatest { (params, snapshot) ->
        flow {
            val zone = ZoneId.systemDefault()
            val (windowStart, windowEnd) = computeStatsWindowMillis(
                granularity = params.granularity,
                selectedMonth = params.month,
                anchorDateMillis = params.anchorDateMillis,
                customStartMillis = params.customStartMillis,
                customEndMillis = params.customEndMillis,
                zone = zone
            )
            val effectiveAnchorMillis = if (params.granularity == StatsGranularity.MONTHLY) {
                params.month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
            } else {
                params.anchorDateMillis
            }
            val result = withContext(Dispatchers.IO) {
                val t = runCatching { ledgerService.computeMonthlyTrend(6, params.displayCurrency) }.getOrDefault(emptyList())
                val buckets = runCatching {
                    ledgerService.computeTrendBuckets(
                        granularity = params.granularity,
                        anchorDateMillis = effectiveAnchorMillis,
                        customStartMillis = params.customStartMillis,
                        customEndMillis = params.customEndMillis,
                        ledgerId = params.activeLedgerId,
                        displayCurrency = params.displayCurrency
                    )
                }.getOrDefault(emptyList())
                val nwHistory = runCatching {
                    ledgerService.computeNetWorthHistory(6, params.displayCurrency)
                }.getOrDefault(emptyList())
                val c = runCatching {
                    ledgerService.computeCategoryStatsForLedger(
                        startMillis = windowStart,
                        endMillis = windowEnd,
                        type = params.type,
                        ledgerId = params.activeLedgerId,
                        displayCurrency = params.displayCurrency
                    )
                }.getOrDefault(emptyList())
                val b = runCatching {
                    ledgerService.computeBudgetProgressForLedger(
                        year = params.month.year,
                        month = params.month.monthValue,
                        ledgerId = params.activeLedgerId
                    )
                }.getOrDefault(emptyList())
                listOf(t, buckets, nwHistory, c, b)
            }
            @Suppress("UNCHECKED_CAST")
            val trend = result[0] as List<MonthlyPoint>
            @Suppress("UNCHECKED_CAST")
            val buckets = result[1] as List<TrendBucketPoint>
            @Suppress("UNCHECKED_CAST")
            val nwHistory = result[2] as List<NetWorthPoint>
            @Suppress("UNCHECKED_CAST")
            val catStats = result[3] as List<CategoryStat>
            @Suppress("UNCHECKED_CAST")
            val budgetProgress = result[4] as List<BudgetProgress>
            val tagStats = computeTagStats(
                transactions = snapshot.txs,
                tags = snapshot.tags,
                rates = snapshot.rates,
                startMillis = windowStart,
                endMillis = windowEnd,
                type = params.type,
                displayCurrency = params.displayCurrency,
                activeLedgerId = params.activeLedgerId
            )
            emit(
                StatsUiState(
                    selectedMonth = params.month,
                    displayCurrency = params.displayCurrency,
                    selectedType = params.type,
                    granularity = params.granularity,
                    anchorDateMillis = effectiveAnchorMillis,
                    customStartMillis = params.customStartMillis,
                    customEndMillis = params.customEndMillis,
                    monthlyTrend = trend,
                    trendBuckets = buckets,
                    netWorthHistory = nwHistory,
                    categoryStats = catStats,
                    tagStats = tagStats,
                    budgetProgressList = budgetProgress,
                    totalCategoryAmountMicros = catStats.sumOf { it.totalBaseMicros },
                    isLoading = false
                )
            )
        }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState(isLoading = true))

    private fun computeTagStats(
        transactions: List<TransactionEntity>,
        tags: List<TagEntity>,
        rates: List<ExchangeRateEntity>,
        startMillis: Long,
        endMillis: Long,
        type: String,
        displayCurrency: String,
        activeLedgerId: Long
    ): List<TagStat> {
        val colorMap = tags.associate { it.name to it.colorHex }
        val totalsByTag = LinkedHashMap<String, Long>()
        val countsByTag = LinkedHashMap<String, Int>()
        for (tx in transactions) {
            if (tx.status != TransactionStatus.CONFIRMED || tx.excludeFromStats) continue
            if (activeLedgerId > 0L && tx.ledgerId != activeLedgerId) continue
            if (tx.type != type || tx.refundOfTxId != null) continue
            if (tx.timestamp !in startMillis..endMillis) continue
            val parsedTags = TagTextCodec.parse(tx.tags)
            if (parsedTags.isEmpty()) continue
            val amountBaseMicros = convertTxToDisplayBase(tx, displayCurrency, rates).coerceAtLeast(0L)
            for (tagName in parsedTags) {
                totalsByTag[tagName] = (totalsByTag[tagName] ?: 0L) + amountBaseMicros
                countsByTag[tagName] = (countsByTag[tagName] ?: 0) + 1
            }
        }
        if (totalsByTag.isEmpty()) return emptyList()
        val totalTagSum = totalsByTag.values.sum()
        return totalsByTag.entries
            .sortedWith(
                compareByDescending<Map.Entry<String, Long>> { it.value }
                    .thenByDescending { countsByTag[it.key] ?: 0 }
                    .thenBy { it.key }
            )
            .map { (tagName, totalMicros) ->
                val ratio = if (totalTagSum > 0L) {
                    BigDecimal(totalMicros)
                        .divide(BigDecimal(totalTagSum), 6, RoundingMode.HALF_UP)
                        .toFloat()
                } else {
                    0f
                }
                TagStat(
                    tagName = tagName,
                    colorHex = colorMap[tagName] ?: KairoIcons.fallbackTagColorHex(tagName),
                    totalBaseMicros = totalMicros,
                    count = countsByTag[tagName] ?: 0,
                    percentage = ratio
                )
            }
    }

    private fun computeStatsWindowMillis(
        granularity: String,
        selectedMonth: YearMonth,
        anchorDateMillis: Long,
        customStartMillis: Long?,
        customEndMillis: Long?,
        zone: ZoneId
    ): Pair<Long, Long> {
        val anchorDate = Instant.ofEpochMilli(anchorDateMillis).atZone(zone).toLocalDate()
        return when (granularity) {
            StatsGranularity.WEEKLY -> {
                val mon = anchorDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                val sun = mon.plusDays(6)
                val s = mon.atStartOfDay(zone).toInstant().toEpochMilli()
                val e = sun.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1L
                s to e
            }
            StatsGranularity.YEARLY -> LedgerAnalytics.yearRangeMillis(anchorDate.year, zone)
            StatsGranularity.CUSTOM -> {
                val sDate = Instant.ofEpochMilli(customStartMillis ?: anchorDateMillis).atZone(zone).toLocalDate()
                val eDate = Instant.ofEpochMilli(customEndMillis ?: anchorDateMillis).atZone(zone).toLocalDate()
                val safeS = if (sDate.isAfter(eDate)) eDate else sDate
                val safeE = if (sDate.isAfter(eDate)) sDate else eDate
                safeS.atStartOfDay(zone).toInstant().toEpochMilli() to
                    (safeE.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1L)
            }
            else -> LedgerAnalytics.monthRangeMillis(selectedMonth.year, selectedMonth.monthValue, zone)
        }
    }

    // Tab 4: Automation & Settings state
    private val _permissionReport = MutableStateFlow<DevicePermissionReport?>(null)
    val permissionReport: StateFlow<DevicePermissionReport?> = _permissionReport.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _sandboxState = MutableStateFlow(SandboxTestUiState())
    val sandboxState: StateFlow<SandboxTestUiState> = _sandboxState.asStateFlow()

    private val _llmTestResult = MutableStateFlow<Result<String>?>(null)
    val llmTestResult: StateFlow<Result<String>?> = _llmTestResult.asStateFlow()

    private val _remoteBackups = MutableStateFlow<List<RemoteBackupFile>>(emptyList())
    val remoteBackups: StateFlow<List<RemoteBackupFile>> = _remoteBackups.asStateFlow()

    private val _webDavStatusMessage = MutableStateFlow<String?>(null)
    val webDavStatusMessage: StateFlow<String?> = _webDavStatusMessage.asStateFlow()

    private val _csvImportDialog = MutableStateFlow<CsvImportDialogState?>(null)
    val csvImportDialog: StateFlow<CsvImportDialogState?> = _csvImportDialog.asStateFlow()

    init {
        refreshPermissions()
    }

    fun showMessage(message: String) {
        _snackbarEvents.tryEmit(message)
    }

    // --- Transactions filter actions ---
    fun setTxMonth(month: YearMonth) {
        _txFilters.update { it.copy(yearMonth = month) }
    }

    fun shiftTxMonth(deltaMonths: Long) {
        _txFilters.update { it.copy(yearMonth = it.yearMonth.plusMonths(deltaMonths)) }
    }

    fun setTxQuery(query: String) {
        _txFilters.update { it.copy(query = query) }
    }

    fun setTxCurrencyFilter(currency: String) {
        _txFilters.update { it.copy(currencyFilter = currency) }
    }

    fun setTxAccountFilter(accountId: Long?) {
        _txFilters.update { it.copy(accountIdFilter = accountId) }
    }

    fun setTxChannelFilter(channel: String) {
        _txFilters.update { it.copy(channelFilter = channel) }
    }

    fun togglePendingExpanded() {
        _txFilters.update { it.copy(pendingExpanded = !it.pendingExpanded) }
    }

    fun setTxCategoryFilter(categoryId: Long?) {
        _txFilters.update { it.copy(categoryIdFilter = categoryId) }
    }

    fun setTxReimburseFilter(status: String) {
        _txFilters.update { it.copy(reimburseFilter = status) }
    }

    fun setTxAmountRangeFilter(minMicros: Long?, maxMicros: Long?) {
        _txFilters.update { it.copy(minAmountMicros = minMicros, maxAmountMicros = maxMicros) }
    }

    fun setTxTagFilter(tag: String?) {
        _txFilters.update { it.copy(tagFilter = tag?.trim()?.takeIf { s -> s.isNotEmpty() }) }
    }

    fun clearTxAdvancedFilters() {
        _txFilters.update {
            it.copy(
                categoryIdFilter = null,
                reimburseFilter = "ALL",
                minAmountMicros = null,
                maxAmountMicros = null,
                tagFilter = null
            )
        }
    }

    // --- Transactions CRUD & Pending ---
    suspend fun previewConversion(
        origAmountMicros: Long,
        origCurrency: String,
        accountId: Long,
        manualAccountAmountMicros: Long? = null,
        manualRateToAccountMicros: Long? = null,
        baseCurrency: String? = null
    ): ConversionPreview = withContext(Dispatchers.IO) {
        ledgerService.previewConversion(
            origAmountMicros = origAmountMicros,
            origCurrency = origCurrency,
            accountId = accountId,
            manualAccountAmountMicros = manualAccountAmountMicros,
            manualRateToAccountMicros = manualRateToAccountMicros,
            baseCurrency = baseCurrency
        )
    }

    suspend fun getRateQuote(fromCurrency: String, toCurrency: String): RateQuote =
        withContext(Dispatchers.IO) {
            rateProvider.getRate(fromCurrency, toCurrency)
        }

    fun saveTransaction(request: RecordTransactionRequest, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                ledgerService.saveTransaction(request)
            }
            result.onSuccess {
                showMessage(if (request.existingId != null) "交易已更新" else "已记一笔")
                onSuccess()
            }.onFailure { err ->
                showMessage("保存失败：${err.message ?: "未知错误"}")
            }
        }
    }

    fun deleteTransaction(transactionId: Long, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                ledgerService.deleteTransaction(transactionId)
            }
            result.onSuccess {
                showMessage("已删除交易")
                onSuccess()
            }.onFailure { err ->
                showMessage("删除失败：${err.message ?: "未知错误"}")
            }
        }
    }

    fun confirmTransaction(transactionId: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { ledgerService.confirmTransaction(transactionId) }
                .onSuccess { showMessage("已确认入账") }
                .onFailure { showMessage("确认失败：${it.message}") }
        }
    }

    fun confirmAllPending() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { ledgerService.confirmAllPending() }
                .onSuccess { count -> showMessage("已一键确认 $count 笔待核对账单") }
                .onFailure { showMessage("批量确认失败：${it.message}") }
        }
    }

    suspend fun findTransactionById(id: Long): TransactionEntity? =
        allTransactions.value.firstOrNull { it.id == id }
            ?: ledgerService.observeTransactions().first().firstOrNull { it.id == id }

    suspend fun findCaptureLogById(id: Long): RawCaptureLogEntity? =
        captureLogs.value.firstOrNull { it.id == id }
            ?: captureEngine.observeCaptureLogs(500).first().firstOrNull { it.id == id }

    // --- Assets actions ---
    fun setAssetsDisplayCurrency(currency: String) {
        _assetsDisplayCurrency.value = currency
    }

    fun setShowArchivedAccounts(show: Boolean) {
        _showArchivedAccounts.value = show
    }

    fun saveAccount(account: AccountEntity, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { ledgerService.saveAccount(account) }
            showMessage(if (account.id == 0L) "已新增账户「${account.name}」" else "已更新账户「${account.name}」")
            onDone()
        }
    }

    fun archiveAccount(accountId: Long, archived: Boolean) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { ledgerService.archiveAccount(accountId, archived) }
            showMessage(if (archived) "账户已归档" else "账户已恢复")
        }
    }

    fun calibrateAccountBalance(accountId: Long, targetBalanceMicros: Long, note: String, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                ledgerService.calibrateAccountBalance(accountId, targetBalanceMicros, note)
            }.onSuccess {
                showMessage("余额已校准")
                onDone()
            }.onFailure {
                showMessage("余额校准失败：${it.message}")
            }
        }
    }

    // --- Stats & Budgets actions ---
    fun shiftStatsMonth(deltaMonths: Long) {
        _statsMonth.update { it.plusMonths(deltaMonths) }
    }

    fun setStatsMonth(month: YearMonth) {
        _statsMonth.value = month
    }

    fun setStatsDisplayCurrency(currency: String) {
        _statsCurrency.value = currency
    }

    fun setStatsType(type: String) {
        _statsType.value = type
    }

    fun setStatsGranularity(granularity: String) {
        _statsGranularity.value = granularity
    }

    fun shiftStatsPeriod(delta: Int) {
        val zone = ZoneId.systemDefault()
        when (_statsGranularity.value) {
            StatsGranularity.WEEKLY -> {
                val cur = Instant.ofEpochMilli(_statsAnchorMillis.value).atZone(zone).toLocalDate()
                _statsAnchorMillis.value = cur.plusWeeks(delta.toLong()).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
            }
            StatsGranularity.YEARLY -> {
                val cur = Instant.ofEpochMilli(_statsAnchorMillis.value).atZone(zone).toLocalDate()
                _statsAnchorMillis.value = cur.plusYears(delta.toLong()).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
            }
            else -> shiftStatsMonth(delta.toLong())
        }
    }

    fun setStatsCustomRange(startMillis: Long, endMillis: Long) {
        _statsCustomStart.value = startMillis
        _statsCustomEnd.value = endMillis
    }

    // --- Ledger, Category, Batch, Reimburse, Debt, Installment, Smart Input actions ---
    fun setActiveLedger(ledgerId: Long) {
        updateSettings { it.copy(activeLedgerId = ledgerId) }
    }

    fun saveLedger(ledger: LedgerEntity, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { ledgerService.saveLedger(ledger) }
            showMessage("已保存账本「${ledger.name}」")
            onDone()
        }
    }

    fun archiveLedger(ledgerId: Long, archived: Boolean) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { ledgerService.archiveLedger(ledgerId, archived) }
            if (archived && settings.value.activeLedgerId == ledgerId) {
                updateSettings { it.copy(activeLedgerId = 1L) }
            }
            showMessage(if (archived) "账本已归档" else "账本已恢复")
        }
    }

    fun saveCategory(category: CategoryEntity, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { ledgerService.saveCategory(category) }
            showMessage("已保存分类「${category.name}」")
            onDone()
        }
    }

    fun deleteCategory(categoryId: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { ledgerService.deleteCategory(categoryId) }
            showMessage("分类已删除")
        }
    }

    fun saveTag(tag: TagEntity, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { ledgerService.saveTag(tag) }
            showMessage("已保存标签「${tag.name.trim()}」")
            onDone()
        }
    }

    fun deleteTag(tagId: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { ledgerService.deleteTag(tagId) }
            showMessage("标签已删除")
        }
    }

    fun seedPresetCatalog() {
        viewModelScope.launch {
            val (catCount, tagCount) = withContext(Dispatchers.IO) {
                ledgerService.ensurePresetCatalogSeeded()
            }
            showMessage("已增量补全 $catCount 个预设分类、$tagCount 个预设标签")
        }
    }

    fun batchUpdateCategory(txIds: List<Long>, categoryId: Long?) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { ledgerService.batchUpdateCategory(txIds, categoryId) }
                .onSuccess { count -> showMessage("已批量更新 $count 笔交易分类") }
                .onFailure { showMessage("批量改分类失败：${it.message}") }
        }
    }

    fun batchDeleteTransactions(txIds: List<Long>) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { ledgerService.batchDeleteTransactions(txIds) }
                .onSuccess { count -> showMessage("已批量删除 $count 笔交易并回滚余额") }
                .onFailure { showMessage("批量删除失败：${it.message}") }
        }
    }

    fun settleReimbursements(txIds: List<Long>, targetAccountId: Long, note: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                ledgerService.settleReimbursements(txIds, targetAccountId, System.currentTimeMillis(), note)
            }.onSuccess {
                showMessage("已完成 ${txIds.size} 笔报销入账核销")
            }.onFailure { err ->
                showMessage("报销核销失败：${err.message}")
            }
        }
    }

    fun clearTransactionReimburseFlag(txId: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val tx = container.database.transactionDao().getById(txId) ?: return@withContext
                ledgerService.saveTransaction(
                    RecordTransactionRequest(
                        existingId = tx.id,
                        type = tx.type,
                        timestamp = tx.timestamp,
                        origAmountMicros = tx.origAmountMicros,
                        origCurrency = tx.origCurrency,
                        accountId = tx.accountId,
                        manualAccountAmountMicros = tx.accountAmountMicros,
                        manualRateToAccountMicros = tx.rateToAccountMicros,
                        toAccountId = tx.toAccountId,
                        manualToAmountMicros = tx.toAmountMicros,
                        categoryId = tx.categoryId,
                        merchant = tx.merchant,
                        note = tx.note,
                        tags = tx.tags,
                        reimburseStatus = ReimburseStatus.NONE,
                        sourceType = tx.sourceType,
                        rawEventId = tx.rawEventId,
                        status = tx.status,
                        ledgerId = tx.ledgerId,
                        excludeFromStats = tx.excludeFromStats,
                        excludeFromBudget = tx.excludeFromBudget,
                        refundOfTxId = tx.refundOfTxId,
                        attachmentPaths = tx.attachmentPaths,
                        reimbursedTxId = tx.reimbursedTxId
                    )
                )
            }
            showMessage("已取消该笔账单待报销标记")
        }
    }

    fun saveDebt(debt: DebtRecordEntity, syncAccountBalance: Boolean) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { ledgerService.saveDebt(debt, syncAccountBalance) }
            showMessage("已保存借贷往来「${debt.personName}」")
        }
    }

    fun settleDebt(debtId: Long, settleAmountMicros: Long, accountId: Long?, note: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                ledgerService.settleDebt(debtId, settleAmountMicros, accountId, note)
            }.onSuccess { updated ->
                showMessage(if (updated.status == DebtStatus.SETTLED) "该笔往来已全部结清" else "已完成部分核销")
            }.onFailure {
                showMessage("核销失败：${it.message}")
            }
        }
    }

    fun deleteDebt(debtId: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { ledgerService.deleteDebt(debtId) }
            showMessage("已删除借贷记录")
        }
    }

    fun saveInstallment(plan: InstallmentPlanEntity) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { ledgerService.saveInstallment(plan) }
            showMessage("已保存分期计划「${plan.name}」")
        }
    }

    fun recordInstallmentPeriod(planId: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { ledgerService.recordInstallmentPeriod(planId) }
                .onSuccess { showMessage("已记入本期分期账单") }
                .onFailure { showMessage("分期记账失败：${it.message}") }
        }
    }

    fun deleteInstallment(planId: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { ledgerService.deleteInstallment(planId) }
            showMessage("已删除分期计划")
        }
    }

    suspend fun parseSmartInputText(text: String): List<ParsedBillDraft> = withContext(Dispatchers.IO) {
        smartInputParser.parseSmartInput(
            text = text,
            settings = settingsStore.getSnapshot(),
            accounts = container.database.accountDao().getAll().filter { !it.archived },
            categories = container.database.categoryDao().getAll(),
            aliases = container.database.accountAliasDao().getEnabled()
        )
    }

    suspend fun parseReceiptImage(imagePath: String): Result<ParsedBillDraft?> = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(imagePath)
            require(file.exists() && file.length() > 0L) { "小票图片文件不存在" }
            val bytes = file.readBytes()
            val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            val mimeType = when (file.extension.lowercase()) {
                "png" -> "image/png"
                "webp" -> "image/webp"
                else -> "image/jpeg"
            }
            val curSettings = settingsStore.getSnapshot()
            val accounts = container.database.accountDao().getAll().filter { !it.archived }
            val categories = container.database.categoryDao().getAll()
            llmBillParser.parseReceiptImage(
                imageBase64 = base64,
                mimeType = mimeType,
                settings = curSettings,
                accounts = accounts,
                categories = categories
            ).getOrThrow()
        }
    }

    fun saveSmartDrafts(
        drafts: List<ParsedBillDraft>,
        ledgerId: Long,
        attachmentPath: String?,
        onDone: () -> Unit = {}
    ) {
        if (drafts.isEmpty()) return
        viewModelScope.launch {
            var savedCount = 0
            val resolvedLedger = ledgerId.takeIf { it > 0L } ?: 1L
            withContext(Dispatchers.IO) {
                val activeAccs = container.database.accountDao().getAll().filter { !it.archived }
                val fallbackAcc = activeAccs.firstOrNull() ?: return@withContext
                for (draft in drafts) {
                    val accId = draft.accountId ?: fallbackAcc.id
                    val res = ledgerService.saveTransaction(
                        RecordTransactionRequest(
                            type = draft.type,
                            timestamp = draft.timestamp,
                            origAmountMicros = draft.origAmountMicros,
                            origCurrency = draft.origCurrency,
                            accountId = accId,
                            manualAccountAmountMicros = draft.manualAccountAmountMicros,
                            categoryId = draft.categoryId,
                            merchant = draft.merchant,
                            note = draft.note,
                            tags = draft.tags,
                            sourceType = if (attachmentPath != null) CaptureChannel.AI else CaptureChannel.MANUAL,
                            status = TransactionStatus.CONFIRMED,
                            ledgerId = resolvedLedger,
                            attachmentPaths = attachmentPath.orEmpty()
                        )
                    )
                    if (res.isSuccess) savedCount++
                }
            }
            if (savedCount > 0) {
                showMessage("已成功记入 $savedCount 笔账单")
                onDone()
            } else {
                showMessage("记账保存失败，请检查金额与账户设置")
            }
        }
    }

    fun saveBudget(budget: BudgetEntity, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { ledgerService.saveBudget(budget) }
            showMessage("预算已保存")
            onDone()
        }
    }

    fun deleteBudget(budgetId: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { ledgerService.deleteBudget(budgetId) }
            showMessage("预算已删除")
        }
    }

    // --- Settings & Automation actions ---
    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { settingsStore.update(transform) }
        }
    }

    fun ensurePermissionsLoaded() {
        if (_permissionReport.value == null) {
            refreshPermissions()
        }
    }

    fun refreshPermissions() {
        viewModelScope.launch {
            val report = withContext(Dispatchers.IO) {
                runCatching { captureEngine.inspectPermissions() }.getOrNull()
            }
            _permissionReport.value = report
        }
    }

    fun tryRootGrantPermissions() {
        viewModelScope.launch {
            _isBusy.value = true
            val result = withContext(Dispatchers.IO) { captureEngine.tryRootGrantPermissions() }
            _isBusy.value = false
            result.onSuccess { msg ->
                showMessage(msg)
                refreshPermissions()
            }.onFailure { err ->
                showMessage("Root 授权失败：${err.message ?: "请检查 su 权限"}")
            }
        }
    }

    fun promoteLogToAlias(logId: Long, accountId: Long, matchPattern: String, matchType: String, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                captureEngine.promoteLogToAliasOrRule(logId, accountId, matchPattern, matchType)
            }.onSuccess {
                showMessage("已沉淀为账户来源指纹")
                onDone()
            }.onFailure {
                showMessage("沉淀失败：${it.message}")
            }
        }
    }

    fun saveAlias(alias: AccountAliasEntity, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { captureEngine.saveAlias(alias) }
            showMessage("已保存账户指纹")
            onDone()
        }
    }

    fun deleteAlias(aliasId: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { captureEngine.deleteAlias(aliasId) }
            showMessage("已删除账户指纹")
        }
    }

    fun saveRule(rule: ParseRuleEntity, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { captureEngine.saveRule(rule) }
            showMessage("已保存正则规则")
            onDone()
        }
    }

    fun deleteRule(ruleId: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { captureEngine.deleteRule(ruleId) }
            showMessage("已删除正则规则")
        }
    }

    fun runSandboxTest(title: String, content: String, sourcePackage: String, allowLlm: Boolean) {
        viewModelScope.launch {
            _sandboxState.value = SandboxTestUiState(isRunning = true)
            val result = withContext(Dispatchers.IO) {
                captureEngine.testParseText(title, content, sourcePackage.ifBlank { "*" }, allowLlm)
            }
            _sandboxState.value = result.fold(
                onSuccess = { draft ->
                    SandboxTestUiState(
                        isRunning = false,
                        hasResult = true,
                        draft = draft,
                        errorMessage = if (draft == null) "未识别为动账消息（可能被噪声过滤或未命中任何规则）" else null
                    )
                },
                onFailure = { err ->
                    SandboxTestUiState(
                        isRunning = false,
                        hasResult = true,
                        draft = null,
                        errorMessage = err.message ?: "解析异常"
                    )
                }
            )
        }
    }

    fun saveAndTestLlm(updatedSettings: AppSettings) {
        viewModelScope.launch {
            _isBusy.value = true
            _llmTestResult.value = null
            withContext(Dispatchers.IO) { settingsStore.saveAll(updatedSettings) }
            val result = withContext(Dispatchers.IO) { captureEngine.testLlmConnection() }
            _llmTestResult.value = result
            _isBusy.value = false
            result.onSuccess { showMessage("AI 连接测试成功") }
                .onFailure { showMessage("AI 连接失败：${it.message}") }
        }
    }

    // --- Exchange rates ---
    fun refreshNetworkRates(baseCurrency: String = "CNY") {
        viewModelScope.launch {
            _isBusy.value = true
            val result = withContext(Dispatchers.IO) { rateProvider.refreshFromNetwork(baseCurrency) }
            _isBusy.value = false
            result.onSuccess { count -> showMessage("已更新 $count 条实时汇率") }
                .onFailure { showMessage("汇率刷新失败：${it.message}") }
        }
    }

    fun saveCustomRate(fromCurrency: String, toCurrency: String, rateMicros: Long, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { rateProvider.saveCustomRate(fromCurrency, toCurrency, rateMicros) }
            showMessage("已覆盖自定义汇率：1 $fromCurrency = ${MoneyAmount.formatRate(rateMicros)} $toCurrency")
            onDone()
        }
    }

    // --- Recurring rules ---
    fun saveRecurringRule(rule: RecurringRuleEntity, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { ledgerService.saveRecurringRule(rule) }
            showMessage("已保存周期订阅「${rule.name}」")
            onDone()
        }
    }

    fun deleteRecurringRule(ruleId: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { ledgerService.deleteRecurringRule(ruleId) }
            showMessage("已删除周期订阅")
        }
    }

    fun runDueRecurringRules() {
        viewModelScope.launch {
            _isBusy.value = true
            val count = withContext(Dispatchers.IO) {
                runCatching { ledgerService.processDueRecurringRules() }.getOrDefault(0)
            }
            _isBusy.value = false
            showMessage(if (count > 0) "已自动执行 $count 笔到期周期账单" else "当前暂无到期的周期订阅账单")
        }
    }

    // --- CSV Import ---
    fun previewCsvImport(fileName: String, bytes: ByteArray) {
        viewModelScope.launch {
            _csvImportDialog.value = CsvImportDialogState(fileName = fileName, bytes = bytes, isProcessing = true)
            val result = withContext(Dispatchers.IO) {
                billImporter.previewCsv(ByteArrayInputStream(bytes))
            }
            _csvImportDialog.value = result.fold(
                onSuccess = { preview ->
                    CsvImportDialogState(fileName = fileName, bytes = bytes, preview = preview, isProcessing = false)
                },
                onFailure = { err ->
                    CsvImportDialogState(
                        fileName = fileName,
                        bytes = bytes,
                        isProcessing = false,
                        error = err.message ?: "CSV 解析失败"
                    )
                }
            )
        }
    }

    fun confirmCsvImport() {
        val current = _csvImportDialog.value ?: return
        val bytes = current.bytes ?: return
        viewModelScope.launch {
            _csvImportDialog.value = current.copy(isProcessing = true)
            val result = withContext(Dispatchers.IO) {
                billImporter.importCsv(ByteArrayInputStream(bytes))
            }
            _csvImportDialog.value = null
            result.onSuccess { summary ->
                showMessage(
                    "导入完成：新增 ${summary.importedCount} 笔，跳过重复 ${summary.skippedDuplicateCount} 笔" +
                        if (summary.createdAccounts > 0 || summary.createdCategories > 0) {
                            "（新建账户 ${summary.createdAccounts} / 分类 ${summary.createdCategories}）"
                        } else ""
                )
            }.onFailure { err ->
                showMessage("导入失败：${err.message ?: "未知错误"}")
            }
        }
    }

    fun dismissCsvImportDialog() {
        _csvImportDialog.value = null
    }

    // --- WebDAV & Local Backup ---
    fun saveAndTestWebDav(updated: AppSettings) {
        viewModelScope.launch {
            _isBusy.value = true
            withContext(Dispatchers.IO) { settingsStore.saveAll(updated) }
            val result = withContext(Dispatchers.IO) { cloudSyncService.testWebDavConnection() }
            _isBusy.value = false
            result.onSuccess { msg ->
                _webDavStatusMessage.value = "连接成功：$msg"
                showMessage("WebDAV 连接成功")
                refreshRemoteBackups()
            }.onFailure { err ->
                _webDavStatusMessage.value = "连接失败：${err.message}"
                showMessage("WebDAV 连接失败：${err.message}")
            }
        }
    }

    fun uploadWebDavBackup(updated: AppSettings) {
        viewModelScope.launch {
            _isBusy.value = true
            withContext(Dispatchers.IO) { settingsStore.saveAll(updated) }
            val result = withContext(Dispatchers.IO) { cloudSyncService.uploadEncryptedBackup() }
            _isBusy.value = false
            result.onSuccess { file ->
                _webDavStatusMessage.value = "已上传：${file.name} (${file.sizeBytes / 1024} KB)"
                showMessage("加密备份已上传：${file.name}")
                refreshRemoteBackups()
            }.onFailure { err ->
                _webDavStatusMessage.value = "上传失败：${err.message}"
                showMessage("上传失败：${err.message}")
            }
        }
    }

    fun refreshRemoteBackups() {
        viewModelScope.launch {
            _isBusy.value = true
            val result = withContext(Dispatchers.IO) { cloudSyncService.listRemoteBackups() }
            _isBusy.value = false
            result.onSuccess { files ->
                _remoteBackups.value = files.sortedByDescending { it.modifiedAtMillis }
            }.onFailure { err ->
                _webDavStatusMessage.value = "拉取云端列表失败：${err.message}"
            }
        }
    }

    fun restoreRemoteBackup(remotePath: String) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = withContext(Dispatchers.IO) { cloudSyncService.restoreEncryptedBackup(remotePath) }
            _isBusy.value = false
            result.onSuccess { msg ->
                _webDavStatusMessage.value = msg
                showMessage(msg)
            }.onFailure { err ->
                _webDavStatusMessage.value = "恢复失败：${err.message}"
                showMessage("恢复失败：${err.message}")
            }
        }
    }

    fun exportLocalBackup(passphrase: String, onBytesReady: (ByteArray) -> Unit) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = withContext(Dispatchers.IO) {
                cloudSyncService.exportLocalEncryptedBytes(passphrase)
            }
            _isBusy.value = false
            result.onSuccess { bytes ->
                onBytesReady(bytes)
            }.onFailure { err ->
                showMessage("导出失败：${err.message ?: "请检查加密口令"}")
            }
        }
    }

    fun importLocalBackup(bytes: ByteArray, passphrase: String) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = withContext(Dispatchers.IO) {
                cloudSyncService.importLocalEncryptedBytes(bytes, passphrase)
            }
            _isBusy.value = false
            result.onSuccess { msg ->
                showMessage(msg)
            }.onFailure { err ->
                showMessage("本地恢复失败：${err.message ?: "口令错误或文件已损坏"}")
            }
        }
    }

    /**
     * 先还原记录时冻结的主币种金额，仅当全局主币种切换为与 tx.baseCurrency 不同时再折算。
     */
    fun convertTxToDisplayBase(
        tx: TransactionEntity,
        displayBaseCurrency: String,
        rates: List<ExchangeRateEntity>
    ): Long {
        val frozenBaseAmount = MoneyAmount.convert(tx.origAmountMicros, tx.rateToBaseMicros)
        if (tx.baseCurrency.equals(displayBaseCurrency, ignoreCase = true)) {
            return frozenBaseAmount
        }
        val rate = resolveRateFromList(tx.baseCurrency, displayBaseCurrency, rates)
        return MoneyAmount.convert(frozenBaseAmount, rate)
    }

    fun resolveRateFromList(
        fromCurrency: String,
        toCurrency: String,
        rates: List<ExchangeRateEntity>
    ): Long {
        val from = fromCurrency.trim().uppercase()
        val to = toCurrency.trim().uppercase()
        if (from == to) return MoneyAmount.RATE_SCALE

        val direct = rates
            .filter { it.baseCurrency.equals(from, true) && it.targetCurrency.equals(to, true) }
            .maxByOrNull { it.date + "_" + it.updatedAt }
        if (direct != null) return direct.rateMicros

        val inverse = rates
            .filter { it.baseCurrency.equals(to, true) && it.targetCurrency.equals(from, true) }
            .maxByOrNull { it.date + "_" + it.updatedAt }
        if (inverse != null) return MoneyAmount.invertRate(inverse.rateMicros)

        val cny = CurrencyCatalog.DEFAULT_BASE_CURRENCY
        val fromToCny = if (from == cny) MoneyAmount.RATE_SCALE else {
            rates.filter { it.baseCurrency.equals(from, true) && it.targetCurrency.equals(cny, true) }
                .maxByOrNull { it.date + "_" + it.updatedAt }?.rateMicros
                ?: rates.filter { it.baseCurrency.equals(cny, true) && it.targetCurrency.equals(from, true) }
                    .maxByOrNull { it.date + "_" + it.updatedAt }?.let { MoneyAmount.invertRate(it.rateMicros) }
                ?: CurrencyCatalog.find(from).fallbackRateToCnyMicros
        }
        val toToCny = if (to == cny) MoneyAmount.RATE_SCALE else {
            rates.filter { it.baseCurrency.equals(to, true) && it.targetCurrency.equals(cny, true) }
                .maxByOrNull { it.date + "_" + it.updatedAt }?.rateMicros
                ?: rates.filter { it.baseCurrency.equals(cny, true) && it.targetCurrency.equals(to, true) }
                    .maxByOrNull { it.date + "_" + it.updatedAt }?.let { MoneyAmount.invertRate(it.rateMicros) }
                ?: CurrencyCatalog.find(to).fallbackRateToCnyMicros
        }
        return MoneyAmount.crossRate(fromToCny, toToCny)
    }
}

class KairoViewModelFactory(
    private val container: AppContainer
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(KairoMainViewModel::class.java)) {
            return KairoMainViewModel(container) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

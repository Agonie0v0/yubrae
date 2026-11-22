package com.kairoledger.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kairoledger.app.AppContainer
import com.kairoledger.app.core.contract.BudgetProgress
import com.kairoledger.app.core.contract.CategoryStat
import com.kairoledger.app.core.contract.ConversionPreview
import com.kairoledger.app.core.contract.DevicePermissionReport
import com.kairoledger.app.core.contract.ImportPreview
import com.kairoledger.app.core.contract.MonthlyPoint
import com.kairoledger.app.core.contract.NetWorthSummary
import com.kairoledger.app.core.contract.ParsedBillDraft
import com.kairoledger.app.core.contract.RateQuote
import com.kairoledger.app.core.contract.RecordTransactionRequest
import com.kairoledger.app.core.contract.RemoteBackupFile
import com.kairoledger.app.core.model.CurrencyCatalog
import com.kairoledger.app.core.model.MoneyAmount
import com.kairoledger.app.data.db.AccountAliasEntity
import com.kairoledger.app.data.db.AccountEntity
import com.kairoledger.app.data.db.BudgetEntity
import com.kairoledger.app.data.db.CategoryEntity
import com.kairoledger.app.data.db.ExchangeRateEntity
import com.kairoledger.app.data.db.ParseRuleEntity
import com.kairoledger.app.data.db.RawCaptureLogEntity
import com.kairoledger.app.data.db.RecurringRuleEntity
import com.kairoledger.app.data.db.TransactionEntity
import com.kairoledger.app.data.db.TransactionStatus
import com.kairoledger.app.data.db.TransactionType
import com.kairoledger.app.data.prefs.AppSettings
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

data class TransactionFilters(
    val yearMonth: YearMonth = YearMonth.now(),
    val query: String = "",
    val currencyFilter: String = "ALL",
    val accountIdFilter: Long? = null,
    val channelFilter: String = "ALL",
    val pendingExpanded: Boolean = false
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
    val pendingCount: Int = 0
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
    val monthlyTrend: List<MonthlyPoint> = emptyList(),
    val categoryStats: List<CategoryStat> = emptyList(),
    val budgetProgressList: List<BudgetProgress> = emptyList(),
    val totalCategoryAmountMicros: Long = 0L,
    val isLoading: Boolean = false
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

    private val _snackbarEvents = MutableSharedFlow<String>(extraBufferCapacity = 24)
    val snackbarEvents: SharedFlow<String> = _snackbarEvents.asSharedFlow()

    val settings: StateFlow<AppSettings> = settingsStore.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    val activeAccounts: StateFlow<List<AccountEntity>> = ledgerService.observeActiveAccounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val allAccounts: StateFlow<List<AccountEntity>> = ledgerService.observeAllAccounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val categories: StateFlow<List<CategoryEntity>> = ledgerService.observeCategories()
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
        pendingCount,
        exchangeRates,
        combine(_txFilters, settings) { filters, appSettings -> filters to appSettings }
    ) { transactions, pending, count, rates, (filters, appSettings) ->
        val base = appSettings.baseCurrency
        val zone = ZoneId.systemDefault()
        val monthStart = filters.yearMonth.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val monthEnd = filters.yearMonth.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1L

        val inMonth = transactions.filter { it.timestamp in monthStart..monthEnd }
        var monthExpense = 0L
        var monthIncome = 0L
        inMonth.forEach { tx ->
            if (tx.status == TransactionStatus.CONFIRMED) {
                val baseMicros = convertTxToDisplayBase(tx, base, rates)
                when (tx.type) {
                    TransactionType.EXPENSE -> monthExpense += baseMicros
                    TransactionType.INCOME -> monthIncome += baseMicros
                }
            }
        }

        val queryLower = filters.query.trim().lowercase()
        val filtered = inMonth.filter { tx ->
            val matchCurrency = filters.currencyFilter == "ALL" ||
                tx.origCurrency.equals(filters.currencyFilter, ignoreCase = true) ||
                tx.accountCurrency.equals(filters.currencyFilter, ignoreCase = true)
            val matchAccount = filters.accountIdFilter == null ||
                tx.accountId == filters.accountIdFilter ||
                tx.toAccountId == filters.accountIdFilter
            val matchChannel = filters.channelFilter == "ALL" || tx.sourceType == filters.channelFilter
            val matchQuery = queryLower.isEmpty() ||
                tx.merchant.lowercase().contains(queryLower) ||
                tx.note.lowercase().contains(queryLower) ||
                tx.tags.lowercase().contains(queryLower)
            matchCurrency && matchAccount && matchChannel && matchQuery
        }

        val groupedDays = filtered
            .groupBy { Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate() }
            .entries
            .sortedByDescending { it.key }
            .map { (date, dayTxs) ->
                var dayExp = 0L
                var dayInc = 0L
                dayTxs.forEach { tx ->
                    if (tx.status == TransactionStatus.CONFIRMED) {
                        val amt = convertTxToDisplayBase(tx, base, rates)
                        when (tx.type) {
                            TransactionType.EXPENSE -> dayExp += amt
                            TransactionType.INCOME -> dayInc += amt
                        }
                    }
                }
                DayTransactionGroup(
                    date = date,
                    transactions = dayTxs.sortedByDescending { it.timestamp },
                    dayExpenseBaseMicros = dayExp,
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
            pendingCount = count
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TransactionsUiState())

    // Tab 2: Assets state
    private val _assetsDisplayCurrency = MutableStateFlow<String?>(null)
    private val _showArchivedAccounts = MutableStateFlow(false)

    val assetsUiState: StateFlow<AssetsUiState> = combine(
        allAccounts,
        exchangeRates,
        settings,
        _assetsDisplayCurrency,
        _showArchivedAccounts
    ) { accounts, _, appSettings, chosenCurrency, showArchived ->
        Triple(accounts, chosenCurrency ?: appSettings.baseCurrency, showArchived)
    }.flatMapLatest { (accounts, displayCurrency, showArchived) ->
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
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AssetsUiState(isLoading = true))

    // Tab 3: Stats state
    private val _statsMonth = MutableStateFlow(YearMonth.now())
    private val _statsCurrency = MutableStateFlow<String?>(null)
    private val _statsType = MutableStateFlow(TransactionType.EXPENSE)

    val statsUiState: StateFlow<StatsUiState> = combine(
        allTransactions,
        budgets,
        exchangeRates,
        settings,
        combine(_statsMonth, _statsCurrency, _statsType) { m, c, t -> Triple(m, c, t) }
    ) { _, _, _, appSettings, (month, chosenCurrency, type) ->
        Triple(month, chosenCurrency ?: appSettings.baseCurrency, type)
    }.flatMapLatest { (month, displayCurrency, type) ->
        flow {
            val zone = ZoneId.systemDefault()
            val startMillis = month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val endMillis = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1L
            val (trend, catStats, budgetProgress) = withContext(Dispatchers.IO) {
                val t = runCatching { ledgerService.computeMonthlyTrend(6, displayCurrency) }.getOrDefault(emptyList())
                val c = runCatching { ledgerService.computeCategoryStats(startMillis, endMillis, type, displayCurrency) }.getOrDefault(emptyList())
                val b = runCatching { ledgerService.computeBudgetProgress(month.year, month.monthValue) }.getOrDefault(emptyList())
                Triple(t, c, b)
            }
            emit(
                StatsUiState(
                    selectedMonth = month,
                    displayCurrency = displayCurrency,
                    selectedType = type,
                    monthlyTrend = trend,
                    categoryStats = catStats,
                    budgetProgressList = budgetProgress,
                    totalCategoryAmountMicros = catStats.sumOf { it.totalBaseMicros },
                    isLoading = false
                )
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState(isLoading = true))

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

package com.yubrae.app.ui.screens.transactions

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yubrae.app.core.model.CurrencyCatalog
import com.yubrae.app.core.model.MoneyAmount
import com.yubrae.app.data.db.LedgerEntity
import com.yubrae.app.data.db.TransactionEntity
import com.yubrae.app.data.db.TransactionStatus
import com.yubrae.app.data.db.TransactionType
import com.yubrae.app.ui.components.ConfirmActionDialog
import com.yubrae.app.ui.components.EmptyStateCard
import com.yubrae.app.ui.components.KairoFormats
import com.yubrae.app.ui.components.LocalPrivacyMasked
import com.yubrae.app.ui.components.MonthSummaryDetailDialog
import com.yubrae.app.ui.components.TopStatusBarScrim
import com.yubrae.app.ui.components.YearMonthPickerDialog
import com.yubrae.app.ui.viewmodel.KairoMainViewModel
import java.time.LocalDate

@Composable
fun TransactionsScreen(
    viewModel: KairoMainViewModel,
    onEditTransaction: (TransactionEntity) -> Unit,
    onOpenQuickRecord: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    activeLedgerId: Long = -1L,
    ledgers: List<LedgerEntity> = emptyList(),
    selectedCategoryIdFilter: Long? = null,
    onSelectCategoryFilter: (Long?) -> Unit = {},
    selectedReimburseFilter: String = "ALL",
    onSelectReimburseFilter: (String) -> Unit = {},
    minAmountFilterText: String = "",
    maxAmountFilterText: String = "",
    onAmountRangeFilterChange: (String, String) -> Unit = { _, _ -> },
    onRefundTransaction: (TransactionEntity) -> Unit = {},
    onBatchUpdateCategory: (List<Long>, Long?) -> Unit = { _, _ -> },
    onBatchDeleteTransactions: (List<Long>) -> Unit = {},
    listState: LazyListState = rememberLazyListState(),
    onOpenLedgerManager: () -> Unit = {},
    onTogglePrivacy: () -> Unit = {
        viewModel.updateSettings { it.copy(privacyMaskEnabled = !it.privacyMaskEnabled) }
    },
    onOpenSmartInput: () -> Unit = {},
    onMultiSelectModeChange: (Boolean) -> Unit = {}
) {
    val uiState by viewModel.transactionsUiState.collectAsStateWithLifecycle()
    val accounts by viewModel.allAccounts.collectAsStateWithLifecycle()
    val activeAccounts by viewModel.activeAccounts.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val tagsList by viewModel.tags.collectAsStateWithLifecycle()
    val rates by viewModel.exchangeRates.collectAsStateWithLifecycle()
    val vmLedgers by viewModel.ledgers.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val privacyMasked = LocalPrivacyMasked.current

    val resolvedLedgers = ledgers.ifEmpty { vmLedgers }
    val resolvedLedgerId = if (activeLedgerId >= 0L) activeLedgerId else settings.activeLedgerId

    val accountMap = remember(accounts) { accounts.associateBy { it.id } }
    val categoryMap = remember(categories) { categories.associateBy { it.id } }
    val ledgerMap = remember(resolvedLedgers) { resolvedLedgers.associateBy { it.id } }
    val tagMap = remember(tagsList) { tagsList.associateBy { it.name } }

    var viewMode by rememberSaveable { mutableStateOf(TxViewMode.LIST) }
    var selectedCalendarDate by remember(uiState.filters.yearMonth) { mutableStateOf<LocalDate?>(null) }

    var searchBarExpanded by rememberSaveable { mutableStateOf(false) }
    var showAdvancedFilters by rememberSaveable { mutableStateOf(false) }

    val filters = uiState.filters
    var localMinAmountText by rememberSaveable {
        mutableStateOf(
            filters.minAmountMicros?.let {
                MoneyAmount.formatPlain(it, CurrencyCatalog.find(uiState.baseCurrency).minorDigits, false)
            } ?: minAmountFilterText
        )
    }
    var localMaxAmountText by rememberSaveable {
        mutableStateOf(
            filters.maxAmountMicros?.let {
                MoneyAmount.formatPlain(it, CurrencyCatalog.find(uiState.baseCurrency).minorDigits, false)
            } ?: maxAmountFilterText
        )
    }

    var isMultiSelectMode by remember { mutableStateOf(false) }
    var selectedTxIds by remember { mutableStateOf(emptySet<Long>()) }
    var showBatchCategoryDialog by remember { mutableStateOf(false) }
    var showBatchDeleteConfirm by remember { mutableStateOf(false) }

    var detailTransaction by remember { mutableStateOf<TransactionEntity?>(null) }
    var deleteCandidate by remember { mutableStateOf<TransactionEntity?>(null) }
    var showMonthPickerDialog by remember { mutableStateOf(false) }
    var showMonthSummaryDialog by remember { mutableStateOf(false) }

    val latestOnMultiSelectModeChange by rememberUpdatedState(onMultiSelectModeChange)
    LaunchedEffect(isMultiSelectMode) {
        latestOnMultiSelectModeChange(isMultiSelectMode)
    }
    DisposableEffect(Unit) {
        onDispose {
            latestOnMultiSelectModeChange(false)
        }
    }

    BackHandler(enabled = isMultiSelectMode) {
        isMultiSelectMode = false
        selectedTxIds = emptySet()
    }

    val visibleDays = remember(uiState.groupedDays, viewMode, selectedCalendarDate) {
        if (viewMode == TxViewMode.CALENDAR && selectedCalendarDate != null) {
            uiState.groupedDays.filter { it.date == selectedCalendarDate }
        } else {
            uiState.groupedDays
        }
    }

    val hasAnySubFilter = filters.query.isNotBlank() ||
        filters.currencyFilter != "ALL" ||
        filters.accountIdFilter != null ||
        filters.channelFilter != "ALL" ||
        filters.categoryIdFilter != null ||
        filters.reimburseFilter != "ALL" ||
        filters.minAmountMicros != null ||
        filters.maxAmountMicros != null ||
        filters.tagFilter != null

    val showSearchAndFilterSection = searchBarExpanded || hasAnySubFilter

    val clearAllFilters: () -> Unit = {
        viewModel.setTxQuery("")
        viewModel.setTxCurrencyFilter("ALL")
        viewModel.setTxAccountFilter(null)
        viewModel.setTxChannelFilter("ALL")
        viewModel.clearTxAdvancedFilters()
        localMinAmountText = ""
        localMaxAmountText = ""
        onSelectCategoryFilter(null)
        onSelectReimburseFilter("ALL")
        onAmountRangeFilterChange("", "")
        searchBarExpanded = false
    }

    val advancedFilterCount = remember(
        filters.categoryIdFilter,
        filters.reimburseFilter,
        filters.minAmountMicros,
        filters.maxAmountMicros,
        filters.tagFilter
    ) {
        listOfNotNull(
            filters.categoryIdFilter,
            filters.reimburseFilter.takeIf { it != "ALL" },
            filters.minAmountMicros ?: filters.maxAmountMicros,
            filters.tagFilter
        ).size
    }

    val allVisibleTxIds = remember(visibleDays, isMultiSelectMode) {
        if (isMultiSelectMode) {
            visibleDays.flatMap { day -> day.transactions.map { it.id } }
        } else {
            emptyList()
        }
    }

    val activeLedger = remember(resolvedLedgerId, ledgerMap, resolvedLedgers) {
        if (resolvedLedgerId == 0L) null else ledgerMap[resolvedLedgerId] ?: resolvedLedgers.firstOrNull { !it.archived }
    }
    val activeLedgerLabel = remember(resolvedLedgerId, activeLedger) {
        when {
            resolvedLedgerId == 0L -> "全部账本"
            else -> activeLedger?.name ?: "账本"
        }
    }

    val foreignCurrencySummaries = remember(uiState.groupedDays, uiState.baseCurrency) {
        val map = linkedMapOf<String, Pair<Long, Long>>()
        uiState.groupedDays.forEach { day ->
            day.transactions.forEach { tx ->
                if (tx.status == TransactionStatus.CONFIRMED &&
                    !tx.excludeFromStats &&
                    !tx.origCurrency.equals(uiState.baseCurrency, ignoreCase = true)
                ) {
                    val code = tx.origCurrency.uppercase()
                    val (exp, inc) = map[code] ?: (0L to 0L)
                    when {
                        tx.type == TransactionType.EXPENSE -> {
                            map[code] = (exp + tx.origAmountMicros) to inc
                        }
                        tx.type == TransactionType.INCOME && tx.refundOfTxId != null -> {
                            map[code] = (exp - tx.origAmountMicros).coerceAtLeast(0L) to inc
                        }
                        tx.type == TransactionType.INCOME -> {
                            map[code] = exp to (inc + tx.origAmountMicros)
                        }
                    }
                }
            }
        }
        map.entries
            .filter { (_, pair) -> pair.first != 0L || pair.second != 0L }
            .map { (code, pair) -> Triple(code, pair.first, pair.second) }
    }

    val showStatusBarScrim by remember(listState) {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 40
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            AnimatedVisibility(visible = isMultiSelectMode) {
                BatchSelectionToolbar(
                    selectedCount = selectedTxIds.size,
                    totalVisibleCount = allVisibleTxIds.size,
                    allSelected = allVisibleTxIds.isNotEmpty() && allVisibleTxIds.all { it in selectedTxIds },
                    onSelectAllToggle = {
                        selectedTxIds = if (allVisibleTxIds.isNotEmpty() && allVisibleTxIds.all { it in selectedTxIds }) {
                            emptySet()
                        } else {
                            allVisibleTxIds.toSet()
                        }
                    },
                    onInvertSelection = {
                        val visibleSet = allVisibleTxIds.toSet()
                        selectedTxIds = visibleSet - selectedTxIds
                    },
                    onBatchCategory = { if (selectedTxIds.isNotEmpty()) showBatchCategoryDialog = true },
                    onBatchDelete = { if (selectedTxIds.isNotEmpty()) showBatchDeleteConfirm = true },
                    onExit = {
                        isMultiSelectMode = false
                        selectedTxIds = emptySet()
                    },
                    modifier = Modifier
                        .statusBarsPadding()
                        .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 4.dp)
                )
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(
                    bottom = contentPadding.calculateBottomPadding() + 84.dp
                )
            ) {
                item(key = "top_header", contentType = "top_header") {
                    TransactionsMinimalHeader(
                        activeLedgerLabel = activeLedgerLabel,
                        activeLedgerIconKey = activeLedger?.icon,
                        yearMonth = filters.yearMonth,
                        baseCurrency = uiState.baseCurrency,
                        expenseMicros = uiState.monthExpenseBaseMicros,
                        incomeMicros = uiState.monthIncomeBaseMicros,
                        balanceMicros = uiState.monthBalanceBaseMicros,
                        monthDiscountBaseMicros = uiState.monthDiscountBaseMicros,
                        monthFeeBaseMicros = uiState.monthFeeBaseMicros,
                        foreignCurrencySummaries = foreignCurrencySummaries,
                        privacyMasked = privacyMasked,
                        viewMode = viewMode,
                        searchExpanded = showSearchAndFilterSection,
                        hasActiveFilter = hasAnySubFilter,
                        applyStatusBarPadding = !isMultiSelectMode,
                        onOpenLedgerManager = onOpenLedgerManager,
                        onToggleSearch = { searchBarExpanded = !searchBarExpanded },
                        onTogglePrivacy = onTogglePrivacy,
                        onOpenSmartInput = onOpenSmartInput,
                        onPrevMonth = { viewModel.shiftTxMonth(-1) },
                        onNextMonth = { viewModel.shiftTxMonth(1) },
                        onOpenMonthPicker = { showMonthPickerDialog = true },
                        onOpenSummaryDetail = { showMonthSummaryDialog = true },
                        onToggleViewMode = {
                            viewMode = if (viewMode == TxViewMode.LIST) TxViewMode.CALENDAR else TxViewMode.LIST
                            if (viewMode == TxViewMode.LIST) {
                                selectedCalendarDate = null
                            }
                        }
                    )
                }

                if (showSearchAndFilterSection) {
                    item(key = "search_filter", contentType = "search_filter") {
                        CollapsibleSearchFilterBar(
                            filters = filters,
                            baseCurrency = uiState.baseCurrency,
                            pinnedCurrencies = settings.pinnedCurrencies,
                            accounts = accounts,
                            accountMap = accountMap,
                            categories = categories,
                            tags = tagsList,
                            hasAnySubFilter = hasAnySubFilter,
                            showAdvancedFilters = showAdvancedFilters,
                            advancedFilterCount = advancedFilterCount,
                            minAmountText = localMinAmountText,
                            maxAmountText = localMaxAmountText,
                            onQueryChange = { viewModel.setTxQuery(it) },
                            onToggleAdvancedFilters = { showAdvancedFilters = !showAdvancedFilters },
                            onClearAllFilters = clearAllFilters,
                            onSelectCurrency = { viewModel.setTxCurrencyFilter(it) },
                            onSelectAccount = { viewModel.setTxAccountFilter(it) },
                            onSelectChannel = { viewModel.setTxChannelFilter(it) },
                            onSelectCategory = { catId ->
                                viewModel.setTxCategoryFilter(catId)
                                onSelectCategoryFilter(catId)
                            },
                            onSelectTag = { viewModel.setTxTagFilter(it) },
                            onSelectReimburseStatus = { status ->
                                viewModel.setTxReimburseFilter(status)
                                onSelectReimburseFilter(status)
                            },
                            onAmountRangeChange = { minStr, maxStr ->
                                localMinAmountText = minStr
                                localMaxAmountText = maxStr
                                val minMicros = minStr.takeIf { it.isNotBlank() }
                                    ?.let { MoneyAmount.parseToMicros(it) }
                                    ?.takeIf { it >= 0L }
                                val maxMicros = maxStr.takeIf { it.isNotBlank() }
                                    ?.let { MoneyAmount.parseToMicros(it) }
                                    ?.takeIf { it >= 0L }
                                viewModel.setTxAmountRangeFilter(minMicros, maxMicros)
                                onAmountRangeFilterChange(minStr, maxStr)
                            },
                            onClearAdvanced = {
                                localMinAmountText = ""
                                localMaxAmountText = ""
                                viewModel.clearTxAdvancedFilters()
                                onSelectCategoryFilter(null)
                                onSelectReimburseFilter("ALL")
                                onAmountRangeFilterChange("", "")
                            },
                            modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp)
                        )
                    }
                }

                if (uiState.pendingCount > 0) {
                    item(key = "pending_inbox", contentType = "pending_inbox") {
                        PendingInboxBanner(
                            pendingCount = uiState.pendingCount,
                            expanded = filters.pendingExpanded,
                            pendingTransactions = uiState.pendingTransactions,
                            baseAmountByTxId = uiState.baseAmountByTxId,
                            accountMap = accountMap,
                            categoryMap = categoryMap,
                            baseCurrency = uiState.baseCurrency,
                            rates = rates,
                            privacyMasked = privacyMasked,
                            viewModel = viewModel,
                            onToggleExpand = { viewModel.togglePendingExpanded() },
                            onConfirmAll = { viewModel.confirmAllPending() },
                            onConfirmOne = { viewModel.confirmTransaction(it.id) },
                            onEditOne = onEditTransaction,
                            onDeleteOne = { deleteCandidate = it },
                            modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp)
                        )
                    }
                }

                if (viewMode == TxViewMode.CALENDAR) {
                    item(key = "calendar_grid", contentType = "calendar_grid") {
                        MonthlyCalendarCard(
                            yearMonth = filters.yearMonth,
                            monthDays = uiState.groupedDays,
                            selectedDate = selectedCalendarDate,
                            privacyMasked = privacyMasked,
                            onSelectDate = { tappedDate ->
                                selectedCalendarDate = if (selectedCalendarDate == tappedDate) null else tappedDate
                            },
                            modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp)
                        )
                    }

                    if (selectedCalendarDate != null) {
                        item(key = "calendar_day_filter_banner", contentType = "calendar_banner") {
                            val picked = selectedCalendarDate!!
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, end = 12.dp, top = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${picked.monthValue}月${picked.dayOfMonth}日",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                                TextButton(onClick = { selectedCalendarDate = null }) {
                                    Text("查看全月")
                                }
                            }
                        }
                    }
                }

                if (visibleDays.isEmpty()) {
                    item(key = "empty_tx", contentType = "empty_state") {
                        val (emptyTitle, emptyActionLabel, emptyAction) = when {
                            hasAnySubFilter -> Triple("无匹配账单", "清空筛选", clearAllFilters)
                            selectedCalendarDate != null -> Triple(
                                "${selectedCalendarDate!!.monthValue}月${selectedCalendarDate!!.dayOfMonth}日暂无账单",
                                "记一笔",
                                onOpenQuickRecord
                            )
                            else -> Triple("暂无账单", "记一笔", onOpenQuickRecord)
                        }
                        EmptyStateCard(
                            icon = Icons.AutoMirrored.Rounded.ReceiptLong,
                            title = emptyTitle,
                            actionLabel = emptyActionLabel,
                            onAction = emptyAction,
                            modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp)
                        )
                    }
                } else {
                    visibleDays.forEachIndexed { dayIndex, group ->
                        item(key = "day_hdr_${group.date}", contentType = "day_header") {
                            DaySectionHeader(
                                group = group,
                                baseCurrency = uiState.baseCurrency,
                                privacyMasked = privacyMasked,
                                modifier = Modifier.padding(
                                    top = if (dayIndex == 0) 12.dp else 16.dp,
                                    bottom = 6.dp
                                )
                            )
                        }

                        itemsIndexed(
                            items = group.transactions,
                            key = { _, tx -> tx.id },
                            contentType = { _, _ -> "tx_row" }
                        ) { idx, tx ->
                            val isFirstInDay = idx == 0
                            val isLastInDay = idx == group.transactions.lastIndex
                            val isSelected = tx.id in selectedTxIds
                            val resolvedBaseMicros = uiState.baseAmountByTxId[tx.id]
                                ?: viewModel.convertTxToDisplayBase(tx, uiState.baseCurrency, rates)
                            val cat = tx.categoryId?.let { categoryMap[it] }
                            val parentCat = cat?.parentId?.let { categoryMap[it] }
                            FlattenedTransactionRow(
                                tx = tx,
                                baseCurrency = uiState.baseCurrency,
                                convertedBaseMicros = resolvedBaseMicros,
                                refundedMicros = uiState.refundedAmountByOrigTxId[tx.id] ?: 0L,
                                account = accountMap[tx.accountId],
                                toAccount = tx.toAccountId?.let { accountMap[it] },
                                category = cat,
                                parentCategory = parentCat,
                                tagMap = tagMap,
                                privacyMasked = privacyMasked,
                                isMultiSelectMode = isMultiSelectMode,
                                isSelected = isSelected,
                                isFirstInDay = isFirstInDay,
                                isLastInDay = isLastInDay,
                                onClick = {
                                    if (isMultiSelectMode) {
                                        selectedTxIds = if (tx.id in selectedTxIds) selectedTxIds - tx.id else selectedTxIds + tx.id
                                    } else {
                                        detailTransaction = tx
                                    }
                                },
                                onLongClick = {
                                    if (!isMultiSelectMode) {
                                        isMultiSelectMode = true
                                        selectedTxIds = selectedTxIds + tx.id
                                    } else {
                                        selectedTxIds = if (tx.id in selectedTxIds) selectedTxIds - tx.id else selectedTxIds + tx.id
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        TopStatusBarScrim(
            visible = showStatusBarScrim,
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }

    detailTransaction?.let { tx ->
        val cat = tx.categoryId?.let { categoryMap[it] }
        val parentCat = cat?.parentId?.let { categoryMap[it] }
        TransactionDetailSheet(
            transaction = tx,
            account = accountMap[tx.accountId],
            toAccount = tx.toAccountId?.let { accountMap[it] },
            category = cat,
            parentCategory = parentCat,
            refundedMicros = uiState.refundedAmountByOrigTxId[tx.id] ?: 0L,
            ledgerName = ledgerMap[tx.ledgerId]?.name,
            tagMap = tagMap,
            accounts = activeAccounts,
            privacyMasked = privacyMasked,
            viewModel = viewModel,
            onConfirm = {
                viewModel.confirmTransaction(tx.id)
                detailTransaction = null
            },
            onEdit = {
                detailTransaction = null
                onEditTransaction(tx)
            },
            onRefund = {
                detailTransaction = null
                onRefundTransaction(tx)
            },
            onDelete = {
                detailTransaction = null
                deleteCandidate = tx
            },
            onDismiss = { detailTransaction = null }
        )
    }

    deleteCandidate?.let { tx ->
        ConfirmActionDialog(
            title = "删除该笔账单？",
            message = "删除「${tx.merchant.ifBlank { categoryMap[tx.categoryId]?.name ?: KairoFormats.transactionTypeLabel(tx.type) }}」将同步回滚对应账户余额。",
            confirmText = "删除",
            isDestructive = true,
            onConfirm = {
                viewModel.deleteTransaction(tx.id)
                deleteCandidate = null
            },
            onDismiss = { deleteCandidate = null }
        )
    }

    if (showBatchCategoryDialog) {
        BatchCategoryPickerDialog(
            categories = categories,
            selectedCount = selectedTxIds.size,
            onPickCategory = { catId ->
                val ids = selectedTxIds.toList()
                showBatchCategoryDialog = false
                isMultiSelectMode = false
                selectedTxIds = emptySet()
                onBatchUpdateCategory(ids, catId)
            },
            onDismiss = { showBatchCategoryDialog = false }
        )
    }

    if (showBatchDeleteConfirm) {
        ConfirmActionDialog(
            title = "批量删除 ${selectedTxIds.size} 笔账单？",
            message = "删除所选账单将同步回滚关联账户余额，此操作不可撤销。",
            confirmText = "确认删除",
            isDestructive = true,
            onConfirm = {
                val ids = selectedTxIds.toList()
                showBatchDeleteConfirm = false
                isMultiSelectMode = false
                selectedTxIds = emptySet()
                onBatchDeleteTransactions(ids)
            },
            onDismiss = { showBatchDeleteConfirm = false }
        )
    }

    if (showMonthPickerDialog) {
        YearMonthPickerDialog(
            initialYearMonth = filters.yearMonth,
            onSelect = { picked -> viewModel.setTxMonth(picked) },
            onDismiss = { showMonthPickerDialog = false }
        )
    }

    if (showMonthSummaryDialog) {
        val context = LocalContext.current
        MonthSummaryDetailDialog(
            yearMonth = filters.yearMonth,
            baseCurrency = uiState.baseCurrency,
            expenseMicros = uiState.monthExpenseBaseMicros,
            incomeMicros = uiState.monthIncomeBaseMicros,
            balanceMicros = uiState.monthBalanceBaseMicros,
            foreignCurrencySummaries = foreignCurrencySummaries,
            privacyMasked = privacyMasked,
            onCopySummary = { summaryText ->
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                clipboard?.setPrimaryClip(ClipData.newPlainText("month_summary", summaryText))
                viewModel.showMessage("已复制月度收支汇总")
            },
            onDismiss = { showMonthSummaryDialog = false }
        )
    }
}

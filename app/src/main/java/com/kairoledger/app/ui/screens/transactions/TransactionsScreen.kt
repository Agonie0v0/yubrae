package com.kairoledger.app.ui.screens.transactions

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.MarkEmailUnread
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kairoledger.app.core.model.MoneyAmount
import com.kairoledger.app.data.db.AccountEntity
import com.kairoledger.app.data.db.AliasMatchType
import com.kairoledger.app.data.db.CaptureChannel
import com.kairoledger.app.data.db.CategoryEntity
import com.kairoledger.app.data.db.ExchangeRateEntity
import com.kairoledger.app.data.db.LedgerEntity
import com.kairoledger.app.data.db.RawCaptureLogEntity
import com.kairoledger.app.data.db.ReimburseStatus
import com.kairoledger.app.data.db.TransactionEntity
import com.kairoledger.app.data.db.TransactionStatus
import com.kairoledger.app.data.db.TransactionType
import com.kairoledger.app.ui.components.CategoryIconBadge
import com.kairoledger.app.ui.components.ChannelChip
import com.kairoledger.app.ui.components.ConfirmActionDialog
import com.kairoledger.app.ui.components.EmptyStateCard
import com.kairoledger.app.ui.components.KairoFormats
import com.kairoledger.app.ui.components.KairoIcons
import com.kairoledger.app.ui.components.LocalPrivacyMasked
import com.kairoledger.app.ui.components.ReceiptThumbnailImage
import com.kairoledger.app.ui.components.ReceiptZoomPreviewDialog
import com.kairoledger.app.ui.components.YimuCard
import com.kairoledger.app.ui.components.formatPrivacyAmount
import com.kairoledger.app.ui.components.transactionAmountColor
import com.kairoledger.app.ui.theme.KairoTheme
import com.kairoledger.app.ui.theme.tabular
import com.kairoledger.app.ui.viewmodel.DayTransactionGroup
import com.kairoledger.app.ui.viewmodel.KairoMainViewModel
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import kotlin.math.abs

private enum class TxViewMode {
    LIST, CALENDAR
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun TransactionsScreen(
    viewModel: KairoMainViewModel,
    onEditTransaction: (TransactionEntity) -> Unit,
    onOpenQuickRecord: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    activeLedgerId: Long = 1L,
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
    onBatchDeleteTransactions: (List<Long>) -> Unit = {}
) {
    val uiState by viewModel.transactionsUiState.collectAsStateWithLifecycle()
    val accounts by viewModel.allAccounts.collectAsStateWithLifecycle()
    val activeAccounts by viewModel.activeAccounts.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val rates by viewModel.exchangeRates.collectAsStateWithLifecycle()
    val privacyMasked = LocalPrivacyMasked.current

    val accountMap = remember(accounts) { accounts.associateBy { it.id } }
    val categoryMap = remember(categories) { categories.associateBy { it.id } }
    val ledgerMap = remember(ledgers) { ledgers.associateBy { it.id } }
    val childrenByParent = remember(categories) {
        categories.filter { it.parentId != null }
            .groupBy { it.parentId!! }
            .mapValues { (_, list) -> list.map { it.id }.toHashSet() }
    }

    // 视图模式与日历选中日
    var viewMode by remember { mutableStateOf(TxViewMode.LIST) }
    var selectedCalendarDate by remember(uiState.filters.yearMonth) { mutableStateOf<LocalDate?>(null) }

    // 本地同步高级筛选状态（既支持外部 ViewModel 控制，也可独立即刻生效）
    var showAdvancedFilters by remember { mutableStateOf(false) }
    var localCategoryIdFilter by remember(selectedCategoryIdFilter) { mutableStateOf(selectedCategoryIdFilter) }
    var localReimburseFilter by remember(selectedReimburseFilter) { mutableStateOf(selectedReimburseFilter) }
    var localMinAmountText by remember(minAmountFilterText) { mutableStateOf(minAmountFilterText) }
    var localMaxAmountText by remember(maxAmountFilterText) { mutableStateOf(maxAmountFilterText) }

    // 批量多选模式与行内左滑快捷操作状态
    var isMultiSelectMode by remember { mutableStateOf(false) }
    var selectedTxIds by remember { mutableStateOf(emptySet<Long>()) }
    var expandedQuickActionTxId by remember { mutableStateOf<Long?>(null) }
    var showBatchCategoryDialog by remember { mutableStateOf(false) }
    var showBatchDeleteConfirm by remember { mutableStateOf(false) }

    var detailTransaction by remember { mutableStateOf<TransactionEntity?>(null) }
    var deleteCandidate by remember { mutableStateOf<TransactionEntity?>(null) }
    var showAccountFilterMenu by remember { mutableStateOf(false) }
    var showChannelFilterMenu by remember { mutableStateOf(false) }

    val minAmountMicros = remember(localMinAmountText) {
        localMinAmountText.takeIf { it.isNotBlank() }?.let { MoneyAmount.parseToMicros(it) }?.takeIf { it >= 0L }
    }
    val maxAmountMicros = remember(localMaxAmountText) {
        localMaxAmountText.takeIf { it.isNotBlank() }?.let { MoneyAmount.parseToMicros(it) }?.takeIf { it >= 0L }
    }

    // 基于 uiState.groupedDays 进行账本 / 分类 / 报销 / 金额区间二次过滤并重算天内小计
    val filteredMonthDays = remember(
        uiState.groupedDays,
        uiState.baseCurrency,
        rates,
        activeLedgerId,
        localCategoryIdFilter,
        localReimburseFilter,
        minAmountMicros,
        maxAmountMicros,
        childrenByParent
    ) {
        val targetCatIds = localCategoryIdFilter?.let { filterId ->
            setOf(filterId) + (childrenByParent[filterId] ?: emptySet())
        }
        uiState.groupedDays.mapNotNull { group ->
            val matchedTxs = group.transactions.filter { tx ->
                val matchLedger = activeLedgerId <= 0L || tx.ledgerId == activeLedgerId
                val matchCat = targetCatIds == null || tx.categoryId in targetCatIds
                val matchReimburse = localReimburseFilter == "ALL" || tx.reimburseStatus == localReimburseFilter
                val matchAmount = if (minAmountMicros != null || maxAmountMicros != null) {
                    val baseAmt = abs(viewModel.convertTxToDisplayBase(tx, uiState.baseCurrency, rates))
                    (minAmountMicros == null || baseAmt >= minAmountMicros) &&
                        (maxAmountMicros == null || baseAmt <= maxAmountMicros)
                } else {
                    true
                }
                matchLedger && matchCat && matchReimburse && matchAmount
            }
            if (matchedTxs.isEmpty()) {
                null
            } else {
                var dayExp = 0L
                var dayInc = 0L
                matchedTxs.forEach { tx ->
                    if (tx.status == TransactionStatus.CONFIRMED && !tx.excludeFromStats) {
                        val amt = viewModel.convertTxToDisplayBase(tx, uiState.baseCurrency, rates)
                        when (tx.type) {
                            TransactionType.EXPENSE -> dayExp += amt
                            TransactionType.INCOME -> if (tx.refundOfTxId == null) {
                                dayInc += amt
                            }
                        }
                    }
                }
                DayTransactionGroup(
                    date = group.date,
                    transactions = matchedTxs,
                    dayExpenseBaseMicros = dayExp,
                    dayIncomeBaseMicros = dayInc
                )
            }
        }
    }

    // 日历视图单日下钻过滤
    val visibleDays = remember(filteredMonthDays, viewMode, selectedCalendarDate) {
        if (viewMode == TxViewMode.CALENDAR && selectedCalendarDate != null) {
            filteredMonthDays.filter { it.date == selectedCalendarDate }
        } else {
            filteredMonthDays
        }
    }

    val hasAnySubFilter = uiState.filters.query.isNotBlank() ||
        uiState.filters.currencyFilter != "ALL" ||
        uiState.filters.accountIdFilter != null ||
        uiState.filters.channelFilter != "ALL" ||
        localCategoryIdFilter != null ||
        localReimburseFilter != "ALL" ||
        minAmountMicros != null ||
        maxAmountMicros != null

    val advancedFilterCount = listOfNotNull(
        localCategoryIdFilter,
        localReimburseFilter.takeIf { it != "ALL" },
        minAmountMicros ?: maxAmountMicros
    ).size

    // 月度汇总（若筛选条件为空且存在当前账本过滤/退款冲减，则精确重算当月有效收支）
    val (displayMonthExpense, displayMonthIncome) = remember(
        filteredMonthDays,
        uiState.monthExpenseBaseMicros,
        uiState.monthIncomeBaseMicros,
        uiState.baseCurrency,
        rates,
        hasAnySubFilter,
        activeLedgerId
    ) {
        if (!hasAnySubFilter) {
            var exp = 0L
            var inc = 0L
            filteredMonthDays.forEach { day ->
                day.transactions.forEach { tx ->
                    if (tx.status == TransactionStatus.CONFIRMED && !tx.excludeFromStats) {
                        val amt = viewModel.convertTxToDisplayBase(tx, uiState.baseCurrency, rates)
                        when {
                            tx.type == TransactionType.EXPENSE -> exp += amt
                            tx.type == TransactionType.INCOME && tx.refundOfTxId != null -> exp -= amt
                            tx.type == TransactionType.INCOME -> inc += amt
                        }
                    }
                }
            }
            exp.coerceAtLeast(0L) to inc
        } else {
            uiState.monthExpenseBaseMicros to uiState.monthIncomeBaseMicros
        }
    }

    val allVisibleTxIds = remember(visibleDays) {
        visibleDays.flatMap { day -> day.transactions.map { it.id } }
    }

    val activeLedgerLabel = remember(activeLedgerId, ledgerMap) {
        when {
            activeLedgerId == 0L -> "全部账本"
            else -> ledgerMap[activeLedgerId]?.name
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 长按多选模式顶部悬浮批量工具栏
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
                modifier = Modifier.padding(
                    top = contentPadding.calculateTopPadding() + 4.dp,
                    start = 16.dp,
                    end = 16.dp,
                    bottom = 4.dp
                )
            )
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(
                top = if (isMultiSelectMode) 4.dp else contentPadding.calculateTopPadding() + 8.dp,
                bottom = contentPadding.calculateBottomPadding() + 88.dp,
                start = 16.dp,
                end = 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 顶部月份切换器
            item(key = "month_switcher") {
                MonthSwitcherBar(
                    yearMonth = uiState.filters.yearMonth,
                    activeLedgerName = activeLedgerLabel,
                    onPrev = { viewModel.shiftTxMonth(-1) },
                    onNext = { viewModel.shiftTxMonth(1) },
                    onResetCurrent = { viewModel.setTxMonth(YearMonth.now()) }
                )
            }

            // 一木清新绿月度汇总卡（含列表/日历一键切换按钮）
            item(key = "monthly_overview") {
                MonthlyOverviewCard(
                    baseCurrency = uiState.baseCurrency,
                    expenseMicros = displayMonthExpense,
                    incomeMicros = displayMonthIncome,
                    balanceMicros = displayMonthIncome - displayMonthExpense,
                    privacyMasked = privacyMasked,
                    viewMode = viewMode,
                    onToggleViewMode = {
                        viewMode = if (viewMode == TxViewMode.LIST) TxViewMode.CALENDAR else TxViewMode.LIST
                        if (viewMode == TxViewMode.LIST) {
                            selectedCalendarDate = null
                        }
                    }
                )
            }

            // 待确认收件箱横幅
            if (uiState.pendingCount > 0) {
                item(key = "pending_inbox") {
                    PendingInboxBanner(
                        pendingCount = uiState.pendingCount,
                        expanded = uiState.filters.pendingExpanded,
                        pendingTransactions = uiState.pendingTransactions,
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
                        onDeleteOne = { deleteCandidate = it }
                    )
                }
            }

            // 搜索与基础/高级筛选栏
            item(key = "search_filter") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = uiState.filters.query,
                        onValueChange = { viewModel.setTxQuery(it) },
                        placeholder = { Text("搜索商户、备注或标签...") },
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                        trailingIcon = {
                            if (uiState.filters.query.isNotEmpty()) {
                                IconButton(onClick = { viewModel.setTxQuery("") }) {
                                    Icon(Icons.Rounded.Close, contentDescription = "清除搜索")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        item(key = "advanced_filter_toggle") {
                            FilterChip(
                                selected = showAdvancedFilters || advancedFilterCount > 0,
                                onClick = { showAdvancedFilters = !showAdvancedFilters },
                                leadingIcon = {
                                    Icon(Icons.Rounded.FilterList, null, modifier = Modifier.size(16.dp))
                                },
                                trailingIcon = {
                                    Icon(
                                        imageVector = if (showAdvancedFilters) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                label = {
                                    Text(if (advancedFilterCount > 0) "高级筛选 ($advancedFilterCount)" else "高级筛选")
                                }
                            )
                        }

                        val currencyOptions = listOf("ALL" to "全部币种", "CNY" to "CNY", "USD" to "USD", "EUR" to "EUR", "GBP" to "GBP")
                        items(currencyOptions, key = { it.first }) { (code, label) ->
                            FilterChip(
                                selected = uiState.filters.currencyFilter == code,
                                onClick = { viewModel.setTxCurrencyFilter(code) },
                                label = { Text(label) }
                            )
                        }

                        item(key = "account_filter") {
                            Box {
                                val selectedAcctName = accountMap[uiState.filters.accountIdFilter]?.name ?: "全部账户"
                                FilterChip(
                                    selected = uiState.filters.accountIdFilter != null,
                                    onClick = { showAccountFilterMenu = true },
                                    leadingIcon = {
                                        Icon(Icons.Rounded.AccountBalanceWallet, null, modifier = Modifier.size(16.dp))
                                    },
                                    trailingIcon = {
                                        Icon(Icons.Rounded.ArrowDropDown, null, modifier = Modifier.size(18.dp))
                                    },
                                    label = { Text(selectedAcctName, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                                )
                                DropdownMenu(
                                    expanded = showAccountFilterMenu,
                                    onDismissRequest = { showAccountFilterMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("全部账户") },
                                        onClick = {
                                            viewModel.setTxAccountFilter(null)
                                            showAccountFilterMenu = false
                                        }
                                    )
                                    accounts.forEach { acc ->
                                        DropdownMenuItem(
                                            text = { Text("${acc.name} (${acc.currencyCode})") },
                                            onClick = {
                                                viewModel.setTxAccountFilter(acc.id)
                                                showAccountFilterMenu = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        item(key = "channel_filter") {
                            Box {
                                val channelLabel = if (uiState.filters.channelFilter == "ALL") {
                                    "全部来源"
                                } else {
                                    KairoFormats.channelLabel(uiState.filters.channelFilter)
                                }
                                FilterChip(
                                    selected = uiState.filters.channelFilter != "ALL",
                                    onClick = { showChannelFilterMenu = true },
                                    leadingIcon = {
                                        Icon(Icons.Rounded.Tune, null, modifier = Modifier.size(16.dp))
                                    },
                                    trailingIcon = {
                                        Icon(Icons.Rounded.ArrowDropDown, null, modifier = Modifier.size(18.dp))
                                    },
                                    label = { Text(channelLabel) }
                                )
                                DropdownMenu(
                                    expanded = showChannelFilterMenu,
                                    onDismissRequest = { showChannelFilterMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("全部来源") },
                                        onClick = {
                                            viewModel.setTxChannelFilter("ALL")
                                            showChannelFilterMenu = false
                                        }
                                    )
                                    listOf(
                                        CaptureChannel.MANUAL,
                                        CaptureChannel.NOTIFICATION,
                                        CaptureChannel.SMS,
                                        CaptureChannel.SCREEN,
                                        CaptureChannel.AUTO_ACCOUNTING,
                                        CaptureChannel.AI,
                                        CaptureChannel.IMPORT,
                                        CaptureChannel.RECURRING,
                                        CaptureChannel.SHARE
                                    ).forEach { ch ->
                                        DropdownMenuItem(
                                            text = { Text(KairoFormats.channelLabel(ch)) },
                                            onClick = {
                                                viewModel.setTxChannelFilter(ch)
                                                showChannelFilterMenu = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 高级筛选折叠区：分类 / 报销状态 / 金额区间
                    AnimatedVisibility(visible = showAdvancedFilters) {
                        AdvancedFilterPanel(
                            categories = categories,
                            selectedCategoryId = localCategoryIdFilter,
                            onSelectCategory = { catId ->
                                localCategoryIdFilter = catId
                                onSelectCategoryFilter(catId)
                            },
                            selectedReimburseStatus = localReimburseFilter,
                            onSelectReimburseStatus = { status ->
                                localReimburseFilter = status
                                onSelectReimburseFilter(status)
                            },
                            minAmountText = localMinAmountText,
                            maxAmountText = localMaxAmountText,
                            baseCurrency = uiState.baseCurrency,
                            onAmountRangeChange = { minStr, maxStr ->
                                localMinAmountText = minStr
                                localMaxAmountText = maxStr
                                onAmountRangeFilterChange(minStr, maxStr)
                            },
                            onClearAdvanced = {
                                localCategoryIdFilter = null
                                localReimburseFilter = "ALL"
                                localMinAmountText = ""
                                localMaxAmountText = ""
                                onSelectCategoryFilter(null)
                                onSelectReimburseFilter("ALL")
                                onAmountRangeFilterChange("", "")
                            }
                        )
                    }
                }
            }

            // 月度日历视图卡片
            if (viewMode == TxViewMode.CALENDAR) {
                item(key = "calendar_grid") {
                    MonthlyCalendarCard(
                        yearMonth = uiState.filters.yearMonth,
                        monthDays = filteredMonthDays,
                        selectedDate = selectedCalendarDate,
                        privacyMasked = privacyMasked,
                        onSelectDate = { tappedDate ->
                            selectedCalendarDate = if (selectedCalendarDate == tappedDate) null else tappedDate
                        }
                    )
                }

                if (selectedCalendarDate != null) {
                    item(key = "calendar_day_filter_banner") {
                        val picked = selectedCalendarDate!!
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "已选 ${picked.monthValue}月${picked.dayOfMonth}日流水",
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

            // 流水列表或空状态
            if (visibleDays.isEmpty()) {
                item(key = "empty_tx") {
                    EmptyStateCard(
                        icon = Icons.Rounded.ReceiptLong,
                        title = if (selectedCalendarDate != null) {
                            "${selectedCalendarDate!!.monthValue}月${selectedCalendarDate!!.dayOfMonth}日暂无流水"
                        } else {
                            "本月暂无符合条件的流水"
                        },
                        subtitle = if (selectedCalendarDate != null) {
                            "点击「查看全月」查看其他日期，或记一笔新账单。"
                        } else {
                            "点击右下角「记一笔」，或前往「自动化」开启通知/短信自动记账与账单导入。"
                        },
                        actionLabel = "立即记一笔",
                        onAction = onOpenQuickRecord
                    )
                }
            } else {
                items(visibleDays, key = { it.date.toString() }) { group ->
                    DayTransactionCard(
                        group = group,
                        baseCurrency = uiState.baseCurrency,
                        accountMap = accountMap,
                        categoryMap = categoryMap,
                        rates = rates,
                        privacyMasked = privacyMasked,
                        viewModel = viewModel,
                        isMultiSelectMode = isMultiSelectMode,
                        selectedTxIds = selectedTxIds,
                        expandedQuickActionTxId = expandedQuickActionTxId,
                        onToggleSelectTx = { txId ->
                            selectedTxIds = if (txId in selectedTxIds) {
                                selectedTxIds - txId
                            } else {
                                selectedTxIds + txId
                            }
                        },
                        onEnterMultiSelect = { txId ->
                            expandedQuickActionTxId = null
                            isMultiSelectMode = true
                            selectedTxIds = selectedTxIds + txId
                        },
                        onToggleQuickAction = { txId ->
                            expandedQuickActionTxId = if (expandedQuickActionTxId == txId) null else txId
                        },
                        onEditTx = { tx ->
                            expandedQuickActionTxId = null
                            onEditTransaction(tx)
                        },
                        onRefundTx = { tx ->
                            expandedQuickActionTxId = null
                            onRefundTransaction(tx)
                        },
                        onDeleteTx = { tx ->
                            expandedQuickActionTxId = null
                            deleteCandidate = tx
                        },
                        onClickTransaction = { tx ->
                            expandedQuickActionTxId = null
                            detailTransaction = tx
                        }
                    )
                }
            }
        }
    }

    // 交易详情弹窗（含小票附件、退款标识、不计收支标识、原始报文溯源）
    detailTransaction?.let { tx ->
        TransactionDetailSheet(
            transaction = tx,
            account = accountMap[tx.accountId],
            toAccount = tx.toAccountId?.let { accountMap[it] },
            category = tx.categoryId?.let { categoryMap[it] },
            ledgerName = ledgerMap[tx.ledgerId]?.name,
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
            title = "删除该笔流水？",
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
            title = "批量删除 ${selectedTxIds.size} 笔流水？",
            message = "删除所选流水将同步回滚关联账户余额，此操作不可撤销。",
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
}

@Composable
private fun BatchSelectionToolbar(
    selectedCount: Int,
    totalVisibleCount: Int,
    allSelected: Boolean,
    onSelectAllToggle: () -> Unit,
    onInvertSelection: () -> Unit,
    onBatchCategory: () -> Unit,
    onBatchDelete: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    YimuCard(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        cornerRadius = 16.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    IconButton(onClick = onExit, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "退出多选",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Text(
                        text = "已选 $selectedCount / $totalVisibleCount 笔",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = onSelectAllToggle,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Icon(Icons.Rounded.SelectAll, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(if (allSelected) "全不选" else "全选")
                    }
                    TextButton(
                        onClick = onInvertSelection,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text("反选")
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onBatchCategory,
                    enabled = selectedCount > 0,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Rounded.Edit, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("批量改分类")
                }
                Button(
                    onClick = onBatchDelete,
                    enabled = selectedCount > 0,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Rounded.Delete, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("批量删除")
                }
            }
        }
    }
}

@Composable
private fun MonthSwitcherBar(
    yearMonth: YearMonth,
    activeLedgerName: String?,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onResetCurrent: () -> Unit
) {
    val label = yearMonth.format(DateTimeFormatter.ofPattern("yyyy年M月"))
    val isCurrent = yearMonth == YearMonth.now()
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            IconButton(onClick = onPrev) {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "上个月")
            }
            Text(
                text = label,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = onNext) {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "下个月")
            }
            if (!activeLedgerName.isNullOrBlank()) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.MenuBook,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = activeLedgerName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
        if (!isCurrent) {
            TextButton(onClick = onResetCurrent) {
                Text("回到本月")
            }
        }
    }
}

@Composable
private fun MonthlyOverviewCard(
    baseCurrency: String,
    expenseMicros: Long,
    incomeMicros: Long,
    balanceMicros: Long,
    privacyMasked: Boolean,
    viewMode: TxViewMode,
    onToggleViewMode: () -> Unit
) {
    val semantic = KairoTheme.semantic
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.58f),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.78f),
                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.48f)
                        )
                    )
                )
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "本月收支总览",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold
                        )
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f)
                        ) {
                            Text(
                                text = "$baseCurrency 折算",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }

                    // 列表视图 / 日历视图 一键切换按钮
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)),
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable(onClick = onToggleViewMode)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = if (viewMode == TxViewMode.LIST) {
                                    Icons.Rounded.CalendarMonth
                                } else {
                                    Icons.AutoMirrored.Rounded.ViewList
                                },
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = if (viewMode == TxViewMode.LIST) "日历视图" else "列表视图",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Column {
                    Text(
                        text = "月度结余",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f)
                    )
                    Text(
                        text = formatPrivacyAmount(
                            privacyMasked,
                            MoneyAmount.format(balanceMicros, baseCurrency, showSign = true)
                        ),
                        style = MaterialTheme.typography.headlineMedium.tabular(),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                            Text(
                                text = "月支出",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = formatPrivacyAmount(privacyMasked, MoneyAmount.format(expenseMicros, baseCurrency)),
                                style = MaterialTheme.typography.titleMedium.tabular(),
                                color = semantic.expense,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                            Text(
                                text = "月收入",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = formatPrivacyAmount(privacyMasked, MoneyAmount.format(incomeMicros, baseCurrency)),
                                style = MaterialTheme.typography.titleMedium.tabular(),
                                color = semantic.income,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthlyCalendarCard(
    yearMonth: YearMonth,
    monthDays: List<DayTransactionGroup>,
    selectedDate: LocalDate?,
    privacyMasked: Boolean,
    onSelectDate: (LocalDate) -> Unit
) {
    val semantic = KairoTheme.semantic
    val today = remember { LocalDate.now() }
    val dayGroupMap = remember(monthDays) { monthDays.associateBy { it.date } }
    val maxDayExpense = remember(monthDays) {
        monthDays.maxOfOrNull { it.dayExpenseBaseMicros } ?: 0L
    }

    val daysInMonth = yearMonth.lengthOfMonth()
    val leadingBlanks = yearMonth.atDay(1).dayOfWeek.value - 1
    val totalSlots = leadingBlanks + daysInMonth
    val rows = (totalSlots + 6) / 7

    YimuCard(cornerRadius = 18.dp) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "收支日历",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "点击日期查看当日明细",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 星期表头（一 ~ 日）
            Row(modifier = Modifier.fillMaxWidth()) {
                val weekHeaders = listOf("一", "二", "三", "四", "五", "六", "日")
                weekHeaders.forEachIndexed { idx, label ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (idx >= 5) {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            for (row in 0 until rows) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    for (col in 0 until 7) {
                        val slotIndex = row * 7 + col
                        val dayNum = slotIndex - leadingBlanks + 1
                        if (dayNum < 1 || dayNum > daysInMonth) {
                            Spacer(modifier = Modifier.weight(1f).height(56.dp))
                        } else {
                            val date = yearMonth.atDay(dayNum)
                            val group = dayGroupMap[date]
                            val exp = group?.dayExpenseBaseMicros ?: 0L
                            val inc = group?.dayIncomeBaseMicros ?: 0L
                            val isSelected = selectedDate == date
                            val isToday = date == today

                            val heatAlpha = if (exp > 0L && maxDayExpense > 0L) {
                                (0.05f + 0.14f * (exp.toFloat() / maxDayExpense.toFloat())).coerceIn(0.05f, 0.20f)
                            } else {
                                0f
                            }
                            val cellBg = when {
                                isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
                                exp > 0L -> semantic.expense.copy(alpha = heatAlpha)
                                inc > 0L -> semantic.income.copy(alpha = 0.08f)
                                else -> MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.55f)
                            }
                            val cellBorder = when {
                                isSelected -> BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                                isToday -> BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f))
                                else -> null
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(56.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(cellBg)
                                    .then(
                                        if (cellBorder != null) Modifier.border(cellBorder, RoundedCornerShape(10.dp)) else Modifier
                                    )
                                    .clickable { onSelectDate(date) }
                                    .padding(horizontal = 2.dp, vertical = 4.dp)
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = dayNum.toString(),
                                        style = MaterialTheme.typography.labelMedium.tabular(),
                                        fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isToday || isSelected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        }
                                    )

                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(1.dp)
                                    ) {
                                        if (exp > 0L) {
                                            Text(
                                                text = if (privacyMasked) "••" else "-${formatCompactCalendarMicros(exp)}",
                                                fontSize = 9.sp,
                                                lineHeight = 10.sp,
                                                color = semantic.expense,
                                                maxLines = 1,
                                                softWrap = false,
                                                overflow = TextOverflow.Clip
                                            )
                                        }
                                        if (inc > 0L) {
                                            Text(
                                                text = if (privacyMasked) "••" else "+${formatCompactCalendarMicros(inc)}",
                                                fontSize = 9.sp,
                                                lineHeight = 10.sp,
                                                color = semantic.income,
                                                maxLines = 1,
                                                softWrap = false,
                                                overflow = TextOverflow.Clip
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatCompactCalendarMicros(micros: Long): String {
    val bd = MoneyAmount.microsToBigDecimal(abs(micros))
    val tenThousand = BigDecimal(10_000)
    return if (bd >= tenThousand) {
        bd.divide(tenThousand, 1, RoundingMode.HALF_UP)
            .stripTrailingZeros()
            .toPlainString() + "w"
    } else if (bd >= BigDecimal(100)) {
        bd.setScale(0, RoundingMode.HALF_UP).toPlainString()
    } else {
        bd.setScale(1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AdvancedFilterPanel(
    categories: List<CategoryEntity>,
    selectedCategoryId: Long?,
    onSelectCategory: (Long?) -> Unit,
    selectedReimburseStatus: String,
    onSelectReimburseStatus: (String) -> Unit,
    minAmountText: String,
    maxAmountText: String,
    baseCurrency: String,
    onAmountRangeChange: (String, String) -> Unit,
    onClearAdvanced: () -> Unit
) {
    val parentCategories = remember(categories) {
        categories.filter { it.parentId == null }.sortedWith(compareBy({ it.type }, { it.sortOrder }, { it.id }))
    }
    val selectedCat = categories.firstOrNull { it.id == selectedCategoryId }
    val activeParentId = selectedCat?.parentId ?: selectedCat?.id
    val subCategories = remember(categories, activeParentId) {
        if (activeParentId != null) categories.filter { it.parentId == activeParentId } else emptyList()
    }

    YimuCard(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        cornerRadius = 16.dp
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "按分类筛选",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(
                    onClick = onClearAdvanced,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    modifier = Modifier.height(26.dp)
                ) {
                    Text("清空筛选", style = MaterialTheme.typography.labelSmall)
                }
            }

            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                item(key = "cat_all") {
                    FilterChip(
                        selected = selectedCategoryId == null,
                        onClick = { onSelectCategory(null) },
                        label = { Text("全部分类") }
                    )
                }
                items(parentCategories, key = { it.id }) { cat ->
                    FilterChip(
                        selected = activeParentId == cat.id,
                        onClick = {
                            onSelectCategory(if (selectedCategoryId == cat.id) null else cat.id)
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = KairoIcons.iconFor(cat.icon),
                                contentDescription = null,
                                tint = Color(cat.colorHex),
                                modifier = Modifier.size(15.dp)
                            )
                        },
                        label = { Text(cat.name) }
                    )
                }
            }

            if (subCategories.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    subCategories.forEach { sub ->
                        FilterChip(
                            selected = selectedCategoryId == sub.id,
                            onClick = {
                                onSelectCategory(if (selectedCategoryId == sub.id) activeParentId else sub.id)
                            },
                            label = { Text(sub.name, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }

            Text(
                text = "报销状态",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val options = listOf(
                    "ALL" to "全部",
                    ReimburseStatus.NONE to "不报销",
                    ReimburseStatus.PENDING to "待报销",
                    ReimburseStatus.REIMBURSED to "已报销"
                )
                items(options, key = { it.first }) { (code, label) ->
                    FilterChip(
                        selected = selectedReimburseStatus == code,
                        onClick = { onSelectReimburseStatus(code) },
                        label = { Text(label) }
                    )
                }
            }

            Text(
                text = "金额区间 ($baseCurrency 折算)",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = minAmountText,
                    onValueChange = { onAmountRangeChange(it, maxAmountText) },
                    placeholder = { Text("最低金额") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    textStyle = MaterialTheme.typography.bodyMedium.tabular(),
                    modifier = Modifier.weight(1f)
                )
                Text("~", color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = maxAmountText,
                    onValueChange = { onAmountRangeChange(minAmountText, it) },
                    placeholder = { Text("最高金额") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    textStyle = MaterialTheme.typography.bodyMedium.tabular(),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun PendingInboxBanner(
    pendingCount: Int,
    expanded: Boolean,
    pendingTransactions: List<TransactionEntity>,
    accountMap: Map<Long, AccountEntity>,
    categoryMap: Map<Long, CategoryEntity>,
    baseCurrency: String,
    rates: List<ExchangeRateEntity>,
    privacyMasked: Boolean,
    viewModel: KairoMainViewModel,
    onToggleExpand: () -> Unit,
    onConfirmAll: () -> Unit,
    onConfirmOne: (TransactionEntity) -> Unit,
    onEditOne: (TransactionEntity) -> Unit,
    onDeleteOne: (TransactionEntity) -> Unit
) {
    val semantic = KairoTheme.semantic
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = semantic.warningContainer.copy(alpha = 0.7f),
        border = BorderStroke(1.dp, semantic.warning.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MarkEmailUnread,
                        contentDescription = null,
                        tint = semantic.warning
                    )
                    Column {
                        Text(
                            text = "待核对账单 ($pendingCount)",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "来自自动采集且待您复核确认",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onConfirmAll) {
                        Icon(Icons.Rounded.DoneAll, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("全部确认")
                    }
                    IconButton(onClick = onToggleExpand) {
                        Icon(
                            imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                            contentDescription = if (expanded) "收起" else "展开"
                        )
                    }
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    pendingTransactions.forEach { tx ->
                        val cat = tx.categoryId?.let { categoryMap[it] }
                        val acc = accountMap[tx.accountId]
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = tx.merchant.ifBlank { cat?.name ?: "自动捕获账单" },
                                            style = MaterialTheme.typography.titleSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${acc?.name ?: "未匹配账户"} · ${KairoFormats.formatDateTime(tx.timestamp)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        val signedMicros = if (tx.type == TransactionType.EXPENSE) -tx.origAmountMicros else tx.origAmountMicros
                                        Text(
                                            text = formatPrivacyAmount(
                                                privacyMasked,
                                                MoneyAmount.format(
                                                    signedMicros,
                                                    tx.origCurrency,
                                                    showSign = tx.type == TransactionType.INCOME,
                                                    showCurrencyCode = true
                                                )
                                            ),
                                            style = MaterialTheme.typography.titleMedium.tabular(),
                                            color = transactionAmountColor(tx.type),
                                            fontWeight = FontWeight.Bold
                                        )
                                        if (!tx.origCurrency.equals(baseCurrency, true)) {
                                            val baseAmt = viewModel.convertTxToDisplayBase(tx, baseCurrency, rates)
                                            Text(
                                                text = formatPrivacyAmount(privacyMasked, "≈ ${MoneyAmount.format(baseAmt, baseCurrency)}"),
                                                style = MaterialTheme.typography.labelSmall.tabular(),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    ChannelChip(channel = tx.sourceType)
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        TextButton(
                                            onClick = { onDeleteOne(tx) },
                                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Text("删除")
                                        }
                                        OutlinedButton(
                                            onClick = { onEditOne(tx) },
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                                        ) {
                                            Text("编辑")
                                        }
                                        Button(
                                            onClick = { onConfirmOne(tx) },
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp)
                                        ) {
                                            Text("确认")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayTransactionCard(
    group: DayTransactionGroup,
    baseCurrency: String,
    accountMap: Map<Long, AccountEntity>,
    categoryMap: Map<Long, CategoryEntity>,
    rates: List<ExchangeRateEntity>,
    privacyMasked: Boolean,
    viewModel: KairoMainViewModel,
    isMultiSelectMode: Boolean,
    selectedTxIds: Set<Long>,
    expandedQuickActionTxId: Long?,
    onToggleSelectTx: (Long) -> Unit,
    onEnterMultiSelect: (Long) -> Unit,
    onToggleQuickAction: (Long) -> Unit,
    onEditTx: (TransactionEntity) -> Unit,
    onRefundTx: (TransactionEntity) -> Unit,
    onDeleteTx: (TransactionEntity) -> Unit,
    onClickTransaction: (TransactionEntity) -> Unit
) {
    val semantic = KairoTheme.semantic
    YimuCard(cornerRadius = 18.dp) {
        Column(modifier = Modifier.padding(vertical = 10.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = KairoFormats.formatDayHeader(group.date),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (group.dayIncomeBaseMicros > 0L) {
                        Text(
                            text = formatPrivacyAmount(
                                privacyMasked,
                                "收 ${MoneyAmount.format(group.dayIncomeBaseMicros, baseCurrency)}"
                            ),
                            style = MaterialTheme.typography.labelMedium.tabular(),
                            color = semantic.income
                        )
                    }
                    if (group.dayExpenseBaseMicros > 0L) {
                        Text(
                            text = formatPrivacyAmount(
                                privacyMasked,
                                "支 ${MoneyAmount.format(group.dayExpenseBaseMicros, baseCurrency)}"
                            ),
                            style = MaterialTheme.typography.labelMedium.tabular(),
                            color = semantic.expense
                        )
                    }
                }
            }

            group.transactions.forEachIndexed { idx, tx ->
                key(tx.id) {
                    if (idx > 0) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 14.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                        )
                    }

                    val isSelected = tx.id in selectedTxIds
                    val quickExpanded = expandedQuickActionTxId == tx.id && !isMultiSelectMode
                    val dismissState = rememberSwipeToDismissBoxState(
                        confirmValueChange = { value ->
                            if (value == SwipeToDismissBoxValue.EndToStart) {
                                onToggleQuickAction(tx.id)
                            }
                            // 永远不直接移除条目，而是通过快捷栏执行编辑/退款/删除，防止误滑误删
                            false
                        },
                        positionalThreshold = { totalDistance -> totalDistance * 0.30f }
                    )

                    SwipeToDismissBox(
                        state = dismissState,
                        enableDismissFromStartToEnd = false,
                        enableDismissFromEndToStart = !isMultiSelectMode,
                        gesturesEnabled = !isMultiSelectMode,
                        backgroundContent = {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                                    .padding(horizontal = 18.dp),
                                contentAlignment = Alignment.CenterEnd
                            ) {
                                Text(
                                    text = "编辑 / 退款 / 删除",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (isSelected) {
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerLowest
                                    }
                                )
                        ) {
                            TransactionRowItem(
                                tx = tx,
                                baseCurrency = baseCurrency,
                                account = accountMap[tx.accountId],
                                toAccount = tx.toAccountId?.let { accountMap[it] },
                                category = tx.categoryId?.let { categoryMap[it] },
                                rates = rates,
                                privacyMasked = privacyMasked,
                                viewModel = viewModel,
                                isMultiSelectMode = isMultiSelectMode,
                                isSelected = isSelected,
                                onClick = {
                                    if (isMultiSelectMode) {
                                        onToggleSelectTx(tx.id)
                                    } else {
                                        onClickTransaction(tx)
                                    }
                                },
                                onLongClick = {
                                    if (!isMultiSelectMode) {
                                        onEnterMultiSelect(tx.id)
                                    } else {
                                        onToggleSelectTx(tx.id)
                                    }
                                }
                            )

                            // 左滑触发展开的行内快捷操作栏
                            AnimatedVisibility(visible = quickExpanded) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                                        .padding(horizontal = 14.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedButton(
                                        onClick = { onEditTx(tx) },
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Icon(Icons.Rounded.Edit, null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("编辑", style = MaterialTheme.typography.labelMedium)
                                    }
                                    if (tx.type == TransactionType.EXPENSE) {
                                        OutlinedButton(
                                            onClick = { onRefundTx(tx) },
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                                            modifier = Modifier.height(32.dp)
                                        ) {
                                            Icon(Icons.Rounded.Replay, null, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("退款", style = MaterialTheme.typography.labelMedium)
                                        }
                                    }
                                    Button(
                                        onClick = { onDeleteTx(tx) },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.error,
                                            contentColor = MaterialTheme.colorScheme.onError
                                        ),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Icon(Icons.Rounded.Delete, null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("删除", style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TransactionRowItem(
    tx: TransactionEntity,
    baseCurrency: String,
    account: AccountEntity?,
    toAccount: AccountEntity?,
    category: CategoryEntity?,
    rates: List<ExchangeRateEntity>,
    privacyMasked: Boolean,
    viewModel: KairoMainViewModel,
    isMultiSelectMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val semantic = KairoTheme.semantic
    val iconKey = when (tx.type) {
        TransactionType.TRANSFER -> "swap_horiz"
        TransactionType.ADJUST -> "tune"
        else -> category?.icon ?: "category"
    }
    val iconColorHex = when (tx.type) {
        TransactionType.TRANSFER -> 0xFF3B82F6L
        TransactionType.ADJUST -> 0xFF6D28D9L
        else -> category?.colorHex ?: 0xFF2AA876L
    }
    val primaryTitle = when (tx.type) {
        TransactionType.TRANSFER -> "转账 · ${account?.name ?: "账户"} → ${toAccount?.name ?: "目标账户"}"
        TransactionType.ADJUST -> "余额校准 · ${account?.name ?: "账户"}"
        else -> category?.name ?: KairoFormats.transactionTypeLabel(tx.type)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AnimatedVisibility(visible = isMultiSelectMode) {
            Row {
                Icon(
                    imageVector = if (isSelected) Icons.Rounded.CheckBox else Icons.Rounded.CheckBoxOutlineBlank,
                    contentDescription = if (isSelected) "已选中" else "未选中",
                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
            }
        }

        CategoryIconBadge(
            iconKey = iconKey,
            colorHex = iconColorHex,
            size = 40.dp,
            iconSize = 20.dp,
            selected = isMultiSelectMode && isSelected
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = if (tx.merchant.isNotBlank() && tx.type != TransactionType.TRANSFER) {
                        "$primaryTitle · ${tx.merchant}"
                    } else {
                        primaryTitle
                    },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (tx.status == TransactionStatus.PENDING) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = semantic.warningContainer
                    ) {
                        Text(
                            text = "待确认",
                            style = MaterialTheme.typography.labelSmall,
                            color = semantic.warning,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                }
                if (tx.refundOfTxId != null) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "退款",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                }
                if (tx.excludeFromStats) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Text(
                            text = "不计收支",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "${account?.name ?: "账户"} · ${KairoFormats.formatTime(tx.timestamp)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                ChannelChip(channel = tx.sourceType)
                if (tx.reimburseStatus != ReimburseStatus.NONE) {
                    Text(
                        text = if (tx.reimburseStatus == ReimburseStatus.PENDING) "待报销" else "已报销",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            if (tx.note.isNotBlank()) {
                Text(
                    text = tx.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        // 双行金额展示：外币主行显示原币种，副行显示主币种冻结折算（均尊重隐私打码）
        Column(horizontalAlignment = Alignment.End) {
            val signedMicros = when (tx.type) {
                TransactionType.EXPENSE -> -abs(tx.origAmountMicros)
                TransactionType.INCOME -> abs(tx.origAmountMicros)
                else -> tx.origAmountMicros
            }
            val showSign = tx.type == TransactionType.EXPENSE || tx.type == TransactionType.INCOME || tx.type == TransactionType.ADJUST
            val isForeign = !tx.origCurrency.equals(baseCurrency, ignoreCase = true)

            Text(
                text = formatPrivacyAmount(
                    privacyMasked,
                    MoneyAmount.format(
                        amountMicros = signedMicros,
                        currencyCode = tx.origCurrency,
                        showSign = showSign,
                        showCurrencyCode = isForeign || !tx.origCurrency.equals("CNY", true)
                    )
                ),
                style = MaterialTheme.typography.titleMedium.tabular(),
                color = transactionAmountColor(tx.type),
                fontWeight = FontWeight.Bold
            )

            if (isForeign) {
                val baseMicros = viewModel.convertTxToDisplayBase(tx, baseCurrency, rates)
                Text(
                    text = formatPrivacyAmount(privacyMasked, "≈ ${MoneyAmount.format(baseMicros, baseCurrency)}"),
                    style = MaterialTheme.typography.bodySmall.tabular(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (!tx.accountCurrency.equals(tx.origCurrency, ignoreCase = true)) {
                Text(
                    text = formatPrivacyAmount(
                        privacyMasked,
                        "账户 ${MoneyAmount.format(tx.accountAmountMicros, tx.accountCurrency, showCurrencyCode = true)}"
                    ),
                    style = MaterialTheme.typography.bodySmall.tabular(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (tx.type == TransactionType.TRANSFER && tx.toCurrency != null && tx.toAmountMicros != null && !tx.toCurrency.equals(tx.origCurrency, true)) {
                Text(
                    text = formatPrivacyAmount(
                        privacyMasked,
                        "→ ${MoneyAmount.format(tx.toAmountMicros, tx.toCurrency, showCurrencyCode = true)}"
                    ),
                    style = MaterialTheme.typography.bodySmall.tabular(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun TransactionDetailSheet(
    transaction: TransactionEntity,
    account: AccountEntity?,
    toAccount: AccountEntity?,
    category: CategoryEntity?,
    ledgerName: String?,
    accounts: List<AccountEntity>,
    privacyMasked: Boolean,
    viewModel: KairoMainViewModel,
    onConfirm: () -> Unit,
    onEdit: () -> Unit,
    onRefund: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var captureLog by remember(transaction.rawEventId) { mutableStateOf<RawCaptureLogEntity?>(null) }
    var showPromoteDialog by remember { mutableStateOf(false) }
    var zoomImagePath by remember { mutableStateOf<String?>(null) }

    val attachments = remember(transaction.attachmentPaths) {
        transaction.attachmentPaths
            .split(";")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    LaunchedEffect(transaction.rawEventId) {
        val logId = transaction.rawEventId
        captureLog = if (logId != null) viewModel.findCaptureLogById(logId) else null
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "流水详情",
                    style = MaterialTheme.typography.titleLarge
                )
                ChannelChip(channel = transaction.sourceType)
            }

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val signedOrig = when (transaction.type) {
                        TransactionType.EXPENSE -> -abs(transaction.origAmountMicros)
                        TransactionType.INCOME -> abs(transaction.origAmountMicros)
                        else -> transaction.origAmountMicros
                    }
                    Text(
                        text = formatPrivacyAmount(
                            privacyMasked,
                            MoneyAmount.format(
                                signedOrig,
                                transaction.origCurrency,
                                showSign = transaction.type != TransactionType.TRANSFER,
                                showCurrencyCode = true
                            )
                        ),
                        style = MaterialTheme.typography.headlineMedium.tabular(),
                        color = transactionAmountColor(transaction.type)
                    )
                    if (!transaction.origCurrency.equals(transaction.accountCurrency, true)) {
                        Text(
                            text = formatPrivacyAmount(
                                privacyMasked,
                                "账户入账：${MoneyAmount.format(transaction.accountAmountMicros, transaction.accountCurrency, showCurrencyCode = true)}（成交汇率 1 ${transaction.origCurrency} = ${MoneyAmount.formatRate(transaction.rateToAccountMicros)} ${transaction.accountCurrency}）"
                            ),
                            style = MaterialTheme.typography.bodySmall.tabular(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (!transaction.origCurrency.equals(transaction.baseCurrency, true)) {
                        val frozenBase = MoneyAmount.convert(transaction.origAmountMicros, transaction.rateToBaseMicros)
                        Text(
                            text = formatPrivacyAmount(
                                privacyMasked,
                                "主币种冻结折算：${MoneyAmount.format(frozenBase, transaction.baseCurrency)}（快照汇率 ${MoneyAmount.formatRate(transaction.rateToBaseMicros)}）"
                            ),
                            style = MaterialTheme.typography.bodySmall.tabular(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 退款 / 不计入统计提示标记
            if (transaction.refundOfTxId != null || transaction.excludeFromStats || transaction.excludeFromBudget) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (transaction.refundOfTxId != null) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                        ) {
                            Text(
                                text = "关联原支出退款（已冲减原分类支出）",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                    if (transaction.excludeFromStats) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            Text(
                                text = "不计入收支统计",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                    if (transaction.excludeFromBudget) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            Text(
                                text = "不计入预算",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }

            DetailRow("类型", KairoFormats.transactionTypeLabel(transaction.type))
            if (!ledgerName.isNullOrBlank()) {
                DetailRow("账本", ledgerName)
            }
            DetailRow("账户", if (toAccount != null) "${account?.name ?: ""} → ${toAccount.name}" else (account?.name ?: "未知账户"))
            if (category != null) DetailRow("分类", category.name)
            if (transaction.merchant.isNotBlank()) DetailRow("商户", transaction.merchant)
            if (transaction.note.isNotBlank()) DetailRow("备注", transaction.note)
            if (transaction.tags.isNotBlank()) DetailRow("标签", transaction.tags)
            if (transaction.reimburseStatus != ReimburseStatus.NONE) {
                val reimburseLabel = when (transaction.reimburseStatus) {
                    ReimburseStatus.PENDING -> "待报销"
                    ReimburseStatus.REIMBURSED -> "已报销"
                    else -> "不报销"
                }
                DetailRow("报销状态", reimburseLabel)
            }
            DetailRow("时间", KairoFormats.formatDateTime(transaction.timestamp))
            DetailRow("状态", if (transaction.status == TransactionStatus.PENDING) "待核对" else "已确认")

            // 小票图片附件缩略图展示（点击放大预览）
            if (attachments.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "小票图片附件 (${attachments.size})",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(attachments, key = { it }) { path ->
                            ReceiptThumbnailImage(
                                path = path,
                                modifier = Modifier
                                    .size(76.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { zoomImagePath = path }
                            )
                        }
                    }
                }
            }

            // 原始捕获报文溯源卡片
            captureLog?.let { log ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "原始捕获报文溯源 (#${log.id})",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "来源包名：${log.sourcePackage} · 解析器：${log.parserUsed.ifBlank { "-" }}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (log.title.isNotBlank()) {
                            Text("标题：${log.title}", style = MaterialTheme.typography.bodySmall)
                        }
                        Text("原文：${log.content}", style = MaterialTheme.typography.bodySmall)
                        TextButton(
                            onClick = { showPromoteDialog = true },
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("将此来源沉淀为账户指纹 →")
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.Delete, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("删除", maxLines = 1)
                }
                if (transaction.type == TransactionType.EXPENSE) {
                    OutlinedButton(
                        onClick = onRefund,
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.Replay, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("退款", maxLines = 1)
                    }
                }
                OutlinedButton(
                    onClick = onEdit,
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.Edit, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("编辑", maxLines = 1)
                }
                if (transaction.status == TransactionStatus.PENDING) {
                    Button(
                        onClick = onConfirm,
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.Check, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("确认", maxLines = 1)
                    }
                }
            }
        }
    }

    zoomImagePath?.let { path ->
        ReceiptZoomPreviewDialog(
            imagePath = path,
            onDismiss = { zoomImagePath = null }
        )
    }

    if (showPromoteDialog && captureLog != null) {
        val log = captureLog!!
        var selectedAccountId by remember { mutableStateOf(transaction.accountId) }
        var matchType by remember { mutableStateOf(AliasMatchType.PACKAGE) }
        var pattern by remember { mutableStateOf(log.sourcePackage) }
        AlertDialog(
            onDismissRequest = { showPromoteDialog = false },
            title = { Text("沉淀为账户指纹") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("目标账户：", style = MaterialTheme.typography.labelMedium)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(accounts, key = { it.id }) { acc ->
                            FilterChip(
                                selected = selectedAccountId == acc.id,
                                onClick = { selectedAccountId = acc.id },
                                label = { Text(acc.name) }
                            )
                        }
                    }
                    Text("匹配方式：", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            AliasMatchType.PACKAGE to "包名",
                            AliasMatchType.CARD_LAST4 to "尾号",
                            AliasMatchType.KEYWORD to "关键词"
                        ).forEach { (type, label) ->
                            FilterChip(
                                selected = matchType == type,
                                onClick = {
                                    matchType = type
                                    if (type == AliasMatchType.PACKAGE) pattern = log.sourcePackage
                                },
                                label = { Text(label) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = pattern,
                        onValueChange = { pattern = it },
                        label = { Text("匹配模式") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.promoteLogToAlias(log.id, selectedAccountId, pattern.trim(), matchType) {
                            showPromoteDialog = false
                        }
                    }
                ) { Text("保存指纹") }
            },
            dismissButton = {
                TextButton(onClick = { showPromoteDialog = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun BatchCategoryPickerDialog(
    categories: List<CategoryEntity>,
    selectedCount: Int,
    onPickCategory: (Long?) -> Unit,
    onDismiss: () -> Unit
) {
    var filterType by remember { mutableStateOf(TransactionType.EXPENSE) }
    val filteredCats = remember(categories, filterType) {
        val typed = categories.filter { it.type == filterType }
        val parents = typed.filter { it.parentId == null }.sortedWith(compareBy({ it.sortOrder }, { it.id }))
        val childMap = typed.filter { it.parentId != null }.groupBy { it.parentId!! }
        val parentIds = parents.map { it.id }.toSet()
        val treeList = parents.flatMap { p ->
            listOf(p) + childMap[p.id].orEmpty().sortedWith(compareBy({ it.sortOrder }, { it.id }))
        }
        val orphans = typed.filter { it.parentId != null && it.parentId !in parentIds }
        treeList + orphans
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("批量修改分类 ($selectedCount 笔)") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = filterType == TransactionType.EXPENSE,
                        onClick = { filterType = TransactionType.EXPENSE },
                        label = { Text("支出分类") }
                    )
                    FilterChip(
                        selected = filterType == TransactionType.INCOME,
                        onClick = { filterType = TransactionType.INCOME },
                        label = { Text("收入分类") }
                    )
                }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(filteredCats, key = { it.id }) { cat ->
                        val isSub = cat.parentId != null
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPickCategory(cat.id) }
                        ) {
                            Row(
                                modifier = Modifier.padding(
                                    start = if (isSub) 24.dp else 12.dp,
                                    end = 12.dp,
                                    top = 9.dp,
                                    bottom = 9.dp
                                ),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                CategoryIconBadge(
                                    iconKey = cat.icon,
                                    colorHex = cat.colorHex,
                                    size = 32.dp,
                                    iconSize = 16.dp
                                )
                                Text(
                                    text = cat.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isSub) FontWeight.Normal else FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

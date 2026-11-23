package com.kairoledger.app.ui.screens.transactions

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.material.icons.rounded.AutoAwesome
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
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kairoledger.app.core.model.CurrencyCatalog
import com.kairoledger.app.core.model.MoneyAmount
import com.kairoledger.app.data.db.AccountEntity
import com.kairoledger.app.data.db.AliasMatchType
import com.kairoledger.app.data.db.CaptureChannel
import com.kairoledger.app.data.db.CategoryEntity
import com.kairoledger.app.data.db.ExchangeRateEntity
import com.kairoledger.app.data.db.LedgerEntity
import com.kairoledger.app.data.db.RawCaptureLogEntity
import com.kairoledger.app.data.db.ReimburseStatus
import com.kairoledger.app.data.db.TagEntity
import com.kairoledger.app.data.db.TagTextCodec
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
import com.kairoledger.app.ui.components.TagBadgeChip
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
    onOpenSmartInput: () -> Unit = {}
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
    val tagColorMap = remember(tagsList) { tagsList.associate { it.name to it.colorHex } }

    // 视图模式与日历选中日
    var viewMode by rememberSaveable { mutableStateOf(TxViewMode.LIST) }
    var selectedCalendarDate by remember(uiState.filters.yearMonth) { mutableStateOf<LocalDate?>(null) }

    // 搜索与筛选区默认折叠，把首屏完整留给账单列表
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

    // 批量多选模式状态
    var isMultiSelectMode by remember { mutableStateOf(false) }
    var selectedTxIds by remember { mutableStateOf(emptySet<Long>()) }
    var showBatchCategoryDialog by remember { mutableStateOf(false) }
    var showBatchDeleteConfirm by remember { mutableStateOf(false) }

    var detailTransaction by remember { mutableStateOf<TransactionEntity?>(null) }
    var deleteCandidate by remember { mutableStateOf<TransactionEntity?>(null) }
    var showAccountFilterMenu by remember { mutableStateOf(false) }
    var showChannelFilterMenu by remember { mutableStateOf(false) }

    BackHandler(enabled = isMultiSelectMode) {
        isMultiSelectMode = false
        selectedTxIds = emptySet()
    }

    // 直接渲染后台预处理好的 uiState.groupedDays（仅日历选日时按单日下钻）
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

    val activeLedgerLabel = remember(resolvedLedgerId, ledgerMap) {
        when {
            resolvedLedgerId == 0L -> "全部账本"
            else -> ledgerMap[resolvedLedgerId]?.name
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
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 4.dp)
            )
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(
                bottom = contentPadding.calculateBottomPadding() + 88.dp
            )
        ) {
            // 贴合状态栏的柔和薄荷绿渐变顶区（含账本切换、搜索/隐私/智能记账、月份切换、列表/日历切换与本月收支总览）
            item(key = "top_header", contentType = "top_header") {
                TransactionsTopHeaderCard(
                    activeLedgerLabel = activeLedgerLabel ?: "日常账本",
                    yearMonth = filters.yearMonth,
                    baseCurrency = uiState.baseCurrency,
                    expenseMicros = uiState.monthExpenseBaseMicros,
                    incomeMicros = uiState.monthIncomeBaseMicros,
                    balanceMicros = uiState.monthBalanceBaseMicros,
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
                    onResetCurrentMonth = { viewModel.setTxMonth(YearMonth.now()) },
                    onToggleViewMode = {
                        viewMode = if (viewMode == TxViewMode.LIST) TxViewMode.CALENDAR else TxViewMode.LIST
                        if (viewMode == TxViewMode.LIST) {
                            selectedCalendarDate = null
                        }
                    }
                )
            }

            // 折叠式搜索与基础/高级筛选栏
            if (showSearchAndFilterSection) {
                item(key = "search_filter", contentType = "search_filter") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, top = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = filters.query,
                            onValueChange = { viewModel.setTxQuery(it) },
                            placeholder = { Text("搜索商户、备注或标签...") },
                            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                            trailingIcon = {
                                if (filters.query.isNotEmpty()) {
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

                            if (hasAnySubFilter) {
                                item(key = "reset_all_filters") {
                                    FilterChip(
                                        selected = false,
                                        onClick = {
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
                                        },
                                        leadingIcon = {
                                            Icon(Icons.Rounded.Close, null, modifier = Modifier.size(15.dp))
                                        },
                                        label = { Text("清空") }
                                    )
                                }
                            }

                            val currencyOptions = listOf("ALL" to "全部币种", "CNY" to "CNY", "USD" to "USD", "EUR" to "EUR", "GBP" to "GBP")
                            items(currencyOptions, key = { it.first }) { (code, label) ->
                                FilterChip(
                                    selected = filters.currencyFilter == code,
                                    onClick = { viewModel.setTxCurrencyFilter(code) },
                                    label = { Text(label) }
                                )
                            }

                            item(key = "account_filter") {
                                Box {
                                    val selectedAcctName = accountMap[filters.accountIdFilter]?.name ?: "全部账户"
                                    FilterChip(
                                        selected = filters.accountIdFilter != null,
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
                                    val channelLabel = if (filters.channelFilter == "ALL") {
                                        "全部来源"
                                    } else {
                                        KairoFormats.channelLabel(filters.channelFilter)
                                    }
                                    FilterChip(
                                        selected = filters.channelFilter != "ALL",
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

                        // 高级筛选折叠区：分类 / 标签 / 报销状态 / 金额区间
                        AnimatedVisibility(visible = showAdvancedFilters) {
                            AdvancedFilterPanel(
                                categories = categories,
                                selectedCategoryId = filters.categoryIdFilter,
                                onSelectCategory = { catId ->
                                    viewModel.setTxCategoryFilter(catId)
                                    onSelectCategoryFilter(catId)
                                },
                                tags = tagsList,
                                selectedTag = filters.tagFilter,
                                onSelectTag = { tagName ->
                                    viewModel.setTxTagFilter(tagName)
                                },
                                selectedReimburseStatus = filters.reimburseFilter,
                                onSelectReimburseStatus = { status ->
                                    viewModel.setTxReimburseFilter(status)
                                    onSelectReimburseFilter(status)
                                },
                                minAmountText = localMinAmountText,
                                maxAmountText = localMaxAmountText,
                                baseCurrency = uiState.baseCurrency,
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
                                }
                            )
                        }
                    }
                }
            }

            // 待确认收件箱横幅
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
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp)
                    )
                }
            }

            // 月度日历视图卡片
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
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp)
                    )
                }

                if (selectedCalendarDate != null) {
                    item(key = "calendar_day_filter_banner", contentType = "calendar_banner") {
                        val picked = selectedCalendarDate!!
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 16.dp, top = 8.dp),
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

            // 彻底扁平化的按天流水列表或空状态
            if (visibleDays.isEmpty()) {
                item(key = "empty_tx", contentType = "empty_state") {
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
                            "点击右下角「记一笔」，或前往「我的 -> 自动记账与权限中心」开启通知/短信自动记账与账单导入。"
                        },
                        actionLabel = "立即记一笔",
                        onAction = onOpenQuickRecord,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)
                    )
                }
            } else {
                visibleDays.forEach { group ->
                    item(key = "day_hdr_${group.date}", contentType = "day_header") {
                        DayHeaderCardRow(
                            group = group,
                            baseCurrency = uiState.baseCurrency,
                            privacyMasked = privacyMasked,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp)
                        )
                    }

                    itemsIndexed(
                        items = group.transactions,
                        key = { _, tx -> tx.id },
                        contentType = { _, _ -> "tx_row" }
                    ) { idx, tx ->
                        val isLastInDay = idx == group.transactions.lastIndex
                        val isSelected = tx.id in selectedTxIds
                        val resolvedBaseMicros = uiState.baseAmountByTxId[tx.id]
                            ?: viewModel.convertTxToDisplayBase(tx, uiState.baseCurrency, rates)
                        FlattenedTransactionRow(
                            tx = tx,
                            baseCurrency = uiState.baseCurrency,
                            convertedBaseMicros = resolvedBaseMicros,
                            account = accountMap[tx.accountId],
                            toAccount = tx.toAccountId?.let { accountMap[it] },
                            category = tx.categoryId?.let { categoryMap[it] },
                            tagColorMap = tagColorMap,
                            privacyMasked = privacyMasked,
                            isMultiSelectMode = isMultiSelectMode,
                            isSelected = isSelected,
                            isLastInDay = isLastInDay,
                            onClick = {
                                if (isMultiSelectMode) {
                                    selectedTxIds = if (tx.id in selectedTxIds) {
                                        selectedTxIds - tx.id
                                    } else {
                                        selectedTxIds + tx.id
                                    }
                                } else {
                                    detailTransaction = tx
                                }
                            },
                            onLongClick = {
                                if (!isMultiSelectMode) {
                                    isMultiSelectMode = true
                                    selectedTxIds = selectedTxIds + tx.id
                                } else {
                                    selectedTxIds = if (tx.id in selectedTxIds) {
                                        selectedTxIds - tx.id
                                    } else {
                                        selectedTxIds + tx.id
                                    }
                                }
                            }
                        )
                    }
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
            tagColorMap = tagColorMap,
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
private fun TransactionsTopHeaderCard(
    activeLedgerLabel: String,
    yearMonth: YearMonth,
    baseCurrency: String,
    expenseMicros: Long,
    incomeMicros: Long,
    balanceMicros: Long,
    privacyMasked: Boolean,
    viewMode: TxViewMode,
    searchExpanded: Boolean,
    hasActiveFilter: Boolean,
    applyStatusBarPadding: Boolean,
    onOpenLedgerManager: () -> Unit,
    onToggleSearch: () -> Unit,
    onTogglePrivacy: () -> Unit,
    onOpenSmartInput: () -> Unit,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onResetCurrentMonth: () -> Unit,
    onToggleViewMode: () -> Unit
) {
    val semantic = KairoTheme.semantic
    val gradientColors = semantic.heroGradient
    val titleColor = semantic.heroOnSurface
    val subtitleColor = titleColor.copy(alpha = 0.76f)
    val pillBgColor = semantic.heroPillBg
    val monthLabel = yearMonth.format(DateTimeFormatter.ofPattern("yyyy年M月"))
    val isCurrentMonth = yearMonth == YearMonth.now()

    Surface(
        shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp),
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(colors = gradientColors))
                .then(if (applyStatusBarPadding) Modifier.statusBarsPadding() else Modifier)
                .padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 14.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // 第一行：左「账本切换胶囊」，右「搜索」「隐私眼睛」「智能记账」
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Surface(
                        shape = CircleShape,
                        color = pillBgColor,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable(onClick = onOpenLedgerManager)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.MenuBook,
                                contentDescription = null,
                                tint = titleColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = activeLedgerLabel,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = titleColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 140.dp)
                            )
                            Icon(
                                imageVector = Icons.Rounded.ArrowDropDown,
                                contentDescription = "切换账本",
                                tint = titleColor,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        IconButton(
                            onClick = onToggleSearch,
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = if (searchExpanded || hasActiveFilter) {
                                    Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(pillBgColor)
                                } else {
                                    Modifier.size(32.dp)
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Search,
                                    contentDescription = if (searchExpanded) "收起搜索筛选" else "展开搜索筛选",
                                    tint = if (hasActiveFilter) MaterialTheme.colorScheme.primary else titleColor,
                                    modifier = Modifier.size(21.dp)
                                )
                            }
                        }
                        IconButton(
                            onClick = onTogglePrivacy,
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                imageVector = if (privacyMasked) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = if (privacyMasked) "关闭隐私遮罩" else "开启隐私遮罩",
                                tint = titleColor,
                                modifier = Modifier.size(21.dp)
                            )
                        }
                        IconButton(
                            onClick = onOpenSmartInput,
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = "AI/语音智能记账",
                                tint = titleColor,
                                modifier = Modifier.size(21.dp)
                            )
                        }
                    }
                }

                // 第二行：月份切换 (< yyyy年M月 > + 回本月) + 列表/日历切换胶囊
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        IconButton(
                            onClick = onPrevMonth,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                                contentDescription = "上个月",
                                tint = titleColor
                            )
                        }
                        Text(
                            text = monthLabel,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = titleColor
                        )
                        IconButton(
                            onClick = onNextMonth,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                contentDescription = "下个月",
                                tint = titleColor
                            )
                        }
                        if (!isCurrentMonth) {
                            Surface(
                                shape = CircleShape,
                                color = pillBgColor,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable(onClick = onResetCurrentMonth)
                            ) {
                                Text(
                                    text = "回本月",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }

                    Surface(
                        shape = CircleShape,
                        color = pillBgColor,
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
                                text = if (viewMode == TxViewMode.LIST) "日历" else "列表",
                                style = MaterialTheme.typography.labelMedium,
                                color = titleColor,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // 第三行：月度结余 + 支出/收入总览
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "本月结余",
                                style = MaterialTheme.typography.labelSmall,
                                color = subtitleColor
                            )
                            Surface(
                                shape = CircleShape,
                                color = pillBgColor
                            ) {
                                Text(
                                    text = baseCurrency,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = subtitleColor,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                )
                            }
                        }
                        Text(
                            text = formatPrivacyAmount(
                                privacyMasked,
                                MoneyAmount.format(balanceMicros, baseCurrency, showSign = true)
                            ),
                            style = MaterialTheme.typography.headlineSmall.tabular(),
                            fontWeight = FontWeight.Bold,
                            color = titleColor
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = pillBgColor,
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                            Text(
                                text = "本月支出",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(1.dp))
                            Text(
                                text = formatPrivacyAmount(privacyMasked, MoneyAmount.format(expenseMicros, baseCurrency)),
                                style = MaterialTheme.typography.titleMedium.tabular(),
                                color = semantic.expense,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = pillBgColor,
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                            Text(
                                text = "本月收入",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(1.dp))
                            Text(
                                text = formatPrivacyAmount(privacyMasked, MoneyAmount.format(incomeMicros, baseCurrency)),
                                style = MaterialTheme.typography.titleMedium.tabular(),
                                color = semantic.income,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
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
    onSelectDate: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
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

    YimuCard(cornerRadius = 18.dp, modifier = modifier) {
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
    tags: List<TagEntity>,
    selectedTag: String?,
    onSelectTag: (String?) -> Unit,
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
                text = "按标签筛选",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val tagFilterEntries = remember(tags, selectedTag) {
                val list = tags.sortedWith(compareBy({ it.sortOrder }, { it.id }))
                    .map { it.name to it.colorHex }
                    .toMutableList()
                if (!selectedTag.isNullOrBlank() && list.none { it.first == selectedTag }) {
                    list.add(0, selectedTag to KairoIcons.fallbackTagColorHex(selectedTag))
                }
                list
            }
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                item(key = "tag_all") {
                    FilterChip(
                        selected = selectedTag == null,
                        onClick = { onSelectTag(null) },
                        label = { Text("全部标签") }
                    )
                }
                items(tagFilterEntries, key = { it.first }) { (tagName, colorHex) ->
                    TagBadgeChip(
                        name = tagName,
                        colorHex = colorHex,
                        selected = selectedTag == tagName,
                        onClick = {
                            onSelectTag(if (selectedTag == tagName) null else tagName)
                        }
                    )
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
    baseAmountByTxId: Map<Long, Long>,
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
    onDeleteOne: (TransactionEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val semantic = KairoTheme.semantic
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = semantic.warningContainer.copy(alpha = 0.7f),
        border = BorderStroke(1.dp, semantic.warning.copy(alpha = 0.45f)),
        modifier = modifier.fillMaxWidth()
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
                                            val baseAmt = baseAmountByTxId[tx.id]
                                                ?: viewModel.convertTxToDisplayBase(tx, baseCurrency, rates)
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

@Composable
private fun DayHeaderCardRow(
    group: DayTransactionGroup,
    baseCurrency: String,
    privacyMasked: Boolean,
    modifier: Modifier = Modifier
) {
    val semantic = KairoTheme.semantic
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
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
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 14.dp),
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FlattenedTransactionRow(
    tx: TransactionEntity,
    baseCurrency: String,
    convertedBaseMicros: Long,
    account: AccountEntity?,
    toAccount: AccountEntity?,
    category: CategoryEntity?,
    tagColorMap: Map<String, Long>,
    privacyMasked: Boolean,
    isMultiSelectMode: Boolean,
    isSelected: Boolean,
    isLastInDay: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val rowShape = if (isLastInDay) {
        RoundedCornerShape(bottomStart = 18.dp, bottomEnd = 18.dp)
    } else {
        RectangleShape
    }
    val rowBg = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerLowest
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(rowShape)
            .background(rowBg)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        TransactionRowItem(
            tx = tx,
            baseCurrency = baseCurrency,
            convertedBaseMicros = convertedBaseMicros,
            account = account,
            toAccount = toAccount,
            category = category,
            tagColorMap = tagColorMap,
            privacyMasked = privacyMasked,
            isMultiSelectMode = isMultiSelectMode,
            isSelected = isSelected,
            isLastInDay = isLastInDay
        )
        if (!isLastInDay) {
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 14.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            )
        }
    }
}

@Composable
private fun TransactionRowItem(
    tx: TransactionEntity,
    baseCurrency: String,
    convertedBaseMicros: Long,
    account: AccountEntity?,
    toAccount: AccountEntity?,
    category: CategoryEntity?,
    tagColorMap: Map<String, Long>,
    privacyMasked: Boolean,
    isMultiSelectMode: Boolean,
    isSelected: Boolean,
    isLastInDay: Boolean
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
            .padding(
                start = 14.dp,
                end = 14.dp,
                top = 11.dp,
                bottom = if (isLastInDay) 13.dp else 11.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isMultiSelectMode) {
            Icon(
                imageVector = if (isSelected) Icons.Rounded.CheckBox else Icons.Rounded.CheckBoxOutlineBlank,
                contentDescription = if (isSelected) "已选中" else "未选中",
                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
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
            val txTags = remember(tx.tags) { TagTextCodec.parse(tx.tags) }
            if (txTags.isNotEmpty()) {
                val visibleTags = txTags.take(3)
                val extraCount = txTags.size - visibleTags.size
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    visibleTags.forEach { tagName ->
                        TagBadgeChip(
                            name = tagName,
                            colorHex = tagColorMap[tagName] ?: KairoIcons.fallbackTagColorHex(tagName),
                            compact = true
                        )
                    }
                    if (extraCount > 0) {
                        Surface(
                            shape = RoundedCornerShape(7.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            Text(
                                text = "+$extraCount",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        // 双行金额展示：外币主行显示原币种，副行显示主币种预折算（均尊重隐私打码）
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
                Text(
                    text = formatPrivacyAmount(privacyMasked, "≈ ${MoneyAmount.format(convertedBaseMicros, baseCurrency)}"),
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
    tagColorMap: Map<String, Long>,
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
            val detailTags = remember(transaction.tags) { TagTextCodec.parse(transaction.tags) }
            if (detailTags.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = "标签",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        detailTags.forEach { tagName ->
                            TagBadgeChip(
                                name = tagName,
                                colorHex = tagColorMap[tagName] ?: KairoIcons.fallbackTagColorHex(tagName),
                                compact = true
                            )
                        }
                    }
                }
            }
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

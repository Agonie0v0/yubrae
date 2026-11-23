package com.kairoledger.app.ui.screens.stats

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kairoledger.app.core.contract.BudgetProgress
import com.kairoledger.app.core.contract.CategoryStat
import com.kairoledger.app.core.contract.MonthlyPoint
import com.kairoledger.app.core.contract.NetWorthPoint
import com.kairoledger.app.core.contract.TagStat
import com.kairoledger.app.core.contract.TrendBucketPoint
import com.kairoledger.app.core.model.CurrencyCatalog
import com.kairoledger.app.core.model.MoneyAmount
import com.kairoledger.app.data.db.AccountEntity
import com.kairoledger.app.data.db.BudgetEntity
import com.kairoledger.app.data.db.CategoryEntity
import com.kairoledger.app.data.db.ExchangeRateEntity
import com.kairoledger.app.data.db.LedgerEntity
import com.kairoledger.app.data.db.StatsGranularity
import com.kairoledger.app.data.db.TagTextCodec
import com.kairoledger.app.data.db.TransactionEntity
import com.kairoledger.app.data.db.TransactionStatus
import com.kairoledger.app.data.db.TransactionType
import com.kairoledger.app.domain.LedgerAnalytics
import com.kairoledger.app.ui.components.CategoryIconBadge
import com.kairoledger.app.ui.components.ChannelChip
import com.kairoledger.app.ui.components.ConfirmActionDialog
import com.kairoledger.app.ui.components.CurrencyBadge
import com.kairoledger.app.ui.components.CurrencyPickerDialog
import com.kairoledger.app.ui.components.EmptyStateCard
import com.kairoledger.app.ui.components.KairoFormats
import com.kairoledger.app.ui.components.LocalPrivacyMasked
import com.kairoledger.app.ui.components.YimuCard
import com.kairoledger.app.ui.components.formatPrivacyAmount
import com.kairoledger.app.ui.theme.KairoTheme
import com.kairoledger.app.ui.theme.tabular
import com.kairoledger.app.ui.viewmodel.KairoMainViewModel
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale

private enum class CustomDatePickTarget {
    START,
    END
}

private data class TrendBarItem(
    val label: String,
    val detailTitle: String,
    val expenseMicros: Long,
    val incomeMicros: Long,
    val year: Int? = null,
    val month: Int? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    viewModel: KairoMainViewModel,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    granularity: String = "",
    onSelectGranularity: (String) -> Unit = { target -> viewModel.setStatsGranularity(target) },
    anchorDateMillis: Long = -1L,
    customStartMillis: Long? = null,
    customEndMillis: Long? = null,
    onShiftPeriod: (Int) -> Unit = { delta -> viewModel.shiftStatsPeriod(delta) },
    onCustomDateRangeChange: (Long, Long) -> Unit = { s, e -> viewModel.setStatsCustomRange(s, e) },
    trendBuckets: List<TrendBucketPoint> = emptyList(),
    netWorthHistory: List<NetWorthPoint> = emptyList(),
    allTransactions: List<TransactionEntity> = emptyList(),
    accounts: List<AccountEntity> = emptyList(),
    ledgers: List<LedgerEntity> = emptyList(),
    activeLedgerId: Long = -1L,
    onEditTransaction: (TransactionEntity) -> Unit = {},
    listState: LazyListState = rememberLazyListState(),
    onOpenLedgerManager: () -> Unit = {},
    onTogglePrivacy: () -> Unit = {
        viewModel.updateSettings { it.copy(privacyMaskEnabled = !it.privacyMaskEnabled) }
    }
) {
    val uiState by viewModel.statsUiState.collectAsStateWithLifecycle()
    val vmLedgers by viewModel.ledgers.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val rates by viewModel.exchangeRates.collectAsStateWithLifecycle()
    val privacyMasked = LocalPrivacyMasked.current

    val resolvedLedgers = ledgers.ifEmpty { vmLedgers }
    val resolvedLedgerId = if (activeLedgerId >= 0L) activeLedgerId else settings.activeLedgerId
    val resolvedGranularity = granularity.ifBlank { uiState.granularity }
    val resolvedAnchorMillis = if (anchorDateMillis > 0L) anchorDateMillis else uiState.anchorDateMillis
    val resolvedCustomStart = customStartMillis ?: uiState.customStartMillis
    val resolvedCustomEnd = customEndMillis ?: uiState.customEndMillis
    val resolvedTrendBuckets = trendBuckets.ifEmpty { uiState.trendBuckets }
    val resolvedNetWorthHistory = netWorthHistory.ifEmpty { uiState.netWorthHistory }

    var localGranularity by remember(resolvedGranularity) { mutableStateOf(resolvedGranularity) }
    var localCustomStart by remember(resolvedCustomStart) { mutableStateOf(resolvedCustomStart) }
    var localCustomEnd by remember(resolvedCustomEnd) { mutableStateOf(resolvedCustomEnd) }
    var customPickTarget by remember { mutableStateOf<CustomDatePickTarget?>(null) }

    var showCurrencyMenu by remember { mutableStateOf(false) }
    var showMoreCurrencies by remember { mutableStateOf(false) }
    var editingBudget by remember { mutableStateOf<BudgetEntity?>(null) }
    var showCreateBudget by remember { mutableStateOf(false) }
    var deleteBudgetCandidate by remember { mutableStateOf<BudgetEntity?>(null) }
    var highlightedCategoryId by remember { mutableStateOf<Long?>(null) }
    var drillDownCategory by remember { mutableStateOf<CategoryStat?>(null) }
    var highlightedTagName by remember { mutableStateOf<String?>(null) }
    var drillDownTag by remember { mutableStateOf<TagStat?>(null) }

    val zone = ZoneId.systemDefault()
    val anchorDate = remember(resolvedAnchorMillis) {
        Instant.ofEpochMilli(resolvedAnchorMillis).atZone(zone).toLocalDate()
    }
    val timeWindow = remember(
        localGranularity,
        uiState.selectedMonth,
        anchorDate,
        localCustomStart,
        localCustomEnd,
        resolvedTrendBuckets
    ) {
        resolveStatsTimeWindow(
            granularity = localGranularity,
            selectedMonth = uiState.selectedMonth,
            anchorDate = anchorDate,
            customStartMillis = localCustomStart,
            customEndMillis = localCustomEnd,
            trendBuckets = resolvedTrendBuckets,
            zone = zone
        )
    }
    val periodTitle = remember(localGranularity, uiState.selectedMonth, timeWindow) {
        formatStatsPeriodTitle(
            granularity = localGranularity,
            selectedMonth = uiState.selectedMonth,
            windowStartMillis = timeWindow.first,
            windowEndMillis = timeWindow.second,
            zone = zone
        )
    }
    val periodSubTitle = remember(localGranularity, timeWindow) {
        if (localGranularity == StatsGranularity.WEEKLY) {
            val s = Instant.ofEpochMilli(timeWindow.first).atZone(zone).toLocalDate()
            val e = Instant.ofEpochMilli(timeWindow.second).atZone(zone).toLocalDate()
            String.format(Locale.US, "%02d/%02d - %02d/%02d", s.monthValue, s.dayOfMonth, e.monthValue, e.dayOfMonth)
        } else {
            null
        }
    }

    val ledgerMap = remember(resolvedLedgers) { resolvedLedgers.associateBy { it.id } }
    val activeLedgerName = remember(resolvedLedgerId, ledgerMap) {
        when {
            resolvedLedgerId == 0L -> "全部账本"
            else -> ledgerMap[resolvedLedgerId]?.name ?: "日常账本"
        }
    }
    val filteredBudgets = remember(uiState.budgetProgressList, resolvedLedgerId) {
        if (resolvedLedgerId > 0L) {
            uiState.budgetProgressList.filter { it.budget.ledgerId == resolvedLedgerId }
        } else {
            uiState.budgetProgressList
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            bottom = contentPadding.calculateBottomPadding() + 88.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 顶部全宽衔接状态栏的紧凑渐变头（左 "统计" + 账本切换胶囊，右 隐私眼睛 + 显示币种胶囊）
        item(key = "stats_top_header", contentType = "stats_top_header") {
            StatsTopHeaderCard(
                activeLedgerName = activeLedgerName,
                displayCurrency = uiState.displayCurrency,
                pinnedCurrencies = settings.pinnedCurrencies,
                privacyMasked = privacyMasked,
                showCurrencyMenu = showCurrencyMenu,
                onOpenLedgerManager = onOpenLedgerManager,
                onTogglePrivacy = onTogglePrivacy,
                onToggleCurrencyMenu = { showCurrencyMenu = it },
                onSelectCurrency = { code ->
                    viewModel.setStatsDisplayCurrency(code)
                    showCurrencyMenu = false
                },
                onOpenMoreCurrencies = {
                    showCurrencyMenu = false
                    showMoreCurrencies = true
                }
            )
        }

        // 周期与收支维度控制卡
        item(key = "stats_header", contentType = "stats_controls") {
            YimuCard(
                modifier = Modifier.padding(horizontal = 16.dp),
                containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    GranularitySegmentedCapsule(
                        selectedGranularity = localGranularity,
                        onSelect = { target ->
                            highlightedCategoryId = null
                            highlightedTagName = null
                            localGranularity = target
                            onSelectGranularity(target)
                            if (target == StatsGranularity.CUSTOM &&
                                (localCustomStart == null || localCustomEnd == null)
                            ) {
                                val defStart = anchorDate.withDayOfMonth(1).atStartOfDay(zone).toInstant().toEpochMilli()
                                val defEnd = anchorDate.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1L
                                localCustomStart = defStart
                                localCustomEnd = defEnd
                                onCustomDateRangeChange(defStart, defEnd)
                            }
                        }
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    highlightedCategoryId = null
                                    highlightedTagName = null
                                    if (localGranularity == StatsGranularity.CUSTOM) {
                                        val sDate = Instant.ofEpochMilli(timeWindow.first).atZone(zone).toLocalDate()
                                        val eDate = Instant.ofEpochMilli(timeWindow.second).atZone(zone).toLocalDate()
                                        val spanDays = (java.time.temporal.ChronoUnit.DAYS.between(sDate, eDate) + 1L)
                                            .coerceAtLeast(1L)
                                        val ns = sDate.minusDays(spanDays).atStartOfDay(zone).toInstant().toEpochMilli()
                                        val ne = eDate.minusDays(spanDays).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1L
                                        localCustomStart = ns
                                        localCustomEnd = ne
                                        onCustomDateRangeChange(ns, ne)
                                    } else {
                                        onShiftPeriod(-1)
                                    }
                                },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "上一周期")
                            }
                            Column {
                                Text(
                                    text = periodTitle,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                if (periodSubTitle != null) {
                                    Text(
                                        text = periodSubTitle,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            IconButton(
                                onClick = {
                                    highlightedCategoryId = null
                                    highlightedTagName = null
                                    if (localGranularity == StatsGranularity.CUSTOM) {
                                        val sDate = Instant.ofEpochMilli(timeWindow.first).atZone(zone).toLocalDate()
                                        val eDate = Instant.ofEpochMilli(timeWindow.second).atZone(zone).toLocalDate()
                                        val spanDays = (java.time.temporal.ChronoUnit.DAYS.between(sDate, eDate) + 1L)
                                            .coerceAtLeast(1L)
                                        val ns = sDate.plusDays(spanDays).atStartOfDay(zone).toInstant().toEpochMilli()
                                        val ne = eDate.plusDays(spanDays).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1L
                                        localCustomStart = ns
                                        localCustomEnd = ne
                                        onCustomDateRangeChange(ns, ne)
                                    } else {
                                        onShiftPeriod(1)
                                    }
                                },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "下一周期")
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(
                                selected = uiState.selectedType == TransactionType.EXPENSE,
                                onClick = {
                                    highlightedCategoryId = null
                                    highlightedTagName = null
                                    viewModel.setStatsType(TransactionType.EXPENSE)
                                },
                                label = { Text("支出分析") }
                            )
                            FilterChip(
                                selected = uiState.selectedType == TransactionType.INCOME,
                                onClick = {
                                    highlightedCategoryId = null
                                    highlightedTagName = null
                                    viewModel.setStatsType(TransactionType.INCOME)
                                },
                                label = { Text("收入分析") }
                            )
                        }
                    }

                    AnimatedVisibility(visible = localGranularity == StatsGranularity.CUSTOM) {
                        val sText = KairoFormats.formatDate(timeWindow.first)
                        val eText = KairoFormats.formatDate(timeWindow.second)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilterChip(
                                selected = true,
                                onClick = { customPickTarget = CustomDatePickTarget.START },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Rounded.CalendarMonth,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                label = { Text("起 $sText") },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = true,
                                onClick = { customPickTarget = CustomDatePickTarget.END },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Rounded.CalendarMonth,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                label = { Text("止 $eText") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        // 多粒度收支对比柱状图卡片（优先渲染 resolvedTrendBuckets，空时回退 monthlyTrend）
        item(key = "trend_bar_chart", contentType = "trend_chart") {
            TrendBarChartCard(
                granularity = localGranularity,
                trendBuckets = resolvedTrendBuckets,
                monthlyTrend = uiState.monthlyTrend,
                displayCurrency = uiState.displayCurrency,
                privacyMasked = privacyMasked,
                onSelectMonth = { year, month ->
                    viewModel.setStatsMonth(YearMonth.of(year, month))
                },
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        // 分类占比环形图 + 排行榜点击下钻明细
        item(key = "category_donut_card", contentType = "category_donut") {
            CategoryBreakdownCard(
                stats = uiState.categoryStats,
                totalMicros = uiState.totalCategoryAmountMicros,
                type = uiState.selectedType,
                displayCurrency = uiState.displayCurrency,
                privacyMasked = privacyMasked,
                highlightedCategoryId = highlightedCategoryId,
                onSelectCategory = { stat ->
                    highlightedCategoryId = stat.categoryId
                    drillDownCategory = stat
                },
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        // 标签消费/收入分布排行（支持点击下钻明细）
        item(key = "tag_breakdown_card", contentType = "tag_breakdown") {
            TagBreakdownCard(
                stats = uiState.tagStats,
                type = uiState.selectedType,
                displayCurrency = uiState.displayCurrency,
                privacyMasked = privacyMasked,
                highlightedTagName = highlightedTagName,
                onSelectTag = { stat ->
                    highlightedTagName = stat.tagName
                    drillDownTag = stat
                },
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        // 净资产历史走势 Canvas 折线图卡片
        item(key = "net_worth_history_card", contentType = "net_worth_chart") {
            NetWorthHistoryCard(
                points = resolvedNetWorthHistory,
                displayCurrency = uiState.displayCurrency,
                privacyMasked = privacyMasked,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        // 预算管理卡片（总进度 + 上月结转 + 剩余日均可用）
        item(key = "budget_management", contentType = "budget_card") {
            BudgetManagementCard(
                budgets = filteredBudgets,
                displayCurrency = uiState.displayCurrency,
                rates = rates,
                ledgerMap = ledgerMap,
                showLedgerBadge = resolvedLedgers.size > 1,
                privacyMasked = privacyMasked,
                onAddBudget = { showCreateBudget = true },
                onEditBudget = { editingBudget = it },
                onDeleteBudget = { deleteBudgetCandidate = it },
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }

    // 分类明细下钻 ModalBottomSheet（仅在打开时才按需收集交易与账户明细流）
    drillDownCategory?.let { targetStat ->
        val resolvedTransactions = if (allTransactions.isNotEmpty()) {
            allTransactions
        } else {
            viewModel.allTransactions.collectAsStateWithLifecycle().value
        }
        val resolvedAccounts = if (accounts.isNotEmpty()) {
            accounts
        } else {
            viewModel.allAccounts.collectAsStateWithLifecycle().value
        }
        val accountMap = remember(resolvedAccounts) { resolvedAccounts.associateBy { it.id } }
        val txById = remember(resolvedTransactions) { resolvedTransactions.associateBy { it.id } }
        val drillDownTxs = remember(
            resolvedTransactions,
            targetStat,
            timeWindow,
            uiState.selectedType,
            resolvedLedgerId
        ) {
            filterCategoryDrillDownTransactions(
                transactions = resolvedTransactions,
                txById = txById,
                categoryId = targetStat.categoryId,
                selectedType = uiState.selectedType,
                startMillis = timeWindow.first,
                endMillis = timeWindow.second,
                activeLedgerId = resolvedLedgerId
            )
        }

        CategoryDrillDownBottomSheet(
            stat = targetStat,
            periodTitle = periodTitle,
            transactions = drillDownTxs,
            accountMap = accountMap,
            displayCurrency = uiState.displayCurrency,
            rates = rates,
            privacyMasked = privacyMasked,
            onEditTransaction = { tx ->
                drillDownCategory = null
                onEditTransaction(tx)
            },
            onDismiss = { drillDownCategory = null }
        )
    }

    // 标签明细下钻 ModalBottomSheet
    drillDownTag?.let { targetTagStat ->
        val resolvedTransactions = if (allTransactions.isNotEmpty()) {
            allTransactions
        } else {
            viewModel.allTransactions.collectAsStateWithLifecycle().value
        }
        val resolvedAccounts = if (accounts.isNotEmpty()) {
            accounts
        } else {
            viewModel.allAccounts.collectAsStateWithLifecycle().value
        }
        val categories by viewModel.categories.collectAsStateWithLifecycle()
        val accountMap = remember(resolvedAccounts) { resolvedAccounts.associateBy { it.id } }
        val categoryMap = remember(categories) { categories.associateBy { it.id } }
        val drillDownTxs = remember(
            resolvedTransactions,
            targetTagStat,
            timeWindow,
            uiState.selectedType,
            resolvedLedgerId
        ) {
            filterTagDrillDownTransactions(
                transactions = resolvedTransactions,
                tagName = targetTagStat.tagName,
                selectedType = uiState.selectedType,
                startMillis = timeWindow.first,
                endMillis = timeWindow.second,
                activeLedgerId = resolvedLedgerId
            )
        }

        TagDrillDownBottomSheet(
            stat = targetTagStat,
            periodTitle = periodTitle,
            transactions = drillDownTxs,
            accountMap = accountMap,
            categoryMap = categoryMap,
            displayCurrency = uiState.displayCurrency,
            rates = rates,
            privacyMasked = privacyMasked,
            onEditTransaction = { tx ->
                drillDownTag = null
                onEditTransaction(tx)
            },
            onDismiss = { drillDownTag = null }
        )
    }

    // 自定义起止日期 DatePickerDialog
    customPickTarget?.let { target ->
        key(target) {
            val currentMillis = if (target == CustomDatePickTarget.START) timeWindow.first else timeWindow.second
            val currentLocalDate = Instant.ofEpochMilli(currentMillis).atZone(zone).toLocalDate()
            val utcInitMillis = currentLocalDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            val datePickerState = rememberDatePickerState(initialSelectedDateMillis = utcInitMillis)

            DatePickerDialog(
                onDismissRequest = { customPickTarget = null },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val selectedUtc = datePickerState.selectedDateMillis
                            if (selectedUtc != null) {
                                val pickedDate = Instant.ofEpochMilli(selectedUtc).atZone(ZoneOffset.UTC).toLocalDate()
                                val currentStartDate = Instant.ofEpochMilli(timeWindow.first).atZone(zone).toLocalDate()
                                val currentEndDate = Instant.ofEpochMilli(timeWindow.second).atZone(zone).toLocalDate()
                                val (newStartDate, newEndDate) = if (target == CustomDatePickTarget.START) {
                                    val s = pickedDate
                                    val e = if (pickedDate.isAfter(currentEndDate)) pickedDate else currentEndDate
                                    s to e
                                } else {
                                    val e = pickedDate
                                    val s = if (pickedDate.isBefore(currentStartDate)) pickedDate else currentStartDate
                                    s to e
                                }
                                val startMs = newStartDate.atStartOfDay(zone).toInstant().toEpochMilli()
                                val endMs = newEndDate.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1L
                                localCustomStart = startMs
                                localCustomEnd = endMs
                                onCustomDateRangeChange(startMs, endMs)
                            }
                            customPickTarget = null
                        }
                    ) {
                        Text("确定")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { customPickTarget = null }) {
                        Text("取消")
                    }
                }
            ) {
                DatePicker(state = datePickerState)
            }
        }
    }

    if (showCreateBudget || editingBudget != null) {
        val categories by viewModel.categories.collectAsStateWithLifecycle()
        BudgetEditorDialog(
            existing = editingBudget,
            expenseCategories = categories.filter { it.type == TransactionType.EXPENSE },
            ledgers = resolvedLedgers,
            activeLedgerId = resolvedLedgerId,
            pinnedCurrencies = settings.pinnedCurrencies,
            defaultCurrency = settings.baseCurrency,
            onSave = { budget ->
                viewModel.saveBudget(budget) {
                    showCreateBudget = false
                    editingBudget = null
                }
            },
            onDismiss = {
                showCreateBudget = false
                editingBudget = null
            }
        )
    }

    deleteBudgetCandidate?.let { budget ->
        ConfirmActionDialog(
            title = "删除预算「${budget.name}」？",
            message = "删除后将不再追踪该项预算限额。",
            confirmText = "删除",
            isDestructive = true,
            onConfirm = {
                viewModel.deleteBudget(budget.id)
                deleteBudgetCandidate = null
            },
            onDismiss = { deleteBudgetCandidate = null }
        )
    }

    if (showMoreCurrencies) {
        CurrencyPickerDialog(
            selectedCode = uiState.displayCurrency,
            pinnedCodes = settings.pinnedCurrencies,
            onSelect = { viewModel.setStatsDisplayCurrency(it) },
            onDismiss = { showMoreCurrencies = false }
        )
    }
}

@Composable
private fun StatsTopHeaderCard(
    activeLedgerName: String,
    displayCurrency: String,
    pinnedCurrencies: List<String>,
    privacyMasked: Boolean,
    showCurrencyMenu: Boolean,
    onOpenLedgerManager: () -> Unit,
    onTogglePrivacy: () -> Unit,
    onToggleCurrencyMenu: (Boolean) -> Unit,
    onSelectCurrency: (String) -> Unit,
    onOpenMoreCurrencies: () -> Unit
) {
    val semantic = KairoTheme.semantic
    val info = CurrencyCatalog.find(displayCurrency)
    val gradientColors = semantic.heroGradient
    val titleColor = semantic.heroOnSurface
    val pillBgColor = semantic.heroPillBg

    Surface(
        shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp),
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(colors = gradientColors))
                .statusBarsPadding()
                .padding(start = 18.dp, end = 14.dp, top = 10.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f, fill = false)
            ) {
                Text(
                    text = "统计",
                    style = MaterialTheme.typography.titleLarge,
                    color = titleColor,
                    fontWeight = FontWeight.Bold
                )
                Surface(
                    shape = CircleShape,
                    color = pillBgColor,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(onClick = onOpenLedgerManager)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.MenuBook,
                            contentDescription = null,
                            tint = titleColor,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = activeLedgerName,
                            style = MaterialTheme.typography.labelLarge,
                            color = titleColor,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 120.dp)
                        )
                        Icon(
                            imageVector = Icons.Rounded.ArrowDropDown,
                            contentDescription = "切换账本",
                            tint = titleColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(onClick = onTogglePrivacy) {
                    Icon(
                        imageVector = if (privacyMasked) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        contentDescription = if (privacyMasked) "关闭隐私遮罩" else "开启隐私遮罩",
                        tint = titleColor
                    )
                }
                Box {
                    Surface(
                        shape = CircleShape,
                        color = pillBgColor,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { onToggleCurrencyMenu(true) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(info.flag, style = MaterialTheme.typography.labelMedium)
                            Text(
                                text = info.code,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = titleColor
                            )
                            Icon(
                                imageVector = Icons.Rounded.ArrowDropDown,
                                contentDescription = "切换展示币种",
                                tint = titleColor,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    DropdownMenu(
                        expanded = showCurrencyMenu,
                        onDismissRequest = { onToggleCurrencyMenu(false) }
                    ) {
                        pinnedCurrencies.forEach { code ->
                            val itemInfo = CurrencyCatalog.find(code)
                            DropdownMenuItem(
                                text = { Text("${itemInfo.flag} ${itemInfo.code} · ${itemInfo.nameZh}") },
                                onClick = { onSelectCurrency(itemInfo.code) }
                            )
                        }
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("更多币种...") },
                            onClick = onOpenMoreCurrencies
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GranularitySegmentedCapsule(
    selectedGranularity: String,
    onSelect: (String) -> Unit
) {
    val options = remember {
        listOf(
            StatsGranularity.WEEKLY to "周",
            StatsGranularity.MONTHLY to "月",
            StatsGranularity.YEARLY to "年",
            StatsGranularity.CUSTOM to "自定义"
        )
    }
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            options.forEach { (key, label) ->
                val isSelected = selectedGranularity == key
                Surface(
                    shape = CircleShape,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    contentColor = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier
                        .weight(1f)
                        .clip(CircleShape)
                        .clickable { onSelect(key) }
                ) {
                    Box(
                        modifier = Modifier.padding(vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TrendBarChartCard(
    granularity: String,
    trendBuckets: List<TrendBucketPoint>,
    monthlyTrend: List<MonthlyPoint>,
    displayCurrency: String,
    privacyMasked: Boolean,
    onSelectMonth: (Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val semantic = KairoTheme.semantic
    val zone = ZoneId.systemDefault()
    val items = remember(trendBuckets, monthlyTrend, granularity) {
        if (trendBuckets.isNotEmpty()) {
            trendBuckets.map { bucket ->
                val title = formatBucketDetailTitle(bucket, granularity, zone)
                TrendBarItem(
                    label = bucket.label,
                    detailTitle = title,
                    expenseMicros = bucket.expenseBaseMicros,
                    incomeMicros = bucket.incomeBaseMicros
                )
            }
        } else {
            monthlyTrend.map { mp ->
                TrendBarItem(
                    label = mp.monthLabel,
                    detailTitle = "${mp.year}年${mp.month}月",
                    expenseMicros = mp.expenseBaseMicros,
                    incomeMicros = mp.incomeBaseMicros,
                    year = mp.year,
                    month = mp.month
                )
            }
        }
    }

    var selectedIdx by remember(items.size, granularity) {
        mutableIntStateOf((items.size - 1).coerceAtLeast(0))
    }
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(
        fontSize = 10.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
    val highlightPillColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.09f)

    val cardTitle = when (granularity) {
        StatsGranularity.WEEKLY -> "本周收支趋势 ($displayCurrency)"
        StatsGranularity.YEARLY -> "全年各月收支趋势 ($displayCurrency)"
        StatsGranularity.CUSTOM -> "区间收支趋势 ($displayCurrency)"
        else -> "近 6 个月收支趋势 ($displayCurrency)"
    }

    YimuCard(modifier = modifier) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = cardTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LegendDot(color = semantic.income, label = "收入")
                    LegendDot(color = semantic.expense, label = "支出")
                }
            }

            if (items.isEmpty()) {
                Text(
                    text = "暂无当前周期收支趋势数据",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            } else {
                val safeIdx = selectedIdx.coerceIn(0, items.lastIndex)
                val activePoint = items[safeIdx]
                val balanceMicros = activePoint.incomeMicros - activePoint.expenseMicros

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = activePoint.detailTitle,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "结余 ${
                                    formatPrivacyAmount(
                                        privacyMasked,
                                        MoneyAmount.format(balanceMicros, displayCurrency, showSign = true)
                                    )
                                }",
                                style = MaterialTheme.typography.labelMedium.tabular(),
                                fontWeight = FontWeight.SemiBold,
                                color = if (balanceMicros >= 0L) semantic.income else semantic.expense
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text(
                                text = "收 ${
                                    formatPrivacyAmount(
                                        privacyMasked,
                                        MoneyAmount.format(activePoint.incomeMicros, displayCurrency)
                                    )
                                }",
                                style = MaterialTheme.typography.labelMedium.tabular(),
                                color = semantic.income
                            )
                            Text(
                                text = "支 ${
                                    formatPrivacyAmount(
                                        privacyMasked,
                                        MoneyAmount.format(activePoint.expenseMicros, displayCurrency)
                                    )
                                }",
                                style = MaterialTheme.typography.labelMedium.tabular(),
                                color = semantic.expense
                            )
                        }
                    }
                }

                val maxMicros = items.maxOfOrNull { maxOf(it.incomeMicros, it.expenseMicros) }
                    ?.coerceAtLeast(100L * MoneyAmount.AMOUNT_SCALE)
                    ?: (100L * MoneyAmount.AMOUNT_SCALE)

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(196.dp)
                        .pointerInput(items) {
                            detectTapGestures { offset ->
                                val leftPad = 48.dp.toPx()
                                val chartW = (size.width - leftPad).coerceAtLeast(1f)
                                if (offset.x >= leftPad && items.isNotEmpty()) {
                                    val colW = chartW / items.size
                                    val tapped = ((offset.x - leftPad) / colW).toInt().coerceIn(0, items.lastIndex)
                                    selectedIdx = tapped
                                    val pt = items[tapped]
                                    if (trendBuckets.isEmpty() && pt.year != null && pt.month != null) {
                                        onSelectMonth(pt.year, pt.month)
                                    }
                                }
                            }
                        }
                ) {
                    val leftPad = 48.dp.toPx()
                    val bottomPad = 26.dp.toPx()
                    val topPad = 10.dp.toPx()
                    val chartWidth = (size.width - leftPad).coerceAtLeast(1f)
                    val chartHeight = (size.height - topPad - bottomPad).coerceAtLeast(1f)

                    val gridSteps = 3
                    for (i in 0..gridSteps) {
                        val ratio = i.toFloat() / gridSteps.toFloat()
                        val y = topPad + chartHeight * (1f - ratio)
                        drawLine(
                            color = gridColor,
                            start = Offset(leftPad, y),
                            end = Offset(size.width, y),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = if (i == 0) null else PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                        )
                        val tickMicros = (maxMicros.toDouble() * ratio).toLong()
                        val tickText = if (privacyMasked) "****" else formatCompactScale(tickMicros, displayCurrency)
                        val measured = textMeasurer.measure(tickText, labelStyle)
                        drawText(
                            textLayoutResult = measured,
                            topLeft = Offset(
                                x = (leftPad - measured.size.width - 6.dp.toPx()).coerceAtLeast(0f),
                                y = (y - measured.size.height / 2f).coerceIn(0f, size.height - measured.size.height)
                            )
                        )
                    }

                    val colWidth = chartWidth / items.size
                    val barWidth = (colWidth * 0.28f).coerceIn(2.dp.toPx(), 16.dp.toPx())
                    val barGap = if (items.size > 15) 1.5.dp.toPx() else 4.dp.toPx()
                    val labelStep = when {
                        items.size <= 12 -> 1
                        items.size <= 20 -> 2
                        else -> (items.size / 6).coerceAtLeast(2)
                    }

                    items.forEachIndexed { idx, pt ->
                        val colLeft = leftPad + idx * colWidth
                        val centerX = colLeft + colWidth / 2f

                        if (idx == safeIdx) {
                            drawRoundRect(
                                color = highlightPillColor,
                                topLeft = Offset(colLeft + 2.dp.toPx(), topPad),
                                size = Size((colWidth - 4.dp.toPx()).coerceAtLeast(2f), chartHeight + bottomPad - 2.dp.toPx()),
                                cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
                            )
                        }

                        val incRatio = (pt.incomeMicros.toDouble() / maxMicros.toDouble()).toFloat().coerceIn(0f, 1f)
                        val expRatio = (pt.expenseMicros.toDouble() / maxMicros.toDouble()).toFloat().coerceIn(0f, 1f)

                        val incH = (chartHeight * incRatio).coerceAtLeast(if (pt.incomeMicros > 0L) 3.dp.toPx() else 0f)
                        val expH = (chartHeight * expRatio).coerceAtLeast(if (pt.expenseMicros > 0L) 3.dp.toPx() else 0f)

                        if (incH > 0f) {
                            drawRoundRect(
                                color = semantic.income,
                                topLeft = Offset(centerX - barWidth - barGap / 2f, topPad + chartHeight - incH),
                                size = Size(barWidth, incH),
                                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                            )
                        }
                        if (expH > 0f) {
                            drawRoundRect(
                                color = semantic.expense,
                                topLeft = Offset(centerX + barGap / 2f, topPad + chartHeight - expH),
                                size = Size(barWidth, expH),
                                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                            )
                        }

                        if (idx % labelStep == 0 || idx == items.lastIndex) {
                            val xMeasured = textMeasurer.measure(pt.label, labelStyle)
                            val xPos = (centerX - xMeasured.size.width / 2f)
                                .coerceIn(leftPad, (size.width - xMeasured.size.width).coerceAtLeast(leftPad))
                            drawText(
                                textLayoutResult = xMeasured,
                                topLeft = Offset(
                                    x = xPos,
                                    y = topPad + chartHeight + 6.dp.toPx()
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryBreakdownCard(
    stats: List<CategoryStat>,
    totalMicros: Long,
    type: String,
    displayCurrency: String,
    privacyMasked: Boolean,
    highlightedCategoryId: Long?,
    onSelectCategory: (CategoryStat) -> Unit,
    modifier: Modifier = Modifier
) {
    val titleText = if (type == TransactionType.EXPENSE) "支出分类占比" else "收入分类占比"
    val trackBgColor = MaterialTheme.colorScheme.surfaceContainerHighest

    YimuCard(modifier = modifier) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = titleText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (stats.isNotEmpty()) {
                    Text(
                        text = "点击分类查看明细",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (stats.isEmpty()) {
                EmptyStateCard(
                    icon = Icons.Rounded.PieChart,
                    title = "当前周期暂无${if (type == TransactionType.EXPENSE) "支出" else "收入"}分类记录",
                    subtitle = "记入已确认流水后，将在此自动生成环形分布图与排行榜。"
                )
            } else {
                val activeStat = stats.firstOrNull { it.categoryId == highlightedCategoryId }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(190.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.size(168.dp)) {
                        val strokeWidth = 26.dp.toPx()
                        val diameter = size.minDimension - strokeWidth
                        val topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)
                        val arcSize = Size(diameter, diameter)

                        drawArc(
                            color = trackBgColor,
                            startAngle = 0f,
                            sweepAngle = 360f,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = strokeWidth)
                        )

                        var currentStart = -90f
                        val gapAngle = if (stats.size > 1) 2.2f else 0f
                        stats.forEach { stat ->
                            val rawSweep = 360f * stat.percentage.coerceIn(0f, 1f)
                            val sweep = (rawSweep - gapAngle).coerceAtLeast(1.5f)
                            val isSelected = highlightedCategoryId == null || highlightedCategoryId == stat.categoryId
                            drawArc(
                                color = Color(stat.colorHex).copy(alpha = if (isSelected) 1f else 0.28f),
                                startAngle = currentStart,
                                sweepAngle = sweep,
                                useCenter = false,
                                topLeft = topLeft,
                                size = arcSize,
                                style = Stroke(
                                    width = if (highlightedCategoryId == stat.categoryId) strokeWidth + 4.dp.toPx() else strokeWidth,
                                    cap = StrokeCap.Butt
                                )
                            )
                            currentStart += rawSweep
                        }
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = activeStat?.categoryName ?: if (type == TransactionType.EXPENSE) "总支出" else "总收入",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = formatPrivacyAmount(
                                privacyMasked,
                                MoneyAmount.format(
                                    activeStat?.totalBaseMicros ?: totalMicros,
                                    displayCurrency
                                )
                            ),
                            style = MaterialTheme.typography.titleLarge.tabular(),
                            fontWeight = FontWeight.Bold
                        )
                        if (activeStat != null) {
                            Text(
                                text = String.format(Locale.US, "%.1f%% · %d 笔", activeStat.percentage * 100f, activeStat.count),
                                style = MaterialTheme.typography.labelSmall.tabular(),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                // 分类排行与进度条（点击直接下钻明细 BottomSheet）
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    stats.forEach { stat ->
                        val catColor = Color(stat.colorHex)
                        val isHighlighted = highlightedCategoryId == stat.categoryId
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isHighlighted) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.36f)
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerLow
                            },
                            border = BorderStroke(
                                0.5.dp,
                                if (isHighlighted) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                }
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { onSelectCategory(stat) }
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CategoryIconBadge(
                                        iconKey = stat.icon,
                                        colorHex = stat.colorHex,
                                        size = 36.dp,
                                        iconSize = 18.dp,
                                        selected = isHighlighted
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = stat.categoryName,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "${stat.count} 笔",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = formatPrivacyAmount(
                                                privacyMasked,
                                                MoneyAmount.format(stat.totalBaseMicros, displayCurrency)
                                            ),
                                            style = MaterialTheme.typography.titleSmall.tabular(),
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = String.format(Locale.US, "%.1f%%", stat.percentage * 100f),
                                            style = MaterialTheme.typography.labelSmall.tabular(),
                                            color = catColor
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                        contentDescription = "查看明细",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                LinearProgressIndicator(
                                    progress = { stat.percentage.coerceIn(0f, 1f) },
                                    color = catColor,
                                    trackColor = catColor.copy(alpha = 0.14f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(CircleShape)
                                )
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
private fun CategoryDrillDownBottomSheet(
    stat: CategoryStat,
    periodTitle: String,
    transactions: List<TransactionEntity>,
    accountMap: Map<Long, AccountEntity>,
    displayCurrency: String,
    rates: List<ExchangeRateEntity>,
    privacyMasked: Boolean,
    onEditTransaction: (TransactionEntity) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val semantic = KairoTheme.semantic

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CategoryIconBadge(
                    iconKey = stat.icon,
                    colorHex = stat.colorHex,
                    size = 44.dp,
                    iconSize = 22.dp,
                    selected = true
                )
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stat.categoryName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            Text(
                                text = periodTitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = "共 ${transactions.size} 笔流水 · 点击单笔可编辑",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "分类净额",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatPrivacyAmount(
                            privacyMasked,
                            MoneyAmount.format(stat.totalBaseMicros, displayCurrency)
                        ),
                        style = MaterialTheme.typography.titleMedium.tabular(),
                        fontWeight = FontWeight.Bold,
                        color = Color(stat.colorHex)
                    )
                }
            }

            HorizontalDivider()

            if (transactions.isEmpty()) {
                EmptyStateCard(
                    icon = Icons.Rounded.PieChart,
                    title = "当前时间窗口暂无明细",
                    subtitle = "可能该分类统计口径为跨期或数据尚未同步至列表。"
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 440.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(transactions, key = { it.id }) { tx ->
                        val isRefund = tx.type == TransactionType.INCOME && tx.refundOfTxId != null
                        val accountName = accountMap[tx.accountId]?.name ?: "账户 #${tx.accountId}"
                        val title = tx.merchant.ifBlank {
                            tx.note.ifBlank { stat.categoryName }
                        }
                        val amountColor = when {
                            isRefund -> semantic.income
                            tx.type == TransactionType.INCOME -> semantic.income
                            else -> semantic.expense
                        }
                        val prefix = if (isRefund || tx.type == TransactionType.INCOME) "+" else "-"
                        val rawOrig = MoneyAmount.format(
                            tx.origAmountMicros,
                            tx.origCurrency,
                            showCurrencyCode = true
                        )
                        val showBaseApprox = !tx.origCurrency.equals(displayCurrency, ignoreCase = true)
                        val baseApproxMicros = if (showBaseApprox) {
                            convertFrozenBaseAmount(tx, displayCurrency, rates)
                        } else {
                            0L
                        }

                        YimuCard(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            cornerRadius = 14.dp,
                            onClick = { onEditTransaction(tx) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CategoryIconBadge(
                                    iconKey = stat.icon,
                                    colorHex = stat.colorHex,
                                    size = 36.dp,
                                    iconSize = 18.dp
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = title,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        if (isRefund) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = semantic.incomeContainer
                                            ) {
                                                Text(
                                                    text = "退款冲减",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = semantic.income,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                    }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = "$accountName · ${KairoFormats.formatDateTime(tx.timestamp)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        ChannelChip(channel = tx.sourceType)
                                    }
                                    if (tx.note.isNotBlank() && tx.note != title) {
                                        Text(
                                            text = tx.note,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = if (privacyMasked) "****" else "$prefix$rawOrig",
                                        style = MaterialTheme.typography.titleSmall.tabular(),
                                        fontWeight = FontWeight.Bold,
                                        color = amountColor
                                    )
                                    if (showBaseApprox) {
                                        Text(
                                            text = if (privacyMasked) {
                                                "****"
                                            } else {
                                                "≈ ${MoneyAmount.format(baseApproxMicros, displayCurrency)}"
                                            },
                                            style = MaterialTheme.typography.labelSmall.tabular(),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
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

@Composable
private fun NetWorthHistoryCard(
    points: List<NetWorthPoint>,
    displayCurrency: String,
    privacyMasked: Boolean,
    modifier: Modifier = Modifier
) {
    val semantic = KairoTheme.semantic
    val mintColor = MaterialTheme.colorScheme.primary
    val surfaceBg = MaterialTheme.colorScheme.surfaceContainerLowest
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    val textMeasurer = rememberTextMeasurer()
    val axisLabelStyle = MaterialTheme.typography.labelSmall.copy(
        fontSize = 10.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    val badgeLabelStyle = MaterialTheme.typography.labelSmall.copy(
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        color = mintColor
    )

    YimuCard(modifier = modifier) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "净资产历史走势 ($displayCurrency)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.TrendingUp,
                    contentDescription = null,
                    tint = mintColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            if (points.isEmpty()) {
                EmptyStateCard(
                    icon = Icons.AutoMirrored.Rounded.TrendingUp,
                    title = "暂无净资产历史走势",
                    subtitle = "添加资产账户并记入流水后，将在此自动回推各月末净资产变化曲线。"
                )
            } else {
                val firstPt = points.first()
                val latestPt = points.last()
                val delta = latestPt.netWorthMicros - firstPt.netWorthMicros
                val pctText = if (firstPt.netWorthMicros != 0L) {
                    val pct = (delta.toDouble() / kotlin.math.abs(firstPt.netWorthMicros).toDouble()) * 100.0
                    String.format(Locale.US, " (%+.1f%%)", pct)
                } else {
                    ""
                }
                val isPositive = delta >= 0L
                val deltaBg = if (isPositive) semantic.incomeContainer else semantic.expenseContainer
                val deltaFg = if (isPositive) semantic.income else semantic.expense

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column {
                        Text(
                            text = "当前净资产",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = formatPrivacyAmount(
                                privacyMasked,
                                MoneyAmount.format(latestPt.netWorthMicros, displayCurrency)
                            ),
                            style = MaterialTheme.typography.headlineSmall.tabular(),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Surface(
                        shape = CircleShape,
                        color = deltaBg
                    ) {
                        Text(
                            text = if (privacyMasked) {
                                "较首期 ****"
                            } else {
                                "较首期 ${MoneyAmount.format(delta, displayCurrency, showSign = true)}$pctText"
                            },
                            style = MaterialTheme.typography.labelSmall.tabular(),
                            fontWeight = FontWeight.SemiBold,
                            color = deltaFg,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                var selectedIdx by remember(points.size) {
                    mutableIntStateOf(points.lastIndex.coerceAtLeast(0))
                }
                val safeIdx = selectedIdx.coerceIn(0, points.lastIndex)
                val activePt = points[safeIdx]

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${activePt.label} (${KairoFormats.formatDate(activePt.epochMillis)})",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "估值 ${
                                formatPrivacyAmount(
                                    privacyMasked,
                                    MoneyAmount.format(activePt.netWorthMicros, displayCurrency)
                                )
                            }",
                            style = MaterialTheme.typography.labelLarge.tabular(),
                            fontWeight = FontWeight.Bold,
                            color = mintColor
                        )
                    }
                }

                val rawMin = points.minOf { it.netWorthMicros }
                val rawMax = points.maxOf { it.netWorthMicros }
                val basePad = if (rawMax == rawMin) {
                    (kotlin.math.abs(rawMax) / 10L).coerceAtLeast(100L * MoneyAmount.AMOUNT_SCALE)
                } else {
                    ((rawMax - rawMin) * 18L) / 100L
                }
                val paddedMin = rawMin - basePad
                val paddedMax = rawMax + basePad
                val valueRange = (paddedMax - paddedMin).coerceAtLeast(1L).toDouble()

                val minIdx = points.indices.minByOrNull { points[it].netWorthMicros } ?: 0
                val maxIdx = points.indices.maxByOrNull { points[it].netWorthMicros } ?: 0

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(196.dp)
                        .pointerInput(points) {
                            detectTapGestures { offset ->
                                val leftPad = 14.dp.toPx()
                                val rightPad = 14.dp.toPx()
                                val chartW = (size.width - leftPad - rightPad).coerceAtLeast(1f)
                                if (points.size == 1) {
                                    selectedIdx = 0
                                } else {
                                    var bestIdx = 0
                                    var bestDist = Float.MAX_VALUE
                                    for (i in points.indices) {
                                        val px = leftPad + chartW * (i.toFloat() / (points.size - 1).toFloat())
                                        val dist = kotlin.math.abs(offset.x - px)
                                        if (dist < bestDist) {
                                            bestDist = dist
                                            bestIdx = i
                                        }
                                    }
                                    selectedIdx = bestIdx
                                }
                            }
                        }
                ) {
                    val leftPad = 14.dp.toPx()
                    val rightPad = 14.dp.toPx()
                    val topPad = 24.dp.toPx()
                    val bottomPad = 26.dp.toPx()
                    val chartW = (size.width - leftPad - rightPad).coerceAtLeast(1f)
                    val chartH = (size.height - topPad - bottomPad).coerceAtLeast(1f)
                    val chartBottom = topPad + chartH

                    // 背景水平参考线
                    for (step in 0..2) {
                        val y = topPad + chartH * (step / 2f)
                        drawLine(
                            color = gridColor,
                            start = Offset(leftPad, y),
                            end = Offset(leftPad + chartW, y),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = if (step == 2) null else PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                        )
                    }

                    // 零线虚线（当同时存在正负净资产时）
                    if (paddedMin < 0L && paddedMax > 0L) {
                        val zeroRatio = ((0L - paddedMin).toDouble() / valueRange).toFloat().coerceIn(0f, 1f)
                        val zeroY = topPad + chartH * (1f - zeroRatio)
                        drawLine(
                            color = mintColor.copy(alpha = 0.35f),
                            start = Offset(leftPad, zeroY),
                            end = Offset(leftPad + chartW, zeroY),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f)
                        )
                    }

                    val coords = points.mapIndexed { idx, pt ->
                        val x = if (points.size == 1) {
                            leftPad + chartW / 2f
                        } else {
                            leftPad + chartW * (idx.toFloat() / (points.size - 1).toFloat())
                        }
                        val normY = ((pt.netWorthMicros - paddedMin).toDouble() / valueRange).toFloat().coerceIn(0f, 1f)
                        val y = topPad + chartH * (1f - normY)
                        Offset(x, y)
                    }

                    val linePath = Path()
                    val areaPath = Path()
                    coords.forEachIndexed { idx, p ->
                        if (idx == 0) {
                            linePath.moveTo(p.x, p.y)
                            areaPath.moveTo(p.x, chartBottom)
                            areaPath.lineTo(p.x, p.y)
                        } else {
                            val prev = coords[idx - 1]
                            val cx = (prev.x + p.x) / 2f
                            linePath.cubicTo(cx, prev.y, cx, p.y, p.x, p.y)
                            areaPath.cubicTo(cx, prev.y, cx, p.y, p.x, p.y)
                        }
                    }
                    areaPath.lineTo(coords.last().x, chartBottom)
                    areaPath.close()

                    drawPath(
                        path = areaPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                mintColor.copy(alpha = 0.28f),
                                mintColor.copy(alpha = 0.02f)
                            ),
                            startY = topPad,
                            endY = chartBottom
                        )
                    )

                    drawPath(
                        path = linePath,
                        color = mintColor,
                        style = Stroke(
                            width = 2.6.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )

                    // 数据圆点 + 选中晕环
                    coords.forEachIndexed { idx, p ->
                        if (idx == safeIdx) {
                            drawCircle(
                                color = mintColor.copy(alpha = 0.22f),
                                radius = 9.dp.toPx(),
                                center = p
                            )
                        }
                        drawCircle(
                            color = surfaceBg,
                            radius = if (idx == safeIdx) 5.5.dp.toPx() else 4.dp.toPx(),
                            center = p
                        )
                        drawCircle(
                            color = mintColor,
                            radius = if (idx == safeIdx) 3.8.dp.toPx() else 2.6.dp.toPx(),
                            center = p
                        )
                    }

                    // 首尾与最高/最低标注（优先保留最新与极值，自动避让重叠矩形）
                    val keyIndices = linkedSetOf(points.lastIndex, maxIdx, minIdx, 0)
                    val placedLabelRects = mutableListOf<Rect>()
                    keyIndices.forEach { idx ->
                        val p = coords[idx]
                        val pt = points[idx]
                        val tag = when {
                            idx == maxIdx && idx == points.lastIndex && points.size > 1 -> "最新/高"
                            idx == minIdx && idx == points.lastIndex && points.size > 1 -> "最新/低"
                            idx == maxIdx && points.size > 1 -> "高"
                            idx == minIdx && points.size > 1 -> "低"
                            idx == 0 -> "期初"
                            else -> "最新"
                        }
                        val valLabel = if (privacyMasked) "****" else formatCompactScale(pt.netWorthMicros, displayCurrency)
                        val text = "$tag $valLabel"
                        val measured = textMeasurer.measure(text, badgeLabelStyle)
                        val w = measured.size.width.toFloat()
                        val h = measured.size.height.toFloat()
                        val textX = (p.x - w / 2f)
                            .coerceIn(0f, (size.width - w).coerceAtLeast(0f))
                        val aboveY = (p.y - h - 6.dp.toPx()).coerceAtLeast(0f)
                        val belowY = (p.y + 8.dp.toPx()).coerceAtMost((chartBottom - h).coerceAtLeast(0f))
                        val preferBelow = idx == minIdx && idx != maxIdx && p.y < chartBottom - 22.dp.toPx()
                        val candidateYs = if (preferBelow) listOf(belowY, aboveY) else listOf(aboveY, belowY)
                        val chosenRect = candidateYs
                            .map { y -> Rect(textX, y, textX + w, y + h) }
                            .firstOrNull { rect ->
                                placedLabelRects.none { existing ->
                                    rect.overlaps(existing.inflate(4.dp.toPx()))
                                }
                            }
                        if (chosenRect != null) {
                            placedLabelRects.add(chosenRect)
                            drawText(
                                textLayoutResult = measured,
                                topLeft = Offset(chosenRect.left, chosenRect.top)
                            )
                        }
                    }

                    // X 轴月度标签
                    coords.forEachIndexed { idx, p ->
                        val measured = textMeasurer.measure(points[idx].label, axisLabelStyle)
                        val textX = (p.x - measured.size.width / 2f)
                            .coerceIn(0f, (size.width - measured.size.width).coerceAtLeast(0f))
                        drawText(
                            textLayoutResult = measured,
                            topLeft = Offset(textX, chartBottom + 6.dp.toPx())
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BudgetManagementCard(
    budgets: List<BudgetProgress>,
    displayCurrency: String,
    rates: List<ExchangeRateEntity>,
    ledgerMap: Map<Long, LedgerEntity>,
    showLedgerBadge: Boolean,
    privacyMasked: Boolean,
    onAddBudget: () -> Unit,
    onEditBudget: (BudgetEntity) -> Unit,
    onDeleteBudget: (BudgetEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val semantic = KairoTheme.semantic
    val mintColor = MaterialTheme.colorScheme.primary

    YimuCard(modifier = modifier) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "月度预算管理",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                TextButton(onClick = onAddBudget) {
                    Icon(Icons.Rounded.Add, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("添加预算")
                }
            }

            if (budgets.isEmpty()) {
                Text(
                    text = "暂未设定预算。可添加全局月度总预算或指定消费分类预算，支持上月结转与日均可用计算。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                // 卡片顶部总预算进度汇总（优先取全局总预算，若无则汇总全部分类预算）
                val (totalSpentDisplay, totalLimitDisplay) = remember(budgets, displayCurrency, rates) {
                    val summaryTargets = budgets.filter { it.budget.categoryId == null }.ifEmpty { budgets }
                    val spent = summaryTargets.sumOf { bp ->
                        convertBetweenCurrencies(
                            bp.spentInBudgetCurrencyMicros,
                            bp.budget.currencyCode,
                            displayCurrency,
                            rates
                        )
                    }
                    val limit = summaryTargets.sumOf { bp ->
                        convertBetweenCurrencies(
                            bp.limitInBudgetCurrencyMicros,
                            bp.budget.currencyCode,
                            displayCurrency,
                            rates
                        )
                    }
                    spent to limit
                }
                val totalRatio = if (totalLimitDisplay > 0L) {
                    (totalSpentDisplay.toDouble() / totalLimitDisplay.toDouble()).toFloat()
                } else {
                    0f
                }
                val totalOver = totalLimitDisplay > 0L && totalSpentDisplay > totalLimitDisplay
                val totalRemaining = totalLimitDisplay - totalSpentDisplay
                val totalBarColor = when {
                    totalOver -> semantic.expense
                    totalRatio >= 0.8f -> semantic.warning
                    else -> mintColor
                }

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "预算总进度",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "${(totalRatio * 100f).toInt().coerceAtLeast(0)}%",
                                style = MaterialTheme.typography.titleSmall.tabular(),
                                fontWeight = FontWeight.Bold,
                                color = totalBarColor
                            )
                        }
                        LinearProgressIndicator(
                            progress = { totalRatio.coerceIn(0f, 1f) },
                            color = totalBarColor,
                            trackColor = totalBarColor.copy(alpha = 0.16f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(CircleShape)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "已用 ${
                                    formatPrivacyAmount(
                                        privacyMasked,
                                        MoneyAmount.format(totalSpentDisplay, displayCurrency)
                                    )
                                } / ${
                                    formatPrivacyAmount(
                                        privacyMasked,
                                        MoneyAmount.format(totalLimitDisplay, displayCurrency)
                                    )
                                }",
                                style = MaterialTheme.typography.labelSmall.tabular(),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = if (totalRemaining >= 0L) {
                                    "剩余 ${
                                        formatPrivacyAmount(
                                            privacyMasked,
                                            MoneyAmount.format(totalRemaining, displayCurrency)
                                        )
                                    }"
                                } else {
                                    "超支 ${
                                        formatPrivacyAmount(
                                            privacyMasked,
                                            MoneyAmount.format(-totalRemaining, displayCurrency)
                                        )
                                    }"
                                },
                                style = MaterialTheme.typography.labelSmall.tabular(),
                                fontWeight = FontWeight.SemiBold,
                                color = if (totalOver) semantic.expense else semantic.income
                            )
                        }
                    }
                }

                budgets.forEach { bp ->
                    val barColor = when {
                        bp.overBudget -> semantic.expense
                        bp.progressRatio >= 0.8f -> semantic.warning
                        else -> mintColor
                    }
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(
                            0.5.dp,
                            if (bp.overBudget) {
                                semantic.expense.copy(alpha = 0.45f)
                            } else {
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                            }
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = bp.budget.name,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                                        ) {
                                            Text(
                                                text = bp.categoryName,
                                                style = MaterialTheme.typography.labelSmall,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                        if (showLedgerBadge) {
                                            val ledgerName = ledgerMap[bp.budget.ledgerId]?.name ?: "默认账本"
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                                            ) {
                                                Text(
                                                    text = ledgerName,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        if (bp.overBudget) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = semantic.expenseContainer
                                            ) {
                                                Text(
                                                    text = "超支警示",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = semantic.expense,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = "已用 ${
                                            formatPrivacyAmount(
                                                privacyMasked,
                                                MoneyAmount.format(bp.spentInBudgetCurrencyMicros, bp.budget.currencyCode)
                                            )
                                        } / 限额 ${
                                            formatPrivacyAmount(
                                                privacyMasked,
                                                MoneyAmount.format(bp.limitInBudgetCurrencyMicros, bp.budget.currencyCode)
                                            )
                                        }",
                                        style = MaterialTheme.typography.bodySmall.tabular(),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Row {
                                    IconButton(
                                        onClick = { onEditBudget(bp.budget) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Rounded.Edit, "编辑", modifier = Modifier.size(16.dp))
                                    }
                                    IconButton(
                                        onClick = { onDeleteBudget(bp.budget) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            Icons.Rounded.Delete,
                                            "删除",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                LinearProgressIndicator(
                                    progress = { bp.progressRatio.coerceIn(0f, 1f) },
                                    color = barColor,
                                    trackColor = barColor.copy(alpha = 0.16f),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(7.dp)
                                        .clip(CircleShape)
                                )
                                Text(
                                    text = "${(bp.progressRatio * 100f).toInt()}%",
                                    style = MaterialTheme.typography.labelMedium.tabular(),
                                    color = barColor,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            if (bp.rolloverMicros > 0L || (!bp.overBudget && bp.remainingDays > 0)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (bp.rolloverMicros > 0L) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = semantic.incomeContainer
                                        ) {
                                            Text(
                                                text = "含上月结转 +${
                                                    formatPrivacyAmount(
                                                        privacyMasked,
                                                        MoneyAmount.format(bp.rolloverMicros, bp.budget.currencyCode)
                                                    )
                                                }",
                                                style = MaterialTheme.typography.labelSmall.tabular(),
                                                color = semantic.income,
                                                fontWeight = FontWeight.Medium,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    if (!bp.overBudget && bp.remainingDays > 0) {
                                        Text(
                                            text = "剩余可用日均 ${
                                                formatPrivacyAmount(
                                                    privacyMasked,
                                                    MoneyAmount.format(bp.dailyAvailableMicros, bp.budget.currencyCode)
                                                )
                                            }/天",
                                            style = MaterialTheme.typography.labelSmall.tabular(),
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Medium
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BudgetEditorDialog(
    existing: BudgetEntity?,
    expenseCategories: List<CategoryEntity>,
    ledgers: List<LedgerEntity>,
    activeLedgerId: Long,
    pinnedCurrencies: List<String>,
    defaultCurrency: String,
    onSave: (BudgetEntity) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(existing?.name ?: "月度总预算") }
    var selectedCategoryId by remember { mutableStateOf(existing?.categoryId) }
    var currencyCode by remember { mutableStateOf(existing?.currencyCode ?: defaultCurrency) }
    var limitInput by remember {
        mutableStateOf(
            if (existing != null) {
                MoneyAmount.formatPlain(existing.limitAmountMicros, CurrencyCatalog.find(existing.currencyCode).minorDigits, false)
            } else {
                "5000.00"
            }
        )
    }
    var enabled by remember { mutableStateOf(existing?.enabled ?: true) }
    var rolloverEnabled by remember { mutableStateOf(existing?.rolloverEnabled ?: false) }
    val initialLedgerId = existing?.ledgerId ?: if (activeLedgerId > 0L) activeLedgerId else 1L
    var selectedLedgerId by remember { mutableStateOf(initialLedgerId) }
    var showMoreCurrencies by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val availableLedgers = remember(ledgers, selectedLedgerId) {
        val visible = ledgers.filter { !it.archived || it.id == selectedLedgerId }
        if (visible.isNotEmpty()) {
            visible
        } else {
            listOf(LedgerEntity(id = 1L, name = "默认账本"))
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "新建月度预算" else "编辑预算") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("预算名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = limitInput,
                    onValueChange = { limitInput = it },
                    label = { Text("月度限额 ($currencyCode)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("预算币种", style = MaterialTheme.typography.labelMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val codes = if (pinnedCurrencies.contains(currencyCode)) pinnedCurrencies else pinnedCurrencies + currencyCode
                    items(codes, key = { it }) { code ->
                        CurrencyBadge(
                            currencyCode = code,
                            selected = code.equals(currencyCode, true),
                            onClick = { currencyCode = code }
                        )
                    }
                    item(key = "more") {
                        FilterChip(
                            selected = false,
                            onClick = { showMoreCurrencies = true },
                            label = { Text("更多") }
                        )
                    }
                }

                Text("所属账本", style = MaterialTheme.typography.labelMedium)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    availableLedgers.forEach { ledger ->
                        FilterChip(
                            selected = selectedLedgerId == ledger.id,
                            onClick = { selectedLedgerId = ledger.id },
                            label = { Text(ledger.name) }
                        )
                    }
                }

                Text("适用范围", style = MaterialTheme.typography.labelMedium)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = selectedCategoryId == null,
                        onClick = {
                            selectedCategoryId = null
                            if (name.isBlank() || expenseCategories.any { "${it.name}预算" == name || it.name == name }) {
                                name = "月度总预算"
                            }
                        },
                        label = { Text("全局总预算") }
                    )
                    expenseCategories.filter { it.parentId == null }.forEach { cat ->
                        FilterChip(
                            selected = selectedCategoryId == cat.id,
                            onClick = {
                                selectedCategoryId = cat.id
                                name = "${cat.name}预算"
                            },
                            label = { Text(cat.name) }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("启用上月结余结转到本月", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = "上月未花完的预算自动累加到本月可用额度",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = rolloverEnabled, onCheckedChange = { rolloverEnabled = it })
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("启用该预算", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }

                errorMsg?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val limitMicros = MoneyAmount.parseToMicros(limitInput)
                    when {
                        name.trim().isEmpty() -> errorMsg = "请输入预算名称"
                        limitMicros == null || limitMicros <= 0L -> errorMsg = "请输入大于 0 的预算金额"
                        else -> onSave(
                            BudgetEntity(
                                id = existing?.id ?: 0L,
                                name = name.trim(),
                                categoryId = selectedCategoryId,
                                period = existing?.period ?: "MONTHLY",
                                limitAmountMicros = limitMicros,
                                currencyCode = currencyCode,
                                enabled = enabled,
                                ledgerId = selectedLedgerId.takeIf { it > 0L } ?: 1L,
                                rolloverEnabled = rolloverEnabled
                            )
                        )
                    }
                }
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )

    if (showMoreCurrencies) {
        CurrencyPickerDialog(
            selectedCode = currencyCode,
            pinnedCodes = pinnedCurrencies,
            onSelect = { currencyCode = it },
            onDismiss = { showMoreCurrencies = false }
        )
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun resolveStatsTimeWindow(
    granularity: String,
    selectedMonth: YearMonth,
    anchorDate: LocalDate,
    customStartMillis: Long?,
    customEndMillis: Long?,
    trendBuckets: List<TrendBucketPoint>,
    zone: ZoneId
): Pair<Long, Long> {
    return when (granularity) {
        StatsGranularity.WEEKLY -> {
            if (trendBuckets.isNotEmpty()) {
                trendBuckets.first().startMillis to trendBuckets.last().endMillis
            } else {
                val monday = anchorDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                val sunday = monday.plusDays(6)
                val start = monday.atStartOfDay(zone).toInstant().toEpochMilli()
                val end = sunday.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1L
                start to end
            }
        }
        StatsGranularity.YEARLY -> {
            if (trendBuckets.isNotEmpty()) {
                trendBuckets.first().startMillis to trendBuckets.last().endMillis
            } else {
                LedgerAnalytics.yearRangeMillis(anchorDate.year, zone)
            }
        }
        StatsGranularity.CUSTOM -> {
            if (customStartMillis != null && customEndMillis != null) {
                val d1 = Instant.ofEpochMilli(customStartMillis).atZone(zone).toLocalDate()
                val d2 = Instant.ofEpochMilli(customEndMillis).atZone(zone).toLocalDate()
                val safeStart = if (d1.isAfter(d2)) d2 else d1
                val safeEnd = if (d1.isAfter(d2)) d1 else d2
                val start = safeStart.atStartOfDay(zone).toInstant().toEpochMilli()
                val end = safeEnd.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1L
                start to end
            } else if (trendBuckets.isNotEmpty()) {
                trendBuckets.first().startMillis to trendBuckets.last().endMillis
            } else {
                val start = anchorDate.withDayOfMonth(1).atStartOfDay(zone).toInstant().toEpochMilli()
                val end = anchorDate.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1L
                start to end
            }
        }
        else -> {
            LedgerAnalytics.monthRangeMillis(selectedMonth.year, selectedMonth.monthValue, zone)
        }
    }
}

private fun formatStatsPeriodTitle(
    granularity: String,
    selectedMonth: YearMonth,
    windowStartMillis: Long,
    windowEndMillis: Long,
    zone: ZoneId
): String {
    return when (granularity) {
        StatsGranularity.WEEKLY -> {
            val startDate = Instant.ofEpochMilli(windowStartMillis).atZone(zone).toLocalDate()
            val wf = WeekFields.ISO
            val weekYear = startDate.get(wf.weekBasedYear())
            val weekNum = startDate.get(wf.weekOfWeekBasedYear())
            "${weekYear}年第${weekNum}周"
        }
        StatsGranularity.YEARLY -> {
            val startDate = Instant.ofEpochMilli(windowStartMillis).atZone(zone).toLocalDate()
            "${startDate.year}年"
        }
        StatsGranularity.CUSTOM -> {
            val s = Instant.ofEpochMilli(windowStartMillis).atZone(zone).toLocalDate()
            val e = Instant.ofEpochMilli(windowEndMillis).atZone(zone).toLocalDate()
            if (s.year == e.year) {
                String.format(Locale.US, "%02d/%02d - %02d/%02d", s.monthValue, s.dayOfMonth, e.monthValue, e.dayOfMonth)
            } else {
                String.format(
                    Locale.US,
                    "%d/%02d/%02d - %d/%02d/%02d",
                    s.year,
                    s.monthValue,
                    s.dayOfMonth,
                    e.year,
                    e.monthValue,
                    e.dayOfMonth
                )
            }
        }
        else -> {
            selectedMonth.format(DateTimeFormatter.ofPattern("yyyy年M月"))
        }
    }
}

private fun formatBucketDetailTitle(
    bucket: TrendBucketPoint,
    granularity: String,
    zone: ZoneId
): String {
    val startDate = Instant.ofEpochMilli(bucket.startMillis).atZone(zone).toLocalDate()
    return when (granularity) {
        StatsGranularity.WEEKLY -> "${startDate.monthValue}月${startDate.dayOfMonth}日 · ${bucket.label}"
        StatsGranularity.YEARLY, StatsGranularity.MONTHLY -> "${startDate.year}年${startDate.monthValue}月"
        else -> "${startDate.year}-${String.format(Locale.US, "%02d-%02d", startDate.monthValue, startDate.dayOfMonth)}"
    }
}

private fun filterCategoryDrillDownTransactions(
    transactions: List<TransactionEntity>,
    txById: Map<Long, TransactionEntity>,
    categoryId: Long?,
    selectedType: String,
    startMillis: Long,
    endMillis: Long,
    activeLedgerId: Long
): List<TransactionEntity> {
    return transactions
        .filter { tx ->
            if (tx.status != TransactionStatus.CONFIRMED || tx.excludeFromStats) return@filter false
            if (activeLedgerId > 0L && tx.ledgerId != activeLedgerId) return@filter false

            if (tx.type == TransactionType.INCOME && tx.refundOfTxId != null) {
                if (selectedType != TransactionType.EXPENSE) return@filter false
                val origTx = txById[tx.refundOfTxId]
                val targetCatId = origTx?.categoryId ?: tx.categoryId
                val targetTime = origTx?.timestamp ?: tx.timestamp
                return@filter targetCatId == categoryId && targetTime in startMillis..endMillis
            }

            tx.type == selectedType &&
                tx.categoryId == categoryId &&
                tx.timestamp in startMillis..endMillis
        }
        .sortedWith(compareByDescending<TransactionEntity> { it.timestamp }.thenByDescending { it.id })
}

private fun convertFrozenBaseAmount(
    tx: TransactionEntity,
    displayCurrency: String,
    rates: List<ExchangeRateEntity>
): Long {
    val frozenBase = LedgerAnalytics.computeFrozenBaseMicros(tx)
    val txBase = LedgerAnalytics.normalizeCurrency(tx.baseCurrency)
    val target = LedgerAnalytics.normalizeCurrency(displayCurrency)
    if (txBase == target) return frozenBase
    val rate = lookupDisplayRate(txBase, target, rates)
    return MoneyAmount.convert(frozenBase, rate)
}

private fun convertBetweenCurrencies(
    amountMicros: Long,
    fromCurrency: String,
    toCurrency: String,
    rates: List<ExchangeRateEntity>
): Long {
    val from = LedgerAnalytics.normalizeCurrency(fromCurrency)
    val to = LedgerAnalytics.normalizeCurrency(toCurrency)
    if (from == to || amountMicros == 0L) return amountMicros
    val rate = lookupDisplayRate(from, to, rates)
    return MoneyAmount.convert(amountMicros, rate)
}

private fun lookupDisplayRate(
    fromCurrency: String,
    toCurrency: String,
    rates: List<ExchangeRateEntity>
): Long {
    val from = LedgerAnalytics.normalizeCurrency(fromCurrency)
    val to = LedgerAnalytics.normalizeCurrency(toCurrency)
    if (from == to) return MoneyAmount.RATE_SCALE

    rates.firstOrNull {
        it.baseCurrency.equals(from, ignoreCase = true) &&
            it.targetCurrency.equals(to, ignoreCase = true)
    }?.let { return it.rateMicros }

    rates.firstOrNull {
        it.baseCurrency.equals(to, ignoreCase = true) &&
            it.targetCurrency.equals(from, ignoreCase = true)
    }?.let { return MoneyAmount.invertRate(it.rateMicros) }

    val fromCny = rates.firstOrNull {
        it.baseCurrency.equals(from, ignoreCase = true) &&
            it.targetCurrency.equals(CurrencyCatalog.DEFAULT_BASE_CURRENCY, ignoreCase = true)
    }?.rateMicros ?: CurrencyCatalog.find(from).fallbackRateToCnyMicros
    val toCny = rates.firstOrNull {
        it.baseCurrency.equals(to, ignoreCase = true) &&
            it.targetCurrency.equals(CurrencyCatalog.DEFAULT_BASE_CURRENCY, ignoreCase = true)
    }?.rateMicros ?: CurrencyCatalog.find(to).fallbackRateToCnyMicros
    return MoneyAmount.crossRate(fromCny, toCny)
}

private fun formatCompactScale(micros: Long, currency: String): String {
    val symbol = CurrencyCatalog.symbolOf(currency)
    val sign = if (micros < 0L) "-" else ""
    val absUnits = kotlin.math.abs(micros) / MoneyAmount.AMOUNT_SCALE
    return when {
        absUnits >= 10_000L -> String.format(Locale.US, "%s%s%.1fw", sign, symbol, absUnits / 10_000.0)
        absUnits >= 1_000L -> String.format(Locale.US, "%s%s%.1fk", sign, symbol, absUnits / 1_000.0)
        else -> "$sign$symbol$absUnits"
    }
}

@Composable
private fun TagBreakdownCard(
    stats: List<TagStat>,
    type: String,
    displayCurrency: String,
    privacyMasked: Boolean,
    highlightedTagName: String?,
    onSelectTag: (TagStat) -> Unit,
    modifier: Modifier = Modifier
) {
    val titleText = if (type == TransactionType.EXPENSE) "支出标签排行" else "收入标签排行"
    YimuCard(modifier = modifier) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = titleText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (stats.isNotEmpty()) {
                    Text(
                        text = "点击标签查看明细",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (stats.isEmpty()) {
                EmptyStateCard(
                    icon = Icons.AutoMirrored.Rounded.Label,
                    title = "当前周期暂无带标签的${if (type == TransactionType.EXPENSE) "支出" else "收入"}流水",
                    subtitle = "为账单添加标签后，可在此查看各标签的汇总金额、笔数与占比排行。"
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    stats.forEach { stat ->
                        val tagColor = Color(stat.colorHex)
                        val isHighlighted = highlightedTagName == stat.tagName
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isHighlighted) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.36f)
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerLow
                            },
                            border = BorderStroke(
                                0.5.dp,
                                if (isHighlighted) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                }
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { onSelectTag(stat) }
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = if (isHighlighted) tagColor else tagColor.copy(alpha = 0.14f),
                                        border = BorderStroke(1.dp, tagColor.copy(alpha = 0.35f)),
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Box(
                                                modifier = Modifier
                                                    .size(11.dp)
                                                    .clip(CircleShape)
                                                    .background(if (isHighlighted) Color.White else tagColor)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "#${stat.tagName}",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${stat.count} 笔",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = formatPrivacyAmount(
                                                privacyMasked,
                                                MoneyAmount.format(stat.totalBaseMicros, displayCurrency)
                                            ),
                                            style = MaterialTheme.typography.titleSmall.tabular(),
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = String.format(Locale.US, "%.1f%%", stat.percentage * 100f),
                                            style = MaterialTheme.typography.labelSmall.tabular(),
                                            color = tagColor
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                        contentDescription = "查看标签明细",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                LinearProgressIndicator(
                                    progress = { stat.percentage.coerceIn(0f, 1f) },
                                    color = tagColor,
                                    trackColor = tagColor.copy(alpha = 0.14f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(CircleShape)
                                )
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
private fun TagDrillDownBottomSheet(
    stat: TagStat,
    periodTitle: String,
    transactions: List<TransactionEntity>,
    accountMap: Map<Long, AccountEntity>,
    categoryMap: Map<Long, CategoryEntity>,
    displayCurrency: String,
    rates: List<ExchangeRateEntity>,
    privacyMasked: Boolean,
    onEditTransaction: (TransactionEntity) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val semantic = KairoTheme.semantic
    val tagColor = Color(stat.colorHex)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = tagColor.copy(alpha = 0.16f),
                    border = BorderStroke(1.2.dp, tagColor),
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Label,
                            contentDescription = null,
                            tint = tagColor,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "#${stat.tagName}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            Text(
                                text = periodTitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = "共 ${transactions.size} 笔流水 · 点击单笔可编辑",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "标签总额",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatPrivacyAmount(
                            privacyMasked,
                            MoneyAmount.format(stat.totalBaseMicros, displayCurrency)
                        ),
                        style = MaterialTheme.typography.titleMedium.tabular(),
                        fontWeight = FontWeight.Bold,
                        color = tagColor
                    )
                }
            }

            HorizontalDivider()

            if (transactions.isEmpty()) {
                EmptyStateCard(
                    icon = Icons.AutoMirrored.Rounded.Label,
                    title = "当前时间窗口暂无明细",
                    subtitle = "可能该标签流水已被修改或删除。"
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 440.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(transactions, key = { it.id }) { tx ->
                        val cat = tx.categoryId?.let { categoryMap[it] }
                        val accountName = accountMap[tx.accountId]?.name ?: "账户 #${tx.accountId}"
                        val title = tx.merchant.ifBlank {
                            cat?.name ?: tx.note.ifBlank { "#${stat.tagName}" }
                        }
                        val amountColor = if (tx.type == TransactionType.INCOME) semantic.income else semantic.expense
                        val prefix = if (tx.type == TransactionType.INCOME) "+" else "-"
                        val rawOrig = MoneyAmount.format(
                            tx.origAmountMicros,
                            tx.origCurrency,
                            showCurrencyCode = true
                        )
                        val showBaseApprox = !tx.origCurrency.equals(displayCurrency, ignoreCase = true)
                        val baseApproxMicros = if (showBaseApprox) {
                            convertFrozenBaseAmount(tx, displayCurrency, rates)
                        } else {
                            0L
                        }

                        YimuCard(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            cornerRadius = 14.dp,
                            onClick = { onEditTransaction(tx) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CategoryIconBadge(
                                    iconKey = cat?.icon ?: "label",
                                    colorHex = cat?.colorHex ?: stat.colorHex,
                                    size = 36.dp,
                                    iconSize = 18.dp
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Text(
                                        text = title,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = "$accountName · ${KairoFormats.formatDateTime(tx.timestamp)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        ChannelChip(channel = tx.sourceType)
                                    }
                                    if (tx.note.isNotBlank() && tx.note != title) {
                                        Text(
                                            text = tx.note,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = if (privacyMasked) "****" else "$prefix$rawOrig",
                                        style = MaterialTheme.typography.titleSmall.tabular(),
                                        fontWeight = FontWeight.Bold,
                                        color = amountColor
                                    )
                                    if (showBaseApprox) {
                                        Text(
                                            text = if (privacyMasked) {
                                                "****"
                                            } else {
                                                "≈ ${MoneyAmount.format(baseApproxMicros, displayCurrency)}"
                                            },
                                            style = MaterialTheme.typography.labelSmall.tabular(),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
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

private fun filterTagDrillDownTransactions(
    transactions: List<TransactionEntity>,
    tagName: String,
    selectedType: String,
    startMillis: Long,
    endMillis: Long,
    activeLedgerId: Long
): List<TransactionEntity> {
    return transactions
        .filter { tx ->
            if (tx.status != TransactionStatus.CONFIRMED || tx.excludeFromStats) return@filter false
            if (activeLedgerId > 0L && tx.ledgerId != activeLedgerId) return@filter false
            if (tx.type != selectedType || tx.refundOfTxId != null) return@filter false
            if (tx.timestamp !in startMillis..endMillis) return@filter false
            tagName in TagTextCodec.parse(tx.tags)
        }
        .sortedWith(compareByDescending<TransactionEntity> { it.timestamp }.thenByDescending { it.id })
}

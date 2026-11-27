package com.yubrae.app.ui.screens.stats

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
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
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
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yubrae.app.core.contract.BudgetProgress
import com.yubrae.app.core.contract.CategoryStat
import com.yubrae.app.core.contract.MonthlyPoint
import com.yubrae.app.core.contract.NetWorthPoint
import com.yubrae.app.core.contract.TagStat
import com.yubrae.app.core.contract.TrendBucketPoint
import com.yubrae.app.core.model.CurrencyCatalog
import com.yubrae.app.core.model.MoneyAmount
import com.yubrae.app.data.db.AccountEntity
import com.yubrae.app.data.db.BudgetEntity
import com.yubrae.app.data.db.CategoryEntity
import com.yubrae.app.data.db.ExchangeRateEntity
import com.yubrae.app.data.db.LedgerEntity
import com.yubrae.app.data.db.StatsGranularity
import com.yubrae.app.data.db.TagEntity
import com.yubrae.app.data.db.TagTextCodec
import com.yubrae.app.data.db.TransactionEntity
import com.yubrae.app.data.db.TransactionStatus
import com.yubrae.app.data.db.TransactionType
import com.yubrae.app.domain.LedgerAnalytics
import com.yubrae.app.ui.components.AutoResizeAmountText
import com.yubrae.app.ui.components.CartoonCategoryIcon
import com.yubrae.app.ui.components.CategoryIconBadge
import com.yubrae.app.ui.components.CompactHairlineDivider
import com.yubrae.app.ui.components.ConfirmActionDialog
import com.yubrae.app.ui.components.CurrencyPickerDialog
import com.yubrae.app.ui.components.EmptyStateCard
import com.yubrae.app.ui.components.KairoFormats
import com.yubrae.app.ui.components.KairoIcons
import com.yubrae.app.ui.components.LocalPrivacyMasked
import com.yubrae.app.ui.components.QuickCurrencySelectorRow
import com.yubrae.app.ui.components.TagBadgeChip
import com.yubrae.app.ui.components.TopStatusBarScrim
import com.yubrae.app.ui.components.WeekPickerDialog
import com.yubrae.app.ui.components.YearMonthPickerDialog
import com.yubrae.app.ui.components.YearPickerDialog
import com.yubrae.app.ui.components.YimuCard
import com.yubrae.app.ui.components.YubraeCalendarDatePickerDialog
import com.yubrae.app.ui.components.YubraeDialog
import com.yubrae.app.ui.components.YubraeSheetShape
import com.yubrae.app.ui.components.formatPrivacyAmount
import com.yubrae.app.ui.components.yubraeSheetContainerColor
import com.yubrae.app.ui.theme.KairoTheme
import com.yubrae.app.ui.theme.tabular
import com.yubrae.app.ui.viewmodel.KairoMainViewModel
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
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

private data class NetWorthChartBounds(
    val paddedMin: Long,
    val paddedMax: Long,
    val valueRange: Double,
    val minIdx: Int,
    val maxIdx: Int
)

private val StatsYearMonthFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy年M月")
private val StatsDrillDownTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("M/d HH:mm")
private val StatsDrillDownYearTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yy/M/d HH:mm")
private val StatsTypeOptions = listOf(
    TransactionType.EXPENSE to "支出",
    TransactionType.INCOME to "收入"
)
private val ChartGridDashEffect: PathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f), 0f)
private val ChartZeroLineDashEffect: PathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f)
private val StatsTopBarBottomShape = RoundedCornerShape(bottomStart = 18.dp, bottomEnd = 18.dp)
private val StatsProgressBarShape = RoundedCornerShape(4.dp)
private val StatsBadgeShape = RoundedCornerShape(5.dp)
private val StatsInlineTagShape = RoundedCornerShape(4.dp)
private val StatsDrillDownSingleShape = RoundedCornerShape(14.dp)
private val StatsDrillDownTopShape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp)
private val StatsDrillDownBottomShape = RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp)
private val StatsDrillDownMiddleShape = RoundedCornerShape(0.dp)
private val StatsSummaryCardShape = RoundedCornerShape(10.dp)
private val StatsDiscountTileShape = RoundedCornerShape(12.dp)

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
    onSelectAnchorDate: (LocalDate) -> Unit = { date -> viewModel.setStatsAnchorDate(date) },
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
    val tags by viewModel.tags.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val rates by viewModel.exchangeRates.collectAsStateWithLifecycle()
    val privacyMasked = LocalPrivacyMasked.current
    val tagMap = remember(tags) { tags.associateBy { it.name } }

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
    var showPeriodPickerDialog by remember { mutableStateOf(false) }

    var showCurrencyMenu by remember { mutableStateOf(false) }
    var showMoreCurrencies by remember { mutableStateOf(false) }
    var editingBudget by remember { mutableStateOf<BudgetEntity?>(null) }
    var showCreateBudget by remember { mutableStateOf(false) }
    var deleteBudgetCandidate by remember { mutableStateOf<BudgetEntity?>(null) }
    var highlightedCategoryId by remember { mutableStateOf<Long?>(null) }
    var drillDownCategory by remember { mutableStateOf<CategoryStat?>(null) }
    var highlightedTagName by remember { mutableStateOf<String?>(null) }
    var drillDownTag by remember { mutableStateOf<TagStat?>(null) }

    val zone = remember { ZoneId.systemDefault() }
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
    val firstActiveLedger = remember(resolvedLedgers) {
        resolvedLedgers.firstOrNull { !it.archived } ?: resolvedLedgers.firstOrNull()
    }
    val activeLedgerName = remember(resolvedLedgerId, ledgerMap, firstActiveLedger) {
        when {
            resolvedLedgerId == 0L -> "全部账本"
            else -> ledgerMap[resolvedLedgerId]?.name ?: firstActiveLedger?.name ?: "账本"
        }
    }
    val filteredBudgets = remember(uiState.budgetProgressList, resolvedLedgerId) {
        if (resolvedLedgerId > 0L) {
            uiState.budgetProgressList.filter { it.budget.ledgerId == resolvedLedgerId }
        } else {
            uiState.budgetProgressList
        }
    }

    val showStatusBarScrim by remember(listState) {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 40
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                bottom = contentPadding.calculateBottomPadding() + 28.dp
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
        // 轻巧顶部控制条（左账本微胶囊 + 中支出/收入分段微胶囊 + 右隐私/币种微胶囊）
        item(key = "stats_top_header", contentType = "stats_top_header") {
            StatsTopControlBar(
                activeLedgerName = activeLedgerName,
                selectedType = uiState.selectedType,
                displayCurrency = uiState.displayCurrency,
                pinnedCurrencies = settings.pinnedCurrencies,
                privacyMasked = privacyMasked,
                showCurrencyMenu = showCurrencyMenu,
                onOpenLedgerManager = onOpenLedgerManager,
                onSelectType = { targetType ->
                    highlightedCategoryId = null
                    highlightedTagName = null
                    viewModel.setStatsType(targetType)
                },
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

        // 紧凑时间粒度与周期切换卡
        item(key = "stats_header", contentType = "stats_controls") {
            YimuCard(
                modifier = Modifier.padding(horizontal = 12.dp),
                containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
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
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                                contentDescription = "上一周期",
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        val isPickerSupported = localGranularity != StatsGranularity.CUSTOM
                        Surface(
                            shape = CircleShape,
                            color = if (isPickerSupported) {
                                MaterialTheme.colorScheme.surfaceContainerLow
                            } else {
                                Color.Transparent
                            },
                            border = if (isPickerSupported) {
                                BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                            } else {
                                null
                            },
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .padding(horizontal = 6.dp)
                                .clip(CircleShape)
                                .then(
                                    if (isPickerSupported) {
                                        Modifier.clickable { showPeriodPickerDialog = true }
                                    } else {
                                        Modifier
                                    }
                                )
                        ) {
                            Row(
                                modifier = Modifier.padding(
                                    horizontal = if (isPickerSupported) 12.dp else 4.dp,
                                    vertical = if (isPickerSupported) 4.dp else 0.dp
                                ),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = periodTitle,
                                        style = MaterialTheme.typography.titleMedium.tabular(),
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (periodSubTitle != null) {
                                        Text(
                                            text = periodSubTitle,
                                            style = MaterialTheme.typography.labelSmall.tabular(),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1
                                        )
                                    }
                                }
                                if (isPickerSupported) {
                                    Icon(
                                        imageVector = Icons.Rounded.ArrowDropDown,
                                        contentDescription = "选择周期",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
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
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                contentDescription = "下一周期",
                                modifier = Modifier.size(20.dp)
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
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                                onClick = { customPickTarget = CustomDatePickTarget.START },
                                modifier = Modifier.weight(1f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.CalendarMonth,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = "起 $sText",
                                        style = MaterialTheme.typography.labelMedium.tabular(),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                                onClick = { customPickTarget = CustomDatePickTarget.END },
                                modifier = Modifier.weight(1f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.CalendarMonth,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = "止 $eText",
                                        style = MaterialTheme.typography.labelMedium.tabular(),
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
                modifier = Modifier.padding(horizontal = 12.dp)
            )
        }

        // 本期优惠与手续费轻量概览卡片（均为 0 时完全隐藏不占地）
        if (uiState.periodDiscountBaseMicros > 0L || uiState.periodFeeBaseMicros > 0L) {
            item(key = "period_discount_fee_overview", contentType = "discount_fee_overview") {
                PeriodDiscountFeeOverviewCard(
                    discountBaseMicros = uiState.periodDiscountBaseMicros,
                    feeBaseMicros = uiState.periodFeeBaseMicros,
                    discountCount = uiState.periodDiscountCount,
                    feeCount = uiState.periodFeeCount,
                    displayCurrency = uiState.displayCurrency,
                    privacyMasked = privacyMasked,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }
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
                modifier = Modifier.padding(horizontal = 12.dp)
            )
        }

        // 标签消费/收入分布排行（支持点击下钻明细）
        item(key = "tag_breakdown_card", contentType = "tag_breakdown") {
            TagBreakdownCard(
                stats = uiState.tagStats,
                tagMap = tagMap,
                type = uiState.selectedType,
                displayCurrency = uiState.displayCurrency,
                privacyMasked = privacyMasked,
                highlightedTagName = highlightedTagName,
                onSelectTag = { stat ->
                    highlightedTagName = stat.tagName
                    drillDownTag = stat
                },
                modifier = Modifier.padding(horizontal = 12.dp)
            )
        }

        // 净资产历史走势 Canvas 折线图卡片
        item(key = "net_worth_history_card", contentType = "net_worth_chart") {
            NetWorthHistoryCard(
                points = resolvedNetWorthHistory,
                displayCurrency = uiState.displayCurrency,
                privacyMasked = privacyMasked,
                modifier = Modifier.padding(horizontal = 12.dp)
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
                modifier = Modifier.padding(horizontal = 12.dp)
            )
        }
        }

        TopStatusBarScrim(
            visible = showStatusBarScrim,
            modifier = Modifier.align(Alignment.TopCenter)
        )
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
            tagMap = tagMap,
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
            tagMap = tagMap,
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

    // 自定义起止日期选择器（周日至周六）
    customPickTarget?.let { target ->
        key(target) {
            val currentMillis = if (target == CustomDatePickTarget.START) timeWindow.first else timeWindow.second
            val currentLocalDate = Instant.ofEpochMilli(currentMillis).atZone(zone).toLocalDate()
            YubraeCalendarDatePickerDialog(
                initialDate = currentLocalDate,
                title = if (target == CustomDatePickTarget.START) "选择开始日期" else "选择结束日期",
                onSelectDate = { pickedDate ->
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
                },
                onDismiss = { customPickTarget = null }
            )
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

    if (showPeriodPickerDialog) {
        when (localGranularity) {
            StatsGranularity.WEEKLY -> {
                WeekPickerDialog(
                    initialDate = anchorDate,
                    onSelectWeekDate = { pickedDate ->
                        highlightedCategoryId = null
                        highlightedTagName = null
                        onSelectAnchorDate(pickedDate)
                    },
                    onDismiss = { showPeriodPickerDialog = false }
                )
            }
            StatsGranularity.YEARLY -> {
                YearPickerDialog(
                    initialYear = anchorDate.year,
                    onSelect = { pickedYear ->
                        highlightedCategoryId = null
                        highlightedTagName = null
                        onSelectAnchorDate(LocalDate.of(pickedYear, 1, 15))
                    },
                    onDismiss = { showPeriodPickerDialog = false }
                )
            }
            StatsGranularity.CUSTOM -> {
                showPeriodPickerDialog = false
            }
            else -> {
                YearMonthPickerDialog(
                    initialYearMonth = uiState.selectedMonth,
                    onSelect = { pickedYm ->
                        highlightedCategoryId = null
                        highlightedTagName = null
                        onSelectAnchorDate(pickedYm.atDay(15))
                    },
                    onDismiss = { showPeriodPickerDialog = false }
                )
            }
        }
    }
}

@Composable
private fun StatsTopControlBar(
    activeLedgerName: String,
    selectedType: String,
    displayCurrency: String,
    pinnedCurrencies: List<String>,
    privacyMasked: Boolean,
    showCurrencyMenu: Boolean,
    onOpenLedgerManager: () -> Unit,
    onSelectType: (String) -> Unit,
    onTogglePrivacy: () -> Unit,
    onToggleCurrencyMenu: (Boolean) -> Unit,
    onSelectCurrency: (String) -> Unit,
    onOpenMoreCurrencies: () -> Unit
) {
    val semantic = KairoTheme.semantic
    val info = CurrencyCatalog.find(displayCurrency)
    val gradientColors = semantic.heroGradient
    val heroBrush = remember(gradientColors) { Brush.linearGradient(colors = gradientColors) }
    val titleColor = semantic.heroOnSurface
    val pillBgColor = semantic.heroPillBg

    Surface(
        shape = StatsTopBarBottomShape,
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(heroBrush)
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart
            ) {
                Surface(
                    shape = CircleShape,
                    color = pillBgColor,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(onClick = onOpenLedgerManager)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.MenuBook,
                            contentDescription = null,
                            tint = titleColor,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = activeLedgerName,
                            style = MaterialTheme.typography.labelMedium,
                            color = titleColor,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 64.dp)
                        )
                        Icon(
                            imageVector = Icons.Rounded.ArrowDropDown,
                            contentDescription = "切换账本",
                            tint = titleColor,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            // 中间精巧 支出 / 收入 分段微胶囊
            Surface(
                shape = CircleShape,
                color = pillBgColor,
                border = BorderStroke(0.5.dp, semantic.heroBorder)
            ) {
                Row(
                    modifier = Modifier.padding(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatsTypeOptions.forEach { (typeCode, label) ->
                        val active = selectedType == typeCode
                        Surface(
                            shape = CircleShape,
                            color = if (active) MaterialTheme.colorScheme.primary else Color.Transparent,
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable { onSelectType(typeCode) }
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                color = if (active) MaterialTheme.colorScheme.onPrimary else titleColor.copy(alpha = 0.8f),
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterEnd
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = pillBgColor,
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onTogglePrivacy)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (privacyMasked) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = if (privacyMasked) "关闭隐私遮罩" else "开启隐私遮罩",
                                tint = titleColor,
                                modifier = Modifier.size(15.dp)
                            )
                        }
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
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(info.flag, fontSize = 11.sp, maxLines = 1, softWrap = false)
                                Text(
                                    text = info.code,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = titleColor,
                                    maxLines = 1,
                                    softWrap = false
                                )
                                Icon(
                                    imageVector = Icons.Rounded.ArrowDropDown,
                                    contentDescription = "切换展示币种",
                                    tint = titleColor,
                                    modifier = Modifier.size(14.dp)
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
                                text = { Text("更多币种") },
                                onClick = onOpenMoreCurrencies
                            )
                        }
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
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.72f),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
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
                        modifier = Modifier.padding(vertical = 5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium,
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
    val zone = remember { ZoneId.systemDefault() }
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
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.24f)
    val highlightPillColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)

    val cardTitle = when (granularity) {
        StatsGranularity.WEEKLY -> "本周收支趋势 ($displayCurrency)"
        StatsGranularity.YEARLY -> "全年各月收支趋势 ($displayCurrency)"
        StatsGranularity.CUSTOM -> "区间收支趋势 ($displayCurrency)"
        else -> "近 6 个月收支趋势 ($displayCurrency)"
    }

    YimuCard(modifier = modifier) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = cardTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LegendDot(color = semantic.income, label = "收入")
                    LegendDot(color = semantic.expense, label = "支出")
                }
            }

            if (items.isEmpty()) {
                EmptyStateCard(
                    icon = Icons.AutoMirrored.Rounded.TrendingUp,
                    title = "暂无收支数据"
                )
            } else {
                val safeIdx = selectedIdx.coerceIn(0, items.lastIndex)
                val activePoint = items[safeIdx]
                val balanceMicros = activePoint.incomeMicros - activePoint.expenseMicros

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = activePoint.detailTitle,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TrendSummaryMetricColumn(
                                label = "收入",
                                amountText = formatPrivacyAmount(
                                    privacyMasked,
                                    MoneyAmount.format(activePoint.incomeMicros, displayCurrency)
                                ),
                                valueColor = semantic.income,
                                modifier = Modifier.weight(1f)
                            )
                            TrendSummaryMetricColumn(
                                label = "支出",
                                amountText = formatPrivacyAmount(
                                    privacyMasked,
                                    MoneyAmount.format(activePoint.expenseMicros, displayCurrency)
                                ),
                                valueColor = semantic.expense,
                                modifier = Modifier.weight(1f)
                            )
                            TrendSummaryMetricColumn(
                                label = "结余",
                                amountText = formatPrivacyAmount(
                                    privacyMasked,
                                    MoneyAmount.format(balanceMicros, displayCurrency, showSign = true)
                                ),
                                valueColor = if (balanceMicros >= 0L) semantic.income else semantic.expense,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                val maxMicros = remember(items) {
                    items.maxOfOrNull { maxOf(it.incomeMicros, it.expenseMicros) }
                        ?.coerceAtLeast(100L * MoneyAmount.AMOUNT_SCALE)
                        ?: (100L * MoneyAmount.AMOUNT_SCALE)
                }

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(172.dp)
                        .pointerInput(items) {
                            detectTapGestures { offset ->
                                val leftPad = 44.dp.toPx()
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
                    val leftPad = 44.dp.toPx()
                    val bottomPad = 24.dp.toPx()
                    val topPad = 8.dp.toPx()
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
                            strokeWidth = 0.7.dp.toPx(),
                            pathEffect = if (i == 0) null else ChartGridDashEffect
                        )
                        val tickMicros = (maxMicros.toDouble() * ratio).toLong()
                        val tickText = if (privacyMasked) "****" else formatCompactScale(tickMicros, displayCurrency)
                        val measured = textMeasurer.measure(tickText, labelStyle)
                        drawText(
                            textLayoutResult = measured,
                            topLeft = Offset(
                                x = (leftPad - measured.size.width - 5.dp.toPx()).coerceAtLeast(0f),
                                y = (y - measured.size.height / 2f).coerceIn(0f, size.height - measured.size.height)
                            )
                        )
                    }

                    val colWidth = chartWidth / items.size
                    val barWidth = (colWidth * 0.25f).coerceIn(2.5.dp.toPx(), 12.dp.toPx())
                    val barGap = if (items.size > 15) 1.5.dp.toPx() else 3.5.dp.toPx()
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
                                cornerRadius = CornerRadius(7.dp.toPx(), 7.dp.toPx())
                            )
                        }

                        val incRatio = (pt.incomeMicros.toDouble() / maxMicros.toDouble()).toFloat().coerceIn(0f, 1f)
                        val expRatio = (pt.expenseMicros.toDouble() / maxMicros.toDouble()).toFloat().coerceIn(0f, 1f)

                        val incH = (chartHeight * incRatio).coerceAtLeast(if (pt.incomeMicros > 0L) 3.dp.toPx() else 0f)
                        val expH = (chartHeight * expRatio).coerceAtLeast(if (pt.expenseMicros > 0L) 3.dp.toPx() else 0f)

                        if (incH > 0f) {
                            val r = minOf(barWidth / 2f, incH / 2f)
                            drawRoundRect(
                                color = semantic.income,
                                topLeft = Offset(centerX - barWidth - barGap / 2f, topPad + chartHeight - incH),
                                size = Size(barWidth, incH),
                                cornerRadius = CornerRadius(r, r)
                            )
                        }
                        if (expH > 0f) {
                            val r = minOf(barWidth / 2f, expH / 2f)
                            drawRoundRect(
                                color = semantic.expense,
                                topLeft = Offset(centerX + barGap / 2f, topPad + chartHeight - expH),
                                size = Size(barWidth, expH),
                                cornerRadius = CornerRadius(r, r)
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
                                    y = topPad + chartHeight + 5.dp.toPx()
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
private fun PeriodDiscountFeeOverviewCard(
    discountBaseMicros: Long,
    feeBaseMicros: Long,
    discountCount: Int,
    feeCount: Int,
    displayCurrency: String,
    privacyMasked: Boolean,
    modifier: Modifier = Modifier
) {
    val semantic = KairoTheme.semantic
    val hasDiscount = discountBaseMicros > 0L
    val hasFee = feeBaseMicros > 0L
    if (!hasDiscount && !hasFee) return

    YimuCard(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "优惠与手续费概览",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = displayCurrency,
                    style = MaterialTheme.typography.labelSmall.tabular(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (hasDiscount) {
                    DiscountFeeMetricTile(
                        label = "累计已省（优惠）",
                        count = discountCount,
                        amountText = formatPrivacyAmount(
                            privacyMasked,
                            MoneyAmount.format(discountBaseMicros, displayCurrency)
                        ),
                        accentColor = semantic.income,
                        containerColor = semantic.incomeContainer.copy(alpha = 0.52f),
                        modifier = Modifier.weight(1f)
                    )
                }
                if (hasFee) {
                    DiscountFeeMetricTile(
                        label = "累计手续费",
                        count = feeCount,
                        amountText = formatPrivacyAmount(
                            privacyMasked,
                            MoneyAmount.format(feeBaseMicros, displayCurrency)
                        ),
                        accentColor = semantic.warning,
                        containerColor = semantic.warningContainer.copy(alpha = 0.58f),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun DiscountFeeMetricTile(
    label: String,
    count: Int,
    amountText: String,
    accentColor: Color,
    containerColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = StatsDiscountTileShape,
        color = containerColor,
        border = BorderStroke(0.5.dp, accentColor.copy(alpha = 0.24f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.5.sp,
                        lineHeight = 14.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (count > 0) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Surface(
                        shape = StatsBadgeShape,
                        color = accentColor.copy(alpha = 0.14f)
                    ) {
                        Text(
                            text = "${count}笔",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 9.5.sp,
                                lineHeight = 12.sp
                            ).tabular(),
                            fontWeight = FontWeight.SemiBold,
                            color = accentColor,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 4.5.dp, vertical = 1.dp)
                        )
                    }
                }
            }
            AutoResizeAmountText(
                text = amountText,
                baseStyle = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 16.sp,
                    lineHeight = 20.sp
                ).tabular(),
                fontWeight = FontWeight.Bold,
                color = accentColor,
                minFontSize = 11.sp,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun StatsTxDiscountFeeBadge(
    text: String,
    containerColor: Color,
    contentColor: Color
) {
    Surface(
        shape = StatsBadgeShape,
        color = containerColor
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                lineHeight = 13.sp
            ).tabular(),
            fontWeight = FontWeight.SemiBold,
            color = contentColor,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(horizontal = 4.5.dp, vertical = 1.dp)
        )
    }
}

@Composable
private fun TrendSummaryMetricColumn(
    label: String,
    amountText: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                lineHeight = 13.sp
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        AutoResizeAmountText(
            text = amountText,
            baseStyle = MaterialTheme.typography.labelMedium.copy(
                fontSize = 12.5.sp,
                lineHeight = 16.sp
            ).tabular(),
            fontWeight = FontWeight.SemiBold,
            color = valueColor,
            minFontSize = 9.sp,
            modifier = Modifier.fillMaxWidth()
        )
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
    val trackBgColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f)

    YimuCard(modifier = modifier) {
        Column(
            modifier = Modifier.padding(top = 12.dp, bottom = 6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = titleText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (stats.isEmpty()) {
                Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
                    EmptyStateCard(
                        icon = Icons.Rounded.PieChart,
                        title = "暂无${if (type == TransactionType.EXPENSE) "支出" else "收入"}分类数据"
                    )
                }
            } else {
                val activeStat = remember(stats, highlightedCategoryId) {
                    stats.firstOrNull { it.categoryId == highlightedCategoryId }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(172.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.size(148.dp)) {
                        val strokeWidth = 16.dp.toPx()
                        val diameter = size.minDimension - strokeWidth
                        val topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)
                        val arcSize = Size(diameter, diameter)
                        val baseStroke = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                        val highlightedStroke = Stroke(width = strokeWidth + 3.dp.toPx(), cap = StrokeCap.Butt)

                        drawArc(
                            color = trackBgColor,
                            startAngle = 0f,
                            sweepAngle = 360f,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = baseStroke
                        )

                        var currentStart = -90f
                        val gapAngle = if (stats.size > 1) 2.6f else 0f
                        stats.forEach { stat ->
                            val rawSweep = 360f * stat.percentage.coerceIn(0f, 1f)
                            val sweep = (rawSweep - gapAngle).coerceAtLeast(1.5f)
                            val isSelected = highlightedCategoryId == null || highlightedCategoryId == stat.categoryId
                            drawArc(
                                color = Color(stat.colorHex).copy(alpha = if (isSelected) 1f else 0.25f),
                                startAngle = currentStart,
                                sweepAngle = sweep,
                                useCenter = false,
                                topLeft = topLeft,
                                size = arcSize,
                                style = if (highlightedCategoryId == stat.categoryId) highlightedStroke else baseStroke
                            )
                            currentStart += rawSweep
                        }
                    }

                    val centerAmountText = formatPrivacyAmount(
                        privacyMasked,
                        MoneyAmount.format(
                            activeStat?.totalBaseMicros ?: totalMicros,
                            displayCurrency
                        )
                    )
                    val centerAmountFontSize = when {
                        centerAmountText.length <= 7 -> 18.sp
                        centerAmountText.length <= 9 -> 15.5.sp
                        centerAmountText.length <= 11 -> 13.5.sp
                        else -> 12.sp
                    }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.widthIn(max = 104.dp)
                    ) {
                        Text(
                            text = activeStat?.categoryName ?: if (type == TransactionType.EXPENSE) "总支出" else "总收入",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = centerAmountText,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontSize = centerAmountFontSize,
                                lineHeight = (centerAmountFontSize.value + 4f).sp
                            ).tabular(),
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (activeStat != null) {
                            Text(
                                text = String.format(Locale.US, "%.1f%% · %d笔", activeStat.percentage * 100f, activeStat.count),
                                style = MaterialTheme.typography.labelSmall.tabular(),
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // 分类排行与进度条（轻量化紧凑行 + 细分隔线，点击下钻明细 BottomSheet）
                Column {
                    stats.forEachIndexed { index, stat ->
                        val catColor = Color(stat.colorHex)
                        val isHighlighted = highlightedCategoryId == stat.categoryId
                        val rowBg = if (isHighlighted) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
                        } else {
                            Color.Transparent
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(rowBg)
                                .clickable { onSelectCategory(stat) }
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CategoryIconBadge(
                                iconKey = stat.icon,
                                colorHex = stat.colorHex,
                                size = 32.dp,
                                iconSize = 16.dp,
                                selected = isHighlighted
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Text(
                                            text = stat.categoryName,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        Text(
                                            text = "${stat.count}笔",
                                            style = MaterialTheme.typography.labelSmall.tabular(),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Text(
                                            text = formatPrivacyAmount(
                                                privacyMasked,
                                                MoneyAmount.format(stat.totalBaseMicros, displayCurrency)
                                            ),
                                            style = MaterialTheme.typography.titleSmall.tabular(),
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = String.format(Locale.US, "%.1f%%", stat.percentage * 100f),
                                            style = MaterialTheme.typography.labelSmall.tabular(),
                                            color = catColor
                                        )
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                            contentDescription = "查看明细",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                }
                                LinearProgressIndicator(
                                    progress = { stat.percentage.coerceIn(0f, 1f) },
                                    color = catColor,
                                    trackColor = catColor.copy(alpha = 0.13f),
                                    strokeCap = StrokeCap.Round,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .clip(StatsProgressBarShape)
                                )
                            }
                        }
                        if (index < stats.lastIndex) {
                            CompactHairlineDivider(startIndent = 52.dp)
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
    tagMap: Map<String, TagEntity> = emptyMap(),
    displayCurrency: String,
    rates: List<ExchangeRateEntity>,
    privacyMasked: Boolean,
    onEditTransaction: (TransactionEntity) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = YubraeSheetShape,
        containerColor = yubraeSheetContainerColor()
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
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            Text(
                                text = periodTitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = "${transactions.size} 笔",
                        style = MaterialTheme.typography.bodySmall.tabular(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "分类净额",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false
                    )
                    Text(
                        text = formatPrivacyAmount(
                            privacyMasked,
                            MoneyAmount.format(stat.totalBaseMicros, displayCurrency)
                        ),
                        style = MaterialTheme.typography.titleMedium.tabular(),
                        fontWeight = FontWeight.Bold,
                        color = Color(stat.colorHex),
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            HorizontalDivider(
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            )

            if (transactions.isEmpty()) {
                EmptyStateCard(
                    icon = Icons.Rounded.PieChart,
                    title = "暂无明细"
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 440.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    itemsIndexed(
                        items = transactions,
                        key = { _, tx -> tx.id },
                        contentType = { _, _ -> "category_drilldown_tx" }
                    ) { index, tx ->
                        val isRefund = tx.type == TransactionType.INCOME && tx.refundOfTxId != null
                        val cleanedNote = remember(tx.note) { KairoFormats.cleanListRowNote(tx.note) }
                        val title = remember(tx.merchant, cleanedNote, stat.categoryName) {
                            tx.merchant.trim().ifBlank {
                                cleanedNote.ifBlank { stat.categoryName }
                            }
                        }
                        val detailNote = remember(cleanedNote, title) {
                            if (cleanedNote.isNotBlank() && !cleanedNote.equals(title, ignoreCase = true)) {
                                cleanedNote
                            } else {
                                ""
                            }
                        }
                        val txTags = remember(tx.tags) { TagTextCodec.parse(tx.tags) }

                        StatsDrillDownTransactionRow(
                            tx = tx,
                            title = title,
                            detailNote = detailNote,
                            iconKey = stat.icon,
                            iconColorHex = stat.colorHex,
                            isRefund = isRefund,
                            inlineTags = txTags.take(2),
                            account = accountMap[tx.accountId],
                            tagMap = tagMap,
                            displayCurrency = displayCurrency,
                            rates = rates,
                            privacyMasked = privacyMasked,
                            isFirst = index == 0,
                            isLast = index == transactions.lastIndex,
                            onClick = { onEditTransaction(tx) }
                        )
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
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.24f)
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
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "净资产走势 ($displayCurrency)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.TrendingUp,
                    contentDescription = null,
                    tint = mintColor,
                    modifier = Modifier.size(18.dp)
                )
            }

            if (points.isEmpty()) {
                EmptyStateCard(
                    icon = Icons.AutoMirrored.Rounded.TrendingUp,
                    title = "暂无净资产走势"
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
                    Column(modifier = Modifier.weight(1f, fill = false)) {
                        Text(
                            text = "当前净资产",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            softWrap = false
                        )
                        Text(
                            text = formatPrivacyAmount(
                                privacyMasked,
                                MoneyAmount.format(latestPt.netWorthMicros, displayCurrency)
                            ),
                            style = MaterialTheme.typography.headlineSmall.tabular(),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
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
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                var selectedIdx by remember(points.size) {
                    mutableIntStateOf(points.lastIndex.coerceAtLeast(0))
                }
                val safeIdx = selectedIdx.coerceIn(0, points.lastIndex)
                val activePt = points[safeIdx]

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${activePt.label} (${KairoFormats.formatDate(activePt.epochMillis)})",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = formatPrivacyAmount(
                                privacyMasked,
                                MoneyAmount.format(activePt.netWorthMicros, displayCurrency)
                            ),
                            style = MaterialTheme.typography.labelMedium.tabular(),
                            fontWeight = FontWeight.Bold,
                            color = mintColor,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                val chartBounds = remember(points) {
                    val rawMin = points.minOf { it.netWorthMicros }
                    val rawMax = points.maxOf { it.netWorthMicros }
                    val basePad = if (rawMax == rawMin) {
                        (kotlin.math.abs(rawMax) / 10L).coerceAtLeast(100L * MoneyAmount.AMOUNT_SCALE)
                    } else {
                        ((rawMax - rawMin) * 18L) / 100L
                    }
                    val pMin = rawMin - basePad
                    val pMax = rawMax + basePad
                    NetWorthChartBounds(
                        paddedMin = pMin,
                        paddedMax = pMax,
                        valueRange = (pMax - pMin).coerceAtLeast(1L).toDouble(),
                        minIdx = points.indices.minByOrNull { points[it].netWorthMicros } ?: 0,
                        maxIdx = points.indices.maxByOrNull { points[it].netWorthMicros } ?: 0
                    )
                }
                val paddedMin = chartBounds.paddedMin
                val paddedMax = chartBounds.paddedMax
                val valueRange = chartBounds.valueRange
                val minIdx = chartBounds.minIdx
                val maxIdx = chartBounds.maxIdx

                val density = LocalDensity.current
                val areaBrush = remember(mintColor, density) {
                    val topPadPx = with(density) { 22.dp.toPx() }
                    val chartBottomPx = with(density) { (172.dp - 24.dp).toPx() }
                    Brush.verticalGradient(
                        colors = listOf(
                            mintColor.copy(alpha = 0.22f),
                            mintColor.copy(alpha = 0.01f)
                        ),
                        startY = topPadPx,
                        endY = chartBottomPx
                    )
                }
                val lineStroke = remember(density) {
                    Stroke(
                        width = with(density) { 2.2.dp.toPx() },
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                }
                val linePath = remember { Path() }
                val areaPath = remember { Path() }

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(172.dp)
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
                    val topPad = 22.dp.toPx()
                    val bottomPad = 24.dp.toPx()
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
                            strokeWidth = 0.7.dp.toPx(),
                            pathEffect = if (step == 2) null else ChartGridDashEffect
                        )
                    }

                    // 零线虚线（当同时存在正负净资产时）
                    if (paddedMin < 0L && paddedMax > 0L) {
                        val zeroRatio = ((0L - paddedMin).toDouble() / valueRange).toFloat().coerceIn(0f, 1f)
                        val zeroY = topPad + chartH * (1f - zeroRatio)
                        drawLine(
                            color = mintColor.copy(alpha = 0.3f),
                            start = Offset(leftPad, zeroY),
                            end = Offset(leftPad + chartW, zeroY),
                            strokeWidth = 0.8.dp.toPx(),
                            pathEffect = ChartZeroLineDashEffect
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

                    linePath.reset()
                    areaPath.reset()
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
                        brush = areaBrush
                    )

                    drawPath(
                        path = linePath,
                        color = mintColor,
                        style = lineStroke
                    )

                    // 数据圆点 + 选中晕环
                    coords.forEachIndexed { idx, p ->
                        if (idx == safeIdx) {
                            drawCircle(
                                color = mintColor.copy(alpha = 0.2f),
                                radius = 8.dp.toPx(),
                                center = p
                            )
                        }
                        drawCircle(
                            color = surfaceBg,
                            radius = if (idx == safeIdx) 5.dp.toPx() else 3.5.dp.toPx(),
                            center = p
                        )
                        drawCircle(
                            color = mintColor,
                            radius = if (idx == safeIdx) 3.4.dp.toPx() else 2.2.dp.toPx(),
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

                    // X 轴月度标签：优先保留首尾及选中点，按步长与水平间距做碰撞避让，避免相邻标签重叠
                    if (coords.isNotEmpty()) {
                        val labelGapPx = 6.dp.toPx()
                        val labelY = chartBottom + 5.dp.toPx()
                        val placedAxisRanges = mutableListOf<Pair<Float, Float>>()

                        fun tryPlaceAxisLabel(idx: Int) {
                            val measured = textMeasurer.measure(points[idx].label, axisLabelStyle)
                            val w = measured.size.width.toFloat()
                            val textX = (coords[idx].x - w / 2f)
                                .coerceIn(0f, (size.width - w).coerceAtLeast(0f))
                            val left = textX
                            val right = textX + w
                            val overlaps = placedAxisRanges.any { (eLeft, eRight) ->
                                left < eRight + labelGapPx && right + labelGapPx > eLeft
                            }
                            if (!overlaps) {
                                placedAxisRanges.add(left to right)
                                drawText(
                                    textLayoutResult = measured,
                                    topLeft = Offset(textX, labelY)
                                )
                            }
                        }

                        val priorityIndices = linkedSetOf(safeIdx, 0, coords.lastIndex)
                        priorityIndices.forEach { idx ->
                            if (idx in coords.indices) {
                                tryPlaceAxisLabel(idx)
                            }
                        }

                        val axisStep = when {
                            coords.size <= 6 -> 1
                            coords.size <= 12 -> 2
                            else -> (coords.size / 5).coerceAtLeast(2)
                        }
                        for (idx in 1 until coords.lastIndex) {
                            if (idx % axisStep == 0 && idx !in priorityIndices) {
                                tryPlaceAxisLabel(idx)
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
            modifier = Modifier.padding(top = 12.dp, bottom = 6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "月度预算",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (budgets.isNotEmpty()) {
                    TextButton(
                        onClick = onAddBudget,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Icon(Icons.Rounded.Add, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("新建预算", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            if (budgets.isEmpty()) {
                Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
                    EmptyStateCard(
                        icon = Icons.Rounded.PieChart,
                        title = "暂无月度预算",
                        actionLabel = "新建预算",
                        onAction = onAddBudget
                    )
                }
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

                val primaryContainer = MaterialTheme.colorScheme.primaryContainer
                val primaryColor = MaterialTheme.colorScheme.primary
                val totalCardBg = remember(primaryContainer) { primaryContainer.copy(alpha = 0.28f) }
                val totalCardBorder = remember(primaryColor) { BorderStroke(0.5.dp, primaryColor.copy(alpha = 0.2f)) }

                Surface(
                    shape = StatsSummaryCardShape,
                    color = totalCardBg,
                    border = totalCardBorder,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "总预算",
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
                            strokeCap = StrokeCap.Round,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(5.dp)
                                .clip(StatsProgressBarShape)
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

                Column {
                    budgets.forEachIndexed { index, bp ->
                        val barColor = when {
                            bp.overBudget -> semantic.expense
                            bp.progressRatio >= 0.8f -> semantic.warning
                            else -> mintColor
                        }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 9.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = bp.budget.name,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.align(Alignment.CenterVertically)
                                        )
                                        Surface(
                                            shape = StatsBadgeShape,
                                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                            modifier = Modifier.align(Alignment.CenterVertically)
                                        ) {
                                            Text(
                                                text = bp.categoryName,
                                                style = MaterialTheme.typography.labelSmall,
                                                maxLines = 1,
                                                softWrap = false,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                            )
                                        }
                                        if (showLedgerBadge) {
                                            val ledgerName = ledgerMap[bp.budget.ledgerId]?.name ?: "默认账本"
                                            Surface(
                                                shape = StatsBadgeShape,
                                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                                                modifier = Modifier.align(Alignment.CenterVertically)
                                            ) {
                                                Text(
                                                    text = ledgerName,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                    maxLines = 1,
                                                    softWrap = false,
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                        if (bp.overBudget) {
                                            Surface(
                                                shape = StatsBadgeShape,
                                                color = semantic.expenseContainer,
                                                modifier = Modifier.align(Alignment.CenterVertically)
                                            ) {
                                                Text(
                                                    text = "已超支",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = semantic.expense,
                                                    maxLines = 1,
                                                    softWrap = false,
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
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
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Rounded.Edit, "编辑", modifier = Modifier.size(15.dp))
                                    }
                                    IconButton(
                                        onClick = { onDeleteBudget(bp.budget) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            Icons.Rounded.Delete,
                                            "删除",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(15.dp)
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
                                    trackColor = barColor.copy(alpha = 0.14f),
                                    strokeCap = StrokeCap.Round,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(4.dp)
                                        .clip(StatsProgressBarShape)
                                )
                                Text(
                                    text = "${(bp.progressRatio * 100f).toInt()}%",
                                    style = MaterialTheme.typography.labelSmall.tabular(),
                                    color = barColor,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            if (bp.rolloverMicros > 0L || (!bp.overBudget && bp.remainingDays > 0)) {
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    if (bp.rolloverMicros > 0L) {
                                        Surface(
                                            shape = StatsBadgeShape,
                                            color = semantic.incomeContainer,
                                            modifier = Modifier.align(Alignment.CenterVertically)
                                        ) {
                                            Text(
                                                text = "结转 +${
                                                    formatPrivacyAmount(
                                                        privacyMasked,
                                                        MoneyAmount.format(bp.rolloverMicros, bp.budget.currencyCode)
                                                    )
                                                }",
                                                style = MaterialTheme.typography.labelSmall.tabular(),
                                                color = semantic.income,
                                                fontWeight = FontWeight.Medium,
                                                maxLines = 1,
                                                softWrap = false,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                    if (!bp.overBudget && bp.remainingDays > 0) {
                                        Text(
                                            text = "日均可用 ${
                                                formatPrivacyAmount(
                                                    privacyMasked,
                                                    MoneyAmount.format(bp.dailyAvailableMicros, bp.budget.currencyCode)
                                                )
                                            }",
                                            style = MaterialTheme.typography.labelSmall.tabular(),
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            softWrap = false,
                                            modifier = Modifier.align(Alignment.CenterVertically)
                                        )
                                    }
                                }
                            }
                        }
                        if (index < budgets.lastIndex) {
                            CompactHairlineDivider(startIndent = 14.dp, modifier = Modifier.padding(end = 14.dp))
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
    val firstActiveLedgerId = remember(ledgers) {
        ledgers.firstOrNull { !it.archived }?.id ?: ledgers.firstOrNull()?.id ?: 0L
    }
    val initialLedgerId = existing?.ledgerId
        ?.takeIf { id -> ledgers.isEmpty() || ledgers.any { it.id == id } }
        ?: activeLedgerId.takeIf { id -> id > 0L && ledgers.any { it.id == id && !it.archived } }
        ?: firstActiveLedgerId
    var selectedLedgerId by remember(existing?.id, activeLedgerId, firstActiveLedgerId) {
        mutableLongStateOf(initialLedgerId)
    }
    var showMoreCurrencies by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val availableLedgers = remember(ledgers, selectedLedgerId) {
        ledgers.filter { !it.archived || it.id == selectedLedgerId }
    }

    YubraeDialog(
        onDismissRequest = onDismiss,
        title = if (existing == null) "新建预算" else "编辑预算",
        icon = Icons.Rounded.PieChart,
        confirmButton = {
            Button(
                onClick = {
                    val limitMicros = MoneyAmount.parseToMicros(limitInput)
                    val resolvedLedgerId = selectedLedgerId.takeIf { it > 0L }
                        ?: firstActiveLedgerId.takeIf { it > 0L }
                    when {
                        name.trim().isEmpty() -> errorMsg = "请输入预算名称"
                        limitMicros == null || limitMicros <= 0L -> errorMsg = "请输入大于 0 的预算金额"
                        resolvedLedgerId == null -> errorMsg = "请先创建可用账本"
                        else -> onSave(
                            BudgetEntity(
                                id = existing?.id ?: 0L,
                                name = name.trim(),
                                categoryId = selectedCategoryId,
                                period = existing?.period ?: "MONTHLY",
                                limitAmountMicros = limitMicros,
                                currencyCode = currencyCode,
                                enabled = enabled,
                                ledgerId = resolvedLedgerId,
                                rolloverEnabled = rolloverEnabled
                            )
                        )
                    }
                },
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp)
            ) { Text("取消") }
        }
    ) {
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
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = limitInput,
                onValueChange = { limitInput = it },
                label = { Text("月度限额 ($currencyCode)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Text("币种", style = MaterialTheme.typography.labelMedium)
            QuickCurrencySelectorRow(
                selectedCurrency = currencyCode,
                pinnedCurrencies = pinnedCurrencies,
                onSelectCurrency = { currencyCode = it },
                onOpenMore = { showMoreCurrencies = true }
            )

            Text("账本", style = MaterialTheme.typography.labelMedium)
            if (availableLedgers.isEmpty()) {
                Text(
                    text = "暂无可用账本",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
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
                Text("上月结余结转", style = MaterialTheme.typography.bodyMedium)
                Switch(checked = rolloverEnabled, onCheckedChange = { rolloverEnabled = it })
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("启用", style = MaterialTheme.typography.bodyMedium)
                Switch(checked = enabled, onCheckedChange = { enabled = it })
            }

            errorMsg?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }

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
                val sunday = anchorDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
                val saturday = sunday.plusDays(6)
                val start = sunday.atStartOfDay(zone).toInstant().toEpochMilli()
                val end = saturday.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1L
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
            val wf = WeekFields.SUNDAY_START
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
            selectedMonth.format(StatsYearMonthFormatter)
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
    tagMap: Map<String, TagEntity> = emptyMap(),
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
            modifier = Modifier.padding(top = 12.dp, bottom = 6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = titleText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (stats.isEmpty()) {
                Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
                    EmptyStateCard(
                        icon = Icons.AutoMirrored.Rounded.Label,
                        title = "暂无${if (type == TransactionType.EXPENSE) "支出" else "收入"}标签数据"
                    )
                }
            } else {
                Column {
                    stats.forEachIndexed { index, stat ->
                        val tagColor = Color(stat.colorHex)
                        val iconKey = tagMap[stat.tagName]?.icon.orEmpty()
                        val isHighlighted = highlightedTagName == stat.tagName
                        val rowBg = if (isHighlighted) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
                        } else {
                            Color.Transparent
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(rowBg)
                                .clickable { onSelectTag(stat) }
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = if (isHighlighted) tagColor else tagColor.copy(alpha = 0.13f),
                                border = if (isHighlighted) BorderStroke(1.5.dp, tagColor) else null,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    if (iconKey.isNotBlank()) {
                                        CartoonCategoryIcon(
                                            iconKey = iconKey,
                                            size = 18.dp,
                                            fallbackColorHex = stat.colorHex
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .clip(CircleShape)
                                                .background(if (isHighlighted) Color.White else tagColor)
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        TagBadgeChip(
                                            name = stat.tagName,
                                            colorHex = stat.colorHex,
                                            selected = isHighlighted,
                                            compact = true,
                                            iconKey = iconKey,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        Text(
                                            text = "${stat.count}笔",
                                            style = MaterialTheme.typography.labelSmall.tabular(),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Text(
                                            text = formatPrivacyAmount(
                                                privacyMasked,
                                                MoneyAmount.format(stat.totalBaseMicros, displayCurrency)
                                            ),
                                            style = MaterialTheme.typography.titleSmall.tabular(),
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = String.format(Locale.US, "%.1f%%", stat.percentage * 100f),
                                            style = MaterialTheme.typography.labelSmall.tabular(),
                                            color = tagColor
                                        )
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                            contentDescription = "查看标签明细",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                }
                                LinearProgressIndicator(
                                    progress = { stat.percentage.coerceIn(0f, 1f) },
                                    color = tagColor,
                                    trackColor = tagColor.copy(alpha = 0.13f),
                                    strokeCap = StrokeCap.Round,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .clip(StatsProgressBarShape)
                                )
                            }
                        }
                        if (index < stats.lastIndex) {
                            CompactHairlineDivider(startIndent = 52.dp)
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
    tagMap: Map<String, TagEntity> = emptyMap(),
    displayCurrency: String,
    rates: List<ExchangeRateEntity>,
    privacyMasked: Boolean,
    onEditTransaction: (TransactionEntity) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val tagColor = Color(stat.colorHex)
    val tagIconKey = tagMap[stat.tagName]?.icon.orEmpty()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = YubraeSheetShape,
        containerColor = yubraeSheetContainerColor()
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
                        if (tagIconKey.isNotBlank()) {
                            CartoonCategoryIcon(
                                iconKey = tagIconKey,
                                size = 24.dp,
                                fallbackColorHex = stat.colorHex
                            )
                        } else {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Label,
                                contentDescription = null,
                                tint = tagColor,
                                modifier = Modifier.size(22.dp)
                            )
                        }
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
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            Text(
                                text = periodTitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = "${transactions.size} 笔",
                        style = MaterialTheme.typography.bodySmall.tabular(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "标签总额",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false
                    )
                    Text(
                        text = formatPrivacyAmount(
                            privacyMasked,
                            MoneyAmount.format(stat.totalBaseMicros, displayCurrency)
                        ),
                        style = MaterialTheme.typography.titleMedium.tabular(),
                        fontWeight = FontWeight.Bold,
                        color = tagColor,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            HorizontalDivider(
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            )

            if (transactions.isEmpty()) {
                EmptyStateCard(
                    icon = Icons.AutoMirrored.Rounded.Label,
                    title = "暂无明细"
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 440.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    itemsIndexed(
                        items = transactions,
                        key = { _, tx -> tx.id },
                        contentType = { _, _ -> "tag_drilldown_tx" }
                    ) { index, tx ->
                        val cat = tx.categoryId?.let { categoryMap[it] }
                        val categoryDisplayName = remember(cat, categoryMap) {
                            if (cat == null) {
                                null
                            } else {
                                val parentName = cat.parentId?.let { categoryMap[it]?.name }?.takeIf { it.isNotBlank() }
                                if (parentName != null && parentName != cat.name) "$parentName-${cat.name}" else cat.name
                            }
                        }
                        val cleanedNote = remember(tx.note) { KairoFormats.cleanListRowNote(tx.note) }
                        val title = remember(categoryDisplayName, tx.merchant, cleanedNote, stat.tagName) {
                            categoryDisplayName
                                ?: tx.merchant.trim().ifBlank { cleanedNote.ifBlank { "#${stat.tagName}" } }
                        }
                        val detailNote = remember(tx.merchant, cleanedNote, title) {
                            buildList {
                                val merchantTrimmed = tx.merchant.trim()
                                if (merchantTrimmed.isNotEmpty() && !merchantTrimmed.equals(title, ignoreCase = true)) {
                                    add(merchantTrimmed)
                                }
                                val noteTrimmed = cleanedNote.trim()
                                if (noteTrimmed.isNotEmpty() &&
                                    !noteTrimmed.equals(title, ignoreCase = true) &&
                                    none { it.equals(noteTrimmed, ignoreCase = true) }
                                ) {
                                    add(noteTrimmed)
                                }
                            }.joinToString(" · ")
                        }
                        val otherTags = remember(tx.tags, stat.tagName) {
                            TagTextCodec.parse(tx.tags)
                                .filterNot { it.equals(stat.tagName, ignoreCase = true) }
                                .take(2)
                        }

                        StatsDrillDownTransactionRow(
                            tx = tx,
                            title = title,
                            detailNote = detailNote,
                            iconKey = cat?.icon ?: "label",
                            iconColorHex = cat?.colorHex ?: stat.colorHex,
                            isRefund = tx.type == TransactionType.INCOME && tx.refundOfTxId != null,
                            inlineTags = otherTags,
                            account = accountMap[tx.accountId],
                            tagMap = tagMap,
                            displayCurrency = displayCurrency,
                            rates = rates,
                            privacyMasked = privacyMasked,
                            isFirst = index == 0,
                            isLast = index == transactions.lastIndex,
                            onClick = { onEditTransaction(tx) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatsDrillDownTransactionRow(
    tx: TransactionEntity,
    title: String,
    detailNote: String,
    iconKey: String,
    iconColorHex: Long,
    isRefund: Boolean,
    inlineTags: List<String>,
    account: AccountEntity?,
    tagMap: Map<String, TagEntity>,
    displayCurrency: String,
    rates: List<ExchangeRateEntity>,
    privacyMasked: Boolean,
    isFirst: Boolean,
    isLast: Boolean,
    onClick: () -> Unit
) {
    val semantic = KairoTheme.semantic
    val shape = when {
        isFirst && isLast -> StatsDrillDownSingleShape
        isFirst -> StatsDrillDownTopShape
        isLast -> StatsDrillDownBottomShape
        else -> StatsDrillDownMiddleShape
    }
    val accountName = account?.name ?: "账户 #${tx.accountId}"
    val amountColor = if (isRefund || tx.type == TransactionType.INCOME) semantic.income else semantic.expense
    val absOrigMicros = kotlin.math.abs(tx.origAmountMicros)
    val signedOrigMicros = if (isRefund || tx.type == TransactionType.INCOME) absOrigMicros else -absOrigMicros
    val showBaseApprox = !tx.origCurrency.equals(displayCurrency, ignoreCase = true)
    val baseApproxMicros = remember(tx, displayCurrency, rates, showBaseApprox) {
        if (showBaseApprox) convertFrozenBaseAmount(tx, displayCurrency, rates) else 0L
    }
    val hasAttachment = remember(tx.attachmentPaths) {
        tx.attachmentPaths.split(';').any { it.isNotBlank() }
    }
    val timeLabel = remember(tx.timestamp) { formatStatsDrillDownTime(tx.timestamp) }

    val line1Badges = buildList<Triple<String, Color, Color>> {
        if (isRefund) {
            add(Triple("退款", semantic.income.copy(alpha = 0.12f), semantic.income))
        }
        if (tx.discountAmountMicros > 0L) {
            val d = if (privacyMasked) {
                ""
            } else {
                KairoFormats.formatPlainSignedAmount(
                    amountMicros = tx.discountAmountMicros,
                    currencyCode = tx.origCurrency,
                    baseCurrency = displayCurrency,
                    showSign = false
                )
            }
            add(Triple("惠$d", semantic.income.copy(alpha = 0.12f), semantic.income))
        }
        if (tx.feeAmountMicros > 0L) {
            val f = if (privacyMasked) {
                ""
            } else {
                KairoFormats.formatPlainSignedAmount(
                    amountMicros = tx.feeAmountMicros,
                    currencyCode = tx.origCurrency,
                    baseCurrency = displayCurrency,
                    showSign = false
                )
            }
            add(Triple("费$f", semantic.warning.copy(alpha = 0.14f), semantic.warning))
        }
    }.take(2)

    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CategoryIconBadge(
                    iconKey = iconKey,
                    colorHex = iconColorHex,
                    size = 34.dp,
                    iconSize = 17.dp
                )
                Spacer(modifier = Modifier.width(10.dp))

                // 左列：严格 2 行（标题+微标 / 时间+内联标签+备注）
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        line1Badges.forEach { (badgeText, bgColor, fgColor) ->
                            StatsTxDiscountFeeBadge(
                                text = badgeText,
                                containerColor = bgColor,
                                contentColor = fgColor
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(
                            text = timeLabel,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.5.sp).tabular(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                            maxLines = 1,
                            softWrap = false
                        )
                        if (hasAttachment) {
                            Icon(
                                imageVector = Icons.Rounded.AttachFile,
                                contentDescription = "含附件",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                                modifier = Modifier.size(12.dp)
                            )
                        }
                        inlineTags.forEach { tagName ->
                            val tagColorHex = tagMap[tagName]?.colorHex ?: KairoIcons.fallbackTagColorHex(tagName)
                            StatsInlineTagPill(name = tagName, colorHex = tagColorHex)
                        }
                        if (detailNote.isNotEmpty()) {
                            Text(
                                text = detailNote,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.5.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // 右列：等宽金额 + 柔灰账户名（外币时紧凑附折算）
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = formatPrivacyAmount(
                            privacyMasked,
                            KairoFormats.formatPlainSignedAmount(
                                amountMicros = signedOrigMicros,
                                currencyCode = tx.origCurrency,
                                baseCurrency = displayCurrency,
                                showSign = true
                            )
                        ),
                        style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp).tabular(),
                        fontWeight = FontWeight.SemiBold,
                        color = amountColor,
                        maxLines = 1,
                        softWrap = false
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (account != null) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(Color(account.colorHex).copy(alpha = 0.82f))
                            )
                        }
                        val rightSubText = if (showBaseApprox && !privacyMasked) {
                            val approx = KairoFormats.formatPlainSignedAmount(
                                amountMicros = baseApproxMicros,
                                currencyCode = displayCurrency,
                                baseCurrency = displayCurrency,
                                showSign = false
                            )
                            "≈ $approx · $accountName"
                        } else {
                            accountName
                        }
                        Text(
                            text = rightSubText,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp).tabular(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 116.dp)
                        )
                    }
                }
            }
            if (!isLast) {
                HorizontalDivider(
                    modifier = Modifier.padding(start = 56.dp, end = 12.dp),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)
                )
            }
        }
    }
}

@Composable
private fun StatsInlineTagPill(
    name: String,
    colorHex: Long
) {
    val tagColor = Color(colorHex)
    Surface(
        shape = StatsInlineTagShape,
        color = tagColor.copy(alpha = 0.10f)
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.5.sp,
                lineHeight = 13.sp
            ),
            fontWeight = FontWeight.Medium,
            color = tagColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .widthIn(max = 60.dp)
                .padding(horizontal = 4.5.dp, vertical = 1.dp)
        )
    }
}

private fun formatStatsDrillDownTime(epochMillis: Long): String {
    val zone = ZoneId.systemDefault()
    val zdt = Instant.ofEpochMilli(epochMillis).atZone(zone)
    val today = LocalDate.now(zone)
    return when {
        zdt.toLocalDate() == today -> KairoFormats.formatTime(epochMillis)
        zdt.year == today.year -> zdt.format(StatsDrillDownTimeFormatter)
        else -> zdt.format(StatsDrillDownYearTimeFormatter)
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

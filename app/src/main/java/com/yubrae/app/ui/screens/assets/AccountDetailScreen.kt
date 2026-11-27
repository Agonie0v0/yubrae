package com.yubrae.app.ui.screens.assets

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yubrae.app.core.model.CurrencyCatalog
import com.yubrae.app.core.model.MoneyAmount
import com.yubrae.app.data.db.AccountEntity
import com.yubrae.app.data.db.AccountGroup
import com.yubrae.app.data.db.CaptureChannel
import com.yubrae.app.data.db.CategoryEntity
import com.yubrae.app.data.db.InstallmentPlanEntity
import com.yubrae.app.data.db.ReimburseStatus
import com.yubrae.app.data.db.TagTextCodec
import com.yubrae.app.data.db.TransactionEntity
import com.yubrae.app.data.db.TransactionStatus
import com.yubrae.app.data.db.TransactionType
import com.yubrae.app.domain.LedgerAnalytics
import com.yubrae.app.ui.components.AutoResizeAmountText
import com.yubrae.app.ui.components.CartoonAccountIcon
import com.yubrae.app.ui.components.CategoryIconBadge
import com.yubrae.app.ui.components.ChannelChip
import com.yubrae.app.ui.components.ConfirmActionDialog
import com.yubrae.app.ui.components.CurrencyBadge
import com.yubrae.app.ui.components.EmptyStateCard
import com.yubrae.app.ui.components.KairoFormats
import com.yubrae.app.ui.components.KairoIcons
import com.yubrae.app.ui.components.LocalPrivacyMasked
import com.yubrae.app.ui.components.TagBadgeChip
import com.yubrae.app.ui.components.YearMonthPickerDialog
import com.yubrae.app.ui.components.YearPickerDialog
import com.yubrae.app.ui.components.YimuCard
import com.yubrae.app.ui.components.YubraeDialog
import com.yubrae.app.ui.components.formatPrivacyAmount
import com.yubrae.app.ui.theme.KairoTheme
import com.yubrae.app.ui.theme.tabular
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private data class DailyBalancePoint(
    val date: LocalDate,
    val label: String,
    val balanceMicros: Long
)

private enum class AccountHistoryPeriodMode(val label: String) {
    MONTH("按月"),
    YEAR("按年"),
    ALL("全部")
}

private object AccountTxTypeFilter {
    const val ALL = "ALL"
    const val EXPENSE = TransactionType.EXPENSE
    const val INCOME = TransactionType.INCOME
    const val TRANSFER = TransactionType.TRANSFER
    const val ADJUST = TransactionType.ADJUST

    val options = listOf(
        ALL to "全部",
        EXPENSE to "支出",
        INCOME to "收入",
        TRANSFER to "转账",
        ADJUST to "校准"
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AccountDetailScreen(
    account: AccountEntity,
    allAccounts: List<AccountEntity> = emptyList(),
    transactions: List<TransactionEntity>,
    categories: List<CategoryEntity>,
    installments: List<InstallmentPlanEntity>,
    displayCurrency: String,
    convertedBalanceMicros: Long?,
    onBack: () -> Unit,
    onQuickRecord: () -> Unit,
    onQuickTransfer: () -> Unit,
    onCalibrateBalance: (Long, String, Boolean) -> Unit,
    onEditAccount: (AccountEntity) -> Unit,
    onToggleArchive: (Boolean) -> Unit,
    onDeleteAccount: (Long?) -> Unit = {},
    onEditTransaction: (TransactionEntity) -> Unit,
    onSaveInstallment: (InstallmentPlanEntity) -> Unit,
    onRecordInstallmentPeriod: (Long) -> Unit,
    onDeleteInstallment: (Long) -> Unit,
    pinnedCurrencies: List<String> = listOf("CNY", "USD", "EUR", "GBP"),
    onQuickRepayCredit: () -> Unit = onQuickTransfer,
    onDeleteTransaction: (Long) -> Unit = {},
    onDuplicateTransaction: (TransactionEntity) -> Unit = {},
    onRefundTransaction: (TransactionEntity) -> Unit = {},
    onBatchUpdateCategory: (List<Long>, Long?) -> Unit = { _, _ -> },
    onBatchMoveAccount: (List<Long>, Long) -> Unit = { _, _ -> },
    onBatchDeleteTransactions: (List<Long>) -> Unit = {},
    onOpenAccountAliases: () -> Unit = {}
) {
    val privacyMasked = LocalPrivacyMasked.current
    val haptic = LocalHapticFeedback.current
    val zone = remember { ZoneId.systemDefault() }
    val categoryMap = remember(categories) { categories.associateBy { it.id } }
    val accountMap = remember(allAccounts) { allAccounts.associateBy { it.id } }

    // 历史账单筛选与时间维度状态
    var periodMode by remember { mutableStateOf(AccountHistoryPeriodMode.MONTH) }
    var selectedMonth by remember { mutableStateOf(YearMonth.now()) }
    var selectedYear by remember { mutableIntStateOf(YearMonth.now().year) }
    var searchQuery by remember { mutableStateOf("") }
    var typeFilter by remember { mutableStateOf(AccountTxTypeFilter.ALL) }
    var showMonthPicker by remember { mutableStateOf(false) }
    var showYearPicker by remember { mutableStateOf(false) }

    // 趋势图时间跨度（7/30/90/180天）
    var trendDays by remember { mutableIntStateOf(30) }

    // 弹窗与菜单状态
    var showTopMenu by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showNoteDialog by remember { mutableStateOf(false) }
    var showCalibrateDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showAddInstallmentDialog by remember { mutableStateOf(false) }
    var deleteInstallmentTarget by remember { mutableStateOf<InstallmentPlanEntity?>(null) }
    var deleteTxTarget by remember { mutableStateOf<TransactionEntity?>(null) }

    // 多选批量管理状态
    var isMultiSelectMode by remember { mutableStateOf(false) }
    var selectedTxIds by remember { mutableStateOf(emptySet<Long>()) }
    var showBatchCategoryDialog by remember { mutableStateOf(false) }
    var showBatchMoveAccountDialog by remember { mutableStateOf(false) }
    var showBatchDeleteConfirm by remember { mutableStateOf(false) }

    BackHandler(enabled = isMultiSelectMode) {
        isMultiSelectMode = false
        selectedTxIds = emptySet()
    }

    val accountTransactions = remember(transactions, account.id) {
        transactions
            .filter { it.accountId == account.id || it.toAccountId == account.id }
            .sortedByDescending { it.timestamp }
    }

    val accountInstallments = remember(installments, account.id) {
        installments
            .filter { it.accountId == account.id }
            .sortedWith(
                compareBy<InstallmentPlanEntity> { it.completedPeriods >= it.totalPeriods }
                    .thenBy { it.nextDueMillis }
            )
    }

    // 选定时间范围内的账户账单
    val periodTransactions = remember(accountTransactions, periodMode, selectedMonth, selectedYear, zone) {
        when (periodMode) {
            AccountHistoryPeriodMode.MONTH -> {
                val monthStart = selectedMonth.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
                val monthEnd = selectedMonth.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1L
                accountTransactions.filter { it.timestamp in monthStart..monthEnd }
            }
            AccountHistoryPeriodMode.YEAR -> {
                val yearStart = LocalDate.of(selectedYear, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli()
                val yearEnd = LocalDate.of(selectedYear + 1, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli() - 1L
                accountTransactions.filter { it.timestamp in yearStart..yearEnd }
            }
            AccountHistoryPeriodMode.ALL -> accountTransactions
        }
    }

    // Hero 卡当前选定时间范围的流入 / 流出 / 净变动
    val (periodInflowMicros, periodOutflowMicros) = remember(periodTransactions, account.id) {
        var inflow = 0L
        var outflow = 0L
        periodTransactions.forEach { tx ->
            if (tx.status == TransactionStatus.CONFIRMED) {
                val delta = accountDeltaForTransaction(tx, account.id)
                if (delta > 0L) {
                    inflow += delta
                } else if (delta < 0L) {
                    outflow += -delta
                }
            }
        }
        inflow to outflow
    }

    val periodPrefix = remember(periodMode, selectedMonth, selectedYear) {
        when (periodMode) {
            AccountHistoryPeriodMode.MONTH -> "${selectedMonth.monthValue}月"
            AccountHistoryPeriodMode.YEAR -> "${selectedYear}年"
            AccountHistoryPeriodMode.ALL -> "累计"
        }
    }

    // 搜索匹配（先按搜索词过滤，再计算类型计数胶囊与最终展示列表）
    val searchMatchedTransactions = remember(periodTransactions, searchQuery, categoryMap, accountMap, account.id) {
        val normalized = searchQuery.trim().lowercase(Locale.ROOT)
        if (normalized.isEmpty()) {
            periodTransactions
        } else {
            val tokens = normalized.split(Regex("\\s+")).filter { it.isNotEmpty() }
            periodTransactions.filter { tx ->
                matchesAccountTransactionQuery(
                    tx = tx,
                    tokens = tokens,
                    accountId = account.id,
                    categoryMap = categoryMap,
                    accountMap = accountMap
                )
            }
        }
    }

    val typeCounts = remember(searchMatchedTransactions) {
        val counts = HashMap<String, Int>(6)
        counts[AccountTxTypeFilter.ALL] = searchMatchedTransactions.size
        searchMatchedTransactions.forEach { tx ->
            counts[tx.type] = (counts[tx.type] ?: 0) + 1
        }
        counts
    }

    val filteredTransactions = remember(searchMatchedTransactions, typeFilter) {
        if (typeFilter == AccountTxTypeFilter.ALL) {
            searchMatchedTransactions
        } else {
            searchMatchedTransactions.filter { it.type == typeFilter }
        }
    }

    val visibleTxIds = remember(filteredTransactions) {
        filteredTransactions.map { it.id }.toSet()
    }

    // 切换时间/筛选后仅保留当前可见的勾选项，避免误操作隐藏账单
    LaunchedEffect(visibleTxIds, isMultiSelectMode) {
        if (!isMultiSelectMode) {
            if (selectedTxIds.isNotEmpty()) selectedTxIds = emptySet()
        } else {
            val cleaned = selectedTxIds.intersect(visibleTxIds)
            if (cleaned.size != selectedTxIds.size) {
                selectedTxIds = cleaned
            }
        }
    }

    val groupedDayTransactions = remember(filteredTransactions, zone) {
        filteredTransactions
            .groupBy { Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate() }
            .entries
            .sortedByDescending { it.key }
            .map { it.key to it.value }
    }

    val dailyTrendPoints = remember(accountTransactions, account.balanceMicros, account.id, trendDays, zone) {
        buildDailyAccountTrend(
            accountId = account.id,
            currentBalanceMicros = account.balanceMicros,
            accountTransactions = accountTransactions,
            days = trendDays,
            zoneId = zone
        )
    }

    val availableMoveTargets = remember(allAccounts, account.id) {
        allAccounts
            .filter { !it.archived && it.id != account.id }
            .sortedWith(compareBy({ it.sortOrder }, { it.id }))
    }

    val hasActiveFilter = searchQuery.isNotBlank() || typeFilter != AccountTxTypeFilter.ALL

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = if (isMultiSelectMode) "批量管理 (${selectedTxIds.size})" else account.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (!isMultiSelectMode && account.archived) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.errorContainer
                            ) {
                                Text(
                                    text = "已归档",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (isMultiSelectMode) {
                                isMultiSelectMode = false
                                selectedTxIds = emptySet()
                            } else {
                                onBack()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = if (isMultiSelectMode) Icons.Rounded.Close else Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = if (isMultiSelectMode) "退出多选" else "返回"
                        )
                    }
                },
                actions = {
                    if (isMultiSelectMode) {
                        val allSelected = visibleTxIds.isNotEmpty() && visibleTxIds.all { it in selectedTxIds }
                        TextButton(
                            onClick = {
                                selectedTxIds = if (allSelected) emptySet() else visibleTxIds
                            }
                        ) {
                            Text(
                                text = if (allSelected) "取消全选" else "全选",
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    } else {
                        IconButton(onClick = { showEditDialog = true }) {
                            Icon(Icons.Rounded.Edit, contentDescription = "编辑账户")
                        }
                        Box {
                            IconButton(onClick = { showTopMenu = true }) {
                                Icon(Icons.Rounded.MoreVert, contentDescription = "更多操作")
                            }
                            DropdownMenu(
                                expanded = showTopMenu,
                                onDismissRequest = { showTopMenu = false }
                            ) {
                                DropdownMenuItem(
                                    leadingIcon = { Icon(Icons.Rounded.Fingerprint, contentDescription = null) },
                                    text = { Text("管理自动记账来源指纹") },
                                    onClick = {
                                        showTopMenu = false
                                        onOpenAccountAliases()
                                    }
                                )
                                DropdownMenuItem(
                                    leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                                    text = { Text("编辑账户") },
                                    onClick = {
                                        showTopMenu = false
                                        showEditDialog = true
                                    }
                                )
                                DropdownMenuItem(
                                    leadingIcon = { Icon(Icons.Rounded.Tune, contentDescription = null) },
                                    text = { Text("校准余额") },
                                    onClick = {
                                        showTopMenu = false
                                        showCalibrateDialog = true
                                    }
                                )
                                DropdownMenuItem(
                                    leadingIcon = {
                                        Icon(
                                            imageVector = if (account.archived) Icons.Rounded.Unarchive else Icons.Rounded.Archive,
                                            contentDescription = null
                                        )
                                    },
                                    text = { Text(if (account.archived) "恢复归档" else "归档账户") },
                                    onClick = {
                                        showTopMenu = false
                                        onToggleArchive(!account.archived)
                                    }
                                )
                                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                                DropdownMenuItem(
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Rounded.Delete,
                                            contentDescription = null,
                                            tint = KairoTheme.semantic.expense
                                        )
                                    },
                                    text = {
                                        Text(
                                            text = "删除账户",
                                            color = KairoTheme.semantic.expense
                                        )
                                    },
                                    onClick = {
                                        showTopMenu = false
                                        showDeleteDialog = true
                                    }
                                )
                            }
                        }
                    }
                }
            )
        },
        bottomBar = {
            AnimatedVisibility(
                visible = isMultiSelectMode,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                AccountBatchActionBottomBar(
                    selectedCount = selectedTxIds.size,
                    totalVisibleCount = visibleTxIds.size,
                    allSelected = visibleTxIds.isNotEmpty() && visibleTxIds.all { it in selectedTxIds },
                    canMoveAccount = availableMoveTargets.isNotEmpty(),
                    onToggleSelectAll = {
                        selectedTxIds = if (visibleTxIds.isNotEmpty() && visibleTxIds.all { it in selectedTxIds }) {
                            emptySet()
                        } else {
                            visibleTxIds
                        }
                    },
                    onBatchCategory = {
                        if (selectedTxIds.isNotEmpty()) showBatchCategoryDialog = true
                    },
                    onBatchMoveAccount = {
                        if (selectedTxIds.isNotEmpty()) showBatchMoveAccountDialog = true
                    },
                    onBatchDelete = {
                        if (selectedTxIds.isNotEmpty()) showBatchDeleteConfirm = true
                    },
                    onExit = {
                        isMultiSelectMode = false
                        selectedTxIds = emptySet()
                    }
                )
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding() + 8.dp,
                bottom = innerPadding.calculateBottomPadding() + 32.dp,
                start = 16.dp,
                end = 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. 顶部 Hero 信息卡（余额、备注快捷编辑、当前时间范围3列汇总、4枚防溢出快捷操作按钮）
            item(key = "account_hero") {
                AccountDetailHeroCard(
                    account = account,
                    displayCurrency = displayCurrency,
                    convertedBalanceMicros = convertedBalanceMicros,
                    periodPrefix = periodPrefix,
                    periodInflowMicros = periodInflowMicros,
                    periodOutflowMicros = periodOutflowMicros,
                    privacyMasked = privacyMasked,
                    onClickNote = { showNoteDialog = true },
                    onQuickRecord = onQuickRecord,
                    onQuickTransferOrRepay = if (account.groupType == AccountGroup.CREDIT) {
                        onQuickRepayCredit
                    } else {
                        onQuickTransfer
                    },
                    onCalibrate = { showCalibrateDialog = true },
                    onEditAccount = { showEditDialog = true }
                )
            }

            // 2. 账户余额走势图（7天 / 30天 / 90天 / 180天 + 最高/最低/净变动 + 触控选点浮层）
            item(key = "balance_trend_chart") {
                AccountBalanceTrendCard(
                    points = dailyTrendPoints,
                    currencyCode = account.currencyCode,
                    accentColor = Color(account.colorHex),
                    trendDays = trendDays,
                    privacyMasked = privacyMasked,
                    onChangeDays = { trendDays = it }
                )
            }

            // 3. 信用卡真实账期面板 + 分期管理
            if (account.groupType == AccountGroup.CREDIT) {
                item(key = "credit_quota_panel") {
                    val cycleSummary = remember(account, transactions) {
                        LedgerAnalytics.computeCreditBillingCycleSummary(account, transactions)
                    }
                    CreditAccountSummaryCard(
                        account = account,
                        cycleStartMillis = cycleSummary.cycleStartMillis,
                        cycleEndMillis = cycleSummary.cycleEndMillis,
                        cycleSpendMicros = cycleSummary.cycleSpendMicros,
                        cycleRepaidMicros = cycleSummary.cycleRepaidMicros,
                        zone = zone,
                        privacyMasked = privacyMasked,
                        onRepayClick = onQuickRepayCredit
                    )
                }

                item(key = "credit_installments_panel") {
                    InstallmentsManagementCard(
                        account = account,
                        installments = accountInstallments,
                        categoryMap = categoryMap,
                        privacyMasked = privacyMasked,
                        onAddInstallment = { showAddInstallmentDialog = true },
                        onRecordPeriod = onRecordInstallmentPeriod,
                        onRequestDelete = { deleteInstallmentTarget = it }
                    )
                }
            }

            // 4. 成熟的历史账单工作台控制区（按月/按年/全部 + 搜索 + 类型胶囊 + 多选开关）
            item(key = "tx_workbench_header") {
                AccountHistoryWorkbenchCard(
                    periodMode = periodMode,
                    selectedMonth = selectedMonth,
                    selectedYear = selectedYear,
                    totalInPeriodCount = periodTransactions.size,
                    filteredCount = filteredTransactions.size,
                    searchQuery = searchQuery,
                    selectedType = typeFilter,
                    typeCounts = typeCounts,
                    isMultiSelectMode = isMultiSelectMode,
                    accentColor = Color(account.colorHex),
                    onSelectPeriodMode = { periodMode = it },
                    onPrevMonth = { selectedMonth = selectedMonth.minusMonths(1) },
                    onNextMonth = { selectedMonth = selectedMonth.plusMonths(1) },
                    onResetCurrentMonth = { selectedMonth = YearMonth.now() },
                    onOpenMonthPicker = { showMonthPicker = true },
                    onPrevYear = { selectedYear = (selectedYear - 1).coerceAtLeast(1970) },
                    onNextYear = { selectedYear += 1 },
                    onResetCurrentYear = { selectedYear = YearMonth.now().year },
                    onOpenYearPicker = { showYearPicker = true },
                    onSearchQueryChange = { searchQuery = it },
                    onSelectType = { typeFilter = it },
                    onToggleMultiSelect = {
                        if (isMultiSelectMode) {
                            isMultiSelectMode = false
                            selectedTxIds = emptySet()
                        } else {
                            isMultiSelectMode = true
                        }
                    },
                    onResetFilters = {
                        searchQuery = ""
                        typeFilter = AccountTxTypeFilter.ALL
                    }
                )
            }

            // 5. 按日分组流水列表或空状态
            if (groupedDayTransactions.isEmpty()) {
                item(key = "empty_account_tx") {
                    when {
                        hasActiveFilter -> EmptyStateCard(
                            icon = Icons.Rounded.Search,
                            title = "未找到匹配的账单",
                            subtitle = "试试调整关键词或切换交易类型筛选",
                            actionLabel = "重置筛选",
                            onAction = {
                                searchQuery = ""
                                typeFilter = AccountTxTypeFilter.ALL
                            }
                        )
                        periodMode != AccountHistoryPeriodMode.ALL && accountTransactions.isNotEmpty() -> EmptyStateCard(
                            icon = Icons.AutoMirrored.Rounded.ReceiptLong,
                            title = when (periodMode) {
                                AccountHistoryPeriodMode.MONTH -> "${selectedMonth.year}年${selectedMonth.monthValue}月暂无账单"
                                AccountHistoryPeriodMode.YEAR -> "${selectedYear}年暂无账单"
                                AccountHistoryPeriodMode.ALL -> "暂无账单"
                            },
                            subtitle = "该账户共有 ${accountTransactions.size} 笔历史账单",
                            actionLabel = "查看全部历史",
                            onAction = { periodMode = AccountHistoryPeriodMode.ALL }
                        )
                        else -> EmptyStateCard(
                            icon = Icons.AutoMirrored.Rounded.ReceiptLong,
                            title = "暂无流水记录",
                            subtitle = "记一笔收支或转账，随时掌握账户余额变化",
                            actionLabel = "记一笔",
                            onAction = onQuickRecord
                        )
                    }
                }
            } else {
                groupedDayTransactions.forEach { (date, dayTxs) ->
                    item(key = "day_$date") {
                        AccountDayTransactionGroupCard(
                            date = date,
                            account = account,
                            transactions = dayTxs,
                            categoryMap = categoryMap,
                            accountMap = accountMap,
                            privacyMasked = privacyMasked,
                            isMultiSelectMode = isMultiSelectMode,
                            selectedTxIds = selectedTxIds,
                            onClickTransaction = { tx ->
                                if (isMultiSelectMode) {
                                    selectedTxIds = if (tx.id in selectedTxIds) {
                                        selectedTxIds - tx.id
                                    } else {
                                        selectedTxIds + tx.id
                                    }
                                } else {
                                    onEditTransaction(tx)
                                }
                            },
                            onLongClickTransaction = { tx ->
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                if (!isMultiSelectMode) {
                                    isMultiSelectMode = true
                                    selectedTxIds = setOf(tx.id)
                                } else {
                                    selectedTxIds = if (tx.id in selectedTxIds) {
                                        selectedTxIds - tx.id
                                    } else {
                                        selectedTxIds + tx.id
                                    }
                                }
                            },
                            onEditTransaction = onEditTransaction,
                            onDuplicateTransaction = onDuplicateTransaction,
                            onRefundTransaction = onRefundTransaction,
                            onRequestDeleteTransaction = { deleteTxTarget = it },
                            onStartMultiSelectWith = { tx ->
                                isMultiSelectMode = true
                                selectedTxIds = selectedTxIds + tx.id
                            }
                        )
                    }
                }
            }
        }
    }

    // 快捷编辑账户备注弹窗
    if (showNoteDialog) {
        QuickEditAccountNoteDialog(
            account = account,
            onSave = { newNote ->
                onEditAccount(
                    account.copy(
                        note = newNote.trim(),
                        updatedAt = System.currentTimeMillis()
                    )
                )
                showNoteDialog = false
            },
            onDismiss = { showNoteDialog = false }
        )
    }

    // 编辑账户弹窗
    if (showEditDialog) {
        AccountEditorDialog(
            existing = account,
            pinnedCurrencies = pinnedCurrencies,
            onSave = { updated ->
                onEditAccount(updated)
                showEditDialog = false
            },
            onDismiss = { showEditDialog = false }
        )
    }

    // 校准余额弹窗
    if (showCalibrateDialog) {
        CalibrateBalanceDialog(
            account = account,
            privacyMasked = privacyMasked,
            onConfirm = { targetMicros, note, createRecord ->
                onCalibrateBalance(targetMicros, note, createRecord)
                showCalibrateDialog = false
            },
            onDismiss = { showCalibrateDialog = false }
        )
    }

    // 新建分期弹窗
    if (showAddInstallmentDialog) {
        InstallmentEditorDialog(
            account = account,
            expenseCategories = categories.filter { it.type == TransactionType.EXPENSE },
            onSave = { plan ->
                onSaveInstallment(plan)
                showAddInstallmentDialog = false
            },
            onDismiss = { showAddInstallmentDialog = false }
        )
    }

    // 删除分期确认弹窗
    deleteInstallmentTarget?.let { plan ->
        ConfirmActionDialog(
            title = "删除分期「${plan.name}」？",
            message = "删除分期计划不会删除已经记入流水的历史账单。",
            confirmText = "删除",
            isDestructive = true,
            onConfirm = {
                onDeleteInstallment(plan.id)
                deleteInstallmentTarget = null
            },
            onDismiss = { deleteInstallmentTarget = null }
        )
    }

    // 删除单笔账单二次确认弹窗
    deleteTxTarget?.let { tx ->
        val titleDesc = resolveTransactionTitle(tx, account.id, categoryMap, accountMap)
        ConfirmActionDialog(
            title = "删除该笔账单？",
            message = "删除「$titleDesc」将自动回滚关联账户余额，此操作不可撤销。",
            confirmText = "删除",
            isDestructive = true,
            onConfirm = {
                onDeleteTransaction(tx.id)
                deleteTxTarget = null
            },
            onDismiss = { deleteTxTarget = null }
        )
    }

    // 删除账户弹窗
    if (showDeleteDialog) {
        DeleteAccountConfirmDialog(
            account = account,
            linkedTxCount = accountTransactions.size,
            availableTargets = availableMoveTargets,
            onConfirmDelete = { migrateToId ->
                showDeleteDialog = false
                onDeleteAccount(migrateToId)
            },
            onDismiss = { showDeleteDialog = false }
        )
    }

    // 年月 / 年份快速选择器
    if (showMonthPicker) {
        YearMonthPickerDialog(
            initialYearMonth = selectedMonth,
            onSelect = { selectedMonth = it },
            onDismiss = { showMonthPicker = false }
        )
    }

    if (showYearPicker) {
        YearPickerDialog(
            initialYear = selectedYear,
            onSelect = { selectedYear = it },
            onDismiss = { showYearPicker = false }
        )
    }

    // 批量改分类弹窗
    if (showBatchCategoryDialog) {
        val selectedTxs = remember(filteredTransactions, selectedTxIds) {
            filteredTransactions.filter { it.id in selectedTxIds }
        }
        val preferIncome = remember(selectedTxs) {
            val incCount = selectedTxs.count { it.type == TransactionType.INCOME }
            val expCount = selectedTxs.count { it.type == TransactionType.EXPENSE }
            incCount > expCount
        }
        AccountBatchCategoryDialog(
            categories = categories,
            selectedCount = selectedTxIds.size,
            initialType = if (preferIncome) TransactionType.INCOME else TransactionType.EXPENSE,
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

    // 批量改账户弹窗
    if (showBatchMoveAccountDialog) {
        AccountBatchMoveAccountDialog(
            sourceAccount = account,
            targetAccounts = availableMoveTargets,
            selectedCount = selectedTxIds.size,
            privacyMasked = privacyMasked,
            onConfirmMove = { targetAccountId ->
                val ids = selectedTxIds.toList()
                showBatchMoveAccountDialog = false
                isMultiSelectMode = false
                selectedTxIds = emptySet()
                onBatchMoveAccount(ids, targetAccountId)
            },
            onDismiss = { showBatchMoveAccountDialog = false }
        )
    }

    // 批量删除确认弹窗
    if (showBatchDeleteConfirm) {
        ConfirmActionDialog(
            title = "批量删除 ${selectedTxIds.size} 笔账单？",
            message = "删除所选账单后将自动精确回滚关联账户余额，此操作不可撤销。",
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
private fun AccountDetailHeroCard(
    account: AccountEntity,
    displayCurrency: String,
    convertedBalanceMicros: Long?,
    periodPrefix: String,
    periodInflowMicros: Long,
    periodOutflowMicros: Long,
    privacyMasked: Boolean,
    onClickNote: () -> Unit,
    onQuickRecord: () -> Unit,
    onQuickTransferOrRepay: () -> Unit,
    onCalibrate: () -> Unit,
    onEditAccount: () -> Unit
) {
    val semantic = KairoTheme.semantic
    val accentColor = Color(account.colorHex)
    val gradientColors = if (semantic.isDark) {
        listOf(
            accentColor.copy(alpha = 0.28f),
            MaterialTheme.colorScheme.surfaceContainerHigh
        )
    } else {
        listOf(
            accentColor.copy(alpha = 0.16f),
            accentColor.copy(alpha = 0.05f)
        )
    }
    val pillColor = if (semantic.isDark) {
        MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.74f)
    } else {
        Color.White.copy(alpha = 0.86f)
    }
    val isForeign = !account.currencyCode.equals(displayCurrency, ignoreCase = true)
    val periodNetMicros = periodInflowMicros - periodOutflowMicros
    val isCredit = account.groupType == AccountGroup.CREDIT

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.28f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .background(Brush.linearGradient(colors = gradientColors))
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // 头部：品牌图标 + 账户名/分组/状态标签 + 币种徽标
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CartoonAccountIcon(
                            iconKey = account.icon,
                            accountName = account.name,
                            colorHex = account.colorHex,
                            size = 44.dp
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(
                                text = account.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = AccountGroup.labelZh(account.groupType),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    softWrap = false
                                )
                                if (!account.includeInNetWorth) {
                                    HeroStatusBadge(
                                        text = "不计净资产",
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (account.archived) {
                                    HeroStatusBadge(
                                        text = "已归档",
                                        containerColor = MaterialTheme.colorScheme.errorContainer,
                                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    CurrencyBadge(currencyCode = account.currencyCode, selected = true)
                }

                // 余额主展示区
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = if (isCredit) "当前账面余额" else "当前账户余额",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    AutoResizeAmountText(
                        text = formatPrivacyAmount(
                            privacyMasked,
                            MoneyAmount.format(
                                amountMicros = account.balanceMicros,
                                currencyCode = account.currencyCode,
                                showCurrencyCode = !account.currencyCode.equals("CNY", ignoreCase = true)
                            )
                        ),
                        baseStyle = MaterialTheme.typography.displaySmall.tabular(),
                        color = if (account.balanceMicros < 0L) semantic.expense else MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        minFontSize = 18.sp,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (isForeign && convertedBalanceMicros != null) {
                        Text(
                            text = formatPrivacyAmount(
                                privacyMasked,
                                "≈ ${MoneyAmount.format(convertedBalanceMicros, displayCurrency, showCurrencyCode = true)}"
                            ),
                            style = MaterialTheme.typography.bodyMedium.tabular(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // 可点击快捷编辑的账户备注条
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = pillColor,
                    border = BorderStroke(0.5.dp, accentColor.copy(alpha = 0.22f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onClickNote)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 11.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.EditNote,
                            contentDescription = null,
                            tint = if (account.note.isNotBlank()) accentColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = account.note.ifBlank { "点击添加账户备注，如尾号、用途或免年费规则" },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (account.note.isNotBlank()) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                            },
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = Icons.Rounded.Edit,
                            contentDescription = "编辑备注",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                // 当前选定时间范围 3 列紧凑汇总：期间流入 / 期间流出 / 净变动
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = pillColor,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        HeroPeriodMetricCell(
                            label = "${periodPrefix}流入",
                            value = formatPrivacyAmount(
                                privacyMasked,
                                MoneyAmount.format(periodInflowMicros, account.currencyCode, showSign = periodInflowMicros > 0L)
                            ),
                            valueColor = semantic.income,
                            modifier = Modifier.weight(1f)
                        )
                        HeroPeriodMetricCell(
                            label = "${periodPrefix}流出",
                            value = formatPrivacyAmount(
                                privacyMasked,
                                MoneyAmount.format(periodOutflowMicros, account.currencyCode)
                            ),
                            valueColor = semantic.expense,
                            modifier = Modifier.weight(1f)
                        )
                        HeroPeriodMetricCell(
                            label = "净变动",
                            value = formatPrivacyAmount(
                                privacyMasked,
                                MoneyAmount.format(periodNetMicros, account.currencyCode, showSign = true)
                            ),
                            valueColor = when {
                                periodNetMicros > 0L -> semantic.income
                                periodNetMicros < 0L -> semantic.expense
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // 4 枚高质感防溢出快捷操作按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HeroQuickActionTile(
                        icon = Icons.Rounded.Add,
                        label = "记一笔",
                        containerColor = accentColor,
                        contentColor = Color.White,
                        iconBgColor = Color.White.copy(alpha = 0.20f),
                        onClick = onQuickRecord,
                        modifier = Modifier.weight(1f)
                    )
                    HeroQuickActionTile(
                        icon = Icons.Rounded.SwapHoriz,
                        label = if (isCredit) "还信用卡" else "转账",
                        containerColor = pillColor,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        iconBgColor = accentColor.copy(alpha = 0.14f),
                        iconTint = accentColor,
                        borderColor = accentColor.copy(alpha = 0.25f),
                        onClick = onQuickTransferOrRepay,
                        modifier = Modifier.weight(1f)
                    )
                    HeroQuickActionTile(
                        icon = Icons.Rounded.Tune,
                        label = "校准余额",
                        containerColor = pillColor,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        iconBgColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
                        iconTint = MaterialTheme.colorScheme.primary,
                        borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.32f),
                        onClick = onCalibrate,
                        modifier = Modifier.weight(1f)
                    )
                    HeroQuickActionTile(
                        icon = Icons.Rounded.Edit,
                        label = "编辑账户",
                        containerColor = pillColor,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        iconBgColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.78f),
                        iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                        borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.32f),
                        onClick = onEditAccount,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroStatusBadge(
    text: String,
    containerColor: Color,
    contentColor: Color
) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = containerColor
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = contentColor,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
        )
    }
}

@Composable
private fun HeroPeriodMetricCell(
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
        AutoResizeAmountText(
            text = value,
            baseStyle = MaterialTheme.typography.titleSmall.tabular(),
            color = valueColor,
            fontWeight = FontWeight.Bold,
            minFontSize = 10.sp,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun HeroQuickActionTile(
    icon: ImageVector,
    label: String,
    containerColor: Color,
    contentColor: Color,
    iconBgColor: Color,
    iconTint: Color = contentColor,
    borderColor: Color? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = containerColor,
        border = borderColor?.let { BorderStroke(0.6.dp, it) },
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 9.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(iconBgColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = iconTint,
                    modifier = Modifier.size(16.dp)
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.5.sp),
                fontWeight = FontWeight.SemiBold,
                color = contentColor,
                maxLines = 1,
                softWrap = false,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun QuickEditAccountNoteDialog(
    account: AccountEntity,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var noteText by remember(account.id, account.note) { mutableStateOf(account.note) }

    YubraeDialog(
        onDismissRequest = onDismiss,
        title = "编辑账户备注",
        subtitle = "${account.name} · ${AccountGroup.labelZh(account.groupType)}",
        icon = Icons.Rounded.EditNote,
        confirmButton = {
            Button(
                onClick = { onSave(noteText) },
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("保存", maxLines = 1, softWrap = false)
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (noteText.isNotEmpty()) {
                    TextButton(
                        onClick = { noteText = "" },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("清空", maxLines = 1, softWrap = false)
                    }
                }
                TextButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("取消", maxLines = 1, softWrap = false)
                }
            }
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = noteText,
                onValueChange = { if (it.length <= 200) noteText = it },
                label = { Text("账户备注") },
                placeholder = { Text("例如：工资卡 · 尾号 8821 · 年刷 6 笔免年费") },
                minLines = 2,
                maxLines = 4,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = "${noteText.length}/200",
                style = MaterialTheme.typography.labelSmall.tabular(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.End)
            )
        }
    }
}

@Composable
private fun AccountBalanceTrendCard(
    points: List<DailyBalancePoint>,
    currencyCode: String,
    accentColor: Color,
    trendDays: Int,
    privacyMasked: Boolean,
    onChangeDays: (Int) -> Unit
) {
    val semantic = KairoTheme.semantic
    val lineColor = if (accentColor.alpha > 0.1f) accentColor else MaterialTheme.colorScheme.primary
    var selectedIdx by remember(trendDays, points.size) {
        mutableIntStateOf((points.size - 1).coerceAtLeast(0))
    }
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 10.sp
    )
    val tooltipStyle = MaterialTheme.typography.labelSmall.copy(
        color = Color.White,
        fontWeight = FontWeight.SemiBold,
        fontSize = 10.sp
    )
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    val surfaceBgColor = MaterialTheme.colorScheme.surfaceContainerLowest

    YimuCard(cornerRadius = 20.dp) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "余额走势",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false
                )
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    listOf(7, 30, 90, 180).forEach { days ->
                        val active = trendDays == days
                        Surface(
                            shape = CircleShape,
                            color = if (active) lineColor.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = if (active) BorderStroke(0.8.dp, lineColor.copy(alpha = 0.55f)) else null,
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable { onChangeDays(days) }
                        ) {
                            Text(
                                text = "${days}天",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                color = if (active) lineColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            if (points.isEmpty()) {
                Text(
                    text = "暂无余额走势数据",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 20.dp)
                )
            } else {
                val minMicros = points.minOf { it.balanceMicros }
                val maxMicros = points.maxOf { it.balanceMicros }
                val netChangeMicros = points.last().balanceMicros - points.first().balanceMicros
                val spanMicros = (maxMicros - minMicros).coerceAtLeast(0L)

                // 周期摘要：最高余额 / 最低余额 / 区间净变动
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TrendMetricChip(
                        label = "最高余额",
                        value = formatPrivacyAmount(privacyMasked, MoneyAmount.format(maxMicros, currencyCode)),
                        valueColor = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    TrendMetricChip(
                        label = "最低余额",
                        value = formatPrivacyAmount(privacyMasked, MoneyAmount.format(minMicros, currencyCode)),
                        valueColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    TrendMetricChip(
                        label = "区间变动",
                        value = formatPrivacyAmount(privacyMasked, MoneyAmount.format(netChangeMicros, currencyCode, showSign = true)),
                        valueColor = when {
                            netChangeMicros > 0L -> semantic.income
                            netChangeMicros < 0L -> semantic.expense
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.weight(1f)
                    )
                }

                // 触控选中点详情提示条
                val safeIdx = selectedIdx.coerceIn(0, points.lastIndex)
                val activePoint = points[safeIdx]
                val dayWeek = activePoint.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.SIMPLIFIED_CHINESE)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = lineColor.copy(alpha = 0.08f),
                    border = BorderStroke(0.5.dp, lineColor.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${activePoint.date} $dayWeek",
                            style = MaterialTheme.typography.labelMedium.tabular(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            softWrap = false
                        )
                        Text(
                            text = formatPrivacyAmount(
                                privacyMasked,
                                MoneyAmount.format(activePoint.balanceMicros, currencyCode, showCurrencyCode = true)
                            ),
                            style = MaterialTheme.typography.titleSmall.tabular(),
                            color = lineColor,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(186.dp)
                        .pointerInput(points) {
                            detectTapGestures { offset ->
                                val leftPad = 50.dp.toPx()
                                val rightPad = 12.dp.toPx()
                                val chartW = (size.width - leftPad - rightPad).coerceAtLeast(1f)
                                if (points.size > 1) {
                                    val fraction = ((offset.x - leftPad) / chartW).coerceIn(0f, 1f)
                                    selectedIdx = Math.round(fraction * points.lastIndex)
                                        .coerceIn(0, points.lastIndex)
                                }
                            }
                        }
                        .pointerInput(points) {
                            detectHorizontalDragGestures { change, _ ->
                                change.consume()
                                val leftPad = 50.dp.toPx()
                                val rightPad = 12.dp.toPx()
                                val chartW = (size.width - leftPad - rightPad).coerceAtLeast(1f)
                                if (points.size > 1) {
                                    val fraction = ((change.position.x - leftPad) / chartW).coerceIn(0f, 1f)
                                    selectedIdx = Math.round(fraction * points.lastIndex)
                                        .coerceIn(0, points.lastIndex)
                                }
                            }
                        }
                ) {
                    val leftPad = 50.dp.toPx()
                    val rightPad = 12.dp.toPx()
                    val topPad = 24.dp.toPx()
                    val bottomPad = 24.dp.toPx()
                    val chartW = (size.width - leftPad - rightPad).coerceAtLeast(1f)
                    val chartH = (size.height - topPad - bottomPad).coerceAtLeast(1f)

                    // 网格线与 Y 轴刻度
                    val gridSteps = 2
                    for (i in 0..gridSteps) {
                        val ratio = i.toFloat() / gridSteps.toFloat()
                        val y = topPad + chartH * (1f - ratio)
                        drawLine(
                            color = gridColor,
                            start = Offset(leftPad, y),
                            end = Offset(size.width - rightPad, y),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = if (i == 0) null else PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                        )
                        val tickLabel = if (privacyMasked) {
                            "****"
                        } else {
                            val tickMicros = if (spanMicros == 0L) {
                                maxMicros
                            } else {
                                minMicros + BigDecimal(spanMicros)
                                    .multiply(BigDecimal(i))
                                    .divide(BigDecimal(gridSteps), 0, RoundingMode.HALF_UP)
                                    .toLong()
                            }
                            formatCompactScale(tickMicros, currencyCode)
                        }
                        val measured = textMeasurer.measure(tickLabel, labelStyle)
                        drawText(
                            textLayoutResult = measured,
                            topLeft = Offset(
                                x = (leftPad - measured.size.width - 6.dp.toPx()).coerceAtLeast(0f),
                                y = (y - measured.size.height / 2f).coerceIn(0f, size.height - measured.size.height)
                            )
                        )
                    }

                    val stepX = if (points.size > 1) chartW / (points.size - 1).toFloat() else 0f
                    val coords = points.mapIndexed { idx, pt ->
                        val x = if (points.size > 1) leftPad + idx * stepX else leftPad + chartW / 2f
                        val yRatio = if (spanMicros == 0L) {
                            0.5f
                        } else {
                            ((pt.balanceMicros - minMicros).toDouble() / spanMicros.toDouble()).toFloat().coerceIn(0f, 1f)
                        }
                        val y = topPad + chartH * (1f - yRatio)
                        Offset(x, y)
                    }

                    if (coords.size >= 2) {
                        val linePath = Path().apply {
                            moveTo(coords.first().x, coords.first().y)
                            for (i in 1 until coords.size) {
                                val prev = coords[i - 1]
                                val curr = coords[i]
                                val midX = (prev.x + curr.x) / 2f
                                cubicTo(midX, prev.y, midX, curr.y, curr.x, curr.y)
                            }
                        }
                        val fillPath = Path().apply {
                            addPath(linePath)
                            lineTo(coords.last().x, topPad + chartH)
                            lineTo(coords.first().x, topPad + chartH)
                            close()
                        }

                        drawPath(
                            path = fillPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(lineColor.copy(alpha = 0.26f), lineColor.copy(alpha = 0.01f)),
                                startY = topPad,
                                endY = topPad + chartH
                            )
                        )
                        drawPath(
                            path = linePath,
                            color = lineColor,
                            style = Stroke(
                                width = 2.4.dp.toPx(),
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    }

                    // 选中点准星、外圈光晕、节点圆点与顶部跟随浮层胶囊
                    val activeCoord = coords[safeIdx]
                    drawLine(
                        color = lineColor.copy(alpha = 0.4f),
                        start = Offset(activeCoord.x, topPad),
                        end = Offset(activeCoord.x, topPad + chartH),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f), 0f)
                    )
                    drawCircle(
                        color = lineColor.copy(alpha = 0.22f),
                        radius = 9.dp.toPx(),
                        center = activeCoord
                    )
                    drawCircle(
                        color = lineColor,
                        radius = 5.dp.toPx(),
                        center = activeCoord
                    )
                    drawCircle(
                        color = surfaceBgColor,
                        radius = 2.2.dp.toPx(),
                        center = activeCoord
                    )

                    val bubbleText = if (privacyMasked) {
                        "${activePoint.label} · ****"
                    } else {
                        "${activePoint.label} · ${formatCompactScale(activePoint.balanceMicros, currencyCode)}"
                    }
                    val bubbleMeasured = textMeasurer.measure(bubbleText, tooltipStyle)
                    val bubblePadH = 7.dp.toPx()
                    val bubblePadV = 2.5.dp.toPx()
                    val bubbleW = bubbleMeasured.size.width + bubblePadH * 2
                    val bubbleH = bubbleMeasured.size.height + bubblePadV * 2
                    val bubbleX = (activeCoord.x - bubbleW / 2f)
                        .coerceIn(leftPad, (size.width - rightPad - bubbleW).coerceAtLeast(leftPad))
                    val bubbleY = 2.dp.toPx()
                    drawRoundRect(
                        color = lineColor,
                        topLeft = Offset(bubbleX, bubbleY),
                        size = Size(bubbleW, bubbleH),
                        cornerRadius = CornerRadius(bubbleH / 2f, bubbleH / 2f)
                    )
                    drawText(
                        textLayoutResult = bubbleMeasured,
                        topLeft = Offset(bubbleX + bubblePadH, bubbleY + bubblePadV)
                    )

                    // 首/中/尾 X 轴日期标注
                    val xLabelIndices = listOf(0, points.lastIndex / 2, points.lastIndex).distinct()
                    xLabelIndices.forEach { idx ->
                        val measured = textMeasurer.measure(points[idx].label, labelStyle)
                        val rawX = coords[idx].x - measured.size.width / 2f
                        drawText(
                            textLayoutResult = measured,
                            topLeft = Offset(
                                x = rawX.coerceIn(leftPad, (size.width - rightPad - measured.size.width).coerceAtLeast(leftPad)),
                                y = topPad + chartH + 5.dp.toPx()
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TrendMetricChip(
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false
            )
            AutoResizeAmountText(
                text = value,
                baseStyle = MaterialTheme.typography.labelLarge.tabular(),
                color = valueColor,
                fontWeight = FontWeight.Bold,
                minFontSize = 9.5.sp,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun CreditAccountSummaryCard(
    account: AccountEntity,
    cycleStartMillis: Long,
    cycleEndMillis: Long,
    cycleSpendMicros: Long,
    cycleRepaidMicros: Long,
    zone: ZoneId,
    privacyMasked: Boolean,
    onRepayClick: () -> Unit
) {
    val semantic = KairoTheme.semantic
    val limitMicros = account.creditLimitMicros.coerceAtLeast(0L)
    val usedMicros = (-account.balanceMicros).coerceAtLeast(0L)
    val availableMicros = (account.creditLimitMicros + account.balanceMicros).coerceAtLeast(0L)
    val usedRatio = if (limitMicros > 0L) {
        (usedMicros.toDouble() / limitMicros.toDouble()).toFloat().coerceIn(0f, 1f)
    } else {
        0f
    }
    val usedPercent = (usedRatio * 100f).toInt()
    val barColor = when {
        usedRatio >= 0.9f -> semantic.expense
        usedRatio >= 0.7f -> semantic.warning
        else -> MaterialTheme.colorScheme.primary
    }
    val countdownText = formatCreditCountdown(account.billingDay, account.repaymentDay)
    val cycleRangeText = remember(cycleStartMillis, cycleEndMillis, zone) {
        formatCycleDateRange(cycleStartMillis, cycleEndMillis, zone)
    }

    YimuCard(cornerRadius = 20.dp) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CreditCard,
                        contentDescription = null,
                        tint = Color(account.colorHex),
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "额度与本期账单",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
                if (countdownText.isNotEmpty()) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Schedule,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = countdownText,
                                style = MaterialTheme.typography.labelSmall.tabular(),
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }

            // 真实本期账单周期面板
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "本期账期：$cycleRangeText",
                            style = MaterialTheme.typography.labelMedium.tabular(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (account.billingDay == null) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh
                            ) {
                                Text(
                                    text = "自然月",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.5.dp)
                                )
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "本期消费",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                softWrap = false
                            )
                            AutoResizeAmountText(
                                text = formatPrivacyAmount(
                                    privacyMasked,
                                    MoneyAmount.format(cycleSpendMicros, account.currencyCode)
                                ),
                                baseStyle = MaterialTheme.typography.titleMedium.tabular(),
                                color = semantic.expense,
                                fontWeight = FontWeight.Bold,
                                minFontSize = 11.sp,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "本期已还",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                softWrap = false
                            )
                            AutoResizeAmountText(
                                text = formatPrivacyAmount(
                                    privacyMasked,
                                    MoneyAmount.format(cycleRepaidMicros, account.currencyCode)
                                ),
                                baseStyle = MaterialTheme.typography.titleMedium.tabular(),
                                color = semantic.income,
                                fontWeight = FontWeight.Bold,
                                minFontSize = 11.sp,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            // 额度三列：信用额度 / 已用 / 可用
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CreditStatCell(
                    label = "信用额度",
                    value = formatPrivacyAmount(privacyMasked, MoneyAmount.format(limitMicros, account.currencyCode)),
                    valueColor = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                CreditStatCell(
                    label = "已用额度",
                    value = formatPrivacyAmount(privacyMasked, MoneyAmount.format(usedMicros, account.currencyCode)),
                    valueColor = semantic.expense,
                    modifier = Modifier.weight(1f)
                )
                CreditStatCell(
                    label = "可用额度",
                    value = formatPrivacyAmount(privacyMasked, MoneyAmount.format(availableMicros, account.currencyCode)),
                    valueColor = semantic.income,
                    modifier = Modifier.weight(1f)
                )
            }

            // 额度使用率进度条
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                LinearProgressIndicator(
                    progress = { usedRatio },
                    color = barColor,
                    trackColor = barColor.copy(alpha = 0.14f),
                    strokeCap = StrokeCap.Round,
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
                        text = if (privacyMasked) "额度使用率 **" else "额度使用率 $usedPercent%",
                        style = MaterialTheme.typography.labelSmall.tabular(),
                        color = barColor,
                        maxLines = 1,
                        softWrap = false
                    )
                    val scheduleText = listOfNotNull(
                        account.billingDay?.let { "${it}日出账" },
                        account.repaymentDay?.let { "${it}日还款" }
                    ).joinToString(" · ")
                    Text(
                        text = scheduleText.ifEmpty { "可在编辑账户中设置账单/还款日" },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Button(
                onClick = onRepayClick,
                shape = CircleShape,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Rounded.SwapHoriz, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "还信用卡",
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

@Composable
private fun CreditStatCell(
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false
            )
            Spacer(modifier = Modifier.height(2.dp))
            AutoResizeAmountText(
                text = value,
                baseStyle = MaterialTheme.typography.titleSmall.tabular(),
                color = valueColor,
                fontWeight = FontWeight.Bold,
                minFontSize = 10.sp,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun InstallmentsManagementCard(
    account: AccountEntity,
    installments: List<InstallmentPlanEntity>,
    categoryMap: Map<Long, CategoryEntity>,
    privacyMasked: Boolean,
    onAddInstallment: () -> Unit,
    onRecordPeriod: (Long) -> Unit,
    onRequestDelete: (InstallmentPlanEntity) -> Unit
) {
    val semantic = KairoTheme.semantic
    val activePlans = remember(installments) {
        installments.filter { it.completedPeriods < it.totalPeriods.coerceAtLeast(1) }
    }

    YimuCard(cornerRadius = 20.dp) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "分期计划 (${installments.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false
                    )
                    if (activePlans.isNotEmpty()) {
                        val remainingPeriodsTotal = activePlans.sumOf { (it.totalPeriods - it.completedPeriods).coerceAtLeast(0) }
                        Text(
                            text = "${activePlans.size} 笔进行中 · 共剩 $remainingPeriodsTotal 期",
                            style = MaterialTheme.typography.labelSmall.tabular(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (installments.isNotEmpty()) {
                    TextButton(
                        onClick = onAddInstallment,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("新建分期", maxLines = 1, softWrap = false)
                    }
                }
            }

            if (installments.isEmpty()) {
                EmptyStateCard(
                    icon = Icons.Rounded.CreditCard,
                    title = "暂无分期计划",
                    subtitle = "管理账单分期与消费分期，按期一键入账",
                    actionLabel = "新建分期",
                    onAction = onAddInstallment
                )
            } else {
                installments.forEach { plan ->
                    val totalPeriods = plan.totalPeriods.coerceAtLeast(1)
                    val isCompleted = plan.completedPeriods >= totalPeriods
                    val progressRatio = (plan.completedPeriods.toFloat() / totalPeriods.toFloat()).coerceIn(0f, 1f)
                    val basePerPeriod = plan.totalAmountMicros / totalPeriods
                    val nextIndex = (plan.completedPeriods + 1).coerceAtMost(totalPeriods)
                    val remainder = if (nextIndex == totalPeriods) {
                        plan.totalAmountMicros - basePerPeriod * totalPeriods
                    } else {
                        0L
                    }
                    val periodPrincipal = basePerPeriod + remainder
                    val periodTotal = periodPrincipal + plan.feePerPeriodMicros
                    val category = plan.categoryId?.let { categoryMap[it] }

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
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
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = plan.name,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = if (isCompleted) semantic.incomeContainer else MaterialTheme.colorScheme.primaryContainer
                                        ) {
                                            Text(
                                                text = if (isCompleted) "已还清" else "剩余 ${totalPeriods - plan.completedPeriods} 期",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (isCompleted) semantic.income else MaterialTheme.colorScheme.onPrimaryContainer,
                                                maxLines = 1,
                                                softWrap = false,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                        if (category != null) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                                modifier = Modifier.widthIn(max = 96.dp)
                                            ) {
                                                Text(
                                                    text = category.name,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    softWrap = false,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(3.dp))
                                    val totalStr = formatPrivacyAmount(
                                        privacyMasked,
                                        MoneyAmount.format(plan.totalAmountMicros, plan.currencyCode)
                                    )
                                    val periodTotalStr = formatPrivacyAmount(
                                        privacyMasked,
                                        MoneyAmount.format(periodTotal, plan.currencyCode)
                                    )
                                    val installmentMeta = if (plan.feePerPeriodMicros > 0L) {
                                        val feeStr = formatPrivacyAmount(
                                            privacyMasked,
                                            MoneyAmount.format(plan.feePerPeriodMicros, plan.currencyCode)
                                        )
                                        "总额 $totalStr · 每期 $periodTotalStr（含费 $feeStr）"
                                    } else {
                                        "总额 $totalStr · 每期 $periodTotalStr"
                                    }
                                    Text(
                                        text = installmentMeta,
                                        style = MaterialTheme.typography.bodySmall.tabular(),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(
                                    onClick = { onRequestDelete(plan) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Delete,
                                        contentDescription = "删除分期",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                LinearProgressIndicator(
                                    progress = { progressRatio },
                                    color = if (isCompleted) semantic.income else MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    strokeCap = StrokeCap.Round,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(6.dp)
                                        .clip(CircleShape)
                                )
                                Text(
                                    text = "${plan.completedPeriods}/$totalPeriods 期",
                                    style = MaterialTheme.typography.labelSmall.tabular(),
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (isCompleted) {
                                        "已全部入账"
                                    } else {
                                        "下期：${KairoFormats.formatDate(plan.nextDueMillis)}"
                                    },
                                    style = MaterialTheme.typography.labelSmall.tabular(),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    softWrap = false
                                )
                                if (!isCompleted) {
                                    FilledTonalButton(
                                        onClick = { onRecordPeriod(plan.id) },
                                        shape = CircleShape,
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Text(
                                            text = "记入本期",
                                            style = MaterialTheme.typography.labelMedium,
                                            maxLines = 1,
                                            softWrap = false
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
private fun AccountHistoryWorkbenchCard(
    periodMode: AccountHistoryPeriodMode,
    selectedMonth: YearMonth,
    selectedYear: Int,
    totalInPeriodCount: Int,
    filteredCount: Int,
    searchQuery: String,
    selectedType: String,
    typeCounts: Map<String, Int>,
    isMultiSelectMode: Boolean,
    accentColor: Color,
    onSelectPeriodMode: (AccountHistoryPeriodMode) -> Unit,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onResetCurrentMonth: () -> Unit,
    onOpenMonthPicker: () -> Unit,
    onPrevYear: () -> Unit,
    onNextYear: () -> Unit,
    onResetCurrentYear: () -> Unit,
    onOpenYearPicker: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSelectType: (String) -> Unit,
    onToggleMultiSelect: () -> Unit,
    onResetFilters: () -> Unit
) {
    val activeAccent = if (accentColor.alpha > 0.1f) accentColor else MaterialTheme.colorScheme.primary
    val currentMonth = remember { YearMonth.now() }
    val currentYear = remember { YearMonth.now().year }
    val hasActiveFilter = searchQuery.isNotBlank() || selectedType != AccountTxTypeFilter.ALL

    YimuCard(cornerRadius = 20.dp) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 第一行：时间维度分段胶囊（按月 / 按年 / 全部） + 右侧多选开关
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Row(
                        modifier = Modifier.padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AccountHistoryPeriodMode.entries.forEach { mode ->
                            val selected = periodMode == mode
                            Surface(
                                shape = CircleShape,
                                color = if (selected) activeAccent else Color.Transparent,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable { onSelectPeriodMode(mode) }
                            ) {
                                Text(
                                    text = mode.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                }

                Surface(
                    shape = CircleShape,
                    color = if (isMultiSelectMode) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                    border = if (isMultiSelectMode) {
                        BorderStroke(0.8.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
                    } else {
                        null
                    },
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(onClick = onToggleMultiSelect)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (isMultiSelectMode) Icons.Rounded.DoneAll else Icons.Rounded.SelectAll,
                            contentDescription = null,
                            tint = if (isMultiSelectMode) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = if (isMultiSelectMode) "完成" else "多选",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isMultiSelectMode) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }

            // 第二行：时间导航条（按月/按年支持前后切换、弹窗点选与一键回本月/今年）
            when (periodMode) {
                AccountHistoryPeriodMode.MONTH -> {
                    val isCurrentMonth = selectedMonth == currentMonth
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(
                            onClick = onPrevMonth,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "上个月")
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable(onClick = onOpenMonthPicker)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = selectedMonth.format(DateTimeFormatter.ofPattern("yyyy年M月")),
                                        style = MaterialTheme.typography.titleSmall.tabular(),
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                    Icon(
                                        imageVector = Icons.Rounded.ArrowDropDown,
                                        contentDescription = "选择年月",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            if (!isCurrentMonth) {
                                Surface(
                                    shape = CircleShape,
                                    color = activeAccent.copy(alpha = 0.14f),
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .clickable(onClick = onResetCurrentMonth)
                                ) {
                                    Text(
                                        text = "回本月",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = activeAccent,
                                        maxLines = 1,
                                        softWrap = false,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                        IconButton(
                            onClick = onNextMonth,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "下个月")
                        }
                    }
                }
                AccountHistoryPeriodMode.YEAR -> {
                    val isCurrentYear = selectedYear == currentYear
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(
                            onClick = onPrevYear,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "上一年")
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable(onClick = onOpenYearPicker)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = "${selectedYear}年",
                                        style = MaterialTheme.typography.titleSmall.tabular(),
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                    Icon(
                                        imageVector = Icons.Rounded.ArrowDropDown,
                                        contentDescription = "选择年份",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            if (!isCurrentYear) {
                                Surface(
                                    shape = CircleShape,
                                    color = activeAccent.copy(alpha = 0.14f),
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .clickable(onClick = onResetCurrentYear)
                                ) {
                                    Text(
                                        text = "回今年",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = activeAccent,
                                        maxLines = 1,
                                        softWrap = false,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                        IconButton(
                            onClick = onNextYear,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "下一年")
                        }
                    }
                }
                AccountHistoryPeriodMode.ALL -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "全部历史账单",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                        Text(
                            text = "共 $totalInPeriodCount 笔",
                            style = MaterialTheme.typography.labelMedium.tabular(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }

            // 第三行：轻量实时搜索框
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.65f),
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(17.dp)
                    )
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = "搜索商户、备注、分类、标签或金额",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = onSearchQueryChange,
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            cursorBrush = SolidColor(activeAccent),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (searchQuery.isNotEmpty()) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "清除搜索",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .size(17.dp)
                                .clip(CircleShape)
                                .clickable { onSearchQueryChange("") }
                        )
                    }
                }
            }

            // 第四行：横向交易类型筛选胶囊（附实时笔数统计）
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(AccountTxTypeFilter.options, key = { it.first }) { (typeKey, label) ->
                    val count = typeCounts[typeKey] ?: 0
                    val active = selectedType == typeKey
                    Surface(
                        shape = CircleShape,
                        color = if (active) {
                            activeAccent.copy(alpha = 0.16f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerLow
                        },
                        border = BorderStroke(
                            width = if (active) 1.dp else 0.5.dp,
                            color = if (active) {
                                activeAccent.copy(alpha = 0.72f)
                            } else {
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                            }
                        ),
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { onSelectType(typeKey) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                color = if (active) activeAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                softWrap = false
                            )
                            Text(
                                text = count.toString(),
                                style = MaterialTheme.typography.labelSmall.tabular(),
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                                color = if (active) {
                                    activeAccent
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                                },
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }

            // 筛选生效时的精简结果摘要行
            if (hasActiveFilter) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "当前命中 $filteredCount / $totalInPeriodCount 笔",
                        style = MaterialTheme.typography.labelSmall.tabular(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false
                    )
                    Text(
                        text = "重置筛选",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = activeAccent,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(onClick = onResetFilters)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AccountBatchActionBottomBar(
    selectedCount: Int,
    totalVisibleCount: Int,
    allSelected: Boolean,
    canMoveAccount: Boolean,
    onToggleSelectAll: () -> Unit,
    onBatchCategory: () -> Unit,
    onBatchMoveAccount: () -> Unit,
    onBatchDelete: () -> Unit,
    onExit: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shadowElevation = 10.dp,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    IconButton(onClick = onExit, modifier = Modifier.size(30.dp)) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "退出多选",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Text(
                        text = "已选 $selectedCount / $totalVisibleCount 笔",
                        style = MaterialTheme.typography.titleSmall.tabular(),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
                TextButton(
                    onClick = onToggleSelectAll,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(Icons.Rounded.SelectAll, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (allSelected) "取消全选" else "全选",
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onBatchCategory,
                    enabled = selectedCount > 0,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.Category, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("改分类", maxLines = 1, softWrap = false)
                }
                OutlinedButton(
                    onClick = onBatchMoveAccount,
                    enabled = selectedCount > 0 && canMoveAccount,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.SwapHoriz, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("改账户", maxLines = 1, softWrap = false)
                }
                Button(
                    onClick = onBatchDelete,
                    enabled = selectedCount > 0,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.Delete, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("删除", maxLines = 1, softWrap = false)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun AccountDayTransactionGroupCard(
    date: LocalDate,
    account: AccountEntity,
    transactions: List<TransactionEntity>,
    categoryMap: Map<Long, CategoryEntity>,
    accountMap: Map<Long, AccountEntity>,
    privacyMasked: Boolean,
    isMultiSelectMode: Boolean,
    selectedTxIds: Set<Long>,
    onClickTransaction: (TransactionEntity) -> Unit,
    onLongClickTransaction: (TransactionEntity) -> Unit,
    onEditTransaction: (TransactionEntity) -> Unit,
    onDuplicateTransaction: (TransactionEntity) -> Unit,
    onRefundTransaction: (TransactionEntity) -> Unit,
    onRequestDeleteTransaction: (TransactionEntity) -> Unit,
    onStartMultiSelectWith: (TransactionEntity) -> Unit
) {
    val semantic = KairoTheme.semantic
    val dayNetMicros = remember(transactions, account.id) {
        transactions
            .filter { it.status == TransactionStatus.CONFIRMED }
            .sumOf { accountDeltaForTransaction(it, account.id) }
    }
    val dayTitle = remember(date) { formatAccountDayHeader(date) }

    YimuCard(cornerRadius = 18.dp) {
        Column(modifier = Modifier.padding(vertical = 6.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = dayTitle,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (dayNetMicros != 0L) {
                        Text(
                            text = formatPrivacyAmount(
                                privacyMasked,
                                MoneyAmount.format(dayNetMicros, account.currencyCode, showSign = true)
                            ),
                            style = MaterialTheme.typography.labelMedium.tabular(),
                            fontWeight = FontWeight.SemiBold,
                            color = if (dayNetMicros > 0L) semantic.income else semantic.expense,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                    Text(
                        text = "${transactions.size} 笔",
                        style = MaterialTheme.typography.labelSmall.tabular(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            transactions.forEach { tx ->
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 14.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                )

                val category = tx.categoryId?.let { categoryMap[it] }
                val isTransferIn = tx.type == TransactionType.TRANSFER && tx.toAccountId == account.id
                val accountSignedAmount = accountDeltaForTransaction(tx, account.id)
                val isSelected = isMultiSelectMode && tx.id in selectedTxIds
                var showRowMenu by remember(tx.id) { mutableStateOf(false) }

                val amountColor = when {
                    isTransferIn -> semantic.income
                    tx.type == TransactionType.TRANSFER -> semantic.transfer
                    tx.type == TransactionType.EXPENSE -> semantic.expense
                    tx.type == TransactionType.INCOME -> semantic.income
                    accountSignedAmount >= 0L -> semantic.income
                    else -> semantic.expense
                }
                val iconKey = when {
                    tx.type == TransactionType.TRANSFER -> "swap_horiz"
                    tx.type == TransactionType.ADJUST -> "tune"
                    category != null -> category.icon
                    else -> "more_horiz"
                }
                val iconColorHex = when {
                    tx.type == TransactionType.TRANSFER -> 0xFF1A65C7L
                    tx.type == TransactionType.ADJUST -> 0xFF475569L
                    category != null -> category.colorHex
                    else -> account.colorHex
                }
                val peerTransferLabel = remember(tx, account.id, accountMap) {
                    resolveTransferPeerLabel(tx, account.id, accountMap)
                }
                val titleText = remember(tx, account.id, category, peerTransferLabel) {
                    when {
                        tx.merchant.isNotBlank() -> tx.merchant
                        tx.type == TransactionType.TRANSFER -> peerTransferLabel ?: if (isTransferIn) "转入" else "转出"
                        category != null -> category.name
                        else -> KairoFormats.transactionTypeLabel(tx.type)
                    }
                }
                val cleanedNote = remember(tx.note) { KairoFormats.cleanListRowNote(tx.note) }
                val parsedTags = remember(tx.tags) { TagTextCodec.parse(tx.tags) }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.32f)
                            } else {
                                Color.Transparent
                            }
                        )
                        .combinedClickable(
                            onClick = { onClickTransaction(tx) },
                            onLongClick = { onLongClickTransaction(tx) }
                        )
                        .padding(start = 14.dp, end = if (isMultiSelectMode) 14.dp else 6.dp, top = 11.dp, bottom = 11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isMultiSelectMode) {
                        Icon(
                            imageVector = if (isSelected) Icons.Rounded.CheckBox else Icons.Rounded.CheckBoxOutlineBlank,
                            contentDescription = if (isSelected) "已勾选" else "未勾选",
                            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    CategoryIconBadge(
                        iconKey = iconKey,
                        colorHex = iconColorHex,
                        size = 38.dp,
                        iconSize = 18.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Text(
                                text = titleText,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (tx.status == TransactionStatus.PENDING) {
                                TxMiniFlagBadge(
                                    text = "待确认",
                                    containerColor = semantic.warningContainer,
                                    contentColor = semantic.warning
                                )
                            }
                            if (tx.reimburseStatus == ReimburseStatus.PENDING) {
                                TxMiniFlagBadge(
                                    text = "待报销",
                                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            } else if (tx.reimburseStatus == ReimburseStatus.REIMBURSED) {
                                TxMiniFlagBadge(
                                    text = "已报销",
                                    containerColor = semantic.incomeContainer,
                                    contentColor = semantic.income
                                )
                            }
                            if (tx.refundOfTxId != null) {
                                TxMiniFlagBadge(
                                    text = "退款",
                                    containerColor = semantic.incomeContainer,
                                    contentColor = semantic.income
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Text(
                                text = KairoFormats.formatTime(tx.timestamp),
                                style = MaterialTheme.typography.labelSmall.tabular(),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                softWrap = false
                            )
                            val subMeta = when {
                                tx.type == TransactionType.TRANSFER && tx.merchant.isNotBlank() && peerTransferLabel != null -> peerTransferLabel
                                tx.type != TransactionType.TRANSFER && tx.merchant.isNotBlank() && category != null -> category.name
                                else -> null
                            }
                            if (!subMeta.isNullOrBlank()) {
                                Text(
                                    text = subMeta,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (tx.sourceType.isNotBlank() && tx.sourceType != CaptureChannel.MANUAL) {
                                ChannelChip(channel = tx.sourceType)
                            }
                            if (cleanedNote.isNotBlank()) {
                                Text(
                                    text = cleanedNote,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                            }
                        }

                        if (parsedTags.isNotEmpty()) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                parsedTags.take(3).forEach { tagName ->
                                    TagBadgeChip(
                                        name = tagName,
                                        colorHex = KairoIcons.fallbackTagColorHex(tagName),
                                        compact = true
                                    )
                                }
                                if (parsedTags.size > 3) {
                                    Text(
                                        text = "+${parsedTags.size - 3}",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = formatPrivacyAmount(
                                privacyMasked,
                                MoneyAmount.format(
                                    amountMicros = accountSignedAmount,
                                    currencyCode = account.currencyCode,
                                    showSign = true,
                                    showCurrencyCode = !account.currencyCode.equals("CNY", true)
                                )
                            ),
                            style = MaterialTheme.typography.titleSmall.tabular(),
                            color = amountColor,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                        if (!tx.origCurrency.equals(account.currencyCode, ignoreCase = true)) {
                            Text(
                                text = formatPrivacyAmount(
                                    privacyMasked,
                                    "原币 ${MoneyAmount.format(tx.origAmountMicros, tx.origCurrency, showCurrencyCode = true)}"
                                ),
                                style = MaterialTheme.typography.labelSmall.tabular(),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                softWrap = false
                            )
                        } else if (!account.currencyCode.equals(tx.baseCurrency, ignoreCase = true) && tx.rateToBaseMicros > 0L) {
                            val frozenBase = LedgerAnalytics.computeFrozenBaseMicros(tx)
                            Text(
                                text = formatPrivacyAmount(
                                    privacyMasked,
                                    "≈ ${MoneyAmount.format(frozenBase, tx.baseCurrency, showCurrencyCode = true)}"
                                ),
                                style = MaterialTheme.typography.labelSmall.tabular(),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }

                    if (!isMultiSelectMode) {
                        Box {
                            IconButton(
                                onClick = { showRowMenu = true },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.MoreVert,
                                    contentDescription = "账单操作",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            DropdownMenu(
                                expanded = showRowMenu,
                                onDismissRequest = { showRowMenu = false }
                            ) {
                                DropdownMenuItem(
                                    leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                                    text = { Text("编辑账单") },
                                    onClick = {
                                        showRowMenu = false
                                        onEditTransaction(tx)
                                    }
                                )
                                DropdownMenuItem(
                                    leadingIcon = { Icon(Icons.Rounded.ContentCopy, contentDescription = null) },
                                    text = { Text("再记一笔") },
                                    onClick = {
                                        showRowMenu = false
                                        onDuplicateTransaction(tx)
                                    }
                                )
                                if (tx.type == TransactionType.EXPENSE) {
                                    DropdownMenuItem(
                                        leadingIcon = { Icon(Icons.Rounded.Replay, contentDescription = null) },
                                        text = { Text("退款") },
                                        onClick = {
                                            showRowMenu = false
                                            onRefundTransaction(tx)
                                        }
                                    )
                                }
                                DropdownMenuItem(
                                    leadingIcon = { Icon(Icons.Rounded.CheckBox, contentDescription = null) },
                                    text = { Text("多选管理") },
                                    onClick = {
                                        showRowMenu = false
                                        onStartMultiSelectWith(tx)
                                    }
                                )
                                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                                DropdownMenuItem(
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Rounded.Delete,
                                            contentDescription = null,
                                            tint = semantic.expense
                                        )
                                    },
                                    text = { Text("删除账单", color = semantic.expense) },
                                    onClick = {
                                        showRowMenu = false
                                        onRequestDeleteTransaction(tx)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TxMiniFlagBadge(
    text: String,
    containerColor: Color,
    contentColor: Color
) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = containerColor
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = contentColor,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
        )
    }
}

@Composable
private fun AccountBatchCategoryDialog(
    categories: List<CategoryEntity>,
    selectedCount: Int,
    initialType: String,
    onPickCategory: (Long?) -> Unit,
    onDismiss: () -> Unit
) {
    var filterType by remember(initialType) { mutableStateOf(initialType) }
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

    YubraeDialog(
        onDismissRequest = onDismiss,
        title = "批量改分类 ($selectedCount 笔)",
        subtitle = "选择目标分类将立即应用到所选账单",
        icon = Icons.Rounded.Category,
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("取消", maxLines = 1, softWrap = false)
            }
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = filterType == TransactionType.EXPENSE,
                    onClick = { filterType = TransactionType.EXPENSE },
                    label = { Text("支出", maxLines = 1, softWrap = false) }
                )
                FilterChip(
                    selected = filterType == TransactionType.INCOME,
                    onClick = { filterType = TransactionType.INCOME },
                    label = { Text("收入", maxLines = 1, softWrap = false) }
                )
                Spacer(modifier = Modifier.weight(1f))
                TextButton(
                    onClick = { onPickCategory(null) },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text("设为未分类", maxLines = 1, softWrap = false)
                }
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
                            .clip(RoundedCornerShape(12.dp))
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
                                fontWeight = if (isSub) FontWeight.Normal else FontWeight.SemiBold,
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
private fun AccountBatchMoveAccountDialog(
    sourceAccount: AccountEntity,
    targetAccounts: List<AccountEntity>,
    selectedCount: Int,
    privacyMasked: Boolean,
    onConfirmMove: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedTargetId by remember(targetAccounts) {
        mutableLongStateOf(targetAccounts.firstOrNull()?.id ?: 0L)
    }

    YubraeDialog(
        onDismissRequest = onDismiss,
        title = "批量改账户 ($selectedCount 笔)",
        subtitle = "当前账户：${sourceAccount.name} (${sourceAccount.currencyCode})",
        icon = Icons.Rounded.SwapHoriz,
        confirmButton = {
            Button(
                onClick = { if (selectedTargetId > 0L) onConfirmMove(selectedTargetId) },
                enabled = selectedTargetId > 0L,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("确认迁移", maxLines = 1, softWrap = false)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("取消", maxLines = 1, softWrap = false)
            }
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "迁移后将自动精确重算双边账户余额（跨币种按汇率换算，涉及同账户自转的转账记录会自动跳过）。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)
                )
            }

            if (targetAccounts.isEmpty()) {
                Text(
                    text = "暂无其他可用的活跃账户，请先新建或恢复其他账户。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(targetAccounts, key = { it.id }) { target ->
                        val selected = target.id == selectedTargetId
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (selected) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerLow
                            },
                            border = if (selected) {
                                BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
                            } else {
                                null
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { selectedTargetId = target.id }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                RadioButton(
                                    selected = selected,
                                    onClick = { selectedTargetId = target.id }
                                )
                                CartoonAccountIcon(
                                    iconKey = target.icon,
                                    accountName = target.name,
                                    colorHex = target.colorHex,
                                    size = 30.dp
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = target.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${AccountGroup.labelZh(target.groupType)} · ${target.currencyCode}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Text(
                                    text = formatPrivacyAmount(
                                        privacyMasked,
                                        MoneyAmount.format(target.balanceMicros, target.currencyCode)
                                    ),
                                    style = MaterialTheme.typography.labelMedium.tabular(),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    softWrap = false
                                )
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
private fun InstallmentEditorDialog(
    account: AccountEntity,
    expenseCategories: List<CategoryEntity>,
    onSave: (InstallmentPlanEntity) -> Unit,
    onDismiss: () -> Unit
) {
    val rootExpenseCategories = remember(expenseCategories) {
        expenseCategories.filter { it.parentId == null }
    }
    var name by remember { mutableStateOf("") }
    var totalAmountInput by remember { mutableStateOf("") }
    var feeInput by remember { mutableStateOf("0.00") }
    var periodsInput by remember { mutableStateOf("12") }
    var billingDayInput by remember { mutableStateOf(account.billingDay?.toString() ?: "5") }
    var selectedCategoryId by remember { mutableStateOf(rootExpenseCategories.firstOrNull()?.id) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    YubraeDialog(
        onDismissRequest = onDismiss,
        title = "新建分期",
        subtitle = "${account.name} · ${account.currencyCode}",
        icon = Icons.Rounded.CreditCard,
        confirmButton = {
            Button(
                onClick = {
                    val trimmed = name.trim()
                    val totalMicros = MoneyAmount.parseToMicros(totalAmountInput)
                    val feeMicros = MoneyAmount.parseToMicros(feeInput.ifBlank { "0" }) ?: -1L
                    val periods = periodsInput.toIntOrNull()
                    val billingDay = billingDayInput.toIntOrNull()?.coerceIn(1, 31)
                    when {
                        trimmed.isEmpty() -> errorMsg = "请输入分期名称"
                        totalMicros == null || totalMicros <= 0L -> errorMsg = "请输入大于 0 的分期总金额"
                        feeMicros < 0L -> errorMsg = "手续费不能为负数"
                        periods == null || periods <= 0 -> errorMsg = "请输入大于 0 的期数"
                        billingDay == null -> errorMsg = "请输入 1-31 的记账日"
                        else -> {
                            val nextDueMillis = computeNextBillingMillis(billingDay)
                            onSave(
                                InstallmentPlanEntity(
                                    accountId = account.id,
                                    name = trimmed,
                                    totalAmountMicros = totalMicros,
                                    feePerPeriodMicros = feeMicros,
                                    currencyCode = account.currencyCode,
                                    totalPeriods = periods,
                                    completedPeriods = 0,
                                    billingDay = billingDay,
                                    nextDueMillis = nextDueMillis,
                                    categoryId = selectedCategoryId,
                                    enabled = true
                                )
                            )
                        }
                    }
                },
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("保存", maxLines = 1, softWrap = false)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("取消", maxLines = 1, softWrap = false)
            }
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
                label = { Text("分期名称") },
                placeholder = { Text("如：手机分期") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = totalAmountInput,
                onValueChange = { totalAmountInput = it },
                label = { Text("总金额 (${account.currencyCode})") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = feeInput,
                onValueChange = { feeInput = it },
                label = { Text("每期手续费 (${account.currencyCode})") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Text("期数", style = MaterialTheme.typography.labelMedium)
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf(3, 6, 12, 24).forEach { p ->
                    FilterChip(
                        selected = periodsInput == p.toString(),
                        onClick = { periodsInput = p.toString() },
                        label = { Text("${p}期", maxLines = 1, softWrap = false) }
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = periodsInput,
                    onValueChange = { periodsInput = it },
                    label = { Text("总期数") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = billingDayInput,
                    onValueChange = { billingDayInput = it },
                    label = { Text("每月记账日") },
                    placeholder = { Text("1-31") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f)
                )
            }

            if (rootExpenseCategories.isNotEmpty()) {
                Text("分类", style = MaterialTheme.typography.labelMedium)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    rootExpenseCategories.forEach { cat ->
                        FilterChip(
                            selected = selectedCategoryId == cat.id,
                            onClick = { selectedCategoryId = cat.id },
                            label = { Text(cat.name, maxLines = 1, softWrap = false) }
                        )
                    }
                }
            }

            errorMsg?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

private fun resolveTransferPeerLabel(
    tx: TransactionEntity,
    accountId: Long,
    accountMap: Map<Long, AccountEntity>
): String? {
    if (tx.type != TransactionType.TRANSFER) return null
    return when {
        tx.toAccountId == accountId && tx.accountId == accountId -> "账户内互转"
        tx.toAccountId == accountId -> {
            val fromName = accountMap[tx.accountId]?.name
            if (fromName != null) "从 $fromName 转入" else "转入"
        }
        tx.accountId == accountId -> {
            val toName = tx.toAccountId?.let { accountMap[it]?.name }
            if (toName != null) "转至 $toName" else "转出"
        }
        else -> null
    }
}

private fun resolveTransactionTitle(
    tx: TransactionEntity,
    accountId: Long,
    categoryMap: Map<Long, CategoryEntity>,
    accountMap: Map<Long, AccountEntity>
): String {
    if (tx.merchant.isNotBlank()) return tx.merchant
    if (tx.type == TransactionType.TRANSFER) {
        return resolveTransferPeerLabel(tx, accountId, accountMap) ?: "转账"
    }
    return tx.categoryId?.let { categoryMap[it]?.name } ?: KairoFormats.transactionTypeLabel(tx.type)
}

private fun matchesAccountTransactionQuery(
    tx: TransactionEntity,
    tokens: List<String>,
    accountId: Long,
    categoryMap: Map<Long, CategoryEntity>,
    accountMap: Map<Long, AccountEntity>
): Boolean {
    val categoryName = tx.categoryId?.let { categoryMap[it]?.name }.orEmpty()
    val peerName = when {
        tx.type == TransactionType.TRANSFER && tx.toAccountId == accountId -> accountMap[tx.accountId]?.name.orEmpty()
        tx.type == TransactionType.TRANSFER && tx.accountId == accountId -> tx.toAccountId?.let { accountMap[it]?.name }.orEmpty()
        else -> ""
    }
    val origPlain = MoneyAmount.formatPlain(kotlin.math.abs(tx.origAmountMicros), CurrencyCatalog.find(tx.origCurrency).minorDigits, false)
    val accPlain = MoneyAmount.formatPlain(kotlin.math.abs(tx.accountAmountMicros), CurrencyCatalog.find(tx.accountCurrency).minorDigits, false)
    val toPlain = tx.toAmountMicros?.let { amt ->
        val toCur = tx.toCurrency ?: tx.accountCurrency
        MoneyAmount.formatPlain(kotlin.math.abs(amt), CurrencyCatalog.find(toCur).minorDigits, false)
    }.orEmpty()

    val haystack = buildString {
        append(tx.merchant).append(' ')
        append(tx.note).append(' ')
        append(tx.tags).append(' ')
        append(categoryName).append(' ')
        append(peerName).append(' ')
        append(origPlain).append(' ')
        append(accPlain).append(' ')
        append(toPlain).append(' ')
        append(tx.origCurrency).append(' ')
        append(KairoFormats.transactionTypeLabel(tx.type))
    }.lowercase(Locale.ROOT)

    return tokens.all { token -> haystack.contains(token) }
}

private fun formatAccountDayHeader(date: LocalDate): String {
    val today = LocalDate.now()
    return if (date.year == today.year) {
        KairoFormats.formatDayHeader(date)
    } else {
        val dayOfWeek = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.SIMPLIFIED_CHINESE)
        "${date.year}年${date.monthValue}月${date.dayOfMonth}日 $dayOfWeek"
    }
}

private fun formatCycleDateRange(startMillis: Long, endMillis: Long, zone: ZoneId): String {
    val startDate = Instant.ofEpochMilli(startMillis).atZone(zone).toLocalDate()
    val endDate = Instant.ofEpochMilli(endMillis).atZone(zone).toLocalDate()
    return if (startDate.year == endDate.year) {
        "${startDate.monthValue}月${startDate.dayOfMonth}日 - ${endDate.monthValue}月${endDate.dayOfMonth}日"
    } else {
        "${startDate.year}/${startDate.monthValue}/${startDate.dayOfMonth} - ${endDate.year}/${endDate.monthValue}/${endDate.dayOfMonth}"
    }
}

private fun accountDeltaForTransaction(tx: TransactionEntity, accountId: Long): Long {
    var delta = 0L
    if (tx.accountId == accountId) {
        delta += when (tx.type) {
            TransactionType.EXPENSE, TransactionType.TRANSFER -> -tx.accountAmountMicros
            TransactionType.INCOME, TransactionType.ADJUST -> tx.accountAmountMicros
            else -> 0L
        }
    }
    if (tx.toAccountId == accountId && tx.type == TransactionType.TRANSFER) {
        delta += (tx.toAmountMicros ?: tx.accountAmountMicros)
    }
    return delta
}

private fun buildDailyAccountTrend(
    accountId: Long,
    currentBalanceMicros: Long,
    accountTransactions: List<TransactionEntity>,
    days: Int,
    zoneId: ZoneId
): List<DailyBalancePoint> {
    val count = days.coerceIn(2, 365)
    val today = LocalDate.now(zoneId)
    val labelFmt = DateTimeFormatter.ofPattern("M/d")

    var futureDelta = 0L
    val txByDate = HashMap<LocalDate, MutableList<TransactionEntity>>()
    accountTransactions.forEach { tx ->
        if (tx.status != TransactionStatus.CONFIRMED) return@forEach
        val date = Instant.ofEpochMilli(tx.timestamp).atZone(zoneId).toLocalDate()
        if (date.isAfter(today)) {
            futureDelta += accountDeltaForTransaction(tx, accountId)
        } else {
            txByDate.getOrPut(date) { mutableListOf() }.add(tx)
        }
    }

    var runningBalance = currentBalanceMicros - futureDelta
    val reversedPoints = ArrayList<DailyBalancePoint>(count)
    for (offset in 0 until count) {
        val date = today.minusDays(offset.toLong())
        reversedPoints.add(
            DailyBalancePoint(
                date = date,
                label = date.format(labelFmt),
                balanceMicros = runningBalance
            )
        )
        txByDate[date]?.forEach { tx ->
            runningBalance -= accountDeltaForTransaction(tx, accountId)
        }
    }
    return reversedPoints.asReversed()
}

private fun computeNextBillingMillis(billingDay: Int, zoneId: ZoneId = ZoneId.systemDefault()): Long {
    val today = LocalDate.now(zoneId)
    val clampedThisMonth = billingDay.coerceIn(1, today.lengthOfMonth())
    val candidate = today.withDayOfMonth(clampedThisMonth)
    val targetDate = if (!candidate.isBefore(today)) {
        candidate
    } else {
        val nextMonth = today.plusMonths(1)
        nextMonth.withDayOfMonth(billingDay.coerceIn(1, nextMonth.lengthOfMonth()))
    }
    return targetDate.atTime(LocalTime.of(9, 0)).atZone(zoneId).toInstant().toEpochMilli()
}

private fun formatCompactScale(micros: Long, currency: String): String {
    val symbol = CurrencyCatalog.symbolOf(currency)
    val sign = if (micros < 0L) "-" else ""
    val units = kotlin.math.abs(micros) / MoneyAmount.AMOUNT_SCALE
    return when {
        units >= 10_000L -> String.format(Locale.US, "%s%s%.1fw", sign, symbol, units / 10_000.0)
        units >= 1_000L -> String.format(Locale.US, "%s%s%.1fk", sign, symbol, units / 1_000.0)
        else -> "$sign$symbol$units"
    }
}

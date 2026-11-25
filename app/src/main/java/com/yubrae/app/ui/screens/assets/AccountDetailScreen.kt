package com.yubrae.app.ui.screens.assets

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Schedule
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
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
import com.yubrae.app.core.model.CurrencyCatalog
import com.yubrae.app.core.model.MoneyAmount
import com.yubrae.app.data.db.AccountEntity
import com.yubrae.app.data.db.AccountGroup
import com.yubrae.app.data.db.CaptureChannel
import com.yubrae.app.data.db.CategoryEntity
import com.yubrae.app.data.db.InstallmentPlanEntity
import com.yubrae.app.data.db.TransactionEntity
import com.yubrae.app.data.db.TransactionStatus
import com.yubrae.app.data.db.TransactionType
import com.yubrae.app.ui.components.CategoryIconBadge
import com.yubrae.app.ui.components.ChannelChip
import com.yubrae.app.ui.components.ConfirmActionDialog
import com.yubrae.app.ui.components.CurrencyBadge
import com.yubrae.app.ui.components.EmptyStateCard
import com.yubrae.app.ui.components.KairoFormats
import com.yubrae.app.ui.components.LocalPrivacyMasked
import com.yubrae.app.ui.components.YimuCard
import com.yubrae.app.ui.components.YubraeDialog
import com.yubrae.app.ui.components.formatPrivacyAmount
import com.yubrae.app.ui.theme.KairoTheme
import com.yubrae.app.ui.theme.tabular
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private data class DailyBalancePoint(
    val date: LocalDate,
    val label: String,
    val balanceMicros: Long
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountDetailScreen(
    account: AccountEntity,
    transactions: List<TransactionEntity>,
    categories: List<CategoryEntity>,
    installments: List<InstallmentPlanEntity>,
    displayCurrency: String,
    convertedBalanceMicros: Long?,
    onBack: () -> Unit,
    onQuickRecord: () -> Unit,
    onQuickTransfer: () -> Unit,
    onCalibrateBalance: (Long, String) -> Unit,
    onEditAccount: (AccountEntity) -> Unit,
    onToggleArchive: (Boolean) -> Unit,
    onEditTransaction: (TransactionEntity) -> Unit,
    onSaveInstallment: (InstallmentPlanEntity) -> Unit,
    onRecordInstallmentPeriod: (Long) -> Unit,
    onDeleteInstallment: (Long) -> Unit,
    pinnedCurrencies: List<String> = listOf("CNY", "USD", "EUR", "GBP")
) {
    val privacyMasked = LocalPrivacyMasked.current
    val zone = remember { ZoneId.systemDefault() }
    val categoryMap = remember(categories) { categories.associateBy { it.id } }

    var selectedMonth by remember { mutableStateOf(YearMonth.now()) }
    var trendDays by remember { mutableIntStateOf(30) }
    var showTopMenu by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showCalibrateDialog by remember { mutableStateOf(false) }
    var showAddInstallmentDialog by remember { mutableStateOf(false) }
    var deleteInstallmentTarget by remember { mutableStateOf<InstallmentPlanEntity?>(null) }

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

    val monthTransactions = remember(accountTransactions, selectedMonth, zone) {
        val monthStart = selectedMonth.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val monthEnd = selectedMonth.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1L
        accountTransactions.filter { it.timestamp in monthStart..monthEnd }
    }

    val (monthInflowMicros, monthOutflowMicros) = remember(monthTransactions, account.id) {
        var inflow = 0L
        var outflow = 0L
        monthTransactions.forEach { tx ->
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

    val dailyTrendPoints = remember(accountTransactions, account.balanceMicros, account.id, trendDays, zone) {
        buildDailyAccountTrend(
            accountId = account.id,
            currentBalanceMicros = account.balanceMicros,
            accountTransactions = accountTransactions,
            days = trendDays,
            zoneId = zone
        )
    }

    val groupedDayTransactions = remember(monthTransactions, zone) {
        monthTransactions
            .groupBy { Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate() }
            .entries
            .sortedByDescending { it.key }
            .map { it.key to it.value }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = account.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (account.archived) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.errorContainer
                            ) {
                                Text(
                                    text = "已归档",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
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
                                text = { Text(if (account.archived) "取消归档" else "归档") },
                                onClick = {
                                    showTopMenu = false
                                    onToggleArchive(!account.archived)
                                }
                            )
                        }
                    }
                }
            )
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
            // 1. 账户专属渐变余额 Hero 卡
            item(key = "account_hero") {
                AccountDetailHeroCard(
                    account = account,
                    displayCurrency = displayCurrency,
                    convertedBalanceMicros = convertedBalanceMicros,
                    selectedMonth = selectedMonth,
                    monthInflowMicros = monthInflowMicros,
                    monthOutflowMicros = monthOutflowMicros,
                    privacyMasked = privacyMasked,
                    onQuickRecord = onQuickRecord,
                    onQuickTransfer = onQuickTransfer,
                    onCalibrate = { showCalibrateDialog = true }
                )
            }

            // 2. 近期账户余额走势 Canvas 折线图
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

            // 3. 信用卡专属面板 + 分期还款管理
            if (account.groupType == AccountGroup.CREDIT) {
                item(key = "credit_quota_panel") {
                    CreditAccountSummaryCard(
                        account = account,
                        privacyMasked = privacyMasked,
                        onRepayClick = onQuickTransfer
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

            // 4. 账户流水月度切换头部
            item(key = "tx_month_header") {
                YimuCard(cornerRadius = 18.dp) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(onClick = { selectedMonth = selectedMonth.minusMonths(1) }) {
                            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "上个月")
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = selectedMonth.format(DateTimeFormatter.ofPattern("yyyy年M月")),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${monthTransactions.size} 笔",
                                style = MaterialTheme.typography.labelSmall.tabular(),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { selectedMonth = selectedMonth.plusMonths(1) }) {
                            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "下个月")
                        }
                    }
                }
            }

            if (groupedDayTransactions.isEmpty()) {
                item(key = "empty_account_tx") {
                    EmptyStateCard(
                        icon = Icons.AutoMirrored.Rounded.ReceiptLong,
                        title = "${selectedMonth.monthValue}月暂无流水",
                        actionLabel = "记一笔",
                        onAction = onQuickRecord
                    )
                }
            } else {
                groupedDayTransactions.forEach { (date, dayTxs) ->
                    item(key = "day_$date") {
                        AccountDayTransactionGroupCard(
                            date = date,
                            account = account,
                            transactions = dayTxs,
                            categoryMap = categoryMap,
                            privacyMasked = privacyMasked,
                            onEditTransaction = onEditTransaction
                        )
                    }
                }
            }
        }
    }

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

    if (showCalibrateDialog) {
        CalibrateBalanceDialog(
            account = account,
            privacyMasked = privacyMasked,
            onConfirm = { targetMicros, note ->
                onCalibrateBalance(targetMicros, note)
                showCalibrateDialog = false
            },
            onDismiss = { showCalibrateDialog = false }
        )
    }

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
}

@Composable
private fun AccountDetailHeroCard(
    account: AccountEntity,
    displayCurrency: String,
    convertedBalanceMicros: Long?,
    selectedMonth: YearMonth,
    monthInflowMicros: Long,
    monthOutflowMicros: Long,
    privacyMasked: Boolean,
    onQuickRecord: () -> Unit,
    onQuickTransfer: () -> Unit,
    onCalibrate: () -> Unit
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
        MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.72f)
    } else {
        Color.White.copy(alpha = 0.85f)
    }
    val isForeign = !account.currencyCode.equals(displayCurrency, ignoreCase = true)

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.28f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .background(Brush.linearGradient(colors = gradientColors))
                .padding(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CategoryIconBadge(
                            iconKey = account.icon,
                            colorHex = account.colorHex,
                            size = 42.dp,
                            iconSize = 22.dp
                        )
                        Column {
                            Text(
                                text = AccountGroup.labelZh(account.groupType),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (!account.includeInNetWorth) {
                                Text(
                                    text = "不计净资产",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    CurrencyBadge(currencyCode = account.currencyCode, selected = true)
                }

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = if (account.groupType == AccountGroup.CREDIT) "账面余额" else "账户余额",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatPrivacyAmount(
                            privacyMasked,
                            MoneyAmount.format(
                                amountMicros = account.balanceMicros,
                                currencyCode = account.currencyCode,
                                showCurrencyCode = !account.currencyCode.equals("CNY", ignoreCase = true)
                            )
                        ),
                        style = MaterialTheme.typography.displaySmall.tabular(),
                        color = if (account.balanceMicros < 0L) semantic.expense else MaterialTheme.colorScheme.onSurface
                    )
                    if (isForeign && convertedBalanceMicros != null) {
                        Text(
                            text = formatPrivacyAmount(
                                privacyMasked,
                                "≈ ${MoneyAmount.format(convertedBalanceMicros, displayCurrency, showCurrencyCode = true)}"
                            ),
                            style = MaterialTheme.typography.bodyMedium.tabular(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = pillColor,
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
                            Text(
                                text = "${selectedMonth.monthValue}月流入",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = formatPrivacyAmount(
                                    privacyMasked,
                                    MoneyAmount.format(monthInflowMicros, account.currencyCode, showSign = true)
                                ),
                                style = MaterialTheme.typography.titleMedium.tabular(),
                                color = semantic.income,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = pillColor,
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
                            Text(
                                text = "${selectedMonth.monthValue}月流出",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = formatPrivacyAmount(
                                    privacyMasked,
                                    MoneyAmount.format(monthOutflowMicros, account.currencyCode)
                                ),
                                style = MaterialTheme.typography.titleMedium.tabular(),
                                color = semantic.expense,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onQuickRecord,
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accentColor,
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("记一笔", maxLines = 1)
                    }
                    FilledTonalButton(
                        onClick = onQuickTransfer,
                        shape = CircleShape,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.SwapHoriz, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (account.groupType == AccountGroup.CREDIT) "还信用卡" else "转账",
                            maxLines = 1
                        )
                    }
                    OutlinedButton(
                        onClick = onCalibrate,
                        shape = CircleShape,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Rounded.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("校准", maxLines = 1)
                    }
                }
            }
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
    val lineColor = if (accentColor.alpha > 0.1f) accentColor else MaterialTheme.colorScheme.primary
    var selectedIdx by remember(points.size) {
        mutableIntStateOf((points.size - 1).coerceAtLeast(0))
    }
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(
        color = MaterialTheme.colorScheme.onSurfaceVariant
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
                    fontWeight = FontWeight.SemiBold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(15, 30).forEach { days ->
                        val active = trendDays == days
                        Surface(
                            shape = CircleShape,
                            color = if (active) lineColor.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.clickable { onChangeDays(days) }
                        ) {
                            Text(
                                text = "${days}天",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                color = if (active) lineColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
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
                val safeIdx = selectedIdx.coerceIn(0, points.lastIndex)
                val activePoint = points[safeIdx]
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
                            text = activePoint.date.toString(),
                            style = MaterialTheme.typography.labelMedium.tabular(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = formatPrivacyAmount(
                                privacyMasked,
                                MoneyAmount.format(activePoint.balanceMicros, currencyCode, showCurrencyCode = true)
                            ),
                            style = MaterialTheme.typography.titleSmall.tabular(),
                            color = lineColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                val minMicros = points.minOf { it.balanceMicros }
                val maxMicros = points.maxOf { it.balanceMicros }
                val spanMicros = (maxMicros - minMicros).coerceAtLeast(0L)

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(175.dp)
                        .pointerInput(points) {
                            detectTapGestures { offset ->
                                val leftPad = 48.dp.toPx()
                                val rightPad = 12.dp.toPx()
                                val chartW = (size.width - leftPad - rightPad).coerceAtLeast(1f)
                                if (points.size > 1) {
                                    val fraction = ((offset.x - leftPad) / chartW).coerceIn(0f, 1f)
                                    selectedIdx = Math.round(fraction * points.lastIndex)
                                        .coerceIn(0, points.lastIndex)
                                }
                            }
                        }
                ) {
                    val leftPad = 48.dp.toPx()
                    val rightPad = 12.dp.toPx()
                    val topPad = 12.dp.toPx()
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
                                minMicros + java.math.BigDecimal(spanMicros)
                                    .multiply(java.math.BigDecimal(i))
                                    .divide(java.math.BigDecimal(gridSteps), 0, java.math.RoundingMode.HALF_UP)
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
                                width = 2.5.dp.toPx(),
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    }

                    // 选中点准星与节点圆点
                    val activeCoord = coords[safeIdx]
                    drawLine(
                        color = lineColor.copy(alpha = 0.35f),
                        start = Offset(activeCoord.x, topPad),
                        end = Offset(activeCoord.x, topPad + chartH),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f), 0f)
                    )
                    drawCircle(
                        color = lineColor,
                        radius = 5.5.dp.toPx(),
                        center = activeCoord
                    )
                    drawCircle(
                        color = surfaceBgColor,
                        radius = 2.5.dp.toPx(),
                        center = activeCoord
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
private fun CreditAccountSummaryCard(
    account: AccountEntity,
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
                        text = "额度与账期",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
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
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CreditStatCell(
                    label = "总额度",
                    value = formatPrivacyAmount(privacyMasked, MoneyAmount.format(limitMicros, account.currencyCode)),
                    valueColor = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                CreditStatCell(
                    label = "已用",
                    value = formatPrivacyAmount(privacyMasked, MoneyAmount.format(usedMicros, account.currencyCode)),
                    valueColor = semantic.expense,
                    modifier = Modifier.weight(1f)
                )
                CreditStatCell(
                    label = "可用",
                    value = formatPrivacyAmount(privacyMasked, MoneyAmount.format(availableMicros, account.currencyCode)),
                    valueColor = semantic.income,
                    modifier = Modifier.weight(1f)
                )
            }

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
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (privacyMasked) "已用 **" else "已用 $usedPercent%",
                        style = MaterialTheme.typography.labelSmall.tabular(),
                        color = barColor
                    )
                    val scheduleText = listOfNotNull(
                        account.billingDay?.let { "${it}日出账" },
                        account.repaymentDay?.let { "${it}日还款" }
                    ).joinToString(" · ")
                    if (scheduleText.isNotEmpty()) {
                        Text(
                            text = scheduleText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Button(
                onClick = onRepayClick,
                shape = CircleShape,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Rounded.SwapHoriz, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("还信用卡", fontWeight = FontWeight.SemiBold)
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
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall.tabular(),
                color = valueColor,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
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
                Text(
                    text = "分期计划 (${installments.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (installments.isNotEmpty()) {
                    TextButton(onClick = onAddInstallment) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("新建分期")
                    }
                }
            }

            if (installments.isEmpty()) {
                EmptyStateCard(
                    icon = Icons.Rounded.CreditCard,
                    title = "暂无分期计划",
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
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                        if (category != null) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = MaterialTheme.colorScheme.surfaceContainerHigh
                                            ) {
                                                Text(
                                                    text = category.name,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                                    fontWeight = FontWeight.SemiBold
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
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (!isCompleted) {
                                    FilledTonalButton(
                                        onClick = { onRecordPeriod(plan.id) },
                                        shape = CircleShape,
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Text("记入本期", style = MaterialTheme.typography.labelMedium)
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
                        label = { Text("${p}期") }
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
                            label = { Text(cat.name) }
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

@Composable
private fun AccountDayTransactionGroupCard(
    date: LocalDate,
    account: AccountEntity,
    transactions: List<TransactionEntity>,
    categoryMap: Map<Long, CategoryEntity>,
    privacyMasked: Boolean,
    onEditTransaction: (TransactionEntity) -> Unit
) {
    val semantic = KairoTheme.semantic
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
                    text = KairoFormats.formatDayHeader(date),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${transactions.size} 笔",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            transactions.forEach { tx ->
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 14.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                )
                val category = tx.categoryId?.let { categoryMap[it] }
                val isTransferIn = tx.type == TransactionType.TRANSFER && tx.toAccountId == account.id
                val accountSignedAmount = accountDeltaForTransaction(tx, account.id)
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
                val titleText = when {
                    tx.merchant.isNotBlank() -> tx.merchant
                    tx.type == TransactionType.TRANSFER -> if (isTransferIn) "转入" else "转出"
                    category != null -> category.name
                    else -> KairoFormats.transactionTypeLabel(tx.type)
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onEditTransaction(tx) }
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
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
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
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
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = semantic.warningContainer
                                ) {
                                    Text(
                                        text = "待确认",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = semantic.warning,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = KairoFormats.formatTime(tx.timestamp),
                                style = MaterialTheme.typography.labelSmall.tabular(),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (tx.sourceType.isNotBlank() && tx.sourceType != CaptureChannel.MANUAL) {
                                ChannelChip(channel = tx.sourceType)
                            }
                            if (tx.note.isNotBlank()) {
                                Text(
                                    text = tx.note,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

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
                            fontWeight = FontWeight.Bold
                        )
                        if (!tx.origCurrency.equals(account.currencyCode, ignoreCase = true)) {
                            Text(
                                text = formatPrivacyAmount(
                                    privacyMasked,
                                    "原币 ${MoneyAmount.format(tx.origAmountMicros, tx.origCurrency, showCurrencyCode = true)}"
                                ),
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
        delta += (tx.toAmountMicros ?: 0L)
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
    val count = days.coerceIn(2, 60)
    val today = LocalDate.now(zoneId)
    val labelFmt = DateTimeFormatter.ofPattern("M/d")

    var futureDelta = 0L
    val txByDate = HashMap<LocalDate, MutableList<TransactionEntity>>()
    accountTransactions.forEach { tx ->
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

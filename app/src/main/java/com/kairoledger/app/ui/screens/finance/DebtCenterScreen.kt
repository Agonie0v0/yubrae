package com.kairoledger.app.ui.screens.finance

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Handshake
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kairoledger.app.core.model.CurrencyCatalog
import com.kairoledger.app.core.model.MoneyAmount
import com.kairoledger.app.data.db.AccountEntity
import com.kairoledger.app.data.db.DebtDirection
import com.kairoledger.app.data.db.DebtRecordEntity
import com.kairoledger.app.data.db.DebtStatus
import com.kairoledger.app.ui.components.ConfirmActionDialog
import com.kairoledger.app.ui.components.CurrencyBadge
import com.kairoledger.app.ui.components.CurrencyPickerDialog
import com.kairoledger.app.ui.components.EmptyStateCard
import com.kairoledger.app.ui.components.KairoFormats
import com.kairoledger.app.ui.components.KairoIcons
import com.kairoledger.app.ui.components.LocalPrivacyMasked
import com.kairoledger.app.ui.components.YimuCard
import com.kairoledger.app.ui.components.formatPrivacyAmount
import com.kairoledger.app.ui.theme.KairoTheme
import com.kairoledger.app.ui.theme.tabular
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

private data class PersonDebtGroup(
    val personName: String,
    val records: List<DebtRecordEntity>,
    val openCount: Int,
    val latestUpdatedAt: Long
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebtCenterScreen(
    debts: List<DebtRecordEntity>,
    accounts: List<AccountEntity>,
    baseCurrency: String,
    pinnedCurrencies: List<String>,
    onBack: () -> Unit,
    onSaveDebt: (debt: DebtRecordEntity, syncAccountBalance: Boolean) -> Unit,
    onSettleDebt: (debtId: Long, settleAmountMicros: Long, accountId: Long?, note: String) -> Unit,
    onDeleteDebt: (Long) -> Unit
) {
    val privacyMasked = LocalPrivacyMasked.current
    val accountMap = remember(accounts) { accounts.associateBy { it.id } }

    var selectedDirection by remember { mutableStateOf(DebtDirection.LEND) }
    var statusFilter by remember { mutableStateOf("ALL") } // "ALL" | OPEN | SETTLED
    var showAddDialog by remember { mutableStateOf(false) }
    var settleTarget by remember { mutableStateOf<DebtRecordEntity?>(null) }
    var deleteTarget by remember { mutableStateOf<DebtRecordEntity?>(null) }

    val (totalReceivableMicros, totalPayableMicros, openLendCount, openBorrowCount) = remember(debts, baseCurrency) {
        var receivable = 0L
        var payable = 0L
        var lendOpen = 0
        var borrowOpen = 0
        debts.forEach { d ->
            if (d.status == DebtStatus.OPEN) {
                val remaining = (d.amountMicros - d.settledAmountMicros).coerceAtLeast(0L)
                val converted = convertToBaseApprox(remaining, d.currencyCode, baseCurrency)
                if (d.direction == DebtDirection.LEND) {
                    receivable += converted
                    lendOpen++
                } else {
                    payable += converted
                    borrowOpen++
                }
            }
        }
        Quad(receivable, payable, lendOpen, borrowOpen)
    }

    val filteredDebts = remember(debts, selectedDirection, statusFilter) {
        debts.filter { d ->
            d.direction == selectedDirection &&
                (statusFilter == "ALL" || d.status == statusFilter)
        }
    }

    val personGroups = remember(filteredDebts) {
        filteredDebts
            .groupBy { it.personName.trim().ifEmpty { "未命名联系人" } }
            .map { (name, list) ->
                val sorted = list.sortedWith(
                    compareBy<DebtRecordEntity> { it.status == DebtStatus.SETTLED }
                        .thenByDescending { it.updatedAt }
                )
                PersonDebtGroup(
                    personName = name,
                    records = sorted,
                    openCount = sorted.count { it.status == DebtStatus.OPEN },
                    latestUpdatedAt = sorted.maxOfOrNull { it.updatedAt } ?: 0L
                )
            }
            .sortedWith(
                compareByDescending<PersonDebtGroup> { it.openCount > 0 }
                    .thenByDescending { it.latestUpdatedAt }
            )
    }

    val existingPersonNames = remember(debts) {
        debts.map { it.personName.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("借贷往来中心") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = CircleShape
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("记一笔借贷", fontWeight = FontWeight.SemiBold)
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding() + 8.dp,
                bottom = innerPadding.calculateBottomPadding() + 92.dp,
                start = 16.dp,
                end = 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 顶部往来净额卡
            item(key = "debt_summary_hero") {
                DebtNetSummaryCard(
                    baseCurrency = baseCurrency,
                    totalReceivableMicros = totalReceivableMicros,
                    totalPayableMicros = totalPayableMicros,
                    selectedDirection = selectedDirection,
                    openLendCount = openLendCount,
                    openBorrowCount = openBorrowCount,
                    privacyMasked = privacyMasked,
                    onSelectDirection = { selectedDirection = it }
                )
            }

            // 状态筛选胶囊
            item(key = "status_filter_row") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf(
                        "ALL" to "全部",
                        DebtStatus.OPEN to "未结清",
                        DebtStatus.SETTLED to "已结清"
                    ).forEach { (code, label) ->
                        FilterChip(
                            selected = statusFilter == code,
                            onClick = { statusFilter = code },
                            label = { Text(label) }
                        )
                    }
                }
            }

            if (personGroups.isEmpty()) {
                item(key = "empty_debts") {
                    val dirLabel = if (selectedDirection == DebtDirection.LEND) "借出(应收)" else "借入(应付)"
                    EmptyStateCard(
                        icon = Icons.Rounded.Handshake,
                        title = "暂无${dirLabel}往来记录",
                        subtitle = "点击右下角「记一笔借贷」登记借出/借入资金、约定还款日，并支持分期核销收款/还款。",
                        actionLabel = "记一笔借贷",
                        onAction = { showAddDialog = true }
                    )
                }
            } else {
                items(personGroups, key = { "${selectedDirection}_${it.personName}" }) { group ->
                    PersonDebtGroupCard(
                        group = group,
                        direction = selectedDirection,
                        accountMap = accountMap,
                        baseCurrency = baseCurrency,
                        privacyMasked = privacyMasked,
                        onSettleClick = { settleTarget = it },
                        onDeleteClick = { deleteTarget = it }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddDebtDialog(
            initialDirection = selectedDirection,
            existingPersons = existingPersonNames,
            accounts = accounts,
            baseCurrency = baseCurrency,
            pinnedCurrencies = pinnedCurrencies,
            onSave = { debt, syncBalance ->
                onSaveDebt(debt, syncBalance)
                selectedDirection = debt.direction
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false }
        )
    }

    settleTarget?.let { debt ->
        SettleDebtDialog(
            debt = debt,
            accounts = accounts,
            privacyMasked = privacyMasked,
            onConfirm = { settleAmountMicros, accountId, note ->
                onSettleDebt(debt.id, settleAmountMicros, accountId, note)
                settleTarget = null
            },
            onDismiss = { settleTarget = null }
        )
    }

    deleteTarget?.let { debt ->
        val actionWord = if (debt.direction == DebtDirection.LEND) "借出" else "借入"
        ConfirmActionDialog(
            title = "删除「${debt.personName}」的${actionWord}记录？",
            message = "删除后将无法继续核销该笔往来，已生成的账户流水不受影响。",
            confirmText = "删除",
            isDestructive = true,
            onConfirm = {
                onDeleteDebt(debt.id)
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null }
        )
    }
}

private data class Quad(
    val a: Long,
    val b: Long,
    val c: Int,
    val d: Int
)

@Composable
private fun DebtNetSummaryCard(
    baseCurrency: String,
    totalReceivableMicros: Long,
    totalPayableMicros: Long,
    selectedDirection: String,
    openLendCount: Int,
    openBorrowCount: Int,
    privacyMasked: Boolean,
    onSelectDirection: (String) -> Unit
) {
    val semantic = KairoTheme.semantic
    val netMicros = totalReceivableMicros - totalPayableMicros
    val gradientColors = if (semantic.isDark) {
        listOf(Color(0xFF143E2D), Color(0xFF19443A))
    } else {
        listOf(Color(0xFFE3F7EE), Color(0xFFD5EFE6), Color(0xFFECF9F4))
    }
    val titleColor = if (semantic.isDark) Color(0xFFCFF7E3) else Color(0xFF0A3D28)
    val borderColor = if (semantic.isDark) {
        Color(0xFF5BD2A0).copy(alpha = 0.25f)
    } else {
        Color(0xFF2AA876).copy(alpha = 0.22f)
    }
    val pillBg = if (semantic.isDark) Color(0xFF0F251C).copy(alpha = 0.72f) else Color.White.copy(alpha = 0.88f)

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .background(Brush.linearGradient(gradientColors))
                .padding(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "净往来（折算 $baseCurrency）",
                        style = MaterialTheme.typography.titleSmall,
                        color = titleColor
                    )
                    Text(
                        text = if (netMicros >= 0L) "净应收" else "净应付",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (netMicros >= 0L) semantic.income else semantic.expense,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Text(
                    text = formatPrivacyAmount(
                        privacyMasked,
                        MoneyAmount.format(
                            amountMicros = netMicros,
                            currencyCode = baseCurrency,
                            showSign = true,
                            showCurrencyCode = baseCurrency != "CNY"
                        )
                    ),
                    style = MaterialTheme.typography.displaySmall.tabular(),
                    color = titleColor
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = pillBg,
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
                            Text(
                                text = "总应收(借出未还)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = formatPrivacyAmount(
                                    privacyMasked,
                                    MoneyAmount.format(totalReceivableMicros, baseCurrency)
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
                        color = pillBg,
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
                            Text(
                                text = "总应付(借入未还)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = formatPrivacyAmount(
                                    privacyMasked,
                                    MoneyAmount.format(totalPayableMicros, baseCurrency)
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

                // 借出(应收) | 借入(应付) 切换 Tab
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(CircleShape)
                        .background(pillBg)
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val tabs = listOf(
                        DebtDirection.LEND to "借出(应收) · $openLendCount",
                        DebtDirection.BORROW to "借入(应付) · $openBorrowCount"
                    )
                    tabs.forEach { (dir, label) ->
                        val active = selectedDirection == dir
                        Surface(
                            shape = CircleShape,
                            color = if (active) MaterialTheme.colorScheme.primary else Color.Transparent,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onSelectDirection(dir) }
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(vertical = 8.dp)
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                    color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
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
private fun PersonDebtGroupCard(
    group: PersonDebtGroup,
    direction: String,
    accountMap: Map<Long, AccountEntity>,
    baseCurrency: String,
    privacyMasked: Boolean,
    onSettleClick: (DebtRecordEntity) -> Unit,
    onDeleteClick: (DebtRecordEntity) -> Unit
) {
    val semantic = KairoTheme.semantic
    val isLend = direction == DebtDirection.LEND
    val palette = KairoIcons.selectableColorPalette
    val avatarColor = Color(palette[kotlin.math.abs(group.personName.hashCode()) % palette.size])
    val avatarChar = group.personName.firstOrNull()?.uppercaseChar()?.toString() ?: "?"

    val openRecords = remember(group.records) {
        group.records.filter { it.status == DebtStatus.OPEN }
    }
    val distinctOpenCurrencies = remember(openRecords) {
        openRecords.map { it.currencyCode.trim().uppercase() }.distinct()
    }
    val openSummaryText = remember(openRecords, distinctOpenCurrencies, baseCurrency, privacyMasked) {
        when {
            openRecords.isEmpty() -> null
            distinctOpenCurrencies.size == 1 -> {
                val cur = distinctOpenCurrencies.first()
                val sum = openRecords.sumOf { (it.amountMicros - it.settledAmountMicros).coerceAtLeast(0L) }
                formatPrivacyAmount(privacyMasked, MoneyAmount.format(sum, cur, showCurrencyCode = cur != baseCurrency))
            }
            else -> {
                val sumBase = openRecords.sumOf {
                    val rem = (it.amountMicros - it.settledAmountMicros).coerceAtLeast(0L)
                    convertToBaseApprox(rem, it.currencyCode, baseCurrency)
                }
                "约 " + formatPrivacyAmount(privacyMasked, MoneyAmount.format(sumBase, baseCurrency))
            }
        }
    }

    YimuCard(cornerRadius = 18.dp) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            // 往来人分组头部
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = avatarColor.copy(alpha = 0.16f),
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = avatarChar,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = avatarColor
                            )
                        }
                    }
                    Column {
                        Text(
                            text = group.personName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${group.records.size} 笔往来 · ${if (group.openCount > 0) "${group.openCount} 笔未结清" else "已结清"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (openSummaryText != null) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = if (isLend) "待收回" else "待归还",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = openSummaryText,
                            style = MaterialTheme.typography.titleSmall.tabular(),
                            color = if (isLend) semantic.income else semantic.expense,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = semantic.incomeContainer
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = semantic.income,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "已结清",
                                style = MaterialTheme.typography.labelSmall,
                                color = semantic.income,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            group.records.forEach { debt ->
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 14.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                )
                DebtRecordRow(
                    debt = debt,
                    linkedAccount = debt.accountId?.let { accountMap[it] },
                    privacyMasked = privacyMasked,
                    onSettleClick = { onSettleClick(debt) },
                    onDeleteClick = { onDeleteClick(debt) }
                )
            }
        }
    }
}

@Composable
private fun DebtRecordRow(
    debt: DebtRecordEntity,
    linkedAccount: AccountEntity?,
    privacyMasked: Boolean,
    onSettleClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val semantic = KairoTheme.semantic
    val isLend = debt.direction == DebtDirection.LEND
    val isSettled = debt.status == DebtStatus.SETTLED
    val remainingMicros = (debt.amountMicros - debt.settledAmountMicros).coerceAtLeast(0L)
    val progressRatio = if (debt.amountMicros > 0L) {
        (debt.settledAmountMicros.toDouble() / debt.amountMicros.toDouble()).toFloat().coerceIn(0f, 1f)
    } else {
        1f
    }
    val progressColor = when {
        isSettled -> semantic.income
        isLend -> MaterialTheme.colorScheme.primary
        else -> semantic.expense
    }

    val dueInfo = remember(debt.dueDateMillis, isSettled) {
        val dueMillis = debt.dueDateMillis ?: return@remember null
        val zone = ZoneId.systemDefault()
        val dueDate = Instant.ofEpochMilli(dueMillis).atZone(zone).toLocalDate()
        val today = LocalDate.now(zone)
        val diffDays = ChronoUnit.DAYS.between(today, dueDate).toInt()
        when {
            isSettled -> Triple("约定 ${KairoFormats.formatDate(dueMillis)}", false, false)
            diffDays < 0 -> Triple("已逾期 ${-diffDays} 天 (${KairoFormats.formatDate(dueMillis)})", true, false)
            diffDays == 0 -> Triple("今日到期", false, true)
            diffDays <= 7 -> Triple("${diffDays}天后到期 (${KairoFormats.formatDate(dueMillis)})", false, true)
            else -> Triple("约定 ${KairoFormats.formatDate(dueMillis)}", false, false)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = formatPrivacyAmount(
                            privacyMasked,
                            MoneyAmount.format(
                                amountMicros = debt.amountMicros,
                                currencyCode = debt.currencyCode,
                                showCurrencyCode = !debt.currencyCode.equals("CNY", true)
                            )
                        ),
                        style = MaterialTheme.typography.titleMedium.tabular(),
                        fontWeight = FontWeight.Bold
                    )
                    if (!isSettled) {
                        Text(
                            text = "剩余 ${
                                formatPrivacyAmount(
                                    privacyMasked,
                                    MoneyAmount.format(remainingMicros, debt.currencyCode)
                                )
                            }",
                            style = MaterialTheme.typography.labelMedium.tabular(),
                            color = if (isLend) semantic.income else semantic.expense,
                            fontWeight = FontWeight.SemiBold
                        )
                    } else {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = semantic.incomeContainer
                        ) {
                            Text(
                                text = "已结清",
                                style = MaterialTheme.typography.labelSmall,
                                color = semantic.income,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                            )
                        }
                    }
                }

                if (debt.note.isNotBlank() || linkedAccount != null) {
                    Text(
                        text = listOfNotNull(
                            linkedAccount?.let { "账户：${it.name}" },
                            debt.note.takeIf { it.isNotBlank() }
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            IconButton(
                onClick = onDeleteClick,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Delete,
                    contentDescription = "删除借贷",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // 进度条（已收/已还比例）
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LinearProgressIndicator(
                progress = { progressRatio },
                color = progressColor,
                trackColor = progressColor.copy(alpha = 0.14f),
                strokeCap = StrokeCap.Round,
                modifier = Modifier
                    .weight(1f)
                    .height(6.dp)
                    .clip(CircleShape)
            )
            val actionVerb = if (isLend) "已收" else "已还"
            Text(
                text = "$actionVerb ${
                    formatPrivacyAmount(
                        privacyMasked,
                        MoneyAmount.format(debt.settledAmountMicros, debt.currencyCode)
                    )
                }",
                style = MaterialTheme.typography.labelSmall.tabular(),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (dueInfo != null) {
                val (dueText, isOverdue, isSoon) = dueInfo
                val badgeBg = when {
                    isOverdue -> semantic.expenseContainer
                    isSoon -> semantic.warningContainer
                    else -> MaterialTheme.colorScheme.surfaceContainerHigh
                }
                val badgeFg = when {
                    isOverdue -> semantic.expense
                    isSoon -> semantic.warning
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = badgeBg
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            imageVector = if (isOverdue) Icons.Rounded.Warning else Icons.Rounded.Schedule,
                            contentDescription = null,
                            tint = badgeFg,
                            modifier = Modifier.size(11.dp)
                        )
                        Text(
                            text = dueText,
                            style = MaterialTheme.typography.labelSmall,
                            color = badgeFg
                        )
                    }
                }
            } else {
                Text(
                    text = "创建于 ${KairoFormats.formatDate(debt.createdAt)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (!isSettled) {
                FilledTonalButton(
                    onClick = onSettleClick,
                    shape = CircleShape,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text(
                        text = if (isLend) "收款核销" else "还款核销",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun SettleDebtDialog(
    debt: DebtRecordEntity,
    accounts: List<AccountEntity>,
    privacyMasked: Boolean,
    onConfirm: (settleAmountMicros: Long, accountId: Long?, note: String) -> Unit,
    onDismiss: () -> Unit
) {
    val isLend = debt.direction == DebtDirection.LEND
    val remainingMicros = (debt.amountMicros - debt.settledAmountMicros).coerceAtLeast(0L)
    val digits = CurrencyCatalog.find(debt.currencyCode).minorDigits
    val activeAccounts = remember(accounts) { accounts.filter { !it.archived } }

    var isFullSettle by remember { mutableStateOf(true) }
    var amountInput by remember(debt.id, privacyMasked) {
        mutableStateOf(
            if (privacyMasked) "" else MoneyAmount.formatPlain(remainingMicros, digits, grouping = false)
        )
    }
    var selectedAccountId by remember(debt.id, activeAccounts) {
        mutableStateOf(debt.accountId ?: activeAccounts.firstOrNull()?.id)
    }
    var noteInput by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (isLend) "收款核销 · ${debt.personName}" else "还款核销 · ${debt.personName}")
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "当前未结清金额：${
                        formatPrivacyAmount(
                            privacyMasked,
                            MoneyAmount.format(remainingMicros, debt.currencyCode, showCurrencyCode = true)
                        )
                    }",
                    style = MaterialTheme.typography.bodyMedium.tabular(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = isFullSettle,
                        onClick = {
                            isFullSettle = true
                            amountInput = if (privacyMasked) {
                                ""
                            } else {
                                MoneyAmount.formatPlain(remainingMicros, digits, grouping = false)
                            }
                        },
                        label = { Text("全额核销") }
                    )
                    FilterChip(
                        selected = !isFullSettle,
                        onClick = {
                            isFullSettle = false
                            amountInput = ""
                        },
                        label = { Text("部分核销") }
                    )
                }

                OutlinedTextField(
                    value = amountInput,
                    onValueChange = {
                        amountInput = it
                        val parsed = MoneyAmount.parseToMicros(it)
                        isFullSettle = parsed == remainingMicros
                    },
                    label = { Text("本次核销金额 (${debt.currencyCode})") },
                    placeholder = if (isFullSettle && privacyMasked) {
                        { Text("已选择全额核销（隐私模式已隐藏具体数字）") }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = if (isLend) "收款入账账户" else "付款扣款账户",
                    style = MaterialTheme.typography.labelMedium
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (debt.accountId == null) {
                        item(key = "no_account") {
                            FilterChip(
                                selected = selectedAccountId == null,
                                onClick = { selectedAccountId = null },
                                label = { Text("不关联账户") }
                            )
                        }
                    }
                    items(activeAccounts, key = { it.id }) { acc ->
                        FilterChip(
                            selected = selectedAccountId == acc.id,
                            onClick = { selectedAccountId = acc.id },
                            label = { Text("${acc.name} (${acc.currencyCode})") }
                        )
                    }
                }

                OutlinedTextField(
                    value = noteInput,
                    onValueChange = { noteInput = it },
                    label = { Text("核销备注（可选）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                errorMsg?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsed = if (isFullSettle) remainingMicros else MoneyAmount.parseToMicros(amountInput)
                    when {
                        parsed == null || parsed <= 0L -> errorMsg = "请输入大于 0 的核销金额"
                        parsed > remainingMicros -> errorMsg = "核销金额不能超过未结清余额"
                        else -> onConfirm(parsed, selectedAccountId, noteInput.trim())
                    }
                }
            ) {
                Text("确认核销")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AddDebtDialog(
    initialDirection: String,
    existingPersons: List<String>,
    accounts: List<AccountEntity>,
    baseCurrency: String,
    pinnedCurrencies: List<String>,
    onSave: (debt: DebtRecordEntity, syncAccountBalance: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val activeAccounts = remember(accounts) { accounts.filter { !it.archived } }
    var direction by remember { mutableStateOf(initialDirection) }
    var personName by remember { mutableStateOf("") }
    var amountInput by remember { mutableStateOf("") }
    var currencyCode by remember { mutableStateOf(baseCurrency) }
    var dueDateMillis by remember { mutableStateOf<Long?>(null) }
    var selectedAccountId by remember { mutableStateOf<Long?>(null) }
    var syncAccountBalance by remember { mutableStateOf(true) }
    var noteInput by remember { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }
    var showMoreCurrencies by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("记一笔借贷") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = direction == DebtDirection.LEND,
                        onClick = { direction = DebtDirection.LEND },
                        label = { Text("借出 (别人欠我)") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = direction == DebtDirection.BORROW,
                        onClick = { direction = DebtDirection.BORROW },
                        label = { Text("借入 (我欠别人)") },
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = personName,
                    onValueChange = { personName = it },
                    label = { Text("往来人姓名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (existingPersons.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        existingPersons.take(6).forEach { person ->
                            FilterChip(
                                selected = personName == person,
                                onClick = { personName = person },
                                label = { Text(person) }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = amountInput,
                    onValueChange = { amountInput = it },
                    label = { Text("借贷金额 ($currencyCode)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("币种", style = MaterialTheme.typography.labelMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val quickCodes = if (pinnedCurrencies.contains(currencyCode)) {
                        pinnedCurrencies
                    } else {
                        pinnedCurrencies + currencyCode
                    }
                    items(quickCodes, key = { it }) { code ->
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

                Text("约定还款日（可选）", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { showDatePicker = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(dueDateMillis?.let { KairoFormats.formatDate(it) } ?: "选择约定还款日")
                    }
                    if (dueDateMillis != null) {
                        IconButton(onClick = { dueDateMillis = null }) {
                            Icon(Icons.Rounded.Close, contentDescription = "清除日期")
                        }
                    }
                }

                Text("关联资金账户（可选）", style = MaterialTheme.typography.labelMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item(key = "no_account") {
                        FilterChip(
                            selected = selectedAccountId == null,
                            onClick = { selectedAccountId = null },
                            label = { Text("不关联账户") }
                        )
                    }
                    items(activeAccounts, key = { it.id }) { acc ->
                        FilterChip(
                            selected = selectedAccountId == acc.id,
                            onClick = {
                                selectedAccountId = acc.id
                                currencyCode = acc.currencyCode
                            },
                            label = { Text("${acc.name} (${acc.currencyCode})") }
                        )
                    }
                }

                if (selectedAccountId != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("同步增减该账户余额", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = if (direction == DebtDirection.LEND) {
                                    "借出同时扣减该账户余额（不计入收支统计）"
                                } else {
                                    "借入同时增加该账户余额（不计入收支统计）"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = syncAccountBalance,
                            onCheckedChange = { syncAccountBalance = it }
                        )
                    }
                }

                OutlinedTextField(
                    value = noteInput,
                    onValueChange = { noteInput = it },
                    label = { Text("备注（可选）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                errorMsg?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmedName = personName.trim()
                    val amountMicros = MoneyAmount.parseToMicros(amountInput)
                    when {
                        trimmedName.isEmpty() -> errorMsg = "请输入往来人姓名"
                        amountMicros == null || amountMicros <= 0L -> errorMsg = "请输入大于 0 的金额"
                        else -> onSave(
                            DebtRecordEntity(
                                direction = direction,
                                personName = trimmedName,
                                amountMicros = amountMicros,
                                settledAmountMicros = 0L,
                                currencyCode = currencyCode,
                                accountId = selectedAccountId,
                                dueDateMillis = dueDateMillis,
                                note = noteInput.trim(),
                                status = DebtStatus.OPEN
                            ),
                            selectedAccountId != null && syncAccountBalance
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

    if (showDatePicker) {
        val zone = ZoneId.systemDefault()
        val initialLocal = Instant.ofEpochMilli(dueDateMillis ?: System.currentTimeMillis())
            .atZone(zone)
            .toLocalDate()
        val utcMillis = initialLocal.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = utcMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { selectedUtc ->
                            val pickedDate = Instant.ofEpochMilli(selectedUtc).atZone(ZoneOffset.UTC).toLocalDate()
                            dueDateMillis = pickedDate.atTime(LocalTime.of(12, 0)).atZone(zone).toInstant().toEpochMilli()
                        }
                        showDatePicker = false
                    }
                ) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("取消") }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }
}

private fun convertToBaseApprox(
    amountMicros: Long,
    fromCurrency: String,
    baseCurrency: String
): Long {
    val from = fromCurrency.trim().uppercase().ifEmpty { CurrencyCatalog.DEFAULT_BASE_CURRENCY }
    val base = baseCurrency.trim().uppercase().ifEmpty { CurrencyCatalog.DEFAULT_BASE_CURRENCY }
    if (amountMicros == 0L || from == base) return amountMicros
    val fromToCny = CurrencyCatalog.find(from).fallbackRateToCnyMicros
    val baseToCny = CurrencyCatalog.find(base).fallbackRateToCnyMicros
    val rate = MoneyAmount.crossRate(fromToCny, baseToCny)
    return MoneyAmount.convert(amountMicros, rate)
}

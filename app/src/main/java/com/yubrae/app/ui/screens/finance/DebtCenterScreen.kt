package com.yubrae.app.ui.screens.finance

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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Handshake
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
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
import com.yubrae.app.core.model.CurrencyCatalog
import com.yubrae.app.core.model.MoneyAmount
import com.yubrae.app.data.db.AccountEntity
import com.yubrae.app.data.db.DebtDirection
import com.yubrae.app.data.db.DebtRecordEntity
import com.yubrae.app.data.db.DebtStatus
import com.yubrae.app.data.db.ExchangeRateEntity
import com.yubrae.app.ui.components.CompactHairlineDivider
import com.yubrae.app.ui.components.ConfirmActionDialog
import com.yubrae.app.ui.components.CurrencyPickerDialog
import com.yubrae.app.ui.components.EmptyStateCard
import com.yubrae.app.ui.components.KairoFormats
import com.yubrae.app.ui.components.KairoIcons
import com.yubrae.app.ui.components.LocalPrivacyMasked
import com.yubrae.app.ui.components.QuickCurrencySelectorRow
import com.yubrae.app.ui.components.YimuCard
import com.yubrae.app.ui.components.YubraeCalendarDatePickerDialog
import com.yubrae.app.ui.components.YubraeDialog
import com.yubrae.app.ui.components.formatPrivacyAmount
import com.yubrae.app.ui.theme.KairoTheme
import com.yubrae.app.ui.theme.tabular
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
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
    onDeleteDebt: (Long) -> Unit,
    rates: List<ExchangeRateEntity> = emptyList()
) {
    val privacyMasked = LocalPrivacyMasked.current
    val accountMap = remember(accounts) { accounts.associateBy { it.id } }

    var selectedDirection by remember { mutableStateOf(DebtDirection.LEND) }
    var statusFilter by remember { mutableStateOf("ALL") } // "ALL" | OPEN | SETTLED
    var showAddDialog by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<DebtRecordEntity?>(null) }
    var settleTarget by remember { mutableStateOf<DebtRecordEntity?>(null) }
    var deleteTarget by remember { mutableStateOf<DebtRecordEntity?>(null) }

    val (totalReceivableMicros, totalPayableMicros, openLendCount, openBorrowCount) = remember(debts, baseCurrency, rates) {
        var receivable = 0L
        var payable = 0L
        var lendOpen = 0
        var borrowOpen = 0
        debts.forEach { d ->
            if (d.status == DebtStatus.OPEN) {
                val remaining = (d.amountMicros - d.settledAmountMicros).coerceAtLeast(0L)
                val converted = convertToBaseWithRates(remaining, d.currencyCode, baseCurrency, rates)
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
                onClick = {
                    editTarget = null
                    showAddDialog = true
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = CircleShape
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("新增借贷", fontWeight = FontWeight.SemiBold)
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding() + 6.dp,
                bottom = innerPadding.calculateBottomPadding() + 84.dp,
                start = 12.dp,
                end = 12.dp
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp)
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
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf(
                        "ALL" to "全部",
                        DebtStatus.OPEN to "未结清",
                        DebtStatus.SETTLED to "已结清"
                    ).forEach { (code, label) ->
                        val active = statusFilter == code
                        Surface(
                            shape = CircleShape,
                            color = if (active) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerLowest
                            },
                            border = BorderStroke(
                                0.5.dp,
                                if (active) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                                }
                            ),
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable { statusFilter = code }
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                                color = if (active) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }

            if (personGroups.isEmpty()) {
                item(key = "empty_debts") {
                    val hasAnyInDirection = debts.any { it.direction == selectedDirection }
                    if (statusFilter != "ALL" && hasAnyInDirection) {
                        EmptyStateCard(
                            icon = Icons.Rounded.Handshake,
                            title = if (statusFilter == DebtStatus.OPEN) "暂无未结清记录" else "暂无已结清记录",
                            actionLabel = "查看全部",
                            onAction = { statusFilter = "ALL" }
                        )
                    } else {
                        EmptyStateCard(
                            icon = Icons.Rounded.Handshake,
                            title = "暂无借贷记录",
                            actionLabel = "新增借贷",
                            onAction = {
                                editTarget = null
                                showAddDialog = true
                            }
                        )
                    }
                }
            } else {
                items(personGroups, key = { "${selectedDirection}_${it.personName}" }) { group ->
                    PersonDebtGroupCard(
                        group = group,
                        direction = selectedDirection,
                        accountMap = accountMap,
                        baseCurrency = baseCurrency,
                        rates = rates,
                        privacyMasked = privacyMasked,
                        onEditClick = {
                            showAddDialog = false
                            editTarget = it
                        },
                        onSettleClick = { settleTarget = it },
                        onDeleteClick = { deleteTarget = it }
                    )
                }
            }
        }
    }

    if (showAddDialog || editTarget != null) {
        DebtEditorDialog(
            existing = editTarget,
            initialDirection = selectedDirection,
            existingPersons = existingPersonNames,
            accounts = accounts,
            baseCurrency = baseCurrency,
            pinnedCurrencies = pinnedCurrencies,
            onSave = { debt, syncBalance ->
                onSaveDebt(debt, syncBalance)
                selectedDirection = debt.direction
                showAddDialog = false
                editTarget = null
            },
            onDismiss = {
                showAddDialog = false
                editTarget = null
            }
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
            message = "已生成的账户流水不受影响。",
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
    val gradientColors = semantic.heroGradient
    val titleColor = semantic.heroOnSurface
    val borderColor = semantic.heroBorder
    val pillBg = semantic.heroPillBg

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color.Transparent,
        border = BorderStroke(0.5.dp, borderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .background(Brush.linearGradient(gradientColors))
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "净往来 ($baseCurrency)",
                        style = MaterialTheme.typography.labelMedium,
                        color = titleColor.copy(alpha = 0.78f)
                    )
                    Surface(
                        shape = CircleShape,
                        color = if (netMicros >= 0L) {
                            semantic.incomeContainer.copy(alpha = 0.85f)
                        } else {
                            semantic.expenseContainer.copy(alpha = 0.85f)
                        }
                    ) {
                        Text(
                            text = if (netMicros >= 0L) "净应收" else "净应付",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (netMicros >= 0L) semantic.income else semantic.expense,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
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
                    style = MaterialTheme.typography.headlineMedium.tabular(),
                    fontWeight = FontWeight.Bold,
                    color = titleColor
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "应收 · 借出",
                            style = MaterialTheme.typography.labelSmall,
                            color = titleColor.copy(alpha = 0.7f)
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
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 10.dp)
                            .width(0.5.dp)
                            .height(26.dp)
                            .background(borderColor)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "应付 · 借入",
                            style = MaterialTheme.typography.labelSmall,
                            color = titleColor.copy(alpha = 0.7f)
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

                // 借出(应收) | 借入(应付) 切换 Tab
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(CircleShape)
                        .background(pillBg)
                        .padding(2.5.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    val tabs = listOf(
                        DebtDirection.LEND to "借出 · $openLendCount",
                        DebtDirection.BORROW to "借入 · $openBorrowCount"
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
                                modifier = Modifier.padding(vertical = 6.dp)
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelMedium,
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
    rates: List<ExchangeRateEntity>,
    privacyMasked: Boolean,
    onEditClick: (DebtRecordEntity) -> Unit,
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
    val openSummaryText = remember(openRecords, distinctOpenCurrencies, baseCurrency, rates, privacyMasked) {
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
                    convertToBaseWithRates(rem, it.currencyCode, baseCurrency, rates)
                }
                "约 " + formatPrivacyAmount(privacyMasked, MoneyAmount.format(sumBase, baseCurrency))
            }
        }
    }

    YimuCard {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            // 往来人分组头部
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = avatarColor.copy(alpha = 0.14f),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = avatarChar,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = avatarColor
                            )
                        }
                    }
                    Column {
                        Text(
                            text = group.personName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (group.openCount > 0) "${group.openCount} 笔未结清 / 共 ${group.records.size} 笔" else "共 ${group.records.size} 笔",
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
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = semantic.income,
                                modifier = Modifier.size(11.dp)
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
                CompactHairlineDivider(startIndent = 12.dp, modifier = Modifier.padding(end = 12.dp))
                DebtRecordRow(
                    debt = debt,
                    linkedAccount = debt.accountId?.let { accountMap[it] },
                    privacyMasked = privacyMasked,
                    onEditClick = { onEditClick(debt) },
                    onSettleClick = { onSettleClick(debt) },
                    onDeleteClick = { onDeleteClick(debt) }
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DebtRecordRow(
    debt: DebtRecordEntity,
    linkedAccount: AccountEntity?,
    privacyMasked: Boolean,
    onEditClick: () -> Unit,
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
            .clickable(onClick = onEditClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
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
                        style = MaterialTheme.typography.titleSmall.tabular(),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (!isSettled) {
                        Text(
                            text = "剩余 ${
                                formatPrivacyAmount(
                                    privacyMasked,
                                    MoneyAmount.format(remainingMicros, debt.currencyCode)
                                )
                            }",
                            style = MaterialTheme.typography.labelSmall.tabular(),
                            color = if (isLend) semantic.income else semantic.expense,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    } else {
                        Surface(
                            shape = RoundedCornerShape(5.dp),
                            color = semantic.incomeContainer
                        ) {
                            Text(
                                text = "已结清",
                                style = MaterialTheme.typography.labelSmall,
                                color = semantic.income,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }
                }

                if (debt.note.isNotBlank() || linkedAccount != null) {
                    Text(
                        text = listOfNotNull(
                            linkedAccount?.name,
                            debt.note.takeIf { it.isNotBlank() }
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(6.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                IconButton(
                    onClick = onEditClick,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = "编辑借贷",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(15.dp)
                    )
                }
                IconButton(
                    onClick = onDeleteClick,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Delete,
                        contentDescription = "删除借贷",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(15.dp)
                    )
                }
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
                    .height(4.dp)
                    .clip(RoundedCornerShape(4.dp))
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
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FlowRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
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
                        shape = RoundedCornerShape(5.dp),
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
                                color = badgeFg,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                } else {
                    Text(
                        text = KairoFormats.formatDate(debt.createdAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (debt.includeInNetWorth) {
                    Surface(
                        shape = RoundedCornerShape(5.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.78f)
                    ) {
                        Text(
                            text = "计入净资产",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            if (!isSettled) {
                FilledTonalButton(
                    onClick = onSettleClick,
                    shape = CircleShape,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.heightIn(min = 30.dp)
                ) {
                    Text(
                        text = if (isLend) "收款核销" else "还款核销",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false
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

    YubraeDialog(
        onDismissRequest = onDismiss,
        title = if (isLend) "收款核销" else "还款核销",
        subtitle = debt.personName,
        icon = Icons.Rounded.Handshake,
        confirmButton = {
            Button(
                onClick = {
                    val parsed = if (isFullSettle) remainingMicros else MoneyAmount.parseToMicros(amountInput)
                    when {
                        parsed == null || parsed <= 0L -> errorMsg = "请输入大于 0 的核销金额"
                        parsed > remainingMicros -> errorMsg = "核销金额不能超过未结清余额"
                        else -> onConfirm(parsed, selectedAccountId, noteInput.trim())
                    }
                },
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("确认核销")
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
            Text(
                text = "待核销：${
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
                label = { Text("核销金额 (${debt.currencyCode})") },
                placeholder = if (isFullSettle && privacyMasked) {
                    { Text("全额核销") }
                } else {
                    null
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
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
                label = { Text("备注（可选）") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )

            errorMsg?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun DebtEditorDialog(
    existing: DebtRecordEntity?,
    initialDirection: String,
    existingPersons: List<String>,
    accounts: List<AccountEntity>,
    baseCurrency: String,
    pinnedCurrencies: List<String>,
    onSave: (debt: DebtRecordEntity, syncAccountBalance: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val isEdit = existing != null
    val initialCurrency = existing?.currencyCode ?: baseCurrency
    val initialDigits = remember(initialCurrency) { CurrencyCatalog.find(initialCurrency).minorDigits }
    val selectableAccounts = remember(accounts, existing?.accountId) {
        accounts.filter { !it.archived || it.id == existing?.accountId }
    }

    var direction by remember(existing?.id) { mutableStateOf(existing?.direction ?: initialDirection) }
    var personName by remember(existing?.id) { mutableStateOf(existing?.personName.orEmpty()) }
    var amountInput by remember(existing?.id) {
        mutableStateOf(
            existing?.let { MoneyAmount.formatPlain(it.amountMicros, initialDigits, grouping = false) }.orEmpty()
        )
    }
    var settledAmountInput by remember(existing?.id) {
        mutableStateOf(
            existing?.let { MoneyAmount.formatPlain(it.settledAmountMicros, initialDigits, grouping = false) }.orEmpty()
        )
    }
    var currencyCode by remember(existing?.id) { mutableStateOf(initialCurrency) }
    var dueDateMillis by remember(existing?.id) { mutableStateOf(existing?.dueDateMillis) }
    var selectedAccountId by remember(existing?.id) { mutableStateOf(existing?.accountId) }
    var syncAccountBalance by remember(existing?.id) { mutableStateOf(!isEdit) }
    var includeInNetWorth by remember(existing?.id) { mutableStateOf(existing?.includeInNetWorth ?: false) }
    var noteInput by remember(existing?.id) { mutableStateOf(existing?.note.orEmpty()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showMoreCurrencies by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    YubraeDialog(
        onDismissRequest = onDismiss,
        title = if (isEdit) "编辑借贷" else "新增借贷",
        icon = Icons.Rounded.Handshake,
        confirmButton = {
            Button(
                onClick = {
                    val trimmedName = personName.trim()
                    val amountMicros = MoneyAmount.parseToMicros(amountInput)
                    val parsedSettledMicros = if (!isEdit) {
                        0L
                    } else if (settledAmountInput.isBlank()) {
                        0L
                    } else {
                        MoneyAmount.parseToMicros(settledAmountInput)
                    }
                    when {
                        trimmedName.isEmpty() -> errorMsg = "请输入往来人姓名"
                        amountMicros == null || amountMicros <= 0L -> errorMsg = "请输入大于 0 的金额"
                        parsedSettledMicros == null || parsedSettledMicros < 0L -> errorMsg = "请输入有效的已核销金额（不能为负数）"
                        parsedSettledMicros > amountMicros -> errorMsg = "已核销金额不能超过借贷总金额"
                        else -> {
                            val clampedSettled = parsedSettledMicros.coerceIn(0L, amountMicros)
                            val resolvedStatus = if (clampedSettled >= amountMicros && amountMicros > 0L) {
                                DebtStatus.SETTLED
                            } else {
                                DebtStatus.OPEN
                            }
                            val targetDebt = if (existing != null) {
                                existing.copy(
                                    direction = direction,
                                    personName = trimmedName,
                                    amountMicros = amountMicros,
                                    settledAmountMicros = clampedSettled,
                                    currencyCode = currencyCode,
                                    accountId = selectedAccountId,
                                    dueDateMillis = dueDateMillis,
                                    note = noteInput.trim(),
                                    status = resolvedStatus,
                                    includeInNetWorth = includeInNetWorth
                                )
                            } else {
                                DebtRecordEntity(
                                    direction = direction,
                                    personName = trimmedName,
                                    amountMicros = amountMicros,
                                    settledAmountMicros = 0L,
                                    currencyCode = currencyCode,
                                    accountId = selectedAccountId,
                                    dueDateMillis = dueDateMillis,
                                    note = noteInput.trim(),
                                    status = DebtStatus.OPEN,
                                    includeInNetWorth = includeInNetWorth
                                )
                            }
                            onSave(targetDebt, !isEdit && selectedAccountId != null && syncAccountBalance)
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = direction == DebtDirection.LEND,
                    onClick = { direction = DebtDirection.LEND },
                    label = { Text("借出（应收）") },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = direction == DebtDirection.BORROW,
                    onClick = { direction = DebtDirection.BORROW },
                    label = { Text("借入（应付）") },
                    modifier = Modifier.weight(1f)
                )
            }

            OutlinedTextField(
                value = personName,
                onValueChange = {
                    personName = it
                    errorMsg = null
                },
                label = { Text("往来人姓名") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
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
                            onClick = {
                                personName = person
                                errorMsg = null
                            },
                            label = { Text(person) }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = amountInput,
                onValueChange = {
                    amountInput = it
                    errorMsg = null
                },
                label = { Text("借贷金额 ($currencyCode)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )

            if (isEdit) {
                OutlinedTextField(
                    value = settledAmountInput,
                    onValueChange = {
                        settledAmountInput = it
                        errorMsg = null
                    },
                    label = { Text("已核销金额 ($currencyCode，可选)") },
                    supportingText = { Text("等于总金额时自动标为已结清，小于总金额时为未结清") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Text("币种", style = MaterialTheme.typography.labelMedium)
            QuickCurrencySelectorRow(
                selectedCurrency = currencyCode,
                pinnedCurrencies = pinnedCurrencies,
                onSelectCurrency = { currencyCode = it },
                onOpenMore = { showMoreCurrencies = true }
            )

            Text("还款日（可选）", style = MaterialTheme.typography.labelMedium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(dueDateMillis?.let { KairoFormats.formatDate(it) } ?: "选择日期")
                }
                if (dueDateMillis != null) {
                    IconButton(onClick = { dueDateMillis = null }) {
                        Icon(Icons.Rounded.Close, contentDescription = "清除日期")
                    }
                }
            }

            Text("关联账户（可选）", style = MaterialTheme.typography.labelMedium)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item(key = "no_account") {
                    FilterChip(
                        selected = selectedAccountId == null,
                        onClick = { selectedAccountId = null },
                        label = { Text("不关联账户") }
                    )
                }
                items(selectableAccounts, key = { it.id }) { acc ->
                    FilterChip(
                        selected = selectedAccountId == acc.id,
                        onClick = {
                            selectedAccountId = acc.id
                            if (!isEdit) {
                                currencyCode = acc.currencyCode
                            }
                        },
                        label = { Text("${acc.name} (${acc.currencyCode})") }
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                ) {
                    Text("计入总资产 / 净资产", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = "开启后，未结清借出计入总资产，未结清借入计入总负债，核销后实时联动",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = includeInNetWorth,
                    onCheckedChange = { includeInNetWorth = it }
                )
            }

            if (!isEdit && selectedAccountId != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                    ) {
                        Text("同步增减账户余额", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = if (direction == DebtDirection.LEND) {
                                "借出扣减余额 · 不计收支"
                            } else {
                                "借入增加余额 · 不计收支"
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
            } else if (isEdit) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "仅更新借贷台账，不改动已生成的历史账户流水",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                    )
                }
            }

            OutlinedTextField(
                value = noteInput,
                onValueChange = { noteInput = it },
                label = { Text("备注（可选）") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )

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

    if (showDatePicker) {
        val zone = ZoneId.systemDefault()
        val initialLocal = Instant.ofEpochMilli(dueDateMillis ?: System.currentTimeMillis())
            .atZone(zone)
            .toLocalDate()
        YubraeCalendarDatePickerDialog(
            initialDate = initialLocal,
            title = "选择还款日期",
            onSelectDate = { pickedDate ->
                dueDateMillis = pickedDate.atTime(LocalTime.of(12, 0)).atZone(zone).toInstant().toEpochMilli()
            },
            onDismiss = { showDatePicker = false }
        )
    }
}

private fun convertToBaseWithRates(
    amountMicros: Long,
    fromCurrency: String,
    baseCurrency: String,
    rates: List<ExchangeRateEntity>
): Long {
    val from = fromCurrency.trim().uppercase().ifEmpty { CurrencyCatalog.DEFAULT_BASE_CURRENCY }
    val base = baseCurrency.trim().uppercase().ifEmpty { CurrencyCatalog.DEFAULT_BASE_CURRENCY }
    if (amountMicros == 0L || from == base) return amountMicros
    val rate = resolveLiveOrFallbackRate(from, base, rates)
    return MoneyAmount.convert(amountMicros, rate)
}

private fun resolveLiveOrFallbackRate(
    from: String,
    to: String,
    rates: List<ExchangeRateEntity>
): Long {
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
    val fromToCny = if (from == cny) {
        MoneyAmount.RATE_SCALE
    } else {
        rates.filter { it.baseCurrency.equals(from, true) && it.targetCurrency.equals(cny, true) }
            .maxByOrNull { it.date + "_" + it.updatedAt }?.rateMicros
            ?: rates.filter { it.baseCurrency.equals(cny, true) && it.targetCurrency.equals(from, true) }
                .maxByOrNull { it.date + "_" + it.updatedAt }?.let { MoneyAmount.invertRate(it.rateMicros) }
            ?: CurrencyCatalog.find(from).fallbackRateToCnyMicros
    }
    val toToCny = if (to == cny) {
        MoneyAmount.RATE_SCALE
    } else {
        rates.filter { it.baseCurrency.equals(to, true) && it.targetCurrency.equals(cny, true) }
            .maxByOrNull { it.date + "_" + it.updatedAt }?.rateMicros
            ?: rates.filter { it.baseCurrency.equals(cny, true) && it.targetCurrency.equals(to, true) }
                .maxByOrNull { it.date + "_" + it.updatedAt }?.let { MoneyAmount.invertRate(it.rateMicros) }
            ?: CurrencyCatalog.find(to).fallbackRateToCnyMicros
    }
    return MoneyAmount.crossRate(fromToCny, toToCny)
}

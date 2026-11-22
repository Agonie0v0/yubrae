package com.kairoledger.app.ui.screens.transactions

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.MarkEmailUnread
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SwapHoriz
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kairoledger.app.core.model.MoneyAmount
import com.kairoledger.app.data.db.AccountEntity
import com.kairoledger.app.data.db.AliasMatchType
import com.kairoledger.app.data.db.CaptureChannel
import com.kairoledger.app.data.db.CategoryEntity
import com.kairoledger.app.data.db.ExchangeRateEntity
import com.kairoledger.app.data.db.RawCaptureLogEntity
import com.kairoledger.app.data.db.ReimburseStatus
import com.kairoledger.app.data.db.TransactionEntity
import com.kairoledger.app.data.db.TransactionStatus
import com.kairoledger.app.data.db.TransactionType
import com.kairoledger.app.ui.components.ChannelChip
import com.kairoledger.app.ui.components.ConfirmActionDialog
import com.kairoledger.app.ui.components.EmptyStateCard
import com.kairoledger.app.ui.components.KairoFormats
import com.kairoledger.app.ui.components.KairoIcons
import com.kairoledger.app.ui.components.transactionAmountColor
import com.kairoledger.app.ui.theme.KairoTheme
import com.kairoledger.app.ui.theme.tabular
import com.kairoledger.app.ui.viewmodel.DayTransactionGroup
import com.kairoledger.app.ui.viewmodel.KairoMainViewModel
import java.time.YearMonth
import java.time.format.DateTimeFormatter

@Composable
fun TransactionsScreen(
    viewModel: KairoMainViewModel,
    onEditTransaction: (TransactionEntity) -> Unit,
    onOpenQuickRecord: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val uiState by viewModel.transactionsUiState.collectAsStateWithLifecycle()
    val accounts by viewModel.allAccounts.collectAsStateWithLifecycle()
    val activeAccounts by viewModel.activeAccounts.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val rates by viewModel.exchangeRates.collectAsStateWithLifecycle()

    val accountMap = remember(accounts) { accounts.associateBy { it.id } }
    val categoryMap = remember(categories) { categories.associateBy { it.id } }

    var detailTransaction by remember { mutableStateOf<TransactionEntity?>(null) }
    var deleteCandidate by remember { mutableStateOf<TransactionEntity?>(null) }
    var showAccountFilterMenu by remember { mutableStateOf(false) }
    var showChannelFilterMenu by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding() + 8.dp,
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
                onPrev = { viewModel.shiftTxMonth(-1) },
                onNext = { viewModel.shiftTxMonth(1) },
                onResetCurrent = { viewModel.setTxMonth(YearMonth.now()) }
            )
        }

        // 月度主币种折算概览卡
        item(key = "monthly_overview") {
            MonthlyOverviewCard(
                baseCurrency = uiState.baseCurrency,
                expenseMicros = uiState.monthExpenseBaseMicros,
                incomeMicros = uiState.monthIncomeBaseMicros,
                balanceMicros = uiState.monthBalanceBaseMicros
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
                    viewModel = viewModel,
                    onToggleExpand = { viewModel.togglePendingExpanded() },
                    onConfirmAll = { viewModel.confirmAllPending() },
                    onConfirmOne = { viewModel.confirmTransaction(it.id) },
                    onEditOne = onEditTransaction,
                    onDeleteOne = { deleteCandidate = it }
                )
            }
        }

        // 搜索与筛选栏
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
            }
        }

        // 流水列表或空状态
        if (uiState.groupedDays.isEmpty()) {
            item(key = "empty_tx") {
                EmptyStateCard(
                    icon = Icons.Rounded.ReceiptLong,
                    title = "本月暂无符合条件的流水",
                    subtitle = "点击右下角「记一笔」，或前往「自动化」开启通知/短信自动记账与账单导入。",
                    actionLabel = "立即记一笔",
                    onAction = onOpenQuickRecord
                )
            }
        } else {
            items(uiState.groupedDays, key = { it.date.toString() }) { group ->
                DayTransactionCard(
                    group = group,
                    baseCurrency = uiState.baseCurrency,
                    accountMap = accountMap,
                    categoryMap = categoryMap,
                    rates = rates,
                    viewModel = viewModel,
                    onClickTransaction = { detailTransaction = it }
                )
            }
        }
    }

    // 交易详情弹窗（含原始报文溯源与沉淀指纹）
    detailTransaction?.let { tx ->
        TransactionDetailSheet(
            transaction = tx,
            account = accountMap[tx.accountId],
            toAccount = tx.toAccountId?.let { accountMap[it] },
            category = tx.categoryId?.let { categoryMap[it] },
            accounts = activeAccounts,
            viewModel = viewModel,
            onConfirm = {
                viewModel.confirmTransaction(tx.id)
                detailTransaction = null
            },
            onEdit = {
                detailTransaction = null
                onEditTransaction(tx)
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
}

@Composable
private fun MonthSwitcherBar(
    yearMonth: YearMonth,
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
        Row(verticalAlignment = Alignment.CenterVertically) {
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
    balanceMicros: Long
) {
    val semantic = KairoTheme.semantic
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
                        )
                    )
                )
                .padding(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "本月收支总览",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f)
                    ) {
                        Text(
                            text = "按冻结 $baseCurrency 折算",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                Column {
                    Text(
                        text = "月度结余",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f)
                    )
                    Text(
                        text = MoneyAmount.format(balanceMicros, baseCurrency, showSign = true),
                        style = MaterialTheme.typography.headlineMedium.tabular(),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
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
                                text = MoneyAmount.format(expenseMicros, baseCurrency),
                                style = MaterialTheme.typography.titleMedium.tabular(),
                                color = semantic.expense,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
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
                                text = MoneyAmount.format(incomeMicros, baseCurrency),
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
private fun PendingInboxBanner(
    pendingCount: Int,
    expanded: Boolean,
    pendingTransactions: List<TransactionEntity>,
    accountMap: Map<Long, AccountEntity>,
    categoryMap: Map<Long, CategoryEntity>,
    baseCurrency: String,
    rates: List<ExchangeRateEntity>,
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
                                            text = MoneyAmount.format(
                                                signedMicros,
                                                tx.origCurrency,
                                                showSign = tx.type == TransactionType.INCOME,
                                                showCurrencyCode = true
                                            ),
                                            style = MaterialTheme.typography.titleMedium.tabular(),
                                            color = transactionAmountColor(tx.type),
                                            fontWeight = FontWeight.Bold
                                        )
                                        if (!tx.origCurrency.equals(baseCurrency, true)) {
                                            val baseAmt = viewModel.convertTxToDisplayBase(tx, baseCurrency, rates)
                                            Text(
                                                text = "≈ ${MoneyAmount.format(baseAmt, baseCurrency)}",
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
private fun DayTransactionCard(
    group: DayTransactionGroup,
    baseCurrency: String,
    accountMap: Map<Long, AccountEntity>,
    categoryMap: Map<Long, CategoryEntity>,
    rates: List<ExchangeRateEntity>,
    viewModel: KairoMainViewModel,
    onClickTransaction: (TransactionEntity) -> Unit
) {
    val semantic = KairoTheme.semantic
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (group.dayIncomeBaseMicros > 0L) {
                        Text(
                            text = "收 ${MoneyAmount.format(group.dayIncomeBaseMicros, baseCurrency)}",
                            style = MaterialTheme.typography.labelMedium.tabular(),
                            color = semantic.income
                        )
                    }
                    if (group.dayExpenseBaseMicros > 0L) {
                        Text(
                            text = "支 ${MoneyAmount.format(group.dayExpenseBaseMicros, baseCurrency)}",
                            style = MaterialTheme.typography.labelMedium.tabular(),
                            color = semantic.expense
                        )
                    }
                }
            }

            group.transactions.forEachIndexed { idx, tx ->
                if (idx > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 14.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                    )
                }
                TransactionRowItem(
                    tx = tx,
                    baseCurrency = baseCurrency,
                    account = accountMap[tx.accountId],
                    toAccount = tx.toAccountId?.let { accountMap[it] },
                    category = tx.categoryId?.let { categoryMap[it] },
                    rates = rates,
                    viewModel = viewModel,
                    onClick = { onClickTransaction(tx) }
                )
            }
        }
    }
}

@Composable
private fun TransactionRowItem(
    tx: TransactionEntity,
    baseCurrency: String,
    account: AccountEntity?,
    toAccount: AccountEntity?,
    category: CategoryEntity?,
    rates: List<ExchangeRateEntity>,
    viewModel: KairoMainViewModel,
    onClick: () -> Unit
) {
    val semantic = KairoTheme.semantic
    val icon = when (tx.type) {
        TransactionType.TRANSFER -> Icons.Rounded.SwapHoriz
        TransactionType.ADJUST -> Icons.Rounded.Tune
        else -> KairoIcons.iconFor(category?.icon ?: "category")
    }
    val iconColor = when (tx.type) {
        TransactionType.TRANSFER -> semantic.transfer
        TransactionType.ADJUST -> MaterialTheme.colorScheme.tertiary
        else -> Color(category?.colorHex ?: 0xFF006874L)
    }
    val primaryTitle = when (tx.type) {
        TransactionType.TRANSFER -> "转账 · ${account?.name ?: "账户"} → ${toAccount?.name ?: "目标账户"}"
        TransactionType.ADJUST -> "余额校准 · ${account?.name ?: "账户"}"
        else -> category?.name ?: KairoFormats.transactionTypeLabel(tx.type)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = CircleShape,
            color = iconColor.copy(alpha = 0.14f),
            modifier = Modifier.size(42.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(21.dp)
                )
            }
        }

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

        // 双行金额展示：外币主行显示原币种，副行显示主币种冻结折算
        Column(horizontalAlignment = Alignment.End) {
            val signedMicros = when (tx.type) {
                TransactionType.EXPENSE -> -kotlin.math.abs(tx.origAmountMicros)
                TransactionType.INCOME -> kotlin.math.abs(tx.origAmountMicros)
                else -> tx.origAmountMicros
            }
            val showSign = tx.type == TransactionType.EXPENSE || tx.type == TransactionType.INCOME || tx.type == TransactionType.ADJUST
            val isForeign = !tx.origCurrency.equals(baseCurrency, ignoreCase = true)

            Text(
                text = MoneyAmount.format(
                    amountMicros = signedMicros,
                    currencyCode = tx.origCurrency,
                    showSign = showSign,
                    showCurrencyCode = isForeign || !tx.origCurrency.equals("CNY", true)
                ),
                style = MaterialTheme.typography.titleMedium.tabular(),
                color = transactionAmountColor(tx.type),
                fontWeight = FontWeight.Bold
            )

            if (isForeign) {
                val baseMicros = viewModel.convertTxToDisplayBase(tx, baseCurrency, rates)
                Text(
                    text = "≈ ${MoneyAmount.format(baseMicros, baseCurrency)}",
                    style = MaterialTheme.typography.bodySmall.tabular(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (!tx.accountCurrency.equals(tx.origCurrency, ignoreCase = true)) {
                Text(
                    text = "账户 ${MoneyAmount.format(tx.accountAmountMicros, tx.accountCurrency, showCurrencyCode = true)}",
                    style = MaterialTheme.typography.bodySmall.tabular(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (tx.type == TransactionType.TRANSFER && tx.toCurrency != null && tx.toAmountMicros != null && !tx.toCurrency.equals(tx.origCurrency, true)) {
                Text(
                    text = "→ ${MoneyAmount.format(tx.toAmountMicros, tx.toCurrency, showCurrencyCode = true)}",
                    style = MaterialTheme.typography.bodySmall.tabular(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransactionDetailSheet(
    transaction: TransactionEntity,
    account: AccountEntity?,
    toAccount: AccountEntity?,
    category: CategoryEntity?,
    accounts: List<AccountEntity>,
    viewModel: KairoMainViewModel,
    onConfirm: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var captureLog by remember(transaction.rawEventId) { mutableStateOf<RawCaptureLogEntity?>(null) }
    var showPromoteDialog by remember { mutableStateOf(false) }

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
                        TransactionType.EXPENSE -> -kotlin.math.abs(transaction.origAmountMicros)
                        TransactionType.INCOME -> kotlin.math.abs(transaction.origAmountMicros)
                        else -> transaction.origAmountMicros
                    }
                    Text(
                        text = MoneyAmount.format(
                            signedOrig,
                            transaction.origCurrency,
                            showSign = transaction.type != TransactionType.TRANSFER,
                            showCurrencyCode = true
                        ),
                        style = MaterialTheme.typography.headlineMedium.tabular(),
                        color = transactionAmountColor(transaction.type)
                    )
                    if (!transaction.origCurrency.equals(transaction.accountCurrency, true)) {
                        Text(
                            text = "账户入账：${MoneyAmount.format(transaction.accountAmountMicros, transaction.accountCurrency, showCurrencyCode = true)}（成交汇率 1 ${transaction.origCurrency} = ${MoneyAmount.formatRate(transaction.rateToAccountMicros)} ${transaction.accountCurrency}）",
                            style = MaterialTheme.typography.bodySmall.tabular(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (!transaction.origCurrency.equals(transaction.baseCurrency, true)) {
                        val frozenBase = MoneyAmount.convert(transaction.origAmountMicros, transaction.rateToBaseMicros)
                        Text(
                            text = "主币种冻结折算：${MoneyAmount.format(frozenBase, transaction.baseCurrency)}（快照汇率 ${MoneyAmount.formatRate(transaction.rateToBaseMicros)}）",
                            style = MaterialTheme.typography.bodySmall.tabular(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            DetailRow("类型", KairoFormats.transactionTypeLabel(transaction.type))
            DetailRow("账户", if (toAccount != null) "${account?.name ?: ""} → ${toAccount.name}" else (account?.name ?: "未知账户"))
            if (category != null) DetailRow("分类", category.name)
            if (transaction.merchant.isNotBlank()) DetailRow("商户", transaction.merchant)
            if (transaction.note.isNotBlank()) DetailRow("备注", transaction.note)
            if (transaction.tags.isNotBlank()) DetailRow("标签", transaction.tags)
            DetailRow("时间", KairoFormats.formatDateTime(transaction.timestamp))
            DetailRow("状态", if (transaction.status == TransactionStatus.PENDING) "待核对" else "已确认")

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
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.Delete, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("删除")
                }
                OutlinedButton(
                    onClick = onEdit,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.Edit, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("编辑")
                }
                if (transaction.status == TransactionStatus.PENDING) {
                    Button(
                        onClick = onConfirm,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.Check, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("确认")
                    }
                }
            }
        }
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

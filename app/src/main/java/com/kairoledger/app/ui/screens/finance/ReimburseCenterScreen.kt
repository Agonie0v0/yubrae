package com.kairoledger.app.ui.screens.finance

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kairoledger.app.core.model.CurrencyCatalog
import com.kairoledger.app.core.model.MoneyAmount
import com.kairoledger.app.data.db.AccountEntity
import com.kairoledger.app.data.db.CategoryEntity
import com.kairoledger.app.data.db.ExchangeRateEntity
import com.kairoledger.app.data.db.ReimburseStatus
import com.kairoledger.app.data.db.TransactionEntity
import com.kairoledger.app.ui.components.CategoryIconBadge
import com.kairoledger.app.ui.components.EmptyStateCard
import com.kairoledger.app.ui.components.KairoFormats
import com.kairoledger.app.ui.components.LocalPrivacyMasked
import com.kairoledger.app.ui.components.YimuCard
import com.kairoledger.app.ui.components.formatPrivacyAmount
import com.kairoledger.app.ui.theme.KairoTheme
import com.kairoledger.app.ui.theme.tabular

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReimburseCenterScreen(
    transactions: List<TransactionEntity>,
    accounts: List<AccountEntity>,
    categories: List<CategoryEntity>,
    baseCurrency: String,
    rates: List<ExchangeRateEntity>,
    convertToBase: (TransactionEntity) -> Long,
    onBack: () -> Unit,
    onSettleReimbursements: (txIds: List<Long>, targetAccountId: Long, note: String) -> Unit,
    onMarkStatusNone: (Long) -> Unit = {},
    onClickTransaction: (TransactionEntity) -> Unit
) {
    val privacyMasked = LocalPrivacyMasked.current
    val accountMap = remember(accounts) { accounts.associateBy { it.id } }
    val categoryMap = remember(categories) { categories.associateBy { it.id } }
    val txById = remember(transactions) { transactions.associateBy { it.id } }

    val pendingList = remember(transactions) {
        transactions
            .filter { it.reimburseStatus == ReimburseStatus.PENDING }
            .sortedByDescending { it.timestamp }
    }
    val reimbursedList = remember(transactions) {
        transactions
            .filter { it.reimburseStatus == ReimburseStatus.REIMBURSED }
            .sortedByDescending { it.timestamp }
    }

    val pendingTotalBaseMicros = remember(pendingList, convertToBase) {
        pendingList.sumOf { convertToBase(it) }
    }
    val reimbursedTotalBaseMicros = remember(reimbursedList, convertToBase) {
        reimbursedList.sumOf { convertToBase(it) }
    }

    var selectedTab by remember { mutableIntStateOf(0) } // 0 = 待报销, 1 = 已报销
    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var showSettleDialog by remember { mutableStateOf(false) }

    val validSelectedIds = remember(pendingList, selectedIds) {
        val pendingSet = pendingList.mapTo(HashSet()) { it.id }
        selectedIds.filterTo(HashSet()) { it in pendingSet }
    }
    val selectedPendingTxs = remember(pendingList, validSelectedIds) {
        pendingList.filter { it.id in validSelectedIds }
    }
    val selectedTotalBaseMicros = remember(selectedPendingTxs, convertToBase) {
        selectedPendingTxs.sumOf { convertToBase(it) }
    }

    val allSelected = pendingList.isNotEmpty() && validSelectedIds.size == pendingList.size

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("报销中心") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (selectedTab == 0 && pendingList.isNotEmpty()) {
                        TextButton(
                            onClick = {
                                selectedIds = if (allSelected) {
                                    emptySet()
                                } else {
                                    pendingList.mapTo(HashSet()) { it.id }
                                }
                            }
                        ) {
                            Text(if (allSelected) "取消全选" else "全选")
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (selectedTab == 0 && pendingList.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    tonalElevation = 3.dp,
                    shadowElevation = 6.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.navigationBarsPadding()) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "已选 ${validSelectedIds.size} 笔",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "合计约 ${
                                        formatPrivacyAmount(
                                            privacyMasked,
                                            MoneyAmount.format(
                                                selectedTotalBaseMicros,
                                                baseCurrency,
                                                showCurrencyCode = baseCurrency != "CNY"
                                            )
                                        )
                                    }",
                                    style = MaterialTheme.typography.titleMedium.tabular(),
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Button(
                                onClick = { showSettleDialog = true },
                                enabled = validSelectedIds.isNotEmpty(),
                                shape = CircleShape,
                                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp)
                            ) {
                                Icon(Icons.Rounded.DoneAll, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("一键报销入账", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding() + 8.dp,
                bottom = innerPadding.calculateBottomPadding() + 24.dp,
                start = 16.dp,
                end = 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 顶部柔和绿汇总卡片 + Segmented Tab
            item(key = "reimburse_summary_hero") {
                ReimburseSummaryHeroCard(
                    baseCurrency = baseCurrency,
                    pendingTotalBaseMicros = pendingTotalBaseMicros,
                    pendingCount = pendingList.size,
                    reimbursedTotalBaseMicros = reimbursedTotalBaseMicros,
                    reimbursedCount = reimbursedList.size,
                    selectedTab = selectedTab,
                    privacyMasked = privacyMasked,
                    onSelectTab = { selectedTab = it }
                )
            }

            if (selectedTab == 0) {
                if (pendingList.isEmpty()) {
                    item(key = "empty_pending_reimburse") {
                        EmptyStateCard(
                            icon = Icons.AutoMirrored.Rounded.ReceiptLong,
                            title = "暂无待报销账单",
                            subtitle = "记账时将支出标记为「待报销」，即可在此统一勾选核销并折算入账。"
                        )
                    }
                } else {
                    item(key = "select_all_row") {
                        YimuCard(cornerRadius = 14.dp) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedIds = if (allSelected) {
                                            emptySet()
                                        } else {
                                            pendingList.mapTo(HashSet()) { it.id }
                                        }
                                    }
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = allSelected,
                                        onCheckedChange = { checked ->
                                            selectedIds = if (checked) {
                                                pendingList.mapTo(HashSet()) { it.id }
                                            } else {
                                                emptySet()
                                            }
                                        }
                                    )
                                    Text(
                                        text = "全选待报销 (${validSelectedIds.size}/${pendingList.size})",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                if (validSelectedIds.isNotEmpty()) {
                                    TextButton(onClick = { selectedIds = emptySet() }) {
                                        Text("清空选择")
                                    }
                                }
                            }
                        }
                    }

                    items(pendingList, key = { it.id }) { tx ->
                        val category = tx.categoryId?.let { categoryMap[it] }
                        val account = accountMap[tx.accountId]
                        val isChecked = tx.id in validSelectedIds
                        PendingReimburseItemCard(
                            transaction = tx,
                            category = category,
                            account = account,
                            baseCurrency = baseCurrency,
                            convertedBaseMicros = convertToBase(tx),
                            checked = isChecked,
                            privacyMasked = privacyMasked,
                            onToggleChecked = {
                                selectedIds = if (isChecked) selectedIds - tx.id else selectedIds + tx.id
                            },
                            onMarkStatusNone = {
                                selectedIds = selectedIds - tx.id
                                onMarkStatusNone(tx.id)
                            },
                            onClick = { onClickTransaction(tx) }
                        )
                    }
                }
            } else {
                if (reimbursedList.isEmpty()) {
                    item(key = "empty_reimbursed") {
                        EmptyStateCard(
                            icon = Icons.Rounded.CheckCircle,
                            title = "暂无已报销记录",
                            subtitle = "完成报销入账后，历史已核销明细与关联回款流水将记录在此。"
                        )
                    }
                } else {
                    items(reimbursedList, key = { it.id }) { tx ->
                        val category = tx.categoryId?.let { categoryMap[it] }
                        val account = accountMap[tx.accountId]
                        val linkedIncomeTx = tx.reimbursedTxId?.let { txById[it] }
                        val linkedAccount = linkedIncomeTx?.let { accountMap[it.accountId] }
                        ReimbursedItemCard(
                            transaction = tx,
                            category = category,
                            account = account,
                            linkedIncomeTx = linkedIncomeTx,
                            linkedAccount = linkedAccount,
                            baseCurrency = baseCurrency,
                            convertedBaseMicros = convertToBase(tx),
                            privacyMasked = privacyMasked,
                            onClick = { onClickTransaction(tx) }
                        )
                    }
                }
            }
        }
    }

    if (showSettleDialog) {
        SettleReimburseDialog(
            selectedTransactions = selectedPendingTxs,
            accounts = accounts,
            baseCurrency = baseCurrency,
            rates = rates,
            privacyMasked = privacyMasked,
            onConfirm = { targetAccountId, note ->
                onSettleReimbursements(selectedPendingTxs.map { it.id }, targetAccountId, note)
                selectedIds = emptySet()
                showSettleDialog = false
            },
            onDismiss = { showSettleDialog = false }
        )
    }
}

@Composable
private fun ReimburseSummaryHeroCard(
    baseCurrency: String,
    pendingTotalBaseMicros: Long,
    pendingCount: Int,
    reimbursedTotalBaseMicros: Long,
    reimbursedCount: Int,
    selectedTab: Int,
    privacyMasked: Boolean,
    onSelectTab: (Int) -> Unit
) {
    val semantic = KairoTheme.semantic
    val gradientColors = semantic.heroGradient
    val titleColor = semantic.heroOnSurface
    val borderColor = semantic.heroBorder
    val pillBg = semantic.heroPillBg

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
                Text(
                    text = "待报销总额（按 $baseCurrency 折算）",
                    style = MaterialTheme.typography.titleSmall,
                    color = titleColor
                )
                Text(
                    text = formatPrivacyAmount(
                        privacyMasked,
                        MoneyAmount.format(
                            amountMicros = pendingTotalBaseMicros,
                            currencyCode = baseCurrency,
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
                                text = "待报销笔数",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "$pendingCount 笔",
                                style = MaterialTheme.typography.titleMedium.tabular(),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
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
                                text = "已累计报销 ($reimbursedCount 笔)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = formatPrivacyAmount(
                                    privacyMasked,
                                    MoneyAmount.format(reimbursedTotalBaseMicros, baseCurrency)
                                ),
                                style = MaterialTheme.typography.titleMedium.tabular(),
                                fontWeight = FontWeight.Bold,
                                color = semantic.income,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // 分段标签栏 Segmented Tab: 待报销 | 已报销
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(CircleShape)
                        .background(pillBg)
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val tabs = listOf("待报销 ($pendingCount)", "已报销 ($reimbursedCount)")
                    tabs.forEachIndexed { idx, label ->
                        val active = selectedTab == idx
                        Surface(
                            shape = CircleShape,
                            color = if (active) MaterialTheme.colorScheme.primary else Color.Transparent,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onSelectTab(idx) }
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
private fun PendingReimburseItemCard(
    transaction: TransactionEntity,
    category: CategoryEntity?,
    account: AccountEntity?,
    baseCurrency: String,
    convertedBaseMicros: Long,
    checked: Boolean,
    privacyMasked: Boolean,
    onToggleChecked: () -> Unit,
    onMarkStatusNone: () -> Unit,
    onClick: () -> Unit
) {
    val semantic = KairoTheme.semantic
    val isForeign = !transaction.origCurrency.equals(baseCurrency, ignoreCase = true)
    val title = transaction.merchant.ifBlank { category?.name ?: "待报销支出" }

    YimuCard(
        cornerRadius = 18.dp,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 6.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = checked,
                onCheckedChange = { onToggleChecked() }
            )

            CategoryIconBadge(
                iconKey = category?.icon ?: "receipt_long",
                colorHex = category?.colorHex ?: 0xFF2AA876L,
                size = 40.dp,
                iconSize = 20.dp
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
                Text(
                    text = buildString {
                        append(KairoFormats.formatDateTime(transaction.timestamp))
                        if (account != null) {
                            append(" · ")
                            append(account.name)
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (transaction.note.isNotBlank()) {
                    Text(
                        text = transaction.note,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = formatPrivacyAmount(
                        privacyMasked,
                        MoneyAmount.format(
                            amountMicros = transaction.origAmountMicros,
                            currencyCode = transaction.origCurrency,
                            showCurrencyCode = isForeign
                        )
                    ),
                    style = MaterialTheme.typography.titleMedium.tabular(),
                    color = semantic.expense,
                    fontWeight = FontWeight.Bold
                )
                if (isForeign) {
                    Text(
                        text = formatPrivacyAmount(
                            privacyMasked,
                            "≈ ${MoneyAmount.format(convertedBaseMicros, baseCurrency)}"
                        ),
                        style = MaterialTheme.typography.labelSmall.tabular(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.clickable(onClick = onMarkStatusNone)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(11.dp)
                        )
                        Text(
                            text = "取消报销",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReimbursedItemCard(
    transaction: TransactionEntity,
    category: CategoryEntity?,
    account: AccountEntity?,
    linkedIncomeTx: TransactionEntity?,
    linkedAccount: AccountEntity?,
    baseCurrency: String,
    convertedBaseMicros: Long,
    privacyMasked: Boolean,
    onClick: () -> Unit
) {
    val semantic = KairoTheme.semantic
    val isForeign = !transaction.origCurrency.equals(baseCurrency, ignoreCase = true)
    val title = transaction.merchant.ifBlank { category?.name ?: "已报销支出" }

    YimuCard(
        cornerRadius = 18.dp,
        onClick = onClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CategoryIconBadge(
                    iconKey = category?.icon ?: "receipt_long",
                    colorHex = category?.colorHex ?: 0xFF2AA876L,
                    size = 40.dp,
                    iconSize = 20.dp
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
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = semantic.incomeContainer
                        ) {
                            Text(
                                text = "已报销",
                                style = MaterialTheme.typography.labelSmall,
                                color = semantic.income,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                            )
                        }
                    }
                    Text(
                        text = buildString {
                            append(KairoFormats.formatDateTime(transaction.timestamp))
                            if (account != null) {
                                append(" · ")
                                append(account.name)
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = formatPrivacyAmount(
                            privacyMasked,
                            MoneyAmount.format(
                                amountMicros = transaction.origAmountMicros,
                                currencyCode = transaction.origCurrency,
                                showCurrencyCode = isForeign
                            )
                        ),
                        style = MaterialTheme.typography.titleSmall.tabular(),
                        fontWeight = FontWeight.Bold
                    )
                    if (isForeign) {
                        Text(
                            text = formatPrivacyAmount(
                                privacyMasked,
                                "≈ ${MoneyAmount.format(convertedBaseMicros, baseCurrency)}"
                            ),
                            style = MaterialTheme.typography.labelSmall.tabular(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (transaction.reimbursedTxId != null) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Payments,
                            contentDescription = null,
                            tint = semantic.income,
                            modifier = Modifier.size(15.dp)
                        )
                        val linkDetail = buildString {
                            append("回款关联流水 #${transaction.reimbursedTxId}")
                            if (linkedIncomeTx != null) {
                                append(" · ")
                                append(linkedAccount?.name ?: "入账账户")
                                append(" ")
                                append(KairoFormats.formatDate(linkedIncomeTx.timestamp))
                                append(" (")
                                append(
                                    formatPrivacyAmount(
                                        privacyMasked,
                                        MoneyAmount.format(
                                            linkedIncomeTx.origAmountMicros,
                                            linkedIncomeTx.origCurrency,
                                            showSign = true,
                                            showCurrencyCode = !linkedIncomeTx.origCurrency.equals(baseCurrency, true)
                                        )
                                    )
                                )
                                append(")")
                            }
                        }
                        Text(
                            text = linkDetail,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettleReimburseDialog(
    selectedTransactions: List<TransactionEntity>,
    accounts: List<AccountEntity>,
    baseCurrency: String,
    rates: List<ExchangeRateEntity>,
    privacyMasked: Boolean,
    onConfirm: (targetAccountId: Long, note: String) -> Unit,
    onDismiss: () -> Unit
) {
    val activeAccounts = remember(accounts) {
        accounts.filter { !it.archived }.ifEmpty { accounts }
    }
    var targetAccountId by remember(activeAccounts) {
        mutableStateOf(activeAccounts.firstOrNull()?.id)
    }
    var note by remember(selectedTransactions.size) {
        mutableStateOf("报销核销 (${selectedTransactions.size} 笔)")
    }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val selectedAccount = activeAccounts.firstOrNull { it.id == targetAccountId }
    val estimatedTargetMicros = remember(selectedTransactions, selectedAccount, rates) {
        if (selectedAccount == null) {
            0L
        } else {
            val targetCode = selectedAccount.currencyCode
            selectedTransactions.sumOf { tx ->
                if (tx.origCurrency.equals(targetCode, ignoreCase = true)) {
                    tx.origAmountMicros
                } else {
                    val rate = resolveRateFromEntities(tx.origCurrency, targetCode, rates)
                    MoneyAmount.convert(tx.origAmountMicros, rate)
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("一键报销入账") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "选择回款账户（共 ${selectedTransactions.size} 笔待报销）",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (activeAccounts.isEmpty()) {
                    Text(
                        text = "当前没有可用资产账户，请先新建账户。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(activeAccounts, key = { it.id }) { acc ->
                            val selected = acc.id == targetAccountId
                            FilterChip(
                                selected = selected,
                                onClick = { targetAccountId = acc.id },
                                label = {
                                    Text("${acc.name} (${acc.currencyCode})")
                                }
                            )
                        }
                    }
                }

                if (selectedAccount != null) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(
                                text = "预计入账「${selectedAccount.name}」",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = formatPrivacyAmount(
                                    privacyMasked,
                                    MoneyAmount.format(
                                        estimatedTargetMicros,
                                        selectedAccount.currencyCode,
                                        showCurrencyCode = !selectedAccount.currencyCode.equals(baseCurrency, true)
                                    )
                                ),
                                style = MaterialTheme.typography.titleMedium.tabular(),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("回款说明") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "提示：将自动按账户币种汇率折算入账，不虚增消费收入统计。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                    )
                }

                errorMsg?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(
                enabled = targetAccountId != null && selectedTransactions.isNotEmpty(),
                onClick = {
                    val chosen = targetAccountId
                    if (chosen == null) {
                        errorMsg = "请选择回款资金账户"
                    } else {
                        onConfirm(chosen, note.trim().ifEmpty { "报销核销 (${selectedTransactions.size} 笔)" })
                    }
                }
            ) {
                Text("确认入账")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

private fun resolveRateFromEntities(
    fromCurrency: String,
    toCurrency: String,
    rates: List<ExchangeRateEntity>
): Long {
    val from = fromCurrency.trim().uppercase()
    val to = toCurrency.trim().uppercase()
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
    val fromToCny = if (from == cny) MoneyAmount.RATE_SCALE else {
        rates.filter { it.baseCurrency.equals(from, true) && it.targetCurrency.equals(cny, true) }
            .maxByOrNull { it.date + "_" + it.updatedAt }?.rateMicros
            ?: rates.filter { it.baseCurrency.equals(cny, true) && it.targetCurrency.equals(from, true) }
                .maxByOrNull { it.date + "_" + it.updatedAt }?.let { MoneyAmount.invertRate(it.rateMicros) }
            ?: CurrencyCatalog.find(from).fallbackRateToCnyMicros
    }
    val toToCny = if (to == cny) MoneyAmount.RATE_SCALE else {
        rates.filter { it.baseCurrency.equals(to, true) && it.targetCurrency.equals(cny, true) }
            .maxByOrNull { it.date + "_" + it.updatedAt }?.rateMicros
            ?: rates.filter { it.baseCurrency.equals(cny, true) && it.targetCurrency.equals(to, true) }
                .maxByOrNull { it.date + "_" + it.updatedAt }?.let { MoneyAmount.invertRate(it.rateMicros) }
            ?: CurrencyCatalog.find(to).fallbackRateToCnyMicros
    }
    return MoneyAmount.crossRate(fromToCny, toToCny)
}

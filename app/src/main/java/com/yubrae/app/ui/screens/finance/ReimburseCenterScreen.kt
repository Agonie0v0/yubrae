package com.yubrae.app.ui.screens.finance

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yubrae.app.core.model.CurrencyCatalog
import com.yubrae.app.core.model.MoneyAmount
import com.yubrae.app.data.db.AccountEntity
import com.yubrae.app.data.db.CategoryEntity
import com.yubrae.app.data.db.ExchangeRateEntity
import com.yubrae.app.data.db.ReimburseStatus
import com.yubrae.app.data.db.TransactionEntity
import com.yubrae.app.ui.components.CategoryIconBadge
import com.yubrae.app.ui.components.CompactHairlineDivider
import com.yubrae.app.ui.components.EmptyStateCard
import com.yubrae.app.ui.components.KairoFormats
import com.yubrae.app.ui.components.LocalPrivacyMasked
import com.yubrae.app.ui.components.YimuCard
import com.yubrae.app.ui.components.YubraeDialog
import com.yubrae.app.ui.components.formatPrivacyAmount
import com.yubrae.app.ui.theme.KairoTheme
import com.yubrae.app.ui.theme.tabular

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
                    tonalElevation = 2.dp,
                    shadowElevation = 4.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.navigationBarsPadding()) {
                        CompactHairlineDivider(startIndent = 0.dp)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "已选 ${validSelectedIds.size} 笔",
                                    style = MaterialTheme.typography.labelSmall,
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
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 9.dp)
                            ) {
                                Icon(Icons.Rounded.DoneAll, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(5.dp))
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
                top = innerPadding.calculateTopPadding() + 6.dp,
                bottom = innerPadding.calculateBottomPadding() + 20.dp,
                start = 12.dp,
                end = 12.dp
            )
        ) {
            // 顶部柔和汇总卡片 + Segmented Tab
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
                Spacer(modifier = Modifier.height(10.dp))
            }

            if (selectedTab == 0) {
                if (pendingList.isEmpty()) {
                    item(key = "empty_pending_reimburse") {
                        EmptyStateCard(
                            icon = Icons.AutoMirrored.Rounded.ReceiptLong,
                            title = "暂无待报销账单"
                        )
                    }
                } else {
                    item(key = "select_all_row") {
                        GroupedListRowContainer(
                            isFirst = true,
                            isLast = false,
                            onClick = {
                                selectedIds = if (allSelected) {
                                    emptySet()
                                } else {
                                    pendingList.mapTo(HashSet()) { it.id }
                                }
                            }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
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
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (validSelectedIds.isNotEmpty()) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    TextButton(
                                        onClick = { selectedIds = emptySet() },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier.heightIn(min = 30.dp)
                                    ) {
                                        Text(
                                            text = "清空选择",
                                            style = MaterialTheme.typography.labelMedium,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }
                            }
                            CompactHairlineDivider(startIndent = 12.dp, modifier = Modifier.padding(end = 12.dp))
                        }
                    }

                    itemsIndexed(pendingList, key = { _, tx -> tx.id }) { idx, tx ->
                        val category = tx.categoryId?.let { categoryMap[it] }
                        val account = accountMap[tx.accountId]
                        val isChecked = tx.id in validSelectedIds
                        val isLast = idx == pendingList.lastIndex
                        GroupedListRowContainer(
                            isFirst = false,
                            isLast = isLast,
                            onClick = { onClickTransaction(tx) }
                        ) {
                            PendingReimburseRow(
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
                                }
                            )
                            if (!isLast) {
                                CompactHairlineDivider(startIndent = 52.dp)
                            }
                        }
                    }
                }
            } else {
                if (reimbursedList.isEmpty()) {
                    item(key = "empty_reimbursed") {
                        EmptyStateCard(
                            icon = Icons.Rounded.CheckCircle,
                            title = "暂无已报销记录",
                            actionLabel = if (pendingList.isNotEmpty()) "查看待报销" else null,
                            onAction = if (pendingList.isNotEmpty()) {
                                { selectedTab = 0 }
                            } else null
                        )
                    }
                } else {
                    itemsIndexed(reimbursedList, key = { _, tx -> tx.id }) { idx, tx ->
                        val category = tx.categoryId?.let { categoryMap[it] }
                        val account = accountMap[tx.accountId]
                        val linkedIncomeTx = tx.reimbursedTxId?.let { txById[it] }
                        val linkedAccount = linkedIncomeTx?.let { accountMap[it.accountId] }
                        val isFirst = idx == 0
                        val isLast = idx == reimbursedList.lastIndex
                        GroupedListRowContainer(
                            isFirst = isFirst,
                            isLast = isLast,
                            onClick = { onClickTransaction(tx) }
                        ) {
                            ReimbursedRow(
                                transaction = tx,
                                category = category,
                                account = account,
                                linkedIncomeTx = linkedIncomeTx,
                                linkedAccount = linkedAccount,
                                baseCurrency = baseCurrency,
                                convertedBaseMicros = convertToBase(tx),
                                privacyMasked = privacyMasked
                            )
                            if (!isLast) {
                                CompactHairlineDivider(startIndent = 56.dp)
                            }
                        }
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
                Text(
                    text = "待报销 ($baseCurrency)",
                    style = MaterialTheme.typography.labelMedium,
                    color = titleColor.copy(alpha = 0.78f)
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
                            text = "待报销",
                            style = MaterialTheme.typography.labelSmall,
                            color = titleColor.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "$pendingCount 笔",
                            style = MaterialTheme.typography.titleMedium.tabular(),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
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
                            text = "已报销 · $reimbursedCount 笔",
                            style = MaterialTheme.typography.labelSmall,
                            color = titleColor.copy(alpha = 0.7f)
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

                // 分段标签栏 Segmented Tab: 待报销 | 已报销
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(CircleShape)
                        .background(pillBg)
                        .padding(2.5.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
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
private fun GroupedListRowContainer(
    isFirst: Boolean,
    isLast: Boolean,
    onClick: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    if (isFirst && isLast) {
        YimuCard(onClick = onClick, content = content)
        return
    }

    val shape = when {
        isFirst -> RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp)
        isLast -> RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp)
        else -> RectangleShape
    }
    val borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.32f)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .drawBehind {
                val strokeW = 0.5.dp.toPx()
                val half = strokeW / 2f
                val r = 14.dp.toPx() - half
                val w = size.width
                val h = size.height
                when {
                    isFirst -> {
                        val path = Path().apply {
                            moveTo(half, h)
                            lineTo(half, half + r)
                            arcTo(Rect(half, half, half + 2f * r, half + 2f * r), 180f, 90f, false)
                            lineTo(w - half - r, half)
                            arcTo(Rect(w - half - 2f * r, half, w - half, half + 2f * r), 270f, 90f, false)
                            lineTo(w - half, h)
                        }
                        drawPath(path = path, color = borderColor, style = Stroke(width = strokeW))
                    }
                    isLast -> {
                        val path = Path().apply {
                            moveTo(half, 0f)
                            lineTo(half, h - half - r)
                            arcTo(Rect(half, h - half - 2f * r, half + 2f * r, h - half), 180f, -90f, false)
                            lineTo(w - half - r, h - half)
                            arcTo(Rect(w - half - 2f * r, h - half - 2f * r, w - half, h - half), 90f, -90f, false)
                            lineTo(w - half, 0f)
                        }
                        drawPath(path = path, color = borderColor, style = Stroke(width = strokeW))
                    }
                    else -> {
                        drawLine(color = borderColor, start = Offset(half, 0f), end = Offset(half, h), strokeWidth = strokeW)
                        drawLine(color = borderColor, start = Offset(w - half, 0f), end = Offset(w - half, h), strokeWidth = strokeW)
                    }
                }
            }
            .clickable(onClick = onClick),
        content = content
    )
}

@Composable
private fun PendingReimburseRow(
    transaction: TransactionEntity,
    category: CategoryEntity?,
    account: AccountEntity?,
    baseCurrency: String,
    convertedBaseMicros: Long,
    checked: Boolean,
    privacyMasked: Boolean,
    onToggleChecked: () -> Unit,
    onMarkStatusNone: () -> Unit
) {
    val semantic = KairoTheme.semantic
    val isForeign = !transaction.origCurrency.equals(baseCurrency, ignoreCase = true)
    val title = transaction.merchant.ifBlank { category?.name ?: "待报销支出" }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = { onToggleChecked() }
        )

        CategoryIconBadge(
            iconKey = category?.icon ?: "receipt_long",
            colorHex = category?.colorHex ?: 0xFF2AA876L,
            size = 34.dp,
            iconSize = 17.dp
        )

        Spacer(modifier = Modifier.width(8.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
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
                    if (transaction.note.isNotBlank()) {
                        append(" · ")
                        append(transaction.note)
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
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
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onMarkStatusNone)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(10.dp)
                    )
                    Text(
                        text = "取消报销",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}

@Composable
private fun ReimbursedRow(
    transaction: TransactionEntity,
    category: CategoryEntity?,
    account: AccountEntity?,
    linkedIncomeTx: TransactionEntity?,
    linkedAccount: AccountEntity?,
    baseCurrency: String,
    convertedBaseMicros: Long,
    privacyMasked: Boolean
) {
    val semantic = KairoTheme.semantic
    val isForeign = !transaction.origCurrency.equals(baseCurrency, ignoreCase = true)
    val title = transaction.merchant.ifBlank { category?.name ?: "已报销支出" }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CategoryIconBadge(
                iconKey = category?.icon ?: "receipt_long",
                colorHex = category?.colorHex ?: 0xFF2AA876L,
                size = 34.dp,
                iconSize = 17.dp
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
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
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 44.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Payments,
                        contentDescription = null,
                        tint = semantic.income,
                        modifier = Modifier.size(13.dp)
                    )
                    val linkDetail = buildString {
                        append("回款 #${transaction.reimbursedTxId}")
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
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
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

    YubraeDialog(
        onDismissRequest = onDismiss,
        title = "一键报销入账",
        icon = Icons.AutoMirrored.Rounded.ReceiptLong,
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
                },
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("确认入账")
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
                text = "回款账户 · ${selectedTransactions.size} 笔",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (activeAccounts.isEmpty()) {
                Text(
                    text = "暂无可用账户",
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
                            text = "入账「${selectedAccount.name}」",
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
                label = { Text("备注") },
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

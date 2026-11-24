package com.yubrae.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yubrae.app.core.model.CurrencyCatalog
import com.yubrae.app.data.db.BudgetEntity
import com.yubrae.app.data.db.LedgerEntity
import com.yubrae.app.data.db.TransactionEntity

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LedgerManageSheet(
    ledgers: List<LedgerEntity>,
    activeLedgerId: Long,
    pinnedCurrencies: List<String>,
    onSelectLedger: (Long) -> Unit,
    onSaveLedger: (LedgerEntity) -> Unit,
    onArchiveLedger: (Long, Boolean) -> Unit,
    onDismiss: () -> Unit,
    transactions: List<TransactionEntity> = emptyList(),
    budgets: List<BudgetEntity> = emptyList(),
    onDeleteLedger: (ledgerId: Long, migrateToLedgerId: Long?) -> Unit = { _, _ -> }
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var editingLedger by remember { mutableStateOf<LedgerEntity?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var deleteCandidate by remember { mutableStateOf<LedgerEntity?>(null) }

    val activeLedgers = remember(ledgers) {
        ledgers.filter { !it.archived }.sortedWith(compareBy({ it.sortOrder }, { it.id }))
    }
    val archivedLedgers = remember(ledgers) {
        ledgers.filter { it.archived }.sortedWith(compareBy({ it.sortOrder }, { it.id }))
    }
    val txCountByLedger = remember(transactions) {
        transactions.groupingBy { it.ledgerId }.eachCount()
    }
    val budgetCountByLedger = remember(budgets) {
        budgets.groupingBy { it.ledgerId }.eachCount()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "账本管理",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = { showCreateDialog = true },
                        shape = CircleShape,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Rounded.Add, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("新建账本", style = MaterialTheme.typography.labelLarge)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "关闭")
                    }
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                // 全部账本汇总（0L）
                item(key = "all_ledgers_summary") {
                    val isSelected = activeLedgerId == 0L
                    YimuCard(
                        containerColor = if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerLowest
                        },
                        onClick = {
                            onSelectLedger(0L)
                            onDismiss()
                        }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.primaryContainer
                                },
                                modifier = Modifier.size(42.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.MenuBook,
                                        contentDescription = null,
                                        tint = if (isSelected) {
                                            MaterialTheme.colorScheme.onPrimary
                                        } else {
                                            MaterialTheme.colorScheme.primary
                                        },
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "全部账本汇总",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (isSelected) {
                                        LedgerTagChip(
                                            text = "当前",
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary
                                        )
                                    }
                                }
                                Text(
                                    text = "${activeLedgers.size} 个活跃账本",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = "已选中",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                // 活跃账本列表
                items(activeLedgers, key = { it.id }) { ledger ->
                    val isSelected = ledger.id == activeLedgerId
                    val canArchiveOrDelete = activeLedgers.size > 1
                    LedgerCardItem(
                        ledger = ledger,
                        txCount = txCountByLedger[ledger.id] ?: 0,
                        selected = isSelected,
                        canArchiveOrDelete = canArchiveOrDelete,
                        onSelect = {
                            onSelectLedger(ledger.id)
                            onDismiss()
                        },
                        onEdit = { editingLedger = ledger },
                        onToggleArchive = {
                            if (canArchiveOrDelete) {
                                if (isSelected) {
                                    activeLedgers.firstOrNull { it.id != ledger.id }?.id?.let(onSelectLedger)
                                }
                                onArchiveLedger(ledger.id, true)
                            }
                        },
                        onDelete = {
                            if (canArchiveOrDelete) {
                                deleteCandidate = ledger
                            }
                        }
                    )
                }

                if (activeLedgers.size <= 1) {
                    item(key = "last_active_ledger_hint") {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = "至少保留 1 个活跃账本，仅剩最后 1 个时禁用归档与删除",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // 已归档账本
                if (archivedLedgers.isNotEmpty()) {
                    item(key = "archived_header") {
                        Text(
                            text = "已归档账本 (${archivedLedgers.size})",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    items(archivedLedgers, key = { it.id }) { ledger ->
                        LedgerCardItem(
                            ledger = ledger,
                            txCount = txCountByLedger[ledger.id] ?: 0,
                            selected = ledger.id == activeLedgerId,
                            canArchiveOrDelete = true,
                            onSelect = {
                                onSelectLedger(ledger.id)
                                onDismiss()
                            },
                            onEdit = { editingLedger = ledger },
                            onToggleArchive = { onArchiveLedger(ledger.id, false) },
                            onDelete = { deleteCandidate = ledger }
                        )
                    }
                }
            }
        }
    }

    if (showCreateDialog || editingLedger != null) {
        LedgerEditorDialog(
            editing = editingLedger,
            pinnedCurrencies = pinnedCurrencies,
            nextSortOrder = (ledgers.maxOfOrNull { it.sortOrder } ?: 0) + 10,
            onSave = { saved ->
                onSaveLedger(saved)
                showCreateDialog = false
                editingLedger = null
            },
            onDismiss = {
                showCreateDialog = false
                editingLedger = null
            }
        )
    }

    deleteCandidate?.let { target ->
        val txCount = txCountByLedger[target.id] ?: 0
        val budgetCount = budgetCountByLedger[target.id] ?: 0
        val migrationTargets = activeLedgers.filter { it.id != target.id }
        DeleteLedgerConfirmDialog(
            ledger = target,
            txCount = txCount,
            budgetCount = budgetCount,
            migrationTargets = migrationTargets,
            onConfirmDelete = { migrateToLedgerId ->
                onDeleteLedger(target.id, migrateToLedgerId)
                deleteCandidate = null
            },
            onDismiss = { deleteCandidate = null }
        )
    }
}

@Composable
private fun LedgerCardItem(
    ledger: LedgerEntity,
    txCount: Int,
    selected: Boolean,
    canArchiveOrDelete: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onToggleArchive: () -> Unit,
    onDelete: () -> Unit
) {
    val accentColor = Color(ledger.colorHex)
    val currencyInfo = CurrencyCatalog.find(ledger.currencyCode)
    YimuCard(
        containerColor = when {
            selected -> accentColor.copy(alpha = 0.12f)
            ledger.archived -> MaterialTheme.colorScheme.surfaceContainerLow
            else -> MaterialTheme.colorScheme.surfaceContainerLowest
        },
        onClick = onSelect
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CategoryIconBadge(
                iconKey = ledger.icon,
                colorHex = ledger.colorHex,
                size = 42.dp,
                iconSize = 20.dp,
                selected = selected
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = ledger.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (selected) {
                        LedgerTagChip(
                            text = "当前",
                            containerColor = accentColor,
                            contentColor = Color.White
                        )
                    }
                    if (ledger.archived) {
                        LedgerTagChip(
                            text = "已归档",
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Text(
                    text = "${currencyInfo.flag} ${currencyInfo.code} · ${currencyInfo.nameZh} · $txCount 笔",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Rounded.Edit,
                    contentDescription = "编辑账本",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
            IconButton(
                onClick = onToggleArchive,
                enabled = canArchiveOrDelete,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = if (ledger.archived) Icons.Rounded.Unarchive else Icons.Rounded.Archive,
                    contentDescription = if (ledger.archived) "取消归档" else "归档账本",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                        alpha = if (canArchiveOrDelete) 1f else 0.35f
                    ),
                    modifier = Modifier.size(18.dp)
                )
            }
            IconButton(
                onClick = onDelete,
                enabled = canArchiveOrDelete,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Delete,
                    contentDescription = "删除账本",
                    tint = MaterialTheme.colorScheme.error.copy(
                        alpha = if (canArchiveOrDelete) 1f else 0.35f
                    ),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DeleteLedgerConfirmDialog(
    ledger: LedgerEntity,
    txCount: Int,
    budgetCount: Int,
    migrationTargets: List<LedgerEntity>,
    onConfirmDelete: (migrateToLedgerId: Long?) -> Unit,
    onDismiss: () -> Unit
) {
    val hasLinkedData = txCount > 0 || budgetCount > 0
    if (!hasLinkedData) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("删除账本「${ledger.name}」？") },
            text = {
                Text(
                    text = "该账本下暂无关联流水与预算，确认后将永久删除该账本。",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = { onConfirmDelete(null) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("确认删除")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        )
        return
    }

    val canMigrate = migrationTargets.isNotEmpty()
    var migrateMode by remember(ledger.id, canMigrate) { mutableStateOf(canMigrate) }
    var targetLedgerId by remember(ledger.id, migrationTargets) {
        mutableLongStateOf(migrationTargets.firstOrNull()?.id ?: 0L)
    }
    val impactSummary = buildList {
        if (txCount > 0) add("$txCount 笔流水")
        if (budgetCount > 0) add("$budgetCount 项预算")
    }.joinToString("、")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("删除账本「${ledger.name}」") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "该账本关联 $impactSummary，请选择删除方式：",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (canMigrate) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (migrateMode) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerLow
                        },
                        border = BorderStroke(
                            width = if (migrateMode) 1.5.dp else 0.5.dp,
                            color = if (migrateMode) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { migrateMode = true }
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = migrateMode,
                                    onClick = { migrateMode = true },
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "迁移流水至指定活跃账本后删除",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f)
                                )
                                LedgerTagChip(
                                    text = "推荐",
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                            Text(
                                text = "保留所有历史交易记录与账户余额，未冲突预算一并迁移",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (migrateMode) {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    migrationTargets.forEach { dest ->
                                        val isDestSelected = targetLedgerId == dest.id
                                        FilterChip(
                                            selected = isDestSelected,
                                            onClick = { targetLedgerId = dest.id },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = KairoIcons.ledgerIcon(dest.icon),
                                                    contentDescription = null,
                                                    tint = Color(dest.colorHex),
                                                    modifier = Modifier.size(15.dp)
                                                )
                                            },
                                            label = { Text(dest.name, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (!migrateMode) {
                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    },
                    border = BorderStroke(
                        width = if (!migrateMode) 1.5.dp else 0.5.dp,
                        color = if (!migrateMode) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { migrateMode = false }
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = !migrateMode,
                                onClick = { migrateMode = false },
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "连带流水彻底删除（同步回滚账户余额）",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (!migrateMode) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(
                            text = "不可撤销地清除该账本下全部流水与预算，并精准回滚对应账户余额",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (migrateMode && canMigrate && targetLedgerId > 0L) {
                Button(onClick = { onConfirmDelete(targetLedgerId) }) {
                    Text("迁移并删除")
                }
            } else {
                Button(
                    onClick = { onConfirmDelete(null) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("彻底删除")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun LedgerTagChip(
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
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LedgerEditorDialog(
    editing: LedgerEntity?,
    pinnedCurrencies: List<String>,
    nextSortOrder: Int,
    onSave: (LedgerEntity) -> Unit,
    onDismiss: () -> Unit
) {
    val isEditing = editing != null
    var name by remember(editing?.id) { mutableStateOf(editing?.name ?: "") }
    var currencyCode by remember(editing?.id) {
        mutableStateOf(editing?.currencyCode ?: CurrencyCatalog.DEFAULT_BASE_CURRENCY)
    }
    var iconKey by remember(editing?.id) { mutableStateOf(editing?.icon ?: "menu_book") }
    var colorHex by remember(editing?.id) {
        mutableLongStateOf(editing?.colorHex ?: KairoIcons.selectableColorPalette.first())
    }
    var nameError by remember { mutableStateOf(false) }
    var showMoreCurrencies by remember { mutableStateOf(false) }

    val quickCurrencies = remember(pinnedCurrencies, currencyCode) {
        val base = pinnedCurrencies.ifEmpty { CurrencyCatalog.PINNED_CURRENCIES }
        if (base.any { it.equals(currencyCode, ignoreCase = true) }) base else base + currencyCode
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEditing) "编辑账本" else "新建账本") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        if (it.isNotBlank()) nameError = false
                    },
                    label = { Text("账本名称") },
                    placeholder = { Text("如：日本旅行、家庭") },
                    isError = nameError,
                    supportingText = if (nameError) {
                        { Text("请输入账本名称") }
                    } else null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "默认币种",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(quickCurrencies, key = { it }) { code ->
                        CurrencyBadge(
                            currencyCode = code,
                            selected = code.equals(currencyCode, ignoreCase = true),
                            onClick = { currencyCode = code }
                        )
                    }
                    item(key = "more_ledger_currencies") {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.clickable { showMoreCurrencies = true }
                        ) {
                            Text(
                                text = "更多...",
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }

                Text(
                    text = "账本图标",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                GroupedIconPicker(
                    groups = KairoIcons.ledgerIconGroups,
                    selectedKey = iconKey,
                    onSelect = { iconKey = it },
                    accentColor = Color(colorHex),
                    gridMaxHeight = 220.dp
                )

                Text(
                    text = "主题色",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    KairoIcons.selectableColorPalette.forEach { hex ->
                        val selected = colorHex == hex
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(Color(hex))
                                .then(
                                    if (selected) {
                                        Modifier.border(BorderStroke(2.5.dp, MaterialTheme.colorScheme.onSurface), CircleShape)
                                    } else {
                                        Modifier
                                    }
                                )
                                .clickable { colorHex = hex },
                            contentAlignment = Alignment.Center
                        ) {
                            if (selected) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = "已选颜色",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmed = name.trim()
                    if (trimmed.isEmpty()) {
                        nameError = true
                        return@Button
                    }
                    onSave(
                        LedgerEntity(
                            id = editing?.id ?: 0L,
                            name = trimmed,
                            currencyCode = currencyCode,
                            icon = iconKey,
                            colorHex = colorHex,
                            sortOrder = editing?.sortOrder ?: nextSortOrder,
                            archived = editing?.archived ?: false,
                            createdAt = editing?.createdAt ?: System.currentTimeMillis()
                        )
                    )
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

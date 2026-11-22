package com.kairoledger.app.ui.components

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
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.kairoledger.app.core.model.CurrencyCatalog
import com.kairoledger.app.data.db.LedgerEntity

private val ledgerIconChoices: List<Pair<String, String>> = listOf(
    "menu_book" to "账本",
    "account_balance_wallet" to "日常",
    "flight" to "旅行",
    "home" to "家庭",
    "work" to "工作",
    "savings" to "储蓄",
    "shopping_bag" to "购物",
    "favorite" to "专项"
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LedgerManageSheet(
    ledgers: List<LedgerEntity>,
    activeLedgerId: Long,
    pinnedCurrencies: List<String>,
    onSelectLedger: (Long) -> Unit,
    onSaveLedger: (LedgerEntity) -> Unit,
    onArchiveLedger: (Long, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var editingLedger by remember { mutableStateOf<LedgerEntity?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }

    val activeLedgers = remember(ledgers) {
        ledgers.filter { !it.archived }.sortedWith(compareBy({ it.sortOrder }, { it.id }))
    }
    val archivedLedgers = remember(ledgers) {
        ledgers.filter { it.archived }.sortedWith(compareBy({ it.sortOrder }, { it.id }))
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
                Column {
                    Text(
                        text = "多账本管理",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "点击切换当前账本，或新建独立场景账本",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
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
                                    text = "合并查看所有账本的收支流水与统计图表",
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
                    LedgerCardItem(
                        ledger = ledger,
                        selected = isSelected,
                        onSelect = {
                            onSelectLedger(ledger.id)
                            onDismiss()
                        },
                        onEdit = { editingLedger = ledger },
                        onToggleArchive = {
                            if (ledger.id != 1L) {
                                if (isSelected) {
                                    onSelectLedger(1L)
                                }
                                onArchiveLedger(ledger.id, true)
                            }
                        }
                    )
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
                            selected = ledger.id == activeLedgerId,
                            onSelect = {
                                onSelectLedger(ledger.id)
                                onDismiss()
                            },
                            onEdit = { editingLedger = ledger },
                            onToggleArchive = {
                                if (ledger.id != 1L) {
                                    onArchiveLedger(ledger.id, false)
                                }
                            }
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
}

@Composable
private fun LedgerCardItem(
    ledger: LedgerEntity,
    selected: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onToggleArchive: () -> Unit
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
                    if (ledger.id == 1L) {
                        LedgerTagChip(
                            text = "默认",
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
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
                    text = "默认币种：${currencyInfo.flag} ${currencyInfo.code} (${currencyInfo.nameZh})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(onClick = onEdit, modifier = Modifier.size(34.dp)) {
                Icon(
                    imageVector = Icons.Rounded.Edit,
                    contentDescription = "编辑账本",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
            if (ledger.id != 1L) {
                IconButton(onClick = onToggleArchive, modifier = Modifier.size(34.dp)) {
                    Icon(
                        imageVector = if (ledger.archived) Icons.Rounded.Unarchive else Icons.Rounded.Archive,
                        contentDescription = if (ledger.archived) "取消归档" else "归档账本",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
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
        mutableStateOf(editing?.colorHex ?: KairoIcons.selectableColorPalette.first())
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
                    label = { Text("账本名称（如 日本旅行、家庭共享）") },
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
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ledgerIconChoices.forEach { (key, label) ->
                        val selected = iconKey == key
                        Column(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { iconKey = key }
                                .padding(2.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            CategoryIconBadge(
                                iconKey = key,
                                colorHex = colorHex,
                                size = 36.dp,
                                iconSize = 18.dp,
                                selected = selected
                            )
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (selected) Color(colorHex) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

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

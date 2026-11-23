package com.kairoledger.app.ui.screens.categories

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
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
import com.kairoledger.app.data.db.TagEntity
import com.kairoledger.app.data.db.TagTextCodec
import com.kairoledger.app.data.db.TransactionEntity
import com.kairoledger.app.ui.components.ConfirmActionDialog
import com.kairoledger.app.ui.components.EmptyStateCard
import com.kairoledger.app.ui.components.KairoIcons
import com.kairoledger.app.ui.components.YimuCard

private val TAG_PRESET_GROUPS = listOf(
    "渠道平台",
    "场景行程",
    "消费属性",
    "对象账务",
    "自定义"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagManageScreen(
    tags: List<TagEntity>,
    transactions: List<TransactionEntity>,
    onSaveTag: (TagEntity) -> Unit,
    onDeleteTag: (Long) -> Unit,
    onSeedPresets: () -> Unit,
    onBack: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var showEditor by remember { mutableStateOf(false) }
    var editingTag by remember { mutableStateOf<TagEntity?>(null) }
    var deleteCandidate by remember { mutableStateOf<TagEntity?>(null) }

    val txCountByTag = remember(transactions) {
        val counts = HashMap<String, Int>()
        for (tx in transactions) {
            for (tagName in TagTextCodec.parse(tx.tags)) {
                counts[tagName] = (counts[tagName] ?: 0) + 1
            }
        }
        counts
    }

    val normalizedQuery = remember(searchQuery) { searchQuery.trim().lowercase() }
    val filteredTags = remember(tags, normalizedQuery) {
        val sorted = tags.sortedWith(compareBy({ it.sortOrder }, { it.id }))
        if (normalizedQuery.isEmpty()) {
            sorted
        } else {
            sorted.filter {
                it.name.lowercase().contains(normalizedQuery) ||
                    it.groupName.lowercase().contains(normalizedQuery)
            }
        }
    }

    val groupedTags = remember(filteredTags) {
        val byGroup = filteredTags.groupBy { it.groupName.ifBlank { "自定义" } }
        val ordered = mutableListOf<Pair<String, List<TagEntity>>>()
        for (group in TAG_PRESET_GROUPS) {
            val list = byGroup[group]
            if (!list.isNullOrEmpty()) {
                ordered.add(group to list)
            }
        }
        for ((group, list) in byGroup) {
            if (group !in TAG_PRESET_GROUPS && list.isNotEmpty()) {
                ordered.add(group to list)
            }
        }
        ordered
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("账单标签管理", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(onClick = onSeedPresets) {
                        Text("补充预设")
                    }
                    IconButton(
                        onClick = {
                            editingTag = null
                            showEditor = true
                        }
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = "新建标签")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    editingTag = null
                    showEditor = true
                },
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("新建标签", fontWeight = FontWeight.SemiBold)
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("搜索标签名称或分组...") },
                leadingIcon = {
                    Icon(Icons.Rounded.Search, contentDescription = null)
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Rounded.Close, contentDescription = "清除搜索")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp)
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (groupedTags.isEmpty()) {
                    item(key = "empty_tags") {
                        if (normalizedQuery.isNotEmpty()) {
                            EmptyStateCard(
                                icon = Icons.Rounded.Search,
                                title = "未找到匹配「${searchQuery.trim()}」的标签",
                                subtitle = "可清空搜索关键词查看全部标签，或点击「新建标签」创建。",
                                actionLabel = "清除搜索",
                                onAction = { searchQuery = "" }
                            )
                        } else {
                            EmptyStateCard(
                                icon = Icons.AutoMirrored.Rounded.Label,
                                title = "暂无账单标签",
                                subtitle = "可点击右上角「补充预设」一键导入 28 个预置标签，或新建自定义标签。",
                                actionLabel = "补充预设标签",
                                onAction = onSeedPresets
                            )
                        }
                    }
                } else {
                    items(groupedTags, key = { it.first }) { (groupName, groupTags) ->
                        TagGroupCard(
                            groupName = groupName,
                            tags = groupTags,
                            txCountByTag = txCountByTag,
                            onEditTag = { tag ->
                                editingTag = tag
                                showEditor = true
                            },
                            onDeleteTag = { tag ->
                                deleteCandidate = tag
                            }
                        )
                    }
                }
            }
        }
    }

    if (showEditor) {
        val nextSortOrder = remember(tags) {
            (tags.maxOfOrNull { it.sortOrder } ?: 280) + 10
        }
        TagEditorDialog(
            editing = editingTag,
            defaultSortOrder = nextSortOrder,
            onSave = { saved ->
                onSaveTag(saved)
                showEditor = false
                editingTag = null
            },
            onDismiss = {
                showEditor = false
                editingTag = null
            }
        )
    }

    deleteCandidate?.let { tag ->
        val usedCount = txCountByTag[tag.name] ?: 0
        val usageHint = if (usedCount > 0) "当前有 $usedCount 笔历史账单使用该标签，删除标签不会丢失历史账单文本。" else ""
        ConfirmActionDialog(
            title = "删除标签「${tag.name}」？",
            message = "确认删除这个自定义标签吗？$usageHint",
            confirmText = "确认删除",
            isDestructive = true,
            onConfirm = {
                onDeleteTag(tag.id)
                deleteCandidate = null
            },
            onDismiss = { deleteCandidate = null }
        )
    }
}

@Composable
private fun TagGroupCard(
    groupName: String,
    tags: List<TagEntity>,
    txCountByTag: Map<String, Int>,
    onEditTag: (TagEntity) -> Unit,
    onDeleteTag: (TagEntity) -> Unit
) {
    YimuCard(cornerRadius = 18.dp) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = groupName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "${tags.size} 个标签",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 14.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            )
            tags.forEachIndexed { idx, tag ->
                if (idx > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 38.dp, end = 14.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                    )
                }
                TagRow(
                    tag = tag,
                    txCount = txCountByTag[tag.name] ?: 0,
                    onEdit = { onEditTag(tag) },
                    onDelete = { onDeleteTag(tag) }
                )
            }
        }
    }
}

@Composable
private fun TagRow(
    tag: TagEntity,
    txCount: Int,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val tagColor = Color(tag.colorHex)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = CircleShape,
            color = tagColor.copy(alpha = 0.16f),
            border = BorderStroke(1.dp, tagColor.copy(alpha = 0.35f)),
            modifier = Modifier.size(30.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(11.dp)
                        .clip(CircleShape)
                        .background(tagColor)
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
                    text = "#${tag.name}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (tag.isSystem) {
                    Surface(
                        shape = RoundedCornerShape(5.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Text(
                            text = "系统",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                }
            }
            Text(
                text = "关联 $txCount 笔账单",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
            Icon(
                imageVector = Icons.Rounded.Edit,
                contentDescription = "编辑标签",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }
        if (!tag.isSystem) {
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Rounded.Delete,
                    contentDescription = "删除标签",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagEditorDialog(
    editing: TagEntity?,
    defaultSortOrder: Int,
    onSave: (TagEntity) -> Unit,
    onDismiss: () -> Unit
) {
    val isEditing = editing != null
    var name by remember(editing?.id) { mutableStateOf(editing?.name ?: "") }
    var selectedGroup by remember(editing?.id) {
        mutableStateOf(
            editing?.groupName?.takeIf { it.isNotBlank() } ?: "自定义"
        )
    }
    var selectedColor by remember(editing?.id) {
        mutableStateOf(editing?.colorHex ?: KairoIcons.selectableColorPalette.first())
    }
    var nameError by remember { mutableStateOf(false) }

    val groupChoices = remember(selectedGroup) {
        if (selectedGroup in TAG_PRESET_GROUPS) TAG_PRESET_GROUPS else TAG_PRESET_GROUPS + selectedGroup
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (isEditing) "编辑标签" else "新建标签")
        },
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
                    label = { Text("标签名称") },
                    placeholder = { Text("例如：咖啡续命、机场免税") },
                    isError = nameError,
                    supportingText = if (nameError) {
                        { Text("请输入标签名称") }
                    } else null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "所属分组",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    groupChoices.forEach { group ->
                        FilterChip(
                            selected = selectedGroup == group,
                            onClick = { selectedGroup = group },
                            label = { Text(group) }
                        )
                    }
                }

                Text(
                    text = "颜色选择（${KairoIcons.selectableColorPalette.size} 色）",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    KairoIcons.selectableColorPalette.forEach { hex ->
                        val isSelected = selectedColor == hex
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(hex))
                                .then(
                                    if (isSelected) {
                                        Modifier.border(
                                            BorderStroke(2.5.dp, MaterialTheme.colorScheme.onSurface),
                                            CircleShape
                                        )
                                    } else {
                                        Modifier
                                    }
                                )
                                .clickable { selectedColor = hex },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
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
                        TagEntity(
                            id = editing?.id ?: 0L,
                            name = trimmed,
                            groupName = selectedGroup,
                            colorHex = selectedColor,
                            sortOrder = editing?.sortOrder ?: defaultSortOrder,
                            isSystem = editing?.isSystem ?: false
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
}

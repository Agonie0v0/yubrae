package com.yubrae.app.ui.screens.categories

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
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SubdirectoryArrowRight
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
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yubrae.app.data.db.CategoryEntity
import com.yubrae.app.data.db.TransactionType
import com.yubrae.app.ui.components.CategoryIconBadge
import com.yubrae.app.ui.components.ConfirmActionDialog
import com.yubrae.app.ui.components.EmptyStateCard
import com.yubrae.app.ui.components.GroupedIconPicker
import com.yubrae.app.ui.components.KairoIcons
import com.yubrae.app.ui.components.YimuCard

private data class CategoryEditorTarget(
    val editing: CategoryEntity? = null,
    val presetParentId: Long? = null
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CategoryManageScreen(
    categories: List<CategoryEntity>,
    onSaveCategory: (CategoryEntity) -> Unit,
    onDeleteCategory: (Long) -> Unit,
    onSeedPresets: (() -> Unit)? = null,
    onBack: () -> Unit
) {
    var selectedType by remember { mutableStateOf(TransactionType.EXPENSE) }
    var searchQuery by remember { mutableStateOf("") }
    var editorTarget by remember { mutableStateOf<CategoryEditorTarget?>(null) }
    var deleteCandidate by remember { mutableStateOf<CategoryEntity?>(null) }

    val typedCategories = remember(categories, selectedType) {
        categories.filter { it.type == selectedType }
            .sortedWith(compareBy({ it.sortOrder }, { it.id }))
    }
    val parentCategories = remember(typedCategories) {
        typedCategories.filter { it.parentId == null }
    }
    val childrenByParent = remember(typedCategories) {
        typedCategories.filter { it.parentId != null }.groupBy { it.parentId!! }
    }
    val parentIds = remember(parentCategories) {
        parentCategories.map { it.id }.toSet()
    }
    val orphanChildren = remember(typedCategories, parentIds) {
        typedCategories.filter { it.parentId != null && it.parentId !in parentIds }
    }
    val normalizedQuery = remember(searchQuery) { searchQuery.trim().lowercase() }
    val visibleParentEntries = remember(parentCategories, childrenByParent, normalizedQuery) {
        if (normalizedQuery.isEmpty()) {
            parentCategories.map { it to childrenByParent[it.id].orEmpty() }
        } else {
            parentCategories.mapNotNull { parent ->
                val allChildren = childrenByParent[parent.id].orEmpty()
                val parentMatches = parent.name.lowercase().contains(normalizedQuery)
                val matchedChildren = allChildren.filter { it.name.lowercase().contains(normalizedQuery) }
                when {
                    parentMatches -> parent to allChildren
                    matchedChildren.isNotEmpty() -> parent to matchedChildren
                    else -> null
                }
            }
        }
    }
    val visibleOrphanChildren = remember(orphanChildren, normalizedQuery) {
        if (normalizedQuery.isEmpty()) {
            orphanChildren
        } else {
            orphanChildren.filter { it.name.lowercase().contains(normalizedQuery) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("分类管理", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (onSeedPresets != null) {
                        TextButton(onClick = onSeedPresets) {
                            Text("导入预设")
                        }
                    }
                    IconButton(onClick = { editorTarget = CategoryEditorTarget() }) {
                        Icon(Icons.Rounded.Add, contentDescription = "新增分类")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { editorTarget = CategoryEditorTarget() },
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("新建分类", fontWeight = FontWeight.SemiBold)
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val selectedTabIndex = if (selectedType == TransactionType.EXPENSE) 0 else 1
            TabRow(selectedTabIndex = selectedTabIndex) {
                Tab(
                    selected = selectedTabIndex == 0,
                    onClick = { selectedType = TransactionType.EXPENSE },
                    text = { Text("支出分类") }
                )
                Tab(
                    selected = selectedTabIndex == 1,
                    onClick = { selectedType = TransactionType.INCOME },
                    text = { Text("收入分类") }
                )
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("搜索分类") },
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
                    .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 2.dp)
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (visibleParentEntries.isEmpty() && visibleOrphanChildren.isEmpty()) {
                    item(key = "empty_categories") {
                        if (normalizedQuery.isNotEmpty()) {
                            EmptyStateCard(
                                icon = Icons.Rounded.Search,
                                title = "未找到匹配分类",
                                actionLabel = "清除搜索",
                                onAction = { searchQuery = "" }
                            )
                        } else {
                            EmptyStateCard(
                                icon = Icons.Rounded.Category,
                                title = "暂无${if (selectedType == TransactionType.EXPENSE) "支出" else "收入"}分类",
                                actionLabel = "新建分类",
                                onAction = { editorTarget = CategoryEditorTarget() }
                            )
                        }
                    }
                } else {
                    items(visibleParentEntries, key = { it.first.id }) { (parent, children) ->
                        CategoryTreeGroupCard(
                            parent = parent,
                            children = children,
                            onAddSubcategory = {
                                editorTarget = CategoryEditorTarget(editing = null, presetParentId = parent.id)
                            },
                            onEditCategory = { cat ->
                                editorTarget = CategoryEditorTarget(editing = cat, presetParentId = cat.parentId)
                            },
                            onDeleteCategory = { cat ->
                                deleteCandidate = cat
                            }
                        )
                    }

                    if (visibleOrphanChildren.isNotEmpty()) {
                        item(key = "orphan_categories") {
                            YimuCard(cornerRadius = 18.dp) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text(
                                        text = "未归属一级分类的子分类",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    visibleOrphanChildren.forEachIndexed { idx, child ->
                                        if (idx > 0) {
                                            HorizontalDivider(
                                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                                            )
                                        }
                                        CategoryRow(
                                            category = child,
                                            isSubcategory = true,
                                            onEdit = {
                                                editorTarget = CategoryEditorTarget(
                                                    editing = child,
                                                    presetParentId = child.parentId
                                                )
                                            },
                                            onDelete = { deleteCandidate = child }
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

    editorTarget?.let { target ->
        CategoryEditorDialog(
            type = selectedType,
            editing = target.editing,
            presetParentId = target.presetParentId,
            parentCandidates = parentCategories.filter { it.id != target.editing?.id },
            onSave = { saved ->
                onSaveCategory(saved)
                editorTarget = null
            },
            onDismiss = { editorTarget = null }
        )
    }

    deleteCandidate?.let { cat ->
        val childCount = childrenByParent[cat.id]?.size ?: 0
        val message = if (childCount > 0) {
            "含 $childCount 个子分类，删除后历史流水不受影响。"
        } else {
            "删除后历史流水不受影响。"
        }
        ConfirmActionDialog(
            title = "删除分类「${cat.name}」？",
            message = message,
            confirmText = "确认删除",
            isDestructive = true,
            onConfirm = {
                onDeleteCategory(cat.id)
                deleteCandidate = null
            },
            onDismiss = { deleteCandidate = null }
        )
    }
}

@Composable
private fun CategoryTreeGroupCard(
    parent: CategoryEntity,
    children: List<CategoryEntity>,
    onAddSubcategory: () -> Unit,
    onEditCategory: (CategoryEntity) -> Unit,
    onDeleteCategory: (CategoryEntity) -> Unit
) {
    YimuCard(cornerRadius = 18.dp) {
        Column(modifier = Modifier.padding(vertical = 6.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onEditCategory(parent) }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CategoryIconBadge(
                    iconKey = parent.icon,
                    colorHex = parent.colorHex,
                    size = 40.dp,
                    iconSize = 20.dp
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = parent.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (parent.isSystem) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
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
                        text = if (children.isEmpty()) "排序 ${parent.sortOrder}" else "${children.size} 个子分类 · 排序 ${parent.sortOrder}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(onClick = onAddSubcategory, modifier = Modifier.size(34.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = "新增二级分类",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(onClick = { onEditCategory(parent) }, modifier = Modifier.size(34.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = "编辑分类",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
                if (!parent.isSystem) {
                    IconButton(onClick = { onDeleteCategory(parent) }, modifier = Modifier.size(34.dp)) {
                        Icon(
                            imageVector = Icons.Rounded.Delete,
                            contentDescription = "删除分类",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            if (children.isNotEmpty()) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 14.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                )
                children.forEachIndexed { idx, sub ->
                    if (idx > 0) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 48.dp, end = 14.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                        )
                    }
                    CategoryRow(
                        category = sub,
                        isSubcategory = true,
                        onEdit = { onEditCategory(sub) },
                        onDelete = { onDeleteCategory(sub) }
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryRow(
    category: CategoryEntity,
    isSubcategory: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
            .padding(
                start = if (isSubcategory) 20.dp else 14.dp,
                end = 14.dp,
                top = 8.dp,
                bottom = 8.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isSubcategory) {
            Icon(
                imageVector = Icons.Rounded.SubdirectoryArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
        }
        CategoryIconBadge(
            iconKey = category.icon,
            colorHex = category.colorHex,
            size = 32.dp,
            iconSize = 16.dp
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = category.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (category.isSystem) {
                    Surface(
                        shape = RoundedCornerShape(5.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Text(
                            text = "系统",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
            }
            Text(
                text = "排序 ${category.sortOrder}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
            Icon(
                imageVector = Icons.Rounded.Edit,
                contentDescription = "编辑",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }
        if (!category.isSystem) {
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Rounded.Delete,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryEditorDialog(
    type: String,
    editing: CategoryEntity?,
    presetParentId: Long?,
    parentCandidates: List<CategoryEntity>,
    onSave: (CategoryEntity) -> Unit,
    onDismiss: () -> Unit
) {
    val isEditing = editing != null
    var name by remember(editing?.id, presetParentId) { mutableStateOf(editing?.name ?: "") }
    var selectedParentId by remember(editing?.id, presetParentId) {
        mutableStateOf(editing?.parentId ?: presetParentId)
    }
    var selectedIcon by remember(editing?.id, presetParentId) {
        mutableStateOf(
            editing?.icon
                ?: KairoIcons.selectableCategoryIcons.first().first
        )
    }
    var selectedColor by remember(editing?.id, presetParentId) {
        val parentColor = presetParentId?.let { pid ->
            parentCandidates.firstOrNull { it.id == pid }?.colorHex
        }
        mutableLongStateOf(
            editing?.colorHex
                ?: parentColor
                ?: KairoIcons.selectableColorPalette.first()
        )
    }
    var sortOrderText by remember(editing?.id, presetParentId) {
        mutableStateOf((editing?.sortOrder ?: 50).toString())
    }
    var nameError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = when {
                    isEditing -> "编辑${if (type == TransactionType.EXPENSE) "支出" else "收入"}分类"
                    selectedParentId != null -> "新建二级分类"
                    else -> "新建一级分类"
                }
            )
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
                    label = { Text("分类名称") },
                    isError = nameError,
                    supportingText = if (nameError) {
                        { Text("请输入分类名称") }
                    } else null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "所属父级",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    item(key = "as_root") {
                        FilterChip(
                            selected = selectedParentId == null,
                            onClick = { selectedParentId = null },
                            label = { Text("作为一级分类") }
                        )
                    }
                    items(parentCandidates, key = { it.id }) { parent ->
                        FilterChip(
                            selected = selectedParentId == parent.id,
                            onClick = { selectedParentId = parent.id },
                            leadingIcon = {
                                Icon(
                                    imageVector = KairoIcons.iconFor(parent.icon),
                                    contentDescription = null,
                                    tint = Color(parent.colorHex),
                                    modifier = Modifier.size(15.dp)
                                )
                            },
                            label = { Text(parent.name) }
                        )
                    }
                }

                Text(
                    text = "图标",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                GroupedIconPicker(
                    groups = KairoIcons.categoryIconGroups,
                    selectedKey = selectedIcon,
                    onSelect = { selectedIcon = it },
                    accentColor = Color(selectedColor),
                    gridMaxHeight = 230.dp,
                    useCartoonCategoryIcons = true
                )

                Text(
                    text = "颜色",
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
                                        Modifier.border(BorderStroke(2.5.dp, MaterialTheme.colorScheme.onSurface), CircleShape)
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

                OutlinedTextField(
                    value = sortOrderText,
                    onValueChange = { sortOrderText = it.filter { ch -> ch.isDigit() || ch == '-' } },
                    label = { Text("排序值") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
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
                    val order = sortOrderText.toIntOrNull() ?: (editing?.sortOrder ?: 50)
                    onSave(
                        CategoryEntity(
                            id = editing?.id ?: 0L,
                            parentId = selectedParentId,
                            name = trimmed,
                            type = editing?.type ?: type,
                            icon = selectedIcon,
                            colorHex = selectedColor,
                            sortOrder = order,
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

package com.yubrae.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.KeyboardHide
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yubrae.app.core.model.CurrencyCatalog
import com.yubrae.app.core.model.MoneyAmount
import com.yubrae.app.data.db.AccountEntity
import com.yubrae.app.data.db.AccountGroup
import com.yubrae.app.data.db.CategoryEntity
import com.yubrae.app.data.db.TagEntity
import com.yubrae.app.data.db.TransactionEntity
import com.yubrae.app.data.db.TransactionType
import com.yubrae.app.domain.RecordUsageRanker
import com.yubrae.app.domain.TagDisplayItem
import com.yubrae.app.ui.theme.tabular
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val QUICK_CATEGORY_ICON_KEYS: List<String> = listOf(
    "restaurant",
    "delivery_dining",
    "local_cafe",
    "shopping_bag",
    "directions_car",
    "subway",
    "home",
    "sports_esports",
    "subscriptions",
    "medical_services",
    "flight",
    "school",
    "card_giftcard",
    "pets",
    "work",
    "trending_up"
)

private val QUICK_TAG_ICON_KEYS: List<String> = listOf(
    "",
    "delivery_dining",
    "shopping_cart",
    "storefront",
    "subway",
    "flight",
    "celebration",
    "verified",
    "subscriptions",
    "local_offer",
    "receipt_long",
    "person",
    "favorite",
    "pets",
    "diamond"
)

private val SMART_TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm")

internal fun formatSmartDateTimeChip(epochMillis: Long): String {
    val zone = ZoneId.systemDefault()
    val zdt = Instant.ofEpochMilli(epochMillis).atZone(zone)
    val date = zdt.toLocalDate()
    val today = LocalDate.now(zone)
    val timeStr = zdt.format(SMART_TIME_FORMATTER)
    val datePrefix = when (date) {
        today -> "今天"
        today.minusDays(1) -> "昨天"
        today.minusDays(2) -> "前天"
        else -> if (date.year == today.year) {
            "${date.monthValue}/${date.dayOfMonth}"
        } else {
            "${date.year % 100}/${date.monthValue}/${date.dayOfMonth}"
        }
    }
    return "$datePrefix $timeStr"
}

/**
 * 纯内嵌分类浏览器（严禁 Dialog / DropdownMenu，可供 QuickRecordSheet 与 FloatingConfirmService 安全复用）：
 * - 实时搜索分类名/父级名，无精确匹配时一键带入新建；
 * - 常用分类横向快捷行（直接推荐二级细分分类，1-tap 选中）；
 * - 5 列多色卡通图标网格 + 紧贴选中行的二级子分类展开面板（含 + 子类）；
 * - 内联快速新建一/二级分类并自动选中。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CategoryExplorerPanel(
    txType: String,
    categories: List<CategoryEntity>,
    selectedCategoryId: Long?,
    recentTransactions: List<TransactionEntity>,
    onSelectCategory: (Long) -> Unit,
    onCreateCategory: suspend (CategoryEntity) -> Long?,
    onOpenCategoryManager: (() -> Unit)? = null,
    onRequestImeFocus: () -> Unit = {},
    onReleaseImeFocus: () -> Unit = {},
    compactMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()

    var searchQuery by remember(txType) { mutableStateOf("") }
    var isCreating by remember(txType) { mutableStateOf(false) }
    var createPresetName by remember(txType) { mutableStateOf("") }
    var createPresetParentId by remember(txType) { mutableStateOf<Long?>(null) }

    val typeCats = remember(categories, txType) {
        categories.filter { it.type.equals(txType, ignoreCase = true) }
            .sortedWith(compareBy<CategoryEntity> { it.sortOrder }.thenBy { it.id })
    }
    val parents = remember(typeCats) { typeCats.filter { it.parentId == null } }
    val childrenByParent = remember(typeCats) {
        typeCats.filter { it.parentId != null }.groupBy { it.parentId!! }
    }
    val frequentCats = remember(txType, typeCats, recentTransactions) {
        RecordUsageRanker.rankFrequentCategories(
            type = txType,
            categories = typeCats,
            transactions = recentTransactions,
            limit = 8
        )
    }

    val openInlineCreate: (String, Long?) -> Unit = { presetName, presetParentId ->
        searchQuery = ""
        focusManager.clearFocus()
        createPresetName = presetName.trim()
        createPresetParentId = presetParentId
        isCreating = true
    }

    AnimatedContent(
        targetState = isCreating,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "category_explorer_mode",
        modifier = modifier.fillMaxWidth()
    ) { creating ->
        if (creating) {
            QuickCreateCategoryInline(
                txType = txType,
                parents = parents,
                existingTypeCategories = typeCats,
                initialName = createPresetName,
                initialParentId = createPresetParentId,
                onBack = {
                    focusManager.clearFocus()
                    onReleaseImeFocus()
                    isCreating = false
                },
                onCreate = { newEntity ->
                    val savedId = onCreateCategory(newEntity)
                    if (savedId != null && savedId > 0L) {
                        onSelectCategory(savedId)
                    }
                    focusManager.clearFocus()
                    onReleaseImeFocus()
                    isCreating = false
                },
                onRequestImeFocus = onRequestImeFocus,
                onReleaseImeFocus = onReleaseImeFocus,
                compactMode = compactMode
            )
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(if (compactMode) 6.dp else 8.dp)
            ) {
                // 顶部搜索与新建入口栏
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    InlineSearchInputBar(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        placeholder = "搜索分类（如 外卖 / 地铁）",
                        onFocusGained = onRequestImeFocus,
                        onFocusLost = onReleaseImeFocus,
                        onDone = {
                            focusManager.clearFocus()
                            onReleaseImeFocus()
                        },
                        height = if (compactMode) 32.dp else 35.dp,
                        modifier = Modifier.weight(1f)
                    )

                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
                        border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)),
                        modifier = Modifier
                            .height(if (compactMode) 32.dp else 35.dp)
                            .clip(CircleShape)
                            .clickable { openInlineCreate("", null) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = "新建分类",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = "新建",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }

                    if (onOpenCategoryManager != null) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.75f),
                            modifier = Modifier
                                .size(if (compactMode) 32.dp else 35.dp)
                                .clip(CircleShape)
                                .clickable(onClick = onOpenCategoryManager)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.Tune,
                                    contentDescription = "分类管理",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                val trimmedQuery = searchQuery.trim()
                if (trimmedQuery.isNotEmpty()) {
                    val results = remember(trimmedQuery, txType, typeCats) {
                        RecordUsageRanker.searchCategories(trimmedQuery, txType, typeCats)
                    }
                    val exactExists = remember(trimmedQuery, txType, typeCats) {
                        RecordUsageRanker.hasExactCategoryName(trimmedQuery, txType, typeCats)
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (!exactExists) {
                            InlineQuickCreateBanner(
                                title = "新建「$trimmedQuery」分类",
                                subtitle = "一键创建并自动选中",
                                onClick = { openInlineCreate(trimmedQuery, null) }
                            )
                        }
                        if (results.isEmpty()) {
                            Text(
                                text = "未找到「$trimmedQuery」，点击上方卡片即可直接新建",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 10.dp)
                            )
                        } else {
                            results.forEach { item ->
                                val isSelected = selectedCategoryId == item.category.id
                                val catColor = Color(item.category.colorHex)
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isSelected) {
                                        catColor.copy(alpha = 0.13f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerLow
                                    },
                                    border = if (isSelected) {
                                        BorderStroke(1.dp, catColor.copy(alpha = 0.7f))
                                    } else {
                                        BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            focusManager.clearFocus()
                                            onReleaseImeFocus()
                                            searchQuery = ""
                                            onSelectCategory(item.category.id)
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        CartoonCategoryIcon(
                                            iconKey = item.category.icon,
                                            size = 28.dp,
                                            fallbackColorHex = item.category.colorHex
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = item.category.name,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isSelected) catColor else MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = item.parent?.let { "隶属于 ${it.name}" } ?: "一级分类",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Rounded.Check,
                                                contentDescription = "已选中",
                                                tint = catColor,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    val selectedCatObj = remember(typeCats, selectedCategoryId) {
                        typeCats.firstOrNull { it.id == selectedCategoryId }
                    }
                    val activeParentId = selectedCatObj?.parentId ?: selectedCatObj?.id
                    val subCategories = remember(childrenByParent, activeParentId) {
                        if (activeParentId != null) childrenByParent[activeParentId].orEmpty() else emptyList()
                    }
                    val activeParent = remember(parents, activeParentId) {
                        parents.firstOrNull { it.id == activeParentId }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(if (compactMode) 6.dp else 8.dp)
                    ) {
                        // 1. 常用分类横向快捷行（含高频二级分类直达）
                        if (frequentCats.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "常用直达",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Medium
                                )
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    items(frequentCats, key = { "freq_${it.id}" }) { cat ->
                                        val isSelected = selectedCategoryId == cat.id
                                        val catColor = Color(cat.colorHex)
                                        Surface(
                                            shape = CircleShape,
                                            color = if (isSelected) {
                                                catColor.copy(alpha = 0.16f)
                                            } else {
                                                MaterialTheme.colorScheme.surfaceContainerLow
                                            },
                                            border = BorderStroke(
                                                width = if (isSelected) 1.dp else 0.5.dp,
                                                color = if (isSelected) {
                                                    catColor.copy(alpha = 0.85f)
                                                } else {
                                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                                                }
                                            ),
                                            modifier = Modifier
                                                .height(if (compactMode) 30.dp else 32.dp)
                                                .clip(CircleShape)
                                                .clickable { onSelectCategory(cat.id) }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 9.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                                            ) {
                                                CartoonCategoryIcon(
                                                    iconKey = cat.icon,
                                                    size = 18.dp,
                                                    fallbackColorHex = cat.colorHex
                                                )
                                                Text(
                                                    text = cat.name,
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = if (isSelected) catColor else MaterialTheme.colorScheme.onSurface,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 2. 全部分类 5 列卡通网格（网格末自带 + 新建格）
                        if (parents.isEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 18.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "暂无分类",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                InlineQuickCreateBanner(
                                    title = "新建首个分类",
                                    subtitle = "无需跳出当前记账弹窗",
                                    onClick = { openInlineCreate("", null) }
                                )
                            }
                        } else {
                            val gridSlots: List<CategoryEntity?> = remember(parents) {
                                parents.map<CategoryEntity, CategoryEntity?> { it } + null
                            }
                            val parentRows = remember(gridSlots) { gridSlots.chunked(5) }

                            Column(verticalArrangement = Arrangement.spacedBy(if (compactMode) 4.dp else 6.dp)) {
                                parentRows.forEach { rowSlots ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        rowSlots.forEach { slot ->
                                            if (slot == null) {
                                                AddCategoryGridCell(
                                                    compactMode = compactMode,
                                                    onClick = { openInlineCreate("", null) },
                                                    modifier = Modifier.weight(1f)
                                                )
                                            } else {
                                                val isParentActive = activeParentId == slot.id
                                                val hasChildren = !childrenByParent[slot.id].isNullOrEmpty()
                                                CategoryGridCellTile(
                                                    category = slot,
                                                    selected = isParentActive,
                                                    hasChildren = hasChildren,
                                                    compactMode = compactMode,
                                                    onClick = { onSelectCategory(slot.id) },
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                        }
                                        repeat(5 - rowSlots.size) {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }
                                    }

                                    // 选中含子类的大类时，紧贴该行下方展示清晰二级分类与「+ 子类」入口
                                    val rowContainsActiveParent = activeParentId != null &&
                                        rowSlots.any { it?.id == activeParentId }
                                    if (rowContainsActiveParent && activeParent != null && subCategories.isNotEmpty()) {
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                                            border = BorderStroke(
                                                0.6.dp,
                                                Color(activeParent.colorHex).copy(alpha = 0.28f)
                                            ),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                                verticalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text(
                                                        text = "${activeParent.name} · 细分",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = Color(activeParent.colorHex),
                                                        fontWeight = FontWeight.SemiBold,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier
                                                            .weight(1f, fill = false)
                                                            .padding(end = 8.dp)
                                                    )
                                                    Text(
                                                        text = if (selectedCategoryId == activeParent.id) "当前：大类默认" else "点击可切回大类",
                                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        maxLines = 1,
                                                        softWrap = false
                                                    )
                                                }
                                                FlowRow(
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    subCategories.forEach { sub ->
                                                        val isSubSelected = selectedCategoryId == sub.id
                                                        SubCategoryCartoonPill(
                                                            category = sub,
                                                            selected = isSubSelected,
                                                            onClick = {
                                                                onSelectCategory(if (isSubSelected) activeParent.id else sub.id)
                                                            }
                                                        )
                                                    }
                                                    Surface(
                                                        shape = CircleShape,
                                                        color = Color.Transparent,
                                                        border = BorderStroke(
                                                            0.7.dp,
                                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                                                        ),
                                                        modifier = Modifier
                                                            .clip(CircleShape)
                                                            .clickable { openInlineCreate("", activeParent.id) }
                                                    ) {
                                                        Row(
                                                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Rounded.Add,
                                                                contentDescription = null,
                                                                tint = MaterialTheme.colorScheme.primary,
                                                                modifier = Modifier.size(13.dp)
                                                            )
                                                            Text(
                                                                text = "子类",
                                                                style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.5.sp),
                                                                color = MaterialTheme.colorScheme.primary,
                                                                fontWeight = FontWeight.SemiBold
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
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryGridCellTile(
    category: CategoryEntity,
    selected: Boolean,
    hasChildren: Boolean,
    compactMode: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val catColor = Color(category.colorHex)
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(11.dp))
            .background(if (selected) catColor.copy(alpha = 0.10f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = if (compactMode) 4.dp else 5.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            CategoryIconBadge(
                iconKey = category.icon,
                colorHex = category.colorHex,
                size = if (compactMode) 34.dp else 38.dp,
                iconSize = if (compactMode) 17.dp else 19.dp,
                selected = selected
            )
            if (hasChildren) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(catColor.copy(alpha = 0.85f))
                        .border(1.dp, MaterialTheme.colorScheme.surface, CircleShape)
                )
            }
        }
        Text(
            text = category.name,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = if (compactMode) 10.5.sp else 11.sp,
                lineHeight = 13.sp
            ),
            color = if (selected) catColor else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun AddCategoryGridCell(
    compactMode: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val primary = MaterialTheme.colorScheme.primary
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(11.dp))
            .clickable(onClick = onClick)
            .padding(vertical = if (compactMode) 4.dp else 5.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = primary.copy(alpha = 0.10f),
            border = BorderStroke(0.9.dp, primary.copy(alpha = 0.35f)),
            modifier = Modifier.size(if (compactMode) 34.dp else 38.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = "新建分类",
                    tint = primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Text(
            text = "新建",
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = if (compactMode) 10.5.sp else 11.sp,
                lineHeight = 13.sp
            ),
            color = primary,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun SubCategoryCartoonPill(
    category: CategoryEntity,
    selected: Boolean,
    onClick: () -> Unit
) {
    val catColor = Color(category.colorHex)
    Surface(
        shape = CircleShape,
        color = if (selected) catColor.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceContainerHigh,
        border = if (selected) BorderStroke(1.dp, catColor.copy(alpha = 0.85f)) else null,
        modifier = Modifier
            .clip(CircleShape)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            CartoonCategoryIcon(
                iconKey = category.icon,
                size = 16.dp,
                fallbackColorHex = category.colorHex
            )
            Text(
                text = category.name,
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.5.sp),
                color = if (selected) catColor else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickCreateCategoryInline(
    txType: String,
    parents: List<CategoryEntity>,
    existingTypeCategories: List<CategoryEntity>,
    initialName: String,
    initialParentId: Long?,
    onBack: () -> Unit,
    onCreate: suspend (CategoryEntity) -> Unit,
    onRequestImeFocus: () -> Unit,
    onReleaseImeFocus: () -> Unit,
    compactMode: Boolean
) {
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    var name by remember(initialName) { mutableStateOf(initialName) }
    var selectedParentId by remember(initialParentId) { mutableStateOf(initialParentId) }
    val initialParent = remember(parents, initialParentId) {
        parents.firstOrNull { it.id == initialParentId }
    }
    val initialSuggested = remember(initialName, initialParent) {
        RecordUsageRanker.suggestCategoryStyle(initialName, initialParent)
    }
    var selectedIcon by remember(initialName, initialParentId) { mutableStateOf(initialSuggested.first) }
    var selectedColor by remember(initialName, initialParentId) { mutableLongStateOf(initialSuggested.second) }
    var hasManualIcon by remember(initialName, initialParentId) { mutableStateOf(false) }
    var hasManualColor by remember(initialName, initialParentId) { mutableStateOf(false) }
    var showAllIcons by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val typeLabel = if (txType == TransactionType.INCOME) "收入" else "支出"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(if (compactMode) 8.dp else 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onBack)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "返回分类列表",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Text(
                text = if (selectedParentId != null) "新建二级${typeLabel}分类" else "新建${typeLabel}分类",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            CategoryIconBadge(
                iconKey = selectedIcon,
                colorHex = selectedColor,
                size = 32.dp,
                iconSize = 17.dp,
                selected = true
            )
        }

        InlineSearchInputBar(
            query = name,
            onQueryChange = { incoming ->
                name = incoming
                errorMsg = null
                val parent = parents.firstOrNull { it.id == selectedParentId }
                val (suggestedIcon, suggestedColor) = RecordUsageRanker.suggestCategoryStyle(incoming, parent)
                if (!hasManualIcon) selectedIcon = suggestedIcon
                if (!hasManualColor) selectedColor = suggestedColor
            },
            placeholder = "输入分类名称（如：瑞幸咖啡 / 养车）",
            leadingIcon = null,
            onFocusGained = onRequestImeFocus,
            onFocusLost = onReleaseImeFocus,
            onDone = {
                focusManager.clearFocus()
                onReleaseImeFocus()
            },
            height = 38.dp
        )

        if (errorMsg != null) {
            Text(
                text = errorMsg!!,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "层级归属",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                item(key = "root_level") {
                    val isRoot = selectedParentId == null
                    FilterPillSmall(
                        label = "作为一级大类",
                        selected = isRoot,
                        accentColor = MaterialTheme.colorScheme.primary,
                        onClick = {
                            selectedParentId = null
                            if (!hasManualColor) {
                                selectedColor = RecordUsageRanker.suggestCategoryStyle(name, null).second
                            }
                        }
                    )
                }
                items(parents, key = { "p_${it.id}" }) { parent ->
                    val isSelected = selectedParentId == parent.id
                    FilterPillSmall(
                        label = parent.name,
                        selected = isSelected,
                        accentColor = Color(parent.colorHex),
                        leadingIconKey = parent.icon,
                        onClick = {
                            selectedParentId = parent.id
                            if (!hasManualColor) selectedColor = parent.colorHex
                            if (!hasManualIcon && name.isBlank()) selectedIcon = parent.icon
                        }
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "快捷图标",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { showAllIcons = !showAllIcons }
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = if (showAllIcons) "收起图标库" else "展开全部图标",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Icon(
                        imageVector = if (showAllIcons) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            if (showAllIcons) {
                GroupedIconPicker(
                    groups = KairoIcons.categoryIconGroups,
                    selectedKey = selectedIcon,
                    onSelect = { key ->
                        hasManualIcon = true
                        selectedIcon = key
                    },
                    accentColor = Color(selectedColor),
                    gridMaxHeight = if (compactMode) 150.dp else 180.dp,
                    useCartoonCategoryIcons = true
                )
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(QUICK_CATEGORY_ICON_KEYS, key = { it }) { iconKey ->
                        val isSelected = selectedIcon.equals(iconKey, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable {
                                    hasManualIcon = true
                                    selectedIcon = iconKey
                                }
                                .padding(2.dp)
                        ) {
                            CategoryIconBadge(
                                iconKey = iconKey,
                                colorHex = selectedColor,
                                size = 34.dp,
                                iconSize = 18.dp,
                                selected = isSelected
                            )
                        }
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "主题配色",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(KairoIcons.selectableColorPalette, key = { it }) { hex ->
                    val isSelected = selectedColor == hex
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(Color(hex))
                            .then(
                                if (isSelected) {
                                    Modifier.border(
                                        BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface),
                                        CircleShape
                                    )
                                } else {
                                    Modifier
                                }
                            )
                            .clickable {
                                hasManualColor = true
                                selectedColor = hex
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }
            }
        }

        Button(
            onClick = {
                val trimmed = name.trim()
                if (trimmed.isEmpty()) {
                    errorMsg = "请输入分类名称"
                    return@Button
                }
                val duplicate = existingTypeCategories.firstOrNull {
                    it.name.trim().equals(trimmed, ignoreCase = true) && it.parentId == selectedParentId
                }
                if (duplicate != null) {
                    errorMsg = "该层级已存在同名分类「${duplicate.name}」"
                    return@Button
                }
                if (!isSaving) {
                    isSaving = true
                    val maxOrder = existingTypeCategories.maxOfOrNull { it.sortOrder } ?: 100
                    coroutineScope.launch {
                        onCreate(
                            CategoryEntity(
                                parentId = selectedParentId,
                                name = trimmed,
                                type = txType,
                                icon = selectedIcon,
                                colorHex = selectedColor,
                                sortOrder = maxOrder + 10,
                                isSystem = false
                            )
                        )
                        isSaving = false
                    }
                }
            },
            enabled = !isSaving,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
        ) {
            if (isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(17.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("保存并选中", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/**
 * 纯内嵌分组标签选择器（严禁 Dialog / DropdownMenu，支持搜索即一键新建、分组筛选与常用排行）：
 * 彻底替代旧版 98dp 小缝隙标签框，提供渠道平台/场景行程/消费属性/对象账务/自定义的清晰归类视图。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GroupedTagPickerPanel(
    selectedTagNames: List<String>,
    availableTags: List<TagEntity>,
    allTransactions: List<TransactionEntity>,
    onToggleTag: (String) -> Unit,
    onClearTags: () -> Unit,
    onQuickCreateTag: suspend (name: String, groupName: String, colorHex: Long, icon: String) -> Unit,
    onDone: (() -> Unit)? = null,
    onRequestImeFocus: () -> Unit = {},
    onReleaseImeFocus: () -> Unit = {},
    compactMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()

    var searchQuery by remember { mutableStateOf("") }
    var activeGroup by remember { mutableStateOf(RecordUsageRanker.TAG_TAB_ALL) }
    var showCreateStyleDrawer by remember { mutableStateOf(false) }
    var newTagGroup by remember { mutableStateOf("自定义") }
    var newTagColor by remember { mutableLongStateOf(KairoIcons.selectableColorPalette.first()) }
    var newTagIcon by remember { mutableStateOf("") }
    var isCreating by remember { mutableStateOf(false) }

    val browserData = remember(searchQuery, activeGroup, availableTags, selectedTagNames, allTransactions) {
        RecordUsageRanker.buildTagBrowserData(
            query = searchQuery,
            selectedGroup = activeGroup,
            availableTags = availableTags,
            selectedTagNames = selectedTagNames,
            allTransactions = allTransactions
        )
    }

    val tagMetaMap = remember(availableTags) {
        availableTags.associateBy { it.name.trim() }
    }
    val selectedSet = remember(selectedTagNames) { selectedTagNames.toSet() }
    val trimmedQuery = searchQuery.trim()

    val triggerCreate: () -> Unit = {
        if (trimmedQuery.isNotEmpty() && !isCreating) {
            isCreating = true
            val resolvedColor = if (showCreateStyleDrawer) {
                newTagColor
            } else {
                KairoIcons.fallbackTagColorHex(trimmedQuery)
            }
            val resolvedGroup = if (showCreateStyleDrawer) newTagGroup else "自定义"
            val resolvedIcon = if (showCreateStyleDrawer) newTagIcon else ""
            coroutineScope.launch {
                onQuickCreateTag(trimmedQuery, resolvedGroup, resolvedColor, resolvedIcon)
                searchQuery = ""
                showCreateStyleDrawer = false
                isCreating = false
                focusManager.clearFocus()
                onReleaseImeFocus()
            }
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(if (compactMode) 6.dp else 8.dp)
    ) {
        // 顶栏标题 + 清空 / 完成
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "选择标签",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                if (selectedTagNames.isNotEmpty()) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "已选 ${selectedTagNames.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (selectedTagNames.isNotEmpty()) {
                    Text(
                        text = "清空",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable(onClick = onClearTags)
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
                if (onDone != null) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable {
                                focusManager.clearFocus()
                                onReleaseImeFocus()
                                onDone()
                            }
                    ) {
                        Text(
                            text = "完成",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }

        // 搜索或直接输入新建栏
        InlineSearchInputBar(
            query = searchQuery,
            onQueryChange = { incoming ->
                searchQuery = incoming
                if (incoming.isNotBlank()) {
                    newTagColor = KairoIcons.fallbackTagColorHex(incoming.trim())
                }
            },
            placeholder = "搜索标签，或直接输入新标签名",
            onFocusGained = onRequestImeFocus,
            onFocusLost = onReleaseImeFocus,
            onDone = {
                if (browserData.canQuickCreate) {
                    triggerCreate()
                } else {
                    focusManager.clearFocus()
                    onReleaseImeFocus()
                }
            },
            height = if (compactMode) 33.dp else 36.dp
        )

        // 搜索词尚不存在时：立即呈现高亮「一键新建并选中」卡片 + 可选样式展开
        AnimatedVisibility(visible = browserData.canQuickCreate) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                    border = BorderStroke(0.7.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !isCreating, onClick = triggerCreate)
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(newTagColor),
                            modifier = Modifier.size(22.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.Add,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "新建并选中 #$trimmedQuery",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "分组：$newTagGroup（点右侧可微调分组/颜色）",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable { showCreateStyleDrawer = !showCreateStyleDrawer }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = if (showCreateStyleDrawer) "收起" else "样式",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Icon(
                                    imageVector = if (showCreateStyleDrawer) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }
                    }
                }

                AnimatedVisibility(visible = showCreateStyleDrawer) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(RecordUsageRanker.CANONICAL_TAG_GROUPS, key = { it }) { grp ->
                                    FilterPillSmall(
                                        label = grp,
                                        selected = newTagGroup == grp,
                                        accentColor = MaterialTheme.colorScheme.primary,
                                        onClick = { newTagGroup = grp }
                                    )
                                }
                            }
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(KairoIcons.selectableColorPalette, key = { "tc_$it" }) { hex ->
                                    val selected = newTagColor == hex
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(Color(hex))
                                            .then(
                                                if (selected) {
                                                    Modifier.border(
                                                        BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface),
                                                        CircleShape
                                                    )
                                                } else Modifier
                                            )
                                            .clickable { newTagColor = hex },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (selected) {
                                            Icon(
                                                imageVector = Icons.Rounded.Check,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(12.dp)
                                            )
                                        }
                                    }
                                }
                            }
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(QUICK_TAG_ICON_KEYS, key = { "ti_$it" }) { key ->
                                    val selected = newTagIcon == key
                                    val iconVec = if (key.isEmpty()) null else KairoIcons.tagIcon(key)
                                    Surface(
                                        shape = CircleShape,
                                        color = if (selected) {
                                            Color(newTagColor).copy(alpha = 0.16f)
                                        } else {
                                            MaterialTheme.colorScheme.surfaceContainerHigh
                                        },
                                        border = if (selected) BorderStroke(1.dp, Color(newTagColor)) else null,
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .clickable { newTagIcon = key }
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            if (iconVec == null) {
                                                Text(
                                                    text = "无",
                                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            } else {
                                                Icon(
                                                    imageVector = iconVec,
                                                    contentDescription = null,
                                                    tint = Color(newTagColor),
                                                    modifier = Modifier.size(15.dp)
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
        }

        // 已选标签回显带（点一下直接取消）
        if (selectedTagNames.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(selectedTagNames, key = { "sel_$it" }) { tagName ->
                    val meta = tagMetaMap[tagName]
                    val colorHex = meta?.colorHex ?: RecordUsageRanker.stableFallbackColorHex(tagName)
                    val color = Color(colorHex)
                    Surface(
                        shape = RoundedCornerShape(7.dp),
                        color = color.copy(alpha = 0.16f),
                        border = BorderStroke(0.9.dp, color.copy(alpha = 0.75f)),
                        modifier = Modifier
                            .clip(RoundedCornerShape(7.dp))
                            .clickable { onToggleTag(tagName) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "#$tagName",
                                style = MaterialTheme.typography.labelSmall,
                                color = color,
                                fontWeight = FontWeight.Bold
                            )
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "移除",
                                tint = color,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
            }
        }

        // 分组筛选 Tab（全部 / 常用 / 渠道平台 / 场景行程 / 消费属性 / 对象账务 / 自定义）
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(browserData.groups, key = { "grp_$it" }) { group ->
                FilterPillSmall(
                    label = group,
                    selected = activeGroup == group,
                    accentColor = MaterialTheme.colorScheme.primary,
                    onClick = { activeGroup = group }
                )
            }
        }

        // 舒展流式标签展示区
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (browserData.sections.isEmpty()) {
                Text(
                    text = if (trimmedQuery.isNotEmpty()) {
                        "未找到匹配「$trimmedQuery」的标签，点击上方即可新建"
                    } else {
                        "当前分组暂无标签"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp)
                )
            } else {
                browserData.sections.forEach { section ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (activeGroup == RecordUsageRanker.TAG_TAB_ALL) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = section.groupName,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "${section.tags.size}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                        }
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(7.dp),
                            verticalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            section.tags.forEach { item: TagDisplayItem ->
                                val isSelected = item.name in selectedSet
                                TagBadgeChip(
                                    name = item.name,
                                    colorHex = item.colorHex,
                                    selected = isSelected,
                                    onClick = { onToggleTag(item.name) },
                                    iconKey = item.icon
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 纯内嵌按分组账户列表（使用品牌卡通账户图标，支持排除账户与快捷回调）。
 */
@Composable
internal fun GroupedAccountPickerPanel(
    accounts: List<AccountEntity>,
    selectedAccountId: Long?,
    onSelect: (AccountEntity) -> Unit,
    excludedAccountId: Long? = null,
    modifier: Modifier = Modifier
) {
    val visibleAccounts = remember(accounts, excludedAccountId) {
        accounts.filter { it.id != excludedAccountId }
            .sortedWith(compareBy<AccountEntity> { it.sortOrder }.thenBy { it.id })
    }
    val grouped = remember(visibleAccounts) {
        val byGroup = visibleAccounts.groupBy { it.groupType }
        val result = mutableListOf<Pair<String, List<AccountEntity>>>()
        AccountGroup.all.forEach { grp ->
            val list = byGroup[grp]
            if (!list.isNullOrEmpty()) {
                result.add(AccountGroup.labelZh(grp) to list)
            }
        }
        byGroup.forEach { (grp, list) ->
            if (grp !in AccountGroup.all && list.isNotEmpty()) {
                result.add(AccountGroup.labelZh(grp) to list)
            }
        }
        result
    }

    if (visibleAccounts.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "暂无可选账户",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        grouped.forEach { (groupLabel, groupAccounts) ->
            item(key = "acc_hdr_$groupLabel") {
                Text(
                    text = groupLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 4.dp, bottom = 2.dp, start = 2.dp)
                )
            }
            items(groupAccounts, key = { "acc_${it.id}" }) { acc ->
                val isSelected = acc.id == selectedAccountId
                val accColor = Color(acc.colorHex)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) {
                        accColor.copy(alpha = 0.12f)
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    },
                    border = if (isSelected) {
                        BorderStroke(1.dp, accColor.copy(alpha = 0.7f))
                    } else {
                        BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSelect(acc) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 11.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CartoonAccountIcon(
                            iconKey = acc.icon,
                            accountName = acc.name,
                            colorHex = acc.colorHex,
                            size = 32.dp
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = acc.name,
                                style = MaterialTheme.typography.titleSmall,
                                color = if (isSelected) accColor else MaterialTheme.colorScheme.onSurface,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${acc.currencyCode} · 余额 ${MoneyAmount.format(acc.balanceMicros, acc.currencyCode)}",
                                style = MaterialTheme.typography.bodySmall.tabular(),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = "已选中",
                                tint = accColor,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 纯内嵌币种选择器（适用于无 Dialog 权限的悬浮窗及需要快速切换的场景）。
 */
@Composable
internal fun CurrencyInlinePickerPanel(
    selectedCode: String,
    pinnedCodes: List<String>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val ordered = remember(pinnedCodes) { CurrencyCatalog.orderedCodes(pinnedCodes) }
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(ordered, key = { "cur_$it" }) { code ->
            val info = CurrencyCatalog.find(code)
            val isSelected = code.equals(selectedCode, ignoreCase = true)
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerLow
                },
                border = if (isSelected) {
                    BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f))
                } else {
                    BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onSelect(info.code) }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(text = info.flag, fontSize = 18.sp)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${info.code} · ${info.nameZh}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                        Text(
                            text = "符号 ${info.symbol} · 小数位 ${info.minorDigits}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = "已选中",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * 舒适触控 4 列数字计算器键盘（支持 + - × ÷ 四则算式与「再记一笔」双按钮模式）。
 */
@Composable
internal fun CompactCalculatorKeypad(
    hasOperator: Boolean,
    onDigit: (String) -> Unit,
    onDot: () -> Unit,
    onOperator: (Char) -> Unit,
    onEquals: () -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    onHideKeypad: (() -> Unit)?,
    onSave: () -> Unit,
    saveLabel: String = "完成",
    showSaveAndContinue: Boolean = false,
    onSaveAndContinue: (() -> Unit)? = null,
    keyHeight: Dp = 44.dp,
    modifier: Modifier = Modifier
) {
    val gap = 5.dp
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        val colWidth = (maxWidth - gap * 3) / 4
        val leftGroupWidth = colWidth * 3 + gap * 2

        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                CalcPadKey("清零", Modifier.width(colWidth), height = keyHeight, isSecondary = true, onClick = onClear)
                CalcPadKey("÷", Modifier.width(colWidth), height = keyHeight, isOperator = true) { onOperator('÷') }
                CalcPadKey("×", Modifier.width(colWidth), height = keyHeight, isOperator = true) { onOperator('×') }
                CalcPadIconKey(Icons.AutoMirrored.Rounded.Backspace, "退格", Modifier.width(colWidth), height = keyHeight, onClick = onBackspace)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                CalcPadKey("7", Modifier.width(colWidth), height = keyHeight) { onDigit("7") }
                CalcPadKey("8", Modifier.width(colWidth), height = keyHeight) { onDigit("8") }
                CalcPadKey("9", Modifier.width(colWidth), height = keyHeight) { onDigit("9") }
                CalcPadKey("-", Modifier.width(colWidth), height = keyHeight, isOperator = true) { onOperator('-') }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                CalcPadKey("4", Modifier.width(colWidth), height = keyHeight) { onDigit("4") }
                CalcPadKey("5", Modifier.width(colWidth), height = keyHeight) { onDigit("5") }
                CalcPadKey("6", Modifier.width(colWidth), height = keyHeight) { onDigit("6") }
                CalcPadKey("+", Modifier.width(colWidth), height = keyHeight, isOperator = true) { onOperator('+') }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                Column(
                    modifier = Modifier.width(leftGroupWidth),
                    verticalArrangement = Arrangement.spacedBy(gap)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        CalcPadKey("1", Modifier.width(colWidth), height = keyHeight) { onDigit("1") }
                        CalcPadKey("2", Modifier.width(colWidth), height = keyHeight) { onDigit("2") }
                        CalcPadKey("3", Modifier.width(colWidth), height = keyHeight) { onDigit("3") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        if (onHideKeypad != null) {
                            CalcPadIconKey(
                                Icons.Rounded.KeyboardHide,
                                "收起键盘",
                                Modifier.width(colWidth),
                                height = keyHeight,
                                onClick = onHideKeypad
                            )
                        } else {
                            CalcPadKey("00", Modifier.width(colWidth), height = keyHeight) { onDigit("00") }
                        }
                        CalcPadKey("0", Modifier.width(colWidth), height = keyHeight) { onDigit("0") }
                        CalcPadKey(".", Modifier.width(colWidth), height = keyHeight, onClick = onDot)
                    }
                }

                if (hasOperator) {
                    Button(
                        onClick = onEquals,
                        shape = RoundedCornerShape(11.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier
                            .width(colWidth)
                            .height(keyHeight * 2 + gap)
                    ) {
                        Text(
                            text = "=",
                            style = MaterialTheme.typography.headlineSmall.tabular(),
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                } else if (showSaveAndContinue && onSaveAndContinue != null) {
                    Column(
                        modifier = Modifier.width(colWidth),
                        verticalArrangement = Arrangement.spacedBy(gap)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(11.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(keyHeight)
                                .clip(RoundedCornerShape(11.dp))
                                .clickable(onClick = onSaveAndContinue)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "再记",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }
                        }
                        Button(
                            onClick = onSave,
                            shape = RoundedCornerShape(11.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(keyHeight)
                        ) {
                            Text(
                                text = saveLabel,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                    }
                } else {
                    Button(
                        onClick = onSave,
                        shape = RoundedCornerShape(11.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier
                            .width(colWidth)
                            .height(keyHeight * 2 + gap)
                    ) {
                        Text(
                            text = saveLabel,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CalcPadKey(
    label: String,
    modifier: Modifier = Modifier,
    height: Dp = 44.dp,
    isOperator: Boolean = false,
    isSecondary: Boolean = false,
    onClick: () -> Unit
) {
    val bg = when {
        isOperator -> MaterialTheme.colorScheme.secondaryContainer
        isSecondary -> MaterialTheme.colorScheme.surfaceContainerHigh
        else -> MaterialTheme.colorScheme.surfaceContainerLow
    }
    val fg = when {
        isOperator -> MaterialTheme.colorScheme.onSecondaryContainer
        isSecondary -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurface
    }
    Surface(
        shape = RoundedCornerShape(11.dp),
        color = bg,
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(11.dp))
            .clickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = if (isSecondary) {
                    MaterialTheme.typography.labelLarge
                } else {
                    MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp).tabular()
                },
                color = fg,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun CalcPadIconKey(
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    height: Dp = 44.dp,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(11.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(11.dp))
            .clickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(19.dp)
            )
        }
    }
}

@Composable
internal fun InlineSearchInputBar(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = Icons.Rounded.Search,
    onFocusGained: () -> Unit = {},
    onFocusLost: () -> Unit = {},
    onDone: () -> Unit = {},
    height: Dp = 35.dp
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = modifier.height(height)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(15.dp)
                )
            }
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart
            ) {
                if (query.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onDone() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { state ->
                            if (state.isFocused) onFocusGained() else onFocusLost()
                        }
                )
            }
            if (query.isNotEmpty()) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "清除",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(15.dp)
                        .clip(CircleShape)
                        .clickable { onQueryChange("") }
                )
            }
        }
    }
}

@Composable
private fun InlineQuickCreateBanner(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
        border = BorderStroke(0.7.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(26.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
private fun FilterPillSmall(
    label: String,
    selected: Boolean,
    accentColor: Color,
    leadingIconKey: String? = null,
    onClick: () -> Unit
) {
    Surface(
        shape = CircleShape,
        color = if (selected) {
            accentColor.copy(alpha = 0.14f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f)
        },
        border = if (selected) {
            BorderStroke(0.9.dp, accentColor.copy(alpha = 0.75f))
        } else {
            null
        },
        modifier = Modifier
            .clip(CircleShape)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (!leadingIconKey.isNullOrBlank()) {
                CartoonCategoryIcon(
                    iconKey = leadingIconKey,
                    size = 14.dp,
                    fallbackColorHex = 0xFF2AA876L
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.5.sp),
                color = if (selected) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1
            )
        }
    }
}

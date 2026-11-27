package com.yubrae.app.ui.screens.transactions

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CurrencyExchange
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yubrae.app.core.model.CurrencyCatalog
import com.yubrae.app.core.model.MoneyAmount
import com.yubrae.app.data.db.AccountEntity
import com.yubrae.app.data.db.CaptureChannel
import com.yubrae.app.data.db.CategoryEntity
import com.yubrae.app.data.db.ReimburseStatus
import com.yubrae.app.data.db.TagEntity
import com.yubrae.app.ui.components.KairoFormats
import com.yubrae.app.ui.components.KairoIcons
import com.yubrae.app.ui.components.TagBadgeChip
import com.yubrae.app.ui.components.YimuCard
import com.yubrae.app.ui.theme.KairoTheme
import com.yubrae.app.ui.theme.tabular
import com.yubrae.app.ui.viewmodel.DayTransactionGroup
import com.yubrae.app.ui.viewmodel.TransactionFilters
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs

internal enum class TxViewMode {
    LIST, CALENDAR
}

private val TxChannelFilterOptions = listOf(
    CaptureChannel.MANUAL,
    CaptureChannel.NOTIFICATION,
    CaptureChannel.SMS,
    CaptureChannel.SCREEN,
    CaptureChannel.AUTO_ACCOUNTING,
    CaptureChannel.AI,
    CaptureChannel.IMPORT,
    CaptureChannel.RECURRING,
    CaptureChannel.SHARE
)
private val CalendarWeekHeaders = listOf("日", "一", "二", "三", "四", "五", "六")
private val CalendarTenThousand = BigDecimal(10_000)
private val ReimburseFilterOptions = listOf(
    "ALL" to "全部",
    ReimburseStatus.NONE to "不报销",
    ReimburseStatus.PENDING to "待报销",
    ReimburseStatus.REIMBURSED to "已报销"
)
private val SearchFieldShape = RoundedCornerShape(12.dp)
private val CalendarCellShape = RoundedCornerShape(9.dp)
private val TagAllPillShape = RoundedCornerShape(6.dp)

@Composable
internal fun CollapsibleSearchFilterBar(
    filters: TransactionFilters,
    baseCurrency: String,
    pinnedCurrencies: List<String>,
    accounts: List<AccountEntity>,
    accountMap: Map<Long, AccountEntity>,
    categories: List<CategoryEntity>,
    tags: List<TagEntity>,
    hasAnySubFilter: Boolean,
    showAdvancedFilters: Boolean,
    advancedFilterCount: Int,
    minAmountText: String,
    maxAmountText: String,
    onQueryChange: (String) -> Unit,
    onToggleAdvancedFilters: () -> Unit,
    onClearAllFilters: () -> Unit,
    onSelectCurrency: (String) -> Unit,
    onSelectAccount: (Long?) -> Unit,
    onSelectChannel: (String) -> Unit,
    onSelectCategory: (Long?) -> Unit,
    onSelectTag: (String?) -> Unit,
    onSelectReimburseStatus: (String) -> Unit,
    onAmountRangeChange: (String, String) -> Unit,
    onClearAdvanced: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showCurrencyFilterMenu by remember { mutableStateOf(false) }
    var showAccountFilterMenu by remember { mutableStateOf(false) }
    var showChannelFilterMenu by remember { mutableStateOf(false) }

    val currencyOptions = remember(pinnedCurrencies, accounts, filters.currencyFilter) {
        linkedSetOf("CNY", "USD", "EUR", "GBP").apply {
            pinnedCurrencies.forEach { code ->
                val normalized = code.trim().uppercase()
                if (normalized.isNotEmpty() && normalized != "ALL") add(normalized)
            }
            accounts.forEach { acc ->
                val normalized = acc.currencyCode.trim().uppercase()
                if (normalized.isNotEmpty() && normalized != "ALL") add(normalized)
            }
            val activeCurrency = filters.currencyFilter.trim().uppercase()
            if (activeCurrency.isNotEmpty() && activeCurrency != "ALL") add(activeCurrency)
        }.toList()
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = filters.query,
            onValueChange = onQueryChange,
            placeholder = { Text("搜索分类 / 商户 / 账户 / 标签 / 备注") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            trailingIcon = {
                if (filters.query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Rounded.Close, contentDescription = "清除搜索")
                    }
                }
            },
            singleLine = true,
            shape = SearchFieldShape,
            modifier = Modifier.fillMaxWidth()
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            item(key = "advanced_filter_toggle", contentType = "filter_chip") {
                FilterChip(
                    selected = showAdvancedFilters || advancedFilterCount > 0,
                    onClick = onToggleAdvancedFilters,
                    leadingIcon = {
                        Icon(Icons.Rounded.FilterList, null, modifier = Modifier.size(16.dp))
                    },
                    trailingIcon = {
                        Icon(
                            imageVector = if (showAdvancedFilters) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    label = {
                        Text(if (advancedFilterCount > 0) "筛选 ($advancedFilterCount)" else "筛选")
                    }
                )
            }

            if (hasAnySubFilter) {
                item(key = "reset_all_filters", contentType = "filter_chip") {
                    FilterChip(
                        selected = false,
                        onClick = onClearAllFilters,
                        leadingIcon = {
                            Icon(Icons.Rounded.Close, null, modifier = Modifier.size(15.dp))
                        },
                        label = { Text("清空筛选") }
                    )
                }
            }

            item(key = "currency_filter", contentType = "dropdown_filter_chip") {
                Box {
                    FilterChip(
                        selected = filters.currencyFilter != "ALL",
                        onClick = { showCurrencyFilterMenu = true },
                        leadingIcon = {
                            Icon(Icons.Rounded.CurrencyExchange, null, modifier = Modifier.size(16.dp))
                        },
                        trailingIcon = {
                            Icon(Icons.Rounded.ArrowDropDown, null, modifier = Modifier.size(18.dp))
                        },
                        label = {
                            Text(
                                if (filters.currencyFilter == "ALL") "全部币种" else filters.currencyFilter,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    )
                    DropdownMenu(
                        expanded = showCurrencyFilterMenu,
                        onDismissRequest = { showCurrencyFilterMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("全部币种") },
                            onClick = {
                                onSelectCurrency("ALL")
                                showCurrencyFilterMenu = false
                            }
                        )
                        currencyOptions.forEach { code ->
                            val info = CurrencyCatalog.find(code)
                            DropdownMenuItem(
                                text = { Text("${info.flag} ${info.code} · ${info.nameZh}") },
                                onClick = {
                                    onSelectCurrency(code)
                                    showCurrencyFilterMenu = false
                                }
                            )
                        }
                    }
                }
            }

            item(key = "account_filter", contentType = "dropdown_filter_chip") {
                Box {
                    val selectedAcctName = accountMap[filters.accountIdFilter]?.name ?: "全部账户"
                    FilterChip(
                        selected = filters.accountIdFilter != null,
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
                                onSelectAccount(null)
                                showAccountFilterMenu = false
                            }
                        )
                        accounts.forEach { acc ->
                            DropdownMenuItem(
                                text = { Text("${acc.name} (${acc.currencyCode})") },
                                onClick = {
                                    onSelectAccount(acc.id)
                                    showAccountFilterMenu = false
                                }
                            )
                        }
                    }
                }
            }

            item(key = "channel_filter", contentType = "dropdown_filter_chip") {
                Box {
                    val channelLabel = if (filters.channelFilter == "ALL") {
                        "全部来源"
                    } else {
                        KairoFormats.channelLabel(filters.channelFilter)
                    }
                    FilterChip(
                        selected = filters.channelFilter != "ALL",
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
                                onSelectChannel("ALL")
                                showChannelFilterMenu = false
                            }
                        )
                        TxChannelFilterOptions.forEach { ch ->
                            DropdownMenuItem(
                                text = { Text(KairoFormats.channelLabel(ch)) },
                                onClick = {
                                    onSelectChannel(ch)
                                    showChannelFilterMenu = false
                                }
                            )
                        }
                    }
                }
            }
        }

        AnimatedVisibility(visible = showAdvancedFilters) {
            AdvancedFilterPanel(
                categories = categories,
                selectedCategoryId = filters.categoryIdFilter,
                onSelectCategory = onSelectCategory,
                tags = tags,
                selectedTag = filters.tagFilter,
                onSelectTag = onSelectTag,
                selectedReimburseStatus = filters.reimburseFilter,
                onSelectReimburseStatus = onSelectReimburseStatus,
                minAmountText = minAmountText,
                maxAmountText = maxAmountText,
                baseCurrency = baseCurrency,
                onAmountRangeChange = onAmountRangeChange,
                onClearAdvanced = onClearAdvanced
            )
        }
    }
}

@Composable
internal fun MonthlyCalendarCard(
    yearMonth: YearMonth,
    monthDays: List<DayTransactionGroup>,
    selectedDate: LocalDate?,
    privacyMasked: Boolean,
    onSelectDate: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    val semantic = KairoTheme.semantic
    val today = remember { LocalDate.now() }
    val dayGroupMap = remember(monthDays) { monthDays.associateBy { it.date } }
    val maxDayExpense = remember(monthDays) {
        monthDays.maxOfOrNull { it.dayExpenseBaseMicros } ?: 0L
    }

    val daysInMonth = yearMonth.lengthOfMonth()
    val leadingBlanks = yearMonth.atDay(1).dayOfWeek.value % 7
    val totalSlots = leadingBlanks + daysInMonth
    val rows = (totalSlots + 6) / 7

    YimuCard(cornerRadius = 14.dp, modifier = modifier) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(
                text = "${yearMonth.monthValue}月收支日历",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                CalendarWeekHeaders.forEachIndexed { idx, label ->
                    val isWeekend = idx == 0 || idx == 6
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isWeekend) {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            for (row in 0 until rows) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    for (col in 0 until 7) {
                        val slotIndex = row * 7 + col
                        val dayNum = slotIndex - leadingBlanks + 1
                        if (dayNum < 1 || dayNum > daysInMonth) {
                            Spacer(modifier = Modifier.weight(1f).height(50.dp))
                        } else {
                            val date = yearMonth.atDay(dayNum)
                            val group = dayGroupMap[date]
                            val exp = group?.dayExpenseBaseMicros ?: 0L
                            val inc = group?.dayIncomeBaseMicros ?: 0L
                            val isSelected = selectedDate == date
                            val isToday = date == today
                            val isWeekendCol = col == 0 || col == 6

                            val heatAlpha = if (exp > 0L && maxDayExpense > 0L) {
                                (0.05f + 0.14f * (exp.toFloat() / maxDayExpense.toFloat())).coerceIn(0.05f, 0.20f)
                            } else {
                                0f
                            }
                            val cellBg = when {
                                isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
                                exp > 0L -> semantic.expense.copy(alpha = heatAlpha)
                                inc > 0L -> semantic.income.copy(alpha = 0.08f)
                                else -> MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.55f)
                            }
                            val cellBorder = when {
                                isSelected -> BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                                isToday -> BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f))
                                else -> null
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp)
                                    .clip(CalendarCellShape)
                                    .background(cellBg)
                                    .then(
                                        if (cellBorder != null) Modifier.border(cellBorder, CalendarCellShape) else Modifier
                                    )
                                    .clickable { onSelectDate(date) }
                                    .padding(horizontal = 2.dp, vertical = 3.dp)
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = dayNum.toString(),
                                        style = MaterialTheme.typography.labelMedium.tabular(),
                                        fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = when {
                                            isToday || isSelected -> MaterialTheme.colorScheme.primary
                                            isWeekendCol -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
                                            else -> MaterialTheme.colorScheme.onSurface
                                        }
                                    )

                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(1.dp)
                                    ) {
                                        if (exp > 0L) {
                                            Text(
                                                text = if (privacyMasked) "••" else "-${formatCompactCalendarMicros(exp)}",
                                                fontSize = 9.sp,
                                                lineHeight = 10.sp,
                                                color = semantic.expense,
                                                maxLines = 1,
                                                softWrap = false,
                                                overflow = TextOverflow.Clip
                                            )
                                        }
                                        if (inc > 0L) {
                                            Text(
                                                text = if (privacyMasked) "••" else "+${formatCompactCalendarMicros(inc)}",
                                                fontSize = 9.sp,
                                                lineHeight = 10.sp,
                                                color = semantic.income,
                                                maxLines = 1,
                                                softWrap = false,
                                                overflow = TextOverflow.Clip
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

private fun formatCompactCalendarMicros(micros: Long): String {
    val bd = MoneyAmount.microsToBigDecimal(abs(micros))
    return if (bd >= CalendarTenThousand) {
        bd.divide(CalendarTenThousand, 1, RoundingMode.HALF_UP)
            .stripTrailingZeros()
            .toPlainString() + "w"
    } else if (bd >= BigDecimal(100)) {
        bd.setScale(0, RoundingMode.HALF_UP).toPlainString()
    } else {
        bd.setScale(1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AdvancedFilterPanel(
    categories: List<CategoryEntity>,
    selectedCategoryId: Long?,
    onSelectCategory: (Long?) -> Unit,
    tags: List<TagEntity>,
    selectedTag: String?,
    onSelectTag: (String?) -> Unit,
    selectedReimburseStatus: String,
    onSelectReimburseStatus: (String) -> Unit,
    minAmountText: String,
    maxAmountText: String,
    baseCurrency: String,
    onAmountRangeChange: (String, String) -> Unit,
    onClearAdvanced: () -> Unit
) {
    val parentCategories = remember(categories) {
        categories.filter { it.parentId == null }.sortedWith(compareBy({ it.type }, { it.sortOrder }, { it.id }))
    }
    val selectedCat = remember(categories, selectedCategoryId) {
        categories.firstOrNull { it.id == selectedCategoryId }
    }
    val activeParentId = selectedCat?.parentId ?: selectedCat?.id
    val subCategories = remember(categories, activeParentId) {
        if (activeParentId != null) categories.filter { it.parentId == activeParentId } else emptyList()
    }

    YimuCard(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        cornerRadius = 14.dp
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "分类",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(
                    onClick = onClearAdvanced,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    modifier = Modifier.height(26.dp)
                ) {
                    Text("重置", style = MaterialTheme.typography.labelSmall)
                }
            }

            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                item(key = "cat_all", contentType = "cat_chip") {
                    FilterChip(
                        selected = selectedCategoryId == null,
                        onClick = { onSelectCategory(null) },
                        label = { Text("全部") }
                    )
                }
                items(parentCategories, key = { it.id }, contentType = { "cat_chip" }) { cat ->
                    FilterChip(
                        selected = activeParentId == cat.id,
                        onClick = {
                            onSelectCategory(if (selectedCategoryId == cat.id) null else cat.id)
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = KairoIcons.iconFor(cat.icon),
                                contentDescription = null,
                                tint = Color(cat.colorHex),
                                modifier = Modifier.size(15.dp)
                            )
                        },
                        label = { Text(cat.name) }
                    )
                }
            }

            if (subCategories.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    subCategories.forEach { sub ->
                        FilterChip(
                            selected = selectedCategoryId == sub.id,
                            onClick = {
                                onSelectCategory(if (selectedCategoryId == sub.id) activeParentId else sub.id)
                            },
                            label = { Text(sub.name, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }

            Text(
                text = "标签",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val tagFilterEntries = remember(tags, selectedTag) {
                val list = tags.sortedWith(compareBy({ it.sortOrder }, { it.id }))
                    .map { Triple(it.name, it.colorHex, it.icon) }
                    .toMutableList()
                if (!selectedTag.isNullOrBlank() && list.none { it.first == selectedTag }) {
                    list.add(0, Triple(selectedTag, KairoIcons.fallbackTagColorHex(selectedTag), ""))
                }
                list
            }
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                item(key = "tag_all", contentType = "tag_all_chip") {
                    val allSelected = selectedTag == null
                    val primaryColor = MaterialTheme.colorScheme.primary
                    Surface(
                        modifier = Modifier
                            .clip(TagAllPillShape)
                            .clickable { onSelectTag(null) },
                        shape = TagAllPillShape,
                        color = if (allSelected) {
                            primaryColor.copy(alpha = 0.16f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f)
                        },
                        contentColor = if (allSelected) primaryColor else MaterialTheme.colorScheme.onSurface,
                        border = if (allSelected) BorderStroke(1.dp, primaryColor.copy(alpha = 0.85f)) else null
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.5.dp)
                        ) {
                            Text(
                                text = "全部",
                                style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp, lineHeight = 14.sp),
                                color = if (allSelected) primaryColor else MaterialTheme.colorScheme.onSurface,
                                fontWeight = if (allSelected) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1,
                                softWrap = false
                            )
                            if (allSelected) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = null,
                                    tint = primaryColor,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }
                }
                items(tagFilterEntries, key = { it.first }, contentType = { "tag_chip" }) { (tagName, colorHex, iconKey) ->
                    TagBadgeChip(
                        name = tagName,
                        colorHex = colorHex,
                        selected = selectedTag == tagName,
                        onClick = {
                            onSelectTag(if (selectedTag == tagName) null else tagName)
                        },
                        iconKey = iconKey
                    )
                }
            }

            Text(
                text = "报销状态",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(ReimburseFilterOptions, key = { it.first }, contentType = { "reimburse_chip" }) { (code, label) ->
                    FilterChip(
                        selected = selectedReimburseStatus == code,
                        onClick = { onSelectReimburseStatus(code) },
                        label = { Text(label) }
                    )
                }
            }

            Text(
                text = "金额 ($baseCurrency)",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = minAmountText,
                    onValueChange = { onAmountRangeChange(it, maxAmountText) },
                    placeholder = { Text("最低") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    textStyle = MaterialTheme.typography.bodyMedium.tabular(),
                    modifier = Modifier.weight(1f)
                )
                Text("~", color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = maxAmountText,
                    onValueChange = { onAmountRangeChange(minAmountText, it) },
                    placeholder = { Text("最高") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    textStyle = MaterialTheme.typography.bodyMedium.tabular(),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

package com.kairoledger.app.ui.screens.stats

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kairoledger.app.core.contract.BudgetProgress
import com.kairoledger.app.core.contract.CategoryStat
import com.kairoledger.app.core.contract.MonthlyPoint
import com.kairoledger.app.core.model.CurrencyCatalog
import com.kairoledger.app.core.model.MoneyAmount
import com.kairoledger.app.data.db.BudgetEntity
import com.kairoledger.app.data.db.CategoryEntity
import com.kairoledger.app.data.db.TransactionType
import com.kairoledger.app.ui.components.ConfirmActionDialog
import com.kairoledger.app.ui.components.CurrencyBadge
import com.kairoledger.app.ui.components.CurrencyPickerDialog
import com.kairoledger.app.ui.components.EmptyStateCard
import com.kairoledger.app.ui.components.KairoIcons
import com.kairoledger.app.ui.theme.KairoTheme
import com.kairoledger.app.ui.theme.tabular
import com.kairoledger.app.ui.viewmodel.KairoMainViewModel
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun StatsScreen(
    viewModel: KairoMainViewModel,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val uiState by viewModel.statsUiState.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    var showCurrencyMenu by remember { mutableStateOf(false) }
    var showMoreCurrencies by remember { mutableStateOf(false) }
    var editingBudget by remember { mutableStateOf<BudgetEntity?>(null) }
    var showCreateBudget by remember { mutableStateOf(false) }
    var deleteBudgetCandidate by remember { mutableStateOf<BudgetEntity?>(null) }
    var highlightedCategoryId by remember { mutableStateOf<Long?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 88.dp,
            start = 16.dp,
            end = 16.dp
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 顶部控制栏：月份切换 + 展示币种 + 收支切换
        item(key = "stats_header") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { viewModel.shiftStatsMonth(-1) }) {
                            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "上个月")
                        }
                        Text(
                            text = uiState.selectedMonth.format(DateTimeFormatter.ofPattern("yyyy年M月")),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = { viewModel.shiftStatsMonth(1) }) {
                            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "下个月")
                        }
                    }

                    Box {
                        val info = CurrencyCatalog.find(uiState.displayCurrency)
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.clickable { showCurrencyMenu = true }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(info.flag, style = MaterialTheme.typography.labelMedium)
                                Text(info.code, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                Icon(Icons.Rounded.ArrowDropDown, null, modifier = Modifier.size(16.dp))
                            }
                        }
                        DropdownMenu(
                            expanded = showCurrencyMenu,
                            onDismissRequest = { showCurrencyMenu = false }
                        ) {
                            settings.pinnedCurrencies.forEach { code ->
                                val itemInfo = CurrencyCatalog.find(code)
                                DropdownMenuItem(
                                    text = { Text("${itemInfo.flag} ${itemInfo.code} · ${itemInfo.nameZh}") },
                                    onClick = {
                                        viewModel.setStatsDisplayCurrency(itemInfo.code)
                                        showCurrencyMenu = false
                                    }
                                )
                            }
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("更多币种...") },
                                onClick = {
                                    showCurrencyMenu = false
                                    showMoreCurrencies = true
                                }
                            )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = uiState.selectedType == TransactionType.EXPENSE,
                        onClick = {
                            highlightedCategoryId = null
                            viewModel.setStatsType(TransactionType.EXPENSE)
                        },
                        label = { Text("支出分析") }
                    )
                    FilterChip(
                        selected = uiState.selectedType == TransactionType.INCOME,
                        onClick = {
                            highlightedCategoryId = null
                            viewModel.setStatsType(TransactionType.INCOME)
                        },
                        label = { Text("收入分析") }
                    )
                }
            }
        }

        // 近 6 个月收支对比柱状图（Canvas 自绘）
        item(key = "monthly_trend_chart") {
            MonthlyTrendBarChartCard(
                points = uiState.monthlyTrend,
                displayCurrency = uiState.displayCurrency,
                onSelectMonth = { year, month ->
                    viewModel.setStatsMonth(YearMonth.of(year, month))
                }
            )
        }

        // 分类占比环形图与排行（Canvas 自绘）
        item(key = "category_donut_card") {
            CategoryBreakdownCard(
                stats = uiState.categoryStats,
                totalMicros = uiState.totalCategoryAmountMicros,
                type = uiState.selectedType,
                displayCurrency = uiState.displayCurrency,
                highlightedCategoryId = highlightedCategoryId,
                onSelectCategory = { catId ->
                    highlightedCategoryId = if (highlightedCategoryId == catId) null else catId
                }
            )
        }

        // 预算管理卡片
        item(key = "budget_management") {
            BudgetManagementCard(
                budgets = uiState.budgetProgressList,
                onAddBudget = { showCreateBudget = true },
                onEditBudget = { editingBudget = it },
                onDeleteBudget = { deleteBudgetCandidate = it }
            )
        }
    }

    if (showCreateBudget || editingBudget != null) {
        BudgetEditorDialog(
            existing = editingBudget,
            expenseCategories = categories.filter { it.type == TransactionType.EXPENSE },
            pinnedCurrencies = settings.pinnedCurrencies,
            defaultCurrency = settings.baseCurrency,
            onSave = { budget ->
                viewModel.saveBudget(budget) {
                    showCreateBudget = false
                    editingBudget = null
                }
            },
            onDismiss = {
                showCreateBudget = false
                editingBudget = null
            }
        )
    }

    deleteBudgetCandidate?.let { budget ->
        ConfirmActionDialog(
            title = "删除预算「${budget.name}」？",
            message = "删除后将不再追踪该项预算限额。",
            confirmText = "删除",
            isDestructive = true,
            onConfirm = {
                viewModel.deleteBudget(budget.id)
                deleteBudgetCandidate = null
            },
            onDismiss = { deleteBudgetCandidate = null }
        )
    }

    if (showMoreCurrencies) {
        CurrencyPickerDialog(
            selectedCode = uiState.displayCurrency,
            pinnedCodes = settings.pinnedCurrencies,
            onSelect = { viewModel.setStatsDisplayCurrency(it) },
            onDismiss = { showMoreCurrencies = false }
        )
    }
}

@Composable
private fun MonthlyTrendBarChartCard(
    points: List<MonthlyPoint>,
    displayCurrency: String,
    onSelectMonth: (Int, Int) -> Unit
) {
    val semantic = KairoTheme.semantic
    var selectedIdx by remember(points.size) {
        mutableIntStateOf((points.size - 1).coerceAtLeast(0))
    }
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
    val highlightPillColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "近 6 个月收支趋势 ($displayCurrency)",
                    style = MaterialTheme.typography.titleMedium
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LegendDot(color = semantic.income, label = "收入")
                    LegendDot(color = semantic.expense, label = "支出")
                }
            }

            if (points.isEmpty()) {
                Text(
                    text = "暂无月度趋势数据",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            } else {
                val safeIdx = selectedIdx.coerceIn(0, points.lastIndex)
                val activePoint = points[safeIdx]
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${activePoint.year}年${activePoint.month}月",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                text = "收 ${MoneyAmount.format(activePoint.incomeBaseMicros, displayCurrency)}",
                                style = MaterialTheme.typography.labelMedium.tabular(),
                                color = semantic.income
                            )
                            Text(
                                text = "支 ${MoneyAmount.format(activePoint.expenseBaseMicros, displayCurrency)}",
                                style = MaterialTheme.typography.labelMedium.tabular(),
                                color = semantic.expense
                            )
                        }
                    }
                }

                val maxMicros = points.maxOfOrNull { maxOf(it.incomeBaseMicros, it.expenseBaseMicros) }
                    ?.coerceAtLeast(10_000L * MoneyAmount.AMOUNT_SCALE / 100L) // at least 100 units
                    ?: (100L * MoneyAmount.AMOUNT_SCALE)

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(190.dp)
                        .pointerInput(points) {
                            detectTapGestures { offset ->
                                val leftPad = 48.dp.toPx()
                                val chartW = (size.width - leftPad).coerceAtLeast(1f)
                                if (offset.x >= leftPad && points.isNotEmpty()) {
                                    val colW = chartW / points.size
                                    val tapped = ((offset.x - leftPad) / colW).toInt().coerceIn(0, points.lastIndex)
                                    selectedIdx = tapped
                                    val pt = points[tapped]
                                    onSelectMonth(pt.year, pt.month)
                                }
                            }
                        }
                ) {
                    val leftPad = 48.dp.toPx()
                    val bottomPad = 26.dp.toPx()
                    val topPad = 10.dp.toPx()
                    val chartWidth = (size.width - leftPad).coerceAtLeast(1f)
                    val chartHeight = (size.height - topPad - bottomPad).coerceAtLeast(1f)

                    // 网格线与 Y 轴刻度
                    val gridSteps = 3
                    for (i in 0..gridSteps) {
                        val ratio = i.toFloat() / gridSteps.toFloat()
                        val y = topPad + chartHeight * (1f - ratio)
                        drawLine(
                            color = gridColor,
                            start = Offset(leftPad, y),
                            end = Offset(size.width, y),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = if (i == 0) null else PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                        )
                        val tickMicros = (maxMicros.toDouble() * ratio).toLong()
                        val tickText = formatCompactScale(tickMicros, displayCurrency)
                        val measured = textMeasurer.measure(tickText, labelStyle)
                        drawText(
                            textLayoutResult = measured,
                            topLeft = Offset(
                                x = (leftPad - measured.size.width - 6.dp.toPx()).coerceAtLeast(0f),
                                y = (y - measured.size.height / 2f).coerceIn(0f, size.height - measured.size.height)
                            )
                        )
                    }

                    val colWidth = chartWidth / points.size
                    val barWidth = (colWidth * 0.28f).coerceIn(6.dp.toPx(), 16.dp.toPx())
                    val barGap = 4.dp.toPx()

                    points.forEachIndexed { idx, pt ->
                        val colLeft = leftPad + idx * colWidth
                        val centerX = colLeft + colWidth / 2f

                        if (idx == safeIdx) {
                            drawRoundRect(
                                color = highlightPillColor,
                                topLeft = Offset(colLeft + 3.dp.toPx(), topPad),
                                size = Size(colWidth - 6.dp.toPx(), chartHeight + bottomPad - 2.dp.toPx()),
                                cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx())
                            )
                        }

                        val incRatio = (pt.incomeBaseMicros.toDouble() / maxMicros.toDouble()).toFloat().coerceIn(0f, 1f)
                        val expRatio = (pt.expenseBaseMicros.toDouble() / maxMicros.toDouble()).toFloat().coerceIn(0f, 1f)

                        val incH = (chartHeight * incRatio).coerceAtLeast(if (pt.incomeBaseMicros > 0L) 3.dp.toPx() else 0f)
                        val expH = (chartHeight * expRatio).coerceAtLeast(if (pt.expenseBaseMicros > 0L) 3.dp.toPx() else 0f)

                        if (incH > 0f) {
                            drawRoundRect(
                                color = semantic.income,
                                topLeft = Offset(centerX - barWidth - barGap / 2f, topPad + chartHeight - incH),
                                size = Size(barWidth, incH),
                                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                            )
                        }
                        if (expH > 0f) {
                            drawRoundRect(
                                color = semantic.expense,
                                topLeft = Offset(centerX + barGap / 2f, topPad + chartHeight - expH),
                                size = Size(barWidth, expH),
                                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                            )
                        }

                        val xMeasured = textMeasurer.measure(pt.monthLabel, labelStyle)
                        drawText(
                            textLayoutResult = xMeasured,
                            topLeft = Offset(
                                x = centerX - xMeasured.size.width / 2f,
                                y = topPad + chartHeight + 6.dp.toPx()
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryBreakdownCard(
    stats: List<CategoryStat>,
    totalMicros: Long,
    type: String,
    displayCurrency: String,
    highlightedCategoryId: Long?,
    onSelectCategory: (Long?) -> Unit
) {
    val titleText = if (type == TransactionType.EXPENSE) "支出分类占比" else "收入分类占比"
    val trackBgColor = MaterialTheme.colorScheme.surfaceContainerHighest

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(text = titleText, style = MaterialTheme.typography.titleMedium)

            if (stats.isEmpty()) {
                EmptyStateCard(
                    icon = Icons.Rounded.PieChart,
                    title = "本月暂无${if (type == TransactionType.EXPENSE) "支出" else "收入"}分类记录",
                    subtitle = "记入已确认流水后，将在此自动生成环形分布图与排行榜。"
                )
            } else {
                val activeStat = stats.firstOrNull { it.categoryId == highlightedCategoryId }
                // 环形图 Canvas
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(190.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.size(168.dp)) {
                        val strokeWidth = 26.dp.toPx()
                        val diameter = size.minDimension - strokeWidth
                        val topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)
                        val arcSize = Size(diameter, diameter)

                        drawArc(
                            color = trackBgColor,
                            startAngle = 0f,
                            sweepAngle = 360f,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = strokeWidth)
                        )

                        var currentStart = -90f
                        val gapAngle = if (stats.size > 1) 2.2f else 0f
                        stats.forEach { stat ->
                            val rawSweep = 360f * stat.percentage.coerceIn(0f, 1f)
                            val sweep = (rawSweep - gapAngle).coerceAtLeast(1.5f)
                            val isSelected = highlightedCategoryId == null || highlightedCategoryId == stat.categoryId
                            drawArc(
                                color = Color(stat.colorHex).copy(alpha = if (isSelected) 1f else 0.28f),
                                startAngle = currentStart,
                                sweepAngle = sweep,
                                useCenter = false,
                                topLeft = topLeft,
                                size = arcSize,
                                style = Stroke(
                                    width = if (highlightedCategoryId == stat.categoryId) strokeWidth + 4.dp.toPx() else strokeWidth,
                                    cap = StrokeCap.Butt
                                )
                            )
                            currentStart += rawSweep
                        }
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = activeStat?.categoryName ?: if (type == TransactionType.EXPENSE) "本月支出" else "本月收入",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = MoneyAmount.format(
                                activeStat?.totalBaseMicros ?: totalMicros,
                                displayCurrency
                            ),
                            style = MaterialTheme.typography.titleLarge.tabular(),
                            fontWeight = FontWeight.Bold
                        )
                        if (activeStat != null) {
                            Text(
                                text = String.format(Locale.US, "%.1f%% · %d 笔", activeStat.percentage * 100f, activeStat.count),
                                style = MaterialTheme.typography.labelSmall.tabular(),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                // 分类排行与进度条
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    stats.forEach { stat ->
                        val catColor = Color(stat.colorHex)
                        val isHighlighted = highlightedCategoryId == stat.categoryId
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isHighlighted) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                            } else {
                                MaterialTheme.colorScheme.surface
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectCategory(stat.categoryId) }
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = catColor.copy(alpha = 0.15f),
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = KairoIcons.iconFor(stat.icon),
                                                contentDescription = null,
                                                tint = catColor,
                                                modifier = Modifier.size(17.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = stat.categoryName,
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                        Text(
                                            text = "${stat.count} 笔",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = MoneyAmount.format(stat.totalBaseMicros, displayCurrency),
                                            style = MaterialTheme.typography.titleSmall.tabular(),
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = String.format(Locale.US, "%.1f%%", stat.percentage * 100f),
                                            style = MaterialTheme.typography.labelSmall.tabular(),
                                            color = catColor
                                        )
                                    }
                                }
                                LinearProgressIndicator(
                                    progress = { stat.percentage.coerceIn(0f, 1f) },
                                    color = catColor,
                                    trackColor = catColor.copy(alpha = 0.14f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(CircleShape)
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
private fun BudgetManagementCard(
    budgets: List<BudgetProgress>,
    onAddBudget: () -> Unit,
    onEditBudget: (BudgetEntity) -> Unit,
    onDeleteBudget: (BudgetEntity) -> Unit
) {
    val semantic = KairoTheme.semantic
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "月度预算管理", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = onAddBudget) {
                    Icon(Icons.Rounded.Add, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("添加预算")
                }
            }

            if (budgets.isEmpty()) {
                Text(
                    text = "暂未设定预算。可添加全局月度总预算或指定消费分类预算，实时监控花销水位。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                budgets.forEach { bp ->
                    val barColor = when {
                        bp.overBudget -> semantic.expense
                        bp.progressRatio >= 0.8f -> semantic.warning
                        else -> MaterialTheme.colorScheme.primary
                    }
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = if (bp.overBudget) BorderStroke(1.dp, semantic.expense.copy(alpha = 0.4f)) else null,
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
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = bp.budget.name,
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                                        ) {
                                            Text(
                                                text = bp.categoryName,
                                                style = MaterialTheme.typography.labelSmall,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                        if (bp.overBudget) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = semantic.expenseContainer
                                            ) {
                                                Text(
                                                    text = "超支警示",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = semantic.expense,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "已用 ${MoneyAmount.format(bp.spentInBudgetCurrencyMicros, bp.budget.currencyCode)} / 限额 ${MoneyAmount.format(bp.limitInBudgetCurrencyMicros, bp.budget.currencyCode)}",
                                        style = MaterialTheme.typography.bodySmall.tabular(),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Row {
                                    IconButton(
                                        onClick = { onEditBudget(bp.budget) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Rounded.Edit, "编辑", modifier = Modifier.size(16.dp))
                                    }
                                    IconButton(
                                        onClick = { onDeleteBudget(bp.budget) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Rounded.Delete, "删除", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                LinearProgressIndicator(
                                    progress = { bp.progressRatio.coerceIn(0f, 1f) },
                                    color = barColor,
                                    trackColor = barColor.copy(alpha = 0.16f),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(7.dp)
                                        .clip(CircleShape)
                                )
                                Text(
                                    text = "${(bp.progressRatio * 100f).toInt()}%",
                                    style = MaterialTheme.typography.labelMedium.tabular(),
                                    color = barColor,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BudgetEditorDialog(
    existing: BudgetEntity?,
    expenseCategories: List<CategoryEntity>,
    pinnedCurrencies: List<String>,
    defaultCurrency: String,
    onSave: (BudgetEntity) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(existing?.name ?: "月度总预算") }
    var selectedCategoryId by remember { mutableStateOf(existing?.categoryId) }
    var currencyCode by remember { mutableStateOf(existing?.currencyCode ?: defaultCurrency) }
    var limitInput by remember {
        mutableStateOf(
            if (existing != null) {
                MoneyAmount.formatPlain(existing.limitAmountMicros, CurrencyCatalog.find(existing.currencyCode).minorDigits, false)
            } else {
                "5000.00"
            }
        )
    }
    var enabled by remember { mutableStateOf(existing?.enabled ?: true) }
    var showMoreCurrencies by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "新建月度预算" else "编辑预算") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("预算名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = limitInput,
                    onValueChange = { limitInput = it },
                    label = { Text("月度限额 ($currencyCode)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("预算币种", style = MaterialTheme.typography.labelMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val codes = if (pinnedCurrencies.contains(currencyCode)) pinnedCurrencies else pinnedCurrencies + currencyCode
                    items(codes, key = { it }) { code ->
                        CurrencyBadge(
                            currencyCode = code,
                            selected = code.equals(currencyCode, true),
                            onClick = { currencyCode = code }
                        )
                    }
                    item(key = "more") {
                        FilterChip(
                            selected = false,
                            onClick = { showMoreCurrencies = true },
                            label = { Text("更多") }
                        )
                    }
                }

                Text("适用范围", style = MaterialTheme.typography.labelMedium)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = selectedCategoryId == null,
                        onClick = {
                            selectedCategoryId = null
                            if (name.isBlank() || expenseCategories.any { it.name == name }) {
                                name = "月度总预算"
                            }
                        },
                        label = { Text("全局总预算") }
                    )
                    expenseCategories.filter { it.parentId == null }.forEach { cat ->
                        FilterChip(
                            selected = selectedCategoryId == cat.id,
                            onClick = {
                                selectedCategoryId = cat.id
                                name = "${cat.name}预算"
                            },
                            label = { Text(cat.name) }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("启用该预算", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }

                errorMsg?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val limitMicros = MoneyAmount.parseToMicros(limitInput)
                    when {
                        name.trim().isEmpty() -> errorMsg = "请输入预算名称"
                        limitMicros == null || limitMicros <= 0L -> errorMsg = "请输入大于 0 的预算金额"
                        else -> onSave(
                            BudgetEntity(
                                id = existing?.id ?: 0L,
                                name = name.trim(),
                                categoryId = selectedCategoryId,
                                period = "MONTHLY",
                                limitAmountMicros = limitMicros,
                                currencyCode = currencyCode,
                                enabled = enabled
                            )
                        )
                    }
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

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatCompactScale(micros: Long, currency: String): String {
    val symbol = CurrencyCatalog.symbolOf(currency)
    val units = micros / MoneyAmount.AMOUNT_SCALE
    return when {
        units >= 10_000L -> String.format(Locale.US, "%s%.1fw", symbol, units / 10_000.0)
        units >= 1_000L -> String.format(Locale.US, "%s%.1fk", symbol, units / 1_000.0)
        else -> "$symbol$units"
    }
}

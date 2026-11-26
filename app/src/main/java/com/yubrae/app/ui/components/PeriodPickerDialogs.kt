package com.yubrae.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yubrae.app.core.model.MoneyAmount
import com.yubrae.app.ui.theme.KairoTheme
import com.yubrae.app.ui.theme.tabular
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale

internal object PeriodPickerHelper {

    fun weekBoundsFor(date: LocalDate): Pair<LocalDate, LocalDate> {
        val monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val sunday = monday.plusDays(6)
        return monday to sunday
    }

    fun weekLabel(date: LocalDate): String {
        val (start, end) = weekBoundsFor(date)
        return if (start.year == end.year) {
            String.format(
                Locale.US,
                "%d/%02d/%02d - %02d/%02d",
                start.year,
                start.monthValue,
                start.dayOfMonth,
                end.monthValue,
                end.dayOfMonth
            )
        } else {
            String.format(
                Locale.US,
                "%d/%02d/%02d - %d/%02d/%02d",
                start.year,
                start.monthValue,
                start.dayOfMonth,
                end.year,
                end.monthValue,
                end.dayOfMonth
            )
        }
    }

    fun yearGridRange(anchorYear: Int): List<Int> {
        val safeYear = anchorYear.coerceAtLeast(0)
        val startYear = (safeYear / 12) * 12
        return (0 until 12).map { startYear + it }
    }

    fun formatMonthSummaryCopyText(
        yearMonthLabel: String,
        baseCurrency: String,
        expenseText: String,
        incomeText: String,
        balanceText: String,
        foreignCurrencySummaries: List<Triple<String, Long, Long>>
    ): String {
        return buildString {
            appendLine("$yearMonthLabel 收支汇总 ($baseCurrency)")
            appendLine("支出：$expenseText")
            appendLine("收入：$incomeText")
            append("结余：$balanceText")
            if (foreignCurrencySummaries.isNotEmpty()) {
                appendLine()
                append("外币明细（原币种）：")
                foreignCurrencySummaries.forEach { (code, expMicros, incMicros) ->
                    appendLine()
                    val parts = buildList {
                        if (expMicros != 0L) add("支 ${MoneyAmount.format(expMicros, code)}")
                        if (incMicros != 0L) add("收 ${MoneyAmount.format(incMicros, code)}")
                    }.ifEmpty {
                        listOf(MoneyAmount.format(0L, code))
                    }
                    append("• $code：${parts.joinToString(" / ")}")
                }
            }
        }
    }
}

/**
 * 自适应单行大额数字文本：从 [baseStyle] 字号向下步进缩放直至完全适配可用宽度，
 * 避免百万/千万/亿元级金额出现省略号；仅在极端窄屏且低于 [minFontSize] 时兜底省略。
 */
@Composable
fun AutoResizeAmountText(
    text: String,
    baseStyle: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight? = baseStyle.fontWeight,
    minFontSize: TextUnit = 9.5.sp,
    stepSp: Float = 0.5f,
    textAlign: TextAlign? = null,
    contentAlignment: Alignment = Alignment.CenterStart
) {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val resolvedBaseStyle = remember(baseStyle, fontWeight) {
        baseStyle.copy(fontWeight = fontWeight ?: baseStyle.fontWeight)
    }

    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = contentAlignment
    ) {
        val maxWidthPx = constraints.maxWidth
        val hasBoundedWidth = constraints.hasBoundedWidth
        val startSp = if (resolvedBaseStyle.fontSize != TextUnit.Unspecified) {
            resolvedBaseStyle.fontSize.value
        } else {
            16f
        }
        val minSp = if (minFontSize != TextUnit.Unspecified) {
            minFontSize.value.coerceAtMost(startSp)
        } else {
            9.5f.coerceAtMost(startSp)
        }
        val safeStep = stepSp.coerceAtLeast(0.25f)

        val (fittedSp, fits) = remember(
            text,
            maxWidthPx,
            hasBoundedWidth,
            resolvedBaseStyle,
            minSp,
            safeStep,
            textMeasurer,
            density
        ) {
            if (!hasBoundedWidth || maxWidthPx <= 0 || text.isEmpty()) {
                startSp to true
            } else {
                var currentSp = startSp
                var doesFit = false
                while (true) {
                    val candidateStyle = resolvedBaseStyle.copy(fontSize = currentSp.sp)
                    val measured = textMeasurer.measure(
                        text = text,
                        style = candidateStyle,
                        maxLines = 1,
                        softWrap = false
                    )
                    if (measured.size.width <= maxWidthPx) {
                        doesFit = true
                        break
                    }
                    if (currentSp <= minSp) {
                        doesFit = false
                        break
                    }
                    currentSp = (currentSp - safeStep).coerceAtLeast(minSp)
                }
                currentSp to doesFit
            }
        }

        Text(
            text = text,
            style = resolvedBaseStyle.copy(fontSize = fittedSp.sp),
            color = color,
            maxLines = 1,
            softWrap = false,
            overflow = if (fits) TextOverflow.Clip else TextOverflow.Ellipsis,
            textAlign = textAlign,
            modifier = if (textAlign != null) Modifier.fillMaxWidth() else Modifier
        )
    }
}

@Composable
fun YearMonthPickerDialog(
    initialYearMonth: YearMonth,
    onSelect: (YearMonth) -> Unit,
    onDismiss: () -> Unit
) {
    val currentYm = remember { YearMonth.now() }
    var displayYear by remember(initialYearMonth) { mutableIntStateOf(initialYearMonth.year) }
    var showYearGrid by remember { mutableStateOf(false) }
    var yearPageAnchor by remember(initialYearMonth) { mutableIntStateOf(initialYearMonth.year) }

    YubraeDialog(
        onDismissRequest = onDismiss,
        title = if (showYearGrid) "快速选择年份" else "选择年月",
        subtitle = "当前已选 ${initialYearMonth.year}年${initialYearMonth.monthValue}月",
        icon = Icons.Rounded.CalendarMonth,
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("取消")
            }
        },
        confirmButton = {
            FilledTonalButton(
                onClick = {
                    onSelect(currentYm)
                    onDismiss()
                },
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("回到本月")
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!showYearGrid) {
                PeriodHeaderNavBar(
                    title = "${displayYear}年",
                    showDropDown = true,
                    prevEnabled = displayYear > 1,
                    onPrev = { displayYear = (displayYear - 1).coerceAtLeast(1) },
                    onTitleClick = {
                        yearPageAnchor = displayYear
                        showYearGrid = true
                    },
                    onNext = { displayYear += 1 },
                    prevDescription = "上一年",
                    nextDescription = "下一年"
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    (1..12).chunked(4).forEach { rowMonths ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowMonths.forEach { month ->
                                val ym = YearMonth.of(displayYear, month)
                                val isSelected = ym == initialYearMonth
                                val isCurrent = ym == currentYm
                                PeriodGridCell(
                                    primaryLabel = "${month}月",
                                    secondaryLabel = if (isCurrent) "本月" else null,
                                    selected = isSelected,
                                    highlighted = isCurrent,
                                    onClick = {
                                        onSelect(ym)
                                        onDismiss()
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            } else {
                val years = remember(yearPageAnchor) {
                    PeriodPickerHelper.yearGridRange(yearPageAnchor)
                }
                PeriodHeaderNavBar(
                    title = "${years.first()} - ${years.last()}年",
                    showDropDown = true,
                    prevEnabled = years.first() >= 12,
                    onPrev = { yearPageAnchor = (years.first() - 12).coerceAtLeast(0) },
                    onTitleClick = { showYearGrid = false },
                    onNext = { yearPageAnchor = years.last() + 1 },
                    prevDescription = "上12年",
                    nextDescription = "下12年"
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    years.chunked(3).forEach { rowYears ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowYears.forEach { year ->
                                val isSelected = year == displayYear
                                val isCurrent = year == currentYm.year
                                PeriodGridCell(
                                    primaryLabel = "${year}年",
                                    secondaryLabel = if (isCurrent) "今年" else null,
                                    selected = isSelected,
                                    highlighted = isCurrent,
                                    onClick = {
                                        displayYear = year.coerceAtLeast(1)
                                        showYearGrid = false
                                    },
                                    modifier = Modifier.weight(1f)
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
fun YearPickerDialog(
    initialYear: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val currentYear = remember { YearMonth.now().year }
    var pageAnchorYear by remember(initialYear) { mutableIntStateOf(initialYear) }
    val years = remember(pageAnchorYear) { PeriodPickerHelper.yearGridRange(pageAnchorYear) }

    YubraeDialog(
        onDismissRequest = onDismiss,
        title = "选择年份",
        subtitle = "当前已选 ${initialYear}年",
        icon = Icons.Rounded.CalendarMonth,
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("取消")
            }
        },
        confirmButton = {
            FilledTonalButton(
                onClick = {
                    onSelect(currentYear)
                    onDismiss()
                },
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("回到今年")
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PeriodHeaderNavBar(
                title = "${years.first()}年 - ${years.last()}年",
                showDropDown = false,
                prevEnabled = years.first() >= 12,
                onPrev = { pageAnchorYear = (years.first() - 12).coerceAtLeast(0) },
                onTitleClick = null,
                onNext = { pageAnchorYear = years.last() + 1 },
                prevDescription = "上12年",
                nextDescription = "下12年"
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                years.chunked(3).forEach { rowYears ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowYears.forEach { year ->
                            val isSelected = year == initialYear
                            val isCurrent = year == currentYear
                            PeriodGridCell(
                                primaryLabel = "${year}年",
                                secondaryLabel = if (isCurrent) "今年" else null,
                                selected = isSelected,
                                highlighted = isCurrent,
                                onClick = {
                                    onSelect(year.coerceAtLeast(1))
                                    onDismiss()
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WeekPickerDialog(
    initialDate: LocalDate,
    onSelectWeekDate: (LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    val today = remember { LocalDate.now() }
    var selectedDate by remember(initialDate) { mutableStateOf(initialDate) }
    var displayMonth by remember(initialDate) { mutableStateOf(YearMonth.from(initialDate)) }
    var showJumpMonthDialog by remember { mutableStateOf(false) }

    val selectedWeekBounds = remember(selectedDate) { PeriodPickerHelper.weekBoundsFor(selectedDate) }
    val selectedWeekLabel = remember(selectedDate) { PeriodPickerHelper.weekLabel(selectedDate) }
    val selectedIsoWeek = remember(selectedWeekBounds) {
        val wf = WeekFields.ISO
        val start = selectedWeekBounds.first
        "${start.get(wf.weekBasedYear())}年第${start.get(wf.weekOfWeekBasedYear())}周"
    }

    val weeksInGrid = remember(displayMonth) {
        val firstDay = displayMonth.atDay(1)
        val lastDay = displayMonth.atEndOfMonth()
        val gridStart = firstDay.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val gridEnd = lastDay.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
        val rows = mutableListOf<List<LocalDate>>()
        var cursor = gridStart
        while (!cursor.isAfter(gridEnd)) {
            val row = (0L..6L).map { cursor.plusDays(it) }
            rows.add(row)
            cursor = cursor.plusWeeks(1)
        }
        rows
    }

    YubraeDialog(
        onDismissRequest = onDismiss,
        title = "选择自然周",
        subtitle = "按周一至周日划分统计周期",
        icon = Icons.Rounded.DateRange,
        dismissButton = {
            TextButton(
                onClick = {
                    selectedDate = today
                    displayMonth = YearMonth.from(today)
                },
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("回到本周")
            }
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("取消")
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSelectWeekDate(selectedDate)
                    onDismiss()
                },
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("确定")
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "所选周：$selectedWeekLabel",
                        style = MaterialTheme.typography.labelLarge.tabular(),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = selectedIsoWeek,
                        style = MaterialTheme.typography.labelSmall.tabular(),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            PeriodHeaderNavBar(
                title = "${displayMonth.year}年${displayMonth.monthValue}月",
                showDropDown = true,
                prevEnabled = true,
                onPrev = { displayMonth = displayMonth.minusMonths(1) },
                onTitleClick = { showJumpMonthDialog = true },
                onNext = { displayMonth = displayMonth.plusMonths(1) },
                prevDescription = "上个月",
                nextDescription = "下个月"
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                listOf("一", "二", "三", "四", "五", "六", "日").forEach { dayName ->
                    Text(
                        text = dayName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                weeksInGrid.forEach { weekDates ->
                    val isSelectedWeek = weekDates.first() == selectedWeekBounds.first
                    Surface(
                        shape = CircleShape,
                        color = if (isSelectedWeek) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            Color.Transparent
                        },
                        border = if (isSelectedWeek) {
                            BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
                        } else {
                            null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            weekDates.forEach { date ->
                                val isInDisplayMonth = YearMonth.from(date) == displayMonth
                                val isSelectedDay = date == selectedDate
                                val isToday = date == today
                                val textColor = when {
                                    isSelectedDay -> MaterialTheme.colorScheme.onPrimary
                                    isSelectedWeek && isInDisplayMonth -> MaterialTheme.colorScheme.onPrimaryContainer
                                    isSelectedWeek -> MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.55f)
                                    isToday -> MaterialTheme.colorScheme.primary
                                    isInDisplayMonth -> MaterialTheme.colorScheme.onSurface
                                    else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                                }

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(34.dp)
                                        .padding(horizontal = 2.dp)
                                        .clip(CircleShape)
                                        .then(
                                            when {
                                                isSelectedDay -> Modifier.background(MaterialTheme.colorScheme.primary)
                                                isToday && !isSelectedWeek -> Modifier.border(
                                                    width = 1.dp,
                                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                                                    shape = CircleShape
                                                )
                                                else -> Modifier
                                            }
                                        )
                                        .clickable { selectedDate = date },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = date.dayOfMonth.toString(),
                                        style = MaterialTheme.typography.bodyMedium.tabular(),
                                        fontWeight = if (isSelectedDay || isToday || isSelectedWeek) {
                                            FontWeight.SemiBold
                                        } else {
                                            FontWeight.Normal
                                        },
                                        color = textColor,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showJumpMonthDialog) {
        YearMonthPickerDialog(
            initialYearMonth = displayMonth,
            onSelect = { pickedYm ->
                displayMonth = pickedYm
            },
            onDismiss = { showJumpMonthDialog = false }
        )
    }
}

@Composable
fun MonthSummaryDetailDialog(
    yearMonth: YearMonth,
    baseCurrency: String,
    expenseMicros: Long,
    incomeMicros: Long,
    balanceMicros: Long,
    foreignCurrencySummaries: List<Triple<String, Long, Long>>,
    privacyMasked: Boolean,
    onCopySummary: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val semantic = KairoTheme.semantic
    val yearMonthLabel = "${yearMonth.year}年${yearMonth.monthValue}月"
    val expenseText = formatPrivacyAmount(privacyMasked, MoneyAmount.format(expenseMicros, baseCurrency))
    val incomeText = formatPrivacyAmount(privacyMasked, MoneyAmount.format(incomeMicros, baseCurrency))
    val balanceText = formatPrivacyAmount(
        privacyMasked,
        MoneyAmount.format(balanceMicros, baseCurrency, showSign = true)
    )
    val copyText = remember(
        yearMonthLabel,
        baseCurrency,
        expenseText,
        incomeText,
        balanceText,
        foreignCurrencySummaries
    ) {
        PeriodPickerHelper.formatMonthSummaryCopyText(
            yearMonthLabel = yearMonthLabel,
            baseCurrency = baseCurrency,
            expenseText = expenseText,
            incomeText = incomeText,
            balanceText = balanceText,
            foreignCurrencySummaries = foreignCurrencySummaries
        )
    }

    YubraeDialog(
        onDismissRequest = onDismiss,
        title = "${yearMonthLabel}收支详情",
        subtitle = "本位币：$baseCurrency",
        icon = Icons.Rounded.AccountBalanceWallet,
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("关闭")
            }
        },
        confirmButton = {
            Button(
                onClick = { onCopySummary(copyText) },
                enabled = !privacyMasked,
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("复制汇总")
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SummaryDetailMetricCard(
                label = "本月支出",
                amountText = expenseText,
                valueColor = semantic.expense
            )
            SummaryDetailMetricCard(
                label = "本月收入",
                amountText = incomeText,
                valueColor = semantic.income
            )
            SummaryDetailMetricCard(
                label = "本月结余",
                amountText = balanceText,
                valueColor = if (privacyMasked) {
                    MaterialTheme.colorScheme.onSurface
                } else if (balanceMicros >= 0L) {
                    semantic.income
                } else {
                    semantic.expense
                }
            )

            if (foreignCurrencySummaries.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "外币原币种明细（已折算计入 $baseCurrency）",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (privacyMasked) {
                            Text(
                                text = "****",
                                style = MaterialTheme.typography.titleMedium.tabular(),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        } else {
                            foreignCurrencySummaries.forEach { (code, expMicros, incMicros) ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = code,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    val detailText = buildList {
                                        if (expMicros != 0L) add("支 ${MoneyAmount.format(expMicros, code)}")
                                        if (incMicros != 0L) add("收 ${MoneyAmount.format(incMicros, code)}")
                                    }.ifEmpty {
                                        listOf(MoneyAmount.format(0L, code))
                                    }.joinToString("  ·  ")
                                    AutoResizeAmountText(
                                        text = detailText,
                                        baseStyle = MaterialTheme.typography.bodyMedium.tabular(),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.Medium,
                                        minFontSize = 10.sp,
                                        textAlign = TextAlign.End,
                                        contentAlignment = Alignment.CenterEnd,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (privacyMasked) {
                Text(
                    text = "已开启隐私遮罩，关闭遮罩后可查看完整金额并复制汇总",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SummaryDetailMetricCard(
    label: String,
    amountText: String,
    valueColor: Color
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            AutoResizeAmountText(
                text = amountText,
                baseStyle = MaterialTheme.typography.headlineMedium.copy(
                    fontSize = 24.sp,
                    lineHeight = 30.sp
                ).tabular(),
                fontWeight = FontWeight.Bold,
                color = valueColor,
                minFontSize = 11.sp,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun PeriodHeaderNavBar(
    title: String,
    showDropDown: Boolean,
    prevEnabled: Boolean,
    onPrev: () -> Unit,
    onTitleClick: (() -> Unit)?,
    onNext: () -> Unit,
    prevDescription: String,
    nextDescription: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(
            onClick = onPrev,
            enabled = prevEnabled,
            modifier = Modifier.size(34.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                contentDescription = prevDescription
            )
        }

        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
            modifier = Modifier
                .clip(CircleShape)
                .then(
                    if (onTitleClick != null) {
                        Modifier.clickable(onClick = onTitleClick)
                    } else {
                        Modifier
                    }
                )
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.tabular(),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (showDropDown) {
                    Icon(
                        imageVector = Icons.Rounded.ArrowDropDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        IconButton(
            onClick = onNext,
            modifier = Modifier.size(34.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = nextDescription
            )
        }
    }
}

@Composable
private fun PeriodGridCell(
    primaryLabel: String,
    secondaryLabel: String?,
    selected: Boolean,
    highlighted: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor = when {
        selected -> MaterialTheme.colorScheme.primary
        highlighted -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
        else -> MaterialTheme.colorScheme.surfaceContainerLow
    }
    val contentColor = when {
        selected -> MaterialTheme.colorScheme.onPrimary
        highlighted -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }
    val border = when {
        selected -> null
        highlighted -> BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
        else -> BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = containerColor,
        border = border,
        modifier = modifier
            .height(50.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = primaryLabel,
                style = MaterialTheme.typography.titleSmall.tabular(),
                fontWeight = if (selected || highlighted) FontWeight.Bold else FontWeight.Medium,
                color = contentColor,
                maxLines = 1
            )
            if (secondaryLabel != null) {
                Text(
                    text = secondaryLabel,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 9.5.sp,
                        lineHeight = 12.sp
                    ),
                    fontWeight = FontWeight.SemiBold,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.88f)
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    maxLines = 1
                )
            }
        }
    }
}

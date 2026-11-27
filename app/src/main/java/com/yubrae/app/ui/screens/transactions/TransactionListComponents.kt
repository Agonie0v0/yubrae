package com.yubrae.app.ui.screens.transactions

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yubrae.app.core.model.CurrencyCatalog
import com.yubrae.app.core.model.MoneyAmount
import com.yubrae.app.data.db.AccountEntity
import com.yubrae.app.data.db.CategoryEntity
import com.yubrae.app.data.db.ExchangeRateEntity
import com.yubrae.app.data.db.ReimburseStatus
import com.yubrae.app.data.db.TagEntity
import com.yubrae.app.data.db.TagTextCodec
import com.yubrae.app.data.db.TransactionEntity
import com.yubrae.app.data.db.TransactionStatus
import com.yubrae.app.data.db.TransactionType
import com.yubrae.app.ui.components.AutoResizeAmountText
import com.yubrae.app.ui.components.CategoryIconBadge
import com.yubrae.app.ui.components.CompactHairlineDivider
import com.yubrae.app.ui.components.KairoFormats
import com.yubrae.app.ui.components.KairoIcons
import com.yubrae.app.ui.components.YimuCard
import com.yubrae.app.ui.components.formatPrivacyAmount
import com.yubrae.app.ui.components.transactionAmountColor
import com.yubrae.app.ui.theme.KairoTheme
import com.yubrae.app.ui.theme.tabular
import com.yubrae.app.ui.viewmodel.DayTransactionGroup
import com.yubrae.app.ui.viewmodel.KairoMainViewModel
import java.time.YearMonth
import kotlin.math.abs

private val CardSingleShape = RoundedCornerShape(14.dp)
private val CardTopShape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp)
private val CardBottomShape = RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp)
private val MicroBadgeShape = RoundedCornerShape(4.dp)
private val InlineTagShape = RoundedCornerShape(4.dp)
private val BannerCardShape = RoundedCornerShape(12.dp)
private val PendingItemShape = RoundedCornerShape(10.dp)
private val HeaderActionShape = RoundedCornerShape(8.dp)
private val SummaryClickShape = RoundedCornerShape(10.dp)

private data class RowMicroBadgeSpec(
    val text: String,
    val containerColor: Color,
    val contentColor: Color
)

private enum class CardSlicePosition {
    SINGLE, TOP, MIDDLE, BOTTOM
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TransactionsMinimalHeader(
    activeLedgerLabel: String,
    activeLedgerIconKey: String?,
    yearMonth: YearMonth,
    baseCurrency: String,
    expenseMicros: Long,
    incomeMicros: Long,
    balanceMicros: Long,
    monthDiscountBaseMicros: Long = 0L,
    monthFeeBaseMicros: Long = 0L,
    foreignCurrencySummaries: List<Triple<String, Long, Long>>,
    privacyMasked: Boolean,
    viewMode: TxViewMode,
    searchExpanded: Boolean,
    hasActiveFilter: Boolean,
    applyStatusBarPadding: Boolean,
    onOpenLedgerManager: () -> Unit,
    onToggleSearch: () -> Unit,
    onTogglePrivacy: () -> Unit,
    onOpenSmartInput: () -> Unit,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onOpenMonthPicker: () -> Unit,
    onOpenSummaryDetail: () -> Unit,
    onToggleViewMode: () -> Unit
) {
    val semantic = KairoTheme.semantic
    val colorScheme = MaterialTheme.colorScheme
    val currentMonth = remember { YearMonth.now() }
    val isCurrentMonth = yearMonth == currentMonth
    val shortMonthLabel = remember(yearMonth, currentMonth.year) {
        if (yearMonth.year == currentMonth.year) {
            "${yearMonth.monthValue}月"
        } else {
            "${yearMonth.year % 100}年${yearMonth.monthValue}月"
        }
    }
    val showNonCnyLabel = !baseCurrency.equals("CNY", ignoreCase = true)
    val expenseText = remember(expenseMicros, baseCurrency, privacyMasked) {
        formatPrivacyAmount(
            privacyMasked,
            KairoFormats.formatPlainSignedAmount(expenseMicros, baseCurrency, baseCurrency, showSign = false)
        )
    }
    val incomeText = remember(incomeMicros, baseCurrency, privacyMasked) {
        formatPrivacyAmount(
            privacyMasked,
            KairoFormats.formatPlainSignedAmount(incomeMicros, baseCurrency, baseCurrency, showSign = false)
        )
    }
    val balanceText = remember(balanceMicros, baseCurrency, privacyMasked) {
        formatPrivacyAmount(
            privacyMasked,
            KairoFormats.formatPlainSignedAmount(balanceMicros, baseCurrency, baseCurrency, showSign = true)
        )
    }
    val metaLine = remember(
        foreignCurrencySummaries,
        monthDiscountBaseMicros,
        monthFeeBaseMicros,
        baseCurrency,
        privacyMasked
    ) {
        buildHeaderMetaFootnote(
            foreignCurrencySummaries = foreignCurrencySummaries,
            monthDiscountBaseMicros = monthDiscountBaseMicros,
            monthFeeBaseMicros = monthFeeBaseMicros,
            baseCurrency = baseCurrency,
            privacyMasked = privacyMasked
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (applyStatusBarPadding) Modifier.statusBarsPadding() else Modifier)
                .padding(start = 14.dp, end = 10.dp, top = 6.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 第 1 行：极简导航（左账本、中月份切换、右日历/搜索/AI速记）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 36.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .clip(HeaderActionShape)
                        .clickable(onClick = onOpenLedgerManager)
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(
                        imageVector = activeLedgerIconKey?.let { KairoIcons.ledgerIcon(it) }
                            ?: Icons.AutoMirrored.Rounded.MenuBook,
                        contentDescription = null,
                        tint = colorScheme.onSurface,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = activeLedgerLabel,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontSize = 13.5.sp,
                            lineHeight = 18.sp
                        ),
                        fontWeight = FontWeight.SemiBold,
                        color = colorScheme.onSurface,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 82.dp)
                    )
                    Icon(
                        imageVector = Icons.Rounded.ArrowDropDown,
                        contentDescription = "切换账本",
                        tint = colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }

                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .clickable(onClick = onPrevMonth),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                                contentDescription = "上个月",
                                tint = colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Row(
                            modifier = Modifier
                                .clip(HeaderActionShape)
                                .clickable(onClick = onOpenMonthPicker)
                                .padding(horizontal = 6.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(
                                text = shortMonthLabel,
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontSize = 14.5.sp,
                                    lineHeight = 19.sp
                                ).tabular(),
                                fontWeight = FontWeight.SemiBold,
                                color = if (!isCurrentMonth) colorScheme.primary else colorScheme.onSurface,
                                maxLines = 1,
                                softWrap = false
                            )
                            if (!isCurrentMonth) {
                                Box(
                                    modifier = Modifier
                                        .size(4.dp)
                                        .clip(CircleShape)
                                        .background(colorScheme.primary)
                                )
                            }
                            Icon(
                                imageVector = Icons.Rounded.ArrowDropDown,
                                contentDescription = "选择年月",
                                tint = if (!isCurrentMonth) colorScheme.primary else colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .clickable(onClick = onNextMonth),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                contentDescription = "下个月",
                                tint = colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    MinimalHeaderIconButton(
                        icon = if (viewMode == TxViewMode.LIST) {
                            Icons.Rounded.CalendarMonth
                        } else {
                            Icons.AutoMirrored.Rounded.ViewList
                        },
                        contentDescription = if (viewMode == TxViewMode.LIST) "切换日历视图" else "切换列表视图",
                        active = viewMode == TxViewMode.CALENDAR,
                        onClick = onToggleViewMode
                    )
                    MinimalHeaderIconButton(
                        icon = Icons.Rounded.Search,
                        contentDescription = if (searchExpanded) "收起搜索筛选" else "展开搜索筛选",
                        active = searchExpanded || hasActiveFilter,
                        onClick = onToggleSearch
                    )
                    MinimalHeaderIconButton(
                        icon = Icons.Rounded.AutoAwesome,
                        contentDescription = "AI/语音智能记账",
                        active = false,
                        onClick = onOpenSmartInput
                    )
                }
            }

            // 第 2 行：三列极简月度汇总（支出 / 收入 / 结余）+ 右侧静默隐私眼
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(SummaryClickShape)
                        .combinedClickable(
                            onClick = onOpenSummaryDetail,
                            onLongClick = onOpenSummaryDetail
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MinimalSummaryMetricColumn(
                        label = if (showNonCnyLabel) "支出 · $baseCurrency" else "支出",
                        amountText = expenseText,
                        valueColor = semantic.expense,
                        modifier = Modifier.weight(1f)
                    )
                    MinimalSummaryMetricColumn(
                        label = "收入",
                        amountText = incomeText,
                        valueColor = semantic.income,
                        modifier = Modifier.weight(1f)
                    )
                    MinimalSummaryMetricColumn(
                        label = "结余",
                        amountText = balanceText,
                        valueColor = colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onTogglePrivacy),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (privacyMasked) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        contentDescription = if (privacyMasked) "关闭隐私遮罩" else "开启隐私遮罩",
                        tint = colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // 可选微信息行（外币 / 省 / 费）：全为 0 时零占位
            if (metaLine.isNotEmpty()) {
                Text(
                    text = metaLine,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        lineHeight = 14.sp
                    ).tabular(),
                    color = colorScheme.onSurfaceVariant.copy(alpha = 0.80f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 2.dp, end = 4.dp)
                )
            }
        }

        HorizontalDivider(
            thickness = 0.5.dp,
            color = colorScheme.outlineVariant.copy(alpha = 0.42f)
        )
    }
}

private fun buildHeaderMetaFootnote(
    foreignCurrencySummaries: List<Triple<String, Long, Long>>,
    monthDiscountBaseMicros: Long,
    monthFeeBaseMicros: Long,
    baseCurrency: String,
    privacyMasked: Boolean
): String {
    val parts = buildList {
        foreignCurrencySummaries.forEach { (code, exp, inc) ->
            if (privacyMasked) {
                add("$code ****")
            } else {
                val digits = CurrencyCatalog.find(code).minorDigits
                val text = when {
                    exp > 0L && inc > 0L ->
                        "$code -${MoneyAmount.formatPlain(exp, digits, true)} / +${MoneyAmount.formatPlain(inc, digits, true)}"
                    exp > 0L -> "$code -${MoneyAmount.formatPlain(exp, digits, true)}"
                    else -> "$code +${MoneyAmount.formatPlain(inc, digits, true)}"
                }
                add(text)
            }
        }
        if (monthDiscountBaseMicros > 0L) {
            val discountAmt = formatPrivacyAmount(
                privacyMasked,
                KairoFormats.formatPlainSignedAmount(monthDiscountBaseMicros, baseCurrency, baseCurrency, showSign = false)
            )
            add("省 $discountAmt")
        }
        if (monthFeeBaseMicros > 0L) {
            val feeAmt = formatPrivacyAmount(
                privacyMasked,
                KairoFormats.formatPlainSignedAmount(monthFeeBaseMicros, baseCurrency, baseCurrency, showSign = false)
            )
            add("费 $feeAmt")
        }
    }
    return parts.joinToString(" · ")
}

@Composable
private fun MinimalHeaderIconButton(
    icon: ImageVector,
    contentDescription: String,
    active: Boolean,
    onClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .then(
                if (active) Modifier.background(colorScheme.primary.copy(alpha = 0.11f)) else Modifier
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (active) colorScheme.primary else colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun MinimalSummaryMetricColumn(
    label: String,
    amountText: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(end = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.sp,
                lineHeight = 14.sp
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.80f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        AutoResizeAmountText(
            text = amountText,
            baseStyle = MaterialTheme.typography.titleMedium.copy(
                fontSize = 17.5.sp,
                lineHeight = 22.sp,
                letterSpacing = (-0.25).sp
            ).tabular(),
            fontWeight = FontWeight.SemiBold,
            color = valueColor,
            minFontSize = 10.sp,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
internal fun DaySectionHeader(
    group: DayTransactionGroup,
    baseCurrency: String,
    privacyMasked: Boolean,
    modifier: Modifier = Modifier
) {
    val semantic = KairoTheme.semantic
    val (dateText, subText) = remember(group.date) { KairoFormats.formatDayHeaderParts(group.date) }
    val incomeText = remember(group.dayIncomeBaseMicros, baseCurrency, privacyMasked) {
        if (group.dayIncomeBaseMicros > 0L) {
            "收 ${formatPrivacyAmount(privacyMasked, KairoFormats.formatPlainSignedAmount(group.dayIncomeBaseMicros, baseCurrency, baseCurrency, showSign = false))}"
        } else {
            null
        }
    }
    val expenseText = remember(group.dayExpenseBaseMicros, baseCurrency, privacyMasked) {
        if (group.dayExpenseBaseMicros > 0L) {
            "支 ${formatPrivacyAmount(privacyMasked, KairoFormats.formatPlainSignedAmount(group.dayExpenseBaseMicros, baseCurrency, baseCurrency, showSign = false))}"
        } else {
            null
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = dateText,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontSize = 13.sp,
                    lineHeight = 17.sp
                ),
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                softWrap = false
            )
            if (subText.isNotEmpty()) {
                Text(
                    text = subText,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    ),
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (incomeText != null) {
                Text(
                    text = incomeText,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    ).tabular(),
                    color = semantic.income.copy(alpha = 0.86f),
                    maxLines = 1,
                    softWrap = false
                )
            }
            if (expenseText != null) {
                Text(
                    text = expenseText,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    ).tabular(),
                    color = semantic.expense.copy(alpha = 0.86f),
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

private fun Modifier.dayCardSliceBorder(
    position: CardSlicePosition,
    borderColor: Color,
    cornerRadius: Dp = 14.dp
): Modifier = this.drawWithCache {
    val strokePx = 0.5.dp.toPx()
    val half = strokePx / 2f
    val w = size.width
    val h = size.height
    if (w <= strokePx || h <= strokePx) {
        return@drawWithCache onDrawWithContent { drawContent() }
    }
    val r = cornerRadius.toPx().coerceAtMost((minOf(w, h) - strokePx).coerceAtLeast(0f) / 2f)
    val stroke = Stroke(width = strokePx)
    when (position) {
        CardSlicePosition.SINGLE -> {
            val topLeft = Offset(half, half)
            val rectSize = Size(w - strokePx, h - strokePx)
            val corner = CornerRadius(r, r)
            onDrawWithContent {
                drawContent()
                drawRoundRect(
                    color = borderColor,
                    topLeft = topLeft,
                    size = rectSize,
                    cornerRadius = corner,
                    style = stroke
                )
            }
        }
        CardSlicePosition.TOP -> {
            val path = Path().apply {
                moveTo(half, h)
                lineTo(half, half + r)
                arcTo(
                    rect = Rect(half, half, half + 2f * r, half + 2f * r),
                    startAngleDegrees = 180f,
                    sweepAngleDegrees = 90f,
                    forceMoveTo = false
                )
                lineTo(w - half - r, half)
                arcTo(
                    rect = Rect(w - half - 2f * r, half, w - half, half + 2f * r),
                    startAngleDegrees = 270f,
                    sweepAngleDegrees = 90f,
                    forceMoveTo = false
                )
                lineTo(w - half, h)
            }
            onDrawWithContent {
                drawContent()
                drawPath(path = path, color = borderColor, style = stroke)
            }
        }
        CardSlicePosition.MIDDLE -> {
            val leftStart = Offset(half, 0f)
            val leftEnd = Offset(half, h)
            val rightStart = Offset(w - half, 0f)
            val rightEnd = Offset(w - half, h)
            onDrawWithContent {
                drawContent()
                drawLine(borderColor, leftStart, leftEnd, strokeWidth = strokePx)
                drawLine(borderColor, rightStart, rightEnd, strokeWidth = strokePx)
            }
        }
        CardSlicePosition.BOTTOM -> {
            val path = Path().apply {
                moveTo(half, 0f)
                lineTo(half, h - half - r)
                arcTo(
                    rect = Rect(half, h - half - 2f * r, half + 2f * r, h - half),
                    startAngleDegrees = 180f,
                    sweepAngleDegrees = -90f,
                    forceMoveTo = false
                )
                lineTo(w - half - r, h - half)
                arcTo(
                    rect = Rect(w - half - 2f * r, h - half - 2f * r, w - half, h - half),
                    startAngleDegrees = 90f,
                    sweepAngleDegrees = -90f,
                    forceMoveTo = false
                )
                lineTo(w - half, 0f)
            }
            onDrawWithContent {
                drawContent()
                drawPath(path = path, color = borderColor, style = stroke)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun FlattenedTransactionRow(
    tx: TransactionEntity,
    baseCurrency: String,
    convertedBaseMicros: Long,
    refundedMicros: Long,
    account: AccountEntity?,
    toAccount: AccountEntity?,
    category: CategoryEntity?,
    parentCategory: CategoryEntity?,
    tagMap: Map<String, TagEntity>,
    privacyMasked: Boolean,
    isMultiSelectMode: Boolean,
    isSelected: Boolean,
    isFirstInDay: Boolean,
    isLastInDay: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val slicePosition = when {
        isFirstInDay && isLastInDay -> CardSlicePosition.SINGLE
        isFirstInDay -> CardSlicePosition.TOP
        isLastInDay -> CardSlicePosition.BOTTOM
        else -> CardSlicePosition.MIDDLE
    }
    val rowShape = when (slicePosition) {
        CardSlicePosition.SINGLE -> CardSingleShape
        CardSlicePosition.TOP -> CardTopShape
        CardSlicePosition.MIDDLE -> RectangleShape
        CardSlicePosition.BOTTOM -> CardBottomShape
    }
    val rowBg = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.24f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerLowest
    }
    val borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.30f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(rowShape)
            .background(rowBg)
            .dayCardSliceBorder(slicePosition, borderColor, 14.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        TransactionRowItem(
            tx = tx,
            baseCurrency = baseCurrency,
            convertedBaseMicros = convertedBaseMicros,
            refundedMicros = refundedMicros,
            account = account,
            toAccount = toAccount,
            category = category,
            parentCategory = parentCategory,
            tagMap = tagMap,
            privacyMasked = privacyMasked,
            isMultiSelectMode = isMultiSelectMode,
            isSelected = isSelected
        )
        if (!isLastInDay) {
            CompactHairlineDivider(
                modifier = Modifier.padding(end = 12.dp),
                startIndent = 54.dp
            )
        }
    }
}

@Composable
internal fun TransactionRowItem(
    tx: TransactionEntity,
    baseCurrency: String,
    convertedBaseMicros: Long,
    refundedMicros: Long,
    account: AccountEntity?,
    toAccount: AccountEntity?,
    category: CategoryEntity?,
    parentCategory: CategoryEntity?,
    tagMap: Map<String, TagEntity>,
    privacyMasked: Boolean,
    isMultiSelectMode: Boolean,
    isSelected: Boolean
) {
    val semantic = KairoTheme.semantic
    val colorScheme = MaterialTheme.colorScheme

    val iconKey = when (tx.type) {
        TransactionType.TRANSFER -> "swap_horiz"
        TransactionType.ADJUST -> "tune"
        else -> category?.icon ?: "category"
    }
    val iconColorHex = when (tx.type) {
        TransactionType.TRANSFER -> 0xFF3B82F6L
        TransactionType.ADJUST -> 0xFF6D28D9L
        else -> category?.colorHex ?: 0xFF2AA876L
    }

    val line1Title = remember(tx.type, category?.name, parentCategory?.name, account?.name, toAccount?.name) {
        when (tx.type) {
            TransactionType.TRANSFER -> "${account?.name ?: "账户"} → ${toAccount?.name ?: "目标账户"}"
            TransactionType.ADJUST -> "余额校准"
            else -> when {
                category != null && parentCategory != null -> "${parentCategory.name}-${category.name}"
                category != null -> category.name
                else -> KairoFormats.transactionTypeLabel(tx.type)
            }
        }
    }

    val hasLinkedRefund = tx.type == TransactionType.EXPENSE && refundedMicros > 0L
    val microBadges = buildList {
        if (hasLinkedRefund) {
            val amt = formatPrivacyAmount(
                privacyMasked,
                KairoFormats.formatPlainSignedAmount(refundedMicros, tx.origCurrency, baseCurrency, showSign = false)
            )
            add(RowMicroBadgeSpec("退$amt", semantic.warningContainer.copy(alpha = 0.76f), semantic.warning))
        }
        if (tx.discountAmountMicros > 0L && size < 2) {
            val amt = formatPrivacyAmount(
                privacyMasked,
                KairoFormats.formatPlainSignedAmount(tx.discountAmountMicros, tx.origCurrency, baseCurrency, showSign = false)
            )
            add(RowMicroBadgeSpec("惠$amt", semantic.incomeContainer.copy(alpha = 0.76f), semantic.income))
        }
        if (tx.feeAmountMicros > 0L && size < 2) {
            val amt = formatPrivacyAmount(
                privacyMasked,
                KairoFormats.formatPlainSignedAmount(tx.feeAmountMicros, tx.origCurrency, baseCurrency, showSign = false)
            )
            add(RowMicroBadgeSpec("费$amt", semantic.warningContainer.copy(alpha = 0.76f), semantic.warning))
        }
        if (tx.status == TransactionStatus.PENDING && size < 2) {
            add(RowMicroBadgeSpec("待确认", semantic.warningContainer.copy(alpha = 0.76f), semantic.warning))
        }
        if (tx.reimburseStatus == ReimburseStatus.PENDING && size < 2) {
            add(RowMicroBadgeSpec("待报销", colorScheme.primaryContainer.copy(alpha = 0.68f), colorScheme.primary))
        }
        if (tx.refundOfTxId != null && size < 2) {
            add(RowMicroBadgeSpec("退款", colorScheme.primaryContainer.copy(alpha = 0.68f), colorScheme.primary))
        }
    }

    val timeText = remember(tx.timestamp) { KairoFormats.formatTime(tx.timestamp) }
    val hasAttachments = remember(tx.attachmentPaths) { tx.attachmentPaths.isNotBlank() }
    val inlineTags = remember(tx.tags) { TagTextCodec.parse(tx.tags).take(2) }
    val detailText = remember(
        tx.merchant,
        tx.note,
        line1Title,
        category?.name,
        parentCategory?.name,
        account?.name,
        toAccount?.name
    ) {
        buildCompactRowDetail(
            merchant = tx.merchant,
            rawNote = tx.note,
            line1Title = line1Title,
            categoryName = category?.name,
            parentCategoryName = parentCategory?.name,
            accountName = account?.name,
            toAccountName = toAccount?.name
        )
    }

    val isForeign = !tx.origCurrency.equals(baseCurrency, ignoreCase = true)
    val transferArrivalText = remember(
        tx.type,
        tx.toCurrency,
        tx.toAmountMicros,
        tx.origCurrency,
        tx.origAmountMicros,
        tx.discountAmountMicros,
        tx.feeAmountMicros,
        baseCurrency,
        privacyMasked
    ) {
        if (tx.type != TransactionType.TRANSFER || tx.toAmountMicros == null) return@remember null
        val toCur = tx.toCurrency ?: tx.origCurrency
        val hasDiff = tx.discountAmountMicros > 0L ||
            tx.feeAmountMicros > 0L ||
            tx.toAmountMicros != tx.origAmountMicros ||
            !toCur.equals(tx.origCurrency, ignoreCase = true)
        if (!hasDiff) return@remember null
        val crossCurrency = !toCur.equals(tx.origCurrency, ignoreCase = true)
        val formattedTo = if (crossCurrency) {
            MoneyAmount.format(
                tx.toAmountMicros,
                toCur,
                showCurrencyCode = !toCur.equals("CNY", ignoreCase = true)
            )
        } else {
            KairoFormats.formatPlainSignedAmount(tx.toAmountMicros, toCur, baseCurrency, showSign = false)
        }
        "到账 ${formatPrivacyAmount(privacyMasked, formattedTo)}"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isMultiSelectMode) {
            Icon(
                imageVector = if (isSelected) Icons.Rounded.CheckBox else Icons.Rounded.CheckBoxOutlineBlank,
                contentDescription = if (isSelected) "已选中" else "未选中",
                tint = if (isSelected) colorScheme.primary else colorScheme.onSurfaceVariant,
                modifier = Modifier.size(19.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }

        CategoryIconBadge(
            iconKey = iconKey,
            colorHex = iconColorHex,
            size = 34.dp,
            iconSize = 17.dp,
            selected = isMultiSelectMode && isSelected
        )

        Spacer(modifier = Modifier.width(10.dp))

        // 中列：严格 2 行（Line 1 分类/转账 + 至多 2 枚微标；Line 2 时间 + 附件 + 至多 2 枚内联标签 + 精简商户/备注）
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = line1Title,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontSize = 14.sp,
                        lineHeight = 19.sp
                    ),
                    fontWeight = FontWeight.Medium,
                    color = colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                microBadges.forEach { badge ->
                    TxFlatMicroBadge(
                        text = badge.text,
                        containerColor = badge.containerColor,
                        contentColor = badge.contentColor
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = timeText,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 11.5.sp,
                        lineHeight = 15.sp
                    ).tabular(),
                    color = colorScheme.onSurfaceVariant.copy(alpha = 0.82f),
                    maxLines = 1,
                    softWrap = false
                )
                if (hasAttachments) {
                    Icon(
                        imageVector = Icons.Rounded.AttachFile,
                        contentDescription = "含票据附件",
                        tint = colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                        modifier = Modifier.size(12.dp)
                    )
                }
                inlineTags.forEach { tagName ->
                    val tagColorHex = tagMap[tagName]?.colorHex ?: KairoIcons.fallbackTagColorHex(tagName)
                    TxInlineFlatTag(
                        name = tagName,
                        colorHex = tagColorHex
                    )
                }
                if (detailText.isNotEmpty()) {
                    val displayDetail = if (!hasAttachments && inlineTags.isEmpty()) "· $detailText" else detailText
                    Text(
                        text = displayDetail,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.5.sp,
                            lineHeight = 15.sp
                        ),
                        color = colorScheme.onSurfaceVariant.copy(alpha = 0.82f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        // 右列：严格 2 行（Line 1 主金额/退款划原额+净额；Line 2 账户名或转账到账）
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            if (hasLinkedRefund) {
                if (privacyMasked) {
                    Text(
                        text = "****",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 15.sp,
                            lineHeight = 19.sp
                        ).tabular(),
                        fontWeight = FontWeight.SemiBold,
                        color = semantic.expense,
                        maxLines = 1,
                        softWrap = false
                    )
                } else {
                    val origAbsMicros = abs(tx.origAmountMicros)
                    val netMicros = (origAbsMicros - refundedMicros).coerceAtLeast(0L)
                    val origText = remember(origAbsMicros, tx.origCurrency, baseCurrency) {
                        KairoFormats.formatPlainSignedAmount(origAbsMicros, tx.origCurrency, baseCurrency, showSign = false)
                    }
                    val netText = remember(netMicros, tx.origCurrency, baseCurrency) {
                        if (netMicros == 0L) {
                            "-${KairoFormats.formatPlainSignedAmount(0L, tx.origCurrency, baseCurrency, showSign = false)}"
                        } else {
                            KairoFormats.formatPlainSignedAmount(-netMicros, tx.origCurrency, baseCurrency, showSign = false)
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = origText,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                                textDecoration = TextDecoration.LineThrough
                            ).tabular(),
                            color = colorScheme.onSurfaceVariant.copy(alpha = 0.62f),
                            maxLines = 1,
                            softWrap = false
                        )
                        Text(
                            text = netText,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = 15.sp,
                                lineHeight = 19.sp
                            ).tabular(),
                            fontWeight = FontWeight.SemiBold,
                            color = semantic.expense,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            } else {
                val signedMicros = when (tx.type) {
                    TransactionType.EXPENSE -> -abs(tx.origAmountMicros)
                    TransactionType.INCOME -> abs(tx.origAmountMicros)
                    TransactionType.TRANSFER -> abs(tx.origAmountMicros)
                    else -> tx.origAmountMicros
                }
                val showSign = tx.type != TransactionType.TRANSFER
                val primaryAmountText = remember(signedMicros, tx.origCurrency, baseCurrency, showSign, privacyMasked) {
                    formatPrivacyAmount(
                        privacyMasked,
                        KairoFormats.formatPlainSignedAmount(
                            amountMicros = signedMicros,
                            currencyCode = tx.origCurrency,
                            baseCurrency = baseCurrency,
                            showSign = showSign
                        )
                    )
                }
                Text(
                    text = primaryAmountText,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 15.sp,
                        lineHeight = 19.sp
                    ).tabular(),
                    fontWeight = FontWeight.SemiBold,
                    color = transactionAmountColor(tx.type),
                    maxLines = 1,
                    softWrap = false
                )
            }

            if (tx.type == TransactionType.TRANSFER) {
                if (transferArrivalText != null) {
                    Text(
                        text = transferArrivalText,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        ).tabular(),
                        color = colorScheme.onSurfaceVariant.copy(alpha = 0.82f),
                        maxLines = 1,
                        softWrap = false
                    )
                }
            } else {
                val accountColor = Color(account?.colorHex ?: 0xFF6B7D9EL)
                val accountName = account?.name ?: "账户"
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (isForeign) {
                        val convText = remember(convertedBaseMicros, baseCurrency, privacyMasked) {
                            "≈ ${formatPrivacyAmount(privacyMasked, KairoFormats.formatPlainSignedAmount(abs(convertedBaseMicros), baseCurrency, baseCurrency, showSign = false))}"
                        }
                        Text(
                            text = convText,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            ).tabular(),
                            color = colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(accountColor.copy(alpha = 0.85f))
                    )
                    Text(
                        text = accountName,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        ),
                        color = colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = if (isForeign) 64.dp else 96.dp)
                    )
                }
            }
        }
    }
}

private fun buildCompactRowDetail(
    merchant: String,
    rawNote: String,
    line1Title: String,
    categoryName: String?,
    parentCategoryName: String?,
    accountName: String?,
    toAccountName: String?
): String {
    val redundant = buildSet {
        add(line1Title.trim().lowercase())
        categoryName?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }?.let { add(it) }
        parentCategoryName?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }?.let { add(it) }
        accountName?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }?.let { add(it) }
        toAccountName?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }?.let { add(it) }
    }
    val merchantClean = merchant.trim().takeIf { it.isNotEmpty() && it.lowercase() !in redundant }
    val noteClean = KairoFormats.cleanListRowNote(rawNote).takeIf { it.isNotEmpty() && it.lowercase() !in redundant }
    return when {
        merchantClean != null && noteClean != null -> {
            when {
                noteClean.contains(merchantClean, ignoreCase = true) -> noteClean
                merchantClean.contains(noteClean, ignoreCase = true) -> merchantClean
                else -> "$merchantClean · $noteClean"
            }
        }
        merchantClean != null -> merchantClean
        noteClean != null -> noteClean
        else -> ""
    }
}

@Composable
private fun TxFlatMicroBadge(
    text: String,
    containerColor: Color,
    contentColor: Color
) {
    Surface(
        shape = MicroBadgeShape,
        color = containerColor
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                lineHeight = 13.sp
            ).tabular(),
            fontWeight = FontWeight.Medium,
            color = contentColor,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
        )
    }
}

@Composable
private fun TxInlineFlatTag(
    name: String,
    colorHex: Long
) {
    val tint = Color(colorHex)
    Surface(
        shape = InlineTagShape,
        color = tint.copy(alpha = 0.11f)
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.5.sp,
                lineHeight = 14.sp
            ),
            color = tint,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .widthIn(max = 56.dp)
                .padding(horizontal = 4.dp, vertical = 0.5.dp)
        )
    }
}

@Composable
internal fun PendingInboxBanner(
    pendingCount: Int,
    expanded: Boolean,
    pendingTransactions: List<TransactionEntity>,
    baseAmountByTxId: Map<Long, Long>,
    accountMap: Map<Long, AccountEntity>,
    categoryMap: Map<Long, CategoryEntity>,
    baseCurrency: String,
    rates: List<ExchangeRateEntity>,
    privacyMasked: Boolean,
    viewModel: KairoMainViewModel,
    onToggleExpand: () -> Unit,
    onConfirmAll: () -> Unit,
    onConfirmOne: (TransactionEntity) -> Unit,
    onEditOne: (TransactionEntity) -> Unit,
    onDeleteOne: (TransactionEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val semantic = KairoTheme.semantic
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        shape = BannerCardShape,
        color = semantic.warningContainer.copy(alpha = 0.52f),
        border = BorderStroke(0.5.dp, semantic.warning.copy(alpha = 0.30f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    modifier = Modifier
                        .weight(1f)
                        .clip(HeaderActionShape)
                        .clickable(onClick = onToggleExpand)
                        .padding(vertical = 3.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(semantic.warning)
                    )
                    Text(
                        text = "$pendingCount 笔待确认",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontSize = 13.sp,
                            lineHeight = 17.sp
                        ).tabular(),
                        fontWeight = FontWeight.SemiBold,
                        color = colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = onConfirmAll,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Icon(Icons.Rounded.DoneAll, null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "全部确认",
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                    IconButton(
                        onClick = onToggleExpand,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                            contentDescription = if (expanded) "收起" else "展开",
                            tint = colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    pendingTransactions.forEach { tx ->
                        val cat = tx.categoryId?.let { categoryMap[it] }
                        val parentCat = cat?.parentId?.let { categoryMap[it] }
                        val acc = accountMap[tx.accountId]
                        val title = when {
                            tx.merchant.isNotBlank() -> tx.merchant
                            cat != null && parentCat != null -> "${parentCat.name}-${cat.name}"
                            cat != null -> cat.name
                            else -> KairoFormats.transactionTypeLabel(tx.type)
                        }
                        val cleanNote = remember(tx.note) { KairoFormats.cleanListRowNote(tx.note) }
                        val subLine = remember(tx.timestamp, acc?.name, cleanNote) {
                            buildList {
                                add(KairoFormats.formatDateTime(tx.timestamp))
                                acc?.name?.takeIf { it.isNotBlank() }?.let { add(it) }
                                cleanNote.takeIf { it.isNotEmpty() }?.let { add(it) }
                            }.joinToString(" · ")
                        }
                        val signedMicros = when (tx.type) {
                            TransactionType.EXPENSE -> -abs(tx.origAmountMicros)
                            TransactionType.INCOME -> abs(tx.origAmountMicros)
                            TransactionType.TRANSFER -> abs(tx.origAmountMicros)
                            else -> tx.origAmountMicros
                        }
                        val amountText = formatPrivacyAmount(
                            privacyMasked,
                            KairoFormats.formatPlainSignedAmount(
                                amountMicros = signedMicros,
                                currencyCode = tx.origCurrency,
                                baseCurrency = baseCurrency,
                                showSign = tx.type != TransactionType.TRANSFER
                            )
                        )
                        Surface(
                            shape = PendingItemShape,
                            color = colorScheme.surfaceContainerLowest,
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
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Text(
                                            text = title,
                                            style = MaterialTheme.typography.titleSmall.copy(fontSize = 13.5.sp),
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = subLine,
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp).tabular(),
                                            color = colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = amountText,
                                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.5.sp).tabular(),
                                            color = transactionAmountColor(tx.type),
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                        if (!tx.origCurrency.equals(baseCurrency, true)) {
                                            val baseAmt = baseAmountByTxId[tx.id]
                                                ?: viewModel.convertTxToDisplayBase(tx, baseCurrency, rates)
                                            Text(
                                                text = "≈ ${formatPrivacyAmount(privacyMasked, KairoFormats.formatPlainSignedAmount(abs(baseAmt), baseCurrency, baseCurrency, showSign = false))}",
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp).tabular(),
                                                color = colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                        }
                                    }
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextButton(
                                        onClick = { onDeleteOne(tx) },
                                        colors = ButtonDefaults.textButtonColors(contentColor = colorScheme.error),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                        modifier = Modifier.height(30.dp)
                                    ) {
                                        Text("删除", style = MaterialTheme.typography.labelMedium, maxLines = 1, softWrap = false)
                                    }
                                    OutlinedButton(
                                        onClick = { onEditOne(tx) },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                        modifier = Modifier.height(30.dp)
                                    ) {
                                        Text("编辑", style = MaterialTheme.typography.labelMedium, maxLines = 1, softWrap = false)
                                    }
                                    Button(
                                        onClick = { onConfirmOne(tx) },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                        modifier = Modifier.height(30.dp)
                                    ) {
                                        Text("确认", style = MaterialTheme.typography.labelMedium, maxLines = 1, softWrap = false)
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
internal fun BatchSelectionToolbar(
    selectedCount: Int,
    totalVisibleCount: Int,
    allSelected: Boolean,
    onSelectAllToggle: () -> Unit,
    onInvertSelection: () -> Unit,
    onBatchCategory: () -> Unit,
    onBatchDelete: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    YimuCard(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        cornerRadius = 14.dp
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    IconButton(onClick = onExit, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "退出多选",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Text(
                        text = "已选 $selectedCount / $totalVisibleCount 笔",
                        style = MaterialTheme.typography.titleSmall.tabular(),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = onSelectAllToggle,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Icon(Icons.Rounded.SelectAll, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(if (allSelected) "全不选" else "全选")
                    }
                    TextButton(
                        onClick = onInvertSelection,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text("反选")
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onBatchCategory,
                    enabled = selectedCount > 0,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Rounded.Edit, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("批量改分类")
                }
                Button(
                    onClick = onBatchDelete,
                    enabled = selectedCount > 0,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Rounded.Delete, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("批量删除")
                }
            }
        }
    }
}

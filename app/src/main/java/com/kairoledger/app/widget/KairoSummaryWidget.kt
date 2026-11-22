package com.kairoledger.app.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.kairoledger.app.KairoApplication
import com.kairoledger.app.MainActivity
import com.kairoledger.app.core.contract.IntentConstants
import com.kairoledger.app.core.model.CurrencyCatalog
import com.kairoledger.app.core.model.MoneyAmount
import com.kairoledger.app.data.db.ExchangeRateDao
import com.kairoledger.app.domain.LedgerAnalytics
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale

data class WidgetRateItem(
    val currencyCode: String,
    val formattedRate: String
)

data class WidgetBudgetSummary(
    val currencyCode: String,
    val spentMicros: Long,
    val limitMicros: Long,
    val remainingMicros: Long,
    val progressRatio: Float,
    val percentUsed: Int,
    val overBudget: Boolean
)

data class WidgetSummaryState(
    val monthLabel: String,
    val baseCurrency: String,
    val expenseMicros: Long,
    val incomeMicros: Long,
    val balanceMicros: Long,
    val budgetSummary: WidgetBudgetSummary? = null,
    val rates: List<WidgetRateItem> = emptyList()
)

/**
 * 棱账一木清新风 Jetpack Glance 桌面小组件：
 * 展示本月按主币种折算的总支出 / 总收入 / 结余，
 * 主账本生效月度总预算使用率进度条，
 * 下方显示 USD / EUR / GBP -> 主币种最新参考汇率，
 * 右上角「+ 记一笔」按钮直接唤醒 MainActivity 极速记账面板。
 */
class KairoSummaryWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = loadWidgetState(context)

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_WIDGET_OPEN_APP
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val quickRecordIntent = Intent(context, MainActivity::class.java).apply {
            action = IntentConstants.ACTION_ADD_RECORD
            putExtra(IntentConstants.EXTRA_OPEN_QUICK_RECORD, true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        provideContent {
            GlanceTheme {
                WidgetContent(
                    state = state,
                    openAppIntent = openAppIntent,
                    quickRecordIntent = quickRecordIntent
                )
            }
        }
    }

    private suspend fun loadWidgetState(context: Context): WidgetSummaryState {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val currentYm = YearMonth.from(today)
        val prevMonthStart = today.minusMonths(1).withDayOfMonth(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val monthEnd = today.plusMonths(1).withDayOfMonth(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1L
        val monthLabel = "${today.monthValue}月"

        return try {
            withContext(Dispatchers.IO) {
                val app = context.applicationContext as? KairoApplication
                val container = app?.container
                if (container == null) {
                    return@withContext fallbackState(monthLabel, CurrencyCatalog.DEFAULT_BASE_CURRENCY)
                }

                val settings = container.settingsStore.getSnapshot()
                val baseCurrency = settings.baseCurrency
                    .ifBlank { CurrencyCatalog.DEFAULT_BASE_CURRENCY }
                    .uppercase(Locale.ROOT)
                val primaryLedgerId = if (settings.activeLedgerId > 0L) settings.activeLedgerId else 1L

                val db = container.database
                val rateDao = db.exchangeRateDao()
                val txs = db.transactionDao().getBetween(prevMonthStart, monthEnd)
                val rateCache = HashMap<String, Long>()

                suspend fun cachedRate(from: String, to: String): Long {
                    val f = from.uppercase(Locale.ROOT)
                    val t = to.uppercase(Locale.ROOT)
                    if (f == t) return MoneyAmount.RATE_SCALE
                    val key = "$f->$t"
                    return rateCache[key] ?: resolveRateMicros(rateDao, f, t).also {
                        rateCache[key] = it
                    }
                }

                val monthlyPoints = LedgerAnalytics.computeMonthlyTrend(
                    months = listOf(currentYm),
                    transactions = txs,
                    displayCurrency = baseCurrency,
                    zoneId = zone,
                    ledgerId = settings.activeLedgerId,
                    fetchRate = { from, to -> cachedRate(from, to) }
                )
                val currentPoint = monthlyPoints.firstOrNull()
                val totalExpense = currentPoint?.expenseBaseMicros ?: 0L
                val totalIncome = currentPoint?.incomeBaseMicros ?: 0L

                // 读取主账本生效的月度总预算（优先当前账本，其次默认主账本 1L）
                val globalMonthlyBudgets = db.budgetDao().getAll().filter { b ->
                    b.enabled &&
                        b.categoryId == null &&
                        b.period.equals("MONTHLY", ignoreCase = true)
                }
                val targetBudget = globalMonthlyBudgets.firstOrNull { it.ledgerId == primaryLedgerId }
                    ?: globalMonthlyBudgets.firstOrNull { it.ledgerId == 1L }
                    ?: globalMonthlyBudgets.firstOrNull()

                val budgetSummary = if (targetBudget != null) {
                    val progress = LedgerAnalytics.computeBudgetProgress(
                        budgets = listOf(targetBudget),
                        transactions = txs,
                        categories = emptyList(),
                        year = today.year,
                        month = today.monthValue,
                        zoneId = zone,
                        ledgerId = targetBudget.ledgerId,
                        nowMillis = System.currentTimeMillis(),
                        fetchRate = { from, to -> cachedRate(from, to) }
                    ).firstOrNull()
                    progress?.let { bp ->
                        WidgetBudgetSummary(
                            currencyCode = bp.budget.currencyCode,
                            spentMicros = bp.spentInBudgetCurrencyMicros,
                            limitMicros = bp.limitInBudgetCurrencyMicros,
                            remainingMicros = bp.limitInBudgetCurrencyMicros - bp.spentInBudgetCurrencyMicros,
                            progressRatio = bp.progressRatio.coerceIn(0f, 1f),
                            percentUsed = (bp.progressRatio * 100f).toInt().coerceAtLeast(0),
                            overBudget = bp.overBudget
                        )
                    }
                } else {
                    null
                }

                val displayCurrencies = listOf("USD", "EUR", "GBP")
                    .filterNot { it == baseCurrency }
                val rateItems = displayCurrencies.map { code ->
                    val rateMicros = cachedRate(code, baseCurrency)
                    WidgetRateItem(
                        currencyCode = code,
                        formattedRate = MoneyAmount.formatRate(rateMicros, decimals = 2)
                    )
                }

                WidgetSummaryState(
                    monthLabel = monthLabel,
                    baseCurrency = baseCurrency,
                    expenseMicros = totalExpense,
                    incomeMicros = totalIncome,
                    balanceMicros = totalIncome - totalExpense,
                    budgetSummary = budgetSummary,
                    rates = rateItems
                )
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Exception) {
            fallbackState(monthLabel, CurrencyCatalog.DEFAULT_BASE_CURRENCY)
        }
    }

    private suspend fun resolveRateMicros(
        rateDao: ExchangeRateDao,
        fromCurrency: String,
        toCurrency: String
    ): Long {
        if (fromCurrency == toCurrency) return MoneyAmount.RATE_SCALE

        rateDao.getLatest(fromCurrency, toCurrency)?.let { return it.rateMicros }
        rateDao.getLatest(toCurrency, fromCurrency)?.let { return MoneyAmount.invertRate(it.rateMicros) }

        if (fromCurrency != CurrencyCatalog.DEFAULT_BASE_CURRENCY &&
            toCurrency != CurrencyCatalog.DEFAULT_BASE_CURRENCY
        ) {
            val fromToCny = rateDao.getLatest(fromCurrency, CurrencyCatalog.DEFAULT_BASE_CURRENCY)?.rateMicros
                ?: rateDao.getLatest(CurrencyCatalog.DEFAULT_BASE_CURRENCY, fromCurrency)?.let {
                    MoneyAmount.invertRate(it.rateMicros)
                }
                ?: CurrencyCatalog.find(fromCurrency).fallbackRateToCnyMicros
            val toToCny = rateDao.getLatest(toCurrency, CurrencyCatalog.DEFAULT_BASE_CURRENCY)?.rateMicros
                ?: rateDao.getLatest(CurrencyCatalog.DEFAULT_BASE_CURRENCY, toCurrency)?.let {
                    MoneyAmount.invertRate(it.rateMicros)
                }
                ?: CurrencyCatalog.find(toCurrency).fallbackRateToCnyMicros
            return MoneyAmount.crossRate(fromToCny, toToCny)
        }

        val fromCny = CurrencyCatalog.find(fromCurrency).fallbackRateToCnyMicros
        val toCny = CurrencyCatalog.find(toCurrency).fallbackRateToCnyMicros
        return MoneyAmount.crossRate(fromCny, toCny)
    }

    private fun fallbackState(monthLabel: String, baseCurrency: String): WidgetSummaryState {
        val rates = listOf("USD", "EUR", "GBP")
            .filterNot { it == baseCurrency }
            .map { code ->
                val r = CurrencyCatalog.find(code).fallbackRateToCnyMicros
                WidgetRateItem(code, MoneyAmount.formatRate(r, decimals = 2))
            }
        return WidgetSummaryState(
            monthLabel = monthLabel,
            baseCurrency = baseCurrency,
            expenseMicros = 0L,
            incomeMicros = 0L,
            balanceMicros = 0L,
            budgetSummary = null,
            rates = rates
        )
    }

    companion object {
        private const val ACTION_WIDGET_OPEN_APP = "com.kairoledger.app.ACTION_WIDGET_OPEN_SUMMARY"
    }
}

@Composable
private fun WidgetContent(
    state: WidgetSummaryState,
    openAppIntent: Intent,
    quickRecordIntent: Intent
) {
    val widgetBg = ColorProvider(
        day = Color(0xFFFFFFFF),
        night = Color(0xFF181F1C)
    )
    val stripBg = ColorProvider(
        day = Color(0xFFF1F7F4),
        night = Color(0xFF212B26)
    )
    val primaryGreenBg = ColorProvider(
        day = Color(0xFF2AA876),
        night = Color(0xFF5BD2A0)
    )
    val onPrimaryGreen = ColorProvider(
        day = Color(0xFFFFFFFF),
        night = Color(0xFF003824)
    )
    val textPrimary = ColorProvider(
        day = Color(0xFF1A211E),
        night = Color(0xFFE1E8E4)
    )
    val textSecondary = ColorProvider(
        day = Color(0xFF54635C),
        night = Color(0xFFA9B7B0)
    )
    val stripTextColor = ColorProvider(
        day = Color(0xFF4E6B5D),
        night = Color(0xFFA7CBB8)
    )
    val expenseColor = ColorProvider(
        day = Color(0xFFE05252),
        night = Color(0xFFFF8983)
    )
    val incomeColor = ColorProvider(
        day = Color(0xFF22A06B),
        night = Color(0xFF65DAA0)
    )
    val warningColor = ColorProvider(
        day = Color(0xFFE89B2D),
        night = Color(0xFFFBBF24)
    )
    val budgetTrackColor = ColorProvider(
        day = Color(0xFFDDE5E1),
        night = Color(0xFF2E3B35)
    )

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .background(widgetBg)
            .cornerRadius(22.dp)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .clickable(actionStartActivity(openAppIntent))
    ) {
        // 顶部栏：月份标题 + 一木绿「+ 记一笔」胶囊
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = "棱账 · ${state.monthLabel}收支 (${state.baseCurrency})",
                    style = TextStyle(
                        color = textPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1
                )
            }
            Spacer(modifier = GlanceModifier.width(8.dp))
            Box(
                modifier = GlanceModifier
                    .background(primaryGreenBg)
                    .cornerRadius(16.dp)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
                    .clickable(actionStartActivity(quickRecordIntent)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "+ 记一笔",
                    style = TextStyle(
                        color = onPrimaryGreen,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1
                )
            }
        }

        Spacer(modifier = GlanceModifier.height(7.dp))

        // 三栏核心指标：支出 / 收入 / 结余
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MetricColumn(
                label = "本月支出",
                value = MoneyAmount.format(state.expenseMicros, state.baseCurrency, grouping = true),
                labelColor = textSecondary,
                valueColor = expenseColor,
                modifier = GlanceModifier.defaultWeight()
            )
            MetricColumn(
                label = "本月收入",
                value = MoneyAmount.format(state.incomeMicros, state.baseCurrency, grouping = true),
                labelColor = textSecondary,
                valueColor = incomeColor,
                modifier = GlanceModifier.defaultWeight()
            )
            MetricColumn(
                label = "本月结余",
                value = MoneyAmount.format(
                    state.balanceMicros,
                    state.baseCurrency,
                    showSign = true,
                    grouping = true
                ),
                labelColor = textSecondary,
                valueColor = textPrimary,
                modifier = GlanceModifier.defaultWeight()
            )
        }

        Spacer(modifier = GlanceModifier.height(7.dp))

        // 一木浅灰绿底条：主账本预算使用率进度条 + 实时汇率
        Column(
            modifier = GlanceModifier
                .fillMaxWidth()
                .background(stripBg)
                .cornerRadius(12.dp)
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            val budget = state.budgetSummary
            if (budget != null) {
                val budgetText = if (budget.remainingMicros >= 0L) {
                    "月预算已用 ${budget.percentUsed}% · 剩余 ${MoneyAmount.format(budget.remainingMicros, budget.currencyCode)}"
                } else {
                    "月预算已用 ${budget.percentUsed}% · 超支 ${MoneyAmount.format(-budget.remainingMicros, budget.currencyCode)}"
                }
                val barColor = when {
                    budget.overBudget -> expenseColor
                    budget.progressRatio >= 0.8f -> warningColor
                    else -> primaryGreenBg
                }
                Text(
                    text = budgetText,
                    style = TextStyle(
                        color = if (budget.overBudget) expenseColor else stripTextColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    maxLines = 1
                )
                Spacer(modifier = GlanceModifier.height(3.dp))
                LinearProgressIndicator(
                    progress = budget.progressRatio,
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .cornerRadius(2.dp),
                    color = barColor,
                    backgroundColor = budgetTrackColor
                )
                Spacer(modifier = GlanceModifier.height(4.dp))
            }

            val rateLine = state.rates.joinToString("  ·  ") { item ->
                "${item.currencyCode} ${item.formattedRate}"
            }
            Text(
                text = "汇率→${state.baseCurrency}  $rateLine",
                style = TextStyle(
                    color = stripTextColor,
                    fontSize = 10.sp
                ),
                maxLines = 1
            )
        }
    }
}

@Composable
private fun MetricColumn(
    label: String,
    value: String,
    labelColor: androidx.glance.unit.ColorProvider,
    valueColor: androidx.glance.unit.ColorProvider,
    modifier: GlanceModifier = GlanceModifier
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = TextStyle(
                color = labelColor,
                fontSize = 10.sp
            ),
            maxLines = 1
        )
        Spacer(modifier = GlanceModifier.height(2.dp))
        Text(
            text = value,
            style = TextStyle(
                color = valueColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            ),
            maxLines = 1
        )
    }
}

class KairoSummaryWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = KairoSummaryWidget()
}

object KairoWidgetUpdater {
    suspend fun updateAll(context: Context) {
        val appContext = context.applicationContext
        KairoSummaryWidget().updateAll(appContext)
        KairoQuickRecordWidget().updateAll(appContext)
    }
}

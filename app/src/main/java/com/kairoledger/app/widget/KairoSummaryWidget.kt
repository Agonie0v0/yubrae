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
import com.kairoledger.app.data.db.TransactionStatus
import com.kairoledger.app.data.db.TransactionType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

data class WidgetRateItem(
    val currencyCode: String,
    val formattedRate: String
)

data class WidgetSummaryState(
    val monthLabel: String,
    val baseCurrency: String,
    val expenseMicros: Long,
    val incomeMicros: Long,
    val balanceMicros: Long,
    val rates: List<WidgetRateItem>
)

/**
 * 棱账 Jetpack Glance 桌面小组件：
 * 展示本月按主币种（默认 CNY）折算的总支出 / 总收入 / 结余，
 * 下方显示 USD / EUR / GBP -> 主币种最新参考汇率，
 * 右上角「记一笔」按钮直接唤醒 MainActivity 极速记账面板。
 */
class KairoSummaryWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = loadWidgetState(context)

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
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
        val monthStart = today.withDayOfMonth(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val monthEnd = today.plusMonths(1).withDayOfMonth(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1L
        val monthLabel = "${today.monthValue}月"

        return try {
            withContext(Dispatchers.IO) {
                val app = context.applicationContext as? KairoApplication
                val container = app?.container
                if (container == null) {
                    return@withContext fallbackState(monthLabel, CurrencyCatalog.DEFAULT_BASE_CURRENCY)
                }

                val baseCurrency = container.settingsStore.getSnapshot().baseCurrency
                    .ifBlank { CurrencyCatalog.DEFAULT_BASE_CURRENCY }
                    .uppercase(Locale.ROOT)
                val db = container.database
                val rateDao = db.exchangeRateDao()
                val txs = db.transactionDao().getBetween(monthStart, monthEnd)

                var totalExpense = 0L
                var totalIncome = 0L
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

                for (tx in txs) {
                    if (tx.status != TransactionStatus.CONFIRMED) continue
                    if (tx.type != TransactionType.EXPENSE && tx.type != TransactionType.INCOME) continue

                    val txBaseAmount = MoneyAmount.convert(tx.origAmountMicros, tx.rateToBaseMicros)
                    val converted = if (tx.baseCurrency.equals(baseCurrency, ignoreCase = true)) {
                        txBaseAmount
                    } else {
                        val r = cachedRate(tx.baseCurrency, baseCurrency)
                        MoneyAmount.convert(txBaseAmount, r)
                    }

                    when (tx.type) {
                        TransactionType.EXPENSE -> totalExpense += converted
                        TransactionType.INCOME -> totalIncome += converted
                    }
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
            rates = rates
        )
    }
}

@Composable
private fun WidgetContent(
    state: WidgetSummaryState,
    openAppIntent: Intent,
    quickRecordIntent: Intent
) {
    val expenseColor = ColorProvider(
        day = Color(0xFFDC2626),
        night = Color(0xFFF87171)
    )
    val incomeColor = ColorProvider(
        day = Color(0xFF16A34A),
        night = Color(0xFF4ADE80)
    )

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(20.dp)
            .padding(14.dp)
            .clickable(actionStartActivity(openAppIntent))
    ) {
        // Header row: Month title + Quick Record button
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = "棱账 · ${state.monthLabel}收支 (${state.baseCurrency})",
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1
                )
            }
            Spacer(modifier = GlanceModifier.width(8.dp))
            Box(
                modifier = GlanceModifier
                    .background(GlanceTheme.colors.primary)
                    .cornerRadius(16.dp)
                    .padding(horizontal = 10.dp, vertical = 5.dp)
                    .clickable(actionStartActivity(quickRecordIntent)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "+ 记一笔",
                    style = TextStyle(
                        color = GlanceTheme.colors.onPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    maxLines = 1
                )
            }
        }

        Spacer(modifier = GlanceModifier.height(10.dp))

        // Summary metrics row: Expense / Income / Balance
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MetricColumn(
                label = "本月支出",
                value = MoneyAmount.format(state.expenseMicros, state.baseCurrency, grouping = true),
                valueColor = expenseColor,
                modifier = GlanceModifier.defaultWeight()
            )
            MetricColumn(
                label = "本月收入",
                value = MoneyAmount.format(state.incomeMicros, state.baseCurrency, grouping = true),
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
                valueColor = GlanceTheme.colors.onSurface,
                modifier = GlanceModifier.defaultWeight()
            )
        }

        Spacer(modifier = GlanceModifier.height(10.dp))

        // Exchange rates ticker bar
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .background(GlanceTheme.colors.surfaceVariant)
                .cornerRadius(10.dp)
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val rateLine = state.rates.joinToString("  ·  ") { item ->
                "${item.currencyCode} ${item.formattedRate}"
            }
            Text(
                text = "汇率→${state.baseCurrency}  $rateLine",
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 11.sp
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
    valueColor: androidx.glance.unit.ColorProvider,
    modifier: GlanceModifier = GlanceModifier
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = TextStyle(
                color = GlanceTheme.colors.onSurfaceVariant,
                fontSize = 11.sp
            ),
            maxLines = 1
        )
        Spacer(modifier = GlanceModifier.height(2.dp))
        Text(
            text = value,
            style = TextStyle(
                color = valueColor,
                fontSize = 14.sp,
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
        KairoSummaryWidget().updateAll(context.applicationContext)
    }
}

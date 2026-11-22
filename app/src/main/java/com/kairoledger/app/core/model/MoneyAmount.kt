package com.kairoledger.app.core.model

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * 金融级定点数运算工具（严禁使用 Float/Double 存储或累加金额）。
 * - 金额缩放因子 AMOUNT_SCALE = 10_000L（支持最多 4 位小数，1.00 元 = 10_000L）
 * - 汇率缩放因子 RATE_SCALE = 1_000_000L（支持 6 位小数精度，汇率 7.2450 = 7_245_000L）
 */
object MoneyAmount {
    const val AMOUNT_SCALE: Long = 10_000L
    const val RATE_SCALE: Long = 1_000_000L

    private val BD_AMOUNT_SCALE = BigDecimal(AMOUNT_SCALE)
    private val BD_RATE_SCALE = BigDecimal(RATE_SCALE)

    /** 将字符串金额转为微元 Long，非法输入返回 null。 */
    fun parseToMicros(raw: String): Long? {
        val cleaned = raw.trim()
            .replace(",", "")
            .replace("，", "")
            .replace("¥", "")
            .replace("￥", "")
            .replace("$", "")
            .replace("€", "")
            .replace("£", "")
            .trim()
        if (cleaned.isEmpty()) return null
        return try {
            BigDecimal(cleaned)
                .multiply(BD_AMOUNT_SCALE)
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact()
        } catch (_: Exception) {
            null
        }
    }

    /** 将用户输入或接口返回的汇率字符串/Double 转为百万分位汇率 Long。 */
    fun parseRateToMicros(raw: String): Long? {
        val cleaned = raw.trim().replace(",", "")
        if (cleaned.isEmpty()) return null
        return try {
            val bd = BigDecimal(cleaned)
            if (bd <= BigDecimal.ZERO) return null
            bd.multiply(BD_RATE_SCALE)
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact()
        } catch (_: Exception) {
            null
        }
    }

    fun rateFromBigDecimal(rate: BigDecimal): Long {
        if (rate <= BigDecimal.ZERO) return RATE_SCALE
        return rate.multiply(BD_RATE_SCALE)
            .setScale(0, RoundingMode.HALF_UP)
            .toLong()
            .coerceAtLeast(1L)
    }

    fun rateToBigDecimal(rateMicros: Long): BigDecimal =
        BigDecimal(rateMicros).divide(BD_RATE_SCALE, 6, RoundingMode.HALF_UP)

    fun microsToBigDecimal(amountMicros: Long): BigDecimal =
        BigDecimal(amountMicros).divide(BD_AMOUNT_SCALE, 4, RoundingMode.HALF_UP)

    /** 使用成交汇率换算金额：target = orig * rateMicros / RATE_SCALE */
    fun convert(amountMicros: Long, rateMicros: Long): Long {
        if (amountMicros == 0L) return 0L
        if (rateMicros == RATE_SCALE) return amountMicros
        return BigDecimal(amountMicros)
            .multiply(BigDecimal(rateMicros))
            .divide(BD_RATE_SCALE, 0, RoundingMode.HALF_UP)
            .longValueExact()
    }

    /** 根据原币种金额与实际账户扣款金额反算成交汇率（支持汇损反算）。 */
    fun computeRate(origAmountMicros: Long, targetAmountMicros: Long): Long {
        if (origAmountMicros == 0L || targetAmountMicros == 0L) return RATE_SCALE
        return BigDecimal(kotlin.math.abs(targetAmountMicros))
            .multiply(BD_RATE_SCALE)
            .divide(BigDecimal(kotlin.math.abs(origAmountMicros)), 0, RoundingMode.HALF_UP)
            .toLong()
            .coerceAtLeast(1L)
    }

    /** 反向求汇率：1 / rateMicros */
    fun invertRate(rateMicros: Long): Long {
        if (rateMicros <= 0L) return RATE_SCALE
        return BD_RATE_SCALE
            .multiply(BD_RATE_SCALE)
            .divide(BigDecimal(rateMicros), 0, RoundingMode.HALF_UP)
            .toLong()
            .coerceAtLeast(1L)
    }

    /** 通过中间币种（如 CNY）推导交叉汇率：from -> via -> to */
    fun crossRate(fromToViaMicros: Long, toToViaMicros: Long): Long {
        if (toToViaMicros <= 0L) return RATE_SCALE
        return BigDecimal(fromToViaMicros)
            .multiply(BD_RATE_SCALE)
            .divide(BigDecimal(toToViaMicros), 0, RoundingMode.HALF_UP)
            .toLong()
            .coerceAtLeast(1L)
    }

    /** 格式化纯数字金额字符串（用于输入框或 CSV 导出），不带货币符号。 */
    fun formatPlain(amountMicros: Long, minorDigits: Int = 2, grouping: Boolean = false): String {
        val bd = BigDecimal(amountMicros).divide(BD_AMOUNT_SCALE, minorDigits, RoundingMode.HALF_UP)
        val pattern = buildString {
            append(if (grouping) "#,##0" else "0")
            if (minorDigits > 0) {
                append('.')
                repeat(minorDigits) { append('0') }
            }
        }
        val df = DecimalFormat(pattern, DecimalFormatSymbols(Locale.US))
        return df.format(bd)
    }

    /** 格式化带币种符号的展示金额，如 ¥128.50、-$15.00 USD。 */
    fun format(
        amountMicros: Long,
        currencyCode: String,
        showSign: Boolean = false,
        showCurrencyCode: Boolean = false,
        grouping: Boolean = true
    ): String {
        val info = CurrencyCatalog.find(currencyCode)
        val absVal = kotlin.math.abs(amountMicros)
        val sign = when {
            amountMicros < 0L -> "-"
            showSign && amountMicros > 0L -> "+"
            else -> ""
        }
        val numberStr = formatPlain(absVal, info.minorDigits, grouping)
        val suffix = if (showCurrencyCode && info.code != CurrencyCatalog.DEFAULT_BASE_CURRENCY) {
            " ${info.code}"
        } else {
            ""
        }
        return "$sign${info.symbol}$numberStr$suffix"
    }

    fun formatRate(rateMicros: Long, decimals: Int = 4): String {
        val bd = BigDecimal(rateMicros).divide(BD_RATE_SCALE, decimals, RoundingMode.HALF_UP)
        return bd.stripTrailingZeros().let {
            if (it.scale() < 2) it.setScale(2, RoundingMode.HALF_UP) else it
        }.toPlainString()
    }
}

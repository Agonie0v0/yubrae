package com.yubrae.app.core.model

/**
 * ISO 4217 币种目录与默认参考汇率（以 1 外币折合 CNY 的微元/百万分位固定小数表示）。
 * 主币种默认 CNY，置顶常用外币：USD、EUR、GBP。
 */
data class CurrencyInfo(
    val code: String,
    val nameZh: String,
    val symbol: String,
    val flag: String,
    val minorDigits: Int,
    /** 1 unit of this currency in CNY, scaled by RATE_SCALE (1_000_000L). */
    val fallbackRateToCnyMicros: Long
)

object CurrencyCatalog {
    const val DEFAULT_BASE_CURRENCY = "CNY"
    val PINNED_CURRENCIES = listOf("CNY", "USD", "EUR", "GBP")

    val all: List<CurrencyInfo> = listOf(
        CurrencyInfo("CNY", "人民币", "¥", "🇨🇳", 2, 1_000_000L),
        CurrencyInfo("USD", "美元", "$", "🇺🇸", 2, 7_240_000L),
        CurrencyInfo("EUR", "欧元", "€", "🇪🇺", 2, 7_620_000L),
        CurrencyInfo("GBP", "英镑", "£", "🇬🇧", 2, 9_150_000L),
        CurrencyInfo("HKD", "港币", "HK$", "🇭🇰", 2, 930_000L),
        CurrencyInfo("JPY", "日元", "JP¥", "🇯🇵", 0, 47_000L),
        CurrencyInfo("SGD", "新加坡元", "S$", "🇸🇬", 2, 5_380_000L),
        CurrencyInfo("AUD", "澳元", "A$", "🇦🇺", 2, 4_710_000L),
        CurrencyInfo("CAD", "加元", "C$", "🇨🇦", 2, 5_160_000L),
        CurrencyInfo("CHF", "瑞士法郎", "CHF", "🇨🇭", 2, 8_160_000L),
        CurrencyInfo("TWD", "新台币", "NT$", "🇹🇼", 2, 223_000L),
        CurrencyInfo("KRW", "韩元", "₩", "🇰🇷", 0, 5_180L)
    )

    private val byCode: Map<String, CurrencyInfo> = all.associateBy { it.code }

    fun find(code: String): CurrencyInfo {
        val upper = code.trim().uppercase()
        return byCode[upper] ?: CurrencyInfo(
            code = upper.ifEmpty { DEFAULT_BASE_CURRENCY },
            nameZh = upper.ifEmpty { "人民币" },
            symbol = upper.ifEmpty { "¥" },
            flag = "🌐",
            minorDigits = 2,
            fallbackRateToCnyMicros = 1_000_000L
        )
    }

    fun symbolOf(code: String): String = find(code).symbol

    fun orderedCodes(pinned: List<String> = PINNED_CURRENCIES): List<String> {
        val normalizedPinned = pinned.map { it.trim().uppercase() }.filter { it.isNotEmpty() }.distinct()
        val remaining = all.map { it.code }.filterNot { it in normalizedPinned }
        return normalizedPinned + remaining
    }

    /** 根据文本中的货币符号或关键词猜测币种。 */
    fun detectCurrencyFromText(text: String, defaultCode: String = DEFAULT_BASE_CURRENCY): String {
        val upper = text.uppercase()
        return when {
            upper.contains("USD") || upper.contains("US$") || upper.contains("美元") ||
                (text.contains("$") && !upper.contains("HK$") && !upper.contains("S$") && !upper.contains("NT$") && !upper.contains("A$") && !upper.contains("C$")) -> "USD"
            upper.contains("EUR") || text.contains("€") || upper.contains("欧元") -> "EUR"
            upper.contains("GBP") || text.contains("£") || upper.contains("英镑") -> "GBP"
            upper.contains("HKD") || upper.contains("HK$") || upper.contains("港币") || upper.contains("港元") -> "HKD"
            upper.contains("JPY") || upper.contains("日元") -> "JPY"
            upper.contains("SGD") || upper.contains("S$") || upper.contains("新元") || upper.contains("新加坡元") -> "SGD"
            upper.contains("AUD") || upper.contains("A$") || upper.contains("澳元") -> "AUD"
            upper.contains("CAD") || upper.contains("C$") || upper.contains("加元") -> "CAD"
            upper.contains("CHF") || upper.contains("瑞郎") -> "CHF"
            upper.contains("CNY") || upper.contains("RMB") || text.contains("¥") || text.contains("￥") || text.contains("元") || upper.contains("人民币") -> "CNY"
            else -> defaultCode
        }
    }
}

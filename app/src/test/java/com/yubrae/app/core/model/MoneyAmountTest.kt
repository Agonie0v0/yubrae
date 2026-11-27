package com.yubrae.app.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyAmountTest {

    @Test
    fun parseAndFormatMicros_handlesStandardDecimalsAndSymbols() {
        assertEquals(1_255_000L, MoneyAmount.parseToMicros("125.50"))
        assertEquals(1_255_000L, MoneyAmount.parseToMicros("$125.5"))
        assertEquals(10_000_000L, MoneyAmount.parseToMicros("1,000"))
        assertNull(MoneyAmount.parseToMicros(""))
        assertNull(MoneyAmount.parseToMicros("abc"))
    }

    @Test
    fun convertAndComputeRate_preservesPrecisionWithoutFloatingPointDrift() {
        // 15.00 USD at rate 7.2450 CNY/USD = 108.675 -> 108.68 CNY (1086750 micros)
        val origUsdMicros = MoneyAmount.parseToMicros("15.00")!!
        val rateMicros = MoneyAmount.parseRateToMicros("7.2450")!!
        val convertedCnyMicros = MoneyAmount.convert(origUsdMicros, rateMicros)
        assertEquals(1_086_750L, convertedCnyMicros)
        assertEquals("¥\u2009108.68", MoneyAmount.format(convertedCnyMicros, "CNY"))

        // Reverse rate calculation when bank settles 109.20 CNY for 15.00 USD
        val actualCnyMicros = MoneyAmount.parseToMicros("109.20")!!
        val impliedRate = MoneyAmount.computeRate(origUsdMicros, actualCnyMicros)
        assertEquals(7_280_000L, impliedRate)
        assertEquals("7.28", MoneyAmount.formatRate(impliedRate))
    }

    @Test
    fun format_appliesUnifiedSignAndSymbolSpacingWhileKeepingFormatPlainPure() {
        assertEquals("-\u00A0¥\u200912.66", MoneyAmount.format(-126_600L, "CNY"))
        assertEquals("+\u00A0¥\u2009563.35", MoneyAmount.format(5_633_500L, "CNY", showSign = true))
        assertEquals("¥\u20095,167.65", MoneyAmount.format(51_676_500L, "CNY", showSign = false))
        assertEquals("¥\u20090.00", MoneyAmount.format(0L, "CNY", showSign = true))
        assertEquals("-\u00A0$\u200953.16 USD", MoneyAmount.format(-531_600L, "USD", showCurrencyCode = true))
        assertEquals("JP¥\u20091,200", MoneyAmount.format(12_000_000L, "JPY"))

        // formatPlain 严禁混入空白或货币符号，确保输入框与 CSV 导出稳定
        assertEquals("-12.66", MoneyAmount.formatPlain(-126_600L, minorDigits = 2, grouping = false))
        assertEquals("5,167.65", MoneyAmount.formatPlain(51_676_500L, minorDigits = 2, grouping = true))
    }

    @Test
    fun parseToMicros_roundTripsFormattedAmountsAndUnicodeSpaces() {
        assertEquals(-126_600L, MoneyAmount.parseToMicros("-\u00A0¥\u200912.66"))
        assertEquals(5_633_500L, MoneyAmount.parseToMicros("+\u00A0¥\u2009563.35"))
        assertEquals(-531_600L, MoneyAmount.parseToMicros("-\u00A0$\u200953.16"))
        assertEquals(51_676_500L, MoneyAmount.parseToMicros("¥\u20095,167.65"))
        assertEquals(-250_000L, MoneyAmount.parseToMicros("-\u00A0€\u200925.00"))
        assertEquals(-12_000_000L, MoneyAmount.parseToMicros("-\u00A0JP¥\u20091,200"))
        assertEquals(12_505_000L, MoneyAmount.parseToMicros("\u3000\u200B¥\u202F1,250.50\u200A\uFEFF"))
        assertEquals(-126_600L, MoneyAmount.parseToMicros(MoneyAmount.format(-126_600L, "CNY")))
        assertEquals(5_633_500L, MoneyAmount.parseToMicros(MoneyAmount.format(5_633_500L, "CNY", showSign = true)))
        assertEquals(-531_600L, MoneyAmount.parseToMicros(MoneyAmount.format(-531_600L, "USD")))
        assertNull(MoneyAmount.parseToMicros("\u00A0\u2009\u202F\u200A\u3000\u200B\uFEFF"))
    }

    @Test
    fun detectCurrencyFromText_identifiesCnyUsdEurGbp() {
        assertEquals("USD", CurrencyCatalog.detectCurrencyFromText("You spent $12.99 at OpenAI"))
        assertEquals("EUR", CurrencyCatalog.detectCurrencyFromText("Payment of EUR 24.50 to Hetzner"))
        assertEquals("GBP", CurrencyCatalog.detectCurrencyFromText("Card charged £18.00 at Sainsbury"))
        assertEquals("CNY", CurrencyCatalog.detectCurrencyFromText("微信支付向星巴克付款32.00元"))
    }
}

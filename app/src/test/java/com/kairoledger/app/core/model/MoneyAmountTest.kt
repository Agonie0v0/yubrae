package com.kairoledger.app.core.model

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
        assertEquals("¥108.68", MoneyAmount.format(convertedCnyMicros, "CNY"))

        // Reverse rate calculation when bank settles 109.20 CNY for 15.00 USD
        val actualCnyMicros = MoneyAmount.parseToMicros("109.20")!!
        val impliedRate = MoneyAmount.computeRate(origUsdMicros, actualCnyMicros)
        assertEquals(7_280_000L, impliedRate)
        assertEquals("7.28", MoneyAmount.formatRate(impliedRate))
    }

    @Test
    fun detectCurrencyFromText_identifiesCnyUsdEurGbp() {
        assertEquals("USD", CurrencyCatalog.detectCurrencyFromText("You spent $12.99 at OpenAI"))
        assertEquals("EUR", CurrencyCatalog.detectCurrencyFromText("Payment of EUR 24.50 to Hetzner"))
        assertEquals("GBP", CurrencyCatalog.detectCurrencyFromText("Card charged £18.00 at Sainsbury"))
        assertEquals("CNY", CurrencyCatalog.detectCurrencyFromText("微信支付向星巴克付款32.00元"))
    }
}

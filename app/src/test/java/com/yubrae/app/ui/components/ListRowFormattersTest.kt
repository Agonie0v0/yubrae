package com.yubrae.app.ui.components

import com.yubrae.app.data.db.ExchangeRateEntity
import com.yubrae.app.data.db.TransactionEntity
import com.yubrae.app.data.db.TransactionStatus
import com.yubrae.app.data.db.TransactionType
import com.yubrae.app.ui.screens.transactions.buildHeaderMetaFootnote
import com.yubrae.app.ui.viewmodel.computeRefundedAmountByOrigTxId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ListRowFormattersTest {

    @Test
    fun cleanListRowNote_stripsAutoNotificationBoilerplateToEmpty() {
        assertEquals("", KairoFormats.cleanListRowNote("刷卡完成，交易金额：1.80元"))
        assertEquals("", KairoFormats.cleanListRowNote("充值金额: 50.00元 · 卡内余额: 66.35元"))
        assertEquals("", KairoFormats.cleanListRowNote("支付成功，交易金额：12.50元"))
        assertEquals("", KairoFormats.cleanListRowNote("付款成功！消费金额：1,280.50元，账户余额：3,400.00元。"))
        assertEquals("", KairoFormats.cleanListRowNote("扣款成功 · 入账金额: $12.00 USD · 剩余余额: 100.00元"))
        assertEquals("", KairoFormats.cleanListRowNote("充值成功，交易金额：50.00元；卡内余额：66.35元"))
        assertEquals("", KairoFormats.cleanListRowNote("地址: 朝阳区 · 优惠 2.00元 · 交易单号：TX9988"))
        assertEquals("", KairoFormats.cleanListRowNote("   "))
    }

    @Test
    fun cleanListRowNote_preservesRealHumanNotesAndMixedSegments() {
        assertEquals("信用卡还款", KairoFormats.cleanListRowNote("信用卡还款"))
        assertEquals("午饭打包", KairoFormats.cleanListRowNote("午饭打包"))
        assertEquals("六婆辣椒面香辣蘸料", KairoFormats.cleanListRowNote("六婆辣椒面香辣蘸料"))
        assertEquals("重庆畅通卡", KairoFormats.cleanListRowNote("重庆畅通卡 · 刷卡完成，交易金额：1.80元"))
        assertEquals("重庆畅通卡", KairoFormats.cleanListRowNote("重庆畅通卡，刷卡完成，交易金额：1.80元"))
        assertEquals("和朋友聚餐，人均80", KairoFormats.cleanListRowNote("和朋友聚餐，人均80"))
        assertEquals("周末大采购！买了咖啡。", KairoFormats.cleanListRowNote("周末大采购！买了咖啡。"))
        assertEquals("午饭打包", KairoFormats.cleanListRowNote("午饭打包 · 优惠金额：2.00元 · 支付成功"))
    }

    @Test
    fun formatDayHeaderParts_handlesTodayYesterdayNormalDayAndCrossYear() {
        val today = LocalDate.of(2026, 11, 27)

        assertEquals(
            "11月27日" to "今天",
            KairoFormats.formatDayHeaderParts(LocalDate.of(2026, 11, 27), today = today)
        )
        assertEquals(
            "11月26日" to "昨天",
            KairoFormats.formatDayHeaderParts(LocalDate.of(2026, 11, 26), today = today)
        )
        assertEquals(
            "11月25日" to "周三",
            KairoFormats.formatDayHeaderParts(LocalDate.of(2026, 11, 25), today = today)
        )
        assertEquals(
            "11月22日" to "周日",
            KairoFormats.formatDayHeaderParts(LocalDate.of(2026, 11, 22), today = today)
        )
        assertEquals(
            "2025年12月31日" to "周三",
            KairoFormats.formatDayHeaderParts(LocalDate.of(2025, 12, 31), today = today)
        )
        assertEquals(
            "2025年12月31日" to "昨天",
            KairoFormats.formatDayHeaderParts(
                date = LocalDate.of(2025, 12, 31),
                today = LocalDate.of(2026, 1, 1)
            )
        )
    }

    @Test
    fun formatPlainSignedAmount_alwaysIncludesCurrencySymbolOrCode() {
        assertEquals("-¥1.80", KairoFormats.formatPlainSignedAmount(-18_000L, "CNY", "CNY"))
        assertEquals("+¥106.00", KairoFormats.formatPlainSignedAmount(1_060_000L, "CNY", "CNY"))
        assertEquals("¥50.00", KairoFormats.formatPlainSignedAmount(500_000L, "CNY", "CNY", showSign = false))
        assertEquals("¥1,280.50", KairoFormats.formatPlainSignedAmount(12_805_000L, "CNY", "CNY", showSign = false))
        assertEquals("¥0.00", KairoFormats.formatPlainSignedAmount(0L, "CNY", "CNY", showSign = true))
        assertEquals("-¥0.00", "-${KairoFormats.formatPlainSignedAmount(0L, "CNY", "CNY", showSign = false)}")
        assertEquals("-¥1.80", KairoFormats.formatPlainSignedAmount(-18_000L, "cny", "CNY"))

        assertEquals("-$53.16", KairoFormats.formatPlainSignedAmount(-531_600L, "USD", "CNY"))
        assertEquals("+$12.00", KairoFormats.formatPlainSignedAmount(120_000L, "USD", "CNY"))
        assertEquals("$12.00", KairoFormats.formatPlainSignedAmount(120_000L, "USD", "CNY", showSign = false))
        assertEquals("-€25.00", KairoFormats.formatPlainSignedAmount(-250_000L, "EUR", "CNY"))
        assertEquals("-JP¥1,200", KairoFormats.formatPlainSignedAmount(-12_000_000L, "JPY", "CNY"))
        assertEquals("-12.00 CHF", KairoFormats.formatPlainSignedAmount(-120_000L, "CHF", "CNY"))
        assertEquals("+50.00 THB", KairoFormats.formatPlainSignedAmount(500_000L, "THB", "CNY"))

        assertEquals("-$10.00", KairoFormats.formatPlainSignedAmount(-100_000L, "USD", "USD"))
        assertEquals("+$10.00", KairoFormats.formatPlainSignedAmount(100_000L, "USD", "USD"))
        assertEquals("$0.00", KairoFormats.formatPlainSignedAmount(0L, "USD", "USD", showSign = true))
        assertEquals("-12.00 CHF", KairoFormats.formatPlainSignedAmount(-120_000L, "CHF", "CHF"))
        assertEquals("-¥10.00", KairoFormats.formatPlainSignedAmount(-100_000L, "CNY", "USD"))
    }

    @Test
    fun buildHeaderMetaFootnote_restoresForeignCurrencySymbolsWithoutDuplicatingCode() {
        assertEquals(
            "USD -$53.16 · EUR -€25.00 · CHF -12.00 · 省 ¥5.00 · 费 ¥1.20",
            buildHeaderMetaFootnote(
                foreignCurrencySummaries = listOf(
                    Triple("USD", 531_600L, 0L),
                    Triple("EUR", 250_000L, 0L),
                    Triple("CHF", 120_000L, 0L)
                ),
                monthDiscountBaseMicros = 50_000L,
                monthFeeBaseMicros = 12_000L,
                baseCurrency = "CNY",
                privacyMasked = false
            )
        )
        assertEquals(
            "USD -$53.16 / +$12.00 · JPY +JP¥1,200 · THB +50.00",
            buildHeaderMetaFootnote(
                foreignCurrencySummaries = listOf(
                    Triple("USD", 531_600L, 120_000L),
                    Triple("JPY", 0L, 12_000_000L),
                    Triple("THB", 0L, 500_000L)
                ),
                monthDiscountBaseMicros = 0L,
                monthFeeBaseMicros = 0L,
                baseCurrency = "CNY",
                privacyMasked = false
            )
        )
        assertEquals(
            "USD **** · 省 ****",
            buildHeaderMetaFootnote(
                foreignCurrencySummaries = listOf(Triple("USD", 531_600L, 0L)),
                monthDiscountBaseMicros = 50_000L,
                monthFeeBaseMicros = 0L,
                baseCurrency = "CNY",
                privacyMasked = true
            )
        )
    }

    @Test
    fun computeRefundedAmountByOrigTxId_sumsMultipleSameCurrencyAndCrossMonthRefunds() {
        val novMillis = 1_795_000_000_000L
        val decMillis = 1_797_600_000_000L
        val origExpense = tx(
            id = 100L,
            type = TransactionType.EXPENSE,
            timestamp = novMillis,
            origAmountMicros = 1_000_000L,
            origCurrency = "CNY"
        )
        val refundSameMonth = tx(
            id = 101L,
            type = TransactionType.INCOME,
            timestamp = novMillis + 3_600_000L,
            origAmountMicros = 300_000L,
            origCurrency = "CNY",
            refundOfTxId = 100L,
            status = TransactionStatus.CONFIRMED
        )
        val refundNextMonth = tx(
            id = 102L,
            type = TransactionType.INCOME,
            timestamp = decMillis,
            origAmountMicros = 255_000L,
            origCurrency = "CNY",
            refundOfTxId = 100L,
            status = TransactionStatus.CONFIRMED
        )
        val pendingRefund = tx(
            id = 103L,
            type = TransactionType.INCOME,
            timestamp = decMillis + 1_000L,
            origAmountMicros = 100_000L,
            origCurrency = "CNY",
            refundOfTxId = 100L,
            status = TransactionStatus.PENDING
        )
        val regularIncome = tx(
            id = 104L,
            type = TransactionType.INCOME,
            timestamp = novMillis,
            origAmountMicros = 500_000L,
            origCurrency = "CNY",
            refundOfTxId = null
        )

        val result = computeRefundedAmountByOrigTxId(
            transactions = listOf(origExpense, refundSameMonth, refundNextMonth, pendingRefund, regularIncome),
            rates = emptyList()
        )

        assertEquals(1, result.size)
        assertEquals(555_000L, result[100L])
    }

    @Test
    fun computeRefundedAmountByOrigTxId_convertsCrossCurrencyRefundToOrigCurrency() {
        val usdToCnyRate = ExchangeRateEntity(
            baseCurrency = "USD",
            targetCurrency = "CNY",
            rateMicros = 7_200_000L,
            date = "2026-11-27",
            source = "TEST"
        )
        val usdExpense = tx(
            id = 200L,
            type = TransactionType.EXPENSE,
            origAmountMicros = 1_000_000L,
            origCurrency = "USD"
        )
        val cnyRefundForUsd = tx(
            id = 201L,
            type = TransactionType.INCOME,
            origAmountMicros = 720_000L,
            origCurrency = "CNY",
            refundOfTxId = 200L
        )
        val cnyExpense = tx(
            id = 300L,
            type = TransactionType.EXPENSE,
            origAmountMicros = 1_000_000L,
            origCurrency = "CNY"
        )
        val usdRefundForCny = tx(
            id = 301L,
            type = TransactionType.INCOME,
            origAmountMicros = 100_000L,
            origCurrency = "USD",
            refundOfTxId = 300L
        )

        val result = computeRefundedAmountByOrigTxId(
            transactions = listOf(usdExpense, cnyRefundForUsd, cnyExpense, usdRefundForCny),
            rates = listOf(usdToCnyRate)
        )

        assertEquals(100_000L, result[200L])
        assertEquals(720_000L, result[300L])
        assertTrue(computeRefundedAmountByOrigTxId(listOf(usdExpense), listOf(usdToCnyRate)).isEmpty())
    }

    private fun tx(
        id: Long,
        type: String,
        timestamp: Long = 1_795_000_000_000L,
        origAmountMicros: Long,
        origCurrency: String = "CNY",
        refundOfTxId: Long? = null,
        status: String = TransactionStatus.CONFIRMED
    ) = TransactionEntity(
        id = id,
        type = type,
        timestamp = timestamp,
        origAmountMicros = origAmountMicros,
        origCurrency = origCurrency,
        accountId = 1L,
        accountAmountMicros = origAmountMicros,
        accountCurrency = origCurrency,
        refundOfTxId = refundOfTxId,
        status = status
    )
}

package com.yubrae.app.ui.components

import com.yubrae.app.data.db.ExchangeRateEntity
import com.yubrae.app.data.db.TransactionEntity
import com.yubrae.app.data.db.TransactionStatus
import com.yubrae.app.data.db.TransactionType
import com.yubrae.app.ui.screens.transactions.buildCompactRowDetail
import com.yubrae.app.ui.screens.transactions.buildHeaderMetaFootnote
import com.yubrae.app.ui.screens.transactions.formatRefundNetExpenseAmount
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
    fun cleanListRowNote_stripsUnicodeInvisibleAndFullWidthWhitespace() {
        assertEquals("", KairoFormats.cleanListRowNote("\u00A0\u00A0\u3000\u200B\uFEFF"))
        assertEquals("信用卡还款", KairoFormats.cleanListRowNote("\u00A0\u3000 · 信用卡还款"))
        assertEquals("信用卡还款", KairoFormats.cleanListRowNote("信用卡还款 · \u00A0\u3000 · \u200B\uFEFF"))
        assertEquals("瑞幸 咖啡", KairoFormats.cleanListRowNote("\uFEFF\u00A0瑞幸\u3000咖啡\u200B"))
        assertEquals("重庆畅通卡", KairoFormats.cleanListRowNote("重庆畅通卡\u00A0·\u00A0刷卡完成，交易金额：1.80元"))
    }

    @Test
    fun buildCompactRowDetail_eliminatesInvisibleWhitespaceBlankDots() {
        assertEquals(
            "信用卡还款",
            buildCompactRowDetail(
                merchant = "\u00A0\u00A0\u00A0",
                rawNote = "信用卡还款",
                line1Title = "招商储蓄卡 → 招商信用卡",
                categoryName = null,
                parentCategoryName = null,
                accountName = "招商储蓄卡",
                toAccountName = "招商信用卡"
            )
        )
        assertEquals(
            "信用卡还款",
            buildCompactRowDetail(
                merchant = "\u3000\u3000",
                rawNote = "\u200B信用卡还款\uFEFF",
                line1Title = "还款",
                categoryName = "还款",
                parentCategoryName = null,
                accountName = "银行卡",
                toAccountName = null
            )
        )
        assertEquals(
            "",
            buildCompactRowDetail(
                merchant = "\u00A0\u3000\u200B",
                rawNote = "\u00A0\u3000\uFEFF",
                line1Title = "餐饮",
                categoryName = "餐饮",
                parentCategoryName = null,
                accountName = "零钱",
                toAccountName = null
            )
        )
        assertEquals(
            "招商银行 · 信用卡还款",
            buildCompactRowDetail(
                merchant = "\u00A0招商银行\u3000",
                rawNote = "\u00A0信用卡还款\u200B",
                line1Title = "转账",
                categoryName = null,
                parentCategoryName = null,
                accountName = "零钱",
                toAccountName = "信用卡"
            )
        )
        assertEquals(
            "信用卡还款",
            buildCompactRowDetail(
                merchant = " \u00A0·\u3000 ",
                rawNote = "信用卡还款",
                line1Title = "转账",
                categoryName = null,
                parentCategoryName = null,
                accountName = "零钱",
                toAccountName = "信用卡"
            )
        )
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
        assertEquals("-\u00A0¥\u20091.80", KairoFormats.formatPlainSignedAmount(-18_000L, "CNY", "CNY"))
        assertEquals("+\u00A0¥\u2009106.00", KairoFormats.formatPlainSignedAmount(1_060_000L, "CNY", "CNY"))
        assertEquals("¥\u200950.00", KairoFormats.formatPlainSignedAmount(500_000L, "CNY", "CNY", showSign = false))
        assertEquals("¥\u20091,280.50", KairoFormats.formatPlainSignedAmount(12_805_000L, "CNY", "CNY", showSign = false))
        assertEquals("¥\u20090.00", KairoFormats.formatPlainSignedAmount(0L, "CNY", "CNY", showSign = true))
        assertEquals("-\u00A0¥\u20090.00", formatRefundNetExpenseAmount(0L, "CNY", "CNY"))
        assertEquals("-\u00A00.00 CHF", formatRefundNetExpenseAmount(0L, "CHF", "CNY"))
        assertEquals("-\u00A0¥\u200912.66", formatRefundNetExpenseAmount(126_600L, "CNY", "CNY"))
        assertEquals("-\u00A0¥\u20091.80", KairoFormats.formatPlainSignedAmount(-18_000L, "cny", "CNY"))

        assertEquals("-\u00A0$\u200953.16", KairoFormats.formatPlainSignedAmount(-531_600L, "USD", "CNY"))
        assertEquals("+\u00A0$\u200912.00", KairoFormats.formatPlainSignedAmount(120_000L, "USD", "CNY"))
        assertEquals("$\u200912.00", KairoFormats.formatPlainSignedAmount(120_000L, "USD", "CNY", showSign = false))
        assertEquals("-\u00A0€\u200925.00", KairoFormats.formatPlainSignedAmount(-250_000L, "EUR", "CNY"))
        assertEquals("-\u00A0JP¥\u20091,200", KairoFormats.formatPlainSignedAmount(-12_000_000L, "JPY", "CNY"))
        assertEquals("-\u00A012.00 CHF", KairoFormats.formatPlainSignedAmount(-120_000L, "CHF", "CNY"))
        assertEquals("12.00 CHF", KairoFormats.formatPlainSignedAmount(120_000L, "CHF", "CNY", showSign = false))
        assertEquals("+\u00A050.00 THB", KairoFormats.formatPlainSignedAmount(500_000L, "THB", "CNY"))

        assertEquals("-\u00A0$\u200910.00", KairoFormats.formatPlainSignedAmount(-100_000L, "USD", "USD"))
        assertEquals("+\u00A0$\u200910.00", KairoFormats.formatPlainSignedAmount(100_000L, "USD", "USD"))
        assertEquals("$\u20090.00", KairoFormats.formatPlainSignedAmount(0L, "USD", "USD", showSign = true))
        assertEquals("-\u00A012.00 CHF", KairoFormats.formatPlainSignedAmount(-120_000L, "CHF", "CHF"))
        assertEquals("-\u00A0¥\u200910.00", KairoFormats.formatPlainSignedAmount(-100_000L, "CNY", "USD"))
    }

    @Test
    fun buildHeaderMetaFootnote_restoresForeignCurrencySymbolsWithoutDuplicatingCode() {
        assertEquals(
            "USD -\u00A0$\u200953.16 · EUR -\u00A0€\u200925.00 · CHF -\u00A012.00 · 省 ¥\u20095.00 · 费 ¥\u20091.20",
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
            "USD -\u00A0$\u200953.16 / +\u00A0$\u200912.00 · JPY +\u00A0JP¥\u20091,200 · THB +\u00A050.00",
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

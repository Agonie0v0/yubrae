package com.kairoledger.app.capture.screen

import com.kairoledger.app.capture.CaptureTestFixtures
import com.kairoledger.app.capture.FakeAccountAliasDao
import com.kairoledger.app.capture.FakeAccountDao
import com.kairoledger.app.capture.FakeCategoryDao
import com.kairoledger.app.capture.FakeParseRuleDao
import com.kairoledger.app.core.model.MoneyAmount
import com.kairoledger.app.data.db.AccountAliasEntity
import com.kairoledger.app.data.db.AccountEntity
import com.kairoledger.app.data.db.CategoryEntity
import com.kairoledger.app.data.db.TransactionType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class ScreenBillParserTest {

    private val testZone = ZoneId.of("Asia/Shanghai")
    private val parser = ScreenBillParser(zoneId = testZone)

    private lateinit var accounts: List<AccountEntity>
    private lateinit var aliases: List<AccountAliasEntity>
    private lateinit var categories: List<CategoryEntity>

    @Before
    fun setup() = runTest {
        val accountDao = FakeAccountDao()
        val aliasDao = FakeAccountAliasDao()
        val categoryDao = FakeCategoryDao()
        val ruleDao = FakeParseRuleDao()
        CaptureTestFixtures.seedFixtures(accountDao, aliasDao, categoryDao, ruleDao)

        accounts = accountDao.getAll()
        aliases = aliasDao.getEnabled()
        categories = categoryDao.getAll()
    }

    @Test
    fun parsesWeChatPaySuccessPageWithSplitCurrencyNodesAndReceiptIncome() {
        val paySnapshot = ScreenSnapshot(
            packageName = "com.tencent.mm",
            className = "com.tencent.mm.plugin.wallet_core.ui.WalletPaidSuccessUI",
            texts = listOf("支付成功", "星巴克", "¥", "32.00", "完成"),
            capturedAtMillis = 1_710_500_000_000L
        )
        val outcome = parser.parse(paySnapshot, aliases, accounts, categories)
        assertEquals(ScreenPageKind.PAY_SUCCESS, outcome.pageKind)
        assertFalse(outcome.shouldFallbackToLlm)
        assertEquals("", outcome.ignoredReason)
        val draft = outcome.draft
        assertNotNull(draft)
        assertEquals(TransactionType.EXPENSE, draft!!.type)
        assertEquals(MoneyAmount.parseToMicros("32.00"), draft.origAmountMicros)
        assertEquals("CNY", draft.origCurrency)
        assertEquals("星巴克", draft.merchant)
        assertEquals("餐饮美食", draft.categoryName)
        assertEquals("微信零钱", accounts.first { it.id == draft.accountId }.name)
        assertTrue(outcome.dedupFingerprint.startsWith("SCREEN:com.tencent.mm:EXPENSE:CNY:"))

        val receiveSnapshot = ScreenSnapshot(
            packageName = "com.tencent.mm",
            className = "com.tencent.mm.plugin.remittance.ui.RemittanceResUI",
            texts = listOf("已收款", "已存入零钱", "¥88.00", "完成"),
            capturedAtMillis = 1_710_500_120_000L
        )
        val receiveOutcome = parser.parse(receiveSnapshot, aliases, accounts, categories)
        assertEquals(ScreenPageKind.PAY_SUCCESS, receiveOutcome.pageKind)
        assertNotNull(receiveOutcome.draft)
        assertEquals(TransactionType.INCOME, receiveOutcome.draft!!.type)
        assertEquals(MoneyAmount.parseToMicros("88.00"), receiveOutcome.draft!!.origAmountMicros)
    }

    @Test
    fun parsesWeChatBillDetailWithCardLast4HistoryTimeAndOrderNo() {
        val snapshot = ScreenSnapshot(
            packageName = "com.tencent.mm",
            className = "com.tencent.mm.plugin.wallet_core.ui.WalletOrderInfoNewUI",
            texts = listOf(
                "账单详情",
                "星巴克",
                "-45.50",
                "当前状态", "支付成功",
                "商品", "星巴克拿铁",
                "收款方全称", "上海星巴克咖啡经营有限公司",
                "支付时间", "2026-03-15 19:42:18",
                "支付方式", "招商银行信用卡(6688)",
                "交易单号", "42000021562026031599887766",
                "复制",
                "商户单号", "M202603150001",
                "申请退款",
                "对订单有疑惑"
            ),
            capturedAtMillis = 1_775_000_000_000L
        )

        val outcome = parser.parse(snapshot, aliases, accounts, categories)
        assertEquals(ScreenPageKind.BILL_DETAIL, outcome.pageKind)
        assertEquals("42000021562026031599887766", outcome.orderNo)
        assertEquals("ORDER:42000021562026031599887766", outcome.dedupFingerprint)

        val draft = outcome.draft
        assertNotNull(draft)
        assertEquals(TransactionType.EXPENSE, draft!!.type)
        assertEquals(MoneyAmount.parseToMicros("45.50"), draft.origAmountMicros)
        assertEquals("CNY", draft.origCurrency)
        assertEquals("6688", draft.accountHint)
        assertEquals("招商信用卡6688", accounts.first { it.id == draft.accountId }.name)
        assertEquals("星巴克", draft.merchant)
        assertEquals("餐饮美食", draft.categoryName)
        assertEquals("42000021562026031599887766", draft.externalOrderNo)

        val expectedMillis = LocalDateTime.of(2026, 3, 15, 19, 42, 18)
            .atZone(testZone)
            .toInstant()
            .toEpochMilli()
        assertEquals(expectedMillis, draft.timestamp)
    }

    @Test
    fun parsesHistoricalTimeFormatsAccurately() {
        val slashTimeSnapshot = ScreenSnapshot(
            packageName = "com.eg.android.AlipayGphone",
            className = "BillDetailActivity",
            texts = listOf(
                "账单详情",
                "瑞幸咖啡",
                "-18.50",
                "当前状态：交易成功",
                "创建时间：2026年02月14日 08:30:05",
                "付款方式：花呗",
                "订单号：2026021400012345"
            )
        )
        val outcome1 = parser.parse(slashTimeSnapshot, aliases, accounts, categories)
        assertEquals(ScreenPageKind.BILL_DETAIL, outcome1.pageKind)
        assertEquals(
            LocalDateTime.of(2026, 2, 14, 8, 30, 5).atZone(testZone).toInstant().toEpochMilli(),
            outcome1.draft!!.timestamp
        )

        val noSecondsSnapshot = ScreenSnapshot(
            packageName = "com.cmbchina.ccd.pluto.cmbActivity",
            className = "OrderDetailActivity",
            texts = listOf(
                "交易详情",
                "滴滴出行",
                "¥28.60",
                "交易时间", "2026/01/20 21:15",
                "付款方式", "招商银行信用卡(6688)",
                "流水号", "CMB202601209988"
            )
        )
        val outcome2 = parser.parse(noSecondsSnapshot, aliases, accounts, categories)
        assertEquals(ScreenPageKind.BILL_DETAIL, outcome2.pageKind)
        assertEquals(
            LocalDateTime.of(2026, 1, 20, 21, 15, 0).atZone(testZone).toInstant().toEpochMilli(),
            outcome2.draft!!.timestamp
        )
        assertEquals("招商信用卡6688", accounts.first { it.id == outcome2.draft!!.accountId }.name)
    }

    @Test
    fun parsesAlipayPaySuccessEvenWhenBillDetailLinkIsPresent() {
        val snapshot = ScreenSnapshot(
            packageName = "com.eg.android.AlipayGphone",
            className = "com.alipay.android.msp.ui.views.MspContainerActivity",
            texts = listOf(
                "支付成功",
                "账单详情",
                "滴滴出行",
                "¥28.60",
                "付款方式",
                "支付宝",
                "完成"
            )
        )
        val outcome = parser.parse(snapshot, aliases, accounts, categories)
        assertEquals(ScreenPageKind.PAY_SUCCESS, outcome.pageKind)
        val draft = outcome.draft
        assertNotNull(draft)
        assertEquals(TransactionType.EXPENSE, draft!!.type)
        assertEquals(MoneyAmount.parseToMicros("28.60"), draft.origAmountMicros)
        assertEquals("CNY", draft.origCurrency)
        assertEquals("滴滴出行", draft.merchant)
        assertEquals("交通出行", draft.categoryName)
        assertEquals("支付宝", accounts.first { it.id == draft.accountId }.name)
    }

    @Test
    fun parsesAlipayForeignCurrencyAndCnySettlementForFxLossReverseCalculation() {
        val snapshot = ScreenSnapshot(
            packageName = "com.eg.android.AlipayGphone",
            className = "com.alipay.mobile.bill.detail.ui.BillDetailActivity",
            texts = listOf(
                "账单详情",
                "OpenAI",
                "-109.20",
                "消费金额 USD 15.00",
                "当前状态", "交易成功",
                "商品说明", "ChatGPT Plus Subscription",
                "参考汇率", "1 USD = 7.28 CNY",
                "付款方式", "招商银行信用卡(6688)",
                "创建时间", "2026-03-16 09:15:30",
                "订单号", "202603162200148899001122"
            )
        )
        val outcome = parser.parse(snapshot, aliases, accounts, categories)
        assertEquals(ScreenPageKind.BILL_DETAIL, outcome.pageKind)
        assertEquals("202603162200148899001122", outcome.orderNo)

        val draft = outcome.draft
        assertNotNull(draft)
        assertEquals(TransactionType.EXPENSE, draft!!.type)
        assertEquals("USD", draft.origCurrency)
        assertEquals(MoneyAmount.parseToMicros("15.00"), draft.origAmountMicros)
        assertEquals(1_092_000L, draft.manualAccountAmountMicros)
        assertEquals(7_280_000L, MoneyAmount.computeRate(draft.origAmountMicros, draft.manualAccountAmountMicros!!))
        assertEquals("招商信用卡6688", accounts.first { it.id == draft.accountId }.name)
        assertEquals("OpenAI", draft.merchant)
        assertEquals("数字订阅", draft.categoryName)

        // 单行格式美元符号 + 实付人民币
        val symbolSnapshot = ScreenSnapshot(
            packageName = "com.eg.android.AlipayGphone",
            className = "BillDetailActivity",
            texts = listOf(
                "账单详情",
                "OpenAI",
                "-$15.00",
                "实付 ¥109.20",
                "当前状态：支付成功",
                "付款方式：支付宝",
                "交易单号：202603162200148899001199"
            )
        )
        val symbolOutcome = parser.parse(symbolSnapshot, aliases, accounts, categories)
        assertEquals("USD", symbolOutcome.draft!!.origCurrency)
        assertEquals(MoneyAmount.parseToMicros("15.00"), symbolOutcome.draft!!.origAmountMicros)
        assertEquals(1_092_000L, symbolOutcome.draft!!.manualAccountAmountMicros)
        assertEquals("支付宝", accounts.first { it.id == symbolOutcome.draft!!.accountId }.name)
    }

    @Test
    fun parsesRefundScreensAsIncome() {
        val refundDetail = ScreenSnapshot(
            packageName = "com.eg.android.AlipayGphone",
            className = "BillDetailActivity",
            texts = listOf(
                "账单详情",
                "淘宝",
                "+59.90",
                "当前状态", "已退款",
                "退款时间", "2026-03-17 11:20:00",
                "退款去向", "招商银行信用卡(6688)",
                "交易单号", "REFUND202603170001"
            )
        )
        val outcome = parser.parse(refundDetail, aliases, accounts, categories)
        assertEquals(ScreenPageKind.BILL_DETAIL, outcome.pageKind)
        val draft = outcome.draft
        assertNotNull(draft)
        assertEquals(TransactionType.INCOME, draft!!.type)
        assertEquals(MoneyAmount.parseToMicros("59.90"), draft.origAmountMicros)
        assertEquals("报销退款", draft.categoryName)
        assertEquals("招商信用卡6688", accounts.first { it.id == draft.accountId }.name)

        val refundSuccess = ScreenSnapshot(
            packageName = "com.tencent.mm",
            className = "WalletRefundSuccessUI",
            texts = listOf("退款成功", "退款到账", "京东商城", "¥128.00", "完成")
        )
        val successOutcome = parser.parse(refundSuccess, aliases, accounts, categories)
        assertEquals(ScreenPageKind.PAY_SUCCESS, successOutcome.pageKind)
        assertEquals(TransactionType.INCOME, successOutcome.draft!!.type)
        assertEquals(MoneyAmount.parseToMicros("128.00"), successOutcome.draft!!.origAmountMicros)
    }

    @Test
    fun ignoresClosedFailedCancelledAndPendingPaymentScreens() {
        val closedStatuses = listOf("交易关闭", "已关闭", "支付失败", "订单已取消", "等待付款", "待支付")
        for (status in closedStatuses) {
            val detail = ScreenSnapshot(
                packageName = "com.eg.android.AlipayGphone",
                className = "BillDetailActivity",
                texts = listOf(
                    "账单详情",
                    "星巴克",
                    "¥32.00",
                    "当前状态", status,
                    "创建时间", "2026-03-18 10:00:00",
                    "订单号", "2026031800001"
                )
            )
            val outcome = parser.parse(detail, aliases, accounts, categories)
            assertNull("Expected null draft for status=$status", outcome.draft)
            assertFalse(outcome.shouldFallbackToLlm)
            assertTrue("Expected ignoredReason for status=$status", outcome.ignoredReason.isNotBlank())
        }

        val checkoutPage = ScreenSnapshot(
            packageName = "com.tencent.mm",
            className = "WalletPayUI",
            texts = listOf("确认付款", "待付款", "星巴克", "¥32.00", "立即支付")
        )
        val checkoutOutcome = parser.parse(checkoutPage, aliases, accounts, categories)
        assertEquals(ScreenPageKind.UNKNOWN, checkoutOutcome.pageKind)
        assertNull(checkoutOutcome.draft)
        assertFalse(checkoutOutcome.shouldFallbackToLlm)
    }

    @Test
    fun doesNotMisfireOnWeChatChatHomeBillListOrUnwatchedPackages() {
        val wechatChat = ScreenSnapshot(
            packageName = "com.tencent.mm",
            className = "com.tencent.mm.ui.LauncherUI",
            texts = listOf("张三", "按住 说话", "表情", "我刚向星巴克支付成功32.00元", "发送")
        )
        val chatOutcome = parser.parse(wechatChat, aliases, accounts, categories)
        assertEquals(ScreenPageKind.UNKNOWN, chatOutcome.pageKind)
        assertNull(chatOutcome.draft)
        assertFalse(chatOutcome.shouldFallbackToLlm)

        val wechatHome = ScreenSnapshot(
            packageName = "com.tencent.mm",
            className = "com.tencent.mm.ui.LauncherUI",
            texts = listOf("微信", "通讯录", "发现", "我", "微信支付", "付款32.00元")
        )
        val homeOutcome = parser.parse(wechatHome, aliases, accounts, categories)
        assertEquals(ScreenPageKind.UNKNOWN, homeOutcome.pageKind)
        assertNull(homeOutcome.draft)

        val billListOverview = ScreenSnapshot(
            packageName = "com.eg.android.AlipayGphone",
            className = "BillListActivity",
            texts = listOf(
                "全部账单", "筛选", "2026年3月", "支出 ¥1,250.00", "收入 ¥8,000.00",
                "星巴克", "-32.00", "滴滴出行", "-28.60"
            )
        )
        val listOutcome = parser.parse(billListOverview, aliases, accounts, categories)
        assertEquals(ScreenPageKind.UNKNOWN, listOutcome.pageKind)
        assertNull(listOutcome.draft)

        val selfAppSnapshot = ScreenSnapshot(
            packageName = "com.kairoledger.app",
            className = "MainActivity",
            texts = listOf("账单详情", "星巴克", "¥32.00", "当前状态", "支付成功", "交易单号", "420000123456")
        )
        val selfOutcome = parser.parse(selfAppSnapshot, aliases, accounts, categories)
        assertEquals(ScreenPageKind.UNKNOWN, selfOutcome.pageKind)
        assertNull(selfOutcome.draft)
    }

    @Test
    fun producesConsistentDedupFingerprintForSameOrderNoAcrossFormats() {
        val twoLineSnapshot = ScreenSnapshot(
            packageName = "com.tencent.mm",
            className = "WalletOrderInfoNewUI",
            texts = listOf(
                "账单详情",
                "星巴克",
                "-32.00",
                "当前状态", "支付成功",
                "支付时间", "2026-03-15 10:00:00",
                "支付方式", "零钱",
                "交易单号", "42000099887766554433"
            ),
            capturedAtMillis = 1_710_000_000_000L
        )
        val inlineSnapshot = ScreenSnapshot(
            packageName = "com.tencent.mm",
            className = "WalletOrderInfoNewUI",
            texts = listOf(
                "账单详情",
                "星巴克",
                "¥32.00",
                "当前状态：支付成功",
                "支付时间：2026-03-15 10:00:00",
                "支付方式：零钱",
                "交易单号：42000099887766554433 复制"
            ),
            capturedAtMillis = 1_710_999_999_999L
        )

        val outcome1 = parser.parse(twoLineSnapshot, aliases, accounts, categories)
        val outcome2 = parser.parse(inlineSnapshot, aliases, accounts, categories)

        assertEquals("42000099887766554433", outcome1.orderNo)
        assertEquals(outcome1.orderNo, outcome2.orderNo)
        assertEquals("ORDER:42000099887766554433", outcome1.dedupFingerprint)
        assertEquals(outcome1.dedupFingerprint, outcome2.dedupFingerprint)
        assertEquals(outcome1.draft!!.externalOrderNo, outcome2.draft!!.externalOrderNo)
    }

    @Test
    fun signalsLlmFallbackWhenConfirmedBillPageHasNoExtractableAmountAndChecksPackageWhitelist() {
        val noAmountPaySuccess = ScreenSnapshot(
            packageName = "com.cmbchina.ccd.pluto.cmbActivity",
            className = "PayResultActivity",
            texts = listOf("支付成功", "商户：星巴克", "支付方式：尾号6688", "金额已隐藏", "完成")
        )
        val llmOutcome = parser.parse(noAmountPaySuccess, aliases, accounts, categories)
        assertEquals(ScreenPageKind.PAY_SUCCESS, llmOutcome.pageKind)
        assertNull(llmOutcome.draft)
        assertTrue(llmOutcome.shouldFallbackToLlm)
        assertTrue(llmOutcome.summaryText.contains("星巴克"))

        assertTrue(ScreenBillParser.isWatchedPackage("com.tencent.mm"))
        assertTrue(ScreenBillParser.isWatchedPackage("com.eg.android.AlipayGphone"))
        assertTrue(ScreenBillParser.isWatchedPackage("com.unionpay"))
        assertTrue(ScreenBillParser.isWatchedPackage("com.cmbchina.ccd.pluto.cmbActivity"))
        assertTrue(ScreenBillParser.isWatchedPackage("com.icbc"))
        assertTrue(ScreenBillParser.isWatchedPackage("com.chinamworld.main"))
        assertTrue(ScreenBillParser.isWatchedPackage("com.android.bankabc"))
        assertTrue(ScreenBillParser.isWatchedPackage("com.pingan.paces.ccms"))
        assertTrue(ScreenBillParser.isWatchedPackage("com.wise.android"))
        assertTrue(ScreenBillParser.isWatchedPackage("com.paypal.android.p2pmobile"))

        assertFalse(ScreenBillParser.isWatchedPackage(""))
        assertFalse(ScreenBillParser.isWatchedPackage("com.kairoledger.app"))
        assertFalse(ScreenBillParser.isWatchedPackage("com.android.settings"))
    }

    @Test
    fun parsesRealCapturedWeChatWebViewBillDetailPreferringHeroStoreNameAndIgnoringBottomAllBillsButton() {
        val realSnapshot = ScreenSnapshot(
            packageName = "com.tencent.mm",
            className = "com.tencent.mm.plugin.webview.ui.tools.MMWebViewUI",
            texts = listOf(
                "哆哆酸辣粉",
                "-16.00",
                "当前状态", "支付成功",
                "支付时间", "2026年11月18日 18:41:35",
                "商品", "嗨客多-单号：3012611181841252285688（哆哆酸辣粉）",
                "商户全称", "商户_王禹睿",
                "收单机构", "随行付支付有限公司",
                "支付方式", "零钱",
                "交易单号", "4500000576202611189062916577",
                "商户单号", "83620261118329900796",
                "账单服务", "发起群收款", "在此商户的交易", "申请电子凭证",
                "本服务由财付通提供",
                "全部账单"
            )
        )
        val outcome = parser.parse(realSnapshot, aliases, accounts, categories)
        assertEquals(ScreenPageKind.BILL_DETAIL, outcome.pageKind)
        val draft = outcome.draft
        assertNotNull(draft)
        assertEquals(TransactionType.EXPENSE, draft!!.type)
        assertEquals(MoneyAmount.parseToMicros("16.00"), draft.origAmountMicros)
        assertEquals("CNY", draft.origCurrency)
        assertEquals("哆哆酸辣粉", draft.merchant)
        assertEquals("餐饮美食", draft.categoryName)
        assertEquals("4500000576202611189062916577", draft.externalOrderNo)
        assertEquals("微信零钱", accounts.first { it.id == draft.accountId }.name)
    }

    @Test
    fun parsesWeChatPersonalTransferDetailWithCounterpartyReceivedAndBottomAllBills() {
        val transferSnapshot = ScreenSnapshot(
            packageName = "com.tencent.mm",
            className = "com.tencent.mm.plugin.webview.ui.tools.MMWebViewUI",
            texts = listOf(
                "向张三转账",
                "-200.00",
                "当前状态", "对方已收钱",
                "转账说明", "聚餐AA",
                "转账时间", "2026-11-20 20:10:00",
                "收钱时间", "2026-11-20 20:11:15",
                "支付方式", "招商银行信用卡(6688)",
                "转账单号", "1000050001202611200012345",
                "账单服务", "定位到聊天位置", "查看往来记录",
                "本服务由财付通提供",
                "全部账单"
            )
        )
        val outcome = parser.parse(transferSnapshot, aliases, accounts, categories)
        assertEquals(ScreenPageKind.BILL_DETAIL, outcome.pageKind)
        val draft = outcome.draft
        assertNotNull(draft)
        assertEquals(TransactionType.EXPENSE, draft!!.type)
        assertEquals(MoneyAmount.parseToMicros("200.00"), draft.origAmountMicros)
        assertEquals("招商信用卡6688", accounts.first { it.id == draft.accountId }.name)
        assertEquals("1000050001202611200012345", draft.externalOrderNo)
    }

    @Test
    fun parsesMinimalWeChatRedPacketOrMiniDetailWithServiceFooterAndBottomAllBills() {
        val redPacketSnapshot = ScreenSnapshot(
            packageName = "com.tencent.mm",
            className = "com.tencent.mm.plugin.webview.ui.tools.MMWebViewUI",
            texts = listOf(
                "微信红包",
                "66.66",
                "当前状态", "对方已领取",
                "本服务由财付通提供",
                "全部账单"
            )
        )
        val outcome = parser.parse(redPacketSnapshot, aliases, accounts, categories)
        assertEquals(ScreenPageKind.BILL_DETAIL, outcome.pageKind)
        val draft = outcome.draft
        assertNotNull(draft)
        assertEquals(TransactionType.EXPENSE, draft!!.type)
        assertEquals(MoneyAmount.parseToMicros("66.66"), draft.origAmountMicros)
    }

    @Test
    fun parsesWeChatWebViewBillDetailWhenTopToolbarNoisePushesHeroLinesBeyondIndex12() {
        val snapshot = ScreenSnapshot(
            packageName = "com.tencent.mm",
            className = "com.tencent.mm.plugin.webview.ui.tools.MMWebViewUI",
            texts = listOf(
                "返回",
                "关闭",
                "更多",
                "网页由 payapp.weixin.qq.com 提供",
                "分享",
                "复制链接",
                "在浏览器打开",
                "刷新",
                "搜索页面内容",
                "字体大小",
                "投诉",
                "微信支付",
                "哆哆酸辣粉",
                "-16.00",
                "当前状态", "支付完成",
                "支付时间", "2026-11-22 12:30:05",
                "商户全称", "商户_王禹睿",
                "支付方式", "零钱",
                "交易单号", "45000005762026112299990001",
                "本服务由财付通提供",
                "全部账单"
            )
        )
        val outcome = parser.parse(snapshot, aliases, accounts, categories)
        assertEquals(ScreenPageKind.BILL_DETAIL, outcome.pageKind)
        val draft = outcome.draft
        assertNotNull(draft)
        assertEquals(TransactionType.EXPENSE, draft!!.type)
        assertEquals(MoneyAmount.parseToMicros("16.00"), draft.origAmountMicros)
        assertEquals("CNY", draft.origCurrency)
        assertEquals("哆哆酸辣粉", draft.merchant)
        assertEquals("餐饮美食", draft.categoryName)
        assertEquals("45000005762026112299990001", draft.externalOrderNo)
        assertEquals("微信零钱", accounts.first { it.id == draft.accountId }.name)
    }

    @Test
    fun parsesWeChatRedPacketBillDetailWithIssuedTimeAndExhaustedStatus() {
        val snapshot = ScreenSnapshot(
            packageName = "com.tencent.mm",
            className = "com.tencent.mm.plugin.webview.ui.tools.MMWebViewUI",
            texts = listOf(
                "微信红包",
                "-20.00",
                "当前状态", "已被领完",
                "发出时间", "2026-11-22 15:18:22",
                "红包个数", "1个",
                "支付方式", "零钱",
                "交易单号", "10000399012026112200887766",
                "查看红包详情",
                "本服务由财付通提供"
            )
        )
        val outcome = parser.parse(snapshot, aliases, accounts, categories)
        assertEquals(ScreenPageKind.BILL_DETAIL, outcome.pageKind)
        val draft = outcome.draft
        assertNotNull(draft)
        assertEquals(TransactionType.EXPENSE, draft!!.type)
        assertEquals(MoneyAmount.parseToMicros("20.00"), draft.origAmountMicros)
        assertEquals("CNY", draft.origCurrency)
        assertEquals("微信红包", draft.merchant)
        assertEquals("10000399012026112200887766", draft.externalOrderNo)
        assertEquals("微信零钱", accounts.first { it.id == draft.accountId }.name)

        val expectedMillis = LocalDateTime.of(2026, 11, 22, 15, 18, 22)
            .atZone(testZone)
            .toInstant()
            .toEpochMilli()
        assertEquals(expectedMillis, draft.timestamp)
    }

    @Test
    fun blocksWeChatAndAlipayChatOrFeedWindowClassesWhileAllowingRealBillActivities() {
        // 微信聊天 / 公众号 / 服务号会话类拦截（含大小写变体）
        assertTrue(ScreenBillParser.isBlockedChatOrFeedWindow("com.tencent.mm", "com.tencent.mm.ui.LauncherUI"))
        assertTrue(ScreenBillParser.isBlockedChatOrFeedWindow("com.tencent.mm", "com.tencent.mm.ui.chatting.ChattingUI"))
        assertTrue(ScreenBillParser.isBlockedChatOrFeedWindow("com.tencent.mm", "com.tencent.mm.ui.bizchat.BizChatConversationUI"))
        assertTrue(ScreenBillParser.isBlockedChatOrFeedWindow("com.tencent.mm", "com.tencent.mm.plugin.brandservice.ui.timeline.BizTimeLineUI"))
        assertTrue(ScreenBillParser.isBlockedChatOrFeedWindow("com.tencent.mm", "com.tencent.mm.ui.conversation.MainConversationUI"))
        assertTrue(ScreenBillParser.isBlockedChatOrFeedWindow("com.tencent.mm", "launcherui"))

        // 支付宝聊天 / 登录 / 首页 Tab 拦截
        assertTrue(ScreenBillParser.isBlockedChatOrFeedWindow("com.eg.android.AlipayGphone", "com.alipay.mobile.chatapp.ui.ChatMessageActivity"))
        assertTrue(ScreenBillParser.isBlockedChatOrFeedWindow("com.eg.android.AlipayGphone", "com.alipay.mobile.social.SocialChatActivity"))
        assertTrue(ScreenBillParser.isBlockedChatOrFeedWindow("com.eg.android.AlipayGphone", "com.alipay.mobile.auth.AlipayLogin"))
        assertTrue(ScreenBillParser.isBlockedChatOrFeedWindow("com.eg.android.AlipayGphone", "com.eg.android.AlipayGphone.TabLauncher"))

        // 真实支付成功页与账单详情页绝不能被拦截
        assertFalse(ScreenBillParser.isBlockedChatOrFeedWindow("com.tencent.mm", "com.tencent.mm.plugin.webview.ui.tools.MMWebViewUI"))
        assertFalse(ScreenBillParser.isBlockedChatOrFeedWindow("com.tencent.mm", "com.tencent.mm.plugin.wallet_core.ui.WalletOrderInfoNewUI"))
        assertFalse(ScreenBillParser.isBlockedChatOrFeedWindow("com.tencent.mm", "com.tencent.mm.plugin.wallet_core.ui.WalletPaidSuccessUI"))
        assertFalse(ScreenBillParser.isBlockedChatOrFeedWindow("com.tencent.mm", "com.tencent.mm.plugin.mall.ui.MallOrderDetailInfoUI"))
        assertFalse(ScreenBillParser.isBlockedChatOrFeedWindow("com.tencent.mm", "com.tencent.mm.framework.app.UIPageFragmentActivity"))
        assertFalse(ScreenBillParser.isBlockedChatOrFeedWindow("com.tencent.mm", "com.tencent.mm.plugin.remittance.ui.RemittanceDetailUI"))
        assertFalse(ScreenBillParser.isBlockedChatOrFeedWindow("com.tencent.mm", ""))
    }

    @Test
    fun ignoresWeChatPayServiceAccountSingleVoucherCardInConversation() {
        val withWindowClass = ScreenSnapshot(
            packageName = "com.tencent.mm",
            className = "com.tencent.mm.ui.LauncherUI",
            texts = listOf(
                "返回",
                "微信支付",
                "14:25",
                "微信支付凭证",
                "付款金额", "¥38.50",
                "收款商户", "星巴克咖啡",
                "支付方式", "零钱",
                "查看账单详情",
                "我的账单",
                "支付服务"
            )
        )
        val outcome1 = parser.parse(withWindowClass, aliases, accounts, categories)
        assertEquals(ScreenPageKind.UNKNOWN, outcome1.pageKind)
        assertNull(outcome1.draft)
        assertFalse(outcome1.shouldFallbackToLlm)

        // 即使类名退化为空，仅凭服务号凭证卡片特征（查看账单详情/收款商户/付款金额）也绝不误触发
        val degradedClassSnapshot = ScreenSnapshot(
            packageName = "com.tencent.mm",
            className = "",
            texts = listOf(
                "微信支付",
                "微信支付凭证",
                "付款金额", "¥38.50",
                "收款商户", "星巴克咖啡",
                "查看账单详情",
                "我的账单",
                "支付服务"
            )
        )
        val outcome2 = parser.parse(degradedClassSnapshot, aliases, accounts, categories)
        assertEquals(ScreenPageKind.UNKNOWN, outcome2.pageKind)
        assertNull(outcome2.draft)
        assertFalse(outcome2.shouldFallbackToLlm)

        // 标题滑出屏幕、只剩卡片正文与底部服务号菜单/跳详情按钮时仍拦截
        val scrolledCardOnly = ScreenSnapshot(
            packageName = "com.tencent.mm",
            className = "android.widget.FrameLayout",
            texts = listOf(
                "付款金额", "¥38.50",
                "收款商户", "星巴克咖啡",
                "查看账单详情",
                "我的账单",
                "更多服务"
            )
        )
        val outcome3 = parser.parse(scrolledCardOnly, aliases, accounts, categories)
        assertEquals(ScreenPageKind.UNKNOWN, outcome3.pageKind)
        assertNull(outcome3.draft)
    }

    @Test
    fun ignoresWeChatPayServiceAccountScrollingMultiCardFeed() {
        val multiCardFeed = ScreenSnapshot(
            packageName = "com.tencent.mm",
            className = "android.widget.FrameLayout",
            texts = listOf(
                "微信支付",
                "昨天 18:40",
                "付款成功通知",
                "付款金额", "¥16.00",
                "收款商户", "哆哆酸辣粉",
                "支付时间", "2026-11-21 18:40:12",
                "查看账单详情",
                "12:15",
                "付款金额", "¥28.00",
                "收款商户", "瑞幸咖啡",
                "支付时间", "2026-11-22 12:15:03",
                "查看账单详情"
            )
        )
        val outcome = parser.parse(multiCardFeed, aliases, accounts, categories)
        assertEquals(ScreenPageKind.UNKNOWN, outcome.pageKind)
        assertNull(outcome.draft)
        assertFalse(outcome.shouldFallbackToLlm)

        // 仅存在两个聊天时间分隔线 + 查看账单详情按钮（如顶部字段刚好滑出）同样拦截
        val multiTimeDividerFeed = ScreenSnapshot(
            packageName = "com.tencent.mm",
            className = "",
            texts = listOf(
                "昨天 18:40",
                "星巴克",
                "¥32.00",
                "查看账单详情",
                "2026年11月22日",
                "滴滴出行",
                "¥24.50"
            )
        )
        val outcome2 = parser.parse(multiTimeDividerFeed, aliases, accounts, categories)
        assertEquals(ScreenPageKind.UNKNOWN, outcome2.pageKind)
        assertNull(outcome2.draft)
    }

    @Test
    fun ignoresWeChatChatWithRedPacketAndTransferBubbles() {
        val chattingWindow = ScreenSnapshot(
            packageName = "com.tencent.mm",
            className = "com.tencent.mm.ui.chatting.ChattingUI",
            texts = listOf(
                "返回",
                "张三",
                "昨天 20:10",
                "微信红包",
                "恭喜发财，大吉大利",
                "已被领完",
                "微信转账",
                "¥200.00",
                "对方已收钱"
            )
        )
        val outcome1 = parser.parse(chattingWindow, aliases, accounts, categories)
        assertEquals(ScreenPageKind.UNKNOWN, outcome1.pageKind)
        assertNull(outcome1.draft)

        // 类名退化为空时，聊天页面红包与转账消息气泡同样绝不误触发
        val degradedChatSnapshot = ScreenSnapshot(
            packageName = "com.tencent.mm",
            className = "",
            texts = listOf(
                "返回",
                "张三",
                "14:20",
                "微信红包",
                "恭喜发财，大吉大利",
                "已被领完",
                "14:22",
                "微信转账",
                "¥200.00",
                "朋友已确认收钱"
            )
        )
        val outcome2 = parser.parse(degradedChatSnapshot, aliases, accounts, categories)
        assertEquals(ScreenPageKind.UNKNOWN, outcome2.pageKind)
        assertNull(outcome2.draft)
        assertFalse(outcome2.shouldFallbackToLlm)
    }

    @Test
    fun ignoresAlipayChatAndServiceFeedMessages() {
        val alipayChat = ScreenSnapshot(
            packageName = "com.eg.android.AlipayGphone",
            className = "com.alipay.mobile.chatapp.ui.ChatMessageActivity",
            texts = listOf(
                "返回",
                "账单助手",
                "动账提醒",
                "付款金额", "¥19.80",
                "收款方", "美团外卖",
                "点击查看详情"
            )
        )
        val outcome1 = parser.parse(alipayChat, aliases, accounts, categories)
        assertEquals(ScreenPageKind.UNKNOWN, outcome1.pageKind)
        assertNull(outcome1.draft)

        val alipayFeedDegraded = ScreenSnapshot(
            packageName = "com.eg.android.AlipayGphone",
            className = "",
            texts = listOf(
                "账单助手",
                "10:30",
                "付款金额", "¥19.80",
                "交易对方", "瑞幸咖啡",
                "查看消费明细",
                "11:45",
                "付款金额", "¥35.00",
                "查看账单详情"
            )
        )
        val outcome2 = parser.parse(alipayFeedDegraded, aliases, accounts, categories)
        assertEquals(ScreenPageKind.UNKNOWN, outcome2.pageKind)
        assertNull(outcome2.draft)
        assertFalse(outcome2.shouldFallbackToLlm)
    }

    @Test
    fun stillAccuratelyParsesRealWeChatMMWebViewBillDetailAndWalletPaidSuccessAfterChatGuards() {
        val realWebViewDetail = ScreenSnapshot(
            packageName = "com.tencent.mm",
            className = "com.tencent.mm.plugin.webview.ui.tools.MMWebViewUI",
            texts = listOf(
                "账单详情",
                "霸王茶姬",
                "-20.00",
                "当前状态", "支付成功",
                "支付时间", "2026-11-22 16:05:10",
                "商品", "伯牙绝弦大杯",
                "支付方式", "零钱",
                "交易单号", "42000099992026112212345678",
                "账单服务", "在此商户的交易",
                "本服务由财付通提供",
                "全部账单"
            )
        )
        val detailOutcome = parser.parse(realWebViewDetail, aliases, accounts, categories)
        assertEquals(ScreenPageKind.BILL_DETAIL, detailOutcome.pageKind)
        assertNotNull(detailOutcome.draft)
        assertEquals(MoneyAmount.parseToMicros("20.00"), detailOutcome.draft!!.origAmountMicros)
        assertEquals("霸王茶姬", detailOutcome.draft!!.merchant)
        assertEquals("42000099992026112212345678", detailOutcome.orderNo)

        val realPaySuccess = ScreenSnapshot(
            packageName = "com.tencent.mm",
            className = "com.tencent.mm.plugin.wallet_core.ui.WalletPaidSuccessUI",
            texts = listOf("支付成功", "霸王茶姬", "¥20.00", "支付方式", "零钱", "完成")
        )
        val payOutcome = parser.parse(realPaySuccess, aliases, accounts, categories)
        assertEquals(ScreenPageKind.PAY_SUCCESS, payOutcome.pageKind)
        assertNotNull(payOutcome.draft)
        assertEquals(MoneyAmount.parseToMicros("20.00"), payOutcome.draft!!.origAmountMicros)
        assertEquals("霸王茶姬", payOutcome.draft!!.merchant)
        assertEquals("微信零钱", accounts.first { it.id == payOutcome.draft!!.accountId }.name)
    }
}

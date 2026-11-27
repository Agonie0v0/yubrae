package com.yubrae.app.capture.screen

import com.yubrae.app.capture.CaptureTestFixtures
import com.yubrae.app.capture.FakeAccountAliasDao
import com.yubrae.app.capture.FakeAccountDao
import com.yubrae.app.capture.FakeCategoryDao
import com.yubrae.app.capture.FakeParseRuleDao
import com.yubrae.app.core.model.MoneyAmount
import com.yubrae.app.data.db.AccountAliasEntity
import com.yubrae.app.data.db.AccountEntity
import com.yubrae.app.data.db.CategoryEntity
import com.yubrae.app.data.db.DefaultSeedData
import com.yubrae.app.data.db.TagTextCodec
import com.yubrae.app.data.db.TransactionType
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
            packageName = "com.yubrae.app",
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
        assertFalse(ScreenBillParser.isWatchedPackage("com.yubrae.app"))
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

    @Test
    fun ignoresPaymentDiscountLineAndPrefersTopBareNegativeHeroAmount() {
        val wechatInlineDiscount = ScreenSnapshot(
            packageName = "com.tencent.mm",
            className = "com.tencent.mm.plugin.webview.ui.tools.MMWebViewUI",
            texts = listOf(
                "账单详情",
                "瑞幸咖啡",
                "-15.80",
                "支付优惠 -¥3.00",
                "当前状态", "支付成功",
                "支付时间", "2026-11-22 09:15:00",
                "支付方式", "零钱",
                "交易单号", "420000112233445566"
            )
        )
        val outcome1 = parser.parse(wechatInlineDiscount, aliases, accounts, categories)
        assertEquals(ScreenPageKind.BILL_DETAIL, outcome1.pageKind)
        val draft1 = outcome1.draft
        assertNotNull(draft1)
        assertEquals(TransactionType.EXPENSE, draft1!!.type)
        assertEquals(MoneyAmount.parseToMicros("15.80"), draft1.origAmountMicros)
        assertEquals("CNY", draft1.origCurrency)
        assertEquals("瑞幸咖啡", draft1.merchant)

        // 即使顶部裸数字下方出现独立正数/无标签优惠金额，仍优先顶部带符号裸数字，且详情字段之后的独立行不得进入 HERO 区间
        val heroOrderingAndLowerDiscount = ScreenSnapshot(
            packageName = "com.tencent.mm",
            className = "com.tencent.mm.plugin.webview.ui.tools.MMWebViewUI",
            texts = listOf(
                "账单详情",
                "瑞幸咖啡",
                "-15.80",
                "当前状态", "支付成功",
                "支付优惠", "-¥3.00",
                "¥3.00",
                "支付方式", "零钱",
                "交易单号", "420000112233445567"
            )
        )
        val outcome2 = parser.parse(heroOrderingAndLowerDiscount, aliases, accounts, categories)
        assertEquals(ScreenPageKind.BILL_DETAIL, outcome2.pageKind)
        assertEquals(MoneyAmount.parseToMicros("15.80"), outcome2.draft!!.origAmountMicros)
    }

    @Test
    fun ignoresMultilineDiscountDescriptionAndSubItemsWithoutSwallowingNextFields() {
        val wechatCouponMultiline = ScreenSnapshot(
            packageName = "com.tencent.mm",
            className = "com.tencent.mm.plugin.webview.ui.tools.MMWebViewUI",
            texts = listOf(
                "账单详情",
                "美团外卖",
                "-25.00",
                "当前状态", "支付成功",
                "优惠说明",
                "微信立减金",
                "-¥5.00",
                "支付时间", "2026-11-22 12:00:00",
                "支付方式", "招商银行信用卡(6688)",
                "交易单号", "420000887766554433"
            )
        )
        val outcome1 = parser.parse(wechatCouponMultiline, aliases, accounts, categories)
        assertEquals(ScreenPageKind.BILL_DETAIL, outcome1.pageKind)
        val draft1 = outcome1.draft
        assertNotNull(draft1)
        assertEquals(MoneyAmount.parseToMicros("25.00"), draft1!!.origAmountMicros)
        assertEquals("美团外卖", draft1.merchant)
        assertEquals("6688", draft1.accountHint)
        assertEquals("招商信用卡6688", accounts.first { it.id == draft1.accountId }.name)
        assertEquals("420000887766554433", draft1.externalOrderNo)

        // 子描述为“满30减5”非字段标签，同样向后穿透跳过关联优惠金额且不吞并下方字段
        val fullReductionMultiline = ScreenSnapshot(
            packageName = "com.tencent.mm",
            className = "com.tencent.mm.plugin.wallet_core.ui.WalletPaidSuccessUI",
            texts = listOf(
                "支付成功",
                "美团外卖",
                "¥25.00",
                "优惠说明：",
                "满30减5",
                "-¥5.00",
                "支付方式", "招商银行信用卡(6688)",
                "完成"
            )
        )
        val outcome2 = parser.parse(fullReductionMultiline, aliases, accounts, categories)
        assertEquals(ScreenPageKind.PAY_SUCCESS, outcome2.pageKind)
        val draft2 = outcome2.draft
        assertNotNull(draft2)
        assertEquals(MoneyAmount.parseToMicros("25.00"), draft2!!.origAmountMicros)
        assertEquals("招商信用卡6688", accounts.first { it.id == draft2.accountId }.name)
    }

    @Test
    fun prefersActualPaidOverOrderAmountMerchantDiscountAndRedPacketInAlipayDetail() {
        val alipayDetail = ScreenSnapshot(
            packageName = "com.eg.android.AlipayGphone",
            className = "com.alipay.mobile.bill.detail.ui.BillDetailActivity",
            texts = listOf(
                "账单详情",
                "盒马鲜生",
                "当前状态", "交易成功",
                "原价", "¥35.00",
                "商家优惠", "-¥5.00",
                "红包抵扣", "-¥2.00",
                "实付金额", "¥28.00",
                "付款方式", "花呗",
                "创建时间", "2026-11-22 18:30:00",
                "订单号", "202611222200148899112233"
            )
        )
        val outcome1 = parser.parse(alipayDetail, aliases, accounts, categories)
        assertEquals(ScreenPageKind.BILL_DETAIL, outcome1.pageKind)
        val draft1 = outcome1.draft
        assertNotNull(draft1)
        assertEquals(TransactionType.EXPENSE, draft1!!.type)
        assertEquals(MoneyAmount.parseToMicros("28.00"), draft1.origAmountMicros)
        assertEquals("CNY", draft1.origCurrency)
        assertEquals("盒马鲜生", draft1.merchant)
        assertEquals("支付宝", accounts.first { it.id == draft1.accountId }.name)

        // 含“订单金额”与各种券/补贴/集分宝抵扣共存时仍精确取实付
        val alipayOrderAmountSnapshot = ScreenSnapshot(
            packageName = "com.eg.android.AlipayGphone",
            className = "BillDetailActivity",
            texts = listOf(
                "账单详情",
                "盒马鲜生",
                "-28.00",
                "当前状态", "交易成功",
                "订单金额", "¥35.00",
                "平台消费券", "-¥4.00",
                "集分宝抵扣", "-¥1.00",
                "商家券", "-¥2.00",
                "实付金额", "¥28.00",
                "付款方式", "支付宝",
                "订单号", "202611222200148899112244"
            )
        )
        val outcome2 = parser.parse(alipayOrderAmountSnapshot, aliases, accounts, categories)
        assertEquals(ScreenPageKind.BILL_DETAIL, outcome2.pageKind)
        assertEquals(MoneyAmount.parseToMicros("28.00"), outcome2.draft!!.origAmountMicros)
    }

    @Test
    fun ignoresInlineDiscountAttachedToPaymentMethodAndPreservesAccountRouting() {
        val twoLinePaymentDiscount = ScreenSnapshot(
            packageName = "com.tencent.mm",
            className = "com.tencent.mm.plugin.webview.ui.tools.MMWebViewUI",
            texts = listOf(
                "账单详情",
                "霸王茶姬",
                "-18.00",
                "当前状态", "支付成功",
                "支付方式", "招商银行信用卡(6688) (立减¥2.00)",
                "支付时间", "2026-11-22 14:20:00",
                "交易单号", "420000556677889900"
            )
        )
        val outcome1 = parser.parse(twoLinePaymentDiscount, aliases, accounts, categories)
        assertEquals(ScreenPageKind.BILL_DETAIL, outcome1.pageKind)
        val draft1 = outcome1.draft
        assertNotNull(draft1)
        assertEquals(MoneyAmount.parseToMicros("18.00"), draft1!!.origAmountMicros)
        assertEquals("6688", draft1.accountHint)
        assertEquals("招商信用卡6688", accounts.first { it.id == draft1.accountId }.name)

        val inlinePaySuccess = ScreenSnapshot(
            packageName = "com.tencent.mm",
            className = "com.tencent.mm.plugin.wallet_core.ui.WalletPaidSuccessUI",
            texts = listOf(
                "支付成功",
                "霸王茶姬",
                "¥18.00",
                "付款方式：零钱(立减¥2.00)",
                "完成"
            )
        )
        val outcome2 = parser.parse(inlinePaySuccess, aliases, accounts, categories)
        assertEquals(ScreenPageKind.PAY_SUCCESS, outcome2.pageKind)
        val draft2 = outcome2.draft
        assertNotNull(draft2)
        assertEquals(MoneyAmount.parseToMicros("18.00"), draft2!!.origAmountMicros)
        assertEquals("微信零钱", accounts.first { it.id == draft2.accountId }.name)
    }

    @Test
    fun ignoresInlineDiscountAndCouponTextsOnPaySuccessPage() {
        val paySuccessWithCoupon = ScreenSnapshot(
            packageName = "com.eg.android.AlipayGphone",
            className = "com.alipay.android.msp.ui.views.MspContainerActivity",
            texts = listOf(
                "支付成功",
                "星巴克",
                "¥26.00 (已优惠¥4.00)",
                "已优惠¥4.00/5元立减金",
                "使用5元立减金",
                "付款方式", "支付宝",
                "完成"
            )
        )
        val outcome = parser.parse(paySuccessWithCoupon, aliases, accounts, categories)
        assertEquals(ScreenPageKind.PAY_SUCCESS, outcome.pageKind)
        val draft = outcome.draft
        assertNotNull(draft)
        assertEquals(TransactionType.EXPENSE, draft!!.type)
        assertEquals(MoneyAmount.parseToMicros("26.00"), draft.origAmountMicros)
        assertEquals("CNY", draft.origCurrency)
        assertEquals("星巴克", draft.merchant)
        assertEquals("餐饮美食", draft.categoryName)
        assertEquals("支付宝", accounts.first { it.id == draft.accountId }.name)
    }

    @Test
    fun resolvesForeignCurrencyWithCnySettlementWhenDiscountAmountAlsoPresent() {
        val foreignWithDiscount = ScreenSnapshot(
            packageName = "com.eg.android.AlipayGphone",
            className = "com.alipay.mobile.bill.detail.ui.BillDetailActivity",
            texts = listOf(
                "账单详情",
                "OpenAI",
                "-$15.00",
                "实付 ¥102.00",
                "官方立减", "-¥7.20",
                "当前状态", "交易成功",
                "付款方式", "招商银行信用卡(6688)",
                "创建时间", "2026-11-22 10:00:00",
                "订单号", "202611229988776655"
            )
        )
        val outcome1 = parser.parse(foreignWithDiscount, aliases, accounts, categories)
        assertEquals(ScreenPageKind.BILL_DETAIL, outcome1.pageKind)
        val draft1 = outcome1.draft
        assertNotNull(draft1)
        assertEquals("USD", draft1!!.origCurrency)
        assertEquals(MoneyAmount.parseToMicros("15.00"), draft1.origAmountMicros)
        assertEquals(MoneyAmount.parseToMicros("102.00"), draft1.manualAccountAmountMicros)
        assertEquals("招商信用卡6688", accounts.first { it.id == draft1.accountId }.name)

        // 含外币消费金额 + CNY 英雄实付 + 多行立减金时，CNY 结算额仍取 102.00 而非 7.20
        val wechatForeignWithMultilineDiscount = ScreenSnapshot(
            packageName = "com.tencent.mm",
            className = "com.tencent.mm.plugin.webview.ui.tools.MMWebViewUI",
            texts = listOf(
                "账单详情",
                "OpenAI",
                "-102.00",
                "消费金额 USD 15.00",
                "优惠说明",
                "微信立减金",
                "-¥7.20",
                "当前状态", "支付成功",
                "支付方式", "招商银行信用卡(6688)",
                "交易单号", "420000123498765432"
            )
        )
        val outcome2 = parser.parse(wechatForeignWithMultilineDiscount, aliases, accounts, categories)
        assertEquals(ScreenPageKind.BILL_DETAIL, outcome2.pageKind)
        val draft2 = outcome2.draft
        assertNotNull(draft2)
        assertEquals("USD", draft2!!.origCurrency)
        assertEquals(MoneyAmount.parseToMicros("15.00"), draft2.origAmountMicros)
        assertEquals(MoneyAmount.parseToMicros("102.00"), draft2.manualAccountAmountMicros)
    }

    @Test
    fun doesNotFalseTriggerIgnoredAmountOnCoffeeMerchantProvinceOrYuEBao() {
        val coffeeAndYuEBao = ScreenSnapshot(
            packageName = "com.eg.android.AlipayGphone",
            className = "BillDetailActivity",
            texts = listOf(
                "账单详情",
                "Starbucks Coffee",
                "-35.00",
                "当前状态", "交易成功",
                "付款方式", "余额宝",
                "创建时间", "2026-11-22 15:00:00",
                "订单号", "202611220000111122"
            )
        )
        val outcome1 = parser.parse(coffeeAndYuEBao, aliases, accounts, categories)
        assertEquals(ScreenPageKind.BILL_DETAIL, outcome1.pageKind)
        assertNotNull(outcome1.draft)
        assertEquals(MoneyAmount.parseToMicros("35.00"), outcome1.draft!!.origAmountMicros)
        assertEquals("Starbucks Coffee", outcome1.draft!!.merchant)

        val provinceMerchant = ScreenSnapshot(
            packageName = "com.tencent.mm",
            className = "com.tencent.mm.plugin.wallet_core.ui.WalletPaidSuccessUI",
            texts = listOf(
                "支付成功",
                "浙江省杭州市星巴克门店 ¥42.00",
                "完成"
            )
        )
        val outcome2 = parser.parse(provinceMerchant, aliases, accounts, categories)
        assertEquals(ScreenPageKind.PAY_SUCCESS, outcome2.pageKind)
        assertNotNull(outcome2.draft)
        assertEquals(MoneyAmount.parseToMicros("42.00"), outcome2.draft!!.origAmountMicros)
    }

    @Test
    fun parsesRealAlipayPasswordRedPacketIncomeDetailWithoutSwallowingFieldsOrAddPlaceholder() {
        val snapshot = ScreenSnapshot(
            packageName = "com.eg.android.AlipayGphone",
            className = "com.alipay.mobile.bill.detail.ui.BillDetailActivity",
            texts = listOf(
                "账单详情",
                "阿麦",
                "15元",
                "交易成功",
                "创建时间", "2026-11-20 15:29:19",
                "收款方式", "账户余额",
                "红包说明", "收到口令红包",
                "账单分类", "红包",
                "对方账户", "138****1234",
                "订单号", "202611202200148899001122",
                "标签和备注",
                "备注…", "添加",
                "对此订单有疑问"
            )
        )

        val outcome = parser.parse(snapshot, aliases, accounts, categories)
        assertEquals(ScreenPageKind.BILL_DETAIL, outcome.pageKind)
        assertEquals("202611202200148899001122", outcome.orderNo)

        val draft = outcome.draft
        assertNotNull(draft)
        assertEquals(TransactionType.INCOME, draft!!.type)
        assertEquals(MoneyAmount.parseToMicros("15.00"), draft.origAmountMicros)
        assertEquals("CNY", draft.origCurrency)
        assertEquals("", draft.merchant)
        assertTrue("Expected note to contain 收到口令红包, got: ${draft.note}", draft.note.contains("收到口令红包"))
        assertTrue("Expected note to contain 账户余额, got: ${draft.note}", draft.note.contains("账户余额"))
        assertFalse("Expected note not to contain 添加, got: ${draft.note}", draft.note.contains("添加"))
        assertEquals("支付宝", accounts.first { it.id == draft.accountId }.name)

        val expectedMillis = LocalDateTime.of(2026, 11, 20, 15, 29, 19)
            .atZone(testZone)
            .toInstant()
            .toEpochMilli()
        assertEquals(expectedMillis, draft.timestamp)
    }

    @Test
    fun parsesAlipayQrExpenseWithCashbackAndArrivedKeywordsAsExpense() {
        val signedQrExpense = ScreenSnapshot(
            packageName = "com.eg.android.AlipayGphone",
            className = "com.alipay.mobile.bill.detail.ui.BillDetailActivity",
            texts = listOf(
                "账单详情",
                "路边煎饼摊",
                "-25.80",
                "交易成功",
                "商品说明", "二维码收款",
                "笔笔返现", "+0.18",
                "已到账",
                "付款方式", "花呗",
                "创建时间", "2026-11-23 08:25:10",
                "订单号", "202611232200140000112233"
            )
        )
        val outcome1 = parser.parse(signedQrExpense, aliases, accounts, categories)
        assertEquals(ScreenPageKind.BILL_DETAIL, outcome1.pageKind)
        val draft1 = outcome1.draft
        assertNotNull(draft1)
        assertEquals(TransactionType.EXPENSE, draft1!!.type)
        assertEquals(MoneyAmount.parseToMicros("25.80"), draft1.origAmountMicros)
        assertEquals("CNY", draft1.origCurrency)
        assertEquals("路边煎饼摊", draft1.merchant)

        // 即使顶部金额不带负号且存在付款方式，二维码收款/笔笔返现/已到账也绝不误判为收入
        val unsignedQrExpense = ScreenSnapshot(
            packageName = "com.eg.android.AlipayGphone",
            className = "BillDetailActivity",
            texts = listOf(
                "账单详情",
                "路边煎饼摊",
                "25.80",
                "当前状态", "已到账",
                "商品说明", "二维码收款",
                "笔笔返现", "0.18元",
                "到账成功",
                "付款方式", "余额宝",
                "创建时间", "2026-11-23 08:25:10",
                "订单号", "202611232200140000112234"
            )
        )
        val outcome2 = parser.parse(unsignedQrExpense, aliases, accounts, categories)
        assertEquals(ScreenPageKind.BILL_DETAIL, outcome2.pageKind)
        assertEquals(TransactionType.EXPENSE, outcome2.draft!!.type)
        assertEquals(MoneyAmount.parseToMicros("25.80"), outcome2.draft!!.origAmountMicros)
    }

    @Test
    fun mergesSplitDecimalAmountNodesAcrossFormatsAndPreservesGeometry() {
        val splitNegative = ScreenSnapshot(
            packageName = "com.eg.android.AlipayGphone",
            className = "BillDetailActivity",
            texts = listOf("账单详情", "麦当劳", "-26", ".80", "交易成功", "创建时间", "2026-11-23 12:00:00", "付款方式", "支付宝")
        )
        val out1 = parser.parse(splitNegative, aliases, accounts, categories)
        assertEquals(TransactionType.EXPENSE, out1.draft!!.type)
        assertEquals(MoneyAmount.parseToMicros("26.80"), out1.draft!!.origAmountMicros)

        val splitSignAndDigits = ScreenSnapshot(
            packageName = "com.eg.android.AlipayGphone",
            className = "BillDetailActivity",
            texts = listOf("账单详情", "麦当劳", "-", "26", ".80", "交易成功", "创建时间", "2026-11-23 12:00:00", "付款方式", "支付宝")
        )
        val out2 = parser.parse(splitSignAndDigits, aliases, accounts, categories)
        assertEquals(TransactionType.EXPENSE, out2.draft!!.type)
        assertEquals(MoneyAmount.parseToMicros("26.80"), out2.draft!!.origAmountMicros)

        val splitCurrencyAndDigits = ScreenSnapshot(
            packageName = "com.tencent.mm",
            className = "WalletPaidSuccessUI",
            texts = listOf("支付成功", "麦当劳", "¥", "26", ".80", "完成")
        )
        val out3 = parser.parse(splitCurrencyAndDigits, aliases, accounts, categories)
        assertEquals(TransactionType.EXPENSE, out3.draft!!.type)
        assertEquals(MoneyAmount.parseToMicros("26.80"), out3.draft!!.origAmountMicros)

        val splitPositive = ScreenSnapshot(
            packageName = "com.eg.android.AlipayGphone",
            className = "BillDetailActivity",
            texts = listOf("账单详情", "张三", "+15", ".00", "交易成功", "创建时间", "2026-11-23 12:00:00", "订单号", "2026112300015000")
        )
        val out4 = parser.parse(splitPositive, aliases, accounts, categories)
        assertEquals(TransactionType.INCOME, out4.draft!!.type)
        assertEquals(MoneyAmount.parseToMicros("15.00"), out4.draft!!.origAmountMicros)

        val splitTrailingDot = ScreenSnapshot(
            packageName = "com.eg.android.AlipayGphone",
            className = "BillDetailActivity",
            texts = listOf("账单详情", "麦当劳", "26.", "80", "交易成功", "创建时间", "2026-11-23 12:00:00", "付款方式", "支付宝")
        )
        val out5 = parser.parse(splitTrailingDot, aliases, accounts, categories)
        assertEquals(TransactionType.EXPENSE, out5.draft!!.type)
        assertEquals(MoneyAmount.parseToMicros("26.80"), out5.draft!!.origAmountMicros)
    }

    @Test
    fun parsesSignedAndCurrencyIntegerAmountsInHeroZoneWhileIgnoringBareCounts() {
        val signedNegativeInt = ScreenSnapshot(
            packageName = "com.eg.android.AlipayGphone",
            className = "BillDetailActivity",
            texts = listOf(
                "账单详情",
                "便利蜂",
                "-50",
                "交易成功",
                "创建时间", "2026-11-23 13:00:00",
                "付款方式", "花呗",
                "订单号", "2026112300050001"
            )
        )
        val out1 = parser.parse(signedNegativeInt, aliases, accounts, categories)
        assertEquals(TransactionType.EXPENSE, out1.draft!!.type)
        assertEquals(MoneyAmount.parseToMicros("50.00"), out1.draft!!.origAmountMicros)

        val signedPositiveInt = ScreenSnapshot(
            packageName = "com.eg.android.AlipayGphone",
            className = "BillDetailActivity",
            texts = listOf(
                "账单详情",
                "李四",
                "+200",
                "交易成功",
                "创建时间", "2026-11-23 13:05:00",
                "收款方式", "余额宝",
                "订单号", "2026112300050002"
            )
        )
        val out2 = parser.parse(signedPositiveInt, aliases, accounts, categories)
        assertEquals(TransactionType.INCOME, out2.draft!!.type)
        assertEquals(MoneyAmount.parseToMicros("200.00"), out2.draft!!.origAmountMicros)

        val currencyIntAndStrayCounter = ScreenSnapshot(
            packageName = "com.eg.android.AlipayGphone",
            className = "BillDetailActivity",
            texts = listOf(
                "账单详情",
                "3",
                "便利蜂",
                "¥15",
                "交易成功",
                "创建时间", "2026-11-23 13:10:00",
                "付款方式", "余额"
            )
        )
        val out3 = parser.parse(currencyIntAndStrayCounter, aliases, accounts, categories)
        assertEquals(TransactionType.EXPENSE, out3.draft!!.type)
        assertEquals(MoneyAmount.parseToMicros("15.00"), out3.draft!!.origAmountMicros)
    }

    @Test
    fun prefersUpperHalfHeroVisualWithLargerHeightPxOverSmallFontAmounts() {
        val nodes = listOf(
            ScreenTextNode("账单详情", topPx = 80, heightPx = 44),
            ScreenTextNode("喜茶", topPx = 200, heightPx = 48),
            // 即使小字金额出现在大字主金额之前（例如顶部小角标），仍应被 HERO_VISUAL 压倒
            ScreenTextNode("¥3.00", topPx = 280, heightPx = 36),
            ScreenTextNode("-48", topPx = 340, heightPx = 120),
            ScreenTextNode(".00", topPx = 390, heightPx = 64),
            ScreenTextNode("交易成功", topPx = 500, heightPx = 40),
            ScreenTextNode("创建时间", topPx = 640, heightPx = 40),
            ScreenTextNode("2026-11-23 15:00:00", topPx = 640, heightPx = 40),
            ScreenTextNode("付款方式", topPx = 720, heightPx = 40),
            ScreenTextNode("支付宝", topPx = 720, heightPx = 40),
            ScreenTextNode("订单号", topPx = 800, heightPx = 40),
            ScreenTextNode("2026112300048001", topPx = 800, heightPx = 40)
        )
        val snapshot = ScreenSnapshot(
            packageName = "com.eg.android.AlipayGphone",
            className = "BillDetailActivity",
            texts = nodes.map { it.text },
            nodes = nodes,
            screenHeightPx = 2400
        )

        val outcome = parser.parse(snapshot, aliases, accounts, categories)
        assertEquals(ScreenPageKind.BILL_DETAIL, outcome.pageKind)
        val draft = outcome.draft
        assertNotNull(draft)
        assertEquals(TransactionType.EXPENSE, draft!!.type)
        assertEquals(MoneyAmount.parseToMicros("48.00"), draft.origAmountMicros)

        // 无正负号的几何大字 "15元" (heightPx=120) 同样压倒带负号的小字 "¥3.00" (heightPx=36)
        val incomeNodes = listOf(
            ScreenTextNode("账单详情", topPx = 80, heightPx = 42),
            ScreenTextNode("阿麦", topPx = 200, heightPx = 46),
            ScreenTextNode("-¥3.00", topPx = 270, heightPx = 36),
            ScreenTextNode("15元", topPx = 340, heightPx = 120),
            ScreenTextNode("交易成功", topPx = 500, heightPx = 40),
            ScreenTextNode("收款方式", topPx = 660, heightPx = 40),
            ScreenTextNode("账户余额", topPx = 660, heightPx = 40),
            ScreenTextNode("创建时间", topPx = 740, heightPx = 40),
            ScreenTextNode("2026-11-20 15:29:19", topPx = 740, heightPx = 40)
        )
        val incomeSnapshot = ScreenSnapshot(
            packageName = "com.eg.android.AlipayGphone",
            className = "BillDetailActivity",
            texts = incomeNodes.map { it.text },
            nodes = incomeNodes,
            screenHeightPx = 2400
        )
        val incomeOutcome = parser.parse(incomeSnapshot, aliases, accounts, categories)
        assertEquals(TransactionType.INCOME, incomeOutcome.draft!!.type)
        assertEquals(MoneyAmount.parseToMicros("15.00"), incomeOutcome.draft!!.origAmountMicros)
    }

    @Test
    fun ignoresAlipayTouchPayDiscountConsumerGoldAndSavingsKeywords() {
        val snapshot = ScreenSnapshot(
            packageName = "com.eg.android.AlipayGphone",
            className = "com.alipay.mobile.bill.detail.ui.BillDetailActivity",
            texts = listOf(
                "账单详情",
                "肯德基",
                "交易成功",
                "原价", "¥38.00",
                "商家优惠", "-¥5.00",
                "碰一下立减", "-¥3.00",
                "消费金", "-¥2.00",
                "花呗金", "-¥1.00",
                "鼓励金", "-¥0.50",
                "体验金", "-¥1.00",
                "淘金币", "-¥0.50",
                "共省13.00元",
                "笔笔攒 1.88元",
                "运费险 0.50元",
                "实付金额", "¥25.00",
                "付款方式", "花呗",
                "创建时间", "2026-11-23 12:10:00",
                "订单号", "202611232200148899007788"
            )
        )

        val outcome = parser.parse(snapshot, aliases, accounts, categories)
        assertEquals(ScreenPageKind.BILL_DETAIL, outcome.pageKind)
        val draft = outcome.draft
        assertNotNull(draft)
        assertEquals(TransactionType.EXPENSE, draft!!.type)
        assertEquals(MoneyAmount.parseToMicros("25.00"), draft.origAmountMicros)
        assertEquals("CNY", draft.origCurrency)
        assertEquals("肯德基", draft.merchant)
    }

    @Test
    fun doesNotReverseExpenseWhenRefundDetailEntryOrPartialRefundAppears() {
        val partialRefundSigned = ScreenSnapshot(
            packageName = "com.eg.android.AlipayGphone",
            className = "BillDetailActivity",
            texts = listOf(
                "账单详情",
                "优衣库",
                "-68.00",
                "当前状态", "部分已退",
                "付款方式", "招商银行信用卡(6688)",
                "创建时间", "2026-11-23 14:00:00",
                "订单号", "202611232200148899009900",
                "退款详情",
                "查看退款详情"
            )
        )
        val outcome1 = parser.parse(partialRefundSigned, aliases, accounts, categories)
        assertEquals(ScreenPageKind.BILL_DETAIL, outcome1.pageKind)
        assertEquals(TransactionType.EXPENSE, outcome1.draft!!.type)
        assertEquals(MoneyAmount.parseToMicros("68.00"), outcome1.draft!!.origAmountMicros)
        assertEquals("招商信用卡6688", accounts.first { it.id == outcome1.draft!!.accountId }.name)

        // 即使顶部金额无负号，仅有付款方式与部分已退/退款详情入口，仍保持 EXPENSE
        val partialRefundUnsigned = ScreenSnapshot(
            packageName = "com.eg.android.AlipayGphone",
            className = "BillDetailActivity",
            texts = listOf(
                "账单详情",
                "优衣库",
                "¥68.00",
                "部分已退",
                "付款方式", "花呗",
                "创建时间", "2026-11-23 14:00:00",
                "订单号", "202611232200148899009901",
                "退款详情"
            )
        )
        val outcome2 = parser.parse(partialRefundUnsigned, aliases, accounts, categories)
        assertEquals(ScreenPageKind.BILL_DETAIL, outcome2.pageKind)
        assertEquals(TransactionType.EXPENSE, outcome2.draft!!.type)
        assertEquals(MoneyAmount.parseToMicros("68.00"), outcome2.draft!!.origAmountMicros)
    }

    @Test
    fun ignoresAlipayRedPacketAndMixedPaymentBreakdownWithoutLeakingSubAmounts() {
        val mixedPaymentSnapshot = ScreenSnapshot(
            packageName = "com.eg.android.AlipayGphone",
            className = "BillDetailActivity",
            texts = listOf(
                "账单详情",
                "美团",
                "-18.00",
                "交易成功",
                "付款方式", "花呗 15.00 + 红包 3.00",
                "组合支付",
                "支付宝红包 3.00元",
                "支付宝红包",
                "3.00元",
                "创建时间", "2026-11-23 18:00:00",
                "订单号", "202611232200148899005566"
            )
        )

        val outcome = parser.parse(mixedPaymentSnapshot, aliases, accounts, categories)
        assertEquals(ScreenPageKind.BILL_DETAIL, outcome.pageKind)
        val draft = outcome.draft
        assertNotNull(draft)
        assertEquals(TransactionType.EXPENSE, draft!!.type)
        assertEquals(MoneyAmount.parseToMicros("18.00"), draft.origAmountMicros)
        assertEquals("美团", draft.merchant)
        assertFalse("Expected note not to contain discount sub-item 红包 3.00, got: ${draft.note}", draft.note.contains("红包 3.00"))
    }

    @Test
    fun preservesHeroBareDecimalWhenCategoryMerchantAndDiscountLikeGoodsAppearAboveIt() {
        val snapshot = ScreenSnapshot(
            packageName = "com.eg.android.AlipayGphone",
            className = "BillDetailActivity",
            texts = listOf(
                "账单详情",
                "账单分类", "餐饮美食",
                "商户", "蓝蛙",
                "商品说明", "美团满30减5",
                "-25.80",
                "当前状态", "支付成功",
                "支付时间", "2026-11-23 19:00:00",
                "支付方式", "余额",
                "交易单号", "420000111122223333"
            )
        )

        val outcome = parser.parse(snapshot, aliases, accounts, categories)
        assertEquals(ScreenPageKind.BILL_DETAIL, outcome.pageKind)
        val draft = outcome.draft
        assertNotNull(draft)
        assertEquals(TransactionType.EXPENSE, draft!!.type)
        assertEquals(MoneyAmount.parseToMicros("25.80"), draft.origAmountMicros)
        assertEquals("蓝蛙", draft.merchant)
        assertTrue("Expected note to contain 美团满30减5, got: ${draft.note}", draft.note.contains("美团满30减5"))
        assertTrue("Expected note to contain 余额, got: ${draft.note}", draft.note.contains("余额"))
    }

    @Test
    fun filtersAllUiPlaceholdersAndAllowsWalletBalanceNamesInPaymentMethod() {
        val snapshot = ScreenSnapshot(
            packageName = "com.eg.android.AlipayGphone",
            className = "BillDetailActivity",
            texts = listOf(
                "账单详情",
                "去添加",
                "全家便利店",
                "-12.50",
                "当前状态", "交易成功",
                "付款方式", "零钱通",
                "商品说明", "未设置",
                "备注...", "暂无",
                "创建时间", "2026-11-23 20:00:00",
                "订单号", "202611232200148899003344"
            )
        )

        val outcome = parser.parse(snapshot, aliases, accounts, categories)
        assertEquals(ScreenPageKind.BILL_DETAIL, outcome.pageKind)
        val draft = outcome.draft
        assertNotNull(draft)
        assertEquals(TransactionType.EXPENSE, draft!!.type)
        assertEquals(MoneyAmount.parseToMicros("12.50"), draft.origAmountMicros)
        assertEquals("全家便利店", draft.merchant)
        assertTrue("Expected note to contain 零钱通, got: ${draft.note}", draft.note.contains("零钱通"))
        assertFalse(draft.note.contains("未设置"))
        assertFalse(draft.note.contains("暂无"))
        assertFalse(draft.note.contains("去添加"))
    }

    @Test
    fun parsesAlipayDirectionalTakeoutDetailWithWelfareAndJianjiankaDiscounts() {
        // 原样复现用户提供的真实支付宝「淘宝闪购」详情页捕获报文
        val snapshot = ScreenSnapshot(
            packageName = "com.eg.android.AlipayGphone",
            className = "com.alipay.mobile.bill.detail.ui.BillDetailActivity",
            texts = listOf(
                "淘宝闪购",
                "支出17.28元",
                "交易成功",
                "网商银行福利金抵扣",
                "-0.02",
                "支付宝减减卡",
                "-0.20",
                "支付时间",
                "2026-11-22 03:03:46",
                "付款方式",
                "网商银行储蓄卡(6978)",
                "商品说明",
                "享哆味汉堡炸鸡(蔡顺路店)外卖订单",
                "支付奖励",
                "立即领取2积分",
                "更多",
                "账单管理",
                "已解锁\"炸鸡汉堡\"贴纸，去兑换好礼",
                "账单分类",
                "餐饮美食",
                "标签",
                "为您推荐",
                "三餐",
                "计入收支",
                "备注",
                "联系商家",
                "查看往来记录",
                "AA收款",
                "往来流水证明",
                "申请电子回单"
            )
        )

        val outcome = parser.parse(snapshot, aliases, accounts, categories)
        assertEquals(ScreenPageKind.BILL_DETAIL, outcome.pageKind)
        assertFalse(outcome.shouldFallbackToLlm)
        val draft = outcome.draft
        assertNotNull(draft)
        assertEquals(TransactionType.EXPENSE, draft!!.type)
        assertEquals(MoneyAmount.parseToMicros("17.28"), draft.origAmountMicros)
        assertEquals("CNY", draft.origCurrency)
        assertEquals("淘宝闪购", draft.merchant)
        assertEquals("6978", draft.accountHint)
        assertTrue("Expected note to contain 享哆味汉堡炸鸡, got: ${draft.note}", draft.note.contains("享哆味汉堡炸鸡"))
        assertFalse(draft.note.contains("联系商家"))
        assertFalse(draft.note.contains("AA收款"))
    }

    @Test
    fun parsesAlipayRedPacketSendDetailWithDirectionalIntegerAmount() {
        // 原样复现用户提供的真实支付宝「支付宝红包」支出详情页捕获报文
        val snapshot = ScreenSnapshot(
            packageName = "com.eg.android.AlipayGphone",
            className = "com.alipay.mobile.bill.detail.ui.BillDetailActivity",
            texts = listOf(
                "支付宝红包",
                "支出55元",
                "交易成功",
                "创建时间",
                "2026-11-20 14:27:31",
                "付款方式",
                "网商银行储蓄卡(6978)",
                "红包说明",
                "发口令红包",
                "服务详情",
                "红包",
                "进入小程序",
                "更多",
                "账单管理",
                "我的消费图鉴",
                "账单分类",
                "转账红包",
                "标签",
                "为您推荐",
                "发红包",
                "计入收支",
                "备注",
                "AA收款"
            )
        )

        val outcome = parser.parse(snapshot, aliases, accounts, categories)
        assertEquals(ScreenPageKind.BILL_DETAIL, outcome.pageKind)
        assertFalse(outcome.shouldFallbackToLlm)
        val draft = outcome.draft
        assertNotNull(draft)
        assertEquals(TransactionType.EXPENSE, draft!!.type)
        assertEquals(MoneyAmount.parseToMicros("55.00"), draft.origAmountMicros)
        assertEquals("CNY", draft.origCurrency)
        assertEquals("支付宝红包", draft.merchant)
        assertEquals("6978", draft.accountHint)
        assertTrue("Expected note to contain 发口令红包, got: ${draft.note}", draft.note.contains("发口令红包"))
        assertFalse(draft.note.contains("AA收款"))
    }

    @Test
    fun blocksUnknownDiscountCardBelowStatusLineFromOverridingTopHeroAmount() {
        // 结构性防卫测试：即使出现词典未收录的新名称优惠卡，只要位于“交易成功”之后且顶部已有主金额，也绝不能反杀主金额
        val snapshot = ScreenSnapshot(
            packageName = "com.eg.android.AlipayGphone",
            className = "BillDetailActivity",
            texts = listOf(
                "喜茶",
                "支出28.50元",
                "交易成功",
                "神秘盲盒卡",
                "-1.50",
                "支付时间", "2026-11-23 15:00:00",
                "付款方式", "花呗",
                "商品说明", "多肉葡萄",
                "账单详情"
            )
        )

        val outcome = parser.parse(snapshot, aliases, accounts, categories)
        assertEquals(ScreenPageKind.BILL_DETAIL, outcome.pageKind)
        val draft = outcome.draft
        assertNotNull(draft)
        assertEquals(TransactionType.EXPENSE, draft!!.type)
        assertEquals(MoneyAmount.parseToMicros("28.50"), draft.origAmountMicros)
        assertEquals("喜茶", draft.merchant)
    }

    @Test
    fun parsesPinduoduoTaobaoAndJdPaidOrderDetailsWithRealStoreGoodsAndTags() = runTest {
        val fullAccDao = FakeAccountDao()
        val fullAliasDao = FakeAccountAliasDao()
        val fullCatDao = FakeCategoryDao()
        DefaultSeedData.ensureAccountsAndTransitCard(fullAccDao, fullAliasDao)
        DefaultSeedData.ensurePresetCategories(fullCatDao)
        val fullAccounts = fullAccDao.getAll()
        val fullAliases = fullAliasDao.getEnabled()
        val fullCategories = fullCatDao.getAll()

        val pddSnapshot = ScreenSnapshot(
            packageName = "com.xunmeng.pinduoduo",
            className = "com.xunmeng.pinduoduo.activity.NewPageActivity",
            texts = listOf(
                "订单详情",
                "买家已付款",
                "南极人家纺旗舰店 >",
                "纯棉四件套全棉床上用品被套床单",
                "2.0m床-水墨灰 x1",
                "¥109.00",
                "商品总价 ¥129.00",
                "百亿补贴 -¥20.00",
                "实付款 ¥99.00",
                "订单编号：241123-123456789012345",
                "付款时间：2026-11-23 14:20:10"
            )
        )
        val pddOutcome = parser.parse(pddSnapshot, fullAliases, fullAccounts, fullCategories)
        assertEquals(ScreenPageKind.ORDER_DETAIL, pddOutcome.pageKind)
        val pddDraft = pddOutcome.draft
        assertNotNull(pddDraft)
        assertEquals("SCREEN_ORDER_DETAIL", pddDraft!!.parserUsed)
        assertEquals(TransactionType.EXPENSE, pddDraft.type)
        assertEquals(MoneyAmount.parseToMicros("99.00"), pddDraft.origAmountMicros)
        assertEquals("南极人家纺旗舰店", pddDraft.merchant)
        assertEquals("家居日用", pddDraft.categoryName)
        assertTrue(pddDraft.note.contains("纯棉四件套"))
        assertEquals("241123-123456789012345", pddDraft.externalOrderNo)
        assertEquals(listOf("拼多多"), TagTextCodec.parse(pddDraft.tags))

        val taobaoSnapshot = ScreenSnapshot(
            packageName = "com.taobao.taobao",
            className = "com.taobao.order.detail.OrderDetailActivity",
            texts = listOf(
                "订单详情",
                "买家已付款",
                "收货人：张三 138****1234",
                "北京市朝阳区建国路88号",
                "优衣库官方旗舰店 >",
                "男装 法兰绒格子衬衫 长袖",
                "颜色:藏青;尺码:L x1",
                "¥149.00",
                "店铺优惠 -¥20.00",
                "实付款",
                "¥129.00",
                "订单编号",
                "394857102938475601",
                "成交时间",
                "2026-11-23 15:10:00"
            )
        )
        val tbOutcome = parser.parse(taobaoSnapshot, fullAliases, fullAccounts, fullCategories)
        assertEquals(ScreenPageKind.ORDER_DETAIL, tbOutcome.pageKind)
        val tbDraft = tbOutcome.draft!!
        assertEquals(MoneyAmount.parseToMicros("129.00"), tbDraft.origAmountMicros)
        assertEquals("优衣库官方旗舰店", tbDraft.merchant)
        assertEquals("服饰鞋包", tbDraft.categoryName)
        assertTrue(tbDraft.note.contains("法兰绒格子衬衫"))
        assertEquals("394857102938475601", tbDraft.externalOrderNo)
        assertEquals(listOf("淘宝"), TagTextCodec.parse(tbDraft.tags))

        val jdSnapshot = ScreenSnapshot(
            packageName = "com.jingdong.app.mall",
            className = "com.jd.lib.ordercenter.detail.OrderDetailActivity",
            texts = listOf(
                "订单详情",
                "已完成",
                "Logitech罗技京东自营旗舰店 >",
                "罗技 MX Master 3S 无线蓝牙鼠标",
                "石墨黑 x1",
                "商品总额 ¥599.00",
                "京豆 -¥10.00",
                "运费 ¥0.00",
                "实付款：¥549.00",
                "订单号：293847561029"
            )
        )
        val jdOutcome = parser.parse(jdSnapshot, fullAliases, fullAccounts, fullCategories)
        assertEquals(ScreenPageKind.ORDER_DETAIL, jdOutcome.pageKind)
        val jdDraft = jdOutcome.draft!!
        assertEquals(MoneyAmount.parseToMicros("549.00"), jdDraft.origAmountMicros)
        assertEquals("Logitech罗技京东自营旗舰店", jdDraft.merchant)
        assertEquals("数码配件", jdDraft.categoryName)
        assertTrue(jdDraft.note.contains("罗技 MX Master 3S"))
        assertEquals(listOf("京东"), TagTextCodec.parse(jdDraft.tags))
    }

    @Test
    fun parsesMeituanTakeoutAndElemeDeliveredOrderDetailsIgnoringFeesAndCoupons() = runTest {
        val fullAccDao = FakeAccountDao()
        val fullAliasDao = FakeAccountAliasDao()
        val fullCatDao = FakeCategoryDao()
        DefaultSeedData.ensureAccountsAndTransitCard(fullAccDao, fullAliasDao)
        DefaultSeedData.ensurePresetCategories(fullCatDao)
        val fullAccounts = fullAccDao.getAll()
        val fullAliases = fullAliasDao.getEnabled()
        val fullCategories = fullCatDao.getAll()

        val meituanSnapshot = ScreenSnapshot(
            packageName = "com.sankuai.meituan.takeoutnew",
            className = "com.sankuai.waimai.bussiness.order.detail.WMOrderDetailActivity",
            texts = listOf(
                "订单详情",
                "已送达",
                "蜜雪冰城(观音桥店) >",
                "冰鲜柠檬水",
                "正常冰/标准糖",
                "x1",
                "¥4.00",
                "满天星奶茶",
                "热/七分糖",
                "x1",
                "¥8.00",
                "打包费 ¥1.00",
                "配送费 ¥3.00",
                "美团红包 -¥3.00",
                "神券 -¥1.00",
                "实付 ¥12.00",
                "订单号码 10293847561029384"
            )
        )
        val mtOutcome = parser.parse(meituanSnapshot, fullAliases, fullAccounts, fullCategories)
        assertEquals(ScreenPageKind.ORDER_DETAIL, mtOutcome.pageKind)
        val mtDraft = mtOutcome.draft!!
        assertEquals(MoneyAmount.parseToMicros("12.00"), mtDraft.origAmountMicros)
        assertEquals("蜜雪冰城(观音桥店)", mtDraft.merchant)
        assertTrue(mtDraft.note.contains("冰鲜柠檬水"))
        assertTrue(mtDraft.note.contains("满天星奶茶"))
        assertEquals("外卖", mtDraft.categoryName)
        assertEquals(listOf("美团"), TagTextCodec.parse(mtDraft.tags))

        val elemeSnapshot = ScreenSnapshot(
            packageName = "me.ele",
            className = "me.ele.order.detail.OrderDetailActivity",
            texts = listOf(
                "订单详情",
                "商品已送达",
                "肯德基宅急送（中关村店）",
                "香辣鸡腿堡套餐 x1",
                "¥29.00",
                "上校鸡块 x1",
                "¥12.50",
                "餐盒费 ¥1.50",
                "餐具费 ¥0.00",
                "配送费 ¥5.00",
                "店铺立减 -¥5.00",
                "合计实付 ¥43.00",
                "订单号：8829103847561920"
            )
        )
        val eleOutcome = parser.parse(elemeSnapshot, fullAliases, fullAccounts, fullCategories)
        assertEquals(ScreenPageKind.ORDER_DETAIL, eleOutcome.pageKind)
        val eleDraft = eleOutcome.draft!!
        assertEquals(MoneyAmount.parseToMicros("43.00"), eleDraft.origAmountMicros)
        assertEquals("肯德基宅急送（中关村店）", eleDraft.merchant)
        assertTrue(eleDraft.note.contains("香辣鸡腿堡套餐"))
        assertTrue(eleDraft.note.contains("上校鸡块"))
        assertEquals("外卖", eleDraft.categoryName)
        assertEquals(emptyList<String>(), TagTextCodec.parse(eleDraft.tags))
    }

    @Test
    fun doesNotMisfireOnEcommerceCartProductDetailPendingPaymentOrOrderList() {
        val productDetail = ScreenSnapshot(
            packageName = "com.taobao.taobao",
            className = "ProductDetailActivity",
            texts = listOf("优衣库男装法兰绒衬衫", "¥149.00", "加入购物车", "立即购买", "猜你喜欢")
        )
        val productOutcome = parser.parse(productDetail, aliases, accounts, categories)
        assertEquals(ScreenPageKind.UNKNOWN, productOutcome.pageKind)
        assertNull(productOutcome.draft)

        val cartCheckout = ScreenSnapshot(
            packageName = "com.xunmeng.pinduoduo",
            className = "CartActivity",
            texts = listOf("购物车", "纯棉四件套", "实付 ¥99.00", "去结算", "提交订单")
        )
        val cartOutcome = parser.parse(cartCheckout, aliases, accounts, categories)
        assertEquals(ScreenPageKind.UNKNOWN, cartOutcome.pageKind)
        assertNull(cartOutcome.draft)

        val unpaidOrder = ScreenSnapshot(
            packageName = "com.jingdong.app.mall",
            className = "OrderDetailActivity",
            texts = listOf("订单详情", "等待买家付款", "实付款 ¥299.00", "立即下单", "订单号：12345678", "待付款")
        )
        val unpaidOutcome = parser.parse(unpaidOrder, aliases, accounts, categories)
        assertEquals(ScreenPageKind.UNKNOWN, unpaidOutcome.pageKind)
        assertNull(unpaidOutcome.draft)

        val orderListWithHeader = ScreenSnapshot(
            packageName = "com.taobao.taobao",
            className = "OrderListActivity",
            texts = listOf(
                "全部订单",
                "交易成功", "南极人家纺旗舰店", "实付 ¥99.00", "订单号：11111111", "再来一单",
                "交易成功", "三只松鼠旗舰店", "实付 ¥49.00", "订单号：22222222", "再来一单",
                "猜你喜欢"
            )
        )
        val listOutcome = parser.parse(orderListWithHeader, aliases, accounts, categories)
        assertEquals(ScreenPageKind.UNKNOWN, listOutcome.pageKind)
        assertNull(listOutcome.draft)

        val scrolledMultiOrderCards = ScreenSnapshot(
            packageName = "com.xunmeng.pinduoduo",
            className = "OrderListActivity",
            texts = listOf(
                "交易成功", "店铺A", "实付 ¥99.00", "订单号：11111111",
                "交易成功", "店铺B", "实付 ¥49.00", "订单号：22222222"
            )
        )
        val multiOutcome = parser.parse(scrolledMultiOrderCards, aliases, accounts, categories)
        assertEquals(ScreenPageKind.UNKNOWN, multiOutcome.pageKind)
        assertNull(multiOutcome.draft)
    }

    @Test
    fun unpacksWeChatAndAlipayPlatformShellMerchantFromGoodsForAccurateSubcategory() = runTest {
        val fullAccDao = FakeAccountDao()
        val fullAliasDao = FakeAccountAliasDao()
        val fullCatDao = FakeCategoryDao()
        DefaultSeedData.ensureAccountsAndTransitCard(fullAccDao, fullAliasDao)
        DefaultSeedData.ensurePresetCategories(fullCatDao)
        val fullAccounts = fullAccDao.getAll()
        val fullAliases = fullAliasDao.getEnabled()
        val fullCategories = fullCatDao.getAll()

        val wechatPddCatFood = ScreenSnapshot(
            packageName = "com.tencent.mm",
            className = "com.tencent.mm.plugin.webview.ui.tools.MMWebViewUI",
            texts = listOf(
                "账单详情",
                "拼多多平台商户",
                "-59.90",
                "当前状态", "支付成功",
                "商品", "拼多多订单-241123998877665544-皇家猫粮全价成猫粮2kg",
                "商户全称", "上海寻梦信息技术有限公司",
                "支付方式", "零钱",
                "交易单号", "4200001234567890"
            )
        )
        val outcome1 = parser.parse(wechatPddCatFood, fullAliases, fullAccounts, fullCategories)
        assertEquals(ScreenPageKind.BILL_DETAIL, outcome1.pageKind)
        val draft1 = outcome1.draft!!
        assertEquals(MoneyAmount.parseToMicros("59.90"), draft1.origAmountMicros)
        assertEquals("宠物食品", draft1.categoryName)
        assertTrue(draft1.merchant.contains("皇家猫粮"))
        assertTrue(draft1.note.contains("皇家猫粮"))
        assertEquals(listOf("拼多多"), TagTextCodec.parse(draft1.tags))

        val wechatPddShop = ScreenSnapshot(
            packageName = "com.tencent.mm",
            className = "com.tencent.mm.plugin.webview.ui.tools.MMWebViewUI",
            texts = listOf(
                "账单详情",
                "拼多多平台商户",
                "-99.00",
                "当前状态", "支付成功",
                "商品", "拼多多订单-南极人家纺旗舰店 纯棉四件套",
                "支付方式", "零钱",
                "交易单号", "4200001234567899"
            )
        )
        val outcome2 = parser.parse(wechatPddShop, fullAliases, fullAccounts, fullCategories)
        assertEquals(ScreenPageKind.BILL_DETAIL, outcome2.pageKind)
        val draft2 = outcome2.draft!!
        assertEquals("南极人家纺旗舰店", draft2.merchant)
        assertEquals("家居日用", draft2.categoryName)
        assertEquals(listOf("拼多多"), TagTextCodec.parse(draft2.tags))
    }

    @Test
    fun parsesXiaomiWalletTransitSwipeResultAndIgnoresEntryOnlyPage() = runTest {
        val fullAccDao = FakeAccountDao()
        val fullAliasDao = FakeAccountAliasDao()
        val fullCatDao = FakeCategoryDao()
        DefaultSeedData.ensureAccountsAndTransitCard(fullAccDao, fullAliasDao)
        DefaultSeedData.ensurePresetCategories(fullCatDao)
        val fullAccounts = fullAccDao.getAll()
        val fullAliases = fullAliasDao.getEnabled()
        val fullCategories = fullCatDao.getAll()

        val swipeResult = ScreenSnapshot(
            packageName = "com.miui.tsmclient",
            className = "com.miui.tsmclient.ui.QuickCardActivity",
            texts = listOf("重庆畅通卡", "刷卡成功", "-2.00", "卡内余额 38.50元")
        )
        val swipeOutcome = parser.parse(swipeResult, fullAliases, fullAccounts, fullCategories)
        assertEquals(ScreenPageKind.PAY_SUCCESS, swipeOutcome.pageKind)
        val swipeDraft = swipeOutcome.draft!!
        assertEquals(TransactionType.EXPENSE, swipeDraft.type)
        assertEquals(MoneyAmount.parseToMicros("2.00"), swipeDraft.origAmountMicros)
        assertEquals("重庆畅通卡", swipeDraft.merchant)
        assertEquals("地铁公交", swipeDraft.categoryName)
        assertEquals("公交卡", fullAccounts.first { it.id == swipeDraft.accountId }.name)
        assertEquals("通勤", swipeDraft.tags)

        val exitInteger = ScreenSnapshot(
            packageName = "com.mipay.wallet",
            className = "com.mipay.wallet.TransitResultActivity",
            texts = listOf("小米钱包", "重庆畅通卡", "出站扣费2元", "卡内余额38.50元")
        )
        val exitOutcome = parser.parse(exitInteger, fullAliases, fullAccounts, fullCategories)
        assertEquals(ScreenPageKind.PAY_SUCCESS, exitOutcome.pageKind)
        val exitDraft = exitOutcome.draft!!
        assertEquals(MoneyAmount.parseToMicros("2.00"), exitDraft.origAmountMicros)
        assertEquals("重庆畅通卡", exitDraft.merchant)
        assertEquals("地铁公交", exitDraft.categoryName)
        assertEquals("通勤", exitDraft.tags)

        val entryOnly = ScreenSnapshot(
            packageName = "com.miui.tsmclient",
            className = "com.miui.tsmclient.ui.QuickCardActivity",
            texts = listOf("重庆畅通卡", "进站成功", "闸机已开启", "卡内余额 38.50元")
        )
        val entryOutcome = parser.parse(entryOnly, fullAliases, fullAccounts, fullCategories)
        assertEquals(ScreenPageKind.UNKNOWN, entryOutcome.pageKind)
        assertNull(entryOutcome.draft)
    }

    @Test
    fun classifiesBreakfastPaySuccessAndIgnoresBottomPromoAdsWithoutTagOrCategoryHijack() = runTest {
        val fullAccDao = FakeAccountDao()
        val fullAliasDao = FakeAccountAliasDao()
        val fullCatDao = FakeCategoryDao()
        DefaultSeedData.ensureAccountsAndTransitCard(fullAccDao, fullAliasDao)
        DefaultSeedData.ensurePresetCategories(fullCatDao)
        val fullAccounts = fullAccDao.getAll()
        val fullAliases = fullAliasDao.getEnabled()
        val fullCategories = fullCatDao.getAll()

        // 1. 支付成功页 + 稍等一下过渡占位文案 -> 三餐外食（有自建“早餐”时命中“早餐”），无淘宝/网购标签
        val loadingPaySuccess = ScreenSnapshot(
            packageName = "com.tencent.mm",
            className = "com.tencent.mm.plugin.wallet_core.ui.WalletPaidSuccessUI",
            texts = listOf(
                "支付成功",
                "逍遥优质胡辣汤（**华）",
                "¥5.50",
                "稍等一下...",
                "完成"
            )
        )
        val loadingOutcome = parser.parse(loadingPaySuccess, fullAliases, fullAccounts, fullCategories)
        assertEquals(ScreenPageKind.PAY_SUCCESS, loadingOutcome.pageKind)
        val loadingDraft = loadingOutcome.draft!!
        assertEquals("逍遥优质胡辣汤", loadingDraft.merchant)
        assertEquals(MoneyAmount.parseToMicros("5.50"), loadingDraft.origAmountMicros)
        assertEquals("三餐外食", loadingDraft.categoryName)
        assertFalse(TagTextCodec.parse(loadingDraft.tags).contains("淘宝"))
        assertFalse(TagTextCodec.parse(loadingDraft.tags).contains("网购"))

        val categoriesWithBreakfast = fullCategories + CategoryEntity(
            id = 9999L,
            name = "早餐",
            type = TransactionType.EXPENSE
        )
        val customBreakfastOutcome = parser.parse(loadingPaySuccess, fullAliases, fullAccounts, categoriesWithBreakfast)
        assertEquals("早餐", customBreakfastOutcome.draft!!.categoryName)

        // 2. 支付成功页 + 底部摇一摇/淘宝闪购可叠加立减广告 -> 依然命中三餐外食，且 tags 不含淘宝/网购
        val promoHijackAttempt = ScreenSnapshot(
            packageName = "com.tencent.mm",
            className = "com.tencent.mm.plugin.wallet_core.ui.WalletPaidSuccessUI",
            texts = listOf(
                "支付成功",
                "逍遥优质胡辣汤（**华）",
                "¥5.50",
                "摇一摇有优惠",
                "淘宝闪购可叠加立减",
                "摇一摇有优惠,淘宝闪购可叠加立减,查看",
                "完成"
            )
        )
        val cleanOutcome = parser.parse(promoHijackAttempt, fullAliases, fullAccounts, fullCategories)
        assertEquals(ScreenPageKind.PAY_SUCCESS, cleanOutcome.pageKind)
        val cleanDraft = cleanOutcome.draft!!
        assertEquals("逍遥优质胡辣汤", cleanDraft.merchant)
        assertEquals("三餐外食", cleanDraft.categoryName)
        assertFalse(TagTextCodec.parse(cleanDraft.tags).contains("淘宝"))
        assertFalse(TagTextCodec.parse(cleanDraft.tags).contains("网购"))

        // 无餐饮字眼店名 + 相同淘宝闪购广告 -> 不被劫持为网购百货，tags 为空
        val neutralShopWithPromo = ScreenSnapshot(
            packageName = "com.tencent.mm",
            className = "com.tencent.mm.plugin.wallet_core.ui.WalletPaidSuccessUI",
            texts = listOf(
                "支付成功",
                "张记小铺（**张）",
                "¥12.00",
                "摇一摇有优惠",
                "淘宝闪购可叠加立减",
                "摇一摇有优惠,淘宝闪购可叠加立减,查看",
                "完成"
            )
        )
        val neutralOutcome = parser.parse(neutralShopWithPromo, fullAliases, fullAccounts, fullCategories)
        assertEquals(ScreenPageKind.PAY_SUCCESS, neutralOutcome.pageKind)
        val neutralDraft = neutralOutcome.draft!!
        assertEquals("张记小铺", neutralDraft.merchant)
        assertEquals("其他支出", neutralDraft.categoryName)
        assertEquals("", neutralDraft.tags)
    }

    @Test
    fun extractsRealMerchantFromWeChatPersonalQrBillDetailInsteadOfNicknameAndPhone() = runTest {
        val fullAccDao = FakeAccountDao()
        val fullAliasDao = FakeAccountAliasDao()
        val fullCatDao = FakeCategoryDao()
        DefaultSeedData.ensureAccountsAndTransitCard(fullAccDao, fullAliasDao)
        DefaultSeedData.ensurePresetCategories(fullCatDao)
        val fullAccounts = fullAccDao.getAll()
        val fullAliases = fullAliasDao.getEnabled()
        val fullCategories = fullCatDao.getAll()

        // 3. 微信个人码账单详情：顶部为个人昵称+手机号，正文为扫二维码付款-给逍遥优质胡辣汤
        val qrBillDetail = ScreenSnapshot(
            packageName = "com.tencent.mm",
            className = "com.tencent.mm.plugin.webview.ui.tools.MMWebViewUI",
            texts = listOf(
                "账单详情",
                "静涵18238949916",
                "-5.50",
                "当前状态", "支付成功",
                "支付时间", "2026-11-24 08:15:20",
                "商品", "扫二维码付款-给逍遥优质胡辣汤",
                "支付方式", "零钱",
                "交易单号", "42000021562026112412345678",
                "账单服务", "在此商户的交易",
                "本服务由财付通提供",
                "全部账单"
            )
        )
        val outcome = parser.parse(qrBillDetail, fullAliases, fullAccounts, fullCategories)
        assertEquals(ScreenPageKind.BILL_DETAIL, outcome.pageKind)
        val draft = outcome.draft!!
        assertEquals(TransactionType.EXPENSE, draft.type)
        assertEquals(MoneyAmount.parseToMicros("5.50"), draft.origAmountMicros)
        assertEquals("逍遥优质胡辣汤", draft.merchant)
        assertEquals("三餐外食", draft.categoryName)

        // 兼容“向商户付款-给XXX”及微信用户泛昵称、以及收款“二维码收款-来自XXX”
        val qrVariantDetail = ScreenSnapshot(
            packageName = "com.tencent.mm",
            className = "com.tencent.mm.plugin.webview.ui.tools.MMWebViewUI",
            texts = listOf(
                "账单详情",
                "微信用户",
                "-12.00",
                "当前状态", "支付成功",
                "商品", "向商户付款-给永和大王",
                "支付方式", "零钱",
                "交易单号", "42000021562026112412345679",
                "本服务由财付通提供"
            )
        )
        val variantOutcome = parser.parse(qrVariantDetail, fullAliases, fullAccounts, fullCategories)
        assertEquals("永和大王", variantOutcome.draft!!.merchant)
        assertEquals("三餐外食", variantOutcome.draft!!.categoryName)

        val qrIncomeDetail = ScreenSnapshot(
            packageName = "com.tencent.mm",
            className = "com.tencent.mm.plugin.webview.ui.tools.MMWebViewUI",
            texts = listOf(
                "账单详情",
                "微信用户",
                "+50.00",
                "当前状态", "已收款",
                "商品", "二维码收款-来自张三",
                "收款方式", "零钱",
                "交易单号", "42000021562026112412345680",
                "本服务由财付通提供"
            )
        )
        val incomeOutcome = parser.parse(qrIncomeDetail, fullAliases, fullAccounts, fullCategories)
        assertEquals(TransactionType.INCOME, incomeOutcome.draft!!.type)
        assertEquals("张三", incomeOutcome.draft!!.merchant)
    }

    @Test
    fun parsesWeChat8066LegacyBillDetailMultilineNodesPayScoreAndSingleJumpLink() {
        // 1. 微信 8.0.66 旧版账单详情：MallOrderTransactionInfoUI / WalletOrderInfoOldUI + 多行合并节点 + 空格分隔字段 + 微信支付分
        val nodes = listOf(
            ScreenTextNode("账单详情", topPx = 80, heightPx = 44),
            ScreenTextNode("星巴克", topPx = 180, heightPx = 48),
            ScreenTextNode("-38.00", topPx = 260, heightPx = 110),
            ScreenTextNode("当前状态\n支付成功", topPx = 420, heightPx = 42),
            ScreenTextNode("支付时间\n2026-11-26 08:15:20", topPx = 500, heightPx = 42),
            ScreenTextNode("商品 星巴克拿铁", topPx = 580, heightPx = 42),
            ScreenTextNode("收款方全称 上海星巴克咖啡经营有限公司", topPx = 660, heightPx = 42),
            ScreenTextNode("支付方式\n招商银行信用卡(6688)", topPx = 740, heightPx = 42),
            ScreenTextNode("交易单号\n4200008066202611260001", topPx = 820, heightPx = 42),
            ScreenTextNode("微信支付分先享后付已免押", topPx = 900, heightPx = 40),
            ScreenTextNode("申请转账电子凭证", topPx = 980, heightPx = 40),
            ScreenTextNode("查看往来转账", topPx = 1040, heightPx = 40),
            ScreenTextNode("对订单有疑问", topPx = 1100, heightPx = 40),
            ScreenTextNode("查看账单详情", topPx = 1160, heightPx = 40)
        )
        val oldUiSnapshot = ScreenSnapshot(
            packageName = "com.tencent.mm",
            className = "com.tencent.mm.plugin.wallet_core.ui.WalletOrderInfoOldUI",
            texts = nodes.map { it.text },
            nodes = nodes,
            screenHeightPx = 2400
        )
        val oldOutcome = parser.parse(oldUiSnapshot, aliases, accounts, categories)
        assertEquals(ScreenPageKind.BILL_DETAIL, oldOutcome.pageKind)
        assertEquals("4200008066202611260001", oldOutcome.orderNo)
        val oldDraft = oldOutcome.draft!!
        assertEquals(TransactionType.EXPENSE, oldDraft.type)
        assertEquals(MoneyAmount.parseToMicros("38.00"), oldDraft.origAmountMicros)
        assertEquals("星巴克", oldDraft.merchant)
        assertEquals("6688", oldDraft.accountHint)
        assertTrue(oldDraft.note.contains("星巴克拿铁"))
        val expectedMillis = LocalDateTime.of(2026, 11, 26, 8, 15, 20)
            .atZone(testZone)
            .toInstant()
            .toEpochMilli()
        assertEquals(expectedMillis, oldDraft.timestamp)

        // 2. MallOrderTransactionInfoUI + 申请转账电子凭证 锚点（无头部“账单详情”仍可识别）
        val mallOrderSnapshot = ScreenSnapshot(
            packageName = "com.tencent.mm",
            className = "com.tencent.mm.plugin.order.ui.MallOrderTransactionInfoUI",
            texts = listOf(
                "瑞幸咖啡",
                "-19.90",
                "当前状态 支付完成",
                "支付时间\r\n2026-11-26 09:05:10",
                "商户全称 瑞幸咖啡(北京)有限公司",
                "支付方式 零钱",
                "交易单号 4200008066202611260002",
                "申请转账电子凭证"
            )
        )
        val mallOutcome = parser.parse(mallOrderSnapshot, aliases, accounts, categories)
        assertEquals(ScreenPageKind.BILL_DETAIL, mallOutcome.pageKind)
        assertEquals("4200008066202611260002", mallOutcome.orderNo)
        assertEquals(MoneyAmount.parseToMicros("19.90"), mallOutcome.draft!!.origAmountMicros)
        assertEquals("瑞幸咖啡", mallOutcome.draft!!.merchant)

        // 3. 强账单锚点诊断识别（如关闭状态/无金额详情页触发，服务号卡片不触发）
        val closedBillDetail = ScreenSnapshot(
            packageName = "com.tencent.mm",
            className = "com.tencent.mm.plugin.wallet_core.ui.WalletOrderInfoOldUI",
            texts = listOf("账单详情", "星巴克", "当前状态", "交易关闭", "交易单号", "4200008066202611260003")
        )
        assertTrue(parser.hasStrongBillDetailAnchors(closedBillDetail))
        val serviceVoucherCard = ScreenSnapshot(
            packageName = "com.tencent.mm",
            className = "android.widget.FrameLayout",
            texts = listOf("微信支付凭证", "付款金额", "¥38.50", "收款商户", "星巴克", "查看账单详情", "我的账单", "支付服务")
        )
        assertFalse(parser.hasStrongBillDetailAnchors(serviceVoucherCard))
    }

    @Test
    fun cleansBackHomeUiNoisePendingWrapperMaskedRealNameAndP2pCounterpartyNames() = runTest {
        val fullAccDao = FakeAccountDao()
        val fullAliasDao = FakeAccountAliasDao()
        val fullCatDao = FakeCategoryDao()
        DefaultSeedData.ensureAccountsAndTransitCard(fullAccDao, fullAliasDao)
        DefaultSeedData.ensurePresetCategories(fullCatDao)
        val fullAccounts = fullAccDao.getAll()
        val fullAliases = fullAliasDao.getEnabled()
        val fullCategories = fullCatDao.getAll()

        // 1. 支付宝支付成功页含「回首页」：仅回首页作完成按钮时仍可识别为 PAY_SUCCESS，且绝不把「回首页」当成商户
        val alipayBackHomeOnly = ScreenSnapshot(
            packageName = "com.eg.android.AlipayGphone",
            className = "com.alipay.android.msp.ui.views.MspContainerActivity",
            texts = listOf("支付成功", "¥12.50", "回首页", "继续逛逛", "加载中...")
        )
        val backHomeOnlyOutcome = parser.parse(alipayBackHomeOnly, fullAliases, fullAccounts, fullCategories)
        assertEquals(ScreenPageKind.PAY_SUCCESS, backHomeOnlyOutcome.pageKind)
        assertEquals("", backHomeOnlyOutcome.draft!!.merchant)
        assertEquals(MoneyAmount.parseToMicros("12.50"), backHomeOnlyOutcome.draft!!.origAmountMicros)

        val alipayBackHomeWithMerchant = ScreenSnapshot(
            packageName = "com.eg.android.AlipayGphone",
            className = "com.alipay.android.msp.ui.views.MspContainerActivity",
            texts = listOf("支付成功", "回首页", "沙县小吃", "¥15.00", "再来一单")
        )
        val backHomeMerchantOutcome = parser.parse(alipayBackHomeWithMerchant, fullAliases, fullAccounts, fullCategories)
        assertEquals(ScreenPageKind.PAY_SUCCESS, backHomeMerchantOutcome.pageKind)
        assertEquals("沙县小吃", backHomeMerchantOutcome.draft!!.merchant)
        assertEquals("三餐外食", backHomeMerchantOutcome.draft!!.categoryName)

        // 2. 「待...确认」/「待...确认收款」解包并保留店名括号
        val pendingConfirm1 = ScreenSnapshot(
            packageName = "com.tencent.mm",
            className = "WalletOrderInfoOldUI",
            texts = listOf("账单详情", "待逸民超市(两江名居桶装水)确认", "-18.00", "当前状态", "支付成功", "交易单号", "4200001234560001")
        )
        assertEquals(
            "逸民超市(两江名居桶装水)",
            parser.parse(pendingConfirm1, fullAliases, fullAccounts, fullCategories).draft!!.merchant
        )
        val pendingConfirm2 = pendingConfirm1.copy(
            texts = listOf("账单详情", "待逸民超市(两江名居桶装水)确认收款", "-18.00", "当前状态", "支付成功", "交易单号", "4200001234560002")
        )
        assertEquals(
            "逸民超市(两江名居桶装水)",
            parser.parse(pendingConfirm2, fullAliases, fullAccounts, fullCategories).draft!!.merchant
        )
        val pendingConfirm3 = pendingConfirm1.copy(
            texts = listOf("账单详情", "等待逸民超市(两江名居桶装水)确认", "-18.00", "当前状态", "支付成功", "交易单号", "4200001234560003")
        )
        assertEquals(
            "逸民超市(两江名居桶装水)",
            parser.parse(pendingConfirm3, fullAliases, fullAccounts, fullCategories).draft!!.merchant
        )

        // 3. 商户末尾脱敏实名括号清洗
        val maskedSuffixDetail = ScreenSnapshot(
            packageName = "com.tencent.mm",
            className = "WalletPaidSuccessUI",
            texts = listOf("支付成功", "逍遥优质胡辣汤(**华)", "¥5.50", "完成")
        )
        assertEquals(
            "逍遥优质胡辣汤",
            parser.parse(maskedSuffixDetail, fullAliases, fullAccounts, fullCategories).draft!!.merchant
        )

        // 4. 支付宝收到口令红包（对方 Cc）-> merchant=""，categoryName="人情礼金"，note 含收到口令红包
        val alipayReceiveRedPacket = ScreenSnapshot(
            packageName = "com.eg.android.AlipayGphone",
            className = "com.alipay.mobile.bill.detail.ui.BillDetailActivity",
            texts = listOf(
                "账单详情",
                "Cc",
                "18.88元",
                "交易成功",
                "创建时间", "2026-11-26 10:00:00",
                "收款方式", "账户余额",
                "红包说明", "收到口令红包",
                "账单分类", "红包",
                "订单号", "20261126220014000088"
            )
        )
        val receiveRedOutcome = parser.parse(alipayReceiveRedPacket, fullAliases, fullAccounts, fullCategories)
        assertEquals(ScreenPageKind.BILL_DETAIL, receiveRedOutcome.pageKind)
        val receiveRedDraft = receiveRedOutcome.draft!!
        assertEquals(TransactionType.INCOME, receiveRedDraft.type)
        assertEquals("", receiveRedDraft.merchant)
        assertEquals("人情礼金", receiveRedDraft.categoryName)
        assertTrue(receiveRedDraft.note.contains("收到口令红包"))

        // 5. 支付宝发口令红包 -> categoryName="红包礼金"，且保留「支付宝红包」标签商户
        val alipaySendRedPacket = ScreenSnapshot(
            packageName = "com.eg.android.AlipayGphone",
            className = "com.alipay.mobile.bill.detail.ui.BillDetailActivity",
            texts = listOf(
                "支付宝红包",
                "支出55元",
                "交易成功",
                "创建时间", "2026-11-26 11:00:00",
                "付款方式", "账户余额",
                "红包说明", "发口令红包",
                "订单号", "20261126220014000099"
            )
        )
        val sendRedDraft = parser.parse(alipaySendRedPacket, fullAliases, fullAccounts, fullCategories).draft!!
        assertEquals(TransactionType.EXPENSE, sendRedDraft.type)
        assertEquals("支付宝红包", sendRedDraft.merchant)
        assertEquals("红包礼金", sendRedDraft.categoryName)

        // 6. 微信 P2P 转账对象「张三」与红包对象「Cc」清洗为 ""，而商用扫码「扫二维码付款-给逍遥优质胡辣汤」保留店名
        val wechatP2pTransfer = ScreenSnapshot(
            packageName = "com.tencent.mm",
            className = "com.tencent.mm.plugin.remittance.ui.RemittanceDetailUI",
            texts = listOf(
                "向张三转账",
                "-200.00",
                "当前状态", "对方已收钱",
                "转账说明", "聚餐AA",
                "转账单号", "100005000120261126001"
            )
        )
        assertEquals("", parser.parse(wechatP2pTransfer, fullAliases, fullAccounts, fullCategories).draft!!.merchant)

        val wechatRedPacketPerson = ScreenSnapshot(
            packageName = "com.tencent.mm",
            className = "com.tencent.mm.plugin.webview.ui.tools.MMWebViewUI",
            texts = listOf(
                "Cc",
                "-20.00",
                "微信红包",
                "当前状态", "已被领完",
                "交易单号", "100003990120261126002"
            )
        )
        val wechatRedDraft = parser.parse(wechatRedPacketPerson, fullAliases, fullAccounts, fullCategories).draft!!
        assertEquals("", wechatRedDraft.merchant)
        assertEquals("红包礼金", wechatRedDraft.categoryName)

        val commercialQr = ScreenSnapshot(
            packageName = "com.tencent.mm",
            className = "com.tencent.mm.plugin.webview.ui.tools.MMWebViewUI",
            texts = listOf(
                "账单详情",
                "静涵18238949916",
                "-5.50",
                "当前状态", "支付成功",
                "商品", "扫二维码付款-给逍遥优质胡辣汤",
                "支付方式", "零钱",
                "交易单号", "420000215620261126999"
            )
        )
        assertEquals("逍遥优质胡辣汤", parser.parse(commercialQr, fullAliases, fullAccounts, fullCategories).draft!!.merchant)

        // 购物使用支付宝红包抵扣（带金额）不应误归「红包礼金」
        val meituanWithRedPacketDiscount = ScreenSnapshot(
            packageName = "com.eg.android.AlipayGphone",
            className = "BillDetailActivity",
            texts = listOf(
                "账单详情",
                "美团",
                "-18.00",
                "交易成功",
                "组合支付",
                "支付宝红包 3.00元",
                "付款方式", "花呗",
                "订单号", "20261126220014000123"
            )
        )
        val meituanDraft = parser.parse(meituanWithRedPacketDiscount, fullAliases, fullAccounts, fullCategories).draft!!
        assertEquals("三餐外食", meituanDraft.categoryName)
    }

    @Test
    fun validatesWeChat8066SafeWindowClassesAndAccessibilityWindowCandidates() {
        val safeWeChatClasses = listOf(
            "com.tencent.mm.plugin.wallet_core.ui.WalletOrderInfoOldUI",
            "com.tencent.mm.plugin.order.ui.MallOrderTransactionInfoUI",
            "com.tencent.mm.plugin.lite.ui.WxaLiteAppTransparentUI",
            "com.tencent.mm.plugin.appbrand.ui.AppBrandUI",
            "com.tencent.mm.flutter.ui.FlutterActivity",
            "com.tencent.kinda.framework.widget.base.KindaActivity",
            "com.tencent.mm.plugin.wallet.pay.ui.WalletPayUI",
            "com.tencent.mm.plugin.luckymoney.ui.LuckyMoneyDetailUI",
            "com.tencent.mm.plugin.collect.ui.CollectMainUI"
        )
        for (cls in safeWeChatClasses) {
            assertFalse("Expected safe window class not blocked: $cls", ScreenBillParser.isBlockedChatOrFeedWindow("com.tencent.mm", cls))
        }
        assertTrue(ScreenBillParser.isBlockedChatOrFeedWindow("com.tencent.mm", "com.tencent.mm.ui.LauncherUI"))
        assertTrue(ScreenBillParser.isBlockedChatOrFeedWindow("com.tencent.mm", "com.tencent.mm.ui.chatting.ChattingUI"))

        assertTrue(KairoAccessibilityService.isWindowClassCandidate("androidx.appcompat.app.AppCompatActivity"))
        assertTrue(KairoAccessibilityService.isWindowClassCandidate("androidx.fragment.app.FragmentActivity"))
        assertTrue(KairoAccessibilityService.isWindowClassCandidate("com.tencent.mm.plugin.wallet_core.ui.WalletOrderInfoOldUI"))
        assertFalse(KairoAccessibilityService.isWindowClassCandidate("android.widget.FrameLayout"))
        assertFalse(KairoAccessibilityService.isWindowClassCandidate("android.view.View"))
        assertFalse(KairoAccessibilityService.isWindowClassCandidate("androidx.recyclerview.widget.RecyclerView"))
        assertFalse(KairoAccessibilityService.isWindowClassCandidate("androidx.viewpager2.widget.ViewPager2"))
        assertFalse(KairoAccessibilityService.isWindowClassCandidate("androidx.core.widget.NestedScrollView"))
        assertFalse(KairoAccessibilityService.isWindowClassCandidate("android.app.Dialog"))
        assertFalse(KairoAccessibilityService.isWindowClassCandidate("android.widget.Toast"))
        assertFalse(KairoAccessibilityService.isWindowClassCandidate("android.widget.PopupWindow"))
    }

    @Test
    fun parsesAlipayTransientPaySuccessOverlayWithoutDoneButtonAndBlocksCheckout() = runTest {
        val fullAccDao = FakeAccountDao()
        val fullAliasDao = FakeAccountAliasDao()
        val fullCatDao = FakeCategoryDao()
        DefaultSeedData.ensureAccountsAndTransitCard(fullAccDao, fullAliasDao)
        val mybankId = fullAccDao.insert(
            AccountEntity(
                name = "支付宝·网商银行",
                groupType = com.yubrae.app.data.db.AccountGroup.DEBIT,
                currencyCode = "CNY"
            )
        )
        DefaultSeedData.ensurePresetCategories(fullCatDao)
        val fullAccounts = fullAccDao.getAll()
        val fullAliases = fullAliasDao.getEnabled()
        val fullCategories = fullCatDao.getAll()

        // 1. 支付宝极速付款成功瞬态弹层（无“完成”按钮，仅“付款成功 + ¥12.66 + 淘宝闪购 + 网商银行”）
        val transientOverlay = ScreenSnapshot(
            packageName = "com.eg.android.AlipayGphone",
            className = "com.alipay.android.msp.ui.views.MspContainerActivity",
            texts = listOf("付款成功", "¥12.66", "淘宝闪购", "网商银行")
        )
        val outcome = parser.parse(transientOverlay, fullAliases, fullAccounts, fullCategories)
        assertEquals(ScreenPageKind.PAY_SUCCESS, outcome.pageKind)
        assertFalse(outcome.shouldFallbackToLlm)
        val draft = outcome.draft
        assertNotNull(draft)
        assertEquals(TransactionType.EXPENSE, draft!!.type)
        assertEquals(MoneyAmount.parseToMicros("12.66"), draft.origAmountMicros)
        assertEquals("CNY", draft.origCurrency)
        assertEquals("淘宝闪购", draft.merchant)
        assertEquals(mybankId, draft.accountId)

        // 2. 拆分节点形式的支付宝瞬态付款成功弹层同样识别
        val splitTransient = ScreenSnapshot(
            packageName = "com.eg.android.AlipayGphone",
            className = "",
            texts = listOf("支付成功", "¥", "12", ".66", "淘宝闪购")
        )
        val splitOutcome = parser.parse(splitTransient, fullAliases, fullAccounts, fullCategories)
        assertEquals(ScreenPageKind.PAY_SUCCESS, splitOutcome.pageKind)
        assertEquals(MoneyAmount.parseToMicros("12.66"), splitOutcome.draft!!.origAmountMicros)

        // 3. 含收银台待付款标记（即使有付款成功促销文案）绝不误触
        val prepayWithPromo = ScreenSnapshot(
            packageName = "com.eg.android.AlipayGphone",
            className = "com.alipay.android.msp.ui.views.MspContainerActivity",
            texts = listOf("付款成功", "¥12.66", "确认付款", "立即支付")
        )
        val prepayOutcome = parser.parse(prepayWithPromo, fullAliases, fullAccounts, fullCategories)
        assertEquals(ScreenPageKind.UNKNOWN, prepayOutcome.pageKind)
        assertNull(prepayOutcome.draft)

        // 4. 仅含优惠/余额金额行（无有效独立正金额行）且无完成按钮不误触
        val discountOnly = ScreenSnapshot(
            packageName = "com.eg.android.AlipayGphone",
            className = "",
            texts = listOf("付款成功", "已优惠¥5.00", "卡内余额 100.00元", "-¥2.00")
        )
        val discountOnlyOutcome = parser.parse(discountOnly, fullAliases, fullAccounts, fullCategories)
        assertEquals(ScreenPageKind.UNKNOWN, discountOnlyOutcome.pageKind)
        assertNull(discountOnlyOutcome.draft)
    }

    @Test
    fun parsesTaobaoPaySuccessWithBottomGuessYouLikeRecommendationFeedUnpolluted() = runTest {
        val fullAccDao = FakeAccountDao()
        val fullAliasDao = FakeAccountAliasDao()
        val fullCatDao = FakeCategoryDao()
        DefaultSeedData.ensureAccountsAndTransitCard(fullAccDao, fullAliasDao)
        DefaultSeedData.ensurePresetCategories(fullCatDao)
        val fullAccounts = fullAccDao.getAll()
        val fullAliases = fullAliasDao.getEnabled()
        val fullCategories = fullCatDao.getAll()

        val taobaoPaySuccessWithFeed = ScreenSnapshot(
            packageName = "com.taobao.taobao",
            className = "com.taobao.pay.PaySuccessActivity",
            texts = listOf(
                "支付成功",
                "淘宝闪购",
                "书亦烧仙草(蔡家店)",
                "¥12.66",
                "查看订单",
                "返回首页",
                "猜你喜欢",
                "推荐爆款猫粮2kg",
                "¥39.90",
                "加入购物车",
                "立即抢购",
                "领券购买"
            )
        )
        val outcome = parser.parse(taobaoPaySuccessWithFeed, fullAliases, fullAccounts, fullCategories)
        assertEquals(ScreenPageKind.PAY_SUCCESS, outcome.pageKind)
        val draft = outcome.draft
        assertNotNull(draft)
        assertEquals(TransactionType.EXPENSE, draft!!.type)
        assertEquals(MoneyAmount.parseToMicros("12.66"), draft.origAmountMicros)
        assertEquals("书亦烧仙草(蔡家店)", draft.merchant)
        assertEquals("外卖", draft.categoryName)
        assertFalse(draft.note.contains("猫粮"))

        // 极简淘宝支付成功页（无店名、带“为您推荐”和加购按钮）
        val minimalTaobaoPaySuccess = ScreenSnapshot(
            packageName = "com.taobao.taobao",
            className = "com.taobao.pay.PaySuccessActivity",
            texts = listOf(
                "付款成功",
                "¥28.50",
                "继续逛逛",
                "为您推荐",
                "网红保温杯",
                "¥99.00",
                "加入购物车"
            )
        )
        val minimalOutcome = parser.parse(minimalTaobaoPaySuccess, fullAliases, fullAccounts, fullCategories)
        assertEquals(ScreenPageKind.PAY_SUCCESS, minimalOutcome.pageKind)
        assertNotNull(minimalOutcome.draft)
        assertEquals(MoneyAmount.parseToMicros("28.50"), minimalOutcome.draft!!.origAmountMicros)
        assertFalse(minimalOutcome.draft!!.merchant.contains("保温杯"))
    }

    @Test
    fun parsesTaobaoFlashSaleTakeoutProgressAndOrderPagesWithRealStoreAndTakeoutCategory() = runTest {
        val fullAccDao = FakeAccountDao()
        val fullAliasDao = FakeAccountAliasDao()
        val fullCatDao = FakeCategoryDao()
        DefaultSeedData.ensureAccountsAndTransitCard(fullAccDao, fullAliasDao)
        DefaultSeedData.ensurePresetCategories(fullCatDao)
        val fullAccounts = fullAccDao.getAll()
        val fullAliases = fullAliasDao.getEnabled()
        val fullCategories = fullCatDao.getAll()

        // 1. 淘宝闪购外卖进度页（等待商家接单 + 预计21:28送达 + 实付 + 底部猜你喜欢）
        val waitingMerchantSnapshot = ScreenSnapshot(
            packageName = "com.taobao.taobao",
            className = "com.taobao.order.detail.OrderDetailActivity",
            texts = listOf(
                "淘宝闪购",
                "等待商家接单",
                "预计21:28送达",
                "催单",
                "修改地址",
                "取消订单",
                "联系商家",
                "联系骑士",
                "打赏骑士",
                "书亦烧仙草(蔡家店)",
                "烧仙草大杯 x1",
                "¥15.00",
                "打包费 ¥1.00",
                "配送费 ¥2.00",
                "实付 ¥12.66",
                "猜你喜欢",
                "蜜雪冰城(观音桥店)",
                "¥39.90",
                "加入购物车"
            )
        )
        val out1 = parser.parse(waitingMerchantSnapshot, fullAliases, fullAccounts, fullCategories)
        assertEquals(ScreenPageKind.ORDER_DETAIL, out1.pageKind)
        val draft1 = out1.draft
        assertNotNull(draft1)
        assertEquals(TransactionType.EXPENSE, draft1!!.type)
        assertEquals(MoneyAmount.parseToMicros("12.66"), draft1.origAmountMicros)
        assertEquals("书亦烧仙草(蔡家店)", draft1.merchant)
        assertEquals("外卖", draft1.categoryName)
        assertTrue(draft1.note.contains("烧仙草大杯"))

        // 2. 骑士到店 + 无显式“实付”仅“小计 ¥12.66” + 底部“附近好店”推荐流
        val riderArrivedSubtotalSnapshot = ScreenSnapshot(
            packageName = "com.taobao.taobao",
            className = "com.taobao.order.detail.OrderDetailActivity",
            texts = listOf(
                "淘宝闪购",
                "骑士到店",
                "预计21:28送达",
                "书亦烧仙草(蔡家店) >",
                "杨枝甘露 x1",
                "¥16.00",
                "打包费 ¥1.00",
                "商家优惠 -¥4.34",
                "小计 ¥12.66",
                "附近好店",
                "肯德基(蔡家店)",
                "¥49.00",
                "立即购买"
            )
        )
        val out2 = parser.parse(riderArrivedSubtotalSnapshot, fullAliases, fullAccounts, fullCategories)
        assertEquals(ScreenPageKind.ORDER_DETAIL, out2.pageKind)
        val draft2 = out2.draft
        assertNotNull(draft2)
        assertEquals(MoneyAmount.parseToMicros("12.66"), draft2!!.origAmountMicros)
        assertEquals("书亦烧仙草(蔡家店)", draft2.merchant)
        assertEquals("外卖", draft2.categoryName)

        // 3. 共支付 / 已支付 / 已付 写法 + 商家备餐中
        val paidVariantSnapshot = ScreenSnapshot(
            packageName = "com.taobao.taobao",
            className = "com.taobao.order.detail.OrderDetailActivity",
            texts = listOf(
                "淘宝闪购",
                "商家备餐中",
                "订单进度",
                "书亦烧仙草(蔡家店)",
                "共支付 ¥12.66",
                "联系商家"
            )
        )
        val out3 = parser.parse(paidVariantSnapshot, fullAliases, fullAccounts, fullCategories)
        assertEquals(ScreenPageKind.ORDER_DETAIL, out3.pageKind)
        assertEquals(MoneyAmount.parseToMicros("12.66"), out3.draft!!.origAmountMicros)
        assertEquals("书亦烧仙草(蔡家店)", out3.draft!!.merchant)
        assertEquals("外卖", out3.draft!!.categoryName)

        // 3b. 顶部纯状态行“已支付”位于门店之上时不触发元数据边界提前截断，且下方“已付 ¥12.66”正常解析
        val topStatusPaidOrder = ScreenSnapshot(
            packageName = "com.taobao.taobao",
            className = "com.taobao.order.detail.OrderDetailActivity",
            texts = listOf(
                "淘宝闪购",
                "已支付",
                "预计21:28送达",
                "书亦烧仙草(蔡家店) >",
                "烧仙草大杯 x1",
                "已付 ¥12.66",
                "联系商家"
            )
        )
        val out3b = parser.parse(topStatusPaidOrder, fullAliases, fullAccounts, fullCategories)
        assertEquals(ScreenPageKind.ORDER_DETAIL, out3b.pageKind)
        assertEquals(MoneyAmount.parseToMicros("12.66"), out3b.draft!!.origAmountMicros)
        assertEquals("书亦烧仙草(蔡家店)", out3b.draft!!.merchant)
        assertEquals("外卖", out3b.draft!!.categoryName)

        // 4. 核验“当前状态 -> 已支付”不会被误判为金额标签吞并下一行
        val statusPaidBill = ScreenSnapshot(
            packageName = "com.eg.android.AlipayGphone",
            className = "BillDetailActivity",
            texts = listOf(
                "账单详情",
                "瑞幸咖啡",
                "-19.90",
                "当前状态", "已支付",
                "创建时间", "2026-11-27 10:00:00",
                "付款方式", "花呗",
                "订单号", "2026112700019900"
            )
        )
        val out4 = parser.parse(statusPaidBill, fullAliases, fullAccounts, fullCategories)
        assertEquals(ScreenPageKind.BILL_DETAIL, out4.pageKind)
        assertEquals(MoneyAmount.parseToMicros("19.90"), out4.draft!!.origAmountMicros)
        assertEquals("瑞幸咖啡", out4.draft!!.merchant)
    }

    @Test
    fun preservesStrictNonMisfireGuardsForTaobaoBrowseCartUnpaidListAlipayPromoAndChat() = runTest {
        val fullAccDao = FakeAccountDao()
        val fullAliasDao = FakeAccountAliasDao()
        val fullCatDao = FakeCategoryDao()
        DefaultSeedData.ensureAccountsAndTransitCard(fullAccDao, fullAliasDao)
        DefaultSeedData.ensurePresetCategories(fullCatDao)
        val fullAccounts = fullAccDao.getAll()
        val fullAliases = fullAliasDao.getEnabled()
        val fullCategories = fullCatDao.getAll()

        // 1. 淘宝商品详情页（正文区含加入购物车/立即购买）不误触
        val taobaoProduct = ScreenSnapshot(
            packageName = "com.taobao.taobao",
            className = "ProductDetailActivity",
            texts = listOf("书亦烧仙草团购券", "¥12.66", "预计21:28送达", "加入购物车", "立即购买", "猜你喜欢")
        )
        assertEquals(ScreenPageKind.UNKNOWN, parser.parse(taobaoProduct, fullAliases, fullAccounts, fullCategories).pageKind)

        // 2. 淘宝购物车（含小计/合计与去结算）不误触
        val taobaoCart = ScreenSnapshot(
            packageName = "com.taobao.taobao",
            className = "CartActivity",
            texts = listOf("购物车", "书亦烧仙草(蔡家店)", "小计 ¥12.66", "合计 ¥12.66", "去结算")
        )
        assertEquals(ScreenPageKind.UNKNOWN, parser.parse(taobaoCart, fullAliases, fullAccounts, fullCategories).pageKind)

        // 3. 淘宝待付款订单页不误触
        val taobaoUnpaid = ScreenSnapshot(
            packageName = "com.taobao.taobao",
            className = "OrderDetailActivity",
            texts = listOf("订单详情", "待付款", "等待买家付款", "书亦烧仙草(蔡家店)", "实付 ¥12.66", "去付款")
        )
        assertEquals(ScreenPageKind.UNKNOWN, parser.parse(taobaoUnpaid, fullAliases, fullAccounts, fullCategories).pageKind)

        // 4. 淘宝全部订单列表页不误触
        val taobaoOrderList = ScreenSnapshot(
            packageName = "com.taobao.taobao",
            className = "OrderListActivity",
            texts = listOf(
                "全部订单",
                "等待商家接单", "书亦烧仙草(蔡家店)", "实付 ¥12.66", "再来一单",
                "已送达", "蜜雪冰城(观音桥店)", "实付 ¥9.90", "再来一单",
                "猜你喜欢"
            )
        )
        assertEquals(ScreenPageKind.UNKNOWN, parser.parse(taobaoOrderList, fullAliases, fullAccounts, fullCategories).pageKind)

        // 5. 支付宝支付成功页底部含“淘宝闪购可叠加立减”广告，不劫持分类或标签
        val alipayPromoAd = ScreenSnapshot(
            packageName = "com.eg.android.AlipayGphone",
            className = "com.alipay.android.msp.ui.views.MspContainerActivity",
            texts = listOf(
                "支付成功",
                "张记小铺",
                "¥12.00",
                "摇一摇有优惠",
                "淘宝闪购可叠加立减",
                "完成"
            )
        )
        val promoOutcome = parser.parse(alipayPromoAd, fullAliases, fullAccounts, fullCategories)
        assertEquals(ScreenPageKind.PAY_SUCCESS, promoOutcome.pageKind)
        assertEquals("张记小铺", promoOutcome.draft!!.merchant)
        assertEquals("其他支出", promoOutcome.draft!!.categoryName)
        assertEquals("", promoOutcome.draft!!.tags)

        // 6. 支付宝聊天/服务号通知流即便含“付款成功”+“¥12.66”仍严格拦截
        val alipayServiceFeed = ScreenSnapshot(
            packageName = "com.eg.android.AlipayGphone",
            className = "",
            texts = listOf("账单助手", "动账提醒", "付款成功", "¥12.66", "查看账单详情")
        )
        val feedOutcome = parser.parse(alipayServiceFeed, fullAliases, fullAccounts, fullCategories)
        assertEquals(ScreenPageKind.UNKNOWN, feedOutcome.pageKind)
        assertNull(feedOutcome.draft)
    }
}

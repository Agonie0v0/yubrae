package com.yubrae.app.capture.screen

import com.yubrae.app.capture.FakeAccountAliasDao
import com.yubrae.app.capture.FakeAccountDao
import com.yubrae.app.capture.FakeCategoryDao
import com.yubrae.app.core.model.MoneyAmount
import com.yubrae.app.data.db.AccountAliasEntity
import com.yubrae.app.data.db.AccountEntity
import com.yubrae.app.data.db.AccountGroup
import com.yubrae.app.data.db.AliasMatchType
import com.yubrae.app.data.db.CategoryEntity
import com.yubrae.app.data.db.DefaultSeedData
import com.yubrae.app.data.db.TransactionType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.ZoneId
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class CheckoutSessionTrackerTest {

    private val testZone = ZoneId.of("Asia/Shanghai")
    private val parser = ScreenBillParser(zoneId = testZone)

    private lateinit var accounts: List<AccountEntity>
    private lateinit var aliases: List<AccountAliasEntity>
    private lateinit var categories: List<CategoryEntity>

    private var mybankAccountId: Long = 0L
    private var huabeiAccountId: Long = 0L
    private var cmbCreditAccountId: Long = 0L
    private var alipayAccountId: Long = 0L

    @Before
    fun setup() = runTest {
        val accountDao = FakeAccountDao()
        val aliasDao = FakeAccountAliasDao()
        val categoryDao = FakeCategoryDao()
        DefaultSeedData.ensureAccountsAndTransitCard(accountDao, aliasDao)
        DefaultSeedData.ensurePresetCategories(categoryDao)

        alipayAccountId = accountDao.getByName("支付宝")!!.id
        mybankAccountId = accountDao.insert(
            AccountEntity(
                name = "支付宝·网商银行",
                groupType = AccountGroup.DEBIT,
                currencyCode = "CNY"
            )
        )
        huabeiAccountId = accountDao.insert(
            AccountEntity(
                name = "花呗",
                groupType = AccountGroup.CREDIT,
                currencyCode = "CNY"
            )
        )
        cmbCreditAccountId = accountDao.insert(
            AccountEntity(
                name = "招商信用卡6688",
                groupType = AccountGroup.CREDIT,
                currencyCode = "CNY"
            )
        )
        aliasDao.insertAll(
            listOf(
                AccountAliasEntity(
                    accountId = mybankAccountId,
                    matchType = AliasMatchType.CARD_LAST4,
                    pattern = "6978",
                    priority = 95
                ),
                AccountAliasEntity(
                    accountId = mybankAccountId,
                    matchType = AliasMatchType.KEYWORD,
                    pattern = "网商银行",
                    priority = 90
                ),
                AccountAliasEntity(
                    accountId = huabeiAccountId,
                    matchType = AliasMatchType.KEYWORD,
                    pattern = "花呗",
                    priority = 88
                ),
                AccountAliasEntity(
                    accountId = cmbCreditAccountId,
                    matchType = AliasMatchType.CARD_LAST4,
                    pattern = "6688",
                    priority = 95
                )
            )
        )

        accounts = accountDao.getAll()
        aliases = aliasDao.getEnabled()
        categories = categoryDao.getAll()
    }

    @Test
    fun completesTaobaoFlashSaleFlowAcrossAllThreeConfirmationVariantsAndConsumesOnce() {
        val baseTime = 1_732_810_000_000L

        val stage0Snapshot = ScreenSnapshot(
            packageName = "com.taobao.taobao",
            className = "com.taobao.order.ConfirmOrderActivity",
            texts = listOf(
                "淘宝闪购",
                "确认订单",
                "书亦烧仙草(蔡家店) >",
                "烧仙草大杯 x1",
                "打包费 ¥1.00",
                "配送费 ¥2.00",
                "已优惠 ¥3.00",
                "实付 ¥12.66",
                "提交订单"
            ),
            capturedAtMillis = baseTime
        )
        val stage1Snapshot = ScreenSnapshot(
            packageName = "com.eg.android.AlipayGphone",
            className = "com.alipay.android.msp.ui.views.MspContainerActivity",
            texts = listOf(
                "付款详情",
                "¥12.66",
                "淘宝闪购",
                "支付优惠", "-¥3.00",
                "付款方式", "网商银行储蓄卡(6978)",
                "确认付款"
            ),
            capturedAtMillis = baseTime + 4_000L
        )

        // 变体 A：支付宝无金额无「完成」按钮的付款成功闪帧 -> 立即合成完整账单并一次性消费
        run {
            val tracker = CheckoutSessionTracker()
            tracker.recordCheckout(parser.extractPendingCheckout(stage0Snapshot)!!)
            tracker.recordCheckout(parser.extractPendingCheckout(stage1Snapshot)!!)
            assertTrue(tracker.hasActivePendingCheckout(baseTime + 7_000L))

            val flashSnapshot = ScreenSnapshot(
                packageName = "com.eg.android.AlipayGphone",
                className = "com.alipay.android.msp.ui.views.MspContainerActivity",
                texts = listOf("付款成功", "淘宝闪购"),
                capturedAtMillis = baseTime + 7_000L
            )
            val signal = parser.detectCheckoutCompletionSignal(flashSnapshot)!!
            val outcome = tracker.consumeIfConfirmed(flashSnapshot, signal, parser, aliases, accounts, categories)
            assertNotNull(outcome)
            assertEquals(ScreenPageKind.PAY_SUCCESS, outcome!!.pageKind)
            val draft = outcome.draft!!
            assertEquals(TransactionType.EXPENSE, draft.type)
            assertEquals(MoneyAmount.parseToMicros("12.66"), draft.origAmountMicros)
            assertEquals("CNY", draft.origCurrency)
            assertEquals("书亦烧仙草(蔡家店)", draft.merchant)
            assertEquals("外卖", draft.categoryName)
            assertEquals(mybankAccountId, draft.accountId)
            assertEquals("6978", draft.accountHint)
            assertEquals("SCREEN_CHECKOUT_CONFIRMED", draft.parserUsed)
            assertTrue("Expected tag 淘宝, got ${draft.tags}", draft.tags.contains("淘宝"))
            assertTrue(draft.note.contains("烧仙草大杯"))
            assertTrue(draft.note.contains("网商银行"))

            // 一次性消费：回到淘宝「支付成功 + 查看订单」不再重复产出
            assertFalse(tracker.hasActivePendingCheckout(baseTime + 8_000L))
            val bounceBackSnapshot = ScreenSnapshot(
                packageName = "com.taobao.taobao",
                className = "com.taobao.pay.PaySuccessActivity",
                texts = listOf("支付成功", "查看订单", "返回首页"),
                capturedAtMillis = baseTime + 8_000L
            )
            val bounceSignal = parser.detectCheckoutCompletionSignal(bounceBackSnapshot)!!
            assertNull(tracker.consumeIfConfirmed(bounceBackSnapshot, bounceSignal, parser, aliases, accounts, categories))
        }

        // 变体 B：跳过支付宝闪帧，直接回到淘宝「支付成功 + 查看订单」无金额页（且底层带隐藏「购物车」）
        run {
            val tracker = CheckoutSessionTracker()
            tracker.recordCheckout(parser.extractPendingCheckout(stage0Snapshot)!!)
            tracker.recordCheckout(parser.extractPendingCheckout(stage1Snapshot)!!)

            val bounceNodes = listOf(
                ScreenTextNode("购物车", isVisibleToUser = false),
                ScreenTextNode("提交订单", isVisibleToUser = false),
                ScreenTextNode("支付成功", isVisibleToUser = true),
                ScreenTextNode("查看订单", isVisibleToUser = true),
                ScreenTextNode("返回首页", isVisibleToUser = true)
            )
            val bounceSnapshot = ScreenSnapshot(
                packageName = "com.taobao.taobao",
                className = "com.taobao.pay.PaySuccessActivity",
                texts = bounceNodes.map { it.text },
                nodes = bounceNodes,
                capturedAtMillis = baseTime + 9_000L
            )
            val signal = parser.detectCheckoutCompletionSignal(bounceSnapshot)!!
            val outcome = tracker.consumeIfConfirmed(bounceSnapshot, signal, parser, aliases, accounts, categories)
            assertNotNull(outcome)
            assertEquals(ScreenPageKind.PAY_SUCCESS, outcome!!.pageKind)
            val draft = outcome.draft!!
            assertEquals(MoneyAmount.parseToMicros("12.66"), draft.origAmountMicros)
            assertEquals("书亦烧仙草(蔡家店)", draft.merchant)
            assertEquals("外卖", draft.categoryName)
            assertEquals(mybankAccountId, draft.accountId)
            assertEquals("SCREEN_CHECKOUT_CONFIRMED", draft.parserUsed)
            assertTrue(draft.tags.contains("淘宝"))
            assertFalse(tracker.hasActivePendingCheckout(baseTime + 9_500L))
        }

        // 变体 C：直接跳入淘宝闪购外卖进度首屏（等待商家接单 + 地图，无金额 + 底层隐藏「购物车」）
        run {
            val tracker = CheckoutSessionTracker()
            tracker.recordCheckout(parser.extractPendingCheckout(stage0Snapshot)!!)
            tracker.recordCheckout(parser.extractPendingCheckout(stage1Snapshot)!!)

            val mapNodes = listOf(
                ScreenTextNode("购物车", isVisibleToUser = false),
                ScreenTextNode("淘宝闪购", isVisibleToUser = true),
                ScreenTextNode("等待商家接单", isVisibleToUser = true),
                ScreenTextNode("预计21:28送达", isVisibleToUser = true),
                ScreenTextNode("催单", isVisibleToUser = true),
                ScreenTextNode("联系商家", isVisibleToUser = true),
                ScreenTextNode("取消订单", isVisibleToUser = true)
            )
            val mapSnapshot = ScreenSnapshot(
                packageName = "com.taobao.taobao",
                className = "com.taobao.order.detail.OrderDetailActivity",
                texts = mapNodes.map { it.text },
                nodes = mapNodes,
                capturedAtMillis = baseTime + 10_000L
            )
            assertFalse(parser.isExplicitUnpaidOrFailedScreen(mapSnapshot))
            val signal = parser.detectCheckoutCompletionSignal(mapSnapshot)!!
            val outcome = tracker.consumeIfConfirmed(mapSnapshot, signal, parser, aliases, accounts, categories)
            assertNotNull(outcome)
            assertEquals(ScreenPageKind.ORDER_DETAIL, outcome!!.pageKind)
            val draft = outcome.draft!!
            assertEquals(MoneyAmount.parseToMicros("12.66"), draft.origAmountMicros)
            assertEquals("书亦烧仙草(蔡家店)", draft.merchant)
            assertEquals("外卖", draft.categoryName)
            assertEquals(mybankAccountId, draft.accountId)
            assertEquals("SCREEN_CHECKOUT_CONFIRMED", draft.parserUsed)
            assertTrue(draft.tags.contains("淘宝"))
        }
    }

    @Test
    fun tracksPaymentMethodAndAmountSwitchesInAlipayCashierUsingFinalSelection() {
        val baseTime = 1_732_820_000_000L
        val tracker = CheckoutSessionTracker()

        val stage0 = parser.extractPendingCheckout(
            ScreenSnapshot(
                packageName = "com.taobao.taobao",
                className = "ConfirmOrderActivity",
                texts = listOf("淘宝闪购", "确认订单", "霸王茶姬(光环店) >", "伯牙绝弦 x1", "配送费 ¥2.00", "实付 ¥18.80", "提交订单"),
                capturedAtMillis = baseTime
            )
        )!!
        tracker.recordCheckout(stage0)

        // 先选招商银行信用卡(6688) ¥18.80
        val stage1Cmb = parser.extractPendingCheckout(
            ScreenSnapshot(
                packageName = "com.eg.android.AlipayGphone",
                className = "MspContainerActivity",
                texts = listOf("付款详情", "¥18.80", "淘宝闪购", "付款方式", "招商银行信用卡(6688)", "确认付款"),
                capturedAtMillis = baseTime + 2_000L
            )
        )!!
        tracker.recordCheckout(stage1Cmb)

        // 再切换为花呗 ¥18.80（验证旧卡尾号 6688 被清理）
        val stage1Huabei = parser.extractPendingCheckout(
            ScreenSnapshot(
                packageName = "com.eg.android.AlipayGphone",
                className = "MspContainerActivity",
                texts = listOf("付款详情", "¥18.80", "淘宝闪购", "付款方式", "花呗", "确认付款"),
                capturedAtMillis = baseTime + 4_000L
            )
        )!!
        tracker.recordCheckout(stage1Huabei)
        val afterHuabei = tracker.peekActivePendingCheckout(baseTime + 4_500L)!!
        assertEquals("花呗", afterHuabei.paymentMethodText)
        assertEquals("", afterHuabei.accountHint)

        // 最后切换为网商银行（叠加立减后 ¥17.30）
        val stage1Mybank = parser.extractPendingCheckout(
            ScreenSnapshot(
                packageName = "com.eg.android.AlipayGphone",
                className = "MspContainerActivity",
                texts = listOf("付款详情", "¥17.30", "淘宝闪购", "支付优惠", "-¥1.50", "付款方式", "网商银行", "确认付款"),
                capturedAtMillis = baseTime + 6_000L
            )
        )!!
        tracker.recordCheckout(stage1Mybank)

        // 模拟收银台弹出期间底层淘宝刷新了一次 Stage 0，绝不覆盖或降级 Stage 1
        tracker.recordCheckout(stage0.copy(capturedAtMillis = baseTime + 6_500L))
        val activeBeforeConfirm = tracker.peekActivePendingCheckout(baseTime + 7_000L)!!
        assertTrue(activeBeforeConfirm.isCashierStage)
        assertEquals("网商银行", activeBeforeConfirm.paymentMethodText)
        assertEquals(MoneyAmount.parseToMicros("17.30"), activeBeforeConfirm.origAmountMicros)

        val confirmSnapshot = ScreenSnapshot(
            packageName = "com.taobao.taobao",
            className = "PaySuccessActivity",
            texts = listOf("支付成功", "查看订单", "返回首页"),
            capturedAtMillis = baseTime + 9_000L
        )
        val signal = parser.detectCheckoutCompletionSignal(confirmSnapshot)!!
        val outcome = tracker.consumeIfConfirmed(confirmSnapshot, signal, parser, aliases, accounts, categories)
        assertNotNull(outcome)
        val draft = outcome!!.draft!!
        assertEquals(MoneyAmount.parseToMicros("17.30"), draft.origAmountMicros)
        assertEquals("霸王茶姬(光环店)", draft.merchant)
        assertEquals("外卖", draft.categoryName)
        assertEquals(mybankAccountId, draft.accountId)
        assertEquals("", draft.accountHint)

        // 若账户列表中未单独创建「花呗」账户，淘宝内嵌 MSP 选择花呗应回落到「支付宝」而非「微信零钱」
        val accountsWithoutHuabei = accounts.filterNot { it.id == huabeiAccountId }
        val aliasesWithoutHuabei = aliases.filterNot { it.accountId == huabeiAccountId }
        val embeddedTracker = CheckoutSessionTracker()
        embeddedTracker.recordCheckout(
            PendingCheckoutContext(
                sourcePackage = "com.taobao.taobao",
                merchant = "书亦烧仙草(蔡家店)",
                origAmountMicros = MoneyAmount.parseToMicros("12.66")!!,
                paymentMethodText = "花呗",
                isTakeout = true,
                capturedAtMillis = baseTime + 20_000L,
                isCashierStage = true
            )
        )
        val embeddedConfirm = confirmSnapshot.copy(capturedAtMillis = baseTime + 22_000L)
        val embeddedOutcome = embeddedTracker.consumeIfConfirmed(
            embeddedConfirm,
            signal,
            parser,
            aliasesWithoutHuabei,
            accountsWithoutHuabei,
            categories
        )
        assertNotNull(embeddedOutcome)
        assertEquals(alipayAccountId, embeddedOutcome!!.draft!!.accountId)
    }

    @Test
    fun zeroesMisfiresOnExplicitCancelDirectWeChatCaptureAndTtlExpiry() {
        val baseTime = 1_732_830_000_000L

        // 1. 取消支付退回「待付款 / 去支付」页 -> 立刻清空待确认上下文，0 误触
        run {
            val tracker = CheckoutSessionTracker()
            tracker.recordCheckout(
                PendingCheckoutContext(
                    sourcePackage = "com.eg.android.AlipayGphone",
                    merchant = "书亦烧仙草(蔡家店)",
                    origAmountMicros = MoneyAmount.parseToMicros("12.66")!!,
                    paymentMethodText = "花呗",
                    isTakeout = true,
                    capturedAtMillis = baseTime,
                    isCashierStage = true
                )
            )
            assertTrue(tracker.hasActivePendingCheckout(baseTime + 2_000L))

            val unpaidSnapshot = ScreenSnapshot(
                packageName = "com.taobao.taobao",
                className = "OrderDetailActivity",
                texts = listOf("订单详情", "待付款", "等待买家付款", "书亦烧仙草(蔡家店)", "去支付"),
                capturedAtMillis = baseTime + 3_000L
            )
            assertTrue(parser.isExplicitUnpaidOrFailedScreen(unpaidSnapshot))
            tracker.markExplicitCancelOrUnpaid(unpaidSnapshot.capturedAtMillis)
            assertFalse(tracker.hasActivePendingCheckout(baseTime + 3_500L))

            // 用户随后浏览另一笔历史已接单订单页 -> 绝不误触发
            val otherOrderSnapshot = ScreenSnapshot(
                packageName = "com.taobao.taobao",
                className = "OrderDetailActivity",
                texts = listOf("淘宝闪购", "商家已接单", "预计21:40送达", "联系商家"),
                capturedAtMillis = baseTime + 5_000L
            )
            val signal = parser.detectCheckoutCompletionSignal(otherOrderSnapshot)!!
            assertNull(tracker.consumeIfConfirmed(otherOrderSnapshot, signal, parser, aliases, accounts, categories))
        }

        // 2. 微信支付成功页直接命中单帧解析后清空状态，跳回淘宝 0 重复触发
        run {
            val tracker = CheckoutSessionTracker()
            tracker.recordCheckout(
                PendingCheckoutContext(
                    sourcePackage = "com.taobao.taobao",
                    merchant = "书亦烧仙草(蔡家店)",
                    origAmountMicros = MoneyAmount.parseToMicros("12.66")!!,
                    isTakeout = true,
                    capturedAtMillis = baseTime,
                    isCashierStage = false
                )
            )
            tracker.recordCheckout(
                PendingCheckoutContext(
                    sourcePackage = "com.tencent.mm",
                    merchant = "淘宝闪购",
                    origAmountMicros = MoneyAmount.parseToMicros("12.66")!!,
                    paymentMethodText = "零钱",
                    capturedAtMillis = baseTime + 2_000L,
                    isCashierStage = true
                )
            )
            // 微信支付成功页单帧解析成功 -> 清理状态机
            tracker.clearOnDirectCapture(MoneyAmount.parseToMicros("12.66")!!)
            assertFalse(tracker.hasActivePendingCheckout(baseTime + 3_000L))

            val backToTaobao = ScreenSnapshot(
                packageName = "com.taobao.taobao",
                className = "PaySuccessActivity",
                texts = listOf("支付成功", "查看订单", "返回首页"),
                capturedAtMillis = baseTime + 4_000L
            )
            val signal = parser.detectCheckoutCompletionSignal(backToTaobao)!!
            assertNull(tracker.consumeIfConfirmed(backToTaobao, signal, parser, aliases, accounts, categories))
        }

        // 3. Stage 1 超过 35 秒 TTL 自动失效
        run {
            val tracker = CheckoutSessionTracker()
            tracker.recordCheckout(
                PendingCheckoutContext(
                    sourcePackage = "com.eg.android.AlipayGphone",
                    merchant = "书亦烧仙草(蔡家店)",
                    origAmountMicros = MoneyAmount.parseToMicros("12.66")!!,
                    paymentMethodText = "花呗",
                    isTakeout = true,
                    capturedAtMillis = baseTime,
                    isCashierStage = true
                )
            )
            assertFalse(tracker.hasActivePendingCheckout(baseTime + 35_001L))
            val lateSnapshot = ScreenSnapshot(
                packageName = "com.taobao.taobao",
                className = "PaySuccessActivity",
                texts = listOf("支付成功", "查看订单", "返回首页"),
                capturedAtMillis = baseTime + 36_000L
            )
            val signal = parser.detectCheckoutCompletionSignal(lateSnapshot)!!
            assertNull(tracker.consumeIfConfirmed(lateSnapshot, signal, parser, aliases, accounts, categories))
        }

        // 4. 无 Stage 1 的免密 Stage 0：20 秒内且强信号放行；弱信号或超 20 秒均拦截
        run {
            val tracker = CheckoutSessionTracker()
            val stage0 = PendingCheckoutContext(
                sourcePackage = "com.taobao.taobao",
                merchant = "书亦烧仙草(蔡家店)",
                goodsSummary = "烧仙草大杯 x1",
                origAmountMicros = MoneyAmount.parseToMicros("12.66")!!,
                isTakeout = true,
                capturedAtMillis = baseTime,
                isCashierStage = false
            )
            tracker.recordCheckout(stage0)

            // 弱信号（仅「查看订单 + 返回首页」，无明确支付成功/接单文案）拦截
            val weakSnapshot = ScreenSnapshot(
                packageName = "com.taobao.taobao",
                className = "PaySuccessActivity",
                texts = listOf("淘宝闪购", "查看订单", "返回首页"),
                capturedAtMillis = baseTime + 5_000L
            )
            val weakSignal = parser.detectCheckoutCompletionSignal(weakSnapshot)!!
            assertFalse(weakSignal.isStrongPaidSignal)
            assertNull(tracker.consumeIfConfirmed(weakSnapshot, weakSignal, parser, aliases, accounts, categories))

            // 超过 20 秒但未到 60 秒：不满足免密直接确认窗口（20s）
            val lateStrongSnapshot = ScreenSnapshot(
                packageName = "com.taobao.taobao",
                className = "PaySuccessActivity",
                texts = listOf("支付成功", "查看订单", "返回首页"),
                capturedAtMillis = baseTime + 25_000L
            )
            val strongSignal = parser.detectCheckoutCompletionSignal(lateStrongSnapshot)!!
            assertNull(tracker.consumeIfConfirmed(lateStrongSnapshot, strongSignal, parser, aliases, accounts, categories))

            // 20 秒内 + 明确支付成功强信号 -> 正常免密确认
            val timelyStrongSnapshot = lateStrongSnapshot.copy(capturedAtMillis = baseTime + 8_000L)
            val outcome = tracker.consumeIfConfirmed(timelyStrongSnapshot, strongSignal, parser, aliases, accounts, categories)
            assertNotNull(outcome)
            assertEquals("书亦烧仙草(蔡家店)", outcome!!.draft!!.merchant)
            assertEquals("外卖", outcome.draft!!.categoryName)
            assertEquals(MoneyAmount.parseToMicros("12.66"), outcome.draft!!.origAmountMicros)
        }
    }

    @Test
    fun enforcesSingleConsumptionUnderConcurrentConfirmations() {
        val baseTime = 1_732_840_000_000L
        val tracker = CheckoutSessionTracker()
        tracker.recordCheckout(
            PendingCheckoutContext(
                sourcePackage = "com.eg.android.AlipayGphone",
                merchant = "书亦烧仙草(蔡家店)",
                origAmountMicros = MoneyAmount.parseToMicros("12.66")!!,
                paymentMethodText = "网商银行",
                isTakeout = true,
                capturedAtMillis = baseTime,
                isCashierStage = true
            )
        )
        val confirmSnapshot = ScreenSnapshot(
            packageName = "com.taobao.taobao",
            className = "PaySuccessActivity",
            texts = listOf("支付成功", "查看订单", "返回首页"),
            capturedAtMillis = baseTime + 2_000L
        )
        val signal = parser.detectCheckoutCompletionSignal(confirmSnapshot)!!

        val workers = 8
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(workers)
        val successCount = AtomicInteger(0)
        val pool = Executors.newFixedThreadPool(workers)
        try {
            repeat(workers) {
                pool.submit {
                    try {
                        startLatch.await()
                        val res = tracker.consumeIfConfirmed(confirmSnapshot, signal, parser, aliases, accounts, categories)
                        if (res != null) successCount.incrementAndGet()
                    } finally {
                        doneLatch.countDown()
                    }
                }
            }
            startLatch.countDown()
            doneLatch.await()
        } finally {
            pool.shutdownNow()
        }
        assertEquals(1, successCount.get())
        assertFalse(tracker.hasActivePendingCheckout(baseTime + 3_000L))
    }
}

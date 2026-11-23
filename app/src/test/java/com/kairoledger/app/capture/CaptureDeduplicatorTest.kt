package com.kairoledger.app.capture

import com.kairoledger.app.capture.screen.ScreenBillParser
import com.kairoledger.app.capture.screen.ScreenPageKind
import com.kairoledger.app.capture.screen.ScreenSnapshot
import com.kairoledger.app.core.contract.CaptureInput
import com.kairoledger.app.core.model.MoneyAmount
import com.kairoledger.app.data.db.AliasMatchType
import com.kairoledger.app.data.db.CaptureChannel
import com.kairoledger.app.data.db.DefaultSeedData
import com.kairoledger.app.data.db.ParseStatus
import com.kairoledger.app.data.db.TagTextCodec
import com.kairoledger.app.data.db.TransactionStatus
import com.kairoledger.app.data.prefs.AppSettings
import com.kairoledger.app.testutil.TestHttpServer
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets

class CaptureDeduplicatorTest {

    private lateinit var accountDao: FakeAccountDao
    private lateinit var aliasDao: FakeAccountAliasDao
    private lateinit var categoryDao: FakeCategoryDao
    private lateinit var ruleDao: FakeParseRuleDao
    private lateinit var transactionDao: FakeTransactionDao
    private lateinit var rawLogDao: FakeRawCaptureLogDao
    private lateinit var ledgerService: FakeLedgerService

    private var promptTriggerCount = 0
    private var currentSettings = AppSettings(floatingConfirmEnabled = true)

    @Before
    fun setup() = runTest {
        accountDao = FakeAccountDao()
        aliasDao = FakeAccountAliasDao()
        categoryDao = FakeCategoryDao()
        ruleDao = FakeParseRuleDao()
        transactionDao = FakeTransactionDao()
        rawLogDao = FakeRawCaptureLogDao()
        ledgerService = FakeLedgerService(accountDao, transactionDao, categoryDao)
        promptTriggerCount = 0
        currentSettings = AppSettings(floatingConfirmEnabled = true)

        CaptureTestFixtures.seedFixtures(accountDao, aliasDao, categoryDao, ruleDao)
    }

    private fun buildOrchestrator(llmClient: OkHttpClient = OkHttpClient()): CaptureOrchestrator {
        return CaptureOrchestrator(
            rawCaptureLogDao = rawLogDao,
            accountAliasDao = aliasDao,
            parseRuleDao = ruleDao,
            accountDao = accountDao,
            categoryDao = categoryDao,
            transactionDao = transactionDao,
            ledgerService = ledgerService,
            localRuleParser = LocalRuleParser(),
            llmBillParser = LlmBillParser(llmClient),
            deduplicator = CaptureDeduplicator(),
            settingsProvider = { currentSettings },
            floatingPromptLauncher = { _, _, _, _ -> promptTriggerCount++ }
        )
    }

    @Test
    fun mergesWeChatNotificationThenBankSmsWithin60SecondsAndMovesBalanceToRealCard() = runTest {
        val orchestrator = buildOrchestrator()
        val baseTime = 1_700_000_000_000L

        // 1. 先捕获微信通知：有商户“星巴克”，无银行卡尾号
        val r1 = orchestrator.handleIncomingCapture(
            CaptureInput(
                channel = CaptureChannel.NOTIFICATION,
                sourcePackage = "com.tencent.mm",
                title = "微信支付",
                content = "微信支付：向星巴克付款32.00元",
                timestamp = baseTime
            )
        )
        assertEquals(ParseStatus.MATCHED_LOCAL, r1.status)
        assertFalse(r1.mergedIntoExisting)
        assertNotNull(r1.transactionId)
        assertEquals(1, promptTriggerCount)

        // 2. 12秒后收到银行扣款短信：有卡尾号 6688，无商户
        val r2 = orchestrator.handleIncomingCapture(
            CaptureInput(
                channel = CaptureChannel.SMS,
                sourcePackage = "95555",
                title = "短信 (95555)",
                content = "【招商银行】您尾号6688信用卡消费人民币32.00元",
                timestamp = baseTime + 12_000L
            )
        )
        assertEquals(ParseStatus.MERGED_DEDUP, r2.status)
        assertTrue(r2.mergedIntoExisting)
        assertEquals(r1.transactionId, r2.transactionId)
        // 去重合并不会重复弹出悬浮确认框
        assertEquals(1, promptTriggerCount)

        val allTxs = transactionDao.getAll()
        assertEquals(1, allTxs.size)
        val mergedTx = allTxs.first()
        val cmbCard = accountDao.getByName("招商信用卡6688")!!
        val wechatWallet = accountDao.getByName("微信零钱")!!

        assertEquals(cmbCard.id, mergedTx.accountId)
        assertEquals("星巴克", mergedTx.merchant)
        assertEquals("餐饮美食", categoryDao.getById(mergedTx.categoryId!!)!!.name)
        assertEquals(TransactionStatus.PENDING, mergedTx.status)
        // 原先扣在微信零钱的 32 元已被原子回滚，并正确记入招商信用卡 6688
        assertEquals(0L, wechatWallet.balanceMicros)
        assertEquals(-320_000L, cmbCard.balanceMicros)

        val logs = rawLogDao.getAll()
        assertEquals(2, logs.size)
        val dedupLog = logs.first { it.id == r2.logId }
        assertEquals(ParseStatus.MERGED_DEDUP, dedupLog.parseStatus)
        assertEquals(mergedTx.id, dedupLog.linkedTransactionId)
    }

    @Test
    fun mergesBankSmsFirstThenWeChatNotificationAndEnrichesMerchantAndCategory() = runTest {
        val orchestrator = buildOrchestrator()
        val baseTime = 1_700_100_000_000L

        val r1 = orchestrator.handleIncomingCapture(
            CaptureInput(
                channel = CaptureChannel.SMS,
                sourcePackage = "95555",
                title = "短信 (95555)",
                content = "【招商银行】您尾号6688信用卡消费人民币45.80元",
                timestamp = baseTime
            )
        )
        assertEquals(ParseStatus.MATCHED_LOCAL, r1.status)

        val r2 = orchestrator.handleIncomingCapture(
            CaptureInput(
                channel = CaptureChannel.NOTIFICATION,
                sourcePackage = "com.tencent.mm",
                title = "微信支付",
                content = "微信支付：向美团外卖付款45.80元",
                timestamp = baseTime + 8_000L
            )
        )
        assertEquals(ParseStatus.MERGED_DEDUP, r2.status)
        assertTrue(r2.mergedIntoExisting)
        assertEquals(r1.transactionId, r2.transactionId)

        val tx = transactionDao.getById(r1.transactionId!!)!!
        val cmbCard = accountDao.getByName("招商信用卡6688")!!
        assertEquals(cmbCard.id, tx.accountId)
        assertEquals("美团外卖", tx.merchant)
        assertEquals("餐饮美食", categoryDao.getById(tx.categoryId!!)!!.name)
    }

    @Test
    fun mergesForeignCurrencyNotificationWithCnyBankSettlementSmsAndReverseComputesRate() = runTest {
        val orchestrator = buildOrchestrator()
        val baseTime = 1_700_200_000_000L

        // 1. 先捕获海外消费通知 $15.00 USD（参考汇率约 ¥108.60）
        val r1 = orchestrator.handleIncomingCapture(
            CaptureInput(
                channel = CaptureChannel.NOTIFICATION,
                sourcePackage = "com.apple.wallet",
                title = "Apple Pay",
                content = "You spent $15.00 at OpenAI",
                timestamp = baseTime
            )
        )
        assertEquals(ParseStatus.MATCHED_LOCAL, r1.status)

        // 2. 15秒后收到国内发卡行人民币入账/扣款短信 ¥109.20（含汇损，尾号6688）
        val r2 = orchestrator.handleIncomingCapture(
            CaptureInput(
                channel = CaptureChannel.SMS,
                sourcePackage = "95555",
                title = "短信 (95555)",
                content = "【招商银行】您尾号6688信用卡消费人民币109.20元",
                timestamp = baseTime + 15_000L
            )
        )
        assertEquals(ParseStatus.MERGED_DEDUP, r2.status)
        assertTrue(r2.mergedIntoExisting)
        assertEquals(r1.transactionId, r2.transactionId)

        val mergedTx = transactionDao.getById(r1.transactionId!!)!!
        val cmbCard = accountDao.getByName("招商信用卡6688")!!
        assertEquals(cmbCard.id, mergedTx.accountId)
        assertEquals("USD", mergedTx.origCurrency)
        assertEquals(MoneyAmount.parseToMicros("15.00"), mergedTx.origAmountMicros)
        // 真实人民币扣款额 109.20 与反算成交汇率 7.28 精准写入
        assertEquals(MoneyAmount.parseToMicros("109.20"), mergedTx.accountAmountMicros)
        assertEquals(7_280_000L, mergedTx.rateToAccountMicros)
        assertEquals("OpenAI", mergedTx.merchant)
        assertEquals("数字订阅", categoryDao.getById(mergedTx.categoryId!!)!!.name)
        assertEquals(0L, accountDao.getByName("美元账户 (USD)")!!.balanceMicros)
        assertEquals(-MoneyAmount.parseToMicros("109.20")!!, cmbCard.balanceMicros)
    }

    @Test
    fun doesNotMergeOutside60SecondWindowOrDifferentMerchants() = runTest {
        val orchestrator = buildOrchestrator()
        val baseTime = 1_700_300_000_000L

        orchestrator.handleIncomingCapture(
            CaptureInput(
                channel = CaptureChannel.NOTIFICATION,
                sourcePackage = "com.tencent.mm",
                title = "微信支付",
                content = "微信支付：向星巴克付款32.00元",
                timestamp = baseTime
            )
        )

        // 1. 60 秒内但跨渠道且明确商户不同（星巴克 vs 瑞幸咖啡），不应误合并
        val diffMerchant = orchestrator.handleIncomingCapture(
            CaptureInput(
                channel = CaptureChannel.NOTIFICATION,
                sourcePackage = "com.eg.android.AlipayGphone",
                title = "支付宝",
                content = "你向瑞幸咖啡付款32.00元",
                timestamp = baseTime + 10_000L
            )
        )
        assertEquals(ParseStatus.MATCHED_LOCAL, diffMerchant.status)
        assertFalse(diffMerchant.mergedIntoExisting)

        // 2. 超出 60 秒窗口（+120s，距前两笔均超过 60s）不应合并
        val outsideWindow = orchestrator.handleIncomingCapture(
            CaptureInput(
                channel = CaptureChannel.SMS,
                sourcePackage = "95555",
                title = "短信 (95555)",
                content = "【招商银行】您尾号6688信用卡消费人民币32.00元",
                timestamp = baseTime + 120_000L
            )
        )
        assertEquals(ParseStatus.MATCHED_LOCAL, outsideWindow.status)
        assertFalse(outsideWindow.mergedIntoExisting)

        assertEquals(3, transactionDao.getAll().size)
    }

    @Test
    fun testParseTextDoesNotPersistLogsAndLlmFallbackTriggersWhenLocalMisses() = runTest {
        val server = TestHttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            val body = """
                {
                  "choices": [{
                    "message": {
                      "role": "assistant",
                      "content": "```json\n{\"isTransaction\":true,\"type\":\"EXPENSE\",\"amount\":\"88.00\",\"currency\":\"CNY\",\"merchant\":\"盒马鲜生\",\"categoryName\":\"餐饮美食\",\"note\":\"生鲜采购\"}\n```"
                    }
                  }]
                }
            """.trimIndent().toByteArray(StandardCharsets.UTF_8)
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()

        try {
            currentSettings = AppSettings(
                floatingConfirmEnabled = false,
                llmEnabled = true,
                llmBaseUrl = "http://127.0.0.1:${server.address.port}/v1/",
                llmApiKey = "sk-demo"
            )
            val orchestrator = buildOrchestrator()

            // 1. testParseText 不写数据库日志与交易表
            val sandboxRes = orchestrator.testParseText(
                title = "微信支付",
                content = "微信支付：向星巴克付款32.00元",
                sourcePackage = "com.tencent.mm",
                allowLlm = false
            )
            assertTrue(sandboxRes.isSuccess)
            assertEquals("星巴克", sandboxRes.getOrNull()?.merchant)
            assertEquals(0, rawLogDao.getAll().size)
            assertEquals(0, transactionDao.getAll().size)

            // 2. 无明确本地规则与动词的非标准报文触发 AI 兜底
            val aiResult = orchestrator.handleIncomingCapture(
                CaptureInput(
                    channel = CaptureChannel.SHARE,
                    sourcePackage = "com.hema.app",
                    title = "订单完成",
                    content = "盒马门店自提订单总额 88.00 结算完成"
                ),
                showFloatingPrompt = false
            )
            assertEquals(ParseStatus.MATCHED_AI, aiResult.status)
            assertNotNull(aiResult.transactionId)
            assertEquals("盒马鲜生", transactionDao.getById(aiResult.transactionId!!)!!.merchant)
            assertEquals(TransactionStatus.CONFIRMED, transactionDao.getById(aiResult.transactionId!!)!!.status)

            // 3. 沉淀日志为账户指纹
            val cmbCard = accountDao.getByName("招商信用卡6688")!!
            val promoteRes = orchestrator.promoteLogToAliasOrRule(
                logId = aiResult.logId,
                accountId = cmbCard.id,
                matchPattern = "盒马",
                matchType = AliasMatchType.KEYWORD
            )
            assertTrue(promoteRes.isSuccess)
            assertTrue(aliasDao.getEnabled().any { it.accountId == cmbCard.id && it.pattern == "盒马" })
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun mergesWeChatPinduoduoShellNotificationWithOrderDetailWithin10MinutesEnrichingMerchantNoteCategoryAndTags() = runTest {
        // 先以旧泛化分类记录微信支付通知（拼多多平台商户 -> 日用购物）
        val orchestrator = buildOrchestrator()
        val baseTime = 1_700_500_000_000L

        val wechatResult = orchestrator.handleIncomingCapture(
            CaptureInput(
                channel = CaptureChannel.NOTIFICATION,
                sourcePackage = "com.tencent.mm",
                title = "微信支付",
                content = "微信支付：向拼多多平台商户付款99.00元",
                timestamp = baseTime
            )
        )
        assertEquals(ParseStatus.MATCHED_LOCAL, wechatResult.status)
        assertNotNull(wechatResult.transactionId)
        assertEquals(1, promptTriggerCount)

        val initialTx = transactionDao.getById(wechatResult.transactionId!!)!!
        assertEquals("拼多多平台商户", initialTx.merchant)
        assertEquals("日用购物", categoryDao.getById(initialTx.categoryId!!)!!.name)
        assertEquals(listOf("拼多多", "网购"), TagTextCodec.parse(initialTx.tags))

        // 补齐完整二级分类库（含 家居日用 / 网购百货 等），模拟 8 分钟后打开拼多多订单详情页
        DefaultSeedData.ensurePresetCategories(categoryDao)
        val screenParser = ScreenBillParser()
        val orderDetailSnapshot = ScreenSnapshot(
            packageName = "com.xunmeng.pinduoduo",
            className = "com.xunmeng.pinduoduo.activity.NewPageActivity",
            texts = listOf(
                "订单详情",
                "买家已付款",
                "南极人家纺旗舰店 >",
                "纯棉四件套全棉床上用品被套床单",
                "2.0m床-水墨灰 x1",
                "商品总价 ¥129.00",
                "百亿补贴 -¥30.00",
                "实付款 ¥99.00",
                "订单编号：241123-123456789012345"
            ),
            capturedAtMillis = baseTime + 480_000L
        )
        val outcome = screenParser.parse(
            snapshot = orderDetailSnapshot,
            aliases = aliasDao.getEnabled(),
            accounts = accountDao.getAll(),
            categories = categoryDao.getAll()
        )
        assertEquals(ScreenPageKind.ORDER_DETAIL, outcome.pageKind)

        val mergeResult = orchestrator.handleScreenCapture(orderDetailSnapshot, outcome)
        assertEquals(ParseStatus.MERGED_DEDUP, mergeResult.status)
        assertTrue(mergeResult.mergedIntoExisting)
        assertEquals(wechatResult.transactionId, mergeResult.transactionId)
        assertEquals(1, promptTriggerCount)
        assertEquals(1, transactionDao.getAll().size)

        val enrichedTx = transactionDao.getById(wechatResult.transactionId!!)!!
        assertEquals("南极人家纺旗舰店", enrichedTx.merchant)
        assertEquals("家居日用", categoryDao.getById(enrichedTx.categoryId!!)!!.name)
        assertTrue("Expected note to retain product details, got: ${enrichedTx.note}", enrichedTx.note.contains("纯棉四件套"))
        assertTrue("Expected note to retain order no, got: ${enrichedTx.note}", enrichedTx.note.contains("241123-123456789012345"))
        assertEquals(listOf("拼多多", "网购"), TagTextCodec.parse(enrichedTx.tags))

        // 超过 10 分钟窗口（+12 分钟）的另一笔同金额拼多多订单详情不应误融合到已有交易
        val outsideOrderSnapshot = orderDetailSnapshot.copy(
            texts = orderDetailSnapshot.texts.map {
                if (it.startsWith("订单编号")) "订单编号：241123-999999999999999" else it
            },
            capturedAtMillis = baseTime + 720_000L
        )
        val outsideOutcome = screenParser.parse(
            snapshot = outsideOrderSnapshot,
            aliases = aliasDao.getEnabled(),
            accounts = accountDao.getAll(),
            categories = categoryDao.getAll()
        )
        val outsideResult = orchestrator.handleScreenCapture(outsideOrderSnapshot, outsideOutcome)
        assertEquals(ParseStatus.MATCHED_LOCAL, outsideResult.status)
        assertFalse(outsideResult.mergedIntoExisting)
        assertEquals(2, transactionDao.getAll().size)

        // 当关闭订单详情识别开关时，CaptureOrchestrator 返回 IGNORED
        currentSettings = currentSettings.copy(accessibilityOrderDetailEnabled = false)
        val disabledResult = orchestrator.handleScreenCapture(outsideOrderSnapshot, outsideOutcome)
        assertEquals(ParseStatus.IGNORED, disabledResult.status)
        assertEquals(2, transactionDao.getAll().size)
    }
}

package com.yubrae.app.capture

import com.yubrae.app.capture.rules.JsRuleBundleLoader
import com.yubrae.app.capture.rules.JsRuleSource
import com.yubrae.app.capture.rules.SandboxedJsRuleEngine
import com.yubrae.app.capture.screen.ScreenBillParser
import com.yubrae.app.capture.screen.ScreenSnapshot
import com.yubrae.app.core.contract.CaptureInput
import com.yubrae.app.core.model.MoneyAmount
import com.yubrae.app.data.db.AccountAliasEntity
import com.yubrae.app.data.db.AccountEntity
import com.yubrae.app.data.db.AccountGroup
import com.yubrae.app.data.db.AliasMatchType
import com.yubrae.app.data.db.CaptureChannel
import com.yubrae.app.data.db.DefaultSeedData
import com.yubrae.app.data.db.ParseRuleEntity
import com.yubrae.app.data.db.ParseStatus
import com.yubrae.app.data.db.TransactionType
import com.yubrae.app.data.prefs.AppSettings
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class CaptureOrchestratorJsRulesTest {

    private lateinit var accountDao: FakeAccountDao
    private lateinit var aliasDao: FakeAccountAliasDao
    private lateinit var categoryDao: FakeCategoryDao
    private lateinit var ruleDao: FakeParseRuleDao
    private lateinit var transactionDao: FakeTransactionDao
    private lateinit var rawLogDao: FakeRawCaptureLogDao
    private lateinit var ledgerService: FakeLedgerService
    private lateinit var jsRuleEngine: SandboxedJsRuleEngine
    private var currentSettings = AppSettings(floatingConfirmEnabled = false)

    @Before
    fun setup() = runTest {
        accountDao = FakeAccountDao()
        aliasDao = FakeAccountAliasDao()
        categoryDao = FakeCategoryDao()
        ruleDao = FakeParseRuleDao()
        transactionDao = FakeTransactionDao()
        rawLogDao = FakeRawCaptureLogDao()
        ledgerService = FakeLedgerService(accountDao, transactionDao, categoryDao)
        currentSettings = AppSettings(
            floatingConfirmEnabled = false,
            jsRulesEnabled = true,
            jsOcrFallbackEnabled = true,
            jsCategoryFallbackEnabled = true
        )

        CaptureTestFixtures.seedFixtures(accountDao, aliasDao, categoryDao, ruleDao)
        DefaultSeedData.ensureAccountsAndTransitCard(accountDao, aliasDao)
        DefaultSeedData.ensurePresetCategories(categoryDao)

        val cmbDebitId = accountDao.insert(
            AccountEntity(name = "招商银行储蓄卡", groupType = AccountGroup.DEBIT, currencyCode = "CNY")
        )
        aliasDao.insert(
            AccountAliasEntity(
                accountId = cmbDebitId,
                matchType = AliasMatchType.CARD_LAST4,
                pattern = "1234",
                priority = 60
            )
        )

        val assetZip = locateProjectFile("app/src/main/assets/rules/builtin_rules.zip")
        val bundle = JsRuleBundleLoader.loadFromZipFile(
            file = assetZip,
            source = JsRuleSource.BUILTIN,
            version = "2026.11.26.1"
        ).getOrThrow()
        jsRuleEngine = SandboxedJsRuleEngine().apply { updateBundle(bundle) }
    }

    private fun buildOrchestrator(): CaptureOrchestrator {
        return CaptureOrchestrator(
            rawCaptureLogDao = rawLogDao,
            accountAliasDao = aliasDao,
            parseRuleDao = ruleDao,
            accountDao = accountDao,
            categoryDao = categoryDao,
            transactionDao = transactionDao,
            ledgerService = ledgerService,
            localRuleParser = LocalRuleParser(),
            llmBillParser = LlmBillParser(OkHttpClient()),
            deduplicator = CaptureDeduplicator(),
            settingsProvider = { currentSettings },
            jsRuleEngine = jsRuleEngine
        )
    }

    @Test
    fun enforcesFourTierPriorityCustomRegexOverJsOverBuiltInRegexOverHeuristic() = runTest {
        val orchestrator = buildOrchestrator()
        val now = 1_773_550_000_000L

        val cmbSms = CaptureInput(
            channel = CaptureChannel.SMS,
            sourcePackage = "95555",
            title = "短信 (95555)",
            content = "【招商银行】您账户1234于03月15日12:30在星巴克支付35.00元",
            timestamp = now
        )
        // 为该短信补充一条匹配的内置规则，以验证优先级顺序
        val builtInSmsRuleId = ruleDao.insert(
            ParseRuleEntity(
                name = "内置招行消费补充正则",
                sourcePackage = "*",
                channelType = "SMS",
                regexPattern = "您账户(\\d{4})于.*?在(.*?)支付([0-9]+\\.[0-9]{2})元",
                transactionType = TransactionType.EXPENSE,
                amountGroup = 3,
                merchantGroup = 2,
                accountHintGroup = 1,
                defaultCurrency = "CNY",
                defaultCategoryName = "餐饮美食",
                priority = 95,
                isBuiltIn = true
            )
        )

        // 1. 用户自定义正则（即使 priority 更低）优先于 JS 规则与内置正则
        val customRuleId = ruleDao.insert(
            ParseRuleEntity(
                name = "用户自定义招行专享规则",
                sourcePackage = "*",
                channelType = "SMS",
                regexPattern = "您账户(\\d{4})于.*?在(.*?)支付([0-9]+\\.[0-9]{2})元",
                transactionType = TransactionType.EXPENSE,
                amountGroup = 3,
                merchantGroup = 2,
                accountHintGroup = 1,
                defaultCurrency = "CNY",
                defaultCategoryName = "咖啡茶饮",
                priority = 10,
                isBuiltIn = false
            )
        )
        val rCustom = orchestrator.handleIncomingCapture(cmbSms, showFloatingPrompt = false)
        assertEquals(ParseStatus.MATCHED_LOCAL, rCustom.status)
        assertEquals("LOCAL_RULE:用户自定义招行专享规则", rCustom.draft?.parserUsed)

        // 2. 删除用户自定义正则后，JS 规则优先于内置正则
        ruleDao.deleteById(customRuleId)
        val rJs = orchestrator.handleIncomingCapture(
            cmbSms.copy(timestamp = now + 120_000L),
            showFloatingPrompt = false
        )
        assertEquals(ParseStatus.MATCHED_LOCAL, rJs.status)
        assertTrue("Expected JS rule to win over built-in regex, got: ${rJs.draft?.parserUsed}", rJs.draft?.parserUsed?.startsWith("BUILTIN_JS:") == true)

        // 3. 关闭 JS 规则开关后，回退到内置正则
        currentSettings = currentSettings.copy(jsRulesEnabled = false)
        val rBuiltIn = orchestrator.handleIncomingCapture(
            cmbSms.copy(timestamp = now + 240_000L),
            showFloatingPrompt = false
        )
        assertEquals(ParseStatus.MATCHED_LOCAL, rBuiltIn.status)
        assertEquals("LOCAL_RULE:内置招行消费补充正则", rBuiltIn.draft?.parserUsed)

        // 4. 清除全部显式规则且 JS 关闭（或 JS 未命中）时，降级到本地启发式解析
        ruleDao.deleteById(builtInSmsRuleId)
        val heuristicInput = CaptureInput(
            channel = CaptureChannel.SHARE,
            sourcePackage = "share",
            title = "消费提醒",
            content = "刚刚在路口便利店扣款12.50元，余额80.00元",
            timestamp = now + 360_000L
        )
        val rHeuristic = orchestrator.handleIncomingCapture(heuristicInput, showFloatingPrompt = false)
        assertEquals(ParseStatus.MATCHED_LOCAL, rHeuristic.status)
        assertEquals("LOCAL_HEURISTIC", rHeuristic.draft?.parserUsed)
    }

    @Test
    fun persistsDualAccountTransferAndDegradesGracefullyWhenTargetMissingOrConflicting() = runTest {
        val orchestrator = buildOrchestrator()
        val now = 1_773_560_000_000L
        val cmbDebit = accountDao.getByName("招商银行储蓄卡")!!
        val cmbCredit = accountDao.getByName("招商信用卡6688")!!

        // 1. 双账户解析齐全：储蓄卡 1234 -> 信用卡 6688 正常落库为 TRANSFER
        val validTransferSms = CaptureInput(
            channel = CaptureChannel.SMS,
            sourcePackage = "95555",
            title = "短信 (95555)",
            content = "【招商银行】您账户1234于03月15日12:30向尾号为6688的信用卡还款人民币2000.00元，余额8000.00元。",
            timestamp = now
        )
        val validRes = orchestrator.handleIncomingCapture(validTransferSms, showFloatingPrompt = false)
        assertEquals(ParseStatus.MATCHED_LOCAL, validRes.status)
        val validTx = transactionDao.getById(validRes.transactionId!!)!!
        assertEquals(TransactionType.TRANSFER, validTx.type)
        assertEquals(cmbDebit.id, validTx.accountId)
        assertEquals(cmbCredit.id, validTx.toAccountId)
        assertEquals(MoneyAmount.parseToMicros("2000.00"), validTx.origAmountMicros)

        // 2. 目标账户缺失（不存在尾号 9999 与光大银行账户）-> 优雅降级为 EXPENSE 且附注 [转入:...]，不丢单
        val missingTargetSms = CaptureInput(
            channel = CaptureChannel.SMS,
            sourcePackage = "95595",
            title = "短信 (95595)",
            content = "【光大银行】光大卡尾号9999还款100.00美元，可用额度为4900.00美元，此卡本期美元账单已还清。",
            timestamp = now + 120_000L
        )
        val missingRes = orchestrator.handleIncomingCapture(missingTargetSms, showFloatingPrompt = false)
        assertEquals(ParseStatus.MATCHED_LOCAL, missingRes.status)
        val missingTx = transactionDao.getById(missingRes.transactionId!!)!!
        assertEquals(TransactionType.EXPENSE, missingTx.type)
        assertNull(missingTx.toAccountId)
        assertTrue(
            "Expected degraded note to contain [转入:光大银行信用卡(9999)], got: ${missingTx.note}",
            missingTx.note.contains("[转入:光大银行信用卡(9999)]")
        )

        // 3. 源账户与目标账户冲突（同一账户 6688 转 6688 且 accountHint 非空）-> 优雅降级为 EXPENSE 不丢单
        val conflictSms = CaptureInput(
            channel = CaptureChannel.SMS,
            sourcePackage = "95555",
            title = "短信 (95555)",
            content = "【招商银行】您账户6688于03月15日12:30向尾号为6688的信用卡还款人民币500.00元，余额1000.00元。",
            timestamp = now + 240_000L
        )
        val conflictRes = orchestrator.handleIncomingCapture(conflictSms, showFloatingPrompt = false)
        assertEquals(ParseStatus.MATCHED_LOCAL, conflictRes.status)
        val conflictTx = transactionDao.getById(conflictRes.transactionId!!)!!
        assertEquals(TransactionType.EXPENSE, conflictTx.type)
        assertNull(conflictTx.toAccountId)
        assertTrue(conflictTx.note.contains("[转入:招商银行信用卡(6688)]"))

        // 4. 目标账户已命中但仅存在单一账户（即使 accountHint 为空也无法选出第二账户）-> 优雅降级为 EXPENSE
        val singleAccountDao = FakeAccountDao()
        val onlyPocketId = singleAccountDao.insert(
            AccountEntity(name = "支付宝小荷包", groupType = AccountGroup.WALLET, currencyCode = "CNY")
        )
        val singleAliasDao = FakeAccountAliasDao().apply {
            insert(AccountAliasEntity(accountId = onlyPocketId, matchType = AliasMatchType.KEYWORD, pattern = "小荷包", priority = 80))
        }
        val singleTxDao = FakeTransactionDao()
        val singleOrchestrator = CaptureOrchestrator(
            rawCaptureLogDao = FakeRawCaptureLogDao(),
            accountAliasDao = singleAliasDao,
            parseRuleDao = FakeParseRuleDao(),
            accountDao = singleAccountDao,
            categoryDao = categoryDao,
            transactionDao = singleTxDao,
            ledgerService = FakeLedgerService(singleAccountDao, singleTxDao, categoryDao),
            settingsProvider = { currentSettings },
            jsRuleEngine = jsRuleEngine
        )
        val singleAccountNotice = CaptureInput(
            channel = CaptureChannel.NOTIFICATION,
            sourcePackage = "com.eg.android.AlipayGphone",
            title = "支付宝小荷包自动攒执行成功",
            content = "已从支付宝余额成功转入50.00元",
            timestamp = now + 360_000L
        )
        val singleRes = singleOrchestrator.handleIncomingCapture(singleAccountNotice, showFloatingPrompt = false)
        assertEquals(ParseStatus.MATCHED_LOCAL, singleRes.status)
        val singleTx = singleTxDao.getById(singleRes.transactionId!!)!!
        assertEquals(TransactionType.EXPENSE, singleTx.type)
        assertEquals(onlyPocketId, singleTx.accountId)
        assertNull(singleTx.toAccountId)
        assertTrue(singleTx.note.contains("[转入:支付宝小荷包]"))
    }

    @Test
    fun fallsBackToJsOcrWhenScreenBillParserMissesAndRespectsFallbackToggle() = runTest {
        val orchestrator = buildOrchestrator()
        val now = 1_773_570_000_000L
        val screenLines = listOf(
            "交易详情",
            "瑞幸咖啡",
            "-19.90",
            "当前状态",
            "交易成功",
            "商户全称",
            "瑞幸咖啡有限公司",
            "交易时间",
            "2026-03-15 12:30:00",
            "付款方式",
            "数字人民币钱包(0012) ￥19.90",
            "查看更多"
        )
        val snapshot = ScreenSnapshot(
            packageName = "cn.gov.pbc.dcep",
            className = "com.dcep.wallet.TradeDetailActivity",
            texts = screenLines,
            capturedAtMillis = now
        )

        // 1. 确认原生 ScreenBillParser 未覆盖该包名（draft == null 且 ignoredReason 为空）
        val nativeOutcome = ScreenBillParser().parse(snapshot)
        assertNull(nativeOutcome.draft)
        assertEquals("", nativeOutcome.ignoredReason)

        // 2. CaptureOrchestrator 自动降级至 JS OCR 规则并落库
        val fallbackRes = orchestrator.handleScreenCapture(snapshot, nativeOutcome)
        assertEquals(ParseStatus.MATCHED_LOCAL, fallbackRes.status)
        assertNotNull(fallbackRes.transactionId)
        assertTrue(fallbackRes.draft?.parserUsed?.startsWith("BUILTIN_JS:") == true)
        val savedTx = transactionDao.getById(fallbackRes.transactionId!!)!!
        assertEquals(TransactionType.EXPENSE, savedTx.type)
        assertEquals(MoneyAmount.parseToMicros("19.90"), savedTx.origAmountMicros)
        assertEquals("瑞幸咖啡", savedTx.merchant)

        // 3. 关闭 jsOcrFallbackEnabled 后不再降级 JS OCR
        currentSettings = currentSettings.copy(jsOcrFallbackEnabled = false)
        val disabledRes = orchestrator.handleScreenCapture(
            snapshot.copy(capturedAtMillis = now + 180_000L),
            nativeOutcome
        )
        assertEquals(ParseStatus.IGNORED, disabledRes.status)
    }

    @Test
    fun testParseText_supportsNotificationSmsAndScreenChannels() = runTest {
        val orchestrator = buildOrchestrator()

        // 1. NOTIFICATION 通道
        val noticeResult = orchestrator.testParseText(
            title = "【美团月付】成功支付36.50元",
            content = "您在美团外卖下单消费36.50元",
            sourcePackage = "com.sankuai.meituan",
            allowLlm = false,
            channel = CaptureChannel.NOTIFICATION
        )
        assertTrue(noticeResult.isSuccess)
        val noticeDraft = noticeResult.getOrNull()
        assertNotNull(noticeDraft)
        assertEquals(MoneyAmount.parseToMicros("36.50"), noticeDraft!!.origAmountMicros)
        assertTrue(noticeDraft.parserUsed.startsWith("BUILTIN_JS:"))

        // 2. SMS 通道
        val smsResult = orchestrator.testParseText(
            title = "短信 (95555)",
            content = "【招商银行】您账户1234于03月15日12:30在星巴克支付35.00元",
            sourcePackage = "95555",
            allowLlm = false,
            channel = CaptureChannel.SMS
        )
        assertTrue(smsResult.isSuccess)
        val smsDraft = smsResult.getOrNull()
        assertNotNull(smsDraft)
        assertEquals(MoneyAmount.parseToMicros("35.00"), smsDraft!!.origAmountMicros)
        assertEquals("星巴克", smsDraft.merchant)
        assertTrue(smsDraft.parserUsed.startsWith("BUILTIN_JS:"))

        // 3. SCREEN 通道（原生 ScreenBillParser 未命中 -> 降级 JS OCR）
        val screenResult = orchestrator.testParseText(
            title = "数字人民币",
            content = """
                交易详情
                瑞幸咖啡
                -19.90
                当前状态
                交易成功
                商户全称
                瑞幸咖啡有限公司
                交易时间
                2026-03-15 12:30:00
                付款方式
                数字人民币钱包(0012) ￥19.90
                查看更多
            """.trimIndent(),
            sourcePackage = "cn.gov.pbc.dcep",
            allowLlm = false,
            channel = CaptureChannel.SCREEN
        )
        assertTrue(screenResult.isSuccess)
        val screenDraft = screenResult.getOrNull()
        assertNotNull(screenDraft)
        assertEquals(MoneyAmount.parseToMicros("19.90"), screenDraft!!.origAmountMicros)
        assertEquals("瑞幸咖啡", screenDraft.merchant)
        assertTrue(screenDraft.parserUsed.startsWith("BUILTIN_JS:"))
    }

    private fun locateProjectFile(relativeFromRoot: String): File {
        val candidates = listOf(
            File(relativeFromRoot),
            File("..", relativeFromRoot),
            File("../..", relativeFromRoot)
        )
        return candidates.firstOrNull { it.exists() && it.isFile }
            ?: error("Cannot locate project test fixture file: $relativeFromRoot (user.dir=${System.getProperty("user.dir")})")
    }
}

package com.yubrae.app.capture.rules

import com.yubrae.app.capture.CaptureTestFixtures
import com.yubrae.app.capture.FakeAccountAliasDao
import com.yubrae.app.capture.FakeAccountDao
import com.yubrae.app.capture.FakeCategoryDao
import com.yubrae.app.capture.FakeParseRuleDao
import com.yubrae.app.core.contract.CaptureInput
import com.yubrae.app.core.model.MoneyAmount
import com.yubrae.app.data.db.AccountAliasEntity
import com.yubrae.app.data.db.AccountEntity
import com.yubrae.app.data.db.AccountGroup
import com.yubrae.app.data.db.AliasMatchType
import com.yubrae.app.data.db.CaptureChannel
import com.yubrae.app.data.db.CategoryEntity
import com.yubrae.app.data.db.DefaultSeedData
import com.yubrae.app.data.db.TransactionType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId

class SandboxedJsRuleEngineTest {

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
        DefaultSeedData.ensureAccountsAndTransitCard(accountDao, aliasDao)
        DefaultSeedData.ensurePresetCategories(categoryDao)

        val cmbDebitId = accountDao.insert(
            AccountEntity(name = "招商银行储蓄卡", groupType = AccountGroup.DEBIT, currencyCode = "CNY")
        )
        val cebCreditId = accountDao.insert(
            AccountEntity(name = "光大银行信用卡", groupType = AccountGroup.CREDIT, currencyCode = "USD")
        )
        val ccbCreditId = accountDao.insert(
            AccountEntity(name = "建设银行信用卡", groupType = AccountGroup.CREDIT, currencyCode = "CNY")
        )
        val icbcDebitId = accountDao.insert(
            AccountEntity(name = "工商银行储蓄卡", groupType = AccountGroup.DEBIT, currencyCode = "CNY")
        )
        val alipayPocketId = accountDao.insert(
            AccountEntity(name = "支付宝小荷包", groupType = AccountGroup.WALLET, currencyCode = "CNY")
        )
        val meituanCreditId = accountDao.insert(
            AccountEntity(name = "美团月付", groupType = AccountGroup.CREDIT, currencyCode = "CNY")
        )
        val huabeiId = accountDao.insert(
            AccountEntity(name = "花呗", groupType = AccountGroup.CREDIT, currencyCode = "CNY")
        )
        aliasDao.insertAll(
            listOf(
                AccountAliasEntity(accountId = cmbDebitId, matchType = AliasMatchType.CARD_LAST4, pattern = "1234", priority = 60),
                AccountAliasEntity(accountId = cebCreditId, matchType = AliasMatchType.CARD_LAST4, pattern = "8812", priority = 60),
                AccountAliasEntity(accountId = ccbCreditId, matchType = AliasMatchType.CARD_LAST4, pattern = "4321", priority = 60),
                AccountAliasEntity(accountId = icbcDebitId, matchType = AliasMatchType.CARD_LAST4, pattern = "7788", priority = 60),
                AccountAliasEntity(accountId = alipayPocketId, matchType = AliasMatchType.KEYWORD, pattern = "小荷包", priority = 80),
                AccountAliasEntity(accountId = meituanCreditId, matchType = AliasMatchType.KEYWORD, pattern = "美团月付", priority = 80),
                AccountAliasEntity(accountId = huabeiId, matchType = AliasMatchType.KEYWORD, pattern = "花呗", priority = 80)
            )
        )

        accounts = accountDao.getAll()
        aliases = aliasDao.getEnabled()
        categories = categoryDao.getAll()
    }

    @Test
    fun loadsRulesZipAndBuiltinAssetZipWithExact128Counts() {
        val repoZip = locateProjectFile("rules/rules.zip")
        val assetZip = locateProjectFile("app/src/main/assets/rules/builtin_rules.zip")

        val repoBundle = JsRuleBundleLoader.loadFromZipFile(
            file = repoZip,
            source = JsRuleSource.CLOUD,
            version = "2026.11.26.1"
        ).getOrThrow()
        val assetBundle = JsRuleBundleLoader.loadFromZipFile(
            file = assetZip,
            source = JsRuleSource.BUILTIN,
            version = "2026.11.26.1"
        ).getOrThrow()

        for (bundle in listOf(repoBundle, assetBundle)) {
            val status = bundle.toStatus()
            assertEquals(128, status.totalCount)
            assertEquals(50, status.smsCount)
            assertEquals(57, status.noticeCount)
            assertEquals(21, status.ocrCount)
            assertTrue(bundle.commonJs.isNotBlank())
            assertTrue(bundle.categoryJs.isNotBlank())
            assertTrue(bundle.smsRulesByBank.containsKey("招商银行"))
            assertTrue(bundle.smsRulesByBank.containsKey("光大银行"))
            assertTrue(bundle.watchedNoticePackages.contains("cmb.pb"))
            assertTrue(bundle.watchedNoticePackages.contains("com.unionpay"))
            assertTrue(bundle.watchedOcrPackages.contains("com.eg.android.AlipayGphone"))
            assertTrue(bundle.watchedOcrPackages.contains("com.tencent.mm"))
            assertTrue(
                bundle.allRules.none {
                    it.meta.ruleType == "app" &&
                        (it.meta.ruleApp == "com.tencent.mm" || it.meta.ruleApp == "com.eg.android.AlipayGphone")
                }
            )
        }
    }

    @Test
    fun matchesRealBankSmsRulesIncludingForeignTransferAndDomesticBanks() {
        val engine = createEngineWithBuiltinBundle()
        val now = 1_773_548_400_000L

        // 0. 无【银行】标记时直接短路返回 null
        assertNull(engine.matchSms("95555", "您尾号1234消费35.00元", now))

        // 1. 光大银行信用卡美元还款 (Transfer + USD)
        val cebInput = CaptureInput(
            channel = CaptureChannel.SMS,
            sourcePackage = "95595",
            title = "短信 (95595)",
            content = "【光大银行】您的光大卡尾号8812还款120.50美元，可用额度为5000.00美元，此卡本期美元账单已还清。",
            timestamp = now
        )
        val cebDraft = engine.parseIncoming(cebInput, aliases, accounts, categories)
        assertNotNull(cebDraft)
        assertEquals(TransactionType.TRANSFER, cebDraft!!.type)
        assertEquals(MoneyAmount.parseToMicros("120.50"), cebDraft.origAmountMicros)
        assertEquals("USD", cebDraft.origCurrency)
        assertEquals("光大银行信用卡(8812)", cebDraft.toAccountHint)
        assertEquals(accounts.first { it.name == "光大银行信用卡" }.id, cebDraft.toAccountId)
        assertTrue(cebDraft.parserUsed.startsWith("BUILTIN_JS:"))

        // 2. 招商银行消费短信
        val cmbInput = CaptureInput(
            channel = CaptureChannel.SMS,
            sourcePackage = "95555",
            title = "短信 (95555)",
            content = "【招商银行】您账户1234于03月15日12:30在星巴克支付35.00元，余额9965.00元。",
            timestamp = now
        )
        val cmbDraft = engine.parseIncoming(cmbInput, aliases, accounts, categories)
        assertNotNull(cmbDraft)
        assertEquals(TransactionType.EXPENSE, cmbDraft!!.type)
        assertEquals(MoneyAmount.parseToMicros("35.00"), cmbDraft.origAmountMicros)
        assertEquals("CNY", cmbDraft.origCurrency)
        assertEquals("星巴克", cmbDraft.merchant)
        assertEquals(accounts.first { it.name == "招商银行储蓄卡" }.id, cmbDraft.accountId)

        // 3. 工商银行收入短信
        val icbcInput = CaptureInput(
            channel = CaptureChannel.SMS,
            sourcePackage = "95588",
            title = "短信 (95588)",
            content = "【工商银行】尾号7788卡03月15日09:10收入(工资)12,500.00元",
            timestamp = now
        )
        val icbcDraft = engine.parseIncoming(icbcInput, aliases, accounts, categories)
        assertNotNull(icbcDraft)
        assertEquals(TransactionType.INCOME, icbcDraft!!.type)
        assertEquals(MoneyAmount.parseToMicros("12500.00"), icbcDraft.origAmountMicros)
        assertEquals("CNY", icbcDraft.origCurrency)
        assertEquals(accounts.first { it.name == "工商银行储蓄卡" }.id, icbcDraft.accountId)

        // 4. 建设银行龙卡信用卡消费短信
        val ccbInput = CaptureInput(
            channel = CaptureChannel.SMS,
            sourcePackage = "95533",
            title = "短信 (95533)",
            content = "【建设银行】您尾号4321的龙卡信用卡03月15日19:30消费人民币58.50元，当前可用额度9941.50元。",
            timestamp = now
        )
        val ccbDraft = engine.parseIncoming(ccbInput, aliases, accounts, categories)
        assertNotNull(ccbDraft)
        assertEquals(TransactionType.EXPENSE, ccbDraft!!.type)
        assertEquals(MoneyAmount.parseToMicros("58.50"), ccbDraft.origAmountMicros)
        assertEquals("CNY", ccbDraft.origCurrency)
        assertEquals(accounts.first { it.name == "建设银行信用卡" }.id, ccbDraft.accountId)
    }

    @Test
    fun matchesRealNotificationRulesAcrossBanksWalletsAndTransfers() {
        val engine = createEngineWithBuiltinBundle()
        val now = 1_773_548_400_000L

        // 1. 招行消费通知
        val cmbNotice = CaptureInput(
            channel = CaptureChannel.NOTIFICATION,
            sourcePackage = "cmb.pb",
            title = "招商银行",
            content = "您的招商银行储蓄卡1234于03月15日12:30，在瑞幸咖啡支付人民币19.90元。",
            timestamp = now
        )
        val cmbDraft = engine.parseIncoming(cmbNotice, aliases, accounts, categories)
        assertNotNull(cmbDraft)
        assertEquals(TransactionType.EXPENSE, cmbDraft!!.type)
        assertEquals(MoneyAmount.parseToMicros("19.90"), cmbDraft.origAmountMicros)
        assertEquals("瑞幸咖啡", cmbDraft.merchant)
        assertEquals(accounts.first { it.name == "招商银行储蓄卡" }.id, cmbDraft.accountId)

        // 2. 云闪付入账通知
        val unionPayNotice = CaptureInput(
            channel = CaptureChannel.NOTIFICATION,
            sourcePackage = "com.unionpay",
            title = "支付助手：入账通知",
            content = "您尾号为6688的银行卡于15日09时30分入账3200.00元",
            timestamp = now
        )
        val unionDraft = engine.parseIncoming(unionPayNotice, aliases, accounts, categories)
        assertNotNull(unionDraft)
        assertEquals(TransactionType.INCOME, unionDraft!!.type)
        assertEquals(MoneyAmount.parseToMicros("3200.00"), unionDraft.origAmountMicros)
        assertEquals(accounts.first { it.name == "招商信用卡6688" }.id, unionDraft.accountId)

        // 3. 支付宝小荷包自动攒 (Transfer)
        val pocketNotice = CaptureInput(
            channel = CaptureChannel.NOTIFICATION,
            sourcePackage = "com.eg.android.AlipayGphone",
            title = "支付宝小荷包自动攒执行成功",
            content = "已从支付宝余额成功转入50.00元",
            timestamp = now
        )
        val pocketDraft = engine.parseIncoming(pocketNotice, aliases, accounts, categories)
        assertNotNull(pocketDraft)
        assertEquals(TransactionType.TRANSFER, pocketDraft!!.type)
        assertEquals(MoneyAmount.parseToMicros("50.00"), pocketDraft.origAmountMicros)
        assertEquals(accounts.first { it.name == "支付宝" }.id, pocketDraft.accountId)
        assertEquals(accounts.first { it.name == "支付宝小荷包" }.id, pocketDraft.toAccountId)
        assertEquals("支付宝小荷包", pocketDraft.toAccountHint)

        // 4. 微信抢红包
        val redPacketNotice = CaptureInput(
            channel = CaptureChannel.NOTIFICATION,
            sourcePackage = "com.tencent.mm",
            title = "张三",
            content = "[微信红包]8.88元已存入零钱，可直接消费",
            timestamp = now
        )
        val redPacketDraft = engine.parseIncoming(redPacketNotice, aliases, accounts, categories)
        assertNotNull(redPacketDraft)
        assertEquals(TransactionType.INCOME, redPacketDraft!!.type)
        assertEquals(MoneyAmount.parseToMicros("8.88"), redPacketDraft.origAmountMicros)
        assertEquals("张三", redPacketDraft.merchant)
        assertEquals(accounts.first { it.name == "微信零钱" }.id, redPacketDraft.accountId)

        // 5. 美团月付支出
        val meituanNotice = CaptureInput(
            channel = CaptureChannel.NOTIFICATION,
            sourcePackage = "com.sankuai.meituan",
            title = "【美团月付】成功支付36.50元",
            content = "您在美团外卖下单消费36.50元",
            timestamp = now
        )
        val meituanDraft = engine.parseIncoming(meituanNotice, aliases, accounts, categories)
        assertNotNull(meituanDraft)
        assertEquals(TransactionType.EXPENSE, meituanDraft!!.type)
        assertEquals(MoneyAmount.parseToMicros("36.50"), meituanDraft.origAmountMicros)
        assertEquals(accounts.first { it.name == "美团月付" }.id, meituanDraft.accountId)
    }

    @Test
    fun matchesRealOcrScreenRulesForAlipayPaySuccessAndWeChatRedPacket() {
        val engine = createEngineWithBuiltinBundle()
        val now = 1_773_548_400_000L

        // 1. 支付宝支付成功 OCR
        val alipayOcrText = """
            支付成功
            ￥ 24.50
            蜜雪冰城
            付款方式
            花呗
        """.trimIndent()
        val alipayDraft = engine.parseScreenOcr(
            packageName = "com.eg.android.AlipayGphone",
            screenText = alipayOcrText,
            timestampMillis = now,
            aliases = aliases,
            accounts = accounts,
            categories = categories
        )
        assertNotNull(alipayDraft)
        assertEquals(TransactionType.EXPENSE, alipayDraft!!.type)
        assertEquals(MoneyAmount.parseToMicros("24.50"), alipayDraft.origAmountMicros)
        assertEquals("蜜雪冰城", alipayDraft.merchant)
        assertEquals(accounts.first { it.name == "花呗" }.id, alipayDraft.accountId)
        assertTrue(alipayDraft.parserUsed.startsWith("BUILTIN_JS:"))

        // 2. 微信红包 OCR
        val wechatRedPacketText = """
            微信红包-来自李四
            +66.66
            当前状态
            已存入零钱
            收款时间
            2026年3月15日12:30:00
        """.trimIndent()
        val wechatDraft = engine.parseScreenOcr(
            packageName = "com.tencent.mm",
            screenText = wechatRedPacketText,
            timestampMillis = now,
            aliases = aliases,
            accounts = accounts,
            categories = categories
        )
        assertNotNull(wechatDraft)
        assertEquals(TransactionType.INCOME, wechatDraft!!.type)
        assertEquals(MoneyAmount.parseToMicros("66.66"), wechatDraft.origAmountMicros)
        assertEquals("李四", wechatDraft.merchant)
        assertEquals(accounts.first { it.name == "微信零钱" }.id, wechatDraft.accountId)
    }

    @Test
    fun evaluatesCategoryJsTimePeriodsAndMapsToYubraeCategories() {
        val engine = createEngineWithBuiltinBundle()
        val rawMeal = JsRuleRawResult(
            type = "Expend",
            money = 18.0,
            shopName = "爆好呷",
            shopItem = "特色餐"
        )

        val zone = ZoneId.systemDefault()
        val breakfastMillis = LocalDateTime.of(2026, 3, 15, 7, 45).atZone(zone).toInstant().toEpochMilli()
        val morningTeaMillis = LocalDateTime.of(2026, 3, 15, 10, 0).atZone(zone).toInstant().toEpochMilli()
        val lunchMillis = LocalDateTime.of(2026, 3, 15, 12, 15).atZone(zone).toInstant().toEpochMilli()
        val afternoonTeaMillis = LocalDateTime.of(2026, 3, 15, 15, 0).atZone(zone).toInstant().toEpochMilli()
        val dinnerMillis = LocalDateTime.of(2026, 3, 15, 18, 30).atZone(zone).toInstant().toEpochMilli()
        val midnightMillis = LocalDateTime.of(2026, 3, 15, 22, 30).atZone(zone).toInstant().toEpochMilli()

        assertEquals("早餐", engine.evalCategory(rawMeal, breakfastMillis))
        assertEquals("早茶", engine.evalCategory(rawMeal, morningTeaMillis))
        assertEquals("午餐", engine.evalCategory(rawMeal, lunchMillis))
        assertEquals("下午茶", engine.evalCategory(rawMeal, afternoonTeaMillis))
        assertEquals("晚餐", engine.evalCategory(rawMeal, dinnerMillis))
        assertEquals("夜宵", engine.evalCategory(rawMeal, midnightMillis))

        assertTrue(JsCategoryMapper.mapToYubraeCandidates("早餐", TransactionType.EXPENSE).contains("三餐外食"))
        assertTrue(JsCategoryMapper.mapToYubraeCandidates("下午茶", TransactionType.EXPENSE).contains("咖啡茶饮"))
        assertTrue(JsCategoryMapper.mapToYubraeCandidates("夜宵", TransactionType.EXPENSE).contains("三餐外食"))
        assertTrue(JsCategoryMapper.mapToYubraeCandidates("收红包", TransactionType.INCOME).contains("微信红包"))
        assertTrue(JsCategoryMapper.mapToYubraeCandidates("工资", TransactionType.INCOME).contains("工资薪金"))

        // 验证 parseIncoming 中 LocalRuleParser 兜底到 "其他支出" 时会自动经由 category.js + JsCategoryMapper 补全精确分类
        val smsForCategoryFallback = CaptureInput(
            channel = CaptureChannel.SMS,
            sourcePackage = "95555",
            title = "短信 (95555)",
            content = "【招商银行】您账户1234于03月15日12:15在爆好呷支付25.00元",
            timestamp = lunchMillis
        )
        val draft = engine.parseIncoming(smsForCategoryFallback, aliases, accounts, categories, enableCategoryJs = true)
        assertNotNull(draft)
        assertEquals("三餐外食", draft!!.categoryName)
    }

    @Test
    fun blocksJavaReflectionAndPackageAccessInsideSandbox() {
        val maliciousScripts = listOf(
            "var rule_evil_1 = { get: function(d) { return java.lang.Runtime.getRuntime().exec('calc'); } };",
            "var rule_evil_2 = { get: function(d) { var f = new Packages.java.io.File('.'); return { type: 'Expend', money: 10, shopName: f.getName() }; } };",
            "var rule_evil_3 = { get: function(d) { var sys = java.lang.System; return { type: 'Expend', money: 10, shopName: sys.getProperty('user.home') }; } };",
            "var rule_evil_4 = { get: function(d) { var c = ({}).getClass().forName('java.lang.Runtime'); return { type: 'Expend', money: 10, shopName: String(c) }; } };"
        )
        val entries = maliciousScripts.mapIndexed { idx, script ->
            JsRuleEntry(
                meta = JsRuleMeta(
                    ruleName = "rule_evil_${idx + 1}",
                    ruleChineseName = "恶意规则${idx + 1}",
                    ruleApp = "com.evil.app",
                    ruleType = "notice",
                    path = "notice/com.evil.app/evil_${idx + 1}.js"
                ),
                script = script
            )
        }
        val bundle = JsRuleBundle(
            source = JsRuleSource.IMPORTED,
            version = "sec-test",
            updatedAt = "",
            changelog = "",
            commonJs = "var common = {};",
            categoryJs = "",
            smsRules = emptyList(),
            smsRulesByBank = emptyMap(),
            unindexedSmsRules = emptyList(),
            noticeRulesByPackage = mapOf("com.evil.app" to entries),
            ocrRulesByPackage = emptyMap(),
            allRules = entries
        )

        val engine = SandboxedJsRuleEngine(ruleTimeoutMillis = 250L)
        engine.updateBundle(bundle)

        val matched = engine.matchNotice("com.evil.app", "title", "content", 1_773_548_400_000L)
        assertNull("Java/Packages access inside sandbox must be blocked and return null", matched)
    }

    @Test
    fun abortsInfiniteLoopsEvenInsideTryCatchUnderOneSecond() {
        val loopEntries = listOf(
            JsRuleEntry(
                meta = JsRuleMeta(
                    ruleName = "rule_loop_plain",
                    ruleChineseName = "普通死循环",
                    ruleApp = "com.loop.app",
                    ruleType = "notice",
                    path = "notice/com.loop.app/loop_plain.js"
                ),
                script = "var rule_loop_plain = { get: function(d) { while (true) {} return { type: 'Expend', money: 1 }; } };"
            ),
            JsRuleEntry(
                meta = JsRuleMeta(
                    ruleName = "rule_loop_trycatch",
                    ruleChineseName = "TryCatch死循环",
                    ruleApp = "com.loop.app",
                    ruleType = "notice",
                    path = "notice/com.loop.app/loop_trycatch.js"
                ),
                script = """
                    var rule_loop_trycatch = {
                        get: function(d) {
                            try {
                                while (true) {}
                            } catch (e) {
                                while (true) {}
                            }
                            return { type: 'Expend', money: 2 };
                        }
                    };
                """.trimIndent()
            )
        )
        val okEntry = JsRuleEntry(
            meta = JsRuleMeta(
                ruleName = "rule_ok_after_loop",
                ruleChineseName = "熔断后恢复正常规则",
                ruleApp = "com.safe.app",
                ruleType = "notice",
                path = "notice/com.safe.app/ok.js"
            ),
            script = "var rule_ok_after_loop = { get: function(d) { return { type: 'Expend', money: 9.9, shopName: '安全商户' }; } };"
        )
        val bundle = JsRuleBundle(
            source = JsRuleSource.IMPORTED,
            version = "timeout-test",
            updatedAt = "",
            changelog = "",
            commonJs = "var common = {};",
            categoryJs = "",
            smsRules = emptyList(),
            smsRulesByBank = emptyMap(),
            unindexedSmsRules = emptyList(),
            noticeRulesByPackage = mapOf(
                "com.loop.app" to loopEntries,
                "com.safe.app" to listOf(okEntry)
            ),
            ocrRulesByPackage = emptyMap(),
            allRules = loopEntries + okEntry
        )

        val engine = SandboxedJsRuleEngine(ruleTimeoutMillis = 120L)
        engine.updateBundle(bundle)

        val startNanos = System.nanoTime()
        val loopResult = engine.matchNotice("com.loop.app", "t", "c", 1_773_548_400_000L)
        val elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000L

        assertNull(loopResult)
        assertTrue("Expected infinite loops (including try/catch) to abort in < 1000ms, took ${elapsedMillis}ms", elapsedMillis < 1_000L)

        // 熔断后引擎仍可正常执行后续合法规则
        val recovered = engine.matchNotice("com.safe.app", "t", "c", 1_773_548_400_000L)
        assertNotNull(recovered)
        assertEquals(9.9, recovered!!.money, 0.001)
        assertEquals("安全商户", recovered.shopName)
    }

    private fun createEngineWithBuiltinBundle(): SandboxedJsRuleEngine {
        val assetZip = locateProjectFile("app/src/main/assets/rules/builtin_rules.zip")
        val bundle = JsRuleBundleLoader.loadFromZipFile(
            file = assetZip,
            source = JsRuleSource.BUILTIN,
            version = "2026.11.26.1"
        ).getOrThrow()
        return SandboxedJsRuleEngine().apply { updateBundle(bundle) }
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

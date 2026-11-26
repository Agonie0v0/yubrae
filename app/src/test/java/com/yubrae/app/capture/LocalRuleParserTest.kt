package com.yubrae.app.capture

import com.yubrae.app.core.contract.CaptureInput
import com.yubrae.app.core.model.MoneyAmount
import com.yubrae.app.data.db.AccountAliasEntity
import com.yubrae.app.data.db.AccountEntity
import com.yubrae.app.data.db.CaptureChannel
import com.yubrae.app.data.db.CategoryEntity
import com.yubrae.app.data.db.DefaultSeedData
import com.yubrae.app.data.db.ParseRuleEntity
import com.yubrae.app.data.db.TagTextCodec
import com.yubrae.app.data.db.TransactionType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LocalRuleParserTest {

    private val parser = LocalRuleParser()
    private lateinit var accounts: List<AccountEntity>
    private lateinit var aliases: List<AccountAliasEntity>
    private lateinit var categories: List<CategoryEntity>
    private lateinit var rules: List<ParseRuleEntity>

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
        rules = ruleDao.getEnabled()
    }

    @Test
    fun filtersNonFinancialNoiseNotificationsAndSms() {
        val noiseSamples = listOf(
            CaptureInput(CaptureChannel.SMS, "1069000", "短信", "【招商银行】验证码849201，您正在登录手机银行，5分钟内有效。"),
            CaptureInput(CaptureChannel.NOTIFICATION, "com.sf.express", "顺丰速运", "您的快件SF102938已到达菜鸟驿站，取件码6-2-108。"),
            CaptureInput(CaptureChannel.SMS, "95508", "短信", "【广发银行】恭喜您获得最高20万元分期额度，点击领取立减金，回TD退订。"),
            CaptureInput(CaptureChannel.NOTIFICATION, "com.android.updater", "系统更新", "HyperOS 2.0.10 版本更新下载完成，请重启安装。"),
            CaptureInput(CaptureChannel.SMS, "95555", "短信", "【招商银行】您的信用卡本期应还人民币3,450.00元，最后还款日每月23日。"),
            CaptureInput(CaptureChannel.NOTIFICATION, "com.cmbchina.app", "安全提醒", "您的账户已成功登录手机银行，如非本人操作请修改密码。")
        )

        noiseSamples.forEach { sample ->
            assertNull("Expected noise to return null: ${sample.content}", parser.parse(sample, rules, aliases, accounts, categories))
        }
    }

    @Test
    fun matchesWeChatAndAlipayRulesWithSmartCategoryAndPackageRouting() {
        val wechatInput = CaptureInput(
            channel = CaptureChannel.NOTIFICATION,
            sourcePackage = "com.tencent.mm",
            title = "微信支付",
            content = "微信支付：向星巴克付款32.00元"
        )
        val wechatDraft = parser.parse(wechatInput, rules, aliases, accounts, categories)
        assertNotNull(wechatDraft)
        assertEquals(TransactionType.EXPENSE, wechatDraft!!.type)
        assertEquals(MoneyAmount.parseToMicros("32.00"), wechatDraft.origAmountMicros)
        assertEquals("CNY", wechatDraft.origCurrency)
        assertEquals("星巴克", wechatDraft.merchant)
        assertEquals("餐饮美食", wechatDraft.categoryName)
        assertEquals("微信零钱", accounts.first { it.id == wechatDraft.accountId }.name)

        val alipayInput = CaptureInput(
            channel = CaptureChannel.NOTIFICATION,
            sourcePackage = "com.eg.android.AlipayGphone",
            title = "支付宝",
            content = "你向滴滴出行付款28.60元"
        )
        val alipayDraft = parser.parse(alipayInput, rules, aliases, accounts, categories)
        assertNotNull(alipayDraft)
        assertEquals(TransactionType.EXPENSE, alipayDraft!!.type)
        assertEquals(MoneyAmount.parseToMicros("28.60"), alipayDraft.origAmountMicros)
        assertEquals("滴滴出行", alipayDraft.merchant)
        assertEquals("交通出行", alipayDraft.categoryName)
        assertEquals("支付宝", accounts.first { it.id == alipayDraft.accountId }.name)
    }

    @Test
    fun parsesForeignCurrencySmsAndRoutesByCardLast4() {
        val smsInput = CaptureInput(
            channel = CaptureChannel.SMS,
            sourcePackage = "95555",
            title = "短信 (95555)",
            content = "【招商银行】您尾号6688信用卡03月15日消费USD 15.00（折合人民币108.68元），商户：OpenAI"
        )
        val draft = parser.parse(smsInput, rules, aliases, accounts, categories)
        assertNotNull(draft)
        assertEquals(TransactionType.EXPENSE, draft!!.type)
        assertEquals(MoneyAmount.parseToMicros("15.00"), draft.origAmountMicros)
        assertEquals("USD", draft.origCurrency)
        assertEquals("6688", draft.accountHint)
        assertEquals("OpenAI", draft.merchant)
        assertEquals("数字订阅", draft.categoryName)
        assertEquals("招商信用卡6688", accounts.first { it.id == draft.accountId }.name)
    }

    @Test
    fun parsesEurAndGbpNotificationsAndSalaryIncomeWithoutConfusingBalance() {
        val eurInput = CaptureInput(
            channel = CaptureChannel.NOTIFICATION,
            sourcePackage = "com.revolut.revolut",
            title = "Revolut EUR",
            content = "You spent €24.50 at Hetzner"
        )
        val eurDraft = parser.parse(eurInput, rules, aliases, accounts, categories)
        assertNotNull(eurDraft)
        assertEquals("EUR", eurDraft!!.origCurrency)
        assertEquals(MoneyAmount.parseToMicros("24.50"), eurDraft.origAmountMicros)
        assertEquals("Hetzner", eurDraft.merchant)
        assertEquals("数字订阅", eurDraft.categoryName)
        assertEquals("欧元账户 (EUR)", accounts.first { it.id == eurDraft.accountId }.name)

        val gbpInput = CaptureInput(
            channel = CaptureChannel.NOTIFICATION,
            sourcePackage = "com.monzo.app",
            title = "Monzo",
            content = "Card charged £18.00 at Sainsbury"
        )
        val gbpDraft = parser.parse(gbpInput, rules, aliases, accounts, categories)
        assertNotNull(gbpDraft)
        assertEquals("GBP", gbpDraft!!.origCurrency)
        assertEquals(MoneyAmount.parseToMicros("18.00"), gbpDraft.origAmountMicros)
        assertEquals("Sainsbury", gbpDraft.merchant)
        assertEquals("英镑账户 (GBP)", accounts.first { it.id == gbpDraft.accountId }.name)

        val salaryInput = CaptureInput(
            channel = CaptureChannel.SMS,
            sourcePackage = "95588",
            title = "短信 (95588)",
            content = "【工商银行】您尾号6688账户03月15日入账工资人民币12,500.00元，余额25,000.00元。"
        )
        val salaryDraft = parser.parse(salaryInput, rules, aliases, accounts, categories)
        assertNotNull(salaryDraft)
        assertEquals(TransactionType.INCOME, salaryDraft!!.type)
        assertEquals(MoneyAmount.parseToMicros("12500.00"), salaryDraft.origAmountMicros)
        assertEquals("CNY", salaryDraft.origCurrency)
        assertEquals("工资薪金", salaryDraft.categoryName)
    }

    @Test
    fun fallsBackToHeuristicParserWhenNoRegexRuleMatches() {
        val subwayInput = CaptureInput(
            channel = CaptureChannel.SHARE,
            sourcePackage = "share",
            title = "出行通知",
            content = "刚刚在地铁闸机扣款5.00元，可用余额95.00元"
        )
        val subwayDraft = parser.parse(subwayInput, rules, aliases, accounts, categories)
        assertNotNull(subwayDraft)
        assertEquals("LOCAL_HEURISTIC", subwayDraft!!.parserUsed)
        assertEquals(TransactionType.EXPENSE, subwayDraft.type)
        assertEquals(MoneyAmount.parseToMicros("5.00"), subwayDraft.origAmountMicros)
        assertEquals("CNY", subwayDraft.origCurrency)
        assertEquals("交通出行", subwayDraft.categoryName)

        val wiseSms = CaptureInput(
            channel = CaptureChannel.SMS,
            sourcePackage = "Wise",
            title = "Wise",
            content = "Wise alert: USD 12.99 debited for purchase at Anthropic"
        )
        val wiseDraft = parser.parse(wiseSms, rules, aliases, accounts, categories)
        assertNotNull(wiseDraft)
        assertTrue(wiseDraft!!.parserUsed.startsWith("LOCAL_"))
        assertEquals(TransactionType.EXPENSE, wiseDraft.type)
        assertEquals("USD", wiseDraft.origCurrency)
        assertEquals(MoneyAmount.parseToMicros("12.99"), wiseDraft.origAmountMicros)
        assertEquals("数字订阅", wiseDraft.categoryName)
        assertEquals("美元账户 (USD)", accounts.first { it.id == wiseDraft.accountId }.name)
    }

    @Test
    fun respectsRulePriorityDescendingWhenMultipleRulesMatch() {
        val customHighRule = ParseRuleEntity(
            id = 999L,
            name = "高优瑞幸专属规则",
            sourcePackage = "com.tencent.mm",
            channelType = "NOTIFICATION",
            regexPattern = "向(瑞幸咖啡)付款([0-9]+\\.[0-9]{2})元",
            transactionType = TransactionType.EXPENSE,
            amountGroup = 2,
            merchantGroup = 1,
            defaultCurrency = "CNY",
            defaultCategoryName = "餐饮美食",
            priority = 200
        )
        val input = CaptureInput(
            channel = CaptureChannel.NOTIFICATION,
            sourcePackage = "com.tencent.mm",
            title = "微信支付",
            content = "向瑞幸咖啡付款18.50元"
        )
        val draft = parser.parse(input, rules + customHighRule, aliases, accounts, categories)
        assertNotNull(draft)
        assertEquals("LOCAL_RULE:高优瑞幸专属规则", draft!!.parserUsed)
        assertEquals("瑞幸咖啡", draft.merchant)
        assertEquals(MoneyAmount.parseToMicros("18.50"), draft.origAmountMicros)
    }

    @Test
    fun parsesMiWalletTransitCardDecimalsAndIntegersStrippingBalanceAndInfersCommuteTag() = runTest {
        val fullAccDao = FakeAccountDao()
        val fullAliasDao = FakeAccountAliasDao()
        val fullCatDao = FakeCategoryDao()
        val fullRuleDao = FakeParseRuleDao()
        DefaultSeedData.ensureAccountsAndTransitCard(fullAccDao, fullAliasDao)
        DefaultSeedData.ensurePresetCategories(fullCatDao)
        DefaultSeedData.ensurePresetParseRules(fullRuleDao)

        val fullAccounts = fullAccDao.getAll()
        val fullAliases = fullAliasDao.getEnabled()
        val fullCategories = fullCatDao.getAll()
        val fullRules = fullRuleDao.getEnabled()

        val decimalInput = CaptureInput(
            channel = CaptureChannel.NOTIFICATION,
            sourcePackage = "com.miui.tsmclient",
            title = "重庆畅通卡",
            content = "刷卡消费2.50元，卡内余额38.50元"
        )
        val decimalDraft = parser.parse(decimalInput, fullRules, fullAliases, fullAccounts, fullCategories)
        assertNotNull(decimalDraft)
        assertEquals(TransactionType.EXPENSE, decimalDraft!!.type)
        assertEquals(MoneyAmount.parseToMicros("2.50"), decimalDraft.origAmountMicros)
        assertEquals("CNY", decimalDraft.origCurrency)
        assertEquals("重庆畅通卡", decimalDraft.merchant)
        assertEquals("地铁公交", decimalDraft.categoryName)
        assertEquals("公交卡", fullAccounts.first { it.id == decimalDraft.accountId }.name)
        assertEquals("通勤", decimalDraft.tags)

        val integerInput = CaptureInput(
            channel = CaptureChannel.NOTIFICATION,
            sourcePackage = "com.mipay.wallet",
            title = "小米钱包",
            content = "重庆畅通卡 本次扣费2元，卡内余额38.50元"
        )
        val integerDraft = parser.parse(integerInput, fullRules, fullAliases, fullAccounts, fullCategories)
        assertNotNull(integerDraft)
        assertEquals(MoneyAmount.parseToMicros("2.00"), integerDraft!!.origAmountMicros)
        assertEquals("重庆畅通卡", integerDraft.merchant)
        assertEquals("地铁公交", integerDraft.categoryName)
        assertEquals("公交卡", fullAccounts.first { it.id == integerDraft.accountId }.name)
        assertEquals("通勤", integerDraft.tags)

        val heuristicTransitInput = CaptureInput(
            channel = CaptureChannel.NOTIFICATION,
            sourcePackage = "com.miui.tsmclient",
            title = "小米钱包",
            content = "重庆畅通卡 出站扣费2元，卡内余额38.50元"
        )
        val heuristicDraft = parser.parse(heuristicTransitInput, emptyList(), fullAliases, fullAccounts, fullCategories)
        assertNotNull(heuristicDraft)
        assertEquals("LOCAL_HEURISTIC", heuristicDraft!!.parserUsed)
        assertEquals(MoneyAmount.parseToMicros("2.00"), heuristicDraft.origAmountMicros)
        assertEquals("重庆畅通卡", heuristicDraft.merchant)
        assertEquals("地铁公交", heuristicDraft.categoryName)
        assertEquals("通勤", heuristicDraft.tags)
    }

    @Test
    fun ignoresTransitEntryAndLowBalanceNoiseUnlessExplicitDeductionPresent() = runTest {
        val fullAccDao = FakeAccountDao()
        val fullAliasDao = FakeAccountAliasDao()
        val fullCatDao = FakeCategoryDao()
        val fullRuleDao = FakeParseRuleDao()
        DefaultSeedData.ensureAccountsAndTransitCard(fullAccDao, fullAliasDao)
        DefaultSeedData.ensurePresetCategories(fullCatDao)
        DefaultSeedData.ensurePresetParseRules(fullRuleDao)

        val fullAccounts = fullAccDao.getAll()
        val fullAliases = fullAliasDao.getEnabled()
        val fullCategories = fullCatDao.getAll()
        val fullRules = fullRuleDao.getEnabled()

        val transitNoiseSamples = listOf(
            CaptureInput(CaptureChannel.NOTIFICATION, "com.miui.tsmclient", "重庆畅通卡", "进站成功"),
            CaptureInput(CaptureChannel.NOTIFICATION, "com.mipay.wallet", "小米钱包", "已进站，闸机已开启，卡内余额38.50元"),
            CaptureInput(CaptureChannel.NOTIFICATION, "com.miui.tsmclient", "重庆畅通卡", "刷卡进站，请刷卡出站"),
            CaptureInput(CaptureChannel.NOTIFICATION, "com.miui.tsmclient", "重庆畅通卡", "余额不足，卡内余额1.20元，请及时充值"),
            CaptureInput(CaptureChannel.NOTIFICATION, "com.mipay.wallet", "小米钱包", "开卡成功，欢迎使用手机交通卡"),
            CaptureInput(CaptureChannel.NOTIFICATION, "com.xiaomi.pass", "小米智能卡", "NFC已开启")
        )
        transitNoiseSamples.forEach { sample ->
            assertNull(
                "Expected transit non-charge noise to return null: ${sample.content}",
                parser.parse(sample, fullRules, fullAliases, fullAccounts, fullCategories)
            )
        }

        val exitDeductWithLowBalanceWarning = CaptureInput(
            channel = CaptureChannel.NOTIFICATION,
            sourcePackage = "com.miui.tsmclient",
            title = "重庆畅通卡",
            content = "出站扣费2元，余额不足，请及时充值，卡内余额0.50元"
        )
        val draft = parser.parse(exitDeductWithLowBalanceWarning, fullRules, fullAliases, fullAccounts, fullCategories)
        assertNotNull(draft)
        assertEquals(MoneyAmount.parseToMicros("2.00"), draft!!.origAmountMicros)
        assertEquals("重庆畅通卡", draft.merchant)
        assertEquals("通勤", draft.tags)
    }

    @Test
    fun matchesExpandedSubcategoriesAndAutoTagsWithFullSeededCatalog() = runTest {
        val fullAccDao = FakeAccountDao()
        val fullAliasDao = FakeAccountAliasDao()
        val fullCatDao = FakeCategoryDao()
        val fullRuleDao = FakeParseRuleDao()
        DefaultSeedData.ensureAccountsAndTransitCard(fullAccDao, fullAliasDao)
        DefaultSeedData.ensurePresetCategories(fullCatDao)
        DefaultSeedData.ensurePresetParseRules(fullRuleDao)

        val fullAccounts = fullAccDao.getAll()
        val fullAliases = fullAliasDao.getEnabled()
        val fullCategories = fullCatDao.getAll()
        val fullRules = fullRuleDao.getEnabled()

        val catFoodDraft = parser.parse(
            CaptureInput(
                channel = CaptureChannel.NOTIFICATION,
                sourcePackage = "com.tencent.mm",
                title = "微信支付",
                content = "微信支付：向淘宝付款68.00元，购买皇家猫粮2kg"
            ),
            fullRules, fullAliases, fullAccounts, fullCategories
        )
        assertNotNull(catFoodDraft)
        assertEquals("宠物食品", catFoodDraft!!.categoryName)
        assertEquals(listOf("淘宝"), TagTextCodec.parse(catFoodDraft.tags))

        val takeoutDraft = parser.parse(
            CaptureInput(
                channel = CaptureChannel.NOTIFICATION,
                sourcePackage = "com.tencent.mm",
                title = "微信支付",
                content = "微信支付：向美团外卖付款26.50元"
            ),
            fullRules, fullAliases, fullAccounts, fullCategories
        )
        assertNotNull(takeoutDraft)
        assertEquals("外卖", takeoutDraft!!.categoryName)
        assertEquals(listOf("美团"), TagTextCodec.parse(takeoutDraft.tags))

        val bookDraft = parser.parse(
            CaptureInput(
                channel = CaptureChannel.SMS,
                sourcePackage = "95555",
                title = "短信 (95555)",
                content = "【招商银行】您尾号6688信用卡消费USD 19.99，商户：Amazon 买书"
            ),
            fullRules, fullAliases, fullAccounts, fullCategories
        )
        assertNotNull(bookDraft)
        assertEquals("书籍资料", bookDraft!!.categoryName)
        assertTrue(TagTextCodec.parse(bookDraft.tags).contains("跨境"))
    }

    @Test
    fun resolvesChineseBreakfastAndDiningMerchantFirstWithoutGroceryOrPlatformHijack() = runTest {
        val fullAccDao = FakeAccountDao()
        val fullAliasDao = FakeAccountAliasDao()
        val fullCatDao = FakeCategoryDao()
        val fullRuleDao = FakeParseRuleDao()
        DefaultSeedData.ensureAccountsAndTransitCard(fullAccDao, fullAliasDao)
        DefaultSeedData.ensurePresetCategories(fullCatDao)
        DefaultSeedData.ensurePresetParseRules(fullRuleDao)

        val fullAccounts = fullAccDao.getAll()
        val fullAliases = fullAliasDao.getEnabled()
        val fullCategories = fullCatDao.getAll()
        val fullRules = fullRuleDao.getEnabled()

        val diningCases = listOf(
            "逍遥优质胡辣汤（**华）" to "三餐外食",
            "兰州牛肉面" to "三餐外食",
            "淮南牛肉汤" to "三餐外食",
            "鸡蛋灌饼" to "三餐外食",
            "永和大王" to "三餐外食",
            "生煎锅贴" to "三餐外食",
            "大米先生" to "三餐外食",
            "美团买菜 蔬菜鸡蛋" to "买菜做饭"
        )
        diningCases.forEach { (merchantText, expectedCategory) ->
            val draft = parser.parse(
                CaptureInput(
                    channel = CaptureChannel.NOTIFICATION,
                    sourcePackage = "com.tencent.mm",
                    title = "微信支付",
                    content = "微信支付：向${merchantText}付款15.00元"
                ),
                fullRules, fullAliases, fullAccounts, fullCategories
            )
            assertNotNull("Expected draft for $merchantText", draft)
            assertEquals("Unexpected category for $merchantText", expectedCategory, draft!!.categoryName)
        }

        // 商户优先匹配：即使 fullText 含淘宝/买菜等干扰词，具体餐馆商户名依然优先锁定餐饮
        val merchantFirstCategory = parser.resolveCategory(
            type = TransactionType.EXPENSE,
            merchant = "逍遥优质胡辣汤（**华）",
            fullText = "逍遥优质胡辣汤（**华） 淘宝闪购可叠加立减 买菜",
            ruleDefaultCategoryName = "其他支出",
            categories = fullCategories
        )
        assertEquals("三餐外食", merchantFirstCategory?.name)

        // 泛平台壳商户仍保留商品关键词匹配（如“淘宝买猫粮 -> 宠物食品”）
        val taobaoCatFood = parser.parse(
            CaptureInput(
                channel = CaptureChannel.NOTIFICATION,
                sourcePackage = "com.tencent.mm",
                title = "微信支付",
                content = "微信支付：向淘宝付款68.00元，购买皇家猫粮2kg"
            ),
            fullRules, fullAliases, fullAccounts, fullCategories
        )
        assertEquals("宠物食品", taobaoCatFood!!.categoryName)

        // 当用户自建了“早餐”分类时，中式早餐词条优先命中“早餐”
        val categoriesWithBreakfast = fullCategories + CategoryEntity(
            id = 9999L,
            name = "早餐",
            type = TransactionType.EXPENSE
        )
        listOf("逍遥优质胡辣汤（**华）", "鸡蛋灌饼", "永和大王", "生煎锅贴").forEach { breakfastMerchant ->
            val cat = parser.resolveCategory(
                type = TransactionType.EXPENSE,
                merchant = breakfastMerchant,
                fullText = breakfastMerchant,
                ruleDefaultCategoryName = "其他支出",
                categories = categoriesWithBreakfast
            )
            assertEquals("Expected custom 早餐 for $breakfastMerchant", "早餐", cat?.name)
        }
        val noodleCat = parser.resolveCategory(
            type = TransactionType.EXPENSE,
            merchant = "兰州牛肉面",
            fullText = "兰州牛肉面",
            ruleDefaultCategoryName = "其他支出",
            categories = categoriesWithBreakfast
        )
        assertEquals("三餐外食", noodleCat?.name)
    }
}

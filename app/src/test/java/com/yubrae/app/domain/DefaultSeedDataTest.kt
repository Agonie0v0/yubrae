package com.yubrae.app.domain

import com.yubrae.app.capture.FakeAccountAliasDao
import com.yubrae.app.capture.FakeParseRuleDao
import com.yubrae.app.capture.LocalRuleParser
import com.yubrae.app.core.contract.CaptureInput
import com.yubrae.app.core.model.MoneyAmount
import com.yubrae.app.data.db.AccountAliasEntity
import com.yubrae.app.data.db.AccountEntity
import com.yubrae.app.data.db.AccountGroup
import com.yubrae.app.data.db.AliasMatchType
import com.yubrae.app.data.db.CaptureChannel
import com.yubrae.app.data.db.CategoryEntity
import com.yubrae.app.data.db.DefaultSeedData
import com.yubrae.app.data.db.LedgerEntity
import com.yubrae.app.data.db.ParseRuleEntity
import com.yubrae.app.data.db.TagEntity
import com.yubrae.app.data.db.TagTextCodec
import com.yubrae.app.data.db.TransactionEntity
import com.yubrae.app.data.db.TransactionType
import com.yubrae.app.data.network.ExchangeRateApi
import com.yubrae.app.data.repository.ExchangeRateRepositoryImpl
import com.yubrae.app.data.repository.LedgerRepositoryImpl
import com.yubrae.app.ui.components.CartoonCategoryIcons
import com.yubrae.app.ui.components.KairoIcons
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DefaultSeedDataTest {

    @Test
    fun ensurePresetCategories_seedsAll16ExpenseAnd9IncomeAndIsIdempotent() = runTest {
        val categoryDao = FakeCategoryDao()

        val firstAdded = DefaultSeedData.ensurePresetCategories(categoryDao)
        val all = categoryDao.getAll()
        val expenseParents = all.filter { it.type == TransactionType.EXPENSE && it.parentId == null }
        val incomeParents = all.filter { it.type == TransactionType.INCOME && it.parentId == null }

        assertEquals(16, expenseParents.size)
        assertEquals(9, incomeParents.size)
        assertEquals(all.size, firstAdded)

        val secondAdded = DefaultSeedData.ensurePresetCategories(categoryDao)
        assertEquals(0, secondAdded)
        assertEquals(all.size, categoryDao.getAll().size)
    }

    @Test
    fun ensurePresetCategories_preservesLegacyTopLevelCategoryWithoutDuplication() = runTest {
        val categoryDao = FakeCategoryDao()
        val legacyFeeId = categoryDao.insert(
            CategoryEntity(
                parentId = null,
                name = "汇损手续费",
                type = TransactionType.EXPENSE,
                icon = "currency_exchange",
                sortOrder = 90,
                isSystem = true
            )
        )
        val customCatId = categoryDao.insert(
            CategoryEntity(
                parentId = null,
                name = "手办收藏",
                type = TransactionType.EXPENSE,
                icon = "category",
                sortOrder = 999,
                isSystem = false
            )
        )

        val added = DefaultSeedData.ensurePresetCategories(categoryDao)
        assertTrue(added > 0)

        val all = categoryDao.getAll()
        val feeMatches = all.filter { it.name == "汇损手续费" && it.type == TransactionType.EXPENSE }
        assertEquals(1, feeMatches.size)
        assertEquals(legacyFeeId, feeMatches.first().id)
        assertEquals(null, feeMatches.first().parentId)

        val customMatches = all.filter { it.name == "手办收藏" }
        assertEquals(1, customMatches.size)
        assertEquals(customCatId, customMatches.first().id)
    }

    @Test
    fun ensurePresetTags_seeds21TagsIn4GroupsAndPreservesCustomAndUpgradesSystemIcons() = runTest {
        val tagDao = FakeTagDao()
        val customSameNameId = tagDao.insert(
            TagEntity(name = "外卖", groupName = "自定义组", colorHex = 0xFF123456L, sortOrder = 1, isSystem = false)
        )
        val redundantSystemId = tagDao.insert(
            TagEntity(name = "公司报销", groupName = "对象账务", colorHex = 0xFF1677FFL, sortOrder = 260, isSystem = true, icon = "receipt_long")
        )
        val legacyFamilyId = tagDao.insert(
            TagEntity(name = "家人", groupName = "对象账务", colorHex = 0xFFEA580CL, sortOrder = 170, isSystem = true, icon = "home")
        )
        val legacyStockupId = tagDao.insert(
            TagEntity(name = "大促囤货", groupName = "场景行程", colorHex = 0xFF9333EAL, sortOrder = 100, isSystem = true, icon = "shopping_basket")
        )

        val added = DefaultSeedData.ensurePresetTags(tagDao)
        assertEquals(19, added)

        val all = tagDao.getAll()
        // 21 个官方标签 + 1 个用户自建的 “外卖” 标签；冗余系统标签 “公司报销” 已被无损清理
        assertEquals(22, all.size)
        assertFalse(all.any { it.id == redundantSystemId || (it.name == "公司报销" && it.isSystem) })

        val customExisting = all.first { it.name == "外卖" }
        assertEquals(customSameNameId, customExisting.id)
        assertEquals("自定义组", customExisting.groupName)
        assertEquals("", customExisting.icon)

        assertEquals("family_restroom", all.first { it.id == legacyFamilyId }.icon)
        assertEquals("add_shopping_cart", all.first { it.id == legacyStockupId }.icon)

        val expectedIcons = mapOf(
            "拼多多" to "loyalty",
            "淘宝" to "shopping_bag",
            "京东" to "local_shipping",
            "美团" to "fastfood",
            "线下" to "storefront",
            "跨境" to "public",
            "通勤" to "subway",
            "差旅" to "luggage",
            "旅行" to "flight",
            "大促囤货" to "add_shopping_cart",
            "刚需" to "verified",
            "分期" to "credit_card",
            "优惠立减" to "local_offer",
            "冲动消费" to "bolt",
            "大额" to "diamond",
            "自己" to "person",
            "家人" to "family_restroom",
            "伴侣" to "favorite",
            "AA" to "groups",
            "可抵税" to "calculate",
            "装修" to "handyman"
        )
        assertEquals(21, expectedIcons.size)
        expectedIcons.forEach { (name, iconKey) ->
            val tag = all.first { it.name == name }
            assertEquals("Unexpected icon key for tag $name", iconKey, tag.icon)
        }

        assertEquals(0, DefaultSeedData.ensurePresetTags(tagDao))
    }

    @Test
    fun presetCategoriesAndTags_haveNoDuplicatesAndUniqueBespokeIcons() = runTest {
        val catDao = FakeCategoryDao()
        val tagDao = FakeTagDao()
        DefaultSeedData.ensurePresetCategories(catDao)
        DefaultSeedData.ensurePresetTags(tagDao)

        val categories = catDao.getAll()
        val tags = tagDao.getAll()

        // 1. 数量与去重校验：25 大类 + 90 二级 = 115 分类；21 标签；分类与标签零重名
        assertEquals(115, categories.size)
        assertEquals(21, tags.size)
        val catNameKeys = categories.map { it.name to it.type }.toSet()
        assertEquals(115, catNameKeys.size)
        val tagNames = tags.map { it.name }.toSet()
        assertEquals(21, tagNames.size)
        assertTrue(
            "Category and Tag names must not collide: ${categories.map { it.name }.toSet().intersect(tagNames)}",
            categories.map { it.name }.toSet().intersect(tagNames).isEmpty()
        )

        // 2. 全部 115 个内置分类图标键 & 卡通视觉图案 100% 唯一、零撞图
        val catIcons = categories.map { it.icon }
        assertTrue(catIcons.all { it.isNotBlank() && CartoonCategoryIcons.hasBespokeIcon(it) })
        assertEquals(115, catIcons.toSet().size)
        val catVisualVariants = catIcons.map { CartoonCategoryIcons.resolveVisualVariantId(it) }.toSet()
        assertEquals("Expected 115 distinct cartoon drawings, got ${catVisualVariants.size}", 115, catVisualVariants.size)

        // 3. 全部 21 个内置标签图标键 & Vector 图标 100% 唯一、全部适配卡通绘与标签库
        val tagIcons = tags.map { it.icon }
        assertTrue(tagIcons.all { it.isNotBlank() && CartoonCategoryIcons.hasBespokeIcon(it) })
        assertEquals(21, tagIcons.toSet().size)
        assertEquals(21, tagIcons.mapNotNull { KairoIcons.tagIcon(it) }.toSet().size)
    }

    @Test
    fun ensurePresetCategories_upgradesLegacySystemIconsPreservingUserChoiceAndMigratingRemovedOtherMisc() = runTest {
        val categoryDao = FakeCategoryDao()
        val transactionDao = FakeTransactionDao()

        val mealsSubId = categoryDao.insert(
            CategoryEntity(parentId = null, name = "三餐外食", type = TransactionType.EXPENSE, icon = "lunch_dining", isSystem = true)
        )
        val customIconCatId = categoryDao.insert(
            CategoryEntity(parentId = null, name = "烟酒茶", type = TransactionType.EXPENSE, icon = "star", isSystem = true)
        )
        val otherExpenseParentId = categoryDao.insert(
            CategoryEntity(parentId = null, name = "其他支出", type = TransactionType.EXPENSE, icon = "more_horiz", isSystem = true)
        )
        val legacyOtherMiscId = categoryDao.insert(
            CategoryEntity(parentId = otherExpenseParentId, name = "其他杂项", type = TransactionType.EXPENSE, icon = "category", isSystem = true)
        )
        val txId = transactionDao.insert(
            TransactionEntity(
                type = TransactionType.EXPENSE,
                timestamp = 1_760_000_000_000L,
                origAmountMicros = 1_000_000L,
                origCurrency = "CNY",
                accountId = 1L,
                accountAmountMicros = 1_000_000L,
                accountCurrency = "CNY",
                categoryId = legacyOtherMiscId,
                merchant = "测试杂项消费"
            )
        )

        DefaultSeedData.ensurePresetCategories(categoryDao, transactionDao)

        // 旧系统默认 icon 被无损升级；用户手动修改过的 icon 原样保留；其他杂项被清理且交易迁移到其他支出
        assertEquals("ramen_dining", categoryDao.getById(mealsSubId)!!.icon)
        assertEquals("star", categoryDao.getById(customIconCatId)!!.icon)
        assertEquals(null, categoryDao.getById(legacyOtherMiscId))
        assertEquals(otherExpenseParentId, transactionDao.getById(txId)!!.categoryId)
        assertEquals(115, categoryDao.getAll().size)
    }

    @Test
    fun ensureAccountsAndTransitCard_createsOrBackfillsTransitAccountAndAliasesIdempotently() = runTest {
        // 1. Fresh DB
        val freshAccDao = FakeAccountDao()
        val freshAliasDao = FakeAccountAliasDao()
        DefaultSeedData.ensureAccountsAndTransitCard(freshAccDao, freshAliasDao)

        assertTrue(freshAccDao.getAll().any { it.name == "公交卡" && it.groupType == AccountGroup.WALLET })
        assertTrue(freshAliasDao.getAll().any { it.matchType == AliasMatchType.PACKAGE && it.pattern == "com.miui.tsmclient" })
        assertTrue(freshAliasDao.getAll().any { it.matchType == AliasMatchType.KEYWORD && it.pattern == "重庆畅通卡" })

        val aliasCountAfterFirst = freshAliasDao.getAll().size
        DefaultSeedData.ensureAccountsAndTransitCard(freshAccDao, freshAliasDao)
        assertEquals(aliasCountAfterFirst, freshAliasDao.getAll().size)

        // 2. Legacy DB without transit card
        val legacyAccDao = FakeAccountDao()
        val legacyAliasDao = FakeAccountAliasDao()
        legacyAccDao.insert(AccountEntity(name = "微信零钱", groupType = AccountGroup.WALLET, currencyCode = "CNY"))
        DefaultSeedData.ensureAccountsAndTransitCard(legacyAccDao, legacyAliasDao)

        val transitAcc = legacyAccDao.getAll().firstOrNull { it.name == "公交卡" }
        assertNotNull(transitAcc)
        assertEquals(13, legacyAliasDao.getAll().count { it.accountId == transitAcc!!.id })

        // 3. Legacy DB that already has a custom transit card ("重庆畅通卡") and one existing alias
        val customAccDao = FakeAccountDao()
        val customAliasDao = FakeAccountAliasDao()
        val changtongId = customAccDao.insert(AccountEntity(name = "重庆畅通卡", groupType = AccountGroup.WALLET, currencyCode = "CNY"))
        customAliasDao.insert(
            AccountAliasEntity(accountId = changtongId, matchType = AliasMatchType.KEYWORD, pattern = "重庆畅通卡", priority = 110)
        )
        DefaultSeedData.ensureAccountsAndTransitCard(customAccDao, customAliasDao)

        assertEquals(1, customAccDao.getAll().size)
        assertEquals(13, customAliasDao.getAll().count { it.accountId == changtongId })
    }

    @Test
    fun ensurePresetParseRules_addsTransitRulesAndParsesMiWalletNotifications() = runTest {
        val ruleDao = FakeParseRuleDao()
        ruleDao.insert(
            ParseRuleEntity(
                name = "微信支付付款通知",
                sourcePackage = "com.tencent.mm",
                channelType = "NOTIFICATION",
                regexPattern = "(?:微信支付|向)(.*?)付款(?:人民币|RMB)?[￥¥]?([0-9]+(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?)元?",
                priority = 100,
                isBuiltIn = true
            )
        )
        DefaultSeedData.ensurePresetParseRules(ruleDao)
        val rules = ruleDao.getEnabled()
        assertEquals(10, rules.size)
        assertEquals(0, run {
            DefaultSeedData.ensurePresetParseRules(ruleDao)
            ruleDao.getAll().size - 10
        })

        val accDao = FakeAccountDao()
        val aliasDao = FakeAccountAliasDao()
        val catDao = FakeCategoryDao()
        DefaultSeedData.ensureAccountsAndTransitCard(accDao, aliasDao)
        DefaultSeedData.ensurePresetCategories(catDao)

        val parser = LocalRuleParser()
        val miTransitInput = CaptureInput(
            channel = CaptureChannel.NOTIFICATION,
            sourcePackage = "com.miui.tsmclient",
            title = "重庆畅通卡",
            content = "刷卡成功，消费金额2.00元，当前余额48.00元"
        )
        val draft = parser.parse(
            input = miTransitInput,
            rules = rules,
            aliases = aliasDao.getEnabled(),
            accounts = accDao.getAll(),
            categories = catDao.getAll()
        )
        assertNotNull(draft)
        assertEquals(MoneyAmount.parseToMicros("2.00"), draft!!.origAmountMicros)
        assertEquals("CNY", draft.origCurrency)
        assertEquals("公交卡", accDao.getById(draft.accountId!!)!!.name)
        assertTrue(draft.parserUsed.contains("手机钱包/NFC公交卡刷卡扣费通知"))
    }

    @Test
    fun ensurePresetParseRules_migratesLegacyMiWalletRuleNameWithoutDuplication() = runTest {
        val ruleDao = FakeParseRuleDao()
        ruleDao.insert(
            ParseRuleEntity(
                name = "小米钱包/NFC公交卡刷卡扣费通知",
                sourcePackage = "com.miui.tsmclient",
                channelType = "NOTIFICATION",
                regexPattern = ".*",
                isBuiltIn = true
            )
        )
        DefaultSeedData.ensurePresetParseRules(ruleDao)
        val all = ruleDao.getAll()
        assertFalse(all.any { it.name == "小米钱包/NFC公交卡刷卡扣费通知" })
        assertEquals(1, all.count { it.name == "手机钱包/NFC公交卡刷卡扣费通知" })
    }

    @Test
    fun tagTextCodec_andLedgerRepositoryTagCrudWorkCorrectly() = runTest {
        assertEquals(
            listOf("外卖", "拼多多", "刚需", "AA"),
            TagTextCodec.parse("#外卖， #拼多多; 外卖 ；刚需, AA, ")
        )
        assertEquals("外卖,拼多多", TagTextCodec.format(listOf(" 外卖 ", "", "拼多多", "外卖")))
        assertEquals("外卖,拼多多,通勤", TagTextCodec.merge("外卖, 拼多多", "#拼多多；通勤"))

        val categoryDao = FakeCategoryDao()
        val tagDao = FakeTagDao()
        val rateProvider = ExchangeRateRepositoryImpl(
            exchangeRateDao = FakeExchangeRateDao(),
            exchangeRateApi = ExchangeRateApi(OkHttpClient()),
            currentDateProvider = { LocalDate.of(2026, 11, 23) }
        )
        val repo = LedgerRepositoryImpl(
            accountDao = FakeAccountDao(),
            transactionDao = FakeTransactionDao(),
            categoryDao = categoryDao,
            budgetDao = FakeBudgetDao(),
            recurringRuleDao = FakeRecurringRuleDao(),
            exchangeRateProvider = rateProvider,
            tagDao = tagDao
        )

        val (catsAdded, tagsAdded) = repo.ensurePresetCatalogSeeded()
        assertTrue(catsAdded > 0)
        assertEquals(21, tagsAdded)
        assertEquals(0 to 0, repo.ensurePresetCatalogSeeded())

        val id1 = repo.saveTag(TagEntity(name = "手冲咖啡", groupName = "日常", colorHex = 0xFFEA580CL))
        assertTrue(id1 > 0L)
        // Saving with id=0L and existing name updates without duplicating or throwing
        val id2 = repo.saveTag(TagEntity(id = 0L, name = " 手冲咖啡 ", groupName = "饮品", colorHex = 0xFF0D9488L))
        assertEquals(id1, id2)
        val updated = repo.observeTags().first().first { it.id == id1 }
        assertEquals("饮品", updated.groupName)
        assertEquals(0xFF0D9488L, updated.colorHex)

        repo.deleteTag(id1)
        assertTrue(repo.observeTags().first().none { it.id == id1 })
    }

    @Test
    fun ensureDefaultLedger_and_ensureSeeded_doNotResurrectDeletedDefaultLedger() = runTest {
        val ledgerDao = FakeLedgerDao()
        val seededId = DefaultSeedData.ensureDefaultLedger(ledgerDao)
        assertEquals(1L, seededId)
        assertEquals(listOf("默认账本"), ledgerDao.getAll().map { it.name })

        // 模拟用户创建第二个账本后删除了 id == 1L 的默认账本
        val secondId = ledgerDao.insert(LedgerEntity(id = 2L, name = "个人账本", sortOrder = 0))
        ledgerDao.deleteById(seededId)

        val resolvedId = DefaultSeedData.ensureDefaultLedger(ledgerDao)
        assertEquals(secondId, resolvedId)
        assertEquals(listOf(secondId), ledgerDao.getAll().map { it.id })
        assertFalse(ledgerDao.getAll().any { it.name == "默认账本" || it.id == 1L })

        val categoryDao = FakeCategoryDao()
        val tagDao = FakeTagDao()
        val accountDao = FakeAccountDao()
        val accountAliasDao = FakeAccountAliasDao()
        val exchangeRateDao = FakeExchangeRateDao()
        val parseRuleDao = FakeParseRuleDao()
        DefaultSeedData.ensureSeeded(
            ledgerDao = ledgerDao,
            categoryDao = categoryDao,
            tagDao = tagDao,
            accountDao = accountDao,
            accountAliasDao = accountAliasDao,
            exchangeRateDao = exchangeRateDao,
            parseRuleDao = parseRuleDao
        )
        assertEquals(listOf(secondId), ledgerDao.getAll().map { it.id })
        assertFalse(ledgerDao.getAll().any { it.name == "默认账本" })
        assertEquals(21, tagDao.getAll().size)
        assertTrue(tagDao.getAll().all { it.icon.isNotBlank() })
        assertEquals("loyalty", tagDao.getAll().first { it.name == "拼多多" }.icon)
        assertEquals("family_restroom", tagDao.getAll().first { it.name == "家人" }.icon)
    }

    @Test
    fun ensureSeeded_doesNotResurrectDeletedDefaultAccountsOrTransitCard() = runTest {
        val ledgerDao = FakeLedgerDao()
        val categoryDao = FakeCategoryDao()
        val tagDao = FakeTagDao()
        val accountDao = FakeAccountDao()
        val accountAliasDao = FakeAccountAliasDao()
        val exchangeRateDao = FakeExchangeRateDao()
        val parseRuleDao = FakeParseRuleDao()

        // 首次初始化生成默认账户与公交卡
        DefaultSeedData.ensureSeeded(
            ledgerDao = ledgerDao,
            categoryDao = categoryDao,
            tagDao = tagDao,
            accountDao = accountDao,
            accountAliasDao = accountAliasDao,
            exchangeRateDao = exchangeRateDao,
            parseRuleDao = parseRuleDao,
            seedInitialAccounts = true,
            seedTransitCardIfMissing = true
        )
        assertEquals(9, accountDao.getAll().size)

        // 删除公交卡后，若标记已初始化，冷启动绝不复活公交卡
        val transit = accountDao.getAll().first { it.name == "公交卡" }
        accountDao.deleteById(transit.id)
        DefaultSeedData.ensureSeeded(
            ledgerDao = ledgerDao,
            categoryDao = categoryDao,
            tagDao = tagDao,
            accountDao = accountDao,
            accountAliasDao = accountAliasDao,
            exchangeRateDao = exchangeRateDao,
            parseRuleDao = parseRuleDao,
            seedInitialAccounts = false,
            seedTransitCardIfMissing = false
        )
        assertFalse(accountDao.getAll().any { it.name == "公交卡" })

        // 清空全部默认账户后，再次冷启动也绝不复活任何默认账户
        accountDao.clearAll()
        DefaultSeedData.ensureSeeded(
            ledgerDao = ledgerDao,
            categoryDao = categoryDao,
            tagDao = tagDao,
            accountDao = accountDao,
            accountAliasDao = accountAliasDao,
            exchangeRateDao = exchangeRateDao,
            parseRuleDao = parseRuleDao,
            seedInitialAccounts = false,
            seedTransitCardIfMissing = false
        )
        assertTrue(accountDao.getAll().isEmpty())
    }
}

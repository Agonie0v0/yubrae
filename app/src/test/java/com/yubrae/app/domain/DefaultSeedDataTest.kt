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
import com.yubrae.app.data.db.BudgetEntity
import com.yubrae.app.data.db.CaptureChannel
import com.yubrae.app.data.db.CategoryEntity
import com.yubrae.app.data.db.DefaultSeedData
import com.yubrae.app.data.db.InstallmentPlanEntity
import com.yubrae.app.data.db.LedgerEntity
import com.yubrae.app.data.db.ParseRuleEntity
import com.yubrae.app.data.db.RecurringRuleEntity
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
        assertTrue(customExisting.icon.isNotBlank())
        assertFalse(customExisting.icon in all.filter { it.isSystem }.map { it.icon })

        assertEquals("tag_family", all.first { it.id == legacyFamilyId }.icon)
        assertEquals("tag_stockup", all.first { it.id == legacyStockupId }.icon)

        val expectedIcons = mapOf(
            "拼多多" to "tag_pdd",
            "淘宝" to "tag_taobao",
            "京东" to "tag_jd",
            "美团" to "tag_meituan",
            "线下" to "tag_offline",
            "跨境" to "tag_crossborder",
            "通勤" to "tag_commute",
            "差旅" to "tag_businesstrip",
            "旅行" to "tag_travel",
            "大促囤货" to "tag_stockup",
            "刚需" to "tag_essential",
            "分期" to "tag_installment",
            "优惠立减" to "tag_discount",
            "冲动消费" to "tag_impulse",
            "大额" to "tag_large",
            "自己" to "tag_self",
            "家人" to "tag_family",
            "伴侣" to "tag_partner",
            "AA" to "tag_aa",
            "可抵税" to "tag_taxdeduct",
            "装修" to "tag_renovation"
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

        // 4. 全部 115 分类 + 21 标签共计 136 个图标键与卡通绘图全局 100% 唯一、零跨界撞图
        val combinedIcons = catIcons + tagIcons
        assertEquals(136, combinedIcons.toSet().size)
        val combinedVariants = combinedIcons.map { CartoonCategoryIcons.resolveVisualVariantId(it) }.toSet()
        assertEquals(
            "Expected 136 globally unique cartoon drawings across 115 categories + 21 tags, got ${combinedVariants.size}",
            136,
            combinedVariants.size
        )
        assertTrue(
            CartoonCategoryIcons.resolveVisualVariantId("more_horiz") !=
                CartoonCategoryIcons.resolveVisualVariantId("category")
        )
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
        assertEquals("tag_pdd", tagDao.getAll().first { it.name == "拼多多" }.icon)
        assertEquals("tag_family", tagDao.getAll().first { it.name == "家人" }.icon)
    }

    @Test
    fun ensurePresetCategories_mergesYimuSynonymParentsAndRepairsAllSubcategoryAndTagIcons() = runTest {
        val categoryDao = FakeCategoryDao()
        val transactionDao = FakeTransactionDao()
        val budgetDao = FakeBudgetDao()
        val recurringRuleDao = FakeRecurringRuleDao()
        val installmentPlanDao = FakeInstallmentPlanDao()
        val tagDao = FakeTagDao()

        DefaultSeedData.ensurePresetCategories(categoryDao)
        DefaultSeedData.ensurePresetTags(tagDao)

        // 复现旧版导入后产生的同义大类及撞图/默认占位图标子分类
        val impTravelId = categoryDao.insert(
            CategoryEntity(parentId = null, name = "出行交通", type = TransactionType.EXPENSE, icon = "directions_car", colorHex = 0xFF0B57D0L, isSystem = false)
        )
        val impBusId = categoryDao.insert(
            CategoryEntity(parentId = impTravelId, name = "公共交通", type = TransactionType.EXPENSE, icon = "directions_car", colorHex = 0xFF0B57D0L, isSystem = false)
        )

        val impShoppingId = categoryDao.insert(
            CategoryEntity(parentId = null, name = "购物消费", type = TransactionType.EXPENSE, icon = "shopping_bag", colorHex = 0xFFDB2777L, isSystem = false)
        )
        val pinkBagChildNames = listOf(
            "办公用品", "虚拟充值", "手机数码", "个护美妆", "日常家居",
            "生活电器", "配饰腕表", "服务器", "服饰运动", "母婴玩具"
        )
        for (childName in pinkBagChildNames) {
            categoryDao.insert(
                CategoryEntity(
                    parentId = impShoppingId,
                    name = childName,
                    type = TransactionType.EXPENSE,
                    icon = "shopping_bag",
                    colorHex = 0xFFDB2777L,
                    isSystem = false
                )
            )
        }

        val impFoodId = categoryDao.insert(
            CategoryEntity(parentId = null, name = "食品餐饮", type = TransactionType.EXPENSE, icon = "restaurant", colorHex = 0xFFEA580CL, isSystem = false)
        )
        categoryDao.insert(
            CategoryEntity(parentId = impFoodId, name = "早餐", type = TransactionType.EXPENSE, icon = "ramen_dining", colorHex = 0xFFEA580CL, isSystem = false)
        )
        categoryDao.insert(
            CategoryEntity(parentId = impFoodId, name = "晚餐", type = TransactionType.EXPENSE, icon = "ramen_dining", colorHex = 0xFFEA580CL, isSystem = false)
        )
        categoryDao.insert(
            CategoryEntity(parentId = impFoodId, name = "休闲零食", type = TransactionType.EXPENSE, icon = "bakery_dining", colorHex = 0xFFEA580CL, isSystem = false)
        )

        val impHomeId = categoryDao.insert(
            CategoryEntity(parentId = null, name = "居家生活", type = TransactionType.EXPENSE, icon = "home", colorHex = 0xFF0B57D0L, isSystem = false)
        )
        val impElectricId = categoryDao.insert(
            CategoryEntity(parentId = impHomeId, name = "电费", type = TransactionType.EXPENSE, icon = "category", colorHex = 0xFF0B57D0L, isSystem = false)
        )
        val impBroadbandId = categoryDao.insert(
            CategoryEntity(parentId = impHomeId, name = "话费宽带", type = TransactionType.EXPENSE, icon = "category", colorHex = 0xFF0B57D0L, isSystem = false)
        )
        for (childName in listOf("房租还贷", "水费", "物业费", "快递费", "燃气费")) {
            categoryDao.insert(
                CategoryEntity(
                    parentId = impHomeId,
                    name = childName,
                    type = TransactionType.EXPENSE,
                    icon = "category",
                    colorHex = 0xFF0B57D0L,
                    isSystem = false
                )
            )
        }

        val impSocialId = categoryDao.insert(
            CategoryEntity(parentId = null, name = "送礼人情", type = TransactionType.EXPENSE, icon = "card_giftcard", colorHex = 0xFF0B57D0L, isSystem = false)
        )
        val impHealthId = categoryDao.insert(
            CategoryEntity(parentId = null, name = "健康医疗", type = TransactionType.EXPENSE, icon = "medical_services", colorHex = 0xFF0B57D0L, isSystem = false)
        )
        val impOtherId = categoryDao.insert(
            CategoryEntity(parentId = null, name = "其他", type = TransactionType.EXPENSE, icon = "category", colorHex = 0xFF0B57D0L, isSystem = false)
        )

        // 空图标的导入标签
        val impTagMealId = tagDao.insert(TagEntity(name = "聚餐", groupName = "导入", colorHex = 0xFFEC4899L, isSystem = false, icon = ""))
        val impTagOvertimeId = tagDao.insert(TagEntity(name = "加班", groupName = "导入", colorHex = 0xFF0284C7L, isSystem = false, icon = ""))
        val impTagCustomId = tagDao.insert(TagEntity(name = "咖啡续命", groupName = "导入", colorHex = 0xFF2AA876L, isSystem = false, icon = ""))

        // 关联交易、预算、周期记账与分期计划
        val txOnTravelId = transactionDao.insert(
            TransactionEntity(
                type = TransactionType.EXPENSE,
                timestamp = 1_760_000_001_000L,
                origAmountMicros = 2_000_000L,
                origCurrency = "CNY",
                accountId = 1L,
                accountAmountMicros = 2_000_000L,
                accountCurrency = "CNY",
                categoryId = impTravelId
            )
        )
        val txOnBroadbandId = transactionDao.insert(
            TransactionEntity(
                type = TransactionType.EXPENSE,
                timestamp = 1_760_000_002_000L,
                origAmountMicros = 10_000_000L,
                origCurrency = "CNY",
                accountId = 1L,
                accountAmountMicros = 10_000_000L,
                accountCurrency = "CNY",
                categoryId = impBroadbandId
            )
        )
        val txOnElectricId = transactionDao.insert(
            TransactionEntity(
                type = TransactionType.EXPENSE,
                timestamp = 1_760_000_003_000L,
                origAmountMicros = 8_000_000L,
                origCurrency = "CNY",
                accountId = 1L,
                accountAmountMicros = 8_000_000L,
                accountCurrency = "CNY",
                categoryId = impElectricId
            )
        )
        val budgetOnShoppingId = budgetDao.insert(
            BudgetEntity(name = "购物预算", categoryId = impShoppingId, limitAmountMicros = 500_000_000L)
        )
        val budgetOnBroadbandId = budgetDao.insert(
            BudgetEntity(name = "宽带预算", categoryId = impBroadbandId, limitAmountMicros = 20_000_000L)
        )
        val ruleOnOtherId = recurringRuleDao.insert(
            RecurringRuleEntity(
                name = "杂项周期",
                amountMicros = 5_000_000L,
                currencyCode = "CNY",
                accountId = 1L,
                categoryId = impOtherId,
                nextRunEpochMillis = 1_760_100_000_000L
            )
        )
        val planOnSocialId = installmentPlanDao.insert(
            InstallmentPlanEntity(
                accountId = 1L,
                name = "人情分期",
                totalAmountMicros = 100_000_000L,
                totalPeriods = 3,
                nextDueMillis = 1_760_200_000_000L,
                categoryId = impSocialId
            )
        )
        val txOnHealthId = transactionDao.insert(
            TransactionEntity(
                type = TransactionType.EXPENSE,
                timestamp = 1_760_000_004_000L,
                origAmountMicros = 5_000_000L,
                origCurrency = "CNY",
                accountId = 1L,
                accountAmountMicros = 5_000_000L,
                accountCurrency = "CNY",
                categoryId = impHealthId
            )
        )

        DefaultSeedData.ensurePresetCategories(
            categoryDao = categoryDao,
            transactionDao = transactionDao,
            budgetDao = budgetDao,
            recurringRuleDao = recurringRuleDao,
            installmentPlanDao = installmentPlanDao
        )
        DefaultSeedData.ensurePresetTags(tagDao)

        val allCats = categoryDao.getAll()
        val rootCats = allCats.filter { it.parentId == null }
        assertEquals(25, rootCats.size)
        for (synonymName in listOf("出行交通", "购物消费", "食品餐饮", "居家生活", "送礼人情", "健康医疗", "其他")) {
            assertFalse("Synonym parent $synonymName should be merged", rootCats.any { it.name == synonymName })
        }

        val travelParent = rootCats.first { it.name == "交通出行" && it.type == TransactionType.EXPENSE }
        val shoppingParent = rootCats.first { it.name == "日用购物" && it.type == TransactionType.EXPENSE }
        val foodParent = rootCats.first { it.name == "餐饮美食" && it.type == TransactionType.EXPENSE }
        val homeParent = rootCats.first { it.name == "居家物业" && it.type == TransactionType.EXPENSE }
        val socialParent = rootCats.first { it.name == "人情社交" && it.type == TransactionType.EXPENSE }
        val healthParent = rootCats.first { it.name == "医疗健康" && it.type == TransactionType.EXPENSE }
        val otherParent = rootCats.first { it.name == "其他支出" && it.type == TransactionType.EXPENSE }
        val canonicalBroadband = allCats.first { it.parentId == homeParent.id && it.name == "话费宽带" }

        // 无损迁移校验
        assertEquals(travelParent.id, transactionDao.getById(txOnTravelId)!!.categoryId)
        assertEquals(canonicalBroadband.id, transactionDao.getById(txOnBroadbandId)!!.categoryId)
        assertEquals(impElectricId, transactionDao.getById(txOnElectricId)!!.categoryId)
        assertEquals(healthParent.id, transactionDao.getById(txOnHealthId)!!.categoryId)
        assertEquals(shoppingParent.id, budgetDao.getAll().first { it.id == budgetOnShoppingId }.categoryId)
        assertEquals(canonicalBroadband.id, budgetDao.getAll().first { it.id == budgetOnBroadbandId }.categoryId)
        assertEquals(otherParent.id, recurringRuleDao.getAll().first { it.id == ruleOnOtherId }.categoryId)
        assertEquals(socialParent.id, installmentPlanDao.getAll().first { it.id == planOnSocialId }.categoryId)

        // 话费宽带去重（全局仅保留 1 条）
        assertEquals(1, allCats.count { it.name == "话费宽带" && it.type == TransactionType.EXPENSE })
        assertEquals(null, categoryDao.getById(impBroadbandId))
        assertEquals(travelParent.id, categoryDao.getById(impBusId)!!.parentId)

        // 检查所有合并后子分类：无 "category"、无复制父图标、继承父色、同级卡通图案 100% 唯一
        for (parent in listOf(travelParent, shoppingParent, foodParent, homeParent)) {
            val siblings = allCats.filter { it.parentId == parent.id }
            assertTrue(siblings.none { it.icon.isBlank() || it.icon == "category" || it.icon == parent.icon })
            assertTrue(siblings.all { it.colorHex == parent.colorHex })
            val variantIds = siblings.map { CartoonCategoryIcons.resolveVisualVariantId(it.icon) }
            assertEquals(
                "Duplicate sibling variantId under ${parent.name}: ${siblings.map { it.name to it.icon }}",
                siblings.size,
                variantIds.toSet().size
            )
            assertFalse(parent.icon.let { CartoonCategoryIcons.resolveVisualVariantId(it) } in variantIds)
        }

        val expectedSubIcons = mapOf(
            "公共交通" to "directions_bus",
            "办公用品" to "office_folder",
            "虚拟充值" to "virtual_recharge",
            "手机数码" to "smartphone_dev",
            "个护美妆" to "personal_care",
            "日常家居" to "household_lamp",
            "生活电器" to "appliance_washer",
            "配饰腕表" to "wrist_watch",
            "服务器" to "server_rack",
            "服饰运动" to "sport_shoe",
            "母婴玩具" to "baby_rattle",
            "早餐" to "breakfast_egg",
            "晚餐" to "dinner_bowl",
            "休闲零食" to "snack_bag",
            "电费" to "electric_plug",
            "水费" to "water_drop",
            "燃气费" to "propane_tank",
            "物业费" to "property_fee",
            "房租还贷" to "house_contract",
            "快递费" to "local_shipping",
            "话费宽带" to "wifi"
        )
        expectedSubIcons.forEach { (name, expectedIcon) ->
            val cat = allCats.first { it.name == name && it.type == TransactionType.EXPENSE }
            assertEquals("Unexpected icon for $name", expectedIcon, cat.icon)
        }

        // 导入标签自动补全非空图标
        val allTags = tagDao.getAll()
        assertTrue(allTags.all { it.icon.isNotBlank() })
        assertEquals("celebration", allTags.first { it.id == impTagMealId }.icon)
        assertEquals("schedule", allTags.first { it.id == impTagOvertimeId }.icon)
        assertTrue(allTags.first { it.id == impTagCustomId }.icon.isNotBlank())
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

package com.kairoledger.app.domain

import com.kairoledger.app.capture.FakeAccountAliasDao
import com.kairoledger.app.capture.FakeParseRuleDao
import com.kairoledger.app.capture.LocalRuleParser
import com.kairoledger.app.core.contract.CaptureInput
import com.kairoledger.app.core.model.MoneyAmount
import com.kairoledger.app.data.db.AccountAliasEntity
import com.kairoledger.app.data.db.AccountEntity
import com.kairoledger.app.data.db.AccountGroup
import com.kairoledger.app.data.db.AliasMatchType
import com.kairoledger.app.data.db.CaptureChannel
import com.kairoledger.app.data.db.CategoryEntity
import com.kairoledger.app.data.db.DefaultSeedData
import com.kairoledger.app.data.db.ParseRuleEntity
import com.kairoledger.app.data.db.TagEntity
import com.kairoledger.app.data.db.TagTextCodec
import com.kairoledger.app.data.db.TransactionType
import com.kairoledger.app.data.network.ExchangeRateApi
import com.kairoledger.app.data.repository.ExchangeRateRepositoryImpl
import com.kairoledger.app.data.repository.LedgerRepositoryImpl
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
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
    fun ensurePresetTags_seeds28TagsIn4GroupsAndPreservesExisting() = runTest {
        val tagDao = FakeTagDao()
        val existingId = tagDao.insert(
            TagEntity(name = "外卖", groupName = "自定义组", colorHex = 0xFF123456L, sortOrder = 1, isSystem = false)
        )

        val added = DefaultSeedData.ensurePresetTags(tagDao)
        assertEquals(27, added)

        val all = tagDao.getAll()
        assertEquals(28, all.size)
        val existing = all.first { it.name == "外卖" }
        assertEquals(existingId, existing.id)
        assertEquals("自定义组", existing.groupName)

        assertEquals(0, DefaultSeedData.ensurePresetTags(tagDao))
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
        assertTrue(draft.parserUsed.contains("小米钱包/NFC公交卡刷卡扣费通知"))
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
        assertEquals(28, tagsAdded)
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
}

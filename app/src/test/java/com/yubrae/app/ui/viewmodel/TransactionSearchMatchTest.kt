package com.yubrae.app.ui.viewmodel

import com.yubrae.app.data.db.AccountEntity
import com.yubrae.app.data.db.AccountGroup
import com.yubrae.app.data.db.CategoryEntity
import com.yubrae.app.data.db.TransactionEntity
import com.yubrae.app.data.db.TransactionType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransactionSearchMatchTest {

    private val catDining = CategoryEntity(id = 1L, parentId = null, name = "餐饮美食", type = TransactionType.EXPENSE)
    private val catOutEating = CategoryEntity(id = 2L, parentId = 1L, name = "三餐外食", type = TransactionType.EXPENSE)
    private val catBreakfast = CategoryEntity(id = 3L, parentId = 1L, name = "早餐", type = TransactionType.EXPENSE)
    private val catMilkTea = CategoryEntity(id = 4L, parentId = 1L, name = "咖啡奶茶", type = TransactionType.EXPENSE)
    private val catTransport = CategoryEntity(id = 5L, parentId = null, name = "交通出行", type = TransactionType.EXPENSE)
    private val catEnglishParent = CategoryEntity(id = 6L, parentId = null, name = "Entertainment", type = TransactionType.EXPENSE)
    private val catEnglishChild = CategoryEntity(id = 7L, parentId = 6L, name = "CinemaTickets", type = TransactionType.EXPENSE)

    private val categoryById = listOf(
        catDining,
        catOutEating,
        catBreakfast,
        catMilkTea,
        catTransport,
        catEnglishParent,
        catEnglishChild
    ).associateBy { it.id }

    private val accWechat = AccountEntity(id = 10L, name = "微信零钱", groupType = AccountGroup.WALLET, currencyCode = "CNY")
    private val accCmbSavings = AccountEntity(id = 11L, name = "招商储蓄卡", groupType = AccountGroup.DEBIT, currencyCode = "CNY")
    private val accVisa = AccountEntity(id = 12L, name = "Visa Global Card", groupType = AccountGroup.CREDIT, currencyCode = "USD")

    private val accountById = listOf(accWechat, accCmbSavings, accVisa).associateBy { it.id }

    private fun tx(
        id: Long = 1L,
        type: String = TransactionType.EXPENSE,
        accountId: Long = accWechat.id,
        toAccountId: Long? = null,
        categoryId: Long? = null,
        merchant: String = "",
        note: String = "",
        tags: String = ""
    ) = TransactionEntity(
        id = id,
        type = type,
        timestamp = 1_700_000_000_000L,
        origAmountMicros = 2_500_000L,
        origCurrency = "CNY",
        accountId = accountId,
        accountAmountMicros = 2_500_000L,
        accountCurrency = "CNY",
        toAccountId = toAccountId,
        categoryId = categoryId,
        merchant = merchant,
        note = note,
        tags = tags
    )

    @Test
    fun parentCategoryAbbreviation_matchesParentAndChildrenWithoutFalsePositives() {
        val directParentTx = tx(id = 1L, categoryId = catDining.id, merchant = "小馆")
        val outEatingTx = tx(id = 2L, categoryId = catOutEating.id, merchant = "商场门店")
        val breakfastTx = tx(id = 3L, categoryId = catBreakfast.id, merchant = "楼下包子铺")
        val transportTx = tx(id = 4L, categoryId = catTransport.id, merchant = "地铁")

        assertTrue(matchesTransactionSearchQuery(directParentTx, "餐饮", categoryById, accountById))
        assertTrue(matchesTransactionSearchQuery(outEatingTx, "餐饮", categoryById, accountById))
        assertTrue(matchesTransactionSearchQuery(breakfastTx, "餐饮", categoryById, accountById))
        assertFalse(matchesTransactionSearchQuery(transportTx, "餐饮", categoryById, accountById))
    }

    @Test
    fun subcategoryPartialName_matchesCorrespondingSubcategory() {
        val outEatingTx = tx(id = 1L, categoryId = catOutEating.id)
        val milkTeaTx = tx(id = 2L, categoryId = catMilkTea.id)
        val breakfastTx = tx(id = 3L, categoryId = catBreakfast.id)

        assertTrue(matchesTransactionSearchQuery(outEatingTx, "外食", categoryById, accountById))
        assertTrue(matchesTransactionSearchQuery(milkTeaTx, "奶茶", categoryById, accountById))
        assertFalse(matchesTransactionSearchQuery(breakfastTx, "奶茶", categoryById, accountById))
    }

    @Test
    fun caseInsensitiveSearch_matchesEnglishCategoryMerchantAndAccount() {
        val englishCatTx = tx(
            id = 1L,
            accountId = accVisa.id,
            categoryId = catEnglishChild.id,
            merchant = "Starbucks Reserve"
        )

        assertTrue(matchesTransactionSearchQuery(englishCatTx, "entertain", categoryById, accountById))
        assertTrue(matchesTransactionSearchQuery(englishCatTx, "cinematickets", categoryById, accountById))
        assertTrue(matchesTransactionSearchQuery(englishCatTx, "STARBUCKS", categoryById, accountById))
        assertTrue(matchesTransactionSearchQuery(englishCatTx, "visa global", categoryById, accountById))
    }

    @Test
    fun accountNamePartialSearch_matchesPrimaryAndTransferTargetAccounts() {
        val expenseTx = tx(id = 1L, accountId = accWechat.id, categoryId = catBreakfast.id)
        val transferTx = tx(
            id = 2L,
            type = TransactionType.TRANSFER,
            accountId = accWechat.id,
            toAccountId = accCmbSavings.id,
            categoryId = null
        )

        assertTrue(matchesTransactionSearchQuery(expenseTx, "微信", categoryById, accountById))
        assertFalse(matchesTransactionSearchQuery(expenseTx, "储蓄", categoryById, accountById))

        assertTrue(matchesTransactionSearchQuery(transferTx, "微信", categoryById, accountById))
        assertTrue(matchesTransactionSearchQuery(transferTx, "储蓄", categoryById, accountById))
    }

    @Test
    fun uncategorizedSpecialTypes_matchFallbackChineseLabels() {
        val transferTx = tx(
            id = 1L,
            type = TransactionType.TRANSFER,
            accountId = accWechat.id,
            toAccountId = accCmbSavings.id,
            categoryId = null
        )
        val adjustTx = tx(
            id = 2L,
            type = TransactionType.ADJUST,
            accountId = accCmbSavings.id,
            categoryId = null
        )
        val uncategorizedExpenseTx = tx(
            id = 3L,
            type = TransactionType.EXPENSE,
            accountId = accWechat.id,
            categoryId = null
        )

        assertTrue(matchesTransactionSearchQuery(transferTx, "转账", categoryById, accountById))
        assertFalse(matchesTransactionSearchQuery(transferTx, "校准", categoryById, accountById))

        assertTrue(matchesTransactionSearchQuery(adjustTx, "校准", categoryById, accountById))
        assertTrue(matchesTransactionSearchQuery(adjustTx, "余额校准", categoryById, accountById))
        assertFalse(matchesTransactionSearchQuery(adjustTx, "转账", categoryById, accountById))

        assertFalse(matchesTransactionSearchQuery(uncategorizedExpenseTx, "转账", categoryById, accountById))
        assertFalse(matchesTransactionSearchQuery(uncategorizedExpenseTx, "校准", categoryById, accountById))
    }

    @Test
    fun merchantNoteTagsAndBoundaryQueries_behaveCorrectly() {
        val sampleTx = tx(
            id = 1L,
            accountId = accWechat.id,
            categoryId = catBreakfast.id,
            merchant = "麦当劳",
            note = "加班早饭报销凭证",
            tags = "差旅,公司"
        )

        assertTrue(matchesTransactionSearchQuery(sampleTx, "麦当", categoryById, accountById))
        assertTrue(matchesTransactionSearchQuery(sampleTx, "报销", categoryById, accountById))
        assertTrue(matchesTransactionSearchQuery(sampleTx, "差旅", categoryById, accountById))
        assertTrue(matchesTransactionSearchQuery(sampleTx, "", categoryById, accountById))
        assertTrue(matchesTransactionSearchQuery(sampleTx, "   \t\n ", categoryById, accountById))
        assertTrue(matchesTransactionSearchQuery(sampleTx, "  麦当劳  ", categoryById, accountById))
        assertFalse(matchesTransactionSearchQuery(sampleTx, "不存在的关键字", categoryById, accountById))
    }
}

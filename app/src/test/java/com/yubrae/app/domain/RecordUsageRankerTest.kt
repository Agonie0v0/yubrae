package com.yubrae.app.domain

import com.yubrae.app.data.db.CategoryEntity
import com.yubrae.app.data.db.TagEntity
import com.yubrae.app.data.db.TransactionEntity
import com.yubrae.app.data.db.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordUsageRankerTest {

    private val now = 1_800_000_000_000L
    private val dayMs = 86_400_000L

    private fun sampleCategories(): List<CategoryEntity> = listOf(
        CategoryEntity(id = 1L, parentId = null, name = "餐饮美食", type = TransactionType.EXPENSE, icon = "restaurant", colorHex = 0xFFEA580CL, sortOrder = 10),
        CategoryEntity(id = 2L, parentId = 1L, name = "外卖", type = TransactionType.EXPENSE, icon = "delivery_dining", colorHex = 0xFFEA580CL, sortOrder = 11),
        CategoryEntity(id = 3L, parentId = 1L, name = "咖啡茶饮", type = TransactionType.EXPENSE, icon = "local_cafe", colorHex = 0xFFEA580CL, sortOrder = 12),
        CategoryEntity(id = 4L, parentId = null, name = "交通出行", type = TransactionType.EXPENSE, icon = "directions_car", colorHex = 0xFF0284C7L, sortOrder = 20),
        CategoryEntity(id = 5L, parentId = 4L, name = "地铁公交", type = TransactionType.EXPENSE, icon = "subway", colorHex = 0xFF0284C7L, sortOrder = 21),
        CategoryEntity(id = 6L, parentId = null, name = "日用购物", type = TransactionType.EXPENSE, icon = "shopping_bag", colorHex = 0xFFDB2777L, sortOrder = 30),
        CategoryEntity(id = 7L, parentId = null, name = "居家物业", type = TransactionType.EXPENSE, icon = "home", colorHex = 0xFF0D9488L, sortOrder = 40),
        CategoryEntity(id = 8L, parentId = null, name = "休闲娱乐", type = TransactionType.EXPENSE, icon = "sports_esports", colorHex = 0xFF9333EAL, sortOrder = 50),
        CategoryEntity(id = 9L, parentId = null, name = "工资薪金", type = TransactionType.INCOME, icon = "work", colorHex = 0xFF16A34AL, sortOrder = 10)
    )

    private fun tx(
        id: Long,
        type: String = TransactionType.EXPENSE,
        categoryId: Long? = null,
        tags: String = "",
        daysAgo: Long = 1L
    ): TransactionEntity = TransactionEntity(
        id = id,
        type = type,
        timestamp = now - daysAgo * dayMs,
        origAmountMicros = 100_000L,
        origCurrency = "CNY",
        accountId = 1L,
        accountAmountMicros = 100_000L,
        accountCurrency = "CNY",
        categoryId = categoryId,
        tags = tags
    )

    @Test
    fun rankFrequentCategories_surfacesChildCategoriesWithRecencyDecayAndBackfillsParents() {
        val cats = sampleCategories()
        val txs = listOf(
            // 二级「咖啡茶饮」近3天用过3次，应排第一
            tx(1L, categoryId = 3L, daysAgo = 1L),
            tx(2L, categoryId = 3L, daysAgo = 2L),
            tx(3L, categoryId = 3L, daysAgo = 3L),
            // 二级「地铁公交」15天前用过2次，应排第二
            tx(4L, categoryId = 5L, daysAgo = 15L),
            tx(5L, categoryId = 5L, daysAgo = 20L),
            // 一级「日用购物」120天前用过1次，权重较低
            tx(6L, categoryId = 6L, daysAgo = 120L)
        )

        val frequent = RecordUsageRanker.rankFrequentCategories(
            type = TransactionType.EXPENSE,
            categories = cats,
            transactions = txs,
            nowMillis = now,
            limit = 6
        )

        assertEquals(3L, frequent[0].id)
        assertEquals(5L, frequent[1].id)
        assertEquals(6L, frequent[2].id)
        // 因历史仅 3 个 (<5)，会按 sortOrder 补足一级大类「餐饮美食」「交通出行」到至少 5 个
        assertTrue(frequent.size >= 5)
        assertTrue(frequent.none { it.type == TransactionType.INCOME })
    }

    @Test
    fun searchCategories_matchesChildAndParentWithPrefixPriority() {
        val cats = sampleCategories()
        val results = RecordUsageRanker.searchCategories("咖啡", TransactionType.EXPENSE, cats)
        assertEquals(1, results.size)
        assertEquals("咖啡茶饮", results.first().category.name)
        assertEquals("餐饮美食", results.first().parent?.name)

        val parentMatches = RecordUsageRanker.searchCategories("餐饮", TransactionType.EXPENSE, cats)
        assertTrue(parentMatches.any { it.category.name == "餐饮美食" && it.parent == null })
        assertTrue(parentMatches.any { it.category.name == "外卖" && it.parent?.name == "餐饮美食" })

        assertTrue(RecordUsageRanker.hasExactCategoryName(" 外卖 ", TransactionType.EXPENSE, cats))
        assertFalse(RecordUsageRanker.hasExactCategoryName("奶茶", TransactionType.EXPENSE, cats))
    }

    @Test
    fun buildTagBrowserData_mergesHistoricalTagsSupportsGroupFilterAndQuickCreate() {
        val presetTags = listOf(
            TagEntity(id = 1L, name = "外卖", groupName = "渠道平台", colorHex = 0xFFEA580CL, sortOrder = 10, icon = "delivery_dining"),
            TagEntity(id = 2L, name = "通勤", groupName = "场景行程", colorHex = 0xFF0369A1L, sortOrder = 20, icon = "subway"),
            TagEntity(id = 3L, name = "刚需", groupName = "消费属性", colorHex = 0xFF16A34AL, sortOrder = 30, icon = "verified")
        )
        val txs = listOf(
            tx(1L, tags = "外卖,手冲咖啡", daysAgo = 1L),
            tx(2L, tags = "外卖,通勤", daysAgo = 5L)
        )

        val allData = RecordUsageRanker.buildTagBrowserData(
            query = "",
            selectedGroup = null,
            availableTags = presetTags,
            selectedTagNames = listOf("手冲咖啡"),
            allTransactions = txs,
            nowMillis = now
        )

        assertTrue(allData.groups.contains(RecordUsageRanker.TAG_TAB_ALL))
        assertTrue(allData.groups.contains(RecordUsageRanker.TAG_TAB_FREQUENT))
        assertTrue(allData.groups.contains("渠道平台"))
        assertTrue(allData.groups.contains("自定义"))
        assertEquals("外卖", allData.frequentTags.first().name)
        assertTrue(allData.flatFiltered.any { it.name == "手冲咖啡" && it.groupName == "自定义" })
        assertFalse(allData.canQuickCreate)

        val searchNew = RecordUsageRanker.buildTagBrowserData(
            query = "瑞幸生椰",
            selectedGroup = RecordUsageRanker.TAG_TAB_ALL,
            availableTags = presetTags,
            selectedTagNames = emptyList(),
            allTransactions = txs,
            nowMillis = now
        )
        assertTrue(searchNew.canQuickCreate)
        assertTrue(searchNew.flatFiltered.isEmpty())

        val searchExisting = RecordUsageRanker.buildTagBrowserData(
            query = "外卖",
            selectedGroup = "渠道平台",
            availableTags = presetTags,
            selectedTagNames = emptyList(),
            allTransactions = txs,
            nowMillis = now
        )
        assertFalse(searchExisting.canQuickCreate)
        assertEquals(listOf("外卖"), searchExisting.flatFiltered.map { it.name })
    }

    @Test
    fun suggestCategoryStyle_infersIconAndInheritsParentColor() {
        val parent = CategoryEntity(id = 1L, name = "餐饮美食", type = TransactionType.EXPENSE, icon = "restaurant", colorHex = 0xFF123456L)
        val (icon1, color1) = RecordUsageRanker.suggestCategoryStyle("瑞幸咖啡", parent)
        assertEquals("local_cafe", icon1)
        assertEquals(0xFF123456L, color1)

        val (icon2, color2) = RecordUsageRanker.suggestCategoryStyle("宠物驱虫", null)
        assertEquals("pets", icon2)
        assertEquals(0xFFD97706L, color2)
    }
}

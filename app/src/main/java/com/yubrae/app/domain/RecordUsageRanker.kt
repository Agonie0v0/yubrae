package com.yubrae.app.domain

import com.yubrae.app.data.db.CategoryEntity
import com.yubrae.app.data.db.TagEntity
import com.yubrae.app.data.db.TagTextCodec
import com.yubrae.app.data.db.TransactionEntity
import java.util.Locale
import kotlin.math.abs

data class CategorySearchResult(
    val category: CategoryEntity,
    val parent: CategoryEntity?
)

data class TagDisplayItem(
    val name: String,
    val groupName: String,
    val colorHex: Long,
    val icon: String,
    val usageCount: Int
)

data class TagGroupSection(
    val groupName: String,
    val tags: List<TagDisplayItem>
)

data class TagBrowserData(
    val groups: List<String>,
    val frequentTags: List<TagDisplayItem>,
    val sections: List<TagGroupSection>,
    val flatFiltered: List<TagDisplayItem>,
    val canQuickCreate: Boolean
)

/**
 * 记账高频智能排序、分类/标签搜索与就地新建默认样式推荐引擎。
 * 纯 Kotlin 逻辑，无 Android 框架依赖，同时服务 Android 手动记账、自动悬浮弹窗与 JVM 单测。
 */
object RecordUsageRanker {

    const val TAG_TAB_ALL = "全部"
    const val TAG_TAB_FREQUENT = "常用"

    val CANONICAL_TAG_GROUPS: List<String> = listOf(
        "渠道平台",
        "场景行程",
        "消费属性",
        "对象账务",
        "自定义"
    )

    private const val DAY_MILLIS = 86_400_000L

    private val FALLBACK_TAG_PALETTE: List<Long> = listOf(
        0xFF2AA876L,
        0xFF006874L,
        0xFF0284C7L,
        0xFF1677FFL,
        0xFF7C3AEDL,
        0xFFDB2777L,
        0xFFE11D48L,
        0xFFEA580CL,
        0xFFD97706L,
        0xFF0D9488L
    )

    /**
     * 按时间衰减加权计算常用分类（支持直接推荐二级细分分类以实现 1-tap 直达）。
     * 当历史交易覆盖不足 5 个时，按 sortOrder 自动补足一级分类。
     */
    fun rankFrequentCategories(
        type: String,
        categories: List<CategoryEntity>,
        transactions: List<TransactionEntity>,
        nowMillis: Long = System.currentTimeMillis(),
        limit: Int = 8
    ): List<CategoryEntity> {
        if (limit <= 0) return emptyList()
        val typed = categories
            .filter { it.type.equals(type, ignoreCase = true) }
            .sortedWith(compareBy<CategoryEntity> { it.sortOrder }.thenBy { it.id })
        if (typed.isEmpty()) return emptyList()

        val catById = typed.associateBy { it.id }
        val scores = HashMap<Long, Double>()
        val latestUsedAt = HashMap<Long, Long>()

        for (tx in transactions) {
            if (!tx.type.equals(type, ignoreCase = true)) continue
            val catId = tx.categoryId ?: continue
            if (catId !in catById) continue
            val ageDays = ((nowMillis - tx.timestamp).coerceAtLeast(0L)) / DAY_MILLIS
            val weight = when {
                ageDays <= 7L -> 4.0
                ageDays <= 30L -> 2.5
                ageDays <= 90L -> 1.5
                else -> 0.5
            }
            scores[catId] = (scores[catId] ?: 0.0) + weight
            val prevTs = latestUsedAt[catId] ?: Long.MIN_VALUE
            if (tx.timestamp > prevTs) {
                latestUsedAt[catId] = tx.timestamp
            }
        }

        val ranked = scores.keys
            .mapNotNull { id -> catById[id] }
            .sortedWith(
                compareByDescending<CategoryEntity> { scores[it.id] ?: 0.0 }
                    .thenByDescending { latestUsedAt[it.id] ?: 0L }
                    .thenBy { it.sortOrder }
                    .thenBy { it.id }
            )
            .take(limit)
            .toMutableList()

        val minTarget = minOf(limit, typed.size)
        if (ranked.size < 5 && ranked.size < minTarget) {
            val seen = ranked.mapTo(HashSet()) { it.id }
            val parents = typed.filter { it.parentId == null }
            for (parent in parents) {
                if (ranked.size >= minTarget) break
                if (seen.add(parent.id)) {
                    ranked.add(parent)
                }
            }
            if (ranked.size < minTarget) {
                for (cat in typed) {
                    if (ranked.size >= minTarget) break
                    if (seen.add(cat.id)) {
                        ranked.add(cat)
                    }
                }
            }
        }
        return ranked
    }

    /**
     * 实时搜索分类：支持命中分类名称及父级名称，前缀命中优先于包含命中。
     */
    fun searchCategories(
        query: String,
        type: String,
        categories: List<CategoryEntity>
    ): List<CategorySearchResult> {
        val needle = query.trim().lowercase(Locale.ROOT)
        if (needle.isEmpty()) return emptyList()

        val typed = categories
            .filter { it.type.equals(type, ignoreCase = true) }
            .sortedWith(compareBy<CategoryEntity> { it.sortOrder }.thenBy { it.id })
        val byId = typed.associateBy { it.id }

        val matches = mutableListOf<Pair<Int, CategorySearchResult>>()
        for (cat in typed) {
            val parent = cat.parentId?.let { byId[it] }
            val nameLower = cat.name.lowercase(Locale.ROOT)
            val parentLower = parent?.name?.lowercase(Locale.ROOT).orEmpty()
            val score = when {
                nameLower == needle -> 0
                nameLower.startsWith(needle) -> 1
                nameLower.contains(needle) -> 2
                parentLower.startsWith(needle) -> 3
                parentLower.contains(needle) -> 4
                else -> -1
            }
            if (score >= 0) {
                matches.add(score to CategorySearchResult(category = cat, parent = parent))
            }
        }

        return matches
            .sortedWith(
                compareBy<Pair<Int, CategorySearchResult>> { it.first }
                    .thenBy { if (it.second.parent == null) 0 else 1 }
                    .thenBy { it.second.category.sortOrder }
                    .thenBy { it.second.category.id }
            )
            .map { it.second }
    }

    fun hasExactCategoryName(
        name: String,
        type: String,
        categories: List<CategoryEntity>
    ): Boolean {
        val normalized = name.trim()
        if (normalized.isEmpty()) return false
        return categories.any {
            it.type.equals(type, ignoreCase = true) &&
                it.name.trim().equals(normalized, ignoreCase = true)
        }
    }

    /**
     * 汇总预置标签、已选标签与历史流水孤立标签，返回分组、常用排行、搜索过滤结果及一键新建标志。
     */
    fun buildTagBrowserData(
        query: String,
        selectedGroup: String?,
        availableTags: List<TagEntity>,
        selectedTagNames: List<String>,
        allTransactions: List<TransactionEntity>,
        nowMillis: Long = System.currentTimeMillis(),
        frequentLimit: Int = 8
    ): TagBrowserData {
        val sortedEntities = availableTags.sortedWith(compareBy<TagEntity> { it.sortOrder }.thenBy { it.id })
        val entityByName = LinkedHashMap<String, TagEntity>(sortedEntities.size)
        for (tag in sortedEntities) {
            val clean = tag.name.trim()
            if (clean.isNotEmpty() && !entityByName.containsKey(clean)) {
                entityByName[clean] = tag
            }
        }

        val usageCounts = HashMap<String, Int>()
        val decayedScores = HashMap<String, Double>()
        val historicalOrder = LinkedHashSet<String>()

        for (tx in allTransactions) {
            if (tx.tags.isBlank()) continue
            val ageDays = ((nowMillis - tx.timestamp).coerceAtLeast(0L)) / DAY_MILLIS
            val weight = when {
                ageDays <= 7L -> 4.0
                ageDays <= 30L -> 2.5
                ageDays <= 90L -> 1.5
                else -> 0.5
            }
            for (tagName in TagTextCodec.parse(tx.tags)) {
                historicalOrder.add(tagName)
                usageCounts[tagName] = (usageCounts[tagName] ?: 0) + 1
                decayedScores[tagName] = (decayedScores[tagName] ?: 0.0) + weight
            }
        }

        val orderedNames = LinkedHashSet<String>()
        sortedEntities.forEach { orderedNames.add(it.name.trim()) }
        selectedTagNames.forEach { clean -> if (clean.isNotBlank()) orderedNames.add(clean.trim()) }
        orderedNames.addAll(historicalOrder)

        val allItems = orderedNames.mapNotNull { rawName ->
            val clean = rawName.trim()
            if (clean.isEmpty()) return@mapNotNull null
            val entity = entityByName[clean]
            TagDisplayItem(
                name = clean,
                groupName = entity?.groupName?.trim()?.ifBlank { "自定义" } ?: "自定义",
                colorHex = entity?.colorHex ?: stableFallbackColorHex(clean),
                icon = entity?.icon.orEmpty(),
                usageCount = usageCounts[clean] ?: 0
            )
        }

        val frequentTags = allItems
            .filter { (decayedScores[it.name] ?: 0.0) > 0.0 || it.usageCount > 0 }
            .sortedWith(
                compareByDescending<TagDisplayItem> { decayedScores[it.name] ?: 0.0 }
                    .thenByDescending { it.usageCount }
                    .thenBy { it.name }
            )
            .take(frequentLimit)

        val byGroup = allItems.groupBy { it.groupName }
        val orderedGroupNames = mutableListOf<String>()
        for (group in CANONICAL_TAG_GROUPS) {
            if (!byGroup[group].isNullOrEmpty()) {
                orderedGroupNames.add(group)
            }
        }
        for (group in byGroup.keys) {
            if (group !in CANONICAL_TAG_GROUPS && !byGroup[group].isNullOrEmpty()) {
                orderedGroupNames.add(group)
            }
        }

        val tabs = buildList {
            add(TAG_TAB_ALL)
            if (frequentTags.isNotEmpty()) add(TAG_TAB_FREQUENT)
            addAll(orderedGroupNames)
        }

        val needle = query.trim().lowercase(Locale.ROOT)
        val canQuickCreate = query.trim().isNotEmpty() &&
            allItems.none { it.name.equals(query.trim(), ignoreCase = true) }

        val activeGroup = selectedGroup?.takeIf { it in tabs } ?: TAG_TAB_ALL
        val baseScope: List<TagDisplayItem> = when (activeGroup) {
            TAG_TAB_ALL -> allItems
            TAG_TAB_FREQUENT -> frequentTags
            else -> byGroup[activeGroup].orEmpty()
        }

        val filtered = if (needle.isEmpty()) {
            baseScope
        } else {
            // 搜索时若用户选中了“全部”，跨全量匹配；否则在当前分栏优先搜索，若为空也保留精准定位体验
            baseScope.filter {
                it.name.lowercase(Locale.ROOT).contains(needle) ||
                    it.groupName.lowercase(Locale.ROOT).contains(needle)
            }.sortedWith(
                compareBy<TagDisplayItem> {
                    val lower = it.name.lowercase(Locale.ROOT)
                    when {
                        lower == needle -> 0
                        lower.startsWith(needle) -> 1
                        lower.contains(needle) -> 2
                        else -> 3
                    }
                }.thenByDescending { it.usageCount }
            )
        }

        val sections: List<TagGroupSection> = when {
            activeGroup == TAG_TAB_FREQUENT -> {
                if (filtered.isEmpty()) emptyList() else listOf(TagGroupSection(TAG_TAB_FREQUENT, filtered))
            }
            activeGroup != TAG_TAB_ALL -> {
                if (filtered.isEmpty()) emptyList() else listOf(TagGroupSection(activeGroup, filtered))
            }
            else -> {
                val filteredByGroup = filtered.groupBy { it.groupName }
                orderedGroupNames.mapNotNull { group ->
                    val list = filteredByGroup[group]
                    if (list.isNullOrEmpty()) null else TagGroupSection(group, list)
                }
            }
        }

        return TagBrowserData(
            groups = tabs,
            frequentTags = frequentTags,
            sections = sections,
            flatFiltered = filtered,
            canQuickCreate = canQuickCreate
        )
    }

    /**
     * 根据分类名称与所属父级智能推荐卡通图标与色彩。
     */
    fun suggestCategoryStyle(
        name: String,
        parent: CategoryEntity? = null
    ): Pair<String, Long> {
        val text = name.trim().lowercase(Locale.ROOT)
        if (text.isNotEmpty()) {
            val matched = KEYWORD_STYLE_RULES.firstOrNull { rule ->
                rule.keywords.any { kw -> text.contains(kw) }
            }
            if (matched != null) {
                return matched.icon to (parent?.colorHex ?: matched.colorHex)
            }
        }
        if (parent != null) {
            return parent.icon to parent.colorHex
        }
        val color = if (text.isEmpty()) 0xFF2AA876L else stableFallbackColorHex(text)
        return "category" to color
    }

    fun stableFallbackColorHex(label: String): Long {
        val idx = abs(label.hashCode()) % FALLBACK_TAG_PALETTE.size
        return FALLBACK_TAG_PALETTE[idx]
    }

    private data class StyleRule(
        val keywords: List<String>,
        val icon: String,
        val colorHex: Long
    )

    private val KEYWORD_STYLE_RULES: List<StyleRule> = listOf(
        StyleRule(listOf("咖啡", "奶茶", "茶饮", "拿铁", "瑞幸", "星巴克", "coffee", "cafe"), "local_cafe", 0xFFEA580CL),
        StyleRule(listOf("外卖", "点餐", "美团", "饿了么"), "delivery_dining", 0xFFEA580CL),
        StyleRule(listOf("餐", "饭", "吃", "火锅", "烧烤", "早餐", "午餐", "晚餐", "夜宵", "面"), "restaurant", 0xFFEA580CL),
        StyleRule(listOf("甜品", "蛋糕", "零食", "面包", "烘焙", "冰淇淋"), "bakery_dining", 0xFFEA580CL),
        StyleRule(listOf("水果", "买菜", "生鲜", "菜市场"), "local_grocery_store", 0xFF16A34AL),
        StyleRule(listOf("地铁", "公交", "巴士", "轻轨", "通勤"), "subway", 0xFF0284C7L),
        StyleRule(listOf("打车", "滴滴", "出租", "网约车"), "local_taxi", 0xFF0284C7L),
        StyleRule(listOf("加油", "充电", "汽车", "停车", "高速", "养车", "车"), "directions_car", 0xFF0284C7L),
        StyleRule(listOf("高铁", "火车", "机票", "飞机", "酒店", "民宿", "旅行", "旅游", "出差"), "flight", 0xFF2563EBL),
        StyleRule(listOf("猫", "狗", "宠", "猫粮", "狗粮"), "pets", 0xFFD97706L),
        StyleRule(listOf("药", "医", "挂号", "医院", "体检", "牙", "诊"), "medical_services", 0xFFDC2626L),
        StyleRule(listOf("水电", "燃气", "电费", "水费", "房租", "房贷", "物业", "宽带", "话费"), "home", 0xFF0D9488L),
        StyleRule(listOf("游戏", "电影", "演出", "ktv", "娱乐", "健身", "运动"), "sports_esports", 0xFF9333EAL),
        StyleRule(listOf("订阅", "会员", "云", "ai", "gpt", "音乐", "视频"), "subscriptions", 0xFF7C3AEDL),
        StyleRule(listOf("书", "课", "学习", "培训", "考试", "文具"), "school", 0xFF1677FFL),
        StyleRule(listOf("红包", "礼金", "送礼", "礼物", "请客", "约会"), "card_giftcard", 0xFFEC4899L),
        StyleRule(listOf("衣", "鞋", "包", "美妆", "护肤", "理发", "美容"), "checkroom", 0xFFE11D48L),
        StyleRule(listOf("数码", "手机", "电脑", "耳机", "家电"), "devices", 0xFF4F46E5L),
        StyleRule(listOf("工资", "薪资", "奖金", "补贴", "兼职", "副业"), "work", 0xFF16A34AL),
        StyleRule(listOf("理财", "基金", "股票", "利息", "分红", "收益"), "trending_up", 0xFF0D9488L),
        StyleRule(listOf("报销", "退款", "理赔"), "replay", 0xFF0284C7L)
    )
}

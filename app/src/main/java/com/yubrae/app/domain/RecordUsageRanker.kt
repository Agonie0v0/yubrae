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
     * 按语义优先级返回分类的候选卡通图标键列表（去重且不含 "category" 兜底键），用于同级防撞图挑选。
     */
    fun suggestCategoryIconCandidates(name: String): List<String> {
        val text = name.trim().lowercase(Locale.ROOT)
        if (text.isEmpty()) return emptyList()
        return KEYWORD_STYLE_RULES
            .asSequence()
            .filter { rule -> rule.keywords.any { kw -> text.contains(kw) } }
            .map { it.icon }
            .filter { it != "category" }
            .distinct()
            .toList()
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

    /**
     * 根据标签名称智能推荐专属标签卡通图标与主题色（始终返回有效图标，不与分类撞图）。
     */
    fun suggestTagStyle(name: String): Pair<String, Long> {
        val text = name.trim().lowercase(Locale.ROOT)
        if (text.isNotEmpty()) {
            val matched = TAG_STYLE_RULES.firstOrNull { rule ->
                rule.keywords.any { kw -> text.contains(kw) }
            }
            if (matched != null) {
                return matched.icon to matched.colorHex
            }
        }
        val fallbackPool = TAG_FALLBACK_ICONS
        val icon = if (text.isEmpty()) {
            fallbackPool.first()
        } else {
            fallbackPool[abs(text.hashCode()) % fallbackPool.size]
        }
        val color = if (text.isEmpty()) 0xFF2AA876L else stableFallbackColorHex(text)
        return icon to color
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

    private val TAG_FALLBACK_ICONS: List<String> = listOf(
        "tag_essential", "tag_discount", "tag_commute", "tag_travel", "tag_self",
        "tag_offline", "sell", "bookmark", "star", "flag"
    )

    private val TAG_STYLE_RULES: List<StyleRule> = listOf(
        StyleRule(listOf("拼多多", "拼单", "百亿补贴"), "tag_pdd", 0xFFE11D48L),
        StyleRule(listOf("淘宝", "天猫", "闲鱼"), "tag_taobao", 0xFFD97706L),
        StyleRule(listOf("京东", "京喜"), "tag_jd", 0xFFDC2626L),
        StyleRule(listOf("美团", "饿了么", "外卖", "闪购"), "tag_meituan", 0xFFF59E0BL),
        StyleRule(listOf("线下", "门店", "实体", "堂食"), "tag_offline", 0xFF0D9488L),
        StyleRule(listOf("跨境", "海淘", "免税", "进口", "关税"), "tag_crossborder", 0xFF4F46E5L),
        StyleRule(listOf("通勤", "地铁", "公交", "早晚高峰"), "tag_commute", 0xFF0369A1L),
        StyleRule(listOf("差旅", "出差", "商旅", "报销单"), "tag_businesstrip", 0xFF2563EBL),
        StyleRule(listOf("旅行", "旅游", "度假", "游玩", "打卡"), "tag_travel", 0xFF0F766EL),
        StyleRule(listOf("囤货", "大促", "双11", "双十一", "618"), "tag_stockup", 0xFF9333EAL),
        StyleRule(listOf("刚需", "日常", "必需", "固定"), "tag_essential", 0xFF16A34AL),
        StyleRule(listOf("分期", "白条", "花呗", "月供", "免息"), "tag_installment", 0xFF64748BL),
        StyleRule(listOf("立减", "优惠", "满减", "神券", "折扣", "返现"), "tag_discount", 0xFF15803DL),
        StyleRule(listOf("冲动", "种草", "剁手", "氪金"), "tag_impulse", 0xFFE11D48L),
        StyleRule(listOf("大额", "贵重", "重磅"), "tag_large", 0xFFDC2626L),
        StyleRule(listOf("自己", "个人", "私用"), "tag_self", 0xFF2AA876L),
        StyleRule(listOf("家人", "父母", "家里", "家庭", "父母家"), "tag_family", 0xFFEA580CL),
        StyleRule(listOf("伴侣", "对象", "恋爱", "纪念日", "约会"), "tag_partner", 0xFFEC4899L),
        StyleRule(listOf("aa", "均摊", "平摊", "分摊"), "tag_aa", 0xFF0284C7L),
        StyleRule(listOf("抵税", "退税", "发票", "专项扣除"), "tag_taxdeduct", 0xFF4F46E5L),
        StyleRule(listOf("装修", "家装", "硬装", "软装", "改造"), "tag_renovation", 0xFF0D9488L),
        StyleRule(listOf("报销", "公费"), "receipt_long", 0xFF0284C7L),
        StyleRule(listOf("订阅", "会员", "续费"), "subscriptions", 0xFF7C3AEDL),
        StyleRule(listOf("攒钱", "储蓄"), "savings", 0xFF16A34AL),
        StyleRule(listOf("退款", "售后"), "replay", 0xFF0EA5E9L),
        StyleRule(listOf("聚餐", "聚会", "团建", "宴请"), "celebration", 0xFFEC4899L),
        StyleRule(listOf("网购", "电商"), "shopping_cart", 0xFFDB2777L),
        StyleRule(listOf("加班", "工作"), "schedule", 0xFF475569L),
        StyleRule(listOf("宠物", "猫", "狗"), "pets", 0xFFD97706L),
        StyleRule(listOf("孩子", "宝宝", "娃"), "child_care", 0xFFF59E0BL),
        StyleRule(listOf("心愿", "清单", "礼物"), "auto_awesome", 0xFF9333EAL)
    )

    private val KEYWORD_STYLE_RULES: List<StyleRule> = listOf(
        // 高特异复合词优先，避免被单字通用规则拦截
        StyleRule(listOf("宠物医疗", "宠物医院", "驱虫", "绝育"), "healing", 0xFFD97706L),
        StyleRule(listOf("猫粮", "狗粮", "宠物食品", "宠物零食"), "pet_food", 0xFFD97706L),
        StyleRule(listOf("猫砂", "宠物用品", "狗绳", "猫爬架", "宠物玩具"), "pet_supplies", 0xFFD97706L),
        StyleRule(listOf("母婴玩具", "母婴", "婴儿玩具", "摇铃"), "baby_rattle", 0xFFF59E0BL),
        StyleRule(listOf("早教", "托育", "幼儿园"), "early_edu", 0xFFF59E0BL),
        StyleRule(listOf("奶粉", "辅食", "尿不湿", "婴儿"), "child_care", 0xFFF59E0BL),
        StyleRule(listOf("童装", "玩具", "绘本", "积木"), "toys", 0xFFF59E0BL),
        StyleRule(listOf("亲子", "育儿", "孩子", "宝宝"), "stroller", 0xFFF59E0BL),
        StyleRule(listOf("购物退款", "退款", "退货"), "refund_parcel", 0xFF0284C7L),
        StyleRule(listOf("公司报销", "报销"), "receipt_long", 0xFF0284C7L),
        StyleRule(listOf("保险理赔", "理赔", "赔付"), "request_quote", 0xFF0284C7L),
        StyleRule(listOf("商业保险", "重疾", "意外险", "寿险", "车险"), "verified_user", 0xFF64748BL),
        StyleRule(listOf("医疗保险", "医保", "社保"), "health_and_safety", 0xFFDC2626L),
        StyleRule(listOf("餐补", "交通补", "房补", "消费补贴", "补贴"), "commute_allowance", 0xFF15803DL),
        StyleRule(listOf("自媒体稿费", "稿费", "自媒体", "创作"), "mic", 0xFF2563EBL),
        StyleRule(listOf("服务器", "主机", "域名", "vps", "机房", "aliyun", "aws", "cloudflare"), "server_rack", 0xFF4F46E5L),
        StyleRule(listOf("虚拟充值", "话费充值", "点卡", "q币", "会员充值"), "virtual_recharge", 0xFF7C3AEDL),
        StyleRule(listOf("生活电器", "小家电", "电器", "冰箱", "洗衣机", "空调", "电饭煲"), "appliance_washer", 0xFF4F46E5L),
        StyleRule(listOf("手机数码", "手机", "iphone", "安卓"), "smartphone_dev", 0xFF4F46E5L),
        StyleRule(listOf("办公用品", "办公耗材", "打印", "复印"), "office_folder", 0xFF1677FFL),
        StyleRule(listOf("文具", "本子", "笔", "尺"), "stationery", 0xFF1677FFL),
        StyleRule(listOf("课程", "培训", "网课", "辅导"), "menu_course", 0xFF1677FFL),
        StyleRule(listOf("服饰运动", "运动鞋", "球鞋", "运动服", "跑鞋"), "sport_shoe", 0xFFE11D48L),
        StyleRule(listOf("配饰腕表", "腕表", "手表", "机械表", "石英表"), "wrist_watch", 0xFFE11D48L),
        StyleRule(listOf("个护", "洗护", "日化", "洗发", "沐浴", "牙膏", "卫生巾"), "personal_care", 0xFFE11D48L),
        StyleRule(listOf("日常家居", "家具", "灯具", "软装", "床品"), "household_lamp", 0xFFDB2777L),
        StyleRule(listOf("物业费", "管理费", "小区费"), "property_fee", 0xFF0D9488L),
        StyleRule(listOf("房租还贷", "还贷", "月供"), "house_contract", 0xFF0D9488L),
        StyleRule(listOf("房租", "房贷", "租房", "按揭"), "cottage", 0xFF0D9488L),
        StyleRule(listOf("电费", "供电"), "electric_plug", 0xFF0D9488L),
        StyleRule(listOf("水费", "自来水", "桶装水"), "water_drop", 0xFF0D9488L),
        StyleRule(listOf("燃气", "煤气", "天然气", "暖气", "取暖"), "propane_tank", 0xFF0D9488L),
        StyleRule(listOf("水电"), "bolt", 0xFF0D9488L),
        StyleRule(listOf("物业", "维修", "水电工", "开锁"), "handyman", 0xFF0D9488L),
        StyleRule(listOf("话费", "宽带", "流量", "网费"), "wifi", 0xFF0D9488L),
        StyleRule(listOf("家政", "保洁", "钟点工", "干洗"), "cleaning_services", 0xFF0D9488L),
        StyleRule(listOf("居家", "住房", "房子"), "home", 0xFF0D9488L),
        StyleRule(listOf("咖啡", "奶茶", "拿铁", "瑞幸", "星巴克", "coffee", "cafe"), "local_cafe", 0xFFEA580CL),
        StyleRule(listOf("烟", "酒", "茶叶", "茶馆", "普洱", "茅台"), "emoji_food_beverage", 0xFFEA580CL),
        StyleRule(listOf("外卖", "点餐", "美团", "饿了么"), "delivery_dining", 0xFFEA580CL),
        StyleRule(listOf("聚餐", "请客", "酒水", "酒吧", "鸡尾酒", "清吧"), "local_bar", 0xFFEA580CL),
        StyleRule(listOf("做饭", "烹饪", "厨房", "下厨", "调料"), "kitchen", 0xFF16A34AL),
        StyleRule(listOf("水果", "买菜", "生鲜", "菜市场", "蔬菜"), "local_grocery_store", 0xFF16A34AL),
        StyleRule(listOf("早餐", "早饭", "早点", "包子", "豆浆"), "breakfast_egg", 0xFFEA580CL),
        StyleRule(listOf("午餐", "午饭", "工作餐", "便当", "盒饭"), "lunch_bento", 0xFFEA580CL),
        StyleRule(listOf("晚餐", "晚饭", "夜宵", "宵夜"), "dinner_bowl", 0xFFEA580CL),
        StyleRule(listOf("甜品", "蛋糕", "面包", "烘焙", "冰淇淋", "奶茶甜点"), "bakery_dining", 0xFFEA580CL),
        StyleRule(listOf("休闲零食", "零食", "薯片", "坚果", "糖果", "辣条", "饼干"), "snack_bag", 0xFFEA580CL),
        StyleRule(listOf("三餐", "外食", "面", "粉", "快餐"), "ramen_dining", 0xFFEA580CL),
        StyleRule(listOf("餐", "饭", "吃", "火锅", "烧烤", "食堂"), "restaurant", 0xFFEA580CL),
        StyleRule(listOf("公共交通", "公交", "巴士", "大巴", "班车"), "directions_bus", 0xFF0284C7L),
        StyleRule(listOf("地铁", "轻轨", "通勤"), "subway", 0xFF0284C7L),
        StyleRule(listOf("打车", "滴滴", "出租", "网约车"), "local_taxi", 0xFF0284C7L),
        StyleRule(listOf("单车", "骑行", "自行车", "电动车"), "pedal_bike", 0xFF0284C7L),
        StyleRule(listOf("高铁", "火车", "动车", "铁路"), "train", 0xFF0284C7L),
        StyleRule(listOf("加油", "充电", "油费", "油卡"), "local_gas_station", 0xFF0284C7L),
        StyleRule(listOf("停车", "高速", "过路"), "local_parking", 0xFF0284C7L),
        StyleRule(listOf("养车", "洗车", "保养", "修车"), "local_car_wash", 0xFF0284C7L),
        StyleRule(listOf("汽车", "车", "出行", "交通"), "directions_car", 0xFF0284C7L),
        StyleRule(listOf("网购", "百货", "淘宝", "京东", "拼多多"), "local_mall", 0xFFDB2777L),
        StyleRule(listOf("超市", "便利", "山姆", "盒马"), "shopping_cart", 0xFFDB2777L),
        StyleRule(listOf("家居", "日用", "收纳", "纸巾"), "weekend", 0xFFDB2777L),
        StyleRule(listOf("快递", "邮寄", "顺丰", "邮费"), "local_shipping", 0xFFDB2777L),
        StyleRule(listOf("购物", "消费", "买"), "shopping_bag", 0xFFDB2777L),
        StyleRule(listOf("护肤", "美妆", "口红", "面膜", "化妆"), "face_retouching_natural", 0xFFE11D48L),
        StyleRule(listOf("理发", "剪发", "美甲", "烫发", "造型"), "content_cut", 0xFFE11D48L),
        StyleRule(listOf("首饰", "配饰", "黄金", "项链", "戒指", "耳环"), "diamond", 0xFFE11D48L),
        StyleRule(listOf("服饰", "穿搭", "鞋", "衣", "包"), "checkroom", 0xFFE11D48L),
        StyleRule(listOf("电脑", "笔记本", "平板", "ipad", "mac"), "laptop_mac", 0xFF4F46E5L),
        StyleRule(listOf("耳机", "键盘", "鼠标", "配件", "充电器"), "headphones", 0xFF4F46E5L),
        StyleRule(listOf("电视", "家电"), "tv", 0xFF4F46E5L),
        StyleRule(listOf("数码", "电子"), "devices", 0xFF4F46E5L),
        StyleRule(listOf("ai", "gpt", "claude", "gemini", "云盘", "icloud"), "smart_toy", 0xFF7C3AEDL),
        StyleRule(listOf("会员", "app", "软件"), "cloud_done", 0xFF7C3AEDL),
        StyleRule(listOf("音乐", "视频", "b站", "爱奇艺", "网易云", "spotify", "netflix"), "music_note", 0xFF7C3AEDL),
        StyleRule(listOf("游戏", "steam", "switch", "氪金"), "sports_esports", 0xFF7C3AEDL),
        StyleRule(listOf("订阅"), "subscriptions", 0xFF7C3AEDL),
        StyleRule(listOf("电影", "演出", "演唱会", "话剧", "脱口秀"), "movie", 0xFF9333EAL),
        StyleRule(listOf("聚会", "桌游", "剧本杀", "ktv", "密室", "玩乐"), "celebration", 0xFF9333EAL),
        StyleRule(listOf("健身", "运动", "瑜伽", "游泳", "球"), "fitness_center", 0xFF9333EAL),
        StyleRule(listOf("露营", "徒步", "爬山", "户外"), "forest", 0xFF9333EAL),
        StyleRule(listOf("娱乐", "休闲"), "theater_comedy", 0xFF9333EAL),
        StyleRule(listOf("挂号", "看病", "医院", "门诊", "急诊"), "local_hospital", 0xFFDC2626L),
        StyleRule(listOf("买药", "药店", "保健", "维生素"), "medication", 0xFFDC2626L),
        StyleRule(listOf("体检", "牙科", "口腔", "洗牙"), "monitor_heart", 0xFFDC2626L),
        StyleRule(listOf("医疗", "健康", "医", "药", "诊"), "medical_services", 0xFFDC2626L),
        StyleRule(listOf("机票", "船票", "航空", "飞行"), "flight", 0xFF2563EBL),
        StyleRule(listOf("酒店", "民宿", "住宿", "宾馆"), "hotel", 0xFF2563EBL),
        StyleRule(listOf("门票", "景点", "景区", "乐园"), "confirmation_number", 0xFF2563EBL),
        StyleRule(listOf("特产", "纪念", "伴手礼"), "backpack", 0xFF2563EBL),
        StyleRule(listOf("旅行", "旅游", "出差", "差旅"), "explore", 0xFF2563EBL),
        StyleRule(listOf("书", "阅读", "资料"), "book", 0xFF1677FFL),
        StyleRule(listOf("考试", "考证", "报名费"), "edit_note", 0xFF1677FFL),
        StyleRule(listOf("学习", "成长", "教育"), "school", 0xFF1677FFL),
        StyleRule(listOf("红包", "份子", "压岁"), "card_giftcard", 0xFFEC4899L),
        StyleRule(listOf("送礼", "礼物", "鲜花"), "redeem", 0xFFEC4899L),
        StyleRule(listOf("长辈", "父母", "爸妈", "孝敬"), "elderly", 0xFFEC4899L),
        StyleRule(listOf("约会", "恋爱", "纪念日"), "favorite", 0xFFEC4899L),
        StyleRule(listOf("人情", "社交", "往来"), "groups", 0xFFEC4899L),
        StyleRule(listOf("猫", "狗", "宠"), "pets", 0xFFD97706L),
        StyleRule(listOf("手续费", "汇损", "兑换"), "currency_exchange", 0xFF64748BL),
        StyleRule(listOf("保险"), "verified_user", 0xFF64748BL),
        StyleRule(listOf("利息", "罚息", "分期费"), "percent", 0xFF64748BL),
        StyleRule(listOf("亏损", "赔钱"), "trending_down", 0xFF64748BL),
        StyleRule(listOf("捐赠", "公益", "捐款"), "volunteer_activism", 0xFF475569L),
        StyleRule(listOf("丢失", "罚款", "遗失"), "priority_high", 0xFF475569L),
        StyleRule(listOf("年终", "十三薪"), "emoji_events", 0xFF16A34AL),
        StyleRule(listOf("加班", "津贴"), "schedule", 0xFF16A34AL),
        StyleRule(listOf("工资", "底薪", "薪资"), "payments", 0xFF16A34AL),
        StyleRule(listOf("绩效", "奖金"), "bar_chart", 0xFF15803DL),
        StyleRule(listOf("理财", "基金", "股票", "分红", "收益"), "trending_up", 0xFF0D9488L),
        StyleRule(listOf("兼职", "副业", "外包", "私活"), "business_center", 0xFF2563EBL),
        StyleRule(listOf("二手", "闲置", "闲鱼", "转卖"), "sell", 0xFF2563EBL),
        StyleRule(listOf("租金", "房租收入", "收租"), "corporate_fare", 0xFFEA580CL),
        StyleRule(listOf("营收", "营业", "店"), "point_of_sale", 0xFFEA580CL),
        StyleRule(listOf("公积金"), "badge", 0xFF006874L),
        StyleRule(listOf("退税", "个税", "抵税"), "calculate", 0xFF006874L)
    )
}

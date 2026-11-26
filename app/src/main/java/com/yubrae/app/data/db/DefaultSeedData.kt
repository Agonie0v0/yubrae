package com.yubrae.app.data.db

import com.yubrae.app.core.model.CurrencyCatalog
import com.yubrae.app.core.model.MoneyAmount
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/**
 * 默认种子数据与幂等增量补齐：
 * - 默认账本
 * - 16 个支出大类 + 67 个二级子类、9 个收入大类 + 23 个二级子类（共 115 个，1:1 唯一图标，按 (name, type) 无损查缺补漏并自动升级旧默认系统图标）
 * - 21 个预置标签（4 组，无重复标签且 1:1 唯一图标，按 name 无损查缺补漏）
 * - 默认账户（含公交卡钱包账户）与账户别名（含交通卡包名/关键字）
 * - 离线兜底汇率
 * - 内置动账正则规则（含手机钱包/NFC公交卡及通用交通卡规则）
 */
object DefaultSeedData {

    private data class ChildCategorySpec(
        val name: String,
        val icon: String
    )

    private data class ParentCategorySpec(
        val name: String,
        val type: String,
        val icon: String,
        val colorHex: Long,
        val sortOrder: Int,
        val children: List<ChildCategorySpec> = emptyList()
    )

    private data class TagSpec(
        val name: String,
        val groupName: String,
        val colorHex: Long,
        val sortOrder: Int,
        val icon: String
    )

    private data class TransitAliasSpec(
        val matchType: String,
        val pattern: String,
        val priority: Int
    )

    private val TRANSIT_ACCOUNT_NAME_KEYWORDS = listOf(
        "公交", "交通卡", "畅通卡", "一卡通", "通卡", "八达通", "地铁卡"
    )

    private val PRESET_CATEGORIES: List<ParentCategorySpec> = listOf(
        // 支出 16 大类及 67 二级（83 个互不重复的专属 iconKey）
        ParentCategorySpec(
            name = "餐饮美食",
            type = TransactionType.EXPENSE,
            icon = "restaurant",
            colorHex = 0xFFEA580CL,
            sortOrder = 10,
            children = listOf(
                ChildCategorySpec("三餐外食", "ramen_dining"),
                ChildCategorySpec("外卖", "delivery_dining"),
                ChildCategorySpec("咖啡茶饮", "local_cafe"),
                ChildCategorySpec("零食甜品", "bakery_dining"),
                ChildCategorySpec("水果生鲜", "local_grocery_store"),
                ChildCategorySpec("买菜做饭", "kitchen"),
                ChildCategorySpec("聚餐请客", "local_bar"),
                ChildCategorySpec("烟酒茶", "emoji_food_beverage")
            )
        ),
        ParentCategorySpec(
            name = "交通出行",
            type = TransactionType.EXPENSE,
            icon = "directions_car",
            colorHex = 0xFF0284C7L,
            sortOrder = 20,
            children = listOf(
                ChildCategorySpec("地铁公交", "subway"),
                ChildCategorySpec("网约打车", "local_taxi"),
                ChildCategorySpec("共享单车", "pedal_bike"),
                ChildCategorySpec("高铁火车", "train"),
                ChildCategorySpec("加油充电", "local_gas_station"),
                ChildCategorySpec("停车过路", "local_parking"),
                ChildCategorySpec("养车维修", "local_car_wash")
            )
        ),
        ParentCategorySpec(
            name = "日用购物",
            type = TransactionType.EXPENSE,
            icon = "shopping_bag",
            colorHex = 0xFFDB2777L,
            sortOrder = 30,
            children = listOf(
                ChildCategorySpec("网购百货", "local_mall"),
                ChildCategorySpec("超市便利", "shopping_cart"),
                ChildCategorySpec("家居日用", "weekend"),
                ChildCategorySpec("快递邮寄", "local_shipping")
            )
        ),
        ParentCategorySpec(
            name = "服饰美容",
            type = TransactionType.EXPENSE,
            icon = "style",
            colorHex = 0xFFE11D48L,
            sortOrder = 40,
            children = listOf(
                ChildCategorySpec("服饰鞋包", "checkroom"),
                ChildCategorySpec("美妆护肤", "face_retouching_natural"),
                ChildCategorySpec("理发美甲", "content_cut"),
                ChildCategorySpec("配饰首饰", "diamond")
            )
        ),
        ParentCategorySpec(
            name = "数码家电",
            type = TransactionType.EXPENSE,
            icon = "devices",
            colorHex = 0xFF4F46E5L,
            sortOrder = 50,
            children = listOf(
                ChildCategorySpec("手机电脑", "laptop_mac"),
                ChildCategorySpec("数码配件", "headphones"),
                ChildCategorySpec("家用电器", "tv")
            )
        ),
        ParentCategorySpec(
            name = "居家物业",
            type = TransactionType.EXPENSE,
            icon = "home",
            colorHex = 0xFF0D9488L,
            sortOrder = 60,
            children = listOf(
                ChildCategorySpec("房租房贷", "cottage"),
                ChildCategorySpec("水电燃气", "bolt"),
                ChildCategorySpec("物业维修", "handyman"),
                ChildCategorySpec("话费宽带", "wifi"),
                ChildCategorySpec("家政服务", "cleaning_services")
            )
        ),
        ParentCategorySpec(
            name = "数字订阅",
            type = TransactionType.EXPENSE,
            icon = "subscriptions",
            colorHex = 0xFF7C3AEDL,
            sortOrder = 70,
            children = listOf(
                ChildCategorySpec("软件会员", "cloud_done"),
                ChildCategorySpec("AI与云服务", "smart_toy"),
                ChildCategorySpec("影音音乐", "music_note"),
                ChildCategorySpec("游戏充值", "sports_esports")
            )
        ),
        ParentCategorySpec(
            name = "休闲娱乐",
            type = TransactionType.EXPENSE,
            icon = "theater_comedy",
            colorHex = 0xFF9333EAL,
            sortOrder = 80,
            children = listOf(
                ChildCategorySpec("电影演出", "movie"),
                ChildCategorySpec("聚会玩乐", "celebration"),
                ChildCategorySpec("运动健身", "fitness_center"),
                ChildCategorySpec("户外露营", "forest")
            )
        ),
        ParentCategorySpec(
            name = "医疗健康",
            type = TransactionType.EXPENSE,
            icon = "medical_services",
            colorHex = 0xFFDC2626L,
            sortOrder = 90,
            children = listOf(
                ChildCategorySpec("看病挂号", "local_hospital"),
                ChildCategorySpec("买药保健", "medication"),
                ChildCategorySpec("体检牙科", "monitor_heart"),
                ChildCategorySpec("医疗保险", "health_and_safety")
            )
        ),
        ParentCategorySpec(
            name = "差旅住宿",
            type = TransactionType.EXPENSE,
            icon = "explore",
            colorHex = 0xFF2563EBL,
            sortOrder = 100,
            children = listOf(
                ChildCategorySpec("机票船票", "flight"),
                ChildCategorySpec("酒店民宿", "hotel"),
                ChildCategorySpec("旅游门票", "confirmation_number"),
                ChildCategorySpec("特产纪念", "backpack")
            )
        ),
        ParentCategorySpec(
            name = "学习成长",
            type = TransactionType.EXPENSE,
            icon = "school",
            colorHex = 0xFF1677FFL,
            sortOrder = 110,
            children = listOf(
                ChildCategorySpec("书籍资料", "book"),
                ChildCategorySpec("课程培训", "science"),
                ChildCategorySpec("考试考证", "edit_note"),
                ChildCategorySpec("文具办公", "palette")
            )
        ),
        ParentCategorySpec(
            name = "人情社交",
            type = TransactionType.EXPENSE,
            icon = "groups",
            colorHex = 0xFFEC4899L,
            sortOrder = 120,
            children = listOf(
                ChildCategorySpec("红包礼金", "card_giftcard"),
                ChildCategorySpec("送礼请客", "redeem"),
                ChildCategorySpec("孝敬长辈", "elderly"),
                ChildCategorySpec("恋爱约会", "favorite")
            )
        ),
        ParentCategorySpec(
            name = "宠物生活",
            type = TransactionType.EXPENSE,
            icon = "pets",
            colorHex = 0xFFD97706L,
            sortOrder = 130,
            children = listOf(
                ChildCategorySpec("宠物食品", "set_meal"),
                ChildCategorySpec("宠物用品", "shopping_basket"),
                ChildCategorySpec("宠物医疗", "healing")
            )
        ),
        ParentCategorySpec(
            name = "亲子育儿",
            type = TransactionType.EXPENSE,
            icon = "stroller",
            colorHex = 0xFFF59E0BL,
            sortOrder = 140,
            children = listOf(
                ChildCategorySpec("奶粉辅食", "child_care"),
                ChildCategorySpec("童装玩具", "toys"),
                ChildCategorySpec("早教培训", "translate")
            )
        ),
        ParentCategorySpec(
            name = "金融保险",
            type = TransactionType.EXPENSE,
            icon = "assured_workload",
            colorHex = 0xFF64748BL,
            sortOrder = 150,
            children = listOf(
                ChildCategorySpec("汇损手续费", "currency_exchange"),
                ChildCategorySpec("商业保险", "verified_user"),
                ChildCategorySpec("利息罚息", "percent"),
                ChildCategorySpec("投资亏损", "trending_down")
            )
        ),
        ParentCategorySpec(
            name = "其他支出",
            type = TransactionType.EXPENSE,
            icon = "more_horiz",
            colorHex = 0xFF475569L,
            sortOrder = 160,
            children = listOf(
                ChildCategorySpec("公益捐赠", "volunteer_activism"),
                ChildCategorySpec("意外丢失", "priority_high")
            )
        ),

        // 收入 9 大类及 23 二级（32 个互不重复的专属 iconKey）
        ParentCategorySpec(
            name = "工资薪金",
            type = TransactionType.INCOME,
            icon = "work",
            colorHex = 0xFF16A34AL,
            sortOrder = 10,
            children = listOf(
                ChildCategorySpec("基本工资", "payments"),
                ChildCategorySpec("加班津贴", "schedule"),
                ChildCategorySpec("年终奖", "emoji_events")
            )
        ),
        ParentCategorySpec(
            name = "奖金补贴",
            type = TransactionType.INCOME,
            icon = "star",
            colorHex = 0xFF15803DL,
            sortOrder = 20,
            children = listOf(
                ChildCategorySpec("绩效奖金", "bar_chart"),
                ChildCategorySpec("餐补交通补", "directions_bus"),
                ChildCategorySpec("节日福利", "cake")
            )
        ),
        ParentCategorySpec(
            name = "投资理财",
            type = TransactionType.INCOME,
            icon = "trending_up",
            colorHex = 0xFF0D9488L,
            sortOrder = 30,
            children = listOf(
                ChildCategorySpec("基金股票", "candlestick_chart"),
                ChildCategorySpec("银行利息", "savings"),
                ChildCategorySpec("分红收益", "pie_chart"),
                ChildCategorySpec("外汇收益", "monetization_on")
            )
        ),
        ParentCategorySpec(
            name = "报销退款",
            type = TransactionType.INCOME,
            icon = "replay",
            colorHex = 0xFF0284C7L,
            sortOrder = 40,
            children = listOf(
                ChildCategorySpec("公司报销", "receipt_long"),
                ChildCategorySpec("购物退款", "local_post_office"),
                ChildCategorySpec("保险理赔", "request_quote")
            )
        ),
        ParentCategorySpec(
            name = "兼职副业",
            type = TransactionType.INCOME,
            icon = "business_center",
            colorHex = 0xFF2563EBL,
            sortOrder = 50,
            children = listOf(
                ChildCategorySpec("外包私活", "desktop_windows"),
                ChildCategorySpec("自媒体稿费", "mic"),
                ChildCategorySpec("二手闲置", "sell")
            )
        ),
        ParentCategorySpec(
            name = "人情礼金",
            type = TransactionType.INCOME,
            icon = "card_membership",
            colorHex = 0xFFDB2777L,
            sortOrder = 60,
            children = listOf(
                ChildCategorySpec("微信红包", "chat"),
                ChildCategorySpec("礼金往来", "handshake")
            )
        ),
        ParentCategorySpec(
            name = "经营所得",
            type = TransactionType.INCOME,
            icon = "storefront",
            colorHex = 0xFFEA580CL,
            sortOrder = 70,
            children = listOf(
                ChildCategorySpec("门店营收", "point_of_sale"),
                ChildCategorySpec("租金收入", "corporate_fare")
            )
        ),
        ParentCategorySpec(
            name = "政策补贴",
            type = TransactionType.INCOME,
            icon = "account_balance",
            colorHex = 0xFF006874L,
            sortOrder = 80,
            children = listOf(
                ChildCategorySpec("公积金提取", "badge"),
                ChildCategorySpec("个税退税", "calculate"),
                ChildCategorySpec("消费补贴", "local_offer")
            )
        ),
        ParentCategorySpec(
            name = "其他收入",
            type = TransactionType.INCOME,
            icon = "attach_money",
            colorHex = 0xFF475569L,
            sortOrder = 90
        )
    )

    private val PRESET_TAGS: List<TagSpec> = listOf(
        // 渠道平台 (6)
        TagSpec("拼多多", "渠道平台", 0xFFE11D48L, 10, "loyalty"),
        TagSpec("淘宝", "渠道平台", 0xFFD97706L, 20, "shopping_bag"),
        TagSpec("京东", "渠道平台", 0xFFDC2626L, 30, "local_shipping"),
        TagSpec("美团", "渠道平台", 0xFFF59E0BL, 40, "fastfood"),
        TagSpec("线下", "渠道平台", 0xFF0D9488L, 50, "storefront"),
        TagSpec("跨境", "渠道平台", 0xFF4F46E5L, 60, "public"),
        // 场景行程 (4)
        TagSpec("通勤", "场景行程", 0xFF0369A1L, 70, "subway"),
        TagSpec("差旅", "场景行程", 0xFF2563EBL, 80, "luggage"),
        TagSpec("旅行", "场景行程", 0xFF0F766EL, 90, "flight"),
        TagSpec("大促囤货", "场景行程", 0xFF9333EAL, 100, "add_shopping_cart"),
        // 消费属性 (5)
        TagSpec("刚需", "消费属性", 0xFF16A34AL, 110, "verified"),
        TagSpec("分期", "消费属性", 0xFF64748BL, 120, "credit_card"),
        TagSpec("优惠立减", "消费属性", 0xFF15803DL, 130, "local_offer"),
        TagSpec("冲动消费", "消费属性", 0xFFE11D48L, 140, "bolt"),
        TagSpec("大额", "消费属性", 0xFFDC2626L, 150, "diamond"),
        // 对象账务 (6)
        TagSpec("自己", "对象账务", 0xFF2AA876L, 160, "person"),
        TagSpec("家人", "对象账务", 0xFFEA580CL, 170, "family_restroom"),
        TagSpec("伴侣", "对象账务", 0xFFEC4899L, 180, "favorite"),
        TagSpec("AA", "对象账务", 0xFF0284C7L, 190, "groups"),
        TagSpec("可抵税", "对象账务", 0xFF4F46E5L, 200, "calculate"),
        TagSpec("装修", "对象账务", 0xFF0D9488L, 210, "handyman")
    )

    private val REMOVED_DUPLICATE_SYSTEM_CATEGORIES: Set<Pair<String, String>> = setOf(
        "其他杂项" to TransactionType.EXPENSE
    )

    private val REMOVED_DUPLICATE_SYSTEM_TAGS: Set<String> = setOf(
        "外卖", "网购", "聚餐", "宠物", "请客", "公司报销", "定期订阅"
    )

    private val LEGACY_CATEGORY_ICONS: Map<Pair<String, String>, Set<String>> = mapOf(
        ("三餐外食" to TransactionType.EXPENSE) to setOf("lunch_dining"),
        ("烟酒茶" to TransactionType.EXPENSE) to setOf("coffee"),
        ("养车维修" to TransactionType.EXPENSE) to setOf("build"),
        ("家居日用" to TransactionType.EXPENSE) to setOf("store"),
        ("服饰美容" to TransactionType.EXPENSE) to setOf("checkroom"),
        ("房租房贷" to TransactionType.EXPENSE) to setOf("home"),
        ("物业维修" to TransactionType.EXPENSE) to setOf("build"),
        ("休闲娱乐" to TransactionType.EXPENSE) to setOf("sports_esports"),
        ("体检牙科" to TransactionType.EXPENSE) to setOf("medical_services"),
        ("差旅住宿" to TransactionType.EXPENSE) to setOf("flight"),
        ("旅游门票" to TransactionType.EXPENSE) to setOf("luggage"),
        ("特产纪念" to TransactionType.EXPENSE) to setOf("redeem"),
        ("课程培训" to TransactionType.EXPENSE) to setOf("school"),
        ("文具办公" to TransactionType.EXPENSE) to setOf("menu_book"),
        ("人情社交" to TransactionType.EXPENSE) to setOf("favorite"),
        ("孝敬长辈" to TransactionType.EXPENSE) to setOf("volunteer_activism"),
        ("宠物食品" to TransactionType.EXPENSE) to setOf("pets"),
        ("宠物医疗" to TransactionType.EXPENSE) to setOf("local_hospital"),
        ("亲子育儿" to TransactionType.EXPENSE) to setOf("child_care"),
        ("童装玩具" to TransactionType.EXPENSE) to setOf("redeem"),
        ("早教培训" to TransactionType.EXPENSE) to setOf("school"),
        ("金融保险" to TransactionType.EXPENSE) to setOf("currency_exchange"),
        ("商业保险" to TransactionType.EXPENSE) to setOf("health_and_safety"),
        ("加班津贴" to TransactionType.INCOME) to setOf("work"),
        ("年终奖" to TransactionType.INCOME) to setOf("card_giftcard"),
        ("奖金补贴" to TransactionType.INCOME) to setOf("card_giftcard"),
        ("绩效奖金" to TransactionType.INCOME) to setOf("trending_up"),
        ("节日福利" to TransactionType.INCOME) to setOf("redeem"),
        ("基金股票" to TransactionType.INCOME) to setOf("trending_up"),
        ("分红收益" to TransactionType.INCOME) to setOf("attach_money"),
        ("外汇收益" to TransactionType.INCOME) to setOf("currency_exchange"),
        ("购物退款" to TransactionType.INCOME) to setOf("replay"),
        ("保险理赔" to TransactionType.INCOME) to setOf("health_and_safety"),
        ("兼职副业" to TransactionType.INCOME) to setOf("laptop_mac"),
        ("外包私活" to TransactionType.INCOME) to setOf("laptop_mac"),
        ("自媒体稿费" to TransactionType.INCOME) to setOf("edit_note"),
        ("人情礼金" to TransactionType.INCOME) to setOf("redeem"),
        ("微信红包" to TransactionType.INCOME) to setOf("card_giftcard"),
        ("门店营收" to TransactionType.INCOME) to setOf("storefront"),
        ("租金收入" to TransactionType.INCOME) to setOf("home"),
        ("公积金提取" to TransactionType.INCOME) to setOf("account_balance"),
        ("个税退税" to TransactionType.INCOME) to setOf("receipt_long")
    )

    private val LEGACY_TAG_ICONS: Map<String, Set<String>> = mapOf(
        "大促囤货" to setOf("shopping_basket"),
        "家人" to setOf("home"),
        "装修" to setOf("build")
    )

    internal fun presetCategoryIconKeys(): Map<Pair<String, String>, String> = buildMap {
        PRESET_CATEGORIES.forEach { parent ->
            put(parent.name to parent.type, parent.icon)
            parent.children.forEach { child ->
                put(child.name to parent.type, child.icon)
            }
        }
    }

    internal fun presetTagIconKeys(): Map<String, String> =
        PRESET_TAGS.associate { it.name to it.icon }

    private val TRANSIT_ALIASES: List<TransitAliasSpec> = listOf(
        TransitAliasSpec(AliasMatchType.PACKAGE, "com.miui.tsmclient", 110),
        TransitAliasSpec(AliasMatchType.PACKAGE, "com.mipay.wallet", 105),
        TransitAliasSpec(AliasMatchType.PACKAGE, "com.xiaomi.pass", 100),
        TransitAliasSpec(AliasMatchType.PACKAGE, "com.huawei.wallet", 100),
        TransitAliasSpec(AliasMatchType.PACKAGE, "com.hihonor.wallet", 100),
        TransitAliasSpec(AliasMatchType.PACKAGE, "com.finshell.wallet", 100),
        TransitAliasSpec(AliasMatchType.PACKAGE, "com.coloros.wallet", 100),
        TransitAliasSpec(AliasMatchType.PACKAGE, "com.vivo.wallet", 100),
        TransitAliasSpec(AliasMatchType.KEYWORD, "重庆畅通卡", 110),
        TransitAliasSpec(AliasMatchType.KEYWORD, "畅通卡", 100),
        TransitAliasSpec(AliasMatchType.KEYWORD, "公交卡", 95),
        TransitAliasSpec(AliasMatchType.KEYWORD, "交通卡", 95),
        TransitAliasSpec(AliasMatchType.KEYWORD, "互联互通", 90)
    )

    private val PRESET_PARSE_RULES: List<ParseRuleEntity> = listOf(
        ParseRuleEntity(
            name = "手机钱包/NFC公交卡刷卡扣费通知",
            sourcePackage = "com.miui.tsmclient,com.mipay.wallet,com.xiaomi.pass,com.huawei.wallet,com.hihonor.wallet,com.finshell.wallet,com.coloros.wallet,com.vivo.wallet,com.samsung.android.spay",
            channelType = "NOTIFICATION",
            regexPattern = "(?:刷卡|乘车|出站|扣费|扣款|消费|支出)(?:成功|完成)?(?:[，,：:\\s]*(?:本次|交易|实际)?(?:扣费|扣款|消费|支出|支付|金额))?(?:为|：|:|\\s)*[￥¥]?\\s*([0-9]+(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?)(?![0-9.])(?!\\s*(?:年|月|日|号|时|分|秒|路|线|号线|次|-|/|:\\d))\\s*元?",
            transactionType = TransactionType.EXPENSE,
            amountGroup = 1,
            currencyGroup = 0,
            defaultCurrency = "CNY",
            defaultCategoryName = "地铁公交",
            priority = 106,
            isBuiltIn = true
        ),
        ParseRuleEntity(
            name = "通用交通卡/畅通卡乘车扣费通知",
            sourcePackage = "*",
            channelType = "ANY",
            regexPattern = "(?:重庆畅通卡|畅通卡|交通卡|公交卡|一卡通|羊城通|岭南通|深圳通|上海交通卡|京津冀|长安通|天府通|金陵通|乘车码|轨道交通|地铁出站).*?(?:扣费|扣款|消费|支出|支付)(?:[，,：:\\s]*(?:本次|交易|实际)?(?:扣费|扣款|消费|支出|支付|金额))?(?:为|：|:|\\s)*[￥¥]?\\s*([0-9]+(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?)(?![0-9.])(?!\\s*(?:年|月|日|号|时|分|秒|路|线|号线|次|-|/|:\\d))\\s*元?",
            transactionType = TransactionType.EXPENSE,
            amountGroup = 1,
            currencyGroup = 0,
            defaultCurrency = "CNY",
            defaultCategoryName = "地铁公交",
            priority = 102,
            isBuiltIn = true
        ),
        ParseRuleEntity(
            name = "微信支付付款通知",
            sourcePackage = "com.tencent.mm",
            channelType = "NOTIFICATION",
            regexPattern = "(?:微信支付|向)(.*?)付款(?:人民币|RMB)?[￥¥]?([0-9]+(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?)元?",
            transactionType = TransactionType.EXPENSE,
            amountGroup = 2,
            currencyGroup = 0,
            defaultCurrency = "CNY",
            merchantGroup = 1,
            defaultCategoryName = "日用购物",
            priority = 100,
            isBuiltIn = true
        ),
        ParseRuleEntity(
            name = "微信收款到账通知",
            sourcePackage = "com.tencent.mm",
            channelType = "NOTIFICATION",
            regexPattern = "(?:收款|到账|微信转账)[￥¥]?([0-9]+(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?)元?",
            transactionType = TransactionType.INCOME,
            amountGroup = 1,
            defaultCurrency = "CNY",
            defaultCategoryName = "其他收入",
            priority = 95,
            isBuiltIn = true
        ),
        ParseRuleEntity(
            name = "支付宝支付/扣款通知",
            sourcePackage = "com.eg.android.AlipayGphone",
            channelType = "NOTIFICATION",
            regexPattern = "(?:向(.*?)付款|消费|支付|扣款)[￥¥]?([0-9]+(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?)元?",
            transactionType = TransactionType.EXPENSE,
            amountGroup = 2,
            defaultCurrency = "CNY",
            merchantGroup = 1,
            defaultCategoryName = "日用购物",
            priority = 100,
            isBuiltIn = true
        ),
        ParseRuleEntity(
            name = "支付宝收款通知",
            sourcePackage = "com.eg.android.AlipayGphone",
            channelType = "NOTIFICATION",
            regexPattern = "(?:你已成功收款|到账|转入)[￥¥]?([0-9]+(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?)元?",
            transactionType = TransactionType.INCOME,
            amountGroup = 1,
            defaultCurrency = "CNY",
            defaultCategoryName = "其他收入",
            priority = 95,
            isBuiltIn = true
        ),
        ParseRuleEntity(
            name = "银行外币消费通用规则(USD/EUR/GBP)",
            sourcePackage = "*",
            channelType = "ANY",
            regexPattern = "(?:尾号(\\d{4})).*?(?:消费|支出|扣款|支付|spent|charged)\\s*(USD|EUR|GBP|HKD|JPY|SGD|CNY|RMB|\\$|€|£|¥)\\s*([0-9]+(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?)",
            transactionType = TransactionType.EXPENSE,
            amountGroup = 3,
            currencyGroup = 2,
            defaultCurrency = "USD",
            accountHintGroup = 1,
            defaultCategoryName = "数字订阅",
            priority = 90,
            isBuiltIn = true
        ),
        ParseRuleEntity(
            name = "银行人民币支出通用短信/通知",
            sourcePackage = "*",
            channelType = "ANY",
            regexPattern = "(?:尾号(\\d{4})).*?(?:支出|消费|扣款|付款|转出).*?(?:人民币|RMB|CNY|¥|￥)?\\s*([0-9]+(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?)\\s*元?",
            transactionType = TransactionType.EXPENSE,
            amountGroup = 2,
            defaultCurrency = "CNY",
            accountHintGroup = 1,
            defaultCategoryName = "其他支出",
            priority = 80,
            isBuiltIn = true
        ),
        ParseRuleEntity(
            name = "银行人民币收入/入账短信",
            sourcePackage = "*",
            channelType = "ANY",
            regexPattern = "(?:尾号(\\d{4})).*?(?:收入|入账|转入|到账|工资).*?(?:人民币|RMB|CNY|¥|￥)?\\s*([0-9]+(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?)\\s*元?",
            transactionType = TransactionType.INCOME,
            amountGroup = 2,
            defaultCurrency = "CNY",
            accountHintGroup = 1,
            defaultCategoryName = "其他收入",
            priority = 80,
            isBuiltIn = true
        ),
        ParseRuleEntity(
            name = "英文境外卡/Wise/Revolut扣款通知",
            sourcePackage = "*",
            channelType = "NOTIFICATION",
            regexPattern = "(?:You spent|Paid|Charged|Payment of)\\s*(\\$|€|£|USD|EUR|GBP|CNY)\\s*([0-9]+(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?)\\s*(?:at|to)\\s+([^.\\n]+)",
            transactionType = TransactionType.EXPENSE,
            amountGroup = 2,
            currencyGroup = 1,
            defaultCurrency = "USD",
            merchantGroup = 3,
            defaultCategoryName = "数字订阅",
            priority = 88,
            isBuiltIn = true
        )
    )

    suspend fun ensureSeeded(
        db: KairoDatabase,
        settingsStore: com.yubrae.app.data.prefs.SettingsStore? = null
    ) {
        val settings = settingsStore?.getSnapshot()
        val shouldSeedInitialAccounts = !(settings?.initialAccountsSeeded ?: false)
        ensureSeeded(
            ledgerDao = db.ledgerDao(),
            categoryDao = db.categoryDao(),
            tagDao = db.tagDao(),
            accountDao = db.accountDao(),
            accountAliasDao = db.accountAliasDao(),
            exchangeRateDao = db.exchangeRateDao(),
            parseRuleDao = db.parseRuleDao(),
            seedInitialAccounts = shouldSeedInitialAccounts,
            seedTransitCardIfMissing = shouldSeedInitialAccounts,
            transactionDao = db.transactionDao()
        )
        if (shouldSeedInitialAccounts) {
            settingsStore?.markInitialAccountsSeeded()
        }
    }

    suspend fun ensureSeeded(
        ledgerDao: LedgerDao,
        categoryDao: CategoryDao,
        tagDao: TagDao,
        accountDao: AccountDao,
        accountAliasDao: AccountAliasDao,
        exchangeRateDao: ExchangeRateDao,
        parseRuleDao: ParseRuleDao,
        seedInitialAccounts: Boolean = true,
        seedTransitCardIfMissing: Boolean = true,
        transactionDao: TransactionDao? = null
    ) {
        ensureDefaultLedger(ledgerDao)
        ensurePresetCategories(categoryDao, transactionDao)
        ensurePresetTags(tagDao)
        ensureAccountsAndTransitCard(
            accountDao = accountDao,
            aliasDao = accountAliasDao,
            seedInitialDefaults = seedInitialAccounts,
            seedTransitCardIfMissing = seedTransitCardIfMissing
        )
        ensureFallbackRates(exchangeRateDao)
        ensurePresetParseRules(parseRuleDao)
    }

    suspend fun ensureDefaultLedger(db: KairoDatabase): Long =
        ensureDefaultLedger(db.ledgerDao())

    /**
     * 仅在 ledgers 表完全为空时创建默认账本；若已存在其他账本（例如用户已删除初始默认账本），绝不按 1L 复活。
     */
    suspend fun ensureDefaultLedger(ledgerDao: LedgerDao): Long {
        val existing = ledgerDao.getAll()
        if (existing.isEmpty()) {
            return ledgerDao.insert(
                LedgerEntity(
                    name = "默认账本",
                    currencyCode = CurrencyCatalog.DEFAULT_BASE_CURRENCY,
                    icon = "menu_book",
                    colorHex = 0xFF2AA876L,
                    sortOrder = 0
                )
            )
        }
        return existing.firstOrNull { !it.archived }?.id ?: existing.first().id
    }

    suspend fun ensurePresetCategories(db: KairoDatabase): Int =
        ensurePresetCategories(db.categoryDao(), db.transactionDao())

    private fun shouldUpgradeSystemCategoryIcon(
        existing: CategoryEntity,
        targetIcon: String,
        key: Pair<String, String>
    ): Boolean {
        if (!existing.isSystem || existing.icon == targetIcon) return false
        return existing.icon.isBlank() || existing.icon in LEGACY_CATEGORY_ICONS[key].orEmpty()
    }

    suspend fun ensurePresetCategories(
        categoryDao: CategoryDao,
        transactionDao: TransactionDao? = null
    ): Int {
        val existingByKey = categoryDao.getAll()
            .associateBy { it.name to it.type }
            .toMutableMap()
        var addedCount = 0

        for (parentSpec in PRESET_CATEGORIES) {
            val parentKey = parentSpec.name to parentSpec.type
            val existingParent = existingByKey[parentKey]
            val parentId = if (existingParent == null) {
                val entity = CategoryEntity(
                    parentId = null,
                    name = parentSpec.name,
                    type = parentSpec.type,
                    icon = parentSpec.icon,
                    colorHex = parentSpec.colorHex,
                    sortOrder = parentSpec.sortOrder,
                    isSystem = true
                )
                val insertedId = categoryDao.insert(entity)
                existingByKey[parentKey] = entity.copy(id = insertedId)
                addedCount++
                insertedId
            } else {
                if (shouldUpgradeSystemCategoryIcon(existingParent, parentSpec.icon, parentKey)) {
                    val upgraded = existingParent.copy(icon = parentSpec.icon)
                    categoryDao.update(upgraded)
                    existingByKey[parentKey] = upgraded
                }
                existingParent.id
            }

            parentSpec.children.forEachIndexed { index, childSpec ->
                val childKey = childSpec.name to parentSpec.type
                val existingChild = existingByKey[childKey]
                if (existingChild == null) {
                    val childEntity = CategoryEntity(
                        parentId = parentId,
                        name = childSpec.name,
                        type = parentSpec.type,
                        icon = childSpec.icon,
                        colorHex = parentSpec.colorHex,
                        sortOrder = parentSpec.sortOrder + index + 1,
                        isSystem = true
                    )
                    val insertedChildId = categoryDao.insert(childEntity)
                    existingByKey[childKey] = childEntity.copy(id = insertedChildId)
                    addedCount++
                } else if (shouldUpgradeSystemCategoryIcon(existingChild, childSpec.icon, childKey)) {
                    val upgraded = existingChild.copy(icon = childSpec.icon)
                    categoryDao.update(upgraded)
                    existingByKey[childKey] = upgraded
                }
            }
        }

        // 无损移除历史版本中与一级分类重叠的系统子分类（如 “其他支出/其他杂项”），并将既有流水归并到父分类
        val fallbackOtherExpenseId = existingByKey["其他支出" to TransactionType.EXPENSE]?.id
        for (removedKey in REMOVED_DUPLICATE_SYSTEM_CATEGORIES) {
            val legacy = existingByKey[removedKey] ?: continue
            if (!legacy.isSystem) continue
            if (transactionDao != null && fallbackOtherExpenseId != null && legacy.id > 0L) {
                val affectedIds = transactionDao.getAll()
                    .asSequence()
                    .filter { it.categoryId == legacy.id }
                    .map { it.id }
                    .toList()
                if (affectedIds.isNotEmpty()) {
                    transactionDao.updateCategoryForIds(affectedIds, fallbackOtherExpenseId)
                }
            }
            categoryDao.deleteById(legacy.id)
            existingByKey.remove(removedKey)
        }

        return addedCount
    }

    suspend fun ensurePresetTags(db: KairoDatabase): Int =
        ensurePresetTags(db.tagDao())

    suspend fun ensurePresetTags(tagDao: TagDao): Int {
        val allExisting = tagDao.getAll()
        val existingByName = HashMap<String, TagEntity>(allExisting.size)
        for (tag in allExisting) {
            if (tag.isSystem && tag.name in REMOVED_DUPLICATE_SYSTEM_TAGS) {
                tagDao.deleteById(tag.id)
            } else {
                existingByName[tag.name] = tag
            }
        }

        val toInsert = mutableListOf<TagEntity>()
        for (spec in PRESET_TAGS) {
            val existing = existingByName[spec.name]
            if (existing == null) {
                val entity = TagEntity(
                    name = spec.name,
                    groupName = spec.groupName,
                    colorHex = spec.colorHex,
                    sortOrder = spec.sortOrder,
                    isSystem = true,
                    icon = spec.icon
                )
                toInsert.add(entity)
                existingByName[spec.name] = entity
            } else if (existing.isSystem && existing.icon != spec.icon &&
                (existing.icon.isBlank() || existing.icon in LEGACY_TAG_ICONS[spec.name].orEmpty())
            ) {
                // 无损升级预置系统标签默认图标，保留用户自定义标签配置
                tagDao.update(existing.copy(icon = spec.icon))
            }
        }
        if (toInsert.isNotEmpty()) {
            tagDao.insertAll(toInsert)
        }
        return toInsert.size
    }

    suspend fun ensureAccountsAndTransitCard(
        db: KairoDatabase,
        seedInitialDefaults: Boolean = true,
        seedTransitCardIfMissing: Boolean = true
    ) {
        ensureAccountsAndTransitCard(
            accountDao = db.accountDao(),
            aliasDao = db.accountAliasDao(),
            seedInitialDefaults = seedInitialDefaults,
            seedTransitCardIfMissing = seedTransitCardIfMissing
        )
    }

    suspend fun ensureAccountsAndTransitCard(
        accountDao: AccountDao,
        aliasDao: AccountAliasDao,
        seedInitialDefaults: Boolean = true,
        seedTransitCardIfMissing: Boolean = true
    ) {
        val existingAccounts = accountDao.getAll()
        if (existingAccounts.isEmpty()) {
            if (!seedInitialDefaults) return
            val wechatId = accountDao.insert(
                AccountEntity(
                    name = "微信零钱",
                    groupType = AccountGroup.WALLET,
                    currencyCode = "CNY",
                    balanceMicros = 0L,
                    icon = "chat",
                    colorHex = 0xFF07C160L,
                    sortOrder = 10
                )
            )
            val alipayId = accountDao.insert(
                AccountEntity(
                    name = "支付宝",
                    groupType = AccountGroup.WALLET,
                    currencyCode = "CNY",
                    balanceMicros = 0L,
                    icon = "account_balance_wallet",
                    colorHex = 0xFF1677FFL,
                    sortOrder = 20
                )
            )
            val transitId = accountDao.insert(createDefaultTransitCardAccount())
            val cashId = accountDao.insert(
                AccountEntity(
                    name = "现金",
                    groupType = AccountGroup.WALLET,
                    currencyCode = "CNY",
                    balanceMicros = 0L,
                    icon = "payments",
                    colorHex = 0xFF0F766EL,
                    sortOrder = 30
                )
            )
            val debitId = accountDao.insert(
                AccountEntity(
                    name = "银行储蓄卡",
                    groupType = AccountGroup.DEBIT,
                    currencyCode = "CNY",
                    balanceMicros = 0L,
                    icon = "account_balance",
                    colorHex = 0xFF0B57D0L,
                    sortOrder = 40
                )
            )
            val creditId = accountDao.insert(
                AccountEntity(
                    name = "全币种信用卡",
                    groupType = AccountGroup.CREDIT,
                    currencyCode = "CNY",
                    balanceMicros = 0L,
                    creditLimitMicros = 50_000L * MoneyAmount.AMOUNT_SCALE,
                    billingDay = 5,
                    repaymentDay = 23,
                    icon = "credit_card",
                    colorHex = 0xFFDC2626L,
                    sortOrder = 50
                )
            )
            val usdId = accountDao.insert(
                AccountEntity(
                    name = "美元账户 (USD)",
                    groupType = AccountGroup.FOREIGN,
                    currencyCode = "USD",
                    balanceMicros = 0L,
                    icon = "attach_money",
                    colorHex = 0xFF1D4ED8L,
                    sortOrder = 60
                )
            )
            val eurId = accountDao.insert(
                AccountEntity(
                    name = "欧元账户 (EUR)",
                    groupType = AccountGroup.FOREIGN,
                    currencyCode = "EUR",
                    balanceMicros = 0L,
                    icon = "euro",
                    colorHex = 0xFF0369A1L,
                    sortOrder = 70
                )
            )
            val gbpId = accountDao.insert(
                AccountEntity(
                    name = "英镑账户 (GBP)",
                    groupType = AccountGroup.FOREIGN,
                    currencyCode = "GBP",
                    balanceMicros = 0L,
                    icon = "currency_pound",
                    colorHex = 0xFF6D28D9L,
                    sortOrder = 80
                )
            )

            aliasDao.insertAll(
                listOf(
                    AccountAliasEntity(accountId = wechatId, matchType = AliasMatchType.PACKAGE, pattern = "com.tencent.mm", priority = 100),
                    AccountAliasEntity(accountId = wechatId, matchType = AliasMatchType.KEYWORD, pattern = "微信支付", priority = 80),
                    AccountAliasEntity(accountId = wechatId, matchType = AliasMatchType.KEYWORD, pattern = "零钱", priority = 70),
                    AccountAliasEntity(accountId = alipayId, matchType = AliasMatchType.PACKAGE, pattern = "com.eg.android.AlipayGphone", priority = 100),
                    AccountAliasEntity(accountId = alipayId, matchType = AliasMatchType.KEYWORD, pattern = "支付宝", priority = 85),
                    AccountAliasEntity(accountId = alipayId, matchType = AliasMatchType.KEYWORD, pattern = "余额宝", priority = 80),
                    AccountAliasEntity(accountId = alipayId, matchType = AliasMatchType.KEYWORD, pattern = "花呗", priority = 75),
                    AccountAliasEntity(accountId = debitId, matchType = AliasMatchType.KEYWORD, pattern = "储蓄卡", priority = 60),
                    AccountAliasEntity(accountId = creditId, matchType = AliasMatchType.KEYWORD, pattern = "信用卡", priority = 65),
                    AccountAliasEntity(accountId = creditId, matchType = AliasMatchType.KEYWORD, pattern = "Visa", priority = 60),
                    AccountAliasEntity(accountId = creditId, matchType = AliasMatchType.KEYWORD, pattern = "Mastercard", priority = 60),
                    AccountAliasEntity(accountId = usdId, matchType = AliasMatchType.KEYWORD, pattern = "美元账户", priority = 70),
                    AccountAliasEntity(accountId = eurId, matchType = AliasMatchType.KEYWORD, pattern = "欧元账户", priority = 70),
                    AccountAliasEntity(accountId = gbpId, matchType = AliasMatchType.KEYWORD, pattern = "英镑账户", priority = 70),
                    AccountAliasEntity(accountId = cashId, matchType = AliasMatchType.KEYWORD, pattern = "现金", priority = 40)
                )
            )
            ensureMissingTransitAliases(transitId, aliasDao)
            return
        }

        val transitAccount = existingAccounts.firstOrNull { account ->
            TRANSIT_ACCOUNT_NAME_KEYWORDS.any { keyword ->
                account.name.contains(keyword, ignoreCase = true)
            }
        }
        val transitAccountId = transitAccount?.id ?: if (seedTransitCardIfMissing) {
            accountDao.insert(createDefaultTransitCardAccount())
        } else {
            return
        }
        ensureMissingTransitAliases(transitAccountId, aliasDao)
    }

    private fun createDefaultTransitCardAccount() = AccountEntity(
        name = "公交卡",
        groupType = AccountGroup.WALLET,
        currencyCode = "CNY",
        balanceMicros = 0L,
        icon = "directions_bus",
        colorHex = 0xFF0284C7L,
        sortOrder = 25
    )

    private suspend fun ensureMissingTransitAliases(
        transitAccountId: Long,
        aliasDao: AccountAliasDao
    ) {
        val existingKeys = aliasDao.getAll().mapTo(HashSet()) { alias ->
            aliasKey(alias.matchType, alias.pattern)
        }
        val toInsert = mutableListOf<AccountAliasEntity>()
        for (spec in TRANSIT_ALIASES) {
            if (existingKeys.add(aliasKey(spec.matchType, spec.pattern))) {
                toInsert.add(
                    AccountAliasEntity(
                        accountId = transitAccountId,
                        matchType = spec.matchType,
                        pattern = spec.pattern,
                        priority = spec.priority
                    )
                )
            }
        }
        if (toInsert.isNotEmpty()) {
            aliasDao.insertAll(toInsert)
        }
    }

    private fun aliasKey(matchType: String, pattern: String): String =
        "${matchType.trim().uppercase(Locale.ROOT)}::${pattern.trim().lowercase(Locale.ROOT)}"

    suspend fun ensureFallbackRates(exchangeRateDao: ExchangeRateDao) {
        if (exchangeRateDao.getAll().isNotEmpty()) return
        val today = LocalDate.now(ZoneId.systemDefault()).toString()
        val now = System.currentTimeMillis()
        val rates = CurrencyCatalog.all
            .filter { it.code != CurrencyCatalog.DEFAULT_BASE_CURRENCY }
            .flatMap { info ->
                val toCny = info.fallbackRateToCnyMicros
                val fromCny = MoneyAmount.invertRate(toCny)
                listOf(
                    ExchangeRateEntity(
                        baseCurrency = info.code,
                        targetCurrency = CurrencyCatalog.DEFAULT_BASE_CURRENCY,
                        rateMicros = toCny,
                        date = today,
                        source = "BUILTIN",
                        updatedAt = now
                    ),
                    ExchangeRateEntity(
                        baseCurrency = CurrencyCatalog.DEFAULT_BASE_CURRENCY,
                        targetCurrency = info.code,
                        rateMicros = fromCny,
                        date = today,
                        source = "BUILTIN",
                        updatedAt = now
                    )
                )
            }
        exchangeRateDao.upsertAll(rates)
    }

    suspend fun ensurePresetParseRules(db: KairoDatabase) {
        ensurePresetParseRules(db.parseRuleDao())
    }

    suspend fun ensurePresetParseRules(parseRuleDao: ParseRuleDao) {
        val existingRules = parseRuleDao.getAll()
        val legacyRuleName = "小米钱包/NFC公交卡刷卡扣费通知"
        val unifiedRuleName = "手机钱包/NFC公交卡刷卡扣费通知"
        val hasUnifiedRule = existingRules.any { it.name == unifiedRuleName }
        if (!hasUnifiedRule) {
            existingRules.firstOrNull { it.isBuiltIn && it.name == legacyRuleName }?.let { legacy ->
                parseRuleDao.update(legacy.copy(name = unifiedRuleName))
            }
        }
        val existingNames = parseRuleDao.getAll().mapTo(HashSet()) { it.name }
        val toInsert = PRESET_PARSE_RULES.filter { existingNames.add(it.name) }
        if (toInsert.isNotEmpty()) {
            parseRuleDao.insertAll(toInsert)
        }
    }
}

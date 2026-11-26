import Foundation

/// 默认种子数据与 Material Icon -> iOS SF Symbols 双向映射（与 Android `DefaultSeedData.kt` 完整对齐）。
public enum DefaultSeedData {

    private struct ChildSpec {
        let name: String
        let icon: String
    }

    private struct ParentSpec {
        let name: String
        let type: String
        let icon: String
        let colorHex: Int64
        let sortOrder: Int
        let children: [ChildSpec]
    }

    private struct TagSpec {
        let name: String
        let groupName: String
        let colorHex: Int64
        let sortOrder: Int
        let icon: String
    }

    // MARK: - Material Icon -> iOS 16+ SF Symbol Mapping

    private static let iconSymbolMap: [String: String] = [
        // 1. 餐饮美食 (9 内置唯一 + 扩展)
        "restaurant": "fork.knife",
        "ramen_dining": "takeoutbag.and.cup.and.straw.fill",
        "delivery_dining": "scooter",
        "local_cafe": "cup.and.saucer.fill",
        "bakery_dining": "birthday.cake.fill",
        "local_grocery_store": "carrot.fill",
        "kitchen": "frying.pan.fill",
        "local_bar": "wineglass.fill",
        "emoji_food_beverage": "mug.fill",
        "lunch_dining": "fork.knife",
        "dinner_dining": "fork.knife.circle",
        "rice_bowl": "takeoutbag.and.cup.and.straw",
        "brunch_dining": "sun.and.horizon.fill",
        "fastfood": "fork.knife.circle.fill",
        "takeout_dining": "bag.circle",
        "bento": "square.grid.2x2",
        "coffee": "cup.and.saucer",
        "free_breakfast": "sunrise.fill",
        "breakfast_dining": "sun.max.fill",
        "cake": "gift.circle.fill",
        "cookie": "circle.dotted",
        "icecream": "snowflake",
        "soup_kitchen": "flame.fill",
        "wine_bar": "wineglass",
        "sports_bar": "sportscourt.fill",
        "liquor": "drop.triangle.fill",
        "tapas": "circle.grid.3x3.fill",
        "local_pizza": "triangle.fill",
        "egg_alt": "oval.fill",
        "outdoor_grill": "flame.circle.fill",

        // 2. 交通出行 (8 内置唯一 + 扩展)
        "directions_car": "car.fill",
        "subway": "tram.fill",
        "local_taxi": "car.2.fill",
        "pedal_bike": "bicycle",
        "train": "train.side.front.car",
        "local_gas_station": "fuelpump.fill",
        "local_parking": "parkingsign.circle.fill",
        "local_car_wash": "wrench.and.screwdriver.fill",
        "directions_bus": "bus.fill",
        "electric_car": "bolt.car.fill",
        "tram": "tram.circle.fill",
        "two_wheeler": "bicycle.circle.fill",
        "electric_scooter": "scooter",
        "directions_boat": "ferry.fill",
        "ev_station": "ev.charger.fill",
        "toll": "road.lanes",
        "build": "wrench.fill",

        // 3. 日用购物与快递 (5 内置唯一 + 扩展)
        "shopping_bag": "bag.fill",
        "local_mall": "cube.box.fill",
        "shopping_cart": "cart.fill",
        "weekend": "sofa.fill",
        "local_shipping": "shippingbox.fill",
        "add_shopping_cart": "cart.badge.plus",
        "store": "house.and.flag.fill",
        "storefront": "storefront.fill",
        "local_convenience_store": "building.fill",
        "inventory_2": "archivebox.fill",
        "qr_code_scanner": "qrcode.viewfinder",
        "shopping_basket": "basket.fill",

        // 4. 服饰美容 (5 内置唯一 + 扩展)
        "style": "wand.and.stars",
        "checkroom": "tshirt.fill",
        "face_retouching_natural": "sparkles",
        "content_cut": "scissors",
        "diamond": "diamond.fill",
        "watch": "applewatch",
        "dry_cleaning": "washer.fill",
        "iron": "sparkles.rectangle.stack.fill",
        "auto_fix_high": "wand.and.rays",
        "filter_vintage": "camera.macro",
        "brush": "paintbrush.fill",
        "spa": "leaf.circle.fill",

        // 5. 数码家电 (4 内置唯一 + 扩展)
        "devices": "laptopcomputer.and.iphone",
        "laptop_mac": "laptopcomputer",
        "headphones": "headphones",
        "tv": "tv.fill",
        "desktop_windows": "desktopcomputer",
        "tablet_mac": "ipad",
        "keyboard": "keyboard.fill",
        "speaker": "hifispeaker.fill",
        "print": "printer.fill",
        "camera_alt": "camera.fill",
        "phone_android": "iphone",
        "smartphone": "iphone.gen3",
        "sim_card": "simcard.fill",
        "router": "wifi.router.fill",

        // 6. 居家物业 (6 内置唯一 + 扩展)
        "home": "house.fill",
        "cottage": "key.fill",
        "bolt": "bolt.fill",
        "handyman": "hammer.fill",
        "wifi": "wifi",
        "cleaning_services": "bubbles.and.sparkles.fill",
        "chair": "chair.lounge.fill",
        "bed": "bed.double.circle.fill",
        "water_drop": "drop.circle.fill",
        "lightbulb": "lightbulb.fill",
        "local_laundry_service": "washer",
        "yard": "leaf.fill",
        "bathtub": "bathtub.fill",
        "ac_unit": "snowflake.circle.fill",
        "propane_tank": "flame",
        "local_fire_department": "flame.fill",
        "local_florist": "camera.macro.circle.fill",

        // 7. 数字订阅 (5 内置唯一)
        "subscriptions": "repeat.circle.fill",
        "cloud_done": "icloud.fill",
        "smart_toy": "cpu.fill",
        "music_note": "music.note",
        "sports_esports": "gamecontroller.fill",

        // 8. 休闲娱乐 (5 内置唯一 + 扩展)
        "theater_comedy": "theatermasks.fill",
        "movie": "film.fill",
        "celebration": "party.popper.fill",
        "fitness_center": "dumbbell.fill",
        "forest": "tree.fill",
        "sports_soccer": "soccerball",
        "self_improvement": "figure.mind.and.body",
        "beach_access": "beach.umbrella.fill",
        "mic": "mic.fill",
        "sports_basketball": "basketball.fill",
        "sports_tennis": "tennis.racket",
        "pool": "figure.pool.swim",
        "hiking": "figure.hiking",
        "park": "tree.circle.fill",
        "nightlife": "moon.stars.fill",

        // 9. 医疗健康 (5 内置唯一 + 扩展)
        "medical_services": "cross.case.fill",
        "local_hospital": "cross.circle.fill",
        "medication": "pills.fill",
        "monitor_heart": "stethoscope",
        "health_and_safety": "heart.text.square.fill",
        "local_pharmacy": "cross.vial.fill",
        "vaccines": "syringe.fill",
        "healing": "bandage.fill",
        "bloodtype": "drop.fill",
        "psychology": "brain.head.profile",
        "visibility": "eye.fill",

        // 10. 差旅住宿 (5 内置唯一 + 扩展)
        "explore": "safari.fill",
        "flight": "airplane",
        "airplane_ticket": "airplane.circle.fill",
        "hotel": "bed.double.fill",
        "confirmation_number": "ticket.fill",
        "backpack": "bag.circle.fill",
        "luggage": "suitcase.fill",
        "map": "map.fill",

        // 11. 学习成长 (5 内置唯一 + 扩展)
        "school": "graduationcap.fill",
        "book": "book.fill",
        "science": "studentdesk",
        "edit_note": "pencil.and.list.clipboard",
        "palette": "pencil.and.ruler.fill",
        "menu_book": "book.closed.fill",

        // 12. 人情社交 (5 内置唯一 + 扩展)
        "groups": "person.3.fill",
        "card_giftcard": "envelope.fill",
        "redeem": "gift.fill",
        "elderly": "heart.circle.fill",
        "favorite": "heart.fill",
        "volunteer_activism": "hands.sparkles.fill",
        "handshake": "person.2.fill",
        "family_restroom": "house.circle.fill",

        // 13. 宠物生活 & 14. 亲子育儿 (8 内置唯一)
        "pets": "pawprint.fill",
        "set_meal": "fish.fill",
        "stroller": "figure.and.child.holdinghands",
        "child_care": "drop.fill",
        "toys": "puzzlepiece.fill",
        "translate": "abc",

        // 15. 金融保险 & 16. 其他支出 (8 内置唯一)
        "assured_workload": "building.columns.circle.fill",
        "currency_exchange": "arrow.triangle.2.circlepath.circle.fill",
        "verified_user": "checkmark.shield.fill",
        "percent": "percent",
        "trending_down": "chart.line.downtrend.xyaxis",
        "more_horiz": "ellipsis.circle.fill",
        "priority_high": "exclamationmark.triangle.fill",

        // 17-25. 收入全大类与二级 (32 内置唯一 + 扩展)
        "work": "briefcase.fill",
        "payments": "banknote.fill",
        "schedule": "clock.fill",
        "emoji_events": "trophy.fill",
        "star": "star.circle.fill",
        "bar_chart": "chart.bar.fill",
        "trending_up": "chart.line.uptrend.xyaxis",
        "show_chart": "waveform.path.ecg",
        "multiline_chart": "chart.line.uptrend.xyaxis.circle.fill",
        "candlestick_chart": "chart.xyaxis.line",
        "savings": "dollarsign.square.fill",
        "pie_chart": "chart.pie.fill",
        "monetization_on": "eurosign.circle.fill",
        "replay": "arrow.uturn.backward.circle.fill",
        "receipt_long": "doc.text.fill",
        "local_post_office": "arrow.counterclockwise.circle.fill",
        "request_quote": "doc.plaintext.fill",
        "business_center": "case.fill",
        "sell": "tag.fill",
        "card_membership": "giftcard.fill",
        "chat": "message.fill",
        "point_of_sale": "qrcode",
        "corporate_fare": "building.2.fill",
        "account_balance": "building.columns.fill",
        "badge": "person.text.rectangle.fill",
        "calculate": "plus.forwardslash.minus",
        "local_offer": "rosette",
        "attach_money": "dollarsign.circle.fill",
        "category": "square.grid.2x2.fill",
        "label": "tag.square.fill",

        // 账户 & 标签扩展图标
        "wallet": "wallet.pass.fill",
        "account_balance_wallet": "wallet.pass.fill",
        "credit_card": "creditcard.fill",
        "credit_score": "creditcard.circle.fill",
        "add_card": "plus.rectangle.fill",
        "local_atm": "banknote",
        "euro": "eurosign.circle.fill",
        "currency_pound": "sterlingsign.circle.fill",
        "currency_yen": "yensign.circle.fill",
        "currency_bitcoin": "bitcoinsign.circle.fill",
        "loyalty": "tag.circle.fill",
        "public": "globe",
        "verified": "checkmark.seal.fill",
        "person": "person.fill",
        "today": "calendar.circle.fill",
        "event_repeat": "repeat",
        "flag": "flag.fill",
        "bookmark": "bookmark.fill",
        "push_pin": "pin.fill",
        "check_circle": "checkmark.circle.fill",
        "auto_awesome": "sparkles.square.filled.on.square"
    ]

    /// 将 Android/跨端图标标识映射为 iOS 16+ 原生 SF Symbol 名称。
    public static func sfSymbol(for iconKey: String) -> String {
        let trimmed = iconKey.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty { return "square.grid.2x2.fill" }
        if let mapped = iconSymbolMap[trimmed.lowercased()] {
            return mapped
        }
        // 若本身已经是 SF Symbol 风格（含点号），直接透传
        if trimmed.contains(".") {
            return trimmed
        }
        return "square.grid.2x2.fill"
    }

    // MARK: - Preset Specs (16 Expense + 9 Income Categories = 115, 21 Tags, 9 Accounts)

    private static let presetCategories: [ParentSpec] = [
        // 支出 16 大类及 67 二级子类（83 个唯一 iconKey）
        ParentSpec(
            name: "餐饮美食", type: TransactionType.EXPENSE, icon: "restaurant", colorHex: 0xFFEA580C, sortOrder: 10,
            children: [
                ChildSpec(name: "三餐外食", icon: "ramen_dining"),
                ChildSpec(name: "外卖", icon: "delivery_dining"),
                ChildSpec(name: "咖啡茶饮", icon: "local_cafe"),
                ChildSpec(name: "零食甜品", icon: "bakery_dining"),
                ChildSpec(name: "水果生鲜", icon: "local_grocery_store"),
                ChildSpec(name: "买菜做饭", icon: "kitchen"),
                ChildSpec(name: "聚餐请客", icon: "local_bar"),
                ChildSpec(name: "烟酒茶", icon: "emoji_food_beverage")
            ]
        ),
        ParentSpec(
            name: "交通出行", type: TransactionType.EXPENSE, icon: "directions_car", colorHex: 0xFF0284C7, sortOrder: 20,
            children: [
                ChildSpec(name: "地铁公交", icon: "subway"),
                ChildSpec(name: "网约打车", icon: "local_taxi"),
                ChildSpec(name: "共享单车", icon: "pedal_bike"),
                ChildSpec(name: "高铁火车", icon: "train"),
                ChildSpec(name: "加油充电", icon: "local_gas_station"),
                ChildSpec(name: "停车过路", icon: "local_parking"),
                ChildSpec(name: "养车维修", icon: "local_car_wash")
            ]
        ),
        ParentSpec(
            name: "日用购物", type: TransactionType.EXPENSE, icon: "shopping_bag", colorHex: 0xFFDB2777, sortOrder: 30,
            children: [
                ChildSpec(name: "网购百货", icon: "local_mall"),
                ChildSpec(name: "超市便利", icon: "shopping_cart"),
                ChildSpec(name: "家居日用", icon: "weekend"),
                ChildSpec(name: "快递邮寄", icon: "local_shipping")
            ]
        ),
        ParentSpec(
            name: "服饰美容", type: TransactionType.EXPENSE, icon: "style", colorHex: 0xFFE11D48, sortOrder: 40,
            children: [
                ChildSpec(name: "服饰鞋包", icon: "checkroom"),
                ChildSpec(name: "美妆护肤", icon: "face_retouching_natural"),
                ChildSpec(name: "理发美甲", icon: "content_cut"),
                ChildSpec(name: "配饰首饰", icon: "diamond")
            ]
        ),
        ParentSpec(
            name: "数码家电", type: TransactionType.EXPENSE, icon: "devices", colorHex: 0xFF4F46E5, sortOrder: 50,
            children: [
                ChildSpec(name: "手机电脑", icon: "laptop_mac"),
                ChildSpec(name: "数码配件", icon: "headphones"),
                ChildSpec(name: "家用电器", icon: "tv")
            ]
        ),
        ParentSpec(
            name: "居家物业", type: TransactionType.EXPENSE, icon: "home", colorHex: 0xFF0D9488, sortOrder: 60,
            children: [
                ChildSpec(name: "房租房贷", icon: "cottage"),
                ChildSpec(name: "水电燃气", icon: "bolt"),
                ChildSpec(name: "物业维修", icon: "handyman"),
                ChildSpec(name: "话费宽带", icon: "wifi"),
                ChildSpec(name: "家政服务", icon: "cleaning_services")
            ]
        ),
        ParentSpec(
            name: "数字订阅", type: TransactionType.EXPENSE, icon: "subscriptions", colorHex: 0xFF7C3AED, sortOrder: 70,
            children: [
                ChildSpec(name: "软件会员", icon: "cloud_done"),
                ChildSpec(name: "AI与云服务", icon: "smart_toy"),
                ChildSpec(name: "影音音乐", icon: "music_note"),
                ChildSpec(name: "游戏充值", icon: "sports_esports")
            ]
        ),
        ParentSpec(
            name: "休闲娱乐", type: TransactionType.EXPENSE, icon: "theater_comedy", colorHex: 0xFF9333EA, sortOrder: 80,
            children: [
                ChildSpec(name: "电影演出", icon: "movie"),
                ChildSpec(name: "聚会玩乐", icon: "celebration"),
                ChildSpec(name: "运动健身", icon: "fitness_center"),
                ChildSpec(name: "户外露营", icon: "forest")
            ]
        ),
        ParentSpec(
            name: "医疗健康", type: TransactionType.EXPENSE, icon: "medical_services", colorHex: 0xFFDC2626, sortOrder: 90,
            children: [
                ChildSpec(name: "看病挂号", icon: "local_hospital"),
                ChildSpec(name: "买药保健", icon: "medication"),
                ChildSpec(name: "体检牙科", icon: "monitor_heart"),
                ChildSpec(name: "医疗保险", icon: "health_and_safety")
            ]
        ),
        ParentSpec(
            name: "差旅住宿", type: TransactionType.EXPENSE, icon: "explore", colorHex: 0xFF2563EB, sortOrder: 100,
            children: [
                ChildSpec(name: "机票船票", icon: "flight"),
                ChildSpec(name: "酒店民宿", icon: "hotel"),
                ChildSpec(name: "旅游门票", icon: "confirmation_number"),
                ChildSpec(name: "特产纪念", icon: "backpack")
            ]
        ),
        ParentSpec(
            name: "学习成长", type: TransactionType.EXPENSE, icon: "school", colorHex: 0xFF1677FF, sortOrder: 110,
            children: [
                ChildSpec(name: "书籍资料", icon: "book"),
                ChildSpec(name: "课程培训", icon: "science"),
                ChildSpec(name: "考试考证", icon: "edit_note"),
                ChildSpec(name: "文具办公", icon: "palette")
            ]
        ),
        ParentSpec(
            name: "人情社交", type: TransactionType.EXPENSE, icon: "groups", colorHex: 0xFFEC4899, sortOrder: 120,
            children: [
                ChildSpec(name: "红包礼金", icon: "card_giftcard"),
                ChildSpec(name: "送礼请客", icon: "redeem"),
                ChildSpec(name: "孝敬长辈", icon: "elderly"),
                ChildSpec(name: "恋爱约会", icon: "favorite")
            ]
        ),
        ParentSpec(
            name: "宠物生活", type: TransactionType.EXPENSE, icon: "pets", colorHex: 0xFFD97706, sortOrder: 130,
            children: [
                ChildSpec(name: "宠物食品", icon: "set_meal"),
                ChildSpec(name: "宠物用品", icon: "shopping_basket"),
                ChildSpec(name: "宠物医疗", icon: "healing")
            ]
        ),
        ParentSpec(
            name: "亲子育儿", type: TransactionType.EXPENSE, icon: "stroller", colorHex: 0xFFF59E0B, sortOrder: 140,
            children: [
                ChildSpec(name: "奶粉辅食", icon: "child_care"),
                ChildSpec(name: "童装玩具", icon: "toys"),
                ChildSpec(name: "早教培训", icon: "translate")
            ]
        ),
        ParentSpec(
            name: "金融保险", type: TransactionType.EXPENSE, icon: "assured_workload", colorHex: 0xFF64748B, sortOrder: 150,
            children: [
                ChildSpec(name: "汇损手续费", icon: "currency_exchange"),
                ChildSpec(name: "商业保险", icon: "verified_user"),
                ChildSpec(name: "利息罚息", icon: "percent"),
                ChildSpec(name: "投资亏损", icon: "trending_down")
            ]
        ),
        ParentSpec(
            name: "其他支出", type: TransactionType.EXPENSE, icon: "more_horiz", colorHex: 0xFF475569, sortOrder: 160,
            children: [
                ChildSpec(name: "公益捐赠", icon: "volunteer_activism"),
                ChildSpec(name: "意外丢失", icon: "priority_high")
            ]
        ),

        // 收入 9 大类及 23 二级子类（32 个唯一 iconKey）
        ParentSpec(
            name: "工资薪金", type: TransactionType.INCOME, icon: "work", colorHex: 0xFF16A34A, sortOrder: 10,
            children: [
                ChildSpec(name: "基本工资", icon: "payments"),
                ChildSpec(name: "加班津贴", icon: "schedule"),
                ChildSpec(name: "年终奖", icon: "emoji_events")
            ]
        ),
        ParentSpec(
            name: "奖金补贴", type: TransactionType.INCOME, icon: "star", colorHex: 0xFF15803D, sortOrder: 20,
            children: [
                ChildSpec(name: "绩效奖金", icon: "bar_chart"),
                ChildSpec(name: "餐补交通补", icon: "directions_bus"),
                ChildSpec(name: "节日福利", icon: "cake")
            ]
        ),
        ParentSpec(
            name: "投资理财", type: TransactionType.INCOME, icon: "trending_up", colorHex: 0xFF0D9488, sortOrder: 30,
            children: [
                ChildSpec(name: "基金股票", icon: "candlestick_chart"),
                ChildSpec(name: "银行利息", icon: "savings"),
                ChildSpec(name: "分红收益", icon: "pie_chart"),
                ChildSpec(name: "外汇收益", icon: "monetization_on")
            ]
        ),
        ParentSpec(
            name: "报销退款", type: TransactionType.INCOME, icon: "replay", colorHex: 0xFF0284C7, sortOrder: 40,
            children: [
                ChildSpec(name: "公司报销", icon: "receipt_long"),
                ChildSpec(name: "购物退款", icon: "local_post_office"),
                ChildSpec(name: "保险理赔", icon: "request_quote")
            ]
        ),
        ParentSpec(
            name: "兼职副业", type: TransactionType.INCOME, icon: "business_center", colorHex: 0xFF2563EB, sortOrder: 50,
            children: [
                ChildSpec(name: "外包私活", icon: "desktop_windows"),
                ChildSpec(name: "自媒体稿费", icon: "mic"),
                ChildSpec(name: "二手闲置", icon: "sell")
            ]
        ),
        ParentSpec(
            name: "人情礼金", type: TransactionType.INCOME, icon: "card_membership", colorHex: 0xFFDB2777, sortOrder: 60,
            children: [
                ChildSpec(name: "微信红包", icon: "chat"),
                ChildSpec(name: "礼金往来", icon: "handshake")
            ]
        ),
        ParentSpec(
            name: "经营所得", type: TransactionType.INCOME, icon: "storefront", colorHex: 0xFFEA580C, sortOrder: 70,
            children: [
                ChildSpec(name: "门店营收", icon: "point_of_sale"),
                ChildSpec(name: "租金收入", icon: "corporate_fare")
            ]
        ),
        ParentSpec(
            name: "政策补贴", type: TransactionType.INCOME, icon: "account_balance", colorHex: 0xFF006874, sortOrder: 80,
            children: [
                ChildSpec(name: "公积金提取", icon: "badge"),
                ChildSpec(name: "个税退税", icon: "calculate"),
                ChildSpec(name: "消费补贴", icon: "local_offer")
            ]
        ),
        ParentSpec(
            name: "其他收入", type: TransactionType.INCOME, icon: "attach_money", colorHex: 0xFF475569, sortOrder: 90,
            children: []
        )
    ]

    private static let presetTags: [TagSpec] = [
        // 渠道平台 (6)
        TagSpec(name: "拼多多", groupName: "渠道平台", colorHex: 0xFFE11D48, sortOrder: 10, icon: "loyalty"),
        TagSpec(name: "淘宝", groupName: "渠道平台", colorHex: 0xFFD97706, sortOrder: 20, icon: "shopping_bag"),
        TagSpec(name: "京东", groupName: "渠道平台", colorHex: 0xFFDC2626, sortOrder: 30, icon: "local_shipping"),
        TagSpec(name: "美团", groupName: "渠道平台", colorHex: 0xFFF59E0B, sortOrder: 40, icon: "fastfood"),
        TagSpec(name: "线下", groupName: "渠道平台", colorHex: 0xFF0D9488, sortOrder: 50, icon: "storefront"),
        TagSpec(name: "跨境", groupName: "渠道平台", colorHex: 0xFF4F46E5, sortOrder: 60, icon: "public"),
        // 场景行程 (4)
        TagSpec(name: "通勤", groupName: "场景行程", colorHex: 0xFF0369A1, sortOrder: 70, icon: "subway"),
        TagSpec(name: "差旅", groupName: "场景行程", colorHex: 0xFF2563EB, sortOrder: 80, icon: "luggage"),
        TagSpec(name: "旅行", groupName: "场景行程", colorHex: 0xFF0F766E, sortOrder: 90, icon: "flight"),
        TagSpec(name: "大促囤货", groupName: "场景行程", colorHex: 0xFF9333EA, sortOrder: 100, icon: "add_shopping_cart"),
        // 消费属性 (5)
        TagSpec(name: "刚需", groupName: "消费属性", colorHex: 0xFF16A34A, sortOrder: 110, icon: "verified"),
        TagSpec(name: "分期", groupName: "消费属性", colorHex: 0xFF64748B, sortOrder: 120, icon: "credit_card"),
        TagSpec(name: "优惠立减", groupName: "消费属性", colorHex: 0xFF15803D, sortOrder: 130, icon: "local_offer"),
        TagSpec(name: "冲动消费", groupName: "消费属性", colorHex: 0xFFE11D48, sortOrder: 140, icon: "bolt"),
        TagSpec(name: "大额", groupName: "消费属性", colorHex: 0xFFDC2626, sortOrder: 150, icon: "diamond"),
        // 对象账务 (6)
        TagSpec(name: "自己", groupName: "对象账务", colorHex: 0xFF2AA876, sortOrder: 160, icon: "person"),
        TagSpec(name: "家人", groupName: "对象账务", colorHex: 0xFFEA580C, sortOrder: 170, icon: "family_restroom"),
        TagSpec(name: "伴侣", groupName: "对象账务", colorHex: 0xFFEC4899, sortOrder: 180, icon: "favorite"),
        TagSpec(name: "AA", groupName: "对象账务", colorHex: 0xFF0284C7, sortOrder: 190, icon: "groups"),
        TagSpec(name: "可抵税", groupName: "对象账务", colorHex: 0xFF4F46E5, sortOrder: 200, icon: "calculate"),
        TagSpec(name: "装修", groupName: "对象账务", colorHex: 0xFF0D9488, sortOrder: 210, icon: "handyman")
    ]

    private static let removedDuplicateSystemCategories: Set<String> = [
        "\(TransactionType.EXPENSE)::其他杂项"
    ]

    private static let removedDuplicateSystemTags: Set<String> = [
        "外卖", "网购", "聚餐", "宠物", "请客", "公司报销", "定期订阅"
    ]

    private static let legacyCategoryIcons: [String: Set<String>] = [
        "\(TransactionType.EXPENSE)::三餐外食": ["lunch_dining"],
        "\(TransactionType.EXPENSE)::烟酒茶": ["coffee"],
        "\(TransactionType.EXPENSE)::养车维修": ["build"],
        "\(TransactionType.EXPENSE)::家居日用": ["store"],
        "\(TransactionType.EXPENSE)::服饰美容": ["checkroom"],
        "\(TransactionType.EXPENSE)::房租房贷": ["home"],
        "\(TransactionType.EXPENSE)::物业维修": ["build"],
        "\(TransactionType.EXPENSE)::休闲娱乐": ["sports_esports"],
        "\(TransactionType.EXPENSE)::体检牙科": ["medical_services"],
        "\(TransactionType.EXPENSE)::差旅住宿": ["flight"],
        "\(TransactionType.EXPENSE)::旅游门票": ["luggage"],
        "\(TransactionType.EXPENSE)::特产纪念": ["redeem"],
        "\(TransactionType.EXPENSE)::课程培训": ["school"],
        "\(TransactionType.EXPENSE)::文具办公": ["menu_book"],
        "\(TransactionType.EXPENSE)::人情社交": ["favorite"],
        "\(TransactionType.EXPENSE)::孝敬长辈": ["volunteer_activism"],
        "\(TransactionType.EXPENSE)::宠物食品": ["pets"],
        "\(TransactionType.EXPENSE)::宠物医疗": ["local_hospital"],
        "\(TransactionType.EXPENSE)::亲子育儿": ["child_care"],
        "\(TransactionType.EXPENSE)::童装玩具": ["redeem"],
        "\(TransactionType.EXPENSE)::早教培训": ["school"],
        "\(TransactionType.EXPENSE)::金融保险": ["currency_exchange"],
        "\(TransactionType.EXPENSE)::商业保险": ["health_and_safety"],
        "\(TransactionType.INCOME)::加班津贴": ["work"],
        "\(TransactionType.INCOME)::年终奖": ["card_giftcard"],
        "\(TransactionType.INCOME)::奖金补贴": ["card_giftcard"],
        "\(TransactionType.INCOME)::绩效奖金": ["trending_up"],
        "\(TransactionType.INCOME)::节日福利": ["redeem"],
        "\(TransactionType.INCOME)::基金股票": ["trending_up"],
        "\(TransactionType.INCOME)::分红收益": ["attach_money"],
        "\(TransactionType.INCOME)::外汇收益": ["currency_exchange"],
        "\(TransactionType.INCOME)::购物退款": ["replay"],
        "\(TransactionType.INCOME)::保险理赔": ["health_and_safety"],
        "\(TransactionType.INCOME)::兼职副业": ["laptop_mac"],
        "\(TransactionType.INCOME)::外包私活": ["laptop_mac"],
        "\(TransactionType.INCOME)::自媒体稿费": ["edit_note"],
        "\(TransactionType.INCOME)::人情礼金": ["redeem"],
        "\(TransactionType.INCOME)::微信红包": ["card_giftcard"],
        "\(TransactionType.INCOME)::门店营收": ["storefront"],
        "\(TransactionType.INCOME)::租金收入": ["home"],
        "\(TransactionType.INCOME)::公积金提取": ["account_balance"],
        "\(TransactionType.INCOME)::个税退税": ["receipt_long"]
    ]

    private static let legacyTagIcons: [String: Set<String>] = [
        "大促囤货": ["shopping_basket"],
        "家人": ["home"],
        "装修": ["build"]
    ]

    // MARK: - Builders

    public static func makeDefaultLedgers(nowMillis: Int64 = currentEpochMillis()) -> [LedgerDto] {
        [
            LedgerDto(
                id: 1,
                name: "默认账本",
                currencyCode: CurrencyCatalog.defaultBaseCurrency,
                icon: "menu_book",
                colorHex: 0xFF2AA876,
                sortOrder: 0,
                archived: false,
                createdAt: nowMillis
            )
        ]
    }

    public static func makeDefaultCategories() -> [CategoryDto] {
        var result: [CategoryDto] = []
        var nextId: Int64 = 1
        for parent in presetCategories {
            let parentId = nextId
            nextId += 1
            result.append(
                CategoryDto(
                    id: parentId,
                    parentId: nil,
                    name: parent.name,
                    type: parent.type,
                    icon: parent.icon,
                    colorHex: parent.colorHex,
                    sortOrder: parent.sortOrder,
                    isSystem: true
                )
            )
            for (index, child) in parent.children.enumerated() {
                let childId = nextId
                nextId += 1
                result.append(
                    CategoryDto(
                        id: childId,
                        parentId: parentId,
                        name: child.name,
                        type: parent.type,
                        icon: child.icon,
                        colorHex: parent.colorHex,
                        sortOrder: parent.sortOrder + index + 1,
                        isSystem: true
                    )
                )
            }
        }
        return result
    }

    private static func shouldUpgradeSystemCategoryIcon(
        _ existing: CategoryDto,
        targetIcon: String,
        key: String
    ) -> Bool {
        guard existing.isSystem, existing.icon != targetIcon else { return false }
        return existing.icon.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ||
            (legacyCategoryIcons[key]?.contains(existing.icon) ?? false)
    }

    /// 同步预置分类与标签目录：补齐缺失项、无损升级旧默认系统图标、并清理已去重的冗余系统项（不丢流水且保护自定义配置）。
    @discardableResult
    public static func syncPresetCatalog(
        categories: inout [CategoryDto],
        tags: inout [TagDto],
        transactions: inout [TransactionDto]
    ) -> (changed: Bool, addedCategories: Int, addedTags: Int) {
        var didMutate = false
        var addedCategories = 0

        var indexByKey: [String: Int] = [:]
        for (idx, cat) in categories.enumerated() {
            indexByKey["\(cat.type)::\(cat.name)"] = idx
        }
        var nextCategoryId = (categories.map(\.id).max() ?? 0) + 1

        for parent in presetCategories {
            let parentKey = "\(parent.type)::\(parent.name)"
            let parentId: Int64
            if let idx = indexByKey[parentKey] {
                let existing = categories[idx]
                if shouldUpgradeSystemCategoryIcon(existing, targetIcon: parent.icon, key: parentKey) {
                    categories[idx].icon = parent.icon
                    didMutate = true
                }
                parentId = existing.id
            } else {
                parentId = nextCategoryId
                nextCategoryId += 1
                let dto = CategoryDto(
                    id: parentId,
                    parentId: nil,
                    name: parent.name,
                    type: parent.type,
                    icon: parent.icon,
                    colorHex: parent.colorHex,
                    sortOrder: parent.sortOrder,
                    isSystem: true
                )
                categories.append(dto)
                indexByKey[parentKey] = categories.count - 1
                addedCategories += 1
                didMutate = true
            }

            for (idx, child) in parent.children.enumerated() {
                let childKey = "\(parent.type)::\(child.name)"
                if let childIdx = indexByKey[childKey] {
                    let existingChild = categories[childIdx]
                    if shouldUpgradeSystemCategoryIcon(existingChild, targetIcon: child.icon, key: childKey) {
                        categories[childIdx].icon = child.icon
                        didMutate = true
                    }
                } else {
                    let childId = nextCategoryId
                    nextCategoryId += 1
                    let dto = CategoryDto(
                        id: childId,
                        parentId: parentId,
                        name: child.name,
                        type: parent.type,
                        icon: child.icon,
                        colorHex: parent.colorHex,
                        sortOrder: parent.sortOrder + idx + 1,
                        isSystem: true
                    )
                    categories.append(dto)
                    indexByKey[childKey] = categories.count - 1
                    addedCategories += 1
                    didMutate = true
                }
            }
        }

        // 无损移除历史冗余系统子分类（如 “其他支出/其他杂项”），并将对应交易归并至父级
        let fallbackOtherExpenseId = indexByKey["\(TransactionType.EXPENSE)::其他支出"].map { categories[$0].id }
        var removedCategoryIds: Set<Int64> = []
        for cat in categories where cat.isSystem {
            let key = "\(cat.type)::\(cat.name)"
            if removedDuplicateSystemCategories.contains(key) {
                removedCategoryIds.insert(cat.id)
            }
        }
        if !removedCategoryIds.isEmpty {
            if let fallbackId = fallbackOtherExpenseId {
                for i in transactions.indices {
                    if let cid = transactions[i].categoryId, removedCategoryIds.contains(cid) {
                        transactions[i].categoryId = fallbackId
                    }
                }
            }
            categories.removeAll { removedCategoryIds.contains($0.id) }
            didMutate = true
        }

        // 同步标签目录：先移除冗余系统标签，再升级保留的系统标签图标并补齐新标签
        let beforeTagCount = tags.count
        tags.removeAll { $0.isSystem && removedDuplicateSystemTags.contains($0.name) }
        if tags.count != beforeTagCount {
            didMutate = true
        }

        var tagIndexByName: [String: Int] = [:]
        for (idx, tag) in tags.enumerated() {
            tagIndexByName[tag.name] = idx
        }
        var nextTagId = (tags.map(\.id).max() ?? 0) + 1
        var addedTags = 0

        for spec in presetTags {
            if let idx = tagIndexByName[spec.name] {
                let existing = tags[idx]
                let emptyIcon = existing.icon.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
                let isLegacy = legacyTagIcons[spec.name]?.contains(existing.icon) ?? false
                if existing.isSystem, existing.icon != spec.icon, (emptyIcon || isLegacy) {
                    tags[idx] = TagDto(
                        id: existing.id,
                        name: existing.name,
                        groupName: existing.groupName,
                        colorHex: existing.colorHex,
                        sortOrder: existing.sortOrder,
                        isSystem: existing.isSystem,
                        icon: spec.icon
                    )
                    didMutate = true
                }
            } else {
                tags.append(
                    TagDto(
                        id: nextTagId,
                        name: spec.name,
                        groupName: spec.groupName,
                        colorHex: spec.colorHex,
                        sortOrder: spec.sortOrder,
                        isSystem: true,
                        icon: spec.icon
                    )
                )
                tagIndexByName[spec.name] = tags.count - 1
                nextTagId += 1
                addedTags += 1
                didMutate = true
            }
        }

        return (didMutate, addedCategories, addedTags)
    }

    /// 幂等补齐缺失的预置分类，返回新增数量。
    public static func mergeMissingPresetCategories(into categories: inout [CategoryDto]) -> Int {
        var indexByKey: [String: Int] = [:]
        for (idx, cat) in categories.enumerated() {
            indexByKey["\(cat.type)::\(cat.name)"] = idx
        }
        var nextId = (categories.map(\.id).max() ?? 0) + 1
        var added = 0

        for parent in presetCategories {
            let parentKey = "\(parent.type)::\(parent.name)"
            let parentId: Int64
            if let idx = indexByKey[parentKey] {
                if shouldUpgradeSystemCategoryIcon(categories[idx], targetIcon: parent.icon, key: parentKey) {
                    categories[idx].icon = parent.icon
                }
                parentId = categories[idx].id
            } else {
                parentId = nextId
                nextId += 1
                let dto = CategoryDto(
                    id: parentId,
                    parentId: nil,
                    name: parent.name,
                    type: parent.type,
                    icon: parent.icon,
                    colorHex: parent.colorHex,
                    sortOrder: parent.sortOrder,
                    isSystem: true
                )
                categories.append(dto)
                indexByKey[parentKey] = categories.count - 1
                added += 1
            }

            for (idx, child) in parent.children.enumerated() {
                let childKey = "\(parent.type)::\(child.name)"
                if let childIdx = indexByKey[childKey] {
                    if shouldUpgradeSystemCategoryIcon(categories[childIdx], targetIcon: child.icon, key: childKey) {
                        categories[childIdx].icon = child.icon
                    }
                } else {
                    let childId = nextId
                    nextId += 1
                    let dto = CategoryDto(
                        id: childId,
                        parentId: parentId,
                        name: child.name,
                        type: parent.type,
                        icon: child.icon,
                        colorHex: parent.colorHex,
                        sortOrder: parent.sortOrder + idx + 1,
                        isSystem: true
                    )
                    categories.append(dto)
                    indexByKey[childKey] = categories.count - 1
                    added += 1
                }
            }
        }
        categories.removeAll { $0.isSystem && removedDuplicateSystemCategories.contains("\($0.type)::\($0.name)") }
        return added
    }

    public static func presetCategoryIconKeys() -> [String: String] {
        var result: [String: String] = [:]
        for parent in presetCategories {
            result["\(parent.type)::\(parent.name)"] = parent.icon
            for child in parent.children {
                result["\(parent.type)::\(child.name)"] = child.icon
            }
        }
        return result
    }

    public static func presetTagIconKeys() -> [String: String] {
        Dictionary(uniqueKeysWithValues: presetTags.map { ($0.name, $0.icon) })
    }

    public static func makeDefaultTags() -> [TagDto] {
        presetTags.enumerated().map { idx, spec in
            TagDto(
                id: Int64(idx + 1),
                name: spec.name,
                groupName: spec.groupName,
                colorHex: spec.colorHex,
                sortOrder: spec.sortOrder,
                isSystem: true,
                icon: spec.icon
            )
        }
    }

    /// 幂等补齐缺失的 21 个预置标签并无损升级旧系统图标，返回新增数量。
    public static func mergeMissingPresetTags(into tags: inout [TagDto]) -> Int {
        tags.removeAll { $0.isSystem && removedDuplicateSystemTags.contains($0.name) }
        var tagIndexByName: [String: Int] = [:]
        for (idx, tag) in tags.enumerated() {
            tagIndexByName[tag.name] = idx
        }
        var nextId = (tags.map(\.id).max() ?? 0) + 1
        var added = 0

        for spec in presetTags {
            if let idx = tagIndexByName[spec.name] {
                let existing = tags[idx]
                let emptyIcon = existing.icon.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
                let isLegacy = legacyTagIcons[spec.name]?.contains(existing.icon) ?? false
                if existing.isSystem, existing.icon != spec.icon, (emptyIcon || isLegacy) {
                    tags[idx] = TagDto(
                        id: existing.id,
                        name: existing.name,
                        groupName: existing.groupName,
                        colorHex: existing.colorHex,
                        sortOrder: existing.sortOrder,
                        isSystem: existing.isSystem,
                        icon: spec.icon
                    )
                }
            } else {
                tags.append(
                    TagDto(
                        id: nextId,
                        name: spec.name,
                        groupName: spec.groupName,
                        colorHex: spec.colorHex,
                        sortOrder: spec.sortOrder,
                        isSystem: true,
                        icon: spec.icon
                    )
                )
                tagIndexByName[spec.name] = tags.count - 1
                nextId += 1
                added += 1
            }
        }
        return added
    }

    public static func makeDefaultAccounts(nowMillis: Int64 = currentEpochMillis()) -> (accounts: [AccountDto], aliases: [AccountAliasDto]) {
        let accounts: [AccountDto] = [
            AccountDto(id: 1, name: "微信零钱", groupType: AccountGroup.WALLET, currencyCode: "CNY", balanceMicros: 0, icon: "chat", colorHex: 0xFF07C160, sortOrder: 10, createdAt: nowMillis, updatedAt: nowMillis),
            AccountDto(id: 2, name: "支付宝", groupType: AccountGroup.WALLET, currencyCode: "CNY", balanceMicros: 0, icon: "account_balance_wallet", colorHex: 0xFF1677FF, sortOrder: 20, createdAt: nowMillis, updatedAt: nowMillis),
            AccountDto(id: 3, name: "公交卡", groupType: AccountGroup.WALLET, currencyCode: "CNY", balanceMicros: 0, icon: "directions_bus", colorHex: 0xFF0284C7, sortOrder: 25, createdAt: nowMillis, updatedAt: nowMillis),
            AccountDto(id: 4, name: "现金", groupType: AccountGroup.WALLET, currencyCode: "CNY", balanceMicros: 0, icon: "payments", colorHex: 0xFF0F766E, sortOrder: 30, createdAt: nowMillis, updatedAt: nowMillis),
            AccountDto(id: 5, name: "银行储蓄卡", groupType: AccountGroup.DEBIT, currencyCode: "CNY", balanceMicros: 0, icon: "account_balance", colorHex: 0xFF0B57D0, sortOrder: 40, createdAt: nowMillis, updatedAt: nowMillis),
            AccountDto(id: 6, name: "全币种信用卡", groupType: AccountGroup.CREDIT, currencyCode: "CNY", balanceMicros: 0, creditLimitMicros: 50_000 * MoneyAmount.amountScale, billingDay: 5, repaymentDay: 23, icon: "credit_card", colorHex: 0xFFDC2626, sortOrder: 50, createdAt: nowMillis, updatedAt: nowMillis),
            AccountDto(id: 7, name: "美元账户 (USD)", groupType: AccountGroup.FOREIGN, currencyCode: "USD", balanceMicros: 0, icon: "attach_money", colorHex: 0xFF1D4ED8, sortOrder: 60, createdAt: nowMillis, updatedAt: nowMillis),
            AccountDto(id: 8, name: "欧元账户 (EUR)", groupType: AccountGroup.FOREIGN, currencyCode: "EUR", balanceMicros: 0, icon: "euro", colorHex: 0xFF0369A1, sortOrder: 70, createdAt: nowMillis, updatedAt: nowMillis),
            AccountDto(id: 9, name: "英镑账户 (GBP)", groupType: AccountGroup.FOREIGN, currencyCode: "GBP", balanceMicros: 0, icon: "currency_pound", colorHex: 0xFF6D28D9, sortOrder: 80, createdAt: nowMillis, updatedAt: nowMillis)
        ]

        let aliases: [AccountAliasDto] = [
            AccountAliasDto(id: 1, accountId: 1, matchType: AliasMatchType.KEYWORD, pattern: "微信支付", priority: 80),
            AccountAliasDto(id: 2, accountId: 1, matchType: AliasMatchType.KEYWORD, pattern: "零钱", priority: 70),
            AccountAliasDto(id: 3, accountId: 2, matchType: AliasMatchType.KEYWORD, pattern: "支付宝", priority: 85),
            AccountAliasDto(id: 4, accountId: 2, matchType: AliasMatchType.KEYWORD, pattern: "余额宝", priority: 80),
            AccountAliasDto(id: 5, accountId: 2, matchType: AliasMatchType.KEYWORD, pattern: "花呗", priority: 75),
            AccountAliasDto(id: 6, accountId: 3, matchType: AliasMatchType.KEYWORD, pattern: "公交卡", priority: 95),
            AccountAliasDto(id: 7, accountId: 3, matchType: AliasMatchType.KEYWORD, pattern: "交通卡", priority: 95),
            AccountAliasDto(id: 8, accountId: 4, matchType: AliasMatchType.KEYWORD, pattern: "现金", priority: 40),
            AccountAliasDto(id: 9, accountId: 5, matchType: AliasMatchType.KEYWORD, pattern: "储蓄卡", priority: 60),
            AccountAliasDto(id: 10, accountId: 6, matchType: AliasMatchType.KEYWORD, pattern: "信用卡", priority: 65),
            AccountAliasDto(id: 11, accountId: 6, matchType: AliasMatchType.KEYWORD, pattern: "Visa", priority: 60),
            AccountAliasDto(id: 12, accountId: 6, matchType: AliasMatchType.KEYWORD, pattern: "Mastercard", priority: 60),
            AccountAliasDto(id: 13, accountId: 7, matchType: AliasMatchType.KEYWORD, pattern: "美元账户", priority: 70),
            AccountAliasDto(id: 14, accountId: 8, matchType: AliasMatchType.KEYWORD, pattern: "欧元账户", priority: 70),
            AccountAliasDto(id: 15, accountId: 9, matchType: AliasMatchType.KEYWORD, pattern: "英镑账户", priority: 70)
        ]

        return (accounts, aliases)
    }

    public static func makeDefaultExchangeRates(nowMillis: Int64 = currentEpochMillis()) -> [ExchangeRateDto] {
        let today = formatISODate(epochMillis: nowMillis)
        var result: [ExchangeRateDto] = []
        var nextId: Int64 = 1
        for info in CurrencyCatalog.all where info.code != CurrencyCatalog.defaultBaseCurrency {
            let toCny = info.fallbackRateToCnyMicros
            let fromCny = MoneyAmount.invertRate(toCny)
            result.append(
                ExchangeRateDto(
                    id: nextId,
                    baseCurrency: info.code,
                    targetCurrency: CurrencyCatalog.defaultBaseCurrency,
                    rateMicros: toCny,
                    date: today,
                    source: "BUILTIN",
                    updatedAt: nowMillis
                )
            )
            nextId += 1
            result.append(
                ExchangeRateDto(
                    id: nextId,
                    baseCurrency: CurrencyCatalog.defaultBaseCurrency,
                    targetCurrency: info.code,
                    rateMicros: fromCny,
                    date: today,
                    source: "BUILTIN",
                    updatedAt: nowMillis
                )
            )
            nextId += 1
        }
        return result
    }

    /// 创建全新的初始化完整快照（含默认账本、全量分类、21 标签、9 账户与离线兜底汇率）。
    public static func makeInitialSnapshot(nowMillis: Int64 = currentEpochMillis()) -> KairoBackupSnapshot {
        let ledgers = makeDefaultLedgers(nowMillis: nowMillis)
        let categories = makeDefaultCategories()
        let tags = makeDefaultTags()
        let (accounts, aliases) = makeDefaultAccounts(nowMillis: nowMillis)
        let rates = makeDefaultExchangeRates(nowMillis: nowMillis)
        var settings = SettingsSnapshotDto()
        settings.activeLedgerId = ledgers.first?.id ?? 1

        return KairoBackupSnapshot(
            formatVersion: KairoBackupSnapshot.currentFormatVersion,
            exportedAtMillis: nowMillis,
            appName: KairoBackupSnapshot.appName,
            settings: settings,
            accounts: accounts,
            categories: categories,
            transactions: [],
            exchangeRates: rates,
            accountAliases: aliases,
            parseRules: [],
            rawCaptureLogs: [],
            budgets: [],
            recurringRules: [],
            ledgers: ledgers,
            debts: [],
            installments: [],
            tags: tags
        )
    }

    public static func formatISODate(epochMillis: Int64, timeZone: TimeZone = .current) -> String {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = timeZone
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter.string(from: Date(timeIntervalSince1970: Double(epochMillis) / 1000.0))
    }
}

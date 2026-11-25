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
        // 餐饮美食
        "restaurant": "fork.knife",
        "lunch_dining": "takeoutbag.and.cup.and.straw.fill",
        "delivery_dining": "bicycle",
        "local_cafe": "cup.and.saucer.fill",
        "bakery_dining": "birthday.cake.fill",
        "local_grocery_store": "carrot.fill",
        "kitchen": "frying.pan.fill",
        "local_bar": "wineglass.fill",
        "coffee": "mug.fill",
        "fastfood": "takeoutbag.and.cup.and.straw.fill",
        // 交通出行
        "directions_car": "car.fill",
        "subway": "tram.fill",
        "local_taxi": "car.2.fill",
        "pedal_bike": "bicycle",
        "train": "train.side.front.car",
        "local_gas_station": "fuelpump.fill",
        "local_parking": "parkingsign.circle.fill",
        "build": "wrench.and.screwdriver.fill",
        "directions_bus": "bus.fill",
        // 日用购物
        "shopping_bag": "bag.fill",
        "local_mall": "bag.fill",
        "shopping_cart": "cart.fill",
        "store": "house.fill",
        "local_shipping": "shippingbox.fill",
        "shopping_basket": "basket.fill",
        // 服饰美容
        "checkroom": "tshirt.fill",
        "face_retouching_natural": "sparkles",
        "content_cut": "scissors",
        "diamond": "diamond.fill",
        // 数码家电
        "devices": "laptopcomputer.and.iphone",
        "laptop_mac": "laptopcomputer",
        "headphones": "headphones",
        "tv": "tv.fill",
        // 居家物业
        "home": "house.fill",
        "bolt": "bolt.fill",
        "wifi": "wifi",
        "cleaning_services": "bubbles.and.sparkles.fill",
        // 数字订阅
        "subscriptions": "repeat.circle.fill",
        "cloud_done": "icloud.fill",
        "smart_toy": "cpu.fill",
        "music_note": "music.note",
        "sports_esports": "gamecontroller.fill",
        // 休闲娱乐
        "movie": "film.fill",
        "celebration": "party.popper.fill",
        "fitness_center": "dumbbell.fill",
        "forest": "tree.fill",
        // 医疗健康
        "medical_services": "cross.case.fill",
        "local_hospital": "cross.circle.fill",
        "medication": "pills.fill",
        "health_and_safety": "heart.text.square.fill",
        // 差旅住宿
        "flight": "airplane",
        "hotel": "bed.double.fill",
        "luggage": "suitcase.fill",
        "redeem": "gift.fill",
        // 学习成长
        "school": "graduationcap.fill",
        "book": "book.fill",
        "edit_note": "pencil.and.list.clipboard",
        "menu_book": "book.closed.fill",
        // 人情社交
        "favorite": "heart.fill",
        "card_giftcard": "giftcard.fill",
        "volunteer_activism": "hands.sparkles.fill",
        "handshake": "person.2.fill",
        // 宠物 & 亲子
        "pets": "pawprint.fill",
        "child_care": "figure.and.child.holdinghands",
        // 金融 & 收入
        "currency_exchange": "arrow.triangle.2.circlepath.circle.fill",
        "percent": "percent",
        "trending_down": "chart.line.downtrend.xyaxis",
        "trending_up": "chart.line.uptrend.xyaxis",
        "more_horiz": "ellipsis.circle.fill",
        "category": "square.grid.2x2.fill",
        "work": "briefcase.fill",
        "payments": "banknote.fill",
        "savings": "dollarsign.circle.fill",
        "attach_money": "dollarsign.circle.fill",
        "replay": "arrow.uturn.backward.circle.fill",
        "receipt_long": "doc.text.fill",
        "sell": "tag.fill",
        "storefront": "storefront.fill",
        "account_balance": "building.columns.fill",
        "local_offer": "tag.fill",
        // 账户 & 标签
        "wallet": "wallet.pass.fill",
        "chat": "message.fill",
        "account_balance_wallet": "wallet.pass.fill",
        "credit_card": "creditcard.fill",
        "euro": "eurosign.circle.fill",
        "currency_pound": "sterlingsign.circle.fill",
        "loyalty": "tag.fill",
        "public": "globe",
        "verified": "checkmark.seal.fill",
        "person": "person.fill",
        "groups": "person.3.fill",
        "calculate": "plus.forwardslash.minus"
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

    // MARK: - Preset Specs (16 Expense + 9 Income Categories, 28 Tags, 9 Accounts)

    private static let presetCategories: [ParentSpec] = [
        // 支出 16 大类及二级子类
        ParentSpec(
            name: "餐饮美食", type: TransactionType.EXPENSE, icon: "restaurant", colorHex: 0xFFEA580C, sortOrder: 10,
            children: [
                ChildSpec(name: "三餐外食", icon: "lunch_dining"),
                ChildSpec(name: "外卖", icon: "delivery_dining"),
                ChildSpec(name: "咖啡茶饮", icon: "local_cafe"),
                ChildSpec(name: "零食甜品", icon: "bakery_dining"),
                ChildSpec(name: "水果生鲜", icon: "local_grocery_store"),
                ChildSpec(name: "买菜做饭", icon: "kitchen"),
                ChildSpec(name: "聚餐请客", icon: "local_bar"),
                ChildSpec(name: "烟酒茶", icon: "coffee")
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
                ChildSpec(name: "养车维修", icon: "build")
            ]
        ),
        ParentSpec(
            name: "日用购物", type: TransactionType.EXPENSE, icon: "shopping_bag", colorHex: 0xFFDB2777, sortOrder: 30,
            children: [
                ChildSpec(name: "网购百货", icon: "local_mall"),
                ChildSpec(name: "超市便利", icon: "shopping_cart"),
                ChildSpec(name: "家居日用", icon: "store"),
                ChildSpec(name: "快递邮寄", icon: "local_shipping")
            ]
        ),
        ParentSpec(
            name: "服饰美容", type: TransactionType.EXPENSE, icon: "checkroom", colorHex: 0xFFE11D48, sortOrder: 40,
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
                ChildSpec(name: "房租房贷", icon: "home"),
                ChildSpec(name: "水电燃气", icon: "bolt"),
                ChildSpec(name: "物业维修", icon: "build"),
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
            name: "休闲娱乐", type: TransactionType.EXPENSE, icon: "sports_esports", colorHex: 0xFF9333EA, sortOrder: 80,
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
                ChildSpec(name: "体检牙科", icon: "medical_services"),
                ChildSpec(name: "医疗保险", icon: "health_and_safety")
            ]
        ),
        ParentSpec(
            name: "差旅住宿", type: TransactionType.EXPENSE, icon: "flight", colorHex: 0xFF2563EB, sortOrder: 100,
            children: [
                ChildSpec(name: "机票船票", icon: "flight"),
                ChildSpec(name: "酒店民宿", icon: "hotel"),
                ChildSpec(name: "旅游门票", icon: "luggage"),
                ChildSpec(name: "特产纪念", icon: "redeem")
            ]
        ),
        ParentSpec(
            name: "学习成长", type: TransactionType.EXPENSE, icon: "school", colorHex: 0xFF1677FF, sortOrder: 110,
            children: [
                ChildSpec(name: "书籍资料", icon: "book"),
                ChildSpec(name: "课程培训", icon: "school"),
                ChildSpec(name: "考试考证", icon: "edit_note"),
                ChildSpec(name: "文具办公", icon: "menu_book")
            ]
        ),
        ParentSpec(
            name: "人情社交", type: TransactionType.EXPENSE, icon: "favorite", colorHex: 0xFFEC4899, sortOrder: 120,
            children: [
                ChildSpec(name: "红包礼金", icon: "card_giftcard"),
                ChildSpec(name: "送礼请客", icon: "redeem"),
                ChildSpec(name: "孝敬长辈", icon: "volunteer_activism"),
                ChildSpec(name: "恋爱约会", icon: "favorite")
            ]
        ),
        ParentSpec(
            name: "宠物生活", type: TransactionType.EXPENSE, icon: "pets", colorHex: 0xFFD97706, sortOrder: 130,
            children: [
                ChildSpec(name: "宠物食品", icon: "pets"),
                ChildSpec(name: "宠物用品", icon: "shopping_basket"),
                ChildSpec(name: "宠物医疗", icon: "local_hospital")
            ]
        ),
        ParentSpec(
            name: "亲子育儿", type: TransactionType.EXPENSE, icon: "child_care", colorHex: 0xFFF59E0B, sortOrder: 140,
            children: [
                ChildSpec(name: "奶粉辅食", icon: "child_care"),
                ChildSpec(name: "童装玩具", icon: "redeem"),
                ChildSpec(name: "早教培训", icon: "school")
            ]
        ),
        ParentSpec(
            name: "金融保险", type: TransactionType.EXPENSE, icon: "currency_exchange", colorHex: 0xFF64748B, sortOrder: 150,
            children: [
                ChildSpec(name: "汇损手续费", icon: "currency_exchange"),
                ChildSpec(name: "商业保险", icon: "health_and_safety"),
                ChildSpec(name: "利息罚息", icon: "percent"),
                ChildSpec(name: "投资亏损", icon: "trending_down")
            ]
        ),
        ParentSpec(
            name: "其他支出", type: TransactionType.EXPENSE, icon: "more_horiz", colorHex: 0xFF475569, sortOrder: 160,
            children: [
                ChildSpec(name: "公益捐赠", icon: "volunteer_activism"),
                ChildSpec(name: "意外丢失", icon: "more_horiz"),
                ChildSpec(name: "其他杂项", icon: "category")
            ]
        ),

        // 收入 9 大类及二级子类
        ParentSpec(
            name: "工资薪金", type: TransactionType.INCOME, icon: "work", colorHex: 0xFF16A34A, sortOrder: 10,
            children: [
                ChildSpec(name: "基本工资", icon: "payments"),
                ChildSpec(name: "加班津贴", icon: "work"),
                ChildSpec(name: "年终奖", icon: "card_giftcard")
            ]
        ),
        ParentSpec(
            name: "奖金补贴", type: TransactionType.INCOME, icon: "card_giftcard", colorHex: 0xFF15803D, sortOrder: 20,
            children: [
                ChildSpec(name: "绩效奖金", icon: "trending_up"),
                ChildSpec(name: "餐补交通补", icon: "directions_bus"),
                ChildSpec(name: "节日福利", icon: "redeem")
            ]
        ),
        ParentSpec(
            name: "投资理财", type: TransactionType.INCOME, icon: "trending_up", colorHex: 0xFF0D9488, sortOrder: 30,
            children: [
                ChildSpec(name: "基金股票", icon: "trending_up"),
                ChildSpec(name: "银行利息", icon: "savings"),
                ChildSpec(name: "分红收益", icon: "attach_money"),
                ChildSpec(name: "外汇收益", icon: "currency_exchange")
            ]
        ),
        ParentSpec(
            name: "报销退款", type: TransactionType.INCOME, icon: "replay", colorHex: 0xFF0284C7, sortOrder: 40,
            children: [
                ChildSpec(name: "公司报销", icon: "receipt_long"),
                ChildSpec(name: "购物退款", icon: "replay"),
                ChildSpec(name: "保险理赔", icon: "health_and_safety")
            ]
        ),
        ParentSpec(
            name: "兼职副业", type: TransactionType.INCOME, icon: "laptop_mac", colorHex: 0xFF2563EB, sortOrder: 50,
            children: [
                ChildSpec(name: "外包私活", icon: "laptop_mac"),
                ChildSpec(name: "自媒体稿费", icon: "edit_note"),
                ChildSpec(name: "二手闲置", icon: "sell")
            ]
        ),
        ParentSpec(
            name: "人情礼金", type: TransactionType.INCOME, icon: "redeem", colorHex: 0xFFDB2777, sortOrder: 60,
            children: [
                ChildSpec(name: "微信红包", icon: "card_giftcard"),
                ChildSpec(name: "礼金往来", icon: "handshake")
            ]
        ),
        ParentSpec(
            name: "经营所得", type: TransactionType.INCOME, icon: "storefront", colorHex: 0xFFEA580C, sortOrder: 70,
            children: [
                ChildSpec(name: "门店营收", icon: "storefront"),
                ChildSpec(name: "租金收入", icon: "home")
            ]
        ),
        ParentSpec(
            name: "政策补贴", type: TransactionType.INCOME, icon: "account_balance", colorHex: 0xFF006874, sortOrder: 80,
            children: [
                ChildSpec(name: "公积金提取", icon: "account_balance"),
                ChildSpec(name: "个税退税", icon: "receipt_long"),
                ChildSpec(name: "消费补贴", icon: "local_offer")
            ]
        ),
        ParentSpec(
            name: "其他收入", type: TransactionType.INCOME, icon: "attach_money", colorHex: 0xFF475569, sortOrder: 90,
            children: []
        )
    ]

    private static let presetTags: [TagSpec] = [
        // 渠道平台 (8)
        TagSpec(name: "外卖", groupName: "渠道平台", colorHex: 0xFFEA580C, sortOrder: 10, icon: "delivery_dining"),
        TagSpec(name: "网购", groupName: "渠道平台", colorHex: 0xFF0284C7, sortOrder: 20, icon: "shopping_cart"),
        TagSpec(name: "拼多多", groupName: "渠道平台", colorHex: 0xFFE11D48, sortOrder: 30, icon: "loyalty"),
        TagSpec(name: "淘宝", groupName: "渠道平台", colorHex: 0xFFD97706, sortOrder: 40, icon: "shopping_bag"),
        TagSpec(name: "京东", groupName: "渠道平台", colorHex: 0xFFDC2626, sortOrder: 50, icon: "local_shipping"),
        TagSpec(name: "美团", groupName: "渠道平台", colorHex: 0xFFF59E0B, sortOrder: 60, icon: "fastfood"),
        TagSpec(name: "线下", groupName: "渠道平台", colorHex: 0xFF0D9488, sortOrder: 70, icon: "storefront"),
        TagSpec(name: "跨境", groupName: "渠道平台", colorHex: 0xFF4F46E5, sortOrder: 80, icon: "public"),
        // 场景行程 (5)
        TagSpec(name: "通勤", groupName: "场景行程", colorHex: 0xFF0369A1, sortOrder: 90, icon: "subway"),
        TagSpec(name: "差旅", groupName: "场景行程", colorHex: 0xFF2563EB, sortOrder: 100, icon: "luggage"),
        TagSpec(name: "旅行", groupName: "场景行程", colorHex: 0xFF0F766E, sortOrder: 110, icon: "flight"),
        TagSpec(name: "聚餐", groupName: "场景行程", colorHex: 0xFFDB2777, sortOrder: 120, icon: "celebration"),
        TagSpec(name: "大促囤货", groupName: "场景行程", colorHex: 0xFF9333EA, sortOrder: 130, icon: "shopping_basket"),
        // 消费属性 (6)
        TagSpec(name: "刚需", groupName: "消费属性", colorHex: 0xFF16A34A, sortOrder: 140, icon: "verified"),
        TagSpec(name: "定期订阅", groupName: "消费属性", colorHex: 0xFF7C3AED, sortOrder: 150, icon: "subscriptions"),
        TagSpec(name: "分期", groupName: "消费属性", colorHex: 0xFF64748B, sortOrder: 160, icon: "credit_card"),
        TagSpec(name: "优惠立减", groupName: "消费属性", colorHex: 0xFF15803D, sortOrder: 170, icon: "local_offer"),
        TagSpec(name: "冲动消费", groupName: "消费属性", colorHex: 0xFFE11D48, sortOrder: 180, icon: "bolt"),
        TagSpec(name: "大额", groupName: "消费属性", colorHex: 0xFFDC2626, sortOrder: 190, icon: "diamond"),
        // 对象账务 (9)
        TagSpec(name: "自己", groupName: "对象账务", colorHex: 0xFF2AA876, sortOrder: 200, icon: "person"),
        TagSpec(name: "家人", groupName: "对象账务", colorHex: 0xFFEA580C, sortOrder: 210, icon: "home"),
        TagSpec(name: "伴侣", groupName: "对象账务", colorHex: 0xFFEC4899, sortOrder: 220, icon: "favorite"),
        TagSpec(name: "宠物", groupName: "对象账务", colorHex: 0xFFD97706, sortOrder: 230, icon: "pets"),
        TagSpec(name: "请客", groupName: "对象账务", colorHex: 0xFFF59E0B, sortOrder: 240, icon: "redeem"),
        TagSpec(name: "AA", groupName: "对象账务", colorHex: 0xFF0284C7, sortOrder: 250, icon: "groups"),
        TagSpec(name: "公司报销", groupName: "对象账务", colorHex: 0xFF1677FF, sortOrder: 260, icon: "receipt_long"),
        TagSpec(name: "可抵税", groupName: "对象账务", colorHex: 0xFF4F46E5, sortOrder: 270, icon: "calculate"),
        TagSpec(name: "装修", groupName: "对象账务", colorHex: 0xFF0D9488, sortOrder: 280, icon: "build")
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

    /// 幂等补齐缺失的预置分类，返回新增数量。
    public static func mergeMissingPresetCategories(into categories: inout [CategoryDto]) -> Int {
        var existingByKey: [String: CategoryDto] = [:]
        for cat in categories {
            existingByKey["\(cat.type)::\(cat.name)"] = cat
        }
        var nextId = (categories.map(\.id).max() ?? 0) + 1
        var added = 0

        for parent in presetCategories {
            let parentKey = "\(parent.type)::\(parent.name)"
            let parentId: Int64
            if let existing = existingByKey[parentKey] {
                parentId = existing.id
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
                existingByKey[parentKey] = dto
                added += 1
            }

            for (idx, child) in parent.children.enumerated() {
                let childKey = "\(parent.type)::\(child.name)"
                if existingByKey[childKey] == nil {
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
                    existingByKey[childKey] = dto
                    added += 1
                }
            }
        }
        return added
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

    /// 幂等补齐缺失的 28 个预置标签，返回新增数量。
    public static func mergeMissingPresetTags(into tags: inout [TagDto]) -> Int {
        var existingNames = Set(tags.map(\.name))
        var nextId = (tags.map(\.id).max() ?? 0) + 1
        var added = 0

        for spec in presetTags {
            if existingNames.insert(spec.name).inserted {
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

    /// 创建全新的初始化完整快照（含默认账本、全量分类、28 标签、9 账户与离线兜底汇率）。
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

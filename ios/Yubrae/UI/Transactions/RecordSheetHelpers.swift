import Foundation

// MARK: - 四则算式求值器（与 Android QuickExprEval 完全对齐，全程使用 Decimal）

public enum QuickExprEvaluator {
    private static let operatorChars: Set<Character> = ["+", "-", "×", "÷", "*", "/"]
    private static let posixLocale = Locale(identifier: "en_US_POSIX")

    public static func isOperator(_ ch: Character) -> Bool {
        operatorChars.contains(ch)
    }

    public static func containsOperator(_ expr: String) -> Bool {
        guard expr.count > 1 else { return false }
        for (idx, ch) in expr.enumerated() where idx > 0 && isOperator(ch) {
            return true
        }
        return false
    }

    private static func lastOperatorIndex(_ expr: String) -> String.Index? {
        var found: String.Index?
        var idx = expr.startIndex
        var offset = 0
        while idx < expr.endIndex {
            if offset > 0 && isOperator(expr[idx]) {
                found = idx
            }
            idx = expr.index(after: idx)
            offset += 1
        }
        return found
    }

    public static func appendDigit(_ expr: String, digit: String) -> String {
        if expr == "0" && digit != "00" { return digit }
        if expr == "0" && digit == "00" { return "0" }
        let currentSeg: String
        if let opIdx = lastOperatorIndex(expr) {
            currentSeg = String(expr[expr.index(after: opIdx)...])
        } else {
            currentSeg = expr
        }
        if currentSeg == "0" {
            if digit == "00" || digit == "0" { return expr }
            return String(expr.dropLast()) + digit
        }
        if currentSeg.isEmpty && digit == "00" {
            return expr + "0"
        }
        return expr + digit
    }

    public static func appendDot(_ expr: String) -> String {
        let currentSeg: String
        if let opIdx = lastOperatorIndex(expr) {
            currentSeg = String(expr[expr.index(after: opIdx)...])
        } else {
            currentSeg = expr
        }
        if currentSeg.contains(".") { return expr }
        return currentSeg.isEmpty ? (expr + "0.") : (expr + ".")
    }

    public static func appendOperator(_ expr: String, op: Character) -> String {
        guard let last = expr.last else { return "" }
        if isOperator(last) || last == "." {
            return String(expr.dropLast()) + String(op)
        }
        return expr + String(op)
    }

    public static func evaluate(_ expr: String) -> Decimal? {
        var cleaned = expr.trimmingCharacters(in: .whitespacesAndNewlines)
        while let last = cleaned.last, isOperator(last) || last == "." {
            cleaned.removeLast()
        }
        guard !cleaned.isEmpty else { return nil }

        var tokens: [String] = []
        var buf = ""
        for (idx, ch) in cleaned.enumerated() {
            if isOperator(ch) && idx > 0 && !buf.isEmpty {
                tokens.append(buf)
                buf = ""
                tokens.append(String(ch))
            } else {
                buf.append(ch)
            }
        }
        if !buf.isEmpty { tokens.append(buf) }
        guard !tokens.isEmpty,
              var acc = Decimal(string: tokens[0], locale: posixLocale) else {
            return nil
        }

        // 第一遍：计算 × / ÷
        var reduced: [String] = []
        var i = 1
        while i + 1 < tokens.count {
            let op = tokens[i]
            guard let rhs = Decimal(string: tokens[i + 1], locale: posixLocale) else { return nil }
            if op == "×" || op == "*" {
                acc = acc * rhs
            } else if op == "÷" || op == "/" {
                if rhs == 0 { return nil }
                acc = acc / rhs
            } else {
                reduced.append(NSDecimalNumber(decimal: acc).stringValue)
                reduced.append(op)
                acc = rhs
            }
            i += 2
        }
        reduced.append(NSDecimalNumber(decimal: acc).stringValue)

        guard var result = Decimal(string: reduced[0], locale: posixLocale) else { return nil }
        var j = 1
        while j + 1 < reduced.count {
            let op = reduced[j]
            guard let rhs = Decimal(string: reduced[j + 1], locale: posixLocale) else { return nil }
            if op == "+" {
                result = result + rhs
            } else if op == "-" {
                result = result - rhs
            } else {
                return nil
            }
            j += 2
        }
        return result
    }

    public static func evaluateAndFormat(_ expr: String, maxScale: Int = 4) -> String? {
        guard let dec = evaluate(expr) else { return nil }
        var input = dec
        var rounded = Decimal()
        let scale = max(2, min(4, maxScale))
        NSDecimalRound(&rounded, &input, scale, .plain)
        let formatter = NumberFormatter()
        formatter.locale = posixLocale
        formatter.numberStyle = .decimal
        formatter.usesGroupingSeparator = false
        formatter.minimumFractionDigits = 0
        formatter.maximumFractionDigits = scale
        return formatter.string(from: NSDecimalNumber(decimal: rounded))
    }

    public static func evaluateToMicros(_ expr: String) -> Int64? {
        guard let dec = evaluate(expr), dec > 0 else { return nil }
        var scaled = dec * Decimal(MoneyAmount.amountScale)
        var rounded = Decimal()
        NSDecimalRound(&rounded, &scaled, 0, .plain)
        guard !rounded.isNaN,
              rounded >= Decimal(Int64.min),
              rounded <= Decimal(Int64.max) else {
            return nil
        }
        let val = NSDecimalNumber(decimal: rounded).int64Value
        return val > 0 ? val : nil
    }
}

// MARK: - iOS 记账智能推荐、搜索与标签分组排序引擎（与 Android RecordUsageRanker 对齐）

public struct IOSCategorySearchResult: Identifiable {
    public var id: Int64 { category.id }
    public let category: CategoryDto
    public let parent: CategoryDto?
}

public struct IOSTagDisplayItem: Identifiable, Hashable {
    public var id: String { name }
    public let name: String
    public let groupName: String
    public let colorHex: Int64
    public let icon: String
    public let usageCount: Int
}

public struct IOSTagGroupSection: Identifiable {
    public var id: String { groupName }
    public let groupName: String
    public let tags: [IOSTagDisplayItem]
}

public struct IOSTagBrowserData {
    public let groups: [String]
    public let frequentTags: [IOSTagDisplayItem]
    public let sections: [IOSTagGroupSection]
    public let flatFiltered: [IOSTagDisplayItem]
    public let canQuickCreate: Bool
}

public enum IOSRecordUsageRanker {
    public static let tagTabAll = "全部"
    public static let tagTabFrequent = "常用"
    public static let canonicalTagGroups: [String] = [
        "渠道平台", "场景行程", "消费属性", "对象账务", "自定义"
    ]

    public static let selectableColorPalette: [Int64] = [
        0xFF2AA876, 0xFF006874, 0xFF07C160, 0xFF16A34A,
        0xFF0D9488, 0xFF0284C7, 0xFF1677FF, 0xFF4F46E5,
        0xFF7C3AED, 0xFFDB2777, 0xFFE11D48, 0xFFDC2626,
        0xFFEA580C, 0xFFD97706, 0xFF475569
    ]

    public static let quickCategoryIconKeys: [String] = [
        "restaurant", "delivery_dining", "local_cafe", "shopping_bag",
        "directions_car", "subway", "home", "sports_esports",
        "subscriptions", "medical_services", "flight", "school",
        "card_giftcard", "pets", "work", "trending_up"
    ]

    private static let dayMillis: Int64 = 86_400_000

    public static func rankFrequentCategories(
        type: String,
        categories: [CategoryDto],
        transactions: [TransactionDto],
        nowMillis: Int64 = currentEpochMillis(),
        limit: Int = 8
    ) -> [CategoryDto] {
        guard limit > 0 else { return [] }
        let typed = categories
            .filter { $0.type.caseInsensitiveCompare(type) == .orderedSame }
            .sorted { ($0.sortOrder, $0.id) < ($1.sortOrder, $1.id) }
        guard !typed.isEmpty else { return [] }

        let byId = Dictionary(uniqueKeysWithValues: typed.map { ($0.id, $0) })
        var scores: [Int64: Double] = [:]
        var latestTs: [Int64: Int64] = [:]

        for tx in transactions where tx.type.caseInsensitiveCompare(type) == .orderedSame {
            guard let cid = tx.categoryId, byId[cid] != nil else { continue }
            let ageDays = max(0, nowMillis - tx.timestamp) / dayMillis
            let weight: Double = ageDays <= 7 ? 4.0 : (ageDays <= 30 ? 2.5 : (ageDays <= 90 ? 1.5 : 0.5))
            scores[cid, default: 0.0] += weight
            if tx.timestamp > (latestTs[cid] ?? Int64.min) {
                latestTs[cid] = tx.timestamp
            }
        }

        var ranked = scores.keys.compactMap { byId[$0] }.sorted { lhs, rhs in
            let ls = scores[lhs.id] ?? 0
            let rs = scores[rhs.id] ?? 0
            if ls != rs { return ls > rs }
            let lt = latestTs[lhs.id] ?? 0
            let rt = latestTs[rhs.id] ?? 0
            if lt != rt { return lt > rt }
            return (lhs.sortOrder, lhs.id) < (rhs.sortOrder, rhs.id)
        }
        if ranked.count > limit {
            ranked = Array(ranked.prefix(limit))
        }

        let minTarget = min(limit, typed.count)
        if ranked.count < 5 && ranked.count < minTarget {
            var seen = Set(ranked.map(\.id))
            for p in typed where p.parentId == nil {
                if ranked.count >= minTarget { break }
                if seen.insert(p.id).inserted { ranked.append(p) }
            }
            if ranked.count < minTarget {
                for cat in typed {
                    if ranked.count >= minTarget { break }
                    if seen.insert(cat.id).inserted { ranked.append(cat) }
                }
            }
        }
        return ranked
    }

    public static func searchCategories(
        query: String,
        type: String,
        categories: [CategoryDto]
    ) -> [IOSCategorySearchResult] {
        let needle = query.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        guard !needle.isEmpty else { return [] }
        let typed = categories
            .filter { $0.type.caseInsensitiveCompare(type) == .orderedSame }
            .sorted { ($0.sortOrder, $0.id) < ($1.sortOrder, $1.id) }
        let byId = Dictionary(uniqueKeysWithValues: typed.map { ($0.id, $0) })

        var matches: [(score: Int, result: IOSCategorySearchResult)] = []
        for cat in typed {
            let parent = cat.parentId.flatMap { byId[$0] }
            let nameLower = cat.name.lowercased()
            let parentLower = parent?.name.lowercased() ?? ""
            let score: Int
            if nameLower == needle { score = 0 }
            else if nameLower.hasPrefix(needle) { score = 1 }
            else if nameLower.contains(needle) { score = 2 }
            else if parentLower.hasPrefix(needle) { score = 3 }
            else if parentLower.contains(needle) { score = 4 }
            else { continue }
            matches.append((score, IOSCategorySearchResult(category: cat, parent: parent)))
        }
        return matches.sorted { lhs, rhs in
            if lhs.score != rhs.score { return lhs.score < rhs.score }
            let lIsChild = lhs.result.parent == nil ? 0 : 1
            let rIsChild = rhs.result.parent == nil ? 0 : 1
            if lIsChild != rIsChild { return lIsChild < rIsChild }
            return (lhs.result.category.sortOrder, lhs.result.category.id) < (rhs.result.category.sortOrder, rhs.result.category.id)
        }.map(\.result)
    }

    public static func hasExactCategoryName(
        name: String,
        type: String,
        categories: [CategoryDto]
    ) -> Bool {
        let trimmed = name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return false }
        return categories.contains {
            $0.type.caseInsensitiveCompare(type) == .orderedSame &&
                $0.name.trimmingCharacters(in: .whitespacesAndNewlines).caseInsensitiveCompare(trimmed) == .orderedSame
        }
    }

    public static func buildTagBrowserData(
        query: String,
        selectedGroup: String?,
        availableTags: [TagDto],
        selectedTagNames: [String],
        allTransactions: [TransactionDto],
        nowMillis: Int64 = currentEpochMillis(),
        frequentLimit: Int = 8
    ) -> IOSTagBrowserData {
        let sortedEntities = availableTags.sorted { ($0.sortOrder, $0.id) < ($1.sortOrder, $1.id) }
        var entityByName: [String: TagDto] = [:]
        for tag in sortedEntities {
            let clean = tag.name.trimmingCharacters(in: .whitespacesAndNewlines)
            if !clean.isEmpty, entityByName[clean] == nil {
                entityByName[clean] = tag
            }
        }

        var usageCounts: [String: Int] = [:]
        var decayedScores: [String: Double] = [:]
        var historicalOrder: [String] = []
        var historicalSeen = Set<String>()

        for tx in allTransactions where !tx.tags.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            let ageDays = max(0, nowMillis - tx.timestamp) / dayMillis
            let weight: Double = ageDays <= 7 ? 4.0 : (ageDays <= 30 ? 2.5 : (ageDays <= 90 ? 1.5 : 0.5))
            for tagName in TagTextCodec.parse(tx.tags) {
                if historicalSeen.insert(tagName).inserted {
                    historicalOrder.append(tagName)
                }
                usageCounts[tagName, default: 0] += 1
                decayedScores[tagName, default: 0.0] += weight
            }
        }

        var orderedNames: [String] = []
        var seenNames = Set<String>()
        for tag in sortedEntities {
            let clean = tag.name.trimmingCharacters(in: .whitespacesAndNewlines)
            if !clean.isEmpty, seenNames.insert(clean).inserted { orderedNames.append(clean) }
        }
        for sel in selectedTagNames {
            let clean = sel.trimmingCharacters(in: .whitespacesAndNewlines)
            if !clean.isEmpty, seenNames.insert(clean).inserted { orderedNames.append(clean) }
        }
        for hist in historicalOrder where seenNames.insert(hist).inserted {
            orderedNames.append(hist)
        }

        let allItems: [IOSTagDisplayItem] = orderedNames.map { name in
            let entity = entityByName[name]
            let grp = entity?.groupName.trimmingCharacters(in: .whitespacesAndNewlines)
            let resolvedGroup = (grp?.isEmpty == false) ? grp! : "自定义"
            return IOSTagDisplayItem(
                name: name,
                groupName: resolvedGroup,
                colorHex: entity?.colorHex ?? stableFallbackColorHex(name),
                icon: entity?.icon ?? "",
                usageCount: usageCounts[name] ?? 0
            )
        }

        let frequentTags = Array(
            allItems
                .filter { (decayedScores[$0.name] ?? 0) > 0 || $0.usageCount > 0 }
                .sorted { lhs, rhs in
                    let ls = decayedScores[lhs.name] ?? 0
                    let rs = decayedScores[rhs.name] ?? 0
                    if ls != rs { return ls > rs }
                    if lhs.usageCount != rhs.usageCount { return lhs.usageCount > rhs.usageCount }
                    return lhs.name < rhs.name
                }
                .prefix(frequentLimit)
        )

        let byGroup = Dictionary(grouping: allItems, by: \.groupName)
        var orderedGroups: [String] = []
        for g in canonicalTagGroups where !(byGroup[g]?.isEmpty ?? true) {
            orderedGroups.append(g)
        }
        for g in byGroup.keys.sorted() where !canonicalTagGroups.contains(g) && !(byGroup[g]?.isEmpty ?? true) {
            orderedGroups.append(g)
        }

        var tabs = [tagTabAll]
        if !frequentTags.isEmpty { tabs.append(tagTabFrequent) }
        tabs.append(contentsOf: orderedGroups)

        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        let needle = trimmed.lowercased()
        let canQuickCreate = !trimmed.isEmpty && !allItems.contains { $0.name.caseInsensitiveCompare(trimmed) == .orderedSame }

        let activeGroup = (selectedGroup != nil && tabs.contains(selectedGroup!)) ? selectedGroup! : tagTabAll
        let scope: [IOSTagDisplayItem]
        if activeGroup == tagTabAll {
            scope = allItems
        } else if activeGroup == tagTabFrequent {
            scope = frequentTags
        } else {
            scope = byGroup[activeGroup] ?? []
        }

        let filtered: [IOSTagDisplayItem]
        if needle.isEmpty {
            filtered = scope
        } else {
            filtered = scope.filter {
                $0.name.lowercased().contains(needle) || $0.groupName.lowercased().contains(needle)
            }.sorted { lhs, rhs in
                let l = lhs.name.lowercased()
                let r = rhs.name.lowercased()
                let ls = l == needle ? 0 : (l.hasPrefix(needle) ? 1 : (l.contains(needle) ? 2 : 3))
                let rs = r == needle ? 0 : (r.hasPrefix(needle) ? 1 : (r.contains(needle) ? 2 : 3))
                if ls != rs { return ls < rs }
                return lhs.usageCount > rhs.usageCount
            }
        }

        let sections: [IOSTagGroupSection]
        if activeGroup == tagTabFrequent {
            sections = filtered.isEmpty ? [] : [IOSTagGroupSection(groupName: tagTabFrequent, tags: filtered)]
        } else if activeGroup != tagTabAll {
            sections = filtered.isEmpty ? [] : [IOSTagGroupSection(groupName: activeGroup, tags: filtered)]
        } else {
            let grouped = Dictionary(grouping: filtered, by: \.groupName)
            sections = orderedGroups.compactMap { g in
                guard let list = grouped[g], !list.isEmpty else { return nil }
                return IOSTagGroupSection(groupName: g, tags: list)
            }
        }

        return IOSTagBrowserData(
            groups: tabs,
            frequentTags: frequentTags,
            sections: sections,
            flatFiltered: filtered,
            canQuickCreate: canQuickCreate
        )
    }

    /// 按语义优先级返回分类的候选图标键列表（去重且不含 `"category"`），用于同级防撞图。
    public static func suggestCategoryIconCandidates(name: String) -> [String] {
        let text = name.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        guard !text.isEmpty else { return [] }
        var seen: Set<String> = []
        var result: [String] = []
        for (kws, icon, _) in categoryStyleRules where icon != "category" && kws.contains(where: { text.contains($0) }) {
            if seen.insert(icon).inserted {
                result.append(icon)
            }
        }
        return result
    }

    public static func suggestCategoryStyle(name: String, parent: CategoryDto? = nil) -> (icon: String, colorHex: Int64) {
        let text = name.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        if !text.isEmpty {
            for (kws, icon, color) in categoryStyleRules where kws.contains(where: { text.contains($0) }) {
                return (icon, parent?.colorHex ?? color)
            }
        }
        if let parent {
            return (parent.icon, parent.colorHex)
        }
        return ("category", text.isEmpty ? 0xFF2AA876 : stableFallbackColorHex(text))
    }

    /// 根据标签名称智能推荐专属标签图标与主题色（不与分类撞图）。
    public static func suggestTagStyle(name: String) -> (icon: String, colorHex: Int64) {
        let text = name.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        if !text.isEmpty {
            for (kws, icon, color) in tagStyleRules where kws.contains(where: { text.contains($0) }) {
                return (icon, color)
            }
        }
        let icon = text.isEmpty
            ? tagFallbackIcons[0]
            : tagFallbackIcons[abs(stableHash(text)) % tagFallbackIcons.count]
        let color = text.isEmpty ? 0xFF2AA876 : stableFallbackColorHex(text)
        return (icon, color)
    }

    private static let tagFallbackIcons: [String] = [
        "tag_essential", "tag_discount", "tag_commute", "tag_travel", "tag_self",
        "tag_offline", "sell", "bookmark", "star", "flag"
    ]

    private static let tagStyleRules: [([String], String, Int64)] = [
        (["拼多多", "拼单", "百亿补贴"], "tag_pdd", 0xFFE11D48),
        (["淘宝", "天猫", "闲鱼"], "tag_taobao", 0xFFD97706),
        (["京东", "京喜"], "tag_jd", 0xFFDC2626),
        (["美团", "饿了么", "外卖", "闪购"], "tag_meituan", 0xFFF59E0B),
        (["线下", "门店", "实体", "堂食"], "tag_offline", 0xFF0D9488),
        (["跨境", "海淘", "免税", "进口", "关税"], "tag_crossborder", 0xFF4F46E5),
        (["通勤", "地铁", "公交", "早晚高峰"], "tag_commute", 0xFF0369A1),
        (["差旅", "出差", "商旅", "报销单"], "tag_businesstrip", 0xFF2563EB),
        (["旅行", "旅游", "度假", "游玩", "打卡"], "tag_travel", 0xFF0F766E),
        (["囤货", "大促", "双11", "双十一", "618"], "tag_stockup", 0xFF9333EA),
        (["刚需", "日常", "必需", "固定"], "tag_essential", 0xFF16A34A),
        (["分期", "白条", "花呗", "月供", "免息"], "tag_installment", 0xFF64748B),
        (["立减", "优惠", "满减", "神券", "折扣", "返现"], "tag_discount", 0xFF15803D),
        (["冲动", "种草", "剁手", "氪金"], "tag_impulse", 0xFFE11D48),
        (["大额", "贵重", "重磅"], "tag_large", 0xFFDC2626),
        (["自己", "个人", "私用"], "tag_self", 0xFF2AA876),
        (["家人", "父母", "家里", "家庭", "父母家"], "tag_family", 0xFFEA580C),
        (["伴侣", "对象", "恋爱", "纪念日", "约会"], "tag_partner", 0xFFEC4899),
        (["aa", "均摊", "平摊", "分摊"], "tag_aa", 0xFF0284C7),
        (["抵税", "退税", "发票", "专项扣除"], "tag_taxdeduct", 0xFF4F46E5),
        (["装修", "家装", "硬装", "软装", "改造"], "tag_renovation", 0xFF0D9488),
        (["报销", "公费"], "receipt_long", 0xFF0284C7),
        (["订阅", "会员", "续费"], "subscriptions", 0xFF7C3AED),
        (["攒钱", "储蓄"], "savings", 0xFF16A34A),
        (["退款", "售后"], "replay", 0xFF0EA5E9),
        (["聚餐", "聚会", "团建", "宴请"], "celebration", 0xFFEC4899),
        (["网购", "电商"], "shopping_cart", 0xFFDB2777),
        (["加班", "工作"], "schedule", 0xFF475569),
        (["宠物", "猫", "狗"], "pets", 0xFFD97706),
        (["孩子", "宝宝", "娃"], "child_care", 0xFFF59E0B),
        (["心愿", "清单", "礼物"], "auto_awesome", 0xFF9333EA)
    ]

    private static let categoryStyleRules: [([String], String, Int64)] = [
        (["宠物医疗", "宠物医院", "驱虫", "绝育"], "healing", 0xFFD97706),
        (["猫粮", "狗粮", "宠物食品", "宠物零食"], "pet_food", 0xFFD97706),
        (["猫砂", "宠物用品", "狗绳", "猫爬架", "宠物玩具"], "pet_supplies", 0xFFD97706),
        (["母婴玩具", "母婴", "婴儿玩具", "摇铃"], "baby_rattle", 0xFFF59E0B),
        (["早教", "托育", "幼儿园"], "early_edu", 0xFFF59E0B),
        (["奶粉", "辅食", "尿不湿", "婴儿"], "child_care", 0xFFF59E0B),
        (["童装", "玩具", "绘本", "积木"], "toys", 0xFFF59E0B),
        (["亲子", "育儿", "孩子", "宝宝"], "stroller", 0xFFF59E0B),
        (["购物退款", "退款", "退货"], "refund_parcel", 0xFF0284C7),
        (["公司报销", "报销"], "receipt_long", 0xFF0284C7),
        (["保险理赔", "理赔", "赔付"], "request_quote", 0xFF0284C7),
        (["商业保险", "重疾", "意外险", "寿险", "车险"], "verified_user", 0xFF64748B),
        (["医疗保险", "医保", "社保"], "health_and_safety", 0xFFDC2626),
        (["餐补", "交通补", "房补", "消费补贴", "补贴"], "commute_allowance", 0xFF15803D),
        (["自媒体稿费", "稿费", "自媒体", "创作"], "mic", 0xFF2563EB),
        (["服务器", "主机", "域名", "vps", "机房", "aliyun", "aws", "cloudflare"], "server_rack", 0xFF4F46E5),
        (["虚拟充值", "话费充值", "点卡", "q币", "会员充值"], "virtual_recharge", 0xFF7C3AED),
        (["生活电器", "小家电", "电器", "冰箱", "洗衣机", "空调", "电饭煲"], "appliance_washer", 0xFF4F46E5),
        (["手机数码", "手机", "iphone", "安卓"], "smartphone_dev", 0xFF4F46E5),
        (["办公用品", "办公耗材", "打印", "复印"], "office_folder", 0xFF1677FF),
        (["文具", "本子", "笔", "尺"], "stationery", 0xFF1677FF),
        (["课程", "培训", "网课", "辅导"], "menu_course", 0xFF1677FF),
        (["服饰运动", "运动鞋", "球鞋", "运动服", "跑鞋"], "sport_shoe", 0xFFE11D48),
        (["配饰腕表", "腕表", "手表", "机械表", "石英表"], "wrist_watch", 0xFFE11D48),
        (["个护", "洗护", "日化", "洗发", "沐浴", "牙膏", "卫生巾"], "personal_care", 0xFFE11D48),
        (["日常家居", "家具", "灯具", "软装", "床品"], "household_lamp", 0xFFDB2777),
        (["物业费", "管理费", "小区费"], "property_fee", 0xFF0D9488),
        (["房租还贷", "还贷", "月供"], "house_contract", 0xFF0D9488),
        (["房租", "房贷", "租房", "按揭"], "cottage", 0xFF0D9488),
        (["电费", "供电"], "electric_plug", 0xFF0D9488),
        (["水费", "自来水", "桶装水"], "water_drop", 0xFF0D9488),
        (["燃气", "煤气", "天然气", "暖气", "取暖"], "propane_tank", 0xFF0D9488),
        (["水电"], "bolt", 0xFF0D9488),
        (["物业", "维修", "水电工", "开锁"], "handyman", 0xFF0D9488),
        (["话费", "宽带", "流量", "网费"], "wifi", 0xFF0D9488),
        (["家政", "保洁", "钟点工", "干洗"], "cleaning_services", 0xFF0D9488),
        (["居家", "住房", "房子"], "home", 0xFF0D9488),
        (["咖啡", "奶茶", "拿铁", "瑞幸", "星巴克", "coffee", "cafe"], "local_cafe", 0xFFEA580C),
        (["烟", "酒", "茶叶", "茶馆", "普洱", "茅台"], "emoji_food_beverage", 0xFFEA580C),
        (["外卖", "点餐", "美团", "饿了么"], "delivery_dining", 0xFFEA580C),
        (["聚餐", "请客", "酒水", "酒吧", "鸡尾酒", "清吧"], "local_bar", 0xFFEA580C),
        (["做饭", "烹饪", "厨房", "下厨", "调料"], "kitchen", 0xFF16A34A),
        (["水果", "买菜", "生鲜", "菜市场", "蔬菜"], "local_grocery_store", 0xFF16A34A),
        (["早餐", "早饭", "早点", "包子", "豆浆"], "breakfast_egg", 0xFFEA580C),
        (["午餐", "午饭", "工作餐", "便当", "盒饭"], "lunch_bento", 0xFFEA580C),
        (["晚餐", "晚饭", "夜宵", "宵夜"], "dinner_bowl", 0xFFEA580C),
        (["甜品", "蛋糕", "面包", "烘焙", "冰淇淋", "奶茶甜点"], "bakery_dining", 0xFFEA580C),
        (["休闲零食", "零食", "薯片", "坚果", "糖果", "辣条", "饼干"], "snack_bag", 0xFFEA580C),
        (["三餐", "外食", "面", "粉", "快餐"], "ramen_dining", 0xFFEA580C),
        (["餐", "饭", "吃", "火锅", "烧烤", "食堂"], "restaurant", 0xFFEA580C),
        (["公共交通", "公交", "巴士", "大巴", "班车"], "directions_bus", 0xFF0284C7),
        (["地铁", "轻轨", "通勤"], "subway", 0xFF0284C7),
        (["打车", "滴滴", "出租", "网约车"], "local_taxi", 0xFF0284C7),
        (["单车", "骑行", "自行车", "电动车"], "pedal_bike", 0xFF0284C7),
        (["高铁", "火车", "动车", "铁路"], "train", 0xFF0284C7),
        (["加油", "充电", "油费", "油卡"], "local_gas_station", 0xFF0284C7),
        (["停车", "高速", "过路"], "local_parking", 0xFF0284C7),
        (["养车", "洗车", "保养", "修车"], "local_car_wash", 0xFF0284C7),
        (["汽车", "车", "出行", "交通"], "directions_car", 0xFF0284C7),
        (["网购", "百货", "淘宝", "京东", "拼多多"], "local_mall", 0xFFDB2777),
        (["超市", "便利", "山姆", "盒马"], "shopping_cart", 0xFFDB2777),
        (["家居", "日用", "收纳", "纸巾"], "weekend", 0xFFDB2777),
        (["快递", "邮寄", "顺丰", "邮费"], "local_shipping", 0xFFDB2777),
        (["购物", "消费", "买"], "shopping_bag", 0xFFDB2777),
        (["护肤", "美妆", "口红", "面膜", "化妆"], "face_retouching_natural", 0xFFE11D48),
        (["理发", "剪发", "美甲", "烫发", "造型"], "content_cut", 0xFFE11D48),
        (["首饰", "配饰", "黄金", "项链", "戒指", "耳环"], "diamond", 0xFFE11D48),
        (["服饰", "穿搭", "鞋", "衣", "包"], "checkroom", 0xFFE11D48),
        (["电脑", "笔记本", "平板", "ipad", "mac"], "laptop_mac", 0xFF4F46E5),
        (["耳机", "键盘", "鼠标", "配件", "充电器"], "headphones", 0xFF4F46E5),
        (["电视", "家电"], "tv", 0xFF4F46E5),
        (["数码", "电子"], "devices", 0xFF4F46E5),
        (["ai", "gpt", "claude", "gemini", "云盘", "icloud"], "smart_toy", 0xFF7C3AED),
        (["会员", "app", "软件"], "cloud_done", 0xFF7C3AED),
        (["音乐", "视频", "b站", "爱奇艺", "网易云", "spotify", "netflix"], "music_note", 0xFF7C3AED),
        (["游戏", "steam", "switch", "氪金"], "sports_esports", 0xFF7C3AED),
        (["订阅"], "subscriptions", 0xFF7C3AED),
        (["电影", "演出", "演唱会", "话剧", "脱口秀"], "movie", 0xFF9333EA),
        (["聚会", "桌游", "剧本杀", "ktv", "密室", "玩乐"], "celebration", 0xFF9333EA),
        (["健身", "运动", "瑜伽", "游泳", "球"], "fitness_center", 0xFF9333EA),
        (["露营", "徒步", "爬山", "户外"], "forest", 0xFF9333EA),
        (["娱乐", "休闲"], "theater_comedy", 0xFF9333EA),
        (["挂号", "看病", "医院", "门诊", "急诊"], "local_hospital", 0xFFDC2626),
        (["买药", "药店", "保健", "维生素"], "medication", 0xFFDC2626),
        (["体检", "牙科", "口腔", "洗牙"], "monitor_heart", 0xFFDC2626),
        (["医疗", "健康", "医", "药", "诊"], "medical_services", 0xFFDC2626),
        (["机票", "船票", "航空", "飞行"], "flight", 0xFF2563EB),
        (["酒店", "民宿", "住宿", "宾馆"], "hotel", 0xFF2563EB),
        (["门票", "景点", "景区", "乐园"], "confirmation_number", 0xFF2563EB),
        (["特产", "纪念", "伴手礼"], "backpack", 0xFF2563EB),
        (["旅行", "旅游", "出差", "差旅"], "explore", 0xFF2563EB),
        (["书", "阅读", "资料"], "book", 0xFF1677FF),
        (["考试", "考证", "报名费"], "edit_note", 0xFF1677FF),
        (["学习", "成长", "教育"], "school", 0xFF1677FF),
        (["红包", "份子", "压岁"], "card_giftcard", 0xFFEC4899),
        (["送礼", "礼物", "鲜花"], "redeem", 0xFFEC4899),
        (["长辈", "父母", "爸妈", "孝敬"], "elderly", 0xFFEC4899),
        (["约会", "恋爱", "纪念日"], "favorite", 0xFFEC4899),
        (["人情", "社交", "往来"], "groups", 0xFFEC4899),
        (["猫", "狗", "宠"], "pets", 0xFFD97706),
        (["手续费", "汇损", "兑换"], "currency_exchange", 0xFF64748B),
        (["保险"], "verified_user", 0xFF64748B),
        (["利息", "罚息", "分期费"], "percent", 0xFF64748B),
        (["亏损", "赔钱"], "trending_down", 0xFF64748B),
        (["捐赠", "公益", "捐款"], "volunteer_activism", 0xFF475569),
        (["丢失", "罚款", "遗失"], "priority_high", 0xFF475569),
        (["年终", "十三薪"], "emoji_events", 0xFF16A34A),
        (["加班", "津贴"], "schedule", 0xFF16A34A),
        (["工资", "底薪", "薪资"], "payments", 0xFF16A34A),
        (["绩效", "奖金"], "bar_chart", 0xFF15803D),
        (["理财", "基金", "股票", "分红", "收益"], "trending_up", 0xFF0D9488),
        (["兼职", "副业", "外包", "私活"], "business_center", 0xFF2563EB),
        (["二手", "闲置", "闲鱼", "转卖"], "sell", 0xFF2563EB),
        (["租金", "房租收入", "收租"], "corporate_fare", 0xFFEA580C),
        (["营收", "营业", "店"], "point_of_sale", 0xFFEA580C),
        (["公积金"], "badge", 0xFF006874),
        (["退税", "个税", "抵税"], "calculate", 0xFF006874)
    ]

    public static func stableFallbackColorHex(_ label: String) -> Int64 {
        Int64(selectableColorPalette[abs(stableHash(label)) % selectableColorPalette.count])
    }

    private static func stableHash(_ label: String) -> Int {
        var hash = 0
        for scalar in label.unicodeScalars {
            hash = hash &* 31 &+ Int(scalar.value)
        }
        return hash
    }
}

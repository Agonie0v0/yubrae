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

    public static func suggestCategoryStyle(name: String, parent: CategoryDto? = nil) -> (icon: String, colorHex: Int64) {
        let text = name.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        let rules: [([String], String, Int64)] = [
            (["咖啡", "奶茶", "茶饮", "拿铁", "瑞幸", "星巴克", "coffee", "cafe"], "local_cafe", 0xFFEA580C),
            (["外卖", "点餐", "美团", "饿了么"], "delivery_dining", 0xFFEA580C),
            (["餐", "饭", "吃", "火锅", "烧烤", "早餐", "午餐", "晚餐", "夜宵"], "restaurant", 0xFFEA580C),
            (["甜品", "蛋糕", "零食", "面包", "烘焙"], "bakery_dining", 0xFFEA580C),
            (["水果", "买菜", "生鲜"], "local_grocery_store", 0xFF16A34A),
            (["地铁", "公交", "巴士", "轻轨", "通勤"], "subway", 0xFF0284C7),
            (["打车", "滴滴", "出租", "网约车"], "local_taxi", 0xFF0284C7),
            (["加油", "充电", "汽车", "停车", "车"], "directions_car", 0xFF0284C7),
            (["高铁", "火车", "机票", "飞机", "酒店", "旅行", "旅游"], "flight", 0xFF2563EB),
            (["猫", "狗", "宠"], "pets", 0xFFD97706),
            (["药", "医", "挂号", "医院", "体检", "诊"], "medical_services", 0xFFDC2626),
            (["水电", "燃气", "房租", "房贷", "物业", "宽带", "话费"], "home", 0xFF0D9488),
            (["游戏", "电影", "娱乐", "健身", "运动"], "sports_esports", 0xFF9333EA),
            (["订阅", "会员", "云", "ai", "音乐", "视频"], "subscriptions", 0xFF7C3AED),
            (["书", "课", "学习", "培训", "文具"], "school", 0xFF1677FF),
            (["红包", "礼金", "送礼", "礼物", "请客"], "card_giftcard", 0xFFEC4899),
            (["衣", "鞋", "包", "美妆", "护肤", "理发"], "checkroom", 0xFFE11D48),
            (["数码", "手机", "电脑", "耳机", "家电"], "devices", 0xFF4F46E5),
            (["工资", "薪资", "奖金", "补贴", "兼职"], "work", 0xFF16A34A),
            (["理财", "基金", "股票", "利息", "分红"], "trending_up", 0xFF0D9488),
            (["报销", "退款", "理赔"], "replay", 0xFF0284C7)
        ]
        if !text.isEmpty {
            for (kws, icon, color) in rules where kws.contains(where: { text.contains($0) }) {
                return (icon, parent?.colorHex ?? color)
            }
        }
        if let parent {
            return (parent.icon, parent.colorHex)
        }
        return ("category", text.isEmpty ? 0xFF2AA876 : stableFallbackColorHex(text))
    }

    public static func stableFallbackColorHex(_ label: String) -> Int64 {
        var hash: Int = 0
        for scalar in label.unicodeScalars {
            hash = hash &* 31 &+ Int(scalar.value)
        }
        let idx = abs(hash) % selectableColorPalette.count
        return selectableColorPalette[idx]
    }
}

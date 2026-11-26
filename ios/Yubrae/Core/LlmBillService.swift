import Foundation

// MARK: - Local Offline Natural Language Bill Parser

/// 纯本地离线自然语言多笔智能记账解析器（无需任何 API Key 即可使用，对齐 Android `SmartInputParser.kt`）。
public enum SmartTextBillParser {

    public static func parseLocalBatch(
        text: String,
        accounts: [AccountDto],
        categories: [CategoryDto],
        aliases: [AccountAliasDto] = [],
        nowMillis: Int64 = currentEpochMillis(),
        timeZone: TimeZone = .current
    ) -> [ParsedBillDraft] {
        let cleanText = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleanText.isEmpty else { return [] }

        let activeAccounts = accounts.filter { !$0.archived }
        let usableAccounts = activeAccounts.isEmpty ? accounts : activeAccounts
        let segments = splitClauses(cleanText)
        guard !segments.isEmpty else { return [] }

        var results: [ParsedBillDraft] = []
        var inheritedDayOffset = 0
        var pendingPrefix = ""

        for rawSegment in segments {
            let segment = rawSegment.trimmingCharacters(in: .whitespacesAndNewlines)
            if segment.isEmpty { continue }

            let explicitOffset = detectDayOffset(segment)
            if let explicitOffset {
                inheritedDayOffset = explicitOffset
            }

            let normalized = normalizeColloquialChineseMoney(segment)
            let hasDigit = normalized.unicodeScalars.contains { CharacterSet.decimalDigits.contains($0) }
            if !hasDigit {
                if !isPureTimeOrDatePhrase(segment) {
                    pendingPrefix = pendingPrefix.isEmpty ? segment : "\(pendingPrefix) \(segment)"
                }
                continue
            }

            let combinedClause = pendingPrefix.isEmpty ? segment : "\(pendingPrefix) \(segment)"
            pendingPrefix = ""

            if let draft = parseSingleClause(
                clause: combinedClause,
                effectiveDayOffset: explicitOffset ?? inheritedDayOffset,
                accounts: usableAccounts,
                categories: categories,
                aliases: aliases,
                nowMillis: nowMillis,
                timeZone: timeZone
            ) {
                results.append(draft)
            }
        }

        return results
    }

    // MARK: - Single Clause Parsing

    private static func parseSingleClause(
        clause: String,
        effectiveDayOffset: Int,
        accounts: [AccountDto],
        categories: [CategoryDto],
        aliases: [AccountAliasDto],
        nowMillis: Int64,
        timeZone: TimeZone
    ) -> ParsedBillDraft? {
        let normalizedClause = normalizeColloquialChineseMoney(clause)
        let clauseTimestamp = resolveClauseTimestamp(
            clause: normalizedClause,
            dayOffset: effectiveDayOffset,
            nowMillis: nowMillis,
            timeZone: timeZone
        )

        let stripped = stripNonAmountTokens(normalizedClause, accounts: accounts)
        guard let amountMatch = extractAmountAndCurrency(strippedText: stripped, originalText: normalizedClause),
              amountMatch.amountMicros > 0 else {
            return nil
        }

        let txType = inferTransactionType(normalizedClause)
        var transferFromId: Int64?
        var transferToAccount: AccountDto?
        if txType == TransactionType.TRANSFER,
           let pair = extractTransferAccounts(normalizedClause, accounts: accounts, aliases: aliases, currency: amountMatch.currency) {
            transferFromId = pair.fromAccountId
            transferToAccount = pair.toAccount
        }

        let accountHint = extractCardLast4(normalizedClause)
        let resolvedAccountId = transferFromId ?? resolveAccountId(
            clause: normalizedClause,
            accountHint: accountHint,
            currency: amountMatch.currency,
            aliases: aliases,
            accounts: accounts
        )
        let effectiveHint = transferToAccount.map { "->\($0.name)" } ?? accountHint

        let merchant = extractCleanMerchant(
            clause: normalizedClause,
            matchedAmountToken: amountMatch.matchedToken,
            txType: txType,
            transferTargetName: transferToAccount?.name ?? ""
        )

        let resolvedCategory: CategoryDto?
        let resolvedCategoryName: String
        if txType == TransactionType.TRANSFER {
            resolvedCategory = nil
            resolvedCategoryName = ""
        } else {
            let defaultCat = txType == TransactionType.INCOME ? "其他收入" : "其他支出"
            let cat = resolveCategory(
                type: txType,
                merchant: merchant,
                fullText: normalizedClause,
                defaultName: defaultCat,
                categories: categories
            )
            resolvedCategory = cat
            resolvedCategoryName = cat?.name ?? defaultCat
        }

        let autoTags = inferAutoTags(
            text: "\(merchant) \(normalizedClause) \(resolvedCategoryName)",
            categoryName: resolvedCategoryName,
            currency: amountMatch.currency
        )

        return ParsedBillDraft(
            type: txType,
            origAmountMicros: amountMatch.amountMicros,
            origCurrency: amountMatch.currency,
            accountId: resolvedAccountId,
            toAccountId: transferToAccount?.id,
            accountHint: effectiveHint,
            categoryId: resolvedCategory?.id,
            categoryName: resolvedCategoryName,
            merchant: merchant,
            note: String(clause.trimmingCharacters(in: .whitespacesAndNewlines).prefix(80)),
            timestamp: clauseTimestamp,
            confidence: 0.86,
            parserUsed: "LOCAL_SMART_INPUT",
            tags: autoTags
        )
    }

    // MARK: - Regex Definitions

    private static let primarySplitRegex = try! NSRegularExpression(
        pattern: #"(?:[，；;、。\r\n]+|(?<!\d),(?!\d)|,(?![0-9]{3}(?:\.[0-9]+)?(?:\D|$)))"#
    )
    private static let connectorSplitRegex = try! NSRegularExpression(
        pattern: #"(?:然后|接着|随后|另外|还有|以及|并且|(?<=\s|[0-9元块美元欧镑币])又|又(?=买|花|付|吃|喝|收|转|充|退|订|打|看))"#
    )
    private static let arabicYuanJiaoRegex = try! NSRegularExpression(pattern: #"(\d+)块(\d)(\d)?(?:[毛角]钱?|钱)?(?![0-9.])"#)
    private static let arabicYuanOnlyRegex = try! NSRegularExpression(pattern: #"(\d+(?:\.\d+)?)块钱?(?![0-9.])"#)
    private static let cnDigits = "零〇一壹二两俩贰三叁四肆五伍六陆七柒八捌九玖"
    private static let cnNumBody = "[\(cnDigits)十拾百佰千仟万萬半]+(?:点[\(cnDigits)半]+)?"
    private static let cnFxRegex = try! NSRegularExpression(
        pattern: "(\(cnNumBody))(美元|美金|港币|港元|欧元|日元|英镑|韩元|新台币|台币|泰铢|加元|澳元|新币|瑞士法郎)"
    )
    private static let cnYuanFracRegex = try! NSRegularExpression(
        pattern: "(花了|花费|消费了?|付款|支付了?|一共|共|合计|收了|收到|入账|发了|转了|充了|充值了?|扣了|交了|给了)?(\(cnNumBody))(块|元|圆)(半|零[\(cnDigits)]分?|([\(cnDigits)])(?:[毛角](?:([\(cnDigits)])分?)?)?)(钱)?(?=$|[\\s,，.。!！?？;；、)）多左右整了呢吧啊])"
    )
    private static let cnYuanRegex = try! NSRegularExpression(
        pattern: "(花了|花费|消费了?|付款|支付了?|一共|共|合计|收了|收到|入账|发了|转了|充了|充值了?|扣了|交了|给了)?(\(cnNumBody))(块钱|块|元钱?|圆钱?)"
    )
    private static let cnBarelyKuaiAfterRegex = try! NSRegularExpression(
        pattern: "^(?:多|左右|整|以内|吧|啊|呢|了|的)?(?=$|[\\s,，.。!！?？;；、)）])"
    )
    private static let cnSmallRmbRegex = try! NSRegularExpression(
        pattern: "(?!九毛九)([\(cnDigits)])([毛角分])(?:([\(cnDigits)])分?)?钱?"
    )
    private static let verbAnchoredCnRegex = try! NSRegularExpression(
        pattern: "(花了|花费|消费了?|付款|支付了?|一共|共|合计|收了|收到|入账|发了|转了|充了|充值了?|扣了|交了|给了)(\(cnNumBody))"
    )
    private static let nonMoneyClassifierAfterRegex = try! NSRegularExpression(
        pattern: "^(?:[个只条件张杯瓶碗盘份盒包袋斤两克次遍趟天日周月年时分秒号楼层线路岁倍成折%点块元圆毛角分])"
    )
    private static let explicitDateRegex = try! NSRegularExpression(pattern: #"\d{4}[-/.年]\d{1,2}[-/.月]\d{1,2}日?|\d{1,2}[-/.月]\d{1,2}日"#)
    private static let explicitClockRegex = try! NSRegularExpression(pattern: #"(?<!\d)(\d{1,2}):(\d{2})(?::\d{2})?(?!\d)"#)
    private static let cardLast4Regex = try! NSRegularExpression(
        pattern: #"(?:尾号|末四位|卡号[*xX]*|卡[（(]|ending\s+in|card\s*[*xX]*|\([*]{2,})\s*(\d{4})\b"#,
        options: .caseInsensitive
    )
    private static let cardLast4StripRegex = try! NSRegularExpression(
        pattern: #"(?:(?:尾号|末四位|卡号[*xX]*)\s*\d{4}|[(（]\s*\d{4}\s*[)）]|(?:信用卡|储蓄卡|借记卡)\s*\d{4}(?!\s*(?:\.|元|块|人民币|美元|美金|欧元|英镑|港币|港元|日元|USD|EUR|GBP|HKD|JPY|CNY|RMB)))"#,
        options: .caseInsensitive
    )
    private static let hyphenatedBrandRegex = try! NSRegularExpression(pattern: #"\b(?:7-11|7-eleven)\b"#, options: .caseInsensitive)
    private static let currencyAmountRegex = try! NSRegularExpression(
        pattern: #"(US\$|HK\$|NT\$|S\$|A\$|C\$|JP¥|USD|EUR|GBP|HKD|JPY|SGD|AUD|CAD|CHF|TWD|KRW|CNY|RMB|人民币|美元|美金|欧元|英镑|港币|港元|日元|新元|新加坡元|澳元|加元|瑞郎|新台币|韩元|\$|€|£|¥|￥|₩)?\s*([0-9]+(?:,[0-9]{3})*(?:\.[0-9]{1,2})?)\s*(元|美元|美金|欧元|英镑|港币|港元|日元|新元|新加坡元|澳元|加元|瑞郎|新台币|韩元|USD|EUR|GBP|HKD|JPY|SGD|AUD|CAD|CHF|TWD|KRW|CNY|RMB)?"#,
        options: .caseInsensitive
    )
    private static let bareNumberRegex = try! NSRegularExpression(pattern: #"[0-9]+(?:,[0-9]{3})*(?:\.[0-9]{1,2})?"#)
    private static let transferFromToRegex = try! NSRegularExpression(pattern: #"从\s*([^\s,，。;；]+?)\s*转(?:到|入|给)\s*([^\s,，。;；0-9]+)"#)
    private static let transferPersonRegex = try! NSRegularExpression(pattern: #"(?:转账给|转给)\s*([^\s,，。;；0-9¥￥$€£]{1,20})"#)
    private static let relativeDateRegex = try! NSRegularExpression(pattern: #"(?:大前天|前天|前日|前晚|昨天|昨日|昨晚|昨夜|今天|今日|今晚|今早|刚刚|刚才)"#)
    private static let timePeriodRegex = try! NSRegularExpression(pattern: #"(?:凌晨|早上|早晨|清晨|上午|中午|下午|傍晚|晚上|夜里)"#)
    private static let paymentAccountNoiseRegex = try! NSRegularExpression(
        pattern: #"(?:用|通过|使用|从)?\s*(?:微信零钱|微信支付|微信|支付宝|花呗|余额宝|公交卡|交通卡|储蓄卡|借记卡|信用卡|全币种信用卡|美元账户|欧元账户|英镑账户|现金)\s*(?:付款|支付|扣款|消费|入账|收款)?"#
    )
    private static let leadingActionRegex = try! NSRegularExpression(pattern: #"^\s*(?:花了|花费|消费了|消费|支出|付款|支付|买了|买|收到|入账|发了)\s*"#)
    private static let trailingActionRegex = try! NSRegularExpression(pattern: #"\s*(?:花了|花费|消费了|消费|付款|支付|块钱|元|块|钱)\s*$"#)
    private static let whitespaceRunRegex = try! NSRegularExpression(pattern: #"\s+"#)

    private static let strongIncomePhrases = ["转账收到", "收到转账", "收到的转账", "收到红包", "转入我", "received transfer"]
    private static let incomeKeywords = [
        "发工资", "工资", "薪资", "收款", "奖金", "退款", "退回", "报销",
        "入账", "到账", "分红", "返现", "兼职", "稿费", "收入", "收到",
        "salary", "bonus", "refund", "reimburse", "income", "received"
    ]
    private static let strongTransferPhrases = ["转账给", "转给", "转到", "还信用卡", "还花呗", "还白条", "还款"]

    private static let expenseCategoryRules: [(keywords: [String], candidates: [String])] = [
        (["美团外卖", "饿了么", "外卖", "点餐", "uber eats", "doordash"], ["外卖", "餐饮美食"]),
        (["星巴克", "starbucks", "瑞幸", "luckin", "库迪", "喜茶", "奈雪", "霸王茶姬", "蜜雪冰城", "咖啡", "奶茶", "果茶"], ["咖啡茶饮", "餐饮美食"]),
        (["零食", "蛋糕", "面包", "烘焙", "甜品", "冰淇淋"], ["零食甜品", "餐饮美食"]),
        (["盒马", "叮咚买菜", "山姆", "水果", "海鲜", "生鲜"], ["水果生鲜", "餐饮美食"]),
        (["火锅", "海底捞", "烧烤", "烤肉", "酒吧", "聚餐"], ["聚餐请客", "餐饮美食"]),
        (["买菜", "菜市场", "蔬菜", "猪肉", "牛肉", "鸡蛋"], ["买菜做饭", "餐饮美食"]),
        (["早饭", "早餐", "午饭", "午餐", "晚饭", "晚餐", "夜宵", "吃饭", "肯德基", "kfc", "麦当劳", "拉面", "面馆", "食堂", "快餐", "便当", "餐饮", "饭店"], ["三餐外食", "餐饮美食"]),
        (["地铁", "公交", "畅通卡", "交通卡", "公交卡", "一卡通", "羊城通", "深圳通", "乘车码", "轻轨", "subway", "bus"], ["地铁公交", "交通出行"]),
        (["滴滴", "打车", "出租车", "网约车", "uber", "taxi", "高德打车"], ["网约打车", "交通出行"]),
        (["单车", "骑车", "哈啰", "青桔", "共享单车"], ["共享单车", "交通出行"]),
        (["12306", "高铁", "动车", "火车", "train"], ["高铁火车", "交通出行"]),
        (["加油", "中石化", "中石油", "充电桩", "油费"], ["加油充电", "交通出行"]),
        (["停车", "高速", "过路费", "etc"], ["停车过路", "交通出行"]),
        (["openai", "chatgpt", "claude", "gemini", "cursor", "icloud", "aws", "cloudflare", "阿里云", "腾讯云", "网盘"], ["AI与云服务", "数字订阅"]),
        (["spotify", "netflix", "youtube", "bilibili", "b站", "爱奇艺", "腾讯视频", "网易云", "qq音乐", "apple music"], ["影音音乐", "数字订阅"]),
        (["steam", "switch", "psn", "原神", "王者荣耀", "游戏充值"], ["游戏充值", "数字订阅"]),
        (["app store", "apple", "github", "notion", "会员", "订阅", "subscription"], ["软件会员", "数字订阅"]),
        (["机票", "航空", "航班", "flight"], ["机票船票", "差旅住宿"]),
        (["酒店", "宾馆", "民宿", "亚朵", "全季", "airbnb", "hotel"], ["酒店民宿", "差旅住宿"]),
        (["房租", "月租", "租房", "房贷"], ["房租房贷", "居家物业"]),
        (["水费", "电费", "燃气", "水电费", "国家电网"], ["水电燃气", "居家物业"]),
        (["话费", "宽带", "网费", "中国移动", "中国联通", "中国电信"], ["话费宽带", "居家物业"]),
        (["物业", "维修", "装修"], ["物业维修", "居家物业"]),
        (["药店", "药房", "买药", "药品", "感冒", "保健品"], ["买药保健", "医疗健康"]),
        (["医院", "门诊", "挂号", "看病", "体检", "牙科"], ["看病挂号", "医疗健康"]),
        (["买书", "书籍", "图书", "书店", "当当", "kindle"], ["书籍资料", "学习成长"]),
        (["课程", "网课", "培训", "学费", "考试", "考证"], ["课程培训", "学习成长"]),
        (["电影", "影院", "演唱会", "猫眼", "大麦"], ["电影演出", "休闲娱乐"]),
        (["健身", "瑜伽", "游泳", "羽毛球", "运动"], ["运动健身", "休闲娱乐"]),
        (["iphone", "ipad", "macbook", "手机", "电脑", "耳机", "airpods", "键盘", "鼠标", "数码"], ["手机电脑", "数码家电"]),
        (["优衣库", "uniqlo", "nike", "衣服", "外套", "裤子", "鞋", "服饰", "理发", "护肤", "化妆品"], ["服饰鞋包", "服饰美容"]),
        (["猫粮", "狗粮", "猫砂", "宠物"], ["宠物食品", "宠物生活"]),
        (["红包", "礼金", "份子钱", "送礼", "请客", "约会"], ["红包礼金", "人情社交"]),
        (["顺丰", "快递", "邮费"], ["快递邮寄", "日用购物"]),
        (["便利店", "7-11", "全家", "罗森", "沃尔玛", "永辉", "超市", "买水", "饮料"], ["超市便利", "日用购物"]),
        (["淘宝", "天猫", "京东", "拼多多", "amazon", "网购", "购物", "日用品", "纸巾"], ["网购百货", "日用购物"])
    ]

    private static let incomeCategoryRules: [(keywords: [String], candidates: [String])] = [
        (["退款", "退货", "退回", "refund"], ["购物退款", "报销退款"]),
        (["报销", "reimburse"], ["公司报销", "报销退款"]),
        (["年终奖", "十三薪"], ["年终奖", "工资薪金"]),
        (["工资", "薪资", "发薪", "salary"], ["工资薪金", "基本工资"]),
        (["奖金", "绩效", "补贴", "津贴", "bonus"], ["奖金补贴", "绩效奖金"]),
        (["利息", "分红", "基金", "股票", "理财", "收益"], ["投资理财", "基金股票"]),
        (["闲鱼", "二手", "兼职", "副业", "稿费", "外包"], ["兼职副业", "外包私活"]),
        (["红包", "礼金"], ["微信红包", "人情礼金"])
    ]

    // MARK: - Helper Functions

    private static func splitClauses(_ text: String) -> [String] {
        let primary = splitRegex(primarySplitRegex, text: text)
        var out: [String] = []
        for piece in primary {
            let subs = splitRegex(connectorSplitRegex, text: piece)
            for sub in subs {
                let t = sub.trimmingCharacters(in: .whitespacesAndNewlines)
                if !t.isEmpty { out.append(t) }
            }
        }
        return out
    }

    static func normalizeColloquialChineseMoney(_ text: String) -> String {
        var s = replaceRegexMatches(arabicYuanJiaoRegex, in: text) { match, ns in
            let yuan = captureGroup(match, at: 1, in: ns)
            let jiao = captureGroup(match, at: 2, in: ns)
            let fen = captureGroup(match, at: 3, in: ns)
            return "\(yuan).\(jiao)\(fen)元"
        }
        s = replaceRegexMatches(arabicYuanOnlyRegex, in: s) { match, ns in
            let yuan = captureGroup(match, at: 1, in: ns)
            return "\(yuan)元"
        }
        s = normalizeChineseNumeralsToMoney(s)
        return s
    }

    private static func normalizeChineseNumeralsToMoney(_ text: String) -> String {
        var s = text

        s = replaceRegexMatches(cnFxRegex, in: s) { match, ns in
            let full = ns.substring(with: match.range)
            if isProtectedNonMoneyToken(full) { return nil }
            let cnPart = captureGroup(match, at: 1, in: ns)
            let unit = captureGroup(match, at: 2, in: ns)
            guard let num = parseChineseNumber(cnPart) else { return nil }
            return "\(formatAmountNumber(num))\(unit)"
        }

        s = replaceRegexMatches(cnYuanFracRegex, in: s) { match, ns in
            let prefix = captureGroup(match, at: 1, in: ns)
            let cnPart = captureGroup(match, at: 2, in: ns)
            let unitWord = captureGroup(match, at: 3, in: ns)
            let fracToken = captureGroup(match, at: 4, in: ns)
            let jiaoStr = captureGroup(match, at: 5, in: ns)
            let fenStr = captureGroup(match, at: 6, in: ns)
            let suffix = captureGroup(match, at: 7, in: ns)
            if isProtectedNonMoneyToken(cnPart + unitWord) { return nil }
            guard let yuanNum = parseChineseNumber(cnPart) else { return nil }

            let total: Double
            if fracToken == "半" {
                total = yuanNum + 0.5
            } else if fracToken.hasPrefix("零"), fracToken.count >= 2, let lastChar = fracToken.last,
                      let fenDigit = parseChineseSingleDigit(String(lastChar)) {
                total = yuanNum + Double(fenDigit) / 100.0
            } else if let jiao = parseChineseSingleDigit(jiaoStr) {
                let fen = fenStr.isEmpty ? 0 : (parseChineseSingleDigit(fenStr) ?? 0)
                total = yuanNum + Double(jiao) / 10.0 + Double(fen) / 100.0
            } else {
                return nil
            }
            return "\(prefix)\(formatAmountNumber(total))元\(suffix)"
        }

        s = replaceRegexMatches(cnYuanRegex, in: s) { match, ns in
            let verb = captureGroup(match, at: 1, in: ns)
            let cnPart = captureGroup(match, at: 2, in: ns)
            let unitPart = captureGroup(match, at: 3, in: ns)
            if isProtectedNonMoneyToken(cnPart + unitPart) { return nil }
            if unitPart == "块" && verb.isEmpty {
                let afterLoc = match.range.location + match.range.length
                let after = afterLoc < ns.length ? ns.substring(from: afterLoc) : ""
                let afterRange = NSRange(location: 0, length: (after as NSString).length)
                let boundaryOk = after.isEmpty || cnBarelyKuaiAfterRegex.firstMatch(in: after, range: afterRange) != nil
                if !boundaryOk { return nil }
                if cnPart == "半" { return nil }
            }
            guard let num = parseChineseNumber(cnPart) else { return nil }
            return "\(verb)\(formatAmountNumber(num))元"
        }

        s = replaceRegexMatches(cnSmallRmbRegex, in: s) { match, ns in
            let full = ns.substring(with: match.range)
            if isProtectedNonMoneyToken(full) || full == "九毛九" { return nil }
            guard let mainDigit = parseChineseSingleDigit(captureGroup(match, at: 1, in: ns)) else { return nil }
            let unit = captureGroup(match, at: 2, in: ns)
            let subStr = captureGroup(match, at: 3, in: ns)
            let subDigit = subStr.isEmpty ? 0 : (parseChineseSingleDigit(subStr) ?? 0)
            let amount: Double
            switch unit {
            case "毛", "角":
                amount = Double(mainDigit) / 10.0 + Double(subDigit) / 100.0
            case "分":
                amount = Double(mainDigit) / 100.0
            default:
                return nil
            }
            return "\(formatAmountNumber(amount))元"
        }

        s = replaceRegexMatches(verbAnchoredCnRegex, in: s) { match, ns in
            let verb = captureGroup(match, at: 1, in: ns)
            let cnPart = captureGroup(match, at: 2, in: ns)
            let afterLoc = match.range.location + match.range.length
            let after = afterLoc < ns.length ? ns.substring(from: afterLoc) : ""
            if !after.isEmpty {
                let afterRange = NSRange(location: 0, length: (after as NSString).length)
                if nonMoneyClassifierAfterRegex.firstMatch(in: after, range: afterRange) != nil {
                    return nil
                }
            }
            if cnPart == "半" { return nil }
            guard let num = parseChineseNumber(cnPart) else { return nil }
            return "\(verb)\(formatAmountNumber(num))元"
        }

        return s
    }

    private static func captureGroup(_ match: NSTextCheckingResult, at index: Int, in ns: NSString) -> String {
        guard index < match.numberOfRanges else { return "" }
        let range = match.range(at: index)
        guard range.location != NSNotFound else { return "" }
        return ns.substring(with: range)
    }

    private static func replaceRegexMatches(
        _ regex: NSRegularExpression,
        in text: String,
        transform: (NSTextCheckingResult, NSString) -> String?
    ) -> String {
        let mutable = NSMutableString(string: text)
        let snapshot = mutable as NSString
        let matches = regex.matches(in: text, range: NSRange(location: 0, length: snapshot.length))
        for match in matches.reversed() {
            if let replacement = transform(match, snapshot) {
                mutable.replaceCharacters(in: match.range, with: replacement)
            }
        }
        return mutable as String
    }

    private static func isProtectedNonMoneyToken(_ token: String) -> Bool {
        token == "九毛九" || token.contains("星期") || token.contains("号") || token.contains("线")
    }

    private static func formatAmountNumber(_ value: Double) -> String {
        let rounded = (value * 100.0).rounded() / 100.0
        let intVal = Int64(rounded)
        if abs(rounded - Double(intVal)) < 1e-6 {
            return String(intVal)
        }
        var formatted = String(format: "%.2f", rounded)
        while formatted.hasSuffix("0") {
            formatted.removeLast()
        }
        if formatted.hasSuffix(".") {
            formatted.removeLast()
        }
        return formatted
    }

    private static func parseChineseSingleDigit(_ ch: String) -> Int? {
        switch ch {
        case "零", "〇": return 0
        case "一", "壹": return 1
        case "二", "两", "俩", "贰": return 2
        case "三", "叁": return 3
        case "四", "肆": return 4
        case "五", "伍": return 5
        case "六", "陆": return 6
        case "七", "柒": return 7
        case "八", "捌": return 8
        case "九", "玖": return 9
        default: return nil
        }
    }

    private static func parseChineseNumber(_ raw: String) -> Double? {
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty { return nil }
        if trimmed == "半" { return 0.5 }
        let parts = trimmed.split(separator: "点", omittingEmptySubsequences: false).map(String.init)
        if parts.count > 2 { return nil }
        guard let intVal = parseChineseInteger(parts[0]) else { return nil }
        if parts.count == 1 { return Double(intVal) }
        let fracStr = parts[1]
        if fracStr.isEmpty { return nil }
        if fracStr == "半" { return Double(intVal) + 0.5 }
        var frac = 0.0
        var scale = 0.1
        for c in fracStr {
            guard let d = parseChineseSingleDigit(String(c)) else { return nil }
            frac += Double(d) * scale
            scale *= 0.1
        }
        return Double(intVal) + frac
    }

    private static func parseChineseInteger(_ raw: String) -> Int64? {
        if raw.isEmpty { return nil }
        if raw == "零" || raw == "〇" { return 0 }
        var total: Int64 = 0
        var section: Int64 = 0
        var current: Int64 = -1
        var lastUnit: Int64 = 0
        var sawZero = false

        for c in raw {
            let s = String(c)
            if let d = parseChineseSingleDigit(s) {
                if d == 0 {
                    sawZero = true
                    current = -1
                } else {
                    if current > 0 { return nil }
                    current = Int64(d)
                }
                continue
            }
            switch c {
            case "十", "拾":
                let v = current <= 0 ? 1 : current
                section += v * 10
                current = -1
                lastUnit = 10
                sawZero = false
            case "百", "佰":
                let v = current <= 0 ? 1 : current
                section += v * 100
                current = -1
                lastUnit = 100
                sawZero = false
            case "千", "仟":
                let v = current <= 0 ? 1 : current
                section += v * 1000
                current = -1
                lastUnit = 1000
                sawZero = false
            case "万", "萬":
                if current > 0 { section += current }
                total += (section == 0 ? 1 : section) * 10000
                section = 0
                current = -1
                lastUnit = 10000
                sawZero = false
            default:
                return nil
            }
        }
        if current > 0 {
            if !sawZero && lastUnit >= 100 {
                section += current * (lastUnit / 10)
            } else {
                section += current
            }
        }
        return total + section
    }

    private static func detectDayOffset(_ clause: String) -> Int? {
        if clause.contains("大前天") { return -3 }
        if clause.contains("前天") || clause.contains("前日") || clause.contains("前晚") { return -2 }
        if clause.contains("昨天") || clause.contains("昨日") || clause.contains("昨晚") || clause.contains("昨夜") { return -1 }
        if clause.contains("今天") || clause.contains("今日") || clause.contains("今晚") ||
            clause.contains("今早") || clause.contains("刚刚") || clause.contains("刚才") { return 0 }
        return nil
    }

    private static func isPureTimeOrDatePhrase(_ segment: String) -> Bool {
        var cleaned = replaceRegex(relativeDateRegex, in: segment, withTemplate: "")
        cleaned = replaceRegex(timePeriodRegex, in: cleaned, withTemplate: "")
        return cleaned.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    private static func resolveClauseTimestamp(
        clause: String,
        dayOffset: Int,
        nowMillis: Int64,
        timeZone: TimeZone
    ) -> Int64 {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = timeZone
        let baseDate = Date(timeIntervalSince1970: Double(nowMillis) / 1000.0)
        var targetDate = cal.date(byAdding: .day, value: dayOffset, to: baseDate) ?? baseDate

        if let match = firstMatch(explicitClockRegex, in: clause),
           let hStr = groupString(match, at: 1, in: clause),
           let mStr = groupString(match, at: 2, in: clause),
           let hour = Int(hStr), let minute = Int(mStr),
           (0...23).contains(hour), (0...59).contains(minute) {
            if let set = cal.date(bySettingHour: hour, minute: minute, second: 0, of: targetDate) {
                return Int64((set.timeIntervalSince1970 * 1000.0).rounded())
            }
        }

        let period: (Int, Int)?
        if clause.contains("凌晨") { period = (2, 0) }
        else if clause.contains("早上") || clause.contains("早晨") || clause.contains("今早") || clause.contains("清晨") { period = (8, 30) }
        else if clause.contains("上午") { period = (10, 0) }
        else if clause.contains("中午") { period = (12, 30) }
        else if clause.contains("下午") { period = (15, 0) }
        else if clause.contains("傍晚") { period = (18, 0) }
        else if clause.contains("晚上") || clause.contains("昨晚") || clause.contains("今晚") || clause.contains("前晚") || clause.contains("昨夜") || clause.contains("夜里") { period = (20, 30) }
        else { period = nil }

        if let (h, m) = period,
           let set = cal.date(bySettingHour: h, minute: m, second: 0, of: targetDate) {
            targetDate = set
        }
        return Int64((targetDate.timeIntervalSince1970 * 1000.0).rounded())
    }

    private static func stripNonAmountTokens(_ text: String, accounts: [AccountDto]) -> String {
        var s = replaceRegex(explicitDateRegex, in: text, withTemplate: " ")
        s = replaceRegex(explicitClockRegex, in: s, withTemplate: " ")
        s = replaceRegex(cardLast4StripRegex, in: s, withTemplate: " ")
        s = replaceRegex(hyphenatedBrandRegex, in: s, withTemplate: " ")
        for acc in accounts where acc.name.count >= 2 && acc.name.unicodeScalars.contains(where: { CharacterSet.decimalDigits.contains($0) }) {
            s = s.replacingOccurrences(of: acc.name, with: " ", options: .caseInsensitive)
        }
        return s.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private static func extractAmountAndCurrency(
        strippedText: String,
        originalText: String
    ) -> (amountMicros: Int64, currency: String, matchedToken: String)? {
        // 1. 优先匹配显式带币种前缀或后缀的金额
        for match in allMatches(currencyAmountRegex, in: strippedText) {
            let prefixCur = groupString(match, at: 1, in: strippedText) ?? ""
            let numStr = groupString(match, at: 2, in: strippedText) ?? ""
            let suffixCur = groupString(match, at: 3, in: strippedText) ?? ""
            if !prefixCur.isEmpty || !suffixCur.isEmpty,
               let micros = MoneyAmount.parseToMicros(numStr),
               micros > 0 {
                let rawCur = !prefixCur.isEmpty ? prefixCur : suffixCur
                let currency = normalizeCurrencyToken(rawCur)
                let matchedToken = groupString(match, at: 0, in: strippedText) ?? numStr
                return (micros, currency, matchedToken)
            }
        }

        // 2. 兜底匹配裸数字（排除 1900...2100 四位年份）
        var bareCandidates: [(Int64, String)] = []
        for match in allMatches(bareNumberRegex, in: strippedText) {
            guard let rawNum = groupString(match, at: 0, in: strippedText) else { continue }
            let intPart = rawNum.split(separator: ".").first.map(String.init)?.replacingOccurrences(of: ",", with: "") ?? ""
            if !rawNum.contains("."), intPart.count == 4, let yr = Int(intPart), (1900...2100).contains(yr) {
                continue
            }
            if let micros = MoneyAmount.parseToMicros(rawNum), micros > 0 {
                bareCandidates.append((micros, rawNum))
            }
        }
        guard let picked = bareCandidates.last else { return nil }
        let currency = CurrencyCatalog.detectCurrency(from: originalText)
        return (picked.0, currency, picked.1)
    }

    private static func normalizeCurrencyToken(_ raw: String) -> String {
        let upper = raw.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        switch upper {
        case "CNY", "RMB", "人民币", "元", "¥", "￥": return "CNY"
        case "USD", "US$", "$", "美元", "美金": return "USD"
        case "EUR", "€", "欧元": return "EUR"
        case "GBP", "£", "英镑": return "GBP"
        case "HKD", "HK$", "港币", "港元": return "HKD"
        case "JPY", "JP¥", "日元": return "JPY"
        case "SGD", "S$", "新元", "新加坡元": return "SGD"
        case "AUD", "A$", "澳元": return "AUD"
        case "CAD", "C$", "加元": return "CAD"
        case "CHF", "瑞郎", "瑞士法郎": return "CHF"
        case "TWD", "NT$", "新台币": return "TWD"
        case "KRW", "₩", "韩元": return "KRW"
        default: return CurrencyCatalog.detectCurrency(from: raw)
        }
    }

    private static func inferTransactionType(_ text: String) -> String {
        let lower = text.lowercased()
        if strongIncomePhrases.contains(where: { lower.contains($0) }) {
            return TransactionType.INCOME
        }
        if firstMatch(transferFromToRegex, in: text) != nil ||
            strongTransferPhrases.contains(where: { lower.contains($0) }) {
            return TransactionType.TRANSFER
        }
        if incomeKeywords.contains(where: { lower.contains($0) }) {
            return TransactionType.INCOME
        }
        if lower.contains("转账") || lower.contains("transfer") {
            return TransactionType.TRANSFER
        }
        return TransactionType.EXPENSE
    }

    private static func extractTransferAccounts(
        _ clause: String,
        accounts: [AccountDto],
        aliases: [AccountAliasDto],
        currency: String
    ) -> (fromAccountId: Int64?, toAccount: AccountDto?)? {
        guard let match = firstMatch(transferFromToRegex, in: clause),
              let fromPart = groupString(match, at: 1, in: clause)?.trimmingCharacters(in: .whitespacesAndNewlines),
              let toPart = groupString(match, at: 2, in: clause)?.trimmingCharacters(in: .whitespacesAndNewlines) else {
            return nil
        }
        let fromId = resolveAccountId(
            clause: fromPart,
            accountHint: extractCardLast4(fromPart),
            currency: currency,
            aliases: aliases,
            accounts: accounts
        )
        let toAcc = findDirectAccountMatch(text: toPart, accounts: accounts)
            ?? accounts.first { $0.id != fromId && matchesCommonAccountKeyword(text: toPart, account: $0) }
        return (fromId, toAcc)
    }

    private static func extractCardLast4(_ text: String) -> String {
        guard let match = firstMatch(cardLast4Regex, in: text),
              let digits = groupString(match, at: 1, in: text) else {
            return ""
        }
        return digits
    }

    private static func resolveAccountId(
        clause: String,
        accountHint: String,
        currency: String,
        aliases: [AccountAliasDto],
        accounts: [AccountDto]
    ) -> Int64? {
        guard !accounts.isEmpty else { return nil }
        if let direct = findDirectAccountMatch(text: clause, accounts: accounts) {
            return direct.id
        }
        if !accountHint.isEmpty, let byHint = accounts.first(where: { $0.name.contains(accountHint) }) {
            return byHint.id
        }
        if let common = accounts.first(where: { matchesCommonAccountKeyword(text: clause, account: $0) }) {
            return common.id
        }
        let activeIds = Set(accounts.map(\.id))
        let sortedAliases = aliases
            .filter { $0.enabled && activeIds.contains($0.accountId) }
            .sorted { lhs, rhs in
                if lhs.priority != rhs.priority { return lhs.priority > rhs.priority }
                return lhs.id < rhs.id
            }
        for alias in sortedAliases {
            let pattern = alias.pattern.trimmingCharacters(in: .whitespacesAndNewlines)
            guard !pattern.isEmpty else { continue }
            switch alias.matchType.uppercased() {
            case AliasMatchType.CARD_LAST4:
                if !accountHint.isEmpty && accountHint.caseInsensitiveCompare(pattern) == .orderedSame {
                    return alias.accountId
                }
            case AliasMatchType.KEYWORD:
                if clause.localizedCaseInsensitiveContains(pattern) {
                    return alias.accountId
                }
            default:
                break
            }
        }
        let upperCur = currency.uppercased()
        let sameCur = accounts.filter { $0.currencyCode.uppercased() == upperCur }
        if !sameCur.isEmpty {
            if upperCur != CurrencyCatalog.defaultBaseCurrency {
                return (sameCur.first { $0.groupType == AccountGroup.FOREIGN } ?? sameCur.first)?.id
            }
            return (sameCur.first { $0.groupType == AccountGroup.WALLET || $0.groupType == AccountGroup.DEBIT } ?? sameCur.first)?.id
        }
        return accounts.first?.id
    }

    private static func findDirectAccountMatch(text: String, accounts: [AccountDto]) -> AccountDto? {
        let sorted = accounts.sorted { $0.name.count > $1.name.count }
        for acc in sorted where acc.name.count >= 2 {
            if text.localizedCaseInsensitiveContains(acc.name) {
                return acc
            }
        }
        return nil
    }

    private static func matchesCommonAccountKeyword(text: String, account: AccountDto) -> Bool {
        let lowerText = text.lowercased()
        let lowerName = account.name.lowercased()
        if lowerText.contains("微信") && lowerName.contains("微信") { return true }
        if (lowerText.contains("支付宝") || lowerText.contains("花呗") || lowerText.contains("余额宝")) && lowerName.contains("支付宝") { return true }
        if (lowerText.contains("公交卡") || lowerText.contains("交通卡")) && (lowerName.contains("公交") || lowerName.contains("交通卡")) { return true }
        if lowerText.contains("信用卡") && (account.groupType == AccountGroup.CREDIT || lowerName.contains("信用卡")) { return true }
        if (lowerText.contains("储蓄卡") || lowerText.contains("借记卡") || lowerText.contains("工资卡")) &&
            (account.groupType == AccountGroup.DEBIT || lowerName.contains("储蓄卡")) { return true }
        if lowerText.contains("现金") && lowerName.contains("现金") { return true }
        if (lowerText.contains("美元账户") || lowerText.contains("美金账户")) && account.currencyCode.uppercased() == "USD" { return true }
        if lowerText.contains("欧元账户") && account.currencyCode.uppercased() == "EUR" { return true }
        if lowerText.contains("英镑账户") && account.currencyCode.uppercased() == "GBP" { return true }
        return false
    }

    private static func resolveCategory(
        type: String,
        merchant: String,
        fullText: String,
        defaultName: String,
        categories: [CategoryDto]
    ) -> CategoryDto? {
        guard !categories.isEmpty else { return nil }
        let typed = categories.filter { $0.type.caseInsensitiveCompare(type) == .orderedSame }
        let pool = typed.isEmpty ? categories : typed
        let rules = type == TransactionType.INCOME ? incomeCategoryRules : expenseCategoryRules
        let haystack = "\(merchant) \(fullText)".lowercased()

        for rule in rules {
            if rule.keywords.contains(where: { haystack.contains($0) }) {
                if let matched = findBestCategoryMatch(candidates: rule.candidates, categories: pool) {
                    return matched
                }
            }
        }
        return findBestCategoryMatch(candidates: [defaultName], categories: pool) ?? pool.first
    }

    private static func findBestCategoryMatch(candidates: [String], categories: [CategoryDto]) -> CategoryDto? {
        for candidate in candidates {
            if let exact = categories.first(where: { $0.name.caseInsensitiveCompare(candidate) == .orderedSame }) {
                return exact
            }
        }
        for candidate in candidates {
            if let partial = categories.first(where: {
                $0.name.localizedCaseInsensitiveContains(candidate) || candidate.localizedCaseInsensitiveContains($0.name)
            }) {
                return partial
            }
        }
        return nil
    }

    private static func extractCleanMerchant(
        clause: String,
        matchedAmountToken: String,
        txType: String,
        transferTargetName: String
    ) -> String {
        if txType == TransactionType.TRANSFER {
            if let match = firstMatch(transferPersonRegex, in: clause),
               let person = groupString(match, at: 1, in: clause)?.trimmingCharacters(in: .whitespacesAndNewlines),
               !person.isEmpty {
                return String(person.prefix(32))
            }
            if !transferTargetName.isEmpty {
                return String("转入\(transferTargetName)".prefix(32))
            }
        }

        var s = clause
        if !matchedAmountToken.isEmpty {
            s = s.replacingOccurrences(of: matchedAmountToken, with: " ")
        }
        s = replaceRegex(explicitDateRegex, in: s, withTemplate: " ")
        s = replaceRegex(explicitClockRegex, in: s, withTemplate: " ")
        s = replaceRegex(relativeDateRegex, in: s, withTemplate: " ")
        s = replaceRegex(timePeriodRegex, in: s, withTemplate: " ")
        s = replaceRegex(paymentAccountNoiseRegex, in: s, withTemplate: " ")
        s = replaceRegex(whitespaceRunRegex, in: s, withTemplate: " ")
        s = s.trimmingCharacters(in: CharacterSet(charactersIn: " ，,。；;：:"))
        s = replaceRegex(leadingActionRegex, in: s, withTemplate: "")
        s = replaceRegex(trailingActionRegex, in: s, withTemplate: "")
        s = replaceRegex(whitespaceRunRegex, in: s, withTemplate: " ")
        s = s.trimmingCharacters(in: CharacterSet(charactersIn: " ，,。；;：:"))
        return String(s.prefix(36))
    }

    private static func inferAutoTags(text: String, categoryName: String, currency: String) -> String {
        let lower = text.lowercased()
        var tags: [String] = []
        if lower.contains("外卖") || categoryName == "外卖" { tags.append("外卖") }
        if lower.contains("美团") { tags.append("美团") }
        if lower.contains("淘宝") || lower.contains("天猫") { tags.append("淘宝"); tags.append("网购") }
        if lower.contains("京东") { tags.append("京东"); tags.append("网购") }
        if lower.contains("拼多多") { tags.append("拼多多"); tags.append("网购") }
        if lower.contains("地铁") || lower.contains("公交") || categoryName == "地铁公交" { tags.append("通勤") }
        if ["数字订阅", "AI与云服务", "软件会员", "影音音乐"].contains(categoryName) { tags.append("定期订阅") }
        if ["差旅住宿", "机票船票", "酒店民宿"].contains(categoryName) { tags.append("差旅") }
        if currency.uppercased() != CurrencyCatalog.defaultBaseCurrency { tags.append("跨境") }
        return TagTextCodec.format(tags)
    }

    // MARK: - NSRegularExpression Utilities

    private static func splitRegex(_ regex: NSRegularExpression, text: String) -> [String] {
        let nsRange = NSRange(text.startIndex..., in: text)
        let matches = regex.matches(in: text, range: nsRange)
        if matches.isEmpty { return [text] }
        var pieces: [String] = []
        var cursor = text.startIndex
        for match in matches {
            guard let range = Range(match.range, in: text) else { continue }
            pieces.append(String(text[cursor..<range.lowerBound]))
            cursor = range.upperBound
        }
        pieces.append(String(text[cursor..<text.endIndex]))
        return pieces
    }

    private static func replaceRegex(_ regex: NSRegularExpression, in text: String, withTemplate template: String) -> String {
        let range = NSRange(text.startIndex..., in: text)
        return regex.stringByReplacingMatches(in: text, range: range, withTemplate: template)
    }

    private static func firstMatch(_ regex: NSRegularExpression, in text: String) -> NSTextCheckingResult? {
        regex.firstMatch(in: text, range: NSRange(text.startIndex..., in: text))
    }

    private static func allMatches(_ regex: NSRegularExpression, in text: String) -> [NSTextCheckingResult] {
        regex.matches(in: text, range: NSRange(text.startIndex..., in: text))
    }

    private static func groupString(_ match: NSTextCheckingResult, at index: Int, in text: String) -> String? {
        guard index < match.numberOfRanges else { return nil }
        let nsRange = match.range(at: index)
        guard nsRange.location != NSNotFound, let range = Range(nsRange, in: text) else { return nil }
        return String(text[range])
    }
}

// MARK: - OpenAI-Compatible LLM + Vision Bill Service

public enum LlmServiceError: LocalizedError {
    case emptyBaseUrl
    case invalidEndpoint(String)
    case missingApiKey
    case emptyImageData
    case httpError(Int, String)
    case invalidResponse(String)

    public var errorDescription: String? {
        switch self {
        case .emptyBaseUrl:
            return "LLM Base URL 不能为空"
        case .invalidEndpoint(let raw):
            return "LLM 接口地址格式无效：\(raw)"
        case .missingApiKey:
            return "请先配置 LLM API Key"
        case .emptyImageData:
            return "图片数据不能为空"
        case .httpError(let code, let detail):
            return "HTTP \(code): \(detail)"
        case .invalidResponse(let detail):
            return detail
        }
    }
}

/// OpenAI 兼容 `/chat/completions` 智能记账与 Vision 小票识图客户端（与 Android `LlmBillParser.kt` 协议对齐）。
public final class LlmBillService: Sendable {
    private let session: URLSession

    public init(session: URLSession = .shared) {
        self.session = session
    }

    /// 优先调用大模型拆单，若未启用或请求失败则自动回退本地 `SmartTextBillParser`（无 API Key 也能用）。
    public func parseSmartInput(
        text: String,
        settings: AppSettings,
        accounts: [AccountDto],
        categories: [CategoryDto],
        aliases: [AccountAliasDto] = [],
        nowMillis: Int64 = currentEpochMillis()
    ) async -> [ParsedBillDraft] {
        let cleaned = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleaned.isEmpty else { return [] }

        if settings.llmEnabled && Self.hasUsableLlmConfig(settings) {
            if let aiDrafts = try? await parseNaturalLanguageBatch(
                text: cleaned,
                settings: settings,
                accounts: accounts,
                categories: categories,
                nowMillis: nowMillis
            ), !aiDrafts.isEmpty {
                return aiDrafts
            }
        }

        return SmartTextBillParser.parseLocalBatch(
            text: cleaned,
            accounts: accounts,
            categories: categories,
            aliases: aliases,
            nowMillis: nowMillis
        )
    }

    public func testConnection(settings: AppSettings) async throws -> String {
        let endpoint = try Self.buildChatCompletionsURL(from: settings.llmBaseUrl)
        try Self.validateSettings(settings, endpoint: endpoint)

        let model = settings.llmModel.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            ? "gpt-4o-mini"
            : settings.llmModel
        let payload: [String: Any] = [
            "model": model,
            "temperature": 0.0,
            "max_tokens": 16,
            "messages": [
                ["role": "user", "content": "Reply with OK only."]
            ]
        ]
        let responseData = try await executeChatRequest(endpoint: endpoint, apiKey: settings.llmApiKey, payload: payload)
        let reply = try Self.extractAssistantContent(from: responseData)
            .trimmingCharacters(in: .whitespacesAndNewlines)
        return "连接成功 (\(model)): \(reply.isEmpty ? "OK" : String(reply.prefix(60)))"
    }

    public func parseNaturalLanguageBatch(
        text: String,
        settings: AppSettings,
        accounts: [AccountDto],
        categories: [CategoryDto],
        nowMillis: Int64 = currentEpochMillis()
    ) async throws -> [ParsedBillDraft] {
        let cleaned = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleaned.isEmpty else { return [] }

        let endpoint = try Self.buildChatCompletionsURL(from: settings.llmBaseUrl)
        try Self.validateSettings(settings, endpoint: endpoint)

        let model = settings.llmModel.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            ? "gpt-4o-mini"
            : settings.llmModel
        let systemPrompt = Self.buildBatchSystemPrompt(accounts: accounts, categories: categories)
        let userPrompt = "请将以下口语或文本拆解为记账明细 JSON 数组：\n\(cleaned)"

        let payload: [String: Any] = [
            "model": model,
            "temperature": 0.1,
            "messages": [
                ["role": "system", "content": systemPrompt],
                ["role": "user", "content": userPrompt]
            ]
        ]

        let responseData = try await executeChatRequest(endpoint: endpoint, apiKey: settings.llmApiKey, payload: payload)
        let content = try Self.extractAssistantContent(from: responseData)
        return try Self.extractBatchDrafts(
            fromContent: content,
            nowMillis: nowMillis,
            accounts: accounts,
            categories: categories
        )
    }

    /// 多模态 Vision 小票/支付截图识图记账（OpenAI Chat Completions `image_url` Base64 Data URI 格式）。
    public func parseReceiptImage(
        imageData: Data,
        mimeType: String = "image/jpeg",
        settings: AppSettings,
        accounts: [AccountDto],
        categories: [CategoryDto],
        nowMillis: Int64 = currentEpochMillis()
    ) async throws -> ParsedBillDraft? {
        guard !imageData.isEmpty else {
            throw LlmServiceError.emptyImageData
        }
        let cleanMime = mimeType.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? "image/jpeg" : mimeType.trimmingCharacters(in: .whitespacesAndNewlines)
        let endpoint = try Self.buildChatCompletionsURL(from: settings.llmBaseUrl)
        try Self.validateSettings(settings, endpoint: endpoint)

        let model = settings.llmModel.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            ? "gpt-4o-mini"
            : settings.llmModel
        let base64 = imageData.base64EncodedString()
        let systemPrompt = Self.buildVisionSystemPrompt(accounts: accounts, categories: categories)

        let userContentParts: [[String: Any]] = [
            [
                "type": "text",
                "text": "请识别这张小票、购物收据或支付凭证图片中的真实交易信息，若无法识别或非账单请返回 {\"isTransaction\": false}："
            ],
            [
                "type": "image_url",
                "image_url": [
                    "url": "data:\(cleanMime);base64,\(base64)"
                ]
            ]
        ]
        let messages: [[String: Any]] = [
            ["role": "system", "content": systemPrompt],
            ["role": "user", "content": userContentParts]
        ]
        let payload: [String: Any] = [
            "model": model,
            "temperature": 0.1,
            "messages": messages
        ]

        let responseData = try await executeChatRequest(endpoint: endpoint, apiKey: settings.llmApiKey, payload: payload)
        let content = try Self.extractAssistantContent(from: responseData)
        return try Self.extractSingleDraft(
            fromContent: content,
            timestamp: nowMillis,
            accounts: accounts,
            categories: categories,
            parserUsed: "LLM_VISION"
        )
    }

    // MARK: - Testable Helpers & Parsers

    public static func hasUsableLlmConfig(_ settings: AppSettings) -> Bool {
        let trimmedUrl = settings.llmBaseUrl.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmedUrl.isEmpty else { return false }
        let isLocal = trimmedUrl.contains("127.0.0.1") || trimmedUrl.contains("localhost")
        return !settings.llmApiKey.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || isLocal
    }

    public static func buildChatCompletionsURL(from baseUrl: String) throws -> URL {
        let trimmed = baseUrl.trimmingCharacters(in: .whitespacesAndNewlines)
            .trimmingCharacters(in: CharacterSet(charactersIn: "/"))
        guard !trimmed.isEmpty else {
            throw LlmServiceError.emptyBaseUrl
        }
        let target = trimmed.lowercased().hasSuffix("/chat/completions")
            ? trimmed
            : "\(trimmed)/chat/completions"
        guard let url = URL(string: target), url.scheme != nil, url.host != nil else {
            throw LlmServiceError.invalidEndpoint(target)
        }
        return url
    }

    public static func extractSingleDraft(
        fromContent rawContent: String,
        timestamp: Int64,
        accounts: [AccountDto],
        categories: [CategoryDto],
        parserUsed: String = "LLM"
    ) throws -> ParsedBillDraft? {
        guard let snippet = extractBalancedJSON(from: rawContent, open: "{", close: "}"),
              let data = snippet.data(using: .utf8),
              let obj = try JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            throw LlmServiceError.invalidResponse("无法从 LLM 输出中提取 JSON 对象")
        }
        return try parseDraftObject(
            obj,
            defaultTimestamp: timestamp,
            accounts: accounts,
            categories: categories,
            parserUsed: parserUsed
        )
    }

    public static func extractBatchDrafts(
        fromContent rawContent: String,
        nowMillis: Int64,
        accounts: [AccountDto],
        categories: [CategoryDto]
    ) throws -> [ParsedBillDraft] {
        guard let snippet = extractFirstJSONArrayOrObject(from: rawContent),
              let data = snippet.data(using: .utf8) else {
            throw LlmServiceError.invalidResponse("无法从 LLM 输出中提取 JSON 数组或对象")
        }
        let parsed = try JSONSerialization.jsonObject(with: data)
        let itemObjects: [[String: Any]]
        if let arr = parsed as? [[String: Any]] {
            itemObjects = arr
        } else if let obj = parsed as? [String: Any] {
            if let isTx = boolValue(obj["isTransaction"]), !isTx {
                return []
            }
            if let arr = (obj["items"] ?? obj["bills"] ?? obj["transactions"] ?? obj["records"]) as? [[String: Any]] {
                itemObjects = arr
            } else if obj["amount"] != nil {
                itemObjects = [obj]
            } else {
                itemObjects = []
            }
        } else {
            itemObjects = []
        }

        var drafts: [ParsedBillDraft] = []
        for item in itemObjects {
            let offsetDays = intValue(item["timestampOffsetDays"]) ?? 0
            let ts = resolveRelativeTimestamp(nowMillis: nowMillis, offsetDays: offsetDays)
            if let draft = try parseDraftObject(
                item,
                defaultTimestamp: ts,
                accounts: accounts,
                categories: categories,
                parserUsed: "LLM_SMART_BATCH"
            ) {
                drafts.append(draft)
            }
        }
        return drafts
    }

    // MARK: - Private Request & JSON Helpers

    private static func validateSettings(_ settings: AppSettings, endpoint: URL) throws {
        let host = (endpoint.host ?? "").lowercased()
        let isLocal = host == "127.0.0.1" || host == "localhost"
        if settings.llmApiKey.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && !isLocal {
            throw LlmServiceError.missingApiKey
        }
    }

    private func executeChatRequest(endpoint: URL, apiKey: String, payload: [String: Any]) async throws -> Data {
        var request = URLRequest(url: endpoint)
        request.httpMethod = "POST"
        request.timeoutInterval = 45
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        let trimmedKey = apiKey.trimmingCharacters(in: .whitespacesAndNewlines)
        if !trimmedKey.isEmpty {
            request.setValue("Bearer \(trimmedKey)", forHTTPHeaderField: "Authorization")
        }
        request.httpBody = try JSONSerialization.data(withJSONObject: payload)

        let (data, response) = try await session.data(for: request)
        guard let http = response as? HTTPURLResponse else {
            throw LlmServiceError.invalidResponse("无效的 HTTP 响应")
        }
        guard (200...299).contains(http.statusCode) else {
            let detail = Self.extractErrorMessage(from: data)
            throw LlmServiceError.httpError(
                http.statusCode,
                detail.isEmpty ? HTTPURLResponse.localizedString(forStatusCode: http.statusCode) : detail
            )
        }
        guard !data.isEmpty else {
            throw LlmServiceError.invalidResponse("LLM 返回空响应体")
        }
        return data
    }

    private static func extractErrorMessage(from data: Data) -> String {
        if let obj = try? JSONSerialization.jsonObject(with: data) as? [String: Any] {
            if let err = obj["error"] as? [String: Any], let msg = err["message"] as? String {
                return msg
            }
            if let msg = obj["message"] as? String {
                return msg
            }
        }
        return String((String(data: data, encoding: .utf8) ?? "").prefix(180))
    }

    private static func extractAssistantContent(from data: Data) throws -> String {
        guard let root = try JSONSerialization.jsonObject(with: data) as? [String: Any],
              let choices = root["choices"] as? [[String: Any]],
              let first = choices.first,
              let message = first["message"] as? [String: Any],
              let content = message["content"] else {
            throw LlmServiceError.invalidResponse("LLM 响应缺少 choices/message/content")
        }
        if let text = content as? String {
            return text
        }
        if let parts = content as? [[String: Any]] {
            let joined = parts.compactMap { $0["text"] as? String }.joined(separator: "\n")
            if !joined.isEmpty { return joined }
        }
        throw LlmServiceError.invalidResponse("LLM 响应内容为空")
    }

    private static func parseDraftObject(
        _ obj: [String: Any],
        defaultTimestamp: Int64,
        accounts: [AccountDto],
        categories: [CategoryDto],
        parserUsed: String
    ) throws -> ParsedBillDraft? {
        if let isTx = boolValue(obj["isTransaction"]), !isTx {
            return nil
        }
        let rawType = stringValue(obj["type"])?.uppercased() ?? ""
        let type: String
        switch rawType {
        case TransactionType.INCOME, "收入": type = TransactionType.INCOME
        case TransactionType.TRANSFER, "转账": type = TransactionType.TRANSFER
        default: type = TransactionType.EXPENSE
        }

        guard let amountRaw = stringValue(obj["amount"]),
              let origAmountMicros = MoneyAmount.parseToMicros(amountRaw),
              origAmountMicros > 0 else {
            throw LlmServiceError.invalidResponse("LLM 返回缺失或非法金额")
        }

        let rawCurrency = stringValue(obj["currency"]) ?? ""
        let origCurrency = rawCurrency.isEmpty
            ? CurrencyCatalog.defaultBaseCurrency
            : CurrencyCatalog.find(rawCurrency).code

        let activeAccounts = accounts.filter { !$0.archived }
        let accountPool = activeAccounts.isEmpty ? accounts : activeAccounts
        let accountHint = (stringValue(obj["accountHint"]) ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        let requestedAccountId = int64Value(obj["accountId"])
        let requestedAccountName = (stringValue(obj["accountName"]) ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        let resolvedAccountId = resolveAccountId(
            requestedId: requestedAccountId,
            requestedName: requestedAccountName,
            accountHint: accountHint,
            currency: origCurrency,
            accounts: accountPool
        )

        let typedCategories = categories.filter { $0.type.caseInsensitiveCompare(type) == .orderedSame }
        let categoryPool = typedCategories.isEmpty ? categories : typedCategories
        let requestedCatId = int64Value(obj["categoryId"])
        let requestedCatName = (stringValue(obj["categoryName"]) ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        let resolvedCategory: CategoryDto?
        if type == TransactionType.TRANSFER && requestedCatId == nil && requestedCatName.isEmpty {
            resolvedCategory = nil
        } else {
            resolvedCategory = resolveCategory(
                requestedId: requestedCatId,
                requestedName: requestedCatName,
                categories: categoryPool
            )
        }

        let merchant = (stringValue(obj["merchant"]) ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        let note = (stringValue(obj["note"]) ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        let confidence = min(1.0, max(0.0, doubleValue(obj["confidence"]) ?? 0.85))
        let dateStr = (stringValue(obj["date"]) ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        let finalTimestamp = dateStr.isEmpty
            ? defaultTimestamp
            : (parseOptionalDateMillis(dateStr, fallbackMillis: defaultTimestamp) ?? defaultTimestamp)

        return ParsedBillDraft(
            type: type,
            origAmountMicros: origAmountMicros,
            origCurrency: origCurrency,
            accountId: resolvedAccountId,
            accountHint: accountHint,
            categoryId: resolvedCategory?.id,
            categoryName: resolvedCategory?.name ?? requestedCatName,
            merchant: merchant,
            note: note,
            timestamp: finalTimestamp,
            confidence: confidence,
            parserUsed: parserUsed
        )
    }

    private static func resolveAccountId(
        requestedId: Int64?,
        requestedName: String,
        accountHint: String,
        currency: String,
        accounts: [AccountDto]
    ) -> Int64? {
        guard !accounts.isEmpty else { return nil }
        if let id = requestedId, accounts.contains(where: { $0.id == id }) { return id }
        if !requestedName.isEmpty {
            if let exact = accounts.first(where: { $0.name.caseInsensitiveCompare(requestedName) == .orderedSame }) { return exact.id }
            if let contains = accounts.first(where: { $0.name.localizedCaseInsensitiveContains(requestedName) }) { return contains.id }
            if let rev = accounts.first(where: { $0.name.count >= 2 && requestedName.localizedCaseInsensitiveContains($0.name) }) { return rev.id }
        }
        if !accountHint.isEmpty, let byHint = accounts.first(where: { $0.name.contains(accountHint) }) { return byHint.id }
        if let byCurrency = accounts.first(where: { $0.currencyCode.caseInsensitiveCompare(currency) == .orderedSame }) { return byCurrency.id }
        return accounts.first?.id
    }

    private static func resolveCategory(
        requestedId: Int64?,
        requestedName: String,
        categories: [CategoryDto]
    ) -> CategoryDto? {
        guard !categories.isEmpty else { return nil }
        if let id = requestedId, let found = categories.first(where: { $0.id == id }) { return found }
        if !requestedName.isEmpty {
            if let exact = categories.first(where: { $0.name.caseInsensitiveCompare(requestedName) == .orderedSame }) { return exact }
            if let fuzzy = categories.first(where: {
                $0.name.localizedCaseInsensitiveContains(requestedName) || requestedName.localizedCaseInsensitiveContains($0.name)
            }) { return fuzzy }
        }
        return categories.first
    }

    private static func stripCodeFence(_ raw: String) -> String {
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard let regex = try? NSRegularExpression(pattern: #"```(?:json)?\s*([\s\S]*?)```"#, options: .caseInsensitive),
              let match = regex.firstMatch(in: trimmed, range: NSRange(trimmed.startIndex..., in: trimmed)),
              match.numberOfRanges > 1,
              let range = Range(match.range(at: 1), in: trimmed) else {
            return trimmed
        }
        return String(trimmed[range]).trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private static func extractFirstJSONArrayOrObject(from raw: String) -> String? {
        let stripped = stripCodeFence(raw)
        let firstBracket = stripped.firstIndex(of: "[")
        let firstBrace = stripped.firstIndex(of: "{")
        if let b = firstBracket, firstBrace == nil || b < firstBrace! {
            return extractBalancedJSON(from: stripped, open: "[", close: "]")
                ?? extractBalancedJSON(from: stripped, open: "{", close: "}")
        }
        if firstBrace != nil {
            return extractBalancedJSON(from: stripped, open: "{", close: "}")
                ?? extractBalancedJSON(from: stripped, open: "[", close: "]")
        }
        return nil
    }

    private static func extractBalancedJSON(from raw: String, open: Character, close: Character) -> String? {
        let stripped = stripCodeFence(raw)
        var startIndex: String.Index?
        var depth = 0
        var inString = false
        var escaped = false

        var idx = stripped.startIndex
        while idx < stripped.endIndex {
            let ch = stripped[idx]
            if escaped {
                escaped = false
            } else if ch == "\\" && inString {
                escaped = true
            } else if ch == "\"" {
                inString.toggle()
            } else if !inString {
                if ch == open {
                    if depth == 0 { startIndex = idx }
                    depth += 1
                } else if ch == close && depth > 0 {
                    depth -= 1
                    if depth == 0, let start = startIndex {
                        return String(stripped[start...idx])
                    }
                }
            }
            idx = stripped.index(after: idx)
        }
        return nil
    }

    private static func formatContext(accounts: [AccountDto], categories: [CategoryDto]) -> (String, String) {
        let active = accounts.filter { !$0.archived }
        let accList = (active.isEmpty ? accounts : active).map {
            "- id=\($0.id), name=\"\($0.name)\", currency=\($0.currencyCode), group=\($0.groupType)"
        }.joined(separator: "\n")
        let catList = categories.map {
            "- id=\($0.id), name=\"\($0.name)\", type=\($0.type)"
        }.joined(separator: "\n")
        return (accList.isEmpty ? "(暂无账户)" : accList, catList.isEmpty ? "(暂无分类)" : catList)
    }

    private static func buildBatchSystemPrompt(accounts: [AccountDto], categories: [CategoryDto]) -> String {
        let (accountsDesc, categoriesDesc) = formatContext(accounts: accounts, categories: categories)
        return """
        你是「余白 YUBRAE」的一句话语音/自然语言智能记账助手。
        用户可能在一句话中同时口述 1 到多笔收支或转账（并可能包含今天/昨天/前天等相对时间）。
        请严格按以下 JSON 数组格式输出全部识别出的明细（仅输出 JSON 数组，不要包含 Markdown 代码块或多余解释）：
        [
          {
            "type": "EXPENSE 或 INCOME 或 TRANSFER",
            "amount": "纯数字金额字符串如 28.50",
            "currency": "ISO 4217 币种代码如 CNY, USD, EUR, GBP, JPY, HKD",
            "merchant": "商户或交易对象名称",
            "categoryName": "最匹配的分类名称",
            "accountName": "最匹配的账户名称",
            "note": "简短中文备注",
            "timestampOffsetDays": 0
          }
        ]
        其中 timestampOffsetDays 为相对今天的天数偏移：今天=0，昨天/昨晚=-1，前天=-2。若整段文本不包含任何有效记账金额，返回 []。

        可用账户列表:
        \(accountsDesc)

        可用分类列表:
        \(categoriesDesc)
        """
    }

    private static func buildVisionSystemPrompt(accounts: [AccountDto], categories: [CategoryDto]) -> String {
        let (accountsDesc, categoriesDesc) = formatContext(accounts: accounts, categories: categories)
        return """
        你是「余白 YUBRAE」的小票、发票与账单截图视觉识图记账助手。
        请仔细识别图片中的实付总金额、币种、商户名称、支付方式/银行卡尾号、交易时间与商品明细，并严格只输出一个 JSON 对象（不要包含 Markdown 解释或多余文字）：

        可用账户列表:
        \(accountsDesc)

        可用分类列表:
        \(categoriesDesc)

        输出格式:
        1. 若图片不是消费小票、收据或账单凭证，输出:
           {"isTransaction": false}
        2. 若属于真实交易小票或账单截图，输出:
           {
             "isTransaction": true,
             "type": "EXPENSE 或 INCOME 或 TRANSFER",
             "amount": "实付总金额纯数字字符串如 45.50",
             "currency": "ISO 4217 币种代码如 CNY, USD, EUR, GBP, JPY, HKD",
             "accountId": 匹配的账户id或null,
             "accountName": "匹配的账户名称",
             "accountHint": "图片中银行卡尾号4位数字如6688，无则空字符串",
             "categoryId": 匹配的分类id或null,
             "categoryName": "最匹配的分类名称",
             "merchant": "商户名称",
             "note": "简短商品明细或中文备注",
             "date": "可选交易日期时间 yyyy-MM-dd HH:mm 或 yyyy-MM-dd，无则空字符串",
             "confidence": 0.90
           }
        """
    }

    private static func resolveRelativeTimestamp(nowMillis: Int64, offsetDays: Int) -> Int64 {
        guard offsetDays != 0 else { return nowMillis }
        let clamped = max(-365, min(30, offsetDays))
        let base = Date(timeIntervalSince1970: Double(nowMillis) / 1000.0)
        let target = Calendar.current.date(byAdding: .day, value: clamped, to: base) ?? base
        return Int64((target.timeIntervalSince1970 * 1000.0).rounded())
    }

    private static func parseOptionalDateMillis(_ raw: String, fallbackMillis: Int64) -> Int64? {
        let df = DateFormatter()
        df.locale = Locale(identifier: "en_US_POSIX")
        df.timeZone = .current
        for fmt in ["yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm"] {
            df.dateFormat = fmt
            if let date = df.date(from: raw) {
                return Int64((date.timeIntervalSince1970 * 1000.0).rounded())
            }
        }
        df.dateFormat = "yyyy-MM-dd"
        if let date = df.date(from: raw) {
            let cal = Calendar.current
            let fb = Date(timeIntervalSince1970: Double(fallbackMillis) / 1000.0)
            let timeComps = cal.dateComponents([.hour, .minute, .second], from: fb)
            let combined = cal.date(
                bySettingHour: timeComps.hour ?? 12,
                minute: timeComps.minute ?? 0,
                second: timeComps.second ?? 0,
                of: date
            ) ?? date
            return Int64((combined.timeIntervalSince1970 * 1000.0).rounded())
        }
        return nil
    }

    private static func stringValue(_ any: Any?) -> String? {
        if any == nil || any is NSNull { return nil }
        if let str = any as? String { return str }
        if let num = any as? NSNumber {
            if CFGetTypeID(num) == CFBooleanGetTypeID() { return nil }
            return num.stringValue
        }
        return nil
    }

    private static func boolValue(_ any: Any?) -> Bool? {
        if let b = any as? Bool { return b }
        if let str = any as? String {
            switch str.trimmingCharacters(in: .whitespacesAndNewlines).lowercased() {
            case "true", "1": return true
            case "false", "0": return false
            default: return nil
            }
        }
        return nil
    }

    private static func intValue(_ any: Any?) -> Int? {
        if let n = any as? NSNumber, CFGetTypeID(n) != CFBooleanGetTypeID() { return n.intValue }
        if let s = any as? String { return Int(s.trimmingCharacters(in: .whitespacesAndNewlines)) }
        return nil
    }

    private static func int64Value(_ any: Any?) -> Int64? {
        if let n = any as? NSNumber, CFGetTypeID(n) != CFBooleanGetTypeID() { return n.int64Value }
        if let s = any as? String { return Int64(s.trimmingCharacters(in: .whitespacesAndNewlines)) }
        return nil
    }

    private static func doubleValue(_ any: Any?) -> Double? {
        if let n = any as? NSNumber, CFGetTypeID(n) != CFBooleanGetTypeID() { return n.doubleValue }
        if let s = any as? String { return Double(s.trimmingCharacters(in: .whitespacesAndNewlines)) }
        return nil
    }
}

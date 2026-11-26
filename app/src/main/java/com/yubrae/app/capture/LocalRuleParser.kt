package com.yubrae.app.capture

import com.yubrae.app.core.contract.CaptureInput
import com.yubrae.app.core.contract.ParsedBillDraft
import com.yubrae.app.core.model.CurrencyCatalog
import com.yubrae.app.core.model.MoneyAmount
import com.yubrae.app.data.db.AccountAliasEntity
import com.yubrae.app.data.db.AccountEntity
import com.yubrae.app.data.db.AccountGroup
import com.yubrae.app.data.db.AliasMatchType
import com.yubrae.app.data.db.CategoryEntity
import com.yubrae.app.data.db.ParseRuleEntity
import com.yubrae.app.data.db.TagTextCodec
import com.yubrae.app.data.db.TransactionType
import java.util.Locale

/**
 * 本地规则引擎与启发式兜底解析器（纯 JVM 可测，不依赖 Android Framework）。
 */
class LocalRuleParser {

    fun isNoise(title: String, content: String): Boolean {
        val combined = "$title\n$content".trim()
        if (combined.isBlank()) return true
        val lower = combined.lowercase(Locale.ROOT)

        // 1. 验证码 / 取件码 / 安全码等绝对噪声
        if (STRONG_NOISE_KEYWORDS.any { lower.contains(it) }) {
            return true
        }

        // 2. 营销推广 / 退订短信
        if (PROMO_NOISE_KEYWORDS.any { lower.contains(it) }) {
            return true
        }

        // 3. 系统更新 / 同步类通知
        if (SYSTEM_NOISE_KEYWORDS.any { lower.contains(it) }) {
            return true
        }

        // 4. 登录与安全验证类通知（无真实动账）
        val hasPaymentReceiptPhrase = PAYMENT_CONFIRM_KEYWORDS.any { lower.contains(it) }
        if (LOGIN_NOISE_KEYWORDS.any { lower.contains(it) } && !hasPaymentReceiptPhrase) {
            return true
        }

        // 5. 快递物流通知（如“快件已派送”“包裹已签收”，排除明确付款凭证）
        if (DELIVERY_NOISE_KEYWORDS.any { lower.contains(it) } && !hasPaymentReceiptPhrase) {
            return true
        }

        // 6. 账单到期/应还提醒（未实际扣款）
        if (BILL_REMINDER_KEYWORDS.any { lower.contains(it) } && !BILL_PAID_KEYWORDS.any { lower.contains(it) }) {
            return true
        }

        // 7. 交通卡纯进站 / 余额不足 / 开卡 / NFC 开启等非扣费状态（含明确扣费关键词及非余额金额时不滤）
        if (TRANSIT_NON_PAYMENT_NOISE_KEYWORDS.any { lower.contains(it) }) {
            val cleanedWithoutBalance = stripBalanceClauses(combined)
            val cleanedLower = cleanedWithoutBalance.lowercase(Locale.ROOT)
            val hasTransitChargeCue = !cleanedLower.contains("失败") &&
                TRANSIT_CHARGE_KEYWORDS.any { cleanedLower.contains(it) } &&
                extractAmountAndCurrency(cleanedWithoutBalance) != null
            if (!hasTransitChargeCue) {
                return true
            }
        }

        return false
    }

    fun parse(
        input: CaptureInput,
        rules: List<ParseRuleEntity>,
        aliases: List<AccountAliasEntity>,
        accounts: List<AccountEntity>,
        categories: List<CategoryEntity>
    ): ParsedBillDraft? {
        if (isNoise(input.title, input.content)) return null

        val activeAccounts = accounts.filter { !it.archived }.ifEmpty { accounts }
        val sortedRules = rules
            .filter { it.enabled }
            .sortedWith(compareByDescending<ParseRuleEntity> { it.priority }.thenBy { it.id })

        val ruleDraft = matchByParseRules(
            input = input,
            rules = sortedRules,
            aliases = aliases,
            accounts = activeAccounts,
            categories = categories
        )
        if (ruleDraft != null) return ruleDraft

        return parseHeuristically(
            input = input,
            aliases = aliases,
            accounts = activeAccounts,
            categories = categories
        )
    }

    fun parseExplicitRules(
        input: CaptureInput,
        rules: List<ParseRuleEntity>,
        aliases: List<AccountAliasEntity>,
        accounts: List<AccountEntity>,
        categories: List<CategoryEntity>
    ): ParsedBillDraft? {
        if (isNoise(input.title, input.content)) return null

        val activeAccounts = accounts.filter { !it.archived }.ifEmpty { accounts }
        val sortedRules = rules
            .filter { it.enabled }
            .sortedWith(compareByDescending<ParseRuleEntity> { it.priority }.thenBy { it.id })

        return matchByParseRules(
            input = input,
            rules = sortedRules,
            aliases = aliases,
            accounts = activeAccounts,
            categories = categories
        )
    }

    fun parseHeuristicallyOnly(
        input: CaptureInput,
        aliases: List<AccountAliasEntity>,
        accounts: List<AccountEntity>,
        categories: List<CategoryEntity>
    ): ParsedBillDraft? {
        if (isNoise(input.title, input.content)) return null

        val activeAccounts = accounts.filter { !it.archived }.ifEmpty { accounts }
        return parseHeuristically(
            input = input,
            aliases = aliases,
            accounts = activeAccounts,
            categories = categories
        )
    }

    private fun matchByParseRules(
        input: CaptureInput,
        rules: List<ParseRuleEntity>,
        aliases: List<AccountAliasEntity>,
        accounts: List<AccountEntity>,
        categories: List<CategoryEntity>
    ): ParsedBillDraft? {
        val combinedText = buildCombinedText(input.title, input.content)
        val candidates = buildList {
            if (input.content.isNotBlank()) add(input.content)
            if (combinedText.isNotBlank() && combinedText != input.content) add(combinedText)
        }

        for (rule in rules) {
            if (!matchesSourcePackage(rule.sourcePackage, input.sourcePackage)) continue
            if (!matchesChannel(rule.channelType, input.channel)) continue

            val regex = compileRegex(rule.regexPattern) ?: continue
            for (targetText in candidates) {
                val match = regex.find(targetText) ?: continue
                val amountRaw = groupValueOrNull(match, rule.amountGroup) ?: continue
                val amountMicros = MoneyAmount.parseToMicros(amountRaw) ?: continue
                if (amountMicros <= 0L) continue

                val currency = resolveRuleCurrency(
                    rule = rule,
                    match = match,
                    targetText = targetText,
                    amountGroup = rule.amountGroup
                )
                val rawHint = groupValueOrNull(match, rule.accountHintGroup).orEmpty().trim()
                val accountHint = rawHint.ifBlank { extractAccountHint(combinedText) }

                val rawMerchant = groupValueOrNull(match, rule.merchantGroup).orEmpty()
                val cleanedMerchant = cleanMerchant(rawMerchant).ifBlank {
                    extractMerchant(input.title, input.content)
                }

                val accountId = resolveAccountId(
                    input = input,
                    fullText = combinedText,
                    accountHint = accountHint,
                    currency = currency,
                    aliases = aliases,
                    accounts = accounts
                )

                val category = resolveCategory(
                    type = rule.transactionType,
                    merchant = cleanedMerchant,
                    fullText = combinedText,
                    ruleDefaultCategoryName = rule.defaultCategoryName,
                    categories = categories
                )
                val resolvedCategoryName = category?.name ?: rule.defaultCategoryName
                val autoTags = inferAutoTags(
                    sourcePackage = input.sourcePackage,
                    merchant = cleanedMerchant,
                    fullText = combinedText,
                    categoryName = resolvedCategoryName,
                    currency = currency
                )

                return ParsedBillDraft(
                    type = rule.transactionType,
                    origAmountMicros = amountMicros,
                    origCurrency = currency,
                    accountId = accountId,
                    accountHint = accountHint,
                    categoryId = category?.id,
                    categoryName = resolvedCategoryName,
                    merchant = cleanedMerchant,
                    note = buildCompactNote(input.title, input.content, cleanedMerchant),
                    timestamp = input.timestamp,
                    confidence = 0.94f,
                    parserUsed = "LOCAL_RULE:${rule.name}",
                    tags = autoTags
                )
            }
        }
        return null
    }

    private fun parseHeuristically(
        input: CaptureInput,
        aliases: List<AccountAliasEntity>,
        accounts: List<AccountEntity>,
        categories: List<CategoryEntity>
    ): ParsedBillDraft? {
        val combinedText = buildCombinedText(input.title, input.content)
        val cleanedText = stripBalanceClauses(combinedText)
        val lower = cleanedText.lowercase(Locale.ROOT)

        val hasExpenseCue = EXPENSE_VERBS.any { lower.contains(it) }
        val hasIncomeCue = INCOME_VERBS.any { lower.contains(it) }
        if (!hasExpenseCue && !hasIncomeCue) return null

        val type = when {
            hasIncomeCue && !hasExpenseCue -> TransactionType.INCOME
            hasExpenseCue && !hasIncomeCue -> TransactionType.EXPENSE
            else -> inferDominantType(lower)
        }

        val extracted = extractAmountAndCurrency(cleanedText) ?: return null
        val (amountMicros, currency) = extracted
        if (amountMicros <= 0L) return null

        val accountHint = extractAccountHint(combinedText)
        val merchant = extractMerchant(input.title, input.content)
        val accountId = resolveAccountId(
            input = input,
            fullText = combinedText,
            accountHint = accountHint,
            currency = currency,
            aliases = aliases,
            accounts = accounts
        )
        val defaultCat = if (type == TransactionType.INCOME) "其他收入" else "其他支出"
        val category = resolveCategory(
            type = type,
            merchant = merchant,
            fullText = combinedText,
            ruleDefaultCategoryName = defaultCat,
            categories = categories
        )
        val resolvedCategoryName = category?.name ?: defaultCat
        val autoTags = inferAutoTags(
            sourcePackage = input.sourcePackage,
            merchant = merchant,
            fullText = combinedText,
            categoryName = resolvedCategoryName,
            currency = currency
        )

        return ParsedBillDraft(
            type = type,
            origAmountMicros = amountMicros,
            origCurrency = currency,
            accountId = accountId,
            accountHint = accountHint,
            categoryId = category?.id,
            categoryName = resolvedCategoryName,
            merchant = merchant,
            note = buildCompactNote(input.title, input.content, merchant),
            timestamp = input.timestamp,
            confidence = 0.78f,
            parserUsed = "LOCAL_HEURISTIC",
            tags = autoTags
        )
    }

    private fun resolveRuleCurrency(
        rule: ParseRuleEntity,
        match: MatchResult,
        targetText: String,
        amountGroup: Int
    ): String {
        val explicit = groupValueOrNull(match, rule.currencyGroup)
        if (!explicit.isNullOrBlank()) {
            return normalizeCurrencyToken(explicit, rule.defaultCurrency)
        }

        // 若规则未配置 currencyGroup，检查金额紧邻上下文是否出现明确外币单位（如 "消费15.00美元"）
        val amountMatchGroup = match.groups[amountGroup]
        if (amountMatchGroup != null) {
            val start = (amountMatchGroup.range.first - 8).coerceAtLeast(0)
            val end = (amountMatchGroup.range.last + 8).coerceAtMost(targetText.length - 1)
            if (start <= end) {
                val snippet = targetText.substring(start, end + 1)
                val detected = CurrencyCatalog.detectCurrencyFromText(snippet, defaultCode = "")
                if (detected.isNotBlank()) return detected
            }
        }
        return normalizeCurrencyToken(rule.defaultCurrency, CurrencyCatalog.DEFAULT_BASE_CURRENCY)
    }

    internal fun resolveAccountId(
        input: CaptureInput,
        fullText: String,
        accountHint: String,
        currency: String,
        aliases: List<AccountAliasEntity>,
        accounts: List<AccountEntity>
    ): Long? {
        if (accounts.isEmpty()) return null
        val activeIds = accounts.map { it.id }.toSet()
        val enabledAliases = aliases
            .filter { it.enabled && it.accountId in activeIds }
            .sortedWith(compareByDescending<AccountAliasEntity> { it.priority }.thenBy { it.id })

        // 1. 优先精确匹配银行卡尾号（物理卡指纹优先于通用渠道包名/关键字）
        val cardMatches = enabledAliases.filter { alias ->
            alias.matchType.equals(AliasMatchType.CARD_LAST4, ignoreCase = true) &&
                matchesCardLast4Alias(alias.pattern, accountHint, fullText)
        }
        if (cardMatches.isNotEmpty()) {
            return cardMatches.first().accountId
        }

        // 2. 按 priority 降序匹配 REGEX / KEYWORD / PACKAGE
        for (alias in enabledAliases) {
            val matched = when (alias.matchType.uppercase(Locale.ROOT)) {
                AliasMatchType.REGEX -> compileRegex(alias.pattern)?.containsMatchIn(fullText) == true
                AliasMatchType.KEYWORD -> alias.pattern.isNotBlank() &&
                    fullText.contains(alias.pattern.trim(), ignoreCase = true)
                AliasMatchType.PACKAGE -> alias.pattern.isNotBlank() &&
                    alias.pattern.trim().equals(input.sourcePackage.trim(), ignoreCase = true)
                else -> false
            }
            if (matched) return alias.accountId
        }

        // 3. 若消费币种为外币，尝试匹配同币种账户（优先 FOREIGN 组）
        val upperCurrency = currency.uppercase(Locale.ROOT)
        val sameCurrencyAccounts = accounts.filter { it.currencyCode.equals(upperCurrency, ignoreCase = true) }
        if (sameCurrencyAccounts.isNotEmpty()) {
            val preferred = if (upperCurrency != CurrencyCatalog.DEFAULT_BASE_CURRENCY) {
                sameCurrencyAccounts.firstOrNull { it.groupType == AccountGroup.FOREIGN }
            } else {
                sameCurrencyAccounts.firstOrNull { it.groupType == AccountGroup.WALLET || it.groupType == AccountGroup.DEBIT }
            }
            return (preferred ?: sameCurrencyAccounts.first()).id
        }

        return accounts.firstOrNull()?.id
    }

    private fun matchesCardLast4Alias(pattern: String, accountHint: String, fullText: String): Boolean {
        val cleanPattern = pattern.trim()
        if (cleanPattern.isEmpty()) return false
        if (accountHint.isNotBlank() && accountHint.equals(cleanPattern, ignoreCase = true)) {
            return true
        }
        val escaped = Regex.escape(cleanPattern)
        val cardRegex = Regex("(?:尾号|末四位|卡号[*xX]*|[*]{2,}|\\(|（|ending\\s+in)\\s*$escaped\\b", RegexOption.IGNORE_CASE)
        return cardRegex.containsMatchIn(fullText)
    }

    internal fun resolveCategory(
        type: String,
        merchant: String,
        fullText: String,
        ruleDefaultCategoryName: String,
        categories: List<CategoryEntity>
    ): CategoryEntity? {
        if (categories.isEmpty()) return null
        val typedCategories = categories.filter { it.type.equals(type, ignoreCase = true) }
            .ifEmpty { categories }

        val mappingRules = if (type == TransactionType.INCOME) INCOME_CATEGORY_KEYWORDS else EXPENSE_CATEGORY_KEYWORDS
        val trimmedMerchant = merchant.trim()
        val fullHaystack = "$merchant $fullText".lowercase(Locale.ROOT)

        // 商户优先匹配：若商户明确且不属于泛平台/泛渠道壳商户，优先仅基于商户名归类，防止底部广告/杂项词抢占分类
        if (trimmedMerchant.isNotEmpty() && !isGenericPlatformShellMerchant(trimmedMerchant)) {
            val merchantLower = trimmedMerchant.lowercase(Locale.ROOT)
            for ((keywords, candidateCategoryNames) in mappingRules) {
                if (keywords.any { merchantLower.contains(it) }) {
                    val matched = findBestCategoryMatch(candidateCategoryNames, typedCategories)
                    if (matched != null) {
                        if (type == TransactionType.EXPENSE &&
                            shouldPreferTakeoutOverMerchantDining(candidateCategoryNames, fullHaystack)
                        ) {
                            findBestCategoryMatch(TAKEOUT_CANDIDATE_CATEGORIES, typedCategories)?.let { return it }
                        }
                        return matched
                    }
                }
            }
        }

        for ((keywords, candidateCategoryNames) in mappingRules) {
            if (keywords.any { fullHaystack.contains(it) }) {
                findBestCategoryMatch(candidateCategoryNames, typedCategories)?.let { return it }
            }
        }

        if (ruleDefaultCategoryName.isNotBlank()) {
            findBestCategoryMatch(listOf(ruleDefaultCategoryName), typedCategories)?.let { return it }
        }

        val fallbackName = if (type == TransactionType.INCOME) "其他收入" else "其他支出"
        return findBestCategoryMatch(listOf(fallbackName), typedCategories) ?: typedCategories.firstOrNull()
    }

    private fun isGenericPlatformShellMerchant(merchant: String): Boolean {
        val trimmed = merchant.trim()
        if (trimmed.isEmpty()) return true
        val lower = trimmed.lowercase(Locale.ROOT)
        if (lower in GENERIC_PLATFORM_SHELL_MERCHANTS) return true
        if (GENERIC_MERCHANT_PREFIXES.any { lower.startsWith(it) }) return true
        if (GENERIC_MERCHANT_SUFFIXES.any { lower.endsWith(it) }) return true
        if (GENERIC_PLATFORM_SHELL_SUBSTRINGS.any { lower.contains(it) }) return true
        return PLATFORM_STORE_SUFFIX_REGEX.containsMatchIn(lower)
    }

    private fun shouldPreferTakeoutOverMerchantDining(
        candidateCategoryNames: List<String>,
        fullHaystack: String
    ): Boolean {
        if (candidateCategoryNames.none { it in DINING_CATEGORY_NAMES }) return false
        return TAKEOUT_OVERRIDE_KEYWORDS.any { fullHaystack.contains(it) }
    }

    private fun findBestCategoryMatch(
        candidateNames: List<String>,
        categories: List<CategoryEntity>
    ): CategoryEntity? {
        for (candidate in candidateNames) {
            categories.firstOrNull { it.name.equals(candidate, ignoreCase = true) }?.let { return it }
        }
        for (candidate in candidateNames) {
            categories.firstOrNull {
                it.name.contains(candidate, ignoreCase = true) || candidate.contains(it.name, ignoreCase = true)
            }?.let { return it }
        }
        return null
    }

    private fun extractAmountAndCurrency(cleanedText: String): Pair<Long, String>? {
        // 优先 1：动账动词紧跟币种与金额
        VERB_AMOUNT_REGEX.find(cleanedText)?.let { match ->
            val prefixCur = match.groupValues.getOrNull(1).orEmpty()
            val numStr = match.groupValues.getOrNull(2).orEmpty()
            val suffixCur = match.groupValues.getOrNull(3).orEmpty()
            val micros = MoneyAmount.parseToMicros(numStr)
            if (micros != null && micros > 0L) {
                val cur = resolveCurrencyFromTokens(prefixCur, suffixCur, cleanedText)
                return micros to cur
            }
        }

        // 优先 2：明确带币种符号或货币单位的金额（使用 findAll 跳过日期/编号等无币种纯数字）
        for (match in CURRENCY_AMOUNT_REGEX.findAll(cleanedText)) {
            val prefixCur = match.groupValues.getOrNull(1).orEmpty()
            val numStr = match.groupValues.getOrNull(2).orEmpty()
            val suffixCur = match.groupValues.getOrNull(3).orEmpty()
            if (prefixCur.isNotBlank() || suffixCur.isNotBlank()) {
                val micros = MoneyAmount.parseToMicros(numStr)
                if (micros != null && micros > 0L) {
                    val cur = resolveCurrencyFromTokens(prefixCur, suffixCur, cleanedText)
                    return micros to cur
                }
            }
        }

        // 优先 3：裸小数金额（排除日期/时间/4位纯整数尾号）
        DECIMAL_AMOUNT_REGEX.find(cleanedText)?.let { match ->
            val micros = MoneyAmount.parseToMicros(match.value)
            if (micros != null && micros > 0L) {
                val cur = CurrencyCatalog.detectCurrencyFromText(cleanedText, CurrencyCatalog.DEFAULT_BASE_CURRENCY)
                return micros to cur
            }
        }
        return null
    }

    private fun resolveCurrencyFromTokens(
        prefixCur: String,
        suffixCur: String,
        fullText: String
    ): String {
        val rawToken = prefixCur.ifBlank { suffixCur }
        if (rawToken.isNotBlank()) {
            return normalizeCurrencyToken(rawToken, CurrencyCatalog.DEFAULT_BASE_CURRENCY)
        }
        return CurrencyCatalog.detectCurrencyFromText(fullText, CurrencyCatalog.DEFAULT_BASE_CURRENCY)
    }

    internal fun normalizeCurrencyToken(raw: String, defaultCode: String): String {
        val trimmed = raw.trim()
        val upper = trimmed.uppercase(Locale.ROOT)
        return when {
            upper in setOf("CNY", "RMB", "人民币", "元", "¥", "￥") -> "CNY"
            upper in setOf("USD", "US$", "$", "美元", "美金") -> "USD"
            upper in setOf("EUR", "€", "欧元") -> "EUR"
            upper in setOf("GBP", "£", "英镑") -> "GBP"
            upper in setOf("HKD", "HK$", "港币", "港元") -> "HKD"
            upper in setOf("JPY", "JP¥", "日元") -> "JPY"
            upper in setOf("SGD", "S$", "新元", "新加坡元") -> "SGD"
            upper in setOf("AUD", "A$", "澳元") -> "AUD"
            upper in setOf("CAD", "C$", "加元") -> "CAD"
            upper in setOf("CHF", "瑞郎", "瑞士法郎") -> "CHF"
            upper in setOf("TWD", "NT$", "新台币") -> "TWD"
            upper in setOf("KRW", "₩", "韩元") -> "KRW"
            else -> CurrencyCatalog.detectCurrencyFromText(trimmed, defaultCode)
        }
    }

    internal fun extractAccountHint(text: String): String {
        val match = CARD_LAST4_REGEX.find(text) ?: return ""
        return match.groupValues.getOrNull(1).orEmpty().trim()
    }

    internal fun extractMerchant(title: String, content: String): String {
        val candidates = listOf(content, title)
        for (text in candidates) {
            for (pattern in MERCHANT_EXTRACT_REGEXES) {
                val match = pattern.find(text) ?: continue
                val extracted = cleanMerchant(match.groupValues.getOrNull(1).orEmpty())
                if (extracted.isNotBlank()) return extracted
            }
        }
        extractTransitCardName(content)?.let { return it }
        extractTransitCardName(title)?.let { return it }

        val cleanTitle = cleanMerchant(title)
        if (cleanTitle.isNotBlank() &&
            !GENERIC_TITLES.any { cleanTitle.equals(it, ignoreCase = true) || cleanTitle.startsWith("短信 (") }
        ) {
            return cleanTitle
        }
        return ""
    }

    internal fun extractTransitCardName(text: String): String? {
        if (text.isBlank()) return null
        val cleaned = stripBalanceClauses(text)
        return TRANSIT_CARD_NAMES.firstOrNull { cleaned.contains(it, ignoreCase = true) }
    }

    fun inferAutoTags(
        sourcePackage: String,
        merchant: String,
        fullText: String,
        categoryName: String = "",
        currency: String = "CNY"
    ): String {
        val pkg = sourcePackage.trim().lowercase(Locale.ROOT)
        val text = "$merchant $fullText $categoryName".lowercase(Locale.ROOT)
        val cat = categoryName.trim()
        val cur = currency.trim().uppercase(Locale.ROOT)
        val tags = mutableListOf<String>()

        val hasTransitCue = TRANSIT_CARD_NAMES.any { text.contains(it.lowercase(Locale.ROOT)) } ||
            cat == "地铁公交" ||
            text.contains("地铁") ||
            text.contains("公交") ||
            text.contains("乘车码") ||
            text.contains("轨道交通") ||
            text.contains("出站扣费") ||
            text.contains("乘车扣费") ||
            pkg in STRICT_TRANSIT_PACKAGES ||
            (pkg in WALLET_TRANSIT_PACKAGES && (text.contains("刷卡") || text.contains("乘车") || text.contains("出站")))
        if (hasTransitCue) {
            tags.add("通勤")
        }

        if (pkg == "com.xunmeng.pinduoduo" || text.contains("拼多多")) {
            tags.add("拼多多")
        }
        if (pkg == "com.taobao.taobao" || pkg == "com.tmall.wireless" || text.contains("淘宝") || text.contains("天猫")) {
            tags.add("淘宝")
        }
        if (pkg == "com.jingdong.app.mall" || text.contains("京东")) {
            tags.add("京东")
        }

        val isMeituan = pkg == "com.sankuai.meituan.takeoutnew" ||
            pkg == "com.sankuai.meituan" ||
            text.contains("美团") ||
            text.contains("三快在线")
        if (isMeituan) {
            tags.add("美团")
        }

        if (cat in TRAVEL_TAG_CATEGORIES ||
            text.contains("机票") ||
            text.contains("酒店") ||
            text.contains("航空") ||
            text.contains("民宿")
        ) {
            tags.add("差旅")
        }
        if (cur.isNotBlank() && cur != CurrencyCatalog.DEFAULT_BASE_CURRENCY) {
            tags.add("跨境")
        }

        return TagTextCodec.format(tags)
    }

    private fun cleanMerchant(raw: String): String {
        var s = raw.trim()
        // 剥离开头常见介词、标点与前缀
        s = s.replace(Regex("^[：:，,。\\-—\\s向在给于]+"), "")
        s = s.replace(Regex("[：:，,。.;；\\s]+$"), "")
        // 截断后半句常见连接词及余额/扣费残余
        s = s.replace(
            Regex("\\s*(?:卡内余额|卡余额|卡上余额|剩余金额|剩余余额|当前余额|可用余额|账户余额|本次扣费|本次消费|本次扣款|出站扣费|乘车扣费|刷卡消费|刷卡扣费|刷卡成功|消费|付款|支付|扣款|扣费|实扣|车费|金额|人民币|USD|EUR|GBP|CNY|折合).*$"),
            ""
        ).trim()
        s = s.replace(Regex("^[：:，,。\\-—\\s向在给于]+"), "")
        s = s.replace(Regex("[：:，,。.;；\\s]+$"), "")
        return s.take(40)
    }

    private fun inferDominantType(lowerText: String): String {
        val expenseIdx = EXPENSE_VERBS.mapNotNull { verb ->
            lowerText.indexOf(verb).takeIf { it >= 0 }
        }.minOrNull() ?: Int.MAX_VALUE
        val incomeIdx = INCOME_VERBS.mapNotNull { verb ->
            lowerText.indexOf(verb).takeIf { it >= 0 }
        }.minOrNull() ?: Int.MAX_VALUE
        return if (incomeIdx < expenseIdx) TransactionType.INCOME else TransactionType.EXPENSE
    }

    private fun stripBalanceClauses(text: String): String {
        return text
            .replace(BALANCE_CLAUSE_REGEX, " ")
            .replace(CARD_LAST4_REGEX, " ")
    }

    private fun buildCombinedText(title: String, content: String): String {
        val t = title.trim()
        val c = content.trim()
        return when {
            t.isBlank() -> c
            c.isBlank() -> t
            c.contains(t) -> c
            else -> "$t $c"
        }
    }

    private fun buildCompactNote(title: String, content: String, merchant: String): String {
        val raw = content.trim().ifBlank { title.trim() }
        return if (raw.length <= 80) raw else raw.take(77) + "..."
    }

    private fun matchesSourcePackage(rulePkg: String, inputPkg: String): Boolean {
        val trimmed = rulePkg.trim()
        if (trimmed.isEmpty() || trimmed == "*") return true
        return trimmed.split(",").any { it.trim().equals(inputPkg.trim(), ignoreCase = true) }
    }

    private fun matchesChannel(ruleChannel: String, inputChannel: String): Boolean {
        val trimmed = ruleChannel.trim()
        if (trimmed.isEmpty() || trimmed.equals("ANY", ignoreCase = true)) return true
        return trimmed.equals(inputChannel.trim(), ignoreCase = true)
    }

    private fun compileRegex(pattern: String): Regex? =
        runCatching { Regex(pattern, setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)) }.getOrNull()

    private fun groupValueOrNull(match: MatchResult, groupIndex: Int): String? {
        if (groupIndex <= 0 || groupIndex >= match.groupValues.size) return null
        return match.groupValues[groupIndex].takeIf { it.isNotBlank() }
    }

    companion object {
        private val STRONG_NOISE_KEYWORDS = listOf(
            "验证码", "校验码", "动态码", "动态口令", "一次性密码", "安全码", "取件码", "提货码",
            "verification code", "security code", "one-time password", "passcode", " otp"
        )

        private val PROMO_NOISE_KEYWORDS = listOf(
            "回td", "td退订", "退订回t", "回复td", "回复t退订", "点击退订",
            "最高可借", "申请额度", "额度提升", "年化利率", "免息分期",
            "限时优惠", "专享礼遇", "立即领取", "立减金", "优惠券", "代金券",
            "邀您体验", "抽奖", "中奖通知"
        )

        private val SYSTEM_NOISE_KEYWORDS = listOf(
            "系统更新", "版本更新", "软件更新", "固件更新", "下载完成", "正在下载安装包",
            "system update", "software update", "download complete"
        )

        private val LOGIN_NOISE_KEYWORDS = listOf(
            "登录验证", "异地登录", "登录提醒", "已成功登录", "账号登录",
            "密码修改", "密码重置", "修改密码", "login alert", "new sign-in"
        )

        private val DELIVERY_NOISE_KEYWORDS = listOf(
            "快件", "包裹", "已签收", "派送中", "正在派送", "派件", "已揽收",
            "菜鸟驿站", "丰巢", "快递柜", "运单号", "快递单号", "物流更新", "已发货"
        )

        private val BILL_REMINDER_KEYWORDS = listOf(
            "本期应还", "最后还款日", "最低还款", "未出账单", "账单已出", "请及时还款", "请按时还款"
        )

        private val BILL_PAID_KEYWORDS = listOf(
            "已扣款", "还款成功", "自动还款成功", "扣款成功"
        )

        private val TRANSIT_NON_PAYMENT_NOISE_KEYWORDS = listOf(
            "进站成功", "已进站", "刷卡进站", "闸机已开启", "请刷卡出站",
            "余额不足", "请及时充值", "开卡成功", "nfc已开启", "nfc 已开启"
        )

        private val TRANSIT_CHARGE_KEYWORDS = listOf(
            "出站扣费", "乘车扣费", "刷卡消费", "刷卡扣费", "本次消费", "本次扣费", "本次扣款",
            "扣费", "扣款", "消费", "实扣", "车费", "支出", "付款", "支付"
        )

        private val PAYMENT_CONFIRM_KEYWORDS = listOf(
            "微信支付", "付款", "扣款", "消费", "支付成功", "实付", "入账", "到账", "收款",
            "you spent", "charged", "paid", "payment of"
        )

        private val EXPENSE_VERBS = listOf(
            "出站扣费", "乘车扣费", "刷卡消费", "刷卡扣费", "本次消费", "本次扣费", "本次扣款",
            "支出", "消费", "扣款", "付款", "支付", "转出", "代扣", "已扣", "刷卡", "扣费", "实扣", "车费",
            "spent", "paid", "charged", "payment", "purchase", "debited", "deducted"
        )

        private val INCOME_VERBS = listOf(
            "收入", "入账", "转入", "到账", "收款", "退款", "退回", "返现", "工资", "奖金",
            "分红", "报销", "存入", "received", "credited", "deposited", "refund", "cashback"
        )

        private val GENERIC_TITLES = setOf(
            "微信", "微信支付", "微信收款助手", "服务通知", "支付宝", "云闪付",
            "短信", "信息", "通知", "系统通知", "银行通知"
        )

        private val TRANSIT_CARD_NAMES = listOf(
            "京津冀互联互通卡", "重庆畅通卡", "上海交通卡", "深圳通互联互通卡",
            "广州羊城通", "羊城通", "岭南通", "深圳通", "长安通", "天府通",
            "金陵通", "江苏交通一卡通", "武汉通", "琴岛通", "燕赵通", "绿城通",
            "潇湘卡", "甬城通", "苏州市民卡", "合肥通", "洪城一卡通", "广西交通一卡通",
            "吉林通", "哈尔滨城市通", "八达通", "畅通卡", "交通一卡通", "交通联合卡",
            "公交卡", "交通卡", "地铁卡"
        )

        private val STRICT_TRANSIT_PACKAGES = setOf(
            "com.miui.tsmclient", "com.bmac.nfc", "com.sptcc.shanghai",
            "com.lingnan.pass", "com.cqrcb.cqpass", "com.octopuscards.nfc_reader"
        )

        private val WALLET_TRANSIT_PACKAGES = setOf(
            "com.mipay.wallet", "com.xiaomi.pass", "com.huawei.wallet",
            "com.hihonor.wallet", "com.finshell.wallet", "com.coloros.wallet",
            "com.vivo.wallet", "com.samsung.android.spay"
        )

        private val TRAVEL_TAG_CATEGORIES = setOf(
            "差旅住宿", "机票船票", "酒店民宿"
        )

        private val CARD_LAST4_REGEX = Regex(
            "(?:尾号|末四位|卡号[*xX]*|卡[（(]|ending\\s+in|card\\s*[*xX]*|\\([*]{2,})\\s*(\\d{4})\\b",
            RegexOption.IGNORE_CASE
        )

        private val BALANCE_CLAUSE_REGEX = Regex(
            "(?:当前余额|可用余额|账户余额|卡内余额|卡余额|卡上余额|剩余金额|剩余余额|余额|可用额度|剩余额度)(?:为|：|:|\\s)*(?:人民币|RMB|CNY|USD|EUR|GBP|[¥￥$€£])?\\s*[0-9]+(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?\\s*(?:元|美元|欧元|英镑)?",
            RegexOption.IGNORE_CASE
        )

        private val VERB_AMOUNT_REGEX = Regex(
            "(?:出站扣费|乘车扣费|刷卡消费|刷卡扣费|本次消费|本次扣费|本次扣款|刷卡|扣费|实扣|车费|消费|支出|扣款|付款|支付|转出|代扣|收入|入账|转入|到账|收款|退款|退回|报销|spent|paid|charged|payment\\s+of|received|credited|debited)(?:成功|完成)?\\s*" +
                "(?:为|：|:)?\\s*(?:金额(?:为|：|:)?|人民币|RMB)?\\s*" +
                "(USD|EUR|GBP|HKD|JPY|SGD|AUD|CAD|CHF|TWD|KRW|CNY|RMB|US\\$|HK\\$|NT\\$|S\\$|A\\$|C\\$|JP¥|\\$|€|£|¥|￥)?\\s*" +
                "([0-9]+(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?)(?![0-9.])(?!\\s*(?:年|月|日|号|时|分|秒|路|线|号线|次|站|-|/|:\\d))\\s*" +
                "(元|美元|美金|欧元|英镑|港币|港元|日元|新元|新加坡元|澳元|加元|瑞郎|新台币|韩元|USD|EUR|GBP|HKD|JPY|SGD|CNY|RMB)?",
            RegexOption.IGNORE_CASE
        )

        private val CURRENCY_AMOUNT_REGEX = Regex(
            "(USD|EUR|GBP|HKD|JPY|SGD|AUD|CAD|CHF|TWD|KRW|CNY|RMB|US\\$|HK\\$|NT\\$|S\\$|A\\$|C\\$|JP¥|\\$|€|£|¥|￥)?\\s*" +
                "([0-9]+(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?)\\s*" +
                "(元|美元|美金|欧元|英镑|港币|港元|日元|新元|新加坡元|澳元|加元|瑞郎|新台币|韩元|USD|EUR|GBP|HKD|JPY|SGD|CNY|RMB)?",
            RegexOption.IGNORE_CASE
        )

        private val DECIMAL_AMOUNT_REGEX = Regex("\\b[0-9]+(?:,[0-9]{3})*\\.[0-9]{1,2}\\b")

        private val MERCHANT_EXTRACT_REGEXES = listOf(
            Regex("(?:向|给)\\s*([^\\s,，。;；()（）]{1,32}?)\\s*(?:付款|支付|转账)"),
            Regex("(?:商户|交易对方|收款方|商家|对方户名)[:：]\\s*([^\\n,，。;；]{1,36})"),
            Regex("(?:在)\\s*([^\\s,，。;；]{1,32}?)\\s*(?:消费|刷卡|购买|下单)"),
            Regex("(?:at|to|from)\\s+([A-Za-z0-9 &._/'-]{2,40}?)(?:\\s+on\\b|\\s+using\\b|\\s+with\\b|[.,;\\n]|$)", RegexOption.IGNORE_CASE)
        )

        private val GENERIC_PLATFORM_SHELL_MERCHANTS = setOf(
            "淘宝", "淘宝网", "淘宝闪购", "天猫", "天猫超市", "京东", "京东商城", "京东自营",
            "京东到家", "京东小时达", "京东超市", "拼多多", "多多买菜",
            "美团", "美团外卖", "美团买菜", "美团优选", "大众点评", "饿了么",
            "抖音", "抖音商城", "抖音支付", "小红书", "amazon", "亚马逊", "唯品会", "得物",
            "微信", "微信支付", "微信收款助手", "支付宝", "财付通", "银联", "云闪付", "网银在线",
            "盒马", "盒马鲜生", "叮咚买菜", "山姆", "山姆会员商店", "costco", "朴朴", "小象超市",
            "超市", "便利店", "百货"
        )

        private val GENERIC_MERCHANT_PREFIXES = listOf(
            "平台商户", "个体户", "商户_"
        )

        private val GENERIC_MERCHANT_SUFFIXES = listOf(
            "平台商户", "技术有限公司", "信息技术有限公司", "网络有限公司",
            "科技有限公司", "科技有限责任公司", "电子商务有限公司", "超市", "便利店"
        )

        private val GENERIC_PLATFORM_SHELL_SUBSTRINGS = listOf(
            "平台商户", "信息技术", "技术有限公司", "淘宝闪购", "京东自营"
        )

        private val PLATFORM_STORE_SUFFIX_REGEX = Regex(
            "(?:淘宝|天猫|京东|拼多多|抖音|小红书).*(?:旗舰店|专卖店|专营店|官方店|自营|店铺|小店)$"
        )

        private val TAKEOUT_CANDIDATE_CATEGORIES = listOf("外卖", "餐饮美食", "餐饮")

        private val DINING_CATEGORY_NAMES = setOf(
            "早餐", "三餐外食", "咖啡茶饮", "零食甜品", "聚餐请客", "餐饮美食", "餐饮"
        )

        private val TAKEOUT_OVERRIDE_KEYWORDS = listOf(
            "美团外卖", "饿了么", "外卖", "宅急送", "uber eats", "doordash"
        )

        private val EXPENSE_CATEGORY_KEYWORDS: List<Pair<List<String>, List<String>>> = listOf(
            // 1. 具体商品/垂直领域优先于泛电商平台（如 淘宝买猫粮 -> 宠物食品，Amazon 买书 -> 书籍资料/日用购物）
            listOf(
                "猫粮", "狗粮", "猫条", "猫罐头", "冻干", "宠物食品", "宠物零食", "幼猫粮", "成猫粮"
            ) to listOf("宠物食品", "宠物生活", "日用购物"),
            listOf(
                "宠物医院", "宠物医疗", "宠物疫苗", "驱虫", "绝育", "宠物看病", "宠物体检"
            ) to listOf("宠物医疗", "宠物生活", "医疗健康"),
            listOf(
                "猫砂", "猫窝", "猫抓板", "牵引绳", "狗窝", "宠物用品", "宠物洗护", "宠物美容", "宠物"
            ) to listOf("宠物用品", "宠物生活", "日用购物"),

            listOf(
                "买书", "买本书", "一本书", "书籍", "图书", "书店", "新华书店", "当当",
                "教材", "教辅", "课本", "绘本", "电子书", "kindle", "books", "bookstore",
                "读物", "杂志", "期刊", "参考书", "工具书", "编程书", "专业书", "二手书"
            ) to listOf("书籍资料", "学习成长", "日用购物"),
            listOf(
                "课程", "网课", "培训", "得到", "极客时间", "慕课", "coursera", "udemy", "学费", "辅导班", "驾校"
            ) to listOf("课程培训", "学习成长", "日用购物"),
            listOf(
                "考试", "报名费", "考证", "四六级", "雅思", "托福", "考研", "软考", "公考"
            ) to listOf("考试考证", "学习成长", "其他支出"),
            listOf(
                "文具", "笔记本", "中性笔", "钢笔", "晨光", "得力", "a4纸", "打印", "复印", "办公用品"
            ) to listOf("文具办公", "学习成长", "日用购物"),

            listOf(
                "iphone", "ipad", "macbook", "手机", "电脑", "笔记本电脑", "平板",
                "小米手机", "红米", "redmi", "华为手机", "thinkpad", "显卡", "内存条", "固态硬盘", "ssd", "显示器"
            ) to listOf("手机电脑", "数码家电", "日用购物"),
            listOf(
                "耳机", "airpods", "充电器", "充电宝", "数据线", "键盘", "鼠标",
                "手机壳", "钢化膜", "路由器", "u盘", "移动硬盘", "数码"
            ) to listOf("数码配件", "数码家电", "日用购物"),
            listOf(
                "空调", "冰箱", "洗衣机", "电视", "微波炉", "电饭煲", "吹风机",
                "扫地机器人", "吸尘器", "空气净化器", "加湿器", "家电", "美的电器", "海尔", "格力", "戴森"
            ) to listOf("家用电器", "数码家电", "日用购物"),

            listOf(
                "优衣库", "uniqlo", "zara", "nike", "耐克", "adidas", "阿迪达斯", "李宁", "安踏", "lululemon",
                "羽绒服", "外套", "卫衣", "毛衣", "衬衫", "t恤", "裤子", "牛仔裤", "裙子", "连衣裙",
                "运动鞋", "跑鞋", "皮鞋", "拖鞋", "袜子", "内衣", "帽子", "双肩包", "手提包",
                "衣服", "服饰", "鞋包", "男装", "女装"
            ) to listOf("服饰鞋包", "服饰美容", "日用购物"),
            listOf(
                "口红", "粉底", "面霜", "精华", "防晒", "卸妆", "洗面奶", "洁面", "面膜",
                "水乳", "眼霜", "香水", "护肤", "美妆", "化妆品", "丝芙兰", "sephora", "兰蔻", "雅诗兰黛", "欧莱雅"
            ) to listOf("美妆护肤", "服饰美容", "日用购物"),
            listOf(
                "理发", "剪发", "烫发", "染发", "美发", "理发店", "美甲", "美睫", "美容院", "spa"
            ) to listOf("理发美甲", "服饰美容", "日用购物"),
            listOf(
                "项链", "耳环", "戒指", "手链", "手表", "墨镜", "太阳镜", "眼镜", "配饰", "首饰", "周大福", "老凤祥"
            ) to listOf("配饰首饰", "服饰美容", "日用购物"),

            listOf(
                "奶粉", "辅食", "纸尿裤", "尿不湿", "奶瓶", "婴儿", "宝宝", "母婴"
            ) to listOf("奶粉辅食", "亲子育儿", "日用购物"),
            listOf(
                "童装", "童鞋", "玩具", "乐高", "lego", "泡泡玛特", "popmart", "积木"
            ) to listOf("童装玩具", "亲子育儿", "日用购物"),
            listOf(
                "早教", "托育", "幼儿园", "少儿", "亲子"
            ) to listOf("早教培训", "亲子育儿", "学习成长"),

            listOf(
                "美团买药", "叮当快药", "药店", "药房", "大药房", "买药", "药物", "药品",
                "保健品", "维生素", "益生菌", "感冒", "消炎药", "创可贴", "口罩"
            ) to listOf("买药保健", "医疗健康", "日用购物"),
            listOf(
                "医院", "门诊", "挂号", "诊所", "急诊", "住院", "就医", "医疗"
            ) to listOf("看病挂号", "医疗健康", "其他支出"),
            listOf(
                "体检", "牙科", "口腔", "洗牙", "拔牙", "补牙", "眼科", "配镜"
            ) to listOf("体检牙科", "医疗健康", "其他支出"),
            listOf(
                "医保", "医疗险", "百万医疗", "惠民保"
            ) to listOf("医疗保险", "医疗健康", "金融保险"),

            listOf(
                "房租", "月租", "租房", "房贷", "按揭", "住房贷款"
            ) to listOf("房租房贷", "居家物业", "其他支出"),
            listOf(
                "水费", "电费", "燃气费", "燃气", "天然气", "煤气", "水电费", "国网", "国家电网", "南方电网", "自来水"
            ) to listOf("水电燃气", "居家物业", "其他支出"),
            listOf(
                "话费", "手机充值", "流量包", "中国移动", "中国联通", "中国电信", "宽带", "网费"
            ) to listOf("话费宽带", "居家物业", "数字订阅"),
            listOf(
                "物业费", "物业", "维修费", "修水管", "开锁", "五金", "家装", "装修"
            ) to listOf("物业维修", "居家物业", "日用购物"),
            listOf(
                "家政", "保洁", "天鹅到家"
            ) to listOf("家政服务", "居家物业", "日用购物"),

            listOf(
                "手续费", "汇损", "货币转换费", "服务费", "转账费"
            ) to listOf("汇损手续费", "金融保险", "其他支出"),
            listOf(
                "商业保险", "车险", "意外险", "重疾险", "寿险", "平安保险", "中国人寿", "保险"
            ) to listOf("商业保险", "金融保险", "其他支出"),
            listOf(
                "滞纳金", "违约金", "罚息", "分期利息", "利息"
            ) to listOf("利息罚息", "金融保险", "其他支出"),

            listOf(
                "微信红包", "qq红包", "发红包", "份子钱", "随礼", "礼金", "红包礼金"
            ) to listOf("红包礼金", "人情社交", "其他支出"),
            listOf(
                "送礼", "礼品", "礼物", "花束", "鲜花", "请客"
            ) to listOf("送礼请客", "人情社交", "其他支出"),
            listOf(
                "孝敬", "给妈妈", "给爸爸"
            ) to listOf("孝敬长辈", "人情社交", "其他支出"),
            listOf(
                "约会", "纪念日", "情人节"
            ) to listOf("恋爱约会", "人情社交", "其他支出"),

            listOf(
                "机票", "航空", "国航", "东航", "南航", "海航", "春秋航空", "吉祥航空",
                "登机牌", "船票", "flight", "airline"
            ) to listOf("机票船票", "差旅住宿", "交通出行"),
            listOf(
                "酒店", "宾馆", "民宿", "客栈", "全季", "亚朵", "汉庭", "如家",
                "万豪", "希尔顿", "洲际", "airbnb", "booking", "agoda", "hotel"
            ) to listOf("酒店民宿", "差旅住宿", "其他支出"),
            listOf(
                "携程", "去哪儿", "飞猪", "同程", "马蜂窝", "景区", "门票", "迪士尼", "环球影城", "乐园", "博物馆"
            ) to listOf("旅游门票", "差旅住宿", "休闲娱乐"),

            listOf(
                "openai", "chatgpt", "anthropic", "claude", "gemini", "copilot", "cursor", "midjourney",
                "hetzner", "aws", "cloudflare", "vultr", "digitalocean", "linode", "vercel",
                "阿里云", "腾讯云", "百度网盘", "夸克网盘", "icloud", "onedrive"
            ) to listOf("AI与云服务", "数字订阅", "订阅"),
            listOf(
                "spotify", "netflix", "youtube", "disney+", "bilibili", "b站", "大会员",
                "爱奇艺", "腾讯视频", "优酷", "芒果tv", "网易云", "qq音乐", "酷狗", "apple music"
            ) to listOf("影音音乐", "数字订阅", "休闲娱乐"),
            listOf(
                "steam", "playstation", "psn", "xbox", "nintendo", "switch", "原神", "王者荣耀",
                "和平精英", "米哈游", "点卡", "游戏充值"
            ) to listOf("游戏充值", "数字订阅", "休闲娱乐"),
            listOf(
                "apple", "app store", "google play", "github", "adobe", "microsoft",
                "office", "wps", "notion", "1password", "会员", "订阅", "subscription"
            ) to listOf("软件会员", "数字订阅", "订阅"),

            listOf(
                "电影", "影城", "影院", "猫眼", "淘票票", "万达影城", "imax", "演唱会", "音乐会", "话剧", "livehouse", "大麦"
            ) to listOf("电影演出", "休闲娱乐", "娱乐"),
            listOf(
                "健身", "瑜伽", "普拉提", "游泳", "羽毛球", "网球", "篮球", "台球", "攀岩", "keep", "乐刻"
            ) to listOf("运动健身", "休闲娱乐", "娱乐"),
            listOf(
                "露营", "徒步", "滑雪", "温泉", "钓鱼"
            ) to listOf("户外露营", "休闲娱乐", "娱乐"),
            listOf(
                "剧本杀", "密室", "ktv", "电玩", "网吧", "桌游", "轰趴", "足浴", "按摩", "洗浴"
            ) to listOf("聚会玩乐", "休闲娱乐", "娱乐"),

            // 交通出行二级
            listOf(
                "重庆畅通卡", "畅通卡", "羊城通", "岭南通", "深圳通", "上海交通卡", "京津冀",
                "长安通", "天府通", "金陵通", "一卡通", "交通卡", "公交卡", "地铁卡",
                "地铁", "公交", "乘车码", "轨道交通", "轻轨", "metro", "transit", "subway", "bus", "车费"
            ) to listOf("地铁公交", "交通出行", "交通"),
            listOf(
                "滴滴", "didi", "高德打车", "曹操出行", "t3出行", "享道", "花小猪",
                "打车", "出租车", "网约车", "uber", "grab", "lyft", "taxi"
            ) to listOf("网约打车", "交通出行", "交通"),
            listOf(
                "哈啰", "青桔", "美团单车", "摩拜", "单车", "骑车", "共享单车"
            ) to listOf("共享单车", "交通出行", "交通"),
            listOf(
                "12306", "铁路", "高铁", "动车", "火车", "train"
            ) to listOf("高铁火车", "交通出行", "交通"),
            listOf(
                "加油", "中石化", "中石油", "壳牌", "汽油", "柴油", "充电桩", "特来电", "星星充电"
            ) to listOf("加油充电", "交通出行", "交通"),
            listOf(
                "停车", "停车场", "高速", "过路费", "etc", "parking"
            ) to listOf("停车过路", "交通出行", "交通"),
            listOf(
                "洗车", "保养", "汽修", "4s店", "换胎", "途虎"
            ) to listOf("养车维修", "交通出行", "交通"),

            // 餐饮美食二级（外卖优先于普通餐饮）
            listOf(
                "美团外卖", "饿了么", "外卖", "点餐", "uber eats", "doordash"
            ) to listOf("外卖", "餐饮美食", "餐饮"),
            listOf(
                "星巴克", "starbucks", "瑞幸", "luckin", "库迪", "cotti", "manner", "m stand",
                "喜茶", "奈雪", "霸王茶姬", "蜜雪冰城", "茶百道", "古茗", "沪上阿姨", "一点点",
                "书亦烧仙草", "coco", "costa", "peet", "咖啡", "奶茶", "果茶", "柠檬茶", "cafe"
            ) to listOf("咖啡茶饮", "餐饮美食", "餐饮"),
            listOf(
                "零食", "薯片", "辣条", "坚果", "饼干", "巧克力", "糖果", "蛋糕",
                "面包", "烘焙", "糕点", "甜品", "冰淇淋", "好利来", "鲍师傅",
                "三只松鼠", "良品铺子", "来伊份", "绝味", "周黑鸭"
            ) to listOf("零食甜品", "餐饮美食", "餐饮"),
            listOf(
                "盒马", "叮咚买菜", "山姆", "costco", "朴朴", "小象超市",
                "水果", "车厘子", "草莓", "西瓜", "芒果", "榴莲", "海鲜", "生鲜"
            ) to listOf("水果生鲜", "餐饮美食", "餐饮"),
            listOf(
                "火锅", "海底捞", "巴奴", "烧烤", "烤肉", "烤鱼", "小龙虾", "酒吧", "清吧", "精酿", "聚餐", "西贝"
            ) to listOf("聚餐请客", "餐饮美食", "餐饮"),
            listOf(
                "香烟", "卷烟", "茅台", "五粮液", "白酒", "红酒", "啤酒", "茶叶", "普洱", "龙井"
            ) to listOf("烟酒茶", "餐饮美食", "餐饮"),
            listOf(
                "胡辣汤", "豆腐脑", "豆花", "油条", "豆浆", "早点", "早餐", "早饭", "早餐店",
                "小笼包", "小笼", "水煎包", "生煎", "锅贴", "灌汤包", "汤包", "包子", "馒头",
                "花卷", "发糕", "油茶", "煎饼", "煎饼果子", "手抓饼", "鸡蛋灌饼", "灌饼", "杂粮煎饼",
                "肉夹馍", "烧饼", "肠粉", "粥铺", "米粥", "白粥", "皮蛋瘦肉粥", "八宝粥",
                "糯米饭", "糯米鸡", "饭团", "粢饭团", "热干面", "馄饨", "云吞", "抄手",
                "巴比", "永和大王", "庆丰", "嘉和一品", "吉祥馄饨", "袁记云饺"
            ) to listOf("早餐", "三餐外食", "餐饮美食", "餐饮"),
            listOf(
                "肯德基", "kfc", "麦当劳", "mcdonald", "汉堡王", "必胜客", "萨莉亚", "塔斯汀",
                "老乡鸡", "乡村基", "大米先生", "米村拌饭", "麻辣烫", "杨国福", "张亮",
                "沙县", "兰州拉面", "黄焖鸡", "牛肉面", "烩面", "板面", "刀削面", "炸酱面",
                "螺蛳粉", "米粉", "凉皮", "牛肉汤", "羊肉汤", "粉丝汤", "鸭血粉丝", "瓦罐汤",
                "砂锅", "盖浇饭", "盖饭", "煲仔饭", "猪脚饭", "木桶饭", "烤冷面", "炸串",
                "卤味", "熟食", "冒菜", "串串", "钵钵鸡", "锅盔", "鸡排",
                "面庄", "粉馆", "汤馆", "小吃", "美食", "小馆", "小厨", "食府", "茶餐厅", "大排档",
                "真功夫", "和府捞面", "遇见小面", "华莱士", "德克士",
                "餐饮", "饭店", "餐厅", "食堂", "面馆", "拉面", "米线", "酸辣粉", "嗨客多",
                "饺子", "水饺", "小面", "牛肉粉", "羊肉粉",
                "炸鸡", "汉堡", "披萨", "快餐", "便当", "午饭", "晚饭", "夜宵",
                "午餐", "晚餐", "吃饭", "restaurant", "sainsbury"
            ) to listOf("三餐外食", "餐饮美食", "餐饮"),
            listOf(
                "买菜", "菜市场", "蔬菜", "猪肉", "牛肉", "鸡肉", "鸡蛋", "大米", "食用油", "调味品"
            ) to listOf("买菜做饭", "餐饮美食", "餐饮"),
            listOf(
                "美团", "大众点评"
            ) to listOf("三餐外食", "餐饮美食", "餐饮"),

            // 日用购物二级（家居/超市/快递优先，泛电商平台兜底）
            listOf(
                "顺丰", "中通", "圆通", "申通", "韵达", "极兔", "京东快递", "菜鸟裹裹", "寄快递", "快递费", "邮费"
            ) to listOf("快递邮寄", "日用购物", "购物"),
            listOf(
                "四件套", "床单", "被套", "被子", "枕头", "家纺", "南极人", "水星家纺", "富安娜", "洁丽雅",
                "毛巾", "浴巾", "纸巾", "抽纸", "卷纸", "卫生纸", "洗洁精", "洗衣液", "沐浴露", "洗发水",
                "牙膏", "牙刷", "垃圾袋", "收纳", "衣架", "宜家", "ikea", "家居"
            ) to listOf("家居日用", "日用购物", "购物"),
            listOf(
                "便利店", "便利蜂", "7-11", "711", "7-eleven", "全家", "罗森", "lawson",
                "美宜佳", "屈臣氏", "名创优品", "miniso", "永辉", "大润发", "沃尔玛", "walmart",
                "家乐福", "物美", "超市"
            ) to listOf("超市便利", "日用购物", "购物"),
            listOf(
                "淘宝", "天猫", "京东", "拼多多", "amazon", "亚马逊", "唯品会", "得物",
                "小红书", "抖音商城", "百货", "网购", "购物"
            ) to listOf("网购百货", "日用购物", "购物"),

            listOf(
                "公益", "捐款", "捐赠", "水滴筹", "腾讯公益", "蚂蚁森林"
            ) to listOf("公益捐赠", "其他支出")
        )

        private val INCOME_CATEGORY_KEYWORDS: List<Pair<List<String>, List<String>>> = listOf(
            listOf("购物退款", "退货", "退款", "退回", "refund") to listOf("购物退款", "报销退款", "退款"),
            listOf("报销", "reimburse") to listOf("公司报销", "报销退款", "退款"),
            listOf("理赔", "赔付") to listOf("保险理赔", "报销退款", "退款"),
            listOf("公积金") to listOf("公积金提取", "政策补贴", "其他收入"),
            listOf("退税", "个税") to listOf("个税退税", "政策补贴", "其他收入"),
            listOf("政府补贴", "国补", "消费补贴", "以旧换新") to listOf("消费补贴", "政策补贴", "其他收入"),
            listOf("年终奖", "十三薪", "13薪") to listOf("年终奖", "工资薪金", "工资"),
            listOf("加班费", "加班津贴", "夜班费") to listOf("加班津贴", "工资薪金", "工资"),
            listOf("基本工资", "底薪") to listOf("基本工资", "工资薪金", "工资"),
            listOf("工资", "薪资", "发薪", "代发", "salary", "payroll") to listOf("工资薪金", "基本工资", "工资"),
            listOf("餐补", "饭补", "交通补", "话补", "房补", "高温补贴") to listOf("餐补交通补", "奖金补贴", "奖金"),
            listOf("节日福利", "过节费", "福利") to listOf("节日福利", "奖金补贴", "奖金"),
            listOf("绩效", "奖金", "bonus") to listOf("绩效奖金", "奖金补贴", "奖金"),
            listOf("补贴", "津贴") to listOf("奖金补贴", "绩效奖金", "奖金"),
            listOf("利息", "结息", "interest") to listOf("银行利息", "投资理财", "理财"),
            listOf("分红", "股息", "派息", "dividend") to listOf("分红收益", "投资理财", "理财"),
            listOf("外汇", "结汇", "汇兑收益") to listOf("外汇收益", "投资理财", "理财"),
            listOf("理财", "基金", "股票", "收益", "余额宝", "余利宝", "零钱通") to listOf("基金股票", "投资理财", "理财"),
            listOf("闲鱼", "转转", "二手", "闲置") to listOf("二手闲置", "兼职副业", "兼职"),
            listOf("稿费", "打赏", "赞赏", "创作收益", "流量主") to listOf("自媒体稿费", "兼职副业", "兼职"),
            listOf("兼职", "副业", "劳务", "外包", "私活") to listOf("外包私活", "兼职副业", "兼职"),
            listOf("口令红包", "微信红包", "qq红包", "红包") to listOf("微信红包", "人情礼金", "奖金补贴"),
            listOf("礼金", "压岁钱", "随礼") to listOf("礼金往来", "人情礼金", "其他收入"),
            listOf("收租", "房租收入", "租金收入") to listOf("租金收入", "经营所得", "其他收入"),
            listOf("收款", "扫码收款", "收钱码", "营收", "营业额") to listOf("门店营收", "经营所得", "其他收入")
        )
    }
}

package com.kairoledger.app.capture

import com.kairoledger.app.core.contract.CaptureInput
import com.kairoledger.app.core.contract.ParsedBillDraft
import com.kairoledger.app.core.model.CurrencyCatalog
import com.kairoledger.app.core.model.MoneyAmount
import com.kairoledger.app.data.db.AccountAliasEntity
import com.kairoledger.app.data.db.AccountEntity
import com.kairoledger.app.data.db.AccountGroup
import com.kairoledger.app.data.db.AliasMatchType
import com.kairoledger.app.data.db.CategoryEntity
import com.kairoledger.app.data.db.ParseRuleEntity
import com.kairoledger.app.data.db.TransactionType
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

                return ParsedBillDraft(
                    type = rule.transactionType,
                    origAmountMicros = amountMicros,
                    origCurrency = currency,
                    accountId = accountId,
                    accountHint = accountHint,
                    categoryId = category?.id,
                    categoryName = category?.name ?: rule.defaultCategoryName,
                    merchant = cleanedMerchant,
                    note = buildCompactNote(input.title, input.content, cleanedMerchant),
                    timestamp = input.timestamp,
                    confidence = 0.94f,
                    parserUsed = "LOCAL_RULE:${rule.name}"
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

        return ParsedBillDraft(
            type = type,
            origAmountMicros = amountMicros,
            origCurrency = currency,
            accountId = accountId,
            accountHint = accountHint,
            categoryId = category?.id,
            categoryName = category?.name ?: defaultCat,
            merchant = merchant,
            note = buildCompactNote(input.title, input.content, merchant),
            timestamp = input.timestamp,
            confidence = 0.78f,
            parserUsed = "LOCAL_HEURISTIC"
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

        val searchHaystack = "$merchant $fullText".lowercase(Locale.ROOT)
        val mappingRules = if (type == TransactionType.INCOME) INCOME_CATEGORY_KEYWORDS else EXPENSE_CATEGORY_KEYWORDS

        for ((keywords, candidateCategoryNames) in mappingRules) {
            if (keywords.any { searchHaystack.contains(it) }) {
                findBestCategoryMatch(candidateCategoryNames, typedCategories)?.let { return it }
            }
        }

        if (ruleDefaultCategoryName.isNotBlank()) {
            findBestCategoryMatch(listOf(ruleDefaultCategoryName), typedCategories)?.let { return it }
        }

        val fallbackName = if (type == TransactionType.INCOME) "其他收入" else "其他支出"
        return findBestCategoryMatch(listOf(fallbackName), typedCategories) ?: typedCategories.firstOrNull()
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
        val cleanTitle = cleanMerchant(title)
        if (cleanTitle.isNotBlank() &&
            !GENERIC_TITLES.any { cleanTitle.equals(it, ignoreCase = true) || cleanTitle.startsWith("短信 (") }
        ) {
            return cleanTitle
        }
        return ""
    }

    private fun cleanMerchant(raw: String): String {
        var s = raw.trim()
        // 剥离开头常见介词、标点与前缀
        s = s.replace(Regex("^[：:，,。\\-—\\s向在给于]+"), "")
        s = s.replace(Regex("[：:，,。.;；\\s]+$"), "")
        // 截断后半句常见连接词
        s = s.replace(Regex("\\s*(?:消费|付款|支付|扣款|金额|人民币|USD|EUR|GBP|CNY|折合).*$"), "").trim()
        s = s.replace(Regex("^[：:，,。\\-—\\s向在给于]+"), "")
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

        private val PAYMENT_CONFIRM_KEYWORDS = listOf(
            "微信支付", "付款", "扣款", "消费", "支付成功", "实付", "入账", "到账", "收款",
            "you spent", "charged", "paid", "payment of"
        )

        private val EXPENSE_VERBS = listOf(
            "支出", "消费", "扣款", "付款", "支付", "转出", "代扣", "已扣", "刷卡", "扣费",
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

        private val CARD_LAST4_REGEX = Regex(
            "(?:尾号|末四位|卡号[*xX]*|卡[（(]|ending\\s+in|card\\s*[*xX]*|\\([*]{2,})\\s*(\\d{4})\\b",
            RegexOption.IGNORE_CASE
        )

        private val BALANCE_CLAUSE_REGEX = Regex(
            "(?:当前余额|可用余额|账户余额|余额|可用额度|剩余额度)[:：]?\\s*(?:人民币|RMB|CNY|USD|EUR|GBP|[¥￥$€£])?\\s*[0-9]+(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?\\s*(?:元|美元|欧元|英镑)?",
            RegexOption.IGNORE_CASE
        )

        private val VERB_AMOUNT_REGEX = Regex(
            "(?:消费|支出|扣款|付款|支付|转出|代扣|收入|入账|转入|到账|收款|退款|退回|报销|spent|paid|charged|payment\\s+of|received|credited|debited)\\s*" +
                "(?:金额为?|人民币|RMB)?\\s*" +
                "(USD|EUR|GBP|HKD|JPY|SGD|AUD|CAD|CHF|TWD|KRW|CNY|RMB|US\\$|HK\\$|NT\\$|S\\$|A\\$|C\\$|JP¥|\\$|€|£|¥|￥)?\\s*" +
                "([0-9]+(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?)\\s*" +
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

        private val EXPENSE_CATEGORY_KEYWORDS: List<Pair<List<String>, List<String>>> = listOf(
            listOf(
                "星巴克", "starbucks", "瑞幸", "luckin", "库迪", "cotti", "manner",
                "喜茶", "奈雪", "霸王茶姬", "蜜雪冰城", "茶百道", "古茗", "costa", "peet",
                "美团", "饿了么", "肯德基", "kfc", "麦当劳", "mcdonald", "必胜客", "海底捞",
                "西贝", "盒马", "叮咚买菜", "山姆", "costco", "餐饮", "饭店", "餐厅",
                "食堂", "外卖", "奶茶", "咖啡", "火锅", "烧烤", "烤肉", "面馆", "拉面",
                "米线", "酸辣粉", "嗨客多", "饺子", "包子", "麻辣烫", "炸鸡", "汉堡",
                "披萨", "面包", "烘焙", "糕点", "甜品", "烤鱼", "快餐", "便当",
                "早餐", "午餐", "晚餐", "生鲜", "cafe", "restaurant", "uber eats", "doordash"
            ) to listOf("餐饮美食", "餐饮", "三餐外食", "咖啡茶饮", "生鲜买菜"),

            listOf(
                "滴滴", "didi", "地铁", "公交", "乘车码", "羊城通", "一卡通", "高德打车",
                "曹操出行", "t3出行", "uber", "grab", "lyft", "12306", "铁路", "高铁",
                "火车", "打车", "出租车", "加油", "中石化", "中石油", "停车", "高速", "etc",
                "metro", "transit", "subway"
            ) to listOf("交通出行", "交通", "地铁公交", "网约打车"),

            listOf(
                "openai", "chatgpt", "anthropic", "claude", "apple", "icloud", "app store",
                "google play", "hetzner", "aws", "cloudflare", "vultr", "digitalocean",
                "linode", "github", "copilot", "cursor", "vercel", "spotify", "netflix",
                "youtube", "adobe", "microsoft", "notion", "1password", "steam",
                "bilibili", "爱奇艺", "腾讯视频", "网易云", "qq音乐", "阿里云", "腾讯云",
                "会员", "订阅", "subscription"
            ) to listOf("数字订阅", "AI与云服务", "软件会员", "订阅"),

            listOf(
                "淘宝", "天猫", "京东", "拼多多", "amazon", "亚马逊", "唯品会", "得物",
                "小红书", "抖音商城", "便利店", "7-11", "全家", "罗森", "屈臣氏",
                "名创优品", "百货", "超市", "购物"
            ) to listOf("日用购物", "购物", "网购百货"),

            listOf(
                "房租", "物业", "电费", "水费", "燃气", "国网", "南方电网",
                "话费", "中国移动", "中国联通", "中国电信", "宽带"
            ) to listOf("居家物业", "房租水电", "话费宽带", "居家"),

            listOf(
                "携程", "去哪儿", "飞猪", "同程", "booking", "airbnb", "agoda",
                "酒店", "宾馆", "机票", "航空", "国航", "东航", "南航"
            ) to listOf("差旅住宿", "差旅"),

            listOf(
                "医院", "药店", "药房", "美团买药", "叮当快药", "挂号", "诊所", "体检", "牙科"
            ) to listOf("医疗健康", "医疗"),

            listOf(
                "电影", "猫眼", "淘票票", "万达影城", "剧本杀", "密室", "ktv", "健身", "门票"
            ) to listOf("休闲娱乐", "娱乐")
        )

        private val INCOME_CATEGORY_KEYWORDS: List<Pair<List<String>, List<String>>> = listOf(
            listOf("工资", "薪资", "发薪", "代发", "salary", "payroll") to listOf("工资薪金", "工资"),
            listOf("奖金", "补贴", "津贴", "绩效", "红包", "bonus") to listOf("奖金补贴", "奖金"),
            listOf("理财", "基金", "分红", "利息", "收益", "股息", "dividend", "interest") to listOf("投资理财", "理财"),
            listOf("退款", "退回", "报销", "赔付", "refund", "reimburse") to listOf("报销退款", "退款"),
            listOf("兼职", "副业", "稿费", "劳务") to listOf("兼职副业", "兼职")
        )
    }
}

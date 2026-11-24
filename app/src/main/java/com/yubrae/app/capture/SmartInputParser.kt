package com.yubrae.app.capture

import com.yubrae.app.core.contract.CaptureInput
import com.yubrae.app.core.contract.ParsedBillDraft
import com.yubrae.app.core.model.CurrencyCatalog
import com.yubrae.app.core.model.MoneyAmount
import com.yubrae.app.data.db.AccountAliasEntity
import com.yubrae.app.data.db.AccountEntity
import com.yubrae.app.data.db.AccountGroup
import com.yubrae.app.data.db.CaptureChannel
import com.yubrae.app.data.db.CategoryEntity
import com.yubrae.app.data.db.TransactionType
import com.yubrae.app.data.prefs.AppSettings
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/**
 * 一句话语音/自然语言多笔智能记账解析器：
 * - 纯 JVM 可测入口：[parseLocalBatch]；
 * - 优先走大模型拆单、失败/未启用自动回退本地规则的入口：[parseSmartInput]。
 */
class SmartInputParser(
    private val localRuleParser: LocalRuleParser = LocalRuleParser(),
    private val llmBillParser: LlmBillParser? = null,
    private val zoneId: ZoneId = ZoneId.systemDefault()
) {

    suspend fun parseSmartInput(
        text: String,
        settings: AppSettings,
        accounts: List<AccountEntity>,
        categories: List<CategoryEntity>,
        aliases: List<AccountAliasEntity> = emptyList(),
        nowMillis: Long = System.currentTimeMillis()
    ): List<ParsedBillDraft> {
        val cleanText = text.trim()
        if (cleanText.isEmpty()) return emptyList()

        if (settings.llmEnabled && llmBillParser != null && hasUsableLlmConfig(settings)) {
            val aiResult = llmBillParser.parseNaturalLanguageBatch(
                text = cleanText,
                settings = settings,
                accounts = accounts,
                categories = categories,
                nowMillis = nowMillis
            )
            val aiDrafts = aiResult.getOrNull()
            if (!aiDrafts.isNullOrEmpty()) {
                return aiDrafts
            }
        }

        return parseLocalBatch(
            text = cleanText,
            accounts = accounts,
            categories = categories,
            aliases = aliases,
            nowMillis = nowMillis
        )
    }

    fun parseLocalBatch(
        text: String,
        accounts: List<AccountEntity>,
        categories: List<CategoryEntity>,
        aliases: List<AccountAliasEntity> = emptyList(),
        nowMillis: Long = System.currentTimeMillis()
    ): List<ParsedBillDraft> {
        val cleanText = text.trim()
        if (cleanText.isEmpty()) return emptyList()

        val activeAccounts = accounts.filter { !it.archived }.ifEmpty { accounts }
        val rawSegments = splitClauses(cleanText)
        if (rawSegments.isEmpty()) return emptyList()

        val results = mutableListOf<ParsedBillDraft>()
        var inheritedDayOffset = 0
        var pendingPrefix = ""

        for (rawSegment in rawSegments) {
            val segment = rawSegment.trim()
            if (segment.isEmpty()) continue

            val explicitDayOffset = detectDayOffset(segment)
            if (explicitDayOffset != null) {
                inheritedDayOffset = explicitDayOffset
            }

            val normalizedAmountText = normalizeColloquialChineseMoney(segment)
            val hasAnyDigit = normalizedAmountText.any { it.isDigit() }
            if (!hasAnyDigit) {
                if (!isPureTimeOrDatePhrase(segment)) {
                    pendingPrefix = if (pendingPrefix.isBlank()) segment else "$pendingPrefix $segment"
                }
                continue
            }

            val combinedClause = if (pendingPrefix.isNotBlank()) {
                "$pendingPrefix $segment"
            } else {
                segment
            }
            pendingPrefix = ""

            val draft = parseSingleClause(
                clause = combinedClause,
                effectiveDayOffset = explicitDayOffset ?: inheritedDayOffset,
                accounts = activeAccounts,
                categories = categories,
                aliases = aliases,
                nowMillis = nowMillis
            )
            if (draft != null) {
                results.add(draft)
            }
        }

        return results
    }

    private fun parseSingleClause(
        clause: String,
        effectiveDayOffset: Int,
        accounts: List<AccountEntity>,
        categories: List<CategoryEntity>,
        aliases: List<AccountAliasEntity>,
        nowMillis: Long
    ): ParsedBillDraft? {
        val normalizedClause = normalizeColloquialChineseMoney(clause)
        val clauseTimestamp = resolveClauseTimestamp(normalizedClause, effectiveDayOffset, nowMillis)

        val strippedForAmount = stripNonAmountTokens(normalizedClause, accounts)
        val amountMatch = extractAmountAndCurrency(strippedForAmount, normalizedClause) ?: return null
        val (amountMicros, currency, matchedToken) = amountMatch
        if (amountMicros <= 0L) return null

        val txType = inferTransactionType(normalizedClause)
        val transferPair = if (txType == TransactionType.TRANSFER) {
            extractTransferAccounts(normalizedClause, accounts, aliases, currency)
        } else {
            null
        }

        val accountHint = localRuleParser.extractAccountHint(normalizedClause)
        val accountId = transferPair?.first ?: resolveSmartAccountId(
            clause = normalizedClause,
            accountHint = accountHint,
            currency = currency,
            aliases = aliases,
            accounts = accounts
        )
        val effectiveAccountHint = when {
            transferPair?.second != null -> "->${transferPair.second!!.name}"
            else -> accountHint
        }

        val merchant = extractCleanMerchant(
            clause = normalizedClause,
            matchedAmountToken = matchedToken,
            txType = txType,
            transferTargetName = transferPair?.second?.name.orEmpty()
        )

        val (resolvedCategoryId, resolvedCategoryName) = if (txType == TransactionType.TRANSFER) {
            null to ""
        } else {
            val defaultCat = if (txType == TransactionType.INCOME) "其他收入" else "其他支出"
            val augmentedContext = augmentCategoryKeywords(normalizedClause)
            val cat = localRuleParser.resolveCategory(
                type = txType,
                merchant = merchant,
                fullText = augmentedContext,
                ruleDefaultCategoryName = defaultCat,
                categories = categories
            )
            cat?.id to (cat?.name ?: defaultCat)
        }

        return ParsedBillDraft(
            type = txType,
            origAmountMicros = amountMicros,
            origCurrency = currency,
            accountId = accountId,
            accountHint = effectiveAccountHint,
            categoryId = resolvedCategoryId,
            categoryName = resolvedCategoryName,
            merchant = merchant,
            note = clause.trim().take(80),
            timestamp = clauseTimestamp,
            confidence = 0.86f,
            parserUsed = "LOCAL_SMART_INPUT"
        )
    }

    private fun splitClauses(text: String): List<String> {
        val primaryPieces = PRIMARY_SPLIT_REGEX.split(text)
        val finalPieces = mutableListOf<String>()
        for (piece in primaryPieces) {
            val subPieces = CONNECTOR_SPLIT_REGEX.split(piece)
            for (sub in subPieces) {
                val trimmed = sub.trim()
                if (trimmed.isNotEmpty()) {
                    finalPieces.add(trimmed)
                }
            }
        }
        return finalPieces
    }

    private fun normalizeColloquialChineseMoney(text: String): String {
        var s = COLLOQUIAL_YUAN_JIAO_REGEX.replace(text) { m ->
            "${m.groupValues[1]}.${m.groupValues[2]}元"
        }
        s = COLLOQUIAL_YUAN_ONLY_REGEX.replace(s) { m ->
            "${m.groupValues[1]}元"
        }
        return s
    }

    private fun detectDayOffset(clause: String): Int? = when {
        clause.contains("大前天") -> -3
        clause.contains("前天") || clause.contains("前日") || clause.contains("前晚") -> -2
        clause.contains("昨天") || clause.contains("昨日") || clause.contains("昨晚") || clause.contains("昨夜") -> -1
        clause.contains("今天") || clause.contains("今日") || clause.contains("今晚") ||
            clause.contains("今早") || clause.contains("刚刚") || clause.contains("刚才") -> 0
        else -> null
    }

    private fun isPureTimeOrDatePhrase(segment: String): Boolean {
        val cleaned = RELATIVE_DATE_WORDS_REGEX.replace(segment, "")
            .replace(TIME_PERIOD_WORDS_REGEX, "")
            .trim()
        return cleaned.isEmpty()
    }

    private fun resolveClauseTimestamp(
        clause: String,
        dayOffset: Int,
        nowMillis: Long
    ): Long {
        var zdt = Instant.ofEpochMilli(nowMillis).atZone(zoneId).plusDays(dayOffset.toLong())

        EXPLICIT_CLOCK_REGEX.find(clause)?.let { match ->
            val hour = match.groupValues[1].toIntOrNull()
            val minute = match.groupValues[2].toIntOrNull()
            if (hour != null && minute != null && hour in 0..23 && minute in 0..59) {
                zdt = zdt.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
                return zdt.toInstant().toEpochMilli()
            }
        }

        val periodTime = when {
            clause.contains("凌晨") -> 2 to 0
            clause.contains("早上") || clause.contains("早晨") || clause.contains("今早") || clause.contains("清晨") -> 8 to 30
            clause.contains("上午") -> 10 to 0
            clause.contains("中午") -> 12 to 30
            clause.contains("下午") -> 15 to 0
            clause.contains("傍晚") -> 18 to 0
            clause.contains("晚上") || clause.contains("昨晚") || clause.contains("今晚") ||
                clause.contains("前晚") || clause.contains("昨夜") || clause.contains("夜里") -> 20 to 30
            else -> null
        }
        if (periodTime != null) {
            zdt = zdt.withHour(periodTime.first).withMinute(periodTime.second).withSecond(0).withNano(0)
        }
        return zdt.toInstant().toEpochMilli()
    }

    private fun stripNonAmountTokens(text: String, accounts: List<AccountEntity>): String {
        var s = text
            .replace(EXPLICIT_DATE_REGEX, " ")
            .replace(EXPLICIT_CLOCK_REGEX, " ")
            .replace(CARD_LAST4_STRIP_REGEX, " ")
            .replace(HYPHENATED_BRAND_REGEX, " ")
        for (acc in accounts) {
            if (acc.name.length >= 2 && acc.name.any { it.isDigit() }) {
                s = s.replace(acc.name, " ", ignoreCase = true)
            }
        }
        return s.trim()
    }

    private fun extractAmountAndCurrency(
        strippedText: String,
        originalText: String
    ): Triple<Long, String, String>? {
        // 1. 优先匹配显式带币种前缀或后缀的数字
        for (match in CURRENCY_AMOUNT_REGEX.findAll(strippedText)) {
            val prefixCur = match.groupValues.getOrNull(1).orEmpty()
            val numStr = match.groupValues.getOrNull(2).orEmpty()
            val suffixCur = match.groupValues.getOrNull(3).orEmpty()
            if (prefixCur.isNotBlank() || suffixCur.isNotBlank()) {
                val micros = MoneyAmount.parseToMicros(numStr) ?: continue
                if (micros <= 0L) continue
                val rawCur = prefixCur.ifBlank { suffixCur }
                val currency = localRuleParser.normalizeCurrencyToken(rawCur, CurrencyCatalog.DEFAULT_BASE_CURRENCY)
                return Triple(micros, currency, match.value)
            }
        }

        // 2. 兜底匹配裸小数或裸整数（如 "打车 35"、"买菜 42.5"），排除 1900..2100 年份数字
        val bareCandidates = mutableListOf<Pair<Long, String>>()
        for (match in BARE_NUMBER_REGEX.findAll(strippedText)) {
            val rawNum = match.value
            val intPart = rawNum.substringBefore('.').replace(",", "")
            if (!rawNum.contains('.') && intPart.length == 4 && (intPart.toIntOrNull() in 1900..2100)) {
                continue
            }
            val micros = MoneyAmount.parseToMicros(rawNum) ?: continue
            if (micros > 0L) {
                bareCandidates.add(micros to rawNum)
            }
        }
        val picked = bareCandidates.lastOrNull() ?: return null
        val detectedCurrency = CurrencyCatalog.detectCurrencyFromText(
            originalText,
            CurrencyCatalog.DEFAULT_BASE_CURRENCY
        )
        return Triple(picked.first, detectedCurrency, picked.second)
    }

    private fun inferTransactionType(text: String): String {
        val lower = text.lowercase(Locale.ROOT)
        if (STRONG_INCOME_PHRASES.any { lower.contains(it) }) {
            return TransactionType.INCOME
        }
        if (TRANSFER_FROM_TO_REGEX.containsMatchIn(text) ||
            STRONG_TRANSFER_PHRASES.any { lower.contains(it) }
        ) {
            return TransactionType.TRANSFER
        }
        if (INCOME_KEYWORDS.any { lower.contains(it) }) {
            return TransactionType.INCOME
        }
        if (lower.contains("转账") || lower.contains("transfer")) {
            return TransactionType.TRANSFER
        }
        return TransactionType.EXPENSE
    }

    private fun extractTransferAccounts(
        clause: String,
        accounts: List<AccountEntity>,
        aliases: List<AccountAliasEntity>,
        currency: String
    ): Pair<Long?, AccountEntity?>? {
        val match = TRANSFER_FROM_TO_REGEX.find(clause) ?: return null
        val fromPart = match.groupValues[1].trim()
        val toPart = match.groupValues[2].trim()

        val fromAccId = resolveSmartAccountId(
            clause = fromPart,
            accountHint = localRuleParser.extractAccountHint(fromPart),
            currency = currency,
            aliases = aliases,
            accounts = accounts
        )
        val toAcc = findDirectAccountMatch(toPart, accounts)
            ?: accounts.firstOrNull { acc ->
                acc.id != fromAccId && matchesCommonAccountKeyword(toPart, acc)
            }
        return fromAccId to toAcc
    }

    private fun resolveSmartAccountId(
        clause: String,
        accountHint: String,
        currency: String,
        aliases: List<AccountAliasEntity>,
        accounts: List<AccountEntity>
    ): Long? {
        if (accounts.isEmpty()) return null

        // 1. 直接匹配账户名称（长名优先，避免短关键词覆盖具体账户）
        findDirectAccountMatch(clause, accounts)?.let { return it.id }

        // 2. 卡尾号 4 位匹配
        if (accountHint.isNotBlank()) {
            accounts.firstOrNull { it.name.contains(accountHint) }?.let { return it.id }
        }

        // 3. 常见口语账户词映射（如 "微信" -> "微信零钱", "信用卡" -> CREDIT 账户）
        accounts.firstOrNull { matchesCommonAccountKeyword(clause, it) }?.let { return it.id }

        // 4. 复用 LocalRuleParser 完整路由（Alias + 币种兜底）
        val input = CaptureInput(
            channel = CaptureChannel.SHARE,
            sourcePackage = "smart_input",
            title = "",
            content = clause
        )
        return localRuleParser.resolveAccountId(
            input = input,
            fullText = clause,
            accountHint = accountHint,
            currency = currency,
            aliases = aliases,
            accounts = accounts
        )
    }

    private fun findDirectAccountMatch(text: String, accounts: List<AccountEntity>): AccountEntity? {
        val sorted = accounts.sortedByDescending { it.name.length }
        for (acc in sorted) {
            if (acc.name.length >= 2 && text.contains(acc.name, ignoreCase = true)) {
                return acc
            }
        }
        return null
    }

    private fun matchesCommonAccountKeyword(text: String, account: AccountEntity): Boolean {
        val lowerText = text.lowercase(Locale.ROOT)
        val lowerName = account.name.lowercase(Locale.ROOT)
        return when {
            lowerText.contains("微信") && lowerName.contains("微信") -> true
            (lowerText.contains("支付宝") || lowerText.contains("花呗") || lowerText.contains("余额宝")) &&
                lowerName.contains("支付宝") -> true
            lowerText.contains("信用卡") &&
                (account.groupType == AccountGroup.CREDIT || lowerName.contains("信用卡")) -> true
            (lowerText.contains("储蓄卡") || lowerText.contains("借记卡") || lowerText.contains("工资卡")) &&
                (account.groupType == AccountGroup.DEBIT || lowerName.contains("储蓄卡")) -> true
            lowerText.contains("现金") && lowerName.contains("现金") -> true
            (lowerText.contains("美元账户") || lowerText.contains("美金账户")) &&
                account.currencyCode.equals("USD", ignoreCase = true) -> true
            lowerText.contains("欧元账户") &&
                account.currencyCode.equals("EUR", ignoreCase = true) -> true
            lowerText.contains("英镑账户") &&
                account.currencyCode.equals("GBP", ignoreCase = true) -> true
            else -> false
        }
    }

    private fun augmentCategoryKeywords(clause: String): String {
        val lower = clause.lowercase(Locale.ROOT)
        val extra = buildList {
            if (listOf("早饭", "午饭", "晚饭", "夜宵", "吃饭", "水果", "买菜", "零食", "买水", "饮料", "点餐").any { lower.contains(it) }) {
                add("餐饮")
            }
            if (listOf("买书", "书籍", "图书", "网购", "日用品").any { lower.contains(it) }) {
                add("购物")
            }
            if (listOf("打车", "滴滴", "地铁", "公交", "骑车", "单车", "停车", "车费").any { lower.contains(it) }) {
                add("交通")
            }
        }
        return if (extra.isEmpty()) clause else "$clause ${extra.joinToString(" ")}"
    }

    private fun extractCleanMerchant(
        clause: String,
        matchedAmountToken: String,
        txType: String,
        transferTargetName: String
    ): String {
        if (txType == TransactionType.TRANSFER) {
            val giveMatch = TRANSFER_TARGET_PERSON_REGEX.find(clause)
            val person = giveMatch?.groupValues?.getOrNull(1)?.trim().orEmpty()
            if (person.isNotBlank()) return person.take(32)
            if (transferTargetName.isNotBlank()) return "转入$transferTargetName".take(32)
        }

        var s = clause
        if (matchedAmountToken.isNotBlank()) {
            s = s.replace(matchedAmountToken, " ")
        }
        s = s.replace(EXPLICIT_DATE_REGEX, " ")
            .replace(EXPLICIT_CLOCK_REGEX, " ")
            .replace(RELATIVE_DATE_WORDS_REGEX, " ")
            .replace(TIME_PERIOD_WORDS_REGEX, " ")
            .replace(PAYMENT_ACCOUNT_NOISE_REGEX, " ")
            .replace(LEADING_ACTION_FILLER_REGEX, "")
            .replace(TRAILING_ACTION_FILLER_REGEX, "")
            .replace(WHITESPACE_RUN_REGEX, " ")
            .trim(' ', '，', ',', '。', '；', ';', '：', ':')

        return s.take(36)
    }

    private fun hasUsableLlmConfig(settings: AppSettings): Boolean {
        val url = settings.llmBaseUrl.trim()
        if (url.isEmpty()) return false
        val isLocal = url.contains("127.0.0.1") || url.contains("localhost")
        return settings.llmApiKey.isNotBlank() || isLocal
    }

    companion object {
        private val PRIMARY_SPLIT_REGEX = Regex("(?:[，；;、。\\r\\n]+|(?<!\\d),(?!\\d)|,(?![0-9]{3}(?:\\.[0-9]+)?(?:\\D|$)))")
        private val CONNECTOR_SPLIT_REGEX = Regex("(?:然后|接着|随后|另外|还有|以及|并且|(?<=\\s|[0-9元块美元欧镑币])又|又(?=买|花|付|吃|喝|收|转|充|退|订|打|看))")

        private val COLLOQUIAL_YUAN_JIAO_REGEX = Regex("(\\d+)块(\\d{1,2})(?![0-9.])")
        private val COLLOQUIAL_YUAN_ONLY_REGEX = Regex("(\\d+)块(?![0-9.])")

        private val EXPLICIT_DATE_REGEX = Regex("\\d{4}[-/.年]\\d{1,2}[-/.月]\\d{1,2}日?|\\d{1,2}[-/.月]\\d{1,2}日")
        private val EXPLICIT_CLOCK_REGEX = Regex("(?<!\\d)(\\d{1,2}):(\\d{2})(?::\\d{2})?(?!\\d)")
        private val CARD_LAST4_STRIP_REGEX = Regex(
            "(?:(?:尾号|末四位|卡号[*xX]*)\\s*\\d{4}|[(（]\\s*\\d{4}\\s*[)）]|(?:信用卡|储蓄卡|借记卡)\\s*\\d{4}(?!\\s*(?:\\.|元|块|人民币|美元|美金|欧元|英镑|港币|港元|日元|新元|新加坡元|澳元|加元|瑞郎|新台币|韩元|USD|EUR|GBP|HKD|JPY|SGD|AUD|CAD|CHF|TWD|KRW|CNY|RMB)))",
            RegexOption.IGNORE_CASE
        )
        private val HYPHENATED_BRAND_REGEX = Regex("\\b(?:7-11|7-eleven)\\b", RegexOption.IGNORE_CASE)

        private val CURRENCY_AMOUNT_REGEX = Regex(
            "(US\\$|HK\\$|NT\\$|S\\$|A\\$|C\\$|JP¥|USD|EUR|GBP|HKD|JPY|SGD|AUD|CAD|CHF|TWD|KRW|CNY|RMB|人民币|美元|美金|欧元|英镑|港币|港元|日元|新元|新加坡元|澳元|加元|瑞郎|新台币|韩元|\\$|€|£|¥|￥|₩)?\\s*" +
                "([0-9]+(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?)\\s*" +
                "(元|美元|美金|欧元|英镑|港币|港元|日元|新元|新加坡元|澳元|加元|瑞郎|新台币|韩元|USD|EUR|GBP|HKD|JPY|SGD|AUD|CAD|CHF|TWD|KRW|CNY|RMB)?",
            RegexOption.IGNORE_CASE
        )
        private val BARE_NUMBER_REGEX = Regex("[0-9]+(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?")

        private val STRONG_INCOME_PHRASES = listOf(
            "转账收到", "收到转账", "收到的转账", "收到红包", "转入我", "received transfer"
        )
        private val INCOME_KEYWORDS = listOf(
            "发工资", "工资", "薪资", "收款", "奖金", "退款", "退回", "报销",
            "入账", "到账", "分红", "返现", "兼职", "稿费", "收入", "收到",
            "salary", "bonus", "refund", "reimburse", "income", "received"
        )

        private val TRANSFER_FROM_TO_REGEX = Regex(
            "从\\s*([^\\s,，。;；]+?)\\s*转(?:到|入|给)\\s*([^\\s,，。;；0-9]+)"
        )
        private val STRONG_TRANSFER_PHRASES = listOf(
            "转账给", "转给", "转到", "还信用卡", "还花呗", "还白条", "还款"
        )
        private val TRANSFER_TARGET_PERSON_REGEX = Regex("(?:转账给|转给)\\s*([^\\s,，。;；0-9¥￥$€£]{1,20})")

        private val RELATIVE_DATE_WORDS_REGEX = Regex("(?:大前天|前天|前日|前晚|昨天|昨日|昨晚|昨夜|今天|今日|今晚|今早|刚刚|刚才)")
        private val TIME_PERIOD_WORDS_REGEX = Regex("(?:凌晨|早上|早晨|清晨|上午|中午|下午|傍晚|晚上|夜里)")
        private val PAYMENT_ACCOUNT_NOISE_REGEX = Regex(
            "(?:用|通过|使用|从)?\\s*(?:微信零钱|微信支付|微信|支付宝|花呗|余额宝|储蓄卡|借记卡|信用卡|全币种信用卡|招商信用卡\\d{0,4}|美元账户|欧元账户|英镑账户|现金)\\s*(?:付款|支付|扣款|消费|入账|收款)?"
        )
        private val LEADING_ACTION_FILLER_REGEX = Regex("^(?:花了|花费|消费了|消费|支出|付款|支付|买了|买|收到|入账|发了)\\s*")
        private val TRAILING_ACTION_FILLER_REGEX = Regex("\\s*(?:花了|花费|付款|支付|元|块)$")
        private val WHITESPACE_RUN_REGEX = Regex("\\s+")
    }
}

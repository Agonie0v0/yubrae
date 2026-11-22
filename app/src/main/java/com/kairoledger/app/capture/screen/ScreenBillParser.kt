package com.kairoledger.app.capture.screen

import com.kairoledger.app.capture.LocalRuleParser
import com.kairoledger.app.core.contract.CaptureInput
import com.kairoledger.app.core.contract.ParsedBillDraft
import com.kairoledger.app.core.model.CurrencyCatalog
import com.kairoledger.app.core.model.MoneyAmount
import com.kairoledger.app.data.db.AccountAliasEntity
import com.kairoledger.app.data.db.AccountEntity
import com.kairoledger.app.data.db.AliasMatchType
import com.kairoledger.app.data.db.CaptureChannel
import com.kairoledger.app.data.db.CategoryEntity
import com.kairoledger.app.data.db.TransactionType
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

/**
 * 无障碍屏幕账单页与支付成功页语义解析器（纯 JVM 可测，严禁依赖 android.*）。
 */
class ScreenBillParser(
    private val localRuleParser: LocalRuleParser = LocalRuleParser(),
    private val zoneId: ZoneId = ZoneId.systemDefault()
) {

    private enum class FieldKey {
        STATUS,
        TIME,
        PAYMENT_METHOD,
        ORDER_NO,
        MERCHANT,
        PAYEE,
        PAYER,
        GOODS,
        ACTUAL_AMOUNT,
        FOREIGN_AMOUNT,
        AMOUNT,
        ORDER_AMOUNT,
        IGNORED_AMOUNT
    }

    private data class ExtractedField(
        val key: FieldKey,
        val rawLabel: String,
        val value: String,
        val lineIndex: Int
    )

    private enum class AmountRole(val priority: Int) {
        ACTUAL_PAID(4),
        FOREIGN_ORIG(4),
        TRANSACTION_AMOUNT(3),
        HERO(2),
        ORDER_AMOUNT(1),
        GENERIC(0)
    }

    private data class AmountCandidate(
        val amountMicros: Long,
        val currency: String,
        val hasExplicitCurrency: Boolean,
        val role: AmountRole,
        val lineIndex: Int,
        val signHint: String?
    )

    private data class ResolvedAmountResult(
        val origAmountMicros: Long,
        val origCurrency: String,
        val cnySettlementMicros: Long?,
        val dominantSign: String?
    )

    fun classifyPage(snapshot: ScreenSnapshot): ScreenPageKind {
        if (!isWatchedPackage(snapshot.packageName)) return ScreenPageKind.UNKNOWN
        val lines = normalizeLines(snapshot.texts)
        if (lines.isEmpty()) return ScreenPageKind.UNKNOWN
        val fields = extractFields(lines)
        return classifyPageInternal(snapshot.packageName, lines, fields).first
    }

    fun parse(
        snapshot: ScreenSnapshot,
        aliases: List<AccountAliasEntity> = emptyList(),
        accounts: List<AccountEntity> = emptyList(),
        categories: List<CategoryEntity> = emptyList()
    ): ScreenParseOutcome {
        if (!isWatchedPackage(snapshot.packageName)) {
            return ScreenParseOutcome(pageKind = ScreenPageKind.UNKNOWN, draft = null)
        }

        val lines = normalizeLines(snapshot.texts)
        if (lines.isEmpty()) {
            return ScreenParseOutcome(pageKind = ScreenPageKind.UNKNOWN, draft = null)
        }

        val fields = extractFields(lines)
        val (pageKind, prepayReason) = classifyPageInternal(snapshot.packageName, lines, fields)
        val summaryText = buildSummaryText(lines)
        val orderNo = extractOrderNo(fields)

        val ignoredStatus = detectIgnoredStatus(lines, fields)
        if (ignoredStatus != null) {
            return ScreenParseOutcome(
                pageKind = pageKind,
                draft = null,
                orderNo = orderNo,
                dedupFingerprint = "",
                summaryText = summaryText,
                shouldFallbackToLlm = false,
                ignoredReason = "交易状态为「$ignoredStatus」，不触发记账"
            )
        }

        if (pageKind == ScreenPageKind.UNKNOWN) {
            return ScreenParseOutcome(
                pageKind = ScreenPageKind.UNKNOWN,
                draft = null,
                orderNo = "",
                dedupFingerprint = "",
                summaryText = "",
                shouldFallbackToLlm = false,
                ignoredReason = prepayReason
            )
        }

        val resolvedAmount = resolveAmounts(lines, fields)
        if (resolvedAmount == null || resolvedAmount.origAmountMicros <= 0L) {
            return ScreenParseOutcome(
                pageKind = pageKind,
                draft = null,
                orderNo = orderNo,
                dedupFingerprint = if (orderNo.isNotBlank()) "ORDER:$orderNo" else "",
                summaryText = summaryText,
                shouldFallbackToLlm = true,
                ignoredReason = ""
            )
        }

        val txType = inferTransactionType(lines, fields, resolvedAmount.dominantSign)
        val timestamp = extractTransactionTimestamp(fields, lines, snapshot.capturedAtMillis)
        val paymentMethodText = extractFirstFieldValue(fields, FieldKey.PAYMENT_METHOD)
        val accountHint = extractAccountHint(paymentMethodText, summaryText)
        val merchant = resolveMerchant(lines, fields, txType)
        val goodsDesc = extractFirstFieldValue(fields, FieldKey.GOODS)

        val activeAccounts = accounts.filter { !it.archived }.ifEmpty { accounts }
        val routingCurrency = if (resolvedAmount.cnySettlementMicros != null) {
            CurrencyCatalog.DEFAULT_BASE_CURRENCY
        } else {
            resolvedAmount.origCurrency
        }
        val accountId = resolveScreenAccountId(
            packageName = snapshot.packageName,
            summaryText = summaryText,
            paymentMethodText = paymentMethodText,
            accountHint = accountHint,
            routingCurrency = routingCurrency,
            aliases = aliases,
            accounts = activeAccounts
        )

        val resolvedAccount = activeAccounts.firstOrNull { it.id == accountId }
        val manualAccountAmountMicros = if (
            resolvedAmount.cnySettlementMicros != null &&
            !resolvedAmount.origCurrency.equals(CurrencyCatalog.DEFAULT_BASE_CURRENCY, ignoreCase = true) &&
            (resolvedAccount == null ||
                resolvedAccount.currencyCode.equals(CurrencyCatalog.DEFAULT_BASE_CURRENCY, ignoreCase = true))
        ) {
            resolvedAmount.cnySettlementMicros
        } else {
            null
        }

        val defaultCat = if (txType == TransactionType.INCOME) "其他收入" else "其他支出"
        val categoryContext = listOf(merchant, goodsDesc, summaryText)
            .filter { it.isNotBlank() }
            .joinToString(" ")
        val resolvedCategory = localRuleParser.resolveCategory(
            type = txType,
            merchant = merchant,
            fullText = categoryContext,
            ruleDefaultCategoryName = defaultCat,
            categories = categories
        )

        val dedupFingerprint = if (orderNo.isNotBlank()) {
            "ORDER:$orderNo"
        } else {
            "SCREEN:${snapshot.packageName}:$txType:${resolvedAmount.origCurrency}:${resolvedAmount.origAmountMicros}:${timestamp / 60_000L}:$merchant"
        }

        val note = buildCompactNote(
            goodsDesc = goodsDesc,
            merchant = merchant,
            paymentMethodText = paymentMethodText,
            orderNo = orderNo
        )

        val draft = ParsedBillDraft(
            type = txType,
            origAmountMicros = resolvedAmount.origAmountMicros,
            origCurrency = resolvedAmount.origCurrency,
            accountId = accountId,
            accountHint = accountHint,
            categoryId = resolvedCategory?.id,
            categoryName = resolvedCategory?.name ?: defaultCat,
            merchant = merchant,
            note = note,
            timestamp = timestamp,
            confidence = if (pageKind == ScreenPageKind.BILL_DETAIL) 0.95f else 0.92f,
            parserUsed = if (pageKind == ScreenPageKind.BILL_DETAIL) "SCREEN_BILL_DETAIL" else "SCREEN_PAY_SUCCESS",
            externalOrderNo = orderNo,
            manualAccountAmountMicros = manualAccountAmountMicros
        )

        return ScreenParseOutcome(
            pageKind = pageKind,
            draft = draft,
            orderNo = orderNo,
            dedupFingerprint = dedupFingerprint,
            summaryText = summaryText,
            shouldFallbackToLlm = false,
            ignoredReason = ""
        )
    }

    private fun normalizeLines(texts: List<String>): List<String> {
        val raw = texts.mapNotNull { line ->
            val cleaned = line
                .replace('\u00A0', ' ')
                .replace("\u200B", "")
                .replace("\uFEFF", "")
                .replace(WHITESPACE_RUN_REGEX, " ")
                .trim()
            cleaned.takeIf { it.isNotEmpty() }
        }
        if (raw.isEmpty()) return emptyList()

        val merged = mutableListOf<String>()
        var i = 0
        while (i < raw.size) {
            val curr = raw[i]
            val next = raw.getOrNull(i + 1)
            val third = raw.getOrNull(i + 2)

            if (next != null && third != null &&
                (curr == "-" || curr == "+") &&
                SPLIT_CURRENCY_ONLY_REGEX.matches(next) &&
                SPLIT_NUMBER_REGEX.matches(third)
            ) {
                val combined = if (next.all { it.isLetter() }) "$curr$next $third" else "$curr$next$third"
                if (merged.lastOrNull() != combined) merged.add(combined)
                i += 3
                continue
            }

            if (next != null && SPLIT_PREFIX_REGEX.matches(curr) && SPLIT_NUMBER_REGEX.matches(next)) {
                val combined = if (curr.all { it.isLetter() }) "$curr $next" else "$curr$next"
                if (merged.lastOrNull() != combined) merged.add(combined)
                i += 2
                continue
            }

            if (next != null && SPLIT_SIGNED_NUMBER_REGEX.matches(curr) && SPLIT_SUFFIX_CURRENCY_REGEX.matches(next)) {
                val combined = "$curr $next"
                if (merged.lastOrNull() != combined) merged.add(combined)
                i += 2
                continue
            }

            if (merged.lastOrNull() != curr) {
                merged.add(curr)
            }
            i++
        }
        return merged
    }

    private fun extractFields(lines: List<String>): List<ExtractedField> {
        val result = mutableListOf<ExtractedField>()
        var i = 0
        while (i < lines.size) {
            val line = lines[i]

            val colonIdx = findKeySeparatorIndex(line)
            if (colonIdx > 0) {
                val rawLabel = line.substring(0, colonIdx).trim()
                val key = matchFieldKey(rawLabel)
                if (key != null) {
                    val inlineVal = line.substring(colonIdx + 1).trim()
                    if (inlineVal.isNotEmpty()) {
                        result.add(ExtractedField(key, rawLabel, inlineVal, i))
                        i++
                        continue
                    } else {
                        val nextLine = lines.getOrNull(i + 1)
                        if (nextLine != null && !isLikelyFieldLabel(nextLine) && !isActionButtonOnly(nextLine)) {
                            result.add(ExtractedField(key, rawLabel, nextLine, i + 1))
                            i += 2
                            continue
                        }
                    }
                }
            }

            val exactKey = matchFieldKey(line)
            if (exactKey != null) {
                val nextLine = lines.getOrNull(i + 1)
                if (nextLine != null && !isLikelyFieldLabel(nextLine) && !isActionButtonOnly(nextLine)) {
                    result.add(ExtractedField(exactKey, line, nextLine, i + 1))
                    i += 2
                    continue
                }
            }

            val spaceField = matchSpaceSeparatedField(line)
            if (spaceField != null) {
                val (key, label, value) = spaceField
                result.add(ExtractedField(key, label, value, i))
            }

            i++
        }
        return result
    }

    private fun findKeySeparatorIndex(line: String): Int {
        val cnColon = line.indexOf('：')
        val enColon = line.indexOf(':')
        return when {
            cnColon > 0 && enColon > 0 -> minOf(cnColon, enColon)
            cnColon > 0 -> cnColon
            enColon > 0 -> enColon
            else -> -1
        }
    }

    private fun matchFieldKey(rawLabel: String): FieldKey? {
        val normalized = rawLabel.trim().trimEnd(':', '：').lowercase(Locale.ROOT)
        return FIELD_KEY_MAP[normalized]
    }

    private fun isLikelyFieldLabel(line: String): Boolean {
        val trimmed = line.trim().trimEnd(':', '：').lowercase(Locale.ROOT)
        return trimmed in FIELD_KEY_MAP
    }

    private fun isActionButtonOnly(line: String): Boolean =
        line.trim().lowercase(Locale.ROOT) in ACTION_ONLY_WORDS

    private fun matchSpaceSeparatedField(line: String): Triple<FieldKey, String, String>? {
        val lower = line.lowercase(Locale.ROOT)
        for ((prefix, key) in SPACE_SEPARATED_PREFIXES) {
            if (lower.startsWith(prefix) && line.length > prefix.length) {
                val nextChar = line[prefix.length]
                if (nextChar == ' ' || nextChar == '\t') {
                    val value = line.substring(prefix.length).trim()
                    if (value.isNotEmpty()) {
                        return Triple(key, line.substring(0, prefix.length), value)
                    }
                }
            }
        }
        return null
    }

    private fun classifyPageInternal(
        packageName: String,
        lines: List<String>,
        fields: List<ExtractedField>
    ): Pair<ScreenPageKind, String> {
        val lowerLines = lines.map { it.lowercase(Locale.ROOT) }
        val lowerJoined = lowerLines.joinToString("\n")

        if (CHAT_STRONG_MARKERS.any { lowerJoined.contains(it) }) {
            return ScreenPageKind.UNKNOWN to ""
        }
        if (lowerLines.contains("表情") && lowerLines.contains("发送")) {
            return ScreenPageKind.UNKNOWN to ""
        }
        if ((lowerLines.contains("通讯录") && lowerLines.contains("发现")) ||
            (lowerLines.contains("首页") && lowerLines.contains("理财") && lowerLines.contains("我的"))
        ) {
            return ScreenPageKind.UNKNOWN to ""
        }

        val distinctDetailKeys = fields
            .map { it.key }
            .filter { it in DETAIL_SIGNAL_KEYS }
            .toSet()
            .size

        val hasDetailHeader = lowerLines.any { line ->
            DETAIL_HEADER_KEYWORDS.any { line == it || line.startsWith("$it ") || line.startsWith("$it·") }
        }

        val isBillListOverview = (BILL_LIST_MARKERS.any { marker -> lowerLines.any { it == marker || it.contains(marker) } } ||
            (lowerLines.any { it == "账单" || it == "我的账单" } &&
                lowerLines.any { it == "支出" || it.startsWith("支出 ") || it.startsWith("支出¥") } &&
                lowerLines.any { it == "收入" || it.startsWith("收入 ") || it.startsWith("收入¥") })) &&
            !hasDetailHeader &&
            distinctDetailKeys < 2

        if (isBillListOverview) {
            return ScreenPageKind.UNKNOWN to ""
        }

        val hasPaySuccessPhrase = lines.take(16).any { line ->
            val lower = line.lowercase(Locale.ROOT)
            !lower.contains('?') && !lower.contains('？') &&
                PAY_SUCCESS_PHRASES.any { phrase ->
                    lower == phrase ||
                        lower.startsWith("$phrase ") ||
                        lower.startsWith("$phrase，") ||
                        lower.startsWith("$phrase!") ||
                        lower.startsWith("$phrase！")
                }
        } || fields.any { field ->
            field.key == FieldKey.STATUS && PAY_SUCCESS_PHRASES.any {
                field.value.lowercase(Locale.ROOT).contains(it)
            }
        }

        val hasPrepayCheckoutMarker = lowerLines.any { line ->
            PREPAY_CHECKOUT_MARKERS.any { line == it || line.startsWith(it) }
        }
        if (hasPrepayCheckoutMarker && !hasPaySuccessPhrase && !hasDetailHeader && distinctDetailKeys < 2) {
            return ScreenPageKind.UNKNOWN to "待付款/收银台页面不触发"
        }

        val hasDoneAnchor = lowerLines.any { line ->
            DONE_ANCHOR_KEYWORDS.any { line == it || line.startsWith("$it(") || line.startsWith("$it（") }
        }

        val strongDetailOnlyCount = fields
            .map { it.key }
            .filter { it == FieldKey.TIME || it == FieldKey.ORDER_NO || it == FieldKey.STATUS }
            .toSet()
            .size

        // 支付结果页常伴随“支付成功 + 完成 + 商户/支付方式”，但不含时间/单号/状态或独立详情标题
        val isPaySuccessResultLayout = hasPaySuccessPhrase && hasDoneAnchor &&
            !hasStandaloneDetailTitleWithoutDone(lowerLines) &&
            strongDetailOnlyCount == 0
        if (strongDetailOnlyCount >= 2 ||
            (distinctDetailKeys >= 2 && !isPaySuccessResultLayout) ||
            (hasDetailHeader && !isPaySuccessResultLayout)
        ) {
            return ScreenPageKind.BILL_DETAIL to ""
        }

        if (hasPaySuccessPhrase) {
            val isWeChat = packageName == PKG_WECHAT
            val isAlipay = packageName == PKG_ALIPAY
            val hasBankOrDetailAnchor = hasDoneAnchor || hasDetailHeader || distinctDetailKeys >= 1 ||
                lowerLines.any { line ->
                    BANK_RESULT_ANCHORS.any { line.contains(it) }
                }
            val matched = when {
                isWeChat -> hasDoneAnchor || hasDetailHeader || distinctDetailKeys >= 1
                isAlipay -> hasDoneAnchor || hasDetailHeader || distinctDetailKeys >= 1
                else -> hasBankOrDetailAnchor
            }
            if (matched) {
                return ScreenPageKind.PAY_SUCCESS to ""
            }
        }

        return ScreenPageKind.UNKNOWN to ""
    }

    private fun hasStandaloneDetailTitleWithoutDone(lowerLines: List<String>): Boolean {
        val hasDone = lowerLines.any { it == "完成" || it == "done" || it == "返回商家" }
        if (hasDone) return false
        return lowerLines.take(4).any { line ->
            DETAIL_HEADER_KEYWORDS.any { line == it }
        }
    }

    private fun detectIgnoredStatus(lines: List<String>, fields: List<ExtractedField>): String? {
        for (field in fields) {
            if (field.key == FieldKey.STATUS) {
                val lowerVal = field.value.lowercase(Locale.ROOT)
                IGNORED_STATUS_KEYWORDS.firstOrNull { lowerVal.contains(it) }?.let { return field.value }
            }
        }

        for (line in lines.take(12)) {
            val lower = line.lowercase(Locale.ROOT)
            if (IGNORED_HELP_EXCLUSIONS.any { lower.contains(it) }) continue
            if (line.length <= 24) {
                IGNORED_STATUS_KEYWORDS.firstOrNull { kw ->
                    lower == kw || lower.startsWith("$kw ") || lower.startsWith("$kw，") || lower.endsWith(kw)
                }?.let { return line }
            }
        }
        return null
    }

    private fun resolveAmounts(lines: List<String>, fields: List<ExtractedField>): ResolvedAmountResult? {
        val candidates = mutableListOf<AmountCandidate>()
        val fieldLinesByIndex = fields.associateBy { it.lineIndex }

        for (field in fields) {
            val role = when (field.key) {
                FieldKey.ACTUAL_AMOUNT -> AmountRole.ACTUAL_PAID
                FieldKey.FOREIGN_AMOUNT -> AmountRole.FOREIGN_ORIG
                FieldKey.AMOUNT -> AmountRole.TRANSACTION_AMOUNT
                FieldKey.ORDER_AMOUNT -> AmountRole.ORDER_AMOUNT
                else -> continue
            }
            val allowBareInteger = role != AmountRole.ORDER_AMOUNT
            candidates += extractCandidatesFromText(
                text = field.value,
                role = role,
                lineIndex = field.lineIndex,
                allowBareDecimal = true,
                allowBareInteger = allowBareInteger
            )
        }

        for ((index, line) in lines.withIndex()) {
            val matchedField = fieldLinesByIndex[index]
            if (matchedField != null) {
                if (matchedField.key in SKIP_AMOUNT_SCAN_KEYS) continue
                if (matchedField.key in listOf(
                        FieldKey.ACTUAL_AMOUNT,
                        FieldKey.FOREIGN_AMOUNT,
                        FieldKey.AMOUNT,
                        FieldKey.ORDER_AMOUNT
                    )
                ) {
                    continue
                }
            }
            if (isLikelyFieldLabel(line)) continue
            if (NOISE_AMOUNT_LINE_KEYWORDS.any { line.contains(it, ignoreCase = true) }) continue

            val cleanedLine = stripNonAmountClauses(line)
            if (cleanedLine.isBlank()) continue

            val isHeroZone = index <= 8 && isStandaloneAmountLine(cleanedLine)
            val role = if (isHeroZone) AmountRole.HERO else AmountRole.GENERIC
            candidates += extractCandidatesFromText(
                text = cleanedLine,
                role = role,
                lineIndex = index,
                allowBareDecimal = isHeroZone,
                allowBareInteger = false
            )
        }

        if (candidates.isEmpty()) return null

        val foreignCandidates = candidates.filter {
            it.hasExplicitCurrency && !it.currency.equals(CurrencyCatalog.DEFAULT_BASE_CURRENCY, ignoreCase = true)
        }
        val cnyCandidates = candidates.filter {
            it.currency.equals(CurrencyCatalog.DEFAULT_BASE_CURRENCY, ignoreCase = true)
        }

        if (foreignCandidates.isNotEmpty() && cnyCandidates.isNotEmpty()) {
            val bestForeign = pickBestCandidate(foreignCandidates)
            val bestCny = pickBestCandidate(cnyCandidates)
            if (bestForeign != null && bestCny != null &&
                isPlausibleSettlementPair(bestForeign, bestCny)
            ) {
                return ResolvedAmountResult(
                    origAmountMicros = bestForeign.amountMicros,
                    origCurrency = bestForeign.currency,
                    cnySettlementMicros = bestCny.amountMicros,
                    dominantSign = bestCny.signHint ?: bestForeign.signHint
                )
            }
        }

        val bestSingle = pickBestCandidate(candidates) ?: return null
        return ResolvedAmountResult(
            origAmountMicros = bestSingle.amountMicros,
            origCurrency = bestSingle.currency,
            cnySettlementMicros = null,
            dominantSign = bestSingle.signHint
        )
    }

    private fun isPlausibleSettlementPair(foreign: AmountCandidate, cny: AmountCandidate): Boolean {
        if (cny.role == AmountRole.ACTUAL_PAID) return true
        val fallbackRate = CurrencyCatalog.find(foreign.currency).fallbackRateToCnyMicros
        val expectedCny = MoneyAmount.convert(foreign.amountMicros, fallbackRate)
        if (expectedCny <= 0L) return false
        // 允许 0.35x ~ 2.8x 弹性范围（覆盖实际汇率波动与手续费，同时排除极小优惠券误配）
        val minAllowed = (expectedCny * 35L) / 100L
        val maxAllowed = (expectedCny * 280L) / 100L
        return cny.amountMicros in minAllowed..maxAllowed
    }

    private fun pickBestCandidate(list: List<AmountCandidate>): AmountCandidate? =
        list.sortedWith(
            compareByDescending<AmountCandidate> { it.role.priority }
                .thenByDescending { it.hasExplicitCurrency }
                .thenBy { it.lineIndex }
        ).firstOrNull()

    private fun extractCandidatesFromText(
        text: String,
        role: AmountRole,
        lineIndex: Int,
        allowBareDecimal: Boolean,
        allowBareInteger: Boolean
    ): List<AmountCandidate> {
        val result = mutableListOf<AmountCandidate>()
        val sanitized = stripNonAmountClauses(text)
        for (match in AMOUNT_TOKEN_REGEX.findAll(sanitized)) {
            val sign1 = match.groupValues.getOrNull(1).orEmpty()
            val prefixCur = match.groupValues.getOrNull(2).orEmpty()
            val sign2 = match.groupValues.getOrNull(3).orEmpty()
            val numStr = match.groupValues.getOrNull(4).orEmpty()
            val suffixCur = match.groupValues.getOrNull(5).orEmpty()

            if (numStr.isBlank()) continue
            val integerPart = numStr.substringBefore('.').replace(",", "")
            if (integerPart.length > 9) continue

            val hasCurrencyToken = prefixCur.isNotBlank() || suffixCur.isNotBlank()
            val hasDecimal = numStr.contains('.')
            if (!hasCurrencyToken) {
                if (hasDecimal && !allowBareDecimal) continue
                if (!hasDecimal && !allowBareInteger) continue
                if (!hasDecimal && integerPart.length == 4 && integerPart.toIntOrNull() in 1900..2100) continue
            }

            val micros = MoneyAmount.parseToMicros(numStr) ?: continue
            if (micros <= 0L) continue

            val currency = if (hasCurrencyToken) {
                val rawToken = prefixCur.ifBlank { suffixCur }
                localRuleParser.normalizeCurrencyToken(rawToken, CurrencyCatalog.DEFAULT_BASE_CURRENCY)
            } else {
                CurrencyCatalog.detectCurrencyFromText(text, CurrencyCatalog.DEFAULT_BASE_CURRENCY)
            }
            val sign = sign1.ifBlank { sign2 }.ifBlank { null }

            result.add(
                AmountCandidate(
                    amountMicros = micros,
                    currency = currency,
                    hasExplicitCurrency = hasCurrencyToken,
                    role = role,
                    lineIndex = lineIndex,
                    signHint = sign
                )
            )
        }
        return result
    }

    private fun isStandaloneAmountLine(line: String): Boolean {
        val stripped = line.replace(WHITESPACE_RUN_REGEX, "")
        return STANDALONE_HERO_AMOUNT_REGEX.matches(stripped)
    }

    private fun stripNonAmountClauses(text: String): String = text
        .replace(DATETIME_STRIP_REGEX, " ")
        .replace(TIME_ONLY_STRIP_REGEX, " ")
        .replace(CARD_HINT_STRIP_REGEX, " ")
        .replace(DISCOUNT_BALANCE_STRIP_REGEX, " ")
        .trim()

    private fun inferTransactionType(
        lines: List<String>,
        fields: List<ExtractedField>,
        dominantSign: String?
    ): String {
        val statusText = extractFirstFieldValue(fields, FieldKey.STATUS)
        val headerSlice = lines.take(12).joinToString("\n")
        val checkSurface = "$statusText\n$headerSlice"

        // 1. 明确退款到账/已退款 -> INCOME（排除“申请退款”按钮）
        val sanitizedForRefund = REFUND_BUTTON_EXCLUSION_REGEX.replace(checkSurface, " ")
        if (REFUND_INCOME_KEYWORDS.any { sanitizedForRefund.contains(it, ignoreCase = true) } ||
            fields.any { it.key == FieldKey.TIME && it.rawLabel.contains("退款") }
        ) {
            return TransactionType.INCOME
        }

        // 2. 转账：先区分“对方已收款”（我方支出）与“你已收款/已收款”（我方收入）
        val hasCounterpartyReceived = COUNTERPARTY_RECEIVED_KEYWORDS.any {
            checkSurface.contains(it, ignoreCase = true)
        }
        if (hasCounterpartyReceived) {
            return TransactionType.EXPENSE
        }

        // 3. 明确收款 / 到账 / 收入信号 -> INCOME
        if (INCOME_SCREEN_KEYWORDS.any { checkSurface.contains(it, ignoreCase = true) } ||
            fields.any { it.key == FieldKey.TIME && it.rawLabel.contains("收款") }
        ) {
            return TransactionType.INCOME
        }

        // 4. 金额前缀正负号
        if (dominantSign == "+") return TransactionType.INCOME
        if (dominantSign == "-") return TransactionType.EXPENSE

        return TransactionType.EXPENSE
    }

    private fun extractTransactionTimestamp(
        fields: List<ExtractedField>,
        lines: List<String>,
        fallbackMillis: Long
    ): Long {
        val timeFields = fields.filter { it.key == FieldKey.TIME }
        val sortedTimeValues = timeFields.sortedBy { timeLabelPriority(it.rawLabel) }.map { it.value }
        for (raw in sortedTimeValues) {
            parseDateTimeMillis(raw, fallbackMillis)?.let { return it }
        }
        for (line in lines) {
            parseDateTimeMillis(line, fallbackMillis)?.let { return it }
        }
        return fallbackMillis
    }

    private fun timeLabelPriority(rawLabel: String): Int {
        val lower = rawLabel.lowercase(Locale.ROOT)
        return when {
            lower.contains("支付时间") || lower.contains("付款时间") || lower.contains("交易时间") ||
                lower.contains("收款时间") || lower.contains("转账时间") || lower.contains("退款时间") -> 0
            lower.contains("创建时间") || lower.contains("完成时间") || lower.contains("到账时间") -> 1
            else -> 2
        }
    }

    private fun parseDateTimeMillis(text: String, fallbackMillis: Long): Long? {
        FULL_DATETIME_REGEX.find(text)?.let { m ->
            val year = m.groupValues[1].toIntOrNull() ?: return@let null
            val month = m.groupValues[2].toIntOrNull() ?: return@let null
            val day = m.groupValues[3].toIntOrNull() ?: return@let null
            val hour = m.groupValues[4].toIntOrNull() ?: return@let null
            val minute = m.groupValues[5].toIntOrNull() ?: return@let null
            val second = m.groupValues[6].toIntOrNull() ?: 0
            return toEpochMillis(year, month, day, hour, minute, second)
        }

        MONTH_DAY_TIME_REGEX.find(text)?.let { m ->
            val month = m.groupValues[1].toIntOrNull() ?: return@let null
            val day = m.groupValues[2].toIntOrNull() ?: return@let null
            val hour = m.groupValues[3].toIntOrNull() ?: return@let null
            val minute = m.groupValues[4].toIntOrNull() ?: return@let null
            val second = m.groupValues[5].toIntOrNull() ?: 0
            val fallbackDate = Instant.ofEpochMilli(fallbackMillis).atZone(zoneId).toLocalDateTime()
            var millis = toEpochMillis(fallbackDate.year, month, day, hour, minute, second) ?: return null
            if (millis - fallbackMillis > 2 * 86_400_000L) {
                millis = toEpochMillis(fallbackDate.year - 1, month, day, hour, minute, second) ?: millis
            }
            return millis
        }
        return null
    }

    private fun toEpochMillis(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
        second: Int
    ): Long? = runCatching {
        LocalDateTime.of(year, month, day, hour, minute, second)
            .atZone(zoneId)
            .toInstant()
            .toEpochMilli()
    }.getOrNull()

    private fun extractOrderNo(fields: List<ExtractedField>): String {
        val orderFields = fields
            .filter { it.key == FieldKey.ORDER_NO }
            .sortedBy { orderLabelPriority(it.rawLabel) }
        for (field in orderFields) {
            val cleaned = cleanOrderNo(field.value)
            if (cleaned.isNotEmpty()) return cleaned
        }
        return ""
    }

    private fun orderLabelPriority(rawLabel: String): Int {
        val lower = rawLabel.lowercase(Locale.ROOT)
        return when {
            lower.contains("交易单号") || lower.contains("流水号") || lower.contains("转账单号") ||
                lower.contains("支付单号") || lower.contains("transaction") -> 0
            lower.contains("订单号") || lower.contains("订单编号") || lower.contains("order id") || lower.contains("order no") -> 1
            else -> 2
        }
    }

    private fun cleanOrderNo(raw: String): String {
        val stripped = raw
            .replace(ORDER_NO_ACTION_SUFFIX_REGEX, "")
            .replace(WHITESPACE_RUN_REGEX, "")
            .trim()
        if (stripped.length !in 6..64) return ""
        if (!ORDER_NO_VALID_REGEX.matches(stripped)) return ""
        if (!stripped.any { it.isDigit() }) return ""
        return stripped
    }

    private fun extractAccountHint(paymentMethodText: String, summaryText: String): String {
        val primary = CARD_LAST4_SCREEN_REGEX.find(paymentMethodText)?.groupValues?.getOrNull(1)?.trim().orEmpty()
        if (primary.isNotEmpty()) return primary
        val fallback = localRuleParser.extractAccountHint(paymentMethodText.ifBlank { summaryText })
        if (fallback.isNotEmpty()) return fallback
        return CARD_LAST4_SCREEN_REGEX.find(summaryText)?.groupValues?.getOrNull(1)?.trim().orEmpty()
    }

    private fun resolveScreenAccountId(
        packageName: String,
        summaryText: String,
        paymentMethodText: String,
        accountHint: String,
        routingCurrency: String,
        aliases: List<AccountAliasEntity>,
        accounts: List<AccountEntity>
    ): Long? {
        if (accounts.isEmpty()) return null
        val activeIds = accounts.map { it.id }.toSet()
        val enabledAliases = aliases
            .filter { it.enabled && it.accountId in activeIds }
            .sortedWith(compareByDescending<AccountAliasEntity> { it.priority }.thenBy { it.id })

        val cardFullText = listOf(paymentMethodText, summaryText).filter { it.isNotBlank() }.joinToString("\n")

        // 1. 卡尾号（CARD_LAST4）最高优先级路由
        val cardMatches = enabledAliases.filter { alias ->
            alias.matchType.equals(AliasMatchType.CARD_LAST4, ignoreCase = true) &&
                matchesCardLast4(alias.pattern, accountHint, cardFullText)
        }
        if (cardMatches.isNotEmpty()) {
            return cardMatches.first().accountId
        }

        // 2. 若支付方式明确命中非 PACKAGE 别名或账户名，优先于通用应用包名路由
        if (paymentMethodText.isNotBlank()) {
            for (alias in enabledAliases) {
                if (alias.matchType.equals(AliasMatchType.PACKAGE, ignoreCase = true)) continue
                val matched = when (alias.matchType.uppercase(Locale.ROOT)) {
                    AliasMatchType.REGEX -> runCatching {
                        Regex(alias.pattern, RegexOption.IGNORE_CASE).containsMatchIn(paymentMethodText)
                    }.getOrDefault(false)
                    AliasMatchType.KEYWORD -> alias.pattern.isNotBlank() &&
                        paymentMethodText.contains(alias.pattern.trim(), ignoreCase = true)
                    else -> false
                }
                if (matched) return alias.accountId
            }

            accounts.firstOrNull { acc ->
                acc.name.length >= 2 && paymentMethodText.contains(acc.name, ignoreCase = true)
            }?.let { return it.id }
        }

        // 3. 复用 LocalRuleParser 标准路由（REGEX -> KEYWORD -> PACKAGE -> 币种匹配 -> 默认账户）
        val input = CaptureInput(
            channel = CaptureChannel.SCREEN,
            sourcePackage = packageName,
            title = "",
            content = cardFullText
        )
        return localRuleParser.resolveAccountId(
            input = input,
            fullText = cardFullText,
            accountHint = accountHint,
            currency = routingCurrency,
            aliases = aliases,
            accounts = accounts
        )
    }

    private fun matchesCardLast4(pattern: String, accountHint: String, fullText: String): Boolean {
        val clean = pattern.trim()
        if (clean.isEmpty()) return false
        if (accountHint.isNotBlank() && accountHint.equals(clean, ignoreCase = true)) return true
        val escaped = Regex.escape(clean)
        val regex = Regex("(?:尾号|末四位|卡号[*xX]*|[*]{2,}|[(（]|ending\\s+in)\\s*$escaped\\b", RegexOption.IGNORE_CASE)
        return regex.containsMatchIn(fullText)
    }

    private fun resolveMerchant(
        lines: List<String>,
        fields: List<ExtractedField>,
        txType: String
    ): String {
        val explicitShort = fields
            .filter { it.key == FieldKey.MERCHANT }
            .mapNotNull { cleanMerchantCandidate(it.value) }
            .firstOrNull { !isGenericChannelMerchant(it) }
        if (!explicitShort.isNullOrBlank()) return explicitShort

        val heroMerchant = extractHeroMerchant(lines)
        if (!heroMerchant.isNullOrBlank() && !isGenericChannelMerchant(heroMerchant)) {
            return heroMerchant
        }

        val payee = fields
            .filter { it.key == FieldKey.PAYEE || (txType == TransactionType.INCOME && it.key == FieldKey.PAYER) }
            .mapNotNull { cleanMerchantCandidate(it.value) }
            .firstOrNull { !isGenericChannelMerchant(it) }
        if (!payee.isNullOrBlank()) return payee

        val goods = fields
            .filter { it.key == FieldKey.GOODS }
            .mapNotNull { cleanMerchantCandidate(it.value) }
            .firstOrNull { !isGenericChannelMerchant(it) }
        if (!goods.isNullOrBlank()) return goods

        return heroMerchant.orEmpty()
    }

    private fun extractHeroMerchant(lines: List<String>): String? {
        for (line in lines.take(10)) {
            if (isLikelyFieldLabel(line)) break
            if (findKeySeparatorIndex(line) > 0) {
                val label = line.substring(0, findKeySeparatorIndex(line))
                if (matchFieldKey(label) != null) break
            }
            if (NOISE_MERCHANT_LINE_REGEX.containsMatchIn(line)) continue
            if (isStandaloneAmountLine(line)) continue
            if (FULL_DATETIME_REGEX.containsMatchIn(line) || TIME_ONLY_STRIP_REGEX.matches(line.trim())) continue

            val cleaned = cleanMerchantCandidate(line) ?: continue
            if (cleaned.isNotBlank()) return cleaned
        }
        return null
    }

    private fun cleanMerchantCandidate(raw: String): String? {
        var s = raw.trim()
        if (s.isEmpty()) return null
        if (isLikelyFieldLabel(s)) return null

        s = s.replace(MERCHANT_ACTION_PREFIX_REGEX, "")
            .replace(MERCHANT_ACTION_SUFFIX_REGEX, "")
            .replace(Regex("^[：:，,。\\-—\\s]+|[：:，,。.;；\\s]+$"), "")
            .trim()
        if (s.isEmpty() || s.length > 40) return null

        val lower = s.lowercase(Locale.ROOT)
        if (lower in UI_STOP_WORDS) return null
        if (UI_STOP_PREFIXES.any { lower.startsWith(it) }) return null
        if (PAY_SUCCESS_PHRASES.any { lower == it }) return null
        if (IGNORED_STATUS_KEYWORDS.any { lower == it }) return null
        if (PURE_NUMBER_OR_CARD_REGEX.matches(s)) return null
        if (AMOUNT_ONLY_LINE_REGEX.matches(s.replace(" ", ""))) return null
        if (FULL_DATETIME_REGEX.containsMatchIn(s) || MONTH_DAY_TIME_REGEX.containsMatchIn(s)) return null
        return s
    }

    private fun isGenericChannelMerchant(merchant: String): Boolean =
        merchant.trim().lowercase(Locale.ROOT) in GENERIC_CHANNEL_MERCHANTS

    private fun extractFirstFieldValue(fields: List<ExtractedField>, key: FieldKey): String =
        fields.firstOrNull { it.key == key }?.value?.trim().orEmpty()

    private fun buildSummaryText(lines: List<String>): String {
        val filtered = lines.filterNot { line ->
            line.trim().lowercase(Locale.ROOT) in CHROME_NOISE_LINES
        }
        val joined = filtered.joinToString("\n")
        return if (joined.length <= 1200) joined else joined.take(1197) + "..."
    }

    private fun buildCompactNote(
        goodsDesc: String,
        merchant: String,
        paymentMethodText: String,
        orderNo: String
    ): String {
        val parts = buildList {
            if (goodsDesc.isNotBlank() && !goodsDesc.equals(merchant, ignoreCase = true)) {
                add(goodsDesc.take(40))
            }
            if (paymentMethodText.isNotBlank()) {
                add(paymentMethodText.take(28))
            }
            if (orderNo.isNotBlank()) {
                add("单号:$orderNo")
            }
        }
        val joined = parts.joinToString(" · ")
        return if (joined.length <= 96) joined else joined.take(93) + "..."
    }

    companion object {
        private const val PKG_WECHAT = "com.tencent.mm"
        private const val PKG_ALIPAY = "com.eg.android.AlipayGphone"

        private val WATCHED_EXACT_PACKAGES: Set<String> = hashSetOf(
            PKG_WECHAT,
            PKG_ALIPAY,
            "com.unionpay",
            "com.jd.jrapp",
            "com.sankuai.meituan",
            "cmb.pb",
            "com.icbc",
            "com.icbc.androidclient",
            "com.chinamworld.main",
            "com.ccb.longzhifu",
            "com.android.bankabc",
            "com.chinamworld.bocmbci",
            "com.boc.bocsoft.mobilebanking",
            "com.bankcomm.Bankcomm",
            "com.yitong.mbank.psbc",
            "com.ecitic.bank.mobile",
            "com.spdb.mobilebank.per",
            "com.cib.cibmb",
            "cn.com.cmbc.newmbank",
            "com.cebbank.mobile.cemb",
            "com.pingan.paces.ccms",
            "com.cgbchina.xpt",
            "com.hxb.mobile.client",
            "com.webank.wemoney",
            "com.mybank.android.phone",
            "com.transferwise.android",
            "com.revolut.revolut",
            "com.paypal.android.p2pmobile",
            "com.android.vending",
            "com.google.android.apps.walletnfcrel",
            "com.hsbc.hsbcchina",
            "uk.co.hsbc.hsbcukmobilebanking",
            "com.hk.hsbc.mobile",
            "com.citi.citimobile",
            "com.chase.sig.android",
            "co.uk.getmondo",
            "de.number26.android",
            "hk.alipay.wallet",
            "com.octopuscards.nfc_reader",
            "com.starlingbank.android"
        )

        private val WATCHED_PACKAGE_PREFIXES = arrayOf(
            "com.cmbchina.",
            "com.icbc.",
            "com.pingan.",
            "com.wise.",
            "com.paypal.",
            "com.revolut.",
            "com.hsbc.",
            "uk.co.hsbc.",
            "com.hk.hsbc.",
            "com.citi.",
            "com.chase.",
            "com.monzo."
        )

        /** O(1) 包名白名单判断，零内存分配，适合无障碍高频入口。 */
        fun isWatchedPackage(packageName: String): Boolean {
            if (packageName.isEmpty()) return false
            if (packageName in WATCHED_EXACT_PACKAGES) return true
            for (prefix in WATCHED_PACKAGE_PREFIXES) {
                if (packageName.startsWith(prefix)) return true
            }
            return false
        }

        private val WHITESPACE_RUN_REGEX = Regex("\\s+")

        private val SPLIT_CURRENCY_ONLY_REGEX = Regex(
            "^(?:US\\$|HK\\$|NT\\$|S\\$|A\\$|C\\$|JP¥|USD|EUR|GBP|HKD|JPY|SGD|AUD|CAD|CHF|TWD|KRW|CNY|RMB|\\$|€|£|¥|￥|₩)$",
            RegexOption.IGNORE_CASE
        )

        private val SPLIT_PREFIX_REGEX = Regex(
            "^(?:[+-]|[+-]?(?:US\\$|HK\\$|NT\\$|S\\$|A\\$|C\\$|JP¥|USD|EUR|GBP|HKD|JPY|SGD|AUD|CAD|CHF|TWD|KRW|CNY|RMB|\\$|€|£|¥|￥|₩))$",
            RegexOption.IGNORE_CASE
        )

        private val SPLIT_NUMBER_REGEX = Regex("^[0-9]+(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?$")
        private val SPLIT_SIGNED_NUMBER_REGEX = Regex("^[+-]?[0-9]+(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?$")
        private val SPLIT_SUFFIX_CURRENCY_REGEX = Regex(
            "^(?:USD|EUR|GBP|HKD|JPY|SGD|AUD|CAD|CHF|TWD|KRW|CNY|RMB|元|美元|美金|欧元|英镑|港币|港元|日元|新元|新加坡元|澳元|加元|瑞郎|新台币|韩元)$",
            RegexOption.IGNORE_CASE
        )

        private val FIELD_KEY_MAP: Map<String, FieldKey> = mapOf(
            "当前状态" to FieldKey.STATUS,
            "交易状态" to FieldKey.STATUS,
            "订单状态" to FieldKey.STATUS,
            "状态" to FieldKey.STATUS,
            "status" to FieldKey.STATUS,

            "支付时间" to FieldKey.TIME,
            "付款时间" to FieldKey.TIME,
            "交易时间" to FieldKey.TIME,
            "创建时间" to FieldKey.TIME,
            "交易创建时间" to FieldKey.TIME,
            "收款时间" to FieldKey.TIME,
            "转账时间" to FieldKey.TIME,
            "退款时间" to FieldKey.TIME,
            "到账时间" to FieldKey.TIME,
            "完成时间" to FieldKey.TIME,
            "transaction time" to FieldKey.TIME,
            "payment time" to FieldKey.TIME,
            "date" to FieldKey.TIME,

            "支付方式" to FieldKey.PAYMENT_METHOD,
            "付款方式" to FieldKey.PAYMENT_METHOD,
            "扣款方式" to FieldKey.PAYMENT_METHOD,
            "收款方式" to FieldKey.PAYMENT_METHOD,
            "付款账号" to FieldKey.PAYMENT_METHOD,
            "付款卡号" to FieldKey.PAYMENT_METHOD,
            "转出账户" to FieldKey.PAYMENT_METHOD,
            "扣款账户" to FieldKey.PAYMENT_METHOD,
            "退款去向" to FieldKey.PAYMENT_METHOD,
            "退回账户" to FieldKey.PAYMENT_METHOD,
            "退回方式" to FieldKey.PAYMENT_METHOD,
            "入账方式" to FieldKey.PAYMENT_METHOD,
            "入账账户" to FieldKey.PAYMENT_METHOD,
            "payment method" to FieldKey.PAYMENT_METHOD,
            "paid with" to FieldKey.PAYMENT_METHOD,

            "交易单号" to FieldKey.ORDER_NO,
            "交易订单号" to FieldKey.ORDER_NO,
            "银行流水号" to FieldKey.ORDER_NO,
            "流水号" to FieldKey.ORDER_NO,
            "转账单号" to FieldKey.ORDER_NO,
            "支付单号" to FieldKey.ORDER_NO,
            "订单号" to FieldKey.ORDER_NO,
            "订单编号" to FieldKey.ORDER_NO,
            "商户单号" to FieldKey.ORDER_NO,
            "商户订单号" to FieldKey.ORDER_NO,
            "transaction id" to FieldKey.ORDER_NO,
            "transaction no" to FieldKey.ORDER_NO,
            "order id" to FieldKey.ORDER_NO,
            "order no" to FieldKey.ORDER_NO,
            "merchant order no" to FieldKey.ORDER_NO,

            "商户" to FieldKey.MERCHANT,
            "商户名称" to FieldKey.MERCHANT,
            "商户全称" to FieldKey.MERCHANT,
            "商家" to FieldKey.MERCHANT,
            "商家名称" to FieldKey.MERCHANT,
            "收款商户" to FieldKey.MERCHANT,
            "交易对方" to FieldKey.MERCHANT,
            "转账对象" to FieldKey.MERCHANT,
            "merchant" to FieldKey.MERCHANT,
            "payee" to FieldKey.MERCHANT,
            "recipient" to FieldKey.MERCHANT,

            "收款方全称" to FieldKey.PAYEE,
            "收款方" to FieldKey.PAYEE,
            "收款人" to FieldKey.PAYEE,
            "对方账户" to FieldKey.PAYEE,
            "对方户名" to FieldKey.PAYEE,

            "付款方" to FieldKey.PAYER,
            "转出方" to FieldKey.PAYER,

            "商品" to FieldKey.GOODS,
            "商品说明" to FieldKey.GOODS,
            "商品名称" to FieldKey.GOODS,
            "商品详情" to FieldKey.GOODS,
            "付款说明" to FieldKey.GOODS,
            "转账说明" to FieldKey.GOODS,
            "交易说明" to FieldKey.GOODS,
            "备注" to FieldKey.GOODS,
            "收款方备注" to FieldKey.GOODS,
            "description" to FieldKey.GOODS,
            "item" to FieldKey.GOODS,

            "实付金额" to FieldKey.ACTUAL_AMOUNT,
            "实付款" to FieldKey.ACTUAL_AMOUNT,
            "实际付款" to FieldKey.ACTUAL_AMOUNT,
            "实付" to FieldKey.ACTUAL_AMOUNT,
            "付款金额" to FieldKey.ACTUAL_AMOUNT,
            "支付金额" to FieldKey.ACTUAL_AMOUNT,
            "扣款金额" to FieldKey.ACTUAL_AMOUNT,
            "折算人民币" to FieldKey.ACTUAL_AMOUNT,
            "折合人民币" to FieldKey.ACTUAL_AMOUNT,
            "人民币金额" to FieldKey.ACTUAL_AMOUNT,
            "入账金额" to FieldKey.ACTUAL_AMOUNT,
            "paid amount" to FieldKey.ACTUAL_AMOUNT,
            "charged amount" to FieldKey.ACTUAL_AMOUNT,

            "原始金额" to FieldKey.FOREIGN_AMOUNT,
            "外币金额" to FieldKey.FOREIGN_AMOUNT,
            "原币金额" to FieldKey.FOREIGN_AMOUNT,
            "消费外币" to FieldKey.FOREIGN_AMOUNT,
            "交易币种金额" to FieldKey.FOREIGN_AMOUNT,
            "original amount" to FieldKey.FOREIGN_AMOUNT,

            "金额" to FieldKey.AMOUNT,
            "交易金额" to FieldKey.AMOUNT,
            "消费金额" to FieldKey.AMOUNT,
            "转账金额" to FieldKey.AMOUNT,
            "收款金额" to FieldKey.AMOUNT,
            "退款金额" to FieldKey.AMOUNT,
            "退回金额" to FieldKey.AMOUNT,
            "账单金额" to FieldKey.AMOUNT,
            "amount" to FieldKey.AMOUNT,

            "订单金额" to FieldKey.ORDER_AMOUNT,
            "原价" to FieldKey.ORDER_AMOUNT,

            "当前余额" to FieldKey.IGNORED_AMOUNT,
            "账户余额" to FieldKey.IGNORED_AMOUNT,
            "可用余额" to FieldKey.IGNORED_AMOUNT,
            "余额" to FieldKey.IGNORED_AMOUNT,
            "汇率" to FieldKey.IGNORED_AMOUNT,
            "参考汇率" to FieldKey.IGNORED_AMOUNT,
            "优惠" to FieldKey.IGNORED_AMOUNT,
            "优惠金额" to FieldKey.IGNORED_AMOUNT,
            "立减" to FieldKey.IGNORED_AMOUNT,
            "立减金额" to FieldKey.IGNORED_AMOUNT,
            "红包" to FieldKey.IGNORED_AMOUNT,
            "红包抵扣" to FieldKey.IGNORED_AMOUNT,
            "折扣" to FieldKey.IGNORED_AMOUNT,
            "积分" to FieldKey.IGNORED_AMOUNT,
            "手续费" to FieldKey.IGNORED_AMOUNT,
            "服务费" to FieldKey.IGNORED_AMOUNT
        )

        private val SPACE_SEPARATED_PREFIXES: List<Pair<String, FieldKey>> = listOf(
            "消费金额" to FieldKey.AMOUNT,
            "交易金额" to FieldKey.AMOUNT,
            "转账金额" to FieldKey.AMOUNT,
            "收款金额" to FieldKey.AMOUNT,
            "退款金额" to FieldKey.AMOUNT,
            "外币金额" to FieldKey.FOREIGN_AMOUNT,
            "原币金额" to FieldKey.FOREIGN_AMOUNT,
            "原始金额" to FieldKey.FOREIGN_AMOUNT,
            "实付金额" to FieldKey.ACTUAL_AMOUNT,
            "付款金额" to FieldKey.ACTUAL_AMOUNT,
            "支付金额" to FieldKey.ACTUAL_AMOUNT,
            "折算人民币" to FieldKey.ACTUAL_AMOUNT,
            "折合人民币" to FieldKey.ACTUAL_AMOUNT,
            "实付" to FieldKey.ACTUAL_AMOUNT,
            "支付方式" to FieldKey.PAYMENT_METHOD,
            "付款方式" to FieldKey.PAYMENT_METHOD,
            "交易单号" to FieldKey.ORDER_NO,
            "订单号" to FieldKey.ORDER_NO,
            "商户单号" to FieldKey.ORDER_NO,
            "支付时间" to FieldKey.TIME,
            "交易时间" to FieldKey.TIME,
            "创建时间" to FieldKey.TIME
        )

        private val DETAIL_SIGNAL_KEYS = setOf(
            FieldKey.STATUS,
            FieldKey.TIME,
            FieldKey.PAYMENT_METHOD,
            FieldKey.ORDER_NO,
            FieldKey.GOODS,
            FieldKey.MERCHANT,
            FieldKey.PAYEE,
            FieldKey.ACTUAL_AMOUNT,
            FieldKey.FOREIGN_AMOUNT
        )

        private val SKIP_AMOUNT_SCAN_KEYS = setOf(
            FieldKey.ORDER_NO,
            FieldKey.TIME,
            FieldKey.PAYMENT_METHOD,
            FieldKey.STATUS,
            FieldKey.IGNORED_AMOUNT,
            FieldKey.MERCHANT,
            FieldKey.PAYEE,
            FieldKey.PAYER
        )

        private val ACTION_ONLY_WORDS = setOf(
            "复制", "copy", "查看", "更多", "完成", "done", "返回", "关闭"
        )

        private val CHAT_STRONG_MARKERS = listOf(
            "按住 说话", "按住说话", "切换到按住说话", "更多功能按钮", "语音输入", "聊天信息", "置顶聊天", "消息免打扰"
        )

        private val BILL_LIST_MARKERS = listOf(
            "全部账单", "月账单", "账单筛选", "收支分析", "账单统计", "筛选", "本月账单"
        )

        private val PREPAY_CHECKOUT_MARKERS = listOf(
            "确认付款", "确认支付", "立即支付", "立即付款", "去付款", "去支付",
            "选择付款方式", "请输入支付密码", "确认交易", "提交订单", "待付款"
        )

        private val DETAIL_HEADER_KEYWORDS = listOf(
            "账单详情", "交易详情", "收支详情", "转账详情", "收款详情",
            "退款详情", "订单详情", "交易明细", "账单明细", "电子回单",
            "transaction details", "payment details", "transfer details"
        )

        private val PAY_SUCCESS_PHRASES = listOf(
            "支付成功", "付款成功", "交易成功", "消费成功", "转账成功",
            "汇款成功", "收款成功", "已收款", "已存入零钱", "免密支付成功",
            "扣款成功", "退款成功", "已退款", "退款到账", "已退回",
            "payment successful", "transfer successful", "transaction successful", "money received"
        )

        private val DONE_ANCHOR_KEYWORDS = listOf(
            "完成", "返回商家", "查看账单", "查看零钱", "账单详情", "查看详情",
            "done", "ok", "close", "返回", "电子回单"
        )

        private val BANK_RESULT_ANCHORS = listOf(
            "电子回单", "转账回单", "交易回单", "付款账号", "收款方", "交易金额", "支付结果", "付款结果"
        )

        private val IGNORED_STATUS_KEYWORDS = listOf(
            "交易关闭", "已关闭", "支付失败", "付款失败", "交易失败", "扣款失败", "转账失败",
            "订单已取消", "已取消", "已撤单", "等待付款", "等待买家付款", "待支付", "待付款",
            "未支付", "已撤销", "已作废",
            "payment failed", "transaction failed", "order cancelled", "payment pending", "transaction closed"
        )

        private val IGNORED_HELP_EXCLUSIONS = listOf(
            "怎么办", "如何", "常见", "帮助", "疑问", "客服"
        )

        private val REFUND_BUTTON_EXCLUSION_REGEX = Regex("(?:申请退款|我要退款|申请售后|退款记录)")

        private val REFUND_INCOME_KEYWORDS = listOf(
            "退款成功", "已退款", "退款到账", "已退回", "原路退回",
            "退款金额", "退款详情", "已全额退款", "部分已退", "refunded", "refund successful"
        )

        private val COUNTERPARTY_RECEIVED_KEYWORDS = listOf(
            "对方已收款", "对方已收钱", "等待对方收款", "待对方收款", "对方已领"
        )

        private val INCOME_SCREEN_KEYWORDS = listOf(
            "已收款", "收款成功", "收款金额", "二维码收款", "赞赏码收款",
            "收到转账", "已存入零钱", "到账成功", "已到账", "入账成功",
            "工资", "分红", "返现", "收到红包", "红包已领取",
            "you received", "money received"
        )

        private val NOISE_AMOUNT_LINE_KEYWORDS = listOf(
            "立减", "优惠", "满减", "红包", "积分", "当前余额", "可用余额", "账户余额", "参考汇率", "汇率", "手续费", "费率"
        )

        private val AMOUNT_TOKEN_REGEX = Regex(
            "([+-])?\\s*" +
                "(US\\$|HK\\$|NT\\$|S\\$|A\\$|C\\$|JP¥|USD|EUR|GBP|HKD|JPY|SGD|AUD|CAD|CHF|TWD|KRW|CNY|RMB|人民币|美元|美金|欧元|英镑|港币|港元|日元|新元|新加坡元|澳元|加元|瑞郎|新台币|韩元|\\$|€|£|¥|￥|₩)?\\s*" +
                "([+-])?\\s*" +
                "([0-9]+(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?)\\s*" +
                "(USD|EUR|GBP|HKD|JPY|SGD|AUD|CAD|CHF|TWD|KRW|CNY|RMB|元|美元|美金|欧元|英镑|港币|港元|日元|新元|新加坡元|澳元|加元|瑞郎|新台币|韩元)?",
            RegexOption.IGNORE_CASE
        )

        private val STANDALONE_HERO_AMOUNT_REGEX = Regex(
            "^[+-]?(?:US\\$|HK\\$|NT\\$|S\\$|A\\$|C\\$|JP¥|USD|EUR|GBP|HKD|JPY|SGD|AUD|CAD|CHF|TWD|KRW|CNY|RMB|\\$|€|£|¥|￥|₩)?[+-]?[0-9]+(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?(?:USD|EUR|GBP|HKD|JPY|SGD|AUD|CAD|CHF|TWD|KRW|CNY|RMB|元|美元|美金|欧元|英镑|港币|港元|日元|新元|新加坡元|澳元|加元|瑞郎|新台币|韩元)?$",
            RegexOption.IGNORE_CASE
        )

        private val AMOUNT_ONLY_LINE_REGEX = STANDALONE_HERO_AMOUNT_REGEX

        private val DATETIME_STRIP_REGEX = Regex(
            "\\b\\d{4}[-/.年]\\d{1,2}[-/.月]\\d{1,2}日?(?:\\s*\\d{1,2}:\\d{2}(?::\\d{2})?)?"
        )
        private val TIME_ONLY_STRIP_REGEX = Regex("^\\d{1,2}:\\d{2}(?::\\d{2})?$")
        private val CARD_HINT_STRIP_REGEX = Regex(
            "(?:尾号|末四位|卡号[*xX]*|[*]{2,}|[(（]|ending\\s+in)\\s*\\d{4}[)）]?",
            RegexOption.IGNORE_CASE
        )
        private val DISCOUNT_BALANCE_STRIP_REGEX = Regex(
            "(?:当前余额|可用余额|账户余额|余额|汇率|参考汇率|立减|优惠|已优惠)[:：]?\\s*(?:USD|EUR|GBP|CNY|RMB|[¥￥$€£])?\\s*[0-9]+(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?\\s*(?:元)?",
            RegexOption.IGNORE_CASE
        )

        private val FULL_DATETIME_REGEX = Regex(
            "(\\d{4})[-/.年](\\d{1,2})[-/.月](\\d{1,2})日?\\s*(\\d{1,2}):(\\d{2})(?::(\\d{2}))?"
        )
        private val MONTH_DAY_TIME_REGEX = Regex(
            "(?<!\\d)(\\d{1,2})[-/.月](\\d{1,2})日?\\s*(\\d{1,2}):(\\d{2})(?::(\\d{2}))?"
        )

        private val CARD_LAST4_SCREEN_REGEX = Regex(
            "(?:尾号|末四位|卡号[*xX]*|[*]{2,}|[(（]|ending\\s+in)\\s*(\\d{4})\\b",
            RegexOption.IGNORE_CASE
        )

        private val ORDER_NO_ACTION_SUFFIX_REGEX = Regex("(?:复制|已复制|copy|查看)$", RegexOption.IGNORE_CASE)
        private val ORDER_NO_VALID_REGEX = Regex("^[A-Za-z0-9_-]{6,64}$")

        private val NOISE_MERCHANT_LINE_REGEX = Regex(
            "^(?:消费金额|外币金额|原币金额|原始金额|实付|实付款|付款金额|支付金额|折算人民币|折合人民币|优惠|立减|红包|当前余额|可用余额|余额|尾号|卡号)"
        )
        private val MERCHANT_ACTION_PREFIX_REGEX = Regex("^(?:向|给|付款给|转账给|转给|在)\\s*")
        private val MERCHANT_ACTION_SUFFIX_REGEX = Regex("\\s*(?:付款|支付|转账|消费|收款)$")
        private val PURE_NUMBER_OR_CARD_REGEX = Regex("^[\\d*xX()（）\\s-]+$")

        private val UI_STOP_WORDS = setOf(
            "账单详情", "交易详情", "收支详情", "转账详情", "收款详情", "退款详情", "订单详情",
            "交易明细", "账单明细", "电子回单", "支付结果", "付款结果", "交易结果",
            "支付成功", "付款成功", "交易成功", "消费成功", "转账成功", "汇款成功", "收款成功",
            "已收款", "已存入零钱", "退款成功", "已退款", "退款到账", "已退回",
            "完成", "返回", "关闭", "确定", "确认", "取消", "返回商家", "查看账单", "查看详情",
            "查看零钱", "复制", "分享", "截屏", "投诉", "联系客服", "联系收款方", "常见问题",
            "对订单有疑惑", "对此订单有疑问", "申请退款", "申请售后", "申请开票", "在此商户的交易记录",
            "更多", "删除账单", "标签和备注", "添加备注", "分类", "计入收支", "不计入收支",
            "微信", "微信支付", "微信收款助手", "支付宝", "云闪付", "招商银行", "工商银行", "建设银行",
            "农业银行", "中国银行", "交通银行", "平安银行", "wise", "paypal", "revolut",
            "支付", "收款", "转账", "付款", "人民币", "美元", "欧元", "英镑", "done", "ok", "close"
        )

        private val UI_STOP_PREFIXES = listOf(
            "对订单有疑", "对此订单有疑", "在此商户的", "联系收款方", "申请开票", "申请退款", "申请售后", "查看"
        )

        private val GENERIC_CHANNEL_MERCHANTS = setOf(
            "微信支付", "支付宝", "云闪付", "扫码付款", "二维码收款", "转账", "收款", "消费", "支付"
        )

        private val CHROME_NOISE_LINES = setOf(
            "返回", "复制", "已复制", "copy", "常见问题", "对订单有疑惑", "对此订单有疑问",
            "在此商户的交易记录", "申请开票", "标签和备注", "添加备注", "删除账单"
        )
    }
}

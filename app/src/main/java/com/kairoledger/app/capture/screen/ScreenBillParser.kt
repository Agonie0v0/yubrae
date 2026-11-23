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
        IGNORED_AMOUNT,
        META
    }

    private data class ExtractedField(
        val key: FieldKey,
        val rawLabel: String,
        val value: String,
        val lineIndex: Int,
        val labelLineIndex: Int = lineIndex
    )

    private data class FieldExtractionResult(
        val fields: List<ExtractedField>,
        val ignoredLineIndices: Set<Int>
    )

    private data class NormalizedLine(
        val text: String,
        val topPx: Int = -1,
        val heightPx: Int = 0,
        val isContentDesc: Boolean = false
    )

    private enum class AmountRole(val priority: Int) {
        HERO_VISUAL(5),
        HERO(4),
        ACTUAL_PAID(3),
        FOREIGN_ORIG(3),
        TRANSACTION_AMOUNT(2),
        ORDER_AMOUNT(1),
        GENERIC(0)
    }

    private data class AmountCandidate(
        val amountMicros: Long,
        val currency: String,
        val hasExplicitCurrency: Boolean,
        val role: AmountRole,
        val lineIndex: Int,
        val signHint: String?,
        val heightPx: Int = 0
    )

    private data class ResolvedAmountResult(
        val origAmountMicros: Long,
        val origCurrency: String,
        val cnySettlementMicros: Long?,
        val dominantSign: String?
    )

    fun classifyPage(snapshot: ScreenSnapshot): ScreenPageKind {
        if (!isWatchedPackage(snapshot.packageName)) return ScreenPageKind.UNKNOWN
        if (isBlockedChatOrFeedWindow(snapshot.packageName, snapshot.className)) {
            return ScreenPageKind.UNKNOWN
        }
        val normalizedLines = normalizeLinesWithNodes(snapshot)
        if (normalizedLines.isEmpty()) return ScreenPageKind.UNKNOWN
        val lines = normalizedLines.map { it.text }
        val fieldResult = extractFields(lines)
        return classifyPageInternal(snapshot.packageName, snapshot.className, lines, fieldResult.fields).first
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
        if (isBlockedChatOrFeedWindow(snapshot.packageName, snapshot.className)) {
            return ScreenParseOutcome(
                pageKind = ScreenPageKind.UNKNOWN,
                draft = null,
                ignoredReason = "聊天或服务号对话框不触发"
            )
        }

        val normalizedLines = normalizeLinesWithNodes(snapshot)
        if (normalizedLines.isEmpty()) {
            return ScreenParseOutcome(pageKind = ScreenPageKind.UNKNOWN, draft = null)
        }
        val lines = normalizedLines.map { it.text }

        val fieldResult = extractFields(lines)
        val fields = fieldResult.fields
        val (pageKind, prepayReason) = classifyPageInternal(
            packageName = snapshot.packageName,
            className = snapshot.className,
            lines = lines,
            fields = fields
        )
        val summaryText = buildSummaryText(lines)
        val orderNo = extractOrderNo(fields)

        if (pageKind == ScreenPageKind.UNKNOWN && prepayReason == "聊天或服务号对话框不触发") {
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

        val resolvedAmount = resolveAmounts(
            lines = lines,
            normalizedLines = normalizedLines,
            fields = fields,
            ignoredLineIndices = fieldResult.ignoredLineIndices,
            screenHeightPx = snapshot.screenHeightPx
        )
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
        val paymentMethodText = sanitizePaymentMethodText(extractFirstFieldValue(fields, FieldKey.PAYMENT_METHOD))
        val accountHint = extractAccountHint(paymentMethodText, summaryText)
        val merchant = resolveMerchant(lines, fields, txType)
        val goodsDesc = extractGoods(fields)

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

    private fun cleanLineText(text: String): String = text
        .replace('\u00A0', ' ')
        .replace("\u200B", "")
        .replace("\uFEFF", "")
        .replace(WHITESPACE_RUN_REGEX, " ")
        .trim()

    private fun normalizeLinesWithNodes(snapshot: ScreenSnapshot): List<NormalizedLine> {
        val raw = buildRawNormalizedLines(snapshot)
        if (raw.isEmpty()) return emptyList()

        // Pass 1：合并整数/小数点拆分节点，如 "-26"+".80"、"+15"+".00"、"26."+"80"
        val decimalMerged = ArrayList<NormalizedLine>(raw.size)
        var i = 0
        while (i < raw.size) {
            val curr = raw[i]
            val next = raw.getOrNull(i + 1)
            if (next != null &&
                ((SPLIT_INT_PART_REGEX.matches(curr.text) && SPLIT_DOT_DEC_REGEX.matches(next.text)) ||
                    (SPLIT_INT_DOT_REGEX.matches(curr.text) && SPLIT_DEC_DIGITS_REGEX.matches(next.text)))
            ) {
                decimalMerged.add(combineNormalizedLines("${curr.text}${next.text}", listOf(curr, next)))
                i += 2
                continue
            }
            decimalMerged.add(curr)
            i++
        }

        // Pass 2：合并正负号、货币符号与后缀币种单位，并保留几何边界
        val merged = ArrayList<NormalizedLine>(decimalMerged.size)
        i = 0
        while (i < decimalMerged.size) {
            val curr = decimalMerged[i]
            val next = decimalMerged.getOrNull(i + 1)
            val third = decimalMerged.getOrNull(i + 2)

            if (next != null && third != null &&
                (curr.text == "-" || curr.text == "+") &&
                SPLIT_CURRENCY_ONLY_REGEX.matches(next.text) &&
                SPLIT_NUMBER_REGEX.matches(third.text)
            ) {
                val combined = if (next.text.all { it.isLetter() }) {
                    "${curr.text}${next.text} ${third.text}"
                } else {
                    "${curr.text}${next.text}${third.text}"
                }
                addOrCoalesceLine(merged, combineNormalizedLines(combined, listOf(curr, next, third)))
                i += 3
                continue
            }

            if (next != null && SPLIT_PREFIX_REGEX.matches(curr.text) && SPLIT_NUMBER_REGEX.matches(next.text)) {
                val combined = if (curr.text.all { it.isLetter() }) {
                    "${curr.text} ${next.text}"
                } else {
                    "${curr.text}${next.text}"
                }
                addOrCoalesceLine(merged, combineNormalizedLines(combined, listOf(curr, next)))
                i += 2
                continue
            }

            if (next != null && SPLIT_SIGNED_NUMBER_REGEX.matches(curr.text) && SPLIT_SUFFIX_CURRENCY_REGEX.matches(next.text)) {
                val combined = "${curr.text} ${next.text}"
                addOrCoalesceLine(merged, combineNormalizedLines(combined, listOf(curr, next)))
                i += 2
                continue
            }

            addOrCoalesceLine(merged, curr)
            i++
        }
        return merged
    }

    private fun buildRawNormalizedLines(snapshot: ScreenSnapshot): List<NormalizedLine> {
        if (snapshot.texts.isEmpty()) {
            return snapshot.nodes.mapNotNull { node ->
                val cleaned = cleanLineText(node.text)
                cleaned.takeIf { it.isNotEmpty() }?.let {
                    NormalizedLine(it, node.topPx, node.heightPx, node.isContentDesc)
                }
            }
        }
        if (snapshot.nodes.isEmpty()) {
            return snapshot.texts.mapNotNull { text ->
                val cleaned = cleanLineText(text)
                cleaned.takeIf { it.isNotEmpty() }?.let { NormalizedLine(it) }
            }
        }

        val cleanedNodes = snapshot.nodes.mapNotNull { node ->
            val cleaned = cleanLineText(node.text)
            cleaned.takeIf { it.isNotEmpty() }?.let {
                NormalizedLine(it, node.topPx, node.heightPx, node.isContentDesc)
            }
        }
        var nodeCursor = 0
        return snapshot.texts.mapNotNull { rawText ->
            val cleaned = cleanLineText(rawText)
            if (cleaned.isEmpty()) return@mapNotNull null
            var matched: NormalizedLine? = null
            val limit = minOf(nodeCursor + 6, cleanedNodes.size)
            for (k in nodeCursor until limit) {
                if (cleanedNodes[k].text == cleaned) {
                    matched = cleanedNodes[k]
                    nodeCursor = k + 1
                    break
                }
            }
            matched ?: NormalizedLine(cleaned)
        }
    }

    private fun combineNormalizedLines(text: String, parts: List<NormalizedLine>): NormalizedLine {
        var minTop = Int.MAX_VALUE
        var maxHeight = 0
        var isDesc = false
        for (part in parts) {
            if (part.topPx >= 0 && part.topPx < minTop) minTop = part.topPx
            if (part.heightPx > maxHeight) maxHeight = part.heightPx
            if (part.isContentDesc) isDesc = true
        }
        return NormalizedLine(
            text = text,
            topPx = if (minTop == Int.MAX_VALUE) -1 else minTop,
            heightPx = maxHeight,
            isContentDesc = isDesc
        )
    }

    private fun addOrCoalesceLine(merged: MutableList<NormalizedLine>, item: NormalizedLine) {
        val last = merged.lastOrNull()
        if (last != null && last.text == item.text) {
            merged[merged.lastIndex] = combineNormalizedLines(item.text, listOf(last, item))
        } else {
            merged.add(item)
        }
    }

    private fun extractFields(lines: List<String>): FieldExtractionResult {
        val result = mutableListOf<ExtractedField>()
        val ignoredIndices = LinkedHashSet<Int>()
        var i = 0
        while (i < lines.size) {
            val line = lines[i]

            val colonIdx = findKeySeparatorIndex(line)
            if (colonIdx > 0) {
                val rawLabel = line.substring(0, colonIdx).trim()
                val key = matchFieldKey(rawLabel)
                if (key != null) {
                    val inlineVal = line.substring(colonIdx + 1).trim()
                    if (key == FieldKey.IGNORED_AMOUNT) {
                        if (inlineVal.isNotEmpty()) {
                            ignoredIndices.add(i)
                            result.add(ExtractedField(key, rawLabel, inlineVal, i, i))
                            i = tryConsumeIgnoredNextLine(lines, i, rawLabel, inlineVal, result, ignoredIndices)
                            continue
                        } else {
                            i = consumeIgnoredTail(lines, i, rawLabel, result, ignoredIndices)
                            continue
                        }
                    }

                    if (inlineVal.isNotEmpty()) {
                        if (!isPlaceholderFieldValue(inlineVal)) {
                            result.add(ExtractedField(key, rawLabel, inlineVal, i, i))
                        }
                        i++
                        continue
                    } else {
                        val nextLine = lines.getOrNull(i + 1)
                        if (canConsumeNextLineValue(key, nextLine)) {
                            result.add(ExtractedField(key, rawLabel, nextLine!!, i + 1, i))
                            i += 2
                            continue
                        }
                        if (nextLine != null && isPlaceholderFieldValue(nextLine)) {
                            i += 2
                            continue
                        }
                    }
                }
            }

            val exactKey = matchFieldKey(line)
            if (exactKey != null) {
                if (exactKey == FieldKey.IGNORED_AMOUNT) {
                    i = consumeIgnoredTail(lines, i, line, result, ignoredIndices)
                    continue
                }
                val nextLine = lines.getOrNull(i + 1)
                if (canConsumeNextLineValue(exactKey, nextLine)) {
                    result.add(ExtractedField(exactKey, line, nextLine!!, i + 1, i))
                    i += 2
                    continue
                }
                if (nextLine != null && isPlaceholderFieldValue(nextLine)) {
                    i += 2
                    continue
                }
            }

            val spaceField = matchSpaceSeparatedField(line)
            if (spaceField != null) {
                val (key, label, value) = spaceField
                if (key == FieldKey.IGNORED_AMOUNT) {
                    result.add(ExtractedField(key, label, value, i, i))
                    ignoredIndices.add(i)
                    i = tryConsumeIgnoredNextLine(lines, i, label, value, result, ignoredIndices)
                    continue
                } else if (!isPlaceholderFieldValue(value)) {
                    result.add(ExtractedField(key, label, value, i, i))
                }
            }

            i++
        }
        return FieldExtractionResult(result, ignoredIndices)
    }

    private fun tryConsumeIgnoredNextLine(
        lines: List<String>,
        labelLineIndex: Int,
        rawLabel: String,
        inlineVal: String,
        result: MutableList<ExtractedField>,
        ignoredIndices: MutableSet<Int>
    ): Int {
        val nextLine = lines.getOrNull(labelLineIndex + 1)
        if (!hasDecimalOrSymbolAmount(inlineVal) &&
            !isStandaloneAmountLine(inlineVal) &&
            nextLine != null &&
            !isStrictFieldLabelLine(nextLine) &&
            !isActionButtonOnly(nextLine) &&
            !isPlaceholderFieldValue(nextLine) &&
            looksLikeDiscountAmountLine(nextLine)
        ) {
            ignoredIndices.add(labelLineIndex + 1)
            result.add(ExtractedField(FieldKey.IGNORED_AMOUNT, rawLabel, nextLine, labelLineIndex + 1, labelLineIndex))
            return labelLineIndex + 2
        }
        return labelLineIndex + 1
    }

    private fun canConsumeNextLineValue(key: FieldKey, nextLine: String?): Boolean {
        if (nextLine == null) return false
        if (isActionButtonOnly(nextLine) || isPlaceholderFieldValue(nextLine)) return false
        val nextStrictKey = matchStrictFieldKeyInLine(nextLine)
        return when (key) {
            // 账户余额/余额/可用余额等在独立行时为忽略余额字段，但作为收付款方式的值时必须正常读取
            FieldKey.PAYMENT_METHOD -> nextStrictKey == null ||
                (nextStrictKey == FieldKey.IGNORED_AMOUNT && isValidPaymentMethodAccountName(nextLine))
            // 商品说明/商户/分类等绝不用模糊优惠正则拒绝正当描述值（如 "收到口令红包"、"美团满30减5"）
            FieldKey.GOODS,
            FieldKey.MERCHANT,
            FieldKey.PAYEE,
            FieldKey.PAYER,
            FieldKey.META -> nextStrictKey == null ||
                (nextStrictKey == FieldKey.IGNORED_AMOUNT &&
                    findKeySeparatorIndex(nextLine) <= 0 &&
                    matchSpaceSeparatedField(nextLine) == null)
            else -> nextStrictKey == null
        }
    }

    private fun consumeIgnoredTail(
        lines: List<String>,
        labelLineIndex: Int,
        rawLabel: String,
        result: MutableList<ExtractedField>,
        ignoredIndices: MutableSet<Int>
    ): Int {
        ignoredIndices.add(labelLineIndex)
        val line1 = lines.getOrNull(labelLineIndex + 1)
        if (line1 == null || isStrictFieldLabelLine(line1) || isActionButtonOnly(line1) || isPlaceholderFieldValue(line1)) {
            return labelLineIndex + 1
        }

        ignoredIndices.add(labelLineIndex + 1)
        result.add(ExtractedField(FieldKey.IGNORED_AMOUNT, rawLabel, line1, labelLineIndex + 1, labelLineIndex))

        // 若第 1 行已经是独立金额行或自带明确币种符号/小数金额（如 "-¥3.00"、"已优惠¥4.00"），停止继续向后吞并
        if (isStandaloneAmountLine(line1) || hasDecimalOrSymbolAmount(line1)) {
            return labelLineIndex + 2
        }

        // 第 1 行为子描述（如 "满30减5"、"使用5元立减金"），向后最多再跳 1 行跳过关联金额值（共跳 2 行）
        val line2 = lines.getOrNull(labelLineIndex + 2)
        if (line2 != null &&
            !isStrictFieldLabelLine(line2) &&
            !isActionButtonOnly(line2) &&
            !isPlaceholderFieldValue(line2) &&
            looksLikeDiscountAmountLine(line2)
        ) {
            ignoredIndices.add(labelLineIndex + 2)
            result.add(ExtractedField(FieldKey.IGNORED_AMOUNT, rawLabel, line2, labelLineIndex + 2, labelLineIndex))
            return labelLineIndex + 3
        }

        return labelLineIndex + 2
    }

    private fun hasDecimalOrSymbolAmount(text: String): Boolean =
        DECIMAL_OR_CURRENCY_SYMBOL_REGEX.containsMatchIn(text)

    private fun looksLikeDiscountAmountLine(line: String): Boolean {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return false
        if (FULL_DATETIME_REGEX.containsMatchIn(trimmed) || TIME_ONLY_STRIP_REGEX.matches(trimmed)) return false
        if (isStandaloneAmountLine(trimmed)) return true
        if (containsNoiseAmountKeyword(trimmed)) return true
        return AMOUNT_TOKEN_REGEX.findAll(trimmed).any { match ->
            val sign1 = match.groupValues.getOrNull(1).orEmpty()
            val prefixCur = match.groupValues.getOrNull(2).orEmpty()
            val sign2 = match.groupValues.getOrNull(3).orEmpty()
            val numStr = match.groupValues.getOrNull(4).orEmpty()
            val suffixCur = match.groupValues.getOrNull(5).orEmpty()
            prefixCur.isNotBlank() || suffixCur.isNotBlank() ||
                sign1.isNotBlank() || sign2.isNotBlank() || numStr.contains('.')
        }
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

    private fun matchStrictFieldKey(rawLabel: String): FieldKey? {
        val normalized = rawLabel.trim().trimEnd(':', '：').lowercase(Locale.ROOT)
        if (normalized.isEmpty()) return null
        FIELD_KEY_MAP[normalized]?.let { return it }

        val normalizedWithoutUnit = UNIT_SUFFIX_STRIP_REGEX.replace(normalized, "").trim()
        FIELD_KEY_MAP[normalizedWithoutUnit]?.let { return it }

        if (normalizedWithoutUnit.isEmpty() || normalizedWithoutUnit.length > 24) return null
        if (LABEL_DISQUALIFIER_REGEX.containsMatchIn(normalizedWithoutUnit)) return null

        if (PRIMARY_ACTUAL_LABEL_HINTS.any { normalizedWithoutUnit.contains(it) }) {
            return FieldKey.ACTUAL_AMOUNT
        }
        if (PRIMARY_FOREIGN_LABEL_HINTS.any { normalizedWithoutUnit.contains(it) }) {
            return FieldKey.FOREIGN_AMOUNT
        }
        if (PRIMARY_ORDER_LABEL_HINTS.any { normalizedWithoutUnit.contains(it) }) {
            return FieldKey.ORDER_AMOUNT
        }
        if (PRIMARY_AMOUNT_LABEL_HINTS.any { normalizedWithoutUnit.contains(it) }) {
            return FieldKey.AMOUNT
        }
        return null
    }

    private fun matchFieldKey(rawLabel: String): FieldKey? {
        matchStrictFieldKey(rawLabel)?.let { return it }
        val normalized = rawLabel.trim().trimEnd(':', '：').lowercase(Locale.ROOT)
        val normalizedWithoutUnit = UNIT_SUFFIX_STRIP_REGEX.replace(normalized, "").trim()
        if (normalizedWithoutUnit.isEmpty() || normalizedWithoutUnit.length > 24) return null
        if (LABEL_DISQUALIFIER_REGEX.containsMatchIn(normalizedWithoutUnit)) return null
        if (isIgnoredAmountFieldLabel(normalizedWithoutUnit)) {
            return FieldKey.IGNORED_AMOUNT
        }
        return null
    }

    private fun matchStrictFieldKeyInLine(line: String): FieldKey? {
        matchStrictFieldKey(line)?.let { return it }
        val colonIdx = findKeySeparatorIndex(line)
        if (colonIdx > 0) {
            matchStrictFieldKey(line.substring(0, colonIdx))?.let { return it }
        }
        return matchSpaceSeparatedField(line)?.first
    }

    private fun isStrictFieldLabel(line: String): Boolean = matchStrictFieldKey(line) != null

    private fun isStrictFieldLabelLine(line: String): Boolean = matchStrictFieldKeyInLine(line) != null

    private fun isIgnoredAmountFieldLabel(normalized: String): Boolean {
        if (normalized.length > 12) return false
        if (LABEL_SENTENCE_PUNCT_REGEX.containsMatchIn(normalized)) return false
        if (NON_IGNORED_LABEL_PREFIXES.any { normalized.startsWith(it) }) return false
        if (normalized in NON_IGNORED_LABEL_EXCLUSIONS) return false
        if (normalized in VALID_ACCOUNT_METHOD_LABELS) return false
        if (normalized in UI_STOP_WORDS || UI_STOP_PREFIXES.any { normalized.startsWith(it) }) return false
        if (PAY_SUCCESS_PHRASES.any { normalized == it } || IGNORED_STATUS_KEYWORDS.any { normalized == it }) return false
        if (CHAT_BUBBLE_MARKERS.any { normalized.contains(it) }) return false
        return IGNORED_FIELD_LABEL_REGEX.containsMatchIn(normalized)
    }

    private fun isValidPaymentMethodAccountName(line: String): Boolean =
        line.trim().lowercase(Locale.ROOT) in VALID_ACCOUNT_METHOD_LABELS

    private fun isPlaceholderFieldValue(value: String): Boolean =
        value.trim().lowercase(Locale.ROOT) in PLACEHOLDER_FIELD_VALUES

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
        className: String,
        lines: List<String>,
        fields: List<ExtractedField>
    ): Pair<ScreenPageKind, String> {
        if (isBlockedChatOrFeedWindow(packageName, className)) {
            return ScreenPageKind.UNKNOWN to "聊天或服务号对话框不触发"
        }

        val lowerLines = lines.map { it.lowercase(Locale.ROOT) }
        val lowerJoined = lowerLines.joinToString("\n")

        if (CHAT_STRONG_MARKERS.any { lowerJoined.contains(it) }) {
            return ScreenPageKind.UNKNOWN to "聊天或服务号对话框不触发"
        }
        if (lowerLines.contains("表情") && lowerLines.contains("发送")) {
            return ScreenPageKind.UNKNOWN to "聊天或服务号对话框不触发"
        }
        if ((lowerLines.contains("通讯录") && lowerLines.contains("发现")) ||
            (lowerLines.contains("首页") && lowerLines.contains("理财") && lowerLines.contains("我的"))
        ) {
            return ScreenPageKind.UNKNOWN to ""
        }

        // 微信支付 / 支付宝服务号通知卡片标题（即使退化为通用类名也立即屏蔽）
        if (SERVICE_CARD_TITLES.any { title -> lowerLines.any { it.contains(title) } }) {
            return ScreenPageKind.UNKNOWN to "聊天或服务号对话框不触发"
        }

        // 微信支付服务号对话框底部菜单特征
        val hasServiceBottomMenu = (lowerLines.contains("我的账单") && lowerLines.contains("支付服务")) ||
            (lowerLines.contains("我的账单") && lowerLines.contains("更多服务")) ||
            (lowerLines.contains("账单") && lowerLines.contains("支付服务"))
        if (hasServiceBottomMenu) {
            return ScreenPageKind.UNKNOWN to "聊天或服务号对话框不触发"
        }

        // 同一屏幕重复出现 ≥ 2 次同种凭证字段键（服务号/消息流滑动多张卡片）
        val voucherKeyCounts = HashMap<String, Int>(4)
        for (line in lowerLines) {
            val matchedKey = matchRepeatedVoucherKey(line) ?: continue
            val nextCount = (voucherKeyCounts[matchedKey] ?: 0) + 1
            if (nextCount >= 2) {
                return ScreenPageKind.UNKNOWN to "聊天或服务号对话框不触发"
            }
            voucherKeyCounts[matchedKey] = nextCount
        }

        val distinctDetailKeys = fields
            .map { it.key }
            .filter { it in DETAIL_SIGNAL_KEYS }
            .toSet()
            .size

        val hasDetailHeader = lowerLines.any { line ->
            DETAIL_HEADER_KEYWORDS.any { line == it || line.startsWith("$it ") || line.startsWith("$it·") }
        }
        val hasDetailAnchorMarker = DETAIL_ANCHOR_MARKERS.any { marker ->
            lowerLines.any { it.contains(marker) }
        }
        val strongDetailOnlyCount = fields
            .map { it.key }
            .filter { it == FieldKey.TIME || it == FieldKey.ORDER_NO || it == FieldKey.STATUS }
            .toSet()
            .size

        // 聊天红包 / 转账消息气泡强特征（排除带完整账单服务锚点的真实详情页）
        if (!hasDetailAnchorMarker &&
            strongDetailOnlyCount < 2 &&
            CHAT_BUBBLE_MARKERS.any { marker -> lowerLines.any { it.contains(marker) } }
        ) {
            return ScreenPageKind.UNKNOWN to "聊天或服务号对话框不触发"
        }

        // 支付宝服务号/聊天助手通知（无真实账单详情头与完整字段时屏蔽）
        if (packageName.equals(PKG_ALIPAY, ignoreCase = true) &&
            !hasDetailHeader &&
            strongDetailOnlyCount < 2 &&
            ALIPAY_FEED_CARD_MARKERS.any { marker -> lowerLines.any { it.contains(marker) } }
        ) {
            return ScreenPageKind.UNKNOWN to "聊天或服务号对话框不触发"
        }

        val timeFieldValueLines = fields
            .asSequence()
            .filter { it.key == FieldKey.TIME }
            .map { it.lineIndex }
            .toSet()
        val chatTimeDividerCount = lowerLines.withIndex().count { (idx, line) ->
            idx !in timeFieldValueLines && CHAT_TIME_DIVIDER_REGEX.matches(line)
        }
        val cardJumpDetailCount = lowerLines.count { line ->
            CARD_JUMP_DETAIL_BUTTONS.any { btn -> line == btn || line.contains(btn) }
        }
        val hasCardJumpDetailButton = cardJumpDetailCount > 0

        if (cardJumpDetailCount >= 2) {
            return ScreenPageKind.UNKNOWN to "聊天或服务号对话框不触发"
        }
        if (chatTimeDividerCount >= 2 && (hasCardJumpDetailButton || (!hasDetailHeader && !hasDetailAnchorMarker))) {
            return ScreenPageKind.UNKNOWN to "聊天或服务号对话框不触发"
        }

        // 仅当顶部 6 行内出现“全部账单/月账单/筛选”等列表页特征且不存在任何详情页锚点时，才判为账单列表页（避免微信详情页底部的“全部账单”按钮误杀）
        val topLines = lowerLines.take(6)
        val isBillListOverview = (BILL_LIST_MARKERS.any { marker -> topLines.any { it == marker || it.contains(marker) } } ||
            (topLines.any { it == "账单" || it == "我的账单" } &&
                lowerLines.any { it == "支出" || it.startsWith("支出 ") || it.startsWith("支出¥") } &&
                lowerLines.any { it == "收入" || it.startsWith("收入 ") || it.startsWith("收入¥") })) &&
            !hasDetailHeader &&
            !hasDetailAnchorMarker &&
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

        val isWeChatOrAlipay = packageName.equals(PKG_WECHAT, ignoreCase = true) ||
            packageName.equals(PKG_ALIPAY, ignoreCase = true)
        val doneKeywords = if (isWeChatOrAlipay) {
            WALLET_STRICT_DONE_ANCHOR_KEYWORDS
        } else {
            DONE_ANCHOR_KEYWORDS
        }
        val hasDoneAnchor = lowerLines.any { line ->
            doneKeywords.any { line == it || line.startsWith("$it(") || line.startsWith("$it（") }
        }
        val hasStrictResultDoneButton = lowerLines.any { line ->
            STRICT_PAY_RESULT_DONE_BUTTONS.any { line == it || line.startsWith("$it(") || line.startsWith("$it（") }
        }

        // 服务号消息卡片常包含“查看账单详情 / 点击查看详情 / 查看消费明细”按钮：
        // 若不具备真实支付结果页（支付成功+完成）特征，则判定为服务号对话卡片
        if (hasCardJumpDetailButton) {
            val isRealPayResultWithJump = hasPaySuccessPhrase &&
                hasStrictResultDoneButton &&
                !hasDetailAnchorMarker &&
                strongDetailOnlyCount == 0 &&
                chatTimeDividerCount == 0
            if (!isRealPayResultWithJump) {
                return ScreenPageKind.UNKNOWN to "聊天或服务号对话框不触发"
            }
        }

        // 支付结果页常伴随“支付成功 + 完成 + 商户/支付方式”，但不含时间/单号/状态或独立详情标题/详情服务锚点
        val isPaySuccessResultLayout = hasPaySuccessPhrase && hasDoneAnchor &&
            !hasStandaloneDetailTitleWithoutDone(lowerLines) &&
            !hasDetailAnchorMarker &&
            strongDetailOnlyCount == 0
        if (strongDetailOnlyCount >= 2 ||
            hasDetailAnchorMarker ||
            (distinctDetailKeys >= 2 && !isPaySuccessResultLayout) ||
            (hasDetailHeader && !isPaySuccessResultLayout)
        ) {
            return ScreenPageKind.BILL_DETAIL to ""
        }

        if (hasPaySuccessPhrase) {
            val isWeChat = packageName.equals(PKG_WECHAT, ignoreCase = true)
            val isAlipay = packageName.equals(PKG_ALIPAY, ignoreCase = true)
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

    private fun matchRepeatedVoucherKey(lowerLine: String): String? {
        for (key in REPEATED_VOUCHER_KEYS) {
            if (lowerLine == key ||
                lowerLine.startsWith("$key:") ||
                lowerLine.startsWith("$key：") ||
                lowerLine.startsWith("$key ") ||
                lowerLine.startsWith("$key\t")
            ) {
                return key
            }
        }
        return null
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

    private data class StandaloneAmountLineInfo(
        val index: Int,
        val cleanedLine: String,
        val topPx: Int,
        val heightPx: Int,
        val hasExplicitSign: Boolean,
        val hasCurrencyHint: Boolean
    )

    private fun resolveAmounts(
        lines: List<String>,
        normalizedLines: List<NormalizedLine>,
        fields: List<ExtractedField>,
        ignoredLineIndices: Set<Int>,
        screenHeightPx: Int
    ): ResolvedAmountResult? {
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
            val heightPx = normalizedLines.getOrNull(field.lineIndex)?.heightPx ?: 0
            candidates += extractCandidatesFromText(
                text = field.value,
                role = role,
                lineIndex = field.lineIndex,
                allowBareDecimal = true,
                allowBareInteger = allowBareInteger,
                heightPx = heightPx
            )
        }

        // 计算有效几何信息与中位文本行高，用于识别上半屏大号主金额 HERO_VISUAL
        val validHeights = normalizedLines.asSequence()
            .map { it.heightPx }
            .filter { it > 0 }
            .sorted()
            .toList()
        val hasValidGeometry = screenHeightPx > 0 && validHeights.isNotEmpty()
        val medianHeight = if (hasValidGeometry) {
            val n = validHeights.size
            if (n % 2 == 1) validHeights[n / 2] else (validHeights[n / 2 - 1] + validHeights[n / 2]) / 2
        } else {
            0
        }
        val upperHalfLimitPx = (screenHeightPx * 0.50f).toInt()

        val firstAnchorFieldLineIndex = fields
            .asSequence()
            .filter {
                it.key == FieldKey.PAYMENT_METHOD ||
                    it.key == FieldKey.TIME ||
                    it.key == FieldKey.ORDER_NO
            }
            .minOfOrNull { it.labelLineIndex }
            ?: Int.MAX_VALUE

        val standaloneLines = mutableMapOf<Int, StandaloneAmountLineInfo>()
        val nonStandaloneCandidates = mutableListOf<Pair<Int, String>>()

        for ((index, line) in lines.withIndex()) {
            if (index in ignoredLineIndices) continue
            val matchedField = fieldLinesByIndex[index]
            if (matchedField != null) {
                if (matchedField.key in SKIP_AMOUNT_SCAN_KEYS) continue
                if (matchedField.key in EXTRACTED_AMOUNT_KEYS) continue
            }
            if (matchFieldKey(line) != null) continue
            if (isMixedPaymentBreakdownLine(line)) continue

            val cleanedLine = stripNonAmountClauses(line)
            if (cleanedLine.isBlank()) continue
            if (containsNoiseAmountKeyword(cleanedLine)) continue

            val trimmedClean = cleanedLine.trim()
            if (isStandaloneAmountLine(trimmedClean)) {
                val node = normalizedLines.getOrNull(index)
                val hasExplicitSign = trimmedClean.startsWith("-") || trimmedClean.startsWith("+")
                standaloneLines[index] = StandaloneAmountLineInfo(
                    index = index,
                    cleanedLine = trimmedClean,
                    topPx = node?.topPx ?: -1,
                    heightPx = node?.heightPx ?: 0,
                    hasExplicitSign = hasExplicitSign,
                    hasCurrencyHint = hasExplicitCurrencyHint(trimmedClean)
                )
            } else {
                nonStandaloneCandidates += index to cleanedLine
            }
        }

        val firstSignedOrCurrencyStandaloneIndex = standaloneLines.values
            .asSequence()
            .filter { it.index < 12 && (it.hasExplicitSign || it.hasCurrencyHint) }
            .minOfOrNull { it.index }
            ?: -1

        val upperHalfHeights = if (hasValidGeometry) {
            standaloneLines.values
                .filter { it.topPx in 0..upperHalfLimitPx && it.heightPx > 0 }
                .map { it.heightPx }
        } else {
            emptyList()
        }
        val maxTopAmountHeight = upperHalfHeights.maxOrNull() ?: 0
        val secondTopAmountHeight = upperHalfHeights.filter { it < maxTopAmountHeight }.maxOrNull() ?: 0

        for (info in standaloneLines.values) {
            val isHeroVisual = hasValidGeometry &&
                info.topPx in 0..upperHalfLimitPx &&
                info.heightPx > 0 &&
                (
                    (medianHeight > 0 &&
                        info.heightPx >= (medianHeight * 1.35f).toInt() &&
                        (maxTopAmountHeight <= 0 || info.heightPx >= (maxTopAmountHeight * 0.80f).toInt())) ||
                        (info.heightPx == maxTopAmountHeight &&
                            ((secondTopAmountHeight > 0 && info.heightPx >= (secondTopAmountHeight * 1.25f).toInt()) ||
                                (secondTopAmountHeight == 0 && medianHeight > 0 && info.heightPx > medianHeight)))
                    )

            val isTextHeroZone = (info.index < 18 && info.index < firstAnchorFieldLineIndex) ||
                (info.index < 12 && info.index == firstSignedOrCurrencyStandaloneIndex)

            val role = when {
                isHeroVisual -> AmountRole.HERO_VISUAL
                isTextHeroZone -> AmountRole.HERO
                else -> AmountRole.GENERIC
            }
            val isHero = role == AmountRole.HERO_VISUAL || role == AmountRole.HERO
            candidates += extractCandidatesFromText(
                text = info.cleanedLine,
                role = role,
                lineIndex = info.index,
                allowBareDecimal = isHero,
                allowBareInteger = isHero && info.hasExplicitSign,
                heightPx = info.heightPx
            )
        }

        for ((index, cleanedLine) in nonStandaloneCandidates) {
            val heightPx = normalizedLines.getOrNull(index)?.heightPx ?: 0
            candidates += extractCandidatesFromText(
                text = cleanedLine,
                role = AmountRole.GENERIC,
                lineIndex = index,
                allowBareDecimal = false,
                allowBareInteger = false,
                heightPx = heightPx
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
        list.sortedWith { a, b ->
            val roleCmp = b.role.priority.compareTo(a.role.priority)
            if (roleCmp != 0) return@sortedWith roleCmp
            val signCmp = (b.signHint != null).compareTo(a.signHint != null)
            if (signCmp != 0) return@sortedWith signCmp
            val heightCmp = b.heightPx.compareTo(a.heightPx)
            if (heightCmp != 0) return@sortedWith heightCmp
            val lineCmp = a.lineIndex.compareTo(b.lineIndex)
            if (lineCmp != 0) return@sortedWith lineCmp
            b.hasExplicitCurrency.compareTo(a.hasExplicitCurrency)
        }.firstOrNull()

    private fun hasExplicitCurrencyHint(text: String): Boolean =
        AMOUNT_TOKEN_REGEX.findAll(text).any { match ->
            match.groupValues.getOrNull(2).orEmpty().isNotBlank() ||
                match.groupValues.getOrNull(5).orEmpty().isNotBlank()
        }

    private fun isMixedPaymentBreakdownLine(line: String): Boolean {
        if (!line.contains('+')) return false
        return MIXED_PAYMENT_NOISE_REGEX.containsMatchIn(line)
    }

    private fun extractCandidatesFromText(
        text: String,
        role: AmountRole,
        lineIndex: Int,
        allowBareDecimal: Boolean,
        allowBareInteger: Boolean,
        heightPx: Int = 0
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
                    signHint = sign,
                    heightPx = heightPx
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
        .replace(PARENTHESIZED_DISCOUNT_STRIP_REGEX, " ")
        .replace(USAGE_DISCOUNT_STRIP_REGEX, " ")
        .replace(MIXED_PAYMENT_DISCOUNT_SUFFIX_REGEX, " ")
        .replace(DISCOUNT_BALANCE_STRIP_REGEX, " ")
        .replace(WHITESPACE_RUN_REGEX, " ")
        .trim(' ', '\t', ',', '，', ';', '；', '、', '/', '|')
        .trimEnd('+', ' ')

    private fun sanitizePaymentMethodText(raw: String): String {
        if (raw.isBlank()) return ""
        return raw
            .replace(PARENTHESIZED_DISCOUNT_STRIP_REGEX, " ")
            .replace(MIXED_PAYMENT_DISCOUNT_SUFFIX_REGEX, " ")
            .replace(WHITESPACE_RUN_REGEX, " ")
            .trim(' ', '\t', ',', '，', ';', '；', '、', '/', '|', '+')
    }

    private fun containsNoiseAmountKeyword(text: String): Boolean =
        NOISE_AMOUNT_LINE_KEYWORDS.any { text.contains(it, ignoreCase = true) } ||
            SAVINGS_AMOUNT_REGEX.containsMatchIn(text) ||
            FULL_REDUCTION_REGEX.containsMatchIn(text) ||
            ENGLISH_NOISE_AMOUNT_REGEX.containsMatchIn(text)

    private fun inferTransactionType(
        lines: List<String>,
        fields: List<ExtractedField>,
        dominantSign: String?
    ): String {
        // 第 1 优先级（最高）：主金额本身带有明确正负号，绝不被下方优惠返现/二维码收款/退款详情反转
        if (dominantSign == "-") return TransactionType.EXPENSE
        if (dominantSign == "+") return TransactionType.INCOME

        val hasIncomePaymentField = fields.any {
            it.key == FieldKey.PAYMENT_METHOD && isIncomePaymentMethodLabel(it.rawLabel)
        }
        val hasExpensePaymentField = fields.any {
            it.key == FieldKey.PAYMENT_METHOD && isExpensePaymentMethodLabel(it.rawLabel)
        }
        val hasIncomeTimeField = fields.any {
            it.key == FieldKey.TIME && isIncomeTimeLabel(it.rawLabel)
        }
        val hasExpenseTimeField = fields.any {
            it.key == FieldKey.TIME && isExpenseTimeLabel(it.rawLabel)
        }

        val statusText = extractFirstFieldValue(fields, FieldKey.STATUS)
        val goodsText = extractGoods(fields)
        val headerSlice = lines.take(12).joinToString("\n")
        val checkSurface = "$statusText\n$goodsText\n$headerSlice"
        val sanitizedForRefund = REFUND_BUTTON_EXCLUSION_REGEX.replace(checkSurface, " ")
        val isExplicitFullRefundStatus = FULL_REFUND_STATUS_KEYWORDS.any { kw ->
            statusText.contains(kw, ignoreCase = true) ||
                lines.take(8).any { line ->
                    REFUND_BUTTON_EXCLUSION_REGEX.replace(line, " ").contains(kw, ignoreCase = true)
                }
        }

        // 第 2 优先级：强方向结构字段（如 "收款方式" -> INCOME，"付款方式" -> EXPENSE）
        if (hasIncomePaymentField && !hasExpensePaymentField) {
            return TransactionType.INCOME
        }
        if (hasExpensePaymentField || hasExpenseTimeField) {
            return if (isExplicitFullRefundStatus) TransactionType.INCOME else TransactionType.EXPENSE
        }

        // 第 3 优先级：转账对方已收款等我方支出信号
        if (COUNTERPARTY_RECEIVED_KEYWORDS.any { checkSurface.contains(it, ignoreCase = true) }) {
            return TransactionType.EXPENSE
        }

        if (hasIncomeTimeField) {
            return TransactionType.INCOME
        }

        // 第 4 优先级：无正负号且无支出字段时的紧缩收入语义兜底（排除孤立的二维码收款/返现/已到账/退款详情/部分已退）
        if (INCOME_CONFIRM_REGEX.containsMatchIn(sanitizedForRefund)) {
            return TransactionType.INCOME
        }

        return TransactionType.EXPENSE
    }

    private fun isIncomePaymentMethodLabel(rawLabel: String): Boolean {
        val normalized = normalizeFieldLabelKey(rawLabel)
        return normalized in INCOME_PAYMENT_METHOD_LABELS
    }

    private fun isExpensePaymentMethodLabel(rawLabel: String): Boolean {
        val normalized = normalizeFieldLabelKey(rawLabel)
        return normalized in EXPENSE_PAYMENT_METHOD_LABELS
    }

    private fun isIncomeTimeLabel(rawLabel: String): Boolean {
        val lower = rawLabel.trim().lowercase(Locale.ROOT)
        return INCOME_TIME_KEYWORDS.any { lower.contains(it) }
    }

    private fun isExpenseTimeLabel(rawLabel: String): Boolean {
        val lower = rawLabel.trim().lowercase(Locale.ROOT)
        return EXPENSE_TIME_KEYWORDS.any { lower.contains(it) }
    }

    private fun normalizeFieldLabelKey(rawLabel: String): String {
        val normalized = rawLabel.trim().trimEnd(':', '：').lowercase(Locale.ROOT)
        return UNIT_SUFFIX_STRIP_REGEX.replace(normalized, "").trim()
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
        val heroMerchant = extractHeroMerchant(lines)
            ?.takeIf { !isGenericChannelMerchant(it) }
        val explicitShort = fields
            .filter { it.key == FieldKey.MERCHANT }
            .mapNotNull { cleanMerchantCandidate(it.value) }
            .firstOrNull { !isGenericChannelMerchant(it) }

        if (!explicitShort.isNullOrBlank()) {
            // 若商户全称是结算主体（如“商户_王禹睿”、“个体户xxx”、“xx有限公司”），而顶部有更直观的店名（如“哆哆酸辣粉”），优先采用顶部直观店名
            if (!heroMerchant.isNullOrBlank() &&
                isSettlementEntityName(explicitShort) &&
                !isSettlementEntityName(heroMerchant)
            ) {
                return heroMerchant
            }
            return explicitShort
        }

        if (!heroMerchant.isNullOrBlank()) {
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

        return ""
    }

    private fun isSettlementEntityName(name: String): Boolean {
        val trimmed = name.trim()
        return trimmed.startsWith("商户_") ||
            trimmed.startsWith("个体户") ||
            trimmed.endsWith("有限公司") ||
            trimmed.endsWith("有限责任公司") ||
            trimmed.endsWith("经营部") ||
            trimmed.endsWith("个体工商户") ||
            trimmed.endsWith("分公司")
    }

    private fun extractHeroMerchant(lines: List<String>): String? {
        for (line in lines.take(20)) {
            val lineKey = matchFieldKey(line)
            if (lineKey == FieldKey.IGNORED_AMOUNT) continue
            if (lineKey != null) break
            if (findKeySeparatorIndex(line) > 0) {
                val label = line.substring(0, findKeySeparatorIndex(line))
                val sepKey = matchFieldKey(label)
                if (sepKey == FieldKey.IGNORED_AMOUNT) continue
                if (sepKey != null) break
            }
            if (NOISE_MERCHANT_LINE_REGEX.containsMatchIn(line)) continue
            if (isStandaloneAmountLine(line)) continue
            if (FULL_DATETIME_REGEX.containsMatchIn(line) || TIME_ONLY_STRIP_REGEX.matches(line.trim())) continue

            val cleaned = cleanMerchantCandidate(line) ?: continue
            if (cleaned.isNotBlank()) return cleaned
        }
        return null
    }

    private fun isNonMerchantUiText(lower: String): Boolean =
        lower in UI_STOP_WORDS ||
            lower in PLACEHOLDER_FIELD_VALUES ||
            UI_STOP_PREFIXES.any { lower.startsWith(it) } ||
            PAY_SUCCESS_PHRASES.any { lower == it } ||
            IGNORED_STATUS_KEYWORDS.any { lower == it }

    private fun cleanMerchantCandidate(raw: String): String? {
        var s = raw.trim()
        if (s.isEmpty()) return null
        if (isStrictFieldLabel(s) || isPlaceholderFieldValue(s)) return null

        // 在剥离“向/给/在/付款/转账”等前后缀之前先检查原始文本，避免“在浏览器打开”等系统 UI 文案绕过停用词表
        if (isNonMerchantUiText(s.lowercase(Locale.ROOT))) return null

        s = s.replace(MERCHANT_ACTION_PREFIX_REGEX, "")
            .replace(MERCHANT_ACTION_SUFFIX_REGEX, "")
            .replace(Regex("^[：:，,。\\-—\\s]+|[：:，,。.;；\\s]+$"), "")
            .trim()
        if (s.isEmpty() || s.length > 40) return null

        val lower = s.lowercase(Locale.ROOT)
        if (isNonMerchantUiText(lower)) return null
        if (PURE_NUMBER_OR_CARD_REGEX.matches(s)) return null
        if (AMOUNT_ONLY_LINE_REGEX.matches(s.replace(" ", ""))) return null
        if (FULL_DATETIME_REGEX.containsMatchIn(s) || MONTH_DAY_TIME_REGEX.containsMatchIn(s)) return null
        return s
    }

    private fun isGenericChannelMerchant(merchant: String): Boolean =
        merchant.trim().lowercase(Locale.ROOT) in GENERIC_CHANNEL_MERCHANTS

    private fun extractFirstFieldValue(fields: List<ExtractedField>, key: FieldKey): String =
        fields.firstOrNull { it.key == key }?.value?.trim().orEmpty()

    private fun extractGoods(fields: List<ExtractedField>): String =
        fields.asSequence()
            .filter { it.key == FieldKey.GOODS }
            .map { it.value.trim() }
            .firstOrNull { value ->
                value.isNotEmpty() &&
                    !isPlaceholderFieldValue(value) &&
                    value.lowercase(Locale.ROOT) !in CHROME_NOISE_LINES
            }
            .orEmpty()

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

        /**
         * 拦截微信聊天与公众号/服务号消息流窗口、支付宝聊天/主 Tab/登录窗口，避免滑动消息列表时误弹记账悬浮窗。
         * 明确放行真实支付成功页与账单详情页（MMWebViewUI、WalletOrderInfoNewUI 等）。
         */
        fun isBlockedChatOrFeedWindow(packageName: String, className: String): Boolean {
            val pkg = packageName.trim()
            val lowerCls = className.trim().lowercase(Locale.ROOT)
            if (pkg.isEmpty() || lowerCls.isEmpty()) return false
            return when {
                pkg.equals(PKG_WECHAT, ignoreCase = true) -> {
                    if (WECHAT_SAFE_WINDOW_CLASSES.any { lowerCls.contains(it) }) {
                        false
                    } else {
                        WECHAT_BLOCKED_WINDOW_TOKENS.any { lowerCls.contains(it) }
                    }
                }
                pkg.equals(PKG_ALIPAY, ignoreCase = true) -> {
                    ALIPAY_BLOCKED_WINDOW_TOKENS.any { lowerCls.contains(it) }
                }
                else -> false
            }
        }

        private val WECHAT_SAFE_WINDOW_CLASSES = listOf(
            "mmwebviewui",
            "walletorderinfonewui",
            "walletpaidsuccessui",
            "mallorderdetailinfoui",
            "uipagefragmentactivity",
            "remittancedetailui",
            "remittanceresui",
            "walletrefundsuccessui"
        )

        private val WECHAT_BLOCKED_WINDOW_TOKENS = listOf(
            "launcherui",
            "chattingui",
            "bizchatconversationui",
            "biztimelineui",
            "conversation"
        )

        private val ALIPAY_BLOCKED_WINDOW_TOKENS = listOf(
            "chatmessageactivity",
            "socialchatactivity",
            "alipaylogin",
            "tablauncher"
        )

        private val WHITESPACE_RUN_REGEX = Regex("\\s+")

        private val SPLIT_CURRENCY_ONLY_REGEX = Regex(
            "^(?:US\\$|HK\\$|NT\\$|S\\$|A\\$|C\\$|JP¥|USD|EUR|GBP|HKD|JPY|SGD|AUD|CAD|CHF|TWD|KRW|CNY|RMB|\\$|€|£|¥|￥|₩)$",
            RegexOption.IGNORE_CASE
        )

        private val SPLIT_PREFIX_REGEX = Regex(
            "^(?:[+-]|[+-]?(?:US\\$|HK\\$|NT\\$|S\\$|A\\$|C\\$|JP¥|USD|EUR|GBP|HKD|JPY|SGD|AUD|CAD|CHF|TWD|KRW|CNY|RMB|\\$|€|£|¥|￥|₩))$",
            RegexOption.IGNORE_CASE
        )

        private val SPLIT_INT_PART_REGEX = Regex(
            "^(?:(?:[+-]?(?:US\\$|HK\\$|NT\\$|S\\$|A\\$|C\\$|JP¥|\\$|€|£|¥|￥|₩)?[+-]?)|(?:[+-]?(?:USD|EUR|GBP|HKD|JPY|SGD|AUD|CAD|CHF|TWD|KRW|CNY|RMB)\\s+))?[0-9]{1,9}(?:,[0-9]{3})*$",
            RegexOption.IGNORE_CASE
        )
        private val SPLIT_DOT_DEC_REGEX = Regex(
            "^\\.[0-9]{1,2}(?:元|USD|EUR|GBP|HKD|JPY|SGD|AUD|CAD|CHF|TWD|KRW|CNY|RMB)?$",
            RegexOption.IGNORE_CASE
        )
        private val SPLIT_INT_DOT_REGEX = Regex(
            "^(?:(?:[+-]?(?:US\\$|HK\\$|NT\\$|S\\$|A\\$|C\\$|JP¥|\\$|€|£|¥|￥|₩)?[+-]?)|(?:[+-]?(?:USD|EUR|GBP|HKD|JPY|SGD|AUD|CAD|CHF|TWD|KRW|CNY|RMB)\\s+))?[0-9]{1,9}(?:,[0-9]{3})*\\.$",
            RegexOption.IGNORE_CASE
        )
        private val SPLIT_DEC_DIGITS_REGEX = Regex(
            "^[0-9]{1,2}(?:元|USD|EUR|GBP|HKD|JPY|SGD|AUD|CAD|CHF|TWD|KRW|CNY|RMB)?$",
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
            "收钱时间" to FieldKey.TIME,
            "转账时间" to FieldKey.TIME,
            "领取时间" to FieldKey.TIME,
            "提现时间" to FieldKey.TIME,
            "申请时间" to FieldKey.TIME,
            "还款时间" to FieldKey.TIME,
            "充值时间" to FieldKey.TIME,
            "转入时间" to FieldKey.TIME,
            "转出时间" to FieldKey.TIME,
            "出账时间" to FieldKey.TIME,
            "入账时间" to FieldKey.TIME,
            "下单时间" to FieldKey.TIME,
            "退款时间" to FieldKey.TIME,
            "到账时间" to FieldKey.TIME,
            "完成时间" to FieldKey.TIME,
            "发出时间" to FieldKey.TIME,
            "发红包时间" to FieldKey.TIME,
            "收红包时间" to FieldKey.TIME,
            "扣款时间" to FieldKey.TIME,
            "结算时间" to FieldKey.TIME,
            "账单时间" to FieldKey.TIME,
            "transaction time" to FieldKey.TIME,
            "payment time" to FieldKey.TIME,
            "date" to FieldKey.TIME,

            "支付方式" to FieldKey.PAYMENT_METHOD,
            "付款方式" to FieldKey.PAYMENT_METHOD,
            "扣款方式" to FieldKey.PAYMENT_METHOD,
            "收款方式" to FieldKey.PAYMENT_METHOD,
            "付款账号" to FieldKey.PAYMENT_METHOD,
            "付款卡号" to FieldKey.PAYMENT_METHOD,
            "扣款银行卡" to FieldKey.PAYMENT_METHOD,
            "转出账户" to FieldKey.PAYMENT_METHOD,
            "扣款账户" to FieldKey.PAYMENT_METHOD,
            "资金来源" to FieldKey.PAYMENT_METHOD,
            "提现银行" to FieldKey.PAYMENT_METHOD,
            "提现到" to FieldKey.PAYMENT_METHOD,
            "到账银行卡" to FieldKey.PAYMENT_METHOD,
            "转入至" to FieldKey.PAYMENT_METHOD,
            "还款银行卡" to FieldKey.PAYMENT_METHOD,
            "收款账户" to FieldKey.PAYMENT_METHOD,
            "存入账户" to FieldKey.PAYMENT_METHOD,
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
            "商家订单号" to FieldKey.ORDER_NO,
            "transaction id" to FieldKey.ORDER_NO,
            "transaction no" to FieldKey.ORDER_NO,
            "order id" to FieldKey.ORDER_NO,
            "order no" to FieldKey.ORDER_NO,
            "merchant order no" to FieldKey.ORDER_NO,

            "商户" to FieldKey.MERCHANT,
            "商户名称" to FieldKey.MERCHANT,
            "商户简称" to FieldKey.MERCHANT,
            "商户全称" to FieldKey.MERCHANT,
            "收款商户全称" to FieldKey.MERCHANT,
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
            "备注…" to FieldKey.GOODS,
            "备注..." to FieldKey.GOODS,
            "备注信息" to FieldKey.GOODS,
            "红包说明" to FieldKey.GOODS,
            "付款方留言" to FieldKey.GOODS,
            "转账备注" to FieldKey.GOODS,
            "description" to FieldKey.GOODS,
            "item" to FieldKey.GOODS,

            "收单机构" to FieldKey.META,
            "清算机构" to FieldKey.META,
            "账单服务" to FieldKey.META,
            "账单分类" to FieldKey.META,
            "对方微信号" to FieldKey.META,
            "对方账号" to FieldKey.META,
            "收款方备注" to FieldKey.META,
            "红包个数" to FieldKey.META,

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
            "优惠信息" to FieldKey.IGNORED_AMOUNT,
            "优惠金额" to FieldKey.IGNORED_AMOUNT,
            "支付优惠" to FieldKey.IGNORED_AMOUNT,
            "优惠说明" to FieldKey.IGNORED_AMOUNT,
            "优惠明细" to FieldKey.IGNORED_AMOUNT,
            "商家优惠" to FieldKey.IGNORED_AMOUNT,
            "平台优惠" to FieldKey.IGNORED_AMOUNT,
            "官方立减" to FieldKey.IGNORED_AMOUNT,
            "立减" to FieldKey.IGNORED_AMOUNT,
            "立减金额" to FieldKey.IGNORED_AMOUNT,
            "立减金" to FieldKey.IGNORED_AMOUNT,
            "微信立减金" to FieldKey.IGNORED_AMOUNT,
            "消费券" to FieldKey.IGNORED_AMOUNT,
            "代金券" to FieldKey.IGNORED_AMOUNT,
            "优惠券" to FieldKey.IGNORED_AMOUNT,
            "商家券" to FieldKey.IGNORED_AMOUNT,
            "消费金" to FieldKey.IGNORED_AMOUNT,
            "花呗金" to FieldKey.IGNORED_AMOUNT,
            "体验金" to FieldKey.IGNORED_AMOUNT,
            "鼓励金" to FieldKey.IGNORED_AMOUNT,
            "淘金币" to FieldKey.IGNORED_AMOUNT,
            "满减" to FieldKey.IGNORED_AMOUNT,
            "抵扣" to FieldKey.IGNORED_AMOUNT,
            "抵用" to FieldKey.IGNORED_AMOUNT,
            "红包" to FieldKey.IGNORED_AMOUNT,
            "红包抵扣" to FieldKey.IGNORED_AMOUNT,
            "折扣" to FieldKey.IGNORED_AMOUNT,
            "补贴" to FieldKey.IGNORED_AMOUNT,
            "减免" to FieldKey.IGNORED_AMOUNT,
            "已减" to FieldKey.IGNORED_AMOUNT,
            "共减" to FieldKey.IGNORED_AMOUNT,
            "直降" to FieldKey.IGNORED_AMOUNT,
            "免单" to FieldKey.IGNORED_AMOUNT,
            "返现" to FieldKey.IGNORED_AMOUNT,
            "省钱" to FieldKey.IGNORED_AMOUNT,
            "笔笔攒" to FieldKey.IGNORED_AMOUNT,
            "运费险" to FieldKey.IGNORED_AMOUNT,
            "积分" to FieldKey.IGNORED_AMOUNT,
            "集分宝" to FieldKey.IGNORED_AMOUNT,
            "手续费" to FieldKey.IGNORED_AMOUNT,
            "服务费" to FieldKey.IGNORED_AMOUNT
        )

        private val UNIT_SUFFIX_STRIP_REGEX = Regex(
            "(?:[(（]\\s*(?:元|人民币|外币|原币|原币种|cny|rmb|usd|eur|gbp|hkd|jpy)\\s*[)）])+$",
            RegexOption.IGNORE_CASE
        )

        private val LABEL_DISQUALIFIER_REGEX = Regex("[0-9¥￥$€£₩()（）]")
        private val LABEL_SENTENCE_PUNCT_REGEX = Regex("[:：，,。.!！?？;；、…\"'“”‘’「」『』]")
        private val NON_IGNORED_LABEL_PREFIXES = listOf("收到", "领取", "发", "抢", "向", "给", "在", "含")

        private val DECIMAL_OR_CURRENCY_SYMBOL_REGEX = Regex("(?:[¥￥$€£₩]|\\d+\\.\\d{1,2})")

        private val PRIMARY_ACTUAL_LABEL_HINTS = listOf(
            "实付金额", "实付款", "实际付款", "付款金额", "支付金额", "扣款金额",
            "折算人民币", "折合人民币", "人民币金额", "入账金额"
        )

        private val PRIMARY_FOREIGN_LABEL_HINTS = listOf(
            "原始金额", "外币金额", "原币金额", "消费外币", "交易币种金额"
        )

        private val PRIMARY_ORDER_LABEL_HINTS = listOf(
            "订单金额", "原价"
        )

        private val PRIMARY_AMOUNT_LABEL_HINTS = listOf(
            "消费金额", "交易金额", "账单金额", "退款金额", "退回金额", "收款金额", "转账金额"
        )

        private val VALID_ACCOUNT_METHOD_LABELS = setOf(
            "账户余额", "余额", "可用余额", "当前余额",
            "余额宝", "余利宝", "零钱", "零钱通", "支付宝余额", "余额支付", "微信零钱"
        )

        private val PLACEHOLDER_FIELD_VALUES = setOf(
            "添加", "去添加", "点击添加", "请选择", "开关", "未设置", "暂无", "去设置"
        )

        private val NON_IGNORED_LABEL_EXCLUSIONS = setOf(
            "微信红包", "qq红包", "企业微信红包",
            "收到红包", "领取红包", "收到口令红包", "红包已领取", "发红包", "抢红包",
            "余额宝", "余利宝", "零钱", "零钱通", "余额支付", "返现成功", "返现到账"
        )

        private val IGNORED_FIELD_LABEL_REGEX = Regex(
            "(?:优惠信息|优惠|立减|红包|折扣|抵扣|抵用|满减|代金券|优惠券|消费券|商家券|消费金|花呗金|体验金|鼓励金|淘金币|补贴|减免|已减|共减|直降|免单|返现|省钱|共省|已省|立省|省了|节省|碰一下|笔笔攒|运费险|组合支付|积分|集分宝|余额|汇率|手续费|服务费|\\bdiscounts?\\b|\\bcoupons?\\b|\\bvouchers?\\b|\\bcashback\\b|\\bbalances?\\b|\\bfees?\\b)",
            RegexOption.IGNORE_CASE
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
            "支付优惠" to FieldKey.IGNORED_AMOUNT,
            "优惠信息" to FieldKey.IGNORED_AMOUNT,
            "优惠说明" to FieldKey.IGNORED_AMOUNT,
            "优惠明细" to FieldKey.IGNORED_AMOUNT,
            "优惠金额" to FieldKey.IGNORED_AMOUNT,
            "商家优惠" to FieldKey.IGNORED_AMOUNT,
            "平台优惠" to FieldKey.IGNORED_AMOUNT,
            "碰一下立减" to FieldKey.IGNORED_AMOUNT,
            "官方立减" to FieldKey.IGNORED_AMOUNT,
            "微信立减金" to FieldKey.IGNORED_AMOUNT,
            "立减金" to FieldKey.IGNORED_AMOUNT,
            "支付宝红包" to FieldKey.IGNORED_AMOUNT,
            "红包抵扣" to FieldKey.IGNORED_AMOUNT,
            "消费券" to FieldKey.IGNORED_AMOUNT,
            "代金券" to FieldKey.IGNORED_AMOUNT,
            "优惠券" to FieldKey.IGNORED_AMOUNT,
            "商家券" to FieldKey.IGNORED_AMOUNT,
            "消费金" to FieldKey.IGNORED_AMOUNT,
            "花呗金" to FieldKey.IGNORED_AMOUNT,
            "体验金" to FieldKey.IGNORED_AMOUNT,
            "鼓励金" to FieldKey.IGNORED_AMOUNT,
            "淘金币" to FieldKey.IGNORED_AMOUNT,
            "笔笔攒" to FieldKey.IGNORED_AMOUNT,
            "运费险" to FieldKey.IGNORED_AMOUNT,
            "组合支付" to FieldKey.IGNORED_AMOUNT,
            "支付方式" to FieldKey.PAYMENT_METHOD,
            "付款方式" to FieldKey.PAYMENT_METHOD,
            "收款方式" to FieldKey.PAYMENT_METHOD,
            "扣款方式" to FieldKey.PAYMENT_METHOD,
            "存入账户" to FieldKey.PAYMENT_METHOD,
            "退款去向" to FieldKey.PAYMENT_METHOD,
            "退回账户" to FieldKey.PAYMENT_METHOD,
            "提现银行" to FieldKey.PAYMENT_METHOD,
            "交易单号" to FieldKey.ORDER_NO,
            "转账单号" to FieldKey.ORDER_NO,
            "商家订单号" to FieldKey.ORDER_NO,
            "商户订单号" to FieldKey.ORDER_NO,
            "订单号" to FieldKey.ORDER_NO,
            "商户单号" to FieldKey.ORDER_NO,
            "支付时间" to FieldKey.TIME,
            "交易时间" to FieldKey.TIME,
            "创建时间" to FieldKey.TIME,
            "收钱时间" to FieldKey.TIME,
            "转账时间" to FieldKey.TIME,
            "提现时间" to FieldKey.TIME,
            "红包说明" to FieldKey.GOODS,
            "商品说明" to FieldKey.GOODS,
            "转账说明" to FieldKey.GOODS,
            "账单分类" to FieldKey.META,
            "收单机构" to FieldKey.META,
            "清算机构" to FieldKey.META
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
            FieldKey.FOREIGN_AMOUNT,
            FieldKey.META
        )

        private val EXTRACTED_AMOUNT_KEYS = setOf(
            FieldKey.ACTUAL_AMOUNT,
            FieldKey.FOREIGN_AMOUNT,
            FieldKey.AMOUNT,
            FieldKey.ORDER_AMOUNT
        )

        private val SKIP_AMOUNT_SCAN_KEYS = setOf(
            FieldKey.ORDER_NO,
            FieldKey.TIME,
            FieldKey.PAYMENT_METHOD,
            FieldKey.STATUS,
            FieldKey.IGNORED_AMOUNT,
            FieldKey.MERCHANT,
            FieldKey.PAYEE,
            FieldKey.PAYER,
            FieldKey.META
        )

        private val ACTION_ONLY_WORDS = setOf(
            "复制", "copy", "查看", "更多", "完成", "done", "返回", "关闭"
        )

        private val CHAT_STRONG_MARKERS = listOf(
            "按住 说话", "按住说话", "切换到按住说话", "更多功能按钮", "语音输入", "聊天信息", "置顶聊天", "消息免打扰"
        )

        private val SERVICE_CARD_TITLES = listOf(
            "微信支付凭证", "付款成功通知", "收款到账通知", "零钱入账通知",
            "支付成功凭证", "转账过期退还通知", "微信支付分", "微信支付小助手"
        )

        private val CARD_JUMP_DETAIL_BUTTONS = listOf(
            "查看账单详情", "点击查看详情", "查看消费明细"
        )

        private val REPEATED_VOUCHER_KEYS = listOf(
            "付款金额", "交易单号", "支付时间", "交易时间"
        )

        private val CHAT_BUBBLE_MARKERS = listOf(
            "恭喜发财", "大吉大利", "领取红包", "查看红包", "发红包给你",
            "转账给你", "待确认收钱", "待确认收款", "朋友已确认收钱",
            "已被对方领取", "已被对方领完", "点击领取", "收款后存入零钱"
        )

        private val ALIPAY_FEED_CARD_MARKERS = listOf(
            "账单助手", "支付助手", "动账提醒", "交易提醒", "收款助手"
        )

        private val CHAT_TIME_DIVIDER_REGEX = Regex(
            "^(?:(?:今天|昨天|前天|上午|下午|晚上|凌晨|星期[一二三四五六日天]|周[一二三四五六日天])\\s*\\d{1,2}:\\d{2}|\\d{1,2}:\\d{2}|\\d{4}年\\d{1,2}月\\d{1,2}日(?:\\s*(?:今天|昨天|前天|上午|下午|晚上)?\\s*\\d{1,2}:\\d{2})?|\\d{1,2}月\\d{1,2}日(?:\\s*(?:上午|下午|晚上)?\\s*\\d{1,2}:\\d{2})?)$"
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

        private val DETAIL_ANCHOR_MARKERS = listOf(
            "账单服务", "发起群收款", "申请电子凭证", "本服务由财付通提供",
            "收单机构", "清算机构", "在此商户的交易", "在此商户的交易记录",
            "定位到聊天位置", "查看往来记录", "联系收款方", "对订单有疑惑", "对此订单有疑问",
            "查看红包详情", "标签和备注", "申请开票"
        )

        private val PAY_SUCCESS_PHRASES = listOf(
            "支付成功", "付款成功", "交易成功", "消费成功", "转账成功",
            "支付完成", "付款完成", "已支付",
            "汇款成功", "收款成功", "已收款", "已存入零钱", "已存入零钱通",
            "免密支付成功", "扣款成功", "退款成功", "已退款", "退款到账", "已退回",
            "对方已收钱", "对方已收款", "对方已领取", "已被领完", "已领完", "已领取", "已提现",
            "已转入", "已转出", "还款成功", "充值成功", "已完成", "已到账", "已入账",
            "payment successful", "transfer successful", "transaction successful", "money received"
        )

        private val DONE_ANCHOR_KEYWORDS = listOf(
            "完成", "返回商家", "查看账单", "查看零钱", "账单详情", "查看详情",
            "done", "ok", "close", "返回", "电子回单"
        )

        private val WALLET_STRICT_DONE_ANCHOR_KEYWORDS = listOf(
            "完成", "返回商家", "查看账单", "查看零钱", "账单详情",
            "done", "ok", "close", "电子回单"
        )

        private val STRICT_PAY_RESULT_DONE_BUTTONS = listOf(
            "完成", "返回商家", "done", "ok"
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

        private val INCOME_PAYMENT_METHOD_LABELS = setOf(
            "收款方式", "收款账户", "存入账户", "入账方式", "入账账户", "退款去向", "退回账户", "退回方式"
        )

        private val EXPENSE_PAYMENT_METHOD_LABELS = setOf(
            "付款方式", "支付方式", "扣款方式", "付款账号", "付款卡号", "扣款银行卡", "扣款账户", "资金来源", "还款银行卡"
        )

        private val INCOME_TIME_KEYWORDS = listOf("收款", "收钱", "退款", "入账", "收红包", "领取")
        private val EXPENSE_TIME_KEYWORDS = listOf("支付", "付款", "扣款", "发红包")

        private val FULL_REFUND_STATUS_KEYWORDS = listOf(
            "退款成功", "已全额退款", "退款到账", "已退款", "refund successful", "refunded"
        )

        private val REFUND_BUTTON_EXCLUSION_REGEX = Regex("(?:申请退款|我要退款|申请售后|退款记录|退款详情|部分已退)")

        private val COUNTERPARTY_RECEIVED_KEYWORDS = listOf(
            "对方已收款", "对方已收钱", "对方已领取", "等待对方收款", "等待对方收钱", "待对方收款", "对方已领"
        )

        private val INCOME_CONFIRM_REGEX = Regex(
            "(?:收到.{0,8}(?:红包|转账|款项|付款)|领到.{0,6}红包|你已收款|已收款|收款成功|已存入零钱通?|退款成功|已全额退款|已退款|退款到账|原路退回|已退回|工资|分红|you\\s+received|money\\s+received|refund\\s+successful)",
            RegexOption.IGNORE_CASE
        )

        private val NOISE_AMOUNT_LINE_KEYWORDS = listOf(
            "支付优惠", "优惠信息", "优惠说明", "优惠明细", "商家优惠", "平台优惠", "官方立减", "立减金",
            "消费券", "代金券", "优惠券", "商家券", "消费金", "花呗金", "体验金", "鼓励金", "淘金币",
            "满减", "抵扣", "抵用", "已优惠", "优惠了", "已减", "共减", "直降", "免单",
            "已省", "共省", "省了", "立省", "省钱", "节省", "返现", "补贴", "减免", "集分宝",
            "碰一下", "笔笔攒", "运费险", "组合支付", "支付宝红包",
            "立减", "优惠", "红包", "折扣", "积分",
            "当前余额", "可用余额", "账户余额", "参考汇率", "汇率", "手续费", "服务费", "费率"
        )

        private val MIXED_PAYMENT_NOISE_REGEX = Regex(
            "(?:红包|优惠|立减|满减|代金券|优惠券|消费券|商家券|消费金|花呗金|体验金|鼓励金|淘金币|集分宝|积分|抵扣|抵用|组合支付|补贴|减免|返现|碰一下)"
        )

        private val MIXED_PAYMENT_DISCOUNT_SUFFIX_REGEX = Regex(
            "\\s*[+/]\\s*(?:支付宝)?(?:红包|立减金|消费券|优惠券|代金券|商家券|消费金|花呗金|体验金|鼓励金|淘金币|集分宝|积分|抵扣|抵用|优惠|补贴|减免)[^+/]*"
        )

        private val SAVINGS_AMOUNT_REGEX = Regex(
            "(?:(?<![\\u4e00-\\u9fa5])省\\s*(?:US\\$|HK\\$|NT\\$|S\\$|A\\$|C\\$|JP¥|USD|EUR|GBP|CNY|RMB|[¥￥$€£₩])?\\s*[0-9]+|省\\s*(?:[¥￥$€£₩]|USD|EUR|GBP|CNY|RMB)\\s*[0-9]+|省\\s*[0-9]+(?:\\.[0-9]{1,2})?\\s*(?:元|块))",
            RegexOption.IGNORE_CASE
        )

        private val FULL_REDUCTION_REGEX = Regex("满\\s*[0-9.]+\\s*减\\s*[0-9.]+")

        private val ENGLISH_NOISE_AMOUNT_REGEX = Regex(
            "\\b(?:discounts?|coupons?|vouchers?|cashback|balances?|(?:service\\s+)?fees?)\\b",
            RegexOption.IGNORE_CASE
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
        private val PARENTHESIZED_DISCOUNT_STRIP_REGEX = Regex(
            "[(（](?=[^()（）]*[0-9])[^()（）]*(?:优惠信息|优惠|立减|红包|折扣|抵扣|抵用|满减|满\\s*[0-9.]+\\s*减\\s*[0-9.]+|代金券|优惠券|消费券|商家券|消费金|花呗金|体验金|鼓励金|淘金币|补贴|减免|已减|共减|直降|免单|返现|已省|共省|立省|省了|省钱|节省|省\\s*(?:[¥￥$€£₩]|[0-9]+\\s*(?:元|块))|碰一下|笔笔攒|运费险|积分|集分宝|余额|汇率|手续费|服务费|discount|coupon|voucher|cashback|balance|fee)[^()（）]*[)）]",
            RegexOption.IGNORE_CASE
        )
        private val USAGE_DISCOUNT_STRIP_REGEX = Regex(
            "(?:已?使用|用了|使用|叠加|含|共)?\\s*[+-]?\\s*(?:US\\$|HK\\$|NT\\$|S\\$|A\\$|C\\$|JP¥|USD|EUR|GBP|HKD|JPY|SGD|AUD|CAD|CHF|TWD|KRW|CNY|RMB|[¥￥$€£₩])?\\s*[0-9]+(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?\\s*(?:元|块|USD|EUR|GBP|CNY|RMB)?\\s*(?:的)?(?:立减金|代金券|优惠券|消费券|商家券|消费金|花呗金|体验金|鼓励金|淘金币|抵用券|红包|集分宝|积分|折扣券)",
            RegexOption.IGNORE_CASE
        )
        private val DISCOUNT_BALANCE_STRIP_REGEX = Regex(
            "(?:当前余额|可用余额|账户余额|余额|参考汇率|汇率|支付优惠|优惠信息|优惠说明|优惠明细|商家优惠|平台优惠|碰一下立减|碰一下|官方立减|微信立减金|立减金|立减|已优惠|优惠了|优惠金额|优惠|支付宝红包|红包抵扣|红包优惠|满减|代金券|优惠券|消费券|商家券|消费金|花呗金|体验金|鼓励金|淘金币|折扣|抵扣|抵用|补贴|减免|已减|共减|直降|免单|返现|笔笔攒|运费险|组合支付|集分宝|积分|手续费|服务费|费率|已省|共省|立省|省了|省钱|节省|(?<![\\u4e00-\\u9fa5])省|discount|coupon|voucher|cashback|balance|service\\s+fee|fee)[:：]?\\s*[+-]?\\s*(?:US\\$|HK\\$|NT\\$|S\\$|A\\$|C\\$|JP¥|USD|EUR|GBP|HKD|JPY|SGD|AUD|CAD|CHF|TWD|KRW|CNY|RMB|[¥￥$€£₩])?\\s*[+-]?\\s*[0-9]+(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?\\s*(?:元|块|USD|EUR|GBP|CNY|RMB)?",
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
            "^(?:消费金额|外币金额|原币金额|原始金额|实付|实付款|付款金额|支付金额|折算人民币|折合人民币|支付优惠|优惠信息|优惠说明|优惠明细|商家优惠|平台优惠|碰一下立减|碰一下|官方立减|微信立减金|立减金|已优惠|优惠了|优惠|立减|满减|代金券|优惠券|消费券|商家券|消费金|花呗金|体验金|鼓励金|淘金币|折扣|抵扣|抵用|补贴|减免|已减|共减|直降|免单|返现|笔笔攒|运费险|组合支付|集分宝|已省|共省|立省|省了|省钱|节省|支付宝红包|红包(?!$)|使用\\s*[0-9.]+\\s*元|满\\s*[0-9.]+\\s*减\\s*[0-9.]+|当前余额|可用余额|余额|尾号|卡号)"
        )
        private val MERCHANT_ACTION_PREFIX_REGEX = Regex("^(?:向|给|付款给|转账给|转给|在)\\s*")
        private val MERCHANT_ACTION_SUFFIX_REGEX = Regex("\\s*(?:付款|支付|转账|消费|收款)$")
        private val PURE_NUMBER_OR_CARD_REGEX = Regex("^[\\d*xX()（）\\s-]+$")

        private val UI_STOP_WORDS = setOf(
            "账单详情", "账单详情页", "交易详情", "收支详情", "转账详情", "收款详情", "退款详情", "订单详情",
            "交易明细", "账单明细", "电子回单", "支付结果", "付款结果", "交易结果",
            "支付成功", "付款成功", "交易成功", "消费成功", "转账成功", "汇款成功", "收款成功",
            "已收款", "已存入零钱", "退款成功", "已退款", "退款到账", "已退回",
            "完成", "返回", "关闭", "确定", "确认", "取消", "返回商家", "查看账单", "查看详情",
            "查看零钱", "复制", "分享", "截屏", "投诉", "联系客服", "联系收款方", "常见问题",
            "对订单有疑惑", "对此订单有疑问", "申请退款", "申请售后", "申请开票", "在此商户的交易记录",
            "在此商户的交易", "账单服务", "发起群收款", "申请电子凭证", "本服务由财付通提供",
            "定位到聊天位置", "查看往来记录", "全部账单", "查看红包详情", "红包详情",
            "复制链接", "在浏览器打开", "在默认浏览器打开", "刷新", "搜索页面内容", "字体大小", "调整字体", "浮窗",
            "发送给朋友", "分享到朋友圈",
            "更多", "删除账单", "标签和备注", "添加备注", "备注…", "备注...", "分类", "计入收支", "不计入收支",
            "添加", "去添加", "点击添加", "请选择", "开关", "未设置", "暂无", "去设置",
            "微信", "微信支付", "微信收款助手", "支付宝", "云闪付", "财付通", "招商银行", "工商银行", "建设银行",
            "农业银行", "中国银行", "交通银行", "平安银行", "wise", "paypal", "revolut",
            "支付", "收款", "转账", "付款", "人民币", "美元", "欧元", "英镑", "done", "ok", "close"
        )

        private val UI_STOP_PREFIXES = listOf(
            "对订单有疑", "对此订单有疑", "在此商户的", "联系收款方", "申请开票", "申请退款", "申请售后",
            "查看", "本服务由", "网页由", "该网页由", "此网页由", "页面由"
        )

        private val GENERIC_CHANNEL_MERCHANTS = setOf(
            "微信支付", "支付宝", "云闪付", "扫码付款", "二维码收款", "转账", "收款", "消费", "支付"
        )

        private val CHROME_NOISE_LINES = setOf(
            "返回", "复制", "已复制", "copy", "常见问题", "对订单有疑惑", "对此订单有疑问",
            "在此商户的交易记录", "申请开票", "标签和备注", "添加备注", "备注…", "备注...",
            "添加", "去添加", "点击添加", "请选择", "开关", "未设置", "暂无", "去设置", "删除账单"
        )
    }
}

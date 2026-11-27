package com.yubrae.app.capture.screen

import com.yubrae.app.capture.LocalRuleParser
import com.yubrae.app.core.contract.CaptureInput
import com.yubrae.app.core.contract.ParsedBillDraft
import com.yubrae.app.core.model.CurrencyCatalog
import com.yubrae.app.core.model.MoneyAmount
import com.yubrae.app.data.db.AccountAliasEntity
import com.yubrae.app.data.db.AccountEntity
import com.yubrae.app.data.db.AliasMatchType
import com.yubrae.app.data.db.CaptureChannel
import com.yubrae.app.data.db.CategoryEntity
import com.yubrae.app.data.db.TransactionType
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
        val rawNormalizedLines = normalizeLinesWithNodes(snapshot)
        if (rawNormalizedLines.isEmpty()) return ScreenPageKind.UNKNOWN
        val normalizedLines = if (snapshot.packageName.lowercase(Locale.ROOT) in ECOMMERCE_AND_DELIVERY_PACKAGES) {
            truncateBottomRecommendationFeed(rawNormalizedLines)
        } else {
            rawNormalizedLines
        }
        val lines = normalizedLines.map { it.text }
        val fieldResult = extractFields(lines)
        return classifyPageInternal(
            packageName = snapshot.packageName,
            className = snapshot.className,
            lines = lines,
            fields = fieldResult.fields,
            ignoredLineIndices = fieldResult.ignoredLineIndices
        ).first
    }

    /**
     * 判断非聊天/非列表窗口是否呈现强账单详情特征，供无障碍服务末轮诊断抓取日志兜底使用。
     */
    internal fun hasStrongBillDetailAnchors(snapshot: ScreenSnapshot): Boolean {
        if (!isWatchedPackage(snapshot.packageName)) return false
        if (isBlockedChatOrFeedWindow(snapshot.packageName, snapshot.className)) return false
        val normalizedLines = normalizeLinesWithNodes(snapshot)
        if (normalizedLines.isEmpty()) return false
        val lines = normalizedLines.map { it.text }
        val lowerLines = lines.map { it.lowercase(Locale.ROOT) }
        val lowerJoined = lowerLines.joinToString("\n")

        if (CHAT_STRONG_MARKERS.any { lowerJoined.contains(it) }) return false
        if (lowerLines.contains("表情") && lowerLines.contains("发送")) return false
        if ((lowerLines.contains("通讯录") && lowerLines.contains("发现")) ||
            (lowerLines.contains("首页") && lowerLines.contains("理财") && lowerLines.contains("我的"))
        ) {
            return false
        }
        val hasServiceBottomMenu = (lowerLines.contains("我的账单") && lowerLines.contains("支付服务")) ||
            (lowerLines.contains("我的账单") && lowerLines.contains("更多服务")) ||
            (lowerLines.contains("账单") && lowerLines.contains("支付服务"))
        if (hasServiceBottomMenu) return false

        val cardJumpDetailCount = lowerLines.count { line ->
            CARD_JUMP_DETAIL_BUTTONS.any { btn -> line == btn || line.contains(btn) }
        }
        if (cardJumpDetailCount >= 2) return false

        val hasDetailHeader = lowerLines.any { line ->
            DETAIL_HEADER_KEYWORDS.any { line == it || line.startsWith("$it ") || line.startsWith("$it·") }
        }
        val hasDetailAnchorMarker = DETAIL_ANCHOR_MARKERS.any { marker ->
            lowerLines.any { it.contains(marker) }
        }
        if (!hasDetailHeader && !hasDetailAnchorMarker &&
            SERVICE_CARD_TITLES.any { title -> lowerLines.any { it.contains(title) } }
        ) {
            return false
        }
        if (!hasDetailAnchorMarker &&
            CHAT_BUBBLE_MARKERS.any { marker -> lowerLines.any { it.contains(marker) } }
        ) {
            return false
        }
        val fields = extractFields(lines).fields
        val distinctDetailKeys = fields.map { it.key }.filter { it in DETAIL_SIGNAL_KEYS }.toSet().size
        val topLines = lowerLines.take(6)
        val isBillListOverview = (BILL_LIST_MARKERS.any { marker -> topLines.any { it == marker || it.contains(marker) } } ||
            (topLines.any { it == "账单" || it == "我的账单" } &&
                lowerLines.any { it == "支出" || it.startsWith("支出 ") || it.startsWith("支出¥") } &&
                lowerLines.any { it == "收入" || it.startsWith("收入 ") || it.startsWith("收入¥") })) &&
            !hasDetailHeader &&
            !hasDetailAnchorMarker &&
            distinctDetailKeys < 2
        if (isBillListOverview) return false

        val matchedAnchorCount = STRONG_BILL_ANCHOR_KEYWORDS.count { anchor ->
            lowerLines.any { it.contains(anchor) }
        }
        return (hasDetailHeader && matchedAnchorCount >= 1) || matchedAnchorCount >= 2
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

        val rawNormalizedLines = normalizeLinesWithNodes(snapshot)
        if (rawNormalizedLines.isEmpty()) {
            return ScreenParseOutcome(pageKind = ScreenPageKind.UNKNOWN, draft = null)
        }
        val pkgLower = snapshot.packageName.lowercase(Locale.ROOT)
        val normalizedLines = if (pkgLower in ECOMMERCE_AND_DELIVERY_PACKAGES) {
            truncateBottomRecommendationFeed(rawNormalizedLines)
        } else {
            rawNormalizedLines
        }
        val lines = normalizedLines.map { it.text }

        val fieldResult = extractFields(lines)
        val fields = fieldResult.fields
        val (pageKind, prepayReason) = classifyPageInternal(
            packageName = snapshot.packageName,
            className = snapshot.className,
            lines = lines,
            fields = fields,
            ignoredLineIndices = fieldResult.ignoredLineIndices
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
            screenHeightPx = snapshot.screenHeightPx,
            pageKind = pageKind
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

        val txType = inferTransactionType(lines, fields, resolvedAmount.dominantSign, pageKind)
        val timestamp = extractTransactionTimestamp(fields, lines, snapshot.capturedAtMillis)
        val rawPaymentMethod = sanitizePaymentMethodText(extractFirstFieldValue(fields, FieldKey.PAYMENT_METHOD))
        val paymentMethodText = rawPaymentMethod.ifBlank {
            if (pageKind == ScreenPageKind.PAY_SUCCESS) extractStandalonePaymentMethodLine(lines) else ""
        }
        val accountHint = extractAccountHint(paymentMethodText, summaryText)

        var merchant: String
        var goodsDesc: String
        var rawGoodsForTags = ""
        if (pageKind == ScreenPageKind.ORDER_DETAIL) {
            val orderInfo = extractOrderDetailMerchantAndGoods(lines, fields)
            merchant = orderInfo.merchant.ifBlank { resolveMerchant(lines, fields, txType, snapshot.packageName) }
            goodsDesc = orderInfo.goodsSummary.ifBlank { extractGoods(fields) }
        } else {
            val baseMerchant = resolveMerchant(lines, fields, txType, snapshot.packageName)
            val baseGoods = extractGoods(fields)
            rawGoodsForTags = baseGoods
            if ((snapshot.packageName.equals(PKG_WECHAT, ignoreCase = true) ||
                    snapshot.packageName.equals(PKG_ALIPAY, ignoreCase = true)) &&
                baseGoods.isNotBlank() &&
                isPlatformShellMerchant(baseMerchant)
            ) {
                val unpacked = unpackPlatformShellGoods(baseMerchant, baseGoods)
                merchant = unpacked.first
                goodsDesc = unpacked.second
            } else {
                merchant = baseMerchant
                goodsDesc = baseGoods
            }
            if (pkgLower in PHONE_WALLET_PACKAGES &&
                (merchant.isBlank() || isGenericWalletBrand(merchant))
            ) {
                merchant = localRuleParser.extractTransitCardName(summaryText)
                    ?: merchant.ifBlank { "公交卡" }
            }
            if (merchant.isNotBlank() &&
                isP2pPersonalCounterpartyToClear(
                    merchant = merchant,
                    lines = lines,
                    ignoredLineIndices = fieldResult.ignoredLineIndices
                )
            ) {
                merchant = ""
            }
        }

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
        val isTaobaoTakeout = pkgLower == PKG_TAOBAO && hasTaobaoTakeoutCue(lines)
        val extraCategoryHints = buildList {
            if (pkgLower in TAKEOUT_PACKAGES || isTaobaoTakeout) add("外卖")
            if (pkgLower in PHONE_WALLET_PACKAGES &&
                TRANSIT_VOUCHER_CUES.any { summaryText.contains(it, ignoreCase = true) }
            ) {
                add("地铁公交")
            }
        }
        val cleanSummary = buildCleanClassificationSummary(lines, pageKind, snapshot.packageName)
        val categoryContext = listOf(merchant, goodsDesc, rawGoodsForTags, cleanSummary, extraCategoryHints.joinToString(" "))
            .filter { it.isNotBlank() }
            .joinToString(" ")
        val resolvedCategory = localRuleParser.resolveCategory(
            type = txType,
            merchant = merchant,
            fullText = categoryContext,
            ruleDefaultCategoryName = defaultCat,
            categories = categories
        )
        val resolvedCategoryName = resolvedCategory?.name ?: defaultCat

        val autoTags = localRuleParser.inferAutoTags(
            sourcePackage = snapshot.packageName,
            merchant = merchant,
            fullText = listOf(rawGoodsForTags, goodsDesc, cleanSummary).filter { it.isNotBlank() }.joinToString(" "),
            categoryName = resolvedCategoryName,
            currency = resolvedAmount.origCurrency
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
            orderNo = orderNo,
            isOrderDetail = pageKind == ScreenPageKind.ORDER_DETAIL
        )

        val parserUsed = when (pageKind) {
            ScreenPageKind.BILL_DETAIL -> "SCREEN_BILL_DETAIL"
            ScreenPageKind.ORDER_DETAIL -> "SCREEN_ORDER_DETAIL"
            else -> "SCREEN_PAY_SUCCESS"
        }
        val confidence = when (pageKind) {
            ScreenPageKind.BILL_DETAIL -> 0.95f
            ScreenPageKind.ORDER_DETAIL -> 0.93f
            else -> 0.92f
        }

        val draft = ParsedBillDraft(
            type = txType,
            origAmountMicros = resolvedAmount.origAmountMicros,
            origCurrency = resolvedAmount.origCurrency,
            accountId = accountId,
            accountHint = accountHint,
            categoryId = resolvedCategory?.id,
            categoryName = resolvedCategoryName,
            merchant = merchant,
            note = note,
            timestamp = timestamp,
            confidence = confidence,
            parserUsed = parserUsed,
            externalOrderNo = orderNo,
            manualAccountAmountMicros = manualAccountAmountMicros,
            tags = autoTags
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
            return snapshot.nodes.flatMap { node ->
                node.text.split('\n', '\r').mapNotNull { part ->
                    val cleaned = cleanLineText(part)
                    cleaned.takeIf { it.isNotEmpty() }?.let {
                        NormalizedLine(it, node.topPx, node.heightPx, node.isContentDesc)
                    }
                }
            }
        }
        if (snapshot.nodes.isEmpty()) {
            return snapshot.texts.flatMap { text ->
                text.split('\n', '\r').mapNotNull { part ->
                    val cleaned = cleanLineText(part)
                    cleaned.takeIf { it.isNotEmpty() }?.let { NormalizedLine(it) }
                }
            }
        }

        val cleanedNodes = snapshot.nodes.flatMap { node ->
            node.text.split('\n', '\r').mapNotNull { part ->
                val cleaned = cleanLineText(part)
                cleaned.takeIf { it.isNotEmpty() }?.let {
                    NormalizedLine(it, node.topPx, node.heightPx, node.isContentDesc)
                }
            }
        }
        var nodeCursor = 0
        return snapshot.texts.flatMap { rawText ->
            rawText.split('\n', '\r').mapNotNull { part ->
                val cleaned = cleanLineText(part)
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
                            val inlineActual = INLINE_ACTUAL_PAID_REGEX.find(line)?.groupValues?.getOrNull(1)?.trim().orEmpty()
                            if (inlineActual.isNotEmpty()) {
                                result.add(ExtractedField(FieldKey.ACTUAL_AMOUNT, "实付", inlineActual, i, i))
                                i++
                                continue
                            }
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
                        val isAmbiguousStatusAmount = key == FieldKey.ACTUAL_AMOUNT &&
                            (rawLabel == "已支付" || rawLabel == "已付")
                        if (!isPlaceholderFieldValue(inlineVal) &&
                            (!isAmbiguousStatusAmount || looksLikeAmountLineValue(inlineVal))
                        ) {
                            result.add(ExtractedField(key, rawLabel, inlineVal, i, i))
                        }
                        i++
                        continue
                    } else {
                        val nextLine = lines.getOrNull(i + 1)
                        val isAmbiguousStatusAmount = key == FieldKey.ACTUAL_AMOUNT &&
                            (rawLabel == "已支付" || rawLabel == "已付")
                        if (!isAmbiguousStatusAmount || looksLikeAmountLineValue(nextLine)) {
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
            }

            val exactKey = matchFieldKey(line)
            if (exactKey != null) {
                if (exactKey == FieldKey.IGNORED_AMOUNT) {
                    val nextLine = lines.getOrNull(i + 1)
                    val isTopMerchantFollowedByHero = i <= 3 &&
                        nextLine != null &&
                        (isDirectionalHeroAmountLine(nextLine) ||
                            (isStandaloneAmountLine(nextLine) && !containsNoiseAmountKeyword(nextLine)))
                    if (isTopMerchantFollowedByHero) {
                        i++
                        continue
                    }
                    i = consumeIgnoredTail(lines, i, line, result, ignoredIndices)
                    continue
                }
                val nextLine = lines.getOrNull(i + 1)
                val trimmedLabel = line.trim().trimEnd(':', '：')
                val isStatusAmbiguousAmountLabel = exactKey == FieldKey.ACTUAL_AMOUNT &&
                    (trimmedLabel == "已支付" || trimmedLabel == "已付")
                if (isStatusAmbiguousAmountLabel && !looksLikeAmountLineValue(nextLine)) {
                    i++
                    continue
                }
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
                    val inlineActual = INLINE_ACTUAL_PAID_REGEX.find(line)?.groupValues?.getOrNull(1)?.trim().orEmpty()
                    if (inlineActual.isNotEmpty()) {
                        result.add(ExtractedField(FieldKey.ACTUAL_AMOUNT, "实付", inlineActual, i, i))
                        i++
                        continue
                    }
                    result.add(ExtractedField(key, label, value, i, i))
                    ignoredIndices.add(i)
                    i = tryConsumeIgnoredNextLine(lines, i, label, value, result, ignoredIndices)
                    continue
                } else if (!isPlaceholderFieldValue(value)) {
                    val isAmbiguousStatusAmount = key == FieldKey.ACTUAL_AMOUNT &&
                        (label == "已支付" || label == "已付")
                    if (!isAmbiguousStatusAmount || looksLikeAmountLineValue(value)) {
                        result.add(ExtractedField(key, label, value, i, i))
                    }
                }
            } else {
                val inlineActual = INLINE_ACTUAL_PAID_REGEX.find(line)?.groupValues?.getOrNull(1)?.trim().orEmpty()
                if (inlineActual.isNotEmpty() && !line.contains("不实付") && !line.contains("非实付")) {
                    result.add(ExtractedField(FieldKey.ACTUAL_AMOUNT, "实付", inlineActual, i, i))
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
            !isDirectionalHeroAmountLine(nextLine) &&
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
        if (isPlaceholderFieldValue(nextLine)) return false
        if (key == FieldKey.STATUS && isKnownStatusLine(nextLine)) {
            return true
        }
        if (isActionButtonOnly(nextLine)) {
            return false
        }
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
        if (line1 == null ||
            isDirectionalHeroAmountLine(line1) ||
            isStrictFieldLabelLine(line1) ||
            isActionButtonOnly(line1) ||
            isPlaceholderFieldValue(line1)
        ) {
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
            !isDirectionalHeroAmountLine(line2) &&
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

    private fun isKnownStatusLine(line: String): Boolean {
        val lower = line.trim().lowercase(Locale.ROOT)
        return PAY_SUCCESS_PHRASES.any { lower == it } ||
            IGNORED_STATUS_KEYWORDS.any { lower == it } ||
            STATUS_ANCHOR_PHRASES.any { lower == it }
    }

    private fun matchSpaceSeparatedField(line: String): Triple<FieldKey, String, String>? {
        val lower = line.lowercase(Locale.ROOT)
        for ((prefix, key) in SPACE_SEPARATED_PREFIXES) {
            if (lower.startsWith(prefix) && line.length > prefix.length) {
                val nextChar = line[prefix.length]
                val isSpace = nextChar == ' ' || nextChar == '\t'
                val isCompactAmountOrOrder = key in COMPACT_PREFIX_KEYS &&
                    (nextChar.isDigit() || nextChar in COMPACT_AMOUNT_LEAD_CHARS)
                if (isSpace || isCompactAmountOrOrder) {
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
        fields: List<ExtractedField>,
        ignoredLineIndices: Set<Int> = emptySet()
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

        val pkgLower = packageName.lowercase(Locale.ROOT)
        if (pkgLower in ECOMMERCE_AND_DELIVERY_PACKAGES) {
            return classifyEcommerceOrTakeoutPage(lines, lowerLines, fields, ignoredLineIndices)
        }

        if (pkgLower in PHONE_WALLET_PACKAGES) {
            val hasTransitNoise = lowerLines.any { line ->
                TRANSIT_NON_PAYMENT_NOISE_KEYWORDS.any { line.contains(it) }
            }
            if (hasTransitNoise) {
                val hasExplicitDeductWithAmount = lines.any { line ->
                    val l = line.lowercase(Locale.ROOT)
                    !l.contains("失败") &&
                        !containsNoiseAmountKeyword(line) &&
                        TRANSIT_DEDUCT_PHRASES.any { l.contains(it) } &&
                        AMOUNT_TOKEN_REGEX.findAll(stripNonAmountClauses(line)).any { m ->
                            val num = m.groupValues.getOrNull(4).orEmpty()
                            (MoneyAmount.parseToMicros(num) ?: 0L) > 0L
                        }
                }
                if (!hasExplicitDeductWithAmount) {
                    return ScreenPageKind.UNKNOWN to "交通卡非扣费状态页面"
                }
            }

            val hasTransitCue = lowerLines.any { line ->
                TRANSIT_VOUCHER_CUES.any { line.contains(it) }
            }
            val hasTransitResultPhrase = lowerLines.any { line ->
                TRANSIT_RESULT_PHRASES.any { line.contains(it) }
            }
            val hasValidAmount = fields.any { it.key in EXTRACTED_AMOUNT_KEYS } ||
                lines.any { line ->
                    !containsNoiseAmountKeyword(line) &&
                        AMOUNT_TOKEN_REGEX.findAll(stripNonAmountClauses(line)).any { m ->
                            val num = m.groupValues.getOrNull(4).orEmpty()
                            val hasCur = m.groupValues.getOrNull(2).orEmpty().isNotBlank() ||
                                m.groupValues.getOrNull(5).orEmpty().isNotBlank()
                            val hasSign = m.groupValues.getOrNull(1).orEmpty().isNotBlank() ||
                                m.groupValues.getOrNull(3).orEmpty().isNotBlank()
                            (hasCur || hasSign || num.contains('.')) &&
                                (MoneyAmount.parseToMicros(num) ?: 0L) > 0L
                        }
                }
            val isPreRecharge = lowerLines.any { line ->
                line.contains("待充值") || line.contains("立即充值") ||
                    line.contains("确认充值") || line.contains("选择充值金额")
            }
            if (hasTransitCue && hasTransitResultPhrase && hasValidAmount && !isPreRecharge) {
                return ScreenPageKind.PAY_SUCCESS to ""
            }
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

        // 微信支付 / 支付宝服务号通知卡片标题（若已有账单详情头、详情锚点或 >=2 个强详情字段，避免误杀含「微信支付分」的真实账单）
        if (!hasDetailHeader &&
            !hasDetailAnchorMarker &&
            strongDetailOnlyCount < 2 &&
            SERVICE_CARD_TITLES.any { title -> lowerLines.any { it.contains(title) } }
        ) {
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
        // 仅当卡片跳转按钮是主体（>=2 个跳转按钮，或无详情头/锚点/强详情字段且非真实支付成功页）时才判为服务号卡片
        if (hasCardJumpDetailButton) {
            val isCardJumpMainBody = cardJumpDetailCount >= 2 ||
                (!hasDetailHeader && !hasDetailAnchorMarker && strongDetailOnlyCount == 0)
            val isRealPayResultWithJump = hasPaySuccessPhrase &&
                hasStrictResultDoneButton &&
                !hasDetailAnchorMarker &&
                strongDetailOnlyCount == 0 &&
                chatTimeDividerCount == 0
            if (isCardJumpMainBody && !isRealPayResultWithJump) {
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
                isAlipay -> hasDoneAnchor || hasDetailHeader || distinctDetailKeys >= 1 ||
                    (!hasPrepayCheckoutMarker && hasValidStandalonePositiveAmountLine(lines, ignoredLineIndices))
                else -> hasBankOrDetailAnchor
            }
            if (matched) {
                return ScreenPageKind.PAY_SUCCESS to ""
            }
        }

        return ScreenPageKind.UNKNOWN to ""
    }

    private fun classifyEcommerceOrTakeoutPage(
        lines: List<String>,
        lowerLines: List<String>,
        fields: List<ExtractedField>,
        ignoredLineIndices: Set<Int>
    ): Pair<ScreenPageKind, String> {
        val topLines = lowerLines.take(6)
        if (ECOMMERCE_HARD_BLOCKERS.any { blocker -> lowerLines.any { it.contains(blocker) } } ||
            topLines.any { it == "我的订单" }
        ) {
            return ScreenPageKind.UNKNOWN to "电商非已支付订单页不触发"
        }

        // 订单列表多卡片防误触：出现 >= 2 次订单号 / 实付 / 再来一单 / 查看物流 / 确认收货
        val repeatedOrderNoCount = lowerLines.count { line ->
            REPEATED_ORDER_NO_LINE_REGEX.containsMatchIn(line)
        }
        val repeatedActualPaidCount = lowerLines.count { line ->
            REPEATED_ACTUAL_PAID_LINE_REGEX.containsMatchIn(line)
        }
        val repeatedAgainCount = lowerLines.count { it.contains("再来一单") }
        val repeatedLogisticsCount = lowerLines.count { it.contains("查看物流") }
        val repeatedConfirmReceiptCount = lowerLines.count { it.contains("确认收货") }
        if (repeatedOrderNoCount >= 2 ||
            repeatedActualPaidCount >= 2 ||
            repeatedAgainCount >= 2 ||
            repeatedLogisticsCount >= 2 ||
            repeatedConfirmReceiptCount >= 2
        ) {
            return ScreenPageKind.UNKNOWN to "订单列表多卡片页面不触发"
        }

        val hasPaidStatus = lowerLines.any { line ->
            ORDER_PAID_STATUS_KEYWORDS.any { line.contains(it) }
        } || fields.any { field ->
            field.key == FieldKey.STATUS && ORDER_PAID_STATUS_KEYWORDS.any {
                field.value.lowercase(Locale.ROOT).contains(it)
            }
        }

        val hasOrderAnchorOrNo = fields.any { it.key == FieldKey.ORDER_NO } ||
            lowerLines.any { line ->
                ORDER_DETAIL_ANCHORS.any { line.contains(it) } ||
                    ETA_DELIVERY_ANCHOR_REGEX.containsMatchIn(line)
            }

        val hasActualPaidAmount = fields.any {
            it.key == FieldKey.ACTUAL_AMOUNT && it.value.any(Char::isDigit)
        } || lowerLines.any { line ->
            ACTUAL_PAID_HINT_REGEX.containsMatchIn(line)
        }

        val hasSubtotalOrTotalAmount = !hasActualPaidAmount &&
            hasSubtotalOrTotalAmount(lines, ignoredLineIndices)

        val hasPaySuccessPhrase = lowerLines.take(16).any { line ->
            !line.contains('?') && !line.contains('？') &&
                PAY_SUCCESS_PHRASES.any { phrase ->
                    line == phrase ||
                        line.startsWith("$phrase ") ||
                        line.startsWith("$phrase，") ||
                        line.startsWith("$phrase!") ||
                        line.startsWith("$phrase！")
                }
        }
        val hasStrictDone = lowerLines.any { line ->
            STRICT_PAY_RESULT_DONE_BUTTONS.any { line == it || line.startsWith("$it(") || line.startsWith("$it（") }
        }
        val hasEcommercePaySuccessAction = lowerLines.any { line ->
            ECOMMERCE_PAY_SUCCESS_ACTION_WORDS.any { line == it || line.startsWith("$it(") || line.startsWith("$it（") }
        }
        val hasFulfillmentStatus = lowerLines.any { line ->
            ORDER_FULFILLMENT_STATUS_KEYWORDS.any { line.contains(it) }
        }
        val isPurePaySuccessLayout = hasPaySuccessPhrase &&
            (hasStrictDone || lowerLines.any { line ->
                PURE_ECOMMERCE_PAY_RESULT_BUTTONS.any { line == it || line.startsWith("$it(") || line.startsWith("$it（") }
            }) &&
            fields.none { it.key == FieldKey.ORDER_NO } &&
            !hasFulfillmentStatus

        if (hasPaidStatus &&
            hasOrderAnchorOrNo &&
            (hasActualPaidAmount || hasSubtotalOrTotalAmount) &&
            !isPurePaySuccessLayout
        ) {
            return ScreenPageKind.ORDER_DETAIL to ""
        }

        if (hasPaySuccessPhrase && (
                hasStrictDone ||
                    hasEcommercePaySuccessAction ||
                    hasActualPaidAmount ||
                    hasValidStandalonePositiveAmountLine(lines, ignoredLineIndices)
                )
        ) {
            return ScreenPageKind.PAY_SUCCESS to ""
        }

        return ScreenPageKind.UNKNOWN to "电商非已支付订单页不触发"
    }

    private fun truncateBottomRecommendationFeed(normalizedLines: List<NormalizedLine>): List<NormalizedLine> {
        for ((index, item) in normalizedLines.withIndex()) {
            if (index < 2) continue
            val trimmed = item.text.trim()
            if (trimmed.length > 20) continue
            val lower = trimmed.lowercase(Locale.ROOT)
            if (ECOMMERCE_RECOMMENDATION_BOUNDARY_KEYWORDS.any { lower.contains(it) }) {
                return normalizedLines.subList(0, index)
            }
        }
        return normalizedLines
    }

    private fun hasValidStandalonePositiveAmountLine(
        lines: List<String>,
        ignoredLineIndices: Set<Int>
    ): Boolean {
        for ((index, line) in lines.withIndex()) {
            if (index in ignoredLineIndices) continue
            if (matchFieldKey(line) != null) continue
            if (isMixedPaymentBreakdownLine(line)) continue
            val cleanedLine = stripNonAmountClauses(line)
            if (cleanedLine.isBlank() || containsNoiseAmountKeyword(cleanedLine)) continue
            val trimmed = cleanedLine.trim()
            if (!isStandaloneAmountLine(trimmed)) continue
            if (trimmed.contains('-')) continue
            val found = extractCandidatesFromText(
                text = trimmed,
                role = AmountRole.HERO,
                lineIndex = index,
                allowBareDecimal = true,
                allowBareInteger = false
            )
            if (found.any { it.amountMicros > 0L }) {
                return true
            }
        }
        return false
    }

    private fun looksLikeAmountLineValue(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        val trimmed = text.trim()
        if (FULL_DATETIME_REGEX.containsMatchIn(trimmed) ||
            MONTH_DAY_TIME_REGEX.containsMatchIn(trimmed) ||
            TIME_ONLY_STRIP_REGEX.matches(trimmed)
        ) {
            return false
        }
        val cleaned = stripNonAmountClauses(trimmed)
        if (cleaned.isBlank() || containsNoiseAmountKeyword(cleaned)) return false
        return extractCandidatesFromText(
            text = cleaned,
            role = AmountRole.ACTUAL_PAID,
            lineIndex = 0,
            allowBareDecimal = true,
            allowBareInteger = false
        ).isNotEmpty()
    }

    private fun hasSubtotalOrTotalAmount(
        lines: List<String>,
        ignoredLineIndices: Set<Int>
    ): Boolean = extractSubtotalOrTotalCandidates(lines, emptyList(), ignoredLineIndices).isNotEmpty()

    private fun extractSubtotalOrTotalCandidates(
        lines: List<String>,
        normalizedLines: List<NormalizedLine>,
        ignoredLineIndices: Set<Int>
    ): List<AmountCandidate> {
        val result = mutableListOf<AmountCandidate>()
        for ((index, line) in lines.withIndex()) {
            if (index in ignoredLineIndices) continue
            val trimmed = line.trim()
            if (trimmed.isEmpty() || containsNoiseAmountKeyword(trimmed)) continue

            val inlineMatch = SUBTOTAL_OR_TOTAL_AMOUNT_REGEX.find(trimmed)
            if (inlineMatch != null) {
                val amountPart = inlineMatch.groupValues.getOrNull(1)?.trim().orEmpty()
                if (amountPart.isNotEmpty() && !amountPart.contains('-')) {
                    val heightPx = normalizedLines.getOrNull(index)?.heightPx ?: 0
                    result += extractCandidatesFromText(
                        text = amountPart,
                        role = AmountRole.TRANSACTION_AMOUNT,
                        lineIndex = index,
                        allowBareDecimal = true,
                        allowBareInteger = false,
                        heightPx = heightPx
                    )
                }
                continue
            }

            if (SUBTOTAL_OR_TOTAL_LABEL_REGEX.matches(trimmed)) {
                val nextIndex = index + 1
                if (nextIndex in ignoredLineIndices) continue
                val nextLine = lines.getOrNull(nextIndex)?.trim() ?: continue
                if (nextLine.isEmpty() || containsNoiseAmountKeyword(nextLine)) continue
                val cleanedNext = stripNonAmountClauses(nextLine).trim()
                if (cleanedNext.isEmpty() || !isStandaloneAmountLine(cleanedNext) || cleanedNext.contains('-')) continue
                val heightPx = normalizedLines.getOrNull(nextIndex)?.heightPx ?: 0
                result += extractCandidatesFromText(
                    text = cleanedNext,
                    role = AmountRole.TRANSACTION_AMOUNT,
                    lineIndex = nextIndex,
                    allowBareDecimal = true,
                    allowBareInteger = false,
                    heightPx = heightPx
                )
            }
        }
        return result
    }

    private fun hasTaobaoTakeoutCue(lines: List<String>): Boolean = lines.any { line ->
        val lower = line.lowercase(Locale.ROOT)
        TAOBAO_TAKEOUT_CUES.any { lower.contains(it) } ||
            ETA_DELIVERY_ANCHOR_REGEX.containsMatchIn(line)
    }

    private fun extractStandalonePaymentMethodLine(lines: List<String>): String {
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.length !in 2..24) continue
            if (STANDALONE_PAYMENT_METHOD_LINE_REGEX.matches(trimmed)) {
                return trimmed
            }
        }
        return ""
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
        val directionalSign: String?,
        val hasCurrencyHint: Boolean
    )

    private fun resolveAmounts(
        lines: List<String>,
        normalizedLines: List<NormalizedLine>,
        fields: List<ExtractedField>,
        ignoredLineIndices: Set<Int>,
        screenHeightPx: Int,
        pageKind: ScreenPageKind = ScreenPageKind.UNKNOWN
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

        // 已支付电商/外卖订单页无显式实付字段时，安全兜底识别「小计 / 合计」金额，优先于单品标价与商品总价
        if (pageKind == ScreenPageKind.ORDER_DETAIL && candidates.none { it.role == AmountRole.ACTUAL_PAID }) {
            candidates += extractSubtotalOrTotalCandidates(
                lines = lines,
                normalizedLines = normalizedLines,
                ignoredLineIndices = ignoredLineIndices
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

        val firstStatusLineIndex = lines.take(12).withIndex()
            .firstOrNull { (idx, rawLine) ->
                val trimmed = rawLine.trim()
                val lower = trimmed.lowercase(Locale.ROOT)
                fields.any { it.key == FieldKey.STATUS && it.labelLineIndex == idx } ||
                    (trimmed.length <= 16 &&
                        STATUS_ANCHOR_PHRASES.any { phrase ->
                            lower == phrase ||
                                lower.startsWith("$phrase ") ||
                                lower.startsWith("$phrase，")
                        })
            }?.index ?: -1

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
                val directionalSign = extractDirectionalPrefixSign(trimmedClean)
                val hasExplicitSign = trimmedClean.startsWith("-") ||
                    trimmedClean.startsWith("+") ||
                    directionalSign != null
                standaloneLines[index] = StandaloneAmountLineInfo(
                    index = index,
                    cleanedLine = trimmedClean,
                    topPx = node?.topPx ?: -1,
                    heightPx = node?.heightPx ?: 0,
                    hasExplicitSign = hasExplicitSign,
                    directionalSign = directionalSign,
                    hasCurrencyHint = hasExplicitCurrencyHint(trimmedClean)
                )
            } else {
                nonStandaloneCandidates += index to cleanedLine
            }
        }

        val hasStandaloneAmountBeforeStatus = firstStatusLineIndex > 0 &&
            standaloneLines.values.any { it.index in 0 until firstStatusLineIndex }

        val firstSignedOrCurrencyStandaloneIndex = standaloneLines.values
            .asSequence()
            .filter {
                val allowedByStatus = !hasStandaloneAmountBeforeStatus || it.index < firstStatusLineIndex
                allowedByStatus && it.index < 12 && (it.hasExplicitSign || it.hasCurrencyHint)
            }
            .minOfOrNull { it.index }
            ?: -1

        val upperHalfHeights = if (hasValidGeometry) {
            standaloneLines.values
                .filter {
                    (!hasStandaloneAmountBeforeStatus || it.index < firstStatusLineIndex) &&
                        it.topPx in 0..upperHalfLimitPx &&
                        it.heightPx > 0
                }
                .map { it.heightPx }
        } else {
            emptyList()
        }
        val maxTopAmountHeight = upperHalfHeights.maxOrNull() ?: 0
        val secondTopAmountHeight = upperHalfHeights.filter { it < maxTopAmountHeight }.maxOrNull() ?: 0

        val allowHeroPromotion = pageKind != ScreenPageKind.ORDER_DETAIL
        for (info in standaloneLines.values) {
            val blockedByStatusBoundary = hasStandaloneAmountBeforeStatus && info.index > firstStatusLineIndex
            val isHeroVisual = allowHeroPromotion &&
                !blockedByStatusBoundary &&
                hasValidGeometry &&
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

            val isTextHeroZone = allowHeroPromotion &&
                !blockedByStatusBoundary && (
                (info.index < 18 && info.index < firstAnchorFieldLineIndex) ||
                    (info.index < 12 && info.index == firstSignedOrCurrencyStandaloneIndex)
                )

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
                allowBareInteger = isHero && (info.hasExplicitSign || info.directionalSign != null),
                heightPx = info.heightPx,
                defaultSignHint = info.directionalSign
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
        heightPx: Int = 0,
        defaultSignHint: String? = null
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
            val sign = sign1.ifBlank { sign2 }.ifBlank { defaultSignHint ?: "" }.ifBlank { null }

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

    private fun extractDirectionalPrefixSign(text: String): String? {
        val trimmed = text.trim()
        if (!isStandaloneAmountLine(trimmed)) return null
        return when {
            DIRECTIONAL_PREFIX_EXPENSE_REGEX.containsMatchIn(trimmed) -> "-"
            DIRECTIONAL_PREFIX_INCOME_REGEX.containsMatchIn(trimmed) -> "+"
            else -> null
        }
    }

    private fun isDirectionalHeroAmountLine(line: String): Boolean {
        val trimmed = line.trim()
        return DIRECTIONAL_HERO_PROTECT_REGEX.containsMatchIn(trimmed) && isStandaloneAmountLine(trimmed)
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
        dominantSign: String?,
        pageKind: ScreenPageKind = ScreenPageKind.UNKNOWN
    ): String {
        if (pageKind == ScreenPageKind.ORDER_DETAIL && dominantSign != "+") {
            return TransactionType.EXPENSE
        }

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
            lower.contains("支付时间") || lower.contains("付款时间") || lower.contains("成交时间") ||
                lower.contains("交易时间") || lower.contains("收款时间") || lower.contains("转账时间") ||
                lower.contains("退款时间") -> 0
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

            val instrumentCore = paymentMethodText
                .replace(PAYMENT_INSTRUMENT_SUFFIX_STRIP_REGEX, "")
                .trim()
            if (instrumentCore.length >= 3 &&
                instrumentCore.lowercase(Locale.ROOT) !in GENERIC_PAYMENT_ACCOUNT_MATCH_EXCLUSIONS
            ) {
                accounts.firstOrNull { acc ->
                    acc.name.contains(instrumentCore, ignoreCase = true)
                }?.let { return it.id }
            }
        }

        // 3. 手机钱包交通卡刷卡页面：若未命中显式别名，优先路由至公交卡/交通卡账户
        val isPhoneWallet = packageName.lowercase(Locale.ROOT) in PHONE_WALLET_PACKAGES
        if (isPhoneWallet) {
            val matchedByAlias = enabledAliases.any { alias ->
                when (alias.matchType.uppercase(Locale.ROOT)) {
                    AliasMatchType.REGEX -> runCatching {
                        Regex(alias.pattern, RegexOption.IGNORE_CASE).containsMatchIn(cardFullText)
                    }.getOrDefault(false)
                    AliasMatchType.KEYWORD -> alias.pattern.isNotBlank() &&
                        cardFullText.contains(alias.pattern.trim(), ignoreCase = true)
                    AliasMatchType.PACKAGE -> alias.pattern.trim().equals(packageName.trim(), ignoreCase = true)
                    else -> false
                }
            }
            if (!matchedByAlias) {
                accounts.firstOrNull { acc ->
                    TRANSIT_ACCOUNT_NAME_KEYWORDS.any { kw -> acc.name.contains(kw, ignoreCase = true) }
                }?.let { return it.id }
            }
        }

        // 4. 复用 LocalRuleParser 标准路由（REGEX -> KEYWORD -> PACKAGE -> 币种匹配 -> 默认账户）
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
        txType: String,
        packageName: String = ""
    ): String {
        val heroMerchant = extractHeroMerchant(lines, packageName)
            ?.takeIf { !isGenericChannelMerchant(it) }
        val explicitShort = fields
            .filter { it.key == FieldKey.MERCHANT }
            .mapNotNull { cleanMerchantCandidate(it.value) }
            .firstOrNull { !isGenericChannelMerchant(it) }
        val qrPayee = extractQrPayeeMerchant(lines, txType)
            ?.takeIf { !isGenericChannelMerchant(it) }

        if (!explicitShort.isNullOrBlank()) {
            // 若商户全称是结算主体（如“商户_王禹睿”、“个体户xxx”、“xx有限公司”），而顶部有更直观的店名（如“哆哆酸辣粉”），优先采用顶部直观店名
            if (!heroMerchant.isNullOrBlank() &&
                isSettlementEntityName(explicitShort) &&
                !isSettlementEntityName(heroMerchant)
            ) {
                return heroMerchant
            }
            if (!qrPayee.isNullOrBlank() &&
                looksLikePersonalNicknameOrPhone(explicitShort) &&
                !looksLikePersonalNicknameOrPhone(qrPayee)
            ) {
                return qrPayee
            }
            return explicitShort
        }

        if (!qrPayee.isNullOrBlank() &&
            (heroMerchant.isNullOrBlank() || looksLikePersonalNicknameOrPhone(heroMerchant))
        ) {
            return qrPayee
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

    private fun extractQrPayeeMerchant(lines: List<String>, txType: String): String? {
        val pattern = if (txType == TransactionType.INCOME) {
            QR_INCOME_PAYER_REGEX
        } else {
            QR_EXPENSE_PAYEE_REGEX
        }
        for (line in lines) {
            val match = pattern.find(line) ?: continue
            val rawCandidate = match.groupValues.getOrNull(1)?.trim().orEmpty()
            val cleaned = cleanMerchantCandidate(rawCandidate) ?: continue
            if (cleaned.isNotBlank() && !isGenericChannelMerchant(cleaned)) {
                return cleaned
            }
        }
        return null
    }

    private fun looksLikePersonalNicknameOrPhone(name: String): Boolean {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return false
        if (LONG_DIGIT_RUN_REGEX.containsMatchIn(trimmed)) return true
        if (MASKED_PHONE_REGEX.containsMatchIn(trimmed)) return true
        val lower = trimmed.lowercase(Locale.ROOT)
        if (PERSONAL_NICKNAME_PREFIXES.any { lower.startsWith(it) }) return true
        return MASKED_PERSONAL_NAME_REGEX.matches(trimmed)
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

    private fun extractHeroMerchant(lines: List<String>, packageName: String = ""): String? {
        val topSlice = lines.take(20)
        val isTaobao = packageName.equals(PKG_TAOBAO, ignoreCase = true)
        var deferredShellMerchant: String? = null
        for ((index, line) in topSlice.withIndex()) {
            val lineKey = matchFieldKey(line)
            if (lineKey == FieldKey.IGNORED_AMOUNT) {
                val nextLine = topSlice.getOrNull(index + 1)
                val nextIsHeroAmount = index <= 2 &&
                    nextLine != null &&
                    isStandaloneAmountLine(nextLine) &&
                    !containsNoiseAmountKeyword(nextLine) &&
                    !STRICT_DISCOUNT_TITLE_REGEX.containsMatchIn(line)
                if (!nextIsHeroAmount) continue
            } else if (lineKey == FieldKey.ACTUAL_AMOUNT &&
                (line.trim() == "已支付" || line.trim() == "已付") &&
                !looksLikeAmountLineValue(topSlice.getOrNull(index + 1))
            ) {
                continue
            } else if (lineKey != null) {
                break
            }
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
            if (cleaned.isNotBlank()) {
                if (isTaobao && cleaned.lowercase(Locale.ROOT) in TAOBAO_SHELL_MERCHANTS_FOR_HERO) {
                    if (deferredShellMerchant == null) deferredShellMerchant = cleaned
                    continue
                }
                return cleaned
            }
        }
        return deferredShellMerchant
    }

    private fun isNonMerchantUiText(lower: String): Boolean =
        lower in UI_STOP_WORDS ||
            lower in PLACEHOLDER_FIELD_VALUES ||
            UI_STOP_PREFIXES.any { lower.startsWith(it) } ||
            PAY_SUCCESS_PHRASES.any { lower == it } ||
            IGNORED_STATUS_KEYWORDS.any { lower == it }

    private data class OrderDetailExtractedInfo(
        val merchant: String,
        val goodsSummary: String
    )

    private fun extractOrderDetailMerchantAndGoods(
        lines: List<String>,
        fields: List<ExtractedField>
    ): OrderDetailExtractedInfo {
        var merchant = ""
        var merchantLineIndex = -1

        val explicitMerchant = fields
            .filter { it.key == FieldKey.MERCHANT }
            .mapNotNull { cleanOrderStoreName(it.value) }
            .firstOrNull { !isGenericChannelMerchant(it) }
        if (!explicitMerchant.isNullOrBlank()) {
            merchant = explicitMerchant
            merchantLineIndex = fields.firstOrNull { it.key == FieldKey.MERCHANT }?.lineIndex ?: -1
        }

        if (merchant.isBlank()) {
            for ((index, rawLine) in lines.withIndex()) {
                val trimmed = rawLine.trim()
                if (trimmed.isEmpty()) continue
                if (isOrderMetadataBoundaryLine(trimmed)) break
                if (isIgnoredOrderCandidateLine(trimmed)) continue

                val hasStoreCue = trimmed.endsWith(">") ||
                    trimmed.endsWith("›") ||
                    trimmed.endsWith("＞") ||
                    STORE_NAME_SUFFIX_REGEX.containsMatchIn(trimmed)
                if (hasStoreCue) {
                    val cleaned = cleanOrderStoreName(trimmed)
                    if (!cleaned.isNullOrBlank()) {
                        merchant = cleaned
                        merchantLineIndex = index
                        break
                    }
                }
            }
        }

        if (merchant.isBlank()) {
            for ((index, rawLine) in lines.withIndex()) {
                val trimmed = rawLine.trim()
                if (trimmed.isEmpty()) continue
                if (isOrderMetadataBoundaryLine(trimmed)) break
                if (isIgnoredOrderCandidateLine(trimmed)) continue
                if (ORDER_QTY_ONLY_REGEX.matches(trimmed) || ORDER_SKU_PREFIX_REGEX.containsMatchIn(trimmed)) continue

                val cleaned = cleanOrderStoreName(trimmed)
                if (!cleaned.isNullOrBlank() && cleaned.length <= 28) {
                    merchant = cleaned
                    merchantLineIndex = index
                    break
                }
            }
        }

        val itemEntries = mutableListOf<String>()
        var lastItemHasQtyOrPrice = false
        val startIdx = if (merchantLineIndex >= 0) merchantLineIndex + 1 else 0

        for (index in startIdx until lines.size) {
            val trimmed = lines[index].trim()
            if (trimmed.isEmpty()) continue
            if (isOrderMetadataBoundaryLine(trimmed)) break
            if (isStandaloneAmountLine(trimmed) || AMOUNT_ONLY_LINE_REGEX.matches(trimmed.replace(" ", ""))) {
                if (itemEntries.isNotEmpty()) lastItemHasQtyOrPrice = true
                continue
            }
            if (isIgnoredOrderCandidateLine(trimmed)) continue

            val cleanedItem = trimmed
                .replace(TRAILING_ITEM_PRICE_REGEX, "")
                .replace(WHITESPACE_RUN_REGEX, " ")
                .trim()
            if (cleanedItem.isEmpty()) continue
            if (merchant.isNotBlank() && cleanedItem.equals(merchant, ignoreCase = true)) continue

            if (ORDER_QTY_ONLY_REGEX.matches(cleanedItem)) {
                if (itemEntries.isNotEmpty()) {
                    itemEntries[itemEntries.lastIndex] = "${itemEntries.last()} $cleanedItem"
                    lastItemHasQtyOrPrice = true
                }
                continue
            }

            val looksLikeSku = !lastItemHasQtyOrPrice &&
                itemEntries.isNotEmpty() &&
                (ORDER_SKU_PREFIX_REGEX.containsMatchIn(cleanedItem) ||
                    (cleanedItem.length <= 28 && !ITEM_TITLE_WITH_QTY_REGEX.containsMatchIn(cleanedItem) &&
                        (cleanedItem.contains('/') || cleanedItem.contains(';') || cleanedItem.contains('；') ||
                            cleanedItem.contains('-') || ITEM_QTY_SUFFIX_REGEX.containsMatchIn(cleanedItem))))

            if (looksLikeSku) {
                val qtyMatch = ITEM_QTY_SUFFIX_REGEX.find(cleanedItem)
                if (qtyMatch != null) {
                    val specOnly = cleanedItem.substring(0, qtyMatch.range.first).trim()
                    val qtyPart = qtyMatch.groupValues[1].trim()
                    val updated = if (specOnly.isNotEmpty()) {
                        "${itemEntries.last()} ($specOnly) $qtyPart"
                    } else {
                        "${itemEntries.last()} $qtyPart"
                    }
                    itemEntries[itemEntries.lastIndex] = updated
                    lastItemHasQtyOrPrice = true
                } else {
                    itemEntries[itemEntries.lastIndex] = "${itemEntries.last()} ($cleanedItem)"
                }
                continue
            }

            if (itemEntries.size < 4) {
                itemEntries.add(cleanedItem)
                lastItemHasQtyOrPrice = ITEM_QTY_SUFFIX_REGEX.containsMatchIn(cleanedItem)
            }
        }

        val fallbackGoods = extractGoods(fields)
        val goodsSummary = when {
            itemEntries.isNotEmpty() -> itemEntries.joinToString("、").take(110)
            else -> fallbackGoods
        }
        return OrderDetailExtractedInfo(merchant = merchant, goodsSummary = goodsSummary)
    }

    private fun isOrderMetadataBoundaryLine(line: String): Boolean {
        val trimmed = line.trim()
        if (trimmed.isNotEmpty() &&
            !containsNoiseAmountKeyword(trimmed) &&
            (SUBTOTAL_OR_TOTAL_LABEL_REGEX.matches(trimmed) || SUBTOTAL_OR_TOTAL_AMOUNT_REGEX.containsMatchIn(trimmed))
        ) {
            return true
        }
        val colonIdx = findKeySeparatorIndex(line)
        if (colonIdx <= 0 &&
            (trimmed == "已支付" || trimmed == "已付") &&
            matchSpaceSeparatedField(trimmed) == null
        ) {
            return false
        }
        val key = if (colonIdx > 0) {
            matchFieldKey(line.substring(0, colonIdx))
        } else {
            matchStrictFieldKeyInLine(line)
        }
        return key == FieldKey.ACTUAL_AMOUNT ||
            key == FieldKey.ORDER_AMOUNT ||
            key == FieldKey.ORDER_NO ||
            key == FieldKey.TIME ||
            key == FieldKey.PAYMENT_METHOD
    }

    private fun isIgnoredOrderCandidateLine(line: String): Boolean {
        if (isStrictFieldLabelLine(line) && !line.startsWith("商品") && !line.startsWith("店铺") && !line.startsWith("商家")) {
            return true
        }
        if (matchFieldKey(line) == FieldKey.IGNORED_AMOUNT) return true
        if (containsNoiseAmountKeyword(line) || isMixedPaymentBreakdownLine(line)) return true
        if (isStandaloneAmountLine(line) || AMOUNT_ONLY_LINE_REGEX.matches(line.replace(" ", ""))) return true
        if (FULL_DATETIME_REGEX.containsMatchIn(line) || MONTH_DAY_TIME_REGEX.containsMatchIn(line) ||
            TIME_ONLY_STRIP_REGEX.matches(line.trim())
        ) {
            return true
        }
        val lower = line.trim().lowercase(Locale.ROOT)
        if (isNonMerchantUiText(lower) || isActionButtonOnly(line) || isPlaceholderFieldValue(line)) return true
        if (ORDER_NOISE_UI_WORDS.any { lower == it || lower.startsWith("$it ") || lower.endsWith(it) }) return true
        val lowerNoChevron = lower.trimEnd('>', '›', '＞', '»', ' ')
        if (ORDER_PAID_STATUS_KEYWORDS.any {
                lowerNoChevron == it ||
                    lowerNoChevron.startsWith("$it ") ||
                    lowerNoChevron.startsWith("$it，") ||
                    lowerNoChevron.startsWith("$it,")
            }
        ) {
            return true
        }
        if (ORDER_ADDRESS_OR_LOGISTICS_REGEX.containsMatchIn(line) && !STORE_NAME_SUFFIX_REGEX.containsMatchIn(line)) return true
        if (MASKED_PHONE_REGEX.containsMatchIn(line)) return true
        return false
    }

    private fun cleanOrderStoreName(raw: String): String? {
        val cleanedChevrons = raw
            .replace(Regex("[>›＞»]+\\s*$"), "")
            .replace(Regex("\\s*(?:进店逛逛|进入店铺|进店|官方认证)$"), "")
            .trim()
        return cleanMerchantCandidate(cleanedChevrons)
    }

    private fun isPlatformShellMerchant(merchant: String): Boolean {
        val lower = merchant.trim().lowercase(Locale.ROOT)
        if (lower.isEmpty()) return false
        if (lower in PLATFORM_SHELL_MERCHANTS) return true
        return PLATFORM_SHELL_SUBSTRINGS.any { lower.contains(it) }
    }

    private fun isGenericWalletBrand(merchant: String): Boolean {
        val lower = merchant.trim().lowercase(Locale.ROOT)
        return lower in GENERIC_WALLET_BRANDS
    }

    private fun unpackPlatformShellGoods(shellMerchant: String, rawGoods: String): Pair<String, String> {
        var cleaned = rawGoods.trim()
        repeat(2) {
            cleaned = cleaned.replace(PLATFORM_GOODS_PREFIX_REGEX, "").trim()
            cleaned = cleaned.replace(PLATFORM_GOODS_ORDER_ID_PREFIX_REGEX, "").trim()
        }
        cleaned = cleaned.replace(PLATFORM_GOODS_EMBEDDED_ORDER_ID_REGEX, " ").trim()
        cleaned = cleaned.replace(Regex("^[\\-—:：/·|\\s]+|[\\-—:：/·|\\s]+$"), "").trim()

        if (cleaned.isEmpty()) return shellMerchant to rawGoods

        val shopMatch = SHOP_NAME_FROM_GOODS_REGEX.find(cleaned)
        if (shopMatch != null) {
            val extractedShop = cleanOrderStoreName(shopMatch.groupValues[1])
            if (!extractedShop.isNullOrBlank() && !isPlatformShellMerchant(extractedShop)) {
                return extractedShop to cleaned
            }
        }

        val platformShort = resolvePlatformShortName(shellMerchant, rawGoods)
        val conciseGoods = cleaned.take(24).trim()
        val displayMerchant = if (platformShort.isNotBlank() && !conciseGoods.startsWith(platformShort)) {
            "$platformShort·$conciseGoods"
        } else {
            conciseGoods
        }
        return displayMerchant to cleaned
    }

    private fun resolvePlatformShortName(shellMerchant: String, rawGoods: String): String {
        val combined = "$shellMerchant $rawGoods".lowercase(Locale.ROOT)
        return when {
            combined.contains("拼多多") || combined.contains("寻梦") -> "拼多多"
            combined.contains("天猫") -> "天猫"
            combined.contains("淘宝") -> "淘宝"
            combined.contains("京东") -> "京东"
            combined.contains("美团") || combined.contains("三快在线") -> "美团"
            combined.contains("饿了么") || combined.contains("拉扎斯") -> "饿了么"
            combined.contains("抖音") || combined.contains("合众易宝") -> "抖音"
            else -> ""
        }
    }

    private fun cleanMerchantCandidate(raw: String): String? {
        var s = raw.trim()
        if (s.isEmpty()) return null
        if (isStrictFieldLabel(s) || isPlaceholderFieldValue(s)) return null

        // 在剥离“向/给/在/付款/转账”等前后缀之前先检查原始文本，避免“在浏览器打开”等系统 UI 文案绕过停用词表
        if (isNonMerchantUiText(s.lowercase(Locale.ROOT))) return null

        // 解包“待XXX确认/收款”及“来自XXX的转账/红包”包裹文案
        MERCHANT_PENDING_WRAPPER_REGEX.matchEntire(s)?.let { m ->
            val inner = m.groupValues[1].trim()
            if (inner.isNotEmpty()) s = inner
        }
        MERCHANT_FROM_WRAPPER_REGEX.matchEntire(s)?.let { m ->
            val inner = m.groupValues[1].trim()
            if (inner.isNotEmpty()) s = inner
        }

        s = s.replace(MERCHANT_ACTION_PREFIX_REGEX, "")
        repeat(2) {
            s = s.replace(MERCHANT_ACTION_SUFFIX_REGEX, "")
        }
        s = s.replace(Regex("^[：:，,。\\-—>›＞\\s]+|[：:，,。.;；>›＞\\s]+$"), "")
            .trim()

        val withoutMaskedRealName = s.replace(TRAILING_MASKED_REALNAME_PAREN_REGEX, "").trim()
        if (withoutMaskedRealName.isNotEmpty()) {
            s = withoutMaskedRealName
        }

        if (s.isEmpty() || s.length > 40) return null
        if (isStrictFieldLabel(s) || isPlaceholderFieldValue(s)) return null

        val lower = s.lowercase(Locale.ROOT)
        if (isNonMerchantUiText(lower)) return null
        if (PURE_NUMBER_OR_CARD_REGEX.matches(s)) return null
        if (AMOUNT_ONLY_LINE_REGEX.matches(s.replace(" ", ""))) return null
        if (FULL_DATETIME_REGEX.containsMatchIn(s) || MONTH_DAY_TIME_REGEX.containsMatchIn(s)) return null
        return s
    }

    private fun isP2pPersonalCounterpartyToClear(
        merchant: String,
        lines: List<String>,
        ignoredLineIndices: Set<Int>
    ): Boolean {
        val trimmed = merchant.trim()
        if (trimmed.isEmpty()) return false
        if (trimmed.lowercase(Locale.ROOT) in P2P_PRESERVED_LABEL_MERCHANTS) return false
        if (hasCommercialEntityCue(trimmed)) return false
        if (!isP2pTransferOrRedPacketContext(lines, ignoredLineIndices)) return false
        return looksLikePersonalNicknameOrPhone(trimmed) ||
            trimmed.startsWith("wxid_", ignoreCase = true) ||
            trimmed.length <= 24
    }

    private fun isP2pTransferOrRedPacketContext(
        lines: List<String>,
        ignoredLineIndices: Set<Int>
    ): Boolean {
        val lowerNonNoiseLines = lines.withIndex()
            .filter { (_, line) -> line.trim().lowercase(Locale.ROOT) !in CHROME_NOISE_LINES }
        if (lowerNonNoiseLines.any { (_, line) ->
                val lower = line.lowercase(Locale.ROOT)
                P2P_COMMERCIAL_EXCLUSION_MARKERS.any { lower.contains(it) }
            }
        ) {
            return false
        }

        return lowerNonNoiseLines.any { (idx, line) ->
            if (idx in ignoredLineIndices) return@any false
            if (containsNoiseAmountKeyword(line) &&
                (AMOUNT_TOKEN_REGEX.containsMatchIn(line) || isStandaloneAmountLine(line))
            ) {
                return@any false
            }
            val lower = line.lowercase(Locale.ROOT)
            P2P_CONTEXT_MARKERS.any { lower.contains(it) } ||
                P2P_TRANSFER_LINE_REGEX.containsMatchIn(line)
        }
    }

    private fun hasCommercialEntityCue(name: String): Boolean {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return false
        val lower = trimmed.lowercase(Locale.ROOT)
        if (COMMERCIAL_ENTITY_KEYWORDS.any { lower.contains(it) }) return true
        return COMMERCIAL_STORE_SUFFIX_REGEX.containsMatchIn(trimmed)
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

    private fun buildCleanClassificationSummary(
        lines: List<String>,
        pageKind: ScreenPageKind,
        packageName: String = ""
    ): String {
        val promoNoiseRegex = if (packageName.equals(PKG_TAOBAO, ignoreCase = true)) {
            TAOBAO_OWN_PAY_SUCCESS_PROMO_NOISE_REGEX
        } else {
            PAY_SUCCESS_PROMO_NOISE_REGEX
        }
        val filtered = lines.filterNot { line ->
            val trimmed = line.trim()
            val lower = trimmed.lowercase(Locale.ROOT)
            lower in CHROME_NOISE_LINES ||
                DISCOUNT_BREAKDOWN_CLASSIFY_NOISE_REGEX.containsMatchIn(trimmed) ||
                (pageKind == ScreenPageKind.PAY_SUCCESS && promoNoiseRegex.containsMatchIn(trimmed))
        }
        val joined = filtered.joinToString("\n")
        return if (joined.length <= 1200) joined else joined.take(1197) + "..."
    }

    private fun buildCompactNote(
        goodsDesc: String,
        merchant: String,
        paymentMethodText: String,
        orderNo: String,
        isOrderDetail: Boolean = false
    ): String {
        val maxGoodsLen = if (isOrderDetail) 96 else 48
        val maxTotalLen = if (isOrderDetail) 140 else 96
        val parts = buildList {
            if (goodsDesc.isNotBlank() && !goodsDesc.equals(merchant, ignoreCase = true)) {
                add(goodsDesc.take(maxGoodsLen))
            }
            if (paymentMethodText.isNotBlank()) {
                add(paymentMethodText.take(28))
            }
            if (orderNo.isNotBlank()) {
                add("单号:$orderNo")
            }
        }
        val joined = parts.joinToString(" · ")
        return if (joined.length <= maxTotalLen) joined else joined.take(maxTotalLen - 3) + "..."
    }

    companion object {
        private const val PKG_WECHAT = "com.tencent.mm"
        private const val PKG_ALIPAY = "com.eg.android.AlipayGphone"
        private const val PKG_TAOBAO = "com.taobao.taobao"

        private val WATCHED_EXACT_PACKAGES: Set<String> = hashSetOf(
            PKG_WECHAT,
            PKG_ALIPAY,
            "com.unionpay",
            "com.jd.jrapp",
            "com.sankuai.meituan",
            // 电商与外卖平台
            "com.xunmeng.pinduoduo",
            "com.taobao.taobao",
            "com.tmall.wireless",
            "com.jingdong.app.mall",
            "com.sankuai.meituan.takeoutnew",
            "me.ele",
            "com.ss.android.ugc.aweme",
            // 手机钱包与 NFC 交通卡服务
            "com.miui.tsmclient",
            "com.mipay.wallet",
            "com.xiaomi.pass",
            "com.huawei.wallet",
            "com.hihonor.wallet",
            "com.finshell.wallet",
            "com.coloros.wallet",
            "com.vivo.wallet",
            "com.samsung.android.spay",
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
            "walletorderinfo",
            "walletpaidsuccessui",
            "mallorderdetailinfoui",
            "mallorder",
            "uipagefragmentactivity",
            "remittancedetailui",
            "remittanceresui",
            "walletrefundsuccessui",
            "wxliteapp",
            "liteapp",
            "appbrand",
            "flutter",
            "kinda",
            "tenpay",
            "wallet",
            "luckymoney",
            "collect"
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
            "成交时间" to FieldKey.TIME,
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
            "订单号码" to FieldKey.ORDER_NO,
            "商户单号" to FieldKey.ORDER_NO,
            "商户订单号" to FieldKey.ORDER_NO,
            "商家订单号" to FieldKey.ORDER_NO,
            "transaction id" to FieldKey.ORDER_NO,
            "transaction no" to FieldKey.ORDER_NO,
            "order id" to FieldKey.ORDER_NO,
            "order no" to FieldKey.ORDER_NO,
            "merchant order no" to FieldKey.ORDER_NO,

            "商户" to FieldKey.MERCHANT,
            "商户名" to FieldKey.MERCHANT,
            "商户名称" to FieldKey.MERCHANT,
            "商户简称" to FieldKey.MERCHANT,
            "商户全称" to FieldKey.MERCHANT,
            "收款商户全称" to FieldKey.MERCHANT,
            "商家" to FieldKey.MERCHANT,
            "商家名称" to FieldKey.MERCHANT,
            "店铺" to FieldKey.MERCHANT,
            "店铺名称" to FieldKey.MERCHANT,
            "门店" to FieldKey.MERCHANT,
            "门店名称" to FieldKey.MERCHANT,
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
            "合计实付" to FieldKey.ACTUAL_AMOUNT,
            "商品实付" to FieldKey.ACTUAL_AMOUNT,
            "实付总额" to FieldKey.ACTUAL_AMOUNT,
            "实际付款" to FieldKey.ACTUAL_AMOUNT,
            "实付" to FieldKey.ACTUAL_AMOUNT,
            "共支付" to FieldKey.ACTUAL_AMOUNT,
            "已支付" to FieldKey.ACTUAL_AMOUNT,
            "已付" to FieldKey.ACTUAL_AMOUNT,
            "付款金额" to FieldKey.ACTUAL_AMOUNT,
            "支付金额" to FieldKey.ACTUAL_AMOUNT,
            "扣款金额" to FieldKey.ACTUAL_AMOUNT,
            "本次扣费" to FieldKey.ACTUAL_AMOUNT,
            "出站扣费" to FieldKey.ACTUAL_AMOUNT,
            "刷卡扣费" to FieldKey.ACTUAL_AMOUNT,
            "乘车扣费" to FieldKey.ACTUAL_AMOUNT,
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
            "商品总价" to FieldKey.ORDER_AMOUNT,
            "商品金额" to FieldKey.ORDER_AMOUNT,
            "订单总额" to FieldKey.ORDER_AMOUNT,
            "商品总额" to FieldKey.ORDER_AMOUNT,
            "原价" to FieldKey.ORDER_AMOUNT,

            "当前余额" to FieldKey.IGNORED_AMOUNT,
            "账户余额" to FieldKey.IGNORED_AMOUNT,
            "可用余额" to FieldKey.IGNORED_AMOUNT,
            "卡内余额" to FieldKey.IGNORED_AMOUNT,
            "卡余额" to FieldKey.IGNORED_AMOUNT,
            "卡上余额" to FieldKey.IGNORED_AMOUNT,
            "剩余金额" to FieldKey.IGNORED_AMOUNT,
            "剩余余额" to FieldKey.IGNORED_AMOUNT,
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
            "店铺优惠" to FieldKey.IGNORED_AMOUNT,
            "店铺立减" to FieldKey.IGNORED_AMOUNT,
            "跨店满减" to FieldKey.IGNORED_AMOUNT,
            "百亿补贴" to FieldKey.IGNORED_AMOUNT,
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
            "淘金币抵扣" to FieldKey.IGNORED_AMOUNT,
            "京豆" to FieldKey.IGNORED_AMOUNT,
            "京豆抵扣" to FieldKey.IGNORED_AMOUNT,
            "美团红包" to FieldKey.IGNORED_AMOUNT,
            "美团津贴" to FieldKey.IGNORED_AMOUNT,
            "美团神券" to FieldKey.IGNORED_AMOUNT,
            "下单返现" to FieldKey.IGNORED_AMOUNT,
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
            "减减卡" to FieldKey.IGNORED_AMOUNT,
            "支付宝减减卡" to FieldKey.IGNORED_AMOUNT,
            "省钱卡" to FieldKey.IGNORED_AMOUNT,
            "福利金" to FieldKey.IGNORED_AMOUNT,
            "权益金" to FieldKey.IGNORED_AMOUNT,
            "膨胀金" to FieldKey.IGNORED_AMOUNT,
            "神券" to FieldKey.IGNORED_AMOUNT,
            "立减卡" to FieldKey.IGNORED_AMOUNT,
            "抵扣金" to FieldKey.IGNORED_AMOUNT,
            "支付奖励" to FieldKey.IGNORED_AMOUNT,
            "笔笔攒" to FieldKey.IGNORED_AMOUNT,
            "运费" to FieldKey.IGNORED_AMOUNT,
            "配送费" to FieldKey.IGNORED_AMOUNT,
            "打包费" to FieldKey.IGNORED_AMOUNT,
            "餐盒费" to FieldKey.IGNORED_AMOUNT,
            "餐具费" to FieldKey.IGNORED_AMOUNT,
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
            "实付金额", "实付款", "合计实付", "商品实付", "实付总额", "实际付款", "共支付",
            "付款金额", "支付金额", "扣款金额",
            "本次扣费", "出站扣费", "刷卡扣费", "乘车扣费", "折算人民币", "折合人民币", "人民币金额", "入账金额"
        )

        private val PRIMARY_FOREIGN_LABEL_HINTS = listOf(
            "原始金额", "外币金额", "原币金额", "消费外币", "交易币种金额"
        )

        private val PRIMARY_ORDER_LABEL_HINTS = listOf(
            "订单金额", "商品总价", "商品金额", "订单总额", "商品总额", "原价"
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
            "(?:优惠信息|优惠|立减|红包|折扣|抵扣金|抵扣|抵用|满减|代金券|优惠券|消费券|商家券|减减卡|省钱卡|立减卡|福利金|权益金|膨胀金|神券|支付奖励|消费金|花呗金|体验金|鼓励金|淘金币|京豆|百亿补贴|美团津贴|下单返现|配送费|打包费|餐盒费|餐具费|运费|补贴|减免|已减|共减|直降|免单|返现|省钱|共省|已省|立省|省了|节省|碰一下|笔笔攒|运费险|组合支付|积分|集分宝|余额|汇率|手续费|服务费|\\bdiscounts?\\b|\\bcoupons?\\b|\\bvouchers?\\b|\\bcashback\\b|\\bbalances?\\b|\\bfees?\\b)",
            RegexOption.IGNORE_CASE
        )

        private val STRICT_DISCOUNT_TITLE_REGEX = Regex(
            "(?:优惠|抵扣|立减|满减|减减卡|省钱卡|立减卡|福利金|权益金|膨胀金|神券|消费券|代金券|优惠券|商家券|补贴|减免|返现|积分|支付奖励|配送费|打包费|餐盒费|餐具费|运费|京豆)"
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
            "合计实付" to FieldKey.ACTUAL_AMOUNT,
            "商品实付" to FieldKey.ACTUAL_AMOUNT,
            "实付总额" to FieldKey.ACTUAL_AMOUNT,
            "实付金额" to FieldKey.ACTUAL_AMOUNT,
            "实付款" to FieldKey.ACTUAL_AMOUNT,
            "实际付款" to FieldKey.ACTUAL_AMOUNT,
            "付款金额" to FieldKey.ACTUAL_AMOUNT,
            "支付金额" to FieldKey.ACTUAL_AMOUNT,
            "本次扣费" to FieldKey.ACTUAL_AMOUNT,
            "出站扣费" to FieldKey.ACTUAL_AMOUNT,
            "刷卡扣费" to FieldKey.ACTUAL_AMOUNT,
            "乘车扣费" to FieldKey.ACTUAL_AMOUNT,
            "折算人民币" to FieldKey.ACTUAL_AMOUNT,
            "折合人民币" to FieldKey.ACTUAL_AMOUNT,
            "共支付" to FieldKey.ACTUAL_AMOUNT,
            "已支付" to FieldKey.ACTUAL_AMOUNT,
            "已付" to FieldKey.ACTUAL_AMOUNT,
            "实付" to FieldKey.ACTUAL_AMOUNT,
            "商品总价" to FieldKey.ORDER_AMOUNT,
            "商品金额" to FieldKey.ORDER_AMOUNT,
            "订单总额" to FieldKey.ORDER_AMOUNT,
            "商品总额" to FieldKey.ORDER_AMOUNT,
            "订单金额" to FieldKey.ORDER_AMOUNT,
            "卡内余额" to FieldKey.IGNORED_AMOUNT,
            "卡上余额" to FieldKey.IGNORED_AMOUNT,
            "卡余额" to FieldKey.IGNORED_AMOUNT,
            "剩余金额" to FieldKey.IGNORED_AMOUNT,
            "剩余余额" to FieldKey.IGNORED_AMOUNT,
            "当前余额" to FieldKey.IGNORED_AMOUNT,
            "可用余额" to FieldKey.IGNORED_AMOUNT,
            "账户余额" to FieldKey.IGNORED_AMOUNT,
            "支付优惠" to FieldKey.IGNORED_AMOUNT,
            "优惠信息" to FieldKey.IGNORED_AMOUNT,
            "优惠说明" to FieldKey.IGNORED_AMOUNT,
            "优惠明细" to FieldKey.IGNORED_AMOUNT,
            "优惠金额" to FieldKey.IGNORED_AMOUNT,
            "商家优惠" to FieldKey.IGNORED_AMOUNT,
            "平台优惠" to FieldKey.IGNORED_AMOUNT,
            "店铺优惠" to FieldKey.IGNORED_AMOUNT,
            "店铺立减" to FieldKey.IGNORED_AMOUNT,
            "跨店满减" to FieldKey.IGNORED_AMOUNT,
            "百亿补贴" to FieldKey.IGNORED_AMOUNT,
            "碰一下立减" to FieldKey.IGNORED_AMOUNT,
            "官方立减" to FieldKey.IGNORED_AMOUNT,
            "微信立减金" to FieldKey.IGNORED_AMOUNT,
            "立减金" to FieldKey.IGNORED_AMOUNT,
            "支付宝红包" to FieldKey.IGNORED_AMOUNT,
            "美团红包" to FieldKey.IGNORED_AMOUNT,
            "美团津贴" to FieldKey.IGNORED_AMOUNT,
            "美团神券" to FieldKey.IGNORED_AMOUNT,
            "神券" to FieldKey.IGNORED_AMOUNT,
            "红包抵扣" to FieldKey.IGNORED_AMOUNT,
            "消费券" to FieldKey.IGNORED_AMOUNT,
            "代金券" to FieldKey.IGNORED_AMOUNT,
            "优惠券" to FieldKey.IGNORED_AMOUNT,
            "商家券" to FieldKey.IGNORED_AMOUNT,
            "消费金" to FieldKey.IGNORED_AMOUNT,
            "花呗金" to FieldKey.IGNORED_AMOUNT,
            "体验金" to FieldKey.IGNORED_AMOUNT,
            "鼓励金" to FieldKey.IGNORED_AMOUNT,
            "淘金币抵扣" to FieldKey.IGNORED_AMOUNT,
            "淘金币" to FieldKey.IGNORED_AMOUNT,
            "京豆抵扣" to FieldKey.IGNORED_AMOUNT,
            "京豆" to FieldKey.IGNORED_AMOUNT,
            "下单返现" to FieldKey.IGNORED_AMOUNT,
            "笔笔攒" to FieldKey.IGNORED_AMOUNT,
            "运费险" to FieldKey.IGNORED_AMOUNT,
            "配送费" to FieldKey.IGNORED_AMOUNT,
            "打包费" to FieldKey.IGNORED_AMOUNT,
            "餐盒费" to FieldKey.IGNORED_AMOUNT,
            "餐具费" to FieldKey.IGNORED_AMOUNT,
            "运费" to FieldKey.IGNORED_AMOUNT,
            "组合支付" to FieldKey.IGNORED_AMOUNT,
            "支付方式" to FieldKey.PAYMENT_METHOD,
            "付款方式" to FieldKey.PAYMENT_METHOD,
            "收款方式" to FieldKey.PAYMENT_METHOD,
            "扣款方式" to FieldKey.PAYMENT_METHOD,
            "存入账户" to FieldKey.PAYMENT_METHOD,
            "退款去向" to FieldKey.PAYMENT_METHOD,
            "退回账户" to FieldKey.PAYMENT_METHOD,
            "提现银行" to FieldKey.PAYMENT_METHOD,
            "当前状态" to FieldKey.STATUS,
            "交易状态" to FieldKey.STATUS,
            "收款方全称" to FieldKey.PAYEE,
            "收款方" to FieldKey.PAYEE,
            "付款方" to FieldKey.PAYER,
            "商户全称" to FieldKey.MERCHANT,
            "商户名称" to FieldKey.MERCHANT,
            "店铺名称" to FieldKey.MERCHANT,
            "商家名称" to FieldKey.MERCHANT,
            "门店名称" to FieldKey.MERCHANT,
            "交易单号" to FieldKey.ORDER_NO,
            "转账单号" to FieldKey.ORDER_NO,
            "商家订单号" to FieldKey.ORDER_NO,
            "商户订单号" to FieldKey.ORDER_NO,
            "订单编号" to FieldKey.ORDER_NO,
            "订单号码" to FieldKey.ORDER_NO,
            "订单号" to FieldKey.ORDER_NO,
            "商户单号" to FieldKey.ORDER_NO,
            "支付时间" to FieldKey.TIME,
            "付款时间" to FieldKey.TIME,
            "成交时间" to FieldKey.TIME,
            "下单时间" to FieldKey.TIME,
            "交易时间" to FieldKey.TIME,
            "创建时间" to FieldKey.TIME,
            "收钱时间" to FieldKey.TIME,
            "转账时间" to FieldKey.TIME,
            "提现时间" to FieldKey.TIME,
            "红包说明" to FieldKey.GOODS,
            "商品说明" to FieldKey.GOODS,
            "商品名称" to FieldKey.GOODS,
            "转账说明" to FieldKey.GOODS,
            "商品" to FieldKey.GOODS,
            "账单分类" to FieldKey.META,
            "收单机构" to FieldKey.META,
            "清算机构" to FieldKey.META
        )

        private val COMPACT_PREFIX_KEYS = setOf(
            FieldKey.ACTUAL_AMOUNT,
            FieldKey.AMOUNT,
            FieldKey.ORDER_AMOUNT,
            FieldKey.FOREIGN_AMOUNT,
            FieldKey.IGNORED_AMOUNT,
            FieldKey.ORDER_NO
        )

        private val COMPACT_AMOUNT_LEAD_CHARS = setOf('¥', '￥', '$', '€', '£', '₩', '+', '-')

        private val INLINE_ACTUAL_PAID_REGEX = Regex(
            "(?:合计实付|商品实付|实付总额|实付金额|实际付款|实付款|实付|共支付|已支付|已付)(?:金额)?(?:为|：|:|\\s)*([+-]?\\s*(?:US\\$|HK\\$|NT\\$|S\\$|A\\$|C\\$|JP¥|USD|EUR|GBP|HKD|JPY|SGD|CNY|RMB|[¥￥$€£₩])?\\s*[0-9]+(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?(?!\\s*(?:件|笔|单|次|个|份|盒|杯|瓶|袋|包|人|天|小时|分钟|秒|年|月|日|号))\\s*(?:元|美元|USD|EUR|GBP|CNY|RMB)?)",
            RegexOption.IGNORE_CASE
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
            "复制", "copy", "查看", "更多", "完成", "done", "返回", "关闭",
            "联系商家", "联系商户", "aa收款", "查看往来记录", "查看往来转账", "往来流水证明",
            "申请电子回单", "申请转账电子凭证", "对订单有疑问",
            "账单管理", "我的消费图鉴", "为您推荐", "服务详情", "进入小程序", "转账红包",
            "回首页", "返回首页", "回到首页", "去首页", "首页", "返回上一页", "返回应用", "返回app",
            "返回微信", "返回支付宝", "返回淘宝", "回到商家", "继续逛逛", "我知道了", "知道了", "好的", "跳过",
            "下一步", "立即体验", "去看看", "查看订单", "查看进度", "查看账单详情", "支付完成", "付款完成",
            "已完成", "关闭页面", "收起", "展开", "分享给好友", "保存图片", "再来一单",
            "去评价", "立即评价", "开发中", "加载中", "加载中..."
        )

        private val STATUS_ANCHOR_PHRASES = listOf(
            "交易成功", "支付成功", "付款成功", "消费成功", "转账成功",
            "已收款", "收款成功", "对方已收钱", "对方已收款",
            "退款成功", "已全额退款", "已退款", "退款到账", "已退回",
            "已完成", "已支付", "已存入零钱"
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
            "账单服务", "发起群收款", "申请电子凭证", "申请转账电子凭证", "本服务由财付通提供",
            "收单机构", "清算机构", "在此商户的交易", "在此商户的交易记录",
            "定位到聊天位置", "查看往来记录", "查看往来转账", "联系收款方", "联系商户",
            "对订单有疑惑", "对此订单有疑问", "对订单有疑问",
            "查看红包详情", "标签和备注", "申请开票", "电子凭证", "收款方备注"
        )

        private val STRONG_BILL_ANCHOR_KEYWORDS = listOf(
            "交易单号", "转账单号", "商户单号", "商户订单号", "订单号", "订单编号",
            "本服务由财付通提供", "账单服务", "发起群收款", "申请电子凭证", "申请转账电子凭证",
            "在此商户的交易", "查看往来记录", "查看往来转账", "对订单有疑惑", "对此订单有疑问", "对订单有疑问"
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
            "回首页", "返回首页", "回到首页",
            "done", "ok", "close", "返回", "电子回单"
        )

        private val WALLET_STRICT_DONE_ANCHOR_KEYWORDS = listOf(
            "完成", "返回商家", "查看账单", "查看零钱", "账单详情",
            "回首页", "返回首页", "回到首页",
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

        private val REFUND_BUTTON_EXCLUSION_REGEX = Regex("(?:申请退款|我要退款|申请售后|退款记录|退款详情|部分已退|极速退款|7天无理由|退换无忧|退换货|坏果包赔)")

        private val COUNTERPARTY_RECEIVED_KEYWORDS = listOf(
            "对方已收款", "对方已收钱", "对方已领取", "等待对方收款", "等待对方收钱", "待对方收款", "对方已领"
        )

        private val INCOME_CONFIRM_REGEX = Regex(
            "(?:收到.{0,8}(?:红包|转账|款项|付款)|领到.{0,6}红包|你已收款|已收款|收款成功|已存入零钱通?|退款成功|已全额退款|已退款|退款到账|原路退回|已退回|工资|分红|you\\s+received|money\\s+received|refund\\s+successful)",
            RegexOption.IGNORE_CASE
        )

        private val NOISE_AMOUNT_LINE_KEYWORDS = listOf(
            "支付优惠", "优惠信息", "优惠说明", "优惠明细", "商家优惠", "平台优惠", "店铺优惠", "店铺立减",
            "跨店满减", "百亿补贴", "官方立减", "立减金",
            "减减卡", "省钱卡", "立减卡", "福利金", "权益金", "膨胀金", "美团神券", "神券", "美团红包", "美团津贴",
            "抵扣金", "支付奖励", "贴纸", "兑换好礼", "下单返现",
            "消费券", "代金券", "优惠券", "商家券", "消费金", "花呗金", "体验金", "鼓励金", "淘金币", "京豆",
            "满减", "抵扣", "抵用", "已优惠", "优惠了", "已减", "共减", "直降", "免单",
            "已省", "共省", "省了", "立省", "省钱", "节省", "返现", "补贴", "减免", "集分宝",
            "碰一下", "笔笔攒", "运费险", "运费", "配送费", "打包费", "餐盒费", "餐具费",
            "组合支付", "支付宝红包",
            "立减", "优惠", "红包", "折扣", "积分",
            "当前余额", "可用余额", "账户余额", "卡内余额", "卡余额", "卡上余额", "剩余金额", "剩余余额",
            "参考汇率", "汇率", "手续费", "服务费", "费率"
        )

        private val MIXED_PAYMENT_NOISE_REGEX = Regex(
            "(?:红包|优惠|立减|满减|代金券|优惠券|消费券|商家券|消费金|花呗金|体验金|鼓励金|淘金币|京豆|神券|百亿补贴|美团津贴|下单返现|配送费|打包费|餐盒费|餐具费|运费|集分宝|积分|抵扣|抵用|组合支付|补贴|减免|返现|碰一下)"
        )

        private val MIXED_PAYMENT_DISCOUNT_SUFFIX_REGEX = Regex(
            "\\s*[+/]\\s*(?:支付宝|美团)?(?:红包|立减金|消费券|优惠券|代金券|商家券|消费金|花呗金|体验金|鼓励金|淘金币|京豆|神券|美团津贴|集分宝|积分|抵扣|抵用|优惠|补贴|减免|运费|配送费|打包费|餐盒费)[^+/]*"
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

        private val DIRECTIONAL_PREFIX_EXPENSE_REGEX = Regex("^(?:支出|付款|消费)\\s*")
        private val DIRECTIONAL_PREFIX_INCOME_REGEX = Regex("^(?:收入|收款|退款)\\s*")
        private val DIRECTIONAL_HERO_PROTECT_REGEX = Regex(
            "^(?:支出|付款|消费|收入|收款|退款)\\s*[+-]?(?:US\\$|HK\\$|NT\\$|S\\$|A\\$|C\\$|JP¥|USD|EUR|GBP|HKD|JPY|SGD|AUD|CAD|CHF|TWD|KRW|CNY|RMB|\\$|€|£|¥|￥|₩)?\\s*[0-9]+(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?",
            RegexOption.IGNORE_CASE
        )

        private val STANDALONE_HERO_AMOUNT_REGEX = Regex(
            "^(?:支出|付款|消费|收入|收款|退款)?[+-]?(?:US\\$|HK\\$|NT\\$|S\\$|A\\$|C\\$|JP¥|USD|EUR|GBP|HKD|JPY|SGD|AUD|CAD|CHF|TWD|KRW|CNY|RMB|\\$|€|£|¥|￥|₩)?[+-]?[0-9]+(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?(?:USD|EUR|GBP|HKD|JPY|SGD|AUD|CAD|CHF|TWD|KRW|CNY|RMB|元|美元|美金|欧元|英镑|港币|港元|日元|新元|新加坡元|澳元|加元|瑞郎|新台币|韩元)?$",
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
            "(?:当前余额|可用余额|账户余额|卡内余额|卡余额|卡上余额|剩余金额|剩余余额|余额|参考汇率|汇率|支付优惠|优惠信息|优惠说明|优惠明细|商家优惠|平台优惠|店铺优惠|店铺立减|跨店满减|百亿补贴|碰一下立减|碰一下|官方立减|微信立减金|立减金|立减|已优惠|优惠了|优惠金额|优惠|支付宝红包|美团红包|美团津贴|美团神券|神券|红包抵扣|红包优惠|满减|代金券|优惠券|消费券|商家券|消费金|花呗金|体验金|鼓励金|淘金币抵扣|淘金币|京豆抵扣|京豆|下单返现|折扣|抵扣|抵用|补贴|减免|已减|共减|直降|免单|返现|笔笔攒|运费险|配送费|打包费|餐盒费|餐具费|运费|组合支付|集分宝|积分|手续费|服务费|费率|已省|共省|立省|省了|省钱|节省|(?<![\\u4e00-\\u9fa5])省|discount|coupon|voucher|cashback|balance|service\\s+fee|fee)(?:为|：|:|\\s)*[+-]?\\s*(?:US\\$|HK\\$|NT\\$|S\\$|A\\$|C\\$|JP¥|USD|EUR|GBP|HKD|JPY|SGD|AUD|CAD|CHF|TWD|KRW|CNY|RMB|[¥￥$€£₩])?\\s*[+-]?\\s*[0-9]+(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?\\s*(?:元|块|USD|EUR|GBP|CNY|RMB)?",
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
            "^(?:消费金额|外币金额|原币金额|原始金额|实付|实付款|合计实付|商品实付|实付总额|共支付|已支付|已付|商品总价|商品金额|订单总额|商品总额|付款金额|支付金额|折算人民币|折合人民币|支付优惠|优惠信息|优惠说明|优惠明细|商家优惠|平台优惠|店铺优惠|店铺立减|跨店满减|百亿补贴|碰一下立减|碰一下|官方立减|微信立减金|立减金|已优惠|优惠了|优惠|立减|满减|代金券|优惠券|消费券|商家券|消费金|花呗金|体验金|鼓励金|淘金币|京豆|美团红包|美团津贴|美团神券|神券|下单返现|配送费|打包费|餐盒费|餐具费|运费|折扣|抵扣|抵用|补贴|减免|已减|共减|直降|免单|返现|笔笔攒|运费险|组合支付|集分宝|已省|共省|立省|省了|省钱|节省|支付宝红包(?!$)|红包(?!$)|使用\\s*[0-9.]+\\s*元|满\\s*[0-9.]+\\s*减\\s*[0-9.]+|当前余额|可用余额|账户余额|卡内余额|卡余额|卡上余额|剩余金额|剩余余额|余额|尾号|卡号)"
        )
        private val MERCHANT_ACTION_PREFIX_REGEX = Regex("^(?:向|给|付款给|转账给|转给|在)\\s*")
        private val MERCHANT_ACTION_SUFFIX_REGEX = Regex(
            "\\s*(?:确认收款|确认收钱|已收款|已收钱|待确认|发红包|收红包|付款|支付|转账|消费|收款|确认|完成|复制|查看|详情)$"
        )
        private val MERCHANT_PENDING_WRAPPER_REGEX = Regex(
            "^(?:请?等待?|请?待)(.+?)(?:确认收款|确认收钱|确认领取|确认|收款|收钱|领取|查收)$"
        )
        private val MERCHANT_FROM_WRAPPER_REGEX = Regex(
            "^来自(.+?)的(?:转账|红包|收款|付款)$"
        )
        private val TRAILING_MASKED_REALNAME_PAREN_REGEX = Regex(
            "\\s*[(（]\\s*(?:[*＊]{1,4}[\\u4e00-\\u9fa5A-Za-z]{1,2}|[\\u4e00-\\u9fa5A-Za-z][*＊]{1,4})\\s*[)）]$"
        )
        private val PURE_NUMBER_OR_CARD_REGEX = Regex("^[\\d*xX()（）\\s-]+$")

        private val UI_STOP_WORDS = setOf(
            "账单详情", "账单详情页", "交易详情", "收支详情", "转账详情", "收款详情", "退款详情", "订单详情",
            "交易明细", "账单明细", "电子回单", "支付结果", "付款结果", "交易结果",
            "支付成功", "付款成功", "交易成功", "消费成功", "转账成功", "汇款成功", "收款成功",
            "已收款", "已存入零钱", "退款成功", "已退款", "退款到账", "已退回",
            "完成", "返回", "关闭", "确定", "确认", "取消", "返回商家", "查看账单", "查看详情",
            "查看零钱", "复制", "分享", "截屏", "投诉", "联系客服", "联系收款方", "联系商户", "常见问题",
            "对订单有疑惑", "对此订单有疑问", "对订单有疑问", "申请退款", "申请售后", "申请开票", "在此商户的交易记录",
            "在此商户的交易", "账单服务", "发起群收款", "申请电子凭证", "申请转账电子凭证", "本服务由财付通提供",
            "定位到聊天位置", "查看往来记录", "查看往来转账", "全部账单", "查看红包详情", "红包详情",
            "复制链接", "在浏览器打开", "在默认浏览器打开", "刷新", "搜索页面内容", "字体大小", "调整字体", "浮窗",
            "发送给朋友", "分享到朋友圈",
            "更多", "删除账单", "标签和备注", "添加备注", "备注…", "备注...", "分类", "计入收支", "不计入收支",
            "联系商家", "aa收款", "往来流水证明", "申请电子回单",
            "账单管理", "我的消费图鉴", "为您推荐", "服务详情", "进入小程序", "转账红包",
            "回首页", "返回首页", "回到首页", "去首页", "首页", "返回上一页", "返回应用", "返回app",
            "返回微信", "返回支付宝", "返回淘宝", "回到商家", "继续逛逛", "我知道了", "知道了", "好的", "跳过",
            "下一步", "立即体验", "去看看", "查看订单", "查看进度", "查看账单详情", "支付完成", "付款完成",
            "已完成", "关闭页面", "收起", "展开", "分享给好友", "保存图片", "再来一单",
            "去评价", "立即评价", "开发中", "加载中", "加载中...",
            "待确认", "待收款", "待确认收款", "待确认收钱", "对方", "朋友", "发红包",
            "添加", "去添加", "点击添加", "请选择", "开关", "未设置", "暂无", "去设置",
            "微信", "微信支付", "微信收款助手", "支付宝", "云闪付", "财付通",
            "小米钱包", "小米智能卡", "华为钱包", "荣耀钱包", "oppo钱包", "vivo钱包", "三星智付",
            "网商银行", "招商银行", "工商银行", "建设银行",
            "农业银行", "中国银行", "交通银行", "平安银行", "wise", "paypal", "revolut",
            "支付", "收款", "转账", "付款", "人民币", "美元", "欧元", "英镑", "done", "ok", "close"
        )

        private val UI_STOP_PREFIXES = listOf(
            "对订单有疑", "对此订单有疑", "在此商户的", "联系收款方", "申请开票", "申请退款", "申请售后",
            "查看", "本服务由", "网页由", "该网页由", "此网页由", "页面由", "稍等一下"
        )

        private val GENERIC_CHANNEL_MERCHANTS = setOf(
            "微信支付", "支付宝", "云闪付", "扫二维码付款", "二维码付款", "扫码付款", "二维码收款", "扫码收款",
            "转账", "收款", "消费", "支付"
        )

        private val TAOBAO_SHELL_MERCHANTS_FOR_HERO = setOf(
            "淘宝闪购", "淘宝", "天猫"
        )

        private val PAYMENT_INSTRUMENT_SUFFIX_STRIP_REGEX = Regex(
            "(?:储蓄卡|借记卡|信用卡|快捷支付|支付)?\\s*(?:[(（]\\s*\\d{4}\\s*[)）])?$"
        )

        private val GENERIC_PAYMENT_ACCOUNT_MATCH_EXCLUSIONS = setOf(
            "支付宝", "微信", "微信支付", "零钱", "银行", "银行卡", "储蓄卡", "借记卡", "信用卡", "余额", "账户余额"
        )

        private val STANDALONE_PAYMENT_METHOD_LINE_REGEX = Regex(
            "^(?:网商银行|花呗|余额宝|余利宝|零钱通|微众银行|招商银行|工商银行|建设银行|农业银行|中国银行|交通银行|平安银行|中信银行|浦发银行|兴业银行|光大银行|民生银行|广发银行|华夏银行|邮储银行)(?:储蓄卡|借记卡|信用卡)?(?:[(（]\\d{4}[)）])?$"
        )

        private val PAY_SUCCESS_PROMO_NOISE_REGEX = Regex(
            "(?:摇一摇|有优惠|可叠加|立减金|随机立减|最高立减|淘宝闪购|京东秒杀|百亿补贴|优惠券|代金券|领券|立即领取|点击领取|去领取|去看看|去使用|去抽奖|抽奖|支付有礼|支付有优惠|天天领红包|免费领|稍等一下|本服务由财付通提供|猜你喜欢|广告|推广|赞助)",
            RegexOption.IGNORE_CASE
        )

        private val TAOBAO_OWN_PAY_SUCCESS_PROMO_NOISE_REGEX = Regex(
            "(?:摇一摇|有优惠|可叠加|立减金|随机立减|最高立减|京东秒杀|百亿补贴|优惠券|代金券|领券|立即领取|点击领取|去领取|去看看|去使用|去抽奖|抽奖|支付有礼|支付有优惠|天天领红包|免费领|稍等一下|本服务由财付通提供|猜你喜欢|广告|推广|赞助)",
            RegexOption.IGNORE_CASE
        )

        private val DISCOUNT_BREAKDOWN_CLASSIFY_NOISE_REGEX = Regex(
            "(?:支付宝红包|美团红包|红包抵扣|红包优惠|立减金|微信立减金|碰一下立减|官方立减|店铺立减|跨店满减|满减|消费券|代金券|优惠券|商家券|组合支付|笔笔攒|抵扣金)\\s*(?:[+-]?\\s*[¥￥$€£₩]?\\s*[0-9]+(?:\\.[0-9]{1,2})?|[+-]\\s*[0-9]+)",
            RegexOption.IGNORE_CASE
        )

        private val P2P_PRESERVED_LABEL_MERCHANTS = setOf(
            "支付宝红包", "微信红包", "qq红包", "企业微信红包"
        )

        private val P2P_COMMERCIAL_EXCLUSION_MARKERS = listOf(
            "扫二维码付款", "二维码付款", "扫码付款", "扫二维码收款", "二维码收款", "扫码收款",
            "向商户付款", "向商家付款", "收款商户", "收款商户全称", "收单机构", "清算机构",
            "在此商户的交易", "商户单号", "商户订单号", "商家订单号"
        )

        private val P2P_CONTEXT_MARKERS = listOf(
            "口令红包", "收到口令红包", "发口令红包", "支付宝红包", "微信红包", "qq红包", "企业微信红包",
            "转账给", "转账说明", "转账备注", "转账单号", "转账对象", "转账时间", "收钱时间",
            "微信转账", "支付宝转账", "个人转账", "好友转账", "面对面红包", "拼手气红包",
            "红包说明", "发红包时间", "收红包时间", "红包个数", "查看红包详情",
            "对方已收钱", "对方已收款", "对方已领取", "已被领完", "等待对方收钱", "等待对方收款",
            "待确认收钱", "待确认收款", "朋友已确认收钱",
            "查看往来转账", "申请转账电子凭证", "定位到聊天位置"
        )

        private val P2P_TRANSFER_LINE_REGEX = Regex(
            "(?:(?:向|给|来自).{1,24}(?:转账|红包)|(?:待|等待).{1,24}(?:确认收钱|确认收款|确认领取))"
        )

        private val COMMERCIAL_ENTITY_KEYWORDS = listOf(
            "餐饮", "餐馆", "餐厅", "饭店", "饭馆", "食堂", "面馆", "粉馆", "汤馆", "小馆", "小厨", "食府",
            "茶餐厅", "火锅", "烧烤", "烤肉", "烤鱼", "麻辣烫", "奶茶", "咖啡", "烘焙", "蛋糕", "面包",
            "小吃", "早餐", "夜宵", "大排档", "胡辣汤", "煎饼", "拉面", "米线", "酸辣粉",
            "商超", "超市", "便利店", "百货", "商场", "商店", "小店", "小铺", "店铺", "门店", "专柜",
            "旗舰店", "专卖店", "专营店", "官方店", "自营店", "自营",
            "大药房", "药房", "药店", "医院", "诊所", "口腔", "门诊",
            "公司", "有限公司", "有限责任公司", "经营部", "工作室", "个体户", "商户_",
            "银行", "酒店", "宾馆", "民宿", "客栈", "影城", "影院", "美容", "理发", "美发", "美甲",
            "健身", "宠物", "书店", "加油站", "停车场", "航空", "铁路", "外卖", "商城", "平台",
            "coffee", "cafe", "shop", "store", "mart", "market", "mall", "inc", "ltd", "llc",
            "corp", "studio", "hotel", "restaurant", "clinic", "hospital"
        )

        private val COMMERCIAL_STORE_SUFFIX_REGEX = Regex("店(?:[(（][^()（）]+[)）])?$")

        private val QR_EXPENSE_PAYEE_REGEX = Regex(
            "(?:扫二维码付款|二维码付款|扫码付款|向商户付款|向商家付款)\\s*(?:[-—–:：]\\s*)?给\\s*(.+)"
        )

        private val QR_INCOME_PAYER_REGEX = Regex(
            "(?:扫二维码收款|二维码收款|扫码收款|微信收款)\\s*(?:[-—–:：]\\s*)?来自\\s*(.+)"
        )

        private val LONG_DIGIT_RUN_REGEX = Regex("\\d{7,}")

        private val PERSONAL_NICKNAME_PREFIXES = listOf(
            "微信用户", "支付宝用户", "qq用户", "抖音用户"
        )

        private val MASKED_PERSONAL_NAME_REGEX = Regex(
            "^(?:[*＊]{1,4}[\\u4e00-\\u9fa5A-Za-z]{1,2}|[\\u4e00-\\u9fa5A-Za-z][*＊]{1,4}|[*＊]{2,4})$"
        )

        private val CHROME_NOISE_LINES = setOf(
            "返回", "复制", "已复制", "copy", "常见问题", "对订单有疑惑", "对此订单有疑问", "对订单有疑问",
            "在此商户的交易记录", "申请开票", "标签和备注", "添加备注", "备注…", "备注...",
            "联系商家", "联系商户", "aa收款", "查看往来记录", "查看往来转账", "往来流水证明",
            "申请电子回单", "申请转账电子凭证",
            "账单管理", "我的消费图鉴", "为您推荐", "服务详情", "进入小程序",
            "回首页", "返回首页", "回到首页", "去首页", "首页", "返回上一页", "返回应用", "返回app",
            "返回微信", "返回支付宝", "返回淘宝", "回到商家", "继续逛逛", "我知道了", "知道了", "好的", "跳过",
            "下一步", "立即体验", "去看看", "查看订单", "查看进度", "查看账单详情", "支付完成", "付款完成",
            "已完成", "关闭页面", "收起", "展开", "分享给好友", "保存图片", "再来一单",
            "去评价", "立即评价", "开发中", "加载中", "加载中...",
            "添加", "去添加", "点击添加", "请选择", "开关", "未设置", "暂无", "去设置", "删除账单"
        )

        private val ECOMMERCE_AND_DELIVERY_PACKAGES = setOf(
            "com.xunmeng.pinduoduo",
            "com.taobao.taobao",
            "com.tmall.wireless",
            "com.jingdong.app.mall",
            "com.sankuai.meituan",
            "com.sankuai.meituan.takeoutnew",
            "me.ele",
            "com.ss.android.ugc.aweme"
        )

        private val TAKEOUT_PACKAGES = setOf(
            "com.sankuai.meituan.takeoutnew",
            "me.ele"
        )

        private val PHONE_WALLET_PACKAGES = setOf(
            "com.miui.tsmclient",
            "com.mipay.wallet",
            "com.xiaomi.pass",
            "com.huawei.wallet",
            "com.hihonor.wallet",
            "com.finshell.wallet",
            "com.coloros.wallet",
            "com.vivo.wallet",
            "com.samsung.android.spay"
        )

        private val TRANSIT_ACCOUNT_NAME_KEYWORDS = listOf(
            "公交", "交通卡", "畅通卡", "一卡通", "通卡", "八达通", "地铁卡"
        )

        private val GENERIC_WALLET_BRANDS = setOf(
            "小米钱包", "小米智能卡", "华为钱包", "荣耀钱包", "oppo钱包", "vivo钱包", "三星智付", "钱包"
        )

        private val TRANSIT_NON_PAYMENT_NOISE_KEYWORDS = listOf(
            "进站成功", "已进站", "刷卡进站", "闸机已开启", "请刷卡出站",
            "余额不足", "请及时充值", "开卡成功", "nfc已开启", "nfc 已开启"
        )

        private val TRANSIT_DEDUCT_PHRASES = listOf(
            "出站扣费", "乘车扣费", "刷卡消费", "刷卡扣费", "本次消费", "本次扣费", "本次扣款",
            "扣费", "扣款", "消费", "实扣", "车费"
        )

        private val TRANSIT_VOUCHER_CUES = listOf(
            "刷卡成功", "刷卡扣费", "出站扣费", "出站成功", "乘车扣费",
            "重庆畅通卡", "畅通卡", "羊城通", "岭南通", "深圳通", "上海交通卡",
            "京津冀", "长安通", "天府通", "金陵通", "一卡通", "公交卡", "交通卡", "地铁卡"
        )

        private val TRANSIT_RESULT_PHRASES = listOf(
            "刷卡成功", "刷卡扣费", "出站扣费", "出站成功", "乘车扣费",
            "扣费", "扣款", "消费", "支付成功", "付款成功"
        )

        private val ECOMMERCE_HARD_BLOCKERS = listOf(
            "加入购物车", "立即购买", "去结算", "提交订单", "立即下单",
            "待付款", "等待买家付款", "全部订单",
            "待支付", "去支付", "去付款", "购物车", "确认订单"
        )

        private val ECOMMERCE_RECOMMENDATION_BOUNDARY_KEYWORDS = listOf(
            "猜你喜欢", "为您推荐", "更多推荐", "好物推荐", "附近好店", "周边推荐"
        )

        private val ECOMMERCE_PAY_SUCCESS_ACTION_WORDS = listOf(
            "查看订单", "订单详情", "返回首页", "回到首页", "继续逛逛", "返回淘宝", "查看进度"
        )

        private val PURE_ECOMMERCE_PAY_RESULT_BUTTONS = listOf(
            "返回首页", "回到首页", "继续逛逛", "返回淘宝"
        )

        private val TAOBAO_TAKEOUT_CUES = listOf(
            "淘宝闪购", "外卖", "骑士", "骑手", "预计送达", "餐盒费", "打包费", "配送费",
            "等待商家接单", "商家已接单", "待商家接单", "商家备餐中", "正在备餐", "商家已出餐", "催单"
        )

        private val REPEATED_ORDER_NO_LINE_REGEX = Regex("^(?:订单编号|订单号|订单号码)(?:[:：\\s0-9A-Za-z]|$)")
        private val REPEATED_ACTUAL_PAID_LINE_REGEX = Regex("^(?:实付款|实付)(?:金额)?(?:[:：\\s¥￥$0-9]|$)")

        private val ORDER_FULFILLMENT_STATUS_KEYWORDS = listOf(
            "已付款", "买家已付款", "订单已完成",
            "已送达", "商品已送达", "商家已接单", "骑手已接单", "配送中",
            "待收货", "待发货", "已签收", "卖家已发货", "拼单成功",
            "等待商家接单", "待商家接单", "商家接单中", "等待接单",
            "商家备餐中", "正在备餐", "备餐中", "正在出餐", "商家已出餐",
            "等待骑手接单", "待骑手接单", "等待骑士接单", "待骑士接单",
            "骑士已接单", "骑士正赶往商家", "骑士赶往商家", "骑手正赶往商家", "骑手赶往商家",
            "骑士已到店", "骑士到店", "骑手已到店", "骑手到店",
            "骑士取餐中", "骑手取餐中", "骑士已取餐", "骑手已取餐",
            "骑士配送中", "骑手配送中", "正在配送"
        )

        private val ORDER_PAID_STATUS_KEYWORDS = ORDER_FULFILLMENT_STATUS_KEYWORDS + listOf(
            "交易成功", "已完成", "下单成功", "支付成功", "付款成功", "已支付"
        )

        private val ORDER_DETAIL_ANCHORS = listOf(
            "订单详情", "订单信息", "订单编号", "订单号", "订单号码", "订单进度",
            "收货信息", "收货地址", "配送信息", "送达时间", "预计送达",
            "付款时间", "成交时间", "下单时间", "支付方式", "申请售后", "申请退款", "再来一单",
            "联系商家", "联系骑手", "联系骑士", "催单", "修改地址", "取消订单"
        )

        private val ETA_DELIVERY_ANCHOR_REGEX = Regex("预计.{0,12}送达")

        private val ACTUAL_PAID_HINT_REGEX = Regex(
            "(?:合计实付|商品实付|实付总额|实付金额|实际付款|实付款|实付|共支付|已支付|已付).*[0-9]"
        )

        private val SUBTOTAL_OR_TOTAL_AMOUNT_REGEX = Regex(
            "(?:小计|合计)(?:金额)?(?:为|：|:|\\s)*([+-]?\\s*(?:US\\$|HK\\$|NT\\$|S\\$|A\\$|C\\$|JP¥|USD|EUR|GBP|HKD|JPY|SGD|CNY|RMB|[¥￥$€£₩])?\\s*[0-9]+(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?(?!\\s*(?:件|笔|单|次|个|份|盒|杯|瓶|袋|包|人|天|小时|分钟|秒|年|月|日|号))\\s*(?:元|美元|USD|EUR|GBP|CNY|RMB)?)",
            RegexOption.IGNORE_CASE
        )

        private val SUBTOTAL_OR_TOTAL_LABEL_REGEX = Regex("^(?:商品)?(?:小计|合计)(?:金额)?[:：]?$")

        private val STORE_NAME_SUFFIX_REGEX = Regex(
            "(?:旗舰店|专卖店|专营店|官方店|自营店|自营|大药房|便利店|餐饮店|专柜|门店|小店|店铺|店[(（][^()（）]{1,12}[)）]|店[)）]?)$"
        )

        private val ORDER_QTY_ONLY_REGEX = Regex("^(?:[xX×*]\\s*\\d+|\\d+\\s*[件份个盒杯瓶袋包]|共\\s*\\d+\\s*[件份])$")
        private val ITEM_QTY_SUFFIX_REGEX = Regex("([xX×*]\\s*\\d+|\\d+\\s*[件份])$")
        private val ITEM_TITLE_WITH_QTY_REGEX = Regex(".{2,}(?:[xX×*]\\s*\\d+|\\d+\\s*[件份])$")
        private val ORDER_SKU_PREFIX_REGEX = Regex("^(?:颜色|尺码|规格|款式|型号|口味|温度|甜度|份量|净含量|版本|套餐)[:：]")
        private val TRAILING_ITEM_PRICE_REGEX = Regex("\\s*[¥￥$]\\s*[0-9]+(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?$")
        private val MASKED_PHONE_REGEX = Regex("1\\d{2}\\*{4}\\d{4}")
        private val ORDER_ADDRESS_OR_LOGISTICS_REGEX = Regex(
            "(?:收货人|收货地址|配送地址|送达地址|送货至|送达至|送出|预计.*送达|快递已签收|已由.*签收|骑手.*(?:到店|取餐|配送|赶往|距)|骑士.*(?:到店|取餐|配送|赶往|距)|配送员|商家已出餐|号楼|单元|\\d+室|[省市区县].{1,12}(?:路|街|道|巷|号|大厦|小区|广场))"
        )

        private val ORDER_NOISE_UI_WORDS = setOf(
            "订单详情", "订单信息", "订单进度", "商品信息", "费用明细", "价格明细", "配送信息", "收货信息",
            "隐私保护中", "号码保护中", "查看物流", "申请售后", "申请退款", "联系商家", "联系客服",
            "再来一单", "开发票", "申请开票", "追加评价", "去评价", "确认收货", "延长收货", "修改地址",
            "进店逛逛", "进入店铺", "联系骑手", "打赏骑手", "联系骑士", "打赏骑士",
            "致电骑手", "致电骑士", "致电商家", "催单", "取消订单", "查看进度", "查看地图",
            "返回首页", "回到首页", "继续逛逛", "返回淘宝", "查看订单",
            "准时宝", "号码保护", "匿名购买",
            "退换无忧", "极速退款", "7天无理由", "假一赔十", "正品险", "坏果包赔", "买贵必赔",
            "复制单号", "淘宝闪购", "拼多多", "淘宝", "天猫", "京东", "美团", "美团外卖", "饿了么", "抖音商城"
        )

        private val PLATFORM_SHELL_MERCHANTS = setOf(
            "拼多多平台商户", "上海寻梦信息技术有限公司", "寻梦信息技术", "拼多多",
            "淘宝平台商户", "天猫平台商户", "淘宝网", "浙江淘宝网络有限公司", "浙江天猫技术有限公司", "淘宝", "天猫",
            "京东平台商户", "京东商城", "北京京东世纪信息技术有限公司", "网银在线", "京东",
            "美团平台商户", "北京三快在线科技有限公司", "三快在线", "美团", "美团外卖",
            "上海拉扎斯信息科技有限公司", "拉扎斯", "饿了么",
            "抖音支付", "抖音商城", "武汉合众易宝科技有限公司", "合众易宝"
        )

        private val PLATFORM_SHELL_SUBSTRINGS = listOf(
            "平台商户", "寻梦信息技术", "三快在线", "拉扎斯", "合众易宝", "网银在线",
            "浙江淘宝网络", "浙江天猫技术", "京东世纪信息"
        )

        private val PLATFORM_GOODS_PREFIX_REGEX = Regex(
            "^(?:拼多多平台商户|拼多多平台订单|拼多多订单|拼多多|淘宝网|淘宝平台订单|淘宝平台商户|淘宝订单|淘宝|天猫平台订单|天猫平台商户|天猫订单|天猫|京东平台订单|京东平台商户|京东商城订单|京东订单|京东|美团外卖订单|美团外卖|美团平台订单|美团订单|美团|饿了么外卖订单|饿了么外卖|饿了么订单|饿了么|抖音商城订单|抖音订单|抖音支付|抖音)(?:\\s*[-—:：/·|]\\s*|\\s+)"
        )

        private val PLATFORM_GOODS_ORDER_ID_PREFIX_REGEX = Regex(
            "^(?:(?:订单编号|订单号|单号)\\s*[:：]?\\s*[A-Za-z0-9_-]{6,}|[A-Za-z0-9_-]{8,})(?:\\s*[-—:：/·|]\\s*|\\s+)"
        )

        private val PLATFORM_GOODS_EMBEDDED_ORDER_ID_REGEX = Regex(
            "\\s*[-—:：/·|]\\s*(?:订单编号|订单号|单号)?[:：]?\\s*[0-9]{8,}(?:\\s*[-—:：/·|]\\s*|\\s+)"
        )

        private val SHOP_NAME_FROM_GOODS_REGEX = Regex(
            "^([^\\s,，;；/·|-]{2,26}?(?:(?:旗舰店|专卖店|专营店|官方店|自营店|自营|大药房|便利店|餐饮店|超市|专柜|门店|店)(?:[(（][^()（）]{1,16}[)）])?|[(（][^()（）]{1,16}店[)）]))(?:\\s*[-—·|/]\\s*|\\s+|$)"
        )
    }
}

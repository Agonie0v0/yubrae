package com.kairoledger.app.capture

import com.kairoledger.app.core.contract.ParsedBillDraft
import com.kairoledger.app.core.contract.RecordTransactionRequest
import com.kairoledger.app.core.model.CurrencyCatalog
import com.kairoledger.app.core.model.MoneyAmount
import com.kairoledger.app.data.db.AccountEntity
import com.kairoledger.app.data.db.CategoryEntity
import com.kairoledger.app.data.db.RawCaptureLogEntity
import com.kairoledger.app.data.db.TransactionEntity
import kotlin.math.abs

data class DedupMatch(
    val existingTransaction: TransactionEntity,
    val mergedRequest: RecordTransactionRequest,
    val reason: String
)

/**
 * 60 秒时间窗口跨渠道去重与信息融合引擎（纯 JVM 可测）。
 * 典型场景：微信/支付宝通知（含商户、无卡尾号） + 银行短信（含卡尾号6688与实际/折算人民币扣款额）自动融合。
 */
class CaptureDeduplicator(
    private val windowMillis: Long = DEFAULT_WINDOW_MILLIS,
    private val fxToleranceBps: Int = DEFAULT_FX_TOLERANCE_BPS
) {

    fun findMatchAndMerge(
        incomingDraft: ParsedBillDraft,
        incomingChannel: String,
        incomingSourcePackage: String,
        incomingRawText: String,
        recentTransactions: List<TransactionEntity>,
        recentLogsByTxId: Map<Long, RawCaptureLogEntity> = emptyMap(),
        accountsById: Map<Long, AccountEntity> = emptyMap(),
        categoriesById: Map<Long, CategoryEntity> = emptyMap(),
        rateResolver: (fromCurrency: String, toCurrency: String) -> Long = ::fallbackCrossRateMicros
    ): DedupMatch? {
        val candidates = recentTransactions
            .asSequence()
            .filter { tx ->
                tx.type == incomingDraft.type &&
                    abs(tx.timestamp - incomingDraft.timestamp) <= windowMillis
            }
            .sortedBy { abs(it.timestamp - incomingDraft.timestamp) }
            .toList()

        for (tx in candidates) {
            val existingLog = recentLogsByTxId[tx.id]
            if (!isEligibleChannelPair(tx, existingLog, incomingDraft, incomingChannel, incomingSourcePackage, incomingRawText)) {
                continue
            }
            if (!isAmountMatching(tx, incomingDraft, rateResolver)) {
                continue
            }

            val merged = buildMergedRequest(
                existing = tx,
                existingLog = existingLog,
                incomingDraft = incomingDraft,
                accountsById = accountsById,
                categoriesById = categoriesById
            )
            val reason = buildString {
                append("与 ${tx.sourceType} 交易 #${tx.id} 在 ${windowMillis / 1000}s 窗口内融合")
                if (merged.accountId != tx.accountId) append("（补全账户#${merged.accountId}）")
                if (merged.merchant != tx.merchant && merged.merchant.isNotBlank()) append("（补全商户:${merged.merchant}）")
                if (merged.origCurrency != tx.origCurrency || merged.manualAccountAmountMicros != null) {
                    append("（融合外币与实际折算扣款）")
                }
            }
            return DedupMatch(
                existingTransaction = tx,
                mergedRequest = merged,
                reason = reason
            )
        }
        return null
    }

    private fun isEligibleChannelPair(
        existing: TransactionEntity,
        existingLog: RawCaptureLogEntity?,
        incomingDraft: ParsedBillDraft,
        incomingChannel: String,
        incomingSourcePackage: String,
        incomingRawText: String
    ): Boolean {
        val existingPkg = existingLog?.sourcePackage.orEmpty()
        val isDifferentChannel = !existing.sourceType.equals(incomingChannel, ignoreCase = true)
        val isDifferentPackage = existingPkg.isNotBlank() &&
            incomingSourcePackage.isNotBlank() &&
            !existingPkg.equals(incomingSourcePackage, ignoreCase = true)

        if (isDifferentChannel || isDifferentPackage) {
            // 跨渠道/跨应用：若双方都有明确且截然不同的商户，避免误合并同一时间两笔不同消费
            if (existing.merchant.isNotBlank() &&
                incomingDraft.merchant.isNotBlank() &&
                !isMerchantCompatible(existing.merchant, incomingDraft.merchant)
            ) {
                return false
            }
            return true
        }

        // 同渠道同包名：仅当通知正文高度一致（系统重复回调同一通知）时判重合并
        val existingRaw = existingLog?.let { "${it.title}\n${it.content}".trim() }.orEmpty()
        if (existingRaw.isNotBlank() && existingRaw == incomingRawText.trim()) {
            return true
        }
        return false
    }

    private fun isAmountMatching(
        existing: TransactionEntity,
        incoming: ParsedBillDraft,
        rateResolver: (String, String) -> Long
    ): Boolean {
        if (existing.origCurrency.equals(incoming.origCurrency, ignoreCase = true)) {
            return abs(existing.origAmountMicros - incoming.origAmountMicros) <= SAME_CURRENCY_TOLERANCE_MICROS
        }

        // 跨币种匹配：如先捕获外币 $15.00 USD，后收到银行 CNY 扣款短信 ¥109.20
        val expectedInIncomingCurrency = if (existing.accountCurrency.equals(incoming.origCurrency, ignoreCase = true) &&
            existing.accountAmountMicros > 0L
        ) {
            existing.accountAmountMicros
        } else {
            val rate = rateResolver(existing.origCurrency, incoming.origCurrency)
            MoneyAmount.convert(existing.origAmountMicros, rate)
        }

        if (expectedInIncomingCurrency <= 0L) return false
        val tolerance = maxOf(
            MIN_FX_TOLERANCE_MICROS,
            expectedInIncomingCurrency * fxToleranceBps / 10_000L
        )
        return abs(expectedInIncomingCurrency - incoming.origAmountMicros) <= tolerance
    }

    private fun buildMergedRequest(
        existing: TransactionEntity,
        existingLog: RawCaptureLogEntity?,
        incomingDraft: ParsedBillDraft,
        accountsById: Map<Long, AccountEntity>,
        categoriesById: Map<Long, CategoryEntity>
    ): RecordTransactionRequest {
        val existingHasCardHint = existingLog?.let {
            CARD_HINT_REGEX.containsMatchIn("${it.title} ${it.content}")
        } == true

        // 若新采集报文带有明确卡尾号（如银行短信 尾号6688），升级路由到真实银行卡账户
        val finalAccountId = when {
            incomingDraft.accountHint.isNotBlank() && incomingDraft.accountId != null && !existingHasCardHint ->
                incomingDraft.accountId
            existing.accountId > 0L -> existing.accountId
            else -> incomingDraft.accountId ?: existing.accountId
        }

        val finalAccount = accountsById[finalAccountId]
        val finalAccountCurrency = finalAccount?.currencyCode ?: existing.accountCurrency

        // 融合币种与实际折算扣款额
        var finalOrigCurrency = existing.origCurrency
        var finalOrigAmountMicros = existing.origAmountMicros
        var manualAccountAmountMicros: Long? = null

        if (!existing.origCurrency.equals(incomingDraft.origCurrency, ignoreCase = true)) {
            // 优先保留外币作为原币种，人民币/账户币种作为真实入账折算金额
            val baseCurrency = CurrencyCatalog.DEFAULT_BASE_CURRENCY
            val existingIsBase = existing.origCurrency.equals(baseCurrency, ignoreCase = true)
            val incomingIsBase = incomingDraft.origCurrency.equals(baseCurrency, ignoreCase = true)

            val (foreignCurrency, foreignMicros, settlementCurrency, settlementMicros) = when {
                !existingIsBase && incomingIsBase ->
                    Quad(existing.origCurrency, existing.origAmountMicros, incomingDraft.origCurrency, incomingDraft.origAmountMicros)
                existingIsBase && !incomingIsBase ->
                    Quad(incomingDraft.origCurrency, incomingDraft.origAmountMicros, existing.origCurrency, existing.origAmountMicros)
                else ->
                    Quad(existing.origCurrency, existing.origAmountMicros, incomingDraft.origCurrency, incomingDraft.origAmountMicros)
            }

            finalOrigCurrency = foreignCurrency
            finalOrigAmountMicros = foreignMicros
            if (finalAccountCurrency.equals(settlementCurrency, ignoreCase = true) &&
                !finalAccountCurrency.equals(finalOrigCurrency, ignoreCase = true)
            ) {
                manualAccountAmountMicros = settlementMicros
            }
        } else if (existing.origCurrency != finalAccountCurrency &&
            existing.accountAmountMicros != existing.origAmountMicros &&
            existing.accountCurrency == finalAccountCurrency
        ) {
            manualAccountAmountMicros = existing.accountAmountMicros
        }

        // 融合商户：优先保留明确、非泛化的商户名
        val finalMerchant = chooseBetterMerchant(existing.merchant, incomingDraft.merchant)

        // 融合分类：用更具体的分类替代泛化分类
        val finalCategoryId = chooseBetterCategoryId(
            existingCategoryId = existing.categoryId,
            incomingCategoryId = incomingDraft.categoryId,
            categoriesById = categoriesById
        )

        // 融合备注
        val finalNote = mergeNotes(existing.note, incomingDraft.accountHint, incomingDraft.note)

        return RecordTransactionRequest(
            existingId = existing.id,
            type = existing.type,
            timestamp = minOf(existing.timestamp, incomingDraft.timestamp),
            origAmountMicros = finalOrigAmountMicros,
            origCurrency = finalOrigCurrency,
            accountId = finalAccountId,
            manualAccountAmountMicros = manualAccountAmountMicros,
            toAccountId = existing.toAccountId,
            manualToAmountMicros = existing.toAmountMicros,
            categoryId = finalCategoryId,
            merchant = finalMerchant,
            note = finalNote,
            tags = existing.tags,
            reimburseStatus = existing.reimburseStatus,
            sourceType = existing.sourceType,
            rawEventId = existing.rawEventId,
            status = existing.status
        )
    }

    private fun chooseBetterMerchant(existing: String, incoming: String): String {
        val a = existing.trim()
        val b = incoming.trim()
        if (a.isBlank()) return b
        if (b.isBlank()) return a
        val aGeneric = GENERIC_MERCHANTS.any { a.contains(it, ignoreCase = true) }
        val bGeneric = GENERIC_MERCHANTS.any { b.contains(it, ignoreCase = true) }
        return when {
            aGeneric && !bGeneric -> b
            !aGeneric && bGeneric -> a
            b.length > a.length && !b.contains("尾号") -> b
            else -> a
        }
    }

    private fun chooseBetterCategoryId(
        existingCategoryId: Long?,
        incomingCategoryId: Long?,
        categoriesById: Map<Long, CategoryEntity>
    ): Long? {
        if (existingCategoryId == null) return incomingCategoryId
        if (incomingCategoryId == null || existingCategoryId == incomingCategoryId) return existingCategoryId

        val existingName = categoriesById[existingCategoryId]?.name.orEmpty()
        val incomingName = categoriesById[incomingCategoryId]?.name.orEmpty()

        val existingIsGeneric = existingName in GENERIC_CATEGORY_NAMES
        val incomingIsGeneric = incomingName in GENERIC_CATEGORY_NAMES

        return if (existingIsGeneric && !incomingIsGeneric) incomingCategoryId else existingCategoryId
    }

    private fun mergeNotes(existingNote: String, incomingAccountHint: String, incomingNote: String): String {
        val base = existingNote.trim()
        val hintTag = if (incomingAccountHint.isNotBlank() && !base.contains(incomingAccountHint)) {
            "尾号$incomingAccountHint"
        } else {
            ""
        }
        return when {
            base.isBlank() && incomingNote.isBlank() -> hintTag
            base.isBlank() -> incomingNote.trim()
            hintTag.isNotBlank() -> "$base | $hintTag"
            else -> base
        }
    }

    private fun isMerchantCompatible(a: String, b: String): Boolean {
        val na = a.trim().lowercase()
        val nb = b.trim().lowercase()
        if (na == nb || na.contains(nb) || nb.contains(na)) return true
        if (GENERIC_MERCHANTS.any { na.contains(it) || nb.contains(it) }) return true
        return false
    }

    private data class Quad(
        val first: String,
        val second: Long,
        val third: String,
        val fourth: Long
    )

    companion object {
        const val DEFAULT_WINDOW_MILLIS = 60_000L
        const val DEFAULT_FX_TOLERANCE_BPS = 500 // 5.00%
        private const val SAME_CURRENCY_TOLERANCE_MICROS = 100L // 0.01 元容差
        private const val MIN_FX_TOLERANCE_MICROS = 1_000L // 0.10 元最低容差

        private val CARD_HINT_REGEX = Regex("(?:尾号|末四位|ending\\s+in)\\s*\\d{4}", RegexOption.IGNORE_CASE)

        private val GENERIC_MERCHANTS = setOf(
            "微信支付", "支付宝", "网上消费", "银联消费", "快捷支付", "消费", "刷卡消费"
        )

        private val GENERIC_CATEGORY_NAMES = setOf(
            "其他支出", "其他收入", "日用购物"
        )

        fun fallbackCrossRateMicros(fromCurrency: String, toCurrency: String): Long {
            val from = fromCurrency.trim().uppercase()
            val to = toCurrency.trim().uppercase()
            if (from == to) return MoneyAmount.RATE_SCALE
            val fromToCny = CurrencyCatalog.find(from).fallbackRateToCnyMicros
            val toToCny = CurrencyCatalog.find(to).fallbackRateToCnyMicros
            return MoneyAmount.crossRate(fromToCny, toToCny)
        }
    }
}

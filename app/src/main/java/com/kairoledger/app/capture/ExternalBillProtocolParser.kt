package com.kairoledger.app.capture

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import com.kairoledger.app.core.model.CurrencyCatalog
import com.kairoledger.app.core.model.MoneyAmount
import com.kairoledger.app.data.db.TransactionType
import java.util.Locale

internal data class ExternalBillParams(
    val origAmountMicros: Long,
    val type: String,
    val categoryName: String?,
    val accountNameOrHint: String?,
    val merchant: String,
    val note: String,
    val timestamp: Long,
    val currency: String?,
    val showFloatingPrompt: Boolean
)

/**
 * 统一解析 AutoAccounting 广播 Intent 与 `kairoledger://addbill?...` URI 参数。
 */
internal object ExternalBillProtocolParser {

    fun fromIntent(intent: Intent): ExternalBillParams? {
        val extras = intent.extras ?: Bundle.EMPTY
        val getter: (String) -> String? = { key -> readExtraAsString(extras, key) }
        return parse(getter)
    }

    fun fromUri(uri: Uri): ExternalBillParams? {
        val getter: (String) -> String? = { key -> uri.getQueryParameter(key) }
        return parse(getter)
    }

    private fun parse(getParam: (String) -> String?): ExternalBillParams? {
        val moneyRaw = getParam("money") ?: getParam("amount") ?: return null
        val amountMicros = MoneyAmount.parseToMicros(moneyRaw)?.takeIf { it > 0L } ?: return null

        val type = parseType(getParam("type"))
        val category = getParam("category")?.trim()?.takeIf { it.isNotEmpty() }
        val account = (getParam("account") ?: getParam("accountName"))?.trim()?.takeIf { it.isNotEmpty() }
        val remark = (getParam("remark") ?: getParam("note")).orEmpty().trim()
        val merchant = (getParam("merchant") ?: getParam("shopName") ?: remark).trim()
        val time = parseTimestamp(getParam("time"))
        val currency = getParam("currency")?.trim()?.takeIf { it.isNotEmpty() }?.let {
            CurrencyCatalog.find(it).code
        }
        val showPrompt = getParam("showPrompt")?.lowercase(Locale.ROOT)?.let {
            it != "false" && it != "0"
        } ?: true

        return ExternalBillParams(
            origAmountMicros = amountMicros,
            type = type,
            categoryName = category,
            accountNameOrHint = account,
            merchant = merchant.take(40),
            note = remark,
            timestamp = time,
            currency = currency,
            showFloatingPrompt = showPrompt
        )
    }

    private fun parseType(raw: String?): String {
        val normalized = raw?.trim()?.uppercase(Locale.ROOT).orEmpty()
        return when (normalized) {
            "1", "INCOME", "IN", "收入" -> TransactionType.INCOME
            "2", "TRANSFER", "转账" -> TransactionType.TRANSFER
            else -> TransactionType.EXPENSE
        }
    }

    private fun parseTimestamp(raw: String?): Long {
        val cleaned = raw?.trim().orEmpty()
        if (cleaned.isEmpty()) return System.currentTimeMillis()
        val numeric = cleaned.toLongOrNull() ?: return System.currentTimeMillis()
        return if (numeric in 1L..9_999_999_999L) numeric * 1000L else numeric
    }

    @Suppress("DEPRECATION")
    private fun readExtraAsString(extras: Bundle, key: String): String? {
        if (!extras.containsKey(key)) return null
        val value = extras.get(key) ?: return null
        return value.toString().trim().takeIf { it.isNotEmpty() }
    }
}

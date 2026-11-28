package com.yubrae.app.capture.screen

import com.yubrae.app.core.contract.ParsedBillDraft

enum class ScreenPageKind { PAY_SUCCESS, BILL_DETAIL, ORDER_DETAIL, UNKNOWN }

data class ScreenTextNode(
    val text: String,
    val topPx: Int = -1,
    val heightPx: Int = 0,
    val isContentDesc: Boolean = false,
    val isVisibleToUser: Boolean = true
)

data class ScreenSnapshot(
    val packageName: String,
    val className: String,
    val texts: List<String>,
    val capturedAtMillis: Long = System.currentTimeMillis(),
    val nodes: List<ScreenTextNode> = emptyList(),
    val screenHeightPx: Int = 0
)

data class ScreenParseOutcome(
    val pageKind: ScreenPageKind,
    val draft: ParsedBillDraft?,
    val orderNo: String = "",
    val dedupFingerprint: String = "",
    val summaryText: String = "",
    val shouldFallbackToLlm: Boolean = false,
    val ignoredReason: String = ""
)

/**
 * 收银台 / 提交订单待确认支付上下文（纯 JVM 模型，仅驻留内存短 TTL，绝不直接落账）。
 */
data class PendingCheckoutContext(
    val sourcePackage: String,
    val merchant: String = "",
    val goodsSummary: String = "",
    val origAmountMicros: Long = 0L,
    val origCurrency: String = "CNY",
    val discountAmountMicros: Long = 0L,
    val paymentMethodText: String = "",
    val accountHint: String = "",
    val isTakeout: Boolean = false,
    val capturedAtMillis: Long = System.currentTimeMillis(),
    val isCashierStage: Boolean = false
)

/**
 * 支付或外卖履约完成确认信号（供 CheckoutSessionTracker 与前置收银台上下文合成记账草稿）。
 */
data class CheckoutCompletionSignal(
    val pageKind: ScreenPageKind,
    val merchantHint: String = "",
    val goodsHint: String = "",
    val orderNo: String = "",
    val isTakeout: Boolean = false,
    val confirmedAmountMicros: Long? = null,
    val summaryText: String = "",
    val isStrongPaidSignal: Boolean = true
)

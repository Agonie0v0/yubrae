package com.yubrae.app.capture.screen

import com.yubrae.app.core.contract.ParsedBillDraft

enum class ScreenPageKind { PAY_SUCCESS, BILL_DETAIL, ORDER_DETAIL, UNKNOWN }

data class ScreenTextNode(
    val text: String,
    val topPx: Int = -1,
    val heightPx: Int = 0,
    val isContentDesc: Boolean = false
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

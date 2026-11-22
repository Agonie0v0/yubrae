package com.kairoledger.app.capture.screen

import com.kairoledger.app.core.contract.ParsedBillDraft

enum class ScreenPageKind { PAY_SUCCESS, BILL_DETAIL, UNKNOWN }

data class ScreenSnapshot(
    val packageName: String,
    val className: String,
    val texts: List<String>,
    val capturedAtMillis: Long = System.currentTimeMillis()
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

package com.yubrae.app.capture

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.yubrae.app.core.contract.IntentConstants
import com.yubrae.app.data.db.CaptureChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 外部自动记账协议广播接收器（兼容 AutoAccounting / Tasker / 快捷指令）：
 * 监听 IntentConstants.ACTION_ADD_RECORD，支持 money, type, category, account, remark, time, currency。
 */
class ExternalBillReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null || intent.action != IntentConstants.ACTION_ADD_RECORD) return
        val params = ExternalBillProtocolParser.fromIntent(intent) ?: return

        val appContext = context.applicationContext
        val pendingResult = goAsync()

        receiverScope.launch {
            try {
                withTimeoutOrNull(8_000L) {
                    val orchestrator = CaptureRuntimeHelper.orchestratorOrNull(appContext)
                        ?: return@withTimeoutOrNull
                    orchestrator.handleExternalBillRecord(
                        channel = CaptureChannel.AUTO_ACCOUNTING,
                        sourcePackage = intent.`package` ?: "broadcast",
                        type = params.type,
                        origAmountMicros = params.origAmountMicros,
                        origCurrency = params.currency,
                        accountNameOrHint = params.accountNameOrHint,
                        categoryName = params.categoryName,
                        merchant = params.merchant,
                        note = params.note,
                        timestamp = params.timestamp,
                        showFloatingPrompt = params.showFloatingPrompt
                    )
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}

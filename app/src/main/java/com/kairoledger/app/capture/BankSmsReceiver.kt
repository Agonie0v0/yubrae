package com.kairoledger.app.capture

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.kairoledger.app.core.contract.CaptureInput
import com.kairoledger.app.data.db.CaptureChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 银行/境外卡动账短信采集广播接收器（支持多段长短信拼接 + goAsync IO 协程投递）。
 */
class BankSmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null || intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = runCatching { Telephony.Sms.Intents.getMessagesFromIntent(intent) }.getOrNull()
        if (messages.isNullOrEmpty()) return

        // 将同一发件人的多段长短信按序合并为完整报文
        val groupedBodies = linkedMapOf<String, StringBuilder>()
        var latestTimestamp = 0L
        for (sms in messages) {
            val sender = sms.displayOriginatingAddress ?: sms.originatingAddress ?: "SMS"
            val bodyPart = sms.displayMessageBody ?: sms.messageBody ?: ""
            if (bodyPart.isNotEmpty()) {
                groupedBodies.getOrPut(sender) { StringBuilder() }.append(bodyPart)
            }
            if (sms.timestampMillis > latestTimestamp) {
                latestTimestamp = sms.timestampMillis
            }
        }
        if (groupedBodies.isEmpty()) return

        val appContext = context.applicationContext
        val captureTimestamp = latestTimestamp.takeIf { it > 0L } ?: System.currentTimeMillis()
        val pendingResult = goAsync()

        receiverScope.launch {
            try {
                withTimeoutOrNull(8_500L) {
                    val container = CaptureRuntimeHelper.containerOrNull(appContext) ?: return@withTimeoutOrNull
                    val settings = runCatching { container.settingsStore.getSnapshot() }.getOrNull()
                    if (settings != null && !settings.smsCaptureEnabled) return@withTimeoutOrNull

                    val engine = container.captureEngine ?: return@withTimeoutOrNull
                    for ((sender, bodyBuilder) in groupedBodies) {
                        val content = bodyBuilder.toString().trim()
                        if (content.isEmpty()) continue
                        engine.handleIncomingCapture(
                            input = CaptureInput(
                                channel = CaptureChannel.SMS,
                                sourcePackage = sender,
                                title = "短信 ($sender)",
                                content = content,
                                timestamp = captureTimestamp
                            ),
                            showFloatingPrompt = true
                        )
                    }
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

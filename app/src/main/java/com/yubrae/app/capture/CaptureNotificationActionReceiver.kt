package com.yubrae.app.capture

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 处理降级确认通知上的「确认」与「删除」按钮动作。
 */
class CaptureNotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return
        val action = intent.action ?: return
        val txId = intent.getLongExtra(EXTRA_TRANSACTION_ID, -1L)
        val notifId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
        if (txId <= 0L) return

        val appContext = context.applicationContext
        if (notifId > 0) {
            runCatching { NotificationManagerCompat.from(appContext).cancel(notifId) }
        }

        val pendingResult = goAsync()
        receiverScope.launch {
            try {
                val ledgerService = CaptureRuntimeHelper.ledgerServiceOrNull(appContext)
                    ?: return@launch
                when (action) {
                    ACTION_CONFIRM_TX -> ledgerService.confirmTransaction(txId)
                    ACTION_DELETE_TX -> ledgerService.deleteTransaction(txId)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_CONFIRM_TX = "com.yubrae.app.capture.ACTION_CONFIRM_TX"
        const val ACTION_DELETE_TX = "com.yubrae.app.capture.ACTION_DELETE_TX"
        const val EXTRA_TRANSACTION_ID = "extra_tx_id"
        const val EXTRA_NOTIFICATION_ID = "extra_notif_id"

        private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}

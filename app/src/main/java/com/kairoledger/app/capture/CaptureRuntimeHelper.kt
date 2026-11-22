package com.kairoledger.app.capture

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.kairoledger.app.AppContainer
import com.kairoledger.app.KairoApplication
import com.kairoledger.app.core.contract.CaptureEngine
import com.kairoledger.app.core.contract.LedgerService
import com.kairoledger.app.data.prefs.SettingsStore

internal object CaptureRuntimeHelper {
    const val CHANNEL_CAPTURE_CONFIRM = "kairo_capture_confirm"
    const val CHANNEL_CAPTURE_FGS = "kairo_capture_fgs"

    fun containerOrNull(context: Context): AppContainer? {
        val app = context.applicationContext as? KairoApplication ?: return null
        return runCatching { app.container }.getOrNull()
    }

    fun captureEngineOrNull(context: Context): CaptureEngine? =
        containerOrNull(context)?.captureEngine

    fun orchestratorOrNull(context: Context): CaptureOrchestrator? =
        captureEngineOrNull(context) as? CaptureOrchestrator

    fun ledgerServiceOrNull(context: Context): LedgerService? =
        containerOrNull(context)?.ledgerService

    fun settingsStoreOrNull(context: Context): SettingsStore? =
        containerOrNull(context)?.settingsStore

    fun ensureNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        if (nm.getNotificationChannel(CHANNEL_CAPTURE_CONFIRM) == null) {
            val confirmChannel = NotificationChannel(
                CHANNEL_CAPTURE_CONFIRM,
                "自动记账确认提醒",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "无悬浮窗权限或后台限制时，用于快捷确认或修改自动识别的账单"
                enableVibration(true)
            }
            nm.createNotificationChannel(confirmChannel)
        }

        if (nm.getNotificationChannel(CHANNEL_CAPTURE_FGS) == null) {
            val fgsChannel = NotificationChannel(
                CHANNEL_CAPTURE_FGS,
                "悬浮确认弹窗服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "展示自动记账悬浮确认卡片时的轻量前台服务通知"
                setShowBadge(false)
            }
            nm.createNotificationChannel(fgsChannel)
        }
    }
}

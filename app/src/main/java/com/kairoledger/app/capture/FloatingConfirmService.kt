package com.kairoledger.app.capture

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.kairoledger.app.MainActivity
import com.kairoledger.app.R
import com.kairoledger.app.core.contract.IntentConstants
import com.kairoledger.app.core.contract.ParsedBillDraft
import com.kairoledger.app.core.model.MoneyAmount
import com.kairoledger.app.data.db.TransactionType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 悬浮窗自动记账确认卡片服务：
 * - 拥有悬浮窗权限时弹出顶部圆角确认卡片，支持倒计时自动确认、一键编辑（跳 MainActivity）、立即确认与删除。
 * - 无悬浮窗权限或后台启动受限时，自动优雅降级为带「确认 / 编辑 / 删除」动作按钮的系统通知，绝不崩溃。
 */
class FloatingConfirmService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var countdownJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        CaptureRuntimeHelper.ensureNotificationChannels(this)

        val asForeground = intent?.getBooleanExtra(EXTRA_AS_FOREGROUND, false) == true
        if (asForeground) {
            startSafeForeground()
        }

        val txId = intent?.getLongExtra(EXTRA_TX_ID, -1L) ?: -1L
        if (txId <= 0L) {
            stopSelfCleanly()
            return START_NOT_STICKY
        }

        val payload = OverlayPayload(
            transactionId = txId,
            type = intent?.getStringExtra(EXTRA_TYPE) ?: TransactionType.EXPENSE,
            origAmountMicros = intent?.getLongExtra(EXTRA_AMOUNT_MICROS, 0L) ?: 0L,
            origCurrency = intent?.getStringExtra(EXTRA_CURRENCY) ?: "CNY",
            merchant = intent?.getStringExtra(EXTRA_MERCHANT).orEmpty(),
            categoryName = intent?.getStringExtra(EXTRA_CATEGORY).orEmpty(),
            accountName = intent?.getStringExtra(EXTRA_ACCOUNT_NAME).orEmpty(),
            parserUsed = intent?.getStringExtra(EXTRA_PARSER_USED).orEmpty(),
            autoConfirmSeconds = intent?.getIntExtra(EXTRA_AUTO_CONFIRM_SECONDS, 8) ?: 8,
            accountAmountMicros = intent?.getLongExtra(EXTRA_ACCOUNT_AMOUNT_MICROS, 0L) ?: 0L,
            accountCurrency = intent?.getStringExtra(EXTRA_ACCOUNT_CURRENCY).orEmpty(),
            sourceBadge = intent?.getStringExtra(EXTRA_SOURCE_BADGE).orEmpty()
        )

        if (!Settings.canDrawOverlays(this)) {
            postFallbackNotification(this, payload)
            stopSelfCleanly()
            return START_NOT_STICKY
        }

        val shown = showOverlayCard(payload)
        if (!shown) {
            postFallbackNotification(this, payload)
            stopSelfCleanly()
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        countdownJob?.cancel()
        removeOverlayView()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startSafeForeground() {
        val notif = NotificationCompat.Builder(this, CaptureRuntimeHelper.CHANNEL_CAPTURE_FGS)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("棱账自动记账")
            .setContentText("正在显示记账确认悬浮卡片")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(
                    this,
                    NOTIF_ID_FGS,
                    notif,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(NOTIF_ID_FGS, notif)
            }
        }
    }

    private fun showOverlayCard(payload: OverlayPayload): Boolean {
        removeOverlayView()
        val wm = getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return false
        windowManager = wm

        val isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

        val cardBgColor = if (isDark) Color.parseColor("#1E242B") else Color.parseColor("#FFFFFF")
        val cardStrokeColor = if (isDark) Color.parseColor("#334155") else Color.parseColor("#E2E8F0")
        val textPrimary = if (isDark) Color.parseColor("#F8FAFC") else Color.parseColor("#0F172A")
        val textSecondary = if (isDark) Color.parseColor("#94A3B8") else Color.parseColor("#475569")
        val chipBg = if (isDark) Color.parseColor("#0F172A") else Color.parseColor("#F1F5F9")
        val primaryColor = Color.parseColor("#0E7490")
        val dangerColor = if (isDark) Color.parseColor("#F87171") else Color.parseColor("#DC2626")
        val amountColor = when (payload.type) {
            TransactionType.INCOME -> if (isDark) Color.parseColor("#4ADE80") else Color.parseColor("#16A34A")
            TransactionType.TRANSFER -> if (isDark) Color.parseColor("#60A5FA") else Color.parseColor("#2563EB")
            else -> dangerColor
        }

        val rootCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = GradientDrawable().apply {
                setColor(cardBgColor)
                cornerRadius = dp(20).toFloat()
                setStroke(dp(1), cardStrokeColor)
            }
            elevation = dp(12).toFloat()
        }

        // 顶部栏：品牌标题 + 倒计时提示
        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val cleanBadge = payload.sourceBadge.trim()
        val titleView = TextView(this).apply {
            text = if (cleanBadge.isNotEmpty()) "棱账 · $cleanBadge" else "棱账 · 自动记账"
            setTextColor(textSecondary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val countdownView = TextView(this).apply {
            setTextColor(primaryColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setPadding(dp(8), dp(3), dp(8), dp(3))
            background = GradientDrawable().apply {
                setColor(chipBg)
                cornerRadius = dp(10).toFloat()
            }
            text = if (payload.autoConfirmSeconds > 0) "${payload.autoConfirmSeconds}s 自动确认" else "待确认"
        }
        headerRow.addView(titleView)
        headerRow.addView(countdownView)
        rootCard.addView(headerRow)

        // 金额行
        val signPrefix = when (payload.type) {
            TransactionType.INCOME -> "+"
            TransactionType.TRANSFER -> ""
            else -> "-"
        }
        val formattedAmount = signPrefix + MoneyAmount.format(
            amountMicros = payload.origAmountMicros,
            currencyCode = payload.origCurrency,
            showSign = false,
            showCurrencyCode = true
        )
        val convertedAccountText = formatConvertedAccountHint(payload)
        val amountView = TextView(this).apply {
            text = formattedAmount
            setTextColor(amountColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(6), 0, if (convertedAccountText.isNotEmpty()) dp(2) else dp(4))
        }
        rootCard.addView(amountView)

        if (convertedAccountText.isNotEmpty()) {
            val convertedView = TextView(this).apply {
                text = convertedAccountText
                setTextColor(textSecondary)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setPadding(0, 0, 0, dp(4))
            }
            rootCard.addView(convertedView)
        }

        // 摘要详情行：商户 · 分类 · 账户
        val metaParts = listOfNotNull(
            payload.merchant.takeIf { it.isNotBlank() },
            payload.categoryName.takeIf { it.isNotBlank() },
            payload.accountName.takeIf { it.isNotBlank() }
        )
        val metaView = TextView(this).apply {
            text = metaParts.joinToString(" · ").ifBlank { "点击确认或编辑账单详情" }
            setTextColor(textPrimary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            setPadding(0, 0, 0, dp(12))
        }
        rootCard.addView(metaView)

        // 底部动作按钮行：删除 | 编辑 | 确认
        val actionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }

        val deleteBtn = buildActionButton(
            label = "删除",
            textColor = dangerColor,
            bgColor = chipBg,
            strokeColor = Color.TRANSPARENT
        ) {
            performActionAndDismiss(payload.transactionId, confirm = false)
        }
        val editBtn = buildActionButton(
            label = "编辑",
            textColor = textPrimary,
            bgColor = Color.TRANSPARENT,
            strokeColor = cardStrokeColor
        ) {
            countdownJob?.cancel()
            val editIntent = Intent(this, MainActivity::class.java).apply {
                putExtra(IntentConstants.EXTRA_EDIT_TRANSACTION_ID, payload.transactionId)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            runCatching { startActivity(editIntent) }
            stopSelfCleanly()
        }
        val confirmBtn = buildActionButton(
            label = "确认",
            textColor = Color.WHITE,
            bgColor = primaryColor,
            strokeColor = Color.TRANSPARENT
        ) {
            performActionAndDismiss(payload.transactionId, confirm = true)
        }

        val btnParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginEnd = dp(8)
        }
        val lastBtnParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        actionRow.addView(deleteBtn, btnParams)
        actionRow.addView(editBtn, btnParams)
        actionRow.addView(confirmBtn, lastBtnParams)
        rootCard.addView(actionRow)

        val wrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(8))
            addView(
                rootCard,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )
        }

        val widthPx = (resources.displayMetrics.widthPixels * 0.94f).toInt()
        val layoutParams = WindowManager.LayoutParams(
            widthPx,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dp(42)
        }

        val attached = runCatching {
            wm.addView(wrapper, layoutParams)
        }.isSuccess

        if (!attached) {
            return false
        }

        overlayView = wrapper
        startCountdown(payload.transactionId, payload.autoConfirmSeconds, countdownView)
        return true
    }

    private fun startCountdown(txId: Long, seconds: Int, countdownView: TextView) {
        countdownJob?.cancel()
        if (seconds <= 0) return

        countdownJob = serviceScope.launch {
            for (remaining in seconds downTo 1) {
                countdownView.text = "${remaining}s 自动确认"
                delay(1_000L)
            }
            countdownView.text = "确认中..."
            withContext(Dispatchers.IO) {
                CaptureRuntimeHelper.ledgerServiceOrNull(applicationContext)?.confirmTransaction(txId)
            }
            stopSelfCleanly()
        }
    }

    private fun performActionAndDismiss(txId: Long, confirm: Boolean) {
        countdownJob?.cancel()
        serviceScope.launch {
            withContext(Dispatchers.IO) {
                val ledgerService = CaptureRuntimeHelper.ledgerServiceOrNull(applicationContext)
                if (confirm) {
                    ledgerService?.confirmTransaction(txId)
                } else {
                    ledgerService?.deleteTransaction(txId)
                }
            }
            stopSelfCleanly()
        }
    }

    private fun buildActionButton(
        label: String,
        textColor: Int,
        bgColor: Int,
        strokeColor: Int,
        onClick: () -> Unit
    ): TextView {
        return TextView(this).apply {
            text = label
            setTextColor(textColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(9), dp(10), dp(9))
            background = GradientDrawable().apply {
                setColor(bgColor)
                cornerRadius = dp(12).toFloat()
                if (strokeColor != Color.TRANSPARENT) {
                    setStroke(dp(1), strokeColor)
                }
            }
            setOnClickListener { onClick() }
        }
    }

    private fun removeOverlayView() {
        val view = overlayView ?: return
        runCatching { windowManager?.removeViewImmediate(view) }
        overlayView = null
    }

    private fun stopSelfCleanly() {
        removeOverlayView()
        runCatching { ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE) }
        stopSelf()
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics
        ).toInt()

    data class OverlayPayload(
        val transactionId: Long,
        val type: String,
        val origAmountMicros: Long,
        val origCurrency: String,
        val merchant: String,
        val categoryName: String,
        val accountName: String,
        val parserUsed: String,
        val autoConfirmSeconds: Int,
        val accountAmountMicros: Long = 0L,
        val accountCurrency: String = "",
        val sourceBadge: String = ""
    )

    companion object {
        private const val EXTRA_TX_ID = "extra_tx_id"
        private const val EXTRA_TYPE = "extra_type"
        private const val EXTRA_AMOUNT_MICROS = "extra_amount_micros"
        private const val EXTRA_CURRENCY = "extra_currency"
        private const val EXTRA_MERCHANT = "extra_merchant"
        private const val EXTRA_CATEGORY = "extra_category"
        private const val EXTRA_ACCOUNT_NAME = "extra_account_name"
        private const val EXTRA_PARSER_USED = "extra_parser_used"
        private const val EXTRA_AUTO_CONFIRM_SECONDS = "extra_auto_confirm_seconds"
        private const val EXTRA_ACCOUNT_AMOUNT_MICROS = "extra_account_amount_micros"
        private const val EXTRA_ACCOUNT_CURRENCY = "extra_account_currency"
        private const val EXTRA_SOURCE_BADGE = "extra_source_badge"
        private const val EXTRA_AS_FOREGROUND = "extra_as_foreground"

        private const val NOTIF_ID_FGS = 9001
        private const val NOTIF_ID_CONFIRM_BASE = 9100

        private fun formatConvertedAccountHint(payload: OverlayPayload): String {
            val accCur = payload.accountCurrency.trim()
            if (accCur.isBlank() ||
                payload.origCurrency.equals(accCur, ignoreCase = true) ||
                payload.accountAmountMicros <= 0L
            ) {
                return ""
            }
            return "≈ 账户实扣 ${MoneyAmount.format(payload.accountAmountMicros, accCur, showCurrencyCode = true)}"
        }

        fun showOrNotify(
            context: Context,
            transactionId: Long,
            draft: ParsedBillDraft,
            accountName: String = "",
            autoConfirmSeconds: Int = 8,
            accountAmountMicros: Long = 0L,
            accountCurrency: String = "",
            sourceBadge: String = ""
        ) {
            val appContext = context.applicationContext
            CaptureRuntimeHelper.ensureNotificationChannels(appContext)

            val payload = OverlayPayload(
                transactionId = transactionId,
                type = draft.type,
                origAmountMicros = draft.origAmountMicros,
                origCurrency = draft.origCurrency,
                merchant = draft.merchant,
                categoryName = draft.categoryName,
                accountName = accountName,
                parserUsed = draft.parserUsed,
                autoConfirmSeconds = autoConfirmSeconds,
                accountAmountMicros = accountAmountMicros,
                accountCurrency = accountCurrency,
                sourceBadge = sourceBadge
            )

            if (!Settings.canDrawOverlays(appContext)) {
                postFallbackNotification(appContext, payload)
                return
            }

            val intent = Intent(appContext, FloatingConfirmService::class.java).apply {
                putExtra(EXTRA_TX_ID, payload.transactionId)
                putExtra(EXTRA_TYPE, payload.type)
                putExtra(EXTRA_AMOUNT_MICROS, payload.origAmountMicros)
                putExtra(EXTRA_CURRENCY, payload.origCurrency)
                putExtra(EXTRA_MERCHANT, payload.merchant)
                putExtra(EXTRA_CATEGORY, payload.categoryName)
                putExtra(EXTRA_ACCOUNT_NAME, payload.accountName)
                putExtra(EXTRA_PARSER_USED, payload.parserUsed)
                putExtra(EXTRA_AUTO_CONFIRM_SECONDS, payload.autoConfirmSeconds)
                putExtra(EXTRA_ACCOUNT_AMOUNT_MICROS, payload.accountAmountMicros)
                putExtra(EXTRA_ACCOUNT_CURRENCY, payload.accountCurrency)
                putExtra(EXTRA_SOURCE_BADGE, payload.sourceBadge)
            }

            val startedDirectly = runCatching {
                appContext.startService(intent)
            }.isSuccess

            if (!startedDirectly) {
                val startedForeground = runCatching {
                    intent.putExtra(EXTRA_AS_FOREGROUND, true)
                    ContextCompat.startForegroundService(appContext, intent)
                }.isSuccess

                if (!startedForeground) {
                    postFallbackNotification(appContext, payload)
                }
            }
        }

        @SuppressLint("MissingPermission")
        fun postFallbackNotification(context: Context, payload: OverlayPayload) {
            val appContext = context.applicationContext
            CaptureRuntimeHelper.ensureNotificationChannels(appContext)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val granted = ContextCompat.checkSelfPermission(
                    appContext,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
                if (!granted) return
            }

            val notifId = NOTIF_ID_CONFIRM_BASE + (payload.transactionId % 10_000L).toInt()
            val signPrefix = when (payload.type) {
                TransactionType.INCOME -> "+"
                TransactionType.TRANSFER -> ""
                else -> "-"
            }
            val amountStr = signPrefix + MoneyAmount.format(
                amountMicros = payload.origAmountMicros,
                currencyCode = payload.origCurrency,
                showCurrencyCode = true
            )
            val convertedHint = formatConvertedAccountHint(payload)
            val detail = listOfNotNull(
                convertedHint.takeIf { it.isNotBlank() },
                payload.merchant.takeIf { it.isNotBlank() },
                payload.categoryName.takeIf { it.isNotBlank() },
                payload.accountName.takeIf { it.isNotBlank() }
            ).joinToString(" · ").ifBlank { "请核对自动识别账单" }
            val cleanBadge = payload.sourceBadge.trim()
            val notifTitle = if (cleanBadge.isNotEmpty()) {
                "棱账 · $cleanBadge：$amountStr"
            } else {
                "棱账自动记账：$amountStr"
            }

            val editIntent = Intent(appContext, MainActivity::class.java).apply {
                putExtra(IntentConstants.EXTRA_EDIT_TRANSACTION_ID, payload.transactionId)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            val editPendingIntent = PendingIntent.getActivity(
                appContext,
                notifId * 10 + 1,
                editIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val confirmIntent = Intent(appContext, CaptureNotificationActionReceiver::class.java).apply {
                action = CaptureNotificationActionReceiver.ACTION_CONFIRM_TX
                putExtra(CaptureNotificationActionReceiver.EXTRA_TRANSACTION_ID, payload.transactionId)
                putExtra(CaptureNotificationActionReceiver.EXTRA_NOTIFICATION_ID, notifId)
            }
            val confirmPendingIntent = PendingIntent.getBroadcast(
                appContext,
                notifId * 10 + 2,
                confirmIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val deleteIntent = Intent(appContext, CaptureNotificationActionReceiver::class.java).apply {
                action = CaptureNotificationActionReceiver.ACTION_DELETE_TX
                putExtra(CaptureNotificationActionReceiver.EXTRA_TRANSACTION_ID, payload.transactionId)
                putExtra(CaptureNotificationActionReceiver.EXTRA_NOTIFICATION_ID, notifId)
            }
            val deletePendingIntent = PendingIntent.getBroadcast(
                appContext,
                notifId * 10 + 3,
                deleteIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification: Notification = NotificationCompat.Builder(
                appContext,
                CaptureRuntimeHelper.CHANNEL_CAPTURE_CONFIRM
            )
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(notifTitle)
                .setContentText(detail)
                .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(editPendingIntent)
                .addAction(0, "确认", confirmPendingIntent)
                .addAction(0, "编辑", editPendingIntent)
                .addAction(0, "删除", deletePendingIntent)
                .build()

            runCatching {
                NotificationManagerCompat.from(appContext).notify(notifId, notification)
            }
        }
    }
}

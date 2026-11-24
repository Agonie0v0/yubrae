package com.yubrae.app.capture

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
import android.text.Editable
import android.text.InputType
import android.text.TextUtils
import android.text.TextWatcher
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.yubrae.app.BuildConfig
import com.yubrae.app.MainActivity
import com.yubrae.app.R
import com.yubrae.app.core.contract.IntentConstants
import com.yubrae.app.core.contract.ParsedBillDraft
import com.yubrae.app.core.contract.RecordTransactionRequest
import com.yubrae.app.core.model.CurrencyCatalog
import com.yubrae.app.core.model.MoneyAmount
import com.yubrae.app.data.db.AccountEntity
import com.yubrae.app.data.db.CaptureChannel
import com.yubrae.app.data.db.CategoryEntity
import com.yubrae.app.data.db.LedgerEntity
import com.yubrae.app.data.db.ReimburseStatus
import com.yubrae.app.data.db.TagEntity
import com.yubrae.app.data.db.TagTextCodec
import com.yubrae.app.data.db.TransactionEntity
import com.yubrae.app.data.db.TransactionStatus
import com.yubrae.app.data.db.TransactionType
import com.yubrae.app.data.prefs.AppSettings
import com.yubrae.app.data.prefs.ThemeMode
import com.yubrae.app.ui.theme.resolveOverlayThemeColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 悬浮窗自动记账确认卡片服务（薄荷清新风可交互面板）：
 * - 支持精简确认态与完整交互编辑态（自由切换），无需退出微信/支付宝即可修改收支类型、币种、账本、分类、账户、商户/备注、报销与不计收支；
 * - 任意用户点击/滚动/输入交互立即取消自动确认倒计时；
 * - 默认携带 FLAG_NOT_FOCUSABLE 不抢微信/支付宝焦点，点击商户/备注框时动态更新 WindowManager 布局弹出软键盘，完成或失焦自动恢复；
 * - 无悬浮窗权限或后台受限时自动优雅降级为系统通知。
 */
class FloatingConfirmService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var windowManager: WindowManager? = null
    private var overlayWrapper: OverlayWrapperLayout? = null
    private var overlayLayoutParams: WindowManager.LayoutParams? = null
    private var loadJob: Job? = null
    private var countdownJob: Job? = null
    private var previewJob: Job? = null

    private var countdownPaused: Boolean = false
    private var windowFocusableForIme: Boolean = false
    private var activeCountdownView: TextView? = null

    private data class EditorState(
        val txId: Long,
        var type: String,
        var origAmountMicros: Long,
        var origCurrency: String,
        var accountId: Long,
        var toAccountId: Long?,
        var categoryId: Long?,
        var ledgerId: Long,
        var merchant: String,
        var note: String,
        var reimburseStatus: String,
        var excludeFromStats: Boolean,
        val excludeFromBudget: Boolean,
        val timestamp: Long,
        val sourceType: String,
        val rawEventId: Long?,
        var tags: String,
        val attachmentPaths: String,
        val refundOfTxId: Long?,
        val reimbursedTxId: Long?,
        var preserveInitialRate: Boolean,
        val initialAccountAmountMicros: Long,
        val initialAccountCurrency: String,
        val initialRateToAccountMicros: Long?,
        var previewAccountAmountMicros: Long,
        var previewAccountCurrency: String,
        var previewRateToAccountMicros: Long,
        var isExpanded: Boolean
    )

    private data class OverlayTheme(
        val isDark: Boolean,
        val cardBg: Int,
        val cardStroke: Int,
        val textPrimary: Int,
        val textSecondary: Int,
        val chipBg: Int,
        val mintAccent: Int,
        val mintContainer: Int,
        val onMintContainer: Int,
        val expenseColor: Int,
        val incomeColor: Int,
        val transferColor: Int,
        val inputBg: Int,
        val warningBg: Int,
        val warningText: Int
    )

    private class MaxHeightScrollView(
        context: Context,
        private val maxHeightPx: Int
    ) : ScrollView(context) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val boundedSpec = MeasureSpec.makeMeasureSpec(maxHeightPx, MeasureSpec.AT_MOST)
            super.onMeasure(widthMeasureSpec, boundedSpec)
        }
    }

    private class OverlayWrapperLayout(
        context: Context,
        private val onOutsideTouch: () -> Unit,
        private val onBackPressedWhileFocusable: () -> Boolean,
        private val onWindowFocusLost: () -> Unit
    ) : LinearLayout(context) {

        override fun onTouchEvent(event: MotionEvent?): Boolean {
            if (event?.action == MotionEvent.ACTION_OUTSIDE) {
                onOutsideTouch()
            }
            return super.onTouchEvent(event)
        }

        override fun dispatchKeyEvent(event: KeyEvent?): Boolean {
            if (event?.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                if (onBackPressedWhileFocusable()) {
                    return true
                }
            }
            return super.dispatchKeyEvent(event)
        }

        override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
            super.onWindowFocusChanged(hasWindowFocus)
            if (!hasWindowFocus) {
                onWindowFocusLost()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        CaptureRuntimeHelper.ensureNotificationChannels(this)

        val asForeground = intent?.getBooleanExtra(EXTRA_AS_FOREGROUND, false) == true
        if (asForeground) {
            startSafeForeground()
        }

        val txId = intent?.getLongExtra(EXTRA_TX_ID, -1L) ?: -1L
        if (txId <= 0L) {
            debugWarn("onStartCommand: invalid txId=$txId, stopSelf")
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
            debugWarn("onStartCommand: overlay permission missing, fallback notif tx=$txId")
            postFallbackNotification(this, payload)
            stopSelfCleanly()
            return START_NOT_STICKY
        }

        loadJob?.cancel()
        countdownJob?.cancel()
        previewJob?.cancel()

        loadJob = serviceScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                val container = CaptureRuntimeHelper.containerOrNull(applicationContext)
                val settings = runCatching { container?.settingsStore?.getSnapshot() }.getOrNull() ?: AppSettings()
                val db = container?.database
                val tx = runCatching { db?.transactionDao()?.getById(txId) }.getOrNull()
                val allAccounts = runCatching { db?.accountDao()?.getAll() }.getOrNull().orEmpty()
                val activeAccounts = allAccounts.filter { !it.archived }.ifEmpty { allAccounts }
                val categories = runCatching { db?.categoryDao()?.getAll() }.getOrNull().orEmpty()
                val allLedgers = runCatching { db?.ledgerDao()?.getAll() }.getOrNull().orEmpty()
                val activeLedgers = allLedgers.filter { !it.archived }.ifEmpty { allLedgers }
                val tags = runCatching { db?.tagDao()?.getAll() }.getOrNull().orEmpty()
                LoadedContext(settings, tx, activeAccounts, categories, activeLedgers, tags)
            }

            val shown = showInteractiveOverlay(payload, loaded)
            if (!shown) {
                debugWarn("onStartCommand: showInteractiveOverlay failed, fallback notif tx=$txId")
                postFallbackNotification(this@FloatingConfirmService, payload)
                stopSelfCleanly()
            } else {
                debugLog("onStartCommand: overlay shown tx=$txId rich=${loaded.settings.floatingRichEditorEnabled}")
            }
        }

        return START_NOT_STICKY
    }

    private data class LoadedContext(
        val settings: AppSettings,
        val tx: TransactionEntity?,
        val accounts: List<AccountEntity>,
        val categories: List<CategoryEntity>,
        val ledgers: List<LedgerEntity>,
        val tags: List<TagEntity>
    )

    override fun onDestroy() {
        loadJob?.cancel()
        previewJob?.cancel()
        countdownJob?.cancel()
        removeOverlayView()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startSafeForeground() {
        val notif = NotificationCompat.Builder(this, CaptureRuntimeHelper.CHANNEL_CAPTURE_FGS)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("余白自动记账")
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

    private fun resolveDark(themeMode: ThemeMode): Boolean = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
    }

    private fun buildTheme(settings: AppSettings): OverlayTheme {
        val resolved = resolveOverlayThemeColors(
            themeStyle = settings.themeStyle,
            isDark = resolveDark(settings.themeMode),
            oledPureBlack = settings.oledPureBlack,
            greenExpenseMode = settings.greenExpenseMode
        )
        return OverlayTheme(
            isDark = resolved.isDark,
            cardBg = resolved.cardBg,
            cardStroke = resolved.cardStroke,
            textPrimary = resolved.textPrimary,
            textSecondary = resolved.textSecondary,
            chipBg = resolved.chipBg,
            mintAccent = resolved.accent,
            mintContainer = resolved.accentContainer,
            onMintContainer = resolved.onAccentContainer,
            expenseColor = resolved.expenseColor,
            incomeColor = resolved.incomeColor,
            transferColor = resolved.transferColor,
            inputBg = resolved.inputBg,
            warningBg = resolved.warningBg,
            warningText = resolved.warningText
        )
    }

    private fun contrastOnAccent(accentColor: Int): Int =
        if (ColorUtils.calculateLuminance(accentColor) > 0.52) Color.BLACK else Color.WHITE

    private fun buildInitialState(payload: OverlayPayload, loaded: LoadedContext): EditorState {
        val tx = loaded.tx
        val rawType = tx?.type ?: payload.type
        val normalizedType = when (rawType) {
            TransactionType.INCOME -> TransactionType.INCOME
            TransactionType.TRANSFER -> TransactionType.TRANSFER
            else -> TransactionType.EXPENSE
        }
        val origAmount = tx?.origAmountMicros?.takeIf { it > 0L } ?: payload.origAmountMicros
        val origCur = (tx?.origCurrency ?: payload.origCurrency)
            .trim()
            .uppercase()
            .ifBlank { CurrencyCatalog.DEFAULT_BASE_CURRENCY }
        val resolvedAccountId = tx?.accountId?.takeIf { id -> loaded.accounts.any { it.id == id } }
            ?: loaded.accounts.firstOrNull { it.name == payload.accountName }?.id
            ?: loaded.accounts.firstOrNull()?.id
            ?: tx?.accountId
            ?: 0L
        val resolvedToAccountId = tx?.toAccountId?.takeIf { id -> loaded.accounts.any { it.id == id } && id != resolvedAccountId }
            ?: loaded.accounts.firstOrNull { it.id != resolvedAccountId }?.id
        val resolvedCategoryId = tx?.categoryId?.takeIf { id -> loaded.categories.any { it.id == id } }
            ?: loaded.categories.firstOrNull {
                it.type.equals(normalizedType, ignoreCase = true) &&
                    it.name.equals(payload.categoryName, ignoreCase = true)
            }?.id
            ?: loaded.categories.firstOrNull { it.type.equals(normalizedType, ignoreCase = true) }?.id
        val resolvedLedgerId = tx?.ledgerId?.takeIf { id -> loaded.ledgers.any { it.id == id } }
            ?: loaded.settings.activeLedgerId.takeIf { id -> loaded.ledgers.any { it.id == id } }
            ?: loaded.ledgers.firstOrNull()?.id
            ?: 1L

        val initialAccAmt = tx?.accountAmountMicros?.takeIf { it > 0L } ?: payload.accountAmountMicros
        val initialAccCur = tx?.accountCurrency?.takeIf { it.isNotBlank() }
            ?: payload.accountCurrency.ifBlank {
                loaded.accounts.firstOrNull { it.id == resolvedAccountId }?.currencyCode.orEmpty()
            }

        return EditorState(
            txId = payload.transactionId,
            type = normalizedType,
            origAmountMicros = origAmount,
            origCurrency = origCur,
            accountId = resolvedAccountId,
            toAccountId = resolvedToAccountId,
            categoryId = resolvedCategoryId,
            ledgerId = resolvedLedgerId,
            merchant = tx?.merchant ?: payload.merchant,
            note = tx?.note.orEmpty(),
            reimburseStatus = tx?.reimburseStatus ?: ReimburseStatus.NONE,
            excludeFromStats = tx?.excludeFromStats ?: false,
            excludeFromBudget = tx?.excludeFromBudget ?: false,
            timestamp = tx?.timestamp ?: System.currentTimeMillis(),
            sourceType = tx?.sourceType ?: CaptureChannel.AUTO_ACCOUNTING,
            rawEventId = tx?.rawEventId,
            tags = tx?.tags.orEmpty(),
            attachmentPaths = tx?.attachmentPaths.orEmpty(),
            refundOfTxId = tx?.refundOfTxId,
            reimbursedTxId = tx?.reimbursedTxId,
            preserveInitialRate = initialAccAmt > 0L && initialAccCur.isNotBlank(),
            initialAccountAmountMicros = initialAccAmt,
            initialAccountCurrency = initialAccCur,
            initialRateToAccountMicros = tx?.rateToAccountMicros?.takeIf { it > 0L },
            previewAccountAmountMicros = initialAccAmt,
            previewAccountCurrency = initialAccCur,
            previewRateToAccountMicros = tx?.rateToAccountMicros?.takeIf { it > 0L } ?: 1_000_000L,
            isExpanded = loaded.settings.floatingRichEditorEnabled
        )
    }

    private fun showInteractiveOverlay(payload: OverlayPayload, loaded: LoadedContext): Boolean {
        removeOverlayView()
        val wm = getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        if (wm == null) {
            debugWarn("showInteractiveOverlay: WindowManager is null tx=${payload.transactionId}")
            return false
        }
        windowManager = wm
        countdownPaused = false
        windowFocusableForIme = false

        val theme = buildTheme(loaded.settings)
        val state = buildInitialState(payload, loaded)

        val rootCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(11), dp(14), dp(11))
            background = GradientDrawable().apply {
                setColor(theme.cardBg)
                cornerRadius = dp(16).toFloat()
                setStroke(hairlineStrokePx(), withAlpha(theme.cardStroke, if (theme.isDark) 135 else 105))
            }
            elevation = dp(8).toFloat()
        }

        var clearTextInputAction: () -> Unit = {}

        val wrapper = OverlayWrapperLayout(
            context = this,
            onOutsideTouch = { clearTextInputAction() },
            onBackPressedWhileFocusable = {
                if (windowFocusableForIme) {
                    clearTextInputAction()
                    true
                } else {
                    false
                }
            },
            onWindowFocusLost = {
                if (windowFocusableForIme) {
                    clearTextInputAction()
                }
            }
        ).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(6), dp(12), dp(6))
            addView(
                rootCard,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )
        }

        fun rebuildCard() {
            clearTextInputAction = renderCardContent(
                rootCard = rootCard,
                theme = theme,
                payload = payload,
                loaded = loaded,
                state = state,
                onRebuild = { rebuildCard() }
            )
        }
        rebuildCard()

        val widthPx = (resources.displayMetrics.widthPixels * 0.94f).toInt()
        val layoutParams = WindowManager.LayoutParams(
            widthPx,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            DEFAULT_WINDOW_FLAGS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dp(32)
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED
        }

        val attachResult = runCatching { wm.addView(wrapper, layoutParams) }
        if (attachResult.isFailure) {
            debugWarn("showInteractiveOverlay: wm.addView failed tx=${payload.transactionId}: ${attachResult.exceptionOrNull()?.message}")
            return false
        }

        overlayWrapper = wrapper
        overlayLayoutParams = layoutParams

        startCountdownIfNeeded(payload.transactionId, payload.autoConfirmSeconds)
        return true
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun renderCardContent(
        rootCard: LinearLayout,
        theme: OverlayTheme,
        payload: OverlayPayload,
        loaded: LoadedContext,
        state: EditorState,
        onRebuild: () -> Unit
    ): () -> Unit {
        rootCard.removeAllViews()
        previewJob?.cancel()

        // 1. 顶部栏：品牌标题 + 倒计时胶囊 + 精简/展开切换
        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val cleanBadge = payload.sourceBadge.trim()
        val titleView = TextView(this).apply {
            text = if (cleanBadge.isNotEmpty()) "余白 · $cleanBadge" else "余白 · 自动记账"
            setTextColor(theme.textSecondary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val countdownView = TextView(this).apply {
            setTextColor(if (countdownPaused) theme.warningText else theme.mintAccent)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
            setPadding(dp(7), dp(2), dp(7), dp(2))
            background = GradientDrawable().apply {
                setColor(if (countdownPaused) theme.warningBg else theme.chipBg)
                cornerRadius = dp(8).toFloat()
            }
            text = when {
                countdownPaused -> "已暂停自动确认"
                payload.autoConfirmSeconds > 0 -> "${payload.autoConfirmSeconds}s 自动确认"
                else -> "待确认"
            }
        }
        activeCountdownView = countdownView
        headerRow.addView(titleView)
        headerRow.addView(countdownView)

        if (state.isExpanded) {
            val collapseBtn = TextView(this).apply {
                text = "切换精简"
                contentDescription = "切换精简"
                setTextColor(theme.textSecondary)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
                setPadding(dp(7), dp(2), dp(7), dp(2))
                background = GradientDrawable().apply {
                    setColor(theme.chipBg)
                    cornerRadius = dp(8).toFloat()
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginStart = dp(5) }
                setOnClickListener {
                    pauseCountdownForUserInteraction(theme)
                    runCatching {
                        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                        imm?.hideSoftInputFromWindow(rootCard.windowToken, 0)
                    }
                    setWindowFocusable(false)
                    state.isExpanded = false
                    onRebuild()
                }
            }
            headerRow.addView(collapseBtn)
        }
        rootCard.addView(headerRow)

        if (!state.isExpanded) {
            renderCompactBody(
                rootCard = rootCard,
                theme = theme,
                payload = payload,
                loaded = loaded,
                state = state,
                onExpand = {
                    pauseCountdownForUserInteraction(theme)
                    state.isExpanded = true
                    onRebuild()
                }
            )
            rootCard.setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_DOWN) {
                    pauseCountdownForUserInteraction(theme)
                }
                false
            }
            return { setWindowFocusable(false) }
        }

        // === 完整轻盈交互编辑态 ===
        // 2. 顶部 支出 | 收入 | 转账 分段切换
        val segmentTrack = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(2), dp(2), dp(2), dp(2))
            background = GradientDrawable().apply {
                setColor(theme.chipBg)
                cornerRadius = dp(10).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(7) }
        }

        val amountView = TextView(this)
        val amountEditHint = TextView(this)
        val amountEdit = buildOverlayEditText(
            hint = "输入金额",
            initialText = "",
            imeOptions = EditorInfo.IME_ACTION_DONE,
            theme = theme,
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL,
            textSizeSp = 18f
        ).apply {
            typeface = Typeface.DEFAULT_BOLD
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        }
        var isUpdatingAmountField = false
        val convertedView = TextView(this)
        val currencyRow = LinearLayout(this)
        val ledgerRow = LinearLayout(this)
        val categorySection = LinearLayout(this)
        val categoryRow = LinearLayout(this)
        val accountRow = LinearLayout(this)
        val toAccountSection = LinearLayout(this)
        val toAccountRow = LinearLayout(this)

        fun updateAmountAppearance() {
            val amountColor = when (state.type) {
                TransactionType.INCOME -> theme.incomeColor
                TransactionType.TRANSFER -> theme.transferColor
                else -> theme.expenseColor
            }
            val signPrefix = when (state.type) {
                TransactionType.INCOME -> "+"
                TransactionType.TRANSFER -> "⇄ "
                else -> "-"
            }
            amountView.text = signPrefix + MoneyAmount.format(
                amountMicros = state.origAmountMicros,
                currencyCode = state.origCurrency,
                showSign = false,
                showCurrencyCode = true
            )
            amountView.setTextColor(amountColor)
        }

        fun updateConversionHintDisplay() {
            val accCur = state.previewAccountCurrency.trim()
            val accAmt = state.previewAccountAmountMicros
            if (accCur.isBlank() ||
                state.origCurrency.equals(accCur, ignoreCase = true) ||
                accAmt <= 0L
            ) {
                convertedView.visibility = View.GONE
            } else {
                val labelPrefix = if (state.type == TransactionType.INCOME) "≈ 账户实入 " else "≈ 账户实扣 "
                val formatted = MoneyAmount.format(accAmt, accCur, showCurrencyCode = true)
                convertedView.text = "$labelPrefix$formatted"
                convertedView.visibility = View.VISIBLE
            }
        }

        fun triggerPreviewConversion() {
            if (state.accountId <= 0L) {
                updateConversionHintDisplay()
                return
            }
            val selectedAcc = loaded.accounts.firstOrNull { it.id == state.accountId }
            if (selectedAcc != null && state.origCurrency.equals(selectedAcc.currencyCode, ignoreCase = true)) {
                state.previewAccountAmountMicros = state.origAmountMicros
                state.previewAccountCurrency = selectedAcc.currencyCode
                state.previewRateToAccountMicros = 1_000_000L
                updateConversionHintDisplay()
                return
            }
            if (state.preserveInitialRate &&
                state.initialAccountAmountMicros > 0L &&
                selectedAcc != null &&
                state.initialAccountCurrency.equals(selectedAcc.currencyCode, ignoreCase = true)
            ) {
                state.previewAccountAmountMicros = state.initialAccountAmountMicros
                state.previewAccountCurrency = state.initialAccountCurrency
                updateConversionHintDisplay()
                return
            }

            previewJob?.cancel()
            val capturedOrigMicros = state.origAmountMicros
            val capturedOrigCurrency = state.origCurrency
            val capturedAccountId = state.accountId
            previewJob = serviceScope.launch {
                val preview = withContext(Dispatchers.IO) {
                    val ledgerService = CaptureRuntimeHelper.ledgerServiceOrNull(applicationContext)
                        ?: return@withContext null
                    runCatching {
                        ledgerService.previewConversion(
                            origAmountMicros = capturedOrigMicros,
                            origCurrency = capturedOrigCurrency,
                            accountId = capturedAccountId,
                            baseCurrency = loaded.settings.baseCurrency
                        )
                    }.getOrNull()
                }
                if (preview != null &&
                    state.origAmountMicros == capturedOrigMicros &&
                    state.origCurrency == capturedOrigCurrency &&
                    state.accountId == capturedAccountId
                ) {
                    state.previewAccountAmountMicros = preview.accountAmountMicros
                    state.previewAccountCurrency = preview.accountCurrency
                    state.previewRateToAccountMicros = preview.rateToAccountMicros
                    updateConversionHintDisplay()
                }
            }
        }

        fun refreshTypeSegments() {
            segmentTrack.removeAllViews()
            val types = listOf(
                TransactionType.EXPENSE to "支出",
                TransactionType.INCOME to "收入",
                TransactionType.TRANSFER to "转账"
            )
            for ((typeKey, typeLabel) in types) {
                val selected = state.type == typeKey
                val btn = TextView(this).apply {
                    text = typeLabel
                    gravity = Gravity.CENTER
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
                    typeface = if (selected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                    setTextColor(if (selected) theme.onMintContainer else theme.textSecondary)
                    setPadding(dp(6), dp(5), dp(6), dp(5))
                    background = GradientDrawable().apply {
                        setColor(if (selected) theme.mintContainer else Color.TRANSPARENT)
                        cornerRadius = dp(8).toFloat()
                    }
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    setOnClickListener {
                        pauseCountdownForUserInteraction(theme)
                        if (typeKey == TransactionType.TRANSFER && loaded.accounts.size < 2) {
                            Toast.makeText(applicationContext, "至少需要两个账户才能记录转账", Toast.LENGTH_SHORT).show()
                            return@setOnClickListener
                        }
                        if (state.type != typeKey) {
                            state.type = typeKey
                            if (typeKey != TransactionType.TRANSFER) {
                                val validCat = loaded.categories.firstOrNull {
                                    it.id == state.categoryId && it.type.equals(typeKey, ignoreCase = true)
                                }
                                if (validCat == null) {
                                    state.categoryId = loaded.categories
                                        .firstOrNull { it.type.equals(typeKey, ignoreCase = true) }
                                        ?.id
                                }
                            } else {
                                if (state.toAccountId == null || state.toAccountId == state.accountId) {
                                    state.toAccountId = loaded.accounts.firstOrNull { it.id != state.accountId }?.id
                                }
                            }
                            refreshTypeSegments()
                            updateAmountAppearance()
                            updateConversionHintDisplay()
                            // 重绘分类与转入账户区
                            categorySection.visibility = if (state.type == TransactionType.TRANSFER) View.GONE else View.VISIBLE
                            toAccountSection.visibility = if (state.type == TransactionType.TRANSFER) View.VISIBLE else View.GONE
                            rebuildCategoryChips(categoryRow, theme, loaded.categories, state) {
                                pauseCountdownForUserInteraction(theme)
                            }
                            rebuildToAccountChips(toAccountRow, theme, loaded.accounts, state) {
                                pauseCountdownForUserInteraction(theme)
                            }
                        }
                    }
                }
                segmentTrack.addView(btn)
            }
        }
        refreshTypeSegments()
        rootCard.addView(segmentTrack)

        // 3. 轻盈金额显示（支持点击二次编辑）+ 币种快速切换胶囊 + 实时换算显示
        var beginAmountEdit: () -> Unit = {}
        val amountRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(6), 0, dp(1))
        }
        amountView.apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 23f)
            typeface = Typeface.DEFAULT_BOLD
            setOnClickListener { beginAmountEdit() }
        }
        amountEditHint.apply {
            text = "改金额"
            setTextColor(theme.textSecondary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            setPadding(dp(7), dp(2), dp(7), dp(2))
            background = GradientDrawable().apply {
                setColor(theme.chipBg)
                cornerRadius = dp(8).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = dp(8) }
            setOnClickListener { beginAmountEdit() }
        }
        updateAmountAppearance()
        amountRow.addView(amountView)
        amountRow.addView(amountEditHint)
        amountRow.addView(amountEdit)
        rootCard.addView(amountRow)

        convertedView.apply {
            setTextColor(theme.textSecondary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setPadding(0, 0, 0, dp(2))
        }
        rootCard.addView(convertedView)
        updateConversionHintDisplay()
        triggerPreviewConversion()

        val currencyScroll = createHorizontalScroll(topMarginDp = 3, theme = theme).apply {
            currencyRow.orientation = LinearLayout.HORIZONTAL
            addView(currencyRow)
        }
        fun rebuildCurrencyChips() {
            currencyRow.removeAllViews()
            val codes = CurrencyCatalog.orderedCodes(loaded.settings.pinnedCurrencies)
            for (code in codes) {
                val info = CurrencyCatalog.find(code)
                val selected = state.origCurrency.equals(code, ignoreCase = true)
                val pill = buildTextPill(
                    label = "${info.flag} ${info.code}",
                    selected = selected,
                    theme = theme
                ) {
                    pauseCountdownForUserInteraction(theme)
                    if (!state.origCurrency.equals(code, ignoreCase = true)) {
                        state.origCurrency = code
                        state.preserveInitialRate = false
                        rebuildCurrencyChips()
                        updateAmountAppearance()
                        triggerPreviewConversion()
                    }
                }
                currencyRow.addView(pill, horizontalItemParams())
            }
        }
        rebuildCurrencyChips()
        rootCard.addView(currencyScroll)

        // 中部可滚动交互区（限高防溢出）
        val maxBodyHeightPx = (resources.displayMetrics.heightPixels * 0.44f).toInt().coerceAtLeast(dp(170))
        val bodyScroll = MaxHeightScrollView(this, maxBodyHeightPx).apply {
            isVerticalScrollBarEnabled = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(4) }
            setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_DOWN) {
                    pauseCountdownForUserInteraction(theme)
                }
                false
            }
        }
        val bodyContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        // 4. 所属账本选择胶囊条（>1 个未归档账本时显示）
        if (loaded.ledgers.size > 1) {
            bodyContainer.addView(buildSectionLabel("所属账本", theme))
            val ledgerScroll = createHorizontalScroll(topMarginDp = 3, theme = theme).apply {
                ledgerRow.orientation = LinearLayout.HORIZONTAL
                addView(ledgerRow)
            }
            fun rebuildLedgerChips() {
                ledgerRow.removeAllViews()
                for (ledger in loaded.ledgers) {
                    val selected = state.ledgerId == ledger.id
                    val pill = buildColoredDotPill(
                        label = ledger.name,
                        dotColor = ledger.colorHex.toInt(),
                        selected = selected,
                        theme = theme
                    ) {
                        pauseCountdownForUserInteraction(theme)
                        state.ledgerId = ledger.id
                        rebuildLedgerChips()
                    }
                    ledgerRow.addView(pill, horizontalItemParams())
                }
            }
            rebuildLedgerChips()
            bodyContainer.addView(ledgerScroll)
        }

        // 5. 分类横向滚动彩色图标胶囊条（按支出/收入过滤，含一二级分类）
        categorySection.apply {
            orientation = LinearLayout.VERTICAL
            visibility = if (state.type == TransactionType.TRANSFER) View.GONE else View.VISIBLE
        }
        categorySection.addView(buildSectionLabel("分类", theme))
        val categoryScroll = createHorizontalScroll(topMarginDp = 3, theme = theme).apply {
            categoryRow.orientation = LinearLayout.HORIZONTAL
            addView(categoryRow)
        }
        rebuildCategoryChips(categoryRow, theme, loaded.categories, state) {
            pauseCountdownForUserInteraction(theme)
        }
        categorySection.addView(categoryScroll)
        bodyContainer.addView(categorySection)

        // 6. 账户横向滚动彩色胶囊条
        bodyContainer.addView(buildSectionLabel(if (state.type == TransactionType.TRANSFER) "转出账户" else "账户", theme))
        val accountScroll = createHorizontalScroll(topMarginDp = 3, theme = theme).apply {
            accountRow.orientation = LinearLayout.HORIZONTAL
            addView(accountRow)
        }
        fun rebuildAccountChips() {
            accountRow.removeAllViews()
            for (acc in loaded.accounts) {
                val selected = state.accountId == acc.id
                val pill = buildColoredDotPill(
                    label = "${acc.name} (${acc.currencyCode})",
                    dotColor = acc.colorHex.toInt(),
                    selected = selected,
                    theme = theme
                ) {
                    pauseCountdownForUserInteraction(theme)
                    if (state.accountId != acc.id) {
                        state.accountId = acc.id
                        state.preserveInitialRate = false
                        if (state.toAccountId == acc.id) {
                            state.toAccountId = loaded.accounts.firstOrNull { it.id != acc.id }?.id
                            rebuildToAccountChips(toAccountRow, theme, loaded.accounts, state) {
                                pauseCountdownForUserInteraction(theme)
                            }
                        }
                        rebuildAccountChips()
                        triggerPreviewConversion()
                    }
                }
                accountRow.addView(pill, horizontalItemParams())
            }
        }
        rebuildAccountChips()
        bodyContainer.addView(accountScroll)

        // 6b. 转入账户条（仅转账时显示）
        toAccountSection.apply {
            orientation = LinearLayout.VERTICAL
            visibility = if (state.type == TransactionType.TRANSFER) View.VISIBLE else View.GONE
        }
        toAccountSection.addView(buildSectionLabel("转入账户", theme))
        val toAccountScroll = createHorizontalScroll(topMarginDp = 3, theme = theme).apply {
            toAccountRow.orientation = LinearLayout.HORIZONTAL
            addView(toAccountRow)
        }
        rebuildToAccountChips(toAccountRow, theme, loaded.accounts, state) {
            pauseCountdownForUserInteraction(theme)
        }
        toAccountSection.addView(toAccountScroll)
        bodyContainer.addView(toAccountSection)

        // 6c. 账单标签横向滚动彩色多选区（已选标签靠前高亮）
        if (loaded.tags.isNotEmpty() || state.tags.isNotBlank()) {
            bodyContainer.addView(buildSectionLabel("标签（可多选）", theme))
            val tagRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
            }
            val tagScroll = createHorizontalScroll(topMarginDp = 3, theme = theme).apply {
                addView(tagRow)
            }
            fun rebuildTagChips() {
                tagRow.removeAllViews()
                val selectedNames = TagTextCodec.parse(state.tags)
                val selectedSet = selectedNames.toSet()
                val tagColorMap = loaded.tags.associate { it.name to it.colorHex.toInt() }
                val orderedTags = LinkedHashSet<String>()
                orderedTags.addAll(selectedNames)
                loaded.tags.sortedWith(compareBy<TagEntity> { it.sortOrder }.thenBy { it.id })
                    .forEach { orderedTags.add(it.name) }

                for (tagName in orderedTags) {
                    val isSelected = tagName in selectedSet
                    val dotColor = tagColorMap[tagName] ?: fallbackOverlayTagColor(tagName)
                    val pill = buildColoredTagPill(
                        label = if (isSelected) "✓ $tagName" else tagName,
                        dotColor = dotColor,
                        selected = isSelected,
                        theme = theme
                    ) {
                        pauseCountdownForUserInteraction(theme)
                        val current = TagTextCodec.parse(state.tags)
                        val updated = if (tagName in current) {
                            current.filterNot { it == tagName }
                        } else {
                            current + tagName
                        }
                        state.tags = TagTextCodec.format(updated)
                        rebuildTagChips()
                    }
                    tagRow.addView(pill, horizontalItemParams())
                }
            }
            rebuildTagChips()
            bodyContainer.addView(tagScroll)
        }

        // 7. 商户 / 备注点击输入区域（动态 FLAG_NOT_FOCUSABLE 切换软键盘）
        val inputHeaderRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(6) }
        }
        val inputTitle = TextView(this).apply {
            text = "商户 / 备注（点击可输入）"
            setTextColor(theme.textSecondary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val finishInputPill = TextView(this).apply {
            text = "完成输入"
            setTextColor(contrastOnAccent(theme.mintAccent))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(9), dp(2), dp(9), dp(2))
            background = GradientDrawable().apply {
                setColor(theme.mintAccent)
                cornerRadius = dp(8).toFloat()
            }
            visibility = View.GONE
        }
        inputHeaderRow.addView(inputTitle)
        inputHeaderRow.addView(finishInputPill)
        bodyContainer.addView(inputHeaderRow)

        val editRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(3) }
        }

        val merchantEdit = buildOverlayEditText(
            hint = "商户名称",
            initialText = state.merchant,
            imeOptions = EditorInfo.IME_ACTION_NEXT,
            theme = theme
        )
        val noteEdit = buildOverlayEditText(
            hint = "添加备注",
            initialText = state.note,
            imeOptions = EditorInfo.IME_ACTION_DONE,
            theme = theme
        )

        fun commitAmountEditIfVisible() {
            if (amountEdit.visibility != View.VISIBLE) return
            val parsed = MoneyAmount.parseToMicros(amountEdit.text?.toString().orEmpty())
            if (parsed != null && parsed > 0L && parsed != state.origAmountMicros) {
                state.origAmountMicros = parsed
                state.preserveInitialRate = false
            }
            amountEdit.visibility = View.GONE
            amountView.visibility = View.VISIBLE
            amountEditHint.visibility = View.VISIBLE
            updateAmountAppearance()
            triggerPreviewConversion()
        }

        fun exitInputFocusMode() {
            commitAmountEditIfVisible()
            if (!windowFocusableForIme &&
                !amountEdit.isFocusableInTouchMode &&
                !merchantEdit.isFocusableInTouchMode &&
                !noteEdit.isFocusableInTouchMode
            ) {
                return
            }
            finishInputPill.visibility = View.GONE
            runCatching {
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.hideSoftInputFromWindow(rootCard.windowToken, 0)
            }
            amountEdit.clearFocus()
            merchantEdit.clearFocus()
            noteEdit.clearFocus()
            amountEdit.isFocusable = false
            amountEdit.isFocusableInTouchMode = false
            merchantEdit.isFocusable = false
            merchantEdit.isFocusableInTouchMode = false
            noteEdit.isFocusable = false
            noteEdit.isFocusableInTouchMode = false
            setWindowFocusable(false)
        }

        fun enterInputFocusMode(target: EditText) {
            pauseCountdownForUserInteraction(theme)
            if (target !== amountEdit) {
                commitAmountEditIfVisible()
            }
            amountEdit.isFocusable = true
            amountEdit.isFocusableInTouchMode = true
            merchantEdit.isFocusable = true
            merchantEdit.isFocusableInTouchMode = true
            noteEdit.isFocusable = true
            noteEdit.isFocusableInTouchMode = true
            finishInputPill.visibility = View.VISIBLE
            setWindowFocusable(true)
            target.post {
                runCatching {
                    target.requestFocus()
                    val len = target.text?.length ?: 0
                    target.setSelection(len)
                    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                    imm?.showSoftInput(target, InputMethodManager.SHOW_IMPLICIT)
                }
            }
        }

        beginAmountEdit = {
            pauseCountdownForUserInteraction(theme)
            isUpdatingAmountField = true
            val digits = CurrencyCatalog.find(state.origCurrency).minorDigits
            amountEdit.setText(MoneyAmount.formatPlain(state.origAmountMicros, digits, grouping = false))
            isUpdatingAmountField = false
            amountView.visibility = View.GONE
            amountEditHint.visibility = View.GONE
            amountEdit.visibility = View.VISIBLE
            enterInputFocusMode(amountEdit)
        }

        finishInputPill.setOnClickListener { exitInputFocusMode() }
        merchantEdit.setOnClickListener { enterInputFocusMode(merchantEdit) }
        noteEdit.setOnClickListener { enterInputFocusMode(noteEdit) }

        amountEdit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (isUpdatingAmountField) return
                if (windowFocusableForIme) {
                    pauseCountdownForUserInteraction(theme)
                }
                val parsed = MoneyAmount.parseToMicros(s?.toString().orEmpty())
                if (parsed != null && parsed > 0L && parsed != state.origAmountMicros) {
                    state.origAmountMicros = parsed
                    state.preserveInitialRate = false
                    triggerPreviewConversion()
                }
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        merchantEdit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                state.merchant = s?.toString().orEmpty()
                if (windowFocusableForIme) {
                    pauseCountdownForUserInteraction(theme)
                }
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        noteEdit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                state.note = s?.toString().orEmpty()
                if (windowFocusableForIme) {
                    pauseCountdownForUserInteraction(theme)
                }
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })

        amountEdit.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                exitInputFocusMode()
                true
            } else {
                false
            }
        }
        merchantEdit.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_NEXT) {
                enterInputFocusMode(noteEdit)
                true
            } else if (actionId == EditorInfo.IME_ACTION_DONE) {
                exitInputFocusMode()
                true
            } else {
                false
            }
        }
        noteEdit.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                exitInputFocusMode()
                true
            } else {
                false
            }
        }
        val focusCheckRunnable = Runnable {
            if (!amountEdit.hasFocus() && !merchantEdit.hasFocus() && !noteEdit.hasFocus()) {
                exitInputFocusMode()
            }
        }
        amountEdit.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                amountEdit.postDelayed(focusCheckRunnable, 120L)
            }
        }
        merchantEdit.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                merchantEdit.postDelayed(focusCheckRunnable, 120L)
            }
        }
        noteEdit.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                noteEdit.postDelayed(focusCheckRunnable, 120L)
            }
        }

        val merchantParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginEnd = dp(5)
        }
        val noteParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.2f)
        editRow.addView(merchantEdit, merchantParams)
        editRow.addView(noteEdit, noteParams)
        bodyContainer.addView(editRow)

        // 8. 快捷开关胶囊：可报销 | 不计收支
        val toggleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(6) }
        }

        fun rebuildToggleChips() {
            toggleRow.removeAllViews()
            val reimburseSelected = state.reimburseStatus == ReimburseStatus.PENDING ||
                state.reimburseStatus == ReimburseStatus.REIMBURSED
            val reimburseLabel = when (state.reimburseStatus) {
                ReimburseStatus.REIMBURSED -> "✓ 已报销"
                ReimburseStatus.PENDING -> "✓ 可报销"
                else -> "可报销"
            }
            val reimbursePill = buildTextPill(
                label = reimburseLabel,
                selected = reimburseSelected,
                theme = theme
            ) {
                pauseCountdownForUserInteraction(theme)
                if (state.reimburseStatus != ReimburseStatus.REIMBURSED) {
                    state.reimburseStatus = if (state.reimburseStatus == ReimburseStatus.PENDING) {
                        ReimburseStatus.NONE
                    } else {
                        ReimburseStatus.PENDING
                    }
                    rebuildToggleChips()
                }
            }

            val excludePill = buildTextPill(
                label = if (state.excludeFromStats) "✓ 不计收支" else "不计收支",
                selected = state.excludeFromStats,
                theme = theme
            ) {
                pauseCountdownForUserInteraction(theme)
                state.excludeFromStats = !state.excludeFromStats
                rebuildToggleChips()
            }

            toggleRow.addView(reimbursePill, horizontalItemParams())
            toggleRow.addView(excludePill, horizontalItemParams())
        }
        rebuildToggleChips()
        bodyContainer.addView(toggleRow)

        bodyScroll.addView(bodyContainer)
        rootCard.addView(bodyScroll)

        // 9. 底部固定操作栏：删除 | 仅收起 | 保存确认
        val actionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(9) }
        }
        val deleteBtn = buildActionButton(
            label = "删除",
            textColor = theme.expenseColor,
            bgColor = theme.chipBg,
            strokeColor = Color.TRANSPARENT
        ) {
            exitInputFocusMode()
            performDeleteAndDismiss(state.txId)
        }
        val dismissBtn = buildActionButton(
            label = "仅收起",
            textColor = theme.textPrimary,
            bgColor = Color.TRANSPARENT,
            strokeColor = withAlpha(theme.cardStroke, 140)
        ) {
            exitInputFocusMode()
            countdownJob?.cancel()
            stopSelfCleanly()
        }
        val saveConfirmBtn = buildActionButton(
            label = "保存确认",
            textColor = contrastOnAccent(theme.mintAccent),
            bgColor = theme.mintAccent,
            strokeColor = Color.TRANSPARENT
        ) {
            exitInputFocusMode()
            performSaveConfirmAndDismiss(state, loaded.accounts)
        }

        val btnParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginEnd = dp(6)
        }
        val lastBtnParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.25f)
        actionRow.addView(deleteBtn, btnParams)
        actionRow.addView(dismissBtn, btnParams)
        actionRow.addView(saveConfirmBtn, lastBtnParams)
        rootCard.addView(actionRow)

        rootCard.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                pauseCountdownForUserInteraction(theme)
            }
            false
        }

        return { exitInputFocusMode() }
    }

    private fun renderCompactBody(
        rootCard: LinearLayout,
        theme: OverlayTheme,
        payload: OverlayPayload,
        loaded: LoadedContext,
        state: EditorState,
        onExpand: () -> Unit
    ) {
        val signPrefix = when (state.type) {
            TransactionType.INCOME -> "+"
            TransactionType.TRANSFER -> "⇄ "
            else -> "-"
        }
        val amountColor = when (state.type) {
            TransactionType.INCOME -> theme.incomeColor
            TransactionType.TRANSFER -> theme.transferColor
            else -> theme.expenseColor
        }
        val formattedAmount = signPrefix + MoneyAmount.format(
            amountMicros = state.origAmountMicros,
            currencyCode = state.origCurrency,
            showSign = false,
            showCurrencyCode = true
        )
        val convertedText = formatConvertedAccountHint(
            origCurrency = state.origCurrency,
            accountAmountMicros = state.previewAccountAmountMicros,
            accountCurrency = state.previewAccountCurrency
        )
        val amountView = TextView(this).apply {
            text = formattedAmount
            setTextColor(amountColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(4), 0, if (convertedText.isNotEmpty()) dp(1) else dp(3))
        }
        rootCard.addView(amountView)

        if (convertedText.isNotEmpty()) {
            val convertedView = TextView(this).apply {
                text = convertedText
                setTextColor(theme.textSecondary)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setPadding(0, 0, 0, dp(3))
            }
            rootCard.addView(convertedView)
        }

        val catName = loaded.categories.firstOrNull { it.id == state.categoryId }?.name ?: payload.categoryName
        val accName = loaded.accounts.firstOrNull { it.id == state.accountId }?.name ?: payload.accountName
        val tagSummary = TagTextCodec.parse(state.tags).joinToString(" ") { "#$it" }
        val metaParts = listOfNotNull(
            state.merchant.takeIf { it.isNotBlank() },
            catName.takeIf { it.isNotBlank() },
            accName.takeIf { it.isNotBlank() },
            tagSummary.takeIf { it.isNotBlank() }
        )
        val metaView = TextView(this).apply {
            text = metaParts.joinToString(" · ").ifBlank { "点击展开编辑或确认账单" }
            setTextColor(theme.textPrimary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            setPadding(0, 0, 0, dp(9))
        }
        rootCard.addView(metaView)

        val actionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }
        val deleteBtn = buildActionButton(
            label = "删除",
            textColor = theme.expenseColor,
            bgColor = theme.chipBg,
            strokeColor = Color.TRANSPARENT
        ) {
            performDeleteAndDismiss(state.txId)
        }
        val expandBtn = buildActionButton(
            label = "展开编辑",
            textColor = theme.textPrimary,
            bgColor = Color.TRANSPARENT,
            strokeColor = withAlpha(theme.cardStroke, 140)
        ) {
            onExpand()
        }
        val confirmBtn = buildActionButton(
            label = "确认",
            textColor = contrastOnAccent(theme.mintAccent),
            bgColor = theme.mintAccent,
            strokeColor = Color.TRANSPARENT
        ) {
            performQuickConfirmAndDismiss(state.txId)
        }

        val btnParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginEnd = dp(6)
        }
        val lastBtnParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        actionRow.addView(deleteBtn, btnParams)
        actionRow.addView(expandBtn, btnParams)
        actionRow.addView(confirmBtn, lastBtnParams)
        rootCard.addView(actionRow)
    }

    private fun rebuildCategoryChips(
        categoryRow: LinearLayout,
        theme: OverlayTheme,
        categories: List<CategoryEntity>,
        state: EditorState,
        onInteracted: () -> Unit
    ) {
        categoryRow.removeAllViews()
        val typed = categories.filter { it.type.equals(state.type, ignoreCase = true) }
        val parents = typed.filter { it.parentId == null }.sortedWith(compareBy<CategoryEntity> { it.sortOrder }.thenBy { it.id })
        val childrenByParent = typed.filter { it.parentId != null }
            .groupBy { it.parentId!! }
        val ordered = mutableListOf<Pair<CategoryEntity, Boolean>>()
        val visited = HashSet<Long>(typed.size)
        for (parent in parents) {
            if (visited.add(parent.id)) {
                ordered.add(parent to false)
            }
            val children = childrenByParent[parent.id].orEmpty()
                .sortedWith(compareBy<CategoryEntity> { it.sortOrder }.thenBy { it.id })
            for (child in children) {
                if (visited.add(child.id)) {
                    ordered.add(child to true)
                }
            }
        }
        for (cat in typed) {
            if (visited.add(cat.id)) {
                ordered.add(cat to (cat.parentId != null))
            }
        }

        for ((cat, isChild) in ordered) {
            val selected = state.categoryId == cat.id
            val displayLabel = if (isChild) "· ${cat.name}" else cat.name
            val pill = buildCategoryIconPill(
                label = displayLabel,
                badgeChar = cat.name.firstOrNull()?.toString() ?: "类",
                accentColor = cat.colorHex.toInt(),
                selected = selected,
                theme = theme
            ) {
                onInteracted()
                state.categoryId = cat.id
                rebuildCategoryChips(categoryRow, theme, categories, state, onInteracted)
            }
            categoryRow.addView(pill, horizontalItemParams())
        }
    }

    private fun rebuildToAccountChips(
        toAccountRow: LinearLayout,
        theme: OverlayTheme,
        accounts: List<AccountEntity>,
        state: EditorState,
        onInteracted: () -> Unit
    ) {
        toAccountRow.removeAllViews()
        for (acc in accounts) {
            if (acc.id == state.accountId) continue
            val selected = state.toAccountId == acc.id
            val pill = buildColoredDotPill(
                label = "${acc.name} (${acc.currencyCode})",
                dotColor = acc.colorHex.toInt(),
                selected = selected,
                theme = theme
            ) {
                onInteracted()
                state.toAccountId = acc.id
                rebuildToAccountChips(toAccountRow, theme, accounts, state, onInteracted)
            }
            toAccountRow.addView(pill, horizontalItemParams())
        }
    }

    private fun buildSectionLabel(text: String, theme: OverlayTheme): TextView =
        TextView(this).apply {
            this.text = text
            setTextColor(theme.textSecondary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
            setPadding(0, dp(5), 0, 0)
        }

    @SuppressLint("ClickableViewAccessibility")
    private fun createHorizontalScroll(topMarginDp: Int, theme: OverlayTheme): HorizontalScrollView =
        HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(topMarginDp) }
            setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_DOWN) {
                    pauseCountdownForUserInteraction(theme)
                }
                false
            }
        }

    private fun horizontalItemParams(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { marginEnd = dp(5) }

    private fun buildTextPill(
        label: String,
        selected: Boolean,
        theme: OverlayTheme,
        onClick: () -> Unit
    ): TextView = TextView(this).apply {
        text = label
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
        typeface = if (selected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        setTextColor(if (selected) theme.onMintContainer else theme.textPrimary)
        setPadding(dp(9), dp(4), dp(9), dp(4))
        background = GradientDrawable().apply {
            setColor(if (selected) theme.mintContainer else theme.chipBg)
            cornerRadius = dp(12).toFloat()
            if (selected) {
                setStroke(hairlineStrokePx(), theme.mintAccent)
            }
        }
        setOnClickListener { onClick() }
    }

    private fun buildColoredDotPill(
        label: String,
        dotColor: Int,
        selected: Boolean,
        theme: OverlayTheme,
        onClick: () -> Unit
    ): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(8), dp(4), dp(9), dp(4))
        background = GradientDrawable().apply {
            setColor(if (selected) theme.mintContainer else theme.chipBg)
            cornerRadius = dp(12).toFloat()
            if (selected) {
                setStroke(hairlineStrokePx(), theme.mintAccent)
            }
        }
        val dot = View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(dotColor)
            }
            layoutParams = LinearLayout.LayoutParams(dp(7), dp(7)).apply { marginEnd = dp(5) }
        }
        val tv = TextView(context).apply {
            text = label
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            typeface = if (selected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            setTextColor(if (selected) theme.onMintContainer else theme.textPrimary)
            maxLines = 1
        }
        addView(dot)
        addView(tv)
        setOnClickListener { onClick() }
    }

    private fun buildColoredTagPill(
        label: String,
        dotColor: Int,
        selected: Boolean,
        theme: OverlayTheme,
        onClick: () -> Unit
    ): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(8), dp(4), dp(9), dp(4))
        background = GradientDrawable().apply {
            setColor(if (selected) withAlpha(dotColor, 42) else theme.chipBg)
            cornerRadius = dp(12).toFloat()
            if (selected) {
                setStroke(hairlineStrokePx(), dotColor)
            }
        }
        val dot = View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(dotColor)
            }
            layoutParams = LinearLayout.LayoutParams(dp(6), dp(6)).apply { marginEnd = dp(5) }
        }
        val tv = TextView(context).apply {
            text = label
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            typeface = if (selected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            setTextColor(theme.textPrimary)
            maxLines = 1
        }
        addView(dot)
        addView(tv)
        setOnClickListener { onClick() }
    }

    private fun fallbackOverlayTagColor(tagName: String): Int {
        val palette = intArrayOf(
            0xFF2AA876.toInt(),
            0xFF0284C7.toInt(),
            0xFFEA580C.toInt(),
            0xFF7C3AED.toInt(),
            0xFFDB2777.toInt(),
            0xFF0D9488.toInt()
        )
        return palette[kotlin.math.abs(tagName.hashCode()) % palette.size]
    }

    private fun buildCategoryIconPill(
        label: String,
        badgeChar: String,
        accentColor: Int,
        selected: Boolean,
        theme: OverlayTheme,
        onClick: () -> Unit
    ): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(6), dp(4), dp(9), dp(4))
        background = GradientDrawable().apply {
            setColor(if (selected) withAlpha(accentColor, 38) else theme.chipBg)
            cornerRadius = dp(14).toFloat()
            if (selected) {
                setStroke(hairlineStrokePx(), accentColor)
            }
        }
        val iconCircle = TextView(context).apply {
            text = badgeChar
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.5f)
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(if (selected) Color.WHITE else accentColor)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(if (selected) accentColor else withAlpha(accentColor, 34))
            }
            layoutParams = LinearLayout.LayoutParams(dp(18), dp(18)).apply { marginEnd = dp(5) }
        }
        val tv = TextView(context).apply {
            text = label
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            typeface = if (selected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            setTextColor(theme.textPrimary)
            maxLines = 1
        }
        addView(iconCircle)
        addView(tv)
        setOnClickListener { onClick() }
    }

    private fun buildOverlayEditText(
        hint: String,
        initialText: String,
        imeOptions: Int,
        theme: OverlayTheme,
        inputType: Int = InputType.TYPE_CLASS_TEXT,
        textSizeSp: Float = 12.5f
    ): EditText = EditText(this).apply {
        this.hint = hint
        setText(initialText)
        setHintTextColor(theme.textSecondary)
        setTextColor(theme.textPrimary)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp)
        setSingleLine(true)
        this.inputType = inputType
        this.imeOptions = imeOptions
        setPadding(dp(9), dp(6), dp(9), dp(6))
        isFocusable = false
        isFocusableInTouchMode = false
        isClickable = true
        background = GradientDrawable().apply {
            setColor(theme.inputBg)
            cornerRadius = dp(10).toFloat()
            setStroke(hairlineStrokePx(), withAlpha(theme.cardStroke, 115))
        }
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        Color.argb(alpha.coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))

    private fun setWindowFocusable(focusable: Boolean) {
        val wm = windowManager ?: return
        val wrapper = overlayWrapper ?: return
        val lp = overlayLayoutParams ?: return

        val desiredFlags = if (focusable) {
            DEFAULT_WINDOW_FLAGS and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        } else {
            DEFAULT_WINDOW_FLAGS
        }
        val desiredSoftInput = if (focusable) {
            WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE or WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN
        } else {
            WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED
        }

        if (lp.flags == desiredFlags && lp.softInputMode == desiredSoftInput && windowFocusableForIme == focusable) {
            return
        }
        lp.flags = desiredFlags
        lp.softInputMode = desiredSoftInput
        windowFocusableForIme = focusable
        runCatching { wm.updateViewLayout(wrapper, lp) }
    }

    private fun pauseCountdownForUserInteraction(theme: OverlayTheme) {
        if (countdownPaused) return
        countdownPaused = true
        countdownJob?.cancel()
        activeCountdownView?.let { cv ->
            cv.text = "已暂停自动确认"
            cv.setTextColor(theme.warningText)
            cv.background = GradientDrawable().apply {
                setColor(theme.warningBg)
                cornerRadius = dp(8).toFloat()
            }
        }
    }

    private fun startCountdownIfNeeded(txId: Long, seconds: Int) {
        countdownJob?.cancel()
        if (seconds <= 0 || countdownPaused) return

        countdownJob = serviceScope.launch {
            for (remaining in seconds downTo 1) {
                if (countdownPaused) return@launch
                activeCountdownView?.text = "${remaining}s 自动确认"
                delay(1_000L)
            }
            if (countdownPaused) return@launch
            activeCountdownView?.text = "确认中..."
            withContext(Dispatchers.IO) {
                CaptureRuntimeHelper.ledgerServiceOrNull(applicationContext)?.confirmTransaction(txId)
            }
            stopSelfCleanly()
        }
    }

    private fun performQuickConfirmAndDismiss(txId: Long) {
        countdownJob?.cancel()
        serviceScope.launch {
            withContext(Dispatchers.IO) {
                CaptureRuntimeHelper.ledgerServiceOrNull(applicationContext)?.confirmTransaction(txId)
            }
            stopSelfCleanly()
        }
    }

    private fun performDeleteAndDismiss(txId: Long) {
        countdownJob?.cancel()
        serviceScope.launch {
            withContext(Dispatchers.IO) {
                CaptureRuntimeHelper.ledgerServiceOrNull(applicationContext)?.deleteTransaction(txId)
            }
            stopSelfCleanly()
        }
    }

    private fun performSaveConfirmAndDismiss(state: EditorState, accounts: List<AccountEntity>) {
        countdownJob?.cancel()
        serviceScope.launch {
            val saveSuccess = withContext(Dispatchers.IO) {
                val ledgerService = CaptureRuntimeHelper.ledgerServiceOrNull(applicationContext)
                    ?: return@withContext false
                val effectiveAccountId = state.accountId.takeIf { it > 0L }
                    ?: accounts.firstOrNull()?.id
                    ?: return@withContext false
                val selectedAccCurrency = accounts.firstOrNull { it.id == effectiveAccountId }?.currencyCode.orEmpty()
                val preservedManualAmount = if (
                    state.preserveInitialRate &&
                    state.initialAccountAmountMicros > 0L &&
                    selectedAccCurrency.isNotBlank() &&
                    !state.origCurrency.equals(selectedAccCurrency, ignoreCase = true)
                ) {
                    state.initialAccountAmountMicros
                } else {
                    null
                }
                val preservedManualRate = if (preservedManualAmount == null &&
                    state.preserveInitialRate &&
                    state.initialRateToAccountMicros != null &&
                    selectedAccCurrency.isNotBlank() &&
                    !state.origCurrency.equals(selectedAccCurrency, ignoreCase = true)
                ) {
                    state.initialRateToAccountMicros
                } else {
                    null
                }

                val effectiveToAccountId = if (state.type == TransactionType.TRANSFER) {
                    state.toAccountId?.takeIf { it != effectiveAccountId }
                        ?: accounts.firstOrNull { it.id != effectiveAccountId }?.id
                } else {
                    null
                }

                val request = RecordTransactionRequest(
                    existingId = state.txId,
                    type = state.type,
                    timestamp = state.timestamp,
                    origAmountMicros = state.origAmountMicros,
                    origCurrency = state.origCurrency,
                    accountId = effectiveAccountId,
                    manualAccountAmountMicros = preservedManualAmount,
                    manualRateToAccountMicros = preservedManualRate,
                    toAccountId = effectiveToAccountId,
                    categoryId = if (state.type == TransactionType.TRANSFER) null else state.categoryId,
                    merchant = state.merchant.trim(),
                    note = state.note.trim(),
                    tags = TagTextCodec.format(TagTextCodec.parse(state.tags)),
                    reimburseStatus = state.reimburseStatus,
                    sourceType = state.sourceType,
                    rawEventId = state.rawEventId,
                    status = TransactionStatus.CONFIRMED,
                    ledgerId = state.ledgerId,
                    excludeFromStats = state.excludeFromStats,
                    excludeFromBudget = state.excludeFromBudget,
                    refundOfTxId = state.refundOfTxId,
                    attachmentPaths = state.attachmentPaths,
                    reimbursedTxId = state.reimbursedTxId
                )
                ledgerService.saveTransaction(request).isSuccess
            }
            if (!saveSuccess) {
                Toast.makeText(applicationContext, "保存失败，请稍后重试", Toast.LENGTH_SHORT).show()
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
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = GradientDrawable().apply {
                setColor(bgColor)
                cornerRadius = dp(10).toFloat()
                if (strokeColor != Color.TRANSPARENT) {
                    setStroke(hairlineStrokePx(), strokeColor)
                }
            }
            setOnClickListener { onClick() }
        }
    }

    private fun removeOverlayView() {
        val wrapper = overlayWrapper ?: return
        runCatching { windowManager?.removeViewImmediate(wrapper) }
        overlayWrapper = null
        overlayLayoutParams = null
        activeCountdownView = null
        windowFocusableForIme = false
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

    private fun hairlineStrokePx(): Int =
        (0.65f * resources.displayMetrics.density).toInt().coerceAtLeast(1)

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

        private const val DEFAULT_WINDOW_FLAGS =
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN

        private fun formatConvertedAccountHint(
            origCurrency: String,
            accountAmountMicros: Long,
            accountCurrency: String
        ): String {
            val accCur = accountCurrency.trim()
            if (accCur.isBlank() ||
                origCurrency.equals(accCur, ignoreCase = true) ||
                accountAmountMicros <= 0L
            ) {
                return ""
            }
            return "≈ 账户实扣 ${MoneyAmount.format(accountAmountMicros, accCur, showCurrencyCode = true)}"
        }

        private fun formatConvertedAccountHint(payload: OverlayPayload): String =
            formatConvertedAccountHint(
                origCurrency = payload.origCurrency,
                accountAmountMicros = payload.accountAmountMicros,
                accountCurrency = payload.accountCurrency
            )

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
                debugWarn("showOrNotify: canDrawOverlays=false, fallback notif tx=$transactionId")
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

            val directResult = runCatching {
                appContext.startService(intent)
            }
            if (directResult.isSuccess) {
                debugLog("showOrNotify: startService dispatched tx=$transactionId badge=$sourceBadge")
                return
            }
            debugWarn("showOrNotify: startService failed tx=$transactionId: ${directResult.exceptionOrNull()?.message}")

            val fgResult = runCatching {
                intent.putExtra(EXTRA_AS_FOREGROUND, true)
                ContextCompat.startForegroundService(appContext, intent)
            }
            if (fgResult.isSuccess) {
                debugLog("showOrNotify: startForegroundService dispatched tx=$transactionId badge=$sourceBadge")
                return
            }
            debugWarn("showOrNotify: startForegroundService failed tx=$transactionId: ${fgResult.exceptionOrNull()?.message}, fallback notif")
            postFallbackNotification(appContext, payload)
        }

        private const val TAG = "KairoOverlay"

        private fun debugLog(msg: String) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, msg)
            }
        }

        private fun debugWarn(msg: String) {
            if (BuildConfig.DEBUG) {
                Log.w(TAG, msg)
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
                "余白 · $cleanBadge：$amountStr"
            } else {
                "余白自动记账：$amountStr"
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

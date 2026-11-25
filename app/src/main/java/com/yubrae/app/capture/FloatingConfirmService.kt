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
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.Toast
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 悬浮窗自动记账确认卡片服务（底部单手热区 Compose 重构版）：
 * - 默认贴合屏幕底部弹出（单手大拇指热区），支持精简确认态与完整编辑态自由切换；
 * - 复用手记同源多色卡通图标、智能常用分类推荐、搜索与就地新建分类、分组标签与搜索即新建；
 * - 全部钻取子视图在卡片内部完成（零系统 Dialog/DropdownMenu），彻底避免 Service Token 崩溃；
 * - 默认携带 FLAG_NOT_FOCUSABLE 不抢微信/支付宝焦点，点击输入时按需切焦点拉起软键盘，完成/点击外部/返回键自动归还焦点；
 * - 无悬浮窗权限或后台受限时自动优雅降级为系统通知。
 */
class FloatingConfirmService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var windowManager: WindowManager? = null
    private var overlayWrapper: OverlayWrapperLayout? = null
    private var overlayLayoutParams: WindowManager.LayoutParams? = null
    private var overlayComposeOwner: OverlayComposeOwner? = null
    private var loadJob: Job? = null
    private var countdownJob: Job? = null
    private var previewJob: Job? = null

    private var windowFocusableForIme: Boolean = false
    private var activeUiState: FloatingEditorUiState? = null

    private data class LoadedContext(
        val settings: AppSettings,
        val tx: TransactionEntity?,
        val accounts: List<AccountEntity>,
        val categories: List<CategoryEntity>,
        val ledgers: List<LedgerEntity>,
        val tags: List<TagEntity>,
        val recentTransactions: List<TransactionEntity>
    )

    private class OverlayComposeOwner :
        LifecycleOwner,
        SavedStateRegistryOwner,
        ViewModelStoreOwner {
        private val lifecycleRegistry = LifecycleRegistry(this)
        private val savedStateController = SavedStateRegistryController.create(this)
        private val store = ViewModelStore()

        override val lifecycle: Lifecycle
            get() = lifecycleRegistry

        override val savedStateRegistry: SavedStateRegistry
            get() = savedStateController.savedStateRegistry

        override val viewModelStore: ViewModelStore
            get() = store

        fun onAttach() {
            savedStateController.performAttach()
            savedStateController.performRestore(null)
            lifecycleRegistry.currentState = Lifecycle.State.CREATED
            lifecycleRegistry.currentState = Lifecycle.State.STARTED
            lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        }

        fun onDetach() {
            runCatching {
                lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
            }
            store.clear()
        }
    }

    private class OverlayWrapperLayout(
        context: Context,
        private val onOutsideTouch: () -> Unit,
        private val onBackPressedWhileFocusable: () -> Boolean,
        private val onWindowFocusLost: () -> Unit
    ) : FrameLayout(context) {

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

        val isPreview = intent?.getBooleanExtra(EXTRA_PREVIEW_MODE, false) == true
        val txId = intent?.getLongExtra(EXTRA_TX_ID, -1L) ?: -1L
        if (!isPreview && txId <= 0L) {
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
            autoConfirmSeconds = if (isPreview) 0 else (intent?.getIntExtra(EXTRA_AUTO_CONFIRM_SECONDS, 8) ?: 8),
            accountAmountMicros = intent?.getLongExtra(EXTRA_ACCOUNT_AMOUNT_MICROS, 0L) ?: 0L,
            accountCurrency = intent?.getStringExtra(EXTRA_ACCOUNT_CURRENCY).orEmpty(),
            sourceBadge = intent?.getStringExtra(EXTRA_SOURCE_BADGE).orEmpty(),
            isPreviewMode = isPreview
        )

        if (!Settings.canDrawOverlays(this)) {
            debugWarn("onStartCommand: overlay permission missing, fallback notif tx=$txId")
            if (!isPreview) {
                postFallbackNotification(this, payload)
            }
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
                val tx = if (isPreview) null else runCatching { db?.transactionDao()?.getById(txId) }.getOrNull()
                val allAccounts = runCatching { db?.accountDao()?.getAll() }.getOrNull().orEmpty()
                val activeAccounts = allAccounts.filter { !it.archived }.ifEmpty { allAccounts }
                val categories = runCatching { db?.categoryDao()?.getAll() }.getOrNull().orEmpty()
                val allLedgers = runCatching { db?.ledgerDao()?.getAll() }.getOrNull().orEmpty()
                val activeLedgers = allLedgers.filter { !it.archived }.ifEmpty { allLedgers }
                val tags = runCatching { db?.tagDao()?.getAll() }.getOrNull().orEmpty()
                val recentTxs = runCatching { db?.transactionDao()?.getAll()?.take(300) }.getOrNull().orEmpty()
                LoadedContext(settings, tx, activeAccounts, categories, activeLedgers, tags, recentTxs)
            }

            val shown = showInteractiveOverlay(payload, loaded)
            if (!shown) {
                debugWarn("onStartCommand: showInteractiveOverlay failed, fallback notif tx=$txId")
                if (!isPreview) {
                    postFallbackNotification(this@FloatingConfirmService, payload)
                }
                stopSelfCleanly()
            } else {
                debugLog("onStartCommand: compose overlay shown tx=$txId preview=$isPreview")
            }
        }

        return START_NOT_STICKY
    }

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

    private fun buildInitialUiState(payload: OverlayPayload, loaded: LoadedContext): FloatingEditorUiState {
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
        val resolvedToAccountId = tx?.toAccountId?.takeIf { id ->
            loaded.accounts.any { it.id == id } && id != resolvedAccountId
        } ?: loaded.accounts.firstOrNull { it.id != resolvedAccountId }?.id
        val resolvedCategoryId = tx?.categoryId?.takeIf { id -> loaded.categories.any { it.id == id } }
            ?: loaded.categories.firstOrNull {
                it.type.equals(normalizedType, ignoreCase = true) &&
                    it.name.equals(payload.categoryName, ignoreCase = true)
            }?.id
            ?: loaded.categories.firstOrNull {
                it.type.equals(normalizedType, ignoreCase = true) && it.parentId == null
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

        return FloatingEditorUiState(
            txId = payload.transactionId,
            isPreviewMode = payload.isPreviewMode,
            sourceBadge = payload.sourceBadge,
            fallbackCategoryName = payload.categoryName,
            fallbackAccountName = payload.accountName,
            initialType = normalizedType,
            initialOrigAmountMicros = origAmount,
            initialOrigCurrency = origCur,
            initialAccountId = resolvedAccountId,
            initialToAccountId = resolvedToAccountId,
            initialCategoryId = resolvedCategoryId,
            initialLedgerId = resolvedLedgerId,
            initialMerchant = tx?.merchant ?: payload.merchant,
            initialNote = tx?.note.orEmpty(),
            initialReimburseStatus = tx?.reimburseStatus ?: ReimburseStatus.NONE,
            initialExcludeFromStats = tx?.excludeFromStats ?: false,
            initialExcludeFromBudget = tx?.excludeFromBudget ?: false,
            timestamp = tx?.timestamp ?: System.currentTimeMillis(),
            sourceType = tx?.sourceType ?: CaptureChannel.AUTO_ACCOUNTING,
            rawEventId = tx?.rawEventId,
            initialTags = tx?.tags.orEmpty(),
            attachmentPaths = tx?.attachmentPaths.orEmpty(),
            refundOfTxId = tx?.refundOfTxId,
            reimbursedTxId = tx?.reimbursedTxId,
            initialPreserveRate = initialAccAmt > 0L && initialAccCur.isNotBlank(),
            initialAccountAmountMicros = initialAccAmt,
            initialAccountCurrency = initialAccCur,
            initialRateToAccountMicros = tx?.rateToAccountMicros?.takeIf { it > 0L },
            initialPreviewAmountMicros = initialAccAmt,
            initialPreviewCurrency = initialAccCur,
            initialPreviewRateMicros = tx?.rateToAccountMicros?.takeIf { it > 0L } ?: 1_000_000L,
            initialExpanded = payload.isPreviewMode || loaded.settings.floatingRichEditorEnabled,
            initialRemainingSeconds = payload.autoConfirmSeconds,
            initialAccounts = loaded.accounts,
            initialCategories = loaded.categories,
            initialLedgers = loaded.ledgers,
            initialTagsList = loaded.tags,
            initialRecentTransactions = loaded.recentTransactions
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
        windowFocusableForIme = false

        val state = buildInitialUiState(payload, loaded)
        activeUiState = state

        val owner = OverlayComposeOwner().apply { onAttach() }
        overlayComposeOwner = owner

        val wrapper = OverlayWrapperLayout(
            context = this,
            onOutsideTouch = { releaseImeFocus() },
            onBackPressedWhileFocusable = {
                if (windowFocusableForIme) {
                    releaseImeFocus()
                    true
                } else {
                    false
                }
            },
            onWindowFocusLost = {
                if (windowFocusableForIme) {
                    releaseImeFocus()
                }
            }
        )
        wrapper.setViewTreeLifecycleOwner(owner)
        wrapper.setViewTreeSavedStateRegistryOwner(owner)
        wrapper.setViewTreeViewModelStoreOwner(owner)

        val composeView = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setContent {
                FloatingConfirmOverlayRoot(
                    settings = loaded.settings,
                    state = state,
                    onUserInteracted = { pauseCountdownForUserInteraction(state) },
                    onRequestImeFocus = { setWindowFocusable(true) },
                    onReleaseImeFocus = { releaseImeFocus() },
                    onTypeOrAmountOrAccountChanged = { triggerPreviewConversion(state, loaded.settings) },
                    onCreateCategory = { entity -> createCategoryInOverlay(state, entity) },
                    onCreateTag = { name, groupName, colorHex, icon ->
                        createTagInOverlay(state, name, groupName, colorHex, icon)
                    },
                    onQuickConfirm = { performQuickConfirmAndDismiss(state) },
                    onSaveConfirm = { performSaveConfirmAndDismiss(state) },
                    onDelete = { performDeleteAndDismiss(state) },
                    onDismissOnly = {
                        countdownJob?.cancel()
                        stopSelfCleanly()
                    }
                )
            }
        }
        wrapper.addView(
            composeView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        )

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            DEFAULT_WINDOW_FLAGS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = 0
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED
        }

        val attachResult = runCatching { wm.addView(wrapper, layoutParams) }
        if (attachResult.isFailure) {
            debugWarn("showInteractiveOverlay: wm.addView failed tx=${payload.transactionId}: ${attachResult.exceptionOrNull()?.message}")
            owner.onDetach()
            overlayComposeOwner = null
            return false
        }

        overlayWrapper = wrapper
        overlayLayoutParams = layoutParams

        triggerPreviewConversion(state, loaded.settings)
        startCountdownIfNeeded(state, payload.autoConfirmSeconds)
        return true
    }

    private fun releaseImeFocus() {
        val wrapper = overlayWrapper
        activeUiState?.imeFocused = false
        if (wrapper != null) {
            runCatching {
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.hideSoftInputFromWindow(wrapper.windowToken, 0)
                wrapper.clearFocus()
            }
        }
        setWindowFocusable(false)
    }

    @Suppress("DEPRECATION")
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
            WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE or WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
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

    private fun pauseCountdownForUserInteraction(state: FloatingEditorUiState) {
        if (state.countdownPaused) return
        state.countdownPaused = true
        countdownJob?.cancel()
    }

    private fun startCountdownIfNeeded(state: FloatingEditorUiState, seconds: Int) {
        countdownJob?.cancel()
        if (state.isPreviewMode || seconds <= 0 || state.countdownPaused) return

        countdownJob = serviceScope.launch {
            for (remaining in seconds downTo 1) {
                if (state.countdownPaused) return@launch
                state.remainingSeconds = remaining
                delay(1_000L)
            }
            if (state.countdownPaused) return@launch
            state.remainingSeconds = 0
            withContext(Dispatchers.IO) {
                CaptureRuntimeHelper.ledgerServiceOrNull(applicationContext)?.confirmTransaction(state.txId)
            }
            stopSelfCleanly()
        }
    }

    private fun triggerPreviewConversion(state: FloatingEditorUiState, settings: AppSettings) {
        if (state.accountId <= 0L) return
        val selectedAcc = state.accounts.firstOrNull { it.id == state.accountId }
        if (selectedAcc != null && state.origCurrency.equals(selectedAcc.currencyCode, ignoreCase = true)) {
            state.previewAccountAmountMicros = state.origAmountMicros
            state.previewAccountCurrency = selectedAcc.currencyCode
            state.previewRateToAccountMicros = 1_000_000L
            return
        }
        if (state.preserveInitialRate &&
            state.initialAccountAmountMicros > 0L &&
            selectedAcc != null &&
            state.initialAccountCurrency.equals(selectedAcc.currencyCode, ignoreCase = true)
        ) {
            state.previewAccountAmountMicros = state.initialAccountAmountMicros
            state.previewAccountCurrency = state.initialAccountCurrency
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
                        baseCurrency = settings.baseCurrency
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
            }
        }
    }

    private suspend fun createCategoryInOverlay(
        state: FloatingEditorUiState,
        entity: CategoryEntity
    ): Long? {
        val ledgerService = CaptureRuntimeHelper.ledgerServiceOrNull(applicationContext) ?: return null
        val savedId = withContext(Dispatchers.IO) {
            runCatching { ledgerService.saveCategory(entity) }.getOrNull()
        } ?: return null
        if (savedId > 0L) {
            val savedEntity = entity.copy(id = savedId)
            state.categories = (state.categories.filterNot { it.id == savedId } + savedEntity)
                .sortedWith(compareBy<CategoryEntity> { it.sortOrder }.thenBy { it.id })
            Toast.makeText(applicationContext, "已新建分类「${entity.name}」", Toast.LENGTH_SHORT).show()
        }
        return savedId
    }

    private suspend fun createTagInOverlay(
        state: FloatingEditorUiState,
        name: String,
        groupName: String,
        colorHex: Long,
        icon: String
    ) {
        val ledgerService = CaptureRuntimeHelper.ledgerServiceOrNull(applicationContext) ?: return
        val nextSort = (state.availableTags.maxOfOrNull { it.sortOrder } ?: 280) + 10
        val newTag = TagEntity(
            name = name.trim(),
            groupName = groupName,
            colorHex = colorHex,
            sortOrder = nextSort,
            isSystem = false,
            icon = icon
        )
        val savedId = withContext(Dispatchers.IO) {
            runCatching { ledgerService.saveTag(newTag) }.getOrNull()
        } ?: 0L
        if (savedId > 0L) {
            val persisted = newTag.copy(id = savedId)
            state.availableTags = (state.availableTags.filterNot {
                it.id == savedId || it.name.equals(persisted.name, ignoreCase = true)
            } + persisted).sortedWith(compareBy<TagEntity> { it.sortOrder }.thenBy { it.id })
            Toast.makeText(applicationContext, "已新建标签「${persisted.name}」", Toast.LENGTH_SHORT).show()
        }
    }

    private fun performQuickConfirmAndDismiss(state: FloatingEditorUiState) {
        countdownJob?.cancel()
        if (state.isPreviewMode) {
            Toast.makeText(applicationContext, "预览演示：已模拟确认入账（未写入真实账单）", Toast.LENGTH_SHORT).show()
            stopSelfCleanly()
            return
        }
        serviceScope.launch {
            withContext(Dispatchers.IO) {
                CaptureRuntimeHelper.ledgerServiceOrNull(applicationContext)?.confirmTransaction(state.txId)
            }
            stopSelfCleanly()
        }
    }

    private fun performDeleteAndDismiss(state: FloatingEditorUiState) {
        countdownJob?.cancel()
        if (state.isPreviewMode) {
            stopSelfCleanly()
            return
        }
        serviceScope.launch {
            withContext(Dispatchers.IO) {
                CaptureRuntimeHelper.ledgerServiceOrNull(applicationContext)?.deleteTransaction(state.txId)
            }
            stopSelfCleanly()
        }
    }

    private fun performSaveConfirmAndDismiss(state: FloatingEditorUiState) {
        countdownJob?.cancel()
        if (state.isPreviewMode) {
            Toast.makeText(applicationContext, "预览演示：已模拟保存修改（未写入真实账单）", Toast.LENGTH_SHORT).show()
            stopSelfCleanly()
            return
        }
        serviceScope.launch {
            val accounts = state.accounts
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
                val preservedManualRate = if (
                    preservedManualAmount == null &&
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
                    reimburseStatus = if (state.type == TransactionType.EXPENSE) state.reimburseStatus else ReimburseStatus.NONE,
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

    private fun removeOverlayView() {
        val wrapper = overlayWrapper
        if (wrapper != null) {
            runCatching { windowManager?.removeViewImmediate(wrapper) }
        }
        overlayComposeOwner?.onDetach()
        overlayComposeOwner = null
        overlayWrapper = null
        overlayLayoutParams = null
        activeUiState = null
        windowFocusableForIme = false
    }

    private fun stopSelfCleanly() {
        removeOverlayView()
        runCatching { ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE) }
        stopSelf()
    }

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
        val sourceBadge: String = "",
        val isPreviewMode: Boolean = false
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
        private const val EXTRA_PREVIEW_MODE = "extra_preview_mode"

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

        fun showPreviewOverlay(context: Context): Boolean {
            val appContext = context.applicationContext
            if (!Settings.canDrawOverlays(appContext)) return false
            CaptureRuntimeHelper.ensureNotificationChannels(appContext)
            val intent = Intent(appContext, FloatingConfirmService::class.java).apply {
                putExtra(EXTRA_PREVIEW_MODE, true)
                putExtra(EXTRA_TX_ID, -1L)
                putExtra(EXTRA_TYPE, TransactionType.EXPENSE)
                putExtra(EXTRA_AMOUNT_MICROS, 368_000L)
                putExtra(EXTRA_CURRENCY, CurrencyCatalog.DEFAULT_BASE_CURRENCY)
                putExtra(EXTRA_MERCHANT, "瑞幸咖啡")
                putExtra(EXTRA_CATEGORY, "咖啡茶饮")
                putExtra(EXTRA_ACCOUNT_NAME, "微信零钱")
                putExtra(EXTRA_AUTO_CONFIRM_SECONDS, 0)
                putExtra(EXTRA_SOURCE_BADGE, "弹窗预览")
            }
            return runCatching {
                appContext.startService(intent)
                true
            }.getOrElse {
                runCatching {
                    intent.putExtra(EXTRA_AS_FOREGROUND, true)
                    ContextCompat.startForegroundService(appContext, intent)
                    true
                }.getOrDefault(false)
            }
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

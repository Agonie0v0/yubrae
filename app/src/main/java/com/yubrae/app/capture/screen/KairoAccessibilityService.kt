package com.yubrae.app.capture.screen

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.yubrae.app.AppContainer
import com.yubrae.app.BuildConfig
import com.yubrae.app.capture.CaptureRuntimeHelper
import com.yubrae.app.capture.normalizeMerchantForMatch
import com.yubrae.app.core.model.MoneyAmount
import com.yubrae.app.data.db.AccountAliasEntity
import com.yubrae.app.data.db.AccountEntity
import com.yubrae.app.data.db.CategoryEntity
import com.yubrae.app.data.prefs.AppSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * 无障碍屏幕账单页与支付成功页监听服务：
 * - 入口 O(1) 检查包名白名单，非目标应用零 IPC / 零内存损耗立即返回；
 * - 针对微信 MMWebViewUI / 支付宝 H5 异步加载采用多点渐进重试扫描；
 * - 解除 WebView 容器剪枝并支持 windows 多窗口兜底遍历（上限 2500 节点 / 深度 48）；
 * - 遵循 SettingsStore 开关与内存 200 条 LRU 页面防重缓存，命中后交由 CaptureOrchestrator 落账并唤起悬浮窗。
 */
class KairoAccessibilityService : AccessibilityService() {

    private enum class ScanAttemptOutcome {
        DISPATCHED_OR_CACHED,
        EXPLICITLY_IGNORED,
        NOT_READY_OR_UNKNOWN
    }

    private data class SnapshotCaptureResult(
        val snapshot: ScreenSnapshot?,
        val failReason: String = "",
        val companionStage0Context: PendingCheckoutContext? = null
    )

    private data class EvaluatedSnapshot(
        val snapshot: ScreenSnapshot,
        val score: Int,
        val pendingContext: PendingCheckoutContext?
    )

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val screenBillParser = ScreenBillParser()
    private val checkoutSessionTracker = CheckoutSessionTracker()

    private var scanJob: Job? = null

    @Volatile
    private var latestWindowPackage: String = ""

    @Volatile
    private var latestWindowClassName: String = ""

    @Volatile
    private var latestWindowId: Int = -1

    @Volatile
    private var activeScanPackage: String = ""

    @Volatile
    private var dirtyContentWhileScanning: Boolean = false

    @Volatile
    private var cachedContainer: AppContainer? = null

    private val cacheLock = Any()
    private val recentPageCache = object : LinkedHashMap<String, Long>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?): Boolean =
            size > MAX_LRU_ENTRIES
    }
    private val recentDiagCache = object : LinkedHashMap<String, Long>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?): Boolean =
            size > MAX_DIAG_CACHE_ENTRIES
    }
    private var lastActivePackage: String = ""
    private var lastActiveDedupKey: String = ""
    private var lastActiveRawKey: String = ""
    private var lastActiveRawAt: Long = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        cachedContainer = CaptureRuntimeHelper.containerOrNull(applicationContext)
        checkoutSessionTracker.reset()
        latestWindowPackage = ""
        latestWindowClassName = ""
        latestWindowId = -1
        synchronized(cacheLock) {
            recentDiagCache.clear()
            lastActivePackage = ""
            lastActiveDedupKey = ""
            lastActiveRawKey = ""
            lastActiveRawAt = 0L
        }
        activeScanPackage = ""
        dirtyContentWhileScanning = false
        if (BuildConfig.DEBUG) {
            Log.i(TAG, "onServiceConnected: bound pkg=$packageName")
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val eventType = event.eventType
        if (eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            eventType != AccessibilityEvent.TYPE_VIEW_SCROLLED
        ) {
            return
        }

        val pkg = event.packageName?.toString()?.trim().orEmpty()
        if (pkg.isEmpty()) return
        if (pkg == packageName) {
            return
        }
        if (!isWatchedScreenPackage(pkg)) {
            if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
                !pkg.contains("systemui") &&
                !pkg.contains("inputmethod") &&
                !pkg.contains("keyboard")
            ) {
                val cls = event.className?.toString()?.trim().orEmpty()
                if (isWindowClassCandidate(cls)) {
                    latestWindowPackage = ""
                    latestWindowClassName = ""
                    latestWindowId = -1
                    synchronized(cacheLock) {
                        lastActivePackage = ""
                        lastActiveDedupKey = ""
                        lastActiveRawKey = ""
                        lastActiveRawAt = 0L
                    }
                    activeScanPackage = ""
                    dirtyContentWhileScanning = false
                }
            }
            return
        }

        val prevWindowId = latestWindowId
        val eventWindowId = event.windowId
        val pkgChanged = latestWindowPackage != pkg
        val windowChanged = eventWindowId != -1 && eventWindowId != prevWindowId
        if (pkgChanged) {
            latestWindowPackage = pkg
            latestWindowClassName = ""
            latestWindowId = eventWindowId
        } else if (windowChanged) {
            latestWindowId = eventWindowId
            latestWindowClassName = ""
        }

        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val cls = event.className?.toString()?.trim().orEmpty()
            if (isWindowClassCandidate(cls)) {
                latestWindowClassName = cls
            } else if (windowChanged || isFullScreenContainerSwitch(event, cls)) {
                latestWindowClassName = ""
            }
            if (ScreenBillParser.isBlockedChatOrFeedWindow(pkg, latestWindowClassName)) {
                debugLog("event=WINDOW_STATE blocked pkg=$pkg cls=$latestWindowClassName")
                scanJob?.cancel()
                synchronized(cacheLock) {
                    if (lastActivePackage == pkg) {
                        lastActivePackage = ""
                        lastActiveDedupKey = ""
                        lastActiveRawKey = ""
                        lastActiveRawAt = 0L
                    }
                }
                activeScanPackage = ""
                dirtyContentWhileScanning = false
                return
            }
        } else if (ScreenBillParser.isBlockedChatOrFeedWindow(pkg, latestWindowClassName)) {
            return
        }

        val retryDelays = if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            debugLog("event=WINDOW_STATE pkg=$pkg winId=$latestWindowId cls=${event.className} tracked=$latestWindowClassName")
            WINDOW_STATE_RETRY_DELAYS
        } else {
            // 正在针对同一应用等待/扫描时不再 cancel 重建协程，防止 XWeb 高频内容刷新饿死扫描
            if (scanJob?.isActive == true && activeScanPackage == pkg) {
                dirtyContentWhileScanning = true
                return
            }
            CONTENT_OR_SCROLL_RETRY_DELAYS
        }

        scanJob?.cancel()
        activeScanPackage = pkg
        dirtyContentWhileScanning = false
        scanJob = serviceScope.launch {
            var lastOutcome = ScanAttemptOutcome.NOT_READY_OR_UNKNOWN
            for ((attemptIdx, waitMs) in retryDelays.withIndex()) {
                delay(waitMs)
                val isLastScheduled = attemptIdx == retryDelays.lastIndex
                lastOutcome = processActiveWindow(
                    expectedPackage = pkg,
                    attempt = attemptIdx + 1,
                    isFinalAttempt = isLastScheduled && !dirtyContentWhileScanning
                )
                if (lastOutcome != ScanAttemptOutcome.NOT_READY_OR_UNKNOWN) {
                    break
                }
            }
            if (lastOutcome == ScanAttemptOutcome.NOT_READY_OR_UNKNOWN && dirtyContentWhileScanning) {
                dirtyContentWhileScanning = false
                delay(TRAILING_RESCAN_DELAY_MS)
                processActiveWindow(
                    expectedPackage = pkg,
                    attempt = retryDelays.size + 1,
                    isFinalAttempt = true
                )
            }
        }
    }

    override fun onInterrupt() {
        scanJob?.cancel()
    }

    override fun onDestroy() {
        scanJob?.cancel()
        checkoutSessionTracker.reset()
        serviceScope.cancel()
        super.onDestroy()
    }

    private suspend fun processActiveWindow(
        expectedPackage: String,
        attempt: Int,
        isFinalAttempt: Boolean = false
    ): ScanAttemptOutcome {
        val container = CaptureRuntimeHelper.containerOrNull(applicationContext)
        if (container == null) {
            debugLog("attempt=$attempt pkg=$expectedPackage -> container null")
            return ScanAttemptOutcome.NOT_READY_OR_UNKNOWN
        }
        val settings = runCatching { container.settingsStore.getSnapshot() }.getOrNull()
        if (settings != null && !settings.accessibilityCaptureEnabled) {
            debugLog("attempt=$attempt pkg=$expectedPackage -> accessibilityCaptureEnabled=false")
            return ScanAttemptOutcome.EXPLICITLY_IGNORED
        }

        val captureResult = captureActiveSnapshot(expectedPackage, container)
        val snapshot = captureResult.snapshot
        if (snapshot == null) {
            debugLog("attempt=$attempt pkg=$expectedPackage -> snapshot null (${captureResult.failReason})")
            if (isFinalAttempt && settings?.accessibilityDiagnosticCaptureEnabled == true) {
                dispatchEmptySnapshotDiagnosticLog(
                    expectedPackage = expectedPackage,
                    failReason = captureResult.failReason,
                    capturedAtMillis = System.currentTimeMillis()
                )
            }
            return ScanAttemptOutcome.NOT_READY_OR_UNKNOWN
        }
        if (ScreenBillParser.isBlockedChatOrFeedWindow(snapshot.packageName, snapshot.className)) {
            debugLog("attempt=$attempt pkg=${snapshot.packageName} cls=${snapshot.className} -> blocked chat/feed window")
            return ScanAttemptOutcome.EXPLICITLY_IGNORED
        }

        // 1. 先更新「提交订单 / 收银台」状态机（绝不在收银台阶段落账）
        val isExplicitUnpaidOrFailed = screenBillParser.isExplicitUnpaidOrFailedScreen(snapshot)
        var recordedPendingCheckout: PendingCheckoutContext? = null
        if (isExplicitUnpaidOrFailed) {
            checkoutSessionTracker.markExplicitCancelOrUnpaid(snapshot.capturedAtMillis)
            debugLog("attempt=$attempt pkg=${snapshot.packageName} -> explicit unpaid/failed, cleared pending checkout")
        } else {
            val extractedPending = screenBillParser.extractPendingCheckout(snapshot)
            if (extractedPending != null) {
                if (extractedPending.isCashierStage && captureResult.companionStage0Context != null) {
                    checkoutSessionTracker.recordCheckout(captureResult.companionStage0Context)
                }
                checkoutSessionTracker.recordCheckout(extractedPending)
                recordedPendingCheckout = extractedPending
                debugLog(
                    "attempt=$attempt pkg=${snapshot.packageName} -> recorded pending checkout " +
                        "stage=${if (extractedPending.isCashierStage) 1 else 0} " +
                        "amount=${extractedPending.origAmountMicros} merchant=${extractedPending.merchant} " +
                        "pay=${extractedPending.paymentMethodText}"
                )
            }
        }

        val quickKind = screenBillParser.classifyPage(snapshot)
        val hasActivePending = checkoutSessionTracker.hasActivePendingCheckout(snapshot.capturedAtMillis)
        val pendingCompletionSignal = if (hasActivePending) {
            screenBillParser.detectCheckoutCompletionSignal(snapshot)
        } else {
            null
        }
        val canTryJsOcr = container.cloudRuleRepository.isWatchedOcrPackage(snapshot.packageName) &&
            settings?.jsRulesEnabled != false &&
            settings?.jsOcrFallbackEnabled != false

        if (quickKind == ScreenPageKind.UNKNOWN && pendingCompletionSignal == null && !canTryJsOcr) {
            val sample = snapshot.texts.take(8).joinToString(" | ") { it.take(24) }
            debugLog(
                "attempt=$attempt pkg=${snapshot.packageName} cls=${snapshot.className} " +
                    "lines=${snapshot.texts.size} -> UNKNOWN sample=[$sample]"
            )
            if (isFinalAttempt) {
                recordFinalDiagnosticLogsIfNeeded(
                    snapshot = snapshot,
                    settings = settings,
                    outcome = null,
                    recordedPendingCheckout = recordedPendingCheckout,
                    isExplicitUnpaidOrFailed = isExplicitUnpaidOrFailed
                )
            }
            clearLastActiveTracking(snapshot.packageName)
            return ScanAttemptOutcome.NOT_READY_OR_UNKNOWN
        }
        if (quickKind != ScreenPageKind.UNKNOWN &&
            !isPageKindEnabled(quickKind, settings) &&
            (pendingCompletionSignal == null || !isPageKindEnabled(pendingCompletionSignal.pageKind, settings))
        ) {
            debugLog("attempt=$attempt pkg=${snapshot.packageName} -> $quickKind disabled")
            return ScanAttemptOutcome.EXPLICITLY_IGNORED
        }

        val effectiveQuickKind = if (quickKind != ScreenPageKind.UNKNOWN) {
            quickKind
        } else {
            pendingCompletionSignal?.pageKind ?: ScreenPageKind.UNKNOWN
        }
        val rawKey = "${snapshot.packageName}|${effectiveQuickKind.name}|${snapshot.texts.joinToString("\u0001")}"
        val rawCooldownMillis = if (effectiveQuickKind == ScreenPageKind.PAY_SUCCESS) {
            PAY_SUCCESS_COOLDOWN_MILLIS
        } else {
            SAME_PAGE_COOLDOWN_MILLIS
        }
        if (pendingCompletionSignal == null) {
            synchronized(cacheLock) {
                if (lastActivePackage == snapshot.packageName &&
                    lastActiveRawKey == rawKey &&
                    (snapshot.capturedAtMillis - lastActiveRawAt) < rawCooldownMillis
                ) {
                    debugLog("attempt=$attempt pkg=${snapshot.packageName} kind=$effectiveQuickKind -> RAW_CACHED")
                    return ScanAttemptOutcome.DISPATCHED_OR_CACHED
                }
            }
        }

        val db = container.database
        val allAccounts = runCatching { db.accountDao().getAll() }.getOrDefault(emptyList())
        val activeAccounts = allAccounts.filter { !it.archived }.ifEmpty { allAccounts }
        val aliases = runCatching { db.accountAliasDao().getEnabled() }.getOrDefault(emptyList())
        val categories = runCatching { db.categoryDao().getAll() }.getOrDefault(emptyList())

        var outcome = if (quickKind == ScreenPageKind.UNKNOWN || !isPageKindEnabled(quickKind, settings)) {
            ScreenParseOutcome(pageKind = ScreenPageKind.UNKNOWN, draft = null)
        } else {
            screenBillParser.parse(
                snapshot = snapshot,
                aliases = aliases,
                accounts = activeAccounts,
                categories = categories
            )
        }

        if (outcome.pageKind == ScreenPageKind.UNKNOWN &&
            outcome.ignoredReason.isBlank() &&
            canTryJsOcr
        ) {
            tryBuildJsOcrFallbackOutcome(
                snapshot = snapshot,
                container = container,
                settings = settings,
                aliases = aliases,
                accounts = activeAccounts,
                categories = categories
            )?.let { ocrOutcome ->
                outcome = ocrOutcome
            }
        }

        // 2. 单帧直捕命中则清空收银台状态机；未产出 draft 且命中支付/履约确认信号则用收银台上下文合成完整账单
        val directDraft = outcome.draft
        if (directDraft != null && directDraft.origAmountMicros > 0L) {
            checkoutSessionTracker.clearOnDirectCapture(directDraft.origAmountMicros)
        } else if ((outcome.pageKind == ScreenPageKind.UNKNOWN || outcome.draft == null) &&
            outcome.ignoredReason.isBlank() &&
            pendingCompletionSignal != null
        ) {
            if (!isPageKindEnabled(pendingCompletionSignal.pageKind, settings)) {
                debugLog(
                    "attempt=$attempt pkg=${snapshot.packageName} -> checkout completion " +
                        "${pendingCompletionSignal.pageKind} disabled"
                )
                return ScanAttemptOutcome.EXPLICITLY_IGNORED
            }
            val confirmedOutcome = checkoutSessionTracker.consumeIfConfirmed(
                snapshot = snapshot,
                signal = pendingCompletionSignal,
                parser = screenBillParser,
                aliases = aliases,
                accounts = activeAccounts,
                categories = categories
            )
            if (confirmedOutcome != null) {
                debugLog(
                    "attempt=$attempt pkg=${snapshot.packageName} kind=${confirmedOutcome.pageKind} " +
                        "amount=${confirmedOutcome.draft?.origAmountMicros} merchant=${confirmedOutcome.draft?.merchant} " +
                        "-> CHECKOUT_CONFIRMED"
                )
                outcome = confirmedOutcome
            }
        }

        if (outcome.pageKind == ScreenPageKind.UNKNOWN) {
            if (isFinalAttempt) {
                recordFinalDiagnosticLogsIfNeeded(
                    snapshot = snapshot,
                    settings = settings,
                    outcome = outcome,
                    recordedPendingCheckout = recordedPendingCheckout,
                    isExplicitUnpaidOrFailed = isExplicitUnpaidOrFailed
                )
            }
            if (quickKind == ScreenPageKind.UNKNOWN) {
                clearLastActiveTracking(snapshot.packageName)
            }
            debugLog("attempt=$attempt pkg=${snapshot.packageName} -> parse UNKNOWN (${outcome.ignoredReason})")
            return ScanAttemptOutcome.NOT_READY_OR_UNKNOWN
        }
        if (!isPageKindEnabled(outcome.pageKind, settings)) {
            debugLog("attempt=$attempt pkg=${snapshot.packageName} -> ${outcome.pageKind} disabled (post-parse)")
            return ScanAttemptOutcome.EXPLICITLY_IGNORED
        }
        if (outcome.ignoredReason.isNotBlank() && outcome.draft == null && !outcome.shouldFallbackToLlm) {
            debugLog("attempt=$attempt pkg=${snapshot.packageName} -> IGNORED: ${outcome.ignoredReason}")
            if (outcome.ignoredReason != "聊天或服务号对话框不触发") {
                recordExplicitlyIgnoredDiagnosticIfNeeded(
                    snapshot = snapshot,
                    settings = settings,
                    outcome = outcome
                )
            }
            return ScanAttemptOutcome.EXPLICITLY_IGNORED
        }
        if (outcome.draft == null && !outcome.shouldFallbackToLlm) {
            debugLog("attempt=$attempt pkg=${snapshot.packageName} kind=${outcome.pageKind} -> draft null")
            if (isFinalAttempt) {
                recordFinalDiagnosticLogsIfNeeded(
                    snapshot = snapshot,
                    settings = settings,
                    outcome = outcome,
                    recordedPendingCheckout = recordedPendingCheckout,
                    isExplicitUnpaidOrFailed = isExplicitUnpaidOrFailed
                )
            }
            return ScanAttemptOutcome.NOT_READY_OR_UNKNOWN
        }

        val dedupKey = buildServiceDedupKey(snapshot, outcome)
        val cooldownMillis = if (outcome.pageKind == ScreenPageKind.PAY_SUCCESS) {
            PAY_SUCCESS_COOLDOWN_MILLIS
        } else {
            SAME_PAGE_COOLDOWN_MILLIS
        }

        if (!tryAcquireDispatchSlot(snapshot.packageName, dedupKey, rawKey, snapshot.capturedAtMillis, cooldownMillis)) {
            debugLog("attempt=$attempt pkg=${snapshot.packageName} kind=${outcome.pageKind} -> CACHED($dedupKey)")
            return ScanAttemptOutcome.DISPATCHED_OR_CACHED
        }

        debugLog(
            "attempt=$attempt pkg=${snapshot.packageName} kind=${outcome.pageKind} " +
                "lines=${snapshot.texts.size} order=${outcome.orderNo} -> DISPATCH"
        )
        val dispatchPkg = snapshot.packageName
        // 使用独立协程执行落账与悬浮窗唤起，避免后续 scanJob 取消导致数据库事务或弹窗被中断
        serviceScope.launch {
            try {
                val res = CaptureRuntimeHelper.orchestratorOrNull(applicationContext)
                    ?.handleScreenCapture(snapshot, outcome)
                debugLog(
                    "dispatch result: pkg=$dispatchPkg status=${res?.status} " +
                        "tx=${res?.transactionId} merged=${res?.mergedIntoExisting} msg=${res?.message}"
                )
            } catch (ce: CancellationException) {
                releaseDispatchSlot(dispatchPkg, dedupKey)
                throw ce
            } catch (t: Throwable) {
                debugLog("dispatch error: pkg=$dispatchPkg err=${t.message}")
                releaseDispatchSlot(dispatchPkg, dedupKey)
            }
        }
        return ScanAttemptOutcome.DISPATCHED_OR_CACHED
    }

    private fun cachedContainerOrNull(): AppContainer? {
        cachedContainer?.let { return it }
        return CaptureRuntimeHelper.containerOrNull(applicationContext)?.also { cachedContainer = it }
    }

    private fun isWatchedScreenPackage(
        pkg: String,
        container: AppContainer? = cachedContainerOrNull()
    ): Boolean {
        if (pkg.isEmpty() || pkg == packageName) return false
        if (ScreenBillParser.isWatchedPackage(pkg)) return true
        return container?.cloudRuleRepository?.isWatchedOcrPackage(pkg) == true
    }

    private fun clearLastActiveTracking(pkg: String) {
        synchronized(cacheLock) {
            if (lastActivePackage == pkg) {
                lastActivePackage = ""
                lastActiveDedupKey = ""
                lastActiveRawKey = ""
                lastActiveRawAt = 0L
            }
        }
    }

    private fun tryBuildJsOcrFallbackOutcome(
        snapshot: ScreenSnapshot,
        container: AppContainer,
        settings: AppSettings?,
        aliases: List<AccountAliasEntity>,
        accounts: List<AccountEntity>,
        categories: List<CategoryEntity>
    ): ScreenParseOutcome? {
        if (!container.cloudRuleRepository.isWatchedOcrPackage(snapshot.packageName)) return null
        if (settings?.jsRulesEnabled == false || settings?.jsOcrFallbackEnabled == false) return null

        val screenText = snapshot.texts.joinToString("\n").trim()
        if (screenText.isBlank()) return null

        val ocrDraft = runCatching {
            container.cloudRuleRepository.ruleEngine.parseScreenOcr(
                packageName = snapshot.packageName,
                screenText = screenText,
                timestampMillis = snapshot.capturedAtMillis,
                aliases = aliases,
                accounts = accounts,
                categories = categories,
                enableCategoryJs = settings?.jsCategoryFallbackEnabled != false
            )
        }.getOrNull() ?: return null

        val orderNo = ocrDraft.externalOrderNo.trim()
        val dedupFingerprint = if (orderNo.isNotBlank()) {
            "ORDER:$orderNo"
        } else {
            "JS_OCR:${snapshot.packageName}:${ocrDraft.type}:${ocrDraft.origCurrency}:${ocrDraft.origAmountMicros}:${ocrDraft.timestamp / 60_000L}:${ocrDraft.merchant}"
        }
        return ScreenParseOutcome(
            pageKind = ScreenPageKind.BILL_DETAIL,
            draft = ocrDraft,
            orderNo = orderNo,
            dedupFingerprint = dedupFingerprint,
            summaryText = screenText.take(1500),
            shouldFallbackToLlm = false,
            ignoredReason = ""
        )
    }

    private fun isPageKindEnabled(pageKind: ScreenPageKind, settings: AppSettings?): Boolean {
        if (settings == null) return true
        return when (pageKind) {
            ScreenPageKind.PAY_SUCCESS -> settings.accessibilityPaySuccessEnabled
            ScreenPageKind.BILL_DETAIL -> settings.accessibilityBillDetailEnabled
            ScreenPageKind.ORDER_DETAIL -> settings.accessibilityOrderDetailEnabled
            ScreenPageKind.UNKNOWN -> true
        }
    }

    private fun recordFinalDiagnosticLogsIfNeeded(
        snapshot: ScreenSnapshot,
        settings: AppSettings?,
        outcome: ScreenParseOutcome?,
        recordedPendingCheckout: PendingCheckoutContext?,
        isExplicitUnpaidOrFailed: Boolean
    ) {
        val baseReason = outcome?.ignoredReason.orEmpty().trim()
        if (baseReason == "聊天或服务号对话框不触发") return

        val debugCaptureEnabled = settings?.accessibilityDiagnosticCaptureEnabled == true
        val probeReason = if (baseReason.isNotBlank()) {
            baseReason
        } else if (debugCaptureEnabled) {
            runCatching { screenBillParser.parse(snapshot).ignoredReason.trim() }.getOrDefault("")
        } else {
            ""
        }
        if (probeReason == "聊天或服务号对话框不触发") return

        val resolvedReason = when {
            isExplicitUnpaidOrFailed -> "待付款/支付失败/已取消页面，不触发记账"
            recordedPendingCheckout != null -> if (recordedPendingCheckout.isCashierStage) {
                "收银台待确认上下文已缓存(Stage1)，等待支付完成确认"
            } else {
                "提交/确认订单上下文已缓存(Stage0)，等待支付完成确认"
            }
            probeReason.isNotBlank() -> probeReason
            outcome != null && outcome.pageKind == ScreenPageKind.BILL_DETAIL && outcome.draft == null ->
                "账单详情未提取到有效金额且无法兜底"
            screenBillParser.hasStrongBillDetailAnchors(snapshot) ->
                "含账单详情锚点但未识别出有效状态或金额"
            else -> "未命中支付或订单识别规则"
        }

        if (debugCaptureEnabled) {
            dispatchDebugDiagnosticLog(
                snapshot = snapshot,
                reason = resolvedReason,
                baseOutcome = outcome
            )
            return
        }

        if (screenBillParser.shouldRecordDiagnosticLog(snapshot, probeReason)) {
            val diagPageKind = if ((outcome?.pageKind == ScreenPageKind.BILL_DETAIL ||
                    screenBillParser.hasStrongBillDetailAnchors(snapshot)) &&
                settings?.accessibilityBillDetailEnabled != false
            ) {
                ScreenPageKind.BILL_DETAIL
            } else {
                ScreenPageKind.UNKNOWN
            }
            dispatchDiagnosticIgnoredLog(
                snapshot = snapshot,
                reason = resolvedReason,
                pageKind = diagPageKind,
                baseOutcome = outcome
            )
        }
    }

    private fun recordExplicitlyIgnoredDiagnosticIfNeeded(
        snapshot: ScreenSnapshot,
        settings: AppSettings?,
        outcome: ScreenParseOutcome
    ) {
        if (settings?.accessibilityDiagnosticCaptureEnabled == true) {
            dispatchDebugDiagnosticLog(
                snapshot = snapshot,
                reason = outcome.ignoredReason,
                baseOutcome = outcome
            )
            return
        }
        val shouldRecordBill = outcome.pageKind == ScreenPageKind.BILL_DETAIL &&
            settings?.accessibilityBillDetailEnabled != false
        if (shouldRecordBill || screenBillParser.shouldRecordDiagnosticLog(snapshot, outcome.ignoredReason)) {
            val diagPageKind = if (shouldRecordBill) ScreenPageKind.BILL_DETAIL else ScreenPageKind.UNKNOWN
            dispatchDiagnosticIgnoredLog(
                snapshot = snapshot,
                reason = outcome.ignoredReason,
                pageKind = diagPageKind,
                baseOutcome = outcome
            )
        }
    }

    private fun dispatchDebugDiagnosticLog(
        snapshot: ScreenSnapshot,
        reason: String,
        baseOutcome: ScreenParseOutcome? = null
    ) {
        val clsLabel = snapshot.className.ifBlank { "<empty>" }
        val cleanReason = reason.trim().ifBlank { "页面未命中记账规则" }
        val visStats = formatVisibilityStats(snapshot)
        val pendingSummary = formatPendingCheckoutSummary(snapshot.capturedAtMillis)
        val body = buildDiagnosticBody(
            snapshot = snapshot,
            fallbackSummary = baseOutcome?.summaryText.orEmpty(),
            includeHiddenPreview = true
        )
        val diagSummary = buildString {
            append("[diag-debug pkg=").append(snapshot.packageName)
                .append(" cls=").append(clsLabel)
                .append(" vis=").append(visStats)
                .append(" pending=").append(pendingSummary)
                .append(" reason=").append(cleanReason)
                .append("]\n")
            append(body)
        }.take(1200)
        val diagOutcome = ScreenParseOutcome(
            pageKind = ScreenPageKind.UNKNOWN,
            draft = null,
            orderNo = baseOutcome?.orderNo.orEmpty(),
            dedupFingerprint = "",
            summaryText = diagSummary,
            shouldFallbackToLlm = false,
            ignoredReason = "[diag-debug] $cleanReason (cls=$clsLabel, vis=$visStats, pending=$pendingSummary)"
        )
        val dedupKey = "DIAG_DEBUG:${snapshot.packageName}:$clsLabel:${body.hashCode()}"
        if (!tryAcquireDiagnosticSlot(dedupKey, snapshot.capturedAtMillis, DIAG_DEBUG_COOLDOWN_MILLIS)) {
            debugLog("diag-debug log CACHED($dedupKey)")
            return
        }
        debugLog("diag-debug log DISPATCH pkg=${snapshot.packageName} cls=$clsLabel vis=$visStats pending=$pendingSummary")
        val dispatchPkg = snapshot.packageName
        serviceScope.launch {
            try {
                CaptureRuntimeHelper.orchestratorOrNull(applicationContext)
                    ?.handleScreenCapture(snapshot, diagOutcome)
            } catch (ce: CancellationException) {
                releaseDiagnosticSlot(dedupKey)
                throw ce
            } catch (t: Throwable) {
                debugLog("diag-debug dispatch error: pkg=$dispatchPkg err=${t.message}")
                releaseDiagnosticSlot(dedupKey)
            }
        }
    }

    private fun dispatchEmptySnapshotDiagnosticLog(
        expectedPackage: String,
        failReason: String,
        capturedAtMillis: Long
    ) {
        val cleanFailReason = failReason.trim().ifBlank { "collected=0" }
        val clsLabel = if (latestWindowPackage == expectedPackage && latestWindowClassName.isNotBlank()) {
            latestWindowClassName
        } else {
            "<empty>"
        }
        val pendingSummary = formatPendingCheckoutSummary(capturedAtMillis)
        val dedupKey = "DIAG_EMPTY:$expectedPackage:$clsLabel:$cleanFailReason"
        if (!tryAcquireDiagnosticSlot(dedupKey, capturedAtMillis, DIAG_DEBUG_COOLDOWN_MILLIS)) {
            return
        }
        val diagSummary = buildString {
            append("[diag-debug pkg=").append(expectedPackage)
                .append(" cls=").append(clsLabel)
                .append(" vis=0/0 pending=").append(pendingSummary)
                .append("]\n")
            append("无障碍未采集到有效节点（可能受安全窗口标记或延迟加载限制）：").append(cleanFailReason)
        }.take(1200)
        val emptySnapshot = ScreenSnapshot(
            packageName = expectedPackage,
            className = clsLabel,
            texts = emptyList(),
            capturedAtMillis = capturedAtMillis
        )
        val diagOutcome = ScreenParseOutcome(
            pageKind = ScreenPageKind.UNKNOWN,
            draft = null,
            summaryText = diagSummary,
            shouldFallbackToLlm = false,
            ignoredReason = "[diag-debug] 节点采集为空 ($cleanFailReason, cls=$clsLabel, pending=$pendingSummary)"
        )
        debugLog("diag-empty log DISPATCH pkg=$expectedPackage cls=$clsLabel fail=$cleanFailReason")
        serviceScope.launch {
            try {
                CaptureRuntimeHelper.orchestratorOrNull(applicationContext)
                    ?.handleScreenCapture(emptySnapshot, diagOutcome)
            } catch (ce: CancellationException) {
                releaseDiagnosticSlot(dedupKey)
                throw ce
            } catch (t: Throwable) {
                debugLog("diag-empty dispatch error: pkg=$expectedPackage err=${t.message}")
                releaseDiagnosticSlot(dedupKey)
            }
        }
    }

    private fun dispatchDiagnosticIgnoredLog(
        snapshot: ScreenSnapshot,
        reason: String,
        pageKind: ScreenPageKind,
        baseOutcome: ScreenParseOutcome? = null
    ) {
        val clsLabel = snapshot.className.ifBlank { "<empty>" }
        val cleanReason = reason.trim().ifBlank { "账单或支付页面未触发记账" }
        val visStats = formatVisibilityStats(snapshot)
        val pendingSummary = formatPendingCheckoutSummary(snapshot.capturedAtMillis)
        val fullReason = "$cleanReason (pkg=${snapshot.packageName}, cls=$clsLabel, vis=$visStats, pending=$pendingSummary)"
        val baseSummary = buildDiagnosticBody(
            snapshot = snapshot,
            fallbackSummary = baseOutcome?.summaryText.orEmpty(),
            includeHiddenPreview = false
        )
        val diagSummary = buildString {
            append("[diag pkg=").append(snapshot.packageName)
                .append(" cls=").append(clsLabel)
                .append(" vis=").append(visStats)
                .append(" pending=").append(pendingSummary)
                .append(" reason=").append(cleanReason)
                .append("]\n")
            append(baseSummary)
        }.take(1200)
        val diagOutcome = ScreenParseOutcome(
            pageKind = pageKind,
            draft = null,
            orderNo = baseOutcome?.orderNo.orEmpty(),
            dedupFingerprint = baseOutcome?.dedupFingerprint.orEmpty(),
            summaryText = diagSummary,
            shouldFallbackToLlm = false,
            ignoredReason = fullReason
        )
        val dedupKey = "DIAG:${snapshot.packageName}:${pageKind.name}:$clsLabel:$cleanReason:${baseSummary.hashCode()}"
        if (!tryAcquireDiagnosticSlot(dedupKey, snapshot.capturedAtMillis, SAME_PAGE_COOLDOWN_MILLIS)) {
            debugLog("diagnostic log CACHED($dedupKey)")
            return
        }
        debugLog("diagnostic log DISPATCH pkg=${snapshot.packageName} cls=$clsLabel vis=$visStats pending=$pendingSummary reason=$cleanReason")
        val dispatchPkg = snapshot.packageName
        serviceScope.launch {
            try {
                CaptureRuntimeHelper.orchestratorOrNull(applicationContext)
                    ?.handleScreenCapture(snapshot, diagOutcome)
            } catch (ce: CancellationException) {
                releaseDiagnosticSlot(dedupKey)
                throw ce
            } catch (t: Throwable) {
                debugLog("diagnostic dispatch error: pkg=$dispatchPkg err=${t.message}")
                releaseDiagnosticSlot(dedupKey)
            }
        }
    }

    private fun formatVisibilityStats(snapshot: ScreenSnapshot): String {
        val total = if (snapshot.nodes.isNotEmpty()) snapshot.nodes.size else snapshot.texts.size
        val visible = if (snapshot.nodes.isNotEmpty()) {
            snapshot.nodes.count { it.isVisibleToUser }
        } else {
            snapshot.texts.size
        }
        return "$visible/$total"
    }

    private fun formatPendingCheckoutSummary(nowMillis: Long): String {
        val pending = checkoutSessionTracker.peekActivePendingCheckout(nowMillis) ?: return "none"
        val stage = if (pending.isCashierStage) "S1" else "S0"
        val amt = if (pending.origAmountMicros > 0L) {
            MoneyAmount.formatPlain(pending.origAmountMicros)
        } else {
            "0"
        }
        val merchant = pending.merchant.ifBlank { "-" }.take(18)
        val pay = pending.paymentMethodText.ifBlank { pending.accountHint.ifBlank { "-" } }.take(16)
        val ageSec = (nowMillis - pending.capturedAtMillis).coerceAtLeast(0L) / 1000L
        return "$stage($amt,$merchant,$pay,${ageSec}s)"
    }

    private fun buildDiagnosticBody(
        snapshot: ScreenSnapshot,
        fallbackSummary: String,
        includeHiddenPreview: Boolean
    ): String {
        val visibleLines = if (snapshot.nodes.isNotEmpty()) {
            snapshot.nodes.asSequence()
                .filter { it.isVisibleToUser }
                .map { it.text.trim() }
                .filter { it.isNotEmpty() }
                .toList()
        } else {
            snapshot.texts.asSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toList()
        }
        val primaryBody = if (visibleLines.isNotEmpty()) {
            visibleLines.joinToString("\n")
        } else {
            fallbackSummary.trim().ifBlank {
                snapshot.texts.asSequence()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .joinToString("\n")
            }
        }
        if (!includeHiddenPreview || snapshot.nodes.isEmpty()) {
            return primaryBody
        }
        val hiddenLines = snapshot.nodes.asSequence()
            .filter { !it.isVisibleToUser }
            .map { it.text.trim() }
            .filter { it.isNotEmpty() }
            .take(12)
            .toList()
        if (hiddenLines.isEmpty()) return primaryBody
        return buildString {
            append(primaryBody)
            append("\n[hidden] ")
            append(hiddenLines.joinToString(" | "))
        }
    }

    private fun captureActiveSnapshot(
        expectedPackage: String,
        container: AppContainer
    ): SnapshotCaptureResult {
        val activeRoot = runCatching { rootInActiveWindow }.getOrNull()
        val activePkg = activeRoot?.packageName?.toString()?.trim().orEmpty()
        val windowList = runCatching { windows }.getOrNull().orEmpty()
        val hasActivePending = checkoutSessionTracker.hasActivePendingCheckout(System.currentTimeMillis())

        var primaryWindowId = -1
        var primarySnapshot: ScreenSnapshot? = null
        var primaryBlocked = false
        var bestEval: EvaluatedSnapshot? = null
        var companionStage0: PendingCheckoutContext? = null

        if (activeRoot != null && (activePkg == expectedPackage || isWatchedScreenPackage(activePkg, container))) {
            primaryWindowId = runCatching { activeRoot.windowId }.getOrDefault(-1)
            try {
                primarySnapshot = extractSnapshotFromRoot(activeRoot, expectedPackage, container)
            } finally {
                activeRoot.safeRecycle()
            }
            if (primarySnapshot != null) {
                primaryBlocked = ScreenBillParser.isBlockedChatOrFeedWindow(
                    primarySnapshot.packageName,
                    primarySnapshot.className
                )
                if (!primaryBlocked) {
                    val primaryEval = evaluateSnapshotCandidate(primarySnapshot, hasActivePending)
                    if (primaryEval.score >= SCORE_CLASSIFIED_PAGE) {
                        return SnapshotCaptureResult(snapshot = primaryEval.snapshot)
                    }
                    bestEval = primaryEval
                    if (primaryEval.pendingContext != null && !primaryEval.pendingContext.isCashierStage) {
                        companionStage0 = primaryEval.pendingContext
                    }
                }
            }
        } else {
            activeRoot?.safeRecycle()
        }

        // 多窗口并存选举（如支付宝弹窗浮在淘宝之上、淘宝内嵌 MSP 浮在确认订单之上、微信 :tools/:appbrand）
        val altRoots = collectWatchedWindowRoots(
            expectedPackage = expectedPackage,
            windowList = windowList,
            excludeWindowId = primaryWindowId,
            container = container
        )

        try {
            for (altRoot in altRoots) {
                val altSnapshot = extractSnapshotFromRoot(altRoot, expectedPackage, container) ?: continue
                if (ScreenBillParser.isBlockedChatOrFeedWindow(altSnapshot.packageName, altSnapshot.className)) {
                    continue
                }
                val altEval = evaluateSnapshotCandidate(altSnapshot, hasActivePending)
                if (altEval.pendingContext != null && !altEval.pendingContext.isCashierStage && companionStage0 == null) {
                    companionStage0 = altEval.pendingContext
                }
                if (altEval.score >= SCORE_CLASSIFIED_PAGE) {
                    return SnapshotCaptureResult(snapshot = altEval.snapshot)
                }
                if (bestEval == null || altEval.score > bestEval.score) {
                    bestEval = altEval
                }
            }
        } finally {
            altRoots.forEach { it.safeRecycle() }
        }

        val resolvedSnapshot = bestEval?.snapshot ?: primarySnapshot
        if (resolvedSnapshot != null) {
            val resolvedCompanionStage0 = if (bestEval?.score == SCORE_CASHIER_STAGE1) companionStage0 else null
            return SnapshotCaptureResult(
                snapshot = resolvedSnapshot,
                companionStage0Context = resolvedCompanionStage0
            )
        }
        return SnapshotCaptureResult(
            snapshot = null,
            failReason = "collected=0, activePkg=$activePkg, windows=${windowList.size}"
        )
    }

    private fun evaluateSnapshotCandidate(
        snapshot: ScreenSnapshot,
        hasActivePending: Boolean
    ): EvaluatedSnapshot {
        if (screenBillParser.classifyPage(snapshot) != ScreenPageKind.UNKNOWN) {
            return EvaluatedSnapshot(snapshot, SCORE_CLASSIFIED_PAGE, null)
        }
        if (hasActivePending && screenBillParser.detectCheckoutCompletionSignal(snapshot) != null) {
            return EvaluatedSnapshot(snapshot, SCORE_COMPLETION_SIGNAL, null)
        }
        val pendingCtx = screenBillParser.extractPendingCheckout(snapshot)
        if (pendingCtx != null) {
            val score = if (pendingCtx.isCashierStage) SCORE_CASHIER_STAGE1 else SCORE_CHECKOUT_STAGE0
            return EvaluatedSnapshot(snapshot, score, pendingCtx)
        }
        if (screenBillParser.isExplicitUnpaidOrFailedScreen(snapshot)) {
            return EvaluatedSnapshot(snapshot, SCORE_UNPAID_OR_FAILED, null)
        }
        if (screenBillParser.shouldRecordDiagnosticLog(snapshot)) {
            return EvaluatedSnapshot(snapshot, SCORE_DIAGNOSTIC_CUE, null)
        }
        return EvaluatedSnapshot(snapshot, SCORE_FALLBACK_WATCHED, null)
    }

    private fun extractSnapshotFromRoot(
        root: AccessibilityNodeInfo,
        expectedPackage: String,
        container: AppContainer
    ): ScreenSnapshot? {
        val resolvedPkg = root.packageName?.toString()?.takeIf { it.isNotBlank() } ?: expectedPackage
        if (resolvedPkg == packageName || !isWatchedScreenPackage(resolvedPkg, container)) {
            return null
        }
        val rootWindowId = runCatching { root.windowId }.getOrDefault(-1)
        val rootRect = Rect()
        root.getBoundsInScreen(rootRect)
        val displayMetrics = resources.displayMetrics
        val screenWidthPx = displayMetrics.widthPixels.takeIf { it > 0 } ?: rootRect.width()
        val screenHeightPx = displayMetrics.heightPixels.takeIf { it > 0 } ?: rootRect.height()
        val snapshotScreenHeightPx = rootRect.height().takeIf { it > 0 } ?: screenHeightPx

        val collected = ArrayList<String>(64)
        val collectedNodes = ArrayList<ScreenTextNode>(64)
        val counter = IntArray(1)
        collectVisibleTexts(
            node = root,
            depth = 0,
            visitedCounter = counter,
            out = collected,
            outNodes = collectedNodes,
            rect = rootRect,
            screenWidthPx = screenWidthPx,
            screenHeightPx = screenHeightPx
        )
        if (collected.isEmpty()) return null

        val cls = if (latestWindowPackage == resolvedPkg &&
            latestWindowClassName.isNotBlank() &&
            (latestWindowId == -1 || rootWindowId == latestWindowId)
        ) {
            latestWindowClassName
        } else {
            root.className?.toString().orEmpty()
        }
        return ScreenSnapshot(
            packageName = resolvedPkg,
            className = cls,
            texts = collected,
            capturedAtMillis = System.currentTimeMillis(),
            nodes = collectedNodes,
            screenHeightPx = snapshotScreenHeightPx
        )
    }

    private fun collectWatchedWindowRoots(
        expectedPackage: String,
        windowList: List<AccessibilityWindowInfo>,
        excludeWindowId: Int = -1,
        container: AppContainer
    ): List<AccessibilityNodeInfo> {
        val orderedWindows = windowList.sortedByDescending { runCatching { it.layer }.getOrDefault(0) }
        val exactMatches = ArrayList<AccessibilityNodeInfo>(2)
        val otherMatches = ArrayList<AccessibilityNodeInfo>(2)
        for (window in orderedWindows) {
            if (window.type != AccessibilityWindowInfo.TYPE_APPLICATION) continue
            if (excludeWindowId != -1 && window.id == excludeWindowId) continue
            val root = runCatching { window.root }.getOrNull() ?: continue
            val pkg = root.packageName?.toString()?.trim().orEmpty()
            when {
                pkg == expectedPackage -> exactMatches.add(root)
                isWatchedScreenPackage(pkg, container) -> otherMatches.add(root)
                else -> root.safeRecycle()
            }
        }
        return exactMatches + otherMatches
    }

    private fun collectVisibleTexts(
        node: AccessibilityNodeInfo,
        depth: Int,
        visitedCounter: IntArray,
        out: MutableList<String>,
        outNodes: MutableList<ScreenTextNode>,
        rect: Rect,
        screenWidthPx: Int,
        screenHeightPx: Int
    ) {
        if (depth > MAX_TREE_DEPTH || visitedCounter[0] >= MAX_TREE_NODES) return
        visitedCounter[0]++

        // 仅跳过密码框；WebView/XWeb 容器常把父节点标为 isVisibleToUser=false，绝不能提前剪枝整棵子树
        if (node.isPassword) return

        val nodeText = node.text?.toString()?.trim().orEmpty()
        val nodeDesc = node.contentDescription?.toString()?.trim().orEmpty()
        if (nodeText.isNotEmpty() || nodeDesc.isNotEmpty()) {
            node.getBoundsInScreen(rect)
            val isOffscreenNode = screenWidthPx > 0 &&
                screenHeightPx > 0 &&
                rect.width() > 0 &&
                rect.height() > 0 &&
                (rect.right <= 0 ||
                    rect.left >= screenWidthPx ||
                    rect.bottom <= 0 ||
                    rect.top >= screenHeightPx * 2)
            if (!isOffscreenNode) {
                val visibleToUser = node.isVisibleToUser
                if (nodeText.isNotEmpty()) {
                    val lastNode = outNodes.lastOrNull()
                    if (lastNode != null && lastNode.text == nodeText) {
                        if (!lastNode.isVisibleToUser && visibleToUser) {
                            outNodes[outNodes.lastIndex] = lastNode.copy(
                                topPx = rect.top,
                                heightPx = rect.height().coerceAtLeast(0),
                                isContentDesc = false,
                                isVisibleToUser = true
                            )
                        }
                    } else {
                        out.add(nodeText)
                        outNodes.add(
                            ScreenTextNode(
                                text = nodeText,
                                topPx = rect.top,
                                heightPx = rect.height().coerceAtLeast(0),
                                isContentDesc = false,
                                isVisibleToUser = visibleToUser
                            )
                        )
                    }
                }
                if (nodeDesc.isNotEmpty() && nodeDesc != nodeText) {
                    val lastNode = outNodes.lastOrNull()
                    if (lastNode != null && lastNode.text == nodeDesc) {
                        if (!lastNode.isVisibleToUser && visibleToUser) {
                            outNodes[outNodes.lastIndex] = lastNode.copy(
                                topPx = rect.top,
                                heightPx = rect.height().coerceAtLeast(0),
                                isContentDesc = true,
                                isVisibleToUser = true
                            )
                        }
                    } else {
                        out.add(nodeDesc)
                        outNodes.add(
                            ScreenTextNode(
                                text = nodeDesc,
                                topPx = rect.top,
                                heightPx = rect.height().coerceAtLeast(0),
                                isContentDesc = true,
                                isVisibleToUser = visibleToUser
                            )
                        )
                    }
                }
            }
        }

        val childCount = node.childCount
        for (i in 0 until childCount) {
            if (visitedCounter[0] >= MAX_TREE_NODES) break
            val child = runCatching { node.getChild(i) }.getOrNull() ?: continue
            try {
                collectVisibleTexts(
                    node = child,
                    depth = depth + 1,
                    visitedCounter = visitedCounter,
                    out = out,
                    outNodes = outNodes,
                    rect = rect,
                    screenWidthPx = screenWidthPx,
                    screenHeightPx = screenHeightPx
                )
            } finally {
                child.safeRecycle()
            }
        }
    }

    private fun tryAcquireDispatchSlot(
        pkg: String,
        dedupKey: String,
        rawKey: String,
        nowMillis: Long,
        cooldownMillis: Long
    ): Boolean = synchronized(cacheLock) {
        if (lastActivePackage == pkg && lastActiveDedupKey == dedupKey) {
            lastActiveRawKey = rawKey
            lastActiveRawAt = nowMillis
            return false
        }
        val prevAt = recentPageCache[dedupKey]
        if (prevAt != null && (nowMillis - prevAt) < cooldownMillis) {
            lastActivePackage = pkg
            lastActiveDedupKey = dedupKey
            lastActiveRawKey = rawKey
            lastActiveRawAt = nowMillis
            return false
        }
        recentPageCache[dedupKey] = nowMillis
        lastActivePackage = pkg
        lastActiveDedupKey = dedupKey
        lastActiveRawKey = rawKey
        lastActiveRawAt = nowMillis
        true
    }

    private fun tryAcquireDiagnosticSlot(
        dedupKey: String,
        nowMillis: Long,
        cooldownMillis: Long
    ): Boolean = synchronized(cacheLock) {
        val prevAt = recentDiagCache[dedupKey]
        if (prevAt != null && (nowMillis - prevAt) < cooldownMillis) {
            return false
        }
        recentDiagCache[dedupKey] = nowMillis
        true
    }

    private fun releaseDiagnosticSlot(dedupKey: String) {
        synchronized(cacheLock) {
            recentDiagCache.remove(dedupKey)
        }
    }

    private fun releaseDispatchSlot(pkg: String, dedupKey: String) {
        synchronized(cacheLock) {
            recentPageCache.remove(dedupKey)
            if (lastActivePackage == pkg && lastActiveDedupKey == dedupKey) {
                lastActiveDedupKey = ""
                lastActiveRawKey = ""
                lastActiveRawAt = 0L
            }
        }
    }

    private fun buildServiceDedupKey(
        snapshot: ScreenSnapshot,
        outcome: ScreenParseOutcome
    ): String {
        val cleanOrderNo = outcome.orderNo.trim()
        if (cleanOrderNo.isNotBlank()) {
            return "ORDER:$cleanOrderNo"
        }
        val draft = outcome.draft
        if (outcome.pageKind == ScreenPageKind.PAY_SUCCESS && draft != null) {
            val normalizedMerchant = normalizeMerchantForCooldown(draft.merchant)
            return "PAY_SUCCESS:${snapshot.packageName}:${draft.type}:${draft.origCurrency}:${draft.origAmountMicros}:$normalizedMerchant"
        }
        return outcome.dedupFingerprint.ifBlank {
            "${outcome.pageKind.name}|${snapshot.packageName}|${outcome.ignoredReason}|${outcome.summaryText.hashCode()}"
        }
    }

    private fun normalizeMerchantForCooldown(raw: String): String =
        normalizeMerchantForMatch(raw)

    @Suppress("DEPRECATION")
    private fun AccessibilityNodeInfo.safeRecycle() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            runCatching { recycle() }
        }
    }

    private fun isFullScreenContainerSwitch(event: AccessibilityEvent, cls: String): Boolean {
        if (!event.isFullScreen || cls.isBlank()) return false
        return !cls.endsWith("Dialog", ignoreCase = true) &&
            !cls.endsWith("PopupWindow", ignoreCase = true) &&
            !cls.endsWith("Toast", ignoreCase = true)
    }

    private fun debugLog(msg: String) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, msg)
        }
    }

    companion object {
        private const val TAG = "KairoA11y"
        private val WINDOW_STATE_RETRY_DELAYS = longArrayOf(60L, 220L, 500L, 900L, 1500L)
        private val CONTENT_OR_SCROLL_RETRY_DELAYS = longArrayOf(60L, 260L, 650L)
        private const val TRAILING_RESCAN_DELAY_MS = 180L
        private const val MAX_TREE_NODES = 2500
        private const val MAX_TREE_DEPTH = 48
        private const val MAX_LRU_ENTRIES = 200
        private const val MAX_DIAG_CACHE_ENTRIES = 64
        private const val SAME_PAGE_COOLDOWN_MILLIS = 15_000L
        private const val PAY_SUCCESS_COOLDOWN_MILLIS = 120_000L
        private const val DIAG_DEBUG_COOLDOWN_MILLIS = 5_000L

        private const val SCORE_CLASSIFIED_PAGE = 500
        private const val SCORE_COMPLETION_SIGNAL = 400
        private const val SCORE_CASHIER_STAGE1 = 300
        private const val SCORE_CHECKOUT_STAGE0 = 200
        private const val SCORE_UNPAID_OR_FAILED = 150
        private const val SCORE_DIAGNOSTIC_CUE = 100
        private const val SCORE_FALLBACK_WATCHED = 10

        internal fun isWindowClassCandidate(cls: String): Boolean {
            if (cls.isBlank()) return false
            if (cls.startsWith("android.widget.") ||
                cls.startsWith("android.view.")
            ) {
                return false
            }
            if (cls.startsWith("androidx.") && !cls.endsWith("Activity")) {
                return false
            }
            if (cls.endsWith("Dialog", ignoreCase = true) ||
                cls.endsWith("PopupWindow", ignoreCase = true) ||
                cls.endsWith("Toast", ignoreCase = true)
            ) {
                return false
            }
            return true
        }
    }
}

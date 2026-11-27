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
        val failReason: String = ""
    )

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val screenBillParser = ScreenBillParser()

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
    private var lastActivePackage: String = ""
    private var lastActiveDedupKey: String = ""
    private var lastActiveRawKey: String = ""
    private var lastActiveRawAt: Long = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        cachedContainer = CaptureRuntimeHelper.containerOrNull(applicationContext)
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
            return ScanAttemptOutcome.NOT_READY_OR_UNKNOWN
        }
        if (ScreenBillParser.isBlockedChatOrFeedWindow(snapshot.packageName, snapshot.className)) {
            debugLog("attempt=$attempt pkg=${snapshot.packageName} cls=${snapshot.className} -> blocked chat/feed window")
            return ScanAttemptOutcome.EXPLICITLY_IGNORED
        }

        val quickKind = screenBillParser.classifyPage(snapshot)
        val canTryJsOcr = container.cloudRuleRepository.isWatchedOcrPackage(snapshot.packageName) &&
            settings?.jsRulesEnabled != false &&
            settings?.jsOcrFallbackEnabled != false

        if (quickKind == ScreenPageKind.UNKNOWN && !canTryJsOcr) {
            val sample = snapshot.texts.take(8).joinToString(" | ") { it.take(24) }
            debugLog(
                "attempt=$attempt pkg=${snapshot.packageName} cls=${snapshot.className} " +
                    "lines=${snapshot.texts.size} -> UNKNOWN sample=[$sample]"
            )
            if (isFinalAttempt &&
                settings?.accessibilityBillDetailEnabled != false &&
                screenBillParser.hasStrongBillDetailAnchors(snapshot)
            ) {
                dispatchDiagnosticIgnoredLog(
                    snapshot = snapshot,
                    reason = "含账单详情锚点但未识别出有效状态或金额",
                    pageKind = ScreenPageKind.BILL_DETAIL
                )
                return ScanAttemptOutcome.EXPLICITLY_IGNORED
            }
            clearLastActiveTracking(snapshot.packageName)
            return ScanAttemptOutcome.NOT_READY_OR_UNKNOWN
        }
        if (quickKind == ScreenPageKind.PAY_SUCCESS && settings != null && !settings.accessibilityPaySuccessEnabled) {
            debugLog("attempt=$attempt pkg=${snapshot.packageName} -> PAY_SUCCESS disabled")
            return ScanAttemptOutcome.EXPLICITLY_IGNORED
        }
        if (quickKind == ScreenPageKind.BILL_DETAIL && settings != null && !settings.accessibilityBillDetailEnabled) {
            debugLog("attempt=$attempt pkg=${snapshot.packageName} -> BILL_DETAIL disabled")
            return ScanAttemptOutcome.EXPLICITLY_IGNORED
        }
        if (quickKind == ScreenPageKind.ORDER_DETAIL && settings != null && !settings.accessibilityOrderDetailEnabled) {
            debugLog("attempt=$attempt pkg=${snapshot.packageName} -> ORDER_DETAIL disabled")
            return ScanAttemptOutcome.EXPLICITLY_IGNORED
        }

        val rawKey = "${snapshot.packageName}|${quickKind.name}|${snapshot.texts.joinToString("\u0001")}"
        val rawCooldownMillis = if (quickKind == ScreenPageKind.PAY_SUCCESS) {
            PAY_SUCCESS_COOLDOWN_MILLIS
        } else {
            SAME_PAGE_COOLDOWN_MILLIS
        }
        synchronized(cacheLock) {
            if (lastActivePackage == snapshot.packageName &&
                lastActiveRawKey == rawKey &&
                (snapshot.capturedAtMillis - lastActiveRawAt) < rawCooldownMillis
            ) {
                debugLog("attempt=$attempt pkg=${snapshot.packageName} kind=$quickKind -> RAW_CACHED")
                return ScanAttemptOutcome.DISPATCHED_OR_CACHED
            }
        }

        val db = container.database
        val allAccounts = runCatching { db.accountDao().getAll() }.getOrDefault(emptyList())
        val activeAccounts = allAccounts.filter { !it.archived }.ifEmpty { allAccounts }
        val aliases = runCatching { db.accountAliasDao().getEnabled() }.getOrDefault(emptyList())
        val categories = runCatching { db.categoryDao().getAll() }.getOrDefault(emptyList())

        var outcome = if (quickKind == ScreenPageKind.UNKNOWN) {
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

        if (outcome.pageKind == ScreenPageKind.UNKNOWN) {
            if (isFinalAttempt &&
                outcome.ignoredReason != "聊天或服务号对话框不触发" &&
                settings?.accessibilityBillDetailEnabled != false &&
                screenBillParser.hasStrongBillDetailAnchors(snapshot)
            ) {
                dispatchDiagnosticIgnoredLog(
                    snapshot = snapshot,
                    reason = outcome.ignoredReason.ifBlank { "含账单详情锚点但未匹配到有效账单字段" },
                    pageKind = ScreenPageKind.BILL_DETAIL,
                    baseOutcome = outcome
                )
                return ScanAttemptOutcome.EXPLICITLY_IGNORED
            }
            if (quickKind == ScreenPageKind.UNKNOWN) {
                clearLastActiveTracking(snapshot.packageName)
            }
            debugLog("attempt=$attempt pkg=${snapshot.packageName} -> parse UNKNOWN (${outcome.ignoredReason})")
            return ScanAttemptOutcome.NOT_READY_OR_UNKNOWN
        }
        if (outcome.pageKind == ScreenPageKind.PAY_SUCCESS && settings != null && !settings.accessibilityPaySuccessEnabled) {
            debugLog("attempt=$attempt pkg=${snapshot.packageName} -> PAY_SUCCESS disabled (post-parse)")
            return ScanAttemptOutcome.EXPLICITLY_IGNORED
        }
        if (outcome.pageKind == ScreenPageKind.BILL_DETAIL && settings != null && !settings.accessibilityBillDetailEnabled) {
            debugLog("attempt=$attempt pkg=${snapshot.packageName} -> BILL_DETAIL disabled (post-parse)")
            return ScanAttemptOutcome.EXPLICITLY_IGNORED
        }
        if (outcome.pageKind == ScreenPageKind.ORDER_DETAIL && settings != null && !settings.accessibilityOrderDetailEnabled) {
            debugLog("attempt=$attempt pkg=${snapshot.packageName} -> ORDER_DETAIL disabled (post-parse)")
            return ScanAttemptOutcome.EXPLICITLY_IGNORED
        }
        if (outcome.ignoredReason.isNotBlank() && outcome.draft == null && !outcome.shouldFallbackToLlm) {
            debugLog("attempt=$attempt pkg=${snapshot.packageName} -> IGNORED: ${outcome.ignoredReason}")
            if (outcome.pageKind == ScreenPageKind.BILL_DETAIL &&
                settings?.accessibilityBillDetailEnabled != false
            ) {
                dispatchDiagnosticIgnoredLog(
                    snapshot = snapshot,
                    reason = outcome.ignoredReason,
                    pageKind = ScreenPageKind.BILL_DETAIL,
                    baseOutcome = outcome
                )
            }
            return ScanAttemptOutcome.EXPLICITLY_IGNORED
        }
        if (outcome.draft == null && !outcome.shouldFallbackToLlm) {
            debugLog("attempt=$attempt pkg=${snapshot.packageName} kind=${outcome.pageKind} -> draft null")
            if (isFinalAttempt &&
                (outcome.pageKind == ScreenPageKind.BILL_DETAIL || screenBillParser.hasStrongBillDetailAnchors(snapshot)) &&
                settings?.accessibilityBillDetailEnabled != false
            ) {
                dispatchDiagnosticIgnoredLog(
                    snapshot = snapshot,
                    reason = "账单详情未提取到有效金额且无法兜底",
                    pageKind = ScreenPageKind.BILL_DETAIL,
                    baseOutcome = outcome
                )
                return ScanAttemptOutcome.EXPLICITLY_IGNORED
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

    private fun dispatchDiagnosticIgnoredLog(
        snapshot: ScreenSnapshot,
        reason: String,
        pageKind: ScreenPageKind,
        baseOutcome: ScreenParseOutcome? = null
    ) {
        val clsLabel = snapshot.className.ifBlank { "<empty>" }
        val cleanReason = reason.trim().ifBlank { "账单详情未触发记账" }
        val fullReason = "$cleanReason (window=$clsLabel)"
        val baseSummary = baseOutcome?.summaryText?.trim().orEmpty().ifBlank {
            snapshot.texts.asSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .joinToString("\n")
        }
        val diagSummary = buildString {
            append("[diag window=").append(clsLabel).append(" reason=").append(cleanReason).append("]\n")
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
        val rawKey = "${snapshot.packageName}|${pageKind.name}|${snapshot.texts.joinToString("\u0001")}"
        if (!tryAcquireDispatchSlot(snapshot.packageName, dedupKey, rawKey, snapshot.capturedAtMillis, SAME_PAGE_COOLDOWN_MILLIS)) {
            debugLog("diagnostic log CACHED($dedupKey)")
            return
        }
        debugLog("diagnostic log DISPATCH pkg=${snapshot.packageName} cls=$clsLabel reason=$cleanReason")
        val dispatchPkg = snapshot.packageName
        serviceScope.launch {
            try {
                CaptureRuntimeHelper.orchestratorOrNull(applicationContext)
                    ?.handleScreenCapture(snapshot, diagOutcome)
            } catch (ce: CancellationException) {
                releaseDispatchSlot(dispatchPkg, dedupKey)
                throw ce
            } catch (t: Throwable) {
                debugLog("diagnostic dispatch error: pkg=$dispatchPkg err=${t.message}")
                releaseDispatchSlot(dispatchPkg, dedupKey)
            }
        }
    }

    private fun captureActiveSnapshot(
        expectedPackage: String,
        container: AppContainer
    ): SnapshotCaptureResult {
        val activeRoot = runCatching { rootInActiveWindow }.getOrNull()
        val activePkg = activeRoot?.packageName?.toString()?.trim().orEmpty()
        val windowList = runCatching { windows }.getOrNull().orEmpty()

        var primaryWindowId = -1
        var primarySnapshot: ScreenSnapshot? = null
        var primaryBlocked = false

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
                if (!primaryBlocked && screenBillParser.classifyPage(primarySnapshot) != ScreenPageKind.UNKNOWN) {
                    return SnapshotCaptureResult(snapshot = primarySnapshot)
                }
            }
        } else {
            activeRoot?.safeRecycle()
        }

        // 主窗口为空、被阻塞或 quickKind == UNKNOWN 时，遍历同包名及其他受监控应用窗口（如微信 :tools / :appbrand / 浮层窗口）
        val altRoots = collectWatchedWindowRoots(
            expectedPackage = expectedPackage,
            windowList = windowList,
            excludeWindowId = primaryWindowId,
            container = container
        )
        var bestFallbackSnapshot: ScreenSnapshot? = if (!primaryBlocked) primarySnapshot else null
        var bestFallbackHasAnchor = bestFallbackSnapshot != null &&
            screenBillParser.hasStrongBillDetailAnchors(bestFallbackSnapshot)

        try {
            for (altRoot in altRoots) {
                val altSnapshot = extractSnapshotFromRoot(altRoot, expectedPackage, container) ?: continue
                if (ScreenBillParser.isBlockedChatOrFeedWindow(altSnapshot.packageName, altSnapshot.className)) {
                    continue
                }
                if (screenBillParser.classifyPage(altSnapshot) != ScreenPageKind.UNKNOWN) {
                    return SnapshotCaptureResult(snapshot = altSnapshot)
                }
                val altHasAnchor = screenBillParser.hasStrongBillDetailAnchors(altSnapshot)
                if (bestFallbackSnapshot == null || (altHasAnchor && !bestFallbackHasAnchor)) {
                    bestFallbackSnapshot = altSnapshot
                    bestFallbackHasAnchor = altHasAnchor
                }
            }
        } finally {
            altRoots.forEach { it.safeRecycle() }
        }

        val resolvedSnapshot = bestFallbackSnapshot ?: primarySnapshot
        if (resolvedSnapshot != null) {
            return SnapshotCaptureResult(snapshot = resolvedSnapshot)
        }
        return SnapshotCaptureResult(
            snapshot = null,
            failReason = "collected=0, activePkg=$activePkg, windows=${windowList.size}"
        )
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
        val exactMatches = ArrayList<AccessibilityNodeInfo>(2)
        val otherMatches = ArrayList<AccessibilityNodeInfo>(2)
        for (window in windowList) {
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
                if (nodeText.isNotEmpty() && out.lastOrNull() != nodeText) {
                    out.add(nodeText)
                    outNodes.add(
                        ScreenTextNode(
                            text = nodeText,
                            topPx = rect.top,
                            heightPx = rect.height().coerceAtLeast(0),
                            isContentDesc = false
                        )
                    )
                }
                if (nodeDesc.isNotEmpty() && nodeDesc != nodeText && out.lastOrNull() != nodeDesc) {
                    out.add(nodeDesc)
                    outNodes.add(
                        ScreenTextNode(
                            text = nodeDesc,
                            topPx = rect.top,
                            heightPx = rect.height().coerceAtLeast(0),
                            isContentDesc = true
                        )
                    )
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
        private const val SAME_PAGE_COOLDOWN_MILLIS = 15_000L
        private const val PAY_SUCCESS_COOLDOWN_MILLIS = 120_000L

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

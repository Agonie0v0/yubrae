package com.kairoledger.app.capture.screen

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.kairoledger.app.BuildConfig
import com.kairoledger.app.capture.CaptureRuntimeHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 无障碍屏幕账单页与支付成功页监听服务：
 * - 入口 O(1) 检查包名白名单，非目标应用零 IPC / 零内存损耗立即返回；
 * - 针对微信 MMWebViewUI / 支付宝 H5 异步加载采用多点渐进重试扫描；
 * - 解除 WebView 容器剪枝并支持 windows 多窗口兜底遍历（上限 900 节点 / 深度 48）；
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
    private var activeScanPackage: String = ""

    @Volatile
    private var dirtyContentWhileScanning: Boolean = false

    private val cacheLock = Any()
    private val recentPageCache = object : LinkedHashMap<String, Long>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?): Boolean =
            size > MAX_LRU_ENTRIES
    }
    private var lastActivePackage: String = ""
    private var lastActiveDedupKey: String = ""
    private var lastActiveRawKey: String = ""

    override fun onServiceConnected() {
        super.onServiceConnected()
        synchronized(cacheLock) {
            lastActivePackage = ""
            lastActiveDedupKey = ""
            lastActiveRawKey = ""
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
        if (pkg == packageName || !ScreenBillParser.isWatchedPackage(pkg)) {
            if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
                !pkg.contains("systemui") &&
                !pkg.contains("inputmethod") &&
                !pkg.contains("keyboard")
            ) {
                val cls = event.className?.toString()?.trim().orEmpty()
                if (pkg != packageName && isWindowClassCandidate(cls)) {
                    latestWindowPackage = ""
                    latestWindowClassName = ""
                }
                synchronized(cacheLock) {
                    lastActivePackage = ""
                    lastActiveDedupKey = ""
                    lastActiveRawKey = ""
                }
                activeScanPackage = ""
                dirtyContentWhileScanning = false
            }
            return
        }

        if (latestWindowPackage != pkg) {
            latestWindowPackage = pkg
            latestWindowClassName = ""
        }

        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val cls = event.className?.toString()?.trim().orEmpty()
            if (isWindowClassCandidate(cls)) {
                latestWindowClassName = cls
            }
            if (ScreenBillParser.isBlockedChatOrFeedWindow(pkg, latestWindowClassName)) {
                debugLog("event=WINDOW_STATE blocked pkg=$pkg cls=$latestWindowClassName")
                scanJob?.cancel()
                activeScanPackage = ""
                dirtyContentWhileScanning = false
                return
            }
        } else if (ScreenBillParser.isBlockedChatOrFeedWindow(pkg, latestWindowClassName)) {
            return
        }

        val retryDelays = if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            debugLog("event=WINDOW_STATE pkg=$pkg cls=${event.className} tracked=$latestWindowClassName")
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
                lastOutcome = processActiveWindow(expectedPackage = pkg, attempt = attemptIdx + 1)
                if (lastOutcome != ScanAttemptOutcome.NOT_READY_OR_UNKNOWN) {
                    break
                }
            }
            if (lastOutcome == ScanAttemptOutcome.NOT_READY_OR_UNKNOWN && dirtyContentWhileScanning) {
                dirtyContentWhileScanning = false
                delay(TRAILING_RESCAN_DELAY_MS)
                processActiveWindow(expectedPackage = pkg, attempt = retryDelays.size + 1)
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
        attempt: Int
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

        val captureResult = captureActiveSnapshot(expectedPackage)
        val snapshot = captureResult.snapshot
        if (snapshot == null) {
            debugLog("attempt=$attempt pkg=$expectedPackage -> snapshot null (${captureResult.failReason})")
            return ScanAttemptOutcome.NOT_READY_OR_UNKNOWN
        }

        val quickKind = screenBillParser.classifyPage(snapshot)
        if (quickKind == ScreenPageKind.UNKNOWN) {
            val sample = snapshot.texts.take(8).joinToString(" | ") { it.take(24) }
            debugLog(
                "attempt=$attempt pkg=${snapshot.packageName} cls=${snapshot.className} " +
                    "lines=${snapshot.texts.size} -> UNKNOWN sample=[$sample]"
            )
            synchronized(cacheLock) {
                if (lastActivePackage == snapshot.packageName) {
                    lastActivePackage = ""
                    lastActiveDedupKey = ""
                    lastActiveRawKey = ""
                }
            }
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

        val rawKey = "${snapshot.packageName}|${quickKind.name}|${snapshot.texts.joinToString("\u0001")}"
        synchronized(cacheLock) {
            if (lastActivePackage == snapshot.packageName && lastActiveRawKey == rawKey) {
                debugLog("attempt=$attempt pkg=${snapshot.packageName} kind=$quickKind -> RAW_CACHED")
                return ScanAttemptOutcome.DISPATCHED_OR_CACHED
            }
        }

        val db = container.database
        val allAccounts = runCatching { db.accountDao().getAll() }.getOrDefault(emptyList())
        val activeAccounts = allAccounts.filter { !it.archived }.ifEmpty { allAccounts }
        val aliases = runCatching { db.accountAliasDao().getEnabled() }.getOrDefault(emptyList())
        val categories = runCatching { db.categoryDao().getAll() }.getOrDefault(emptyList())

        val outcome = screenBillParser.parse(
            snapshot = snapshot,
            aliases = aliases,
            accounts = activeAccounts,
            categories = categories
        )

        if (outcome.pageKind == ScreenPageKind.UNKNOWN) {
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
        if (outcome.ignoredReason.isNotBlank() && outcome.draft == null && !outcome.shouldFallbackToLlm) {
            debugLog("attempt=$attempt pkg=${snapshot.packageName} -> IGNORED: ${outcome.ignoredReason}")
            return ScanAttemptOutcome.EXPLICITLY_IGNORED
        }
        if (outcome.draft == null && !outcome.shouldFallbackToLlm) {
            debugLog("attempt=$attempt pkg=${snapshot.packageName} kind=${outcome.pageKind} -> draft null")
            return ScanAttemptOutcome.NOT_READY_OR_UNKNOWN
        }

        val dedupKey = outcome.dedupFingerprint.ifBlank {
            "${outcome.pageKind.name}|${snapshot.packageName}|${outcome.ignoredReason}|${outcome.summaryText.hashCode()}"
        }

        if (!tryAcquireDispatchSlot(snapshot.packageName, dedupKey, rawKey, snapshot.capturedAtMillis)) {
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

    private fun captureActiveSnapshot(expectedPackage: String): SnapshotCaptureResult {
        val activeRoot = runCatching { rootInActiveWindow }.getOrNull()
        val activePkg = activeRoot?.packageName?.toString()?.trim().orEmpty()
        val windowList = runCatching { windows }.getOrNull().orEmpty()

        val candidateRoots = mutableListOf<AccessibilityNodeInfo>()
        if (activeRoot != null && (activePkg == expectedPackage || ScreenBillParser.isWatchedPackage(activePkg))) {
            candidateRoots.add(activeRoot)
        } else {
            activeRoot?.safeRecycle()
        }

        if (candidateRoots.isEmpty()) {
            val fallbackRoot = findWatchedWindowRoot(expectedPackage, windowList)
            if (fallbackRoot != null) {
                candidateRoots.add(fallbackRoot)
            }
        }

        if (candidateRoots.isEmpty()) {
            val reason = "activeRoot=${activeRoot != null}, activePkg=$activePkg, windows=${windowList.size}"
            return SnapshotCaptureResult(snapshot = null, failReason = reason)
        }

        val primaryRoot = candidateRoots.first()
        val primaryWindowId = runCatching { primaryRoot.windowId }.getOrDefault(-1)
        try {
            extractSnapshotFromRoot(primaryRoot, expectedPackage)?.let { snapshot ->
                return SnapshotCaptureResult(snapshot = snapshot)
            }
        } finally {
            primaryRoot.safeRecycle()
        }

        // 若主窗口节点暂为空（例如 XWeb 过渡/半透明蒙层），尝试同包名其他应用窗口
        val altRoot = findWatchedWindowRoot(expectedPackage, windowList, excludeWindowId = primaryWindowId)
        if (altRoot != null) {
            try {
                extractSnapshotFromRoot(altRoot, expectedPackage)?.let { snapshot ->
                    return SnapshotCaptureResult(snapshot = snapshot)
                }
            } finally {
                altRoot.safeRecycle()
            }
        }

        return SnapshotCaptureResult(
            snapshot = null,
            failReason = "collected=0, activePkg=$activePkg, windows=${windowList.size}"
        )
    }

    private fun extractSnapshotFromRoot(
        root: AccessibilityNodeInfo,
        expectedPackage: String
    ): ScreenSnapshot? {
        val resolvedPkg = root.packageName?.toString()?.takeIf { it.isNotBlank() } ?: expectedPackage
        if (resolvedPkg == packageName || !ScreenBillParser.isWatchedPackage(resolvedPkg)) {
            return null
        }
        val rootRect = Rect()
        root.getBoundsInScreen(rootRect)
        val screenHeightPx = rootRect.height().takeIf { it > 0 }
            ?: resources.displayMetrics.heightPixels

        val collected = ArrayList<String>(64)
        val collectedNodes = ArrayList<ScreenTextNode>(64)
        val counter = IntArray(1)
        collectVisibleTexts(
            node = root,
            depth = 0,
            visitedCounter = counter,
            out = collected,
            outNodes = collectedNodes,
            rect = rootRect
        )
        if (collected.isEmpty()) return null

        val cls = if (latestWindowPackage == resolvedPkg && latestWindowClassName.isNotBlank()) {
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
            screenHeightPx = screenHeightPx
        )
    }

    private fun findWatchedWindowRoot(
        expectedPackage: String,
        windowList: List<AccessibilityWindowInfo>,
        excludeWindowId: Int = -1
    ): AccessibilityNodeInfo? {
        var fallbackCandidate: AccessibilityNodeInfo? = null
        for (window in windowList) {
            if (window.type != AccessibilityWindowInfo.TYPE_APPLICATION) continue
            if (excludeWindowId != -1 && window.id == excludeWindowId) continue
            val root = runCatching { window.root }.getOrNull() ?: continue
            val pkg = root.packageName?.toString()?.trim().orEmpty()
            if (pkg == expectedPackage) {
                fallbackCandidate?.safeRecycle()
                return root
            }
            if (fallbackCandidate == null && pkg != packageName && ScreenBillParser.isWatchedPackage(pkg)) {
                fallbackCandidate = root
            } else {
                root.safeRecycle()
            }
        }
        return fallbackCandidate
    }

    private fun collectVisibleTexts(
        node: AccessibilityNodeInfo,
        depth: Int,
        visitedCounter: IntArray,
        out: MutableList<String>,
        outNodes: MutableList<ScreenTextNode>,
        rect: Rect
    ) {
        if (depth > MAX_TREE_DEPTH || visitedCounter[0] >= MAX_TREE_NODES) return
        visitedCounter[0]++

        // 仅跳过密码框；WebView/XWeb 容器常把父节点标为 isVisibleToUser=false，绝不能提前剪枝整棵子树
        if (node.isPassword) return

        val nodeText = node.text?.toString()?.trim().orEmpty()
        if (nodeText.isNotEmpty() && out.lastOrNull() != nodeText) {
            out.add(nodeText)
            node.getBoundsInScreen(rect)
            outNodes.add(
                ScreenTextNode(
                    text = nodeText,
                    topPx = rect.top,
                    heightPx = rect.height().coerceAtLeast(0),
                    isContentDesc = false
                )
            )
        }

        val nodeDesc = node.contentDescription?.toString()?.trim().orEmpty()
        if (nodeDesc.isNotEmpty() && nodeDesc != nodeText && out.lastOrNull() != nodeDesc) {
            out.add(nodeDesc)
            node.getBoundsInScreen(rect)
            outNodes.add(
                ScreenTextNode(
                    text = nodeDesc,
                    topPx = rect.top,
                    heightPx = rect.height().coerceAtLeast(0),
                    isContentDesc = true
                )
            )
        }

        val childCount = node.childCount
        for (i in 0 until childCount) {
            if (visitedCounter[0] >= MAX_TREE_NODES) break
            val child = runCatching { node.getChild(i) }.getOrNull() ?: continue
            try {
                collectVisibleTexts(child, depth + 1, visitedCounter, out, outNodes, rect)
            } finally {
                child.safeRecycle()
            }
        }
    }

    private fun tryAcquireDispatchSlot(
        pkg: String,
        dedupKey: String,
        rawKey: String,
        nowMillis: Long
    ): Boolean = synchronized(cacheLock) {
        if (lastActivePackage == pkg && lastActiveDedupKey == dedupKey) {
            return false
        }
        val prevAt = recentPageCache[dedupKey]
        if (prevAt != null && (nowMillis - prevAt) < SAME_PAGE_COOLDOWN_MILLIS) {
            lastActivePackage = pkg
            lastActiveDedupKey = dedupKey
            lastActiveRawKey = rawKey
            return false
        }
        recentPageCache[dedupKey] = nowMillis
        lastActivePackage = pkg
        lastActiveDedupKey = dedupKey
        lastActiveRawKey = rawKey
        true
    }

    private fun releaseDispatchSlot(pkg: String, dedupKey: String) {
        synchronized(cacheLock) {
            recentPageCache.remove(dedupKey)
            if (lastActivePackage == pkg && lastActiveDedupKey == dedupKey) {
                lastActiveDedupKey = ""
                lastActiveRawKey = ""
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun AccessibilityNodeInfo.safeRecycle() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            runCatching { recycle() }
        }
    }

    private fun isWindowClassCandidate(cls: String): Boolean {
        if (cls.isBlank()) return false
        if (cls.startsWith("android.widget.") ||
            cls.startsWith("android.view.") ||
            cls.startsWith("androidx.")
        ) {
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

    private fun debugLog(msg: String) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, msg)
        }
    }

    companion object {
        private const val TAG = "KairoA11y"
        private val WINDOW_STATE_RETRY_DELAYS = longArrayOf(200L, 450L, 800L, 1200L)
        private val CONTENT_OR_SCROLL_RETRY_DELAYS = longArrayOf(180L, 550L)
        private const val TRAILING_RESCAN_DELAY_MS = 300L
        private const val MAX_TREE_NODES = 900
        private const val MAX_TREE_DEPTH = 48
        private const val MAX_LRU_ENTRIES = 200
        private const val SAME_PAGE_COOLDOWN_MILLIS = 15_000L
    }
}

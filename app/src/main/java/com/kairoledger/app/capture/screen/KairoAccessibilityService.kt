package com.kairoledger.app.capture.screen

import android.accessibilityservice.AccessibilityService
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

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val screenBillParser = ScreenBillParser()

    private var scanJob: Job? = null

    @Volatile
    private var latestWindowPackage: String = ""

    @Volatile
    private var latestWindowClassName: String = ""

    private val cacheLock = Any()
    private val recentPageCache = object : LinkedHashMap<String, Long>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?): Boolean =
            size > MAX_LRU_ENTRIES
    }
    private var lastActivePackage: String = ""
    private var lastActiveDedupKey: String = ""
    private var lastActiveRawKey: String = ""

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val eventType = event.eventType
        if (eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            eventType != AccessibilityEvent.TYPE_VIEW_SCROLLED
        ) {
            return
        }

        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName || !ScreenBillParser.isWatchedPackage(pkg)) {
            if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
                !pkg.contains("systemui") &&
                !pkg.contains("inputmethod") &&
                !pkg.contains("keyboard")
            ) {
                synchronized(cacheLock) {
                    lastActivePackage = ""
                    lastActiveDedupKey = ""
                    lastActiveRawKey = ""
                }
            }
            return
        }

        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || latestWindowPackage != pkg) {
            latestWindowPackage = pkg
            val cls = event.className?.toString().orEmpty()
            if (cls.isNotBlank() && !cls.startsWith("android.widget.") && !cls.startsWith("androidx.")) {
                latestWindowClassName = cls
            } else if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                latestWindowClassName = cls
            }
        }

        val retryDelays = if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            WINDOW_STATE_RETRY_DELAYS
        } else {
            CONTENT_OR_SCROLL_RETRY_DELAYS
        }

        scanJob?.cancel()
        scanJob = serviceScope.launch {
            for ((attemptIdx, waitMs) in retryDelays.withIndex()) {
                delay(waitMs)
                val status = processActiveWindow(expectedPackage = pkg, attempt = attemptIdx + 1)
                if (status != ScanAttemptOutcome.NOT_READY_OR_UNKNOWN) {
                    break
                }
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
            ?: return ScanAttemptOutcome.NOT_READY_OR_UNKNOWN
        val settings = runCatching { container.settingsStore.getSnapshot() }.getOrNull()
        if (settings != null && !settings.accessibilityCaptureEnabled) {
            return ScanAttemptOutcome.EXPLICITLY_IGNORED
        }

        val snapshot = captureActiveSnapshot(expectedPackage)
        if (snapshot == null) {
            debugLog("attempt=$attempt pkg=$expectedPackage -> snapshot null")
            return ScanAttemptOutcome.NOT_READY_OR_UNKNOWN
        }

        val quickKind = screenBillParser.classifyPage(snapshot)
        if (quickKind == ScreenPageKind.UNKNOWN) {
            debugLog(
                "attempt=$attempt pkg=${snapshot.packageName} cls=${snapshot.className} " +
                    "lines=${snapshot.texts.size} -> UNKNOWN"
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
            return ScanAttemptOutcome.NOT_READY_OR_UNKNOWN
        }
        if (outcome.pageKind == ScreenPageKind.PAY_SUCCESS && settings != null && !settings.accessibilityPaySuccessEnabled) {
            return ScanAttemptOutcome.EXPLICITLY_IGNORED
        }
        if (outcome.pageKind == ScreenPageKind.BILL_DETAIL && settings != null && !settings.accessibilityBillDetailEnabled) {
            return ScanAttemptOutcome.EXPLICITLY_IGNORED
        }
        if (outcome.ignoredReason.isNotBlank() && outcome.draft == null && !outcome.shouldFallbackToLlm) {
            debugLog("attempt=$attempt pkg=${snapshot.packageName} -> IGNORED: ${outcome.ignoredReason}")
            return ScanAttemptOutcome.EXPLICITLY_IGNORED
        }
        if (outcome.draft == null && !outcome.shouldFallbackToLlm) {
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
        return try {
            CaptureRuntimeHelper.orchestratorOrNull(applicationContext)
                ?.handleScreenCapture(snapshot, outcome)
            ScanAttemptOutcome.DISPATCHED_OR_CACHED
        } catch (ce: CancellationException) {
            releaseDispatchSlot(snapshot.packageName, dedupKey)
            throw ce
        } catch (t: Throwable) {
            debugLog("dispatch error: ${t.message}")
            releaseDispatchSlot(snapshot.packageName, dedupKey)
            ScanAttemptOutcome.NOT_READY_OR_UNKNOWN
        }
    }

    private fun captureActiveSnapshot(expectedPackage: String): ScreenSnapshot? {
        val activeRoot = runCatching { rootInActiveWindow }.getOrNull()
        val activePkg = activeRoot?.packageName?.toString()?.trim().orEmpty()

        val targetRoot = when {
            activeRoot != null && (activePkg == expectedPackage || ScreenBillParser.isWatchedPackage(activePkg)) -> {
                activeRoot
            }
            else -> {
                activeRoot?.safeRecycle()
                findWatchedWindowRoot(expectedPackage)
            }
        } ?: return null

        return try {
            val resolvedPkg = targetRoot.packageName?.toString()?.takeIf { it.isNotBlank() } ?: expectedPackage
            if (resolvedPkg == packageName || !ScreenBillParser.isWatchedPackage(resolvedPkg)) {
                return null
            }
            val collected = ArrayList<String>(64)
            val counter = IntArray(1)
            collectVisibleTexts(targetRoot, depth = 0, visitedCounter = counter, out = collected)
            if (collected.isEmpty()) return null

            val cls = if (latestWindowPackage == resolvedPkg && latestWindowClassName.isNotBlank()) {
                latestWindowClassName
            } else {
                targetRoot.className?.toString().orEmpty()
            }
            ScreenSnapshot(
                packageName = resolvedPkg,
                className = cls,
                texts = collected,
                capturedAtMillis = System.currentTimeMillis()
            )
        } finally {
            targetRoot.safeRecycle()
        }
    }

    private fun findWatchedWindowRoot(expectedPackage: String): AccessibilityNodeInfo? {
        val windowList = runCatching { windows }.getOrNull() ?: return null
        var fallbackCandidate: AccessibilityNodeInfo? = null
        for (window in windowList) {
            if (window.type != AccessibilityWindowInfo.TYPE_APPLICATION) continue
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
        out: MutableList<String>
    ) {
        if (depth > MAX_TREE_DEPTH || visitedCounter[0] >= MAX_TREE_NODES) return
        visitedCounter[0]++

        // 仅跳过密码框；WebView/XWeb 容器常把父节点标为 isVisibleToUser=false，绝不能提前剪枝整棵子树
        if (node.isPassword) return

        val nodeText = node.text?.toString()?.trim().orEmpty()
        if (nodeText.isNotEmpty() && out.lastOrNull() != nodeText) {
            out.add(nodeText)
        }

        val nodeDesc = node.contentDescription?.toString()?.trim().orEmpty()
        if (nodeDesc.isNotEmpty() && nodeDesc != nodeText && out.lastOrNull() != nodeDesc) {
            out.add(nodeDesc)
        }

        val childCount = node.childCount
        for (i in 0 until childCount) {
            if (visitedCounter[0] >= MAX_TREE_NODES) break
            val child = runCatching { node.getChild(i) }.getOrNull() ?: continue
            try {
                collectVisibleTexts(child, depth + 1, visitedCounter, out)
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

    private fun debugLog(msg: String) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, msg)
        }
    }

    companion object {
        private const val TAG = "KairoA11y"
        private val WINDOW_STATE_RETRY_DELAYS = longArrayOf(320L, 600L, 850L, 1100L)
        private val CONTENT_OR_SCROLL_RETRY_DELAYS = longArrayOf(250L, 650L, 1000L)
        private const val MAX_TREE_NODES = 900
        private const val MAX_TREE_DEPTH = 48
        private const val MAX_LRU_ENTRIES = 200
        private const val SAME_PAGE_COOLDOWN_MILLIS = 15_000L
    }
}

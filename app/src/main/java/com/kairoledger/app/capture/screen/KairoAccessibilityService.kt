package com.kairoledger.app.capture.screen

import android.accessibilityservice.AccessibilityService
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
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
 * - 450ms 防抖合并连续窗口刷新；
 * - 安全遍历 rootInActiveWindow（上限 260 节点 / 深度 24，仅采集可见节点 text 与 contentDescription，按阅读顺序去重）；
 * - 遵循 SettingsStore 开关与内存 200 条 LRU 页面防重缓存，命中后交由 CaptureOrchestrator 落账并唤起悬浮窗。
 */
class KairoAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val screenBillParser = ScreenBillParser()

    private var debounceJob: Job? = null

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
            eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
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

        debounceJob?.cancel()
        debounceJob = serviceScope.launch {
            delay(DEBOUNCE_MILLIS)
            processActiveWindow(expectedPackage = pkg)
        }
    }

    override fun onInterrupt() {
        debounceJob?.cancel()
    }

    override fun onDestroy() {
        debounceJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    private suspend fun processActiveWindow(expectedPackage: String) {
        val container = CaptureRuntimeHelper.containerOrNull(applicationContext) ?: return
        val settings = runCatching { container.settingsStore.getSnapshot() }.getOrNull()
        if (settings != null && !settings.accessibilityCaptureEnabled) {
            return
        }

        val snapshot = captureActiveSnapshot(expectedPackage) ?: return
        val quickKind = screenBillParser.classifyPage(snapshot)
        if (quickKind == ScreenPageKind.UNKNOWN) {
            synchronized(cacheLock) {
                if (lastActivePackage == snapshot.packageName) {
                    lastActivePackage = ""
                    lastActiveDedupKey = ""
                    lastActiveRawKey = ""
                }
            }
            return
        }
        if (quickKind == ScreenPageKind.PAY_SUCCESS && settings != null && !settings.accessibilityPaySuccessEnabled) {
            return
        }
        if (quickKind == ScreenPageKind.BILL_DETAIL && settings != null && !settings.accessibilityBillDetailEnabled) {
            return
        }

        val rawKey = "${snapshot.packageName}|${quickKind.name}|${snapshot.texts.joinToString("\u0001")}"
        synchronized(cacheLock) {
            if (lastActivePackage == snapshot.packageName && lastActiveRawKey == rawKey) {
                return
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

        if (outcome.pageKind == ScreenPageKind.UNKNOWN) return
        if (outcome.pageKind == ScreenPageKind.PAY_SUCCESS && settings != null && !settings.accessibilityPaySuccessEnabled) {
            return
        }
        if (outcome.pageKind == ScreenPageKind.BILL_DETAIL && settings != null && !settings.accessibilityBillDetailEnabled) {
            return
        }
        if (outcome.draft == null && !outcome.shouldFallbackToLlm && outcome.ignoredReason.isBlank()) {
            return
        }

        val dedupKey = outcome.dedupFingerprint.ifBlank {
            "${outcome.pageKind.name}|${snapshot.packageName}|${outcome.ignoredReason}|${outcome.summaryText.hashCode()}"
        }

        if (!tryAcquireDispatchSlot(snapshot.packageName, dedupKey, rawKey, snapshot.capturedAtMillis)) {
            return
        }

        try {
            CaptureRuntimeHelper.orchestratorOrNull(applicationContext)
                ?.handleScreenCapture(snapshot, outcome)
        } catch (ce: CancellationException) {
            releaseDispatchSlot(snapshot.packageName, dedupKey)
            throw ce
        } catch (_: Throwable) {
            releaseDispatchSlot(snapshot.packageName, dedupKey)
        }
    }

    private fun captureActiveSnapshot(expectedPackage: String): ScreenSnapshot? {
        val root = runCatching { rootInActiveWindow }.getOrNull() ?: return null
        return try {
            val activePkg = root.packageName?.toString()?.takeIf { it.isNotBlank() } ?: expectedPackage
            if (activePkg == packageName || !ScreenBillParser.isWatchedPackage(activePkg)) {
                return null
            }
            val collected = ArrayList<String>(48)
            val counter = IntArray(1)
            collectVisibleTexts(root, depth = 0, visitedCounter = counter, out = collected)
            if (collected.isEmpty()) return null

            val cls = if (latestWindowPackage == activePkg && latestWindowClassName.isNotBlank()) {
                latestWindowClassName
            } else {
                root.className?.toString().orEmpty()
            }
            ScreenSnapshot(
                packageName = activePkg,
                className = cls,
                texts = collected,
                capturedAtMillis = System.currentTimeMillis()
            )
        } finally {
            root.safeRecycle()
        }
    }

    private fun collectVisibleTexts(
        node: AccessibilityNodeInfo,
        depth: Int,
        visitedCounter: IntArray,
        out: MutableList<String>
    ) {
        if (depth > MAX_TREE_DEPTH || visitedCounter[0] >= MAX_TREE_NODES) return
        visitedCounter[0]++

        if (!node.isVisibleToUser || node.isPassword) return

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

    companion object {
        private const val DEBOUNCE_MILLIS = 450L
        private const val MAX_TREE_NODES = 260
        private const val MAX_TREE_DEPTH = 24
        private const val MAX_LRU_ENTRIES = 200
        private const val SAME_PAGE_COOLDOWN_MILLIS = 15_000L
    }
}

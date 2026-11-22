package com.kairoledger.app.capture

import android.app.Notification
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.kairoledger.app.core.contract.CaptureInput
import com.kairoledger.app.data.db.AliasMatchType
import com.kairoledger.app.data.db.CaptureChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * 通知栏自动记账监听服务：
 * 监听微信、支付宝、云闪付、主流中资/外资银行、Wise/Revolut/PayPal/Play Store 等动账通知，
 * 过滤自身包名、常驻通知、摘要通知及微信群聊/普通聊天通知。
 */
class KairoNotificationListenerService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val recentDispatchCache = ConcurrentHashMap<String, Long>()

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val pkg = sbn.packageName ?: return
        if (pkg == packageName || pkg in IGNORED_SYSTEM_PACKAGES) return

        val notification = sbn.notification ?: return
        if ((notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return
        if ((notification.flags and Notification.FLAG_ONGOING_EVENT) != 0) return

        val extras = notification.extras ?: Bundle.EMPTY
        val title = readCharSeq(extras, Notification.EXTRA_TITLE)
        val body = extractNotificationContent(extras)
        if (title.isBlank() && body.isBlank()) return

        if (pkg == PKG_WECHAT && !isLikelyWeChatPayment(title, body, extras)) {
            return
        }

        val timestamp = sbn.postTime.takeIf { it > 0L } ?: System.currentTimeMillis()
        val dedupKey = "$pkg|$title|$body"
        val prevTime = recentDispatchCache[dedupKey]
        if (prevTime != null && timestamp - prevTime < SAME_POST_DEBOUNCE_MS) {
            return
        }
        recentDispatchCache[dedupKey] = timestamp
        pruneDebounceCache(timestamp)

        serviceScope.launch {
            val container = CaptureRuntimeHelper.containerOrNull(applicationContext) ?: return@launch
            val settings = runCatching { container.settingsStore.getSnapshot() }.getOrNull()
            if (settings != null && !settings.notificationCaptureEnabled) return@launch

            if (!isPackageWatched(pkg, container)) return@launch

            val engine = container.captureEngine ?: return@launch
            engine.handleIncomingCapture(
                input = CaptureInput(
                    channel = CaptureChannel.NOTIFICATION,
                    sourcePackage = pkg,
                    title = title,
                    content = body,
                    timestamp = timestamp
                ),
                showFloatingPrompt = true
            )
        }
    }

    private suspend fun isPackageWatched(
        pkg: String,
        container: com.kairoledger.app.AppContainer
    ): Boolean {
        if (pkg in DEFAULT_WATCHED_PACKAGES) return true
        val db = container.database
        val hasAlias = runCatching {
            db.accountAliasDao().getEnabled().any {
                it.matchType.equals(AliasMatchType.PACKAGE, ignoreCase = true) &&
                    it.pattern.trim().equals(pkg, ignoreCase = true)
            }
        }.getOrDefault(false)
        if (hasAlias) return true

        return runCatching {
            db.parseRuleDao().getEnabled().any { rule ->
                rule.sourcePackage != "*" &&
                    rule.sourcePackage.split(",").any { it.trim().equals(pkg, ignoreCase = true) }
            }
        }.getOrDefault(false)
    }

    private fun extractNotificationContent(extras: Bundle): String {
        val bigText = readCharSeq(extras, Notification.EXTRA_BIG_TEXT)
        val text = readCharSeq(extras, Notification.EXTRA_TEXT)
        val subText = readCharSeq(extras, Notification.EXTRA_SUB_TEXT)
        val lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            ?.mapNotNull { it?.toString()?.trim()?.takeIf { line -> line.isNotEmpty() } }
            .orEmpty()

        val primary = when {
            bigText.isNotBlank() -> bigText
            lines.isNotEmpty() -> lines.joinToString("\n")
            else -> text
        }
        return if (subText.isNotBlank() && !primary.contains(subText)) {
            "$primary $subText".trim()
        } else {
            primary.trim()
        }
    }

    private fun isLikelyWeChatPayment(title: String, content: String, extras: Bundle): Boolean {
        if (extras.getBoolean("android.isGroupConversation", false)) return false

        val normalizedTitle = title.trim()
        val combined = "$normalizedTitle\n$content"
        if (WECHAT_GROUP_PREFIX_REGEX.containsMatchIn(content.trim())) return false

        if (WECHAT_PAYMENT_TITLES.any { normalizedTitle.contains(it) }) {
            return true
        }
        return WECHAT_PAYMENT_CONTENT_CUES.any { combined.contains(it) }
    }

    private fun readCharSeq(extras: Bundle, key: String): String =
        extras.getCharSequence(key)?.toString()?.trim().orEmpty()

    private fun pruneDebounceCache(nowMillis: Long) {
        if (recentDispatchCache.size <= 64) return
        val iterator = recentDispatchCache.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (nowMillis - entry.value > SAME_POST_DEBOUNCE_MS * 4) {
                iterator.remove()
            }
        }
    }

    companion object {
        private const val PKG_WECHAT = "com.tencent.mm"
        private const val PKG_ALIPAY = "com.eg.android.AlipayGphone"
        private const val SAME_POST_DEBOUNCE_MS = 3_000L

        private val WECHAT_GROUP_PREFIX_REGEX = Regex("^\\[\\d+条\\]")

        private val WECHAT_PAYMENT_TITLES = setOf(
            "微信支付", "微信收款", "微信收款助手", "微信收款商业版", "微信转账", "服务通知", "零钱通"
        )

        private val WECHAT_PAYMENT_CONTENT_CUES = listOf(
            "微信支付", "付款", "支付成功", "收款", "已存入零钱", "二维码收款", "赞赏码", "扣款成功"
        )

        private val IGNORED_SYSTEM_PACKAGES = setOf(
            "android",
            "com.android.systemui",
            "com.android.providers.downloads"
        )

        val DEFAULT_WATCHED_PACKAGES = setOf(
            // 国民钱包与支付平台
            PKG_WECHAT,
            PKG_ALIPAY,
            "com.unionpay",
            "com.jd.jrapp",
            "com.sankuai.meituan",
            // 主流中资银行
            "com.icbc",
            "com.icbc.androidclient",
            "com.chinamworld.main",
            "com.ccb.longzhifu",
            "com.android.bankabc",
            "com.chinamworld.bocmbci",
            "com.boc.bocsoft.mobilebanking",
            "cmb.pb",
            "com.cmbchina.ccd.pluto.cmbActivity",
            "com.bankcomm.Bankcomm",
            "com.yitong.mbank.psbc",
            "com.ecitic.bank.mobile",
            "com.spdb.mobilebank.per",
            "com.cib.cibmb",
            "cn.com.cmbc.newmbank",
            "com.cebbank.mobile.cemb",
            "com.pingan.paces.ccms",
            "com.cgbchina.xpt",
            "com.hxb.mobile.client",
            "com.webank.wemoney",
            "com.mybank.android.phone",
            // 境外银行、跨境钱包与数字商店
            "com.transferwise.android",
            "com.revolut.revolut",
            "com.paypal.android.p2pmobile",
            "com.android.vending",
            "com.google.android.apps.walletnfcrel",
            "com.hsbc.hsbcchina",
            "uk.co.hsbc.hsbcukmobilebanking",
            "com.hk.hsbc.mobile",
            "com.citi.citimobile",
            "com.chase.sig.android",
            "co.uk.getmondo",
            "de.number26.android",
            "hk.alipay.wallet",
            "com.octopuscards.nfc_reader",
            "com.starlingbank.android"
        )
    }
}

package com.yubrae.app.capture

import android.Manifest
import android.annotation.SuppressLint
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.yubrae.app.core.contract.DevicePermissionReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 通用设备权限体检与 Root 一键授权工具：
 * - 常规体检零侵入（绝不主动调用 su 触发非预期 Root 授权弹窗）；
 * - 通用适配 AOSP / Pixel / 三星 / 华为 / 荣耀 / OPPO / 一加 / vivo / 魅族等 Root 与免 Root 环境；
 * - 仅在小米 HyperOS / MIUI 设备上附加 PowerKeeper、immobulus 与 Greezer 专项放行。
 */
object AndroidPermissionHelper {

    internal const val ACCESSIBILITY_SERVICE_CLASS =
        "com.yubrae.app.capture.screen.KairoAccessibilityService"
    private const val ACCESSIBILITY_SERVICE_SHORT_SUFFIX =
        ".capture.screen.KairoAccessibilityService"
    internal const val NOTIFICATION_LISTENER_CLASS =
        "com.yubrae.app.capture.KairoNotificationListenerService"
    private const val NOTIFICATION_LISTENER_SHORT_SUFFIX =
        ".capture.KairoNotificationListenerService"

    private const val ROOT_PROBE_PREFS_NAME = "yubrae_root_probe"
    private const val KEY_ROOT_PROBE_AUTHORIZED = "root_probe_authorized"
    internal const val ROOT_PROBE_CACHE_TTL_NANOS = 2_500_000_000L

    private val SU_PATHS = listOf(
        "/system/bin/su",
        "/system/xbin/su",
        "/sbin/su",
        "/su/bin/su",
        "/data/local/su",
        "/data/local/bin/su",
        "/data/local/xbin/su",
        "/vendor/bin/su",
        "/apex/com.android.runtime/bin/su"
    )

    private val ROOT_MANAGER_PATHS = listOf(
        "/data/adb/magisk",
        "/data/adb/ksu",
        "/data/adb/apatch"
    )

    private val ROOT_MANAGER_PACKAGES = listOf(
        "com.topjohnwu.magisk",
        "me.weishu.kernelsu",
        "me.bmax.apatch",
        "com.rifsxd.ksunext",
        "com.sukisu.ultra"
    )

    private const val ROOT_BASE_PROBE_SCRIPT =
        "id; " +
            "[ -d /data/adb/lspd ] && echo __LSPD_DIR__; " +
            "for m in /data/adb/modules/*; do " +
            "[ -d \"\$m\" ] && [ ! -e \"\$m/disable\" ] && echo \"__MOD__\${m##*/}\"; " +
            "done; " +
            "(pidof lspd >/dev/null 2>&1 || ps -ef 2>/dev/null | grep -q '[l]spd') && echo __LSPD_PROC__"

    private val SAFE_PACKAGE_REGEX = Regex("^[A-Za-z0-9._]+$")

    internal data class RootProbeResult(
        val isRootGranted: Boolean,
        val hasLsposed: Boolean,
        val powerKeeperBgControl: String? = null
    )

    internal data class RootProbeResolution(
        val probeResult: RootProbeResult,
        val shouldClearAuthorization: Boolean
    )

    internal data class CachedRootProbe(
        val packageName: String,
        val isXiaomiRom: Boolean,
        val userId: Int,
        val result: RootProbeResult,
        val recordedAtNanos: Long
    )

    @Volatile
    private var cachedRootProbe: CachedRootProbe? = null

    suspend fun inspectPermissions(context: Context): DevicePermissionReport = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val pkg = appContext.packageName
        val romInfo = DeviceRomProfile.detectCurrent()
        val userId = (Process.myUid() / 100_000).coerceAtLeast(0)

        // 仅当用户曾主动成功执行 Root 授权（持久化标记）时才执行只读 su 探测；
        // 一旦探测发现权限被撤销或单次授权已过期，立即清零持久化标记并重置状态，避免死循环弹窗。
        val authorized = isRootProbeAuthorized(appContext)
        val cachedProbe = if (authorized) {
            getValidCachedRootProbe(pkg, romInfo.isXiaomiFamily, userId)
        } else {
            null
        }
        val probeResolution = resolveRootProbe(
            isAuthorized = authorized,
            cachedProbe = cachedProbe,
            runProbe = {
                probeRootAndLsposed(
                    packageName = pkg,
                    isXiaomiRom = romInfo.isXiaomiFamily,
                    userId = userId
                )
            }
        )
        if (probeResolution.shouldClearAuthorization) {
            setRootProbeAuthorized(appContext, false)
            clearCachedRootProbe()
        } else if (authorized && cachedProbe == null && probeResolution.probeResult.isRootGranted) {
            updateCachedRootProbe(pkg, romInfo.isXiaomiFamily, userId, probeResolution.probeResult)
        }

        val probeResult = probeResolution.probeResult
        val rootGrantedConfirmed = probeResult.isRootGranted
        val isRooted = rootGrantedConfirmed ||
            detectRootBinary() ||
            detectRootManagerInstalled(appContext)
        val hasLsposed = probeResult.hasLsposed || detectLsposedNonRoot(appContext)

        val notificationListenerEnabled = runCatching {
            NotificationManagerCompat.getEnabledListenerPackages(appContext).contains(pkg)
        }.getOrDefault(false)

        val smsGranted = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.RECEIVE_SMS
        ) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.READ_SMS
            ) == PackageManager.PERMISSION_GRANTED

        val overlayGranted = runCatching { Settings.canDrawOverlays(appContext) }.getOrDefault(false)

        val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val dozeIgnored = runCatching {
            powerManager?.isIgnoringBatteryOptimizations(pkg) == true
        }.getOrDefault(false)
        val ignoringBattery = if (romInfo.isXiaomiFamily && probeResult.powerKeeperBgControl != null) {
            dozeIgnored && probeResult.powerKeeperBgControl.equals("noRestrict", ignoreCase = true)
        } else {
            dozeIgnored
        }

        val postNotificationGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            runCatching { NotificationManagerCompat.from(appContext).areNotificationsEnabled() }.getOrDefault(true)
        }

        val accessibilityServiceEnabled = isAccessibilityServiceEnabled(appContext)
        val accessibilityServiceRunning = if (accessibilityServiceEnabled) {
            isAccessibilityServiceRunning(appContext)
        } else {
            false
        }

        DevicePermissionReport(
            isRooted = isRooted,
            hasLsposedHint = hasLsposed,
            notificationListenerEnabled = notificationListenerEnabled,
            smsPermissionGranted = smsGranted,
            overlayPermissionGranted = overlayGranted,
            ignoringBatteryOptimizations = ignoringBattery,
            postNotificationGranted = postNotificationGranted,
            manufacturer = romInfo.brandDisplay,
            osDisplay = romInfo.osDisplay,
            accessibilityServiceEnabled = accessibilityServiceEnabled,
            accessibilityServiceRunning = accessibilityServiceRunning,
            romFamilyName = romInfo.family.name,
            isXiaomiRom = romInfo.isXiaomiFamily,
            rootGrantedConfirmed = rootGrantedConfirmed
        )
    }

    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val appContext = context.applicationContext
        val masterEnabled = runCatching {
            Settings.Secure.getInt(
                appContext.contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED,
                1
            ) == 1
        }.getOrDefault(true)
        if (!masterEnabled) return false

        val enabledRaw = runCatching {
            Settings.Secure.getString(
                appContext.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
        }.getOrNull()
        return isAccessibilityServiceInList(enabledRaw, appContext.packageName)
    }

    fun isAccessibilityServiceRunning(context: Context): Boolean {
        val appContext = context.applicationContext
        return runCatching {
            val manager = appContext.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
                ?: return@runCatching isAccessibilityServiceEnabled(appContext)
            if (!manager.isEnabled) return@runCatching false
            val pkg = appContext.packageName
            val runningServices = manager.getEnabledAccessibilityServiceList(
                AccessibilityServiceInfo.FEEDBACK_ALL_MASK
            )
            runningServices.any { info ->
                val svcInfo = info.resolveInfo?.serviceInfo
                if (svcInfo != null) {
                    svcInfo.packageName == pkg && (
                        svcInfo.name == ACCESSIBILITY_SERVICE_CLASS ||
                            svcInfo.name == ACCESSIBILITY_SERVICE_SHORT_SUFFIX ||
                            svcInfo.name.endsWith(ACCESSIBILITY_SERVICE_SHORT_SUFFIX)
                        )
                } else {
                    isAccessibilityServiceInList(info.id, pkg)
                }
            }
        }.getOrElse {
            isAccessibilityServiceEnabled(appContext)
        }
    }

    internal fun isAccessibilityServiceInList(
        enabledRaw: String?,
        packageName: String,
        serviceClassName: String = ACCESSIBILITY_SERVICE_CLASS
    ): Boolean {
        val raw = enabledRaw?.trim().orEmpty()
        if (raw.isEmpty() || raw.equals("null", ignoreCase = true)) return false
        val targetFull = "$packageName/$serviceClassName"
        return raw.split(':')
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }
            .any { entry ->
                isSameComponent(
                    a = entry,
                    b = targetFull,
                    knownShortSuffix = ACCESSIBILITY_SERVICE_SHORT_SUFFIX,
                    knownFullClass = serviceClassName
                )
            }
    }

    internal fun buildAppendedAccessibilityServices(
        existingRaw: String?,
        targetComponent: String
    ): String = buildAppendedComponentList(
        existingRaw = existingRaw,
        targetComponent = targetComponent,
        knownShortSuffix = ACCESSIBILITY_SERVICE_SHORT_SUFFIX,
        knownFullClass = ACCESSIBILITY_SERVICE_CLASS
    )

    internal fun buildAppendedComponentList(
        existingRaw: String?,
        targetComponent: String,
        knownShortSuffix: String? = null,
        knownFullClass: String? = null
    ): String {
        val cleanTarget = targetComponent.trim()
        val raw = existingRaw?.trim().orEmpty()
        if (raw.isEmpty() || raw.equals("null", ignoreCase = true)) {
            return cleanTarget
        }
        val deduplicated = mutableListOf<String>()
        for (token in raw.split(':')) {
            val item = token.trim()
            if (item.isEmpty() || item.equals("null", ignoreCase = true)) continue
            if (deduplicated.none { isSameComponent(it, item, knownShortSuffix, knownFullClass) }) {
                deduplicated.add(item)
            }
        }
        if (cleanTarget.isNotEmpty() &&
            deduplicated.none { isSameComponent(it, cleanTarget, knownShortSuffix, knownFullClass) }
        ) {
            deduplicated.add(cleanTarget)
        }
        return deduplicated.joinToString(":")
    }

    private fun isSameComponent(
        a: String,
        b: String,
        knownShortSuffix: String? = ACCESSIBILITY_SERVICE_SHORT_SUFFIX,
        knownFullClass: String? = ACCESSIBILITY_SERVICE_CLASS
    ): Boolean {
        val ca = canonicalComponentName(a)
        val cb = canonicalComponentName(b)
        if (ca == cb) return true

        if (knownShortSuffix == null || knownFullClass == null) return false

        val aSlash = a.indexOf('/')
        val bSlash = b.indexOf('/')
        if (aSlash <= 0 || bSlash <= 0) return false
        val aPkg = a.substring(0, aSlash).trim()
        val bPkg = b.substring(0, bSlash).trim()
        if (aPkg != bPkg) return false

        val aCls = a.substring(aSlash + 1).trim()
        val bCls = b.substring(bSlash + 1).trim()
        return (aCls == knownShortSuffix && bCls == knownFullClass) ||
            (bCls == knownShortSuffix && aCls == knownFullClass)
    }

    private fun canonicalComponentName(component: String): String {
        val trimmed = component.trim()
        val slashIdx = trimmed.indexOf('/')
        if (slashIdx <= 0 || slashIdx >= trimmed.length - 1) return trimmed
        val pkg = trimmed.substring(0, slashIdx).trim()
        val cls = trimmed.substring(slashIdx + 1).trim()
        val fullCls = if (cls.startsWith(".")) "$pkg$cls" else cls
        return "$pkg/$fullCls"
    }

    suspend fun tryRootGrantPermissions(context: Context): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val appContext = context.applicationContext
            val pkg = appContext.packageName
            require(SAFE_PACKAGE_REGEX.matches(pkg)) { "非法应用包名: $pkg" }

            val romInfo = DeviceRomProfile.detectCurrent()
            val uid = Process.myUid()
            val userId = (uid / 100_000).coerceAtLeast(0)
            val listenerComponent = "$pkg/${KairoNotificationListenerService::class.java.name}"
            val accessibilityComponent = "$pkg/$ACCESSIBILITY_SERVICE_CLASS"

            // 非 Root 读取当前 Secure 配置作为单次 su 脚本内无损合并的兜底值，避免提前连弹多次 su
            val existingAccessibility = runCatching {
                Settings.Secure.getString(
                    appContext.contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                )
            }.getOrNull()
            val mergedAccessibility = buildAppendedAccessibilityServices(
                existingRaw = existingAccessibility,
                targetComponent = accessibilityComponent
            )
            val quotedAccessibility = shellSingleQuote(mergedAccessibility)

            val quotedNotificationListeners = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                val existingListeners = runCatching {
                    Settings.Secure.getString(
                        appContext.contentResolver,
                        "enabled_notification_listeners"
                    )
                }.getOrNull()
                val mergedListeners = buildAppendedComponentList(
                    existingRaw = existingListeners,
                    targetComponent = listenerComponent,
                    knownShortSuffix = NOTIFICATION_LISTENER_SHORT_SUFFIX,
                    knownFullClass = NOTIFICATION_LISTENER_CLASS
                )
                shellSingleQuote(mergedListeners)
            } else {
                null
            }

            val updatedImmobulus = if (romInfo.isXiaomiFamily) {
                val existingImmobulus = runCatching {
                    Settings.Secure.getString(
                        appContext.contentResolver,
                        "immobulus_mode_switch_restrict"
                    )
                }.getOrNull()
                buildAppendedImmobulusAllowlist(existingImmobulus, pkg)
            } else {
                null
            }

            val grantScript = buildRootGrantScript(
                pkg = pkg,
                uid = uid,
                userId = userId,
                sdkInt = Build.VERSION.SDK_INT,
                listenerComponent = listenerComponent,
                quotedAccessibility = quotedAccessibility,
                isXiaomiRom = romInfo.isXiaomiFamily,
                updatedImmobulus = updatedImmobulus,
                quotedNotificationListeners = quotedNotificationListeners
            )
            val probeScript = buildRootProbeScript(
                packageName = pkg,
                isXiaomiRom = romInfo.isXiaomiFamily,
                userId = userId
            )
            val combinedScript = "$grantScript ; $probeScript ; true"

            val process = ProcessBuilder("su", "-c", combinedScript)
                .redirectErrorStream(true)
                .start()

            val finished = process.waitFor(15, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                setRootProbeAuthorized(appContext, false)
                clearCachedRootProbe()
                throw IllegalStateException("执行 su 命令超时，请检查 Root 授权管理器弹窗")
            }

            val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
            val exitCode = process.exitValue()
            val (parsedRootGranted, parsedLsposed) = parseRootProbeOutput(output)
            if ((exitCode != 0 && !output.contains("__KAIRO_ROOT_OK__")) || !parsedRootGranted) {
                setRootProbeAuthorized(appContext, false)
                clearCachedRootProbe()
                throw IllegalStateException("Root 授权被拒绝或失败 (code=$exitCode): ${output.take(160)}")
            }

            val bgControl = if (romInfo.isXiaomiFamily) {
                parsePowerKeeperBgControlFromProbe(output)
            } else {
                null
            }
            val grantProbeResult = RootProbeResult(
                isRootGranted = true,
                hasLsposed = parsedLsposed,
                powerKeeperBgControl = bgControl
            )
            setRootProbeAuthorized(appContext, true)
            updateCachedRootProbe(pkg, romInfo.isXiaomiFamily, userId, grantProbeResult)

            delay(300)
            val report = inspectPermissions(appContext)
            val modeLabel = if (romInfo.isXiaomiFamily) {
                "${romInfo.family.displayName} Root 授权已执行"
            } else {
                "通用 Android Root 授权已执行（${romInfo.family.displayName}）"
            }
            buildString {
                append(modeLabel).append("：")
                append("通知监听=").append(if (report.notificationListenerEnabled) "已开启" else "未生效")
                append("，电池白名单=").append(if (report.ignoringBatteryOptimizations) "已放行" else "未生效")
                append("，短信=").append(if (report.smsPermissionGranted) "已授权" else "未生效")
                append("，悬浮窗=").append(if (report.overlayPermissionGranted) "已授权" else "未生效")
                append("，无障碍=").append(if (report.accessibilityServiceEnabled) "已开启" else "未生效")
            }
        }
    }

    internal fun buildRootGrantScript(
        pkg: String,
        uid: Int,
        userId: Int,
        sdkInt: Int,
        listenerComponent: String,
        quotedAccessibility: String,
        isXiaomiRom: Boolean,
        updatedImmobulus: String?,
        quotedNotificationListeners: String? = null
    ): String = buildString {
        val userFlag = if (userId > 0) " --user $userId" else ""
        val appOpsUserFlag = if (userId > 0) "--user $userId " else ""

        // 1. 通用 AOSP 电池白名单与待机桶放行
        append("dumpsys deviceidle whitelist +").append(pkg).append(" ; ")
        append("am set-standby-bucket").append(userFlag).append(' ').append(pkg).append(" active 2>/dev/null ; ")

        // 2. 仅小米 HyperOS / MIUI 执行 PowerKeeper 与 immobulus 专属策略
        if (isXiaomiRom) {
            append("_PK_OUT=$(content query --uri content://com.miui.powerkeeper.configure/userTable --where \"pkgName='")
                .append(pkg).append("' AND userId=").append(userId).append("\" 2>&1) ; ")
            append("_NOW_S=$(date +%s 2>/dev/null) ; ")
            append("case \"\$_NOW_S\" in ''|*[!0-9]*) _NOW_MS=0 ;; *) _NOW_MS=\"\${_NOW_S}000\" ;; esac ; ")
            append("case \"\$_PK_OUT\" in ")
            append("*Error*|*Exception*|*\"Could not find provider\"*) ;; ")
            append("*\"Row:\"*) ")
            append("case \"\$_PK_OUT\" in ")
            append("*\"bgControl=noRestrict\"*) ;; ")
            append("*) content update --uri content://com.miui.powerkeeper.configure/userTable --where \"pkgName='")
                .append(pkg).append("' AND userId=").append(userId)
                .append("\" --bind bgControl:s:noRestrict --bind lastConfigured:l:\"\$_NOW_MS\" >/dev/null 2>&1 ;; ")
            append("esac ;; ")
            append("*\"No result found\"*) ")
            append("content insert --uri content://com.miui.powerkeeper.configure/userTable --bind userId:i:")
                .append(userId).append(" --bind pkgName:s:\"").append(pkg)
                .append("\" --bind bgControl:s:noRestrict --bind lastConfigured:l:\"\$_NOW_MS\" >/dev/null 2>&1 ;; ")
            append("esac ; ")
            if (updatedImmobulus != null) {
                append("settings put").append(userFlag).append(" secure immobulus_mode_switch_restrict ")
                    .append(shellSingleQuote(updatedImmobulus)).append(" 2>/dev/null ; ")
            } else {
                append("_IMMOB_CUR=$(settings get").append(userFlag)
                    .append(" secure immobulus_mode_switch_restrict 2>/dev/null | tr -d '\\r\\n') ; ")
                append("case \"\$_IMMOB_CUR\" in *_allowlist_*) ")
                append("case \"\$_IMMOB_CUR\" in *_wakeuplist_*) _ALLOW_PART=\"\${_IMMOB_CUR%%_wakeuplist_*}\" ;; *) _ALLOW_PART=\"\$_IMMOB_CUR\" ;; esac ; ")
                append("_ALLOW_SEG=\"\${_ALLOW_PART#*_allowlist_}\" ; _NEED_ADD=1 ; ")
                append("case \"\$_ALLOW_SEG\" in \"")
                    .append(pkg).append("\"|\"")
                    .append(pkg).append("_\"*|*\"_")
                    .append(pkg).append("\"|*\"_")
                    .append(pkg).append("_\"*) _NEED_ADD=0 ;; esac ; ")
                append("if [ \"\$_NEED_ADD\" = \"1\" ]; then ")
                append("_HEAD_PART=\"\${_IMMOB_CUR%%_allowlist_*}\" ; _TAIL_PART=\"\${_IMMOB_CUR#*_allowlist_}\" ; ")
                append("if [ -z \"\$_TAIL_PART\" ]; then _IMMOB_NEW=\"\${_HEAD_PART}_allowlist_").append(pkg).append("\" ; ")
                append("else case \"\$_TAIL_PART\" in _*) _IMMOB_NEW=\"\${_HEAD_PART}_allowlist_").append(pkg).append("\${_TAIL_PART}\" ;; ")
                append("*) _IMMOB_NEW=\"\${_HEAD_PART}_allowlist_").append(pkg).append("_\${_TAIL_PART}\" ;; esac ; fi ; ")
                append("settings put").append(userFlag)
                    .append(" secure immobulus_mode_switch_restrict \"\$_IMMOB_NEW\" 2>/dev/null ; ")
                append("fi ;; esac ; ")
            }
        }

        // 3. 运行时权限与标准 AOSP AppOps（含 Android 13+ 受限设置放行）
        append("pm grant").append(userFlag).append(' ').append(pkg).append(" android.permission.RECEIVE_SMS 2>/dev/null ; ")
        append("pm grant").append(userFlag).append(' ').append(pkg).append(" android.permission.READ_SMS 2>/dev/null ; ")
        if (sdkInt >= Build.VERSION_CODES.TIRAMISU) {
            append("pm grant").append(userFlag).append(' ').append(pkg).append(" android.permission.POST_NOTIFICATIONS 2>/dev/null ; ")
        }

        val opsList = buildList {
            add("SYSTEM_ALERT_WINDOW")
            add("RUN_IN_BACKGROUND")
            add("RUN_ANY_IN_BACKGROUND")
            add("ACCESS_RESTRICTED_SETTINGS")
            add("RECEIVE_SENSITIVE_NOTIFICATIONS")
            if (isXiaomiRom) {
                add("10008")
                add("10021")
            }
        }
        append("for _OP in ").append(opsList.joinToString(" ")).append("; do ")
        append("appops set ").append(appOpsUserFlag).append(pkg).append(" \$_OP allow 2>/dev/null; done ; ")

        // 4. 小米 Greezer 与 cgroup 冻结解除
        if (isXiaomiRom && uid > 0) {
            append("cmd greezer thuid ").append(uid).append(" 1000 >/dev/null 2>&1 ; ")
            append("for _CF in /sys/fs/cgroup/apps/uid_").append(uid)
                .append("/cgroup.freeze /sys/fs/cgroup/apps/uid_").append(uid).append("/pid_*/cgroup.freeze; do ")
            append("[ -w \"\$_CF\" ] && printf '0\\n' > \"\$_CF\" 2>/dev/null; done ; ")
        }

        // 5. 最后启用通知监听与无障碍服务（多用户与 API 26-27 兼容，且在单次 su 内无损保留已有服务）
        if (sdkInt >= Build.VERSION_CODES.P) {
            append("cmd notification allow_listener ").append(listenerComponent)
            if (userId > 0) {
                append(' ').append(userId)
            }
            append(" ; ")
        } else {
            val listenerFallback = quotedNotificationListeners ?: shellSingleQuote(listenerComponent)
            val listenerShort = listenerComponent.substringAfter('/', "").let { cls ->
                when {
                    cls.startsWith("$pkg.") -> "$pkg/${cls.removePrefix(pkg)}"
                    cls == NOTIFICATION_LISTENER_CLASS -> "$pkg/$NOTIFICATION_LISTENER_SHORT_SUFFIX"
                    else -> null
                }
            }
            append("_NL_CUR=$(settings get").append(userFlag)
                .append(" secure enabled_notification_listeners 2>/dev/null | tr -d '\\r\\n') ; ")
            append("case \":\$_NL_CUR:\" in ")
            if (listenerShort != null) {
                append("*\":$listenerComponent:\"*|*\":$listenerShort:\"*) _NL_NEW=\"\$_NL_CUR\" ;; ")
            } else {
                append("*\":$listenerComponent:\"*) _NL_NEW=\"\$_NL_CUR\" ;; ")
            }
            append("\"::\"|\":null:\"|\":::\") _NL_NEW=").append(listenerFallback).append(" ;; ")
            append("*) _NL_NEW=\"\${_NL_CUR%:}:$listenerComponent\" ;; ")
            append("esac ; ")
            append("settings put").append(userFlag)
                .append(" secure enabled_notification_listeners \"\$_NL_NEW\" ; ")
        }

        val a11yFull = "$pkg/$ACCESSIBILITY_SERVICE_CLASS"
        val a11yShort = "$pkg/$ACCESSIBILITY_SERVICE_SHORT_SUFFIX"
        append("_A11Y_CUR=$(settings get").append(userFlag)
            .append(" secure enabled_accessibility_services 2>/dev/null | tr -d '\\r\\n') ; ")
        append("case \":\$_A11Y_CUR:\" in ")
        append("*\":$a11yFull:\"*|*\":$a11yShort:\"*) _A11Y_NEW=\"\$_A11Y_CUR\" ;; ")
        append("\"::\"|\":null:\"|\":::\") _A11Y_NEW=").append(quotedAccessibility).append(" ;; ")
        append("*) _A11Y_NEW=\"\${_A11Y_CUR%:}:$a11yFull\" ;; ")
        append("esac ; ")
        append("settings put").append(userFlag).append(" secure enabled_accessibility_services \"\$_A11Y_NEW\" ; ")
        append("settings put").append(userFlag).append(" secure accessibility_enabled 1 ; ")
        append("echo __KAIRO_ROOT_OK__")
    }

    internal fun buildRootProbeScript(
        packageName: String,
        isXiaomiRom: Boolean,
        userId: Int
    ): String = buildString {
        append(ROOT_BASE_PROBE_SCRIPT)
        if (isXiaomiRom && SAFE_PACKAGE_REGEX.matches(packageName)) {
            append("; echo __PK_BEGIN__; ")
            append("content query --uri content://com.miui.powerkeeper.configure/userTable --where \"pkgName='")
                .append(packageName).append("' AND userId=").append(userId).append("\" 2>/dev/null; ")
            append("echo __PK_END__")
        }
    }

    internal fun resolveRootProbe(
        isAuthorized: Boolean,
        cachedProbe: RootProbeResult?,
        runProbe: () -> RootProbeResult
    ): RootProbeResolution {
        if (!isAuthorized) {
            return RootProbeResolution(
                probeResult = RootProbeResult(isRootGranted = false, hasLsposed = false),
                shouldClearAuthorization = false
            )
        }
        if (cachedProbe != null && cachedProbe.isRootGranted) {
            return RootProbeResolution(
                probeResult = cachedProbe,
                shouldClearAuthorization = false
            )
        }
        val fresh = runProbe()
        return RootProbeResolution(
            probeResult = fresh,
            shouldClearAuthorization = !fresh.isRootGranted
        )
    }

    internal fun getValidCachedRootProbe(
        packageName: String,
        isXiaomiRom: Boolean,
        userId: Int,
        nowNanos: Long = System.nanoTime()
    ): RootProbeResult? {
        val cached = cachedRootProbe ?: return null
        val ageNanos = nowNanos - cached.recordedAtNanos
        val matches = cached.packageName == packageName &&
            cached.isXiaomiRom == isXiaomiRom &&
            cached.userId == userId &&
            cached.result.isRootGranted &&
            ageNanos in 0L..ROOT_PROBE_CACHE_TTL_NANOS
        return if (matches) cached.result else null
    }

    internal fun updateCachedRootProbe(
        packageName: String,
        isXiaomiRom: Boolean,
        userId: Int,
        result: RootProbeResult,
        nowNanos: Long = System.nanoTime()
    ) {
        cachedRootProbe = if (result.isRootGranted) {
            CachedRootProbe(
                packageName = packageName,
                isXiaomiRom = isXiaomiRom,
                userId = userId,
                result = result,
                recordedAtNanos = nowNanos
            )
        } else {
            null
        }
    }

    internal fun clearCachedRootProbe() {
        cachedRootProbe = null
    }

    private fun isRootProbeAuthorized(context: Context): Boolean = runCatching {
        context.applicationContext
            .getSharedPreferences(ROOT_PROBE_PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ROOT_PROBE_AUTHORIZED, false)
    }.getOrDefault(false)

    private fun setRootProbeAuthorized(context: Context, authorized: Boolean) {
        runCatching {
            context.applicationContext
                .getSharedPreferences(ROOT_PROBE_PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_ROOT_PROBE_AUTHORIZED, authorized)
                .commit()
        }
    }

    private fun shellSingleQuote(value: String): String =
        "'" + value.replace("'", "'\\''") + "'"

    /**
     * 仅在已持久化确认授过 su 时调用，用于探测 /data/adb/lspd、已启用 LSPosed 模块及（仅小米）PowerKeeper 策略。
     */
    private fun probeRootAndLsposed(
        packageName: String,
        isXiaomiRom: Boolean,
        userId: Int
    ): RootProbeResult {
        return runCatching {
            val probeScript = buildRootProbeScript(
                packageName = packageName,
                isXiaomiRom = isXiaomiRom,
                userId = userId
            )
            val process = ProcessBuilder("su", "-c", probeScript)
                .redirectErrorStream(true)
                .start()
            val finished = process.waitFor(3, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                return@runCatching RootProbeResult(isRootGranted = false, hasLsposed = false)
            }
            val output = process.inputStream.bufferedReader().use { it.readText() }
            val (isRootGranted, hasLsposed) = parseRootProbeOutput(output)
            val bgControl = if (isRootGranted && isXiaomiRom) {
                parsePowerKeeperBgControlFromProbe(output)
            } else {
                null
            }
            RootProbeResult(
                isRootGranted = isRootGranted,
                hasLsposed = hasLsposed,
                powerKeeperBgControl = bgControl
            )
        }.getOrDefault(RootProbeResult(isRootGranted = false, hasLsposed = false))
    }

    internal fun parseRootProbeOutput(output: String): Pair<Boolean, Boolean> {
        if (output.isBlank()) return false to false
        val lines = output.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        val isRootGranted = lines.any { it.contains("uid=0") }
        if (!isRootGranted) return false to false

        val hasLspdDir = lines.any { it == "__LSPD_DIR__" }
        val hasLspdProc = lines.any { it == "__LSPD_PROC__" }
        val hasLspdModule = lines.any { line ->
            line == "__LSPD_MOD__" || (
                line.startsWith("__MOD__") && line.removePrefix("__MOD__").let { modName ->
                    modName.contains("lsposed", ignoreCase = true) ||
                        modName.contains("lspd", ignoreCase = true)
                }
                )
        }
        return true to (hasLspdDir || hasLspdProc || hasLspdModule)
    }

    internal fun parsePowerKeeperBgControlFromProbe(output: String): String? {
        val beginMarker = "__PK_BEGIN__"
        val endMarker = "__PK_END__"
        val beginIdx = output.indexOf(beginMarker)
        if (beginIdx < 0) return null
        val contentStart = beginIdx + beginMarker.length
        val endIdx = output.indexOf(endMarker, startIndex = contentStart)
        if (endIdx < 0) return null
        return parsePowerKeeperBgControl(output.substring(contentStart, endIdx))
    }

    internal fun parsePowerKeeperBgControl(queryOutput: String?): String? {
        val raw = queryOutput?.trim().orEmpty()
        if (raw.isEmpty()) return null
        if (raw.contains("Could not find provider", ignoreCase = true) ||
            raw.contains("Error", ignoreCase = true) ||
            raw.contains("Exception", ignoreCase = true)
        ) {
            return null
        }
        if (raw.contains("No result found", ignoreCase = true)) {
            return "__NONE__"
        }
        val rowIdx = raw.indexOf("Row:")
        if (rowIdx < 0) return null
        val keyToken = "bgControl="
        val keyIdx = raw.indexOf(keyToken, startIndex = rowIdx)
        if (keyIdx < 0) return null
        val afterKey = raw.substring(keyIdx + keyToken.length)
        val value = afterKey.takeWhile { ch ->
            ch != ',' && ch != '\r' && ch != '\n' && !ch.isWhitespace()
        }.trim()
        return value.takeIf { it.isNotEmpty() }
    }

    internal fun buildAppendedImmobulusAllowlist(existingRaw: String?, packageName: String): String? {
        val raw = existingRaw?.trim().orEmpty()
        val cleanPkg = packageName.trim()
        if (raw.isEmpty() || raw.equals("null", ignoreCase = true) || cleanPkg.isEmpty()) return null

        val allowMarker = "_allowlist_"
        val allowIdx = raw.indexOf(allowMarker)
        if (allowIdx < 0) return null

        val allowContentStart = allowIdx + allowMarker.length
        val wakeIdx = raw.indexOf("_wakeuplist_", startIndex = allowContentStart)
        val allowSegment = if (wakeIdx >= 0) {
            raw.substring(allowContentStart, wakeIdx)
        } else {
            raw.substring(allowContentStart)
        }

        val alreadyPresent = allowSegment == cleanPkg ||
            allowSegment.startsWith("${cleanPkg}_") ||
            allowSegment.endsWith("_${cleanPkg}") ||
            allowSegment.contains("_${cleanPkg}_")
        if (alreadyPresent) return null

        val head = raw.substring(0, allowContentStart)
        val tail = raw.substring(allowContentStart)
        return when {
            tail.isEmpty() -> "$head$cleanPkg"
            tail.startsWith("_") -> "$head$cleanPkg$tail"
            else -> "$head${cleanPkg}_$tail"
        }
    }

    private fun detectRootBinary(): Boolean {
        if (SU_PATHS.any { runCatching { File(it).exists() }.getOrDefault(false) }) {
            return true
        }
        if (ROOT_MANAGER_PATHS.any { runCatching { File(it).exists() }.getOrDefault(false) }) {
            return true
        }
        val pathEnv = System.getenv("PATH").orEmpty()
        if (pathEnv.split(":").any { dir ->
                dir.isNotBlank() && runCatching { File(dir, "su").exists() }.getOrDefault(false)
            }
        ) {
            return true
        }
        return false
    }

    private fun detectRootManagerInstalled(context: Context): Boolean {
        return ROOT_MANAGER_PACKAGES.any { pkg ->
            @Suppress("DEPRECATION")
            runCatching { context.packageManager.getPackageInfo(pkg, 0) }.isSuccess
        }
    }

    private fun detectLsposedNonRoot(context: Context): Boolean {
        if (runCatching { File("/data/adb/lspd").exists() }.getOrDefault(false)) return true
        val hasLspdModule = runCatching {
            File("/data/adb/modules").listFiles()?.any {
                !File(it, "disable").exists() && (
                    it.name.contains("lsposed", ignoreCase = true) ||
                        it.name.contains("lspd", ignoreCase = true)
                    )
            } == true
        }.getOrDefault(false)
        if (hasLspdModule) return true
        if (runCatching { Class.forName("de.robv.android.xposed.XposedBridge") }.isSuccess) return true
        if (getSystemProperty("ro.xposed.version").isNotBlank()) return true
        @Suppress("DEPRECATION")
        if (runCatching { context.packageManager.getPackageInfo("org.lsposed.manager", 0) }.isSuccess) return true
        return false
    }

    @SuppressLint("PrivateApi")
    private fun getSystemProperty(key: String): String {
        return runCatching {
            val clazz = Class.forName("android.os.SystemProperties")
            val getMethod = clazz.getMethod("get", String::class.java, String::class.java)
            (getMethod.invoke(null, key, "") as? String).orEmpty().trim()
        }.getOrDefault("")
    }
}

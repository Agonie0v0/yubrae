package com.kairoledger.app.capture

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.kairoledger.app.core.contract.DevicePermissionReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 设备权限体检与 Root 一键授权工具（兼容小米 14 / HyperOS / Magisk / KernelSU / LSPosed）。
 */
object AndroidPermissionHelper {

    internal const val ACCESSIBILITY_SERVICE_CLASS =
        "com.kairoledger.app.capture.screen.KairoAccessibilityService"
    private const val ACCESSIBILITY_SERVICE_SHORT_SUFFIX =
        ".capture.screen.KairoAccessibilityService"

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

    private const val ROOT_PROBE_SCRIPT =
        "id; " +
            "[ -d /data/adb/lspd ] && echo __LSPD_DIR__; " +
            "for m in /data/adb/modules/*; do " +
            "[ -d \"\$m\" ] && [ ! -e \"\$m/disable\" ] && echo \"__MOD__\${m##*/}\"; " +
            "done; " +
            "(pidof lspd >/dev/null 2>&1 || ps -ef 2>/dev/null | grep -q '[l]spd') && echo __LSPD_PROC__"

    suspend fun inspectPermissions(context: Context): DevicePermissionReport = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val pkg = appContext.packageName

        val (rootGranted, lsposedByRoot) = probeRootAndLsposed()
        val isRooted = rootGranted || detectRootBinary()
        val hasLsposed = lsposedByRoot || detectLsposedNonRoot(appContext)

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
        val ignoringBattery = runCatching {
            powerManager?.isIgnoringBatteryOptimizations(pkg) == true
        }.getOrDefault(false)

        val postNotificationGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            runCatching { NotificationManagerCompat.from(appContext).areNotificationsEnabled() }.getOrDefault(true)
        }

        val accessibilityServiceEnabled = isAccessibilityServiceEnabled(appContext)

        DevicePermissionReport(
            isRooted = isRooted,
            hasLsposedHint = hasLsposed,
            notificationListenerEnabled = notificationListenerEnabled,
            smsPermissionGranted = smsGranted,
            overlayPermissionGranted = overlayGranted,
            ignoringBatteryOptimizations = ignoringBattery,
            postNotificationGranted = postNotificationGranted,
            manufacturer = Build.MANUFACTURER.orEmpty(),
            osDisplay = buildOsDisplay(),
            accessibilityServiceEnabled = accessibilityServiceEnabled
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
            .any { entry -> isSameAccessibilityComponent(entry, targetFull, serviceClassName) }
    }

    internal fun buildAppendedAccessibilityServices(
        existingRaw: String?,
        targetComponent: String
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
            if (deduplicated.none { isSameAccessibilityComponent(it, item) }) {
                deduplicated.add(item)
            }
        }
        if (cleanTarget.isNotEmpty() && deduplicated.none { isSameAccessibilityComponent(it, cleanTarget) }) {
            deduplicated.add(cleanTarget)
        }
        return deduplicated.joinToString(":")
    }

    private fun isSameAccessibilityComponent(
        a: String,
        b: String,
        knownServiceClass: String = ACCESSIBILITY_SERVICE_CLASS
    ): Boolean {
        val ca = canonicalComponentName(a)
        val cb = canonicalComponentName(b)
        if (ca == cb) return true

        val aSlash = a.indexOf('/')
        val bSlash = b.indexOf('/')
        if (aSlash <= 0 || bSlash <= 0) return false
        val aPkg = a.substring(0, aSlash).trim()
        val bPkg = b.substring(0, bSlash).trim()
        if (aPkg != bPkg) return false

        val aCls = a.substring(aSlash + 1).trim()
        val bCls = b.substring(bSlash + 1).trim()
        return (aCls == ACCESSIBILITY_SERVICE_SHORT_SUFFIX && bCls == knownServiceClass) ||
            (bCls == ACCESSIBILITY_SERVICE_SHORT_SUFFIX && aCls == knownServiceClass)
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
            val listenerComponent = "$pkg/${KairoNotificationListenerService::class.java.name}"
            val accessibilityComponent = "$pkg/$ACCESSIBILITY_SERVICE_CLASS"

            val existingAccessibility = readSecureSettingViaRoot("enabled_accessibility_services")
                ?: runCatching {
                    Settings.Secure.getString(
                        appContext.contentResolver,
                        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                    )
                }.getOrNull()
            val mergedAccessibility = buildAppendedAccessibilityServices(
                existingRaw = existingAccessibility,
                targetComponent = accessibilityComponent
            )
            val quotedAccessibility = "'" + mergedAccessibility.replace("'", "'\\''") + "'"

            val script = buildString {
                append("cmd notification allow_listener ").append(listenerComponent).append(" ; ")
                append("settings put secure enabled_accessibility_services ").append(quotedAccessibility).append(" ; ")
                append("settings put secure accessibility_enabled 1 ; ")
                append("dumpsys deviceidle whitelist +").append(pkg).append(" ; ")
                append("pm grant ").append(pkg).append(" android.permission.RECEIVE_SMS 2>/dev/null ; ")
                append("pm grant ").append(pkg).append(" android.permission.READ_SMS 2>/dev/null ; ")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    append("pm grant ").append(pkg).append(" android.permission.POST_NOTIFICATIONS 2>/dev/null ; ")
                }
                append("appops set ").append(pkg).append(" SYSTEM_ALERT_WINDOW allow 2>/dev/null ; ")
                append("echo __KAIRO_ROOT_OK__")
            }

            val process = ProcessBuilder("su", "-c", script)
                .redirectErrorStream(true)
                .start()

            val finished = process.waitFor(8, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                throw IllegalStateException("执行 su 命令超时，请检查 Root 授权管理器弹窗")
            }

            val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
            val exitCode = process.exitValue()
            if (exitCode != 0 && !output.contains("__KAIRO_ROOT_OK__")) {
                throw IllegalStateException("Root 授权被拒绝或失败 (code=$exitCode): ${output.take(160)}")
            }

            delay(250)
            val report = inspectPermissions(appContext)
            buildString {
                append("Root 授权已执行：")
                append("通知监听=").append(if (report.notificationListenerEnabled) "已开启" else "未生效")
                append("，电池白名单=").append(if (report.ignoringBatteryOptimizations) "已放行" else "未生效")
                append("，短信=").append(if (report.smsPermissionGranted) "已授权" else "未生效")
                append("，悬浮窗=").append(if (report.overlayPermissionGranted) "已授权" else "未生效")
                append("，无障碍页面监听=").append(if (report.accessibilityServiceEnabled) "已开启" else "未生效")
            }
        }
    }

    private fun readSecureSettingViaRoot(key: String): String? {
        return runCatching {
            val process = ProcessBuilder("su", "-c", "settings get secure $key")
                .redirectErrorStream(true)
                .start()
            val finished = process.waitFor(3, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                return@runCatching null
            }
            val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
            if (process.exitValue() == 0) output else null
        }.getOrNull()
    }

    /**
     * 通过受权 Root shell 探测 /data/adb/lspd、已启用 LSPosed 模块与 lspd 守护进程。
     * 避免普通应用进程因 /data/adb 为 0700 root:root 权限导致探测恒为 false。
     */
    private fun probeRootAndLsposed(): Pair<Boolean, Boolean> {
        return runCatching {
            val process = ProcessBuilder("su", "-c", ROOT_PROBE_SCRIPT)
                .redirectErrorStream(true)
                .start()
            val finished = process.waitFor(2, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                return@runCatching false to false
            }
            val output = process.inputStream.bufferedReader().use { it.readText() }
            parseRootProbeOutput(output)
        }.getOrDefault(false to false)
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

    private fun buildOsDisplay(): String {
        val hyperOsVer = getSystemProperty("ro.mi.os.version.name")
        val miuiVer = getSystemProperty("ro.miui.ui.version.name")
        val androidRelease = Build.VERSION.RELEASE.orEmpty()
        val model = Build.MODEL.orEmpty()

        return when {
            hyperOsVer.isNotBlank() -> "HyperOS $hyperOsVer ($miuiVer) · Android $androidRelease"
            miuiVer.isNotBlank() -> "MIUI $miuiVer · Android $androidRelease"
            else -> "$model · Android $androidRelease"
        }
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

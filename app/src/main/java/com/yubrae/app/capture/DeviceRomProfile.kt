package com.yubrae.app.capture

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import java.util.Locale

enum class RomFamily(val displayName: String) {
    HYPER_OS("小米 HyperOS"),
    MIUI("小米 MIUI"),
    HARMONY_OS("华为 HarmonyOS"),
    EMUI("华为 EMUI"),
    MAGIC_OS("荣耀 MagicOS"),
    COLOR_OS("OPPO / 一加 ColorOS"),
    OXYGEN_OS("一加 OxygenOS"),
    REALME_UI("真我 realme UI"),
    ORIGIN_OS("vivo OriginOS"),
    FUNTOUCH_OS("vivo FuntouchOS"),
    ONE_UI("三星 One UI"),
    FLYME("魅族 Flyme"),
    AOSP("原生 / 类原生 Android")
}

data class DeviceRomInfo(
    val family: RomFamily,
    val brandDisplay: String,
    val osDisplay: String,
    val guideTitle: String,
    val guideSteps: List<String>
) {
    val isXiaomiFamily: Boolean
        get() = family == RomFamily.HYPER_OS || family == RomFamily.MIUI
}

object DeviceRomProfile {

    fun detectCurrent(): DeviceRomInfo = detect(
        manufacturer = Build.MANUFACTURER.orEmpty(),
        brand = Build.BRAND.orEmpty(),
        model = Build.MODEL.orEmpty(),
        androidRelease = Build.VERSION.RELEASE.orEmpty(),
        propReader = ::getSystemProperty
    )

    internal fun detect(
        manufacturer: String,
        brand: String,
        model: String,
        androidRelease: String,
        propReader: (String) -> String
    ): DeviceRomInfo {
        val mfr = manufacturer.trim()
        val brd = brand.trim()
        val mdl = model.trim()
        val androidVer = androidRelease.trim().ifEmpty { "Unknown" }
        val combinedVendor = "$mfr $brd".uppercase(Locale.ROOT)

        val hyperOsVer = propReader("ro.mi.os.version.name").trim()
        val miuiVer = propReader("ro.miui.ui.version.name").trim()
        val miuiCode = propReader("ro.miui.ui.version.code").trim()
        val harmonyVer = propReader("hw_sc.build.platform.version").trim()
        val magicVer = propReader("ro.build.version.magic").trim()
            .ifEmpty { propReader("ro.build.version.magicui").trim() }
        val emuiVer = propReader("ro.build.version.emui").trim()
        val oplusVer = propReader("ro.build.version.oplusrom").trim()
            .ifEmpty { propReader("ro.build.version.opporom").trim() }
        val oxygenVer = propReader("ro.oxygen.version").trim()
        val realmeUiVer = propReader("ro.build.version.realmeui").trim()
        val vivoDisplayId = propReader("ro.vivo.os.build.display.id").trim()
        val vivoOsVer = propReader("ro.vivo.os.version").trim()
        val oneUiCode = propReader("ro.build.version.oneui").trim()
        val buildDisplayId = propReader("ro.build.display.id").trim()

        val family = when {
            hyperOsVer.isNotEmpty() -> RomFamily.HYPER_OS
            miuiVer.isNotEmpty() || miuiCode.isNotEmpty() -> RomFamily.MIUI
            harmonyVer.isNotEmpty() -> RomFamily.HARMONY_OS
            magicVer.isNotEmpty() || combinedVendor.contains("HONOR") -> RomFamily.MAGIC_OS
            emuiVer.isNotEmpty() || combinedVendor.contains("HUAWEI") -> RomFamily.EMUI
            oxygenVer.isNotEmpty() -> RomFamily.OXYGEN_OS
            realmeUiVer.isNotEmpty() || combinedVendor.contains("REALME") -> RomFamily.REALME_UI
            oplusVer.isNotEmpty() || combinedVendor.contains("OPPO") ||
                combinedVendor.contains("ONEPLUS") -> RomFamily.COLOR_OS
            vivoDisplayId.contains("OriginOS", ignoreCase = true) -> RomFamily.ORIGIN_OS
            vivoDisplayId.contains("Funtouch", ignoreCase = true) -> RomFamily.FUNTOUCH_OS
            vivoDisplayId.isNotEmpty() || vivoOsVer.isNotEmpty() ||
                combinedVendor.contains("VIVO") || combinedVendor.contains("IQOO") -> {
                if (vivoOsVer.toDoubleOrNull()?.let { it >= 12.0 } == true && combinedVendor.contains("VIVO")) {
                    RomFamily.ORIGIN_OS
                } else {
                    RomFamily.FUNTOUCH_OS
                }
            }
            oneUiCode.isNotEmpty() || combinedVendor.contains("SAMSUNG") -> RomFamily.ONE_UI
            buildDisplayId.contains("Flyme", ignoreCase = true) ||
                combinedVendor.contains("MEIZU") -> RomFamily.FLYME
            else -> RomFamily.AOSP
        }

        val osDisplay = when (family) {
            RomFamily.HYPER_OS -> buildString {
                append("HyperOS ").append(hyperOsVer)
                if (miuiVer.isNotEmpty()) append(" (").append(miuiVer).append(")")
                append(" · Android ").append(androidVer)
            }
            RomFamily.MIUI -> if (miuiVer.isNotEmpty()) {
                "MIUI $miuiVer · Android $androidVer"
            } else {
                "MIUI · Android $androidVer"
            }
            RomFamily.HARMONY_OS -> "HarmonyOS $harmonyVer · Android $androidVer"
            RomFamily.MAGIC_OS -> if (magicVer.isNotEmpty()) {
                "MagicOS $magicVer · Android $androidVer"
            } else {
                "MagicOS · Android $androidVer"
            }
            RomFamily.EMUI -> if (emuiVer.isNotEmpty()) {
                val cleaned = emuiVer.removePrefix("EmotionUI_").removePrefix("EMUI").trim()
                if (cleaned.isNotEmpty()) "EMUI $cleaned · Android $androidVer" else "EMUI · Android $androidVer"
            } else {
                "EMUI · Android $androidVer"
            }
            RomFamily.OXYGEN_OS -> if (oxygenVer.isNotEmpty()) {
                "OxygenOS $oxygenVer · Android $androidVer"
            } else {
                "OxygenOS · Android $androidVer"
            }
            RomFamily.REALME_UI -> if (realmeUiVer.isNotEmpty()) {
                "realme UI $realmeUiVer · Android $androidVer"
            } else {
                "realme UI · Android $androidVer"
            }
            RomFamily.COLOR_OS -> if (oplusVer.isNotEmpty()) {
                "ColorOS $oplusVer · Android $androidVer"
            } else {
                "ColorOS · Android $androidVer"
            }
            RomFamily.ORIGIN_OS -> {
                val ver = vivoDisplayId.ifEmpty { vivoOsVer }
                when {
                    ver.isEmpty() -> "OriginOS · Android $androidVer"
                    ver.contains("OriginOS", ignoreCase = true) -> "$ver · Android $androidVer"
                    else -> "OriginOS $ver · Android $androidVer"
                }
            }
            RomFamily.FUNTOUCH_OS -> {
                val ver = vivoDisplayId.ifEmpty { vivoOsVer }
                when {
                    ver.isEmpty() -> "FuntouchOS · Android $androidVer"
                    ver.contains("Funtouch", ignoreCase = true) -> "$ver · Android $androidVer"
                    else -> "FuntouchOS $ver · Android $androidVer"
                }
            }
            RomFamily.ONE_UI -> {
                val formatted = formatOneUiVersion(oneUiCode)
                if (formatted.isNotEmpty()) "One UI $formatted · Android $androidVer" else "One UI · Android $androidVer"
            }
            RomFamily.FLYME -> {
                val ver = buildDisplayId.ifEmpty { "Flyme" }
                "$ver · Android $androidVer"
            }
            RomFamily.AOSP -> {
                val prefix = mdl.ifEmpty { mfr.ifEmpty { "Android" } }
                "$prefix · Android $androidVer"
            }
        }

        val brandDisplay = formatBrandDisplay(mfr, brd, mdl)
        val (guideTitle, guideSteps) = buildGuideForFamily(family)

        return DeviceRomInfo(
            family = family,
            brandDisplay = brandDisplay,
            osDisplay = osDisplay,
            guideTitle = guideTitle,
            guideSteps = guideSteps
        )
    }

    fun byFamilyName(name: String): RomFamily =
        RomFamily.entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: RomFamily.AOSP

    fun guideForFamily(family: RomFamily): Pair<String, List<String>> = buildGuideForFamily(family)

    fun needsRestrictedSettingsHint(sdkInt: Int = Build.VERSION.SDK_INT): Boolean =
        sdkInt >= Build.VERSION_CODES.TIRAMISU

    fun openAppDetails(context: Context): Boolean {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
        return safeStartActivity(context, intent)
    }

    fun openOemBackgroundSettings(context: Context, family: RomFamily): Boolean {
        val candidates = when (family) {
            RomFamily.HYPER_OS, RomFamily.MIUI -> listOf(
                ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
                ComponentName("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsContainerManagementActivity")
            )
            RomFamily.HARMONY_OS, RomFamily.EMUI -> listOf(
                ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
                ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"),
                ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity")
            )
            RomFamily.MAGIC_OS -> listOf(
                ComponentName("com.hihonor.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
                ComponentName("com.hihonor.systemmanager", "com.hihonor.systemmanager.appcontrol.activity.StartupAppControlActivity"),
                ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")
            )
            RomFamily.COLOR_OS, RomFamily.OXYGEN_OS, RomFamily.REALME_UI -> listOf(
                ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
                ComponentName("com.oplus.safecenter", "com.oplus.safecenter.startupapp.StartupAppListActivity"),
                ComponentName("com.coloros.oppoguardelf", "com.coloros.powermanager.fuelgaue.PowerUsageModelActivity"),
                ComponentName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity")
            )
            RomFamily.ORIGIN_OS, RomFamily.FUNTOUCH_OS -> listOf(
                ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManagerActivity"),
                ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
                ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity")
            )
            RomFamily.ONE_UI -> listOf(
                ComponentName("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity"),
                ComponentName("com.samsung.android.sm", "com.samsung.android.sm.battery.ui.BatteryActivity")
            )
            RomFamily.FLYME -> listOf(
                ComponentName("com.meizu.safe", "com.meizu.safe.permission.SmartBGActivity"),
                ComponentName("com.meizu.safe", "com.meizu.safe.security.HomeActivity")
            )
            RomFamily.AOSP -> emptyList()
        }

        for (component in candidates) {
            val intent = Intent().apply {
                this.component = component
                putExtra("package_name", context.packageName)
                putExtra("packageName", context.packageName)
            }
            if (safeStartActivity(context, intent)) {
                return true
            }
        }

        if (safeStartActivity(context, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))) {
            return true
        }
        return openAppDetails(context)
    }

    fun openBatteryWhitelist(context: Context, family: RomFamily): Boolean {
        val powerManager = (context.applicationContext ?: context)
            .getSystemService(Context.POWER_SERVICE) as? PowerManager
        val alreadyIgnoringDoze = runCatching {
            powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
        }.getOrDefault(false)
        if (alreadyIgnoringDoze) {
            return openOemBackgroundSettings(context, family)
        }
        val reqIntent = Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:${context.packageName}")
        )
        if (safeStartActivity(context, reqIntent)) {
            return true
        }
        return openOemBackgroundSettings(context, family)
    }

    private fun safeStartActivity(context: Context, intent: Intent): Boolean =
        runCatching {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        }.getOrDefault(false)

    private fun formatOneUiVersion(rawCode: String): String {
        val code = rawCode.toIntOrNull() ?: return rawCode
        if (code < 10_000) return rawCode
        val major = code / 10_000
        val minor = (code % 10_000) / 100
        return "$major.$minor"
    }

    private fun formatBrandDisplay(manufacturer: String, brand: String, model: String): String {
        val primary = when {
            brand.isNotEmpty() -> brand
            manufacturer.isNotEmpty() -> manufacturer
            else -> "Android"
        }
        return if (model.isNotEmpty() && !model.equals(primary, ignoreCase = true)) {
            if (model.startsWith(primary, ignoreCase = true)) model else "$primary $model"
        } else {
            primary
        }
    }

    private fun buildGuideForFamily(family: RomFamily): Pair<String, List<String>> = when (family) {
        RomFamily.HYPER_OS, RomFamily.MIUI -> "${family.displayName} 防冻结指引" to listOf(
            "1. 应用信息 → 省电策略 → 设为「无限制」",
            "2. 应用信息 → 权限管理 → 开启「自启动」「显示悬浮窗」与「后台弹出界面」",
            "3. 若 Android 13+ 无法开启无障碍，先在「应用信息」右上角点击「允许受限设置」",
            "4. 系统设置 → 通知使用权 / 无障碍 → 开启「余白」",
            "5. 多任务界面下拉或长按「余白」卡片加锁防止清理"
        )
        RomFamily.HARMONY_OS, RomFamily.EMUI -> "${family.displayName} 保活指引" to listOf(
            "1. 手机管家 / 设置 → 应用启动管理 → 找到「余白」关闭自动管理，全开「允许自启动、允许关联启动、允许后台活动」",
            "2. 应用信息 → 权限 → 开启「悬浮窗」权限",
            "3. 系统设置 → 通知与状态栏 / 无障碍 → 分别开启「余白」通知使用权与无障碍服务",
            "4. 多任务卡片下滑锁定「余白」，避免锁屏后服务被回收"
        )
        RomFamily.MAGIC_OS -> "荣耀 MagicOS 保活指引" to listOf(
            "1. 系统管家 → 应用启动管理 → 将「余白」改为「手动管理」并开启允许自启动、关联启动与后台活动",
            "2. 设置 → 应用 → 应用管理 → 余白 → 耗电管理 → 设为「无限制」并开启「悬浮窗」",
            "3. Android 13+ 若无障碍置灰，先在「应用信息」右上角菜单点击「允许受限设置」",
            "4. 多任务界面下滑锁定「余白」后台卡片"
        )
        RomFamily.COLOR_OS, RomFamily.OXYGEN_OS, RomFamily.REALME_UI -> "${family.displayName} 保活指引" to listOf(
            "1. 应用信息 → 耗电管理 → 设为「允许完全后台行为 / 不优化」",
            "2. 手机管家 → 权限隐私 → 自启动管理 → 开启「余白」自启动，并开启「悬浮窗」",
            "3. Android 13+ 若无法打开无障碍或通知监听，先在「应用信息」右上角选择「允许受限设置」",
            "4. 系统设置 → 无障碍 / 通知访问权限 → 开启「余白」，多任务界面点击右上角锁定"
        )
        RomFamily.ORIGIN_OS, RomFamily.FUNTOUCH_OS -> "${family.displayName} 保活指引" to listOf(
            "1. i 管家 / 应用信息 → 电池 → 后台耗电管理 → 选择「允许后台高耗电」",
            "2. i 管家 → 权限管理 → 分别开启「余白」的「自启动」「悬浮窗」与「后台弹出界面」",
            "3. Android 13+ 若受限无法开无障碍，先在「应用信息」右上角点击「解除受限设置」",
            "4. 多任务界面下滑加锁，避免清理后台时断开无障碍绑定"
        )
        RomFamily.ONE_UI -> "三星 One UI 保活指引" to listOf(
            "1. 应用信息 → 电池 → 选择「不受限制」",
            "2. 设备维护 → 电池 → 后台使用限制 → 确保「余白」位于「从不休眠的应用」中",
            "3. Android 13+ 若无法打开无障碍，在「应用信息」右上角 ⋮ 菜单点击「允许受限设置」",
            "4. 多任务界面点击应用图标 → 选择「保持打开」锁定后台"
        )
        RomFamily.FLYME -> "魅族 Flyme 保活指引" to listOf(
            "1. 手机管家 → 权限管理 → 后台管理 → 将「余白」设为「允许后台运行」",
            "2. 手机管家 → 权限管理 → 开启「悬浮窗」与自启动权限",
            "3. 若 Android 13+ 提示受限设置，先在「应用信息」右上角开启「允许受限设置」",
            "4. 多任务卡片下滑锁定「余白」"
        )
        RomFamily.AOSP -> "通用 Android 后台保活指引" to listOf(
            "1. 应用信息 → 应用电池用量 → 选择「不受限制」",
            "2. Android 13 及以上若无法打开无障碍或通知访问权限，先在「应用信息」右上角 ⋮ 菜单点击「允许受限设置」",
            "3. 系统设置 → 无障碍 / 通知使用权 → 开启「余白」对应服务",
            "4. 若设备自带厂商后台管控，请在系统管家中允许自启动与悬浮窗权限"
        )
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

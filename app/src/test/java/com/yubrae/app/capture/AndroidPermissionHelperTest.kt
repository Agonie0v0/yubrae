package com.yubrae.app.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidPermissionHelperTest {

    @Test
    fun parseRootProbeOutput_detectsKernelSuAndZygiskLsposed() {
        val output = """
            uid=0(root) gid=0(root) groups=0(root) context=u:r:ksu:s0
            __LSPD_DIR__
            __MOD__tricky_store
            __MOD__zygisk_lsposed
            __MOD__zygisksu
            __LSPD_PROC__
        """.trimIndent()

        assertEquals(true to true, AndroidPermissionHelper.parseRootProbeOutput(output))
    }

    @Test
    fun parseRootProbeOutput_detectsModuleNameCaseInsensitivelyWithoutDirOrProc() {
        val output = """
            uid=0(root) gid=0(root)
            __MOD__Zygisk-LSPosed-Custom
        """.trimIndent()

        assertEquals(true to true, AndroidPermissionHelper.parseRootProbeOutput(output))
    }

    @Test
    fun parseRootProbeOutput_returnsRootTrueAndLsposedFalseWhenOnlyRootPresent() {
        val output = """
            uid=0(root) gid=0(root)
            __MOD__playintegrityfix
            __MOD__tricky_store
        """.trimIndent()

        assertEquals(true to false, AndroidPermissionHelper.parseRootProbeOutput(output))
    }

    @Test
    fun parseRootProbeOutput_rejectsUnprivilegedOrEmptyOutput() {
        assertEquals(false to false, AndroidPermissionHelper.parseRootProbeOutput(""))
        assertEquals(false to false, AndroidPermissionHelper.parseRootProbeOutput("Permission denied"))
        assertEquals(false to false, AndroidPermissionHelper.parseRootProbeOutput("uid=10321(u0_a321)\n__LSPD_DIR__"))
    }

    @Test
    fun isAccessibilityServiceInList_matchesFullAndAbbreviatedComponents() {
        val pkg = "com.yubrae.app"
        val debugPkg = "com.yubrae.app.debug"

        assertTrue(
            AndroidPermissionHelper.isAccessibilityServiceInList(
                "li.songe.gkd/.service.A11yService:$pkg/com.yubrae.app.capture.screen.KairoAccessibilityService",
                pkg
            )
        )
        assertTrue(
            AndroidPermissionHelper.isAccessibilityServiceInList(
                "$pkg/.capture.screen.KairoAccessibilityService:com.omarea.vtools/.AccessibilityScenceMode",
                pkg
            )
        )
        assertTrue(
            AndroidPermissionHelper.isAccessibilityServiceInList(
                "$debugPkg/com.yubrae.app.capture.screen.KairoAccessibilityService",
                debugPkg
            )
        )
        assertTrue(
            AndroidPermissionHelper.isAccessibilityServiceInList(
                "$debugPkg/.capture.screen.KairoAccessibilityService",
                debugPkg
            )
        )
        assertFalse(
            AndroidPermissionHelper.isAccessibilityServiceInList(
                "li.songe.gkd/.service.A11yService:com.omarea.vtools/.AccessibilityScenceMode",
                pkg
            )
        )
        assertFalse(AndroidPermissionHelper.isAccessibilityServiceInList(null, pkg))
        assertFalse(AndroidPermissionHelper.isAccessibilityServiceInList("null", pkg))
    }

    @Test
    fun buildAppendedAccessibilityServices_preservesExistingGkdAndSceneServices() {
        val target = "com.yubrae.app/com.yubrae.app.capture.screen.KairoAccessibilityService"
        val existing = "li.songe.gkd/li.songe.gkd.service.A11yService:com.omarea.vtools/.AccessibilityScenceMode"

        val merged = AndroidPermissionHelper.buildAppendedAccessibilityServices(existing, target)

        assertEquals(
            "li.songe.gkd/li.songe.gkd.service.A11yService:com.omarea.vtools/.AccessibilityScenceMode:$target",
            merged
        )
        assertEquals(target, AndroidPermissionHelper.buildAppendedAccessibilityServices(null, target))
        assertEquals(target, AndroidPermissionHelper.buildAppendedAccessibilityServices("null", target))
        assertEquals(target, AndroidPermissionHelper.buildAppendedAccessibilityServices("  ", target))
    }

    @Test
    fun buildAppendedAccessibilityServices_avoidsDuplicatesAcrossShortAndFullNames() {
        val target = "com.yubrae.app/com.yubrae.app.capture.screen.KairoAccessibilityService"
        val existingShort = "li.songe.gkd/.A11yService:com.yubrae.app/.capture.screen.KairoAccessibilityService:com.omarea.vtools/.AccessibilityScenceMode"

        val once = AndroidPermissionHelper.buildAppendedAccessibilityServices(existingShort, target)
        assertEquals(
            "li.songe.gkd/.A11yService:com.yubrae.app/.capture.screen.KairoAccessibilityService:com.omarea.vtools/.AccessibilityScenceMode",
            once
        )

        val twice = AndroidPermissionHelper.buildAppendedAccessibilityServices(
            ":li.songe.gkd/.A11yService:li.songe.gkd/li.songe.gkd.A11yService::",
            target
        )
        assertEquals("li.songe.gkd/.A11yService:$target", twice)

        val repeatTarget = AndroidPermissionHelper.buildAppendedAccessibilityServices(twice, target)
        assertEquals("li.songe.gkd/.A11yService:$target", repeatTarget)
    }

    @Test
    fun parsePowerKeeperBgControl_extractsPolicyAndHandlesMissingOrErrorOutputs() {
        val noRestrictRow =
            "Row: 1 _id=419, userId=0, pkgName=com.wangc.bill, lastConfigured=1791278917035, bgControl=noRestrict, bgLocation=NULL"
        val miuiAutoRow =
            "Row: 6 _id=28034, userId=0, pkgName=com.yubrae.app.debug, lastConfigured=1795335400380, bgControl=miuiAuto, bgLocation=NULL"

        assertEquals("noRestrict", AndroidPermissionHelper.parsePowerKeeperBgControl(noRestrictRow))
        assertEquals("miuiAuto", AndroidPermissionHelper.parsePowerKeeperBgControl(miuiAutoRow))
        assertEquals("__NONE__", AndroidPermissionHelper.parsePowerKeeperBgControl("No result found."))

        assertNull(AndroidPermissionHelper.parsePowerKeeperBgControl(null))
        assertNull(AndroidPermissionHelper.parsePowerKeeperBgControl("   "))
        assertNull(
            AndroidPermissionHelper.parsePowerKeeperBgControl(
                "Error while accessing provider:com.miui.powerkeeper.configure"
            )
        )
        assertNull(
            AndroidPermissionHelper.parsePowerKeeperBgControl(
                "java.lang.SecurityException: Permission Denial"
            )
        )
        assertNull(
            AndroidPermissionHelper.parsePowerKeeperBgControl(
                "Could not find provider: com.miui.powerkeeper.configure"
            )
        )

        val probeOutput = """
            uid=0(root) gid=0(root)
            __PK_BEGIN__
            $noRestrictRow
            __PK_END__
        """.trimIndent()
        assertEquals("noRestrict", AndroidPermissionHelper.parsePowerKeeperBgControlFromProbe(probeOutput))
        assertNull(AndroidPermissionHelper.parsePowerKeeperBgControlFromProbe("uid=0(root)"))
    }

    @Test
    fun buildAppendedImmobulusAllowlist_preservesExistingAndWakeuplistWithoutDuplicates() {
        val pkg = "com.yubrae.app.debug"
        val realRaw =
            "enable_24_allowlist_android.ext.services_com.onepassword.android_com.tencent.wetype_wakeuplist_com.tencent.mm_com.google.android.gms"

        val updated = AndroidPermissionHelper.buildAppendedImmobulusAllowlist(realRaw, pkg)
        assertEquals(
            "enable_24_allowlist_com.yubrae.app.debug_android.ext.services_com.onepassword.android_com.tencent.wetype_wakeuplist_com.tencent.mm_com.google.android.gms",
            updated
        )

        // 已在 _allowlist_ 中时幂等返回 null
        assertNull(AndroidPermissionHelper.buildAppendedImmobulusAllowlist(updated, pkg))

        // 仅出现在 _wakeuplist_ 中而不在 _allowlist_ 中时仍应追加到 _allowlist_
        val wakeOnlyRaw = "enable_24_allowlist_com.onepassword.android_wakeuplist_com.yubrae.app.debug"
        assertEquals(
            "enable_24_allowlist_com.yubrae.app.debug_com.onepassword.android_wakeuplist_com.yubrae.app.debug",
            AndroidPermissionHelper.buildAppendedImmobulusAllowlist(wakeOnlyRaw, pkg)
        )

        // 包名前缀/超集区分（com.yubrae.app 不应误判为已包含 com.yubrae.app.debug）
        val shorterPkgOnly = "enable_24_allowlist_com.yubrae.app_com.tencent.wetype"
        assertEquals(
            "enable_24_allowlist_com.yubrae.app.debug_com.yubrae.app_com.tencent.wetype",
            AndroidPermissionHelper.buildAppendedImmobulusAllowlist(shorterPkgOnly, pkg)
        )

        // 空值或非 HyperOS immobulus 格式返回 null
        assertNull(AndroidPermissionHelper.buildAppendedImmobulusAllowlist(null, pkg))
        assertNull(AndroidPermissionHelper.buildAppendedImmobulusAllowlist("null", pkg))
        assertNull(AndroidPermissionHelper.buildAppendedImmobulusAllowlist("disable_mode", pkg))
    }

    @Test
    fun buildRootGrantScript_isolatesXiaomiSpecificCommandsForNonXiaomiRootDevices() {
        val genericScript = AndroidPermissionHelper.buildRootGrantScript(
            pkg = "com.yubrae.app",
            uid = 10250,
            userId = 0,
            sdkInt = 35,
            listenerComponent = "com.yubrae.app/com.yubrae.app.capture.KairoNotificationListenerService",
            quotedAccessibility = "'com.yubrae.app/com.yubrae.app.capture.screen.KairoAccessibilityService'",
            isXiaomiRom = false,
            updatedImmobulus = null
        )

        assertTrue(genericScript.contains("dumpsys deviceidle whitelist +com.yubrae.app"))
        assertTrue(genericScript.contains("SYSTEM_ALERT_WINDOW"))
        assertTrue(genericScript.contains("ACCESS_RESTRICTED_SETTINGS"))
        assertTrue(genericScript.contains("POST_NOTIFICATIONS"))
        assertTrue(genericScript.contains("cmd notification allow_listener"))
        assertTrue(genericScript.contains("enabled_accessibility_services"))
        assertFalse(genericScript.contains("powerkeeper"))
        assertFalse(genericScript.contains("immobulus"))
        assertFalse(genericScript.contains("greezer"))
        assertFalse(genericScript.contains("10008"))
        assertFalse(genericScript.contains("10021"))

        val xiaomiMultiUserScript = AndroidPermissionHelper.buildRootGrantScript(
            pkg = "com.yubrae.app",
            uid = 1010250,
            userId = 10,
            sdkInt = 35,
            listenerComponent = "com.yubrae.app/com.yubrae.app.capture.KairoNotificationListenerService",
            quotedAccessibility = "'com.yubrae.app/com.yubrae.app.capture.screen.KairoAccessibilityService'",
            isXiaomiRom = true,
            updatedImmobulus = "enable_24_allowlist_com.yubrae.app"
        )

        assertTrue(xiaomiMultiUserScript.contains("com.miui.powerkeeper.configure/userTable"))
        assertTrue(xiaomiMultiUserScript.contains("userId=10"))
        assertTrue(xiaomiMultiUserScript.contains("--user 10"))
        assertTrue(xiaomiMultiUserScript.contains("immobulus_mode_switch_restrict"))
        assertTrue(xiaomiMultiUserScript.contains("10008"))
        assertTrue(xiaomiMultiUserScript.contains("10021"))
        assertTrue(xiaomiMultiUserScript.contains("cmd greezer thuid 1010250 1000"))
        assertTrue(
            xiaomiMultiUserScript.contains(
                "cmd notification allow_listener com.yubrae.app/com.yubrae.app.capture.KairoNotificationListenerService 10 ;"
            )
        )
        assertTrue(
            genericScript.contains(
                "cmd notification allow_listener com.yubrae.app/com.yubrae.app.capture.KairoNotificationListenerService ;"
            )
        )
    }

    @Test
    fun buildRootGrantScript_handlesApi26And27NotificationListenersAndMultiUserLosslessly() {
        val listener = "com.yubrae.app/com.yubrae.app.capture.KairoNotificationListenerService"
        val a11yQuoted = "'com.yubrae.app/com.yubrae.app.capture.screen.KairoAccessibilityService'"

        val api26Primary = AndroidPermissionHelper.buildRootGrantScript(
            pkg = "com.yubrae.app",
            uid = 10250,
            userId = 0,
            sdkInt = 26,
            listenerComponent = listener,
            quotedAccessibility = a11yQuoted,
            isXiaomiRom = false,
            updatedImmobulus = null
        )
        assertFalse(api26Primary.contains("cmd notification allow_listener"))
        assertFalse(api26Primary.contains("POST_NOTIFICATIONS"))
        assertTrue(api26Primary.contains("settings get secure enabled_notification_listeners"))
        assertTrue(api26Primary.contains("settings put secure enabled_notification_listeners \"\$_NL_NEW\""))
        assertTrue(api26Primary.contains("com.yubrae.app/.capture.KairoNotificationListenerService"))

        val api27WorkProfile = AndroidPermissionHelper.buildRootGrantScript(
            pkg = "com.yubrae.app",
            uid = 1010250,
            userId = 10,
            sdkInt = 27,
            listenerComponent = listener,
            quotedAccessibility = a11yQuoted,
            isXiaomiRom = false,
            updatedImmobulus = null,
            quotedNotificationListeners = "'com.other.watch/.WatchListener:$listener'"
        )
        assertFalse(api27WorkProfile.contains("cmd notification allow_listener"))
        assertTrue(api27WorkProfile.contains("settings get --user 10 secure enabled_notification_listeners"))
        assertTrue(api27WorkProfile.contains("settings put --user 10 secure enabled_notification_listeners \"\$_NL_NEW\""))
        assertTrue(api27WorkProfile.contains("'com.other.watch/.WatchListener:$listener'"))
        assertTrue(api27WorkProfile.contains("settings get --user 10 secure enabled_accessibility_services"))
        assertTrue(api27WorkProfile.contains("settings put --user 10 secure enabled_accessibility_services \"\$_A11Y_NEW\""))

        val api28WorkProfile = AndroidPermissionHelper.buildRootGrantScript(
            pkg = "com.yubrae.app",
            uid = 1010250,
            userId = 10,
            sdkInt = 28,
            listenerComponent = listener,
            quotedAccessibility = a11yQuoted,
            isXiaomiRom = false,
            updatedImmobulus = null
        )
        assertTrue(api28WorkProfile.contains("cmd notification allow_listener $listener 10 ;"))
        assertFalse(api28WorkProfile.contains("enabled_notification_listeners"))
    }

    @Test
    fun buildRootGrantScript_embedsShellSideImmobulusAppendWhenNonRootReadUnavailable() {
        val xiaomiFallbackScript = AndroidPermissionHelper.buildRootGrantScript(
            pkg = "com.yubrae.app",
            uid = 1010250,
            userId = 10,
            sdkInt = 35,
            listenerComponent = "com.yubrae.app/com.yubrae.app.capture.KairoNotificationListenerService",
            quotedAccessibility = "'com.yubrae.app/com.yubrae.app.capture.screen.KairoAccessibilityService'",
            isXiaomiRom = true,
            updatedImmobulus = null
        )
        assertTrue(xiaomiFallbackScript.contains("settings get --user 10 secure immobulus_mode_switch_restrict"))
        assertTrue(xiaomiFallbackScript.contains("_allowlist_"))
        assertTrue(xiaomiFallbackScript.contains("_wakeuplist_"))
        assertTrue(xiaomiFallbackScript.contains("settings put --user 10 secure immobulus_mode_switch_restrict \"\$_IMMOB_NEW\""))
    }

    @Test
    fun buildAppendedComponentList_preservesExistingNotificationListenersAndDeduplicates() {
        val target = "com.yubrae.app.debug/${AndroidPermissionHelper.NOTIFICATION_LISTENER_CLASS}"
        val existing = "com.smartwatch.app/.NotifService:com.tasker.app/com.tasker.app.Listener"

        val appended = AndroidPermissionHelper.buildAppendedComponentList(
            existingRaw = existing,
            targetComponent = target,
            knownShortSuffix = ".capture.KairoNotificationListenerService",
            knownFullClass = AndroidPermissionHelper.NOTIFICATION_LISTENER_CLASS
        )
        assertEquals("$existing:$target", appended)

        val existingShort = "com.smartwatch.app/.NotifService:com.yubrae.app.debug/.capture.KairoNotificationListenerService"
        val deduped = AndroidPermissionHelper.buildAppendedComponentList(
            existingRaw = existingShort,
            targetComponent = target,
            knownShortSuffix = ".capture.KairoNotificationListenerService",
            knownFullClass = AndroidPermissionHelper.NOTIFICATION_LISTENER_CLASS
        )
        assertEquals(existingShort, deduped)
    }

    @Test
    fun resolveRootProbe_enforcesZeroSuWhenUnauthorizedReusesCacheAndClearsOnRevocation() {
        var probeCount = 0

        // 1. 未授权/免 Root：绝不触发 runProbe
        val unauthorized = AndroidPermissionHelper.resolveRootProbe(
            isAuthorized = false,
            cachedProbe = null,
            runProbe = {
                probeCount++
                AndroidPermissionHelper.RootProbeResult(isRootGranted = true, hasLsposed = true)
            }
        )
        assertEquals(0, probeCount)
        assertFalse(unauthorized.probeResult.isRootGranted)
        assertFalse(unauthorized.shouldClearAuthorization)

        // 2. 已授权且命中短缓存：直接复用，避免紧接着 refreshPermissions 重复弹 su
        val cached = AndroidPermissionHelper.RootProbeResult(
            isRootGranted = true,
            hasLsposed = true,
            powerKeeperBgControl = "noRestrict"
        )
        val fromCache = AndroidPermissionHelper.resolveRootProbe(
            isAuthorized = true,
            cachedProbe = cached,
            runProbe = {
                probeCount++
                AndroidPermissionHelper.RootProbeResult(isRootGranted = false, hasLsposed = false)
            }
        )
        assertEquals(0, probeCount)
        assertTrue(fromCache.probeResult.isRootGranted)
        assertEquals("noRestrict", fromCache.probeResult.powerKeeperBgControl)
        assertFalse(fromCache.shouldClearAuthorization)

        // 3. 已授权但缓存过期（如冷启动）：执行只读探测，捕获小米 PowerKeeper 漂移
        val drifted = AndroidPermissionHelper.resolveRootProbe(
            isAuthorized = true,
            cachedProbe = null,
            runProbe = {
                probeCount++
                AndroidPermissionHelper.RootProbeResult(
                    isRootGranted = true,
                    hasLsposed = false,
                    powerKeeperBgControl = "miuiAuto"
                )
            }
        )
        assertEquals(1, probeCount)
        assertTrue(drifted.probeResult.isRootGranted)
        assertEquals("miuiAuto", drifted.probeResult.powerKeeperBgControl)
        assertFalse(drifted.shouldClearAuthorization)

        // 4. Root 被撤销或仅本次允许过期：立刻通知清除持久化标记，且 rootGrantedConfirmed 为 false
        val revoked = AndroidPermissionHelper.resolveRootProbe(
            isAuthorized = true,
            cachedProbe = null,
            runProbe = {
                probeCount++
                AndroidPermissionHelper.RootProbeResult(isRootGranted = false, hasLsposed = false)
            }
        )
        assertEquals(2, probeCount)
        assertFalse(revoked.probeResult.isRootGranted)
        assertTrue(revoked.shouldClearAuthorization)
    }

    @Test
    fun rootProbeCache_respectsTtlKeyMatchingAndNeverCachesDeniedResult() {
        AndroidPermissionHelper.clearCachedRootProbe()
        val t0 = 1_000_000_000L
        val granted = AndroidPermissionHelper.RootProbeResult(
            isRootGranted = true,
            hasLsposed = true,
            powerKeeperBgControl = "noRestrict"
        )

        AndroidPermissionHelper.updateCachedRootProbe("com.yubrae.app", true, 0, granted, nowNanos = t0)
        assertEquals(
            granted,
            AndroidPermissionHelper.getValidCachedRootProbe(
                "com.yubrae.app",
                true,
                0,
                nowNanos = t0 + 1_000_000_000L
            )
        )
        // 不同 userId / 机型标志不命中
        assertNull(
            AndroidPermissionHelper.getValidCachedRootProbe(
                "com.yubrae.app",
                true,
                10,
                nowNanos = t0 + 500_000_000L
            )
        )
        assertNull(
            AndroidPermissionHelper.getValidCachedRootProbe(
                "com.yubrae.app",
                false,
                0,
                nowNanos = t0 + 500_000_000L
            )
        )
        // 超过 TTL 过期
        assertNull(
            AndroidPermissionHelper.getValidCachedRootProbe(
                "com.yubrae.app",
                true,
                0,
                nowNanos = t0 + AndroidPermissionHelper.ROOT_PROBE_CACHE_TTL_NANOS + 1L
            )
        )

        // 拒绝结果不缓存且清空旧缓存
        AndroidPermissionHelper.updateCachedRootProbe(
            "com.yubrae.app",
            true,
            0,
            AndroidPermissionHelper.RootProbeResult(isRootGranted = false, hasLsposed = false),
            nowNanos = t0
        )
        assertNull(AndroidPermissionHelper.getValidCachedRootProbe("com.yubrae.app", true, 0, nowNanos = t0))
    }

    @Test
    fun buildRootProbeScript_onlyQueriesPowerKeeperOnXiaomiAndCombinedOutputParsesCleanly() {
        val nonXiaomiProbe = AndroidPermissionHelper.buildRootProbeScript("com.yubrae.app", false, 0)
        assertFalse(nonXiaomiProbe.contains("powerkeeper"))
        assertFalse(nonXiaomiProbe.contains("__PK_BEGIN__"))

        val xiaomiProbe = AndroidPermissionHelper.buildRootProbeScript("com.yubrae.app", true, 10)
        assertTrue(xiaomiProbe.contains("com.miui.powerkeeper.configure/userTable"))
        assertTrue(xiaomiProbe.contains("userId=10"))
        assertTrue(xiaomiProbe.contains("__PK_BEGIN__"))
        assertTrue(xiaomiProbe.contains("__PK_END__"))

        val combinedOutput = """
            __KAIRO_ROOT_OK__
            uid=0(root) gid=0(root)
            __LSPD_DIR__
            __PK_BEGIN__
            Row: 0 _id=1, userId=10, pkgName=com.yubrae.app, bgControl=noRestrict
            __PK_END__
        """.trimIndent()
        assertEquals(true to true, AndroidPermissionHelper.parseRootProbeOutput(combinedOutput))
        assertEquals("noRestrict", AndroidPermissionHelper.parsePowerKeeperBgControlFromProbe(combinedOutput))
    }
}

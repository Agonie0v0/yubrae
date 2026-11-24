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
}

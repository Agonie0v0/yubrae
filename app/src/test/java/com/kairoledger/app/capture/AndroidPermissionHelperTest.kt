package com.kairoledger.app.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        val pkg = "com.kairoledger.app"
        val debugPkg = "com.kairoledger.app.debug"

        assertTrue(
            AndroidPermissionHelper.isAccessibilityServiceInList(
                "li.songe.gkd/.service.A11yService:$pkg/com.kairoledger.app.capture.screen.KairoAccessibilityService",
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
                "$debugPkg/com.kairoledger.app.capture.screen.KairoAccessibilityService",
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
        val target = "com.kairoledger.app/com.kairoledger.app.capture.screen.KairoAccessibilityService"
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
        val target = "com.kairoledger.app/com.kairoledger.app.capture.screen.KairoAccessibilityService"
        val existingShort = "li.songe.gkd/.A11yService:com.kairoledger.app/.capture.screen.KairoAccessibilityService:com.omarea.vtools/.AccessibilityScenceMode"

        val once = AndroidPermissionHelper.buildAppendedAccessibilityServices(existingShort, target)
        assertEquals(
            "li.songe.gkd/.A11yService:com.kairoledger.app/.capture.screen.KairoAccessibilityService:com.omarea.vtools/.AccessibilityScenceMode",
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
}

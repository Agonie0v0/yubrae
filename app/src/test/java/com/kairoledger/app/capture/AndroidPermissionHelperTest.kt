package com.kairoledger.app.capture

import org.junit.Assert.assertEquals
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
}

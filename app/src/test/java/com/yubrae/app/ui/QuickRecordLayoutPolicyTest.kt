package com.yubrae.app.ui

import com.yubrae.app.ui.components.quickRecordKeypadKeyHeightDp
import com.yubrae.app.ui.components.shouldShowQuickRecordKeypad
import com.yubrae.app.ui.navigation.shouldDisplayInRootSnackbarHost
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickRecordLayoutPolicyTest {

    @Test
    fun keypadKeyHeight_usesCompactHeightBelow660dp() {
        assertEquals(40, quickRecordKeypadKeyHeightDp(560))
        assertEquals(40, quickRecordKeypadKeyHeightDp(640))
        assertEquals(40, quickRecordKeypadKeyHeightDp(659))
        assertEquals(44, quickRecordKeypadKeyHeightDp(660))
        assertEquals(44, quickRecordKeypadKeyHeightDp(800))
    }

    @Test
    fun keypadVisibility_hidesWhenSystemImeOrInputFocused() {
        assertTrue(shouldShowQuickRecordKeypad(isSystemImeVisible = false, imeInputFocused = false))
        assertFalse(shouldShowQuickRecordKeypad(isSystemImeVisible = true, imeInputFocused = false))
        assertFalse(shouldShowQuickRecordKeypad(isSystemImeVisible = false, imeInputFocused = true))
        assertFalse(shouldShowQuickRecordKeypad(isSystemImeVisible = true, imeInputFocused = true))
    }

    @Test
    fun rootSnackbarRouting_skipsOnlyWhileQuickRecordIsActivelyShown() {
        assertTrue(shouldDisplayInRootSnackbarHost(latestShowQuickRecord = false, currentShowQuickRecord = false))
        assertFalse(shouldDisplayInRootSnackbarHost(latestShowQuickRecord = true, currentShowQuickRecord = true))
        // 点击「完成记账」同帧关闭弹窗时，底层宿主仍应接管展示完成提示
        assertTrue(shouldDisplayInRootSnackbarHost(latestShowQuickRecord = true, currentShowQuickRecord = false))
        // 弹窗刚触发打开但尚未重组前，底层宿主不丢消息
        assertTrue(shouldDisplayInRootSnackbarHost(latestShowQuickRecord = false, currentShowQuickRecord = true))
    }
}

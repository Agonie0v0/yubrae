package com.yubrae.app.update

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.yubrae.app.ui.components.formatReleaseNotesForDisplay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseNotesFormatterTest {

    @Test
    fun blankInput_returnsFriendlyDefaultFallback() {
        assertEquals(
            "本次发布包含功能优化与稳定性修复。",
            formatReleaseNotesForDisplay("   \r\n\t  ").text
        )
    }

    @Test
    fun markdownHeadingsBulletsBoldAndChangelog_areFormattedCleanly() {
        val raw = """
            ## What's Changed
            ### 新功能
            - **账单速记**：支持 `QuickRecordSheet` 顶栏防重叠
            * **多币种体验**：统一币种胶囊高度
              - 兼容 [发布说明](https://github.com/Agonie0v0/yubrae/releases/tag/v1.2.2)

            ---

            **Full Changelog**: https://github.com/Agonie0v0/yubrae/compare/v1.2.1...v1.2.2
        """.trimIndent()

        val formatted = formatReleaseNotesForDisplay(raw)
        val text = formatted.text

        assertFalse("Should not leak raw bold markers: $text", text.contains("**"))
        assertFalse("Should not leak raw heading markers: $text", text.contains("##"))
        assertFalse("Should not leak raw backticks: $text", text.contains("`"))
        assertFalse("Should not leak raw horizontal rule: $text", text.contains("---"))
        assertFalse("Should not leak raw compare URL: $text", text.contains("https://github.com"))

        assertTrue(text.contains("What's Changed"))
        assertTrue(text.contains("新功能"))
        assertTrue(text.contains("• 账单速记：支持 QuickRecordSheet 顶栏防重叠"))
        assertTrue(text.contains("• 多币种体验：统一币种胶囊高度"))
        assertTrue(text.contains("  ◦ 兼容 发布说明"))
        assertTrue(text.contains("完整变更记录：v1.2.1...v1.2.2"))

        val boldStart = text.indexOf("账单速记")
        val boldEnd = boldStart + "账单速记".length
        assertTrue(
            "Bold text should carry FontWeight.Bold span",
            formatted.spanStyles.any { span ->
                span.start == boldStart &&
                    span.end == boldEnd &&
                    span.item.fontWeight == FontWeight.Bold
            }
        )

        val headingStart = text.indexOf("新功能")
        val headingEnd = headingStart + "新功能".length
        assertTrue(
            "Heading should carry FontWeight.SemiBold span",
            formatted.spanStyles.any { span ->
                span.start == headingStart &&
                    span.end == headingEnd &&
                    span.item.fontWeight == FontWeight.SemiBold
            }
        )

        val codeStart = text.indexOf("QuickRecordSheet")
        val codeEnd = codeStart + "QuickRecordSheet".length
        assertTrue(
            "Inline code should carry Monospace span",
            formatted.spanStyles.any { span ->
                span.start == codeStart &&
                    span.end == codeEnd &&
                    span.item.fontFamily == FontFamily.Monospace
            }
        )
    }

    @Test
    fun crlfAndExcessiveBlankLines_areNormalizedAndOrderedListPreserved() {
        val raw = "1. 修复 __金额溢出__ 问题\r\n\r\n\r\n2. 优化 **弹窗安全区**\r\n\r\n"
        val formatted = formatReleaseNotesForDisplay(raw)

        assertEquals("1. 修复 金额溢出 问题\n\n2. 优化 弹窗安全区", formatted.text)
        assertTrue(
            formatted.spanStyles.any {
                formatted.text.substring(it.start, it.end) == "金额溢出" &&
                    it.item.fontWeight == FontWeight.Bold
            }
        )
    }
}

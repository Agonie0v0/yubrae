package com.yubrae.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PeriodPickerHelperTest {

    @Test
    fun weekBoundsFor_handlesNormalCrossMonthAndLeapYearCrossYearWeeks() {
        // 普通周（周中：2026-11-26 周四 -> 2026-11-23 周一 ~ 2026-11-29 周日）
        assertEquals(
            LocalDate.of(2026, 11, 23) to LocalDate.of(2026, 11, 29),
            PeriodPickerHelper.weekBoundsFor(LocalDate.of(2026, 11, 26))
        )

        // 边界：周一本身与周日本身
        assertEquals(
            LocalDate.of(2026, 11, 23) to LocalDate.of(2026, 11, 29),
            PeriodPickerHelper.weekBoundsFor(LocalDate.of(2026, 11, 23))
        )
        assertEquals(
            LocalDate.of(2026, 11, 23) to LocalDate.of(2026, 11, 29),
            PeriodPickerHelper.weekBoundsFor(LocalDate.of(2026, 11, 29))
        )

        // 跨月与闰年二月底（2024-02-29 闰日周四 -> 2024-02-26 ~ 2024-03-03）
        assertEquals(
            LocalDate.of(2024, 2, 26) to LocalDate.of(2024, 3, 3),
            PeriodPickerHelper.weekBoundsFor(LocalDate.of(2024, 2, 29))
        )

        // 闰年跨年（2024-12-31 周二 -> 2024-12-30 ~ 2025-01-05）
        assertEquals(
            LocalDate.of(2024, 12, 30) to LocalDate.of(2025, 1, 5),
            PeriodPickerHelper.weekBoundsFor(LocalDate.of(2024, 12, 31))
        )
        assertEquals(
            LocalDate.of(2024, 12, 30) to LocalDate.of(2025, 1, 5),
            PeriodPickerHelper.weekBoundsFor(LocalDate.of(2025, 1, 1))
        )
    }

    @Test
    fun weekLabel_formatsSameYearAndCrossYearRanges() {
        assertEquals(
            "2026/11/23 - 11/29",
            PeriodPickerHelper.weekLabel(LocalDate.of(2026, 11, 26))
        )
        assertEquals(
            "2024/12/30 - 2025/01/05",
            PeriodPickerHelper.weekLabel(LocalDate.of(2024, 12, 31))
        )
    }

    @Test
    fun yearGridRange_alignsTwelveYearPagesCleanly() {
        assertEquals((2016..2027).toList(), PeriodPickerHelper.yearGridRange(2026))
        assertEquals((2016..2027).toList(), PeriodPickerHelper.yearGridRange(2016))
        assertEquals((2016..2027).toList(), PeriodPickerHelper.yearGridRange(2027))
        assertEquals((2028..2039).toList(), PeriodPickerHelper.yearGridRange(2028))
        assertEquals((0..11).toList(), PeriodPickerHelper.yearGridRange(-5))
        assertEquals(12, PeriodPickerHelper.yearGridRange(2026).size)
    }

    @Test
    fun formatMonthSummaryCopyText_formatsBaseAndForeignCurrencies() {
        val baseOnly = PeriodPickerHelper.formatMonthSummaryCopyText(
            yearMonthLabel = "2026年11月",
            baseCurrency = "CNY",
            expenseText = "¥1,234,567.89",
            incomeText = "¥2,000,000.00",
            balanceText = "+¥765,432.11",
            foreignCurrencySummaries = emptyList()
        )
        assertTrue(baseOnly.contains("2026年11月 收支汇总 (CNY)"))
        assertTrue(baseOnly.contains("支出：¥1,234,567.89"))
        assertTrue(baseOnly.contains("收入：¥2,000,000.00"))
        assertTrue(baseOnly.contains("结余：+¥765,432.11"))
        assertFalse(baseOnly.contains("外币明细"))

        val withForeign = PeriodPickerHelper.formatMonthSummaryCopyText(
            yearMonthLabel = "2026年11月",
            baseCurrency = "CNY",
            expenseText = "¥12,345.00",
            incomeText = "¥20,000.00",
            balanceText = "+¥7,655.00",
            foreignCurrencySummaries = listOf(
                Triple("USD", 1_500_000L, 2_500_000L),
                Triple("JPY", 8_000_000L, 0L)
            )
        )
        assertTrue(withForeign.contains("外币明细（原币种）："))
        assertTrue(withForeign.contains("USD：支 $150.00 / 收 $250.00"))
        assertTrue(withForeign.contains("JPY：支 JP¥800"))
    }
}

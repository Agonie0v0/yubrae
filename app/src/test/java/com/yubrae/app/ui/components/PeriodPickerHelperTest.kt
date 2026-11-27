package com.yubrae.app.ui.components

import com.yubrae.app.core.model.MoneyAmount
import com.yubrae.app.data.db.StatsGranularity
import com.yubrae.app.domain.LedgerAnalytics
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class PeriodPickerHelperTest {

    @Test
    fun weekBoundsFor_handlesSundayToSaturdayNormalCrossMonthAndLeapYearCrossYearWeeks() {
        // 普通周（周中：2026-11-26 周四 -> 2026-11-22 周日 ~ 2026-11-28 周六）
        assertEquals(
            LocalDate.of(2026, 11, 22) to LocalDate.of(2026, 11, 28),
            PeriodPickerHelper.weekBoundsFor(LocalDate.of(2026, 11, 26))
        )

        // 边界：周日本身与周六本身（同一周）
        assertEquals(
            LocalDate.of(2026, 11, 22) to LocalDate.of(2026, 11, 28),
            PeriodPickerHelper.weekBoundsFor(LocalDate.of(2026, 11, 22))
        )
        assertEquals(
            LocalDate.of(2026, 11, 22) to LocalDate.of(2026, 11, 28),
            PeriodPickerHelper.weekBoundsFor(LocalDate.of(2026, 11, 28))
        )

        // 边界：次日周日跨入下一自然周（且跨月：2026-11-29 周日 ~ 2026-12-05 周六）
        assertEquals(
            LocalDate.of(2026, 11, 29) to LocalDate.of(2026, 12, 5),
            PeriodPickerHelper.weekBoundsFor(LocalDate.of(2026, 11, 29))
        )

        // 跨月与闰年二月底（2024-02-29 闰日周四 -> 2024-02-25 周日 ~ 2024-03-02 周六）
        assertEquals(
            LocalDate.of(2024, 2, 25) to LocalDate.of(2024, 3, 2),
            PeriodPickerHelper.weekBoundsFor(LocalDate.of(2024, 2, 29))
        )
        assertEquals(
            LocalDate.of(2024, 2, 25) to LocalDate.of(2024, 3, 2),
            PeriodPickerHelper.weekBoundsFor(LocalDate.of(2024, 2, 25))
        )
        assertEquals(
            LocalDate.of(2024, 2, 25) to LocalDate.of(2024, 3, 2),
            PeriodPickerHelper.weekBoundsFor(LocalDate.of(2024, 3, 2))
        )

        // 闰年跨年（2024-12-31 周二 / 2025-01-01 周三 -> 2024-12-29 周日 ~ 2025-01-04 周六）
        assertEquals(
            LocalDate.of(2024, 12, 29) to LocalDate.of(2025, 1, 4),
            PeriodPickerHelper.weekBoundsFor(LocalDate.of(2024, 12, 29))
        )
        assertEquals(
            LocalDate.of(2024, 12, 29) to LocalDate.of(2025, 1, 4),
            PeriodPickerHelper.weekBoundsFor(LocalDate.of(2024, 12, 31))
        )
        assertEquals(
            LocalDate.of(2024, 12, 29) to LocalDate.of(2025, 1, 4),
            PeriodPickerHelper.weekBoundsFor(LocalDate.of(2025, 1, 1))
        )
        assertEquals(
            LocalDate.of(2024, 12, 29) to LocalDate.of(2025, 1, 4),
            PeriodPickerHelper.weekBoundsFor(LocalDate.of(2025, 1, 4))
        )

        // 平年跨年（2025-12-31 周三 -> 2025-12-28 周日 ~ 2026-01-03 周六）
        assertEquals(
            LocalDate.of(2025, 12, 28) to LocalDate.of(2026, 1, 3),
            PeriodPickerHelper.weekBoundsFor(LocalDate.of(2025, 12, 31))
        )
        assertEquals(
            LocalDate.of(2026, 1, 4) to LocalDate.of(2026, 1, 10),
            PeriodPickerHelper.weekBoundsFor(LocalDate.of(2026, 1, 4))
        )
    }

    @Test
    fun weekLabel_formatsSameYearAndCrossYearRanges() {
        assertEquals(
            "2026/11/22 - 11/28",
            PeriodPickerHelper.weekLabel(LocalDate.of(2026, 11, 26))
        )
        assertEquals(
            "2026/11/29 - 12/05",
            PeriodPickerHelper.weekLabel(LocalDate.of(2026, 11, 29))
        )
        assertEquals(
            "2024/12/29 - 2025/01/04",
            PeriodPickerHelper.weekLabel(LocalDate.of(2024, 12, 31))
        )
    }

    @Test
    fun monthCalendarWeeks_alwaysAlignsSundayToSaturdayAcrossNormalAndLeapMonths() {
        val nov2026 = PeriodPickerHelper.monthCalendarWeeks(YearMonth.of(2026, 11))
        assertEquals(5, nov2026.size)
        assertEquals(LocalDate.of(2026, 11, 1), nov2026.first().first())
        assertEquals(LocalDate.of(2026, 12, 5), nov2026.last().last())
        nov2026.forEach { row ->
            assertEquals(7, row.size)
            assertEquals(DayOfWeek.SUNDAY, row.first().dayOfWeek)
            assertEquals(DayOfWeek.SATURDAY, row.last().dayOfWeek)
        }

        val feb2024 = PeriodPickerHelper.monthCalendarWeeks(YearMonth.of(2024, 2))
        assertEquals(5, feb2024.size)
        assertEquals(LocalDate.of(2024, 1, 28), feb2024.first().first())
        assertEquals(LocalDate.of(2024, 3, 2), feb2024.last().last())
        feb2024.forEach { row ->
            assertEquals(7, row.size)
            assertEquals(DayOfWeek.SUNDAY, row.first().dayOfWeek)
            assertEquals(DayOfWeek.SATURDAY, row.last().dayOfWeek)
        }
    }

    @Test
    fun formatDateWithWeekday_formatsChineseDateAndWeekday() {
        assertEquals("2026年11月27日 周五", PeriodPickerHelper.formatDateWithWeekday(LocalDate.of(2026, 11, 27)))
        assertEquals("2026年11月22日 周日", PeriodPickerHelper.formatDateWithWeekday(LocalDate.of(2026, 11, 22)))
        assertEquals("2026年11月28日 周六", PeriodPickerHelper.formatDateWithWeekday(LocalDate.of(2026, 11, 28)))
    }

    @Test
    fun weeklyTrendBuckets_alignsSundayToSaturday() = runBlocking {
        val zone = ZoneId.of("Asia/Shanghai")
        val anchorMillis = LocalDate.of(2026, 11, 27).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        val buckets = LedgerAnalytics.computeTrendBuckets(
            granularity = StatsGranularity.WEEKLY,
            anchorDateMillis = anchorMillis,
            customStartMillis = null,
            customEndMillis = null,
            ledgerId = 0L,
            transactions = emptyList(),
            displayCurrency = "CNY",
            zoneId = zone,
            fetchRate = { _, _ -> MoneyAmount.RATE_SCALE }
        )

        assertEquals(listOf("周日", "周一", "周二", "周三", "周四", "周五", "周六"), buckets.map { it.label })
        val firstDate = Instant.ofEpochMilli(buckets.first().startMillis).atZone(zone).toLocalDate()
        val lastDate = Instant.ofEpochMilli(buckets.last().endMillis).atZone(zone).toLocalDate()
        assertEquals(LocalDate.of(2026, 11, 22), firstDate)
        assertEquals(LocalDate.of(2026, 11, 28), lastDate)
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
        assertTrue(withForeign.contains("USD：支 $\u2009150.00 / 收 $\u2009250.00"))
        assertTrue(withForeign.contains("JPY：支 JP¥\u2009800"))
    }
}

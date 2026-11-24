package com.yubrae.app.domain

import com.yubrae.app.data.db.RecurringRuleEntity
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * 周期账单下次触发时间计算器：
 * - WEEKLY：按 7 天推进
 * - MONTHLY：按自然月推进，并依据 dayOfCycle 与当月最大天数安全夹紧（如 1/31 -> 2/28 -> 3/31）
 * - YEARLY：按自然年推进，妥善处理闰日 2/29 -> 2/28 边界
 */
object RecurringScheduleCalculator {

    const val CYCLE_DAILY = "DAILY"
    const val CYCLE_WEEKLY = "WEEKLY"
    const val CYCLE_MONTHLY = "MONTHLY"
    const val CYCLE_YEARLY = "YEARLY"

    fun nextRunEpochMillis(
        rule: RecurringRuleEntity,
        currentRunMillis: Long = rule.nextRunEpochMillis,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Long {
        val currentZdt = Instant.ofEpochMilli(currentRunMillis).atZone(zoneId)
        val nextZdt = when (rule.cycleType.trim().uppercase()) {
            CYCLE_DAILY -> currentZdt.plusDays(1)
            CYCLE_WEEKLY -> currentZdt.plusWeeks(1)
            CYCLE_YEARLY -> {
                val nextYear = currentZdt.year + 1
                val nextYm = YearMonth.of(nextYear, currentZdt.monthValue)
                val desiredDay = if (rule.dayOfCycle in 2..31) rule.dayOfCycle else currentZdt.dayOfMonth
                val targetDay = desiredDay.coerceIn(1, nextYm.lengthOfMonth())
                ZonedDateTime.of(
                    LocalDate.of(nextYm.year, nextYm.month, targetDay),
                    currentZdt.toLocalTime(),
                    zoneId
                )
            }
            CYCLE_MONTHLY -> computeNextMonthly(currentZdt, rule.dayOfCycle, zoneId)
            else -> computeNextMonthly(currentZdt, rule.dayOfCycle, zoneId)
        }
        return nextZdt.toInstant().toEpochMilli()
    }

    private fun computeNextMonthly(
        currentZdt: ZonedDateTime,
        dayOfCycle: Int,
        zoneId: ZoneId
    ): ZonedDateTime {
        val nextYm = YearMonth.from(currentZdt).plusMonths(1)
        val desiredDay = if (dayOfCycle in 1..31) dayOfCycle else currentZdt.dayOfMonth
        val targetDay = desiredDay.coerceIn(1, nextYm.lengthOfMonth())
        return ZonedDateTime.of(
            LocalDate.of(nextYm.year, nextYm.month, targetDay),
            currentZdt.toLocalTime(),
            zoneId
        )
    }
}

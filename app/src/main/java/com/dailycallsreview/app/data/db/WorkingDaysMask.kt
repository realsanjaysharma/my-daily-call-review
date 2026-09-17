package com.dailycallsreview.app.data.db

import java.time.DayOfWeek

object WorkingDaysMask {
    private val ORDER = listOf(
        DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY
    )

    fun toSet(mask: Int): Set<DayOfWeek> =
        ORDER.filterIndexed { index, _ -> (mask shr index) and 1 == 1 }.toSet()

    fun fromSet(days: Set<DayOfWeek>): Int =
        ORDER.foldIndexed(0) { index, acc, day ->
            if (day in days) acc or (1 shl index) else acc
        }

    val DEFAULT_MON_TO_SAT: Int = fromSet(ORDER.take(6).toSet())
}

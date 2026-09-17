package com.dailycallsreview.app.data.db

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek

class WorkingDaysMaskTest {

    @Test
    fun `round-trips a Monday-to-Saturday set`() {
        val days = setOf(
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY
        )
        val mask = WorkingDaysMask.fromSet(days)
        assertEquals(days, WorkingDaysMask.toSet(mask))
    }

    @Test
    fun `round-trips an empty set`() {
        assertEquals(emptySet<DayOfWeek>(), WorkingDaysMask.toSet(WorkingDaysMask.fromSet(emptySet())))
    }

    @Test
    fun `round-trips a single day`() {
        val days = setOf(DayOfWeek.SUNDAY)
        assertEquals(days, WorkingDaysMask.toSet(WorkingDaysMask.fromSet(days)))
    }

    @Test
    fun `default mask is Monday through Saturday`() {
        val expected = setOf(
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY
        )
        assertEquals(expected, WorkingDaysMask.toSet(WorkingDaysMask.DEFAULT_MON_TO_SAT))
    }
}

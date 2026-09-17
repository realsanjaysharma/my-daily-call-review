package com.dailycallsreview.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset

class CallAggregatorTest {

    private val zone = ZoneOffset.UTC
    private val schedule = WorkSchedule(
        workStart = LocalTime.of(9, 0),
        workEnd = LocalTime.of(18, 0),
        workingDays = setOf(
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY
        ),
        holidays = setOf(LocalDate.of(2026, 1, 26))
    )

    private fun millisAt(date: LocalDate, time: LocalTime): Long =
        LocalDateTime.of(date, time).toInstant(zone).toEpochMilli()

    private fun record(
        date: LocalDate,
        time: LocalTime,
        number: String = "9876543210",
        name: String = "Asha",
        durationSeconds: Int = 120,
        type: CallType = CallType.ANSWERED
    ) = CallRecord(
        phoneNumberLast10 = number,
        contactName = name,
        timestampMillis = millisAt(date, time),
        durationSeconds = durationSeconds,
        type = type
    )

    @Test
    fun `call before working hours is off-hours`() {
        val monday = LocalDate.of(2026, 3, 2)
        val call = record(monday, LocalTime.of(7, 0))
        assertTrue(CallAggregator.isOffHours(call, schedule, zone))
    }

    @Test
    fun `call after working hours is off-hours`() {
        val monday = LocalDate.of(2026, 3, 2)
        val call = record(monday, LocalTime.of(19, 30))
        assertTrue(CallAggregator.isOffHours(call, schedule, zone))
    }

    @Test
    fun `call within working hours on a working day is not off-hours`() {
        val monday = LocalDate.of(2026, 3, 2)
        val call = record(monday, LocalTime.of(11, 0))
        assertTrue(!CallAggregator.isOffHours(call, schedule, zone))
    }

    @Test
    fun `call on a non-working weekday is off-hours even within the time window`() {
        val sunday = LocalDate.of(2026, 3, 1)
        val call = record(sunday, LocalTime.of(11, 0))
        assertTrue(CallAggregator.isOffHours(call, schedule, zone))
    }

    @Test
    fun `call on a holiday is off-hours even on a normally-working day within the time window`() {
        val holiday = LocalDate.of(2026, 1, 26)
        val call = record(holiday, LocalTime.of(11, 0))
        assertTrue(CallAggregator.isOffHours(call, schedule, zone))
    }

    @Test
    fun `daily summary with no calls has null first, last and shift span`() {
        val monday = LocalDate.of(2026, 3, 2)
        val summary = CallAggregator.computeDailySummary(monday, emptyList(), schedule, zone)
        assertEquals(0, summary.totalCalls)
        assertNull(summary.firstCallTime)
        assertNull(summary.lastCallTime)
        assertNull(summary.shiftSpanSeconds)
    }

    @Test
    fun `daily summary with a single call has a zero shift span, not null`() {
        val monday = LocalDate.of(2026, 3, 2)
        val call = record(monday, LocalTime.of(10, 0))
        val summary = CallAggregator.computeDailySummary(monday, listOf(call), schedule, zone)
        assertEquals(1, summary.totalCalls)
        assertEquals(0L, summary.shiftSpanSeconds)
    }

    @Test
    fun `daily summary computes shift span from first to last call and per-coworker breakdown`() {
        val monday = LocalDate.of(2026, 3, 2)
        val calls = listOf(
            record(monday, LocalTime.of(9, 2), number = "9876543210", name = "Asha", durationSeconds = 300),
            record(monday, LocalTime.of(12, 0), number = "9000000000", name = "Ravi", durationSeconds = 180),
            record(monday, LocalTime.of(17, 47), number = "9876543210", name = "Asha", durationSeconds = 60)
        )
        val summary = CallAggregator.computeDailySummary(monday, calls, schedule, zone)

        assertEquals(3, summary.totalCalls)
        assertEquals(540L, summary.totalTalkTimeSeconds)
        assertEquals(LocalTime.of(9, 2), summary.firstCallTime?.toLocalTime())
        assertEquals(LocalTime.of(17, 47), summary.lastCallTime?.toLocalTime())
        assertEquals(2, summary.perCoworker.size)
        assertEquals(2, summary.perCoworker["9876543210"]?.callCount)
        assertEquals(360L, summary.perCoworker["9876543210"]?.totalTalkTimeSeconds)
    }

    @Test
    fun `daily summary counts off-hours calls separately while including them in the total`() {
        val monday = LocalDate.of(2026, 3, 2)
        val calls = listOf(
            record(monday, LocalTime.of(10, 0)),
            record(monday, LocalTime.of(20, 0))
        )
        val summary = CallAggregator.computeDailySummary(monday, calls, schedule, zone)
        assertEquals(2, summary.totalCalls)
        assertEquals(1, summary.offHoursCallCount)
    }

    @Test
    fun `range summary merges per-coworker stats, averages shift span across days that had calls, and finds the busiest coworker`() {
        val day1 = LocalDate.of(2026, 3, 2)
        val day2 = LocalDate.of(2026, 3, 3)
        val day3 = LocalDate.of(2026, 3, 4)

        val summary1 = CallAggregator.computeDailySummary(
            day1,
            listOf(
                record(day1, LocalTime.of(9, 0), number = "9876543210", name = "Asha"),
                record(day1, LocalTime.of(17, 0), number = "9876543210", name = "Asha")
            ),
            schedule,
            zone
        )
        val summary2 = CallAggregator.computeDailySummary(
            day2,
            listOf(record(day2, LocalTime.of(10, 0), number = "9000000000", name = "Ravi")),
            schedule,
            zone
        )
        val summary3 = CallAggregator.computeDailySummary(day3, emptyList(), schedule, zone)

        val range = CallAggregator.computeRangeSummary(listOf(summary1, summary2, summary3))

        assertEquals(3, range.totalCalls)
        assertEquals(2, range.perCoworker.size)
        assertEquals("Asha", range.busiestCoworker?.displayName)
        // summary1 spans 09:00->17:00 = 28800s, summary2 is a single call = 0s, summary3 has no calls (excluded)
        assertEquals(14400L, range.averageShiftSpanSeconds)
    }
}

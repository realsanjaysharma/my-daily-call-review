package com.dailycallsreview.app.core

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

object CallAggregator {

    /**
     * A call is off-hours if it falls on a holiday, on a non-working day of week, or outside
     * the `[workStart, workEnd]` window — [WorkSchedule.workStart] and [WorkSchedule.workEnd]
     * are inclusive boundaries, so a call exactly at either edge counts as within hours.
     */
    fun isOffHours(record: CallRecord, schedule: WorkSchedule, zone: ZoneId): Boolean {
        val dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(record.timestampMillis), zone)
        val isHoliday = dateTime.toLocalDate() in schedule.holidays
        val isNonWorkingDay = dateTime.dayOfWeek !in schedule.workingDays
        val isOutsideHours = dateTime.toLocalTime() < schedule.workStart || dateTime.toLocalTime() > schedule.workEnd
        return isHoliday || isNonWorkingDay || isOutsideHours
    }

    /**
     * Aggregates one day's [CallRecord]s into a [DailySummary]. `shiftSpanSeconds` is `null`
     * only when there were no calls that day; a single call yields `0`, not `null`. Each
     * coworker's `displayName` prefers the most recent non-null `contactName` seen that day for
     * that number, falling back to the raw number only if none of the day's calls had a name.
     */
    fun computeDailySummary(
        date: LocalDate,
        records: List<CallRecord>,
        schedule: WorkSchedule,
        zone: ZoneId
    ): DailySummary {
        val sortedRecords = records.sortedBy { it.timestampMillis }

        val perCoworker = sortedRecords
            .groupBy { it.phoneNumberLast10 }
            .mapValues { (number, calls) ->
                CoworkerStat(
                    phoneNumberLast10 = number,
                    displayName = calls.mapNotNull { it.contactName }.lastOrNull() ?: number,
                    callCount = calls.size,
                    totalTalkTimeSeconds = calls.sumOf { it.durationSeconds.toLong() }
                )
            }

        val firstCallTime = sortedRecords.firstOrNull()?.let {
            LocalDateTime.ofInstant(Instant.ofEpochMilli(it.timestampMillis), zone)
        }
        val lastCallTime = sortedRecords.lastOrNull()?.let {
            LocalDateTime.ofInstant(Instant.ofEpochMilli(it.timestampMillis), zone)
        }
        val shiftSpanSeconds = if (firstCallTime != null && lastCallTime != null) {
            Duration.between(firstCallTime, lastCallTime).seconds
        } else null

        return DailySummary(
            date = date,
            totalCalls = sortedRecords.size,
            totalTalkTimeSeconds = sortedRecords.sumOf { it.durationSeconds.toLong() },
            perCoworker = perCoworker,
            firstCallTime = firstCallTime,
            lastCallTime = lastCallTime,
            shiftSpanSeconds = shiftSpanSeconds,
            offHoursCallCount = sortedRecords.count { isOffHours(it, schedule, zone) },
            isHoliday = date in schedule.holidays,
            isNonWorkingDay = date.dayOfWeek !in schedule.workingDays
        )
    }

    /**
     * Composes multiple [DailySummary]s into a [RangeSummary]. `averageShiftSpanSeconds` is
     * `null` only when no day in the range had any calls; it otherwise averages over days that
     * had calls, excluding call-free days entirely. A merged coworker's `displayName` keeps an
     * already-known real name rather than letting a later no-name day overwrite it with a raw
     * number fallback. `busiestCoworker` breaks ties by `callCount` first, then
     * `totalTalkTimeSeconds`.
     */
    fun computeRangeSummary(dailySummaries: List<DailySummary>): RangeSummary {
        require(dailySummaries.isNotEmpty()) { "Cannot summarize an empty range" }

        val sortedDays = dailySummaries.sortedBy { it.date }
        val mergedCoworkers = mutableMapOf<String, CoworkerStat>()
        for (day in sortedDays) {
            for ((number, stat) in day.perCoworker) {
                val existing = mergedCoworkers[number]
                mergedCoworkers[number] = if (existing == null) {
                    stat
                } else {
                    val resolvedDisplayName = when {
                        existing.displayName != existing.phoneNumberLast10 && stat.displayName == stat.phoneNumberLast10 -> existing.displayName
                        else -> stat.displayName
                    }
                    existing.copy(
                        displayName = resolvedDisplayName,
                        callCount = existing.callCount + stat.callCount,
                        totalTalkTimeSeconds = existing.totalTalkTimeSeconds + stat.totalTalkTimeSeconds
                    )
                }
            }
        }

        val spans = sortedDays.mapNotNull { it.shiftSpanSeconds }
        val averageShiftSpanSeconds = if (spans.isEmpty()) null else spans.sum() / spans.size

        val busiestCoworker = mergedCoworkers.values.maxWithOrNull(
            compareBy({ it.callCount }, { it.totalTalkTimeSeconds })
        )

        return RangeSummary(
            startDate = sortedDays.first().date,
            endDate = sortedDays.last().date,
            totalCalls = sortedDays.sumOf { it.totalCalls },
            totalTalkTimeSeconds = sortedDays.sumOf { it.totalTalkTimeSeconds },
            perCoworker = mergedCoworkers,
            averageShiftSpanSeconds = averageShiftSpanSeconds,
            busiestCoworker = busiestCoworker,
            offHoursCallCount = sortedDays.sumOf { it.offHoursCallCount },
            holidayCount = sortedDays.count { it.isHoliday },
            nonWorkingDayCount = sortedDays.count { it.isNonWorkingDay }
        )
    }
}

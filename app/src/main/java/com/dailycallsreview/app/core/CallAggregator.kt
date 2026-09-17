package com.dailycallsreview.app.core

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

object CallAggregator {

    fun isOffHours(record: CallRecord, schedule: WorkSchedule, zone: ZoneId): Boolean {
        val dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(record.timestampMillis), zone)
        val isHoliday = dateTime.toLocalDate() in schedule.holidays
        val isNonWorkingDay = dateTime.dayOfWeek !in schedule.workingDays
        val isOutsideHours = dateTime.toLocalTime() < schedule.workStart || dateTime.toLocalTime() > schedule.workEnd
        return isHoliday || isNonWorkingDay || isOutsideHours
    }

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
                    displayName = calls.last().contactName ?: number,
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
                    existing.copy(
                        displayName = stat.displayName,
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

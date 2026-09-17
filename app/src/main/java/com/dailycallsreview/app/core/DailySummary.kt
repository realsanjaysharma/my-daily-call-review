package com.dailycallsreview.app.core

import java.time.LocalDate
import java.time.LocalDateTime

data class DailySummary(
    val date: LocalDate,
    val totalCalls: Int,
    val totalTalkTimeSeconds: Long,
    val perCoworker: Map<String, CoworkerStat>,
    val firstCallTime: LocalDateTime?,
    val lastCallTime: LocalDateTime?,
    val shiftSpanSeconds: Long?,
    val offHoursCallCount: Int,
    val isHoliday: Boolean,
    val isNonWorkingDay: Boolean
)

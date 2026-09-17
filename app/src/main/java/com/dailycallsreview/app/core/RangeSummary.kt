package com.dailycallsreview.app.core

import java.time.LocalDate

data class RangeSummary(
    val startDate: LocalDate,
    val endDate: LocalDate,
    val totalCalls: Int,
    val totalTalkTimeSeconds: Long,
    val perCoworker: Map<String, CoworkerStat>,
    val averageShiftSpanSeconds: Long?,
    val busiestCoworker: CoworkerStat?,
    val offHoursCallCount: Int,
    val holidayCount: Int,
    val nonWorkingDayCount: Int
)

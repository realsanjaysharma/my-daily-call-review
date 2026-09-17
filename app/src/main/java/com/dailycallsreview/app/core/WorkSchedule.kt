package com.dailycallsreview.app.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

data class WorkSchedule(
    val workStart: LocalTime,
    val workEnd: LocalTime,
    val workingDays: Set<DayOfWeek>,
    val holidays: Set<LocalDate>
)

package com.dailycallsreview.app.data

import com.dailycallsreview.app.core.WorkSchedule
import com.dailycallsreview.app.data.db.AppSettingsDao
import com.dailycallsreview.app.data.db.AppSettingsEntity
import com.dailycallsreview.app.data.db.HolidayDao
import com.dailycallsreview.app.data.db.HolidayEntity
import com.dailycallsreview.app.data.db.WorkingDaysMask
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

private val DEFAULT_SETTINGS = AppSettingsEntity(
    workStartMinutes = 9 * 60,
    workEndMinutes = 18 * 60,
    workingDaysMask = WorkingDaysMask.DEFAULT_MON_TO_SAT
)

class SettingsRepository(
    private val settingsDao: AppSettingsDao,
    private val holidayDao: HolidayDao
) {
    fun observeWorkSchedule(): Flow<WorkSchedule> =
        combine(settingsDao.observe(), holidayDao.observeAll()) { settingsEntity, holidayEntities ->
            val settings = settingsEntity ?: DEFAULT_SETTINGS
            WorkSchedule(
                workStart = LocalTime.ofSecondOfDay(settings.workStartMinutes * 60L),
                workEnd = LocalTime.ofSecondOfDay(settings.workEndMinutes * 60L),
                workingDays = WorkingDaysMask.toSet(settings.workingDaysMask),
                holidays = holidayEntities.map { LocalDate.parse(it.date) }.toSet()
            )
        }

    suspend fun saveWorkSchedule(workStart: LocalTime, workEnd: LocalTime, workingDays: Set<DayOfWeek>) {
        settingsDao.upsert(
            AppSettingsEntity(
                workStartMinutes = workStart.toSecondOfDay() / 60,
                workEndMinutes = workEnd.toSecondOfDay() / 60,
                workingDaysMask = WorkingDaysMask.fromSet(workingDays)
            )
        )
    }

    suspend fun addHoliday(date: LocalDate, label: String) {
        holidayDao.insert(HolidayEntity(date.toString(), label))
    }

    suspend fun removeHoliday(date: LocalDate) {
        holidayDao.deleteByDate(date.toString())
    }
}

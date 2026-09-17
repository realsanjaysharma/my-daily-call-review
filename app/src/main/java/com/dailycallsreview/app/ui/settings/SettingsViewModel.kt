package com.dailycallsreview.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailycallsreview.app.core.WorkSchedule
import com.dailycallsreview.app.data.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

class SettingsViewModel(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    // This screen is the only writer of settings, so once loaded, local state is authoritative.
    // Edits update _schedule synchronously so rapid successive edits correctly accumulate off
    // each other; persistence to the DB is fire-and-forget and never read back into this state,
    // since a stale echo could otherwise overwrite an edit made after that write was issued.
    private val _schedule = MutableStateFlow<WorkSchedule?>(null)
    val schedule: StateFlow<WorkSchedule?> = _schedule.asStateFlow()

    init {
        viewModelScope.launch {
            _schedule.value = settingsRepository.observeWorkSchedule().first()
        }
    }

    fun saveWorkHours(start: LocalTime, end: LocalTime, workingDays: Set<DayOfWeek>) {
        _schedule.value = _schedule.value?.copy(workStart = start, workEnd = end, workingDays = workingDays)
        viewModelScope.launch { settingsRepository.saveWorkSchedule(start, end, workingDays) }
    }

    fun addHoliday(date: LocalDate, label: String) {
        _schedule.value = _schedule.value?.let { it.copy(holidays = it.holidays + date) }
        viewModelScope.launch { settingsRepository.addHoliday(date, label) }
    }

    fun removeHoliday(date: LocalDate) {
        _schedule.value = _schedule.value?.let { it.copy(holidays = it.holidays - date) }
        viewModelScope.launch { settingsRepository.removeHoliday(date) }
    }
}

package com.dailycallsreview.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailycallsreview.app.core.WorkSchedule
import com.dailycallsreview.app.data.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

class SettingsViewModel(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val schedule: StateFlow<WorkSchedule?> = settingsRepository.observeWorkSchedule()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun saveWorkHours(start: LocalTime, end: LocalTime, workingDays: Set<DayOfWeek>) {
        viewModelScope.launch { settingsRepository.saveWorkSchedule(start, end, workingDays) }
    }

    fun addHoliday(date: LocalDate, label: String) {
        viewModelScope.launch { settingsRepository.addHoliday(date, label) }
    }

    fun removeHoliday(date: LocalDate) {
        viewModelScope.launch { settingsRepository.removeHoliday(date) }
    }
}

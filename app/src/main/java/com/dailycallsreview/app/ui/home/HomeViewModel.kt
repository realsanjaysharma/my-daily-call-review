package com.dailycallsreview.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailycallsreview.app.core.CallAggregator
import com.dailycallsreview.app.core.DailySummary
import com.dailycallsreview.app.core.WorkSchedule
import com.dailycallsreview.app.data.SettingsRepository
import com.dailycallsreview.app.data.TeamRepository
import com.dailycallsreview.app.data.calllog.CallLogRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

class HomeViewModel(
    private val callLogRepository: CallLogRepository,
    private val teamRepository: TeamRepository,
    private val settingsRepository: SettingsRepository,
    private val zone: ZoneId = ZoneId.systemDefault()
) : ViewModel() {

    private val refreshTrigger = MutableStateFlow(0)
    private val _todaySummary = MutableStateFlow<DailySummary?>(null)
    val todaySummary: StateFlow<DailySummary?> = _todaySummary.asStateFlow()

    init {
        viewModelScope.launch {
            combine(settingsRepository.observeWorkSchedule(), refreshTrigger) { schedule, _ -> schedule }
                .collectLatest { schedule -> refresh(schedule) }
        }
    }

    private suspend fun refresh(schedule: WorkSchedule) {
        val today = LocalDate.now(zone)
        val startMillis = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val endMillis = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val taggedNumbers = teamRepository.getTaggedNumbersOnce()
        val records = callLogRepository.getCallsBetween(startMillis, endMillis, taggedNumbers)
        _todaySummary.value = CallAggregator.computeDailySummary(today, records, schedule, zone)
    }

    fun refreshNow() {
        refreshTrigger.value += 1
    }
}

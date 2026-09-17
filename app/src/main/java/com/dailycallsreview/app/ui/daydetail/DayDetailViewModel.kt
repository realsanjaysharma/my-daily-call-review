package com.dailycallsreview.app.ui.daydetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailycallsreview.app.core.CallAggregator
import com.dailycallsreview.app.core.DailySummary
import com.dailycallsreview.app.data.SettingsRepository
import com.dailycallsreview.app.data.TeamRepository
import com.dailycallsreview.app.data.calllog.CallLogRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

class DayDetailViewModel(
    private val date: LocalDate,
    private val callLogRepository: CallLogRepository,
    private val teamRepository: TeamRepository,
    private val settingsRepository: SettingsRepository,
    private val zone: ZoneId = ZoneId.systemDefault()
) : ViewModel() {

    private val _summary = MutableStateFlow<DailySummary?>(null)
    val summary: StateFlow<DailySummary?> = _summary.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.observeWorkSchedule().collectLatest { schedule ->
                val taggedNumbers = teamRepository.getTaggedNumbersOnce()
                val startMillis = date.atStartOfDay(zone).toInstant().toEpochMilli()
                val endMillis = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                val records = callLogRepository.getCallsBetween(startMillis, endMillis, taggedNumbers)
                _summary.value = CallAggregator.computeDailySummary(date, records, schedule, zone)
            }
        }
    }
}

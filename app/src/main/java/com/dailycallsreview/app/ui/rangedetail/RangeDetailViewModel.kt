package com.dailycallsreview.app.ui.rangedetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailycallsreview.app.core.CallAggregator
import com.dailycallsreview.app.core.CallRecord
import com.dailycallsreview.app.core.DailySummary
import com.dailycallsreview.app.core.RangeSummary
import com.dailycallsreview.app.data.SettingsRepository
import com.dailycallsreview.app.data.TeamRepository
import com.dailycallsreview.app.data.calllog.CallLogRepository
import com.dailycallsreview.app.ui.history.HistoryFilter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class RangeDetailUiState(
    val filter: HistoryFilter = HistoryFilter.ALL,
    val summary: RangeSummary? = null,
    val filteredDays: List<DailySummary> = emptyList(),
    val offHoursCalls: List<CallRecord> = emptyList()
)

class RangeDetailViewModel(
    startDate: LocalDate,
    endDate: LocalDate,
    private val callLogRepository: CallLogRepository,
    private val teamRepository: TeamRepository,
    private val settingsRepository: SettingsRepository,
    private val zone: ZoneId = ZoneId.systemDefault()
) : ViewModel() {

    private val rangeStart = minOf(startDate, endDate)
    private val rangeEnd = maxOf(startDate, endDate)

    private val filter = MutableStateFlow(HistoryFilter.ALL)

    private val _uiState = MutableStateFlow(RangeDetailUiState())
    val uiState: StateFlow<RangeDetailUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(filter, settingsRepository.observeWorkSchedule()) { currentFilter, schedule ->
                currentFilter to schedule
            }.collectLatest { (currentFilter, schedule) ->
                val taggedNumbers = teamRepository.getTaggedNumbersOnce()
                val startMillis = rangeStart.atStartOfDay(zone).toInstant().toEpochMilli()
                val endMillis = rangeEnd.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                val records = callLogRepository.getCallsBetween(startMillis, endMillis, taggedNumbers)
                val recordsByDate = records.groupBy {
                    Instant.ofEpochMilli(it.timestampMillis).atZone(zone).toLocalDate()
                }
                val dailySummaries = generateSequence(rangeStart) { it.plusDays(1) }
                    .takeWhile { !it.isAfter(rangeEnd) }
                    .map { date -> CallAggregator.computeDailySummary(date, recordsByDate[date].orEmpty(), schedule, zone) }
                    .toList()

                val offHoursCalls = records
                    .filter { CallAggregator.isOffHours(it, schedule, zone) }
                    .sortedBy { it.timestampMillis }

                val filteredDays = when (currentFilter) {
                    HistoryFilter.ALL -> dailySummaries
                    HistoryFilter.OFF_DAYS -> dailySummaries.filter { it.isNonWorkingDay }
                    HistoryFilter.HOLIDAYS -> dailySummaries.filter { it.isHoliday }
                    HistoryFilter.OUTSIDE_HOURS -> dailySummaries
                }

                _uiState.value = RangeDetailUiState(
                    filter = currentFilter,
                    summary = CallAggregator.computeRangeSummary(dailySummaries),
                    filteredDays = filteredDays,
                    offHoursCalls = if (currentFilter == HistoryFilter.OUTSIDE_HOURS) offHoursCalls else emptyList()
                )
            }
        }
    }

    fun setFilter(newFilter: HistoryFilter) { filter.value = newFilter }
}

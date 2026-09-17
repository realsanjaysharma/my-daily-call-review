package com.dailycallsreview.app.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailycallsreview.app.core.CallAggregator
import com.dailycallsreview.app.core.CallRecord
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
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

enum class HistoryViewMode { MONTH, WEEK }
enum class HistoryFilter { ALL, OFF_DAYS, HOLIDAYS, OUTSIDE_HOURS }

data class HistoryUiState(
    val viewMode: HistoryViewMode = HistoryViewMode.MONTH,
    val filter: HistoryFilter = HistoryFilter.ALL,
    val anchorDate: LocalDate = LocalDate.now(),
    val days: List<DailySummary> = emptyList(),
    val offHoursCalls: List<CallRecord> = emptyList()
)

private data class LoadParams(
    val mode: HistoryViewMode,
    val filter: HistoryFilter,
    val anchor: LocalDate,
    val schedule: WorkSchedule
)

class HistoryViewModel(
    private val callLogRepository: CallLogRepository,
    private val teamRepository: TeamRepository,
    private val settingsRepository: SettingsRepository,
    private val zone: ZoneId = ZoneId.systemDefault()
) : ViewModel() {

    private val viewMode = MutableStateFlow(HistoryViewMode.MONTH)
    private val filter = MutableStateFlow(HistoryFilter.ALL)
    private val anchorDate = MutableStateFlow(LocalDate.now(zone))

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                viewMode, filter, anchorDate, settingsRepository.observeWorkSchedule()
            ) { mode, currentFilter, anchor, schedule ->
                LoadParams(mode, currentFilter, anchor, schedule)
            }.collectLatest { params -> loadRange(params) }
        }
    }

    fun setViewMode(mode: HistoryViewMode) { viewMode.value = mode }
    fun setFilter(newFilter: HistoryFilter) { filter.value = newFilter }
    fun setAnchorDate(date: LocalDate) { anchorDate.value = date }

    private suspend fun loadRange(params: LoadParams) {
        val rangeStart = when (params.mode) {
            HistoryViewMode.MONTH -> params.anchor.withDayOfMonth(1)
            HistoryViewMode.WEEK -> params.anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        }
        val rangeEnd = when (params.mode) {
            HistoryViewMode.MONTH -> params.anchor.withDayOfMonth(params.anchor.lengthOfMonth())
            HistoryViewMode.WEEK -> rangeStart.plusDays(6)
        }

        val taggedNumbers = teamRepository.getTaggedNumbersOnce()
        val startMillis = rangeStart.atStartOfDay(zone).toInstant().toEpochMilli()
        val endMillis = rangeEnd.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val records = callLogRepository.getCallsBetween(startMillis, endMillis, taggedNumbers)
        val recordsByDate = records.groupBy { Instant.ofEpochMilli(it.timestampMillis).atZone(zone).toLocalDate() }

        val days = generateSequence(rangeStart) { it.plusDays(1) }
            .takeWhile { !it.isAfter(rangeEnd) }
            .map { date -> CallAggregator.computeDailySummary(date, recordsByDate[date].orEmpty(), params.schedule, zone) }
            .toList()

        val offHoursCalls = records
            .filter { CallAggregator.isOffHours(it, params.schedule, zone) }
            .sortedBy { it.timestampMillis }

        val filteredDays = when (params.filter) {
            HistoryFilter.ALL -> days
            HistoryFilter.OFF_DAYS -> days.filter { it.isNonWorkingDay }
            HistoryFilter.HOLIDAYS -> days.filter { it.isHoliday }
            HistoryFilter.OUTSIDE_HOURS -> days
        }

        _uiState.value = HistoryUiState(
            viewMode = params.mode,
            filter = params.filter,
            anchorDate = params.anchor,
            days = filteredDays,
            offHoursCalls = if (params.filter == HistoryFilter.OUTSIDE_HOURS) offHoursCalls else emptyList()
        )
    }
}

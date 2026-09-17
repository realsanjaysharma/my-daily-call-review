package com.dailycallsreview.app.ui.rangedetail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.dailycallsreview.app.DailyCallsReviewApplication
import com.dailycallsreview.app.ui.export.CsvExporter
import com.dailycallsreview.app.ui.history.HistoryFilter
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun RangeDetailScreen(
    app: DailyCallsReviewApplication,
    startDate: LocalDate,
    endDate: LocalDate,
    onOpenDay: (LocalDate) -> Unit
) {
    val viewModel: RangeDetailViewModel = viewModel(
        key = "$startDate-$endDate",
        factory = viewModelFactory {
            initializer {
                RangeDetailViewModel(startDate, endDate, app.callLogRepository, app.teamRepository, app.settingsRepository)
            }
        }
    )
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    Column {
        Row {
            HistoryFilter.entries.forEach { filterOption ->
                FilterChip(
                    selected = state.filter == filterOption,
                    onClick = { viewModel.setFilter(filterOption) },
                    label = { Text(filterOption.name) }
                )
            }
        }

        when (state.filter) {
            HistoryFilter.ALL -> state.summary?.let { s ->
                Column {
                    Text("Range: ${s.startDate} to ${s.endDate}")
                    Text("Total calls: ${s.totalCalls}")
                    Text("Total talk time: ${s.totalTalkTimeSeconds / 60} min")
                    Text("Average shift span: ${s.averageShiftSpanSeconds?.let { "${it / 3600}h" } ?: "—"}")
                    Text("Busiest coworker: ${s.busiestCoworker?.displayName ?: "—"}")
                    Text("Off-hours calls: ${s.offHoursCallCount}")
                    Text("Holidays in range: ${s.holidayCount}")
                    Text("Non-working days in range: ${s.nonWorkingDayCount}")
                    s.perCoworker.values.forEach { stat ->
                        Text("${stat.displayName}: ${stat.callCount} calls, ${stat.totalTalkTimeSeconds / 60} min")
                    }
                }
            } ?: Text("Loading…")
            HistoryFilter.OUTSIDE_HOURS -> LazyColumn {
                items(state.offHoursCalls) { call ->
                    val time = Instant.ofEpochMilli(call.timestampMillis).atZone(ZoneId.systemDefault())
                    Text("${call.contactName ?: call.phoneNumberLast10} — $time")
                }
            }
            else -> LazyColumn {
                items(state.filteredDays) { day ->
                    Row {
                        Text("${day.date}: ${day.totalCalls} calls")
                        Button(onClick = { onOpenDay(day.date) }) { Text("View") }
                    }
                }
            }
        }

        Button(onClick = {
            when (state.filter) {
                HistoryFilter.ALL -> state.summary?.let {
                    CsvExporter.exportRangeAndShare(context, "range_export.csv", it)
                }
                HistoryFilter.OUTSIDE_HOURS -> CsvExporter.exportCallsAndShare(
                    context, "range_export.csv", state.offHoursCalls, ZoneId.systemDefault()
                )
                else -> CsvExporter.exportDailySummariesAndShare(context, "range_export.csv", state.filteredDays)
            }
        }) { Text("Export CSV") }
    }
}

package com.dailycallsreview.app.ui.history

import android.app.DatePickerDialog
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
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun HistoryScreen(
    app: DailyCallsReviewApplication,
    onOpenDay: (LocalDate) -> Unit,
    onOpenRange: (LocalDate, LocalDate) -> Unit
) {
    val viewModel: HistoryViewModel = viewModel(
        factory = viewModelFactory {
            initializer { HistoryViewModel(app.callLogRepository, app.teamRepository, app.settingsRepository) }
        }
    )
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    Column {
        Row {
            Button(onClick = { viewModel.setViewMode(HistoryViewMode.MONTH) }) { Text("Month") }
            Button(onClick = { viewModel.setViewMode(HistoryViewMode.WEEK) }) { Text("Week") }
            Button(onClick = {
                DatePickerDialog(context, { _, y1, m1, d1 ->
                    val start = LocalDate.of(y1, m1 + 1, d1)
                    DatePickerDialog(context, { _, y2, m2, d2 ->
                        onOpenRange(start, LocalDate.of(y2, m2 + 1, d2))
                    }, start.year, start.monthValue - 1, start.dayOfMonth).show()
                }, state.anchorDate.year, state.anchorDate.monthValue - 1, state.anchorDate.dayOfMonth).show()
            }) { Text("Custom range") }
            Button(onClick = {
                if (state.filter == HistoryFilter.OUTSIDE_HOURS) {
                    CsvExporter.exportCallsAndShare(context, "history_export.csv", state.offHoursCalls, ZoneId.systemDefault())
                } else {
                    CsvExporter.exportDailySummariesAndShare(context, "history_export.csv", state.days)
                }
            }) { Text("Export CSV") }
        }
        Row {
            HistoryFilter.entries.forEach { filterOption ->
                FilterChip(
                    selected = state.filter == filterOption,
                    onClick = { viewModel.setFilter(filterOption) },
                    label = { Text(filterOption.name) }
                )
            }
        }
        if (state.filter == HistoryFilter.OUTSIDE_HOURS) {
            LazyColumn {
                items(state.offHoursCalls) { call ->
                    val time = Instant.ofEpochMilli(call.timestampMillis).atZone(ZoneId.systemDefault())
                    Text("${call.contactName ?: call.phoneNumberLast10} — $time")
                }
            }
        } else {
            LazyColumn {
                items(state.days) { day ->
                    Row {
                        Text("${day.date}: ${day.totalCalls} calls")
                        Button(onClick = { onOpenDay(day.date) }) { Text("View") }
                    }
                }
            }
        }
    }
}

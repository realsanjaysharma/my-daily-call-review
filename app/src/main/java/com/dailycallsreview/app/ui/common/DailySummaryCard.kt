package com.dailycallsreview.app.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dailycallsreview.app.core.DailySummary
import java.time.format.DateTimeFormatter

private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("hh:mm a")

@Composable
fun DailySummaryCard(summary: DailySummary) {
    Card(modifier = Modifier.padding(16.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Date: ${summary.date}")
            Text("Total calls: ${summary.totalCalls}")
            Text("Total talk time: ${summary.totalTalkTimeSeconds / 60} min")
            Text("First call: ${summary.firstCallTime?.format(TIME_FORMAT) ?: "—"}")
            Text("Last call: ${summary.lastCallTime?.format(TIME_FORMAT) ?: "—"}")
            Text("Shift span: ${summary.shiftSpanSeconds?.let { formatDuration(it) } ?: "—"}")
            if (summary.offHoursCallCount > 0) {
                Text("⚠ ${summary.offHoursCallCount} call(s) outside working hours")
            }
            if (summary.isHoliday) Text("🎉 Holiday")
            if (summary.isNonWorkingDay) Text("Off day")
            Text("Per coworker:")
            summary.perCoworker.values.forEach { stat ->
                Text("  ${stat.displayName}: ${stat.callCount} calls, ${stat.totalTalkTimeSeconds / 60} min")
            }
        }
    }
}

private fun formatDuration(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    return "${hours}h ${minutes}m"
}

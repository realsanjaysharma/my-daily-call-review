package com.dailycallsreview.app.ui.export

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.dailycallsreview.app.core.CallRecord
import com.dailycallsreview.app.core.DailySummary
import com.dailycallsreview.app.core.RangeSummary
import java.io.File
import java.io.FileWriter
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object CsvExporter {

    private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("hh:mm a")

    fun exportDailySummariesAndShare(context: Context, fileName: String, summaries: List<DailySummary>) {
        val file = newExportFile(context, fileName)
        FileWriter(file).use { writer ->
            writer.appendLine(
                "Date,Total Calls,Total Talk Time (min),First Call,Last Call,Shift Span,Off-Hours Calls,Holiday,Non-Working Day"
            )
            for (summary in summaries) {
                writer.appendLine(
                    listOf(
                        summary.date.toString(),
                        summary.totalCalls.toString(),
                        (summary.totalTalkTimeSeconds / 60).toString(),
                        summary.firstCallTime?.format(TIME_FORMAT) ?: "",
                        summary.lastCallTime?.format(TIME_FORMAT) ?: "",
                        summary.shiftSpanSeconds?.let { formatDuration(it) } ?: "",
                        summary.offHoursCallCount.toString(),
                        summary.isHoliday.toString(),
                        summary.isNonWorkingDay.toString()
                    ).joinToString(",")
                )
            }
        }
        shareFile(context, file)
    }

    fun exportCallsAndShare(context: Context, fileName: String, calls: List<CallRecord>, zone: ZoneId) {
        val file = newExportFile(context, fileName)
        FileWriter(file).use { writer ->
            writer.appendLine("Date,Time,Contact,Duration (sec),Type")
            for (call in calls) {
                val dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(call.timestampMillis), zone)
                writer.appendLine(
                    listOf(
                        dateTime.toLocalDate().toString(),
                        dateTime.toLocalTime().format(TIME_FORMAT),
                        csvField(call.contactName ?: call.phoneNumberLast10),
                        call.durationSeconds.toString(),
                        call.type.name
                    ).joinToString(",")
                )
            }
        }
        shareFile(context, file)
    }

    fun exportRangeAndShare(context: Context, fileName: String, summary: RangeSummary) {
        val file = newExportFile(context, fileName)
        FileWriter(file).use { writer ->
            writer.appendLine(
                listOf("Range", "${summary.startDate} to ${summary.endDate}").joinToString(",")
            )
            writer.appendLine(listOf("Total Calls", summary.totalCalls.toString()).joinToString(","))
            writer.appendLine(
                listOf("Total Talk Time (min)", (summary.totalTalkTimeSeconds / 60).toString()).joinToString(",")
            )
            writer.appendLine(
                listOf(
                    "Average Shift Span",
                    summary.averageShiftSpanSeconds?.let { formatDuration(it) } ?: ""
                ).joinToString(",")
            )
            writer.appendLine(
                listOf("Busiest Coworker", csvField(summary.busiestCoworker?.displayName ?: "")).joinToString(",")
            )
            writer.appendLine(listOf("Off-Hours Calls", summary.offHoursCallCount.toString()).joinToString(","))
            writer.appendLine(listOf("Holidays In Range", summary.holidayCount.toString()).joinToString(","))
            writer.appendLine(
                listOf("Non-Working Days In Range", summary.nonWorkingDayCount.toString()).joinToString(",")
            )
            writer.appendLine()
            writer.appendLine("Coworker,Calls,Talk Time (min)")
            for (stat in summary.perCoworker.values) {
                writer.appendLine(
                    listOf(
                        csvField(stat.displayName),
                        stat.callCount.toString(),
                        (stat.totalTalkTimeSeconds / 60).toString()
                    ).joinToString(",")
                )
            }
        }
        shareFile(context, file)
    }

    private fun newExportFile(context: Context, fileName: String): File {
        val exportsDir = File(context.cacheDir, "exports").apply { mkdirs() }
        return File(exportsDir, fileName)
    }

    private fun shareFile(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Export Daily Calls Review"))
    }

    private fun formatDuration(seconds: Long): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        return "${hours}h ${minutes}m"
    }

    private fun csvField(value: String): String =
        if (value.contains(',') || value.contains('"') || value.contains('\n')) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
}

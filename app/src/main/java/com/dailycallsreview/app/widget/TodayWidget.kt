package com.dailycallsreview.app.widget

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Column
import androidx.glance.layout.padding
import androidx.glance.text.Text
import androidx.compose.ui.unit.dp
import com.dailycallsreview.app.DailyCallsReviewApplication
import com.dailycallsreview.app.MainActivity
import com.dailycallsreview.app.core.CallAggregator
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.ZoneId

private val REQUIRED_PERMISSIONS = arrayOf(
    Manifest.permission.READ_CALL_LOG,
    Manifest.permission.READ_CONTACTS
)

private fun hasAllPermissions(context: Context): Boolean =
    REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

class TodayWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as DailyCallsReviewApplication

        if (!hasAllPermissions(context)) {
            provideContent {
                Column(modifier = GlanceModifier.padding(12.dp).clickable(actionStartActivity<MainActivity>())) {
                    Text("Open Daily Calls Review to grant permissions")
                }
            }
            return
        }

        val taggedNumbers = app.teamRepository.getTaggedNumbersOnce()

        if (taggedNumbers.isEmpty()) {
            provideContent {
                Column(modifier = GlanceModifier.padding(12.dp).clickable(actionStartActivity<MainActivity>())) {
                    Text("Tag coworkers in the app to see today's stats here")
                }
            }
            return
        }

        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val schedule = app.settingsRepository.observeWorkSchedule().first()
        val startMillis = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val endMillis = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val records = app.callLogRepository.getCallsBetween(startMillis, endMillis, taggedNumbers)
        val summary = CallAggregator.computeDailySummary(today, records, schedule, zone)

        provideContent {
            Column(modifier = GlanceModifier.padding(12.dp).clickable(actionStartActivity<MainActivity>())) {
                Text("Today: ${summary.totalCalls} calls")
                Text("${summary.totalTalkTimeSeconds / 60} min")
            }
        }
    }
}

package com.dailycallsreview.app.ui.daydetail

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.dailycallsreview.app.DailyCallsReviewApplication
import com.dailycallsreview.app.ui.common.DailySummaryCard
import java.time.LocalDate

@Composable
fun DayDetailScreen(app: DailyCallsReviewApplication, date: LocalDate) {
    val viewModel: DayDetailViewModel = viewModel(
        key = date.toString(),
        factory = viewModelFactory {
            initializer {
                DayDetailViewModel(date, app.callLogRepository, app.teamRepository, app.settingsRepository)
            }
        }
    )
    val summary by viewModel.summary.collectAsState()
    summary?.let { DailySummaryCard(it) } ?: Text("Loading…")
}

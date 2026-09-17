package com.dailycallsreview.app.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.dailycallsreview.app.DailyCallsReviewApplication
import com.dailycallsreview.app.ui.common.DailySummaryCard

@Composable
fun HomeScreen(
    app: DailyCallsReviewApplication,
    onOpenHistory: () -> Unit,
    onOpenTeamSetup: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val viewModel: HomeViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                HomeViewModel(app.callLogRepository, app.teamRepository, app.settingsRepository)
            }
        }
    )
    val summary by viewModel.todaySummary.collectAsState()

    Column {
        HomeActionBar(
            onOpenHistory = onOpenHistory,
            onOpenTeamSetup = onOpenTeamSetup,
            onOpenSettings = onOpenSettings,
            onRefresh = viewModel::refreshNow
        )
        summary?.let { DailySummaryCard(it) } ?: Text("Loading today's calls…")
    }
}

@Composable
private fun HomeActionBar(
    onOpenHistory: () -> Unit,
    onOpenTeamSetup: () -> Unit,
    onOpenSettings: () -> Unit,
    onRefresh: () -> Unit
) {
    Row {
        Button(onClick = onOpenHistory) { Text("History") }
        Button(onClick = onOpenTeamSetup) { Text("Team") }
        Button(onClick = onOpenSettings) { Text("Settings") }
        Button(onClick = onRefresh) { Text("Refresh") }
    }
}

package com.dailycallsreview.app.ui.settings

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.dailycallsreview.app.DailyCallsReviewApplication
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

@Composable
fun SettingsScreen(app: DailyCallsReviewApplication) {
    val viewModel: SettingsViewModel = viewModel(
        factory = viewModelFactory {
            initializer { SettingsViewModel(app.settingsRepository) }
        }
    )
    val schedule by viewModel.schedule.collectAsState()
    val context = LocalContext.current

    schedule?.let { current ->
        var workStart by remember(current) { mutableStateOf(current.workStart) }
        var workEnd by remember(current) { mutableStateOf(current.workEnd) }
        var workingDays by remember(current) { mutableStateOf(current.workingDays) }
        var holidayLabel by remember { mutableStateOf("") }
        var holidayDate by remember { mutableStateOf(LocalDate.now()) }

        LazyColumn(modifier = Modifier.padding(16.dp)) {
            item {
                Button(onClick = {
                    TimePickerDialog(context, { _, hour, minute ->
                        workStart = LocalTime.of(hour, minute)
                        viewModel.saveWorkHours(workStart, workEnd, workingDays)
                    }, workStart.hour, workStart.minute, false).show()
                }) { Text("Work start: $workStart") }
            }
            item {
                Button(onClick = {
                    TimePickerDialog(context, { _, hour, minute ->
                        workEnd = LocalTime.of(hour, minute)
                        viewModel.saveWorkHours(workStart, workEnd, workingDays)
                    }, workEnd.hour, workEnd.minute, false).show()
                }) { Text("Work end: $workEnd") }
            }
            items(DayOfWeek.entries) { day ->
                Row {
                    Checkbox(
                        checked = day in workingDays,
                        onCheckedChange = { checked ->
                            workingDays = if (checked) workingDays + day else workingDays - day
                            viewModel.saveWorkHours(workStart, workEnd, workingDays)
                        }
                    )
                    Text(day.name)
                }
            }
            item {
                Row {
                    Button(onClick = {
                        DatePickerDialog(context, { _, year, month, day ->
                            holidayDate = LocalDate.of(year, month + 1, day)
                        }, holidayDate.year, holidayDate.monthValue - 1, holidayDate.dayOfMonth).show()
                    }) { Text("Date: $holidayDate") }
                    TextField(
                        value = holidayLabel,
                        onValueChange = { holidayLabel = it },
                        label = { Text("Label") }
                    )
                    Button(onClick = {
                        viewModel.addHoliday(holidayDate, holidayLabel)
                        holidayLabel = ""
                    }) { Text("Add holiday") }
                }
            }
            items(current.holidays.sorted()) { date ->
                Row {
                    Text(date.toString())
                    Button(onClick = { viewModel.removeHoliday(date) }) { Text("Remove") }
                }
            }
        }
    }
}

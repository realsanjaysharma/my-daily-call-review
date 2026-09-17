package com.dailycallsreview.app

import android.app.Application
import androidx.room.Room
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.dailycallsreview.app.data.SettingsRepository
import com.dailycallsreview.app.data.TeamRepository
import com.dailycallsreview.app.data.calllog.CallLogRepository
import com.dailycallsreview.app.data.contacts.ContactsRepository
import com.dailycallsreview.app.data.db.AppDatabase
import com.dailycallsreview.app.widget.TodayWidgetWorker
import java.util.concurrent.TimeUnit

class DailyCallsReviewApplication : Application() {

    val database: AppDatabase by lazy {
        Room.databaseBuilder(applicationContext, AppDatabase::class.java, "daily-calls-review.db").build()
    }

    val teamRepository: TeamRepository by lazy { TeamRepository(database.taggedContactDao()) }
    val settingsRepository: SettingsRepository by lazy {
        SettingsRepository(database.appSettingsDao(), database.holidayDao())
    }
    val callLogRepository: CallLogRepository by lazy { CallLogRepository(applicationContext) }
    val contactsRepository: ContactsRepository by lazy { ContactsRepository(applicationContext) }

    override fun onCreate() {
        super.onCreate()
        val request = PeriodicWorkRequestBuilder<TodayWidgetWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "today_widget_refresh",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}

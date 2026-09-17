package com.dailycallsreview.app

import android.app.Application
import androidx.room.Room
import com.dailycallsreview.app.data.SettingsRepository
import com.dailycallsreview.app.data.TeamRepository
import com.dailycallsreview.app.data.calllog.CallLogRepository
import com.dailycallsreview.app.data.contacts.ContactsRepository
import com.dailycallsreview.app.data.db.AppDatabase

class DailyCallsReviewApplication : Application() {

    val database: AppDatabase by lazy {
        Room.databaseBuilder(this, AppDatabase::class.java, "daily-calls-review.db").build()
    }

    val teamRepository: TeamRepository by lazy { TeamRepository(database.taggedContactDao()) }
    val settingsRepository: SettingsRepository by lazy {
        SettingsRepository(database.appSettingsDao(), database.holidayDao())
    }
    val callLogRepository: CallLogRepository by lazy { CallLogRepository(applicationContext) }
    val contactsRepository: ContactsRepository by lazy { ContactsRepository(applicationContext) }
}

package com.travelindex.app

import android.app.Application
import com.travelindex.app.reminders.NotificationChannels
import com.travelindex.app.reminders.ReminderCoordinator
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Inject

@HiltAndroidApp
class TravelIndexApp : Application() {
    @Inject lateinit var reminders: ReminderCoordinator
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.create(this)
        reminders.start(appScope)
    }
}

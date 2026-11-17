package com.travelindex.app

import android.app.Application
import android.content.SharedPreferences
import com.travelindex.app.data.AppShortcuts
import com.travelindex.app.data.LanguageManager
import com.travelindex.app.reminders.NotificationChannels
import com.travelindex.app.data.repo.TripRepository
import com.travelindex.app.reminders.ReminderCoordinator
import com.travelindex.app.widget.NextEventWidget
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class TravelIndexApp : Application() {
    @Inject lateinit var reminders: ReminderCoordinator
    @Inject lateinit var trips: TripRepository
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Al cambiar el idioma en Perfil, lo que se ve fuera de la app (accesos directos, canal de avisos, widget) se rehace al momento.
    private val languageListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        appScope.launch {
            NotificationChannels.create(this@TravelIndexApp)
            AppShortcuts.install(this@TravelIndexApp)
            trips.bundles.value?.let { NextEventWidget.updateAll(this@TravelIndexApp, it) }
        }
    }

    override fun onCreate() {
        // Idioma elegido como predeterminado del proceso desde el primer momento (también si arranca por un aviso o el widget).
        LanguageManager.localize(this)
        super.onCreate()
        LanguageManager.register(this, languageListener)
        NotificationChannels.create(this)
        AppShortcuts.install(this)
        reminders.start(appScope)
        // El widget de la pantalla de inicio se refresca cada vez que cambian los datos (si hay alguno puesto).
        appScope.launch { trips.bundles.filterNotNull().collect { NextEventWidget.updateAll(this@TravelIndexApp, it) } }
    }
}

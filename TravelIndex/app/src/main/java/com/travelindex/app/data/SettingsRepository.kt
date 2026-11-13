package com.travelindex.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.travelindex.app.domain.AppSettings
import com.travelindex.app.domain.DateFormatOption
import com.travelindex.app.domain.ThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Preferencias del usuario (DataStore). Todo se guarda en el dispositivo. */
@Singleton
class SettingsRepository @Inject constructor(@ApplicationContext private val context: Context) {

    private object K {
        val NAME = stringPreferencesKey("user_name")
        val PHOTO = stringPreferencesKey("photo_path")
        val CURRENCY = stringPreferencesKey("default_currency")
        val DATE = stringPreferencesKey("date_format")
        val THEME = stringPreferencesKey("theme_mode")
        val NOTIF = booleanPreferencesKey("notifications_enabled")
        val FLIGHT_H = intPreferencesKey("flight_lead_hours")
        val EVENT_M = intPreferencesKey("event_lead_minutes")
        val DOC_D = intPreferencesKey("doc_warn_days")
        val HOUR = intPreferencesKey("reminder_hour")
        val SECURE = booleanPreferencesKey("block_screenshots")
    }

    private fun read(p: Preferences): AppSettings {
        val d = AppSettings()
        return AppSettings(
            userName = p[K.NAME].orEmpty(),
            photoPath = p[K.PHOTO],
            defaultCurrency = p[K.CURRENCY] ?: d.defaultCurrency,
            dateFormat = p[K.DATE]?.let { runCatching { DateFormatOption.valueOf(it) }.getOrNull() } ?: d.dateFormat,
            themeMode = (p[K.THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: d.themeMode)
                .let { if (it == ThemeMode.SYSTEM) ThemeMode.LIGHT else it },
            notificationsEnabled = p[K.NOTIF] ?: d.notificationsEnabled,
            flightLeadHours = p[K.FLIGHT_H] ?: d.flightLeadHours,
            eventLeadMinutes = p[K.EVENT_M] ?: d.eventLeadMinutes,
            docWarnDays = p[K.DOC_D] ?: d.docWarnDays,
            reminderHour = p[K.HOUR] ?: d.reminderHour,
            blockScreenshots = p[K.SECURE] ?: d.blockScreenshots,
        )
    }

    private fun write(p: MutablePreferences, s: AppSettings) {
        p[K.NAME] = s.userName
        if (s.photoPath == null) p.remove(K.PHOTO) else p[K.PHOTO] = s.photoPath
        p[K.CURRENCY] = s.defaultCurrency
        p[K.DATE] = s.dateFormat.name
        p[K.THEME] = s.themeMode.name
        p[K.NOTIF] = s.notificationsEnabled
        p[K.FLIGHT_H] = s.flightLeadHours
        p[K.EVENT_M] = s.eventLeadMinutes
        p[K.DOC_D] = s.docWarnDays
        p[K.HOUR] = s.reminderHour
        p[K.SECURE] = s.blockScreenshots
    }

    val settings: Flow<AppSettings> = context.settingsStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { read(it) }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.settingsStore.edit { p -> write(p, transform(read(p))) }
    }
}

package com.travelindex.app.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.travelindex.app.domain.ReminderSpec
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Programa los avisos con AlarmManager (inexacto, sin permisos especiales: puede variar unos minutos).
 * Cada aviso tiene una clave estable → reprogramar lo mismo SUSTITUYE el aviso anterior (nunca duplica),
 * y los avisos de elementos borrados o editados se cancelan.
 */
@Singleton
class ReminderScheduler @Inject constructor(@ApplicationContext private val context: Context) {
    private val alarms = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val prefs = context.getSharedPreferences("reminders", Context.MODE_PRIVATE)

    @Synchronized
    fun sync(specs: List<ReminderSpec>) {
        val previous: Set<String> = HashSet(prefs.getStringSet(KEY_SET, emptySet()).orEmpty())
        val current = specs.map { it.key }.toSet()
        (previous - current).forEach { cancel(it) }
        specs.forEach { schedule(it) }
        prefs.edit().putStringSet(KEY_SET, current).apply()
    }

    private fun intentFor(key: String): Intent =
        Intent(context, ReminderReceiver::class.java)
            .setAction(ACTION)
            .setData(Uri.parse("travelindex://reminder/" + Uri.encode(key)))

    private fun schedule(s: ReminderSpec) {
        val intent = intentFor(s.key).apply {
            putExtra(ReminderReceiver.EXTRA_KEY, s.key)
            putExtra(ReminderReceiver.EXTRA_KIND, s.kind.name)
            putExtra(ReminderReceiver.EXTRA_TRIP, s.tripName)
            putExtra(ReminderReceiver.EXTRA_SUBJECT, s.subject)
            putExtra(ReminderReceiver.EXTRA_EVENT_AT, s.eventAtMs)
            putExtra(ReminderReceiver.EXTRA_ZONE, s.zoneId)
            putExtra(ReminderReceiver.EXTRA_TIME_KNOWN, s.timeKnown)
        }
        val pi = PendingIntent.getBroadcast(
            context, s.key.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, s.triggerAtMs, pi)
    }

    private fun cancel(key: String) {
        val pi = PendingIntent.getBroadcast(
            context, key.hashCode(), intentFor(key), PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
        if (pi != null) { alarms.cancel(pi); pi.cancel() }
    }

    companion object {
        const val ACTION = "com.travelindex.app.REMINDER"
        private const val KEY_SET = "scheduled_keys"
    }
}

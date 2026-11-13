package com.travelindex.app.reminders

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.travelindex.app.MainActivity
import com.travelindex.app.R
import com.travelindex.app.domain.ReminderKind
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

object NotificationChannels {
    const val REMINDERS = "reminders"

    fun create(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(REMINDERS, context.getString(R.string.notif_channel_name), NotificationManager.IMPORTANCE_HIGH).apply {
                description = context.getString(R.string.notif_channel_desc)
            }
        )
    }
}

/** Muestra la notificación cuando salta la alarma. */
class ReminderReceiver : BroadcastReceiver() {

    @SuppressLint("MissingPermission")
    override fun onReceive(context: Context, intent: Intent) {
        val kind = runCatching { ReminderKind.valueOf(intent.getStringExtra(EXTRA_KIND).orEmpty()) }.getOrNull() ?: return
        val key = intent.getStringExtra(EXTRA_KEY) ?: return
        val subject = intent.getStringExtra(EXTRA_SUBJECT).orEmpty()
        val trip = intent.getStringExtra(EXTRA_TRIP).orEmpty()
        val eventAt = intent.getLongExtra(EXTRA_EVENT_AT, 0L)
        val zone = runCatching { ZoneId.of(intent.getStringExtra(EXTRA_ZONE)) }.getOrDefault(ZoneId.systemDefault())
        val timeKnown = intent.getBooleanExtra(EXTRA_TIME_KNOWN, true)

        val whenText = whenText(context, eventAt, zone, timeKnown)
        val (title, text) = when (kind) {
            ReminderKind.FLIGHT -> context.getString(R.string.notif_flight_title, whenText) to joinNonBlank(subject, trip)
            ReminderKind.LODGING_CHECK_IN -> context.getString(R.string.notif_checkin_title) to joinNonBlank(subject, whenText)
            ReminderKind.ACTIVITY -> context.getString(R.string.notif_activity_title, whenText) to joinNonBlank(subject, trip)
            ReminderKind.RESTAURANT -> context.getString(R.string.notif_restaurant_title, whenText) to joinNonBlank(subject, trip)
            ReminderKind.TRANSPORT -> context.getString(R.string.notif_transport_title, whenText) to joinNonBlank(subject, trip)
            ReminderKind.NOTE_DUE -> context.getString(R.string.notif_note_title) to joinNonBlank(subject, trip)
            ReminderKind.DOC_EXPIRY -> context.getString(R.string.notif_doc_title) to
                context.getString(R.string.notif_doc_text, subject.ifBlank { trip }, whenText)
        }

        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, NotificationChannels.REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        val nm = NotificationManagerCompat.from(context)
        if (nm.areNotificationsEnabled()) nm.notify(key.hashCode(), n)
    }

    private fun joinNonBlank(a: String, b: String): String = listOf(a, b).filter { it.isNotBlank() }.joinToString(" · ")

    /** "hoy a las 11:45", "mañana", "el 12 jun 2027 a las 09:00" (en la zona horaria del evento). */
    private fun whenText(context: Context, eventAtMs: Long, zone: ZoneId, timeKnown: Boolean): String {
        val local = Instant.ofEpochMilli(eventAtMs).atZone(zone)
        val today = LocalDate.now(zone)
        val day = when (local.toLocalDate()) {
            today -> context.getString(R.string.when_today)
            today.plusDays(1) -> context.getString(R.string.when_tomorrow)
            else -> DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).format(local)
        }
        return if (timeKnown) context.getString(R.string.when_day_time, day, DateTimeFormatter.ofPattern("HH:mm").format(local)) else day
    }

    companion object {
        const val EXTRA_KEY = "key"
        const val EXTRA_KIND = "kind"
        const val EXTRA_TRIP = "trip"
        const val EXTRA_SUBJECT = "subject"
        const val EXTRA_EVENT_AT = "eventAt"
        const val EXTRA_ZONE = "zone"
        const val EXTRA_TIME_KNOWN = "timeKnown"
    }
}

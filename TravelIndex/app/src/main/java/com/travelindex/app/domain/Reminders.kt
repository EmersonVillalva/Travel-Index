package com.travelindex.app.domain

import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

enum class ReminderKind { FLIGHT, LODGING_CHECK_IN, ACTIVITY, RESTAURANT, TRANSPORT, NOTE_DUE, DOC_EXPIRY }

/**
 * Un aviso programable. Es puro (sin Android): el texto final se compone al dispararse, con recursos traducibles.
 * [key] es estable por elemento → reprogramar el mismo elemento sustituye el aviso anterior (sin duplicados).
 */
data class ReminderSpec(
    val key: String,
    val triggerAtMs: Long,
    val kind: ReminderKind,
    val tripId: Long,
    val tripName: String,
    val subject: String,
    val eventAtMs: Long,
    val zoneId: String,
    /** false → el evento no tiene hora concreta (no se muestra "a las 00:00"). */
    val timeKnown: Boolean = true,
)

object ReminderPlanner {
    const val MAX_DAYS_AHEAD = 120L
    /** Android limita a 500 alarmas simultáneas por app; dejamos margen. */
    const val MAX_ALARMS = 400
    private val DOC_HOUR = LocalTime.of(10, 0)

    fun plan(
        bundles: List<TripBundle>,
        settings: AppSettings,
        nowMs: Long,
        deviceZone: ZoneId = ZoneId.systemDefault(),
    ): List<ReminderSpec> {
        if (!settings.notificationsEnabled) return emptyList()
        val horizon = nowMs + Duration.ofDays(MAX_DAYS_AHEAD).toMillis()
        val hour = LocalTime.of(settings.reminderHour.coerceIn(0, 23), 0)
        val out = mutableListOf<ReminderSpec>()

        fun add(key: String, trigger: ZonedDateTime, kind: ReminderKind, b: TripBundle, subject: String, eventAt: ZonedDateTime, timeKnown: Boolean = true) {
            val t = trigger.toInstant().toEpochMilli()
            if (t > nowMs && t <= horizon) {
                out += ReminderSpec(key, t, kind, b.trip.id, b.trip.name, subject, eventAt.toInstant().toEpochMilli(), eventAt.zone.id, timeKnown)
            }
        }

        bundles.filter { !it.trip.cancelled }.forEach { b ->
            b.events.forEach { e ->
                val dayAtHour = e.start.toLocalDate().atTime(hour).atZone(e.start.zone)
                val before = e.start.minusMinutes(settings.eventLeadMinutes.toLong())
                when (e.type) {
                    EventType.FLIGHT -> {
                        // Dos avisos fijos por vuelo: con antelación (ajustable, 24 h) y el de última hora,
                        // 1 hora antes de salir. Un vuelo guardado con poco margen recibe los que queden en el
                        // futuro; como los dos instantes son fijos, nunca se repiten ni se reprograman.
                        add(e.key, e.start.minusHours(settings.flightLeadHours.toLong()), ReminderKind.FLIGHT, b, e.title, e.start)
                        if (settings.flightLeadHours > 1) {
                            add(e.key + ":SOON", e.start.minusHours(1), ReminderKind.FLIGHT, b, e.title, e.start)
                        }
                    }
                    EventType.LODGING_CHECK_IN -> {
                        val t = if (e.timeKnown && before.isBefore(dayAtHour)) before else dayAtHour
                        add(e.key, t, ReminderKind.LODGING_CHECK_IN, b, e.title, e.start, e.timeKnown)
                    }
                    EventType.ACTIVITY ->
                        add(e.key, if (e.timeKnown) before else dayAtHour, ReminderKind.ACTIVITY, b, e.title, e.start, e.timeKnown)
                    EventType.RESTAURANT ->
                        add(e.key, if (e.timeKnown) before else dayAtHour, ReminderKind.RESTAURANT, b, e.title, e.start, e.timeKnown)
                    EventType.TRANSPORT_START ->
                        add(e.key, if (e.timeKnown) before else dayAtHour, ReminderKind.TRANSPORT, b, e.title, e.start, e.timeKnown)
                    EventType.NOTE_DUE ->
                        add(e.key, if (e.timeKnown) e.start else dayAtHour, ReminderKind.NOTE_DUE, b, e.title, e.start, e.timeKnown)
                    EventType.LODGING_CHECK_OUT, EventType.TRANSPORT_END -> Unit
                }
            }
            b.docs.forEach { d ->
                val expiry: LocalDate = d.expiryDate ?: return@forEach
                val trigger = expiry.minusDays(settings.docWarnDays.toLong()).atTime(DOC_HOUR).atZone(deviceZone)
                val subject = d.title.ifBlank { d.docType }
                add("DOC:${d.id}", trigger, ReminderKind.DOC_EXPIRY, b, subject, expiry.atTime(DOC_HOUR).atZone(deviceZone), timeKnown = false)
            }
        }
        return out.sortedBy { it.triggerAtMs }.take(MAX_ALARMS)
    }
}

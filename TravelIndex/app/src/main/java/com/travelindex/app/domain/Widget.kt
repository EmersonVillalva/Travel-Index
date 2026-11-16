package com.travelindex.app.domain

import java.time.ZoneId

/** Lo que enseña el widget de pantalla de inicio: el próximo evento o, si no hay, el próximo viaje. */
data class WidgetEvent(
    val tripName: String,
    val title: String,
    val startMs: Long,
    val zoneId: String,
    val timeKnown: Boolean,
    val isTripStart: Boolean = false,
)

object WidgetPlanner {
    /** El siguiente evento de cualquier viaje no cancelado; sin eventos futuros, el arranque del próximo viaje. */
    fun next(bundles: List<TripBundle>, nowMs: Long, zone: ZoneId): WidgetEvent? {
        val active = bundles.filter { !it.trip.cancelled }
        val ev = active
            .flatMap { b -> b.events.map { b.trip.name to it } }
            .filter { (_, e) -> e.start.toInstant().toEpochMilli() >= nowMs }
            .minByOrNull { (_, e) -> e.start.toInstant().toEpochMilli() }
        if (ev != null) {
            val (tripName, e) = ev
            return WidgetEvent(tripName, e.title, e.start.toInstant().toEpochMilli(), e.start.zone.id, e.timeKnown)
        }
        val trip = active
            .map { it.trip to it.trip.startDate.atStartOfDay(zone) }
            .filter { (_, start) -> start.toInstant().toEpochMilli() >= nowMs }
            .minByOrNull { (_, start) -> start.toInstant().toEpochMilli() }
            ?: return null
        return WidgetEvent(trip.first.name, "", trip.second.toInstant().toEpochMilli(), zone.id, timeKnown = false, isTripStart = true)
    }
}

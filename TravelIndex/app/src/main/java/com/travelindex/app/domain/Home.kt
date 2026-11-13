package com.travelindex.app.domain

import com.travelindex.app.data.db.TripEntity
import java.time.LocalDate
import java.time.ZonedDateTime

enum class AttentionKind { FLIGHT_PENDING, DOCUMENT }

/** Algo que requiere atención del usuario: un vuelo sin confirmar o un documento pendiente / a punto de caducar. */
data class AttentionItem(
    val tripId: Long,
    val tripName: String,
    val kind: AttentionKind,
    val module: ModuleType,
    val itemId: Long,
    val title: String,
    val warning: DocWarning?,
)

data class HomeStats(val trips: Int, val countries: Int, val reservations: Int)

data class HomeSummary(
    /** Viaje destacado: el que está en curso o, si no hay, el próximo. */
    val next: TripBundle?,
    val upcomingTrips: List<TripEntity>,
    val pastTrips: List<TripEntity>,
    val nextEvents: List<DayEntry>,
    val attention: List<AttentionItem>,
    val stats: HomeStats,
)

object HomeBuilder {
    fun build(
        bundles: List<TripBundle>,
        now: ZonedDateTime,
        today: LocalDate,
        eventLimit: Int = 5,
        attentionLimit: Int = 6,
    ): HomeSummary {
        fun group(b: TripBundle) = TripListBuilder.groupOf(b.trip, today)
        val ongoing = bundles.filter { group(it) == TripGroup.ONGOING }.sortedBy { it.trip.startDate }
        val upcoming = bundles.filter { group(it) == TripGroup.UPCOMING }.sortedWith(compareBy({ it.trip.startDate }, { it.trip.id }))
        val next = ongoing.firstOrNull() ?: upcoming.firstOrNull()
        val active = ongoing + upcoming

        val events = active.flatMap { b -> b.events.filter { !it.start.isBefore(now) }.map { DayEntry(b.trip, it) } }
            .sortedWith(compareBy({ it.event.start.toInstant() }, { it.event.key }))
            .take(eventLimit)

        val attention = active.flatMap { b ->
            val docs = b.docs.mapNotNull { d ->
                val w = DocWarnings.evaluate(d, b.trip.endDate, today)
                if (w == DocWarning.NONE) null
                else AttentionItem(
                    b.trip.id, b.trip.name, AttentionKind.DOCUMENT, ModuleType.TRAVEL_DOC, d.id,
                    d.title.ifBlank { d.docType }, w,
                )
            }
            val flights = b.flights.filter { it.status == "PENDING" }.map { f ->
                AttentionItem(
                    b.trip.id, b.trip.name, AttentionKind.FLIGHT_PENDING, ModuleType.FLIGHT, f.id,
                    "${f.airline} ${f.flightNumber}".trim(), null,
                )
            }
            docs + flights
        }.sortedBy { severity(it) }.take(attentionLimit)

        val notCancelled = bundles.filter { !it.trip.cancelled }
        val stats = HomeStats(
            trips = notCancelled.size,
            countries = notCancelled.map { TripListBuilder.normalize(it.trip.country) }.filter { it.isNotEmpty() }.toSet().size,
            reservations = notCancelled.sumOf { it.reservationsCount },
        )

        return HomeSummary(
            next = next,
            upcomingTrips = active.map { it.trip }.filter { it.id != next?.trip?.id },
            pastTrips = bundles.filter { group(it) == TripGroup.PAST }.map { it.trip }
                .sortedWith(compareByDescending<TripEntity> { it.endDate }.thenBy { it.id }),
            nextEvents = events, attention = attention, stats = stats,
        )
    }

    /** Menor = más urgente. */
    private fun severity(a: AttentionItem): Int = when (a.warning) {
        DocWarning.EXPIRED -> 0
        DocWarning.REJECTED -> 1
        DocWarning.EXPIRES_BEFORE_TRIP_END -> 2
        DocWarning.EXPIRES_SOON -> 3
        DocWarning.PENDING -> 4
        else -> 5
    }
}

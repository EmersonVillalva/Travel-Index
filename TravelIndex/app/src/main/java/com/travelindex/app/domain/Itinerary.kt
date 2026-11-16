package com.travelindex.app.domain

import com.travelindex.app.data.db.ActivityEntity
import com.travelindex.app.data.db.FlightEntity
import com.travelindex.app.data.db.LodgingEntity
import com.travelindex.app.data.db.NoteItemEntity
import com.travelindex.app.data.db.RestaurantEntity
import com.travelindex.app.data.db.TransportEntity
import com.travelindex.app.domain.forms.ActivityCodec
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

enum class EventType {
    FLIGHT, LODGING_CHECK_IN, LODGING_CHECK_OUT, ACTIVITY, TRANSPORT_START, TRANSPORT_END, RESTAURANT, NOTE_DUE
}

/** Módulo al que pertenece el evento (para abrir su detalle). */
fun EventType.moduleType(): ModuleType? = when (this) {
    EventType.FLIGHT -> ModuleType.FLIGHT
    EventType.LODGING_CHECK_IN, EventType.LODGING_CHECK_OUT -> ModuleType.LODGING
    EventType.ACTIVITY -> ModuleType.ACTIVITY
    EventType.TRANSPORT_START, EventType.TRANSPORT_END -> ModuleType.TRANSPORT
    EventType.RESTAURANT -> ModuleType.RESTAURANT
    EventType.NOTE_DUE -> null
}

/**
 * Evento derivado. No se guarda en base de datos: se calcula siempre a partir de las reservas,
 * por lo que editar o borrar una reserva nunca deja eventos duplicados ni huérfanos.
 * [key] es estable (tipo + id de origen) y sirve también para identificar recordatorios.
 */
data class ItineraryEvent(
    val key: String,
    val type: EventType,
    val sourceId: Long,
    val tripId: Long,
    val title: String,
    val subtitle: String?,
    val start: ZonedDateTime,
    val end: ZonedDateTime?,
    val timeKnown: Boolean,
    /** Clave del subtipo (p. ej. tipo de transporte) para que la interfaz ponga la etiqueta traducida. */
    val kind: String? = null,
)

data class ItineraryDay(val date: LocalDate, val events: List<ItineraryEvent>)

object ItineraryBuilder {
    val DEFAULT_CHECK_IN: LocalTime = LocalTime.of(15, 0)
    val DEFAULT_CHECK_OUT: LocalTime = LocalTime.of(11, 0)

    fun events(
        flights: List<FlightEntity>,
        lodgings: List<LodgingEntity>,
        activities: List<ActivityEntity>,
        transports: List<TransportEntity> = emptyList(),
        restaurants: List<RestaurantEntity> = emptyList(),
        notes: List<NoteItemEntity> = emptyList(),
    ): List<ItineraryEvent> {
        val out = mutableListOf<ItineraryEvent>()

        flights.forEach { f ->
            val dep = Instant.ofEpochMilli(f.departureEpochMs).atZone(ZoneId.of(f.departureZoneId))
            val arr = Instant.ofEpochMilli(f.arrivalEpochMs).atZone(ZoneId.of(f.arrivalZoneId))
            out += ItineraryEvent(
                key = "FLIGHT:${f.id}", type = EventType.FLIGHT, sourceId = f.id, tripId = f.tripId,
                title = "${f.airline} ${f.flightNumber}".trim(),
                subtitle = "${Airports.displayName(f.departureAirport)} → ${Airports.displayName(f.arrivalAirport)}",
                start = dep, end = arr, timeKnown = true,
            )
        }

        lodgings.forEach { l ->
            val zone = ZoneId.of(l.timeZoneId)
            val inTime = l.checkInTime
            val outTime = l.checkOutTime
            out += ItineraryEvent(
                key = "LODGING_IN:${l.id}", type = EventType.LODGING_CHECK_IN, sourceId = l.id, tripId = l.tripId,
                title = l.name, subtitle = l.address.ifBlank { null },
                start = l.checkInDate.atTime(inTime ?: DEFAULT_CHECK_IN).atZone(zone),
                end = null, timeKnown = inTime != null,
            )
            out += ItineraryEvent(
                key = "LODGING_OUT:${l.id}", type = EventType.LODGING_CHECK_OUT, sourceId = l.id, tripId = l.tripId,
                title = l.name, subtitle = l.address.ifBlank { null },
                start = l.checkOutDate.atTime(outTime ?: DEFAULT_CHECK_OUT).atZone(zone),
                end = null, timeKnown = outTime != null,
            )
        }

        activities.forEach { a ->
            val zone = ZoneId.of(a.timeZoneId)
            val st = a.startTime
            out += ItineraryEvent(
                key = "ACTIVITY:${a.id}", type = EventType.ACTIVITY, sourceId = a.id, tripId = a.tripId,
                title = a.name, subtitle = a.location.ifBlank { null },
                start = a.date.atTime(st ?: LocalTime.MIN).atZone(zone),
                end = ActivityCodec.end(a)?.atZone(zone),
                timeKnown = st != null,
            )
        }

        transports.forEach { t ->
            val zone = ZoneId.of(t.timeZoneId)
            val st = t.startTime
            val route = listOf(t.originPlace, t.destinationPlace).filter { it.isNotBlank() }.joinToString(" → ")
            val title = t.company.ifBlank { t.model }
            out += ItineraryEvent(
                key = "TRANSPORT_START:${t.id}", type = EventType.TRANSPORT_START, sourceId = t.id, tripId = t.tripId,
                title = title, subtitle = route.ifBlank { null },
                start = t.startDate.atTime(st ?: LocalTime.MIN).atZone(zone),
                end = null, timeKnown = st != null, kind = t.type,
            )
            t.endDate?.let { ed ->
                val et = t.endTime
                out += ItineraryEvent(
                    key = "TRANSPORT_END:${t.id}", type = EventType.TRANSPORT_END, sourceId = t.id, tripId = t.tripId,
                    title = title, subtitle = t.destinationPlace.ifBlank { null },
                    start = ed.atTime(et ?: LocalTime.MIN).atZone(zone),
                    end = null, timeKnown = et != null, kind = t.type,
                )
            }
        }

        restaurants.forEach { r ->
            val zone = ZoneId.of(r.timeZoneId)
            val t = r.time
            out += ItineraryEvent(
                key = "RESTAURANT:${r.id}", type = EventType.RESTAURANT, sourceId = r.id, tripId = r.tripId,
                title = r.name, subtitle = r.address.ifBlank { null },
                start = r.date.atTime(t ?: LocalTime.MIN).atZone(zone),
                end = null, timeKnown = t != null,
            )
        }

        val deviceZone = ZoneId.systemDefault()
        notes.forEach { n ->
            val due = n.dueDate ?: return@forEach
            if (n.done) return@forEach
            val t = n.dueTime
            out += ItineraryEvent(
                key = "NOTE:${n.id}", type = EventType.NOTE_DUE, sourceId = n.id, tripId = n.tripId,
                title = n.text, subtitle = n.body.ifBlank { null },
                start = due.atTime(t ?: LocalTime.MIN).atZone(deviceZone),
                end = null, timeKnown = t != null,
            )
        }
        return out
    }

    /** Agrupa por día LOCAL del evento y ordena por instante absoluto (correcto entre zonas horarias). */
    fun days(
        flights: List<FlightEntity>,
        lodgings: List<LodgingEntity>,
        activities: List<ActivityEntity>,
        transports: List<TransportEntity> = emptyList(),
        restaurants: List<RestaurantEntity> = emptyList(),
        notes: List<NoteItemEntity> = emptyList(),
    ): List<ItineraryDay> = group(events(flights, lodgings, activities, transports, restaurants, notes))

    fun group(events: List<ItineraryEvent>): List<ItineraryDay> =
        events
            .sortedWith(compareBy({ it.start.toInstant() }, { it.key }))
            .groupBy { it.start.toLocalDate() }
            .map { (date, list) -> ItineraryDay(date, list) }
            .sortedBy { it.date }

    /**
     * Todos los días del viaje (también los que no tienen planes) más cualquier día con eventos
     * fuera de las fechas del viaje, en orden cronológico.
     */
    fun fullDays(start: LocalDate, end: LocalDate, events: List<ItineraryEvent>): List<ItineraryDay> {
        val grouped = group(events).associate { it.date to it.events }
        val dates = java.util.TreeSet<LocalDate>()
        var d = start
        while (!d.isAfter(end)) { dates.add(d); d = d.plusDays(1) }
        dates.addAll(grouped.keys)
        return dates.map { ItineraryDay(it, grouped[it].orEmpty()) }
    }
}

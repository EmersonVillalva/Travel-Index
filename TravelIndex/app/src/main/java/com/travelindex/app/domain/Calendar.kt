package com.travelindex.app.domain

import com.travelindex.app.data.db.TripEntity
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

data class CalendarCell(
    val date: LocalDate,
    val inMonth: Boolean,
    val isToday: Boolean,
    /** Viajes (no cancelados) cuyas fechas incluyen este día. */
    val tripIds: Set<Long>,
    val eventTypes: Set<EventType>,
)

data class DayEntry(val trip: TripEntity, val event: ItineraryEvent)

object CalendarBuilder {
    /** Cuadrícula de 6 semanas × 7 días (altura estable entre meses). */
    fun month(
        month: YearMonth,
        today: LocalDate,
        bundles: List<TripBundle>,
        firstDay: DayOfWeek = DayOfWeek.MONDAY,
    ): List<List<CalendarCell>> {
        val first = month.atDay(1)
        val shift = (first.dayOfWeek.value - firstDay.value + 7) % 7
        val gridStart = first.minusDays(shift.toLong())
        val active = bundles.filter { !it.trip.cancelled }
        val eventsByDate: Map<LocalDate, Set<EventType>> = active
            .flatMap { it.events }
            .groupBy { it.start.toLocalDate() }
            .mapValues { (_, list) -> list.map { it.type }.toSet() }

        return (0 until 6).map { week ->
            (0 until 7).map { dow ->
                val d = gridStart.plusDays((week * 7 + dow).toLong())
                CalendarCell(
                    date = d,
                    inMonth = d.month == month.month && d.year == month.year,
                    isToday = d == today,
                    tripIds = active.filter { !d.isBefore(it.trip.startDate) && !d.isAfter(it.trip.endDate) }
                        .map { it.trip.id }.toSet(),
                    eventTypes = eventsByDate[d].orEmpty(),
                )
            }
        }
    }

    /** Eventos de un día (todos los viajes no cancelados), ordenados por instante. */
    fun eventsOn(date: LocalDate, bundles: List<TripBundle>): List<DayEntry> =
        bundles.filter { !it.trip.cancelled }
            .flatMap { b -> b.events.filter { it.start.toLocalDate() == date }.map { DayEntry(b.trip, it) } }
            .sortedWith(compareBy(ItineraryBuilder.ORDER) { it.event })

    /** Viajes cuyas fechas incluyen el día (incluye los cancelados para poder mostrarlos tachados si se quiere). */
    fun tripsOn(date: LocalDate, bundles: List<TripBundle>): List<TripEntity> =
        bundles.map { it.trip }.filter { !date.isBefore(it.startDate) && !date.isAfter(it.endDate) }
}

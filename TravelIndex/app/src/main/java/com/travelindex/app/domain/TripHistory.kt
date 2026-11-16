package com.travelindex.app.domain

import java.time.LocalDate

/** Cifras de los viajes que ya terminaron. */
data class HistoryStats(
    val trips: Int,
    /** Días de viaje sumados (la salida y el regreso cuentan; el mismo día = 1). */
    val days: Int,
    val countries: Int,
    val cities: Int,
    /** Lo pagado en total en esos viajes, por moneda (nunca se mezclan monedas). */
    val paid: Map<String, Long>,
)

/** Los viajes terminados de un año (el año en que acabaron), el más reciente primero. */
data class HistoryYear(val year: Int, val trips: List<TripBundle>)

data class TripHistory(val stats: HistoryStats, val years: List<HistoryYear>) {
    val isEmpty: Boolean get() = stats.trips == 0
}

/**
 * Historial de viajes anteriores: solo los viajes terminados (los cancelados no cuentan como viajados). Se agrupan por el
 * año en que acabaron, de más reciente a más antiguo, y se resumen en cifras: viajes, días, países y ciudades distintos y
 * lo pagado.
 */
object TripHistoryBuilder {
    fun build(bundles: List<TripBundle>, today: LocalDate): TripHistory {
        val done = bundles.filter { it.status(today) == TripStatus.COMPLETED }
        val years = done.groupBy { it.trip.endDate.year }
            .toSortedMap(compareByDescending { it })
            .map { (year, list) ->
                HistoryYear(
                    year,
                    list.sortedWith(
                        compareByDescending<TripBundle> { it.trip.endDate }
                            .thenByDescending { it.trip.startDate }
                            .thenBy { it.trip.id },
                    ),
                )
            }
        val paid = done.flatMap { CostBreakdown.totals(CostBreakdown.of(it)).entries }
            .groupBy({ it.key }, { it.value })
            .mapValues { (_, l) -> l.sum() }
            .toSortedMap()
        return TripHistory(
            HistoryStats(
                trips = done.size,
                days = done.sumOf { tripDurationDays(it.trip.startDate, it.trip.endDate) },
                countries = distinctPlaces(done.flatMap { b -> listOf(b.trip.country) + b.destinations.map { it.country } }),
                cities = distinctPlaces(done.flatMap { b -> listOf(b.trip.city) + b.destinations.map { it.city } }),
                paid = paid,
            ),
            years,
        )
    }

    /** Cuántos lugares distintos hay, sin repetir por mayúsculas, acentos o espacios ("Perú" = "peru ") y sin contar vacíos. */
    fun distinctPlaces(names: List<String>): Int =
        names.map { TripListBuilder.normalize(it) }.filter { it.isNotEmpty() }.toSet().size

    /** Lo pagado en un viaje, por moneda: el mismo importe que sale en su tarjeta. */
    fun paidOf(b: TripBundle): Map<String, Long> = CostBreakdown.totals(CostBreakdown.of(b))
}

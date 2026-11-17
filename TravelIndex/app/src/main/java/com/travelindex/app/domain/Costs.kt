package com.travelindex.app.domain

enum class CostCategory { FLIGHTS, LODGING, ACTIVITIES, TRANSPORT, RESTAURANTS, OTHER }

/** Lo gastado en una categoría, por moneda. NUNCA se suman monedas distintas entre sí. */
data class CostLine(val category: CostCategory, val byCurrency: Map<String, Long>)

/** Suma los importes de cada moneda por separado (un elemento sin precio no cuenta). */
private fun sumByCurrency(items: List<Pair<Long?, String>>): Map<String, Long> =
    items.mapNotNull { (m, c) -> m?.let { c to it } }
        .groupBy({ it.first }, { it.second })
        .mapValues { (_, l) -> l.sum() }
        .toSortedMap()

object CostBreakdown {
    /** Categorías con algún precio, en orden fijo. Los importes de cada moneda se suman por separado. */
    fun of(b: TripBundle): List<CostLine> {
        val sum = ::sumByCurrency
        val lines = listOf(
            CostCategory.FLIGHTS to sum(b.flights.map { it.priceMinor to it.currency }),
            CostCategory.LODGING to sum(b.lodgings.map { it.priceMinor to it.currency }),
            CostCategory.ACTIVITIES to sum(b.activities.map { it.priceMinor to it.currency }),
            CostCategory.TRANSPORT to sum(b.transports.map { it.priceMinor to it.currency }),
            CostCategory.RESTAURANTS to sum(b.restaurants.map { it.priceMinor to it.currency }),
            // Gastos sueltos de versiones anteriores (ya no se pueden crear, pero pueden existir en una copia restaurada).
            CostCategory.OTHER to sum(b.expenses.map { it.amountMinor to it.currency }),
        )
        return lines.filter { it.second.isNotEmpty() }.map { CostLine(it.first, it.second) }
    }

    /** Total por moneda de todas las categorías. */
    fun totals(lines: List<CostLine>): Map<String, Long> =
        lines.flatMap { it.byCurrency.entries }
            .groupBy({ it.key }, { it.value })
            .mapValues { (_, l) -> l.sum() }
            .toSortedMap()
}

/**
 * Lo pagado en cada apartado del viaje (los que salen como tarjetas en el panel). Dos o más elementos con precio se
 * suman; los que no tienen precio no cuentan; sin ningún precio el resultado está vacío (no se muestra nada).
 * Las monedas distintas nunca se suman entre sí.
 */
object SectionCosts {
    /** Vuelos, alojamiento, actividades y restaurantes. Los demás módulos no tienen precio y devuelven vacío. */
    fun of(b: TripBundle, type: ModuleType): Map<String, Long> = when (type) {
        ModuleType.FLIGHT -> sumByCurrency(b.flights.map { it.priceMinor to it.currency })
        ModuleType.LODGING -> sumByCurrency(b.lodgings.map { it.priceMinor to it.currency })
        ModuleType.ACTIVITY -> sumByCurrency(b.activities.map { it.priceMinor to it.currency })
        ModuleType.TRANSPORT -> sumByCurrency(b.transports.map { it.priceMinor to it.currency })
        ModuleType.RESTAURANT -> sumByCurrency(b.restaurants.map { it.priceMinor to it.currency })
        else -> emptyMap()
    }

    /** Un apartado de transporte: los vuelos, o los transportes de ese medio (tren, autobús, barco, coche, otros). */
    fun of(b: TripBundle, section: TransportSection): Map<String, Long> =
        if (section == TransportSection.FLIGHTS) of(b, ModuleType.FLIGHT)
        else sumByCurrency(b.transports.filter { section.matches(it.type) }.map { it.priceMinor to it.currency })

    /** "1.234,50 €" o, con varias monedas, "120,00 € · $50.00". Vacío si no hay importes. */
    fun format(byCurrency: Map<String, Long>): String =
        byCurrency.entries.joinToString(" · ") { (cur, minor) -> Money.format(minor, cur) }
}

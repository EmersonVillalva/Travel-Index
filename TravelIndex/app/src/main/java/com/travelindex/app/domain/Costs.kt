package com.travelindex.app.domain

enum class CostCategory { FLIGHTS, LODGING, ACTIVITIES, TRANSPORT, RESTAURANTS, OTHER }

/** Lo gastado en una categoría, por moneda. NUNCA se suman monedas distintas entre sí. */
data class CostLine(val category: CostCategory, val byCurrency: Map<String, Long>)

object CostBreakdown {
    /** Categorías con algún precio, en orden fijo. Los importes de cada moneda se suman por separado. */
    fun of(b: TripBundle): List<CostLine> {
        fun sum(items: List<Pair<Long?, String>>): Map<String, Long> =
            items.mapNotNull { (m, c) -> m?.let { c to it } }
                .groupBy({ it.first }, { it.second })
                .mapValues { (_, l) -> l.sum() }
                .toSortedMap()

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

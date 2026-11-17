package com.travelindex.app.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/**
 * Lo marcado en el calendario de fechas del viaje: ida y vuelta en una sola ventana.
 * Primer toque = ida; segundo toque (el mismo día o uno posterior) = vuelta; un toque anterior a la ida
 * vuelve a empezar desde ahí; con el rango completo, el siguiente toque empieza otro.
 * Un solo día marcado sirve como viaje de un día (la vuelta es la propia ida).
 */
data class RangeSelection(val start: LocalDate? = null, val end: LocalDate? = null) {
    fun tap(day: LocalDate): RangeSelection = when {
        start == null || end != null -> RangeSelection(day, null)
        day.isBefore(start) -> RangeSelection(day, null)
        else -> RangeSelection(start, day)
    }

    /** Ida y vuelta para guardar; null si aún no hay ni ida. */
    fun result(): Pair<LocalDate, LocalDate>? = start?.let { it to (end ?: it) }

    fun isSelected(day: LocalDate): Boolean = day == start || day == end

    /** ¿Hay un tramo de varios días marcado (ida y vuelta distintas)? */
    private val spansDays: Boolean get() = start != null && end != null && start != end

    private fun isBetween(day: LocalDate): Boolean =
        spansDays && day.isAfter(start) && day.isBefore(end)

    /** Franja de color a la derecha del día (desde la ida y en los días intermedios). */
    fun bandRight(day: LocalDate): Boolean = spansDays && (day == start || isBetween(day))

    /** Franja de color a la izquierda del día (hasta la vuelta y en los días intermedios). */
    fun bandLeft(day: LocalDate): Boolean = spansDays && (day == end || isBetween(day))
}

/** Cuadrícula de un mes para dibujar el calendario. */
object MonthGrid {
    /** Días de la semana empezando por [firstDay]. */
    fun weekdays(firstDay: DayOfWeek): List<DayOfWeek> = (0L..6L).map { firstDay.plus(it) }

    /**
     * Semanas del mes, de 7 huecos cada una (null = hueco antes del día 1 o después del último).
     * [minWeeks] rellena con semanas vacías para que el calendario no cambie de alto al pasar de mes.
     */
    fun weeks(month: YearMonth, firstDay: DayOfWeek, minWeeks: Int = 0): List<List<LocalDate?>> {
        val lead = (month.atDay(1).dayOfWeek.value - firstDay.value + 7) % 7
        val cells = ArrayList<LocalDate?>()
        repeat(lead) { cells += null }
        for (d in 1..month.lengthOfMonth()) cells += month.atDay(d)
        while (cells.size % 7 != 0) cells += null
        while (cells.size < minWeeks * 7) cells += null
        return cells.chunked(7)
    }

    /** Años que se ofrecen al saltar de año: desde el pasado hasta diez años adelante, y siempre el que se está viendo. */
    fun years(today: LocalDate, shown: Int): IntRange =
        minOf(today.year - 1, shown)..maxOf(today.year + 10, shown)
}

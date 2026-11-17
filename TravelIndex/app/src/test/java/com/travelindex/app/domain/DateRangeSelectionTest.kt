package com.travelindex.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

class DateRangeSelectionTest {
    private fun d(day: Int, month: Int = 2) = LocalDate.of(2027, month, day)

    @Test fun `primer toque es la ida y el segundo la vuelta`() {
        val a = RangeSelection().tap(d(10))
        assertEquals(d(10), a.start); assertNull(a.end)
        val b = a.tap(d(14))
        assertEquals(d(10), b.start); assertEquals(d(14), b.end)
        assertEquals(d(10) to d(14), b.result())
    }

    @Test fun `un toque anterior a la ida empieza de nuevo desde ahi`() {
        val b = RangeSelection().tap(d(10)).tap(d(5))
        assertEquals(d(5), b.start); assertNull(b.end)
    }

    @Test fun `con el rango completo el siguiente toque empieza otro`() {
        val b = RangeSelection().tap(d(10)).tap(d(14)).tap(d(20))
        assertEquals(d(20), b.start); assertNull(b.end)
    }

    @Test fun `tocar dos veces el mismo dia es un viaje de un dia`() {
        val b = RangeSelection().tap(d(10)).tap(d(10))
        assertEquals(d(10) to d(10), b.result())
        assertFalse(b.bandLeft(d(10))); assertFalse(b.bandRight(d(10)))   // sin franja: es un solo día
    }

    @Test fun `solo la ida marcada tambien vale como viaje de un dia`() {
        val a = RangeSelection().tap(d(10))
        assertEquals(d(10) to d(10), a.result())
        assertNull(RangeSelection().result())
    }

    @Test fun `la franja cubre de la ida a la vuelta`() {
        val s = RangeSelection(d(10), d(13))
        assertTrue(s.isSelected(d(10)) && s.isSelected(d(13)))
        assertFalse(s.isSelected(d(11)))
        assertTrue(s.bandRight(d(10))); assertFalse(s.bandLeft(d(10)))   // la ida solo pinta a su derecha
        assertTrue(s.bandLeft(d(11)) && s.bandRight(d(11)))              // un día intermedio, a ambos lados
        assertTrue(s.bandLeft(d(13))); assertFalse(s.bandRight(d(13)))   // la vuelta solo a su izquierda
        assertFalse(s.bandLeft(d(9)) || s.bandRight(d(14)))
    }

    @Test fun `desde el campo Vuelta solo cambia la vuelta`() {
        val s = RangeSelection(d(10), d(14))
        assertEquals(RangeSelection(d(10), d(20)), s.tap(d(20), RangeEdge.END))
        assertEquals(RangeSelection(d(10), d(10)), s.tap(d(10), RangeEdge.END))   // la vuelta puede ser la propia ida
        assertEquals(RangeSelection(d(5), null), s.tap(d(5), RangeEdge.END))       // antes de la ida: empieza de nuevo
    }

    @Test fun `desde el campo Ida solo cambia la ida`() {
        val s = RangeSelection(d(10), d(14))
        assertEquals(RangeSelection(d(3), d(14)), s.tap(d(3), RangeEdge.START))
        assertEquals(RangeSelection(d(14), d(14)), s.tap(d(14), RangeEdge.START))
        assertEquals(RangeSelection(d(20), null), s.tap(d(20), RangeEdge.START))   // después de la vuelta: empieza de nuevo
    }

    @Test fun `sin rango previo el campo no cambia el comportamiento`() {
        assertEquals(RangeSelection().tap(d(10)), RangeSelection().tap(d(10), RangeEdge.END))
        assertEquals(RangeSelection().tap(d(10)), RangeSelection().tap(d(10), RangeEdge.START))
        val onlyStart = RangeSelection(d(10), null)
        assertEquals(RangeSelection(d(10), d(14)), onlyStart.tap(d(14), RangeEdge.END))
    }

    @Test fun `tras cambiar un extremo el siguiente toque sigue cambiando ese extremo`() {
        val s = RangeSelection(d(10), d(14)).tap(d(20), RangeEdge.END).tap(d(17), RangeEdge.END)
        assertEquals(RangeSelection(d(10), d(17)), s)
    }

    @Test fun `el rango puede cruzar de mes`() {
        val s = RangeSelection(d(28), d(2, 3))
        assertTrue(s.bandLeft(d(1, 3)) && s.bandRight(d(1, 3)))
    }
}

class MonthGridTest {
    @Test fun `febrero de 2027 empieza en lunes y tiene 28 dias`() {
        val w = MonthGrid.weeks(YearMonth.of(2027, 2), DayOfWeek.MONDAY)
        assertEquals(4, w.size)
        assertEquals(LocalDate.of(2027, 2, 1), w[0][0])
        assertEquals(LocalDate.of(2027, 2, 28), w[3][6])
        assertEquals(28, w.flatten().mapNotNull { it }.size)
    }

    @Test fun `el hueco inicial depende del primer dia de la semana`() {
        // 1 de febrero de 2027 es lunes.
        val mon = MonthGrid.weeks(YearMonth.of(2027, 2), DayOfWeek.MONDAY)[0]
        assertEquals(0, mon.takeWhile { it == null }.size)
        val sun = MonthGrid.weeks(YearMonth.of(2027, 2), DayOfWeek.SUNDAY)[0]
        assertEquals(1, sun.takeWhile { it == null }.size)
        val sat = MonthGrid.weeks(YearMonth.of(2027, 2), DayOfWeek.SATURDAY)[0]
        assertEquals(2, sat.takeWhile { it == null }.size)
    }

    @Test fun `las semanas son siempre de siete huecos y se puede fijar el alto`() {
        for (m in 1..12) {
            val w = MonthGrid.weeks(YearMonth.of(2027, m), DayOfWeek.MONDAY, minWeeks = 6)
            assertTrue(w.all { it.size == 7 })
            assertEquals(6, w.size)
            assertEquals(YearMonth.of(2027, m).lengthOfMonth(), w.flatten().mapNotNull { it }.size)
        }
    }

    @Test fun `un mes de seis semanas no se recorta`() {
        // Mayo de 2027 empieza en sábado: con semana en lunes ocupa seis filas.
        assertEquals(6, MonthGrid.weeks(YearMonth.of(2027, 5), DayOfWeek.MONDAY).size)
    }

    @Test fun `los dias de la semana empiezan donde se pide`() {
        assertEquals(DayOfWeek.MONDAY, MonthGrid.weekdays(DayOfWeek.MONDAY).first())
        assertEquals(DayOfWeek.SUNDAY, MonthGrid.weekdays(DayOfWeek.MONDAY).last())
        assertEquals(DayOfWeek.SATURDAY, MonthGrid.weekdays(DayOfWeek.SUNDAY).last())
        assertEquals(7, MonthGrid.weekdays(DayOfWeek.WEDNESDAY).toSet().size)
    }

    @Test fun `los anos ofrecidos incluyen siempre el que se ve`() {
        val today = LocalDate.of(2026, 11, 17)
        assertEquals(2025..2036, MonthGrid.years(today, 2027))
        assertEquals(2025..2040, MonthGrid.years(today, 2040))
        assertEquals(2020..2036, MonthGrid.years(today, 2020))
    }
}

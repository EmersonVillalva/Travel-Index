package com.travelindex.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class CalendarBuilderTest {
    private val b = listOf(Fixtures.bundle())
    private val today = LocalDate.of(2027, 6, 1)

    @Test fun `la cuadricula tiene 6 semanas de 7 dias y empieza en lunes`() {
        val grid = CalendarBuilder.month(YearMonth.of(2027, 6), today, b, DayOfWeek.MONDAY)
        assertEquals(6, grid.size); assertTrue(grid.all { it.size == 7 })
        assertEquals(DayOfWeek.MONDAY, grid[0][0].date.dayOfWeek)
        assertEquals(LocalDate.of(2027, 5, 31), grid[0][0].date)   // 1 junio 2027 es martes
        assertFalse(grid[0][0].inMonth); assertTrue(grid[0][1].inMonth)
    }

    @Test fun `marca dias del viaje, eventos y hoy`() {
        val cells = CalendarBuilder.month(YearMonth.of(2027, 6), today, b).flatten().associateBy { it.date }
        assertTrue(cells.getValue(LocalDate.of(2027, 6, 14)).tripIds.contains(1L))
        assertTrue(cells.getValue(LocalDate.of(2027, 6, 19)).tripIds.isEmpty())
        assertTrue(EventType.FLIGHT in cells.getValue(LocalDate.of(2027, 6, 12)).eventTypes)
        assertTrue(cells.getValue(today).isToday)
    }

    @Test fun `eventsOn devuelve los eventos del dia ordenados y nada si esta cancelado`() {
        val e = CalendarBuilder.eventsOn(LocalDate.of(2027, 6, 12), b)
        assertEquals(listOf(EventType.FLIGHT, EventType.LODGING_CHECK_IN, EventType.RESTAURANT), e.map { it.event.type })
        val cancelled = listOf(b[0].copy(trip = Fixtures.trip.copy(cancelled = true)))
        assertTrue(CalendarBuilder.eventsOn(LocalDate.of(2027, 6, 12), cancelled).isEmpty())
    }

    @Test fun `semana que empieza en domingo desplaza la cuadricula`() {
        val g = CalendarBuilder.month(YearMonth.of(2027, 6), today, b, DayOfWeek.SUNDAY)
        assertEquals(DayOfWeek.SUNDAY, g[0][0].date.dayOfWeek)
    }
}

class ReminderPlannerTest {
    private val zone = ZoneId.of("Europe/Madrid")
    private val now = Fixtures.ms("Europe/Madrid", 2027, 6, 1, 9, 0)
    private val s = AppSettings()

    private fun plan(b: TripBundle = Fixtures.bundle(), settings: AppSettings = s, at: Long = now) =
        ReminderPlanner.plan(listOf(b), settings, at, zone)

    @Test fun `vuelo avisa 24 horas antes`() {
        val f = plan().first { it.key == "FLIGHT:1" }
        assertEquals(Fixtures.ms("Europe/Madrid", 2027, 6, 11, 11, 45), f.triggerAtMs)
        assertEquals(ReminderKind.FLIGHT, f.kind)
    }

    @Test fun `actividad con hora avisa 2 horas antes y restaurante tambien`() {
        assertEquals(Fixtures.ms("Europe/Rome", 2027, 6, 13, 8, 0), plan().first { it.key == "ACTIVITY:1" }.triggerAtMs)
        assertEquals(Fixtures.ms("Europe/Rome", 2027, 6, 12, 18, 30), plan().first { it.key == "RESTAURANT:1" }.triggerAtMs)
    }

    @Test fun `check-in avisa el mismo dia por la manana`() {
        assertEquals(Fixtures.ms("Europe/Rome", 2027, 6, 12, 9, 0), plan().first { it.key == "LODGING_IN:1" }.triggerAtMs)
    }

    @Test fun `caducidad de documento avisa 30 dias antes`() {
        val b = Fixtures.bundle().copy(docs = listOf(Fixtures.doc.copy(expiryDate = LocalDate.of(2027, 7, 15))))
        val d = plan(b).first { it.key == "DOC:1" }
        assertEquals(Fixtures.ms("Europe/Madrid", 2027, 6, 15, 10, 0), d.triggerAtMs)
    }

    @Test fun `no programa avisos pasados, ni de viajes cancelados, ni con notificaciones desactivadas`() {
        assertTrue(plan(at = Fixtures.ms("Europe/Madrid", 2027, 6, 30, 0, 0)).isEmpty())
        assertTrue(plan(Fixtures.bundle().copy(trip = Fixtures.trip.copy(cancelled = true))).isEmpty())
        assertTrue(plan(settings = s.copy(notificationsEnabled = false)).isEmpty())
    }

    @Test fun `claves unicas y ordenadas por fecha (sin duplicados al editar)`() {
        val p = plan()
        assertEquals(p.size, p.map { it.key }.toSet().size)
        assertEquals(p.sortedBy { it.triggerAtMs }, p)
        // Editar el vuelo (otro asiento) mantiene la misma clave
        val edited = plan(Fixtures.bundle().copy(flights = listOf(Fixtures.flight.copy(seat = "1A"))))
        assertEquals(p.map { it.key }, edited.map { it.key })
    }

    @Test fun `respeta el limite de horizonte`() {
        val far = Fixtures.bundle().copy(flights = listOf(Fixtures.flight.copy(
            departureEpochMs = Fixtures.ms("Europe/Madrid", 2028, 6, 12, 11, 0))))
        assertFalse(plan(far).any { it.key == "FLIGHT:1" })
    }
}

class DocWarningsAndAirportsTest {
    private val today = LocalDate.of(2027, 6, 1)
    private val d = Fixtures.doc

    @Test fun `avisos de caducidad`() {
        assertEquals(DocWarning.NONE, DocWarnings.evaluate(d, LocalDate.of(2027, 6, 18), today))
        assertEquals(DocWarning.EXPIRED, DocWarnings.evaluate(d.copy(expiryDate = LocalDate.of(2027, 5, 1)), null, today))
        assertEquals(DocWarning.EXPIRES_BEFORE_TRIP_END, DocWarnings.evaluate(d.copy(expiryDate = LocalDate.of(2027, 6, 15)), LocalDate.of(2027, 6, 18), today, soonDays = 5))
        assertEquals(DocWarning.EXPIRES_SOON, DocWarnings.evaluate(d.copy(expiryDate = LocalDate.of(2027, 7, 1)), null, today, soonDays = 60))
        assertEquals(DocWarning.PENDING, DocWarnings.evaluate(d.copy(expiryDate = null, status = "PENDING"), null, today))
        assertEquals(DocWarning.REJECTED, DocWarnings.evaluate(d.copy(status = "REJECTED"), null, today))
        assertEquals(30L, DocWarnings.daysLeft(d.copy(expiryDate = LocalDate.of(2027, 7, 1)), today))
    }

    @Test fun `enmascara numeros de documento`() {
        assertEquals("••••3456", DocWarnings.mask("PAB123456"))
        assertEquals("•••", DocWarnings.mask("123"))
        assertEquals("", DocWarnings.mask("  "))
    }

    @Test fun `todas las zonas de aeropuerto son validas`() {
        Airports.zones().forEach { ZoneId.of(it) }
        assertTrue(Airports.codes.size > 150)
    }

    @Test fun `deduce la zona del texto del aeropuerto`() {
        assertEquals("Europe/Madrid", Airports.zoneFor("mad"))
        assertEquals("Europe/Rome", Airports.zoneFor("Roma Fiumicino (FCO)"))
        assertEquals("America/New_York", Airports.zoneFor("JFK - Nueva York"))
        assertEquals(null, Airports.zoneFor("Aeropuerto desconocido"))
    }

    @Test fun `formatos de fecha segun preferencia`() {
        val f = Formats(DateFormatOption.DMY, java.util.Locale("es", "ES"))
        assertEquals("12/06/2027", f.date(LocalDate.of(2027, 6, 12)))
        assertEquals("2027-06-12", Formats(DateFormatOption.YMD).date(LocalDate.of(2027, 6, 12)))
        assertEquals("09:05", f.time(java.time.LocalTime.of(9, 5)))
    }
}

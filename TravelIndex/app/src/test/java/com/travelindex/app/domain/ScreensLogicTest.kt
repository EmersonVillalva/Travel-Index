package com.travelindex.app.domain

import com.travelindex.app.data.db.TripEntity
import com.travelindex.app.domain.forms.FieldError
import com.travelindex.app.domain.forms.TripCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

private fun trip(id: Long, name: String, country: String, start: LocalDate, end: LocalDate, cancelled: Boolean = false) =
    Fixtures.trip.copy(id = id, name = name, city = name, country = country, startDate = start, endDate = end, cancelled = cancelled)

class NoteValidatorTest {
    @Test fun `el texto es obligatorio`() {
        val e = NoteValidator.validate(NoteKind.NOTE, "   ", null)
        assertEquals(FieldError.REQUIRED, e["text"])
    }

    @Test fun `un recordatorio exige fecha pero una tarea no`() {
        assertEquals(FieldError.REQUIRED, NoteValidator.validate(NoteKind.REMINDER, "Pagar", null)["dueDate"])
        assertTrue(NoteValidator.validate(NoteKind.TODO, "Pagar", null).isEmpty())
        assertTrue(NoteValidator.validate(NoteKind.REMINDER, "Pagar", LocalDate.of(2027, 1, 1)).isEmpty())
    }

    @Test fun `tipos desconocidos caen en nota y las listas tienen casilla`() {
        assertEquals(NoteKind.NOTE, NoteKind.fromName("loquesea"))
        assertTrue(NoteKind.PACKING.checkable && NoteKind.TODO.checkable && NoteKind.SHOPPING.checkable)
        assertTrue(!NoteKind.NOTE.checkable && !NoteKind.INFO.checkable)
        assertTrue(NoteKind.TODO.usesDue && NoteKind.REMINDER.usesDue && !NoteKind.PACKING.usesDue)
    }
}

class TripListBuilderTest {
    private val today = LocalDate.of(2027, 6, 14)
    private val upcoming = trip(1, "Roma", "Italia", LocalDate.of(2027, 9, 1), LocalDate.of(2027, 9, 8))
    private val ongoing = trip(2, "Lisboa", "Portugal", LocalDate.of(2027, 6, 12), LocalDate.of(2027, 6, 16))
    private val pastOld = trip(3, "Cusco", "Perú", LocalDate.of(2024, 1, 5), LocalDate.of(2024, 1, 12))
    private val pastNew = trip(4, "Oslo", "Noruega", LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 4))
    private val cancelled = trip(5, "Tokio", "Japón", LocalDate.of(2027, 11, 1), LocalDate.of(2027, 11, 9), cancelled = true)
    private val all = listOf(upcoming, ongoing, pastOld, pastNew, cancelled)

    @Test fun `las secciones salen en orden y los pasados del mas reciente al mas antiguo`() {
        val s = TripListBuilder.build(all, "", TripFilter.ALL, TripSort.SMART, today)
        assertEquals(listOf(TripGroup.ONGOING, TripGroup.UPCOMING, TripGroup.PAST, TripGroup.CANCELLED), s.map { it.group })
        assertEquals(listOf(4L, 3L), s.first { it.group == TripGroup.PAST }.trips.map { it.id })
    }

    @Test fun `el filtro de pasados deja solo los terminados y los viajes pasados siguen visibles`() {
        val s = TripListBuilder.build(all, "", TripFilter.PAST, TripSort.SMART, today)
        assertEquals(listOf(TripGroup.PAST), s.map { it.group })
        assertEquals(2, s.single().trips.size)
    }

    @Test fun `proximos incluye los que estan en curso`() {
        val ids = TripListBuilder.build(all, "", TripFilter.UPCOMING, TripSort.SMART, today).flatMap { it.trips }.map { it.id }
        assertEquals(listOf(2L, 1L), ids)
    }

    @Test fun `la busqueda ignora acentos y mayusculas y mira nombre ciudad y pais`() {
        assertEquals(listOf(3L), TripListBuilder.build(all, "peru", TripFilter.ALL, TripSort.SMART, today).flatMap { it.trips }.map { it.id })
        assertEquals(listOf(5L), TripListBuilder.build(all, "  JAPON ", TripFilter.ALL, TripSort.SMART, today).flatMap { it.trips }.map { it.id })
        assertTrue(TripListBuilder.build(all, "zzz", TripFilter.ALL, TripSort.SMART, today).isEmpty())
    }

    @Test fun `los contadores cuadran`() {
        val c = TripListBuilder.counts(all, today)
        assertEquals(5, c[TripFilter.ALL]); assertEquals(2, c[TripFilter.UPCOMING])
        assertEquals(2, c[TripFilter.PAST]); assertEquals(1, c[TripFilter.CANCELLED])
    }

    @Test fun `orden por nombre y por fecha`() {
        val byName = TripListBuilder.build(listOf(pastOld, pastNew), "", TripFilter.ALL, TripSort.NAME, today).single().trips.map { it.name }
        assertEquals(listOf("Cusco", "Oslo"), byName)
        val asc = TripListBuilder.build(listOf(pastOld, pastNew), "", TripFilter.ALL, TripSort.DATE_ASC, today).single().trips.map { it.id }
        assertEquals(listOf(3L, 4L), asc)
    }
}

class HomeBuilderTest {
    private val today = LocalDate.of(2027, 6, 14)
    private val now = ZonedDateTime.of(2027, 6, 14, 12, 0, 0, 0, ZoneId.of("Europe/Madrid"))

    @Test fun `resume el viaje en curso, lo siguiente, avisos y estadisticas`() {
        val rome = Fixtures.bundle()   // 12-18 jun 2027, en curso
        val lima = TripBundle(
            trip(2, "Lima", "Perú", LocalDate.of(2027, 9, 1), LocalDate.of(2027, 9, 10)),
            docs = listOf(Fixtures.doc.copy(id = 2, tripId = 2, expiryDate = LocalDate.of(2027, 9, 5))),
            flights = listOf(Fixtures.flight.copy(id = 9, tripId = 2, status = "PENDING")),
        )
        val old = TripBundle(trip(3, "Turín", "Italia", LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 5)))
        val s = HomeBuilder.build(listOf(old, lima, rome), now, today)

        assertEquals(1L, s.next?.trip?.id)
        assertEquals(listOf(2L), s.upcomingTrips.map { it.id })
        assertEquals(listOf(3L), s.pastTrips.map { it.id })
        // Solo lo que queda por delante, ordenado: check-out del hotel y fin del coche.
        assertEquals(listOf("LODGING_OUT:1", "TRANSPORT_END:1"), s.nextEvents.map { it.event.key }.take(2))
        assertTrue(s.nextEvents.all { !it.event.start.isBefore(now) })
        // El pasaporte caduca antes de volver y hay un vuelo sin confirmar.
        assertEquals(DocWarning.EXPIRES_BEFORE_TRIP_END, s.attention.first().warning)
        assertTrue(s.attention.any { it.kind == AttentionKind.FLIGHT_PENDING })
        assertEquals(3, s.stats.trips)
        assertEquals(2, s.stats.countries)   // Italia (2 viajes) + Perú
    }

    @Test fun `sin viajes todo vacio`() {
        val s = HomeBuilder.build(emptyList(), now, today)
        assertNull(s.next)
        assertTrue(s.nextEvents.isEmpty() && s.attention.isEmpty() && s.pastTrips.isEmpty())
        assertEquals(0, s.stats.trips)
    }

    @Test fun `un viaje cancelado no cuenta ni genera avisos`() {
        val c = TripBundle(
            trip(7, "X", "Y", LocalDate.of(2027, 8, 1), LocalDate.of(2027, 8, 5), cancelled = true),
            flights = listOf(Fixtures.flight.copy(id = 4, tripId = 7, status = "PENDING")),
        )
        val s = HomeBuilder.build(listOf(c), now, today)
        assertNull(s.next); assertTrue(s.attention.isEmpty()); assertEquals(0, s.stats.trips)
    }
}

class TripCodecTest {
    @Test fun `viaje ida y vuelta de valores conserva todo`() {
        val t: TripEntity = Fixtures.trip.copy(budgetMinor = 150_000, description = "d", notes = "n", currency = "USD")
        val built = TripCodec.build(t.id, TripCodec.values(t), null, t.createdAt, false)
        assertEquals(t, built)
    }

    @Test fun `el regreso no puede ser anterior a la salida`() {
        val v = TripCodec.values(Fixtures.trip) + ("endDate" to "2027-06-01")
        assertEquals(FieldError.END_BEFORE_START, TripCodec.cross(v)["endDate"])
        assertTrue(TripCodec.cross(TripCodec.values(Fixtures.trip)).isEmpty())
    }

    @Test fun `los viajeros van de 1 a 99`() {
        val base = TripCodec.values(Fixtures.trip)
        assertEquals(FieldError.INVALID_NUMBER, TripCodec.cross(base + ("travelers" to "0"))["travelers"])
        assertEquals(FieldError.INVALID_NUMBER, TripCodec.cross(base + ("travelers" to "100"))["travelers"])
        assertTrue(TripCodec.cross(base + ("travelers" to "99")).isEmpty())
    }

    @Test fun `valores por defecto de un viaje nuevo`() {
        val d = TripCodec.defaults("MXN")
        assertEquals("1", d["travelers"]); assertEquals("MXN", d["currency"])
    }
}

class FullDaysTest {
    @Test fun `incluye los dias vacios del viaje`() {
        val days = ItineraryBuilder.fullDays(LocalDate.of(2027, 6, 12), LocalDate.of(2027, 6, 18), emptyList())
        assertEquals(7, days.size)
        assertTrue(days.all { it.events.isEmpty() })
    }

    @Test fun `incluye un dia con eventos fuera de las fechas del viaje, en orden`() {
        val outside = ItineraryBuilder.events(emptyList(), emptyList(), listOf(Fixtures.activity.copy(date = LocalDate.of(2027, 6, 25))))
        val inside = ItineraryBuilder.events(listOf(Fixtures.flight), emptyList(), emptyList())
        val days = ItineraryBuilder.fullDays(LocalDate.of(2027, 6, 12), LocalDate.of(2027, 6, 18), inside + outside)
        assertEquals(8, days.size)
        assertEquals(LocalDate.of(2027, 6, 25), days.last().date)
        assertEquals(1, days.last().events.size)
        assertEquals(1, days.first().events.size)
        assertEquals(days.map { it.date }.sorted(), days.map { it.date })
    }
}

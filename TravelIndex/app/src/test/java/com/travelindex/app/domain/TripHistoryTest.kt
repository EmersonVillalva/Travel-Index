package com.travelindex.app.domain

import com.travelindex.app.data.db.DestinationEntity
import com.travelindex.app.data.db.FlightEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TripHistoryTest {
    private val today = LocalDate.of(2026, 11, 14)

    private fun trip(id: Long, start: LocalDate, end: LocalDate, country: String = "Italia", city: String = "Roma", cancelled: Boolean = false) =
        Fixtures.trip.copy(id = id, name = "Viaje $id", startDate = start, endDate = end, country = country, city = city, cancelled = cancelled)

    private fun bundle(t: com.travelindex.app.data.db.TripEntity, flights: List<FlightEntity> = emptyList(), dest: List<DestinationEntity> = emptyList()) =
        TripBundle(trip = t, flights = flights, destinations = dest)

    @Test fun `sin viajes terminados el historial esta vacio`() {
        val h = TripHistoryBuilder.build(emptyList(), today)
        assertTrue(h.isEmpty); assertTrue(h.years.isEmpty()); assertEquals(0, h.stats.days)
        val onlyFuture = TripHistoryBuilder.build(listOf(bundle(trip(1, LocalDate.of(2027, 1, 1), LocalDate.of(2027, 1, 5)))), today)
        assertTrue(onlyFuture.isEmpty)
    }

    @Test fun `solo cuentan los viajes terminados y no los cancelados ni los de ahora`() {
        val past = trip(1, LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 4))
        val ongoing = trip(2, LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 20))
        val cancelled = trip(3, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 4), cancelled = true)
        val upcoming = trip(4, LocalDate.of(2027, 3, 1), LocalDate.of(2027, 3, 4))
        val h = TripHistoryBuilder.build(listOf(past, ongoing, cancelled, upcoming).map { bundle(it) }, today)
        assertEquals(listOf(1L), h.years.flatMap { it.trips }.map { it.trip.id })
        assertEquals(1, h.stats.trips)
    }

    @Test fun `un viaje que termino ayer ya es historial y uno que termina hoy todavia no`() {
        val endedYesterday = trip(1, LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 13))
        val endsToday = trip(2, LocalDate.of(2026, 11, 10), today)
        val h = TripHistoryBuilder.build(listOf(endedYesterday, endsToday).map { bundle(it) }, today)
        assertEquals(listOf(1L), h.years.flatMap { it.trips }.map { it.trip.id })
    }

    @Test fun `los anos van del mas reciente al mas antiguo y dentro de cada ano el ultimo viaje primero`() {
        val a = trip(1, LocalDate.of(2025, 2, 1), LocalDate.of(2025, 2, 5))
        val b = trip(2, LocalDate.of(2026, 1, 10), LocalDate.of(2026, 1, 15))
        val c = trip(3, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 9))
        val d = trip(4, LocalDate.of(2024, 12, 28), LocalDate.of(2025, 1, 3))   // acaba en 2025: cuenta en 2025
        val h = TripHistoryBuilder.build(listOf(a, b, c, d).map { bundle(it) }, today)
        assertEquals(listOf(2026, 2025), h.years.map { it.year })
        assertEquals(listOf(3L, 2L), h.years[0].trips.map { it.trip.id })
        assertEquals(listOf(1L, 4L), h.years[1].trips.map { it.trip.id })
    }

    @Test fun `los dias suman cada viaje con salida y regreso incluidos`() {
        val a = trip(1, LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 4))      // 4 días
        val b = trip(2, LocalDate.of(2026, 6, 10), LocalDate.of(2026, 6, 10))    // 1 día
        assertEquals(5, TripHistoryBuilder.build(listOf(a, b).map { bundle(it) }, today).stats.days)
    }

    @Test fun `paises y ciudades se cuentan sin repetir ni por acentos ni por mayusculas`() {
        assertEquals(2, TripHistoryBuilder.distinctPlaces(listOf("Perú", "peru ", "Italia", "", "  ")))
        val a = trip(1, LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 4), country = "Italia", city = "Roma")
        val b = trip(2, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 4), country = "ITALIA", city = "Florencia")
        val dest = DestinationEntity(id = 1, tripId = 2, city = "Pisa", country = "Italia", startDate = LocalDate.of(2026, 6, 2),
            endDate = LocalDate.of(2026, 6, 3), timeZoneId = "Europe/Rome", position = 0)
        val s = TripHistoryBuilder.build(listOf(bundle(a), bundle(b, dest = listOf(dest))), today).stats
        assertEquals(1, s.countries)
        assertEquals(3, s.cities)
    }

    @Test fun `lo pagado se suma por moneda sin mezclarlas`() {
        val t1 = trip(1, LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 4))
        val t2 = trip(2, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 4))
        val eur = Fixtures.flight.copy(id = 1, tripId = 1, priceMinor = 10_000, currency = "EUR")
        val eur2 = Fixtures.flight.copy(id = 2, tripId = 2, priceMinor = 5_050, currency = "EUR")
        val usd = Fixtures.flight.copy(id = 3, tripId = 2, priceMinor = 2_000, currency = "USD")
        val s = TripHistoryBuilder.build(listOf(bundle(t1, listOf(eur)), bundle(t2, listOf(eur2, usd))), today).stats
        assertEquals(mapOf("EUR" to 15_050L, "USD" to 2_000L), s.paid)
        assertEquals(mapOf("EUR" to 10_000L), TripHistoryBuilder.paidOf(bundle(t1, listOf(eur))))
    }

    @Test fun `un viaje sin precios no aporta importes`() {
        val s = TripHistoryBuilder.build(listOf(bundle(trip(1, LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 4)))), today).stats
        assertTrue(s.paid.isEmpty())
    }
}

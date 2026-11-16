package com.travelindex.app.domain

import com.travelindex.app.data.db.ActivityEntity
import com.travelindex.app.data.db.FlightEntity
import com.travelindex.app.data.db.LodgingEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class ItineraryBuilderTest {
    private fun ms(z: String, y: Int, m: Int, d: Int, h: Int, min: Int) =
        ZonedDateTime.of(y, m, d, h, min, 0, 0, ZoneId.of(z)).toInstant().toEpochMilli()

    private val flight = FlightEntity(
        id = 1, tripId = 1, airline = "IB", flightNumber = "3250",
        departureAirport = "MAD", arrivalAirport = "FCO",
        departureEpochMs = ms("Europe/Madrid", 2027, 6, 12, 11, 45), departureZoneId = "Europe/Madrid",
        arrivalEpochMs = ms("Europe/Rome", 2027, 6, 12, 14, 15), arrivalZoneId = "Europe/Rome",
    )
    private val hotel = LodgingEntity(
        id = 1, tripId = 1, name = "Hotel", checkInDate = LocalDate.of(2027, 6, 12),
        checkOutDate = LocalDate.of(2027, 6, 15), timeZoneId = "Europe/Rome", checkInTime = LocalTime.of(16, 0),
    )
    private val activity = ActivityEntity(
        id = 1, tripId = 1, name = "Coliseo", date = LocalDate.of(2027, 6, 13),
        startTime = LocalTime.of(10, 0), timeZoneId = "Europe/Rome",
    )

    @Test fun `eventos ordenados cronologicamente y agrupados por dia`() {
        val days = ItineraryBuilder.days(listOf(flight), listOf(hotel), listOf(activity))
        assertEquals(listOf(LocalDate.of(2027, 6, 12), LocalDate.of(2027, 6, 13), LocalDate.of(2027, 6, 15)), days.map { it.date })
        assertEquals(listOf(EventType.FLIGHT, EventType.LODGING_CHECK_IN), days[0].events.map { it.type })
    }

    @Test fun `editar una reserva no duplica eventos`() {
        val before = ItineraryBuilder.events(listOf(flight), listOf(hotel), listOf(activity))
        val after = ItineraryBuilder.events(listOf(flight.copy(seat = "12A")), listOf(hotel), listOf(activity))
        assertEquals(before.map { it.key }, after.map { it.key })
        assertEquals(before.size, after.map { it.key }.toSet().size)
    }

    @Test fun `la hora de llegada respeta la zona horaria del destino`() {
        val ev = ItineraryBuilder.events(listOf(flight), emptyList(), emptyList()).single()
        assertEquals(14, ev.end!!.hour)
        assertEquals(11, ev.start.hour)
    }

    @Test fun `check-in sin hora usa la hora por defecto y marca hora desconocida`() {
        val ev = ItineraryBuilder.events(emptyList(), listOf(hotel.copy(checkInTime = null)), emptyList())
            .first { it.type == EventType.LODGING_CHECK_IN }
        assertFalse(ev.timeKnown); assertEquals(15, ev.start.hour)
    }

    @Test fun `vuelo nocturno que cambia de dia se agrupa por dia de salida`() {
        val night = flight.copy(
            departureEpochMs = ms("Europe/Madrid", 2027, 6, 12, 23, 30),
            arrivalEpochMs = ms("America/New_York", 2027, 6, 13, 2, 0), arrivalZoneId = "America/New_York",
        )
        val days = ItineraryBuilder.days(listOf(night), emptyList(), emptyList())
        assertEquals(LocalDate.of(2027, 6, 12), days.single().date)
        assertTrue(days.single().events.single().end!!.zone.id == "America/New_York")
    }

    // El día de llegada, primero el transporte y luego el alojamiento aunque el check-in no tenga hora.
    @Test fun `check-in sin hora sale despues del vuelo que llega tarde ese dia`() {
        val lateFlight = flight.copy(
            departureEpochMs = ms("Europe/Madrid", 2027, 6, 12, 18, 0),
            arrivalEpochMs = ms("Europe/Rome", 2027, 6, 12, 20, 30),
        )
        val day = ItineraryBuilder.group(
            ItineraryBuilder.events(listOf(lateFlight), listOf(hotel.copy(checkInTime = null)), emptyList()),
        ).first { it.date == LocalDate.of(2027, 6, 12) }
        assertEquals(listOf(EventType.FLIGHT, EventType.LODGING_CHECK_IN), day.events.map { it.type })
    }

    // El último día, el check-out sin hora sale antes del transporte de vuelta, aunque salga de madrugada.
    @Test fun `check-out sin hora sale antes del vuelo de vuelta aunque salga pronto`() {
        val returnFlight = flight.copy(
            id = 2, departureAirport = "FCO", arrivalAirport = "MAD",
            departureEpochMs = ms("Europe/Rome", 2027, 6, 15, 8, 0), departureZoneId = "Europe/Rome",
            arrivalEpochMs = ms("Europe/Madrid", 2027, 6, 15, 10, 30), arrivalZoneId = "Europe/Madrid",
        )
        val day = ItineraryBuilder.group(
            ItineraryBuilder.events(listOf(returnFlight), listOf(hotel.copy(checkOutTime = null)), emptyList()),
        ).first { it.date == LocalDate.of(2027, 6, 15) }
        assertEquals(listOf(EventType.LODGING_CHECK_OUT, EventType.FLIGHT), day.events.map { it.type })
    }

    // Sin transportes ese día, el check-in sin hora conserva su hora por defecto (15:00).
    @Test fun `sin movimientos ese dia el check-in sin hora se queda a las 15`() {
        val ev = ItineraryBuilder.events(emptyList(), listOf(hotel.copy(checkInTime = null)), emptyList())
            .first { it.type == EventType.LODGING_CHECK_IN }
        assertEquals(15, ev.start.hour)
    }

    // El coche simplificado no tiene empresa ni modelo: su título es el trayecto, sin repetirlo en el subtítulo.
    @Test fun `un coche sin empresa se titula por su trayecto`() {
        val car = Fixtures.transport.copy(company = "", model = "", originPlace = "Aeropuerto", destinationPlace = "Centro")
        val ev = ItineraryBuilder.events(emptyList(), emptyList(), emptyList(), transports = listOf(car))
            .first { it.type == EventType.TRANSPORT_START }
        assertEquals("Aeropuerto → Centro", ev.title)
        assertEquals(null, ev.subtitle)
    }
}

class ItineraryExtendedTest {
    @Test fun `incluye transportes y restaurantes con clave estable y no genera eventos de notas heredadas`() {
        val keys = Fixtures.bundle().events.map { it.key }.toSet()
        assertEquals(
            setOf("FLIGHT:1", "LODGING_IN:1", "LODGING_OUT:1", "ACTIVITY:1", "TRANSPORT_START:1", "TRANSPORT_END:1", "RESTAURANT:1"),
            keys,
        )
    }

    @Test fun `una nota heredada no genera evento aunque este pendiente`() {
        val b = Fixtures.bundle().copy(notes = listOf(Fixtures.note.copy(done = false)))
        assertFalse(b.events.any { it.type == EventType.NOTE_DUE })
    }

    @Test fun `el dia 12 mezcla vuelo, check-in y cena en orden`() {
        val day = ItineraryBuilder.group(Fixtures.bundle().events).first { it.date == LocalDate.of(2027, 6, 12) }
        assertEquals(listOf(EventType.FLIGHT, EventType.LODGING_CHECK_IN, EventType.RESTAURANT), day.events.map { it.type })
    }
}

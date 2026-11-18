package com.travelindex.app.domain

import com.travelindex.app.data.db.FlightEntity
import com.travelindex.app.domain.forms.FlightCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Madrid → Mánchester → Pekín: dos vuelos distintos enlazados como un solo viaje con escalas. */
class FlightConnectionsTest {
    private fun leg(id: Long, from: String, to: String, dep: Long, arr: Long, cid: String = "C1") = Fixtures.flight.copy(
        id = id, departureAirport = from, arrivalAirport = to, departureEpochMs = dep, arrivalEpochMs = arr, connectionId = cid,
        departureZoneId = "UTC", arrivalZoneId = "UTC",
    )

    private val h = 3_600_000L
    private val t0 = Fixtures.ms("UTC", 2027, 3, 1, 8, 0)
    private val madMan = leg(1, "MAD", "MAN", t0, t0 + 2 * h + 30 * 60_000)                    // 08:00 → 10:30
    private val manPek = leg(2, "MAN", "PEK", t0 + 5 * h + 40 * 60_000, t0 + 15 * h)            // sale a las 13:40 (escala de 3 h 10 min)
    private val solo = Fixtures.flight.copy(id = 3, connectionId = "")
    private val all = listOf(manPek, solo, madMan)                                               // desordenados a propósito

    @Test fun `un vuelo suelto es un unico tramo y no tiene escalas`() {
        assertEquals(listOf(solo), FlightConnections.legsOf(solo, all))
        assertNull(FlightConnections.infoFor(solo, all))
    }

    @Test fun `los tramos de una conexion salen por hora de salida`() {
        assertEquals(listOf(madMan, manPek), FlightConnections.legsOf(manPek, all))
        assertEquals(listOf(madMan, manPek), FlightConnections.legsOf(madMan, all))
    }

    @Test fun `la ruta nombra la salida y cada llegada`() {
        assertEquals("Madrid-Barajas → Mánchester → Pekín Capital", FlightConnections.routeText(listOf(madMan, manPek)))
    }

    @Test fun `un tramo que sale de otro aeropuerto tambien lo nombra`() {
        val other = leg(4, "LGW", "PEK", t0 + 6 * h, t0 + 16 * h)
        assertEquals("Madrid-Barajas → Londres Heathrow → Londres Gatwick → Pekín Capital",
            FlightConnections.routeText(listOf(leg(5, "MAD", "LHR", t0, t0 + 2 * h), other)))
    }

    @Test fun `la informacion del tramo lleva su numero la ruta y la escala previa`() {
        val first = FlightConnections.infoFor(madMan, all)!!
        assertEquals(1, first.number); assertEquals(2, first.count)
        assertNull(first.layoverMinutes); assertNull(first.layoverAt)
        val second = FlightConnections.infoFor(manPek, all)!!
        assertEquals(2, second.number)
        assertEquals(3 * 60 + 10L, second.layoverMinutes)
        assertEquals("Mánchester", second.layoverAt)
        assertEquals("Madrid-Barajas → Mánchester → Pekín Capital", second.route)
    }

    @Test fun `si borras un tramo el que queda ya no es una conexion`() {
        assertNull(FlightConnections.infoFor(madMan, listOf(madMan, solo)))
    }

    @Test fun `la escala solo se calcula con una llegada conocida y horas coherentes`() {
        val unknownArrival = madMan.copy(arrivalEpochMs = madMan.departureEpochMs)   // sin hora de llegada
        assertNull(FlightConnections.layoverMinutes(unknownArrival, manPek))
        val overlapping = manPek.copy(departureEpochMs = madMan.arrivalEpochMs - h)   // sale antes de llegar el anterior
        assertNull(FlightConnections.layoverMinutes(madMan, overlapping))
        assertEquals(0L, FlightConnections.layoverMinutes(madMan, manPek.copy(departureEpochMs = madMan.arrivalEpochMs)))
    }

    @Test fun `solo el ultimo tramo permite anadir otro a continuacion`() {
        assertFalse(FlightConnections.isLastLeg(madMan, all))
        assertTrue(FlightConnections.isLastLeg(manPek, all))
        assertTrue(FlightConnections.isLastLeg(solo, all))
    }

    @Test fun `el tramo siguiente sale de donde llego el anterior y conserva la reserva`() {
        val saved = mapOf(
            "airline" to "IB", "flightNumber" to "3080", "departureAirport" to "Madrid", "arrivalAirport" to "Mánchester",
            "depDate" to "2027-03-01", "depTime" to "08:00", "depZone" to "Europe/Madrid",
            "arrDate" to "2027-03-01", "arrTime" to "09:30", "arrZone" to "Europe/London",
            "locator" to "ABC123", "ticketNumber" to "075-123", "cabinClass" to "ECONOMY", "baggage" to "1 maleta",
            "price" to "900,00", "currency" to "EUR", "status" to "CONFIRMED", "seat" to "12A", "terminal" to "T4", "notes" to "x",
        )
        val next = FlightConnections.nextLegValues(saved, "C9")
        assertEquals("C9", next["connectionId"])
        assertEquals("Mánchester", next["departureAirport"])
        assertEquals("2027-03-01", next["depDate"]); assertEquals("2027-03-01", next["arrDate"])
        assertEquals("Europe/London", next["depZone"]); assertEquals("Europe/London", next["arrZone"])
        assertEquals("ABC123", next["locator"]); assertEquals("075-123", next["ticketNumber"])
        assertEquals("ECONOMY", next["cabinClass"]); assertEquals("1 maleta", next["baggage"]); assertEquals("EUR", next["currency"])
        // Lo propio de cada tramo queda en blanco.
        listOf("airline", "flightNumber", "depTime", "arrTime", "arrivalAirport", "seat", "terminal", "price", "notes").forEach {
            assertTrue("'$it' debe quedar vacío", next[it].isNullOrEmpty())
        }
    }

    @Test fun `sin fecha de llegada el siguiente tramo usa la fecha de salida`() {
        val next = FlightConnections.nextLegValues(mapOf("depDate" to "2027-03-01", "depZone" to "Europe/Madrid", "arrivalAirport" to "Roma"), "C1")
        assertEquals("2027-03-01", next["depDate"]); assertEquals("Europe/Madrid", next["depZone"])
        assertEquals("CONFIRMED", next["status"])
    }

    @Test fun `el identificador de conexion es nuevo cada vez`() {
        assertNotEquals(FlightConnections.newConnectionId(), FlightConnections.newConnectionId())
        assertTrue(FlightConnections.newConnectionId().isNotBlank())
    }

    @Test fun `el identificador de conexion sobrevive a guardar y editar`() {
        val values = FlightCodec.values(madMan)
        assertEquals("C1", values["connectionId"])
        assertEquals("C1", FlightCodec.build(1, 1, values).connectionId)
        // Un vuelo guardado antes de esta función (sin el valor) queda como vuelo suelto.
        assertEquals("", FlightCodec.build(1, 1, values - "connectionId").connectionId)
    }
}

package com.travelindex.app.domain

import com.travelindex.app.data.db.FlightEntity
import com.travelindex.app.domain.forms.FieldError
import com.travelindex.app.domain.forms.FlightCodec
import com.travelindex.app.domain.forms.ModuleCodecs
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

    // ---- Formulario con escalas: Madrid → Mánchester → Pekín dentro de un solo formulario ----------------------------------

    /** Lo que el usuario rellena: primer vuelo, una escala en Mánchester (llega 09:30, sale 13:40) y la llegada a Pekín. */
    private val form: Map<String, String> = mapOf(
        "airline" to "IB", "flightNumber" to "3080",
        "departureAirport" to "MAD", "depDate" to "2027-03-01", "depTime" to "08:00", "depZone" to "Europe/Madrid",
        "stops" to "1",
        "s1_airport" to "MAN", "s1_zone" to "Europe/London",
        "s1_arrDate" to "2027-03-01", "s1_arrTime" to "09:30",
        "s1_depDate" to "2027-03-01", "s1_depTime" to "13:40",
        "s1_airline" to "CA", "s1_flightNumber" to "902",
        "arrivalAirport" to "PEK", "arrDate" to "2027-03-02", "arrTime" to "06:00", "arrZone" to "Asia/Shanghai",
        "locator" to "ABC123", "ticketNumber" to "075-123", "cabinClass" to "ECONOMY", "baggage" to "1 maleta",
        "currency" to "EUR", "price" to "900.00", "status" to "CONFIRMED", "terminal" to "T4", "seat" to "12A", "notes" to "Viaje a Pekín",
    )
    private val direct = form - "stops"

    @Test fun `el numero de escalas sale del formulario y tiene limite`() {
        assertEquals(0, FlightConnections.stopsOf(direct))
        assertEquals(1, FlightConnections.stopsOf(form))
        assertEquals(FlightConnections.MAX_STOPS, FlightConnections.stopsOf(form + ("stops" to "99")))
        assertEquals(0, FlightConnections.stopsOf(form + ("stops" to "-3")))
        assertEquals(0, FlightConnections.stopsOf(form + ("stops" to "abc")))
    }

    @Test fun `las claves de una escala se reconocen`() {
        assertEquals(2 to "airport", FlightConnections.parseStopKey("s2_airport"))
        assertEquals(6 to "flightNumber", FlightConnections.parseStopKey(FlightConnections.stopKey(6, "flightNumber")))
        assertNull(FlightConnections.parseStopKey("airport"))
        assertNull(FlightConnections.parseStopKey("seat"))
        assertNull(FlightConnections.parseStopKey("stops"))
    }

    @Test fun `sin escalas sale un solo vuelo y sin conexion`() {
        val legs = FlightConnections.legValues(direct + ("connectionId" to "VIEJA"), "C9")
        assertEquals(1, legs.size)
        assertEquals("", legs[0]["connectionId"])
        assertEquals("Madrid-Barajas", legs[0]["departureAirport"]); assertEquals("Pekín Capital", legs[0]["arrivalAirport"])
        assertEquals("IB", legs[0]["airline"]); assertEquals("2027-03-02", legs[0]["arrDate"])
    }

    @Test fun `una escala reparte el formulario en dos vuelos que se encadenan`() {
        val (first, second) = FlightConnections.legValues(form, "C9")
        assertEquals("C9", first["connectionId"]); assertEquals("C9", second["connectionId"])
        // Primer vuelo: lo de arriba, hasta la escala.
        assertEquals("IB", first["airline"]); assertEquals("3080", first["flightNumber"])
        assertEquals("Madrid-Barajas", first["departureAirport"]); assertEquals("Mánchester", first["arrivalAirport"])
        assertEquals("08:00", first["depTime"]); assertEquals("09:30", first["arrTime"]); assertEquals("Europe/London", first["arrZone"])
        // Segundo vuelo: sale de la escala con su aerolínea y su número, y llega adonde acaba el viaje.
        assertEquals("CA", second["airline"]); assertEquals("902", second["flightNumber"])
        assertEquals("Mánchester", second["departureAirport"]); assertEquals("Pekín Capital", second["arrivalAirport"])
        assertEquals("13:40", second["depTime"]); assertEquals("Europe/London", second["depZone"])
        assertEquals("2027-03-02", second["arrDate"]); assertEquals("06:00", second["arrTime"]); assertEquals("Asia/Shanghai", second["arrZone"])
    }

    @Test fun `la reserva vale para todos los vuelos y lo propio del primero no se copia`() {
        val (first, second) = FlightConnections.legValues(form, "C9")
        listOf("locator", "ticketNumber", "cabinClass", "baggage", "currency", "status").forEach { assertEquals(first[it], second[it]) }
        assertEquals("ABC123", second["locator"])
        assertEquals("T4", first["terminal"]); assertEquals("12A", first["seat"]); assertEquals("900.00", first["price"])
        assertEquals("Viaje a Pekín", first["notes"])
        listOf("terminal", "seat", "price", "notes").forEach { assertTrue("'$it' debe quedar vacío", second[it].isNullOrEmpty()) }
    }

    @Test fun `con varias escalas cada vuelo sale de donde llego el anterior`() {
        val v = form + mapOf(
            "stops" to "3",
            "s2_airport" to "LHR", "s2_arrDate" to "2027-03-01", "s2_arrTime" to "20:00", "s2_depDate" to "2027-03-01", "s2_depTime" to "22:00",
            "s2_airline" to "BA", "s2_flightNumber" to "11", "s2_zone" to "Europe/London",
            "s3_airport" to "LGW", "s3_arrDate" to "2027-03-02", "s3_arrTime" to "01:00", "s3_depDate" to "2027-03-02", "s3_depTime" to "03:00",
            "s3_airline" to "VY", "s3_flightNumber" to "7", "s3_zone" to "Europe/London",
        )
        val legs = FlightConnections.legValues(v, "C9")
        assertEquals(4, legs.size)
        assertEquals(listOf("IB", "CA", "BA", "VY"), legs.map { it["airline"] })
        for (i in 1 until legs.size) {
            assertEquals(legs[i - 1]["arrivalAirport"], legs[i]["departureAirport"])
            assertEquals(legs[i - 1]["arrZone"], legs[i]["depZone"])
        }
        assertEquals("Madrid-Barajas", legs.first()["departureAirport"]); assertEquals("Pekín Capital", legs.last()["arrivalAirport"])
        assertEquals(setOf("C9"), legs.map { it["connectionId"] }.toSet())
    }

    @Test fun `las escalas ocultas no se guardan aunque tengan datos`() {
        val hidden = direct + mapOf("s1_airport" to "MAN", "s1_airline" to "CA", "s1_flightNumber" to "902")
        assertEquals(1, FlightConnections.legValues(hidden, "C9").size)
        // Con 1 escala pedida, la segunda escrita queda fuera.
        val two = form + mapOf("s2_airport" to "LHR", "s2_airline" to "BA")
        assertEquals(2, FlightConnections.legValues(two, "C9").size)
    }

    @Test fun `los vuelos que se guardan llevan sus horas y zonas`() {
        val (first, second) = FlightConnections.buildLegs(7, "C9", form, emptyList())
        assertEquals(0L, first.id); assertEquals(0L, second.id)
        assertEquals(7L, first.tripId)
        assertEquals(Fixtures.ms("Europe/Madrid", 2027, 3, 1, 8, 0), first.departureEpochMs)
        assertEquals(Fixtures.ms("Europe/London", 2027, 3, 1, 9, 30), first.arrivalEpochMs)
        assertEquals("Europe/London", first.arrivalZoneId)
        assertEquals(Fixtures.ms("Europe/London", 2027, 3, 1, 13, 40), second.departureEpochMs)
        assertEquals(Fixtures.ms("Asia/Shanghai", 2027, 3, 2, 6, 0), second.arrivalEpochMs)
        assertEquals("C9", first.connectionId); assertEquals("C9", second.connectionId)
        assertEquals(90_000L, first.priceMinor); assertNull(second.priceMinor)
        assertEquals("12A", first.seat); assertEquals("", second.seat)
        // La escala dura lo que hay entre la llegada (09:30) y la salida (13:40): 4 h 10 min.
        assertEquals(250L, FlightConnections.layoverMinutes(first, second))
    }

    @Test fun `sin hora de llegada a la escala el vuelo queda con llegada desconocida`() {
        val (first, _) = FlightConnections.buildLegs(7, "C9", form - "s1_arrTime", emptyList())
        assertFalse(FlightCodec.hasArrival(first))
        assertEquals(first.departureEpochMs, first.arrivalEpochMs)
    }

    @Test fun `guardar y volver a abrir deja los mismos vuelos`() {
        val saved = FlightConnections.buildLegs(7, "C9", form, emptyList()).mapIndexed { i, leg -> leg.copy(id = 11L + i) }
        val reopened = FlightConnections.formValues(saved)
        assertEquals("1", reopened["stops"])
        assertEquals("Mánchester", reopened["s1_airport"]); assertEquals("Europe/London", reopened["s1_zone"])
        assertEquals("09:30", reopened["s1_arrTime"]); assertEquals("13:40", reopened["s1_depTime"])
        assertEquals("CA", reopened["s1_airline"]); assertEquals("902", reopened["s1_flightNumber"])
        assertEquals("Pekín Capital", reopened["arrivalAirport"]); assertEquals("2027-03-02", reopened["arrDate"])
        assertEquals("C9", reopened["connectionId"])
        assertEquals(saved, FlightConnections.buildLegs(7, "C9", reopened, saved))
    }

    @Test fun `un vuelo directo se abre sin escalas`() {
        val reopened = FlightConnections.formValues(listOf(Fixtures.flight))
        assertEquals(FlightCodec.values(Fixtures.flight), reopened)
        assertEquals(0, FlightConnections.stopsOf(reopened))
    }

    @Test fun `al abrir un viaje con escalas el precio es la suma de sus vuelos`() {
        val saved = FlightConnections.buildLegs(7, "C9", form, emptyList()).mapIndexed { i, leg -> leg.copy(id = 11L + i) }
        val withSecondPrice = listOf(saved[0], saved[1].copy(priceMinor = 5_000))
        val reopened = FlightConnections.formValues(withSecondPrice)
        assertEquals("950.00", reopened["price"])
        // Al guardar queda todo en el primero y el total no cambia.
        val rebuilt = FlightConnections.buildLegs(7, "C9", reopened, withSecondPrice)
        assertEquals(95_000L, rebuilt.sumOf { it.priceMinor ?: 0L })
        assertEquals(95_000L, rebuilt[0].priceMinor); assertNull(rebuilt[1].priceMinor)
    }

    @Test fun `un precio en otra moneda no se suma ni se pierde`() {
        val saved = FlightConnections.buildLegs(7, "C9", form, emptyList()).mapIndexed { i, leg -> leg.copy(id = 11L + i) }
        val mixed = listOf(saved[0], saved[1].copy(priceMinor = 7_000, currency = "USD"))
        val reopened = FlightConnections.formValues(mixed)
        assertEquals("900.00", reopened["price"])
        val rebuilt = FlightConnections.buildLegs(7, "C9", reopened, mixed)
        assertEquals(7_000L, rebuilt[1].priceMinor); assertEquals("USD", rebuilt[1].currency)
        assertEquals("EUR", rebuilt[0].currency)
    }

    @Test fun `al editar cada vuelo conserva su id y lo que el formulario no ensena`() {
        val saved = FlightConnections.buildLegs(7, "C9", form, emptyList())
            .mapIndexed { i, leg -> leg.copy(id = 11L + i) }
            .let { listOf(it[0], it[1].copy(seat = "14C", terminal = "T1", notes = "tren a la ciudad")) }
        val edited = FlightConnections.buildLegs(7, "C9", FlightConnections.formValues(saved) + ("s1_flightNumber" to "903"), saved)
        assertEquals(listOf(11L, 12L), edited.map { it.id })
        assertEquals("903", edited[1].flightNumber)
        assertEquals("14C", edited[1].seat); assertEquals("T1", edited[1].terminal); assertEquals("tren a la ciudad", edited[1].notes)
    }

    @Test fun `quitar las escalas deja solo el primer vuelo sin conexion`() {
        val saved = FlightConnections.buildLegs(7, "C9", form, emptyList()).mapIndexed { i, leg -> leg.copy(id = 11L + i) }
        val values = FlightConnections.withStops(FlightConnections.formValues(saved), 0)
        val result = FlightConnections.buildLegs(7, "C9", values, saved)
        assertEquals(1, result.size)
        assertEquals(11L, result[0].id); assertEquals("", result[0].connectionId)
        assertEquals("Pekín Capital", result[0].arrivalAirport)   // la llegada final es la que queda
    }

    @Test fun `anadir escalas a un vuelo directo reutiliza el vuelo y crea los demas`() {
        val saved = listOf(Fixtures.flight.copy(id = 5, connectionId = ""))
        val values = FlightConnections.withStops(FlightConnections.formValues(saved), 2) + mapOf(
            "s1_airport" to "MAN", "s1_airline" to "CA", "s1_flightNumber" to "1", "s1_depTime" to "10:00",
            "s2_airport" to "LHR", "s2_airline" to "BA", "s2_flightNumber" to "2", "s2_depTime" to "12:00",
        )
        val result = FlightConnections.buildLegs(1, "C9", values, saved)
        assertEquals(listOf(5L, 0L, 0L), result.map { it.id })
        assertEquals(setOf("C9"), result.map { it.connectionId }.toSet())
    }

    @Test fun `al pedir escalas las nuevas empiezan el mismo dia que el vuelo anterior`() {
        val base = mapOf("depDate" to "2027-03-01", "airline" to "IB")
        val two = FlightConnections.withStops(base, 2)
        assertEquals("2", two["stops"])
        listOf("s1_arrDate", "s1_depDate", "s2_arrDate", "s2_depDate").forEach { assertEquals(it, "2027-03-01", two[it]) }
        assertEquals("IB", two["airline"])
        // Lo que ya estaba escrito no se pisa, y la escala siguiente parte de la salida de la anterior.
        val typed = FlightConnections.withStops(base + ("s1_depDate" to "2027-03-02"), 2)
        assertEquals("2027-03-01", typed["s1_arrDate"]); assertEquals("2027-03-02", typed["s1_depDate"]); assertEquals("2027-03-02", typed["s2_arrDate"])
    }

    @Test fun `quitar las escalas oculta pero no borra lo escrito y el numero tiene limite`() {
        val two = FlightConnections.withStops(mapOf("depDate" to "2027-03-01"), 2)
        val none = FlightConnections.withStops(two, 0)
        assertFalse("stops" in none)
        assertEquals("2027-03-01", none["s1_depDate"])
        assertEquals(FlightConnections.MAX_STOPS.toString(), FlightConnections.withStops(two, 99)["stops"])
        assertFalse("stops" in FlightConnections.withStops(two, -1))
    }

    @Test fun `un vuelo no puede salir antes de que llegue el anterior`() {
        assertTrue(FlightConnections.cross(form).isEmpty())
        val early = form + ("s1_depTime" to "09:00")   // el anterior llega a las 09:30
        assertEquals(mapOf("s1_depDate" to FieldError.DEPARTS_BEFORE_ARRIVAL), FlightConnections.cross(early))
        // Pasa por el mismo sitio que usa el formulario.
        assertEquals(FlightConnections.cross(early), ModuleCodecs.cross(ModuleType.FLIGHT, early))
    }

    @Test fun `sin hora de llegada a la escala se compara con la salida del vuelo anterior`() {
        val noArrival = form - "s1_arrTime"
        assertTrue(FlightConnections.cross(noArrival).isEmpty())
        val before = noArrival + ("s1_depTime" to "06:30")   // el primer vuelo sale a las 08:00 (hora de Madrid) = 07:00 en Londres
        assertEquals(setOf("s1_depDate"), FlightConnections.cross(before).keys)
    }

    @Test fun `cada llegada se compara con su propia salida y el error sale en su campo`() {
        val atStop = form + ("s1_arrTime" to "06:00")   // 06:00 en Londres = 07:00 en Madrid: antes de salir
        assertEquals(mapOf("s1_arrDate" to FieldError.END_BEFORE_START), FlightConnections.cross(atStop))
        val atEnd = form + mapOf("arrDate" to "2027-03-01", "arrTime" to "09:00")
        assertEquals(mapOf("arrDate" to FieldError.END_BEFORE_START), FlightConnections.cross(atEnd))
    }

    @Test fun `la fecha que se rellena sola en la escala no obliga a poner la hora`() {
        assertTrue(FlightConnections.cross(form - "s1_arrTime").isEmpty())
        // Si se pone la hora, la fecha tiene que estar.
        assertEquals(mapOf("s1_arrDate" to FieldError.REQUIRED), FlightConnections.cross(form - "s1_arrDate"))
    }

    @Test fun `sin escalas se valida igual que un vuelo suelto`() {
        assertTrue(FlightConnections.cross(direct).isEmpty())
        assertEquals(FlightCodec.cross(direct + ("arrDate" to "2027-02-28")), FlightConnections.cross(direct + ("arrDate" to "2027-02-28")))
        assertEquals(setOf("arrDate"), FlightConnections.cross(direct + ("arrDate" to "2027-02-28")).keys)
    }
}

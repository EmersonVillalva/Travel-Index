package com.travelindex.app.domain

import com.travelindex.app.data.db.AttachmentEntity
import com.travelindex.app.data.db.ExpenseEntity
import com.travelindex.app.domain.forms.FlightCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class MiniJsonTest {
    @Test fun `ida y vuelta con texto, numeros, listas y nulos`() {
        val src = mapOf("a" to "x\"y\\z\ná", "n" to 5L, "d" to 1.5, "b" to true, "l" to listOf("1", mapOf("k" to null)))
        val back = MiniJson.parseObject(MiniJson.stringify(src))
        assertEquals("x\"y\\z\ná", back["a"]); assertEquals(5L, (back["n"] as Number).toLong())
        assertEquals(true, back["b"]); assertEquals(2, (back["l"] as List<*>).size)
    }

    @Test fun `rechaza JSON roto y anidacion excesiva`() {
        listOf("", "{", "{\"a\":}", "[1,]x", "nope").forEach {
            val r = runCatching { MiniJson.parse(it) }
            assertTrue("debía fallar: $it", r.isFailure && r.exceptionOrNull() is IllegalArgumentException)
        }
        val deep = "[".repeat(200) + "]".repeat(200)
        assertTrue(runCatching { MiniJson.parse(deep) }.exceptionOrNull() is IllegalArgumentException)
    }
}

class CostBreakdownTest {
    @Test fun `separa por categoria y por moneda sin mezclar`() {
        val b = Fixtures.bundle().let {
            it.copy(
                flights = listOf(Fixtures.flight, Fixtures.flight.copy(id = 2, priceMinor = 10_000, currency = "USD")),
                expenses = listOf(ExpenseEntity(1, 1, "Taxi", "TRANSPORT", 2_500, "EUR", LocalDate.of(2027, 6, 13))),
            )
        }
        val lines = CostBreakdown.of(b).associateBy { it.category }
        assertEquals(mapOf("EUR" to 18_990L, "USD" to 10_000L), lines.getValue(CostCategory.FLIGHTS).byCurrency)
        assertEquals(mapOf("EUR" to 45_000L), lines.getValue(CostCategory.LODGING).byCurrency)
        assertEquals(mapOf("EUR" to 2_500L), lines.getValue(CostCategory.OTHER).byCurrency)
        assertEquals(CostCategory.entries.filter { it in lines.keys }, CostBreakdown.of(b).map { it.category })   // orden fijo
        val t = CostBreakdown.totals(CostBreakdown.of(b))
        assertEquals(18_990L + 45_000 + 30_000 / 5 * 0 + 0 + 2_500 + 0 + 12_000 + 6_000 + 0, t.getValue("EUR") - 0)   // vuelos+hotel+gasto+coche+restaurante
        assertEquals(10_000L, t.getValue("USD"))
    }

    @Test fun `sin precios no hay lineas`() {
        val b = Fixtures.bundle().copy(
            flights = listOf(Fixtures.flight.copy(priceMinor = null)), lodgings = emptyList(), activities = emptyList(),
            transports = emptyList(), restaurants = emptyList(),
        )
        assertTrue(CostBreakdown.of(b).isEmpty())
    }
}

class TripShareFormatTest {
    private val b = Fixtures.bundle().copy(
        attachments = listOf(
            AttachmentEntity(id = 1, tripId = 1, ownerType = "FLIGHT", ownerId = 1, displayName = "Tarjeta.pdf",
                relativePath = "x/y.pdf", mimeType = "application/pdf", sizeBytes = 1234, category = "BOARDING_PASS"),
            AttachmentEntity(id = 2, tripId = 1, ownerType = null, ownerId = null, displayName = "foto.jpg",
                relativePath = "z.jpg", mimeType = "image/jpeg", sizeBytes = 99, category = "PHOTO"),
        ),
    )

    private fun pkg() = TripShareFormat.fromBundle(b, { "files/${it.id}${TripShareFormat.safeExtension(it.displayName)}" }, null)

    @Test fun `codifica y descodifica sin perder nada`() {
        val p = pkg()
        val back = (TripShareFormat.decode(TripShareFormat.encode(p)) as ShareDecode.Ok).pkg
        assertEquals(p.tripValues, back.tripValues)
        assertEquals(p.items.map { it.type to it.values }, back.items.map { it.type to it.values })
        assertEquals(listOf("files/1.pdf"), back.items.first { it.type == ModuleType.FLIGHT }.files.map { it.zipPath })
        assertEquals(listOf("files/2.jpg"), back.tripFiles.map { it.zipPath })
        assertEquals("Roma", back.tripValues["name"]); assertFalse(back.cancelled)
        assertEquals(6, back.items.size)   // vuelo, hotel, actividad, transporte, restaurante, documento
    }

    @Test fun `la copia reconstruye entidades iguales (vuelo y viaje)`() {
        val back = (TripShareFormat.decode(TripShareFormat.encode(pkg())) as ShareDecode.Ok).pkg
        val fv = back.items.first { it.type == ModuleType.FLIGHT }.values
        val rebuilt = FlightCodec.build(7, 0, fv)
        assertEquals(Fixtures.flight.copy(id = 0, tripId = 7), rebuilt)
    }

    @Test fun `rechaza paquetes ajenos, nuevos o con rutas peligrosas`() {
        assertEquals(ShareDecode.Reason.NOT_JSON, (TripShareFormat.decode("zip") as ShareDecode.Invalid).reason)
        assertEquals(ShareDecode.Reason.WRONG_FORMAT, (TripShareFormat.decode("{\"format\":\"otro\"}") as ShareDecode.Invalid).reason)
        val newer = TripShareFormat.encode(pkg()).replace("\"version\":1", "\"version\":99")
        assertEquals(ShareDecode.Reason.TOO_NEW, (TripShareFormat.decode(newer) as ShareDecode.Invalid).reason)
        val evil = TripShareFormat.encode(pkg()).replace("files/1.pdf", "../../etc/passwd")
        assertEquals(ShareDecode.Reason.MALFORMED, (TripShareFormat.decode(evil) as ShareDecode.Invalid).reason)
        assertFalse(TripShareFormat.isSafeZipPath("files/../x")); assertFalse(TripShareFormat.isSafeZipPath("/files/1"))
        assertTrue(TripShareFormat.isSafeZipPath("files/12.pdf")); assertTrue(TripShareFormat.isSafeZipPath("files/3"))
    }

    @Test fun `extension segura`() {
        assertEquals(".pdf", TripShareFormat.safeExtension("a.PDF")); assertEquals("", TripShareFormat.safeExtension("noext"))
        assertEquals("", TripShareFormat.safeExtension("x.tar/../gz"))
        assertNotNull(TripShareFormat.FORMAT)
    }
}

class FlightOptionalArrivalTest {
    private val base = mapOf(
        "airline" to "Iberia", "flightNumber" to "IB3250", "departureAirport" to "Madrid", "arrivalAirport" to "Roma",
        "depDate" to "2027-06-12", "depTime" to "11:45", "depZone" to "Europe/Madrid", "arrZone" to "Europe/Rome",
        "currency" to "EUR",
    )

    @Test fun `sin llegada se guarda como desconocida y no inventa duracion`() {
        val f = FlightCodec.build(1, 0, base)
        assertFalse(FlightCodec.hasArrival(f)); assertEquals(0L, FlightCodec.durationMinutes(f))
        val v = FlightCodec.values(f)
        assertEquals("", v["arrDate"]); assertEquals("", v["arrTime"]); assertEquals("11:45", v["depTime"])
    }

    @Test fun `con llegada la duracion sale sola`() {
        val v = base + mapOf("arrDate" to "2027-06-12", "arrTime" to "14:15")
        val f = FlightCodec.build(1, 0, v)
        assertTrue(FlightCodec.hasArrival(f)); assertEquals(150L, FlightCodec.durationMinutes(f))
    }

    @Test fun `fecha y hora de llegada van juntas`() {
        assertEquals(setOf("arrTime"), FlightCodec.cross(base + ("arrDate" to "2027-06-12")).keys)
        assertEquals(setOf("arrDate"), FlightCodec.cross(base + ("arrTime" to "14:15")).keys)
        assertTrue(FlightCodec.cross(base).isEmpty())
        assertEquals(setOf("arrDate"), FlightCodec.cross(base + mapOf("arrDate" to "2027-06-12", "arrTime" to "09:00")).keys)
    }
}

class AirportNamesTest {
    @Test fun `un codigo IATA se muestra con el nombre del aeropuerto`() {
        assertEquals("Madrid-Barajas", Airports.displayName("MAD"))
        assertEquals("Madrid-Barajas", Airports.displayName("mad"))
        assertEquals("Roma Fiumicino", Airports.displayName("FCO"))
    }

    @Test fun `un nombre escrito a mano se respeta y se quita el codigo entre parentesis`() {
        assertEquals("Mi aeropuerto", Airports.displayName("Mi aeropuerto"))
        assertEquals("Madrid-Barajas", Airports.displayName("Madrid-Barajas (MAD)"))
    }

    @Test fun `al elegir un aeropuerto conocido se deduce su zona horaria`() {
        assertEquals("Europe/Madrid", Airports.zoneFor("Madrid-Barajas"))
        assertEquals("Europe/Madrid", Airports.zoneFor("MAD"))
        assertNull(Airports.zoneFor("Las Palmas"))
    }
}

package com.travelindex.app.domain.forms

import com.travelindex.app.domain.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CodecsTest {
    @Test fun `vuelo ida y vuelta de valores conserva todo`() {
        val f = Fixtures.flight
        assertEquals(f, FlightCodec.build(f.tripId, f.id, FlightCodec.values(f)))
    }

    @Test fun `alojamiento actividad transporte restaurante documento y gasto son reversibles`() {
        val h = Fixtures.hotel; assertEquals(h, LodgingCodec.build(h.tripId, h.id, LodgingCodec.values(h)))
        val a = Fixtures.activity; assertEquals(a, ActivityCodec.build(a.tripId, a.id, ActivityCodec.values(a)))
        val t = Fixtures.transport; assertEquals(t, TransportCodec.build(t.tripId, t.id, TransportCodec.values(t)))
        val r = Fixtures.restaurant; assertEquals(r, RestaurantCodec.build(r.tripId, r.id, RestaurantCodec.values(r)))
        val d = Fixtures.doc; assertEquals(d, TravelDocCodec.build(d.tripId, d.id, TravelDocCodec.values(d)))
        val e = com.travelindex.app.data.db.ExpenseEntity(
            id = 3, tripId = 1, concept = "Taxi", category = "TRANSPORT", amountMinor = 2_550, currency = "EUR",
            date = LocalDate.of(2027, 6, 12), paymentMethod = "CARD", notes = "n",
        )
        assertEquals(e, ExpenseCodec.build(1, 3, ExpenseCodec.values(e)))
        val dest = com.travelindex.app.data.db.DestinationEntity(
            id = 2, tripId = 1, city = "Florencia", country = "Italia", startDate = LocalDate.of(2027, 6, 15),
            endDate = LocalDate.of(2027, 6, 17), timeZoneId = "Europe/Rome", position = 1,
        )
        assertEquals(dest, DestinationCodec.build(1, 2, DestinationCodec.values(dest), 1))
    }

    @Test fun `precio con coma europea se guarda en centimos`() {
        val v = FlightCodec.values(Fixtures.flight) + mapOf("price" to "1.234,50", "currency" to "EUR")
        assertEquals(123_450L, FlightCodec.build(1, 0, v).priceMinor)
    }

    @Test fun `precio vacio es null`() {
        val v = FlightCodec.values(Fixtures.flight) + mapOf("price" to "")
        assertNull(FlightCodec.build(1, 0, v).priceMinor)
    }

    @Test fun `vuelo con llegada anterior a salida da error y duracion real respeta zonas`() {
        val v = FlightCodec.values(Fixtures.flight)
        assertTrue(FlightCodec.cross(v).isEmpty())
        // Madrid 11:45 → Roma 14:15 son 2h30 de vuelo? No: ambas zonas son UTC+2 en junio → 2h30.
        assertEquals(150L, FlightCodec.durationMinutes(Fixtures.flight))
        val bad = v + mapOf("arrTime" to "09:00")
        assertEquals(FieldError.END_BEFORE_START, FlightCodec.cross(bad)["arrDate"])
    }

    @Test fun `vuelo transatlantico calcula bien la duracion con zonas distintas`() {
        // Madrid 11:00 (UTC+2) → Nueva York 13:00 (UTC-4): salida 09:00Z, llegada 17:00Z = 8h
        val f = Fixtures.flight.copy(
            departureEpochMs = Fixtures.ms("Europe/Madrid", 2027, 6, 12, 11, 0), arrivalZoneId = "America/New_York",
            arrivalEpochMs = Fixtures.ms("America/New_York", 2027, 6, 12, 13, 0),
        )
        assertEquals(480L, FlightCodec.durationMinutes(f))
        assertTrue(FlightCodec.cross(FlightCodec.values(f)).isEmpty())
    }

    @Test fun `reglas cruzadas de fechas`() {
        val h = LodgingCodec.values(Fixtures.hotel) + mapOf("checkOutDate" to "2027-06-01")
        assertEquals(FieldError.END_BEFORE_START, LodgingCodec.cross(h)["checkOutDate"])
        val a = ActivityCodec.values(Fixtures.activity) + mapOf("endTime" to "09:00")
        assertEquals(FieldError.END_BEFORE_START, ActivityCodec.cross(a)["endTime"])
        assertEquals(150L, ActivityCodec.durationMinutes(Fixtures.activity))
        val t = TransportCodec.values(Fixtures.transport) + mapOf("endDate" to "2027-06-10")
        assertEquals(FieldError.END_BEFORE_START, TransportCodec.cross(t)["endDate"])
        val d = TravelDocCodec.values(Fixtures.doc) + mapOf("expiryDate" to "2019-01-01")
        assertEquals(FieldError.END_BEFORE_START, TravelDocCodec.cross(d)["expiryDate"])
    }

    @Test fun `ModuleItems expone los modulos con reservas del bundle`() {
        val b = Fixtures.bundle()
        com.travelindex.app.domain.ModuleType.entries.forEach { m ->
            val n = ModuleItems.of(m, b).size
            if (m != com.travelindex.app.domain.ModuleType.EXPENSE && m != com.travelindex.app.domain.ModuleType.DESTINATION) {
                assertEquals("modulo $m", 1, n)
            }
        }
    }
}

class FormValidatorTest {
    private val fields = listOf(
        FieldDef("name", 1, FieldType.TEXT, required = true),
        FieldDef("date", 2, FieldType.DATE, required = true),
        FieldDef("time", 3, FieldType.TIME),
        FieldDef("n", 4, FieldType.INT),
        FieldDef("price", 5, FieldType.MONEY),
        FieldDef("currency", 6, FieldType.CURRENCY),
        FieldDef("zone", 7, FieldType.ZONE),
        FieldDef("kind", 8, FieldType.CHOICE, choices = listOf(Choice("A", 1), Choice("B", 2))),
    )
    private val ok = mapOf("name" to "x", "date" to "2027-06-12", "currency" to "EUR")

    @Test fun `valores correctos no dan errores`() {
        assertTrue(FormValidator.validate(fields, ok + mapOf("time" to "09:30", "n" to "2", "price" to "10,5", "zone" to "Europe/Rome", "kind" to "A")).isEmpty())
    }

    @Test fun `obligatorios y formatos invalidos`() {
        val e = FormValidator.validate(fields, mapOf("name" to " ", "date" to "12/06/2027", "time" to "25:99", "n" to "-1",
            "price" to "abc", "currency" to "EUR", "zone" to "Mars/Base", "kind" to "Z"))
        assertEquals(FieldError.REQUIRED, e["name"]); assertEquals(FieldError.INVALID_DATE, e["date"])
        assertEquals(FieldError.INVALID_TIME, e["time"]); assertEquals(FieldError.INVALID_NUMBER, e["n"])
        assertEquals(FieldError.INVALID_MONEY, e["price"]); assertEquals(FieldError.INVALID_ZONE, e["zone"])
        assertEquals(FieldError.REQUIRED, e["kind"])
    }

    @Test fun `moneda invalida se marca en el campo de moneda`() {
        val e = FormValidator.validate(fields, ok + mapOf("currency" to "XXQ9", "price" to "10"))
        assertEquals(FieldError.INVALID_CURRENCY, e["currency"]); assertNull(e["price"])
    }

    @Test fun `FormDefaults usa fechas y moneda del viaje`() {
        val d = FormDefaults.forNew(com.travelindex.app.domain.ModuleType.LODGING, Fixtures.bundle(), LocalDate.of(2027, 1, 1))
        assertEquals("2027-06-12", d["checkInDate"]); assertEquals("2027-06-18", d["checkOutDate"]); assertEquals("EUR", d["currency"])
        val e = FormDefaults.forNew(com.travelindex.app.domain.ModuleType.EXPENSE, Fixtures.bundle(), LocalDate.of(2027, 1, 1))
        assertEquals("2027-06-12", e["date"])   // hoy queda fuera del viaje → se ajusta al inicio
    }
}

package com.travelindex.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class SectionCostsTest {
    private val train = Fixtures.transport.copy(id = 10, type = "TRAIN", priceMinor = 5_000)
    private val train2 = Fixtures.transport.copy(id = 11, type = "TRAIN", priceMinor = 2_550)
    private val bus = Fixtures.transport.copy(id = 12, type = "BUS", priceMinor = 900)
    private val taxi = Fixtures.transport.copy(id = 13, type = "TAXI", priceMinor = 1_500)

    private fun bundle(vararg t: com.travelindex.app.data.db.TransportEntity) =
        Fixtures.bundle().copy(transports = t.toList())

    @Test fun `dos vuelos con precio se suman`() {
        val b = Fixtures.bundle().copy(flights = listOf(Fixtures.flight, Fixtures.flight.copy(id = 2, priceMinor = 10_010)))
        assertEquals(mapOf("EUR" to 29_000L), SectionCosts.of(b, ModuleType.FLIGHT))
        assertEquals(mapOf("EUR" to 29_000L), SectionCosts.of(b, TransportSection.FLIGHTS))
    }

    @Test fun `un elemento sin precio no cuenta y sin ningun precio no sale nada`() {
        val half = Fixtures.bundle().copy(flights = listOf(Fixtures.flight, Fixtures.flight.copy(id = 2, priceMinor = null)))
        assertEquals(mapOf("EUR" to 18_990L), SectionCosts.of(half, ModuleType.FLIGHT))
        val none = Fixtures.bundle().copy(flights = listOf(Fixtures.flight.copy(priceMinor = null)))
        assertTrue(SectionCosts.of(none, ModuleType.FLIGHT).isEmpty())
        assertEquals("", SectionCosts.format(SectionCosts.of(none, ModuleType.FLIGHT)))
        // La actividad del ejemplo no tiene precio: su apartado queda vacío.
        assertTrue(SectionCosts.of(Fixtures.bundle(), ModuleType.ACTIVITY).isEmpty())
    }

    @Test fun `monedas distintas no se suman entre si`() {
        val b = Fixtures.bundle().copy(lodgings = listOf(Fixtures.hotel, Fixtures.hotel.copy(id = 2, priceMinor = 10_000, currency = "USD")))
        assertEquals(mapOf("EUR" to 45_000L, "USD" to 10_000L), SectionCosts.of(b, ModuleType.LODGING))
    }

    @Test fun `cada apartado de transporte suma solo lo suyo`() {
        val b = bundle(train, train2, bus, taxi, Fixtures.transport)   // + coche de alquiler del ejemplo (120 €)
        assertEquals(mapOf("EUR" to 7_550L), SectionCosts.of(b, TransportSection.TRAINS))
        assertEquals(mapOf("EUR" to 900L), SectionCosts.of(b, TransportSection.BUSES))
        assertTrue(SectionCosts.of(b, TransportSection.SHIPS).isEmpty())
        assertEquals(mapOf("EUR" to 12_000L), SectionCosts.of(b, TransportSection.CARS))
        assertEquals(mapOf("EUR" to 1_500L), SectionCosts.of(b, TransportSection.TRANSPORT_OTHER))
        assertEquals(mapOf("EUR" to 7_550L + 900 + 1_500 + 12_000), SectionCosts.of(b, TransportSection.TRANSPORT_ALL))
    }

    @Test fun `los modulos sin precio devuelven vacio`() {
        assertTrue(SectionCosts.of(Fixtures.bundle(), ModuleType.TRAVEL_DOC).isEmpty())
    }

    @Test fun `restaurantes y alojamiento salen del ejemplo`() {
        val b = Fixtures.bundle()
        assertEquals(mapOf("EUR" to 6_000L), SectionCosts.of(b, ModuleType.RESTAURANT))
        assertEquals(mapOf("EUR" to 45_000L), SectionCosts.of(b, ModuleType.LODGING))
    }

    @Test fun `el texto lleva el simbolo y une varias monedas`() {
        val saved = Locale.getDefault()
        try {
            Locale.setDefault(Locale.US)
            assertEquals("€45,000.00", SectionCosts.format(mapOf("EUR" to 4_500_000L)))
            assertEquals("€10.00 · \$20.00", SectionCosts.format(mapOf("EUR" to 1_000L, "USD" to 2_000L)))
        } finally {
            Locale.setDefault(saved)
        }
    }
}

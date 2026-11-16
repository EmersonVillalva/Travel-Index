package com.travelindex.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchTest {
    private val bundles = listOf(Fixtures.bundle())

    @Test
    fun `ignora acentos y mayusculas`() {
        assertEquals("edimburgo", TripSearch.normalize("EDIMBÚRGO "))
        val hits = TripSearch.search("roma", bundles)
        assertTrue(hits.any { it.kind == SearchKind.TRIP })
    }

    @Test
    fun `menos de dos letras no busca`() {
        assertTrue(TripSearch.search("r", bundles).isEmpty())
        assertTrue(TripSearch.search("  ", bundles).isEmpty())
    }

    @Test
    fun `encuentra un vuelo por aerolinea o numero`() {
        val byAirline = TripSearch.search(Fixtures.flight.airline, bundles)
        assertTrue(byAirline.any { it.kind == SearchKind.ITEM && it.route == "FLIGHT" && it.itemId == Fixtures.flight.id })
        val byNumber = TripSearch.search(Fixtures.flight.flightNumber, bundles)
        assertTrue(byNumber.any { it.route == "FLIGHT" })
    }

    @Test
    fun `encuentra un transporte y navega a su apartado`() {
        val hit = TripSearch.search(Fixtures.transport.company, bundles).first { it.kind == SearchKind.ITEM && it.itemId == Fixtures.transport.id }
        assertEquals(TransportSection.forTransport(Fixtures.transport.type).routeName, hit.route)
    }

    @Test
    fun `encuentra un archivo con su mime para decidir el visor`() {
        val att = Fixtures.bundle().attachments.first()
        val hit = TripSearch.search(att.displayName.substringBefore('.'), bundles).first { it.kind == SearchKind.FILE }
        assertEquals(att.id, hit.itemId)
        assertEquals(att.mimeType, hit.mime)
    }

    @Test
    fun `sin coincidencias, lista vacia`() {
        assertTrue(TripSearch.search("zzzzzz", bundles).isEmpty())
    }
}

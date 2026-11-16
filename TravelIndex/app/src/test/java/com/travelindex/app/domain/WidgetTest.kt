package com.travelindex.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class WidgetTest {
    private val zone = ZoneId.of("Europe/Madrid")

    @Test
    fun `elige el siguiente evento futuro mas cercano`() {
        val now = Fixtures.ms("Europe/Madrid", 2027, 6, 12, 12, 0)   // el vuelo de las 11:45 ya salió
        val next = WidgetPlanner.next(listOf(Fixtures.bundle()), now, zone)!!
        assertEquals("Roma", next.tripName)
        // Lo siguiente del día 12 tras el vuelo es el check-in del hotel (16:00).
        assertEquals("Hotel Roma", next.title)
        assertTrue(next.timeKnown)
    }

    @Test
    fun `sin eventos futuros, el arranque del proximo viaje`() {
        val b = TripBundle(Fixtures.trip)   // viaje sin reservas
        val now = Fixtures.ms("Europe/Madrid", 2027, 6, 1, 0, 0)
        val next = WidgetPlanner.next(listOf(b), now, zone)!!
        assertTrue(next.isTripStart)
        assertEquals("Roma", next.tripName)
    }

    @Test
    fun `los viajes cancelados no pintan nada`() {
        val b = Fixtures.bundle().copy(trip = Fixtures.trip.copy(cancelled = true))
        val now = Fixtures.ms("Europe/Madrid", 2027, 6, 1, 0, 0)
        assertNull(WidgetPlanner.next(listOf(b), now, zone))
    }

    @Test
    fun `todo pasado, nada que ensenar`() {
        val now = Fixtures.ms("Europe/Madrid", 2028, 1, 1, 0, 0)
        assertNull(WidgetPlanner.next(listOf(Fixtures.bundle()), now, zone))
    }
}

package com.travelindex.app.domain

import com.travelindex.app.domain.forms.FormDefaults
import com.travelindex.app.domain.forms.TransportCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class TripSectionsTest {
    private val none = emptyList<String>()

    @Test fun `sin medios elegidos y sin nada guardado no sale ningun apartado vacio`() {
        // El panel pone entonces una sola tarjeta común de transporte.
        assertEquals(emptyList<TransportSection>(), TransportSections.shown(emptyList(), none, 0))
        // Pero se sigue pudiendo añadir vuelos y transporte.
        assertEquals(listOf(TransportSection.FLIGHTS, TransportSection.TRANSPORT_ALL), TransportSections.offered(emptyList()))
    }

    @Test fun `sin medios elegidos solo salen los apartados que ya tienen algo`() {
        assertEquals(listOf(TransportSection.FLIGHTS), TransportSections.shown(emptyList(), none, 2))
        assertEquals(listOf(TransportSection.TRANSPORT_ALL), TransportSections.shown(emptyList(), listOf("TRAIN"), 0))
        assertEquals(listOf(TransportSection.TRANSPORT_ALL), TransportSections.shown(emptyList(), listOf("TAXI", "CAR_RENTAL"), 0))
        assertEquals(
            listOf(TransportSection.FLIGHTS, TransportSection.TRANSPORT_ALL),
            TransportSections.shown(emptyList(), listOf("BUS"), 1),
        )
    }

    @Test fun `la tarjeta comun ofrece cada medio y otro transporte al final`() {
        assertEquals(
            listOf(
                TransportSection.FLIGHTS, TransportSection.TRAINS, TransportSection.BUSES, TransportSection.SHIPS,
                TransportSection.CARS, TransportSection.TRANSPORT_ALL,
            ),
            TransportSections.addable,
        )
        assertFalse(TransportSection.TRANSPORT_OTHER in TransportSections.addable)
    }

    @Test fun `si solo eliges tren no sale vuelos`() {
        val shown = TransportSections.shown(listOf(TravelMode.TRAIN), none, 0)
        assertEquals(listOf(TransportSection.TRAINS), shown)
        assertFalse(TransportSection.FLIGHTS in TransportSections.offered(listOf(TravelMode.TRAIN)))
        assertEquals(listOf(TransportSection.TRAINS), TransportSections.offered(listOf(TravelMode.TRAIN)))
    }

    @Test fun `con dos medios salen los dos`() {
        assertEquals(
            listOf(TransportSection.FLIGHTS, TransportSection.TRAINS),
            TransportSections.shown(listOf(TravelMode.TRAIN, TravelMode.PLANE), none, 0),
        )
        assertEquals(
            listOf(TransportSection.FLIGHTS, TransportSection.TRAINS),
            TransportSections.offered(listOf(TravelMode.TRAIN, TravelMode.PLANE)),
        )
    }

    @Test fun `con todos los medios salen los cinco en orden`() {
        val all = TravelMode.entries
        assertEquals(
            listOf(TransportSection.FLIGHTS, TransportSection.TRAINS, TransportSection.BUSES, TransportSection.SHIPS, TransportSection.CARS),
            TransportSections.offered(all),
        )
    }

    @Test fun `al desmarcar un medio su apartado desaparece aunque tenga reservas`() {
        // Viaje que ahora solo dice tren, con un autobús guardado: el autobús ya no se ve ni se ofrece.
        val types = listOf("BUS", "TRAIN")
        assertEquals(listOf(TransportSection.TRAINS), TransportSections.shown(listOf(TravelMode.TRAIN), types, 0))
        assertEquals(listOf(TransportSection.TRAINS), TransportSections.offered(listOf(TravelMode.TRAIN)))
        // Al volver a marcarlo, vuelve el apartado con su reserva.
        assertEquals(
            listOf(TransportSection.TRAINS, TransportSection.BUSES),
            TransportSections.shown(listOf(TravelMode.TRAIN, TravelMode.BUS), types, 0),
        )
        assertEquals(1, TransportSections.count(TransportSection.BUSES, 0, types))
    }

    @Test fun `un medio marcado sin reservas sigue saliendo`() {
        assertEquals(listOf(TransportSection.BUSES), TransportSections.shown(listOf(TravelMode.BUS), none, 0))
    }

    @Test fun `un taxi guardado sigue siendo alcanzable en un viaje con medios`() {
        val shown = TransportSections.shown(listOf(TravelMode.TRAIN), listOf("TAXI", "TRAIN"), 0)
        assertEquals(listOf(TransportSection.TRAINS, TransportSection.TRANSPORT_OTHER), shown)
    }

    @Test fun `los contadores separan cada medio`() {
        val types = listOf("TRAIN", "TRAIN", "BUS", "TAXI")
        assertEquals(2, TransportSections.count(TransportSection.TRAINS, 5, types))
        assertEquals(1, TransportSections.count(TransportSection.BUSES, 5, types))
        assertEquals(0, TransportSections.count(TransportSection.SHIPS, 5, types))
        assertEquals(5, TransportSections.count(TransportSection.FLIGHTS, 5, types))
        assertEquals(1, TransportSections.count(TransportSection.TRANSPORT_OTHER, 5, types))
        assertEquals(4, TransportSections.count(TransportSection.TRANSPORT_ALL, 5, types))
    }

    @Test fun `cada apartado solo reconoce sus elementos`() {
        assertTrue(TransportSection.TRAINS.matches("TRAIN")); assertFalse(TransportSection.TRAINS.matches("BUS"))
        assertTrue(TransportSection.SHIPS.matches("FERRY")); assertTrue(TransportSection.CARS.matches("CAR_RENTAL"))
        assertTrue(TransportSection.TRANSPORT_ALL.matches("TAXI")); assertTrue(TransportSection.TRANSPORT_ALL.matches("TRAIN"))
        assertTrue(TransportSection.TRANSPORT_OTHER.matches("TAXI")); assertFalse(TransportSection.TRANSPORT_OTHER.matches("TRAIN"))
    }

    @Test fun `cada medio del viaje tiene su apartado`() {
        val modes = TransportSection.entries.mapNotNull { it.mode }
        assertEquals(TravelMode.entries.toSet(), modes.toSet())
        assertEquals(modes.size, modes.toSet().size)   // ninguno repetido
    }
}

class ModuleTargetTest {
    @Test fun `la ruta indica modulo y apartado`() {
        assertEquals(ModuleTarget(ModuleType.TRANSPORT, TransportSection.TRAINS), ModuleTarget.parse("TRAIN"))
        assertEquals(ModuleTarget(ModuleType.TRANSPORT, TransportSection.SHIPS), ModuleTarget.parse("FERRY"))
        assertEquals(ModuleTarget(ModuleType.FLIGHT, TransportSection.FLIGHTS), ModuleTarget.parse("FLIGHT"))
        assertEquals(ModuleTarget(ModuleType.TRANSPORT, TransportSection.TRANSPORT_ALL), ModuleTarget.parse("TRANSPORT"))
        assertEquals(ModuleTarget(ModuleType.LODGING, null), ModuleTarget.parse("LODGING"))
    }

    @Test fun `una ruta desconocida abre vuelos`() {
        assertEquals(ModuleType.FLIGHT, ModuleTarget.parse("nada").type)
        assertEquals(ModuleType.FLIGHT, ModuleTarget.parse(null).type)
    }

    @Test fun `el nombre de la ruta se puede volver a leer`() {
        for (s in TransportSection.entries) assertEquals(s, ModuleTarget.parse(ModuleTarget(s.module, s).routeName).section)
        for (t in ModuleType.entries) assertEquals(t, ModuleTarget.parse(ModuleTarget(t, null).routeName).type)
    }

    @Test fun `los nombres de apartado no pisan los de otros modulos`() {
        val clashes = TransportSection.entries.filter { s ->
            ModuleType.fromName(s.routeName).let { it != null && it != s.module }
        }
        assertTrue("choques: $clashes", clashes.isEmpty())
        assertEquals(TransportSection.entries.size, TransportSection.entries.map { it.routeName }.toSet().size)
    }

    @Test fun `un transporte guardado se abre con los campos de su medio`() {
        assertEquals(TransportSection.TRAINS, TransportSection.forTransport("TRAIN"))
        assertEquals(TransportSection.BUSES, TransportSection.forTransport("BUS"))
        assertEquals(TransportSection.SHIPS, TransportSection.forTransport("FERRY"))
        assertEquals(TransportSection.CARS, TransportSection.forTransport("CAR_RENTAL"))
        assertEquals(TransportSection.TRANSPORT_ALL, TransportSection.forTransport("TAXI"))
        assertEquals(TransportSection.TRANSPORT_ALL, TransportSection.forTransport(null))
    }
}

class TransportSectionFormTest {
    @Test fun `un tren nuevo empieza con tipo tren`() {
        val d = FormDefaults.forNew(ModuleType.TRANSPORT, Fixtures.bundle(), LocalDate.of(2027, 1, 1), transportType = "TRAIN")
        assertEquals("TRAIN", d["type"])
        assertEquals("2027-06-12", d["startDate"])
    }

    @Test fun `sin tipo se mantiene coche de alquiler`() {
        val d = FormDefaults.forNew(ModuleType.TRANSPORT, Fixtures.bundle(), LocalDate.of(2027, 1, 1))
        assertEquals("CAR_RENTAL", d["type"])
    }

    @Test fun `el tipo del apartado se guarda aunque el formulario no lo pregunte`() {
        val values = TransportCodec.values(Fixtures.transport) + mapOf("type" to "FERRY")
        assertEquals("FERRY", TransportCodec.build(1, 1, values).type)
    }

    @Test fun `solo con hora de llegada, la llegada es el mismo dia`() {
        val values = TransportCodec.values(Fixtures.transport.copy(endDate = null, endTime = LocalTime.of(13, 30)))
        val built = TransportCodec.build(1, 1, values)
        assertEquals(Fixtures.transport.startDate, built.endDate)
        assertEquals(LocalTime.of(13, 30), built.endTime)
    }

    @Test fun `sin llegada no se inventa una`() {
        val values = TransportCodec.values(Fixtures.transport.copy(endDate = null, endTime = null))
        val built = TransportCodec.build(1, 1, values)
        assertNull(built.endDate); assertNull(built.endTime)
    }
}

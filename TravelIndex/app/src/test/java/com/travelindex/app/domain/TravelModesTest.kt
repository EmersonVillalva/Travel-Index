package com.travelindex.app.domain

import com.travelindex.app.domain.forms.FieldError
import com.travelindex.app.domain.forms.TripCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TravelModesTest {
    @Test fun `sin seleccion no hay medios`() {
        assertTrue(TravelModes.parse("").isEmpty())
        assertTrue(TravelModes.parse(null).isEmpty())
        assertEquals("", TravelModes.encode(emptyList()))
    }

    @Test fun `se pueden elegir varios y quedan en orden fijo sin repetir`() {
        assertEquals(listOf(TravelMode.PLANE, TravelMode.TRAIN, TravelMode.CAR), TravelModes.parse("CAR,PLANE,TRAIN,PLANE"))
        assertEquals("PLANE,TRAIN,CAR", TravelModes.normalize("CAR,PLANE,TRAIN,PLANE"))
    }

    @Test fun `las claves desconocidas o con espacios y minusculas se toleran`() {
        assertEquals(listOf(TravelMode.SHIP, TravelMode.BUS), TravelModes.parse(" bus , barco, SHIP ,, "))
    }

    @Test fun `marcar y desmarcar`() {
        var sel = ""
        sel = TravelModes.toggle(sel, TravelMode.CAR)
        sel = TravelModes.toggle(sel, TravelMode.PLANE)
        assertEquals("PLANE,CAR", sel)
        sel = TravelModes.toggle(sel, TravelMode.CAR)
        assertEquals("PLANE", sel)
        sel = TravelModes.toggle(sel, TravelMode.PLANE)
        assertEquals("", sel)
    }

    @Test fun `los cinco medios pedidos existen`() {
        assertEquals(
            setOf("PLANE", "SHIP", "TRAIN", "BUS", "CAR"),
            TravelMode.entries.map { it.name }.toSet(),
        )
    }

    @Test fun `el viaje guarda y recupera los medios elegidos`() {
        val t = Fixtures.trip.copy(travelModes = "TRAIN,PLANE")
        val values = TripCodec.values(t)
        assertEquals("PLANE,TRAIN", values["travelModes"])
        val built = TripCodec.build(t.id, values, null, t.createdAt, false)
        assertEquals("PLANE,TRAIN", built.travelModes)
    }

    @Test fun `un viaje antiguo sin la clave se abre sin medios`() {
        val values = TripCodec.values(Fixtures.trip) - "travelModes"
        assertEquals("", TripCodec.build(1, values, null, 0L, false).travelModes)
    }
}

class TripDatesTest {
    private val base = TripCodec.values(Fixtures.trip)   // 12 jun 2027 -> 18 jun 2027

    @Test fun `mover la ida despues de la vuelta arrastra la vuelta`() {
        val v = TripCodec.withValue(base, "startDate", "2027-07-01")
        assertEquals("2027-07-01", v["startDate"])
        assertEquals("2027-07-01", v["endDate"])
        assertTrue(TripCodec.cross(v).isEmpty())
    }

    @Test fun `mover la ida antes de la vuelta no toca la vuelta`() {
        val v = TripCodec.withValue(base, "startDate", "2027-06-14")
        assertEquals("2027-06-18", v["endDate"])
    }

    @Test fun `la ida el mismo dia que la vuelta es valida`() {
        val v = TripCodec.withValue(base, "startDate", "2027-06-18")
        assertEquals("2027-06-18", v["endDate"])
        assertTrue(TripCodec.cross(v).isEmpty())
    }

    @Test fun `sin vuelta elegida no se inventa una`() {
        val v = TripCodec.withValue(base - "endDate", "startDate", "2027-07-01")
        assertEquals(null, v["endDate"])
    }

    @Test fun `cambiar otro campo no modifica las fechas`() {
        val v = TripCodec.withValue(base, "name", "Otro")
        assertEquals(base["startDate"], v["startDate"])
        assertEquals(base["endDate"], v["endDate"])
    }

    @Test fun `ida 1 de diciembre y vuelta 20 de noviembre se rechaza al guardar`() {
        val bad = base + ("startDate" to "2027-12-01") + ("endDate" to "2027-11-20")
        assertEquals(FieldError.END_BEFORE_START, TripCodec.cross(bad)["endDate"])
    }
}

class ShareFileNameTest {
    @Test fun `el archivo compartido ya no es un zip sino un archivo propio`() {
        assertEquals("application/vnd.travelindex.trip", TripShareFormat.MIME)
        assertEquals("travelindex", TripShareFormat.EXTENSION)
        assertEquals("Roma.travelindex", TripShareFormat.fileNameFor("Roma"))
    }

    @Test fun `el nombre queda limpio de acentos, simbolos y espacios`() {
        assertEquals("Mexico_DF_2027.travelindex", TripShareFormat.fileNameFor("México, D.F. 2027!"))
        assertEquals("Ano_nuevo.travelindex", TripShareFormat.fileNameFor("Año nuevo"))
    }

    @Test fun `un nombre vacio o solo con simbolos usa uno por defecto`() {
        assertEquals("viaje.travelindex", TripShareFormat.fileNameFor("???"))
        assertEquals("viaje.travelindex", TripShareFormat.fileNameFor(""))
    }

    @Test fun `el nombre no pasa de 40 caracteres mas la extension`() {
        val n = TripShareFormat.fileNameFor("a".repeat(100))
        assertEquals(40 + ".travelindex".length, n.length)
    }
}

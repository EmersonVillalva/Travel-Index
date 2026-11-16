package com.travelindex.app.domain

import com.travelindex.app.data.db.ActivityEntity
import com.travelindex.app.domain.forms.ActivityCodec
import com.travelindex.app.domain.forms.Durations
import com.travelindex.app.domain.forms.FieldDef
import com.travelindex.app.domain.forms.FieldError
import com.travelindex.app.domain.forms.FieldType
import com.travelindex.app.domain.forms.FormValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class ActivityDurationTest {
    private val base = Fixtures.activity   // 13 jun 2027, 10:00, 150 min

    @Test fun `la hora de fin sale de inicio mas duracion`() {
        assertEquals(LocalDateTime.of(2027, 6, 13, 12, 30), ActivityCodec.end(base))
        assertEquals(LocalDateTime.of(2027, 6, 13, 11, 0), ActivityCodec.end(base.copy(durationMinutes = 60)))
    }

    @Test fun `sin duracion o sin hora de inicio no se inventa una hora de fin`() {
        assertNull(ActivityCodec.end(base.copy(durationMinutes = null)))
        assertNull(ActivityCodec.end(base.copy(startTime = null)))
        assertNull(ActivityCodec.durationMinutes(base.copy(durationMinutes = null)))
    }

    @Test fun `una actividad que pasa de medianoche termina al dia siguiente`() {
        val night = base.copy(startTime = LocalTime.of(22, 30), durationMinutes = 180)
        assertEquals(LocalDateTime.of(2027, 6, 14, 1, 30), ActivityCodec.end(night))
    }

    @Test fun `la duracion es opcional y el formulario la guarda y la recupera`() {
        val values = ActivityCodec.values(base)
        assertEquals("150", values["duration"])
        assertFalse(values.containsKey("endTime"))
        assertEquals(base, ActivityCodec.build(base.tripId, base.id, values))

        val without = ActivityCodec.build(1, 0, values - "duration")
        assertNull(without.durationMinutes)
        assertNull(without.endTime)
        assertEquals("", ActivityCodec.values(without)["duration"])
    }

    @Test fun `no hay regla cruzada entre inicio y duracion`() {
        assertTrue(ActivityCodec.cross(ActivityCodec.values(base)).isEmpty())
        assertTrue(ActivityCodec.cross(ActivityCodec.values(base) + ("startTime" to "")).isEmpty())
    }

    @Test fun `una actividad antigua con hora de fin enseña su duracion y la hora de fin calculada es la misma`() {
        val old = ActivityEntity(
            id = 7, tripId = 1, name = "Coliseo", date = LocalDate.of(2027, 6, 13), startTime = LocalTime.of(10, 0),
            endTime = LocalTime.of(12, 30), timeZoneId = "Europe/Rome",
        )
        assertEquals(150L, ActivityCodec.durationMinutes(old))
        assertEquals(LocalDateTime.of(2027, 6, 13, 12, 30), ActivityCodec.end(old))
        // Al abrirla para editar sale con su duración; al guardarla ya queda con duración y sin hora de fin.
        val values = ActivityCodec.values(old)
        assertEquals("150", values["duration"])
        val saved = ActivityCodec.build(old.tripId, old.id, values)
        assertEquals(150, saved.durationMinutes)
        assertNull(saved.endTime)
        assertEquals(ActivityCodec.end(old), ActivityCodec.end(saved))
    }

    @Test fun `un viaje compartido con la version anterior trae hora de fin y se convierte en duracion`() {
        val legacy = mapOf(
            "name" to "Tour", "date" to "2027-06-13", "startTime" to "09:00", "endTime" to "11:15", "timeZone" to "Europe/Rome",
            "currency" to "EUR", "participants" to "2",
        )
        val a = ActivityCodec.build(1, 0, legacy)
        assertEquals(135, a.durationMinutes)
        assertNull(a.endTime)
    }

    @Test fun `la duracion escrita en el formulario manda sobre la hora de fin antigua`() {
        val v = mapOf("name" to "Tour", "date" to "2027-06-13", "startTime" to "09:00", "endTime" to "11:15", "duration" to "60",
            "timeZone" to "Europe/Rome", "currency" to "EUR")
        assertEquals(60, ActivityCodec.build(1, 0, v).durationMinutes)
    }

    @Test fun `una hora de fin antigua anterior al inicio se ignora`() {
        val v = mapOf("name" to "Tour", "date" to "2027-06-13", "startTime" to "09:00", "endTime" to "08:00",
            "timeZone" to "Europe/Rome", "currency" to "EUR")
        assertNull(ActivityCodec.build(1, 0, v).durationMinutes)
    }
}

class DurationFieldTest {
    private val field = FieldDef("duration", 0, FieldType.DURATION)

    @Test fun `solo vale una cantidad de minutos entre 1 y 7 dias`() {
        assertEquals(150, Durations.parse("150"))
        assertEquals(10_080, Durations.parse("10080"))
        assertNull(Durations.parse("0"))
        assertNull(Durations.parse("-5"))
        assertNull(Durations.parse("10081"))
        assertNull(Durations.parse("dos horas"))
        assertNull(Durations.parse(null))
    }

    @Test fun `horas y minutos se juntan sin pasarse del maximo`() {
        assertEquals(150, Durations.of(2, 30))
        assertEquals(45, Durations.of(0, 45))
        assertEquals(Durations.MAX_MINUTES, Durations.of(500, 0))
        assertEquals(0, Durations.of(-1, -5))
    }

    @Test fun `el campo vacio es valido porque es opcional y uno mal escrito se rechaza`() {
        assertTrue(FormValidator.validate(listOf(field), mapOf("duration" to "")).isEmpty())
        assertTrue(FormValidator.validate(listOf(field), emptyMap()).isEmpty())
        assertTrue(FormValidator.validate(listOf(field), mapOf("duration" to "90")).isEmpty())
        assertEquals(FieldError.INVALID_NUMBER, FormValidator.validate(listOf(field), mapOf("duration" to "0"))["duration"])
        assertEquals(FieldError.INVALID_NUMBER, FormValidator.validate(listOf(field), mapOf("duration" to "x"))["duration"])
    }
}

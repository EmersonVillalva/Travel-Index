package com.travelindex.app.uitest

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.travelindex.app.R
import com.travelindex.app.domain.Fixtures
import com.travelindex.app.domain.FlightConnections
import com.travelindex.app.domain.ModuleType
import com.travelindex.app.domain.forms.FormValidator
import com.travelindex.app.ui.modules.FlightStopsCard
import com.travelindex.app.ui.modules.ModuleSpecs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Escalas dentro del formulario de un vuelo: el control para elegir cuántas y los bloques que salen entre la salida y la llegada. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class FlightStopsUiTest {
    @get:Rule
    val compose = createComposeRule()

    private val app get() = RuntimeEnvironment.getApplication()
    private fun str(id: Int) = app.getString(id)
    private fun plural(id: Int, n: Int) = app.resources.getQuantityString(id, n, n)

    private val spec get() = ModuleSpecs.of(ModuleType.FLIGHT)
    private fun visibleKeys(v: Map<String, String>) = spec.visibleFields(v).map { it.key }

    // ---- El control de escalas ---------------------------------------------------------------------------------------

    @Test
    fun sinEscalasSoloSeVeElInterruptor() {
        compose.setContent { MaterialTheme { FlightStopsCard(stops = 0, savedLegs = 1, onStops = {}) } }
        compose.onNodeWithText(str(R.string.flight_has_stops)).assertIsDisplayed()
        compose.onNodeWithContentDescription(str(R.string.flight_stops_more)).assertDoesNotExist()
        compose.onNodeWithContentDescription(str(R.string.flight_stops_less)).assertDoesNotExist()
    }

    @Test
    fun alActivarElInterruptorSePideUnaEscala() {
        var asked = -1
        compose.setContent { MaterialTheme { FlightStopsCard(stops = 0, savedLegs = 1, onStops = { asked = it }) } }
        compose.onNodeWithText(str(R.string.flight_has_stops)).performClick()
        assertEquals(1, asked)
    }

    @Test
    fun alDesactivarloSeQuitanTodasLasEscalas() {
        var asked = -1
        compose.setContent { MaterialTheme { FlightStopsCard(stops = 3, savedLegs = 1, onStops = { asked = it }) } }
        compose.onNodeWithText(str(R.string.flight_has_stops)).performClick()
        assertEquals(0, asked)
    }

    @Test
    fun conEscalasSeVeCuantasSonYSePuedenSubirYBajar() {
        var asked = -1
        compose.setContent { MaterialTheme { FlightStopsCard(stops = 2, savedLegs = 1, onStops = { asked = it }) } }
        compose.onNodeWithText(plural(R.plurals.flight_stops_count, 2)).assertIsDisplayed()
        compose.onNodeWithContentDescription(str(R.string.flight_stops_more)).performClick()
        assertEquals(3, asked)
        compose.onNodeWithContentDescription(str(R.string.flight_stops_less)).performClick()
        assertEquals(1, asked)
    }

    @Test
    fun noSePuedenPedirMasEscalasDeLasQueHayLugar() {
        compose.setContent { MaterialTheme { FlightStopsCard(stops = FlightConnections.MAX_STOPS, savedLegs = 1, onStops = {}) } }
        compose.onNodeWithContentDescription(str(R.string.flight_stops_more)).assertIsNotEnabled()
        compose.onNodeWithContentDescription(str(R.string.flight_stops_less)).assertIsEnabled()
    }

    @Test
    fun siSeQuitanEscalasYaGuardadasSeAvisaDeLosVuelosQueSeBorran() {
        compose.setContent { MaterialTheme { FlightStopsCard(stops = 1, savedLegs = 3, onStops = {}) } }
        compose.onNodeWithText(plural(R.plurals.flight_legs_removed, 1)).assertIsDisplayed()
    }

    @Test
    fun sinVuelosQueBorrarNoHayAviso() {
        compose.setContent { MaterialTheme { FlightStopsCard(stops = 1, savedLegs = 2, onStops = {}) } }
        compose.onNodeWithText(plural(R.plurals.flight_legs_removed, 1)).assertDoesNotExist()
    }

    // ---- Los bloques de cada escala en el formulario -----------------------------------------------------------------

    @Test
    fun sinEscalasNoSaleNingunBloque() {
        assertTrue(visibleKeys(emptyMap()).none { FlightConnections.parseStopKey(it) != null })
    }

    @Test
    fun soloSeVenTantosBloquesComoEscalasSePidan() {
        val two = visibleKeys(mapOf("stops" to "2"))
        assertTrue("s1_airport" in two && "s1_flightNumber" in two && "s2_airline" in two && "s2_depTime" in two)
        assertFalse("s3_airport" in two)
        // Ver las escalas no esconde nada del resto del formulario.
        assertTrue(listOf("airline", "departureAirport", "arrivalAirport", "price").all { it in two })
    }

    @Test
    fun laZonaDeLaEscalaSoloSePideSiElAeropuertoNoSeConoce() {
        assertFalse("s1_zone" in visibleKeys(mapOf("stops" to "1", "s1_airport" to "MAN")))
        assertFalse("s1_zone" in visibleKeys(mapOf("stops" to "1")))
        assertTrue("s1_zone" in visibleKeys(mapOf("stops" to "1", "s1_airport" to "Aeródromo de Villa Nada")))
    }

    @Test
    fun losBloquesDeEscalaVanEntreLaSalidaYLaLlegadaEnOrden() {
        val titles = spec.sections.map { it.titleRes }
        val stops = listOf(
            R.string.sec_stop_1, R.string.sec_stop_2, R.string.sec_stop_3, R.string.sec_stop_4, R.string.sec_stop_5, R.string.sec_stop_6,
        )
        assertEquals(FlightConnections.MAX_STOPS, stops.size)
        val departure = titles.indexOf(R.string.sec_departure)
        val arrival = titles.indexOf(R.string.sec_arrival)
        assertEquals(stops.indices.map { departure + 1 + it }, stops.map { titles.indexOf(it) })
        assertTrue(arrival > titles.indexOf(R.string.sec_stop_6))
    }

    @Test
    fun cadaEscalaPideSusCamposYNingunOtro() {
        for (i in 1..FlightConnections.MAX_STOPS) {
            val keys = FlightConnections.STOP_FIELDS.map { FlightConnections.stopKey(i, it) }
            keys.forEach { assertNotNull("falta $it", spec.field(it)) }
            val section = spec.sections.first { it.formKeys.firstOrNull() == FlightConnections.stopKey(i, "airport") }
            assertEquals(keys.toSet(), section.formKeys.toSet())
            // El detalle y el PDF no enseñan las escalas como secciones: cada tramo es su propio vuelo.
            assertTrue(section.keys.isEmpty())
        }
        assertEquals(FlightConnections.MAX_STOPS * FlightConnections.STOP_FIELDS.size, spec.fields.count { FlightConnections.parseStopKey(it.key) != null })
    }

    @Test
    fun alAbrirUnViajeConEscalasTodoLoQueSeRellenaTieneCampoEnElFormulario() {
        val h = 3_600_000L
        val legs = (0..2).map { i ->
            Fixtures.flight.copy(
                id = 10L + i, connectionId = "C1",
                departureEpochMs = Fixtures.flight.departureEpochMs + i * 5 * h, arrivalEpochMs = Fixtures.flight.departureEpochMs + i * 5 * h + 2 * h,
            )
        }
        val known = spec.fields.map { it.key }.toSet() + setOf("connectionId", "stops")
        val values = FlightConnections.formValues(legs)
        assertTrue("sobran claves: ${values.keys - known}", values.keys.all { it in known })
        assertEquals("2", values["stops"])
    }

    @Test
    fun alGuardarSoloSeExigenLasEscalasPedidas() {
        val direct = mapOf(
            "airline" to "IB", "flightNumber" to "3080", "departureAirport" to "MAD", "depDate" to "2027-03-01", "depTime" to "08:00",
            "arrivalAirport" to "PEK", "currency" to "EUR",
        )
        fun errors(v: Map<String, String>) = FormValidator.validate(spec.visibleFields(v), v)
        assertTrue(errors(direct).isEmpty())
        // Con una escala pedida y sin rellenar, faltan sus datos obligatorios (la hora de llegada y el número de vuelo son opcionales).
        assertEquals(
            setOf("s1_airport", "s1_depDate", "s1_depTime", "s1_airline"),
            errors(direct + ("stops" to "1")).keys,
        )
        // Con una sola pedida, las demás escritas o no escritas no cuentan.
        assertEquals(4, errors(direct + ("stops" to "1") + ("s2_airline" to "")).size)
    }

    @Test
    fun elNumeroDeVueloNoEsObligatorioNiEnElVueloNiEnLasEscalas() {
        val direct = mapOf(
            "airline" to "IB", "departureAirport" to "MAD", "depDate" to "2027-03-01", "depTime" to "08:00",
            "arrivalAirport" to "PEK", "currency" to "EUR",
        )
        fun errors(v: Map<String, String>) = FormValidator.validate(spec.visibleFields(v), v)
        assertTrue(errors(direct).isEmpty())
        val withStop = direct + mapOf("stops" to "1", "s1_airport" to "MAN", "s1_depDate" to "2027-03-01", "s1_depTime" to "13:40", "s1_airline" to "CA")
        assertTrue(errors(withStop).isEmpty())
    }
}

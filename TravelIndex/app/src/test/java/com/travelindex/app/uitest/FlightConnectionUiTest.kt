package com.travelindex.app.uitest

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.travelindex.app.R
import com.travelindex.app.domain.Fixtures
import com.travelindex.app.domain.FlightConnections
import com.travelindex.app.domain.forms.Durations
import com.travelindex.app.domain.forms.FlightCodec
import com.travelindex.app.ui.modules.FlightConnectionCard
import com.travelindex.app.ui.modules.FlightItemCard
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Vuelo con escalas en la interfaz: Madrid → Mánchester → Pekín, dos vuelos enlazados por una conexión. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class FlightConnectionUiTest {
    @get:Rule
    val compose = createComposeRule()

    private fun str(id: Int, vararg args: Any) = RuntimeEnvironment.getApplication().getString(id, *args)

    private val h = 3_600_000L
    private val t0 = Fixtures.ms("UTC", 2027, 3, 1, 8, 0)
    private val madMan = Fixtures.flight.copy(
        id = 1, departureAirport = "MAD", arrivalAirport = "MAN", connectionId = "C1",
        departureEpochMs = t0, departureZoneId = "UTC", arrivalEpochMs = t0 + 2 * h + 30 * 60_000, arrivalZoneId = "UTC",
    )
    private val manPek = Fixtures.flight.copy(
        id = 2, airline = "CA", flightNumber = "902", departureAirport = "MAN", arrivalAirport = "PEK", connectionId = "C1",
        departureEpochMs = t0 + 5 * h + 40 * 60_000, departureZoneId = "UTC", arrivalEpochMs = t0 + 15 * h, arrivalZoneId = "UTC",
    )
    private val legs = listOf(madMan, manPek)
    private val layover get() = str(R.string.flight_chip_layover, "Mánchester", Durations.text(190L))

    @Test
    fun laTarjetaDelSegundoTramoDiceQueEsElTramo2yLaEscala() {
        val info = FlightConnections.infoFor(manPek, legs)!!
        compose.setContent { MaterialTheme { FlightItemCard(FlightCodec.values(manPek), {}, leg = info) } }
        compose.onNodeWithText(str(R.string.flight_chip_leg, 2, 2)).assertIsDisplayed()
        compose.onNodeWithText(layover).assertIsDisplayed()
    }

    @Test
    fun laTarjetaDelPrimerTramoNoTieneEscalaPrevia() {
        val info = FlightConnections.infoFor(madMan, legs)!!
        compose.setContent { MaterialTheme { FlightItemCard(FlightCodec.values(madMan), {}, leg = info) } }
        compose.onNodeWithText(str(R.string.flight_chip_leg, 1, 2)).assertIsDisplayed()
        compose.onNodeWithText(layover).assertDoesNotExist()
    }

    @Test
    fun unVueloSueltoNoLlevaEtiquetaDeTramo() {
        compose.setContent { MaterialTheme { FlightItemCard(FlightCodec.values(Fixtures.flight), {}) } }
        compose.onNodeWithText(str(R.string.flight_chip_leg, 1, 1)).assertDoesNotExist()
    }

    @Test
    fun elDetalleMuestraLaRutaCompletaLosTramosYLaEscala() {
        compose.setContent { MaterialTheme { FlightConnectionCard(legs, currentId = 2) } }
        compose.onNodeWithText("Madrid-Barajas → Mánchester → Pekín Capital").assertIsDisplayed()
        compose.onNodeWithText(str(R.string.flight_connection_leg, 1, "IB 3250")).assertIsDisplayed()
        compose.onNodeWithText(str(R.string.flight_connection_leg, 2, "CA 902")).assertIsDisplayed()
        compose.onNodeWithText(layover).assertIsDisplayed()
    }
}

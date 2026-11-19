package com.travelindex.app.uitest

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.travelindex.app.ui.trips.Tile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Las tarjetas de secciones del panel del viaje miden siempre lo mismo, pase lo que pase con su texto. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class DashboardTileTest {
    @get:Rule
    val compose = createComposeRule()

    private val cases: List<Triple<String, String, String>> = listOf(
        Triple("Itinerario", "6 eventos", ""),                                           // sin precio
        Triple("Vuelos", "2 elementos", "80,65 €"),                                      // con precio
        Triple("Visados y documentos de viaje con un nombre larguísimo", "Vacío", ""),   // título largo
        Triple("Vuelos", "2 elementos", "80,65 € · £30.00 · \$50.00 · 12.000 ¥"),        // varias monedas
        Triple("A", "Un subtítulo extraordinariamente largo que no cabe en una línea", "1.234,50 €"),
    )

    @Composable
    private fun Tiles() {
        MaterialTheme {
            Column {
                cases.forEachIndexed { i, (title, subtitle, price) ->
                    Box(Modifier.width(160.dp).testTag("tile$i")) {
                        Tile(if (i % 2 == 0) Icons.Outlined.Timeline else Icons.Outlined.Flight, title, subtitle, {}, price = price)
                    }
                }
            }
        }
    }

    private fun heights(): List<Int> = cases.indices.map { compose.onNodeWithTag("tile$it").fetchSemanticsNode().size.height }

    @Test
    fun todasLasTarjetasMidenLoMismoConOSinPrecioYConTextosLargos() {
        compose.setContent { Tiles() }
        val h = heights()
        assertTrue("las tarjetas deben tener altura: $h", h.all { it > 0 })
        assertEquals("alturas: $h", 1, h.toSet().size)
    }

    @Test
    fun siguenMidiendoLoMismoConLaLetraMuyGrande() {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale = 1.6f)) { Tiles() }
        }
        val h = heights()
        assertTrue("las tarjetas deben tener altura: $h", h.all { it > 0 })
        assertEquals("alturas: $h", 1, h.toSet().size)
    }
}

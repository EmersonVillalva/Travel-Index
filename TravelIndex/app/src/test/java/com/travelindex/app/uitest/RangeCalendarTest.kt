package com.travelindex.app.uitest

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.travelindex.app.R
import com.travelindex.app.domain.Formats
import com.travelindex.app.ui.components.DateRangePickerDialogHost
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.YearMonth

/**
 * Calendario de fechas del viaje (JVM, Robolectric + Compose): flechas de mes, marcar ida y vuelta, salto de año.
 * Los textos se piden a los recursos y al formateador, así que no dependen del idioma del test.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class RangeCalendarTest {
    @get:Rule
    val compose = createComposeRule()

    private val f = Formats()
    private fun str(id: Int) = RuntimeEnvironment.getApplication().getString(id)
    private fun month(y: Int, m: Int) = f.month(YearMonth.of(y, m))
    private fun day(y: Int, m: Int, d: Int) = f.full(LocalDate.of(y, m, d))

    private var picked: Pair<LocalDate, LocalDate>? = null

    private fun open(start: LocalDate?, end: LocalDate?) {
        picked = null
        compose.setContent {
            MaterialTheme { DateRangePickerDialogHost(start, end, onDismiss = {}, onPick = { s, e -> picked = s to e }) }
        }
    }

    @Test
    fun lasFlechasCambianDeMes() {
        open(LocalDate.of(2027, 2, 10), null)
        compose.onNodeWithText(month(2027, 2)).assertIsDisplayed()
        compose.onNodeWithContentDescription(str(R.string.range_next_month)).performClick()
        compose.onNodeWithText(month(2027, 3)).assertIsDisplayed()
        compose.onNodeWithContentDescription(str(R.string.range_prev_month)).performClick()
        compose.onNodeWithContentDescription(str(R.string.range_prev_month)).performClick()
        compose.onNodeWithText(month(2027, 1)).assertIsDisplayed()
    }

    @Test
    fun seMarcanIdaYVueltaYSeAcepta() {
        open(LocalDate.of(2027, 2, 1), LocalDate.of(2027, 2, 1))
        compose.onNodeWithContentDescription(day(2027, 2, 5)).performClick()
        compose.onNodeWithContentDescription(day(2027, 2, 9)).performClick()
        compose.onNodeWithText(str(R.string.action_ok)).performClick()
        assertEquals(LocalDate.of(2027, 2, 5) to LocalDate.of(2027, 2, 9), picked)
    }

    @Test
    fun laVueltaPuedeEstarEnOtroMes() {
        open(LocalDate.of(2027, 2, 1), LocalDate.of(2027, 2, 1))
        compose.onNodeWithContentDescription(day(2027, 2, 26)).performClick()
        compose.onNodeWithContentDescription(str(R.string.range_next_month)).performClick()
        compose.onNodeWithContentDescription(day(2027, 3, 3)).performClick()
        compose.onNodeWithText(str(R.string.action_ok)).performClick()
        assertEquals(LocalDate.of(2027, 2, 26) to LocalDate.of(2027, 3, 3), picked)
    }

    @Test
    fun unSoloDiaMarcadoEsUnViajeDeUnDia() {
        open(LocalDate.of(2027, 2, 1), LocalDate.of(2027, 2, 1))
        compose.onNodeWithContentDescription(day(2027, 2, 5)).performClick()
        compose.onNodeWithText(str(R.string.action_ok)).performClick()
        assertEquals(LocalDate.of(2027, 2, 5) to LocalDate.of(2027, 2, 5), picked)
    }

    @Test
    fun alPulsarElMesSePuedeSaltarDeAno() {
        open(LocalDate.of(2027, 2, 10), null)
        compose.onNodeWithText(month(2027, 2)).performClick()
        compose.onNodeWithText("2028").performClick()
        compose.onNodeWithText(month(2028, 2)).assertIsDisplayed()
    }

    @Test
    fun sinNadaMarcadoNoSePuedeAceptar() {
        open(null, null)
        compose.onNodeWithText(str(R.string.action_ok)).performClick()   // desactivado: no hace nada
        assertNull(picked)
    }
}

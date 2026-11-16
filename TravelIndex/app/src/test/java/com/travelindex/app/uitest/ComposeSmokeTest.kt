package com.travelindex.app.uitest

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.travelindex.app.domain.FileGroup
import com.travelindex.app.ui.components.PickerField
import com.travelindex.app.ui.files.FileGroupHeader
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests de interfaz en el JVM (Robolectric + Compose): montan composables reales y los pulsan, sin
 * emulador ni móvil. Corren con `gradle testDebugUnitTest`, igual que los tests de dominio.
 * (El arnés local con kotlinc no los compila: necesitan el classpath de Android; los ejecuta CI.)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ComposeSmokeTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun cabeceraDeGrupoRespondeAlToque() {
        var toggles = 0
        compose.setContent {
            MaterialTheme { FileGroupHeader(FileGroup.FLIGHTS, 3, expanded = false, onToggle = { toggles++ }) }
        }
        compose.onNodeWithText("3").assertIsDisplayed()
        compose.onNodeWithText("3").performClick()
        assertEquals(1, toggles)
    }

    @Test
    fun campoSelectorMuestraEtiquetaValorYError() {
        var clicks = 0
        compose.setContent {
            MaterialTheme { PickerField(label = "Fechas", value = "1 – 5", onClick = { clicks++ }, error = "Campo obligatorio") }
        }
        compose.onNodeWithText("Fechas").assertIsDisplayed()
        compose.onNodeWithText("1 – 5").assertIsDisplayed()
        compose.onNodeWithText("Campo obligatorio").assertIsDisplayed()
        compose.onNodeWithText("1 – 5").performClick()
        assertEquals(1, clicks)
    }
}

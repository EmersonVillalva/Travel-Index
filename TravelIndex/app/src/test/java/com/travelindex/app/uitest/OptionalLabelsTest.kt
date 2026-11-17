package com.travelindex.app.uitest

import android.app.Application
import com.travelindex.app.domain.ModuleType
import com.travelindex.app.domain.TransportSection
import com.travelindex.app.ui.modules.ModuleSpec
import com.travelindex.app.ui.modules.ModuleSpecs
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * «(opcional)» solo se ve al añadir o editar datos: el detalle y el PDF usan las etiquetas sin él.
 * Las comprobaciones no dependen del idioma del test (miran «opcional» y «optional»).
 */
@RunWith(RobolectricTestRunner::class)
// Application vacía: solo se leen textos, y así no arranca la app real (Hilt, Room…) con consultas en segundo plano.
@Config(sdk = [34], application = Application::class)
class OptionalLabelsTest {
    private val context get() = RuntimeEnvironment.getApplication()

    private fun text(id: Int) = context.getString(id)
    private fun marksOptional(s: String) = s.contains("opcional", ignoreCase = true) || s.contains("optional", ignoreCase = true)

    private fun allSpecs(): List<ModuleSpec> =
        ModuleType.entries.flatMap { type ->
            if (type == ModuleType.TRANSPORT) {
                listOf(ModuleSpecs.of(type)) + TransportSection.entries.map { ModuleSpecs.of(type, it) }
            } else {
                listOf(ModuleSpecs.of(type))
            }
        }.distinct()

    @Test
    fun llegadaDelVueloSoloEsOpcionalEnElFormulario() {
        val section = ModuleSpecs.of(ModuleType.FLIGHT).sections.first { it.formTitleRes != null }
        assertFalse(marksOptional(text(section.titleRes)))
        assertTrue(marksOptional(text(section.formTitleRes!!)))
    }

    @Test
    fun duracionDeLaActividadSoloEsOpcionalEnElFormulario() {
        val def = ModuleSpecs.of(ModuleType.ACTIVITY).field("duration")
        assertNotNull(def)
        assertFalse(marksOptional(text(def!!.labelRes)))
        assertTrue(marksOptional(text(def.formLabelRes!!)))
    }

    @Test
    fun ningunaEtiquetaDeDetalleNiPdfDiceOpcional() {
        allSpecs().forEach { spec ->
            spec.sections.forEach { assertFalse("sección ${it.titleRes}", marksOptional(text(it.titleRes))) }
            spec.fields.forEach { assertFalse("campo ${it.key}", marksOptional(text(it.labelRes))) }
        }
    }
}

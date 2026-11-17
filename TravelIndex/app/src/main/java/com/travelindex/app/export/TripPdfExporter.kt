package com.travelindex.app.export

import android.content.Context
import com.travelindex.app.R
import com.travelindex.app.data.LanguageManager
import com.travelindex.app.data.SettingsRepository
import com.travelindex.app.data.repo.TripRepository
import com.travelindex.app.domain.Formats
import com.travelindex.app.domain.pdf.TripPdf
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Crea el PDF de un viaje completo en la caché de la app y devuelve el archivo; la pantalla lo entrega a la app que elija
 * el usuario (abrir, compartir, guardar, imprimir). Todo se hace en el móvil: sin internet ni servidores.
 */
@Singleton
class TripPdfExporter @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val trips: TripRepository,
    private val settings: SettingsRepository,
) {
    // El PDF sale en el idioma elegido en la app, no en el del móvil.
    private val context: Context get() = LanguageManager.localize(appContext)
    private val sharedDir: File get() = File(context.cacheDir, "shared").apply { mkdirs() }

    /** null si el viaje ya no existe. */
    suspend fun export(tripId: Long): File? = withContext(Dispatchers.IO) {
        val bundle = trips.bundles.filterNotNull().first().firstOrNull { it.trip.id == tripId } ?: return@withContext null
        val formats = Formats(settings.settings.first().dateFormat)
        val blocks = TripPdfContent(context, formats).build(bundle)
        // Solo se conserva el último PDF. El archivo de «Compartir viaje» vive en la misma carpeta y no se toca.
        sharedDir.listFiles()?.filter { it.name.endsWith("." + TripPdf.EXTENSION) }?.forEach { it.delete() }
        val file = File(sharedDir, TripPdf.fileNameFor(bundle.trip.name))
        val name = bundle.trip.name.take(40)
        try {
            PdfWriter.write(blocks, file) { page, total -> context.getString(R.string.pdf_footer, name, page, total) }
        } catch (t: Throwable) {
            file.delete(); throw t
        }
        file
    }
}

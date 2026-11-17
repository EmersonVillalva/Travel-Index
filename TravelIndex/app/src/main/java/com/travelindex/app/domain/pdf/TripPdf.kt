package com.travelindex.app.domain.pdf

/** Datos del archivo PDF de un viaje. */
object TripPdf {
    const val MIME = "application/pdf"
    const val EXTENSION = "pdf"

    /** Tamaño de página A4 en puntos, con margen de unos 15 mm. */
    val PAGE = PageSpec(width = 595f, height = 842f, margin = 42f, footerHeight = 14f)

    /** Páginas de archivos adjuntos que se meten en el PDF como máximo (cada una ocupa memoria hasta terminar de escribirlo). */
    const val MAX_ATTACHMENT_PAGES = 30

    /** Ancho en píxeles al que se dibuja cada página de un PDF adjunto, y lado mayor de una foto adjunta. */
    const val ATTACHMENT_PAGE_PX = 900
    const val ATTACHMENT_PHOTO_PX = 1200

    /** Nombre del archivo: "Roma.pdf" (sin acentos ni símbolos que den problemas al enviarlo). */
    fun fileNameFor(tripName: String): String {
        val plain = java.text.Normalizer.normalize(tripName, java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
        val safe = plain.replace(Regex("[^A-Za-z0-9 _-]"), "").trim().replace(' ', '_').take(40).ifEmpty { "viaje" }
        return "$safe.$EXTENSION"
    }
}

package com.travelindex.app.domain

/**
 * Visor interno de archivos: qué tipos abre y los cálculos puros de su dibujo (tamaño de página y límites del zoom).
 * La parte que pinta en pantalla está en ui/viewer; esta lógica se prueba con JUnit.
 */
object ViewerSupport {
    const val MIN_SCALE = 1f
    const val MAX_SCALE = 5f

    /** El visor interno abre PDFs e imágenes; el resto sigue yendo a una app externa. */
    fun opensInternally(mime: String): Boolean = mime == "application/pdf" || mime.startsWith("image/")

    /**
     * Ancho en píxeles al que se dibuja una página de PDF: una vez y media la pantalla, para que al acercar un poco
     * siga nítida sin gastar memoria de más (una página A4 a 1620 px son ~15 MB; a doble pantalla serían ~26 MB).
     */
    fun pageRenderWidth(screenWidthPx: Int): Int = (screenWidthPx * 3 / 2).coerceIn(480, 1620)

    /** Alto proporcional al dibujar una página de [pageW]x[pageH] puntos con [targetW] píxeles de ancho. */
    fun pageHeightFor(pageW: Int, pageH: Int, targetW: Int): Int =
        if (pageW <= 0 || pageH <= 0) targetW else (targetW.toLong() * pageH / pageW).toInt().coerceAtLeast(1)

    fun clampScale(s: Float): Float = s.coerceIn(MIN_SCALE, MAX_SCALE)

    /** Desplazamiento máximo para que el contenido ampliado no se escape del marco: (tamaño·(escala−1))/2. */
    fun maxPan(sizePx: Float, scale: Float): Float = (sizePx * (clampScale(scale) - 1f) / 2f).coerceAtLeast(0f)

    fun clampPan(offset: Float, sizePx: Float, scale: Float): Float {
        val m = maxPan(sizePx, scale)
        return offset.coerceIn(-m, m)
    }
}

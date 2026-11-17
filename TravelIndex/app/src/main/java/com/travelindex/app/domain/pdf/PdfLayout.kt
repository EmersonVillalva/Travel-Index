package com.travelindex.app.domain.pdf

/**
 * Motor de maquetación del PDF del viaje. Es Kotlin puro (sin Android): recibe bloques de contenido y devuelve páginas con
 * textos y líneas ya colocados. Quien dibuja (la capa de Android) solo tiene que pintar cada operación. Así el reparto del
 * texto en líneas y en páginas se puede probar sin un móvil.
 */
enum class PdfStyle { TITLE, SUBTITLE, HEADING, ENTRY_TITLE, LABEL, BODY, SMALL }

sealed interface PdfBlock {
    /** Un párrafo; los saltos de línea del texto se respetan y las líneas largas se parten. */
    data class Paragraph(val text: String, val style: PdfStyle = PdfStyle.BODY, val indent: Float = 0f) : PdfBlock

    /** Etiqueta a la izquierda y valor a la derecha; el valor puede ocupar varias líneas. */
    data class Row(val label: String, val value: String) : PdfBlock

    /** Línea fina horizontal. */
    data object Rule : PdfBlock

    /** Espacio en blanco (se ignora al principio de una página). */
    data class Gap(val height: Float) : PdfBlock

    /**
     * Bloques que se intentan dejar en la misma página (un título con sus filas, o un apartado con su primer elemento).
     * Si no caben en una página entera, se reparten igualmente.
     */
    data class Keep(val blocks: List<PdfBlock>) : PdfBlock

    /** Empieza una página nueva (si la actual ya tiene contenido; nunca deja una página vacía). */
    data object PageBreak : PdfBlock

    /**
     * Una imagen (p. ej. una página de un archivo adjunto) a todo el ancho útil, con la proporción [aspect] = ancho / alto.
     * Si no cabe en lo que queda de página se reduce para caber; si queda muy poco sitio, pasa a una página nueva.
     * [key] la identifica cuando quien dibuja la pide.
     */
    data class Picture(val key: String, val aspect: Float) : PdfBlock
}

interface TextMeasurer {
    fun width(text: String, style: PdfStyle): Float
    fun lineHeight(style: PdfStyle): Float
}

/** Tamaño de página y márgenes, en puntos (1 pt = 1/72 de pulgada; un A4 son 595 × 842). */
data class PageSpec(val width: Float, val height: Float, val margin: Float, val footerHeight: Float)

sealed interface DrawOp {
    /** [baseline] es la línea sobre la que se apoya el texto. */
    data class Text(val x: Float, val baseline: Float, val text: String, val style: PdfStyle) : DrawOp
    data class Line(val x1: Float, val x2: Float, val y: Float) : DrawOp
    /** Imagen [key] dentro del rectángulo (x, y, ancho, alto), ya reducida y centrada. */
    data class Picture(val key: String, val x: Float, val y: Float, val width: Float, val height: Float) : DrawOp
}

data class PdfPage(val ops: List<DrawOp>)

object PdfLayout {
    /** Parte de la anchura que ocupa la columna de etiquetas en una fila. */
    const val LABEL_FRACTION = 0.30f
    const val COLUMN_GAP = 10f
    private const val ROW_PADDING = 3f
    private const val RULE_HEIGHT = 8f
    /** Dónde cae la línea base dentro de una línea de texto (fracción de su altura). */
    private const val BASELINE = 0.78f
    /** Si a una imagen le quedan menos puntos que estos en la página, empieza otra en vez de salir diminuta. */
    private const val MIN_PICTURE_SPACE = 120f

    /**
     * Reparte [blocks] en páginas. [footer] da el texto del pie de cada página (número de página y total); null = sin pie.
     * Siempre devuelve al menos una página.
     */
    fun layout(
        blocks: List<PdfBlock>, page: PageSpec, measurer: TextMeasurer, footer: ((pageNumber: Int, total: Int) -> String?)? = null,
    ): List<PdfPage> {
        val l = Layouter(page, measurer)
        blocks.forEach { l.place(it) }
        val pages = l.finish()
        return pages.mapIndexed { i, ops ->
            val text = footer?.invoke(i + 1, pages.size)
            if (text.isNullOrEmpty()) PdfPage(ops)
            else PdfPage(ops + DrawOp.Text(page.margin, page.height - page.margin, text, PdfStyle.SMALL))
        }
    }

    /** Parte [text] en líneas que caben en [maxWidth]: respeta los saltos de línea y parte las palabras demasiado largas. */
    fun wrap(text: String, style: PdfStyle, maxWidth: Float, m: TextMeasurer): List<String> {
        val out = ArrayList<String>()
        for (paragraph in text.replace("\r\n", "\n").replace('\r', '\n').split('\n')) {
            val words = paragraph.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            if (words.isEmpty()) { out += ""; continue }
            var line = ""
            for (word in words) {
                val candidate = if (line.isEmpty()) word else "$line $word"
                if (m.width(candidate, style) <= maxWidth) { line = candidate; continue }
                if (line.isNotEmpty()) { out += line; line = "" }
                // Una palabra más ancha que la línea (una dirección web, por ejemplo) se parte por letras.
                var rest = word
                while (m.width(rest, style) > maxWidth && rest.length > 1) {
                    var n = 1
                    while (n < rest.length && m.width(rest.substring(0, n + 1), style) <= maxWidth) n++
                    out += rest.substring(0, n)
                    rest = rest.substring(n)
                }
                line = rest
            }
            if (line.isNotEmpty()) out += line
        }
        return out
    }

    private class Piece(val height: Float, val isGap: Boolean, val draw: (top: Float, out: MutableList<DrawOp>) -> Unit)

    private class Layouter(val page: PageSpec, val m: TextMeasurer) {
        private val left = page.margin
        private val right = page.width - page.margin
        private val top = page.margin
        private val bottom = page.height - page.margin - page.footerHeight
        private val pages = ArrayList<MutableList<DrawOp>>()
        private var ops: MutableList<DrawOp> = newPage()
        private var y = top

        private fun newPage(): MutableList<DrawOp> = ArrayList<DrawOp>().also { pages += it }

        private fun breakPage() { ops = newPage(); y = top }

        fun finish(): List<List<DrawOp>> = pages

        fun place(block: PdfBlock) {
            when (block) {
                PdfBlock.PageBreak -> { if (y > top) breakPage(); return }
                is PdfBlock.Picture -> { placePicture(block); return }
                else -> Unit
            }
            val pieces = pieces(block)
            if (block is PdfBlock.Keep) {
                val total = pieces.sumOf { it.height.toDouble() }.toFloat()
                // Cabe en una página entera pero no en lo que queda de esta: empieza una nueva para no partirlo.
                if (total <= bottom - top && y + total > bottom && y > top) breakPage()
            }
            pieces.forEach { put(it) }
        }

        /** La imagen ocupa todo el ancho; si no cabe a lo alto, se reduce (sin deformarse) y se centra. */
        private fun placePicture(pic: PdfBlock.Picture) {
            val aspect = if (pic.aspect > 0.01f) pic.aspect else 1f
            var available = bottom - y
            if (y > top && available < MIN_PICTURE_SPACE) { breakPage(); available = bottom - y }
            val maxWidth = right - left
            var w = maxWidth
            var h = w / aspect
            if (h > available) { h = available; w = h * aspect }
            ops.add(DrawOp.Picture(pic.key, left + (maxWidth - w) / 2, y, w, h))
            y += h
        }

        private fun put(p: Piece) {
            if (p.isGap) {
                // Un espacio nunca abre una página ni la empuja: al principio o al final de una página desaparece.
                if (y == top) return
                if (y + p.height > bottom) { breakPage(); return }
                y += p.height
                return
            }
            if (y + p.height > bottom && y > top) breakPage()
            p.draw(y, ops)
            y += p.height
        }

        private fun pieces(block: PdfBlock): List<Piece> = when (block) {
            is PdfBlock.Keep -> block.blocks.flatMap { pieces(it) }
            is PdfBlock.Gap -> listOf(Piece(block.height, true) { _, _ -> })
            PdfBlock.Rule -> listOf(Piece(RULE_HEIGHT, false) { t, out -> out += DrawOp.Line(left, right, t + RULE_HEIGHT / 2) })
            is PdfBlock.Paragraph -> {
                val x = left + block.indent
                val lh = m.lineHeight(block.style)
                wrap(block.text, block.style, right - x, m).map { line ->
                    Piece(lh, false) { t, out -> if (line.isNotEmpty()) out += DrawOp.Text(x, t + lh * BASELINE, line, block.style) }
                }
            }
            is PdfBlock.Row -> listOf(row(block))
            PdfBlock.PageBreak -> emptyList()
            // Dentro de un Keep una imagen no se reduce al hueco de la página: ocupa su alto natural (como mucho una página).
            is PdfBlock.Picture -> {
                val maxWidth = right - left
                val aspect = if (block.aspect > 0.01f) block.aspect else 1f
                var w = maxWidth
                var h = w / aspect
                if (h > bottom - top) { h = bottom - top; w = h * aspect }
                listOf(Piece(h, false) { t, out -> out += DrawOp.Picture(block.key, left + (maxWidth - w) / 2, t, w, h) })
            }
        }

        private fun row(r: PdfBlock.Row): Piece {
            val contentWidth = right - left
            val labelWidth = contentWidth * LABEL_FRACTION
            val valueX = left + labelWidth + COLUMN_GAP
            val labelLines = wrap(r.label, PdfStyle.LABEL, labelWidth, m)
            val valueLines = wrap(r.value, PdfStyle.BODY, right - valueX, m)
            val bodyH = m.lineHeight(PdfStyle.BODY)
            val labelH = m.lineHeight(PdfStyle.LABEL)
            val height = maxOf(valueLines.size * bodyH, labelLines.size * labelH) + ROW_PADDING
            return Piece(height, false) { t, out ->
                labelLines.forEachIndexed { i, s -> if (s.isNotEmpty()) out += DrawOp.Text(left, t + i * labelH + labelH * BASELINE, s, PdfStyle.LABEL) }
                valueLines.forEachIndexed { i, s -> if (s.isNotEmpty()) out += DrawOp.Text(valueX, t + i * bodyH + bodyH * BASELINE, s, PdfStyle.BODY) }
            }
        }
    }
}

/** Piezas pequeñas para armar el contenido del PDF, con la regla de que un título nunca se queda solo al final de una página. */
object PdfBlocks {
    /** Un elemento (un vuelo, un hotel...): su título (si lo tiene), una línea de resumen opcional y una fila por dato. Va junto en una página. */
    fun entry(title: String, subtitle: String, rows: List<Pair<String, String>>): PdfBlock.Keep = PdfBlock.Keep(
        buildList {
            if (title.isNotBlank()) add(PdfBlock.Paragraph(title, PdfStyle.ENTRY_TITLE))
            if (subtitle.isNotBlank()) add(PdfBlock.Paragraph(subtitle, PdfStyle.BODY))
            rows.filter { it.second.isNotBlank() }.forEach { (label, value) -> add(PdfBlock.Row(label, value)) }
            add(PdfBlock.Gap(8f))
        },
    )

    /** Un apartado: su título y sus elementos. El título va pegado al primer elemento. Sin elementos no hay apartado. */
    fun section(title: String, entries: List<PdfBlock>): List<PdfBlock> {
        if (entries.isEmpty()) return emptyList()
        val head = listOf(PdfBlock.Gap(10f), PdfBlock.Paragraph(title, PdfStyle.HEADING), PdfBlock.Rule)
        val first = entries.first()
        val firstKept = if (first is PdfBlock.Keep) PdfBlock.Keep(head + first.blocks) else PdfBlock.Keep(head + first)
        return listOf(firstKept) + entries.drop(1)
    }
}

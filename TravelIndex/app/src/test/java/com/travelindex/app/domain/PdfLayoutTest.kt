package com.travelindex.app.domain

import com.travelindex.app.domain.pdf.DrawOp
import com.travelindex.app.domain.pdf.PageSpec
import com.travelindex.app.domain.pdf.PdfBlock
import com.travelindex.app.domain.pdf.PdfBlocks
import com.travelindex.app.domain.pdf.PdfLayout
import com.travelindex.app.domain.pdf.PdfPage
import com.travelindex.app.domain.pdf.PdfStyle
import com.travelindex.app.domain.pdf.TextMeasurer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfLayoutTest {
    /** 6 puntos por letra y 12 de alto por línea (el título, 24): así se calcula a mano. */
    private val measurer = object : TextMeasurer {
        override fun width(text: String, style: PdfStyle) = text.length * 6f
        override fun lineHeight(style: PdfStyle) = if (style == PdfStyle.TITLE) 24f else 12f
    }

    /** 300 × 200 con margen 20 y pie de 10: anchura útil 260 (43 letras), alto útil de 20 a 170 (150 puntos = 12 líneas). */
    private val page = PageSpec(width = 300f, height = 200f, margin = 20f, footerHeight = 10f)

    private fun texts(p: PdfPage) = p.ops.filterIsInstance<DrawOp.Text>()
    private fun lay(vararg blocks: PdfBlock, footer: ((Int, Int) -> String?)? = null) =
        PdfLayout.layout(blocks.toList(), page, measurer, footer)

    @Test fun `un texto corto cabe en una pagina en el margen izquierdo`() {
        val pages = lay(PdfBlock.Paragraph("Hola"))
        assertEquals(1, pages.size)
        val t = texts(pages[0]).single()
        assertEquals("Hola", t.text); assertEquals(20f, t.x, 0.001f)
    }

    @Test fun `sin contenido sigue habiendo una pagina`() {
        assertEquals(1, PdfLayout.layout(emptyList(), page, measurer).size)
    }

    @Test fun `una linea larga se parte por palabras sin pasarse del ancho`() {
        val text = "uno dos tres cuatro cinco seis siete ocho nueve diez once doce trece catorce quince"
        val lines = PdfLayout.wrap(text, PdfStyle.BODY, 260f, measurer)
        assertTrue(lines.size > 1)
        lines.forEach { assertTrue("'$it' mide ${measurer.width(it, PdfStyle.BODY)}", measurer.width(it, PdfStyle.BODY) <= 260f) }
        assertEquals(text.split(" "), lines.flatMap { it.split(" ") })   // no se pierde ni se repite ninguna palabra
    }

    @Test fun `una palabra mas larga que la linea se parte por letras`() {
        val url = "https://ejemplo.com/" + "a".repeat(80)
        val lines = PdfLayout.wrap(url, PdfStyle.BODY, 260f, measurer)
        assertTrue(lines.size >= 3)
        lines.forEach { assertTrue(measurer.width(it, PdfStyle.BODY) <= 260f) }
        assertEquals(url, lines.joinToString(""))
    }

    @Test fun `los saltos de linea se respetan y una linea vacia sigue siendo una linea`() {
        assertEquals(listOf("a", "", "b"), PdfLayout.wrap("a\n\nb", PdfStyle.BODY, 260f, measurer))
        assertEquals(listOf("a", "b"), PdfLayout.wrap("a\r\nb", PdfStyle.BODY, 260f, measurer))
        assertEquals(listOf(""), PdfLayout.wrap("", PdfStyle.BODY, 260f, measurer))
    }

    @Test fun `lo que no cabe pasa a la pagina siguiente y nada se sale del area util`() {
        val blocks = (1..40).map { PdfBlock.Paragraph("Linea $it") }
        val pages = PdfLayout.layout(blocks, page, measurer)
        assertEquals(4, pages.size)   // 12 líneas por página
        assertEquals(listOf(12, 12, 12, 4), pages.map { texts(it).size })
        pages.forEach { p -> texts(p).forEach { assertTrue("${it.text} en ${it.baseline}", it.baseline in 20f..170f) } }
        assertEquals((1..40).map { "Linea $it" }, pages.flatMap { texts(it) }.map { it.text })
    }

    @Test fun `un bloque Keep que no cabe en lo que queda se lleva entero a la pagina siguiente`() {
        val fill = (1..8).map { PdfBlock.Paragraph("relleno $it") }                   // 96 de 150 puntos
        val keep = PdfBlock.Keep((1..5).map { PdfBlock.Paragraph("junto $it") })      // 60 puntos: no caben en los 54 que quedan
        val pages = PdfLayout.layout(fill + keep, page, measurer)
        assertEquals(2, pages.size)
        assertEquals(8, texts(pages[0]).size)
        assertEquals((1..5).map { "junto $it" }, texts(pages[1]).map { it.text })
    }

    @Test fun `un bloque Keep mas grande que una pagina se reparte igualmente`() {
        val keep = PdfBlock.Keep((1..25).map { PdfBlock.Paragraph("l$it") })
        val pages = PdfLayout.layout(listOf(keep), page, measurer)
        assertEquals(3, pages.size)
        assertEquals((1..25).map { "l$it" }, pages.flatMap { texts(it) }.map { it.text })
    }

    @Test fun `un espacio al principio de una pagina se ignora`() {
        val fill = (1..12).map { PdfBlock.Paragraph("r$it") }      // llena la página
        val pages = PdfLayout.layout(fill + PdfBlock.Gap(30f) + PdfBlock.Paragraph("nueva"), page, measurer)
        assertEquals(2, pages.size)
        assertEquals(20f + 12f * 0.78f, texts(pages[1]).single().baseline, 0.001f)
    }

    @Test fun `una fila pone la etiqueta a la izquierda y el valor a la derecha, y el valor largo ocupa varias lineas`() {
        val pages = lay(PdfBlock.Row("Terminal", "Terminal 2 del aeropuerto, puerta B12, cerca de la zona de embarque"))
        val ts = texts(pages[0])
        val label = ts.first { it.style == PdfStyle.LABEL }
        val values = ts.filter { it.style == PdfStyle.BODY }
        assertEquals(20f, label.x, 0.001f)
        assertTrue(values.size > 1)
        values.forEach { assertTrue(it.x > label.x + 60f) }
        values.forEach { assertTrue(it.x + measurer.width(it.text, PdfStyle.BODY) <= 280.001f) }
    }

    @Test fun `el pie lleva el numero de pagina y el total`() {
        val blocks = (1..15).map { PdfBlock.Paragraph("Linea $it") }
        val pages = PdfLayout.layout(blocks, page, measurer) { n, total -> "Pagina $n de $total" }
        assertEquals(2, pages.size)
        assertEquals("Pagina 1 de 2", texts(pages[0]).last().text)
        assertEquals("Pagina 2 de 2", texts(pages[1]).last().text)
        assertEquals(PdfStyle.SMALL, texts(pages[1]).last().style)
        assertEquals(180f, texts(pages[0]).last().baseline, 0.001f)
    }

    @Test fun `la regla se dibuja como una linea de margen a margen`() {
        val line = lay(PdfBlock.Rule)[0].ops.filterIsInstance<DrawOp.Line>().single()
        assertEquals(20f, line.x1, 0.001f); assertEquals(280f, line.x2, 0.001f)
    }

    @Test fun `un titulo de apartado nunca se queda solo al final de una pagina`() {
        val fill = (1..8).map { PdfBlock.Paragraph("relleno $it") }
        val entries = (1..3).map { PdfBlocks.entry("Elemento $it", "resumen", listOf("Dato" to "valor")) }
        val blocks = fill + PdfBlocks.section("Vuelos", entries)
        val pages = PdfLayout.layout(blocks, page, measurer)
        val firstPageTexts = texts(pages[0]).map { it.text }
        // Si el título está en la primera página, su primer elemento también.
        if ("Vuelos" in firstPageTexts) assertTrue("Elemento 1" in firstPageTexts)
        else assertTrue("Vuelos" in texts(pages[1]).map { it.text })
        assertEquals(1, pages.flatMap { texts(it) }.count { it.text == "Vuelos" })
    }

    @Test fun `un apartado sin elementos no se dibuja y una fila sin valor no sale`() {
        assertTrue(PdfBlocks.section("Vuelos", emptyList()).isEmpty())
        val entry = PdfBlocks.entry("Hotel", "", listOf("Telefono" to "", "Ciudad" to "Roma"))
        assertEquals(1, entry.blocks.count { it is PdfBlock.Row })
        assertFalse(entry.blocks.any { it is PdfBlock.Paragraph && it.style == PdfStyle.BODY })   // sin resumen no hay línea vacía
        assertNotEquals(0, entry.blocks.size)
    }

    // ---- Saltos de página e imágenes (archivos adjuntos) ----

    private fun pictures(p: PdfPage) = p.ops.filterIsInstance<DrawOp.Picture>()

    @Test fun `un salto de pagina empieza otra pagina pero nunca deja una vacia`() {
        assertEquals(2, lay(PdfBlock.Paragraph("a"), PdfBlock.PageBreak, PdfBlock.Paragraph("b")).size)
        assertEquals(1, lay(PdfBlock.PageBreak, PdfBlock.Paragraph("a")).size)
        assertEquals(2, lay(PdfBlock.Paragraph("a"), PdfBlock.PageBreak, PdfBlock.PageBreak, PdfBlock.Paragraph("b")).size)
    }

    @Test fun `una imagen apaisada ocupa todo el ancho util`() {
        val pic = pictures(lay(PdfBlock.Picture("p1", 2f))[0]).single()
        assertEquals("p1", pic.key)
        assertEquals(260f, pic.width, 0.001f); assertEquals(130f, pic.height, 0.001f)
        assertEquals(20f, pic.x, 0.001f); assertEquals(20f, pic.y, 0.001f)
    }

    @Test fun `una imagen alta se reduce al alto de la pagina y se centra sin deformarse`() {
        val pic = pictures(lay(PdfBlock.Picture("p", 0.5f))[0]).single()
        assertEquals(150f, pic.height, 0.001f); assertEquals(75f, pic.width, 0.001f)
        assertEquals(0.5f, pic.width / pic.height, 0.001f)
        assertEquals(112.5f, pic.x, 0.001f)   // (260 - 75) / 2 + 20
    }

    @Test fun `con poco sitio la imagen pasa a la pagina siguiente`() {
        // 10 lineas de 12 = 120 puntos usados: quedan 30, menos del minimo.
        val text = PdfBlock.Paragraph((1..10).joinToString("\n") { "linea $it" })
        val pages = lay(text, PdfBlock.Picture("p", 1f))
        assertEquals(2, pages.size)
        assertTrue(pictures(pages[0]).isEmpty())
        assertEquals(20f, pictures(pages[1]).single().y, 0.001f)
    }

    @Test fun `con sitio suficiente la imagen se reduce al hueco que queda`() {
        val pages = lay(PdfBlock.Paragraph("titulo"), PdfBlock.Picture("p", 0.5f))
        assertEquals(1, pages.size)
        val pic = pictures(pages[0]).single()
        assertEquals(32f, pic.y, 0.001f)          // justo debajo de la linea de 12
        assertEquals(138f, pic.height, 0.001f)    // lo que queda hasta el pie
        assertEquals(69f, pic.width, 0.001f)
    }
}

class TripPdfNameTest {
    @Test fun `el nombre del pdf queda limpio de acentos y simbolos`() {
        assertEquals("application/pdf", com.travelindex.app.domain.pdf.TripPdf.MIME)
        assertEquals("Roma.pdf", com.travelindex.app.domain.pdf.TripPdf.fileNameFor("Roma"))
        assertEquals("Mexico_DF_2027.pdf", com.travelindex.app.domain.pdf.TripPdf.fileNameFor("México, D.F. 2027!"))
        assertEquals("viaje.pdf", com.travelindex.app.domain.pdf.TripPdf.fileNameFor("???"))
        assertEquals(40 + ".pdf".length, com.travelindex.app.domain.pdf.TripPdf.fileNameFor("a".repeat(100)).length)
    }

    @Test fun `un elemento sin titulo no deja una linea vacia`() {
        val e = PdfBlocks.entry("", "", listOf("Total" to "10 €"))
        assertEquals(listOf(PdfBlock.Row::class, PdfBlock.Gap::class), e.blocks.map { it::class })
    }

    @Test fun `el pdf de un viaje real cabe en paginas A4 y nada se sale del margen`() {
        val m = object : TextMeasurer {
            override fun width(text: String, style: PdfStyle) = text.length * 5.2f
            override fun lineHeight(style: PdfStyle) = 14f
        }
        val entries = (1..60).map { PdfBlocks.entry("Vuelo $it", "Madrid → Roma · 2 h 30 min", listOf(
            "Fecha de salida" to "12 jun 2027 11:45", "Localizador" to "ABC123", "Notas" to "texto largo ".repeat(30))) }
        val blocks = listOf<PdfBlock>(PdfBlock.Paragraph("Roma", PdfStyle.TITLE)) + PdfBlocks.section("Vuelos", entries)
        val pages = PdfLayout.layout(blocks, com.travelindex.app.domain.pdf.TripPdf.PAGE, m) { n, t -> "p $n/$t" }
        assertTrue(pages.size > 3)
        val spec = com.travelindex.app.domain.pdf.TripPdf.PAGE
        pages.forEach { p ->
            p.ops.filterIsInstance<DrawOp.Text>().filter { it.style != PdfStyle.SMALL }.forEach {
                assertTrue(it.baseline <= spec.height - spec.margin - spec.footerHeight)
                assertTrue(it.x >= spec.margin && it.x + m.width(it.text, it.style) <= spec.width - spec.margin + 0.01f)
            }
        }
        assertEquals(60, pages.flatMap { it.ops.filterIsInstance<DrawOp.Text>() }.count { it.text.startsWith("Vuelo ") })
    }
}

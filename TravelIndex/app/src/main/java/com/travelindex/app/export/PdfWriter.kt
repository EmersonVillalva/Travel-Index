package com.travelindex.app.export

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.travelindex.app.domain.pdf.DrawOp
import com.travelindex.app.domain.pdf.PdfBlock
import com.travelindex.app.domain.pdf.PdfLayout
import com.travelindex.app.domain.pdf.PdfPage
import com.travelindex.app.domain.pdf.PdfStyle
import com.travelindex.app.domain.pdf.TextMeasurer
import com.travelindex.app.domain.pdf.TripPdf
import java.io.File
import java.io.FileOutputStream

/**
 * Escribe un PDF con la clase PdfDocument de Android (no hace falta ninguna librería ni internet). El reparto del texto en
 * páginas lo hace [PdfLayout]; aquí solo se mide con las fuentes reales y se dibuja cada operación.
 */
object PdfWriter {
    /**
     * [images] da las imágenes de los bloques Picture (páginas de los archivos adjuntos). PdfDocument solo guarda una
     * referencia a cada imagen hasta que escribe el archivo, así que no se liberan hasta el final.
     */
    fun write(blocks: List<PdfBlock>, out: File, images: PdfImageSource? = null, footer: (pageNumber: Int, total: Int) -> String?) {
        val paints = Paints()
        val spec = TripPdf.PAGE
        val pages = PdfLayout.layout(blocks, spec, paints, footer)
        val doc = PdfDocument()
        val loaded = ArrayList<Bitmap>()
        try {
            pages.forEachIndexed { i, p ->
                val info = PdfDocument.PageInfo.Builder(spec.width.toInt(), spec.height.toInt(), i + 1).create()
                val page = doc.startPage(info)
                paints.draw(page.canvas, p, images, loaded)
                doc.finishPage(page)
            }
            FileOutputStream(out).use { doc.writeTo(it) }
        } finally {
            doc.close()
            loaded.forEach { it.recycle() }
        }
    }

    /** Fuentes y colores de cada estilo; también sirve para medir el texto al repartirlo en líneas. */
    private class Paints : TextMeasurer {
        private fun text(size: Float, bold: Boolean, color: String) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            this.color = Color.parseColor(color)
        }

        private val byStyle: Map<PdfStyle, Paint> = mapOf(
            PdfStyle.TITLE to text(24f, true, "#0F1E36"),
            PdfStyle.SUBTITLE to text(12f, false, "#55606E"),
            PdfStyle.HEADING to text(15f, true, "#0F1E36"),
            PdfStyle.ENTRY_TITLE to text(11.5f, true, "#1B2A41"),
            PdfStyle.LABEL to text(9f, false, "#6B7684"),
            PdfStyle.BODY to text(10.5f, false, "#1B1B1B"),
            PdfStyle.SMALL to text(8.5f, false, "#8A94A0"),
        )
        private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private val rule = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#C9D0DA"); strokeWidth = 0.8f }

        private fun paint(style: PdfStyle): Paint = byStyle.getValue(style)

        override fun width(text: String, style: PdfStyle): Float = paint(style).measureText(text)
        override fun lineHeight(style: PdfStyle): Float = paint(style).textSize * 1.35f

        fun draw(canvas: Canvas, page: PdfPage, images: PdfImageSource?, loaded: MutableList<Bitmap>) {
            page.ops.forEach { op ->
                when (op) {
                    is DrawOp.Text -> canvas.drawText(op.text, op.x, op.baseline, paint(op.style))
                    is DrawOp.Line -> canvas.drawLine(op.x1, op.y, op.x2, op.y, rule)
                    is DrawOp.Picture -> images?.load(op.key)?.let { bmp ->
                        loaded += bmp
                        canvas.drawBitmap(bmp, null, RectF(op.x, op.y, op.x + op.width, op.y + op.height), bitmapPaint)
                    }
                }
            }
        }
    }
}

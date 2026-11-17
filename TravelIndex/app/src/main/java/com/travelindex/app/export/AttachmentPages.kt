package com.travelindex.app.export

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.media.ExifInterface
import android.os.ParcelFileDescriptor
import com.travelindex.app.data.FileStorage
import com.travelindex.app.data.db.AttachmentEntity
import com.travelindex.app.domain.pdf.TripPdf
import java.io.Closeable
import java.io.File

/** De dónde saca el escritor del PDF las imágenes: la clave es la de cada [com.travelindex.app.domain.pdf.PdfBlock.Picture]. */
interface PdfImageSource {
    fun load(key: String): Bitmap?
}

/**
 * Convierte los archivos adjuntos de un viaje (PDF e imágenes) en páginas que se pueden dibujar dentro del PDF del viaje.
 * Primero se hace un [plan] (cuántas páginas y con qué proporción, sin dibujar nada) y las imágenes se dibujan de una en una
 * cuando el escritor las pide con [load]. Un PDF adjunto se abre con PdfRenderer, el mismo que usa el visor de la app.
 */
class AttachmentPages(private val files: FileStorage) : PdfImageSource, Closeable {
    data class PageRef(val key: String, val aspect: Float)

    sealed interface Plan {
        /** Ni PDF ni imagen (un .docx, por ejemplo): no se puede dibujar. */
        data object Unsupported : Plan
        /** Falta el archivo, está dañado o protegido con contraseña. */
        data object Failed : Plan
        /** [pages] son las que caben dentro del límite pedido; [total] son todas las que tiene el archivo. */
        data class Pages(val pages: List<PageRef>, val total: Int) : Plan
    }

    private sealed interface Source {
        data class PdfPage(val file: File, val index: Int) : Source
        data class Photo(val file: File) : Source
    }

    private val sources = HashMap<String, Source>()
    private var openFile: File? = null
    private var descriptor: ParcelFileDescriptor? = null
    private var renderer: PdfRenderer? = null

    fun plan(att: AttachmentEntity, maxPages: Int): Plan {
        val isPdf = att.mimeType == "application/pdf"
        val isImage = att.mimeType.startsWith("image/")
        if (!isPdf && !isImage) return Plan.Unsupported
        val file = files.resolve(att.relativePath)
        if (!file.isFile) return Plan.Failed
        return if (isPdf) planPdf(att.id, file, maxPages) else planImage(att.id, file, maxPages)
    }

    private fun planPdf(id: Long, file: File, maxPages: Int): Plan = try {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { r ->   // lanza excepción si está protegido con contraseña o dañado
                val total = r.pageCount
                val refs = (0 until minOf(total, maxPages.coerceAtLeast(0))).map { i ->
                    r.openPage(i).use { page ->
                        val key = "att$id-p$i"
                        sources[key] = Source.PdfPage(file, i)
                        PageRef(key, page.width.toFloat() / page.height.coerceAtLeast(1))
                    }
                }
                if (total == 0) Plan.Failed else Plan.Pages(refs, total)
            }
        }
    } catch (_: Exception) {
        Plan.Failed
    }

    private fun planImage(id: Long, file: File, maxPages: Int): Plan = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var w = bounds.outWidth
        var h = bounds.outHeight
        if (w <= 0 || h <= 0) Plan.Failed else {
            if (exifOrientation(file) in 5..8) { val t = w; w = h; h = t }   // la foto está girada 90°
            if (maxPages <= 0) Plan.Pages(emptyList(), 1) else {
                val key = "att$id"
                sources[key] = Source.Photo(file)
                Plan.Pages(listOf(PageRef(key, w.toFloat() / h)), 1)
            }
        }
    } catch (_: Exception) {
        Plan.Failed
    }

    override fun load(key: String): Bitmap? = try {
        when (val s = sources[key]) {
            is Source.PdfPage -> renderPdfPage(s)
            is Source.Photo -> decodePhoto(s.file)
            null -> null
        }
    } catch (_: Exception) {
        null
    } catch (_: OutOfMemoryError) {
        null
    }

    private fun renderPdfPage(s: Source.PdfPage): Bitmap? {
        val r = rendererFor(s.file) ?: return null
        if (s.index !in 0 until r.pageCount) return null
        return r.openPage(s.index).use { page ->
            val w = TripPdf.ATTACHMENT_PAGE_PX
            val h = (w.toLong() * page.height / page.width.coerceAtLeast(1)).toInt().coerceIn(1, 4 * w)
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            bmp.eraseColor(Color.WHITE)   // las páginas de un PDF son transparentes
            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            bmp
        }
    }

    /** Las páginas se piden en orden, así que se deja abierto el PDF en curso (PdfRenderer solo admite una página abierta a la vez). */
    private fun rendererFor(file: File): PdfRenderer? {
        if (openFile == file) renderer?.let { return it }
        closeRenderer()
        val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        return try {
            PdfRenderer(fd).also { descriptor = fd; renderer = it; openFile = file }
        } catch (e: Exception) {
            fd.close()
            null
        }
    }

    private fun decodePhoto(file: File): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        val limit = TripPdf.ATTACHMENT_PHOTO_PX
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= limit) sample *= 2
        var bmp = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        exifMatrix(exifOrientation(file))?.let { m ->
            val turned = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
            if (turned !== bmp) bmp.recycle()
            bmp = turned
        }
        val longest = maxOf(bmp.width, bmp.height)
        if (longest > limit) {
            val k = limit.toFloat() / longest
            val small = Bitmap.createScaledBitmap(bmp, (bmp.width * k).toInt().coerceAtLeast(1), (bmp.height * k).toInt().coerceAtLeast(1), true)
            if (small !== bmp) bmp.recycle()
            bmp = small
        }
        return bmp
    }

    private fun exifOrientation(file: File): Int = try {
        ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    } catch (_: Exception) {
        ExifInterface.ORIENTATION_NORMAL
    }

    private fun exifMatrix(orientation: Int): Matrix? {
        val m = Matrix()
        when (orientation) {
            2 -> m.setScale(-1f, 1f)
            3 -> m.setRotate(180f)
            4 -> { m.setRotate(180f); m.postScale(-1f, 1f) }
            5 -> { m.setRotate(90f); m.postScale(-1f, 1f) }
            6 -> m.setRotate(90f)
            7 -> { m.setRotate(-90f); m.postScale(-1f, 1f) }
            8 -> m.setRotate(-90f)
            else -> return null
        }
        return m
    }

    private fun closeRenderer() {
        try { renderer?.close() } catch (_: Exception) {}
        try { descriptor?.close() } catch (_: Exception) {}
        renderer = null; descriptor = null; openFile = null
    }

    override fun close() {
        closeRenderer()
        sources.clear()
    }
}

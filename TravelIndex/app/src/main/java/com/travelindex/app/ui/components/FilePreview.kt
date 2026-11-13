package com.travelindex.app.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Se puede mostrar una vista previa dentro de la app (imágenes y PDF). */
fun hasInlinePreview(mime: String): Boolean = mime.startsWith("image/") || mime == "application/pdf"

/**
 * Vista previa de un archivo SIN tener que abrirlo: la imagen entera, o la primera página de un PDF.
 * Si el archivo no se puede leer (PDF protegido, imagen corrupta), no pinta nada.
 */
@Composable
fun FilePreview(
    mime: String,
    modifier: Modifier = Modifier,
    file: File? = null,
    uri: Uri? = null,
    onClick: (() -> Unit)? = null,
) {
    if (!hasInlinePreview(mime) || (file == null && uri == null)) return
    val shape = RoundedCornerShape(12.dp)
    val base = modifier.fillMaxWidth().heightIn(max = 280.dp).clip(shape).let { if (onClick != null) it.clickable(onClick = onClick) else it }
    if (mime.startsWith("image/")) {
        AsyncImage(
            model = file ?: uri, contentDescription = null, modifier = base,
            contentScale = ContentScale.Fit, alignment = Alignment.Center,
        )
    } else {
        val ctx = LocalContext.current
        val page by produceState<Bitmap?>(initialValue = null, file, uri) { value = renderFirstPdfPage(ctx, file, uri) }
        page?.let {
            Image(
                bitmap = it.asImageBitmap(), contentDescription = null, modifier = base,
                contentScale = ContentScale.Crop, alignment = Alignment.TopCenter,
            )
        }
    }
}

private suspend fun renderFirstPdfPage(context: Context, file: File?, uri: Uri?): Bitmap? = withContext(Dispatchers.IO) {
    try {
        val fd: ParcelFileDescriptor = when {
            file != null -> ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            uri != null -> context.contentResolver.openFileDescriptor(uri, "r") ?: return@withContext null
            else -> return@withContext null
        }
        fd.use {
            PdfRenderer(it).use { renderer ->
                if (renderer.pageCount == 0) return@withContext null
                renderer.openPage(0).use { page ->
                    val width = 900
                    val height = (width.toFloat() * page.height / page.width).toInt().coerceIn(1, 2400)
                    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(android.graphics.Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bmp
                }
            }
        }
    } catch (e: Exception) {
        null   // PDF protegido, dañado o sin permiso: simplemente no hay vista previa
    }
}

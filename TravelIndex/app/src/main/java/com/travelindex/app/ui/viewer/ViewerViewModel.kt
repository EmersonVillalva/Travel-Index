package com.travelindex.app.ui.viewer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.travelindex.app.data.FileStorage
import com.travelindex.app.data.db.AttachmentDao
import com.travelindex.app.data.db.AttachmentEntity
import com.travelindex.app.domain.ViewerSupport
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Lo que el visor necesita: el archivo y, si es un PDF, cuántas páginas tiene (null = no se pudo abrir). */
data class ViewerState(
    val loaded: Boolean = false,
    val att: AttachmentEntity? = null,
    val file: File? = null,
    val isPdf: Boolean = false,
    val pageCount: Int? = null,
)

/** Abre el archivo del visor interno. Las páginas del PDF se dibujan de una en una (PdfRenderer no es concurrente). */
@HiltViewModel
class ViewerViewModel @Inject constructor(
    @ApplicationContext private val ctx: Context,
    savedState: SavedStateHandle,
    private val dao: AttachmentDao,
) : ViewModel() {
    private val attachmentId: Long = savedState["attachmentId"] ?: 0L

    private val _state = MutableStateFlow(ViewerState())
    val state: StateFlow<ViewerState> = _state

    /** Todas las operaciones del renderizador pasan por aquí: abrir, dibujar página y cerrar. */
    private val lock = Mutex()
    private var fd: ParcelFileDescriptor? = null
    private var renderer: PdfRenderer? = null

    init {
        viewModelScope.launch {
            val att = dao.getById(attachmentId)
            val file = att?.let { File(File(ctx.filesDir, FileStorage.DIR), it.relativePath) }?.takeIf { it.isFile }
            val isPdf = att?.mimeType == "application/pdf"
            val pages = if (isPdf && file != null) withContext(Dispatchers.IO) {
                lock.withLock {
                    try {
                        val p = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                        val r = PdfRenderer(p)   // lanza excepción si está protegido con contraseña o dañado
                        fd = p
                        renderer = r
                        r.pageCount
                    } catch (_: Exception) {
                        null
                    }
                }
            } else null
            _state.value = ViewerState(loaded = true, att = att, file = file, isPdf = isPdf, pageCount = pages)
        }
    }

    /** Dibuja una página al ancho pedido, con fondo blanco (las páginas de un PDF son transparentes). */
    suspend fun renderPage(index: Int, targetWidth: Int): Bitmap? = withContext(Dispatchers.IO) {
        lock.withLock {
            val r = renderer ?: return@withLock null
            if (index < 0 || index >= r.pageCount) return@withLock null
            try {
                r.openPage(index).use { page ->
                    val w = targetWidth.coerceAtLeast(1)
                    val h = ViewerSupport.pageHeightFor(page.width, page.height, w)
                    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bmp
                }
            } catch (_: Exception) {
                null
            }
        }
    }

    override fun onCleared() {
        // Espera a que termine la página en curso (como mucho una) antes de cerrar el renderizador nativo.
        runBlocking {
            lock.withLock {
                try { renderer?.close() } catch (_: Exception) {}
                try { fd?.close() } catch (_: Exception) {}
                renderer = null
                fd = null
            }
        }
    }
}

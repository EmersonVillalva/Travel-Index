package com.travelindex.app.data

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Fotos hechas con la cámara para guardarlas como documento ("escanear"). La cámara del sistema escribe la foto en un
 * archivo temporal de la caché de la app; al guardar el documento se copia al almacenamiento privado y el temporal se borra.
 * No hace falta el permiso de cámara: la foto la hace la app de cámara del sistema.
 */
object CameraCapture {
    /** Carpeta dentro de la caché; tiene que coincidir con `res/xml/file_paths.xml`. */
    private const val DIR = "camera"
    private const val STALE_MS = 24L * 60 * 60 * 1000

    private fun authority(context: Context) = context.packageName + ".fileprovider"
    private fun dir(context: Context) = File(context.cacheDir, DIR)

    /** Archivo nuevo y vacío para que la cámara escriba la foto. Antes limpia las fotos de más de un día que nunca se guardaron. */
    fun newUri(context: Context): Uri {
        val folder = dir(context).apply { mkdirs() }
        val limit = System.currentTimeMillis() - STALE_MS
        folder.listFiles()?.filter { it.lastModified() < limit }?.forEach { it.delete() }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(folder, "Escaneo_$stamp.jpg")
        file.createNewFile()   // algunas apps de cámara esperan que el archivo ya exista
        return FileProvider.getUriForFile(context, authority(context), file)
    }

    /** Borra la foto temporal si [uri] es una de las de la cámara; con cualquier otro uri no hace nada. */
    fun deleteIfCapture(context: Context, uri: Uri) {
        if (uri.authority != authority(context)) return
        val segments = uri.pathSegments
        if (segments.size != 2 || segments[0] != DIR) return
        val name = segments[1]
        if (name.contains('/') || name.contains("..")) return
        runCatching { File(dir(context), name).delete() }
    }
}

package com.travelindex.app.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class StoredFile(val relativePath: String, val displayName: String, val mimeType: String, val sizeBytes: Long)

/**
 * Copia los archivos elegidos por el usuario al almacenamiento PRIVADO de la app
 * (filesDir/attachments). Así siguen disponibles tras reiniciar y no dependen de permisos
 * de URI que caducan. Los nombres internos son UUID: no revelan el contenido.
 */
@Singleton
class FileStorage @Inject constructor(@ApplicationContext private val context: Context) {

    val root: File get() = File(context.filesDir, DIR).apply { mkdirs() }

    suspend fun import(uri: Uri): StoredFile = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        var name = uri.lastPathSegment?.substringAfterLast('/') ?: "archivo"
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0)?.let { name = it }
        }
        // Un file:// (p. ej. el PDF del escáner) no tiene tipo en el resolver: se deduce por la extensión.
        val mime = resolver.getType(uri) ?: when (name.substringAfterLast('.', "").lowercase()) {
            "pdf" -> "application/pdf"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            else -> "application/octet-stream"
        }
        val ext = name.substringAfterLast('.', "").takeIf { it.length in 1..5 }?.let { ".$it" } ?: ""
        val rel = UUID.randomUUID().toString() + ext
        val dest = File(root, rel)
        try {
            resolver.openInputStream(uri)?.use { input -> dest.outputStream().use { input.copyTo(it) } }
                ?: error("No se pudo abrir el archivo")
        } catch (t: Throwable) {
            dest.delete(); throw t
        }
        CameraCapture.deleteIfCapture(context, uri)
        StoredFile(rel, name, mime, dest.length())
    }

    /** Guarda el contenido de un flujo (p. ej. un archivo de un viaje compartido) con un nombre interno aleatorio. */
    suspend fun store(input: InputStream, displayName: String, mimeType: String, maxBytes: Long): StoredFile = withContext(Dispatchers.IO) {
        val ext = displayName.substringAfterLast('.', "").lowercase().takeIf { it.length in 1..5 && it.all { c -> c.isLetterOrDigit() } }?.let { ".$it" } ?: ""
        val rel = UUID.randomUUID().toString() + ext
        val dest = File(root, rel)
        try {
            var total = 0L
            dest.outputStream().use { out ->
                val buf = ByteArray(16 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    total += n
                    if (total > maxBytes) throw java.io.IOException("Archivo demasiado grande")
                    out.write(buf, 0, n)
                }
            }
        } catch (t: Throwable) {
            dest.delete(); throw t
        }
        StoredFile(rel, displayName, mimeType, dest.length())
    }

    /** Nombre visible de un archivo del selector del sistema (para mostrarlo antes de importarlo). */
    fun displayName(uri: Uri): String {
        var name = "archivo"
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0)?.let { name = it }
        }
        return name
    }

    suspend fun delete(relativePath: String) = withContext(Dispatchers.IO) {
        // Evita salir del directorio privado con rutas manipuladas.
        val f = File(root, relativePath)
        if (f.canonicalPath.startsWith(root.canonicalPath + File.separator)) f.delete()
    }

    fun resolve(relativePath: String): File = File(root, relativePath)

    // ---- Foto de perfil: carpeta aparte (no forma parte de los archivos de los viajes) ----

    private val profileDir: File get() = File(context.filesDir, PROFILE_DIR).apply { mkdirs() }

    /** Copia la foto elegida a almacenamiento privado y devuelve su nombre de archivo. */
    suspend fun importProfilePhoto(uri: Uri): String = withContext(Dispatchers.IO) {
        val mime = context.contentResolver.getType(uri).orEmpty()
        if (!mime.startsWith("image/")) throw IllegalArgumentException("No es una imagen")
        val name = "photo-" + UUID.randomUUID().toString() + ".img"
        val dest = File(profileDir, name)
        try {
            context.contentResolver.openInputStream(uri)?.use { input -> dest.outputStream().use { input.copyTo(it) } }
                ?: error("No se pudo abrir la imagen")
        } catch (t: Throwable) {
            dest.delete(); throw t
        }
        name
    }

    fun profilePhotoFile(name: String): File = File(profileDir, name)

    suspend fun deleteProfilePhoto(name: String) = withContext(Dispatchers.IO) {
        val f = File(profileDir, name)
        if (f.canonicalPath.startsWith(profileDir.canonicalPath + File.separator)) f.delete()
        Unit
    }

    companion object {
        const val DIR = "attachments"
        const val PROFILE_DIR = "profile"
    }
}

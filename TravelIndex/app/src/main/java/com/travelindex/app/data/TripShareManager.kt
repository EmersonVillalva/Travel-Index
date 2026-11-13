package com.travelindex.app.data

import android.content.Context
import android.net.Uri
import com.travelindex.app.data.repo.AttachmentRepository
import com.travelindex.app.data.repo.ModuleRepository
import com.travelindex.app.data.repo.TripRepository
import com.travelindex.app.domain.ModuleType
import com.travelindex.app.domain.ShareDecode
import com.travelindex.app.domain.ShareFile
import com.travelindex.app.domain.SharePackage
import com.travelindex.app.domain.TripShareFormat
import com.travelindex.app.domain.forms.TripCodec
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/** Lo que se muestra al usuario antes de importar. */
data class SharePreview(
    val name: String, val city: String, val country: String, val startDate: String, val endDate: String,
    val counts: Map<ModuleType, Int>, val fileCount: Int, val totalBytes: Long,
)

sealed interface PrepareResult {
    data class Ready(val preview: SharePreview) : PrepareResult
    data class Invalid(val reason: ShareDecode.Reason?) : PrepareResult
    data object Unreadable : PrepareResult
}

sealed interface ImportResult {
    data class Done(val tripId: Long, val skippedItems: Int) : ImportResult
    data object Failed : ImportResult
}

/**
 * Compartir un viaje = un ZIP (`trip.json` + `files/<n>`) con una copia exacta de sus datos y archivos.
 * No hay servidor: el archivo viaja por la app de mensajería, correo, etc. que elija el usuario.
 * El ZIP NO está cifrado y puede contener números de documentos: se avisa antes de compartir.
 */
@Singleton
class TripShareManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val trips: TripRepository,
    private val modules: ModuleRepository,
    private val attachments: AttachmentRepository,
    private val files: FileStorage,
) {
    private val sharedDir: File get() = File(context.cacheDir, "shared").apply { mkdirs() }
    private val importDir: File get() = File(context.cacheDir, "import").apply { mkdirs() }
    private val pendingFile: File get() = File(importDir, "pending.zip")

    // ---- Exportar -------------------------------------------------------------------------------

    /** Crea el ZIP del viaje en la caché y lo devuelve; null si no existe o es demasiado grande. */
    suspend fun export(tripId: Long): File? = withContext(Dispatchers.IO) {
        val bundle = trips.bundles.filterNotNull().first().firstOrNull { it.trip.id == tripId } ?: return@withContext null
        sharedDir.listFiles()?.forEach { it.delete() }   // solo se conserva el último

        val zipPaths = HashMap<Long, String>()
        bundle.attachments.forEach { a ->
            zipPaths[a.id] = "files/${a.id}${TripShareFormat.safeExtension(a.displayName)}"
        }
        val coverRel = bundle.trip.coverPath?.takeIf { File(files.root, it).isFile }
        // Los ids de adjuntos empiezan en 1, así que "files/0.ext" no choca con ninguno.
        val coverZipSafe = coverRel?.let { "files/0${TripShareFormat.safeExtension(it)}" }
        val pkg = TripShareFormat.fromBundle(bundle, { zipPaths.getValue(it.id) }, coverZipSafe)

        val total = bundle.attachments.sumOf { files.resolve(it.relativePath).takeIf { f -> f.isFile }?.length() ?: 0L }
        if (total > TripShareFormat.MAX_TOTAL_BYTES) return@withContext null

        val safeName = bundle.trip.name.replace(Regex("[^A-Za-z0-9 _-]"), "").trim().replace(' ', '_').take(40).ifEmpty { "viaje" }
        val out = File(sharedDir, "TravelIndex-$safeName.zip")
        try {
            ZipOutputStream(out.outputStream().buffered()).use { zip ->
                zip.putNextEntry(ZipEntry(TripShareFormat.ENTRY_JSON))
                zip.write(TripShareFormat.encode(pkg).toByteArray(Charsets.UTF_8)); zip.closeEntry()
                if (coverRel != null && coverZipSafe != null) addFile(zip, coverZipSafe, files.resolve(coverRel))
                bundle.attachments.forEach { a -> addFile(zip, zipPaths.getValue(a.id), files.resolve(a.relativePath)) }
            }
        } catch (e: IOException) {
            out.delete(); return@withContext null
        }
        out
    }

    private fun addFile(zip: ZipOutputStream, name: String, src: File) {
        if (!src.isFile) return
        zip.putNextEntry(ZipEntry(name))
        src.inputStream().use { it.copyTo(zip) }
        zip.closeEntry()
    }

    // ---- Importar -------------------------------------------------------------------------------

    /** Copia el archivo recibido a la caché, lo valida y devuelve un resumen. No toca los datos del usuario. */
    suspend fun prepare(uri: Uri): PrepareResult = withContext(Dispatchers.IO) {
        try {
            val limit = TripShareFormat.MAX_TOTAL_BYTES + 8L * 1024 * 1024
            context.contentResolver.openInputStream(uri)?.use { input ->
                var total = 0L
                pendingFile.outputStream().use { out ->
                    val buf = ByteArray(32 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        total += n
                        if (total > limit) { pendingFile.delete(); return@withContext PrepareResult.Invalid(ShareDecode.Reason.TOO_BIG) }
                        out.write(buf, 0, n)
                    }
                }
            } ?: return@withContext PrepareResult.Unreadable

            val pkg = readPackage(pendingFile) ?: run { pendingFile.delete(); return@withContext PrepareResult.Invalid(null) }
            when (pkg) {
                is ShareDecode.Invalid -> { pendingFile.delete(); PrepareResult.Invalid(pkg.reason) }
                is ShareDecode.Ok -> {
                    val p = pkg.pkg
                    val v = p.tripValues
                    if (!tripLooksValid(p)) { pendingFile.delete(); return@withContext PrepareResult.Invalid(ShareDecode.Reason.MALFORMED) }
                    PrepareResult.Ready(
                        SharePreview(
                            name = v["name"].orEmpty(), city = v["city"].orEmpty(), country = v["country"].orEmpty(),
                            startDate = v["startDate"].orEmpty(), endDate = v["endDate"].orEmpty(),
                            counts = p.items.groupingBy { it.type }.eachCount(),
                            fileCount = p.allFiles.size, totalBytes = p.allFiles.sumOf { it.sizeBytes },
                        )
                    )
                }
            }
        } catch (e: IOException) {
            pendingFile.delete(); PrepareResult.Unreadable
        } catch (e: java.util.zip.ZipException) {
            pendingFile.delete(); PrepareResult.Invalid(null)
        } catch (e: SecurityException) {
            PrepareResult.Unreadable
        }
    }

    private fun tripLooksValid(p: SharePackage): Boolean = runCatching {
        TripCodec.cross(p.tripValues).isEmpty() && p.tripValues["name"].orEmpty().isNotBlank() &&
            TripCodec.build(0, p.tripValues, null, 0L, false).name.isNotBlank()
    }.getOrDefault(false)

    private fun readPackage(zipFile: File): ShareDecode? = ZipFile(zipFile).use { zip ->
        if (zip.size() > TripShareFormat.MAX_FILES + 2) return ShareDecode.Invalid(ShareDecode.Reason.TOO_BIG)
        val entry = zip.getEntry(TripShareFormat.ENTRY_JSON) ?: return null
        if (entry.size > 8L * 1024 * 1024) return ShareDecode.Invalid(ShareDecode.Reason.TOO_BIG)
        val text = zip.getInputStream(entry).use { it.readBytes() }.toString(Charsets.UTF_8)
        TripShareFormat.decode(text)
    }

    /** Descarta lo preparado (el usuario dijo que no). */
    fun discard() { pendingFile.delete() }

    /** Crea una copia exacta del viaje preparado. Si algo falla, no queda nada a medias. */
    suspend fun commit(): ImportResult = withContext(Dispatchers.IO) {
        if (!pendingFile.isFile) return@withContext ImportResult.Failed
        val stored = ArrayList<String>()
        var tripId = 0L
        try {
            ZipFile(pendingFile).use { zip ->
                val pkg = (readPackage(pendingFile) as? ShareDecode.Ok)?.pkg ?: return@withContext ImportResult.Failed
                if (!tripLooksValid(pkg)) return@withContext ImportResult.Failed

                suspend fun store(f: ShareFile): StoredFile? {
                    if (!TripShareFormat.isSafeZipPath(f.zipPath)) return null
                    val e = zip.getEntry(f.zipPath) ?: return null
                    if (e.isDirectory) return null
                    return zip.getInputStream(e).use { files.store(it, f.displayName, f.mimeType, TripShareFormat.MAX_FILE_BYTES) }
                        .also { stored += it.relativePath }
                }

                val cover = pkg.cover?.let { store(it) }
                tripId = trips.createTrip(TripCodec.build(0, pkg.tripValues, cover?.relativePath, System.currentTimeMillis(), pkg.cancelled))
                var skipped = 0
                for (item in pkg.items) {
                    val id = runCatching { modules.save(item.type, tripId, 0, item.values) }.getOrNull()
                    if (id == null) { skipped++; continue }
                    for (f in item.files) store(f)?.let { attachments.addStored(tripId, item.type.name, id, it, f.category) }
                }
                for (f in pkg.tripFiles) store(f)?.let { attachments.addStored(tripId, null, null, it, f.category) }
                pendingFile.delete()
                ImportResult.Done(tripId, skipped)
            }
        } catch (t: Throwable) {
            if (tripId != 0L) runCatching { trips.deleteTrip(tripId) }
            stored.forEach { runCatching { files.delete(it) } }
            ImportResult.Failed
        }
    }

    companion object {
        /** Enlace de la ficha en Google Play. Solo funcionará cuando la app esté publicada. */
        const val PLAY_URL = "https://play.google.com/store/apps/details?id=com.travelindex.app"
    }
}

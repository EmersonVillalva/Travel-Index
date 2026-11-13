package com.travelindex.app.domain

import com.travelindex.app.data.db.AttachmentEntity
import com.travelindex.app.domain.forms.ModuleItems
import com.travelindex.app.domain.forms.TripCodec

/** Un archivo dentro del paquete. [zipPath] es el nombre dentro del ZIP (siempre `files/<n>[.ext]`, nunca el original). */
data class ShareFile(
    val zipPath: String,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val category: String,
)

data class ShareItem(
    val type: ModuleType,
    val values: Map<String, String>,
    val files: List<ShareFile> = emptyList(),
)

/** Copia exacta de un viaje, lista para escribirse como `trip.json` + archivos. */
data class SharePackage(
    val tripValues: Map<String, String>,
    val cancelled: Boolean,
    val cover: ShareFile? = null,
    val items: List<ShareItem> = emptyList(),
    /** Archivos sueltos del viaje (no pertenecen a un vuelo, hotel, etc.). */
    val tripFiles: List<ShareFile> = emptyList(),
) {
    val allFiles: List<ShareFile> get() = listOfNotNull(cover) + items.flatMap { it.files } + tripFiles
}

sealed interface ShareDecode {
    data class Ok(val pkg: SharePackage) : ShareDecode
    data class Invalid(val reason: Reason) : ShareDecode
    enum class Reason { NOT_JSON, WRONG_FORMAT, TOO_NEW, MALFORMED, TOO_BIG }
}

object TripShareFormat {
    const val FORMAT = "travelindex-trip"
    const val VERSION = 1
    const val ENTRY_JSON = "trip.json"
    /** Tipo propio del archivo de un viaje: así Android sabe que lo abre Travel Index (por dentro va comprimido, pero se ve como ".travelindex"). */
    const val MIME = "application/vnd.travelindex.trip"
    const val EXTENSION = "travelindex"

    /** Nombre del archivo que se comparte: "Roma.travelindex" (sin acentos ni símbolos que den problemas al enviarlo). */
    fun fileNameFor(tripName: String): String {
        val plain = java.text.Normalizer.normalize(tripName, java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
        val safe = plain.replace(Regex("[^A-Za-z0-9 _-]"), "").trim().replace(' ', '_').take(40).ifEmpty { "viaje" }
        return "$safe.$EXTENSION"
    }

    // Límites contra paquetes malformados o hostiles.
    const val MAX_ITEMS = 2_000
    const val MAX_FILES = 1_000
    const val MAX_TEXT = 20_000
    const val MAX_FILE_BYTES = 64L * 1024 * 1024
    const val MAX_TOTAL_BYTES = 400L * 1024 * 1024

    /** Solo se aceptan nombres de entrada que nosotros mismos generamos: evita rutas tipo `../../x` (zip-slip). */
    private val ZIP_PATH = Regex("^files/[0-9]{1,6}(\\.[a-z0-9]{1,8})?$")
    fun isSafeZipPath(p: String): Boolean = ZIP_PATH.matches(p)

    /** Extensión segura (solo letras/dígitos, máx. 8) para el nombre dentro del ZIP. */
    fun safeExtension(displayName: String): String {
        val e = displayName.substringAfterLast('.', "").lowercase()
        return if (e.length in 1..8 && e.all { it in 'a'..'z' || it in '0'..'9' }) ".$e" else ""
    }

    /**
     * Construye el paquete a partir del viaje. [zipPathFor] da el nombre dentro del ZIP de cada archivo
     * (el gestor lo asigna y copia el contenido).
     */
    fun fromBundle(b: TripBundle, zipPathFor: (AttachmentEntity) -> String, coverZipPath: String?): SharePackage {
        fun file(a: AttachmentEntity) = ShareFile(zipPathFor(a), a.displayName, a.mimeType, a.sizeBytes, a.category)
        val items = ModuleType.entries.flatMap { type ->
            ModuleItems.of(type, b).map { mi ->
                val files = if (type.attachable)
                    b.attachments.filter { it.ownerType == type.name && it.ownerId == mi.id }.map(::file)
                else emptyList()
                ShareItem(type, mi.values, files)
            }
        }
        val loose = b.attachments.filter { it.ownerType == null }.map(::file)
        val cover = coverZipPath?.let { ShareFile(it, "cover", "image/jpeg", 0L, "PHOTO") }
        return SharePackage(TripCodec.values(b.trip), b.trip.cancelled, cover, items, loose)
    }

    fun encode(p: SharePackage): String {
        fun file(f: ShareFile) = mapOf(
            "path" to f.zipPath, "name" to f.displayName, "mime" to f.mimeType, "size" to f.sizeBytes, "category" to f.category,
        )
        val root = mapOf(
            "format" to FORMAT, "version" to VERSION.toLong(),
            "trip" to mapOf("values" to p.tripValues, "cancelled" to p.cancelled, "cover" to p.cover?.let(::file)),
            "items" to p.items.map { mapOf("type" to it.type.name, "values" to it.values, "files" to it.files.map(::file)) },
            "tripFiles" to p.tripFiles.map(::file),
        )
        return MiniJson.stringify(root)
    }

    fun decode(json: String): ShareDecode {
        val root = try { MiniJson.parseObject(json) } catch (e: IllegalArgumentException) {
            return ShareDecode.Invalid(ShareDecode.Reason.NOT_JSON)
        }
        if (root["format"] != FORMAT) return ShareDecode.Invalid(ShareDecode.Reason.WRONG_FORMAT)
        val version = (root["version"] as? Number)?.toInt() ?: return ShareDecode.Invalid(ShareDecode.Reason.MALFORMED)
        if (version > VERSION) return ShareDecode.Invalid(ShareDecode.Reason.TOO_NEW)

        val trip = root["trip"] as? Map<*, *> ?: return ShareDecode.Invalid(ShareDecode.Reason.MALFORMED)
        val tripValues = values(trip["values"]) ?: return ShareDecode.Invalid(ShareDecode.Reason.MALFORMED)
        val cover = (trip["cover"] as? Map<*, *>)?.let { file(it) ?: return ShareDecode.Invalid(ShareDecode.Reason.MALFORMED) }
        val rawItems = root["items"] as? List<*> ?: emptyList<Any?>()
        val rawLoose = root["tripFiles"] as? List<*> ?: emptyList<Any?>()
        if (rawItems.size > MAX_ITEMS || rawLoose.size > MAX_FILES) return ShareDecode.Invalid(ShareDecode.Reason.TOO_BIG)

        val items = ArrayList<ShareItem>()
        for (r in rawItems) {
            val m = r as? Map<*, *> ?: return ShareDecode.Invalid(ShareDecode.Reason.MALFORMED)
            val type = ModuleType.fromName(m["type"] as? String) ?: continue   // tipo desconocido (versión futura): se ignora
            val vals = values(m["values"]) ?: return ShareDecode.Invalid(ShareDecode.Reason.MALFORMED)
            val files = (m["files"] as? List<*>).orEmpty().map { file(it as? Map<*, *> ?: return ShareDecode.Invalid(ShareDecode.Reason.MALFORMED)) ?: return ShareDecode.Invalid(ShareDecode.Reason.MALFORMED) }
            items += ShareItem(type, vals, files)
        }
        val loose = rawLoose.map { file(it as? Map<*, *> ?: return ShareDecode.Invalid(ShareDecode.Reason.MALFORMED)) ?: return ShareDecode.Invalid(ShareDecode.Reason.MALFORMED) }
        val pkg = SharePackage(tripValues, trip["cancelled"] == true, cover, items, loose)
        val paths = pkg.allFiles.map { it.zipPath }
        if (paths.size > MAX_FILES) return ShareDecode.Invalid(ShareDecode.Reason.TOO_BIG)
        if (paths.any { !isSafeZipPath(it) }) return ShareDecode.Invalid(ShareDecode.Reason.MALFORMED)
        return ShareDecode.Ok(pkg)
    }

    /** Solo texto, de longitud razonable. */
    private fun values(raw: Any?): Map<String, String>? {
        val m = raw as? Map<*, *> ?: return null
        val out = LinkedHashMap<String, String>()
        for ((k, v) in m) {
            val key = k as? String ?: return null
            val text = when (v) { null -> ""; is String -> v; is Boolean, is Number -> v.toString(); else -> return null }
            if (key.length > 64 || text.length > MAX_TEXT) return null
            out[key] = text
        }
        return out
    }

    private fun file(m: Map<*, *>): ShareFile? {
        val path = m["path"] as? String ?: return null
        val name = (m["name"] as? String).orEmpty().take(200).ifBlank { "archivo" }
        val mime = (m["mime"] as? String).orEmpty().take(100).ifBlank { "application/octet-stream" }
        val size = (m["size"] as? Number)?.toLong() ?: 0L
        val category = (m["category"] as? String).orEmpty().take(40).ifBlank { "OTHER" }
        return ShareFile(path, name, mime, size.coerceAtLeast(0), category)
    }
}

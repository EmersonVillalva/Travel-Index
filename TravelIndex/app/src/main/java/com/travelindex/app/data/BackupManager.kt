package com.travelindex.app.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.travelindex.app.BuildConfig
import com.travelindex.app.data.db.TravelIndexDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Copia de seguridad = un ZIP con la base de datos SQLite + todos los archivos adjuntos.
 * Es el único respaldo: la copia automática de Android está desactivada para proteger los datos.
 * El archivo NO está cifrado: guárdalo en un sitio de confianza.
 */
@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: TravelIndexDatabase,
    private val files: FileStorage,
) {
    suspend fun export(target: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            // Vuelca el WAL al archivo principal para que la copia sea coherente.
            db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { it.moveToFirst() }
            val dbFile = context.getDatabasePath(TravelIndexDatabase.NAME)
            val out = context.contentResolver.openOutputStream(target, "w") ?: throw IOException("No se pudo crear el archivo")
            ZipOutputStream(BufferedOutputStream(out)).use { zip ->
                val manifest = "{\"app\":\"TravelIndex\",\"appVersion\":\"${BuildConfig.VERSION_NAME}\"," +
                    "\"dbVersion\":${TravelIndexDatabase.VERSION},\"createdAt\":${System.currentTimeMillis()}}"
                zip.putNextEntry(ZipEntry(MANIFEST)); zip.write(manifest.toByteArray()); zip.closeEntry()
                zip.putNextEntry(ZipEntry("db/${TravelIndexDatabase.NAME}"))
                dbFile.inputStream().use { it.copyTo(zip) }; zip.closeEntry()
                files.root.listFiles()?.filter { it.isFile }?.forEach { f ->
                    zip.putNextEntry(ZipEntry("files/${f.name}"))
                    f.inputStream().use { it.copyTo(zip) }; zip.closeEntry()
                }
            }
            Unit
        }
    }

    /**
     * Valida el ZIP ANTES de tocar nada y solo entonces sustituye los datos.
     * Tras éxito hay que llamar a [restartApp]: la base de datos queda cerrada.
     */
    suspend fun import(source: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val tmp = File(context.cacheDir, "restore").apply { deleteRecursively(); mkdirs() }
            try {
                val input = context.contentResolver.openInputStream(source) ?: throw IOException("No se pudo abrir el archivo")
                ZipInputStream(BufferedInputStream(input)).use { zin ->
                    var e: ZipEntry? = zin.nextEntry
                    while (e != null) {
                        val out = File(tmp, e.name).canonicalFile
                        // Protección contra rutas maliciosas dentro del ZIP.
                        if (!out.path.startsWith(tmp.canonicalPath + File.separator)) throw IOException("Archivo no válido")
                        if (e.isDirectory) out.mkdirs() else {
                            out.parentFile?.mkdirs()
                            out.outputStream().use { zin.copyTo(it) }
                        }
                        zin.closeEntry(); e = zin.nextEntry
                    }
                }
                val newDb = File(tmp, "db/${TravelIndexDatabase.NAME}")
                if (!newDb.exists()) throw IOException("La copia no contiene una base de datos de TravelIndex")
                val header = ByteArray(100)
                newDb.inputStream().use { if (it.read(header) < 100) throw IOException("Base de datos dañada") }
                if (String(header, 0, 15, Charsets.US_ASCII) != "SQLite format 3") throw IOException("Base de datos dañada")
                val version = ByteBuffer.wrap(header, 60, 4).int
                if (version < 1 || version > TravelIndexDatabase.VERSION) {
                    throw IOException("La copia es de una versión más nueva de TravelIndex")
                }

                // 1) Todo el trabajo pesado se hace ANTES de tocar los datos actuales: si algo falla aquí, no se pierde nada.
                val dbFile = context.getDatabasePath(TravelIndexDatabase.NAME)
                val stagedDb = File(dbFile.path + ".new")
                val stagedFiles = File(files.root.parentFile, FileStorage.DIR + ".new")
                stagedDb.delete(); stagedFiles.deleteRecursively()
                try {
                    newDb.copyTo(stagedDb, overwrite = true)
                    stagedFiles.mkdirs()
                    File(tmp, "files").listFiles()?.forEach { it.copyTo(File(stagedFiles, it.name), overwrite = true) }
                } catch (t: Throwable) {
                    stagedDb.delete(); stagedFiles.deleteRecursively(); throw t
                }

                // 2) Intercambio mediante renombrados (rápidos); si falla, se deshace.
                val filesDir = files.root
                val oldDb = File(dbFile.path + ".old")
                val oldFiles = File(filesDir.parentFile, FileStorage.DIR + ".old")
                oldDb.delete(); oldFiles.deleteRecursively()
                db.close()
                var dbMoved = false
                var filesMoved = false
                try {
                    listOf(File(dbFile.path + "-wal"), File(dbFile.path + "-shm")).forEach { it.delete() }
                    if (dbFile.exists()) { if (!dbFile.renameTo(oldDb)) throw IOException("No se pudo reemplazar la base de datos"); dbMoved = true }
                    if (!stagedDb.renameTo(dbFile)) throw IOException("No se pudo reemplazar la base de datos")
                    if (filesDir.exists()) { if (!filesDir.renameTo(oldFiles)) throw IOException("No se pudieron reemplazar los archivos"); filesMoved = true }
                    if (!stagedFiles.renameTo(filesDir)) throw IOException("No se pudieron reemplazar los archivos")
                } catch (t: Throwable) {
                    // Deshacer: se recupera lo que había antes.
                    if (filesMoved && !filesDir.exists()) oldFiles.renameTo(filesDir)
                    if (dbMoved) { dbFile.delete(); oldDb.renameTo(dbFile) }
                    stagedDb.delete(); stagedFiles.deleteRecursively()
                    throw t
                }
                oldDb.delete(); oldFiles.deleteRecursively()
            } finally {
                tmp.deleteRecursively()
            }
            Unit
        }
    }

    /** true si la base de datos quedó cerrada (hay que reiniciar la app aunque la restauración haya fallado). */
    val databaseClosed: Boolean get() = !db.isOpen

    /** Reinicia el proceso para abrir la base de datos restaurada. */
    fun restartApp() {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            context.startActivity(intent)
        }
        Runtime.getRuntime().exit(0)
    }

    companion object { private const val MANIFEST = "manifest.json" }
}

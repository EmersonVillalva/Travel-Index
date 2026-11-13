package com.travelindex.app.data.repo

import android.net.Uri
import com.travelindex.app.data.FileStorage
import com.travelindex.app.data.StoredFile
import com.travelindex.app.data.db.AttachmentDao
import com.travelindex.app.data.db.AttachmentEntity
import com.travelindex.app.domain.AttachmentCategory
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AttachmentRepository @Inject constructor(
    private val dao: AttachmentDao,
    private val files: FileStorage,
) {
    /** Copia el archivo al almacenamiento privado y lo registra. Si falla el registro, borra la copia. */
    suspend fun add(tripId: Long, uri: Uri, ownerType: String?, ownerId: Long?, category: AttachmentCategory? = null): Long {
        val stored = files.import(uri)
        return try {
            dao.insert(
                AttachmentEntity(
                    tripId = tripId, ownerType = ownerType, ownerId = ownerId,
                    displayName = stored.displayName, relativePath = stored.relativePath,
                    mimeType = stored.mimeType, sizeBytes = stored.sizeBytes,
                    category = (category ?: AttachmentCategory.defaultFor(ownerType, stored.mimeType)).name,
                )
            )
        } catch (t: Throwable) {
            files.delete(stored.relativePath); throw t
        }
    }

    /** Registra un archivo que ya está en el almacenamiento privado (viaje importado). */
    suspend fun addStored(tripId: Long, ownerType: String?, ownerId: Long?, stored: StoredFile, category: String): Long =
        dao.insert(
            AttachmentEntity(
                tripId = tripId, ownerType = ownerType, ownerId = ownerId,
                displayName = stored.displayName, relativePath = stored.relativePath,
                mimeType = stored.mimeType, sizeBytes = stored.sizeBytes,
                category = AttachmentCategory.fromName(category).name,
            )
        )

    suspend fun update(item: AttachmentEntity) = dao.update(item)

    suspend fun delete(item: AttachmentEntity) {
        dao.delete(item)
        files.delete(item.relativePath)
    }
}

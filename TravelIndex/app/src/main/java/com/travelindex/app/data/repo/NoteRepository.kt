package com.travelindex.app.data.repo

import com.travelindex.app.data.db.NoteItemDao
import com.travelindex.app.data.db.NoteItemEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NoteRepository @Inject constructor(private val dao: NoteItemDao) {
    suspend fun add(item: NoteItemEntity): Long = dao.insert(item)
    suspend fun update(item: NoteItemEntity) = dao.update(item)
    suspend fun delete(id: Long) = dao.deleteById(id)
}

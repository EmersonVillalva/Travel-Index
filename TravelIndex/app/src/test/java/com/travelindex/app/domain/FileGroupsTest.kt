package com.travelindex.app.domain

import com.travelindex.app.data.db.AttachmentEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FileGroupsTest {
    private fun file(id: Long, owner: String?, ownerId: Long? = null, at: Long = id) = AttachmentEntity(
        id = id, tripId = 1, ownerType = owner, ownerId = ownerId, displayName = "f$id.pdf",
        relativePath = "p$id", mimeType = "application/pdf", sizeBytes = 10, createdAt = at,
    )

    @Test fun `sin archivos no hay apartados`() {
        assertTrue(FileGroups.group(emptyList()).isEmpty())
    }

    @Test fun `cada archivo va al apartado de su reserva y los vacios no salen`() {
        val groups = FileGroups.group(listOf(
            file(1, "TRAVEL_DOC", 9), file(2, "FLIGHT", 1), file(3, "LODGING", 4), file(4, null), file(5, "ACTIVITY", 2), file(6, "RESTAURANT", 3),
        ))
        assertEquals(
            listOf(FileGroup.FLIGHTS, FileGroup.LODGING, FileGroup.ACTIVITIES, FileGroup.RESTAURANTS, FileGroup.DOCUMENTS, FileGroup.OTHER),
            groups.map { it.group },
        )
    }

    @Test fun `el orden de los apartados es fijo aunque los archivos lleguen revueltos`() {
        val a = FileGroups.group(listOf(file(1, null), file(2, "FLIGHT", 1))).map { it.group }
        val b = FileGroups.group(listOf(file(2, "FLIGHT", 1), file(1, null))).map { it.group }
        assertEquals(listOf(FileGroup.FLIGHTS, FileGroup.OTHER), a)
        assertEquals(a, b)
    }

    @Test fun `los transportes se separan por tipo`() {
        val types = mapOf(1L to "TRAIN", 2L to "BUS", 3L to "FERRY", 4L to "CAR_RENTAL", 5L to "TAXI")
        val groups = FileGroups.group((1L..5L).map { file(it, "TRANSPORT", it) }, types)
        assertEquals(
            listOf(FileGroup.TRAINS, FileGroup.BUSES, FileGroup.SHIPS, FileGroup.CARS, FileGroup.TRANSPORT_OTHER),
            groups.map { it.group },
        )
    }

    @Test fun `un transporte que ya no existe o de tipo desconocido va a otros transportes`() {
        assertEquals(FileGroup.TRANSPORT_OTHER, FileGroups.of(file(1, "TRANSPORT", 99), mapOf(1L to "TRAIN")))
        assertEquals(FileGroup.TRANSPORT_OTHER, FileGroups.of(file(1, "TRANSPORT", null)))
    }

    @Test fun `un propietario desconocido va a otros`() {
        assertEquals(FileGroup.OTHER, FileGroups.of(file(1, "EXPENSE", 1)))
        assertEquals(FileGroup.OTHER, FileGroups.of(file(1, "COSA_RARA", 1)))
    }

    @Test fun `los archivos de una misma reserva quedan juntos y por orden de llegada`() {
        val g = FileGroups.group(listOf(
            file(1, "FLIGHT", 20, at = 100), file(2, "FLIGHT", 10, at = 300), file(3, "FLIGHT", 20, at = 50), file(4, "FLIGHT", 10, at = 200),
        )).single()
        assertEquals(listOf(4L, 2L, 3L, 1L), g.files.map { it.id })
    }
}

package com.travelindex.app.domain

import com.travelindex.app.data.db.AttachmentEntity

/** Apartados en que se agrupan los archivos de un viaje, en el orden en que se enseñan. */
enum class FileGroup { FLIGHTS, TRAINS, BUSES, SHIPS, CARS, TRANSPORT_OTHER, LODGING, ACTIVITIES, RESTAURANTS, DOCUMENTS, OTHER }

/** Los archivos de un apartado (un vuelo, un hotel...), ya ordenados. Un apartado sin archivos no aparece. */
data class FileGroupEntry(val group: FileGroup, val files: List<AttachmentEntity>)

/**
 * Agrupa los archivos de un viaje según a qué pertenecen: vuelos, trenes, alojamientos, visados... Los que no se han
 * asociado a nada van aparte, en "otros". Dentro de cada apartado los archivos de una misma reserva quedan juntos y,
 * dentro de ella, por orden de llegada.
 */
object FileGroups {
    /**
     * [transportTypeById] dice de qué tipo es cada transporte del viaje ("TRAIN", "BUS"...) para separar trenes,
     * autobuses, barcos y coches; sin él (o con un tipo desconocido) el archivo va a "otros transportes".
     */
    fun of(att: AttachmentEntity, transportTypeById: Map<Long, String> = emptyMap()): FileGroup = when (att.ownerType) {
        ModuleType.FLIGHT.name -> FileGroup.FLIGHTS
        ModuleType.TRANSPORT.name -> when (TransportSection.forTransport(att.ownerId?.let { transportTypeById[it] })) {
            TransportSection.TRAINS -> FileGroup.TRAINS
            TransportSection.BUSES -> FileGroup.BUSES
            TransportSection.SHIPS -> FileGroup.SHIPS
            TransportSection.CARS -> FileGroup.CARS
            else -> FileGroup.TRANSPORT_OTHER
        }
        ModuleType.LODGING.name -> FileGroup.LODGING
        ModuleType.ACTIVITY.name -> FileGroup.ACTIVITIES
        ModuleType.RESTAURANT.name -> FileGroup.RESTAURANTS
        ModuleType.TRAVEL_DOC.name -> FileGroup.DOCUMENTS
        else -> FileGroup.OTHER
    }

    fun group(files: List<AttachmentEntity>, transportTypeById: Map<Long, String> = emptyMap()): List<FileGroupEntry> {
        val byGroup = files.groupBy { of(it, transportTypeById) }
        return FileGroup.entries.mapNotNull { g ->
            val list = byGroup[g] ?: return@mapNotNull null
            FileGroupEntry(g, list.sortedWith(compareBy<AttachmentEntity> { it.ownerId ?: 0L }.thenBy { it.createdAt }.thenBy { it.id }))
        }
    }
}

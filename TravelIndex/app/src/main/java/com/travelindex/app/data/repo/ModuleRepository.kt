package com.travelindex.app.data.repo

import com.travelindex.app.data.db.ActivityDao
import com.travelindex.app.data.db.AttachmentDao
import com.travelindex.app.data.db.DestinationDao
import com.travelindex.app.data.db.ExpenseDao
import com.travelindex.app.data.db.FlightDao
import com.travelindex.app.data.db.FlightEntity
import com.travelindex.app.data.db.LodgingDao
import com.travelindex.app.data.db.RestaurantDao
import com.travelindex.app.data.db.TransportDao
import com.travelindex.app.data.db.TravelIndexDatabase
import com.travelindex.app.data.db.TravelDocDao
import androidx.room.withTransaction
import com.travelindex.app.data.FileStorage
import com.travelindex.app.domain.ModuleType
import com.travelindex.app.domain.forms.ActivityCodec
import com.travelindex.app.domain.forms.DestinationCodec
import com.travelindex.app.domain.forms.ExpenseCodec
import com.travelindex.app.domain.forms.FlightCodec
import com.travelindex.app.domain.forms.FormValues
import com.travelindex.app.domain.forms.LodgingCodec
import com.travelindex.app.domain.forms.RestaurantCodec
import com.travelindex.app.domain.forms.TransportCodec
import com.travelindex.app.domain.forms.TravelDocCodec
import javax.inject.Inject
import javax.inject.Singleton

/** Alta, edición y baja de cualquier elemento de un viaje (vuelo, alojamiento, gasto...). */
@Singleton
class ModuleRepository @Inject constructor(
    private val flights: FlightDao,
    private val lodgings: LodgingDao,
    private val activities: ActivityDao,
    private val transports: TransportDao,
    private val restaurants: RestaurantDao,
    private val docs: TravelDocDao,
    private val expenses: ExpenseDao,
    private val destinations: DestinationDao,
    private val attachments: AttachmentDao,
    private val files: FileStorage,
    private val db: TravelIndexDatabase,
) {
    /** [id] = 0 crea un elemento nuevo; otro valor lo actualiza. Devuelve el id resultante. Los valores ya deben estar validados. */
    suspend fun save(type: ModuleType, tripId: Long, id: Long, v: FormValues): Long = when (type) {
        ModuleType.FLIGHT -> FlightCodec.build(tripId, id, v).let { if (id == 0L) flights.insert(it) else { flights.update(it); id } }
        ModuleType.LODGING -> LodgingCodec.build(tripId, id, v).let { if (id == 0L) lodgings.insert(it) else { lodgings.update(it); id } }
        ModuleType.ACTIVITY -> ActivityCodec.build(tripId, id, v).let { if (id == 0L) activities.insert(it) else { activities.update(it); id } }
        ModuleType.TRANSPORT -> TransportCodec.build(tripId, id, v).let { if (id == 0L) transports.insert(it) else { transports.update(it); id } }
        ModuleType.RESTAURANT -> RestaurantCodec.build(tripId, id, v).let { if (id == 0L) restaurants.insert(it) else { restaurants.update(it); id } }
        ModuleType.TRAVEL_DOC -> TravelDocCodec.build(tripId, id, v).let { if (id == 0L) docs.insert(it) else { docs.update(it); id } }
        ModuleType.EXPENSE -> ExpenseCodec.build(tripId, id, v).let { if (id == 0L) expenses.insert(it) else { expenses.update(it); id } }
        ModuleType.DESTINATION -> DestinationCodec.build(tripId, id, v, 0).let { if (id == 0L) destinations.insert(it) else { destinations.update(it); id } }
    }

    /**
     * Guarda de una vez todos los vuelos de un viaje con escalas. [legs] son los vuelos que quedan (los que tienen id ya existían
     * y se actualizan, los de id 0 son nuevos) y [existing] los que había guardados antes: los que ya no están en [legs] se
     * borran con sus archivos adjuntos. Todo o nada. Devuelve el id del primer vuelo.
     */
    suspend fun saveFlightLegs(existing: List<FlightEntity>, legs: List<FlightEntity>): Long {
        val kept = legs.mapNotNull { it.id.takeIf { id -> id != 0L } }.toSet()
        val removed = existing.filter { it.id !in kept }
        val orphanFiles = ArrayList<String>()
        val firstId = db.withTransaction {
            var first = 0L
            legs.forEachIndexed { i, leg ->
                val id = if (leg.id == 0L) flights.insert(leg) else { flights.update(leg); leg.id }
                if (i == 0) first = id
            }
            removed.forEach { gone ->
                attachments.forOwner(ModuleType.FLIGHT.name, gone.id).forEach { orphanFiles += it.relativePath }
                attachments.deleteForOwner(ModuleType.FLIGHT.name, gone.id)
                flights.deleteById(gone.id)
            }
            first
        }
        // Los archivos se borran al final, cuando la base de datos ya quedó guardada.
        orphanFiles.forEach { files.delete(it) }
        return firstId
    }

    /** Borra el elemento y los archivos adjuntos asociados a él. */
    suspend fun delete(type: ModuleType, id: Long) {
        if (type.attachable) {
            attachments.forOwner(type.name, id).forEach { files.delete(it.relativePath) }
            attachments.deleteForOwner(type.name, id)
        }
        when (type) {
            ModuleType.FLIGHT -> flights.deleteById(id)
            ModuleType.LODGING -> lodgings.deleteById(id)
            ModuleType.ACTIVITY -> activities.deleteById(id)
            ModuleType.TRANSPORT -> transports.deleteById(id)
            ModuleType.RESTAURANT -> restaurants.deleteById(id)
            ModuleType.TRAVEL_DOC -> docs.deleteById(id)
            ModuleType.EXPENSE -> expenses.deleteById(id)
            ModuleType.DESTINATION -> destinations.deleteById(id)
        }
    }
}

package com.travelindex.app.data.repo

import com.travelindex.app.data.db.ActivityDao
import com.travelindex.app.data.db.AttachmentDao
import com.travelindex.app.data.db.DestinationDao
import com.travelindex.app.data.db.ExpenseDao
import com.travelindex.app.data.db.FlightDao
import com.travelindex.app.data.db.LodgingDao
import com.travelindex.app.data.db.RestaurantDao
import com.travelindex.app.data.db.TransportDao
import com.travelindex.app.data.db.TravelDocDao
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

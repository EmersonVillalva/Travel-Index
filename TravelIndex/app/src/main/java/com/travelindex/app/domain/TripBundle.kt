package com.travelindex.app.domain

import com.travelindex.app.data.db.ActivityEntity
import com.travelindex.app.data.db.AttachmentEntity
import com.travelindex.app.data.db.DestinationEntity
import com.travelindex.app.data.db.ExpenseEntity
import com.travelindex.app.data.db.FlightEntity
import com.travelindex.app.data.db.LodgingEntity
import com.travelindex.app.data.db.NoteItemEntity
import com.travelindex.app.data.db.RestaurantEntity
import com.travelindex.app.data.db.TransportEntity
import com.travelindex.app.data.db.TravelDocEntity
import com.travelindex.app.data.db.TripEntity
import java.time.LocalDate

/** Un viaje con todo su contenido. Es la única fuente de verdad de las pantallas: todo se deriva de aquí. */
data class TripBundle(
    val trip: TripEntity,
    val destinations: List<DestinationEntity> = emptyList(),
    val flights: List<FlightEntity> = emptyList(),
    val lodgings: List<LodgingEntity> = emptyList(),
    val activities: List<ActivityEntity> = emptyList(),
    val transports: List<TransportEntity> = emptyList(),
    val restaurants: List<RestaurantEntity> = emptyList(),
    val docs: List<TravelDocEntity> = emptyList(),
    val notes: List<NoteItemEntity> = emptyList(),
    val expenses: List<ExpenseEntity> = emptyList(),
    val attachments: List<AttachmentEntity> = emptyList(),
) {
    /**
     * Eventos derivados (vuelos, check-in/out, actividades, transportes, restaurantes).
     * Las notas heredadas de versiones anteriores siguen en la base de datos, pero la app ya no las muestra
     * ni genera eventos con ellas.
     */
    val events: List<ItineraryEvent> by lazy {
        ItineraryBuilder.events(flights, lodgings, activities, transports, restaurants)
    }

    val reservationsCount: Int
        get() = flights.size + lodgings.size + activities.size + transports.size + restaurants.size

    fun status(today: LocalDate): TripStatus =
        tripStatus(trip.startDate, trip.endDate, trip.cancelled, today)

    fun count(type: ModuleType): Int = when (type) {
        ModuleType.FLIGHT -> flights.size
        ModuleType.LODGING -> lodgings.size
        ModuleType.ACTIVITY -> activities.size
        ModuleType.TRANSPORT -> transports.size
        ModuleType.RESTAURANT -> restaurants.size
        ModuleType.TRAVEL_DOC -> docs.size
        ModuleType.EXPENSE -> expenses.size
        ModuleType.DESTINATION -> destinations.size
    }
}

enum class ModuleType { FLIGHT, LODGING, ACTIVITY, TRANSPORT, RESTAURANT, TRAVEL_DOC, EXPENSE, DESTINATION;

    /** Solo estos módulos admiten archivos adjuntos. */
    val attachable: Boolean get() = this != EXPENSE && this != DESTINATION

    companion object {
        fun fromName(name: String?): ModuleType? = entries.firstOrNull { it.name == name }
    }
}

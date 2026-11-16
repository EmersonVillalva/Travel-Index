package com.travelindex.app.data

import com.travelindex.app.data.db.NoteItemEntity
import com.travelindex.app.data.db.TripEntity
import com.travelindex.app.data.repo.ModuleRepository
import com.travelindex.app.data.repo.NoteRepository
import com.travelindex.app.data.repo.TripRepository
import com.travelindex.app.domain.ModuleType
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/** Viaje de ejemplo OPCIONAL (se crea solo si el usuario lo pide y se borra como cualquier otro viaje). */
@Singleton
class DemoData @Inject constructor(
    private val trips: TripRepository,
    private val modules: ModuleRepository,
    private val notes: NoteRepository,
) {
    suspend fun createRome(today: LocalDate, deviceZone: ZoneId, currency: String): Long {
        val start = today.plusDays(14)
        val end = start.plusDays(5)
        val s = start.toString()
        val id = trips.createTrip(
            TripEntity(
                name = "Ejemplo · Roma", city = "Roma", country = "Italia", startDate = start, endDate = end,
                travelers = 2, description = "Viaje de ejemplo para probar TravelIndex. Puedes borrarlo cuando quieras.",
                budgetMinor = 150_000, currency = currency,
            )
        )
        val dz = deviceZone.id
        modules.save(
            ModuleType.FLIGHT, id, 0,
            mapOf(
                "airline" to "Ejemplo Air", "flightNumber" to "EX101", "departureAirport" to "MAD", "arrivalAirport" to "FCO",
                "depDate" to s, "depTime" to "11:45", "depZone" to "Europe/Madrid",
                "arrDate" to s, "arrTime" to "14:15", "arrZone" to "Europe/Rome",
                "price" to "189.90", "currency" to currency, "status" to "CONFIRMED", "cabinClass" to "ECONOMY",
            ),
        )
        modules.save(
            ModuleType.LODGING, id, 0,
            mapOf(
                "name" to "Hotel de ejemplo", "address" to "Via del Corso 1, Roma", "city" to "Roma", "country" to "Italia",
                "checkInDate" to s, "checkInTime" to "16:00", "checkOutDate" to end.toString(), "checkOutTime" to "11:00",
                "timeZone" to "Europe/Rome", "guests" to "2", "price" to "540", "currency" to currency,
            ),
        )
        modules.save(
            ModuleType.ACTIVITY, id, 0,
            mapOf(
                "name" to "Visita al Coliseo", "location" to "Piazza del Colosseo, Roma", "date" to start.plusDays(1).toString(),
                "startTime" to "10:00", "duration" to "150", "timeZone" to "Europe/Rome", "participants" to "2",
                "price" to "32", "currency" to currency,
            ),
        )
        modules.save(
            ModuleType.RESTAURANT, id, 0,
            mapOf(
                "name" to "Trattoria de ejemplo", "address" to "Via Margutta 10, Roma", "date" to s, "time" to "20:30",
                "timeZone" to "Europe/Rome", "people" to "2", "price" to "60", "currency" to currency,
            ),
        )
        modules.save(
            ModuleType.TRANSPORT, id, 0,
            mapOf(
                "type" to "TRAIN", "company" to "Tren de ejemplo", "startDate" to start.plusDays(2).toString(),
                "startTime" to "09:15", "endDate" to start.plusDays(2).toString(), "endTime" to "10:45",
                "timeZone" to "Europe/Rome", "originPlace" to "Roma Termini", "destinationPlace" to "Florencia SMN",
                "price" to "29", "currency" to currency,
            ),
        )
        modules.save(
            ModuleType.TRAVEL_DOC, id, 0,
            mapOf("docType" to "INSURANCE", "title" to "Seguro de viaje (ejemplo)", "status" to "APPROVED",
                "expiryDate" to end.plusDays(30).toString()),
        )
        modules.save(
            ModuleType.EXPENSE, id, 0,
            mapOf("concept" to "Cena de bienvenida", "category" to "FOOD", "amount" to "58.40", "currency" to currency,
                "date" to s, "paymentMethod" to "CARD"),
        )
        notes.add(NoteItemEntity(tripId = id, kind = "PACKING", text = "Pasaporte / DNI"))
        notes.add(NoteItemEntity(tripId = id, kind = "PACKING", text = "Cargador y adaptador"))
        notes.add(NoteItemEntity(tripId = id, kind = "TODO", text = "Hacer check-in online", dueDate = start.minusDays(1)))
        notes.add(NoteItemEntity(tripId = id, kind = "SHOPPING", text = "Recuerdos para la familia"))
        return id
    }
}

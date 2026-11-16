package com.travelindex.app.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.LocalTime

/** Los importes se guardan en unidades menores (céntimos) para evitar errores de coma flotante. */

@Entity(tableName = "trips")
data class TripEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val city: String,
    val country: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val travelers: Int = 1,
    val coverPath: String? = null,
    val description: String = "",
    val budgetMinor: Long? = null,
    val currency: String = "EUR",
    val notes: String = "",
    val cancelled: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    /** Medios de transporte del viaje, claves separadas por comas ("PLANE,CAR"); vacío si no se eligió ninguno. */
    val travelModes: String = "",
)

@Entity(
    tableName = "trip_destinations",
    foreignKeys = [ForeignKey(entity = TripEntity::class, parentColumns = ["id"], childColumns = ["tripId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("tripId")],
)
data class DestinationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val city: String,
    val country: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val timeZoneId: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val position: Int = 0,
)

@Entity(
    tableName = "flights",
    foreignKeys = [ForeignKey(entity = TripEntity::class, parentColumns = ["id"], childColumns = ["tripId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("tripId")],
)
data class FlightEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val airline: String,
    val flightNumber: String,
    val departureAirport: String,
    val arrivalAirport: String,
    val terminal: String = "",
    /** Instante absoluto (UTC, epoch ms) + zona horaria local del aeropuerto. */
    val departureEpochMs: Long,
    val departureZoneId: String,
    val arrivalEpochMs: Long,
    val arrivalZoneId: String,
    val locator: String = "",
    val ticketNumber: String = "",
    val seat: String = "",
    val cabinClass: String = "",
    val baggage: String = "",
    val priceMinor: Long? = null,
    val currency: String = "EUR",
    val status: String = "CONFIRMED",
    val notes: String = "",
)

@Entity(
    tableName = "lodgings",
    foreignKeys = [ForeignKey(entity = TripEntity::class, parentColumns = ["id"], childColumns = ["tripId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("tripId")],
)
data class LodgingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val name: String,
    val address: String = "",
    val city: String = "",
    val country: String = "",
    val checkInDate: LocalDate,
    val checkOutDate: LocalDate,
    val checkInTime: LocalTime? = null,
    val checkOutTime: LocalTime? = null,
    val timeZoneId: String,
    val guests: Int = 1,
    val roomType: String = "",
    val reservationNumber: String = "",
    val priceMinor: Long? = null,
    val currency: String = "EUR",
    val phone: String = "",
    val website: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val notes: String = "",
)

@Entity(
    tableName = "activities",
    foreignKeys = [ForeignKey(entity = TripEntity::class, parentColumns = ["id"], childColumns = ["tripId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("tripId")],
)
data class ActivityEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val name: String,
    val description: String = "",
    val location: String = "",
    val date: LocalDate,
    val startTime: LocalTime? = null,
    /** Solo actividades guardadas con la versión 0.3 o anterior; desde la 0.4 se guarda la duración y la hora de fin se calcula. */
    val endTime: LocalTime? = null,
    val timeZoneId: String,
    val organizer: String = "",
    val meetingAddress: String = "",
    val priceMinor: Long? = null,
    val currency: String = "EUR",
    val participants: Int = 1,
    val locator: String = "",
    val bookingUrl: String = "",
    val notes: String = "",
    /** Duración en minutos (opcional). Con la hora de inicio, la app calcula a qué hora termina. */
    val durationMinutes: Int? = null,
)

@Entity(
    tableName = "expenses",
    foreignKeys = [ForeignKey(entity = TripEntity::class, parentColumns = ["id"], childColumns = ["tripId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("tripId")],
)
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val concept: String,
    val category: String,
    val amountMinor: Long,
    val currency: String,
    val date: LocalDate,
    val paymentMethod: String = "",
    val notes: String = "",
)

/**
 * Archivo adjunto. El contenido se COPIA al almacenamiento privado de la app (filesDir),
 * nunca se guarda solo la URI del selector, que puede perder el permiso.
 */
@Entity(
    tableName = "attachments",
    foreignKeys = [ForeignKey(entity = TripEntity::class, parentColumns = ["id"], childColumns = ["tripId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("tripId"), Index("ownerType", "ownerId")],
)
data class AttachmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val ownerType: String? = null,   // FLIGHT, LODGING, ACTIVITY... o null = documento suelto
    val ownerId: Long? = null,
    val displayName: String,
    val relativePath: String,        // relativo a filesDir/attachments
    val mimeType: String,
    val sizeBytes: Long,
    val category: String = "OTHER",
    val createdAt: Long = System.currentTimeMillis(),
)

// ------------------------------------------------------------------------------------------------
// Esquema v2: nuevas tablas. Las tablas de la v1 no se modifican.
// ------------------------------------------------------------------------------------------------

@Entity(
    tableName = "transports",
    foreignKeys = [ForeignKey(entity = TripEntity::class, parentColumns = ["id"], childColumns = ["tripId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("tripId")],
)
data class TransportEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    /** CAR_RENTAL, TRAIN, BUS, FERRY, TRANSFER, TAXI, OTHER */
    val type: String,
    val company: String = "",
    val model: String = "",
    val plate: String = "",
    val startDate: LocalDate,
    val startTime: LocalTime? = null,
    val endDate: LocalDate? = null,
    val endTime: LocalTime? = null,
    val timeZoneId: String,
    val originPlace: String = "",
    val destinationPlace: String = "",
    val address: String = "",
    val reservationNumber: String = "",
    val priceMinor: Long? = null,
    val currency: String = "EUR",
    val insurance: String = "",
    val mileage: String = "",
    val fuel: String = "",
    val conditions: String = "",
    val phone: String = "",
    val notes: String = "",
)

@Entity(
    tableName = "restaurants",
    foreignKeys = [ForeignKey(entity = TripEntity::class, parentColumns = ["id"], childColumns = ["tripId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("tripId")],
)
data class RestaurantEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val name: String,
    val address: String = "",
    val date: LocalDate,
    val time: LocalTime? = null,
    val timeZoneId: String,
    val people: Int = 2,
    val reservationNumber: String = "",
    val priceMinor: Long? = null,
    val currency: String = "EUR",
    val url: String = "",
    val phone: String = "",
    val notes: String = "",
)

@Entity(
    tableName = "travel_docs",
    foreignKeys = [ForeignKey(entity = TripEntity::class, parentColumns = ["id"], childColumns = ["tripId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("tripId")],
)
data class TravelDocEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    /** VISA, PASSPORT, ID_CARD, ENTRY_PERMIT, ETA, INSURANCE, OTHER */
    val docType: String,
    val title: String = "",
    val issuingCountry: String = "",
    val issueDate: LocalDate? = null,
    val expiryDate: LocalDate? = null,
    /** Dato sensible: en pantalla se muestra enmascarado. */
    val reference: String = "",
    val approvalDate: LocalDate? = null,
    /** PENDING, APPLIED, APPROVED, REJECTED */
    val status: String = "APPROVED",
    val notes: String = "",
)

@Entity(
    tableName = "note_items",
    foreignKeys = [ForeignKey(entity = TripEntity::class, parentColumns = ["id"], childColumns = ["tripId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("tripId")],
)
data class NoteItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    /** NOTE, PACKING, TODO, REMINDER, SHOPPING, INFO */
    val kind: String,
    val text: String,
    val body: String = "",
    val done: Boolean = false,
    val dueDate: LocalDate? = null,
    val dueTime: LocalTime? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

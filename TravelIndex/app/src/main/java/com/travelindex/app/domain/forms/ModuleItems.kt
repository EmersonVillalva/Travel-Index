package com.travelindex.app.domain.forms

import com.travelindex.app.domain.ModuleType
import com.travelindex.app.domain.TripBundle
import java.time.LocalDate
import java.time.ZoneId

/** Un elemento de un módulo (vuelo, hotel...) en forma de valores de formulario. */
data class ModuleItem(val id: Long, val values: FormValues)

object ModuleItems {
    fun of(type: ModuleType, b: TripBundle): List<ModuleItem> = when (type) {
        ModuleType.FLIGHT -> b.flights.map { ModuleItem(it.id, FlightCodec.values(it)) }
        ModuleType.LODGING -> b.lodgings.map { ModuleItem(it.id, LodgingCodec.values(it)) }
        ModuleType.ACTIVITY -> b.activities.map { ModuleItem(it.id, ActivityCodec.values(it)) }
        ModuleType.TRANSPORT -> b.transports.map { ModuleItem(it.id, TransportCodec.values(it)) }
        ModuleType.RESTAURANT -> b.restaurants.map { ModuleItem(it.id, RestaurantCodec.values(it)) }
        ModuleType.TRAVEL_DOC -> b.docs.map { ModuleItem(it.id, TravelDocCodec.values(it)) }
        ModuleType.EXPENSE -> b.expenses.map { ModuleItem(it.id, ExpenseCodec.values(it)) }
        ModuleType.DESTINATION -> b.destinations.map { ModuleItem(it.id, DestinationCodec.values(it)) }
    }
}

/** Valores iniciales de un formulario nuevo, adaptados al viaje (fechas del viaje, moneda del viaje...). */
object FormDefaults {
    fun forNew(type: ModuleType, b: TripBundle, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): FormValues {
        val start = b.trip.startDate.toString()
        val end = b.trip.endDate.toString()
        val cur = b.trip.currency
        val z = zone.id
        return when (type) {
            ModuleType.FLIGHT -> mapOf(
                "depDate" to start, "arrDate" to start, "depZone" to z, "arrZone" to z,
                "currency" to cur, "status" to "CONFIRMED", "cabinClass" to "ECONOMY",
            )
            ModuleType.LODGING -> mapOf(
                "checkInDate" to start, "checkOutDate" to end, "timeZone" to z,
                "guests" to b.trip.travelers.toString(), "currency" to cur, "city" to b.trip.city, "country" to b.trip.country,
            )
            ModuleType.ACTIVITY -> mapOf(
                "date" to start, "timeZone" to z, "participants" to b.trip.travelers.toString(), "currency" to cur,
            )
            ModuleType.TRANSPORT -> mapOf("type" to "CAR_RENTAL", "startDate" to start, "timeZone" to z, "currency" to cur)
            ModuleType.RESTAURANT -> mapOf(
                "date" to start, "timeZone" to z, "people" to b.trip.travelers.toString(), "currency" to cur,
            )
            ModuleType.TRAVEL_DOC -> mapOf("docType" to "VISA", "status" to "APPROVED")
            ModuleType.EXPENSE -> mapOf(
                "date" to today.coerceIn(b.trip.startDate, b.trip.endDate).toString(),
                "currency" to cur, "category" to "OTHER", "paymentMethod" to "CARD",
            )
            ModuleType.DESTINATION -> mapOf("startDate" to start, "endDate" to end, "timeZone" to z)
        }
    }

    private fun LocalDate.coerceIn(min: LocalDate, max: LocalDate): LocalDate =
        if (this.isBefore(min)) min else if (this.isAfter(max)) max else this
}

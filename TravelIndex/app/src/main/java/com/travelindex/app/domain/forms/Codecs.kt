package com.travelindex.app.domain.forms

import com.travelindex.app.data.db.ActivityEntity
import com.travelindex.app.data.db.DestinationEntity
import com.travelindex.app.data.db.ExpenseEntity
import com.travelindex.app.data.db.FlightEntity
import com.travelindex.app.data.db.LodgingEntity
import com.travelindex.app.data.db.RestaurantEntity
import com.travelindex.app.data.db.TransportEntity
import com.travelindex.app.data.db.TravelDocEntity
import com.travelindex.app.domain.ModuleType
import com.travelindex.app.domain.Money
import com.travelindex.app.domain.formatPlain
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

// ---- Ayudas de conversión (texto ↔ tipos) -------------------------------------------------------
internal fun FormValues.str(k: String): String = this[k].orEmpty().trim()
internal fun FormValues.date(k: String): LocalDate? =
    this[k]?.trim()?.takeIf { it.isNotEmpty() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
internal fun FormValues.time(k: String): LocalTime? =
    this[k]?.trim()?.takeIf { it.isNotEmpty() }?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
internal fun FormValues.int(k: String, default: Int): Int = this[k]?.trim()?.toIntOrNull() ?: default
internal fun FormValues.currency(): String = this["currency"].orEmpty().trim().uppercase().ifEmpty { "EUR" }
internal fun FormValues.minor(k: String): Long? = str(k).takeIf { it.isNotEmpty() }?.let { Money.parseToMinor(it, currency()) }
internal fun FormValues.zone(k: String): ZoneId =
    runCatching { ZoneId.of(this[k].orEmpty().trim()) }.getOrDefault(ZoneId.systemDefault())

private fun LocalTime?.text(): String = this?.truncatedTo(ChronoUnit.MINUTES)?.toString().orEmpty()
private fun LocalDate?.text(): String = this?.toString().orEmpty()
private fun Long?.money(currency: String): String = this?.let { Money.formatPlain(it, currency) }.orEmpty()

private fun end(v: FormValues, dk: String, tk: String, sk: String, stk: String): Map<String, FieldError> {
    val sd = v.date(sk) ?: return emptyMap()
    val ed = v.date(dk) ?: return emptyMap()
    if (ed.isBefore(sd)) return mapOf(dk to FieldError.END_BEFORE_START)
    if (ed == sd) {
        val st = v.time(stk); val et = v.time(tk)
        if (st != null && et != null && et.isBefore(st)) return mapOf(tk to FieldError.END_BEFORE_START)
    }
    return emptyMap()
}

// ---- Vuelos -------------------------------------------------------------------------------------
object FlightCodec {
    fun values(f: FlightEntity): FormValues {
        val dep = Instant.ofEpochMilli(f.departureEpochMs).atZone(ZoneId.of(f.departureZoneId))
        val arr = Instant.ofEpochMilli(f.arrivalEpochMs).atZone(ZoneId.of(f.arrivalZoneId))
        return mapOf(
            "airline" to f.airline, "flightNumber" to f.flightNumber,
            "departureAirport" to f.departureAirport, "arrivalAirport" to f.arrivalAirport, "terminal" to f.terminal,
            "depDate" to dep.toLocalDate().text(), "depTime" to dep.toLocalTime().text(), "depZone" to f.departureZoneId,
            "arrDate" to if (hasArrival(f)) arr.toLocalDate().text() else "",
            "arrTime" to if (hasArrival(f)) arr.toLocalTime().text() else "",
            "arrZone" to f.arrivalZoneId,
            "locator" to f.locator, "ticketNumber" to f.ticketNumber, "seat" to f.seat, "cabinClass" to f.cabinClass,
            "baggage" to f.baggage, "price" to f.priceMinor.money(f.currency), "currency" to f.currency,
            "status" to f.status, "notes" to f.notes,
        )
    }

    private fun instant(date: LocalDate, time: LocalTime, zone: ZoneId): Long =
        LocalDateTime.of(date, time).atZone(zone).toInstant().toEpochMilli()

    fun build(tripId: Long, id: Long, v: FormValues): FlightEntity = FlightEntity(
        id = id, tripId = tripId, airline = v.str("airline"), flightNumber = v.str("flightNumber"),
        departureAirport = v.str("departureAirport"), arrivalAirport = v.str("arrivalAirport"), terminal = v.str("terminal"),
        departureEpochMs = instant(v.date("depDate")!!, v.time("depTime")!!, v.zone("depZone")),
        departureZoneId = v.zone("depZone").id,
        // Sin hora de llegada (el usuario solo puso la salida y no se pudo buscar el vuelo): llegada = salida = "desconocida".
        arrivalEpochMs = if (v.date("arrDate") != null && v.time("arrTime") != null)
            instant(v.date("arrDate")!!, v.time("arrTime")!!, v.zone("arrZone"))
        else instant(v.date("depDate")!!, v.time("depTime")!!, v.zone("depZone")),
        arrivalZoneId = v.zone("arrZone").id,
        locator = v.str("locator"), ticketNumber = v.str("ticketNumber"), seat = v.str("seat"),
        cabinClass = v.str("cabinClass"), baggage = v.str("baggage"),
        priceMinor = v.minor("price"), currency = v.currency(),
        status = v.str("status").ifEmpty { "CONFIRMED" }, notes = v.str("notes"),
    )

    /** Hay hora de llegada guardada. Si es igual a la salida, el usuario no la puso y no se pudo buscar: se desconoce. */
    fun hasArrival(f: FlightEntity): Boolean = f.arrivalEpochMs > f.departureEpochMs

    /** La llegada es opcional, pero si se pone, fecha y hora van juntas y no puede ser anterior a la salida (cada una en su zona). */
    fun cross(v: FormValues): Map<String, FieldError> {
        val dd = v.date("depDate"); val dt = v.time("depTime"); val ad = v.date("arrDate"); val at = v.time("arrTime")
        if (ad != null && at == null && v.str("arrTime").isEmpty()) return mapOf("arrTime" to FieldError.REQUIRED)
        if (at != null && ad == null && v.str("arrDate").isEmpty()) return mapOf("arrDate" to FieldError.REQUIRED)
        if (dd == null || dt == null || ad == null || at == null) return emptyMap()
        val dep = instant(dd, dt, v.zone("depZone")); val arr = instant(ad, at, v.zone("arrZone"))
        return if (arr < dep) mapOf("arrDate" to FieldError.END_BEFORE_START) else emptyMap()
    }

    /** Duración real del vuelo en minutos (independiente de las zonas horarias). */
    fun durationMinutes(f: FlightEntity): Long = ((f.arrivalEpochMs - f.departureEpochMs) / 60_000L).coerceAtLeast(0)
}

// ---- Alojamientos -------------------------------------------------------------------------------
object LodgingCodec {
    fun values(l: LodgingEntity): FormValues = mapOf(
        "name" to l.name, "address" to l.address, "city" to l.city, "country" to l.country,
        "checkInDate" to l.checkInDate.text(), "checkInTime" to l.checkInTime.text(),
        "checkOutDate" to l.checkOutDate.text(), "checkOutTime" to l.checkOutTime.text(),
        "timeZone" to l.timeZoneId, "guests" to l.guests.toString(), "roomType" to l.roomType,
        "reservationNumber" to l.reservationNumber, "price" to l.priceMinor.money(l.currency), "currency" to l.currency,
        "phone" to l.phone, "website" to l.website, "notes" to l.notes,
    )

    fun build(tripId: Long, id: Long, v: FormValues): LodgingEntity = LodgingEntity(
        id = id, tripId = tripId, name = v.str("name"), address = v.str("address"), city = v.str("city"),
        country = v.str("country"), checkInDate = v.date("checkInDate")!!, checkOutDate = v.date("checkOutDate")!!,
        checkInTime = v.time("checkInTime"), checkOutTime = v.time("checkOutTime"),
        timeZoneId = v.zone("timeZone").id, guests = v.int("guests", 1).coerceAtLeast(1),
        roomType = v.str("roomType"), reservationNumber = v.str("reservationNumber"),
        priceMinor = v.minor("price"), currency = v.currency(), phone = v.str("phone"),
        website = v.str("website"), notes = v.str("notes"),
    )

    fun cross(v: FormValues): Map<String, FieldError> {
        val a = v.date("checkInDate"); val b = v.date("checkOutDate")
        return if (a != null && b != null && b.isBefore(a)) mapOf("checkOutDate" to FieldError.END_BEFORE_START) else emptyMap()
    }
}

// ---- Actividades --------------------------------------------------------------------------------
object ActivityCodec {
    fun values(a: ActivityEntity): FormValues = mapOf(
        "name" to a.name, "description" to a.description, "location" to a.location,
        "date" to a.date.text(), "startTime" to a.startTime.text(), "endTime" to a.endTime.text(),
        "timeZone" to a.timeZoneId, "organizer" to a.organizer, "meetingAddress" to a.meetingAddress,
        "price" to a.priceMinor.money(a.currency), "currency" to a.currency,
        "participants" to a.participants.toString(), "locator" to a.locator, "bookingUrl" to a.bookingUrl, "notes" to a.notes,
    )

    fun build(tripId: Long, id: Long, v: FormValues): ActivityEntity = ActivityEntity(
        id = id, tripId = tripId, name = v.str("name"), description = v.str("description"), location = v.str("location"),
        date = v.date("date")!!, startTime = v.time("startTime"), endTime = v.time("endTime"),
        timeZoneId = v.zone("timeZone").id, organizer = v.str("organizer"), meetingAddress = v.str("meetingAddress"),
        priceMinor = v.minor("price"), currency = v.currency(), participants = v.int("participants", 1).coerceAtLeast(1),
        locator = v.str("locator"), bookingUrl = v.str("bookingUrl"), notes = v.str("notes"),
    )

    fun cross(v: FormValues): Map<String, FieldError> {
        val s = v.time("startTime"); val e = v.time("endTime")
        return if (s != null && e != null && e.isBefore(s)) mapOf("endTime" to FieldError.END_BEFORE_START) else emptyMap()
    }

    fun durationMinutes(a: ActivityEntity): Long? {
        val s = a.startTime ?: return null; val e = a.endTime ?: return null
        return ChronoUnit.MINUTES.between(s, e).takeIf { it >= 0 }
    }
}

// ---- Transportes (coche de alquiler, tren, autobús, ferry, traslado, taxi...) --------------------
object TransportCodec {
    fun values(t: TransportEntity): FormValues = mapOf(
        "type" to t.type, "company" to t.company, "model" to t.model, "plate" to t.plate,
        "startDate" to t.startDate.text(), "startTime" to t.startTime.text(),
        "endDate" to t.endDate.text(), "endTime" to t.endTime.text(), "timeZone" to t.timeZoneId,
        "originPlace" to t.originPlace, "destinationPlace" to t.destinationPlace, "address" to t.address,
        "reservationNumber" to t.reservationNumber, "price" to t.priceMinor.money(t.currency), "currency" to t.currency,
        "insurance" to t.insurance, "mileage" to t.mileage, "fuel" to t.fuel, "conditions" to t.conditions,
        "phone" to t.phone, "notes" to t.notes,
    )

    fun build(tripId: Long, id: Long, v: FormValues): TransportEntity = TransportEntity(
        id = id, tripId = tripId, type = v.str("type").ifEmpty { "OTHER" }, company = v.str("company"),
        model = v.str("model"), plate = v.str("plate"), startDate = v.date("startDate")!!,
        startTime = v.time("startTime"),
        // Un tren de 10:00 a 13:30: si solo se escribe la hora de llegada, llega el mismo día.
        endDate = v.date("endDate") ?: v.time("endTime")?.let { v.date("startDate") }, endTime = v.time("endTime"),
        timeZoneId = v.zone("timeZone").id, originPlace = v.str("originPlace"), destinationPlace = v.str("destinationPlace"),
        address = v.str("address"), reservationNumber = v.str("reservationNumber"),
        priceMinor = v.minor("price"), currency = v.currency(), insurance = v.str("insurance"),
        mileage = v.str("mileage"), fuel = v.str("fuel"), conditions = v.str("conditions"),
        phone = v.str("phone"), notes = v.str("notes"),
    )

    fun cross(v: FormValues): Map<String, FieldError> = end(v, "endDate", "endTime", "startDate", "startTime")
}

// ---- Restaurantes -------------------------------------------------------------------------------
object RestaurantCodec {
    fun values(r: RestaurantEntity): FormValues = mapOf(
        "name" to r.name, "address" to r.address, "date" to r.date.text(), "time" to r.time.text(),
        "timeZone" to r.timeZoneId, "people" to r.people.toString(), "reservationNumber" to r.reservationNumber,
        "price" to r.priceMinor.money(r.currency), "currency" to r.currency, "url" to r.url,
        "phone" to r.phone, "notes" to r.notes,
    )

    fun build(tripId: Long, id: Long, v: FormValues): RestaurantEntity = RestaurantEntity(
        id = id, tripId = tripId, name = v.str("name"), address = v.str("address"), date = v.date("date")!!,
        time = v.time("time"), timeZoneId = v.zone("timeZone").id, people = v.int("people", 2).coerceAtLeast(1),
        reservationNumber = v.str("reservationNumber"), priceMinor = v.minor("price"), currency = v.currency(),
        url = v.str("url"), phone = v.str("phone"), notes = v.str("notes"),
    )
}

// ---- Visados y documentación de viaje -----------------------------------------------------------
object TravelDocCodec {
    fun values(d: TravelDocEntity): FormValues = mapOf(
        "docType" to d.docType, "title" to d.title, "issuingCountry" to d.issuingCountry,
        "issueDate" to d.issueDate.text(), "expiryDate" to d.expiryDate.text(), "reference" to d.reference,
        "approvalDate" to d.approvalDate.text(), "status" to d.status, "notes" to d.notes,
    )

    fun build(tripId: Long, id: Long, v: FormValues): TravelDocEntity = TravelDocEntity(
        id = id, tripId = tripId, docType = v.str("docType").ifEmpty { "OTHER" }, title = v.str("title"),
        issuingCountry = v.str("issuingCountry"), issueDate = v.date("issueDate"), expiryDate = v.date("expiryDate"),
        reference = v.str("reference"), approvalDate = v.date("approvalDate"),
        status = v.str("status").ifEmpty { "APPROVED" }, notes = v.str("notes"),
    )

    fun cross(v: FormValues): Map<String, FieldError> {
        val i = v.date("issueDate"); val e = v.date("expiryDate")
        return if (i != null && e != null && e.isBefore(i)) mapOf("expiryDate" to FieldError.END_BEFORE_START) else emptyMap()
    }
}

// ---- Gastos -------------------------------------------------------------------------------------
object ExpenseCodec {
    fun values(e: ExpenseEntity): FormValues = mapOf(
        "concept" to e.concept, "category" to e.category, "amount" to e.amountMinor.money(e.currency),
        "currency" to e.currency, "date" to e.date.text(), "paymentMethod" to e.paymentMethod, "notes" to e.notes,
    )

    fun build(tripId: Long, id: Long, v: FormValues): ExpenseEntity = ExpenseEntity(
        id = id, tripId = tripId, concept = v.str("concept"), category = v.str("category").ifEmpty { "OTHER" },
        amountMinor = v.minor("amount") ?: 0L, currency = v.currency(), date = v.date("date")!!,
        paymentMethod = v.str("paymentMethod"), notes = v.str("notes"),
    )
}

// ---- Destinos de un viaje de varias ciudades ----------------------------------------------------
object DestinationCodec {
    fun values(d: DestinationEntity): FormValues = mapOf(
        "city" to d.city, "country" to d.country, "startDate" to d.startDate.text(),
        "endDate" to d.endDate.text(), "timeZone" to d.timeZoneId,
    )

    fun build(tripId: Long, id: Long, v: FormValues, position: Int): DestinationEntity = DestinationEntity(
        id = id, tripId = tripId, city = v.str("city"), country = v.str("country"),
        startDate = v.date("startDate")!!, endDate = v.date("endDate")!!, timeZoneId = v.zone("timeZone").id,
        position = position,
    )

    fun cross(v: FormValues): Map<String, FieldError> = end(v, "endDate", "endTime", "startDate", "startTime")
}

object ModuleCodecs {
    fun cross(type: ModuleType, v: FormValues): Map<String, FieldError> = when (type) {
        ModuleType.FLIGHT -> FlightCodec.cross(v)
        ModuleType.LODGING -> LodgingCodec.cross(v)
        ModuleType.ACTIVITY -> ActivityCodec.cross(v)
        ModuleType.TRANSPORT -> TransportCodec.cross(v)
        ModuleType.RESTAURANT -> emptyMap()
        ModuleType.TRAVEL_DOC -> TravelDocCodec.cross(v)
        ModuleType.EXPENSE -> emptyMap()
        ModuleType.DESTINATION -> DestinationCodec.cross(v)
    }
}

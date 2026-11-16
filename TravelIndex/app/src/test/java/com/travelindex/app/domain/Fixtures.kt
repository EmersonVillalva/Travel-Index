package com.travelindex.app.domain

import com.travelindex.app.data.db.ActivityEntity
import com.travelindex.app.data.db.FlightEntity
import com.travelindex.app.data.db.LodgingEntity
import com.travelindex.app.data.db.NoteItemEntity
import com.travelindex.app.data.db.RestaurantEntity
import com.travelindex.app.data.db.TransportEntity
import com.travelindex.app.data.db.TravelDocEntity
import com.travelindex.app.data.db.TripEntity
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

object Fixtures {
    fun ms(z: String, y: Int, m: Int, d: Int, h: Int, min: Int) =
        ZonedDateTime.of(y, m, d, h, min, 0, 0, ZoneId.of(z)).toInstant().toEpochMilli()

    val trip = TripEntity(
        id = 1, name = "Roma", city = "Roma", country = "Italia",
        startDate = LocalDate.of(2027, 6, 12), endDate = LocalDate.of(2027, 6, 18), travelers = 2, currency = "EUR",
    )
    val flight = FlightEntity(
        id = 1, tripId = 1, airline = "IB", flightNumber = "3250", departureAirport = "MAD", arrivalAirport = "FCO",
        departureEpochMs = ms("Europe/Madrid", 2027, 6, 12, 11, 45), departureZoneId = "Europe/Madrid",
        arrivalEpochMs = ms("Europe/Rome", 2027, 6, 12, 14, 15), arrivalZoneId = "Europe/Rome",
        priceMinor = 18_990, currency = "EUR", locator = "ABC123", seat = "12A",
    )
    val hotel = LodgingEntity(
        id = 1, tripId = 1, name = "Hotel Roma", address = "Via Veneto 1", checkInDate = LocalDate.of(2027, 6, 12),
        checkOutDate = LocalDate.of(2027, 6, 15), timeZoneId = "Europe/Rome", checkInTime = LocalTime.of(16, 0),
        priceMinor = 45_000, currency = "EUR",
    )
    val activity = ActivityEntity(
        id = 1, tripId = 1, name = "Coliseo", location = "Piazza del Colosseo", date = LocalDate.of(2027, 6, 13),
        startTime = LocalTime.of(10, 0), durationMinutes = 150, timeZoneId = "Europe/Rome", participants = 2,
    )
    val transport = TransportEntity(
        id = 1, tripId = 1, type = "CAR_RENTAL", company = "Hertz", model = "Fiat 500", plate = "AB123CD",
        startDate = LocalDate.of(2027, 6, 14), startTime = LocalTime.of(9, 0), endDate = LocalDate.of(2027, 6, 16),
        endTime = LocalTime.of(18, 0), timeZoneId = "Europe/Rome", originPlace = "FCO", destinationPlace = "FCO",
        priceMinor = 12_000, currency = "EUR",
    )
    val restaurant = RestaurantEntity(
        id = 1, tripId = 1, name = "Da Enzo", date = LocalDate.of(2027, 6, 12), time = LocalTime.of(20, 30),
        timeZoneId = "Europe/Rome", people = 2, priceMinor = 6_000, currency = "EUR",
    )
    val doc = TravelDocEntity(
        id = 1, tripId = 1, docType = "PASSPORT", title = "Pasaporte", issuingCountry = "España",
        issueDate = LocalDate.of(2020, 1, 1), expiryDate = LocalDate.of(2030, 1, 1), reference = "PAB123456",
        status = "APPROVED",
    )
    val note = NoteItemEntity(
        id = 1, tripId = 1, kind = "REMINDER", text = "Hacer check-in online",
        dueDate = LocalDate.of(2027, 6, 11), dueTime = LocalTime.of(8, 0),
    )
    fun bundle() = TripBundle(
        trip, flights = listOf(flight), lodgings = listOf(hotel), activities = listOf(activity),
        transports = listOf(transport), restaurants = listOf(restaurant), docs = listOf(doc), notes = listOf(note),
    )
}

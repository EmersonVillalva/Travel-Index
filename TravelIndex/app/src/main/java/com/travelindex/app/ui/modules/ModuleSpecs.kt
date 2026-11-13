package com.travelindex.app.ui.modules

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.Hotel
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.ui.graphics.vector.ImageVector
import com.travelindex.app.R
import com.travelindex.app.domain.Airports
import com.travelindex.app.domain.ModuleType
import com.travelindex.app.domain.forms.Choice
import com.travelindex.app.domain.forms.FieldDef
import com.travelindex.app.domain.forms.FieldType

/** [keys] = lo que muestra el detalle; [formKeys] = lo que se pide en el formulario (por defecto, lo mismo). */
data class FormSection(@StringRes val titleRes: Int, val keys: List<String>, val formKeys: List<String> = keys)

data class ModuleSpec(
    val type: ModuleType,
    @StringRes val titleRes: Int,
    @StringRes val newTitleRes: Int,
    @StringRes val editTitleRes: Int,
    @StringRes val emptyTitleRes: Int,
    @StringRes val emptyBodyRes: Int,
    val icon: ImageVector,
    val fields: List<FieldDef>,
    val sections: List<FormSection>,
    /** Campos de teléfono y enlaces para acciones rápidas en el detalle. */
    val phoneKey: String? = null,
    val urlKey: String? = null,
    /** Campos que solo se muestran en ciertos casos (p. ej. la zona horaria de un aeropuerto que no conocemos). */
    val visibleWhen: (FieldDef, Map<String, String>) -> Boolean = { _, _ -> true },
) {
    fun field(key: String): FieldDef? = fields.firstOrNull { it.key == key }
}

private fun f(key: String, @StringRes label: Int, type: FieldType = FieldType.TEXT, required: Boolean = false,
              choices: List<Choice> = emptyList(), sensitive: Boolean = false, notBeforeKey: String? = null) =
    FieldDef(key, label, type, required, choices, sensitive, notBeforeKey)

object Choices {
    val cabin = listOf(
        Choice("ECONOMY", R.string.c_cabin_economy), Choice("PREMIUM", R.string.c_cabin_premium),
        Choice("BUSINESS", R.string.c_cabin_business), Choice("FIRST", R.string.c_cabin_first),
    )
    val flightStatus = listOf(
        Choice("CONFIRMED", R.string.c_status_confirmed), Choice("PENDING", R.string.c_status_pending),
        Choice("CHECKED_IN", R.string.c_status_checked_in), Choice("CANCELLED", R.string.c_status_cancelled),
    )
    val transportType = listOf(
        Choice("CAR_RENTAL", R.string.c_tr_car), Choice("TRAIN", R.string.c_tr_train), Choice("BUS", R.string.c_tr_bus),
        Choice("FERRY", R.string.c_tr_ferry), Choice("TRANSFER", R.string.c_tr_transfer), Choice("TAXI", R.string.c_tr_taxi),
        Choice("OTHER", R.string.c_other),
    )
    val docType = listOf(
        Choice("VISA", R.string.c_doc_visa), Choice("PASSPORT", R.string.c_doc_passport), Choice("ID_CARD", R.string.c_doc_id),
        Choice("ENTRY_PERMIT", R.string.c_doc_permit), Choice("ETA", R.string.c_doc_eta),
        Choice("INSURANCE", R.string.c_doc_insurance), Choice("OTHER", R.string.c_other),
    )
    val docStatus = listOf(
        Choice("PENDING", R.string.c_docst_pending), Choice("APPLIED", R.string.c_docst_applied),
        Choice("APPROVED", R.string.c_docst_approved), Choice("REJECTED", R.string.c_docst_rejected),
    )
    val expenseCategory = listOf(
        Choice("FLIGHTS", R.string.c_cat_flights), Choice("LODGING", R.string.c_cat_lodging), Choice("FOOD", R.string.c_cat_food),
        Choice("TRANSPORT", R.string.c_cat_transport), Choice("ACTIVITIES", R.string.c_cat_activities),
        Choice("SHOPPING", R.string.c_cat_shopping), Choice("OTHER", R.string.c_other),
    )
    val payment = listOf(
        Choice("CASH", R.string.c_pay_cash), Choice("CARD", R.string.c_pay_card), Choice("TRANSFER", R.string.c_pay_transfer),
        Choice("OTHER", R.string.c_other),
    )

    fun label(choices: List<Choice>, key: String): Int? = choices.firstOrNull { it.key == key }?.labelRes
}

object ModuleSpecs {
    private val flight = ModuleSpec(
        type = ModuleType.FLIGHT, titleRes = R.string.mod_flights, newTitleRes = R.string.new_flight, editTitleRes = R.string.edit_flight,
        emptyTitleRes = R.string.empty_flights_title, emptyBodyRes = R.string.empty_flights_body, icon = Icons.Outlined.Flight,
        fields = listOf(
            f("airline", R.string.f_airline, required = true),
            f("flightNumber", R.string.f_flight_number, required = true),
            f("status", R.string.f_status, FieldType.CHOICE, choices = Choices.flightStatus),
            f("departureAirport", R.string.f_dep_airport, FieldType.AIRPORT, required = true),
            f("terminal", R.string.f_terminal),
            f("depDate", R.string.f_dep_date, FieldType.DATE, required = true),
            f("depTime", R.string.f_dep_time, FieldType.TIME, required = true),
            f("depZone", R.string.f_dep_zone, FieldType.ZONE),
            f("arrivalAirport", R.string.f_arr_airport, FieldType.AIRPORT, required = true),
            f("arrDate", R.string.f_arr_date, FieldType.DATE),
            f("arrTime", R.string.f_arr_time, FieldType.TIME),
            f("arrZone", R.string.f_arr_zone, FieldType.ZONE),
            // Estos campos ya no se piden, pero se conservan si un vuelo guardado antes los tenía.
            f("locator", R.string.f_locator),
            f("ticketNumber", R.string.f_ticket, sensitive = true),
            f("seat", R.string.f_seat),
            f("cabinClass", R.string.f_class, FieldType.CHOICE, choices = Choices.cabin),
            f("baggage", R.string.f_baggage),
            f("price", R.string.f_price, FieldType.MONEY),
            f("currency", R.string.f_currency, FieldType.CURRENCY, required = true),
            f("notes", R.string.f_notes, FieldType.MULTILINE),
        ),
        sections = listOf(
            FormSection(R.string.sec_flight, listOf("flightNumber", "airline")),
            FormSection(R.string.sec_departure, listOf("departureAirport", "terminal", "depDate", "depTime", "depZone"),
                formKeys = listOf("departureAirport", "depDate", "depTime", "depZone")),
            FormSection(R.string.sec_arrival, listOf("arrivalAirport", "arrDate", "arrTime", "arrZone")),
            FormSection(R.string.sec_price, listOf("price", "currency")),
        ),
        // La zona horaria solo se pide si el aeropuerto no es de nuestra lista (si lo es, se pone sola).
        visibleWhen = { def, v ->
            when (def.key) {
                "depZone" -> v["departureAirport"].orEmpty().isNotBlank() && Airports.zoneFor(v["departureAirport"].orEmpty()) == null
                "arrZone" -> v["arrivalAirport"].orEmpty().isNotBlank() && Airports.zoneFor(v["arrivalAirport"].orEmpty()) == null
                else -> true
            }
        },
    )

    private val lodging = ModuleSpec(
        type = ModuleType.LODGING, titleRes = R.string.mod_lodgings, newTitleRes = R.string.new_lodging, editTitleRes = R.string.edit_lodging,
        emptyTitleRes = R.string.empty_lodgings_title, emptyBodyRes = R.string.empty_lodgings_body, icon = Icons.Outlined.Hotel,
        fields = listOf(
            f("name", R.string.f_hotel_name, required = true),
            f("address", R.string.f_address),
            f("city", R.string.field_city),
            f("country", R.string.field_country),
            f("checkInDate", R.string.f_checkin_date, FieldType.DATE, required = true),
            f("checkInTime", R.string.f_checkin_time, FieldType.TIME),
            f("checkOutDate", R.string.f_checkout_date, FieldType.DATE, required = true, notBeforeKey = "checkInDate"),
            f("checkOutTime", R.string.f_checkout_time, FieldType.TIME),
            f("timeZone", R.string.f_timezone, FieldType.ZONE, required = true),
            f("guests", R.string.f_guests, FieldType.INT),
            f("roomType", R.string.f_room),
            f("reservationNumber", R.string.f_reservation),
            f("price", R.string.f_price, FieldType.MONEY),
            f("currency", R.string.f_currency, FieldType.CURRENCY, required = true),
            f("phone", R.string.f_phone, FieldType.PHONE),
            f("website", R.string.f_website, FieldType.URL),
            f("notes", R.string.f_notes, FieldType.MULTILINE),
        ),
        sections = listOf(
            FormSection(R.string.sec_lodging, listOf("name", "address", "city", "country")),
            FormSection(R.string.sec_stay, listOf("checkInDate", "checkInTime", "checkOutDate", "checkOutTime", "timeZone", "guests", "roomType")),
            FormSection(R.string.sec_booking, listOf("reservationNumber", "price", "currency")),
            FormSection(R.string.sec_contact, listOf("phone", "website")),
            FormSection(R.string.sec_notes, listOf("notes")),
        ),
        phoneKey = "phone", urlKey = "website",
    )

    private val activity = ModuleSpec(
        type = ModuleType.ACTIVITY, titleRes = R.string.mod_activities, newTitleRes = R.string.new_activity, editTitleRes = R.string.edit_activity,
        emptyTitleRes = R.string.empty_activities_title, emptyBodyRes = R.string.empty_activities_body, icon = Icons.Outlined.Explore,
        fields = listOf(
            f("name", R.string.f_name, required = true),
            f("description", R.string.field_description, FieldType.MULTILINE),
            f("location", R.string.f_location),
            f("date", R.string.f_date, FieldType.DATE, required = true),
            f("startTime", R.string.f_start_time, FieldType.TIME),
            f("endTime", R.string.f_end_time, FieldType.TIME),
            f("timeZone", R.string.f_timezone, FieldType.ZONE, required = true),
            f("organizer", R.string.f_organizer),
            f("meetingAddress", R.string.f_meeting),
            f("participants", R.string.f_participants, FieldType.INT),
            f("locator", R.string.f_locator),
            f("bookingUrl", R.string.f_booking_url, FieldType.URL),
            f("price", R.string.f_price, FieldType.MONEY),
            f("currency", R.string.f_currency, FieldType.CURRENCY, required = true),
            f("notes", R.string.f_notes, FieldType.MULTILINE),
        ),
        sections = listOf(
            FormSection(R.string.sec_activity, listOf("name", "description", "location")),
            FormSection(R.string.sec_schedule, listOf("date", "startTime", "endTime", "timeZone")),
            FormSection(R.string.sec_booking, listOf("organizer", "meetingAddress", "participants", "locator", "bookingUrl", "price", "currency")),
            FormSection(R.string.sec_notes, listOf("notes")),
        ),
        urlKey = "bookingUrl",
    )

    private val transport = ModuleSpec(
        type = ModuleType.TRANSPORT, titleRes = R.string.mod_transports, newTitleRes = R.string.new_transport, editTitleRes = R.string.edit_transport,
        emptyTitleRes = R.string.empty_transports_title, emptyBodyRes = R.string.empty_transports_body, icon = Icons.Outlined.DirectionsCar,
        fields = listOf(
            f("type", R.string.f_transport_type, FieldType.CHOICE, required = true, choices = Choices.transportType),
            f("company", R.string.f_company),
            f("model", R.string.f_vehicle_model),
            f("plate", R.string.f_plate),
            f("startDate", R.string.f_pickup_date, FieldType.DATE, required = true),
            f("startTime", R.string.f_pickup_time, FieldType.TIME),
            f("endDate", R.string.f_return_date, FieldType.DATE, notBeforeKey = "startDate"),
            f("endTime", R.string.f_return_time, FieldType.TIME),
            f("timeZone", R.string.f_timezone, FieldType.ZONE, required = true),
            f("originPlace", R.string.f_origin),
            f("destinationPlace", R.string.f_destination),
            f("address", R.string.f_address),
            f("reservationNumber", R.string.f_reservation),
            f("price", R.string.f_price, FieldType.MONEY),
            f("currency", R.string.f_currency, FieldType.CURRENCY, required = true),
            f("phone", R.string.f_phone, FieldType.PHONE),
            f("insurance", R.string.f_insurance),
            f("mileage", R.string.f_mileage),
            f("fuel", R.string.f_fuel),
            f("conditions", R.string.f_conditions, FieldType.MULTILINE),
            f("notes", R.string.f_notes, FieldType.MULTILINE),
        ),
        sections = listOf(
            FormSection(R.string.sec_transport, listOf("type", "company", "model", "plate")),
            FormSection(R.string.sec_schedule, listOf("startDate", "startTime", "endDate", "endTime", "timeZone")),
            FormSection(R.string.sec_places, listOf("originPlace", "destinationPlace", "address")),
            FormSection(R.string.sec_booking, listOf("reservationNumber", "price", "currency", "phone")),
            FormSection(R.string.sec_car_rental, listOf("insurance", "mileage", "fuel", "conditions")),
            FormSection(R.string.sec_notes, listOf("notes")),
        ),
        phoneKey = "phone",
    )

    private val restaurant = ModuleSpec(
        type = ModuleType.RESTAURANT, titleRes = R.string.mod_restaurants, newTitleRes = R.string.new_restaurant, editTitleRes = R.string.edit_restaurant,
        emptyTitleRes = R.string.empty_restaurants_title, emptyBodyRes = R.string.empty_restaurants_body, icon = Icons.Outlined.Restaurant,
        fields = listOf(
            f("name", R.string.f_restaurant_name, required = true),
            f("address", R.string.f_address),
            f("date", R.string.f_date, FieldType.DATE, required = true),
            f("time", R.string.f_time, FieldType.TIME),
            f("timeZone", R.string.f_timezone, FieldType.ZONE, required = true),
            f("people", R.string.f_people, FieldType.INT),
            f("reservationNumber", R.string.f_reservation),
            f("price", R.string.f_price_estimated, FieldType.MONEY),
            f("currency", R.string.f_currency, FieldType.CURRENCY, required = true),
            f("url", R.string.f_url, FieldType.URL),
            f("phone", R.string.f_phone, FieldType.PHONE),
            f("notes", R.string.f_notes, FieldType.MULTILINE),
        ),
        sections = listOf(
            FormSection(R.string.sec_restaurant, listOf("name", "address")),
            FormSection(R.string.sec_booking, listOf("date", "time", "timeZone", "people", "reservationNumber", "price", "currency")),
            FormSection(R.string.sec_contact, listOf("url", "phone")),
            FormSection(R.string.sec_notes, listOf("notes")),
        ),
        phoneKey = "phone", urlKey = "url",
    )

    private val travelDoc = ModuleSpec(
        type = ModuleType.TRAVEL_DOC, titleRes = R.string.mod_docs, newTitleRes = R.string.new_doc, editTitleRes = R.string.edit_doc,
        emptyTitleRes = R.string.empty_docs_title, emptyBodyRes = R.string.empty_docs_body, icon = Icons.Outlined.Badge,
        fields = listOf(
            f("docType", R.string.f_doc_type, FieldType.CHOICE, required = true, choices = Choices.docType),
            f("title", R.string.f_doc_title),
            f("issuingCountry", R.string.f_issuing_country),
            f("issueDate", R.string.f_issue_date, FieldType.DATE),
            f("expiryDate", R.string.f_expiry_date, FieldType.DATE, notBeforeKey = "issueDate"),
            f("reference", R.string.f_reference, sensitive = true),
            f("approvalDate", R.string.f_approval_date, FieldType.DATE),
            f("status", R.string.f_doc_status, FieldType.CHOICE, required = true, choices = Choices.docStatus),
            f("notes", R.string.f_notes, FieldType.MULTILINE),
        ),
        sections = listOf(
            FormSection(R.string.sec_document, listOf("docType", "title", "issuingCountry", "status")),
            FormSection(R.string.sec_dates, listOf("issueDate", "expiryDate", "approvalDate")),
            FormSection(R.string.sec_sensitive, listOf("reference")),
            FormSection(R.string.sec_notes, listOf("notes")),
        ),
    )

    private val expense = ModuleSpec(
        type = ModuleType.EXPENSE, titleRes = R.string.mod_expenses, newTitleRes = R.string.new_expense, editTitleRes = R.string.edit_expense,
        emptyTitleRes = R.string.empty_expenses_title, emptyBodyRes = R.string.empty_expenses_body, icon = Icons.Outlined.Payments,
        fields = listOf(
            f("concept", R.string.f_concept, required = true),
            f("category", R.string.f_category, FieldType.CHOICE, required = true, choices = Choices.expenseCategory),
            f("amount", R.string.f_amount, FieldType.MONEY, required = true),
            f("currency", R.string.f_currency, FieldType.CURRENCY, required = true),
            f("date", R.string.f_date, FieldType.DATE, required = true),
            f("paymentMethod", R.string.f_payment, FieldType.CHOICE, choices = Choices.payment),
            f("notes", R.string.f_notes, FieldType.MULTILINE),
        ),
        sections = listOf(
            FormSection(R.string.sec_expense, listOf("concept", "category", "amount", "currency", "date", "paymentMethod")),
            FormSection(R.string.sec_notes, listOf("notes")),
        ),
    )

    private val destination = ModuleSpec(
        type = ModuleType.DESTINATION, titleRes = R.string.mod_destinations, newTitleRes = R.string.new_destination, editTitleRes = R.string.edit_destination,
        emptyTitleRes = R.string.empty_destinations_title, emptyBodyRes = R.string.empty_destinations_body, icon = Icons.Outlined.Place,
        fields = listOf(
            f("city", R.string.field_city, required = true),
            f("country", R.string.field_country, required = true),
            f("startDate", R.string.f_dest_start, FieldType.DATE, required = true),
            f("endDate", R.string.f_dest_end, FieldType.DATE, required = true),
            f("timeZone", R.string.f_timezone, FieldType.ZONE, required = true),
        ),
        sections = listOf(FormSection(R.string.sec_destination, listOf("city", "country", "startDate", "endDate", "timeZone"))),
    )

    fun of(type: ModuleType): ModuleSpec = when (type) {
        ModuleType.FLIGHT -> flight
        ModuleType.LODGING -> lodging
        ModuleType.ACTIVITY -> activity
        ModuleType.TRANSPORT -> transport
        ModuleType.RESTAURANT -> restaurant
        ModuleType.TRAVEL_DOC -> travelDoc
        ModuleType.EXPENSE -> expense
        ModuleType.DESTINATION -> destination
    }
}

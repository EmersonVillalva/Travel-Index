package com.travelindex.app.ui.trips

import com.travelindex.app.R
import com.travelindex.app.domain.forms.FieldDef
import com.travelindex.app.domain.forms.FieldType

/** Campos del formulario de viaje. Las claves coinciden con las de TripCodec (hay una prueba que lo comprueba). */
object TripSpec {
    val name = FieldDef("name", R.string.field_name, FieldType.TEXT, required = true)
    val city = FieldDef("city", R.string.field_city, FieldType.TEXT, required = true)
    val country = FieldDef("country", R.string.field_country, FieldType.TEXT, required = true)
    val startDate = FieldDef("startDate", R.string.field_start, FieldType.DATE, required = true)
    val endDate = FieldDef("endDate", R.string.field_end, FieldType.DATE, required = true)
    val travelers = FieldDef("travelers", R.string.field_travelers, FieldType.INT, required = true)
    val budget = FieldDef("budget", R.string.field_budget, FieldType.MONEY)
    val currency = FieldDef("currency", R.string.field_currency, FieldType.CURRENCY, required = true)
    val description = FieldDef("description", R.string.field_description, FieldType.MULTILINE)
    val notes = FieldDef("notes", R.string.field_notes, FieldType.MULTILINE)
    val travelModes = FieldDef("travelModes", R.string.field_travel_modes, FieldType.TEXT)

    val all = listOf(name, city, country, startDate, endDate, travelers, budget, currency, description, notes, travelModes)
}

package com.travelindex.app.domain.forms

import com.travelindex.app.data.db.TripEntity
import com.travelindex.app.domain.Money
import com.travelindex.app.domain.formatPlain

/** Formulario del propio viaje (crear y editar). Mismas reglas de validación que el resto de módulos. */
object TripCodec {
    const val MAX_TRAVELERS = 99

    fun values(t: TripEntity): FormValues = mapOf(
        "name" to t.name, "city" to t.city, "country" to t.country,
        "startDate" to t.startDate.toString(), "endDate" to t.endDate.toString(),
        "travelers" to t.travelers.toString(),
        "budget" to t.budgetMinor?.let { Money.formatPlain(it, t.currency) }.orEmpty(),
        "currency" to t.currency, "description" to t.description, "notes" to t.notes,
    )

    fun defaults(currency: String): FormValues = mapOf("travelers" to "1", "currency" to currency)

    /** Reglas que dependen de varios campos a la vez. */
    fun cross(v: FormValues): Map<String, FieldError> {
        val errors = linkedMapOf<String, FieldError>()
        val start = v.date("startDate")
        val end = v.date("endDate")
        if (start != null && end != null && end.isBefore(start)) errors["endDate"] = FieldError.END_BEFORE_START
        val travelers = v.str("travelers").toIntOrNull()
        if (travelers != null && (travelers < 1 || travelers > MAX_TRAVELERS)) errors["travelers"] = FieldError.INVALID_NUMBER
        return errors
    }

    /** Los valores deben haber pasado [FormValidator] y [cross]. [coverPath] es la portada ya guardada (o null). */
    fun build(id: Long, v: FormValues, coverPath: String?, createdAt: Long, cancelled: Boolean): TripEntity = TripEntity(
        id = id, name = v.str("name"), city = v.str("city"), country = v.str("country"),
        startDate = v.date("startDate")!!, endDate = v.date("endDate")!!,
        travelers = v.int("travelers", 1).coerceIn(1, MAX_TRAVELERS),
        coverPath = coverPath, description = v.str("description"),
        budgetMinor = v.minor("budget"), currency = v.currency(), notes = v.str("notes"),
        cancelled = cancelled, createdAt = createdAt,
    )
}

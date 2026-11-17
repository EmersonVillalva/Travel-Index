package com.travelindex.app.domain.forms

import com.travelindex.app.domain.Money
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

enum class FieldType { TEXT, MULTILINE, PHONE, URL, INT, MONEY, CURRENCY, DATE, TIME, ZONE, CHOICE, AIRPORT, DURATION }

/** [labelRes] es un id de recurso (Int) que rellena la capa de interfaz; el dominio no depende de Android. */
data class Choice(val key: String, val labelRes: Int)

data class FieldDef(
    val key: String,
    val labelRes: Int,
    val type: FieldType = FieldType.TEXT,
    val required: Boolean = false,
    val choices: List<Choice> = emptyList(),
    /** Datos sensibles (números de documento): enmascarados en el detalle. */
    val sensitive: Boolean = false,
    /** Solo para fechas: clave de otra fecha del formulario; este campo no puede ser anterior a ella (p. ej. salida del hotel). */
    val notBeforeKey: String? = null,
    /** Etiqueta solo para el formulario (p. ej. con "(opcional)"); el detalle y el PDF usan [labelRes]. */
    val formLabelRes: Int? = null,
)

enum class FieldError {
    REQUIRED, INVALID_DATE, INVALID_TIME, INVALID_NUMBER, INVALID_MONEY, INVALID_CURRENCY, INVALID_ZONE, END_BEFORE_START
}

typealias FormValues = Map<String, String>

/** Duraciones en minutos, guardadas como texto en el formulario ("150" = 2 h 30 min). */
object Durations {
    /** Máximo que admite un campo de duración: 7 días. */
    const val MAX_MINUTES = 7 * 24 * 60

    fun parse(text: String?): Int? = text?.trim()?.toIntOrNull()?.takeIf { it in 1..MAX_MINUTES }

    fun isValid(text: String?): Boolean = parse(text) != null

    /** "2 h 30 min", "45 min", "3 h". */
    fun text(minutes: Long): String {
        val h = minutes / 60; val m = minutes % 60
        return when { h == 0L -> "$m min"; m == 0L -> "$h h"; else -> "$h h $m min" }
    }

    fun of(hours: Int, minutes: Int): Int = (hours.coerceAtLeast(0) * 60 + minutes.coerceAtLeast(0)).coerceAtMost(MAX_MINUTES)
}

object FormValidator {
    /** Valida cada campo según su tipo. Los campos MONEY usan la moneda del campo "currency" del mismo formulario. */
    fun validate(fields: List<FieldDef>, values: FormValues): Map<String, FieldError> {
        val errors = linkedMapOf<String, FieldError>()
        val currency = values["currency"].orEmpty().trim().uppercase()
        for (f in fields) {
            val v = values[f.key].orEmpty().trim()
            if (v.isEmpty()) {
                if (f.required) errors[f.key] = FieldError.REQUIRED
                continue
            }
            val err: FieldError? = when (f.type) {
                FieldType.DATE -> if (runCatching { LocalDate.parse(v) }.isSuccess) null else FieldError.INVALID_DATE
                FieldType.TIME -> if (runCatching { LocalTime.parse(v) }.isSuccess) null else FieldError.INVALID_TIME
                FieldType.INT -> if ((v.toIntOrNull() ?: -1) >= 0) null else FieldError.INVALID_NUMBER
                FieldType.DURATION -> if (Durations.isValid(v)) null else FieldError.INVALID_NUMBER
                FieldType.CURRENCY -> if (Money.isValidCurrency(v)) null else FieldError.INVALID_CURRENCY
                FieldType.MONEY ->
                    if (!Money.isValidCurrency(currency)) null   // el error se marca en el campo de moneda
                    else if (Money.parseToMinor(v, currency) != null) null else FieldError.INVALID_MONEY
                FieldType.ZONE -> if (runCatching { ZoneId.of(v) }.isSuccess) null else FieldError.INVALID_ZONE
                FieldType.CHOICE ->
                    if (f.choices.isEmpty() || f.choices.any { it.key == v }) null else FieldError.REQUIRED
                else -> null
            }
            if (err != null) errors[f.key] = err
        }
        return errors
    }
}

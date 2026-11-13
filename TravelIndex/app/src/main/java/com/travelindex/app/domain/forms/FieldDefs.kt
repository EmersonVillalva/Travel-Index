package com.travelindex.app.domain.forms

import com.travelindex.app.domain.Money
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

enum class FieldType { TEXT, MULTILINE, PHONE, URL, INT, MONEY, CURRENCY, DATE, TIME, ZONE, CHOICE, AIRPORT }

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
)

enum class FieldError {
    REQUIRED, INVALID_DATE, INVALID_TIME, INVALID_NUMBER, INVALID_MONEY, INVALID_CURRENCY, INVALID_ZONE, END_BEFORE_START
}

typealias FormValues = Map<String, String>

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

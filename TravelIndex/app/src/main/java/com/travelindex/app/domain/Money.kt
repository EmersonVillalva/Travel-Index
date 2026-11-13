package com.travelindex.app.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

object Money {
    fun isValidCurrency(code: String): Boolean =
        runCatching { Currency.getInstance(code.trim().uppercase()) }.isSuccess

    fun fractionDigits(code: String): Int =
        runCatching { Currency.getInstance(code).defaultFractionDigits.coerceAtLeast(0) }.getOrDefault(2)

    /** Texto del usuario ("1.234,50" o "1234.50") → unidades menores. Devuelve null si no es válido o es negativo. */
    fun parseToMinor(text: String, currency: String): Long? {
        val t = text.trim().replace(" ", "")
        if (t.isEmpty()) return null
        val normalized = when {
            t.contains(',') && t.contains('.') ->
                if (t.lastIndexOf(',') > t.lastIndexOf('.')) t.replace(".", "").replace(',', '.') else t.replace(",", "")
            t.contains(',') -> t.replace(',', '.')
            else -> t
        }
        val bd = normalized.toBigDecimalOrNull() ?: return null
        if (bd.signum() < 0) return null
        val digits = fractionDigits(currency)
        return bd.setScale(digits, RoundingMode.HALF_UP).movePointRight(digits).longValueExact()
    }

    fun format(minor: Long, currency: String, locale: Locale = Locale.getDefault()): String {
        val digits = fractionDigits(currency)
        val value = BigDecimal(minor).movePointLeft(digits)
        val nf = NumberFormat.getCurrencyInstance(locale)
        runCatching { nf.currency = Currency.getInstance(currency) }
        nf.minimumFractionDigits = digits; nf.maximumFractionDigits = digits
        return nf.format(value)
    }
}

/** Importe en unidades menores → texto editable ("1234.50"). Es el formato que usan los formularios. */
fun Money.formatPlain(minor: Long, currency: String): String {
    val digits = fractionDigits(currency)
    return java.math.BigDecimal(minor).movePointLeft(digits).setScale(digits).toPlainString()
}

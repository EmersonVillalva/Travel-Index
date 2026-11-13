package com.travelindex.app.domain

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** La app solo ofrece claro y oscuro. SYSTEM se conserva solo para leer ajustes antiguos y se trata como claro. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** [pattern] == null → formato corto del idioma del sistema. */
enum class DateFormatOption(val pattern: String?) {
    SYSTEM(null), DMY("dd/MM/yyyy"), MDY("MM/dd/yyyy"), YMD("yyyy-MM-dd"), LONG("d MMM yyyy"),
}

data class AppSettings(
    val userName: String = "",
    val photoPath: String? = null,
    val defaultCurrency: String = "EUR",
    val dateFormat: DateFormatOption = DateFormatOption.SYSTEM,
    val themeMode: ThemeMode = ThemeMode.LIGHT,
    val notificationsEnabled: Boolean = true,
    /** Aviso de vuelos: horas de antelación. */
    val flightLeadHours: Int = 24,
    /** Aviso de actividades, restaurantes y transportes con hora: minutos de antelación. */
    val eventLeadMinutes: Int = 120,
    /** Aviso de caducidad de documentos: días de antelación. */
    val docWarnDays: Int = 30,
    /** Hora del día (0-23) para avisos de eventos sin hora concreta. */
    val reminderHour: Int = 9,
    val blockScreenshots: Boolean = false,
)

/** Formateadores según las preferencias del usuario. */
class Formats(val option: DateFormatOption = DateFormatOption.SYSTEM, val locale: Locale = Locale.getDefault()) {
    private val dateFmt: DateTimeFormatter =
        option.pattern?.let { DateTimeFormatter.ofPattern(it, locale) }
            ?: DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
    private val fullFmt: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale)
    private val timeFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    private val monthFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("LLLL yyyy", locale)
    private val shortDayFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d", locale)

    fun date(d: LocalDate): String = d.format(dateFmt)
    fun full(d: LocalDate): String = d.format(fullFmt)
    fun time(t: LocalTime): String = t.format(timeFmt)
    fun time(z: ZonedDateTime): String = z.format(timeFmt)
    fun month(ym: java.time.YearMonth): String = ym.format(monthFmt).replaceFirstChar { it.titlecase(locale) }
    fun shortDay(d: LocalDate): String = d.format(shortDayFmt)
    fun range(a: LocalDate, b: LocalDate): String = if (a == b) date(a) else "${date(a)} – ${date(b)}"
}

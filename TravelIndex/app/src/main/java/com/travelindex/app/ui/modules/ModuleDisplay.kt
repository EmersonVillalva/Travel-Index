package com.travelindex.app.ui.modules

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.travelindex.app.R
import com.travelindex.app.ui.components.pluralText
import com.travelindex.app.domain.Airports
import com.travelindex.app.domain.DocWarning
import com.travelindex.app.domain.DocWarnings
import com.travelindex.app.domain.Formats
import com.travelindex.app.domain.ModuleType
import com.travelindex.app.domain.forms.FieldDef
import com.travelindex.app.domain.forms.FieldType
import com.travelindex.app.domain.forms.FlightCodec
import com.travelindex.app.domain.forms.ModuleItem
import com.travelindex.app.domain.forms.TravelDocCodec
import com.travelindex.app.ui.components.Pill
import com.travelindex.app.ui.components.formatMoneyValue
import com.travelindex.app.ui.components.zoneLabel
import com.travelindex.app.ui.theme.LocalFormats
import com.travelindex.app.ui.theme.Spacing
import com.travelindex.app.ui.theme.StatusDanger
import com.travelindex.app.ui.theme.StatusNeutral
import com.travelindex.app.ui.theme.StatusOk
import com.travelindex.app.ui.theme.StatusWarn
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

private fun Map<String, String>.v(k: String): String = this[k].orEmpty().trim()

private fun dateOf(v: Map<String, String>, k: String): LocalDate? =
    v.v(k).takeIf { it.isNotEmpty() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

private fun timeOf(v: Map<String, String>, k: String): LocalTime? =
    v.v(k).takeIf { it.isNotEmpty() }?.let { runCatching { LocalTime.parse(it) }.getOrNull() }

private fun Formats.d(v: Map<String, String>, k: String): String = dateOf(v, k)?.let { date(it) }.orEmpty()
private fun Formats.t(v: Map<String, String>, k: String): String = timeOf(v, k)?.let { time(it) }.orEmpty()

private fun joinNonBlank(sep: String, vararg parts: String): String = parts.filter { it.isNotBlank() }.joinToString(sep)

fun durationText(minutes: Long): String {
    val h = minutes / 60; val m = minutes % 60
    return when { h == 0L -> "$m min"; m == 0L -> "$h h"; else -> "$h h $m min" }
}

@Composable
fun docWarningLabel(w: DocWarning): String? = when (w) {
    DocWarning.NONE -> null
    DocWarning.PENDING -> stringResource(R.string.doc_warn_pending)
    DocWarning.EXPIRES_SOON -> stringResource(R.string.doc_warn_soon)
    DocWarning.EXPIRES_BEFORE_TRIP_END -> stringResource(R.string.doc_warn_before_trip)
    DocWarning.EXPIRED -> stringResource(R.string.doc_warn_expired)
    DocWarning.REJECTED -> stringResource(R.string.doc_warn_rejected)
}

fun docWarningColor(w: DocWarning): Color = when (w) {
    DocWarning.NONE -> StatusNeutral
    DocWarning.PENDING, DocWarning.EXPIRES_SOON, DocWarning.EXPIRES_BEFORE_TRIP_END -> StatusWarn
    DocWarning.EXPIRED, DocWarning.REJECTED -> StatusDanger
}

@Composable
fun choiceText(def: FieldDef?, key: String): String =
    def?.choices?.firstOrNull { it.key == key }?.let { stringResource(it.labelRes) } ?: key

/** Texto de un campo para la pantalla de detalle (formateado y, si es sensible, enmascarado). */
@Composable
fun displayValue(def: FieldDef, v: Map<String, String>, reveal: Boolean): String {
    val raw = v.v(def.key)
    if (raw.isEmpty()) return ""
    val formats = LocalFormats.current
    return when {
        def.sensitive -> if (reveal) raw else DocWarnings.mask(raw)
        def.type == FieldType.DATE -> formats.d(v, def.key).ifEmpty { raw }
        def.type == FieldType.TIME -> formats.t(v, def.key).ifEmpty { raw }
        def.type == FieldType.ZONE -> zoneLabel(raw)
        def.type == FieldType.AIRPORT -> Airports.displayName(raw)
        def.type == FieldType.CHOICE -> choiceText(def, raw)
        def.type == FieldType.MONEY -> formatMoneyValue(raw, v["currency"]) ?: raw
        else -> raw
    }
}

/** Título de un elemento (para barras superiores y listas). */
@Composable
fun moduleTitle(spec: ModuleSpec, v: Map<String, String>): String = when (spec.type) {
    ModuleType.FLIGHT -> joinNonBlank(" ", v.v("airline"), v.v("flightNumber"))
    ModuleType.TRANSPORT -> joinNonBlank(" ", v.v("company"), v.v("model")).ifEmpty { choiceText(spec.field("type"), v.v("type")) }
    ModuleType.TRAVEL_DOC -> v.v("title").ifEmpty { choiceText(spec.field("docType"), v.v("docType")) }
    ModuleType.EXPENSE -> v.v("concept")
    ModuleType.DESTINATION -> joinNonBlank(", ", v.v("city"), v.v("country"))
    else -> v.v("name")
}

private data class RowTexts(val title: String, val line1: String, val line2: String, val badge: Pair<String, Color>?, val trailing: String?)

@Composable
private fun rowTexts(spec: ModuleSpec, v: Map<String, String>, today: LocalDate, tripEnd: LocalDate?): RowTexts {
    val f = LocalFormats.current
    val title = moduleTitle(spec, v)
    return when (spec.type) {
        ModuleType.FLIGHT -> {
            val minutes = runCatching { FlightCodec.build(0, 0, v) }.getOrNull()?.let { FlightCodec.durationMinutes(it) }?.takeIf { it > 0 }
            val status = v.v("status")
            val badge = if (status.isEmpty() || status == "CONFIRMED") null else
                choiceText(spec.field("status"), status) to (if (status == "CANCELLED") StatusDanger else StatusWarn)
            RowTexts(
                title, joinNonBlank(" → ", Airports.displayName(v.v("departureAirport")), Airports.displayName(v.v("arrivalAirport"))),
                joinNonBlank(" · ", joinNonBlank(" → ", joinNonBlank(" ", f.d(v, "depDate"), f.t(v, "depTime")), f.t(v, "arrTime")),
                    minutes?.let { durationText(it) }.orEmpty()),
                badge, null,
            )
        }
        ModuleType.LODGING -> {
            val nights = ChronoUnit.DAYS.between(dateOf(v, "checkInDate") ?: today, dateOf(v, "checkOutDate") ?: today).toInt()
            RowTexts(
                title, joinNonBlank(", ", v.v("city"), v.v("country")).ifEmpty { v.v("address") },
                joinNonBlank(" · ", f.d(v, "checkInDate") + " – " + f.d(v, "checkOutDate"),
                    if (nights > 0) pluralText(R.plurals.nights_count, nights) else ""),
                null, null,
            )
        }
        ModuleType.ACTIVITY -> RowTexts(
            title, v.v("location"),
            joinNonBlank(" · ", f.d(v, "date"), joinNonBlank(" – ", f.t(v, "startTime"), f.t(v, "endTime"))), null, null,
        )
        ModuleType.TRANSPORT -> RowTexts(
            title,
            joinNonBlank(" · ", choiceText(spec.field("type"), v.v("type")), joinNonBlank(" → ", v.v("originPlace"), v.v("destinationPlace"))),
            joinNonBlank(" → ", joinNonBlank(" ", f.d(v, "startDate"), f.t(v, "startTime")), joinNonBlank(" ", f.d(v, "endDate"), f.t(v, "endTime"))),
            null, null,
        )
        ModuleType.RESTAURANT -> RowTexts(
            title, v.v("address"),
            joinNonBlank(" · ", joinNonBlank(" ", f.d(v, "date"), f.t(v, "time")), pluralText(R.plurals.people_count, v.v("people").toIntOrNull() ?: 1)),
            null, formatMoneyValue(v["price"], v["currency"]),
        )
        ModuleType.TRAVEL_DOC -> {
            val doc = runCatching { TravelDocCodec.build(0, 0, v) }.getOrNull()
            val w = doc?.let { DocWarnings.evaluate(it, tripEnd, today) } ?: DocWarning.NONE
            val label = docWarningLabel(w)
            RowTexts(
                title, joinNonBlank(" · ", choiceText(spec.field("docType"), v.v("docType")), v.v("issuingCountry")),
                if (v.v("expiryDate").isNotEmpty()) stringResource(R.string.doc_expires_on, f.d(v, "expiryDate")) else "",
                if (label != null) label to docWarningColor(w) else null, null,
            )
        }
        ModuleType.EXPENSE -> RowTexts(
            title, choiceText(spec.field("category"), v.v("category")), f.d(v, "date"),
            null, formatMoneyValue(v["amount"], v["currency"]),
        )
        ModuleType.DESTINATION -> RowTexts(title, "", f.d(v, "startDate") + " – " + f.d(v, "endDate"), null, null)
    }
}

/** Tarjeta de un elemento en las listas de cada módulo. */
@Composable
fun ModuleItemCard(spec: ModuleSpec, item: ModuleItem, today: LocalDate, tripEnd: LocalDate?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val t = rowTexts(spec, item.values, today, tripEnd)
    Card(
        onClick = onClick, modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(Modifier.padding(Spacing.m), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) { Icon(spec.icon, contentDescription = null, tint = MaterialTheme.colorScheme.secondary) }
            Column(Modifier.weight(1f)) {
                Text(t.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (t.line1.isNotBlank()) Text(t.line1, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (t.line2.isNotBlank()) Text(t.line2, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                t.badge?.let { (label, color) -> Pill(label, color, Modifier.padding(top = 4.dp)) }
            }
            t.trailing?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
        }
    }
}

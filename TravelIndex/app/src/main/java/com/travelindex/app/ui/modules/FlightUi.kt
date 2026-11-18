package com.travelindex.app.ui.modules

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.travelindex.app.R
import com.travelindex.app.data.db.FlightEntity
import com.travelindex.app.domain.Airports
import com.travelindex.app.domain.FlightConnections
import com.travelindex.app.domain.ModuleType
import com.travelindex.app.domain.forms.FlightCodec
import com.travelindex.app.ui.components.Pill
import com.travelindex.app.ui.components.SectionCard
import com.travelindex.app.ui.components.formatMoneyValue
import com.travelindex.app.ui.components.pluralText
import com.travelindex.app.ui.theme.LocalFormats
import com.travelindex.app.ui.theme.Spacing
import com.travelindex.app.ui.theme.StatusDanger
import com.travelindex.app.ui.theme.StatusNeutral
import com.travelindex.app.ui.theme.StatusWarn
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Flecha de vuelo: puntos en los extremos, línea discontinua con punta y un avión en el centro. */
@Composable
fun FlightArrow(modifier: Modifier = Modifier, label: String? = null) {
    val color = MaterialTheme.colorScheme.secondary
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().height(28.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxWidth().height(28.dp)) {
                val y = size.height / 2
                val stroke = 2.dp.toPx()
                drawCircle(color, radius = 4.dp.toPx(), center = Offset(4.dp.toPx(), y))
                drawLine(
                    color, Offset(10.dp.toPx(), y), Offset(size.width - 2.dp.toPx(), y), strokeWidth = stroke,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f)),
                )
                val tip = Offset(size.width, y)
                drawLine(color, tip, Offset(size.width - 9.dp.toPx(), y - 6.dp.toPx()), stroke, StrokeCap.Round)
                drawLine(color, tip, Offset(size.width - 9.dp.toPx(), y + 6.dp.toPx()), stroke, StrokeCap.Round)
            }
            Box(Modifier.background(MaterialTheme.colorScheme.surface, CircleShape).padding(3.dp)) {
                Icon(Icons.Outlined.Flight, null, tint = color, modifier = Modifier.rotate(90f))
            }
        }
        if (!label.isNullOrBlank()) Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** "De X a Y" con la flecha de vuelo en medio, los nombres de los aeropuertos y las horas. */
@Composable
fun FlightRouteCard(
    from: String, to: String, depClock: String, arrClock: String, duration: String?,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)),
    ) {
        Row(Modifier.fillMaxWidth().padding(Spacing.m), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Column(Modifier.weight(1.1f)) {
                Text(from, style = MaterialTheme.typography.titleMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                if (depClock.isNotBlank()) Text(depClock, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FlightArrow(Modifier.weight(1f), duration)
            Column(Modifier.weight(1.1f), horizontalAlignment = Alignment.End) {
                Text(to, style = MaterialTheme.typography.titleMedium, maxLines = 3, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End)
                if (arrClock.isNotBlank()) Text(arrClock, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/**
 * Tarjeta de un vuelo en la lista: aerolínea y número, fecha, horas de salida y llegada, duración, aeropuertos y lo que
 * costó. Todo se ve sin abrir el vuelo.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FlightItemCard(
    values: Map<String, String>, onClick: () -> Unit, modifier: Modifier = Modifier,
    /** Si el vuelo es un tramo de un viaje con escalas: su posición y la espera desde el tramo anterior. */
    leg: FlightConnections.LegInfo? = null,
) {
    val f = LocalFormats.current
    val flight = remember(values) { runCatching { FlightCodec.build(0, 0, values) }.getOrNull() }
    val title = listOf(values["airline"].orEmpty().trim(), values["flightNumber"].orEmpty().trim()).filter { it.isNotEmpty() }.joinToString(" ")
    val from = Airports.displayName(values["departureAirport"].orEmpty())
    val to = Airports.displayName(values["arrivalAirport"].orEmpty())
    val price = formatMoneyValue(values["price"], values["currency"])

    val dep = flight?.let { Instant.ofEpochMilli(it.departureEpochMs).atZone(ZoneId.of(it.departureZoneId)) }
    val knownArrival = flight != null && FlightCodec.hasArrival(flight)
    val arr = if (knownArrival && flight != null) Instant.ofEpochMilli(flight.arrivalEpochMs).atZone(ZoneId.of(flight.arrivalZoneId)) else null
    val minutes = flight?.takeIf { knownArrival }?.let { FlightCodec.durationMinutes(it) }?.takeIf { it > 0 }
    val dayOffset = if (dep != null && arr != null) ChronoUnit.DAYS.between(dep.toLocalDate(), arr.toLocalDate()).toInt() else 0

    val status = values["status"].orEmpty().trim()
    val statusLabel = if (status.isEmpty() || status == "CONFIRMED") null
        else choiceText(ModuleSpecs.of(ModuleType.FLIGHT).field("status"), status)
    val legChips = if (leg == null) emptyList() else listOfNotNull(
        stringResource(R.string.flight_chip_leg, leg.number, leg.count),
        leg.layoverMinutes?.let { stringResource(R.string.flight_chip_layover, leg.layoverAt.orEmpty(), durationText(it)) },
    )
    val extras = listOfNotNull(
        values["locator"]?.trim()?.takeIf { it.isNotEmpty() }?.let { stringResource(R.string.flight_chip_locator, it) },
        values["terminal"]?.trim()?.takeIf { it.isNotEmpty() }?.let { stringResource(R.string.flight_chip_terminal, it) },
        values["seat"]?.trim()?.takeIf { it.isNotEmpty() }?.let { stringResource(R.string.flight_chip_seat, it) },
    )

    Card(
        onClick = onClick, modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Outlined.Flight, contentDescription = null, tint = MaterialTheme.colorScheme.secondary) }
                Column(Modifier.weight(1f)) {
                    Text(title.ifEmpty { from }, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (dep != null) Text(f.date(dep.toLocalDate()), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (price != null) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(price, style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.flight_paid), style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Column(Modifier.weight(1.1f)) {
                    Text(dep?.let { f.time(it) } ?: "—", style = MaterialTheme.typography.titleLarge)
                    Text(from, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                FlightArrow(Modifier.weight(1f), minutes?.let { durationText(it) })
                Column(Modifier.weight(1.1f), horizontalAlignment = Alignment.End) {
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Top) {
                        Text(arr?.let { f.time(it) } ?: "—", style = MaterialTheme.typography.titleLarge)
                        if (dayOffset > 0) Text("+$dayOffset", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                    }
                    Text(to, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End)
                }
            }

            if (statusLabel != null || extras.isNotEmpty() || legChips.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    if (statusLabel != null) Pill(statusLabel, if (status == "CANCELLED") StatusDanger else StatusWarn)
                    legChips.forEach { Pill(it, StatusNeutral) }
                    extras.forEach { Pill(it, StatusNeutral) }
                }
            }
        }
    }
}

/** Un vuelo con escalas: la ruta completa y, tramo a tramo, sus vuelos y lo que se espera entre uno y otro. */
@Composable
fun FlightConnectionCard(legs: List<FlightEntity>, currentId: Long, modifier: Modifier = Modifier) {
    val f = LocalFormats.current
    SectionCard(stringResource(R.string.sec_flight_connection), modifier) {
        Text(FlightConnections.routeText(legs), style = MaterialTheme.typography.titleMedium)
        legs.forEachIndexed { i, leg ->
            val previous = legs.getOrNull(i - 1)
            val layover = previous?.let { FlightConnections.layoverMinutes(it, leg) }
            if (previous != null && layover != null) {
                Text(
                    stringResource(R.string.flight_chip_layover, Airports.displayName(previous.arrivalAirport), durationText(layover)),
                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary,
                )
            }
            val dep = Instant.ofEpochMilli(leg.departureEpochMs).atZone(ZoneId.of(leg.departureZoneId))
            val arr = if (FlightCodec.hasArrival(leg)) Instant.ofEpochMilli(leg.arrivalEpochMs).atZone(ZoneId.of(leg.arrivalZoneId)) else null
            val name = listOf(leg.airline.trim(), leg.flightNumber.trim()).filter { it.isNotEmpty() }.joinToString(" ")
            Column(Modifier.fillMaxWidth()) {
                Text(
                    stringResource(R.string.flight_connection_leg, i + 1, name),
                    style = MaterialTheme.typography.bodyLarge, fontWeight = if (leg.id == currentId) FontWeight.Bold else FontWeight.Normal,
                )
                Text(
                    Airports.displayName(leg.departureAirport) + " → " + Airports.displayName(leg.arrivalAirport),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    f.date(dep.toLocalDate()) + " · " + f.time(dep) + (arr?.let { " → " + f.time(it) } ?: ""),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Escalas de un vuelo, dentro del mismo formulario: un interruptor y, al activarlo, cuántas escalas tiene el viaje. Los bloques de
 * cada escala salen debajo, entre la salida y la llegada, tanto si ya están rellenados como si no.
 * [savedLegs] son los vuelos que el viaje tiene guardados: si se piden menos, se avisa de que se borrarán.
 */
@Composable
fun FlightStopsCard(stops: Int, savedLegs: Int, onStops: (Int) -> Unit, modifier: Modifier = Modifier) {
    val on = stops > 0
    SectionCard(stringResource(R.string.sec_flight_stops), modifier) {
        Row(
            Modifier.fillMaxWidth().toggleable(
                value = on, role = Role.Switch,
                onValueChange = { onStops(if (it) 1 else 0) },
            ),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            Text(stringResource(R.string.flight_has_stops), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Switch(checked = on, onCheckedChange = null)
        }
        if (on) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                IconButton(onClick = { onStops(stops - 1) }) {
                    Icon(Icons.Outlined.Remove, stringResource(R.string.flight_stops_less))
                }
                Text(pluralText(R.plurals.flight_stops_count, stops), style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = { onStops(stops + 1) }, enabled = stops < FlightConnections.MAX_STOPS) {
                    Icon(Icons.Outlined.Add, stringResource(R.string.flight_stops_more))
                }
            }
        }
        val removed = savedLegs - (stops + 1)
        if (removed > 0) {
            Text(
                pluralText(R.plurals.flight_legs_removed, removed), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

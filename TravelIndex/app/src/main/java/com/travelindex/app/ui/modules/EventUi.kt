package com.travelindex.app.ui.modules

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.Hotel
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.travelindex.app.R
import com.travelindex.app.domain.EventType
import com.travelindex.app.domain.ItineraryEvent
import com.travelindex.app.ui.components.zoneLabel
import com.travelindex.app.ui.theme.LocalFormats
import com.travelindex.app.ui.theme.Spacing
import java.time.ZoneId

fun eventIcon(type: EventType): ImageVector = when (type) {
    EventType.FLIGHT -> Icons.Outlined.Flight
    EventType.LODGING_CHECK_IN, EventType.LODGING_CHECK_OUT -> Icons.Outlined.Hotel
    EventType.ACTIVITY -> Icons.Outlined.Explore
    EventType.TRANSPORT_START, EventType.TRANSPORT_END -> Icons.Outlined.DirectionsCar
    EventType.RESTAURANT -> Icons.Outlined.Restaurant
    EventType.NOTE_DUE -> Icons.Outlined.NotificationsActive
}

@Composable
fun eventTypeLabel(e: ItineraryEvent): String = when (e.type) {
    EventType.FLIGHT -> stringResource(R.string.ev_flight)
    EventType.LODGING_CHECK_IN -> stringResource(R.string.ev_check_in)
    EventType.LODGING_CHECK_OUT -> stringResource(R.string.ev_check_out)
    EventType.ACTIVITY -> stringResource(R.string.ev_activity)
    EventType.TRANSPORT_START -> Choices.label(Choices.transportType, e.kind.orEmpty())?.let { stringResource(it) }
        ?: stringResource(R.string.ev_transport)
    EventType.TRANSPORT_END -> stringResource(R.string.ev_transport_end)
    EventType.RESTAURANT -> stringResource(R.string.ev_restaurant)
    EventType.NOTE_DUE -> stringResource(R.string.ev_reminder)
}

/**
 * Fila de un evento del itinerario. [showDate] añade la fecha (para listas que mezclan días);
 * [tripName] indica a qué viaje pertenece cuando se mezclan varios (calendario, inicio).
 */
@Composable
fun EventRow(
    event: ItineraryEvent,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    showDate: Boolean = false,
    tripName: String? = null,
) {
    val f = LocalFormats.current
    val timeText = if (event.timeKnown) f.time(event.start) else stringResource(R.string.time_unknown)
    val zoneNote = if (event.start.zone != ZoneId.systemDefault() && event.timeKnown) " · " + zoneLabel(event.start.zone.id) else ""
    val body: @Composable () -> Unit = {
        Row(
            Modifier.fillMaxWidth().padding(Spacing.m),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            Column(Modifier.width(64.dp)) {
                Text(timeText, style = MaterialTheme.typography.titleMedium)
                if (showDate) Text(f.shortDay(event.start.toLocalDate()), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) { Icon(eventIcon(event.type), contentDescription = null, tint = MaterialTheme.colorScheme.secondary) }
            Column(Modifier.weight(1f)) {
                Text(eventTypeLabel(event) + zoneNote, style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(event.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                event.subtitle?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                tripName?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) { body() }
    } else {
        Card(modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) { body() }
    }
}

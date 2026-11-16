package com.travelindex.app.ui.components

import androidx.annotation.PluralsRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Luggage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.travelindex.app.R
import com.travelindex.app.data.FileStorage
import com.travelindex.app.data.db.TripEntity
import com.travelindex.app.domain.TripStatus
import com.travelindex.app.domain.daysUntil
import com.travelindex.app.domain.tripDurationDays
import com.travelindex.app.domain.tripStatus
import com.travelindex.app.ui.theme.IndexNavy
import com.travelindex.app.ui.theme.LocalFormats
import com.travelindex.app.ui.theme.RouteTeal
import com.travelindex.app.ui.theme.Spacing
import com.travelindex.app.ui.theme.SunriseAmber
import java.io.File
import java.time.LocalDate

/** Texto con singular/plural ("1 noche", "3 noches"). El número se pasa también como argumento %1$d. */
@Composable
fun pluralText(@PluralsRes id: Int, count: Int): String =
    LocalContext.current.resources.getQuantityString(id, count, count)

@Composable
fun StatusChip(status: TripStatus, modifier: Modifier = Modifier) {
    val (label, color) = when (status) {
        TripStatus.UPCOMING -> stringResource(R.string.status_upcoming) to RouteTeal
        TripStatus.ONGOING -> stringResource(R.string.status_ongoing) to SunriseAmber
        TripStatus.COMPLETED -> stringResource(R.string.status_completed) to Color(0xFF9AA8BD)
        TripStatus.CANCELLED -> stringResource(R.string.status_cancelled) to Color(0xFFE5736F)
    }
    Surface(modifier = modifier, shape = RoundedCornerShape(50), color = color) {
        Text(label, Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium, color = Color(0xFF0B1220))
    }
}

/** Etiqueta pequeña de color (avisos, estados). */
@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(50), color = color.copy(alpha = 0.22f)) {
        Text(text, Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun countdownText(trip: TripEntity, today: LocalDate = LocalDate.now()): String? {
    val d = daysUntil(trip.startDate, today)
    return when {
        trip.cancelled || d < 0 -> null
        d == 0L -> stringResource(R.string.countdown_today)
        d == 1L -> stringResource(R.string.countdown_tomorrow)
        else -> stringResource(R.string.countdown_days, d.toInt())
    }
}

/** Tarjeta de viaje con portada. Si [onClick] es null no es pulsable (p. ej. cabecera del panel del viaje). */
@Composable
fun TripCard(trip: TripEntity, onClick: (() -> Unit)?, modifier: Modifier = Modifier, tall: Boolean = false, paidText: String? = null) {
    val formats = LocalFormats.current
    val today = LocalDate.now()
    val status = tripStatus(trip.startDate, trip.endDate, trip.cancelled, today)
    val ctx = LocalContext.current
    val content: @Composable () -> Unit = {
        Box(Modifier.fillMaxWidth().heightIn(min = if (tall) 240.dp else 196.dp)) {
            if (trip.coverPath != null) {
                AsyncImage(
                    model = File(File(ctx.filesDir, FileStorage.DIR), trip.coverPath),
                    contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize(),
                )
            } else {
                Box(Modifier.matchParentSize().background(Brush.linearGradient(listOf(IndexNavy, Color(0xFF14506A), RouteTeal))))
            }
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xDD0B1220)))))
            Row(
                Modifier.align(Alignment.TopStart).fillMaxWidth().padding(Spacing.m),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                StatusChip(status)
                countdownText(trip, today)?.let {
                    Surface(shape = RoundedCornerShape(50), color = Color(0x990B1220)) {
                        Text(it, Modifier.padding(horizontal = 10.dp, vertical = 4.dp), color = Color.White,
                            style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            Column(Modifier.align(Alignment.BottomStart).padding(Spacing.m)) {
                Text(trip.name, color = Color.White, style = MaterialTheme.typography.titleLarge,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${trip.city}, ${trip.country}", color = Color(0xFFDDE6F2), style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    formats.range(trip.startDate, trip.endDate) + " · " +
                        pluralText(R.plurals.duration_days, tripDurationDays(trip.startDate, trip.endDate)),
                    color = Color(0xFFDDE6F2), style = MaterialTheme.typography.labelMedium,
                )
                TravelModeIcons(trip.travelModes, Color(0xFFDDE6F2), Modifier.padding(top = 4.dp))
                // Viajes pasados: lo que se pagó en total (por moneda).
                if (!paidText.isNullOrBlank()) Text(
                    stringResource(R.string.trip_paid, paidText), color = Color.White, style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
    if (onClick != null) {
        Card(
            onClick = onClick, modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        ) { content() }
    } else {
        Card(
            modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        ) { content() }
    }
}

@Composable
fun EmptyState(
    title: String, body: String, modifier: Modifier = Modifier, icon: ImageVector = Icons.Outlined.Luggage,
    actionLabel: String? = null, onAction: (() -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Box(Modifier.size(72.dp).clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(36.dp))
        }
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(body, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (actionLabel != null && onAction != null) Button(onClick = onAction) { Text(actionLabel) }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) =
    // heading(): con un lector de pantalla se puede saltar de sección en sección.
    Text(text, modifier.padding(top = Spacing.l, bottom = Spacing.s).semantics { heading() },
        style = MaterialTheme.typography.titleMedium)

@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

/** Tarjeta con título de sección. */
@Composable
fun SectionCard(title: String?, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            if (title != null) Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

/** Fila "etiqueta: valor" de las pantallas de detalle. */
@Composable
fun InfoRow(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier, shape = MaterialTheme.shapes.medium, elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Column(Modifier.padding(Spacing.m)) {
            Text(value, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.secondary)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun ConfirmDialog(
    title: String, body: String, confirmLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit,
    destructive: Boolean = true,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

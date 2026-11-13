package com.travelindex.app.ui.modules

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.travelindex.app.ui.theme.Spacing

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

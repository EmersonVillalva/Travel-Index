package com.travelindex.app.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DirectionsBoat
import androidx.compose.material.icons.outlined.DirectionsBus
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.Train
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.travelindex.app.R
import com.travelindex.app.domain.TravelMode
import com.travelindex.app.domain.TravelModes

fun TravelMode.icon(): ImageVector = when (this) {
    TravelMode.PLANE -> Icons.Outlined.Flight
    TravelMode.SHIP -> Icons.Outlined.DirectionsBoat
    TravelMode.TRAIN -> Icons.Outlined.Train
    TravelMode.BUS -> Icons.Outlined.DirectionsBus
    TravelMode.CAR -> Icons.Outlined.DirectionsCar
}

@StringRes
fun TravelMode.labelRes(): Int = when (this) {
    TravelMode.PLANE -> R.string.mode_plane
    TravelMode.SHIP -> R.string.mode_ship
    TravelMode.TRAIN -> R.string.mode_train
    TravelMode.BUS -> R.string.mode_bus
    TravelMode.CAR -> R.string.mode_car
}

/** Selector de medios de transporte del viaje: se puede marcar más de uno. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TravelModePicker(selected: List<TravelMode>, onToggle: (TravelMode) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TravelMode.entries.forEach { mode ->
            FilterChip(
                selected = mode in selected,
                onClick = { onToggle(mode) },
                label = { Text(stringResource(mode.labelRes())) },
                leadingIcon = { Icon(mode.icon(), contentDescription = null, modifier = Modifier.size(18.dp)) },
            )
        }
    }
}

/** Fila pequeña de iconos con los medios del viaje (tarjeta del viaje). No dibuja nada si no hay ninguno. */
@Composable
fun TravelModeIcons(csv: String, tint: Color, modifier: Modifier = Modifier) {
    val modes = TravelModes.parse(csv)
    if (modes.isEmpty()) return
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        modes.forEach { mode ->
            Icon(mode.icon(), contentDescription = stringResource(mode.labelRes()), tint = tint, modifier = Modifier.size(16.dp))
        }
    }
}

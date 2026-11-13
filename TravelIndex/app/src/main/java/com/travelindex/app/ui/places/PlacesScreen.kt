package com.travelindex.app.ui.places

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.travelindex.app.R
import com.travelindex.app.domain.Airports
import com.travelindex.app.domain.forms.FlightCodec
import com.travelindex.app.ui.components.EmptyState
import com.travelindex.app.ui.components.LoadingBox
import com.travelindex.app.ui.modules.FlightRouteCard
import com.travelindex.app.ui.modules.TripBundleViewModel
import com.travelindex.app.ui.modules.durationText
import com.travelindex.app.ui.theme.CenteredContent
import com.travelindex.app.ui.theme.LocalFormats
import com.travelindex.app.ui.theme.Spacing
import java.time.Instant
import java.time.ZoneId

/**
 * Mapa de vuelos: cada vuelo del viaje como una flecha de vuelo de origen a destino (con nombres de aeropuerto, horas y duración).
 * No se incrusta un mapa de calles: no hace falta ninguna clave ni servicio externo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlacesScreen(onBack: () -> Unit, onOpenItem: (Long) -> Unit, vm: TripBundleViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val bundle = state.bundle
    val formats = LocalFormats.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.mod_places)) },
                navigationIcon = { IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } },
            )
        },
    ) { pad ->
        if (bundle == null) { LoadingBox(Modifier.padding(pad)); return@Scaffold }
        CenteredContent(Modifier.padding(pad)) {
            if (bundle.flights.isEmpty()) {
                EmptyState(stringResource(R.string.places_empty_title), stringResource(R.string.places_empty_body), icon = Icons.Outlined.Flight)
            } else {
                LazyColumn(contentPadding = PaddingValues(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                    items(bundle.flights.sortedBy { it.departureEpochMs }, key = { it.id }) { f ->
                        val dep = Instant.ofEpochMilli(f.departureEpochMs).atZone(ZoneId.of(f.departureZoneId))
                        val arr = Instant.ofEpochMilli(f.arrivalEpochMs).atZone(ZoneId.of(f.arrivalZoneId))
                        val hasArrival = FlightCodec.hasArrival(f)
                        Column(Modifier.clickable { onOpenItem(f.id) }, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            Text(
                                listOf(f.airline, f.flightNumber).filter { it.isNotBlank() }.joinToString(" "),
                                style = MaterialTheme.typography.titleSmall,
                            )
                            FlightRouteCard(
                                from = Airports.displayName(f.departureAirport), to = Airports.displayName(f.arrivalAirport),
                                depClock = formats.date(dep.toLocalDate()) + " " + formats.time(dep),
                                arrClock = if (hasArrival) formats.date(arr.toLocalDate()) + " " + formats.time(arr) else "",
                                duration = if (hasArrival) durationText(FlightCodec.durationMinutes(f)) else null,
                            )
                        }
                    }
                }
            }
        }
    }
}

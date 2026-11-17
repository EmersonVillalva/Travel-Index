package com.travelindex.app.ui.itinerary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.travelindex.app.R
import com.travelindex.app.data.db.TripEntity
import com.travelindex.app.domain.ItineraryBuilder
import com.travelindex.app.domain.ItineraryDay
import com.travelindex.app.domain.ItineraryEvent
import com.travelindex.app.domain.TripBundle
import com.travelindex.app.ui.components.EmptyState
import com.travelindex.app.ui.components.LoadingBox
import com.travelindex.app.ui.modules.EventRow
import com.travelindex.app.ui.modules.TripBundleViewModel
import com.travelindex.app.ui.theme.CenteredContent
import com.travelindex.app.ui.theme.LocalFormats
import com.travelindex.app.ui.theme.Spacing
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Itinerario del viaje en tres vistas: general (todos los días), por día y cronológica. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItineraryScreen(
    onBack: () -> Unit,
    onOpenEvent: (ItineraryEvent) -> Unit,
    vm: TripBundleViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val bundle = state.bundle
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.mod_itinerary)) },
                navigationIcon = { IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } },
            )
        },
    ) { pad ->
        if (bundle == null) { LoadingBox(Modifier.padding(pad)); return@Scaffold }
        Column(Modifier.padding(pad).fillMaxSize()) {
            TabRow(selectedTabIndex = tab) {
                listOf(R.string.it_tab_overview, R.string.it_tab_day, R.string.it_tab_chrono).forEachIndexed { i, r ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(stringResource(r)) })
                }
            }
            if (bundle.events.isEmpty()) {
                EmptyState(
                    stringResource(R.string.it_empty_title), stringResource(R.string.it_empty_body),
                    icon = Icons.Outlined.Timeline,
                )
            } else {
                CenteredContent {
                    when (tab) {
                        0 -> Overview(bundle, onOpenEvent)
                        1 -> ByDay(bundle, onOpenEvent)
                        else -> Chronological(bundle, onOpenEvent)
                    }
                }
            }
        }
    }
}

@Composable
private fun dayTitle(trip: TripEntity, date: LocalDate): String {
    val f = LocalFormats.current
    val n = ChronoUnit.DAYS.between(trip.startDate, date).toInt() + 1
    val inTrip = !date.isBefore(trip.startDate) && !date.isAfter(trip.endDate)
    return if (inTrip) stringResource(R.string.it_day_n, n, f.full(date)) else f.full(date)
}

@Composable
private fun Overview(b: TripBundle, onOpen: (ItineraryEvent) -> Unit) {
    val days = remember(b.events, b.trip.startDate, b.trip.endDate) {
        ItineraryBuilder.fullDays(b.trip.startDate, b.trip.endDate, b.events)
    }
    LazyColumn(
        contentPadding = PaddingValues(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        days.forEach { day ->
            item(key = "h" + day.date) {
                Text(dayTitle(b.trip, day.date), Modifier.padding(top = Spacing.s), style = MaterialTheme.typography.titleMedium)
            }
            if (day.events.isEmpty()) {
                item(key = "e" + day.date) {
                    Text(stringResource(R.string.it_no_plans), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                items(day.events, key = { it.key }) { e -> EventRow(e, onClick = { onOpen(e) }) }
            }
        }
    }
}

@Composable
private fun ByDay(b: TripBundle, onOpen: (ItineraryEvent) -> Unit) {
    val f = LocalFormats.current
    val days: List<ItineraryDay> = remember(b.events, b.trip.startDate, b.trip.endDate) {
        ItineraryBuilder.fullDays(b.trip.startDate, b.trip.endDate, b.events)
    }
    // Se recuerda la fecha elegida (ISO). Si no existe ya, se usa hoy (si está en el viaje) o el primer día.
    var selected by rememberSaveable { mutableStateOf("") }
    val today = LocalDate.now()
    val current = days.firstOrNull { it.date.toString() == selected }
        ?: days.firstOrNull { it.date == today } ?: days.first()

    Column(Modifier.fillMaxSize()) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = Spacing.m, vertical = Spacing.s),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            items(days, key = { it.date.toString() }) { d ->
                FilterChip(
                    selected = d.date == current.date,
                    onClick = { selected = d.date.toString() },
                    label = { Text(f.shortDay(d.date) + if (d.events.isNotEmpty()) " · ${d.events.size}" else "") },
                )
            }
        }
        LazyColumn(
            contentPadding = PaddingValues(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            item { Text(dayTitle(b.trip, current.date), style = MaterialTheme.typography.titleMedium) }
            if (current.events.isEmpty()) {
                item { Text(stringResource(R.string.it_no_plans), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                items(current.events, key = { it.key }) { e -> EventRow(e, onClick = { onOpen(e) }) }
            }
        }
    }
}

@Composable
private fun Chronological(b: TripBundle, onOpen: (ItineraryEvent) -> Unit) {
    val sorted = remember(b.events) { b.events.sortedWith(ItineraryBuilder.ORDER) }
    LazyColumn(
        contentPadding = PaddingValues(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        items(sorted, key = { it.key }) { e -> EventRow(e, onClick = { onOpen(e) }, showDate = true) }
    }
}

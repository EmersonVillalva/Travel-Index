package com.travelindex.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Luggage
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.travelindex.app.R
import com.travelindex.app.domain.AttentionItem
import com.travelindex.app.domain.AttentionKind
import com.travelindex.app.domain.DayEntry
import com.travelindex.app.domain.HomeSummary
import com.travelindex.app.ui.components.EmptyState
import com.travelindex.app.ui.components.LoadingBox
import com.travelindex.app.ui.components.Pill
import com.travelindex.app.ui.components.SectionTitle
import com.travelindex.app.ui.components.StatTile
import com.travelindex.app.ui.components.TripCard
import com.travelindex.app.ui.modules.EventRow
import com.travelindex.app.ui.modules.docWarningColor
import com.travelindex.app.ui.modules.docWarningLabel
import com.travelindex.app.ui.theme.LocalFormats
import com.travelindex.app.ui.theme.Spacing
import com.travelindex.app.ui.theme.StatusWarn
import java.time.LocalDate

private const val PAST_PREVIEW = 4

/** Inicio: saludo, viaje destacado, próximos eventos, pendientes, estadísticas, próximos y pasados. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenTrip: (Long) -> Unit,
    onNewTrip: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenTrips: () -> Unit,
    onOpenEvent: (DayEntry) -> Unit,
    onOpenAttention: (AttentionItem) -> Unit,
    vm: HomeViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val f = LocalFormats.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onOpenSearch) { Icon(Icons.Filled.Search, stringResource(R.string.action_search)) }
                },
            )
        },
        floatingActionButton = {
            if (state.hasTrips) ExtendedFloatingActionButton(
                onClick = onNewTrip, icon = { Icon(Icons.Filled.Add, null) },
                text = { Text(stringResource(R.string.action_new_trip)) },
            )
        },
    ) { pad ->
        if (state.loading) { LoadingBox(Modifier.padding(pad)); return@Scaffold }
        val summary = state.summary
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 320.dp),
            modifier = Modifier.padding(pad),
            contentPadding = PaddingValues(start = Spacing.m, end = Spacing.m, top = Spacing.s, bottom = 96.dp),
            horizontalArrangement = Arrangement.spacedBy(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    Text(
                        if (state.userName.isBlank()) stringResource(R.string.home_greeting_anon)
                        else stringResource(R.string.home_greeting, state.userName),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(f.full(LocalDate.now()), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.app_tagline), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary)
                }
            }

            if (!state.hasTrips || summary == null) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        EmptyState(
                            stringResource(R.string.home_empty_title), stringResource(R.string.home_empty_body),
                            icon = Icons.Outlined.Luggage,
                            actionLabel = stringResource(R.string.home_create_first), onAction = onNewTrip,
                        )
                    }
                }
            } else {
                content(summary, onOpenTrip, onOpenTrips, onOpenEvent, onOpenAttention)
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.grid.LazyGridScope.content(
    s: HomeSummary,
    onOpenTrip: (Long) -> Unit,
    onOpenTrips: () -> Unit,
    onOpenEvent: (DayEntry) -> Unit,
    onOpenAttention: (AttentionItem) -> Unit,
) {
    s.next?.let { b ->
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                SectionTitle(stringResource(R.string.home_next_trip), Modifier.padding(top = 0.dp))
                TripCard(b.trip, onClick = { onOpenTrip(b.trip.id) }, tall = true)
            }
        }
    }

    if (s.nextEvents.isNotEmpty()) {
        item(span = { GridItemSpan(maxLineSpan) }) { SectionTitle(stringResource(R.string.home_next_events)) }
        items(s.nextEvents, key = { it.trip.id.toString() + it.event.key }) { e ->
            EventRow(e.event, onClick = { onOpenEvent(e) }, showDate = true, tripName = e.trip.name)
        }
    }

    if (s.attention.isNotEmpty()) {
        item(span = { GridItemSpan(maxLineSpan) }) { SectionTitle(stringResource(R.string.attention_title)) }
        items(s.attention, key = { "a" + it.module.name + it.itemId }) { a ->
            Card(
                onClick = { onOpenAttention(a) }, shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = StatusWarn.copy(alpha = 0.14f)),
            ) {
                Row(Modifier.padding(Spacing.m), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                    Icon(Icons.Outlined.Warning, null, tint = StatusWarn)
                    Column(Modifier.weight(1f)) {
                        Text(a.title, style = MaterialTheme.typography.titleSmall)
                        Text(a.tripName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    when (a.kind) {
                        AttentionKind.FLIGHT_PENDING -> Pill(stringResource(R.string.attention_flight_unconfirmed), StatusWarn)
                        AttentionKind.DOCUMENT -> a.warning?.let { w ->
                            docWarningLabel(w)?.let { Pill(it, docWarningColor(w)) }
                        }
                    }
                }
            }
        }
    }

    item(span = { GridItemSpan(maxLineSpan) }) {
        Column {
            SectionTitle(stringResource(R.string.home_activity))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                StatTile(stringResource(R.string.stat_trips), s.stats.trips.toString(), Modifier.weight(1f))
                StatTile(stringResource(R.string.stat_countries), s.stats.countries.toString(), Modifier.weight(1f))
                StatTile(stringResource(R.string.stat_reservations), s.stats.reservations.toString(), Modifier.weight(1f))
            }
        }
    }

    if (s.upcomingTrips.isNotEmpty()) {
        item(span = { GridItemSpan(maxLineSpan) }) { SectionTitle(stringResource(R.string.group_upcoming)) }
        items(s.upcomingTrips, key = { "u" + it.id }) { t -> TripCard(t, onClick = { onOpenTrip(t.id) }) }
    }

    if (s.pastTrips.isNotEmpty()) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                SectionTitle(stringResource(R.string.group_past))
                if (s.pastTrips.size > PAST_PREVIEW) TextButton(onClick = onOpenTrips) { Text(stringResource(R.string.action_see_all)) }
            }
        }
        items(s.pastTrips.take(PAST_PREVIEW), key = { "p" + it.id }) { t -> TripCard(t, onClick = { onOpenTrip(t.id) }) }
    }
}

package com.travelindex.app.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.travelindex.app.R
import com.travelindex.app.domain.HistoryStats
import com.travelindex.app.ui.components.EmptyState
import com.travelindex.app.ui.components.LoadingBox
import com.travelindex.app.ui.components.SectionTitle
import com.travelindex.app.ui.components.StatTile
import com.travelindex.app.ui.components.TripCard
import com.travelindex.app.ui.components.pluralText
import com.travelindex.app.ui.theme.Spacing

/** Historial: los viajes que ya terminaron, por año, con un resumen de lo que has viajado. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(onBack: () -> Unit, onOpenTrip: (Long) -> Unit, vm: HistoryViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.history_title)) },
                navigationIcon = { IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } },
            )
        },
    ) { pad ->
        val history = state.history
        if (state.loading || history == null) { LoadingBox(Modifier.padding(pad)); return@Scaffold }
        if (history.isEmpty) {
            Column(Modifier.padding(pad).fillMaxSize()) {
                EmptyState(
                    stringResource(R.string.history_empty_title), stringResource(R.string.history_empty_body),
                    icon = Icons.Outlined.History,
                )
            }
            return@Scaffold
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 320.dp),
            modifier = Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(start = Spacing.m, end = Spacing.m, top = Spacing.s, bottom = Spacing.xl),
            horizontalArrangement = Arrangement.spacedBy(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            item(key = "stats", span = { GridItemSpan(maxLineSpan) }) { StatsBlock(history.stats, state.paidTotal) }
            history.years.forEach { year ->
                item(key = "year-${year.year}", span = { GridItemSpan(maxLineSpan) }) {
                    SectionTitle(
                        year.year.toString() + " · " + pluralText(R.plurals.history_trips, year.trips.size),
                        Modifier.padding(top = Spacing.xs),
                    )
                }
                items(year.trips, key = { it.trip.id }) { b ->
                    TripCard(b.trip, onClick = { onOpenTrip(b.trip.id) }, paidText = state.paid[b.trip.id])
                }
            }
        }
    }
}

@Composable
private fun StatsBlock(stats: HistoryStats, paidTotal: String) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            StatTile(stringResource(R.string.history_stat_trips), stats.trips.toString(), Modifier.weight(1f))
            StatTile(stringResource(R.string.history_stat_days), stats.days.toString(), Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            StatTile(stringResource(R.string.history_stat_countries), stats.countries.toString(), Modifier.weight(1f))
            StatTile(stringResource(R.string.history_stat_cities), stats.cities.toString(), Modifier.weight(1f))
        }
        if (paidTotal.isNotBlank()) {
            Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
                Column(Modifier.fillMaxWidth().padding(Spacing.m)) {
                    Text(paidTotal, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.secondary)
                    Text(stringResource(R.string.history_paid_total), style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

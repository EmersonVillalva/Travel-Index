package com.travelindex.app.ui.trips

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Luggage
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.travelindex.app.R
import com.travelindex.app.domain.TripFilter
import com.travelindex.app.domain.TripGroup
import com.travelindex.app.domain.TripSort
import com.travelindex.app.ui.components.EmptyState
import com.travelindex.app.ui.components.LoadingBox
import com.travelindex.app.ui.components.SectionTitle
import com.travelindex.app.ui.components.TripCard
import com.travelindex.app.ui.theme.Spacing

@Composable
fun groupTitle(g: TripGroup): String = stringResource(
    when (g) {
        TripGroup.ONGOING -> R.string.group_ongoing
        TripGroup.UPCOMING -> R.string.group_upcoming
        TripGroup.PAST -> R.string.group_past
        TripGroup.CANCELLED -> R.string.group_cancelled
    }
)

/** Mis viajes: todos los viajes (próximos, en curso, pasados y cancelados) con búsqueda, filtros y orden. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripsScreen(
    onOpenTrip: (Long) -> Unit, onNewTrip: () -> Unit, onOpenHistory: () -> Unit, vm: TripsViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var sortMenu by remember { mutableStateOf(false) }
    // Importar un viaje que te han compartido: se elige el archivo y la ventana de importación pregunta si añadirlo.
    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) vm.importFile(uri) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_trips)) },
                actions = {
                    IconButton(onClick = onOpenHistory) { Icon(Icons.Outlined.History, stringResource(R.string.history_action)) }
                    IconButton(onClick = { importPicker.launch(arrayOf("*/*")) }) {
                        Icon(Icons.Outlined.Download, stringResource(R.string.action_import_trip))
                    }
                    if (state.total > 1) {
                        IconButton(onClick = { sortMenu = true }) { Icon(Icons.Outlined.Sort, stringResource(R.string.action_sort)) }
                        DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                            listOf(
                                TripSort.SMART to R.string.sort_smart, TripSort.DATE_ASC to R.string.sort_date_asc,
                                TripSort.DATE_DESC to R.string.sort_date_desc, TripSort.NAME to R.string.sort_name,
                            ).forEach { (s, label) ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(label)) },
                                    leadingIcon = { RadioButton(selected = state.sort == s, onClick = null) },
                                    onClick = { vm.setSort(s); sortMenu = false },
                                )
                            }
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNewTrip, icon = { Icon(Icons.Filled.Add, null) },
                text = { Text(stringResource(R.string.action_new_trip)) },
            )
        },
    ) { pad ->
        if (state.loading) { LoadingBox(Modifier.padding(pad)); return@Scaffold }
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (state.total == 0) {
                EmptyState(
                    stringResource(R.string.trips_empty_title), stringResource(R.string.trips_empty_body),
                    icon = Icons.Outlined.Luggage,
                    actionLabel = stringResource(R.string.action_new_trip), onAction = onNewTrip,
                )
            } else {
                OutlinedTextField(
                    value = state.query, onValueChange = vm::setQuery, singleLine = true,
                    placeholder = { Text(stringResource(R.string.trips_search_hint)) },
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    trailingIcon = {
                        if (state.query.isNotEmpty()) IconButton(onClick = { vm.setQuery("") }) {
                            Icon(Icons.Filled.Close, stringResource(R.string.a11y_clear))
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.m, vertical = Spacing.xs),
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = Spacing.m, vertical = Spacing.xs),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                ) {
                    items(
                        listOf(
                            TripFilter.ALL to R.string.filter_all, TripFilter.UPCOMING to R.string.filter_upcoming,
                            TripFilter.PAST to R.string.filter_past, TripFilter.CANCELLED to R.string.filter_cancelled,
                        ).filter { (f, _) -> f == TripFilter.ALL || (state.counts[f] ?: 0) > 0 || state.filter == f },
                        key = { it.first.name },
                    ) { (f, label) ->
                        FilterChip(
                            selected = state.filter == f, onClick = { vm.setFilter(f) },
                            label = { Text(stringResource(label) + " · " + (state.counts[f] ?: 0)) },
                        )
                    }
                }
                if (state.sections.isEmpty()) {
                    EmptyState(
                        stringResource(R.string.trips_no_results_title), stringResource(R.string.trips_no_results_body),
                        icon = Icons.Outlined.SearchOff,
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 320.dp),
                        contentPadding = PaddingValues(start = Spacing.m, end = Spacing.m, top = Spacing.xs, bottom = 96.dp),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.m),
                    ) {
                        state.sections.forEach { section ->
                            item(key = "h-" + section.group.name, span = { GridItemSpan(maxLineSpan) }) {
                                SectionTitle(groupTitle(section.group) + " · " + section.trips.size, Modifier.padding(top = Spacing.xs))
                            }
                            items(section.trips, key = { it.id }) { trip -> TripCard(trip, onClick = { onOpenTrip(trip.id) }, paidText = state.paid[trip.id]) }
                        }
                    }
                }
            }
        }
    }
}

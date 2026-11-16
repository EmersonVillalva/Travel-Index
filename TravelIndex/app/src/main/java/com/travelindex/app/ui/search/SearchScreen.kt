package com.travelindex.app.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Luggage
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.travelindex.app.R
import com.travelindex.app.domain.SearchHit
import com.travelindex.app.domain.SearchKind
import com.travelindex.app.domain.ViewerSupport
import com.travelindex.app.ui.components.EmptyState
import com.travelindex.app.ui.components.SectionTitle
import com.travelindex.app.ui.components.iconForMime
import com.travelindex.app.ui.theme.Spacing

/** Buscador global: viajes, reservas, lugares y archivos, agrupados por viaje. Tocar un resultado abre su pantalla. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onOpenTrip: (Long) -> Unit,
    onOpenItem: (Long, String, Long) -> Unit,
    onOpenFiles: (Long) -> Unit,
    onOpenViewer: (Long) -> Unit,
    vm: SearchViewModel = hiltViewModel(),
) {
    val query by vm.query.collectAsStateWithLifecycle()
    val hits by vm.hits.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.action_search)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) }
                },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            OutlinedTextField(
                value = query, onValueChange = vm::setQuery, singleLine = true,
                placeholder = { Text(stringResource(R.string.search_hint_global)) },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { vm.setQuery("") }) {
                        Icon(Icons.Filled.Close, stringResource(R.string.a11y_clear))
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.m, vertical = Spacing.xs).focusRequester(focus),
            )
            when {
                query.trim().length < 2 -> EmptyState(
                    stringResource(R.string.action_search), stringResource(R.string.search_prompt), icon = Icons.Filled.Search,
                )
                hits.isEmpty() -> EmptyState(
                    stringResource(R.string.trips_no_results_title), stringResource(R.string.trips_no_results_body),
                    icon = Icons.Outlined.SearchOff,
                )
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    hits.groupBy { it.tripId }.forEach { (_, tripHits) ->
                        item { SectionTitle(tripHits.first().tripName, Modifier.padding(horizontal = Spacing.m)) }
                        for (h in tripHits) item {
                            HitRow(h) {
                                when (h.kind) {
                                    SearchKind.TRIP -> onOpenTrip(h.tripId)
                                    SearchKind.ITEM -> onOpenItem(h.tripId, h.route.orEmpty(), h.itemId)
                                    SearchKind.FILE ->
                                        if (ViewerSupport.opensInternally(h.mime.orEmpty())) onOpenViewer(h.itemId)
                                        else onOpenFiles(h.tripId)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HitRow(h: SearchHit, onClick: () -> Unit) {
    val icon = when (h.kind) {
        SearchKind.TRIP -> Icons.Outlined.Luggage
        SearchKind.ITEM -> Icons.Outlined.Receipt
        SearchKind.FILE -> iconForMime(h.mime.orEmpty())
    }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = Spacing.m, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
        Column(Modifier.weight(1f)) {
            Text(h.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (h.subtitle != null) Text(h.subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

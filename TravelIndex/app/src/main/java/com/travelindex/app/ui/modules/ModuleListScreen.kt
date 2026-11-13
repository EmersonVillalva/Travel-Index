package com.travelindex.app.ui.modules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.travelindex.app.domain.ModuleType
import com.travelindex.app.domain.forms.ModuleItems
import com.travelindex.app.ui.components.EmptyState
import com.travelindex.app.ui.components.LoadingBox
import com.travelindex.app.ui.theme.Spacing
import java.time.LocalDate

/** Lista de los elementos de un módulo (vuelos, alojamientos...) con botón para añadir. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModuleListScreen(
    type: ModuleType,
    onBack: () -> Unit,
    onOpen: (Long) -> Unit,
    onAdd: () -> Unit,
    vm: TripBundleViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val spec = ModuleSpecs.of(type)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(spec.titleRes)) },
                navigationIcon = { IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onAdd, icon = { Icon(Icons.Filled.Add, null) },
                text = { Text(stringResource(R.string.action_add)) })
        },
    ) { pad ->
        val bundle = state.bundle
        if (!state.loaded) { LoadingBox(Modifier.padding(pad)); return@Scaffold }
        val list = if (bundle == null) emptyList() else ModuleItems.of(type, bundle)
        val today = LocalDate.now()
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 340.dp),
            modifier = Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(start = Spacing.m, end = Spacing.m, top = Spacing.m, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            if (list.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(
                        stringResource(spec.emptyTitleRes), stringResource(spec.emptyBodyRes), icon = spec.icon,
                        actionLabel = stringResource(spec.newTitleRes), onAction = onAdd,
                    )
                }
            }
            items(list, key = { it.id }) { item ->
                ModuleItemCard(spec, item, today, bundle?.trip?.endDate, onClick = { onOpen(item.id) })
            }
        }
    }
}

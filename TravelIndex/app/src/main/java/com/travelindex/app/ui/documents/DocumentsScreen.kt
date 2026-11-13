package com.travelindex.app.ui.documents

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.travelindex.app.R
import com.travelindex.app.data.db.AttachmentEntity
import com.travelindex.app.domain.AttachmentCategory
import com.travelindex.app.domain.ModuleType
import com.travelindex.app.domain.TripBundle
import com.travelindex.app.domain.TripListBuilder
import com.travelindex.app.domain.forms.ModuleItems
import com.travelindex.app.ui.components.ChoiceDialog
import com.travelindex.app.ui.components.ConfirmDialog
import com.travelindex.app.ui.components.EmptyState
import com.travelindex.app.ui.components.LoadingBox
import com.travelindex.app.ui.components.Option
import com.travelindex.app.ui.components.SectionTitle
import com.travelindex.app.ui.files.CategoryDialog
import com.travelindex.app.ui.files.FileCard
import com.travelindex.app.ui.files.RenameDialog
import com.travelindex.app.ui.files.categoryLabel
import com.travelindex.app.ui.files.ownerKeyOf
import com.travelindex.app.ui.files.ownerOptions
import com.travelindex.app.ui.modules.ModuleItemCard
import com.travelindex.app.ui.modules.ModuleSpecs
import com.travelindex.app.ui.theme.CenteredContent
import com.travelindex.app.ui.theme.Spacing
import java.time.LocalDate

/**
 * Documentos de todos los viajes: documentos de viaje (visados, pasaportes...) con sus avisos y todos los
 * archivos guardados. Para agregar a un viaje concreto también se puede usar el botón «Agregar» de su panel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentsScreen(
    onOpenItem: (tripId: Long, ModuleType, itemId: Long) -> Unit,
    onNewTrip: () -> Unit,
    vm: DocumentsViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("") }
    var pendingTrip by rememberSaveable { mutableStateOf<Long?>(null) }
    var choosingTrip by rememberSaveable { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<AttachmentEntity?>(null) }
    var categorizing by remember { mutableStateOf<AttachmentEntity?>(null) }
    var associating by remember { mutableStateOf<Pair<TripBundle, AttachmentEntity>?>(null) }
    var deleting by remember { mutableStateOf<AttachmentEntity?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        val trip = pendingTrip
        if (trip != null) vm.add(trip, uris)
        pendingTrip = null
    }
    val addedMsg = stringResource(R.string.file_added)
    val failedMsg = stringResource(R.string.error_attach)
    LaunchedEffect(Unit) { vm.results.collect { r -> snackbar.showSnackbar(if (r.failed > 0) failedMsg else addedMsg) } }

    fun startAdd() {
        val trips = state.bundles
        when {
            trips.isEmpty() -> Unit   // sin viajes no hay donde guardar: la pantalla ya lo explica
            trips.size == 1 -> { pendingTrip = trips[0].trip.id; picker.launch(arrayOf("*/*")) }
            else -> choosingTrip = true
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { TopAppBar(title = { Text(stringResource(R.string.nav_documents)) }) },
        floatingActionButton = {
            if (!state.loading && state.bundles.isNotEmpty()) ExtendedFloatingActionButton(
                onClick = ::startAdd, icon = { Icon(Icons.Filled.Add, null) },
                text = { Text(stringResource(R.string.action_add_file)) },
            )
        },
    ) { pad ->
        if (state.loading) { LoadingBox(Modifier.padding(pad)); return@Scaffold }
        val bundles = state.bundles
        val today = LocalDate.now()
        val allFiles = bundles.flatMap { b -> b.attachments.map { b to it } }
        val allDocs = bundles.flatMap { b -> b.docs.map { b to it } }

        CenteredContent(Modifier.padding(pad)) {
            Column(Modifier.fillMaxSize()) {
                if (bundles.isEmpty()) {
                    EmptyState(
                        stringResource(R.string.docs_empty_no_trips_title), stringResource(R.string.docs_empty_no_trips_body),
                        icon = Icons.Outlined.Description,
                        actionLabel = stringResource(R.string.action_new_trip), onAction = onNewTrip,
                    )
                } else if (allFiles.isEmpty() && allDocs.isEmpty()) {
                    EmptyState(
                        stringResource(R.string.docs_empty_title), stringResource(R.string.docs_empty_body),
                        icon = Icons.Outlined.Description,
                        actionLabel = stringResource(R.string.action_add_file), onAction = ::startAdd,
                    )
                } else {
                    OutlinedTextField(
                        value = query, onValueChange = { query = it.take(80) }, singleLine = true,
                        placeholder = { Text(stringResource(R.string.docs_search_hint)) },
                        leadingIcon = { Icon(Icons.Filled.Search, null) },
                        trailingIcon = {
                            if (query.isNotEmpty()) IconButton(onClick = { query = "" }) {
                                Icon(Icons.Filled.Close, stringResource(R.string.a11y_clear))
                            }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.m, vertical = Spacing.xs),
                    )
                    val categoriesPresent = AttachmentCategory.entries.filter { c -> allFiles.any { it.second.category == c.name } }
                    if (categoriesPresent.size > 1) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = Spacing.m, vertical = Spacing.xs),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                        ) {
                            item { FilterChip(selected = filter.isEmpty(), onClick = { filter = "" },
                                label = { Text(stringResource(R.string.filter_all)) }) }
                            items(categoriesPresent, key = { it.name }) { c ->
                                FilterChip(selected = filter == c.name, onClick = { filter = c.name }, label = { Text(categoryLabel(c)) })
                            }
                        }
                    }

                    val q = query
                    val docSpec = ModuleSpecs.of(ModuleType.TRAVEL_DOC)
                    val docItems = if (filter.isNotEmpty()) emptyList() else bundles.flatMap { b ->
                        ModuleItems.of(ModuleType.TRAVEL_DOC, b)
                            .filter { q.isBlank() || TripListBuilder.normalize(listOf(b.trip.name, it.values["title"].orEmpty(),
                                it.values["issuingCountry"].orEmpty()).joinToString(" ")).contains(TripListBuilder.normalize(q)) }
                            .map { b to it }
                    }
                    val ownersByTrip = bundles.associate { it.trip.id to ownerOptions(it) }
                    val fileGroups = bundles.mapNotNull { b ->
                        val owners = ownersByTrip[b.trip.id].orEmpty()
                        val list = b.attachments.filter { att ->
                            (filter.isEmpty() || att.category == filter) &&
                                (q.isBlank() || TripListBuilder.normalize(att.displayName + " " + b.trip.name + " " +
                                    owners.firstOrNull { it.key.isNotEmpty() && it.key == ownerKeyOf(att) }?.label.orEmpty())
                                    .contains(TripListBuilder.normalize(q)))
                        }
                        if (list.isEmpty()) null else b to list
                    }

                    if (docItems.isEmpty() && fileGroups.isEmpty()) {
                        EmptyState(stringResource(R.string.trips_no_results_title), stringResource(R.string.trips_no_results_body))
                    }
                    LazyColumn(
                        contentPadding = PaddingValues(start = Spacing.m, end = Spacing.m, top = Spacing.xs, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(Spacing.s),
                    ) {
                        if (docItems.isNotEmpty()) {
                            item { SectionTitle(stringResource(R.string.docs_travel_docs), Modifier.padding(top = 0.dp)) }
                            items(docItems, key = { "d" + it.first.trip.id + "-" + it.second.id }) { (b, item) ->
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(b.trip.name, style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    ModuleItemCard(docSpec, item, today, b.trip.endDate,
                                        onClick = { onOpenItem(b.trip.id, ModuleType.TRAVEL_DOC, item.id) })
                                }
                            }
                        }
                        fileGroups.forEach { (b, list) ->
                            item(key = "h" + b.trip.id) { SectionTitle(stringResource(R.string.docs_files_of, b.trip.name)) }
                            items(list, key = { "f" + it.id }) { att ->
                                val owners = ownersByTrip[b.trip.id].orEmpty()
                                FileCard(
                                    att = att,
                                    ownerLabel = owners.firstOrNull { it.key.isNotEmpty() && it.key == ownerKeyOf(att) }?.label,
                                    onRename = { renaming = att }, onCategory = { categorizing = att },
                                    onAssociate = { associating = b to att }, onDelete = { deleting = att },
                                )
                            }
                        }
                    }
                }
            }
        }

        if (choosingTrip) {
            ChoiceDialog(
                title = stringResource(R.string.docs_choose_trip),
                options = bundles.map { Option(it.trip.id.toString(), it.trip.name) }, selected = null,
                searchable = bundles.size > 8, onDismiss = { choosingTrip = false },
                onPick = { key -> choosingTrip = false; pendingTrip = key.toLongOrNull(); picker.launch(arrayOf("*/*")) },
            )
        }
        renaming?.let { att ->
            RenameDialog(att.displayName, onDismiss = { renaming = null }, onConfirm = { vm.rename(att, it); renaming = null })
        }
        categorizing?.let { att ->
            CategoryDialog(att.category, onDismiss = { categorizing = null }, onPick = { vm.setCategory(att, it); categorizing = null })
        }
        associating?.let { (b, att) ->
            val owners = ownerOptions(b)
            ChoiceDialog(
                title = stringResource(R.string.action_associate), options = owners, selected = ownerKeyOf(att),
                searchable = owners.size > 8, onDismiss = { associating = null },
                onPick = { vm.associate(att, it); associating = null },
            )
        }
        deleting?.let { att ->
            ConfirmDialog(
                title = stringResource(R.string.delete_file_title), body = stringResource(R.string.delete_file_body, att.displayName),
                confirmLabel = stringResource(R.string.action_delete),
                onConfirm = { vm.delete(att); deleting = null }, onDismiss = { deleting = null },
            )
        }
    }
}

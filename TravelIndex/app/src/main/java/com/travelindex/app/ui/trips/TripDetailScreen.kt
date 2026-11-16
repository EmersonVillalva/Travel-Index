package com.travelindex.app.ui.trips

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.travelindex.app.R
import com.travelindex.app.data.FileActions
import com.travelindex.app.data.TripShareManager
import com.travelindex.app.domain.CostBreakdown
import com.travelindex.app.domain.TripShareFormat
import com.travelindex.app.domain.TripStatus
import com.travelindex.app.domain.DocWarning
import com.travelindex.app.domain.DocWarnings
import com.travelindex.app.domain.ItineraryEvent
import com.travelindex.app.domain.forms.TravelDocCodec
import com.travelindex.app.domain.ModuleType
import com.travelindex.app.domain.TravelModes
import com.travelindex.app.domain.TransportSection
import com.travelindex.app.domain.TransportSections
import com.travelindex.app.domain.TripBundle
import com.travelindex.app.domain.pdf.TripPdf
import com.travelindex.app.domain.tripDurationDays
import com.travelindex.app.ui.components.ConfirmDialog
import com.travelindex.app.ui.components.LoadingBox
import com.travelindex.app.ui.components.Pill
import com.travelindex.app.ui.components.SectionTitle
import com.travelindex.app.ui.components.StatTile
import com.travelindex.app.ui.components.TripCard
import com.travelindex.app.ui.components.pluralText
import com.travelindex.app.ui.components.rememberAddFileFlow
import com.travelindex.app.ui.modules.EventRow
import com.travelindex.app.ui.modules.ModuleSpecs
import com.travelindex.app.ui.modules.docWarningColor
import com.travelindex.app.ui.modules.docWarningLabel
import com.travelindex.app.ui.modules.moduleTitle
import com.travelindex.app.ui.theme.CenteredContent
import com.travelindex.app.ui.theme.LocalFormats
import com.travelindex.app.ui.theme.Spacing
import com.travelindex.app.ui.theme.StatusWarn
import java.io.File
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZonedDateTime

/** Todo lo que el panel del viaje puede abrir. La navegación lo conecta con las rutas reales. */
class TripActions(
    val onBack: () -> Unit,
    val onEdit: () -> Unit,
    val openItinerary: () -> Unit,
    val openList: (ModuleType) -> Unit,
    val addItem: (ModuleType) -> Unit,
    /** Abrir / agregar en un apartado de transporte (vuelos, trenes, autobuses, barcos, coche). */
    val openSection: (TransportSection) -> Unit,
    val addSection: (TransportSection) -> Unit,
    val openFiles: () -> Unit,
    val openEvent: (ItineraryEvent) -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripDetailScreen(actions: TripActions, vm: TripDetailViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var menuOpen by remember { mutableStateOf(false) }
    var showAdd by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var confirmCancel by rememberSaveable { mutableStateOf(false) }
    var confirmShare by rememberSaveable { mutableStateOf(false) }
    val sharing by vm.sharing.collectAsStateWithLifecycle()
    val pdfBusy by vm.pdfBusy.collectAsStateWithLifecycle()
    // El PDF ya creado: mientras haya uno, se ofrece abrirlo o compartirlo.
    var pdfFile by remember { mutableStateOf<File?>(null) }
    val ctx = LocalContext.current
    val shareFailed = stringResource(R.string.error_share_trip)
    val shareText = stringResource(R.string.share_trip_text, state.bundle?.trip?.name.orEmpty(), TripShareManager.PLAY_URL)
    val okMsg = stringResource(R.string.file_added)
    val errMsg = stringResource(R.string.error_attach)

    val addFile = rememberAddFileFlow { uris -> uris.forEach { vm.addFile(it) } }
    val pdfFailed = stringResource(R.string.error_pdf)
    val noAppToOpen = stringResource(R.string.error_no_app_to_open)
    val shareFailedMsg = stringResource(R.string.error_share)
    LaunchedEffect(Unit) { vm.messages.collect { ok -> snackbar.showSnackbar(if (ok) okMsg else errMsg) } }
    LaunchedEffect(Unit) {
        vm.pdfEvents.collect { e ->
            when (e) {
                is ShareEvent.Ready -> pdfFile = e.file
                ShareEvent.Failed -> snackbar.showSnackbar(pdfFailed)
            }
        }
    }
    LaunchedEffect(Unit) {
        vm.shareEvents.collect { e ->
            when (e) {
                is ShareEvent.Ready -> if (!FileActions.share(ctx, e.file, TripShareFormat.MIME, shareText)) snackbar.showSnackbar(shareFailed)
                ShareEvent.Failed -> snackbar.showSnackbar(shareFailed)
            }
        }
    }
    // Viaje borrado: se vuelve a la lista.
    LaunchedEffect(state.loaded, state.bundle) { if (state.loaded && state.bundle == null) actions.onBack() }

    val bundle = state.bundle
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(bundle?.trip?.name.orEmpty(), maxLines = 1) },
                navigationIcon = { IconButton(onClick = actions.onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } },
                actions = {
                    if (bundle != null) {
                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, stringResource(R.string.action_more)) }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.action_edit_trip)) },
                                onClick = { menuOpen = false; actions.onEdit() })
                            DropdownMenuItem(text = { Text(stringResource(R.string.action_share_trip)) },
                                onClick = { menuOpen = false; confirmShare = true })
                            DropdownMenuItem(text = { Text(stringResource(R.string.action_export_pdf)) },
                                onClick = { menuOpen = false; vm.exportPdf() })
                            DropdownMenuItem(
                                text = { Text(stringResource(if (bundle.trip.cancelled) R.string.action_reactivate_trip else R.string.action_cancel_trip)) },
                                onClick = { menuOpen = false; if (bundle.trip.cancelled) vm.setCancelled(false) else confirmCancel = true },
                            )
                            DropdownMenuItem(text = { Text(stringResource(R.string.action_delete_trip)) },
                                onClick = { menuOpen = false; confirmDelete = true })
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (bundle != null) ExtendedFloatingActionButton(
                onClick = { showAdd = true },
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text(stringResource(R.string.action_add)) },
            )
        },
    ) { pad ->
        if (bundle == null) { LoadingBox(Modifier.padding(pad)); return@Scaffold }
        Dashboard(bundle, actions, Modifier.padding(pad))
    }

    if (showAdd && bundle != null) {
        AddSheet(
            sections = TransportSections.offered(TravelModes.parse(bundle.trip.travelModes)),
            onDismiss = { showAdd = false },
            onPick = { choice ->
                showAdd = false
                when (choice) {
                    is AddChoice.Section -> actions.addSection(choice.section)
                    is AddChoice.Module -> actions.addItem(choice.type)
                    AddChoice.File -> addFile()
                }
            },
        )
    }
    if (confirmShare && bundle != null) ConfirmDialog(
        title = stringResource(R.string.share_trip_title), body = stringResource(R.string.share_trip_body),
        confirmLabel = stringResource(R.string.action_share), destructive = false,
        onConfirm = { confirmShare = false; vm.share() }, onDismiss = { confirmShare = false },
    )
    if (sharing) AlertDialog(
        onDismissRequest = {}, confirmButton = {},
        title = { Text(stringResource(R.string.share_preparing)) },
        text = { LinearProgressIndicator(Modifier.fillMaxWidth()) },
    )
    if (pdfBusy) AlertDialog(
        onDismissRequest = {}, confirmButton = {},
        title = { Text(stringResource(R.string.pdf_generating)) },
        text = { LinearProgressIndicator(Modifier.fillMaxWidth()) },
    )
    pdfFile?.let { file ->
        AlertDialog(
            onDismissRequest = { pdfFile = null },
            title = { Text(stringResource(R.string.pdf_ready_title)) },
            text = { Text(stringResource(R.string.pdf_ready_body)) },
            confirmButton = {
                TextButton(onClick = {
                    pdfFile = null
                    if (!FileActions.share(ctx, file, TripPdf.MIME)) scope.launch { snackbar.showSnackbar(shareFailedMsg) }
                }) { Text(stringResource(R.string.pdf_share)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    pdfFile = null
                    if (!FileActions.open(ctx, file, TripPdf.MIME)) scope.launch { snackbar.showSnackbar(noAppToOpen) }
                }) { Text(stringResource(R.string.pdf_open)) }
            },
        )
    }
    if (confirmDelete && bundle != null) ConfirmDialog(
        title = stringResource(R.string.delete_trip_title), body = stringResource(R.string.delete_trip_body, bundle.trip.name),
        confirmLabel = stringResource(R.string.action_delete),
        onConfirm = { confirmDelete = false; vm.delete() }, onDismiss = { confirmDelete = false },
    )
    if (confirmCancel && bundle != null) ConfirmDialog(
        title = stringResource(R.string.cancel_trip_title), body = stringResource(R.string.cancel_trip_body),
        confirmLabel = stringResource(R.string.action_cancel_trip), destructive = false,
        onConfirm = { confirmCancel = false; vm.setCancelled(true) }, onDismiss = { confirmCancel = false },
    )
}

private sealed interface AddChoice {
    data class Section(val section: TransportSection) : AddChoice
    data class Module(val type: ModuleType) : AddChoice
    data object File : AddChoice
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddSheet(sections: List<TransportSection>, onDismiss: () -> Unit, onPick: (AddChoice) -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    fun pick(c: AddChoice) { scope.launch { sheet.hide() }.invokeOnCompletion { onPick(c) } }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = Spacing.l)) {
            Text(stringResource(R.string.add_sheet_title), Modifier.padding(horizontal = Spacing.m, vertical = Spacing.s),
                style = MaterialTheme.typography.titleMedium)
            // Primero los medios de transporte del viaje (solo los que se eligieron), después lo demás.
            sections.forEach { s ->
                val spec = ModuleSpecs.of(s.module, s)
                SheetRow(spec.icon, stringResource(spec.newTitleRes)) { pick(AddChoice.Section(s)) }
            }
            listOf(
                ModuleType.LODGING, ModuleType.ACTIVITY, ModuleType.RESTAURANT, ModuleType.TRAVEL_DOC,
            ).forEach { t ->
                val spec = ModuleSpecs.of(t)
                SheetRow(spec.icon, stringResource(spec.newTitleRes)) { pick(AddChoice.Module(t)) }
            }
            SheetRow(Icons.Outlined.AttachFile, stringResource(R.string.add_file)) { pick(AddChoice.File) }
        }
    }
}

@Composable
private fun SheetRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = Spacing.m, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.secondary)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun Dashboard(b: TripBundle, a: TripActions, modifier: Modifier) {
    val today = LocalDate.now()
    val f = LocalFormats.current
    val trip = b.trip
    val warnings = b.docs.mapNotNull { d ->
        val w = DocWarnings.evaluate(d, trip.endDate, today)
        if (w == DocWarning.NONE) null else d to w
    }
    val pendingFlights = b.flights.count { it.status == "PENDING" }
    val now = ZonedDateTime.now()
    val next = if (trip.cancelled) null else b.events.filter { !it.start.isBefore(now) }.minByOrNull { it.start.toInstant() }
    val costs = if (b.status(today) == TripStatus.COMPLETED) CostBreakdown.of(b) else emptyList()

    CenteredContent(modifier, maxWidth = 920.dp) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 150.dp),
            contentPadding = PaddingValues(start = Spacing.m, end = Spacing.m, top = Spacing.s, bottom = 96.dp),
            horizontalArrangement = Arrangement.spacedBy(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) { TripCard(trip, onClick = null, tall = true) }

            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    StatTile(stringResource(R.string.stat_days), tripDurationDays(trip.startDate, trip.endDate).toString(), Modifier.weight(1f))
                    StatTile(stringResource(R.string.stat_travelers), trip.travelers.toString(), Modifier.weight(1f))
                    StatTile(stringResource(R.string.stat_reservations), b.reservationsCount.toString(), Modifier.weight(1f))
                }
            }

            if (trip.description.isNotBlank()) item(span = { GridItemSpan(maxLineSpan) }) {
                Text(trip.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (warnings.isNotEmpty() || pendingFlights > 0) item(span = { GridItemSpan(maxLineSpan) }) {
                Card(
                    onClick = { a.openList(ModuleType.TRAVEL_DOC) }, shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = StatusWarn.copy(alpha = 0.16f)),
                ) {
                    Column(Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                            Icon(Icons.Outlined.Warning, null, tint = StatusWarn)
                            Text(stringResource(R.string.attention_title), style = MaterialTheme.typography.titleMedium)
                        }
                        warnings.forEach { (d, w) ->
                            val label = docWarningLabel(w)
                            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
                                Text(moduleTitle(ModuleSpecs.of(ModuleType.TRAVEL_DOC),
                                    TravelDocCodec.values(d)),
                                    Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                if (label != null) Pill(label, docWarningColor(w))
                            }
                        }
                        if (pendingFlights > 0) Text(pluralText(R.plurals.attention_flights_pending, pendingFlights),
                            style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            if (next != null) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column {
                        SectionTitle(stringResource(R.string.next_event), Modifier.padding(top = Spacing.s))
                        EventRow(next, onClick = { a.openEvent(next) }, showDate = true)
                    }
                }
            }

            // Viajes pasados: lo que se pagó, separado por vuelos, alojamiento y el resto.
            if (costs.isNotEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { CostBreakdownCard(costs) }

            item(span = { GridItemSpan(maxLineSpan) }) { SectionTitle(stringResource(R.string.sections_title)) }

            item { Tile(Icons.Outlined.Timeline, stringResource(R.string.mod_itinerary),
                pluralText(R.plurals.tile_events, b.events.size), a.openItinerary) }
            // Transporte: solo los apartados de los medios que se eligieron al crear el viaje.
            val transportTypes = b.transports.map { it.type }
            TransportSections.shown(TravelModes.parse(trip.travelModes), transportTypes).forEach { s ->
                val spec = ModuleSpecs.of(s.module, s)
                item {
                    Tile(spec.icon, stringResource(spec.titleRes),
                        countText(TransportSections.count(s, b.flights.size, transportTypes)), { a.openSection(s) })
                }
            }
            listOf(ModuleType.LODGING, ModuleType.ACTIVITY, ModuleType.RESTAURANT).forEach { t ->
                val spec = ModuleSpecs.of(t)
                item { Tile(spec.icon, stringResource(spec.titleRes), countText(b.count(t)), { a.openList(t) }) }
            }
            item { Tile(Icons.Outlined.Badge, stringResource(R.string.mod_docs), countText(b.docs.size), { a.openList(ModuleType.TRAVEL_DOC) }) }
            item { Tile(Icons.Outlined.Folder, stringResource(R.string.mod_files), countText(b.attachments.size), a.openFiles) }
        }
    }
}

@Composable
private fun countText(n: Int): String = if (n == 0) stringResource(R.string.tile_empty) else pluralText(R.plurals.tile_count, n)

@Composable
private fun Tile(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Card(onClick = onClick, shape = MaterialTheme.shapes.medium, elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Column(Modifier.fillMaxWidth().padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(28.dp))
            // minLines = 2: todas las tarjetas reservan dos líneas de título, tengan una o dos
            // («Visados y documentos de viaje»), para que la cuadrícula quede con alturas iguales.
            Text(title, style = MaterialTheme.typography.titleSmall, minLines = 2, maxLines = 2)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
    }
}

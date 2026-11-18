package com.travelindex.app.ui.modules

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.travelindex.app.R
import com.travelindex.app.data.db.AttachmentEntity
import com.travelindex.app.domain.ModuleType
import com.travelindex.app.domain.ViewerSupport
import com.travelindex.app.domain.forms.FieldDef
import com.travelindex.app.domain.forms.FieldType
import com.travelindex.app.ui.components.AttachmentRow
import com.travelindex.app.ui.components.ConfirmDialog
import com.travelindex.app.ui.components.FormField
import com.travelindex.app.ui.components.LoadingBox
import com.travelindex.app.ui.components.PendingFileRow
import com.travelindex.app.ui.components.SectionCard
import com.travelindex.app.ui.components.rememberAddFileFlow
import java.time.LocalDate
import com.travelindex.app.ui.theme.CenteredContent
import com.travelindex.app.ui.theme.LocalFormats
import com.travelindex.app.ui.theme.Spacing

/** Formulario único para todos los módulos: los campos salen de la especificación del módulo. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModuleFormScreen(onClose: () -> Unit, onOpenViewer: (Long) -> Unit, vm: ModuleFormViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val spec = vm.spec
    val ctx = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var confirmDiscard by remember { mutableStateOf(false) }
    var confirmAttachment by remember { mutableStateOf<AttachmentEntity?>(null) }

    val fixMsg = stringResource(R.string.error_fix_fields)
    val saveMsg = stringResource(R.string.error_save)
    val fileMsg = stringResource(R.string.error_attach)
    val scroll = rememberScrollState()
    val legSavedMsg = { leg: Int -> ctx.getString(R.string.flight_leg_saved, leg) }

    val addFile = rememberAddFileFlow { uris -> uris.forEach { vm.addFile(it) } }

    LaunchedEffect(Unit) {
        vm.events.collect { e ->
            when (e) {
                is FormEvent.Saved -> {
                    if (e.failedFiles > 0) Toast.makeText(ctx, fileMsg, Toast.LENGTH_LONG).show()
                    onClose()
                }
                is FormEvent.NextLeg -> {
                    // Vuelo con escalas: el formulario ya está listo para el siguiente tramo.
                    scroll.scrollTo(0)
                    snackbar.showSnackbar(legSavedMsg(e.leg))
                }
                FormEvent.ValidationFailed -> snackbar.showSnackbar(fixMsg)
                FormEvent.SaveFailed -> snackbar.showSnackbar(saveMsg)
                FormEvent.FileFailed -> snackbar.showSnackbar(fileMsg)
            }
        }
    }
    // Un elemento que ya no existe (borrado desde otro sitio) cierra el formulario.
    LaunchedEffect(state.loaded, state.notFound) { if (state.loaded && state.notFound) onClose() }

    val tryClose = { if (state.dirty) confirmDiscard = true else onClose() }
    BackHandler(enabled = state.dirty) { confirmDiscard = true }

    Scaffold(
        modifier = Modifier.imePadding(),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (state.leg >= 2) stringResource(R.string.flight_new_leg_title, state.leg)
                        else stringResource(if (vm.itemId == 0L) spec.newTitleRes else spec.editTitleRes)
                    )
                },
                navigationIcon = { IconButton(onClick = tryClose) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                // navigationBarsPadding: el botón queda por encima de los botones del sistema (atrás, inicio...).
                Button(
                    onClick = vm::save, enabled = state.loaded && !state.saving,
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = Spacing.m, vertical = Spacing.s),
                ) {
                    val addsLeg = spec.type == ModuleType.FLIGHT && state.canAddLeg && state.values["nextLeg"] == "1"
                    Text(stringResource(if (addsLeg) R.string.action_save_add_leg else R.string.action_save))
                }
            }
        },
    ) { pad ->
        if (!state.loaded) { LoadingBox(Modifier.padding(pad)); return@Scaffold }
        CenteredContent(Modifier.padding(pad)) {
            Column(
                Modifier.fillMaxSize().verticalScroll(scroll).padding(Spacing.m),
                verticalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                if (state.leg >= 2) {
                    Text(
                        stringResource(R.string.flight_leg_hint),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                spec.sections.forEach { section ->
                    val defs = section.formKeys.mapNotNull { spec.field(it) }.filter { spec.visibleWhen(it, state.values) }
                    if (defs.isEmpty()) return@forEach
                    SectionCard(stringResource(section.formTitleRes ?: section.titleRes)) {
                        FieldGroups(defs) { def ->
                            FormField(
                                def = def, value = state.values[def.key].orEmpty(), error = state.errors[def.key],
                                onChange = { vm.setValue(def.key, it) },
                                minDate = def.notBeforeKey?.let { k ->
                                    state.values[k]?.takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                                },
                            )
                        }
                        // Actividad: en cuanto hay hora de inicio y duración, se ve a qué hora terminará.
                        if (spec.type == ModuleType.ACTIVITY && defs.any { it.key == "duration" }) {
                            val endText = activityEndClock(LocalFormats.current, state.values)
                            Text(
                                if (endText != null) stringResource(R.string.activity_ends_at, endText) else stringResource(R.string.duration_hint),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = Spacing.xs),
                            )
                        }
                    }
                }

                // Vuelo con escalas: al guardar se abre el formulario del siguiente tramo, tantas veces como haga falta.
                if (spec.type == ModuleType.FLIGHT && state.canAddLeg) {
                    val on = state.values["nextLeg"] == "1"
                    SectionCard(stringResource(R.string.sec_flight_stops)) {
                        Row(
                            Modifier.fillMaxWidth().toggleable(
                                value = on, role = Role.Switch,
                                onValueChange = { vm.setValue("nextLeg", if (it) "1" else "") },
                            ),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m),
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.flight_has_stops), style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    stringResource(R.string.flight_has_stops_hint), style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(checked = on, onCheckedChange = null)
                        }
                    }
                }

                if (spec.type.attachable) {
                    SectionCard(stringResource(R.string.sec_attachments)) {
                        state.existing.forEach { att ->
                            AttachmentRow(att, onDelete = { confirmAttachment = att },
                                onOpen = if (ViewerSupport.opensInternally(att.mimeType)) ({ onOpenViewer(att.id) }) else null)
                        }
                        state.pending.forEachIndexed { i, p -> PendingFileRow(onRemove = { vm.removePending(i) }, uri = p.uri) }
                        if (state.existing.isEmpty() && state.pending.isEmpty()) {
                            Text(stringResource(R.string.attach_empty), style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        OutlinedButton(onClick = addFile, Modifier.fillMaxWidth()) {
                            Icon(Icons.Outlined.AttachFile, null)
                            Text("  " + stringResource(R.string.action_attach))
                        }
                    }
                }
            }
        }
    }

    if (confirmDiscard) ConfirmDialog(
        title = stringResource(R.string.discard_title), body = stringResource(R.string.discard_body),
        confirmLabel = stringResource(R.string.action_discard),
        onConfirm = { confirmDiscard = false; onClose() }, onDismiss = { confirmDiscard = false },
    )
    confirmAttachment?.let { att ->
        ConfirmDialog(
            title = stringResource(R.string.delete_file_title), body = stringResource(R.string.delete_file_body, att.displayName),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = { vm.removeExisting(att); confirmAttachment = null }, onDismiss = { confirmAttachment = null },
        )
    }
}

/** Coloca fecha+hora e importe+moneda en la misma fila; el resto, uno por fila. */
@Composable
private fun FieldGroups(defs: List<FieldDef>, field: @Composable (FieldDef) -> Unit) {
    val rows = ArrayList<List<FieldDef>>()
    var i = 0
    while (i < defs.size) {
        val a = defs[i]
        val b = defs.getOrNull(i + 1)
        val paired = b != null && (
            (a.type == FieldType.DATE && b.type == FieldType.TIME) ||
                (a.type == FieldType.MONEY && b.type == FieldType.CURRENCY)
            )
        if (paired && b != null) { rows.add(listOf(a, b)); i += 2 } else { rows.add(listOf(a)); i += 1 }
    }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        rows.forEach { row ->
            if (row.size == 2) {
                val firstWeight = if (row[0].type == FieldType.MONEY) 2f else 1.3f
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    Column(Modifier.weight(firstWeight)) { field(row[0]) }
                    Column(Modifier.weight(1f)) { field(row[1]) }
                }
            } else {
                field(row[0])
            }
        }
    }
}

package com.travelindex.app.ui.modules

import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import java.time.LocalDate
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.travelindex.app.R
import com.travelindex.app.data.FileActions
import com.travelindex.app.data.db.AttachmentEntity
import com.travelindex.app.domain.Airports
import com.travelindex.app.domain.ModuleType
import com.travelindex.app.domain.forms.ActivityCodec
import com.travelindex.app.domain.forms.FieldType
import com.travelindex.app.domain.forms.FlightCodec
import com.travelindex.app.ui.components.AttachmentRow
import com.travelindex.app.ui.components.ConfirmDialog
import com.travelindex.app.ui.components.InfoRow
import com.travelindex.app.ui.components.LoadingBox
import com.travelindex.app.ui.components.Pill
import com.travelindex.app.ui.components.SectionCard
import com.travelindex.app.ui.components.rememberAddFileFlow
import com.travelindex.app.ui.theme.CenteredContent
import com.travelindex.app.ui.theme.LocalFormats
import com.travelindex.app.ui.theme.Spacing
import com.travelindex.app.ui.theme.StatusOk

/** Detalle completo de un elemento: todos sus datos, acciones rápidas y archivos adjuntos. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModuleDetailScreen(onBack: () -> Unit, onEdit: (Long) -> Unit, vm: ModuleDetailViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val spec = vm.spec
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmAttachment by remember { mutableStateOf<AttachmentEntity?>(null) }
    var reveal by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val attachError = stringResource(R.string.error_attach)
    val noApp = stringResource(R.string.error_no_app)
    fun toast(msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()

    val addFile = rememberAddFileFlow { uris -> uris.forEach { vm.addFile(it) } }

    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(attachError) } }
    // Si el elemento desaparece (borrado), se vuelve a la lista.
    LaunchedEffect(state.loaded, state.item) { if (state.loaded && state.item == null) onBack() }

    val item = state.item
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(if (item != null) moduleTitle(spec, item.values) else "") },
                navigationIcon = { IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } },
                actions = {
                    IconButton(onClick = { onEdit(vm.itemId) }) { Icon(Icons.Filled.Edit, stringResource(R.string.action_edit)) }
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, stringResource(R.string.action_delete)) }
                },
            )
        },
    ) { pad ->
        if (item == null) { LoadingBox(Modifier.padding(pad)); return@Scaffold }
        val v = item.values
        val phone = spec.phoneKey?.let { v[it].orEmpty().trim() }.orEmpty()
        val url = spec.urlKey?.let { v[it].orEmpty().trim() }.orEmpty()
        val duration: Long? = when (spec.type) {
            ModuleType.FLIGHT -> runCatching { FlightCodec.build(0, 0, v) }.getOrNull()?.let { FlightCodec.durationMinutes(it) }
            ModuleType.ACTIVITY -> runCatching { ActivityCodec.build(0, 0, v) }.getOrNull()?.let { ActivityCodec.durationMinutes(it) }
            else -> null
        }?.takeIf { it > 0 }
        // Actividad con hora de inicio y duración: la app calcula a qué hora termina.
        val activityEnd = if (spec.type == ModuleType.ACTIVITY) activityEndClock(LocalFormats.current, v) else null

        CenteredContent(Modifier.padding(pad)) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.m),
                verticalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                if (spec.type == ModuleType.FLIGHT) {
                    // "De X a Y" con la flecha de vuelo; la duración sale sola de las horas y las zonas horarias.
                    FlightRouteCard(
                        from = Airports.displayName(v["departureAirport"].orEmpty()), to = Airports.displayName(v["arrivalAirport"].orEmpty()),
                        depClock = LocalFormats.current.let { f -> listOf(v["depDate"]?.let { d -> runCatching { f.date(LocalDate.parse(d)) }.getOrNull() }, v["depTime"]).filter { !it.isNullOrBlank() }.joinToString(" ") },
                        arrClock = LocalFormats.current.let { f -> listOf(v["arrDate"]?.let { d -> runCatching { f.date(LocalDate.parse(d)) }.getOrNull() }, v["arrTime"]).filter { !it.isNullOrBlank() }.joinToString(" ") },
                        duration = duration?.let { durationText(it) },
                    )
                }
                if ((duration != null && spec.type != ModuleType.FLIGHT) || activityEnd != null || phone.isNotEmpty() || url.isNotEmpty()) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        if (duration != null && spec.type != ModuleType.FLIGHT) Pill(stringResource(R.string.duration_label, durationText(duration)), StatusOk)
                        if (activityEnd != null) Pill(stringResource(R.string.activity_ends_at, activityEnd), StatusOk)
                        if (phone.isNotEmpty()) AssistChip(
                            onClick = { if (!FileActions.dial(ctx, phone)) toast(noApp) },
                            label = { Text(stringResource(R.string.action_call)) },
                            leadingIcon = { Icon(Icons.Outlined.Phone, null) },
                        )
                        if (url.isNotEmpty()) AssistChip(
                            onClick = { if (!FileActions.openUrl(ctx, url)) toast(noApp) },
                            label = { Text(stringResource(R.string.action_open_link)) },
                            leadingIcon = { Icon(Icons.Outlined.Language, null) },
                        )
                    }
                }

                spec.sections.forEach { section ->
                    val rows = section.keys.mapNotNull { key -> spec.field(key) }
                        .filter { it.type != FieldType.CURRENCY }
                        .map { def -> def to displayValue(def, v, reveal) }
                        .filter { (_, text) -> text.isNotEmpty() }
                    if (rows.isNotEmpty()) {
                        SectionCard(stringResource(section.titleRes)) {
                            rows.forEach { (def, text) ->
                                InfoRow(stringResource(def.labelRes), text)
                            }
                            if (rows.any { it.first.sensitive }) {
                                TextButton(onClick = { reveal = !reveal }) {
                                    Text(stringResource(if (reveal) R.string.action_hide_sensitive else R.string.action_show_sensitive))
                                }
                            }
                        }
                    }
                }

                if (spec.type.attachable) {
                    SectionCard(stringResource(R.string.sec_attachments)) {
                        if (state.attachments.isEmpty()) {
                            Text(stringResource(R.string.attach_empty), style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        state.attachments.forEach { att -> AttachmentRow(att, onDelete = { confirmAttachment = att }) }
                        OutlinedButton(onClick = addFile, Modifier.fillMaxWidth()) {
                            Icon(Icons.Outlined.AttachFile, null)
                            Text("  " + stringResource(R.string.action_attach))
                        }
                    }
                }
            }
        }
    }

    if (confirmDelete) ConfirmDialog(
        title = stringResource(R.string.delete_item_title), body = stringResource(R.string.delete_item_body),
        confirmLabel = stringResource(R.string.action_delete),
        onConfirm = { confirmDelete = false; vm.delete() }, onDismiss = { confirmDelete = false },
    )
    confirmAttachment?.let { att ->
        ConfirmDialog(
            title = stringResource(R.string.delete_file_title), body = stringResource(R.string.delete_file_body, att.displayName),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = { vm.removeAttachment(att); confirmAttachment = null }, onDismiss = { confirmAttachment = null },
        )
    }
}

package com.travelindex.app.ui.notes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.travelindex.app.R
import com.travelindex.app.data.db.NoteItemEntity
import com.travelindex.app.domain.NoteKind
import com.travelindex.app.domain.NoteValidator
import com.travelindex.app.domain.forms.FieldError
import com.travelindex.app.ui.components.ConfirmDialog
import com.travelindex.app.ui.components.DatePickerDialogHost
import com.travelindex.app.ui.components.EmptyState
import com.travelindex.app.ui.components.LoadingBox
import com.travelindex.app.ui.components.PickerField
import com.travelindex.app.ui.components.Pill
import com.travelindex.app.ui.components.TimePickerDialogHost
import com.travelindex.app.ui.components.errorText
import com.travelindex.app.ui.theme.CenteredContent
import com.travelindex.app.ui.theme.LocalFormats
import com.travelindex.app.ui.theme.Spacing
import com.travelindex.app.ui.theme.StatusDanger
import com.travelindex.app.ui.theme.StatusOk
import java.time.LocalDate
import java.time.LocalTime

@Composable
fun noteKindLabel(k: NoteKind): String = stringResource(
    when (k) {
        NoteKind.NOTE -> R.string.nk_note
        NoteKind.PACKING -> R.string.nk_packing
        NoteKind.TODO -> R.string.nk_todo
        NoteKind.REMINDER -> R.string.nk_reminder
        NoteKind.SHOPPING -> R.string.nk_shopping
        NoteKind.INFO -> R.string.nk_info
    }
)

/** Notas y listas del viaje: notas, equipaje, tareas, recordatorios, compras e información útil. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(onBack: () -> Unit, vm: NotesViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf("") }
    // null = diálogo cerrado · 0 = nueva nota · otro = editar esa nota
    var editId by rememberSaveable { mutableStateOf(if (vm.startAdding) 0L else null) }
    var deleteId by rememberSaveable { mutableStateOf<Long?>(null) }
    val bundle = state.bundle

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.mod_notes)) },
                navigationIcon = { IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { editId = 0L }, icon = { Icon(Icons.Filled.Add, null) },
                text = { Text(stringResource(R.string.action_add)) },
            )
        },
    ) { pad ->
        if (bundle == null) { LoadingBox(Modifier.padding(pad)); return@Scaffold }
        val all = bundle.notes
        val kindsPresent = NoteKind.entries.filter { k -> all.any { it.kind == k.name } }
        val selectedKind = NoteKind.entries.firstOrNull { it.name == filter }
        val shown = if (selectedKind == null) all else all.filter { it.kind == selectedKind.name }

        CenteredContent(Modifier.padding(pad)) {
            Column(Modifier.fillMaxSize()) {
                if (all.isEmpty()) {
                    EmptyState(
                        stringResource(R.string.notes_empty_title), stringResource(R.string.notes_empty_body),
                        icon = Icons.Outlined.EditNote,
                        actionLabel = stringResource(R.string.action_add), onAction = { editId = 0L },
                    )
                } else {
                    if (kindsPresent.size > 1) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = Spacing.m, vertical = Spacing.s),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                        ) {
                            item { FilterChip(selected = filter.isEmpty(), onClick = { filter = "" },
                                label = { Text(stringResource(R.string.filter_all)) }) }
                            items(kindsPresent, key = { it.name }) { k ->
                                FilterChip(selected = filter == k.name, onClick = { filter = k.name },
                                    label = { Text(noteKindLabel(k) + " · " + all.count { it.kind == k.name }) })
                            }
                        }
                    }
                    LazyColumn(
                        contentPadding = PaddingValues(start = Spacing.m, end = Spacing.m, top = Spacing.s, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(Spacing.s),
                    ) {
                        NoteKind.entries.forEach { kind ->
                            val group = shown.filter { it.kind == kind.name }
                            if (group.isNotEmpty()) {
                                item(key = "h-" + kind.name) {
                                    val done = group.count { it.done }
                                    Text(
                                        noteKindLabel(kind) + if (kind.checkable) "  ·  " + stringResource(R.string.notes_progress, done, group.size) else "",
                                        Modifier.padding(top = Spacing.s), style = MaterialTheme.typography.titleMedium,
                                    )
                                }
                                items(group, key = { it.id }) { n ->
                                    NoteRow(n, onToggle = { vm.toggle(n) }, onClick = { editId = n.id })
                                }
                            }
                        }
                    }
                }
            }
        }

        editId?.let { id ->
            val existing = if (id == 0L) null else all.firstOrNull { it.id == id }
            if (id == 0L || existing != null) {
                NoteDialog(
                    initial = existing,
                    defaultKind = selectedKind ?: NoteKind.NOTE,
                    onDismiss = { editId = null },
                    onSave = { kind, text, body, due, time -> vm.save(id, kind, text, body, due, time); editId = null },
                    onDelete = if (existing != null) ({ deleteId = existing.id }) else null,
                )
            }
        }
        deleteId?.let { id ->
            ConfirmDialog(
                title = stringResource(R.string.delete_note_title), body = stringResource(R.string.delete_note_body),
                confirmLabel = stringResource(R.string.action_delete),
                onConfirm = { vm.delete(id); deleteId = null; editId = null }, onDismiss = { deleteId = null },
            )
        }
    }
}

@Composable
private fun NoteRow(n: NoteItemEntity, onToggle: () -> Unit, onClick: () -> Unit) {
    val kind = NoteKind.fromName(n.kind)
    val f = LocalFormats.current
    val overdue = n.dueDate != null && !n.done && n.dueDate.isBefore(LocalDate.now())
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Row(Modifier.padding(Spacing.s), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            if (kind.checkable) Checkbox(checked = n.done, onCheckedChange = { onToggle() })
            else Icon(Icons.Outlined.EditNote, null, Modifier.padding(start = Spacing.s), tint = MaterialTheme.colorScheme.secondary)
            Column(Modifier.weight(1f).padding(vertical = Spacing.xs)) {
                Text(
                    n.text, style = MaterialTheme.typography.titleSmall,
                    textDecoration = if (n.done) TextDecoration.LineThrough else null,
                    color = if (n.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                if (n.body.isNotBlank()) Text(n.body, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
                if (n.dueDate != null) {
                    val txt = f.date(n.dueDate) + (n.dueTime?.let { " · " + f.time(it) } ?: "")
                    Pill(txt, if (overdue) StatusDanger else StatusOk, Modifier.padding(top = Spacing.xs))
                }
            }
        }
    }
}

@Composable
private fun NoteDialog(
    initial: NoteItemEntity?,
    defaultKind: NoteKind,
    onDismiss: () -> Unit,
    onSave: (NoteKind, String, String, LocalDate?, LocalTime?) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var kindName by rememberSaveable { mutableStateOf(initial?.kind ?: defaultKind.name) }
    var text by rememberSaveable { mutableStateOf(initial?.text.orEmpty()) }
    var body by rememberSaveable { mutableStateOf(initial?.body.orEmpty()) }
    var dueIso by rememberSaveable { mutableStateOf(initial?.dueDate?.toString().orEmpty()) }
    var timeIso by rememberSaveable { mutableStateOf(initial?.dueTime?.toString().orEmpty()) }
    var errors by remember { mutableStateOf<Map<String, FieldError>>(emptyMap()) }
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }

    val kind = NoteKind.fromName(kindName)
    val f = LocalFormats.current
    val due = dueIso.takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    val time = timeIso.takeIf { it.isNotBlank() }?.let { runCatching { LocalTime.parse(it) }.getOrNull() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initial == null) R.string.note_new else R.string.note_edit)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    items(NoteKind.entries, key = { it.name }) { k ->
                        FilterChip(selected = k == kind, onClick = { kindName = k.name; errors = errors - "dueDate" },
                            label = { Text(noteKindLabel(k)) })
                    }
                }
                OutlinedTextField(
                    value = text, onValueChange = { if (it.length <= NoteValidator.MAX_TEXT) { text = it; errors = errors - "text" } },
                    label = { Text(stringResource(if (kind.usesBody) R.string.note_title else R.string.note_item) + " *") },
                    isError = errors["text"] != null, supportingText = errors["text"]?.let { { Text(errorText(it)) } },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = body, onValueChange = { if (it.length <= NoteValidator.MAX_BODY) body = it },
                    label = { Text(stringResource(if (kind.usesBody) R.string.note_content else R.string.note_details)) },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    minLines = if (kind.usesBody) 4 else 2, modifier = Modifier.fillMaxWidth(),
                )
                if (kind.usesDue) {
                    PickerField(
                        label = stringResource(R.string.note_due_date) + if (kind == NoteKind.REMINDER) " *" else "",
                        value = due?.let { f.date(it) }.orEmpty(), onClick = { pickDate = true },
                        error = errors["dueDate"]?.let { errorText(it) },
                        onClear = if (due != null) ({ dueIso = ""; timeIso = "" }) else null,
                    )
                    if (due != null) PickerField(
                        label = stringResource(R.string.note_due_time), value = time?.let { f.time(it) }.orEmpty(),
                        onClick = { pickTime = true }, onClear = if (time != null) ({ timeIso = "" }) else null,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val e = NoteValidator.validate(kind, text, if (kind.usesDue) due else null)
                if (e.isEmpty()) onSave(kind, text, body, due, time) else errors = e
            }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = onDelete) {
                    Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        },
    )
    if (pickDate) DatePickerDialogHost(due, onDismiss = { pickDate = false },
        onPick = { dueIso = it.toString(); pickDate = false; errors = errors - "dueDate" })
    if (pickTime) TimePickerDialogHost(time, onDismiss = { pickTime = false }, onPick = { timeIso = it.toString(); pickTime = false })
}

package com.travelindex.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.travelindex.app.R
import com.travelindex.app.domain.forms.Durations
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

/** Campo que parece un campo de texto pero abre un selector al pulsarlo. */
@Composable
fun PickerField(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    error: String? = null,
    onClear: (() -> Unit)? = null,
) {
    val errorColor = MaterialTheme.colorScheme.error
    Column(modifier.fillMaxWidth()) {
        OutlinedCard(
            onClick = onClick, modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(1.dp, if (error != null) errorColor else MaterialTheme.colorScheme.outline),
        ) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(label, style = MaterialTheme.typography.bodySmall,
                        color = if (error != null) errorColor else MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(value.ifBlank { "—" }, style = MaterialTheme.typography.bodyLarge)
                }
                if (onClear != null) {
                    IconButton(onClick = onClear) {
                        Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.a11y_clear))
                    }
                }
            }
        }
        if (error != null) {
            Text(error, color = errorColor, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 16.dp, top = 4.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerDialogHost(initial: LocalDate?, onDismiss: () -> Unit, onPick: (LocalDate) -> Unit, minDate: LocalDate? = null) {
    val minMillis = minDate?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()
    // Una fecha anterior al mínimo no puede quedar marcada de entrada (el calendario no la admitiría): se parte sin selección.
    val start = initial?.takeIf { minDate == null || !it.isBefore(minDate) }
    val state = rememberDatePickerState(
        initialSelectedDateMillis = start?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
        // Sin fecha elegida, el calendario se abre en el mes de la fecha mínima (p. ej. el mes de la ida).
        initialDisplayedMonthMillis = (start ?: minDate)?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean = minMillis == null || utcTimeMillis >= minMillis
            override fun isSelectableYear(year: Int): Boolean = minDate == null || year >= minDate.year
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val ms = state.selectedDateMillis
                if (ms == null) onDismiss() else onPick(Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate())
            }) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    ) { DatePicker(state = state) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialogHost(initial: LocalTime?, onDismiss: () -> Unit, onPick: (LocalTime) -> Unit) {
    val base = initial ?: LocalTime.of(9, 0)
    val state = rememberTimePickerState(initialHour = base.hour, initialMinute = base.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onPick(LocalTime.of(state.hour, state.minute)) }) { Text(stringResource(R.string.action_ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
        text = { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { TimePicker(state = state) } },
    )
}

/** Duraciones que se piden más a menudo: un toque y listo. */
private val QUICK_DURATIONS = listOf(30, 60, 90, 120, 180, 240, 360, 480)

/**
 * Duración en horas y minutos, con atajos para las más habituales. Al aceptar devuelve los minutos totales,
 * o null si se deja en cero (sin duración).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DurationPickerDialog(initialMinutes: Int?, onDismiss: () -> Unit, onPick: (Int?) -> Unit) {
    var hours by rememberSaveable { mutableStateOf(initialMinutes?.let { (it / 60).toString() }.orEmpty()) }
    var minutes by rememberSaveable { mutableStateOf(initialMinutes?.let { (it % 60).toString() }.orEmpty()) }
    val total = Durations.of(hours.toIntOrNull() ?: 0, minutes.toIntOrNull() ?: 0)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.duration_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = hours, onValueChange = { hours = it.filter(Char::isDigit).take(3) }, singleLine = true,
                        label = { Text(stringResource(R.string.duration_hours)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = minutes, onValueChange = { minutes = it.filter(Char::isDigit).take(2) }, singleLine = true,
                        label = { Text(stringResource(R.string.duration_minutes)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f),
                    )
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    QUICK_DURATIONS.forEach { q ->
                        AssistChip(
                            onClick = { hours = (q / 60).toString(); minutes = (q % 60).toString() },
                            label = { Text(Durations.text(q.toLong())) },
                        )
                    }
                }
                Text(
                    if (total > 0) Durations.text(total.toLong()) else stringResource(R.string.duration_hint),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onPick(total.takeIf { it > 0 }) }) { Text(stringResource(R.string.action_ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

data class Option(val key: String, val label: String)

/** Lista de opciones en un diálogo, con buscador opcional (zonas horarias, monedas). */
@Composable
fun ChoiceDialog(
    title: String,
    options: List<Option>,
    selected: String?,
    searchable: Boolean,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = remember(options, query) {
        if (query.isBlank()) options
        else options.filter { it.label.contains(query.trim(), ignoreCase = true) || it.key.contains(query.trim(), ignoreCase = true) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                if (searchable) {
                    OutlinedTextField(
                        value = query, onValueChange = { query = it }, singleLine = true,
                        label = { Text(stringResource(R.string.action_search)) }, modifier = Modifier.fillMaxWidth(),
                    )
                }
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(filtered, key = { it.key }) { o ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onPick(o.key) }.padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Start,
                        ) {
                            RadioButton(selected = o.key == selected, onClick = null)
                            Spacer(Modifier.width(12.dp))
                            Text(o.label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

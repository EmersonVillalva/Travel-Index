package com.travelindex.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.travelindex.app.R
import com.travelindex.app.domain.Airports
import com.travelindex.app.domain.Money
import com.travelindex.app.domain.forms.FieldDef
import com.travelindex.app.domain.forms.FieldError
import com.travelindex.app.domain.forms.Durations
import com.travelindex.app.domain.forms.FieldType
import com.travelindex.app.ui.theme.LocalFormats
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Currency

@Composable
fun errorText(e: FieldError): String = stringResource(
    when (e) {
        FieldError.REQUIRED -> R.string.error_required
        FieldError.INVALID_DATE -> R.string.error_date
        FieldError.INVALID_TIME -> R.string.error_time
        FieldError.INVALID_NUMBER -> R.string.error_number
        FieldError.INVALID_MONEY -> R.string.error_budget
        FieldError.INVALID_CURRENCY -> R.string.error_currency
        FieldError.INVALID_ZONE -> R.string.error_zone
        FieldError.END_BEFORE_START -> R.string.error_end_before_start
    }
)

/** Nombre legible de una zona horaria: "Europe/Rome" → "Europe/Rome" (sin guiones bajos). */
fun zoneLabel(id: String): String = id.replace('_', ' ')

private val zoneOptions: List<Option> by lazy {
    ZoneId.getAvailableZoneIds()
        .filter { it.contains('/') && !it.startsWith("Etc/") && !it.startsWith("SystemV/") }
        .sorted().map { Option(it, zoneLabel(it)) }
}

private val currencyOptions: List<Option> by lazy {
    val preferred = listOf("EUR", "USD", "GBP", "CHF", "JPY", "MXN", "ARS", "COP", "CLP", "PEN", "BRL", "CAD", "AUD")
    val all = Currency.getAvailableCurrencies().map { Option(it.currencyCode, it.currencyCode + " · " + it.displayName) }
        .sortedBy { it.key }
    preferred.mapNotNull { p -> all.firstOrNull { it.key == p } } + all.filter { it.key !in preferred }
}

fun currencyOptionsList(): List<Option> = currencyOptions

/**
 * Pinta un campo según su tipo. [label] ya viene resuelto; las opciones de tipo CHOICE llevan sus etiquetas.
 */
@Composable
fun FormField(
    def: FieldDef,
    value: String,
    error: FieldError?,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    /** Solo para fechas: el selector no deja elegir un día anterior a este. */
    minDate: LocalDate? = null,
) {
    val label = stringResource(def.labelRes) + if (def.required) " *" else ""
    val errMsg = error?.let { errorText(it) }
    val formats = LocalFormats.current

    when (def.type) {
        FieldType.TEXT, FieldType.MULTILINE, FieldType.PHONE, FieldType.URL, FieldType.INT, FieldType.MONEY -> {
            val kb = when (def.type) {
                FieldType.PHONE -> KeyboardType.Phone
                FieldType.URL -> KeyboardType.Uri
                FieldType.INT -> KeyboardType.Number
                FieldType.MONEY -> KeyboardType.Decimal
                else -> KeyboardType.Text
            }
            OutlinedTextField(
                value = value,
                onValueChange = { v -> onChange(if (def.type == FieldType.INT) v.filter(Char::isDigit).take(4) else v) },
                label = { Text(label) },
                isError = errMsg != null,
                supportingText = errMsg?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(
                    keyboardType = kb,
                    capitalization = if (kb == KeyboardType.Text) KeyboardCapitalization.Sentences else KeyboardCapitalization.None,
                ),
                singleLine = def.type != FieldType.MULTILINE,
                minLines = if (def.type == FieldType.MULTILINE) 3 else 1,
                modifier = modifier.fillMaxWidth(),
            )
        }

        FieldType.DATE -> {
            var open by remember { mutableStateOf(false) }
            val parsed = value.takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            PickerField(
                label = label, value = parsed?.let { formats.date(it) } ?: value, error = errMsg, modifier = modifier,
                onClick = { open = true },
                onClear = if (!def.required && value.isNotBlank()) ({ onChange("") }) else null,
            )
            if (open) DatePickerDialogHost(parsed, onDismiss = { open = false }, onPick = { onChange(it.toString()); open = false }, minDate = minDate)
        }

        FieldType.TIME -> {
            var open by remember { mutableStateOf(false) }
            val parsed = value.takeIf { it.isNotBlank() }?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
            PickerField(
                label = label, value = parsed?.let { formats.time(it) } ?: value, error = errMsg, modifier = modifier,
                onClick = { open = true },
                onClear = if (!def.required && value.isNotBlank()) ({ onChange("") }) else null,
            )
            if (open) TimePickerDialogHost(parsed, onDismiss = { open = false }, onPick = { onChange(it.toString()); open = false })
        }

        FieldType.DURATION -> {
            var open by remember { mutableStateOf(false) }
            val minutes = Durations.parse(value)
            PickerField(
                label = label, value = minutes?.let { Durations.text(it.toLong()) } ?: value, error = errMsg, modifier = modifier,
                onClick = { open = true },
                onClear = if (!def.required && value.isNotBlank()) ({ onChange("") }) else null,
            )
            if (open) DurationPickerDialog(minutes, onDismiss = { open = false }, onPick = { onChange(it?.toString().orEmpty()); open = false })
        }

        FieldType.ZONE -> {
            var open by remember { mutableStateOf(false) }
            PickerField(label = label, value = zoneLabel(value), error = errMsg, modifier = modifier, onClick = { open = true })
            if (open) ChoiceDialog(
                title = label, options = zoneOptions, selected = value, searchable = true,
                onDismiss = { open = false }, onPick = { onChange(it); open = false },
            )
        }

        FieldType.CURRENCY -> {
            var open by remember { mutableStateOf(false) }
            PickerField(label = label, value = value, error = errMsg, modifier = modifier, onClick = { open = true })
            if (open) ChoiceDialog(
                title = label, options = currencyOptions, selected = value, searchable = true,
                onDismiss = { open = false }, onPick = { onChange(it); open = false },
            )
        }

        FieldType.AIRPORT -> AirportField(label, value, errMsg, onChange, modifier)

        FieldType.CHOICE -> {
            var open by remember { mutableStateOf(false) }
            val options = def.choices.map { Option(it.key, stringResource(it.labelRes)) }
            PickerField(
                label = label, value = options.firstOrNull { it.key == value }?.label.orEmpty(), error = errMsg, modifier = modifier,
                onClick = { open = true },
                onClear = if (!def.required && value.isNotBlank()) ({ onChange("") }) else null,
            )
            if (open) ChoiceDialog(
                title = label, options = options, selected = value, searchable = false,
                onDismiss = { open = false }, onPick = { onChange(it); open = false },
            )
        }
    }
}

/**
 * Aeropuerto por NOMBRE (no por código): al escribir salen sugerencias de la lista; también vale cualquier texto libre.
 * Si el usuario escribe un código ("MAD"), se ofrece el nombre ("Madrid") para tocarlo.
 */
@Composable
private fun AirportField(label: String, value: String, error: String?, onChange: (String) -> Unit, modifier: Modifier) {
    val suggestions = remember(value) {
        val isNameAlready = Airports.exact(value)?.name.equals(value.trim(), ignoreCase = true)
        if (isNameAlready) emptyList() else Airports.search(value, 5)
    }
    Column(modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value, onValueChange = { onChange(it.take(80)) }, label = { Text(label) },
            isError = error != null, supportingText = error?.let { { Text(it) } }, singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            modifier = Modifier.fillMaxWidth(),
        )
        if (suggestions.isNotEmpty()) {
            Surface(shape = MaterialTheme.shapes.small, tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
                Column {
                    suggestions.forEach { a ->
                        Text(
                            a.name, style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.fillMaxWidth().clickable { onChange(a.name) }.padding(horizontal = 12.dp, vertical = 10.dp),
                        )
                    }
                }
            }
        }
    }
}

/** Importe formateado ("1.234,50 €") a partir de los valores de formulario, o null si no hay importe. */
fun formatMoneyValue(amountText: String?, currency: String?): String? {
    val c = currency.orEmpty().ifBlank { return null }
    val minor = amountText?.takeIf { it.isNotBlank() }?.let { Money.parseToMinor(it, c) } ?: return null
    return Money.format(minor, c)
}

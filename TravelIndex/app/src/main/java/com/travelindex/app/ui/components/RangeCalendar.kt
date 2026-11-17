package com.travelindex.app.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.travelindex.app.R
import com.travelindex.app.domain.MonthGrid
import com.travelindex.app.domain.RangeEdge
import com.travelindex.app.domain.RangeSelection
import com.travelindex.app.ui.theme.LocalFormats
import com.travelindex.app.ui.theme.Spacing
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.WeekFields

private val CELL_HEIGHT = 44.dp
private val DAY_SIZE = 32.dp

/**
 * Calendario de las fechas del viaje: una sola ventana donde se marcan ida y vuelta, con flechas para pasar de mes
 * (y un salto de año al pulsar el mes). Un solo día marcado vale como viaje de un día.
 */
@Composable
fun DateRangePickerDialogHost(
    initialStart: LocalDate?,
    initialEnd: LocalDate?,
    onDismiss: () -> Unit,
    onPick: (LocalDate, LocalDate) -> Unit,
    /** Se abrió desde el campo de la Ida o de la Vuelta: el calendario cambia solo ese extremo. */
    edge: RangeEdge? = null,
) {
    // Se guardan como texto para que el estado sobreviva a un giro de pantalla.
    var startText by rememberSaveable { mutableStateOf(initialStart?.toString().orEmpty()) }
    var endText by rememberSaveable { mutableStateOf(initialEnd?.toString().orEmpty()) }
    var monthText by rememberSaveable {
        // Abre en el mes del extremo que se va a cambiar.
        val focus = if (edge == RangeEdge.END) initialEnd ?: initialStart else initialStart
        mutableStateOf(YearMonth.from(focus ?: LocalDate.now()).toString())
    }
    var yearsOpen by rememberSaveable { mutableStateOf(false) }

    val selection = RangeSelection(startText.toDateOrNull(), endText.toDateOrNull())
    val month = remember(monthText) { YearMonth.parse(monthText) }
    val formats = LocalFormats.current
    val result = selection.result()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            val hasRange = initialStart != null && initialEnd != null
            Text(
                stringResource(
                    when {
                        hasRange && edge == RangeEdge.START -> R.string.field_start
                        hasRange && edge == RangeEdge.END -> R.string.field_end
                        else -> R.string.field_trip_dates
                    }
                )
            )
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Text(
                    text = when {
                        selection.start == null -> stringResource(R.string.range_hint_start)
                        selection.end == null -> stringResource(R.string.range_hint_end)
                        else -> formats.range(selection.start, selection.end)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { monthText = month.minusMonths(1).toString(); yearsOpen = false }) {
                        Icon(Icons.Outlined.ChevronLeft, stringResource(R.string.range_prev_month))
                    }
                    TextButton(onClick = { yearsOpen = !yearsOpen }, modifier = Modifier.weight(1f)) {
                        Text(formats.month(month), style = MaterialTheme.typography.titleMedium)
                        Icon(Icons.Outlined.ExpandMore, null, Modifier.size(20.dp))
                    }
                    IconButton(onClick = { monthText = month.plusMonths(1).toString(); yearsOpen = false }) {
                        Icon(Icons.Outlined.ChevronRight, stringResource(R.string.range_next_month))
                    }
                }
                if (yearsOpen) {
                    YearChooser(month) { year -> monthText = month.withYear(year).toString(); yearsOpen = false }
                } else {
                    MonthView(month, selection) { day ->
                        val next = selection.tap(day, edge)
                        startText = next.start?.toString().orEmpty()
                        endText = next.end?.toString().orEmpty()
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { result?.let { onPick(it.first, it.second) } }, enabled = result != null) {
                Text(stringResource(R.string.action_ok))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

private fun String.toDateOrNull(): LocalDate? = takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

@Composable
private fun YearChooser(month: YearMonth, onPick: (Int) -> Unit) {
    val years = remember(month.year) { MonthGrid.years(LocalDate.now(), month.year).toList() }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        years.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                row.forEach { year ->
                    FilterChip(
                        selected = year == month.year, onClick = { onPick(year) },
                        label = { Text(year.toString(), Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
                        modifier = Modifier.weight(1f),
                    )
                }
                // Última fila incompleta: huecos para que los botones midan lo mismo.
                repeat(3 - row.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun MonthView(month: YearMonth, selection: RangeSelection, onTap: (LocalDate) -> Unit) {
    val formats = LocalFormats.current
    val firstDay: DayOfWeek = remember { WeekFields.of(formats.locale).firstDayOfWeek }
    val today = remember { LocalDate.now() }
    val weeks = remember(month, firstDay) { MonthGrid.weeks(month, firstDay, minWeeks = 6) }

    Column {
        Row(Modifier.fillMaxWidth()) {
            MonthGrid.weekdays(firstDay).forEach { dow ->
                Text(
                    dow.getDisplayName(TextStyle.NARROW, formats.locale),
                    Modifier.weight(1f), textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        weeks.forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { day ->
                    if (day == null) Box(Modifier.weight(1f).height(CELL_HEIGHT))
                    else DayCell(day, selection, today, onTap, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun DayCell(day: LocalDate, selection: RangeSelection, today: LocalDate, onTap: (LocalDate) -> Unit, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val bandColor = colors.secondaryContainer
    val isSelected = selection.isSelected(day)
    val bandLeft = selection.bandLeft(day)
    val bandRight = selection.bandRight(day)
    val description = LocalFormats.current.full(day)

    Box(
        modifier
            .height(CELL_HEIGHT)
            .drawBehind {
                // La franja del rango ocupa la altura del círculo del día y enlaza con las celdas vecinas.
                val h = DAY_SIZE.toPx()
                val top = (size.height - h) / 2f
                if (bandLeft) drawRect(bandColor, Offset(0f, top), Size(size.width / 2f, h))
                if (bandRight) drawRect(bandColor, Offset(size.width / 2f, top), Size(size.width / 2f, h))
            }
            .clickable(onClickLabel = stringResource(R.string.range_day_action)) { onTap(day) }
            .semantics { contentDescription = description; selected = isSelected },
        contentAlignment = Alignment.Center,
    ) {
        val mark = when {
            isSelected -> Modifier.background(colors.primary, CircleShape)
            day == today -> Modifier.border(1.dp, colors.primary, CircleShape)
            else -> Modifier
        }
        Box(Modifier.size(DAY_SIZE).then(mark), contentAlignment = Alignment.Center) {
            Text(
                day.dayOfMonth.toString(), style = MaterialTheme.typography.bodyMedium,
                color = if (isSelected) colors.onPrimary else colors.onSurface,
                fontWeight = if (isSelected || day == today) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
    }
}

/**
 * Fechas del viaje en dos campos, Ida y Vuelta, como siempre; los dos abren el mismo calendario de rango
 * y cada uno cambia su extremo. [onPick] recibe ida y vuelta ya elegidas.
 */
@Composable
fun TripDateFields(
    start: LocalDate?,
    end: LocalDate?,
    startError: String?,
    endError: String?,
    onPick: (LocalDate, LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val formats = LocalFormats.current
    var edge by remember { mutableStateOf<RangeEdge?>(null) }
    var open by remember { mutableStateOf(false) }
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        PickerField(
            label = stringResource(R.string.field_start), value = start?.let { formats.date(it) }.orEmpty(),
            onClick = { edge = RangeEdge.START; open = true }, modifier = Modifier.weight(1f), error = startError,
        )
        PickerField(
            label = stringResource(R.string.field_end), value = end?.let { formats.date(it) }.orEmpty(),
            onClick = { edge = RangeEdge.END; open = true }, modifier = Modifier.weight(1f), error = endError,
        )
    }
    if (open) DateRangePickerDialogHost(
        initialStart = start, initialEnd = end, edge = edge,
        onDismiss = { open = false },
        onPick = { s, e -> onPick(s, e); open = false },
    )
}

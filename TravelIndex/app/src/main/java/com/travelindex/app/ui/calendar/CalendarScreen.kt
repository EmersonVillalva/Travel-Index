package com.travelindex.app.ui.calendar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.travelindex.app.R
import com.travelindex.app.domain.CalendarCell
import com.travelindex.app.domain.DayEntry
import com.travelindex.app.ui.components.EmptyState
import com.travelindex.app.ui.components.LoadingBox
import com.travelindex.app.ui.components.SectionTitle
import com.travelindex.app.ui.modules.EventRow
import com.travelindex.app.ui.theme.CenteredContent
import com.travelindex.app.ui.theme.LocalFormats
import com.travelindex.app.ui.theme.Spacing
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** Calendario mensual con todos los viajes; al pulsar un día se ven sus eventos de todos los viajes. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    onOpenTrip: (Long) -> Unit,
    onOpenEvent: (DayEntry) -> Unit,
    vm: CalendarViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val f = LocalFormats.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_calendar)) },
                actions = { TextButton(onClick = vm::goToToday) { Text(stringResource(R.string.calendar_today)) } },
            )
        },
    ) { pad ->
        if (state.loading) { LoadingBox(Modifier.padding(pad)); return@Scaffold }
        CenteredContent(Modifier.padding(pad), maxWidth = 640.dp) {
            LazyColumn(contentPadding = PaddingValues(start = Spacing.m, end = Spacing.m, top = Spacing.xs, bottom = Spacing.xl)) {
                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        IconButton(onClick = vm::previousMonth) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.calendar_prev))
                        }
                        Text(f.month(state.month), style = MaterialTheme.typography.titleLarge)
                        IconButton(onClick = vm::nextMonth) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.calendar_next))
                        }
                    }
                }
                item { WeekdayHeader(state.firstDay) }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        state.weeks.forEach { week ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                week.forEach { cell ->
                                    DayCell(cell, selected = cell.date == state.selected, Modifier.weight(1f)) { vm.select(cell.date) }
                                }
                            }
                        }
                    }
                }
                item {
                    Row(Modifier.padding(top = Spacing.s), horizontalArrangement = Arrangement.spacedBy(Spacing.m),
                        verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.25f)))
                        Text(stringResource(R.string.calendar_legend_trip), style = MaterialTheme.typography.labelSmall)
                        Box(Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.tertiary))
                        Text(stringResource(R.string.calendar_legend_events), style = MaterialTheme.typography.labelSmall)
                    }
                }
                item { SectionTitle(f.full(state.selected)) }

                if (!state.hasAnyTrip) {
                    item {
                        EmptyState(stringResource(R.string.calendar_empty_title), stringResource(R.string.calendar_empty_body),
                            icon = Icons.Outlined.CalendarMonth)
                    }
                } else {
                    if (state.trips.isNotEmpty()) {
                        items(state.trips, key = { "t" + it.id }) { t ->
                            Card(
                                onClick = { onOpenTrip(t.id) }, modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.s),
                                shape = MaterialTheme.shapes.medium, elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                            ) {
                                Column(Modifier.padding(Spacing.m)) {
                                    Text(t.name, style = MaterialTheme.typography.titleSmall)
                                    Text(f.range(t.startDate, t.endDate), style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    if (state.entries.isEmpty()) {
                        item {
                            Text(
                                stringResource(R.string.calendar_no_plans), style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = Spacing.s),
                            )
                        }
                    } else {
                        items(state.entries, key = { it.trip.id.toString() + it.event.key }) { e ->
                            EventRow(e.event, onClick = { onOpenEvent(e) }, tripName = e.trip.name, modifier = Modifier.padding(bottom = Spacing.s))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekdayHeader(first: DayOfWeek) {
    val locale = Locale.getDefault()
    Row(Modifier.fillMaxWidth()) {
        (0 until 7).forEach { i ->
            val d = first.plus(i.toLong())
            Text(
                d.getDisplayName(TextStyle.SHORT, locale), Modifier.weight(1f), textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
            )
        }
    }
}

@Composable
private fun DayCell(cell: CalendarCell, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val f = LocalFormats.current
    val inTrip = cell.tripIds.isNotEmpty()
    val shape = RoundedCornerShape(10.dp)
    val bg = when {
        selected -> MaterialTheme.colorScheme.primary
        inTrip -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.22f)
        else -> Color.Transparent
    }
    val fg = when {
        selected -> MaterialTheme.colorScheme.onPrimary
        !cell.inMonth -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        else -> MaterialTheme.colorScheme.onSurface
    }
    val desc = f.full(cell.date) +
        (if (cell.tripIds.isNotEmpty()) ", " + stringResource(R.string.calendar_a11y_trip) else "") +
        (if (cell.eventTypes.isNotEmpty()) ", " + stringResource(R.string.calendar_a11y_events) else "")
    Box(
        modifier
            .heightIn(min = 44.dp).aspectRatio(1f)
            .clip(shape).background(bg)
            .then(if (cell.isToday && !selected) Modifier.border(BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary), shape) else Modifier)
            .clickable(onClick = onClick)
            .semantics { contentDescription = desc },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                cell.date.dayOfMonth.toString(), color = fg, style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (cell.isToday) FontWeight.Bold else FontWeight.Normal,
            )
            Box(
                Modifier.padding(top = 2.dp).size(6.dp).clip(CircleShape)
                    .background(if (cell.eventTypes.isNotEmpty()) (if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.tertiary) else Color.Transparent),
            )
        }
    }
}

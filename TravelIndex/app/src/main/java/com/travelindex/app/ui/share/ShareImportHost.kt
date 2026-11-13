package com.travelindex.app.ui.share

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.travelindex.app.R
import com.travelindex.app.data.SharePreview
import com.travelindex.app.domain.ModuleType
import com.travelindex.app.domain.ShareDecode
import com.travelindex.app.ui.components.humanSize
import com.travelindex.app.ui.theme.LocalFormats
import com.travelindex.app.ui.theme.Spacing
import java.time.LocalDate

/** Ventanas de la importación de un viaje compartido. Se coloca una sola vez, encima de toda la navegación. */
@Composable
fun ShareImportHost(onOpenTrip: (Long) -> Unit, vm: ShareImportViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()

    when (val s = state) {
        ImportUi.Idle -> Unit
        ImportUi.Working -> AlertDialog(
            onDismissRequest = {}, confirmButton = {},
            title = { Text(stringResource(R.string.import_working)) },
            text = { LinearProgressIndicator(Modifier.fillMaxWidth()) },
        )
        is ImportUi.Confirm -> AlertDialog(
            onDismissRequest = vm::dismiss,
            title = { Text(stringResource(R.string.import_confirm_title)) },
            text = { PreviewText(s.preview) },
            confirmButton = { TextButton(onClick = vm::confirm) { Text(stringResource(R.string.import_confirm_yes)) } },
            dismissButton = { TextButton(onClick = vm::dismiss) { Text(stringResource(R.string.action_cancel)) } },
        )
        is ImportUi.Failed -> AlertDialog(
            onDismissRequest = vm::dismiss,
            title = { Text(stringResource(R.string.import_failed_title)) },
            text = {
                Text(
                    stringResource(
                        when {
                            s.unreadable -> R.string.import_err_unreadable
                            s.reason == ShareDecode.Reason.TOO_NEW -> R.string.import_err_too_new
                            s.reason == ShareDecode.Reason.TOO_BIG -> R.string.import_err_too_big
                            s.reason == null -> R.string.import_err_generic
                            else -> R.string.import_err_not_trip
                        }
                    )
                )
            },
            confirmButton = { TextButton(onClick = vm::dismiss) { Text(stringResource(R.string.action_ok)) } },
        )
        is ImportUi.Done -> {
            if (s.skipped == 0) {
                LaunchedEffect(s.tripId) { vm.dismiss(); onOpenTrip(s.tripId) }
            } else {
                AlertDialog(
                    onDismissRequest = { vm.dismiss(); onOpenTrip(s.tripId) },
                    title = { Text(stringResource(R.string.import_done_title)) },
                    text = { Text(stringResource(R.string.import_done_skipped, s.skipped)) },
                    confirmButton = { TextButton(onClick = { vm.dismiss(); onOpenTrip(s.tripId) }) { Text(stringResource(R.string.action_ok)) } },
                )
            }
        }
    }
}

@Composable
private fun PreviewText(p: SharePreview) {
    val f = LocalFormats.current
    fun date(t: String) = runCatching { f.date(LocalDate.parse(t)) }.getOrDefault(t)
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(p.name, style = MaterialTheme.typography.titleMedium)
        Text(listOf(p.city, p.country).filter { it.isNotBlank() }.joinToString(", "), style = MaterialTheme.typography.bodyMedium)
        Text(date(p.startDate) + " – " + date(p.endDate), style = MaterialTheme.typography.bodyMedium)
        val parts = buildList {
            p.counts[ModuleType.FLIGHT]?.let { add(stringResource(R.string.import_count_flights, it)) }
            p.counts[ModuleType.LODGING]?.let { add(stringResource(R.string.import_count_lodgings, it)) }
            val others = p.counts.filterKeys { it != ModuleType.FLIGHT && it != ModuleType.LODGING }.values.sum()
            if (others > 0) add(stringResource(R.string.import_count_others, others))
            if (p.fileCount > 0) add(stringResource(R.string.import_count_files, p.fileCount, humanSize(p.totalBytes)))
        }
        if (parts.isNotEmpty()) Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.import_confirm_body), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

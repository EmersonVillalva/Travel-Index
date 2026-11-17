package com.travelindex.app.ui.trips

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.travelindex.app.R
import com.travelindex.app.data.FileStorage
import com.travelindex.app.domain.TravelModes
import com.travelindex.app.domain.forms.FieldDef
import com.travelindex.app.ui.components.ConfirmDialog
import com.travelindex.app.ui.components.FormField
import com.travelindex.app.ui.components.errorText
import com.travelindex.app.ui.components.TripDateFields
import com.travelindex.app.ui.components.LoadingBox
import com.travelindex.app.ui.components.SectionCard
import com.travelindex.app.ui.components.TravelModePicker
import com.travelindex.app.ui.theme.CenteredContent
import com.travelindex.app.ui.theme.Spacing
import java.io.File
import java.time.LocalDate

/** Crear o editar un viaje. Al guardar un viaje nuevo se abre su panel para poder agregar cosas enseguida. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateTripScreen(
    onClose: () -> Unit,
    onSaved: (tripId: Long, isNew: Boolean) -> Unit,
    vm: CreateTripViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var confirmDiscard by remember { mutableStateOf(false) }
    val failMsg = stringResource(R.string.error_save)
    val fixMsg = stringResource(R.string.error_fix_fields)
    val savedMsg = stringResource(R.string.trip_saved)

    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) vm.setCover(uri) }

    LaunchedEffect(Unit) {
        vm.events.collect { e ->
            when (e) {
                is TripFormEvent.Saved -> { Toast.makeText(ctx, savedMsg, Toast.LENGTH_SHORT).show(); onSaved(e.tripId, e.isNew) }
                TripFormEvent.Failed -> snackbar.showSnackbar(failMsg)
                TripFormEvent.ValidationFailed -> snackbar.showSnackbar(fixMsg)
            }
        }
    }
    LaunchedEffect(state.loaded, state.notFound) { if (state.loaded && state.notFound) onClose() }

    val tryClose = { if (state.dirty) confirmDiscard = true else onClose() }
    BackHandler(enabled = state.dirty) { confirmDiscard = true }

    Scaffold(
        modifier = Modifier.imePadding(),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (vm.isEditing) R.string.trip_form_title_edit else R.string.trip_form_title_new)) },
                navigationIcon = { IconButton(onClick = tryClose) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Button(
                    onClick = vm::save, enabled = state.loaded && !state.saving,
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = Spacing.m, vertical = Spacing.s),
                ) { Text(stringResource(R.string.action_save)) }
            }
        },
    ) { pad ->
        if (!state.loaded) { LoadingBox(Modifier.padding(pad)); return@Scaffold }
        CenteredContent(Modifier.padding(pad)) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.m),
                verticalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                SectionCard(stringResource(R.string.sec_trip_basic)) {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        TripField(TripSpec.name, state, vm); TripField(TripSpec.city, state, vm); TripField(TripSpec.country, state, vm)
                    }
                }
                SectionCard(stringResource(R.string.sec_trip_dates)) {
                    TripDatesField(state, vm)
                }
                SectionCard(stringResource(R.string.sec_trip_transport)) {
                    Text(
                        stringResource(R.string.trip_modes_hint), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TravelModePicker(selected = TravelModes.parse(state.values["travelModes"]), onToggle = vm::toggleMode)
                }
                SectionCard(stringResource(R.string.sec_trip_budget)) {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        // El presupuesto y las notas del viaje ya no se piden (si un viaje los tenía, se conservan).
                        TripField(TripSpec.travelers, state, vm)
                        TripField(TripSpec.currency, state, vm)
                    }
                }
                SectionCard(stringResource(R.string.sec_trip_cover)) {
                    CoverPreview(state.newCover, if (state.removeCover) null else state.existingCover)
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        OutlinedButton(onClick = { coverPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                            Icon(Icons.Outlined.Image, null)
                            Text("  " + stringResource(R.string.action_cover))
                        }
                        if (state.newCover != null || (state.existingCover != null && !state.removeCover)) {
                            OutlinedButton(onClick = vm::removeCover) { Text(stringResource(R.string.action_remove_cover)) }
                        }
                    }
                }
                SectionCard(stringResource(R.string.sec_trip_more)) {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        TripField(TripSpec.description, state, vm)
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
}

/**
 * Ida y vuelta en dos campos (como siempre) que abren un solo calendario: marcas el primer y el último día
 * (1 feb – 5 feb) sin abrir dos ventanas; tocando un campo después, el calendario cambia solo ese extremo.
 */
@Composable
private fun TripDatesField(state: TripFormState, vm: CreateTripViewModel) {
    fun parsed(key: String): LocalDate? =
        state.values[key]?.takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    TripDateFields(
        start = parsed(TripSpec.startDate.key), end = parsed(TripSpec.endDate.key),
        startError = state.errors[TripSpec.startDate.key]?.let { errorText(it) },
        endError = state.errors[TripSpec.endDate.key]?.let { errorText(it) },
        onPick = { s, e ->
            vm.setValue(TripSpec.startDate.key, s.toString())
            vm.setValue(TripSpec.endDate.key, e.toString())
        },
    )
}

@Composable
private fun TripField(def: FieldDef, state: TripFormState, vm: CreateTripViewModel) {
    // La vuelta no puede ser anterior a la ida: el calendario de la vuelta no deja elegir esos días.
    val minDate = if (def.key == TripSpec.endDate.key)
        state.values[TripSpec.startDate.key]?.takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    else null
    FormField(def, state.values[def.key].orEmpty(), state.errors[def.key], onChange = { vm.setValue(def.key, it) }, minDate = minDate)
}

@Composable
private fun CoverPreview(newCover: Uri?, existing: String?) {
    val ctx = LocalContext.current
    val model: Any? = newCover ?: existing?.let { File(File(ctx.filesDir, FileStorage.DIR), it) }
    if (model != null) {
        AsyncImage(
            model = model, contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().height(160.dp).clip(MaterialTheme.shapes.medium),
        )
    } else {
        Box(
            Modifier.fillMaxWidth().height(96.dp).clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) { Text(stringResource(R.string.cover_none), color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

package com.travelindex.app.ui.modules

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.travelindex.app.data.FileStorage
import com.travelindex.app.data.db.AttachmentEntity
import com.travelindex.app.data.db.FlightEntity
import com.travelindex.app.data.repo.AttachmentRepository
import com.travelindex.app.data.repo.ModuleRepository
import com.travelindex.app.data.repo.TripRepository
import com.travelindex.app.domain.Airports
import com.travelindex.app.domain.FlightConnections
import com.travelindex.app.domain.ModuleTarget
import com.travelindex.app.domain.ModuleType
import com.travelindex.app.domain.TripBundle
import com.travelindex.app.domain.forms.FieldError
import com.travelindex.app.domain.forms.FormDefaults
import com.travelindex.app.domain.forms.FormValidator
import com.travelindex.app.domain.forms.ModuleCodecs
import com.travelindex.app.domain.forms.ModuleItem
import com.travelindex.app.domain.forms.ModuleItems
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class BundleState(val loaded: Boolean = false, val bundle: TripBundle? = null)

/** Un viaje completo (solo lectura). Lo usan itinerario, lugares, presupuesto, listas de módulos... */
@HiltViewModel
class TripBundleViewModel @Inject constructor(savedState: SavedStateHandle, repo: TripRepository) : ViewModel() {
    val tripId: Long = savedState["tripId"] ?: 0L
    val state: StateFlow<BundleState> = repo.observeBundle(tripId)
        .map { BundleState(loaded = true, bundle = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BundleState())
}

data class PendingFile(val uri: Uri, val name: String)

sealed interface FormEvent {
    data class Saved(val failedFiles: Int) : FormEvent
    /** Se guardó un tramo de un vuelo con escalas y el formulario queda listo para el siguiente (número de tramo). */
    data class NextLeg(val leg: Int) : FormEvent
    data object ValidationFailed : FormEvent
    data object SaveFailed : FormEvent
    data object FileFailed : FormEvent
}

data class FormState(
    val loaded: Boolean = false,
    val notFound: Boolean = false,
    val values: Map<String, String> = emptyMap(),
    val errors: Map<String, FieldError> = emptyMap(),
    val saving: Boolean = false,
    val pending: List<PendingFile> = emptyList(),
    val existing: List<AttachmentEntity> = emptyList(),
    /** true cuando el usuario ya ha cambiado algo (para avisar antes de descartar). */
    val dirty: Boolean = false,
    /** Vuelo con escalas: número del tramo que se está rellenando (2 o más); 0 si es un vuelo normal. */
    val leg: Int = 0,
    /** Vuelo: se puede añadir otro tramo a continuación (no si se edita un tramo que ya tiene otros después). */
    val canAddLeg: Boolean = true,
)

@HiltViewModel
class ModuleFormViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val trips: TripRepository,
    private val modules: ModuleRepository,
    private val attachments: AttachmentRepository,
    private val files: FileStorage,
) : ViewModel() {
    val tripId: Long = savedState["tripId"] ?: 0L
    /** El módulo y, en transporte, el apartado (tren, autobús, barco, coche) con el que se abrió la pantalla. */
    private val target: ModuleTarget = ModuleTarget.parse(savedState["module"])
    val type: ModuleType = target.type
    val itemId: Long = savedState["itemId"] ?: 0L
    val spec: ModuleSpec = ModuleSpecs.of(type, target.section)

    private val _state = MutableStateFlow(FormState())
    val state: StateFlow<FormState> = _state.asStateFlow()
    private val _events = Channel<FormEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var initialized = false
    /** El elemento que se edita ahora: pasa a 0 (nuevo) al seguir con el siguiente tramo de un vuelo con escalas. */
    private var editingId: Long = itemId
    private var knownFlights: List<FlightEntity> = emptyList()

    init {
        viewModelScope.launch {
            trips.observeBundle(tripId).collect { b ->
                if (b == null) { _state.update { it.copy(loaded = true, notFound = true) }; return@collect }
                knownFlights = b.flights
                val owned = b.attachments.filter { it.ownerType == type.name && it.ownerId == editingId && editingId != 0L }
                if (!initialized) {
                    initialized = true
                    val existingItem = if (itemId != 0L) ModuleItems.of(type, b).firstOrNull { it.id == itemId } else null
                    if (itemId != 0L && existingItem == null) {
                        _state.update { it.copy(loaded = true, notFound = true) }
                    } else {
                        val values = existingItem?.values ?: FormDefaults.forNew(type, b, LocalDate.now(), transportType = target.section?.transportType)
                        // Editando un tramo que ya tiene otros después, no se ofrece añadir uno a continuación.
                        val canAddLeg = type != ModuleType.FLIGHT ||
                            (b.flights.firstOrNull { it.id == itemId }?.let { FlightConnections.isLastLeg(it, b.flights) } ?: true)
                        _state.update { it.copy(loaded = true, values = values, existing = owned, canAddLeg = canAddLeg) }
                    }
                } else {
                    _state.update { it.copy(existing = owned) }
                }
            }
        }
    }

    fun setValue(key: String, value: String) {
        _state.update { s ->
            val next = s.values.toMutableMap()
            next[key] = value
            // Al escribir un aeropuerto conocido (MAD, FCO...) se rellena su zona horaria.
            if (type == ModuleType.FLIGHT) {
                if (key == "departureAirport") Airports.zoneFor(value)?.let { next["depZone"] = it }
                if (key == "arrivalAirport") Airports.zoneFor(value)?.let { next["arrZone"] = it }
            }
            s.copy(values = next, errors = s.errors - key, dirty = true)
        }
    }

    fun addFile(uri: Uri) {
        if (editingId != 0L) {
            viewModelScope.launch {
                runCatching { attachments.add(tripId, uri, type.name, editingId) }
                    .onFailure { _events.send(FormEvent.FileFailed) }
            }
        } else {
            _state.update { it.copy(pending = it.pending + PendingFile(uri, files.displayName(uri)), dirty = true) }
        }
    }

    fun removePending(index: Int) = _state.update { s -> s.copy(pending = s.pending.filterIndexed { i, _ -> i != index }) }

    fun removeExisting(att: AttachmentEntity) {
        viewModelScope.launch { runCatching { attachments.delete(att) } }
    }

    fun save() {
        val s = _state.value
        if (s.saving) return
        val fieldErrors = FormValidator.validate(spec.fields, s.values)
        val errors = if (fieldErrors.isEmpty()) ModuleCodecs.cross(type, s.values) else fieldErrors
        if (errors.isNotEmpty()) {
            _state.update { it.copy(errors = errors) }
            viewModelScope.launch { _events.send(FormEvent.ValidationFailed) }
            return
        }
        _state.update { it.copy(saving = true, errors = emptyMap()) }
        viewModelScope.launch {
            try {
                // Se guardan nombres de aeropuerto, no códigos ("MAD" → "Madrid").
                // Vuelo con escalas: este tramo y los siguientes comparten un identificador de conexión.
                val chain = type == ModuleType.FLIGHT && s.values["nextLeg"] == "1"
                val connectionId = s.values["connectionId"].orEmpty()
                    .let { if (chain && it.isBlank()) FlightConnections.newConnectionId() else it }
                val values = if (type == ModuleType.FLIGHT) s.values + mapOf(
                    "departureAirport" to Airports.displayName(s.values["departureAirport"].orEmpty()),
                    "arrivalAirport" to Airports.displayName(s.values["arrivalAirport"].orEmpty()),
                    "connectionId" to connectionId,
                ) else s.values
                val id = modules.save(type, tripId, editingId, values)
                var failed = 0
                s.pending.forEach { p ->
                    runCatching { attachments.add(tripId, p.uri, type.name, id) }.onFailure { failed++ }
                }
                if (chain) {
                    // Los demás tramos de la conexión que ya están guardados, más este y el que viene.
                    val leg = knownFlights.count { it.connectionId == connectionId && it.id != id } + 2
                    editingId = 0
                    _state.update {
                        FormState(
                            loaded = true, values = FlightConnections.nextLegValues(values, connectionId), leg = leg,
                            canAddLeg = true,
                        )
                    }
                    if (failed > 0) _events.send(FormEvent.FileFailed)
                    _events.send(FormEvent.NextLeg(leg))
                } else {
                    _events.send(FormEvent.Saved(failed))
                }
            } catch (t: Throwable) {
                _state.update { it.copy(saving = false) }
                _events.send(FormEvent.SaveFailed)
            }
        }
    }
}

data class DetailState(
    val loaded: Boolean = false,
    val bundle: TripBundle? = null,
    val item: ModuleItem? = null,
    val attachments: List<AttachmentEntity> = emptyList(),
)

@HiltViewModel
class ModuleDetailViewModel @Inject constructor(
    savedState: SavedStateHandle,
    trips: TripRepository,
    private val modules: ModuleRepository,
    private val attachments: AttachmentRepository,
) : ViewModel() {
    val tripId: Long = savedState["tripId"] ?: 0L
    private val target: ModuleTarget = ModuleTarget.parse(savedState["module"])
    val type: ModuleType = target.type
    val itemId: Long = savedState["itemId"] ?: 0L
    val spec: ModuleSpec = ModuleSpecs.of(type, target.section)

    val state: StateFlow<DetailState> = trips.observeBundle(tripId).map { b ->
        DetailState(
            loaded = true, bundle = b,
            item = b?.let { ModuleItems.of(type, it).firstOrNull { i -> i.id == itemId } },
            attachments = b?.attachments.orEmpty().filter { it.ownerType == type.name && it.ownerId == itemId },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailState())

    private val _messages = Channel<Boolean>(Channel.BUFFERED)
    /** true = error al adjuntar un archivo. */
    val messages = _messages.receiveAsFlow()

    /** Al borrar, el flujo deja de encontrar el elemento y la pantalla se cierra sola. */
    fun delete() {
        viewModelScope.launch {
            runCatching { modules.delete(type, itemId) }
        }
    }

    fun addFile(uri: Uri) {
        viewModelScope.launch {
            runCatching { attachments.add(tripId, uri, type.name, itemId) }.onFailure { _messages.send(true) }
        }
    }

    fun removeAttachment(att: AttachmentEntity) {
        viewModelScope.launch { runCatching { attachments.delete(att) } }
    }
}

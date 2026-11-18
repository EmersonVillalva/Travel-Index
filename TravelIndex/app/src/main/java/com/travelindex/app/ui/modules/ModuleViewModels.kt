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
    /** Vuelo: cuántos vuelos tiene ya guardados este viaje (con escalas son varios); sirve para avisar si se quitan escalas. */
    val savedLegs: Int = 0,
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
    private val editingId: Long = itemId
    private var knownFlights: List<FlightEntity> = emptyList()
    /** Vuelo: los tramos que ya estaban guardados al abrir el formulario, por orden (un vuelo con escalas son varios). */
    private var legIds: List<Long> = emptyList()

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
                    } else if (type == ModuleType.FLIGHT && existingItem != null) {
                        // Un vuelo con escalas se edita entero, sea cual sea el tramo que se abrió.
                        val legs = FlightConnections.legsOf(b.flights.first { it.id == itemId }, b.flights)
                        legIds = legs.map { it.id }
                        _state.update {
                            it.copy(loaded = true, values = FlightConnections.formValues(legs), existing = owned, savedLegs = legs.size)
                        }
                    } else {
                        val values = existingItem?.values ?: FormDefaults.forNew(type, b, LocalDate.now(), transportType = target.section?.transportType)
                        _state.update { it.copy(loaded = true, values = values, existing = owned) }
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
                FlightConnections.parseStopKey(key)?.let { (index, name) ->
                    if (name == "airport") Airports.zoneFor(value)?.let { next[FlightConnections.stopKey(index, "zone")] = it }
                }
            }
            s.copy(values = next, errors = s.errors - key, dirty = true)
        }
    }

    /** Vuelo: cuántas escalas tiene (0 = directo). Los bloques de cada escala aparecen entre la salida y la llegada. */
    fun setStops(count: Int) {
        _state.update { s ->
            val next = FlightConnections.withStops(s.values, count)
            // Los errores de escalas que ya no se ven dejan de tener sentido.
            val errors = s.errors.filterKeys { k -> FlightConnections.parseStopKey(k)?.let { it.first <= count } ?: true }
            s.copy(values = next, errors = errors, dirty = true)
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
        // Solo se valida lo que se ve: las escalas ocultas no cuentan.
        val fieldErrors = FormValidator.validate(spec.visibleFields(s.values), s.values)
        val errors = if (fieldErrors.isEmpty()) ModuleCodecs.cross(type, s.values) else fieldErrors
        if (errors.isNotEmpty()) {
            _state.update { it.copy(errors = errors) }
            viewModelScope.launch { _events.send(FormEvent.ValidationFailed) }
            return
        }
        _state.update { it.copy(saving = true, errors = emptyMap()) }
        viewModelScope.launch {
            try {
                val id = if (type == ModuleType.FLIGHT) {
                    // Un vuelo con escalas son varios vuelos enlazados por un mismo identificador de conexión.
                    val existing = legIds.mapNotNull { id -> knownFlights.firstOrNull { it.id == id } }
                    val connectionId = s.values["connectionId"].orEmpty().ifBlank { FlightConnections.newConnectionId() }
                    modules.saveFlightLegs(existing, FlightConnections.buildLegs(tripId, connectionId, s.values, existing))
                } else {
                    modules.save(type, tripId, editingId, s.values)
                }
                var failed = 0
                s.pending.forEach { p ->
                    runCatching { attachments.add(tripId, p.uri, type.name, id) }.onFailure { failed++ }
                }
                _events.send(FormEvent.Saved(failed))
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

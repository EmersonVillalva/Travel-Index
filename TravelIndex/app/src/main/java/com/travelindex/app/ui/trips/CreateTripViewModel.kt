package com.travelindex.app.ui.trips

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.travelindex.app.data.FileStorage
import com.travelindex.app.data.SettingsRepository
import com.travelindex.app.data.db.TripEntity
import com.travelindex.app.data.repo.TripRepository
import com.travelindex.app.domain.forms.FieldError
import com.travelindex.app.domain.forms.FormValidator
import com.travelindex.app.domain.forms.TripCodec
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TripFormState(
    val loaded: Boolean = false,
    val notFound: Boolean = false,
    val values: Map<String, String> = emptyMap(),
    val errors: Map<String, FieldError> = emptyMap(),
    val saving: Boolean = false,
    /** Portada nueva elegida en esta sesión (aún no copiada al almacenamiento privado). */
    val newCover: Uri? = null,
    /** Portada que ya tiene el viaje (al editar). */
    val existingCover: String? = null,
    val removeCover: Boolean = false,
    val dirty: Boolean = false,
)

sealed interface TripFormEvent {
    /** [tripId] es el viaje guardado: si era nuevo se abre su panel para poder agregar cosas enseguida. */
    data class Saved(val tripId: Long, val isNew: Boolean) : TripFormEvent
    data object Failed : TripFormEvent
    data object ValidationFailed : TripFormEvent
}

/** Crea o edita un viaje: sin argumento `tripId` (o 0) crea uno nuevo. */
@HiltViewModel
class CreateTripViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repo: TripRepository,
    private val files: FileStorage,
    private val settings: SettingsRepository,
) : ViewModel() {
    val tripId: Long = savedState["tripId"] ?: 0L
    val isEditing: Boolean get() = tripId != 0L

    private val _state = MutableStateFlow(TripFormState())
    val state: StateFlow<TripFormState> = _state.asStateFlow()
    private val _events = Channel<TripFormEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var original: TripEntity? = null

    init {
        viewModelScope.launch {
            if (tripId == 0L) {
                val cur = runCatching { settings.settings.first().defaultCurrency }.getOrDefault("EUR")
                _state.update { it.copy(loaded = true, values = TripCodec.defaults(cur)) }
            } else {
                val t = repo.getTrip(tripId)
                if (t == null) {
                    _state.update { it.copy(loaded = true, notFound = true) }
                } else {
                    original = t
                    _state.update { it.copy(loaded = true, values = TripCodec.values(t), existingCover = t.coverPath) }
                }
            }
        }
    }

    fun setValue(key: String, value: String) {
        _state.update { s -> s.copy(values = s.values + (key to value), errors = s.errors - key, dirty = true) }
    }

    fun setCover(uri: Uri) = _state.update { it.copy(newCover = uri, removeCover = false, dirty = true) }

    fun removeCover() = _state.update { it.copy(newCover = null, removeCover = true, dirty = true) }

    fun save() {
        val s = _state.value
        if (s.saving) return
        val fieldErrors = FormValidator.validate(TripSpec.all, s.values)
        val errors = if (fieldErrors.isEmpty()) TripCodec.cross(s.values) else fieldErrors
        if (errors.isNotEmpty()) {
            _state.update { it.copy(errors = errors) }
            viewModelScope.launch { _events.send(TripFormEvent.ValidationFailed) }
            return
        }
        _state.update { it.copy(errors = emptyMap(), saving = true) }
        viewModelScope.launch {
            var imported: String? = null
            try {
                imported = s.newCover?.let { files.import(it).relativePath }
                val old = original
                val cover = when {
                    imported != null -> imported
                    s.removeCover -> null
                    else -> old?.coverPath
                }
                val id = if (old == null) {
                    repo.createTrip(TripCodec.build(0, s.values, cover, System.currentTimeMillis(), false))
                } else {
                    repo.updateTrip(TripCodec.build(old.id, s.values, cover, old.createdAt, old.cancelled))
                    old.id
                }
                // La portada anterior solo se borra cuando el nuevo guardado ya ha tenido éxito.
                val oldCover = old?.coverPath
                if (oldCover != null && oldCover != cover) files.delete(oldCover)
                _events.send(TripFormEvent.Saved(id, isNew = old == null))
            } catch (t: Throwable) {
                imported?.let { files.delete(it) }   // no dejar archivos huérfanos si falla el guardado
                _state.update { it.copy(saving = false) }
                _events.send(TripFormEvent.Failed)
            }
        }
    }
}

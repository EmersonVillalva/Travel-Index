package com.travelindex.app.ui.trips

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.travelindex.app.data.TripShareManager
import com.travelindex.app.data.repo.AttachmentRepository
import com.travelindex.app.data.repo.TripRepository
import com.travelindex.app.ui.modules.BundleState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

sealed interface ShareEvent {
    data class Ready(val file: File) : ShareEvent
    data object Failed : ShareEvent
}

/** Panel de un viaje: datos en vivo y acciones del viaje (cancelar, borrar, añadir archivo suelto). */
@HiltViewModel
class TripDetailViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repo: TripRepository,
    private val attachments: AttachmentRepository,
    private val shareManager: TripShareManager,
) : ViewModel() {
    val tripId: Long = savedState["tripId"] ?: 0L

    val state: StateFlow<BundleState> = repo.observeBundle(tripId)
        .map { BundleState(loaded = true, bundle = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BundleState())

    private val _messages = Channel<Boolean>(Channel.BUFFERED)
    /** true = archivo añadido; false = no se pudo añadir. */
    val messages = _messages.receiveAsFlow()

    private val _share = Channel<ShareEvent>(Channel.BUFFERED)
    val shareEvents = _share.receiveAsFlow()
    private val _sharing = MutableStateFlow(false)
    val sharing: StateFlow<Boolean> = _sharing.asStateFlow()

    /** Prepara el archivo con una copia exacta del viaje; la pantalla lo entrega a la app que elija el usuario. */
    fun share() {
        if (_sharing.value) return
        _sharing.value = true
        viewModelScope.launch {
            val file = runCatching { shareManager.export(tripId) }.getOrNull()
            _sharing.value = false
            _share.send(if (file != null) ShareEvent.Ready(file) else ShareEvent.Failed)
        }
    }

    fun setCancelled(cancelled: Boolean) {
        viewModelScope.launch { runCatching { repo.setCancelled(tripId, cancelled) } }
    }

    /** Al borrarse, el flujo deja de encontrar el viaje y la pantalla se cierra sola. */
    fun delete() {
        viewModelScope.launch { runCatching { repo.deleteTrip(tripId) } }
    }

    fun addFile(uri: Uri) {
        viewModelScope.launch {
            val ok = runCatching { attachments.add(tripId, uri, null, null) }.isSuccess
            _messages.send(ok)
        }
    }
}

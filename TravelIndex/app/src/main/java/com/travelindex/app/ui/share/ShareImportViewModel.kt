package com.travelindex.app.ui.share

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.travelindex.app.data.ImportInbox
import com.travelindex.app.data.ImportResult
import com.travelindex.app.data.PrepareResult
import com.travelindex.app.data.SharePreview
import com.travelindex.app.data.TripShareManager
import com.travelindex.app.domain.ShareDecode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ImportUi {
    data object Idle : ImportUi
    data object Working : ImportUi
    data class Confirm(val preview: SharePreview) : ImportUi
    data class Failed(val reason: ShareDecode.Reason?, val unreadable: Boolean = false) : ImportUi
    data class Done(val tripId: Long, val skipped: Int) : ImportUi
}

/**
 * Recibe el archivo de un viaje compartido (desde otra app o desde "Importar viaje"), lo valida,
 * pregunta al usuario y crea una copia exacta del viaje.
 */
@HiltViewModel
class ShareImportViewModel @Inject constructor(
    private val inbox: ImportInbox,
    private val manager: TripShareManager,
) : ViewModel() {
    private val _state = MutableStateFlow<ImportUi>(ImportUi.Idle)
    val state: StateFlow<ImportUi> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            inbox.pending.filterNotNull().collect { uri ->
                inbox.clear()
                _state.value = ImportUi.Working
                _state.value = when (val r = manager.prepare(uri)) {
                    is PrepareResult.Ready -> ImportUi.Confirm(r.preview)
                    is PrepareResult.Invalid -> ImportUi.Failed(r.reason)
                    PrepareResult.Unreadable -> ImportUi.Failed(null, unreadable = true)
                }
            }
        }
    }

    fun confirm() {
        if (_state.value !is ImportUi.Confirm) return
        _state.value = ImportUi.Working
        viewModelScope.launch {
            _state.value = when (val r = manager.commit()) {
                is ImportResult.Done -> ImportUi.Done(r.tripId, r.skippedItems)
                ImportResult.Failed -> ImportUi.Failed(null)
            }
        }
    }

    /** El usuario dice que no, o cierra el aviso. */
    fun dismiss() {
        manager.discard()
        _state.value = ImportUi.Idle
    }
}

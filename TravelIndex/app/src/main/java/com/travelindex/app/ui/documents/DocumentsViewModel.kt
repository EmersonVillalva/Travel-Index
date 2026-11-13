package com.travelindex.app.ui.documents

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.travelindex.app.data.db.AttachmentEntity
import com.travelindex.app.data.repo.AttachmentRepository
import com.travelindex.app.data.repo.TripRepository
import com.travelindex.app.domain.AttachmentCategory
import com.travelindex.app.domain.TripBundle
import com.travelindex.app.ui.files.AddResult
import com.travelindex.app.ui.files.withOwner
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DocumentsState(val loading: Boolean = true, val bundles: List<TripBundle> = emptyList())

/** Pestaña Documentos: documentos de viaje y archivos de TODOS los viajes. */
@HiltViewModel
class DocumentsViewModel @Inject constructor(
    repo: TripRepository,
    private val attachments: AttachmentRepository,
) : ViewModel() {
    val state: StateFlow<DocumentsState> = repo.bundles
        .map { DocumentsState(loading = it == null, bundles = it.orEmpty()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DocumentsState())

    private val _results = Channel<AddResult>(Channel.BUFFERED)
    val results = _results.receiveAsFlow()

    fun add(tripId: Long, uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            var ok = 0
            var failed = 0
            uris.forEach { u -> if (runCatching { attachments.add(tripId, u, null, null) }.isSuccess) ok++ else failed++ }
            _results.send(AddResult(ok, failed))
        }
    }

    fun rename(att: AttachmentEntity, name: String) {
        viewModelScope.launch { runCatching { attachments.update(att.copy(displayName = name)) } }
    }

    fun setCategory(att: AttachmentEntity, category: AttachmentCategory) {
        viewModelScope.launch { runCatching { attachments.update(att.copy(category = category.name)) } }
    }

    fun associate(att: AttachmentEntity, ownerKey: String) {
        viewModelScope.launch { runCatching { attachments.update(att.withOwner(ownerKey)) } }
    }

    fun delete(att: AttachmentEntity) {
        viewModelScope.launch { runCatching { attachments.delete(att) } }
    }
}

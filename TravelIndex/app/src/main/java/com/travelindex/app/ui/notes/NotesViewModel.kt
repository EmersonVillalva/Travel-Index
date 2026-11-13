package com.travelindex.app.ui.notes

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.travelindex.app.data.db.NoteItemEntity
import com.travelindex.app.data.repo.NoteRepository
import com.travelindex.app.data.repo.TripRepository
import com.travelindex.app.domain.NoteKind
import com.travelindex.app.ui.modules.BundleState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

@HiltViewModel
class NotesViewModel @Inject constructor(
    savedState: SavedStateHandle,
    trips: TripRepository,
    private val notes: NoteRepository,
) : ViewModel() {
    val tripId: Long = savedState["tripId"] ?: 0L
    /** true cuando se llega desde el botón "Agregar" del viaje: se abre el diálogo de nueva nota. */
    val startAdding: Boolean = savedState["add"] ?: false

    val state: StateFlow<BundleState> = trips.observeBundle(tripId)
        .map { BundleState(loaded = true, bundle = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BundleState())

    /** [id] = 0 crea una nota nueva. Los datos ya vienen validados por el diálogo. */
    fun save(id: Long, kind: NoteKind, text: String, body: String, due: LocalDate?, time: LocalTime?) {
        viewModelScope.launch {
            runCatching {
                val dueDate = if (kind.usesDue) due else null
                val dueTime = if (dueDate != null) time else null
                if (id == 0L) {
                    notes.add(NoteItemEntity(tripId = tripId, kind = kind.name, text = text.trim(), body = body.trim(),
                        dueDate = dueDate, dueTime = dueTime))
                } else {
                    val old = state.value.bundle?.notes?.firstOrNull { it.id == id } ?: return@runCatching
                    notes.update(old.copy(kind = kind.name, text = text.trim(), body = body.trim(), dueDate = dueDate, dueTime = dueTime))
                }
            }
        }
    }

    fun toggle(item: NoteItemEntity) {
        viewModelScope.launch { runCatching { notes.update(item.copy(done = !item.done)) } }
    }

    fun delete(id: Long) {
        viewModelScope.launch { runCatching { notes.delete(id) } }
    }
}

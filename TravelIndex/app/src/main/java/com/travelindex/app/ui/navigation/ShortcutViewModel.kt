package com.travelindex.app.ui.navigation

import androidx.lifecycle.ViewModel
import com.travelindex.app.data.ShortcutInbox
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

/** Acerca el buzón de accesos directos a la navegación (el NavHost consume el destino pendiente). */
@HiltViewModel
class ShortcutViewModel @Inject constructor(private val inbox: ShortcutInbox) : ViewModel() {
    val pending: StateFlow<String?> = inbox.pending
    fun consume() = inbox.clear()
}

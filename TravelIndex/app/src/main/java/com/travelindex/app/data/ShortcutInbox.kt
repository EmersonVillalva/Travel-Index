package com.travelindex.app.data

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Buzón de los accesos directos del icono (mantener pulsado → «Nuevo viaje», «Buscar»). La actividad deja
 * aquí el destino (el host de travelindex://nuevo) y la navegación lo consume, igual que ImportInbox.
 */
@Singleton
class ShortcutInbox @Inject constructor() {
    private val _pending = MutableStateFlow<String?>(null)
    val pending: StateFlow<String?> = _pending

    fun offer(destination: String) { _pending.value = destination }
    fun clear() { _pending.value = null }
}

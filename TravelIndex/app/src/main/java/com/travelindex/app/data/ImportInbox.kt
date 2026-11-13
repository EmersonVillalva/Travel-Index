package com.travelindex.app.data

import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Buzón entre la actividad (que recibe el archivo de un viaje compartido) y la pantalla que pregunta si importarlo. */
@Singleton
class ImportInbox @Inject constructor() {
    private val _pending = MutableStateFlow<Uri?>(null)
    val pending: StateFlow<Uri?> = _pending.asStateFlow()

    fun offer(uri: Uri) { _pending.value = uri }
    fun clear() { _pending.value = null }
}

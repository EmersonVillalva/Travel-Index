package com.travelindex.app.ui.profile

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.travelindex.app.data.BackupManager
import com.travelindex.app.data.FileStorage
import com.travelindex.app.data.SettingsRepository
import com.travelindex.app.data.repo.TripRepository
import com.travelindex.app.domain.AppSettings
import com.travelindex.app.domain.DateFormatOption
import com.travelindex.app.domain.Money
import com.travelindex.app.domain.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Resultado de una acción de la pantalla de perfil, para mostrar un mensaje. */
enum class ProfileMessage {
    BACKUP_OK, BACKUP_FAILED, RESTORE_FAILED, DATA_DELETED, DELETE_FAILED, PHOTO_FAILED,
}

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val settingsRepo: SettingsRepository,
    private val trips: TripRepository,
    private val files: FileStorage,
    private val backup: BackupManager,
) : ViewModel() {
    val settings: StateFlow<AppSettings> =
        settingsRepo.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    private val _busy = MutableStateFlow(false)
    /** true mientras se exporta o restaura una copia (la interfaz muestra un indicador y bloquea botones). */
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _messages = Channel<ProfileMessage>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    private fun update(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { runCatching { settingsRepo.update(transform) } }
    }

    fun setName(name: String) = update { it.copy(userName = name.trim().take(40)) }
    fun setCurrency(code: String) { if (Money.isValidCurrency(code)) update { it.copy(defaultCurrency = code.trim().uppercase()) } }
    fun setDateFormat(o: DateFormatOption) = update { it.copy(dateFormat = o) }
    fun setTheme(m: ThemeMode) = update { it.copy(themeMode = if (m == ThemeMode.SYSTEM) ThemeMode.DARK else m) }
    fun setNotifications(on: Boolean) = update { it.copy(notificationsEnabled = on) }
    fun setBlockScreenshots(on: Boolean) = update { it.copy(blockScreenshots = on) }

    fun setPhoto(uri: Uri) {
        viewModelScope.launch {
            try {
                val old = settingsRepo.settings.first().photoPath
                val name = files.importProfilePhoto(uri)
                settingsRepo.update { it.copy(photoPath = name) }
                old?.let { files.deleteProfilePhoto(it) }
            } catch (t: Throwable) {
                _messages.send(ProfileMessage.PHOTO_FAILED)
            }
        }
    }

    fun removePhoto() {
        viewModelScope.launch {
            val old = settingsRepo.settings.first().photoPath
            runCatching { settingsRepo.update { it.copy(photoPath = null) } }
            old?.let { runCatching { files.deleteProfilePhoto(it) } }
        }
    }

    fun exportBackup(target: Uri) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            val ok = backup.export(target).isSuccess
            _busy.value = false
            _messages.send(if (ok) ProfileMessage.BACKUP_OK else ProfileMessage.BACKUP_FAILED)
        }
    }

    /** Si la restauración funciona, la app se reinicia (la base de datos queda cerrada). */
    fun restoreBackup(source: Uri) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            val ok = backup.import(source).isSuccess
            // Con éxito la base de datos restaurada solo se abre tras reiniciar. Si falló después de cerrarla,
            // también hay que reiniciar para volver a abrir los datos que había.
            if (ok || backup.databaseClosed) { backup.restartApp(); return@launch }
            _busy.value = false
            _messages.send(ProfileMessage.RESTORE_FAILED)
        }
    }

    /** Borra todos los viajes y sus archivos. Los ajustes y el perfil se conservan. */
    fun deleteAllTrips() {
        viewModelScope.launch {
            runCatching { trips.deleteEverything() }
                .onSuccess { _messages.send(ProfileMessage.DATA_DELETED) }
                .onFailure { _messages.send(ProfileMessage.DELETE_FAILED) }
        }
    }
}

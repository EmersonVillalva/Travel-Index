package com.travelindex.app.ui.profile

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.travelindex.app.data.FileStorage
import com.travelindex.app.data.SettingsRepository
import com.travelindex.app.domain.AppSettings
import com.travelindex.app.domain.DateFormatOption
import com.travelindex.app.domain.Money
import com.travelindex.app.domain.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Resultado de una acción de la pantalla de perfil, para mostrar un mensaje. */
enum class ProfileMessage {
    PHOTO_FAILED,
}

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val settingsRepo: SettingsRepository,
    private val files: FileStorage,
) : ViewModel() {
    val settings: StateFlow<AppSettings> =
        settingsRepo.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

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
}

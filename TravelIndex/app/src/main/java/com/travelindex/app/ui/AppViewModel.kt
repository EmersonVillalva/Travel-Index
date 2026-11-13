package com.travelindex.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.travelindex.app.data.SettingsRepository
import com.travelindex.app.domain.AppSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Ajustes globales para la actividad (tema, splash, bloqueo de capturas) y la interfaz (formatos). `null` = cargando. */
@HiltViewModel
class AppViewModel @Inject constructor(settingsRepo: SettingsRepository) : ViewModel() {
    val settings: StateFlow<AppSettings?> =
        settingsRepo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)
}

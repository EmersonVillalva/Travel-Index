package com.travelindex.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.travelindex.app.data.SettingsRepository
import com.travelindex.app.data.repo.TripRepository
import com.travelindex.app.domain.HomeBuilder
import com.travelindex.app.domain.HomeSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.ZonedDateTime
import javax.inject.Inject

data class HomeState(
    val loading: Boolean = true,
    val userName: String = "",
    val hasTrips: Boolean = false,
    val summary: HomeSummary? = null,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    repo: TripRepository,
    private val settings: SettingsRepository,
) : ViewModel() {
    val state: StateFlow<HomeState> = combine(repo.bundles, settings.settings) { bundles, s ->
        if (bundles == null) HomeState(loading = true, userName = s.userName)
        else HomeState(
            loading = false, userName = s.userName, hasTrips = bundles.isNotEmpty(),
            summary = HomeBuilder.build(bundles, ZonedDateTime.now(), LocalDate.now()),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())
}

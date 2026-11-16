package com.travelindex.app.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.travelindex.app.data.repo.TripRepository
import com.travelindex.app.domain.SearchHit
import com.travelindex.app.domain.TripSearch
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** Buscador global: cruza el texto con todos los viajes ya cargados (la lógica vive en TripSearch, probada). */
@HiltViewModel
class SearchViewModel @Inject constructor(trips: TripRepository) : ViewModel() {
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    val hits: StateFlow<List<SearchHit>> = combine(trips.bundles, _query) { bundles, q ->
        if (bundles == null) emptyList() else TripSearch.search(q, bundles)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setQuery(q: String) { _query.value = q }
}

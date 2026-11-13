package com.travelindex.app.ui.trips

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.travelindex.app.data.ImportInbox
import com.travelindex.app.data.repo.TripRepository
import com.travelindex.app.domain.CostBreakdown
import com.travelindex.app.domain.Money
import com.travelindex.app.domain.TripStatus
import com.travelindex.app.domain.TripFilter
import com.travelindex.app.domain.TripListBuilder
import com.travelindex.app.domain.TripSection
import com.travelindex.app.domain.TripSort
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject

data class TripsState(
    val loading: Boolean = true,
    val total: Int = 0,
    val sections: List<TripSection> = emptyList(),
    val counts: Map<TripFilter, Int> = emptyMap(),
    val query: String = "",
    val filter: TripFilter = TripFilter.ALL,
    val sort: TripSort = TripSort.SMART,
    /** Viajes pasados: lo pagado en total, por moneda ("1.234 € · 100 $"). */
    val paid: Map<Long, String> = emptyMap(),
)

@HiltViewModel
class TripsViewModel @Inject constructor(repo: TripRepository, private val inbox: ImportInbox) : ViewModel() {
    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow(TripFilter.ALL)
    private val sort = MutableStateFlow(TripSort.SMART)

    val state: StateFlow<TripsState> = combine(repo.bundles, query, filter, sort) { bundles, q, f, s ->
        if (bundles == null) TripsState(loading = true, query = q, filter = f, sort = s)
        else {
            val trips = bundles.map { it.trip }
            val today = LocalDate.now()
            TripsState(
                loading = false, total = trips.size, sections = TripListBuilder.build(trips, q, f, s, today),
                counts = TripListBuilder.counts(trips, today), query = q, filter = f, sort = s,
                paid = bundles.filter { it.status(today) == TripStatus.COMPLETED }.mapNotNull { b ->
                    val totals = CostBreakdown.totals(CostBreakdown.of(b))
                    if (totals.isEmpty()) null else b.trip.id to totals.entries.joinToString(" · ") { (cur, minor) -> Money.format(minor, cur) }
                }.toMap(),
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TripsState())

    fun setQuery(q: String) { query.value = q.take(80) }
    fun setFilter(f: TripFilter) { filter.value = f }
    fun setSort(s: TripSort) { sort.value = s }

    /** Un archivo de viaje elegido a mano: lo recoge la ventana de importación. */
    fun importFile(uri: android.net.Uri) = inbox.offer(uri)
}

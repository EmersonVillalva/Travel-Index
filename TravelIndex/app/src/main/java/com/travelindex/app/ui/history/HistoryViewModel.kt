package com.travelindex.app.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.travelindex.app.data.repo.TripRepository
import com.travelindex.app.domain.Money
import com.travelindex.app.domain.TripHistory
import com.travelindex.app.domain.TripHistoryBuilder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject

data class HistoryState(
    val loading: Boolean = true,
    val history: TripHistory? = null,
    /** Lo pagado en cada viaje terminado, ya formateado por moneda ("1.234 € · 100 $"). */
    val paid: Map<Long, String> = emptyMap(),
    /** Lo pagado en total, ya formateado por moneda; vacío si ningún viaje tiene precios. */
    val paidTotal: String = "",
)

@HiltViewModel
class HistoryViewModel @Inject constructor(repo: TripRepository) : ViewModel() {
    val state: StateFlow<HistoryState> = repo.bundles.map { bundles ->
        if (bundles == null) HistoryState()
        else {
            val history = TripHistoryBuilder.build(bundles, LocalDate.now())
            fun text(totals: Map<String, Long>) = totals.entries.joinToString(" · ") { (cur, minor) -> Money.format(minor, cur) }
            HistoryState(
                loading = false, history = history,
                paid = history.years.flatMap { it.trips }.mapNotNull { b ->
                    val totals = TripHistoryBuilder.paidOf(b)
                    if (totals.isEmpty()) null else b.trip.id to text(totals)
                }.toMap(),
                paidTotal = text(history.stats.paid),
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryState())
}

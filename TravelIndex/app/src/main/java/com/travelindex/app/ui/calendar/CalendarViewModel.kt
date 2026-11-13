package com.travelindex.app.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.travelindex.app.data.repo.TripRepository
import com.travelindex.app.data.db.TripEntity
import com.travelindex.app.domain.CalendarBuilder
import com.travelindex.app.domain.CalendarCell
import com.travelindex.app.domain.DayEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.WeekFields
import java.util.Locale
import javax.inject.Inject

data class CalendarState(
    val loading: Boolean = true,
    val month: YearMonth = YearMonth.now(),
    val firstDay: DayOfWeek = DayOfWeek.MONDAY,
    val weeks: List<List<CalendarCell>> = emptyList(),
    val selected: LocalDate = LocalDate.now(),
    val entries: List<DayEntry> = emptyList(),
    val trips: List<TripEntity> = emptyList(),
    val hasAnyTrip: Boolean = false,
)

@HiltViewModel
class CalendarViewModel @Inject constructor(repo: TripRepository) : ViewModel() {
    private val month = MutableStateFlow(YearMonth.now())
    private val selected = MutableStateFlow(LocalDate.now())
    private val firstDay: DayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek

    val state: StateFlow<CalendarState> = combine(repo.bundles, month, selected) { bundles, m, sel ->
        if (bundles == null) CalendarState(loading = true, month = m, selected = sel, firstDay = firstDay)
        else CalendarState(
            loading = false, month = m, firstDay = firstDay, selected = sel,
            weeks = CalendarBuilder.month(m, LocalDate.now(), bundles, firstDay),
            entries = CalendarBuilder.eventsOn(sel, bundles),
            trips = CalendarBuilder.tripsOn(sel, bundles),
            hasAnyTrip = bundles.isNotEmpty(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CalendarState(firstDay = firstDay))

    fun previousMonth() { month.value = month.value.minusMonths(1) }
    fun nextMonth() { month.value = month.value.plusMonths(1) }

    fun goToToday() {
        val today = LocalDate.now()
        month.value = YearMonth.from(today)
        selected.value = today
    }

    /** Al pulsar un día de otro mes, el calendario salta a ese mes. */
    fun select(date: LocalDate) {
        selected.value = date
        val ym = YearMonth.from(date)
        if (ym != month.value) month.value = ym
    }
}

package com.travelindex.app.domain

import com.travelindex.app.data.db.TripEntity
import java.text.Normalizer
import java.time.LocalDate
import java.util.Locale

/** Filtro de la lista de viajes. UPCOMING incluye los que están en curso. */
enum class TripFilter { ALL, UPCOMING, PAST, CANCELLED }

/** SMART: próximos por fecha ascendente y pasados del más reciente al más antiguo. */
enum class TripSort { SMART, DATE_ASC, DATE_DESC, NAME }

enum class TripGroup { ONGOING, UPCOMING, PAST, CANCELLED }

data class TripSection(val group: TripGroup, val trips: List<TripEntity>)

object TripListBuilder {
    fun groupOf(t: TripEntity, today: LocalDate): TripGroup = when (tripStatus(t.startDate, t.endDate, t.cancelled, today)) {
        TripStatus.ONGOING -> TripGroup.ONGOING
        TripStatus.UPCOMING -> TripGroup.UPCOMING
        TripStatus.COMPLETED -> TripGroup.PAST
        TripStatus.CANCELLED -> TripGroup.CANCELLED
    }

    /** Minúsculas y sin acentos, para que "Peru" encuentre "Perú". */
    fun normalize(s: String): String =
        Normalizer.normalize(s.trim().lowercase(Locale.ROOT), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")

    fun matches(t: TripEntity, query: String): Boolean {
        val q = normalize(query)
        if (q.isEmpty()) return true
        return listOf(t.name, t.city, t.country).any { normalize(it).contains(q) }
    }

    fun counts(trips: List<TripEntity>, today: LocalDate): Map<TripFilter, Int> {
        val groups = trips.map { groupOf(it, today) }
        return mapOf(
            TripFilter.ALL to trips.size,
            TripFilter.UPCOMING to groups.count { it == TripGroup.UPCOMING || it == TripGroup.ONGOING },
            TripFilter.PAST to groups.count { it == TripGroup.PAST },
            TripFilter.CANCELLED to groups.count { it == TripGroup.CANCELLED },
        )
    }

    fun build(trips: List<TripEntity>, query: String, filter: TripFilter, sort: TripSort, today: LocalDate): List<TripSection> {
        val visible = trips.filter { matches(it, query) }.filter {
            val g = groupOf(it, today)
            when (filter) {
                TripFilter.ALL -> true
                TripFilter.UPCOMING -> g == TripGroup.UPCOMING || g == TripGroup.ONGOING
                TripFilter.PAST -> g == TripGroup.PAST
                TripFilter.CANCELLED -> g == TripGroup.CANCELLED
            }
        }
        return TripGroup.entries.mapNotNull { group ->
            val list = visible.filter { groupOf(it, today) == group }
            if (list.isEmpty()) null else TripSection(group, sorted(list, group, sort))
        }
    }

    private fun sorted(list: List<TripEntity>, group: TripGroup, sort: TripSort): List<TripEntity> = when (sort) {
        TripSort.SMART ->
            if (group == TripGroup.PAST) list.sortedWith(compareByDescending<TripEntity> { it.endDate }.thenBy { it.id })
            else list.sortedWith(compareBy<TripEntity> { it.startDate }.thenBy { it.id })
        TripSort.DATE_ASC -> list.sortedWith(compareBy<TripEntity> { it.startDate }.thenBy { it.id })
        TripSort.DATE_DESC -> list.sortedWith(compareByDescending<TripEntity> { it.startDate }.thenBy { it.id })
        TripSort.NAME -> list.sortedWith(compareBy<TripEntity> { normalize(it.name) }.thenBy { it.id })
    }
}

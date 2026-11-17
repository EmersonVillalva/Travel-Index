package com.travelindex.app.data.repo

import com.travelindex.app.data.FileStorage
import com.travelindex.app.data.db.ActivityDao
import com.travelindex.app.data.db.AttachmentDao
import com.travelindex.app.data.db.DestinationDao
import com.travelindex.app.data.db.ExpenseDao
import com.travelindex.app.data.db.FlightDao
import com.travelindex.app.data.db.LodgingDao
import com.travelindex.app.data.db.NoteItemDao
import com.travelindex.app.data.db.RestaurantDao
import com.travelindex.app.data.db.TransportDao
import com.travelindex.app.data.db.TravelDocDao
import com.travelindex.app.data.db.TripDao
import com.travelindex.app.data.db.TripEntity
import com.travelindex.app.domain.TripBundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TripRepository @Inject constructor(
    private val trips: TripDao,
    destinations: DestinationDao,
    flights: FlightDao,
    lodgings: LodgingDao,
    activities: ActivityDao,
    transports: TransportDao,
    restaurants: RestaurantDao,
    docs: TravelDocDao,
    notes: NoteItemDao,
    expenses: ExpenseDao,
    private val attachments: AttachmentDao,
    private val files: FileStorage,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private data class Part1(
        val trips: List<TripEntity>,
        val destinations: List<com.travelindex.app.data.db.DestinationEntity>,
        val flights: List<com.travelindex.app.data.db.FlightEntity>,
        val lodgings: List<com.travelindex.app.data.db.LodgingEntity>,
        val activities: List<com.travelindex.app.data.db.ActivityEntity>,
    )

    private data class Part2(
        val transports: List<com.travelindex.app.data.db.TransportEntity>,
        val restaurants: List<com.travelindex.app.data.db.RestaurantEntity>,
        val docs: List<com.travelindex.app.data.db.TravelDocEntity>,
        val notes: List<com.travelindex.app.data.db.NoteItemEntity>,
        val expenses: List<com.travelindex.app.data.db.ExpenseEntity>,
    )

    private val part1: Flow<Part1> = combine(
        trips.observeAll(), destinations.observeAll(), flights.observeAll(), lodgings.observeAll(), activities.observeAll(),
    ) { t, d, f, l, a -> Part1(t, d, f, l, a) }

    private val part2: Flow<Part2> = combine(
        transports.observeAll(), restaurants.observeAll(), docs.observeAll(), notes.observeAll(), expenses.observeAll(),
    ) { tr, r, d, n, e -> Part2(tr, r, d, n, e) }

    /** Todos los viajes con su contenido. `null` = todavía cargando. Se comparte entre pantallas. */
    val bundles: StateFlow<List<TripBundle>?> = combine(part1, part2, attachments.observeAll()) { a, b, att ->
        val dest = a.destinations.groupBy { it.tripId }
        val fl = a.flights.groupBy { it.tripId }
        val lo = a.lodgings.groupBy { it.tripId }
        val ac = a.activities.groupBy { it.tripId }
        val tr = b.transports.groupBy { it.tripId }
        val re = b.restaurants.groupBy { it.tripId }
        val dc = b.docs.groupBy { it.tripId }
        val nt = b.notes.groupBy { it.tripId }
        val ex = b.expenses.groupBy { it.tripId }
        val at = att.groupBy { it.tripId }
        a.trips.map { t ->
            TripBundle(
                trip = t, destinations = dest[t.id].orEmpty(), flights = fl[t.id].orEmpty(),
                lodgings = lo[t.id].orEmpty(), activities = ac[t.id].orEmpty(), transports = tr[t.id].orEmpty(),
                restaurants = re[t.id].orEmpty(), docs = dc[t.id].orEmpty(), notes = nt[t.id].orEmpty(),
                expenses = ex[t.id].orEmpty(), attachments = at[t.id].orEmpty(),
            )
        }
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), null)

    fun observeBundle(tripId: Long): Flow<TripBundle?> =
        bundles.filterNotNull().map { list -> list.firstOrNull { it.trip.id == tripId } }

    suspend fun getTrip(id: Long): TripEntity? = trips.getById(id)
    suspend fun createTrip(trip: TripEntity): Long = trips.insert(trip)
    suspend fun updateTrip(trip: TripEntity) = trips.update(trip)

    suspend fun setCancelled(tripId: Long, cancelled: Boolean) {
        val t = trips.getById(tripId) ?: return
        trips.update(t.copy(cancelled = cancelled))
    }

    /** Borra el viaje (las filas hijas caen por CASCADE) y después sus archivos físicos. */
    suspend fun deleteTrip(tripId: Long) {
        val trip = trips.getById(tripId) ?: return
        val paths = attachments.pathsForTrip(tripId)
        trips.deleteById(tripId)
        paths.forEach { files.delete(it) }
        trip.coverPath?.let { files.delete(it) }
    }
}

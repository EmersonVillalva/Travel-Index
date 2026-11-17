package com.travelindex.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TripDao {
    @Query("SELECT * FROM trips ORDER BY startDate ASC")
    fun observeAll(): Flow<List<TripEntity>>

    @Query("SELECT * FROM trips WHERE id = :id")
    fun observe(id: Long): Flow<TripEntity?>

    @Query("SELECT * FROM trips WHERE id = :id")
    suspend fun getById(id: Long): TripEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(trip: TripEntity): Long

    @Update
    suspend fun update(trip: TripEntity)

    @Query("DELETE FROM trips WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface FlightDao {
    @Query("SELECT * FROM flights ORDER BY departureEpochMs ASC")
    fun observeAll(): Flow<List<FlightEntity>>
    @Query("SELECT * FROM flights WHERE tripId = :tripId ORDER BY departureEpochMs ASC")
    fun observeForTrip(tripId: Long): Flow<List<FlightEntity>>
    @Insert suspend fun insert(item: FlightEntity): Long
    @Update suspend fun update(item: FlightEntity)
    @Delete suspend fun delete(item: FlightEntity)
    @Query("DELETE FROM flights WHERE id = :id") suspend fun deleteById(id: Long)
}

@Dao
interface LodgingDao {
    @Query("SELECT * FROM lodgings ORDER BY checkInDate ASC")
    fun observeAll(): Flow<List<LodgingEntity>>
    @Query("SELECT * FROM lodgings WHERE tripId = :tripId ORDER BY checkInDate ASC")
    fun observeForTrip(tripId: Long): Flow<List<LodgingEntity>>
    @Insert suspend fun insert(item: LodgingEntity): Long
    @Update suspend fun update(item: LodgingEntity)
    @Delete suspend fun delete(item: LodgingEntity)
    @Query("DELETE FROM lodgings WHERE id = :id") suspend fun deleteById(id: Long)
}

@Dao
interface ActivityDao {
    @Query("SELECT * FROM activities ORDER BY date ASC, startTime ASC")
    fun observeAll(): Flow<List<ActivityEntity>>
    @Query("SELECT * FROM activities WHERE tripId = :tripId ORDER BY date ASC, startTime ASC")
    fun observeForTrip(tripId: Long): Flow<List<ActivityEntity>>
    @Insert suspend fun insert(item: ActivityEntity): Long
    @Update suspend fun update(item: ActivityEntity)
    @Delete suspend fun delete(item: ActivityEntity)
    @Query("DELETE FROM activities WHERE id = :id") suspend fun deleteById(id: Long)
}

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses ORDER BY date DESC, id DESC")
    fun observeAll(): Flow<List<ExpenseEntity>>
    @Query("SELECT * FROM expenses WHERE tripId = :tripId ORDER BY date DESC, id DESC")
    fun observeForTrip(tripId: Long): Flow<List<ExpenseEntity>>
    @Insert suspend fun insert(item: ExpenseEntity): Long
    @Update suspend fun update(item: ExpenseEntity)
    @Delete suspend fun delete(item: ExpenseEntity)
    @Query("DELETE FROM expenses WHERE id = :id") suspend fun deleteById(id: Long)
}

@Dao
interface AttachmentDao {
    @Query("SELECT * FROM attachments ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<AttachmentEntity>>
    @Query("SELECT * FROM attachments WHERE tripId = :tripId ORDER BY createdAt DESC")
    fun observeForTrip(tripId: Long): Flow<List<AttachmentEntity>>
    @Query("SELECT relativePath FROM attachments WHERE tripId = :tripId")
    suspend fun pathsForTrip(tripId: Long): List<String>
    @Query("SELECT * FROM attachments WHERE ownerType = :ownerType AND ownerId = :ownerId")
    suspend fun forOwner(ownerType: String, ownerId: Long): List<AttachmentEntity>
    @Query("SELECT * FROM attachments WHERE id = :id")
    suspend fun getById(id: Long): AttachmentEntity?
    @Insert suspend fun insert(item: AttachmentEntity): Long
    @Update suspend fun update(item: AttachmentEntity)
    @Delete suspend fun delete(item: AttachmentEntity)
    @Query("DELETE FROM attachments WHERE ownerType = :ownerType AND ownerId = :ownerId")
    suspend fun deleteForOwner(ownerType: String, ownerId: Long)
}

@Dao
interface DestinationDao {
    @Query("SELECT * FROM trip_destinations ORDER BY position ASC, startDate ASC")
    fun observeAll(): Flow<List<DestinationEntity>>
    @Query("SELECT * FROM trip_destinations WHERE tripId = :tripId ORDER BY position ASC, startDate ASC")
    fun observeForTrip(tripId: Long): Flow<List<DestinationEntity>>
    @Insert suspend fun insert(item: DestinationEntity): Long
    @Update suspend fun update(item: DestinationEntity)
    @Delete suspend fun delete(item: DestinationEntity)
    @Query("DELETE FROM trip_destinations WHERE id = :id") suspend fun deleteById(id: Long)
}

@Dao
interface TransportDao {
    @Query("SELECT * FROM transports ORDER BY startDate ASC, startTime ASC")
    fun observeAll(): Flow<List<TransportEntity>>
    @Insert suspend fun insert(item: TransportEntity): Long
    @Update suspend fun update(item: TransportEntity)
    @Query("DELETE FROM transports WHERE id = :id") suspend fun deleteById(id: Long)
}

@Dao
interface RestaurantDao {
    @Query("SELECT * FROM restaurants ORDER BY date ASC, time ASC")
    fun observeAll(): Flow<List<RestaurantEntity>>
    @Insert suspend fun insert(item: RestaurantEntity): Long
    @Update suspend fun update(item: RestaurantEntity)
    @Query("DELETE FROM restaurants WHERE id = :id") suspend fun deleteById(id: Long)
}

@Dao
interface TravelDocDao {
    @Query("SELECT * FROM travel_docs ORDER BY expiryDate IS NULL, expiryDate ASC, id ASC")
    fun observeAll(): Flow<List<TravelDocEntity>>
    @Insert suspend fun insert(item: TravelDocEntity): Long
    @Update suspend fun update(item: TravelDocEntity)
    @Query("DELETE FROM travel_docs WHERE id = :id") suspend fun deleteById(id: Long)
}

@Dao
interface NoteItemDao {
    @Query("SELECT * FROM note_items ORDER BY done ASC, createdAt DESC")
    fun observeAll(): Flow<List<NoteItemEntity>>
    @Insert suspend fun insert(item: NoteItemEntity): Long
    @Update suspend fun update(item: NoteItemEntity)
    @Query("DELETE FROM note_items WHERE id = :id") suspend fun deleteById(id: Long)
}

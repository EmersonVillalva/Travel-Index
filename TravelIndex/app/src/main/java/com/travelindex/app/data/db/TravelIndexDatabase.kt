package com.travelindex.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        TripEntity::class, DestinationEntity::class, FlightEntity::class, LodgingEntity::class,
        ActivityEntity::class, ExpenseEntity::class, AttachmentEntity::class,
        TransportEntity::class, RestaurantEntity::class, TravelDocEntity::class, NoteItemEntity::class,
    ],
    version = TravelIndexDatabase.VERSION,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class TravelIndexDatabase : RoomDatabase() {
    abstract fun tripDao(): TripDao
    abstract fun destinationDao(): DestinationDao
    abstract fun flightDao(): FlightDao
    abstract fun lodgingDao(): LodgingDao
    abstract fun activityDao(): ActivityDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun attachmentDao(): AttachmentDao
    abstract fun transportDao(): TransportDao
    abstract fun restaurantDao(): RestaurantDao
    abstract fun travelDocDao(): TravelDocDao
    abstract fun noteItemDao(): NoteItemDao

    companion object {
        const val NAME = "travelindex.db"
        const val VERSION = 3
    }
}

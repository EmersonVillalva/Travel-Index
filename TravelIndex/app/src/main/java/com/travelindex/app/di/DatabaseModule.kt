package com.travelindex.app.di

import android.content.Context
import androidx.room.Room
import com.travelindex.app.data.db.MIGRATION_1_2
import com.travelindex.app.data.db.MIGRATION_2_3
import com.travelindex.app.data.db.MIGRATION_3_4
import com.travelindex.app.data.db.TravelIndexDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    // Sin fallbackToDestructiveMigration: una migración que falte debe fallar en tests, no borrar datos del usuario.
    @Provides @Singleton
    fun database(@ApplicationContext ctx: Context): TravelIndexDatabase =
        Room.databaseBuilder(ctx, TravelIndexDatabase::class.java, TravelIndexDatabase.NAME)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
            .build()

    @Provides fun tripDao(db: TravelIndexDatabase) = db.tripDao()
    @Provides fun destinationDao(db: TravelIndexDatabase) = db.destinationDao()
    @Provides fun flightDao(db: TravelIndexDatabase) = db.flightDao()
    @Provides fun lodgingDao(db: TravelIndexDatabase) = db.lodgingDao()
    @Provides fun activityDao(db: TravelIndexDatabase) = db.activityDao()
    @Provides fun expenseDao(db: TravelIndexDatabase) = db.expenseDao()
    @Provides fun attachmentDao(db: TravelIndexDatabase) = db.attachmentDao()
    @Provides fun transportDao(db: TravelIndexDatabase) = db.transportDao()
    @Provides fun restaurantDao(db: TravelIndexDatabase) = db.restaurantDao()
    @Provides fun travelDocDao(db: TravelIndexDatabase) = db.travelDocDao()
    @Provides fun noteItemDao(db: TravelIndexDatabase) = db.noteItemDao()
}

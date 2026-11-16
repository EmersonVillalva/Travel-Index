package com.travelindex.app.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v1 -> v2: añade transportes, restaurantes, documentos de viaje y notas/listas.
 * Solo CREA tablas nuevas; no toca los datos existentes. El SQL replica exactamente lo que Room espera
 * (tools/check_migration.py lo compara con las entidades).
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `transports` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`tripId` INTEGER NOT NULL, `type` TEXT NOT NULL, `company` TEXT NOT NULL, `model` TEXT NOT NULL, " +
                "`plate` TEXT NOT NULL, `startDate` INTEGER NOT NULL, `startTime` INTEGER, `endDate` INTEGER, " +
                "`endTime` INTEGER, `timeZoneId` TEXT NOT NULL, `originPlace` TEXT NOT NULL, " +
                "`destinationPlace` TEXT NOT NULL, `address` TEXT NOT NULL, `reservationNumber` TEXT NOT NULL, " +
                "`priceMinor` INTEGER, `currency` TEXT NOT NULL, `insurance` TEXT NOT NULL, `mileage` TEXT NOT NULL, " +
                "`fuel` TEXT NOT NULL, `conditions` TEXT NOT NULL, `phone` TEXT NOT NULL, `notes` TEXT NOT NULL, " +
                "FOREIGN KEY(`tripId`) REFERENCES `trips`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_transports_tripId` ON `transports` (`tripId`)")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `restaurants` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`tripId` INTEGER NOT NULL, `name` TEXT NOT NULL, `address` TEXT NOT NULL, `date` INTEGER NOT NULL, " +
                "`time` INTEGER, `timeZoneId` TEXT NOT NULL, `people` INTEGER NOT NULL, " +
                "`reservationNumber` TEXT NOT NULL, `priceMinor` INTEGER, `currency` TEXT NOT NULL, " +
                "`url` TEXT NOT NULL, `phone` TEXT NOT NULL, `notes` TEXT NOT NULL, " +
                "FOREIGN KEY(`tripId`) REFERENCES `trips`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_restaurants_tripId` ON `restaurants` (`tripId`)")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `travel_docs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`tripId` INTEGER NOT NULL, `docType` TEXT NOT NULL, `title` TEXT NOT NULL, " +
                "`issuingCountry` TEXT NOT NULL, `issueDate` INTEGER, `expiryDate` INTEGER, " +
                "`reference` TEXT NOT NULL, `approvalDate` INTEGER, `status` TEXT NOT NULL, `notes` TEXT NOT NULL, " +
                "FOREIGN KEY(`tripId`) REFERENCES `trips`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_travel_docs_tripId` ON `travel_docs` (`tripId`)")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `note_items` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`tripId` INTEGER NOT NULL, `kind` TEXT NOT NULL, `text` TEXT NOT NULL, `body` TEXT NOT NULL, " +
                "`done` INTEGER NOT NULL, `dueDate` INTEGER, `dueTime` INTEGER, `createdAt` INTEGER NOT NULL, " +
                "FOREIGN KEY(`tripId`) REFERENCES `trips`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_note_items_tripId` ON `note_items` (`tripId`)")
    }
}

/**
 * v2 -> v3: el viaje guarda sus medios de transporte (avión, barco, tren, autobús, coche).
 * Solo AÑADE una columna con valor por defecto vacío; los viajes que ya existen quedan igual.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `trips` ADD COLUMN `travelModes` TEXT NOT NULL DEFAULT ''")
    }
}

/**
 * v3 -> v4: la actividad guarda su duración (opcional) en vez de la hora de fin.
 * Solo AÑADE una columna que admite vacío; las actividades que ya existen quedan igual y siguen mostrando su hora de fin.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `activities` ADD COLUMN `durationMinutes` INTEGER")
    }
}

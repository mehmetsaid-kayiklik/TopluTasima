package com.example.toplutasima.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.toplutasima.auth.CurrentUserProvider
import com.example.toplutasima.data.local.dao.DriveSyncOperationDao
import com.example.toplutasima.data.local.dao.DriveSyncMetadataDao
import com.example.toplutasima.data.local.dao.DriveSyncReceiptDao
import com.example.toplutasima.data.local.dao.DriveFieldProvenanceDao
import com.example.toplutasima.data.local.dao.DriveTripDao
import com.example.toplutasima.data.local.dao.DriveVehicleDao
import com.example.toplutasima.data.local.dao.DriveVehicleAssignmentDao
import com.example.toplutasima.data.local.dao.DriveAssignmentOperationDao
import com.example.toplutasima.data.local.dao.DriveAssignmentSyncMetadataDao
import com.example.toplutasima.data.local.dao.DriveAssignmentSyncReceiptDao
import com.example.toplutasima.data.local.dao.ProfileDao
import com.example.toplutasima.data.local.dao.TripDao
import com.example.toplutasima.data.local.dao.TripProfileLinkDao
import com.example.toplutasima.data.local.entity.DriveSyncOperationEntity
import com.example.toplutasima.data.local.entity.DriveSyncMetadataEntity
import com.example.toplutasima.data.local.entity.DriveSyncReceiptEntity
import com.example.toplutasima.data.local.entity.DriveFieldProvenanceEntity
import com.example.toplutasima.data.local.entity.DriveTripEntity
import com.example.toplutasima.data.local.entity.DriveVehicleEntity
import com.example.toplutasima.data.local.entity.DriveVehicleAssignmentEntity
import com.example.toplutasima.data.local.entity.DriveAssignmentOperationEntity
import com.example.toplutasima.data.local.entity.DriveAssignmentSyncMetadataEntity
import com.example.toplutasima.data.local.entity.DriveAssignmentSyncReceiptEntity
import com.example.toplutasima.data.local.entity.ProfileEntity
import com.example.toplutasima.data.local.entity.TripEntity
import com.example.toplutasima.data.local.entity.TripProfileLinkEntity

@Database(
    entities = [
        TripEntity::class,
        ProfileEntity::class,
        TripProfileLinkEntity::class,
        DriveVehicleEntity::class,
        DriveTripEntity::class,
        DriveSyncOperationEntity::class,
        DriveSyncMetadataEntity::class,
        DriveSyncReceiptEntity::class,
        DriveFieldProvenanceEntity::class,
        DriveVehicleAssignmentEntity::class,
        DriveAssignmentOperationEntity::class,
        DriveAssignmentSyncMetadataEntity::class,
        DriveAssignmentSyncReceiptEntity::class
    ],
    version = 11,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun tripDao(): TripDao
    abstract fun profileDao(): ProfileDao
    abstract fun tripProfileLinkDao(): TripProfileLinkDao
    abstract fun driveVehicleDao(): DriveVehicleDao
    abstract fun driveTripDao(): DriveTripDao
    abstract fun driveSyncOperationDao(): DriveSyncOperationDao
    abstract fun driveSyncMetadataDao(): DriveSyncMetadataDao
    abstract fun driveSyncReceiptDao(): DriveSyncReceiptDao
    abstract fun driveFieldProvenanceDao(): DriveFieldProvenanceDao
    abstract fun driveVehicleAssignmentDao(): DriveVehicleAssignmentDao
    abstract fun driveAssignmentOperationDao(): DriveAssignmentOperationDao
    abstract fun driveAssignmentSyncMetadataDao(): DriveAssignmentSyncMetadataDao
    abstract fun driveAssignmentSyncReceiptDao(): DriveAssignmentSyncReceiptDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE trips ADD COLUMN seatmateUuid TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `profiles` (
                        `id` TEXT NOT NULL, 
                        `displayName` TEXT NOT NULL, 
                        `nameKind` TEXT NOT NULL,
                        `memoryNote` TEXT, 
                        `birthHint` TEXT,
                        `infoSource` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL, 
                        `updatedAt` INTEGER NOT NULL, 
                        `archived` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
                
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `trip_profile_links` (
                        `id` TEXT NOT NULL, 
                        `tripStableKey` TEXT NOT NULL, 
                        `profileId` TEXT NOT NULL, 
                        `seatmateNote` TEXT, 
                        `createdAt` INTEGER NOT NULL, 
                        `updatedAt` INTEGER NOT NULL, 
                        PRIMARY KEY(`id`), 
                        FOREIGN KEY(`profileId`) REFERENCES `profiles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_trip_profile_links_tripStableKey` ON `trip_profile_links` (`tripStableKey`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_trip_profile_links_profileId` ON `trip_profile_links` (`profileId`)")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE profiles ADD COLUMN sharedWithTransit INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `profiles` RENAME TO `profiles_old`")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `profiles` (
                        `id` TEXT NOT NULL,
                        `displayName` TEXT NOT NULL,
                        `memoryNote` TEXT,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        `archived` INTEGER NOT NULL,
                        `sharedWithTransit` INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())

                db.execSQL("""
                    INSERT INTO `profiles` (
                        `id`,
                        `displayName`,
                        `memoryNote`,
                        `createdAt`,
                        `updatedAt`,
                        `archived`,
                        `sharedWithTransit`
                    )
                    SELECT
                        `id`,
                        `displayName`,
                        `memoryNote`,
                        `createdAt`,
                        `updatedAt`,
                        `archived`,
                        `sharedWithTransit`
                    FROM `profiles_old`
                """.trimIndent())

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `trip_profile_links_new` (
                        `id` TEXT NOT NULL,
                        `tripStableKey` TEXT NOT NULL,
                        `profileId` TEXT NOT NULL,
                        `seatmateNote` TEXT,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`profileId`) REFERENCES `profiles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())

                db.execSQL("""
                    INSERT INTO `trip_profile_links_new` (
                        `id`,
                        `tripStableKey`,
                        `profileId`,
                        `seatmateNote`,
                        `createdAt`,
                        `updatedAt`
                    )
                    SELECT
                        `id`,
                        `tripStableKey`,
                        `profileId`,
                        `seatmateNote`,
                        `createdAt`,
                        `updatedAt`
                    FROM `trip_profile_links`
                """.trimIndent())

                db.execSQL("DROP TABLE `trip_profile_links`")
                db.execSQL("DROP TABLE `profiles_old`")
                db.execSQL("ALTER TABLE `trip_profile_links_new` RENAME TO `trip_profile_links`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_trip_profile_links_tripStableKey` ON `trip_profile_links` (`tripStableKey`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_trip_profile_links_profileId` ON `trip_profile_links` (`profileId`)")
            }
        }

        fun migration7To8(userId: String): Migration {
            require(userId.isNotBlank()) { "Migration userId must not be blank" }
            return object : Migration(7, 8) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS `trips_new` (
                            `id` TEXT NOT NULL,
                            `firestoreDocId` TEXT,
                            `tarih` TEXT,
                            `gun` TEXT,
                            `tur` TEXT,
                            `hat` TEXT,
                            `yon` TEXT,
                            `binisDuragi` TEXT,
                            `planlananBinis` TEXT,
                            `gercekBinis` TEXT,
                            `gecikme` TEXT,
                            `inisDuragi` TEXT,
                            `planlananInis` TEXT,
                            `gercekInis` TEXT,
                            `gununTipi` TEXT,
                            `havaDurumu` TEXT,
                            `oturabildimMi` TEXT,
                            `planlananYolSuresi` TEXT,
                            `gercekYolSuresi` TEXT,
                            `not` TEXT,
                            `biletKontrolu` TEXT,
                            `mesafe` TEXT,
                            `orsMesafeKm` REAL,
                            `orsMesafeText` TEXT,
                            `rmvMesafeKm` REAL,
                            `rmvMesafeMetre` INTEGER,
                            `rmvMesafeText` TEXT,
                            `rmvMesafeDurumu` TEXT,
                            `rmvMesafeGuncellemeTarihi` TEXT,
                            `rmvApiVersion` TEXT,
                            `journeyRef` TEXT,
                            `fromStopId` TEXT,
                            `toStopId` TEXT,
                            `durakSayisi` TEXT,
                            `yearMonth` TEXT,
                            `sortDate` TEXT,
                            `seatmateUuid` TEXT NOT NULL,
                            `userId` TEXT NOT NULL,
                            PRIMARY KEY(`userId`, `id`)
                        )
                    """.trimIndent())
                    db.execSQL("""
                        INSERT INTO `trips_new` (
                            `id`, `firestoreDocId`, `tarih`, `gun`, `tur`, `hat`, `yon`,
                            `binisDuragi`, `planlananBinis`, `gercekBinis`, `gecikme`,
                            `inisDuragi`, `planlananInis`, `gercekInis`, `gununTipi`,
                            `havaDurumu`, `oturabildimMi`, `planlananYolSuresi`,
                            `gercekYolSuresi`, `not`, `biletKontrolu`, `mesafe`,
                            `orsMesafeKm`, `orsMesafeText`, `rmvMesafeKm`, `rmvMesafeMetre`,
                            `rmvMesafeText`, `rmvMesafeDurumu`, `rmvMesafeGuncellemeTarihi`,
                            `rmvApiVersion`, `journeyRef`, `fromStopId`, `toStopId`,
                            `durakSayisi`, `yearMonth`, `sortDate`, `seatmateUuid`, `userId`
                        )
                        SELECT
                            `id`, `firestoreDocId`, `tarih`, `gun`, `tur`, `hat`, `yon`,
                            `binisDuragi`, `planlananBinis`, `gercekBinis`, `gecikme`,
                            `inisDuragi`, `planlananInis`, `gercekInis`, `gununTipi`,
                            `havaDurumu`, `oturabildimMi`, `planlananYolSuresi`,
                            `gercekYolSuresi`, `not`, `biletKontrolu`, `mesafe`,
                            `orsMesafeKm`, `orsMesafeText`, `rmvMesafeKm`, `rmvMesafeMetre`,
                            `rmvMesafeText`, `rmvMesafeDurumu`, `rmvMesafeGuncellemeTarihi`,
                            `rmvApiVersion`, `journeyRef`, `fromStopId`, `toStopId`,
                            `durakSayisi`, `yearMonth`, `sortDate`, `seatmateUuid`, ?
                        FROM `trips`
                    """.trimIndent(), arrayOf(userId))
                    db.execSQL("DROP TABLE `trips`")
                    db.execSQL("ALTER TABLE `trips_new` RENAME TO `trips`")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_trips_userId_yearMonth` ON `trips` (`userId`, `yearMonth`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_trips_userId_sortDate` ON `trips` (`userId`, `sortDate`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_trips_userId_firestoreDocId` ON `trips` (`userId`, `firestoreDocId`)")

                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS `profiles_new` (
                            `id` TEXT NOT NULL,
                            `displayName` TEXT NOT NULL,
                            `memoryNote` TEXT,
                            `createdAt` INTEGER NOT NULL,
                            `updatedAt` INTEGER NOT NULL,
                            `archived` INTEGER NOT NULL,
                            `sharedWithTransit` INTEGER NOT NULL DEFAULT 0,
                            `userId` TEXT NOT NULL,
                            PRIMARY KEY(`userId`, `id`)
                        )
                    """.trimIndent())
                    db.execSQL("""
                        INSERT INTO `profiles_new` (
                            `id`, `displayName`, `memoryNote`, `createdAt`, `updatedAt`,
                            `archived`, `sharedWithTransit`, `userId`
                        )
                        SELECT
                            `id`, `displayName`, `memoryNote`, `createdAt`, `updatedAt`,
                            `archived`, `sharedWithTransit`, ?
                        FROM `profiles`
                    """.trimIndent(), arrayOf(userId))

                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS `trip_profile_links_backup` (
                            `id` TEXT NOT NULL,
                            `tripStableKey` TEXT NOT NULL,
                            `profileId` TEXT NOT NULL,
                            `seatmateNote` TEXT,
                            `createdAt` INTEGER NOT NULL,
                            `updatedAt` INTEGER NOT NULL
                        )
                    """.trimIndent())
                    db.execSQL("""
                        INSERT INTO `trip_profile_links_backup` (
                            `id`, `tripStableKey`, `profileId`, `seatmateNote`, `createdAt`, `updatedAt`
                        )
                        SELECT
                            `id`, `tripStableKey`, `profileId`, `seatmateNote`, `createdAt`, `updatedAt`
                        FROM `trip_profile_links`
                    """.trimIndent())

                    db.execSQL("DROP TABLE `trip_profile_links`")
                    db.execSQL("DROP TABLE `profiles`")
                    db.execSQL("ALTER TABLE `profiles_new` RENAME TO `profiles`")
                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS `trip_profile_links_new` (
                            `id` TEXT NOT NULL,
                            `tripStableKey` TEXT NOT NULL,
                            `profileId` TEXT NOT NULL,
                            `seatmateNote` TEXT,
                            `createdAt` INTEGER NOT NULL,
                            `updatedAt` INTEGER NOT NULL,
                            `userId` TEXT NOT NULL,
                            PRIMARY KEY(`userId`, `id`),
                            FOREIGN KEY(`userId`, `profileId`)
                                REFERENCES `profiles`(`userId`, `id`)
                                ON UPDATE NO ACTION ON DELETE CASCADE
                        )
                    """.trimIndent())
                    db.execSQL("""
                        INSERT INTO `trip_profile_links_new` (
                            `id`, `tripStableKey`, `profileId`, `seatmateNote`,
                            `createdAt`, `updatedAt`, `userId`
                        )
                        SELECT
                            `id`, `tripStableKey`, `profileId`, `seatmateNote`,
                            `createdAt`, `updatedAt`, ?
                        FROM `trip_profile_links_backup`
                    """.trimIndent(), arrayOf(userId))
                    db.execSQL("DROP TABLE `trip_profile_links_backup`")
                    db.execSQL("ALTER TABLE `trip_profile_links_new` RENAME TO `trip_profile_links`")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_trip_profile_links_userId_tripStableKey` ON `trip_profile_links` (`userId`, `tripStableKey`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_trip_profile_links_userId_profileId` ON `trip_profile_links` (`userId`, `profileId`)")
                }
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `drive_vehicles` (
                        `id` TEXT NOT NULL,
                        `userId` TEXT NOT NULL,
                        `displayName` TEXT NOT NULL,
                        `brand` TEXT,
                        `model` TEXT,
                        `licensePlate` TEXT,
                        `modelYear` INTEGER,
                        `fuelType` TEXT,
                        `initialOdometerKm` REAL,
                        `currentOdometerKm` REAL,
                        `assignedPersonId` TEXT,
                        `notes` TEXT,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        `deletedAt` INTEGER,
                        `syncState` TEXT NOT NULL,
                        PRIMARY KEY(`userId`, `id`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS " +
                        "`index_drive_vehicles_userId_deletedAt_displayName` " +
                        "ON `drive_vehicles` (`userId`, `deletedAt`, `displayName`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS " +
                        "`index_drive_vehicles_userId_syncState_updatedAt` " +
                        "ON `drive_vehicles` (`userId`, `syncState`, `updatedAt`)"
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `drive_trips` (
                        `id` TEXT NOT NULL,
                        `userId` TEXT NOT NULL,
                        `vehicleId` TEXT NOT NULL,
                        `startedAt` INTEGER NOT NULL,
                        `endedAt` INTEGER,
                        `startOdometerKm` REAL,
                        `endOdometerKm` REAL,
                        `distanceKm` REAL NOT NULL,
                        `purpose` TEXT NOT NULL,
                        `startLocationName` TEXT,
                        `endLocationName` TEXT,
                        `notes` TEXT,
                        `entrySource` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        `deletedAt` INTEGER,
                        `syncState` TEXT NOT NULL,
                        PRIMARY KEY(`userId`, `id`),
                        FOREIGN KEY(`userId`, `vehicleId`)
                            REFERENCES `drive_vehicles`(`userId`, `id`)
                            ON UPDATE NO ACTION ON DELETE NO ACTION
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS " +
                        "`index_drive_trips_userId_vehicleId_deletedAt_startedAt` " +
                        "ON `drive_trips` (`userId`, `vehicleId`, `deletedAt`, `startedAt`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS " +
                        "`index_drive_trips_userId_syncState_updatedAt` " +
                        "ON `drive_trips` (`userId`, `syncState`, `updatedAt`)"
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `drive_sync_operations` (
                        `operationId` TEXT NOT NULL,
                        `userId` TEXT NOT NULL,
                        `entityType` TEXT NOT NULL,
                        `recordId` TEXT NOT NULL,
                        `operationType` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        `attemptCount` INTEGER NOT NULL,
                        `lastErrorCode` TEXT,
                        `retryEligible` INTEGER NOT NULL,
                        `nextAttemptAt` INTEGER,
                        PRIMARY KEY(`userId`, `entityType`, `recordId`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS " +
                        "`index_drive_sync_operations_userId_operationId` " +
                        "ON `drive_sync_operations` (`userId`, `operationId`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS " +
                        "`index_drive_sync_operations_userId_retryEligible_nextAttemptAt_createdAt` " +
                        "ON `drive_sync_operations` " +
                        "(`userId`, `retryEligible`, `nextAttemptAt`, `createdAt`)"
                )
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `drive_sync_metadata` (
                        `userId` TEXT NOT NULL,
                        `initialHydrationCompleted` INTEGER NOT NULL,
                        `vehicleCursorSeconds` INTEGER,
                        `vehicleCursorNanos` INTEGER,
                        `vehicleCursorDocumentId` TEXT,
                        `tripCursorSeconds` INTEGER,
                        `tripCursorNanos` INTEGER,
                        `tripCursorDocumentId` TEXT,
                        `lastSuccessfulPullAt` INTEGER,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`userId`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `drive_sync_receipts` (
                        `userId` TEXT NOT NULL,
                        `receiptId` TEXT NOT NULL,
                        `kind` TEXT NOT NULL,
                        `entityType` TEXT,
                        `recordId` TEXT,
                        `operationType` TEXT,
                        `status` TEXT NOT NULL,
                        `startedAt` INTEGER NOT NULL,
                        `finishedAt` INTEGER,
                        `attemptCount` INTEGER NOT NULL,
                        `errorCode` TEXT,
                        PRIMARY KEY(`userId`, `receiptId`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_drive_sync_receipts_userId_startedAt` " +
                        "ON `drive_sync_receipts` (`userId`, `startedAt`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS " +
                        "`index_drive_sync_receipts_userId_status_startedAt` " +
                        "ON `drive_sync_receipts` (`userId`, `status`, `startedAt`)"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `drive_field_provenance` (
                        `userId` TEXT NOT NULL,
                        `entityType` TEXT NOT NULL,
                        `recordId` TEXT NOT NULL,
                        `fieldName` TEXT NOT NULL,
                        `source` TEXT NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`userId`, `entityType`, `recordId`, `fieldName`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS " +
                        "`index_drive_field_provenance_userId_entityType_recordId` " +
                        "ON `drive_field_provenance` (`userId`, `entityType`, `recordId`)"
                )
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `drive_vehicle_assignments` (
                        `ownerUid` TEXT NOT NULL,
                        `vehicleId` TEXT NOT NULL,
                        `personId` TEXT,
                        `schemaVersion` INTEGER NOT NULL,
                        `revision` INTEGER NOT NULL,
                        `operationId` TEXT NOT NULL,
                        `source` TEXT NOT NULL,
                        `clientUpdatedAt` INTEGER NOT NULL,
                        `serverUpdatedAtSeconds` INTEGER,
                        `serverUpdatedAtNanos` INTEGER,
                        `deletedAt` INTEGER,
                        `syncState` TEXT NOT NULL,
                        `healthCode` TEXT,
                        `conflictOperationId` TEXT,
                        `lastErrorCode` TEXT,
                        PRIMARY KEY(`ownerUid`, `vehicleId`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_drive_vehicle_assignments_ownerUid_personId` " +
                        "ON `drive_vehicle_assignments` (`ownerUid`, `personId`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_drive_vehicle_assignments_ownerUid_syncState_clientUpdatedAt` " +
                        "ON `drive_vehicle_assignments` (`ownerUid`, `syncState`, `clientUpdatedAt`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_drive_vehicle_assignments_ownerUid_deletedAt` " +
                        "ON `drive_vehicle_assignments` (`ownerUid`, `deletedAt`)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_drive_vehicle_assignments_ownerUid_operationId` " +
                        "ON `drive_vehicle_assignments` (`ownerUid`, `operationId`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_drive_vehicle_assignments_ownerUid_healthCode` " +
                        "ON `drive_vehicle_assignments` (`ownerUid`, `healthCode`)"
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `drive_assignment_operations` (
                        `ownerUid` TEXT NOT NULL,
                        `operationId` TEXT NOT NULL,
                        `vehicleId` TEXT NOT NULL,
                        `personId` TEXT,
                        `schemaVersion` INTEGER NOT NULL,
                        `targetRevision` INTEGER NOT NULL,
                        `source` TEXT NOT NULL,
                        `clientUpdatedAt` INTEGER NOT NULL,
                        `deletedAt` INTEGER,
                        `state` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        `attemptCount` INTEGER NOT NULL,
                        `nextAttemptAt` INTEGER,
                        `lastErrorCode` TEXT,
                        PRIMARY KEY(`ownerUid`, `operationId`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_drive_assignment_operations_ownerUid_vehicleId_targetRevision` " +
                        "ON `drive_assignment_operations` (`ownerUid`, `vehicleId`, `targetRevision`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_drive_assignment_operations_ownerUid_state_nextAttemptAt_createdAt` " +
                        "ON `drive_assignment_operations` (`ownerUid`, `state`, `nextAttemptAt`, `createdAt`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_drive_assignment_operations_ownerUid_vehicleId_createdAt` " +
                        "ON `drive_assignment_operations` (`ownerUid`, `vehicleId`, `createdAt`)"
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `drive_assignment_sync_metadata` (
                        `ownerUid` TEXT NOT NULL,
                        `initialHydrationCompleted` INTEGER NOT NULL,
                        `cursorSeconds` INTEGER,
                        `cursorNanos` INTEGER,
                        `cursorDocumentId` TEXT,
                        `lastSuccessfulPullAt` INTEGER,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`ownerUid`)
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `drive_assignment_sync_receipts` (
                        `ownerUid` TEXT NOT NULL,
                        `receiptId` TEXT NOT NULL,
                        `operationId` TEXT,
                        `vehicleId` TEXT,
                        `kind` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `source` TEXT,
                        `revision` INTEGER,
                        `winningOperationId` TEXT,
                        `startedAt` INTEGER NOT NULL,
                        `finishedAt` INTEGER,
                        `attemptCount` INTEGER NOT NULL,
                        `errorCode` TEXT,
                        PRIMARY KEY(`ownerUid`, `receiptId`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_drive_assignment_sync_receipts_ownerUid_operationId` " +
                        "ON `drive_assignment_sync_receipts` (`ownerUid`, `operationId`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_drive_assignment_sync_receipts_ownerUid_status_startedAt` " +
                        "ON `drive_assignment_sync_receipts` (`ownerUid`, `status`, `startedAt`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_drive_assignment_sync_receipts_ownerUid_vehicleId_startedAt` " +
                        "ON `drive_assignment_sync_receipts` (`ownerUid`, `vehicleId`, `startedAt`)"
                )
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            val userId = CurrentUserProvider.requireUserId()
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "toplutasima_database"
                )
                .addMigrations(
                    MIGRATION_3_4,
                    MIGRATION_4_5,
                    MIGRATION_5_6,
                    MIGRATION_6_7,
                    migration7To8(userId),
                    MIGRATION_8_9,
                    MIGRATION_9_10,
                    MIGRATION_10_11
                )
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

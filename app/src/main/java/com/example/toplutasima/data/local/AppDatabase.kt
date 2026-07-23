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
import com.example.toplutasima.data.local.dao.DriveVehiclePhotoDao
import com.example.toplutasima.data.local.dao.DrivePhotoOperationDao
import com.example.toplutasima.data.local.dao.DrivePhotoSyncMetadataDao
import com.example.toplutasima.data.local.dao.DrivePhotoSyncReceiptDao
import com.example.toplutasima.data.local.dao.DriveAssignmentOperationDao
import com.example.toplutasima.data.local.dao.DriveAssignmentSyncMetadataDao
import com.example.toplutasima.data.local.dao.DriveAssignmentSyncReceiptDao
import com.example.toplutasima.data.local.dao.DriveExpenseDao
import com.example.toplutasima.data.local.dao.DriveLedgerConflictDao
import com.example.toplutasima.data.local.dao.DriveLedgerOperationDao
import com.example.toplutasima.data.local.dao.DriveLedgerSyncMetadataDao
import com.example.toplutasima.data.local.dao.DriveLedgerSyncReceiptDao
import com.example.toplutasima.data.local.dao.DriveOdometerEntryDao
import com.example.toplutasima.data.local.dao.DriveReminderDao
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
import com.example.toplutasima.data.local.entity.DriveVehiclePhotoEntity
import com.example.toplutasima.data.local.entity.DrivePhotoOperationEntity
import com.example.toplutasima.data.local.entity.DrivePhotoSyncMetadataEntity
import com.example.toplutasima.data.local.entity.DrivePhotoSyncReceiptEntity
import com.example.toplutasima.data.local.entity.DriveExpenseEntity
import com.example.toplutasima.data.local.entity.DriveLedgerConflictEntity
import com.example.toplutasima.data.local.entity.DriveLedgerOperationEntity
import com.example.toplutasima.data.local.entity.DriveLedgerSyncMetadataEntity
import com.example.toplutasima.data.local.entity.DriveLedgerSyncReceiptEntity
import com.example.toplutasima.data.local.entity.DriveOdometerEntryEntity
import com.example.toplutasima.data.local.entity.DriveReminderEntity
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
        DriveAssignmentSyncReceiptEntity::class,
        DriveVehiclePhotoEntity::class,
        DrivePhotoOperationEntity::class,
        DrivePhotoSyncMetadataEntity::class,
        DrivePhotoSyncReceiptEntity::class,
        DriveOdometerEntryEntity::class,
        DriveExpenseEntity::class,
        DriveReminderEntity::class,
        DriveLedgerOperationEntity::class,
        DriveLedgerSyncMetadataEntity::class,
        DriveLedgerSyncReceiptEntity::class,
        DriveLedgerConflictEntity::class
    ],
    version = 13,
    exportSchema = true
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
    abstract fun driveVehiclePhotoDao(): DriveVehiclePhotoDao
    abstract fun drivePhotoOperationDao(): DrivePhotoOperationDao
    abstract fun drivePhotoSyncMetadataDao(): DrivePhotoSyncMetadataDao
    abstract fun drivePhotoSyncReceiptDao(): DrivePhotoSyncReceiptDao
    abstract fun driveOdometerEntryDao(): DriveOdometerEntryDao
    abstract fun driveExpenseDao(): DriveExpenseDao
    abstract fun driveReminderDao(): DriveReminderDao
    abstract fun driveLedgerOperationDao(): DriveLedgerOperationDao
    abstract fun driveLedgerSyncMetadataDao(): DriveLedgerSyncMetadataDao
    abstract fun driveLedgerSyncReceiptDao(): DriveLedgerSyncReceiptDao
    abstract fun driveLedgerConflictDao(): DriveLedgerConflictDao

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

        /** Sprint 6B: additive extended vehicle profile and UID-scoped photo outbox. */
        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                listOf(
                    "countryCode TEXT",
                    "transmissionType TEXT",
                    "bodyType TEXT",
                    "color TEXT",
                    "vin TEXT",
                    "engineDisplacementCc INTEGER",
                    "enginePowerKw INTEGER",
                    "purchaseDate INTEGER",
                    "purchasePriceMinor INTEGER",
                    "currencyCode TEXT",
                    "primaryPhotoId TEXT",
                    "trimLevel TEXT",
                    "engineCode TEXT",
                    "registrationDate INTEGER",
                    "inspectionDueDate INTEGER",
                    "insuranceDueDate INTEGER",
                    "tireSize TEXT",
                    "schemaVersion INTEGER NOT NULL DEFAULT 2",
                    "primaryPhotoRevision INTEGER NOT NULL DEFAULT 0",
                    "primaryPhotoOperationId TEXT"
                ).forEach { definition ->
                    db.execSQL("ALTER TABLE `drive_vehicles` ADD COLUMN $definition")
                }

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `drive_vehicle_photos` (
                        `ownerUid` TEXT NOT NULL,
                        `photoId` TEXT NOT NULL,
                        `vehicleId` TEXT NOT NULL,
                        `localUri` TEXT,
                        `localPreparedPath` TEXT,
                        `storagePath` TEXT,
                        `contentHash` TEXT,
                        `mimeType` TEXT,
                        `width` INTEGER,
                        `height` INTEGER,
                        `sizeBytes` INTEGER,
                        `sortOrder` INTEGER NOT NULL,
                        `isPrimary` INTEGER NOT NULL,
                        `schemaVersion` INTEGER NOT NULL,
                        `revision` INTEGER NOT NULL,
                        `operationId` TEXT NOT NULL,
                        `source` TEXT NOT NULL,
                        `clientUpdatedAt` INTEGER NOT NULL,
                        `serverUpdatedAtSeconds` INTEGER,
                        `serverUpdatedAtNanos` INTEGER,
                        `deletedAt` INTEGER,
                        `uploadState` TEXT NOT NULL,
                        `remoteState` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        `lastErrorCode` TEXT,
                        `healthCode` TEXT,
                        PRIMARY KEY(`ownerUid`, `photoId`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_drive_vehicle_photos_ownerUid_vehicleId` ON `drive_vehicle_photos` (`ownerUid`, `vehicleId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_drive_vehicle_photos_ownerUid_uploadState` ON `drive_vehicle_photos` (`ownerUid`, `uploadState`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_drive_vehicle_photos_ownerUid_deletedAt` ON `drive_vehicle_photos` (`ownerUid`, `deletedAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_drive_vehicle_photos_ownerUid_vehicleId_sortOrder` ON `drive_vehicle_photos` (`ownerUid`, `vehicleId`, `sortOrder`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_drive_vehicle_photos_ownerUid_contentHash` ON `drive_vehicle_photos` (`ownerUid`, `contentHash`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_drive_vehicle_photos_ownerUid_serverUpdatedAtSeconds_serverUpdatedAtNanos_photoId` ON `drive_vehicle_photos` (`ownerUid`, `serverUpdatedAtSeconds`, `serverUpdatedAtNanos`, `photoId`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `drive_photo_operations` (
                        `ownerUid` TEXT NOT NULL,
                        `operationId` TEXT NOT NULL,
                        `photoId` TEXT NOT NULL,
                        `vehicleId` TEXT NOT NULL,
                        `type` TEXT NOT NULL,
                        `targetRevision` INTEGER NOT NULL,
                        `targetPrimaryPhotoId` TEXT,
                        `expectedContentHash` TEXT,
                        `state` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        `attemptCount` INTEGER NOT NULL,
                        `nextAttemptAt` INTEGER NOT NULL,
                        `claimedAt` INTEGER,
                        `lastErrorCode` TEXT,
                        PRIMARY KEY(`ownerUid`, `operationId`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_drive_photo_operations_ownerUid_photoId` ON `drive_photo_operations` (`ownerUid`, `photoId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_drive_photo_operations_ownerUid_vehicleId` ON `drive_photo_operations` (`ownerUid`, `vehicleId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_drive_photo_operations_ownerUid_state_nextAttemptAt_createdAt` ON `drive_photo_operations` (`ownerUid`, `state`, `nextAttemptAt`, `createdAt`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_drive_photo_operations_ownerUid_photoId_type_targetRevision` ON `drive_photo_operations` (`ownerUid`, `photoId`, `type`, `targetRevision`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `drive_photo_sync_metadata` (
                        `ownerUid` TEXT NOT NULL,
                        `initialHydrationCompleted` INTEGER NOT NULL,
                        `cursorSeconds` INTEGER,
                        `cursorNanos` INTEGER,
                        `cursorDocumentPath` TEXT,
                        `lastSuccessfulPullAt` INTEGER,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`ownerUid`)
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `drive_photo_sync_receipts` (
                        `ownerUid` TEXT NOT NULL,
                        `receiptId` TEXT NOT NULL,
                        `operationId` TEXT NOT NULL,
                        `photoId` TEXT NOT NULL,
                        `vehicleId` TEXT NOT NULL,
                        `kind` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `provenance` TEXT NOT NULL,
                        `revision` INTEGER NOT NULL,
                        `winningOperationId` TEXT,
                        `attemptCount` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `finishedAt` INTEGER,
                        `errorCode` TEXT,
                        PRIMARY KEY(`ownerUid`, `receiptId`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_drive_photo_sync_receipts_ownerUid_operationId` ON `drive_photo_sync_receipts` (`ownerUid`, `operationId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_drive_photo_sync_receipts_ownerUid_vehicleId_createdAt` ON `drive_photo_sync_receipts` (`ownerUid`, `vehicleId`, `createdAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_drive_photo_sync_receipts_ownerUid_status_createdAt` ON `drive_photo_sync_receipts` (`ownerUid`, `status`, `createdAt`)")
            }
        }

        /** Sprint 7A: additive UID-scoped vehicle ledger, odometer, expense and reminder core. */
        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `drive_odometer_entries` (
                        `ownerUid` TEXT NOT NULL,
                        `odometerEntryId` TEXT NOT NULL,
                        `vehicleId` TEXT NOT NULL,
                        `observedAt` INTEGER,
                        `odometerMeters` INTEGER NOT NULL,
                        `quality` TEXT NOT NULL,
                        `readingRole` TEXT NOT NULL,
                        `odometerSeriesId` TEXT NOT NULL,
                        `sourceRecordType` TEXT,
                        `sourceRecordId` TEXT,
                        `correctionOfEntryId` TEXT,
                        `resetReason` TEXT,
                        `notes` TEXT,
                        `schemaVersion` INTEGER NOT NULL,
                        `revision` INTEGER NOT NULL,
                        `operationId` TEXT NOT NULL,
                        `source` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `clientUpdatedAt` INTEGER NOT NULL,
                        `serverUpdatedAtSeconds` INTEGER,
                        `serverUpdatedAtNanos` INTEGER,
                        `deletedAt` INTEGER,
                        `syncState` TEXT NOT NULL,
                        `healthCode` TEXT,
                        PRIMARY KEY(`ownerUid`, `odometerEntryId`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_drive_odometer_entries_ownerUid_vehicleId_deletedAt_observedAt_odometerEntryId` ON `drive_odometer_entries` (`ownerUid`, `vehicleId`, `deletedAt`, `observedAt`, `odometerEntryId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_drive_odometer_entries_ownerUid_vehicleId_odometerSeriesId_odometerMeters` ON `drive_odometer_entries` (`ownerUid`, `vehicleId`, `odometerSeriesId`, `odometerMeters`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_drive_odometer_entries_ownerUid_sourceRecordType_sourceRecordId_readingRole` ON `drive_odometer_entries` (`ownerUid`, `sourceRecordType`, `sourceRecordId`, `readingRole`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_drive_odometer_entries_ownerUid_syncState_clientUpdatedAt` ON `drive_odometer_entries` (`ownerUid`, `syncState`, `clientUpdatedAt`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_drive_odometer_entries_ownerUid_operationId` ON `drive_odometer_entries` (`ownerUid`, `operationId`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `drive_expenses` (
                        `ownerUid` TEXT NOT NULL,
                        `expenseId` TEXT NOT NULL,
                        `vehicleId` TEXT NOT NULL,
                        `occurredAt` INTEGER NOT NULL,
                        `category` TEXT NOT NULL,
                        `transactionKind` TEXT NOT NULL,
                        `amountMinor` INTEGER NOT NULL,
                        `currencyCode` TEXT NOT NULL,
                        `currencyExponent` INTEGER NOT NULL,
                        `vendorName` TEXT,
                        `notes` TEXT,
                        `referenceNumber` TEXT,
                        `periodStartEpochDay` INTEGER,
                        `periodEndEpochDay` INTEGER,
                        `dueEpochDay` INTEGER,
                        `odometerEntryId` TEXT,
                        `odometerMetersSnapshot` INTEGER,
                        `splitGroupId` TEXT,
                        `duplicateFingerprint` TEXT,
                        `relatedExpenseId` TEXT,
                        `schemaVersion` INTEGER NOT NULL,
                        `revision` INTEGER NOT NULL,
                        `operationId` TEXT NOT NULL,
                        `source` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `clientUpdatedAt` INTEGER NOT NULL,
                        `serverUpdatedAtSeconds` INTEGER,
                        `serverUpdatedAtNanos` INTEGER,
                        `deletedAt` INTEGER,
                        `syncState` TEXT NOT NULL,
                        `healthCode` TEXT,
                        PRIMARY KEY(`ownerUid`, `expenseId`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_drive_expenses_ownerUid_vehicleId_deletedAt_occurredAt_expenseId` ON `drive_expenses` (`ownerUid`, `vehicleId`, `deletedAt`, `occurredAt`, `expenseId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_drive_expenses_ownerUid_vehicleId_category_occurredAt` ON `drive_expenses` (`ownerUid`, `vehicleId`, `category`, `occurredAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_drive_expenses_ownerUid_vehicleId_currencyCode_currencyExponent_occurredAt` ON `drive_expenses` (`ownerUid`, `vehicleId`, `currencyCode`, `currencyExponent`, `occurredAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_drive_expenses_ownerUid_syncState_clientUpdatedAt` ON `drive_expenses` (`ownerUid`, `syncState`, `clientUpdatedAt`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_drive_expenses_ownerUid_operationId` ON `drive_expenses` (`ownerUid`, `operationId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_drive_expenses_ownerUid_duplicateFingerprint` ON `drive_expenses` (`ownerUid`, `duplicateFingerprint`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `drive_reminders` (
                        `ownerUid` TEXT NOT NULL,
                        `reminderId` TEXT NOT NULL,
                        `vehicleId` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `reminderType` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `dueEpochDay` INTEGER,
                        `dueOdometerMeters` INTEGER,
                        `recurrenceMonths` INTEGER,
                        `recurrenceDistanceMeters` INTEGER,
                        `recurrenceAnchor` TEXT NOT NULL,
                        `leadDays` INTEGER,
                        `leadDistanceMeters` INTEGER,
                        `snoozedUntilEpochDay` INTEGER,
                        `linkedServiceRecordId` TEXT,
                        `lastCompletedServiceRecordId` TEXT,
                        `lastCompletedAt` INTEGER,
                        `lastCompletedOdometerMeters` INTEGER,
                        `notes` TEXT,
                        `schemaVersion` INTEGER NOT NULL,
                        `revision` INTEGER NOT NULL,
                        `operationId` TEXT NOT NULL,
                        `source` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `clientUpdatedAt` INTEGER NOT NULL,
                        `serverUpdatedAtSeconds` INTEGER,
                        `serverUpdatedAtNanos` INTEGER,
                        `deletedAt` INTEGER,
                        `syncState` TEXT NOT NULL,
                        `healthCode` TEXT,
                        PRIMARY KEY(`ownerUid`, `reminderId`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_drive_reminders_ownerUid_vehicleId_status_dueEpochDay` ON `drive_reminders` (`ownerUid`, `vehicleId`, `status`, `dueEpochDay`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_drive_reminders_ownerUid_vehicleId_status_dueOdometerMeters` ON `drive_reminders` (`ownerUid`, `vehicleId`, `status`, `dueOdometerMeters`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_drive_reminders_ownerUid_linkedServiceRecordId` ON `drive_reminders` (`ownerUid`, `linkedServiceRecordId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_drive_reminders_ownerUid_syncState_clientUpdatedAt` ON `drive_reminders` (`ownerUid`, `syncState`, `clientUpdatedAt`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_drive_reminders_ownerUid_operationId` ON `drive_reminders` (`ownerUid`, `operationId`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `drive_ledger_operations` (
                        `ownerUid` TEXT NOT NULL,
                        `operationId` TEXT NOT NULL,
                        `logicalBatchId` TEXT NOT NULL,
                        `entityType` TEXT NOT NULL,
                        `recordId` TEXT NOT NULL,
                        `vehicleId` TEXT NOT NULL,
                        `kind` TEXT NOT NULL,
                        `targetRevision` INTEGER NOT NULL,
                        `state` TEXT NOT NULL,
                        `attemptCount` INTEGER NOT NULL,
                        `nextAttemptAt` INTEGER NOT NULL,
                        `claimedAt` INTEGER,
                        `claimedBy` TEXT,
                        `safeErrorCode` TEXT,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`ownerUid`, `operationId`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_drive_ledger_operations_ownerUid_entityType_recordId_targetRevision` ON `drive_ledger_operations` (`ownerUid`, `entityType`, `recordId`, `targetRevision`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_drive_ledger_operations_ownerUid_state_nextAttemptAt_createdAt` ON `drive_ledger_operations` (`ownerUid`, `state`, `nextAttemptAt`, `createdAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_drive_ledger_operations_ownerUid_vehicleId_createdAt` ON `drive_ledger_operations` (`ownerUid`, `vehicleId`, `createdAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_drive_ledger_operations_ownerUid_logicalBatchId` ON `drive_ledger_operations` (`ownerUid`, `logicalBatchId`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `drive_ledger_sync_metadata` (
                        `ownerUid` TEXT NOT NULL,
                        `collectionType` TEXT NOT NULL,
                        `initialHydrationCompleted` INTEGER NOT NULL,
                        `cursorSeconds` INTEGER,
                        `cursorNanos` INTEGER,
                        `cursorDocumentId` TEXT,
                        `lastSuccessfulPullAt` INTEGER,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`ownerUid`, `collectionType`)
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `drive_ledger_sync_receipts` (
                        `ownerUid` TEXT NOT NULL,
                        `receiptId` TEXT NOT NULL,
                        `operationId` TEXT NOT NULL,
                        `logicalBatchId` TEXT,
                        `entityType` TEXT NOT NULL,
                        `recordId` TEXT NOT NULL,
                        `vehicleId` TEXT NOT NULL,
                        `kind` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `provenance` TEXT NOT NULL,
                        `revision` INTEGER,
                        `winningOperationId` TEXT,
                        `attemptCount` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `finishedAt` INTEGER,
                        `safeErrorCode` TEXT,
                        PRIMARY KEY(`ownerUid`, `receiptId`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_drive_ledger_sync_receipts_ownerUid_operationId` ON `drive_ledger_sync_receipts` (`ownerUid`, `operationId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_drive_ledger_sync_receipts_ownerUid_entityType_recordId_createdAt` ON `drive_ledger_sync_receipts` (`ownerUid`, `entityType`, `recordId`, `createdAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_drive_ledger_sync_receipts_ownerUid_status_createdAt` ON `drive_ledger_sync_receipts` (`ownerUid`, `status`, `createdAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_drive_ledger_sync_receipts_ownerUid_vehicleId_createdAt` ON `drive_ledger_sync_receipts` (`ownerUid`, `vehicleId`, `createdAt`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `drive_ledger_conflicts` (
                        `ownerUid` TEXT NOT NULL,
                        `conflictId` TEXT NOT NULL,
                        `entityType` TEXT NOT NULL,
                        `recordId` TEXT NOT NULL,
                        `vehicleId` TEXT NOT NULL,
                        `localOperationId` TEXT NOT NULL,
                        `remoteOperationId` TEXT NOT NULL,
                        `localRevision` INTEGER NOT NULL,
                        `remoteRevision` INTEGER NOT NULL,
                        `localSnapshotJson` TEXT NOT NULL,
                        `remoteSnapshotJson` TEXT NOT NULL,
                        `winnerOperationId` TEXT NOT NULL,
                        `reason` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `resolvedAt` INTEGER,
                        PRIMARY KEY(`ownerUid`, `conflictId`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_drive_ledger_conflicts_ownerUid_entityType_recordId_resolvedAt` ON `drive_ledger_conflicts` (`ownerUid`, `entityType`, `recordId`, `resolvedAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_drive_ledger_conflicts_ownerUid_vehicleId_createdAt` ON `drive_ledger_conflicts` (`ownerUid`, `vehicleId`, `createdAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_drive_ledger_conflicts_ownerUid_localOperationId` ON `drive_ledger_conflicts` (`ownerUid`, `localOperationId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_drive_ledger_conflicts_ownerUid_remoteOperationId` ON `drive_ledger_conflicts` (`ownerUid`, `remoteOperationId`)")
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
                    MIGRATION_10_11,
                    MIGRATION_11_12,
                    MIGRATION_12_13
                )
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

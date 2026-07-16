package com.example.toplutasima.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.toplutasima.auth.CurrentUserProvider
import com.example.toplutasima.data.local.dao.ProfileDao
import com.example.toplutasima.data.local.dao.TripDao
import com.example.toplutasima.data.local.dao.TripProfileLinkDao
import com.example.toplutasima.data.local.entity.ProfileEntity
import com.example.toplutasima.data.local.entity.TripEntity
import com.example.toplutasima.data.local.entity.TripProfileLinkEntity

@Database(
    entities = [TripEntity::class, ProfileEntity::class, TripProfileLinkEntity::class],
    version = 8,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun tripDao(): TripDao
    abstract fun profileDao(): ProfileDao
    abstract fun tripProfileLinkDao(): TripProfileLinkDao

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
                    migration7To8(userId)
                )
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

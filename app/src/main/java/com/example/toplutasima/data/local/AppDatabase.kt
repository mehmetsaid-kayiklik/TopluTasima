package com.example.toplutasima.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.toplutasima.data.local.dao.ProfileDao
import com.example.toplutasima.data.local.dao.TripDao
import com.example.toplutasima.data.local.dao.TripProfileLinkDao
import com.example.toplutasima.data.local.entity.ProfileEntity
import com.example.toplutasima.data.local.entity.TripEntity
import com.example.toplutasima.data.local.entity.TripProfileLinkEntity

@Database(
    entities = [TripEntity::class, ProfileEntity::class, TripProfileLinkEntity::class],
    version = 7,
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
                        `memoryNote` TEXT, 
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

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "toplutasima_database"
                )
                .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

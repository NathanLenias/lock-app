package com.nathanb.lock.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.nathanb.lock.data.model.NfcTag
import com.nathanb.lock.data.model.Profile
import com.nathanb.lock.data.model.Session

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `nfc_tags` (" +
                "`uid` TEXT NOT NULL, " +
                "`name` TEXT NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, " +
                "PRIMARY KEY(`uid`))"
        )
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sessions_startTime` ON `sessions` (`startTime`)")
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // New profile columns (defaults must match @ColumnInfo defaults in Models.kt)
        db.execSQL("ALTER TABLE `profiles` ADD COLUMN `type` TEXT NOT NULL DEFAULT 'standard'")
        db.execSQL("ALTER TABLE `profiles` ADD COLUMN `isDefault` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `profiles` ADD COLUMN `durationMs` INTEGER")
        // Tag -> profile activation link
        db.execSQL("ALTER TABLE `nfc_tags` ADD COLUMN `profileId` INTEGER")

        // Promote the single existing profile to default, and bind existing tags to it.
        // Pre-v4 there is exactly one (implicit) profile; pick the lowest id defensively.
        db.execSQL(
            "UPDATE `profiles` SET `isDefault` = 1 " +
                "WHERE `id` = (SELECT `id` FROM `profiles` ORDER BY `id` ASC LIMIT 1)"
        )
        db.execSQL(
            "UPDATE `nfc_tags` SET `profileId` = " +
                "(SELECT `id` FROM `profiles` ORDER BY `id` ASC LIMIT 1)"
        )
    }
}

@Database(
    entities = [Profile::class, Session::class, NfcTag::class],
    version = 4,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class LockDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun sessionDao(): SessionDao
    abstract fun nfcTagDao(): NfcTagDao
}

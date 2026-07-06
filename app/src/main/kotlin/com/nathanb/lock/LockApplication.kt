package com.nathanb.lock

import android.app.Application
import androidx.room.Room
import com.nathanb.lock.data.database.LockDatabase
import com.nathanb.lock.data.database.MIGRATION_1_2
import com.nathanb.lock.data.database.MIGRATION_2_3
import com.nathanb.lock.data.database.MIGRATION_3_4
import com.nathanb.lock.data.repository.LockRepository

class LockApplication : Application() {

    lateinit var database: LockDatabase
        private set

    lateinit var repository: LockRepository
        private set

    override fun onCreate() {
        super.onCreate()

        database = Room.databaseBuilder(
            this,
            LockDatabase::class.java,
            "lock.db",
        )
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
            .build()

        repository = LockRepository(
            context = this,
            profileDao = database.profileDao(),
            sessionDao = database.sessionDao(),
            nfcTagDao = database.nfcTagDao(),
            database = database,
        )
    }
}

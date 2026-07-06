package com.nathanb.lock.data.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.nathanb.lock.data.model.NfcTag
import com.nathanb.lock.data.model.Profile
import com.nathanb.lock.data.model.Session
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profiles")
    fun getAll(): Flow<List<Profile>>

    @Query("SELECT * FROM profiles WHERE id = :id")
    suspend fun getById(id: Long): Profile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(profile: Profile): Long

    @Update
    suspend fun update(profile: Profile)

    @Delete
    suspend fun delete(profile: Profile)

    @Query("SELECT * FROM profiles")
    suspend fun getAllOnce(): List<Profile>

    @Query("DELETE FROM profiles")
    suspend fun deleteAll()
}

@Dao
interface SessionDao {
    @Insert
    suspend fun insert(session: Session): Long

    @Query("UPDATE sessions SET endTime = :endTime, endReason = :endReason WHERE id = :id")
    suspend fun endSession(id: Long, endTime: Long, endReason: String)

    @Query("SELECT * FROM sessions ORDER BY startTime DESC")
    fun getAll(): Flow<List<Session>>

    @Query("SELECT * FROM sessions WHERE endTime IS NULL LIMIT 1")
    suspend fun getActiveSession(): Session?

    @Query("SELECT COUNT(*) FROM sessions WHERE endTime IS NOT NULL")
    fun getCompletedCount(): Flow<Int>

    @Query("SELECT COALESCE(SUM(endTime - startTime), 0) FROM sessions WHERE endTime IS NOT NULL")
    fun getTotalBlockedMs(): Flow<Long>

    @Query("SELECT MAX(endTime - startTime) FROM sessions WHERE endTime IS NOT NULL")
    fun getLongestSessionMs(): Flow<Long?>

    @Query("SELECT * FROM sessions ORDER BY startTime DESC")
    suspend fun getAllOnce(): List<Session>

    @Insert
    suspend fun insertAll(sessions: List<Session>)

    @Query("DELETE FROM sessions")
    suspend fun deleteAll()
}

@Dao
interface NfcTagDao {
    @Query("SELECT * FROM nfc_tags ORDER BY createdAt ASC")
    fun getAll(): Flow<List<NfcTag>>

    @Query("SELECT * FROM nfc_tags WHERE uid = :uid")
    suspend fun getByUid(uid: String): NfcTag?

    @Query("SELECT COUNT(*) FROM nfc_tags")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(tag: NfcTag)

    @Query("DELETE FROM nfc_tags WHERE uid = :uid")
    suspend fun delete(uid: String)

    @Query("UPDATE nfc_tags SET name = :name WHERE uid = :uid")
    suspend fun rename(uid: String, name: String)

    @Query("UPDATE nfc_tags SET profileId = :profileId WHERE uid = :uid")
    suspend fun setProfile(uid: String, profileId: Long?)

    @Query("UPDATE nfc_tags SET profileId = :newProfileId WHERE profileId = :oldProfileId")
    suspend fun reassignProfile(oldProfileId: Long, newProfileId: Long)

    @Query("SELECT * FROM nfc_tags WHERE profileId = :profileId")
    suspend fun getByProfile(profileId: Long): List<NfcTag>

    @Query("SELECT * FROM nfc_tags ORDER BY createdAt ASC")
    suspend fun getAllOnce(): List<NfcTag>

    @Query("DELETE FROM nfc_tags")
    suspend fun deleteAll()
}

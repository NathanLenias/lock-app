package com.nathanb.lock.fake

import com.nathanb.lock.data.database.NfcTagDao
import com.nathanb.lock.data.database.ProfileDao
import com.nathanb.lock.data.database.ScheduleDao
import com.nathanb.lock.data.database.ScheduleProfileDao
import com.nathanb.lock.data.database.SessionDao
import com.nathanb.lock.data.model.NfcTag
import com.nathanb.lock.data.model.Profile
import com.nathanb.lock.data.model.Schedule
import com.nathanb.lock.data.model.ScheduleProfileLink
import com.nathanb.lock.data.model.Session
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class FakeProfileDao : ProfileDao {
    private val profiles = MutableStateFlow<List<Profile>>(emptyList())
    private var nextId = 1L

    override fun getAll(): Flow<List<Profile>> = profiles

    override suspend fun getById(id: Long): Profile? = profiles.value.find { it.id == id }

    override suspend fun insert(profile: Profile): Long {
        val id = if (profile.id == 0L) nextId++ else profile.id
        val newProfile = profile.copy(id = id)
        profiles.update { list -> list.filter { it.id != id } + newProfile }
        return id
    }

    override suspend fun update(profile: Profile) {
        profiles.update { list -> list.map { if (it.id == profile.id) profile else it } }
    }

    override suspend fun delete(profile: Profile) {
        profiles.update { list -> list.filter { it.id != profile.id } }
    }

    override suspend fun getAllOnce(): List<Profile> = profiles.value

    override suspend fun deleteAll() {
        profiles.value = emptyList()
    }
}

class FakeSessionDao : SessionDao {
    private val sessions = MutableStateFlow<List<Session>>(emptyList())
    private var nextId = 1L

    override suspend fun insert(session: Session): Long {
        val id = if (session.id == 0L) nextId++ else session.id
        val newSession = session.copy(id = id)
        sessions.update { it + newSession }
        return id
    }

    override suspend fun endSession(id: Long, endTime: Long, endReason: String) {
        sessions.update { list ->
            list.map {
                if (it.id == id) it.copy(endTime = endTime, endReason = endReason) else it
            }
        }
    }

    override fun getAll(): Flow<List<Session>> = sessions.map { it.sortedByDescending { s -> s.startTime } }

    override suspend fun getActiveSession(): Session? = sessions.value.find { it.endTime == null }

    override fun getCompletedCount(): Flow<Int> = sessions.map { list -> list.count { it.endTime != null } }

    override fun getTotalBlockedMs(): Flow<Long> = sessions.map { list ->
        list.filter { it.endTime != null }.sumOf { (it.endTime!! - it.startTime) }
    }

    override fun getLongestSessionMs(): Flow<Long?> = sessions.map { list ->
        list.filter { it.endTime != null }.maxOfOrNull { it.endTime!! - it.startTime }
    }

    override suspend fun getAllOnce(): List<Session> = sessions.value

    override suspend fun insertAll(sessions: List<Session>) {
        this.sessions.update { it + sessions }
    }

    override suspend fun deleteAll() {
        sessions.value = emptyList()
    }
}

class FakeScheduleDao : ScheduleDao {
    private val schedules = MutableStateFlow<List<Schedule>>(emptyList())
    private var nextId = 1L

    override fun getAll(): Flow<List<Schedule>> = schedules.map { it.sortedBy { s -> s.createdAt } }

    override suspend fun getById(id: Long): Schedule? = schedules.value.find { it.id == id }

    override suspend fun insert(schedule: Schedule): Long {
        val id = if (schedule.id == 0L) nextId++ else schedule.id
        val newSchedule = schedule.copy(id = id)
        schedules.update { list -> list.filter { it.id != id } + newSchedule }
        return id
    }

    override suspend fun update(schedule: Schedule) {
        schedules.update { list -> list.map { if (it.id == schedule.id) schedule else it } }
    }

    override suspend fun setEnabled(id: Long, enabled: Boolean) {
        schedules.update { list -> list.map { if (it.id == id) it.copy(enabled = enabled) else it } }
    }

    override suspend fun delete(id: Long) {
        schedules.update { list -> list.filter { it.id != id } }
    }

    override suspend fun getAllOnce(): List<Schedule> = schedules.value

    override suspend fun deleteAll() {
        schedules.value = emptyList()
    }
}

class FakeScheduleProfileDao : ScheduleProfileDao {
    private val links = MutableStateFlow<List<ScheduleProfileLink>>(emptyList())

    override fun getAll(): Flow<List<ScheduleProfileLink>> = links

    override suspend fun getByScheduleOnce(scheduleId: Long): List<ScheduleProfileLink> =
        links.value.filter { it.scheduleId == scheduleId }

    override suspend fun insertAll(links: List<ScheduleProfileLink>) {
        this.links.update { list -> (list.filter { existing -> links.none { it == existing } } + links) }
    }

    override suspend fun deleteBySchedule(scheduleId: Long) {
        links.update { list -> list.filter { it.scheduleId != scheduleId } }
    }

    override suspend fun deleteByProfile(profileId: Long) {
        links.update { list -> list.filter { it.profileId != profileId } }
    }

    override suspend fun getAllOnce(): List<ScheduleProfileLink> = links.value

    override suspend fun deleteAll() {
        links.value = emptyList()
    }
}

class FakeNfcTagDao : NfcTagDao {
    private val tags = MutableStateFlow<List<NfcTag>>(emptyList())

    override fun getAll(): Flow<List<NfcTag>> = tags.map { it.sortedBy { t -> t.createdAt } }

    override suspend fun getByUid(uid: String): NfcTag? = tags.value.find { it.uid == uid }

    override suspend fun count(): Int = tags.value.size

    override suspend fun insert(tag: NfcTag) {
        tags.update { list -> list.filter { it.uid != tag.uid } + tag }
    }

    override suspend fun delete(uid: String) {
        tags.update { list -> list.filter { it.uid != uid } }
    }

    override suspend fun rename(uid: String, name: String) {
        tags.update { list -> list.map { if (it.uid == uid) it.copy(name = name) else it } }
    }

    override suspend fun setProfile(uid: String, profileId: Long?) {
        tags.update { list -> list.map { if (it.uid == uid) it.copy(profileId = profileId) else it } }
    }

    override suspend fun reassignProfile(oldProfileId: Long, newProfileId: Long) {
        tags.update { list ->
            list.map { if (it.profileId == oldProfileId) it.copy(profileId = newProfileId) else it }
        }
    }

    override suspend fun getByProfile(profileId: Long): List<NfcTag> =
        tags.value.filter { it.profileId == profileId }

    override suspend fun getAllOnce(): List<NfcTag> = tags.value

    override suspend fun deleteAll() {
        tags.value = emptyList()
    }
}

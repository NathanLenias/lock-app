package com.nathanb.lock.data.repository

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import app.cash.turbine.turbineScope
import com.nathanb.lock.data.model.EndReason
import com.nathanb.lock.data.model.ProfileType
import com.nathanb.lock.fake.FakeNfcTagDao
import com.nathanb.lock.fake.FakeProfileDao
import com.nathanb.lock.fake.FakeScheduleDao
import com.nathanb.lock.fake.FakeScheduleProfileDao
import com.nathanb.lock.fake.FakeSessionDao
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class LockRepositoryTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var profileDao: FakeProfileDao
    private lateinit var sessionDao: FakeSessionDao
    private lateinit var nfcTagDao: FakeNfcTagDao
    private lateinit var scheduleDao: FakeScheduleDao
    private lateinit var scheduleProfileDao: FakeScheduleProfileDao
    private lateinit var testDataStore: androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>
    private lateinit var repository: LockRepository

    @Before
    fun setup() {
        profileDao = FakeProfileDao()
        sessionDao = FakeSessionDao()
        nfcTagDao = FakeNfcTagDao()
        scheduleDao = FakeScheduleDao()
        scheduleProfileDao = FakeScheduleProfileDao()

        testDataStore = PreferenceDataStoreFactory.create(
            scope = testScope,
            produceFile = { tmpFolder.newFile("test_prefs.preferences_pb") },
        )

        repository = LockRepository(
            profileDao = profileDao,
            sessionDao = sessionDao,
            nfcTagDao = nfcTagDao,
            scheduleDao = scheduleDao,
            scheduleProfileDao = scheduleProfileDao,
            dataStore = testDataStore,
            ioDispatcher = testDispatcher,
        )
    }

    @After
    fun teardown() {
        repository.close()
    }

    // --- Lock session lifecycle ---

    @Test
    fun `startLockSession sets locked state with correct fields`() = testScope.runTest {
        val profileId = profileDao.insert(
            com.nathanb.lock.data.model.Profile(name = "Test", blockedPackages = listOf("com.test"))
        )

        repository.startLockSession(profileId)

        val state = repository.getLockState()
        assertTrue(state.isLocked)
        assertNotNull(state.sessionStartTime)
        assertEquals(profileId, state.activeProfileId)
    }

    @Test
    fun `endLockSession clears locked state`() = testScope.runTest {
        val profileId = profileDao.insert(
            com.nathanb.lock.data.model.Profile(name = "Test", blockedPackages = listOf("com.test"))
        )
        repository.startLockSession(profileId)

        repository.endLockSession(EndReason.NFC.value)

        val state = repository.getLockState()
        assertFalse(state.isLocked)
        assertNull(state.sessionStartTime)
        assertNull(state.activeProfileId)
    }

    @Test
    fun `endLockSession records end time and reason in session`() = testScope.runTest {
        val profileId = profileDao.insert(
            com.nathanb.lock.data.model.Profile(name = "Test", blockedPackages = emptyList())
        )
        repository.startLockSession(profileId)

        repository.endLockSession(EndReason.TIMEOUT.value)

        val sessions = sessionDao.getAllOnce()
        assertEquals(1, sessions.size)
        assertNotNull(sessions[0].endTime)
        assertEquals(EndReason.TIMEOUT.value, sessions[0].endReason)
    }

    @Test
    fun `toggle lock-unlock-lock creates two sessions`() = testScope.runTest {
        val profileId = profileDao.insert(
            com.nathanb.lock.data.model.Profile(name = "Test", blockedPackages = emptyList())
        )

        repository.startLockSession(profileId)
        repository.endLockSession(EndReason.NFC.value)
        repository.startLockSession(profileId)
        repository.endLockSession(EndReason.NFC.value)

        val sessions = sessionDao.getAllOnce()
        assertEquals(2, sessions.size)
        assertTrue(sessions.all { it.endTime != null })
    }

    // --- Emergency unlock ---

    @Test
    fun `useEmergencyUnlock decrements counter`() = testScope.runTest {
        val profileId = profileDao.insert(
            com.nathanb.lock.data.model.Profile(name = "Test", blockedPackages = emptyList())
        )
        repository.startLockSession(profileId)

        val before = repository.getLockState().emergencyUnlocksRemaining
        repository.useEmergencyUnlock()
        val after = repository.getLockState().emergencyUnlocksRemaining

        assertEquals(before - 1, after)
    }

    @Test
    fun `useEmergencyUnlock does nothing when counter is zero`() = testScope.runTest {
        val profileId = profileDao.insert(
            com.nathanb.lock.data.model.Profile(name = "Test", blockedPackages = emptyList())
        )
        repository.startLockSession(profileId)

        // Use all emergency unlocks
        val initial = repository.getLockState().emergencyUnlocksRemaining
        repeat(initial) { repository.useEmergencyUnlock() }
        assertEquals(0, repository.getLockState().emergencyUnlocksRemaining)

        // Try once more — should stay at 0
        repository.useEmergencyUnlock()
        assertEquals(0, repository.getLockState().emergencyUnlocksRemaining)
    }

    @Test
    fun `emergency unlocks reset on new session`() = testScope.runTest {
        val profileId = profileDao.insert(
            com.nathanb.lock.data.model.Profile(name = "Test", blockedPackages = emptyList())
        )

        repository.startLockSession(profileId)
        repository.useEmergencyUnlock()
        repository.endLockSession(EndReason.NFC.value)

        // New session should reset emergency unlocks
        repository.startLockSession(profileId)
        val state = repository.getLockState()
        assertEquals(2, state.emergencyUnlocksRemaining) // default value
    }

    // --- Emergency pause ---

    @Test
    fun `setEmergencyPause toggles pause state`() = testScope.runTest {
        assertFalse(repository.emergencyPause.value)

        repository.setEmergencyPause(true)
        assertTrue(repository.emergencyPause.value)

        repository.setEmergencyPause(false)
        assertFalse(repository.emergencyPause.value)
    }

    // --- NFC tags ---

    @Test
    fun `addNfcTag and findNfcTag work correctly`() = testScope.runTest {
        repository.addNfcTag("ABC123", "Mon tag")

        val tag = repository.findNfcTag("ABC123")
        assertNotNull(tag)
        assertEquals("Mon tag", tag!!.name)
    }

    @Test
    fun `removeNfcTag deletes tag`() = testScope.runTest {
        repository.addNfcTag("ABC123", "Mon tag")
        repository.removeNfcTag("ABC123")

        assertNull(repository.findNfcTag("ABC123"))
        assertFalse(repository.hasAnyNfcTag())
    }

    @Test
    fun `renameNfcTag updates name`() = testScope.runTest {
        repository.addNfcTag("ABC123", "Ancien")
        repository.renameNfcTag("ABC123", "Nouveau")

        assertEquals("Nouveau", repository.findNfcTag("ABC123")!!.name)
    }

    @Test
    fun `hasAnyNfcTag returns correct value`() = testScope.runTest {
        assertFalse(repository.hasAnyNfcTag())
        repository.addNfcTag("ABC", "Tag")
        assertTrue(repository.hasAnyNfcTag())
    }

    // --- Profiles ---

    @Test
    fun `createProfile and getProfile work correctly`() = testScope.runTest {
        val id = repository.createProfile("Focus", listOf("com.instagram", "com.twitter"))

        val profile = repository.getProfile(id)
        assertNotNull(profile)
        assertEquals("Focus", profile!!.name)
        assertEquals(2, profile.blockedPackages.size)
    }

    @Test
    fun `updateProfile updates blocked packages`() = testScope.runTest {
        val id = repository.createProfile("Focus", listOf("com.instagram"))
        val profile = repository.getProfile(id)!!

        repository.updateProfile(profile.copy(blockedPackages = listOf("com.instagram", "com.tiktok")))

        val updated = repository.getProfile(id)!!
        assertEquals(2, updated.blockedPackages.size)
        assertTrue(updated.blockedPackages.contains("com.tiktok"))
    }

    // --- Typed profiles & default ---

    @Test
    fun `first created profile becomes default`() = testScope.runTest {
        val id = repository.createProfile("Focus", emptyList())
        assertTrue(repository.getProfile(id)!!.isDefault)
        assertEquals(id, repository.getDefaultProfile()!!.id)
    }

    @Test
    fun `second created profile is not default`() = testScope.runTest {
        repository.createProfile("Focus", emptyList())
        val second = repository.createProfile("Sleep", emptyList())
        assertFalse(repository.getProfile(second)!!.isDefault)
    }

    @Test
    fun `no-escape profile carries type and duration`() = testScope.runTest {
        repository.createProfile("Focus", emptyList())
        val se = repository.createProfile(
            "Stop Scroll", emptyList(), ProfileType.NO_ESCAPE, durationMs = 15 * 60_000L,
        )
        val profile = repository.getProfile(se)!!
        assertEquals(ProfileType.NO_ESCAPE.value, profile.type)
        assertEquals(15 * 60_000L, profile.durationMs)
        assertFalse(profile.isDefault)
    }

    @Test
    fun `setDefaultProfile moves default and clears the previous one`() = testScope.runTest {
        val a = repository.createProfile("A", emptyList())
        val b = repository.createProfile("B", emptyList())

        repository.setDefaultProfile(b)

        assertFalse(repository.getProfile(a)!!.isDefault)
        assertTrue(repository.getProfile(b)!!.isDefault)
    }

    @Test
    fun `setDefaultProfile ignores no-escape targets`() = testScope.runTest {
        val std = repository.createProfile("A", emptyList())
        val se = repository.createProfile("SE", emptyList(), ProfileType.NO_ESCAPE, 600_000L)

        repository.setDefaultProfile(se)

        assertTrue(repository.getProfile(std)!!.isDefault)
        assertFalse(repository.getProfile(se)!!.isDefault)
    }

    @Test
    fun `addNfcTag stores profileId`() = testScope.runTest {
        val p = repository.createProfile("A", emptyList())
        repository.addNfcTag("UID1", "Tag", p)
        assertEquals(p, repository.findNfcTag("UID1")!!.profileId)
    }

    @Test
    fun `deleteProfile removes profile`() = testScope.runTest {
        val def = repository.createProfile("Default", emptyList())
        val id = repository.createProfile("Focus", emptyList())
        val profile = repository.getProfile(id)!!

        repository.deleteProfile(profile)

        assertNull(repository.getProfile(id))
    }

    @Test
    fun `deleteProfile reassigns its tags to the default profile`() = testScope.runTest {
        val def = repository.createProfile("Default", emptyList())
        val other = repository.createProfile("Other", emptyList())
        repository.addNfcTag("UID1", "Tag", other)

        val result = repository.deleteProfile(repository.getProfile(other)!!)

        assertEquals(LockRepository.DeleteProfileResult.Success, result)
        assertNull(repository.getProfile(other))
        assertEquals(def, repository.findNfcTag("UID1")!!.profileId)
    }

    @Test
    fun `deleteProfile refuses the default profile`() = testScope.runTest {
        val def = repository.createProfile("Default", emptyList())
        val result = repository.deleteProfile(repository.getProfile(def)!!)
        assertEquals(LockRepository.DeleteProfileResult.BlockedDefault, result)
        assertNotNull(repository.getProfile(def))
    }

    @Test
    fun `deleteProfile refuses while a session is active`() = testScope.runTest {
        val def = repository.createProfile("Default", emptyList())
        val other = repository.createProfile("Other", emptyList())
        repository.startLockSession(def)

        val result = repository.deleteProfile(repository.getProfile(other)!!)

        assertEquals(LockRepository.DeleteProfileResult.BlockedActiveSession, result)
        assertNotNull(repository.getProfile(other))
    }

    // --- No-escape session ---

    @Test
    fun `no-escape session has zero emergency unlocks and records duration`() = testScope.runTest {
        val se = repository.createProfile(
            "SE", emptyList(), ProfileType.NO_ESCAPE, durationMs = 10 * 60_000L,
        )
        repository.startLockSession(se)

        val state = repository.getLockState()
        assertTrue(state.isNoEscape)
        assertEquals(10 * 60_000L, state.lockDurationMs)
        assertEquals(0, state.emergencyUnlocksRemaining)
    }

    @Test
    fun `standard session keeps max emergency unlocks and no duration`() = testScope.runTest {
        val std = repository.createProfile("Std", emptyList())
        repository.startLockSession(std)

        val state = repository.getLockState()
        assertFalse(state.isNoEscape)
        assertNull(state.lockDurationMs)
        assertEquals(2, state.emergencyUnlocksRemaining)
    }

    @Test
    fun `standard session after no-escape restores emergency unlocks`() = testScope.runTest {
        val std = repository.createProfile("Std", emptyList())
        val se = repository.createProfile("SE", emptyList(), ProfileType.NO_ESCAPE, 600_000L)

        repository.startLockSession(se)
        repository.endLockSession(EndReason.TIMEOUT.value)
        repository.startLockSession(std)

        assertEquals(2, repository.getLockState().emergencyUnlocksRemaining)
    }

    @Test
    fun `setProfileDuration updates only the duration`() = testScope.runTest {
        val se = repository.createProfile("SE", listOf("com.a"), ProfileType.NO_ESCAPE, 600_000L)
        repository.setProfileDuration(se, 20 * 60_000L)
        val p = repository.getProfile(se)!!
        assertEquals(20 * 60_000L, p.durationMs)
        assertEquals(listOf("com.a"), p.blockedPackages)
    }

    // --- Backup restore remapping ---

    @Test
    fun `restoreBackupData remaps tag and session profileIds to new ids`() = testScope.runTest {
        val data = com.nathanb.lock.data.backup.BackupData(
            profiles = listOf(
                com.nathanb.lock.data.model.Profile(
                    id = 7, name = "Std", blockedPackages = listOf("com.a"),
                    type = "standard", isDefault = true, durationMs = null,
                ),
                com.nathanb.lock.data.model.Profile(
                    id = 9, name = "SE", blockedPackages = listOf("com.b"),
                    type = "no_escape", isDefault = false, durationMs = 600_000L,
                ),
            ),
            nfcTags = listOf(
                com.nathanb.lock.data.model.NfcTag(uid = "U1", name = "T1", profileId = 7),
                com.nathanb.lock.data.model.NfcTag(uid = "U2", name = "T2", profileId = 9),
            ),
            sessions = listOf(
                com.nathanb.lock.data.model.Session(profileId = 9, startTime = 1000, endTime = 2000, endReason = "timeout"),
            ),
        )

        repository.restoreBackupData(data)

        val profiles = profileDao.getAllOnce()
        val std = profiles.first { it.name == "Std" }
        val se = profiles.first { it.name == "SE" }
        assertEquals(std.id, repository.findNfcTag("U1")!!.profileId)
        assertEquals(se.id, repository.findNfcTag("U2")!!.profileId)
        assertEquals(se.id, sessionDao.getAllOnce().single().profileId)
    }

    @Test
    fun `restoreBackupData guarantees exactly one standard default`() = testScope.runTest {
        val data = com.nathanb.lock.data.backup.BackupData(
            profiles = listOf(
                com.nathanb.lock.data.model.Profile(id = 1, name = "A", blockedPackages = emptyList()),
                com.nathanb.lock.data.model.Profile(id = 2, name = "B", blockedPackages = emptyList()),
            ),
            nfcTags = emptyList(),
            sessions = emptyList(),
        )

        repository.restoreBackupData(data)

        val defaults = profileDao.getAllOnce().filter { it.isDefault }
        assertEquals(1, defaults.size)
    }

    @Test
    fun `restoreBackupData binds v1 tags without profileId to the default`() = testScope.runTest {
        val data = com.nathanb.lock.data.backup.BackupData(
            profiles = listOf(
                com.nathanb.lock.data.model.Profile(id = 1, name = "A", blockedPackages = emptyList()),
            ),
            nfcTags = listOf(
                com.nathanb.lock.data.model.NfcTag(uid = "U1", name = "T1", profileId = null),
            ),
            sessions = emptyList(),
        )

        repository.restoreBackupData(data)

        val def = repository.getDefaultProfile()!!
        assertEquals(def.id, repository.findNfcTag("U1")!!.profileId)
    }

    // --- Schedules ---

    @Test
    fun `createSchedule links only standard profiles`() = testScope.runTest {
        val stdId = profileDao.insert(
            com.nathanb.lock.data.model.Profile(name = "Std", blockedPackages = listOf("com.a"))
        )
        val neId = profileDao.insert(
            com.nathanb.lock.data.model.Profile(
                name = "SE", blockedPackages = listOf("com.b"),
                type = "no_escape", durationMs = 600_000L,
            )
        )

        val scheduleId = repository.createSchedule(
            daysOfWeek = 0b0011111, startMinuteOfDay = 540, endMinuteOfDay = 1020,
            profileIds = listOf(stdId, neId, 999L),
        )

        val links = scheduleProfileDao.getByScheduleOnce(scheduleId)
        assertEquals(listOf(stdId), links.map { it.profileId })
        assertTrue(scheduleDao.getById(scheduleId)!!.enabled)
    }

    @Test
    fun `updateSchedule replaces links`() = testScope.runTest {
        val p1 = profileDao.insert(com.nathanb.lock.data.model.Profile(name = "A", blockedPackages = emptyList()))
        val p2 = profileDao.insert(com.nathanb.lock.data.model.Profile(name = "B", blockedPackages = emptyList()))
        val id = repository.createSchedule(1, 0, 60, listOf(p1))

        repository.updateSchedule(scheduleDao.getById(id)!!.copy(startMinuteOfDay = 30), listOf(p2))

        assertEquals(30, scheduleDao.getById(id)!!.startMinuteOfDay)
        assertEquals(listOf(p2), scheduleProfileDao.getByScheduleOnce(id).map { it.profileId })
    }

    @Test
    fun `deleteSchedule removes schedule and its links`() = testScope.runTest {
        val p1 = profileDao.insert(com.nathanb.lock.data.model.Profile(name = "A", blockedPackages = emptyList()))
        val id = repository.createSchedule(1, 0, 60, listOf(p1))

        repository.deleteSchedule(id)

        assertNull(scheduleDao.getById(id))
        assertTrue(scheduleProfileDao.getAllOnce().isEmpty())
    }

    @Test
    fun `deleteProfile removes its schedule links but keeps the schedule`() = testScope.runTest {
        val def = profileDao.insert(
            com.nathanb.lock.data.model.Profile(name = "Def", blockedPackages = emptyList(), isDefault = true)
        )
        val other = profileDao.insert(com.nathanb.lock.data.model.Profile(name = "Other", blockedPackages = emptyList()))
        val id = repository.createSchedule(1, 0, 60, listOf(other, def))

        val result = repository.deleteProfile(profileDao.getById(other)!!)

        assertEquals(LockRepository.DeleteProfileResult.Success, result)
        assertNotNull(scheduleDao.getById(id))
        assertEquals(listOf(def), scheduleProfileDao.getByScheduleOnce(id).map { it.profileId })
    }

    @Test
    fun `restoreBackupData remaps schedule links and drops orphans`() = testScope.runTest {
        val data = com.nathanb.lock.data.backup.BackupData(
            profiles = listOf(
                com.nathanb.lock.data.model.Profile(id = 7, name = "Std", blockedPackages = listOf("com.a"), isDefault = true),
            ),
            nfcTags = emptyList(),
            sessions = emptyList(),
            schedules = listOf(
                com.nathanb.lock.data.model.Schedule(
                    id = 3, daysOfWeek = 0b1111111, startMinuteOfDay = 1320, endMinuteOfDay = 360,
                ),
            ),
            scheduleLinks = listOf(
                com.nathanb.lock.data.model.ScheduleProfileLink(scheduleId = 3, profileId = 7),
                com.nathanb.lock.data.model.ScheduleProfileLink(scheduleId = 3, profileId = 42), // orphan
            ),
        )

        repository.restoreBackupData(data)

        val schedule = scheduleDao.getAllOnce().single()
        assertEquals(1320, schedule.startMinuteOfDay)
        val newProfileId = profileDao.getAllOnce().single().id
        assertEquals(
            listOf(newProfileId),
            scheduleProfileDao.getByScheduleOnce(schedule.id).map { it.profileId },
        )
    }

    @Test
    fun `restoreBackupData from pre-schedule backup leaves schedules empty`() = testScope.runTest {
        repository.createSchedule(1, 0, 60, emptyList())

        val data = com.nathanb.lock.data.backup.BackupData(
            profiles = listOf(com.nathanb.lock.data.model.Profile(id = 1, name = "A", blockedPackages = emptyList())),
            nfcTags = emptyList(),
            sessions = emptyList(),
        )
        repository.restoreBackupData(data)

        assertTrue(scheduleDao.getAllOnce().isEmpty())
        assertTrue(scheduleProfileDao.getAllOnce().isEmpty())
    }

    // --- Scheduled sessions ---

    @Test
    fun `startScheduledSession sets schedule origin and blocks the union`() = testScope.runTest {
        val profileId = profileDao.insert(
            com.nathanb.lock.data.model.Profile(name = "A", blockedPackages = listOf("com.a"))
        )

        repository.startScheduledSession(profileId, setOf("com.a", "com.b"))

        val state = repository.getLockState()
        assertTrue(state.isLocked)
        assertTrue(state.isScheduleOrigin)
        assertFalse(state.isNoEscape)
        assertNull(state.lockDurationMs)
        assertEquals(setOf("com.a", "com.b"), repository.blockedPackages.value)
    }

    @Test
    fun `updateScheduledPackages refreshes the blocked union`() = testScope.runTest {
        val profileId = profileDao.insert(
            com.nathanb.lock.data.model.Profile(name = "A", blockedPackages = listOf("com.a"))
        )
        repository.startScheduledSession(profileId, setOf("com.a"))

        repository.updateScheduledPackages(setOf("com.a", "com.c"))

        assertEquals(setOf("com.a", "com.c"), repository.blockedPackages.value)
    }

    @Test
    fun `user-initiated end consumes covering windows`() = testScope.runTest {
        val profileId = profileDao.insert(
            com.nathanb.lock.data.model.Profile(name = "A", blockedPackages = listOf("com.a"))
        )
        // All-days 00:00 -> 00:00 defensive 24h window: always covering.
        val scheduleId = repository.createSchedule(0b1111111, 0, 0, listOf(profileId))
        repository.startScheduledSession(profileId, setOf("com.a"))

        repository.endLockSession(EndReason.NFC.value)

        val state = repository.getLockState()
        assertFalse(state.isLocked)
        assertFalse(state.isScheduleOrigin)
        assertTrue(repository.blockedPackages.value.isEmpty())
        val consumed = repository.getConsumedWindowKeys()
        assertEquals(1, consumed.size)
        assertTrue(consumed.single().startsWith("$scheduleId:"))
    }

    @Test
    fun `system end does not consume windows`() = testScope.runTest {
        val profileId = profileDao.insert(
            com.nathanb.lock.data.model.Profile(name = "A", blockedPackages = listOf("com.a"))
        )
        repository.createSchedule(0b1111111, 0, 0, listOf(profileId))
        repository.startScheduledSession(profileId, setOf("com.a"))

        repository.endLockSession(EndReason.SCHEDULE.value)

        assertTrue(repository.getConsumedWindowKeys().isEmpty())
    }

    @Test
    fun `onSessionEnded hook fires on every end`() = testScope.runTest {
        var invoked = 0
        repository.onSessionEnded = { invoked++ }
        val profileId = profileDao.insert(
            com.nathanb.lock.data.model.Profile(name = "A", blockedPackages = listOf("com.a"))
        )
        repository.startLockSession(profileId)

        repository.endLockSession(EndReason.TIMEOUT.value)

        assertEquals(1, invoked)
    }

    @Test
    fun `updateSchedule with a past start keeps today's consumption`() = testScope.runTest {
        val profileId = profileDao.insert(
            com.nathanb.lock.data.model.Profile(name = "A", blockedPackages = listOf("com.a"))
        )
        // 00:00 start: never strictly in the future, so consumption must survive the edit.
        val id = repository.createSchedule(0b1111111, 0, 0, listOf(profileId))
        repository.startScheduledSession(profileId, setOf("com.a"))
        repository.endLockSession(EndReason.NFC.value)
        assertEquals(1, repository.getConsumedWindowKeys().size)

        repository.updateSchedule(scheduleDao.getById(id)!!.copy(startMinuteOfDay = 0), listOf(profileId))

        assertEquals(1, repository.getConsumedWindowKeys().size)
    }

    @Test
    fun `consumed keys can be pruned via setConsumedWindowKeys`() = testScope.runTest {
        val profileId = profileDao.insert(
            com.nathanb.lock.data.model.Profile(name = "A", blockedPackages = listOf("com.a"))
        )
        repository.createSchedule(0b1111111, 0, 0, listOf(profileId))
        repository.startScheduledSession(profileId, setOf("com.a"))
        repository.endLockSession(EndReason.MANUAL.value)
        assertEquals(1, repository.getConsumedWindowKeys().size)

        repository.setConsumedWindowKeys(emptySet())

        assertTrue(repository.getConsumedWindowKeys().isEmpty())
    }

    // --- Setup ---

    @Test
    fun `completeSetup marks setup as completed`() = testScope.runTest {
        assertFalse(repository.isSetupCompleted.first())

        repository.completeSetup(1)

        assertTrue(repository.isSetupCompleted.first())
    }

    // --- Changelog version seeding ---

    @Test
    fun `seedLastSeenVersionIfFreshInstall records current version on fresh install`() = testScope.runTest {
        repository.seedLastSeenVersionIfFreshInstall(4)

        assertEquals(4, repository.lastSeenVersionCode.first())
    }

    @Test
    fun `seedLastSeenVersionIfFreshInstall leaves pre-changelog updaters at 0 so they see the changelog`() = testScope.runTest {
        // Existing user: setup completed on a version that predates the changelog feature
        testDataStore.edit { it[booleanPreferencesKey("setup_completed")] = true }

        repository.seedLastSeenVersionIfFreshInstall(4)

        assertEquals(0, repository.lastSeenVersionCode.first())
    }

    @Test
    fun `seedLastSeenVersionIfFreshInstall never overwrites a recorded version`() = testScope.runTest {
        repository.setLastSeenVersionCode(3)

        repository.seedLastSeenVersionIfFreshInstall(4)

        assertEquals(3, repository.lastSeenVersionCode.first())
    }

    // --- Blocked packages sync ---

    @Test
    fun `blockedPackages updates when lock session starts`() = testScope.runTest {
        val profileId = profileDao.insert(
            com.nathanb.lock.data.model.Profile(
                name = "Test",
                blockedPackages = listOf("com.instagram", "com.twitter"),
            )
        )

        assertTrue(repository.blockedPackages.value.isEmpty())

        repository.startLockSession(profileId)

        // Collector runs on the injected test dispatcher, so this is now deterministic.
        testScope.testScheduler.advanceUntilIdle()

        assertEquals(setOf("com.instagram", "com.twitter"), repository.blockedPackages.value)
    }

    @Test
    fun `blockedPackages clears when session ends`() = testScope.runTest {
        val profileId = profileDao.insert(
            com.nathanb.lock.data.model.Profile(
                name = "Test",
                blockedPackages = listOf("com.instagram"),
            )
        )
        repository.startLockSession(profileId)
        testScope.testScheduler.advanceUntilIdle()

        repository.endLockSession(EndReason.NFC.value)
        testScope.testScheduler.advanceUntilIdle()

        assertTrue(repository.blockedPackages.value.isEmpty())
    }

    // --- Session stats ---

    @Test
    fun `completedSessionCount reflects finished sessions`() = testScope.runTest {
        val profileId = profileDao.insert(
            com.nathanb.lock.data.model.Profile(name = "Test", blockedPackages = emptyList())
        )

        assertEquals(0, repository.completedSessionCount.first())

        repository.startLockSession(profileId)
        repository.endLockSession(EndReason.NFC.value)

        assertEquals(1, repository.completedSessionCount.first())
    }

    // --- Session settings ---

    @Test
    fun `session settings persist and read back correctly`() = testScope.runTest {
        repository.setGracePeriodMs(60_000L)
        assertEquals(60_000L, repository.gracePeriodMs.first())

        repository.setMaxEmergencyUnlocks(5)
        assertEquals(5, repository.maxEmergencyUnlocks.first())

        repository.setEmergencyUnlockDurationMs(15 * 60_000L)
        assertEquals(15 * 60_000L, repository.emergencyUnlockDurationMs.first())

        repository.setTimeoutDurationMs(3 * 60 * 60_000L)
        assertEquals(3 * 60 * 60_000L, repository.timeoutDurationMs.first())
    }

    // --- Theme ---

    @Test
    fun `theme mode persists correctly`() = testScope.runTest {
        assertEquals(com.nathanb.lock.ui.theme.ThemeMode.SYSTEM, repository.themeMode.first())

        repository.setThemeMode(com.nathanb.lock.ui.theme.ThemeMode.DARK)
        assertEquals(com.nathanb.lock.ui.theme.ThemeMode.DARK, repository.themeMode.first())

        repository.setThemeMode(com.nathanb.lock.ui.theme.ThemeMode.LIGHT)
        assertEquals(com.nathanb.lock.ui.theme.ThemeMode.LIGHT, repository.themeMode.first())
    }
}

package com.nathanb.lock.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import android.net.Uri
import androidx.room.withTransaction
import com.nathanb.lock.data.backup.BackupManager
import com.nathanb.lock.data.database.LockDatabase
import com.nathanb.lock.data.database.NfcTagDao
import com.nathanb.lock.data.database.ProfileDao
import com.nathanb.lock.data.database.ScheduleDao
import com.nathanb.lock.data.database.ScheduleProfileDao
import com.nathanb.lock.data.database.SessionDao
import com.nathanb.lock.data.model.LockState
import com.nathanb.lock.data.model.NfcTag
import com.nathanb.lock.data.model.Profile
import com.nathanb.lock.data.model.ProfileType
import com.nathanb.lock.data.model.Schedule
import com.nathanb.lock.data.model.ScheduleProfileLink
import com.nathanb.lock.data.model.Session
import com.nathanb.lock.ui.theme.ThemeMode
import com.nathanb.lock.util.Constants
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

private val Context.dataStore by preferencesDataStore(name = "lock_prefs")

class LockRepository(
    private val context: Context? = null,
    private val profileDao: ProfileDao,
    private val sessionDao: SessionDao,
    private val nfcTagDao: NfcTagDao,
    private val scheduleDao: ScheduleDao,
    private val scheduleProfileDao: ScheduleProfileDao,
    private val database: LockDatabase? = null,
    private val dataStore: DataStore<Preferences> = context!!.dataStore,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)

    // DataStore keys
    private object Keys {
        val IS_LOCKED = booleanPreferencesKey("is_locked")
        val SESSION_START_TIME = longPreferencesKey("session_start_time")
        val ACTIVE_PROFILE_ID = longPreferencesKey("active_profile_id")
        val EMERGENCY_UNLOCKS = intPreferencesKey("emergency_unlocks_remaining")
        val SETUP_COMPLETED = booleanPreferencesKey("setup_completed")
        val ACTIVE_SESSION_ID = longPreferencesKey("active_session_id")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        // Session settings
        val GRACE_PERIOD_MS = longPreferencesKey("grace_period_ms")
        val MAX_EMERGENCY_UNLOCKS_SETTING = intPreferencesKey("max_emergency_unlocks")
        val EMERGENCY_UNLOCK_DURATION_MS = longPreferencesKey("emergency_unlock_duration_ms")
        val TIMEOUT_DURATION_MS = longPreferencesKey("timeout_duration_ms")
        val IS_MANUAL_MODE = booleanPreferencesKey("is_manual_mode")
        val LOCK_DURATION_MS = longPreferencesKey("lock_duration_ms")
        // In-app prompts
        val SUPPORT_PROMPT_STAGE = intPreferencesKey("support_prompt_stage")
        val LAST_SEEN_VERSION = intPreferencesKey("last_seen_version_code")
        val IS_NO_ESCAPE = booleanPreferencesKey("is_no_escape")
    }

    // Blocked packages — observed by the Accessibility Service
    private val _blockedPackages = MutableStateFlow<Set<String>>(emptySet())
    val blockedPackages: StateFlow<Set<String>> = _blockedPackages.asStateFlow()

    // Emergency pause — when true, blocking is temporarily suspended
    private val _emergencyPause = MutableStateFlow(false)
    val emergencyPause: StateFlow<Boolean> = _emergencyPause.asStateFlow()

    fun setEmergencyPause(paused: Boolean) {
        _emergencyPause.value = paused
    }

    // Lock state as Flow
    val lockStateFlow: Flow<LockState> = dataStore.data.map { prefs ->
        LockState(
            isLocked = prefs[Keys.IS_LOCKED] ?: false,
            sessionStartTime = prefs[Keys.SESSION_START_TIME],
            activeProfileId = prefs[Keys.ACTIVE_PROFILE_ID],
            emergencyUnlocksRemaining = prefs[Keys.EMERGENCY_UNLOCKS]
                ?: (prefs[Keys.MAX_EMERGENCY_UNLOCKS_SETTING] ?: Constants.DEFAULT_MAX_EMERGENCY_UNLOCKS),
            isManualMode = prefs[Keys.IS_MANUAL_MODE] ?: false,
            lockDurationMs = prefs[Keys.LOCK_DURATION_MS],
            isNoEscape = prefs[Keys.IS_NO_ESCAPE] ?: false,
        )
    }

    val isSetupCompleted: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.SETUP_COMPLETED] ?: false
    }

    // NFC tags (multi-tag support)
    val nfcTags: Flow<List<NfcTag>> = nfcTagDao.getAll()

    // Profiles
    val profiles: Flow<List<Profile>> = profileDao.getAll()

    init {
        // Keep blocked packages in sync with current state
        scope.launch {
            lockStateFlow.collect { state ->
                if (state.isLocked && state.activeProfileId != null) {
                    val profile = profileDao.getById(state.activeProfileId)
                    _blockedPackages.value = profile?.blockedPackages?.toSet() ?: emptySet()
                } else {
                    _blockedPackages.value = emptySet()
                }
            }
        }
        // Note: auto-disable of manual mode when NFC tag is added
        // is handled by the ViewModel (needs access to ForegroundService + timers)
    }

    suspend fun getLockState(): LockState {
        return lockStateFlow.first()
    }

    suspend fun setManualMode(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.IS_MANUAL_MODE] = enabled
        }
    }

    suspend fun startLockSession(profileId: Long) {
        val now = System.currentTimeMillis()
        val profile = profileDao.getById(profileId)
        val isNoEscape = ProfileType.fromValue(profile?.type) == ProfileType.NO_ESCAPE
        val durationMs = if (isNoEscape) profile?.durationMs else null
        val sessionId = sessionDao.insert(
            Session(profileId = profileId, startTime = now)
        )
        dataStore.edit { prefs ->
            prefs[Keys.IS_LOCKED] = true
            prefs[Keys.SESSION_START_TIME] = now
            prefs[Keys.ACTIVE_PROFILE_ID] = profileId
            prefs[Keys.ACTIVE_SESSION_ID] = sessionId
            prefs[Keys.IS_NO_ESCAPE] = isNoEscape
            if (durationMs != null) prefs[Keys.LOCK_DURATION_MS] = durationMs
            else prefs.remove(Keys.LOCK_DURATION_MS)
            // No-escape: no emergency unlocks. Standard: reset to max (per-session budget).
            prefs[Keys.EMERGENCY_UNLOCKS] = if (isNoEscape) {
                0
            } else {
                prefs[Keys.MAX_EMERGENCY_UNLOCKS_SETTING] ?: Constants.DEFAULT_MAX_EMERGENCY_UNLOCKS
            }
        }
    }

    suspend fun endLockSession(reason: String) {
        val now = System.currentTimeMillis()
        val prefs = dataStore.data.first()
        val sessionId = prefs[Keys.ACTIVE_SESSION_ID]
        if (sessionId != null) {
            sessionDao.endSession(sessionId, now, reason)
        }
        dataStore.edit { it ->
            it[Keys.IS_LOCKED] = false
            it.remove(Keys.SESSION_START_TIME)
            it.remove(Keys.ACTIVE_PROFILE_ID)
            it.remove(Keys.ACTIVE_SESSION_ID)
            it.remove(Keys.IS_NO_ESCAPE)
            it.remove(Keys.LOCK_DURATION_MS)
        }
    }

    suspend fun useEmergencyUnlock() {
        val state = getLockState()
        if (state.emergencyUnlocksRemaining > 0) {
            dataStore.edit { prefs ->
                prefs[Keys.EMERGENCY_UNLOCKS] = state.emergencyUnlocksRemaining - 1
            }
        }
    }

    // NFC tags
    suspend fun addNfcTag(uid: String, name: String, profileId: Long? = null) {
        nfcTagDao.insert(NfcTag(uid = uid, name = name, profileId = profileId))
    }

    suspend fun removeNfcTag(uid: String) {
        nfcTagDao.delete(uid)
    }

    suspend fun renameNfcTag(uid: String, name: String) {
        nfcTagDao.rename(uid, name)
    }

    suspend fun findNfcTag(uid: String): NfcTag? {
        return nfcTagDao.getByUid(uid)
    }

    suspend fun hasAnyNfcTag(): Boolean {
        return nfcTagDao.count() > 0
    }

    // Setup
    suspend fun completeSetup(currentVersionCode: Int) {
        dataStore.edit { prefs ->
            prefs[Keys.SETUP_COMPLETED] = true
            // New users start "caught up" so they don't see the changelog for the version they installed.
            if (prefs[Keys.LAST_SEEN_VERSION] == null) prefs[Keys.LAST_SEEN_VERSION] = currentVersionCode
        }
    }

    // In-app prompts (support reminder + changelog)

    /**
     * Called once at app start, BEFORE the UI renders (the splash gate waits on it).
     * Fresh installs start "caught up": if no version was ever recorded and setup was never
     * completed, record the current version so the changelog card never shows for the
     * version the user just installed. Users updating from a pre-changelog version
     * (setup completed, no version recorded) keep the 0 default and see the changelog once.
     */
    suspend fun seedLastSeenVersionIfFreshInstall(currentVersionCode: Int) {
        dataStore.edit { prefs ->
            if (prefs[Keys.LAST_SEEN_VERSION] == null && prefs[Keys.SETUP_COMPLETED] != true) {
                prefs[Keys.LAST_SEEN_VERSION] = currentVersionCode
            }
        }
    }

    /** 0 = never shown; 1..4 = milestones already declined (3, 10, 20, 30 sessions); 5 = done/never. */
    val supportPromptStage: Flow<Int> = dataStore.data.map { it[Keys.SUPPORT_PROMPT_STAGE] ?: 0 }
        .distinctUntilChanged()

    suspend fun setSupportPromptStage(stage: Int) {
        dataStore.edit { it[Keys.SUPPORT_PROMPT_STAGE] = stage }
    }

    val lastSeenVersionCode: Flow<Int> = dataStore.data.map { it[Keys.LAST_SEEN_VERSION] ?: 0 }
        .distinctUntilChanged()

    suspend fun setLastSeenVersionCode(code: Int) {
        dataStore.edit { it[Keys.LAST_SEEN_VERSION] = code }
    }

    // Profile CRUD
    sealed interface DeleteProfileResult {
        data object Success : DeleteProfileResult
        data object BlockedDefault : DeleteProfileResult
        data object BlockedActiveSession : DeleteProfileResult
    }

    suspend fun createProfile(
        name: String,
        blockedPackages: List<String>,
        type: ProfileType = ProfileType.STANDARD,
        durationMs: Long? = null,
    ): Long {
        val isFirst = profileDao.getAllOnce().isEmpty()
        return profileDao.insert(
            Profile(
                name = name,
                blockedPackages = blockedPackages,
                type = type.value,
                // First profile (always standard via onboarding) becomes the default.
                isDefault = isFirst && type == ProfileType.STANDARD,
                durationMs = durationMs,
            )
        )
    }

    suspend fun updateProfile(profile: Profile) {
        profileDao.update(profile)
    }

    suspend fun getProfile(id: Long): Profile? {
        return profileDao.getById(id)
    }

    /** A no-escape profile can never be the default. No-op if [profileId] is no-escape. */
    suspend fun setDefaultProfile(profileId: Long) {
        val all = profileDao.getAllOnce()
        val target = all.find { it.id == profileId } ?: return
        if (ProfileType.fromValue(target.type) != ProfileType.STANDARD) return
        all.forEach { p ->
            val shouldBeDefault = p.id == profileId
            if (p.isDefault != shouldBeDefault) {
                profileDao.update(p.copy(isDefault = shouldBeDefault))
            }
        }
    }

    suspend fun getDefaultProfile(): Profile? {
        val all = profileDao.getAllOnce()
        return all.firstOrNull { it.isDefault }
            ?: all.firstOrNull { ProfileType.fromValue(it.type) == ProfileType.STANDARD }
            ?: all.firstOrNull()
    }

    suspend fun deleteProfile(profile: Profile): DeleteProfileResult {
        if (getLockState().isLocked) return DeleteProfileResult.BlockedActiveSession
        if (profile.isDefault) return DeleteProfileResult.BlockedDefault
        val default = getDefaultProfile()
        if (default != null && default.id != profile.id) {
            nfcTagDao.reassignProfile(profile.id, default.id)
        }
        // Schedules keep existing without this profile; a schedule with zero profiles is inert.
        scheduleProfileDao.deleteByProfile(profile.id)
        profileDao.delete(profile)
        return DeleteProfileResult.Success
    }

    // Schedules (recurring auto-lock windows)
    val schedules: Flow<List<Schedule>> = scheduleDao.getAll()
    val scheduleLinks: Flow<List<ScheduleProfileLink>> = scheduleProfileDao.getAll()

    /** Only STANDARD profiles can be attached to a schedule (no-escape are excluded by design). */
    private suspend fun standardProfileIds(profileIds: List<Long>): List<Long> {
        val standard = profileDao.getAllOnce()
            .filter { ProfileType.fromValue(it.type) == ProfileType.STANDARD }
            .map { it.id }
            .toSet()
        return profileIds.filter { it in standard }.distinct()
    }

    suspend fun createSchedule(
        daysOfWeek: Int,
        startMinuteOfDay: Int,
        endMinuteOfDay: Int,
        profileIds: List<Long>,
    ): Long {
        val id = scheduleDao.insert(
            Schedule(
                daysOfWeek = daysOfWeek,
                startMinuteOfDay = startMinuteOfDay,
                endMinuteOfDay = endMinuteOfDay,
            )
        )
        scheduleProfileDao.insertAll(
            standardProfileIds(profileIds).map { ScheduleProfileLink(id, it) }
        )
        return id
    }

    suspend fun updateSchedule(schedule: Schedule, profileIds: List<Long>) {
        scheduleDao.update(schedule)
        scheduleProfileDao.deleteBySchedule(schedule.id)
        scheduleProfileDao.insertAll(
            standardProfileIds(profileIds).map { ScheduleProfileLink(schedule.id, it) }
        )
    }

    suspend fun setScheduleEnabled(scheduleId: Long, enabled: Boolean) {
        scheduleDao.setEnabled(scheduleId, enabled)
    }

    suspend fun deleteSchedule(scheduleId: Long) {
        scheduleProfileDao.deleteBySchedule(scheduleId)
        scheduleDao.delete(scheduleId)
    }

    suspend fun setTagProfile(uid: String, profileId: Long?) {
        nfcTagDao.setProfile(uid, profileId)
    }

    suspend fun setProfileDuration(profileId: Long, durationMs: Long) {
        val profile = profileDao.getById(profileId) ?: return
        profileDao.update(profile.copy(durationMs = durationMs))
    }

    // Session history & stats
    val sessions: Flow<List<Session>> = sessionDao.getAll()
    val completedSessionCount: Flow<Int> = sessionDao.getCompletedCount()
    val totalBlockedMs: Flow<Long> = sessionDao.getTotalBlockedMs()
    val longestSessionMs: Flow<Long?> = sessionDao.getLongestSessionMs()

    // Backup
    suspend fun exportBackup(uri: Uri) {
        val profiles = profileDao.getAllOnce()
        val nfcTags = nfcTagDao.getAllOnce()
        val sessions = sessionDao.getAllOnce()
        val schedules = scheduleDao.getAllOnce()
        val scheduleLinks = scheduleProfileDao.getAllOnce()
        BackupManager.export(context!!, uri, profiles, nfcTags, sessions, schedules, scheduleLinks)
    }

    // Theme preference
    val themeMode: Flow<ThemeMode> = dataStore.data.map { prefs ->
        when (prefs[Keys.THEME_MODE]) {
            "LIGHT" -> ThemeMode.LIGHT
            "DARK" -> ThemeMode.DARK
            else -> ThemeMode.SYSTEM
        }
    }.distinctUntilChanged()

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { prefs ->
            prefs[Keys.THEME_MODE] = mode.name
        }
    }

    // Notification preference
    val notificationsEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.NOTIFICATIONS_ENABLED] ?: false
    }.distinctUntilChanged()

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.NOTIFICATIONS_ENABLED] = enabled
        }
    }

    // Session settings
    val gracePeriodMs: Flow<Long> = dataStore.data.map { prefs ->
        prefs[Keys.GRACE_PERIOD_MS] ?: Constants.DEFAULT_GRACE_PERIOD_MS
    }.distinctUntilChanged()

    val maxEmergencyUnlocks: Flow<Int> = dataStore.data.map { prefs ->
        prefs[Keys.MAX_EMERGENCY_UNLOCKS_SETTING] ?: Constants.DEFAULT_MAX_EMERGENCY_UNLOCKS
    }.distinctUntilChanged()

    val emergencyUnlockDurationMs: Flow<Long> = dataStore.data.map { prefs ->
        prefs[Keys.EMERGENCY_UNLOCK_DURATION_MS] ?: Constants.DEFAULT_EMERGENCY_UNLOCK_DURATION_MS
    }.distinctUntilChanged()

    val timeoutDurationMs: Flow<Long> = dataStore.data.map { prefs ->
        prefs[Keys.TIMEOUT_DURATION_MS] ?: Constants.DEFAULT_TIMEOUT_DURATION_MS
    }.distinctUntilChanged()

    suspend fun setGracePeriodMs(value: Long) {
        dataStore.edit { prefs -> prefs[Keys.GRACE_PERIOD_MS] = value }
    }

    suspend fun setMaxEmergencyUnlocks(value: Int) {
        dataStore.edit { prefs -> prefs[Keys.MAX_EMERGENCY_UNLOCKS_SETTING] = value }
    }

    suspend fun setEmergencyUnlockDurationMs(value: Long) {
        dataStore.edit { prefs -> prefs[Keys.EMERGENCY_UNLOCK_DURATION_MS] = value }
    }

    suspend fun setTimeoutDurationMs(value: Long) {
        dataStore.edit { prefs -> prefs[Keys.TIMEOUT_DURATION_MS] = value }
    }

    fun close() {
        scope.cancel()
    }

    suspend fun importBackup(uri: Uri) {
        val data = BackupManager.import(context!!, uri)
        database!!.withTransaction {
            restoreBackupData(data)
        }
    }

    /** Wipe-and-restore with id remapping. Extracted (no transaction) for unit testing. */
    internal suspend fun restoreBackupData(data: com.nathanb.lock.data.backup.BackupData) {
        scheduleProfileDao.deleteAll()
        scheduleDao.deleteAll()
        sessionDao.deleteAll()
        nfcTagDao.deleteAll()
        profileDao.deleteAll()

        // Insert profiles with fresh ids, mapping old backup id -> new id.
        val idMap = HashMap<Long, Long>()
        data.profiles.forEach { p ->
            val newId = profileDao.insert(p.copy(id = 0))
            idMap[p.id] = newId
        }

        ensureSingleDefault()
        val fallbackDefaultId = getDefaultProfile()?.id

        // Tags: remap profileId; v1 tags (null) fall back to the default profile.
        data.nfcTags.forEach { t ->
            val mapped = t.profileId?.let { idMap[it] } ?: fallbackDefaultId
            nfcTagDao.insert(t.copy(profileId = mapped))
        }

        // Sessions: remap profileId (keep originals if no mapping found).
        if (data.sessions.isNotEmpty()) {
            sessionDao.insertAll(
                data.sessions.map { s -> s.copy(id = 0, profileId = idMap[s.profileId] ?: s.profileId) }
            )
        }

        // Schedules: fresh ids, then links remapped through both maps.
        // Links whose profile didn't survive the restore are dropped (inert schedule is fine).
        val scheduleIdMap = HashMap<Long, Long>()
        data.schedules.forEach { s ->
            val newId = scheduleDao.insert(s.copy(id = 0))
            scheduleIdMap[s.id] = newId
        }
        val remappedLinks = data.scheduleLinks.mapNotNull { link ->
            val scheduleId = scheduleIdMap[link.scheduleId] ?: return@mapNotNull null
            val profileId = idMap[link.profileId] ?: return@mapNotNull null
            ScheduleProfileLink(scheduleId, profileId)
        }
        if (remappedLinks.isNotEmpty()) scheduleProfileDao.insertAll(remappedLinks)
    }

    private suspend fun ensureSingleDefault() {
        val all = profileDao.getAllOnce()
        val standards = all.filter { ProfileType.fromValue(it.type) == ProfileType.STANDARD }
        val defaults = all.filter { it.isDefault }
        val validSingle = defaults.size == 1 &&
            ProfileType.fromValue(defaults.first().type) == ProfileType.STANDARD
        if (validSingle) return
        // Reset every default flag, then promote the first standard profile.
        all.forEach { if (it.isDefault) profileDao.update(it.copy(isDefault = false)) }
        standards.firstOrNull()?.let { profileDao.update(it.copy(isDefault = true)) }
    }
}

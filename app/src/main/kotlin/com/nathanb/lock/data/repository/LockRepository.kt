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
import com.nathanb.lock.data.model.ScanBehavior
import com.nathanb.lock.data.model.ProfileType
import com.nathanb.lock.data.model.EndReason
import com.nathanb.lock.data.model.Schedule
import com.nathanb.lock.data.model.ScheduleProfileLink
import com.nathanb.lock.data.model.Session
import com.nathanb.lock.schedule.ScheduleWindowCalculator
import java.time.ZonedDateTime
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
import kotlinx.coroutines.flow.combine
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
    /** Injectable clock for schedule-window math, so tests can pin the date. */
    private val zonedNow: () -> ZonedDateTime = { ZonedDateTime.now() },
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
        // Schedules (recurring auto-lock windows)
        val SCHEDULED_PACKAGES = stringPreferencesKey("scheduled_packages")
        val IS_SCHEDULE_ORIGIN = booleanPreferencesKey("is_schedule_origin")
        val CONSUMED_WINDOWS = stringPreferencesKey("consumed_windows")
        val PAUSED_UNTIL = longPreferencesKey("schedule_paused_until")
    }

    /** Reasons that mean the USER ended the session: covering windows get consumed for the day. */
    private val consumingEndReasons = setOf(
        EndReason.NFC.value, EndReason.MANUAL.value, EndReason.CANCELLED.value,
    )

    // Newline-joined encoding (not org.json: that's a stub on the unit-test JVM).
    // Safe: package names and consumption keys never contain newlines.
    private fun encodeStringSet(values: Set<String>): String =
        values.joinToString("\n")

    private fun decodeStringSet(encoded: String?): Set<String> {
        if (encoded.isNullOrEmpty()) return emptySet()
        return encoded.split('\n').filter { it.isNotEmpty() }.toSet()
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
            isScheduleOrigin = prefs[Keys.IS_SCHEDULE_ORIGIN] ?: false,
        )
    }

    /** Union of packages blocked by the currently covering scheduled windows (empty when none). */
    private val scheduledPackagesFlow: Flow<Set<String>> = dataStore.data
        .map { prefs -> decodeStringSet(prefs[Keys.SCHEDULED_PACKAGES]) }
        .distinctUntilChanged()

    val isSetupCompleted: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.SETUP_COMPLETED] ?: false
    }

    // NFC tags (multi-tag support)
    val nfcTags: Flow<List<NfcTag>> = nfcTagDao.getAll()

    // Profiles
    val profiles: Flow<List<Profile>> = profileDao.getAll()

    init {
        // Keep blocked packages in sync with current state:
        // active profile's packages ∪ scheduled-window packages (both empty when unlocked).
        scope.launch {
            combine(lockStateFlow, scheduledPackagesFlow) { state, scheduled ->
                state to scheduled
            }.collect { (state, scheduled) ->
                _blockedPackages.value = if (state.isLocked) {
                    val profilePackages = state.activeProfileId
                        ?.let { profileDao.getById(it)?.blockedPackages }
                        ?.toSet()
                        .orEmpty()
                    profilePackages + scheduled
                } else {
                    emptySet()
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

    /**
     * Invoked after a session ends. LockApplication wires it to the ScheduleManager so
     * windows are re-evaluated immediately (e.g. a window takes over right after a
     * no-escape session expires). Nullable: unit tests leave it unset.
     */
    var onSessionEnded: (suspend () -> Unit)? = null

    /**
     * [reevaluate] MUST be false when the caller IS the schedule engine: it is already
     * mid-evaluation and will arm the next boundary itself. Re-invoking it through the
     * hook from inside would deadlock its run guard (the "frozen engine" ANR).
     */
    suspend fun endLockSession(reason: String, reevaluate: Boolean = true): Long? {
        val now = System.currentTimeMillis()
        val prefs = dataStore.data.first()
        val sessionId = prefs[Keys.ACTIVE_SESSION_ID]
        if (sessionId != null) {
            sessionDao.endSession(sessionId, now, reason)
        }
        // User-initiated end: covering UNLOCK-behavior windows get consumed for the day, so
        // the re-evaluation below won't re-lock until their next occurrence. PAUSE-behavior
        // windows are never consumed: the scan pauses blocking for their duration instead,
        // and the resume alarm (armed by the evaluation) brings the block back.
        var pausedUntil: Long? = null
        val consumedToAdd = if (reason in consumingEndReasons) {
            val schedulesById = scheduleDao.getAllOnce().associateBy { it.id }
            val covering = ScheduleWindowCalculator.coveringOccurrences(
                schedulesById.values.toList(),
                zonedNow(),
            )
            val (pausing, unlocking) = covering.partition {
                ScanBehavior.fromValue(schedulesById[it.scheduleId]?.scanBehavior) == ScanBehavior.PAUSE
            }
            if (pausing.isNotEmpty()) {
                // Overlapping pause windows: the strictest (shortest) pause wins.
                val pauseMs = pausing.mapNotNull { schedulesById[it.scheduleId]?.pauseDurationMs }
                    .minOrNull() ?: Constants.DEFAULT_SCHEDULE_PAUSE_MS
                pausedUntil = zonedNow().toInstant().toEpochMilli() + pauseMs
            }
            unlocking.map { it.consumptionKey }
        } else {
            emptyList()
        }
        dataStore.edit { it ->
            it[Keys.IS_LOCKED] = false
            it.remove(Keys.SESSION_START_TIME)
            it.remove(Keys.ACTIVE_PROFILE_ID)
            it.remove(Keys.ACTIVE_SESSION_ID)
            it.remove(Keys.IS_NO_ESCAPE)
            it.remove(Keys.LOCK_DURATION_MS)
            it.remove(Keys.SCHEDULED_PACKAGES)
            it.remove(Keys.IS_SCHEDULE_ORIGIN)
            if (consumedToAdd.isNotEmpty()) {
                val current = decodeStringSet(it[Keys.CONSUMED_WINDOWS])
                it[Keys.CONSUMED_WINDOWS] = encodeStringSet(current + consumedToAdd)
            }
            pausedUntil?.let { until -> it[Keys.PAUSED_UNTIL] = until }
        }
        if (reevaluate) onSessionEnded?.invoke()
        return pausedUntil
    }

    /** Epoch millis until which scheduled blocking is paused; 0 when no pause was ever set. */
    suspend fun getSchedulePausedUntil(): Long =
        dataStore.data.first()[Keys.PAUSED_UNTIL] ?: 0L

    /**
     * Scanning while a pause is already running RESTARTS it for the pause duration (no
     * stacking). Returns the new pausedUntil, or null when no pause is active over a
     * covering pause-behavior window (callers fall through to normal scan handling).
     */
    suspend fun restartActivePause(): Long? {
        val nowMs = zonedNow().toInstant().toEpochMilli()
        val current = dataStore.data.first()[Keys.PAUSED_UNTIL] ?: return null
        if (current <= nowMs) return null
        val schedulesById = scheduleDao.getAllOnce().associateBy { it.id }
        val pausing = ScheduleWindowCalculator
            .coveringOccurrences(schedulesById.values.toList(), zonedNow())
            .filter { ScanBehavior.fromValue(schedulesById[it.scheduleId]?.scanBehavior) == ScanBehavior.PAUSE }
        if (pausing.isEmpty()) return null
        val pauseMs = pausing.mapNotNull { schedulesById[it.scheduleId]?.pauseDurationMs }
            .minOrNull() ?: Constants.DEFAULT_SCHEDULE_PAUSE_MS
        val newUntil = nowMs + pauseMs
        dataStore.edit { it[Keys.PAUSED_UNTIL] = newUntil }
        onSessionEnded?.invoke() // re-arm the resume alarm on the new deadline
        return newUntil
    }

    /** Live pause deadline for the UI (epoch ms; 0 = none). */
    val schedulePausedUntilFlow: Flow<Long> = dataStore.data.map { it[Keys.PAUSED_UNTIL] ?: 0L }

    /** "Resume blocking" tapped during a schedule pause: clear it, windows re-lock now. */
    suspend fun resumeSchedulePause() {
        clearSchedulePause()
        onSessionEnded?.invoke()
    }

    // --- Scheduled sessions (started by ScheduleManager, never from the UI) ---

    /**
     * Starts a session for a scheduled window. [profileId] is the first attached profile
     * (stats attribution); [packages] is the union of all covering windows' packages.
     * Never sets LOCK_DURATION_MS: the window-end alarm is the natural bound, and the
     * foreground service exempts schedule-origin sessions from the global timeout.
     */
    suspend fun startScheduledSession(profileId: Long, packages: Set<String>) {
        val now = System.currentTimeMillis()
        val sessionId = sessionDao.insert(
            Session(profileId = profileId, startTime = now)
        )
        dataStore.edit { prefs ->
            prefs[Keys.IS_LOCKED] = true
            prefs[Keys.SESSION_START_TIME] = now
            prefs[Keys.ACTIVE_PROFILE_ID] = profileId
            prefs[Keys.ACTIVE_SESSION_ID] = sessionId
            prefs[Keys.IS_NO_ESCAPE] = false
            prefs[Keys.IS_SCHEDULE_ORIGIN] = true
            prefs[Keys.SCHEDULED_PACKAGES] = encodeStringSet(packages)
            prefs.remove(Keys.LOCK_DURATION_MS)
            prefs[Keys.EMERGENCY_UNLOCKS] =
                prefs[Keys.MAX_EMERGENCY_UNLOCKS_SETTING] ?: Constants.DEFAULT_MAX_EMERGENCY_UNLOCKS
        }
    }

    /** Refreshes the blocked union of an active schedule-origin session (overlap changes). */
    suspend fun updateScheduledPackages(packages: Set<String>) {
        dataStore.edit { prefs ->
            prefs[Keys.SCHEDULED_PACKAGES] = encodeStringSet(packages)
        }
    }

    suspend fun getConsumedWindowKeys(): Set<String> =
        decodeStringSet(dataStore.data.first()[Keys.CONSUMED_WINDOWS])

    /** Rewrites the consumed set (used to prune stale keys). */
    suspend fun setConsumedWindowKeys(keys: Set<String>) {
        dataStore.edit { prefs ->
            if (keys.isEmpty()) prefs.remove(Keys.CONSUMED_WINDOWS)
            else prefs[Keys.CONSUMED_WINDOWS] = encodeStringSet(keys)
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

    /**
     * Saving, updating or re-enabling a schedule means "apply blocking now": a running
     * pause would silently swallow the change (a schedule created mid-pause would not
     * start), so schedule mutations clear it.
     */
    private suspend fun clearSchedulePause() {
        dataStore.edit { it.remove(Keys.PAUSED_UNTIL) }
    }

    /**
     * Disabling or deleting the last covering pause-behavior window makes a running pause
     * pointless (nothing would resume): drop it so the UI shows a plain unlock. If another
     * pause window still covers now, the countdown stays and that window resumes.
     */
    private suspend fun clearPauseIfOrphaned() {
        val nowMs = zonedNow().toInstant().toEpochMilli()
        val current = dataStore.data.first()[Keys.PAUSED_UNTIL] ?: return
        if (current <= nowMs) return
        val schedulesById = scheduleDao.getAllOnce().associateBy { it.id }
        val stillCovered = ScheduleWindowCalculator
            .coveringOccurrences(schedulesById.values.toList(), zonedNow())
            .any { ScanBehavior.fromValue(schedulesById[it.scheduleId]?.scanBehavior) == ScanBehavior.PAUSE }
        if (!stillCovered) clearSchedulePause()
    }

    suspend fun createSchedule(
        daysOfWeek: Int,
        startMinuteOfDay: Int,
        endMinuteOfDay: Int,
        profileIds: List<Long>,
        allDay: Boolean = false,
        scanBehavior: String = ScanBehavior.UNLOCK.value,
        pauseDurationMs: Long? = null,
    ): Long {
        clearSchedulePause()
        val id = scheduleDao.insert(
            Schedule(
                daysOfWeek = daysOfWeek,
                startMinuteOfDay = startMinuteOfDay,
                endMinuteOfDay = endMinuteOfDay,
                allDay = allDay,
                scanBehavior = scanBehavior,
                pauseDurationMs = pauseDurationMs,
            )
        )
        scheduleProfileDao.insertAll(
            standardProfileIds(profileIds).map { ScheduleProfileLink(id, it) }
        )
        return id
    }

    suspend fun updateSchedule(schedule: Schedule, profileIds: List<Long>) {
        clearSchedulePause()
        scheduleDao.update(schedule)
        scheduleProfileDao.deleteBySchedule(schedule.id)
        scheduleProfileDao.insertAll(
            standardProfileIds(profileIds).map { ScheduleProfileLink(schedule.id, it) }
        )
        // Rescheduling a window cancelled today to a FUTURE start reactivates it for the day.
        // A past start stays consumed: clearing it would start a session the moment of the save.
        val now = zonedNow()
        val todayKey = ScheduleWindowCalculator.consumptionKey(schedule.id, now.toLocalDate())
        val consumed = getConsumedWindowKeys()
        // All-day windows start at 00:00, so "future start" never lifts them; saving an
        // all-day schedule means "run it now" (blocked by default), lift unconditionally.
        if (todayKey in consumed &&
            (schedule.allDay || ScheduleWindowCalculator.startsLaterToday(schedule, now))
        ) {
            setConsumedWindowKeys(consumed - todayKey)
        }
    }

    suspend fun setScheduleEnabled(scheduleId: Long, enabled: Boolean) {
        scheduleDao.setEnabled(scheduleId, enabled)
        if (enabled) {
            clearSchedulePause()
            // Re-enabling a schedule whose window is in progress must re-lock immediately
            // ("blocked by default"): lift its current consumption instead of keeping it.
            val today = zonedNow().toLocalDate()
            val keys = setOf(
                ScheduleWindowCalculator.consumptionKey(scheduleId, today),
                ScheduleWindowCalculator.consumptionKey(scheduleId, today.minusDays(1)),
            )
            val consumed = getConsumedWindowKeys()
            if (consumed.any { it in keys }) setConsumedWindowKeys(consumed - keys)
        } else {
            clearPauseIfOrphaned()
        }
    }

    suspend fun deleteSchedule(scheduleId: Long) {
        scheduleProfileDao.deleteBySchedule(scheduleId)
        scheduleDao.delete(scheduleId)
        clearPauseIfOrphaned()
    }

    suspend fun setTagProfile(uid: String, profileId: Long?) {
        nfcTagDao.setProfile(uid, profileId)
    }

    suspend fun setProfileDuration(profileId: Long, durationMs: Long) {
        val profile = profileDao.getById(profileId) ?: return
        profileDao.update(profile.copy(durationMs = durationMs))
    }

    suspend fun setProfileContinuity(profileId: Long, enabled: Boolean) {
        val profile = profileDao.getById(profileId) ?: return
        profileDao.update(profile.copy(continuity = enabled))
    }

    /**
     * A timed (no-escape) session reached its end. Default: end it with [reason]. If the
     * active profile opted into blocking continuity AND at least one tag exists to unlock
     * (any known tag stops a standard session), convert the session in place into a
     * standard one that keeps blocking until the next scan: the timer is removed, the
     * no-escape flag drops, and the emergency budget comes back (it is a standard session
     * now, and the safety valves must exist since the timer no longer bounds it).
     * Returns true when the session was continued — callers must then KEEP the foreground
     * service alive (restarting it re-arms the global safety timeout).
     */
    suspend fun endOrContinueTimedSession(reason: String): Boolean {
        val state = getLockState()
        val profile = state.activeProfileId?.let { profileDao.getById(it) }
        val shouldContinue = reason == EndReason.DURATION.value &&
            state.isLocked && state.isNoEscape &&
            profile?.continuity == true &&
            hasAnyNfcTag()
        if (!shouldContinue) {
            endLockSession(reason)
            return false
        }
        dataStore.edit { prefs ->
            prefs.remove(Keys.LOCK_DURATION_MS)
            prefs[Keys.IS_NO_ESCAPE] = false
            prefs[Keys.EMERGENCY_UNLOCKS] =
                prefs[Keys.MAX_EMERGENCY_UNLOCKS_SETTING] ?: Constants.DEFAULT_MAX_EMERGENCY_UNLOCKS
        }
        return true
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

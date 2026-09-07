package com.nathanb.lock.ui.viewmodel

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ColorMatrixColorFilter
import android.net.Uri
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import com.nathanb.lock.R
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nathanb.lock.LockApplication
import com.nathanb.lock.data.model.LockState
import com.nathanb.lock.data.model.EndReason
import com.nathanb.lock.data.model.NfcTag
import com.nathanb.lock.data.model.Profile
import com.nathanb.lock.data.model.ProfileType
import com.nathanb.lock.data.model.Schedule
import com.nathanb.lock.data.model.ScheduleProfileLink
import com.nathanb.lock.data.model.Session
import com.nathanb.lock.data.repository.LockRepository
import com.nathanb.lock.nfc.NdefWriteResult
import com.nathanb.lock.nfc.NfcManager
import com.nathanb.lock.nfc.NfcResult
import com.nathanb.lock.service.SessionNotifier
import com.nathanb.lock.ui.theme.ThemeMode
import com.nathanb.lock.util.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.nathanb.lock.util.SupportPrompt

data class InstalledApp(
    val packageName: String,
    val label: String,
    val category: Int = -1, // ApplicationInfo.CATEGORY_UNDEFINED
)

class LockViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as LockApplication
    private val repository = app.repository
    val nfcManager = NfcManager(repository)

    // Wait for DataStore to load before rendering UI — prevents setup screen flash
    private val _isSetupLoaded = MutableStateFlow(false)
    val isSetupLoaded: StateFlow<Boolean> = _isSetupLoaded.asStateFlow()

    private val _isSetupCompleted = MutableStateFlow(false)
    val isSetupCompleted: StateFlow<Boolean> = _isSetupCompleted.asStateFlow()

    val lockState: StateFlow<LockState> = repository.lockStateFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LockState())

    val themeMode: StateFlow<ThemeMode> = repository.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeMode.SYSTEM)

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            repository.setThemeMode(mode)
        }
    }

    val notificationsEnabled: StateFlow<Boolean> = repository.notificationsEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setNotificationsEnabled(context: Context, enabled: Boolean) {
        viewModelScope.launch {
            repository.setNotificationsEnabled(enabled)
            // Update notification channel importance
            val manager = context.getSystemService(NotificationManager::class.java)
            val importance = if (enabled) {
                NotificationManager.IMPORTANCE_DEFAULT
            } else {
                NotificationManager.IMPORTANCE_MIN
            }
            val channel = manager.getNotificationChannel(Constants.NOTIFICATION_CHANNEL_ID)
            if (channel != null) {
                val updated = NotificationChannel(
                    Constants.NOTIFICATION_CHANNEL_ID,
                    channel.name,
                    importance,
                ).apply {
                    description = channel.description
                    setShowBadge(false)
                }
                manager.createNotificationChannel(updated)
            }
        }
    }

    init {
        viewModelScope.launch {
            // Read the real value BEFORE signaling that we're loaded
            val realValue = repository.isSetupCompleted.first()
            // Fresh install: record the installed version before anything renders, so the
            // changelog card can never appear for the version the user just installed.
            repository.seedLastSeenVersionIfFreshInstall(com.nathanb.lock.BuildConfig.VERSION_CODE)
            _isSetupCompleted.value = realValue
            _isSetupLoaded.value = true

            // Keep listening for future changes
            repository.isSetupCompleted.collect { value ->
                _isSetupCompleted.value = value
            }
        }
    }

    val profiles: StateFlow<List<Profile>> = repository.profiles
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val sessions: StateFlow<List<Session>> = repository.sessions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val completedSessionCount: StateFlow<Int> = repository.completedSessionCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val totalBlockedMs: StateFlow<Long> = repository.totalBlockedMs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val longestSessionMs: StateFlow<Long?> = repository.longestSessionMs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val nfcTags: StateFlow<List<NfcTag>> = repository.nfcTags
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Cached app icon bitmaps for blocked packages (avoids recomputing in composables)
    private val _blockedAppIcons = MutableStateFlow<List<ImageBitmap>>(emptyList())
    val blockedAppIcons: StateFlow<List<ImageBitmap>> = _blockedAppIcons.asStateFlow()

    // Cached installed apps list — loaded on demand (when settings tab opens),
    // invalidated by package broadcast
    private val _installedApps = MutableStateFlow<List<InstalledApp>>(emptyList())
    val installedApps: StateFlow<List<InstalledApp>> = _installedApps.asStateFlow()

    private val _installedAppsLoading = MutableStateFlow(false)
    val installedAppsLoading: StateFlow<Boolean> = _installedAppsLoading.asStateFlow()

    private var installedAppsLoaded = false

    // Icon cache: packageName -> ImageBitmap, loaded on demand from composables
    private val _appIconCache = MutableStateFlow<Map<String, ImageBitmap>>(emptyMap())
    val appIconCache: StateFlow<Map<String, ImageBitmap>> = _appIconCache.asStateFlow()

    fun getAppIcon(packageName: String): ImageBitmap? {
        _appIconCache.value[packageName]?.let { return it }
        // Load in background, will trigger recomposition when ready
        viewModelScope.launch(Dispatchers.IO) {
            if (_appIconCache.value.containsKey(packageName)) return@launch
            try {
                val pm = getApplication<Application>().packageManager
                val icon = pm.getApplicationIcon(packageName)
                    .toBitmap(width = 48, height = 48).asImageBitmap()
                _appIconCache.value = _appIconCache.value + (packageName to icon)
            } catch (_: Exception) { }
        }
        return null
    }

    private val packageChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            // Only refresh if already loaded once (user has visited settings)
            if (installedAppsLoaded) {
                refreshInstalledApps()
            }
        }
    }

    /** Call when navigating to settings to pre-load the installed apps cache. */
    fun ensureInstalledAppsLoaded() {
        if (installedAppsLoaded) return
        refreshInstalledApps()
    }

    init {
        // Listen for app install/uninstall to invalidate cache
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        getApplication<Application>().registerReceiver(packageChangeReceiver, filter, Context.RECEIVER_EXPORTED)
    }

    private fun refreshInstalledApps() {
        viewModelScope.launch(Dispatchers.IO) {
            _installedAppsLoading.value = true
            val pm = getApplication<Application>().packageManager
            val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val apps = pm.queryIntentActivities(launcherIntent, PackageManager.ResolveInfoFlags.of(0))
                .mapNotNull { it.activityInfo }
                .distinctBy { it.packageName }
                .filter { it.packageName !in Constants.WHITELISTED_PACKAGES }
                .map { activityInfo ->
                    val appCategory = try {
                        pm.getApplicationInfo(
                            activityInfo.packageName,
                            PackageManager.ApplicationInfoFlags.of(0),
                        ).category
                    } catch (_: Exception) { -1 }
                    InstalledApp(
                        packageName = activityInfo.packageName,
                        label = activityInfo.loadLabel(pm).toString(),
                        category = appCategory,
                    )
                }
                .sortedBy { it.label.lowercase() }
            _installedApps.value = apps
            installedAppsLoaded = true
            _installedAppsLoading.value = false
        }
    }

    /**
     * Extract the foreground layer of an adaptive icon as a white-on-transparent bitmap.
     * For non-adaptive icons, returns a grayscale version of the full icon.
     */
    private fun extractAppIcon(packageName: String, size: Int): Bitmap? {
        val pm = getApplication<Application>().packageManager
        val drawable = pm.getApplicationIcon(packageName)
        return drawable.toBitmap(width = size, height = size)
    }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            profiles
                .map { list -> (list.firstOrNull { it.isDefault } ?: list.firstOrNull())?.blockedPackages.orEmpty() }
                .distinctUntilChanged()
                .collect { packages ->
                    val icons = packages.mapNotNull { pkg ->
                        try {
                            extractAppIcon(pkg, 96)?.asImageBitmap()
                        } catch (_: Exception) {
                            null
                        }
                    }
                    _blockedAppIcons.value = icons
                }
        }
    }

    // Self-heal: a timed (no-escape) session must auto-end even if the timeout alarm was
    // lost (app update, OOM, reboot). Independent of the SessionTimeoutReceiver alarm.
    private var autoEndJob: kotlinx.coroutines.Job? = null
    private var wasLocked = false
    init {
        viewModelScope.launch {
            lockState.collect { state ->
                // Sessions can end outside the ViewModel (timeout alarm, schedule window end):
                // clear grace/emergency UI state so it doesn't leak into the next session.
                if (wasLocked && !state.isLocked) {
                    cancelGracePeriod()
                    cancelEmergency()
                }
                wasLocked = state.isLocked

                autoEndJob?.cancel()
                val duration = state.lockDurationMs
                val start = state.sessionStartTime
                if (state.isLocked && duration != null && start != null) {
                    autoEndJob = viewModelScope.launch {
                        val remaining = (start + duration) - System.currentTimeMillis()
                        if (remaining > 0) delay(remaining)
                        if (lockState.value.isLocked) {
                            cancelGracePeriod()
                            cancelEmergency()
                            if (repository.endOrContinueTimedSession(EndReason.DURATION.value)) {
                                SessionNotifier.start(getApplication())
                            } else {
                                SessionNotifier.stop(getApplication())
                            }
                        }
                    }
                }
            }
        }
    }

    // Auto-disable manual mode when an NFC tag is added
    init {
        viewModelScope.launch {
            nfcTags.collect { tags ->
                if (tags.isNotEmpty() && lockState.value.isManualMode) {
                    disableManualMode()
                }
            }
        }
    }

    /** Remove uninstalled apps from every profile. Called from MainActivity.onResume(). */
    fun cleanupUninstalledPackages() {
        viewModelScope.launch(Dispatchers.IO) {
            val pm = getApplication<Application>().packageManager
            profiles.value.forEach { profile ->
                val installed = profile.blockedPackages.filter { pkg ->
                    try {
                        pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0)); true
                    } catch (_: PackageManager.NameNotFoundException) { false }
                }
                if (installed.size < profile.blockedPackages.size) {
                    repository.updateProfile(profile.copy(blockedPackages = installed))
                }
            }
        }
    }

    // UID of the last scanned tag in pairing mode, waiting for a name
    private val _pendingPairingUid = MutableStateFlow<String?>(null)
    val pendingPairingUid: StateFlow<String?> = _pendingPairingUid.asStateFlow()

    /**
     * Write outcome of the last pairing attempt. TRANSIENT_FAILURE means the pairing screen
     * must stay up ("hold the tag still") — the next contact retries automatically.
     */
    private val _pairingWriteResult = MutableStateFlow<NdefWriteResult?>(null)
    val pairingWriteResult: StateFlow<NdefWriteResult?> = _pairingWriteResult.asStateFlow()

    /**
     * UID of the tag whose write keeps failing, and whether we've given up on writing it
     * ([NfcManager.MAX_WRITE_RETRIES] failures in a row) — the UI then offers to pair anyway.
     */
    private val _pairingWriteExhaustedUid = MutableStateFlow<String?>(null)
    val pairingWriteExhaustedUid: StateFlow<String?> = _pairingWriteExhaustedUid.asStateFlow()

    fun clearPairingWriteResult() {
        _pairingWriteResult.value = null
        _pairingWriteExhaustedUid.value = null
    }

    /** User accepted an unwritable tag: pair it anyway (works only while the app is open). */
    fun pairAnywayWithoutWrite() {
        val uid = _pairingWriteExhaustedUid.value ?: return
        nfcManager.forcePairWithoutWrite()
        _pairingWriteExhaustedUid.value = null
        _pairingWriteResult.value = NdefWriteResult.WRITE_PROTECTED
        _pendingPairingUid.value = uid
    }

    private val _nfcEvents = MutableSharedFlow<NfcResult>()
    val nfcEvents = _nfcEvents.asSharedFlow()

    private val _isEmergencyActive = MutableStateFlow(false)
    val isEmergencyActive: StateFlow<Boolean> = _isEmergencyActive.asStateFlow()

    // Visual lock state — lags behind real lock state to wait for animation
    private val _visualLocked = MutableStateFlow(false)
    val visualLocked: StateFlow<Boolean> = _visualLocked.asStateFlow()

    fun setVisualLocked(locked: Boolean) {
        _visualLocked.value = locked
    }

    private val _emergencyTimeRemaining = MutableStateFlow(0L)
    val emergencyTimeRemaining: StateFlow<Long> = _emergencyTimeRemaining.asStateFlow()

    // Grace period — 2 min window to cancel after activation
    private val _isGracePeriod = MutableStateFlow(false)
    val isGracePeriod: StateFlow<Boolean> = _isGracePeriod.asStateFlow()

    private val _graceTimeRemaining = MutableStateFlow(0L)
    val graceTimeRemaining: StateFlow<Long> = _graceTimeRemaining.asStateFlow()

    private var graceJob: kotlinx.coroutines.Job? = null
    private var emergencyJob: kotlinx.coroutines.Job? = null

    // Session settings (exposed for UI)
    val gracePeriodMs: StateFlow<Long> = repository.gracePeriodMs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Constants.DEFAULT_GRACE_PERIOD_MS)

    val maxEmergencyUnlocks: StateFlow<Int> = repository.maxEmergencyUnlocks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Constants.DEFAULT_MAX_EMERGENCY_UNLOCKS)

    val emergencyUnlockDurationMs: StateFlow<Long> = repository.emergencyUnlockDurationMs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Constants.DEFAULT_EMERGENCY_UNLOCK_DURATION_MS)

    val timeoutDurationMs: StateFlow<Long> = repository.timeoutDurationMs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Constants.DEFAULT_TIMEOUT_DURATION_MS)

    fun setGracePeriodMs(value: Long) {
        viewModelScope.launch { repository.setGracePeriodMs(value) }
    }

    fun setMaxEmergencyUnlocks(value: Int) {
        viewModelScope.launch { repository.setMaxEmergencyUnlocks(value) }
    }

    fun setEmergencyUnlockDurationMs(value: Long) {
        viewModelScope.launch { repository.setEmergencyUnlockDurationMs(value) }
    }

    fun setTimeoutDurationMs(value: Long) {
        viewModelScope.launch { repository.setTimeoutDurationMs(value) }
    }

    fun handleNfcResult(result: NfcResult) {
        viewModelScope.launch {
            _nfcEvents.emit(result)
            when (result) {
                is NfcResult.Started -> {
                    SessionNotifier.start(getApplication())
                    if (!result.isNoEscape) startGracePeriod() // No-escape: instant lock, no grace
                }
                is NfcResult.Stopped -> {
                    cancelGracePeriod()
                    cancelEmergency()
                    SessionNotifier.stop(getApplication())
                }
                is NfcResult.Paused -> {
                    // Scheduled block paused by the scan: the session (if any) is already
                    // ended in the repository; the resume alarm re-locks on its own.
                    cancelGracePeriod()
                    cancelEmergency()
                    SessionNotifier.stop(getApplication())
                }
                is NfcResult.TagPaired -> {
                    _pairingWriteResult.value = result.writeResult
                    // A transient failure keeps the pairing screen waiting for a retry:
                    // don't hand the tag over for naming/confirmation yet. After too many
                    // failures in a row, let the UI offer pairing without the write.
                    if (result.writeResult == NdefWriteResult.TRANSIENT_FAILURE) {
                        _pairingWriteExhaustedUid.value = result.uid
                            .takeIf { nfcManager.consecutiveWriteFailures() >= NfcManager.MAX_WRITE_RETRIES }
                    } else {
                        _pairingWriteExhaustedUid.value = null
                        _pendingPairingUid.value = result.uid
                    }
                }
                else -> {} // IgnoredNoEscapeActive / UnknownTag / Error -> no state change
            }
        }
    }

    fun emergencyUnlock() {
        emergencyJob?.cancel()
        emergencyJob = viewModelScope.launch {
            val state = repository.getLockState()
            if (state.emergencyUnlocksRemaining <= 0) return@launch

            repository.useEmergencyUnlock()
            _isEmergencyActive.value = true

            // Pause blocking in the Accessibility Service
            repository.setEmergencyPause(true)

            // Countdown using absolute time
            val endTime = System.currentTimeMillis() + emergencyUnlockDurationMs.value
            while (true) {
                val remaining = endTime - System.currentTimeMillis()
                if (remaining <= 0) break
                _emergencyTimeRemaining.value = remaining
                delay(1000)
            }

            // Resume blocking
            repository.setEmergencyPause(false)
            _isEmergencyActive.value = false
            _emergencyTimeRemaining.value = 0
        }
    }

    fun endEmergencyEarly() {
        emergencyJob?.cancel()
        repository.setEmergencyPause(false)
        _isEmergencyActive.value = false
        _emergencyTimeRemaining.value = 0
    }

    private fun cancelEmergency() {
        emergencyJob?.cancel()
        emergencyJob = null
        repository.setEmergencyPause(false)
        _isEmergencyActive.value = false
        _emergencyTimeRemaining.value = 0
    }

    /** Shared session start: persists, posts the notification + timeout, and grace-period unless no-escape. */
    private suspend fun beginSession(profileId: Long) {
        repository.startLockSession(profileId)
        SessionNotifier.start(getApplication())
        val isNoEscape = repository.getProfile(profileId)
            ?.let { ProfileType.fromValue(it.type) == ProfileType.NO_ESCAPE } ?: false
        if (!isNoEscape) startGracePeriod()
    }

    /** Start a specific profile manually (e.g. the "Démarrer" button on a profile detail screen). */
    fun startProfile(profileId: Long) {
        viewModelScope.launch {
            if (lockState.value.isLocked) return@launch // one session at a time
            beginSession(profileId)
        }
    }

    fun manualLock() {
        viewModelScope.launch {
            val state = lockState.value
            if (state.isLocked) return@launch
            // In normal mode, require NFC tags
            if (!state.isManualMode && nfcTags.value.isEmpty()) return@launch
            val profileId = repository.getDefaultProfile()?.id ?: return@launch
            beginSession(profileId)
        }
    }

    /**
     * Foreground safety net: if a timed (no-escape) session has reached its end, terminate it.
     * Guarded and idempotent — no-op for standard sessions, still-running sessions, or when idle.
     * Complements the ViewModel self-heal and the timeout alarm.
     */
    fun endTimedSessionIfExpired() {
        viewModelScope.launch {
            val state = lockState.value
            val duration = state.lockDurationMs ?: return@launch
            val start = state.sessionStartTime ?: return@launch
            if (state.isLocked && System.currentTimeMillis() - start >= duration) {
                cancelGracePeriod()
                cancelEmergency()
                if (repository.endOrContinueTimedSession(EndReason.DURATION.value)) {
                    SessionNotifier.start(getApplication())
                } else {
                    SessionNotifier.stop(getApplication())
                }
            }
        }
    }

    fun manualUnlock() {
        viewModelScope.launch {
            if (lockState.value.isNoEscape) return@launch // no manual unlock during no-escape
            cancelGracePeriod()
            cancelEmergency()
            repository.endLockSession(EndReason.MANUAL.value)
            SessionNotifier.stop(getApplication())
        }
    }

    fun enableManualMode() {
        viewModelScope.launch {
            repository.setManualMode(true)
        }
    }

    fun disableManualMode() {
        viewModelScope.launch {
            cancelGracePeriod()
            cancelEmergency()
            if (lockState.value.isLocked) {
                repository.endLockSession(EndReason.CANCELLED.value)
                SessionNotifier.stop(getApplication())
            }
            repository.setManualMode(false)
        }
    }

    private fun startGracePeriod() {
        graceJob?.cancel()
        val periodMs = gracePeriodMs.value
        if (periodMs <= 0L) return // "Immédiat" — no grace period
        _isGracePeriod.value = true
        graceJob = viewModelScope.launch {
            val endTime = System.currentTimeMillis() + periodMs
            while (true) {
                val remaining = endTime - System.currentTimeMillis()
                if (remaining <= 0) break
                _graceTimeRemaining.value = remaining
                delay(1000)
            }
            _isGracePeriod.value = false
            _graceTimeRemaining.value = 0
        }
    }

    private fun cancelGracePeriod() {
        graceJob?.cancel()
        graceJob = null
        _isGracePeriod.value = false
        _graceTimeRemaining.value = 0
    }

    fun cancelLock() {
        viewModelScope.launch {
            if (lockState.value.isNoEscape) return@launch // no-escape has no grace to cancel
            cancelGracePeriod()
            cancelEmergency()
            repository.endLockSession(EndReason.CANCELLED.value)
            SessionNotifier.stop(getApplication())
        }
    }

    fun completeSetup() {
        viewModelScope.launch {
            repository.completeSetup(com.nathanb.lock.BuildConfig.VERSION_CODE)
        }
    }

    // --- In-app prompts (support reminder + changelog) ---
    // Initial values are chosen so the prompts DON'T show during the DataStore load window
    // (avoids a one-frame flash): "done" for support, "up-to-date" for the changelog.
    val supportNextThreshold: StateFlow<Int> = repository.supportNextThreshold
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Int.MAX_VALUE)

    val lastSeenVersionCode: StateFlow<Int> = repository.lastSeenVersionCode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.nathanb.lock.BuildConfig.VERSION_CODE)

    /** "Maybe later" / closed: next milestone is the next multiple of 10 sessions. */
    fun declineSupportPrompt() {
        _inAppCardShownThisLaunch.value = true
        viewModelScope.launch {
            repository.setSupportNextThreshold(
                SupportPrompt.nextAfterDecline(completedSessionCount.value),
            )
        }
    }

    /** "Support" tapped: ask again 20 sessions later, then back to the every-10 rhythm. */
    fun completeSupportPrompt() {
        _inAppCardShownThisLaunch.value = true
        viewModelScope.launch {
            repository.setSupportNextThreshold(
                SupportPrompt.nextAfterSupport(completedSessionCount.value),
            )
        }
    }

    fun markVersionSeen(versionCode: Int) {
        _inAppCardShownThisLaunch.value = true
        viewModelScope.launch { repository.setLastSeenVersionCode(versionCode) }
    }

    // At most one in-app card per app launch: prevents the support reminder from popping
    // right after the changelog card is dismissed (the two look nearly identical).
    // Set when a card is DISMISSED (not when it composes — that would falsify the card's
    // own display condition and make it self-dismiss), and only gates the NEXT card.
    private val _inAppCardShownThisLaunch = MutableStateFlow(false)
    val inAppCardShownThisLaunch: StateFlow<Boolean> = _inAppCardShownThisLaunch.asStateFlow()

    fun enableNfcPairing() {
        clearPairingWriteResult()
        nfcManager.enablePairingMode()
    }

    /** Repair path: rewrite the routing data on an already-paired tag. */
    fun startTagRewrite(uid: String) {
        clearPairingWriteResult()
        nfcManager.enableRewriteMode(uid)
    }

    fun cancelTagRewrite() {
        nfcManager.disableRewriteMode()
        clearPairingWriteResult()
    }

    fun confirmPairing(name: String) {
        val uid = _pendingPairingUid.value ?: return
        viewModelScope.launch {
            repository.addNfcTag(uid, name)
            _pendingPairingUid.value = null
        }
    }

    fun confirmPairingWithName(uid: String, name: String) {
        viewModelScope.launch {
            repository.addNfcTag(uid, name)
        }
    }

    /** Pair a tag already bound to a given profile (single DB op, avoids insert/update races). */
    fun confirmPairingWithProfile(uid: String, name: String, profileId: Long) {
        viewModelScope.launch {
            repository.addNfcTag(uid, name, profileId)
        }
    }

    fun cancelPairing() {
        _pendingPairingUid.value = null
    }

    fun removeNfcTag(uid: String) {
        viewModelScope.launch {
            repository.removeNfcTag(uid)
        }
    }

    fun renameNfcTag(uid: String, name: String) {
        viewModelScope.launch {
            repository.renameNfcTag(uid, name)
        }
    }

    fun createDefaultProfile(blockedPackages: List<String>) {
        viewModelScope.launch {
            val existing = profiles.value
            if (existing.isEmpty()) {
                repository.createProfile(getApplication<Application>().getString(R.string.default_profile_name), blockedPackages)
            } else {
                repository.updateProfile(existing.first().copy(blockedPackages = blockedPackages))
            }
        }
    }

    /**
     * Guarantees a default profile exists without touching an existing one.
     * Called when the onboarding app picker is skipped, so later flows (home checklist,
     * NFC fallback) always have a real profile id to work with.
     */
    fun ensureDefaultProfile() {
        viewModelScope.launch {
            if (profiles.value.isEmpty()) {
                repository.createProfile(getApplication<Application>().getString(R.string.default_profile_name), emptyList())
            }
        }
    }

    fun updateProfileApps(profileId: Long, blockedPackages: List<String>) {
        viewModelScope.launch {
            val profile = repository.getProfile(profileId)
            when {
                profile != null -> repository.updateProfile(profile.copy(blockedPackages = blockedPackages))
                // Installs that skipped the onboarding app picker before 1.2.3 have no
                // profile at all; the home checklist then routes here with a sentinel id.
                // Create the default profile instead of dropping the selection.
                profiles.value.isEmpty() -> repository.createProfile(
                    getApplication<Application>().getString(R.string.default_profile_name),
                    blockedPackages,
                )
                else -> Log.w("LockViewModel", "updateProfileApps: unknown profile $profileId, selection dropped")
            }
        }
    }

    // --- Profile management (UI API) ---

    sealed interface ProfileEvent {
        data object Deleted : ProfileEvent
        data object BlockedDefault : ProfileEvent
        data object BlockedActiveSession : ProfileEvent
    }

    private val _profileEvents = MutableSharedFlow<ProfileEvent>()
    val profileEvents = _profileEvents.asSharedFlow()

    /** Default first, then by id ascending (creation order). */
    val profilesSorted: StateFlow<List<Profile>> = profiles
        .map { list -> list.sortedWith(compareByDescending<Profile> { it.isDefault }.thenBy { it.id }) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun createProfile(
        name: String,
        type: ProfileType,
        durationMs: Long?,
        copyAppsFromProfileId: Long?,
    ) {
        viewModelScope.launch {
            val apps = copyAppsFromProfileId
                ?.let { repository.getProfile(it)?.blockedPackages }
                .orEmpty()
            repository.createProfile(name, apps, type, durationMs)
        }
    }

    /** Create a profile with an explicit app list, optionally making it the default (standard only). */
    fun createProfileFull(
        name: String,
        type: ProfileType,
        durationMs: Long?,
        apps: List<String>,
        makeDefault: Boolean,
    ) {
        viewModelScope.launch {
            val id = repository.createProfile(name, apps, type, durationMs)
            if (makeDefault && type == ProfileType.STANDARD) {
                repository.setDefaultProfile(id)
            }
        }
    }

    fun renameProfile(profileId: Long, name: String) {
        viewModelScope.launch {
            val profile = repository.getProfile(profileId) ?: return@launch
            repository.updateProfile(profile.copy(name = name))
        }
    }

    fun setProfileDuration(profileId: Long, durationMs: Long) {
        viewModelScope.launch { repository.setProfileDuration(profileId, durationMs) }
    }

    fun setProfileContinuity(profileId: Long, enabled: Boolean) {
        viewModelScope.launch { repository.setProfileContinuity(profileId, enabled) }
    }

    fun setDefaultProfile(profileId: Long) {
        viewModelScope.launch { repository.setDefaultProfile(profileId) }
    }

    fun deleteProfile(profileId: Long) {
        viewModelScope.launch {
            val profile = repository.getProfile(profileId) ?: return@launch
            val event = when (repository.deleteProfile(profile)) {
                LockRepository.DeleteProfileResult.Success -> ProfileEvent.Deleted
                LockRepository.DeleteProfileResult.BlockedDefault -> ProfileEvent.BlockedDefault
                LockRepository.DeleteProfileResult.BlockedActiveSession -> ProfileEvent.BlockedActiveSession
            }
            _profileEvents.emit(event)
        }
    }

    fun assignTagToProfile(uid: String, profileId: Long) {
        viewModelScope.launch { repository.setTagProfile(uid, profileId) }
    }

    /** Detach a tag from its profile: it falls back to activating the default profile. */
    fun dissociateTag(uid: String) {
        viewModelScope.launch { repository.setTagProfile(uid, null) }
    }

    // --- Schedules (recurring auto-lock windows) ---

    /** Deadline of the running schedule pause (epoch ms; 0 = none). */
    val schedulePausedUntil: StateFlow<Long> = repository.schedulePausedUntilFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    /** "Resume blocking" during a schedule pause: end it now, covering windows re-lock. */
    fun resumeScheduledBlocking() {
        viewModelScope.launch { repository.resumeSchedulePause() }
    }

    /** The set actually enforced by the blocker (union of profile + scheduled windows). */
    val liveBlockedPackages: StateFlow<Set<String>> = repository.blockedPackages

    val schedules: StateFlow<List<Schedule>> = repository.schedules
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val scheduleLinks: StateFlow<List<ScheduleProfileLink>> = repository.scheduleLinks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private suspend fun rearmSchedules() {
        getApplication<LockApplication>().scheduleManager.evaluateAndRearm()
    }

    fun createSchedule(
        daysOfWeek: Int,
        startMinuteOfDay: Int,
        endMinuteOfDay: Int,
        profileIds: List<Long>,
        allDay: Boolean = false,
        scanBehavior: String = com.nathanb.lock.data.model.ScanBehavior.UNLOCK.value,
        pauseDurationMs: Long? = null,
    ) {
        viewModelScope.launch {
            repository.createSchedule(
                daysOfWeek, startMinuteOfDay, endMinuteOfDay, profileIds,
                allDay, scanBehavior, pauseDurationMs,
            )
            rearmSchedules()
        }
    }

    fun updateSchedule(schedule: Schedule, profileIds: List<Long>) {
        viewModelScope.launch {
            repository.updateSchedule(schedule, profileIds)
            rearmSchedules()
        }
    }

    fun setScheduleEnabled(scheduleId: Long, enabled: Boolean) {
        viewModelScope.launch {
            repository.setScheduleEnabled(scheduleId, enabled)
            rearmSchedules()
        }
    }

    fun deleteSchedule(scheduleId: Long) {
        viewModelScope.launch {
            repository.deleteSchedule(scheduleId)
            rearmSchedules()
        }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            getApplication<Application>().unregisterReceiver(packageChangeReceiver)
        } catch (_: Exception) { }
    }

    // Backup
    sealed interface BackupEvent {
        data class ExportSuccess(val uri: Uri) : BackupEvent
        data class ImportSuccess(val profileCount: Int, val tagCount: Int) : BackupEvent
        data class Error(val message: String) : BackupEvent
    }

    private val _backupEvents = MutableSharedFlow<BackupEvent>()
    val backupEvents = _backupEvents.asSharedFlow()

    fun exportBackup(uri: Uri) {
        viewModelScope.launch {
            try {
                repository.exportBackup(uri)
                _backupEvents.emit(BackupEvent.ExportSuccess(uri))
            } catch (e: Exception) {
                _backupEvents.emit(BackupEvent.Error(e.message ?: getApplication<Application>().getString(R.string.error_export)))
            }
        }
    }

    fun importBackup(uri: Uri) {
        viewModelScope.launch {
            try {
                repository.importBackup(uri)
                val profiles = repository.profiles.first()
                val tags = repository.nfcTags.first()
                _backupEvents.emit(BackupEvent.ImportSuccess(profiles.size, tags.size))
            } catch (e: Exception) {
                _backupEvents.emit(BackupEvent.Error(e.message ?: getApplication<Application>().getString(R.string.error_import)))
            }
        }
    }
}

package com.nathanb.lock.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import com.nathanb.lock.BuildConfig
import com.nathanb.lock.LockApplication
import com.nathanb.lock.MainActivity
import com.nathanb.lock.R
import com.nathanb.lock.data.model.EndReason
import com.nathanb.lock.util.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class LockForegroundService : Service() {

    companion object {
        private const val TAG = "LockForegroundService"
        const val ACTION_START = "com.nathanb.lock.START"
        const val ACTION_STOP = "com.nathanb.lock.STOP"

        fun start(context: Context) {
            val intent = Intent(context, LockForegroundService::class.java).apply {
                action = ACTION_START
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, LockForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        /** Remaining ms for a positive-duration session, based on its start time. */
        fun remainingMs(durationMs: Long, sessionStartTime: Long, now: Long): Long =
            (durationMs - (now - sessionStartTime)).coerceAtLeast(0L)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var notificationUpdateJob: Job? = null
    private var timeoutJob: Job? = null

    private val repository by lazy {
        (application as LockApplication).repository
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startSession()
            ACTION_STOP -> stopSession()
            else -> startSession()
        }
        return START_STICKY
    }

    private fun startSession() {
        if (BuildConfig.DEBUG) Log.d(TAG, "Starting lock session")

        // Start with a basic notification, then update with correct data
        val notification = buildTimerNotification()

        startForeground(
            Constants.NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )

        // Fetch actual session start time + blocked app count and update notification
        notificationUpdateJob?.cancel()
        notificationUpdateJob = scope.launch {
            val state = repository.getLockState()
            val startTime = state.sessionStartTime
            val profileId = state.activeProfileId
            val appCount = if (profileId != null) {
                repository.getProfile(profileId)?.blockedPackages?.size ?: 0
            } else {
                0
            }
            if (startTime != null) {
                val updated = buildTimerNotification(startTime, appCount)
                getSystemService(NotificationManager::class.java)
                    .notify(Constants.NOTIFICATION_ID, updated)
            }
        }

        // Auto-unlock: per-session duration (no-escape) or global timeout (standard); 0 = unlimited.
        timeoutJob?.cancel()
        timeoutJob = scope.launch {
            val state = repository.getLockState()
            val durationMs = state.lockDurationMs ?: repository.timeoutDurationMs.first()
            if (durationMs <= 0L) return@launch // unlimited
            val startTime = state.sessionStartTime ?: System.currentTimeMillis()
            val remaining = remainingMs(durationMs, startTime, System.currentTimeMillis())
            delay(remaining)
            val reason = if (state.lockDurationMs != null) EndReason.DURATION.value else EndReason.TIMEOUT.value
            if (BuildConfig.DEBUG) Log.d(TAG, "Session auto-unlock ($reason)")
            repository.endLockSession(reason)
            stopSelf()
        }
    }

    private fun stopSession() {
        if (BuildConfig.DEBUG) Log.d(TAG, "Stopping lock session")
        notificationUpdateJob?.cancel()
        timeoutJob?.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        val enabled = runBlocking { repository.notificationsEnabled.first() }
        val importance = if (enabled) {
            NotificationManager.IMPORTANCE_DEFAULT
        } else {
            NotificationManager.IMPORTANCE_MIN
        }
        val channel = NotificationChannel(
            Constants.NOTIFICATION_CHANNEL_ID,
            getString(R.string.notification_channel_name),
            importance,
        ).apply {
            description = getString(R.string.notification_channel_desc)
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildTimerNotification(
        sessionStartTime: Long? = null,
        blockedAppCount: Int = 0,
    ): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            Constants.FOREGROUND_SERVICE_REQUEST_CODE,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val sessionStart = sessionStartTime ?: System.currentTimeMillis()
        val title = if (blockedAppCount > 0) {
            getString(R.string.notification_session_active_count, blockedAppCount, if (blockedAppCount > 1) "s" else "", if (blockedAppCount > 1) "s" else "")
        } else {
            getString(R.string.notification_session_active)
        }

        return Notification.Builder(this, Constants.NOTIFICATION_CHANNEL_ID)
            .setContentTitle(title)
            .setSmallIcon(R.drawable.ic_lock_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setUsesChronometer(true)
            .setWhen(sessionStart)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        if (BuildConfig.DEBUG) Log.d(TAG, "Service destroyed")
    }
}

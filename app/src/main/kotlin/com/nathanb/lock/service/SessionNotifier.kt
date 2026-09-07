package com.nathanb.lock.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.nathanb.lock.BuildConfig
import com.nathanb.lock.LockApplication
import com.nathanb.lock.MainActivity
import com.nathanb.lock.R
import com.nathanb.lock.data.model.EndReason
import com.nathanb.lock.data.model.LockState
import com.nathanb.lock.util.Constants
import kotlinx.coroutines.flow.first

/**
 * Posts and clears the session notification, and arms or cancels the session timeout alarm.
 *
 * Deliberately NOT a foreground service. A foreground service lists the app under
 * "Active apps" in the notification shade (Android 13+), whose Stop button kills the
 * process AND leaves the accessibility service unbound while its toggle still reads as
 * enabled in Android settings. Blocking could be bypassed in two taps, and nothing here
 * ever needed a service: blocking lives in [AppBlockerService] (accessibility) and the
 * timeout is an AlarmManager alarm ([SessionTimeoutReceiver]).
 *
 * The chronometer is rendered by the system from `setWhen`, so nothing has to tick.
 * On Android 14+ an ongoing notification that is not backed by a foreground service can
 * be swiped away; the session itself is unaffected and the next session event
 * (start, continuity, boot) posts it again.
 */
object SessionNotifier {

    private const val TAG = "SessionNotifier"

    /** Which alarm, if any, bounds the current session. */
    data class TimeoutPlan(val triggerAtEpochMillis: Long, val reason: String)

    /**
     * Pure decision, unit-tested: per-session duration (no-escape) or global safety timeout
     * (standard sessions); 0 means unlimited. Schedule-origin sessions without a duration
     * are exempt: their bound is the window-end alarm, and a 9h-17h window must not be cut
     * (then instantly re-locked) by the 5 h safety timeout.
     */
    fun timeoutPlan(state: LockState, defaultTimeoutMs: Long): TimeoutPlan? {
        if (!state.isLocked) return null
        if (state.isScheduleOrigin && state.lockDurationMs == null) return null
        val durationMs = state.lockDurationMs ?: defaultTimeoutMs
        if (durationMs <= 0L) return null
        val startTime = state.sessionStartTime ?: return null
        val reason = if (state.lockDurationMs != null) EndReason.DURATION.value else EndReason.TIMEOUT.value
        return TimeoutPlan(startTime + durationMs, reason)
    }

    /** Posts (or refreshes) the session notification and arms the timeout alarm. */
    suspend fun start(context: Context) {
        val app = context.applicationContext as LockApplication
        val repository = app.repository
        val state = repository.getLockState()
        if (BuildConfig.DEBUG) Log.d(TAG, "Session started (locked=${state.isLocked})")

        ensureChannel(app, repository.notificationsEnabled.first())

        // Blocked-set snapshot covers union sessions (scheduled windows), not just the profile.
        val appCount = repository.blockedPackages.value.size.takeIf { it > 0 }
            ?: state.activeProfileId?.let { repository.getProfile(it)?.blockedPackages?.size }
            ?: 0
        app.getSystemService(NotificationManager::class.java)
            .notify(Constants.NOTIFICATION_ID, buildNotification(app, state.sessionStartTime, appCount))

        // Armed as a wall-clock alarm, never a coroutine delay: delay counts on the uptime
        // clock, which stops in deep sleep, and a 5 h timeout used to run 6 h+ on an idle phone.
        // A past trigger time (process restarted after the deadline) fires immediately.
        val plan = timeoutPlan(state, repository.timeoutDurationMs.first())
        val startTime = state.sessionStartTime
        if (plan != null && startTime != null) {
            SessionTimeoutReceiver.arm(app, plan.triggerAtEpochMillis, startTime, plan.reason)
            if (BuildConfig.DEBUG) Log.d(TAG, "Timeout alarm armed (${plan.reason}) at ${plan.triggerAtEpochMillis}")
        } else if (BuildConfig.DEBUG) {
            Log.d(TAG, "No timeout alarm for this session")
        }
    }

    /** Clears the session notification and cancels the timeout alarm. */
    fun stop(context: Context) {
        if (BuildConfig.DEBUG) Log.d(TAG, "Session stopped")
        SessionTimeoutReceiver.cancel(context)
        context.getSystemService(NotificationManager::class.java).cancel(Constants.NOTIFICATION_ID)
    }

    private fun ensureChannel(context: Context, notificationsEnabled: Boolean) {
        val importance = if (notificationsEnabled) {
            NotificationManager.IMPORTANCE_DEFAULT
        } else {
            NotificationManager.IMPORTANCE_MIN
        }
        val channel = NotificationChannel(
            Constants.NOTIFICATION_CHANNEL_ID,
            context.getString(R.string.notification_channel_name),
            importance,
        ).apply {
            description = context.getString(R.string.notification_channel_desc)
            setShowBadge(false)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(
        context: Context,
        sessionStartTime: Long?,
        blockedAppCount: Int,
    ): Notification {
        val pendingIntent = PendingIntent.getActivity(
            context,
            Constants.SESSION_NOTIFICATION_REQUEST_CODE,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val title = if (blockedAppCount > 0) {
            context.resources.getQuantityString(
                R.plurals.notification_session_active_count, blockedAppCount, blockedAppCount,
            )
        } else {
            context.getString(R.string.notification_session_active)
        }

        return Notification.Builder(context, Constants.NOTIFICATION_CHANNEL_ID)
            .setContentTitle(title)
            .setSmallIcon(R.drawable.ic_lock_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setUsesChronometer(true)
            .setWhen(sessionStartTime ?: System.currentTimeMillis())
            .build()
    }
}

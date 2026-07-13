package com.nathanb.lock.schedule

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.nathanb.lock.data.model.EndReason
import com.nathanb.lock.data.repository.LockRepository
import com.nathanb.lock.service.LockForegroundService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import java.time.ZonedDateTime
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The engine's side effects on the platform, extracted so the engine itself can run
 * (and be tested) without Android. Production uses [AndroidScheduleEffects].
 */
interface ScheduleEffects {
    /** Arm the single window-boundary alarm (exact when the platform allows it). */
    fun armWindowBoundary(triggerAtEpochMillis: Long)
    fun cancelWindowBoundary()
    fun startLockService()
    fun stopLockService()
}

class AndroidScheduleEffects(private val context: Context) : ScheduleEffects {

    private fun pendingIntent(): PendingIntent {
        val intent = Intent(context, ScheduleAlarmReceiver::class.java)
            .setAction(ScheduleAlarmReceiver.ACTION_WINDOW_BOUNDARY)
        return PendingIntent.getBroadcast(
            context,
            BOUNDARY_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    override fun armWindowBoundary(triggerAtEpochMillis: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtEpochMillis, pendingIntent())
        } else {
            // Degraded mode without the exact-alarm permission: inexact delivery,
            // backed by re-evaluation on app start and on the other receiver triggers.
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtEpochMillis, pendingIntent())
        }
    }

    override fun cancelWindowBoundary() {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pendingIntent())
    }

    override fun startLockService() = LockForegroundService.start(context)

    override fun stopLockService() = LockForegroundService.stop(context)

    private companion object {
        const val BOUNDARY_REQUEST_CODE = 200
    }
}

/**
 * Drives scheduled auto-lock windows. Stateless by design: every trigger (window boundary
 * alarm, boot, app update, time/zone change, schedule edit, session end) calls
 * [evaluateAndRearm], which recomputes the truth from the database and DataStore, applies
 * the required transition, then arms ONE alarm for the next boundary.
 *
 * Concurrency: [evaluateAndRearm] never blocks on itself. A trigger arriving while an
 * evaluation runs (including a re-entrant one) is absorbed into ONE extra pass of the
 * running evaluation instead of waiting on the guard — a second waiter deadlocked here
 * once, freezing the engine and surfacing as an ANR at the next alarm.
 */
class ScheduleManager(
    private val repository: LockRepository,
    private val effects: ScheduleEffects,
    private val now: () -> ZonedDateTime = { ZonedDateTime.now() },
) {
    private val runGuard = Mutex()
    private val rerunRequested = AtomicBoolean(false)

    suspend fun evaluateAndRearm() {
        if (!runGuard.tryLock()) {
            // Someone is already evaluating: ask them for one more pass and leave.
            rerunRequested.set(true)
            return
        }
        try {
            do {
                rerunRequested.set(false)
                evaluateOnce()
            } while (rerunRequested.compareAndSet(true, false))
        } finally {
            runGuard.unlock()
        }
    }

    private suspend fun evaluateOnce() {
        val now = now()
        val schedules = repository.schedules.first()

        // Prune stale consumption keys (yesterday's overnight windows still matter).
        val consumed = ScheduleWindowCalculator.pruneConsumed(
            repository.getConsumedWindowKeys(), now.toLocalDate(),
        )
        repository.setConsumedWindowKeys(consumed)

        val links = repository.scheduleLinks.first()
        val profilesById = repository.profiles.first().associateBy { it.id }
        val occurrences = ScheduleWindowCalculator.coveringOccurrences(schedules, now)
        val union = ScheduleWindowCalculator.activePackages(occurrences, consumed, links, profilesById)

        val state = repository.getLockState()
        when {
            union.isEmpty() && state.isLocked && state.isScheduleOrigin -> {
                // Last covering window closed (or lost its profiles): end the session.
                // reevaluate=false — we ARE the evaluation; re-entering would self-deadlock.
                repository.endLockSession(EndReason.SCHEDULE.value, reevaluate = false)
                effects.stopLockService()
            }
            union.isNotEmpty() && !state.isLocked -> {
                val activeIds = occurrences
                    .filter { it.consumptionKey !in consumed }
                    .map { it.scheduleId }
                    .toSet()
                val firstProfileId = links.firstOrNull {
                    it.scheduleId in activeIds && profilesById.containsKey(it.profileId)
                }?.profileId
                if (firstProfileId != null) {
                    repository.startScheduledSession(firstProfileId, union)
                    effects.startLockService()
                }
            }
            union.isNotEmpty() && state.isLocked && state.isScheduleOrigin -> {
                // Overlap changed while a scheduled session runs: refresh the union.
                repository.updateScheduledPackages(union)
            }
            // union non-empty + manual/NFC session active: window is ignored (spec).
            // union empty + not locked, or + manual session: nothing to do.
        }

        val next = ScheduleWindowCalculator.nextBoundary(schedules, now)
        if (next == null) {
            effects.cancelWindowBoundary()
        } else {
            effects.armWindowBoundary(next.toInstant().toEpochMilli())
        }
    }
}

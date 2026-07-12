package com.nathanb.lock.schedule

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.nathanb.lock.data.model.EndReason
import com.nathanb.lock.data.model.Schedule
import com.nathanb.lock.data.repository.LockRepository
import com.nathanb.lock.service.LockForegroundService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.ZonedDateTime

/**
 * Drives scheduled auto-lock windows. Stateless by design: every trigger (window boundary
 * alarm, boot, app update, time/zone change, schedule edit, session end) calls
 * [evaluateAndRearm], which recomputes the truth from the database and DataStore, applies
 * the required transition, then arms ONE alarm for the next boundary.
 */
class ScheduleManager(
    private val context: Context,
    private val repository: LockRepository,
) {
    private val mutex = Mutex()

    suspend fun evaluateAndRearm() {
        mutex.withLock {
            val now = ZonedDateTime.now()
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
                    repository.endLockSession(EndReason.SCHEDULE.value)
                    LockForegroundService.stop(context)
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
                        LockForegroundService.start(context)
                    }
                }
                union.isNotEmpty() && state.isLocked && state.isScheduleOrigin -> {
                    // Overlap changed while a scheduled session runs: refresh the union.
                    repository.updateScheduledPackages(union)
                }
                // union non-empty + manual/NFC session active: window is ignored (spec).
                // union empty + not locked, or + manual session: nothing to do.
            }

            armNextBoundary(schedules, now)
        }
    }

    private fun armNextBoundary(schedules: List<Schedule>, now: ZonedDateTime) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = boundaryPendingIntent()
        val next = ScheduleWindowCalculator.nextBoundary(schedules, now)
        if (next == null) {
            alarmManager.cancel(pendingIntent)
            return
        }
        val triggerAtMillis = next.toInstant().toEpochMilli()
        if (alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        } else {
            // Degraded mode without the exact-alarm permission: inexact delivery,
            // backed by re-evaluation on app start and on the other receiver triggers.
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
    }

    private fun boundaryPendingIntent(): PendingIntent {
        val intent = Intent(context, ScheduleAlarmReceiver::class.java)
            .setAction(ScheduleAlarmReceiver.ACTION_WINDOW_BOUNDARY)
        return PendingIntent.getBroadcast(
            context,
            BOUNDARY_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    companion object {
        private const val BOUNDARY_REQUEST_CODE = 200
    }
}

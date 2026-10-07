package com.nathanb.lock.service

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * While a blocked app is pinned, Lock cannot leave it (HOME is refused), so the block
 * overlay stays up without its OK button. Unpinning is the only way out (the tag and the
 * emergency unlock need Lock's own screen, which pinning forbids): this watch notices it.
 */
internal enum class PinnedWatchAction { KEEP, DISMISS, BLOCK_NORMALLY }

internal fun pinnedWatchAction(
    stillPinned: Boolean,
    blockingActive: Boolean,
    foregroundStillBlocked: Boolean,
): PinnedWatchAction = when {
    !blockingActive -> PinnedWatchAction.DISMISS // session ended or emergency pause
    stillPinned -> PinnedWatchAction.KEEP
    foregroundStillBlocked -> PinnedWatchAction.BLOCK_NORMALLY
    else -> PinnedWatchAction.DISMISS
}

private const val PINNED_POLL_MS = 500L

/** Polls until the pinned block resolves, then dismisses (and re-blocks if still needed). */
internal fun CoroutineScope.watchPinnedBlock(
    isPinned: () -> Boolean,
    blockingActive: () -> Boolean,
    foregroundStillBlocked: () -> Boolean,
    onDismiss: () -> Unit,
    onBlockNormally: () -> Unit,
): Job = launch {
    while (isActive) {
        delay(PINNED_POLL_MS)
        when (pinnedWatchAction(isPinned(), blockingActive(), foregroundStillBlocked())) {
            PinnedWatchAction.KEEP -> Unit
            PinnedWatchAction.DISMISS -> {
                onDismiss()
                return@launch
            }
            PinnedWatchAction.BLOCK_NORMALLY -> {
                onDismiss()
                onBlockNormally()
                return@launch
            }
        }
    }
}

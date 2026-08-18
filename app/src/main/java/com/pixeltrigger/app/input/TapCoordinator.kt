package com.pixeltrigger.app.input

import android.os.SystemClock
import java.util.concurrent.atomic.AtomicLong

/**
 * Converts one detector FIRE into one tap request.
 *
 * There is intentionally no second duplicate-rejection gate here. The detector
 * state machine already emits one FIRE for the transition, and extra monotonic
 * gates can incorrectly reject legitimate taps after component restarts.
 */
class TapCoordinator(
    private val engine: TapEngine,
    @Suppress("UNUSED_PARAMETER") private val gate: ExactlyOnceTapGate = ExactlyOnceTapGate(),
) {
    private val ids = AtomicLong(0L)

    fun fire(x: Float, y: Float, displayId: Int = 0): TapResult {
        val request = TapRequest(
            triggerId = ids.incrementAndGet(),
            x = x,
            y = y,
            requestedDurationMs = 1L,
            requestedAtNs = SystemClock.elapsedRealtimeNanos(),
            displayId = displayId,
        )
        return engine.tap(request)
    }
}

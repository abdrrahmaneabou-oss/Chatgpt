package com.pixeltrigger.app.input

import android.os.SystemClock
import java.util.concurrent.atomic.AtomicLong

/** Converts each detector FIRE directly into one tap request. */
class TapCoordinator(
    private val engine: TapEngine,
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

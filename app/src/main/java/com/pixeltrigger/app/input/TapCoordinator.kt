package com.pixeltrigger.app.input

import android.os.SystemClock
import java.util.concurrent.atomic.AtomicLong

/** Exactly one engine submission per detector FIRE, with monotonic IDs for diagnostics. */
class TapCoordinator(
    private val engine: TapEngine,
    private val gate: ExactlyOnceTapGate = ExactlyOnceTapGate(),
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
        if (!gate.accept(request.triggerId)) {
            return TapResult.Rejected(
                triggerId = request.triggerId,
                acceptedAtNs = SystemClock.elapsedRealtimeNanos(),
                reason = "duplicate triggerId",
            )
        }
        return engine.tap(request)
    }
}

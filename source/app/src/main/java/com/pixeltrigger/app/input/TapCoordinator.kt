package com.pixeltrigger.app.input

import android.os.SystemClock
import java.util.concurrent.atomic.AtomicLong

/**
 * Generates monotonic trigger IDs and guarantees exactly one TapEngine submission per trigger.
 */
class TapCoordinator(
    private val engine: TapEngine,
    private val gate: ExactlyOnceTapGate = ExactlyOnceTapGate(),
) {
    private val ids = AtomicLong(0L)

    fun nextRequest(x: Float, y: Float, durationMs: Long = 1L): TapRequest = TapRequest(
        triggerId = ids.incrementAndGet(),
        x = x,
        y = y,
        requestedDurationMs = durationMs,
        requestedAtNs = SystemClock.elapsedRealtimeNanos(),
    )

    suspend fun submit(request: TapRequest): TapResult {
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

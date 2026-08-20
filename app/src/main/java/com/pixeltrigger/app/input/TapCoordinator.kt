package com.pixeltrigger.app.input

import android.os.SystemClock
import com.pixeltrigger.app.profiling.AppLatencyProfiler
import java.util.concurrent.atomic.AtomicLong

/** Converts each detector FIRE directly into one tap request. */
class TapCoordinator(
    private val engine: TapEngine,
) {
    private val ids = AtomicLong(0L)

    fun fire(
        x: Float,
        y: Float,
        displayId: Int = 0,
        frameTimestampNs: Long = 0L,
        captureCallbackNs: Long = 0L,
        sampleStartNs: Long = 0L,
        sampleEndNs: Long = 0L,
        detectionStartNs: Long = 0L,
        fireDecisionNs: Long = 0L,
    ): TapResult {
        val cadence = AppLatencyProfiler.snapshotForFire()
        val request = TapRequest(
            triggerId = ids.incrementAndGet(),
            x = x,
            y = y,
            requestedDurationMs = 1L,
            requestedAtNs = SystemClock.elapsedRealtimeNanos(),
            displayId = displayId,
            frameTimestampNs = frameTimestampNs,
            captureCallbackNs = captureCallbackNs,
            sampleStartNs = sampleStartNs,
            sampleEndNs = sampleEndNs,
            detectionStartNs = detectionStartNs,
            fireDecisionNs = fireDecisionNs,
            samplerEntryGapNs = cadence.samplerEntryGapNs,
            imageTimestampGapNs = cadence.imageTimestampGapNs,
        )
        return when (val result = engine.tap(request)) {
            is TapResult.Rejected -> TapResult.Failed(
                triggerId = result.triggerId,
                acceptedAtNs = result.acceptedAtNs,
                reason = result.reason,
            )
            else -> result
        }
    }
}

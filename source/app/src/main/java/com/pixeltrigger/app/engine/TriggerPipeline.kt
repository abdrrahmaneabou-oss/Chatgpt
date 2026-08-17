package com.pixeltrigger.app.engine

import android.os.SystemClock
import com.pixeltrigger.app.input.TapCoordinator
import com.pixeltrigger.app.input.TapResult
import com.pixeltrigger.app.input.TapTelemetry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Bridges the frozen v2.12 detector to a replaceable TapEngine without changing detector rules.
 *
 * DetectionEngine transitions to WAITING_REARM before it returns Event.Fired, matching v2.12.
 * Only then is a monotonic tap request created and submitted. No automatic retry is performed.
 */
class TriggerPipeline(
    val detector: DetectionEngine,
    private val tapCoordinator: TapCoordinator,
    private val scope: CoroutineScope,
    private val targetProvider: () -> Target?,
    private val telemetry: TapTelemetry = TapTelemetry(),
    private val backendNameProvider: () -> String,
) {
    data class Target(val x: Float, val y: Float)

    fun processSample(
        sample: DetectionEngine.ColorSample,
        nowMs: Long,
    ): DetectionEngine.Event {
        val event = detector.processSample(sample, nowMs)
        if (event is DetectionEngine.Event.Fired) {
            submitExactlyOnceTap()
        }
        return event
    }

    fun telemetrySnapshot(): List<TapTelemetry.Entry> = telemetry.snapshot()

    private fun submitExactlyOnceTap() {
        val target = targetProvider() ?: return
        val request = tapCoordinator.nextRequest(target.x, target.y, durationMs = 1L)
        val detectedBackend = backendNameProvider()
        telemetry.record(
            TapTelemetry.Entry(
                triggerId = request.triggerId,
                phase = TapTelemetry.Phase.DETECTED,
                timestampNs = request.requestedAtNs,
                backend = detectedBackend,
            ),
        )

        scope.launch {
            val submittedBackend = backendNameProvider()
            telemetry.record(
                TapTelemetry.Entry(
                    triggerId = request.triggerId,
                    phase = TapTelemetry.Phase.SUBMITTED,
                    timestampNs = SystemClock.elapsedRealtimeNanos(),
                    backend = submittedBackend,
                ),
            )

            val result = tapCoordinator.submit(request)
            val timestamp = SystemClock.elapsedRealtimeNanos()
            when (result) {
                is TapResult.Completed -> telemetry.record(
                    TapTelemetry.Entry(
                        result.triggerId,
                        TapTelemetry.Phase.COMPLETED,
                        timestamp,
                        submittedBackend,
                    ),
                )

                is TapResult.Cancelled -> telemetry.record(
                    TapTelemetry.Entry(
                        result.triggerId,
                        TapTelemetry.Phase.CANCELLED,
                        timestamp,
                        submittedBackend,
                    ),
                )

                is TapResult.Rejected -> telemetry.record(
                    TapTelemetry.Entry(
                        result.triggerId,
                        TapTelemetry.Phase.REJECTED,
                        timestamp,
                        submittedBackend,
                        result.reason,
                    ),
                )
            }
        }
    }
}

package com.pixeltrigger.app.input

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.SystemClock
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Compatibility backend matching v2.12's accessibility injection semantics.
 * This backend is intentionally not the target concurrent-touch backend.
 */
class LegacyAccessibilityTapEngine(
    private val serviceProvider: () -> AccessibilityService?,
) : TapEngine {
    override val name: String = "accessibility-legacy"

    override suspend fun tap(request: TapRequest): TapResult {
        val acceptedAt = SystemClock.elapsedRealtimeNanos()
        val service = serviceProvider()
            ?: return TapResult.Rejected(request.triggerId, acceptedAt, "accessibility service unavailable")

        val path = Path().apply { moveTo(request.x, request.y) }
        val gesture = GestureDescription.Builder()
            .addStroke(
                GestureDescription.StrokeDescription(
                    path,
                    0L,
                    request.requestedDurationMs.coerceAtLeast(1L),
                ),
            )
            .build()

        return suspendCancellableCoroutine { continuation ->
            val callback = object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription) {
                    if (continuation.isActive) {
                        continuation.resume(
                            TapResult.Completed(
                                request.triggerId,
                                acceptedAt,
                                SystemClock.elapsedRealtimeNanos(),
                            ),
                        )
                    }
                }

                override fun onCancelled(gestureDescription: GestureDescription) {
                    if (continuation.isActive) {
                        continuation.resume(
                            TapResult.Cancelled(
                                request.triggerId,
                                acceptedAt,
                                SystemClock.elapsedRealtimeNanos(),
                            ),
                        )
                    }
                }
            }

            val accepted = service.dispatchGesture(gesture, callback, null)
            if (!accepted && continuation.isActive) {
                continuation.resume(
                    TapResult.Rejected(request.triggerId, acceptedAt, "dispatchGesture rejected"),
                )
            }
        }
    }
}

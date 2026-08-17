package com.pixeltrigger.app.input

import android.os.SystemClock

/**
 * TapEngine facade for the privileged unified-touch daemon.
 *
 * Unlike the legacy Accessibility backend this never creates a competing Android gesture.
 * The daemon adds one MT slot to the same relayed touchscreen stream as the player's fingers.
 */
class UnifiedTouchRootEngine(
    private val controller: RootTouchDaemonController,
    private val displayInfo: () -> DisplayInfo,
) : TapEngine {
    override val name: String = "root-unified-touch"

    data class DisplayInfo(
        val widthPx: Int,
        val heightPx: Int,
        /** Surface rotation: 0, 1, 2, 3. */
        val rotation: Int,
    )

    override suspend fun tap(request: TapRequest): TapResult {
        // Do not start/restart the root proxy in response to a FIRE. Grabbing the physical
        // touchscreen must happen during explicit service startup, never mid-gesture.
        if (!controller.isAlive()) {
            return TapResult.Rejected(
                triggerId = request.triggerId,
                acceptedAtNs = SystemClock.elapsedRealtimeNanos(),
                reason = "root touch daemon not ready",
            )
        }
        val display = displayInfo()
        return controller.tap(
            request = request,
            widthPx = display.widthPx,
            heightPx = display.heightPx,
            rotation = display.rotation,
        )
    }
}

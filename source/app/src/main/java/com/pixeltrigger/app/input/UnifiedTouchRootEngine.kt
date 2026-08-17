package com.pixeltrigger.app.input

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
        if (!controller.isAlive() && controller.start() == null) {
            return TapResult.Rejected(
                triggerId = request.triggerId,
                acceptedAtNs = android.os.SystemClock.elapsedRealtimeNanos(),
                reason = "root touch daemon unavailable",
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

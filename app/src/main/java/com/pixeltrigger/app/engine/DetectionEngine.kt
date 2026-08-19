package com.pixeltrigger.app.engine

/**
 * PixelTrigger v2.12 white arming/rearming with a strict near-black FIRE rule.
 *
 * Only white can arm or rearm. Once ARMED, bright/colored non-white samples are
 * neutral and keep the engine ARMED. A near-black sample fires immediately.
 */
class DetectionEngine(
    var whiteRearmEnabled: Boolean = true,
    var rearmDelayEnabled: Boolean = false,
    var rearmSeconds: Int = 10,
) {
    enum class State { WAITING_FOR_WHITE, ARMED, WAITING_REARM }

    data class ColorSample(
        val averageRed: Int,
        val averageGreen: Int,
        val averageBlue: Int,
        val whiteRatio: Float,
        val averageLuminance: Int,
        val averageChroma: Int,
    ) {
        fun isArmingWhite(): Boolean =
            whiteRatio >= ARM_WHITE_COVERAGE &&
                averageLuminance >= ARM_WHITE_AVERAGE_LUMINANCE &&
                averageChroma <= ARM_WHITE_AVERAGE_CHROMA

        fun isHoldingWhite(): Boolean =
            whiteRatio >= HOLD_WHITE_COVERAGE &&
                averageLuminance >= HOLD_WHITE_AVERAGE_LUMINANCE &&
                averageChroma <= HOLD_WHITE_AVERAGE_CHROMA

        /**
         * DARK/FIRE is driven by luminance, as required. Chroma is only a guard
         * against saturated dark colors (deep blue/red/etc.) that are visually far
         * from black despite having low weighted luminance.
         *
         * Do not gate on max(R,G,B): capture/filtering can make one channel exceed
         * the luminance threshold even when the sampled region is visibly black.
         */
        fun isFireDark(): Boolean =
            averageLuminance <= FIRE_MAX_LUMINANCE &&
                averageChroma <= FIRE_MAX_CHROMA

        fun isMeaningfulChangeFrom(reference: ColorSample): Boolean {
            val channelDelta = maxOf(
                kotlin.math.abs(averageRed - reference.averageRed),
                kotlin.math.abs(averageGreen - reference.averageGreen),
                kotlin.math.abs(averageBlue - reference.averageBlue),
            )
            val luminanceDrop = reference.averageLuminance - averageLuminance
            val chromaRise = averageChroma - reference.averageChroma
            val coverageDrop = reference.whiteRatio - whiteRatio
            return channelDelta >= MIN_CHANGE_CHANNEL_DELTA ||
                luminanceDrop >= MIN_CHANGE_LUMINANCE_DROP ||
                chromaRise >= MIN_CHANGE_CHROMA_RISE ||
                coverageDrop >= MIN_CHANGE_WHITE_COVERAGE_DROP
        }
    }

    sealed interface Event {
        data object None : Event
        data class Armed(val sample: ColorSample) : Event
        data class Fired(val firedAtMs: Long) : Event
        data class Rearmed(val sample: ColorSample) : Event
        data class ManualRearmed(val sample: ColorSample) : Event
        data object ManualRearmTimedOut : Event
        data object ManualRearmCleared : Event
    }

    var state: State = State.WAITING_FOR_WHITE
        private set
    var armedWhiteSample: ColorSample? = null
        private set
    var firedAtMs: Long = 0
        private set
    var manualRearmRequestedAtMs: Long = 0
        private set

    private var whiteFrames: Int = 0
    private var changedFrames: Int = 0
    private var manualRearmWhiteFrames: Int = 0

    fun processSample(sample: ColorSample, nowMs: Long): Event {
        val manualEvent = processOneTimeRearmOverride(sample, nowMs)
        if (manualEvent is Event.ManualRearmed || manualEvent is Event.ManualRearmTimedOut) {
            return manualEvent
        }
        return updateTriggerState(sample, nowMs)
    }

    fun requestOneTimeRearmOverride(nowMs: Long): Boolean {
        if (!whiteRearmEnabled || !rearmDelayEnabled || state != State.WAITING_REARM) return false
        manualRearmRequestedAtMs = nowMs
        manualRearmWhiteFrames = 0
        return true
    }

    fun resetForSensorMove() {
        state = State.WAITING_FOR_WHITE
        clearOneTimeRearmRequest()
        armedWhiteSample = null
        whiteFrames = 0
        changedFrames = 0
    }

    private fun processOneTimeRearmOverride(sample: ColorSample, nowMs: Long): Event {
        val requestedAt = manualRearmRequestedAtMs
        if (requestedAt == 0L) return Event.None

        if (state != State.WAITING_REARM || !whiteRearmEnabled || !rearmDelayEnabled) {
            clearOneTimeRearmRequest()
            return Event.ManualRearmCleared
        }
        if (nowMs - requestedAt < MANUAL_REARM_MENU_SETTLE_MS) return Event.None

        manualRearmWhiteFrames = if (sample.isArmingWhite()) manualRearmWhiteFrames + 1 else 0
        if (manualRearmWhiteFrames >= MANUAL_REARM_WHITE_FRAMES) {
            clearOneTimeRearmRequest()
            arm(sample)
            return Event.ManualRearmed(sample)
        }
        if (nowMs - requestedAt >= MANUAL_REARM_TIMEOUT_MS) {
            clearOneTimeRearmRequest()
            return Event.ManualRearmTimedOut
        }
        return Event.None
    }

    private fun updateTriggerState(sample: ColorSample, nowMs: Long): Event = when (state) {
        State.WAITING_FOR_WHITE -> {
            // Only white can arm. Colored/neutral/dark samples never arm by themselves.
            whiteFrames = if (sample.isArmingWhite()) whiteFrames + 1 else 0
            if (whiteFrames >= REQUIRED_ARM_FRAMES) {
                arm(sample)
                Event.Armed(sample)
            } else Event.None
        }

        State.ARMED -> {
            // White and every non-dark color keep ARMED. The very first DARK frame fires.
            if (sample.isFireDark()) {
                fire(nowMs)
                Event.Fired(nowMs)
            } else {
                Event.None
            }
        }

        State.WAITING_REARM -> {
            // Rearming remains white-only and always requires three consecutive frames.
            whiteFrames = if (sample.isArmingWhite()) whiteFrames + 1 else 0
            val whiteReady = whiteRearmEnabled && whiteFrames >= REQUIRED_REARM_FRAMES
            val delayReady = !rearmDelayEnabled || nowMs - firedAtMs >= rearmSeconds * 1000L
            if (whiteReady && delayReady) {
                arm(sample)
                Event.Rearmed(sample)
            } else Event.None
        }
    }

    private fun arm(sample: ColorSample) {
        state = State.ARMED
        armedWhiteSample = sample
        whiteFrames = 0
        changedFrames = 0
    }

    private fun fire(nowMs: Long) {
        state = State.WAITING_REARM
        clearOneTimeRearmRequest()
        armedWhiteSample = null
        firedAtMs = nowMs
        whiteFrames = 0
        changedFrames = 0
    }

    private fun clearOneTimeRearmRequest() {
        manualRearmRequestedAtMs = 0L
        manualRearmWhiteFrames = 0
    }

    companion object {
        const val WHITE_PIXEL_LUMINANCE = 195
        const val WHITE_PIXEL_MIN_CHANNEL = 175
        const val WHITE_PIXEL_MAX_CHROMA = 55
        const val MIN_SAMPLE_PIXELS = 3

        const val ARM_WHITE_AVERAGE_LUMINANCE = 195
        const val ARM_WHITE_AVERAGE_CHROMA = 50
        const val ARM_WHITE_COVERAGE = 0.60f

        const val HOLD_WHITE_AVERAGE_LUMINANCE = 170
        const val HOLD_WHITE_AVERAGE_CHROMA = 70
        const val HOLD_WHITE_COVERAGE = 0.35f

        const val MIN_CHANGE_CHANNEL_DELTA = 30
        const val MIN_CHANGE_LUMINANCE_DROP = 26
        const val MIN_CHANGE_CHROMA_RISE = 24
        const val MIN_CHANGE_WHITE_COVERAGE_DROP = 0.35f

        /** Inclusive luminance ceiling for a DARK/FIRE sample. */
        const val FIRE_MAX_LUMINANCE = 72
        /** Allows normal capture tint/noise around black while rejecting saturated colors. */
        const val FIRE_MAX_CHROMA = 90

        const val REQUIRED_ARM_FRAMES = 3
        const val REQUIRED_CHANGE_FRAMES = 1
        const val REQUIRED_REARM_FRAMES = 3
        const val SENSOR_DIAMETER_MM = 0.8f

        const val MANUAL_REARM_MENU_SETTLE_MS = 35L
        const val MANUAL_REARM_TIMEOUT_MS = 500L
        const val MANUAL_REARM_WHITE_FRAMES = 3
    }
}

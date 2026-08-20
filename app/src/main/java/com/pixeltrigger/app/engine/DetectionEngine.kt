package com.pixeltrigger.app.engine

/**
 * White-only arming/rearming with predictive FIRE on the first real weakening
 * of the armed white signal.
 *
 * Exactly three consecutive white frames are averaged into a stable baseline.
 * Once ARMED, FIRE happens on either:
 *  - a clear first-frame weakening relative to that baseline, or
 *  - the legacy hard fallback where holding-white coverage is lost.
 *
 * This intentionally predicts disappearance before waiting for the sampled
 * region to fall all the way below the old 35% holding-white threshold.
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
        val darkRatio: Float,
        val averageLuminance: Int,
        val averageChroma: Int,
    ) {
        fun isArmingWhite(): Boolean = whiteRatio >= ARM_WHITE_COVERAGE
        fun isHoldingWhite(): Boolean = whiteRatio >= HOLD_WHITE_COVERAGE
        fun isFireDark(): Boolean = darkRatio >= FIRE_DARK_COVERAGE

        /**
         * Aggressive but noise-aware predictor. One meaningful coverage step is
         * enough to FIRE immediately. Uniform dimming must cross both luminance
         * and channel-drop gates so tiny capture jitter does not false-fire.
         */
        fun isPredictiveWhiteLossFrom(reference: ColorSample): Boolean {
            val coverageDrop = reference.whiteRatio - whiteRatio
            if (coverageDrop >= PREDICTIVE_WHITE_COVERAGE_DROP) return true

            val referenceMin = minOf(reference.averageRed, reference.averageGreen, reference.averageBlue)
            val currentMin = minOf(averageRed, averageGreen, averageBlue)
            val minChannelDrop = referenceMin - currentMin
            val luminanceDrop = reference.averageLuminance - averageLuminance
            val chromaRise = averageChroma - reference.averageChroma

            if (
                luminanceDrop >= PREDICTIVE_LUMINANCE_DROP &&
                minChannelDrop >= PREDICTIVE_MIN_CHANNEL_DROP
            ) return true

            return chromaRise >= PREDICTIVE_CHROMA_RISE &&
                luminanceDrop >= PREDICTIVE_COLOR_LUMINANCE_DROP
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
    private var manualRearmWhiteFrames: Int = 0

    // Consecutive-white baseline accumulator. It is touched only while arming or
    // rearming, never in the steady ARMED hot path.
    private var whiteRedSum = 0L
    private var whiteGreenSum = 0L
    private var whiteBlueSum = 0L
    private var whiteRatioSum = 0f
    private var whiteDarkRatioSum = 0f
    private var whiteLuminanceSum = 0L
    private var whiteChromaSum = 0L

    fun processSample(
        sample: ColorSample,
        nowMs: Long,
        @Suppress("UNUSED_PARAMETER") fireAllowed: Boolean = true,
    ): Event {
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
        resetWhiteSequence()
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
            if (sample.isArmingWhite()) {
                appendWhite(sample)
                if (whiteFrames >= REQUIRED_ARM_FRAMES) {
                    val baseline = averagedWhiteBaseline()
                    arm(baseline)
                    Event.Armed(baseline)
                } else Event.None
            } else {
                resetWhiteSequence()
                Event.None
            }
        }

        State.ARMED -> {
            val reference = armedWhiteSample
            if (
                !sample.isHoldingWhite() ||
                (reference != null && sample.isPredictiveWhiteLossFrom(reference))
            ) {
                fire(nowMs)
                Event.Fired(nowMs)
            } else Event.None
        }

        State.WAITING_REARM -> {
            if (sample.isArmingWhite()) appendWhite(sample) else resetWhiteSequence()
            val whiteReady = whiteRearmEnabled && whiteFrames >= REQUIRED_REARM_FRAMES
            val delayReady = !rearmDelayEnabled || nowMs - firedAtMs >= rearmSeconds * 1000L
            if (whiteReady && delayReady) {
                val baseline = averagedWhiteBaseline()
                arm(baseline)
                Event.Rearmed(baseline)
            } else Event.None
        }
    }

    private fun appendWhite(sample: ColorSample) {
        whiteFrames++
        whiteRedSum += sample.averageRed
        whiteGreenSum += sample.averageGreen
        whiteBlueSum += sample.averageBlue
        whiteRatioSum += sample.whiteRatio
        whiteDarkRatioSum += sample.darkRatio
        whiteLuminanceSum += sample.averageLuminance
        whiteChromaSum += sample.averageChroma
    }

    private fun averagedWhiteBaseline(): ColorSample {
        val count = whiteFrames.coerceAtLeast(1)
        return ColorSample(
            averageRed = (whiteRedSum / count).toInt(),
            averageGreen = (whiteGreenSum / count).toInt(),
            averageBlue = (whiteBlueSum / count).toInt(),
            whiteRatio = whiteRatioSum / count.toFloat(),
            darkRatio = whiteDarkRatioSum / count.toFloat(),
            averageLuminance = (whiteLuminanceSum / count).toInt(),
            averageChroma = (whiteChromaSum / count).toInt(),
        )
    }

    private fun resetWhiteSequence() {
        whiteFrames = 0
        whiteRedSum = 0L
        whiteGreenSum = 0L
        whiteBlueSum = 0L
        whiteRatioSum = 0f
        whiteDarkRatioSum = 0f
        whiteLuminanceSum = 0L
        whiteChromaSum = 0L
    }

    private fun arm(sample: ColorSample) {
        state = State.ARMED
        armedWhiteSample = sample
        resetWhiteSequence()
    }

    private fun fire(nowMs: Long) {
        state = State.WAITING_REARM
        clearOneTimeRearmRequest()
        armedWhiteSample = null
        firedAtMs = nowMs
        resetWhiteSequence()
    }

    private fun clearOneTimeRearmRequest() {
        manualRearmRequestedAtMs = 0L
        manualRearmWhiteFrames = 0
    }

    companion object {
        const val WHITE_PIXEL_LUMINANCE = 190
        const val WHITE_PIXEL_MIN_CHANNEL = 170
        const val WHITE_PIXEL_MAX_CHROMA = 60
        const val MIN_SAMPLE_PIXELS = 3

        const val ARM_WHITE_COVERAGE = 0.50f
        const val HOLD_WHITE_COVERAGE = 0.35f

        // Predictive white-loss gates. These are relative to the averaged three-
        // frame arming baseline, so they can FIRE before the hard 35% fallback.
        const val PREDICTIVE_WHITE_COVERAGE_DROP = 0.15f
        const val PREDICTIVE_LUMINANCE_DROP = 18
        const val PREDICTIVE_MIN_CHANNEL_DROP = 14
        const val PREDICTIVE_CHROMA_RISE = 24
        const val PREDICTIVE_COLOR_LUMINANCE_DROP = 8

        const val ARM_WHITE_AVERAGE_LUMINANCE = 195
        const val ARM_WHITE_AVERAGE_CHROMA = 50
        const val HOLD_WHITE_AVERAGE_LUMINANCE = 170
        const val HOLD_WHITE_AVERAGE_CHROMA = 70

        const val MIN_CHANGE_CHANNEL_DELTA = 30
        const val MIN_CHANGE_LUMINANCE_DROP = 26
        const val MIN_CHANGE_CHROMA_RISE = 24
        const val MIN_CHANGE_WHITE_COVERAGE_DROP = 0.35f

        const val DARK_PIXEL_MAX_LUMINANCE = 88
        const val DARK_PIXEL_MAX_CHANNEL = 118
        const val DARK_PIXEL_MAX_CHROMA = 72
        const val FIRE_DARK_COVERAGE = 0.45f
        const val FIRE_MAX_LUMINANCE = DARK_PIXEL_MAX_LUMINANCE
        const val FIRE_MAX_CHROMA = DARK_PIXEL_MAX_CHROMA

        const val REQUIRED_ARM_FRAMES = 3
        const val REQUIRED_CHANGE_FRAMES = 1
        const val REQUIRED_REARM_FRAMES = 3
        const val SENSOR_DIAMETER_MM = 0.3f

        const val MANUAL_REARM_MENU_SETTLE_MS = 35L
        const val MANUAL_REARM_TIMEOUT_MS = 500L
        const val MANUAL_REARM_WHITE_FRAMES = 3
    }
}

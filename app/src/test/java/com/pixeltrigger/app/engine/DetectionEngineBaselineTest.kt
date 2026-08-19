package com.pixeltrigger.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DetectionEngineBaselineTest {
    private val white = DetectionEngine.ColorSample(240, 240, 240, 0.90f, 240, 0)
    private val neutralBlue = DetectionEngine.ColorSample(45, 170, 205, 0.02f, 150, 160)
    private val neutralGray = DetectionEngine.ColorSample(120, 120, 120, 0.05f, 120, 0)
    private val nearBlack = DetectionEngine.ColorSample(19, 24, 29, 0.00f, 23, 10)
    // Typical capture/filter tint around a region that is still visibly black.
    // One channel exceeds the luminance threshold, so the old max-channel gate rejected it.
    private val tintedNearBlack = DetectionEngine.ColorSample(45, 60, 100, 0.00f, 59, 55)
    private val lowLuminanceSaturatedBlue = DetectionEngine.ColorSample(0, 0, 150, 0.00f, 17, 150)

    @Test fun armingNeedsThreeConsecutiveWhiteFrames() {
        val e = DetectionEngine()
        assertTrue(e.processSample(white, 1) is DetectionEngine.Event.None)
        assertTrue(e.processSample(white, 2) is DetectionEngine.Event.None)
        assertTrue(e.processSample(white, 3) is DetectionEngine.Event.Armed)
        assertEquals(DetectionEngine.State.ARMED, e.state)
        assertEquals(white, e.armedWhiteSample)
    }

    @Test fun neutralOrDarkCannotArmByThemselves() {
        val e = DetectionEngine()
        val samples = listOf(neutralBlue, neutralGray, nearBlack, tintedNearBlack, lowLuminanceSaturatedBlue)
        repeat(15) { index ->
            assertTrue(e.processSample(samples[index % samples.size], index.toLong()) is DetectionEngine.Event.None)
        }
        assertEquals(DetectionEngine.State.WAITING_FOR_WHITE, e.state)
    }

    @Test fun nonWhiteBreaksConsecutiveWhiteArmingSequence() {
        val e = DetectionEngine()
        assertTrue(e.processSample(white, 1) is DetectionEngine.Event.None)
        assertTrue(e.processSample(white, 2) is DetectionEngine.Event.None)
        assertTrue(e.processSample(neutralBlue, 3) is DetectionEngine.Event.None)
        assertTrue(e.processSample(white, 4) is DetectionEngine.Event.None)
        assertTrue(e.processSample(white, 5) is DetectionEngine.Event.None)
        assertTrue(e.processSample(white, 6) is DetectionEngine.Event.Armed)
    }

    @Test fun neutralColorsKeepAlreadyArmedEngineArmed() {
        val e = DetectionEngine()
        e.processSample(white, 1); e.processSample(white, 2); e.processSample(white, 3)
        assertTrue(e.processSample(neutralBlue, 4) is DetectionEngine.Event.None)
        assertTrue(e.processSample(neutralGray, 5) is DetectionEngine.Event.None)
        assertTrue(e.processSample(lowLuminanceSaturatedBlue, 6) is DetectionEngine.Event.None)
        assertEquals(DetectionEngine.State.ARMED, e.state)
    }

    @Test fun firstNearBlackFrameFires() {
        val e = DetectionEngine()
        e.processSample(white, 1); e.processSample(white, 2); e.processSample(white, 3)
        assertTrue(e.processSample(nearBlack, 10) is DetectionEngine.Event.Fired)
        assertEquals(DetectionEngine.State.WAITING_REARM, e.state)
        assertEquals(null, e.armedWhiteSample)
    }

    @Test fun tintedNearBlackWithOneChannelAbove72StillFires() {
        val e = DetectionEngine()
        e.processSample(white, 1); e.processSample(white, 2); e.processSample(white, 3)
        assertTrue(e.processSample(tintedNearBlack, 10) is DetectionEngine.Event.Fired)
        assertEquals(DetectionEngine.State.WAITING_REARM, e.state)
    }

    @Test fun luminanceAboveDarkThresholdDoesNotFire() {
        val e = DetectionEngine()
        e.processSample(white, 1); e.processSample(white, 2); e.processSample(white, 3)
        val aboveThresholdGray = DetectionEngine.ColorSample(73, 73, 73, 0.00f, 73, 0)
        assertTrue(e.processSample(aboveThresholdGray, 4) is DetectionEngine.Event.None)
        assertEquals(DetectionEngine.State.ARMED, e.state)
    }

    @Test fun saturatedLowLuminanceColorDoesNotFire() {
        val e = DetectionEngine()
        e.processSample(white, 1); e.processSample(white, 2); e.processSample(white, 3)
        assertTrue(e.processSample(lowLuminanceSaturatedBlue, 4) is DetectionEngine.Event.None)
        assertEquals(DetectionEngine.State.ARMED, e.state)
    }

    @Test fun nonWhiteButNotNearBlackDoesNotFire() {
        val e = DetectionEngine()
        e.processSample(white, 1); e.processSample(white, 2); e.processSample(white, 3)
        val justNotArmingWhite = DetectionEngine.ColorSample(240, 240, 240, 0.59f, 240, 0)
        assertTrue(e.processSample(justNotArmingWhite, 4) is DetectionEngine.Event.None)
        assertEquals(DetectionEngine.State.ARMED, e.state)
    }

    @Test fun allFrozenV212ArmingConstantsStayExact() {
        assertEquals(195, DetectionEngine.WHITE_PIXEL_LUMINANCE)
        assertEquals(175, DetectionEngine.WHITE_PIXEL_MIN_CHANNEL)
        assertEquals(55, DetectionEngine.WHITE_PIXEL_MAX_CHROMA)
        assertEquals(3, DetectionEngine.MIN_SAMPLE_PIXELS)
        assertEquals(0.60f, DetectionEngine.ARM_WHITE_COVERAGE)
        assertEquals(195, DetectionEngine.ARM_WHITE_AVERAGE_LUMINANCE)
        assertEquals(50, DetectionEngine.ARM_WHITE_AVERAGE_CHROMA)
        assertEquals(0.35f, DetectionEngine.HOLD_WHITE_COVERAGE)
        assertEquals(170, DetectionEngine.HOLD_WHITE_AVERAGE_LUMINANCE)
        assertEquals(70, DetectionEngine.HOLD_WHITE_AVERAGE_CHROMA)
        assertEquals(30, DetectionEngine.MIN_CHANGE_CHANNEL_DELTA)
        assertEquals(26, DetectionEngine.MIN_CHANGE_LUMINANCE_DROP)
        assertEquals(24, DetectionEngine.MIN_CHANGE_CHROMA_RISE)
        assertEquals(0.35f, DetectionEngine.MIN_CHANGE_WHITE_COVERAGE_DROP)
        assertEquals(72, DetectionEngine.FIRE_MAX_LUMINANCE)
        assertEquals(90, DetectionEngine.FIRE_MAX_CHROMA)
        assertEquals(3, DetectionEngine.REQUIRED_ARM_FRAMES)
        assertEquals(1, DetectionEngine.REQUIRED_CHANGE_FRAMES)
        assertEquals(3, DetectionEngine.REQUIRED_REARM_FRAMES)
        assertEquals(0.8f, DetectionEngine.SENSOR_DIAMETER_MM)
        assertEquals(35L, DetectionEngine.MANUAL_REARM_MENU_SETTLE_MS)
        assertEquals(500L, DetectionEngine.MANUAL_REARM_TIMEOUT_MS)
        assertEquals(3, DetectionEngine.MANUAL_REARM_WHITE_FRAMES)
    }
}

package com.pixeltrigger.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DetectionEngineBaselineTest {
    private val white = DetectionEngine.ColorSample(240, 240, 240, 0.90f, 240, 0)
    private val dark = DetectionEngine.ColorSample(120, 120, 120, 0.05f, 120, 0)

    @Test fun armingNeedsThreeConsecutiveFrames() {
        val e = DetectionEngine()
        assertTrue(e.processSample(white, 1) is DetectionEngine.Event.None)
        assertTrue(e.processSample(white, 2) is DetectionEngine.Event.None)
        assertTrue(e.processSample(white, 3) is DetectionEngine.Event.Armed)
        assertEquals(DetectionEngine.State.ARMED, e.state)
        assertEquals(white, e.armedWhiteSample)
    }

    @Test fun firstClearlyNonWhiteFrameFires() {
        val e = DetectionEngine()
        e.processSample(white, 1); e.processSample(white, 2); e.processSample(white, 3)
        assertTrue(e.processSample(dark, 10) is DetectionEngine.Event.Fired)
        assertEquals(DetectionEngine.State.WAITING_REARM, e.state)
        assertEquals(null, e.armedWhiteSample)
    }

    @Test fun firstFrameBelowArmingWhiteFiresEvenWithoutOldMeaningfulChange() {
        val e = DetectionEngine()
        e.processSample(white, 1); e.processSample(white, 2); e.processSample(white, 3)

        // RGB/luminance/chroma are unchanged and white coverage drops only 0.31.
        // Under the old meaningfulChange logic this would NOT fire because the
        // coverage-drop threshold was 0.35 and holding-white would still be true.
        val justNotArmingWhite = DetectionEngine.ColorSample(240, 240, 240, 0.59f, 240, 0)
        assertTrue(e.processSample(justNotArmingWhite, 4) is DetectionEngine.Event.Fired)
        assertEquals(DetectionEngine.State.WAITING_REARM, e.state)
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
        assertEquals(3, DetectionEngine.REQUIRED_ARM_FRAMES)
        assertEquals(1, DetectionEngine.REQUIRED_CHANGE_FRAMES)
        assertEquals(3, DetectionEngine.REQUIRED_REARM_FRAMES)
        assertEquals(0.8f, DetectionEngine.SENSOR_DIAMETER_MM)
        assertEquals(35L, DetectionEngine.MANUAL_REARM_MENU_SETTLE_MS)
        assertEquals(500L, DetectionEngine.MANUAL_REARM_TIMEOUT_MS)
        assertEquals(2, DetectionEngine.MANUAL_REARM_WHITE_FRAMES)
    }
}

package com.pixeltrigger.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DetectionEngineBaselineTest {
    private fun sample(
        r: Int,
        g: Int,
        b: Int,
        whiteRatio: Float,
        darkRatio: Float,
        luminance: Int,
        chroma: Int,
    ) = DetectionEngine.ColorSample(r, g, b, whiteRatio, darkRatio, luminance, chroma)

    private val white = sample(240, 240, 240, 0.90f, 0.00f, 240, 0)
    private val mixedWhite = sample(205, 205, 205, 0.55f, 0.00f, 182, 65)
    private val neutralBlue = sample(45, 170, 205, 0.02f, 0.00f, 150, 160)
    private val neutralGray = sample(120, 120, 120, 0.05f, 0.00f, 120, 0)
    private val nearBlack = sample(19, 24, 29, 0.00f, 0.95f, 23, 10)
    private val mixedNearBlack = sample(70, 77, 91, 0.00f, 0.55f, 79, 35)
    private val lowLuminanceSaturatedBlue = sample(0, 0, 150, 0.00f, 0.00f, 17, 150)

    @Test fun armingNeedsThreeConsecutiveWhiteFrames() {
        val e = DetectionEngine()
        assertTrue(e.processSample(white, 1) is DetectionEngine.Event.None)
        assertTrue(e.processSample(white, 2) is DetectionEngine.Event.None)
        assertTrue(e.processSample(white, 3) is DetectionEngine.Event.Armed)
        assertEquals(DetectionEngine.State.ARMED, e.state)
    }

    @Test fun legacyReadinessFlagCannotDelayDarkFire() {
        val e = DetectionEngine()
        e.processSample(white, 1)
        e.processSample(white, 2)
        e.processSample(white, 3)
        assertTrue(e.processSample(nearBlack, 4, fireAllowed = false) is DetectionEngine.Event.Fired)
        assertEquals(DetectionEngine.State.WAITING_REARM, e.state)
    }

    @Test fun mixedWhiteStillArmsAfterThreeFrames() {
        val e = DetectionEngine()
        assertTrue(e.processSample(mixedWhite, 1) is DetectionEngine.Event.None)
        assertTrue(e.processSample(mixedWhite, 2) is DetectionEngine.Event.None)
        assertTrue(e.processSample(mixedWhite, 3) is DetectionEngine.Event.Armed)
        assertEquals(DetectionEngine.State.ARMED, e.state)
    }

    @Test fun nonWhiteBreaksConsecutiveWhiteSequence() {
        val e = DetectionEngine()
        e.processSample(white, 1)
        e.processSample(white, 2)
        e.processSample(neutralBlue, 3)
        assertTrue(e.processSample(white, 4) is DetectionEngine.Event.None)
        assertTrue(e.processSample(white, 5) is DetectionEngine.Event.None)
        assertTrue(e.processSample(white, 6) is DetectionEngine.Event.Armed)
    }

    @Test fun neutralAndDarkCannotArmFromWaiting() {
        val e = DetectionEngine()
        repeat(12) { index ->
            val s = when (index % 4) {
                0 -> neutralBlue
                1 -> neutralGray
                2 -> nearBlack
                else -> lowLuminanceSaturatedBlue
            }
            assertTrue(e.processSample(s, index.toLong()) is DetectionEngine.Event.None)
        }
        assertEquals(DetectionEngine.State.WAITING_FOR_WHITE, e.state)
    }

    @Test fun neutralColorsKeepArmedState() {
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
    }

    @Test fun mixedNearBlackStillFiresImmediately() {
        val e = DetectionEngine()
        e.processSample(white, 1); e.processSample(white, 2); e.processSample(white, 3)
        assertTrue(e.processSample(mixedNearBlack, 10) is DetectionEngine.Event.Fired)
        assertEquals(DetectionEngine.State.WAITING_REARM, e.state)
    }

    @Test fun insufficientDarkCoverageDoesNotFire() {
        val e = DetectionEngine()
        e.processSample(white, 1); e.processSample(white, 2); e.processSample(white, 3)
        val mostlyNeutral = sample(80, 85, 90, 0.00f, 0.40f, 84, 10)
        assertTrue(e.processSample(mostlyNeutral, 4) is DetectionEngine.Event.None)
        assertEquals(DetectionEngine.State.ARMED, e.state)
    }

    @Test fun saturatedLowLuminanceColorDoesNotFire() {
        val e = DetectionEngine()
        e.processSample(white, 1); e.processSample(white, 2); e.processSample(white, 3)
        assertTrue(e.processSample(lowLuminanceSaturatedBlue, 4) is DetectionEngine.Event.None)
        assertEquals(DetectionEngine.State.ARMED, e.state)
    }

    @Test fun frozenStateRulesRemainExact() {
        assertEquals(3, DetectionEngine.REQUIRED_ARM_FRAMES)
        assertEquals(3, DetectionEngine.REQUIRED_REARM_FRAMES)
        assertEquals(3, DetectionEngine.MANUAL_REARM_WHITE_FRAMES)
        assertEquals(0.3f, DetectionEngine.SENSOR_DIAMETER_MM)
        assertEquals(0.50f, DetectionEngine.ARM_WHITE_COVERAGE)
        assertEquals(0.45f, DetectionEngine.FIRE_DARK_COVERAGE)
        assertEquals(190, DetectionEngine.WHITE_PIXEL_LUMINANCE)
        assertEquals(170, DetectionEngine.WHITE_PIXEL_MIN_CHANNEL)
        assertEquals(60, DetectionEngine.WHITE_PIXEL_MAX_CHROMA)
        assertEquals(88, DetectionEngine.DARK_PIXEL_MAX_LUMINANCE)
        assertEquals(118, DetectionEngine.DARK_PIXEL_MAX_CHANNEL)
        assertEquals(72, DetectionEngine.DARK_PIXEL_MAX_CHROMA)
    }
}

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

    private fun rgb(v: Int): Int = (v shl 16) or (v shl 8) or v

    private fun probeSample(
        p0: Int,
        p1: Int = p0,
        p2: Int = p0,
        p3: Int = p0,
        p4: Int = p0,
        count: Int = 5,
        whiteRatio: Float = 1.0f,
    ): DetectionEngine.ColorSample {
        val values = intArrayOf(p0, p1, p2, p3, p4)
        var r = 0
        var g = 0
        var b = 0
        var i = 0
        while (i < count) {
            val packed = values[i]
            r += (packed ushr 16) and 0xff
            g += (packed ushr 8) and 0xff
            b += packed and 0xff
            i++
        }
        val divisor = count.coerceAtLeast(1)
        val ar = r / divisor
        val ag = g / divisor
        val ab = b / divisor
        val minimum = minOf(ar, ag, ab)
        val maximum = maxOf(ar, ag, ab)
        val chroma = maximum - minimum
        val luminance = ((ar * 54) + (ag * 183) + (ab * 19)) shr 8
        return DetectionEngine.ColorSample(
            averageRed = ar,
            averageGreen = ag,
            averageBlue = ab,
            whiteRatio = whiteRatio,
            darkRatio = 0f,
            averageLuminance = luminance,
            averageChroma = chroma,
            probeCount = count,
            probe0 = p0,
            probe1 = p1,
            probe2 = p2,
            probe3 = p3,
            probe4 = p4,
        )
    }

    private val white = sample(240, 240, 240, 0.90f, 0.00f, 240, 0)
    private val tinyJitterWhite = sample(226, 226, 226, 0.90f, 0.00f, 226, 2)
    private val predictiveFade = sample(220, 220, 220, 0.90f, 0.00f, 220, 2)
    private val predictiveCoverageDrop = sample(238, 238, 238, 0.70f, 0.00f, 238, 1)
    private val mixedWhite = sample(205, 205, 205, 0.55f, 0.00f, 182, 65)
    private val neutralBlue = sample(45, 170, 205, 0.02f, 0.00f, 150, 160)
    private val neutralGray = sample(120, 120, 120, 0.05f, 0.00f, 120, 0)
    private val nearBlack = sample(19, 24, 29, 0.00f, 0.95f, 23, 10)
    private val lowLuminanceSaturatedBlue = sample(0, 0, 150, 0.00f, 0.00f, 17, 150)

    @Test fun armingNeedsThreeConsecutiveWhiteFrames() {
        val e = DetectionEngine()
        assertTrue(e.processSample(white, 1) is DetectionEngine.Event.None)
        assertTrue(e.processSample(white, 2) is DetectionEngine.Event.None)
        assertTrue(e.processSample(white, 3) is DetectionEngine.Event.Armed)
        assertEquals(DetectionEngine.State.ARMED, e.state)
    }

    @Test fun averagedBaselineIgnoresSmallWhiteJitter() {
        val e = DetectionEngine()
        e.processSample(white, 1); e.processSample(white, 2); e.processSample(white, 3)
        assertTrue(e.processSample(tinyJitterWhite, 4) is DetectionEngine.Event.None)
        assertEquals(DetectionEngine.State.ARMED, e.state)
    }

    @Test fun uniformWhiteWeakeningFiresBeforeHardHoldingThreshold() {
        val e = DetectionEngine()
        e.processSample(white, 1); e.processSample(white, 2); e.processSample(white, 3)
        assertTrue(e.processSample(predictiveFade, 4) is DetectionEngine.Event.Fired)
        assertEquals(DetectionEngine.State.WAITING_REARM, e.state)
    }

    @Test fun oneMeaningfulCoverageStepFiresImmediately() {
        val e = DetectionEngine()
        e.processSample(white, 1); e.processSample(white, 2); e.processSample(white, 3)
        assertTrue(e.processSample(predictiveCoverageDrop, 4) is DetectionEngine.Event.Fired)
    }

    @Test fun firstNonWhiteFrameFiresImmediately() {
        val e = DetectionEngine()
        e.processSample(white, 1); e.processSample(white, 2); e.processSample(white, 3)
        assertTrue(e.processSample(neutralBlue, 4) is DetectionEngine.Event.Fired)
        assertEquals(DetectionEngine.State.WAITING_REARM, e.state)
    }

    @Test fun legacyReadinessFlagCannotDelayWhiteDisappearanceFire() {
        val e = DetectionEngine()
        e.processSample(white, 1); e.processSample(white, 2); e.processSample(white, 3)
        assertTrue(e.processSample(neutralGray, 4, fireAllowed = false) is DetectionEngine.Event.Fired)
        assertEquals(DetectionEngine.State.WAITING_REARM, e.state)
    }

    @Test fun darkAlsoFiresOnFirstDisappearanceFrame() {
        val e = DetectionEngine()
        e.processSample(white, 1); e.processSample(white, 2); e.processSample(white, 3)
        assertTrue(e.processSample(nearBlack, 4) is DetectionEngine.Event.Fired)
    }

    @Test fun saturatedBlueAlsoFiresBecauseWhiteHasDisappeared() {
        val e = DetectionEngine()
        e.processSample(white, 1); e.processSample(white, 2); e.processSample(white, 3)
        assertTrue(e.processSample(lowLuminanceSaturatedBlue, 4) is DetectionEngine.Event.Fired)
    }

    @Test fun mixedWhiteStillArmsAfterThreeFrames() {
        val e = DetectionEngine()
        assertTrue(e.processSample(mixedWhite, 1) is DetectionEngine.Event.None)
        assertTrue(e.processSample(mixedWhite, 2) is DetectionEngine.Event.None)
        assertTrue(e.processSample(mixedWhite, 3) is DetectionEngine.Event.Armed)
    }

    @Test fun stableMixedWhiteDoesNotSelfFireAfterArming() {
        val e = DetectionEngine()
        e.processSample(mixedWhite, 1); e.processSample(mixedWhite, 2); e.processSample(mixedWhite, 3)
        assertTrue(e.processSample(mixedWhite, 4) is DetectionEngine.Event.None)
        assertEquals(DetectionEngine.State.ARMED, e.state)
    }

    @Test fun nonWhiteBreaksConsecutiveWhiteSequenceBeforeArming() {
        val e = DetectionEngine()
        e.processSample(white, 1)
        e.processSample(white, 2)
        e.processSample(neutralBlue, 3)
        assertTrue(e.processSample(white, 4) is DetectionEngine.Event.None)
        assertTrue(e.processSample(white, 5) is DetectionEngine.Event.None)
        assertTrue(e.processSample(white, 6) is DetectionEngine.Event.Armed)
    }

    @Test fun nonWhiteCannotArmFromWaiting() {
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

    @Test fun v4FivePointProbeRequiresThreeChangedPoints() {
        val e = DetectionEngine()
        val armedWhite = probeSample(rgb(240))
        e.processSample(armedWhite, 1)
        e.processSample(armedWhite, 2)
        assertTrue(e.processSample(armedWhite, 3) is DetectionEngine.Event.Armed)

        val onlyTwoChanged = probeSample(
            p0 = rgb(205),
            p1 = rgb(205),
            p2 = rgb(240),
            p3 = rgb(240),
            p4 = rgb(240),
            whiteRatio = 0.60f,
        )
        assertTrue(e.processSample(onlyTwoChanged, 4) is DetectionEngine.Event.None)
        assertEquals(DetectionEngine.State.ARMED, e.state)

        val threeChanged = probeSample(
            p0 = rgb(205),
            p1 = rgb(205),
            p2 = rgb(205),
            p3 = rgb(240),
            p4 = rgb(240),
            whiteRatio = 0.40f,
        )
        assertTrue(e.processSample(threeChanged, 5) is DetectionEngine.Event.Fired)
    }

    @Test fun v4OneCapturePixelProbeCanFireImmediately() {
        val e = DetectionEngine()
        val armedWhite = probeSample(rgb(240), count = 1)
        e.processSample(armedWhite, 1)
        e.processSample(armedWhite, 2)
        e.processSample(armedWhite, 3)

        val changed = probeSample(rgb(210), count = 1, whiteRatio = 1.0f)
        assertTrue(e.processSample(changed, 4) is DetectionEngine.Event.Fired)
    }

    @Test fun v4SmallPerPixelJitterDoesNotFire() {
        val e = DetectionEngine()
        val armedWhite = probeSample(rgb(240))
        e.processSample(armedWhite, 1)
        e.processSample(armedWhite, 2)
        e.processSample(armedWhite, 3)

        val jitter = probeSample(rgb(231))
        assertTrue(e.processSample(jitter, 4) is DetectionEngine.Event.None)
        assertEquals(DetectionEngine.State.ARMED, e.state)
    }

    @Test fun v4ThreeWhiteFramesAreAveragedIntoProbeBaseline() {
        val e = DetectionEngine()
        e.processSample(probeSample(rgb(236)), 1)
        e.processSample(probeSample(rgb(240)), 2)
        assertTrue(e.processSample(probeSample(rgb(244)), 3) is DetectionEngine.Event.Armed)

        val baseline = e.armedWhiteSample!!
        assertEquals(rgb(240), baseline.probe0)
        assertEquals(5, baseline.probeCount)
    }

    @Test fun frozenStateRulesRemainExact() {
        assertEquals(3, DetectionEngine.REQUIRED_ARM_FRAMES)
        assertEquals(3, DetectionEngine.REQUIRED_REARM_FRAMES)
        assertEquals(3, DetectionEngine.MANUAL_REARM_WHITE_FRAMES)
        assertEquals(0.3f, DetectionEngine.SENSOR_DIAMETER_MM)
        assertEquals(5, DetectionEngine.MAX_PROBE_POINTS)
        assertEquals(1, DetectionEngine.MIN_SAMPLE_PIXELS)
        assertEquals(18, DetectionEngine.PROBE_CHANNEL_DELTA)
        assertEquals(12, DetectionEngine.PROBE_LUMINANCE_DROP)
        assertEquals(0.50f, DetectionEngine.ARM_WHITE_COVERAGE)
        assertEquals(0.35f, DetectionEngine.HOLD_WHITE_COVERAGE)
        assertEquals(0.15f, DetectionEngine.PREDICTIVE_WHITE_COVERAGE_DROP)
        assertEquals(18, DetectionEngine.PREDICTIVE_LUMINANCE_DROP)
        assertEquals(190, DetectionEngine.WHITE_PIXEL_LUMINANCE)
        assertEquals(170, DetectionEngine.WHITE_PIXEL_MIN_CHANNEL)
        assertEquals(60, DetectionEngine.WHITE_PIXEL_MAX_CHROMA)
    }
}

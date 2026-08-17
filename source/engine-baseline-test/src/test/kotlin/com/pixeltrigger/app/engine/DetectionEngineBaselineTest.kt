package com.pixeltrigger.app.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DetectionEngineBaselineTest {
    private val white = DetectionEngine.ColorSample(
        averageRed = 240,
        averageGreen = 240,
        averageBlue = 240,
        whiteRatio = 0.90f,
        averageLuminance = 240,
        averageChroma = 0,
    )

    private val meaningfulDark = DetectionEngine.ColorSample(
        averageRed = 120,
        averageGreen = 120,
        averageBlue = 120,
        whiteRatio = 0.05f,
        averageLuminance = 120,
        averageChroma = 0,
    )

    @Test
    fun `arming still requires exactly three consecutive arming-white frames`() {
        val engine = DetectionEngine()
        assertIs<DetectionEngine.Event.None>(engine.processSample(white, 1))
        assertIs<DetectionEngine.Event.None>(engine.processSample(white, 2))
        assertEquals(DetectionEngine.State.WAITING_FOR_WHITE, engine.state)
        assertIs<DetectionEngine.Event.Armed>(engine.processSample(white, 3))
        assertEquals(DetectionEngine.State.ARMED, engine.state)
        assertEquals(white, engine.armedWhiteSample)
    }

    @Test
    fun `one meaningful change frame fires after arming`() {
        val engine = armedEngine()
        assertIs<DetectionEngine.Event.Fired>(engine.processSample(meaningfulDark, 10))
        assertEquals(DetectionEngine.State.WAITING_REARM, engine.state)
        assertEquals(null, engine.armedWhiteSample)
        assertEquals(10, engine.firedAtMs)
    }

    @Test
    fun `holding-white sample does not fire`() {
        val engine = armedEngine()
        val holding = DetectionEngine.ColorSample(220, 220, 220, 0.70f, 220, 0)
        repeat(10) { assertIs<DetectionEngine.Event.None>(engine.processSample(holding, 20L + it)) }
        assertEquals(DetectionEngine.State.ARMED, engine.state)
    }

    @Test
    fun `timed rearm still needs both delay and three white frames`() {
        val engine = DetectionEngine(whiteRearmEnabled = true, rearmDelayEnabled = true, rearmSeconds = 5)
        armAndFire(engine, firedAt = 100)
        assertIs<DetectionEngine.Event.None>(engine.processSample(white, 5_099))
        assertIs<DetectionEngine.Event.None>(engine.processSample(white, 5_100))
        assertEquals(DetectionEngine.State.WAITING_REARM, engine.state)
        assertIs<DetectionEngine.Event.Rearmed>(engine.processSample(white, 5_101))
        assertEquals(DetectionEngine.State.ARMED, engine.state)
    }

    @Test
    fun `manual timed-rearm override keeps 35ms settle and two white frames`() {
        val engine = DetectionEngine(whiteRearmEnabled = true, rearmDelayEnabled = true, rearmSeconds = 60)
        armAndFire(engine, firedAt = 100)
        assertTrue(engine.requestOneTimeRearmOverride(1_000))
        assertIs<DetectionEngine.Event.None>(engine.processSample(white, 1_034))
        assertEquals(DetectionEngine.State.WAITING_REARM, engine.state)
        assertIs<DetectionEngine.Event.None>(engine.processSample(white, 1_035))
        assertIs<DetectionEngine.Event.ManualRearmed>(engine.processSample(white, 1_036))
        assertEquals(DetectionEngine.State.ARMED, engine.state)
    }

    @Test
    fun `manual override times out at 500ms without silently arming`() {
        val engine = DetectionEngine(whiteRearmEnabled = true, rearmDelayEnabled = true, rearmSeconds = 60)
        armAndFire(engine, firedAt = 100)
        assertTrue(engine.requestOneTimeRearmOverride(1_000))
        val notWhite = DetectionEngine.ColorSample(10, 10, 10, 0f, 10, 0)
        assertIs<DetectionEngine.Event.ManualRearmTimedOut>(engine.processSample(notWhite, 1_500))
        assertEquals(DetectionEngine.State.WAITING_REARM, engine.state)
    }

    @Test
    fun `all frozen constants remain v2_12 values`() {
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

    private fun armedEngine(): DetectionEngine = DetectionEngine().also {
        it.processSample(white, 1)
        it.processSample(white, 2)
        it.processSample(white, 3)
    }

    private fun armAndFire(engine: DetectionEngine, firedAt: Long) {
        engine.processSample(white, 1)
        engine.processSample(white, 2)
        engine.processSample(white, 3)
        assertIs<DetectionEngine.Event.Fired>(engine.processSample(meaningfulDark, firedAt))
    }
}

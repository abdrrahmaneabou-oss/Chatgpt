import com.pixeltrigger.app.engine.DetectionEngine

fun main() {
    check(DetectionEngine.WHITE_PIXEL_LUMINANCE == 195)
    check(DetectionEngine.WHITE_PIXEL_MIN_CHANNEL == 175)
    check(DetectionEngine.WHITE_PIXEL_MAX_CHROMA == 55)
    check(DetectionEngine.REQUIRED_ARM_FRAMES == 3)
    check(DetectionEngine.REQUIRED_CHANGE_FRAMES == 1)
    check(DetectionEngine.REQUIRED_REARM_FRAMES == 3)
    check(DetectionEngine.SENSOR_DIAMETER_MM == 0.8f)

    val white = DetectionEngine.ColorSample(240, 240, 240, 0.9f, 240, 0)
    val changed = DetectionEngine.ColorSample(120, 120, 120, 0.05f, 120, 0)
    val engine = DetectionEngine()
    check(engine.processSample(white, 1) is DetectionEngine.Event.None)
    check(engine.processSample(white, 2) is DetectionEngine.Event.None)
    check(engine.processSample(white, 3) is DetectionEngine.Event.Armed)
    check(engine.processSample(changed, 4) is DetectionEngine.Event.Fired)
    check(engine.state == DetectionEngine.State.WAITING_REARM)
    println("PixelTrigger v2.12 detection baseline: PASS")
}

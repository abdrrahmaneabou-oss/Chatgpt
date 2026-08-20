from pathlib import Path

p = Path('app/src/main/java/com/pixeltrigger/app/ScreenCaptureService.kt')
s = p.read_text()
original = s

s = s.replace('import com.pixeltrigger.app.input.TapCoordinator\n', '')
s = s.replace('    @Volatile private var lastFrameAgeNs = 0L\n    @Volatile private var lastSamplerNs = 0L\n    @Volatile private var lastFireSubmitNs = 0L\n\n', '')
s = s.replace('    private lateinit var tapCoordinator: TapCoordinator\n', '')
s = s.replace('        tapCoordinator = TapCoordinator(tapEngine)\n', '')

old = '''    private fun processImage(image: Image) {\n        val processStartedNs = SystemClock.elapsedRealtimeNanos()\n        val imageTimestampNs = image.timestamp\n        if (imageTimestampNs > 0L && processStartedNs >= imageTimestampNs) {\n            lastFrameAgeNs = processStartedNs - imageTimestampNs\n        }\n\n        if (!engineEnabled || circleEditMode) return\n'''
new = '''    private fun processImage(image: Image) {\n        if (!engineEnabled || circleEditMode) return\n'''
if old not in s and new not in s:
    raise SystemExit('processImage timing block not found')
s = s.replace(old, new)

old = '''        val samplerStartedNs = SystemClock.elapsedRealtimeNanos()\n        val sample = PixelSampler.sampleCircularRegion(image, centerX, centerY, radiusX, radiusY) ?: return\n        lastSamplerNs = SystemClock.elapsedRealtimeNanos() - samplerStartedNs\n\n        // Re-check immediately before the state transition. WHITE detection/arming is\n        // never gated. DARK only consumes FIRE when the one-way Nubia path is ready.\n        val fireAllowedNow = tapEngine.isReady()\n        when (val event = detectionEngine.processSample(sample, SystemClock.elapsedRealtime(), fireAllowed = fireAllowedNow)) {\n            is DetectionEngine.Event.Armed,\n            is DetectionEngine.Event.Rearmed,\n            is DetectionEngine.Event.ManualRearmed ->\n                updateSensorStatus(if (fireAllowedNow) SensorStatus.ARMED else SensorStatus.INPUT_NOT_READY)\n            is DetectionEngine.Event.Fired -> {\n                val submitStartedNs = SystemClock.elapsedRealtimeNanos()\n                executeTapImmediately()\n                lastFireSubmitNs = SystemClock.elapsedRealtimeNanos() - submitStartedNs\n                updateSensorStatus(SensorStatus.FIRED)\n            }\n'''
new = '''        val sample = PixelSampler.sampleCircularRegion(image, centerX, centerY, radiusX, radiusY) ?: return\n\n        // No profiler timestamps, no second readiness read, and no diagnostic gate in\n        // the detector hot path. Predictive white-loss can submit FIRE immediately.\n        when (val event = detectionEngine.processSample(sample, SystemClock.elapsedRealtime())) {\n            is DetectionEngine.Event.Armed,\n            is DetectionEngine.Event.Rearmed,\n            is DetectionEngine.Event.ManualRearmed ->\n                updateSensorStatus(if (inputReady) SensorStatus.ARMED else SensorStatus.INPUT_NOT_READY)\n            is DetectionEngine.Event.Fired -> {\n                executeTapImmediately()\n                updateSensorStatus(SensorStatus.FIRED)\n            }\n'''
if old not in s and new not in s:
    raise SystemExit('sampling/fire block not found')
s = s.replace(old, new)

old = '''        tapCoordinator.fire(tapX, tapY, displayId = 0)\n'''
new = '''        tapEngine.fireFast(tapX, tapY, displayId = 0)\n'''
if old not in s and new not in s:
    raise SystemExit('tapCoordinator FIRE call not found')
s = s.replace(old, new)

old = '''    private fun captureStatsText(): String {\n        fun ms(ns: Long): String = if (ns <= 0L) "n/a" else String.format(java.util.Locale.US, "%.3fms", ns / 1_000_000.0)\n        return "capture=${captureWidth}x${captureHeight} (${(CAPTURE_SCALE * 100).roundToInt()}%); frameAge=${ms(lastFrameAgeNs)}; sampler=${ms(lastSamplerNs)}; fireSubmit=${ms(lastFireSubmitNs)}"\n    }\n'''
new = '''    private fun captureStatsText(): String =\n        "capture=${captureWidth}x${captureHeight} (${(CAPTURE_SCALE * 100).roundToInt()}%); predictive white-loss; profiler=OFF"\n'''
if old not in s and new not in s:
    raise SystemExit('captureStatsText block not found')
s = s.replace(old, new)

if s != original:
    p.write_text(s)
    print('Applied ultrafast predictive ScreenCaptureService migration')
else:
    print('UltraFast migration already applied')

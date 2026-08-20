#!/usr/bin/env python3
from pathlib import Path

path = Path("app/src/main/java/com/pixeltrigger/app/ScreenCaptureService.kt")
text = path.read_text(encoding="utf-8")
original = text

replacements = [
    (
        '''        val imageTimestampNs = image.timestamp\n        if (imageTimestampNs > 0L && captureCallbackNs >= imageTimestampNs) {\n            lastFrameAgeNs = captureCallbackNs - imageTimestampNs\n        }''',
        '''        // Image.timestamp is a foreign/source clock on this device. Keep the raw\n        // value for same-domain cadence forensics only; never subtract it from\n        // elapsedRealtimeNanos or present the result as absolute frame age.\n        val imageTimestampNs = image.timestamp\n        lastFrameAgeNs = 0L''',
    ),
    (
        '''text = "🔬 Nano Latency Profiler\\nنفّذ عدة طلقات ثم اضغط «تحديث تحليل التأخير»."''',
        '''text = "🧪 Professional Latency Lab v2\\nنفّذ عدة طلقات ثم افتح التحليل. لا يتم عرض أي فرق زمني بين clocks غير متزامنة."''',
    ),
    (
        '''menuButton("🔬 تحديث تحليل التأخير بالنانوثانية")''',
        '''menuButton("🧪 تحديث مختبر التأخير الاحترافي")''',
    ),
    (
        '''menuButton("🧹 مسح سجل آخر 64 طلقة")''',
        '''menuButton("🧹 مسح القياسات (512 frame / 128 shot)")''',
    ),
    (
        '''return "capture=${captureWidth}x${captureHeight} (${(CAPTURE_SCALE * 100).roundToInt()}%); " +\n            "frameAge=${metric(lastFrameAgeNs)}; sampler=${metric(lastSamplerNs)}; " +\n            "detection=${metric(lastDetectionNs)}; fireSubmit=${metric(lastFireSubmitNs)}"''',
        '''return "capture=${captureWidth}x${captureHeight} (${(CAPTURE_SCALE * 100).roundToInt()}%); " +\n            "absoluteFrameAge=UNSYNCED(disabled); sampler=${metric(lastSamplerNs)}; " +\n            "detection=${metric(lastDetectionNs)}; fireSubmit=${metric(lastFireSubmitNs)}"''',
    ),
]

for old, new in replacements:
    if old in text:
        text = text.replace(old, new, 1)
    elif new not in text:
        raise SystemExit(f"Profiler v2 migration marker missing:\n{old}")

# Hard invariant: the known invalid cross-clock subtraction must not survive.
for forbidden in (
    "lastFrameAgeNs = captureCallbackNs - imageTimestampNs",
    "frameAge=${metric(lastFrameAgeNs)}",
    "🔬 Nano Latency Profiler",
    "مسح سجل آخر 64 طلقة",
):
    if forbidden in text:
        raise SystemExit(f"Forbidden legacy profiler expression still present: {forbidden}")

if text != original:
    path.write_text(text, encoding="utf-8")
    print("Profiler v2 UI/source migration applied")
else:
    print("Profiler v2 UI/source migration already applied")

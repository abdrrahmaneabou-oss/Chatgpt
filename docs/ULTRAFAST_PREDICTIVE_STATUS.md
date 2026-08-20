# PixelTrigger UltraFast Predictive status

- Unit tests: PASS
- APK build: PASS (clean --rerun-tasks)
- Source commit: 6ed987b5aac7a7ba9fabee39931c88ee3e0db883
- Profiler: REMOVED / OFF
- Monitor diameter: 0.3 mm
- Capture scale: 50%
- Capture thread: THREAD_PRIORITY_URGENT_DISPLAY
- Arming baseline: exactly 3 consecutive WHITE frames, averaged
- Predictive FIRE: first meaningful weakening relative to the 3-frame white baseline
- Predictive coverage drop gate: 0.15
- Predictive uniform luminance drop gate: 18 with minimum-channel drop >= 14
- Hard fallback: white coverage below 0.35
- FIRE path: DetectionEngine -> ShizukuTapEngine.fireFast -> one oneway AIDL -> Nubia virtualTouchEvent
- No TapCoordinator allocation/AtomicLong/timestamp on FIRE hot path
- Exactly one DOWN + one UP; no retry / backup tap / Accessibility fallback
- Requested DOWN/UP contact separation: ~1 ms after DOWN returns
- APK SHA-256: f65fbcd794615d6f606d59095a86123348f28c452eacab52d7bfac37ff8e2c4c

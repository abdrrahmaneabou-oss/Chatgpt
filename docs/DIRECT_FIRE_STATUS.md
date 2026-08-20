# PixelTrigger immediate-fire CI status

- Unit tests: PASS
- APK build: PASS (clean --rerun-tasks)
- Source commit: 6d9671e8e2a60a365d76e020e494a5b7fb3bf8f5
- Monitor diameter: 0.3 mm
- Arming: exactly 3 consecutive WHITE frames
- FIRE: first frame after ARMED that is no longer holding-white; no wait for DARK/black
- Input readiness: diagnostics cannot delay the detector FIRE transition
- Nubia hot path: readiness is latched after successful warm-up and cleared only on real disconnect / explicit unsafe result
- Tap backend: Shizuku shell -> Nubia InputManager.virtualTouchEvent -> InputReader/NubiaGamepad
- Exactly one tap: one DOWN + one UP; no retry / backup press / Accessibility fallback
- Requested DOWN/UP separation: ~1 ms after DOWN returns
- APK SHA-256: d751774f4d13bd12be10ba38f7bd5d99fc7aa33102e92961a84e3a225d352924

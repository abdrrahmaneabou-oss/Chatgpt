# PixelTrigger immediate-fire + ns-profiler CI status

- Unit tests: PASS
- APK build: PASS (clean --rerun-tasks)
- Source commit: 1bc108fd04bb8f60bf11f094552989c72cd125a9
- Monitor diameter: 0.3 mm
- Arming: exactly 3 consecutive WHITE frames
- FIRE: first frame after ARMED that is no longer holding-white; no wait for DARK/black
- Input readiness: diagnostics cannot delay the detector FIRE transition
- Nubia hot path: readiness is latched after successful warm-up and cleared only on real disconnect / explicit unsafe result
- Profiler clock: SystemClock.elapsedRealtimeNanos across app + Shizuku UserService
- Profiler stages: frame/callback/sampling/detection/request/Binder queue/UserService dispatch/Nubia DOWN/hold/Nubia UP
- Profiler history: last 64 shots with P50/P95/MAX and automatic largest-stage culprit
- Profiler overhead policy: primitive timestamps only on FIRE; formatting/statistics only when diagnostics UI is opened
- Shizuku UserService interface version: 10 (prevents stale v9 daemon reuse)
- Tap backend: Shizuku shell -> Nubia InputManager.virtualTouchEvent -> InputReader/NubiaGamepad
- Exactly one tap: one DOWN + one UP; no retry / backup press / Accessibility fallback
- Requested DOWN/UP separation: ~1 ms after DOWN returns
- APK SHA-256: 5beb6d0271eba91459d292976c6f8e366ecbc841f95fb55e4204bc10663ac252

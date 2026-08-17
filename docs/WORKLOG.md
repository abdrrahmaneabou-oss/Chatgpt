# Worklog

## v2.12 recovery

- Verified APK SHA-256: `35d3d07465c015ed5509353463a97c2ee3732a9c835a80a5907e159d4d0e5fac`.
- Identified package `com.pixeltrigger.app` as native Android/Kotlin, not Unity.
- Recovered app source with JADX 1.5.6 in a reproducible GitHub Actions workflow.
- Persisted the app-specific recovered files under `recovered/v2.12/` as a read-only reference.

## Engine freeze

- Documented exact white-pixel thresholds, arming/holding thresholds, meaningful-change thresholds, frame counts and rearm behavior in `ENGINE_BASELINE.md`.
- Clean `DetectionEngine` transcription includes normal arming/fire/rearm plus the one-time manual timed-rearm override (35 ms settle, two white frames, 500 ms timeout).

## Input refactor

- Introduced `TapEngine` backend boundary.
- Introduced monotonic `triggerId` requests and `ExactlyOnceTapGate`.
- Added legacy Accessibility backend with `GestureResultCallback` for diagnostics only.
- Next: privileged/root unified multi-touch proxy and device capability probing.

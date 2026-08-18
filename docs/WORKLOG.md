# Worklog

## v2.12 recovery

- Verified APK SHA-256: `35d3d07465c015ed5509353463a97c2ee3732a9c835a80a5907e159d4d0e5fac`.
- Identified native Android/Kotlin package `com.pixeltrigger.app`.
- Recovered the app-specific JADX source and saved a reproducible baseline workflow.

## Detection freeze

- Transcribed v2.12 detection/arming behavior into `DetectionEngine.kt`.
- Added baseline unit tests for three-frame arming, one-frame meaningful-change firing, and all frozen thresholds/constants.
- Added exact circular pixel sampler with v2.12 luminance/white rules.

## No-root input refactor

- Removed the abandoned Root/uinput direction from the v3 source tree.
- Added Shizuku 13.1.5 API/provider dependencies.
- Added Shizuku UserService and strict shell-UID-2000 policy.
- Added direct hidden `InputManager.injectInputEvent()` backend.
- Added concurrent-touch capability probe using `enable_multi_device_same_window_stream`.
- Added exactly-once trigger IDs in both client and remote process.
- Hard-coded synthetic event timestamps to DOWN at `t`, UP at `t+1ms`.
- Removed Accessibility as an automatic input backend.

## Overlay refactor

- PT button: draggable; single-tap menu; double-tap engine OFF/ON.
- Engine OFF resets to `WAITING_FOR_WHITE`; ON restarts from the same state.
- Sensor/target are non-touchable while playing and draggable while the menu is open.
- Menu is scrollable, landscape-bounded, and draggable by its header; position is persisted.

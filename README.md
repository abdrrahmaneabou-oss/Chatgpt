# PixelTrigger v3 — Shizuku / No Root

Development branch for the PixelTrigger v2.12 input-engine refactor.

## Non-negotiable behavior

1. The v2.12 white detector and arming state machine are frozen. See `docs/ENGINE_BASELINE.md`.
2. Root is forbidden. The new primary backend accepts only Shizuku started through ADB/Wireless debugging (shell UID 2000).
3. Accessibility `dispatchGesture()` is **not** used as a silent fallback because it can cancel an in-progress player gesture.
4. One detector FIRE produces at most one tap request. Duplicate guards exist in both the app process and Shizuku UserService.
5. Synthetic contact is encoded as `DOWN` then `UP` with a requested event-time separation of **1 ms**.
6. Strict mode blocks injection when the Android build cannot verify concurrent multi-device touch support. A missed trigger is preferable to cancelling the player's running/dragging/jumping touch stream.

## New input path

`MediaProjection -> v2.12 Detector -> FIRE -> TapCoordinator -> Shizuku UserService (UID 2000) -> InputManager.injectInputEvent()`

The UserService probes Android's `enable_multi_device_same_window_stream` feature before allowing injection. No Root/uinput `/dev/input` implementation exists in this branch.

## Overlay behavior

- Small draggable `PT` floating button.
- Single tap: open/close menu.
- Fast double tap: engine OFF/ON. OFF clears arming state; ON starts from `WAITING_FOR_WHITE`.
- Sensor and target circles are `FLAG_NOT_TOUCHABLE` during gameplay so they do not block player input. They become draggable only while the configuration menu is open.
- The menu is scrollable, clamped to the current screen bounds, landscape-safe, and draggable by its header.

## Build

The GitHub Actions workflow `.github/workflows/android-ci.yml` runs unit tests and builds the debug APK. The APK is uploaded as the `PixelTrigger-v3-debug` workflow artifact.

## Baseline

Original APK SHA-256:

`35d3d07465c015ed5509353463a97c2ee3732a9c835a80a5907e159d4d0e5fac`

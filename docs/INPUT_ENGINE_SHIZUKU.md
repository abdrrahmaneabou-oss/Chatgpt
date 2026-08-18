# PixelTrigger v3 Shizuku Input Engine

## Goal

Produce one extremely short synthetic tap without using Root and without cancelling the player's existing touch stream.

## Why v2.12 input delivery is removed

v2.12 called `AccessibilityService.dispatchGesture()`. Android's accessibility gesture injection model may cancel an in-progress gesture, so it is incompatible with the product requirement that the user must continue dragging/running/jumping while PixelTrigger fires.

## Backend

The v3 primary backend uses a Shizuku `UserService` running as ADB shell UID **2000**. The app explicitly rejects any backend UID other than 2000, including UID 0.

The UserService invokes Android's hidden `InputManager.injectInputEvent()` API directly. The Shizuku UserService process has no normal-app hidden-API restriction.

### Tap encoding

For trigger `N`:

1. app-side `ExactlyOnceTapGate` accepts `N` once;
2. UserService-side `AtomicLong` accepts `N` once;
3. create `ACTION_DOWN` at `t`;
4. create `ACTION_UP` at `t + 1 ms`;
5. inject both using asynchronous input injection for minimum blocking latency;
6. never resend DOWN after it has been accepted; if UP enqueue fails, only an UP cleanup is attempted.

The requested event duration is 1 ms. Actual wall-clock delivery latency is device/kernel/scheduler dependent and must be measured on-device.

## Player-touch safety gate

AOSP historically cancels an existing pointer stream when another input device starts a conflicting stream in the same window. Modern AOSP contains `enable_multi_device_same_window_stream`, intended to allow multiple input devices to remain active in one window simultaneously.

PixelTrigger strict mode reads the feature state using `aflags list` from the shell UserService. Injection is enabled only when the flag is explicitly reported as enabled. If the flag is disabled or cannot be verified, the backend reports NOT READY and refuses to inject rather than risk an `ACTION_CANCEL` to the player.

This is intentionally conservative. On-device testing is still required on each OEM build before claiming a zero-cancellation guarantee.

## No Accessibility fallback

The branch intentionally does not register `TriggerAccessibilityService`. A future compatibility backend may be added only as an explicit user-selectable mode, never as an automatic fallback.

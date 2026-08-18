# PixelTrigger v2.12 Engine Baseline

This document freezes the trigger/detection behavior recovered from `PixelTrigger-v2.12-debug.apk` before the input-engine refactor.

APK SHA-256:
`35d3d07465c015ed5509353463a97c2ee3732a9c835a80a5907e159d4d0e5fac`

## State machine

`WAITING_FOR_WHITE -> ARMED -> WAITING_REARM`

### WAITING_FOR_WHITE
- Increment `whiteFrames` only while `ColorSample.isArmingWhite()` is true.
- Otherwise reset `whiteFrames` to 0.
- Arm at exactly `whiteFrames >= 3`.

### ARMED
- Baseline reference is the `ColorSample` from the frame that completed arming.
- `meaningfulChange = sample.isMeaningfulChangeFrom(reference)`.
- `stillWhite = sample.isHoldingWhite() && !meaningfulChange`.
- While `stillWhite`, reset `changedFrames` to 0.
- Otherwise increment `changedFrames` only when `meaningfulChange` is true; reset to 0 when false.
- Fire at `changedFrames >= 1`.

### WAITING_REARM
- Rearm requires 3 consecutive `isArmingWhite()` frames.
- If timed rearm is enabled, elapsed time since fire must also be at least `rearmSeconds * 1000`.

## Pixel sampling

The monitored area is an ellipse/circle over the screen capture. For every pixel inside the normalized radius:

- `minChannel = min(R,G,B)`
- `maxChannel = max(R,G,B)`
- `chroma = maxChannel - minChannel`
- `luminance = (54*R + 183*G + 19*B) >> 8`

A pixel counts as white iff:

- `luminance >= 195`
- `minChannel >= 175`
- `chroma <= 55`

A sample is rejected when fewer than 3 pixels are available.

## Arming white

`isArmingWhite()` is true iff all are true:

- `whiteRatio >= 0.60`
- `averageLuminance >= 195`
- `averageChroma <= 50`

Required consecutive frames: **3**.

## Holding white

`isHoldingWhite()` is true iff all are true:

- `whiteRatio >= 0.35`
- `averageLuminance >= 170`
- `averageChroma <= 70`

## Meaningful change

Compared with the armed reference sample, a change is meaningful if **any** condition is true:

- `max(abs(dR), abs(dG), abs(dB)) >= 30`
- `referenceLuminance - currentLuminance >= 26`
- `currentChroma - referenceChroma >= 24`
- `referenceWhiteRatio - currentWhiteRatio >= 0.35`

Required change frames: **1**.

## Physical/config constants

- `SENSOR_DIAMETER_MM = 0.8f`
- `MIN_SAMPLE_PIXELS = 3`
- `REQUIRED_ARM_FRAMES = 3`
- `REQUIRED_CHANGE_FRAMES = 1`
- `REQUIRED_REARM_FRAMES = 3`
- Manual rearm menu settle: `35 ms`
- Manual rearm timeout: `500 ms`
- Manual rearm white frames: `2`

## Original tap behavior (to be replaced, not the trigger rules)

- `TAP_DURATION_MS = 1`
- Target overlay is temporarily changed to `FLAG_NOT_TOUCHABLE`.
- Target alpha becomes `0.35`.
- Tap is posted at the front of the main-thread queue.
- `AccessibilityService.dispatchGesture()` sends one 1 ms stroke.
- Overlay flags/alpha are restored after `40 ms`.

The refactor is allowed to replace only this input-execution path. Detection, arming, meaningful-change, fire/rearm state rules above are frozen unless a future change explicitly updates this baseline document.

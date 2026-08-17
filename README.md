# PixelTrigger reconstruction

This branch reconstructs **PixelTrigger v2.12** from the original debug APK and isolates its input-delivery layer so the tap backend can be replaced without changing the trigger detector.

## Source of truth

- Original APK: `PixelTrigger-v2.12-debug.apk`
- SHA-256: `35d3d07465c015ed5509353463a97c2ee3732a9c835a80a5907e159d4d0e5fac`
- `recovered/v2.12/`: read-only JADX reference generated directly from the APK.
- `docs/ENGINE_BASELINE.md`: frozen behavioral specification of white detection, arming, firing and rearming.
- `source/`: clean maintainable source being reconstructed.

## Non-negotiable compatibility rule

The v2.12 detection/arming behavior is frozen during the input-engine refactor. The current work may change **how a FIRE event is delivered as a touch**, but must not silently change:

- white-pixel classification;
- sample geometry/minimum sample size;
- arming and holding-white thresholds;
- meaningful-change thresholds;
- required frame counts;
- timed/white rearm behavior;
- one-time manual rearm override behavior.

## Input-engine work

The old backend is `AccessibilityService.dispatchGesture()`. It remains only as a compatibility backend. The new architecture uses `TapEngine`, `TapCoordinator` and an exactly-once gate so each detector FIRE has one monotonic `triggerId` and can produce at most one injection request.

The target Pro backend is a privileged/root unified multi-touch input proxy. It must relay physical touchscreen contacts and add the PixelTrigger contact as another slot of the same virtual multi-touch stream, rather than starting a competing Android input stream.

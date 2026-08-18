# PixelTrigger direct-fire CI status

- Unit tests: PASS
- APK build: PASS
- Arming: original v2.12 thresholds + 3 consecutive white frames
- FIRE: first captured frame after ARMED that fails isArmingWhite()
- Tap backend: Shizuku shell -> InputManager.injectInputEvent
- Accessibility fallback: none
- Requested DOWN/UP event-time separation: 1 ms
- Capability-flag gate: removed from FIRE path
- APK SHA-256: 311890f001534e21eaa2a6ad3c2e33de1d01a552c5bf5f4fcb1cea171d766e74

# PixelTrigger direct-fire CI status

- Unit tests: PASS
- APK build: PASS
- Arming: original v2.12 thresholds + 3 consecutive white frames
- FIRE: first captured frame after ARMED that fails isArmingWhite()
- Tap backend: Shizuku shell -> InputManager.injectInputEvent
- Accessibility fallback: none
- Requested DOWN/UP event-time separation: 1 ms
- Capability-flag gate: removed from FIRE path
- APK SHA-256: 7769aaed72e652e605531c4ec043b3309e47d6ef86163a58ebcf604564e83676

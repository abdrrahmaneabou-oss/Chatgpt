# PixelTrigger direct-fire CI status

- Unit tests: PASS
- APK build: PASS
- Arming: original v2.12 thresholds + 3 consecutive white frames
- FIRE: first captured frame after ARMED that fails isArmingWhite()
- Tap backend: Shizuku shell -> Nubia InputManager.virtualTouchEvent -> InputReader/NubiaGamepad
- Virtual pointer params: keyCode=-4, DOWN=0, UP=2, mode=1, gamepadId=-2
- Accessibility fallback: none
- Requested DOWN/UP separation: ~1 ms
- Generic InputManager.injectInputEvent: not used
- APK SHA-256: 863e8f1a96de1fff9c7de8781102f95681d06ee627c2b6ae0273a7f1b7d21f28

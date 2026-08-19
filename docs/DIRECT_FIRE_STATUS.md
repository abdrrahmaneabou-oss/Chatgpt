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
- APK SHA-256: d17e19890bff5c4ca2215972ca835f428fc5449a436eaf0db9d5371882852f83

# PixelTrigger direct-fire CI status

- Unit tests: PASS
- APK build: PASS (clean --rerun-tasks)
- Source commit: fcf9399bd31f9d3f9f06a38c0d1c55efece05629
- Monitor diameter: 0.3 mm
- Arming: 3 consecutive WHITE frames; input readiness never resets detection state
- FIRE: first DARK/near-black frame after ARMED, only when Nubia/Shizuku input is ready
- Input unavailable while DARK: stays ARMED; FIRE is not consumed
- NEUTRAL: stays ARMED and never arms from zero
- Tap backend: Shizuku shell -> Nubia InputManager.virtualTouchEvent -> InputReader/NubiaGamepad
- Exactly one tap: one DOWN + one UP; no retry / backup press / Accessibility fallback
- Requested DOWN/UP separation: ~1 ms after DOWN returns
- APK SHA-256: 0f2c5fe3c302e1472d5bd907a455ff57f6e828c92875577c1f8906222f5d5983

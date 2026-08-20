# PixelTrigger v4 PixelProbe status

- Unit tests: PASS
- APK build: PASS (clean --rerun-tasks)
- Source commit: af39777f2a05cf6f39019aa53fe3f73227981685
- Monitor diameter: exactly 0.3 mm
- Arming: exactly 3 consecutive WHITE frames
- FIRE gate: meaningful v4 probe departure AND average luminance <= 90
- Luminance >= 91 stays ARMED and does not FIRE
- No debounce / timer / extra-frame wait in FIRE gate
- Input: one oneway Shizuku AIDL -> Nubia virtualTouchEvent
- No Accessibility fallback / retry / second DOWN
- APK SHA-256: 7af0e5b1ed0a2701251c9236b4538efcb08268e037b1528faa4d25355fe983b5

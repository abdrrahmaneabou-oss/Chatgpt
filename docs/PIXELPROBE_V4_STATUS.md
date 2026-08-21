# PixelTrigger v4 PixelProbe status

- Unit tests: PASS
- APK build: PASS (clean --rerun-tasks)
- Source commit: e23b23710cc84bcb9c472090e4068e603789179d
- Monitor diameter: exactly 0.3 mm
- Arming: exactly 3 consecutive WHITE frames
- FIRE gate: meaningful v4 probe departure AND average luminance <= 90
- Luminance >= 91 stays ARMED and does not FIRE
- No debounce / timer / extra-frame wait in FIRE gate
- Input: one oneway Shizuku AIDL -> Nubia virtualTouchEvent
- No Accessibility fallback / retry / second DOWN
- APK SHA-256: 0c656a673f03ab1616580748745400742541c07f4abc8dff8c3066a3b9f530f6

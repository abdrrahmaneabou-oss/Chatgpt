# PixelTrigger v4 PixelProbe status

- Unit tests: PASS
- APK build: PASS (clean --rerun-tasks)
- Source commit: adbe92cd2243ce4e0926ddcafcaf4264848f68f8
- Monitor diameter: exactly 0.3 mm
- Arming: exactly 3 consecutive WHITE frames
- FIRE gate: meaningful v4 probe departure AND average luminance <= 90
- Luminance >= 91 stays ARMED and does not FIRE
- No debounce / timer / extra-frame wait in FIRE gate
- Input: one oneway Shizuku AIDL -> Nubia virtualTouchEvent
- No Accessibility fallback / retry / second DOWN
- APK SHA-256: cd254b0b0d267ff58e26937846f268924b65aaabb1139d9640821e23640f7844

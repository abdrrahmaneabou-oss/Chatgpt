from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one match, found {count}")
    return text.replace(old, new, 1)

# 1) Preserve all arming thresholds / frame counts, but FIRE on the first frame
# that no longer satisfies the exact arming-white predicate.
engine_path = Path("app/src/main/java/com/pixeltrigger/app/engine/DetectionEngine.kt")
engine = engine_path.read_text()
old_armed = '''        State.ARMED -> {
            val reference = armedWhiteSample
            val meaningfulChange = reference != null && sample.isMeaningfulChangeFrom(reference)
            val stillWhite = sample.isHoldingWhite() && !meaningfulChange
            if (stillWhite) {
                changedFrames = 0
                Event.None
            } else {
                changedFrames = if (meaningfulChange) changedFrames + 1 else 0
                if (changedFrames >= REQUIRED_CHANGE_FRAMES) {
                    fire(nowMs)
                    Event.Fired(nowMs)
                } else Event.None
            }
        }
'''
new_armed = '''        State.ARMED -> {
            // Arming itself is unchanged: same v2.12 white thresholds and 3 consecutive frames.
            // Once ARMED, the first captured frame that is no longer arming-white fires immediately.
            // No meaningful-change threshold, holding-white threshold, debounce frame, or delay is used.
            if (!sample.isArmingWhite()) {
                fire(nowMs)
                Event.Fired(nowMs)
            } else {
                Event.None
            }
        }
'''
engine = replace_once(engine, old_armed, new_armed, "DetectionEngine ARMED block")
engine_path.write_text(engine)

# 2) App-side Shizuku path: direct binder injection. Do not gate the hot path on
# the multi-device flag. Bump UserService version so Shizuku cannot reuse the old gated process.
tap_path = Path("app/src/main/java/com/pixeltrigger/app/input/ShizukuTapEngine.kt")
tap = tap_path.read_text()
tap = replace_once(
    tap,
    '.tag("pixeltrigger-input-v3")\n        .version(3)',
    '.tag("pixeltrigger-input-v4-direct")\n        .version(4)',
    "Shizuku UserService version",
)
tap = replace_once(
    tap,
    '    fun isReady(): Boolean = capability == InputCapability.CONCURRENT_TOUCH_SAFE && remote != null\n',
    '    fun isReady(): Boolean = remote != null\n',
    "Shizuku isReady gate",
)
old_gate = '''        if (capability != InputCapability.CONCURRENT_TOUCH_SAFE) {
            return TapResult.Rejected(request.triggerId, acceptedAt, "unsafe/unknown concurrent-touch capability: $capabilityDetail")
        }
'''
tap = replace_once(tap, old_gate, '', "Shizuku app-side capability gate")
tap_path.write_text(tap)

# 3) UserService: shell UID + InputManager availability are the only prerequisites.
# The concurrent feature flag is informational and never blocks injection.
svc_path = Path("app/src/main/java/com/pixeltrigger/app/input/ShizukuInputUserService.kt")
svc = svc_path.read_text()
start = svc.index("    override fun probeCapability(): Int {")
end = svc.index("    override fun getCapabilityDetail(): String", start)
new_probe = '''    override fun probeCapability(): Int {
        val status = when {
            Process.myUid() != SHELL_UID -> {
                detail = "PixelTrigger requires Shizuku ADB/shell UID 2000; backend uid=${Process.myUid()}"
                STATUS_ROOT_OR_NON_SHELL_REJECTED
            }
            injector == null -> {
                detail = "InputManager.injectInputEvent unavailable on this build"
                STATUS_INJECTOR_UNAVAILABLE
            }
            else -> {
                detail = "Direct Shizuku InputManager ready; no concurrent-touch capability gate"
                STATUS_SAFE
            }
        }
        cachedCapability = status
        return status
    }

'''
svc = svc[:start] + new_probe + svc[end:]
svc = replace_once(
    svc,
    '        if (Process.myUid() != SHELL_UID || cachedCapability != STATUS_SAFE) return STATUS_NOT_READY\n',
    '        if (Process.myUid() != SHELL_UID) return STATUS_ROOT_OR_NON_SHELL_REJECTED\n',
    "UserService hot-path safety gate",
)
svc = svc.replace(
    " * Strict safety policy: injection is enabled only when the device reports Android's\n * multi-device-same-window input stream feature as enabled. This prevents silently falling back\n * to the legacy behavior that can cancel the player's active touch stream.\n",
    " * Direct no-root mode: Shizuku runs this service as ADB shell UID 2000 and injects through\n * InputManager directly. No Accessibility fallback and no capability flag gate are used.\n",
)
svc_path.write_text(svc)

# 4) Tests: preserve arming baseline and prove first non-arming-white frame fires even when
# old meaningful-change thresholds would NOT have fired.
test_path = Path("app/src/test/java/com/pixeltrigger/app/engine/DetectionEngineBaselineTest.kt")
test = test_path.read_text()
anchor = '''    @Test fun oneMeaningfulChangeFrameFires() {
        val e = DetectionEngine()
        e.processSample(white, 1); e.processSample(white, 2); e.processSample(white, 3)
        assertTrue(e.processSample(dark, 10) is DetectionEngine.Event.Fired)
        assertEquals(DetectionEngine.State.WAITING_REARM, e.state)
        assertEquals(null, e.armedWhiteSample)
    }

'''
addition = anchor + '''    @Test fun firstFrameThatStopsMeetingArmingWhiteFiresWithoutMeaningfulChange() {
        val e = DetectionEngine()
        e.processSample(white, 1); e.processSample(white, 2); e.processSample(white, 3)
        // Same RGB/luminance/chroma and only a 0.31 white-coverage drop: the old
        // meaningful-change logic would NOT fire, but this is no longer arming-white (< 0.60).
        val justNotWhite = DetectionEngine.ColorSample(240, 240, 240, 0.59f, 240, 0)
        assertTrue(e.processSample(justNotWhite, 4) is DetectionEngine.Event.Fired)
        assertEquals(DetectionEngine.State.WAITING_REARM, e.state)
    }

'''
test = replace_once(test, anchor, addition, "direct-fire regression test insertion")
test_path.write_text(test)

print("Applied direct first-frame FIRE + Shizuku direct 1ms injection patch")

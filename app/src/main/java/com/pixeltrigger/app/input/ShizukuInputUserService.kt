package com.pixeltrigger.app.input

import android.os.Process
import android.os.SystemClock
import java.lang.reflect.Method
import kotlin.math.roundToInt

/**
 * REDMAGIC/Nubia virtual-touch backend running inside the Shizuku UserService
 * (ADB shell UID 2000).
 *
 * The detector path is unchanged. A FIRE is translated into Nubia's vendor
 * InputManager.virtualTouchEvent() path, which reaches the InputReader/NubiaGamepad
 * virtual-pointer implementation instead of generic injectInputEvent().
 *
 * Values were verified on REDMAGIC 10S Pro / Android 16 against the device's
 * framework and native input libraries, then confirmed with Binder transaction 126:
 * - keyCode = -4 (vendor virtual pointer slot)
 * - action 0 = DOWN, action 2 = UP
 * - mode = 1
 * - gamepadId = -2
 *
 * Important: this service deliberately does NOT keep a monotonic trigger-id gate.
 * The app process and the Shizuku UserService have independent lifetimes, so a
 * persistent remote counter can incorrectly reject valid taps after the app side
 * restarts its local counter.
 */
class ShizukuInputUserService : IShizukuInputService.Stub {
    constructor()
    constructor(@Suppress("UNUSED_PARAMETER") context: android.content.Context)

    @Volatile private var lastDownNs = 0L
    @Volatile private var lastUpNs = 0L
    @Volatile private var detail = "not probed"

    private val nubiaInjector: NubiaVirtualTouchInjector? by lazy {
        NubiaVirtualTouchInjector.create().also {
            detail = it?.detail ?: "Nubia InputManager.virtualTouchEvent unavailable"
        }
    }

    override fun getBackendUid(): Int = Process.myUid()

    override fun probeCapability(): Int {
        return when {
            Process.myUid() != SHELL_UID -> {
                detail = "PixelTrigger requires Shizuku ADB/shell UID 2000; backend uid=${Process.myUid()}"
                STATUS_ROOT_OR_NON_SHELL_REJECTED
            }
            nubiaInjector == null -> {
                if (detail == "not probed") detail = "Nubia InputManager.virtualTouchEvent unavailable"
                STATUS_INJECTOR_UNAVAILABLE
            }
            else -> {
                detail = nubiaInjector!!.detail
                STATUS_SAFE
            }
        }
    }

    override fun getCapabilityDetail(): String = detail

    override fun injectTap(
        triggerId: Long,
        x: Float,
        y: Float,
        requestedDurationMs: Long,
        displayId: Int,
    ): Int {
        if (Process.myUid() != SHELL_UID) return STATUS_ROOT_OR_NON_SHELL_REJECTED
        // triggerId is diagnostic only. Never reject a valid FIRE because counters
        // from two independently-lived processes are not globally monotonic.
        if (triggerId <= 0L) return STATUS_INVALID_ARGUMENT
        if (!x.isFinite() || !y.isFinite()) return STATUS_INVALID_ARGUMENT

        val injector = nubiaInjector ?: return STATUS_INJECTOR_UNAVAILABLE
        @Suppress("UNUSED_VARIABLE")
        val ignoredCallerDuration = requestedDurationMs
        @Suppress("UNUSED_VARIABLE")
        val ignoredDisplayId = displayId // Vendor input service owns internal-display coordinate translation.

        val px = x.roundToInt()
        val py = y.roundToInt()
        var downSent = false
        var upSent = false

        return try {
            lastDownNs = SystemClock.elapsedRealtimeNanos()
            injector.send(ACTION_DOWN, px, py)
            downSent = true

            // Keep only the synthetic contact alive for ~1 ms. There is no
            // pre-DOWN delay on the detector hot path.
            val upDeadlineNs = lastDownNs + TAP_DURATION_NS
            while (SystemClock.elapsedRealtimeNanos() < upDeadlineNs) {
                // Intentional short spin: avoids Handler/sleep scheduling jitter.
            }

            lastUpNs = SystemClock.elapsedRealtimeNanos()
            injector.send(ACTION_UP, px, py)
            upSent = true
            STATUS_OK
        } catch (t: Throwable) {
            detail = "Nubia InputReader virtual-touch error: ${t.javaClass.simpleName}: ${t.message ?: "unknown"}"
            STATUS_EXCEPTION
        } finally {
            // Never leave the vendor virtual pointer held if UP failed after a
            // successful DOWN. A best-effort recovery UP is safer than a stuck slot.
            if (downSent && !upSent) {
                runCatching {
                    lastUpNs = SystemClock.elapsedRealtimeNanos()
                    injector.send(ACTION_UP, px, py)
                }
            }
        }
    }

    override fun getLastDownNs(): Long = lastDownNs
    override fun getLastUpNs(): Long = lastUpNs

    override fun destroy() {
        System.exit(0)
    }

    private class NubiaVirtualTouchInjector(
        private val inputManager: Any,
        private val eventMethod: Method,
    ) {
        val detail: String =
            "REDMAGIC/Nubia InputReader virtual-touch ready (keyCode=$VIRTUAL_KEYCODE, mode=$VIRTUAL_TOUCH_MODE, gamepadId=$VIRTUAL_GAMEPAD_ID)"

        fun send(action: Int, x: Int, y: Int) {
            eventMethod.invoke(
                inputManager,
                VIRTUAL_KEYCODE,
                action,
                VIRTUAL_TOUCH_MODE,
                VIRTUAL_GAMEPAD_ID,
                x,
                y,
            )
        }

        companion object {
            fun create(): NubiaVirtualTouchInjector? {
                return runCatching {
                    val clazz = Class.forName("android.hardware.input.InputManager")
                    val instance = clazz.getDeclaredMethod("getInstance")
                        .apply { isAccessible = true }
                        .invoke(null)

                    val event = clazz.getDeclaredMethod(
                        "virtualTouchEvent",
                        Int::class.javaPrimitiveType,
                        Int::class.javaPrimitiveType,
                        Int::class.javaPrimitiveType,
                        Int::class.javaPrimitiveType,
                        Int::class.javaPrimitiveType,
                        Int::class.javaPrimitiveType,
                    ).apply { isAccessible = true }

                    NubiaVirtualTouchInjector(instance, event)
                }.getOrNull()
            }
        }
    }

    companion object {
        const val SHELL_UID = 2000
        const val TAP_DURATION_NS = 1_000_000L

        // Verified REDMAGIC/Nubia InputReader virtual-touch branch.
        const val VIRTUAL_KEYCODE = -4
        const val ACTION_DOWN = 0
        const val ACTION_MOVE = 1
        const val ACTION_UP = 2
        const val VIRTUAL_TOUCH_MODE = 1
        const val VIRTUAL_GAMEPAD_ID = -2

        const val STATUS_OK = 0
        // Kept for protocol compatibility with older app builds; new code never returns it.
        const val STATUS_DUPLICATE = 1
        const val STATUS_NOT_READY = 2
        const val STATUS_ROOT_OR_NON_SHELL_REJECTED = 3
        const val STATUS_INJECTOR_UNAVAILABLE = 4
        const val STATUS_CONCURRENT_TOUCH_UNSAFE = 5
        const val STATUS_CONCURRENT_TOUCH_UNKNOWN = 6
        const val STATUS_INVALID_ARGUMENT = 7
        const val STATUS_DOWN_REJECTED = 8
        const val STATUS_UP_REJECTED = 9
        const val STATUS_EXCEPTION = 10
        const val STATUS_SAFE = 100
    }
}

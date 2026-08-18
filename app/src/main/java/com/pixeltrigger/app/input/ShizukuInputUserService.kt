package com.pixeltrigger.app.input

import android.os.Process
import android.os.SystemClock
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.roundToInt

/**
 * Experimental REDMAGIC/Nubia virtual-touch backend running inside the Shizuku
 * UserService (ADB shell UID 2000).
 *
 * Reverse-engineered from NubiaGamepad_160 classes.dex on REDMAGIC 10S Pro:
 * - InputManager.setCameraKeyVirtualTouchEnable(boolean)
 * - InputManager.setCameraKeyVirtualTouchMode(int mode, int keyCode)
 * - InputManager.virtualTouchEvent(int keyCode, int action, int virtualType,
 *   int deviceOrPointerId, int x, int y)
 *
 * The detector path is unchanged. A FIRE is translated directly into Nubia's
 * own virtual-touch DOWN and UP instead of generic injectInputEvent().
 */
class ShizukuInputUserService : IShizukuInputService.Stub {
    constructor()
    constructor(@Suppress("UNUSED_PARAMETER") context: android.content.Context)

    private val lastTriggerId = AtomicLong(0L)
    @Volatile private var lastDownNs = 0L
    @Volatile private var lastUpNs = 0L
    @Volatile private var detail = "not probed"

    private val nubiaInjector: NubiaVirtualTouchInjector? by lazy {
        NubiaVirtualTouchInjector.create().also {
            detail = it?.detail ?: "Nubia virtual-touch InputManager extensions unavailable"
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
                if (detail == "not probed") detail = "Nubia virtual-touch InputManager extensions unavailable"
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
        if (triggerId <= 0L || !acceptTriggerId(triggerId)) return STATUS_DUPLICATE
        if (!x.isFinite() || !y.isFinite()) return STATUS_INVALID_ARGUMENT

        val injector = nubiaInjector ?: return STATUS_INJECTOR_UNAVAILABLE
        @Suppress("UNUSED_VARIABLE")
        val ignoredCallerDuration = requestedDurationMs
        @Suppress("UNUSED_VARIABLE")
        val ignoredDisplayId = displayId // Nubia CVT targets the internal game display path itself.

        return try {
            val px = x.roundToInt()
            val py = y.roundToInt()

            lastDownNs = SystemClock.elapsedRealtimeNanos()
            injector.send(ACTION_DOWN, px, py)

            // Keep only the synthetic contact itself alive for ~1 ms. There is no
            // pre-DOWN delay on the detector hot path.
            val upDeadlineNs = lastDownNs + TAP_DURATION_NS
            while (SystemClock.elapsedRealtimeNanos() < upDeadlineNs) {
                // Intentional 1 ms spin: avoids Handler/sleep scheduling jitter.
            }

            lastUpNs = SystemClock.elapsedRealtimeNanos()
            injector.send(ACTION_UP, px, py)
            STATUS_OK
        } catch (t: Throwable) {
            detail = "Nubia virtual-touch error: ${t.javaClass.simpleName}: ${t.message ?: "unknown"}"
            STATUS_EXCEPTION
        }
    }

    override fun getLastDownNs(): Long = lastDownNs
    override fun getLastUpNs(): Long = lastUpNs

    override fun destroy() {
        runCatching { nubiaInjector?.disable() }
        System.exit(0)
    }

    private fun acceptTriggerId(id: Long): Boolean {
        while (true) {
            val previous = lastTriggerId.get()
            if (id <= previous) return false
            if (lastTriggerId.compareAndSet(previous, id)) return true
        }
    }

    private class NubiaVirtualTouchInjector(
        private val inputManager: Any,
        private val enableMethod: Method,
        private val modeMethod: Method,
        private val eventMethod: Method,
    ) {
        val detail: String = "REDMAGIC/Nubia CameraKeyVirtualTouch ready (single mode, keyCode=$KEYCODE_FOCUS, type=$VIRTUAL_TOUCH_TYPE)"

        fun send(action: Int, x: Int, y: Int) {
            eventMethod.invoke(
                inputManager,
                KEYCODE_FOCUS,
                action,
                VIRTUAL_TOUCH_TYPE,
                VIRTUAL_DEVICE_OR_POINTER_ID,
                x,
                y,
            )
        }

        fun disable() {
            enableMethod.invoke(inputManager, false)
        }

        companion object {
            fun create(): NubiaVirtualTouchInjector? {
                return runCatching {
                    val clazz = Class.forName("android.hardware.input.InputManager")
                    val instance = clazz.getDeclaredMethod("getInstance")
                        .apply { isAccessible = true }
                        .invoke(null)

                    val enable = clazz.getDeclaredMethod(
                        "setCameraKeyVirtualTouchEnable",
                        Boolean::class.javaPrimitiveType,
                    ).apply { isAccessible = true }

                    val mode = clazz.getDeclaredMethod(
                        "setCameraKeyVirtualTouchMode",
                        Int::class.javaPrimitiveType,
                        Int::class.javaPrimitiveType,
                    ).apply { isAccessible = true }

                    val event = clazz.getDeclaredMethod(
                        "virtualTouchEvent",
                        Int::class.javaPrimitiveType,
                        Int::class.javaPrimitiveType,
                        Int::class.javaPrimitiveType,
                        Int::class.javaPrimitiveType,
                        Int::class.javaPrimitiveType,
                        Int::class.javaPrimitiveType,
                    ).apply { isAccessible = true }

                    // Nubia's own CvtHelper enables CVT first and uses mode 0 for
                    // CVT_SINGLE_OPT. KEYCODE_FOCUS (80) is CVT_HALF_PRESS_KEY_KEYCODE.
                    enable.invoke(instance, true)
                    mode.invoke(instance, CVT_SINGLE_OPT, KEYCODE_FOCUS)

                    NubiaVirtualTouchInjector(instance, enable, mode, event)
                }.getOrNull()
            }
        }
    }

    companion object {
        const val SHELL_UID = 2000
        const val TAP_DURATION_NS = 1_000_000L

        // Values recovered from NubiaGamepad_160 classes.dex (CvtHelper).
        const val KEYCODE_FOCUS = 80
        const val CVT_SINGLE_OPT = 0
        const val ACTION_DOWN = 0
        const val ACTION_MOVE = 1
        const val ACTION_UP = 2
        const val VIRTUAL_TOUCH_TYPE = 8
        const val VIRTUAL_DEVICE_OR_POINTER_ID = -4

        const val STATUS_OK = 0
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

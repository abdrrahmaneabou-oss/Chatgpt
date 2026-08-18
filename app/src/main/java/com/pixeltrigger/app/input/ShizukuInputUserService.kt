package com.pixeltrigger.app.input

import android.content.Context
import android.os.Process
import android.os.SystemClock
import android.view.InputDevice
import android.view.InputEvent
import android.view.MotionEvent
import java.util.concurrent.atomic.AtomicLong

/**
 * Shizuku UserService running as ADB shell UID 2000.
 *
 * Direct mode deliberately does not block injection on Android's
 * multi-device-same-window feature flag. There is no Accessibility fallback.
 * A detector FIRE is converted immediately to one synthetic DOWN and one UP
 * whose event-time separation is exactly 1 ms.
 */
class ShizukuInputUserService : IShizukuInputService.Stub {
    constructor()
    constructor(@Suppress("UNUSED_PARAMETER") context: Context)

    private val lastTriggerId = AtomicLong(0L)
    @Volatile private var lastDownNs = 0L
    @Volatile private var lastUpNs = 0L
    @Volatile private var detail = "not probed"

    private val injector: FrameworkInputInjector? by lazy { FrameworkInputInjector.create() }

    override fun getBackendUid(): Int = Process.myUid()

    override fun probeCapability(): Int {
        return when {
            Process.myUid() != SHELL_UID -> {
                detail = "PixelTrigger requires Shizuku ADB/shell UID 2000; backend uid=${Process.myUid()}"
                STATUS_ROOT_OR_NON_SHELL_REJECTED
            }
            injector == null -> {
                detail = "InputManager.injectInputEvent unavailable on this build"
                STATUS_INJECTOR_UNAVAILABLE
            }
            else -> {
                detail = "Direct Shizuku InputManager ready; capability flag gate disabled"
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

        val inputInjector = injector ?: return STATUS_INJECTOR_UNAVAILABLE

        // Product invariant: the synthetic contact itself is represented as exactly 1 ms.
        @Suppress("UNUSED_VARIABLE")
        val ignoredCallerDuration = requestedDurationMs
        val downTime = SystemClock.uptimeMillis()
        val upTime = downTime + TAP_DURATION_MS

        val down = MotionEvent.obtain(
            downTime,
            downTime,
            MotionEvent.ACTION_DOWN,
            x,
            y,
            1.0f,
            1.0f,
            0,
            1.0f,
            1.0f,
            0,
            0,
        ).apply {
            source = InputDevice.SOURCE_TOUCHSCREEN
            setDisplayIdCompat(displayId)
        }
        val up = MotionEvent.obtain(
            downTime,
            upTime,
            MotionEvent.ACTION_UP,
            x,
            y,
            0.0f,
            1.0f,
            0,
            1.0f,
            1.0f,
            0,
            0,
        ).apply {
            source = InputDevice.SOURCE_TOUCHSCREEN
            setDisplayIdCompat(displayId)
        }

        return try {
            // MODE_ASYNC avoids waiting for dispatch completion on the detector hot path.
            lastDownNs = SystemClock.elapsedRealtimeNanos()
            if (!inputInjector.inject(down, MODE_ASYNC)) return STATUS_DOWN_REJECTED

            lastUpNs = SystemClock.elapsedRealtimeNanos()
            if (!inputInjector.inject(up, MODE_ASYNC)) {
                // Never resend DOWN. Only attempt to terminate the stream that was already started.
                inputInjector.inject(up, MODE_WAIT_FOR_RESULT)
                return STATUS_UP_REJECTED
            }
            STATUS_OK
        } catch (t: Throwable) {
            detail = "injection error: ${t.javaClass.simpleName}: ${t.message ?: "unknown"}"
            STATUS_EXCEPTION
        } finally {
            down.recycle()
            up.recycle()
        }
    }

    override fun getLastDownNs(): Long = lastDownNs
    override fun getLastUpNs(): Long = lastUpNs

    override fun destroy() {
        System.exit(0)
    }

    private fun acceptTriggerId(id: Long): Boolean {
        while (true) {
            val previous = lastTriggerId.get()
            if (id <= previous) return false
            if (lastTriggerId.compareAndSet(previous, id)) return true
        }
    }

    private fun MotionEvent.setDisplayIdCompat(displayId: Int) {
        if (displayId < 0) return
        runCatching {
            InputEvent::class.java
                .getDeclaredMethod("setDisplayId", Int::class.javaPrimitiveType)
                .apply { isAccessible = true }
                .invoke(this, displayId)
        }
    }

    private class FrameworkInputInjector(
        private val instance: Any,
        private val method: java.lang.reflect.Method,
    ) {
        fun inject(event: InputEvent, mode: Int): Boolean =
            (method.invoke(instance, event, mode) as? Boolean) == true

        companion object {
            fun create(): FrameworkInputInjector? {
                runCatching {
                    val clazz = Class.forName("android.hardware.input.InputManagerGlobal")
                    val instance = clazz.getDeclaredMethod("getInstance").invoke(null)
                    val method = clazz.getDeclaredMethod(
                        "injectInputEvent",
                        InputEvent::class.java,
                        Int::class.javaPrimitiveType,
                    ).apply { isAccessible = true }
                    return FrameworkInputInjector(instance, method)
                }
                runCatching {
                    val clazz = Class.forName("android.hardware.input.InputManager")
                    val instance = clazz.getDeclaredMethod("getInstance").invoke(null)
                    val method = clazz.getDeclaredMethod(
                        "injectInputEvent",
                        InputEvent::class.java,
                        Int::class.javaPrimitiveType,
                    ).apply { isAccessible = true }
                    return FrameworkInputInjector(instance, method)
                }
                return null
            }
        }
    }

    companion object {
        const val SHELL_UID = 2000
        const val TAP_DURATION_MS = 1L

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

        const val MODE_ASYNC = 0
        const val MODE_WAIT_FOR_RESULT = 1
    }
}

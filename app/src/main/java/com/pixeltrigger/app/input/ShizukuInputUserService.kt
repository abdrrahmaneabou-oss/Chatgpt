package com.pixeltrigger.app.input

import android.content.Context
import android.os.Process
import android.os.SystemClock
import android.view.InputDevice
import android.view.InputEvent
import android.view.MotionEvent
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Shizuku UserService: runs as ADB shell UID 2000. Root UID 0 is rejected by design.
 *
 * Strict safety policy: injection is enabled only when the device reports Android's
 * multi-device-same-window input stream feature as enabled. This prevents silently falling back
 * to the legacy behavior that can cancel the player's active touch stream.
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
        if (Process.myUid() != SHELL_UID) {
            detail = "PixelTrigger accepts Shizuku ADB/shell UID 2000 only; backend uid=${Process.myUid()}"
            return STATUS_ROOT_OR_NON_SHELL_REJECTED
        }
        if (injector == null) {
            detail = "InputManager.injectInputEvent unavailable on this build"
            return STATUS_INJECTOR_UNAVAILABLE
        }

        val flag = readConcurrentTouchFlag()
        return when (flag) {
            FlagState.ENABLED -> {
                detail = "enable_multi_device_same_window_stream=enabled; strict concurrent mode ready"
                STATUS_SAFE
            }
            FlagState.DISABLED -> {
                detail = "enable_multi_device_same_window_stream=disabled; injection blocked to protect player touch"
                STATUS_CONCURRENT_TOUCH_UNSAFE
            }
            FlagState.UNKNOWN -> {
                detail = "concurrent-touch feature state could not be verified; injection blocked in strict mode"
                STATUS_CONCURRENT_TOUCH_UNKNOWN
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
        if (probeCapability() != STATUS_SAFE) return STATUS_NOT_READY
        if (triggerId <= 0L || !acceptTriggerId(triggerId)) return STATUS_DUPLICATE
        if (!x.isFinite() || !y.isFinite()) return STATUS_INVALID_ARGUMENT

        val inputInjector = injector ?: return STATUS_INJECTOR_UNAVAILABLE
        // Hard invariant from the product requirement: requested contact is exactly 1 ms.
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
            lastDownNs = SystemClock.elapsedRealtimeNanos()
            if (!inputInjector.inject(down, MODE_ASYNC)) return STATUS_DOWN_REJECTED

            lastUpNs = SystemClock.elapsedRealtimeNanos()
            if (!inputInjector.inject(up, MODE_ASYNC)) {
                // Never resend DOWN. Try only to terminate the already-started synthetic stream.
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

    private fun readConcurrentTouchFlag(): FlagState {
        val commands = listOf(
            arrayOf("/system/bin/aflags", "list"),
            arrayOf("aflags", "list"),
        )
        for (command in commands) {
            val process = runCatching { ProcessBuilder(*command).redirectErrorStream(true).start() }.getOrNull() ?: continue
            val lines = ArrayList<String>()
            BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.contains(FLAG_NAME, ignoreCase = true)) lines += line
                }
            }
            if (!process.waitFor(1, TimeUnit.SECONDS)) process.destroyForcibly()
            val joined = lines.joinToString(" ").lowercase()
            if (joined.isNotBlank()) {
                if (Regex("(^|\\s|=|:)enabled($|\\s)").containsMatchIn(joined)) return FlagState.ENABLED
                if (Regex("(^|\\s|=|:)disabled($|\\s)").containsMatchIn(joined)) return FlagState.DISABLED
                if (joined.contains(" true")) return FlagState.ENABLED
                if (joined.contains(" false")) return FlagState.DISABLED
            }
        }
        return FlagState.UNKNOWN
    }

    private enum class FlagState { ENABLED, DISABLED, UNKNOWN }

    private class FrameworkInputInjector(
        private val instance: Any,
        private val method: java.lang.reflect.Method,
    ) {
        fun inject(event: InputEvent, mode: Int): Boolean =
            (method.invoke(instance, event, mode) as? Boolean) == true

        companion object {
            fun create(): FrameworkInputInjector? {
                // Current Android path.
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
                // Compatibility path for older Android implementations.
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
        const val FLAG_NAME = "enable_multi_device_same_window_stream"

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

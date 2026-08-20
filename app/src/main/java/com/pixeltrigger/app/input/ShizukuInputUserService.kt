package com.pixeltrigger.app.input

import android.os.IBinder
import android.os.Parcel
import android.os.Process
import android.os.SystemClock
import java.lang.reflect.Method
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.roundToInt

/**
 * REDMAGIC/Nubia virtual-touch backend running inside the Shizuku UserService.
 *
 * FIRE remains a one-way request. Profiling stores only primitive nanosecond values
 * on the hot path. Formatting, sorting, percentiles, and culprit analysis happen
 * only when the diagnostics UI explicitly requests a report.
 */
class ShizukuInputUserService : IShizukuInputService.Stub {
    constructor()
    constructor(@Suppress("UNUSED_PARAMETER") context: android.content.Context)

    @Volatile private var lastTriggerId = 0L
    @Volatile private var lastFrameTimestampNs = 0L
    @Volatile private var lastCaptureCallbackNs = 0L
    @Volatile private var lastSampleStartNs = 0L
    @Volatile private var lastSampleEndNs = 0L
    @Volatile private var lastDetectionStartNs = 0L
    @Volatile private var lastFireDecisionNs = 0L
    @Volatile private var lastRequestCreatedNs = 0L
    @Volatile private var lastBinderSubmitStartNs = 0L
    @Volatile private var lastRequestReceivedNs = 0L
    @Volatile private var lastDownCallStartNs = 0L
    @Volatile private var lastDownCallEndNs = 0L
    @Volatile private var lastUpCallStartNs = 0L
    @Volatile private var lastUpCallEndNs = 0L
    @Volatile private var lastDownNs = 0L
    @Volatile private var lastUpNs = 0L
    @Volatile private var detail = "not probed"

    private val traceWriteIndex = AtomicInteger(0)
    private val traceSamples = AtomicInteger(0)
    private val historyFrameToCallbackNs = LongArray(TRACE_CAPACITY) { INVALID_NS }
    private val historySamplerNs = LongArray(TRACE_CAPACITY) { INVALID_NS }
    private val historyDetectionNs = LongArray(TRACE_CAPACITY) { INVALID_NS }
    private val historyDecisionToSubmitNs = LongArray(TRACE_CAPACITY) { INVALID_NS }
    private val historyBinderQueueNs = LongArray(TRACE_CAPACITY) { INVALID_NS }
    private val historyBackendDispatchNs = LongArray(TRACE_CAPACITY) { INVALID_NS }
    private val historyDownCallNs = LongArray(TRACE_CAPACITY) { INVALID_NS }
    private val historyDecisionToDownNs = LongArray(TRACE_CAPACITY) { INVALID_NS }
    private val historyFrameToDownNs = LongArray(TRACE_CAPACITY) { INVALID_NS }

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

    override fun injectTapFast(
        triggerId: Long,
        x: Float,
        y: Float,
        displayId: Int,
        frameTimestampNs: Long,
        captureCallbackNs: Long,
        sampleStartNs: Long,
        sampleEndNs: Long,
        detectionStartNs: Long,
        fireDecisionNs: Long,
        requestCreatedNs: Long,
        binderSubmitStartNs: Long,
    ) {
        val requestReceivedNs = SystemClock.elapsedRealtimeNanos()

        // Store trace seed first so even a rejected/failed injection shows where it stopped.
        lastTriggerId = triggerId
        lastFrameTimestampNs = frameTimestampNs
        lastCaptureCallbackNs = captureCallbackNs
        lastSampleStartNs = sampleStartNs
        lastSampleEndNs = sampleEndNs
        lastDetectionStartNs = detectionStartNs
        lastFireDecisionNs = fireDecisionNs
        lastRequestCreatedNs = requestCreatedNs
        lastBinderSubmitStartNs = binderSubmitStartNs
        lastRequestReceivedNs = requestReceivedNs
        lastDownCallStartNs = 0L
        lastDownCallEndNs = 0L
        lastUpCallStartNs = 0L
        lastUpCallEndNs = 0L

        runCatching { Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_DISPLAY) }

        if (Process.myUid() != SHELL_UID) {
            detail = "tap ignored: UserService uid=${Process.myUid()}"
            return
        }
        if (triggerId <= 0L || !x.isFinite() || !y.isFinite()) {
            detail = "tap ignored: invalid argument"
            return
        }

        val injector = nubiaInjector ?: run {
            detail = "tap ignored: Nubia injector unavailable"
            return
        }
        @Suppress("UNUSED_VARIABLE")
        val ignoredDisplayId = displayId

        val px = x.roundToInt()
        val py = y.roundToInt()
        var downSent = false
        var upSent = false

        try {
            lastDownCallStartNs = SystemClock.elapsedRealtimeNanos()
            injector.send(ACTION_DOWN, px, py)
            lastDownCallEndNs = SystemClock.elapsedRealtimeNanos()
            lastDownNs = lastDownCallEndNs
            downSent = true

            // Requested contact interval only; it begins after synchronous DOWN returns.
            val upDeadlineNs = lastDownCallEndNs + TAP_DURATION_NS
            while (SystemClock.elapsedRealtimeNanos() < upDeadlineNs) {
                // Intentional 1 ms spin to avoid Handler/sleep scheduler jitter.
            }

            lastUpCallStartNs = SystemClock.elapsedRealtimeNanos()
            injector.send(ACTION_UP, px, py)
            lastUpCallEndNs = SystemClock.elapsedRealtimeNanos()
            lastUpNs = lastUpCallEndNs
            upSent = true
            detail = injector.detail
        } catch (t: Throwable) {
            detail = "Nubia virtual-touch error: ${t.javaClass.simpleName}: ${t.message ?: "unknown"}"
        } finally {
            if (downSent && !upSent) {
                runCatching {
                    lastUpCallStartNs = SystemClock.elapsedRealtimeNanos()
                    injector.send(ACTION_UP, px, py)
                    lastUpCallEndNs = SystemClock.elapsedRealtimeNanos()
                    lastUpNs = lastUpCallEndNs
                }
            }
            recordCompletedTrace()
        }
    }

    private fun recordCompletedTrace() {
        val slot = traceWriteIndex.getAndIncrement() and (TRACE_CAPACITY - 1)
        historyFrameToCallbackNs[slot] = delta(lastFrameTimestampNs, lastCaptureCallbackNs)
        historySamplerNs[slot] = delta(lastSampleStartNs, lastSampleEndNs)
        historyDetectionNs[slot] = delta(lastDetectionStartNs, lastFireDecisionNs)
        historyDecisionToSubmitNs[slot] = delta(lastFireDecisionNs, lastBinderSubmitStartNs)
        historyBinderQueueNs[slot] = delta(lastBinderSubmitStartNs, lastRequestReceivedNs)
        historyBackendDispatchNs[slot] = delta(lastRequestReceivedNs, lastDownCallStartNs)
        historyDownCallNs[slot] = delta(lastDownCallStartNs, lastDownCallEndNs)
        historyDecisionToDownNs[slot] = delta(lastFireDecisionNs, lastDownCallEndNs)
        historyFrameToDownNs[slot] = delta(lastFrameTimestampNs, lastDownCallEndNs)
        traceSamples.updateAndGet { current -> if (current < TRACE_CAPACITY) current + 1 else TRACE_CAPACITY }
    }

    override fun getLastDownNs(): Long = lastDownNs
    override fun getLastUpNs(): Long = lastUpNs

    override fun getLatencyDetail(): String {
        return "backend=${nubiaInjector?.kind ?: "none"}; " +
            "binderQueue=${fmtCompact(delta(lastBinderSubmitStartNs, lastRequestReceivedNs))}; " +
            "dispatch=${fmtCompact(delta(lastRequestReceivedNs, lastDownCallStartNs))}; " +
            "downCall=${fmtCompact(delta(lastDownCallStartNs, lastDownCallEndNs))}; " +
            "upCall=${fmtCompact(delta(lastUpCallStartNs, lastUpCallEndNs))}"
    }

    override fun getLatencyTraceReport(): String {
        val stages = listOf(
            Stage("frame → capture callback", delta(lastFrameTimestampNs, lastCaptureCallbackNs)),
            Stage("callback → sample start", delta(lastCaptureCallbackNs, lastSampleStartNs)),
            Stage("pixel sampling", delta(lastSampleStartNs, lastSampleEndNs)),
            Stage("sample end → detection start", delta(lastSampleEndNs, lastDetectionStartNs)),
            Stage("detection / FIRE decision", delta(lastDetectionStartNs, lastFireDecisionNs)),
            Stage("decision → TapRequest", delta(lastFireDecisionNs, lastRequestCreatedNs)),
            Stage("TapRequest → Binder submit", delta(lastRequestCreatedNs, lastBinderSubmitStartNs)),
            Stage("Binder one-way queue", delta(lastBinderSubmitStartNs, lastRequestReceivedNs)),
            Stage("UserService dispatch → DOWN call", delta(lastRequestReceivedNs, lastDownCallStartNs)),
            Stage("Nubia DOWN transact", delta(lastDownCallStartNs, lastDownCallEndNs)),
            Stage("DOWN return → UP call", delta(lastDownCallEndNs, lastUpCallStartNs)),
            Stage("Nubia UP transact", delta(lastUpCallStartNs, lastUpCallEndNs)),
        )
        val culprit = stages.filter { it.ns >= 0L }.maxByOrNull { it.ns }
        val sampleCount = traceSamples.get().coerceIn(0, TRACE_CAPACITY)

        return buildString(2400) {
            append("🔬 PixelTrigger ns profiler — trigger #").append(lastTriggerId).append('\n')
            append("Clock: elapsedRealtimeNanos; values are ns timestamps/deltas, not a guarantee of 1 ns physical accuracy.\n\n")

            append("LAST SHOT\n")
            stages.forEachIndexed { index, stage ->
                append(String.format(Locale.US, "%02d. %-31s %s\n", index + 1, stage.name, fmt(stage.ns)))
            }
            append('\n')
            append("TOTAL fire decision → DOWN returned: ")
                .append(fmt(delta(lastFireDecisionNs, lastDownCallEndNs))).append('\n')
            append("TOTAL capture callback → DOWN returned: ")
                .append(fmt(delta(lastCaptureCallbackNs, lastDownCallEndNs))).append('\n')
            append("TOTAL frame timestamp → DOWN returned: ")
                .append(fmt(delta(lastFrameTimestampNs, lastDownCallEndNs))).append('\n')
            append("Requested DOWN → UP hold: ")
                .append(fmt(delta(lastDownCallEndNs, lastUpCallStartNs))).append('\n')

            if (culprit != null) {
                append("\n🚨 Largest measured stage: ").append(culprit.name)
                    .append(" = ").append(fmt(culprit.ns)).append('\n')
            }

            append("\nROLLING STATS — last ").append(sampleCount).append(" shots\n")
            append(statLine("frame→callback", historyFrameToCallbackNs, sampleCount))
            append(statLine("sampling", historySamplerNs, sampleCount))
            append(statLine("detection", historyDetectionNs, sampleCount))
            append(statLine("decision→submit", historyDecisionToSubmitNs, sampleCount))
            append(statLine("Binder queue", historyBinderQueueNs, sampleCount))
            append(statLine("backend dispatch", historyBackendDispatchNs, sampleCount))
            append(statLine("Nubia DOWN", historyDownCallNs, sampleCount))
            append(statLine("decision→DOWN", historyDecisionToDownNs, sampleCount))
            append(statLine("frame→DOWN", historyFrameToDownNs, sampleCount))

            append("\nRAW ns\n")
            append("frame=").append(lastFrameTimestampNs)
                .append(" callback=").append(lastCaptureCallbackNs)
                .append(" sampleStart=").append(lastSampleStartNs)
                .append(" sampleEnd=").append(lastSampleEndNs).append('\n')
            append("detectStart=").append(lastDetectionStartNs)
                .append(" fire=").append(lastFireDecisionNs)
                .append(" request=").append(lastRequestCreatedNs)
                .append(" submit=").append(lastBinderSubmitStartNs).append('\n')
            append("serviceRx=").append(lastRequestReceivedNs)
                .append(" downStart=").append(lastDownCallStartNs)
                .append(" downEnd=").append(lastDownCallEndNs)
                .append(" upStart=").append(lastUpCallStartNs)
                .append(" upEnd=").append(lastUpCallEndNs)
        }
    }

    override fun clearLatencyTraceHistory() {
        traceWriteIndex.set(0)
        traceSamples.set(0)
        listOf(
            historyFrameToCallbackNs,
            historySamplerNs,
            historyDetectionNs,
            historyDecisionToSubmitNs,
            historyBinderQueueNs,
            historyBackendDispatchNs,
            historyDownCallNs,
            historyDecisionToDownNs,
            historyFrameToDownNs,
        ).forEach { it.fill(INVALID_NS) }
    }

    private fun statLine(label: String, source: LongArray, count: Int): String {
        if (count <= 0) return String.format(Locale.US, "%-18s n/a\n", label)
        val valid = source.asSequence().filter { it >= 0L }.take(count).toList().sorted()
        if (valid.isEmpty()) return String.format(Locale.US, "%-18s n/a\n", label)
        fun percentile(p: Double): Long {
            val index = ((valid.size - 1) * p).roundToInt().coerceIn(0, valid.lastIndex)
            return valid[index]
        }
        return String.format(
            Locale.US,
            "%-18s P50=%-12s P95=%-12s MAX=%s\n",
            label,
            fmtCompact(percentile(0.50)),
            fmtCompact(percentile(0.95)),
            fmtCompact(valid.last()),
        )
    }

    override fun destroy() {
        System.exit(0)
    }

    private data class Stage(val name: String, val ns: Long)

    private fun delta(startNs: Long, endNs: Long): Long =
        if (startNs > 0L && endNs >= startNs) endNs - startNs else INVALID_NS

    private fun fmt(ns: Long): String = when {
        ns < 0L -> "n/a"
        ns < 1_000L -> "$ns ns"
        ns < 1_000_000L -> String.format(Locale.US, "%,d ns  |  %.3f µs", ns, ns / 1_000.0)
        else -> String.format(Locale.US, "%,d ns  |  %.3f µs  |  %.6f ms", ns, ns / 1_000.0, ns / 1_000_000.0)
    }

    private fun fmtCompact(ns: Long): String = when {
        ns < 0L -> "n/a"
        ns < 1_000L -> "${ns}ns"
        ns < 1_000_000L -> String.format(Locale.US, "%.3fµs", ns / 1_000.0)
        else -> String.format(Locale.US, "%.6fms", ns / 1_000_000.0)
    }

    private interface NubiaVirtualTouchInjector {
        val detail: String
        val kind: String
        fun send(action: Int, x: Int, y: Int)

        companion object {
            fun create(): NubiaVirtualTouchInjector? =
                DirectBinderInjector.create() ?: ReflectionInjector.create()
        }
    }

    private class DirectBinderInjector(private val binder: IBinder) : NubiaVirtualTouchInjector {
        override val kind: String = "direct-binder-126"
        override val detail: String =
            "REDMAGIC/Nubia direct IInputManager transaction ready (tx=$TRANSACTION_VIRTUAL_TOUCH_EVENT, keyCode=$VIRTUAL_KEYCODE, mode=$VIRTUAL_TOUCH_MODE, gamepadId=$VIRTUAL_GAMEPAD_ID)"

        override fun send(action: Int, x: Int, y: Int) {
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            try {
                data.writeInterfaceToken(INPUT_MANAGER_DESCRIPTOR)
                data.writeInt(VIRTUAL_KEYCODE)
                data.writeInt(action)
                data.writeInt(VIRTUAL_TOUCH_MODE)
                data.writeInt(VIRTUAL_GAMEPAD_ID)
                data.writeInt(x)
                data.writeInt(y)
                if (!binder.transact(TRANSACTION_VIRTUAL_TOUCH_EVENT, data, reply, 0)) {
                    throw UnsupportedOperationException("IInputManager transaction $TRANSACTION_VIRTUAL_TOUCH_EVENT not handled")
                }
                reply.readException()
            } finally {
                reply.recycle()
                data.recycle()
            }
        }

        companion object {
            fun create(): DirectBinderInjector? = runCatching {
                val serviceManager = Class.forName("android.os.ServiceManager")
                val getService = serviceManager.getDeclaredMethod("getService", String::class.java)
                    .apply { isAccessible = true }
                val binder = getService.invoke(null, "input") as? IBinder ?: return@runCatching null
                val descriptor = runCatching { binder.interfaceDescriptor }.getOrNull()
                if (descriptor != INPUT_MANAGER_DESCRIPTOR) return@runCatching null
                DirectBinderInjector(binder)
            }.getOrNull()
        }
    }

    private class ReflectionInjector(
        private val inputManager: Any,
        private val eventMethod: Method,
    ) : NubiaVirtualTouchInjector {
        override val kind: String = "cached-reflection"
        override val detail: String =
            "REDMAGIC/Nubia cached virtualTouchEvent fallback ready (keyCode=$VIRTUAL_KEYCODE, mode=$VIRTUAL_TOUCH_MODE, gamepadId=$VIRTUAL_GAMEPAD_ID)"

        override fun send(action: Int, x: Int, y: Int) {
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
            fun create(): ReflectionInjector? = runCatching {
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
                ReflectionInjector(instance, event)
            }.getOrNull()
        }
    }

    companion object {
        const val SHELL_UID = 2000
        const val TAP_DURATION_NS = 1_000_000L

        const val VIRTUAL_KEYCODE = -4
        const val ACTION_DOWN = 0
        const val ACTION_MOVE = 1
        const val ACTION_UP = 2
        const val VIRTUAL_TOUCH_MODE = 1
        const val VIRTUAL_GAMEPAD_ID = -2

        private const val INPUT_MANAGER_DESCRIPTOR = "android.hardware.input.IInputManager"
        private const val TRANSACTION_VIRTUAL_TOUCH_EVENT = 126
        private const val TRACE_CAPACITY = 64
        private const val INVALID_NS = -1L

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

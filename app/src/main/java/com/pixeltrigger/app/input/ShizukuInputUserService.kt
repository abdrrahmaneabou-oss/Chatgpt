package com.pixeltrigger.app.input

import android.os.IBinder
import android.os.Parcel
import android.os.Process
import android.os.SystemClock
import java.lang.reflect.Method
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * REDMAGIC/Nubia virtual-touch backend running inside the Shizuku UserService.
 *
 * Professional profiler rules:
 * 1) elapsedRealtimeNanos timestamps may be compared across app/UserService processes.
 * 2) Image.timestamp ABSOLUTE values are never compared with elapsedRealtimeNanos.
 * 3) Image.timestamp is retained only as an intra-domain consecutive-frame delta.
 * 4) Hot path stores primitive longs only. Formatting/sorting happens on diagnostics read.
 * 5) Stage names describe what is actually observable; no kernel/scheduler attribution is invented.
 */
class ShizukuInputUserService : IShizukuInputService.Stub {
    constructor()
    constructor(@Suppress("UNUSED_PARAMETER") context: android.content.Context)

    @Volatile private var lastTriggerId = 0L
    @Volatile private var lastRawImageTimestampNs = 0L
    @Volatile private var lastCaptureProcessStartNs = 0L
    @Volatile private var lastSampleStartNs = 0L
    @Volatile private var lastSampleEndNs = 0L
    @Volatile private var lastDetectionStartNs = 0L
    @Volatile private var lastFireDecisionNs = 0L
    @Volatile private var lastRequestCreatedNs = 0L
    @Volatile private var lastAidlSubmitStartNs = 0L
    @Volatile private var lastSamplerEntryGapNs = INVALID_NS
    @Volatile private var lastImageTimestampGapNs = INVALID_NS
    @Volatile private var lastRequestReceivedNs = 0L
    @Volatile private var lastPriorityStartNs = 0L
    @Volatile private var lastPriorityEndNs = 0L
    @Volatile private var lastDownCallStartNs = 0L
    @Volatile private var lastDownCallEndNs = 0L
    @Volatile private var lastUpCallStartNs = 0L
    @Volatile private var lastUpCallEndNs = 0L
    @Volatile private var lastDownNs = 0L
    @Volatile private var lastUpNs = 0L
    @Volatile private var detail = "not probed"

    private val traceWriteIndex = AtomicInteger(0)
    private val traceSamples = AtomicInteger(0)
    private val historyTriggerId = LongArray(TRACE_CAPACITY)
    private val historySamplerEntryGapNs = LongArray(TRACE_CAPACITY) { INVALID_NS }
    private val historyImageTimestampGapNs = LongArray(TRACE_CAPACITY) { INVALID_NS }
    private val historyPreSampleNs = LongArray(TRACE_CAPACITY) { INVALID_NS }
    private val historySamplerNs = LongArray(TRACE_CAPACITY) { INVALID_NS }
    private val historySampleToDetectionNs = LongArray(TRACE_CAPACITY) { INVALID_NS }
    private val historyDetectionNs = LongArray(TRACE_CAPACITY) { INVALID_NS }
    private val historyDecisionToRequestNs = LongArray(TRACE_CAPACITY) { INVALID_NS }
    private val historyRequestToSubmitNs = LongArray(TRACE_CAPACITY) { INVALID_NS }
    private val historyAppToServiceArrivalNs = LongArray(TRACE_CAPACITY) { INVALID_NS }
    private val historyPriorityCallNs = LongArray(TRACE_CAPACITY) { INVALID_NS }
    private val historyServicePrepNs = LongArray(TRACE_CAPACITY) { INVALID_NS }
    private val historyDownCallNs = LongArray(TRACE_CAPACITY) { INVALID_NS }
    private val historyDecisionToDownNs = LongArray(TRACE_CAPACITY) { INVALID_NS }
    private val historyProcessStartToDownNs = LongArray(TRACE_CAPACITY) { INVALID_NS }
    private val historyHoldNs = LongArray(TRACE_CAPACITY) { INVALID_NS }
    private val historyUpCallNs = LongArray(TRACE_CAPACITY) { INVALID_NS }

    private val nubiaInjector: NubiaVirtualTouchInjector? by lazy {
        NubiaVirtualTouchInjector.create().also {
            detail = it?.detail ?: "Nubia InputManager.virtualTouchEvent unavailable"
        }
    }

    override fun getBackendUid(): Int = Process.myUid()

    override fun probeCapability(): Int = when {
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
        samplerEntryGapNs: Long,
        imageTimestampGapNs: Long,
    ) {
        val requestReceivedNs = SystemClock.elapsedRealtimeNanos()

        lastTriggerId = triggerId
        lastRawImageTimestampNs = frameTimestampNs
        lastCaptureProcessStartNs = captureCallbackNs
        lastSampleStartNs = sampleStartNs
        lastSampleEndNs = sampleEndNs
        lastDetectionStartNs = detectionStartNs
        lastFireDecisionNs = fireDecisionNs
        lastRequestCreatedNs = requestCreatedNs
        lastAidlSubmitStartNs = binderSubmitStartNs
        lastSamplerEntryGapNs = samplerEntryGapNs
        lastImageTimestampGapNs = imageTimestampGapNs
        lastRequestReceivedNs = requestReceivedNs
        lastPriorityStartNs = 0L
        lastPriorityEndNs = 0L
        lastDownCallStartNs = 0L
        lastDownCallEndNs = 0L
        lastUpCallStartNs = 0L
        lastUpCallEndNs = 0L

        var downSent = false
        var upSent = false
        var injector: NubiaVirtualTouchInjector? = null
        try {
            lastPriorityStartNs = SystemClock.elapsedRealtimeNanos()
            runCatching { Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_DISPLAY) }
            lastPriorityEndNs = SystemClock.elapsedRealtimeNanos()

            if (Process.myUid() != SHELL_UID) {
                detail = "tap ignored: UserService uid=${Process.myUid()}"
                return
            }
            if (triggerId <= 0L || !x.isFinite() || !y.isFinite()) {
                detail = "tap ignored: invalid argument"
                return
            }

            val activeInjector = nubiaInjector ?: run {
                detail = "tap ignored: Nubia injector unavailable"
                return
            }
            injector = activeInjector
            @Suppress("UNUSED_VARIABLE")
            val ignoredDisplayId = displayId

            val px = x.roundToInt()
            val py = y.roundToInt()

            lastDownCallStartNs = SystemClock.elapsedRealtimeNanos()
            activeInjector.send(ACTION_DOWN, px, py)
            lastDownCallEndNs = SystemClock.elapsedRealtimeNanos()
            lastDownNs = lastDownCallEndNs
            downSent = true

            val upDeadlineNs = lastDownCallEndNs + TAP_DURATION_NS
            while (SystemClock.elapsedRealtimeNanos() < upDeadlineNs) {
                // Intentional contact hold. It is measured separately from DOWN/UP transact time.
            }

            lastUpCallStartNs = SystemClock.elapsedRealtimeNanos()
            activeInjector.send(ACTION_UP, px, py)
            lastUpCallEndNs = SystemClock.elapsedRealtimeNanos()
            lastUpNs = lastUpCallEndNs
            upSent = true
            detail = activeInjector.detail
        } catch (t: Throwable) {
            detail = "Nubia virtual-touch error: ${t.javaClass.simpleName}: ${t.message ?: "unknown"}"
        } finally {
            if (downSent && !upSent) {
                injector?.let { activeInjector ->
                    runCatching {
                        lastUpCallStartNs = SystemClock.elapsedRealtimeNanos()
                        activeInjector.send(ACTION_UP, x.roundToInt(), y.roundToInt())
                        lastUpCallEndNs = SystemClock.elapsedRealtimeNanos()
                        lastUpNs = lastUpCallEndNs
                    }
                }
            }
            recordTrace()
        }
    }

    private fun recordTrace() {
        val slot = traceWriteIndex.getAndIncrement() and (TRACE_CAPACITY - 1)
        historyTriggerId[slot] = lastTriggerId
        historySamplerEntryGapNs[slot] = lastSamplerEntryGapNs
        historyImageTimestampGapNs[slot] = lastImageTimestampGapNs
        historyPreSampleNs[slot] = delta(lastCaptureProcessStartNs, lastSampleStartNs)
        historySamplerNs[slot] = delta(lastSampleStartNs, lastSampleEndNs)
        historySampleToDetectionNs[slot] = delta(lastSampleEndNs, lastDetectionStartNs)
        historyDetectionNs[slot] = delta(lastDetectionStartNs, lastFireDecisionNs)
        historyDecisionToRequestNs[slot] = delta(lastFireDecisionNs, lastRequestCreatedNs)
        historyRequestToSubmitNs[slot] = delta(lastRequestCreatedNs, lastAidlSubmitStartNs)
        historyAppToServiceArrivalNs[slot] = delta(lastAidlSubmitStartNs, lastRequestReceivedNs)
        historyPriorityCallNs[slot] = delta(lastPriorityStartNs, lastPriorityEndNs)
        historyServicePrepNs[slot] = delta(lastRequestReceivedNs, lastDownCallStartNs)
        historyDownCallNs[slot] = delta(lastDownCallStartNs, lastDownCallEndNs)
        historyDecisionToDownNs[slot] = delta(lastFireDecisionNs, lastDownCallEndNs)
        historyProcessStartToDownNs[slot] = delta(lastCaptureProcessStartNs, lastDownCallEndNs)
        historyHoldNs[slot] = delta(lastDownCallEndNs, lastUpCallStartNs)
        historyUpCallNs[slot] = delta(lastUpCallStartNs, lastUpCallEndNs)
        traceSamples.updateAndGet { if (it < TRACE_CAPACITY) it + 1 else TRACE_CAPACITY }
    }

    override fun getLastDownNs(): Long = lastDownNs
    override fun getLastUpNs(): Long = lastUpNs

    override fun getLatencyDetail(): String =
        "backend=${nubiaInjector?.kind ?: "none"}; " +
            "app→service=${fmtCompact(delta(lastAidlSubmitStartNs, lastRequestReceivedNs))}; " +
            "servicePrep=${fmtCompact(delta(lastRequestReceivedNs, lastDownCallStartNs))}; " +
            "downCall=${fmtCompact(delta(lastDownCallStartNs, lastDownCallEndNs))}; " +
            "upCall=${fmtCompact(delta(lastUpCallStartNs, lastUpCallEndNs))}"

    override fun getLatencyTraceReport(): String {
        val intraShotStages = listOf(
            Stage("capture processing start → sample", delta(lastCaptureProcessStartNs, lastSampleStartNs)),
            Stage("PixelSampler (outer wall time)", delta(lastSampleStartNs, lastSampleEndNs)),
            Stage("sample end → detection start", delta(lastSampleEndNs, lastDetectionStartNs)),
            Stage("DetectionEngine wall time", delta(lastDetectionStartNs, lastFireDecisionNs)),
            Stage("FIRE decision → TapRequest created", delta(lastFireDecisionNs, lastRequestCreatedNs)),
            Stage("TapRequest → AIDL submit start", delta(lastRequestCreatedNs, lastAidlSubmitStartNs)),
            Stage("AIDL submit start → UserService receive", delta(lastAidlSubmitStartNs, lastRequestReceivedNs)),
            Stage("setThreadPriority call", delta(lastPriorityStartNs, lastPriorityEndNs)),
            Stage("UserService receive → Nubia DOWN start", delta(lastRequestReceivedNs, lastDownCallStartNs)),
            Stage("Nubia DOWN synchronous transact", delta(lastDownCallStartNs, lastDownCallEndNs)),
            Stage("requested DOWN hold", delta(lastDownCallEndNs, lastUpCallStartNs)),
            Stage("Nubia UP synchronous transact", delta(lastUpCallStartNs, lastUpCallEndNs)),
        )
        val culprit = intraShotStages.filter { it.ns >= 0L }.maxByOrNull { it.ns }
        val sampleCount = traceSamples.get().coerceIn(0, TRACE_CAPACITY)

        return buildString(5200) {
            append("BACKEND / FIRE PIPELINE — trigger #").append(lastTriggerId).append('\n')
            append("Clock domain: elapsedRealtimeNanos only for every cross-process subtraction below.\n")
            append("Image.timestamp absolute value is UNSYNCED and excluded from all latency totals/culprit calculations.\n\n")

            append("TRIGGER-FRAME CADENCE (already-computed clock-safe deltas)\n")
            append("sampler-entry interval: ").append(fmt(lastSamplerEntryGapNs)).append('\n')
            append("source ΔImage.timestamp: ").append(fmt(lastImageTimestampGapNs))
                .append("  [same foreign clock only]\n\n")

            append("LAST SHOT — OBSERVABLE STAGES\n")
            intraShotStages.forEachIndexed { index, stage ->
                append(String.format(Locale.US, "%02d. %-39s %s\n", index + 1, stage.name, fmt(stage.ns)))
            }
            append('\n')
            append("TOTAL FIRE decision → Nubia DOWN return: ")
                .append(fmt(delta(lastFireDecisionNs, lastDownCallEndNs))).append('\n')
            append("TOTAL capture-processing start → Nubia DOWN return: ")
                .append(fmt(delta(lastCaptureProcessStartNs, lastDownCallEndNs))).append('\n')
            append("Requested contact hold: ").append(fmt(delta(lastDownCallEndNs, lastUpCallStartNs))).append('\n')

            if (culprit != null) {
                append("\n🚨 Largest OBSERVABLE intra-shot stage: ")
                    .append(culprit.name).append(" = ").append(fmt(culprit.ns)).append('\n')
            }

            append("\nROLLING FIRE STATS — last ").append(sampleCount).append(" shots\n")
            append(statLine("sampler-entry interval", historySamplerEntryGapNs, sampleCount))
            append(statLine("source image Δtimestamp", historyImageTimestampGapNs, sampleCount))
            append(statLine("pre-sample app work", historyPreSampleNs, sampleCount))
            append(statLine("PixelSampler", historySamplerNs, sampleCount))
            append(statLine("sample→detection", historySampleToDetectionNs, sampleCount))
            append(statLine("DetectionEngine", historyDetectionNs, sampleCount))
            append(statLine("decision→request", historyDecisionToRequestNs, sampleCount))
            append(statLine("request→AIDL submit", historyRequestToSubmitNs, sampleCount))
            append(statLine("app→UserService arrival", historyAppToServiceArrivalNs, sampleCount))
            append(statLine("setThreadPriority", historyPriorityCallNs, sampleCount))
            append(statLine("UserService prep", historyServicePrepNs, sampleCount))
            append(statLine("Nubia DOWN", historyDownCallNs, sampleCount))
            append(statLine("decision→DOWN return", historyDecisionToDownNs, sampleCount))
            append(statLine("process start→DOWN", historyProcessStartToDownNs, sampleCount))
            append(statLine("DOWN hold", historyHoldNs, sampleCount))
            append(statLine("Nubia UP", historyUpCallNs, sampleCount))

            append("\nTOP DECISION→DOWN OUTLIERS\n")
            append(outlierReport(sampleCount))

            append("\nMEASUREMENT BOUNDARIES / DO NOT OVERCLAIM\n")
            append("✓ We can measure: sampler cadence, app processing, cross-process arrival, UserService prep, vendor Binder call duration.\n")
            append("✗ We cannot directly measure from this app: physical OLED pixel-change time → MediaProjection availability.\n")
            append("✗ Nubia DOWN transact return is not proof of the exact instant the target game consumed the event.\n")
            append("✗ app→UserService arrival combines Binder transport + remote scheduling; this app cannot split those two without system tracing.\n")
            append("For kernel scheduler/Binder/InputDispatcher attribution, capture a Perfetto system trace around an incident.\n")

            append("\nRAW CLOCK-SAFE elapsedRealtimeNanos\n")
            append("processStart=").append(lastCaptureProcessStartNs)
                .append(" sampleStart=").append(lastSampleStartNs)
                .append(" sampleEnd=").append(lastSampleEndNs)
                .append(" detectStart=").append(lastDetectionStartNs).append('\n')
            append("fire=").append(lastFireDecisionNs)
                .append(" request=").append(lastRequestCreatedNs)
                .append(" submit=").append(lastAidlSubmitStartNs)
                .append(" serviceRx=").append(lastRequestReceivedNs).append('\n')
            append("priorityStart=").append(lastPriorityStartNs)
                .append(" priorityEnd=").append(lastPriorityEndNs)
                .append(" downStart=").append(lastDownCallStartNs)
                .append(" downEnd=").append(lastDownCallEndNs).append('\n')
            append("upStart=").append(lastUpCallStartNs)
                .append(" upEnd=").append(lastUpCallEndNs).append('\n')
            append("raw Image.timestamp (UNSYNCED, informational only)=").append(lastRawImageTimestampNs).append('\n')
        }
    }

    override fun clearLatencyTraceHistory() {
        traceWriteIndex.set(0)
        traceSamples.set(0)
        historyTriggerId.fill(0L)
        listOf(
            historySamplerEntryGapNs,
            historyImageTimestampGapNs,
            historyPreSampleNs,
            historySamplerNs,
            historySampleToDetectionNs,
            historyDetectionNs,
            historyDecisionToRequestNs,
            historyRequestToSubmitNs,
            historyAppToServiceArrivalNs,
            historyPriorityCallNs,
            historyServicePrepNs,
            historyDownCallNs,
            historyDecisionToDownNs,
            historyProcessStartToDownNs,
            historyHoldNs,
            historyUpCallNs,
        ).forEach { it.fill(INVALID_NS) }
    }

    private fun outlierReport(count: Int): String {
        if (count <= 0) return "n/a\n"
        val candidates = ArrayList<Pair<Int, Long>>(count)
        for (i in 0 until TRACE_CAPACITY) {
            val value = historyDecisionToDownNs[i]
            if (value >= 0L && historyTriggerId[i] > 0L) candidates.add(i to value)
        }
        if (candidates.isEmpty()) return "n/a\n"
        candidates.sortByDescending { it.second }
        return buildString {
            candidates.take(5).forEachIndexed { rank, (index, total) ->
                append('#').append(rank + 1)
                    .append(" trigger=").append(historyTriggerId[index])
                    .append(" decision→DOWN=").append(fmtCompact(total))
                    .append(" frameGap=").append(fmtCompact(historySamplerEntryGapNs[index]))
                    .append(" app→service=").append(fmtCompact(historyAppToServiceArrivalNs[index]))
                    .append(" servicePrep=").append(fmtCompact(historyServicePrepNs[index]))
                    .append(" downCall=").append(fmtCompact(historyDownCallNs[index]))
                    .append('\n')
            }
        }
    }

    private fun statLine(label: String, source: LongArray, count: Int): String {
        if (count <= 0) return String.format(Locale.US, "%-24s n/a\n", label)
        val valid = source.filter { it >= 0L }.take(count).sorted()
        if (valid.isEmpty()) return String.format(Locale.US, "%-24s n/a\n", label)
        fun percentile(p: Double): Long {
            val rank = ceil(p * valid.size).toInt().coerceIn(1, valid.size)
            return valid[rank - 1]
        }
        return String.format(
            Locale.US,
            "%-24s P50=%-10s P90=%-10s P95=%-10s P99=%-10s MAX=%s\n",
            label,
            fmtCompact(percentile(0.50)),
            fmtCompact(percentile(0.90)),
            fmtCompact(percentile(0.95)),
            fmtCompact(percentile(0.99)),
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
        ns < 1_000_000L -> String.format(Locale.US, "%,d ns | %.3f µs", ns, ns / 1_000.0)
        else -> String.format(Locale.US, "%,d ns | %.3f µs | %.6f ms", ns, ns / 1_000.0, ns / 1_000_000.0)
    }

    private fun fmtCompact(ns: Long): String = when {
        ns < 0L -> "n/a"
        ns < 1_000L -> "${ns}ns"
        ns < 1_000_000L -> String.format(Locale.US, "%.3fµs", ns / 1_000.0)
        else -> String.format(Locale.US, "%.3fms", ns / 1_000_000.0)
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
        private val inputManager: Any?,
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
        private const val TRACE_CAPACITY = 128
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

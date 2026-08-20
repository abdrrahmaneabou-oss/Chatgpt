package com.pixeltrigger.app.input

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.SystemClock
import com.pixeltrigger.app.profiling.AppLatencyProfiler
import rikka.shizuku.Shizuku
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.min

/** App-side, no-root Shizuku tap backend. No Accessibility fallback is used silently. */
class ShizukuTapEngine(private val context: Context) : TapEngine {
    override val name: String = "shizuku-redmagic-nubia-inputreader-ultralow"

    @Volatile private var remote: IShizukuInputService? = null
    @Volatile private var hotPathReady: Boolean = false
    @Volatile private var lastSubmitTriggerId: Long = 0L
    @Volatile private var lastBinderSubmitStartNs: Long = 0L
    @Volatile private var lastBinderSubmitReturnNs: Long = 0L
    @Volatile private var submitSamples: Long = 0L
    private val submitDurationsNs = LongArray(SUBMIT_HISTORY_CAPACITY) { INVALID_NS }

    @Volatile var capability: InputCapability = InputCapability.DISCONNECTED
        private set
    @Volatile var capabilityDetail: String = "Shizuku not connected"
        private set

    private val args = Shizuku.UserServiceArgs(
        ComponentName(context.packageName, ShizukuInputUserService::class.java.name),
    )
        .processNameSuffix("pixeltrigger_input")
        .daemon(true)
        .tag("pixeltrigger-input-v11-clock-safe-profiler")
        .version(11)

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            remote = IShizukuInputService.Stub.asInterface(service)
            refreshCapability()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            remote = null
            hotPathReady = false
            capability = InputCapability.DISCONNECTED
            capabilityDetail = "Shizuku UserService disconnected"
        }
    }

    fun connect(): Boolean {
        if (!Shizuku.pingBinder()) {
            hotPathReady = false
            capability = InputCapability.DISCONNECTED
            capabilityDetail = "Start Shizuku with Wireless debugging/ADB"
            return false
        }
        val uid = runCatching { Shizuku.getUid() }.getOrDefault(-1)
        if (uid != ShizukuInputUserService.SHELL_UID) {
            hotPathReady = false
            capability = InputCapability.ROOT_REJECTED
            capabilityDetail = "No-root policy: Shizuku must run as ADB shell UID 2000 (got $uid)"
            return false
        }
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            hotPathReady = false
            capability = InputCapability.PERMISSION_REQUIRED
            capabilityDetail = "Shizuku permission required"
            return false
        }
        return runCatching {
            Shizuku.bindUserService(args, connection)
            true
        }.getOrElse {
            hotPathReady = false
            capability = InputCapability.DISCONNECTED
            capabilityDetail = "bind failed: ${it.message ?: it.javaClass.simpleName}"
            false
        }
    }

    /** Slow capability/diagnostic path. Never used as a synchronous FIRE gate. */
    fun refreshCapability(): InputCapability {
        val service = remote ?: run {
            hotPathReady = false
            capability = InputCapability.DISCONNECTED
            return capability
        }
        val code = runCatching { service.probeCapability() }.getOrElse {
            capabilityDetail = "probe failed (hot path preserved): ${it.message ?: it.javaClass.simpleName}"
            if (!hotPathReady) capability = InputCapability.DISCONNECTED
            return capability
        }
        capabilityDetail = runCatching { service.capabilityDetail }.getOrDefault("status=$code")
        capability = when (code) {
            ShizukuInputUserService.STATUS_SAFE -> InputCapability.CONCURRENT_TOUCH_SAFE
            ShizukuInputUserService.STATUS_ROOT_OR_NON_SHELL_REJECTED -> InputCapability.ROOT_REJECTED
            ShizukuInputUserService.STATUS_INJECTOR_UNAVAILABLE -> InputCapability.INJECT_EVENTS_UNAVAILABLE
            ShizukuInputUserService.STATUS_CONCURRENT_TOUCH_UNKNOWN -> InputCapability.CONCURRENT_TOUCH_UNKNOWN
            ShizukuInputUserService.STATUS_CONCURRENT_TOUCH_UNSAFE -> InputCapability.CONCURRENT_TOUCH_UNSAFE
            else -> InputCapability.DISCONNECTED
        }
        hotPathReady = capability == InputCapability.CONCURRENT_TOUCH_SAFE
        return capability
    }

    /** Volatile-memory check only; no synchronous probe in the FIRE path. */
    fun isReady(): Boolean = remote != null && hotPathReady

    /** Exactly one one-way AIDL submission. No retries, logs, formatting, or diagnostic reads. */
    override fun tap(request: TapRequest): TapResult {
        val acceptedAt = SystemClock.elapsedRealtimeNanos()
        val service = remote
            ?: return TapResult.Failed(request.triggerId, acceptedAt, "Shizuku input service disconnected")
        if (!hotPathReady) {
            return TapResult.Failed(request.triggerId, acceptedAt, "Nubia input backend not warmed/ready")
        }

        val submitStartNs = SystemClock.elapsedRealtimeNanos()
        lastSubmitTriggerId = request.triggerId
        lastBinderSubmitStartNs = submitStartNs

        return runCatching {
            service.injectTapFast(
                request.triggerId,
                request.x,
                request.y,
                request.displayId,
                request.frameTimestampNs,
                request.captureCallbackNs,
                request.sampleStartNs,
                request.sampleEndNs,
                request.detectionStartNs,
                request.fireDecisionNs,
                request.requestedAtNs,
                submitStartNs,
                request.samplerEntryGapNs,
                request.imageTimestampGapNs,
            )
            val submitReturnNs = SystemClock.elapsedRealtimeNanos()
            lastBinderSubmitReturnNs = submitReturnNs
            recordSubmitDuration(submitReturnNs - submitStartNs)
            TapResult.Completed(
                triggerId = request.triggerId,
                acceptedAtNs = acceptedAt,
                downSentAtNs = 0L,
                upSentAtNs = 0L,
            )
        }.getOrElse {
            val submitReturnNs = SystemClock.elapsedRealtimeNanos()
            lastBinderSubmitReturnNs = submitReturnNs
            recordSubmitDuration(submitReturnNs - submitStartNs)
            TapResult.Failed(request.triggerId, acceptedAt, "binder submit error: ${it.message ?: it.javaClass.simpleName}")
        }
    }

    private fun recordSubmitDuration(ns: Long) {
        if (ns < 0L) return
        val sequence = submitSamples
        submitDurationsNs[(sequence % SUBMIT_HISTORY_CAPACITY).toInt()] = ns
        submitSamples = sequence + 1L
    }

    /** Compact UI status; diagnostic read only. */
    fun latencyDetail(): String {
        val service = remote ?: return "latency: disconnected"
        val localNs = safeDelta(lastBinderSubmitStartNs, lastBinderSubmitReturnNs)
        val remoteDetail = runCatching { service.latencyDetail }.getOrDefault("latency: unavailable")
        return "app one-way call=${fmtCompact(localNs)}; $remoteDetail"
    }

    /** Full report. Formatting/statistics happen only when the user opens diagnostics. */
    fun latencyTraceReport(): String {
        val service = remote ?: return "Latency trace: Shizuku UserService disconnected"
        val localNs = safeDelta(lastBinderSubmitStartNs, lastBinderSubmitReturnNs)
        val localStats = submitStats()
        val backend = runCatching { service.latencyTraceReport }.getOrElse {
            "Trace read failed: ${it.message ?: it.javaClass.simpleName}"
        }
        return buildString(5000) {
            append("🧪 PixelTrigger PROFESSIONAL LATENCY LAB\n")
            append("No absolute Image.timestamp ↔ elapsedRealtimeNanos subtraction is permitted.\n\n")
            append(AppLatencyProfiler.report()).append('\n')
            append("APP → AIDL SUBMISSION\n")
            append("Latest one-way AIDL call return: ").append(fmt(localNs))
                .append(" (trigger #").append(lastSubmitTriggerId).append(")\n")
            append("Rolling one-way call: ").append(localStats).append("\n\n")
            append(backend)
        }
    }

    fun clearLatencyTraceHistory() {
        lastSubmitTriggerId = 0L
        lastBinderSubmitStartNs = 0L
        lastBinderSubmitReturnNs = 0L
        submitSamples = 0L
        submitDurationsNs.fill(INVALID_NS)
        AppLatencyProfiler.clear()
        remote?.let { service -> runCatching { service.clearLatencyTraceHistory() } }
    }

    fun disconnect() {
        runCatching { Shizuku.unbindUserService(args, connection, false) }
        remote = null
        hotPathReady = false
        capability = InputCapability.DISCONNECTED
    }

    private fun submitStats(): String {
        val count = min(submitSamples, SUBMIT_HISTORY_CAPACITY.toLong()).toInt()
        if (count <= 0) return "n/a"
        val values = submitDurationsNs.filter { it >= 0L }.take(count).sorted()
        if (values.isEmpty()) return "n/a"
        fun pct(p: Double): Long {
            val rank = ceil(p * values.size).toInt().coerceIn(1, values.size)
            return values[rank - 1]
        }
        return "P50=${fmtCompact(pct(0.50))} P90=${fmtCompact(pct(0.90))} " +
            "P95=${fmtCompact(pct(0.95))} P99=${fmtCompact(pct(0.99))} MAX=${fmtCompact(values.last())}"
    }

    private fun safeDelta(startNs: Long, endNs: Long): Long =
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

    companion object {
        private const val SUBMIT_HISTORY_CAPACITY = 128
        private const val INVALID_NS = -1L
    }
}

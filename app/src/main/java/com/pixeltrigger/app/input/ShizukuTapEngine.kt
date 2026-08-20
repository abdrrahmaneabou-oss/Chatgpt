package com.pixeltrigger.app.input

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.SystemClock
import rikka.shizuku.Shizuku

/** App-side, no-root Shizuku tap backend. No Accessibility fallback is used silently. */
class ShizukuTapEngine(private val context: Context) : TapEngine {
    override val name: String = "shizuku-redmagic-nubia-inputreader-ultralow"

    @Volatile private var remote: IShizukuInputService? = null
    @Volatile private var hotPathReady: Boolean = false
    @Volatile private var lastSubmitTriggerId: Long = 0L
    @Volatile private var lastBinderSubmitStartNs: Long = 0L
    @Volatile private var lastBinderSubmitReturnNs: Long = 0L

    @Volatile var capability: InputCapability = InputCapability.DISCONNECTED
        private set
    @Volatile var capabilityDetail: String = "Shizuku not connected"
        private set

    private val args = Shizuku.UserServiceArgs(
        ComponentName(context.packageName, ShizukuInputUserService::class.java.name),
    )
        .processNameSuffix("pixeltrigger_input")
        .daemon(true)
        .tag("pixeltrigger-input-v10-ns-trace")
        .version(10)

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

    /**
     * Slow capability/diagnostic path. Once STATUS_SAFE has warmed the vendor path,
     * a transient diagnostic Binder failure must not de-arm the hot path.
     */
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

    /**
     * Hot path: exactly one one-way Binder transaction. The extra timestamps are plain
     * primitive longs; there is no logging, formatting, allocation-heavy statistics or
     * synchronous diagnostic read on FIRE.
     */
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
            )
            val submitReturnNs = SystemClock.elapsedRealtimeNanos()
            lastBinderSubmitReturnNs = submitReturnNs
            TapResult.Completed(
                triggerId = request.triggerId,
                acceptedAtNs = acceptedAt,
                downSentAtNs = 0L,
                upSentAtNs = 0L,
            )
        }.getOrElse {
            lastBinderSubmitReturnNs = SystemClock.elapsedRealtimeNanos()
            TapResult.Failed(request.triggerId, acceptedAt, "binder submit error: ${it.message ?: it.javaClass.simpleName}")
        }
    }

    /** Slow diagnostics path, never called from FIRE. */
    fun latencyDetail(): String {
        val service = remote ?: return "latency: disconnected"
        val localNs = (lastBinderSubmitReturnNs - lastBinderSubmitStartNs).takeIf { it >= 0L } ?: -1L
        val local = if (localNs >= 0L) String.format(java.util.Locale.US, "appAidlSubmit=%.3fµs", localNs / 1_000.0)
        else "appAidlSubmit=n/a"
        val remoteDetail = runCatching { service.latencyDetail }.getOrDefault("latency: unavailable")
        return "$local; $remoteDetail"
    }

    /** Full last-shot + rolling latency report. Only used from menu/diagnostics UI. */
    fun latencyTraceReport(): String {
        val service = remote ?: return "Latency trace: Shizuku UserService disconnected"
        val localSubmitNs = if (lastBinderSubmitReturnNs >= lastBinderSubmitStartNs && lastBinderSubmitStartNs > 0L) {
            lastBinderSubmitReturnNs - lastBinderSubmitStartNs
        } else -1L
        val localLine = if (localSubmitNs >= 0L) {
            String.format(
                java.util.Locale.US,
                "APP AIDL enqueue return: %.3f µs  (trigger #%d)",
                localSubmitNs / 1_000.0,
                lastSubmitTriggerId,
            )
        } else "APP AIDL enqueue return: n/a"
        val backend = runCatching { service.latencyTraceReport }.getOrElse {
            "Trace read failed: ${it.message ?: it.javaClass.simpleName}"
        }
        return "$localLine\n$backend"
    }

    fun clearLatencyTraceHistory() {
        lastSubmitTriggerId = 0L
        lastBinderSubmitStartNs = 0L
        lastBinderSubmitReturnNs = 0L
        remote?.let { service -> runCatching { service.clearLatencyTraceHistory() } }
    }

    fun disconnect() {
        runCatching { Shizuku.unbindUserService(args, connection, false) }
        remote = null
        hotPathReady = false
        capability = InputCapability.DISCONNECTED
    }
}

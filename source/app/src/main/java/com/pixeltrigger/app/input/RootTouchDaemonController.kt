package com.pixeltrigger.app.input

import android.content.Context
import android.os.Build
import android.os.Process
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.concurrent.TimeUnit

/**
 * Owns the privileged PixelTrigger touchproxy process.
 *
 * The control protocol intentionally stays on the stdin/stdout pipes of one persistent `su`
 * process. This avoids per-tap shell startup cost and avoids cross-SELinux-domain filesystem
 * sockets. If the app process dies, the pipe closes; touchproxy treats stdin HUP as a shutdown
 * signal and releases EVIOCGRAB before exiting.
 */
class RootTouchDaemonController(
    context: Context,
    private val suBinary: String = "su",
) {
    data class ReadyInfo(
        val devicePath: String,
        val physicalSlots: Int,
        val rawMinX: Int,
        val rawMaxX: Int,
        val rawMinY: Int,
        val rawMaxY: Int,
    )

    data class ProbeCandidate(
        val devicePath: String,
        val direct: Boolean,
        val slots: Int,
        val rawMinX: Int,
        val rawMaxX: Int,
        val rawMinY: Int,
        val rawMaxY: Int,
        val vendorId: Int,
        val name: String,
    )

    private val appContext = context.applicationContext
    private val ioMutex = Mutex()
    private val uid = Process.myUid()
    private val rootDir = "/data/local/tmp/pixeltrigger-$uid"
    private val rootBinaryPath = "$rootDir/touchproxy"

    @Volatile private var process: java.lang.Process? = null
    @Volatile private var reader: BufferedReader? = null
    @Volatile private var writer: BufferedWriter? = null
    @Volatile var readyInfo: ReadyInfo? = null
        private set

    suspend fun hasRoot(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val result = runRootOneShot("id -u", timeoutMs = 2_000)
            result.exitCode == 0 && result.stdout.lineSequence().any { it.trim() == "0" }
        }.getOrDefault(false)
    }

    /** Copies the ABI-specific daemon asset into a root-owned executable directory. */
    suspend fun install(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val assetPath = selectPackagedBinaryAsset() ?: return@runCatching false
            val command = buildString {
                append("umask 077; mkdir -p ").append(shellQuote(rootDir))
                append("; chmod 700 ").append(shellQuote(rootDir))
                append("; cat > ").append(shellQuote(rootBinaryPath))
                append("; chmod 700 ").append(shellQuote(rootBinaryPath))
            }
            val p = ProcessBuilder(suBinary, "-c", command).start()
            try {
                appContext.assets.open(assetPath).use { input ->
                    p.outputStream.use { output -> input.copyTo(output, DEFAULT_BUFFER_SIZE) }
                }
                if (!p.waitFor(5, TimeUnit.SECONDS)) {
                    p.destroyForcibly()
                    return@runCatching false
                }
                if (p.exitValue() != 0) return@runCatching false
                val verify = runRootOneShot(
                    "test -x ${shellQuote(rootBinaryPath)} && echo OK",
                    timeoutMs = 2_000,
                )
                verify.exitCode == 0 && verify.stdout.lineSequence().any { it.trim() == "OK" }
            } finally {
                runCatching { p.inputStream.close() }
                runCatching { p.errorStream.close() }
            }
        }.getOrDefault(false)
    }

    suspend fun probe(): List<ProbeCandidate> = withContext(Dispatchers.IO) {
        if (!ensureInstalled()) return@withContext emptyList()
        val result = runCatching {
            runRootOneShot("${shellQuote(rootBinaryPath)} --probe", timeoutMs = 3_000)
        }.getOrNull() ?: return@withContext emptyList()
        if (result.exitCode != 0) return@withContext emptyList()
        result.stdout.lineSequence().mapNotNull(::parseProbeLine).toList()
    }

    suspend fun start(): ReadyInfo? = ioMutex.withLock {
        withContext(Dispatchers.IO) {
            if (isAlive()) return@withContext readyInfo
            cleanupProcessReferences(force = true)
            if (!ensureInstalled()) return@withContext null

            val command = "exec ${shellQuote(rootBinaryPath)} --auto --stdio"
            val p = runCatching { ProcessBuilder(suBinary, "-c", command).start() }.getOrNull()
                ?: return@withContext null
            val r = BufferedReader(InputStreamReader(p.inputStream))
            val w = BufferedWriter(OutputStreamWriter(p.outputStream))
            process = p
            reader = r
            writer = w
            drainStderr(p)

            val line = readLineWithDeadline(r, p, 3_000)
            val ready = line?.let(::parseReadyLine)
            if (ready == null) {
                cleanupProcessReferences(force = true)
                return@withContext null
            }
            readyInfo = ready
            ready
        }
    }

    suspend fun ping(): Boolean = ioMutex.withLock {
        withContext(Dispatchers.IO) {
            val p = process ?: return@withContext false
            val r = reader ?: return@withContext false
            val w = writer ?: return@withContext false
            if (!p.isAlive) return@withContext false
            try {
                w.write("PING\n")
                w.flush()
                readLineWithDeadline(r, p, 750) == "PONG"
            } catch (_: Throwable) {
                false
            }
        }
    }

    suspend fun tap(
        request: TapRequest,
        widthPx: Int,
        heightPx: Int,
        rotation: Int,
    ): TapResult = ioMutex.withLock {
        withContext(Dispatchers.IO) {
            val acceptedAt = SystemClock.elapsedRealtimeNanos()
            val p = process
            val r = reader
            val w = writer
            if (p == null || r == null || w == null || !p.isAlive || readyInfo == null) {
                return@withContext TapResult.Rejected(
                    request.triggerId,
                    acceptedAt,
                    "root touch daemon not ready",
                )
            }
            if (widthPx <= 0 || heightPx <= 0) {
                return@withContext TapResult.Rejected(
                    request.triggerId,
                    acceptedAt,
                    "invalid display geometry",
                )
            }

            val durationUs = request.requestedDurationMs.coerceAtLeast(1L) * 1_000L
            val command = buildString {
                append("TAP ").append(request.triggerId).append(' ')
                append(request.x).append(' ').append(request.y).append(' ')
                append(widthPx).append(' ').append(heightPx).append(' ')
                append(((rotation % 4) + 4) % 4).append(' ')
                append(durationUs).append('\n')
            }

            try {
                w.write(command)
                w.flush()
                when (val ack = readLineWithDeadline(r, p, 750) ?: "") {
                    "ACK ${request.triggerId}" -> Unit
                    "DUP ${request.triggerId}" -> return@withContext TapResult.Rejected(
                        request.triggerId,
                        acceptedAt,
                        "daemon duplicate guard",
                    )
                    else -> return@withContext TapResult.Rejected(
                        request.triggerId,
                        acceptedAt,
                        "daemon rejected: $ack",
                    )
                }

                // Never retry after ACK. An ambiguous completion must not become two taps.
                return@withContext when (val done = readLineWithDeadline(r, p, 1_000) ?: "") {
                    "DONE ${request.triggerId}" -> TapResult.Completed(
                        request.triggerId,
                        acceptedAt,
                        SystemClock.elapsedRealtimeNanos(),
                    )
                    else -> TapResult.Rejected(
                        request.triggerId,
                        acceptedAt,
                        "ambiguous daemon completion: $done",
                    )
                }
            } catch (t: Throwable) {
                TapResult.Rejected(
                    request.triggerId,
                    acceptedAt,
                    "root touch I/O: ${t.message ?: t::class.java.simpleName}",
                )
            }
        }
    }

    suspend fun stop() = ioMutex.withLock {
        withContext(Dispatchers.IO) {
            val p = process
            val r = reader
            val w = writer
            if (p != null && p.isAlive && r != null && w != null) {
                try {
                    w.write("STOP\n")
                    w.flush()
                    readLineWithDeadline(r, p, 500)
                } catch (_: Throwable) {
                    // Closing stdin below is also a fail-safe shutdown signal.
                }
            }
            cleanupProcessReferences(force = true)
        }
    }

    fun isAlive(): Boolean = process?.isAlive == true && readyInfo != null

    private suspend fun ensureInstalled(): Boolean {
        val check = runCatching {
            runRootOneShot(
                "test -x ${shellQuote(rootBinaryPath)} && echo OK",
                timeoutMs = 1_000,
            )
        }.getOrNull()
        return if (check?.exitCode == 0 && check.stdout.contains("OK")) true else install()
    }

    private fun selectPackagedBinaryAsset(): String? {
        val candidates = buildList {
            Build.SUPPORTED_ABIS.forEach { add("touchproxy/$it/touchproxy") }
            add("touchproxy/touchproxy")
        }
        return candidates.firstOrNull { path ->
            try {
                appContext.assets.open(path).use { true }
            } catch (_: Throwable) {
                false
            }
        }
    }

    private data class RootResult(val exitCode: Int, val stdout: String, val stderr: String)

    private fun runRootOneShot(command: String, timeoutMs: Long): RootResult {
        val p = ProcessBuilder(suBinary, "-c", command).start()
        p.outputStream.close()
        if (!p.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
            p.destroyForcibly()
            return RootResult(-1, "", "timeout")
        }
        return RootResult(
            p.exitValue(),
            p.inputStream.bufferedReader().use { it.readText() },
            p.errorStream.bufferedReader().use { it.readText() },
        )
    }

    private suspend fun readLineWithDeadline(
        r: BufferedReader,
        p: java.lang.Process,
        timeoutMs: Long,
    ): String? {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            if (r.ready()) return r.readLine()
            if (!p.isAlive) return if (r.ready()) r.readLine() else null
            delay(2)
        }
        return null
    }

    private fun parseReadyLine(line: String): ReadyInfo? {
        val parts = line.trim().split(' ')
        if (parts.size != 7 || parts[0] != "READY") return null
        return runCatching {
            ReadyInfo(
                devicePath = parts[1],
                physicalSlots = parts[2].toInt(),
                rawMinX = parts[3].toInt(),
                rawMaxX = parts[4].toInt(),
                rawMinY = parts[5].toInt(),
                rawMaxY = parts[6].toInt(),
            )
        }.getOrNull()
    }

    private fun parseProbeLine(line: String): ProbeCandidate? {
        val parts = line.split('\t', limit = 10)
        if (parts.size != 10 || parts[0] != "PROBE") return null
        return runCatching {
            ProbeCandidate(
                devicePath = parts[1],
                direct = parts[2] == "1",
                slots = parts[3].toInt(),
                rawMinX = parts[4].toInt(),
                rawMaxX = parts[5].toInt(),
                rawMinY = parts[6].toInt(),
                rawMaxY = parts[7].toInt(),
                vendorId = parts[8].toInt(),
                name = parts[9],
            )
        }.getOrNull()
    }

    private fun drainStderr(p: java.lang.Process) {
        Thread({
            try {
                p.errorStream.bufferedReader().useLines { lines -> lines.forEach { /* drain */ } }
            } catch (_: Throwable) {
                // Process shutdown closes the pipe.
            }
        }, "PixelTrigger-touchproxy-stderr").apply {
            isDaemon = true
            start()
        }
    }

    private fun cleanupProcessReferences(force: Boolean) {
        val p = process
        try { writer?.close() } catch (_: Throwable) {}
        try { reader?.close() } catch (_: Throwable) {}
        if (p != null && p.isAlive) {
            try { p.outputStream.close() } catch (_: Throwable) {}
            if (!p.waitFor(300, TimeUnit.MILLISECONDS) && force) {
                p.destroy()
                if (!p.waitFor(300, TimeUnit.MILLISECONDS)) p.destroyForcibly()
            }
        }
        process = null
        reader = null
        writer = null
        readyInfo = null
    }

    private fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"
}

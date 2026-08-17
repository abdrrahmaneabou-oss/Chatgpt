package com.pixeltrigger.app.input

import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.channels.Channels
import java.nio.channels.SocketChannel

/**
 * True-concurrent backend for the PixelTrigger unified-touch daemon.
 *
 * The daemon runs as root, relays the physical touchscreen through one uinput multi-touch
 * device, and reserves one extra MT slot for PixelTrigger. This class never calls
 * AccessibilityService.dispatchGesture().
 */
class UnifiedTouchRootEngine(
    private val socketFile: File,
    private val displayInfo: () -> DisplayInfo,
) : TapEngine {
    override val name: String = "root-unified-touch"

    data class DisplayInfo(
        val widthPx: Int,
        val heightPx: Int,
        /** Surface rotation: 0, 1, 2, 3. */
        val rotation: Int,
    )

    override suspend fun tap(request: TapRequest): TapResult = withContext(Dispatchers.IO) {
        val acceptedAt = SystemClock.elapsedRealtimeNanos()
        val display = displayInfo()
        if (display.widthPx <= 0 || display.heightPx <= 0) {
            return@withContext TapResult.Rejected(request.triggerId, acceptedAt, "invalid display geometry")
        }
        if (!socketFile.exists()) {
            return@withContext TapResult.Rejected(request.triggerId, acceptedAt, "unified-touch daemon unavailable")
        }

        val durationUs = request.requestedDurationMs.coerceAtLeast(1L) * 1_000L
        val address = UnixDomainSocketAddress.of(socketFile.toPath())

        try {
            SocketChannel.open(StandardProtocolFamily.UNIX).use { channel ->
                channel.connect(address)
                val writer = BufferedWriter(OutputStreamWriter(Channels.newOutputStream(channel)))
                val reader = BufferedReader(InputStreamReader(Channels.newInputStream(channel)))
                writer.write(
                    "TAP ${request.triggerId} ${request.x} ${request.y} " +
                        "${display.widthPx} ${display.heightPx} ${display.rotation} $durationUs\n",
                )
                writer.flush()

                when (val ack = reader.readLine() ?: "") {
                    "ACK ${request.triggerId}" -> Unit
                    "DUP ${request.triggerId}" -> {
                        return@withContext TapResult.Rejected(request.triggerId, acceptedAt, "daemon duplicate guard")
                    }
                    else -> {
                        return@withContext TapResult.Rejected(request.triggerId, acceptedAt, "daemon rejected: $ack")
                    }
                }

                when (val done = reader.readLine() ?: "") {
                    "DONE ${request.triggerId}" -> TapResult.Completed(
                        request.triggerId,
                        acceptedAt,
                        SystemClock.elapsedRealtimeNanos(),
                    )
                    else -> TapResult.Rejected(request.triggerId, acceptedAt, "daemon completion failure: $done")
                }
            }
        } catch (t: Throwable) {
            TapResult.Rejected(
                request.triggerId,
                acceptedAt,
                "unified-touch I/O: ${t.message ?: t::class.java.simpleName}",
            )
        }
    }
}

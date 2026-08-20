package com.pixeltrigger.app.profiling

import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * Low-overhead app-side latency/cadence profiler.
 *
 * Clock-domain rule:
 * - All app/UserService wall-clock timestamps use SystemClock.elapsedRealtimeNanos().
 * - Image.timestamp is a foreign/source clock. Its ABSOLUTE value is never compared
 *   with elapsedRealtimeNanos(). Only consecutive Image.timestamp deltas are retained.
 *
 * PixelSampler is the per-frame observation point. One capture thread writes these
 * fields/arrays; diagnostics may read them from the UI thread. The volatile commit
 * counter is written only after a slot is complete, so readers never depend on a lock
 * in the frame hot path.
 */
object AppLatencyProfiler {
    data class FireFrameSnapshot(
        val frameOrdinal: Long,
        val samplerEntryGapNs: Long,
        val imageTimestampGapNs: Long,
        val samplerInternalNs: Long,
    )

    private const val CAPACITY = 512
    private const val INVALID_NS = -1L
    private const val FIFTY_MS_NS = 50_000_000L
    private const val TWO_MS_NS = 2_000_000L

    private val frameGapNs = LongArray(CAPACITY) { INVALID_NS }
    private val imageGapNs = LongArray(CAPACITY) { INVALID_NS }
    private val samplerNs = LongArray(CAPACITY) { INVALID_NS }

    @Volatile private var committedFrames = 0L
    @Volatile private var lastSamplerEntryNs = 0L
    @Volatile private var lastImageTimestampNs = 0L
    @Volatile private var currentEntryNs = 0L
    @Volatile private var currentFrameGapNs = INVALID_NS
    @Volatile private var currentImageGapNs = INVALID_NS
    @Volatile private var lastCommittedFrameGapNs = INVALID_NS
    @Volatile private var lastCommittedImageGapNs = INVALID_NS
    @Volatile private var lastCommittedSamplerNs = INVALID_NS
    @Volatile private var imageTimestampDiscontinuities = 0L

    /** Called exactly once at PixelSampler entry for every processed image. */
    fun onSamplerEntry(elapsedNs: Long, imageTimestampNs: Long) {
        val previousEntry = lastSamplerEntryNs
        currentFrameGapNs = safeDelta(previousEntry, elapsedNs)
        lastSamplerEntryNs = elapsedNs
        currentEntryNs = elapsedNs

        val previousImage = lastImageTimestampNs
        currentImageGapNs = when {
            imageTimestampNs <= 0L -> INVALID_NS
            previousImage <= 0L -> INVALID_NS
            imageTimestampNs >= previousImage -> imageTimestampNs - previousImage
            else -> {
                imageTimestampDiscontinuities++
                INVALID_NS
            }
        }
        if (imageTimestampNs > 0L) lastImageTimestampNs = imageTimestampNs
    }

    /** Called in a finally block so a failed/short sample still closes the cadence record. */
    fun onSamplerExit(elapsedNs: Long) {
        val internal = safeDelta(currentEntryNs, elapsedNs)
        val sequence = committedFrames
        val slot = (sequence % CAPACITY).toInt()
        frameGapNs[slot] = currentFrameGapNs
        imageGapNs[slot] = currentImageGapNs
        samplerNs[slot] = internal
        lastCommittedFrameGapNs = currentFrameGapNs
        lastCommittedImageGapNs = currentImageGapNs
        lastCommittedSamplerNs = internal
        committedFrames = sequence + 1L
    }

    /** One small allocation per FIRE only; never per frame. */
    fun snapshotForFire(): FireFrameSnapshot = FireFrameSnapshot(
        frameOrdinal = committedFrames,
        samplerEntryGapNs = lastCommittedFrameGapNs,
        imageTimestampGapNs = lastCommittedImageGapNs,
        samplerInternalNs = lastCommittedSamplerNs,
    )

    fun clear() {
        frameGapNs.fill(INVALID_NS)
        imageGapNs.fill(INVALID_NS)
        samplerNs.fill(INVALID_NS)
        committedFrames = 0L
        lastSamplerEntryNs = 0L
        lastImageTimestampNs = 0L
        currentEntryNs = 0L
        currentFrameGapNs = INVALID_NS
        currentImageGapNs = INVALID_NS
        lastCommittedFrameGapNs = INVALID_NS
        lastCommittedImageGapNs = INVALID_NS
        lastCommittedSamplerNs = INVALID_NS
        imageTimestampDiscontinuities = 0L
    }

    fun report(): String {
        val count = min(committedFrames, CAPACITY.toLong()).toInt()
        val frameValues = validCopy(frameGapNs, count)
        val imageValues = validCopy(imageGapNs, count)
        val samplerValues = validCopy(samplerNs, count)
        val frameStats = stats(frameValues)
        val imageStats = stats(imageValues)
        val samplerStats = stats(samplerValues)

        val baseline = frameStats?.p50 ?: INVALID_NS
        val incidentThreshold = if (baseline > 0L) max(FIFTY_MS_NS, baseline * 4L) else FIFTY_MS_NS
        val incidentCount = frameValues.count { it >= incidentThreshold }
        val lastGap = lastCommittedFrameGapNs
        val lastImageGap = lastCommittedImageGapNs

        val verdict = when {
            lastGap < 0L -> "No trigger-frame cadence sample yet."
            lastGap < incidentThreshold -> "No large processed-frame cadence stall on the latest sampled frame."
            lastImageGap > 0L && abs(lastGap - lastImageGap) <= max(TWO_MS_NS, lastGap / 6L) ->
                "Large gap is also present in consecutive Image.timestamp DELTAS. The stall is upstream of/inside capture production or frames were skipped before delivery."
            lastImageGap > 0L && lastGap - lastImageGap >= max(FIFTY_MS_NS, baseline * 2L) ->
                "Large sampler-entry gap without a matching source timestamp delta. Suspect app delivery/scheduling/backpressure after image production."
            else ->
                "Large processed-frame gap detected. Clock-safe data proves a stall before sampling; system tracing is required to attribute scheduler vs capture producer."
        }

        return buildString(1800) {
            append("CAPTURE / FRAME CADENCE — clock-safe profiler\n")
            append("Clock A: elapsedRealtimeNanos = app/UserService comparable monotonic domain.\n")
            append("Clock B: Image.timestamp = FOREIGN/UNSYNCED absolute domain. Absolute frame age is intentionally NOT computed.\n")
            append("Only ΔImage.timestamp between consecutive sampled images is used.\n\n")
            append("Frames observed: ").append(committedFrames).append(" (rolling window ").append(count).append(")\n")
            append("Latest sampler-entry gap: ").append(fmt(lastGap)).append('\n')
            append("Latest source ΔImage.timestamp: ").append(fmt(lastImageGap)).append('\n')
            append("Latest PixelSampler internal wall time: ").append(fmt(lastCommittedSamplerNs)).append('\n')
            append("Image timestamp discontinuities: ").append(imageTimestampDiscontinuities).append("\n\n")
            append(statLine("sampler-entry interval", frameStats))
            append(statLine("source image Δtimestamp", imageStats))
            append(statLine("PixelSampler internal", samplerStats))
            append("\nCadence incident threshold: ").append(fmt(incidentThreshold))
                .append(" ; incidents in window: ").append(incidentCount).append('\n')
            append("CAPTURE VERDICT: ").append(verdict).append('\n')
        }
    }

    private data class Stats(val p50: Long, val p90: Long, val p95: Long, val p99: Long, val max: Long)

    private fun validCopy(source: LongArray, count: Int): LongArray {
        if (count <= 0) return LongArray(0)
        val temp = LongArray(count)
        var n = 0
        for (value in source) {
            if (value >= 0L && n < count) temp[n++] = value
        }
        return temp.copyOf(n).apply { sort() }
    }

    private fun stats(sorted: LongArray): Stats? {
        if (sorted.isEmpty()) return null
        fun pct(p: Double): Long {
            val rank = ceil(p * sorted.size).toInt().coerceIn(1, sorted.size)
            return sorted[rank - 1]
        }
        return Stats(
            p50 = pct(0.50),
            p90 = pct(0.90),
            p95 = pct(0.95),
            p99 = pct(0.99),
            max = sorted.last(),
        )
    }

    private fun statLine(label: String, s: Stats?): String {
        if (s == null) return "$label: n/a\n"
        return String.format(
            Locale.US,
            "%s: P50=%s  P90=%s  P95=%s  P99=%s  MAX=%s\n",
            label,
            fmtCompact(s.p50),
            fmtCompact(s.p90),
            fmtCompact(s.p95),
            fmtCompact(s.p99),
            fmtCompact(s.max),
        )
    }

    private fun safeDelta(startNs: Long, endNs: Long): Long =
        if (startNs > 0L && endNs >= startNs) endNs - startNs else INVALID_NS

    private fun fmt(ns: Long): String = when {
        ns < 0L -> "n/a"
        ns < 1_000L -> "$ns ns"
        ns < 1_000_000L -> String.format(Locale.US, "%.3f µs", ns / 1_000.0)
        else -> String.format(Locale.US, "%.6f ms", ns / 1_000_000.0)
    }

    private fun fmtCompact(ns: Long): String = when {
        ns < 0L -> "n/a"
        ns < 1_000L -> "${ns}ns"
        ns < 1_000_000L -> String.format(Locale.US, "%.3fµs", ns / 1_000.0)
        else -> String.format(Locale.US, "%.3fms", ns / 1_000_000.0)
    }
}

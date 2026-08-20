package com.pixeltrigger.app.engine

import android.graphics.Rect
import android.media.Image
import android.os.SystemClock
import com.pixeltrigger.app.profiling.AppLatencyProfiler
import java.nio.ByteBuffer
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** Cached circular sampler with per-pixel WHITE/DARK coverage for robust tiny-region detection. */
object PixelSampler {
    private data class SamplePlan(
        val radiusXBits: Int,
        val radiusYBits: Int,
        val dx: IntArray,
        val dy: IntArray,
    )

    private var cachedPlan: SamplePlan? = null

    fun sampleCircularRegion(
        image: Image,
        centerX: Int,
        centerY: Int,
        radiusX: Float,
        radiusY: Float,
    ): DetectionEngine.ColorSample? {
        AppLatencyProfiler.onSamplerEntry(
            elapsedNs = SystemClock.elapsedRealtimeNanos(),
            imageTimestampNs = image.timestamp,
        )
        try {
            val crop: Rect = image.cropRect
            if (centerX !in crop.left until crop.right || centerY !in crop.top until crop.bottom) return null

            val plane = image.planes.firstOrNull() ?: return null
            val buffer: ByteBuffer = plane.buffer
            val pixelStride = plane.pixelStride
            val rowStride = plane.rowStride
            if (pixelStride < 3 || rowStride <= 0) return null

            val plan = planFor(radiusX, radiusY)
            var redTotal = 0L
            var greenTotal = 0L
            var blueTotal = 0L
            var luminanceTotal = 0L
            var chromaTotal = 0L
            var whiteCount = 0
            var darkCount = 0
            var count = 0
            val base = buffer.position()

            for (i in plan.dx.indices) {
                val x = centerX + plan.dx[i]
                val y = centerY + plan.dy[i]
                if (x < crop.left || x >= crop.right || y < crop.top || y >= crop.bottom) continue

                val offset = base + y * rowStride + x * pixelStride
                if (offset < 0 || offset + 2 >= buffer.limit()) continue

                val red = buffer.get(offset).toInt() and 0xff
                val green = buffer.get(offset + 1).toInt() and 0xff
                val blue = buffer.get(offset + 2).toInt() and 0xff
                val minimumChannel = min(red, min(green, blue))
                val maximumChannel = max(red, max(green, blue))
                val chroma = maximumChannel - minimumChannel
                val luminance = ((red * 54) + (green * 183) + (blue * 19)) shr 8

                redTotal += red
                greenTotal += green
                blueTotal += blue
                luminanceTotal += luminance
                chromaTotal += chroma

                if (
                    luminance >= DetectionEngine.WHITE_PIXEL_LUMINANCE &&
                    minimumChannel >= DetectionEngine.WHITE_PIXEL_MIN_CHANNEL &&
                    chroma <= DetectionEngine.WHITE_PIXEL_MAX_CHROMA
                ) {
                    whiteCount++
                }

                if (
                    luminance <= DetectionEngine.DARK_PIXEL_MAX_LUMINANCE &&
                    maximumChannel <= DetectionEngine.DARK_PIXEL_MAX_CHANNEL &&
                    chroma <= DetectionEngine.DARK_PIXEL_MAX_CHROMA
                ) {
                    darkCount++
                }
                count++
            }

            if (count < DetectionEngine.MIN_SAMPLE_PIXELS) return null
            return DetectionEngine.ColorSample(
                averageRed = (redTotal / count).toInt(),
                averageGreen = (greenTotal / count).toInt(),
                averageBlue = (blueTotal / count).toInt(),
                whiteRatio = whiteCount.toFloat() / count.toFloat(),
                darkRatio = darkCount.toFloat() / count.toFloat(),
                averageLuminance = (luminanceTotal / count).toInt(),
                averageChroma = (chromaTotal / count).toInt(),
            )
        } finally {
            AppLatencyProfiler.onSamplerExit(SystemClock.elapsedRealtimeNanos())
        }
    }

    private fun planFor(radiusX: Float, radiusY: Float): SamplePlan {
        val safeRadiusX = max(radiusX, 0.5f)
        val safeRadiusY = max(radiusY, 0.5f)
        val xBits = safeRadiusX.toBits()
        val yBits = safeRadiusY.toBits()
        cachedPlan?.let { if (it.radiusXBits == xBits && it.radiusYBits == yBits) return it }

        val minDx = floor(-safeRadiusX).toInt()
        val maxDx = ceil(safeRadiusX).toInt()
        val minDy = floor(-safeRadiusY).toInt()
        val maxDy = ceil(safeRadiusY).toInt()
        val xs = ArrayList<Int>()
        val ys = ArrayList<Int>()

        for (dy in minDy..maxDy) {
            val normalizedY = dy / safeRadiusY
            for (dx in minDx..maxDx) {
                val normalizedX = dx / safeRadiusX
                if (normalizedX * normalizedX + normalizedY * normalizedY <= 1f) {
                    xs.add(dx)
                    ys.add(dy)
                }
            }
        }

        return SamplePlan(
            radiusXBits = xBits,
            radiusYBits = yBits,
            dx = xs.toIntArray(),
            dy = ys.toIntArray(),
        ).also { cachedPlan = it }
    }
}

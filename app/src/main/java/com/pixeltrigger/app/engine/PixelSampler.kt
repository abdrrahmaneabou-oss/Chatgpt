package com.pixeltrigger.app.engine

import android.graphics.Rect
import android.media.Image
import java.nio.ByteBuffer
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** Exact v2.12 pixel-region sampler, kept separate from tap delivery. */
object PixelSampler {
    fun sampleCircularRegion(
        image: Image,
        centerX: Int,
        centerY: Int,
        radiusX: Float,
        radiusY: Float,
    ): DetectionEngine.ColorSample? {
        val crop: Rect = image.cropRect
        if (centerX !in crop.left until crop.right || centerY !in crop.top until crop.bottom) return null

        val plane = image.planes.firstOrNull() ?: return null
        val buffer: ByteBuffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        if (pixelStride < 3 || rowStride <= 0) return null

        var redTotal = 0L
        var greenTotal = 0L
        var blueTotal = 0L
        var luminanceTotal = 0L
        var chromaTotal = 0L
        var whiteCount = 0
        var count = 0
        val base = buffer.position()

        val minX = max(floor(centerX - radiusX).toInt(), crop.left)
        val maxX = min(ceil(centerX + radiusX).toInt(), crop.right - 1)
        val minY = max(floor(centerY - radiusY).toInt(), crop.top)
        val maxY = min(ceil(centerY + radiusY).toInt(), crop.bottom - 1)

        for (y in minY..maxY) {
            val normalizedY = (y - centerY) / radiusY
            for (x in minX..maxX) {
                val normalizedX = (x - centerX) / radiusX
                if (normalizedX * normalizedX + normalizedY * normalizedY > 1f) continue

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
                count++
            }
        }

        if (count < DetectionEngine.MIN_SAMPLE_PIXELS) return null
        return DetectionEngine.ColorSample(
            averageRed = (redTotal / count).toInt(),
            averageGreen = (greenTotal / count).toInt(),
            averageBlue = (blueTotal / count).toInt(),
            whiteRatio = whiteCount.toFloat() / count.toFloat(),
            averageLuminance = (luminanceTotal / count).toInt(),
            averageChroma = (chromaTotal / count).toInt(),
        )
    }
}

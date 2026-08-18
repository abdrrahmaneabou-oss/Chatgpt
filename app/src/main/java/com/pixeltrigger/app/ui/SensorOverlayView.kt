package com.pixeltrigger.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import kotlin.math.min

class SensorOverlayView(context: Context, visibleDiameterPx: Int) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private var status: SensorStatus = SensorStatus.WAITING
    val outerDiameterPx: Int = maxOf(visibleDiameterPx + dp(10), dp(12))

    fun setStatus(value: SensorStatus) {
        status = value
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val radius = min(width, height).coerceAtMost(outerDiameterPx) / 2f - dp(1)
        val color = when (status) {
            SensorStatus.OFF -> Color.rgb(120, 120, 126)
            SensorStatus.WAITING -> Color.rgb(255, 184, 77)
            SensorStatus.ARMED -> Color.rgb(60, 220, 120)
            SensorStatus.FIRED -> Color.rgb(255, 80, 95)
            SensorStatus.INPUT_NOT_READY -> Color.rgb(220, 85, 255)
        }
        fill.color = Color.argb(40, Color.red(color), Color.green(color), Color.blue(color))
        paint.color = color
        paint.strokeWidth = dp(2).toFloat()
        canvas.drawCircle(cx, cy, radius, fill)
        canvas.drawCircle(cx, cy, radius, paint)
        canvas.drawCircle(cx, cy, maxOf(1f, radius * 0.18f), paint)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt().coerceAtLeast(1)
}

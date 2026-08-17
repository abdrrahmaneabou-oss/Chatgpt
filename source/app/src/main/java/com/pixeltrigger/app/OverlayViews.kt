package com.pixeltrigger.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import kotlin.math.ceil
import kotlin.math.max

class SensorOverlayView(
    context: Context,
    val sampleDiameterPx: Int,
) : View(context) {
    private val ringGapPx = max(1f, sampleDiameterPx * 0.10f)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = max(1f, sampleDiameterPx * 0.08f)
        alpha = 220
    }

    var status: SensorStatus = SensorStatus.WAITING
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    val outerDiameterPx: Int
        get() = ceil(sampleDiameterPx + ringGapPx * 2f + ringPaint.strokeWidth).toInt()

    init {
        setBackgroundColor(Color.TRANSPARENT)
        contentDescription = "دائرة الاستشعار"
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val monitoredRadius = sampleDiameterPx / 2f
        val ringRadius = ringGapPx + monitoredRadius + ringPaint.strokeWidth / 2f
        ringPaint.color = when (status) {
            SensorStatus.WAITING -> Color.rgb(255, 190, 80)
            SensorStatus.ARMED -> Color.rgb(68, 229, 170)
            SensorStatus.FIRED -> Color.rgb(255, 87, 111)
        }
        canvas.drawCircle(cx, cy, ringRadius, ringPaint)
    }
}

class TargetOverlayView(
    context: Context,
    val visibleDiameterPx: Int,
) : View(context) {
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(120, 112, 76, 255)
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = resources.displayMetrics.density * 1.5f
        color = Color.rgb(186, 169, 255)
    }
    private val crossPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = resources.displayMetrics.density
        color = Color.WHITE
    }

    init {
        setBackgroundColor(Color.TRANSPARENT)
        contentDescription = "دائرة تنفيذ الضغطة"
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val radius = visibleDiameterPx / 2f
        canvas.drawCircle(cx, cy, radius, fillPaint)
        canvas.drawCircle(cx, cy, radius, strokePaint)
        val arm = radius * 0.45f
        canvas.drawLine(cx - arm, cy, cx + arm, cy, crossPaint)
        canvas.drawLine(cx, cy - arm, cx, cy + arm, crossPaint)
    }
}

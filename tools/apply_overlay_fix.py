from pathlib import Path
import subprocess

ENGINE = Path('app/src/main/java/com/pixeltrigger/app/engine/DetectionEngine.kt')
SERVICE = Path('app/src/main/java/com/pixeltrigger/app/ScreenCaptureService.kt')
SENSOR_VIEW = Path('app/src/main/java/com/pixeltrigger/app/ui/SensorOverlayView.kt')
EXPECTED_ENGINE_BLOB = '3c772ee89cee6cce107409394345bab92c47e0d6'


def git_blob(path: Path) -> str:
    return subprocess.check_output(['git', 'hash-object', str(path)], text=True).strip()


def require_engine_baseline() -> None:
    actual = git_blob(ENGINE)
    if actual != EXPECTED_ENGINE_BLOB:
        raise SystemExit(f'DetectionEngine baseline mismatch: {actual}')


s = SERVICE.read_text()


def once(old: str, new: str, label: str) -> None:
    global s
    if old not in s:
        raise SystemExit(f'missing anchor: {label}')
    s = s.replace(old, new, 1)


def splice(start: str, end: str, replacement: str, label: str) -> None:
    global s
    i = s.find(start)
    if i < 0:
        raise SystemExit(f'missing section start: {label}')
    j = s.find(end, i + len(start))
    if j < 0:
        raise SystemExit(f'missing section end: {label}')
    s = s[:i] + replacement + s[j:]


require_engine_baseline()

once(
    'sensorVisibleDiameter = max(mmToPx(DetectionEngine.SENSOR_DIAMETER_MM), 1)',
    'sensorVisibleDiameter = max(mmToPx(MONITOR_DIAMETER_MM), 1)',
    'monitor diameter source',
)
once('clampPosition(sensorLp, sensor.outerDiameterPx)', 'clampCirclePosition(sensorLp, sensorVisibleDiameter)', 'sensor initial clamp')
once('clampPosition(targetLp, targetVisibleDiameter)', 'clampCirclePosition(targetLp, targetVisibleDiameter)', 'target initial clamp')

if 'private var circleEditMode = false' not in s:
    once(
        '    private var configMode = false\n',
        '    private var configMode = false\n    @Volatile private var circleEditMode = false\n',
        'circle edit state',
    )

once(
    '        if (!engineEnabled) return\n',
    '        if (!engineEnabled || circleEditMode) return\n',
    'pause capture processing while moving circles',
)

once(
    '''    private fun showMenu() {
        if (menuPanel != null) return
        tapEngine.refreshCapability()
        setConfigurationTouchability(true)
''',
    '''    private fun showMenu() {
        if (menuPanel != null) return
        tapEngine.refreshCapability()
        setConfigurationTouchability(false)
''',
    'menu should not implicitly enable circle dragging',
)

once(
    '''            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                toggleMenu()
                return true
            }
            override fun onDoubleTap(e: MotionEvent): Boolean {
                toggleEngine()
                return true
            }
''',
    '''            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (circleEditMode) finishCirclePositionEditing() else toggleMenu()
                return true
            }
            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (circleEditMode) finishCirclePositionEditing() else toggleEngine()
                return true
            }
''',
    'PT behavior while editing positions',
)

splice(
    '    private fun attachDrag(',
    '    private fun toggleEngine()',
    '''    private fun attachDrag(
        view: View,
        params: WindowManager.LayoutParams,
        visibleDiameter: Int,
        onMoved: (Int, Int) -> Unit,
    ) {
        var grabOffsetX = 0f
        var grabOffsetY = 0f
        var framePending = false

        fun updateNextFrame() {
            if (framePending) return
            framePending = true
            view.postOnAnimation {
                framePending = false
                runCatching { windowManager.updateViewLayout(view, params) }
            }
        }

        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    grabOffsetX = event.rawX - params.x
                    grabOffsetY = event.rawY - params.y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = (event.rawX - grabOffsetX).roundToInt()
                    params.y = (event.rawY - grabOffsetY).roundToInt()
                    clampCirclePosition(params, visibleDiameter)
                    updateNextFrame()
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    clampCirclePosition(params, visibleDiameter)
                    runCatching { windowManager.updateViewLayout(view, params) }
                    onMoved(params.x, params.y)
                    true
                }
                else -> true
            }
        }
    }

    private fun beginCirclePositionEditing() {
        closeMenu()
        circleEditMode = true
        setCirclesVisible(true)
        setConfigurationTouchability(true)
        updateButtonVisual()
        showMessage("اسحب الدائرتين إلى الموضع المطلوب، ثم اضغط ✓ للحفظ")
    }

    private fun finishCirclePositionEditing() {
        if (!circleEditMode) return
        circleEditMode = false
        setConfigurationTouchability(false)
        captureHandler?.post { detectionEngine.resetForSensorMove() }
        updateButtonVisual()
        showMessage("تم حفظ مواضع الدوائر")
    }

''',
    'circle drag and explicit edit mode',
)

splice(
    '    private fun updateButtonVisual() {',
    '    private fun toggleMenu() {',
    '''    private fun updateButtonVisual() {
        val button = menuButton ?: return
        val fill = when {
            circleEditMode -> Color.rgb(30, 165, 92)
            !engineEnabled -> Color.rgb(95, 95, 104)
            tapEngine.capability != InputCapability.CONCURRENT_TOUCH_SAFE -> Color.rgb(165, 70, 190)
            detectionEngine.state == DetectionEngine.State.ARMED -> Color.rgb(32, 170, 88)
            else -> Color.rgb(91, 54, 221)
        }
        button.text = when {
            circleEditMode -> "✓"
            engineEnabled -> "PT"
            else -> "OFF"
        }
        button.textSize = if (circleEditMode || engineEnabled) 15f else 10f
        button.background = roundedBackground(fill, Color.argb(210, 220, 220, 255), 18f)
    }

''',
    'PT visual state',
)

once(
    '        content.addView(menuButton("إظهار / إخفاء الدوائر") { setCirclesVisible(!circlesVisible) }, matchWrap(dp(50)))\n',
    '''        content.addView(
            actionCard(
                "تعديل مواضع الدوائر",
                "اسحب دائرة المراقبة ودائرة الضغط لأي موضع على الشاشة، ثم اضغط ✓ للحفظ.",
            ) { beginCirclePositionEditing() },
            matchWrap(dp(88)),
        )
        content.addView(menuButton("إظهار / إخفاء الدوائر") { setCirclesVisible(!circlesVisible) }, matchWrap(dp(50)))
''',
    'position card',
)

splice(
    '    private fun attachMenuDrag(',
    '    private fun closeMenu()',
    '''    private fun attachMenuDrag(handle: View, panel: View, params: WindowManager.LayoutParams) {
        var grabOffsetX = 0f
        var grabOffsetY = 0f
        var framePending = false

        fun updateNextFrame() {
            if (framePending) return
            framePending = true
            panel.postOnAnimation {
                framePending = false
                if (menuPanel === panel) runCatching { windowManager.updateViewLayout(panel, params) }
            }
        }

        handle.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    grabOffsetX = event.rawX - params.x
                    grabOffsetY = event.rawY - params.y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = (event.rawX - grabOffsetX).roundToInt()
                    params.y = (event.rawY - grabOffsetY).roundToInt()
                    clampMenuPosition(params)
                    updateNextFrame()
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    clampMenuPosition(params)
                    runCatching { windowManager.updateViewLayout(panel, params) }
                    preferences.edit().putInt(KEY_MENU_X, params.x).putInt(KEY_MENU_Y, params.y).apply()
                    true
                }
                else -> true
            }
        }
    }

''',
    'smooth menu drag',
)

start = '        listOf(sensorView to sensorParams, targetView to targetParams, menuButton to menuButtonParams).forEach'
end = '        menuPanelParams?.let'
i = s.find(start)
j = s.find(end, i if i >= 0 else 0)
if i < 0 or j < 0:
    raise SystemExit('missing display geometry clamp section')
s = s[:i] + '''        sensorParams?.let { lp ->
            clampCirclePosition(lp, sensorVisibleDiameter)
            sensorView?.let { runCatching { windowManager.updateViewLayout(it, lp) } }
        }
        targetParams?.let { lp ->
            val visibleDiameter = max(mmToPx(5f), dp(12))
            clampCirclePosition(lp, visibleDiameter)
            targetView?.let { runCatching { windowManager.updateViewLayout(it, lp) } }
        }
        menuButtonParams?.let { lp ->
            clampPosition(lp, min(lp.width, lp.height))
            menuButton?.let { runCatching { windowManager.updateViewLayout(it, lp) } }
        }
''' + s[j:]

once(
    '    private fun clampMenuPosition(params: WindowManager.LayoutParams) {\n',
    '''    private fun clampCirclePosition(params: WindowManager.LayoutParams, visibleDiameter: Int) {
        val halfWindowW = max(params.width, 1) / 2f
        val halfWindowH = max(params.height, 1) / 2f
        val radius = max(visibleDiameter, 1) / 2f
        val centerX = (params.x + halfWindowW).coerceIn(radius, max(screenWidth - radius, radius))
        val centerY = (params.y + halfWindowH).coerceIn(radius, max(screenHeight - radius, radius))
        params.x = (centerX - halfWindowW).roundToInt()
        params.y = (centerY - halfWindowH).roundToInt()
    }

    private fun clampMenuPosition(params: WindowManager.LayoutParams) {
''',
    'edge-aware circle clamp',
)

once(
    '    private fun menuButton(textValue: String, action: () -> Unit): Button = Button(this).apply {\n',
    '''    private fun actionCard(title: String, subtitle: String, action: () -> Unit): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(8), dp(14), dp(8))
            background = roundedBackground(Color.rgb(232, 247, 239), Color.rgb(64, 166, 105), 14f)
            isClickable = true
            isFocusable = true
            addView(TextView(this@ScreenCaptureService).apply {
                text = title
                textSize = 16f
                setTextColor(Color.rgb(20, 95, 55))
            }, matchWrap())
            addView(TextView(this@ScreenCaptureService).apply {
                text = subtitle
                textSize = 12f
                setTextColor(Color.rgb(55, 75, 64))
            }, matchWrap())
            setOnClickListener { action() }
        }

    private fun menuButton(textValue: String, action: () -> Unit): Button = Button(this).apply {
''',
    'action card helper',
)

if 'private const val MONITOR_DIAMETER_MM = 0.5f' not in s:
    once(
        '        private const val PREFS_NAME = "pixeltrigger_prefs"\n',
        '        private const val PREFS_NAME = "pixeltrigger_prefs"\n        private const val MONITOR_DIAMETER_MM = 0.5f\n',
        '0.5mm constant',
    )

sensor_source = '''package com.pixeltrigger.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import kotlin.math.min

/** Visible sensor diameter is exact. A larger invisible hit box exists only while positioning. */
class SensorOverlayView(context: Context, requestedVisibleDiameterPx: Int) : View(context) {
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private var status: SensorStatus = SensorStatus.WAITING

    val visibleDiameterPx: Int = maxOf(requestedVisibleDiameterPx, 1)
    val outerDiameterPx: Int = visibleDiameterPx

    fun setStatus(value: SensorStatus) {
        status = value
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val diameter = min(visibleDiameterPx.toFloat(), min(width, height).toFloat()).coerceAtLeast(1f)
        val radius = diameter / 2f
        val strokeWidth = (diameter * 0.16f).coerceIn(1f, radius.coerceAtLeast(1f))
        val strokeRadius = (radius - strokeWidth / 2f).coerceAtLeast(0f)
        val color = when (status) {
            SensorStatus.OFF -> Color.rgb(120, 120, 126)
            SensorStatus.WAITING -> Color.rgb(255, 184, 77)
            SensorStatus.ARMED -> Color.rgb(60, 220, 120)
            SensorStatus.FIRED -> Color.rgb(255, 80, 95)
            SensorStatus.INPUT_NOT_READY -> Color.rgb(220, 85, 255)
        }
        fillPaint.color = Color.argb(70, Color.red(color), Color.green(color), Color.blue(color))
        strokePaint.color = color
        strokePaint.strokeWidth = strokeWidth
        canvas.drawCircle(cx, cy, radius, fillPaint)
        canvas.drawCircle(cx, cy, strokeRadius, strokePaint)
    }
}
'''

# Validate the complete in-memory service before writing any source file.
checks = {
    '0.5mm monitor constant': 'MONITOR_DIAMETER_MM = 0.5f' in s,
    'sample radius follows visible diameter': 'val screenRadius = sensorVisibleDiameter / 2f' in s,
    'legacy 0.8mm source removed from service': 'DetectionEngine.SENSOR_DIAMETER_MM' not in s,
    'position card': 'تعديل مواضع الدوائر' in s,
    'sampling paused during edit': 'if (!engineEnabled || circleEditMode) return' in s,
    'circle drag frame coalescing': 'view.postOnAnimation' in s,
    'menu drag frame coalescing': 'panel.postOnAnimation' in s,
    'edge-aware circle clamp': 'private fun clampCirclePosition' in s,
    'visible diameter equals outer diameter': 'val outerDiameterPx: Int = visibleDiameterPx' in sensor_source,
    'no old dp inflation in sensor view': '+ dp(10)' not in sensor_source and 'dp(12)' not in sensor_source,
}
failed = [name for name, ok in checks.items() if not ok]
if failed:
    raise SystemExit('validation failed: ' + ', '.join(failed))

SERVICE.write_text(s)
SENSOR_VIEW.write_text(sensor_source)

require_engine_baseline()
print('OVERLAY_FIX_APPLIED_AND_STATICALLY_VALIDATED')

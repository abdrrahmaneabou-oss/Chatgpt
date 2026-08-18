package com.pixeltrigger.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.PowerManager
import android.os.Process
import android.os.SystemClock
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.pixeltrigger.app.engine.DetectionEngine
import com.pixeltrigger.app.engine.PixelSampler
import com.pixeltrigger.app.input.InputCapability
import com.pixeltrigger.app.input.ShizukuTapEngine
import com.pixeltrigger.app.input.TapCoordinator
import com.pixeltrigger.app.input.TapResult
import com.pixeltrigger.app.ui.SensorOverlayView
import com.pixeltrigger.app.ui.SensorStatus
import com.pixeltrigger.app.ui.TargetOverlayView
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class ScreenCaptureService : Service() {
    private lateinit var windowManager: WindowManager
    private lateinit var preferences: SharedPreferences
    private val mainHandler = Handler(android.os.Looper.getMainLooper())

    private var captureThread: HandlerThread? = null
    private var captureHandler: Handler? = null
    private var imageReader: ImageReader? = null
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private var screenWidth = 0
    private var screenHeight = 0
    private var densityDpi = 0

    private var sensorView: SensorOverlayView? = null
    private var sensorParams: WindowManager.LayoutParams? = null
    private var sensorVisibleDiameter = 1
    private var sensorTouchSize = 1

    private var targetView: TargetOverlayView? = null
    private var targetParams: WindowManager.LayoutParams? = null
    private var targetTouchSize = 1

    private var menuButton: TextView? = null
    private var menuButtonParams: WindowManager.LayoutParams? = null
    private var menuPanel: View? = null
    private var menuPanelParams: WindowManager.LayoutParams? = null
    private var menuStatusText: TextView? = null

    private var circlesVisible = true
    @Volatile private var engineEnabled = true
    private val engineStateLock = Any()
    private var configMode = false
    @Volatile private var circleEditMode = false
    private var lastInputReady = false

    private val detectionEngine = DetectionEngine()
    private lateinit var tapEngine: ShizukuTapEngine
    private lateinit var tapCoordinator: TapCoordinator

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit
        override fun onDisplayChanged(displayId: Int) {
            mainHandler.removeCallbacks(refreshDisplayRunnable)
            mainHandler.postDelayed(refreshDisplayRunnable, 120L)
        }
    }
    private val refreshDisplayRunnable = Runnable { refreshDisplayGeometry() }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        circlesVisible = preferences.getBoolean(KEY_CIRCLES_VISIBLE, true)
        detectionEngine.whiteRearmEnabled = preferences.getBoolean(KEY_WHITE_REARM, true)
        detectionEngine.rearmDelayEnabled = preferences.getBoolean(KEY_REARM_DELAY_ENABLED, false)
        detectionEngine.rearmSeconds = preferences.getInt(KEY_REARM_SECONDS, 10).coerceIn(5, 60)

        tapEngine = ShizukuTapEngine(this)
        tapCoordinator = TapCoordinator(tapEngine)
        tapEngine.connect()

        (getSystemService(DISPLAY_SERVICE) as DisplayManager).registerDisplayListener(displayListener, mainHandler)
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        wakeLock = (getSystemService(POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:PixelMonitor")
            .apply { acquire() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> if (mediaProjection == null) {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
                val data = projectionIntent(intent)
                if (resultCode == 0 || data == null) stopSelf() else setupProjection(resultCode, data)
            }
            ACTION_STOP -> shutdownCompletely()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun projectionIntent(intent: Intent): Intent? =
        if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        else @Suppress("DEPRECATION") intent.getParcelableExtra(EXTRA_RESULT_DATA)

    private fun setupProjection(resultCode: Int, resultData: Intent) {
        val bounds = currentScreenBounds()
        screenWidth = bounds.width()
        screenHeight = bounds.height()
        densityDpi = resources.displayMetrics.densityDpi

        captureThread = HandlerThread("PixelTriggerCapture", Process.THREAD_PRIORITY_URGENT_DISPLAY).also { it.start() }
        captureHandler = Handler(captureThread!!.looper)

        mediaProjection = (getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager)
            .getMediaProjection(resultCode, resultData)
            .also { projection ->
                projection.registerCallback(object : MediaProjection.Callback() {
                    override fun onStop() = stopSelf()
                }, mainHandler)
            }
        imageReader = createImageReader(screenWidth, screenHeight)
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "PixelTriggerDisplay",
            screenWidth,
            screenHeight,
            densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            captureHandler,
        )
        mainHandler.post {
            createOverlays()
            updateSensorStatus(if (tapEngine.isReady()) SensorStatus.WAITING else SensorStatus.INPUT_NOT_READY)
        }
    }

    private fun createImageReader(width: Int, height: Int): ImageReader =
        ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2).also { reader ->
            reader.setOnImageAvailableListener({ source ->
                source.acquireLatestImage()?.use(::processImage)
            }, captureHandler)
        }

    private fun processImage(image: Image) {
        if (!engineEnabled || circleEditMode) return

        val inputReady = tapEngine.isReady()
        if (inputReady != lastInputReady) {
            lastInputReady = inputReady
            detectionEngine.resetForSensorMove()
            updateSensorStatus(if (inputReady) SensorStatus.WAITING else SensorStatus.INPUT_NOT_READY)
        }
        if (!inputReady) return

        val params = sensorParams ?: return
        if (screenWidth <= 0 || screenHeight <= 0) return
        val crop = image.cropRect
        if (crop.width() <= 0 || crop.height() <= 0) return

        val screenCenterX = params.x + sensorTouchSize / 2
        val screenCenterY = params.y + sensorTouchSize / 2
        val centerX = (crop.left + (screenCenterX * crop.width().toFloat() / screenWidth)).roundToInt()
            .coerceIn(crop.left, crop.right - 1)
        val centerY = (crop.top + (screenCenterY * crop.height().toFloat() / screenHeight)).roundToInt()
            .coerceIn(crop.top, crop.bottom - 1)
        val screenRadius = sensorVisibleDiameter / 2f
        val radiusX = max(0.5f, crop.width() * screenRadius / screenWidth)
        val radiusY = max(0.5f, crop.height() * screenRadius / screenHeight)
        val sample = PixelSampler.sampleCircularRegion(image, centerX, centerY, radiusX, radiusY) ?: return

        when (val event = detectionEngine.processSample(sample, SystemClock.elapsedRealtime())) {
            is DetectionEngine.Event.Armed,
            is DetectionEngine.Event.Rearmed,
            is DetectionEngine.Event.ManualRearmed -> updateSensorStatus(SensorStatus.ARMED)
            is DetectionEngine.Event.Fired -> {
                updateSensorStatus(SensorStatus.FIRED)
                executeTapImmediately()
            }
            is DetectionEngine.Event.ManualRearmTimedOut -> showMessage("لم يتم التسليح: اللون الأبيض غير موجود")
            else -> Unit
        }
    }

    /** Called on the high-priority capture thread immediately after DetectionEngine FIRE. */
    private fun executeTapImmediately() {
        val target = targetParams ?: return
        val tapX = target.x + targetTouchSize / 2f
        val tapY = target.y + targetTouchSize / 2f
        val result = synchronized(engineStateLock) {
            // OFF wins over a frame that was already being processed when the user double-tapped PT.
            if (!engineEnabled) return
            tapCoordinator.fire(tapX, tapY, displayId = 0)
        }
        if (result is TapResult.Rejected) {
            // Fail closed: never substitute Accessibility because that can cancel the player's touch.
            detectionEngine.resetForSensorMove()
            updateSensorStatus(SensorStatus.INPUT_NOT_READY)
            showMessage("تم منع الضغطة لحماية التحكم: ${result.reason}")
        }
    }

    private fun createOverlays() {
        if (sensorView != null) return
        sensorVisibleDiameter = max(mmToPx(MONITOR_DIAMETER_MM), 1)
        val targetVisibleDiameter = max(mmToPx(5f), dp(12))

        val sensor = SensorOverlayView(this, sensorVisibleDiameter)
        sensorView = sensor
        sensorTouchSize = max(dp(48), sensor.outerDiameterPx + dp(30))
        val sensorLp = overlayParams(sensorTouchSize, sensorTouchSize).apply {
            x = preferences.getInt(KEY_SENSOR_X, screenWidth / 2 - sensorTouchSize / 2)
            y = preferences.getInt(KEY_SENSOR_Y, screenHeight / 2 - sensorTouchSize / 2)
        }
        sensorParams = sensorLp
        clampCirclePosition(sensorLp, sensorVisibleDiameter)
        windowManager.addView(sensor, sensorLp)
        attachDrag(sensor, sensorLp, sensor.outerDiameterPx) { x, y ->
            detectionEngine.resetForSensorMove()
            preferences.edit().putInt(KEY_SENSOR_X, x).putInt(KEY_SENSOR_Y, y).apply()
        }

        targetTouchSize = max(dp(52), dp(24) + targetVisibleDiameter)
        val target = TargetOverlayView(this, targetVisibleDiameter)
        targetView = target
        val targetLp = overlayParams(targetTouchSize, targetTouchSize).apply {
            x = preferences.getInt(KEY_TARGET_X, screenWidth / 2 + dp(70))
            y = preferences.getInt(KEY_TARGET_Y, screenHeight / 2 - targetTouchSize / 2)
        }
        targetParams = targetLp
        clampCirclePosition(targetLp, targetVisibleDiameter)
        windowManager.addView(target, targetLp)
        attachDrag(target, targetLp, targetVisibleDiameter) { x, y ->
            preferences.edit().putInt(KEY_TARGET_X, x).putInt(KEY_TARGET_Y, y).apply()
        }

        val buttonSize = dp(46)
        val button = TextView(this).apply {
            text = "PT"
            textSize = 15f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = roundedBackground(Color.rgb(91, 54, 221), Color.rgb(155, 135, 255), 18f)
        }
        menuButton = button
        val buttonLp = overlayParams(buttonSize, buttonSize).apply {
            x = preferences.getInt(KEY_BUTTON_X, max(screenWidth - buttonSize - dp(12), 0))
            y = preferences.getInt(KEY_BUTTON_Y, dp(60))
            flags = baseOverlayFlags() // always touchable
        }
        menuButtonParams = buttonLp
        clampPosition(buttonLp, buttonSize)
        windowManager.addView(button, buttonLp)
        attachFloatingButtonGesture(button, buttonLp)

        setConfigurationTouchability(false)
        setCirclesVisible(circlesVisible)
        updateButtonVisual()
    }

    /** Sensor/target do not consume gameplay touches unless the menu is intentionally open. */
    private fun setConfigurationTouchability(enabled: Boolean) {
        configMode = enabled
        listOf(sensorView to sensorParams, targetView to targetParams).forEach { (view, lp) ->
            if (view == null || lp == null) return@forEach
            lp.flags = if (enabled) baseOverlayFlags() else baseOverlayFlags() or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            runCatching { windowManager.updateViewLayout(view, lp) }
        }
    }

    private fun attachFloatingButtonGesture(view: View, params: WindowManager.LayoutParams) {
        val detector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true
            override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
                params.x -= distanceX.roundToInt()
                params.y -= distanceY.roundToInt()
                clampPosition(params, view.width.takeIf { it > 0 } ?: params.width)
                windowManager.updateViewLayout(view, params)
                preferences.edit().putInt(KEY_BUTTON_X, params.x).putInt(KEY_BUTTON_Y, params.y).apply()
                return true
            }
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (circleEditMode) finishCirclePositionEditing() else toggleMenu()
                return true
            }
            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (circleEditMode) finishCirclePositionEditing() else toggleEngine()
                return true
            }
        })
        view.setOnTouchListener { _, event -> detector.onTouchEvent(event) }
    }

    private fun attachDrag(
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

    private fun toggleEngine() {
        val enableRequested = synchronized(engineStateLock) {
            val requested = !engineEnabled
            // Enter OFF immediately for both transitions. Re-enable only after the capture thread
            // has reset all v2.12 arming state, so stale ARMED state can never fire on resume.
            engineEnabled = false
            requested
        }

        if (!enableRequested) {
            captureHandler?.post { detectionEngine.resetForSensorMove() }
            updateSensorStatus(SensorStatus.OFF)
            return
        }

        val restart = Runnable {
            detectionEngine.resetForSensorMove()
            synchronized(engineStateLock) { engineEnabled = true }
            updateSensorStatus(if (tapEngine.isReady()) SensorStatus.WAITING else SensorStatus.INPUT_NOT_READY)
        }
        captureHandler?.post(restart) ?: restart.run()
    }

    private fun updateButtonVisual() {
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

    private fun toggleMenu() {
        if (menuPanel != null) closeMenu() else showMenu()
    }

    private fun showMenu() {
        if (menuPanel != null) return
        tapEngine.refreshCapability()
        setConfigurationTouchability(false)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(12))
            background = roundedBackground(Color.rgb(245, 245, 250), Color.rgb(155, 155, 170), 18f)
        }
        val header = TextView(this).apply {
            text = "PixelTrigger  •  اسحب من هنا لتحريك القائمة"
            textSize = 15f
            setTextColor(Color.rgb(25, 25, 32))
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(9), dp(8), dp(9))
            background = roundedBackground(Color.rgb(224, 222, 245), Color.rgb(172, 166, 220), 12f)
        }
        root.addView(header, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(44)))

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, 0)
        }
        menuStatusText = TextView(this).apply {
            text = engineStatusText()
            textSize = 14f
            setTextColor(Color.rgb(40, 40, 48))
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(6), dp(6), dp(8))
        }
        content.addView(menuStatusText, matchWrap())
        content.addView(TextView(this).apply {
            text = "Input: ${tapEngine.capability}\n${tapEngine.capabilityDetail}"
            textSize = 12f
            setTextColor(Color.rgb(70, 70, 88))
            gravity = Gravity.CENTER
            setPadding(dp(5), 0, dp(5), dp(8))
        }, matchWrap())

        content.addView(
            actionCard(
                "تعديل مواضع الدوائر",
                "اسحب دائرة المراقبة ودائرة الضغط لأي موضع على الشاشة، ثم اضغط ✓ للحفظ.",
            ) { beginCirclePositionEditing() },
            matchWrap(dp(88)),
        )
        content.addView(menuButton("إظهار / إخفاء الدوائر") { setCirclesVisible(!circlesVisible) }, matchWrap(dp(50)))

        val whiteSwitch = Switch(this).apply {
            text = "إعادة التسليح عند ظهور الأبيض"
            isChecked = detectionEngine.whiteRearmEnabled
            setTextColor(Color.rgb(30, 30, 36))
            setOnCheckedChangeListener { _, checked ->
                detectionEngine.whiteRearmEnabled = checked
                preferences.edit().putBoolean(KEY_WHITE_REARM, checked).apply()
            }
        }
        content.addView(whiteSwitch, matchWrap(dp(54)))

        val delaySwitch = Switch(this).apply {
            text = "تأخير إعادة التسليح"
            isChecked = detectionEngine.rearmDelayEnabled
            setTextColor(Color.rgb(30, 30, 36))
            setOnCheckedChangeListener { _, checked ->
                detectionEngine.rearmDelayEnabled = checked
                preferences.edit().putBoolean(KEY_REARM_DELAY_ENABLED, checked).apply()
            }
        }
        content.addView(delaySwitch, matchWrap(dp(54)))

        val secondsText = TextView(this).apply {
            text = "${detectionEngine.rearmSeconds} ثانية"
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(30, 30, 36))
            textSize = 16f
        }
        val durationRow = LinearLayout(this).apply { gravity = Gravity.CENTER }
        durationRow.addView(menuButton("−") {
            detectionEngine.rearmSeconds = (detectionEngine.rearmSeconds - 1).coerceIn(5, 60)
            preferences.edit().putInt(KEY_REARM_SECONDS, detectionEngine.rearmSeconds).apply()
            secondsText.text = "${detectionEngine.rearmSeconds} ثانية"
        }, LinearLayout.LayoutParams(dp(62), dp(48)))
        durationRow.addView(secondsText, LinearLayout.LayoutParams(0, dp(48), 1f))
        durationRow.addView(menuButton("+") {
            detectionEngine.rearmSeconds = (detectionEngine.rearmSeconds + 1).coerceIn(5, 60)
            preferences.edit().putInt(KEY_REARM_SECONDS, detectionEngine.rearmSeconds).apply()
            secondsText.text = "${detectionEngine.rearmSeconds} ثانية"
        }, LinearLayout.LayoutParams(dp(62), dp(48)))
        content.addView(durationRow, matchWrap(dp(54)))

        content.addView(menuButton("تفعيل التسليح الآن لمرة واحدة") {
            captureHandler?.post {
                if (!detectionEngine.requestOneTimeRearmOverride(SystemClock.elapsedRealtime())) {
                    showMessage("لا يوجد تأخير جارٍ يمكن تجاوزه")
                }
            }
            closeMenu()
        }, matchWrap(dp(50)))
        content.addView(menuButton("إغلاق القائمة") { closeMenu() }, matchWrap(dp(50)))
        content.addView(menuButton("إيقاف PixelTrigger بالكامل") { shutdownCompletely() }, matchWrap(dp(50), danger = true))

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = true
            addView(content, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        }
        root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        val margin = dp(10)
        val availableWidth = max(screenWidth - margin * 2, 1)
        val availableHeight = max(screenHeight - margin * 2, 1)
        val width = min(dp(420), availableWidth).coerceAtLeast(min(dp(220), availableWidth))
        val height = min(dp(600), availableHeight).coerceAtLeast(min(dp(180), availableHeight))
        val lp = WindowManager.LayoutParams(
            width,
            height,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = preferences.getInt(KEY_MENU_X, ((screenWidth - width) / 2).coerceAtLeast(margin))
            y = preferences.getInt(KEY_MENU_Y, ((screenHeight - height) / 2).coerceAtLeast(margin))
        }
        menuPanel = root
        menuPanelParams = lp
        clampMenuPosition(lp)
        windowManager.addView(root, lp)
        attachMenuDrag(header, root, lp)
    }

    private fun attachMenuDrag(handle: View, panel: View, params: WindowManager.LayoutParams) {
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

    private fun closeMenu() {
        menuPanel?.let { runCatching { windowManager.removeView(it) } }
        menuPanel = null
        menuPanelParams = null
        menuStatusText = null
        setConfigurationTouchability(false)
    }

    private fun setCirclesVisible(visible: Boolean) {
        circlesVisible = visible
        preferences.edit().putBoolean(KEY_CIRCLES_VISIBLE, visible).apply()
        sensorView?.visibility = if (visible) View.VISIBLE else View.INVISIBLE
        targetView?.visibility = if (visible) View.VISIBLE else View.INVISIBLE
    }

    private fun engineStatusText(): String = when {
        !engineEnabled -> "المحرك: OFF — انقر مرتين بسرعة على PT للتشغيل"
        tapEngine.capability != InputCapability.CONCURRENT_TOUCH_SAFE -> "المحرك: ينتظر Shizuku الآمن"
        detectionEngine.state == DetectionEngine.State.ARMED -> "المحرك: ARMED"
        detectionEngine.state == DetectionEngine.State.WAITING_REARM -> "المحرك: WAITING_REARM"
        else -> "المحرك: WAITING_FOR_WHITE"
    }

    private fun updateSensorStatus(status: SensorStatus) {
        mainHandler.post {
            sensorView?.setStatus(status)
            updateButtonVisual()
            menuStatusText?.text = engineStatusText()
        }
    }

    private fun refreshDisplayGeometry() {
        val bounds = currentScreenBounds()
        val newWidth = bounds.width()
        val newHeight = bounds.height()
        if (newWidth <= 0 || newHeight <= 0 || (newWidth == screenWidth && newHeight == screenHeight)) return
        screenWidth = newWidth
        screenHeight = newHeight
        densityDpi = resources.displayMetrics.densityDpi

        captureHandler?.post {
            val replacement = createImageReader(newWidth, newHeight)
            val old = imageReader
            imageReader = replacement
            virtualDisplay?.resize(newWidth, newHeight, densityDpi)
            virtualDisplay?.surface = replacement.surface
            old?.close()
            detectionEngine.resetForSensorMove()
        }
        sensorParams?.let { lp ->
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
        menuPanelParams?.let { lp ->
            val panel = menuPanel ?: return@let
            val availableWidth = max(screenWidth - dp(20), 1)
            val availableHeight = max(screenHeight - dp(20), 1)
            lp.width = min(dp(420), availableWidth).coerceAtLeast(min(dp(220), availableWidth))
            lp.height = min(dp(600), availableHeight).coerceAtLeast(min(dp(180), availableHeight))
            clampMenuPosition(lp)
            runCatching { windowManager.updateViewLayout(panel, lp) }
        }
    }

    private fun currentScreenBounds(): Rect = if (Build.VERSION.SDK_INT >= 30) {
        windowManager.currentWindowMetrics.bounds
    } else {
        @Suppress("DEPRECATION")
        val point = android.graphics.Point().also { windowManager.defaultDisplay.getRealSize(it) }
        Rect(0, 0, point.x, point.y)
    }

    private fun clampPosition(params: WindowManager.LayoutParams, @Suppress("UNUSED_PARAMETER") visibleDiameter: Int) {
        val w = max(params.width, 1)
        val h = max(params.height, 1)
        params.x = params.x.coerceIn(0, max(screenWidth - w, 0))
        params.y = params.y.coerceIn(0, max(screenHeight - h, 0))
    }

    private fun clampCirclePosition(params: WindowManager.LayoutParams, visibleDiameter: Int) {
        val halfWindowW = max(params.width, 1) / 2f
        val halfWindowH = max(params.height, 1) / 2f
        val radius = max(visibleDiameter, 1) / 2f
        val centerX = (params.x + halfWindowW).coerceIn(radius, max(screenWidth - radius, radius))
        val centerY = (params.y + halfWindowH).coerceIn(radius, max(screenHeight - radius, radius))
        params.x = (centerX - halfWindowW).roundToInt()
        params.y = (centerY - halfWindowH).roundToInt()
    }

    private fun clampMenuPosition(params: WindowManager.LayoutParams) {
        params.x = params.x.coerceIn(0, max(screenWidth - params.width, 0))
        params.y = params.y.coerceIn(0, max(screenHeight - params.height, 0))
    }

    private fun overlayParams(width: Int, height: Int) = WindowManager.LayoutParams(
        width,
        height,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        baseOverlayFlags(),
        PixelFormat.TRANSLUCENT,
    ).apply { gravity = Gravity.TOP or Gravity.START }

    private fun baseOverlayFlags(): Int =
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN

    private fun actionCard(title: String, subtitle: String, action: () -> Unit): View =
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
        text = textValue
        textSize = 14f
        setOnClickListener { action() }
    }

    private fun matchWrap(height: Int = LinearLayout.LayoutParams.WRAP_CONTENT, danger: Boolean = false): LinearLayout.LayoutParams {
        @Suppress("UNUSED_VARIABLE") val ignored = danger
        return LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, height).apply { bottomMargin = dp(6) }
    }

    private fun roundedBackground(fill: Int, stroke: Int, radiusDp: Float): GradientDrawable = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = dp(radiusDp).toFloat()
        setStroke(dp(1), stroke)
    }

    private fun mmToPx(mm: Float): Int {
        val metrics = resources.displayMetrics
        val x = metrics.xdpi.takeIf { it.isFinite() && it in 100f..1000f } ?: metrics.densityDpi.toFloat()
        val y = metrics.ydpi.takeIf { it.isFinite() && it in 100f..1000f } ?: metrics.densityDpi.toFloat()
        val dpi = (x + y) / 2f
        return (mm * dpi / 25.4f).roundToInt()
    }

    private fun showMessage(message: String) {
        mainHandler.post { Toast.makeText(this, message, Toast.LENGTH_SHORT).show() }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "PixelTrigger monitoring", NotificationManager.IMPORTANCE_LOW),
            )
        }
    }

    private fun buildNotification(): Notification = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_menu_view)
        .setContentTitle("PixelTrigger")
        .setContentText("مراقبة البكسل فعّالة — Shizuku No Root")
        .setOngoing(true)
        .build()

    private fun shutdownCompletely() {
        closeMenu()
        stopSelf()
    }

    override fun onDestroy() {
        (getSystemService(DISPLAY_SERVICE) as DisplayManager).unregisterDisplayListener(displayListener)
        closeMenu()
        listOf(sensorView, targetView, menuButton).forEach { view -> if (view != null) runCatching { windowManager.removeView(view) } }
        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection?.stop()
        captureThread?.quitSafely()
        tapEngine.disconnect()
        wakeLock?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()
    private fun dp(value: Float): Int = (value * resources.displayMetrics.density).roundToInt()

    companion object {
        const val ACTION_START = "com.pixeltrigger.app.action.START"
        const val ACTION_STOP = "com.pixeltrigger.app.action.STOP"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"

        private const val CHANNEL_ID = "pixeltrigger_monitor"
        private const val NOTIFICATION_ID = 41
        private const val PREFS_NAME = "pixeltrigger_prefs"
        private const val MONITOR_DIAMETER_MM = 0.5f
        private const val KEY_SENSOR_X = "sensor_x"
        private const val KEY_SENSOR_Y = "sensor_y"
        private const val KEY_TARGET_X = "target_x"
        private const val KEY_TARGET_Y = "target_y"
        private const val KEY_BUTTON_X = "button_x"
        private const val KEY_BUTTON_Y = "button_y"
        private const val KEY_MENU_X = "menu_x"
        private const val KEY_MENU_Y = "menu_y"
        private const val KEY_CIRCLES_VISIBLE = "circles_visible"
        private const val KEY_WHITE_REARM = "white_rearm_enabled"
        private const val KEY_REARM_DELAY_ENABLED = "rearm_delay_enabled"
        private const val KEY_REARM_SECONDS = "rearm_seconds"
    }
}

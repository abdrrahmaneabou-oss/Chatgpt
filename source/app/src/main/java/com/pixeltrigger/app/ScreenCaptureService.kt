package com.pixeltrigger.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
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
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.pixeltrigger.app.engine.DetectionEngine
import com.pixeltrigger.app.engine.TriggerPipeline
import com.pixeltrigger.app.input.LegacyAccessibilityTapEngine
import com.pixeltrigger.app.input.RootTouchDaemonController
import com.pixeltrigger.app.input.SwitchableTapEngine
import com.pixeltrigger.app.input.TapCoordinator
import com.pixeltrigger.app.input.TapEngine
import com.pixeltrigger.app.input.TapRequest
import com.pixeltrigger.app.input.TapResult
import com.pixeltrigger.app.input.UnifiedTouchRootEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.nio.ByteBuffer
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class ScreenCaptureService : Service() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private lateinit var windowManager: WindowManager
    private lateinit var preferences: SharedPreferences
    private lateinit var detector: DetectionEngine
    private lateinit var switchableTapEngine: SwitchableTapEngine
    private lateinit var tapCoordinator: TapCoordinator
    private lateinit var triggerPipeline: TriggerPipeline
    private lateinit var rootController: RootTouchDaemonController
    private lateinit var rootTapEngine: UnifiedTouchRootEngine
    private lateinit var legacyTapEngine: LegacyAccessibilityTapEngine

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var captureThread: HandlerThread? = null
    private var captureHandler: Handler? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private var screenWidth = 0
    private var screenHeight = 0
    private var densityDpi = 0

    private var sensorView: SensorOverlayView? = null
    private var targetView: TargetOverlayView? = null
    private var menuButton: TextView? = null
    private var menuPanel: View? = null
    private var backendStatusView: TextView? = null

    private var sensorParams: WindowManager.LayoutParams? = null
    private var targetParams: WindowManager.LayoutParams? = null
    private var menuButtonParams: WindowManager.LayoutParams? = null
    private var menuPanelParams: WindowManager.LayoutParams? = null

    private var sensorVisibleDiameter = 1
    private var sensorTouchSize = 1
    private var targetTouchSize = 1
    private var circlesVisible = true
    private var editMode = false
    private var startingProjection = false
    @Volatile private var backendStatus = "تهيئة محرك الإدخال…"

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit
        override fun onDisplayChanged(displayId: Int) {
            if (displayId == 0 && mediaProjection != null) {
                mainHandler.removeCallbacks(refreshDisplayRunnable)
                mainHandler.postDelayed(refreshDisplayRunnable, 250L)
            }
        }
    }

    private val refreshDisplayRunnable = Runnable { refreshDisplayGeometry() }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WindowManager::class.java)
        preferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val whiteRearm = preferences.getBoolean(KEY_WHITE_REARM, true)
        val timedRearm = preferences.getBoolean(
            KEY_REARM_DELAY_ENABLED,
            preferences.getBoolean(KEY_TIMED_REARM_LEGACY, false),
        )
        val rearmSeconds = preferences.getInt(KEY_REARM_SECONDS, 10).coerceIn(5, 60)
        detector = DetectionEngine(
            whiteRearmEnabled = whiteRearm,
            rearmDelayEnabled = timedRearm,
            rearmSeconds = rearmSeconds,
        )

        rootController = RootTouchDaemonController(this)
        legacyTapEngine = LegacyAccessibilityTapEngine { TriggerAccessibilityService.current() }
        val unavailable = object : TapEngine {
            override val name: String = "input-not-ready"
            override suspend fun tap(request: TapRequest): TapResult = TapResult.Rejected(
                triggerId = request.triggerId,
                acceptedAtNs = SystemClock.elapsedRealtimeNanos(),
                reason = "tap backend not ready",
            )
        }
        switchableTapEngine = SwitchableTapEngine(unavailable)
        rootTapEngine = UnifiedTouchRootEngine(rootController) {
            UnifiedTouchRootEngine.DisplayInfo(
                widthPx = screenWidth,
                heightPx = screenHeight,
                rotation = currentRotation(),
            )
        }
        tapCoordinator = TapCoordinator(switchableTapEngine)
        triggerPipeline = TriggerPipeline(
            detector = detector,
            tapCoordinator = tapCoordinator,
            scope = serviceScope,
            targetProvider = {
                val params = targetParams
                if (params == null || targetTouchSize <= 0) null
                else TriggerPipeline.Target(
                    x = params.x + targetTouchSize / 2f,
                    y = params.y + targetTouchSize / 2f,
                )
            },
            backendNameProvider = { switchableTapEngine.name },
        )

        getSystemService(DisplayManager::class.java).registerDisplayListener(displayListener, mainHandler)
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())

        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:PixelMonitor")
            .apply { acquire() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> shutdownCompletely()
            ACTION_START -> {
                if (mediaProjection == null && !startingProjection) {
                    val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
                    val resultData = projectionIntent(intent)
                    if (resultCode == 0 || resultData == null) {
                        stopSelf()
                    } else {
                        startingProjection = true
                        serviceScope.launch {
                            prepareTapBackend()
                            withContext(Dispatchers.Main) {
                                if (mediaProjection == null) setupProjection(resultCode, resultData)
                                startingProjection = false
                            }
                        }
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private suspend fun prepareTapBackend() {
        updateBackendStatus("فحص صلاحية Root…")
        val rootReady = runCatching {
            rootController.hasRoot() && rootController.start() != null
        }.getOrDefault(false)

        if (rootReady) {
            switchableTapEngine.switchTo(rootTapEngine)
            updateBackendStatus("True Concurrent · Root")
            return
        }

        runCatching { rootController.stop() }
        if (TriggerAccessibilityService.isEnabled(this)) {
            switchableTapEngine.switchTo(legacyTapEngine)
            updateBackendStatus("Compatibility · Accessibility (قد يقطع اللمس الجاري)")
        } else {
            updateBackendStatus("لا يوجد محرك ضغط جاهز: فعّل Root أو Accessibility")
        }
    }

    private fun updateBackendStatus(text: String) {
        backendStatus = text
        mainHandler.post { backendStatusView?.text = text }
    }

    private fun projectionIntent(intent: Intent): Intent? = if (Build.VERSION.SDK_INT >= 33) {
        intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
    } else {
        @Suppress("DEPRECATION")
        intent.getParcelableExtra(EXTRA_RESULT_DATA)
    }

    private fun setupProjection(resultCode: Int, resultData: Intent) {
        val bounds = currentScreenBounds()
        screenWidth = bounds.width()
        screenHeight = bounds.height()
        densityDpi = resources.displayMetrics.densityDpi

        captureThread = HandlerThread("PixelTriggerCapture", -8).also { it.start() }
        captureHandler = Handler(requireNotNull(captureThread).looper)

        val manager = getSystemService(MediaProjectionManager::class.java)
        val projection = manager.getMediaProjection(resultCode, resultData)
        projection.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                stopSelf()
            }
        }, mainHandler)
        mediaProjection = projection

        imageReader = createImageReader(screenWidth, screenHeight)
        virtualDisplay = projection.createVirtualDisplay(
            "PixelTriggerDisplay",
            screenWidth,
            screenHeight,
            densityDpi,
            16,
            imageReader?.surface,
            null,
            captureHandler,
        )

        createOverlays()
        updateSensorStatus(SensorStatus.WAITING)
    }

    private fun createImageReader(width: Int, height: Int): ImageReader =
        ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2).also { reader ->
            reader.setOnImageAvailableListener({ source ->
                val image = source.acquireLatestImage() ?: return@setOnImageAvailableListener
                try {
                    if (!editMode) processImage(image)
                } finally {
                    image.close()
                }
            }, captureHandler)
        }

    private fun processImage(image: Image) {
        val params = sensorParams ?: return
        if (screenWidth <= 0 || screenHeight <= 0) return
        val crop = image.cropRect
        if (crop.width() <= 0 || crop.height() <= 0) return

        val now = SystemClock.elapsedRealtime()
        val screenCenterX = params.x + sensorTouchSize / 2
        val screenCenterY = params.y + sensorTouchSize / 2
        val centerX = (crop.left + (screenCenterX.toFloat() * crop.width() / screenWidth))
            .roundToInt().coerceIn(crop.left, crop.right - 1)
        val centerY = (crop.top + (screenCenterY.toFloat() * crop.height() / screenHeight))
            .roundToInt().coerceIn(crop.top, crop.bottom - 1)
        val screenRadius = sensorVisibleDiameter / 2f
        val radiusX = max(0.5f, crop.width() * screenRadius / screenWidth)
        val radiusY = max(0.5f, crop.height() * screenRadius / screenHeight)
        val sample = sampleCircularRegion(image, centerX, centerY, radiusX, radiusY) ?: return

        val event = triggerPipeline.processSample(sample, now)
        when (event) {
            is DetectionEngine.Event.Armed,
            is DetectionEngine.Event.Rearmed,
            is DetectionEngine.Event.ManualRearmed -> {
                updateSensorStatus(SensorStatus.ARMED)
                if (event is DetectionEngine.Event.ManualRearmed) {
                    toast("تم تفعيل التسليح وتجاوز التأخير لهذه المرة")
                }
            }
            is DetectionEngine.Event.ManualRearmTimedOut -> toast("لم يتم التسليح: اللون الأبيض غير موجود")
            is DetectionEngine.Event.Fired -> updateSensorStatus(SensorStatus.FIRED)
            else -> Unit
        }
    }

    private fun sampleCircularRegion(
        image: Image,
        centerX: Int,
        centerY: Int,
        radiusX: Float,
        radiusY: Float,
    ): DetectionEngine.ColorSample? {
        val crop = image.cropRect
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
                val minChannel = min(red, min(green, blue))
                val maxChannel = max(red, max(green, blue))
                val chroma = maxChannel - minChannel
                val luminance = ((54 * red) + (183 * green) + (19 * blue)) shr 8

                redTotal += red
                greenTotal += green
                blueTotal += blue
                luminanceTotal += luminance
                chromaTotal += chroma
                if (
                    luminance >= DetectionEngine.WHITE_PIXEL_LUMINANCE &&
                    minChannel >= DetectionEngine.WHITE_PIXEL_MIN_CHANNEL &&
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

    private fun createOverlays() {
        if (sensorView != null) return
        sensorVisibleDiameter = max(mmToPx(DetectionEngine.SENSOR_DIAMETER_MM), 1)
        val targetVisibleDiameter = max(mmToPx(5f), dp(12))
        val sensor = SensorOverlayView(this, sensorVisibleDiameter)
        sensorTouchSize = max(dp(48), sensor.outerDiameterPx + dp(30))
        targetTouchSize = max(dp(52), dp(24) + targetVisibleDiameter)

        sensorView = sensor
        sensorParams = overlayParams(sensorTouchSize, sensorTouchSize).apply {
            x = preferences.getInt(KEY_SENSOR_X, screenWidth / 2 - sensorTouchSize / 2)
            y = preferences.getInt(KEY_SENSOR_Y, screenHeight / 2 - sensorTouchSize / 2)
        }
        clampOverlayPosition(requireNotNull(sensorParams), sensor.outerDiameterPx)
        attachDrag(sensor, requireNotNull(sensorParams), sensor.outerDiameterPx, KEY_SENSOR_X, KEY_SENSOR_Y) {
            resetTriggerForSensorMove()
        }
        windowManager.addView(sensor, sensorParams)

        val target = TargetOverlayView(this, targetVisibleDiameter)
        targetView = target
        targetParams = overlayParams(targetTouchSize, targetTouchSize).apply {
            x = preferences.getInt(KEY_TARGET_X, screenWidth / 2 + dp(70))
            y = preferences.getInt(KEY_TARGET_Y, screenHeight / 2 - targetTouchSize / 2)
        }
        clampOverlayPosition(requireNotNull(targetParams), targetVisibleDiameter)
        attachDrag(target, requireNotNull(targetParams), targetVisibleDiameter, KEY_TARGET_X, KEY_TARGET_Y, null)
        windowManager.addView(target, targetParams)

        val menu = TextView(this).apply {
            text = "PT"
            textSize = 18f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            background = roundedBackground(Color.rgb(36, 39, 46), Color.rgb(100, 105, 118), 16f)
            setOnClickListener { toggleMenu() }
            contentDescription = "قائمة PixelTrigger"
        }
        menuButton = menu
        val menuSize = dp(58)
        menuButtonParams = overlayParams(menuSize, menuSize).apply {
            x = max(0, screenWidth - menuSize - dp(12))
            y = dp(40)
        }
        windowManager.addView(menu, menuButtonParams)

        applyCircleInteractionMode()
    }

    private fun attachDrag(
        view: View,
        params: WindowManager.LayoutParams,
        visibleDiameter: Int,
        keyX: String,
        keyY: String,
        onMoved: (() -> Unit)?,
    ) {
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var moved = false
        view.setOnTouchListener { _, event ->
            if (!editMode) return@setOnTouchListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startX = params.x
                    startY = params.y
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val nx = startX + (event.rawX - downX).roundToInt()
                    val ny = startY + (event.rawY - downY).roundToInt()
                    if (nx != params.x || ny != params.y) moved = true
                    params.x = nx
                    params.y = ny
                    clampOverlayPosition(params, visibleDiameter)
                    runCatching { windowManager.updateViewLayout(view, params) }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    preferences.edit().putInt(keyX, params.x).putInt(keyY, params.y).apply()
                    if (moved) onMoved?.invoke()
                    true
                }
                else -> false
            }
        }
    }

    private fun setEditMode(enabled: Boolean) {
        if (editMode == enabled) return
        editMode = enabled
        detector.resetForSensorMove()
        updateSensorStatus(SensorStatus.WAITING)
        applyCircleInteractionMode()
        refreshMenuPanelIfOpen()
    }

    private fun applyCircleInteractionMode() {
        listOf(sensorView to sensorParams, targetView to targetParams).forEach { (view, params) ->
            if (view == null || params == null) return@forEach
            params.flags = if (editMode) BASE_OVERLAY_FLAGS else BASE_OVERLAY_FLAGS or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            params.alpha = when {
                !circlesVisible -> 0f
                editMode -> 1f
                else -> MONITOR_WINDOW_ALPHA
            }
            view.visibility = if (circlesVisible) View.VISIBLE else View.INVISIBLE
            runCatching { windowManager.updateViewLayout(view, params) }
        }
    }

    private fun resetTriggerForSensorMove() {
        detector.resetForSensorMove()
        updateSensorStatus(SensorStatus.WAITING)
    }

    private fun updateSensorStatus(status: SensorStatus) {
        mainHandler.post { sensorView?.status = status }
    }

    private fun toggleMenu() {
        if (menuPanel == null) showMenu() else closeMenu()
    }

    private fun showMenu() {
        if (menuPanel != null) return
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = roundedBackground(Color.argb(245, 245, 246, 248), Color.rgb(180, 183, 190), 18f)
        }

        backendStatusView = TextView(this).apply {
            text = backendStatus
            textSize = 13f
            setTextColor(Color.rgb(55, 58, 66))
        }
        panel.addView(backendStatusView, matchWrap(dp(8)))

        panel.addView(menuButton(if (editMode) "إنهاء تعديل المواقع" else "تعديل مواقع الدوائر") {
            setEditMode(!editMode)
        }, matchWrap(dp(6)))

        panel.addView(menuButton(if (circlesVisible) "إخفاء الدوائر" else "إظهار الدوائر") {
            circlesVisible = !circlesVisible
            applyCircleInteractionMode()
            refreshMenuPanelIfOpen()
        }, matchWrap(dp(6)))

        panel.addView(menuButton(if (detector.whiteRearmEnabled) "إعادة التسليح بالأبيض: مفعلة" else "إعادة التسليح بالأبيض: متوقفة") {
            detector.whiteRearmEnabled = !detector.whiteRearmEnabled
            preferences.edit().putBoolean(KEY_WHITE_REARM, detector.whiteRearmEnabled).apply()
            refreshMenuPanelIfOpen()
        }, matchWrap(dp(6)))

        panel.addView(menuButton(if (detector.rearmDelayEnabled) "تأخير إعادة التسليح: مفعّل" else "تأخير إعادة التسليح: متوقف") {
            detector.rearmDelayEnabled = !detector.rearmDelayEnabled
            preferences.edit().putBoolean(KEY_REARM_DELAY_ENABLED, detector.rearmDelayEnabled).apply()
            refreshMenuPanelIfOpen()
        }, matchWrap(dp(6)))

        val delayRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val minus = Button(this).apply { text = "−"; setOnClickListener { changeRearmSeconds(-1) } }
        val delayLabel = TextView(this).apply {
            text = "${detector.rearmSeconds}s"
            gravity = Gravity.CENTER
            textSize = 16f
        }
        val plus = Button(this).apply { text = "+"; setOnClickListener { changeRearmSeconds(1) } }
        delayRow.addView(minus, LinearLayout.LayoutParams(0, dp(48), 1f))
        delayRow.addView(delayLabel, LinearLayout.LayoutParams(0, dp(48), 1f))
        delayRow.addView(plus, LinearLayout.LayoutParams(0, dp(48), 1f))
        panel.addView(delayRow, matchWrap(dp(6)))

        panel.addView(menuButton("تجاوز التأخير لهذه الدورة") {
            requestOneTimeRearmOverride()
        }, matchWrap(dp(6)))

        panel.addView(menuButton("إيقاف PixelTrigger", danger = true) { shutdownCompletely() }, matchWrap(0))

        menuPanel = panel
        val width = min(dp(320), max(dp(220), screenWidth - dp(24)))
        menuPanelParams = overlayParams(width, WindowManager.LayoutParams.WRAP_CONTENT).apply {
            x = max(0, screenWidth - width - dp(12))
            y = dp(106)
        }
        windowManager.addView(panel, menuPanelParams)
    }

    private fun refreshMenuPanelIfOpen() {
        if (menuPanel == null) return
        closeMenu()
        showMenu()
    }

    private fun closeMenu() {
        menuPanel?.let { runCatching { windowManager.removeView(it) } }
        menuPanel = null
        menuPanelParams = null
        backendStatusView = null
    }

    private fun requestOneTimeRearmOverride() {
        if (!detector.whiteRearmEnabled || !detector.rearmDelayEnabled) {
            toast("فعّل تأخير إعادة التسليح أولًا")
            return
        }
        closeMenu()
        val handler = captureHandler
        if (handler == null) {
            toast("المراقبة غير جاهزة")
            return
        }
        handler.post {
            val accepted = detector.requestOneTimeRearmOverride(SystemClock.elapsedRealtime())
            if (!accepted) toast("لا يوجد تأخير جارٍ لتجاوزه")
        }
    }

    private fun menuButton(textValue: String, danger: Boolean = false, action: () -> Unit): Button = Button(this).apply {
        text = textValue
        isAllCaps = false
        textSize = 15f
        setTextColor(if (danger) Color.rgb(120, 27, 38) else Color.rgb(28, 28, 34))
        setOnClickListener { action() }
    }

    private fun matchWrap(bottomMargin: Int): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            this.bottomMargin = bottomMargin
        }

    private fun changeRearmSeconds(delta: Int) {
        detector.rearmSeconds = (detector.rearmSeconds + delta).coerceIn(5, 60)
        preferences.edit().putInt(KEY_REARM_SECONDS, detector.rearmSeconds).apply()
        refreshMenuPanelIfOpen()
    }

    private fun overlayParams(width: Int, height: Int): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            width,
            height,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            BASE_OVERLAY_FLAGS,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.START }

    private fun clampOverlayPosition(params: WindowManager.LayoutParams, visibleDiameter: Int? = null) {
        val width = max(params.width, 1)
        val height = max(params.height, 1)
        val horizontalPadding = visibleDiameter?.let { max(0, (width - min(it, width)) / 2) } ?: 0
        val verticalPadding = visibleDiameter?.let { max(0, (height - min(it, height)) / 2) } ?: 0
        val minX = -horizontalPadding
        val minY = -verticalPadding
        val maxX = max(minX, screenWidth - width + horizontalPadding)
        val maxY = max(minY, screenHeight - height + verticalPadding)
        params.x = params.x.coerceIn(minX, maxX)
        params.y = params.y.coerceIn(minY, maxY)
    }

    private fun refreshDisplayGeometry() {
        val bounds = currentScreenBounds()
        val newWidth = bounds.width()
        val newHeight = bounds.height()
        val newDensity = resources.displayMetrics.densityDpi
        if (newWidth <= 0 || newHeight <= 0) return
        if (newWidth == screenWidth && newHeight == screenHeight && newDensity == densityDpi) return

        val oldWidth = max(screenWidth, 1)
        val oldHeight = max(screenHeight, 1)
        val sensorCenter = normalizedCenter(sensorParams, sensorTouchSize, oldWidth, oldHeight)
        val targetCenter = normalizedCenter(targetParams, targetTouchSize, oldWidth, oldHeight)
        val menuSize = menuButtonParams?.width ?: dp(58)
        val menuCenter = normalizedCenter(menuButtonParams, menuSize, oldWidth, oldHeight)

        closeMenu()
        screenWidth = newWidth
        screenHeight = newHeight
        densityDpi = newDensity

        captureHandler?.post {
            val replacement = createImageReader(newWidth, newHeight)
            val old = imageReader
            imageReader = replacement
            virtualDisplay?.resize(newWidth, newHeight, newDensity)
            virtualDisplay?.setSurface(replacement.surface)
            old?.setOnImageAvailableListener(null, null)
            old?.close()
        }

        repositionOverlay(sensorView, sensorParams, sensorCenter, sensorView?.outerDiameterPx)
        repositionOverlay(targetView, targetParams, targetCenter, targetView?.visibleDiameterPx)
        repositionOverlay(menuButton, menuButtonParams, menuCenter, null)
        resetTriggerForSensorMove()
    }

    private fun normalizedCenter(
        params: WindowManager.LayoutParams?,
        size: Int,
        width: Int,
        height: Int,
    ): Pair<Float, Float>? {
        params ?: return null
        return Pair(
            ((params.x + size / 2f) / width).coerceIn(0f, 1f),
            ((params.y + size / 2f) / height).coerceIn(0f, 1f),
        )
    }

    private fun repositionOverlay(
        view: View?,
        params: WindowManager.LayoutParams?,
        center: Pair<Float, Float>?,
        visibleDiameter: Int?,
    ) {
        if (view == null || params == null || center == null) return
        val width = max(if (view.width > 0) view.width else params.width, 1)
        val height = max(if (view.height > 0) view.height else params.height, 1)
        params.x = (center.first * screenWidth - width / 2f).roundToInt()
        params.y = (center.second * screenHeight - height / 2f).roundToInt()
        clampOverlayPosition(params, visibleDiameter)
        runCatching { windowManager.updateViewLayout(view, params) }
    }

    private fun currentScreenBounds(): Rect {
        return if (Build.VERSION.SDK_INT >= 30) {
            windowManager.currentWindowMetrics.bounds
        } else {
            @Suppress("DEPRECATION")
            val point = android.graphics.Point().also { windowManager.defaultDisplay.getRealSize(it) }
            Rect(0, 0, point.x, point.y)
        }
    }

    private fun currentRotation(): Int {
        @Suppress("DEPRECATION")
        return windowManager.defaultDisplay.rotation
    }

    private fun mmToPx(mm: Float): Int {
        val metrics = resources.displayMetrics
        val fallbackDpi = metrics.densityDpi.toFloat()
        val xDpi = metrics.xdpi.takeIf { it.isFinite() && it in 100f..1000f } ?: fallbackDpi
        val yDpi = metrics.ydpi.takeIf { it.isFinite() && it in 100f..1000f } ?: fallbackDpi
        val physicalDpi = (xDpi + yDpi) / 2f
        return max(((mm / 25.4f) * physicalDpi).roundToInt(), 1)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    private fun roundedBackground(fill: Int, stroke: Int, radiusDp: Float): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(fill)
            cornerRadius = dp(radiusDp.roundToInt()).toFloat()
            setStroke(dp(1), stroke)
        }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "مراقبة PixelTrigger", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_media_play)
        .setContentTitle("PixelTrigger")
        .setContentText("مراقبة البكسل قيد التشغيل")
        .setOngoing(true)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .build()

    fun shutdownCompletely() {
        detector.resetForSensorMove()
        closeMenu()
        listOfNotNull(sensorView, targetView, menuButton).forEach { view ->
            runCatching { windowManager.removeViewImmediate(view) }
        }
        sensorView = null
        targetView = null
        menuButton = null
        sensorParams = null
        targetParams = null
        menuButtonParams = null
        circlesVisible = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(refreshDisplayRunnable)
        runCatching { getSystemService(DisplayManager::class.java).unregisterDisplayListener(displayListener) }
        closeMenu()
        listOfNotNull(sensorView, targetView, menuButton).forEach { view -> runCatching { windowManager.removeView(view) } }

        imageReader?.setOnImageAvailableListener(null, null)
        imageReader?.close()
        imageReader = null
        virtualDisplay?.release()
        virtualDisplay = null
        runCatching { mediaProjection?.stop() }
        mediaProjection = null
        captureThread?.quitSafely()
        captureThread = null
        captureHandler = null

        runBlocking {
            withTimeoutOrNull(700L) { runCatching { rootController.stop() } }
        }
        serviceScope.cancel()
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun toast(message: String) {
        mainHandler.post { Toast.makeText(this, message, Toast.LENGTH_SHORT).show() }
    }

    companion object {
        const val ACTION_START = "com.pixeltrigger.app.action.START"
        const val ACTION_STOP = "com.pixeltrigger.app.action.STOP"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"

        private const val PREFS_NAME = "pixel_trigger_settings"
        private const val KEY_SENSOR_X = "sensor_x"
        private const val KEY_SENSOR_Y = "sensor_y"
        private const val KEY_TARGET_X = "target_x"
        private const val KEY_TARGET_Y = "target_y"
        private const val KEY_WHITE_REARM = "white_rearm"
        private const val KEY_REARM_DELAY_ENABLED = "rearm_delay_enabled"
        private const val KEY_REARM_SECONDS = "rearm_seconds"
        private const val KEY_TIMED_REARM_LEGACY = "timed_rearm"

        private const val CHANNEL_ID = "pixel_trigger_monitor"
        private const val NOTIFICATION_ID = 2207
        private const val BASE_OVERLAY_FLAGS = 776
        private const val MONITOR_WINDOW_ALPHA = 0.55f
    }
}

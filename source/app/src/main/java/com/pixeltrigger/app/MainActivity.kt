package com.pixeltrigger.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {
    private lateinit var statusView: TextView

    private val projectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            val serviceIntent = Intent(this, ScreenCaptureService::class.java)
                .setAction(ScreenCaptureService.ACTION_START)
                .putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, result.resultCode)
                .putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, data)
            ContextCompat.startForegroundService(this, serviceIntent)
            statusView.text = "تم إرسال إذن التقاط الشاشة. PixelTrigger يبدأ الآن."
        } else {
            statusView.text = "لم يتم منح إذن التقاط الشاشة."
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) beginStartFlow() else refreshStatus()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "PixelTrigger"
        setContentView(buildContent())
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun buildContent(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(22), dp(28), dp(22), dp(28))
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }

        root.addView(TextView(this).apply {
            text = "PixelTrigger"
            textSize = 30f
            setTextColor(Color.rgb(30, 32, 37))
            gravity = Gravity.CENTER
        }, matchWrap(dp(8)))

        root.addView(TextView(this).apply {
            text = "v3 · محرك ضغط متزامن"
            textSize = 16f
            setTextColor(Color.rgb(80, 83, 92))
            gravity = Gravity.CENTER
        }, matchWrap(dp(20)))

        statusView = TextView(this).apply {
            textSize = 15f
            setTextColor(Color.rgb(45, 48, 56))
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        root.addView(statusView, matchWrap(dp(16)))

        root.addView(actionButton("تشغيل PixelTrigger") { beginStartFlow() }, matchWrap(dp(10)))
        root.addView(actionButton("إيقاف PixelTrigger") {
            startService(
                Intent(this, ScreenCaptureService::class.java)
                    .setAction(ScreenCaptureService.ACTION_STOP),
            )
        }, matchWrap(dp(10)))

        root.addView(actionButton("إذن الظهور فوق التطبيقات") { openOverlayPermission() }, matchWrap(dp(8)))
        root.addView(actionButton("استثناء تحسين البطارية") { requestBatteryExemption() }, matchWrap(dp(8)))
        root.addView(actionButton("Accessibility · وضع التوافق فقط") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }, matchWrap(dp(16)))

        root.addView(TextView(this).apply {
            text = "الوضع الاحترافي يستخدم Root لتشغيل Unified Touch Proxy. إذا لم يتوفر Root، يمكن للتطبيق الرجوع إلى Accessibility، لكن وضع التوافق قد يقطع اللمس الجاري كما في v2.12."
            textSize = 13f
            setTextColor(Color.rgb(90, 93, 101))
        }, matchWrap(0))

        return ScrollView(this).apply { addView(root) }
    }

    private fun beginStartFlow() {
        if (!Settings.canDrawOverlays(this)) {
            openOverlayPermission()
            return
        }
        if (!isIgnoringBatteryOptimizations()) {
            requestBatteryExemption()
            return
        }
        if (
            Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }

        val manager = getSystemService(android.media.projection.MediaProjectionManager::class.java)
        projectionLauncher.launch(manager.createScreenCaptureIntent())
    }

    private fun openOverlayPermission() {
        startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName"),
            ),
        )
    }

    private fun requestBatteryExemption() {
        if (isIgnoringBatteryOptimizations()) return
        runCatching {
            startActivity(
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:$packageName"),
                ),
            )
        }.onFailure {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    private fun isIgnoringBatteryOptimizations(): Boolean =
        getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)

    private fun refreshStatus() {
        val overlay = Settings.canDrawOverlays(this)
        val battery = isIgnoringBatteryOptimizations()
        val accessibility = TriggerAccessibilityService.isEnabled(this)
        statusView.text = buildString {
            append(if (overlay) "✓" else "✗").append(" الظهور فوق التطبيقات\n")
            append(if (battery) "✓" else "✗").append(" استثناء البطارية\n")
            append(if (accessibility) "✓" else "○").append(" Accessibility fallback\n")
            append("Root: يُفحص عند التشغيل ويُفضّل تلقائيًا إذا كان متاحًا")
        }
    }

    private fun actionButton(label: String, action: () -> Unit): Button = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 16f
        setOnClickListener { action() }
    }

    private fun matchWrap(bottomMargin: Int): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { this.bottomMargin = bottomMargin }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}

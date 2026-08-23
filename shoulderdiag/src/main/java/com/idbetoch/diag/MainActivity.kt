package com.idbetoch.diag

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import rikka.shizuku.Shizuku

class MainActivity : AppCompatActivity() {
    private enum class UiState { READY, CONNECTING, SCANNING, DONE }

    private lateinit var statusCard: LinearLayout
    private lateinit var statusTitle: TextView
    private lateinit var statusBody: TextView
    private lateinit var resultView: TextView
    private lateinit var resultScroll: ScrollView
    private lateinit var actionsRow: LinearLayout
    private lateinit var copyButton: Button
    private lateinit var deleteButton: Button

    private val uiHandler = Handler(Looper.getMainLooper())
    @Volatile private var remote: IShoulderDiagService? = null
    private var pendingStart = false
    private var scanStartedAt = 0L
    private var uiState = UiState.READY

    private val permissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode != REQ_SHIZUKU) return@OnRequestPermissionResultListener
        if (grantResult == PackageManager.PERMISSION_GRANTED && pendingStart) {
            ensureBoundAndStart()
        } else if (grantResult != PackageManager.PERMISSION_GRANTED) {
            pendingStart = false
            showReady("إذن Shizuku مطلوب لقراءة مسار زر R بدقة.")
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            remote = IShoulderDiagService.Stub.asInterface(service)
            if (pendingStart) beginRemoteScan()
            else showReady("جاهز. اضغط البطاقة لبدء الفحص.")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            remote = null
            if (uiState == UiState.SCANNING) {
                showReady("انقطع اتصال Shizuku UserService. أعد المحاولة.")
            }
        }
    }

    private val userServiceArgs by lazy {
        Shizuku.UserServiceArgs(
            ComponentName(packageName, ShoulderDiagUserService::class.java.name)
        )
            .processNameSuffix("id_be_toch_diag")
            .daemon(true)
            .tag("id-be-toch-diag-v1")
            .version(1)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Shizuku.addRequestPermissionResultListener(permissionListener)
        setContentView(buildUi())
        showReady("الفحص يعمل لمدة 30 ثانية. أبقِ الهاتف أفقيًا واستخدم زر R أثناء الفحص.")
    }

    override fun onDestroy() {
        uiHandler.removeCallbacksAndMessages(null)
        Shizuku.removeRequestPermissionResultListener(permissionListener)
        runCatching { Shizuku.unbindUserService(userServiceArgs, connection, false) }
        super.onDestroy()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (uiState == UiState.SCANNING) {
            val device = event.device
            val actionName = when (event.action) {
                KeyEvent.ACTION_DOWN -> "DOWN"
                KeyEvent.ACTION_UP -> "UP"
                else -> event.action.toString()
            }
            val line = buildString {
                append("action=$actionName")
                append(" keyCode=${event.keyCode}")
                append(" keyName=${KeyEvent.keyCodeToString(event.keyCode)}")
                append(" scanCode=${event.scanCode}")
                append(" deviceId=${event.deviceId}")
                append(" source=0x${event.source.toString(16)}")
                append(" flags=0x${event.flags.toString(16)}")
                append(" repeat=${event.repeatCount}")
                append(" meta=0x${event.metaState.toString(16)}")
                append(" downTime=${event.downTime}")
                append(" eventTime=${event.eventTime}")
                if (device != null) {
                    append(" deviceName=${device.name}")
                    append(" descriptor=${device.descriptor}")
                    append(" vendorId=${device.vendorId}")
                    append(" productId=${device.productId}")
                    append(" deviceSources=0x${device.sources.toString(16)}")
                }
            }
            runCatching { remote?.appendAppKeyEvent(line) }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun buildUi(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(18), dp(18), dp(18), dp(18))
            setBackgroundColor(Color.rgb(14, 15, 20))
        }

        val left = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(8), dp(14), dp(8))
        }
        root.addView(left, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 0.42f))

        left.addView(TextView(this).apply {
            text = "ID BE TOCH"
            textSize = 26f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(52)))

        left.addView(TextView(this).apply {
            text = "فاحص زر الكتف R — REDMAGIC / Nubia"
            textSize = 15f
            setTextColor(Color.rgb(190, 195, 210))
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(44)))

        statusCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(22), dp(24), dp(22))
            background = rounded(Color.rgb(31, 34, 45), 22f)
            isClickable = true
            isFocusable = true
            setOnClickListener { if (uiState == UiState.READY) startRequested() }
        }
        left.addView(statusCard, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f).apply {
            topMargin = dp(14)
            bottomMargin = dp(14)
        })

        statusTitle = TextView(this).apply {
            textSize = 24f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        statusCard.addView(statusTitle, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(58)))

        statusBody = TextView(this).apply {
            textSize = 16f
            setTextColor(Color.rgb(205, 210, 225))
            gravity = Gravity.CENTER
        }
        statusCard.addView(statusBody, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        actionsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            visibility = View.GONE
        }
        left.addView(actionsRow, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(58)))

        copyButton = Button(this).apply {
            text = "نسخ النتيجة"
            textSize = 16f
            setOnClickListener { copyResult() }
        }
        actionsRow.addView(copyButton, LinearLayout.LayoutParams(0, dp(54), 1f).apply { marginEnd = dp(8) })

        deleteButton = Button(this).apply {
            text = "حذف النتيجة"
            textSize = 16f
            setOnClickListener { deleteResult() }
        }
        actionsRow.addView(deleteButton, LinearLayout.LayoutParams(0, dp(54), 1f).apply { marginStart = dp(8) })

        val right = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(8), dp(8), dp(8))
        }
        root.addView(right, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 0.58f))

        right.addView(TextView(this).apply {
            text = "النتيجة التقنية"
            textSize = 19f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(44)))

        resultView = TextView(this).apply {
            textSize = 11f
            typeface = Typeface.MONOSPACE
            setTextColor(Color.rgb(216, 220, 230))
            setTextIsSelectable(true)
            setPadding(dp(14), dp(14), dp(14), dp(14))
            text = "لم يبدأ الفحص بعد."
        }
        resultScroll = ScrollView(this).apply {
            background = rounded(Color.rgb(22, 24, 31), 16f)
            addView(resultView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        }
        right.addView(resultScroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        return root
    }

    private fun startRequested() {
        pendingStart = true
        if (!Shizuku.pingBinder()) {
            pendingStart = false
            showReady("Shizuku غير مشغّل. شغّله عبر Wireless debugging/ADB ثم اضغط بدء الفحص.")
            Toast.makeText(this, "شغّل Shizuku أولًا", Toast.LENGTH_LONG).show()
            return
        }
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            uiState = UiState.CONNECTING
            setCard("منح الإذن", "وافق على إذن Shizuku، وسيبدأ الفحص تلقائيًا بعدها.")
            Shizuku.requestPermission(REQ_SHIZUKU)
            return
        }
        ensureBoundAndStart()
    }

    private fun ensureBoundAndStart() {
        if (!Shizuku.pingBinder()) {
            pendingStart = false
            showReady("Shizuku غير متصل.")
            return
        }
        val service = remote
        if (service != null) {
            beginRemoteScan()
            return
        }
        uiState = UiState.CONNECTING
        setCard("جاري الاتصال…", "يتم الآن تشغيل فاحص Shizuku. سيبدأ الفحص تلقائيًا.")
        runCatching { Shizuku.bindUserService(userServiceArgs, connection) }
            .onFailure {
                pendingStart = false
                showReady("فشل الاتصال بـ UserService: ${it.message ?: it.javaClass.simpleName}")
            }
    }

    private fun beginRemoteScan() {
        val service = remote ?: return
        pendingStart = false
        val uid = runCatching { service.backendUid }.getOrDefault(-1)
        runCatching { service.startScan(SCAN_SECONDS) }
            .onFailure {
                showReady("تعذر بدء الفحص: ${it.message ?: it.javaClass.simpleName}")
                return
            }
        uiState = UiState.SCANNING
        scanStartedAt = System.currentTimeMillis()
        actionsRow.visibility = View.GONE
        resultView.text = "بدأ الفحص…\nbackendUid=$uid\n\nاستخدم زر R الآن."
        tickScanUi()
    }

    private fun tickScanUi() {
        if (uiState != UiState.SCANNING) return
        val elapsed = ((System.currentTimeMillis() - scanStartedAt) / 1000L).toInt()
        val remaining = (SCAN_SECONDS - elapsed).coerceAtLeast(0)
        setCard(
            "الفحص جارٍ — $remaining ثانية",
            "استخدم زر R الآن. اضغط R واتركه عدة مرات خلال مدة الفحص.\nلا تغلق التطبيق وحافظ على الوضع الأفقي."
        )

        val service = remote
        if (service != null) {
            runCatching { service.result }.getOrNull()?.let { text ->
                if (text.isNotBlank()) {
                    resultView.text = text
                    resultScroll.post { resultScroll.fullScroll(View.FOCUS_DOWN) }
                }
            }
            val stillRunning = runCatching { service.isRunning }.getOrDefault(true)
            if (!stillRunning && elapsed >= 1) {
                finishScan()
                return
            }
        }
        uiHandler.postDelayed({ tickScanUi() }, 500)
    }

    private fun finishScan() {
        uiState = UiState.DONE
        val text = runCatching { remote?.result.orEmpty() }.getOrDefault(resultView.text.toString())
        resultView.text = text.ifBlank { "اكتمل الفحص لكن لم تصل نتيجة من UserService." }
        setCard("اكتمل الفحص", "النتيجة جاهزة. استخدم زر «نسخ النتيجة» لإرسالها، أو احذفها لبدء فحص جديد.")
        statusCard.isClickable = false
        actionsRow.visibility = View.VISIBLE
    }

    private fun copyResult() {
        val text = resultView.text.toString()
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("ID BE TOCH result", text))
        Toast.makeText(this, "تم نسخ النتيجة", Toast.LENGTH_SHORT).show()
    }

    private fun deleteResult() {
        runCatching { remote?.clearResult() }
        resultView.text = "تم حذف النتيجة. اضغط بدء الفحص لإجراء فحص جديد."
        actionsRow.visibility = View.GONE
        showReady("الفحص يعمل لمدة 30 ثانية. استخدم زر R أثناء الفحص.")
    }

    private fun showReady(body: String) {
        uiState = UiState.READY
        statusCard.isClickable = true
        setCard("بدء الفحص", body)
    }

    private fun setCard(title: String, body: String) {
        statusTitle.text = title
        statusBody.text = body
    }

    private fun rounded(color: Int, radiusDp: Float): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(color)
        cornerRadius = dp(radiusDp.toInt()).toFloat()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    companion object {
        private const val REQ_SHIZUKU = 9104
        private const val SCAN_SECONDS = 30
    }
}

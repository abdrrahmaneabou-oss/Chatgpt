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

    private lateinit var shizukuCard: LinearLayout
    private lateinit var shizukuTitle: TextView
    private lateinit var shizukuBody: TextView
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
    private var userRequestedConnect = false
    private var scanStartedAt = 0L
    private var uiState = UiState.READY

    private fun uiReady(): Boolean = ::shizukuCard.isInitialized && ::statusCard.isInitialized

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        if (!uiReady()) return@OnBinderReceivedListener
        refreshShizukuState(fromBinderCallback = true)
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        remote = null
        if (!uiReady()) return@OnBinderDeadListener
        showShizukuDisconnected("انقطع اتصال Shizuku. شغّله من تطبيق Shizuku ثم عد إلى هنا.")
        if (uiState == UiState.SCANNING) {
            pendingStart = false
            showReady("انقطع اتصال Shizuku أثناء الفحص. أعد الاتصال ثم ابدأ فحصًا جديدًا.")
        }
    }

    private val permissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode != REQ_SHIZUKU || !uiReady()) return@OnRequestPermissionResultListener
        if (grantResult == PackageManager.PERMISSION_GRANTED) {
            showShizukuConnecting("تم منح الإذن. جاري ربط خدمة الفحص بـ Shizuku…")
            bindDiagnosticService()
        } else {
            userRequestedConnect = false
            pendingStart = false
            showShizukuDisconnected("تم رفض إذن Shizuku. اضغط «اتصل بـ Shizuku» للمحاولة مرة أخرى.")
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            remote = IShoulderDiagService.Stub.asInterface(service)
            if (!uiReady()) return
            userRequestedConnect = false
            showShizukuConnected()
            if (pendingStart) beginRemoteScan()
            else if (uiState != UiState.SCANNING && uiState != UiState.DONE) {
                showReady("Shizuku متصل وجاهز. اضغط «بدء الفحص» عندما تكون مستعدًا.")
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            remote = null
            if (!uiReady()) return
            showShizukuDisconnected("انقطع اتصال خدمة الفحص بـ Shizuku. اضغط البطاقة لإعادة الربط.")
            if (uiState == UiState.SCANNING) {
                pendingStart = false
                showReady("انقطع اتصال Shizuku UserService. أعد الاتصال ثم ابدأ الفحص من جديد.")
            }
        }
    }

    private val userServiceArgs by lazy {
        Shizuku.UserServiceArgs(
            ComponentName(packageName, ShoulderDiagUserService::class.java.name)
        )
            .processNameSuffix("id_be_toch_diag")
            .daemon(true)
            .tag("id-be-toch-diag-v2")
            .version(2)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Build the UI first. addBinderReceivedListenerSticky may invoke immediately
        // when Shizuku is already running, so registering it before these lateinit
        // views exist causes an instant startup crash.
        setContentView(buildUi())
        showReady("الفحص يعمل لمدة 30 ثانية. أبقِ الهاتف أفقيًا واستخدم زر R أثناء الفحص.")

        Shizuku.addBinderDeadListener(binderDeadListener)
        Shizuku.addRequestPermissionResultListener(permissionListener)
        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)

        refreshShizukuState(fromBinderCallback = false)
    }

    override fun onResume() {
        super.onResume()
        if (uiReady()) refreshShizukuState(fromBinderCallback = false)
    }

    override fun onDestroy() {
        uiHandler.removeCallbacksAndMessages(null)
        Shizuku.removeBinderReceivedListener(binderReceivedListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
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
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(46)))

        left.addView(TextView(this).apply {
            text = "فاحص زر الكتف R — REDMAGIC / Nubia"
            textSize = 15f
            setTextColor(Color.rgb(190, 195, 210))
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(38)))

        shizukuCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(18), dp(12), dp(18), dp(12))
            background = rounded(Color.rgb(40, 45, 61), 18f)
            isClickable = true
            isFocusable = true
            setOnClickListener { connectRequested() }
        }
        left.addView(shizukuCard, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(92)).apply {
            topMargin = dp(8)
            bottomMargin = dp(8)
        })

        shizukuTitle = TextView(this).apply {
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        shizukuCard.addView(shizukuTitle, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(36)))

        shizukuBody = TextView(this).apply {
            textSize = 13f
            setTextColor(Color.rgb(205, 210, 225))
            gravity = Gravity.CENTER
        }
        shizukuCard.addView(shizukuBody, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        statusCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(18), dp(24), dp(18))
            background = rounded(Color.rgb(31, 34, 45), 22f)
            isClickable = true
            isFocusable = true
            setOnClickListener { if (uiState == UiState.READY) startRequested() }
        }
        left.addView(statusCard, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f).apply {
            topMargin = dp(6)
            bottomMargin = dp(10)
        })

        statusTitle = TextView(this).apply {
            textSize = 24f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        statusCard.addView(statusTitle, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(52)))

        statusBody = TextView(this).apply {
            textSize = 15f
            setTextColor(Color.rgb(205, 210, 225))
            gravity = Gravity.CENTER
        }
        statusCard.addView(statusBody, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        actionsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            visibility = View.GONE
        }
        left.addView(actionsRow, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(54)))

        copyButton = Button(this).apply {
            text = "نسخ النتيجة"
            textSize = 16f
            setOnClickListener { copyResult() }
        }
        actionsRow.addView(copyButton, LinearLayout.LayoutParams(0, dp(50), 1f).apply { marginEnd = dp(8) })

        deleteButton = Button(this).apply {
            text = "حذف النتيجة"
            textSize = 16f
            setOnClickListener { deleteResult() }
        }
        actionsRow.addView(deleteButton, LinearLayout.LayoutParams(0, dp(50), 1f).apply { marginStart = dp(8) })

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

    private fun connectRequested() {
        userRequestedConnect = true
        refreshShizukuState(fromBinderCallback = false, forcePermissionPrompt = true)
    }

    private fun refreshShizukuState(fromBinderCallback: Boolean, forcePermissionPrompt: Boolean = false) {
        if (!uiReady()) return
        val alive = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        if (!alive) {
            showShizukuDisconnected(
                if (userRequestedConnect)
                    "Shizuku لم يرسل Binder بعد. شغّله من تطبيق Shizuku ثم ارجع؛ سيتم اكتشافه تلقائيًا دون إعادة فتح التطبيق."
                else
                    "اضغط هنا للاتصال. إذا كان Shizuku متوقفًا شغّله ثم عد إلى التطبيق."
            )
            return
        }

        val permission = runCatching { Shizuku.checkSelfPermission() }.getOrDefault(PackageManager.PERMISSION_DENIED)
        if (permission != PackageManager.PERMISSION_GRANTED) {
            showShizukuConnecting("Shizuku يعمل. اضغط البطاقة لإظهار نافذة الإذن الرسمية من Shizuku.")
            if (forcePermissionPrompt || userRequestedConnect || fromBinderCallback) {
                runCatching { Shizuku.requestPermission(REQ_SHIZUKU) }
                    .onFailure {
                        showShizukuDisconnected("تعذر طلب إذن Shizuku: ${it.message ?: it.javaClass.simpleName}")
                    }
            }
            return
        }

        if (remote != null) {
            userRequestedConnect = false
            showShizukuConnected()
            return
        }

        showShizukuConnecting("Shizuku يعمل والإذن ممنوح. جاري ربط UserService…")
        bindDiagnosticService()
    }

    private fun bindDiagnosticService() {
        if (!runCatching { Shizuku.pingBinder() }.getOrDefault(false)) {
            showShizukuDisconnected("Shizuku غير متصل حاليًا.")
            return
        }
        if (runCatching { Shizuku.checkSelfPermission() }.getOrDefault(PackageManager.PERMISSION_DENIED) != PackageManager.PERMISSION_GRANTED) {
            showShizukuConnecting("يلزم إذن Shizuku أولًا. اضغط البطاقة لإظهار نافذة الإذن.")
            return
        }
        runCatching { Shizuku.bindUserService(userServiceArgs, connection) }
            .onFailure {
                remote = null
                showShizukuDisconnected("فشل ربط UserService: ${it.message ?: it.javaClass.simpleName}. اضغط البطاقة لإعادة المحاولة.")
            }
    }

    private fun showShizukuConnected() {
        shizukuTitle.text = "Shizuku متصل ✓"
        shizukuBody.text = "Binder متاح، الإذن ممنوح، وخدمة الفحص مرتبطة."
        shizukuCard.background = rounded(Color.rgb(29, 78, 58), 18f)
    }

    private fun showShizukuConnecting(body: String) {
        shizukuTitle.text = "الاتصال بـ Shizuku"
        shizukuBody.text = body
        shizukuCard.background = rounded(Color.rgb(74, 63, 31), 18f)
    }

    private fun showShizukuDisconnected(body: String) {
        shizukuTitle.text = "اتصل بـ Shizuku"
        shizukuBody.text = body
        shizukuCard.background = rounded(Color.rgb(68, 39, 45), 18f)
    }

    private fun startRequested() {
        pendingStart = true
        if (remote != null) {
            beginRemoteScan()
            return
        }
        uiState = UiState.CONNECTING
        setCard("انتظار Shizuku…", "سيبدأ الفحص تلقائيًا فور اكتمال الاتصال بـ Shizuku.")
        userRequestedConnect = true
        refreshShizukuState(fromBinderCallback = false, forcePermissionPrompt = true)
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
        refreshShizukuState(fromBinderCallback = false)
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

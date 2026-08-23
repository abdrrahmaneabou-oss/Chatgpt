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
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import rikka.shizuku.Shizuku

class MainActivity : AppCompatActivity() {
    private lateinit var shizukuCard: LinearLayout
    private lateinit var shizukuTitle: TextView
    private lateinit var shizukuBody: TextView
    private lateinit var sessionTitle: TextView
    private lateinit var sessionBody: TextView
    private lateinit var commandEditor: EditText
    private lateinit var prepareEditor: EditText
    private lateinit var captureEditor: EditText
    private lateinit var resultView: TextView
    private lateinit var resultScroll: ScrollView

    private val ui = Handler(Looper.getMainLooper())
    @Volatile private var remote: IShoulderDiagService? = null
    private var requestedConnect = false

    private fun uiReady() = ::shizukuCard.isInitialized

    private val binderReceived = Shizuku.OnBinderReceivedListener {
        if (uiReady()) refreshShizuku(true)
    }

    private val binderDead = Shizuku.OnBinderDeadListener {
        remote = null
        if (uiReady()) showShizuku(false, "انقطع Binder من Shizuku")
    }

    private val permissionResult = Shizuku.OnRequestPermissionResultListener { code, result ->
        if (code != REQ_SHIZUKU || !uiReady()) return@OnRequestPermissionResultListener
        if (result == PackageManager.PERMISSION_GRANTED) bindService() else showShizuku(false, "تم رفض إذن Shizuku")
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            remote = IShoulderDiagService.Stub.asInterface(service)
            requestedConnect = false
            showShizuku(true, "UID=${runCatching { remote?.backendUid }.getOrNull() ?: "?"} — UserService جاهز")
            poll()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            remote = null
            showShizuku(false, "UserService انفصل — اضغط للاتصال من جديد")
        }
    }

    private val serviceArgs by lazy {
        Shizuku.UserServiceArgs(ComponentName(packageName, ShoulderDiagUserService::class.java.name))
            .processNameSuffix("id_be_toch_lab")
            .daemon(true)
            .tag("id-be-toch-nubia-input-lab-v3")
            .version(3)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildUi())

        Shizuku.addBinderDeadListener(binderDead)
        Shizuku.addRequestPermissionResultListener(permissionResult)
        Shizuku.addBinderReceivedListenerSticky(binderReceived)

        refreshShizuku(false)
        poll()
    }

    override fun onResume() {
        super.onResume()
        if (uiReady()) refreshShizuku(false)
    }

    override fun onDestroy() {
        ui.removeCallbacksAndMessages(null)
        Shizuku.removeBinderReceivedListener(binderReceived)
        Shizuku.removeBinderDeadListener(binderDead)
        Shizuku.removeRequestPermissionResultListener(permissionResult)
        super.onDestroy()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val service = remote
        if (service != null && runCatching { service.isBusy }.getOrDefault(false)) {
            val action = when (event.action) {
                KeyEvent.ACTION_DOWN -> "DOWN"
                KeyEvent.ACTION_UP -> "UP"
                else -> event.action.toString()
            }
            val d = event.device
            val line = buildString {
                append("$action keyCode=${event.keyCode} scanCode=${event.scanCode} deviceId=${event.deviceId}")
                append(" source=0x${event.source.toString(16)} flags=0x${event.flags.toString(16)}")
                if (d != null) append(" device=${d.name}")
            }
            runCatching { service.appendAppKeyEvent(line) }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun buildUi(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            setBackgroundColor(Color.rgb(12, 13, 17))
        }

        val leftScroll = ScrollView(this).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
        }
        root.addView(leftScroll, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 0.43f))

        val left = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(6), dp(4), dp(10), dp(12))
        }
        leftScroll.addView(left)

        left.addView(TextView(this).apply {
            text = "NUBIA INPUT LAB"
            textSize = 24f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(42)))

        left.addView(TextView(this).apply {
            text = "هدف الأداة: الوصول إلى R/L الفيزيائي وبناء النصف الأيسر لـ PixelTrigger"
            textSize = 12f
            setTextColor(Color.rgb(185, 190, 205))
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(38)))

        shizukuCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(14), dp(8), dp(14), dp(8))
            background = rounded(Color.rgb(67, 39, 45), 16)
            isClickable = true
            setOnClickListener { requestedConnect = true; refreshShizuku(false, true) }
        }
        left.addView(shizukuCard, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(76)).apply { topMargin = dp(6) })

        shizukuTitle = TextView(this).apply {
            text = "اتصل بـ Shizuku"
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        shizukuCard.addView(shizukuTitle, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(30)))

        shizukuBody = TextView(this).apply {
            textSize = 12f
            setTextColor(Color.rgb(215, 218, 228))
            gravity = Gravity.CENTER
        }
        shizukuCard.addView(shizukuBody, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        val firstCapture = Button(this).apply {
            text = "الفحص الأول الحاسم — R + L"
            textSize = 15f
            setOnClickListener { startFirstCapture() }
        }
        left.addView(firstCapture, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(50)).apply { topMargin = dp(8) })

        left.addView(TextView(this).apply {
            text = "يعطيك 3 ثوانٍ للاستعداد، ثم يلتقط R وL وNubia GameKey لمدة 15 ثانية فقط دون dumpsys ضخم."
            textSize = 11f
            setTextColor(Color.rgb(180, 186, 202))
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(42)))

        val presets = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        left.addView(presets, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(44)).apply { topMargin = dp(4) })
        fun preset(label: String, command: String, capture: Int) {
            presets.addView(Button(this).apply {
                text = label
                textSize = 11f
                setOnClickListener {
                    commandEditor.setText(command)
                    captureEditor.setText(capture.toString())
                }
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
        }
        preset("R LIVE", "getevent -lt /dev/input/event6", 8)
        preset("L LIVE", "getevent -lt /dev/input/event3", 8)
        preset("INPUT", "dumpsys input | grep -i -E 'tgk|gamekey|nubia_tgk'", 0)

        left.addView(TextView(this).apply {
            text = "COMMAND — الصق أمر Shizuku هنا"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(8), dp(4), dp(4))
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(34)))

        commandEditor = EditText(this).apply {
            setText("getevent -lt /dev/input/event6")
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            hint = "اكتب أو الصق أمر shell هنا — يدعم عدة أسطر و pipes"
            gravity = Gravity.TOP or Gravity.START
            minLines = 5
            maxLines = 10
            isSingleLine = false
            setHorizontallyScrolling(false)
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = rounded(Color.rgb(25, 27, 35), 12)
        }
        left.addView(commandEditor, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(150)).apply { topMargin = dp(3) })

        val timing = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        left.addView(timing, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(46)).apply { topMargin = dp(5) })
        timing.addView(TextView(this).apply { text = "استعداد"; setTextColor(Color.WHITE); gravity = Gravity.CENTER }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 0.7f))
        prepareEditor = numberBox("3")
        timing.addView(prepareEditor, LinearLayout.LayoutParams(0, dp(42), 0.55f))
        timing.addView(TextView(this).apply { text = "ث  |  التقاط"; setTextColor(Color.WHITE); gravity = Gravity.CENTER }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
        captureEditor = numberBox("8")
        timing.addView(captureEditor, LinearLayout.LayoutParams(0, dp(42), 0.55f))
        timing.addView(TextView(this).apply { text = "ث"; setTextColor(Color.WHITE); gravity = Gravity.CENTER }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 0.35f))

        val runRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        left.addView(runRow, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48)))
        runRow.addView(Button(this).apply { text = "RUN"; setOnClickListener { runCommand() } }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
        runRow.addView(Button(this).apply { text = "STOP"; setOnClickListener { runCatching { remote?.stopActiveSession() } } }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))

        val right = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(4), dp(6), dp(4))
        }
        root.addView(right, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 0.57f))

        sessionTitle = TextView(this).apply {
            text = "IDLE"
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
        }
        right.addView(sessionTitle, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(30)))

        sessionBody = TextView(this).apply {
            text = "النتيجة محدودة تلقائيًا لمنع انهيار UserService."
            textSize = 11f
            setTextColor(Color.rgb(180, 187, 205))
        }
        right.addView(sessionBody, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(30)))

        val actionRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        right.addView(actionRow, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(44)))
        actionRow.addView(Button(this).apply { text = "نسخ"; setOnClickListener { copyResult() } }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
        actionRow.addView(Button(this).apply { text = "مسح"; setOnClickListener { runCatching { remote?.clearResult() }; resultView.text = "" } }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))

        resultView = TextView(this).apply {
            textSize = 11f
            typeface = Typeface.MONOSPACE
            setTextColor(Color.rgb(220, 223, 232))
            setTextIsSelectable(true)
            setPadding(dp(10), dp(10), dp(10), dp(10))
            text = "اتصل بـ Shizuku ثم شغّل «الفحص الأول الحاسم»."
        }
        resultScroll = ScrollView(this).apply {
            background = rounded(Color.rgb(20, 22, 28), 12)
            addView(resultView)
        }
        right.addView(resultScroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        return root
    }

    private fun numberBox(initial: String) = EditText(this).apply {
        setText(initial)
        inputType = InputType.TYPE_CLASS_NUMBER
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        background = rounded(Color.rgb(30, 33, 42), 10)
    }

    private fun startFirstCapture() {
        val s = requireService() ?: return
        if (runCatching { s.isBusy }.getOrDefault(false)) {
            Toast.makeText(this, "هناك جلسة تعمل بالفعل", Toast.LENGTH_SHORT).show()
            return
        }
        runCatching { s.startFirstCapture(3, 15) }
            .onFailure { Toast.makeText(this, "تعذر بدء الفحص: ${it.message}", Toast.LENGTH_LONG).show() }
    }

    private fun runCommand() {
        val s = requireService() ?: return
        val command = commandEditor.text.toString().trim()
        if (command.isBlank()) return
        val prep = prepareEditor.text.toString().toIntOrNull()?.coerceIn(0, 15) ?: 0
        val capture = captureEditor.text.toString().toIntOrNull()?.coerceIn(0, 120) ?: 0
        runCatching { s.runScript(command, prep, capture) }
            .onFailure { Toast.makeText(this, "تعذر تشغيل الأمر: ${it.message}", Toast.LENGTH_LONG).show() }
    }

    private fun requireService(): IShoulderDiagService? {
        val s = remote
        if (s != null) return s
        requestedConnect = true
        refreshShizuku(false, true)
        Toast.makeText(this, "اربط Shizuku أولًا", Toast.LENGTH_SHORT).show()
        return null
    }

    private fun poll() {
        val s = remote
        if (s != null) {
            runCatching {
                sessionTitle.text = s.sessionState
                val text = s.result
                if (text != resultView.text.toString()) {
                    resultView.text = text
                    resultScroll.post { resultScroll.fullScroll(View.FOCUS_DOWN) }
                }
                sessionBody.text = if (s.isBusy) "جلسة نشطة — يمكنك الضغط على R/L الآن أو STOP" else "جاهز — سجل دائري بحد أقصى 2000 سطر"
            }
        }
        ui.postDelayed({ poll() }, 300)
    }

    private fun refreshShizuku(fromBinder: Boolean, forcePrompt: Boolean = false) {
        if (!uiReady()) return
        val alive = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        if (!alive) {
            showShizuku(false, if (requestedConnect) "Shizuku يعمل في التطبيق الآخر؟ ارجع بعد تشغيله وسيُكتشف تلقائيًا." else "اضغط للاتصال")
            return
        }

        val permission = runCatching { Shizuku.checkSelfPermission() }.getOrDefault(PackageManager.PERMISSION_DENIED)
        if (permission != PackageManager.PERMISSION_GRANTED) {
            showShizuku(false, "Binder موجود — اضغط لمنح الإذن")
            if (forcePrompt || requestedConnect || fromBinder) runCatching { Shizuku.requestPermission(REQ_SHIZUKU) }
            return
        }

        if (remote != null) {
            showShizuku(true, "Shizuku + UserService متصلان")
            return
        }
        showShizuku(false, "الإذن ممنوح — جاري ربط UserService…")
        bindService()
    }

    private fun bindService() {
        runCatching { Shizuku.bindUserService(serviceArgs, connection) }
            .onFailure { showShizuku(false, "فشل bind: ${it.message ?: it.javaClass.simpleName}") }
    }

    private fun showShizuku(connected: Boolean, body: String) {
        shizukuTitle.text = if (connected) "Shizuku متصل ✓" else "اتصل بـ Shizuku"
        shizukuBody.text = body
        shizukuCard.background = rounded(if (connected) Color.rgb(28, 78, 57) else Color.rgb(67, 39, 45), 16)
    }

    private fun copyResult() {
        val clip = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clip.setPrimaryClip(ClipData.newPlainText("Nubia Input Lab result", resultView.text.toString()))
        Toast.makeText(this, "تم نسخ النتيجة", Toast.LENGTH_SHORT).show()
    }

    private fun rounded(color: Int, radiusDp: Int) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(color)
        cornerRadius = dp(radiusDp).toFloat()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    companion object {
        private const val REQ_SHIZUKU = 9104
    }
}

package com.idbetoch.diag

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.os.SystemClock
import java.io.BufferedReader
import java.io.InputStreamReader
import java.lang.reflect.Method
import java.util.ArrayDeque
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean

class ShoulderDiagUserService : IShoulderDiagService.Stub {
    private var serviceContext: Context? = null

    private val busy = AtomicBoolean(false)
    private val stopRequested = AtomicBoolean(false)
    private val lock = Any()
    private val lines = ArrayDeque<String>()
    private val activeProcesses = mutableListOf<java.lang.Process>()

    @Volatile private var state = "IDLE"
    @Volatile private var registeredListener: Any? = null
    @Volatile private var registeredManager: Any? = null
    @Volatile private var registeredUnregisterMethod: Method? = null
    @Volatile private var listenerThread: HandlerThread? = null

    constructor()
    constructor(context: Context) { serviceContext = context }

    override fun getBackendUid(): Int = Process.myUid()
    override fun isBusy(): Boolean = busy.get()
    override fun getSessionState(): String = state
    override fun getResult(): String = synchronized(lock) { lines.joinToString("\n") }

    override fun clearResult() {
        if (busy.get()) return
        synchronized(lock) { lines.clear() }
        state = "IDLE"
    }

    override fun stopActiveSession() {
        stopRequested.set(true)
        synchronized(activeProcesses) {
            activeProcesses.forEach { p -> runCatching { p.destroy() } }
        }
        state = "STOPPING"
    }

    override fun appendAppKeyEvent(eventLine: String?) {
        if (!busy.get() || eventLine.isNullOrBlank()) return
        append("APP $eventLine")
    }

    override fun runScript(script: String?, prepareSeconds: Int, captureSeconds: Int) {
        val clean = script?.trim().orEmpty()
        if (clean.isBlank()) return
        if (!busy.compareAndSet(false, true)) return
        stopRequested.set(false)
        resetOutput()
        Thread({ runScriptSession(clean, prepareSeconds, captureSeconds) }, "id-be-toch-script").start()
    }

    override fun startFirstCapture(prepareSeconds: Int, captureSeconds: Int) {
        if (!busy.compareAndSet(false, true)) return
        stopRequested.set(false)
        resetOutput()
        Thread({ runFirstCapture(prepareSeconds, captureSeconds) }, "id-be-toch-first-capture").start()
    }

    private fun runScriptSession(script: String, prepareSeconds: Int, captureSeconds: Int) {
        try {
            val prep = prepareSeconds.coerceIn(0, 15)
            val capture = captureSeconds.coerceIn(0, 120)
            append("=== INTERACTIVE SHIZUKU COMMAND ===")
            append("uid=${Process.myUid()} prepare=${prep}s capture=${capture}s")
            append("$ $script")
            if (!countdown(prep, "استعد الآن")) return

            state = if (capture > 0) "CAPTURING ${capture}s" else "RUNNING"
            val process = ProcessBuilder("/system/bin/sh", "-c", script)
                .redirectErrorStream(true)
                .start()
            track(process)

            val reader = startReader(process, "OUT")
            if (capture > 0) {
                waitWindow(capture * 1000L)
                if (process.isAlive) runCatching { process.destroy() }
            }
            waitProcess(process, 1500)
            reader.join(1200)
            append("EXIT=${runCatching { process.exitValue() }.getOrDefault(-999)}")
            append(if (stopRequested.get()) "=== STOPPED ===" else "=== COMMAND COMPLETE ===")
        } catch (t: Throwable) {
            append("FATAL ${t.javaClass.simpleName}: ${t.message.orEmpty()}")
        } finally {
            cleanupProcesses()
            state = if (stopRequested.get()) "STOPPED" else "DONE"
            busy.set(false)
        }
    }

    private fun runFirstCapture(prepareSeconds: Int, captureSeconds: Int) {
        val prep = prepareSeconds.coerceIn(1, 10)
        val capture = captureSeconds.coerceIn(6, 40)
        try {
            state = "PREPARING"
            append("=== PIXELTRIGGER LEFT-HALF FIRST CAPTURE ===")
            append("GOAL: identify the physical R/L signal and Nubia GameKey callback with minimal noise")
            append("uid=${Process.myUid()}")

            val ctx = serviceContext
            if (ctx == null) {
                append("FATAL: UserService context unavailable")
                return
            }

            val manager = ctx.getSystemService(Context.INPUT_SERVICE)
            if (manager == null) {
                append("FATAL: InputManager unavailable")
                return
            }

            appendTargetedInputState(manager)
            registerGameKeyListener(manager)

            val deviceList = runQuiet(listOf("/system/bin/getevent", "-pl"), 180_000)
            val rNode = discoverShoulderNode(deviceList, "nubia_tgk_aw_sar1_ch0")
            val lNode = discoverShoulderNode(deviceList, "nubia_tgk_aw_sar0_ch0")
            append("R_NODE=${rNode ?: "NOT_FOUND"} expectedLinuxKey=KEY_F8 expectedAndroidKeyCode=138")
            append("L_NODE=${lNode ?: "NOT_FOUND"} expectedLinuxKey=KEY_F7 expectedAndroidKeyCode=137")

            append("TARGETED_METHODS:")
            manager.javaClass.methods
                .filter { m ->
                    val n = m.name.lowercase()
                    n.contains("tgk") || n.contains("gamekey") || n.contains("gameleft") || n.contains("gameright")
                }
                .map { methodSignature(it) }
                .distinct()
                .sorted()
                .forEach { append("  $it") }

            if (!countdown(prep, "بعد انتهاء العد اضغط R ثم L عدة مرات")) return

            state = "CAPTURING R+L ${capture}s"
            append("=== LIVE START ===")
            append("اضغط R واتركه، ثم L واتركه، وكرر ذلك عدة مرات خلال ${capture}s.")

            val captures = mutableListOf<Capture>()
            if (rNode != null) startGetevent("R", rNode)?.let { captures += it }
            if (lNode != null) startGetevent("L", lNode)?.let { captures += it }

            waitWindow(capture * 1000L)
            captures.forEach { finishCapture(it) }

            append("=== LIVE END ===")
            appendTargetedInputState(manager)
            append("=== FIRST CAPTURE COMPLETE ===")
            append("Send this result as-is. It is intentionally compact and targeted at building PixelTrigger's left half.")
        } catch (t: Throwable) {
            append("FATAL ${t.javaClass.simpleName}: ${t.message.orEmpty()}")
        } finally {
            unregisterGameKeyListener()
            cleanupProcesses()
            state = if (stopRequested.get()) "STOPPED" else "DONE"
            busy.set(false)
        }
    }

    private fun appendTargetedInputState(manager: Any) {
        append("INPUT_STATE:")
        listOf(
            "isGameKeyEnable",
            "isLeftGameKeyEnable",
            "isRightGameKeyEnable",
            "getGameLeftKeyLinkFunction",
            "getGameRightKeyLinkFunction"
        ).forEach { name ->
            val m = (manager.javaClass.methods.asList() + manager.javaClass.declaredMethods.asList())
                .firstOrNull { it.name == name && it.parameterCount == 0 }
            if (m == null) {
                append("  $name=METHOD_NOT_FOUND")
            } else {
                val value = runCatching {
                    m.isAccessible = true
                    m.invoke(manager)
                }.fold({ it?.toString() ?: "null" }, { "ERROR:${it.cause?.message ?: it.message ?: it.javaClass.simpleName}" })
                append("  $name=$value")
            }
        }
    }

    private fun registerGameKeyListener(manager: Any) {
        append("GAMEKEY_LISTENER:")
        val methods = (manager.javaClass.methods.asList() + manager.javaClass.declaredMethods.asList())
            .distinctBy { methodSignature(it) }
            .filter { it.name.equals("registerInputGameKeyActionChangedListener", true) }
        if (methods.isEmpty()) {
            append("  NOT_FOUND")
            return
        }

        for (m in methods) {
            val listenerType = m.parameterTypes.firstOrNull { it.isInterface && it.name.contains("Listener", true) }
                ?: continue
            try {
                val listener = java.lang.reflect.Proxy.newProxyInstance(
                    listenerType.classLoader,
                    arrayOf(listenerType)
                ) { _: Any, callback: Method, args: Array<out Any?>? ->
                    val values = args ?: emptyArray()
                    if (callback.name == "onGameKeyActionChanged" && values.size == 7) {
                        val key = (values[0] as? Number)?.toInt()
                        val action = (values[2] as? Number)?.toInt()
                        val keyName = when (key) { 138 -> "R"; 137 -> "L"; else -> "K$key" }
                        val actionName = when (action) { 0 -> "DOWN"; 1 -> "UP"; else -> "A$action" }
                        append("GAMEKEY $keyName $actionName raw=${values.joinToString(prefix = "[", postfix = "]")}")
                    } else {
                        append("GAMEKEY ${callback.name}${values.joinToString(prefix = "[", postfix = "]")}")
                    }
                    defaultReturn(callback.returnType)
                }

                val ht = HandlerThread("id-be-toch-gamekey").apply { start() }
                listenerThread = ht
                val handler = Handler(ht.looper)
                val executor = Executor { command -> handler.post(command) }
                val args = m.parameterTypes.map { type ->
                    when {
                        type.isInstance(listener) -> listener
                        Handler::class.java.isAssignableFrom(type) -> handler
                        Executor::class.java.isAssignableFrom(type) -> executor
                        type == String::class.java -> serviceContext?.packageName
                        type == Int::class.javaPrimitiveType || type == Int::class.javaObjectType -> 0
                        type == Boolean::class.javaPrimitiveType || type == Boolean::class.javaObjectType -> false
                        else -> null
                    }
                }.toTypedArray()
                m.isAccessible = true
                m.invoke(manager, *args)
                registeredManager = manager
                registeredListener = listener
                registeredUnregisterMethod = (manager.javaClass.methods.asList() + manager.javaClass.declaredMethods.asList())
                    .firstOrNull { it.name.equals("unregisterInputGameKeyActionChangedListener", true) }
                append("  REGISTERED ${methodSignature(m)}")
                return
            } catch (t: Throwable) {
                append("  FAILED ${t.cause?.javaClass?.simpleName ?: t.javaClass.simpleName}: ${t.cause?.message ?: t.message.orEmpty()}")
            }
        }
    }

    private fun unregisterGameKeyListener() {
        val manager = registeredManager
        val listener = registeredListener
        val method = registeredUnregisterMethod
        if (manager != null && listener != null && method != null) {
            runCatching {
                val args = method.parameterTypes.map { type ->
                    when {
                        type.isInstance(listener) -> listener
                        type == Int::class.javaPrimitiveType || type == Int::class.javaObjectType -> 0
                        type == Boolean::class.javaPrimitiveType || type == Boolean::class.javaObjectType -> false
                        else -> null
                    }
                }.toTypedArray()
                method.isAccessible = true
                method.invoke(manager, *args)
            }
        }
        registeredManager = null
        registeredListener = null
        registeredUnregisterMethod = null
        listenerThread?.quitSafely()
        listenerThread = null
    }

    private data class Capture(val label: String, val process: java.lang.Process, val reader: Thread)

    private fun startGetevent(label: String, node: String): Capture? = try {
        val p = ProcessBuilder("/system/bin/getevent", "-lt", node).redirectErrorStream(true).start()
        track(p)
        val reader = Thread({
            BufferedReader(InputStreamReader(p.inputStream)).use { br ->
                var line: String?
                while (!stopRequested.get() && br.readLine().also { line = it } != null) {
                    val s = line.orEmpty()
                    if (s.contains("EV_KEY") || s.contains("ABS_DISTANCE")) append("$label $s")
                }
            }
        }, "id-be-toch-$label-reader").apply { start() }
        Capture(label, p, reader)
    } catch (t: Throwable) {
        append("$label GETEVENT_START_FAILED ${t.javaClass.simpleName}: ${t.message.orEmpty()}")
        null
    }

    private fun finishCapture(c: Capture) {
        runCatching { c.process.destroy() }
        waitProcess(c.process, 1000)
        c.reader.join(900)
        append("${c.label}_GETEVENT_EXIT=${runCatching { c.process.exitValue() }.getOrDefault(-999)}")
    }

    private fun startReader(process: java.lang.Process, prefix: String): Thread = Thread({
        BufferedReader(InputStreamReader(process.inputStream)).use { br ->
            var line: String?
            while (!stopRequested.get() && br.readLine().also { line = it } != null) append("$prefix ${line.orEmpty()}")
        }
    }, "id-be-toch-output").apply { start() }

    private fun countdown(seconds: Int, message: String): Boolean {
        if (seconds <= 0) return !stopRequested.get()
        for (n in seconds downTo 1) {
            if (stopRequested.get()) return false
            state = "READY IN ${n}s"
            append("READY_IN=$n $message")
            Thread.sleep(1000)
        }
        return !stopRequested.get()
    }

    private fun waitWindow(ms: Long) {
        val end = SystemClock.elapsedRealtime() + ms
        while (!stopRequested.get() && SystemClock.elapsedRealtime() < end) {
            Thread.sleep(minOf(100L, end - SystemClock.elapsedRealtime()))
        }
    }

    private fun waitProcess(p: java.lang.Process, maxMs: Long) {
        val end = SystemClock.elapsedRealtime() + maxMs
        while (p.isAlive && SystemClock.elapsedRealtime() < end) Thread.sleep(25)
        if (p.isAlive) runCatching { p.destroyForcibly() }
    }

    private fun runQuiet(command: List<String>, maxChars: Int): String = try {
        val p = ProcessBuilder(command).redirectErrorStream(true).start()
        val sb = StringBuilder()
        BufferedReader(InputStreamReader(p.inputStream)).use { br ->
            while (true) {
                val line = br.readLine() ?: break
                if (sb.length < maxChars) sb.append(line).append('\n')
            }
        }
        waitProcess(p, 3000)
        sb.toString()
    } catch (_: Throwable) { "" }

    private fun discoverShoulderNode(text: String, deviceName: String): String? {
        var current: String? = null
        text.lineSequence().forEach { raw ->
            val line = raw.trim()
            Regex("/dev/input/event\\d+").find(line)?.value?.let { current = it }
            if (line.contains(deviceName, true)) return current
        }
        return null
    }

    private fun methodSignature(m: Method): String = buildString {
        append(m.returnType.typeName).append(' ').append(m.name).append('(')
        append(m.parameterTypes.joinToString(", ") { it.typeName })
        append(')')
    }

    private fun defaultReturn(type: Class<*>): Any? = when (type) {
        java.lang.Boolean.TYPE -> false
        java.lang.Byte.TYPE -> 0.toByte()
        java.lang.Short.TYPE -> 0.toShort()
        java.lang.Integer.TYPE -> 0
        java.lang.Long.TYPE -> 0L
        java.lang.Float.TYPE -> 0f
        java.lang.Double.TYPE -> 0.0
        java.lang.Character.TYPE -> '\u0000'
        else -> null
    }

    private fun track(p: java.lang.Process) = synchronized(activeProcesses) { activeProcesses += p }

    private fun cleanupProcesses() {
        synchronized(activeProcesses) {
            activeProcesses.forEach { p -> if (p.isAlive) runCatching { p.destroyForcibly() } }
            activeProcesses.clear()
        }
    }

    private fun resetOutput() {
        synchronized(lock) { lines.clear() }
        append("backendUid=${Process.myUid()}")
    }

    private fun append(line: String) {
        synchronized(lock) {
            lines.addLast(line)
            while (lines.size > MAX_LINES) lines.removeFirst()
            var chars = lines.sumOf { it.length + 1 }
            while (chars > MAX_CHARS && lines.isNotEmpty()) {
                chars -= lines.removeFirst().length + 1
            }
        }
    }

    companion object {
        private const val MAX_LINES = 2000
        private const val MAX_CHARS = 140_000
    }
}

package com.idbetoch.diag

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.os.SystemClock
import java.io.BufferedReader
import java.io.InputStreamReader
import java.lang.reflect.Method
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean

class ShoulderDiagUserService : IShoulderDiagService.Stub {
    private var serviceContext: Context? = null
    private val running = AtomicBoolean(false)
    private val lock = Any()
    private val out = StringBuilder()
    @Volatile private var registeredListener: Any? = null
    @Volatile private var registeredManager: Any? = null
    @Volatile private var registeredUnregisterMethod: Method? = null

    constructor()
    constructor(context: Context) { serviceContext = context }

    override fun getBackendUid(): Int = Process.myUid()
    override fun isRunning(): Boolean = running.get()

    override fun clearResult() {
        if (running.get()) return
        synchronized(lock) { out.setLength(0) }
    }

    override fun appendAppKeyEvent(eventLine: String?) {
        if (!running.get() || eventLine.isNullOrBlank()) return
        append("[APP_KEYEVENT] $eventLine")
    }

    override fun getResult(): String = synchronized(lock) { out.toString() }

    override fun startScan(seconds: Int) {
        if (!running.compareAndSet(false, true)) return
        synchronized(lock) { out.setLength(0) }
        val duration = seconds.coerceIn(5, 60)
        Thread({ runScan(duration) }, "id-be-toch-scan").start()
    }

    private fun runScan(seconds: Int) {
        val deadlineMs = SystemClock.elapsedRealtime() + seconds * 1000L
        try {
            append("=== ID BE TOCH / SHOULDER R DIAGNOSTIC ===")
            append("backendUid=${Process.myUid()}")
            append("scanSeconds=$seconds")
            append("timeMs=${System.currentTimeMillis()}")
            append("")

            append("=== BUILD ===")
            appendShell("/system/bin/getprop", "ro.build.fingerprint")
            appendShell("/system/bin/getprop", "ro.build.type")
            appendShell("/system/bin/getprop", "ro.debuggable")
            append("")

            dumpHiddenApiSurface()
            tryRegisterGameKeyListener()

            append("=== INPUT DEVICES / GETEVENT -PL ===")
            val deviceList = runShell(listOf("/system/bin/getevent", "-pl"), 600_000)
            appendBlock(deviceList)
            val rNode = discoverRightShoulderNode(deviceList)
            append("RIGHT_SHOULDER_NODE=${rNode ?: "NOT_FOUND"}")
            append("")

            if (rNode != null) {
                append("=== R DEVICE CAPABILITIES ===")
                appendBlock(runShell(listOf("/system/bin/getevent", "-lp", rNode), 100_000))
                append("")
            }

            append("=== DUMPSYS INPUT MATCHES ===")
            val dumpsys = runShell(listOf("/system/bin/dumpsys", "input"), 1_500_000)
            appendBlock(filterWithContext(dumpsys, listOf(
                "nubia_tgk_aw_sar1_ch0", rNode ?: "__none__", "KEY_F8", "F8", "gamekey", "tgk"
            ), 8))
            append("")

            val remainingMs = (deadlineMs - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
            append("=== LIVE PHYSICAL R EVENTS (${remainingMs}ms remaining) ===")
            append("Press and release R several times now.")
            if (rNode == null) {
                append("Cannot start live getevent: R node was not discovered.")
                if (remainingMs > 0) Thread.sleep(remainingMs)
            } else if (remainingMs > 0) {
                captureGetevent(rNode, remainingMs)
            } else {
                append("No live window remained after static diagnostics.")
            }

            unregisterGameKeyListener()
            append("")
            append("=== SCAN COMPLETE ===")
        } catch (t: Throwable) {
            append("FATAL ${t.javaClass.simpleName}: ${t.message ?: ""}")
        } finally {
            unregisterGameKeyListener()
            running.set(false)
        }
    }

    private fun dumpHiddenApiSurface() {
        append("=== HIDDEN INPUT / REDMAGIC API SURFACE ===")
        val names = listOf(
            "android.hardware.input.InputManager",
            "android.hardware.input.IInputGameKeyActionChangedListener",
            "android.hardware.input.InputManager\$InputGameKeyActionChangedListener",
            "com.redmagic.game.touchgamekey.GameOperationKeyInputHelper",
            "com.redmagic.game.touchgamekey.GameOperationKeyInputEventHelper",
            "com.redmagic.game.touchgamekey.IInputGameKeyActionChangedListener"
        )
        names.forEach { className ->
            try {
                val c = Class.forName(className)
                append("CLASS $className interface=${c.isInterface}")
                c.declaredMethods
                    .filter { m ->
                        val s = m.name.lowercase()
                        s.contains("game") || s.contains("tgk") || s.contains("key") || s.contains("input") || s.contains("action")
                    }
                    .sortedBy { it.name }
                    .forEach { append("  ${methodSignature(it)}") }
            } catch (t: Throwable) {
                append("CLASS $className -> ${t.javaClass.simpleName}: ${t.message ?: ""}")
            }
        }
        append("")
    }

    private fun tryRegisterGameKeyListener() {
        append("=== GAME KEY CALLBACK REGISTRATION ===")
        try {
            val ctx = serviceContext ?: run {
                append("No UserService Context available; callback registration skipped.")
                return
            }
            val manager = ctx.getSystemService(Context.INPUT_SERVICE) ?: run {
                append("InputManager service unavailable.")
                return
            }
            registeredManager = manager
            val methods = (manager.javaClass.methods.asList() + manager.javaClass.declaredMethods.asList())
                .distinctBy { methodSignature(it) }
                .filter { it.name.equals("registerInputGameKeyActionChangedListener", true) }
            if (methods.isEmpty()) {
                append("registerInputGameKeyActionChangedListener not found on runtime InputManager class ${manager.javaClass.name}")
                return
            }

            for (m in methods) {
                append("TRY ${methodSignature(m)}")
                val listenerType = m.parameterTypes.firstOrNull { it.isInterface && it.name.contains("Listener", true) }
                    ?: m.parameterTypes.firstOrNull { it.name.contains("Listener", true) }
                if (listenerType == null || !listenerType.isInterface) {
                    append("  Listener parameter is not a Java interface; cannot proxy dynamically.")
                    continue
                }

                val listener = java.lang.reflect.Proxy.newProxyInstance(listenerType.classLoader, arrayOf(listenerType)) { _: Any, callback: Method, args: Array<out Any?>? ->
                    val rendered = args?.joinToString(prefix = "[", postfix = "]") { value -> renderArg(value) } ?: "[]"
                    append("[GAMEKEY_CALLBACK] ${callback.name}$rendered")
                    defaultReturn(callback.returnType)
                }

                val handlerThread = HandlerThread("id-be-toch-listener").apply { start() }
                val handler = Handler(handlerThread.looper)
                val executor = Executor { command -> handler.post(command) }
                val invokeArgs = m.parameterTypes.map { type ->
                    when {
                        type.isInstance(listener) -> listener
                        Handler::class.java.isAssignableFrom(type) -> handler
                        Executor::class.java.isAssignableFrom(type) -> executor
                        type == String::class.java -> ctx.packageName
                        type == Int::class.javaPrimitiveType || type == Int::class.javaObjectType -> 0
                        type == Boolean::class.javaPrimitiveType || type == Boolean::class.javaObjectType -> false
                        else -> null
                    }
                }.toTypedArray()

                try {
                    m.isAccessible = true
                    m.invoke(manager, *invokeArgs)
                    registeredListener = listener
                    registeredUnregisterMethod = (manager.javaClass.methods.asList() + manager.javaClass.declaredMethods.asList())
                        .firstOrNull { it.name.equals("unregisterInputGameKeyActionChangedListener", true) || it.name.equals("unRegisterInputGameKeyActionChangedListener", true) }
                    append("REGISTERED via ${methodSignature(m)}")
                    return
                } catch (t: Throwable) {
                    val cause = t.cause ?: t
                    append("  FAILED ${cause.javaClass.simpleName}: ${cause.message ?: ""}")
                }
            }
        } catch (t: Throwable) {
            append("registration error ${t.javaClass.simpleName}: ${t.message ?: ""}")
        }
    }

    private fun unregisterGameKeyListener() {
        val manager = registeredManager ?: return
        val listener = registeredListener ?: return
        val m = registeredUnregisterMethod ?: return
        try {
            val args = m.parameterTypes.map { type ->
                when {
                    type.isInstance(listener) -> listener
                    type == Int::class.javaPrimitiveType || type == Int::class.javaObjectType -> 0
                    type == Boolean::class.javaPrimitiveType || type == Boolean::class.javaObjectType -> false
                    else -> null
                }
            }.toTypedArray()
            m.isAccessible = true
            m.invoke(manager, *args)
            append("GAMEKEY listener unregistered")
        } catch (t: Throwable) {
            val cause = t.cause ?: t
            append("GAMEKEY unregister failed: ${cause.javaClass.simpleName}: ${cause.message ?: ""}")
        } finally {
            registeredListener = null
            registeredManager = null
            registeredUnregisterMethod = null
        }
    }

    private fun captureGetevent(node: String, durationMs: Long) {
        try {
            val p = ProcessBuilder("/system/bin/getevent", "-lt", node)
                .redirectErrorStream(true)
                .start()
            val readerThread = Thread({
                BufferedReader(InputStreamReader(p.inputStream)).use { br ->
                    var line: String?
                    while (br.readLine().also { line = it } != null) append("[GETEVENT] ${line.orEmpty()}")
                }
            }, "id-be-toch-getevent-reader").apply { start() }

            Thread.sleep(durationMs)
            p.destroy()
            runCatching { p.waitFor() }
            if (p.isAlive) p.destroyForcibly()
            readerThread.join(1200)
            append("GETEVENT_EXIT=${runCatching { p.exitValue() }.getOrDefault(-999)}")
        } catch (t: Throwable) {
            append("getevent capture failed ${t.javaClass.simpleName}: ${t.message ?: ""}")
        }
    }

    private fun discoverRightShoulderNode(text: String): String? {
        var current: String? = null
        text.lineSequence().forEach { raw ->
            val line = raw.trim()
            Regex("/dev/input/event\\d+").find(line)?.value?.let { current = it }
            if (line.contains("nubia_tgk_aw_sar1_ch0", true)) return current
        }
        return null
    }

    private fun filterWithContext(text: String, needles: List<String>, radius: Int): String {
        val lines = text.lines()
        val keep = sortedSetOf<Int>()
        lines.forEachIndexed { i, line ->
            if (needles.any { it.isNotBlank() && line.contains(it, true) }) {
                for (j in (i - radius).coerceAtLeast(0)..(i + radius).coerceAtMost(lines.lastIndex)) keep.add(j)
            }
        }
        return if (keep.isEmpty()) "(no matching lines)" else keep.joinToString("\n") { lines[it] }
    }

    private fun runShell(command: List<String>, maxChars: Int): String {
        return try {
            val p = ProcessBuilder(command).redirectErrorStream(true).start()
            val sb = StringBuilder()
            BufferedReader(InputStreamReader(p.inputStream)).use { br ->
                while (true) {
                    val line = br.readLine() ?: break
                    if (sb.length < maxChars) sb.append(line).append('\n')
                }
            }
            p.waitFor()
            if (sb.length >= maxChars) sb.append("\n[TRUNCATED at $maxChars chars]\n")
            sb.toString()
        } catch (t: Throwable) {
            "${t.javaClass.simpleName}: ${t.message ?: ""}"
        }
    }

    private fun appendShell(vararg command: String) = appendBlock(runShell(command.toList(), 100_000))

    private fun methodSignature(m: Method): String = buildString {
        append(m.returnType.typeName).append(' ').append(m.name).append('(')
        append(m.parameterTypes.joinToString(", ") { it.typeName })
        append(')')
    }

    private fun renderArg(value: Any?): String = when (value) {
        null -> "null"
        is Number, is Boolean, is CharSequence -> value.toString()
        else -> "${value.javaClass.name}=$value"
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

    private fun appendBlock(text: String) { text.lineSequence().forEach { append(it) } }

    private fun append(line: String) {
        synchronized(lock) { out.append(line).append('\n') }
    }
}

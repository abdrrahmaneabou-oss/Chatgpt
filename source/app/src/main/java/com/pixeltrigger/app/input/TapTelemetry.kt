package com.pixeltrigger.app.input

import java.util.ArrayDeque

/**
 * Small in-memory trace used to prove whether a visible double action came from two detector
 * FIRE events, two injection submissions, or the target app interpreting one touch twice.
 */
class TapTelemetry(private val capacity: Int = 512) {
    enum class Phase {
        DETECTED,
        SUBMITTED,
        COMPLETED,
        CANCELLED,
        REJECTED,
    }

    data class Entry(
        val triggerId: Long,
        val phase: Phase,
        val timestampNs: Long,
        val backend: String,
        val detail: String? = null,
    )

    private val entries = ArrayDeque<Entry>(capacity)

    @Synchronized
    fun record(entry: Entry) {
        while (entries.size >= capacity) entries.removeFirst()
        entries.addLast(entry)
    }

    @Synchronized
    fun snapshot(): List<Entry> = entries.toList()

    @Synchronized
    fun clear() = entries.clear()
}

package com.pixeltrigger.app.input

import java.util.LinkedHashSet

/** Prevents the same detector trigger ID from producing more than one injection request. */
class ExactlyOnceTapGate(private val capacity: Int = 256) {
    private val accepted = LinkedHashSet<Long>()

    @Synchronized
    fun accept(triggerId: Long): Boolean {
        if (!accepted.add(triggerId)) return false
        while (accepted.size > capacity) {
            val oldest = accepted.iterator().next()
            accepted.remove(oldest)
        }
        return true
    }
}

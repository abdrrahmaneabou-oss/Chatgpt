package com.pixeltrigger.app.input

import java.util.concurrent.atomic.AtomicLong

/** Monotonic, lock-free duplicate guard. A trigger ID can be accepted only once. */
class ExactlyOnceTapGate {
    private val highestAccepted = AtomicLong(0L)

    fun accept(triggerId: Long): Boolean {
        if (triggerId <= 0) return false
        while (true) {
            val previous = highestAccepted.get()
            if (triggerId <= previous) return false
            if (highestAccepted.compareAndSet(previous, triggerId)) return true
        }
    }
}

package com.pixeltrigger.app.input

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExactlyOnceTapGateTest {
    @Test fun rejectsSameOrOlderTriggerIds() {
        val gate = ExactlyOnceTapGate()
        assertTrue(gate.accept(1))
        assertFalse(gate.accept(1))
        assertFalse(gate.accept(0))
        assertTrue(gate.accept(2))
        assertFalse(gate.accept(1))
    }
}

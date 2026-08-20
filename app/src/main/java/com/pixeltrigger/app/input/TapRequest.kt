package com.pixeltrigger.app.input

data class TapRequest(
    val triggerId: Long,
    val x: Float,
    val y: Float,
    val requestedDurationMs: Long = 1L,
    val requestedAtNs: Long,
    val displayId: Int = 0,
    val frameTimestampNs: Long = 0L,
    val captureCallbackNs: Long = 0L,
    val sampleStartNs: Long = 0L,
    val sampleEndNs: Long = 0L,
    val detectionStartNs: Long = 0L,
    val fireDecisionNs: Long = 0L,
)

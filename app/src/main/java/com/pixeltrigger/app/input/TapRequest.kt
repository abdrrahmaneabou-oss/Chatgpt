package com.pixeltrigger.app.input

data class TapRequest(
    val triggerId: Long,
    val x: Float,
    val y: Float,
    val requestedDurationMs: Long = 1L,
    val requestedAtNs: Long,
    val displayId: Int = 0,
    // Kept for source compatibility/forensics only. Absolute Image.timestamp must
    // never be compared with elapsedRealtimeNanos because its clock domain may differ.
    val frameTimestampNs: Long = 0L,
    val captureCallbackNs: Long = 0L,
    val sampleStartNs: Long = 0L,
    val sampleEndNs: Long = 0L,
    val detectionStartNs: Long = 0L,
    val fireDecisionNs: Long = 0L,
    // Clock-safe cadence metrics captured by AppLatencyProfiler on every sampled frame.
    val samplerEntryGapNs: Long = -1L,
    // Delta only inside the foreign Image.timestamp domain; never an absolute cross-clock age.
    val imageTimestampGapNs: Long = -1L,
)

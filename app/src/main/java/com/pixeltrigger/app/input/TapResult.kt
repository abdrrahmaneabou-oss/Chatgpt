package com.pixeltrigger.app.input

sealed interface TapResult {
    val triggerId: Long
    val acceptedAtNs: Long

    data class Completed(
        override val triggerId: Long,
        override val acceptedAtNs: Long,
        val downSentAtNs: Long,
        val upSentAtNs: Long,
    ) : TapResult

    data class Rejected(
        override val triggerId: Long,
        override val acceptedAtNs: Long,
        val reason: String,
    ) : TapResult
}

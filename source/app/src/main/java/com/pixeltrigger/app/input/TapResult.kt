package com.pixeltrigger.app.input

sealed interface TapResult {
    val triggerId: Long
    val acceptedAtNs: Long

    data class Completed(
        override val triggerId: Long,
        override val acceptedAtNs: Long,
        val completedAtNs: Long,
    ) : TapResult

    data class Cancelled(
        override val triggerId: Long,
        override val acceptedAtNs: Long,
        val cancelledAtNs: Long,
    ) : TapResult

    data class Rejected(
        override val triggerId: Long,
        override val acceptedAtNs: Long,
        val reason: String,
    ) : TapResult
}

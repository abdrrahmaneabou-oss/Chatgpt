package com.pixeltrigger.app.input

interface TapEngine {
    val name: String
    suspend fun tap(request: TapRequest): TapResult
}

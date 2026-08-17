package com.pixeltrigger.app.input

import java.util.concurrent.atomic.AtomicReference

/**
 * Keeps TapCoordinator stable while the service selects the privileged backend during startup.
 * Switching is explicit and never happens in the middle of one tap request.
 */
class SwitchableTapEngine(initial: TapEngine) : TapEngine {
    private val active = AtomicReference(initial)

    override val name: String
        get() = active.get().name

    fun current(): TapEngine = active.get()

    fun switchTo(engine: TapEngine) {
        active.set(engine)
    }

    override suspend fun tap(request: TapRequest): TapResult {
        val selected = active.get()
        return selected.tap(request)
    }
}

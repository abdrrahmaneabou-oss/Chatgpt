package com.pixeltrigger.app

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent

/**
 * Compatibility-only input backend. The Pro backend does not use Accessibility injection.
 *
 * IMPORTANT: merely enabling this Android accessibility service is not enough to make
 * PixelTrigger use dispatchGesture(). The old injector can interrupt an active user gesture,
 * so compatibility fallback is disabled by default and requires an explicit app opt-in flag.
 */
class TriggerAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    companion object {
        private const val PREFS_NAME = "pixel_trigger_settings"
        private const val KEY_ALLOW_ACCESSIBILITY_FALLBACK = "allow_accessibility_fallback"

        @Volatile
        private var instance: TriggerAccessibilityService? = null

        fun current(): TriggerAccessibilityService? = instance

        /** True only when the user has explicitly opted into the legacy, interrupting backend. */
        fun isEnabled(context: Context): Boolean =
            isCompatibilityFallbackOptedIn(context) && isServiceEnabled(context)

        fun isServiceEnabled(context: Context): Boolean {
            val expected = ComponentName(context, TriggerAccessibilityService::class.java).flattenToString()
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ) ?: return false
            return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
        }

        fun isCompatibilityFallbackOptedIn(context: Context): Boolean =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_ALLOW_ACCESSIBILITY_FALLBACK, false)

        fun setCompatibilityFallbackOptIn(context: Context, enabled: Boolean) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_ALLOW_ACCESSIBILITY_FALLBACK, enabled)
                .apply()
        }
    }
}

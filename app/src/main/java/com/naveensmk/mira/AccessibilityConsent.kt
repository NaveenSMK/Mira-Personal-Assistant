package com.naveensmk.mira

import android.content.Context

/** Versioned affirmative-consent gate for optional AccessibilityService automation. */
object AccessibilityConsent {
    private const val PREFS = "mira_accessibility_consent"
    private const val KEY_VERSION = "accepted_version"
    private const val KEY_ACCEPTED_AT = "accepted_at"
    const val CURRENT_VERSION = 1

    fun isAccepted(context: Context): Boolean =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_VERSION, 0) >= CURRENT_VERSION

    fun accept(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt(KEY_VERSION, CURRENT_VERSION)
            .putLong(KEY_ACCEPTED_AT, System.currentTimeMillis())
            .apply()
    }

    fun revoke(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove(KEY_VERSION).remove(KEY_ACCEPTED_AT).apply()
        AccessibilityController.cancelPending("MIRA automation consent was revoked. Continue manually.")
    }
}

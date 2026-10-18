package com.naveensmk.mira

/** Best-effort PII minimization; not a guarantee that every secret can be detected. */
object ScreenContextPolicy {
    private val patterns = listOf(
        Regex("(?i)\\b(?:password|passcode|pin|one[- ]time password|verification code|security code)\\s*[:=-]?\\s*[A-Za-z0-9@#$%^&*!_-]{3,}"),
        Regex("(?i)\\b(?:otp|one[- ]time password|verification code|security code|passcode)\\s*[:=-]?\\s*(\\d{4,8})\\b"),
        Regex("\\b(?:\\d[ -]*?){13,19}\\b"),
        Regex("(?i)\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b"),
        Regex("(?i)\\b(?:account|acct|routing)\\s*(?:number|no\\.?)?\\s*[:#-]?\\s*\\d{4,}\\b")
    )

    fun sanitize(input: String): String {
        var result = input.take(ScreenContextStore.MAX_TEXT_CHARS * 2)
        for (pattern in patterns) result = pattern.replace(result, "[REDACTED]")
        return result.take(ScreenContextStore.MAX_TEXT_CHARS)
    }
}

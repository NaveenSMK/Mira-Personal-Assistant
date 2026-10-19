package com.naveensmk.mira

/** Short-lived in-memory screen context. Raw screenshots are never retained here. */
object ScreenContextStore {
    @Volatile private var assistText = ""
    @Volatile private var ocrText = ""
    @Volatile private var capturedAtMillis = 0L
    @Volatile private var ocrGeneration = 0L
    @Volatile private var sessionGeneration = 0L

    @Synchronized fun beginCapture(): Long {
        sessionGeneration++
        ocrGeneration++
        assistText = ""
        ocrText = ""
        capturedAtMillis = System.currentTimeMillis()
        return sessionGeneration
    }

    @Synchronized fun updateAssistText(text: String, expectedSessionGeneration: Long? = null) {
        if (expectedSessionGeneration != null && expectedSessionGeneration != sessionGeneration) return
        if (sessionGeneration == 0L) sessionGeneration = 1L
        assistText = ScreenContextPolicy.sanitize(text).take(MAX_TEXT_CHARS)
        capturedAtMillis = System.currentTimeMillis()
    }

    @Synchronized fun updateScreenshot(): Long {
        // Each screenshot gets a unique OCR generation so delayed results cannot overwrite newer context.
        ocrGeneration++
        // A screenshot begins a new OCR generation only. Keep valid AssistStructure
        // text from this assistant invocation even if OCR is empty or fails.
        ocrText = ""
        capturedAtMillis = System.currentTimeMillis()
        return ocrGeneration
    }

    @Synchronized fun updateOcrText(text: String, expectedGeneration: Long) {
        if (expectedGeneration != ocrGeneration || text.isBlank()) return
        ocrText = ScreenContextPolicy.sanitize(text).take(MAX_TEXT_CHARS)
        capturedAtMillis = System.currentTimeMillis()
    }

    @Synchronized fun snapshotText(): String {
        expireIfNeeded()
        return listOf(assistText.trim(), ocrText.trim()).filter { it.isNotBlank() }
            .distinct().joinToString("\n").take(MAX_TEXT_CHARS)
    }

    @Synchronized fun hasContext(): Boolean {
        expireIfNeeded()
        return assistText.isNotBlank() || ocrText.isNotBlank()
    }

    @Synchronized fun clear() {
        sessionGeneration++
        ocrGeneration++
        assistText = ""
        ocrText = ""
        capturedAtMillis = 0L
    }

    @Synchronized fun capturedAt(): Long {
        expireIfNeeded()
        return capturedAtMillis
    }

    private fun expireIfNeeded() {
        if (capturedAtMillis > 0L && System.currentTimeMillis() - capturedAtMillis > TTL_MILLIS) {
            sessionGeneration++
            ocrGeneration++
            assistText = ""
            ocrText = ""
            capturedAtMillis = 0L
        }
    }

    const val MAX_TEXT_CHARS = 4_000
    const val TTL_MILLIS = 120_000L
}

package com.example.voicefirst

/** Short-lived in-memory screen context. Raw screenshots are never retained here. */
object ScreenContextStore {
    @Volatile private var assistText = ""
    @Volatile private var ocrText = ""
    @Volatile private var capturedAtMillis = 0L
    @Volatile private var generation = 0L

    @Synchronized fun beginCapture() {
        generation++
        assistText = ""
        ocrText = ""
        capturedAtMillis = System.currentTimeMillis()
    }

    @Synchronized fun updateAssistText(text: String) {
        if (generation == 0L) generation = 1L
        assistText = ScreenContextPolicy.sanitize(text).take(MAX_TEXT_CHARS)
        capturedAtMillis = System.currentTimeMillis()
    }

    @Synchronized fun updateScreenshot(): Long {
        if (generation == 0L) generation = 1L
        capturedAtMillis = System.currentTimeMillis()
        return generation
    }

    @Synchronized fun updateOcrText(text: String, expectedGeneration: Long) {
        if (expectedGeneration != generation || text.isBlank()) return
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
        generation++
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
            generation++
            assistText = ""
            ocrText = ""
            capturedAtMillis = 0L
        }
    }

    companion object {
        const val MAX_TEXT_CHARS = 4_000
        const val TTL_MILLIS = 120_000L
    }
}

package com.naveensmk.mira

import android.service.voice.VoiceInteractionService

/**
 * Lightweight system entry point. The system keeps the selected service alive, so this
 * class intentionally does not load the model or perform expensive work.
 */
class MiraAssistantService : VoiceInteractionService()

package com.naveensmk.mira

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognitionService
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.concurrent.ConcurrentHashMap

/**
 * Recognition-service adapter required by Android's assistant profile on older releases.
 * It delegates to an installed system/external recognizer, never itself, to avoid recursion.
 * MIRA's own microphone UI still starts recognition only after the user taps the mic.
 */
class MiraRecognitionService : RecognitionService() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val active = ConcurrentHashMap<Callback, SpeechRecognizer>()

    override fun onStartListening(recognizerIntent: Intent?, listener: Callback?) {
        if (listener == null) return
        mainHandler.post {
            if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                listener.error(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS)
                return@post
            }

            val component = resolveExternalRecognizer()
            if (component == null) {
                listener.error(SpeechRecognizer.ERROR_CLIENT)
                return@post
            }

            runCatching {
                val recognizer = SpeechRecognizer.createSpeechRecognizer(this, component)
                active.put(listener, recognizer)?.destroy()
                recognizer.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) { listener.readyForSpeech(params ?: Bundle()) }
                    override fun onBeginningOfSpeech() { listener.beginningOfSpeech() }
                    override fun onRmsChanged(rmsdB: Float) { listener.rmsChanged(rmsdB) }
                    override fun onBufferReceived(buffer: ByteArray?) { if (buffer != null) listener.bufferReceived(buffer) }
                    override fun onEndOfSpeech() { listener.endOfSpeech() }
                    override fun onError(error: Int) {
                        listener.error(error)
                        destroyFor(listener)
                    }
                    override fun onResults(results: Bundle?) {
                        if (results != null) listener.results(results) else listener.error(SpeechRecognizer.ERROR_NO_MATCH)
                        destroyFor(listener)
                    }
                    override fun onPartialResults(partialResults: Bundle?) {
                        if (partialResults != null) listener.partialResults(partialResults)
                    }
                    override fun onEvent(eventType: Int, params: Bundle?) {
                        // No additional recognition events need forwarding.
                    }
                })
                recognizer.startListening(recognizerIntent ?: Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH))
            }.onFailure {
                listener.error(SpeechRecognizer.ERROR_CLIENT)
                destroyFor(listener)
            }
        }
    }

    override fun onStopListening(listener: Callback?) {
        if (listener == null) return
        mainHandler.post { active[listener]?.let { runCatching { it.stopListening() } } }
    }

    override fun onCancel(listener: Callback?) {
        if (listener == null) return
        mainHandler.post { destroyFor(listener, cancel = true) }
    }

    private fun resolveExternalRecognizer(): ComponentName? {
        val configured = Settings.Secure.getString(contentResolver, "voice_recognition_service")
            ?.let { ComponentName.unflattenFromString(it) }
        if (configured != null && configured.packageName != packageName) {
            val valid = runCatching {
                val info = packageManager.getServiceInfo(configured, PackageManager.MATCH_ALL)
                info.enabled && info.exported && info.permission == android.Manifest.permission.BIND_SPEECH_RECOGNITION_SERVICE
            }.getOrDefault(false)
            if (valid) return configured
        }
        // If the device does not expose its configured component, ask Android's intent
        // resolver for the default provider. Never pick an arbitrary service by package order.
        val query = Intent(RecognitionService.SERVICE_INTERFACE).addCategory(Intent.CATEGORY_DEFAULT)
        val info = packageManager.resolveService(query, PackageManager.MATCH_DEFAULT_ONLY)?.serviceInfo ?: return null
        if (info.packageName == packageName || !info.enabled || !info.exported ||
            info.permission != android.Manifest.permission.BIND_SPEECH_RECOGNITION_SERVICE) return null
        val isSystem = runCatching {
            packageManager.getApplicationInfo(info.packageName, 0).flags and
                android.content.pm.ApplicationInfo.FLAG_SYSTEM != 0
        }.getOrDefault(false)
        if (!isSystem) return null
        return ComponentName(info.packageName, info.name)
    }

    private fun destroyFor(callback: Callback, cancel: Boolean = false) {
        active.remove(callback)?.let { recognizer ->
            runCatching { if (cancel) recognizer.cancel() }
            runCatching { recognizer.destroy() }
        }
    }

    override fun onDestroy() {
        mainHandler.post {
            active.values.toList().forEach { runCatching { it.cancel() }; runCatching { it.destroy() } }
            active.clear()
        }
        super.onDestroy()
    }
}

package com.example.voicefirst

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognitionService
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.concurrent.ConcurrentHashMap

/**
 * Recognition-service adapter required by Android's assistant profile on older releases.
 * It delegates to an installed system/external recognizer, never itself, to avoid recursion.
 * VoiceFirst's own microphone UI still starts recognition only after the user taps the mic.
 */
class VoiceFirstRecognitionService : RecognitionService() {
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
                    override fun onEvent(eventType: Int,params:Bundle?){
                        //No Additional handling needed
                    }
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
        val query = Intent(RecognitionService.SERVICE_INTERFACE)
        return packageManager.queryIntentServices(query, PackageManager.MATCH_ALL)
            .asSequence()
            .mapNotNull { it.serviceInfo }
            .firstOrNull { it.packageName != packageName && it.enabled && it.exported }
            ?.let { ComponentName(it.packageName, it.name) }
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

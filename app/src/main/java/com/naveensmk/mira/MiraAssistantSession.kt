package com.naveensmk.mira

import android.app.assist.AssistContent
import android.app.assist.AssistStructure
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

/**
 * The system invokes this session when MIRA is summoned as the default assistant.
 * Android may provide AssistStructure and a screenshot, subject to user/device/app policy.
 */
class MiraAssistantSession(context: android.content.Context) :
    VoiceInteractionSession(context) {

    @Suppress("DEPRECATION")
    override fun onHandleAssist(data: Bundle?, structure: AssistStructure?, content: AssistContent?) {
        val text = extractVisibleText(structure)
        if (text.isNotBlank()) ScreenContextStore.updateAssistText(text)
    }

    override fun onCreate() {
        super.onCreate()
        // Reset prior invocation before the system delivers this session's assist callbacks.
        ScreenContextStore.beginCapture()
    }

    override fun onHandleScreenshot(screenshot: Bitmap?) {
        if (screenshot == null || screenshot.isRecycled) return
        val maxEdge = 1_024
        val scale = minOf(1f, maxEdge.toFloat() / maxOf(screenshot.width, screenshot.height).coerceAtLeast(1))
        val scaled = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                screenshot,
                (screenshot.width * scale).toInt().coerceAtLeast(1),
                (screenshot.height * scale).toInt().coerceAtLeast(1),
                true
            )
        } else {
            screenshot.copy(Bitmap.Config.RGB_565, false)
        }
        // Process the system-provided screenshot transiently; never store the bitmap.
        val captureGeneration = ScreenContextStore.updateScreenshot()
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        recognizer.process(InputImage.fromBitmap(scaled, 0))
            .addOnSuccessListener { result ->
                val recognized = result.text.trim()
                if (recognized.isNotBlank()) ScreenContextStore.updateOcrText(recognized, captureGeneration)
            }
            .addOnCompleteListener {
                recognizer.close()
                if (!scaled.isRecycled) scaled.recycle()
            }
    }

    override fun onCreateContentView(): View {
        val density = context.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(16))
            setBackgroundColor(0xFFF3F9FD.toInt())
            layoutParams = ViewGroup.LayoutParams(-1, -2)
        }
        root.addView(TextView(context).apply {
            text = "MIRA screen assistant"
            textSize = 20f
            setTextColor(0xFF123047.toInt())
        })
        root.addView(TextView(context).apply {
            text = "Screen context is processed locally. Some apps hide their content. Nothing is uploaded automatically. Screenshot OCR is Latin-script; Android assist text may include other scripts."
            textSize = 13f
            setTextColor(0xFF678094.toInt())
            setPadding(0, dp(8), 0, dp(12))
        })
        root.addView(Button(context).apply {
            text = "Ask MIRA about this screen"
            setOnClickListener {
                val launch = Intent(context, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    putExtra(MainActivity.EXTRA_OPEN_SCREEN_CONTEXT, true)
                }
                context.startActivity(launch)
                finish()
            }
        })
        root.addView(Button(context).apply {
            text = "Dismiss"
            setOnClickListener { finish() }
        })
        return ScrollView(context).apply { addView(root) }
    }

    private fun extractVisibleText(structure: AssistStructure?): String {
        if (structure == null || structure.windowNodeCount <= 0) return ""
        val out = StringBuilder()
        var visited = 0
        fun appendNode(node: AssistStructure.ViewNode?) {
            if (node == null || visited >= MAX_NODES || out.length >= ScreenContextStore.MAX_TEXT_CHARS) return
            visited++
            val inputType = node.inputType
            val passwordMask = InputType.TYPE_TEXT_VARIATION_PASSWORD or
                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD or
                InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD or
                InputType.TYPE_NUMBER_VARIATION_PASSWORD
            val hints = node.autofillHints.orEmpty().joinToString(" ").lowercase()
            val credentialLike = (inputType and passwordMask) != 0 ||
                hints.contains("password") || hints.contains("passcode") ||
                hints.contains("pin") || hints.contains("credit") ||
                hints.contains("otp") || hints.contains("one-time") ||
                hints.contains("verification") || hints.contains("securitycode") ||
                hints.contains("security_code") || hints.contains("recovery") ||
                hints.contains("seedphrase") || hints.contains("privatekey")
            if (credentialLike) return
            val candidates = listOf(node.text?.toString(), node.contentDescription?.toString(), node.hint?.toString())
            candidates.forEach { candidate ->
                val value = candidate?.trim().orEmpty()
                if (value.isNotEmpty() && out.length < ScreenContextStore.MAX_TEXT_CHARS) {
                    val safe = ScreenContextPolicy.sanitize(value.take(500))
                    if (safe.isNotBlank() && !out.contains(safe)) out.append(safe).append('\n')
                }
            }
            for (i in 0 until node.childCount) {
                appendNode(node.getChildAt(i))
                if (visited >= MAX_NODES || out.length >= ScreenContextStore.MAX_TEXT_CHARS) break
            }
        }
        for (i in 0 until structure.windowNodeCount) {
            appendNode(structure.getWindowNodeAt(i)?.rootViewNode)
            if (visited >= MAX_NODES || out.length >= ScreenContextStore.MAX_TEXT_CHARS) break
        }
        return out.toString().take(ScreenContextStore.MAX_TEXT_CHARS)
    }

    companion object { private const val MAX_NODES = 350 }
}

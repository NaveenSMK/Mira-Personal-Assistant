package com.example.voicefirst

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

/** Narrow, queued UI capabilities. There is no arbitrary-intent or coordinate API. */
enum class UiAction { CLICK_TEXT, ENTER_TEXT, SCROLL_FORWARD, SCROLL_BACKWARD, BACK }

data class UiCommand(
    val action: UiAction,
    val target: String = "",
    val text: String = "",
    val expiresAtElapsedRealtime: Long = SystemClock.elapsedRealtime() + 15_000L
)

internal data class PendingUiAction(
    val command: UiCommand,
    val result: CompletableDeferred<String> = CompletableDeferred()
)

object AccessibilityController {
    private val lock = Any()
    @Volatile private var service: GenericAccessibilityService? = null
    private var pending: PendingUiAction? = null
    private var active: PendingUiAction? = null
    private var processing = false

    @Volatile
    var lastResult: String = "Accessibility service is not enabled"
        private set

    internal fun attach(instance: GenericAccessibilityService?) {
        val abandoned: List<PendingUiAction>
        synchronized(lock) {
            service = instance
            if (instance == null) {
                abandoned = listOfNotNull(pending, active)
                pending = null
                active = null
                processing = false
                lastResult = "Accessibility service disconnected"
            } else {
                abandoned = emptyList()
                lastResult = "Accessibility service ready"
            }
        }
        abandoned.forEach { it.result.complete("Accessibility service disconnected; continue manually.") }
    }

    fun isConnected(): Boolean = service != null

    suspend fun submit(command: UiCommand): String {
        val instance: GenericAccessibilityService
        val request = PendingUiAction(command)
        synchronized(lock) {
            instance = service ?: return "Enable MIRA Accessibility in Android Settings first."
            if (processing || pending != null) {
                return "Another accessibility action is pending. Review the current app and try again."
            }
            if (command.expiresAtElapsedRealtime <= SystemClock.elapsedRealtime()) {
                return "That action has expired; nothing was changed."
            }
            pending = request
            lastResult = "Action queued"
        }
        instance.requestProcessing()
        return try {
            withTimeout(10_000L) { request.result.await() }
        } catch (_: TimeoutCancellationException) {
            cancel(request, "No completion was received. Review the app manually before retrying.")
        } catch (cancelled: CancellationException) {
            cancel(request, "UI action cancelled; review the app before retrying.")
            throw cancelled
        }
    }

    internal fun beginProcessing(): PendingUiAction? = synchronized(lock) {
        if (processing) return@synchronized null
        val request = pending ?: return@synchronized null
        pending = null
        active = request
        processing = true
        request
    }

    internal fun finish(request: PendingUiAction, result: String) {
        synchronized(lock) {
            if (active === request) {
                active = null
                processing = false
            }
            if (pending === request) pending = null
            lastResult = result
        }
        request.result.complete(result)
    }

    private fun cancel(request: PendingUiAction, result: String): String {
        synchronized(lock) {
            if (pending === request) pending = null
            if (active === request) {
                active = null
                processing = false
            }
            lastResult = result
        }
        request.result.complete(result)
        return result
    }

    internal fun cancelPending(result: String) {
        val abandoned: List<PendingUiAction>
        synchronized(lock) {
            abandoned = listOfNotNull(pending, active)
            pending = null
            active = null
            lastResult = result
            processing = false
        }
        abandoned.forEach { it.result.complete(result) }
    }
}

class GenericAccessibilityService : AccessibilityService() {
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = serviceInfo.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 150
        }
        AccessibilityController.attach(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Events trigger only an explicit queued command. Never persist event text.
        processPendingCommand()
    }

    override fun onInterrupt() {
        AccessibilityController.cancelPending("Interrupted by Android. Please review the app manually.")
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        AccessibilityController.attach(null)
        super.onDestroy()
    }

    internal fun requestProcessing() {
        mainHandler.post { processPendingCommand() }
    }

    private fun processPendingCommand() {
        val request = AccessibilityController.beginProcessing() ?: return
        val command = request.command
        var result = "Action failed safely. Please continue manually."
        var root: AccessibilityNodeInfo? = null
        try {
            if (command.expiresAtElapsedRealtime <= SystemClock.elapsedRealtime()) {
                result = "Action expired; nothing was changed."
                return
            }
            val activeRoot = rootInActiveWindow
            root = activeRoot
            if (activeRoot == null) {
                result = "No accessible active screen. Complete this step manually."
                return
            }

            val packageName = activeRoot.packageName?.toString().orEmpty()
            if (AppSafetyPolicy.screenSafety(packageName, activeRoot) != ScreenSafety.ORDINARY) {
                result = "Safety checkpoint reached. Complete this screen manually in its app."
                return
            }

            if (command.action == UiAction.CLICK_TEXT &&
                AppSafetyPolicy.isRestrictedActionTarget(command.target)
            ) {
                result = "This action requires user handling and was not clicked."
                return
            }

            if (command.action == UiAction.ENTER_TEXT &&
                (command.text.isBlank() || command.text.length > MAX_ENTRY_CHARS ||
                    command.text.contains('\u0000') ||
                    AppSafetyPolicy.isCredentialLikePayload(command.text) ||
                    AppSafetyPolicy.isRestrictedFieldHint(command.target))
            ) {
                result = "Text entry was rejected by the safety policy."
                return
            }

            val succeeded = when (command.action) {
                UiAction.BACK -> performGlobalAction(GLOBAL_ACTION_BACK)
                UiAction.SCROLL_FORWARD -> scroll(activeRoot, forward = true)
                UiAction.SCROLL_BACKWARD -> scroll(activeRoot, forward = false)
                UiAction.CLICK_TEXT -> clickExactVisibleLabel(activeRoot, command.target)
                UiAction.ENTER_TEXT -> enterTextInUniqueHintField(activeRoot, command.target, command.text)
            }
            result = if (succeeded) {
                "Action performed. Review the app before requesting another action."
            } else {
                "Action not performed. No unique safe target was found."
            }
        } catch (_: SecurityException) {
            result = "Android denied this action. Please continue manually."
        } catch (_: Exception) {
            // Deliberately exclude exception details; platform/app errors may contain data.
            result = "Action failed safely. Please continue manually."
        } finally {
            root?.recycle()
            AccessibilityController.finish(request, result)
        }
    }

    private fun clickExactVisibleLabel(root: AccessibilityNodeInfo, target: String): Boolean {
        val cleanTarget = target.trim()
        if (cleanTarget.isBlank() || cleanTarget.length > MAX_TARGET_CHARS ||
            AppSafetyPolicy.isRestrictedActionTarget(cleanTarget)
        ) return false

        // Resolve label nodes to their nearest clickable ancestor. Deduplicate by the
        // clickable node's resource ID/class/bounds so a Button and its child TextView
        // count as one target, while two separate same-label buttons remain ambiguous.
        val targets = linkedMapOf<String, AccessibilityNodeInfo>()
        collectClickableTargets(root, cleanTarget, 0, targets)
        try {
            if (targets.size != 1) return false
            val targetNode = targets.values.single()
            return targetNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        } finally {
            targets.values.forEach { it.recycle() }
        }
    }

    private fun collectClickableTargets(
        node: AccessibilityNodeInfo,
        target: String,
        depth: Int,
        output: MutableMap<String, AccessibilityNodeInfo>
    ) {
        if (depth > MAX_TREE_DEPTH || output.size > 1) return
        // For editable nodes, inspect only hint text; never inspect the current field value.
        val matched = if (node.isEditable) {
            node.hintText?.toString()?.equals(target, ignoreCase = true) == true
        } else {
            node.text?.toString()?.equals(target, ignoreCase = true) == true ||
                node.contentDescription?.toString()?.equals(target, ignoreCase = true) == true
        }

        if (matched && node.isVisibleToUser && node.isEnabled) {
            var candidate: AccessibilityNodeInfo? = AccessibilityNodeInfo.obtain(node)
            var steps = 0
            while (candidate != null && steps <= MAX_TREE_DEPTH) {
                if (candidate.isVisibleToUser && candidate.isEnabled && candidate.isClickable) {
                    val bounds = Rect()
                    candidate.getBoundsInScreen(bounds)
                    val signature = "${candidate.viewIdResourceName.orEmpty()}|${candidate.className?.toString().orEmpty()}|${bounds.flattenToString()}"
                    if (signature !in output) output[signature] = AccessibilityNodeInfo.obtain(candidate)
                    candidate.recycle()
                    candidate = null
                    break
                }
                val parent = candidate.parent
                candidate.recycle()
                candidate = parent
                steps++
            }
            candidate?.recycle()
        }

        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            try {
                collectClickableTargets(child, target, depth + 1, output)
            } finally {
                child.recycle()
            }
            if (output.size > 1) return
        }
    }

    private fun enterTextInUniqueHintField(
        root: AccessibilityNodeInfo,
        targetHint: String,
        value: String
    ): Boolean {
        val hint = targetHint.trim()
        if (hint.isBlank() || hint.length > MAX_TARGET_CHARS || value.length > MAX_ENTRY_CHARS) return false
        val matches = mutableListOf<AccessibilityNodeInfo>()
        collectEditableHintMatches(root, hint, 0, matches)
        try {
            if (matches.size != 1) return false
            val field = matches.single()
            if (!field.isVisibleToUser || !field.isEnabled || !field.isEditable ||
                AppSafetyPolicy.isPasswordField(field)
            ) return false
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
            }
            return field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        } finally {
            matches.forEach { it.recycle() }
        }
    }

    private fun collectEditableHintMatches(
        node: AccessibilityNodeInfo,
        hint: String,
        depth: Int,
        output: MutableList<AccessibilityNodeInfo>
    ) {
        if (depth > MAX_TREE_DEPTH || output.size > 1) return
        // Do not inspect field.text or contentDescription while seeking text-entry targets.
        if (node.isEditable && node.isVisibleToUser && node.hintText?.toString()
                ?.equals(hint, ignoreCase = true) == true
        ) {
            output.add(AccessibilityNodeInfo.obtain(node))
            if (output.size > 1) return
        }
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            try {
                collectEditableHintMatches(child, hint, depth + 1, output)
            } finally {
                child.recycle()
            }
            if (output.size > 1) return
        }
    }

    private fun scroll(root: AccessibilityNodeInfo, forward: Boolean): Boolean {
        val action = if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        return scrollFirstContainer(root, action, 0)
    }

    private fun scrollFirstContainer(node: AccessibilityNodeInfo, action: Int, depth: Int): Boolean {
        if (depth > MAX_TREE_DEPTH) return false
        if (node.isVisibleToUser && node.isEnabled && node.isScrollable && node.performAction(action)) {
            return true
        }
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            val found = try {
                scrollFirstContainer(child, action, depth + 1)
            } finally {
                child.recycle()
            }
            if (found) return true
        }
        return false
    }

    companion object {
        private const val MAX_TARGET_CHARS = 100
        private const val MAX_ENTRY_CHARS = 500
        private const val MAX_TREE_DEPTH = 14
    }
}

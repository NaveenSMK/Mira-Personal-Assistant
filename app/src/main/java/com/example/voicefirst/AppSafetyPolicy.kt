package com.example.voicefirst

import android.text.InputType
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Conservative screen and target policy for the optional accessibility controller.
 * This is defense in depth, not a guarantee that every app exposes enough semantics
 * to identify every sensitive screen. Known financial packages always require handoff.
 */
object AppSafetyPolicy {
    private val financialPackageHints = listOf(
        "bank", "trading", "broker", "investment", "finance", "wallet",
        "payments", "paytm", "phonepe", "gpay", "google.android.apps.nbu",
        "groww", "zerodha", "upstox", "angelbroking", "kite"
    )

    // Labels that indicate the active page itself needs manual handling. Button labels
    // such as Send/Buy/Submit are handled separately so safe composition/navigation can
    // continue until the user reaches that final-action checkpoint.
    private val checkpointLabels = listOf(
        "password", "passcode", "pin code", "enter pin", "otp", "one time password",
        "verification code", "security code", "recovery phrase", "seed phrase",
        "private key", "two factor", "2fa", "authenticate", "sign in", "log in", "login",
        "unlock", "place order", "market order", "limit order", "confirm trade", "buy order",
        "sell order", "withdraw", "transfer money", "make payment", "pay now", "payment method",
        "bank account", "upi id", "ifsc", "swift code", "credit card", "debit card",
        "investment account", "confirm purchase", "submit payment", "checkout", "stock broker",
        "portfolio", "investment", "banking", "wallet balance", "account balance",
        "transaction history", "money transfer", "place trade", "order type", "trading account",
        "stock trading", "mutual funds", "financial account", "payment history", "account number",
        "sign out", "log out", "logout", "delete account", "factory reset", "reset device",
        "permission request", "grant permission", "allow access", "allow while using"
    )

    // These action labels are never clicked by the generic one-action controller.
    // The user must perform final/irreversible actions directly in the target app.
    private val restrictedActionTargets = listOf(
        "send", "send message", "send money", "submit", "submit payment", "pay", "pay now",
        "transfer", "withdraw", "buy", "sell", "trade", "place order", "confirm",
        "confirm payment", "confirm purchase", "approve", "authorize", "delete", "remove",
        "purchase", "checkout", "payment", "deposit", "add funds", "top up", "cash out",
        "invest", "investment", "execute", "order", "open position", "close position",
        "buy shares", "sell shares", "allow", "grant", "accept", "sign out", "log out", "logout",
        "factory reset", "reset device", "continue", "next", "verify"
    )

    // These hints indicate a field where MIRA must hand off instead of typing. This is
    // intentionally broader than the screen-level labels: e-mail, account, amount, and
    // quantity fields can be ordinary in one app and credential/financial in another.
    private val restrictedFieldHints = listOf(
        "password", "passcode", "pin", "otp", "one time password", "verification", "security code",
        "code", "token", "secret", "credential", "recovery phrase", "seed phrase", "private key",
        "username", "user id", "email", "phone number", "account", "account number", "amount",
        "quantity", "price", "shares", "order", "card number", "cvv", "cvc", "expiry date",
        "routing number", "ifsc", "upi id"
    )

    /**
     * Prevents credential-bearing commands from entering MIRA's transcript, task store, or model loop.
     * Credentials must be entered directly in the target app.
     */
    fun looksLikeCredentialAutomationRequest(raw: String): Boolean {
        val lower = raw.lowercase()
        val credential = Regex("\\b(password|passcode|pin|otp|one[- ]time password|verification code|security code|recovery phrase|seed phrase|private key)\\b")
            .containsMatchIn(lower)
        val directive = Regex("\\b(type|enter|input|fill|paste|submit|provide|use)\\b").containsMatchIn(lower)
        val inlineSecret = Regex("\\b(password|passcode|pin|otp|verification code|security code)\\b\\s*(is|:|=)\\s*\\S+")
            .containsMatchIn(lower)
        val labelledCode = Regex("\\b(pin|otp|passcode|verification code|security code)\\b\\s*(is|:|=)?\\s*\\d{4,8}\\b")
            .containsMatchIn(lower)
        val numericCode = directive && Regex("\\b(type|enter|input|fill|paste)\\b.*\\b\\d{4,8}\\b").containsMatchIn(lower)
        return (credential && (directive || inlineSecret)) || labelledCode || numericCode
    }

    /**
     * Blocks credential-like payloads at the text-entry boundary as defense in depth.
     * Bare 4–8 digit strings are rejected because they can be OTPs/PINs; users must
     * enter those directly in the destination app. This is deliberately conservative.
     */
    fun isCredentialLikePayload(value: String): Boolean {
        val normalized = value.trim()
        if (normalized.isBlank()) return false
        return looksLikeCredentialAutomationRequest(normalized) ||
            Regex("^\\d{4,8}$").matches(normalized) ||
            Regex("\\b(password|passcode|pin|otp|one[- ]time password|verification code|security code|recovery phrase|seed phrase|private key)\\b", RegexOption.IGNORE_CASE)
                .containsMatchIn(normalized)
    }

    fun isFinancialPackage(packageName: String): Boolean {
        val normalized = packageName.lowercase()
        return financialPackageHints.any { normalized.contains(it) }
    }

    fun isRestrictedActionTarget(target: String): Boolean {
        val normalized = normalize(target)
        if (normalized.isBlank()) return true
        return restrictedActionTargets.any { containsPhrase(normalized, it) }
    }

    fun isRestrictedFieldHint(hint: String): Boolean {
        val normalized = normalize(hint)
        return normalized.isNotBlank() && restrictedFieldHints.any { containsPhrase(normalized, it) }
    }

    /** Screen labels are read only for transient policy checks; not logged or persisted. */
    fun isCheckpointLabel(label: String): Boolean {
        val normalized = normalize(label)
        return checkpointLabels.any { containsPhrase(normalized, it) }
    }

    fun screenSafety(packageName: String, root: AccessibilityNodeInfo?): ScreenSafety {
        if (root == null || isFinancialPackage(packageName)) return ScreenSafety.CHECKPOINT
        return if (containsProtectedFieldOrLabel(root, 0)) {
            ScreenSafety.CHECKPOINT
        } else {
            ScreenSafety.ORDINARY
        }
    }

    fun isPasswordField(node: AccessibilityNodeInfo): Boolean {
        if (node.isPassword) return true
        val variation = node.inputType and InputType.TYPE_MASK_VARIATION
        return variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
            variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
    }

    private fun containsProtectedFieldOrLabel(node: AccessibilityNodeInfo, depth: Int): Boolean {
        // Fail closed if the tree is unexpectedly deep; do not assume it is ordinary.
        if (depth > 14) return true
        if (isPasswordField(node)) return true

        // Do not read text/contentDescription from editable nodes: those may contain
        // values typed by the user. Hint text is placeholder metadata, not field value.
        if (node.isEditable) {
            if (isRestrictedFieldHint(node.hintText?.toString().orEmpty())) return true
        } else {
            val label = listOfNotNull(
                node.text?.toString(),
                node.contentDescription?.toString(),
                node.hintText?.toString()
            ).joinToString(" ")
            if (isCheckpointLabel(label)) return true
        }

        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            val found = try {
                containsProtectedFieldOrLabel(child, depth + 1)
            } finally {
                child.recycle()
            }
            if (found) return true
        }
        return false
    }

    private fun containsPhrase(normalized: String, phrase: String): Boolean {
        if (normalized == phrase) return true
        return normalized.startsWith("$phrase ") || normalized.endsWith(" $phrase") ||
            normalized.contains(" $phrase ")
    }

    private fun normalize(value: String): String =
        value.lowercase().replace(Regex("[^a-z0-9]+"), " ").trim().replace(Regex("\\s+"), " ")
}

enum class ScreenSafety { ORDINARY, CHECKPOINT }

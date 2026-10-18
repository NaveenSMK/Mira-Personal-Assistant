package com.example.voicefirst

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSafetyPolicyTest {
    @Test
    fun sensitiveAndFinancialLabelsTriggerCheckpoint() {
        listOf(
            "Enter password", "One time password", "OTP", "Enter PIN",
            "Place order", "Payment method", "Submit payment", "Withdraw", "Portfolio", "Login",
            "Account balance", "Transaction history", "Permission request"
        ).forEach { label -> assertTrue("Expected checkpoint for: $label", AppSafetyPolicy.isCheckpointLabel(label)) }
    }

    @Test
    fun ordinaryNavigationLabelsRemainUsable() {
        listOf("Open Settings", "Search apps", "Photos", "Scroll down", "Help center", "Buy product", "Send feedback", "Email settings")
            .forEach { label -> assertFalse("Unexpected checkpoint for: $label", AppSafetyPolicy.isCheckpointLabel(label)) }
    }

    @Test
    fun restrictedActionTargetsAreAlwaysBlocked() {
        listOf("Send", "Pay now", "Transfer", "Submit", "Buy", "Sell", "Confirm", "Delete")
            .forEach { target -> assertTrue("Expected blocked target: $target", AppSafetyPolicy.isRestrictedActionTarget(target)) }
        listOf("Settings", "Search", "Open photos", "Back")
            .forEach { target -> assertFalse("Unexpected block for: $target", AppSafetyPolicy.isRestrictedActionTarget(target)) }
    }

    @Test
    fun credentialAndFinancialFieldHintsTriggerHandoff() {
        listOf("Email", "Username", "OTP", "Code", "Password", "PIN", "Account number", "Amount", "Quantity", "CVV")
            .forEach { hint -> assertTrue("Expected restricted field hint: $hint", AppSafetyPolicy.isRestrictedFieldHint(hint)) }
        listOf("Search", "Message", "Note title", "Website")
            .forEach { hint -> assertFalse("Unexpected restricted field hint: $hint", AppSafetyPolicy.isRestrictedFieldHint(hint)) }
    }

    @Test
    fun credentialAutomationRequestsAreStoppedBeforePersistence() {
        listOf(
            "enter OTP 123456", "type password: correct-horse", "fill PIN into the field", "PIN 1234",
            "paste verification code 883221", "enter 123456 into the field"
        ).forEach { request ->
            assertTrue("Expected credential gate for: $request", AppSafetyPolicy.looksLikeCredentialAutomationRequest(request))
        }
        listOf(
            "How do I change my password?", "Search for OTP meaning", "Type hello into search",
            "Open the settings screen"
        ).forEach { request ->
            assertFalse("Unexpected credential gate for: $request", AppSafetyPolicy.looksLikeCredentialAutomationRequest(request))
        }
    }

    @Test
    fun credentialLikeTextPayloadsAreBlockedAtEntryBoundary() {
        listOf("1234", "123456", "OTP 123456", "my password is secret", "verification code: 883221")
            .forEach { payload -> assertTrue("Expected sensitive payload block: $payload", AppSafetyPolicy.isCredentialLikePayload(payload)) }
        listOf("Hello there", "Meet me at 6", "Find restaurants nearby")
            .forEach { payload -> assertFalse("Unexpected sensitive payload block: $payload", AppSafetyPolicy.isCredentialLikePayload(payload)) }
    }

    @Test
    fun knownFinancialPackagesAlwaysRequireHandoff() {
        listOf("com.example.mybank", "com.zerodha.kite", "com.google.android.apps.wallet")
            .forEach { pkg -> assertTrue("Expected financial package: $pkg", AppSafetyPolicy.isFinancialPackage(pkg)) }
        assertFalse(AppSafetyPolicy.isFinancialPackage("com.android.settings"))
    }
}

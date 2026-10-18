package com.example.voicefirst

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenContextPolicyTest {
    @Test fun redactsContextualOtpButPreservesPostalPinCode() {
        val result = ScreenContextPolicy.sanitize("OTP: 123456 Delivery to Chennai 600001")
        assertFalse(result.contains("123456"))
        assertTrue(result.contains("600001"))
    }

    @Test fun redactsPinAndPasscodeValues() {
        val result = ScreenContextPolicy.sanitize("PIN: 1234 Passcode: correct-horse OTP: 883221")
        assertFalse(result.contains("1234"))
        assertFalse(result.contains("correct-horse"))
        assertFalse(result.contains("883221"))
    }

    @Test fun redactsEmailAddress() {
        val result = ScreenContextPolicy.sanitize("Contact person@example.com for help")
        assertFalse(result.contains("person@example.com"))
    }

    @Test fun boundsTextLength() {
        assertTrue(ScreenContextPolicy.sanitize("x".repeat(20_000)).length <= ScreenContextStore.MAX_TEXT_CHARS)
    }
}

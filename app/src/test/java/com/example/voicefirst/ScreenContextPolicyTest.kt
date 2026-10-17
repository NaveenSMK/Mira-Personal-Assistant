package com.example.voicefirst

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenContextPolicyTest {
    @Test fun redactsOtpAndPasswordLikeContent() {
        val result = ScreenContextPolicy.sanitize("Verification code: 123456 Password: hunter2")
        assertFalse(result.contains("123456"))
        assertFalse(result.contains("hunter2"))
        assertTrue(result.contains("[REDACTED]"))
    }

    @Test fun redactsEmailAddress() {
        val result = ScreenContextPolicy.sanitize("Contact person@example.com for help")
        assertFalse(result.contains("person@example.com"))
    }

    @Test fun boundsTextLength() {
        assertTrue(ScreenContextPolicy.sanitize("x".repeat(20_000)).length <= ScreenContextStore.MAX_TEXT_CHARS)
    }
}

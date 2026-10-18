#!/usr/bin/env python3
from pathlib import Path
import sys
root = Path(__file__).resolve().parents[1]
checks = {
    "screen store does not retain Bitmap": "Bitmap" not in (root / "app/src/main/java/com/example/voicefirst/ScreenContextStore.kt").read_text(),
    "screen context TTL": "TTL_MILLIS" in (root / "app/src/main/java/com/example/voicefirst/ScreenContextStore.kt").read_text(),
    "bounded context": "MAX_TEXT_CHARS = 4_000" in (root / "app/src/main/java/com/example/voicefirst/ScreenContextStore.kt").read_text(),
    "sanitizer present": (root / "app/src/main/java/com/example/voicefirst/ScreenContextPolicy.kt").exists(),
    "credential filtering present": "credentialLike" in (root / "app/src/main/java/com/example/voicefirst/VoiceFirstVoiceInteractionSession.kt").read_text(),
    "approval defense in depth": "action.risk != RiskLevel.LOW" in (root / "app/src/main/java/com/example/voicefirst/AgentRuntime.kt").read_text(),
    "untrusted screen/OCR prompt": "screen text, OCR" in (root / "app/src/main/java/com/example/voicefirst/AgentOrchestrator.kt").read_text(),
    "sanitizer tests present": (root / "app/src/test/java/com/example/voicefirst/ScreenContextPolicyTest.kt").exists(),
    "assist context skips pin/passcode hints": 'hints.contains("pin")' in (root / "app/src/main/java/com/example/voicefirst/VoiceFirstVoiceInteractionSession.kt").read_text() and 'hints.contains("passcode")' in (root / "app/src/main/java/com/example/voicefirst/VoiceFirstVoiceInteractionSession.kt").read_text(),
    "backup disabled": 'android:allowBackup="false"' in (root / "app/src/main/AndroidManifest.xml").read_text(),
    "accessibility service requires OS binding permission": 'android:name=".GenericAccessibilityService"' in (root / "app/src/main/AndroidManifest.xml").read_text() and 'android.permission.BIND_ACCESSIBILITY_SERVICE' in (root / "app/src/main/AndroidManifest.xml").read_text(),
    "financial app checkpoint policy": 'isFinancialPackage(packageName)' in (root / "app/src/main/java/com/example/voicefirst/AppSafetyPolicy.kt").read_text(),
    "restricted target labels blocked": 'restrictedActionTargets' in (root / "app/src/main/java/com/example/voicefirst/AppSafetyPolicy.kt").read_text(),
    "UI actions expire": 'expiresAtElapsedRealtime' in (root / "app/src/main/java/com/example/voicefirst/GenericAccessibilityService.kt").read_text(),
    "UI automation not given gesture injection": 'android:canPerformGestures="false"' in (root / "app/src/main/res/xml/mira_accessibility_service.xml").read_text() and 'dispatchGesture(' not in (root / "app/src/main/java/com/example/voicefirst/GenericAccessibilityService.kt").read_text(),
    "credential commands rejected before storage": 'looksLikeCredentialAutomationRequest(text)' in (root / "app/src/main/java/com/example/voicefirst/MainActivity.kt").read_text(),
    "credential-like entered payloads blocked": 'isCredentialLikePayload' in (root / "app/src/main/java/com/example/voicefirst/AgentRuntime.kt").read_text() and 'isCredentialLikePayload(command.text)' in (root / "app/src/main/java/com/example/voicefirst/GenericAccessibilityService.kt").read_text(),
    "UI-entered text redacted from agent history": '[UI text omitted from agent history]' in (root / "app/src/main/java/com/example/voicefirst/AgentRuntime.kt").read_text(),
    "credential policy unit tests": (root / "app/src/test/java/com/example/voicefirst/AppSafetyPolicyTest.kt").exists(),
}
for name, ok in checks.items(): print(("PASS " if ok else "FAIL ") + name)
failed = [name for name, ok in checks.items() if not ok]
print(f"{len(checks)-len(failed)}/{len(checks)} source checks passed")
sys.exit(1 if failed else 0)

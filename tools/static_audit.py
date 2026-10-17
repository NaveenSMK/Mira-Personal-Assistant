#!/usr/bin/env python3
"""Lightweight source-only safety checks for VoiceFirst; not a replacement for Android Lint."""
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "app/src/main/java/com/example/voicefirst"
checks: list[tuple[str, bool, str]] = []

def check(name: str, ok: bool, detail: str = "") -> None:
    checks.append((name, ok, detail))

manifest_path = ROOT / "app/src/main/AndroidManifest.xml"
manifest = manifest_path.read_text(encoding="utf-8")
try:
    ET.parse(manifest_path)
    check("Manifest XML parses", True)
except ET.ParseError as exc:
    check("Manifest XML parses", False, str(exc))
check("Android backup disabled", 'android:allowBackup="false"' in manifest)
check("Cleartext traffic disabled", 'android:usesCleartextTraffic="false"' in manifest)
try:
    parsed_manifest = ET.parse(manifest_path).getroot()
    android_ns = "{http://schemas.android.com/apk/res/android}"
    exported_activities = [
        node for node in parsed_manifest.findall("application/activity")
        if node.get(android_ns + "exported") == "true"
    ]
    exported_services = [
        node for node in parsed_manifest.findall("application/service")
        if node.get(android_ns + "exported") == "true"
    ]
    only_launcher_exported = len(exported_activities) == 1 and exported_activities[0].get(android_ns + "name") == ".MainActivity"
    services_system_bound = all(
        (
            node.get(android_ns + "name") == ".VoiceFirstRecognitionService"
            and node.get(android_ns + "permission") == "android.permission.BIND_SPEECH_RECOGNITION_SERVICE"
        ) or (
            node.get(android_ns + "name") in (".VoiceFirstVoiceInteractionService", ".VoiceFirstVoiceInteractionSessionService")
            and node.get(android_ns + "permission") == "android.permission.BIND_VOICE_INTERACTION"
        )
        for node in exported_services
    )
    check("Only launcher activity is exported", only_launcher_exported)
    check("Exported assistant services are system-bound", len(exported_services) == 3 and services_system_bound)
except Exception as exc:
    check("Only launcher activity is exported", False, str(exc))

registry = (SRC / "AgentToolRegistry.kt").read_text(encoding="utf-8")
runtime = (SRC / "AgentRuntime.kt").read_text(encoding="utf-8")
main = (SRC / "MainActivity.kt").read_text(encoding="utf-8")
router = (SRC / "AndroidActionRouter.kt").read_text(encoding="utf-8")
all_source = "\n".join(p.read_text(encoding="utf-8") for p in SRC.glob("*.kt"))
tools = re.findall(r'AgentAction\("([a-z_.]+)"', registry)
check("Tool registry is nonempty", bool(tools))
missing = [tool for tool in tools if f'"{tool}"' not in runtime + main + router]
check("Every registered tool has a dispatch/validation reference", not missing, ", ".join(missing))
for tool in ("phone.dial_contact", "calendar.create", "gmail.search_unread", "gmail.create_draft", "memory.forget"):
    line = next((row.strip() for row in registry.splitlines() if f'AgentAction("{tool}"' in row), "")
    check(f"Confirmation policy: {tool}", "true" in line)
check("Tool loop bounded", "maxToolCalls: Int = 5" in runtime and "calls >= maxToolCalls" in runtime)
check("Model inference timeout configured", "withTimeout(120_000L)" in runtime)
check("Tool execution timeout configured", "withTimeout(60_000L)" in runtime)
for forbidden in ("Runtime.getRuntime().exec", "ProcessBuilder(", "DexClassLoader", "ScriptEngineManager", "eval("):
    check(f"No forbidden dynamic execution: {forbidden}", forbidden not in all_source)
check("Model unload is suspend/serialized", "override suspend fun unload()" in all_source and "override fun unload()" not in all_source)
check("Default assistant service is declared", "VoiceFirstVoiceInteractionService" in manifest and "android.service.voice.VoiceInteractionService" in manifest)
check("Assist text callback implemented", "onHandleAssist(data: Bundle?, structure: AssistStructure?" in (SRC / "VoiceFirstVoiceInteractionSession.kt").read_text(encoding="utf-8"))
check("Assistant session service is declared", "VoiceFirstVoiceInteractionSessionService" in manifest)
check("Recognition service metadata is present", "VoiceFirstRecognitionService" in (ROOT / "app/src/main/res/xml/voice_interaction_service.xml").read_text(encoding="utf-8"))
check("Screen context is bounded", "MAX_TEXT_CHARS = 8_000" in (SRC / "ScreenContextStore.kt").read_text(encoding="utf-8"))
check("Screen context has clear operation", "ScreenContextStore.clear()" in main)
check("Screen text treated as untrusted prompt data", "untrusted app content" in main)
check("No AccessibilityService registered", "android.accessibilityservice.AccessibilityService" not in manifest)

imbalances = []
for path in SRC.glob("*.kt"):
    text = path.read_text(encoding="utf-8")
    for label, opening, closing in (("braces", "{", "}"), ("parentheses", "(", ")"), ("brackets", "[", "]")):
        if text.count(opening) != text.count(closing):
            imbalances.append(f"{path.name}: {label}")
check("Coarse delimiter balance", not imbalances, ", ".join(imbalances))

failed = 0
for name, ok, detail in checks:
    print(("PASS" if ok else "FAIL") + " | " + name + (f" | {detail}" if detail else ""))
    failed += not ok
print(f"\n{len(checks) - failed}/{len(checks)} checks passed")
sys.exit(1 if failed else 0)

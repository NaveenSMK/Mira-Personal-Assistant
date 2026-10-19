package com.naveensmk.mira

/** Lightweight host-side smoke checks for the Android-free routing/context components. */
fun main() {
    check(AndroidIntentParser.parse("open browser and search for Cricbuzz") == ParsedAndroidIntent(AndroidIntentAction.WEB_SEARCH, "Cricbuzz"))
    check(AndroidIntentParser.parse("search maps for petrol stations") == ParsedAndroidIntent(AndroidIntentAction.MAPS_SEARCH, "petrol stations"))
    check(AndroidIntentParser.parse("open YouTube app and search for Tamil songs") == ParsedAndroidIntent(AndroidIntentAction.YOUTUBE_SEARCH, "Tamil songs"))
    check(AndroidIntentParser.parse("open YouTube app")?.action == AndroidIntentAction.OPEN_YOUTUBE)
    check(AndroidIntentParser.parse("call my wife")?.action == AndroidIntentAction.CALL_CONTACT)
    check(AndroidIntentParser.parse("open contacts and search for wife") == ParsedAndroidIntent(AndroidIntentAction.CONTACT_SEARCH, "wife"))
    check(AndroidIntentParser.parse("search for Cricbuzz")?.action == AndroidIntentAction.WEB_SEARCH)

    ScreenContextStore.clear()
    val session = ScreenContextStore.beginCapture()
    ScreenContextStore.updateAssistText("Tamil assist text: வணக்கம்", session)
    val oldOcr = ScreenContextStore.updateScreenshot()
    val currentOcr = ScreenContextStore.updateScreenshot()
    ScreenContextStore.updateOcrText("stale screenshot", oldOcr)
    ScreenContextStore.updateOcrText("current screenshot", currentOcr)
    check(ScreenContextStore.snapshotText().contains("வணக்கம்"))
    check(ScreenContextStore.snapshotText().contains("current screenshot"))
    check(!ScreenContextStore.snapshotText().contains("stale screenshot"))
    val newSession = ScreenContextStore.beginCapture()
    ScreenContextStore.updateAssistText("old session callback", session)
    check(!ScreenContextStore.snapshotText().contains("old session callback"))
    ScreenContextStore.updateAssistText("new session", newSession)
    check(ScreenContextStore.snapshotText().contains("new session"))
    ScreenContextStore.clear()
    println("PASS: 12 pure Kotlin routing/context assertions")
}

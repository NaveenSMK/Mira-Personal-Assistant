package com.naveensmk.mira

import java.util.Locale

/** Pure phrase-to-action mapping. Kept separate from Android APIs so routing rules are testable. */
enum class AndroidIntentAction {
    OPEN_BROWSER,
    WEB_SEARCH,
    OPEN_YOUTUBE,
    YOUTUBE_SEARCH,
    OPEN_CONTACTS,
    CONTACT_SEARCH,
    CALL_CONTACT,
    MAPS_DIRECTIONS,
    MAPS_SEARCH,
    SET_TIMER,
    SET_ALARM,
    CREATE_CALENDAR_EVENT,
    SHOW_ALARMS
}

data class ParsedAndroidIntent(val action: AndroidIntentAction, val query: String = "")

object AndroidIntentParser {
    fun parse(raw: String): ParsedAndroidIntent? {
        val typed = raw.trim().trimEnd('.', '?', '!')
        if (typed.isBlank()) return null
        // Strip common speech politeness prefixes before deterministic intent matching.
        var original = typed
        val politePrefix = Regex("(?i)^(?:(?:can|could|would) you please\\s+|(?:can|could|would) you\\s+|please\\s+)")
        repeat(2) { original = original.replace(politePrefix, "").trim() }
        val lower = original.lowercase(Locale.ROOT)

        contactQuery(lower)?.let { return ParsedAndroidIntent(if (it.first) AndroidIntentAction.CALL_CONTACT else AndroidIntentAction.CONTACT_SEARCH, it.second) }
        if (lower == "open contacts" || lower == "open contacts app") return ParsedAndroidIntent(AndroidIntentAction.OPEN_CONTACTS)

        val webSearchPrefixes = listOf(
            "open browser and do a search for ",
            "open browser and search the web for ",
            "open browser and search for ",
            "open browser and search ",
            "open chrome and search for ",
            "browser search for ",
            "search the web for ",
            "search web for ",
            "google search for ",
            "search on the web for ",
            "search the internet for "
        )
        findAfterPrefix(original, lower, webSearchPrefixes)?.let { return ParsedAndroidIntent(AndroidIntentAction.WEB_SEARCH, it) }
        if (lower == "open browser" || lower == "open web browser" || lower == "open chrome" || lower == "launch browser" || lower == "open browser and do a search" || lower == "open browser and search") {
            return ParsedAndroidIntent(AndroidIntentAction.OPEN_BROWSER)
        }
        suffixQuery(original, lower, "search for ", " on the web")?.let { return ParsedAndroidIntent(AndroidIntentAction.WEB_SEARCH, it) }

        val youtubePrefixes = listOf(
            "open youtube app and search for ",
            "open youtube and search for ",
            "open youtube app and search ",
            "open youtube and search ",
            "search youtube for ",
            "search youtube ",
            "play on youtube ",
            "find on youtube "
        )
        findAfterPrefix(original, lower, youtubePrefixes)?.let { return ParsedAndroidIntent(AndroidIntentAction.YOUTUBE_SEARCH, it) }
        suffixQuery(original, lower, "search for ", " on youtube")?.let { return ParsedAndroidIntent(AndroidIntentAction.YOUTUBE_SEARCH, it) }
        if (lower == "open youtube" || lower == "open youtube app" || lower == "launch youtube") return ParsedAndroidIntent(AndroidIntentAction.OPEN_YOUTUBE)

        if (lower.startsWith("navigate to ") || lower.startsWith("directions to ")) {
            return ParsedAndroidIntent(AndroidIntentAction.MAPS_DIRECTIONS, original.substringAfter(" to ", "").trim())
        }
        findAfterPrefix(original, lower, listOf("open maps and search for ", "search maps for ", "find on maps "))?.let {
            return ParsedAndroidIntent(AndroidIntentAction.MAPS_SEARCH, it)
        }
        suffixQuery(original, lower, "search for ", " on maps")?.let { return ParsedAndroidIntent(AndroidIntentAction.MAPS_SEARCH, it) }
        if (lower == "open maps") return ParsedAndroidIntent(AndroidIntentAction.MAPS_SEARCH)

        if (Regex("^(set|start|create) (?:a )?timer\\b").containsMatchIn(lower) || lower.startsWith("timer for ")) {
            return ParsedAndroidIntent(AndroidIntentAction.SET_TIMER, original)
        }
        if (Regex("^set (?:an? )?alarm\\b").containsMatchIn(lower) || lower.startsWith("wake me at ")) {
            return ParsedAndroidIntent(AndroidIntentAction.SET_ALARM, original)
        }
        if (lower.startsWith("create calendar event ") || lower.startsWith("add calendar event ")) {
            return ParsedAndroidIntent(AndroidIntentAction.CREATE_CALENDAR_EVENT, original.substringAfter("event ", "").trim())
        }
        if (lower == "show my alarms" || lower == "open alarms") return ParsedAndroidIntent(AndroidIntentAction.SHOW_ALARMS)
        if (lower.startsWith("search for ")) return ParsedAndroidIntent(AndroidIntentAction.WEB_SEARCH, original.drop("search for ".length).trim())
        return null
    }

    private fun contactQuery(lower: String): Pair<Boolean, String>? {
        val patterns = listOf(
            Regex("^open contacts(?: app)? and search for (.+)$") to false,
            Regex("^search contacts for (.+)$") to false,
            Regex("^find contact (.+)$") to false,
            Regex("^(?:call|phone|dial) (?:my )?(.+)$") to true,
            Regex("^make (?:a )?call to (?:my )?(.+)$") to true,
            Regex("^place (?:a )?call to (?:my )?(.+)$") to true
        )
        for ((pattern, isCall) in patterns) {
            val match = pattern.matchEntire(lower)
            if (match != null) return isCall to match.groupValues[1].trim()
        }
        return null
    }

    private fun findAfterPrefix(original: String, lower: String, prefixes: List<String>): String? {
        val prefix = prefixes.firstOrNull(lower::startsWith) ?: return null
        return original.drop(prefix.length).trim()
    }

    private fun suffixQuery(original: String, lower: String, prefix: String, suffix: String): String? {
        if (!lower.startsWith(prefix) || !lower.endsWith(suffix)) return null
        val query = original.substring(prefix.length, original.length - suffix.length).trim()
        return query.takeIf { it.isNotBlank() }
    }
}

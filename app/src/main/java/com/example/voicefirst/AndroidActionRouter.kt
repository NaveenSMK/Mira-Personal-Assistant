package com.example.voicefirst

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.widget.Toast
import java.util.Calendar
import java.util.regex.Pattern

/**
 * Safe first-pass Android system integrations.
 *
 * These are deterministic phrase handlers, not the final natural-language parser.
 * They use implicit intents so Android can choose the installed handler. Phone calls
 * open the dialer rather than placing a call directly.
 */
class AndroidActionRouter(private val activity: Activity) {
    companion object {
        const val REQUEST_PICK_PHONE = 7410
    }


    /** Execute only validated, named tools. Arguments are never interpreted as arbitrary intents. */
    fun executeTool(actionId: String, args: Map<String, String>): String {
        return try {
            when (actionId) {
                "alarm.set" -> {
                    val parts = args.getValue("time").split(":")
                    val hour = parts[0].toInt(); val minute = parts[1].toInt()
                    val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                        putExtra(AlarmClock.EXTRA_HOUR, hour); putExtra(AlarmClock.EXTRA_MINUTES, minute)
                        putExtra(AlarmClock.EXTRA_MESSAGE, "VoiceFirst alarm"); putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                    }
                    if (!canHandle(intent)) "No compatible alarm app is installed." else { activity.startActivity(intent); "Opened alarm editor for ${args.getValue("time")}. Please review and confirm." }
                }
                "timer.set" -> {
                    val amount = args.getValue("duration").toInt()
                    val seconds = amount * when (args.getValue("unit")) { "seconds" -> 1; "minutes" -> 60; else -> 3600 }
                    val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                        putExtra(AlarmClock.EXTRA_LENGTH, seconds); putExtra(AlarmClock.EXTRA_MESSAGE, "VoiceFirst timer"); putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                    }
                    if (!canHandle(intent)) "No compatible timer app is installed." else { activity.startActivity(intent); "Opened timer for $amount ${args.getValue("unit")}." }
                }
                "maps.directions" -> openDirections(args.getValue("destination"))
                "maps.search" -> openMapSearch(args.getValue("query"))
                "youtube.search" -> openYouTube(args.getValue("query"))
                "calendar.create" -> createCalendarEvent(args.getValue("title"))
                "phone.dial_contact" -> pickContactForCall()
                else -> "This Android tool is not implemented."
            }
        } catch (e: Exception) {
            "Tool failed: ${e.message ?: e.javaClass.simpleName}"
        }
    }

    fun tryHandle(raw: String): String? {
        val text = raw.trim()
        val lower = text.lowercase()

        return try {
            when {
                isTimerRequest(lower) -> setTimer(text, lower)
                isAlarmRequest(lower) -> setAlarm(text, lower)
                isCallRequest(lower) -> pickContactForCall()
                lower.startsWith("navigate to ") || lower.startsWith("directions to ") ->
                    openDirections(text.substringAfter(" to ", "").trim())
                lower.startsWith("open maps") || lower.startsWith("search maps for ") ||
                    lower.startsWith("find on maps ") -> openMapSearch(extractMapQuery(text, lower))
                lower.startsWith("search youtube for ") || lower.startsWith("play on youtube ") ||
                    lower.startsWith("find on youtube ") -> openYouTube(extractYouTubeQuery(text, lower))
                lower.startsWith("create calendar event ") || lower.startsWith("add calendar event ") ->
                    createCalendarEvent(text.substringAfter("event ", "").trim())
                lower == "show my alarms" || lower == "open alarms" -> showAlarms()
                else -> null
            }
        } catch (_: ActivityNotFoundException) {
            "I couldn't find an app that can handle that action."
        } catch (_: Exception) {
            "I couldn't complete that action. Please try again."
        }
    }

    private fun isTimerRequest(lower: String) =
        lower.startsWith("set timer") || lower.startsWith("start timer") || lower.startsWith("timer for ")

    private fun setTimer(text: String, lower: String): String? {
        val match = Regex("""(\d+)\s*(seconds?|secs?|minutes?|mins?|hours?|hrs?)""").find(lower)
            ?: return "Tell me the timer duration, for example: set a timer for 5 minutes."
        val amount = match.groupValues[1].toIntOrNull() ?: return "I couldn't understand that timer duration."
        val unit = match.groupValues[2]
        val multiplier = when {
            unit.startsWith("sec") -> 1
            unit.startsWith("min") -> 60
            else -> 3600
        }
        val seconds = (amount.toLong() * multiplier).coerceIn(1L, 24L * 60L * 60L).toInt()
        val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, seconds)
            putExtra(AlarmClock.EXTRA_MESSAGE, "VoiceFirst timer")
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
        }
        if (!canHandle(intent)) return "No compatible timer app is installed."
        activity.startActivity(intent)
        return "Opening your timer for $amount $unit."
    }

    private fun isAlarmRequest(lower: String) =
        lower.startsWith("set alarm") || lower.startsWith("wake me at ")

    private fun setAlarm(text: String, lower: String): String {
        val match = Regex("""(\d{1,2})(?::(\d{2}))?\s*(am|pm)?""").find(lower)
            ?: return "Tell me the time, for example: set an alarm for 6:30 AM."
        var hour = match.groupValues[1].toIntOrNull() ?: return "I couldn't understand that alarm time."
        val minute = match.groupValues[2].ifBlank { "0" }.toIntOrNull() ?: 0
        val meridiem = match.groupValues[3]
        if (minute !in 0..59 || hour !in 0..23) return "That doesn't look like a valid time."
        if (meridiem == "pm" && hour in 1..11) hour += 12
        if (meridiem == "am" && hour == 12) hour = 0
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            putExtra(AlarmClock.EXTRA_MESSAGE, "VoiceFirst alarm")
        }
        if (!canHandle(intent)) return "No compatible alarm app is installed."
        activity.startActivity(intent)
        return "Opening the alarm app. Please confirm the time."
    }

    private fun isCallRequest(lower: String) =
        lower.startsWith("call ") || lower.startsWith("phone ") || lower.startsWith("dial ")

    private fun pickContactForCall(): String {
        val intent = Intent(Intent.ACTION_PICK).apply {
            type = ContactsContract.CommonDataKinds.Phone.CONTENT_TYPE
        }
        if (!canHandle(intent)) return "No contacts app is available."
        activity.startActivityForResult(intent, REQUEST_PICK_PHONE)
        return "Choose the contact to call. I'll open the dialer so you can confirm before calling."
    }

    private fun openDirections(destination: String): String {
        if (destination.isBlank()) return "Where would you like directions to?"
        val url = "https://www.google.com/maps/dir/?api=1&destination=${Uri.encode(destination)}"
        openUrl(url)
        return "Opening directions to $destination."
    }

    private fun extractMapQuery(text: String, lower: String): String {
        return when {
            lower.startsWith("search maps for ") -> text.substringAfter("for ", text, ignoreCase = true).trim()
            lower.startsWith("find on maps ") -> text.removePrefix("Find on Maps ").removePrefix("find on maps ").trim()
            else -> ""
        }
    }

    private fun openMapSearch(query: String): String {
        if (query.isBlank()) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q="))
            if (!canHandle(intent)) return "No compatible maps app is installed."
            activity.startActivity(intent)
            return "Opening Maps."
        }
        openUrl("https://www.google.com/maps/search/?api=1&query=${Uri.encode(query)}")
        return "Searching Maps for $query."
    }

    private fun extractYouTubeQuery(text: String, lower: String): String = when {
        lower.startsWith("search youtube for ") -> text.substringAfter("for ", text, ignoreCase = true).trim()
        lower.startsWith("play on youtube ") -> text.substringAfter("youtube ", text, ignoreCase = true).trim()
        lower.startsWith("find on youtube ") -> text.substringAfter("youtube ", text, ignoreCase = true).trim()
        else -> ""
    }

    private fun openYouTube(query: String): String {
        if (query.isBlank()) return "What would you like to search for on YouTube?"
        openUrl("https://www.youtube.com/results?search_query=${Uri.encode(query)}")
        return "Opening YouTube results for $query."
    }

    private fun createCalendarEvent(title: String): String {
        if (title.isBlank()) return "Tell me the event title, for example: create calendar event Dentist appointment."
        val start = Calendar.getInstance().apply { add(Calendar.HOUR_OF_DAY, 1); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
        val end = (start.clone() as Calendar).apply { add(Calendar.HOUR_OF_DAY, 1) }
        val intent = Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI).apply {
            putExtra(CalendarContract.Events.TITLE, title)
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start.timeInMillis)
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, end.timeInMillis)
        }
        if (!canHandle(intent)) return "No compatible calendar app is installed."
        activity.startActivity(intent)
        return "Opening a calendar event for $title. Review the date and time before saving."
    }

    private fun showAlarms(): String {
        val intent = Intent(AlarmClock.ACTION_SHOW_ALARMS)
        if (!canHandle(intent)) return "Your alarm app doesn't support opening the alarm list."
        activity.startActivity(intent)
        return "Opening your alarms."
    }

    private fun openUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        if (!canHandle(intent)) throw ActivityNotFoundException()
        activity.startActivity(intent)
    }

    private fun canHandle(intent: Intent) = intent.resolveActivity(activity.packageManager) != null
}

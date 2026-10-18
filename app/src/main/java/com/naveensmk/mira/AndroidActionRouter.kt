package com.naveensmk.mira

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import java.util.Calendar
import java.util.Locale

/**
 * Deterministic Android action router. Explicit app/search phrases are resolved here
 * before the model planner runs, so a browser request cannot silently become a Maps
 * search. External actions use bounded, well-formed intents and report launch outcomes.
 */
class AndroidActionRouter(private val activity: Activity) {
    data class PhoneContact(val name: String, val number: String)

    fun needsContactsPermission(raw: String): Boolean {
        return AndroidIntentParser.parse(raw)?.action in setOf(AndroidIntentAction.CONTACT_SEARCH, AndroidIntentAction.CALL_CONTACT)
    }

    fun needsCallPermission(raw: String): Boolean =
        AndroidIntentParser.parse(raw)?.action == AndroidIntentAction.CALL_CONTACT

    fun executeTool(actionId: String, args: Map<String, String>): String = try {
        when (actionId) {
            "alarm.set" -> {
                val parts = args.getValue("time").split(":")
                val hour = parts[0].toInt(); val minute = parts[1].toInt()
                val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                    putExtra(AlarmClock.EXTRA_HOUR, hour)
                    putExtra(AlarmClock.EXTRA_MINUTES, minute)
                    putExtra(AlarmClock.EXTRA_MESSAGE, "MIRA alarm")
                    putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                }
                launch(intent, "No compatible alarm app is installed.", "Opened the alarm editor for ${args.getValue("time")}. Review and confirm the alarm.")
            }
            "timer.set" -> {
                val amount = args.getValue("duration").toInt()
                val seconds = amount * when (args.getValue("unit")) { "seconds" -> 1; "minutes" -> 60; else -> 3600 }
                val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                    putExtra(AlarmClock.EXTRA_LENGTH, seconds)
                    putExtra(AlarmClock.EXTRA_MESSAGE, "MIRA timer")
                    putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                }
                launch(intent, "No compatible timer app is installed.", "Opened the timer for $amount ${args.getValue("unit")}.")
            }
            "browser.open" -> openBrowserHome()
            "browser.search" -> openBrowserSearch(args["query"].orEmpty())
            "youtube.open" -> openYouTubeHome()
            "youtube.search" -> openYouTube(args["query"].orEmpty())
            "maps.directions" -> openDirections(args.getValue("destination"))
            "maps.search" -> openMapSearch(args.getValue("query"))
            "calendar.create" -> createCalendarEvent(args.getValue("title"))
            "phone.dial_contact" -> {
                val query = args["query"] ?: args["name"] ?: args["contact"] ?: args["key"].orEmpty()
                if (query.isBlank()) "Tell me the contact name or relationship you want to call."
                else findContact(query, startDialer = true)
            }
            else -> "This Android tool is not implemented."
        }
    } catch (e: ActivityNotFoundException) {
        "No compatible app is installed to complete that action."
    } catch (e: SecurityException) {
        "Android denied this action. Check the relevant permission in Settings."
    } catch (e: Exception) {
        "Tool failed safely: ${e.message ?: e.javaClass.simpleName}"
    }

    fun tryHandle(raw: String): String? {
        val intent = AndroidIntentParser.parse(raw) ?: return null
        return try {
            when (intent.action) {
                AndroidIntentAction.OPEN_BROWSER -> openBrowserHome()
                AndroidIntentAction.WEB_SEARCH -> openBrowserSearch(intent.query)
                AndroidIntentAction.OPEN_YOUTUBE -> openYouTubeHome()
                AndroidIntentAction.YOUTUBE_SEARCH -> openYouTube(intent.query)
                AndroidIntentAction.OPEN_CONTACTS -> openContacts()
                AndroidIntentAction.CONTACT_SEARCH -> findContact(intent.query, startDialer = false)
                AndroidIntentAction.CALL_CONTACT -> findContact(intent.query, startDialer = true)
                AndroidIntentAction.MAPS_DIRECTIONS -> openDirections(intent.query)
                AndroidIntentAction.MAPS_SEARCH -> openMapSearch(intent.query)
                AndroidIntentAction.SET_TIMER -> setTimer(intent.query.lowercase(Locale.ROOT))
                AndroidIntentAction.SET_ALARM -> setAlarm(intent.query.lowercase(Locale.ROOT))
                AndroidIntentAction.CREATE_CALENDAR_EVENT -> createCalendarEvent(intent.query)
                AndroidIntentAction.SHOW_ALARMS -> showAlarms()
            }
        } catch (e: ActivityNotFoundException) {
            "I couldn't find an installed app that can handle that action."
        } catch (e: SecurityException) {
            "Android denied that action. Check the relevant permission in Settings."
        } catch (e: Exception) {
            "I couldn't complete that action safely: ${e.message ?: "unknown error"}"
        }
    }

    private fun openBrowserHome(): String {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/")).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
        }
        if (!canHandle(intent)) return "No compatible web browser is installed."
        activity.startActivity(intent)
        return "Opened a web page in your default browser."
    }

    private fun openBrowserSearch(query: String): String {
        if (query.isBlank()) return "What would you like to search for on the web?"
        val url = "https://www.google.com/search?q=${Uri.encode(query)}"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply { addCategory(Intent.CATEGORY_BROWSABLE) }
        if (!canHandle(intent)) return "No compatible browser is installed to search for $query."
        activity.startActivity(intent)
        return "Opened web search results for $query in your default browser."
    }

    private fun openYouTubeHome(): String {
        val launchIntent = runCatching { activity.packageManager.getLaunchIntentForPackage("com.google.android.youtube") }.getOrNull()
        if (launchIntent != null) {
            activity.startActivity(launchIntent)
            return "Opened the YouTube app."
        }
        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/")).apply { addCategory(Intent.CATEGORY_BROWSABLE) }
        if (!canHandle(browserIntent)) return "YouTube is not installed and no compatible browser is available."
        activity.startActivity(browserIntent)
        return "YouTube app was not found; opened YouTube in your default browser instead."
    }

    private fun openYouTube(query: String): String {
        if (query.isBlank()) return "What would you like to search for on YouTube?"
        val url = Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(query)}")
        val appIntent = Intent(Intent.ACTION_VIEW, url).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            setPackage("com.google.android.youtube")
        }
        val browserIntent = Intent(Intent.ACTION_VIEW, url).apply { addCategory(Intent.CATEGORY_BROWSABLE) }
        return when {
            canHandle(appIntent) -> {
                activity.startActivity(appIntent)
                "Opened YouTube search results for $query in the YouTube app."
            }
            canHandle(browserIntent) -> {
                activity.startActivity(browserIntent)
                "YouTube app is unavailable; opened YouTube search results for $query in your browser."
            }
            else -> "Neither the YouTube app nor a compatible browser is available."
        }
    }

    private fun isTimerRequest(lower: String) = lower.startsWith("set timer") || lower.startsWith("start timer") || lower.startsWith("timer for ")

    private fun setTimer(lower: String): String {
        val match = Regex("""(\d+)\s*(seconds?|secs?|minutes?|mins?|hours?|hrs?)""").find(lower)
            ?: return "Tell me the timer duration, for example: set a timer for 5 minutes."
        val amount = match.groupValues[1].toIntOrNull() ?: return "I couldn't understand that timer duration."
        val unit = match.groupValues[2]
        val multiplier = when { unit.startsWith("sec") -> 1; unit.startsWith("min") -> 60; else -> 3600 }
        val seconds = (amount.toLong() * multiplier).coerceIn(1L, 86_400L).toInt()
        val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, seconds)
            putExtra(AlarmClock.EXTRA_MESSAGE, "MIRA timer")
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
        }
        if (!canHandle(intent)) return "No compatible timer app is installed."
        activity.startActivity(intent)
        return "Opened the timer for $amount $unit. Review the timer in your clock app."
    }

    private fun isAlarmRequest(lower: String) = lower.startsWith("set alarm") || lower.startsWith("wake me at ")

    private fun setAlarm(lower: String): String {
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
            putExtra(AlarmClock.EXTRA_MESSAGE, "MIRA alarm")
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
        }
        if (!canHandle(intent)) return "No compatible alarm app is installed."
        activity.startActivity(intent)
        return "Opened the alarm editor. Review the time before saving."
    }

    private fun openContacts(): String {
        val intent = Intent(Intent.ACTION_VIEW, ContactsContract.Contacts.CONTENT_URI)
        if (!canHandle(intent)) return "No compatible contacts app is installed."
        activity.startActivity(intent)
        return "Opened Contacts."
    }

    private fun findContact(query: String, startDialer: Boolean): String {
        if (query.isBlank()) return "Tell me which contact you want to call."
        val contacts = readPhoneContacts()
        if (contacts.isEmpty()) return "No phone contacts were found. Check that the requested contact is saved with a phone number."

        val normalizedQuery = query.trim().lowercase(Locale.ROOT)
        val relatedNames = when (normalizedQuery) {
            "wife", "husband", "spouse", "my wife", "my husband", "my spouse" -> findSpouseNames()
            else -> emptySet()
        }
        val matches = if (relatedNames.isNotEmpty()) {
            contacts.filter { phone -> relatedNames.any { it.equals(phone.name, ignoreCase = true) || it.equals(phone.name.substringBefore(','), ignoreCase = true) } }
        } else {
            val tokens = normalizedQuery.split(Regex("\\s+")).filter { it.isNotBlank() }
            contacts.filter { contact ->
                val name = contact.name.lowercase(Locale.ROOT)
                name.contains(normalizedQuery) || tokens.all(name::contains)
            }
        }.distinctBy { it.name.lowercase(Locale.ROOT) to it.number.filter(Char::isDigit) }

        if (matches.isEmpty()) {
            val aliasHint = if (normalizedQuery in setOf("wife", "husband", "spouse", "my wife", "my husband", "my spouse"))
                " No spouse relationship is saved in Contacts, so try the person's saved contact name."
            else ""
            return "I couldn't find a contact matching ‘$query’ with a phone number.$aliasHint"
        }
        if (matches.size == 1) {
            val contact = matches.single()
            if (startDialer) return callOrOpenDialer(contact)
            activity.runOnUiThread {
                AlertDialog.Builder(activity)
                    .setTitle(contact.name)
                    .setMessage(contact.number)
                    .setPositiveButton("Call") { _, _ -> openDialer(contact) }
                    .setNegativeButton("Close", null)
                    .show()
            }
            return "Found ${contact.name}. Contact details are displayed; choose Call if you want to place a call."
        }
        val labels = matches.map { "${it.name}  ·  ${it.number}" }.toTypedArray()
        activity.runOnUiThread {
            AlertDialog.Builder(activity)
                .setTitle(if (startDialer) "Which contact do you want to call?" else "Matching contacts")
                .setItems(labels) { _, index ->
                    val contact = matches[index]
                    if (startDialer) {
                        val result = callOrOpenDialer(contact)
                        Toast.makeText(activity, result, Toast.LENGTH_LONG).show()
                    } else {
                        AlertDialog.Builder(activity).setTitle(contact.name).setMessage(contact.number)
                            .setPositiveButton("Call") { _, _ -> openDialer(contact) }
                            .setNegativeButton("Close", null).show()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
        return if (startDialer) "I found ${matches.size} matching contact entries. Choose the correct person before calling." else "I found ${matches.size} matching contact entries. Choose the person you meant."
    }

    private fun readPhoneContacts(): List<PhoneContact> {
        if (activity.checkSelfPermission(Manifest.permission.READ_CONTACTS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            throw SecurityException("Contact permission is needed to search contacts.")
        }
        val out = mutableListOf<PhoneContact>()
        val projection = arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER)
        activity.contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, projection, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (cursor.moveToNext()) {
                val name = if (nameIndex >= 0) cursor.getString(nameIndex).orEmpty() else ""
                val number = if (numberIndex >= 0) cursor.getString(numberIndex).orEmpty() else ""
                if (name.isNotBlank() && number.isNotBlank()) out += PhoneContact(name, number)
            }
        }
        return out
    }

    private fun findSpouseNames(): Set<String> {
        val result = linkedSetOf<String>()
        runCatching {
            val projection = arrayOf(
                ContactsContract.Data.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Relation.NAME
            )
            val selection = "${ContactsContract.Data.MIMETYPE}=? AND ${ContactsContract.CommonDataKinds.Relation.TYPE}=?"
            val args = arrayOf(
                ContactsContract.CommonDataKinds.Relation.CONTENT_ITEM_TYPE,
                ContactsContract.CommonDataKinds.Relation.TYPE_SPOUSE.toString()
            )
            activity.contentResolver.query(ContactsContract.Data.CONTENT_URI, projection, selection, args, null)?.use { cursor ->
                val relationIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Relation.NAME)
                while (cursor.moveToNext()) {
                    val name = if (relationIndex >= 0) cursor.getString(relationIndex).orEmpty().trim() else ""
                    if (name.isNotBlank()) result += name
                }
            }
        }
        return result
    }

    private fun callOrOpenDialer(contact: PhoneContact): String {
        if (activity.checkSelfPermission(Manifest.permission.CALL_PHONE) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            val callIntent = Intent(Intent.ACTION_CALL, Uri.fromParts("tel", contact.number, null))
            if (canHandle(callIntent)) {
                activity.startActivity(callIntent)
                return "Calling ${contact.name}."
            }
        }
        return if (openDialer(contact)) {
            "MIRA could not place the call directly. Opened the dialer for ${contact.name}; review the number and tap Call."
        } else {
            "No compatible calling app or dialer is available for ${contact.name}."
        }
    }

    private fun openDialer(contact: PhoneContact): Boolean {
        val intent = Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", contact.number, null))
        if (!canHandle(intent)) return false
        activity.startActivity(intent)
        return true
    }

    private fun openDirections(destination: String): String {
        if (destination.isBlank()) return "Where would you like directions to?"
        val url = "https://www.google.com/maps/dir/?api=1&destination=${Uri.encode(destination)}"
        openExternalUrl(url)
        return "Opened Maps directions to $destination."
    }

    private fun extractMapQuery(text: String, lower: String): String = when {
        lower.startsWith("search maps for ") -> text.drop("search maps for ".length).trim()
        lower.startsWith("find on maps ") -> text.drop("find on maps ".length).trim()
        lower.startsWith("open maps and search for ") -> text.drop("open maps and search for ".length).trim()
        else -> ""
    }

    private fun openMapSearch(query: String): String {
        if (query.isBlank()) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q="))
            if (!canHandle(intent)) return "No compatible maps app is installed."
            activity.startActivity(intent)
            return "Opened Maps."
        }
        openExternalUrl("https://www.google.com/maps/search/?api=1&query=${Uri.encode(query)}")
        return "Opened Maps search results for $query."
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
        return "Opened a calendar event for $title. Review the details before saving."
    }

    private fun showAlarms(): String {
        val intent = Intent(AlarmClock.ACTION_SHOW_ALARMS)
        if (!canHandle(intent)) return "Your alarm app doesn't support opening the alarm list."
        activity.startActivity(intent)
        return "Opened your alarms."
    }

    private fun openExternalUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply { addCategory(Intent.CATEGORY_BROWSABLE) }
        if (!canHandle(intent)) throw ActivityNotFoundException()
        activity.startActivity(intent)
    }

    private fun launch(intent: Intent, error: String, success: String): String {
        if (!canHandle(intent)) return error
        activity.startActivity(intent)
        return success
    }

    private fun canHandle(intent: Intent) = intent.resolveActivity(activity.packageManager) != null
}

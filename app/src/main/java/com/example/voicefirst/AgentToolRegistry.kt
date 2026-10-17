package com.example.voicefirst

/** Allowlist is the security boundary between model proposals and Android capabilities. */
class AgentToolRegistry {
    private val actions = listOf(
        AgentAction("alarm.set", "Set alarm", "Open system alarm editor for a specified time", RiskLevel.LOW, false),
        AgentAction("timer.set", "Set timer", "Open system timer for a duration", RiskLevel.LOW, false),
        AgentAction("phone.dial_contact", "Call contact", "Pick a contact and open dialer; never place a call automatically", RiskLevel.MEDIUM, true),
        AgentAction("maps.directions", "Open directions", "Open map directions to a destination", RiskLevel.LOW, false),
        AgentAction("maps.search", "Search Maps", "Search a place in Maps", RiskLevel.LOW, false),
        AgentAction("youtube.search", "Search YouTube", "Open YouTube search results", RiskLevel.LOW, false),
        AgentAction("calendar.create", "Create calendar event", "Open an editable calendar event for review; does not save automatically", RiskLevel.MEDIUM, true),
        AgentAction("gmail.search_unread", "Search unread Gmail", "Read recent unread email summaries (private data; ask first)", RiskLevel.MEDIUM, true),
        AgentAction("gmail.create_draft", "Create Gmail draft", "Create an email draft for review; never send it", RiskLevel.HIGH, true),
        AgentAction("memory.save", "Save memory", "Store a memory only when explicitly requested by the user", RiskLevel.LOW, false),
        AgentAction("memory.forget", "Forget memory", "Delete matching local memory entries", RiskLevel.MEDIUM, true)
    )
    fun all(): List<AgentAction> = actions.toList()
    fun find(id: String): AgentAction? = actions.firstOrNull { it.id == id }
}

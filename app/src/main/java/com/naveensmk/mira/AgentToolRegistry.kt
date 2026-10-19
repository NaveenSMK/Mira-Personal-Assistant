package com.naveensmk.mira

/** Allowlist is the security boundary between model proposals and Android capabilities. */
class AgentToolRegistry {
    private val actions = listOf(
        AgentAction("alarm.set", "Set alarm", "Open system alarm editor for a specified time", RiskLevel.LOW, false),
        AgentAction("timer.set", "Set timer", "Open system timer for a duration", RiskLevel.LOW, false),
        AgentAction("phone.dial_contact", "Call contact", "Resolve one matching contact, ask the user to choose if ambiguous, then open the system dialer for user review; never place a call directly", RiskLevel.MEDIUM, true),
        AgentAction("browser.open", "Open browser", "Open a web page in the user's default browser", RiskLevel.LOW, false),
        AgentAction("browser.search", "Search the web", "Open web search results in the user's default browser", RiskLevel.LOW, false),
        AgentAction("youtube.open", "Open YouTube", "Launch the YouTube app, or use the web fallback when it is not installed", RiskLevel.LOW, false),
        AgentAction("maps.directions", "Open directions", "Open map directions to a destination", RiskLevel.LOW, false),
        AgentAction("maps.search", "Search Maps", "Search an explicitly requested place in Maps", RiskLevel.LOW, false),
        AgentAction("youtube.search", "Search YouTube", "Open search results in the YouTube app or its web fallback", RiskLevel.LOW, false),
        AgentAction("calendar.create", "Create calendar event", "Open an editable calendar event for review; does not save automatically", RiskLevel.MEDIUM, true),
        AgentAction("gmail.search_unread", "Search unread Gmail", "Read recent unread email summaries (private data; ask first)", RiskLevel.MEDIUM, true),
        AgentAction("gmail.create_draft", "Create Gmail draft", "Create an email draft for review; never send it", RiskLevel.HIGH, true),
        AgentAction("memory.save", "Save memory", "Store a memory only when explicitly requested by the user", RiskLevel.LOW, false),
        AgentAction("memory.forget", "Forget memory", "Delete matching local memory entries", RiskLevel.MEDIUM, true),
        AgentAction("ui.perform", "Perform permitted UI action", "On an ordinary screen, click one exact visible label, enter non-secret text in one exact hinted field, scroll, or go back. Stop at safety checkpoints; never send, submit, purchase, trade, pay, or delete.", RiskLevel.LOW, false)
    )
    fun all(): List<AgentAction> = actions.toList()
    fun find(id: String): AgentAction? = actions.firstOrNull { it.id == id }
}

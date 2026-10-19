package com.naveensmk.mira

enum class RiskLevel { LOW, MEDIUM, HIGH }

data class AgentAction(
    val id: String,
    val title: String,
    val description: String,
    val risk: RiskLevel,
    val requiresConfirmation: Boolean,
    val requiredPermissions: List<String> = emptyList()
)

data class AgentStep(
    val actionId: String,
    val arguments: Map<String, String> = emptyMap(),
    val rationale: String = "",
    val requiresConfirmation: Boolean = false
)

data class AgentPlan(
    val userRequest: String,
    val steps: List<AgentStep>,
    val createdAtMillis: Long = System.currentTimeMillis()
)

data class AgentExecutionResult(
    val actionId: String,
    val success: Boolean,
    val message: String,
    val shouldContinue: Boolean = false
)

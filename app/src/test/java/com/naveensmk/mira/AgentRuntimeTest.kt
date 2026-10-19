package com.naveensmk.mira

import android.net.Uri
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class AgentRuntimeTest {
    private class FakeModel(private val responses: MutableList<String>) : LocalModelEngine {
        override suspend fun load(modelUri: Uri) = ModelLoadResult(true, "loaded")
        override suspend fun generate(prompt: String): ModelGenerationResult {
            if (responses.isEmpty()) return ModelGenerationResult(false, "", "No fake response configured")
            return ModelGenerationResult(true, responses.removeAt(0))
        }
        override fun isLoaded() = true
        override suspend fun unload() = Unit
    }

    @Test fun executesAllowlistedToolThenReturnsFinalResponse() = runBlocking {
        var executions = 0
        val model = FakeModel(mutableListOf(
            """{"type":"tool_call","tool":"maps.search","arguments":{"query":"coffee shops"},"rationale":"Find nearby options"}""",
            """{"type":"final","message":"I opened Maps search."}"""
        ))
        val runtime = AgentRuntime(model, AgentToolRegistry(), { id, args ->
            executions++
            assertEquals("maps.search", id)
            assertEquals("coffee shops", args["query"])
            "Maps opened"
        }, { _, _ -> fail("This low-risk tool should not require confirmation"); false })

        val result = runtime.run("Find coffee shops", "base context")
        assertTrue(result.success)
        assertEquals(1, result.toolCalls)
        assertEquals(1, executions)
        assertEquals("I opened Maps search.", result.message)
    }

    @Test fun rejectsUnknownToolBeforeExecution() = runBlocking {
        var executions = 0
        val runtime = AgentRuntime(
            FakeModel(mutableListOf("""{"type":"tool_call","tool":"shell.exec","arguments":{}}""")),
            AgentToolRegistry(), { _, _ -> executions++; "should not run" }, { _, _ -> true }
        )
        val result = runtime.run("Do something", "base context")
        assertFalse(result.success)
        assertEquals(0, executions)
    }

    @Test fun invalidTimerArgumentsNeverReachHandler() = runBlocking {
        var executions = 0
        val runtime = AgentRuntime(
            FakeModel(mutableListOf("""{"type":"tool_call","tool":"timer.set","arguments":{"duration":"999999","unit":"hours"}}""")),
            AgentToolRegistry(), { _, _ -> executions++; "should not run" }, { _, _ -> true }
        )
        val result = runtime.run("Set a timer", "base context")
        assertFalse(result.success)
        assertEquals(0, executions)
        assertTrue(result.message.contains("24 hours"))
    }

    @Test fun deniedSensitiveActionIsNotExecuted() = runBlocking {
        var executions = 0
        val runtime = AgentRuntime(
            FakeModel(mutableListOf("""{"type":"tool_call","tool":"gmail.create_draft","arguments":{"to":"a@example.com","subject":"Hello","body":"Draft"}}""")),
            AgentToolRegistry(), { _, _ -> executions++; "should not run" }, { action, _ ->
                assertEquals("gmail.create_draft", action.id)
                false
            }
        )
        val result = runtime.run("Draft an email", "base context")
        assertTrue(result.success)
        assertEquals(0, executions)
        assertTrue(result.message.contains("cancelled", ignoreCase = true))
    }

    @Test fun explicitToolFailureIsNotReportedAsSuccessfulTask() = runBlocking {
        var modelCalls = 0
        val model = object : LocalModelEngine {
            override suspend fun load(modelUri: Uri) = ModelLoadResult(true, "loaded")
            override suspend fun generate(prompt: String): ModelGenerationResult {
                modelCalls++
                return ModelGenerationResult(true, """{"type":"tool_call","tool":"browser.search","arguments":{"query":"news"}}""")
            }
            override fun isLoaded() = true
            override suspend fun unload() = Unit
        }
        val result = AgentRuntime(model, AgentToolRegistry(), { _, _ -> "No compatible web browser is installed." }, { _, _ -> true })
            .run("Search for news", "base")
        assertFalse(result.success)
        assertTrue(result.message.contains("No compatible"))
        assertEquals(1, modelCalls)
    }

    @Test fun actionRequestWithoutToolCannotBeMarkedComplete() = runBlocking {
        var executions = 0
        val runtime = AgentRuntime(
            FakeModel(mutableListOf("""{"type":"final","message":"Done, I created the draft."}""")),
            AgentToolRegistry(), { _, _ -> executions++; "should not run" }, { _, _ -> true }
        )
        val result = runtime.run("Create a Gmail draft", "base context")
        assertFalse(result.success)
        assertEquals(0, result.toolCalls)
        assertEquals(0, executions)
        assertTrue(result.message.contains("no tool ran", ignoreCase = true))
    }

    @Test fun failurePhraseEmbeddedInHandlerResultStopsAgentLoop() = runBlocking {
        var modelCalls = 0
        val model = object : LocalModelEngine {
            override suspend fun load(modelUri: Uri) = ModelLoadResult(true, "loaded")
            override suspend fun generate(prompt: String): ModelGenerationResult {
                modelCalls++
                return if (modelCalls == 1) ModelGenerationResult(true,
                    """{"type":"tool_call","tool":"gmail.search_unread","arguments":{}}""")
                else ModelGenerationResult(true, """{"type":"final","message":"I found your messages."}""")
            }
            override fun isLoaded() = true
            override suspend fun unload() = Unit
        }
        val result = AgentRuntime(model, AgentToolRegistry(), { _, _ -> "Gmail is not connected. Please reconnect." }, { _, _ -> true })
            .run("Read my unread emails", "base")
        assertFalse(result.success)
        assertTrue(result.message.contains("not connected", ignoreCase = true))
        assertEquals("A failed handler result must stop the model from claiming success.", 1, modelCalls)
    }

    @Test fun contactDisambiguationStopsModelFromClaimingCallSucceeded() = runBlocking {
        var modelCalls = 0
        val model = object : LocalModelEngine {
            override suspend fun load(modelUri: Uri) = ModelLoadResult(true, "loaded")
            override suspend fun generate(prompt: String): ModelGenerationResult {
                modelCalls++
                return ModelGenerationResult(true, """{"type":"tool_call","tool":"phone.dial_contact","arguments":{"query":"Alex"}}""")
            }
            override fun isLoaded() = true
            override suspend fun unload() = Unit
        }
        val result = AgentRuntime(model, AgentToolRegistry(), { _, _ ->
            "I found 2 matching contact entries. Choose the correct person before calling."
        }, { _, _ -> true }).run("Call Alex", "base")
        assertTrue(result.success)
        assertTrue(result.message.contains("Choose the correct person"))
        assertEquals("The model must not continue after the picker has been opened.", 1, modelCalls)
    }

    @Test fun rejectsNonObjectArguments() = runBlocking {
        val runtime = AgentRuntime(
            FakeModel(mutableListOf("""{"type":"tool_call","tool":"gmail.search_unread","arguments":[]}""")),
            AgentToolRegistry(), { _, _ -> "should not run" }, { _, _ -> true }
        )
        val result = runtime.run("Read email", "base context")
        assertFalse(result.success)
        assertTrue(result.message.contains("JSON object"))
    }
    @Test fun normalizesLegacyUiKeyAndClickAliases() = runBlocking {
        var executions = 0
        val runtime = AgentRuntime(
            FakeModel(mutableListOf(
                """{"type":"tool_call","tool":"ui.perform","arguments":{"action":"click","key":"Contacts"}}""",
                """{"type":"final","message":"Opened Contacts."}"""
            )),
            AgentToolRegistry(), { id, args ->
                executions++
                assertEquals("ui.perform", id)
                assertEquals("click_text", args["action"])
                assertEquals("Contacts", args["target"])
                "Tapped Contacts"
            }, { _, _ -> fail("A low-risk visible UI click should not require approval"); false }
        )
        val result = runtime.run("Open Contacts", "base")
        assertTrue(result.success)
        assertEquals(1, result.toolCalls)
        assertEquals(1, executions)
    }

    @Test fun normalizesContactSearchKeyAliasAndStillRequiresApproval() = runBlocking {
        var executions = 0
        val runtime = AgentRuntime(
            FakeModel(mutableListOf(
                """{"type":"tool_call","tool":"phone.dial_contact","arguments":{"key":"wife"}}""",
                """{"type":"final","message":"The dialer is ready for review."}"""
            )),
            AgentToolRegistry(), { id, args ->
                executions++
                assertEquals("phone.dial_contact", id)
                assertEquals("wife", args["query"])
                "Dialer ready"
            }, { action, args ->
                assertEquals("phone.dial_contact", action.id)
                assertEquals("wife", args["query"])
                true
            }
        )
        val result = runtime.run("Call my wife", "base")
        assertTrue(result.success)
        assertEquals(1, result.toolCalls)
        assertEquals(1, executions)
    }

}

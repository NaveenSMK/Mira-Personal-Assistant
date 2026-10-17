package com.example.voicefirst

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
        assertTrue(result.success)
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

    @Test fun rejectsNonObjectArguments() = runBlocking {
        val runtime = AgentRuntime(
            FakeModel(mutableListOf("""{"type":"tool_call","tool":"gmail.search_unread","arguments":[]}""")),
            AgentToolRegistry(), { _, _ -> "should not run" }, { _, _ -> true }
        )
        val result = runtime.run("Read email", "base context")
        assertFalse(result.success)
        assertTrue(result.message.contains("JSON object"))
    }
}

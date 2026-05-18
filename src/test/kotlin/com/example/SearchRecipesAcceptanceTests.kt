package com.example

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import org.http4k.ai.a2a.client.testA2AJsonRpcClient
import org.http4k.ai.model.ToolName
import org.http4k.routing.reverseProxy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo
import org.http4k.ai.llm.model.Message as LLMMessage

class SearchRecipesAcceptanceTests {
    private val mealApiServer = FakeMealApiServer()

    private val llm = ScriptedChat(
        // CoordinatorAgent -> LLM: what tool to execute
        { request ->
            val lastMessage = request.messages.last()
            if (lastMessage == LLMMessage.User("Give me the list of recipes for a Carbonara")) {
                Answers.RequireToolExecution(ToolName.of(RecipesAgent.card.name), mapOf("query" to "Search recipes for Carbonara"))
            } else {
                Answers.DontKnowHowToRespond()
            }
        },
        // RecipesAgent -> LLM: what tool to execute
        { request ->
            val lastMessage = request.messages.last()
            if (lastMessage == LLMMessage.User("Search recipes for Carbonara")) {
                Answers.RequireToolExecution(SearchRecipesTool.definition.name, mapOf("query" to "Carbonara"))
            } else {
                Answers.DontKnowHowToRespond()
            }
        },
        // RecipesAgent -> LLM: summarise final answer
        { request ->
            val lastMessage = request.messages.last()
            if (lastMessage is LLMMessage.ToolResult) {
                Answers.TextReply("Found recipes for: Carbonara\n\n${lastMessage.text}")
            } else {
                Answers.DontKnowHowToRespond()
            }
        },
        // CoordinatorAgent -> LLM: summarise final answer
        { request ->
            val lastMessage = request.messages.last()
            if (lastMessage is LLMMessage.ToolResult) {
                Answers.TextReply(lastMessage.text)
            } else {
                Answers.DontKnowHowToRespond()
            }
        }
    )

    private val traces = InMemorySpanExporter.create()
    private val telemetry = ConfigurableTelemetry(traces)

    private val app = App(
        llm = FixedModelChat(
            llm = llm,
            model = ScriptedChat.model
        ),
        outgoing = reverseProxy(mealApiServer.uri.authority to mealApiServer),
        telemetry = telemetry
    )

    private val chef = AmateurChef(app.testA2AJsonRpcClient())

    @Test
    fun `provides all recipes for a carbonara`() {
        val answer = chef.asks("Give me the list of recipes for a Carbonara")

        assertThat(answer, equalTo("""
        Found recipes for: Carbonara

        1 Spaghetti alla Carbonara
        """.trimIndent()))
    }

    @AfterEach
    fun tearDown(testInfo: TestInfo) {
        GenerateSequenceDiagramsDocs(traces.finishedSpanItems, testInfo)
    }
}

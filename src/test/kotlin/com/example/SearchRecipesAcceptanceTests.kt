package com.example

import com.example.Answers.OnToolResult
import com.example.Answers.OnUserMessage
import com.example.Answers.RequireToolExecution
import com.example.Answers.TextReply
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import org.http4k.ai.a2a.client.testA2AJsonRpcClient
import org.http4k.ai.model.ToolName
import org.http4k.routing.reverseProxy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo

class SearchRecipesAcceptanceTests {
    private val mealApiServer = FakeMealApiServer()

    private val llm = ScriptedChat(
        // CoordinatorAgent -> LLM: what tool to execute
        OnUserMessage("Give me the list of recipes for a Carbonara") {
            RequireToolExecution(ToolName.of(RecipesAgent.card.name), mapOf("query" to "Search recipes for Carbonara"))
        },
        // RecipesAgent -> LLM: what tool to execute
        OnUserMessage("Search recipes for Carbonara") {
            RequireToolExecution(SearchRecipesTool.definition.name, mapOf("query" to "Carbonara"))
        },
        // RecipesAgent -> LLM: summarise final answer
        OnToolResult { message ->
            TextReply("Found recipes for: Carbonara\n\n${message.text}")
        },
        // CoordinatorAgent -> LLM: summarise final answer
        OnToolResult { message ->
            TextReply(message.text)
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

        1. Spaghetti alla Carbonara
        Ingredients:
        - 320g Spaghetti
        - 6 Egg Yolks
        - 150g Bacon
        - 50g Pecorino
        - Salt to taste
        - Black Pepper to taste

        """.trimIndent()))
    }

    @AfterEach
    fun tearDown(testInfo: TestInfo) {
        GenerateSequenceDiagramsDocs(traces.finishedSpanItems, testInfo)
    }
}

package com.example

import com.example.Answers.OnAnyUserMessage
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

class ShoppingListAcceptanceTests {
    private val mealApiServer = FakeMealApiServer()

    private val llm = ScriptedChat(
        // 1. CoordinatorAgent -> LLM: route to RecipesAgent first
        OnUserMessage("Generate the shopping list for spaghetti alla carbonara") {
            RequireToolExecution(ToolName.of(RecipesAgent.card.name), mapOf("query" to "Search recipes for Spaghetti alla Carbonara"))
        },
        // 2. RecipesAgent -> LLM: what tool to execute
        OnUserMessage("Search recipes for Spaghetti alla Carbonara") {
            RequireToolExecution(SearchRecipesTool.definition.name, mapOf("query" to "Spaghetti alla Carbonara"))
        },
        // 3. RecipesAgent -> LLM: summarise recipe text (with ingredients)
        OnToolResult {
            TextReply("Spaghetti alla Carbonara recipe: 320g Spaghetti, 6 Egg Yolks, Salt, 150g Bacon, 50g Pecorino, Black Pepper")
        },
        // 4. CoordinatorAgent -> LLM: recipe text received, route to ShoppingListAgent
        OnToolResult { message ->
            RequireToolExecution(ToolName.of(ShoppingListAgent.card.name), mapOf("query" to "Generate a shopping list for the following recipe:\n\n${message.text}"))
        },
        // 5. ShoppingListAgent -> LLM: parse recipe text, produce shopping list
        OnAnyUserMessage {
            RequireToolExecution(
                FormatShoppingListTool.definition.name,
                mapOf("ingredients" to listOf("320g Spaghetti", "6 Egg Yolks", "Salt", "150g Bacon", "50g Pecorino", "Black Pepper"))
            )
        },
        // 6. ShoppingListAgent -> LLM: summarise shopping list text
        OnToolResult { message ->
            TextReply("Shopping list for spaghetti alla carbonara\n\n${message.text}")
        },
        // 7. CoordinatorAgent -> LLM: relay ShoppingListAgent result back to user
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
        telemetry = telemetry,
    )

    private val chef = AmateurChef(app.testA2AJsonRpcClient())

    @Test
    fun `provides the shopping list for 'spaghetti alla carbonara'`(info: TestInfo) {
        val answer = chef.asks("Generate the shopping list for spaghetti alla carbonara")

        assertThat(answer, equalTo("""
        Shopping list for spaghetti alla carbonara

        - 320g Spaghetti
        - 6 Egg Yolks
        - Salt
        - 150g Bacon
        - 50g Pecorino
        - Black Pepper
        """.trimIndent()))
    }

    @AfterEach
    fun tearDown(testInfo: TestInfo) {
        GenerateSequenceDiagramsDocs(traces.finishedSpanItems, testInfo)
    }
}

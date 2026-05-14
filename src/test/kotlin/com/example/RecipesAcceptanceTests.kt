package com.example

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import dev.forkhandles.result4k.Success
import dev.forkhandles.result4k.valueOrNull
import org.http4k.ai.a2a.client.testA2AJsonRpcClient
import org.http4k.ai.a2a.model.A2ARole.ROLE_USER
import org.http4k.ai.a2a.model.Message
import org.http4k.ai.a2a.model.MessageId
import org.http4k.ai.a2a.model.Part
import org.http4k.ai.a2a.model.ResponseStream
import org.http4k.ai.a2a.model.Task
import org.http4k.ai.model.ToolName
import org.http4k.routing.reverseProxy
import org.junit.jupiter.api.Test
import org.http4k.ai.llm.model.Message as LLMMessage

class RecipesAcceptanceTests {
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
                Answers.RequireToolExecution(SearchRecipesTool.name, mapOf("query" to "Carbonara"))
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

    private val app = App(
        llm = llm,
        outgoing = reverseProxy(mealApiServer.uri.authority to mealApiServer)
    )

    private val client = app.testA2AJsonRpcClient()

    @Test
    fun `agent card is discoverable`() {
        assertThat(client.agentCard(), equalTo(Success(CoordinatorAgent.card)))
    }

    @Test
    fun `provides all recipes for a carbonara`() {
        val response = client.messageStream(
            Message(
                messageId = MessageId.random(),
                role = ROLE_USER,
                parts = listOf(Part.Text("Give me the list of recipes for a Carbonara"))
            )
        ).valueOrNull()!! as ResponseStream

        val last = response.last() as Task

        assertThat(last.status.message?.parts?.filterIsInstance<Part.Text>()?.joinToString("\n") { it.text }, equalTo("""
        Found recipes for: Carbonara

        1 Spaghetti alla Carbonara
        """.trimIndent()))
    }
}

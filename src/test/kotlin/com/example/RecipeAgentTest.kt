package com.example

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.greaterThan
import dev.forkhandles.result4k.Success
import dev.forkhandles.result4k.valueOrNull
import org.http4k.ai.a2a.client.testA2AJsonRpcClient
import org.http4k.ai.a2a.model.A2ARole.ROLE_USER
import org.http4k.ai.a2a.model.Message
import org.http4k.ai.a2a.model.MessageId
import org.http4k.ai.a2a.model.Part
import org.http4k.ai.a2a.model.ResponseStream
import org.http4k.ai.a2a.model.Task
import org.http4k.ai.a2a.model.TaskState.TASK_STATE_COMPLETED
import org.http4k.ai.a2a.model.TaskState.TASK_STATE_WORKING
import org.http4k.ai.llm.chat.ChatRequest
import org.http4k.ai.llm.chat.ChatResponse
import org.http4k.ai.llm.model.Content
import org.http4k.ai.llm.tools.ToolRequest
import org.http4k.ai.model.RequestId
import org.http4k.ai.model.ResponseId
import org.http4k.connect.openai.OpenAIModels
import org.http4k.routing.reverseProxy
import org.junit.jupiter.api.Test
import java.util.*
import org.http4k.ai.llm.model.Message as LLMMessage

class RecipeAgentTest {
    private val mealApiServer = FakeMealApiServer()
    private val llm = ScriptedChat(
        { request ->
            val lastMessage = message(request)
            if (lastMessage == LLMMessage.User("Give me the list of recipes for Carbonara")) {
                ChatResponse(
                    message = LLMMessage.Assistant(
                        toolRequests = listOf(
                            ToolRequest(
                                id = RequestId.of(UUID.randomUUID().toString()),
                                name = SearchRecipesTool.name,
                                arguments = mapOf("query" to "Carbonara")
                            )
                        )
                    ),
                    metadata = ChatResponse.Metadata(
                        id = ResponseId.of(UUID.randomUUID().toString()),
                        model = OpenAIModels.GPT4,
                    )
                )
            } else {
                Answers.DownKnowHowToRespond()
            }
        },
        { request ->
            val lastMessage = request.messages.last()
            if (lastMessage is LLMMessage.ToolResult) {
                ChatResponse(
                    message = LLMMessage.Assistant(listOf(
                        Content.Text("Found recipes for: Carbonara\n\n${lastMessage.text}")
                    )),
                    metadata = ChatResponse.Metadata(
                        id = ResponseId.of(UUID.randomUUID().toString()),
                        model = OpenAIModels.GPT4,
                    )
                )
            } else {
                Answers.DownKnowHowToRespond()
            }
        }
    )

    private fun message(request: ChatRequest): LLMMessage = request.messages.last()
    private val app = App(
        llm = llm,
        outgoing = reverseProxy(
            mealApiServer.uri.authority to mealApiServer
        )
    )
    private val client = app.testA2AJsonRpcClient()

    @Test
    fun `agent card is discoverable`() {
        assertThat(client.agentCard(), equalTo(Success(recipeAgentCard)))
    }

    @Test
    fun `agent returns streaming task updates`() {
        val response = client.messageStream(
            Message(MessageId.of("test-msg"), ROLE_USER, listOf(Part.Text("Carbonara")))
        ).valueOrNull()!! as ResponseStream

        val items = response.toList()
        assertThat(items.size, greaterThan(1))

        val first = items.first() as Task
        assertThat(first.status.state, equalTo(TASK_STATE_WORKING))

        val last = items.last() as Task
        assertThat(last.status.state, equalTo(TASK_STATE_COMPLETED))

        assertThat(last.status.message?.parts?.filterIsInstance<Part.Text>()?.joinToString("\n") { it.text }, equalTo("""
        Found recipes for: Carbonara

        1 Spaghetti alla Carbonara
        """.trimIndent()))
    }
}

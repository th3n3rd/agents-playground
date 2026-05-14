package com.example

import dev.forkhandles.result4k.Result
import dev.forkhandles.result4k.flatMap
import dev.forkhandles.result4k.map
import dev.forkhandles.result4k.peek
import dev.forkhandles.result4k.valueOrNull
import org.http4k.ai.a2a.model.A2ARole
import org.http4k.ai.a2a.model.AgentCapabilities
import org.http4k.ai.a2a.model.AgentCard
import org.http4k.ai.a2a.model.AgentSkill
import org.http4k.ai.a2a.model.ContextId
import org.http4k.ai.a2a.model.MessageId
import org.http4k.ai.a2a.model.Part
import org.http4k.ai.a2a.model.ResponseStream
import org.http4k.ai.a2a.model.SkillId
import org.http4k.ai.a2a.model.Task
import org.http4k.ai.a2a.model.TaskId
import org.http4k.ai.a2a.model.TaskState
import org.http4k.ai.a2a.model.TaskStatus
import org.http4k.ai.a2a.model.Version
import org.http4k.ai.llm.LLMError
import org.http4k.ai.llm.LLMResult
import org.http4k.ai.llm.chat.Chat
import org.http4k.ai.llm.chat.ChatRequest
import org.http4k.ai.llm.chat.ChatResponse
import org.http4k.ai.llm.model.Content
import org.http4k.ai.llm.model.Message
import org.http4k.ai.llm.model.ModelParams
import org.http4k.ai.llm.tools.LLMTools
import org.http4k.ai.llm.tools.McpLLMTools
import org.http4k.ai.mcp.testing.testMcpClient
import org.http4k.connect.model.MimeType
import org.http4k.connect.openai.OpenAIModels
import org.http4k.core.HttpHandler
import org.http4k.core.PolyHandler
import org.http4k.routing.a2aJsonRpc
import java.time.Duration
import java.util.*

object RecipesAgent {
    val card = AgentCard(
        name = "recipe-agent",
        version = Version.of("1.0.0"),
        description = "An agent that helps users find and explore recipes",
        capabilities = AgentCapabilities(streaming = true),
        defaultInputModes = listOf(MimeType.of("text/plain")),
        defaultOutputModes = listOf(MimeType.of("text/plain")),
        skills = listOf(
            AgentSkill(
                id = SkillId.of("search-recipes"),
                name = "Search Recipe",
                description = "Search for recipes by ingredients or cuisine",
                tags = listOf("cooking", "recipes", "search")
            )
        )
    )

    operator fun invoke(llm: Chat, outgoing: HttpHandler): PolyHandler {
        val recipes = MealApiRecipes(outgoing)

        val mcpTools = RecipesMcp(recipes)
            .testMcpClient() // TODO: should not use a test client BUT I am not sure yet how to create a client for an in-memory mcp handler
            .apply { start(Duration.ofSeconds(1)) }
            .let { McpLLMTools(it) }

        return a2aJsonRpc(card, messageHandler = { request ->
            val query = request.message.parts.filterIsInstance<Part.Text>().joinToString(" ") { it.text }
            val taskId = TaskId.of(UUID.randomUUID().toString())
            val contextId = ContextId.of(UUID.randomUUID().toString())

            val history = mutableListOf<Message>()

            ResponseStream(sequence {
                yield(
                    Task(
                        id = taskId,
                        status = TaskStatus(state = TaskState.TASK_STATE_WORKING),
                        contextId = contextId,
                        history = listOf(request.message)
                    )
                )

                processQuery(llm, query, history, mcpTools)
                    .map { answer(it) }
                    .peek {
                        yield(
                            Task(
                                id = taskId,
                                status = TaskStatus(state = TaskState.TASK_STATE_COMPLETED, message = it),
                                contextId = contextId
                            ),
                        )
                    }
            })
        })
    }

    private fun answer(response: ChatResponse): org.http4k.ai.a2a.model.Message = org.http4k.ai.a2a.model.Message(
        messageId = MessageId.random(),
        role = A2ARole.ROLE_AGENT,
        parts = listOf(
            Part.Text(
                response.message.contents
                    .filterIsInstance<Content.Text>()
                    .joinToString("\n") { it.text }
            )
        )
    )

    private fun processQuery(
        llm: Chat,
        query: String,
        history: MutableList<Message>,
        mcpTools: McpLLMTools
    ): Result<ChatResponse, LLMError> =
        llm.ask(Message.User(query), history, mcpTools)
            .flatMap { mcpTools(it.message.toolRequests.first()) } // TODO: need to understand how to deal with many tool calls
            .flatMap { llm.ask(it.result, history, mcpTools) }

    private fun Chat.ask(
        message: Message,
        history: MutableList<Message>,
        llmTools: LLMTools
    ): LLMResult<ChatResponse> {
        history.add(message)
        return this(
            ChatRequest(
                messages = history,
                params = ModelParams(
                    modelName = OpenAIModels.GPT4,
                    tools = llmTools.list().valueOrNull()!!
                )
            )
        )
    }
}
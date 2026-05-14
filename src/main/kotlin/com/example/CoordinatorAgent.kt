package com.example

import dev.forkhandles.result4k.Result
import dev.forkhandles.result4k.asSuccess
import dev.forkhandles.result4k.flatMap
import dev.forkhandles.result4k.map
import dev.forkhandles.result4k.peek
import dev.forkhandles.result4k.valueOrNull
import org.http4k.ai.a2a.client.A2AClient
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
import org.http4k.ai.llm.tools.LLMTool
import org.http4k.ai.llm.tools.ToolRequest
import org.http4k.connect.model.MimeType
import org.http4k.connect.openai.OpenAIModels
import org.http4k.core.PolyHandler
import org.http4k.routing.a2aJsonRpc
import java.util.UUID

object CoordinatorAgent {
    val card = AgentCard(
        name = "cooking-assistant-agent",
        version = Version.of("1.0.0"),
        description = "A cooking assistant that helps users discover recipes, explore meal ideas, and plan their shopping",
        capabilities = AgentCapabilities(streaming = true),
        defaultInputModes = listOf(MimeType.of("text/plain")),
        defaultOutputModes = listOf(MimeType.of("text/plain")),
        skills = listOf(
            AgentSkill(
                id = SkillId.of("cooking-assistance"),
                name = "cooking-assistance",
                description = "Answer any cooking-related question: find recipes, get ingredient lists, and build shopping lists",
                tags = listOf("cooking", "recipes", "shopping", "meal-planning")
            )
        )
    )

    operator fun invoke(llm: Chat, subAgents: List<A2AClient>): PolyHandler {
        val subAgent = subAgents.first() // TODO: needs to support multiple sub-agents

        val llmTools = listOf(subAgent)
            .map { it.agentCard() }
            .mapNotNull { it.valueOrNull() }
            .map { it.toLLM() }

        return a2aJsonRpc(card, messageHandler = { request ->
            val query = request.message.parts.filterIsInstance<Part.Text>().joinToString(" ") { it.text }
            val taskId = TaskId.of(UUID.randomUUID().toString())
            val contextId = ContextId.of(UUID.randomUUID().toString())

            ResponseStream(sequence {
                yield(
                    Task(
                        id = taskId,
                        status = TaskStatus(state = TaskState.TASK_STATE_WORKING),
                        contextId = contextId,
                        history = listOf(request.message)
                    )
                )

                val history = mutableListOf<Message>()

                processQuery(llm, query, history, llmTools, subAgent)
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

    private fun processQuery(
        llm: Chat,
        query: String,
        history: MutableList<Message>,
        llmTools: List<LLMTool>,
        subAgent: A2AClient
    ): Result<ChatResponse, LLMError> =
        llm.ask(Message.User(query), history, llmTools)
            .flatMap { subAgent.delegate(it.message.toolRequests.first()) } // TODO: need to understand how to deal with many tool calls
            .flatMap { llm.ask(it, history, llmTools) }

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

    private fun A2AClient.delegate(request: ToolRequest): Result<Message.ToolResult, Nothing> {
        val query = request.arguments["query"].toString()

        val result = message( // TODO: support streaming (i.e. non blocking) responses
            org.http4k.ai.a2a.model.Message(
                MessageId.random(),
                A2ARole.ROLE_USER,
                listOf(Part.Text(query))
            ) // TODO: need to understand what's the right parameter(s)
        ).valueOrNull()!!
            .let { it as Task }
            .status
            .message
            ?.parts?.filterIsInstance<Part.Text>()
            ?.joinToString("\n") { it.text }
            .orEmpty()

        return Message.ToolResult(
            id = request.id,
            tool = request.name,
            text = result
        ).asSuccess()
    }

    private fun Chat.ask(
        message: Message,
        history: MutableList<Message>,
        llmTools: List<LLMTool> = emptyList()
    ): LLMResult<ChatResponse> {
        history.add(message)
        return this(
            ChatRequest(
                messages = history,
                params = ModelParams(
                    modelName = OpenAIModels.GPT4,
                    tools = llmTools
                )
            )
        )
    }
}
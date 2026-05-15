package com.example

import dev.forkhandles.result4k.Success
import dev.forkhandles.result4k.allValues
import dev.forkhandles.result4k.asFailure
import dev.forkhandles.result4k.asSuccess
import dev.forkhandles.result4k.flatMap
import dev.forkhandles.result4k.map
import dev.forkhandles.result4k.mapFailure
import dev.forkhandles.result4k.peek
import dev.forkhandles.result4k.valueOrNull
import org.http4k.ai.a2a.client.A2AClient
import org.http4k.ai.a2a.model.A2ARole
import org.http4k.ai.a2a.model.AgentCard
import org.http4k.ai.a2a.model.Message
import org.http4k.ai.a2a.model.MessageId
import org.http4k.ai.a2a.model.Part
import org.http4k.ai.a2a.model.Task
import org.http4k.ai.llm.LLMError
import org.http4k.ai.llm.LLMResult
import org.http4k.ai.llm.chat.Chat
import org.http4k.ai.llm.chat.ChatRequest
import org.http4k.ai.llm.chat.ChatResponse
import org.http4k.ai.llm.model.Message.User
import org.http4k.ai.llm.model.ModelParams
import org.http4k.ai.llm.tools.LLMTool
import org.http4k.ai.llm.tools.LLMTools
import org.http4k.ai.llm.tools.ToolRequest
import org.http4k.ai.llm.tools.ToolResponse
import org.http4k.ai.model.ModelName
import org.http4k.ai.llm.model.Message as LLMMessage

fun AgentCard.toLLM(): LLMTool = LLMTool(
    name = name,
    description = """
        $description
        
        Skills: ${skills.joinToString { "${it.name}: ${it.description}" }}
        """.trimIndent(),
    inputSchema = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "query" to mapOf(
                "type" to "string",
                "description" to "The request to send to this agent"
            )
        ),
        "required" to listOf("query")
    )
)

fun ModelName.Companion.inherited() = ModelName.of("inherited")

class FixedModelChat(private val llm: Chat, private val model: ModelName) : Chat {
    override fun invoke(request: ChatRequest): LLMResult<ChatResponse> {
        return llm(request.copy(params = request.params.copy(modelName = model)))
    }
}

fun Chat.reactLoop(query: String, tools: LLMTools): LLMResult<ChatResponse> {
    val history = mutableListOf<LLMMessage>()

    fun act(toolRequests: List<ToolRequest>) = toolRequests
        .map { req -> tools(req).map { it.result } }
        .allValues()

    fun remember(toolResults: List<LLMMessage.ToolResult>) =
        toolResults.forEach { history.add(it) }

    fun reason() = this(
        ChatRequest(
            messages = history,
            params = ModelParams(
                modelName = ModelName.inherited(),
                tools = tools.list().valueOrNull()!!
            )
        )
    )

    fun loop(response: ChatResponse): LLMResult<ChatResponse> {
        if (response.message.toolRequests.isEmpty()) {
            return Success(response)
        }

        return act(response.message.toolRequests)
            .peek { remember(it) }
            .flatMap { reason() }
            .flatMap { loop(it) }
    }

    history.add(User(query))

    return reason().flatMap { loop(it) }
}

class NoTools : LLMTools {
    override fun list(): LLMResult<List<LLMTool>> = emptyList<LLMTool>().asSuccess()
    override fun invoke(request: ToolRequest): LLMResult<ToolResponse> = LLMError.Internal(Exception("No tools available")).asFailure()
}

class AgentTool(val client: A2AClient) : LLMTools {
    override fun list(): LLMResult<List<LLMTool>> {
        return client.agentCard()
            .map { listOf(it.toLLM()) }
            .mapFailure { LLMError.Internal(Exception(it.toString())) }
    }

    override fun invoke(request: ToolRequest): LLMResult<ToolResponse> {
        val query = request.arguments["query"].toString()

        val result = client.message( // TODO: support streaming (i.e. non blocking) responses
            Message(
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

        return ToolResponse(
            id = request.id,
            tool = request.name,
            text = result
        ).asSuccess()
    }
}

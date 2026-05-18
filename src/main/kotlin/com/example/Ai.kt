package com.example

import dev.forkhandles.result4k.Success
import dev.forkhandles.result4k.allValues
import dev.forkhandles.result4k.asFailure
import dev.forkhandles.result4k.asSuccess
import dev.forkhandles.result4k.flatMap
import dev.forkhandles.result4k.map
import dev.forkhandles.result4k.mapFailure
import dev.forkhandles.result4k.peek
import dev.forkhandles.result4k.peekFailure
import dev.forkhandles.result4k.valueOrNull
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import io.opentelemetry.sdk.trace.export.SpanExporter
import org.http4k.ai.a2a.client.A2AClient
import org.http4k.ai.a2a.model.A2ARole
import org.http4k.ai.a2a.model.AgentCard
import org.http4k.ai.a2a.model.ContextId
import org.http4k.ai.a2a.model.Message
import org.http4k.ai.a2a.model.MessageId
import org.http4k.ai.a2a.model.MessageRequest
import org.http4k.ai.a2a.model.Part
import org.http4k.ai.a2a.model.ResponseStream
import org.http4k.ai.a2a.model.Task
import org.http4k.ai.a2a.model.TaskId
import org.http4k.ai.a2a.model.TaskState
import org.http4k.ai.a2a.model.TaskStatus
import org.http4k.ai.llm.LLMError
import org.http4k.ai.llm.LLMResult
import org.http4k.ai.llm.chat.Chat
import org.http4k.ai.llm.chat.ChatRequest
import org.http4k.ai.llm.chat.ChatResponse
import org.http4k.ai.llm.model.Content
import org.http4k.ai.llm.model.Message.User
import org.http4k.ai.llm.model.ModelParams
import org.http4k.ai.llm.tools.LLMTool
import org.http4k.ai.llm.tools.LLMTools
import org.http4k.ai.llm.tools.McpLLMTools
import org.http4k.ai.llm.tools.ToolRequest
import org.http4k.ai.llm.tools.ToolResponse
import org.http4k.ai.model.ModelName
import org.http4k.core.PolyHandler
import org.http4k.core.then
import org.http4k.filter.OpenTelemetryTracing
import org.http4k.filter.PolyFilters
import org.http4k.routing.RoutingToolHandler
import org.http4k.routing.a2aJsonRpc
import org.http4k.ai.llm.model.Message as LLMMessage

interface ReActAgent {
    val card: AgentCard

    operator fun invoke(llm: Chat, tools: LLMTools = NoTools()): PolyHandler {
        return a2aJsonRpc(card, messageHandler = { request ->
            val query = request.message.parts.filterIsInstance<Part.Text>().joinToString(" ") { it.text }
            val taskId = TaskId.random()
            val contextId = ContextId.random()

            ResponseStream(sequence {
                yield(inProgress(taskId, request, contextId))

                llm.reactLoop(query, tools)
                    .peek { yield(completed(taskId, answer(it), contextId)) }
                    .peekFailure { yield(failed(taskId, answer(it), contextId)) }
            })
        })
    }

    private fun inProgress(taskId: TaskId, request: MessageRequest, contextId: ContextId): Task = Task(
        id = taskId,
        status = TaskStatus(state = TaskState.TASK_STATE_WORKING),
        contextId = contextId,
        history = listOf(request.message)
    )

    private fun completed(taskId: TaskId, message: Message, contextId: ContextId): Task = Task(
        id = taskId,
        status = TaskStatus(state = TaskState.TASK_STATE_COMPLETED, message = message),
        contextId = contextId
    )

    private fun failed(taskId: TaskId, message: Message, contextId: ContextId): Task = Task(
        id = taskId,
        status = TaskStatus(state = TaskState.TASK_STATE_FAILED, message = message),
        contextId = contextId
    )

    private fun Chat.reactLoop(query: String, tools: LLMTools): LLMResult<ChatResponse> {
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

    private fun answer(response: ChatResponse): Message = Message(
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

    private fun answer(error: LLMError): Message = Message(
        messageId = MessageId.random(),
        role = A2ARole.ROLE_AGENT,
        parts = listOf(Part.Text(error.toString()))
    )
}

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

class TracedTools(private val source: String, private val destination: String, private val tools: LLMTools, private val telemetry: OpenTelemetry) : LLMTools by tools {
    override fun invoke(request: ToolRequest): LLMResult<ToolResponse> {
        return telemetry.getTracer("agents-playground")
            .spanBuilder(
                when (tools) {
                    is AgentTool -> "task"
                    is McpLLMTools -> "tools/call ${request.name}"
                    is RoutingToolHandler -> request.name.value
                    else -> ""
                }
            )
            .let {
                when (tools) {
                    is AgentTool -> it.setAttribute("service.peer.name", destination)
                    else -> it
                        .setAttribute("service.name", source)
                        .setAttribute("service.peer.name", destination)
                }
            }
            .setSpanKind(SpanKind.CLIENT)
            .startSpan()
            .useSpan { tools(request) }
    }
}

inline fun <T> Span.useSpan(block: () -> T): T {
    val scope = makeCurrent()
    return try {
        block()
    } catch (t: Throwable) {
        recordException(t)
        setStatus(StatusCode.ERROR)
        throw t
    } finally {
        scope.close()
        end()
    }
}

fun LLMTools.traced(
    source: String,
    destination: String,
    telemetry: OpenTelemetry
) = TracedTools(source, destination, this, telemetry)

fun PolyHandler.traced(telemetry: OpenTelemetry) = PolyFilters.OpenTelemetryTracing(telemetry).then(this)

object ConfigurableTelemetry {
    operator fun invoke(exporter: SpanExporter): OpenTelemetrySdk = OpenTelemetrySdk
        .builder()
        .setTracerProvider(
            SdkTracerProvider
                .builder()
                .addResource(
                    Resource.create(
                        Attributes.of(AttributeKey.stringKey("service.name"), "cooking-assistant")
                    )
                )
                .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                .build()
        )
        .build()
}

fun ModelName.Companion.inherited() = ModelName.of("inherited")

class FixedModelChat(private val llm: Chat, private val model: ModelName) : Chat {
    override fun invoke(request: ChatRequest): LLMResult<ChatResponse> {
        return llm(request.copy(params = request.params.copy(modelName = model)))
    }
}

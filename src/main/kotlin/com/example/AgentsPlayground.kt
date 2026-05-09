package com.example

import dev.forkhandles.result4k.flatMap
import dev.forkhandles.result4k.peek
import dev.forkhandles.result4k.valueOrNull
import org.http4k.ai.a2a.model.A2ARole.ROLE_AGENT
import org.http4k.ai.a2a.model.AgentCapabilities
import org.http4k.ai.a2a.model.AgentCard
import org.http4k.ai.a2a.model.AgentSkill
import org.http4k.ai.a2a.model.ContextId
import org.http4k.ai.a2a.model.Message
import org.http4k.ai.a2a.model.MessageId
import org.http4k.ai.a2a.model.Part
import org.http4k.ai.a2a.model.ResponseStream
import org.http4k.ai.a2a.model.SkillId
import org.http4k.ai.a2a.model.Task
import org.http4k.ai.a2a.model.TaskId
import org.http4k.ai.a2a.model.TaskState
import org.http4k.ai.a2a.model.TaskState.TASK_STATE_WORKING
import org.http4k.ai.a2a.model.TaskStatus
import org.http4k.ai.a2a.model.Version
import org.http4k.ai.llm.chat.Chat
import org.http4k.ai.llm.chat.ChatRequest
import org.http4k.ai.llm.chat.OpenAI
import org.http4k.ai.llm.model.Content
import org.http4k.ai.llm.model.ModelParams
import org.http4k.ai.mcp.ToolRequest
import org.http4k.ai.mcp.protocol.ServerMetaData
import org.http4k.ai.mcp.protocol.messages.toLLM
import org.http4k.ai.mcp.server.security.NoMcpSecurity
import org.http4k.ai.mcp.testing.testMcpClient
import org.http4k.ai.mcp.toLLM
import org.http4k.ai.model.ApiKey
import org.http4k.client.JavaHttpClient
import org.http4k.connect.model.MimeType
import org.http4k.connect.openai.FakeOpenAI
import org.http4k.connect.openai.OpenAIModels
import org.http4k.core.HttpHandler
import org.http4k.core.PolyHandler
import org.http4k.core.then
import org.http4k.filter.DebuggingFilters.PrintRequest
import org.http4k.routing.a2aJsonRpc
import org.http4k.routing.mcp
import org.http4k.server.Jetty
import org.http4k.server.asServer
import java.time.Duration
import java.util.*
import org.http4k.ai.llm.model.Message as LLMMessage

val recipeAgentCard = AgentCard(
    name = "Recipe Agent",
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

object App {
    operator fun invoke(
        llm: Chat,
        outgoing: HttpHandler = JavaHttpClient()
    ): PolyHandler {
        val recipes = MealApiRecipes(outgoing)

        val mcp = mcp(
            metadata = ServerMetaData("mcp-server", "0.0.1"),
            security = NoMcpSecurity,
            SearchRecipesTool(recipes)
        )

        // TODO: should not use a test client BUT I am not sure yet how to create a client for an in-memory mcp handler
        val mcpClient = mcp.testMcpClient().apply {
            start(Duration.ofSeconds(1))
        }

        val llmTools = mcpClient.tools()
            .list()
            .valueOrNull()
            .orEmpty()
            .map { it.toLLM() }

        val agent = a2aJsonRpc(recipeAgentCard, messageHandler = { request ->
            val query = request.message.parts.filterIsInstance<Part.Text>().joinToString(" ") { it.text }
            val taskId = TaskId.of(UUID.randomUUID().toString())
            val contextId = ContextId.of(UUID.randomUUID().toString())

            val history = mutableListOf<LLMMessage>()

            ResponseStream(sequence {
                yield(
                    Task(
                        id = taskId,
                        status = TaskStatus(state = TASK_STATE_WORKING),
                        contextId = contextId,
                        history = listOf(request.message)
                    )
                )

                history.add(
                    LLMMessage.User("Give me the list of recipes for $query")
                )

                llm(
                    ChatRequest(
                        messages = history,
                        params = ModelParams(
                            modelName = OpenAIModels.GPT4,
                            tools = llmTools
                        )
                    )
                ).flatMap {
                    val toolRequest = it.message.toolRequests.first() // TODO: need to understand how to deal with many tool calls

                    mcpClient
                        .tools()
                        .call(toolRequest.name, ToolRequest(toolRequest.arguments))
                        .valueOrNull()!!
                        .toLLM(toolRequest)
                }
                    .peek {
                        history.add(it.result)
                    }
                    .flatMap {
                        llm(
                            ChatRequest(
                                messages = history,
                                params = ModelParams(
                                    modelName = OpenAIModels.GPT4,
                                    tools = llmTools
                                )
                            )
                        )
                    }.peek {
                        yield(
                            Task(
                                id = taskId,
                                status = TaskStatus(
                                    state = TaskState.TASK_STATE_COMPLETED,
                                    message = Message(
                                        messageId = MessageId.random(),
                                        role = ROLE_AGENT,
                                        parts = listOf(
                                            Part.Text(
                                                it.message.contents
                                                    .filterIsInstance<Content.Text>()
                                                    .joinToString("\n") { it.text }
                                            )
                                        )
                                    )
                                ),
                                contextId = contextId
                            ),
                        )
                    }
            })
        })

        return agent
    }
}

fun main() {
    val openApiServer = FakeOpenAI()

    val llm = Chat.OpenAI(apiKey = ApiKey.of("test"), http = openApiServer)

    val printingApp: PolyHandler = PrintRequest().then(App(llm))

    val server = printingApp.asServer(Jetty(9000)).start()

    println("Recipe Agent running on http://localhost:9000")
    println("Agent Card at http://localhost:9000/.well-known/agent-card.json")

    println("Agent started on " + server.port())
}

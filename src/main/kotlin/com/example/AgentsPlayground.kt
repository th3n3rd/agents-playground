package com.example

import dev.forkhandles.result4k.map
import dev.forkhandles.result4k.peek
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
import org.http4k.ai.mcp.ToolRequest
import org.http4k.ai.mcp.ToolResponse
import org.http4k.ai.mcp.model.Tool
import org.http4k.ai.mcp.model.string
import org.http4k.ai.mcp.protocol.ServerMetaData
import org.http4k.ai.mcp.server.security.NoMcpSecurity
import org.http4k.ai.mcp.testing.testMcpClient
import org.http4k.ai.model.ToolName
import org.http4k.client.JavaHttpClient
import org.http4k.connect.model.MimeType
import org.http4k.core.HttpHandler
import org.http4k.core.PolyHandler
import org.http4k.core.then
import org.http4k.filter.DebuggingFilters.PrintRequest
import org.http4k.lens.with
import org.http4k.routing.a2aJsonRpc
import org.http4k.routing.bind
import org.http4k.routing.mcp
import org.http4k.server.Jetty
import org.http4k.server.asServer
import java.time.Duration
import java.util.*

val recipeAgentCard = AgentCard(
    name = "Recipe Agent",
    version = Version.of("1.0.0"),
    description = "An agent that helps users find and explore recipes",
    capabilities = AgentCapabilities(streaming = true),
    defaultInputModes = listOf(MimeType.of("text/plain")),
    defaultOutputModes = listOf(MimeType.of("text/plain")),
    skills = listOf(
        AgentSkill(
            id = SkillId.of("find-recipe"),
            name = "Find Recipe",
            description = "Search for recipes by ingredients or cuisine",
            tags = listOf("cooking", "recipes", "search")
        ),
        AgentSkill(
            id = SkillId.of("nutrition"),
            name = "Nutrition Info",
            description = "Get nutritional breakdown for a recipe",
            tags = listOf("nutrition", "health")
        )
    )
)

object SearchRecipesTool {
    val name = ToolName.of("search_recipes")
    val query = Tool.Arg.string().required("query")

    operator fun invoke(recipes: Recipes) = Tool(
        name.value,
        "Search recipes",
        query
    ) bind {
        ToolResponse.Ok(
            recipes.findAllBy(query(it))
                .mapIndexed { index, recipe -> "${index + 1} ${recipe.name}" }
                .joinToString("\n")
        )
    }
}

object App {
    operator fun invoke(outgoing: HttpHandler = JavaHttpClient()): PolyHandler {
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

        val agent = a2aJsonRpc(recipeAgentCard, messageHandler = { request ->
            val query = request.message.parts.filterIsInstance<Part.Text>().joinToString(" ") { it.text }
            val taskId = TaskId.of(UUID.randomUUID().toString())
            val contextId = ContextId.of(UUID.randomUUID().toString())

            ResponseStream(sequence {
                yield(
                    Task(
                        id = taskId,
                        status = TaskStatus(state = TASK_STATE_WORKING),
                        contextId = contextId,
                        history = listOf(request.message)
                    )
                )

                mcpClient
                    .tools()
                    .call(SearchRecipesTool.name, ToolRequest().with(SearchRecipesTool.query of query))
                    .map {
                        when (it) {
                            is ToolResponse.Ok -> it.content
                            else -> TODO()
                        }
                    }
                    .peek {
                        it?.let {
                            yield(
                                Task(
                                    id = taskId,
                                    status = TaskStatus(
                                        state = TaskState.TASK_STATE_COMPLETED,
                                        message = Message(
                                            messageId = MessageId.random(),
                                            role = ROLE_AGENT,
                                            parts = listOf(Part.Text("Found recipes for: $query\n\n$it"))
                                        )
                                    ),
                                    contextId = contextId
                                )
                            )
                        }
                    }

            })
        })

        return agent
    }
}

fun main() {
    val printingApp: PolyHandler = PrintRequest().then(App())

    val server = printingApp.asServer(Jetty(9000)).start()

    println("Recipe Agent running on http://localhost:9000")
    println("Agent Card at http://localhost:9000/.well-known/agent-card.json")

    println("Agent started on " + server.port())
}

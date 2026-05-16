package com.example

import io.opentelemetry.api.OpenTelemetry
import org.http4k.ai.a2a.client.testA2AJsonRpcClient
import org.http4k.ai.llm.chat.Chat
import org.http4k.ai.llm.chat.OpenAI
import org.http4k.ai.llm.tools.McpLLMTools
import org.http4k.ai.mcp.testing.testMcpClient
import org.http4k.ai.model.ApiKey
import org.http4k.client.JavaHttpClient
import org.http4k.connect.openai.FakeOpenAI
import org.http4k.connect.openai.OpenAIModels
import org.http4k.core.HttpHandler
import org.http4k.core.PolyHandler
import org.http4k.core.then
import org.http4k.filter.DebuggingFilters.PrintRequest
import org.http4k.filter.OpenTelemetryTracing
import org.http4k.filter.PolyFilters
import org.http4k.routing.tools
import org.http4k.server.Jetty
import org.http4k.server.asServer
import java.time.Duration

object App {
    operator fun invoke(
        llm: Chat,
        outgoing: HttpHandler = JavaHttpClient(),
        telemetry: OpenTelemetry = OpenTelemetry.noop()
    ): PolyHandler {
        val recipes = MealApiRecipes(outgoing)

        val recipeAgent = RecipesAgent(
            llm = llm,
            tools = RecipesMcp(recipes)
                .testMcpClient() // TODO: should not use a test client BUT I am not sure yet how to create a client for an in-memory mcp handler
                .apply { start(Duration.ofSeconds(1)) }
                .let {
                    TracedTools(
                        source = RecipesAgent.card.name,
                        destination = RecipesMcp.definition.name.value,
                        tools = McpLLMTools(it),
                        telemetry = telemetry
                    )
                }
        )

        val shoppingListAgent = ShoppingListAgent(
            llm = llm,
            tools = tools(
                TracedTools(
                    ShoppingListAgent.card.name,
                    ShoppingListAgent.card.name,
                    FormatShoppingListTool(),
                    telemetry
                )
            )
        )

        return PolyFilters.OpenTelemetryTracing(telemetry).then(
            CoordinatorAgent(
                llm = llm,
                tools = tools(
                    TracedTools(
                        source = CoordinatorAgent.card.name,
                        destination = RecipesAgent.card.name,
                        tools = AgentTool(recipeAgent.testA2AJsonRpcClient()),
                        telemetry = telemetry
                    ), // TODO: should not use a test client BUT I am not sure yet how to create a client for an in-memory a2a handler
                    TracedTools(
                        source = CoordinatorAgent.card.name,
                        destination = ShoppingListAgent.card.name,
                        tools = AgentTool(shoppingListAgent.testA2AJsonRpcClient()),
                        telemetry = telemetry
                    )
                )
            )
        )
    }
}

fun main() {
    val openApiServer = FakeOpenAI()

    val llm = FixedModelChat(
        llm = Chat.OpenAI(apiKey = ApiKey.of("test"), http = openApiServer),
        model = OpenAIModels.GPT4
    )

    val printingApp: PolyHandler = PrintRequest().then(App(llm))

    val server = printingApp.asServer(Jetty(9000)).start()

    println("Recipe Agent running on http://localhost:9000")
    println("Agent Card at http://localhost:9000/.well-known/agent-card.json")

    println("Agent started on " + server.port())
}

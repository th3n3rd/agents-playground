package com.example

import org.http4k.ai.llm.chat.Chat
import org.http4k.ai.llm.chat.OpenAI
import org.http4k.ai.mcp.testing.testMcpClient
import org.http4k.ai.model.ApiKey
import org.http4k.client.JavaHttpClient
import org.http4k.connect.openai.FakeOpenAI
import org.http4k.core.HttpHandler
import org.http4k.core.PolyHandler
import org.http4k.core.then
import org.http4k.filter.DebuggingFilters.PrintRequest
import org.http4k.server.Jetty
import org.http4k.server.asServer
import java.time.Duration

object App {
    operator fun invoke(
        llm: Chat,
        outgoing: HttpHandler = JavaHttpClient()
    ): PolyHandler {
        val recipes = MealApiRecipes(outgoing)

        val mcpClient = RecipesMcp(recipes)
            .testMcpClient() // TODO: should not use a test client BUT I am not sure yet how to create a client for an in-memory mcp handler
            .apply { start(Duration.ofSeconds(1)) }

        return RecipesAgent(llm, mcpClient)
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

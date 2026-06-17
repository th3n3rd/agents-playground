package com.example

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.containsSubstring
import org.http4k.ai.a2a.client.testA2AJsonRpcClient
import org.http4k.ai.llm.chat.Chat
import org.http4k.ai.llm.chat.OpenAI
import org.http4k.routing.reverseProxy
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("local-llm")
class LocalLanguageModelAcceptanceTests {
    private val mealApiServer = FakeMealApiServer()

    private val llm = FixedModelChat(
        llm = Chat.OpenAI(LocalLanguageModelClient.ollama()),
        model = LocalLanguageModels.LLAMA31_8B
    )

    private val app = App(
        llm = llm,
        outgoing = reverseProxy(mealApiServer.uri.authority to mealApiServer),
    )

    private val chef = AmateurChef(app.testA2AJsonRpcClient())

    @Test
    fun `provides the shopping list for 'spaghetti alla carbonara'`() {
        val answer = chef.asks("Generate the shopping list for spaghetti alla carbonara").lowercase()

        val expectedEntries = listOf(
            "320g of spaghetti",
            "6 egg yolk",
            "150g of bacon",
            "50g of pecorino",
            "salt to taste",
            "pepper to taste"
        )

        expectedEntries.forEach { assertThat(answer, containsSubstring(it)) }
    }
}

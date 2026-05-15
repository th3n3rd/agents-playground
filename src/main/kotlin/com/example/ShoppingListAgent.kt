package com.example

import dev.forkhandles.result4k.asSuccess
import org.http4k.ai.a2a.model.AgentCapabilities
import org.http4k.ai.a2a.model.AgentCard
import org.http4k.ai.a2a.model.AgentSkill
import org.http4k.ai.a2a.model.SkillId
import org.http4k.ai.a2a.model.Version
import org.http4k.ai.llm.tools.LLMTool
import org.http4k.ai.llm.tools.ToolResponse
import org.http4k.ai.model.ToolName
import org.http4k.connect.model.MimeType
import org.http4k.routing.bind

object ShoppingListAgent : ReActAgent {
    override val card = AgentCard(
        name = "shopping-list-agent",
        version = Version.of("1.0.0"),
        description = "An agent that generates a shopping list from a recipe description",
        capabilities = AgentCapabilities(streaming = true),
        defaultInputModes = listOf(MimeType.of("text/plain")),
        defaultOutputModes = listOf(MimeType.of("text/plain")),
        skills = listOf(
            AgentSkill(
                id = SkillId.of("generate-shopping-list"),
                name = "Generate Shopping List",
                description = "Generate a shopping list from a recipe description",
                tags = listOf("shopping", "ingredients", "meal-planning")
            )
        )
    )
}

object FormatShoppingListTool {
    val definition = LLMTool(
        name = ToolName.of("format-shopping-list"),
        description = "Formats raw ingredients into a shopping list",
        inputSchema = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "ingredients" to mapOf(
                    "type" to "array",
                    "items" to mapOf("type" to "string"),
                    "description" to "List of ingredients"
                )
            ),
            "required" to listOf("ingredients")
        )
    )

    operator fun invoke() = definition bind { request ->
        val ingredients = request.arguments["ingredients"] as List<String>
        val list = ingredients.joinToString("\n") { "- ${it.trim()}" }
        ToolResponse(
            id = request.id,
            tool = request.name,
            text = list
        ).asSuccess()
    }
}

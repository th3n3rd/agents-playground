package com.example

import org.http4k.ai.a2a.model.AgentCapabilities
import org.http4k.ai.a2a.model.AgentCard
import org.http4k.ai.a2a.model.AgentSkill
import org.http4k.ai.a2a.model.SkillId
import org.http4k.ai.a2a.model.Version
import org.http4k.ai.llm.model.Message
import org.http4k.ai.mcp.ToolResponse
import org.http4k.ai.mcp.model.Tool
import org.http4k.ai.mcp.model.string
import org.http4k.ai.mcp.protocol.ServerMetaData
import org.http4k.ai.mcp.protocol.VersionedMcpEntity
import org.http4k.ai.mcp.server.security.NoMcpSecurity
import org.http4k.connect.model.MimeType
import org.http4k.core.PolyHandler
import org.http4k.routing.bind
import org.http4k.routing.mcp

object RecipesAgent : ReActAgent {
    override val systemPrompt = Message.System("""
        You are a recipe search agent.

        Rules:
        - Use the search_recipes tool whenever the user asks for a recipe by name, cuisine, ingredient, or meal idea.
        - Do not answer recipe lookup questions from memory.
        - Do not include ingredients or preparation details in your response unless they are included in the search results.
        - After receiving search results, return the complete recipe including ALL ingredients exactly as listed in the results.
        - Do not summarize, abbreviate, or omit any ingredient from the list.
    """.trimIndent())

    override val card = AgentCard(
        name = "recipe-agent",
        version = Version.of("1.0.0"),
        description = """
            An agent that finds full recipes, including ingredients and preparation details, from recipe names or cooking queries
        """.trimIndent(),
        capabilities = AgentCapabilities(streaming = true),
        defaultInputModes = listOf(MimeType.of("text/plain")),
        defaultOutputModes = listOf(MimeType.of("text/plain")),
        skills = listOf(
            AgentSkill(
                id = SkillId.of("search-recipes"),
                name = "Search Recipe",
                description = "Search for recipes by name and return the full recipe, including ingredients",
                tags = listOf("cooking", "recipes", "search", "ingredients")
            )
        )
    )
}

object RecipesMcp {
    val definition = VersionedMcpEntity(
        name = "recipes-mcp",
        version = "1.0.0",
    )

    operator fun invoke(recipes: Recipes): PolyHandler {
        return mcp(
            metadata = ServerMetaData("mcp-server", "0.0.1"),
            security = NoMcpSecurity,
            SearchRecipesTool(recipes)
        )
    }
}

object SearchRecipesTool {
    val query = Tool.Arg.string().required("query")
    val definition = Tool(
        "search_recipes",
        "Search recipes",
        query
    )

    operator fun invoke(recipes: Recipes) = definition bind { request ->
        ToolResponse.Ok(
            recipes.findAllBy(query(request))
                .mapIndexed { index, recipe ->
                    buildString {
                        appendLine("${index + 1}. ${recipe.name}")
                        appendLine("Ingredients:")
                        recipe.ingredients
                            .filter { it.first.isNotBlank() || it.second.isNotBlank() }
                            .sortedBy { (measure, _) -> if (measure.trim().any { c -> c.isDigit() }) 0 else 1 }
                            .forEach { (measure, ingredient) ->
                                val trimmed = measure.trim()
                                val entry = when {
                                    trimmed.isBlank() -> ingredient
                                    trimmed.equals("As required", ignoreCase = true) -> "$ingredient to taste"
                                    else -> "$trimmed $ingredient"
                                }
                                appendLine("- $entry")
                            }
                    }
                }
                .joinToString("\n")
        )
    }
}
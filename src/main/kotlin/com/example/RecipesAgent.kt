package com.example

import org.http4k.ai.a2a.model.AgentCapabilities
import org.http4k.ai.a2a.model.AgentCard
import org.http4k.ai.a2a.model.AgentSkill
import org.http4k.ai.a2a.model.SkillId
import org.http4k.ai.a2a.model.Version
import org.http4k.ai.mcp.ToolResponse
import org.http4k.ai.mcp.model.Tool
import org.http4k.ai.mcp.model.string
import org.http4k.ai.mcp.protocol.ServerMetaData
import org.http4k.ai.mcp.server.security.NoMcpSecurity
import org.http4k.ai.model.ToolName
import org.http4k.connect.model.MimeType
import org.http4k.core.PolyHandler
import org.http4k.routing.bind
import org.http4k.routing.mcp

object RecipesAgent : ReActAgent {
    override val card = AgentCard(
        name = "recipe-agent",
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
}

object RecipesMcp {
    operator fun invoke(recipes: Recipes): PolyHandler {
        return mcp(
            metadata = ServerMetaData("mcp-server", "0.0.1"),
            security = NoMcpSecurity,
            SearchRecipesTool(recipes)
        )
    }
}

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
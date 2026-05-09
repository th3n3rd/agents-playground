package com.example

import org.http4k.ai.mcp.ToolResponse
import org.http4k.ai.mcp.model.Tool
import org.http4k.ai.mcp.model.string
import org.http4k.ai.model.ToolName
import org.http4k.routing.bind

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
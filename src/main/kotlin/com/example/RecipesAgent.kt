package com.example

import org.http4k.ai.a2a.model.AgentCapabilities
import org.http4k.ai.a2a.model.AgentCard
import org.http4k.ai.a2a.model.AgentSkill
import org.http4k.ai.a2a.model.SkillId
import org.http4k.ai.a2a.model.Version
import org.http4k.connect.model.MimeType

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
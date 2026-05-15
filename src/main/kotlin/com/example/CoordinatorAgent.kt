package com.example

import org.http4k.ai.a2a.model.AgentCapabilities
import org.http4k.ai.a2a.model.AgentCard
import org.http4k.ai.a2a.model.AgentSkill
import org.http4k.ai.a2a.model.SkillId
import org.http4k.ai.a2a.model.Version
import org.http4k.connect.model.MimeType

object CoordinatorAgent : ReActAgent {
    override val card = AgentCard(
        name = "cooking-assistant-agent",
        version = Version.of("1.0.0"),
        description = "A cooking assistant that helps users discover recipes, explore meal ideas, and plan their shopping",
        capabilities = AgentCapabilities(streaming = true),
        defaultInputModes = listOf(MimeType.of("text/plain")),
        defaultOutputModes = listOf(MimeType.of("text/plain")),
        skills = listOf(
            AgentSkill(
                id = SkillId.of("cooking-assistance"),
                name = "cooking-assistance",
                description = "Answer any cooking-related question: find recipes, get ingredient lists, and build shopping lists",
                tags = listOf("cooking", "recipes", "shopping", "meal-planning")
            )
        )
    )
}

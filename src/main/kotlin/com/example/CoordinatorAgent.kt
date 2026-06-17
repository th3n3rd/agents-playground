package com.example

import org.http4k.ai.a2a.model.AgentCapabilities
import org.http4k.ai.a2a.model.AgentCard
import org.http4k.ai.a2a.model.AgentSkill
import org.http4k.ai.a2a.model.SkillId
import org.http4k.ai.a2a.model.Version
import org.http4k.ai.llm.model.Message
import org.http4k.connect.model.MimeType

object CoordinatorAgent : ReActAgent {
    override val systemPrompt = Message.System("""
        You are the coordinator agent.
        
        Routing rules:
        - If the user asks for a recipe by name, call recipe-agent.
        - If the user asks for a shopping list for a recipe name, first call recipe-agent to get the recipe and ingredients.
        - When calling shopping-list-agent, pass the COMPLETE ingredient list with ALL items and quantities exactly as received from recipe-agent. Do not omit any ingredient.
        - Only call shopping-list-agent after you have a full recipe description or explicit ingredient list.
        - Never call shopping-list-agent with only a recipe name.
        
        Final answer rules:
        - Do not mention sub-agents, tool calls, routing decisions, or internal steps.
        - Do not include raw output from recipe-agent unless it is part of the requested final answer.
        - If shopping-list-agent was called, return its complete shopping list. Include EVERY item from the list without omission or reformatting.
        - Answer as if you are one assistant, not a coordinator.
    """.trimIndent())

    override val card = AgentCard(
        name = "cooking-assistant-agent",
        version = Version.of("1.0.0"),
        description = """
            A cooking assistant that helps users discover recipes, explore meal ideas, and plan their shopping
        """.trimIndent(),
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

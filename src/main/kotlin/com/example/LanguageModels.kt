package com.example

import org.http4k.ai.a2a.model.AgentCard
import org.http4k.ai.llm.tools.LLMTool

fun AgentCard.toLLM(): LLMTool = LLMTool(
    name = name,
    description = """
        $description
        
        Skills: ${skills.joinToString { "${it.name}: ${it.description}" }}
        """.trimIndent(),
    inputSchema = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "query" to mapOf(
                "type" to "string",
                "description" to "The request to send to this agent"
            )
        ),
        "required" to listOf("query")
    )
)
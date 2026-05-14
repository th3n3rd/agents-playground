package com.example

import dev.forkhandles.result4k.asSuccess
import dev.forkhandles.result4k.map
import dev.forkhandles.result4k.mapFailure
import dev.forkhandles.result4k.valueOrNull
import org.http4k.ai.a2a.client.A2AClient
import org.http4k.ai.a2a.model.A2ARole
import org.http4k.ai.a2a.model.AgentCard
import org.http4k.ai.a2a.model.Message
import org.http4k.ai.a2a.model.MessageId
import org.http4k.ai.a2a.model.Part
import org.http4k.ai.a2a.model.Task
import org.http4k.ai.llm.LLMError
import org.http4k.ai.llm.LLMResult
import org.http4k.ai.llm.tools.LLMTool
import org.http4k.ai.llm.tools.LLMTools
import org.http4k.ai.llm.tools.ToolRequest
import org.http4k.ai.llm.tools.ToolResponse

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

class AgentTool(val client: A2AClient) : LLMTools {
    override fun list(): LLMResult<List<LLMTool>> {
        return client.agentCard()
            .map { listOf(it.toLLM()) }
            .mapFailure { LLMError.Internal(Exception(it.toString())) }
    }

    override fun invoke(request: ToolRequest): LLMResult<ToolResponse> {
        val query = request.arguments["query"].toString()

        val result = client.message( // TODO: support streaming (i.e. non blocking) responses
            Message(
                MessageId.Companion.random(),
                A2ARole.ROLE_USER,
                listOf(Part.Text(query))
            ) // TODO: need to understand what's the right parameter(s)
        ).valueOrNull()!!
            .let { it as Task }
            .status
            .message
            ?.parts?.filterIsInstance<Part.Text>()
            ?.joinToString("\n") { it.text }
            .orEmpty()

        return ToolResponse(
            id = request.id,
            tool = request.name,
            text = result
        ).asSuccess()
    }
}
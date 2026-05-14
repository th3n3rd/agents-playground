package com.example

import dev.forkhandles.result4k.map
import dev.forkhandles.result4k.peek
import org.http4k.ai.a2a.model.A2ARole
import org.http4k.ai.a2a.model.AgentCapabilities
import org.http4k.ai.a2a.model.AgentCard
import org.http4k.ai.a2a.model.AgentSkill
import org.http4k.ai.a2a.model.ContextId
import org.http4k.ai.a2a.model.MessageId
import org.http4k.ai.a2a.model.Part
import org.http4k.ai.a2a.model.ResponseStream
import org.http4k.ai.a2a.model.SkillId
import org.http4k.ai.a2a.model.Task
import org.http4k.ai.a2a.model.TaskId
import org.http4k.ai.a2a.model.TaskState
import org.http4k.ai.a2a.model.TaskStatus
import org.http4k.ai.a2a.model.Version
import org.http4k.ai.llm.chat.Chat
import org.http4k.ai.llm.chat.ChatResponse
import org.http4k.ai.llm.model.Content
import org.http4k.ai.llm.tools.LLMTools
import org.http4k.connect.model.MimeType
import org.http4k.core.PolyHandler
import org.http4k.routing.a2aJsonRpc
import java.util.UUID

object CoordinatorAgent {
    val card = AgentCard(
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

    operator fun invoke(llm: Chat, tools: LLMTools = NoTools()): PolyHandler {
        return a2aJsonRpc(card, messageHandler = { request ->
            val query = request.message.parts.filterIsInstance<Part.Text>().joinToString(" ") { it.text }
            val taskId = TaskId.random()
            val contextId = ContextId.random()

            ResponseStream(sequence {
                yield(
                    Task(
                        id = taskId,
                        status = TaskStatus(state = TaskState.TASK_STATE_WORKING),
                        contextId = contextId,
                        history = listOf(request.message)
                    )
                )

                llm.reactLoop(query, tools)
                    .map { answer(it) }
                    .peek {
                        yield(
                            Task(
                                id = taskId,
                                status = TaskStatus(state = TaskState.TASK_STATE_COMPLETED, message = it),
                                contextId = contextId
                            )
                        )
                    }
            })
        })
    }

    private fun answer(response: ChatResponse): org.http4k.ai.a2a.model.Message = org.http4k.ai.a2a.model.Message(
        messageId = MessageId.random(),
        role = A2ARole.ROLE_AGENT,
        parts = listOf(
            Part.Text(
                response.message.contents
                    .filterIsInstance<Content.Text>()
                    .joinToString("\n") { it.text }
            )
        )
    )
}

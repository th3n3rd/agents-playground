package com.example

import dev.forkhandles.result4k.map
import dev.forkhandles.result4k.peek
import dev.forkhandles.result4k.valueOrNull
import org.http4k.ai.a2a.model.*
import org.http4k.ai.llm.chat.Chat
import org.http4k.ai.llm.chat.ChatRequest
import org.http4k.ai.llm.model.Content
import org.http4k.ai.llm.model.Message
import org.http4k.ai.llm.model.ModelParams
import org.http4k.connect.model.MimeType
import org.http4k.connect.openai.OpenAIModels
import org.http4k.core.PolyHandler
import org.http4k.routing.a2aJsonRpc
import java.util.*

object ShoppingListAgent {
    val card = AgentCard(
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

    operator fun invoke(llm: Chat): PolyHandler {
        return a2aJsonRpc(card, messageHandler = { request ->
            val recipeText = request.message.parts.filterIsInstance<Part.Text>().joinToString(" ") { it.text }
            val taskId = TaskId.of(UUID.randomUUID().toString())
            val contextId = ContextId.of(UUID.randomUUID().toString())

            ResponseStream(sequence {
                yield(
                    Task(
                        id = taskId,
                        status = TaskStatus(state = TaskState.TASK_STATE_WORKING),
                        contextId = contextId,
                        history = listOf(request.message)
                    )
                )

                llm(
                    ChatRequest(
                        messages = listOf(Message.User("Generate a shopping list for the following recipe:\n\n$recipeText")),
                        params = ModelParams(modelName = OpenAIModels.GPT4)
                    )
                ).map { answer(it.message.contents.filterIsInstance<Content.Text>().joinToString("\n") { it.text }) }
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

    private fun answer(text: String): org.http4k.ai.a2a.model.Message = Message(
        messageId = MessageId.random(),
        role = A2ARole.ROLE_AGENT,
        parts = listOf(Part.Text(text))
    )
}

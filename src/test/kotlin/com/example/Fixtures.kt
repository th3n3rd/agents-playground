package com.example

import org.http4k.ai.llm.chat.ChatResponse
import org.http4k.ai.llm.model.Content
import org.http4k.ai.llm.tools.ToolRequest
import org.http4k.ai.model.RequestId
import org.http4k.ai.model.ResponseId
import org.http4k.ai.model.ToolName
import org.http4k.connect.openai.OpenAIModels
import java.util.*
import org.http4k.ai.llm.model.Message as LLMMessage

object Answers {
    object RequireToolExecution {
        operator fun invoke(name: ToolName, arguments: Map<String, Any>) = ChatResponse(
            message = LLMMessage.Assistant(
                toolRequests = listOf(
                    ToolRequest(
                        id = RequestId.of(UUID.randomUUID().toString()),
                        name = name,
                        arguments = arguments,
                    )
                )
            ),
            metadata = ChatResponse.Metadata(
                id = ResponseId.of(UUID.randomUUID().toString()),
                model = OpenAIModels.GPT4,
            )
        )
    }

    object TextReply {
        operator fun invoke(text: String) = ChatResponse(
            message = LLMMessage.Assistant(listOf(
                Content.Text(text)
            )),
            metadata = ChatResponse.Metadata(
                id = ResponseId.of(UUID.randomUUID().toString()),
                model = OpenAIModels.GPT4,
            )
        )
    }

    object DontKnowHowToRespond {
        operator fun invoke() = ChatResponse(
            message = LLMMessage.Assistant(
                listOf(
                    Content.Text("Not sure how to answer that")
                )
            ),
            metadata = ChatResponse.Metadata(
                id = ResponseId.of(UUID.randomUUID().toString()),
                model = OpenAIModels.GPT4,
            )
        )
    }
}
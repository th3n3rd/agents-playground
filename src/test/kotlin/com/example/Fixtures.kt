package com.example

import org.http4k.ai.llm.chat.ChatResponse
import org.http4k.ai.llm.model.Content
import org.http4k.ai.llm.model.Message
import org.http4k.ai.model.ResponseId
import org.http4k.connect.openai.OpenAIModels
import java.util.*

object Answers {
    object DownKnowHowToRespond {
        operator fun invoke() = ChatResponse(
            message = Message.Assistant(
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
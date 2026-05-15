package com.example

import dev.forkhandles.result4k.Failure
import dev.forkhandles.result4k.Success
import org.http4k.ai.llm.LLMError
import org.http4k.ai.llm.LLMResult
import org.http4k.ai.llm.chat.Chat
import org.http4k.ai.llm.chat.ChatRequest
import org.http4k.ai.llm.chat.ChatResponse
import org.http4k.ai.model.ModelName
import java.util.*

class ScriptedChat(vararg responses: (ChatRequest) -> ChatResponse) : Chat {
    private val responses = LinkedList(responses.toList())

    override fun invoke(request: ChatRequest): LLMResult<ChatResponse> {
        if (request.params.modelName != model) {
            return Failure(LLMError.Internal(Exception("Model ${request.params.modelName} not supported by ScriptedChat, expected $model")))
        }
        return Success(responses.remove()(request))
    }

    companion object {
        val model = ModelName.of("scripted")
    }
}
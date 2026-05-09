package com.example

import dev.forkhandles.result4k.Success
import org.http4k.ai.llm.LLMResult
import org.http4k.ai.llm.chat.Chat
import org.http4k.ai.llm.chat.ChatRequest
import org.http4k.ai.llm.chat.ChatResponse
import java.util.*

class ScriptedChat(vararg responses: (ChatRequest) -> ChatResponse) : Chat {
    private val responses = LinkedList(responses.toList())

    override fun invoke(request: ChatRequest): LLMResult<ChatResponse> {
        return Success(responses.remove()(request))
    }
}
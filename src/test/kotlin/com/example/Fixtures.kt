package com.example

import dev.forkhandles.result4k.Failure
import dev.forkhandles.result4k.Success
import org.http4k.ai.llm.LLMError
import org.http4k.ai.llm.LLMResult
import org.http4k.ai.llm.OpenAICompatibleClient
import org.http4k.ai.llm.chat.Chat
import org.http4k.ai.llm.chat.ChatRequest
import org.http4k.ai.llm.chat.ChatResponse
import org.http4k.ai.llm.model.Content
import org.http4k.ai.llm.tools.ToolRequest
import org.http4k.ai.model.ModelName
import org.http4k.ai.model.RequestId
import org.http4k.ai.model.ResponseId
import org.http4k.ai.model.ToolName
import org.http4k.client.JavaHttpClient
import org.http4k.connect.openai.OpenAI
import org.http4k.connect.openai.OpenAIAction
import org.http4k.connect.openai.OpenAIModels
import org.http4k.core.HttpHandler
import org.http4k.core.Uri
import org.http4k.core.then
import org.http4k.filter.ClientFilters.SetBaseUriFrom
import org.http4k.filter.debug
import java.util.*
import org.http4k.ai.llm.model.Message as LLMMessage

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



object Answers {
    object OnUserMessage {
        operator fun invoke(text: String, answer: () -> ChatResponse): (ChatRequest) -> ChatResponse = { request ->
            if (request.messages.last() == LLMMessage.User(text)) answer()
            else DontKnowHowToRespond()
        }
    }

    object OnToolResult {
        operator fun invoke(answer: (LLMMessage.ToolResult) -> ChatResponse): (ChatRequest) -> ChatResponse = { request ->
            val last = request.messages.last()
            if (last is LLMMessage.ToolResult) answer(last)
            else DontKnowHowToRespond()
        }
    }

    object OnAnyUserMessage {
        operator fun invoke(answer: (LLMMessage.User) -> ChatResponse): (ChatRequest) -> ChatResponse = { request ->
            val last = request.messages.last()
            if (last is LLMMessage.User) answer(last)
            else DontKnowHowToRespond()
        }
    }

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

class LocalLanguageModelClient(private val http: HttpHandler = JavaHttpClient()) : OpenAICompatibleClient {
    override fun invoke() = object : OpenAI {
        override fun <R> invoke(action: OpenAIAction<R>) = action.toResult(http(action.toRequest()))
    }

    companion object {
        fun ollama(debug: Boolean = false): LocalLanguageModelClient {
            val http = SetBaseUriFrom(Uri.of("http://127.0.0.1:11434")).then(JavaHttpClient())
            return LocalLanguageModelClient(if (debug) http.debug() else http)
        }
    }
}

object LocalLanguageModels {
    val LLAMA31_8B = ModelName.of("llama3.1:8b")
}
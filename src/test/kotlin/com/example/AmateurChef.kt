package com.example

import dev.forkhandles.result4k.valueOrNull
import org.http4k.ai.a2a.client.A2AClient
import org.http4k.ai.a2a.model.A2ARole
import org.http4k.ai.a2a.model.Message
import org.http4k.ai.a2a.model.MessageId
import org.http4k.ai.a2a.model.Part
import org.http4k.ai.a2a.model.ResponseStream
import org.http4k.ai.a2a.model.Task

class AmateurChef(val client: A2AClient) {
    fun asks(query: String): String {
        val response = client.messageStream(
            Message(
                messageId = MessageId.Companion.random(),
                role = A2ARole.ROLE_USER,
                parts = listOf(Part.Text(query))
            )
        ).valueOrNull()!! as ResponseStream

        val last = response.last() as Task
        return last.status.message?.parts?.filterIsInstance<Part.Text>()?.joinToString("\n") { it.text } ?: ""
    }
}
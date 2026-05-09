package com.example

import dev.forkhandles.result4k.valueOrNull
import org.http4k.ai.a2a.client.HttpA2AClient
import org.http4k.ai.a2a.model.A2ARole.ROLE_USER
import org.http4k.ai.a2a.model.Message
import org.http4k.ai.a2a.model.MessageId
import org.http4k.ai.a2a.model.Part
import org.http4k.ai.a2a.model.ResponseStream
import org.http4k.core.Uri
import org.http4k.server.Jetty
import org.http4k.server.asServer

fun main() {
    // start the A2A server
    val server = app.asServer(Jetty(9000)).start()

    // create a client and start the connection
    HttpA2AClient(Uri.of("http://localhost:9000")).use { client ->
        val card = client.agentCard().valueOrNull()!!
        println("Connected to: ${card.name} (${card.skills.map { it.name }})")

        val response = client.messageStream(
            Message(MessageId.random(), ROLE_USER, listOf(Part.Text("pasta with mushrooms")))
        ).valueOrNull()!! as ResponseStream

        response.forEach { println(it) }
    }

    server.stop()
}

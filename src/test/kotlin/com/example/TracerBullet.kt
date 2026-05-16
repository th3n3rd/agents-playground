package com.example

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.sdk.trace.data.SpanData
import org.junit.jupiter.api.TestInfo
import java.io.File
import java.lang.reflect.Method

object TracerBullet {

    operator fun invoke(spans: List<SpanData>): String {
        return invoke(spans.map(Interaction::from))
    }

    operator fun invoke(interactions: List<Interaction>, x: Int = 1): String {
        return buildString {
            appendLine("sequenceDiagram")

            interactions
                .flatMap {
                    when (it.type) {
                        InteractionType.Mcp -> listOf(
                            it.startedAt to "note over ${it.from},${it.to}: MCP ${it.request.split(" ").joinToString("<br/>")}"
                        )
                        InteractionType.Http -> listOf(
                            it.startedAt to "${it.from}->>+${it.to}: ${it.request}",
                            it.endedAt to "${it.to}-->>-${it.from}: ${it.response}"
                        )
                    }
                }
                .sortedBy { it.first }
                .map { it.second }
                .forEach { appendLine("    $it") }

            interactions
                .flatMap {
                    listOf(
                        it.from to it.from.aliased(),
                        it.to to it.to.aliased()
                    )
                }
                .distinct()
                .sortedBy { it.second }
                .forEach { (name, alias) -> appendLine("    participant $name as $alias") }
        }
    }

    data class Interaction(
        val from: String = "unknown",
        val to: String = "unknown",
        val request: String = "",
        val response: String = "",
        val startedAt: Long = 0,
        val endedAt: Long = 0,
        val type: InteractionType = InteractionType.Http,
    ) {
        companion object {
            fun from(span: SpanData): Interaction = Interaction(
                from = when (span.kind) {
                    SpanKind.CLIENT -> span.serviceName()
                    SpanKind.SERVER -> "client" // TODO: simplification, ideally this should be passed as input?
                    else -> TODO()
                } ?: "unknown",
                to = when (span.kind) {
                    SpanKind.CLIENT -> span.servicePeerName()
                    SpanKind.SERVER -> span.serviceName()
                    else -> TODO()
                } ?: "unknown",
                request = span.name,
                response = span.httpResponseStatusCode()?.toString() ?: "",
                startedAt = span.startEpochNanos,
                endedAt = span.endEpochNanos,
                type = if (span.mcpMethodName() != null) InteractionType.Mcp else InteractionType.Http
            )
        }
    }

    enum class InteractionType { Http, Mcp }

    private fun SpanData.serviceName() = attributes[AttributeKey.stringKey("service.name")]
        ?: resource.attributes[AttributeKey.stringKey("service.name")]

    private fun SpanData.servicePeerName() = attributes[AttributeKey.stringKey("service.peer.name")]

    private fun SpanData.httpResponseStatusCode() = attributes[AttributeKey.longKey("http.response.status_code")]

    private fun SpanData.mcpMethodName() = attributes[AttributeKey.stringKey("mcp.method.name")]

    private fun String.aliased() = split("-").joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
}

object GenerateSequenceDiagramsDocs {
    operator fun invoke(spans: List<SpanData>, testInfo: TestInfo) {
        testInfo.testMethod.map { testMethod ->
            File("./docs/scenarios/${testMethod.toFileName()}.md").writeText(
                """
# ${testMethod.toMarkdownTitle()}

```mermaid    
${TracerBullet(spans)}
```
                """.trimIndent()
            )
        }
    }

    fun Method.toFileName() =
        name
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')

    fun Method.toMarkdownTitle() =
        name
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), " ")
            .replaceFirstChar { it.uppercase() }
            .trim()
}
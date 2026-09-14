package dev.kinetic.data.model

import dev.kinetic.core.model.*
import dev.kinetic.core.tools.ToolDefinition
import kotlinx.serialization.json.*

/** Partial arguments are progress only. Completion is withheld until a valid terminal event AND EOF. */
internal class ResponsesSseDecoder(private val turnId: String, tools: List<ToolDefinition>) {
    private val definitions = tools.associateBy { it.id }
    private val data = StringBuilder()
    private val text = StringBuilder()
    private val calls = linkedMapOf<String, Fragment>()
    private var responseId: String? = null
    private var completed: ModelResponse? = null
    private var delivered = false
    var output = JsonArray(emptyList())
        private set
    var usage: JsonObject? = null
        private set

    fun acceptLine(line: String): List<ModelStreamEvent> {
        if (line.isBlank()) return dispatch()
        if (line.startsWith(":")) return emptyList()
        if (completed != null) throw responseFailure("Data arrived after response completion.")
        if (line.startsWith("data:")) {
            if (data.isNotEmpty()) data.append('\n')
            data.append(line.removePrefix("data:").trimStart())
            if (data.length > 1_048_576) throw responseFailure("A response event exceeded local bounds.")
        }
        return emptyList()
    }

    fun finish(): ModelStreamEvent.Completed {
        dispatch()
        if (delivered) throw responseFailure("Response completion was repeated.")
        delivered = true
        return ModelStreamEvent.Completed(completed ?: throw responseFailure("The stream ended before completion."))
    }

    private fun dispatch(): List<ModelStreamEvent> {
        if (data.isEmpty()) return emptyList()
        if (completed != null) throw responseFailure("Response completion was repeated.")
        val event = try { Json.parseToJsonElement(data.toString()).jsonObject }
        catch (_: Exception) { throw responseFailure("Malformed response event.") }
        data.clear()
        val type = event.string("type")
        if (type == "error" || type == "response.failed") throw ModelProviderException(
            ModelProviderFailureKind.SERVER, "OpenAI stopped the response. Review the request before retrying.",
        )
        if (type == "response.created") {
            if (responseId != null) throw responseFailure("Response creation was repeated.")
            responseId = event.getValue("response").jsonObject.string("id")
            return listOf(ModelStreamEvent.Started)
        }
        if (responseId == null) throw responseFailure("Response creation is missing.")
        return when (type) {
            "response.output_text.delta" -> {
                val delta = event.string("delta")
                text.append(delta)
                if (text.length > 32_768) throw responseFailure("Response text exceeded local bounds.")
                listOf(ModelStreamEvent.TextDelta(delta))
            }
            "response.output_item.added" -> {
                val item = event.getValue("item").jsonObject
                when (item.string("type")) {
                    "function_call" -> {
                        val itemId = item.string("id")
                        val callId = item.string("call_id")
                        val name = item.string("name")
                        if (name !in definitions || calls.isNotEmpty() || callId.length !in 1..200 ||
                            item.string("arguments").isNotEmpty()) throw responseFailure("Invalid or unexposed function proposal.")
                        calls[itemId] = Fragment(callId, name)
                        listOf(ModelStreamEvent.ToolCallStarted(callId, name))
                    }
                    "message", "reasoning" -> emptyList()
                    else -> throw responseFailure("Only Kinetic function tools may be proposed.")
                }
            }
            "response.function_call_arguments.delta" -> {
                val fragment = calls[event.string("item_id")] ?: throw responseFailure("Unknown function item.")
                if (fragment.done != null) throw responseFailure("Arguments arrived after completion.")
                val delta = event.string("delta")
                fragment.arguments.append(delta)
                if (fragment.arguments.length > 8_192) throw responseFailure("Function arguments exceeded local bounds.")
                listOf(ModelStreamEvent.ToolArgumentDelta(fragment.callId, delta))
            }
            "response.function_call_arguments.done" -> {
                val fragment = calls[event.string("item_id")] ?: throw responseFailure("Unknown function item.")
                val arguments = event.string("arguments")
                if (fragment.done != null || arguments != fragment.arguments.toString()) {
                    throw responseFailure("Completed arguments differ from streamed arguments.")
                }
                fragment.done = arguments
                emptyList()
            }
            "response.completed" -> {
                complete(event.getValue("response").jsonObject)
                emptyList()
            }
            "error", "response.failed" -> throw ModelProviderException(
                ModelProviderFailureKind.SERVER, "OpenAI stopped the response. Retry only after reviewing the request.",
            )
            "response.incomplete" -> throw responseFailure("OpenAI returned an incomplete response; no action was authorized.")
            // These events are informational. Reasoning and refusal deltas are never rendered as thoughts.
            "response.in_progress", "response.content_part.added", "response.content_part.done",
            "response.output_text.done", "response.output_item.done", "response.refusal.delta", "response.refusal.done",
            "response.reasoning_summary_part.added", "response.reasoning_summary_part.done",
            "response.reasoning_summary_text.delta", "response.reasoning_summary_text.done",
            "response.reasoning_text.delta", "response.reasoning_text.done" -> emptyList()
            else -> throw responseFailure("An unsupported response event was received.")
        }
    }

    private fun complete(response: JsonObject) {
        if (response.string("id") != responseId || response.string("status") != "completed") {
            throw responseFailure("The final response identity or status is invalid.")
        }
        val items = response["output"] as? JsonArray ?: throw responseFailure("Response output is missing.")
        if (items.size > 32 || items.toString().length > 524_288) throw responseFailure("Response output exceeded local bounds.")
        val finalText = StringBuilder()
        val proposals = mutableListOf<dev.kinetic.core.tools.ToolCall>()
        items.forEach { element ->
            val item = element.jsonObject
            when (item.string("type")) {
                "message" -> {
                    if (item.string("role") != "assistant" || item.string("status") != "completed") {
                        throw responseFailure("Invalid assistant output.")
                    }
                    item.getValue("content").jsonArray.forEach { part ->
                        val content = part.jsonObject
                        finalText.append(when (content.string("type")) {
                            "output_text" -> content.string("text")
                            "refusal" -> content.string("refusal")
                            else -> throw responseFailure("Only text output is supported.")
                        })
                    }
                }
                "reasoning" -> {
                    // Encrypted continuation stays inside :data:model, never Room or user-visible output.
                    if ((item["encrypted_content"] as? JsonPrimitive)?.isString != true) {
                        throw responseFailure("Stateless reasoning continuation material is missing.")
                    }
                }
                "function_call" -> {
                    val fragment = calls[item.string("id")] ?: throw responseFailure("Unannounced function call.")
                    if (item.string("status") != "completed" || item.string("call_id") != fragment.callId ||
                        item.string("name") != fragment.name || item.string("arguments") != fragment.done) {
                        throw responseFailure("Final function call differs from its completed stream.")
                    }
                    var arguments = item.string("arguments")
                    if (fragment.name == "compose_email") {
                        val fields = Json.parseToJsonElement(arguments).jsonObject
                        if (fields.keys != setOf("recipient", "subject", "body")) throw responseFailure("Invalid email fields.")
                        arguments = JsonObject(fields.filterValues { it != JsonNull }).toString()
                    }
                    proposals += decodeToolCall(fragment.callId, fragment.name, arguments, turnId, 0, definitions)
                }
                else -> throw responseFailure("An unsupported hosted tool was returned.")
            }
        }
        if (proposals.size != calls.size || proposals.size > 1) throw responseFailure("Function output is incomplete.")
        if (text.isNotEmpty() && text.toString() != finalText.toString()) throw responseFailure("Final text differs from streamed text.")
        if (finalText.length > 32_768 || finalText.isBlank() && proposals.isEmpty()) throw responseFailure("Empty or oversized response.")
        output = items
        usage = response["usage"] as? JsonObject
        completed = ModelResponse(requireNotNull(responseId), finalText.toString(), proposals)
    }

    private class Fragment(val callId: String, val name: String) {
        val arguments = StringBuilder()
        var done: String? = null
    }
}

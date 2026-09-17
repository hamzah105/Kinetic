package dev.kinetic.data.model.mcp

import dev.kinetic.core.memory.SensitiveMemoryFilter
import kotlinx.serialization.json.*
import java.net.URI
import java.security.MessageDigest

const val MCP_VERSION = "2026-07-28"
enum class McpError {
    MCP_NOT_CONFIGURED, MCP_INVALID_ENDPOINT, MCP_NETWORK_UNAVAILABLE, MCP_TIMEOUT, MCP_TLS_FAILURE,
    MCP_AUTH_REQUIRED, MCP_PROTOCOL_ERROR, MCP_UNSUPPORTED_PROTOCOL, MCP_SCHEMA_INVALID,
    MCP_TOOL_NOT_FOUND, MCP_TOOL_DISABLED, MCP_TOOL_SCHEMA_CHANGED, MCP_RESULT_TOO_LARGE,
    MCP_INPUT_REQUIRED_UNSUPPORTED, MCP_EXTENSION_UNSUPPORTED, MCP_CANCELLED, MCP_REMOTE_ERROR,
}
class McpException(val code: McpError) : RuntimeException(code.name)
internal fun reject(code: McpError = McpError.MCP_PROTOCOL_ERROR): Nothing = throw McpException(code)

data class McpEndpoint(val serverId: String, val url: String) {
    init {
        require(serverId.matches(Regex("s_[a-f0-9]{32}")))
        if (url.length > 2048 || url.any { it.isWhitespace() || it.isISOControl() } || '\\' in url) reject(McpError.MCP_INVALID_ENDPOINT)
        val uri = try { URI(url) } catch (_: Exception) { reject(McpError.MCP_INVALID_ENDPOINT) }
        val host = uri.host.orEmpty().lowercase()
        if (uri.scheme != "https" || uri.userInfo != null || uri.fragment != null || uri.query != null ||
            (uri.port != -1 && uri.port !in 1..65535) || !host.matches(Regex("[a-z0-9-]+(?:\\.[a-z0-9-]+)+")) ||
            host.all { it.isDigit() || it == '.' } || host.endsWith(".local") || host.endsWith(".localhost") ||
            host.endsWith(".internal") || host.split('.').any { it.length > 63 || it.startsWith('-') || it.endsWith('-') } ||
            SensitiveMemoryFilter.isSensitive(url)) reject(McpError.MCP_INVALID_ENDPOINT)
    }
}

/** Scan before parsing: depth, duplicate keys, token count and strict JSON grammar. */
internal fun strictJson(text: String, limit: Int = 65536, maxNodes: Int = 2048): JsonElement {
    if (text.toByteArray(Charsets.UTF_8).size > limit) reject(McpError.MCP_RESULT_TOO_LARGE)
    var at = 0; var nodes = 0
    fun whitespace() { while (at < text.length && text[at] in " \r\n\t") at++ }
    fun string(): String {
        val start = at
        if (at >= text.length || text[at++] != '"') reject()
        var escaped = false
        while (at < text.length) {
            val ch = text[at++]
            if (!escaped && ch == '"') return try { Json.parseToJsonElement(text.substring(start, at)).jsonPrimitive.content } catch (_: Exception) { reject() }
            escaped = !escaped && ch == '\\'
        }
        reject()
    }
    fun value(depth: Int) {
        whitespace(); if (depth > 8 || ++nodes > maxNodes || at >= text.length) reject()
        when (text[at]) {
            '{', '[' -> {
                val objectValue = text[at++] == '{'; val end = if (objectValue) '}' else ']'
                val keys = mutableSetOf<String>(); whitespace()
                if (at < text.length && text[at] == end) { at++; return }
                while (true) {
                    whitespace()
                    if (objectValue) { if (!keys.add(string())) reject(); whitespace(); if (at >= text.length || text[at++] != ':') reject() }
                    value(depth + 1); whitespace()
                    if (at >= text.length) reject()
                    if (text[at] == end) { at++; break }
                    if (text[at++] != ',') reject()
                }
            }
            '"' -> string()
            else -> { val start = at; while (at < text.length && text[at] !in " \t\r\n,]}") at++
                if (start == at || !text.substring(start, at).matches(Regex("(?:true|false|null|-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?)"))) reject() }
        }
    }
    value(0); whitespace(); if (at != text.length) reject()
    return try { Json.parseToJsonElement(text) } catch (_: Exception) { reject() }
}

internal fun canonical(value: JsonElement): String = when (value) {
    is JsonObject -> value.keys.sorted().joinToString(",", "{", "}") { JsonPrimitive(it).toString() + ":" + canonical(value.getValue(it)) }
    is JsonArray -> value.joinToString(",", "[", "]", transform = ::canonical)
    else -> value.toString()
}
internal fun sha(text: String) = MessageDigest.getInstance("SHA-256").digest(text.toByteArray())
    .joinToString("") { "%02x".format(it.toInt() and 255) }
internal fun JsonObject.text(key: String): String = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: reject()

/** Deliberately narrow draft2020-12 subset: closed flat objects of bounded scalar values. */
class McpSchema(raw: String) {
    val json: JsonObject
    val preview: String get() = json.toString()
    val fingerprint: String
    private val properties: JsonObject
    private val required: Set<String>
    init {
        try {
            json = strictJson(raw, 8192) as? JsonObject ?: reject(McpError.MCP_SCHEMA_INVALID)
            require(json.keys.all { it in setOf("type", "properties", "required", "additionalProperties", "description", "title", "\$schema") })
            require(json.text("type") == "object" && json["additionalProperties"] == JsonPrimitive(false))
            listOf("description", "title").forEach { key -> json[key]?.let { require(json.text(key).length <= 1000) } }
            json["\$schema"]?.let { require(it == JsonPrimitive("https://json-schema.org/draft/2020-12/schema")) }
            properties = (json["properties"] as? JsonObject) ?: JsonObject(emptyMap()).also { require(json["properties"] == null) }
            require(properties.size <= 16)
            val requiredValues = (json["required"] as? JsonArray) ?: JsonArray(emptyList()).also { require(json["required"] == null) }
            required = requiredValues.map { (it as? JsonPrimitive)?.takeIf { v -> v.isString }?.content ?: reject() }.toSet()
            require(required.size == requiredValues.size && properties.keys.containsAll(required))
            val headers = mutableSetOf<String>()
            properties.forEach { (key, element) ->
                require(key.matches(Regex("[A-Za-z_][A-Za-z0-9_]{0,63}")))
                require(!Regex("(?i)(password|passcode|secret|token|api_?key|credential|authorization)").containsMatchIn(key))
                val property = element as? JsonObject ?: reject()
                listOf("description", "title").forEach { key -> property[key]?.let { require(property.text(key).length <= 1000) } }
                require(property.keys.all { it in setOf("type", "description", "title", "enum", "minLength", "maxLength", "minimum", "maximum", "x-mcp-header") })
                val type = property.text("type"); require(type in setOf("string", "integer", "boolean"))
                property["x-mcp-header"]?.let {
                    val header = property.text("x-mcp-header")
                    require(header.matches(Regex("[A-Za-z][A-Za-z0-9-]{0,39}")) && headers.add(header.lowercase()))
                }
                listOf("minLength", "maxLength").forEach { keyword -> property[keyword]?.let {
                    require(type == "string" && !it.jsonPrimitive.isString && it.jsonPrimitive.intOrNull in 0..2000)
                } }
                listOf("minimum", "maximum").forEach { keyword -> property[keyword]?.let {
                    require(type == "integer" && it.jsonPrimitive.longOrNull != null && !it.jsonPrimitive.isString)
                } }
                property["enum"]?.let { values -> require(values is JsonArray && values.size in 1..16)
                    values.forEach { require(validScalar(it, property, checkEnum = false)) } }
                require((property["minLength"]?.jsonPrimitive?.intOrNull ?: 0) <= (property["maxLength"]?.jsonPrimitive?.intOrNull ?: 2000))
                require((property["minimum"]?.jsonPrimitive?.longOrNull ?: Long.MIN_VALUE) <= (property["maximum"]?.jsonPrimitive?.longOrNull ?: Long.MAX_VALUE))
            }
            require(!SensitiveMemoryFilter.isSensitive(json.toString()))
            fingerprint = sha(canonical(JsonObject(json + ("required" to JsonArray(required.sorted().map(::JsonPrimitive))))))
        } catch (_: Exception) { reject(McpError.MCP_SCHEMA_INVALID) }
    }
    private fun validScalar(value: JsonElement, spec: JsonObject, checkEnum: Boolean = true): Boolean {
        val scalar = value as? JsonPrimitive ?: return false
        val valid = when (spec.text("type")) {
            "string" -> scalar.isString && scalar.content.length in
                ((spec["minLength"] as? JsonPrimitive)?.intOrNull ?: 0)..((spec["maxLength"] as? JsonPrimitive)?.intOrNull ?: 2000)
            "boolean" -> !scalar.isString && scalar.booleanOrNull != null
            "integer" -> !scalar.isString && scalar.longOrNull?.let {
                it in -9007199254740991L..9007199254740991L &&
                    it >= ((spec["minimum"] as? JsonPrimitive)?.longOrNull ?: Long.MIN_VALUE) &&
                    it <= ((spec["maximum"] as? JsonPrimitive)?.longOrNull ?: Long.MAX_VALUE)
            } == true
            else -> false
        }
        return valid && (!checkEnum || spec["enum"] == null || value in spec["enum"]!!.jsonArray)
    }
    fun arguments(raw: String): JsonObject {
        val value = strictJson(raw, 8192) as? JsonObject ?: reject(McpError.MCP_SCHEMA_INVALID)
        if (!value.keys.containsAll(required) || !properties.keys.containsAll(value.keys) ||
            value.any { (key, v) -> !validScalar(v, properties.getValue(key).jsonObject) } ||
            SensitiveMemoryFilter.isSensitive(value.toString()) || value.values.any { SensitiveMemoryFilter.isSensitive(it.jsonPrimitive.content) }) reject(McpError.MCP_SCHEMA_INVALID)
        return value
    }
    fun headers(arguments: JsonObject): Map<String, String> = buildMap {
        properties.forEach { (key, spec) ->
            spec.jsonObject["x-mcp-header"]?.let { annotation -> arguments[key]?.let { value ->
                val text = value.jsonPrimitive.content
                val safe = text == text.trim() && text.all { it.code in 32..126 || it == '\t' } && !(text.startsWith("=?base64?") && text.endsWith("?="))
                put("Mcp-Param-${annotation.jsonPrimitive.content}", if (safe) text else
                    "=?base64?${java.util.Base64.getEncoder().encodeToString(text.toByteArray(Charsets.UTF_8))}?=")
            } }
        }
    }
}

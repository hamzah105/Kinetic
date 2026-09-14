package dev.kinetic.data.model.mcp

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.InetAddress
import java.net.Proxy
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

interface McpTransport {
    suspend fun request(endpoint: McpEndpoint, method: String, params: JsonObject, headers: Map<String, String> = emptyMap()): JsonObject
}

internal fun publicAddress(address: InetAddress): Boolean {
    if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress || address.isSiteLocalAddress || address.isMulticastAddress) return false
    val bytes = address.address.map { it.toInt() and 255 }
    if (bytes.size == 16) return bytes[0] in 0x20..0x3f && !(bytes[0] == 0x20 && bytes[1] == 1 && bytes[2] == 0x0d && bytes[3] == 0xb8) && !(bytes[0] == 0x20 && bytes[1] == 2)
    val a = bytes[0]; val b = bytes[1]
    return a !in setOf(0, 10, 127) && a < 224 && !(a == 100 && b in 64..127) &&
        !(a == 169 && b == 254) && !(a == 172 && b in 16..31) && !(a == 192 && b in setOf(0, 168)) &&
        !(a == 198 && b in setOf(18, 19, 51)) && !(a == 203 && b == 0)
}

/** No TLS override, proxies, redirects, cookies, auth, downgrade, retries or endpoint discovery. */
class McpHttpTransport internal constructor(base: OkHttpClient) : McpTransport {
    constructor() : this(OkHttpClient.Builder().proxy(Proxy.NO_PROXY).dns { hostname ->
        Dns.SYSTEM.lookup(hostname).also { addresses ->
            if (addresses.isEmpty() || addresses.any { !publicAddress(it) }) throw IOException("MCP endpoint address denied")
        }
    }.build())
    private val client = base.newBuilder().followRedirects(false).followSslRedirects(false)
        .retryOnConnectionFailure(false).callTimeout(20, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build()

    override suspend fun request(endpoint: McpEndpoint, method: String, params: JsonObject, headers: Map<String, String>): JsonObject {
        if (method !in setOf("tools/list", "tools/call")) reject(McpError.MCP_EXTENSION_UNSUPPORTED)
        val id = UUID.randomUUID().toString()
        val payload = buildJsonObject {
            put("jsonrpc", "2.0"); put("id", id); put("method", method)
            put("params", JsonObject(params + ("_meta" to buildJsonObject {
                put("io.modelcontextprotocol/protocolVersion", MCP_VERSION)
                put("io.modelcontextprotocol/clientCapabilities", JsonObject(emptyMap()))
                put("io.modelcontextprotocol/clientInfo", buildJsonObject { put("name", "Kinetic"); put("version", "7a") })
            })))
        }.toString()
        if (payload.toByteArray().size > 16384) reject(McpError.MCP_RESULT_TOO_LARGE)
        val builder = Request.Builder().url(endpoint.url).header("Accept", "application/json, text/event-stream")
            .header("MCP-Protocol-Version", MCP_VERSION).header("Mcp-Method", method)
            .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
        if (method == "tools/call") builder.header("Mcp-Name", params.text("name"))
        headers.forEach { (name, value) ->
            if (!name.matches(Regex("Mcp-Param-[A-Za-z][A-Za-z0-9-]{0,39}")) || value.length > 4096) reject()
            builder.header(name, value)
        }
        return suspendCancellableCoroutine { continuation ->
            val call = client.newCall(builder.build())
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(McpException(when (e) {
                        is SocketTimeoutException -> McpError.MCP_TIMEOUT
                        is SSLException -> McpError.MCP_TLS_FAILURE
                        else -> McpError.MCP_NETWORK_UNAVAILABLE
                    }))
                }
                override fun onResponse(call: Call, response: Response) {
                    try {
                        val result = response.use { read(it, id) }
                        if (continuation.isActive) continuation.resume(result)
                    } catch (failure: Exception) {
                        if (continuation.isActive) continuation.resumeWithException(when (failure) {
                            is McpException -> failure
                            is SocketTimeoutException -> McpException(McpError.MCP_TIMEOUT)
                            else -> McpException(McpError.MCP_PROTOCOL_ERROR)
                        })
                    }
                }
            })
        }
    }
    private fun decode(bytes: ByteArray) = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()

    private fun read(response: Response, id: String): JsonObject {
        if (response.code in setOf(401, 403)) reject(McpError.MCP_AUTH_REQUIRED)
        if (response.code in 300..399) reject(McpError.MCP_INVALID_ENDPOINT)
        response.header("MCP-Protocol-Version")?.let { if (it != MCP_VERSION) reject(McpError.MCP_UNSUPPORTED_PROTOCOL) }
        val body = response.body
        val type = body.contentType() ?: reject()
        if (type.charset(Charsets.UTF_8) != Charsets.UTF_8) reject()
        if (body.contentLength() > 65536) reject(McpError.MCP_RESULT_TOO_LARGE)
        val source = body.source()
        if (type.type == "application" && type.subtype == "json") {
            val bytes = ByteArrayOutputStream()
            while (!source.exhausted()) { if (bytes.size() == 65536) reject(McpError.MCP_RESULT_TOO_LARGE); bytes.write(source.readByte().toInt()) }
            return envelope(strictJson(decode(bytes.toByteArray())).jsonObject, id)
        }
        if (type.type != "text" || type.subtype != "event-stream" || !response.isSuccessful) reject()
        var total = 0; var events = 0; val data = StringBuilder()
        while (!source.exhausted()) {
            val line = ByteArrayOutputStream()
            while (!source.exhausted()) {
                if (++total > 65536) reject(McpError.MCP_RESULT_TOO_LARGE)
                val byte = source.readByte().toInt() and 255
                if (byte == 10) break
                line.write(byte)
            }
            val text = decode(line.toByteArray()).removeSuffix("\r")
            when {
                text.isEmpty() && data.isNotEmpty() -> {
                    if (++events > 64) reject(McpError.MCP_RESULT_TOO_LARGE)
                    val event = strictJson(data.toString().removeSuffix("\n")).jsonObject; data.clear()
                    if (event["id"] != null) return envelope(event, id)
                    if (event.text("jsonrpc") != "2.0" || event.text("method") !in setOf("notifications/progress", "notifications/message")) reject(McpError.MCP_EXTENSION_UNSUPPORTED)
                }
                text.startsWith("data:") -> data.append(text.substring(5).removePrefix(" ")).append('\n')
                text.startsWith(":") || text.isEmpty() || text == "event: message" -> Unit
                else -> reject(McpError.MCP_EXTENSION_UNSUPPORTED)
            }
        }
        reject()
    }
    private fun envelope(value: JsonObject, id: String): JsonObject {
        if (value.text("jsonrpc") != "2.0" || value["id"] != JsonPrimitive(id) || value.containsKey("method") ||
            value.containsKey("error") == value.containsKey("result")) reject()
        value["error"]?.let { error -> reject(if ((error as? JsonObject)?.get("code") == JsonPrimitive(-32022))
            McpError.MCP_UNSUPPORTED_PROTOCOL else McpError.MCP_REMOTE_ERROR) }
        val result = value["result"] as? JsonObject ?: reject()
        when (result.text("resultType")) {
            "input_required" -> reject(McpError.MCP_INPUT_REQUIRED_UNSUPPORTED)
            "complete" -> Unit
            else -> reject(McpError.MCP_EXTENSION_UNSUPPORTED)
        }
        if ("task" in result || "inputRequests" in result) reject(McpError.MCP_EXTENSION_UNSUPPORTED)
        return result
    }
}

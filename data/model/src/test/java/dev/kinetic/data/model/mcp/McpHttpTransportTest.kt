package dev.kinetic.data.model.mcp

import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import mockwebserver3.*
import okhttp3.OkHttpClient
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*

class McpHttpTransportTest {
    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient
    private val requests = AtomicInteger()
    private var response: (JsonObject) -> MockResponse = { json -> jsonResponse(json) }
    @Before fun setup() {
        val certificate = HeldCertificate.Builder().addSubjectAlternativeName("mcp.example.com").build()
        val serverTrust = HandshakeCertificates.Builder().heldCertificate(certificate).build()
        val trust = HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
        server = MockWebServer().apply {
            useHttps(serverTrust.sslSocketFactory())
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    requests.incrementAndGet()
                    return response(obj(request.body!!.utf8()))
                }
            }
            start()
        }
        // TEST ONLY: a pinned synthetic certificate and local DNS; production has neither override.
        client = OkHttpClient.Builder().dns { listOf(InetAddress.getByName("127.0.0.1")) }
            .sslSocketFactory(trust.sslSocketFactory(), trust.trustManager).build()
    }
    @After fun close() { server.close() }
    private fun endpoint() = McpEndpoint("s_" + "a".repeat(32), "https://mcp.example.com:${server.port}/mcp")
    private fun jsonResponse(request: JsonObject, result: String = """{"resultType":"complete","tools":[]}""", code: Int = 200): MockResponse =
        MockResponse.Builder().code(code).addHeader("Content-Type", "application/json; charset=utf-8")
            .body("""{"jsonrpc":"2.0","id":${request["id"]},"result":$result}""").build()
    private suspend fun list(transport: McpHttpTransport = McpHttpTransport(client)) = transport.request(endpoint(), "tools/list", obj("{}"))

    @Test fun exact_stateless_protocol_headers_and_body_without_initialize() = runBlocking {
        assertEquals(JsonArray(emptyList()), list()["tools"])
        val sent = server.takeRequest()
        assertEquals("POST", sent.method)
        assertEquals(MCP_VERSION, sent.headers["MCP-Protocol-Version"])
        assertEquals("tools/list", sent.headers["Mcp-Method"])
        assertEquals("application/json, text/event-stream", sent.headers["Accept"])
        assertNull(sent.headers["Mcp-Session-Id"]); assertNull(sent.headers["Authorization"])
        val meta = obj(sent.body!!.utf8())["params"]!!.jsonObject["_meta"]!!.jsonObject
        assertEquals(JsonPrimitive(MCP_VERSION), meta["io.modelcontextprotocol/protocolVersion"])
        assertEquals(obj("{}"), meta["io.modelcontextprotocol/clientCapabilities"])
        assertEquals(1, requests.get())
    }
    @Test fun tools_call_sets_name_and_mirrored_headers_once() = runBlocking {
        McpHttpTransport(client).request(endpoint(), "tools/call", obj("""{"name":"remote.action","arguments":{}}"""), mapOf("Mcp-Param-Route" to "safe"))
        val sent = server.takeRequest()
        assertEquals("remote.action", sent.headers["Mcp-Name"]); assertEquals("safe", sent.headers["Mcp-Param-Route"])
        assertEquals(1, requests.get())
    }
    @Test fun per_request_sse_supported_without_legacy_stream_or_followup() = runBlocking {
        response = { request -> MockResponse.Builder().addHeader("Content-Type", "text/event-stream").body(
            "data: {\"jsonrpc\":\"2.0\",\"method\":\"notifications/progress\",\"params\":{}}\n\n" +
            "data: {\"jsonrpc\":\"2.0\",\"id\":${request["id"]},\"result\":{\"resultType\":\"complete\",\"tools\":[]}}\n\n").build() }
        list(); assertEquals(1, requests.get())
    }
    @Test fun malformed_json_id_mismatch_content_type_and_oversize_rejected() = runBlocking {
        for ((body, contentType) in listOf("{" to "application/json", "{}" to "text/html", "x".repeat(65537) to "application/json", """{"jsonrpc":"2.0","id":"wrong","result":{"resultType":"complete"}}""" to "application/json")) {
            response = { MockResponse.Builder().addHeader("Content-Type", contentType).body(body).build() }
            assertFailsWith<McpException> { list() }
        }
    }
    @Test fun redirects_auth_and_error_status_fail_without_retry() = runBlocking {
        for ((code, expected) in listOf(302 to McpError.MCP_INVALID_ENDPOINT, 401 to McpError.MCP_AUTH_REQUIRED, 403 to McpError.MCP_AUTH_REQUIRED, 500 to McpError.MCP_REMOTE_ERROR)) {
            response = { jsonResponse(it, code = code) }
            val before = requests.get()
            assertEquals(expected, assertFailsWith<McpException> { list() }.code)
            assertEquals(before + 1, requests.get())
        }
    }
    @Test fun incompatible_version_and_multiround_extensions_typed() = runBlocking {
        response = { request -> MockResponse.Builder().addHeader("Content-Type", "application/json").body("""{"jsonrpc":"2.0","id":${request["id"]},"error":{"code":-32022,"message":"secret remote text"}}""").code(400).build() }
        assertEquals(McpError.MCP_UNSUPPORTED_PROTOCOL, assertFailsWith<McpException> { list() }.code)
        for ((result, expected) in listOf("""{"resultType":"input_required"}""" to McpError.MCP_INPUT_REQUIRED_UNSUPPORTED, """{"resultType":"complete","task":{}}""" to McpError.MCP_EXTENSION_UNSUPPORTED)) {
            response = { jsonResponse(it, result) }
            assertEquals(expected, assertFailsWith<McpException> { list() }.code)
        }
    }
    @Test fun timeout_maps_safely_without_real_delay_or_retry() = runBlocking {
        val timed = client.newBuilder().addInterceptor { throw SocketTimeoutException("synthetic secret must not surface") }.build()
        val failure = assertFailsWith<McpException> { list(McpHttpTransport(timed)) }
        assertEquals(McpError.MCP_TIMEOUT, failure.code); assertFalse(failure.message!!.contains("synthetic")); assertEquals(0, requests.get())
    }
    @Test fun invalid_utf8_and_declared_charset_fail_closed() = runBlocking {
        response = { MockResponse.Builder().addHeader("Content-Type", "application/json").body(okio.Buffer().write(byteArrayOf(0xc3.toByte(), 0x28))).build() }
        assertEquals(McpError.MCP_PROTOCOL_ERROR, assertFailsWith<McpException> { list() }.code)
        response = { MockResponse.Builder().addHeader("Content-Type", "application/json; charset=unknown").body("{}").build() }
        assertEquals(McpError.MCP_PROTOCOL_ERROR, assertFailsWith<McpException> { list() }.code)
    }
    @Test fun actual_cross_origin_redirect_is_not_followed() = runBlocking {
        response = { MockResponse.Builder().code(307).addHeader("Location", "https://elsewhere.invalid/mcp").build() }
        assertEquals(McpError.MCP_INVALID_ENDPOINT, assertFailsWith<McpException> { list() }.code)
        assertEquals(1, requests.get())
    }
    @Test fun cancellation_cancels_underlying_call() = runBlocking {
        response = { jsonResponse(it).newBuilder().headersDelay(3, TimeUnit.SECONDS).build() }
        val job = launch { list() }
        withContext(Dispatchers.IO) { assertNotNull(server.takeRequest(5, TimeUnit.SECONDS)) }
        withTimeout(5000) { while (requests.get() == 0) delay(10) }
        job.cancelAndJoin()
        withTimeout(5000) { while (client.dispatcher.runningCallsCount() != 0) delay(10) }
        assertEquals(1, requests.get())
    }
}

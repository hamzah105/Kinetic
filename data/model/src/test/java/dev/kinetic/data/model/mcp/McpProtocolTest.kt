package dev.kinetic.data.model.mcp

import kotlinx.serialization.json.*
import org.junit.Test
import java.net.InetAddress
import kotlin.test.*

class McpProtocolTest {
    @Test fun endpoints_require_public_https_without_embedded_credentials() {
        val id = "s_" + "a".repeat(32)
        for (url in listOf("http://example.com", "https://u:p@example.com", "https://example.com/#fragment", "https://localhost", "https://127.0.0.1", "https://[::1]", "https://test.local", "https://example.com/?token=x", "https://-bad.example.com", "https://example.com/" + "x".repeat(2048))) {
            assertEquals(McpError.MCP_INVALID_ENDPOINT, assertFailsWith<McpException> { McpEndpoint(id, url) }.code)
        }
        McpEndpoint(id, "https://mcp.example.com/tools")
    }
    @Test fun dns_private_reserved_and_link_local_addresses_denied() {
        for (address in listOf("127.0.0.1", "10.0.0.1", "169.254.169.254", "192.168.1.1", "172.16.1.1", "100.64.1.1", "::1", "fe80::1", "fc00::1", "2001:db8::1", "2002:a00:1::")) assertFalse(publicAddress(InetAddress.getByName(address)), address)
        assertTrue(publicAddress(InetAddress.getByName("8.8.8.8")))
    }
    @Test fun strict_json_rejects_duplicates_depth_bad_tokens_and_size() {
        for (raw in listOf("{\"x\":1,\"x\":2}", "[1,]", "{x:1}", "[01]", "[NaN]", "[1] trailing", "[".repeat(10) + "0" + "]".repeat(10))) assertFailsWith<McpException>(raw) { strictJson(raw) }
        assertEquals(McpError.MCP_RESULT_TOO_LARGE, assertFailsWith<McpException> { strictJson("x".repeat(65537)) }.code)
    }
    @Test fun schema_fingerprint_ignores_object_order_and_required_order() {
        val a = McpSchema("""{"type":"object","properties":{"a":{"type":"string"},"b":{"type":"boolean"}},"required":["a","b"],"additionalProperties":false}""")
        val b = McpSchema("""{"additionalProperties":false,"required":["b","a"],"properties":{"b":{"type":"boolean"},"a":{"type":"string"}},"type":"object"}""")
        assertEquals(a.fingerprint, b.fingerprint)
    }
    @Test fun unsupported_schema_keywords_and_wrong_constraints_fail_closed() {
        for (property in listOf("""{"type":"object"}""", """{"type":"array"}""", """{"type":"string","pattern":".*"}""", """{"type":"string","maxLength":"5"}""", """{"type":"integer","minimum":5,"maximum":1}""", """{"type":"string","description":{}}""")) {
            assertEquals(McpError.MCP_SCHEMA_INVALID, assertFailsWith<McpException> { McpSchema("""{"type":"object","additionalProperties":false,"properties":{"value":$property}}""") }.code)
        }
        assertFailsWith<McpException> { McpSchema("""{"type":"object","additionalProperties":true}""") }
    }
    @Test fun exact_argument_validation_enforces_required_bounds_types_and_secrets() {
        val schema = McpSchema("""{"type":"object","additionalProperties":false,"properties":{"value":{"type":"string","minLength":1,"maxLength":5}},"required":["value"]}""")
        schema.arguments("""{"value":"ok"}""")
        for (raw in listOf("{}", """{"value":4}""", """{"value":"too long"}""", """{"value":"ok","extra":1}""", """{"value":"a","value":"b"}""")) assertFailsWith<McpException> { schema.arguments(raw) }
    }
    @Test fun metadata_header_mirroring_is_bounded_and_encoded() {
        val schema = McpSchema("""{"type":"object","additionalProperties":false,"properties":{"value":{"type":"string","x-mcp-header":"Route"}}}""")
        assertEquals("plain", schema.headers(schema.arguments("""{"value":"plain"}"""))["Mcp-Param-Route"])
        assertTrue(schema.headers(schema.arguments("""{"value":" white "}"""))["Mcp-Param-Route"]!!.startsWith("=?base64?"))
    }
}

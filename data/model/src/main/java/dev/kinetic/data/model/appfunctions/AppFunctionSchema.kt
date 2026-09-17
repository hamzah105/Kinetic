package dev.kinetic.data.model.appfunctions

import dev.kinetic.core.memory.SensitiveMemoryFilter
import dev.kinetic.core.tools.*
import dev.kinetic.data.model.mcp.*
import kotlinx.serialization.json.*

internal fun denied(error: AppFunctionAdapterError): Nothing = throw AppFunctionAdapterException(error)
internal fun safeMetadata(text: String, limit: Int = 1000): Boolean = text.length <= limit &&
    !text.any { it == '\u0000' || it in '\u202a'..'\u202e' || it in '\u2066'..'\u2069' } && !SensitiveMemoryFilter.isSensitive(text)
internal fun validPackage(value: String) = value.length <= 200 && value.matches(Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+"))

/** Applied to a fresh platform metadata/state observation immediately before execution. */
internal fun validateCurrentFunction(expected: AppFunctionCapabilityDescriptor, current: AppFunctionCapabilityDescriptor?) {
    if (current == null) denied(AppFunctionAdapterError.APPFUNCTION_NOT_FOUND)
    if (!current.platformEnabled) denied(AppFunctionAdapterError.APPFUNCTION_DISABLED)
    if (AppFunctionSchema(current).fingerprint != AppFunctionSchema(expected).fingerprint)
        denied(AppFunctionAdapterError.APPFUNCTION_SCHEMA_INVALID)
}

internal fun validateResultEnvelope(bytes: Int, hasExtras: Boolean, hasUriGrants: Boolean,
    properties: Set<String>, type: AppFunctionScalarType, returnKey: String) {
    if (bytes !in 0..16384) denied(AppFunctionAdapterError.APPFUNCTION_RESULT_TOO_LARGE)
    if (hasExtras || hasUriGrants) denied(AppFunctionAdapterError.APPFUNCTION_SCHEMA_UNSUPPORTED)
    val expected = if (type == AppFunctionScalarType.UNIT) emptySet() else setOf(returnKey)
    if (properties != expected) denied(AppFunctionAdapterError.APPFUNCTION_EXECUTION_FAILED)
}

/** Reuses only the existing strict flat JSON validator, not MCP transport/catalog/authority. */
internal class AppFunctionSchema(val descriptor: AppFunctionCapabilityDescriptor) {
    val schema: McpSchema
    val fingerprint: String
    val id: String
    init {
        try {
            require(validPackage(descriptor.packageName))
            require(descriptor.functionId.length in 1..200 && descriptor.functionId.none { it.isWhitespace() || it.isISOControl() })
            require(safeMetadata(descriptor.functionId, 200) && safeMetadata(descriptor.description) && safeMetadata(descriptor.returnDescription))
            require(descriptor.parameters.size <= 16 && descriptor.parameters.map { it.name }.distinct().size == descriptor.parameters.size)
            descriptor.returnAllowedValues?.let { values ->
                require(descriptor.returnType in setOf(AppFunctionScalarType.STRING, AppFunctionScalarType.INT))
                require(values.size in 1..16 && values.distinct().size == values.size)
                require(values.all { safeMetadata(it, 2000) && (descriptor.returnType != AppFunctionScalarType.INT || it.toIntOrNull() != null) })
            }
            schema = McpSchema(buildJsonObject {
                put("type", "object"); put("additionalProperties", false)
                put("properties", buildJsonObject { descriptor.parameters.forEach { parameter ->
                    require(parameter.type != AppFunctionScalarType.UNIT && safeMetadata(parameter.description))
                    put(parameter.name, buildJsonObject {
                        put("type", when(parameter.type) { AppFunctionScalarType.STRING -> "string"; AppFunctionScalarType.BOOLEAN -> "boolean"; else -> "integer" })
                        put("description", parameter.description)
                        if (parameter.type == AppFunctionScalarType.INT) { put("minimum", Int.MIN_VALUE); put("maximum", Int.MAX_VALUE) }
                        parameter.allowedValues?.let { values ->
                            require(values.size in 1..16 && values.all { safeMetadata(it, 2000) })
                            put("enum", JsonArray(values.map { if (parameter.type == AppFunctionScalarType.STRING) JsonPrimitive(it) else JsonPrimitive(it.toInt()) }))
                        }
                    })
                } })
                put("required", JsonArray(descriptor.parameters.filter { it.required }.map { JsonPrimitive(it.name) }))
            }.toString())
            fingerprint = sha(canonical(encodeDescriptor(descriptor.copy(platformEnabled = false))))
            id = "appfn_" + sha(descriptor.packageName + "\u0000" + descriptor.functionId + "\u0000" + fingerprint).take(58)
        } catch (_: Exception) { denied(AppFunctionAdapterError.APPFUNCTION_SCHEMA_INVALID) }
    }
    fun arguments(raw: String): Map<String, String> = try { schema.arguments(raw).mapValues { it.value.jsonPrimitive.content } }
        catch (_: Exception) { denied(AppFunctionAdapterError.APPFUNCTION_ARGUMENT_INVALID) }

    /** Also validates direct adapter invocations, independently of provider/host decoding. */
    fun validateValues(values: Map<String, String>) {
        try {
            require(values.size <= 16 && values.keys.all { key -> descriptor.parameters.any { it.name == key } })
            val json = buildJsonObject { values.forEach { (key, value) ->
                val type = descriptor.parameters.single { it.name == key }.type
                put(key, when (type) {
                    AppFunctionScalarType.STRING -> JsonPrimitive(value)
                    AppFunctionScalarType.BOOLEAN -> JsonPrimitive(value.toBooleanStrict())
                    AppFunctionScalarType.INT -> JsonPrimitive(value.toInt())
                    AppFunctionScalarType.LONG -> JsonPrimitive(value.toLong())
                    AppFunctionScalarType.UNIT -> error("Unsupported parameter")
                })
            } }
            arguments(json.toString())
        } catch (_: Exception) { denied(AppFunctionAdapterError.APPFUNCTION_ARGUMENT_INVALID) }
    }

    fun validateResult(result: AppFunctionInvocationResult) {
        if (result.type != descriptor.returnType) denied(AppFunctionAdapterError.APPFUNCTION_EXECUTION_FAILED)
        if (!safeMetadata(result.value, 4000) || result.value.toByteArray(Charsets.UTF_8).size > 16384)
            denied(AppFunctionAdapterError.APPFUNCTION_RESULT_TOO_LARGE)
        val valid = when (result.type) {
            AppFunctionScalarType.STRING -> true
            AppFunctionScalarType.BOOLEAN -> result.value in setOf("true", "false")
            AppFunctionScalarType.INT -> result.value.toIntOrNull() != null
            AppFunctionScalarType.LONG -> result.value.toLongOrNull() != null
            AppFunctionScalarType.UNIT -> result.value == "Completed; no return value."
        }
        if (!valid || descriptor.returnAllowedValues?.let { result.value !in it } == true)
            denied(AppFunctionAdapterError.APPFUNCTION_EXECUTION_FAILED)
    }
}
internal fun encodeDescriptor(d: AppFunctionCapabilityDescriptor): JsonObject = buildJsonObject {
    put("package", d.packageName); put("function", d.functionId); put("description", d.description)
    put("returnType", d.returnType.name); put("returnDescription", d.returnDescription); put("platformEnabled", d.platformEnabled)
    d.returnAllowedValues?.let { put("returnEnum", JsonArray(it.sorted().map(::JsonPrimitive))) }
    put("parameters", JsonArray(d.parameters.sortedBy { it.name }.map { p -> buildJsonObject {
        put("name", p.name); put("type", p.type.name); put("required", p.required); put("description", p.description)
        p.allowedValues?.let { put("enum", JsonArray(it.sorted().map(::JsonPrimitive))) }
    } }))
}
internal fun decodeDescriptor(d: JsonObject) = AppFunctionCapabilityDescriptor(d.text("package"), d.text("function"), d.text("description"),
    d.getValue("parameters").jsonArray.map { element -> val p = element.jsonObject
        AppFunctionParameter(p.text("name"), AppFunctionScalarType.valueOf(p.text("type")), p.getValue("required").jsonPrimitive.boolean,
            p.text("description"), p["enum"]?.jsonArray?.map { it.jsonPrimitive.content }) },
    AppFunctionScalarType.valueOf(d.text("returnType")), d.getValue("platformEnabled").jsonPrimitive.boolean, d.text("returnDescription"),
    d["returnEnum"]?.jsonArray?.map { it.jsonPrimitive.content })

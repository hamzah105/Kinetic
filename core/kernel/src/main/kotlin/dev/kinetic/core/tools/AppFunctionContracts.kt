package dev.kinetic.core.tools

/** Android-free AppFunctions caller boundary. Descriptors and outputs are untrusted data. */
enum class AppFunctionAvailability {
    SUPPORTED, FEATURE_UNAVAILABLE, PERMISSION_MISSING, CALLER_NOT_AUTHORIZED,
    PACKAGE_NOT_QUERYABLE, FUNCTION_NOT_FOUND, FUNCTION_DISABLED, SCHEMA_UNSUPPORTED,
    TEMPORARILY_UNAVAILABLE, UNKNOWN,
}
enum class AppFunctionAdapterError {
    APPFUNCTIONS_FEATURE_UNAVAILABLE, APPFUNCTIONS_PERMISSION_MISSING,
    APPFUNCTIONS_CALLER_NOT_AUTHORIZED, APPFUNCTIONS_PACKAGE_NOT_QUERYABLE,
    APPFUNCTION_NOT_FOUND, APPFUNCTION_DISABLED, APPFUNCTION_SCHEMA_INVALID,
    APPFUNCTION_SCHEMA_UNSUPPORTED, APPFUNCTION_ARGUMENT_INVALID,
    APPFUNCTION_RESULT_TOO_LARGE, APPFUNCTION_EXECUTION_DENIED, APPFUNCTION_EXECUTION_FAILED,
    APPFUNCTION_CANCELLED, APPFUNCTION_EFFECT_UNCERTAIN,
}
class AppFunctionAdapterException(val error: AppFunctionAdapterError) : RuntimeException(error.name)
enum class AppFunctionScalarType { STRING, BOOLEAN, INT, LONG, UNIT }
data class AppFunctionParameter(val name: String, val type: AppFunctionScalarType, val required: Boolean,
    val description: String = "", val allowedValues: List<String>? = null)
data class AppFunctionCapabilityDescriptor(val packageName: String, val functionId: String,
    val description: String, val parameters: List<AppFunctionParameter>, val returnType: AppFunctionScalarType,
    val platformEnabled: Boolean, val returnDescription: String = "", val returnAllowedValues: List<String>? = null)
data class AppFunctionBinding(val descriptor: AppFunctionCapabilityDescriptor, val fingerprint: String,
    val id: String, val enabled: Boolean = false)
data class AppFunctionCatalog(val packageName: String, val availability: AppFunctionAvailability = AppFunctionAvailability.UNKNOWN,
    val bindings: List<AppFunctionBinding> = emptyList(), val status: String = "NOT_DISCOVERED")
data class AppFunctionInvocation(val descriptor: AppFunctionCapabilityDescriptor, val values: Map<String, String>)
data class AppFunctionInvocationResult(val value: String, val type: AppFunctionScalarType)
interface AppFunctionCallerAdapter {
    fun availability(packageName: String): AppFunctionAvailability
    suspend fun discover(packageName: String): List<AppFunctionCapabilityDescriptor>
    /** Must revalidate current metadata/state before crossing the platform execution boundary. */
    suspend fun invoke(invocation: AppFunctionInvocation): AppFunctionInvocationResult
}

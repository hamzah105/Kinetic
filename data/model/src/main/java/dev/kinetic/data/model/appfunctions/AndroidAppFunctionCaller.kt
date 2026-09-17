package dev.kinetic.data.model.appfunctions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Parcel
import androidx.annotation.RequiresApi
import androidx.appfunctions.*
import androidx.appfunctions.metadata.*
import dev.kinetic.core.tools.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

/** Caller only. No provider service, app scanning, role acquisition, intents or fallback. */
class AndroidAppFunctionCaller(private val context: Context) : AppFunctionCallerAdapter {
    override fun availability(packageName: String): AppFunctionAvailability {
        if (Build.VERSION.SDK_INT < 36) return AppFunctionAvailability.FEATURE_UNAVAILABLE
        return try {
            if (AppFunctionManager.getInstance(context) == null) AppFunctionAvailability.FEATURE_UNAVAILABLE
            else if (context.checkSelfPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS) != PackageManager.PERMISSION_GRANTED) AppFunctionAvailability.PERMISSION_MISSING
            else if (!validPackage(packageName) || packageName == context.packageName || !context.packageManager.canPackageQuery(context.packageName, packageName)) AppFunctionAvailability.PACKAGE_NOT_QUERYABLE
            else AppFunctionAvailability.SUPPORTED // Technical prerequisites only, not caller certification.
        } catch (_: SecurityException) { AppFunctionAvailability.CALLER_NOT_AUTHORIZED
        } catch (_: PackageManager.NameNotFoundException) { AppFunctionAvailability.PACKAGE_NOT_QUERYABLE
        } catch (_: Exception) { AppFunctionAvailability.TEMPORARILY_UNAVAILABLE }
    }
    @RequiresApi(36)
    private fun manager(pkg: String): AppFunctionManager {
        when (availability(pkg)) {
            AppFunctionAvailability.SUPPORTED -> Unit
            AppFunctionAvailability.FEATURE_UNAVAILABLE -> denied(AppFunctionAdapterError.APPFUNCTIONS_FEATURE_UNAVAILABLE)
            AppFunctionAvailability.PERMISSION_MISSING -> denied(AppFunctionAdapterError.APPFUNCTIONS_PERMISSION_MISSING)
            AppFunctionAvailability.PACKAGE_NOT_QUERYABLE -> denied(AppFunctionAdapterError.APPFUNCTIONS_PACKAGE_NOT_QUERYABLE)
            AppFunctionAvailability.CALLER_NOT_AUTHORIZED -> denied(AppFunctionAdapterError.APPFUNCTIONS_CALLER_NOT_AUTHORIZED)
            else -> denied(AppFunctionAdapterError.APPFUNCTION_EXECUTION_FAILED)
        }
        return AppFunctionManager.getInstance(context) ?: denied(AppFunctionAdapterError.APPFUNCTIONS_FEATURE_UNAVAILABLE)
    }
    override suspend fun discover(packageName: String): List<AppFunctionCapabilityDescriptor> {
        if (Build.VERSION.SDK_INT < 36) denied(AppFunctionAdapterError.APPFUNCTIONS_FEATURE_UNAVAILABLE)
        return try {
            val manager = manager(packageName)
            val metadata = manager.searchAppFunctions(AppFunctionSearchSpec(packageNames = setOf(packageName)))
            if (metadata.size > 16) denied(AppFunctionAdapterError.APPFUNCTION_SCHEMA_INVALID)
            val states = manager.getAppFunctionStates(metadata.map { AppFunctionName(it.packageName, it.id) })
            metadata.map { meta ->
                if (meta.packageName != packageName) denied(AppFunctionAdapterError.APPFUNCTION_SCHEMA_INVALID)
                descriptor(meta, states.singleOrNull { it.functionName == AppFunctionName(meta.packageName, meta.id) }?.isEnabled == true)
            }
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (failure: Exception) { throw mapped(failure, false) }
    }
    private fun scalar(type: AppFunctionDataTypeMetadata): AppFunctionScalarType {
        if (type.isNullable || !safeMetadata(type.description)) denied(AppFunctionAdapterError.APPFUNCTION_SCHEMA_UNSUPPORTED)
        return when (type) {
            is AppFunctionStringTypeMetadata -> {
                if (type.pattern != null || type.format != null) denied(AppFunctionAdapterError.APPFUNCTION_SCHEMA_UNSUPPORTED)
                AppFunctionScalarType.STRING
            }
            is AppFunctionIntTypeMetadata -> AppFunctionScalarType.INT
            is AppFunctionLongTypeMetadata -> AppFunctionScalarType.LONG
            is AppFunctionBooleanTypeMetadata -> AppFunctionScalarType.BOOLEAN
            is AppFunctionUnitTypeMetadata -> AppFunctionScalarType.UNIT
            else -> denied(AppFunctionAdapterError.APPFUNCTION_SCHEMA_UNSUPPORTED)
        }
    }
    private fun descriptor(meta: AppFunctionMetadata, enabled: Boolean): AppFunctionCapabilityDescriptor {
        if (meta.parameters.size > 16) denied(AppFunctionAdapterError.APPFUNCTION_SCHEMA_INVALID)
        // No reference/object/array/Parcelable/resource schema is traversed or instantiated.
        val result = AppFunctionCapabilityDescriptor(meta.packageName, meta.id, meta.description, meta.parameters.map { p ->
            val type = scalar(p.dataType)
            val enum = when (val t = p.dataType) {
                is AppFunctionStringTypeMetadata -> t.enumValues?.also { if (it.size > 16) denied(AppFunctionAdapterError.APPFUNCTION_SCHEMA_UNSUPPORTED) }?.toList()
                is AppFunctionIntTypeMetadata -> t.enumValues?.also { if (it.size > 16) denied(AppFunctionAdapterError.APPFUNCTION_SCHEMA_UNSUPPORTED) }?.map { it.toString() }
                else -> null
            }
            AppFunctionParameter(p.name, type, p.isRequired, p.description + p.dataType.description, enum)
        }, scalar(meta.response.valueType), enabled, meta.response.description + meta.response.valueType.description,
            when (val t = meta.response.valueType) {
                is AppFunctionStringTypeMetadata -> t.enumValues?.toList()
                is AppFunctionIntTypeMetadata -> t.enumValues?.map { it.toString() }
                else -> null
            })
        AppFunctionSchema(result)
        return result
    }
    override suspend fun invoke(invocation: AppFunctionInvocation): AppFunctionInvocationResult {
        if (Build.VERSION.SDK_INT < 36) denied(AppFunctionAdapterError.APPFUNCTIONS_FEATURE_UNAVAILABLE)
        var crossed = false
        try {
            val expected = invocation.descriptor
            val schema = AppFunctionSchema(expected)
            schema.validateValues(invocation.values)
            val manager = manager(expected.packageName)
            val name = AppFunctionName(expected.packageName, expected.functionId)
            val meta = manager.searchAppFunctions(AppFunctionSearchSpec(packageNames = setOf(expected.packageName), functionNames = setOf(name)))
                .singleOrNull { it.packageName == expected.packageName && it.id == expected.functionId } ?: denied(AppFunctionAdapterError.APPFUNCTION_NOT_FOUND)
            val state = manager.getAppFunctionStates(listOf(name)).singleOrNull { it.functionName == name } ?: denied(AppFunctionAdapterError.APPFUNCTION_NOT_FOUND)
            validateCurrentFunction(expected, descriptor(meta, state.isEnabled))
            val builder = AppFunctionData.Builder(meta.parameters, AppFunctionComponentsMetadata())
            expected.parameters.forEach { p -> invocation.values[p.name]?.let { v -> when(p.type) {
                AppFunctionScalarType.STRING -> builder.setString(p.name, v)
                AppFunctionScalarType.INT -> builder.setInt(p.name, v.toInt())
                AppFunctionScalarType.LONG -> builder.setLong(p.name, v.toLong())
                AppFunctionScalarType.BOOLEAN -> builder.setBoolean(p.name, v.toBooleanStrict())
                AppFunctionScalarType.UNIT -> denied(AppFunctionAdapterError.APPFUNCTION_ARGUMENT_INVALID)
            } } }
            val request = ExecuteAppFunctionRequest(expected.packageName, expected.functionId, builder.build())
            coroutineContext.ensureActive()
            crossed = true
            // Jetpack suspend execution forwards cancellation into platform CancellationSignal.
            return when (val result = manager.executeAppFunction(request)) {
                is ExecuteAppFunctionResponse.Error -> throw mapped(result.error, true)
                is ExecuteAppFunctionResponse.Success -> boundedResult(result, expected.returnType).also(schema::validateResult)
            }
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (failure: Exception) { throw mapped(failure, crossed) }
    }
    @RequiresApi(36)
    private fun boundedResult(result: ExecuteAppFunctionResponse.Success, type: AppFunctionScalarType): AppFunctionInvocationResult {
        val platform = result.toPlatformExecuteAppFunctionResponse()
        val parcel = Parcel.obtain()
        val bytes = try { platform.writeToParcel(parcel, 0); parcel.dataSize() } finally { parcel.recycle() }
        val document = platform.resultDocument
        val key = ExecuteAppFunctionResponse.Success.PROPERTY_RETURN_VALUE
        // URI grants are transport authority, not text; never persist or forward them.
        validateResultEnvelope(bytes, !platform.extras.isEmpty, Build.VERSION.SDK_INT >= 37 && platform.uriGrants.isNotEmpty(), document.propertyNames, type, key)
        if (type == AppFunctionScalarType.UNIT) {
            if (document.propertyNames.isNotEmpty()) denied(AppFunctionAdapterError.APPFUNCTION_EXECUTION_FAILED)
            return AppFunctionInvocationResult("Completed; no return value.", type)
        }
        if (document.propertyNames != setOf(key)) denied(AppFunctionAdapterError.APPFUNCTION_EXECUTION_FAILED)
        val value = when (type) {
            AppFunctionScalarType.STRING -> document.getPropertyStringArray(key)?.singleOrNull()
            AppFunctionScalarType.BOOLEAN -> document.getPropertyBooleanArray(key)?.singleOrNull()?.toString()
            AppFunctionScalarType.INT -> document.getPropertyLongArray(key)?.singleOrNull()?.takeIf { it in Int.MIN_VALUE..Int.MAX_VALUE }?.toString()
            AppFunctionScalarType.LONG -> document.getPropertyLongArray(key)?.singleOrNull()?.toString()
            else -> null
        } ?: denied(AppFunctionAdapterError.APPFUNCTION_EXECUTION_FAILED)
        if (!safeMetadata(value, 4000)) denied(AppFunctionAdapterError.APPFUNCTION_RESULT_TOO_LARGE)
        return AppFunctionInvocationResult(value, type)
    }
    private fun mapped(failure: Exception, crossed: Boolean): AppFunctionAdapterException {
        if (failure is AppFunctionAdapterException) return failure
        return AppFunctionAdapterException(when (failure) {
            is SecurityException, is AppFunctionDeniedException -> AppFunctionAdapterError.APPFUNCTIONS_CALLER_NOT_AUTHORIZED
            is AppFunctionDisabledException -> AppFunctionAdapterError.APPFUNCTION_DISABLED
            is AppFunctionFunctionNotFoundException -> AppFunctionAdapterError.APPFUNCTION_NOT_FOUND
            is AppFunctionInvalidArgumentException -> AppFunctionAdapterError.APPFUNCTION_ARGUMENT_INVALID
            else -> if (crossed) AppFunctionAdapterError.APPFUNCTION_EFFECT_UNCERTAIN else AppFunctionAdapterError.APPFUNCTION_EXECUTION_FAILED
        })
    }
}

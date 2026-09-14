package dev.kinetic.data.androidcapabilities

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import dev.kinetic.core.tools.MAX_CLIPBOARD_TEXT_LENGTH
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicBoolean

/** Safe, bounded availability evaluated locally rather than by a model/provider. */
enum class AndroidCapabilityAvailability {
    AVAILABLE,
    UNAVAILABLE_NO_FOREGROUND_ACTIVITY,
    UNAVAILABLE_UNSUPPORTED_OS,
    UNAVAILABLE_NO_HANDLER,
    UNAVAILABLE_PRECONDITION,
    UNAVAILABLE_CONCURRENT_EXECUTION,
}

/**
 * Owns the current resumed Activity only through a weak reference and serializes Android effects.
 * Lifecycle registration has no execution side effect: returning to Kinetic can never redispatch.
 */
class ForegroundAndroidCapabilityExecutionCoordinator : AndroidCapabilityDispatcher {
    private val ownerLock = Any()
    private var foregroundActivity = WeakReference<Activity>(null)
    private val dispatchInProgress = AtomicBoolean(false)

    fun registerResumedActivity(activity: Activity) = synchronized(ownerLock) {
        foregroundActivity = WeakReference(activity)
    }

    fun unregisterPausedActivity(activity: Activity) = synchronized(ownerLock) {
        if (foregroundActivity.get() === activity) foregroundActivity.clear()
    }

    override suspend fun availability(
        request: AndroidCapabilityRequest,
    ): AndroidCapabilityAvailability = evaluateAvailability(request, includeBusy = true)

    override fun dispatch(request: AndroidCapabilityRequest): CapabilityDispatchResult {
        if (!dispatchInProgress.compareAndSet(false, true)) {
            return CapabilityDispatchResult.Failed(
                CapabilityDispatchFailureKind.CONCURRENT_DISPATCH,
            )
        }
        return try {
            when (val status = evaluateAvailability(request, includeBusy = false)) {
                AndroidCapabilityAvailability.AVAILABLE -> {
                    val activity = currentUsableActivity()
                        ?: return CapabilityDispatchResult.Failed(
                            CapabilityDispatchFailureKind.NO_FOREGROUND_ACTIVITY,
                        )
                    when (request) {
                        is AndroidCapabilityRequest.CopyTextToClipboard ->
                            copyText(activity, request.text)
                        is AndroidCapabilityRequest.ActivityRequest ->
                            launchActivity(activity, request)
                    }
                }
                else -> CapabilityDispatchResult.Failed(status.toFailureKind())
            }
        } finally {
            dispatchInProgress.set(false)
        }
    }

    private fun evaluateAvailability(
        request: AndroidCapabilityRequest,
        includeBusy: Boolean,
    ): AndroidCapabilityAvailability {
        if (includeBusy && dispatchInProgress.get()) {
            return AndroidCapabilityAvailability.UNAVAILABLE_CONCURRENT_EXECUTION
        }
        val activity = currentUsableActivity()
            ?: return AndroidCapabilityAvailability.UNAVAILABLE_NO_FOREGROUND_ACTIVITY
        return when (request) {
            is AndroidCapabilityRequest.CopyTextToClipboard -> when {
                request.text.isBlank() || request.text.length > MAX_CLIPBOARD_TEXT_LENGTH ->
                    AndroidCapabilityAvailability.UNAVAILABLE_PRECONDITION
                activity.getSystemService(ClipboardManager::class.java) == null ->
                    AndroidCapabilityAvailability.UNAVAILABLE_PRECONDITION
                else -> AndroidCapabilityAvailability.AVAILABLE
            }
            is AndroidCapabilityRequest.ActivityRequest -> try {
                AndroidIntentFactory.create(request)
                AndroidCapabilityAvailability.AVAILABLE
            } catch (_: IllegalArgumentException) {
                AndroidCapabilityAvailability.UNAVAILABLE_PRECONDITION
            }
        }
    }

    private fun currentUsableActivity(): Activity? = synchronized(ownerLock) {
        val current = foregroundActivity.get()
        if (current == null || current.isFinishing || current.isDestroyed) {
            foregroundActivity.clear()
            null
        } else {
            current
        }
    }

    private fun copyText(activity: Activity, text: String): CapabilityDispatchResult = try {
        val clipboard = activity.getSystemService(ClipboardManager::class.java)
            ?: return CapabilityDispatchResult.Failed(
                CapabilityDispatchFailureKind.SYSTEM_SERVICE_UNAVAILABLE,
            )
        clipboard.setPrimaryClip(ClipData.newPlainText("Kinetic text", text))
        CapabilityDispatchResult.Dispatched
    } catch (_: SecurityException) {
        CapabilityDispatchResult.Failed(CapabilityDispatchFailureKind.BLOCKED_BY_ANDROID)
    } catch (_: IllegalArgumentException) {
        CapabilityDispatchResult.Failed(CapabilityDispatchFailureKind.INVALID_REQUEST)
    }

    private fun launchActivity(
        activity: Activity,
        request: AndroidCapabilityRequest.ActivityRequest,
    ): CapabilityDispatchResult {
        val intent = try {
            AndroidIntentFactory.create(request)
        } catch (_: IllegalArgumentException) {
            return CapabilityDispatchResult.Failed(CapabilityDispatchFailureKind.INVALID_REQUEST)
        }
        return try {
            activity.startActivity(intent)
            CapabilityDispatchResult.Dispatched
        } catch (_: ActivityNotFoundException) {
            CapabilityDispatchResult.Failed(CapabilityDispatchFailureKind.NO_HANDLER)
        } catch (_: SecurityException) {
            CapabilityDispatchResult.Failed(CapabilityDispatchFailureKind.BLOCKED_BY_ANDROID)
        } catch (_: IllegalArgumentException) {
            CapabilityDispatchResult.Failed(CapabilityDispatchFailureKind.INVALID_REQUEST)
        }
    }
}

private fun AndroidCapabilityAvailability.toFailureKind(): CapabilityDispatchFailureKind = when (this) {
    AndroidCapabilityAvailability.AVAILABLE -> error("Available is not a failure")
    AndroidCapabilityAvailability.UNAVAILABLE_NO_FOREGROUND_ACTIVITY ->
        CapabilityDispatchFailureKind.NO_FOREGROUND_ACTIVITY
    AndroidCapabilityAvailability.UNAVAILABLE_NO_HANDLER -> CapabilityDispatchFailureKind.NO_HANDLER
    AndroidCapabilityAvailability.UNAVAILABLE_CONCURRENT_EXECUTION ->
        CapabilityDispatchFailureKind.CONCURRENT_DISPATCH
    AndroidCapabilityAvailability.UNAVAILABLE_UNSUPPORTED_OS,
    AndroidCapabilityAvailability.UNAVAILABLE_PRECONDITION,
    -> CapabilityDispatchFailureKind.INVALID_REQUEST
}

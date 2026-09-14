package dev.kinetic.data.androidcapabilities

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.kinetic.core.tools.SettingsDestination
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class DispatcherTestActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onResume() {
        super.onResume()
        coordinator?.registerResumedActivity(this)
    }

    override fun onPause() {
        coordinator?.unregisterPausedActivity(this)
        super.onPause()
    }

    override fun startActivity(intent: Intent) {
        dispatchAttempts.incrementAndGet()
        dispatchEntered.countDown()
        if (blockDispatch) check(allowDispatch.await(5, TimeUnit.SECONDS))
        if (failWithNoHandler) throw ActivityNotFoundException("test-only")
        acceptedDispatches.incrementAndGet()
    }

    companion object {
        @Volatile var coordinator: ForegroundAndroidCapabilityExecutionCoordinator? = null
        @Volatile var failWithNoHandler = false
        @Volatile var blockDispatch = false
        var dispatchAttempts = AtomicInteger()
        var acceptedDispatches = AtomicInteger()
        var dispatchEntered = CountDownLatch(1)
        var allowDispatch = CountDownLatch(0)

        fun reset() {
            coordinator = null
            failWithNoHandler = false
            blockDispatch = false
            dispatchAttempts = AtomicInteger()
            acceptedDispatches = AtomicInteger()
            dispatchEntered = CountDownLatch(1)
            allowDispatch = CountDownLatch(0)
        }
    }
}

@RunWith(AndroidJUnit4::class)
class AndroidCapabilityDispatcherTest {
    @After
    fun resetActivityHooks() = DispatcherTestActivity.reset()

    @Test
    fun noForegroundActivityFailsAvailabilityAndDispatchClosed() = runBlocking {
        val coordinator = ForegroundAndroidCapabilityExecutionCoordinator()
        val request = AndroidCapabilityRequest.OpenSettings(SettingsDestination.WIFI)

        assertEquals(
            AndroidCapabilityAvailability.UNAVAILABLE_NO_FOREGROUND_ACTIVITY,
            coordinator.availability(request),
        )
        assertEquals(
            CapabilityDispatchResult.Failed(
                CapabilityDispatchFailureKind.NO_FOREGROUND_ACTIVITY,
            ),
            coordinator.dispatch(request),
        )
    }

    @Test
    fun resumedOwnerBackgroundAndReplacementActivityAreTrackedWithoutStaleReferences() =
        runBlocking {
            val coordinator = ForegroundAndroidCapabilityExecutionCoordinator()
            DispatcherTestActivity.coordinator = coordinator
            val request = AndroidCapabilityRequest.OpenSettings(SettingsDestination.WIFI)

            ActivityScenario.launch(DispatcherTestActivity::class.java).use { scenario ->
                lateinit var original: DispatcherTestActivity
                scenario.onActivity { activity -> original = activity }
                assertEquals(AndroidCapabilityAvailability.AVAILABLE, coordinator.availability(request))

                scenario.moveToState(Lifecycle.State.CREATED)
                assertEquals(
                    AndroidCapabilityAvailability.UNAVAILABLE_NO_FOREGROUND_ACTIVITY,
                    coordinator.availability(request),
                )

                scenario.moveToState(Lifecycle.State.RESUMED)
                scenario.recreate()
                scenario.onActivity { replacement ->
                    assertNotSame(original, replacement)
                    coordinator.unregisterPausedActivity(original)
                }
                assertEquals(AndroidCapabilityAvailability.AVAILABLE, coordinator.availability(request))
                assertEquals(0, DispatcherTestActivity.dispatchAttempts.get())
            }

            assertEquals(
                AndroidCapabilityAvailability.UNAVAILABLE_NO_FOREGROUND_ACTIVITY,
                coordinator.availability(request),
            )
        }

    @Test
    fun destroyedActivityCannotDispatchUntilReplacementRegisters() = runBlocking {
        val coordinator = ForegroundAndroidCapabilityExecutionCoordinator()
        val request = AndroidCapabilityRequest.OpenSettings(SettingsDestination.GENERAL)

        ActivityScenario.launch(DispatcherTestActivity::class.java).use { scenario ->
            lateinit var original: DispatcherTestActivity
            scenario.onActivity { activity ->
                original = activity
                coordinator.registerResumedActivity(activity)
            }
            scenario.recreate()

            assertTrue(original.isDestroyed)
            assertEquals(
                AndroidCapabilityAvailability.UNAVAILABLE_NO_FOREGROUND_ACTIVITY,
                coordinator.availability(request),
            )
            scenario.onActivity(coordinator::registerResumedActivity)
            assertEquals(AndroidCapabilityAvailability.AVAILABLE, coordinator.availability(request))
        }
    }

    @Test
    fun activityNotFoundMapsToSafeNoHandlerFailure() = runBlocking {
        val coordinator = ForegroundAndroidCapabilityExecutionCoordinator()
        DispatcherTestActivity.coordinator = coordinator
        DispatcherTestActivity.failWithNoHandler = true

        ActivityScenario.launch(DispatcherTestActivity::class.java).use {
            val result = coordinator.dispatch(
                AndroidCapabilityRequest.OpenHttpsUrl("https://example.com"),
            )
            assertEquals(
                CapabilityDispatchResult.Failed(CapabilityDispatchFailureKind.NO_HANDLER),
                result,
            )
            assertEquals(1, DispatcherTestActivity.dispatchAttempts.get())
            assertEquals(0, DispatcherTestActivity.acceptedDispatches.get())
        }
    }

    @Test
    fun concurrentDispatchIsRejectedRatherThanQueuedBehindAnotherEffect() = runBlocking {
        val coordinator = ForegroundAndroidCapabilityExecutionCoordinator()
        DispatcherTestActivity.coordinator = coordinator
        DispatcherTestActivity.blockDispatch = true
        DispatcherTestActivity.allowDispatch = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor()

        try {
            ActivityScenario.launch(DispatcherTestActivity::class.java).use {
                val first = executor.submit<CapabilityDispatchResult> {
                    coordinator.dispatch(
                        AndroidCapabilityRequest.OpenSettings(SettingsDestination.WIFI),
                    )
                }
                assertTrue(DispatcherTestActivity.dispatchEntered.await(5, TimeUnit.SECONDS))

                assertEquals(
                    CapabilityDispatchResult.Failed(
                        CapabilityDispatchFailureKind.CONCURRENT_DISPATCH,
                    ),
                    coordinator.dispatch(
                        AndroidCapabilityRequest.OpenSettings(SettingsDestination.GENERAL),
                    ),
                )
                DispatcherTestActivity.allowDispatch.countDown()
                assertEquals(CapabilityDispatchResult.Dispatched, first.get(5, TimeUnit.SECONDS))
                assertEquals(1, DispatcherTestActivity.acceptedDispatches.get())
            }
        } finally {
            DispatcherTestActivity.allowDispatch.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun lifecycleChangesAndReturnNeverRedispatchAnAcceptedHandoff() = runBlocking {
        val coordinator = ForegroundAndroidCapabilityExecutionCoordinator()
        DispatcherTestActivity.coordinator = coordinator

        ActivityScenario.launch(DispatcherTestActivity::class.java).use { scenario ->
            assertEquals(
                CapabilityDispatchResult.Dispatched,
                coordinator.dispatch(
                    AndroidCapabilityRequest.OpenHttpsUrl("https://example.com"),
                ),
            )
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.recreate()
            assertEquals(1, DispatcherTestActivity.acceptedDispatches.get())
        }
    }
}

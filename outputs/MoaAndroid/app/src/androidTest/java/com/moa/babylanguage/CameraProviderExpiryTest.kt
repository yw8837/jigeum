package com.moa.babylanguage

import android.Manifest
import androidx.activity.ComponentActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/** Actual CameraX provider and use cases; only listener scheduling and the grant clock are controlled. */
@RunWith(AndroidJUnit4::class)
class CameraProviderExpiryTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val visible = mutableStateOf(true)
    private val authorize = mutableStateOf<() -> Boolean>({ false })
    private val heldListener = HeldProviderExecutor()
    private val ready = AtomicInteger()
    private val observations = AtomicInteger()
    private val errors = CopyOnWriteArrayList<String>()
    private lateinit var preview: Preview
    private lateinit var analysis: ImageAnalysis
    private var started = false

    private fun start(grant: () -> Boolean): ProcessCameraProvider {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.grantRuntimePermission(compose.activity.packageName, Manifest.permission.CAMERA)
        authorize.value = grant
        started = true
        compose.setContent {
            if (visible.value) CameraObservationPreview(
                requestedCamera = CameraSelector.LENS_FACING_BACK,
                authorize = authorize.value,
                onObservation = { observations.incrementAndGet() },
                onError = { errors += it },
                onWarning = {},
                onReady = { _, _ -> ready.incrementAndGet() },
                providerCallbackExecutor = heldListener,
                onUseCasesCreated = { createdPreview, createdAnalysis ->
                    preview = createdPreview
                    analysis = createdAnalysis
                }
            )
        }
        assertTrue("Real provider listener must be queued before authorization changes", heldListener.await())
        // Listener enqueue happens only after the real getInstance future has completed.
        val provider = ProcessCameraProvider.getInstance(compose.activity).get(5, TimeUnit.SECONDS)
        compose.runOnIdle {
            assertFalse(provider.isBound(preview))
            assertFalse(provider.isBound(analysis))
        }
        return provider
    }

    @After fun disposePreview() {
        if (started) {
            compose.runOnIdle { visible.value = false }
            compose.waitForIdle()
            // No callback can survive the test composition or retain its activity.
            compose.runOnIdle { heldListener.releaseIfHeld() }
        }
    }

    @Test fun expiryBeforeRealProviderListenerExecutesPreventsBothUseCasesFromBinding() {
        val now = AtomicLong(1_000)
        val provider = start { now.get() < 2_000 }
        compose.runOnIdle {
            now.set(2_000)
            heldListener.release()
            // Composition is still alive: this specifically exercises the pre-bind grant check.
            assertTrue(visible.value)
            assertFalse(provider.isBound(preview))
            assertFalse(provider.isBound(analysis))
            assertEquals(0, ready.get())
            assertEquals(0, observations.get())
            assertTrue(errors.isEmpty())
        }
    }

    @Test fun replacementAuthorizationIsReadWhenDelayedProviderListenerRuns() {
        val originalChecks = AtomicInteger()
        val currentChecks = AtomicInteger()
        val provider = start { originalChecks.incrementAndGet(); true }
        compose.runOnIdle {
            authorize.value = { currentChecks.incrementAndGet(); false }
        }
        compose.waitForIdle() // Commit rememberUpdatedState while the listener remains held.
        compose.runOnIdle {
            heldListener.release()
            assertEquals(0, originalChecks.get())
            assertEquals(1, currentChecks.get())
            assertFalse(provider.isBound(preview))
            assertFalse(provider.isBound(analysis))
            assertEquals(0, ready.get())
        }
    }

    @Test fun validGrantAllowsRealProviderBindAndDisposalUnbindsBothUseCases() {
        val provider = start { true }
        compose.runOnIdle {
            heldListener.release()
            assertTrue("Real Preview must bind with a current grant: $errors", provider.isBound(preview))
            assertTrue("Real ImageAnalysis must bind with a current grant: $errors", provider.isBound(analysis))
            assertEquals(1, ready.get())
        }
        compose.runOnIdle { visible.value = false }
        compose.waitForIdle()
        compose.runOnIdle {
            assertFalse(provider.isBound(preview))
            assertFalse(provider.isBound(analysis))
        }
    }

    @Test fun disposalBeforeDelayedProviderListenerPreventsBindingDespiteValidGrant() {
        val checks = AtomicInteger()
        val provider = start { checks.incrementAndGet(); true }
        compose.runOnIdle { visible.value = false }
        compose.waitForIdle()
        compose.runOnIdle {
            heldListener.release()
            assertEquals(0, checks.get())
            assertFalse(provider.isBound(preview))
            assertFalse(provider.isBound(analysis))
            assertEquals(0, ready.get())
        }
    }

    /** Guava calls execute after future completion; tests release the same runnable on Main. */
    private class HeldProviderExecutor : Executor {
        private val queued = CountDownLatch(1)
        private val command = AtomicReference<Runnable?>()
        override fun execute(task: Runnable) {
            check(command.compareAndSet(null, task)) { "Only one real provider callback is expected" }
            queued.countDown()
        }
        fun await(): Boolean = queued.await(25, TimeUnit.SECONDS)
        fun release() { checkNotNull(command.getAndSet(null)).run() }
        fun releaseIfHeld() { command.getAndSet(null)?.run() }
    }
}

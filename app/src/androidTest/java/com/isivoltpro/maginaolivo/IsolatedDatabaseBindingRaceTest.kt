package com.isivoltpro.maginaolivo

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.isivoltpro.maginaolivo.data.local.MaginaOlivoDatabase
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Test

/** No Activity or production database: uses the actual singleton initializer and its actual monitor. */
class IsolatedDatabaseBindingRaceTest {
    @Test fun bindingWaitsForInFlightRealInitializationAndRestoresItsDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("com.isivoltpro.maginaolivo.dev", context.packageName)
        android.os.ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation()
            .uiAutomation.executeShellCommand("getprop ro.kernel.qemu"))
            .bufferedReader().use { assertEquals("Emulator only", "1", it.readText().trim()) }

        val token = UUID.randomUUID().toString()
        val fixtureName = "isolated-main-binding-$token.db"
        val initializerName = "qa-binding-initializer-$token.db"
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val ownsProductionMonitor = AtomicBoolean(false)
        val wrapped = object : ContextWrapper(context) {
            override fun getApplicationContext(): Context {
                ownsProductionMonitor.set(Thread.holdsLock(MaginaOlivoDatabase.Companion))
                entered.countDown()
                check(release.await(10, TimeUnit.SECONDS)) { "Initializer release was not signalled" }
                return this
            }

            override fun getDatabasePath(name: String): File = context.getDatabasePath(initializerName)
        }
        val fixture = MaginaOlivoDatabase.create(context, fixtureName)
        val singletonField = MaginaOlivoDatabase::class.java.getDeclaredField("instance").apply { isAccessible = true }
        val originalSingleton = AtomicReference<MaginaOlivoDatabase?>()
        val initialized = AtomicReference<MaginaOlivoDatabase?>()
        val replaced = AtomicReference<MaginaOlivoDatabase?>()
        val initializerFailure = AtomicReference<Throwable?>()
        val binderFailure = AtomicReference<Throwable?>()
        val initializer = Thread({
            try {
                synchronized(MaginaOlivoDatabase.Companion) {
                    // Atomic null -> real initialization, so another caller cannot win
                    // between the reset and the initializer's inner null check.
                    originalSingleton.set(singletonField.get(null) as MaginaOlivoDatabase?)
                    IsolatedDatabaseBinding.restore(null)
                    initialized.set(MaginaOlivoDatabase.getInstance(wrapped))
                }
            }
            catch (failure: Throwable) { initializerFailure.set(failure) }
        }, "qa-real-database-initializer")
        val binder = Thread({
            try { replaced.set(IsolatedDatabaseBinding.install(fixture)) }
            catch (failure: Throwable) { binderFailure.set(failure) }
        }, "qa-isolated-database-binder")
        try {
            initializer.start()
            assertTrue("Real initializer did not reach applicationContext", entered.await(10, TimeUnit.SECONDS))
            assertTrue("applicationContext must run inside the real Companion monitor", ownsProductionMonitor.get())
            binder.start()
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
            while (binder.state != Thread.State.BLOCKED && binder.state != Thread.State.TERMINATED && System.nanoTime() < deadline) {
                Thread.yield()
            }
            assertTrue("Binder must be blocked on the monitor or have completed; state=${binder.state}",
                binder.state == Thread.State.BLOCKED || binder.state == Thread.State.TERMINATED)
            println("QA binding state before initializer release: ${binder.state}")
            release.countDown()
            initializer.join(10_000)
            binder.join(10_000)
            assertFalse("Initializer did not finish", initializer.isAlive)
            assertFalse("Binder did not finish", binder.isAlive)
            initializerFailure.get()?.let { throw AssertionError("Real initializer failed", it) }
            binderFailure.get()?.let { throw AssertionError("Binding failed", it) }
            assertNotNull("Real initializer must create its own QA database", initialized.get())
            // RED with the extracted old helper: binder terminates first, then the real
            // initializer overwrites its fixture. No mirrored singleton or simulated lock.
            assertSame("In-flight initialization must not overwrite the isolated fixture",
                fixture, MaginaOlivoDatabase.getInstance(wrapped))
            assertSame("Install must preserve the initializer's database for restoration", initialized.get(), replaced.get())
            IsolatedDatabaseBinding.restore(replaced.get())
            assertSame(initialized.get(), MaginaOlivoDatabase.getInstance(wrapped))
        } finally {
            release.countDown()
            if (initializer.state != Thread.State.NEW) initializer.join(10_000)
            if (binder.state != Thread.State.NEW) binder.join(10_000)
            synchronized(MaginaOlivoDatabase.Companion) {
                IsolatedDatabaseBinding.restore(originalSingleton.get())
            }
            fixture.close()
            initialized.get()?.takeIf { it !== fixture && it !== originalSingleton.get() }?.close()
            context.deleteDatabase(fixtureName)
            context.deleteDatabase(initializerName)
        }
    }
}

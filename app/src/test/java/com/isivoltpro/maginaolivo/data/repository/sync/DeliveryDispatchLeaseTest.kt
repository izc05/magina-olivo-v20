package com.isivoltpro.maginaolivo.data.repository.sync

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DeliveryDispatchLeaseTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun onlyOneOwnerAcrossInstancesAndCanonicalPaths() {
        val directory = temporary.newFolder()
        val first = checkNotNull(DeliveryDispatchLease.tryAcquire(directory))
        first.use {
            it.requireHeld()
            assertNull(DeliveryDispatchLease.tryAcquire(File(directory, ".")))
        }
        checkNotNull(DeliveryDispatchLease.tryAcquire(directory)).use { it.requireHeld() }
    }

    @Test fun releasedTokenCannotAuthorizeRecovery() {
        val lease = checkNotNull(DeliveryDispatchLease.tryAcquire(temporary.newFolder()))
        lease.close()
        lease.close()
        assertThrows(IllegalStateException::class.java) { lease.requireHeld() }
    }

    @Test fun oldTimestampDoesNotReleaseAnActiveOwner() {
        val directory = temporary.newFolder()
        checkNotNull(DeliveryDispatchLease.tryAcquire(directory)).use {
            directory.listFiles()!!.single().setLastModified(1L)
            assertNull(DeliveryDispatchLease.tryAcquire(directory))
            it.requireHeld()
        }
    }

    @Test fun exceptionReleasesOwnership() {
        val directory = temporary.newFolder()
        assertThrows(IllegalStateException::class.java) {
            checkNotNull(DeliveryDispatchLease.tryAcquire(directory)).use { error("interrupted work") }
        }
        checkNotNull(DeliveryDispatchLease.tryAcquire(directory)).use { it.requireHeld() }
    }

    @Test fun invalidStorageFailsClosed() {
        val file = temporary.newFile()
        assertThrows(java.io.IOException::class.java) { DeliveryDispatchLease.tryAcquire(file) }
    }

    @Test fun durableMarkerBindsTheCanonicalNamespace() {
        val directory = temporary.newFolder()
        val first = checkNotNull(DeliveryDispatchLease.tryAcquire(directory)).use { it.claimMarker() }
        checkNotNull(DeliveryDispatchLease.tryAcquire(File(directory, "."))).use {
            assertEquals(first, it.claimMarker())
        }
        checkNotNull(DeliveryDispatchLease.tryAcquire(temporary.newFolder())).use {
            assertNotEquals(first, it.claimMarker())
        }
    }

    @Test fun killedJvmReleasesTheLockButLiveSlowJvmCannotBeReplaced() {
        val directory = temporary.newFolder()
        val marker = File(directory, "ready")
        // Gradle test workers have a bootstrap-only java.class.path; derive the actual
        // runtime locations so this also works in the Android project's JVM test task.
        val classpath = listOf(DeliveryDispatchLeaseTest::class.java, DeliveryDispatchLease::class.java,
            kotlin.Unit::class.java).map { File(it.protectionDomain.codeSource.location.toURI()).path }
            .distinct().joinToString(File.pathSeparator)
        val process = ProcessBuilder(File(System.getProperty("java.home"), "bin/java").path,
            "-cp", classpath, DispatchLeaseChild::class.java.name, directory.path)
            .redirectErrorStream(true).redirectOutput(File(directory, "child.log")).start()
        try {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
            while (!marker.isFile && process.isAlive && System.nanoTime() < deadline) Thread.sleep(10)
            assertTrue("Child did not reach lock checkpoint: ${File(directory, "child.log").readText()}", marker.isFile)
            assertEquals(process.pid().toString(), marker.readText())
            assertTrue(process.isAlive)
            assertNull(DeliveryDispatchLease.tryAcquire(directory))
            process.destroyForcibly()
            assertTrue("Child must actually die", process.waitFor(10, TimeUnit.SECONDS))
            assertFalse(process.isAlive)
            checkNotNull(DeliveryDispatchLease.tryAcquire(directory)).use { it.requireHeld() }
        } finally {
            if (process.isAlive) { process.destroyForcibly(); process.waitFor(10, TimeUnit.SECONDS) }
        }
    }
}

object DispatchLeaseChild {
    @JvmStatic fun main(args: Array<String>) {
        val directory = File(args.single())
        checkNotNull(DeliveryDispatchLease.tryAcquire(directory)).use {
            val pending = File(directory, "ready.pending")
            FileOutputStream(pending).use { output ->
                output.write(ProcessHandle.current().pid().toString().toByteArray())
                output.fd.sync()
            }
            Files.move(pending.toPath(), File(directory, "ready").toPath(), StandardCopyOption.ATOMIC_MOVE)
            Thread.sleep(30_000)
            error("Host must kill the child; normal completion is not a passing checkpoint")
        }
    }
}

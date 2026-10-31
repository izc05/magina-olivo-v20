package com.isivoltpro.maginaolivo

import android.content.Context
import android.content.Intent
import android.os.Process
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.isivoltpro.maginaolivo.data.repository.sync.DeliveryDispatchLease
import com.isivoltpro.maginaolivo.data.repository.sync.DeliveryDispatchLeaseProbeService
import java.io.File
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * B2 gate — REAL cross-process exclusion of the delivery dispatch lease.
 *
 * Every other lease test runs inside one JVM, where `DeliveryDispatchLease.ownedPaths` alone
 * could explain the exclusion. This test puts a second OS process on the same lock file, so a
 * pass means the operating system's `FileLock` is doing the work. It asserts the probe's PID
 * differs from this process's PID, otherwise the result would prove nothing.
 *
 * No HTTP, no database, no production data: only a lock file in a throwaway directory.
 */
@RunWith(AndroidJUnit4::class)
class DeliveryDispatchCrossProcessTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val root = File(context.noBackupFilesDir, "g24-b2-${UUID.randomUUID()}")
    private val lockDirectory = File(root, "lock")
    private val report = File(root, "probe-report.txt")
    private val releaseSentinel = File(root, "release")

    @After
    fun after() {
        releaseSentinel.writeText("stop")
        // Give a holding probe its chance to release before the directory disappears.
        waitFor(5_000L) { tokens().contains("RELEASED") || tokens().contains("BLOCKED") }
        root.deleteRecursively()
    }

    @Test
    fun aSecondOsProcessIsExcludedByTheLockAndThisProcessIsExcludedByIt() {
        root.mkdirs()

        // 1. This process owns the lease.
        val mine = DeliveryDispatchLease.tryAcquire(lockDirectory)
        assertNotNull("Esta prueba necesita el lease para empezar", mine)

        // 2. A real second process must be refused while we hold it.
        startProbe(DeliveryDispatchLeaseProbeService.MODE_TRY)
        waitFor(20_000L) { tokens().any { it == "BLOCKED" || it == "ACQUIRED" } }
        val first = tokens()
        assertNotEquals(
            "La sonda debe correr en OTRO proceso; si no, la exclusion no prueba nada",
            Process.myPid().toString(),
            probePid(first),
        )
        assertTrue(
            "Un segundo proceso del sistema no puede poseer el mismo lease: $first",
            first.contains("BLOCKED"),
        )
        assertTrue("La sonda no debio adquirirlo: $first", !first.contains("ACQUIRED"))

        // 3. We release. The other process must now be able to take it.
        mine!!.close()
        report.delete()
        startProbe(DeliveryDispatchLeaseProbeService.MODE_HOLD)
        waitFor(20_000L) { tokens().contains("ACQUIRED") }
        val second = tokens()
        assertTrue("Tras liberar, el otro proceso debe adquirirlo: $second", second.contains("ACQUIRED"))
        assertNotEquals(Process.myPid().toString(), probePid(second))

        // 4. And now THIS process must be the one excluded, by the OS and not by its own
        //    in-JVM guard: nothing in this JVM holds the path any more.
        assertNull(
            "El lock del sistema operativo debe excluir a este proceso mientras el otro lo tiene",
            DeliveryDispatchLease.tryAcquire(lockDirectory),
        )

        // 5. The probe releases; ownership becomes available again, same directory.
        releaseSentinel.writeText("stop")
        waitFor(20_000L) { tokens().contains("RELEASED") }
        assertTrue("La sonda debe liberar: " + tokens(), tokens().contains("RELEASED"))
        val again = DeliveryDispatchLease.tryAcquire(lockDirectory)
        assertNotNull("Liberado el lock, el mismo directorio vuelve a ser adquirible", again)
        again!!.close()

        // The lock file is never deleted to recover ownership.
        assertTrue("El fichero de lock se conserva", File(lockDirectory, "delivery-dispatch.lock").exists())
    }

    private fun startProbe(mode: String) {
        val intent = Intent(context, DeliveryDispatchLeaseProbeService::class.java).apply {
            putExtra(DeliveryDispatchLeaseProbeService.EXTRA_DIRECTORY, lockDirectory.absolutePath)
            putExtra(DeliveryDispatchLeaseProbeService.EXTRA_RESULT, report.absolutePath)
            putExtra(DeliveryDispatchLeaseProbeService.EXTRA_MODE, mode)
            putExtra(DeliveryDispatchLeaseProbeService.EXTRA_RELEASE, releaseSentinel.absolutePath)
        }
        assertNotNull("El servicio de sonda debe existir en el flavor dev", context.startService(intent))
    }

    private fun tokens(): List<String> =
        if (report.exists()) report.readLines().filter { it.isNotBlank() } else emptyList()

    private fun probePid(lines: List<String>): String? =
        lines.firstOrNull { it.startsWith("PID:") }?.removePrefix("PID:")

    private fun waitFor(timeoutMillis: Long, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline && !condition()) {
            Thread.sleep(50L)
        }
    }

    @Test
    fun theProbeReportsItsOwnDistinctProcessIdentity() {
        root.mkdirs()
        startProbe(DeliveryDispatchLeaseProbeService.MODE_TRY)
        waitFor(20_000L) { probePid(tokens()) != null }
        val pid = probePid(tokens())
        assertNotNull("La sonda debe informar su PID: " + tokens(), pid)
        assertNotEquals("La sonda no puede compartir proceso con la prueba", Process.myPid().toString(), pid)
        waitFor(20_000L) { tokens().contains("RELEASED") }
        assertEquals(
            "Sin competencia, la sonda adquiere y libera",
            listOf("ACQUIRED", "RELEASED"),
            tokens().filter { !it.startsWith("PID:") },
        )
    }
}

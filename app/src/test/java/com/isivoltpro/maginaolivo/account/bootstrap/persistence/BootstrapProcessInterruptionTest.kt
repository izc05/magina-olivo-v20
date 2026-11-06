package com.isivoltpro.maginaolivo.account.bootstrap.persistence

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Actual JVM process termination. Does not substitute Android filesystem/device verification. */
class BootstrapProcessInterruptionTest {
    @Test fun killedProcessRetainsPreparedPlanOrCommittedReceiptAtThreeBoundaries() {
        val directory = Files.createTempDirectory("bootstrap-process").toFile()
        try {
            listOf("prepared", "receipt-before-rename", "receipt-committed").forEach { checkpoint ->
                val file = File(directory, checkpoint)
                val java = File(System.getProperty("java.home"), "bin/java").absolutePath
                val classpath = listOf(javaClass, DurableBootstrapJournal::class.java, Unit::class.java)
                    .map { File(it.protectionDomain.codeSource.location.toURI()).absolutePath }
                    .distinct().joinToString(File.pathSeparator)
                val process = ProcessBuilder(java, "-cp", classpath,
                    "com.isivoltpro.maginaolivo.account.bootstrap.persistence.BootstrapProcessWriter",
                    file.absolutePath, checkpoint).redirectErrorStream(true).start()
                val reader = Executors.newSingleThreadExecutor()
                try {
                    val ready = reader.submit<String> { process.inputStream.bufferedReader().readLine() }
                    assertEquals("CHECKPOINT:$checkpoint", ready.get(30, TimeUnit.SECONDS))
                    process.destroyForcibly()
                    assertTrue(process.waitFor(10, TimeUnit.SECONDS))
                    assertNotEquals(0, process.exitValue())
                    if (checkpoint == "receipt-before-rename") {
                        assertTrue(directory.listFiles()!!.any { it.name.startsWith(file.name + ".tmp-") })
                    }
                    val f = BootstrapJournalFixture
                    val journal = DurableBootstrapJournal(file)
                    val recovered = journal.load()!!
                    assertEquals(f.plan.steps, recovered.plan.steps)
                    assertEquals(f.actor, recovered.plan.actor)
                    assertEquals(f.consent, recovered.plan.consentId)
                    assertEquals(f.workspace, recovered.plan.remoteWorkspaceId)
                    val confirmed = checkpoint == "receipt-committed"
                    assertEquals(if (confirmed) listOf(f.rootReceipt) else emptyList(), recovered.receipts)
                    assertEquals(if (confirmed) listOf(f.farmStep) else listOf(f.root),
                        recovered.ready(f.actor, f.consent, setOf(f.farm)))
                    assertFalse(directory.listFiles()!!.any { it.name.startsWith(file.name + ".tmp-") })
                    journal.record(f.rootReceipt)
                    assertEquals(listOf(f.rootReceipt), journal.load()!!.receipts)
                } finally {
                    if (process.isAlive) { process.destroyForcibly(); process.waitFor(10, TimeUnit.SECONDS) }
                    reader.shutdownNow()
                }
            }
        } finally { directory.deleteRecursively() }
    }
}

object BootstrapProcessWriter {
    @JvmStatic fun main(args: Array<String>) {
        require(args.size == 2)
        val file = File(args[0]); val checkpoint = args[1]
        require(checkpoint in setOf("prepared", "receipt-before-rename", "receipt-committed"))
        val f = BootstrapJournalFixture
        DurableBootstrapJournal(file).prepare(f.plan)
        fun boundary(): Nothing {
            println("CHECKPOINT:$checkpoint"); System.out.flush()
            CountDownLatch(1).await()
            error("Only forced termination should finish the writer")
        }
        if (checkpoint == "prepared") boundary()
        val journal = if (checkpoint == "receipt-before-rename") DurableBootstrapJournal(file) { boundary() }
            else DurableBootstrapJournal(file)
        journal.record(f.rootReceipt)
        boundary()
    }
}

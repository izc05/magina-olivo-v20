package com.isivoltpro.maginaolivo.data.repository.sync

import com.isivoltpro.maginaolivo.account.reservation.ReservationActor
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DeliveryReceiptJournalTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun file() = File(temporary.newFolder(), "operation.receipt")

    @Test fun prepareRecordReopenAndReplayKeepTheExactHistoricalReceipt() {
        val file = file(); val intent = receiptFixture()
        val journal = DeliveryReceiptJournal(file)
        assertEquals(DeliveryReceiptSnapshot(intent, null), journal.prepare(intent))
        assertEquals(DeliveryReceiptSnapshot(intent, null), DeliveryReceiptJournal(file).load())
        val receipt = journal.record(intent, 7L)
        val bytes = file.readBytes()
        assertEquals(receipt, DeliveryReceiptJournal(file).load())
        assertEquals(receipt, journal.prepare(intent))
        assertEquals(receipt, journal.record(intent, 7L))
        assertArrayEquals(bytes, file.readBytes())
    }

    @Test fun ackCannotBorrowADestinationOrIdentityWithoutPreparation() {
        val file = file(); val journal = DeliveryReceiptJournal(file)
        assertThrows(IOException::class.java) { journal.record(receiptFixture(), 7L) }
        assertFalse(file.exists())
    }

    @Test fun actorTargetConsentAndSnapshotAreImmutable() {
        val file = file(); val original = receiptFixture(); val journal = DeliveryReceiptJournal(file)
        journal.prepare(original); val bytes = file.readBytes()
        val altered = listOf(
            original.copy(actor = original.actor.copy(userId = UUID.randomUUID())),
            original.copy(actor = original.actor.copy(issuer = "https://other.example")),
            original.copy(localWorkspaceId = UUID.randomUUID()), original.copy(remoteWorkspaceId = UUID.randomUUID()),
            original.copy(consentId = UUID.randomUUID()), original.copy(farmId = UUID.randomUUID()),
            original.copy(operationId = UUID.randomUUID()), original.copy(deliveryId = UUID.randomUUID()),
            original.copy(payloadHash = "b".repeat(64)), original.copy(payloadVersion = 1),
            original.copy(baseRemoteVersion = 2L), original.copy(consentTextVersion = 3),
        )
        altered.forEach {
            assertThrows(IllegalArgumentException::class.java) { journal.prepare(it) }
            assertThrows(IllegalArgumentException::class.java) { journal.record(it, 7L) }
            assertArrayEquals(bytes, file.readBytes())
        }
    }

    @Test fun conflictingReplayCannotReplaceAConfirmedVersion() {
        val file = file(); val journal = DeliveryReceiptJournal(file); val intent = receiptFixture()
        journal.prepare(intent); journal.record(intent, 7L); val bytes = file.readBytes()
        assertThrows(IllegalArgumentException::class.java) { journal.record(intent, 8L) }
        assertArrayEquals(bytes, file.readBytes())
    }

    @Test fun failedPublicationPreservesPendingScopeAndAllowsAnExactRetry() {
        val file = file(); val intent = receiptFixture()
        DeliveryReceiptJournal(file).prepare(intent); val bytes = file.readBytes()
        assertThrows(IOException::class.java) {
            DeliveryReceiptJournal(file) { throw IOException("injected before rename") }.record(intent, 7L)
        }
        assertArrayEquals(bytes, file.readBytes())
        assertEquals(7L, DeliveryReceiptJournal(file).record(intent, 7L).remoteVersion)
    }

    @Test fun corruptionAndTruncationFailClosedWithoutResettingTheFile() {
        val file = file(); val intent = receiptFixture(); val journal = DeliveryReceiptJournal(file)
        journal.prepare(intent); val valid = file.readBytes()
        listOf(valid.copyOf(valid.size - 1), valid.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }).forEach {
            file.writeBytes(it)
            assertThrows(IOException::class.java) { journal.load() }
            assertThrows(IOException::class.java) { journal.prepare(intent) }
            assertArrayEquals(it, file.readBytes())
        }
    }

    @Test fun concurrentIdenticalReceiptsConvergeWithoutReplacingTheIntent() {
        val file = file(); val intent = receiptFixture(); val pool = Executors.newFixedThreadPool(2)
        try {
            val tasks = (1..2).map { pool.submit<DeliveryReceiptSnapshot> {
                DeliveryReceiptJournal(file).prepare(intent)
                DeliveryReceiptJournal(file).record(intent, 7L)
            } }
            tasks.forEach { assertEquals(DeliveryReceiptSnapshot(intent, 7L), it.get(10, TimeUnit.SECONDS)) }
            assertEquals(DeliveryReceiptSnapshot(intent, 7L), DeliveryReceiptJournal(file).load())
        } finally { pool.shutdownNow() }
    }

    @Test fun malformedUnicodeAndInvalidVersionsCannotPublishAReceipt() {
        val file = file(); val intent = receiptFixture()
        assertThrows(IllegalArgumentException::class.java) { intent.copy(payloadHash = "invalid") }
        assertThrows(IllegalArgumentException::class.java) { DeliveryReceiptSnapshot(intent, 0L) }
        assertThrows(IllegalArgumentException::class.java) { DeliveryReceiptSnapshot(intent.copy(baseRemoteVersion = 7), 7L) }
        assertThrows(IOException::class.java) {
            DeliveryReceiptJournal(file).prepare(intent.copy(actor = intent.actor.copy(issuer = "bad\uD800")))
        }
        assertFalse(file.exists())
    }

    @Test fun realProcessDeathPreservesThePublishedBoundaryAndExactRetry() {
        for (checkpoint in listOf("before-rename", "committed")) {
            val file = file(); val directory = file.parentFile!!
            val marker = File(directory, "ready")
            val classpath = listOf(DeliveryReceiptJournalTest::class.java, DeliveryReceiptJournal::class.java,
                ReservationActor::class.java, kotlin.Unit::class.java)
                .map { File(it.protectionDomain.codeSource.location.toURI()).path }.distinct().joinToString(File.pathSeparator)
            val process = ProcessBuilder(File(System.getProperty("java.home"), "bin/java").path,
                "-cp", classpath, ReceiptJournalChild::class.java.name, file.path, checkpoint)
                .redirectErrorStream(true).redirectOutput(File(directory, "child.log")).start()
            try {
                val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
                while (!marker.isFile && process.isAlive && System.nanoTime() < deadline) Thread.sleep(10)
                assertTrue("Checkpoint missing: ${File(directory, "child.log").readText()}", marker.isFile)
                val pid = java.lang.Process::class.java.getMethod("pid").invoke(process) as Long
                assertEquals(pid.toString(), marker.readText())
                assertTrue(process.isAlive)
                process.destroyForcibly()
                assertTrue(process.waitFor(10, TimeUnit.SECONDS)); assertFalse(process.isAlive)
                val recovered = checkNotNull(DeliveryReceiptJournal(file).load())
                assertEquals(receiptFixture(), recovered.intent)
                assertEquals(if (checkpoint == "committed") 7L else null, recovered.remoteVersion)
                assertEquals(7L, DeliveryReceiptJournal(file).record(receiptFixture(), 7L).remoteVersion)
            } finally {
                if (process.isAlive) { process.destroyForcibly(); process.waitFor(10, TimeUnit.SECONDS) }
            }
        }
    }
}

object ReceiptJournalChild {
    @JvmStatic fun main(args: Array<String>) {
        val file = File(args[0]); val checkpoint = args[1]; val intent = receiptFixture()
        fun hold() {
            val pending = File(file.parentFile, "ready.pending")
            FileOutputStream(pending).use { output ->
                output.write(ProcessHandle.current().pid().toString().toByteArray()); output.fd.sync()
            }
            Files.move(pending.toPath(), File(file.parentFile, "ready").toPath(), StandardCopyOption.ATOMIC_MOVE)
            Thread.sleep(30_000)
            error("Host must force-stop this JVM; normal return is forbidden")
        }
        DeliveryReceiptJournal(file).prepare(intent)
        if (checkpoint == "before-rename") DeliveryReceiptJournal(file) { hold() }.record(intent, 7L)
        else { require(checkpoint == "committed"); DeliveryReceiptJournal(file).record(intent, 7L); hold() }
    }
}

internal fun receiptFixture() = DeliveryReceiptIntent(
    actor = ReservationActor("https://issuer.example", UUID.fromString("00000000-0000-0000-0000-000000000001")),
    localWorkspaceId = UUID.fromString("00000000-0000-0000-0000-000000000002"),
    remoteWorkspaceId = UUID.fromString("00000000-0000-0000-0000-000000000003"),
    consentId = UUID.fromString("00000000-0000-0000-0000-000000000004"),
    farmId = UUID.fromString("00000000-0000-0000-0000-000000000005"),
    operationId = UUID.fromString("00000000-0000-0000-0000-000000000006"),
    deliveryId = UUID.fromString("00000000-0000-0000-0000-000000000007"),
    payloadVersion = 2, payloadHash = "a".repeat(64),
)

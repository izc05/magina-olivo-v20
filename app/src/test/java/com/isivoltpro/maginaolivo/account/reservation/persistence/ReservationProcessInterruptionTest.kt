package com.isivoltpro.maginaolivo.account.reservation.persistence

import com.isivoltpro.maginaolivo.account.reservation.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Real OS process termination, not an exception seam. This is JVM evidence, not Android QA. */
class ReservationProcessInterruptionTest {
    @Test fun forceKilledWriterRecoversPendingOrCommittedReceiptWithoutChangingAccount() {
        val directory = Files.createTempDirectory("g1c-process-interruption").toFile()
        try {
            listOf("prepared", "receipt-before-rename", "receipt-committed").forEach { checkpoint ->
                val file = File(directory, checkpoint)
                val java = File(System.getProperty("java.home"), "bin/java").absolutePath
                val classpath = listOf(javaClass, DurableReservationJournal::class.java, Unit::class.java)
                    .map { File(it.protectionDomain.codeSource.location.toURI()).absolutePath }
                    .distinct().joinToString(File.pathSeparator)
                val process = ProcessBuilder(java, "-cp", classpath,
                    "com.isivoltpro.maginaolivo.account.reservation.persistence.ReservationProcessWriter",
                    file.absolutePath, checkpoint).redirectErrorStream(true).start()
                val reader = Executors.newSingleThreadExecutor()
                try {
                    val ready = reader.submit<String> { process.inputStream.bufferedReader().readLine() }
                    assertEquals("Child must reach the real durable boundary", "CHECKPOINT:$checkpoint",
                        ready.get(30, TimeUnit.SECONDS))
                    process.destroyForcibly()
                    assertTrue("Writer must terminate", process.waitFor(10, TimeUnit.SECONDS))
                    assertNotEquals("Forced process exit must not be normal", 0, process.exitValue())
                    val restored = DurableReservationJournal(file).load(ProcessReservationFixture.scope)!!
                    assertEquals(ProcessReservationFixture.request, restored.request)
                    val committed = checkpoint == "receipt-committed"
                    assertEquals(if (committed) ReservationStatus.RESERVED_REMOTE else ReservationStatus.PENDING, restored.status)
                    assertEquals(if (committed) ProcessReservationFixture.receipt else null, restored.receipt)
                    val other = ProcessReservationFixture.actor.copy(userId = UUID(0, 99))
                    assertNull(DurableReservationJournal(file).load(ProcessReservationFixture.scope.copy(actor = other)))
                    val same = DurableReservationJournal(file).attempt(ProcessReservationFixture.scope, { other },
                        object : ReservationTransport {
                            override fun reserve(scope: ReservationScope, request: ReservationRequest): ReservationResponse =
                                throw AssertionError("Another account must never send this operation")
                        })
                    assertEquals(restored, same)
                    val recovered = DurableReservationJournal(file).attempt(ProcessReservationFixture.scope,
                        { ProcessReservationFixture.actor }, ProcessReservationFixture.transport)
                    assertEquals(ProcessReservationFixture.receipt, recovered.receipt)
                    assertEquals(ReservationStatus.RESERVED_REMOTE, recovered.status)
                } finally {
                    if (process.isAlive) { process.destroyForcibly(); process.waitFor(10, TimeUnit.SECONDS) }
                    reader.shutdownNow()
                }
            }
        } finally { directory.deleteRecursively() }
    }
}

internal object ProcessReservationFixture {
    val actor = ReservationActor("https://process-fixture.example", UUID(0, 1))
    val scope = ReservationScope(actor, UUID(0, 3))
    val request = ReservationRequest(scope.operationId, UUID(0, 2), UUID(0, 4), UUID(0, 5), 2, "a".repeat(64), 1)
    val receipt = ReservationReceipt(scope, request.farmId, request.payloadHash(), UUID(0, 6), UUID(0, 7))
    val transport = object : ReservationTransport {
        override fun reserve(scope: ReservationScope, request: ReservationRequest) = ReservationResponse.Accepted(receipt)
    }
}

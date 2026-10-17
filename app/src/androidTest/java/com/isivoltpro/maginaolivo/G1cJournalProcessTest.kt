package com.isivoltpro.maginaolivo

import android.content.Context
import android.os.ParcelFileDescriptor
import android.os.Process
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.isivoltpro.maginaolivo.account.reservation.*
import com.isivoltpro.maginaolivo.account.reservation.persistence.DurableReservationJournal
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Explicit synthetic two-process Android QA. No app Auth, Room, network or consumer wiring. */
class G1cJournalProcessTest {
    private data class Fixture(val context: Context, val token: String, val checkpoint: String, val directory: File) {
        val journalFile get() = File(directory, "journal.bin")
        val markerFile get() = File(directory, "marker.txt")
        val resultFile get() = File(directory, "result.txt")
    }

    private fun fixture(): Fixture {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val args = InstrumentationRegistry.getArguments()
        assumeTrue("Explicit G1c Android process opt-in required", args.getString("qaG1cJournalProcess") == "true")
        val token = checkNotNull(args.getString("qaG1cJournalToken"))
        check(UUID.fromString(token).toString() == token) { "Canonical UUID required" }
        val checkpoint = checkNotNull(args.getString("qaG1cJournalCheckpoint"))
        check(checkpoint in setOf("prepared", "receipt-before-rename", "receipt-committed"))
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("com.isivoltpro.maginaolivo.dev", context.packageName)
        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand("getprop ro.kernel.qemu"))
            .bufferedReader().use { assertEquals("QEMU only", "1", it.readText().trim()) }
        return Fixture(context, token, checkpoint, File(context.filesDir, "g1c-journal-process/$token/$checkpoint"))
    }

    @Test fun holdAtCheckpoint() {
        val fixture = fixture()
        check(!fixture.directory.exists()) { "Synthetic checkpoint directory already exists" }
        check(fixture.directory.mkdirs()) { "Cannot create a fresh synthetic checkpoint" }
        withEvidence(fixture, "writer") { trace ->
            var commits = 0
            val journal = DurableReservationJournal(fixture.journalFile) {
                commits++
                if (commits == 2 && fixture.checkpoint == "receipt-before-rename") {
                    trace.appendLine("commit_callbacks=$commits")
                    trace.appendLine("receipt_temporary_synced_before_rename=true")
                    hold(fixture, trace)
                }
            }
            assertEquals(ReservationStatus.PENDING, journal.prepare(scopeA, request).status)
            assertEquals(1, commits)
            trace.appendLine("prepare_committed_callbacks=$commits")
            if (fixture.checkpoint == "prepared") hold(fixture, trace)
            val committed = journal.attempt(scopeA, { actorA }, accepted(scopeA, request, receipt))
            assertEquals(2, commits)
            assertEquals(ReservationStatus.RESERVED_REMOTE, committed.status)
            assertEquals(receipt, committed.receipt)
            trace.appendLine("receipt_committed_callbacks=$commits")
            check(fixture.checkpoint == "receipt-committed") { "Writer unexpectedly passed its hold boundary" }
            hold(fixture, trace)
        }
    }

    private fun hold(fixture: Fixture, trace: StringBuilder): Nothing {
        val marker = "checkpoint=${fixture.checkpoint}\npid=${Process.myPid()}\njournal_sha256=${sha256(fixture.journalFile)}\n"
        durableWrite(fixture.markerFile, marker)
        trace.appendLine("marker_durable=true").appendLine("writer_pid=${Process.myPid()}")
        durableWrite(fixture.resultFile, "state=WAITING_FOR_FORCE_STOP\n$trace")
        copyEvidence(fixture)
        println("G1C_CHECKPOINT:${fixture.checkpoint}:pid=${Process.myPid()}")
        CountDownLatch(1).await(60, TimeUnit.SECONDS)
        throw AssertionError("Writer must be force-stopped at the durable boundary; normal return is forbidden")
    }

    @Test fun recoverAfterForceStop() {
        val fixture = fixture()
        check(fixture.directory.isDirectory) { "Exact synthetic checkpoint directory is missing" }
        withEvidence(fixture, "recovery") { trace ->
            val marker = fixture.markerFile.readLines().associate { line ->
                val pair = line.split('=', limit = 2)
                check(pair.size == 2)
                pair[0] to pair[1]
            }
            assertEquals(fixture.checkpoint, marker["checkpoint"])
            val writerPid = checkNotNull(marker["pid"]).toInt()
            assertTrue(writerPid > 0)
            assertNotEquals("Recovery must execute in a different Android process", writerPid, Process.myPid())
            trace.appendLine("writer_pid=$writerPid").appendLine("recovery_pid=${Process.myPid()}")
            durableWrite(File(fixture.directory, "journal-before.bin"), fixture.journalFile.readBytes())
            assertEquals("Committed main bytes must survive force-stop unchanged", marker["journal_sha256"], sha256(fixture.journalFile))
            trace.appendLine("checkpoint_main_hash_verified=true")
            val journal = DurableReservationJournal(fixture.journalFile)
            val restored = checkNotNull(journal.load(scopeA))
            val receiptCommitted = fixture.checkpoint == "receipt-committed"
            assertEquals(request, restored.request)
            assertEquals(if (receiptCommitted) ReservationStatus.RESERVED_REMOTE else ReservationStatus.PENDING, restored.status)
            assertEquals(if (receiptCommitted) receipt else null, restored.receipt)
            trace.appendLine("restored_status=${restored.status}")
                .appendLine("operation_id=${restored.request.operationId}")
                .appendLine("restored_receipt_id=${restored.receipt?.receiptId}")
            assertNull(journal.load(scopeB))
            val originalBytes = fixture.journalFile.readBytes()
            val noSend = object : ReservationTransport {
                override fun reserve(scope: ReservationScope, request: ReservationRequest): ReservationResponse =
                    throw AssertionError("B or an already accepted operation must never call transport")
            }
            assertEquals(restored, journal.attempt(scopeA, { actorB }, noSend))
            durableWrite(File(fixture.directory, "journal-after-b.bin"), fixture.journalFile.readBytes())
            assertArrayEquals(originalBytes, fixture.journalFile.readBytes())
            trace.appendLine("actor_b_scope_hidden_and_no_send_no_main_write=true")

            var retryCalls = 0
            val retry = object : ReservationTransport {
                override fun reserve(scope: ReservationScope, incoming: ReservationRequest): ReservationResponse {
                    assertEquals(scopeA, scope)
                    assertEquals(request, incoming)
                    retryCalls++
                    return ReservationResponse.RetrySameOperation
                }
            }
            assertEquals(restored, journal.attempt(scopeA, { actorA }, retry))
            assertEquals(if (receiptCommitted) 0 else 1, retryCalls)
            assertEquals(request.operationId, checkNotNull(journal.load(scopeA)).request.operationId)
            assertArrayEquals(originalBytes, fixture.journalFile.readBytes())
            trace.appendLine("lost_connection_retry_same_operation_verified=true")

            var acceptedCalls = 0
            val accepting = object : ReservationTransport {
                override fun reserve(scope: ReservationScope, incoming: ReservationRequest): ReservationResponse {
                    assertEquals(scopeA, scope)
                    assertEquals(request, incoming)
                    acceptedCalls++
                    return ReservationResponse.Accepted(receipt)
                }
            }
            assertEquals(receipt, journal.attempt(scopeA, { actorA }, accepting).receipt)
            assertEquals(if (receiptCommitted) 0 else 1, acceptedCalls)
            val reopened = DurableReservationJournal(fixture.journalFile)
            assertEquals(ReservationStatus.RESERVED_REMOTE, reopened.load(scopeA)?.status)
            assertEquals(receipt, reopened.load(scopeA)?.receipt)
            val acceptedBytes = fixture.journalFile.readBytes()
            assertEquals(receipt, reopened.attempt(scopeA, { actorA }, noSend).receipt)
            durableWrite(File(fixture.directory, "journal-after-a.bin"), fixture.journalFile.readBytes())
            assertArrayEquals(acceptedBytes, fixture.journalFile.readBytes())
            assertNull(reopened.load(scopeB))
            trace.appendLine("exact_receipt_durable_and_duplicate_no_send_no_main_write=true")

            // Separate synthetic operation: never rewrite the accepted checkpoint operation.
            val delayedFile = File(fixture.directory, "delayed-journal.bin")
            check(!delayedFile.exists()) { "Recovery has already run for this checkpoint" }
            val delayed = DurableReservationJournal(delayedFile)
            val delayedRequest = request.copy(operationId = UUID(0, 30), farmId = UUID(0, 32))
            val delayedScope = scopeA.copy(operationId = delayedRequest.operationId)
            val delayedReceipt = receipt.copy(scope = delayedScope, farmId = delayedRequest.farmId,
                payloadHash = delayedRequest.payloadHash(), receiptId = UUID(0, 31))
            delayed.prepare(delayedScope, delayedRequest)
            var visible = actorA
            val switching = object : ReservationTransport {
                override fun reserve(scope: ReservationScope, incoming: ReservationRequest): ReservationResponse {
                    assertEquals(delayedScope, scope)
                    assertEquals(delayedRequest, incoming)
                    visible = actorB
                    return ReservationResponse.Accepted(delayedReceipt)
                }
            }
            val waiting = delayed.attempt(delayedScope, { visible }, switching)
            assertEquals(ReservationStatus.AWAITING_ACTOR, waiting.status)
            assertEquals(delayedReceipt, waiting.receipt)
            val delayedBytes = delayedFile.readBytes()
            assertNull(delayed.load(delayedScope.copy(actor = actorB)))
            assertEquals(waiting, delayed.attempt(delayedScope, { actorB }, noSend))
            assertArrayEquals(delayedBytes, delayedFile.readBytes())
            assertEquals(waiting, DurableReservationJournal(delayedFile).load(delayedScope))
            trace.appendLine("delayed_ack_under_b_awaiting_actor_and_no_b_operation=true")
        }
    }

    private fun withEvidence(fixture: Fixture, phase: String, action: (StringBuilder) -> Unit) {
        val started = SystemClock.elapsedRealtime()
        val trace = StringBuilder("phase=$phase\ncheckpoint=${fixture.checkpoint}\n")
        try {
            action(trace)
            durableWrite(fixture.resultFile, "PASS checkpoint=${fixture.checkpoint}\nstate=PASS\nelapsed_ms=${SystemClock.elapsedRealtime() - started}\n$trace")
            copyEvidence(fixture)
        } catch (failure: Throwable) {
            runCatching {
                durableWrite(fixture.resultFile, "state=FAIL\nelapsed_ms=${SystemClock.elapsedRealtime() - started}\n$trace\n${failure.stackTraceToString()}")
                copyEvidence(fixture)
            }.exceptionOrNull()?.let(failure::addSuppressed)
            throw failure
        }
    }

    private fun copyEvidence(fixture: Fixture) {
        val destination = File(checkNotNull(fixture.context.getExternalFilesDir(null)),
            "g1c-journal-process-evidence/${fixture.token}/${fixture.checkpoint}")
        check(destination.mkdirs() || destination.isDirectory)
        val names = setOf("marker.txt", "journal-before.bin", "journal-after-b.bin", "journal-after-a.bin", "result.txt")
        fixture.directory.listFiles().orEmpty().filter { file ->
            file.isFile && file.name in names
        }.forEach { it.copyTo(File(destination, it.name), overwrite = true) }
    }

    private fun durableWrite(file: File, value: String) {
        durableWrite(file, value.toByteArray(Charsets.UTF_8))
    }

    private fun durableWrite(file: File, value: ByteArray) {
        FileOutputStream(file).use { stream -> stream.write(value); stream.flush(); stream.fd.sync() }
    }

    private fun sha256(file: File): String = MessageDigest.getInstance("SHA-256").digest(file.readBytes())
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private fun accepted(expectedScope: ReservationScope, expectedRequest: ReservationRequest, value: ReservationReceipt) =
        object : ReservationTransport {
            override fun reserve(scope: ReservationScope, request: ReservationRequest): ReservationResponse {
                assertEquals(expectedScope, scope)
                assertEquals(expectedRequest, request)
                return ReservationResponse.Accepted(value)
            }
        }

    private companion object {
        val actorA = ReservationActor("https://android-fixture.example", UUID(0, 1))
        val actorB = ReservationActor("https://android-fixture.example", UUID(0, 99))
        val scopeA = ReservationScope(actorA, UUID(0, 3))
        val scopeB = scopeA.copy(actor = actorB)
        val request = ReservationRequest(scopeA.operationId, UUID(0, 2), UUID(0, 4), UUID(0, 5), 2, "a".repeat(64), 1)
        val receipt = ReservationReceipt(scopeA, request.farmId, request.payloadHash(), UUID(0, 6), UUID(0, 7))
    }
}

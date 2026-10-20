package com.isivoltpro.maginaolivo.account.reservation.persistence

import com.isivoltpro.maginaolivo.account.reservation.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.UUID

class DurableReservationJournalTest {
    private val actor = ReservationActor("https://issuer-a.example", UUID(0, 1))
    private val scope = ReservationScope(actor, UUID(0, 3))
    private val request = ReservationRequest(scope.operationId, UUID(0, 2), UUID(0, 4), UUID(0, 5), 1, "a".repeat(64), 7)
    private fun receipt() = ReservationReceipt(scope, request.farmId, request.payloadHash(), UUID(0, 6), UUID(0, 7))
    private fun file() = Files.createTempDirectory("reservation-journal").resolve("journal").toFile()
    private fun accepted() = object : ReservationTransport {
        override fun reserve(scope: ReservationScope, request: ReservationRequest) = ReservationResponse.Accepted(receipt())
    }

    @Test fun `request is durable before transport and receipt survives reopening`() {
        val file = file()
        val journal = DurableReservationJournal(file)
        journal.prepare(scope, request)
        val transport = object : ReservationTransport {
            override fun reserve(scope: ReservationScope, request: ReservationRequest): ReservationResponse {
                assertEquals(request, DurableReservationJournal(file).load(scope)?.request)
                return ReservationResponse.Accepted(receipt())
            }
        }
        assertEquals(ReservationStatus.RESERVED_REMOTE, journal.attempt(scope, { actor }, transport).status)
        assertEquals(receipt(), DurableReservationJournal(file).load(scope)?.receipt)
    }

    @Test fun `lost acknowledgement reopens same immutable operation`() {
        val file = file()
        DurableReservationJournal(file).prepare(scope, request)
        val lost = object : ReservationTransport {
            override fun reserve(scope: ReservationScope, request: ReservationRequest): ReservationResponse = throw IOException("lost ack")
        }
        assertThrows(IOException::class.java) { DurableReservationJournal(file).attempt(scope, { actor }, lost) }
        assertEquals(request, DurableReservationJournal(file).load(scope)?.request)
        assertEquals(receipt(), DurableReservationJournal(file).attempt(scope, { actor }, accepted()).receipt)
        assertThrows(IllegalArgumentException::class.java) { DurableReservationJournal(file).prepare(scope, request.copy(aggregateVersion = 8)) }
    }

    @Test fun `actor and issuer isolation retains late receipt privately`() {
        val file = file()
        val journal = DurableReservationJournal(file)
        journal.prepare(scope, request)
        val other = actor.copy(issuer = "https://issuer-b.example")
        val otherScope = scope.copy(actor = other)
        assertNull(journal.load(otherScope))
        assertEquals(ReservationStatus.PENDING, journal.attempt(scope, { other }, accepted()).status)
        var visible = actor
        val switching = object : ReservationTransport {
            override fun reserve(scope: ReservationScope, request: ReservationRequest): ReservationResponse {
                visible = other
                return ReservationResponse.Accepted(receipt())
            }
        }
        assertEquals(ReservationStatus.AWAITING_ACTOR, journal.attempt(scope, { visible }, switching).status)
        assertEquals(receipt(), DurableReservationJournal(file).load(scope)?.receipt)
    }

    @Test fun `corruption blocks reading and prevents overwrite`() {
        val file = file()
        DurableReservationJournal(file).prepare(scope, request)
        val corrupted = file.readBytes().also { it[it.size / 2] = (it[it.size / 2].toInt() xor 1).toByte() }
        file.writeBytes(corrupted)
        assertThrows(IOException::class.java) { DurableReservationJournal(file).load(scope) }
        assertThrows(IOException::class.java) { DurableReservationJournal(file).prepare(scope, request) }
        assertArrayEquals(corrupted, file.readBytes())
    }

    @Test fun `uncommitted temporary bytes do not replace committed snapshot`() {
        val file = file()
        DurableReservationJournal(file).prepare(scope, request)
        File(file.path + ".tmp").writeText("torn")
        assertEquals(request, DurableReservationJournal(file).load(scope)?.request)
        assertNull(DurableReservationJournal(File(file.parentFile, "absent")).load(scope))
    }

    @Test fun `failed request commit sends nothing and failed receipt commit retains pending`() {
        val file = file()
        var fail = true
        val journal = DurableReservationJournal(file) { if (fail) throw IOException("disk full") }
        assertThrows(IOException::class.java) { journal.prepare(scope, request) }
        assertNull(DurableReservationJournal(file).load(scope))
        fail = false
        journal.prepare(scope, request)
        fail = true
        assertThrows(IOException::class.java) { journal.attempt(scope, { actor }, accepted()) }
        assertEquals(ReservationStatus.PENDING, DurableReservationJournal(file).load(scope)?.status)
    }

    @Test fun `separate instances preserve two actor records and duplicate receipt`() {
        val file = file()
        val other = scope.copy(actor = actor.copy(userId = UUID(0, 8)))
        DurableReservationJournal(file).prepare(scope, request)
        DurableReservationJournal(file).prepare(other, request)
        val journal = DurableReservationJournal(file)
        journal.attempt(scope, { actor }, accepted())
        assertEquals(request, journal.load(other)?.request)
        assertEquals(receipt(), journal.attempt(scope, { actor }, accepted()).receipt)
    }

    @Test fun `orphaned temporaries are removed on recovery while retaining unrelated files`() {
        val file = file()
        val journal = DurableReservationJournal(file)
        journal.prepare(scope, request)
        val parent = file.parentFile
        val orphan1 = File(parent, file.name + ".tmp-" + UUID.randomUUID()).also { it.writeText("stale-1") }
        val orphan2 = File(parent, file.name + ".tmp-" + UUID.randomUUID()).also { it.writeText("stale-2") }
        val otherJournalTemp = File(parent, "other.bin.tmp-" + UUID.randomUUID()).also { it.writeText("other-temp") }
        val nonUuidTemp = File(parent, file.name + ".tmp").also { it.writeText("non-uuid") }
        val unrelated = File(parent, "unrelated.txt").also { it.writeText("data") }

        val reloaded = DurableReservationJournal(file).load(scope)
        assertEquals(request, reloaded?.request)
        assertFalse(orphan1.exists())
        assertFalse(orphan2.exists())
        assertTrue(otherJournalTemp.exists())
        assertTrue(nonUuidTemp.exists())
        assertTrue(unrelated.exists())
    }

    @Test fun `multibyte unicode issuer up to 2048 chars serializes and deserializes accurately`() {
        val file = file()
        val multibyteIssuer = "\u3042".repeat(2048)
        assertEquals(2048, multibyteIssuer.length)
        val multibyteBytes = multibyteIssuer.toByteArray(Charsets.UTF_8).size
        assertEquals(6144, multibyteBytes)
        val multibyteActor = ReservationActor(multibyteIssuer, UUID(0, 1))
        val multibyteScope = ReservationScope(multibyteActor, UUID(0, 3))
        val journal = DurableReservationJournal(file)
        journal.prepare(multibyteScope, request)
        val loaded = DurableReservationJournal(file).load(multibyteScope)
        assertNotNull(loaded)
        assertEquals(multibyteActor, loaded!!.scope.actor)
        assertEquals(request, loaded.request)
    }
}

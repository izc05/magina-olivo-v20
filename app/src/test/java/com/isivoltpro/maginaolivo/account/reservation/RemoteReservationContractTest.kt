package com.isivoltpro.maginaolivo.account.reservation

import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class RemoteReservationContractTest {
    private val actor = ReservationActor("https://issuer-a.example", UUID(0, 1))
    private val farm = UUID(0, 2)
    private val operation = UUID(0, 3)
    private fun request() = ReservationRequest(operation, farm, UUID(0, 4), UUID(0, 5), 1, "a".repeat(64), 7)
    private fun receipt(request: ReservationRequest = request()) = ReservationReceipt(
        ReservationScope(actor, request.operationId), request.farmId, request.payloadHash(), UUID(0, 6), UUID(0, 7))

    @Test fun `lost acknowledgement retries identical operation after recovery`() {
        val request = request()
        val initial = ReservationRecovery(ReservationScope(actor, operation), request)
        val server = MemoryServer()
        assertEquals(ReservationStatus.PENDING, initial.attempt({ actor }, server).status)
        val restarted = ReservationRecovery(initial.scope, initial.request)
        val recovered = restarted.attempt({ actor }, server)
        assertEquals(ReservationStatus.RESERVED_REMOTE, recovered.status)
        assertEquals(1, server.reservations.size)
        assertEquals(receipt(), recovered.receipt)
    }

    @Test fun `changed payload on same operation fails closed`() {
        val server = MemoryServer()
        val first = ReservationRecovery(ReservationScope(actor, operation), request())
        first.attempt({ actor }, server)
        val changed = ReservationRecovery(first.scope, request().copy(aggregateVersion = 8))
        assertEquals(ReservationStatus.CONFLICT, changed.attempt({ actor }, server).status)
    }

    @Test fun `late acknowledgement stays private until correct actor resumes`() {
        val pending = ReservationRecovery(ReservationScope(actor, operation), request())
        val deferred = pending.accept(receipt(), actor.copy(userId = UUID(0, 90)))
        assertEquals(ReservationStatus.AWAITING_ACTOR, deferred.status)
        assertEquals(receipt(), deferred.receipt)
        assertEquals(ReservationStatus.RESERVED_REMOTE, deferred.accept(receipt(), actor).status)
        val server = MemoryServer()
        assertEquals(ReservationStatus.RESERVED_REMOTE, deferred.attempt({ actor }, server).status)
        assertTrue(server.reservations.isEmpty())
    }

    @Test fun `mismatched receipt cannot bind farm`() {
        val pending = ReservationRecovery(ReservationScope(actor, operation), request())
        listOf(receipt().copy(scope = ReservationScope(actor.copy(userId = UUID(0, 90)), operation)),
            receipt().copy(scope = ReservationScope(actor, UUID(0, 91))),
            receipt().copy(farmId = UUID(0, 92)), receipt().copy(payloadHash = "b".repeat(64)))
            .forEach { assertEquals(ReservationStatus.INVALID_RECEIPT, pending.accept(it, actor).status) }
    }

    @Test fun `wrong visible actor sends nothing`() {
        val pending = ReservationRecovery(ReservationScope(actor, operation), request())
        val server = MemoryServer()
        assertEquals(pending, pending.attempt({ actor.copy(userId = UUID(0, 90)) }, server))
        assertTrue(server.reservations.isEmpty())
    }

    @Test fun `hash binds every payload field deterministically`() {
        val request = request()
        assertEquals(request.payloadHash(), request.copy().payloadHash())
        listOf(request.copy(consentTextVersion = 2), request.copy(aggregateVersion = 8),
            request.copy(manifestSha256 = "b".repeat(64)), request.copy(farmId = UUID(0, 9)),
            request.copy(operationId = UUID(0, 10)), request.copy(consentId = UUID(0, 11)),
            request.copy(localWorkspaceId = UUID(0, 12)))
            .forEach { assertNotEquals(request.payloadHash(), it.payloadHash()) }
    }

    @Test fun `foreign or legacy farm conflict cannot be retried into ownership`() {
        val server = object : ReservationTransport {
            override fun reserve(scope: ReservationScope, request: ReservationRequest) = ReservationResponse.Conflict
        }
        val pending = ReservationRecovery(ReservationScope(actor, operation), request())
        val conflict = pending.attempt({ actor }, server)
        assertEquals(ReservationStatus.CONFLICT, conflict.status)
        assertNull(conflict.receipt)
        assertEquals(conflict, conflict.attempt({ actor }, MemoryServer()))
    }

    @Test fun `receipt cannot change after it has been retained`() {
        val pending = ReservationRecovery(ReservationScope(actor, operation), request())
        val retained = pending.accept(receipt(), null)
        val altered = receipt().copy(remoteWorkspaceId = UUID(0, 99))
        assertEquals(ReservationStatus.INVALID_RECEIPT, retained.accept(altered, actor).status)
    }

    @Test fun `invalid manifest or aggregate is rejected before transport`() {
        assertThrows(IllegalArgumentException::class.java) { request().copy(manifestSha256 = "invalid") }
        assertThrows(IllegalArgumentException::class.java) { request().copy(aggregateVersion = -1) }
        assertThrows(IllegalArgumentException::class.java) { request().copy(aggregateVersion = 0) }
        assertThrows(IllegalArgumentException::class.java) { request().copy(consentTextVersion = 0) }
        assertThrows(IllegalArgumentException::class.java) {
            ReservationRecovery(ReservationScope(actor, UUID(0, 99)), request())
        }
    }

    @Test fun `account change during request cannot apply acknowledgement to visible account`() {
        var visibleActor: ReservationActor? = actor
        val server = object : ReservationTransport {
            override fun reserve(scope: ReservationScope, request: ReservationRequest): ReservationResponse {
                visibleActor = actor.copy(userId = UUID(0, 90))
                return ReservationResponse.Accepted(receipt(request))
            }
        }
        val pending = ReservationRecovery(ReservationScope(actor, operation), request())
        val result = pending.attempt({ visibleActor }, server)
        assertEquals(ReservationStatus.AWAITING_ACTOR, result.status)
        assertEquals(receipt(), result.receipt)
    }

    @Test fun `restored reserved snapshot rejects mismatched receipt`() {
        assertThrows(IllegalArgumentException::class.java) {
            ReservationRecovery(ReservationScope(actor, operation), request(), ReservationStatus.RESERVED_REMOTE,
                receipt().copy(farmId = UUID(0, 99)))
        }
    }

    @Test fun `same user UUID under different issuer cannot send or apply receipt`() {
        val otherProject = actor.copy(issuer = "https://issuer-b.example")
        val pending = ReservationRecovery(ReservationScope(actor, operation), request())
        val server = MemoryServer()
        assertEquals(pending, pending.attempt({ otherProject }, server))
        assertTrue(server.reservations.isEmpty())
        assertEquals(ReservationStatus.INVALID_RECEIPT,
            pending.accept(receipt().copy(scope = ReservationScope(otherProject, operation)), actor).status)
    }

    @Test fun `issuer must be bounded and nonblank`() {
        listOf("", " ", "x".repeat(2049)).forEach { issuer ->
            assertThrows(IllegalArgumentException::class.java) { actor.copy(issuer = issuer) }
        }
        val validMultibyte = "\u3042".repeat(2048)
        assertEquals(2048, validMultibyte.length)
        assertEquals(6144, validMultibyte.toByteArray(Charsets.UTF_8).size)
        val validActor = actor.copy(issuer = validMultibyte)
        assertEquals(validMultibyte, validActor.issuer)
    }

    @Test fun `consent text version must be positive`() {
        assertThrows(IllegalArgumentException::class.java) { request().copy(consentTextVersion = 0) }
        assertThrows(IllegalArgumentException::class.java) { request().copy(consentTextVersion = -1) }
    }

    @Test fun `canonical hash matches independently calculated wire vector`() {
        assertEquals("cf676e143f408ff520e646aed3ac57fd3ae1838eca2d769775c46b1668a7de33",
            request().payloadHash())
    }

    private inner class MemoryServer : ReservationTransport {
        val reservations = mutableMapOf<ReservationScope, ReservationReceipt>()
        override fun reserve(scope: ReservationScope, request: ReservationRequest): ReservationResponse {
            val existing = reservations[scope]
            if (existing != null) return if (existing.payloadHash == request.payloadHash())
                ReservationResponse.Accepted(existing) else ReservationResponse.Conflict
            reservations[scope] = receipt(request)
            return ReservationResponse.RetrySameOperation
        }
    }
}

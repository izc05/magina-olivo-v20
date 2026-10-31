package com.isivoltpro.maginaolivo.feature.accountlink

import java.time.Instant
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** G2.4: renewed consent is ordered by the ledger sequence, never by device clocks. */
class FarmLinkLedgerOrderTest {
    private val subject = "issuer|00000000-0000-0000-0000-00000000000a"
    private val farm = UUID.fromString("20000000-0000-0000-0000-000000000001")
    private val other = UUID.fromString("20000000-0000-0000-0000-000000000002")
    private val t = Instant.parse("2026-10-24T08:00:00Z")
    private val consent = FarmLinkConsent(UUID.randomUUID(), subject,
        listOf(FarmChoice(farm, UUID.randomUUID(), "Los Olivos", 1), FarmChoice(other, UUID.randomUUID(), "El Cerrón", 1)),
        UUID.randomUUID(), "a".repeat(64), t)
    private val first = FarmLinkRevocation(UUID.randomUUID(), subject, farm, t, sequence = 1)

    @Test fun renewalAfterTheRevocationRestoresTheFarmEvenWithAnEarlierClock() {
        // The device clock went backwards: the renewal's timestamp is BEFORE the revocation.
        val renewal = FarmLinkReconsent(UUID.randomUUID(), subject, farm, first.operationId, t.minusSeconds(3600), sequence = 2)
        val ledger = FarmLinkLedger(listOf(consent), listOf(first), listOf(renewal))
        assertEquals(setOf(farm, other), ledger.activeFarmIds)
        assertEquals(renewal, ledger.reconsentAfter(first.operationId))
        assertNull(ledger.latestRevocation(farm))
    }

    @Test fun renewalOrderedBeforeTheRevocationDoesNotCount() {
        val early = FarmLinkReconsent(UUID.randomUUID(), subject, farm, first.operationId, t.plusSeconds(3600), sequence = 0)
        val ledger = FarmLinkLedger(listOf(consent), listOf(first.copy(sequence = 5)), listOf(early))
        assertEquals(setOf(farm), ledger.revokedFarmIds)
        assertNull(ledger.reconsentAfter(first.operationId))
    }

    @Test fun aLaterSecondRevocationWithdrawsTheRenewalAgain() {
        val renewal = FarmLinkReconsent(UUID.randomUUID(), subject, farm, first.operationId, t, sequence = 2)
        val second = FarmLinkRevocation(UUID.randomUUID(), subject, farm, t, sequence = 3)
        val ledger = FarmLinkLedger(listOf(consent), listOf(first, second), listOf(renewal))
        assertEquals(setOf(farm), ledger.revokedFarmIds)
        assertEquals(second, ledger.latestRevocation(farm))
        assertNull("The farm is revoked again: the old renewal no longer authorizes", ledger.reconsentAfter(first.operationId))
        assertNull(ledger.reconsentAfter(second.operationId))
    }

    @Test fun renewalOfAnotherFarmOrRevocationDoesNotCount() {
        val otherRevocation = FarmLinkRevocation(UUID.randomUUID(), subject, other, t, sequence = 2)
        val wrongTarget = FarmLinkReconsent(UUID.randomUUID(), subject, farm, otherRevocation.operationId, t, sequence = 3)
        val ledger = FarmLinkLedger(listOf(consent), listOf(first, otherRevocation), listOf(wrongTarget))
        assertNull(ledger.reconsentAfter(first.operationId))
        assertNull(ledger.reconsentAfter(otherRevocation.operationId))
    }
}

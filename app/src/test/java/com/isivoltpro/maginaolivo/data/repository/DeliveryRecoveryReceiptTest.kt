package com.isivoltpro.maginaolivo.data.repository

import com.isivoltpro.maginaolivo.data.local.entity.SyncOutboxEntity
import com.isivoltpro.maginaolivo.data.local.model.OutboxOperation
import com.isivoltpro.maginaolivo.data.local.model.OutboxStatus
import com.isivoltpro.maginaolivo.data.local.model.SyncEntityType
import java.time.Instant
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class DeliveryRecoveryReceiptTest {
    private val operationId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val deliveryId = UUID.fromString("00000000-0000-0000-0000-000000000002")
    private val farmId = UUID.fromString("00000000-0000-0000-0000-000000000003")
    private val now = Instant.parse("2026-10-22T00:00:00Z")
    private val snapshot = """{"delivery_id":"00000000-0000-0000-0000-000000000002"}"""

    private fun operation() = SyncOutboxEntity(
        id = operationId,
        entityType = SyncEntityType.DELIVERY,
        entityId = deliveryId,
        operation = OutboxOperation.CREATE,
        payloadVersion = 1,
        createdAt = now,
        updatedAt = now,
        accountId = "account-A",
        farmId = farmId,
        payloadJson = snapshot,
    )

    private fun receipt() = RecoveredDeliveryReceipt(
        operationId = operationId,
        deliveryId = deliveryId,
        accountId = "account-A",
        remoteVersion = 7,
        payloadHash = "server-hash",
    )

    @Test fun matchingReceiptPreservesExactSnapshot() {
        val verified = verifiedRecoveredDeliveryReceipt(operation(), receipt()) { payload, hash ->
            payload == snapshot && hash == "server-hash"
        }
        assertNotNull(verified)
        assertEquals(snapshot, verified!!.payloadJson)
        assertEquals(farmId, verified.farmId)
        assertEquals(7L, verified.remoteVersion)
    }

    @Test fun rejectsWrongAccountOrOperation() {
        assertNull(verifiedRecoveredDeliveryReceipt(operation(), receipt().copy(accountId = "account-B")) { _, _ -> true })
        assertNull(verifiedRecoveredDeliveryReceipt(operation(), receipt().copy(operationId = UUID.randomUUID())) { _, _ -> true })
        assertNull(verifiedRecoveredDeliveryReceipt(operation(), receipt().copy(deliveryId = UUID.randomUUID())) { _, _ -> true })
    }

    @Test fun rejectsMissingBindingsOrHashMismatch() {
        assertNull(verifiedRecoveredDeliveryReceipt(operation().copy(accountId = null), receipt()) { _, _ -> true })
        assertNull(verifiedRecoveredDeliveryReceipt(operation().copy(farmId = null), receipt()) { _, _ -> true })
        assertNull(verifiedRecoveredDeliveryReceipt(operation().copy(payloadJson = null), receipt()) { _, _ -> true })
        assertNull(verifiedRecoveredDeliveryReceipt(operation(), receipt()) { _, _ -> false })
        assertNull(verifiedRecoveredDeliveryReceipt(operation(), receipt().copy(payloadHash = "")) { _, _ -> true })
    }

    @Test fun rejectsBlockedConfirmedOrInvalidVersion() {
        assertNull(verifiedRecoveredDeliveryReceipt(operation().copy(status = OutboxStatus.BLOCKED), receipt()) { _, _ -> true })
        assertNull(verifiedRecoveredDeliveryReceipt(operation().copy(status = OutboxStatus.CONFIRMED), receipt()) { _, _ -> true })
        assertNull(verifiedRecoveredDeliveryReceipt(operation(), receipt().copy(remoteVersion = 0)) { _, _ -> true })
    }

    @Test fun rejectsInvalidBaseVersionContracts() {
        assertNull(verifiedRecoveredDeliveryReceipt(operation().copy(baseRemoteVersion = 1), receipt()) { _, _ -> true })
        val update = operation().copy(operation = OutboxOperation.UPDATE)
        assertNull(verifiedRecoveredDeliveryReceipt(update, receipt()) { _, _ -> true })
        assertNull(verifiedRecoveredDeliveryReceipt(update.copy(baseRemoteVersion = 7), receipt()) { _, _ -> true })
        assertNotNull(verifiedRecoveredDeliveryReceipt(update.copy(baseRemoteVersion = 6), receipt()) { _, _ -> true })
        assertNull(verifiedRecoveredDeliveryReceipt(operation().copy(payloadJson = " "), receipt()) { _, _ -> true })
    }
}

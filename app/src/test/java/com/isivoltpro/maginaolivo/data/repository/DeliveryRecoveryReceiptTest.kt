package com.isivoltpro.maginaolivo.data.repository

import com.isivoltpro.maginaolivo.data.local.entity.SyncOutboxEntity
import com.isivoltpro.maginaolivo.data.local.model.OutboxOperation
import com.isivoltpro.maginaolivo.data.local.model.OutboxStatus
import com.isivoltpro.maginaolivo.data.local.model.SyncEntityType
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
        payloadHash = "a".repeat(64),
    )

    @Test fun defaultVerifierUsesTheSharedPostgresHashAndPreservesOriginalBytes() {
        val payload = """{"netGrams":2500000,"notes":"Mágina","id":"x"}"""
        val op = operation().copy(payloadJson = payload)
        // Exact immutable digest independently checked in disposable PostgreSQL.
        val ack = receipt().copy(payloadHash = "0bfbfddbfc532eca8c68abd37a0d9b5e01dfd8df30b68ea4429b7f198225302b")
        val verified = verifiedRecoveredDeliveryReceipt(op, ack)
        assertNotNull(verified)
        assertEquals(payload, verified!!.payloadJson)
        assertEquals(op.id, verified.operationId)
        assertEquals(op.accountId, verified.accountId)
        assertNull(verifiedRecoveredDeliveryReceipt(op.copy(payloadJson = payload.replace("2500000", "2500001")), ack))
        assertNull(verifiedRecoveredDeliveryReceipt(op, ack.copy(payloadHash = "0".repeat(64))))
        assertNull(verifiedRecoveredDeliveryReceipt(op.copy(payloadJson = """{"notes":null}"""), ack))
    }

    @Test fun v2ReceiptRequiresMatchingVersionAndSharedParcelHash() {
        val fixture = checkNotNull(javaClass.getResourceAsStream("/g2-delivery-payload-hash-vectors.json"))
            .bufferedReader(Charsets.UTF_8).use { it.readText() }
        val row = kotlinx.serialization.json.Json.parseToJsonElement(fixture).jsonArray[6].jsonObject
        val payload = row.getValue("payload").jsonPrimitive.content
        val op = operation().copy(payloadVersion = 2, payloadJson = payload)
        val ack = receipt().copy(payloadVersion = 2, payloadHash = row.getValue("sha256").jsonPrimitive.content)
        val verified = verifiedRecoveredDeliveryReceipt(op, ack)
        assertNotNull(verified)
        assertEquals(payload, verified!!.payloadJson)
        assertNull(verifiedRecoveredDeliveryReceipt(op, ack.copy(payloadVersion = 1)))
        assertNull(verifiedRecoveredDeliveryReceipt(op.copy(payloadVersion = 1), ack))
        assertNull(verifiedRecoveredDeliveryReceipt(op.copy(payloadJson = payload.replace("1000000", "1000001")), ack))
        assertNull(verifiedRecoveredDeliveryReceipt(op, ack.copy(payloadVersion = 3)))
    }

    @Test fun matchingReceiptPreservesExactSnapshot() {
        val verified = verifiedRecoveredDeliveryReceipt(operation(), receipt()) { payload, hash ->
            payload == snapshot && hash == "a".repeat(64)
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
        assertNull(verifiedRecoveredDeliveryReceipt(operation().copy(accountId = ""), receipt().copy(accountId = "")) { _, _ -> true })
        assertNull(verifiedRecoveredDeliveryReceipt(operation(), receipt().copy(accountId = "")) { _, _ -> true })
        assertNull(verifiedRecoveredDeliveryReceipt(operation().copy(farmId = null), receipt()) { _, _ -> true })
        assertNull(verifiedRecoveredDeliveryReceipt(operation().copy(payloadJson = null), receipt()) { _, _ -> true })
        assertNull(verifiedRecoveredDeliveryReceipt(operation(), receipt()) { _, _ -> false })
        assertNull(verifiedRecoveredDeliveryReceipt(operation(), receipt().copy(payloadHash = "")) { _, _ -> true })
        assertNull(verifiedRecoveredDeliveryReceipt(operation(), receipt().copy(payloadHash = "not-a-sha256")) { _, _ -> true })
        assertNull(verifiedRecoveredDeliveryReceipt(operation(), receipt().copy(payloadHash = "G".repeat(64))) { _, _ -> true })
        assertNull(verifiedRecoveredDeliveryReceipt(operation(), receipt().copy(payloadHash = "A".repeat(64))) { _, _ -> true })
    }

    @Test fun rejectsNonDeliveryAndNonWriteOperations() {
        assertNull(verifiedRecoveredDeliveryReceipt(operation().copy(entityType = SyncEntityType.FARM), receipt()) { _, _ -> true })
        assertNull(verifiedRecoveredDeliveryReceipt(operation().copy(operation = OutboxOperation.DELETE), receipt()) { _, _ -> true })
        assertNull(verifiedRecoveredDeliveryReceipt(operation().copy(operation = OutboxOperation.UPLOAD_ATTACHMENT), receipt()) { _, _ -> true })
    }

    @Test fun allowsOnlyRetryableOutboxStates() {
        for (status in listOf(OutboxStatus.PENDING, OutboxStatus.PROCESSING, OutboxStatus.FAILED)) {
            assertNotNull(verifiedRecoveredDeliveryReceipt(operation().copy(status = status), receipt()) { _, _ -> true })
        }
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
        assertNull(verifiedRecoveredDeliveryReceipt(operation().copy(payloadVersion = 0), receipt()) { _, _ -> true })
        assertNull(verifiedRecoveredDeliveryReceipt(operation().copy(payloadVersion = 2), receipt()) { _, _ -> true })
        assertNull(verifiedRecoveredDeliveryReceipt(update.copy(baseRemoteVersion = -1), receipt()) { _, _ -> true })
        assertNull(verifiedRecoveredDeliveryReceipt(update.copy(baseRemoteVersion = 0), receipt()) { _, _ -> true })
    }
}

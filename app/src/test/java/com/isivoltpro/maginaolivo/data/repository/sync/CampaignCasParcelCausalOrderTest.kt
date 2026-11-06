package com.isivoltpro.maginaolivo.data.repository.sync

import com.isivoltpro.maginaolivo.data.local.entity.LocalMetadata
import com.isivoltpro.maginaolivo.data.local.entity.ParcelEntity
import com.isivoltpro.maginaolivo.data.local.entity.SyncOutboxEntity
import com.isivoltpro.maginaolivo.data.local.model.OutboxOperation
import com.isivoltpro.maginaolivo.data.local.model.OutboxStatus
import com.isivoltpro.maginaolivo.data.local.model.RecordStatus
import com.isivoltpro.maginaolivo.data.local.model.SyncEntityType
import com.isivoltpro.maginaolivo.data.local.model.SyncStatus
import java.time.Instant
import java.util.UUID
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** G2.6 2b: the only PARCEL marker a campaign CAS may be staged over is one later, never-sent ARCHIVE. */
class CampaignCasParcelCausalOrderTest {
    private val parcelId = UUID.fromString("22222222-2222-2222-2222-222222222222")
    private val t0 = Instant.parse("2026-11-02T10:00:00Z")

    private fun parcel(status: RecordStatus = RecordStatus.ARCHIVED, sync: SyncStatus = SyncStatus.PENDING,
        remote: Long? = 1, deleted: Instant? = t0.plusSeconds(10)) = ParcelEntity(
        id = parcelId, workspaceId = UUID.randomUUID(), displayName = "Parcela", source = "MANUAL", status = status,
        metadata = LocalMetadata(t0, t0, deletedAt = deleted, version = 2, syncStatus = sync, remoteVersion = remote),
    )

    private fun marker(operation: OutboxOperation, at: Long, status: OutboxStatus = OutboxStatus.PENDING,
        account: String? = null, payload: String? = null, base: Long? = null, attempts: Int = 0,
        code: String? = null) = SyncOutboxEntity(
        id = UUID.randomUUID(), entityType = SyncEntityType.PARCEL, entityId = parcelId, operation = operation,
        payloadVersion = 1, baseRemoteVersion = base, status = status, attemptCount = attempts, lastErrorCode = code,
        createdAt = t0.plusSeconds(at), updatedAt = t0.plusSeconds(at), accountId = account, payloadJson = payload,
    )

    private val created = marker(OutboxOperation.CREATE, 0, OutboxStatus.CONFIRMED, account = "a")

    @Test fun unchangedRuleWithoutOpenMarkersStillRequiresSynced() {
        assertTrue(parcelRemoteStateConfirmed(parcel(RecordStatus.ACTIVE, SyncStatus.SYNCED, deleted = null), listOf(created)))
        assertFalse(parcelRemoteStateConfirmed(parcel(RecordStatus.ACTIVE, SyncStatus.PENDING, deleted = null), listOf(created)))
        assertFalse(parcelRemoteStateConfirmed(parcel(RecordStatus.ACTIVE, SyncStatus.CONFLICT, deleted = null), listOf(created)))
    }

    @Test fun oneLaterUnsentArchiveDoesNotChangeTheConfirmedRemoteVersion() {
        for (code in listOf(null, "WAITING_DEPENDENCIES", "RESOLVED_REVIEW")) {
            assertTrue("$code", parcelRemoteStateConfirmed(parcel(), listOf(created, marker(OutboxOperation.ARCHIVE, 10, code = code))))
        }
    }

    @Test fun boundAttemptedBlockedOrFailedArchivesRefuse() {
        val refused = listOf(
            marker(OutboxOperation.ARCHIVE, 10, account = "a", payload = "{}", base = 1),
            marker(OutboxOperation.ARCHIVE, 10, payload = "{}"),
            marker(OutboxOperation.ARCHIVE, 10, account = "a"),
            marker(OutboxOperation.ARCHIVE, 10, base = 1),
            marker(OutboxOperation.ARCHIVE, 10, attempts = 1),
            marker(OutboxOperation.ARCHIVE, 10, OutboxStatus.FAILED, code = "OUTCOME_UNKNOWN"),
            marker(OutboxOperation.ARCHIVE, 10, OutboxStatus.BLOCKED, code = "ARCHIVE_BLOCKED_CAMPAIGN"),
            marker(OutboxOperation.ARCHIVE, 10, code = "NEEDS_REBASE"),
        )
        refused.forEachIndexed { index, archive ->
            assertFalse("case $index", parcelRemoteStateConfirmed(parcel(), listOf(created, archive)))
        }
    }

    @Test fun otherOperationsOrSeveralOpenMarkersRefuse() {
        for (operation in listOf(OutboxOperation.UPDATE, OutboxOperation.RESTORE, OutboxOperation.DELETE)) {
            assertFalse("$operation", parcelRemoteStateConfirmed(parcel(), listOf(created, marker(operation, 10))))
        }
        assertFalse(parcelRemoteStateConfirmed(parcel(),
            listOf(created, marker(OutboxOperation.UPDATE, 5), marker(OutboxOperation.ARCHIVE, 10))))
        assertFalse(parcelRemoteStateConfirmed(parcel(),
            listOf(created, marker(OutboxOperation.ARCHIVE, 10), marker(OutboxOperation.RESTORE, 20))))
    }

    @Test fun theArchiveMustBeTheLatestMarkerAndTheParcelLocallyArchivedOverAConfirmedVersion() {
        val archive = marker(OutboxOperation.ARCHIVE, 10)
        assertFalse(parcelRemoteStateConfirmed(parcel(),
            listOf(created, archive, marker(OutboxOperation.UPDATE, 20, OutboxStatus.CONFIRMED, account = "a"))))
        assertFalse(parcelRemoteStateConfirmed(parcel(remote = null), listOf(created, archive)))
        assertFalse(parcelRemoteStateConfirmed(parcel(remote = 0), listOf(created, archive)))
        assertFalse(parcelRemoteStateConfirmed(parcel(status = RecordStatus.ACTIVE, deleted = null), listOf(created, archive)))
        assertFalse(parcelRemoteStateConfirmed(parcel(deleted = null), listOf(created, archive)))
        assertFalse(parcelRemoteStateConfirmed(parcel(sync = SyncStatus.CONFLICT), listOf(created, archive)))
        assertFalse(parcelRemoteStateConfirmed(parcel(sync = SyncStatus.SYNCED), listOf(created, archive)))
    }
}

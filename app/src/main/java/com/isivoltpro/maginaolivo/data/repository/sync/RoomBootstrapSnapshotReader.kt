package com.isivoltpro.maginaolivo.data.repository.sync

import androidx.room.withTransaction
import com.isivoltpro.maginaolivo.data.local.MaginaOlivoDatabase
import com.isivoltpro.maginaolivo.data.local.entity.*
import com.isivoltpro.maginaolivo.data.local.model.CampaignStatus
import java.util.Collections
import java.util.UUID

internal class BootstrapParcelSnapshot internal constructor(
    val parcel: ParcelEntity,
    memberships: List<FarmParcelMembershipEntity>,
) {
    val memberships: List<FarmParcelMembershipEntity> = Collections.unmodifiableList(memberships.toList())
}

/** Raw local rows, including local metadata. Not a remote DTO or proof of remote ownership. */
internal class RoomBootstrapSnapshot internal constructor(
    val workspace: WorkspaceEntity,
    farms: List<FarmEntity>,
    val campaign: CampaignEntity,
    campaignParcels: List<CampaignParcelSnapshotEntity>,
    parcels: List<BootstrapParcelSnapshot>,
) {
    val farms: List<FarmEntity> = Collections.unmodifiableList(farms.toList())
    val campaignParcels: List<CampaignParcelSnapshotEntity> = Collections.unmodifiableList(campaignParcels.toList())
    val parcels: List<BootstrapParcelSnapshot> = Collections.unmodifiableList(parcels.toList())
}

/**
 * Read-only preparation for ONE explicit farm/campaign, plus explicit extra delivery parcels.
 * All rows are read in one Room transaction; no auto-selection of workspace/account or outbox writes.
 * Consented IDs are an input guard, not durable consent verification. A future coordinator must
 * obtain/recheck them from the account-bound ledger before persistence and immediately around sending.
 */
internal class RoomBootstrapSnapshotReader(private val db: MaginaOlivoDatabase) {
    suspend fun capture(
        workspaceId: UUID,
        farmId: UUID,
        campaignId: UUID,
        additionalParcelIds: Set<UUID>,
        consentedFarmIds: Set<UUID>,
    ): RoomBootstrapSnapshot {
        // Snapshot caller collections before the first suspension point.
        val extra = additionalParcelIds.toSet()
        val consented = consentedFarmIds.toSet()
        require(extra.size <= 1000 && consented.size in 1..10000 && farmId in consented)
        return db.withTransaction {
            val workspace = requireNotNull(db.workspaceDao().findById(workspaceId)) { "Workspace missing" }
            require(workspace.metadata.deletedAt == null)
            val campaign = requireNotNull(db.campaignDao().findById(campaignId)) { "Campaign missing" }
            require(campaign.workspaceId == workspaceId && campaign.farmId == farmId && campaign.metadata.deletedAt == null)
            val snapshots = db.campaignDao().listSnapshots(campaignId).sortedBy { it.id.toString() }
            require(snapshots.size <= 1000 && (snapshots.isNotEmpty() || campaign.status == CampaignStatus.PREPARATION))
            require(snapshots.map { it.parcelId }.distinct().size == snapshots.size)
            snapshots.forEach { snapshot ->
                // The current server create contract cannot carry tombstones or another farm-at-start.
                // Fail the whole preparation rather than silently dropping or rewriting historical rows.
                require(snapshot.workspaceId == workspaceId && snapshot.campaignId == campaignId &&
                    snapshot.farmIdAtStart == farmId && snapshot.metadata.deletedAt == null)
            }
            val parcelIds = snapshots.map { it.parcelId }.toSet() + extra
            require(parcelIds.size <= 1000)
            val requiredFarmIds = mutableSetOf(farmId)
            val parcels = parcelIds.sortedBy(UUID::toString).map { parcelId ->
                val parcel = requireNotNull(db.parcelDao().findById(parcelId)) { "Parcel missing" }
                require(parcel.workspaceId == workspaceId && parcel.metadata.deletedAt == null)
                // Keep ended and logically deleted memberships: they are part of the restored history.
                val memberships = db.parcelDao().listMemberships(parcelId).sortedBy { it.id.toString() }
                require(memberships.size in 1..1000)
                memberships.forEach { membership ->
                    require(membership.workspaceId == workspaceId && membership.parcelId == parcelId)
                    require(membership.farmId in consented) { "Unconsented farm history" }
                    requiredFarmIds.add(membership.farmId)
                }
                // Every selected parcel must have a real historical association with this farm.
                require(memberships.any { it.farmId == farmId })
                BootstrapParcelSnapshot(parcel, memberships)
            }
            val farms = requiredFarmIds.sortedBy(UUID::toString).map { requiredId ->
                val farm = requireNotNull(db.farmDao().findById(requiredId)) { "Farm missing" }
                require(farm.workspaceId == workspaceId && farm.metadata.deletedAt == null)
                farm
            }
            RoomBootstrapSnapshot(workspace, farms, campaign, snapshots, parcels)
        }
    }
}

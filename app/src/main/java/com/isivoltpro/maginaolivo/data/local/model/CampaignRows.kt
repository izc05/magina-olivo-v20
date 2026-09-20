package com.isivoltpro.maginaolivo.data.local.model

import androidx.room.Embedded
import androidx.room.Relation
import com.isivoltpro.maginaolivo.data.local.entity.CampaignEntity
import com.isivoltpro.maginaolivo.data.local.entity.CampaignParcelSnapshotEntity

data class CampaignWithSnapshots(
    @Embedded val campaign: CampaignEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "campaign_id",
    )
    val snapshots: List<CampaignParcelSnapshotEntity>,
)

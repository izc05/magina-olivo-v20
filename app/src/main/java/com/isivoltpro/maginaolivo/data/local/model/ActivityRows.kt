package com.isivoltpro.maginaolivo.data.local.model

import androidx.room.Embedded
import androidx.room.Relation
import com.isivoltpro.maginaolivo.data.local.entity.ActivityEntity
import com.isivoltpro.maginaolivo.data.local.entity.ActivityParcelTargetEntity
import com.isivoltpro.maginaolivo.data.local.entity.FertilizationDetailEntity
import com.isivoltpro.maginaolivo.data.local.entity.IncidentDetailEntity
import com.isivoltpro.maginaolivo.data.local.entity.IrrigationDetailEntity
import com.isivoltpro.maginaolivo.data.local.entity.IrrigationPriceSnapshotEntity
import com.isivoltpro.maginaolivo.data.local.entity.MaintenanceDetailEntity
import com.isivoltpro.maginaolivo.data.local.entity.PhytosanitaryDetailEntity
import com.isivoltpro.maginaolivo.data.local.entity.PruningDetailEntity
import com.isivoltpro.maginaolivo.data.local.entity.SoilWorkDetailEntity

/**
 * One Activity read whole: its header, its Parcel targets and its typed detail.
 *
 * Every detail relation is nullable and at most one of them is ever set, because the
 * detail must match the Activity's own type. Reading them together is what lets the
 * repository return a complete aggregate in a single observation, instead of the screen
 * having to stitch a header and a detail from two sources that can disagree.
 */
data class ActivityWithTargets(
    @Embedded val activity: ActivityEntity,
    @Relation(parentColumn = "id", entityColumn = "activity_id")
    val targets: List<ActivityParcelTargetEntity>,
    @Relation(parentColumn = "id", entityColumn = "activity_id")
    val pruning: PruningDetailEntity? = null,
    @Relation(parentColumn = "id", entityColumn = "activity_id")
    val fertilization: FertilizationDetailEntity? = null,
    @Relation(parentColumn = "id", entityColumn = "activity_id")
    val phytosanitary: PhytosanitaryDetailEntity? = null,
    @Relation(parentColumn = "id", entityColumn = "activity_id")
    val soilWork: SoilWorkDetailEntity? = null,
    @Relation(parentColumn = "id", entityColumn = "activity_id")
    val irrigation: IrrigationDetailEntity? = null,
    @Relation(parentColumn = "id", entityColumn = "activity_id")
    val irrigationPrice: IrrigationPriceSnapshotEntity? = null,
    @Relation(parentColumn = "id", entityColumn = "activity_id")
    val maintenance: MaintenanceDetailEntity? = null,
    @Relation(parentColumn = "id", entityColumn = "activity_id")
    val incident: IncidentDetailEntity? = null,
)

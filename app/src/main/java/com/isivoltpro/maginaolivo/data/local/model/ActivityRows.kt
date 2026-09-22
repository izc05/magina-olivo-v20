package com.isivoltpro.maginaolivo.data.local.model

import androidx.room.Embedded
import androidx.room.Relation
import com.isivoltpro.maginaolivo.data.local.entity.ActivityEntity
import com.isivoltpro.maginaolivo.data.local.entity.ActivityParcelTargetEntity

data class ActivityWithTargets(
    @Embedded val activity: ActivityEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "activity_id",
    )
    val targets: List<ActivityParcelTargetEntity>,
)

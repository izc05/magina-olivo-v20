package com.isivoltpro.maginaolivo.feature.activities

import androidx.compose.ui.graphics.vector.ImageVector
import com.isivoltpro.maginaolivo.domain.activity.ActivityType
import com.isivoltpro.maginaolivo.ui.components.MoIcons

/** One icon per kind of work, shared by every list that shows Activities. */
internal fun ActivityType.icon(): ImageVector = when (this) {
    ActivityType.PRUNING -> MoIcons.Shears
    ActivityType.SOIL_WORK -> MoIcons.Tractor
    ActivityType.FERTILIZATION -> MoIcons.Sack
    ActivityType.PHYTOSANITARY -> MoIcons.Spray
    ActivityType.IRRIGATION -> MoIcons.Drop
    ActivityType.MAINTENANCE -> MoIcons.Wrench
    ActivityType.INCIDENT -> MoIcons.Warning
    ActivityType.HARVEST_DAY -> MoIcons.Harvest
    ActivityType.OBSERVATION -> MoIcons.Leaf
    ActivityType.OTHER -> MoIcons.Activity
}

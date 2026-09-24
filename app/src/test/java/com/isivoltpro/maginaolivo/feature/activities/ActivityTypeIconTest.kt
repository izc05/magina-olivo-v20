package com.isivoltpro.maginaolivo.feature.activities

import com.isivoltpro.maginaolivo.domain.activity.ActivityType
import com.isivoltpro.maginaolivo.ui.components.MoIconTone
import com.isivoltpro.maginaolivo.ui.components.MoIcons
import org.junit.Assert.assertEquals
import org.junit.Test

class ActivityTypeIconTest {

    @Test
    fun everyKindOfWorkHasItsOwnIcon() {
        val icons = ActivityType.entries.map { it.icon().name }
        assertEquals("each ActivityType must be told apart by its icon", icons.size, icons.toSet().size)
    }

    @Test
    fun iconFamiliesFollowTheAddendum() {
        assertEquals(MoIconTone.WATER, MoIconTone.of(ActivityType.IRRIGATION.icon()))
        assertEquals(MoIconTone.VALUE, MoIconTone.of(ActivityType.HARVEST_DAY.icon()))
        assertEquals(MoIconTone.ALERT, MoIconTone.of(ActivityType.INCIDENT.icon()))
        assertEquals(MoIconTone.GROVE, MoIconTone.of(ActivityType.PRUNING.icon()))
        assertEquals(MoIconTone.LAND, MoIconTone.of(MoIcons.Tractor))
        assertEquals(MoIconTone.VALUE, MoIconTone.of(MoIcons.Euro))
        assertEquals(MoIconTone.GROVE, MoIconTone.of(MoIcons.ChevronRight))
    }
}

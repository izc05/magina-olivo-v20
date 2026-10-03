package com.isivoltpro.maginaolivo.feature.activities

import com.isivoltpro.maginaolivo.domain.activity.ActivityType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** #378: Trabajo is the labour done; a recogida day is planned in Avisos, not recorded as work. */
class WorkTypesTest {
    @Test fun trabajoDoesNotOfferTheRecogidaDay() {
        val offered = workTypes(planning = false)
        assertFalse(ActivityType.HARVEST_DAY in offered)
        assertEquals(
            listOf(
                ActivityType.OBSERVATION, ActivityType.PRUNING, ActivityType.SOIL_WORK, ActivityType.FERTILIZATION,
                ActivityType.PHYTOSANITARY, ActivityType.IRRIGATION, ActivityType.MAINTENANCE, ActivityType.INCIDENT,
                ActivityType.OTHER,
            ).toSet(),
            offered.toSet(),
        )
    }

    @Test fun planningInAvisosStillOffersIt() {
        assertTrue(ActivityType.HARVEST_DAY in workTypes(planning = true))
    }
}

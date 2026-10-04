package com.isivoltpro.maginaolivo.feature.activities

import com.isivoltpro.maginaolivo.domain.activity.ActivityType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** #378/#410: Trabajo is general work; Riego/Tratamiento have their own Cuaderno paths. */
class WorkTypesTest {
    @Test fun trabajoDoesNotDuplicateSpecializedPaths() {
        val offered = workTypes(planning = false)
        assertFalse(ActivityType.HARVEST_DAY in offered)
        assertFalse(ActivityType.IRRIGATION in offered)
        assertFalse(ActivityType.PHYTOSANITARY in offered)
        assertEquals(
            listOf(
                ActivityType.OBSERVATION,
                ActivityType.PRUNING,
                ActivityType.SOIL_WORK,
                ActivityType.FERTILIZATION,
                ActivityType.MAINTENANCE,
                ActivityType.INCIDENT,
                ActivityType.OTHER,
            ).toSet(),
            offered.toSet(),
        )
    }

    @Test fun planningInAvisosStillOffersTheCompleteCatalogue() {
        assertTrue(ActivityType.HARVEST_DAY in workTypes(planning = true))
        assertTrue(ActivityType.IRRIGATION in workTypes(planning = true))
        assertTrue(ActivityType.PHYTOSANITARY in workTypes(planning = true))
    }
}

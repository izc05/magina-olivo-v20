package com.isivoltpro.maginaolivo.feature.maps

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CurrentLocationPolicyTest {
    @Test fun recentPreciseFixCanDriveNearbyParcelSearch() {
        val quality = assessLocationQuality(ageMillis = 15_000, accuracyMeters = 12f)
        assertTrue(quality.accepted)
        assertFalse(quality.approximate)
    }

    @Test fun staleFixIsNeverPresentedAsCurrent() {
        val quality = assessLocationQuality(ageMillis = MAX_LOCATION_AGE_MS + 1, accuracyMeters = 10f)
        assertFalse(quality.accepted)
    }

    @Test fun veryPoorAccuracyIsRejectedEvenWhenFresh() {
        val quality = assessLocationQuality(ageMillis = 5_000, accuracyMeters = MAX_USEFUL_ACCURACY_METERS + 1f)
        assertFalse(quality.accepted)
    }

    @Test fun recentCoarseFixCanCentreButIsMarkedApproximate() {
        val quality = assessLocationQuality(ageMillis = 20_000, accuracyMeters = 120f)
        assertTrue(quality.accepted)
        assertTrue(quality.approximate)
    }

    @Test fun missingAccuracyNeverPretendsToBePrecise() {
        val quality = assessLocationQuality(ageMillis = 10_000, accuracyMeters = null)
        assertTrue(quality.accepted)
        assertTrue(quality.approximate)
    }
}

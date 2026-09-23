package com.isivoltpro.maginaolivo.feature.home

import com.isivoltpro.maginaolivo.domain.farm.Farm
import java.time.LocalTime
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeUiStateTest {

    private val workspace = UUID.randomUUID()

    private fun farm(parcels: Long, areaM2: Double?) =
        Farm(UUID.randomUUID(), workspace, "Finca", null, null, null, null, null, parcels, areaM2, null, null, 1)

    @Test
    fun summaryAddsOnlyKnownAreas() {
        val state = HomeUiState(isLoading = false, farms = listOf(farm(2, 52_000.0), farm(1, null)))

        assertEquals(3L, state.parcelCount)
        assertEquals(52_000.0, state.knownAreaM2!!, 0.0)
    }

    @Test
    fun areaIsUnknownWhenNoFarmHasOne() {
        assertNull(HomeUiState(isLoading = false, farms = listOf(farm(1, null))).knownAreaM2)
        assertNull(HomeUiState(isLoading = false).knownAreaM2)
    }

    @Test
    fun greetingFollowsTheHourOfTheDay() {
        assertEquals("Buenas noches", greeting(LocalTime.of(5, 59)))
        assertEquals("Buenos días", greeting(LocalTime.of(6, 0)))
        assertEquals("Buenos días", greeting(LocalTime.of(13, 59)))
        assertEquals("Buenas tardes", greeting(LocalTime.of(14, 0)))
        assertEquals("Buenas tardes", greeting(LocalTime.of(20, 59)))
        assertEquals("Buenas noches", greeting(LocalTime.of(21, 0)))
    }
}

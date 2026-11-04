package com.isivoltpro.maginaolivo.domain.weather

import com.isivoltpro.maginaolivo.domain.farm.Farm
import com.isivoltpro.maginaolivo.domain.feed.FeedLocation
import com.isivoltpro.maginaolivo.domain.feed.SpanishMunicipalityCatalog
import com.isivoltpro.maginaolivo.domain.profile.ProfileSettings
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherLocationResolverTest {

    private val workspace = UUID.randomUUID()
    private val user = UUID.randomUUID()

    private fun farm(municipality: String, province: String = "Jaén") = Farm(
        id = UUID.randomUUID(),
        workspaceId = workspace,
        name = "Finca $municipality",
        description = null,
        municipality = municipality,
        province = province,
        notes = null,
        coverDocumentId = null,
        parcelCount = 0L,
        totalAreaM2 = null,
        activeCampaignName = null,
        archivedAt = null,
        version = 1L,
    )

    @Test
    fun explicitPreferenceTakesHighestPriorityOverFarmsAndProfile() {
        val farms = listOf(farm("Bedmar"), farm("Bedmar"))
        val explicitPref = FeedLocation("Úbeda", "Jaén", "23092")
        val legacyProfile = ProfileSettings("Jódar", "Jaén")

        val result = WeatherLocationResolver.resolve(
            farms = farms,
            explicitPreference = explicitPref,
            legacyProfileSettings = legacyProfile,
            identity = WeatherPreferenceIdentity.Authenticated(user),
        )

        assertEquals("23092", result.location?.ineCode)
        assertEquals("Úbeda", result.location?.municipality)
        assertTrue(result.hasExplicitPreference)
        assertFalse(result.ambiguous)
        assertEquals("Bedmar", result.farmSuggestion?.municipality)
    }

    @Test
    fun unambiguousFarmMunicipalityIsUsedWhenNoExplicitPreferenceExists() {
        val farms = listOf(farm("Bedmar"), farm("  bedmar  "))
        val legacyProfile = ProfileSettings("Úbeda", "Jaén")

        val result = WeatherLocationResolver.resolve(
            farms = farms,
            explicitPreference = null,
            legacyProfileSettings = legacyProfile,
            identity = WeatherPreferenceIdentity.Authenticated(user),
        )

        assertEquals("Bedmar", result.location?.municipality)
        assertEquals("23902", result.location?.ineCode)
        assertFalse(result.hasExplicitPreference)
        assertFalse(result.ambiguous)
        assertEquals("Bedmar", result.farmSuggestion?.municipality)
    }

    @Test
    fun ambiguousFarmsYieldNullLocationWithAmbiguityFlag() {
        val farms = listOf(farm("Bedmar"), farm("Jódar"))

        val result = WeatherLocationResolver.resolve(
            farms = farms,
            explicitPreference = null,
            legacyProfileSettings = ProfileSettings(),
            identity = WeatherPreferenceIdentity.Authenticated(user),
        )

        assertNull(result.location)
        assertNull(result.farmSuggestion)
        assertFalse(result.hasExplicitPreference)
        assertTrue(result.ambiguous)
    }

    @Test
    fun legacyProfileFallbackAppliesOnlyInLocalMode() {
        val farms = listOf(farm("Bedmar"), farm("Jódar"))
        val legacyProfile = ProfileSettings("Úbeda", "Jaén")

        // In Authenticated mode, legacy profile is NOT used
        val authResult = WeatherLocationResolver.resolve(
            farms = farms,
            explicitPreference = null,
            legacyProfileSettings = legacyProfile,
            identity = WeatherPreferenceIdentity.Authenticated(user),
        )
        assertNull(authResult.location)

        // In Local mode, legacy profile is used as fallback
        val localResult = WeatherLocationResolver.resolve(
            farms = farms,
            explicitPreference = null,
            legacyProfileSettings = legacyProfile,
            identity = WeatherPreferenceIdentity.Local,
        )
        assertEquals("Úbeda", localResult.location?.municipality)
        assertEquals("23092", localResult.location?.ineCode)
    }
}


package com.isivoltpro.maginaolivo.domain.weather

import com.isivoltpro.maginaolivo.domain.farm.Farm
import com.isivoltpro.maginaolivo.domain.feed.FeedLocation
import com.isivoltpro.maginaolivo.domain.feed.SpanishMunicipalityCatalog
import com.isivoltpro.maginaolivo.domain.profile.ProfileSettings

/**
 * Resolved weather location for Inicio and El tiempo (National Weather Selector 1.0).
 *
 * Priority chain:
 * 1. Explicit personal weather preference (`explicitPreference`) from [WeatherLocationPreferenceStore]
 *    for the verified user identity and active workspace.
 * 2. Unambiguous farm municipality (`farmLocation`), enriched with its 5-digit INE code when matched
 *    in [SpanishMunicipalityCatalog].
 * 3. Legacy `profile_settings` municipality (`legacyProfileSettings.location`), ONLY when in
 *    [WeatherPreferenceIdentity.Local] mode (never copied or applied to an authenticated account
 *    or after logout) and only when it resolves in [SpanishMunicipalityCatalog] or is non-blank.
 * 4. Otherwise `null` (`FeedState.NoLocation`).
 */
data class ResolvedWeatherPlace(
    val location: FeedLocation?,
    val farmSuggestion: FeedLocation?,
    val hasExplicitPreference: Boolean,
    val ambiguous: Boolean,
)

object WeatherLocationResolver {

    fun resolve(
        farms: List<Farm>,
        explicitPreference: FeedLocation?,
        legacyProfileSettings: ProfileSettings = ProfileSettings(),
        identity: WeatherPreferenceIdentity = WeatherPreferenceIdentity.Local,
    ): ResolvedWeatherPlace {
        val farmPlaces = farms.mapNotNull { farm ->
            farm.municipality?.trim()?.takeIf(String::isNotEmpty)?.let { FeedLocation(it, farm.province) }
        }
        val multipleFarmPlaces = farmPlaces.map(FeedLocation::key).distinct().size > 1
        val commonFarmPlace = FeedLocation.common(farms.map { it.municipality to it.province })
            ?.withValidatedIneCode()

        // Priority 1: Explicit personal weather preference in WeatherLocationPreferenceStore
        if (explicitPreference != null) {
            return ResolvedWeatherPlace(
                location = explicitPreference.withValidatedIneCode(),
                farmSuggestion = commonFarmPlace,
                hasExplicitPreference = true,
                ambiguous = false,
            )
        }

        // Priority 2: Unambiguous farm location
        if (commonFarmPlace != null) {
            return ResolvedWeatherPlace(
                location = commonFarmPlace,
                farmSuggestion = commonFarmPlace,
                hasExplicitPreference = false,
                ambiguous = false,
            )
        }

        // Priority 3: Legacy profile_settings fallback ONLY in Local/unconfigured mode
        val legacyPlace = if (identity == WeatherPreferenceIdentity.Local) {
            legacyProfileSettings.location?.let { raw ->
                val resolved = SpanishMunicipalityCatalog.resolve(raw.municipality, raw.province)
                if (resolved != null) {
                    raw.copy(
                        province = raw.province ?: resolved.provinceName,
                        ineCode = resolved.ineCode,
                    )
                } else {
                    raw
                }
            }
        } else {
            null
        }

        if (legacyPlace != null) {
            return ResolvedWeatherPlace(
                location = legacyPlace,
                farmSuggestion = null,
                hasExplicitPreference = false,
                ambiguous = false,
            )
        }

        // Priority 4: No location available
        return ResolvedWeatherPlace(
            location = null,
            farmSuggestion = null,
            hasExplicitPreference = false,
            ambiguous = multipleFarmPlaces,
        )
    }
}


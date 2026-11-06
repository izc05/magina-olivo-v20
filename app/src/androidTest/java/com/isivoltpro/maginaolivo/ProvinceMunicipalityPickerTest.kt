package com.isivoltpro.maginaolivo

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.isivoltpro.maginaolivo.domain.feed.IneMunicipality
import com.isivoltpro.maginaolivo.domain.feed.FeedLocation
import com.isivoltpro.maginaolivo.domain.feed.SpanishMunicipalityCatalog
import com.isivoltpro.maginaolivo.feature.home.WeatherLocationSelectorContent
import com.isivoltpro.maginaolivo.ui.components.ProvinceMunicipalityPicker
import com.isivoltpro.maginaolivo.ui.theme.MaginaOlivoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ProvinceMunicipalityPickerTest {
    @get:Rule val compose = createComposeRule()

    @Test fun provinceFiltersMunicipalitiesAndChangingItClearsSelectionOffline() {
        var saved: IneMunicipality? = null
        compose.setContent {
            var province by remember { mutableStateOf<String?>(null) }
            var municipality by remember { mutableStateOf<IneMunicipality?>(null) }
            MaginaOlivoTheme {
                ProvinceMunicipalityPicker(province, municipality,
                    onProvinceSelected = { province = it; municipality = null },
                    onMunicipalitySelected = { municipality = it; saved = it },
                    tagPrefix = "test")
            }
        }
        compose.onNodeWithTag("test-municipality").assertIsNotEnabled()
        compose.onNodeWithTag("test-province").performClick()
        compose.onNodeWithTag("test-search").performTextInput("Jaen")
        compose.onNodeWithTag("test-province-23").performClick()
        compose.onNodeWithTag("test-municipality").assertIsEnabled().performClick()
        compose.onNodeWithTag("test-search").performTextInput("Huelma")
        compose.onNodeWithTag("test-municipality-23044").performClick()
        compose.waitForIdle(); captureReviewScreenshot("selector-municipio")
        assertEquals("Huelma", saved?.displayName)
        assertEquals("Jaén", saved?.provinceName)
        compose.onNodeWithTag("test-province").performClick()
        compose.onNodeWithTag("test-search").performTextInput("Granada")
        compose.onNodeWithTag("test-province-18").performClick()
        compose.onNodeWithTag("test-municipality").assertTextContains("Elegir municipio")
        compose.onNodeWithTag("test-municipality").performClick()
        compose.onNodeWithTag("test-search").performTextInput("Huelma")
        compose.onNodeWithTag("test-municipality-23044").assertDoesNotExist()
        assertEquals(SpanishMunicipalityCatalog.resolveProvince("Granada")?.code, "18")
    }

    @Test fun savedWeatherMunicipalityReopensSelectedAndCanBeSavedAgain() {
        var saved: IneMunicipality? = null
        compose.setContent {
            MaginaOlivoTheme {
                WeatherLocationSelectorContent(
                    currentLocation = FeedLocation("Huelma", "Jaén", "23044"),
                    onSelectMunicipality = { saved = it }, onClearLocation = {}, onCancel = {},
                )
            }
        }
        compose.onNodeWithTag("weather-province").assertTextContains("Jaén", substring = true)
        compose.onNodeWithTag("weather-municipality").assertTextContains("Huelma", substring = true)
        compose.onNodeWithTag("weather-save-location").performClick()
        assertEquals("23044", saved?.ineCode)
    }
}

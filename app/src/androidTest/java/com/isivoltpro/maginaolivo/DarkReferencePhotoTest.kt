package com.isivoltpro.maginaolivo

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import com.isivoltpro.maginaolivo.app.AppearanceMode
import com.isivoltpro.maginaolivo.ui.reference.home.HomeReferenceScreen
import com.isivoltpro.maginaolivo.ui.theme.MaginaOlivoTheme
import kotlin.math.abs
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class DarkReferencePhotoTest {
    @get:Rule val compose = createComposeRule()

    @Test fun referencePhotoAndItsShadeRemainUnchangedAcrossThemes() {
        var mode by mutableStateOf(AppearanceMode.LIGHT)
        compose.setContent { MaginaOlivoTheme(mode) { HomeReferenceScreen() } }
        val photo = compose.onNodeWithTag("home-reference-territory-hero").performScrollTo()
        val light = photo.captureToImage().asAndroidBitmap()
        compose.runOnIdle { mode = AppearanceMode.DARK }
        compose.waitForIdle()
        val dark = photo.captureToImage().asAndroidBitmap()
        // Sample the lower photo where its readability shade is strongest, away from labels.
        val x = light.width * 3 / 4
        val y = light.height * 3 / 4
        assertTrue("Photo shade must stay fixed, preserving source colors and white-label contrast",
            (0 until 16).all { dx -> (0 until 16).all { dy ->
                val a = light.getPixel(x + dx, y + dy)
                val b = dark.getPixel(x + dx, y + dy)
                listOf(0, 8, 16, 24).all { shift ->
                    abs(((a ushr shift) and 255) - ((b ushr shift) and 255)) <= 1
                }
            } })
    }
}

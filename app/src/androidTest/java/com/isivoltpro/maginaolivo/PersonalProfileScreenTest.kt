package com.isivoltpro.maginaolivo

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.isivoltpro.maginaolivo.feature.profile.PersonalProfile
import com.isivoltpro.maginaolivo.feature.profile.ProfileScreen
import com.isivoltpro.maginaolivo.ui.theme.MaginaOlivoTheme
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Validacin de Perfil Personal en entorno Android:
 * - Modo invitado (sin sesin).
 * - Sesin activa con avatar de iniciales, nombre y correo.
 * - Acceso a Cuenta.
 * - Cierre de sesin (eliminacin reactiva de identidad).
 * - Cambio de cuenta A ? B.
 */
class PersonalProfileScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun profileWithoutSessionDisplaysGuestHeader() {
        val accountClicks = AtomicInteger()
        composeRule.setContent {
            MaginaOlivoTheme {
                ProfileScreen(
                    appVersion = "1.0.0-TEST",
                    notificationsOn = true,
                    onNotifications = {},
                    onMachinery = {},
                    onAccount = { accountClicks.incrementAndGet() },
                    personalProfile = null,
                )
            }
        }

        composeRule.onNodeWithTag("profile-personal-header").assertIsDisplayed()
        composeRule.onNodeWithTag("profile-avatar-guest").assertIsDisplayed()
        composeRule.onNodeWithTag("profile-display-name").assertTextContains("Perfil local", substring = true)
        composeRule.onNodeWithTag("profile-header-account-button").assertTextContains("Iniciar sesión", substring = true)

        composeRule.onNodeWithTag("profile-header-account-button").performClick()
        assertEquals(1, accountClicks.get())
    }

    @Test
    fun profileWithSessionDisplaysInitialsAndEmail() {
        val user = UUID.randomUUID()
        val profile = PersonalProfile(
            userId = user,
            email = "isicio@example.com",
            displayName = "Isicio Zafra",
        )

        val accountClicks = AtomicInteger()
        composeRule.setContent {
            MaginaOlivoTheme {
                ProfileScreen(
                    appVersion = "1.0.0-TEST",
                    notificationsOn = true,
                    onNotifications = {},
                    onMachinery = {},
                    onAccount = { accountClicks.incrementAndGet() },
                    personalProfile = profile,
                )
            }
        }

        composeRule.onNodeWithTag("profile-personal-header").assertIsDisplayed()
        composeRule.onNodeWithTag("profile-avatar-initials").assertIsDisplayed()
        composeRule.onNodeWithTag("profile-avatar-text").assertTextContains("IZ")
        composeRule.onNodeWithTag("profile-display-name").assertTextContains("Isicio Zafra")
        composeRule.onNodeWithTag("profile-email").assertTextContains("isicio@example.com")
        composeRule.onNodeWithTag("profile-header-account-button").assertTextContains("Cuenta", substring = true)

        composeRule.onNodeWithTag("profile-header-account-button").performClick()
        assertEquals(1, accountClicks.get())
    }

    @Test
    fun sessionSignOutClearsIdentityImmediately() {
        var profileState by mutableStateOf<PersonalProfile?>(
            PersonalProfile(
                userId = UUID.randomUUID(),
                email = "active.user@example.com",
                displayName = "Usuario Activo",
            )
        )

        composeRule.setContent {
            MaginaOlivoTheme {
                ProfileScreen(
                    appVersion = "1.0.0-TEST",
                    notificationsOn = true,
                    onNotifications = {},
                    onMachinery = {},
                    onAccount = {},
                    personalProfile = profileState,
                )
            }
        }

        composeRule.onNodeWithTag("profile-avatar-initials").assertIsDisplayed()
        composeRule.onNodeWithTag("profile-avatar-text").assertTextContains("UA")
        composeRule.onNodeWithTag("profile-email").assertTextContains("active.user@example.com")

        // Simular logout
        composeRule.runOnUiThread {
            profileState = null
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("profile-avatar-initials").assertDoesNotExist()
        composeRule.onNodeWithTag("profile-avatar-guest").assertIsDisplayed()
        composeRule.onNodeWithTag("profile-display-name").assertTextContains("Perfil local")
        composeRule.onNodeWithTag("profile-email").assertDoesNotExist()
    }

    @Test
    fun accountSwitchFromAToBUpdatesIdentity() {
        val userA = UUID.randomUUID()
        val userB = UUID.randomUUID()
        var currentProfile by mutableStateOf(
            PersonalProfile(
                userId = userA,
                email = "userA@example.com",
                displayName = "Isicio Zafra",
            )
        )

        composeRule.setContent {
            MaginaOlivoTheme {
                ProfileScreen(
                    appVersion = "1.0.0-TEST",
                    notificationsOn = true,
                    onNotifications = {},
                    onMachinery = {},
                    onAccount = {},
                    personalProfile = currentProfile,
                )
            }
        }

        composeRule.onNodeWithTag("profile-avatar-text").assertTextContains("IZ")
        composeRule.onNodeWithTag("profile-email").assertTextContains("userA@example.com")

        // Cambiar a Usuario B
        composeRule.runOnUiThread {
            currentProfile = PersonalProfile(
                userId = userB,
                email = "userB@example.com",
                displayName = "María Ángeles",
            )
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("profile-avatar-text").assertTextContains("MÁ")
        composeRule.onNodeWithTag("profile-display-name").assertTextContains("María Ángeles")
        composeRule.onNodeWithTag("profile-email").assertTextContains("userB@example.com")
        composeRule.onNodeWithText("userA@example.com").assertDoesNotExist()
    }

    @Test
    fun profileWithAvatarImageDisplaysImageAndNotInitials() {
        val user = UUID.randomUUID()
        val tempImageFile = java.io.File.createTempFile("avatar_test_", ".jpg")
        try {
            val bitmap = android.graphics.Bitmap.createBitmap(50, 50, android.graphics.Bitmap.Config.ARGB_8888)
            java.io.FileOutputStream(tempImageFile).use { out ->
                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out)
            }
            bitmap.recycle()

            val profileWithPhoto = PersonalProfile(
                userId = user,
                email = "foto.usuario@example.com",
                displayName = "Foto Usuario",
                avatarFile = tempImageFile,
            )

            composeRule.setContent {
                MaginaOlivoTheme {
                    ProfileScreen(
                        appVersion = "1.0.0-TEST",
                        notificationsOn = true,
                        onNotifications = {},
                        onMachinery = {},
                        onAccount = {},
                        personalProfile = profileWithPhoto,
                    )
                }
            }

            composeRule.onNodeWithTag("profile-avatar-image").assertIsDisplayed()
            composeRule.onNodeWithTag("profile-avatar-initials").assertDoesNotExist()
            composeRule.onNodeWithTag("profile-display-name").assertTextContains("Foto Usuario")
        } finally {
            tempImageFile.delete()
        }
    }
}


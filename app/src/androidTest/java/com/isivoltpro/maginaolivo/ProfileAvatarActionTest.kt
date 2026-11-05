package com.isivoltpro.maginaolivo

import android.graphics.Bitmap
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.platform.LocalContext
import com.isivoltpro.maginaolivo.feature.account.AccountRepository
import com.isivoltpro.maginaolivo.feature.account.MemoryAccountSessionStore
import com.isivoltpro.maginaolivo.feature.account.SimulatedAccountAuthApi
import com.isivoltpro.maginaolivo.feature.profile.ProfileAvatarAction
import com.isivoltpro.maginaolivo.feature.profile.ProfileAvatarStore
import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.ui.theme.MaginaOlivoTheme
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test

class ProfileAvatarActionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun topAvatarRefreshesAfterPhotoChangeAndDoesNotShowAnotherAccountsPhoto() = runBlocking {
        var uiContext: android.content.Context? = null
        val repository = AccountRepository(SimulatedAccountAuthApi(), MemoryAccountSessionStore())
        val suffix = java.util.UUID.randomUUID().toString().take(8)
        repository.login("farmer-$suffix@example.test", "password42", true)
        val userA = repository.state.value.userId!!
        compose.setContent { uiContext = LocalContext.current; MaginaOlivoTheme { ProfileAvatarAction(repository, {}) } }
        compose.waitForIdle()
        val store = ProfileAvatarStore.fromContext(checkNotNull(uiContext))
        compose.onNodeWithText("F").assertExists()
        val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        val bytes = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()
        bitmap.recycle()
        try {
            org.junit.Assert.assertTrue(store.saveAvatar(userA, ByteArrayInputStream(bytes)) is AppResult.Success)
            compose.waitUntil(5_000) { compose.onAllNodesWithText("F").fetchSemanticsNodes().isEmpty() }
            repository.logout(false)
            repository.login("other-$suffix@example.test", "password42", true)
            compose.onNodeWithText("O").assertExists()
            compose.onNodeWithText("F").assertDoesNotExist()
        } finally {
            store.deleteAvatar(userA)
        }
    }
}

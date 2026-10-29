package com.isivoltpro.maginaolivo

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import com.isivoltpro.maginaolivo.core.common.AppError
import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.feature.profile.ProfileAvatarStore
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Validacin de almacenamiento y procesamiento real de fotos de perfil en entorno Android:
 * - Rechazo por tamao excesivo (> 5 MiB).
 * - Rechazo de archivos corruptos o no-imagen.
 * - Guardado, decodificacin y escalado de bitmap real.
 * - Aislamiento estricto por cuenta (UUID).
 * - Eliminacin durable sin dejar residuos hurfanos.
 */
class ProfileAvatarStoreInstrumentedTest {

    private lateinit var targetDir: File
    private lateinit var store: ProfileAvatarStore

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        targetDir = File(context.cacheDir, "avatar_test_${UUID.randomUUID()}")
        targetDir.mkdirs()
        store = ProfileAvatarStore(avatarDirectory = targetDir)
    }

    @After
    fun tearDown() {
        targetDir.deleteRecursively()
    }

    @Test
    fun rejectsFileExceedingFiveMegabytes() {
        val user = UUID.randomUUID()
        val oversizedBytes = ByteArray(ProfileAvatarStore.MAX_INPUT_BYTES + 1) { 1 }

        val result = store.saveAvatar(user, ByteArrayInputStream(oversizedBytes))

        assertTrue(result is AppResult.Failure)
        val error = (result as AppResult.Failure).error
        assertTrue(error is AppError.Validation)
        assertEquals("FILE_TOO_LARGE", (error as AppError.Validation).code)
        assertNull(store.getAvatarFile(user))
    }

    @Test
    fun rejectsInvalidNonImageBytes() {
        val user = UUID.randomUUID()
        val invalidBytes = "este no es un archivo de imagen valido".toByteArray()

        val result = store.saveAvatar(user, ByteArrayInputStream(invalidBytes))

        assertTrue(result is AppResult.Failure)
        val error = (result as AppResult.Failure).error
        assertTrue(error is AppError.Validation)
        assertEquals("INVALID_IMAGE", (error as AppError.Validation).code)
        assertNull(store.getAvatarFile(user))
    }

    @Test
    fun savesValidImageAndEnforcesUserIsolation() {
        val userA = UUID.randomUUID()
        val userB = UUID.randomUUID()

        val imageStreamA = createSyntheticJpegStream(width = 120, height = 120)
        val resultA = store.saveAvatar(userA, imageStreamA)

        assertTrue("Guardado de avatar A debe ser exitoso", resultA is AppResult.Success)
        val fileA = store.getAvatarFile(userA)
        assertTrue(fileA != null && fileA.exists() && fileA.length() > 0)

        // Usuario B no tiene avatar asignado
        assertNull(store.getAvatarFile(userB))

        // Eliminar avatar de A
        val deleteResult = store.deleteAvatar(userA)
        assertTrue(deleteResult is AppResult.Success)
        assertNull(store.getAvatarFile(userA))
    }

    @Test
    fun removesAvatarLeavesNoOrphanedFiles() {
        val user = UUID.randomUUID()
        val imageStream = createSyntheticJpegStream(width = 60, height = 60)
        store.saveAvatar(user, imageStream)

        assertTrue(store.hasAvatar(user))
        store.deleteAvatar(user)

        assertFalse(store.hasAvatar(user))
        assertEquals(0, targetDir.listFiles()?.size ?: 0)
    }

    private fun createSyntheticJpegStream(width: Int, height: Int): ByteArrayInputStream {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
        bitmap.recycle()
        return ByteArrayInputStream(stream.toByteArray())
    }
}


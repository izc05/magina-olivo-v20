package com.isivoltpro.maginaolivo.feature.profile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.isivoltpro.maginaolivo.core.common.AppError
import com.isivoltpro.maginaolivo.core.common.AppResult
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID

/**
 * Almacenamiento local seguro de fotografas de perfil de usuario.
 * Garantiza:
 * - Aislamiento estricto por cuenta (UUID).
 * - Lmite mximo de entrada de 5 MiB.
 * - Normalizacin y escalado mximo a 1024 px sin desbordar memoria.
 * - Eliminacin completa de metadatos EXIF de ubicacin mediante recompresin.
 * - Escritura atmica sin corrupcin de archivos.
 */
class ProfileAvatarStore(
    private val avatarDirectory: File,
) {
    companion object {
        const val MAX_INPUT_BYTES: Int = 5 * 1024 * 1024 // 5 MiB
        const val MAX_DIMENSION: Int = 1024 // 1024 px
        private const val JPEG_QUALITY: Int = 85

        fun fromContext(context: Context): ProfileAvatarStore {
            val dir = File(context.filesDir, "avatars").apply { if (!exists()) mkdirs() }
            return ProfileAvatarStore(dir)
        }
    }

    init {
        if (!avatarDirectory.exists()) {
            avatarDirectory.mkdirs()
        }
    }

    /**
     * Retorna el archivo de imagen de avatar para un usuario si existe y no est vaco.
     */
    fun getAvatarFile(userId: UUID): File? {
        val file = File(avatarDirectory, "$userId.jpg")
        return if (file.exists() && file.length() > 0) file else null
    }

    /**
     * Indica si existe avatar local para el usuario.
     */
    fun hasAvatar(userId: UUID): Boolean = getAvatarFile(userId) != null

    /**
     * Procesa, valida y guarda la fotografa de forma atmica.
     */
    fun saveAvatar(userId: UUID, inputStream: InputStream): AppResult<File> {
        val bytes = try {
            readBytesBounded(inputStream, MAX_INPUT_BYTES)
        } catch (e: IllegalArgumentException) {
            return AppResult.Failure(
                AppError.Validation(
                    field = "avatar",
                    code = "FILE_TOO_LARGE",
                    cause = e,
                )
            )
        } catch (e: Exception) {
            return AppResult.Failure(AppError.Storage(operation = "read_avatar_stream", cause = e))
        }

        if (bytes.isEmpty()) {
            return AppResult.Failure(
                AppError.Validation(
                    field = "avatar",
                    code = "INVALID_IMAGE",
                )
            )
        }

        // 1. Decodificar lmites
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        if (options.outWidth <= 0 || options.outHeight <= 0) {
            return AppResult.Failure(
                AppError.Validation(
                    field = "avatar",
                    code = "INVALID_IMAGE",
                )
            )
        }

        // 2. Calcular sample size para evitar OOM
        options.inSampleSize = calculateInSampleSize(options.outWidth, options.outHeight, MAX_DIMENSION, MAX_DIMENSION)
        options.inJustDecodeBounds = false

        val decodedBitmap = try {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        } catch (e: OutOfMemoryError) {
            return AppResult.Failure(AppError.Storage(operation = "decode_avatar_oom", cause = e))
        } catch (e: Exception) {
            return AppResult.Failure(AppError.Storage(operation = "decode_avatar", cause = e))
        }

        if (decodedBitmap == null) {
            return AppResult.Failure(
                AppError.Validation(
                    field = "avatar",
                    code = "INVALID_IMAGE",
                )
            )
        }

        // 3. Escalar proporcionalmente a MAX_DIMENSION si es mayor
        val finalBitmap = scaleIfNeeded(decodedBitmap, MAX_DIMENSION)

        // 4. Escribir a archivo temporal y renombrar (atmico)
        val tempFile = File(avatarDirectory, "tmp_${UUID.randomUUID()}.jpg")
        val targetFile = File(avatarDirectory, "$userId.jpg")

        return try {
            FileOutputStream(tempFile).use { out ->
                finalBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
                out.flush()
            }

            if (targetFile.exists()) {
                targetFile.delete()
            }

            if (tempFile.renameTo(targetFile)) {
                AppResult.Success(targetFile)
            } else {
                // Fallback por si renameTo falla en algn FS
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
                AppResult.Success(targetFile)
            }
        } catch (e: Exception) {
            if (tempFile.exists()) tempFile.delete()
            AppResult.Failure(AppError.Storage(operation = "write_avatar_file", cause = e))
        } finally {
            if (finalBitmap != decodedBitmap) {
                finalBitmap.recycle()
            }
            decodedBitmap.recycle()
        }
    }

    /**
     * Elimina el avatar local del usuario.
     */
    fun deleteAvatar(userId: UUID): AppResult<Unit> {
        return try {
            val file = File(avatarDirectory, "$userId.jpg")
            if (file.exists()) {
                file.delete()
            }
            AppResult.Success(Unit)
        } catch (e: Exception) {
            AppResult.Failure(AppError.Storage(operation = "delete_avatar", cause = e))
        }
    }

    private fun readBytesBounded(inputStream: InputStream, maxBytes: Int): ByteArray {
        val buffer = ByteArrayOutputStream()
        val chunk = ByteArray(8192)
        var totalRead = 0

        var bytesRead: Int
        while (inputStream.read(chunk).also { bytesRead = it } != -1) {
            totalRead += bytesRead
            if (totalRead > maxBytes) {
                throw IllegalArgumentException("Stream exceeded maximum allowed size of $maxBytes bytes")
            }
            buffer.write(chunk, 0, bytesRead)
        }
        return buffer.toByteArray()
    }

    private fun calculateInSampleSize(rawWidth: Int, rawHeight: Int, reqWidth: Int, reqHeight: Int): Int {
        var inSampleSize = 1
        if (rawHeight > reqHeight || rawWidth > reqWidth) {
            val halfHeight = rawHeight / 2
            val halfWidth = rawWidth / 2
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    private fun scaleIfNeeded(bitmap: Bitmap, maxDim: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= maxDim && height <= maxDim) return bitmap

        val ratio = width.toFloat() / height.toFloat()
        val targetWidth: Int
        val targetHeight: Int
        if (width > height) {
            targetWidth = maxDim
            targetHeight = (maxDim / ratio).toInt().coerceAtLeast(1)
        } else {
            targetHeight = maxDim
            targetWidth = (maxDim * ratio).toInt().coerceAtLeast(1)
        }

        return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
    }
}


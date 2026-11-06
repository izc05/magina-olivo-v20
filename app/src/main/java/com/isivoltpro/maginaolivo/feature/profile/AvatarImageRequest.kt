package com.isivoltpro.maginaolivo.feature.profile

import android.content.Context
import coil3.request.ImageRequest
import java.io.File

internal fun avatarImageRequest(context: Context, file: File, revision: Long): ImageRequest =
    ImageRequest.Builder(context)
        .data(file)
        .memoryCacheKey("${file.absolutePath}:$revision")
        .build()

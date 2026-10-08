package com.isivoltpro.maginaolivo.data.local

import android.content.Context
import android.content.ContextWrapper
import java.io.File

/** Keep fixture files within the existing FileProvider roots, away from app-owned files. */
internal fun attachmentFixtureContext(base: Context, fixture: String): Context =
    object : ContextWrapper(base) {
        override fun getFilesDir(): File = File(base.filesDir, "attachments/test-fixtures/$fixture")
        override fun getCacheDir(): File = File(base.cacheDir, "camera/test-fixtures/$fixture")
    }

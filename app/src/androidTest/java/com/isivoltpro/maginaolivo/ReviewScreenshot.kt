package com.isivoltpro.maginaolivo

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File

internal fun captureReviewScreenshot(name: String) {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val out = File(context.getExternalFilesDir(null), "review-1-0/$name.png")
    check(out.parentFile?.mkdirs() == true || out.parentFile?.isDirectory == true)
    val bitmap = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
    out.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
}

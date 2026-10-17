package com.isivoltpro.maginaolivo

import android.content.Context
import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/** Opt-in synthetic CI evidence. Place inside the Compose rule, before its teardown. */
class FailureEvidenceRule(private val semantics: () -> String) : TestWatcher() {
    override fun failed(error: Throwable, description: Description) {
        if (InstrumentationRegistry.getArguments().getString("qaFailureDiagnostics") != "true") return
        // Diagnostic failure must never replace the original test failure.
        runCatching { capture(error, description) }.onFailure(error::addSuppressed)
    }

    private fun capture(error: Throwable, description: Description) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        check(context.packageName == "com.isivoltpro.maginaolivo.dev") { "DEV only" }
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        fun shell(command: String): String =
            ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command))
                .bufferedReader().use { reader ->
                    // Bound host-memory consumption as well as the Android command duration.
                    val buffer = CharArray(262_144)
                    var total = 0
                    while (total < buffer.size) {
                        val count = reader.read(buffer, total, buffer.size - total)
                        if (count < 0) break
                        total += count
                    }
                    String(buffer, 0, total)
                }
        check(shell("timeout 5s getprop ro.kernel.qemu").trim() == "1") { "QEMU only" }
        val name = "${description.className}-${description.methodName}".replace(Regex("[^A-Za-z0-9_.-]"), "_").take(180)
        val folder = File(context.getExternalFilesDir(null), "failure-evidence/$name").apply { check(mkdirs() || isDirectory) }
        File(folder, "failure.txt").writeText(error.stackTraceToString())
        fun evidence(name: String, action: () -> Unit) {
            runCatching(action).onFailure { failure ->
                error.addSuppressed(failure)
                runCatching { File(folder, "$name-error.txt").writeText(failure.stackTraceToString()) }
            }
        }
        evidence("screen") {
            val bitmap = checkNotNull(automation.takeScreenshot())
            try {
                File(folder, "screen.png").outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            } finally { bitmap.recycle() }
        }
        evidence("active-window") { File(folder, "active-window.txt").writeText(automation.rootInActiveWindow?.toString() ?: "No active window") }
        for ((name, command) in listOf(
            "window" to "dumpsys window",
            "ime" to "dumpsys input_method",
            "activity" to "dumpsys activity activities",
            "logcat" to "logcat -d -v threadtime -t 400",
        )) {
            evidence(name) { File(folder, "$name.txt").writeText(shell("timeout 5s $command")) }
        }
        // Last: Compose may wait for its own synchronization timeout when UI is stuck.
        // Do not change global idling policy or leave a worker thread querying torn-down UI.
        evidence("semantics") {
            File(folder, "semantics.txt").writeText(runCatching(semantics).getOrElse { "Semantics unavailable: $it" })
        }
    }
}

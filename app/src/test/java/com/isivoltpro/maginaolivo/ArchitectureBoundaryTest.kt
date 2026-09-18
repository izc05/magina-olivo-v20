package com.isivoltpro.maginaolivo

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchitectureBoundaryTest {
    @Test
    fun phase2DoesNotPullFutureInfrastructureIntoSource() {
        val sourceRoot = sequenceOf(
            File("src/main/java"),
            File("app/src/main/java"),
        ).firstOrNull { it.exists() }

        assertTrue("Could not locate main source directory", sourceRoot != null)

        val forbidden = listOf(
            "androidx.room",
            "supabase",
            "maplibre",
            "workmanager",
        )

        val sourceText = sourceRoot!!
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .joinToString("\n") { it.readText() }
            .lowercase()

        forbidden.forEach { token ->
            assertFalse("Phase 2 source must not depend on $token", sourceText.contains(token))
        }
    }

    @Test
    fun productionApplicationIdRemainsStableInBuildScript() {
        val buildFile = sequenceOf(
            File("build.gradle.kts"),
            File("app/build.gradle.kts"),
        ).firstOrNull { it.exists() && it.readText().contains("applicationId") }

        assertTrue("Could not locate app build.gradle.kts", buildFile != null)

        val buildText = buildFile!!.readText()
        assertTrue(
            "Production applicationId contract changed",
            buildText.contains("applicationId = \"com.isivoltpro.maginaolivo\""),
        )
    }
}

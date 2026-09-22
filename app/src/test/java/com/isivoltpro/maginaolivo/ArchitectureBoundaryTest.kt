package com.isivoltpro.maginaolivo

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchitectureBoundaryTest {
    @Test
    fun futureInfrastructureRemainsBlockedUntilItsPhase() {
        val sourceRoot = sequenceOf(
            File("src/main/java"),
            File("app/src/main/java"),
        ).firstOrNull { it.exists() }

        assertTrue("Could not locate main source directory", sourceRoot != null)

        val forbidden = listOf(
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
            assertFalse("Source must not depend on $token before its phase", sourceText.contains(token))
        }
    }

    @Test
    fun uiAndNavigationDoNotAccessRoomOrDaosDirectly() {
        val sourceRoot = sequenceOf(
            File("src/main/java"),
            File("app/src/main/java"),
        ).firstOrNull { it.exists() }

        assertTrue("Could not locate main source directory", sourceRoot != null)

        val presentationFiles =
            sourceRoot!!
                .walkTopDown()
                .filter { file ->
                    file.isFile &&
                        file.extension == "kt" &&
                        ("/ui/" in file.invariantSeparatorsPath ||
                            "/navigation/" in file.invariantSeparatorsPath)
                }.toList()

        assertTrue("No UI/navigation Kotlin sources found", presentationFiles.isNotEmpty())

        val forbidden = listOf(
            "androidx.room",
            ".data.local.dao",
            "MaginaOlivoDatabase",
        )

        presentationFiles.forEach { file ->
            val text = file.readText()
            forbidden.forEach { token ->
                assertFalse("${file.name} must not access $token", text.contains(token))
            }
        }
    }

    @Test
    fun localPersistenceLayerDoesNotDependOnCompose() {
        val sourceRoot = sequenceOf(
            File("src/main/java"),
            File("app/src/main/java"),
        ).firstOrNull { it.exists() }

        assertTrue("Could not locate main source directory", sourceRoot != null)

        val localDataRoot = File(sourceRoot, "com/isivoltpro/maginaolivo/data/local")
        assertTrue("Could not locate Room persistence package", localDataRoot.exists())

        localDataRoot
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                assertFalse(
                    "${file.name} must not depend on Compose",
                    file.readText().contains("androidx.compose"),
                )
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

package com.isivoltpro.maginaolivo

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchitectureBoundaryTest {
    @Test
    fun infrastructureOutsideAuthorizedAuthRemainsBlocked() {
        val sourceRoot = sequenceOf(
            File("src/main/java"),
            File("app/src/main/java"),
        ).firstOrNull { it.exists() }

        assertTrue("Could not locate main source directory", sourceRoot != null)

        val forbidden = listOf(
            "supabase",
            "workmanager",
        )
        // CR-006 (Phase 20B): the weather Edge Function client is the one place allowed to name
        // the backend, and only over plain HTTPS. The Supabase SDK stays forbidden everywhere.
        val weatherEdgeClient = "/data/remote/weather/"
        // Owner authorization 14/10/2026 (#725): Auth-only, without agricultural sync.
        val accountAuth = "/feature/account/"
        // Owner authorization 25/10/2026 (#774): the G2.4 Delivery RPC HTTPS client, and ONLY
        // that file. Deliberately NOT the whole /data/repository/sync/ folder: every other file
        // there must stay free of the backend's name, and this one stays unwired, which
        // [theDeliveryRpcHttpsClientIsNamedButNeverWired] below enforces.
        val deliveryRpcClient = "/data/repository/sync/HttpsDeliveryRpcTransport.kt"

        val sources = sourceRoot!!
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .toList()
        val sourceText = sources
            .filterNot {
                weatherEdgeClient in it.invariantSeparatorsPath ||
                    accountAuth in it.invariantSeparatorsPath ||
                    it.invariantSeparatorsPath.endsWith(deliveryRpcClient)
            }
            .joinToString("\n") { it.readText() }
            .lowercase()

        forbidden.forEach { token ->
            assertFalse("Source must not depend on $token before its phase", sourceText.contains(token))
        }
        assertFalse("WorkManager/sync remains blocked in Auth too", sources.joinToString("\n") { it.readText() }.lowercase().contains("workmanager"))
        val sdk = listOf("io.github.jan.supabase", "supabase-kt", "gotrue", "postgrest", "realtime")
        sources.filter {
            weatherEdgeClient in it.invariantSeparatorsPath ||
                accountAuth in it.invariantSeparatorsPath ||
                it.invariantSeparatorsPath.endsWith(deliveryRpcClient)
        }.forEach { file ->
            val text = file.readText().lowercase()
            sdk.forEach { token -> assertFalse("${file.name} must stay plain HTTPS (CR-006): $token", text.contains(token)) }
        }
        sources.filter { accountAuth in it.invariantSeparatorsPath }.forEach { file ->
            val text=file.readText()
            listOf("MaginaOlivoDatabase", "LocalPersistence", ".data.local", "SyncOutbox", "/rest/v1", "/storage/v1")
                .forEach { token -> assertFalse("${file.name}: Auth must stay isolated from $token",text.contains(token)) }
        }
    }

    /**
     * The 25/10/2026 (#774) authorization lets ONE file name the backend. It does not authorize
     * sending anything. This test is what keeps those two apart: the HTTPS client may exist, but
     * nothing may construct it, so no code path can reach the agricultural RPCs. Wiring it is a
     * later, separate authorization, and that step is exactly this test turning red.
     */
    @Test
    fun theDeliveryRpcHttpsClientIsNamedButNeverWired() {
        val sourceRoot = sequenceOf(File("src/main/java"), File("app/src/main/java"))
            .firstOrNull { it.exists() }
        assertTrue("Could not locate main source directory", sourceRoot != null)

        val client = "HttpsDeliveryRpcTransport"
        val own = "/data/repository/sync/$client.kt"
        val sources = sourceRoot!!.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

        val declaring = sources.filter { it.invariantSeparatorsPath.endsWith(own) }
        assertTrue("The authorization covers exactly one file; found ${declaring.size}", declaring.size <= 1)

        sources.filterNot { it.invariantSeparatorsPath.endsWith(own) }.forEach { file ->
            val text = file.readText()
            assertFalse(
                "${file.name} must not construct $client: the transport stays unwired until a " +
                    "later owner authorization (#774 authorized naming it, not sending with it)",
                text.contains("$client("),
            )
            assertFalse(
                "${file.name} must not import $client either",
                text.contains("import com.isivoltpro.maginaolivo.data.repository.sync.$client"),
            )
        }
    }

    /**
     * The file-scoped exception must stay file-scoped: no other file under the sync package may
     * name the backend. If a second file there needs it, that needs its own authorization.
     */
    @Test
    fun theSyncPackageAsideFromThatOneFileStaysFreeOfTheBackendName() {
        val sourceRoot = sequenceOf(File("src/main/java"), File("app/src/main/java"))
            .firstOrNull { it.exists() }
        assertTrue("Could not locate main source directory", sourceRoot != null)

        val own = "/data/repository/sync/HttpsDeliveryRpcTransport.kt"
        sourceRoot!!.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { "/data/repository/sync/" in it.invariantSeparatorsPath }
            .filterNot { it.invariantSeparatorsPath.endsWith(own) }
            .forEach { file ->
                val text = file.readText().lowercase()
                listOf("supabase", "/rest/v1", "/storage/v1").forEach { token ->
                    assertFalse(
                        "${file.name}: the #774 exception covers one file only, not $token here",
                        text.contains(token),
                    )
                }
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

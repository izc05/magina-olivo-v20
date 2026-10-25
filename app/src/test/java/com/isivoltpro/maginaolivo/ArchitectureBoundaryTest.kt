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
        // Owner decision 25/10/2026 (#774): the G2.4 Delivery RPC HTTPS client was moved OUT of
        // main into the dev flavor, so staging and production carry no agricultural client or
        // route at all. There is therefore NO exception here any more: if that file ever returns
        // to main, this test fires again, which is exactly what should happen. The dev-flavor
        // rules live in [theDeliveryRpcHttpsClientLivesOnlyInTheDevFlavorAndIsNeverWired].

        val sources = sourceRoot!!
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .toList()
        val sourceText = sources
            .filterNot {
                weatherEdgeClient in it.invariantSeparatorsPath ||
                    accountAuth in it.invariantSeparatorsPath
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
                accountAuth in it.invariantSeparatorsPath
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
     * Owner decision 25/10/2026 (#774): for the first DEV drill the Delivery RPC HTTPS client
     * lives in the **dev flavor only**, so staging and production carry no agricultural client
     * and no agricultural route at all. Naming the backend there was authorized; SENDING with it
     * was not. This test holds all four parts of that decision at once:
     *
     *  1. the file exists, and in the dev source set only;
     *  2. it is absent from main, staging and production;
     *  3. nothing anywhere constructs or imports it, so no code path can reach the RPCs;
     *  4. it stays plain HTTPS, with no backend SDK.
     *
     * Promoting it to main is a later authorization and a new review; that step is exactly parts
     * 1 and 2 of this test turning red.
     */
    @Test
    fun theDeliveryRpcHttpsClientLivesOnlyInTheDevFlavorAndIsNeverWired() {
        val app = sequenceOf(File("src"), File("app/src")).firstOrNull { it.exists() }
        assertTrue("Could not locate the app source sets", app != null)

        val client = "HttpsDeliveryRpcTransport"
        val fileName = "$client.kt"
        val devHome = "/dev/java/com/isivoltpro/maginaolivo/data/repository/sync/$fileName"

        val all = app!!.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        assertTrue("No Kotlin sources found under ${app.path}", all.isNotEmpty())

        // 1. Exactly one declaration, and it is the dev-flavor one.
        val declaring = all.filter { it.name == fileName }
        assertTrue(
            "Expected exactly one $fileName; found ${declaring.map { it.invariantSeparatorsPath }}",
            declaring.size == 1,
        )
        assertTrue(
            "$fileName must live in the dev source set; found at ${declaring.single().invariantSeparatorsPath}",
            declaring.single().invariantSeparatorsPath.endsWith(devHome),
        )

        // 2. Absent from the source sets that feed staging and production (and from main).
        listOf("/main/java/", "/staging/java/", "/production/java/").forEach { set ->
            assertTrue(
                "$fileName must not exist under $set: staging and production carry no " +
                    "agricultural client while the transport is unactivated",
                declaring.none { set in it.invariantSeparatorsPath },
            )
        }

        // 3. Nothing constructs or imports it, in any source set, test sources included.
        all.filterNot { it.name == fileName }.forEach { file ->
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

        // 4. Plain HTTPS only, like the two older exceptions.
        val text = declaring.single().readText().lowercase()
        listOf("io.github.jan.supabase", "supabase-kt", "gotrue", "postgrest", "realtime").forEach { token ->
            assertFalse("$fileName must stay plain HTTPS (CR-006): $token", text.contains(token))
        }
    }

    /**
     * The exception is one file wide, not one package wide. No other file of the sync package, in
     * any source set, may name the backend or its routes. A second file needing it needs its own
     * authorization.
     */
    @Test
    fun theSyncPackageAsideFromThatOneFileStaysFreeOfTheBackendName() {
        val app = sequenceOf(File("src"), File("app/src")).firstOrNull { it.exists() }
        assertTrue("Could not locate the app source sets", app != null)

        app!!.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { "/data/repository/sync/" in it.invariantSeparatorsPath }
            .filterNot { it.name == "HttpsDeliveryRpcTransport.kt" }
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

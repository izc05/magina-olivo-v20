package com.isivoltpro.maginaolivo

import java.io.File
import org.junit.Assert.assertEquals
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

        // 3. Only the two opt-in DEV instrumented probes may construct this client. Neither
        // may wire the dispatch worker or invoke a remote write.
        val readProbe = all.filter { it.name == "DeliveryReadOnFreshDeviceTest.kt" }
        assertTrue("At most one DEV read probe is allowed", readProbe.size <= 1)
        readProbe.singleOrNull()?.let { probe ->
            assertTrue(probe.invariantSeparatorsPath.contains("/androidTestDev/"))
            val probeText = probe.readText()
            assertTrue(probeText.contains("g24ReadOnlyProject"))
            assertTrue(probeText.contains(".readOwnDeliveryAggregate("))
            listOf(".syncOwnDelivery(", "DeliveryDispatchWorker(", "deliveryDao().upsert").forEach { forbidden ->
                assertFalse("DEV read probe must stay read-only: $forbidden", probeText.contains(forbidden))
            }
        }
        val receiveProbe = all.filter { it.name == "DeliveryReceiveOnFreshDeviceTest.kt" }
        assertTrue("At most one DEV receive probe is allowed", receiveProbe.size <= 1)
        receiveProbe.singleOrNull()?.let { probe ->
            assertTrue(probe.invariantSeparatorsPath.contains("/androidTestDev/"))
            val probeText = probe.readText()
            assertTrue(probeText.contains("g25ReceiveProject"))
            assertTrue(probeText.contains(".readOwnDeliveryReceiveBundle("))
            assertTrue(probeText.contains(".receiveDeliveryBundle("))
            listOf(".syncOwnDelivery(", "DeliveryDispatchWorker(", ".runSingleDispatch(").forEach { forbidden ->
                assertFalse("DEV receive probe must not send: $forbidden", probeText.contains(forbidden))
            }
        }
        val campaignProbe = all.filter { it.name == "CampaignCasHttpsEphemeralTest.kt" }
        assertEquals("Exactly one isolated CAMPAIGN HTTPS probe", 1, campaignProbe.size)
        assertTrue(campaignProbe.single().invariantSeparatorsPath.contains("/androidTestDev/"))
        val campaignText = campaignProbe.single().readText()
        assertTrue(campaignText.contains("CampaignCasCoordinator("))
        assertTrue(campaignText.contains("RpcScope.CAMPAIGN_CAS"))
        assertFalse(campaignText.contains("WorkManager"))
        // G2.6 parents: read-only probe. It may receive WORKSPACE/FARM/PARCEL and the campaign
        // that depends on them, never send or wire a worker.
        val parentProbe = all.filter { it.name == "ParentReceiveHttpsEphemeralTest.kt" }
        assertEquals("Exactly one isolated parent receive HTTPS probe", 1, parentProbe.size)
        assertTrue(parentProbe.single().invariantSeparatorsPath.contains("/androidTestDev/"))
        val parentText = parentProbe.single().readText()
        assertTrue(parentText.contains("ParentReceiveCoordinator("))
        assertTrue(parentText.contains("RpcScope.PARENT_RECEIVE"))
        listOf("WorkManager", "CampaignCasCoordinator(", "RpcScope.CAMPAIGN_CAS", "RpcScope.DELIVERY",
            ".dispatch(").forEach { forbidden ->
            assertFalse("Parent receive probe must stay read-only: $forbidden", parentText.contains(forbidden))
        }
        // G2.6 WORKSPACE send: creates and sends only WORKSPACE, then reads it back as parents.
        val workspaceProbe = all.filter { it.name == "WorkspaceSendHttpsEphemeralTest.kt" }
        assertEquals("Exactly one isolated WORKSPACE send HTTPS probe", 1, workspaceProbe.size)
        assertTrue(workspaceProbe.single().invariantSeparatorsPath.contains("/androidTestDev/"))
        val workspaceText = workspaceProbe.single().readText()
        assertTrue(workspaceText.contains("WorkspaceSendCoordinator("))
        assertTrue(workspaceText.contains("RpcScope.WORKSPACE_SEND"))
        listOf("WorkManager", "CampaignCasCoordinator(", "RpcScope.CAMPAIGN_CAS", "RpcScope.DELIVERY",
            ".dispatch(", "create_reserved_farm", "create_own_parcel").forEach { forbidden ->
            assertFalse("WORKSPACE send probe must not send other entities: $forbidden", workspaceText.contains(forbidden))
        }
        // G2.6 FARM send: reserves and creates only FARM rows, then reads them back as parents.
        val farmProbe = all.filter { it.name == "FarmSendHttpsEphemeralTest.kt" }
        assertEquals("Exactly one isolated FARM send HTTPS probe", 1, farmProbe.size)
        assertTrue(farmProbe.single().invariantSeparatorsPath.contains("/androidTestDev/"))
        val farmText = farmProbe.single().readText()
        assertTrue(farmText.contains("FarmSendCoordinator("))
        assertTrue(farmText.contains("RpcScope.FARM_SEND"))
        listOf("WorkManager", "CampaignCasCoordinator(", "RpcScope.CAMPAIGN_CAS", "RpcScope.DELIVERY",
            ".dispatch(", "create_own_parcel", "create_own_campaign").forEach { forbidden ->
            assertFalse("FARM send probe must not send other entities: $forbidden", farmText.contains(forbidden))
        }
        // G2.6 PARCEL send: creates only PARCEL aggregates (with their creation-time memberships).
        val parcelProbe = all.filter { it.name == "ParcelSendHttpsEphemeralTest.kt" }
        assertEquals("Exactly one isolated PARCEL send HTTPS probe", 1, parcelProbe.size)
        assertTrue(parcelProbe.single().invariantSeparatorsPath.contains("/androidTestDev/"))
        val parcelText = parcelProbe.single().readText()
        assertTrue(parcelText.contains("ParcelSendCoordinator("))
        assertTrue(parcelText.contains("RpcScope.PARCEL_SEND"))
        listOf("WorkManager", "CampaignCasCoordinator(", "RpcScope.CAMPAIGN_CAS", "RpcScope.DELIVERY",
            ".dispatch(", "create_own_campaign", "update_own_parcel", "delete_own_parcel").forEach { forbidden ->
            assertFalse("PARCEL send probe must not send other entities: $forbidden", parcelText.contains(forbidden))
        }
        // G2.6 2c-3 convergence: parcel mutations (#805) from A and the v2 parents read (#812) on B, over a
        // disposable Supabase only. No campaign, delivery, worker or dispatch.
        val convergenceProbe = all.filter { it.name == "ParcelConvergenceHttpsEphemeralTest.kt" }
        assertEquals("Exactly one isolated convergence HTTPS probe", 1, convergenceProbe.size)
        assertTrue(convergenceProbe.single().invariantSeparatorsPath.contains("/androidTestDev/"))
        val convergenceText = convergenceProbe.single().readText()
        assertTrue(convergenceText.contains("ParentReceiveV2Coordinator("))
        assertTrue(convergenceText.contains("RpcScope.PARENT_RECEIVE_V2"))
        assertTrue(convergenceText.contains("RpcScope.PARCEL_MUTATION_SEND"))
        listOf("WorkManager", "CampaignCasCoordinator(", "RpcScope.CAMPAIGN_CAS", "RpcScope.DELIVERY",
            ".dispatch(", "create_own_campaign", "DeliveryDispatchWorker(").forEach { forbidden ->
            assertFalse("Convergence probe must stay within parcels and parents: $forbidden", convergenceText.contains(forbidden))
        }
        // #808 Android transport: the v3 Pesada writer against a disposable server only. It may wire the
        // dispatch worker (that is what it proves), but never the receiver, a scheduler or campaigns.
        val deliveryV3Probe = all.filter { it.name == "DeliveryV3HttpsEphemeralTest.kt" }
        assertEquals("Exactly one isolated Pesada v3 HTTPS probe", 1, deliveryV3Probe.size)
        assertTrue(deliveryV3Probe.single().invariantSeparatorsPath.contains("/androidTestDev/"))
        val deliveryV3Text = deliveryV3Probe.single().readText()
        assertTrue(deliveryV3Text.contains("DeliveryDispatchWorker("))
        assertTrue(deliveryV3Text.contains("RpcScope.DELIVERY"))
        listOf("WorkManager", "CampaignCasCoordinator(", "RpcScope.CAMPAIGN_CAS", "create_own_campaign",
            ".readOwnDeliveryReceiveBundle(", ".receiveDeliveryBundle(", ".runSingleDispatch(").forEach { forbidden ->
            assertFalse("Pesada v3 probe must stay a writer probe: $forbidden", deliveryV3Text.contains(forbidden))
        }
        all.filterNot { it.name == fileName || it in readProbe || it in receiveProbe || it in campaignProbe ||
            it in parentProbe || it in workspaceProbe || it in farmProbe || it in parcelProbe || it in convergenceProbe ||
            it in deliveryV3Probe }.forEach { file ->
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
     * The drill composition point is prepared but switched off, and that must be visible in a
     * diff rather than hidden in a setting. This reads the source and requires the literal
     * `const val ENABLED = false`, so turning the drill on is an obvious, reviewable change that
     * starts by making this test red — which is the moment the owner's authorization is needed.
     *
     * It also holds the rule that there is no user-facing control: the drill must not be referenced
     * from any UI or navigation source.
     */
    @Test
    fun theDeliveryDrillStaysCompiledOffUntilItIsAuthorized() {
        val app = sequenceOf(File("src"), File("app/src")).firstOrNull { it.exists() }
        assertTrue("Could not locate the app source sets", app != null)

        val sources = app!!.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        val drill = sources.singleOrNull { it.name == "DeliveryDrill.kt" }
        assertTrue("Expected exactly one DeliveryDrill.kt", drill != null)
        assertTrue(
            "The drill belongs to the dev flavor only; found at ${drill!!.invariantSeparatorsPath}",
            "/dev/java/" in drill.invariantSeparatorsPath,
        )

        val text = drill.readText()
        assertTrue(
            "DeliveryDrill.ENABLED must be the literal false while the drill is unauthorized",
            Regex("""const\s+val\s+ENABLED\s*=\s*false""").containsMatchIn(text),
        )
        // Every assignment to ENABLED in that file must be the literal false: not a BuildConfig
        // field, not a setting, not a computed expression that could become true elsewhere.
        val assigned = Regex("""ENABLED\s*=\s*(\S+)""").findAll(text)
            .map { it.groupValues[1].trimEnd(')', ',') }
            .toSet()
        assertEquals(
            "ENABLED must be the literal false and nothing else; found $assigned",
            setOf("false"),
            assigned,
        )

        // No user-facing control may exist while the drill cannot run.
        sources.filter {
            "/ui/" in it.invariantSeparatorsPath || "/navigation/" in it.invariantSeparatorsPath
        }.forEach { file ->
            assertFalse(
                "${file.name} must not reference the drill: no control may look operational " +
                    "while sending is unauthorized",
                file.readText().contains("DeliveryDrill"),
            )
        }
    }

    /**
     * #808: the v3 Pesada route is OFF in every shipped build. Production code never constructs the
     * historical route or the server owner check, so every new Pesada stays on v2 and no v3 operation
     * can be prepared. Turning it on is a visible diff that starts by making this test red.
     */
    @Test
    fun theHistoricalPesadaRouteIsNeverWiredInShippedCode() {
        val app = sequenceOf(File("src"), File("app/src")).firstOrNull { it.exists() }
        assertTrue("Could not locate the app source sets", app != null)
        val shipped = app!!.walkTopDown().filter { it.isFile && it.extension == "kt" }
            .filter { file -> listOf("/main/java/", "/staging/java/", "/production/java/", "/dev/java/")
                .any { it in file.invariantSeparatorsPath } }
            .filterNot { it.name == "DeliveryHistoricalRoute.kt" }
            .toList()
        assertTrue("No shipped Kotlin sources found", shipped.isNotEmpty())
        shipped.forEach { file ->
            val text = file.readText()
            listOf("HistoricalDeliveryRoute(", "ServerWorkspaceOwnerVerifier(", "ownerVerifier =", "payloadRoute =")
                .forEach { forbidden ->
                    assertFalse("${file.name} must not wire the #808 v3 route: $forbidden", text.contains(forbidden))
                }
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

    /** G2.6 receivers and the WORKSPACE/FARM/PARCEL senders are explicit QA entry points: no app source may construct them yet. */
    @Test
    fun g26ReceiversStayUnwiredFromAppAndScheduler() {
        val app = sequenceOf(File("src"), File("app/src")).firstOrNull { it.exists() }
        assertTrue("Could not locate the app source sets", app != null)
        val appSources = app!!.walkTopDown().filter { file ->
            file.isFile && file.extension == "kt" &&
                listOf("/main/java/", "/dev/java/", "/staging/java/", "/production/java/")
                    .any { it in file.invariantSeparatorsPath }
        }.toList()
        listOf("ParentReceiveCoordinator", "CampaignReceiveCoordinator", "WorkspaceSendCoordinator",
            "FarmSendCoordinator", "ParcelSendCoordinator", "ParcelMutationSendCoordinator", "ParentReceiveV2Coordinator").forEach { receiver ->
            appSources.filterNot { it.name == "$receiver.kt" }.forEach { file ->
                assertFalse("${file.name} must not construct $receiver", file.readText().contains("$receiver("))
            }
        }
    }
}

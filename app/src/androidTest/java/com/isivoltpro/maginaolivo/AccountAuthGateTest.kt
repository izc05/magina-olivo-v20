package com.isivoltpro.maginaolivo

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.isivoltpro.maginaolivo.app.AppCompositionRoot
import com.isivoltpro.maginaolivo.app.AppEnvironment
import com.isivoltpro.maginaolivo.app.AppearanceMode
import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.data.repository.LocalFarmLinkRepository
import com.isivoltpro.maginaolivo.domain.farm.NewFarm
import com.isivoltpro.maginaolivo.domain.parcel.NewParcel
import com.isivoltpro.maginaolivo.domain.campaign.NewCampaign
import com.isivoltpro.maginaolivo.feature.farms.FarmSection
import com.isivoltpro.maginaolivo.feature.farms.FarmSectionRoute
import com.isivoltpro.maginaolivo.feature.parcels.ParcelDetailRoute
import com.isivoltpro.maginaolivo.feature.maps.FarmMapRoute
import com.isivoltpro.maginaolivo.data.local.entity.DocumentEntity
import com.isivoltpro.maginaolivo.data.local.entity.LocalMetadata
import com.isivoltpro.maginaolivo.data.local.model.SyncStatus
import androidx.activity.compose.setContent
import com.isivoltpro.maginaolivo.feature.account.*
import com.isivoltpro.maginaolivo.ui.theme.MaginaOlivoTheme
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.junit.runners.model.Statement

/**
 * Android instrumented tests for Mandatory Access (Auth Gate) 1.0.
 *
 * Verifies:
 * 1. First launch without session: displays Iniciar sesión and Crear cuenta, agricultural shell blocked.
 * 2. Production fail-closed: unconfigured or unauthenticated access in PRODUCTION is strictly blocked.
 * 3. Offline access with expired JWT: verified account offline is granted local access without remote calls.
 * 4. Restart with saved session: opens directly into the app without prompting for password.
 * 5. Logout: returns to access gate immediately, blocks agricultural shell, preserves local data.
 * 6. Multi-user Room farm isolation (A -> B -> A): Real Room database, User A creates a farm,
 *    logs out, User B logs in and sees 0 farms (A's farm is absent), B creates their farm, logs out,
 *    A logs back in and recovers their farm intact. Works with ledger == null.
 */
@RunWith(AndroidJUnit4::class)
class AccountAuthGateTest {
    private val compose = createAndroidComposeRule<ComponentActivity>()
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private val userA = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val userB = UUID.fromString("00000000-0000-0000-0000-000000000002")

    private val guard = TestRule { base, _ ->
        object : Statement() {
            override fun evaluate() {
                assertEquals("com.isivoltpro.maginaolivo.dev", context.packageName)
                android.os.ParcelFileDescriptor.AutoCloseInputStream(
                    InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("getprop ro.kernel.qemu")
                ).bufferedReader().use { assertEquals("1", it.readText().trim()) }
                base.evaluate()
            }
        }
    }

    @get:Rule
    val rules: TestRule = RuleChain.outerRule(guard).around(IsolatedAppStateRule()).around(compose)

    private inner class TestAuthApi : AccountAuthApi {
        override val simulated: Boolean = false
        override val redirectUri: String = "com.isivoltpro.maginaolivo.dev://auth/callback"

        private fun makeSession(userId: UUID, email: String, expired: Boolean = false) =
            AuthSession(
                userId = userId,
                email = email,
                accessToken = "synthetic-access",
                refreshToken = "synthetic-refresh",
                expiresAt = if (expired) Instant.now().minusSeconds(3600) else Instant.now().plusSeconds(3600)
            )

        override suspend fun signIn(email: String, password: String): AuthSession {
            if (password == "incorrecta") throw AuthFailure(AuthFailure.Kind.CREDENTIALS)
            val id = if (email.startsWith("b")) userB else userA
            return makeSession(id, email)
        }

        override suspend fun signUp(email: String, password: String, challenge: String): AuthSession? = null
        override suspend fun recover(email: String, challenge: String) {}
        override suspend fun exchange(code: String, verifier: String): AuthSession = makeSession(userA, "a@example.com")
        override suspend fun refresh(session: AuthSession): AuthSession = makeSession(session.userId, session.email, expired = false)
        override suspend fun user(session: AuthSession): Pair<UUID, String> = session.userId to session.email
        override suspend fun changePassword(session: AuthSession, password: String) {}
        override suspend fun signOut(session: AuthSession) {}
    }

    private fun renderGate(
        repository: AccountRepository,
        root: AppCompositionRoot,
        environment: AppEnvironment = AppEnvironment.DEV,
        contentTag: String = "agricultural-content"
    ) {
        compose.setContent {
            MaginaOlivoTheme(AppearanceMode.DARK) {
                Box(Modifier.fillMaxSize().systemBarsPadding()) {
                    AuthGateHost(
                        accountRepository = repository,
                        farmLinks = root.farmLinks,
                        simulationAllowed = true,
                        environment = environment
                    ) {
                        Box(Modifier.fillMaxSize().testTag(contentTag)) {
                            Text("Contenido agrícola protegido", Modifier.testTag("protected-label"))
                        }
                    }
                }
            }
        }
    }

    @Test
    fun firstLaunchShowsLoginAndRegisterAndBlocksAgriculturalContent() {
        val store = MemoryAccountSessionStore()
        val repo = AccountRepository(TestAuthApi(), store)
        val root = AppCompositionRoot.createAndroid(context, "DEV")

        renderGate(repo, root)

        // Auth gate is displayed
        waitFor("auth-gate-root")
        compose.onNodeWithTag("auth-gate-root").assertIsDisplayed()
        compose.onNodeWithTag("account-login").assertIsDisplayed() // "Iniciar sesión"
        compose.onNodeWithTag("account-mode-register").assertIsDisplayed() // "Crear cuenta"

        // Agricultural content must NOT exist
        compose.onNodeWithTag("agricultural-content").assertDoesNotExist()
        compose.onNodeWithTag("protected-label").assertDoesNotExist()
    }

    @Test
    fun productionEnvironmentWithoutConfigurationFailsClosed() {
        val unconfiguredRepo = AccountRepository(null)
        val root = AppCompositionRoot.createAndroid(context, "DEV")

        renderGate(unconfiguredRepo, root, environment = AppEnvironment.PRODUCTION)

        waitFor("auth-gate-root")
        compose.onNodeWithTag("auth-gate-root").assertIsDisplayed()
        compose.onNodeWithTag("auth-gate-unconfigured-message").assertIsDisplayed()
        compose.onNodeWithTag("agricultural-content").assertDoesNotExist()
    }

    @Test
    fun restartWithSavedSessionRestoresDirectAccessWithoutPassword() = runBlocking {
        val store = MemoryAccountSessionStore()
        val sessionA = AuthSession(userA, "a@example.com", "access", "refresh", Instant.now().plusSeconds(3600))
        store.write(AccountRecord(session = sessionA))

        val repo = AccountRepository(TestAuthApi(), store)
        repo.resume(online = true)
        val root = AppCompositionRoot.createAndroid(context, "DEV")

        renderGate(repo, root)

        // Immediately grants access to agricultural content
        waitFor("agricultural-content")
        compose.onNodeWithTag("agricultural-content").assertIsDisplayed()
        compose.onNodeWithTag("protected-label").assertIsDisplayed()

        // Auth gate and password prompt must not be shown
        compose.onNodeWithTag("auth-gate-root").assertDoesNotExist()
        compose.onNodeWithTag("account-password").assertDoesNotExist()
    }

    @Test
    fun offlineModeWithExpiredJwtAllowsLocalAccessForVerifiedAccount() = runBlocking {
        val store = MemoryAccountSessionStore()
        // Token expired 2 hours ago
        val sessionExpired = AuthSession(userA, "campo@example.test", "access", "refresh", Instant.now().minusSeconds(7200))
        store.write(AccountRecord(session = sessionExpired))

        val repo = AccountRepository(TestAuthApi(), store)
        // Resume offline (no network in field)
        repo.resume(online = false)
        assertEquals(AccountStatus.EXPIRED, repo.state.value.status)
        assertFalse(repo.state.value.online)
        assertNotNull(repo.state.value.userId)

        val root = AppCompositionRoot.createAndroid(context, "DEV")
        renderGate(repo, root, environment = AppEnvironment.PRODUCTION)

        // Directly enters into protected content in offline mode despite expired JWT
        waitFor("agricultural-content")
        compose.onNodeWithTag("agricultural-content").assertIsDisplayed()
        compose.onNodeWithTag("auth-gate-root").assertDoesNotExist()
    }

    @Test
    fun logoutReturnsToGateAndProtectsLocalDataWithoutDeletingIt() = runBlocking {
        val store = MemoryAccountSessionStore()
        val sessionA = AuthSession(userA, "a@example.com", "access", "refresh", Instant.now().plusSeconds(3600))
        store.write(AccountRecord(session = sessionA))

        val root = AppCompositionRoot.createAndroid(context, "DEV")
        val local = checkNotNull(root.localPersistence)
        val workspace = (local.workspaceRepository.ensureLocalWorkspace() as AppResult.Success).value
        val farm = (local.farmRepository.create(NewFarm(workspace, "Finca local preservada")) as AppResult.Success).value

        val repo = AccountRepository(TestAuthApi(), store)
        repo.resume(online = true)

        renderGate(repo, root)
        waitFor("agricultural-content")

        // Perform logout
        repo.logout(online = true)

        // Screen immediately switches to Auth Gate
        waitFor("auth-gate-root")
        compose.onNodeWithTag("auth-gate-root").assertIsDisplayed()
        compose.onNodeWithTag("account-login").assertIsDisplayed()
        compose.onNodeWithTag("agricultural-content").assertDoesNotExist()

        // Verify local data is completely preserved in Room
        assertNotNull(local.database.farmDao().findById(farm))
        assertNull(store.read().session)
    }

    @Test
    fun realRoomMultiUserFarmIsolationAToBToA() = runBlocking {
        val store = MemoryAccountSessionStore()
        val repo = AccountRepository(TestAuthApi(), store)
        // Pass repo so that all repositories (workspaceRepository, farmLinks, etc.) share the exact same AccountRepository instance
        val root = AppCompositionRoot.createAndroid(context, "DEV", accountRepository = repo)
        val local = checkNotNull(root.localPersistence)
        val linkRepo = checkNotNull(root.farmLinks)

        // 1. User A logs in
        repo.login("a@example.com", "passwordA123", online = true)
        assertEquals(userA, repo.state.value.userId)

        // User A creates a farm in Room
        val workspaceA = (local.workspaceRepository.ensureLocalWorkspace() as AppResult.Success).value
        val farmA = (local.farmRepository.create(NewFarm(workspaceA, "Finca Olivar de A")) as AppResult.Success).value
        val farmsForA = local.farmRepository.observeActive(workspaceA).first()
        assertEquals(1, farmsForA.size)
        assertEquals("Finca Olivar de A", farmsForA.first().name)

        assertEquals("With ledger == null, User A sees their own farm in link choices", 1, linkRepo.choices().size)
        assertEquals("Finca Olivar de A", linkRepo.choices().first().name)

        // 2. User A logs out
        repo.logout(online = true)
        assertEquals(AccountStatus.SIGNED_OUT, repo.state.value.status)

        // 3. User B logs in
        repo.login("b@example.com", "passwordB123", online = true)
        assertEquals(userB, repo.state.value.userId)

        // User B resolves their own workspace
        val workspaceB = (local.workspaceRepository.ensureLocalWorkspace() as AppResult.Success).value
        assertNotEquals("User B must not be assigned User A's workspace", workspaceA, workspaceB)

        // Assertion of absence: User B does NOT see User A's farm!
        val farmsForBInitial = local.farmRepository.observeActive(workspaceB).first()
        assertEquals("User B must see 0 active farms initially (A's farm is absent)", 0, farmsForBInitial.size)
        assertEquals("With ledger == null, User B must see 0 choices (A's farm is isolated)", 0, linkRepo.choices().size)

        // User B creates their own farm in Room
        val farmB = (local.farmRepository.create(NewFarm(workspaceB, "Finca Olivar de B")) as AppResult.Success).value
        val farmsForBAfter = local.farmRepository.observeActive(workspaceB).first()
        assertEquals(1, farmsForBAfter.size)
        assertEquals("Finca Olivar de B", farmsForBAfter.first().name)
        assertEquals("With ledger == null, User B sees only their own farm", 1, linkRepo.choices().size)
        assertEquals("Finca Olivar de B", linkRepo.choices().first().name)

        // 4. User B logs out
        repo.logout(online = true)

        // 5. User A logs back in
        repo.login("a@example.com", "passwordA123", online = true)
        assertEquals(userA, repo.state.value.userId)

        // User A resolves their workspace
        val workspaceARecovered = (local.workspaceRepository.ensureLocalWorkspace() as AppResult.Success).value
        assertEquals("User A must recover their original workspace", workspaceA, workspaceARecovered)

        // Assertion of recovery: User A's farm is fully recovered and B's farm is not present!
        val farmsForARecovered = local.farmRepository.observeActive(workspaceARecovered).first()
        assertEquals(1, farmsForARecovered.size)
        assertEquals("Finca Olivar de A", farmsForARecovered.first().name)
        assertFalse(farmsForARecovered.any { it.name == "Finca Olivar de B" })
        assertEquals("With ledger == null, User A recovers their farm in choices without B's farm", 1, linkRepo.choices().size)
        assertEquals("Finca Olivar de A", linkRepo.choices().first().name)

        // Verify both farms still exist unharmed in the database
        assertNotNull(local.database.farmDao().findById(farmA))
        assertNotNull(local.database.farmDao().findById(farmB))
    }

    @Test
    fun historicalFarmsAreNotVisibleByDefaultToAuthenticatedUser() = runBlocking {
        val store = MemoryAccountSessionStore()
        val repo = AccountRepository(TestAuthApi(), store)
        val root = AppCompositionRoot.createAndroid(context, "DEV", accountRepository = repo)
        val local = checkNotNull(root.localPersistence)
        val linkRepo = checkNotNull(root.farmLinks)

        // Seed a historical unlinked farm in SQLite
        val db = local.database.openHelper.writableDatabase
        val historicalWorkspaceId = UUID.randomUUID().toString()
        val historicalFarmId = UUID.randomUUID().toString()
        db.execSQL("INSERT INTO workspaces (id,name,owner_user_id,country_code,timezone,locale,currency,created_at,updated_at,version,sync_status) VALUES (?,'Antiguo','local-owner','ES','Europe/Madrid','es-ES','EUR',1,1,1,'LOCAL_ONLY')", arrayOf(historicalWorkspaceId))
        db.execSQL("INSERT INTO farms (id,workspace_id,name,status,created_at,updated_at,version,sync_status) VALUES (?,?,'Finca Antigua Sin Vincular','ACTIVE',1,1,1,'LOCAL_ONLY')", arrayOf(historicalFarmId, historicalWorkspaceId))

        // User A logs in
        repo.login("a@example.com", "passwordA123", online = true)

        // Historical farm is NOT visible or linked to User A by default
        val choices = linkRepo.choices()
        assertFalse("Historical farm must not be visible by default to authenticated user", choices.any { it.name == "Finca Antigua Sin Vincular" })
        assertEquals(0, choices.size)

        // Data is strictly preserved in SQLite
        assertNotNull("Historical farm must be preserved in SQLite", local.database.farmDao().findById(UUID.fromString(historicalFarmId)))
    }

    @Test
    fun stagingEnvironmentWithoutConfigurationFailsClosed() = runBlocking {
        val unconfiguredRepo = AccountRepository(null)
        val root = AppCompositionRoot.createAndroid(context, "STAGING", accountRepository = unconfiguredRepo)

        renderGate(unconfiguredRepo, root, environment = AppEnvironment.STAGING)

        waitFor("auth-gate-root")
        compose.onNodeWithTag("auth-gate-root").assertIsDisplayed()
        compose.onNodeWithTag("auth-gate-unconfigured-message").assertIsDisplayed()
        compose.onNodeWithTag("agricultural-content").assertDoesNotExist()
    }

    @Test
    fun directRouteToForeignFarmDoesNotDiscloseParcelsCampaignsOrDocumentsToAnotherUser() = runBlocking {
        val store = MemoryAccountSessionStore()
        val repo = AccountRepository(TestAuthApi(), store)
        val root = AppCompositionRoot.createAndroid(context, "DEV", accountRepository = repo)
        val local = checkNotNull(root.localPersistence)

        // 1. User A logs in and creates Farm A, a Parcel, a Campaign, and a Document
        repo.login("a@example.com", "passwordA123", online = true)
        val workspaceA = (local.workspaceRepository.ensureLocalWorkspace() as AppResult.Success).value
        val farmA = (local.farmRepository.create(NewFarm(workspaceA, "Finca Privada de A")) as AppResult.Success).value

        val parcelA = (local.parcelRepository.create(
            NewParcel(
                farmId = farmA,
                displayName = "Parcela Secreta A",
            )
        ) as AppResult.Success).value

        val campaignA = (local.campaignRepository.create(
            NewCampaign(
                farmId = farmA,
                name = "Campaña Secreta A",
                startDate = LocalDate.of(2025, 11, 1),
            )
        ) as AppResult.Success).value

        val docA = UUID.randomUUID()
        val now = Instant.now()
        local.database.documentDao().insert(
            DocumentEntity(
                id = docA,
                workspaceId = workspaceA,
                ownerType = "FARM",
                ownerId = farmA,
                type = "OTHER",
                mimeType = "application/pdf",
                displayName = "Escritura Privada A",
                fileSizeBytes = 2048L,
                sha256 = "dummy-sha",
                localUri = "file:///fake/storage/doc.pdf",
                remotePath = null,
                uploadStatus = "LOCAL_ONLY",
                metadata = LocalMetadata(createdAt = now, updatedAt = now, syncStatus = SyncStatus.LOCAL_ONLY),
            )
        )

        // 2. User A logs out
        repo.logout(online = true)

        // 3. User B logs in
        repo.login("b@example.com", "passwordB123", online = true)
        val workspaceB = (local.workspaceRepository.ensureLocalWorkspace() as AppResult.Success).value
        assertNotEquals(workspaceA, workspaceB)

        // 4. Test direct navigation to PARCELS section of Farm A: User B must NOT see Farm A or its parcels
        compose.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                MaginaOlivoTheme {
                    FarmSectionRoute(
                        farmId = farmA,
                        section = FarmSection.PARCELS,
                        persistence = local,
                        onParcelSelected = {},
                        onCampaignSelected = {},
                        onActivitySelected = {},
                    )
                }
            }
        }
        waitFor("farm-section-unauthorized")
        compose.onNodeWithTag("farm-section-unauthorized").assertIsDisplayed()
        compose.onNodeWithText("Finca Privada de A").assertDoesNotExist()
        compose.onNodeWithText("Parcela Secreta A").assertDoesNotExist()

        // 5. Test direct navigation to CAMPAIGNS section of Farm A: User B must NOT see campaigns of Farm A
        compose.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                MaginaOlivoTheme {
                    FarmSectionRoute(
                        farmId = farmA,
                        section = FarmSection.CAMPAIGNS,
                        persistence = local,
                        onParcelSelected = {},
                        onCampaignSelected = {},
                        onActivitySelected = {},
                    )
                }
            }
        }
        waitFor("farm-section-unauthorized")
        compose.onNodeWithTag("farm-section-unauthorized").assertIsDisplayed()
        compose.onNodeWithText("Campaña Secreta A").assertDoesNotExist()

        // 6. Test direct navigation to DOCUMENTS section of Farm A: User B must NOT see documents of Farm A
        compose.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                MaginaOlivoTheme {
                    FarmSectionRoute(
                        farmId = farmA,
                        section = FarmSection.DOCUMENTS,
                        persistence = local,
                        onParcelSelected = {},
                        onCampaignSelected = {},
                        onActivitySelected = {},
                    )
                }
            }
        }
        waitFor("farm-section-unauthorized")
        compose.onNodeWithTag("farm-section-unauthorized").assertIsDisplayed()
        compose.onNodeWithText("Escritura Privada A").assertDoesNotExist()
    }

    @Test
    fun directRouteToForeignParcelDoesNotDiscloseParcelDetailOrLocateMapToAnotherUser() = runBlocking {
        val store = MemoryAccountSessionStore()
        val repo = AccountRepository(TestAuthApi(), store)
        val root = AppCompositionRoot.createAndroid(context, "DEV", accountRepository = repo)
        val local = checkNotNull(root.localPersistence)

        // 1. User A logs in and creates a Farm and a Parcel
        repo.login("a@example.com", "passwordA123", online = true)
        val workspaceA = (local.workspaceRepository.ensureLocalWorkspace() as AppResult.Success).value
        val farmA = (local.farmRepository.create(NewFarm(workspaceA, "Finca Exclusiva A")) as AppResult.Success).value
        val parcelA = (local.parcelRepository.create(
            NewParcel(
                farmId = farmA,
                displayName = "Parcela Confidencial A",
            )
        ) as AppResult.Success).value

        // 2. User A logs out
        repo.logout(online = true)

        // 3. User B logs in
        repo.login("b@example.com", "passwordB123", online = true)
        val workspaceB = (local.workspaceRepository.ensureLocalWorkspace() as AppResult.Success).value
        assertNotEquals(workspaceA, workspaceB)

        // 4. Test direct navigation to ParcelDetailRoute of Parcel A: User B must see "Parcela no disponible"
        compose.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                MaginaOlivoTheme {
                    ParcelDetailRoute(
                        parcelId = parcelA,
                        persistence = local,
                        onArchived = {},
                    )
                }
            }
        }
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Parcela no disponible").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Parcela no disponible").assertIsDisplayed()
        compose.onNodeWithText("Parcela Confidencial A").assertDoesNotExist()

        // 5. Test direct navigation to FarmMapRoute with locateParcelId = Parcel A: User B must not see parcel name
        compose.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                MaginaOlivoTheme {
                    FarmMapRoute(
                        farmId = farmA,
                        persistence = local,
                        locateParcelId = parcelA,
                        onOpenParcel = {},
                        onSearchByReference = {},
                    )
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("Ubicar «Parcela Confidencial A»").assertDoesNotExist()
        compose.onNodeWithText("Parcela Confidencial A").assertDoesNotExist()

        // 6. User B logs out
        repo.logout(online = true)

        // 7. User A logs back in and recovers their parcel
        repo.login("a@example.com", "passwordA123", online = true)
        compose.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                MaginaOlivoTheme {
                    ParcelDetailRoute(
                        parcelId = parcelA,
                        persistence = local,
                        onArchived = {},
                    )
                }
            }
        }
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Parcela Confidencial A").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Parcela Confidencial A").assertIsDisplayed()
    }

    @Test
    fun androidPersistedSessionStoreSurvivesOfflineWithExpiredJwt() = runBlocking {
        val sessionFile = File(context.noBackupFilesDir, "qa-offline-session-${UUID.randomUUID()}.json")
        val keyAlias = "qa-key-${UUID.randomUUID()}"
        val scope = "test-scope"
        try {
            val store = AndroidAccountSessionStore(sessionFile, keyAlias, scope)
            val sessionExpired = AuthSession(userA, "persisted-offline@example.test", "access", "refresh", Instant.now().minusSeconds(3600))
            store.write(AccountRecord(session = sessionExpired))

            // Simulate application process restart: new Store and Repository reading encrypted file from disk
            val reloadedStore = AndroidAccountSessionStore(sessionFile, keyAlias, scope)
            val reloadedRepo = AccountRepository(TestAuthApi(), reloadedStore)

            // Resume in offline mode without network
            reloadedRepo.resume(online = false)

            assertEquals(AccountStatus.EXPIRED, reloadedRepo.state.value.status)
            assertFalse(reloadedRepo.state.value.online)
            assertEquals(userA, reloadedRepo.state.value.userId)
            assertEquals(userA, reloadedStore.read().session?.userId)

            val root = AppCompositionRoot.createAndroid(context, "DEV", accountRepository = reloadedRepo)
            renderGate(reloadedRepo, root, environment = AppEnvironment.PRODUCTION)

            waitFor("agricultural-content")
            compose.onNodeWithTag("agricultural-content").assertIsDisplayed()
            compose.onNodeWithTag("auth-gate-root").assertDoesNotExist()
        } finally {
            sessionFile.delete()
            File(sessionFile.path + ".bak").delete()
        }
    }

    private fun waitFor(tag: String) =
        compose.waitUntil(15_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
}

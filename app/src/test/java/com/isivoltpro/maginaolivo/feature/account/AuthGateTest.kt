package com.isivoltpro.maginaolivo.feature.account

import com.isivoltpro.maginaolivo.app.AppEnvironment
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class AuthGateTest {
    private val now = Instant.parse("2026-10-30T10:00:00Z")
    private val userA = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val userB = UUID.fromString("00000000-0000-0000-0000-000000000002")

    private fun session(userId: UUID, email: String, expired: Boolean = false): AuthSession =
        AuthSession(
            userId = userId,
            email = email,
            accessToken = "token-$userId",
            refreshToken = "refresh-$userId",
            expiresAt = if (expired) now.minusSeconds(3600) else now.plusSeconds(3600)
        )

    private inner class FakeApi(override val simulated: Boolean = false) : AccountAuthApi {
        override val redirectUri: String = "com.isivoltpro.maginaolivo.dev://auth/callback"
        override suspend fun signIn(email: String, password: String): AuthSession {
            val id = if (email.startsWith("b")) userB else userA
            return session(id, email)
        }
        override suspend fun signUp(email: String, password: String, challenge: String): AuthSession? = null
        override suspend fun recover(email: String, challenge: String) {}
        override suspend fun exchange(code: String, verifier: String): AuthSession = session(userA, "a@example.com")
        override suspend fun refresh(session: AuthSession): AuthSession = session(session.userId, session.email, expired = false)
        override suspend fun user(session: AuthSession): Pair<UUID, String> = session.userId to session.email
        override suspend fun changePassword(session: AuthSession, password: String) {}
        override suspend fun signOut(session: AuthSession) {}
    }

    private fun checkAuthorized(
        repo: AccountRepository,
        environment: AppEnvironment = AppEnvironment.DEV
    ): Boolean {
        val state = repo.state.value
        val hasOfflineVerifiedAccess = !state.online && state.userId != null
        return when {
            environment in setOf(AppEnvironment.PRODUCTION, AppEnvironment.STAGING) -> {
                repo.isConfigured && (state.status in setOf(AccountStatus.SIGNED_IN, AccountStatus.OFFLINE) || hasOfflineVerifiedAccess)
            }
            repo.isConfigured && !state.simulated -> {
                state.status in setOf(AccountStatus.SIGNED_IN, AccountStatus.OFFLINE) || hasOfflineVerifiedAccess
            }
            else -> {
                true
            }
        }
    }

    @Test
    fun stagingEnvironmentWithoutConfigurationFailsClosed() {
        val unconfiguredRepo = AccountRepository(null)
        assertFalse(unconfiguredRepo.isConfigured)

        val isAuthorized = checkAuthorized(unconfiguredRepo, AppEnvironment.STAGING)
        assertFalse("STAGING without Auth configuration must fail closed and block access", isAuthorized)
    }

    @Test
    fun stagingEnvironmentRequiresAuthenticatedSession() {
        val repo = AccountRepository(FakeApi())
        assertTrue(repo.isConfigured)
        assertEquals(AccountStatus.SIGNED_OUT, repo.state.value.status)

        assertFalse("Signed out state in STAGING must be unauthorized", checkAuthorized(repo, AppEnvironment.STAGING))
    }

    @Test
    fun productionEnvironmentWithoutConfigurationFailsClosed() {
        val unconfiguredRepo = AccountRepository(null)
        assertFalse(unconfiguredRepo.isConfigured)

        val isAuthorized = checkAuthorized(unconfiguredRepo, AppEnvironment.PRODUCTION)
        assertFalse("PRODUCTION without Auth configuration must fail closed and block access", isAuthorized)
    }

    @Test
    fun productionEnvironmentRequiresAuthenticatedSession() {
        val repo = AccountRepository(FakeApi())
        assertTrue(repo.isConfigured)
        assertEquals(AccountStatus.SIGNED_OUT, repo.state.value.status)

        assertFalse("Signed out state in PRODUCTION must be unauthorized", checkAuthorized(repo, AppEnvironment.PRODUCTION))
    }

    @Test
    fun productionEnvironmentAllowsAccessWhenAuthenticated() = runTest {
        val store = MemoryAccountSessionStore()
        val repo = AccountRepository(FakeApi(), store, now = { now })
        repo.login("productor@example.com", "password42", online = true)

        assertEquals(AccountStatus.SIGNED_IN, repo.state.value.status)
        assertTrue("Authenticated session in PRODUCTION must grant access", checkAuthorized(repo, AppEnvironment.PRODUCTION))
    }

    @Test
    fun unconfiguredDevHarnessPreservesBypassForAutomatedTestsOnly() {
        val unconfiguredRepo = AccountRepository(null)
        assertTrue("DEV unconfigured harness allows bypass for screenshot tests", checkAuthorized(unconfiguredRepo, AppEnvironment.DEV))
    }

    @Test
    fun configuredDevRequiresAuthentication() {
        val repo = AccountRepository(FakeApi())
        assertFalse("Configured DEV requires authentication", checkAuthorized(repo, AppEnvironment.DEV))
    }

    @Test
    fun offlineAccessWithExpiredTokenAllowsLocalAccessForVerifiedAccount() = runTest {
        val store = MemoryAccountSessionStore()
        // Simulate a token that expired 1 hour ago
        store.write(AccountRecord(session = session(userA, "campo@example.test", expired = true)))
        val repo = AccountRepository(FakeApi(simulated = false), store, now = { now })

        // When offline in the olive grove with no mobile coverage
        repo.resume(online = false)

        assertEquals("Previously verified user offline gets EXPIRED status for token", AccountStatus.EXPIRED, repo.state.value.status)
        assertEquals(userA, repo.state.value.userId)
        assertFalse(repo.state.value.online)
        assertTrue("Must allow local offline access in field", checkAuthorized(repo, AppEnvironment.PRODUCTION))

        // Remote operations requiring fresh token are forbidden
        var caught: AuthFailure? = null
        try {
            repo.withLocalIdentity(requireFresh = true) { /* remote rpc */ }
        } catch (e: AuthFailure) {
            caught = e
        }
        org.junit.Assert.assertNotNull("Expected AuthFailure to be thrown", caught)
        assertEquals(AuthFailure.Kind.EXPIRED, caught!!.kind)

        // Cached local access succeeds
        val accessedId = repo.withCachedLocalAccess { it }
        assertEquals(userA, accessedId)
    }

    @Test
    fun onlineResumptionWithExpiredTokenRefreshesOrLocksOut() = runTest {
        val store = MemoryAccountSessionStore()
        store.write(AccountRecord(session = session(userA, "campo@example.test", expired = true)))
        val repo = AccountRepository(FakeApi(), store, now = { now })

        // When device comes back online, resume refreshes token
        repo.resume(online = true)

        assertEquals(AccountStatus.SIGNED_IN, repo.state.value.status)
        assertEquals(userA, repo.state.value.userId)
        assertTrue(checkAuthorized(repo, AppEnvironment.PRODUCTION))
    }

    @Test
    fun logoutReturnsToLockedStateAndPreservesLocalRecordsIntact() = runTest {
        val store = MemoryAccountSessionStore()
        store.write(AccountRecord(session = session(userA, "a@example.com")))
        val repo = AccountRepository(FakeApi(), store, now = { now })
        repo.resume(online = true)
        assertEquals(AccountStatus.SIGNED_IN, repo.state.value.status)

        repo.logout(online = true)

        assertEquals(AccountStatus.SIGNED_OUT, repo.state.value.status)
        assertNull(repo.state.value.userId)
        assertNull(store.read().session)
        assertFalse("Logged out state must lock access gate in all environments", checkAuthorized(repo, AppEnvironment.PRODUCTION))
        assertFalse("Logged out state must lock access gate in DEV", checkAuthorized(repo, AppEnvironment.DEV))
    }

    @Test
    fun accountSwitchFromAToBReplacesIdentityWithoutLeakage() = runTest {
        val store = MemoryAccountSessionStore()
        val repo = AccountRepository(FakeApi(), store, now = { now })

        // User A logs in
        repo.login("a@example.com", "passwordA123", online = true)
        assertEquals(userA, repo.state.value.userId)

        // User A logs out
        repo.logout(online = true)
        assertEquals(AccountStatus.SIGNED_OUT, repo.state.value.status)

        // User B logs in
        repo.login("b@example.com", "passwordB123", online = true)
        assertEquals(AccountStatus.SIGNED_IN, repo.state.value.status)
        assertEquals(userB, repo.state.value.userId)
        assertEquals(userB, store.read().session?.userId)
    }
}

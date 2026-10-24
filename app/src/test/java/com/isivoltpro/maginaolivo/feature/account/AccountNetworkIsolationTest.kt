package com.isivoltpro.maginaolivo.feature.account

import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * G2.4 block 1: saving field data never waits for Auth HTTP, and a late Auth result never
 * restores or clears an identity that was signed out or replaced while it was in flight.
 */
class AccountNetworkIsolationTest {
    private val time = Instant.parse("2026-10-24T08:00:00Z")
    private val idA = UUID.fromString("00000000-0000-0000-0000-00000000000a")
    private val idB = UUID.fromString("00000000-0000-0000-0000-00000000000b")
    private fun session(id: UUID, email: String, expires: Instant = time.plusSeconds(3600)) =
        AuthSession(id, email, "access-$email", "refresh-$email", expires)

    /** Every HTTP call can be held open until the test releases it, like a very poor signal. */
    private inner class SlowApi : AccountAuthApi {
        override val redirectUri = "com.isivoltpro.maginaolivo.dev://auth/callback"
        var refreshGate: CompletableDeferred<Unit>? = null
        var signInGate: CompletableDeferred<Unit>? = null
        var refreshReply: AuthSession = session(idA, "a@example.com")
        var refreshFailure: AuthFailure? = null
        val signIns = mutableMapOf<String, AuthSession>()
        var signOuts = 0
        override suspend fun signIn(email: String, password: String): AuthSession {
            signInGate?.await(); return signIns.getValue(email)
        }
        override suspend fun signUp(email: String, password: String, challenge: String): AuthSession? = null
        override suspend fun recover(email: String, challenge: String) = Unit
        override suspend fun exchange(code: String, verifier: String): AuthSession = error("unused")
        override suspend fun refresh(session: AuthSession): AuthSession {
            refreshGate?.await(); refreshFailure?.let { throw it }; return refreshReply
        }
        override suspend fun user(session: AuthSession) = session.userId to session.email
        override suspend fun changePassword(session: AuthSession, password: String) = Unit
        override suspend fun signOut(session: AuthSession) { signOuts++ }
    }

    private fun expiringA(store: MemoryAccountSessionStore) =
        store.write(AccountRecord(session = session(idA, "a@example.com", time.minusSeconds(1))))

    @Test fun localWriteCompletesWhileRefreshHttpIsSuspended() = runTest {
        val api = SlowApi().apply { refreshGate = CompletableDeferred() }
        val store = MemoryAccountSessionStore().also(::expiringA)
        val repo = AccountRepository(api, store, { time })
        val refresh = launch { repo.resume(true) }
        testScheduler.runCurrent()
        assertFalse("The refresh must still be waiting for the server", refresh.isCompleted)
        var attributed: UUID? = null
        // Runs to completion without advancing the scheduler: it does not wait for HTTP.
        repo.withLocalWriteIdentity { attributed = it }
        assertEquals("Offline attribution uses the cached identity", idA, attributed)
        assertFalse(refresh.isCompleted)
        api.refreshGate!!.complete(Unit); refresh.join()
        assertEquals(AccountStatus.SIGNED_IN, repo.state.value.status)
    }

    @Test fun localWriteCompletesWhileLoginHttpIsSuspended() = runTest {
        val api = SlowApi().apply {
            signInGate = CompletableDeferred(); signIns["a@example.com"] = session(idA, "a@example.com")
        }
        val repo = AccountRepository(api, MemoryAccountSessionStore(), { time })
        val login = launch { repo.login("a@example.com", "password42", true) }
        testScheduler.runCurrent()
        var calls = 0
        repo.withLocalWriteIdentity { assertNull("No identity until login really completes", it); calls++ }
        assertEquals(1, calls)
        api.signInGate!!.complete(Unit); login.join()
        repo.withLocalWriteIdentity { assertEquals(idA, it) }
    }

    @Test fun logoutDuringRefreshIsImmediateAndTheLateRefreshCannotRestoreTheAccount() = runTest {
        val api = SlowApi().apply { refreshGate = CompletableDeferred() }
        val store = MemoryAccountSessionStore().also(::expiringA)
        val repo = AccountRepository(api, store, { time })
        val refresh = launch { repo.resume(true) }
        testScheduler.runCurrent()
        repo.logout(false) // completes without the refresh finishing
        assertFalse(refresh.isCompleted)
        assertNull(store.read().session)
        api.refreshGate!!.complete(Unit); refresh.join()
        assertNull("A stale refresh must not resurrect a signed-out account", store.read().session)
        assertEquals(AccountStatus.SIGNED_OUT, repo.state.value.status)
        repo.withLocalWriteIdentity { assertNull(it) }
    }

    @Test fun staleRefreshFailureDoesNotShowAnErrorOrTouchTheNextAccount() = runTest {
        val api = SlowApi().apply {
            refreshGate = CompletableDeferred(); refreshFailure = AuthFailure(AuthFailure.Kind.EXPIRED)
            signIns["b@example.com"] = session(idB, "b@example.com")
        }
        val store = MemoryAccountSessionStore().also(::expiringA)
        val repo = AccountRepository(api, store, { time })
        val refresh = launch { repo.resume(true) }
        testScheduler.runCurrent()
        repo.logout(false)
        // B's login queues behind A's Auth flow, but local writes keep working meanwhile.
        val loginB = launch { repo.login("b@example.com", "password42", true) }
        testScheduler.runCurrent()
        repo.withLocalWriteIdentity { assertNull(it) }
        api.refreshGate!!.complete(Unit); refresh.join(); loginB.join()
        assertEquals(idB, store.read().session!!.userId)
        assertEquals(AccountStatus.SIGNED_IN, repo.state.value.status)
        assertNull(repo.state.value.error)
        repo.withLocalWriteIdentity { assertEquals(idB, it) }
    }

    @Test fun accountSwitchAToBNeverAttributesBWritesToA() = runTest {
        val api = SlowApi().apply {
            refreshGate = CompletableDeferred(); refreshReply = session(idA, "a@example.com")
            signIns["b@example.com"] = session(idB, "b@example.com")
        }
        val store = MemoryAccountSessionStore().also(::expiringA)
        val repo = AccountRepository(api, store, { time })
        val refreshA = launch { repo.resume(true) }
        testScheduler.runCurrent()
        repo.withLocalWriteIdentity { assertEquals(idA, it) }
        repo.logout(false)
        val loginB = launch { repo.login("b@example.com", "password42", true) }
        api.refreshGate!!.complete(Unit); refreshA.join(); loginB.join()
        val seen = mutableListOf<UUID?>()
        repo.withLocalWriteIdentity { seen += it }
        assertEquals(listOf<UUID?>(idB), seen)
        assertEquals("B's session replaced A's; A's late refresh did not win", idB, store.read().session!!.userId)
    }

    @Test fun loginSupersededByLogoutIsDiscardedAndRevokedRemotely() = runTest {
        val api = SlowApi().apply {
            signInGate = CompletableDeferred(); signIns["a@example.com"] = session(idA, "a@example.com")
        }
        val store = MemoryAccountSessionStore()
        val repo = AccountRepository(api, store, { time })
        val login = launch { repo.login("a@example.com", "password42", true) }
        testScheduler.runCurrent()
        repo.logout(false)
        api.signInGate!!.complete(Unit); login.join()
        assertNull(store.read().session)
        assertEquals("The discarded session is not left alive on the server", 1, api.signOuts)
        assertEquals(AccountStatus.SIGNED_OUT, repo.state.value.status)
        assertFalse(repo.state.value.busy)
    }

    @Test fun failedLocalWriteDuringRefreshIsNotRetriedAndDoesNotChangeIdentity() = runTest {
        val api = SlowApi().apply { refreshGate = CompletableDeferred() }
        val store = MemoryAccountSessionStore().also(::expiringA)
        val repo = AccountRepository(api, store, { time })
        val refresh = launch { repo.resume(true) }
        testScheduler.runCurrent()
        var calls = 0
        val failure = runCatching {
            repo.withLocalWriteIdentity { calls++; throw IllegalStateException("room write failed") }
        }.exceptionOrNull()
        assertTrue(failure is IllegalStateException)
        assertEquals(1, calls)
        assertEquals(idA, store.read().session!!.userId)
        api.refreshGate!!.complete(Unit); refresh.join()
        assertEquals(idA, store.read().session!!.userId)
    }
}

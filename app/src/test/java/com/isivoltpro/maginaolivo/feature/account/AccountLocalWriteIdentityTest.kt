package com.isivoltpro.maginaolivo.feature.account

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class AccountLocalWriteIdentityTest {
    private fun fixtureApi() = object : AccountAuthApi by SimulatedAccountAuthApi() {
        override val simulated = false
    }

    @Test fun offlineAttributionUsesCachedIdentityButSimulationAndLogoutDoNot() = runBlocking {
        val simulated = AccountRepository(SimulatedAccountAuthApi())
        simulated.login("a@example.test", "password42", true)
        simulated.withLocalWriteIdentity { assertNull(it) }
        val account = AccountRepository(fixtureApi())
        account.login("a@example.test", "password42", true)
        val expected = account.state.value.userId!!
        account.resume(false)
        account.withLocalWriteIdentity { assertEquals(expected, it) }
        account.logout(false)
        account.withLocalWriteIdentity { assertNull(it) }
    }

    @Test fun brokenIdentityFallsBackToLocalOnlyAndNeverRetriesTheWrite() = runBlocking {
        val broken = object : AccountSessionStore {
            override fun read(): AccountRecord = throw AuthFailure(AuthFailure.Kind.STORAGE)
            override fun write(record: AccountRecord) = Unit
            override fun clear() = Unit
        }
        val account = AccountRepository(fixtureApi(), broken)
        var calls = 0
        try {
            account.withLocalWriteIdentity {
                assertNull(it)
                calls++
                throw AuthFailure(AuthFailure.Kind.STORAGE)
            }
            fail("An action failure must propagate, not be swallowed or retried")
        } catch (_: AuthFailure) { }
        assertEquals(1, calls)
    }

    @Test fun logoutCannotReplaceIdentityDuringTheLocalTransaction() = runBlocking {
        val account = AccountRepository(fixtureApi())
        account.login("a@example.test", "password42", true)
        val expected = account.state.value.userId!!
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        val write = launch(start = CoroutineStart.UNDISPATCHED) {
            account.withLocalWriteIdentity {
                assertEquals(expected, it)
                entered.complete(Unit)
                release.await()
                assertEquals(expected, account.state.value.userId)
                events.add("write")
            }
        }
        entered.await()
        val logout = launch(start = CoroutineStart.UNDISPATCHED) { account.logout(false); events.add("logout") }
        assertFalse(logout.isCompleted)
        release.complete(Unit)
        write.join(); logout.join()
        assertEquals(listOf("write", "logout"), events)
        account.withLocalWriteIdentity { assertNull(it) }
    }
}

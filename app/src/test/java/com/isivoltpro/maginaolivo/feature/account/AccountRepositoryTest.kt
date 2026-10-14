package com.isivoltpro.maginaolivo.feature.account

import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class AccountRepositoryTest {
    private val time=Instant.parse("2026-10-14T12:00:00Z")
    private val id=UUID.fromString("00000000-0000-0000-0000-000000000042")
    private fun session(email:String="qa@example.com",expires:Instant=time.plusSeconds(3600)) = AuthSession(id,email,"private-access","private-refresh",expires)
    private inner class Api : AccountAuthApi {
        override val redirectUri="com.isivoltpro.maginaolivo.dev://auth/callback"
        override val simulated=true
        var refreshes=0;var exchanges=0;var changes=0;var logouts=0;var signedUpSession:AuthSession?=null
        var failure:AuthFailure?=null
        var reply=session()
        var userIdOverride:UUID?=null
        var barrier:CompletableDeferred<Unit>?=null
        override suspend fun signIn(email:String,password:String):AuthSession { barrier?.await();failure?.let { throw it };return reply }
        override suspend fun signUp(email:String,password:String,challenge:String):AuthSession? { assertTrue(challenge.length>=43);return signedUpSession }
        override suspend fun recover(email:String,challenge:String) { assertTrue(challenge.length>=43) }
        override suspend fun exchange(code:String,verifier:String):AuthSession { exchanges++;assertTrue(verifier.length>=43);failure?.let{throw it};return reply }
        override suspend fun refresh(session:AuthSession):AuthSession { refreshes++;failure?.let{throw it};return reply }
        override suspend fun user(session:AuthSession) = (userIdOverride ?: reply.userId) to reply.email
        override suspend fun changePassword(session:AuthSession,password:String) { changes++;failure?.let{throw it} }
        override suspend fun signOut(session:AuthSession) { logouts++;failure?.let{throw it} }
    }
    @Test fun loginAndOfflineLogoutPersistOnlyAuthAndDoNotLeakTokens() = runTest {
        val api=Api();val store=MemoryAccountSessionStore();val repo=AccountRepository(api,store,{time})
        repo.login(" qa@example.com ","password42",true)
        assertEquals(AccountStatus.SIGNED_IN,repo.state.value.status);assertNotNull(store.read().session)
        assertFalse(repo.state.value.toString().contains("private-"));assertTrue(repo.state.value.simulated)
        repo.logout(false)
        assertEquals(AccountStatus.SIGNED_OUT,repo.state.value.status);assertNull(store.read().session);assertEquals(0,api.logouts)
        assertTrue(repo.state.value.message!!.contains("teléfono"))
    }
    @Test fun confirmedEmailRequiredDoesNotBecomeLoginAndFailureIsVisible() = runTest {
        val api=Api();val store=MemoryAccountSessionStore();val repo=AccountRepository(api,store,{time})
        repo.register("qa@example.com","password42",true)
        assertNull(store.read().session);assertEquals(LinkPurpose.SIGNUP,store.read().pending!!.purpose)
        api.failure=AuthFailure(AuthFailure.Kind.CONFIRM_EMAIL)
        repo.login("qa@example.com","password42",true)
        assertNull(store.read().session);assertTrue(repo.state.value.error!!.contains("Confirma"));assertFalse(repo.state.value.busy)
    }
    @Test fun offlineRestorationDistinguishesCachedIdentityFromExpiredAccess() = runTest {
        val api=Api();val store=MemoryAccountSessionStore();store.write(AccountRecord(session=session()))
        val repo=AccountRepository(api,store,{time});repo.resume(false)
        assertEquals(AccountStatus.OFFLINE,repo.state.value.status);assertEquals(0,api.refreshes)
        val expired=MemoryAccountSessionStore();expired.write(AccountRecord(session=session(expires=time.minusSeconds(1))))
        AccountRepository(api,expired,{time}).also { it.resume(false);assertEquals(AccountStatus.EXPIRED,it.state.value.status) }
    }
    @Test fun refreshIsSerializedAndServerRevocationClearsOnlyCredentials() = runTest {
        val api=Api();val store=MemoryAccountSessionStore();store.write(AccountRecord(session=session(expires=time.minusSeconds(1))))
        val repo=AccountRepository(api,store,{time});repo.resume(true)
        assertEquals(1,api.refreshes);assertEquals(AccountStatus.SIGNED_IN,repo.state.value.status)
        val revoked=MemoryAccountSessionStore();revoked.write(AccountRecord(session=session(expires=time.minusSeconds(1))))
        api.failure=AuthFailure(AuthFailure.Kind.EXPIRED)
        AccountRepository(api,revoked,{time}).also { it.resume(true);assertNull(revoked.read().session);assertNotNull(it.state.value.error) }
    }
    @Test fun wrongIdentityCannotReplaceExpectedEmail() = runTest {
        val api=Api();api.reply=session("intruso@example.com")
        val store=MemoryAccountSessionStore();val repo=AccountRepository(api,store,{time})
        repo.login("qa@example.com","password42",true)
        assertNull(store.read().session);assertNotNull(repo.state.value.error)
    }
    @Test fun recoveryRequiresOwnPkceFlowAndSingleUseBeforePasswordChange() = runTest {
        val api=Api();val store=MemoryAccountSessionStore();val repo=AccountRepository(api,store,{time})
        repo.requestRecovery("qa@example.com",true)
        repo.completeLink("${api.redirectUri}?code=one-time-code",true)
        assertEquals(AccountStatus.RECOVERY,repo.state.value.status);assertNull(store.read().session)
        repo.completeLink("${api.redirectUri}?code=one-time-code",true)
        assertEquals(1,api.exchanges)
        repo.updatePassword("New-password42",true)
        assertEquals(1,api.changes);assertNull(store.read().recovery);assertEquals(AccountStatus.SIGNED_OUT,repo.state.value.status)
    }
    @Test fun malformedForeignExpiredAndTokenCallbacksNeverExchange() = runTest {
        val api=Api();val store=MemoryAccountSessionStore();val repo=AccountRepository(api,store,{time})
        val invalid=listOf("https://evil.test/auth/callback?code=x", "${api.redirectUri}?code=x&code=y", "${api.redirectUri}#access_token=secret", "${api.redirectUri}?access_token=secret", "${api.redirectUri}?code=x&redirect_to=evil")
        for(url in invalid) { repo.requestRecovery("qa@example.com",true);repo.completeLink(url,true) }
        assertEquals(0,api.exchanges);assertNull(store.read().session)
        store.write(AccountRecord(pending=PendingAuthLink("qa@example.com",LinkPurpose.RECOVERY,"v".repeat(43),time.minusSeconds(7200))))
        AccountRepository(api,store,{time}).completeLink("${api.redirectUri}?code=x",true)
        assertEquals(0,api.exchanges)
    }
    @Test fun failedRemoteLogoutStillRemovesLocalSession() = runTest {
        val api=Api();val store=MemoryAccountSessionStore();val repo=AccountRepository(api,store,{time})
        repo.login("qa@example.com","password42",true);api.failure=AuthFailure(AuthFailure.Kind.NETWORK);repo.logout(true)
        assertNull(store.read().session);assertEquals(AccountStatus.SIGNED_OUT,repo.state.value.status)
        assertTrue(repo.state.value.message!!.contains("revocación"))
    }
    @Test fun storageFailureDoesNotReportAuthenticatedSession() = runTest {
        val store=object:AccountSessionStore {
            override fun read()=AccountRecord()
            override fun write(record:AccountRecord) { throw java.io.IOException("sensitive storage error") }
            override fun clear() {}
        }
        val repo=AccountRepository(Api(),store,{time});repo.login("qa@example.com","password42",true)
        assertEquals(AccountStatus.SIGNED_OUT,repo.state.value.status);assertFalse(repo.state.value.error!!.contains("sensitive"))
    }
    @Test fun logoutQueuedDuringLoginCannotBeUndoneByLateResponse() = runTest {
        val api=Api();api.barrier=CompletableDeferred();val store=MemoryAccountSessionStore();val repo=AccountRepository(api,store,{time})
        val login=launch { repo.login("qa@example.com","password42",true) }
        val logout=launch { repo.logout(false) }
        api.barrier!!.complete(Unit);login.join();logout.join()
        assertNull(store.read().session);assertEquals(AccountStatus.SIGNED_OUT,repo.state.value.status)
    }
    @Test fun onlineIdentityMismatchFailsClosed() = runTest {
        val api=Api();api.userIdOverride=UUID.randomUUID();val store=MemoryAccountSessionStore();store.write(AccountRecord(session=session()))
        val repo=AccountRepository(api,store,{time});repo.resume(true)
        assertNull(store.read().session);assertEquals(AccountStatus.SIGNED_OUT,repo.state.value.status);assertNotNull(repo.state.value.error)
    }
    @Test fun refreshAcceptsChangedEmailOnlyForTheSameUserId() = runTest {
        val api=Api();api.reply=session("nuevo@example.com");val store=MemoryAccountSessionStore();store.write(AccountRecord(session=session(expires=time.minusSeconds(1))))
        val repo=AccountRepository(api,store,{time});repo.resume(true)
        assertEquals(AccountStatus.SIGNED_IN,repo.state.value.status);assertEquals("nuevo@example.com",store.read().session!!.email)
    }
}

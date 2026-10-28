package com.isivoltpro.maginaolivo.feature.account

import java.time.Instant
import java.net.URI
import java.net.URLDecoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** No agricultural repository, database, file store or outbox is a dependency. */
class AccountRepository(private val api: AccountAuthApi?, private val store: AccountSessionStore = MemoryAccountSessionStore(),
    private val now: () -> Instant = Instant::now) {
    private val mutable = MutableStateFlow(AccountState(status=if(api==null) AccountStatus.UNCONFIGURED else AccountStatus.SIGNED_OUT, simulated=api?.simulated==true))
    val state: StateFlow<AccountState> = mutable
    // Two locks, always taken in this order and never nested the other way round:
    //  - [network] serializes Auth flows (login, refresh, link exchange). It is held across HTTP.
    //  - [local] guards the persisted identity and is held only for short local sections: reading
    //    or replacing the session, and the Room writes that attribute field data to it.
    // Field work therefore never waits for an HTTP call; a late HTTP result commits only if the
    // identity [generation] it started from is still current (logout/account change bump it).
    private val network = Mutex()
    private val local = Mutex()
    @Volatile private var record: AccountRecord? = null
    private var generation = 0L
    private val inFlight = java.util.concurrent.atomic.AtomicInteger()
    // This repository is the process Auth owner; a recreated Activity must not cancel exchange.
    private val callbackScope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    fun receiveLink(url:String,online:Boolean) = callbackScope.launch { completeLink(url,online) }
    fun requestLogout(online:Boolean) = callbackScope.launch { logout(online) }
    /** Cached identity from the Auth owner. Offline use is valid only before token expiry. */
    suspend fun workspaceSession(): WorkspaceSession = local.withLock {
        val current = current()
        if (api == null || current.recovery != null || api.simulated) return@withLock WorkspaceSession.SignedOut
        val session = current.session ?: return@withLock WorkspaceSession.SignedOut
        if (!session.expiresAt.isAfter(now())) WorkspaceSession.Expired
        else WorkspaceSession.Authenticated(session.userId)
    }
    internal suspend fun <T> withCachedLocalAccess(action: suspend (java.util.UUID?) -> T): T = local.withLock {
        action(if(api?.simulated==true) null else current().session?.userId)
    }

    /** Local attribution only, not fresh Auth or permission to send. Do not retry the action.
     * Never waits for Auth HTTP: only logout/account replacement and other local writes exclude it. */
    internal suspend fun <T> withLocalWriteIdentity(action: suspend (java.util.UUID?) -> T): T = local.withLock {
        val id = try { if (api == null || api.simulated) null else current().session?.userId }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { null }
        action(id)
    }

    suspend fun resume(online: Boolean) {
        if(api==null) { mutable.value=mutable.value.copy(online=online);return }
        operation(online,false) { start ->
        val current = local.withLock { current().session } ?: return@operation
        if (!online) return@operation
        var expected = start
        try {
            val valid = if (!current.expiresAt.isAfter(now().plusSeconds(30))) {
                requireApi().refresh(current).also { checkIdentity(it,id=current.userId);expected=commitOrSuperseded(expected,AccountRecord(session=it)) }
            } else current
            val (id,email) = requireApi().user(valid)
            if (id != valid.userId || !AccountValidation.email(email)) throw AuthFailure(AuthFailure.Kind.PROTOCOL)
            if (email != valid.email) commitOrSuperseded(expected,AccountRecord(session=AuthSession(id,email,valid.accessToken,valid.refreshToken,valid.expiresAt)))
        } catch (e: AuthFailure) {
            // A stale failure (the user logged out or changed account meanwhile) never clears the newer identity.
            if (e.kind in setOf(AuthFailure.Kind.EXPIRED,AuthFailure.Kind.CREDENTIALS,AuthFailure.Kind.PROTOCOL)) commitOrSuperseded(expected,AccountRecord())
            throw e
        }
        }
    }
    fun clearNotice() { mutable.value=mutable.value.copy(error=null,message=null) }
    /** Serializes local consent with logout/account replacement; recovery tokens are excluded. */
    internal suspend fun <T> withLocalIdentity(requireFresh: Boolean = true, action: suspend (java.util.UUID) -> T): T = local.withLock {
        if (requireApi().simulated) throw AuthFailure(AuthFailure.Kind.PROTOCOL)
        val identity = current().session ?: throw AuthFailure(AuthFailure.Kind.CREDENTIALS)
        if (requireFresh && !identity.expiresAt.isAfter(now())) throw AuthFailure(AuthFailure.Kind.EXPIRED)
        action(identity.userId)
    }
    /**
     * Explicit reconciliation only. The server confirms whom the stored session belongs to, then
     * [remote] runs authenticated reads with it while no local lock is held, and [commit] runs
     * under the local identity lock only if no logout or account change happened meanwhile.
     * Never refreshes or changes the stored identity, and never extends offline attribution.
     */
    internal suspend fun <P, T> withServerVerifiedIdentity(
        remote: suspend (AuthSession) -> P,
        commit: suspend (java.util.UUID, P) -> T,
    ): T = network.withLock {
        val api = requireApi()
        if (api.simulated) throw AuthFailure(AuthFailure.Kind.PROTOCOL)
        val (start, session) = local.withLock { generation to current().session }
        if (session == null) throw AuthFailure(AuthFailure.Kind.CREDENTIALS)
        if (!session.expiresAt.isAfter(now())) throw AuthFailure(AuthFailure.Kind.EXPIRED)
        val (id, _) = api.user(session)
        if (id != session.userId) throw AuthFailure(AuthFailure.Kind.PROTOCOL)
        val prepared = remote(session)
        local.withLock {
            if (generation != start) throw AuthFailure(AuthFailure.Kind.CREDENTIALS)
            commit(id, prepared)
        }
    }
    suspend fun simulateLink(online:Boolean) { if(api?.simulated==true)completeLink("${api.redirectUri}?code=simulated-code",online) }
    suspend fun login(email: String, password: String, online: Boolean) = operation(online,true) { start ->
        val normalized = checkedEmail(email)
        if (password.isBlank() || password.length > 1024) throw AuthFailure(AuthFailure.Kind.CREDENTIALS)
        val session = requireApi().signIn(normalized,password)
        checkIdentity(session,normalized)
        saveSession(start,session)
        notice("Sesión iniciada. Las fincas de este teléfono no se han vinculado a la cuenta.")
    }
    suspend fun register(email: String, password: String, online: Boolean) = operation(online,true) { start ->
        val normalized = checkedEmail(email);checkedPassword(password);local.withLock { requireSignedOut() }
        val pending = newLink(normalized,LinkPurpose.SIGNUP)
        val expected = commitOrSuperseded(start,AccountRecord(pending=pending)) // Crash-safe before the email can be sent.
        val session = requireApi().signUp(normalized,password,challenge(pending.verifier))
        if (session != null) { checkIdentity(session,normalized);saveSession(expected,session);notice("Sesión iniciada. No se han vinculado fincas.") }
        else notice(if(requireApi().simulated) "Simulación: confirmación preparada; no se envía correo." else "Revisa tu correo y abre el enlace en este dispositivo para confirmar el registro.")
    }
    suspend fun requestRecovery(email: String, online: Boolean) = operation(online,true) { start ->
        val normalized = checkedEmail(email);local.withLock { requireSignedOut() }
        val pending = newLink(normalized,LinkPurpose.RECOVERY)
        commitOrSuperseded(start,AccountRecord(pending=pending))
        requireApi().recover(normalized,challenge(pending.verifier))
        notice(if(requireApi().simulated) "Simulación: recuperación preparada; no se envía correo." else "Si existe esa cuenta, recibirás un enlace. Ábrelo en este dispositivo para elegir una nueva contraseña.")
    }
    suspend fun completeLink(url: String, online: Boolean) = operation(online,true) { start ->
        val snapshot = local.withLock { current() }
        val pending = snapshot.pending ?: throw AuthFailure(AuthFailure.Kind.EXPIRED)
        if (pending.createdAt.isAfter(now()) || !pending.createdAt.plusSeconds(3600).isAfter(now())) throw AuthFailure(AuthFailure.Kind.EXPIRED)
        val code = callbackCode(url)
        // Consume locally before exchange: after an uncertain network result restart the flow.
        val expected = commitOrSuperseded(start,AccountRecord(session=snapshot.session,recovery=snapshot.recovery))
        val session = requireApi().exchange(code,pending.verifier)
        checkIdentity(session,pending.email)
        if (pending.purpose == LinkPurpose.RECOVERY) {
            commitOrSuperseded(expected,AccountRecord(recovery=session));notice("Enlace verificado. Elige una nueva contraseña.")
        } else { saveSession(expected,session);notice("Correo confirmado. Sesión iniciada sin vincular fincas.") }
    }
    suspend fun updatePassword(password: String, online: Boolean) = operation(online,true) { start ->
        checkedPassword(password)
        val recovery = local.withLock { current().recovery } ?: throw AuthFailure(AuthFailure.Kind.EXPIRED)
        if (!recovery.expiresAt.isAfter(now())) throw AuthFailure(AuthFailure.Kind.EXPIRED)
        requireApi().changePassword(recovery,password)
        val revoked = revoke(recovery)
        commitOrSuperseded(start,null)
        notice("Contraseña actualizada. Inicia sesión de nuevo." + if(revoked) "" else " No se pudo confirmar la revocación remota.")
    }
    /** Local sign-out is immediate and never waits for an Auth request in flight; that request's
     * late result is discarded by the generation check. Remote revocation happens afterwards. */
    suspend fun logout(online: Boolean) = busy(online) {
        try {
            val previous = local.withLock {
                val previous = runCatching { current().session ?: current().recovery }.getOrNull()
                persist(null);publish()
                previous
            }
            val revoked = previous == null || (online && revoke(previous))
            notice(if(revoked) "Sesión cerrada en este teléfono. Tus datos de campo se conservan." else "Sesión cerrada en este teléfono. No se pudo confirmar la revocación remota; tus datos de campo se conservan.")
        } catch(e: CancellationException) { throw e }
        catch (_: Exception) { mutable.value=mutable.value.copy(error=AuthFailure(AuthFailure.Kind.STORAGE).message) }
    }
    fun acceptsLink(url: String): Boolean = runCatching {
        val uri=URI(url);val expected=URI(requireApi().redirectUri)
        uri.scheme==expected.scheme && uri.host==expected.host && uri.rawPath==expected.rawPath && uri.port == -1 && uri.userInfo==null
    }.getOrDefault(false)

    private suspend fun operation(online: Boolean, network: Boolean, block: suspend (Long) -> Unit) = this.network.withLock {
        busy(online) {
            try {
                requireApi()
                val start = local.withLock { current();generation }
                if(network && !online) throw AuthFailure(AuthFailure.Kind.NETWORK)
                block(start)
            } catch(e: CancellationException) { throw e }
            catch(_: SupersededAuthResult) { }
            catch(e: AuthFailure) { mutable.value=mutable.value.copy(error=e.message) }
            catch (_: Exception) { mutable.value=mutable.value.copy(error=AuthFailure(AuthFailure.Kind.STORAGE).message) }
        }
    }
    private suspend fun <T> busy(online: Boolean, block: suspend () -> T): T {
        inFlight.incrementAndGet()
        mutable.value=mutable.value.copy(busy=true,online=online,error=null,message=null)
        try { return block() }
        finally {
            val remaining = inFlight.decrementAndGet()
            local.withLock { publish() }
            mutable.value=mutable.value.copy(busy=remaining>0)
        }
    }
    /** An Auth result that started from an identity generation that is no longer current. */
    private class SupersededAuthResult : Exception()
    /** Persists [value] (null clears) only if no identity change happened since [expected]. */
    private suspend fun commitOrSuperseded(expected: Long, value: AccountRecord?): Long = local.withLock {
        if (generation != expected) throw SupersededAuthResult()
        persist(value) // Published when the operation completes: intermediate states are not navigation signals.
        generation
    }
    /** Caller holds [local]. Every persisted identity change starts a new generation. */
    private fun persist(value: AccountRecord?) {
        if (value == null) store.clear() else store.write(value)
        record = value ?: AccountRecord()
        generation++
    }
    private fun requireApi() = api ?: throw AuthFailure(AuthFailure.Kind.CONFIGURATION)
    /** Caller holds [local]. */
    private fun current(): AccountRecord = record ?: store.read().also { record=it }
    private suspend fun saveSession(expected: Long, session: AuthSession) {
        try { commitOrSuperseded(expected,AccountRecord(session=session)) }
        catch (superseded: SupersededAuthResult) { revoke(session);throw superseded }
        catch (_: Exception) { revoke(session);throw AuthFailure(AuthFailure.Kind.STORAGE) }
    }
    private suspend fun revoke(session: AuthSession): Boolean = try {
        val valid = if (session.expiresAt.isAfter(now())) session else requireApi().refresh(session).also { checkIdentity(it,id=session.userId) }
        requireApi().signOut(valid);true
    } catch(e: CancellationException) { throw e }
    catch (_: Exception) { false }
    private fun notice(text: String) { mutable.value=mutable.value.copy(message=text);publish() }
    private fun publish() {
        val current=record ?: AccountRecord()
        val session=current.session ?: current.recovery
        val status=when {
            api==null -> AccountStatus.UNCONFIGURED
            session==null -> AccountStatus.SIGNED_OUT
            !session.expiresAt.isAfter(now()) -> AccountStatus.EXPIRED
            current.recovery!=null -> AccountStatus.RECOVERY
            !mutable.value.online -> AccountStatus.OFFLINE
            else -> AccountStatus.SIGNED_IN
        }
        mutable.value=mutable.value.copy(status=status,email=session?.email,pendingPurpose=current.pending?.purpose,userId=current.session?.userId)
    }
    /** Caller holds [local]. */
    private fun requireSignedOut() { if(current().session!=null) throw AuthFailure(AuthFailure.Kind.PROTOCOL) }
    private fun checkedEmail(value: String) = value.trim().also { if(!AccountValidation.email(it)) throw AuthFailure(AuthFailure.Kind.CREDENTIALS) }
    private fun checkedPassword(value: String) { if(!AccountValidation.password(value)) throw AuthFailure(AuthFailure.Kind.CREDENTIALS) }
    private fun checkIdentity(session: AuthSession,email: String?=null,id: java.util.UUID?=null) {
        if(!AccountValidation.email(session.email) || (email!=null && !session.email.equals(email,ignoreCase=true)) || (id!=null && session.userId!=id) || !session.expiresAt.isAfter(now())) throw AuthFailure(AuthFailure.Kind.PROTOCOL)
    }
    private fun newLink(email: String,purpose: LinkPurpose): PendingAuthLink {
        val bytes=ByteArray(32).also { SecureRandom().nextBytes(it) }
        return PendingAuthLink(email,purpose,Base64.getUrlEncoder().withoutPadding().encodeToString(bytes),now())
    }
    private fun challenge(verifier: String) = Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))
    private fun callbackCode(url: String): String {
        if(url.length>4096 || !acceptsLink(url)) throw AuthFailure(AuthFailure.Kind.PROTOCOL)
        val uri=URI(url)
        if(uri.rawFragment!=null) throw AuthFailure(AuthFailure.Kind.PROTOCOL)
        val parts=uri.rawQuery?.split('&') ?: throw AuthFailure(AuthFailure.Kind.PROTOCOL)
        if(parts.size!=1 || !parts.single().startsWith("code=")) throw AuthFailure(AuthFailure.Kind.PROTOCOL)
        return URLDecoder.decode(parts.single().substringAfter('='),"UTF-8").also {
            if(!Regex("^[A-Za-z0-9_-]{1,2048}$").matches(it)) throw AuthFailure(AuthFailure.Kind.PROTOCOL)
        }
    }
}

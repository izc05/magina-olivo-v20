package com.isivoltpro.maginaolivo.feature.account

import java.time.Instant
import java.net.URI
import java.net.URLDecoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** No agricultural repository, database, file store or outbox is a dependency. */
class AccountRepository(private val api: AccountAuthApi?, private val store: AccountSessionStore = MemoryAccountSessionStore(),
    private val now: () -> Instant = Instant::now) {
    private val mutable = MutableStateFlow(AccountState(status=if(api==null) AccountStatus.UNCONFIGURED else AccountStatus.SIGNED_OUT, simulated=api?.simulated==true))
    val state: StateFlow<AccountState> = mutable
    private val mutex = Mutex()
    private var record: AccountRecord? = null

    suspend fun resume(online: Boolean) = operation(online,false) {
        val current = current().session ?: return@operation
        if (!online) return@operation
        try {
            val valid = if (!current.expiresAt.isAfter(now().plusSeconds(30))) {
                requireApi().refresh(current).also { checkIdentity(it,id=current.userId);save(AccountRecord(session=it)) }
            } else current
            val (id,email) = requireApi().user(valid)
            if (id != valid.userId || !AccountValidation.email(email)) throw AuthFailure(AuthFailure.Kind.PROTOCOL)
            if (email != valid.email) save(AccountRecord(session=AuthSession(id,email,valid.accessToken,valid.refreshToken,valid.expiresAt)))
        } catch (e: AuthFailure) {
            if (e.kind in setOf(AuthFailure.Kind.EXPIRED,AuthFailure.Kind.CREDENTIALS,AuthFailure.Kind.PROTOCOL)) save(AccountRecord())
            throw e
        }
    }
    suspend fun login(email: String, password: String, online: Boolean) = operation(online,true) {
        val normalized = checkedEmail(email)
        if (password.isBlank() || password.length > 1024) throw AuthFailure(AuthFailure.Kind.CREDENTIALS)
        val session = requireApi().signIn(normalized,password)
        checkIdentity(session,normalized)
        saveSession(session)
        notice("Sesión iniciada. Las fincas de este teléfono no se han vinculado a la cuenta.")
    }
    suspend fun register(email: String, password: String, online: Boolean) = operation(online,true) {
        val normalized = checkedEmail(email);checkedPassword(password);requireSignedOut()
        val pending = newLink(normalized,LinkPurpose.SIGNUP)
        save(AccountRecord(pending=pending)) // Crash-safe before the email can be sent.
        val session = requireApi().signUp(normalized,password,challenge(pending.verifier))
        if (session != null) { checkIdentity(session,normalized);saveSession(session);notice("Sesión iniciada. No se han vinculado fincas.") }
        else notice(if(requireApi().simulated) "Simulación: confirmación preparada; no se envía correo." else "Revisa tu correo y abre el enlace en este dispositivo para confirmar el registro.")
    }
    suspend fun requestRecovery(email: String, online: Boolean) = operation(online,true) {
        val normalized = checkedEmail(email);requireSignedOut()
        val pending = newLink(normalized,LinkPurpose.RECOVERY)
        save(AccountRecord(pending=pending))
        requireApi().recover(normalized,challenge(pending.verifier))
        notice(if(requireApi().simulated) "Simulación: recuperación preparada; no se envía correo." else "Si existe esa cuenta, recibirás un enlace. Ábrelo en este dispositivo para elegir una nueva contraseña.")
    }
    suspend fun completeLink(url: String, online: Boolean) = operation(online,true) {
        val pending = current().pending ?: throw AuthFailure(AuthFailure.Kind.EXPIRED)
        if (pending.createdAt.isAfter(now()) || !pending.createdAt.plusSeconds(3600).isAfter(now())) throw AuthFailure(AuthFailure.Kind.EXPIRED)
        val code = callbackCode(url)
        // Consume locally before exchange: after an uncertain network result restart the flow.
        save(AccountRecord(session=current().session,recovery=current().recovery))
        val session = requireApi().exchange(code,pending.verifier)
        checkIdentity(session,pending.email)
        if (pending.purpose == LinkPurpose.RECOVERY) {
            save(AccountRecord(recovery=session));notice("Enlace verificado. Elige una nueva contraseña.")
        } else { saveSession(session);notice("Correo confirmado. Sesión iniciada sin vincular fincas.") }
    }
    suspend fun updatePassword(password: String, online: Boolean) = operation(online,true) {
        checkedPassword(password)
        val recovery = current().recovery ?: throw AuthFailure(AuthFailure.Kind.EXPIRED)
        if (!recovery.expiresAt.isAfter(now())) throw AuthFailure(AuthFailure.Kind.EXPIRED)
        requireApi().changePassword(recovery,password)
        val revoked = revoke(recovery)
        store.clear();record=AccountRecord()
        notice("Contraseña actualizada. Inicia sesión de nuevo." + if(revoked) "" else " No se pudo confirmar la revocación remota.")
    }
    suspend fun logout(online: Boolean) = mutex.withLock {
        mutable.value=mutable.value.copy(busy=true,online=online,error=null,message=null)
        try {
            val previous = runCatching { current().session ?: current().recovery }.getOrNull()
            store.clear();record=AccountRecord();publish()
            val revoked = previous == null || (online && revoke(previous))
            notice(if(revoked) "Sesión cerrada en este teléfono. Tus datos de campo se conservan." else "Sesión cerrada en este teléfono. No se pudo confirmar la revocación remota; tus datos de campo se conservan.")
        } catch(e: CancellationException) { throw e }
        catch (_: Exception) { mutable.value=mutable.value.copy(error=AuthFailure(AuthFailure.Kind.STORAGE).message) }
        finally { publish();mutable.value=mutable.value.copy(busy=false) }
    }
    fun acceptsLink(url: String): Boolean = runCatching {
        val uri=URI(url);val expected=URI(requireApi().redirectUri)
        uri.scheme==expected.scheme && uri.host==expected.host && uri.rawPath==expected.rawPath && uri.port == -1 && uri.userInfo==null
    }.getOrDefault(false)

    private suspend fun operation(online: Boolean, network: Boolean, block: suspend () -> Unit) = mutex.withLock {
        mutable.value=mutable.value.copy(busy=true,online=online,error=null,message=null)
        try {
            requireApi();current()
            if(network && !online) throw AuthFailure(AuthFailure.Kind.NETWORK)
            block()
        } catch(e: CancellationException) { throw e }
        catch(e: AuthFailure) { mutable.value=mutable.value.copy(error=e.message) }
        catch (_: Exception) { mutable.value=mutable.value.copy(error=AuthFailure(AuthFailure.Kind.STORAGE).message) }
        finally { publish();mutable.value=mutable.value.copy(busy=false) }
    }
    private fun requireApi() = api ?: throw AuthFailure(AuthFailure.Kind.CONFIGURATION)
    private fun current(): AccountRecord = record ?: store.read().also { record=it }
    private fun save(value: AccountRecord) { store.write(value);record=value }
    private suspend fun saveSession(session: AuthSession) {
        try { save(AccountRecord(session=session)) }
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
        mutable.value=mutable.value.copy(status=status,email=session?.email)
    }
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

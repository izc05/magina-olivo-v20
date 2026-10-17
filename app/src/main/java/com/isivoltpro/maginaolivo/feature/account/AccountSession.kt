package com.isivoltpro.maginaolivo.feature.account

import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.StateFlow

enum class AccountStatus { UNCONFIGURED, SIGNED_OUT, CHECKING, SIGNED_IN, OFFLINE, EXPIRED, RECOVERY }
data class AccountState(
    val status: AccountStatus = AccountStatus.SIGNED_OUT,
    val email: String? = null,
    val busy: Boolean = false,
    val online: Boolean = true,
    val message: String? = null,
    val error: String? = null,
    val simulated: Boolean = false,
    val pendingPurpose: LinkPurpose? = null,
    val userId: UUID? = null,
)

/** Credentials deliberately have no generated data-class toString/copy/component methods. */
class AuthSession(val userId: UUID, val email: String, internal val accessToken: String,
    internal val refreshToken: String, val expiresAt: Instant) {
    override fun toString() = "AuthSession(credentials=redacted)"
}
enum class LinkPurpose { SIGNUP, RECOVERY }
class PendingAuthLink(val email: String, val purpose: LinkPurpose, internal val verifier: String, val createdAt: Instant) {
    override fun toString() = "PendingAuthLink(verifier=redacted)"
}
class AccountRecord(val session: AuthSession? = null, val pending: PendingAuthLink? = null, val recovery: AuthSession? = null) {
    override fun toString() = "AccountRecord(credentials=redacted)"
}
interface AccountSessionStore {
    fun read(): AccountRecord
    fun write(record: AccountRecord)
    fun clear()
}
class MemoryAccountSessionStore : AccountSessionStore {
    private var record = AccountRecord()
    override fun read() = record
    override fun write(record: AccountRecord) { this.record = record }
    override fun clear() { record = AccountRecord() }
}
class AuthFailure(val kind: Kind) : Exception(when(kind) {
    Kind.CREDENTIALS -> "Correo o contraseña incorrectos."
    Kind.CONFIRM_EMAIL -> "Confirma tu correo antes de iniciar sesión."
    Kind.RATE_LIMIT -> "Demasiados intentos. Espera unos minutos y vuelve a probar."
    Kind.NETWORK -> "No se pudo conectar. Tus datos de campo siguen en este teléfono."
    Kind.EXPIRED -> "El acceso o enlace ha caducado. Vuelve a iniciar el proceso."
    Kind.PROTOCOL -> "No se pudo validar la respuesta de Cuenta."
    Kind.STORAGE -> "No se pudo guardar o recuperar la sesión de forma segura."
    Kind.CONFIGURATION -> "El acceso de pruebas todavía no está configurado."
}) { enum class Kind { CREDENTIALS, CONFIRM_EMAIL, RATE_LIMIT, NETWORK, EXPIRED, PROTOCOL, STORAGE, CONFIGURATION } }

interface AccountAuthApi {
    val redirectUri: String
    val simulated: Boolean get() = false
    suspend fun signIn(email: String, password: String): AuthSession
    suspend fun signUp(email: String, password: String, challenge: String): AuthSession?
    suspend fun recover(email: String, challenge: String)
    suspend fun exchange(code: String, verifier: String): AuthSession
    suspend fun refresh(session: AuthSession): AuthSession
    suspend fun user(session: AuthSession): Pair<UUID, String>
    suspend fun changePassword(session: AuthSession, password: String)
    suspend fun signOut(session: AuthSession)
}

internal object AccountValidation {
    fun email(value: String): Boolean = value.trim().let {
        it.length <= 254 && Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$").matches(it)
    }
    fun password(value: String): Boolean = value.length in 8..128
}

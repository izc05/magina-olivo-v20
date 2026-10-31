package com.isivoltpro.maginaolivo.feature.account

import java.time.Instant
import java.util.UUID
import java.util.Base64
import java.net.URI
import java.net.URLEncoder
import kotlinx.serialization.json.*

class AuthConfiguration(val projectUrl: String, internal val publicKey: String, val redirectUri: String) {
    override fun toString() = "AuthConfiguration(key=redacted)"
    companion object {
        fun forEnvironment(environment: String, url: String, key: String, applicationId: String): AuthConfiguration? {
            if (environment !in setOf("DEV", "STAGING", "PRODUCTION") || url.isBlank() || key.isBlank()) return null
            val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return null
            if (uri.scheme != "https" || uri.port != -1 || uri.userInfo != null || uri.rawQuery != null || uri.rawFragment != null || uri.path !in listOf("", "/")) return null
            val host = uri.host ?: return null
            if (!Regex("^[a-z]{20}\\.supabase\\.co$").matches(host) || host.substringBefore('.') in setOf("zzelvbcuxsboafibfxch", "klchkjsdybbgritxkswp")) return null
            val publicKey = key.trim()
            val valid = if (publicKey.startsWith("sb_publishable_")) Regex("^sb_publishable_[A-Za-z0-9_-]+$").matches(publicKey) else runCatching {
                val parts = publicKey.split('.')
                parts.size == 3 && Json.parseToJsonElement(String(Base64.getUrlDecoder().decode(parts[1]))).jsonObject["role"]?.jsonPrimitive?.content == "anon"
            }.getOrDefault(false)
            if (!valid || !Regex("^[A-Za-z0-9.]+$").matches(applicationId)) return null
            return AuthConfiguration("https://$host", publicKey, "$applicationId://auth/callback")
        }
    }
}
class AuthHttpResponse(val status: Int, internal val body: String) {
    override fun toString() = "AuthHttpResponse(status=$status, body=redacted)"
}
fun interface AuthHttpTransport {
    suspend fun request(method: String, path: String, body: String?, accessToken: String?): AuthHttpResponse
}
class SupabaseAuthApi(private val configuration: AuthConfiguration, private val transport: AuthHttpTransport,
    private val now: () -> Instant = Instant::now) : AccountAuthApi {
    override val redirectUri = configuration.redirectUri
    override suspend fun signIn(email: String, password: String): AuthSession = session(call("POST", "/token?grant_type=password", json("email" to email, "password" to password)))
    override suspend fun signUp(email: String, password: String, challenge: String): AuthSession? {
        val response = call("POST", "/signup$redirect", json("email" to email, "password" to password, "code_challenge" to challenge, "code_challenge_method" to "s256"))
        return if (response["access_token"] != null) session(response) else {
            // Confirm-required/signup obfuscation is not evidence of an authenticated user.
            if (response["id"] == null && response["user"] == null) throw AuthFailure(AuthFailure.Kind.PROTOCOL)
            null
        }
    }
    override suspend fun recover(email: String, challenge: String) {
        call("POST", "/recover$redirect", json("email" to email, "code_challenge" to challenge, "code_challenge_method" to "s256"))
    }
    override suspend fun exchange(code: String, verifier: String): AuthSession = session(call("POST", "/token?grant_type=pkce", json("auth_code" to code, "code_verifier" to verifier)))
    override suspend fun refresh(session: AuthSession): AuthSession = session(call("POST", "/token?grant_type=refresh_token", json("refresh_token" to session.refreshToken)))
    override suspend fun user(session: AuthSession): Pair<UUID, String> = identity(call("GET", "/user", token = session.accessToken))
    override suspend fun changePassword(session: AuthSession, password: String) { call("PUT", "/user", json("password" to password), session.accessToken) }
    override suspend fun signOut(session: AuthSession) { call("POST", "/logout?scope=local", token = session.accessToken) }

    private val redirect get() = "?redirect_to=" + URLEncoder.encode(redirectUri, "UTF-8")
    private fun json(vararg pairs: Pair<String,String>) = buildJsonObject { pairs.forEach { put(it.first,it.second) } }.toString()
    private suspend fun call(method: String, path: String, body: String? = null, token: String? = null): JsonObject {
        val result = transport.request(method,path,body,token)
        val parsed = if (result.body.isBlank()) JsonObject(emptyMap()) else runCatching { Json.parseToJsonElement(result.body).jsonObject }.getOrNull()
        if (result.status !in 200..299) {
            val code = parsed?.get("error_code")?.jsonPrimitive?.contentOrNull
            throw AuthFailure(when {
                result.status == 429 -> AuthFailure.Kind.RATE_LIMIT
                result.status >= 500 -> AuthFailure.Kind.NETWORK
                code == "email_not_confirmed" -> AuthFailure.Kind.CONFIRM_EMAIL
                code == "invalid_credentials" -> AuthFailure.Kind.CREDENTIALS
                result.status == 401 || code in setOf("otp_expired", "refresh_token_not_found", "refresh_token_already_used", "bad_code_verifier") -> AuthFailure.Kind.EXPIRED
                else -> AuthFailure.Kind.PROTOCOL
            })
        }
        return parsed ?: throw AuthFailure(AuthFailure.Kind.PROTOCOL)
    }
    private fun identity(json: JsonObject): Pair<UUID,String> = try {
        check(json["is_anonymous"]?.jsonPrimitive?.booleanOrNull != true)
        UUID.fromString(json.getValue("id").jsonPrimitive.content) to json.getValue("email").jsonPrimitive.content.also { check(AccountValidation.email(it)) }
    } catch (_: Exception) { throw AuthFailure(AuthFailure.Kind.PROTOCOL) }
    private fun session(json: JsonObject): AuthSession = try {
        check(json.getValue("token_type").jsonPrimitive.content == "bearer")
        val access = json.getValue("access_token").jsonPrimitive.content.also { check(it.isNotBlank()) }
        val refresh = json.getValue("refresh_token").jsonPrimitive.content.also { check(it.isNotBlank()) }
        val duration = json.getValue("expires_in").jsonPrimitive.long.also { check(it in 1..604800) }
        val (id,email) = identity(json.getValue("user").jsonObject)
        AuthSession(id,email,access,refresh,now().plusSeconds(duration))
    } catch (_: Exception) { throw AuthFailure(AuthFailure.Kind.PROTOCOL) }
}

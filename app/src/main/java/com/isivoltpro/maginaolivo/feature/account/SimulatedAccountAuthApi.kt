package com.isivoltpro.maginaolivo.feature.account

import java.time.Instant
import java.util.UUID

/** Explicit opt-in DEV simulation. No networking, real users, email or password persistence. */
class SimulatedAccountAuthApi(override val redirectUri:String="com.isivoltpro.maginaolivo.dev://auth/callback"):AccountAuthApi {
    override val simulated=true
    private var pendingEmail="synthetic@example.test"
    private fun session(email:String)=AuthSession(UUID.nameUUIDFromBytes(email.toByteArray(Charsets.UTF_8)),email,"synthetic-access","synthetic-refresh",Instant.now().plusSeconds(3600))
    override suspend fun signIn(email:String,password:String)=session(email)
    override suspend fun signUp(email:String,password:String,challenge:String):AuthSession? { pendingEmail=email;return null }
    override suspend fun recover(email:String,challenge:String) { pendingEmail=email }
    override suspend fun exchange(code:String,verifier:String)=session(pendingEmail)
    override suspend fun refresh(session:AuthSession)=session(session.email)
    override suspend fun user(session:AuthSession)=session.userId to session.email
    override suspend fun changePassword(session:AuthSession,password:String) {}
    override suspend fun signOut(session:AuthSession) {}
}

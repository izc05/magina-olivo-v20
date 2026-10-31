package com.isivoltpro.maginaolivo.feature.account

import java.time.Instant
import java.util.Base64
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class SupabaseAuthApiTest {
    private val url="https://abcdefghijklmnopqrst.supabase.co"
    private val config=AuthConfiguration(url,"sb_publishable_fake","com.isivoltpro.maginaolivo.dev://auth/callback")
    private val now=Instant.parse("2026-10-14T12:00:00Z")
    private val id=UUID.fromString("00000000-0000-0000-0000-000000000042")
    private val sessionJson="""{"access_token":"access","refresh_token":"refresh","token_type":"bearer","expires_in":3600,"user":{"id":"$id","email":"qa@example.com"}}"""
    @Test fun acceptsOnlyExplicitAuthorizedEnvironmentsAndPublicKeys() {
        assertNotNull(AuthConfiguration.forEnvironment("DEV",url,"sb_publishable_fake","com.isivoltpro.maginaolivo.dev"))
        assertNotNull(AuthConfiguration.forEnvironment("STAGING",url,"sb_publishable_fake","com.isivoltpro.maginaolivo.staging"))
        assertNotNull(AuthConfiguration.forEnvironment("PRODUCTION",url,"sb_publishable_fake","com.isivoltpro.maginaolivo"))
        assertNull(AuthConfiguration.forEnvironment("UNKNOWN",url,"sb_publishable_fake","app"))
        listOf("http://abcdefghijklmnopqrst.supabase.co", "https://zzelvbcuxsboafibfxch.supabase.co", "$url/path", "$url?x=1", "https://user@abcdefghijklmnopqrst.supabase.co", "https://abcdefghijklmnopqrst.supabase.co.evil.test")
            .forEach { assertNull(it,AuthConfiguration.forEnvironment("DEV",it,"sb_publishable_fake","app")) }
        listOf("", "sb_secret_private", "not-a-key").forEach { assertNull(AuthConfiguration.forEnvironment("DEV",url,it,"app")) }
        val role=Base64.getUrlEncoder().withoutPadding().encodeToString("""{"role":"service_role"}""".toByteArray())
        assertNull(AuthConfiguration.forEnvironment("DEV",url,"header.$role.signature","app"))
    }
    @Test fun passwordGrantParsesServerSessionWithoutLoggingCredentials() = runTest {
        var called=false
        val api=SupabaseAuthApi(config,AuthHttpTransport { method,path,body,token ->
            called=true;assertEquals("POST",method);assertEquals("/token?grant_type=password",path)
            assertTrue(checkNotNull(body).contains("qa@example.com"));assertTrue(body.contains("not-a-log"));assertNull(token)
            AuthHttpResponse(200,sessionJson)
        },{now})
        val session=api.signIn("qa@example.com","not-a-log")
        assertTrue(called);assertEquals(id,session.userId);assertEquals(now.plusSeconds(3600),session.expiresAt)
        assertFalse(session.toString().contains("access"));assertFalse(config.toString().contains("sb_publishable"))
    }
    @Test fun confirmationRequiredIsNotAnAuthenticatedSession() = runTest {
        val api=SupabaseAuthApi(config,AuthHttpTransport { _,path,body,_ ->
            assertTrue(path.startsWith("/signup?redirect_to="));assertTrue(checkNotNull(body).contains("s256"))
            AuthHttpResponse(200,"""{"id":"$id","email":"qa@example.com"}""")
        },{now})
        assertNull(api.signUp("qa@example.com","password42","challenge"))
    }
    @Test fun mapsErrorsWithoutReflectingSensitiveServerMessages() = runTest {
        for((status,code,kind) in listOf(Triple(400,"invalid_credentials",AuthFailure.Kind.CREDENTIALS),Triple(400,"email_not_confirmed",AuthFailure.Kind.CONFIRM_EMAIL),Triple(429,"rate_limit",AuthFailure.Kind.RATE_LIMIT),Triple(503,"unavailable",AuthFailure.Kind.NETWORK))) {
            val api=SupabaseAuthApi(config,AuthHttpTransport { _,_,_,_ -> AuthHttpResponse(status,"""{"error_code":"$code","msg":"secret-password"}""") },{now})
            try { api.signIn("qa@example.com","password42");fail("Expected error") } catch(e:AuthFailure) { assertEquals(kind,e.kind);assertFalse(e.message!!.contains("secret-password")) }
        }
    }
    @Test fun rejectsMalformedSessionsAndResponses() = runTest {
        for(body in listOf("not-json","{}",sessionJson.replace("bearer","other"),sessionJson.replace("refresh",""))) {
            val api=SupabaseAuthApi(config,AuthHttpTransport { _,_,_,_ -> AuthHttpResponse(200,body) },{now})
            try { api.signIn("qa@example.com","password42");fail("Must reject incomplete session") } catch(e:AuthFailure) { assertEquals(AuthFailure.Kind.PROTOCOL,e.kind) }
        }
    }
    @Test fun passwordRecoveryAndPkceUseOnlyAuthEndpoints() = runTest {
        val paths=mutableListOf<String>()
        val api=SupabaseAuthApi(config,AuthHttpTransport { method,path,body,token ->
            paths+=path
            when {
                path.startsWith("/recover?") -> { assertTrue(checkNotNull(body).contains("challenge"));assertNull(token);AuthHttpResponse(200,"{}") }
                path=="/token?grant_type=pkce" -> { assertTrue(checkNotNull(body).contains("verifier"));AuthHttpResponse(200,sessionJson) }
                path=="/user" && method=="PUT" -> { assertEquals("access",token);AuthHttpResponse(200,"{}") }
                path=="/logout?scope=local" -> { assertEquals("access",token);AuthHttpResponse(204,"") }
                else -> error("Unexpected path")
            }
        },{now})
        api.recover("qa@example.com","challenge")
        val session=api.exchange("code","verifier")
        api.changePassword(session,"New-password42");api.signOut(session)
        assertEquals(4,paths.size);assertTrue(paths.none { it.contains("rest/v1") || it.contains("storage") })
    }
}

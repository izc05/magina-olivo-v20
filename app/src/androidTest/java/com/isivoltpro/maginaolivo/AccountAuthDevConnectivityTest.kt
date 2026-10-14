package com.isivoltpro.maginaolivo

import androidx.test.platform.app.InstrumentationRegistry
import com.isivoltpro.maginaolivo.feature.account.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.UUID

/** Explicit opt-in only: real DEV HTTPS/Auth rejection, no email or user creation. */
class AccountAuthDevConnectivityTest {
    @Test fun realDevRejectsUnknownCredentialsWithoutImportingSession() = runBlocking {
        val project="zubpicdibqbmewxeurhm"
        assumeTrue("Real DEV Auth requires explicit opt-in; CI remains simulated",
            InstrumentationRegistry.getArguments().getString("realDevAuthProject")==project)
        assertEquals("DEV",BuildConfig.ENVIRONMENT)
        assertEquals("https://$project.supabase.co",BuildConfig.AUTH_PROJECT_URL)
        assertTrue(BuildConfig.AUTH_PUBLIC_KEY.startsWith("sb_publishable_"))
        val configuration=checkNotNull(AuthConfiguration.forEnvironment("DEV",BuildConfig.AUTH_PROJECT_URL,
            BuildConfig.AUTH_PUBLIC_KEY,BuildConfig.APPLICATION_ID))
        val store=MemoryAccountSessionStore()
        val repository=AccountRepository(SupabaseAuthApi(configuration,HttpsAuthTransport(configuration)),store)
        repository.login("nonexistent-${UUID.randomUUID()}@example.invalid","synthetic-invalid-password",true)
        assertEquals(AuthFailure(AuthFailure.Kind.CREDENTIALS).message,repository.state.value.error)
        assertFalse(repository.state.value.simulated)
        assertEquals(AccountStatus.SIGNED_OUT,repository.state.value.status)
        assertNull(store.read().session)
    }
}

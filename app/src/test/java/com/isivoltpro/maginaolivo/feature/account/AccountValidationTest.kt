package com.isivoltpro.maginaolivo.feature.account

import java.time.Instant
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

class AccountValidationTest {
    @Test fun acceptsOrdinaryEmailAndNewPassword() {
        assertTrue(AccountValidation.email(" agricultor+prueba@example.com "))
        assertTrue(AccountValidation.password("Olivo de prueba 42"))
    }
    @Test fun rejectsBlankMalformedAndHeaderInjectionEmails() {
        listOf("", "sin-arroba", "a@", "@example.com", "a@b\r\nAuthorization: secret", "a b@example.com")
            .forEach { assertFalse(it, AccountValidation.email(it)) }
    }
    @Test fun newPasswordHasBoundsAndIsNotTrimmed() {
        assertFalse(AccountValidation.password("1234567"))
        assertFalse(AccountValidation.password("x".repeat(129)))
        assertTrue(AccountValidation.password(" 123456 "))
    }
    @Test fun credentialsDoNotLeakThroughGeneratedStrings() {
        val session=AuthSession(UUID.randomUUID(),"qa@example.com","secret-access","secret-refresh",Instant.now())
        val link=PendingAuthLink("qa@example.com",LinkPurpose.RECOVERY,"secret-verifier",Instant.now())
        listOf(session.toString(),link.toString(),AccountRecord(session,link).toString()).forEach {
            assertFalse(it.contains("secret-"))
        }
    }
}

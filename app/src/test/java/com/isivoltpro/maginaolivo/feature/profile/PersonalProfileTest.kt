package com.isivoltpro.maginaolivo.feature.profile

import com.isivoltpro.maginaolivo.feature.account.AccountState
import com.isivoltpro.maginaolivo.feature.account.AccountStatus
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PersonalProfileTest {

    @Test
    fun initialsFromTwoWords() {
        assertEquals("IZ", PersonalProfile.computeInitials("Isicio Zafra", "isicio@example.com"))
        assertEquals("MÁ", PersonalProfile.computeInitials("María Ángeles", "maria@example.com"))
    }

    @Test
    fun initialsFromSingleWord() {
        assertEquals("IS", PersonalProfile.computeInitials("Isicio", "isicio@example.com"))
        assertEquals("A", PersonalProfile.computeInitials("A", "a@example.com"))
    }

    @Test
    fun initialsWithExtraWhitespace() {
        assertEquals("IZ", PersonalProfile.computeInitials("   Isicio    Zafra   ", "isicio@example.com"))
    }

    @Test
    fun initialsFromEmailWhenDisplayNameIsNull() {
        assertEquals("I", PersonalProfile.computeInitials(null, "isicio@example.com"))
        assertEquals("U", PersonalProfile.computeInitials("", "user.test@example.com"))
        assertEquals("O", PersonalProfile.computeInitials("   ", "olivar@example.com"))
    }

    @Test
    fun initialsFallbackWhenBothMissing() {
        assertEquals("?", PersonalProfile.computeInitials(null, null))
        assertEquals("?", PersonalProfile.computeInitials("", ""))
    }

    @Test
    fun personalProfileFromSignedInAccountState() {
        val userId = UUID.randomUUID()
        val state = AccountState(
            status = AccountStatus.SIGNED_IN,
            userId = userId,
            email = "isicio@example.com",
        )

        val profile = PersonalProfile.fromAccountState(state)

        assertEquals(userId, profile?.userId)
        assertEquals("isicio@example.com", profile?.email)
        assertEquals("I", profile?.initials)
    }

    @Test
    fun personalProfileIsNullWhenSignedOutOrUnconfigured() {
        val signedOutState = AccountState(
            status = AccountStatus.SIGNED_OUT,
            userId = null,
            email = null,
        )
        assertNull(PersonalProfile.fromAccountState(signedOutState))

        val unconfiguredState = AccountState(
            status = AccountStatus.UNCONFIGURED,
            userId = UUID.randomUUID(),
            email = "pending@example.com",
        )
        assertNull(PersonalProfile.fromAccountState(unconfiguredState))
    }

    @Test
    fun accountSwitchDoesNotRetainPreviousProfile() {
        val userA = UUID.randomUUID()
        val userB = UUID.randomUUID()

        val stateA = AccountState(status = AccountStatus.SIGNED_IN, userId = userA, email = "userA@example.com")
        val profileA = PersonalProfile.fromAccountState(stateA)
        assertEquals(userA, profileA?.userId)
        assertEquals("userA@example.com", profileA?.email)

        // Logout
        val loggedOut = AccountState(status = AccountStatus.SIGNED_OUT)
        assertNull(PersonalProfile.fromAccountState(loggedOut))

        // Sign in User B
        val stateB = AccountState(status = AccountStatus.SIGNED_IN, userId = userB, email = "userB@example.com")
        val profileB = PersonalProfile.fromAccountState(stateB)
        assertEquals(userB, profileB?.userId)
        assertEquals("userB@example.com", profileB?.email)
    }
}


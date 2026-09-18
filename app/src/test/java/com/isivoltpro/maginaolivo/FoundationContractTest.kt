package com.isivoltpro.maginaolivo

import org.junit.Assert.assertEquals
import org.junit.Test

class FoundationContractTest {
    @Test
    fun devVariantContractIsStable() {
        assertEquals("com.isivoltpro.maginaolivo.dev", BuildConfig.APPLICATION_ID)
        assertEquals("DEV", BuildConfig.ENVIRONMENT)
    }
}

package com.isivoltpro.maginaolivo.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingStateStoreTest {
    @Test
    fun completionSurvivesReadsFromSameStore() {
        val store = InMemoryOnboardingStateStore()

        assertFalse(store.isCompleted())
        store.markCompleted()

        assertTrue(store.isCompleted())
    }
}

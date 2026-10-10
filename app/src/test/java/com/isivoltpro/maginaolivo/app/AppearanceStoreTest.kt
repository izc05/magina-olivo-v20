package com.isivoltpro.maginaolivo.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class AppearanceStoreTest {
    @Test fun unknownOrAbsentValuesFollowSystem() {
        listOf(null, "", "night", "dark", "garbage").forEach {
            assertEquals(AppearanceMode.SYSTEM, AppearanceMode.fromStoredValue(it))
        }
        AppearanceMode.entries.forEach {
            assertEquals(it, AppearanceMode.fromStoredValue(it.name))
        }
    }

    @Test fun explicitModesOverrideSystemAndSystemTracksBothStates() {
        assertFalse(AppearanceMode.LIGHT.isDark(true))
        assertTrue(AppearanceMode.DARK.isDark(false))
        assertFalse(AppearanceMode.SYSTEM.isDark(false))
        assertTrue(AppearanceMode.SYSTEM.isDark(true))
    }

    @Test fun persistedModeIsAvailableBeforeCollectingAndSurvivesNewStore() = runTest {
        val storage = MemoryStorage()
        val first = PersistentAppearanceStore(storage, Dispatchers.Unconfined)
        assertEquals(AppearanceMode.SYSTEM, first.mode.value)
        assertTrue(first.setMode(AppearanceMode.DARK))
        assertEquals(AppearanceMode.DARK, first.mode.value)
        assertEquals(AppearanceMode.DARK, PersistentAppearanceStore(storage).mode.value)
        assertTrue(first.setMode(AppearanceMode.LIGHT))
        assertEquals(AppearanceMode.LIGHT, first.mode.value)
    }

    @Test fun unavailableStorageDoesNotCrashOrClaimAPersistedChange() = runTest {
        val broken = object : AppearanceStorage {
            override fun read(): String? = error("unavailable")
            override fun write(value: String): Boolean = error("unavailable")
        }
        val store = PersistentAppearanceStore(broken, Dispatchers.Unconfined)
        assertEquals(AppearanceMode.SYSTEM, store.mode.value)
        assertFalse(store.setMode(AppearanceMode.DARK))
        assertEquals(AppearanceMode.SYSTEM, store.mode.value)
    }

    @Test fun rejectedWritePreservesTheLastConfirmedPreference() = runTest {
        val storage = MemoryStorage("LIGHT", accepted = false)
        val store = PersistentAppearanceStore(storage, Dispatchers.Unconfined)
        assertFalse(store.setMode(AppearanceMode.DARK))
        assertEquals(AppearanceMode.LIGHT, store.mode.value)
        assertEquals("LIGHT", storage.value)
    }

    @Test fun rejectedCachedWriteStaysRejectedAfterRecreation() = runTest {
        var cached = "LIGHT"
        val storage = object : AppearanceStorage {
            override fun read(): String = cached
            override fun write(value: String): Boolean {
                // SharedPreferences commits to memory before its disk result is known.
                cached = value
                return false
            }
        }
        val first = PersistentAppearanceStore(storage, Dispatchers.Unconfined)
        assertFalse(first.setMode(AppearanceMode.DARK))
        assertEquals(AppearanceMode.LIGHT, first.mode.value)
        assertEquals(AppearanceMode.LIGHT, PersistentAppearanceStore(storage).mode.value)
    }

    @Test fun exceptionAfterCachingStaysRejectedAfterRecreation() = runTest {
        var cached = "LIGHT"
        val storage = object : AppearanceStorage {
            override fun read(): String = cached
            override fun write(value: String): Boolean {
                cached = value
                if (value == "DARK") error("disk write failed after updating memory")
                return false // Even a rejected rollback has restored the in-memory value.
            }
        }
        val first = PersistentAppearanceStore(storage, Dispatchers.Unconfined)
        assertFalse(first.setMode(AppearanceMode.DARK))
        assertEquals(AppearanceMode.LIGHT, first.mode.value)
        assertEquals(AppearanceMode.LIGHT, PersistentAppearanceStore(storage).mode.value)
    }

    private class MemoryStorage(var value: String? = null, val accepted: Boolean = true) : AppearanceStorage {
        override fun read(): String? = value
        override fun write(value: String): Boolean {
            if (accepted) this.value = value
            return accepted
        }
    }
}

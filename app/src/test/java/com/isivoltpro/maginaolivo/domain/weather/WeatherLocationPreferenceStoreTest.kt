package com.isivoltpro.maginaolivo.domain.weather

import com.isivoltpro.maginaolivo.domain.feed.FeedLocation
import com.isivoltpro.maginaolivo.domain.feed.SpanishMunicipalityCatalog
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WeatherLocationPreferenceStoreTest {

    private val userA = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
    private val userB = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb")
    private val workspace1 = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val workspace2 = UUID.fromString("22222222-2222-2222-2222-222222222222")

    private val bedmar = SpanishMunicipalityCatalog.findByCode("23902")!!
    private val ubeda = SpanishMunicipalityCatalog.findByCode("23092")!!
    private val jodar = SpanishMunicipalityCatalog.findByCode("23053")!!

    @Test
    fun preferencesAreStrictlyIsolatedBetweenUsersAndWorkspaces() = runTest {
        val storage = MapWeatherLocationStorage()
        val store = InMemoryWeatherLocationPreferenceStore(storage)

        val idUserA = WeatherPreferenceIdentity.Authenticated(userA)
        val idUserB = WeatherPreferenceIdentity.Authenticated(userB)
        val idLocal = WeatherPreferenceIdentity.Local
        val idSignedOut = WeatherPreferenceIdentity.SignedOut

        // User A sets Bedmar on workspace 1
        assertTrue(store.set(idUserA, workspace1, bedmar))
        // User A sets Úbeda on workspace 2
        assertTrue(store.set(idUserA, workspace2, ubeda))
        // User B sets Jódar on workspace 1
        assertTrue(store.set(idUserB, workspace1, jodar))

        assertEquals("23902", store.get(idUserA, workspace1)?.ineCode)
        assertEquals("23092", store.get(idUserA, workspace2)?.ineCode)
        assertEquals("23053", store.get(idUserB, workspace1)?.ineCode)
        assertNull(store.get(idUserB, workspace2))

        // Local and SignedOut identities cannot observe User A's preference
        assertNull(store.get(idLocal, workspace1))
        assertNull(store.get(idSignedOut, workspace1))
        assertNull(store.observe(idSignedOut, workspace1).first())

        // Signed out writes are rejected
        assertFalse(store.set(idSignedOut, workspace1, bedmar))
    }

    @Test
    fun logoutAndAccountSwitchNeverExposeOtherIdentityPreferences() = runTest {
        val storage = MapWeatherLocationStorage()
        val store = InMemoryWeatherLocationPreferenceStore(storage)

        val idUserA = WeatherPreferenceIdentity.Authenticated(userA)
        val idUserB = WeatherPreferenceIdentity.Authenticated(userB)

        store.set(idUserA, workspace1, bedmar)
        assertEquals("Bedmar y Garcíez", store.get(idUserA, workspace1)?.municipality)

        // Simulate Logout -> state transitions to SignedOut
        val signedOutIdentity = WeatherPreferenceIdentity.SignedOut
        assertNull(store.get(signedOutIdentity, workspace1))

        // Simulate User B Login
        assertNull(store.get(idUserB, workspace1))
    }

    @Test
    fun restartRestoresOnlyValidOfficialIneCodes() = runTest {
        val rawMap = LinkedHashMap<String, String>()
        val key = WeatherLocationPreferenceStore.scopeKey(WeatherPreferenceIdentity.Authenticated(userA), workspace1)!!
        rawMap[key] = "23902"
        // Corrupt or obsolete code
        rawMap["weather_loc|user:$userA|ws:$workspace2"] = "99999"

        val storage = MapWeatherLocationStorage(rawMap)
        val restartedStore = PersistentWeatherLocationPreferenceStore(storage)

        val restored1 = restartedStore.get(WeatherPreferenceIdentity.Authenticated(userA), workspace1)
        assertNotNull(restored1)
        assertEquals("23902", restored1?.ineCode)
        assertEquals("Bedmar y Garcíez", restored1?.municipality)

        // Corrupted code is rejected and returns null
        val restored2 = restartedStore.get(WeatherPreferenceIdentity.Authenticated(userA), workspace2)
        assertNull(restored2)
    }

    @Test
    fun diskFailureLeavesPreviousConfirmedValueIntact() = runTest {
        val storage = MapWeatherLocationStorage()
        val store = PersistentWeatherLocationPreferenceStore(storage)
        val id = WeatherPreferenceIdentity.Authenticated(userA)

        assertTrue(store.set(id, workspace1, bedmar))
        assertEquals("23902", store.get(id, workspace1)?.ineCode)

        // Simulate disk error
        storage.failWrites = true
        assertFalse(store.set(id, workspace1, ubeda))

        // Previous confirmed value remains active
        assertEquals("23902", store.get(id, workspace1)?.ineCode)
    }
}


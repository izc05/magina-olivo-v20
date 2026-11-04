package com.isivoltpro.maginaolivo.domain.weather

import com.isivoltpro.maginaolivo.domain.feed.FeedLocation
import com.isivoltpro.maginaolivo.domain.feed.IneMunicipality
import com.isivoltpro.maginaolivo.domain.feed.SpanishMunicipalityCatalog
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Verified identity scope for personal weather location preferences.
 *
 * - [Local]: unconfigured or simulated local/demo mode.
 * - [Authenticated]: a verified account session (signed in or offline with a real user id).
 * - [SignedOut]: a configured account that is signed out, expired, in recovery or checking.
 *   It has no storage scope: it never reads or writes any [Authenticated] or [Local] preference,
 *   so logging out never exposes another identity's weather location.
 */
sealed interface WeatherPreferenceIdentity {
    val storagePrefix: String?

    data object Local : WeatherPreferenceIdentity {
        override val storagePrefix: String = "local"
    }

    data class Authenticated(val userId: UUID) : WeatherPreferenceIdentity {
        override val storagePrefix: String = "user:$userId"
    }

    data object SignedOut : WeatherPreferenceIdentity {
        override val storagePrefix: String? = null
    }
}

/**
 * Local device preference store for the farmer's chosen weather municipality.
 *
 * Contract (National Weather Selector 1.0):
 * - Independent of `profile_settings`, Room and the synchronization outbox.
 * - Isolated by verified [WeatherPreferenceIdentity] and active `workspaceId`.
 * - Stores only municipalities validated against the official 2026 INE catalog.
 * - Local to this device in 1.0 (not synchronized across devices yet).
 */
interface WeatherLocationPreferenceStore {
    fun observe(identity: WeatherPreferenceIdentity, workspaceId: UUID): Flow<FeedLocation?>
    suspend fun get(identity: WeatherPreferenceIdentity, workspaceId: UUID): FeedLocation?
    suspend fun set(identity: WeatherPreferenceIdentity, workspaceId: UUID, municipality: IneMunicipality): Boolean
    suspend fun clear(identity: WeatherPreferenceIdentity, workspaceId: UUID): Boolean

    companion object {
        fun scopeKey(identity: WeatherPreferenceIdentity, workspaceId: UUID): String? {
            val prefix = identity.storagePrefix ?: return null
            return "weather_loc|$prefix|ws:$workspaceId"
        }
    }
}

/** Raw key → INE code storage (SharedPreferences on Android, a map in tests). */
interface WeatherLocationStorage {
    fun readAll(): Map<String, String>
    fun write(key: String, ineCode: String): Boolean
    fun remove(key: String): Boolean
}

class PersistentWeatherLocationPreferenceStore(
    private val storage: WeatherLocationStorage,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : WeatherLocationPreferenceStore {

    private val state = MutableStateFlow(loadValidatedMap())
    private val mutex = Mutex()

    override fun observe(identity: WeatherPreferenceIdentity, workspaceId: UUID): Flow<FeedLocation?> {
        val key = WeatherLocationPreferenceStore.scopeKey(identity, workspaceId)
        return state.map { map -> if (key == null) null else map[key] }.distinctUntilChanged()
    }

    override suspend fun get(identity: WeatherPreferenceIdentity, workspaceId: UUID): FeedLocation? {
        val key = WeatherLocationPreferenceStore.scopeKey(identity, workspaceId) ?: return null
        return state.value[key]
    }

    override suspend fun set(
        identity: WeatherPreferenceIdentity,
        workspaceId: UUID,
        municipality: IneMunicipality,
    ): Boolean = mutex.withLock {
        val key = WeatherLocationPreferenceStore.scopeKey(identity, workspaceId) ?: return@withLock false
        // Only official INE codes are stored; free text never becomes a preference.
        val validated = SpanishMunicipalityCatalog.findByCode(municipality.ineCode) ?: return@withLock false
        withContext(io) {
            val saved = writeSafely { storage.write(key, validated.ineCode) }
            if (saved) {
                state.value = state.value + (key to validated.toFeedLocation())
            } else {
                // SharedPreferences may change its cache before reporting a disk failure.
                val previous = state.value[key]?.ineCode
                if (previous != null) writeSafely { storage.write(key, previous) } else writeSafely { storage.remove(key) }
            }
            saved
        }
    }

    override suspend fun clear(identity: WeatherPreferenceIdentity, workspaceId: UUID): Boolean = mutex.withLock {
        val key = WeatherLocationPreferenceStore.scopeKey(identity, workspaceId) ?: return@withLock false
        withContext(io) {
            val removed = writeSafely { storage.remove(key) }
            if (removed) {
                state.value = state.value - key
            } else {
                state.value[key]?.ineCode?.let { previous -> writeSafely { storage.write(key, previous) } }
            }
            removed
        }
    }

    /** Entries whose code is no longer in the INE catalog are ignored, never guessed. */
    private fun loadValidatedMap(): Map<String, FeedLocation> =
        runCatching { storage.readAll() }
            .getOrDefault(emptyMap())
            .mapNotNull { (key, storedCode) ->
                if (!key.startsWith("weather_loc|")) return@mapNotNull null
                val entry = SpanishMunicipalityCatalog.findByCode(storedCode) ?: return@mapNotNull null
                key to entry.toFeedLocation()
            }
            .toMap()

    private fun writeSafely(block: () -> Boolean): Boolean = try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        false
    }
}

/** In-memory storage, used by tests and previews; also lets tests simulate a "restart". */
class MapWeatherLocationStorage(private val backing: MutableMap<String, String> = LinkedHashMap()) : WeatherLocationStorage {
    var failWrites: Boolean = false
    override fun readAll(): Map<String, String> = HashMap(backing)
    override fun write(key: String, ineCode: String): Boolean {
        if (failWrites) return false
        backing[key] = ineCode
        return true
    }
    override fun remove(key: String): Boolean {
        if (failWrites) return false
        backing.remove(key)
        return true
    }
}

class InMemoryWeatherLocationPreferenceStore(
    storage: WeatherLocationStorage = MapWeatherLocationStorage(),
) : WeatherLocationPreferenceStore by PersistentWeatherLocationPreferenceStore(storage, Dispatchers.Unconfined)

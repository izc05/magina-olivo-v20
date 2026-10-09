package com.isivoltpro.maginaolivo.app

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** #707: device UI preference, never a Farm/workspace attribute or Room/outbox row. */
enum class AppearanceMode {
    SYSTEM, LIGHT, DARK;

    fun isDark(systemDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemDark
        LIGHT -> false
        DARK -> true
    }

    companion object {
        fun fromStoredValue(value: String?): AppearanceMode = entries.firstOrNull { it.name == value } ?: SYSTEM
    }
}

interface AppearanceStore {
    val mode: StateFlow<AppearanceMode>
    /** False means the previous confirmed mode remains active; agricultural work is unaffected. */
    suspend fun setMode(mode: AppearanceMode): Boolean
}

/** Temporary release guard, removed in DARK-3 together with the final Perfil selector. */
internal fun productionAppearanceMode(stored: AppearanceMode): AppearanceMode = when (stored) {
    AppearanceMode.SYSTEM, AppearanceMode.LIGHT, AppearanceMode.DARK -> AppearanceMode.LIGHT
}

internal interface AppearanceStorage {
    fun read(): String?
    fun write(value: String): Boolean
}

internal class PersistentAppearanceStore(
    private val storage: AppearanceStorage,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : AppearanceStore {
    // A tiny synchronous bootstrap prevents a light frame before a persisted dark preference.
    private val state = MutableStateFlow(AppearanceMode.fromStoredValue(runCatching { storage.read() }.getOrNull()))
    override val mode = state.asStateFlow()
    private val mutex = Mutex()

    override suspend fun setMode(mode: AppearanceMode): Boolean = mutex.withLock {
        withContext(io) {
            val saved = try {
                storage.write(mode.name)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                false
            }
            if (saved) state.value = mode
            saved
        }
    }
}

class InMemoryAppearanceStore(initial: AppearanceMode = AppearanceMode.SYSTEM) : AppearanceStore {
    private val state = MutableStateFlow(initial)
    override val mode = state.asStateFlow()
    override suspend fun setMode(mode: AppearanceMode): Boolean {
        state.value = mode
        return true
    }
}

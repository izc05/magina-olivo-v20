package com.isivoltpro.maginaolivo.app

import android.content.Context
import androidx.core.content.edit
import java.util.UUID

/**
 * UX-C (Issue #246, CR-007): the Farm Mi Cuaderno opens on — the last one the farmer chose.
 * A device preference only: not domain data, not synchronised, no Room table.
 */
interface ActiveFarmStore {
    fun get(): UUID?

    fun set(farmId: UUID)

    /** #427: no active Farm means no persisted fallback UUID from an archived/deleted Farm. */
    fun clear()
}

class InMemoryActiveFarmStore(private var farmId: UUID? = null) : ActiveFarmStore {
    override fun get(): UUID? = farmId

    override fun set(farmId: UUID) {
        this.farmId = farmId
    }

    override fun clear() {
        farmId = null
    }
}

class AndroidActiveFarmStore(context: Context) : ActiveFarmStore {
    private val preferences = context.getSharedPreferences(OnboardingStatePreferences.NAME, Context.MODE_PRIVATE)

    override fun get(): UUID? =
        preferences.getString(KEY_ACTIVE_FARM, null)?.let { runCatching { UUID.fromString(it) }.getOrNull() }

    override fun set(farmId: UUID) {
        preferences.edit { putString(KEY_ACTIVE_FARM, farmId.toString()) }
    }

    override fun clear() {
        preferences.edit { remove(KEY_ACTIVE_FARM) }
    }

    private companion object {
        const val KEY_ACTIVE_FARM = "active_farm_id"
    }
}

private object OnboardingStatePreferences {
    const val NAME = AndroidOnboardingStateStore.PREFERENCES_NAME
}

package com.isivoltpro.maginaolivo.app

import android.content.Context
import com.isivoltpro.maginaolivo.domain.weather.PersistentWeatherLocationPreferenceStore
import com.isivoltpro.maginaolivo.domain.weather.WeatherLocationPreferenceStore
import com.isivoltpro.maginaolivo.domain.weather.WeatherLocationStorage
import com.isivoltpro.maginaolivo.domain.weather.WeatherPreferenceIdentity
import com.isivoltpro.maginaolivo.feature.account.AccountRepository
import com.isivoltpro.maginaolivo.feature.account.AccountState
import com.isivoltpro.maginaolivo.feature.account.AccountStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * National Weather Selector 1.0: maps the account state (read only, Cuenta is not modified) to
 * the identity scope of personal weather preferences.
 */
object WeatherIdentityMapper {
    fun fromAccountState(state: AccountState): WeatherPreferenceIdentity {
        if (state.status == AccountStatus.UNCONFIGURED || state.simulated) return WeatherPreferenceIdentity.Local
        val uid = state.userId
        if ((state.status == AccountStatus.SIGNED_IN || state.status == AccountStatus.OFFLINE) && uid != null) {
            return WeatherPreferenceIdentity.Authenticated(uid)
        }
        return WeatherPreferenceIdentity.SignedOut
    }

    fun observe(accountRepository: AccountRepository?): Flow<WeatherPreferenceIdentity> =
        accountRepository?.state?.map(::fromAccountState)?.distinctUntilChanged()
            ?: flowOf(WeatherPreferenceIdentity.Local)
}

/** Device preference (not Room, not outbox, not synchronised): `magina_weather_location_preferences`. */
class AndroidWeatherLocationPreferenceStore(context: Context) : WeatherLocationPreferenceStore by PersistentWeatherLocationPreferenceStore(
    object : WeatherLocationStorage {
        private val preferences by lazy {
            context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        }

        override fun readAll(): Map<String, String> =
            preferences.all.mapNotNull { (k, v) -> (v as? String)?.let { k to it } }.toMap()

        override fun write(key: String, ineCode: String): Boolean = preferences.edit().putString(key, ineCode).commit()

        override fun remove(key: String): Boolean = preferences.edit().remove(key).commit()
    },
) {
    companion object {
        const val PREFERENCES_NAME = "magina_weather_location_preferences"
    }
}

